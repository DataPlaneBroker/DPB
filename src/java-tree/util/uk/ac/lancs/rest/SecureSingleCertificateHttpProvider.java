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
package uk.ac.lancs.rest;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.HttpClients;

/**
 * Provides HTTP clients for SSL contexts generated for a single host.
 * Rather than providing a whole certificate management framework, this
 * class allows an HTTPS certificate to be used in isolation to provide
 * HTTP clients.
 * 
 * @author simpsons
 */
public final class SecureSingleCertificateHttpProvider {
    private final SSLContext sslContext;

    private SecureSingleCertificateHttpProvider(SSLContext sslContext) {
        this.sslContext = sslContext;
    }

    /**
     * Get a new client using the provided context.
     * 
     * @return the new HTTP client
     */
    public HttpClient newClient() {
        HttpClient httpClient = HttpClients.custom().setSSLContext(sslContext)
            .setSSLHostnameVerifier(new NoopHostnameVerifier()).build();
        return httpClient;
    }

    private static SSLContext getContextForCert(X509Certificate cert)
        throws KeyManagementException,
            NoSuchAlgorithmException {
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

        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, tm, new SecureRandom());
        return sslContext;
    }

    /**
     * Create an HTTP provider based on a single certificate. The
     * generated HTTP clients will do no host checking.
     * 
     * @param cert the certficate, or {@code null} if plain HTTP is to
     * be used rather than HTTPS
     * 
     * @return an entity generating HTTP clients
     * 
     * @throws KeyManagementException if there is a problem with the key
     * 
     * @throws NoSuchAlgorithmException if SSL is an unknown context
     * type
     */
    public static Supplier<HttpClient> forCertificate(X509Certificate cert)
        throws KeyManagementException,
            NoSuchAlgorithmException {
        if (cert == null) return HttpClients::createDefault;
        return new SecureSingleCertificateHttpProvider(getContextForCert(cert))::newClient;
    }
}
