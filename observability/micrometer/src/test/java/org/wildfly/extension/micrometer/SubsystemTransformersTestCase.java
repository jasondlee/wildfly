/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.micrometer;

import static org.jboss.as.weld.Capabilities.WELD_CAPABILITY_NAME;

import org.jboss.as.controller.ModelVersion;
import org.jboss.as.model.test.ModelFixer;
import org.jboss.as.model.test.ModelTestControllerVersion;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.junit.Assert;
import org.junit.Test;

/**
 * Mixed domain transformation testing for the Micrometer subsystem
 */
public class SubsystemTransformersTestCase extends AbstractSubsystemTest {

    public SubsystemTransformersTestCase() {
        super(MicrometerConfigurationConstants.NAME, new MicrometerExtension());
    }

    @Test
    public void testTransformerEAPXP4() throws Exception {
        testTransformation(ModelTestControllerVersion.EAP_XP_4);
    }

    @Test
    public void testTransformerEAPXP5() throws Exception {
        testTransformation(ModelTestControllerVersion.EAP_XP_5);
    }

    private void testTransformation(final ModelTestControllerVersion controller) throws Exception {
        final ModelVersion version = getModelVersion(controller).getVersion();
        final String subsystemXmlResource = String.format("transform-%d_%d_%d.xml", version.getMajor(), version.getMinor(), version.getMicro());

        KernelServices services = this.buildKernelServices(readResource(subsystemXmlResource), controller, version, getDependencies(controller));

        // check that both versions of the legacy model are the same and valid
        checkSubsystemModelTransformation(services, version);
    }

    private KernelServices buildKernelServices(String xml, ModelTestControllerVersion controllerVersion,
                                               ModelVersion version, String... mavenResourceURLs) throws Exception {
        KernelServicesBuilder builder = this.createKernelServicesBuilder(createAdditionalInitialization()).setSubsystemXml(xml);

        builder.createLegacyKernelServicesBuilder(createAdditionalInitialization(), controllerVersion, version)
                .addMavenResourceURL(mavenResourceURLs)
                .skipReverseControllerCheck()
                .dontPersistXml();

        KernelServices services = builder.build();
        Assert.assertTrue(ModelTestControllerVersion.MASTER + " boot failed", services.isSuccessfulBoot());
        Assert.assertTrue(controllerVersion.getMavenGavVersion() + " boot failed", services.getLegacyServices(version).isSuccessfulBoot());
        return services;
    }

    private AdditionalInitialization createAdditionalInitialization() {
        return AdditionalInitialization.withCapabilities(WELD_CAPABILITY_NAME);
    }

    private static MicrometerSubsystemModel getModelVersion(ModelTestControllerVersion controllerVersion) {
        switch (controllerVersion) {
            case EAP_XP_4-> {
                return MicrometerSubsystemModel.VERSION_1_0_0;
            }
            case EAP_XP_5 -> {
                return MicrometerSubsystemModel.VERSION_1_1_0;
            }
        }
        throw new IllegalArgumentException();
    }

    private static String[] getDependencies(ModelTestControllerVersion version) {
        return new String[]{
            String.format("%s:wildfly-micrometer:%s", version.getMavenGroupId(), version.getMavenGavVersion())
        };
    }

    private static ModelFixer createModelFixer(ModelVersion version) {
        return model -> {
            if (MicrometerSubsystemModel.VERSION_1_1_0.requiresTransformation(version)) {
                // The transformer will turn 'undefined' on the DC to 'otlp' on the legacy HC, so confirm that was done
                // and then switch it back to undefined so the comparison to DC passes
//                Assert.assertEquals(model.toString(), EXPORTER_OTLP, model.get(EXPORTER_TYPE).asString());
//                model.get(EXPORTER_TYPE).set(ModelType.UNDEFINED);
            }
            return model;
        };
    }
}
