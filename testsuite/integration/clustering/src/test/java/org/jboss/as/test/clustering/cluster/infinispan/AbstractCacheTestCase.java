/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.clustering.cluster.infinispan;

import static org.jboss.as.test.shared.PermissionUtils.createPermissionsXmlAsset;

import java.io.IOException;
import java.lang.reflect.ReflectPermission;
import java.net.URISyntaxException;
import java.net.URL;

import javax.management.MBeanPermission;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.CloseableHttpClient;
import org.infinispan.protostream.SerializationContextInitializer;
import org.jboss.arquillian.container.test.api.OperateOnDeployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.as.test.clustering.cluster.AbstractClusteringTestCase;
import org.jboss.as.test.clustering.cluster.infinispan.bean.Cache;
import org.jboss.as.test.clustering.cluster.infinispan.bean.InfinispanCacheSerializationContextInitializer;
import org.jboss.as.test.clustering.cluster.infinispan.servlet.InfinispanCacheServlet;
import org.jboss.as.test.clustering.single.web.SimpleWebTestCase;
import org.jboss.as.test.http.util.TestHttpClientUtils;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Validates marshallability of application specific cache entries.
 * @author Paul Ferraro
 */
@RunWith(Arquillian.class)
public abstract class AbstractCacheTestCase extends AbstractClusteringTestCase {

    protected static Archive<?> createDeployment(Class<? extends Cache> beanClass, String module) {
        WebArchive war = ShrinkWrap.create(WebArchive.class, beanClass.getSimpleName() + ".war");
        war.addPackage(InfinispanCacheServlet.class.getPackage());
        war.addPackage(Cache.class.getPackage());
        war.addClass(beanClass);
        war.setWebXML(SimpleWebTestCase.class.getPackage(), "web.xml");
        war.addAsResource(new StringAsset(String.format("Manifest-Version: 1.0\nDependencies: %s, org.infinispan.commons, org.infinispan.protostream\n", module)), "META-INF/MANIFEST.MF");
        war.addAsServiceProvider(SerializationContextInitializer.class.getName(), InfinispanCacheSerializationContextInitializer.class.getName() + "Impl");
        war.addAsManifestResource(createPermissionsXmlAsset(
                new MBeanPermission("-#-[-]", "queryNames"),
                new MBeanPermission("org.infinispan.*[org.wildfly.clustering.infinispan:*,type=*]", "registerMBean"),
                new ReflectPermission("suppressAccessChecks"),
                new RuntimePermission("accessDeclaredMembers"),
                new RuntimePermission("getClassLoader")
        ), "permissions.xml");
        return war;
    }

    @Test
    public void test(@ArquillianResource @OperateOnDeployment(DEPLOYMENT_1) URL baseURL1, @ArquillianResource @OperateOnDeployment(DEPLOYMENT_2) URL baseURL2) throws URISyntaxException, IOException {
        System.out.println(InfinispanCacheServlet.createURI(baseURL1, "foo", "bar"));
        try (CloseableHttpClient client = TestHttpClientUtils.promiscuousCookieHttpClient()) {
            try (CloseableHttpResponse response = client.execute(new HttpPut(InfinispanCacheServlet.createURI(baseURL1, "foo", "bar")))) {
                Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                Assert.assertFalse(response.containsHeader(InfinispanCacheServlet.RESULT));
            }

            try (CloseableHttpResponse response = client.execute(new HttpGet(InfinispanCacheServlet.createURI(baseURL2, "foo")))) {
                Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                Assert.assertTrue(response.containsHeader(InfinispanCacheServlet.RESULT));
                Assert.assertEquals("bar", response.getFirstHeader(InfinispanCacheServlet.RESULT).getValue());
            }

            try (CloseableHttpResponse response = client.execute(new HttpDelete(InfinispanCacheServlet.createURI(baseURL1, "foo")))) {
                Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                Assert.assertTrue(response.containsHeader(InfinispanCacheServlet.RESULT));
                Assert.assertEquals("bar", response.getFirstHeader(InfinispanCacheServlet.RESULT).getValue());
            }

            try (CloseableHttpResponse response = client.execute(new HttpGet(InfinispanCacheServlet.createURI(baseURL2, "foo")))) {
                Assert.assertEquals(HttpServletResponse.SC_OK, response.getStatusLine().getStatusCode());
                Assert.assertFalse(response.containsHeader(InfinispanCacheServlet.RESULT));
            }
        }
    }
}
