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

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpProcessor;

/**
 * Challenges clients with basic HTTP authentication, and verifies
 * credentials.
 * 
 * @param <R> the authentication record type
 * 
 * @author simpsons
 */
public final class BasicAuthenticator<R> implements HttpProcessor {
    private static final Map<HttpContext, BasicAuthenticator<?>> selves =
        Collections.synchronizedMap(new WeakHashMap<>());
    private final String contextKey;
    private final String encodedRealm;
    private final Predicate<? super String> selector;
    private final BiFunction<? super String, ? super String, ? extends R> verifier;

    /**
     * Create an authenticator.
     * 
     * @param contextKey the key in the context under which the
     * authentication record will be placed
     * 
     * @param realm the realm string to be presented to the caller to
     * help it select credentials
     * 
     * @param selector Tests whether this authentication applies to the
     * 
     * @param verifier Verifies the password against the username, and
     * returns an authentication record
     */
    public BasicAuthenticator(String contextKey, String realm,
                              Predicate<? super String> selector,
                              BiFunction<? super String, ? super String, ? extends R> verifier) {
        this.contextKey = contextKey;
        this.encodedRealm = realm.replace("\"", "\\\"");
        this.selector = selector;
        this.verifier = verifier;
    }

    private static final Pattern AUTHZ_FMT = Pattern.compile("^\\s*Basic\\s+"
        + "([a-zA-Z0-9+/]{4}*(?:[a-zA-Z0-9+/]{2}(?:[a-zA-Z0-9+/]|=)=)?)\\s*$");
    private static final Pattern PAIR_FMT = Pattern.compile("^([^:]*):(.*)$");

    /**
     * Check for authentication provided by the client, and replace it
     * with a corresponding authentication record in the processing
     * context. Each <samp>Authorization</samp> header is checked for
     * <samp>Basic <var>b64up</var></samp>, where
     * <samp><var>b64up</var></samp> is a Base64-encoded byte sequence
     * of the UTF-8 encoding of a string of the form
     * <samp><var>username</var>:<var>password</var></samp>. The
     * username and password are passed to the configured verifier. If
     * this yields an authentication record, it is added to a list, and
     * the corresponding header is removed. When all headers have been
     * processed, the list is added to the context under the given key.
     * 
     * @param request the HTTP request to be checked for authentication
     * 
     * @param context the context to which an authentication record will
     * be added
     * 
     * @throws HttpException if an HTTP violation or processing problem
     * occurs
     * 
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void process(HttpRequest request, HttpContext context)
        throws HttpException,
            IOException {
        if (!selector
            .test(URI.create(request.getRequestLine().getUri()).getPath()))
            return;
        selves.put(context, this);
        List<R> results = new ArrayList<>();
        for (Header header : request.getHeaders("Authorization")) {
            /* Check that the authorization is of type Basic, and
             * extract the Base64 username and password. */
            Matcher m = AUTHZ_FMT.matcher(header.getValue());
            if (!m.matches()) continue;
            String pair = new String(Base64.getDecoder().decode(m.group(1)),
                                     StandardCharsets.UTF_8);
            Matcher m2 = PAIR_FMT.matcher(pair);
            if (!m2.matches()) continue;
            String username = m2.group(1);
            String password = m2.group(2);

            /* Verify the password against the stated user. */
            R record = verifier.apply(username, password);
            if (record == null) continue;

            /* The client has been verified. Remove the header, and
             * store the authentication record in the context. */
            request.removeHeader(header);
            results.add(record);
        }
        context.setAttribute(contextKey, results);
    }

    /**
     * If the server intends to challenge the user for credentials,
     * ensure that the correct realm is used. If the response code is
     * <samp>401 Unauthorized</samp>, and a
     * <samp>WWW-Authenticate</samp> has not been supplied, this method
     * adds one specifying the authentication type (<samp>Basic</samp>)
     * and the configured realm.
     * 
     * @param response the HTTP response to be modified
     * 
     * @param context the context shared across the processing chain
     * 
     * @throws HttpException if an HTTP violation or processing problem
     * occurs
     * 
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void process(HttpResponse response, HttpContext context)
        throws HttpException,
            IOException {
        if (selves.get(context) != this) return;
        if (response.getStatusLine()
            .getStatusCode() != HttpStatus.SC_UNAUTHORIZED) return;
        if (response.getFirstHeader("WWW-Authenticate") != null) return;
        response.addHeader("WWW-Authenticate", "Basic realm=\"" + encodedRealm
            + "\",charset=\"UTF-8\"");
    }

}
