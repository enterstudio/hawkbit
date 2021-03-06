/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.distributions.dstable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.hawkbit.repository.DistributionSetManagement;
import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.SoftwareManagement;
import org.eclipse.hawkbit.repository.TagManagement;
import org.eclipse.hawkbit.repository.TargetManagement;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.SoftwareModule;
import org.eclipse.hawkbit.ui.SpPermissionChecker;
import org.eclipse.hawkbit.ui.artifacts.event.SoftwareModuleEvent;
import org.eclipse.hawkbit.ui.artifacts.event.SoftwareModuleEvent.SoftwareModuleEventType;
import org.eclipse.hawkbit.ui.common.detailslayout.AbstractNamedVersionedEntityTableDetailsLayout;
import org.eclipse.hawkbit.ui.common.detailslayout.DistributionSetMetadatadetailslayout;
import org.eclipse.hawkbit.ui.common.detailslayout.SoftwareModuleDetailsTable;
import org.eclipse.hawkbit.ui.common.detailslayout.TargetFilterQueryDetailsTable;
import org.eclipse.hawkbit.ui.common.entity.SoftwareModuleIdName;
import org.eclipse.hawkbit.ui.common.tagdetails.DistributionTagToken;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.decorators.SPUIButtonStyleSmallNoBorder;
import org.eclipse.hawkbit.ui.distributions.event.SaveActionWindowEvent;
import org.eclipse.hawkbit.ui.distributions.state.ManageDistUIState;
import org.eclipse.hawkbit.ui.management.dstable.DistributionAddUpdateWindowLayout;
import org.eclipse.hawkbit.ui.management.event.DistributionTableEvent;
import org.eclipse.hawkbit.ui.management.state.ManagementUIState;
import org.eclipse.hawkbit.ui.utils.HawkbitCommonUtil;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.vaadin.spring.events.EventBus.UIEventBus;
import org.vaadin.spring.events.EventScope;
import org.vaadin.spring.events.annotation.EventBusListenerMethod;

import com.vaadin.data.Item;
import com.vaadin.server.FontAwesome;
import com.vaadin.ui.Button;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Window;

/**
 * Distribution set details layout.
 */
public class DistributionSetDetails extends AbstractNamedVersionedEntityTableDetailsLayout<DistributionSet> {

    private static final long serialVersionUID = -4595004466943546669L;

    private static final String SOFT_MODULE = "softwareModule";

    private final ManageDistUIState manageDistUIState;

    private final DistributionAddUpdateWindowLayout distributionAddUpdateWindowLayout;

    private final DistributionTagToken distributionTagToken;

    private final transient SoftwareManagement softwareManagement;

    private final transient DistributionSetManagement distributionSetManagement;

    private final transient TargetManagement targetManagement;

    private final DsMetadataPopupLayout dsMetadataPopupLayout;

    private final SoftwareModuleDetailsTable softwareModuleTable;

    private final DistributionSetMetadatadetailslayout dsMetadataTable;

    private final TargetFilterQueryDetailsTable tfqDetailsTable;

    private VerticalLayout tagsLayout;

    private Map<String, StringBuilder> assignedSWModule;

    DistributionSetDetails(final VaadinMessageSource i18n, final UIEventBus eventBus, final SpPermissionChecker permissionChecker,
            final ManageDistUIState manageDistUIState, final ManagementUIState managementUIState,
            final DistributionAddUpdateWindowLayout distributionAddUpdateWindowLayout,
            final SoftwareManagement softwareManagement, final DistributionSetManagement distributionSetManagement,
            final TargetManagement targetManagement, final EntityFactory entityFactory,
            final UINotification uinotification, final TagManagement tagManagement,
            final DsMetadataPopupLayout popupLayout, final UINotification uiNotification) {
        super(i18n, eventBus, permissionChecker, managementUIState);
        this.manageDistUIState = manageDistUIState;
        this.distributionAddUpdateWindowLayout = distributionAddUpdateWindowLayout;
        this.distributionTagToken = new DistributionTagToken(permissionChecker, i18n, uinotification, eventBus,
                managementUIState, tagManagement, distributionSetManagement);
        this.softwareManagement = softwareManagement;
        this.distributionSetManagement = distributionSetManagement;
        this.targetManagement = targetManagement;
        this.dsMetadataPopupLayout = popupLayout;

        softwareModuleTable = new SoftwareModuleDetailsTable(i18n, true, permissionChecker, distributionSetManagement,
                eventBus, manageDistUIState, uiNotification);

        dsMetadataTable = new DistributionSetMetadatadetailslayout(i18n, permissionChecker, distributionSetManagement,
                dsMetadataPopupLayout, entityFactory, uiNotification);

        tfqDetailsTable = new TargetFilterQueryDetailsTable(i18n);
        addTabs(detailsTab);
        restoreState();
    }

    protected VerticalLayout createTagsLayout() {
        tagsLayout = getTabLayout();
        return tagsLayout;
    }

    @Override
    protected void populateDetailsWidget() {
        populateDetails();
        populateModule();
        populateTags();
        populateMetadataDetails();
        populateTargetFilterQueries();
    }

    private void populateModule() {
        softwareModuleTable.populateModule(getSelectedBaseEntity());
        showUnsavedAssignment();
    }

    @SuppressWarnings("unchecked")
    private void showUnsavedAssignment() {
        final Set<SoftwareModuleIdName> softwareModuleIdNameList = manageDistUIState.getLastSelectedDistribution()
                .map(selectedDistId -> manageDistUIState.getAssignedList().entrySet().stream()
                        .filter(entry -> entry.getKey().getId().equals(selectedDistId)).findAny()
                        .map(Map.Entry::getValue).orElse(null))
                .orElse(null);

        if (null != softwareModuleIdNameList) {
            if (assignedSWModule == null) {
                assignedSWModule = new HashMap<>();
            }

            softwareModuleIdNameList.stream().map(SoftwareModuleIdName::getId)
                    .map(softwareManagement::findSoftwareModuleById)
                    .forEach(found -> found.ifPresent(softwareModule -> {

                        if (assignedSWModule.containsKey(softwareModule.getType().getName())) {
                            assignedSWModule.get(softwareModule.getType().getName()).append("</br>").append("<I>")
                                    .append(HawkbitCommonUtil.getFormattedNameVersion(softwareModule.getName(),
                                            softwareModule.getVersion()))
                                    .append("<I>");

                        } else {
                            assignedSWModule
                                    .put(softwareModule.getType().getName(),
                                            new StringBuilder().append("<I>")
                                                    .append(HawkbitCommonUtil.getFormattedNameVersion(
                                                            softwareModule.getName(), softwareModule.getVersion()))
                                                    .append("<I>"));
                        }

                    }));

            for (final Map.Entry<String, StringBuilder> entry : assignedSWModule.entrySet()) {
                final Item item = softwareModuleTable.getContainerDataSource().getItem(entry.getKey());
                if (item != null) {
                    item.getItemProperty(SOFT_MODULE).setValue(createSoftModuleLayout(entry.getValue().toString()));
                }
            }
        }
    }

    private Button assignSoftModuleButton(final String softwareModuleName) {
        if (getPermissionChecker().hasUpdateDistributionPermission() && manageDistUIState.getLastSelectedDistribution()
                .map(selected -> targetManagement.countTargetByAssignedDistributionSet(selected) <= 0).orElse(false)) {

            final Button reassignSoftModule = SPUIComponentProvider.getButton(softwareModuleName, "", "", "", true,
                    FontAwesome.TIMES, SPUIButtonStyleSmallNoBorder.class);
            reassignSoftModule.setEnabled(false);
            return reassignSoftModule;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void updateSoftwareModule(final SoftwareModule module) {
        if (assignedSWModule == null) {
            assignedSWModule = new HashMap<>();
        }

        softwareModuleTable.getContainerDataSource().getItemIds();
        if (assignedSWModule.containsKey(module.getType().getName())) {
            /*
             * If software module type is software, means multiple softwares can
             * assigned to that type. Hence if multiple softwares belongs to
             * same type is dropped, then add to the list.
             */

            if (module.getType().getMaxAssignments() > 1) {
                assignedSWModule.get(module.getType().getName()).append("</br>").append("<I>")
                        .append(HawkbitCommonUtil.getFormattedNameVersion(module.getName(), module.getVersion()))
                        .append("</I>");
            }

            /*
             * If software module type is firmware, means single software can be
             * assigned to that type. Hence if multiple softwares belongs to
             * same type is dropped, then override with previous one.
             */
            if (module.getType().getMaxAssignments() == 1) {
                assignedSWModule.put(module.getType().getName(),
                        new StringBuilder().append("<I>").append(
                                HawkbitCommonUtil.getFormattedNameVersion(module.getName(), module.getVersion()))
                                .append("</I>"));
            }

        } else {
            assignedSWModule.put(module.getType().getName(),
                    new StringBuilder().append("<I>")
                            .append(HawkbitCommonUtil.getFormattedNameVersion(module.getName(), module.getVersion()))
                            .append("</I>"));
        }

        for (final Map.Entry<String, StringBuilder> entry : assignedSWModule.entrySet()) {
            final Item item = softwareModuleTable.getContainerDataSource().getItem(entry.getKey());
            if (item != null) {
                item.getItemProperty(SOFT_MODULE).setValue(createSoftModuleLayout(entry.getValue().toString()));
            }
        }
    }

    private VerticalLayout createSoftModuleLayout(final String softwareModuleName) {
        final VerticalLayout verticalLayout = new VerticalLayout();
        final HorizontalLayout horizontalLayout = new HorizontalLayout();
        horizontalLayout.setSizeFull();
        final Label softwareModule = HawkbitCommonUtil.getFormatedLabel(StringUtils.EMPTY);
        final Button reassignSoftModule = assignSoftModuleButton(softwareModuleName);
        softwareModule.setValue(softwareModuleName);
        softwareModule.setDescription(softwareModuleName);
        softwareModule.setId(softwareModuleName + "-label");
        horizontalLayout.addComponent(softwareModule);
        horizontalLayout.setExpandRatio(softwareModule, 1F);
        horizontalLayout.addComponent(reassignSoftModule);
        verticalLayout.addComponent(horizontalLayout);
        return verticalLayout;
    }

    private VerticalLayout createSoftwareModuleTab() {
        final VerticalLayout softwareLayout = getTabLayout();
        softwareLayout.setSizeFull();
        softwareLayout.addComponent(softwareModuleTable);
        return softwareLayout;
    }

    private void populateTags() {
        tagsLayout.removeAllComponents();
        if (getSelectedBaseEntity() == null) {
            return;
        }
        tagsLayout.addComponent(distributionTagToken.getTokenField());
    }

    private void populateDetails() {
        if (getSelectedBaseEntity() != null) {
            updateDistributionSetDetailsLayout(getSelectedBaseEntity().getType().getName(),
                    getSelectedBaseEntity().isRequiredMigrationStep());
        } else {
            updateDistributionSetDetailsLayout(null, null);
        }
    }

    @Override
    protected void populateMetadataDetails() {
        dsMetadataTable.populateDSMetadata(getSelectedBaseEntity());
    }

    protected void populateTargetFilterQueries() {
        tfqDetailsTable.populateTableByDistributionSet(getSelectedBaseEntity());
    }

    private void updateDistributionSetDetailsLayout(final String type, final Boolean isMigrationRequired) {
        final VerticalLayout detailsTabLayout = getDetailsLayout();
        detailsTabLayout.removeAllComponents();

        if (type != null) {
            final Label typeLabel = SPUIComponentProvider.createNameValueLabel(getI18n().getMessage("label.dist.details.type"),
                    type);
            typeLabel.setId(UIComponentIdProvider.DETAILS_TYPE_LABEL_ID);
            detailsTabLayout.addComponent(typeLabel);
        }

        if (isMigrationRequired != null) {
            detailsTabLayout.addComponent(SPUIComponentProvider.createNameValueLabel(
                    getI18n().getMessage("checkbox.dist.migration.required"),
                    isMigrationRequired.equals(Boolean.TRUE) ? getI18n().getMessage("label.yes") : getI18n().getMessage("label.no")));
        }
    }

    @Override
    protected void onEdit(final ClickEvent event) {
        final Window newDistWindow = distributionAddUpdateWindowLayout.getWindow(getSelectedBaseEntityId());
        newDistWindow.setCaption(getI18n().getMessage(UIComponentIdProvider.DIST_UPDATE_CAPTION));
        UI.getCurrent().addWindow(newDistWindow);
        newDistWindow.setVisible(Boolean.TRUE);
    }

    @Override
    protected String getEditButtonId() {
        return UIComponentIdProvider.DS_EDIT_BUTTON;
    }

    @Override
    protected boolean onLoadIsTableRowSelected() {
        return manageDistUIState.getSelectedDistributions().map(selected -> !selected.isEmpty()).orElse(false);
    }

    @Override
    protected boolean onLoadIsTableMaximized() {
        return manageDistUIState.isDsTableMaximized();
    }

    @Override
    protected String getDefaultCaption() {
        return getI18n().getMessage("distribution.details.header");
    }

    @Override
    protected void addTabs(final TabSheet detailsTab) {
        detailsTab.addTab(createDetailsLayout(), getI18n().getMessage("caption.tab.details"), null);
        detailsTab.addTab(createDescriptionLayout(), getI18n().getMessage("caption.tab.description"), null);
        detailsTab.addTab(createSoftwareModuleTab(), getI18n().getMessage("caption.softwares.distdetail.tab"), null);
        detailsTab.addTab(createTagsLayout(), getI18n().getMessage("caption.tags.tab"), null);
        detailsTab.addTab(createLogLayout(), getI18n().getMessage("caption.logs.tab"), null);
        detailsTab.addTab(dsMetadataTable, getI18n().getMessage("caption.metadata"), null);
        detailsTab.addTab(tfqDetailsTable, getI18n().getMessage("caption.auto.assignment.ds"), null);
    }

    @Override
    protected boolean hasEditPermission() {
        return getPermissionChecker().hasUpdateDistributionPermission();
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    void onEvent(final SoftwareModuleEvent event) {
        if (event.getSoftwareModuleEventType() == SoftwareModuleEventType.ASSIGN_SOFTWARE_MODULE) {
            UI.getCurrent().access(() -> updateSoftwareModule(event.getEntity()));
        }
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    void onEvent(final DistributionTableEvent distributionTableEvent) {
        onBaseEntityEvent(distributionTableEvent);
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    void onEvent(final SaveActionWindowEvent saveActionWindowEvent) {
        if ((saveActionWindowEvent == SaveActionWindowEvent.SAVED_ASSIGNMENTS
                || saveActionWindowEvent == SaveActionWindowEvent.DISCARD_ALL_ASSIGNMENTS)
                && getSelectedBaseEntity() != null) {
            if (assignedSWModule != null) {
                assignedSWModule.clear();
            }

            distributionSetManagement.findDistributionSetByIdWithDetails(getSelectedBaseEntityId()).ifPresent(set -> {
                setSelectedBaseEntity(set);
                UI.getCurrent().access(this::populateModule);
            });
        }
    }

    @EventBusListenerMethod(scope = EventScope.UI)
    void onEventDiscard(final SaveActionWindowEvent saveActionWindowEvent) {
        if (saveActionWindowEvent == SaveActionWindowEvent.DISCARD_ASSIGNMENT
                || saveActionWindowEvent == SaveActionWindowEvent.DISCARD_ALL_ASSIGNMENTS
                || saveActionWindowEvent == SaveActionWindowEvent.DELETE_ALL_SOFWARE) {
            if (assignedSWModule != null) {
                assignedSWModule.clear();
            }
            showUnsavedAssignment();
        }
    }

    @Override
    protected String getTabSheetId() {
        return UIComponentIdProvider.DISTRIBUTIONSET_DETAILS_TABSHEET_ID;
    }

    @Override
    protected String getDetailsHeaderCaptionId() {
        return UIComponentIdProvider.DISTRIBUTION_DETAILS_HEADER_LABEL_ID;
    }

    @Override
    protected boolean isMetadataIconToBeDisplayed() {
        return true;
    }

    @Override
    protected void showMetadata(final ClickEvent event) {
        distributionSetManagement.findDistributionSetByIdWithDetails(getSelectedBaseEntityId())
                .ifPresent(ds -> UI.getCurrent().addWindow(dsMetadataPopupLayout.getWindow(ds, null)));
    }
}
