/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.ui.artifacts.smtable;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.hawkbit.repository.EntityFactory;
import org.eclipse.hawkbit.repository.SoftwareManagement;
import org.eclipse.hawkbit.repository.builder.SoftwareModuleCreate;
import org.eclipse.hawkbit.repository.exception.EntityNotFoundException;
import org.eclipse.hawkbit.repository.model.SoftwareModule;
import org.eclipse.hawkbit.repository.model.SoftwareModuleType;
import org.eclipse.hawkbit.ui.artifacts.event.SoftwareModuleEvent;
import org.eclipse.hawkbit.ui.common.CommonDialogWindow;
import org.eclipse.hawkbit.ui.common.CommonDialogWindow.SaveDialogCloseListener;
import org.eclipse.hawkbit.ui.common.SoftwareModuleTypeBeanQuery;
import org.eclipse.hawkbit.ui.common.builder.TextAreaBuilder;
import org.eclipse.hawkbit.ui.common.builder.TextFieldBuilder;
import org.eclipse.hawkbit.ui.common.builder.WindowBuilder;
import org.eclipse.hawkbit.ui.common.table.BaseEntityEventType;
import org.eclipse.hawkbit.ui.components.SPUIComponentProvider;
import org.eclipse.hawkbit.ui.utils.HawkbitCommonUtil;
import org.eclipse.hawkbit.ui.utils.VaadinMessageSource;
import org.eclipse.hawkbit.ui.utils.SPUIDefinitions;
import org.eclipse.hawkbit.ui.utils.SPUILabelDefinitions;
import org.eclipse.hawkbit.ui.utils.UIComponentIdProvider;
import org.eclipse.hawkbit.ui.utils.UINotification;
import org.vaadin.addons.lazyquerycontainer.BeanQueryFactory;
import org.vaadin.spring.events.EventBus;
import org.vaadin.spring.events.EventBus.UIEventBus;

import com.vaadin.ui.ComboBox;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.TextField;
import com.vaadin.ui.themes.ValoTheme;

/**
 * Generates window for Software module add or update.
 */
public class SoftwareModuleAddUpdateWindow extends CustomComponent {

    private static final long serialVersionUID = -5217675246477211483L;

    private final VaadinMessageSource i18n;

    private final UINotification uiNotifcation;

    private final transient EventBus.UIEventBus eventBus;

    private final transient SoftwareManagement softwareManagement;

    private final transient EntityFactory entityFactory;

    private TextField nameTextField;

    private TextField versionTextField;

    private TextField vendorTextField;

    private ComboBox typeComboBox;

    private TextArea descTextArea;

    private Boolean editSwModule = Boolean.FALSE;

    private Long baseSwModuleId;

    private FormLayout formLayout;

    /**
     * Constructor for SoftwareModuleAddUpdateWindow
     * 
     * @param i18n
     *            I18N
     * @param uiNotifcation
     *            UINotification
     * @param eventBus
     *            UIEventBus
     * @param softwareManagement
     *            SoftwareManagement
     * @param entityFactory
     *            EntityFactory
     */
    public SoftwareModuleAddUpdateWindow(final VaadinMessageSource i18n, final UINotification uiNotifcation, final UIEventBus eventBus,
            final SoftwareManagement softwareManagement, final EntityFactory entityFactory) {
        this.i18n = i18n;
        this.uiNotifcation = uiNotifcation;
        this.eventBus = eventBus;
        this.softwareManagement = softwareManagement;
        this.entityFactory = entityFactory;

        createRequiredComponents();
    }

    /**
     * Save or update the sw module.
     */
    private final class SaveOnDialogCloseListener implements SaveDialogCloseListener {
        @Override
        public void saveOrUpdate() {
            if (editSwModule) {
                updateSwModule();
                return;
            }
            addNewBaseSoftware();
        }

        @Override
        public boolean canWindowSaveOrUpdate() {
            return editSwModule || !isDuplicate();
        }

        private void addNewBaseSoftware() {
            final String name = HawkbitCommonUtil.trimAndNullIfEmpty(nameTextField.getValue());
            final String version = HawkbitCommonUtil.trimAndNullIfEmpty(versionTextField.getValue());
            final String vendor = HawkbitCommonUtil.trimAndNullIfEmpty(vendorTextField.getValue());
            final String description = HawkbitCommonUtil.trimAndNullIfEmpty(descTextArea.getValue());
            final String type = typeComboBox.getValue() != null ? typeComboBox.getValue().toString() : null;

            final SoftwareModuleType softwareModuleTypeByName = softwareManagement.findSoftwareModuleTypeByName(type)
                    .orElseThrow(() -> new EntityNotFoundException(SoftwareModuleType.class, type));
            final SoftwareModuleCreate softwareModule = entityFactory.softwareModule().create()
                    .type(softwareModuleTypeByName).name(name).version(version).description(description).vendor(vendor);

            final SoftwareModule newSoftwareModule = softwareManagement.createSoftwareModule(softwareModule);

            if (newSoftwareModule != null) {
                eventBus.publish(this, new SoftwareModuleEvent(BaseEntityEventType.ADD_ENTITY, newSoftwareModule));
                uiNotifcation.displaySuccess(i18n.getMessage("message.save.success",
                        new Object[] { newSoftwareModule.getName() + ":" + newSoftwareModule.getVersion() }));
            }
        }

        private boolean isDuplicate() {
            final String name = nameTextField.getValue();
            final String version = versionTextField.getValue();
            final String type = typeComboBox.getValue() != null ? typeComboBox.getValue().toString() : null;

            final Optional<Long> moduleType = softwareManagement.findSoftwareModuleTypeByName(type)
                    .map(SoftwareModuleType::getId);
            if (moduleType.isPresent() && softwareManagement
                    .findSoftwareModuleByNameAndVersion(name, version, moduleType.get()).isPresent()) {
                uiNotifcation.displayValidationError(
                        i18n.getMessage("message.duplicate.softwaremodule", new Object[] { name, version }));
                return true;
            }
            return false;
        }

        /**
         * updates a softwareModule
         */
        private void updateSwModule() {
            final SoftwareModule newSWModule = softwareManagement.updateSoftwareModule(entityFactory.softwareModule()
                    .update(baseSwModuleId).description(descTextArea.getValue()).vendor(vendorTextField.getValue()));
            if (newSWModule != null) {
                uiNotifcation.displaySuccess(i18n.getMessage("message.save.success",
                        new Object[] { newSWModule.getName() + ":" + newSWModule.getVersion() }));

                eventBus.publish(this, new SoftwareModuleEvent(BaseEntityEventType.UPDATED_ENTITY, newSWModule));
            }
        }
    }

    /**
     * Create window for new software module.
     * 
     * @return reference of {@link com.vaadin.ui.Window} to add new software
     *         module.
     */
    public CommonDialogWindow createAddSoftwareModuleWindow() {
        return createUpdateSoftwareModuleWindow(null);
    }

    /**
     * Create window for update software module.
     * 
     * @param baseSwModuleId
     *            is id of the software module to edit.
     * @return reference of {@link com.vaadin.ui.Window} to update software
     *         module.
     */
    public CommonDialogWindow createUpdateSoftwareModuleWindow(final Long baseSwModuleId) {
        this.baseSwModuleId = baseSwModuleId;
        resetComponents();
        populateTypeNameCombo();
        populateValuesOfSwModule();
        return createWindow();
    }

    private void createRequiredComponents() {

        nameTextField = createTextField("textfield.name", UIComponentIdProvider.SOFT_MODULE_NAME);

        versionTextField = createTextField("textfield.version", UIComponentIdProvider.SOFT_MODULE_VERSION);

        vendorTextField = createTextField("textfield.vendor", UIComponentIdProvider.SOFT_MODULE_VENDOR);
        vendorTextField.setRequired(false);
        vendorTextField.setNullRepresentation(StringUtils.EMPTY);

        descTextArea = new TextAreaBuilder().caption(i18n.getMessage("textfield.description")).style("text-area-style")
                .prompt(i18n.getMessage("textfield.description")).id(UIComponentIdProvider.ADD_SW_MODULE_DESCRIPTION)
                .buildTextComponent();
        descTextArea.setNullRepresentation(StringUtils.EMPTY);

        typeComboBox = SPUIComponentProvider.getComboBox(i18n.getMessage("upload.swmodule.type"), "", null, null, true, null,
                i18n.getMessage("upload.swmodule.type"));
        typeComboBox.setId(UIComponentIdProvider.SW_MODULE_TYPE);
        typeComboBox.setStyleName(SPUIDefinitions.COMBO_BOX_SPECIFIC_STYLE + " " + ValoTheme.COMBOBOX_TINY);
        typeComboBox.setNewItemsAllowed(Boolean.FALSE);
        typeComboBox.setImmediate(Boolean.TRUE);
    }

    private TextField createTextField(final String in18Key, final String id) {
        return new TextFieldBuilder().caption(i18n.getMessage(in18Key)).required(true).prompt(i18n.getMessage(in18Key))
                .immediate(true).id(id).buildTextComponent();
    }

    private void populateTypeNameCombo() {
        typeComboBox.setContainerDataSource(
                HawkbitCommonUtil.createLazyQueryContainer(new BeanQueryFactory<>(SoftwareModuleTypeBeanQuery.class)));
        typeComboBox.setItemCaptionPropertyId(SPUILabelDefinitions.VAR_NAME);
    }

    private void resetComponents() {

        vendorTextField.clear();
        nameTextField.clear();
        versionTextField.clear();
        descTextArea.clear();
        typeComboBox.clear();
        editSwModule = Boolean.FALSE;
    }

    private CommonDialogWindow createWindow() {
        final Label madatoryStarLabel = new Label("*");
        madatoryStarLabel.setStyleName("v-caption v-required-field-indicator");
        madatoryStarLabel.setWidth(null);
        addStyleName("lay-color");
        setSizeUndefined();

        formLayout = new FormLayout();
        formLayout.setCaption(null);
        formLayout.addComponent(typeComboBox);
        formLayout.addComponent(nameTextField);
        formLayout.addComponent(versionTextField);
        formLayout.addComponent(vendorTextField);
        formLayout.addComponent(descTextArea);

        setCompositionRoot(formLayout);

        final CommonDialogWindow window = new WindowBuilder(SPUIDefinitions.CREATE_UPDATE_WINDOW)
                .caption(i18n.getMessage("upload.caption.add.new.swmodule")).content(this).layout(formLayout).i18n(i18n)
                .saveDialogCloseListener(new SaveOnDialogCloseListener()).buildCommonDialogWindow();
        nameTextField.setEnabled(!editSwModule);
        versionTextField.setEnabled(!editSwModule);
        typeComboBox.setEnabled(!editSwModule);

        typeComboBox.focus();

        return window;
    }

    /**
     * fill the data of a softwareModule in the content of the window
     */
    private void populateValuesOfSwModule() {
        if (baseSwModuleId == null) {
            return;
        }
        editSwModule = Boolean.TRUE;
        softwareManagement.findSoftwareModuleById(baseSwModuleId).ifPresent(swModule -> {
            nameTextField.setValue(swModule.getName());
            versionTextField.setValue(swModule.getVersion());
            vendorTextField.setValue(HawkbitCommonUtil.trimAndNullIfEmpty(swModule.getVendor()));
            descTextArea.setValue(HawkbitCommonUtil.trimAndNullIfEmpty(swModule.getDescription()));

            if (swModule.getType().isDeleted()) {
                typeComboBox.addItem(swModule.getType().getName());
            }
            typeComboBox.setValue(swModule.getType().getName());
        });
    }

    public FormLayout getFormLayout() {
        return formLayout;
    }

}
