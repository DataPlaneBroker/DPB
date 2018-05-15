/*
 * Copyright 2017, Regents of the University of Lancaster
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
package uk.ac.lancs.networks.corsa;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.stream.Collectors;

import org.json.simple.parser.ParseException;

import uk.ac.lancs.agent.Agent;
import uk.ac.lancs.agent.AgentBuilder;
import uk.ac.lancs.agent.AgentContext;
import uk.ac.lancs.agent.AgentCreationException;
import uk.ac.lancs.agent.AgentFactory;
import uk.ac.lancs.agent.AgentInitiationException;
import uk.ac.lancs.config.Configuration;
import uk.ac.lancs.networks.fabric.Fabric;
import uk.ac.lancs.scc.jardeps.Service;

/**
 * Creates agents each implementing a Corsa DP2X00-compatible fabric.
 * 
 * <p>
 * The following configuration properties are recognized:
 * 
 * <dl>
 * 
 * <dt><samp>capacity.bridges</samp> (default 63)
 * 
 * <dd>Specifies the maximum number of bridges to be created.
 * 
 * <dt><samp>description.partial</samp> (default
 * <samp>{@value #DEFAULT_PARTIAL_DESCRIPTION}</samp>)
 * <dt><samp>description.complete</samp> (default
 * <samp>{@value #DEFAULT_COMPLETE_DESCRIPTION}</samp>)
 * 
 * <dd>Specify the strings used in the text descriptions of each bridge.
 * The partial text is used on creation, and the complete text is used
 * when the bridge is fully configured and operational. Bridges left in
 * the partial state due to error or early termination will be removed
 * on the next restart of the agent.
 * 
 * <dt><samp>subtype</samp> (default
 * <samp>{@value #DEFAULT_SUBYTPE}</samp>)
 * 
 * <dd>Specifies the type of VFC to create.
 * 
 * <dt><samp>ctrl.netns</samp> (default
 * <samp>{@value #DEFAULT_NETWORK_NAMESPACE}</samp>!)
 * 
 * <dd>Specifies the network namespace for the controller port of the
 * new bridges.
 * 
 * <dt><samp>ctrl.host</samp> (default <samp>172.17.1.1</samp>)
 * <dt><samp>ctrl.port</samp> (default <samp>6553</samp>)
 * 
 * <dd>Specify the controller address used to configure each bridge
 * with. The defaults point to the local learning switch.
 * 
 * <dt><samp>rest.location</samp>
 * 
 * <dd>Specifies the URI of the switch's REST API. Do not include
 * <samp>api/v1/</samp> components, as these are added automatically.
 * 
 * <dt><samp>rest.cert.file</samp>
 * 
 * <dd>Specifies the filename (as a URI relative to the containing file)
 * containing an X.509 certificate used to verify the identity of the
 * Corsa switch.
 * 
 * <dt><samp>rest.authz.file</samp>
 * 
 * <dd>Specifies the filename (as a URI relative to the containing file)
 * containing the authorization string to be sent with each REST
 * request. The string may be broken into several lines for readability.
 * 
 * </dl>
 * 
 * @author simpsons
 */
@Service(AgentFactory.class)
public class DP2000FabricAgentFactory implements AgentFactory {
    /**
     * @undocumented
     */
    public static final String DEFAULT_PARTIAL_DESCRIPTION =
        "initiate:vc:partial";

    /**
     * @undocumented
     */
    public static final String DEFAULT_COMPLETE_DESCRIPTION =
        "initiate:vc:complete";

    /**
     * @undocumented
     */
    public static final String DEFAULT_NETWORK_NAMESPACE = "default";

    /**
     * @undocumented
     */
    public static final String DEFAULT_SUBYTPE = "l2-vpn";

    /**
     * @undocumented
     */
    public static final String TYPE_NAME = "corsa-dp2x00-brperlink";

    /**
     * {@inheritDoc}
     * 
     * <p>
     * This implementation recognizes only the string
     * <samp>{@value #TYPE_NAME}</samp>.
     */
    @Override
    public boolean recognize(String type) {
        return TYPE_NAME.equals(type);
    }

    @Override
    public Agent makeAgent(AgentContext ctxt, Configuration conf)
        throws AgentCreationException {
        final String partialDesc =
            conf.get("description.partial", DEFAULT_PARTIAL_DESCRIPTION);
        final String fullDesc =
            conf.get("description.complete", DEFAULT_COMPLETE_DESCRIPTION);
        final String subtype = conf.get("subtype", DEFAULT_SUBYTPE);
        final String netns =
            conf.get("ctrl.netns", DEFAULT_NETWORK_NAMESPACE);
        final InetSocketAddress controller =
            new InetSocketAddress(conf.get("ctrl.host", "172.17.1.1"), Integer
                .parseInt(conf.get("ctrl.port", "6553")));
        final int maxBridges =
            Integer.parseInt(conf.get("capacity.bridges", "63"));
        final URI service = URI.create(conf.get("rest.location"));
        final File certFile = conf.getFile("rest.cert.file");
        final X509Certificate cert;
        try (InputStream in = new FileInputStream(certFile)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            cert = (X509Certificate) cf.generateCertificate(in);
        } catch (IOException | CertificateException e) {
            throw new AgentCreationException("getting certificate from "
                + certFile, e);
        }
        final Path authzFile = conf.getPath("rest.authz.file");
        final String authz;
        try {
            authz = Files.readAllLines(authzFile, StandardCharsets.US_ASCII)
                .stream().collect(Collectors.joining());
        } catch (IOException e) {
            throw new AgentCreationException("getting authorization from "
                + authzFile, e);
        }
        final DP2000Fabric fabric;
        try {
            fabric =
                new DP2000Fabric(maxBridges, partialDesc, fullDesc, subtype,
                                 netns, controller, service, cert, authz);
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            throw new AgentCreationException("building fabric", e);
        }
        return AgentBuilder.start().add(fabric, Fabric.class).create(() -> {
            try {
                fabric.init();
            } catch (IOException | ParseException e) {
                throw new AgentInitiationException(e);
            }
        });
    }
}
