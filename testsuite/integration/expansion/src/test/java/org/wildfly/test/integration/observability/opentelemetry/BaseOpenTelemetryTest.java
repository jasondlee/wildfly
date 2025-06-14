/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.integration.observability.opentelemetry;

import java.net.MalformedURLException;

import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.testcontainers.api.TestcontainersRequired;
import org.jboss.arquillian.testcontainers.api.Testcontainer;
import org.jboss.as.test.shared.CdiUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.observability.containers.OpenTelemetryCollectorContainer;
import org.jboss.as.test.shared.observability.signals.jaeger.JaegerResponse;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.runner.RunWith;
import org.wildfly.test.integration.observability.JaxRsActivator;
import org.wildfly.test.integration.observability.opentelemetry.application.OtelMetricResource;
import org.wildfly.test.integration.observability.opentelemetry.application.OtelService1;

@RunWith(Arquillian.class)
@TestcontainersRequired
public abstract class BaseOpenTelemetryTest {
    @Testcontainer
    protected OpenTelemetryCollectorContainer otelCollector;

    private static final String MP_CONFIG = "otel.sdk.disabled=false\n" +
            // Lower the interval from 60 seconds to 100 millis
            "otel.metric.export.interval=100";

    static WebArchive buildBaseArchive(String name) {
        return ShrinkWrap
            .create(WebArchive.class, name + ".war")
            .addClasses(
                BaseOpenTelemetryTest.class,
                JaxRsActivator.class,
                OtelService1.class,
                OtelMetricResource.class
            )
            .addPackage(JaegerResponse.class.getPackage())
            .addAsManifestResource(new StringAsset(MP_CONFIG), "microprofile-config.properties")
            .addAsWebInfResource(CdiUtils.createBeansXml(), "beans.xml")
            ;
    }

    protected String getDeploymentUrl(String deploymentName) throws MalformedURLException {
        return TestSuiteEnvironment.getHttpUrl() + "/" + deploymentName + "/";
    }
}
