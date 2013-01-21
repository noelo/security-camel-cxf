package com.redhat.fuse.example.camel;

import com.redhat.fuse.example.*;
import org.apache.camel.CamelContext;
import org.apache.camel.test.junit4.CamelSpringTestSupport;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.interceptor.InFaultInterceptors;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.message.Message;
import org.apache.cxf.ws.security.wss4j.WSS4JOutInterceptor;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebServiceAuthenticateCustomerUsingRealmTest extends CamelSpringTestSupport {

    // should be the same address as we have in our route
    private static final String URL = "http://localhost:9191/training/WebService";

    protected CamelContext camel;
    protected JaxWsProxyFactoryBean factory;

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("/META-INF/spring/CamelContext2.xml");
    }


    @Override
    public boolean isCreateCamelContextPerClass() {
        return true;
    }

    @Before
    public void configure() throws Exception {
        String path = "simpleJaas.config";
        java.net.URL resource = this.getClass().getClassLoader().getResource(path);
        if (resource != null) {
            path = resource.getFile();
             System.setProperty("java.security.auth.login.config", path);
        }
    }

    protected CustomerService createCXFClient(String url, String user) {

        List<Interceptor<? extends Message>> outInterceptors = new ArrayList<Interceptor<? extends Message>>();
        List<Interceptor<? extends Message>> inInterceptors = new ArrayList<Interceptor<? extends Message>>();

        // Define WSS4j properties for flow outgoing
        Map<String, Object> outProps = new HashMap<String, Object>();
        outProps.put("action", "UsernameToken Timestamp");

        // CONFIG WITH CLEAR PASSWORD
        outProps.put("passwordType", "PasswordText");
        outProps.put("user", user);
        outProps.put("passwordCallbackClass", "com.redhat.fuse.example.jaas.UTPasswordCallback");

        WSS4JOutInterceptor wss4j = new WSS4JOutInterceptor(outProps);

        // Add LoggingOutInterceptor
        LoggingOutInterceptor loggingOutInterceptor = new LoggingOutInterceptor();

        // Add LoggingInInterceptor
        LoggingInInterceptor loggingInInterceptor = new LoggingInInterceptor();

        outInterceptors.add(wss4j);
        outInterceptors.add(loggingOutInterceptor);
        inInterceptors.add(loggingInInterceptor);

        // We use CXF to create a client for us as its easier than JAXWS and works
        factory = new JaxWsProxyFactoryBean();
        factory.setOutInterceptors(outInterceptors);
        factory.setServiceClass(CustomerService.class);
        factory.setAddress(url);
        return (CustomerService) factory.create();
    }

    @Test
    public void testGetCustomerByNameAuthorized() throws Exception {

        String company = "Fuse";
        String user = "charles";

        // Create Get Customer By Name
        GetCustomerByName req = new GetCustomerByName();
        req.setName(company);

        // create the webservice client and send the request
        String url = context.resolvePropertyPlaceholders(URL);
        CustomerService customerService = createCXFClient(url,user);

        GetCustomerByNameResponse result = customerService.getCustomerByName(req);

        // Assert get Fuse customer
        assertEquals("Fuse", result.getReturn().get(0).getName());
        assertEquals("FuseSource Office", result.getReturn().get(0).getAddress().get(0));
        assertEquals(CustomerType.BUSINESS, result.getReturn().get(0).getType());

        // SetDefaultBus to null to avoid issue
        // when within same JVM we run different CXF
        // tests using Spring Beans
        SpringBusFactory.setDefaultBus(null);


    }

    @Test
    public void testGetCustomerByNameNotAuthorized() throws Exception {

        String company = "Fuse";
        String user = "jim";

        // Create Get Customer By Name
        GetCustomerByName req = new GetCustomerByName();
        req.setName(company);

        // create the webservice client and send the request
        String url = context.resolvePropertyPlaceholders(URL);
        CustomerService customerService = createCXFClient(url,user);

        // ADd additional classes
        Map<String, Object> props = new HashMap<String, Object>();
        props.put("jaxb.additionalContextClasses",
                new Class[] {NoSuchCustomerFault.class, NotAuthorizedUserFault.class});
        factory.setProperties(props);

        Throwable t = null;
        try {
            GetCustomerByNameResponse result = customerService.getCustomerByName(req);
            fail("expect NotAuthorizedUserException");
        } catch (NotAuthorizedUserFault e) {
            t = e;
            assertEquals("Not Authorized user : ", "jim", e.getFaultInfo().getUser());
        }

        assertNotNull(t);
        assertTrue(t instanceof NotAuthorizedUserFault);

        // SetDefaultBus to null to avoid issue
        // when within same JVM we run different CXF
        // tests using Spring Beans
        SpringBusFactory.setDefaultBus(null);

    }
}
