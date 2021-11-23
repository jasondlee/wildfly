package org.wildfly.test.integration.observability.micrometer;

import javax.annotation.PostConstruct;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * @author <a href="mailto:jasondlee@redhat.com">Jason Lee</a>
 */
@RequestScoped
@Path("/")
public class MetricResource {
    @Inject
    private MeterRegistry meterRegistry;
    private Counter counter;
    private Timer timer;

    @PostConstruct
    public void setupMeters() {
        counter = meterRegistry.counter("demo_counter");
        timer = meterRegistry.timer("demo_timer");
    }

    @GET
    @Path("/")
    public double getCount() {
        Timer.Sample sample = Timer.start();
        try {
            Thread.sleep((long) (Math.random() * 1000L));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        counter.increment();
        sample.stop(timer);

        return counter.count();
    }
}
