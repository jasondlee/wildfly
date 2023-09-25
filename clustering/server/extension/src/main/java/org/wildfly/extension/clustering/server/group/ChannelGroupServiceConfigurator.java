/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.clustering.server.group;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jboss.as.clustering.controller.CapabilityServiceConfigurator;
import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.clustering.dispatcher.CommandDispatcherFactory;
import org.wildfly.clustering.group.Group;
import org.wildfly.clustering.server.service.ClusteringRequirement;
import org.wildfly.clustering.service.FunctionalService;
import org.wildfly.clustering.service.ServiceConfigurator;
import org.wildfly.clustering.service.ServiceSupplierDependency;
import org.wildfly.clustering.service.SimpleServiceNameProvider;
import org.wildfly.clustering.service.SupplierDependency;

/**
 * Builds a channel-based {@link Group} service.
 * @author Paul Ferraro
 */
public class ChannelGroupServiceConfigurator extends SimpleServiceNameProvider implements CapabilityServiceConfigurator, Supplier<Group> {

    private final String group;

    private volatile SupplierDependency<CommandDispatcherFactory> factory;

    public ChannelGroupServiceConfigurator(ServiceName name, String group) {
        super(name);
        this.group = group;
    }

    @Override
    public Group get() {
        return this.factory.get().getGroup();
    }

    @Override
    public ServiceConfigurator configure(CapabilityServiceSupport support) {
        this.factory = new ServiceSupplierDependency<>(ClusteringRequirement.COMMAND_DISPATCHER_FACTORY.getServiceName(support, this.group));
        return this;
    }

    @Override
    public ServiceBuilder<?> build(ServiceTarget target) {
        ServiceBuilder<?> builder = target.addService(this.getServiceName());
        Consumer<Group> group = this.factory.register(builder).provides(this.getServiceName());
        Service service = new FunctionalService<>(group, Function.identity(), this);
        return builder.setInstance(service).setInitialMode(ServiceController.Mode.ON_DEMAND);
    }
}
