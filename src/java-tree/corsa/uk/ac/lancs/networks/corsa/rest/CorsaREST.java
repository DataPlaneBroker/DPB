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
package uk.ac.lancs.networks.corsa.rest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.EntityBuilder;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.HttpClients;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Connects to a Corsa REST interface.
 * 
 * @author simpsons
 */
public final class CorsaREST {
    private final URI service;
    private final String authz;
    private final SSLContext sslContext;

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
        this.service = service;
        this.authz = authz;

        TrustManager[] tm = new TrustManager[] { new X509TrustManager() {
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[] { cert };
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain,
                                           String authType)
                throws CertificateException {
                for (X509Certificate cand : chain) {
                    if (!cand.equals(cert)) continue;
                    return;
                }
                throw new CertificateException("not accepted");
            }

            @Override
            public void checkClientTrusted(X509Certificate[] arg0,
                                           String arg1)
                throws CertificateException {
                ;
            }
        }, };

        sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, tm, new SecureRandom());
    }

    private HttpClient newClient() {
        HttpClient httpClient = HttpClients.custom().setSSLContext(sslContext)
            .setSSLHostnameVerifier(new NoopHostnameVerifier()).build();
        return httpClient;
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private RESTResponse<JSONEntity>
        request(HttpUriRequest request) throws IOException, ParseException {
        HttpClient client = newClient();
        request.setHeader("Authorization", authz);
        HttpResponse rsp = client.execute(request);
        final int code = rsp.getStatusLine().getStatusCode();
        HttpEntity ent = rsp.getEntity();
        final JSONEntity result;
        if (ent == null) {
            result = null;
        } else {
            try (Reader in = new InputStreamReader(ent.getContent(), UTF8)) {
                JSONParser parser = new JSONParser();
                result = new JSONEntity(parser.parse(in));
            }
        }
        return new RESTResponse<JSONEntity>(code, result);
    }

    private static HttpEntity entityOf(Map<?, ?> params) {
        return EntityBuilder.create()
            .setContentType(ContentType.APPLICATION_JSON)
            .setText(JSONObject.toJSONString(params)).build();
    }

    private static HttpEntity entityOf(List<?> params) {
        String text = JSONArray.toJSONString(params);
        return EntityBuilder.create()
            .setContentType(ContentType.APPLICATION_JSON).setText(text)
            .build();
    }

    private RESTResponse<JSONEntity>
        get(String sub) throws IOException, ParseException {
        URI location = service.resolve("api/v1/" + sub);
        HttpGet request = new HttpGet(location);
        return request(request);
    }

    private RESTResponse<JSONEntity>
        delete(String sub) throws IOException, ParseException {
        URI location = service.resolve("api/v1/" + sub);
        HttpDelete request = new HttpDelete(location);
        return request(request);
    }

    private RESTResponse<JSONEntity> post(String sub, Map<?, ?> params)
        throws IOException,
            ParseException {
        URI location = service.resolve("api/v1/" + sub);
        HttpPost request = new HttpPost(location);
        request.setEntity(entityOf(params));
        return request(request);
    }

    private RESTResponse<JSONEntity>
        patch(String sub, List<?> params) throws IOException, ParseException {
        URI location = service.resolve("api/v1/" + sub);
        HttpPatch request = new HttpPatch(location);
        request.setEntity(entityOf(params));
        return request(request);
    }

    /**
     * Get the description of the switch's API.
     * 
     * @return the switch API description
     * 
     * @throws ParseException if the response was not in the expected
     * format
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<APIDesc>
        getAPIDesc() throws IOException, ParseException {
        return get("").adapt(APIDesc::new);
    }

    /**
     * Get a list of bridges.
     * 
     * @return the list of bridges
     * 
     * @throws ParseException if the response was not in the expected
     * format
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<Map<String, BridgeDesc>>
        getBridgeDescs() throws IOException, ParseException {
        RESTResponse<JSONEntity> rsp = get("bridges?list=true");
        return rsp.adapt(s -> BridgeDesc.of((JSONArray) s.map.get("list")));
    }

    /**
     * Get all bridge names.
     * 
     * @return a set of all bridge names
     * 
     * @throws ParseException if the response was not in the expected
     * format
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<Collection<String>>
        getBridgeNames() throws IOException, ParseException {
        return get("bridges").adapt(s -> {
            @SuppressWarnings("unchecked")
            Map<String, JSONObject> links =
                (Map<String, JSONObject>) s.map.get("links");
            return links.keySet();
        });
    }

    /**
     * Get details of a specific bridge.
     * 
     * @param name the bridge identifier of the form
     * <samp>br<var>N</var></samp>, where <var>N</var> is in [1,63]
     * 
     * @return the bridge details
     * 
     * @throws ParseException if the response was not in the expected
     * format
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<BridgeDesc>
        getBridgeDesc(String name) throws IOException, ParseException {
        return get("bridges/" + name).adapt(s -> {
            return new BridgeDesc(s.map).bridge(name);
        });
    }

    /**
     * Create a bridge.
     * 
     * @param desc the bridge's configuration
     * 
     * @return the bridge's name
     * 
     * @throws ParseException if the response was not in the expected
     * format
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<String>
        createBridge(BridgeDesc desc) throws IOException, ParseException {
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
     * @throws ParseException if the response was not in the expected
     * format
     * 
     * @throws IOException if there was an I/O error
     */
    @SuppressWarnings("unchecked")
    public RESTResponse<Void> patchBridge(String bridge, BridgePatchOp... ops)
        throws IOException,
            ParseException {
        JSONArray root = new JSONArray();
        for (PatchOp op : ops)
            root.add(op.marshal());
        return patch("bridges/" + bridge, root).adapt(s -> null);
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
     * @throws ParseException if the response was not in the expected
     * format
     * 
     * @throws IOException if there was an I/O error
     */
    @SuppressWarnings("unchecked")
    public RESTResponse<Void> patchTunnel(String bridge, int ofport,
                                          TunnelPatchOp... ops)
        throws IOException,
            ParseException {
        JSONArray root = new JSONArray();
        for (PatchOp op : ops)
            root.add(op.marshal());
        return patch("bridges/" + bridge + "/tunnels/" + ofport, root)
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
     * @throws ParseException if the response was not in the expected
     * format
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<Void>
        destroyBridge(String bridge) throws IOException, ParseException {
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
     * @throws ParseException if the response was not in the expected
     * format
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<ControllersDesc>
        getControllers(String bridge) throws IOException, ParseException {
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
     * @throws ParseException if the response was not in the expected
     * format
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<ControllerDesc> getController(String bridge,
                                                      String ctrl)
        throws IOException,
            ParseException {
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
     * @throws ParseException if the response was not in the expected
     * format
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<ControllersDesc>
        attachController(String bridge, ControllerConfig config)
            throws IOException,
                ParseException {
        return post("bridges/" + bridge + "/controllers", config.toJSON())
            .adapt(ControllersDesc::new);
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
     * @throws ParseException if the response was not in the expected
     * format
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<Void> detachController(String bridge, String ctrl)
        throws IOException,
            ParseException {
        return delete("bridges/" + bridge + "/controllers/" + ctrl)
            .adapt(s -> null);
    }

    /**
     * Get a list of tunnels of a bridge.
     * 
     * @param bridge the bridge identifier of the form
     * <samp>br<var>N</var></samp>, where <var>N</var> is in [1,63]
     * 
     * @return the response
     * 
     * @throws ParseException if the response was not in the expected
     * format
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<Map<Integer, TunnelDesc>>
        getTunnels(String bridge) throws IOException, ParseException {
        return get("bridges/" + bridge + "/tunnels?list=true")
            .adapt(s -> TunnelDesc.of((JSONArray) s.map.get("list")));
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
     * @throws ParseException if the response was not in the expected
     * format
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<Void> attachTunnel(String bridge, TunnelDesc config)
        throws IOException,
            ParseException {
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
     * @throws ParseException if the response was not in the expected
     * format
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<TunnelDesc> getTunnel(String bridge, int ofport)
        throws IOException,
            ParseException {
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
     * @param handler invoked with the result when the operation is
     * complete
     * 
     * @return the response
     * 
     * @throws ParseException if the response was not in the expected
     * format
     * 
     * @throws IOException if there was an I/O error
     */
    public RESTResponse<Void> detachTunnel(String bridge, int ofport)
        throws IOException,
            ParseException {
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
