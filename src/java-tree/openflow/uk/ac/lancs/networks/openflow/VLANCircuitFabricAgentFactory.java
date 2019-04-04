/*
 * Copyright 2018,2019, Regents of the University of Lancaster
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 * 
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the
 *    distribution.
 * 
 *  * Neither the name of the University of Lancaster nor the names of
 *    its contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *
 * Author: Steven Simpson <s.simpson@lancaster.ac.uk>
 */
package uk.ac.lancs.networks.openflow;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import uk.ac.lancs.agent.Agent;
import uk.ac.lancs.agent.AgentContext;
import uk.ac.lancs.agent.AgentCreationException;
import uk.ac.lancs.agent.AgentFactory;
import uk.ac.lancs.agent.CacheAgent;
import uk.ac.lancs.agent.ServiceCreationException;
import uk.ac.lancs.config.Configuration;
import uk.ac.lancs.networks.fabric.Fabric;

/**
 * Creates agents each implementing a simple fabric that accepts sets of
 * 1-, 2- and 3-tuples identifying raw ports, ctagged ports or
 * stag-ctagged ports as network service end points to be connected
 * together. Tuple sets are passed over a REST interface defined by
 * {@link VLANCircuitControllerREST}, usually to an OpenFlow controller.
 * QoS parameters are ignored. The service type is {@link Fabric},
 * implemented by {@link VLANCircuitFabric}.
 * 
 * <p>
 * The following configuration properties are recognized:
 * 
 * <dl>
 * 
 * <dt><samp>capacity.ports</samp> (default
 * {@value #DEFAULT_PORT_COUNT})
 * 
 * <dd>Specifies the number of ports in the switch.
 * 
 * <dt><samp>dpid</samp>
 * 
 * <dd>Specifies the datapath id of the switch.
 * 
 * <dt><samp>ctrl.rest.location</samp>
 * 
 * <dd>Specifies the URI of the controller's REST API. Do not include
 * <samp>api/v1/</samp> components, as these are added automatically.
 * 
 * <dt><samp>ctrl.rest.cert.file</samp>
 * 
 * <dd>Specifies the filename (as a URI relative to the containing file)
 * containing an X.509 certificate used to verify the identity of the
 * controller.
 * 
 * <dt><samp>ctrl.rest.authz.file</samp>
 * 
 * <dd>Specifies the filename (as a URI relative to the containing file)
 * containing the authorization string to be sent with each REST request
 * to the controller. The string may be broken into several lines for
 * readability.
 * 
 * </dl>
 * 
 * @author simpsons
 */
public final class VLANCircuitFabricAgentFactory implements AgentFactory {
    private static final String TYPE_FIELD = "type";
    private static final String VLANCIRCUIT_TYPE_NAME =
        "openflow-vlancircuit";
    private static final int DEFAULT_PORT_COUNT = 32;

    /**
     * {@inheritDoc}
     * 
     * @default This implementation recognizes only the string
     * <samp>{@value #VLANCIRCUIT_TYPE_NAME}</samp> in the field
     * <samp>{@value #TYPE_FIELD}</samp>.
     */
    @Override
    public boolean recognize(Configuration conf) {
        String type = conf.get(TYPE_FIELD);
        if (type == null) return false;
        switch (type) {
        case VLANCIRCUIT_TYPE_NAME:
            return true;

        default:
            return false;
        }
    }

    @Override
    public Agent makeAgent(AgentContext ctxt, Configuration conf)
        throws AgentCreationException {
        final int portCount = Integer.parseInt(conf
            .get("capacity.ports", Integer.toString(DEFAULT_PORT_COUNT)));
        final long dpid = Long.parseLong(conf.get("dpid"));
        final URI ctrlService = conf.getLocation("ctrl.rest.location");
        final X509Certificate ctrlCert;
        final String ctrlAuthz;
        {
            final File ctrlCertFile = conf.getFile("ctrl.rest.cert.file");
            if (ctrlCertFile == null) {
                ctrlCert = null;
            } else {
                try (InputStream in = new FileInputStream(ctrlCertFile)) {
                    CertificateFactory cf =
                        CertificateFactory.getInstance("X.509");
                    ctrlCert = (X509Certificate) cf.generateCertificate(in);
                } catch (IOException | CertificateException e) {
                    throw new AgentCreationException("getting certificate from "
                        + ctrlCertFile, e);
                }
            }
            final Path ctrlAuthzFile = conf.getPath("ctrl.rest.authz.file");
            if (ctrlAuthzFile == null) {
                ctrlAuthz = null;
            } else {
                try {
                    ctrlAuthz = Files
                        .readAllLines(ctrlAuthzFile,
                                      StandardCharsets.US_ASCII)
                        .stream().collect(Collectors.joining());
                } catch (IOException e) {
                    throw new AgentCreationException("getting authorization from "
                        + ctrlAuthzFile, e);
                }
            }
        }

        return new CacheAgent(new Agent() {
            @Override
            public Collection<String> getKeys(Class<?> type) {
                if (type == Fabric.class) return Collections.singleton(null);
                return Collections.emptySet();
            }

            @Override
            public <T> T findService(Class<T> type, String key)
                throws ServiceCreationException {
                if (key != null) return null;
                if (type != Fabric.class) return null;
                try {
                    VLANCircuitFabric result =
                        new VLANCircuitFabric(portCount, dpid, ctrlService,
                                              ctrlCert, ctrlAuthz);
                    return type.cast(result);
                } catch (KeyManagementException
                    | NoSuchAlgorithmException ex) {
                    throw new ServiceCreationException(ex);
                }
            }
        });
    }
}
