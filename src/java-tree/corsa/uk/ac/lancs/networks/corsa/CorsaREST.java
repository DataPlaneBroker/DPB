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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
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

import uk.ac.lancs.networks.util.IdleExecutor;

/**
 * 
 * 
 * @author simpsons
 */
public final class CorsaREST {
    private final Executor executor;
    private final URI service;
    private final String authz;
    private final SSLContext sslContext;

    @SuppressWarnings("unchecked")
    private <I> I protect(Class<I> type, I base) {
        InvocationHandler h = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
                if (method.getReturnType() != Void.TYPE)
                    return method.invoke(base, args);
                executor.execute(() -> {
                    try {
                        method.invoke(base, args);
                    } catch (IllegalAccessException | IllegalArgumentException
                        | InvocationTargetException e) {
                        throw new AssertionError("unreachable", e);
                    }
                });
                return null;
            }
        };
        return (I) Proxy.newProxyInstance(type.getClassLoader(),
                                          new Class<?>[]
                                          { type }, h);
    }

    /**
     * @throws NoSuchAlgorithmException
     * @throws KeyManagementException
     * 
     */
    public CorsaREST(Executor executor, URI service, X509Certificate cert,
                     String authz)
        throws NoSuchAlgorithmException,
            KeyManagementException {
        this.executor = executor;
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

    private void request(HttpUriRequest request,
                         ResponseHandler<? super JSONObject> handler) {
        @SuppressWarnings("unchecked")
        ResponseHandler<JSONObject> altHandler =
            protect(ResponseHandler.class, handler);
        HttpClient client = newClient();
        request.setHeader("Authorization", authz);
        try {
            HttpResponse rsp = client.execute(request);
            final int code = rsp.getStatusLine().getStatusCode();
            HttpEntity ent = rsp.getEntity();
            final JSONObject result;
            try (Reader in = new InputStreamReader(ent.getContent(), UTF8)) {
                JSONParser parser = new JSONParser();
                result = (JSONObject) parser.parse(in);
            }
            handler.response(code, result);
        } catch (IOException ex) {
            altHandler.exception(ex);
        } catch (ParseException ex) {
            altHandler.exception(ex);
        } catch (Throwable t) {
            altHandler.exception(t);
        }
    }

    private static HttpEntity entityOf(Map<?, ?> params) {
        return EntityBuilder.create()
            .setContentType(ContentType.APPLICATION_JSON)
            .setText(JSONObject.toJSONString(params)).build();
    }

    private static HttpEntity entityOf(List<?> params) {
        String text = JSONArray.toJSONString(params);
        System.err.printf("Request: %s%n", text);
        return EntityBuilder.create()
            .setContentType(ContentType.APPLICATION_JSON).setText(text)
            .build();
    }

    private void get(String sub,
                     ResponseHandler<? super JSONObject> handler) {
        URI location = service.resolve("api/v1/" + sub);
        HttpGet request = new HttpGet(location);
        request(request, handler);
    }

    private void delete(String sub,
                        ResponseHandler<? super JSONObject> handler) {
        URI location = service.resolve("api/v1/" + sub);
        HttpDelete request = new HttpDelete(location);
        request(request, handler);
    }

    private void post(String sub, Map<?, ?> params,
                      ResponseHandler<? super JSONObject> handler) {
        URI location = service.resolve("api/v1/" + sub);
        HttpPost request = new HttpPost(location);
        request.setEntity(entityOf(params));
        request(request, handler);
    }

    private void patch(String sub, List<?> params,
                       ResponseHandler<? super JSONObject> handler) {
        URI location = service.resolve("api/v1/" + sub);
        HttpPatch request = new HttpPatch(location);
        request.setEntity(entityOf(params));
        request(request, handler);
    }

    @SuppressWarnings("unused")
    private void request(String method, String sub, Object params,
                         ResponseHandler<JSONObject> handler) {
        @SuppressWarnings("unchecked")
        ResponseHandler<JSONObject> altHandler =
            protect(ResponseHandler.class, handler);
        try {
            URI location = service.resolve("api/v1/" + sub);

            final HttpUriRequest request;
            if ("GET".equals(method)) {
                HttpGet get = new HttpGet(location);
                request = get;
            } else if ("POST".equals(method)) {
                HttpPost post = new HttpPost(location);
                request = post;
            } else {
                throw new IllegalArgumentException("unknown method: "
                    + method);
            }

            request.setHeader("Authorization", authz);

            HttpClient client = newClient();

            URLConnection conn = location.toURL().openConnection();
            conn.setRequestProperty("Authorization", authz);
            int code = 0;
            if (conn instanceof HttpURLConnection) {
                HttpURLConnection httpConn = (HttpURLConnection) conn;
                httpConn.setRequestMethod(method);
            }
            if (conn instanceof HttpsURLConnection) {
                /* Add the SSL context to validate the certificate. */
                HttpsURLConnection httpsConn = (HttpsURLConnection) conn;
                httpsConn.setSSLSocketFactory(sslContext.getSocketFactory());
                httpsConn.setHostnameVerifier(new HostnameVerifier() {
                    public boolean verify(String hostname,
                                          SSLSession sslSession) {
                        return true;
                    }
                });
            }
            if (params != null) {
                /* Write the params as JSON. */
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                try (Writer out =
                    new OutputStreamWriter(conn.getOutputStream(), "UTF-8")) {
                    if (params instanceof Map) {
                        @SuppressWarnings("rawtypes")
                        Map mapParams = (Map) params;
                        JSONObject.writeJSONString(mapParams, out);
                        System.err.println("Request: "
                            + JSONObject.toJSONString(mapParams));
                    } else if (params instanceof List) {
                        @SuppressWarnings("rawtypes")
                        List listParams = (List) params;
                        JSONArray.writeJSONString(listParams, out);
                        System.err.println("Request: "
                            + JSONArray.toJSONString(listParams));
                    }
                }
            }
            /* No more request details after this. */

            if (conn instanceof HttpURLConnection) {
                HttpURLConnection httpConn = (HttpURLConnection) conn;
                code = httpConn.getResponseCode();
            }

            final String rspText;
            try (Reader in =
                new InputStreamReader(conn.getInputStream(), "UTF-8")) {
                StringBuilder b = new StringBuilder();
                int c;
                while ((c = in.read()) >= 0) {
                    b.append((char) c);
                }
                rspText = b.toString();
            }
            // System.err.printf("Response: %s%n", rspText);
            JSONParser parser = new JSONParser();
            altHandler.response(code, (JSONObject) parser.parse(rspText));
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
            handler.exception(ex);
        } catch (ParseException ex) {
            handler.exception(ex);
        } catch (Throwable ex) {
            handler.exception(ex);
        }
    }

    public void getAPIDesc(ResponseHandler<APIDesc> handler) {
        get("", new AdaptiveHandler<>(handler, APIDesc::new));
    }

    public void getBridgesDesc(ResponseHandler<BridgesDesc> handler) {
        get("bridges", new AdaptiveHandler<>(handler, BridgesDesc::new));
    }

    public void getBridgeDesc(String bridge,
                              ResponseHandler<BridgeDesc> handler) {
        get("bridges/" + bridge,
            new AdaptiveHandler<>(handler, BridgeDesc::new));
    }

    public void createBridge(BridgeDesc desc,
                             ResponseHandler<BridgesDesc> handler) {
        post("bridges", desc.toJSON(),
             new AdaptiveHandler<>(handler, BridgesDesc::new));
    }

    @SuppressWarnings("unchecked")
    public void patchBridge(String bridge,
                            ResponseHandler<JSONObject> handler,
                            BridgePatchOp... ops) {
        JSONArray root = new JSONArray();
        for (PatchOp op : ops)
            root.add(op.marshal());
        patch("bridges/" + bridge, root, handler);
    }

    @SuppressWarnings("unchecked")
    public void patchTunnel(String bridge, int ofport,
                            ResponseHandler<JSONObject> handler,
                            TunnelPatchOp... ops) {
        JSONArray root = new JSONArray();
        for (PatchOp op : ops)
            root.add(op.marshal());
        patch("bridges/" + bridge + "/tunnels/" + ofport, root, handler);
    }

    public void deleteBridge(String bridge, ResponseHandler<Void> handler) {
        delete("bridges/" + bridge,
               new AdaptiveHandler<>(handler, s -> null));
    }

    public void getControllers(String bridge,
                               ResponseHandler<ControllersDesc> handler) {
        get("bridges/" + bridge + "/controllers",
            new AdaptiveHandler<>(handler, ControllersDesc::new));
    }

    public void getController(String bridge, String ctrl,
                              ResponseHandler<ControllerDesc> handler) {
        get("bridges/" + bridge + "/controllers/" + ctrl,
            new AdaptiveHandler<>(handler, ControllerDesc::new));
    }

    public void attachController(String bridge, ControllerConfig config,
                                 ResponseHandler<ControllersDesc> handler) {
        post("bridges/" + bridge + "/controllers", config.toJSON(),
             new AdaptiveHandler<>(handler, ControllersDesc::new));
    }

    public void detachController(String bridge, String ctrl,
                                 ResponseHandler<Void> handler) {
        delete("bridges/" + bridge + "/controllers/" + ctrl,
               new AdaptiveHandler<>(handler, s -> null));
    }

    public void getTunnels(String bridge,
                           ResponseHandler<TunnelsDesc> handler) {
        get("bridges/" + bridge + "/tunnels",
            new AdaptiveHandler<>(handler, TunnelsDesc::new));
    }

    public void attachTunnel(String bridge, TunnelDesc config,
                             ResponseHandler<TunnelsDesc> handler) {
        post("bridges/" + bridge + "/tunnels", config.toJSON(),
             new AdaptiveHandler<>(handler, TunnelsDesc::new));
    }

    public void getTunnel(String bridge, int ofport,
                          ResponseHandler<TunnelDesc> handler) {
        get("bridges/" + bridge + "/tunnels/" + ofport,
            new AdaptiveHandler<>(handler, TunnelDesc::new));
    }

    public void detachTunnel(String bridge, int ofport,
                             ResponseHandler<Void> handler) {
        delete("bridges/" + bridge + "/tunnels/" + ofport,
               new AdaptiveHandler<>(handler, s -> null));
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

        CorsaREST rest = new CorsaREST(IdleExecutor.INSTANCE, root, cert,
                                       authz.toString());
        rest.getAPIDesc(new ResponseHandler<APIDesc>() {
            @Override
            public void response(int code, APIDesc rsp) {
                System.out.printf("(%d) bridges at: %s%n", code, rsp.bridges);
            }
        });
        rest.getBridgesDesc(new ResponseHandler<BridgesDesc>() {
            @Override
            public void response(int code, BridgesDesc rsp) {
                System.out.printf("Code: (%d)%n", code);
                System.out.printf("types: %s%n", rsp.supportedSubtypes);
                System.out.printf("bridges at: %s%n", rsp.bridges);
            }
        });
        if (false) {
            rest.createBridge(new BridgeDesc().bridge("br2")
                .subtype("openflow").dpid(0x2003024L).resources(5),
                              new ResponseHandler<BridgesDesc>() {
                                  @Override
                                  public void response(int code,
                                                       BridgesDesc rsp) {
                                      System.out.printf("Code: (%d)%n", code);
                                      System.out.printf("bridge at: %s%n",
                                                        rsp.bridges);
                                  }

                                  @Override
                                  public void exception(IOException ex) {
                                      System.out.printf("br2 IO: %s%n", ex);
                                  }

                                  @Override
                                  public void exception(ParseException ex) {
                                      System.out.printf("br2 parse: %s%n",
                                                        ex);
                                  }

                                  @Override
                                  public void exception(Throwable ex) {
                                      System.out.printf("br2 other: %s%n",
                                                        ex);
                                  }
                              });
        }
        rest.getBridgeDesc("br1", ComposedHandler.<BridgeDesc>start()
            .onResponse((c, obj) -> {
                System.out.printf("Bridge: %s%n", obj.bridge);
                System.out.printf("  DPID: %016x%n", obj.dpid);
                System.out.printf("  Resources: %d%%%n", obj.resources);
                if (obj.trafficClass != null) System.out
                    .printf("  Traffic class: %d%n", obj.trafficClass);
                System.out.printf("  Subtype: %s%n", obj.subtype);
                System.out.printf("  Protos: %s%n", obj.protocols);
                if (obj.descr != null)
                    System.out.printf("  Description: %s%n", obj.descr);
                if (obj.links != null)
                    System.out.printf("  Links: %s%n", obj.links);
            }));
        rest.patchBridge("br1",
                         ComposedHandler.<JSONObject>start()
                             .onResponse((c, obj) -> {}),
                         ReplaceBridgeDescription.of("Yes!"));
        System.out.printf("Requests issued%n");
        IdleExecutor.processAll();
        System.out.printf("Idle%n");
    }
}
