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
package uk.ac.lancs.networks.corsa.rest;

import java.util.function.Function;

/**
 * A REST response message with an HTTP response code
 * 
 * @param <T> the application-specific message type
 * 
 * @author simpsons
 */
public class RESTResponse<T> {
    /**
     * The HTTP response code
     */
    public final int code;

    /**
     * The raw JSON response entity
     */
    public final JSONEntity entity;

    /**
     * The REST response message, translated from {@link #entity}
     */
    public final T message;

    /**
     * Combine an HTTP response code with a map-like JSON message
     * converted to an application-specific message.
     * 
     * @param code the HTTP response code
     * 
     * @param entity the raw message entity
     * 
     * @param adapter a converter from the entity to the intended type
     */
    public RESTResponse(int code, JSONEntity entity,
                        Function<? super JSONEntity, ? extends T> adapter) {
        this.code = code;
        this.entity = entity;
        this.message = adapter.apply(this.entity);
    }

    /**
     * Combine an HTTP response code with a map-like JSON message
     * converted to an application-specific message.
     * 
     * @param code the HTTP response code
     * 
     * @param entity the raw message entity
     * 
     * @param adapter a converter from the entity to the intended type
     */
    public RESTResponse(int code, JSONEntity entity) {
        this.code = code;
        this.entity = entity;
        this.message = null;
    }

    /**
     * Adapt this response to a new type.
     * 
     * @param <E> the new type
     * 
     * @param adapter a converter from the entity to the intended type
     * 
     * @return the adapted response
     */
    public <E> RESTResponse<E>
        adapt(Function<? super JSONEntity, ? extends E> adapter) {
        return new RESTResponse<>(this.code, this.entity, adapter);
    }
}
