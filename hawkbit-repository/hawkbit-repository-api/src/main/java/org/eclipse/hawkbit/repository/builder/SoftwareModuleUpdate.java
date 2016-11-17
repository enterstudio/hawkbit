/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.repository.builder;

import org.eclipse.hawkbit.repository.model.SoftwareModule;

/**
 * Builder to update an existing {@link SoftwareModule} entry. Defines all
 * fields that can be updated.
 *
 */
public interface SoftwareModuleUpdate {

    /**
     * @param description
     *            for {@link SoftwareModule#getDescription()}
     * @return updated builder instance
     */
    SoftwareModuleUpdate description(String description);

    /**
     * @param vendor
     *            for {@link SoftwareModule#getVendor()}
     * @return updated builder instance
     */
    SoftwareModuleUpdate vendor(String vendor);
}
