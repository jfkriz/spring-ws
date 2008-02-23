/*
 * Copyright 2007 the original author or authors.
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

package org.springframework.ws.soap.addressing;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import javax.xml.namespace.QName;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.springframework.util.StringUtils;
import org.springframework.ws.soap.SoapFault;
import org.springframework.ws.soap.SoapHeader;
import org.springframework.ws.soap.SoapHeaderElement;
import org.springframework.ws.soap.SoapMessage;
import org.springframework.ws.soap.soap11.Soap11Body;
import org.springframework.ws.soap.soap12.Soap12Body;
import org.springframework.ws.soap.soap12.Soap12Fault;
import org.springframework.xml.namespace.QNameUtils;
import org.springframework.xml.transform.TransformerObjectSupport;
import org.springframework.xml.xpath.XPathExpression;
import org.springframework.xml.xpath.XPathExpressionFactory;

/**
 * Abstract base class for {@link WsAddressingVersion} implementations. Uses {@link XPathExpression}s to retrieve
 * addressing information.
 *
 * @author Arjen Poutsma
 * @since 1.5.0
 */
public abstract class AbstractWsAddressingVersion extends TransformerObjectSupport implements WsAddressingVersion {

    private final XPathExpression toExpression;

    private final XPathExpression actionExpression;

    private final XPathExpression messageIdExpression;

    private final XPathExpression fromExpression;

    private final XPathExpression replyToExpression;

    private final XPathExpression faultToExpression;

    private final XPathExpression addressExpression;

    private final XPathExpression referencePropertiesExpression;

    private final XPathExpression referenceParametersExpression;

    protected AbstractWsAddressingVersion() {
        Properties namespaces = new Properties();
        namespaces.setProperty(getNamespacePrefix(), getNamespaceUri());
        toExpression = createNormalizedExpression(getToName(), namespaces);
        actionExpression = createNormalizedExpression(getActionName(), namespaces);
        messageIdExpression = createNormalizedExpression(getMessageIdName(), namespaces);
        fromExpression = createExpression(getFromName(), namespaces);
        replyToExpression = createExpression(getReplyToName(), namespaces);
        faultToExpression = createExpression(getFaultToName(), namespaces);
        addressExpression = createNormalizedExpression(getAddressName(), namespaces);
        if (getReferencePropertiesName() != null) {
            referencePropertiesExpression = createChildrenExpression(getReferencePropertiesName(), namespaces);
        }
        else {
            referencePropertiesExpression = null;
        }
        if (getReferenceParametersName() != null) {
            referenceParametersExpression = createChildrenExpression(getReferenceParametersName(), namespaces);
        }
        else {
            referenceParametersExpression = null;
        }
    }

    private XPathExpression createExpression(QName name, Properties namespaces) {
        String expression = name.getPrefix() + ":" + name.getLocalPart();
        return XPathExpressionFactory.createXPathExpression(expression, namespaces);
    }

    private XPathExpression createNormalizedExpression(QName name, Properties namespaces) {
        String expression = "normalize-space(" + name.getPrefix() + ":" + name.getLocalPart() + ")";
        return XPathExpressionFactory.createXPathExpression(expression, namespaces);
    }

    private XPathExpression createChildrenExpression(QName name, Properties namespaces) {
        String expression = name.getPrefix() + ":" + name.getLocalPart() + "/*";
        return XPathExpressionFactory.createXPathExpression(expression, namespaces);
    }

    public MessageAddressingProperties getMessageAddressingProperties(SoapMessage message) {
        Element headerElement = getSoapHeaderElement(message);
        URI to = getUri(headerElement, toExpression);
        EndpointReference from = getEndpointReference(fromExpression.evaluateAsNode(headerElement));
        EndpointReference replyTo = getEndpointReference(replyToExpression.evaluateAsNode(headerElement));
        if (replyTo == null && getAnonymous() != null) {
            replyTo = getDefaultReplyTo(from);
        }
        EndpointReference faultTo = getEndpointReference(faultToExpression.evaluateAsNode(headerElement));
        if (faultTo == null) {
            faultTo = replyTo;
        }
        URI action = getUri(headerElement, actionExpression);
        URI messageId = getUri(headerElement, messageIdExpression);
        return new MessageAddressingProperties(to, from, replyTo, faultTo, action, messageId);
    }

    private URI getUri(Node node, XPathExpression expression) {
        String messageId = expression.evaluateAsString(node);
        if (!StringUtils.hasLength(messageId)) {
            return null;
        }
        try {
            return new URI(messageId);
        }
        catch (URISyntaxException e) {
            return null;
        }
    }

    private Element getSoapHeaderElement(SoapMessage message) {
        SoapHeader header = message.getSoapHeader();
        if (header.getSource() instanceof DOMSource) {
            DOMSource domSource = (DOMSource) header.getSource();
            if (domSource.getNode() != null && domSource.getNode().getNodeType() == Node.ELEMENT_NODE) {
                return (Element) domSource.getNode();
            }
        }
        try {
            DOMResult domResult = new DOMResult();
            transform(header.getSource(), domResult);
            Document document = (Document) domResult.getNode();
            return document.getDocumentElement();
        }
        catch (TransformerException ex) {
            throw new WsAddressingException("Could not transform SoapHeader to Document", ex);
        }
    }

    /** Given a ReplyTo, FaultTo, or From node, returns an endpoint reference. */
    private EndpointReference getEndpointReference(Node node) {
        if (node == null) {
            return null;
        }
        URI address = getUri(node, addressExpression);
        if (address == null) {
            return null;
        }
        List referenceProperties = referencePropertiesExpression != null ?
                referencePropertiesExpression.evaluateAsNodeList(node) : Collections.EMPTY_LIST;
        List referenceParameters = referenceParametersExpression != null ?
                referenceParametersExpression.evaluateAsNodeList(node) : Collections.EMPTY_LIST;
        return new EndpointReference(address, referenceProperties, referenceParameters);
    }

    public final boolean understands(SoapHeaderElement headerElement) {
        return getNamespaceUri().equals(headerElement.getName().getNamespaceURI());
    }

    public final void addAddressingHeaders(SoapMessage message, MessageAddressingProperties map) {
        SoapHeader header = message.getSoapHeader();
        SoapHeaderElement messageId = header.addHeaderElement(getMessageIdName());
        messageId.setText(map.getMessageId().toString());
        SoapHeaderElement relatesTo = header.addHeaderElement(getRelatesToName());
        relatesTo.setText(map.getRelatesTo().toString());
        SoapHeaderElement to = header.addHeaderElement(getToName());
        to.setText(map.getTo().toString());
        to.setMustUnderstand(true);
        if (map.getAction() != null) {
            SoapHeaderElement action = header.addHeaderElement(getActionName());
            action.setText(map.getAction().toString());
        }
        try {
            Transformer transformer = createTransformer();
            for (Iterator iterator = map.getReferenceParameters().iterator(); iterator.hasNext();) {
                Node node = (Node) iterator.next();
                DOMSource source = new DOMSource(node);
                transformer.transform(source, header.getResult());
            }
            for (Iterator iterator = map.getReferenceProperties().iterator(); iterator.hasNext();) {
                Node node = (Node) iterator.next();
                DOMSource source = new DOMSource(node);
                transformer.transform(source, header.getResult());
            }
        }
        catch (TransformerException ex) {
            throw new WsAddressingException("Could not add reference properties/parameters to message", ex);
        }
    }

    public final SoapFault addInvalidAddressingHeaderFault(SoapMessage message) {
        return addAddressingFault(message, getInvalidAddressingHeaderFaultSubcode(),
                getInvalidAddressingHeaderFaultReason());
    }

    public final SoapFault addMessageAddressingHeaderRequiredFault(SoapMessage message) {
        return addAddressingFault(message, getMessageAddressingHeaderRequiredFaultSubcode(),
                getMessageAddressingHeaderRequiredFaultReason());
    }

    private SoapFault addAddressingFault(SoapMessage message, QName subcode, String reason) {
        if (message.getSoapBody() instanceof Soap11Body) {
            Soap11Body soapBody = (Soap11Body) message.getSoapBody();
            return soapBody.addFault(subcode, reason, Locale.ENGLISH);
        }
        else if (message.getSoapBody() instanceof Soap12Body) {
            Soap12Body soapBody = (Soap12Body) message.getSoapBody();
            Soap12Fault soapFault = (Soap12Fault) soapBody.addClientOrSenderFault(reason, Locale.ENGLISH);
            soapFault.addFaultSubcode(subcode);
            return soapFault;
        }
        return null;
    }

    /*
    * Address URIs
    */

    public final boolean hasAnonymousAddress(EndpointReference epr) {
        URI anonymous = getAnonymous();
        return anonymous != null && anonymous.equals(epr.getAddress());
    }

    public final boolean hasNoneAddress(EndpointReference epr) {
        URI none = getNone();
        return none != null && none.equals(epr.getAddress());
    }

    /** Returns the prefix associated with the WS-Addressing namespace handled by this specification. */
    protected String getNamespacePrefix() {
        return "wsa";
    }

    /** Returns the WS-Addressing namespace handled by this specification. */
    protected abstract String getNamespaceUri();

    /*
     * Message addressing properties
     */

    /** Returns the qualified name of the <code>To</code> addressing header. */
    protected QName getToName() {
        return QNameUtils.createQName(getNamespaceUri(), "To", getNamespacePrefix());
    }

    /** Returns the qualified name of the <code>From</code> addressing header. */
    protected QName getFromName() {
        return QNameUtils.createQName(getNamespaceUri(), "From", getNamespacePrefix());
    }

    /** Returns the qualified name of the <code>ReplyTo</code> addressing header. */
    protected QName getReplyToName() {
        return QNameUtils.createQName(getNamespaceUri(), "ReplyTo", getNamespacePrefix());
    }

    /** Returns the qualified name of the <code>FaultTo</code> addressing header. */
    protected QName getFaultToName() {
        return QNameUtils.createQName(getNamespaceUri(), "FaultTo", getNamespacePrefix());
    }

    /** Returns the qualified name of the <code>Action</code> addressing header. */
    protected QName getActionName() {
        return QNameUtils.createQName(getNamespaceUri(), "Action", getNamespacePrefix());
    }

    /** Returns the qualified name of the <code>MessageID</code> addressing header. */
    protected QName getMessageIdName() {
        return QNameUtils.createQName(getNamespaceUri(), "MessageID", getNamespacePrefix());
    }

    /** Returns the qualified name of the <code>RelatesTo</code> addressing header. */
    protected QName getRelatesToName() {
        return QNameUtils.createQName(getNamespaceUri(), "RelatesTo", getNamespacePrefix());
    }

    /**
     * Returns the qualified name of the <code>ReferenceProperties</code> in the endpoint reference. Returns
     * <code>null</code> when reference properties are not supported by this version of the spec.
     */
    protected QName getReferencePropertiesName() {
        return QNameUtils.createQName(getNamespaceUri(), "ReferenceProperties", getNamespacePrefix());
    }

    /**
     * Returns the qualified name of the <code>ReferenceParameters</code> in the endpoint reference. Returns
     * <code>null</code> when reference parameters are not supported by this version of the spec.
     */
    protected QName getReferenceParametersName() {
        return QNameUtils.createQName(getNamespaceUri(), "ReferenceParameters", getNamespacePrefix());
    }

    /*
     * Endpoint Reference
     */

    /** The qualified name of the <code>Address</code> in <code>EndpointReference</code>. */
    protected QName getAddressName() {
        return QNameUtils.createQName(getNamespaceUri(), "Address", getNamespacePrefix());
    }

    /** Returns the default ReplyTo EPR. Can be based on the From EPR, or the anonymous URI. */
    protected abstract EndpointReference getDefaultReplyTo(EndpointReference from);

    /*
     * Address URIs
     */

    /** Returns the anonymous URI. */
    protected abstract URI getAnonymous();

    /** Returns the none URI, or <code>null</code> if the spec does not define it. */
    protected abstract URI getNone();

    /*
     * Faults
     */

    /** Returns the qualified name of the fault subcode that indicates that a header is missing. */
    protected abstract QName getMessageAddressingHeaderRequiredFaultSubcode();

    /** Returns the reason of the fault that indicates that a header is missing. */
    protected abstract String getMessageAddressingHeaderRequiredFaultReason();

    /** Returns the qualified name of the fault subcode that indicates that a header is invalid. */
    protected abstract QName getInvalidAddressingHeaderFaultSubcode();

    /** Returns the reason of the fault that indicates that a header is invalid. */
    protected abstract String getInvalidAddressingHeaderFaultReason();

    public String toString() {
        return getNamespaceUri();
    }
}