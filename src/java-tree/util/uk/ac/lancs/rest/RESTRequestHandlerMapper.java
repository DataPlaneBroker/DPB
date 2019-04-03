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

package uk.ac.lancs.rest;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerMapper;

/**
 * Maps requests to augmented HTTP handlers based on matching the
 * request URI path against regular expressions. Captures of an
 * expression can be extracted and parsed.
 * 
 * <p>
 * For example:
 * 
 * <pre>
 * RESTRequestHandlerMapper mapper = ...;
 * RESTRegistration.start()
 *                 .on("GET")
 *                 .on("POST")
 *                 .at("/foo/(?&lt;uuid&gt;[0-9a-fA-F]{2}(?:-?[0-9a-fA-F]{2}){15})"),
 *                 .with(RESTField.of(UUID::parse).from("uuid"))
 *                 .register(mapper, myHandler);
 * </pre>
 * 
 * <p>
 * Care must be taken to devise regular expressions. The number of
 * slashes in the pattern determines its specificity. Slashes must not
 * be optional components, e.g., <code>/?</code>. The expression
 * matching any character (<code>.</code>) must not be used;
 * <code>[^/]</code> should be used instead.
 * 
 * @author simpsons
 */
public class RESTRequestHandlerMapper implements HttpRequestHandlerMapper {
    private static final Comparator<Pattern> SLASH_COUNTER = (a, b) -> {
        long ac = a.pattern().chars().filter(c -> c == '/').count();
        long bc = b.pattern().chars().filter(c -> c == '/').count();
        return Long.compare(bc, ac);
    };

    private final static class Doobrie {
        final RESTRequestHandler handler;
        final Collection<RESTField<?>> fields;

        public Doobrie(RESTRequestHandler handler,
                       Collection<RESTField<?>> fields) {
            this.handler = handler;
            this.fields = fields;
        }
    }

    void unregister(Collection<? extends String> methods, Pattern pattern) {
        for (String method : methods) {
            Map<Pattern, Doobrie> submap = mapping.get(method);
            if (submap == null) continue;
            submap.remove(pattern);
            if (submap.isEmpty()) mapping.remove(method);
        }
    }

    void register(Collection<? extends String> methods, Pattern pattern,
                  Collection<? extends RESTField<?>> fields,
                  RESTRequestHandler handler) {
        Doobrie d = new Doobrie(handler, new HashSet<>(fields));
        for (String method : methods)
            mapping
                .computeIfAbsent(method, (k) -> new TreeMap<>(SLASH_COUNTER))
                .put(pattern, d);
    }

    private final Map<String, Map<Pattern, Doobrie>> mapping =
        new HashMap<>();

    /**
     * Find a handler for the given request. Mappings are tried by
     * decreasing specificity.
     * 
     * @param request the request to match against
     * 
     * @return a matching handler, or {@code null} if none found
     */
    @Override
    public HttpRequestHandler lookup(HttpRequest request) {
        URI requestUri = URI.create(request.getRequestLine().getUri());
        if (requestUri.isAbsolute()) return null;

        /* Match the request URI path against each of the patterns,
         * starting with those with the greatest number of path
         * elements. */
        String path = requestUri.getPath();
        for (Map.Entry<Pattern, Doobrie> entry : mapping
            .getOrDefault(request.getRequestLine().getMethod(),
                          Collections.emptyMap())
            .entrySet()) {
            Matcher m = entry.getKey().matcher(path);
            if (!m.matches()) continue;

            /* Make the REST fields available. */
            final Collection<RESTField<?>> fields = entry.getValue().fields;
            RESTFields fieldCtxt = new RESTFields() {
                @Override
                public <T> T get(RESTField<T> field) {
                    if (!fields.contains(field)) return null;
                    return field.resolve(m);
                }
            };

            /* Wrap the REST handler in an HTTP handler. */
            final RESTRequestHandler handler = entry.getValue().handler;
            return new HttpRequestHandler() {
                @Override
                public void handle(HttpRequest request, HttpResponse response,
                                   HttpContext context)
                    throws HttpException,
                        IOException {
                    handler.handle(request, response, context, fieldCtxt);
                }
            };
        }

        return null;
    }
}
