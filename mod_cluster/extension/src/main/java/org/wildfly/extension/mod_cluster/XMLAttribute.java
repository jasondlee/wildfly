/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.mod_cluster;

import java.util.HashMap;
import java.util.Map;

import org.jboss.as.clustering.controller.Attribute;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;

/**
 * Enumeration of XML attributes used solely by {@link ModClusterSubsystemXMLReader}.
 *
 * @author Emanuel Muckenhuber
 * @author Jean-Frederic Clere
 * @author Radoslav Husar
 */
enum XMLAttribute {
    UNKNOWN((String) null),

    // Proxy configuration
    ADVERTISE(ProxyConfigurationResourceDefinition.Attribute.ADVERTISE),
    ADVERTISE_SECURITY_KEY(ProxyConfigurationResourceDefinition.Attribute.ADVERTISE_SECURITY_KEY),
    ADVERTISE_SOCKET(ProxyConfigurationResourceDefinition.Attribute.ADVERTISE_SOCKET),
    AUTO_ENABLE_CONTEXTS(ProxyConfigurationResourceDefinition.Attribute.AUTO_ENABLE_CONTEXTS),
    BALANCER(ProxyConfigurationResourceDefinition.Attribute.BALANCER),
    CONNECTOR("connector"),
    DOMAIN("domain"),
    EXCLUDED_CONTEXTS(ProxyConfigurationResourceDefinition.Attribute.EXCLUDED_CONTEXTS),
    FLUSH_PACKETS(ProxyConfigurationResourceDefinition.Attribute.FLUSH_PACKETS),
    FLUSH_WAIT(ProxyConfigurationResourceDefinition.Attribute.FLUSH_WAIT),
    LISTENER(ProxyConfigurationResourceDefinition.Attribute.LISTENER),
    LOAD_BALANCING_GROUP(ProxyConfigurationResourceDefinition.Attribute.LOAD_BALANCING_GROUP),
    MAX_ATTEMPTS(ProxyConfigurationResourceDefinition.Attribute.MAX_ATTEMPTS),
    NAME(ModelDescriptionConstants.NAME),
    NODE_TIMEOUT(ProxyConfigurationResourceDefinition.Attribute.NODE_TIMEOUT),
    PING(ProxyConfigurationResourceDefinition.Attribute.PING),
    PROXIES(ProxyConfigurationResourceDefinition.Attribute.PROXIES),
    PROXY_LIST("proxy-list"),
    PROXY_URL(ProxyConfigurationResourceDefinition.Attribute.PROXY_URL),
    SESSION_DRAINING_STRATEGY(ProxyConfigurationResourceDefinition.Attribute.SESSION_DRAINING_STRATEGY),
    SMAX(ProxyConfigurationResourceDefinition.Attribute.SMAX),
    SOCKET_TIMEOUT(ProxyConfigurationResourceDefinition.Attribute.SOCKET_TIMEOUT),
    SSL_CONTEXT(ProxyConfigurationResourceDefinition.Attribute.SSL_CONTEXT),
    STATUS_INTERVAL(ProxyConfigurationResourceDefinition.Attribute.STATUS_INTERVAL),
    STICKY_SESSION(ProxyConfigurationResourceDefinition.Attribute.STICKY_SESSION),
    STICKY_SESSION_FORCE(ProxyConfigurationResourceDefinition.Attribute.STICKY_SESSION_FORCE),
    STICKY_SESSION_REMOVE(ProxyConfigurationResourceDefinition.Attribute.STICKY_SESSION_REMOVE),
    STOP_CONTEXT_TIMEOUT(ProxyConfigurationResourceDefinition.Attribute.STOP_CONTEXT_TIMEOUT),
    TTL(ProxyConfigurationResourceDefinition.Attribute.TTL),
    WORKER_TIMEOUT(ProxyConfigurationResourceDefinition.Attribute.WORKER_TIMEOUT),

    // Load provider
    DECAY(DynamicLoadProviderResourceDefinition.Attribute.DECAY),
    FACTOR(SimpleLoadProviderResourceDefinition.Attribute.FACTOR),
    HISTORY(DynamicLoadProviderResourceDefinition.Attribute.HISTORY),
    INITIAL_LOAD(DynamicLoadProviderResourceDefinition.Attribute.INITIAL_LOAD),

    // Load metrics
    CAPACITY(LoadMetricResourceDefinition.SharedAttribute.CAPACITY),
    CLASS(CustomLoadMetricResourceDefinition.Attribute.CLASS),
    MODULE(CustomLoadMetricResourceDefinition.Attribute.MODULE),
    TYPE(LoadMetricResourceDefinition.Attribute.TYPE),
    WEIGHT(LoadMetricResourceDefinition.SharedAttribute.WEIGHT),
    ;

    private final String name;

    XMLAttribute(String name) {
        this.name = name;
    }

    XMLAttribute(Attribute attribute) {
        this.name = attribute.getName();
    }

    public String getLocalName() {
        return name;
    }

    private static final Map<String, XMLAttribute> MAP;

    static {
        Map<String, XMLAttribute> map = new HashMap<>(XMLAttribute.values().length);
        for (XMLAttribute element : values()) {
            String name = element.getLocalName();
            if (name != null) {
                map.put(name, element);
            }
        }
        MAP = map;
    }

    public static XMLAttribute forName(String localName) {
        XMLAttribute element = MAP.get(localName);
        return element == null ? UNKNOWN : element;
    }

    @Override
    public String toString() {
        return this.getLocalName();
    }
}
