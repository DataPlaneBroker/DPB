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
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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

    private void request(String method, String sub, Map<?, ?> params,
                         ResponseHandler<JSONObject> handler) {
        @SuppressWarnings("unchecked")
        ResponseHandler<JSONObject> altHandler =
            protect(ResponseHandler.class, handler);
        try {
            URI location = service.resolve("api/v1/" + sub);
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
                System.err.printf("request: %s%n",
                                  JSONObject.toJSONString(params));
                try (Writer out =
                    new OutputStreamWriter(conn.getOutputStream(), "UTF-8")) {
                    JSONObject.writeJSONString(params, out);
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
        request("GET", "", null,
                new AdaptiveHandler<APIDesc>(handler, APIDesc::new));
    }

    public void getBridgesDesc(ResponseHandler<BridgesDesc> handler) {
        request("GET", "bridges", null,
                new AdaptiveHandler<BridgesDesc>(handler, BridgesDesc::new));
    }

    public void getBridgeDesc(String bridge,
                              ResponseHandler<BridgeDesc> handler) {
        request("GET", "bridges/" + bridge, null,
                new AdaptiveHandler<BridgeDesc>(handler, BridgeDesc::new));
    }

    public void createBridge(BridgeDesc desc,
                             ResponseHandler<BridgesDesc> handler) {
        request("POST", "bridges", desc.toJSON(),
                new AdaptiveHandler<BridgesDesc>(handler, BridgesDesc::new));
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
        System.out.printf("Requests issued%n");
        IdleExecutor.processAll();
        System.out.printf("Idle%n");
    }
}
