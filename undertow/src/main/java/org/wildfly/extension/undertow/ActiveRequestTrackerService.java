package org.wildfly.extension.undertow;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import io.undertow.predicate.Predicate;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.ActiveRequestTrackerHandler;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

public class ActiveRequestTrackerService implements Service<ActiveRequestTrackerService> {
    private final Consumer<ActiveRequestTrackerService> serviceConsumer;
    private final Supplier<Host> host;
    private final Predicate predicate;
    private ActiveRequestTrackerHandler handler;

    public ActiveRequestTrackerService(Consumer<ActiveRequestTrackerService> serviceConsumer,
                                       Supplier<Host> host,
                                       final Predicate predicate) {
        this.serviceConsumer = serviceConsumer;
        this.host = host;
        this.predicate = predicate;
    }

    @Override
    public void start(StartContext startContext) throws StartException {
        final Host host = this.host.get();
        host.setActiveRequestTrackerHandler(new Function<HttpHandler, HttpHandler>() {
            @Override
            public HttpHandler apply(final HttpHandler httpHandler) {
                handler = new ActiveRequestTrackerHandler(httpHandler, predicate);
                return handler;
            }
        });

        serviceConsumer.accept(this);
    }

    @Override
    public void stop(StopContext stopContext) {

    }

    @Override
    public ActiveRequestTrackerService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public ActiveRequestTrackerHandler getHandler() {
        return handler;
    }
}
