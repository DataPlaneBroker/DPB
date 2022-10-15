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

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
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
 * @author simpsons
 */
public class RESTDispatcher implements HttpRequestHandlerMapper {
    private static final Comparator<Pattern> SLASH_COUNTER = (a, b) -> {
        long ac = a.pattern().chars().filter(c -> c == '/').count();
        long bc = b.pattern().chars().filter(c -> c == '/').count();
        int rc = Long.compare(bc, ac);
        if (rc != 0) return rc;
        return a.pattern().compareTo(b.pattern());
    };

    private void deregister(Collection<? extends String> methods,
                            Pattern pattern) {
        for (String method : methods) {
            Map<Pattern, HttpRequestHandler> submap = mapping.get(method);
            if (submap == null) continue;
            @SuppressWarnings("unused")
            HttpRequestHandler lost = submap.remove(pattern);
            if (submap.isEmpty()) mapping.remove(method);
        }
    }

    private void register(Collection<? extends String> methods,
                          Pattern pattern, HttpRequestHandler handler) {
        if (methods == null) return;
        if (pattern == null) throw new NullPointerException("pattern");
        if (handler == null) throw new NullPointerException("handler");
        for (String method : methods) {
            mapping
                .computeIfAbsent(method, (k) -> new TreeMap<>(SLASH_COUNTER))
                .put(pattern, handler);
        }
    }

    synchronized void
        register(Collection<? extends RESTRegistration.Record> records) {
        for (RESTRegistration.Record record : records)
            register(record.methods, record.pattern, record.handler);
    }

    synchronized void
        deregister(Collection<? extends RESTRegistration.Record> records) {
        for (RESTRegistration.Record record : records)
            deregister(record.methods, record.pattern);
    }

    private final Map<String, Map<Pattern, HttpRequestHandler>> mapping =
        new HashMap<>();

    private synchronized Map<Pattern, HttpRequestHandler>
        copy(String method) {
        return new HashMap<>(mapping.getOrDefault(method,
                                                  Collections.emptyMap()));
    }

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
        final URI requestUri = URI.create(request.getRequestLine().getUri());
        if (requestUri.isAbsolute()) return null;

        /* Match the request URI path against each of the patterns,
         * starting with those with the greatest number of path
         * elements. */
        final String path = requestUri.getPath();
        final String method = request.getRequestLine().getMethod();
        for (Map.Entry<Pattern, HttpRequestHandler> entry : copy(method)
            .entrySet()) {
            final Pattern pattern = entry.getKey();
            final Matcher m = pattern.matcher(path);
            if (!m.matches()) continue;

            RESTContext ctxt = new RESTContext(requestUri, m);

            /* Wrap the REST handler in an HTTP handler. */
            final HttpRequestHandler handler = entry.getValue();
            return new HttpRequestHandler() {
                @Override
                public void handle(HttpRequest request, HttpResponse response,
                                   HttpContext context)
                    throws HttpException,
                        IOException {
                    try {
                        ctxt.set(context);
                        logger.finest(() -> String
                            .format("%s %s -> %s%n",
                                    request.getRequestLine().getMethod(),
                                    request.getRequestLine().getUri(),
                                    handler));
                        handler.handle(request, response, context);
                        logger.finer(() -> String
                            .format("%s %s = %d%n",
                                    request.getRequestLine().getMethod(),
                                    request.getRequestLine().getUri(),
                                    response.getStatusLine()
                                        .getStatusCode()));
                    } finally {
                        ctxt.unset(context);
                    }
                }
            };
        }

        return null;
    }

    private final Logger logger =
        Logger.getLogger(RESTDispatcher.class.getName());
}
