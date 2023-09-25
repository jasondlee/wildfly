/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.smoke.webservices;

import java.net.URL;

import javax.xml.namespace.QName;
import jakarta.xml.ws.Service;
import jakarta.xml.ws.WebEndpoint;
import jakarta.xml.ws.WebServiceClient;
import jakarta.xml.ws.WebServiceFeature;

/**
 * This class was generated by Apache CXF 2.3.1
 * Mon Mar 14 19:07:07 BRT 2011
 * Generated source version: 2.3.1
 *
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 */
@WebServiceClient(name = "EndpointService",
                  wsdlLocation = "META-INF/wsdl/EndpointService.wsdl",//"http://localhost:8080/ws-example?wsdl",
                  targetNamespace = "http://webservices.smoke.test.as.jboss.org/")
public class EndpointService extends Service {

    public static final QName SERVICE = new QName("http://archive.ws.demos.as.jboss.org/", "EndpointService");
    public static final QName EndpointPort = new QName("http://archive.ws.demos.as.jboss.org/", "EndpointPort");


    public EndpointService(URL wsdlLocation) {
        super(wsdlLocation, SERVICE);
    }

    public EndpointService(URL wsdlLocation, QName serviceName) {
        super(wsdlLocation, serviceName);
    }

    public EndpointService() {
        super(null, SERVICE);
    }

    @WebEndpoint(name = "EndpointPort")
    public Endpoint getEndpointPort() {
        return super.getPort(EndpointPort, Endpoint.class);
    }

    @WebEndpoint(name = "EndpointPort")
    public Endpoint getEndpointPort(WebServiceFeature... features) {
        return super.getPort(EndpointPort, Endpoint.class, features);
    }

}
