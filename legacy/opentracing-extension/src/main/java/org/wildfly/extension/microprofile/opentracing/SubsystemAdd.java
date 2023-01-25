/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2018, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.wildfly.extension.microprofile.opentracing;

import static org.wildfly.extension.microprofile.opentracing.SubsystemDefinition.DEFAULT_TRACER;
import static org.wildfly.extension.microprofile.opentracing.SubsystemDefinition.DEFAULT_TRACER_CAPABILITY;
import static org.wildfly.extension.microprofile.opentracing.SubsystemDefinition.OPENTRACING_CAPABILITY;

import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.dmr.ModelNode;

/**
 * OSH for adding the OpneTracing subsystem.
 * @author Emmanuel Hugonnet (c) 2019 Red Hat, Inc.
 */
class SubsystemAdd extends AbstractBoottimeAddStepHandler {

    static final SubsystemAdd INSTANCE = new SubsystemAdd();

    private SubsystemAdd() {
        super(DEFAULT_TRACER);
    }

    @Override
    protected void performBoottime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        TracingExtensionLogger.ROOT_LOGGER.activatingSubsystem();
        context.addStep(new AbstractDeploymentChainStep() {
            @Override
            public void execute(DeploymentProcessorTarget processorTarget) {
            }
        }, OperationContext.Stage.RUNTIME);
    }

    @Override
    protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        context.registerCapability(OPENTRACING_CAPABILITY);
        ModelNode defaultTracer = DEFAULT_TRACER.resolveModelAttribute(context, operation);
        if (defaultTracer.isDefined()) {
            context.registerCapability(DEFAULT_TRACER_CAPABILITY);
        }
    }
}
