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
 * Allows REST services to be defined as a set of instance methods of a
 * class.
 * 
 * <p>
 * Methods may be registered with the
 * <code>{@linkplain Route &#64;Route}</code> annotation, which holds
 * the regular expression. The
 * <code>{@linkplain Method &#64;Method}</code> annotation may then be
 * applied multiple times to indicate which HTTP methods to respond to.
 * {@link Subpath &#64;Subpath} can be added to optionally or
 * necessarily match subpaths.
 * 
 * <pre>
 * &#64;Method("GET")
 * &#64;Method("POST")
 * &#64;Route("/foo/(?&lt;uuid&gt;[0-9a-fA-F]{2}(?:-?[0-9a-fA-F]{2}){15})")
 * void handleFoo(HttpRequest request,
 *                HttpResponse response,
 *                HttpContext context) {
 *     RESTContext rest = RESTContext.get(context);
 *     UUID uuid = rest.get(UUID_FIELD);
 *     ...
 * }
 * </pre>
 * 
 * <p>
 * To register all annotated methods of an object:
 * 
 * <pre>
 * RESTRegistration reg = new RESTRegistration();
 * reg.record(obj, "/prefix");
 * reg.register(mapper);
 * </pre>
 * 
 * <p>
 * By placing <samp>lurest-service.jar</samp> and
 * <samp>lurest-service-proc.jar</samp> in the compiler's processor
 * path, annotated methods' signatures and regular expressions will be
 * checked at compile time.
 * 
 * @author simpsons
 */
package uk.ac.lancs.rest.service;
