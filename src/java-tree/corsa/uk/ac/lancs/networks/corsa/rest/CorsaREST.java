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
package uk.ac.lancs.networks.corsa.rest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonStructure;

import uk.ac.lancs.rest.client.RESTClient;
import uk.ac.lancs.rest.client.RESTResponse;
import uk.ac.lancs.rest.client.SecureSingleCertificateHttpProvider;

/**
 * Connects to a Corsa REST interface.
 * 
 * @author simpsons
 */
public final class CorsaREST extends RESTClient {
    /**
     * Create a connection to a Corsa REST interface.
     * 
     * @throws NoSuchAlgorithmException if there is no SSL support in
     * this implementation
     * 
     * @throws KeyManagementException if there is a problem with the
     * certificate
     */
    public CorsaREST(URI service, X509Certificate cert, String authz)
        throws NoSuchAlgorithmException,
            KeyManagementException {
        super(service.resolve("api/v1/"),
              SecureSingleCertificateHttpProvider.forCertificate(cert),
              authz);
    }

    /**
     * Get the description of the switch's API.
     * 
     * @return the switch API description
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<APIDesc> getAPIDesc() throws IOException {
        return get("").adapt(APIDesc::new);
    }

    /**
     * Get a list of bridges.
     * 
     * @return the list of bridges
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<Map<String, BridgeDesc>> getBridgeDescs()
        throws IOException {
        RESTResponse<JsonStructure> rsp = get("bridges?list=true");
        return rsp
            .adapt(s -> BridgeDesc.of(((JsonObject) s).getJsonArray("list")));
    }

    /**
     * Get all bridge names.
     * 
     * @return a set of all bridge names
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<Collection<String>> getBridgeNames()
        throws IOException {
        return get("bridges")
            .adapt(s -> ((JsonObject) s).getJsonObject("links").keySet());
    }

    /**
     * Get details of a specific bridge.
     * 
     * @param name the bridge identifier of the form
     * <samp>br<var>N</var></samp>, where <var>N</var> is in [1,63]
     * 
     * @return the bridge details
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<BridgeDesc> getBridgeDesc(String name)
        throws IOException {
        return get("bridges/" + name).adapt(s -> {
            return new BridgeDesc(s).bridge(name);
        });
    }

    /**
     * Create a bridge.
     * 
     * @param desc the bridge's configuration
     * 
     * @return the bridge's name
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<String> createBridge(BridgeDesc desc)
        throws IOException {
        if (desc.name != null)
            return post("bridges", desc.toJSON()).adapt(s -> desc.name);

        restart:
        for (;;) {
            /* Find out what bridges exist. */
            RESTResponse<Collection<String>> known = getBridgeNames();

            /* Find one that doesn't exist, and try to create it. */
            for (int i = 1; i <= 63; i++) {
                String cand = "br" + i;
                if (!known.message.contains(cand)) {
                    desc.bridge(cand);
                    RESTResponse<String> rsp =
                        post("bridges", desc.toJSON()).adapt(s -> cand);

                    /* A 409 Conflict response means we should try
                     * again. Otherwise, it won't matter if we try
                     * again; we either have the successful result, or
                     * an error that would keep happening. */
                    if (rsp.code == 409 /* Conflict */) continue restart;
                    return rsp;
                }
            }

            /* It's all used up. */
            return new RESTResponse<>(409, null);
        }
    }

    /**
     * Modify a bridge.
     * 
     * @param bridge the bridge identifier of the form
     * <samp>br<var>N</var></samp>, where <var>N</var> is in [1,63]
     * 
     * @param ops a set of operations to apply to the bridge
     * 
     * @return the response
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<Void> patchBridge(String bridge, BridgePatchOp... ops)
        throws IOException {
        JsonArrayBuilder root = Json.createArrayBuilder();
        for (PatchOp op : ops)
            root.add(op.marshal());
        return patch("bridges/" + bridge, root.build()).adapt(s -> null);
    }

    /**
     * Modify a tunnel.
     * 
     * @param bridge the bridge identifier of the form
     * <samp>br<var>N</var></samp>, where <var>N</var> is in [1,63]
     * 
     * @param ofport the ofport of the tunnel
     * 
     * @param ops a set of operations to apply to the tunnel
     * 
     * @return the response
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<Void> patchTunnel(String bridge, int ofport,
                                          TunnelPatchOp... ops)
        throws IOException {
        JsonArrayBuilder root = Json.createArrayBuilder();
        for (PatchOp op : ops)
            root.add(op.marshal());
        return patch("bridges/" + bridge + "/tunnels/" + ofport, root.build())
            .adapt(s -> null);
    }

    /**
     * Destroy a bridge.
     * 
     * @param bridge the bridge identifier of the form
     * <samp>br<var>N</var></samp>, where <var>N</var> is in [1,63]
     * 
     * @return the response
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<Void> destroyBridge(String bridge)
        throws IOException {
        return delete("bridges/" + bridge).adapt(s -> null);
    }

    /**
     * Get a list of controllers for a bridge.
     * 
     * @param bridge the bridge identifier of the form
     * <samp>br<var>N</var></samp>, where <var>N</var> is in [1,63]
     * 
     * @return the response
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<ControllersDesc> getControllers(String bridge)
        throws IOException {
        return get("bridges/" + bridge + "/controllers")
            .adapt(ControllersDesc::new);
    }

    /**
     * Get details of a bridge's controller.
     * 
     * @param bridge the bridge identifier of the form
     * <samp>br<var>N</var></samp>, where <var>N</var> is in [1,63]
     * 
     * @param ctrl the controller identifier
     * 
     * @return the response
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<ControllerDesc>
        getController(String bridge, String ctrl) throws IOException {
        return get("bridges/" + bridge + "/controllers/" + ctrl)
            .adapt(ControllerDesc::new);
    }

    /**
     * Attach a controller to a bridge.
     * 
     * @param bridge the bridge identifier of the form
     * <samp>br<var>N</var></samp>, where <var>N</var> is in [1,63]
     * 
     * @param config the controller details
     * 
     * @return the response
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<Void> attachController(String bridge,
                                               ControllerConfig config)
        throws IOException {
        return post("bridges/" + bridge + "/controllers", config.toJSON())
            .adapt(s -> null);
    }

    /**
     * Detach a controller from a bridge.
     * 
     * @param bridge the bridge identifier of the form
     * <samp>br<var>N</var></samp>, where <var>N</var> is in [1,63]
     * 
     * @param ctrl the controller identifier
     * 
     * @return the response
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<Void> detachController(String bridge, String ctrl)
        throws IOException {
        return delete("bridges/" + bridge + "/controllers/" + ctrl)
            .adapt(s -> null);
    }

    /**
     * Get a list of tunnels of a bridge.
     * 
     * @param bridge the bridge identifier of the form
     * <samp>br<var>N</var></samp>, where <var>N</var> is in [1,63]
     * 
     * @return a map from ofport to tunnel description
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<Map<Integer, TunnelDesc>> getTunnels(String bridge)
        throws IOException {
        return get("bridges/" + bridge + "/tunnels?list=true")
            .adapt(s -> TunnelDesc.of(((JsonObject) s).getJsonArray("list")));
    }

    /**
     * Attach a tunnel to a bridge.
     * 
     * @param bridge the bridge identifier of the form
     * <samp>br<var>N</var></samp>, where <var>N</var> is in [1,63]
     * 
     * @param config the tunnel configuration
     * 
     * @return the response
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<Void> attachTunnel(String bridge, TunnelDesc config)
        throws IOException {
        return post("bridges/" + bridge + "/tunnels", config.toJSON())
            .adapt(s -> null);
    }

    /**
     * Get details of a bridge's tunnel.
     * 
     * @param bridge the bridge identifier of the form
     * <samp>br<var>N</var></samp>, where <var>N</var> is in [1,63]
     * 
     * @param ofport the ofport of the tunnel
     * 
     * @return the response
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<TunnelDesc> getTunnel(String bridge, int ofport)
        throws IOException {
        return get("bridges/" + bridge + "/tunnels/" + ofport)
            .adapt(TunnelDesc::new);
    }

    /**
     * Detach a tunnel from a bridge.
     * 
     * @param bridge the bridge identifier of the form
     * <samp>br<var>N</var></samp>, where <var>N</var> is in [1,63]
     * 
     * @param ofport the ofport of the tunnel
     * 
     * @return the response
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<Void> detachTunnel(String bridge, int ofport)
        throws IOException {
        return delete("bridges/" + bridge + "/tunnels/" + ofport)
            .adapt(s -> null);
    }

    /**
     * @undocumented
     */
    public static void main(String[] args) throws Exception {
        /* Parse the arguments. */
        URI root = URI.create(args[0]);

        final X509Certificate cert;
        try (InputStream inStream = new FileInputStream(args[1])) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            cert = (X509Certificate) cf.generateCertificate(inStream);
        }

        StringBuilder authz = new StringBuilder();
        try (BufferedReader in =
            new BufferedReader(new FileReader(new File(args[2])))) {
            for (String line; (line = in.readLine()) != null;) {
                authz.append(line);
            }
        }

        CorsaREST rest = new CorsaREST(root, cert, authz.toString());

        {
            RESTResponse<APIDesc> rsp = rest.getAPIDesc();
            System.out.printf("(%d) bridges at: %s%n", rsp.code,
                              rsp.message.bridges);
        }

        {
            RESTResponse<Collection<String>> rsp = rest.getBridgeNames();
            System.out.printf("Code: (%d)%n", rsp.code);
            System.out.printf("bridges: %s%n", rsp.message);
        }

        {
            RESTResponse<BridgeDesc> rsp = rest.getBridgeDesc("br1");
            System.out.printf("Bridge: %s%n", rsp.message.name);
            System.out.printf("  DPID: %016x%n", rsp.message.dpid);
            System.out.printf("  Resources: %d%%%n", rsp.message.resources);
            if (rsp.message.trafficClass != null) System.out
                .printf("  Traffic class: %d%n", rsp.message.trafficClass);
            System.out.printf("  Subtype: %s%n", rsp.message.subtype);
            System.out.printf("  Protos: %s%n", rsp.message.protocols);
            if (rsp.message.descr != null)
                System.out.printf("  Description: %s%n", rsp.message.descr);
            if (rsp.message.links != null)
                System.out.printf("  Links: %s%n", rsp.message.links);
        }

        {
            RESTResponse<Map<String, BridgeDesc>> rsp = rest.getBridgeDescs();
            System.out.printf("bridges: %s%n", rsp.message.keySet());
        }

        if (false) {
            RESTResponse<String> rsp = rest.createBridge(new BridgeDesc()
                .subtype("openflow").dpid(0x2003024L).resources(5));
            System.out.printf("Creating bridge: (%d) %s%n", rsp.code,
                              rsp.message);
        }

        {
            RESTResponse<Void> rsp =
                rest.patchBridge("br1", ReplaceBridgeDescription.of("Yes!"));
            System.out.printf("Patch rsp: %d%n", rsp.code);
        }

        rest.getTunnels("br1");
    }
}
