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

package uk.ac.lancs.rest.service;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Binds a Java method to an HTTP method. This annotation is to be used
 * in conjunction with {@link Route &#64;Route}, and has no effect
 * without it. When an object is passed to
 * {@link uk.ac.lancs.rest.server.RESTRegistration#record(Object, String)},
 * its methods annotated with {@link Route &#64;Route} are registered as
 * REST handlers. If a method is not annotated with
 * <code>&#64;Method</code>, only <code>GET</code> requests will be
 * directed to the handler. Otherwise, only those listed by
 * <code>&#64;Method</code> annotations will be directed to the handler.
 * <code>&#64;Method</code> may be used multiple times.
 * 
 * <p>
 * For example:
 * 
 * <pre>
 * &#64;Route("/object/(?&lt;objid&gt;[0-9a-fA-F])/delete")
 * &#64;Method("PUT")
 * &#64;Method("POST")
 * void handleDeletion(HttpRequest req,
 *                     HttpResponse rsp,
 *                     HttpContext ctxt) {
 *     <var>...</var>
 * }
 * </pre>
 * 
 * @author simpsons
 * 
 * @see uk.ac.lancs.rest.server.RESTRegistration#record(Object, String)
 */
@Retention(RUNTIME)
@Target(METHOD)
@Repeatable(Methods.class)
public @interface Method {
    /**
     * Get the HTTP method to which the annotated element should be
     * bound.
     * 
     * @return the HTTP method
     */
    String value();
}
