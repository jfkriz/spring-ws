/*
 * Copyright 2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.oxm.jaxb;

import java.util.Collections;
import javax.xml.transform.sax.SAXResult;

import org.easymock.MockControl;
import org.springframework.oxm.AbstractMarshallerTest;
import org.springframework.oxm.Marshaller;
import org.springframework.oxm.XmlMappingException;
import org.springframework.oxm.jaxb.impl.FlightTypeImpl;
import org.springframework.oxm.jaxb.impl.FlightsImpl;
import org.xml.sax.ContentHandler;

public class JaxbMarshallerTest extends AbstractMarshallerTest {

    protected Marshaller createMarshaller() throws Exception {
        JaxbMarshaller marshaller = new JaxbMarshaller();
        marshaller.setContextPath("org.springframework.oxm.jaxb");
        marshaller.afterPropertiesSet();
        return marshaller;
    }

    protected Object createFlights() {
        FlightType flight = new FlightTypeImpl();
        flight.setNumber(42L);
        Flights flights = new FlightsImpl();
        flights.getFlight().add(flight);
        return flights;
    }

    public void testMarshalSaxResult() throws Exception {
        MockControl handlerControl = MockControl.createStrictControl(ContentHandler.class);
        ContentHandler handlerMock = (ContentHandler) handlerControl.getMock();
        handlerMock.setDocumentLocator(null);
        handlerControl.setMatcher(MockControl.ALWAYS_MATCHER);
        handlerMock.startDocument();
        handlerMock.startPrefixMapping("ns1", "http://samples.springframework.org/flight");
        handlerMock.startElement("http://samples.springframework.org/flight", "flights", "ns1:flights", null);
        handlerControl.setMatcher(MockControl.ALWAYS_MATCHER);
        handlerMock.startElement("http://samples.springframework.org/flight", "flight", "ns1:flight", null);
        handlerControl.setMatcher(MockControl.ALWAYS_MATCHER);
        handlerMock.startElement("http://samples.springframework.org/flight", "number", "ns1:number", null);
        handlerControl.setMatcher(MockControl.ALWAYS_MATCHER);
        handlerMock.characters(new char[]{'4', '2'}, 0, 2);
        handlerControl.setMatcher(MockControl.ARRAY_MATCHER);
        handlerMock.endElement("http://samples.springframework.org/flight", "number", "ns1:number");
        handlerMock.endElement("http://samples.springframework.org/flight", "flight", "ns1:flight");
        handlerMock.endElement("http://samples.springframework.org/flight", "flights", "ns1:flights");
        handlerMock.endPrefixMapping("ns1");
        handlerMock.endDocument();

        handlerControl.replay();
        SAXResult result = new SAXResult(handlerMock);
        marshaller.marshal(flights, result);
        handlerControl.verify();
    }

    public void testAfterPropertiesSetNoContextPath() throws Exception {
        try {
            JaxbMarshaller marshaller = new JaxbMarshaller();
            marshaller.afterPropertiesSet();
            fail("Should have thrown an IllegalArgumentException");
        }
        catch (IllegalArgumentException e) {
        }
    }

    public void testAfterPropertiesSet() throws Exception {
        try {
            JaxbMarshaller marshaller = new JaxbMarshaller();
            marshaller.setContextPath("ab");
            marshaller.afterPropertiesSet();
            fail("Should have thrown an XmlMappingException");
        }
        catch (XmlMappingException ex) {
        }
    }

    public void testProperties() throws Exception {
        JaxbMarshaller marshaller = new JaxbMarshaller();
        marshaller.setContextPath("org.springframework.oxm.jaxb");
        marshaller.setMarshallerProperties(
                Collections.singletonMap(javax.xml.bind.Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE));
        marshaller.afterPropertiesSet();
    }


}
