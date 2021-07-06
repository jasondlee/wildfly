/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2021 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.micrometer;

import java.util.Arrays;
import java.util.Collection;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;

public class MicrometerSubsystemDefinition extends PersistentResourceDefinition {
    static final String HTTP_EXTENSIBILITY_CAPABILITY = "org.wildfly.management.http.extensible";
    public static final String MICROMETER_HTTP_SECURITY_CAPABILITY = "org.wildfly.extension.micrometer.http-context.security-enabled";

    static final RuntimeCapability<Void> MICROMETER_HTTP_CONTEXT_CAPABILITY = RuntimeCapability.Builder
            .of("org.wildfly.extension.micrometer.http-context", MicrometerContextService.class)
//            .addRequirements(HTTP_EXTENSIBILITY_CAPABILITY)
            .build();

    public static final String[] MODULES = {
    };

    public static final String[] EXPORTED_MODULES = {
            "org.wildfly.extension.micrometer-extension"
    };

    static final AttributeDefinition[] ATTRIBUTES = {
    };

    public static final MicrometerSubsystemDefinition INSTANCE = new MicrometerSubsystemDefinition();

    protected MicrometerSubsystemDefinition() {

        super(new SimpleResourceDefinition.Parameters(MicrometerSubsystemExtension.SUBSYSTEM_PATH,
                MicrometerSubsystemExtension.getResourceDescriptionResolver(MicrometerSubsystemExtension.SUBSYSTEM_NAME))
                .setAddHandler(MicrometerSubsystemAdd.INSTANCE)
                .setRemoveHandler(MicrometerSubsystemRemove.INSTANCE)
                .addCapabilities(MICROMETER_HTTP_CONTEXT_CAPABILITY));

    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Arrays.asList(ATTRIBUTES);
    }
}
