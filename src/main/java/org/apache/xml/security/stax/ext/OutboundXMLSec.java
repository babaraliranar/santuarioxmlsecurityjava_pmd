/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.xml.security.stax.ext;

import java.io.OutputStream;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.List;

import javax.crypto.KeyGenerator;
import javax.xml.stream.XMLStreamWriter;

import org.apache.xml.security.exceptions.XMLSecurityException;
import org.apache.xml.security.stax.config.JCEAlgorithmMapper;
import org.apache.xml.security.stax.impl.DocumentContextImpl;
import org.apache.xml.security.stax.impl.OutboundSecurityContextImpl;
import org.apache.xml.security.stax.impl.OutputProcessorChainImpl;
import org.apache.xml.security.stax.impl.XMLSecurityStreamWriter;
import org.apache.xml.security.stax.impl.processor.output.FinalOutputProcessor;
import org.apache.xml.security.stax.impl.processor.output.XMLEncryptOutputProcessor;
import org.apache.xml.security.stax.impl.processor.output.XMLSignatureOutputProcessor;
import org.apache.xml.security.stax.impl.securityToken.GenericOutboundSecurityToken;
import org.apache.xml.security.stax.impl.util.IDGenerator;
import org.apache.xml.security.stax.securityEvent.SecurityEventListener;
import org.apache.xml.security.stax.securityToken.OutboundSecurityToken;
import org.apache.xml.security.stax.securityToken.SecurityTokenConstants;
import org.apache.xml.security.stax.securityToken.SecurityTokenProvider;

/**
 * Outbound Streaming-XML-Security
 * An instance of this class can be retrieved over the XMLSec class
 *
 */
public class OutboundXMLSec {

    private final XMLSecurityProperties securityProperties;

    public OutboundXMLSec(XMLSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    /**
     * This method is the entry point for the incoming security-engine.
     * Hand over a outputStream and use the returned XMLStreamWriter for further processing
     *
     * @param outputStream The original outputStream
     * @return A new XMLStreamWriter which does transparently the security processing.
     * @throws XMLSecurityException thrown when a Security failure occurs
     */
    public XMLStreamWriter processOutMessage(OutputStream outputStream, String encoding) throws XMLSecurityException {
        return processOutMessage((Object)outputStream, encoding, null);
    }

    /**
     * This method is the entry point for the incoming security-engine.
     * Hand over the original XMLStreamWriter and use the returned one for further processing
     *
     * @param xmlStreamWriter The original xmlStreamWriter
     * @return A new XMLStreamWriter which does transparently the security processing.
     * @throws XMLSecurityException thrown when a Security failure occurs
     */
    public XMLStreamWriter processOutMessage(XMLStreamWriter xmlStreamWriter, String encoding) throws XMLSecurityException {
        return processOutMessage((Object)xmlStreamWriter, encoding, null);
    }

    public XMLStreamWriter processOutMessage(OutputStream outputStream, String encoding,
                                             SecurityEventListener eventListener) throws XMLSecurityException {
        return processOutMessage((Object)outputStream, encoding, eventListener);
    }

    public XMLStreamWriter processOutMessage(XMLStreamWriter xmlStreamWriter, String encoding,
                                             SecurityEventListener eventListener) throws XMLSecurityException {
        return processOutMessage((Object) xmlStreamWriter, encoding, eventListener);
    }

    private XMLStreamWriter processOutMessage(
        Object output, String encoding, SecurityEventListener eventListener) throws XMLSecurityException {
        final OutboundSecurityContextImpl outboundSecurityContext = new OutboundSecurityContextImpl(securityProperties);

        if (eventListener != null) {
            outboundSecurityContext.addSecurityEventListener(eventListener);
        }

        final DocumentContextImpl documentContext = new DocumentContextImpl();
        documentContext.setEncoding(encoding);

        OutputProcessorChainImpl outputProcessorChain = new OutputProcessorChainImpl(outboundSecurityContext, documentContext);

        int actionOrder = 0;
        for (XMLSecurityConstants.Action action : securityProperties.getActions()) {
            if (XMLSecurityConstants.SIGNATURE.equals(action)) {
                XMLSignatureOutputProcessor signatureOutputProcessor = new XMLSignatureOutputProcessor();
                initializeOutputProcessor(outputProcessorChain, signatureOutputProcessor, action, actionOrder++);

                configureSignatureKeys(outboundSecurityContext);
                List<SecurePartSelector> signaturePartSelectors = securityProperties.getSignaturePartSelectors();
                signaturePartSelectors.stream().forEach(securePartSelector -> securePartSelector.init(outputProcessorChain));
                outputProcessorChain.getSecurityContext().put(
                        XMLSecurityConstants.SIGNATURE_PART_SELECTORS,
                        signaturePartSelectors
                );
            } else if (XMLSecurityConstants.ENCRYPTION.equals(action)) {
                XMLEncryptOutputProcessor encryptOutputProcessor = new XMLEncryptOutputProcessor();
                initializeOutputProcessor(outputProcessorChain, encryptOutputProcessor, action, actionOrder++);

                configureEncryptionKeys(outboundSecurityContext);
                List<SecurePartSelector> encryptionPartSelectors = securityProperties.getEncryptionPartSelectors();
                encryptionPartSelectors.stream().forEach(securePartSelector -> securePartSelector.init(outputProcessorChain));
                outputProcessorChain.getSecurityContext().put(
                        XMLSecurityConstants.ENCRYPTION_PART_SELECTORS,
                        encryptionPartSelectors
                );
            }
        }
        if (output instanceof OutputStream) {
            final FinalOutputProcessor finalOutputProcessor = new FinalOutputProcessor((OutputStream) output, encoding);
            initializeOutputProcessor(outputProcessorChain, finalOutputProcessor, null, -1);

        } else if (output instanceof XMLStreamWriter) {
            final FinalOutputProcessor finalOutputProcessor = new FinalOutputProcessor((XMLStreamWriter) output);
            initializeOutputProcessor(outputProcessorChain, finalOutputProcessor, null, -1);

        } else {
            throw new IllegalArgumentException(output + " is not supported as output");
        }

        return new XMLSecurityStreamWriter(outputProcessorChain);
    }

    private void initializeOutputProcessor(OutputProcessorChainImpl outputProcessorChain, OutputProcessor outputProcessor, XMLSecurityConstants.Action action, int actionOrder) throws XMLSecurityException {
        outputProcessor.setXMLSecurityProperties(securityProperties);
        outputProcessor.setAction(action, actionOrder);
        outputProcessor.init(outputProcessorChain);
    }

    private void configureSignatureKeys(final OutboundSecurityContextImpl outboundSecurityContext) throws XMLSecurityException {
        Key key = securityProperties.getSignatureKey();
        X509Certificate[] x509Certificates = securityProperties.getSignatureCerts();
        if (key instanceof PrivateKey && (x509Certificates == null || x509Certificates.length == 0)
            && securityProperties.getSignatureVerificationKey() == null) {
            throw new XMLSecurityException("stax.signature.publicKeyOrCertificateMissing");
        }

        final String securityTokenid = IDGenerator.generateID("SIG");
        final OutboundSecurityToken securityToken =
                new GenericOutboundSecurityToken(securityTokenid, SecurityTokenConstants.DefaultToken, key, x509Certificates);
        if (securityProperties.getSignatureVerificationKey() instanceof PublicKey) {
            ((GenericOutboundSecurityToken)securityToken).setPublicKey(
                (PublicKey)securityProperties.getSignatureVerificationKey());
        }

        final SecurityTokenProvider<OutboundSecurityToken> securityTokenProvider =
                new SecurityTokenProvider<OutboundSecurityToken>() {

            @Override
            public OutboundSecurityToken getSecurityToken() throws XMLSecurityException {
                return securityToken;
            }

            @Override
            public String getId() {
                return securityTokenid;
            }
        };
        outboundSecurityContext.registerSecurityTokenProvider(securityTokenid, securityTokenProvider);

        outboundSecurityContext.put(XMLSecurityConstants.PROP_USE_THIS_TOKEN_ID_FOR_SIGNATURE, securityTokenid);
    }

    private void configureEncryptionKeys(final OutboundSecurityContextImpl outboundSecurityContext) throws XMLSecurityException {
        // Sort out transport keys / key wrapping keys first.
        Key transportKey = securityProperties.getEncryptionTransportKey();
        X509Certificate transportCert = securityProperties.getEncryptionUseThisCertificate();
        X509Certificate[] transportCerts = null;
        if (transportCert != null) {
            transportCerts = new X509Certificate[]{transportCert};
        }

        final OutboundSecurityToken transportSecurityToken =
                new GenericOutboundSecurityToken(IDGenerator.generateID(null), SecurityTokenConstants.DefaultToken, transportKey, transportCerts);

        // Now sort out the session key
        Key key = securityProperties.getEncryptionKey();
        if (key == null) {
            if (transportCert == null && transportKey == null) {
                throw new XMLSecurityException("stax.encryption.encryptionKeyMissing");
            }
            // If none is configured then generate one
            String keyAlgorithm =
                JCEAlgorithmMapper.getJCEKeyAlgorithmFromURI(securityProperties.getEncryptionSymAlgorithm());
            KeyGenerator keyGen;
            try {
                keyGen = KeyGenerator.getInstance(keyAlgorithm);
            } catch (NoSuchAlgorithmException e) {
                throw new XMLSecurityException(e);
            }
            //the sun JCE provider expects the real key size for 3DES (112 or 168 bit)
            //whereas bouncy castle expects the block size of 128 or 192 bits
            if (keyAlgorithm.contains("AES")) {
                int keyLength =
                    JCEAlgorithmMapper.getKeyLengthFromURI(securityProperties.getEncryptionSymAlgorithm());
                keyGen.init(keyLength);
            }

            key = keyGen.generateKey();
        }

        final String securityTokenid = IDGenerator.generateID(null);
        final GenericOutboundSecurityToken securityToken =
                new GenericOutboundSecurityToken(securityTokenid, SecurityTokenConstants.DefaultToken, key);
        securityToken.setKeyWrappingToken(transportSecurityToken);

        final SecurityTokenProvider<OutboundSecurityToken> securityTokenProvider =
                new SecurityTokenProvider<OutboundSecurityToken>() {

            @Override
            public OutboundSecurityToken getSecurityToken() throws XMLSecurityException {
                return securityToken;
            }

            @Override
            public String getId() {
                return securityTokenid;
            }
        };
        outboundSecurityContext.registerSecurityTokenProvider(securityTokenid, securityTokenProvider);
        outboundSecurityContext.put(XMLSecurityConstants.PROP_USE_THIS_TOKEN_ID_FOR_ENCRYPTION, securityTokenid);
    }
}
