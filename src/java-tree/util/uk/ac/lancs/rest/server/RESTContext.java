// -*- c-basic-offset: 4; indent-tabs-mode: nil -*-

/*
 * Copyright 2018,2019, Lancaster University
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
 *  * Neither the name of the copyright holder nor the names of
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
 * Author: Steven Simpson <http://github.com/simpsonst>
 */

package uk.ac.lancs.rest.server;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.protocol.HttpContext;

/**
 * Accesses context derived from an HTTP request.
 * 
 * @author simpsons
 */
public final class RESTContext {
    private final URI requestUri;
    private final Matcher matcher;

    RESTContext(URI requestUri, Matcher matcher) {
        this.requestUri = requestUri;
        this.matcher = matcher;
    }

    private static final Map<HttpContext, RESTContext> attachments =
        Collections.synchronizedMap(new WeakHashMap<>());

    void set(HttpContext ctxt) {
        attachments.put(ctxt, this);
    }

    void unset(HttpContext ctxt) {
        attachments.remove(ctxt);
    }

    /**
     * Get the REST subcontext of an HTTP context.
     * 
     * @param ctxt the HTTP context
     * 
     * @return the corresponding REST subcontext, or {@code null} if not
     * present
     */
    public static RESTContext get(HttpContext ctxt) {
        return attachments.get(ctxt);
    }

    /**
     * Get the request URI as a parsed object.
     * 
     * @return the request URI
     */
    public URI requestURI() {
        return requestUri;
    }

    /**
     * Get the matcher on the request URI.
     * 
     * @return the matcher
     */
    public Matcher matcher() {
        return matcher;
    }

    private Map<RESTField<?>, Object> cache = new ConcurrentHashMap<>();

    private <T> T reget(RESTField<T> key) {
        return key.get(matcher());
    }

    /**
     * Get a converted field from the matcher on the request URI. The
     * value is cached, so if a default function is specified for the
     * field, it will only be called once for this context.
     * 
     * @param <T> the type of the field's value
     * 
     * @param key the field selector
     * 
     * @return the field value, or {@code null} if not present, and no
     * default is set
     */
    public <T> T get(RESTField<T> key) {
        @SuppressWarnings("unchecked")
        T result = (T) cache.computeIfAbsent(key, this::reget);
        return result;
    }

    /**
     * Get a raw field from the matcher on the request URI.
     * 
     * @param key the field selector
     * 
     * @return the raw field value, or {@code null} if not present
     */
    public String getText(RESTField<?> key) {
        return key.getText(matcher());
    }

    private Map<String, List<String>> paramMap;

    private static final Pattern PARAM_SEP = Pattern.compile("&");
    private static final Pattern PARAM_FMT =
        Pattern.compile("^([^=]+)=(.*)$");

    private void getParams() {
        if (paramMap != null) return;
        paramMap = new HashMap<>();
        String query = requestUri.getRawQuery();
        for (String part : PARAM_SEP.split(query)) {
            Matcher m = PARAM_FMT.matcher(part);
            if (!m.matches()) continue;
            String name = m.group(1);
            String value = m.group(2);
            paramMap.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }

        /* Make the structure immutable. */
        for (Map.Entry<String, List<String>> entry : paramMap.entrySet())
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        paramMap = Collections.unmodifiableMap(paramMap);
    }

    /**
     * Get a parameter from the query string.
     * 
     * @param name the parameter name
     * 
     * @return the first matching parameter, or {@code null} if not set
     */
    public String param(CharSequence name) {
        getParams();
        List<String> r = paramMap.get(name);
        if (r == null) return null;
        return r.get(0);
    }

    /**
     * Get all values for a parameter from the query string.
     * 
     * @param name the parameter name
     * 
     * @return all matching parameters' values
     */
    public List<String> params(CharSequence name) {
        getParams();
        return paramMap.getOrDefault(name, Collections.emptyList());
    }
}
