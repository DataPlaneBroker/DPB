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

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

/**
 * Handles an HTTP request with fields extracted from the path of the
 * request URI.
 * 
 * @author simpsons
 * 
 * @deprecated Just use {@link HttpRequestHandler} now, and call
 * {@link RESTContext#get(HttpContext)}.
 */
@FunctionalInterface
@Deprecated
public interface RESTHandler extends HttpRequestHandler {
    /**
     * Handle a REST interaction.
     * 
     * @param request the HTTP request
     * 
     * @param response the HTTP response to be filled in by this call
     * 
     * @param context the HTTP execution context
     * 
     * @param restCtxt the extended context derived from parts of the
     * request
     * 
     * @throws HttpException if an HTTP violation or processing problem
     * occurs
     * 
     * @throws IOException if an I/O error occurs
     */
    void handle(HttpRequest request, HttpResponse response,
                HttpContext context, RESTContext restCtxt)
        throws HttpException,
            IOException;

    /**
     * Handle an HTTP interaction. A {@link RESTContext} is extracted
     * from the <code>context</code> argument, and used to invoke
     * {@link #handle(HttpRequest, HttpResponse, HttpContext, RESTContext)}.
     * 
     * @param request the HTTP request
     * 
     * @param response the HTTP response to be filled in by this call
     * 
     * @param context the HTTP execution context
     * 
     * @throws HttpException if an HTTP violation or processing problem
     * occurs
     * 
     * @throws IOException if an I/O error occurs
     */
    @Override
    default void handle(HttpRequest request, HttpResponse response,
                        HttpContext context)
        throws HttpException,
            IOException {
        this.handle(request, response, context, RESTContext.get(context));
    }
}
