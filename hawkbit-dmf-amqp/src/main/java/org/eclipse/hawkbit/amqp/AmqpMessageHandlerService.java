/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.amqp;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.hawkbit.dmf.amqp.api.EventTopic;
import org.eclipse.hawkbit.dmf.amqp.api.MessageHeaderKey;
import org.eclipse.hawkbit.dmf.amqp.api.MessageType;
import org.eclipse.hawkbit.dmf.json.model.ActionUpdateStatus;
import org.eclipse.hawkbit.dmf.json.model.AttributeUpdate;
import org.eclipse.hawkbit.im.authentication.SpPermission.SpringEvalExpressions;
import org.eclipse.hawkbit.im.authentication.TenantAwareAuthenticationDetails;
import org.eclipse.hawkbit.repository.ControllerManagement;
import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.RepositoryConstants;
import org.eclipse.hawkbit.repository.builder.ActionStatusCreate;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.Action.Status;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.util.IpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

/**
 *
 * {@link AmqpMessageHandlerService} handles all incoming target interaction
 * AMQP messages (e.g. create target, check for updates etc.) for the queue
 * which is configured for the property hawkbit.dmf.rabbitmq.receiverQueue.
 *
 */
public class AmqpMessageHandlerService extends BaseAmqpService {

    private static final Logger LOG = LoggerFactory.getLogger(AmqpMessageHandlerService.class);

    private final AmqpMessageDispatcherService amqpMessageDispatcherService;

    private final ControllerManagement controllerManagement;

    private final EntityFactory entityFactory;

    /**
     * Constructor.
     * 
     * @param rabbitTemplate
     *            for converting messages
     * @param amqpMessageDispatcherService
     *            to sending events to DMF client
     * @param controllerManagement
     *            for target repo access
     * @param entityFactory
     *            to create entities
     */
    public AmqpMessageHandlerService(final RabbitTemplate rabbitTemplate,
            final AmqpMessageDispatcherService amqpMessageDispatcherService,
            final ControllerManagement controllerManagement, final EntityFactory entityFactory) {
        super(rabbitTemplate);
        this.amqpMessageDispatcherService = amqpMessageDispatcherService;
        this.controllerManagement = controllerManagement;
        this.entityFactory = entityFactory;
    }

    /**
     * Method to handle all incoming DMF amqp messages.
     *
     * @param message
     *            incoming message
     * @param type
     *            the message type
     * @param tenant
     *            the contentType of the message
     * 
     * @return a message if <null> no message is send back to sender
     */
    @RabbitListener(queues = "${hawkbit.dmf.rabbitmq.receiverQueue:dmf_receiver}", containerFactory = "listenerContainerFactory")
    public Message onMessage(final Message message, @Header(MessageHeaderKey.TYPE) final String type,
            @Header(MessageHeaderKey.TENANT) final String tenant) {
        return onMessage(message, type, tenant, getRabbitTemplate().getConnectionFactory().getVirtualHost());
    }

    /**
     * * Executed if a amqp message arrives.
     * 
     * @param message
     *            the message
     * @param type
     *            the type
     * @param tenant
     *            the tenant
     * @param virtualHost
     *            the virtual host
     * @return the rpc message back to supplier.
     */
    public Message onMessage(final Message message, final String type, final String tenant, final String virtualHost) {
        checkContentTypeJson(message);
        final SecurityContext oldContext = SecurityContextHolder.getContext();
        try {
            final MessageType messageType = MessageType.valueOf(type);
            switch (messageType) {
            case THING_CREATED:
                setTenantSecurityContext(tenant);
                registerTarget(message, virtualHost);
                break;
            case EVENT:
                setTenantSecurityContext(tenant);
                final String topicValue = getStringHeaderKey(message, MessageHeaderKey.TOPIC, "EventTopic is null");
                final EventTopic eventTopic = EventTopic.valueOf(topicValue);
                handleIncomingEvent(message, eventTopic);
                break;
            default:
                logAndThrowMessageError(message, "No handle method was found for the given message type.");
            }
        } catch (final IllegalArgumentException ex) {
            throw new AmqpRejectAndDontRequeueException("Invalid message!", ex);
        } finally {
            SecurityContextHolder.setContext(oldContext);
        }
        return null;
    }

    private static void setSecurityContext(final Authentication authentication) {
        final SecurityContextImpl securityContextImpl = new SecurityContextImpl();
        securityContextImpl.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContextImpl);
    }

    private static void setTenantSecurityContext(final String tenantId) {
        final AnonymousAuthenticationToken authenticationToken = new AnonymousAuthenticationToken(
                UUID.randomUUID().toString(), "AMQP-Controller",
                Collections.singletonList(new SimpleGrantedAuthority(SpringEvalExpressions.CONTROLLER_ROLE_ANONYMOUS)));
        authenticationToken.setDetails(new TenantAwareAuthenticationDetails(tenantId, true));
        setSecurityContext(authenticationToken);
    }

    /**
     * Method to create a new target or to find the target if it already exists.
     *
     * @param targetID
     *            the ID of the target/thing
     * @param ip
     *            the ip of the target/thing
     */
    private void registerTarget(final Message message, final String virtualHost) {
        final String thingId = getStringHeaderKey(message, MessageHeaderKey.THING_ID, "ThingId is null");
        final String replyTo = message.getMessageProperties().getReplyTo();

        if (StringUtils.isEmpty(replyTo)) {
            logAndThrowMessageError(message, "No ReplyTo was set for the createThing message.");
        }

        final URI amqpUri = IpUtil.createAmqpUri(virtualHost, replyTo);
        final Target target = controllerManagement.findOrRegisterTargetIfItDoesNotexist(thingId, amqpUri);
        LOG.debug("Target {} reported online state.", thingId);

        lookIfUpdateAvailable(target);
    }

    private void lookIfUpdateAvailable(final Target target) {

        final Optional<Action> actionOptional = controllerManagement
                .findOldestActiveActionByTarget(target.getControllerId());

        if (!actionOptional.isPresent()) {
            return;
        }

        final Action action = actionOptional.get();
        if (action.isCancelingOrCanceled()) {
            amqpMessageDispatcherService.sendCancelMessageToTarget(target.getTenant(), target.getControllerId(),
                    action.getId(), target.getAddress());
            return;
        }

        amqpMessageDispatcherService.sendUpdateMessageToTarget(action.getTenant(), action.getTarget(), action.getId(),
                action.getDistributionSet().getModules());
    }

    /**
     * Method to handle the different topics to an event.
     *
     * @param message
     *            the incoming event message.
     * @param topic
     *            the topic of the event.
     */
    private void handleIncomingEvent(final Message message, final EventTopic topic) {
        switch (topic) {
        case UPDATE_ACTION_STATUS:
            updateActionStatus(message);
            break;
        case UPDATE_ATTRIBUTES:
            updateAttributes(message);
            break;
        default:
            logAndThrowMessageError(message, "Got event without appropriate topic.");
            break;
        }

    }

    private void updateAttributes(final Message message) {
        final AttributeUpdate attributeUpdate = convertMessage(message, AttributeUpdate.class);
        final String thingId = getStringHeaderKey(message, MessageHeaderKey.THING_ID, "ThingId is null");

        controllerManagement.updateControllerAttributes(thingId, attributeUpdate.getAttributes());
    }

    /**
     * Method to update the action status of an action through the event.
     *
     * @param actionUpdateStatus
     *            the object form the ampq message
     */
    private void updateActionStatus(final Message message) {
        final ActionUpdateStatus actionUpdateStatus = convertMessage(message, ActionUpdateStatus.class);
        final Action action = checkActionExist(message, actionUpdateStatus);

        final List<String> messages = actionUpdateStatus.getMessage();
        if (ArrayUtils.isNotEmpty(message.getMessageProperties().getCorrelationId())) {
            messages.add(RepositoryConstants.SERVER_MESSAGE_PREFIX + "DMF message correlation-id "
                    + convertCorrelationId(message));
        }

        updateLastPollTime(action.getTarget());

        final Status status = mapStatus(message, actionUpdateStatus, action);
        final ActionStatusCreate actionStatus = entityFactory.actionStatus().create(action.getId()).status(status)
                .messages(messages);

        final Action addUpdateActionStatus = getUpdateActionStatus(status, actionStatus);

        if (!addUpdateActionStatus.isActive()) {
            lookIfUpdateAvailable(action.getTarget());
        }
    }

    private Status mapStatus(final Message message, final ActionUpdateStatus actionUpdateStatus, final Action action) {
        Status status = null;
        switch (actionUpdateStatus.getActionStatus()) {
        case DOWNLOAD:
            status = Status.DOWNLOAD;
            break;
        case RETRIEVED:
            status = Status.RETRIEVED;
            break;
        case RUNNING:
            status = Status.RUNNING;
            break;
        case CANCELED:
            status = Status.CANCELED;
            break;
        case FINISHED:
            status = Status.FINISHED;
            break;
        case ERROR:
            status = Status.ERROR;
            break;
        case WARNING:
            status = Status.WARNING;
            break;
        case CANCEL_REJECTED:
            status = hanldeCancelRejectedState(message, action);
            break;
        default:
            logAndThrowMessageError(message, "Status for action does not exisit.");
        }

        return status;
    }

    private Status hanldeCancelRejectedState(final Message message, final Action action) {
        if (action.isCancelingOrCanceled()) {
            return Status.CANCEL_REJECTED;
        }
        logAndThrowMessageError(message,
                "Cancel rejected message is not allowed, if action is on state: " + action.getStatus());
        return null;

    }

    private void updateLastPollTime(final Target target) {
        controllerManagement.updateLastTargetQuery(target.getControllerId(), null);
    }

    private static String convertCorrelationId(final Message message) {
        return new String(message.getMessageProperties().getCorrelationId(), StandardCharsets.UTF_8);
    }

    private Action getUpdateActionStatus(final Status status, final ActionStatusCreate actionStatus) {
        if (Status.CANCELED.equals(status)) {
            return controllerManagement.addCancelActionStatus(actionStatus);
        }
        return controllerManagement.addUpdateActionStatus(actionStatus);
    }

    // Exception squid:S3655 - logAndThrowMessageError throws exception, i.e.
    // get will not be called
    @SuppressWarnings("squid:S3655")
    private Action checkActionExist(final Message message, final ActionUpdateStatus actionUpdateStatus) {
        final Long actionId = actionUpdateStatus.getActionId();

        LOG.debug("Target notifies intermediate about action {} with status {}.", actionId,
                actionUpdateStatus.getActionStatus());

        final Optional<Action> findActionWithDetails = controllerManagement.findActionWithDetails(actionId);
        if (!findActionWithDetails.isPresent()) {
            logAndThrowMessageError(message,
                    "Got intermediate notification about action " + actionId + " but action does not exist");
        }

        return findActionWithDetails.get();
    }
}
