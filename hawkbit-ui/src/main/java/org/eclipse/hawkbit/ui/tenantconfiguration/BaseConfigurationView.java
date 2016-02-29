package org.eclipse.hawkbit.ui.tenantconfiguration;

import java.util.ArrayList;
import java.util.List;

import com.vaadin.ui.CustomComponent;

/**
 * base class for all configuration views. This class implements the logic for
 * the handling of the
 * 
 */
public abstract class BaseConfigurationView extends CustomComponent implements ConfigurationGroup {

    private static final long serialVersionUID = 1L;

    private final List<ConfigurationItemChangeListener> configurationChangeListeners = new ArrayList<>();

    protected void notifyConfigurationChanged() {
        configurationChangeListeners.forEach(listener -> listener.configurationHasChanged());
    }

    @Override
    public void addChangeListener(final ConfigurationItemChangeListener listener) {
        configurationChangeListeners.add(listener);
    }

    @Override
    public boolean isUserInputValid() {
        // default return value is true, because often user can only choose from
        // different valid options.
        return true;
    }
}
