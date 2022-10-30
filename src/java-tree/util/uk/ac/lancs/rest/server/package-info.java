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

/**
 * Apache {@link org.apache.http.protocol.HttpRequestHandler}s can be
 * collected with a {@link uk.ac.lancs.rest.server.RESTRegistration},
 * and registered as a unit with a
 * {@link uk.ac.lancs.rest.server.RESTDispatcher} under specific HTTP
 * methods and distinct regular expressions matching on the Request-URI
 * path. The dispatcher then serves as an Apache
 * {@link org.apache.http.protocol.HttpRequestHandlerMapper}, yielding
 * the relevant REST handler for the given request, and ensuring that
 * the additional REST context is available to the user-supplied
 * handler.
 * 
 * <p>
 * For example:
 * 
 * <pre>
 * // Define the handler.
 * static final RESTField&lt;UUID&gt; UUID_FIELD =
 *     RESTField.of(UUID::parse).from("uuid");
 * HttpRequestHandler myHandler = (request, response, context) -&gt; {
 *     RESTContext rest = RESTContext.get(context);
 *     UUID uuid = rest.get(UUID_FIELD);
 *     ...
 * };
 * 
 * // Register the handler.
 * RESTDispatcher mapper = ...;
 * RESTRegistration reg = new RESTRegistration();
 * reg.start(myHandler).on("GET").on("POST")
 *    .at("/foo/(?&lt;uuid&gt;[0-9a-fA-F]{2}(?:-?[0-9a-fA-F]{2}){15})")
 *    .done();
 * reg.register(mapper);
 * </pre>
 * 
 * <p>
 * The number of slashes in the regular expression determines its
 * specificity, and the dispatcher attempts to match handlers registered
 * against more specific expressions first. Slashes should not be
 * optional components, e.g., <code>/?</code>. The expression
 * <code>[^\\u005c]</code> should be used instead of one matching any
 * character (<code>.</code>).
 * 
 * <hr>
 * 
 * <p>
 * By the way, {@link org.apache.http.protocol.HttpContext} should use
 * typed keys analogous to {@link RESTField}. You simply wouldn't have
 * to worry about type-casting or key clashes. Also, I'd have called
 * {@link org.apache.http.protocol.HttpRequestHandler}
 * <code>HttpHandler</code>, and
 * {@link org.apache.http.protocol.HttpRequestHandlerMapper}
 * <code>HttpDispatcher</code>.
 * 
 * @resume Tools for implementing REST APIs
 * 
 * @author simpsons
 */
package uk.ac.lancs.rest.server;
