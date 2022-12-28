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
package org.apache.xml.security.keys.keyresolver.implementations;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Iterator;

import org.apache.xml.security.exceptions.XMLSecurityException;
import org.apache.xml.security.keys.content.x509.XMLX509SubjectName;
import org.apache.xml.security.keys.keyresolver.KeyResolverException;
import org.apache.xml.security.keys.keyresolver.KeyResolverSpi;
import org.apache.xml.security.keys.storage.StorageResolver;
import org.apache.xml.security.utils.Constants;
import org.apache.xml.security.utils.XMLUtils;
import org.w3c.dom.Element;

public class X509SubjectNameResolver extends KeyResolverSpi {

    private static final Logger LOG = System.getLogger(X509SubjectNameResolver.class.getName());

    /** {@inheritDoc} */
    @Override
    protected boolean engineCanResolve(Element element, String baseURI, StorageResolver storage) {
        if (!XMLUtils.elementIsInSignatureSpace(element, Constants._TAG_X509DATA)) {
            return false;
        }
        Element[] x509childNodes =
            XMLUtils.selectDsNodes(element.getFirstChild(), Constants._TAG_X509SUBJECTNAME);

        return x509childNodes != null && x509childNodes.length > 0;
    }

    /** {@inheritDoc} */
    @Override
    protected PublicKey engineResolvePublicKey(
        Element element, String baseURI, StorageResolver storage, boolean secureValidation
    ) throws KeyResolverException {

        X509Certificate cert =
            this.engineResolveX509Certificate(element, baseURI, storage, secureValidation);

        if (cert != null) {
            return cert.getPublicKey();
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override
    protected X509Certificate engineResolveX509Certificate(
        Element element, String baseURI, StorageResolver storage, boolean secureValidation
    ) throws KeyResolverException {
        Element[] x509childNodes =
            XMLUtils.selectDsNodes(element.getFirstChild(), Constants._TAG_X509SUBJECTNAME);
        if (!(x509childNodes != null && x509childNodes.length > 0)) {
            return null;
        }

        try {
            if (storage == null) {
                Object[] exArgs = { Constants._TAG_X509SUBJECTNAME };
                KeyResolverException ex =
                    new KeyResolverException("KeyResolver.needStorageResolver", exArgs);

                LOG.log(Level.DEBUG, "", ex);

                throw ex;
            }

            XMLX509SubjectName[] x509childObject = new XMLX509SubjectName[x509childNodes.length];

            for (int i = 0; i < x509childNodes.length; i++) {
                x509childObject[i] = new XMLX509SubjectName(x509childNodes[i], baseURI);
            }

            Iterator<Certificate> storageIterator = storage.getIterator();
            while (storageIterator.hasNext()) {
                X509Certificate cert = (X509Certificate) storageIterator.next();
                XMLX509SubjectName certSN = new XMLX509SubjectName(element.getOwnerDocument(), cert);
                LOG.log(Level.DEBUG, "Found Certificate SN: {0}", certSN.getSubjectName());

                for (XMLX509SubjectName childSubject : x509childObject) {
                    LOG.log(Level.DEBUG, "Found Element SN:     {0}", childSubject.getSubjectName());

                    if (certSN.equals(childSubject)) {
                        LOG.log(Level.DEBUG, "match !!! ");
                        return cert;
                    }
                    LOG.log(Level.DEBUG, "no match...");
                }
            }

            return null;
        } catch (XMLSecurityException ex) {
            LOG.log(Level.DEBUG, "XMLSecurityException", ex);
            throw new KeyResolverException(ex);
        }
    }

    /** {@inheritDoc} */
    @Override
    protected javax.crypto.SecretKey engineResolveSecretKey(
        Element element, String baseURI, StorageResolver storage, boolean secureValidation
    ) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    protected PrivateKey engineResolvePrivateKey(
        Element element, String baseURI, StorageResolver storage, boolean secureValidation
    ) {
        return null;
    }
}
