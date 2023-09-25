/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.jaxb.unit;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.as.arquillian.api.ServerSetup;
import org.jboss.as.test.integration.security.common.AbstractSystemPropertiesServerSetupTask;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * <p>Test for JAXB using a System Property. The test will try using a
 * custom/fake implementation.</p>
 *
 * @author rmartinc
 */
@RunWith(Arquillian.class)
@ServerSetup({JAXBContextSystemPropCustomTestCase.SystemPropertiesSetup.class})
@RunAsClient
public class JAXBContextSystemPropCustomTestCase extends JAXBContextTestBase {

   /**
     * Setup the system property to configure the custom jaxb implementation.
     */
    static class SystemPropertiesSetup extends AbstractSystemPropertiesServerSetupTask {

        @Override
        protected SystemProperty[] getSystemProperties() {
            return new SystemProperty[] {
                new DefaultSystemProperty(JAVAX_FACTORY_PROP_NAME, CUSTOM_JAXB_FACTORY_CLASS),
                new DefaultSystemProperty(JAKARTA_FACTORY_PROP_NAME, CUSTOM_JAXB_FACTORY_CLASS)
            };
        }
    }

    @Deployment(name = "app-custom", testable = false)
    public static WebArchive createCustomDeployment() {
        return JAXBContextTestBase.createCustomDeployment();
    }

    @OperateOnDeployment("app-custom")
    @Test
    public void testCustom() throws Exception {
        testCustomImplementation(url);
    }
}
