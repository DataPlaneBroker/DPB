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

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.json.simple.parser.ParseException;

/**
 * @summary A handler composed of necessary parts, with others having no
 * effect
 * 
 * @author simpsons
 */
public class ComposedHandler<R> implements ResponseHandler<R> {
    private Consumer<? super IOException> forIOException;
    private Consumer<? super ParseException> forParseException;
    private Consumer<? super Throwable> forThrowable;
    private BiConsumer<? super Integer, ? super R> forResponse;

    /**
     * Set the action to take when a response is returned.
     * 
     * @param action the action to take
     * 
     * @return this object
     */
    public ComposedHandler<R>
        onResponse(BiConsumer<? super Integer, ? super R> action) {
        this.forResponse = action;
        return this;
    }

    /**
     * Set the action to take on an I/O exception.
     * 
     * @param action the action to take
     * 
     * @return this object
     */
    public ComposedHandler<R>
        onIOException(Consumer<? super IOException> action) {
        this.forIOException = action;
        return this;
    }

    /**
     * Set the action to take on a JSON parsing exception.
     * 
     * @param action the action to take
     * 
     * @return this object
     */
    public ComposedHandler<R>
        onParseException(Consumer<? super ParseException> action) {
        this.forParseException = action;
        return this;
    }

    /**
     * Set the action to take on exception.
     * 
     * @param action the action to take
     * 
     * @return this object
     */
    public ComposedHandler<R>
        onThrowable(Consumer<? super Throwable> action) {
        this.forThrowable = action;
        return this;
    }

    /**
     * Create a handler with no effect for any event.
     * 
     * @return the new handler
     */
    public static <T> ComposedHandler<T> start() {
        return new ComposedHandler<T>();
    }

    private ComposedHandler() {}

    /**
     * {@inheritDoc} This implementation invokes the action specified by
     * {@link #onResponse(BiConsumer)}.
     */
    @Override
    public void response(int code, R rsp) {
        if (forResponse == null) return;
        forResponse.accept(code, rsp);
    }

    /**
     * {@inheritDoc} This implementation invokes the action specified by
     * {@link #onIOException(Consumer)}, or calls
     * {@link #exception(Throwable)} by default.
     */
    @Override
    public void exception(IOException ex) {
        if (forIOException != null) {
            forIOException.accept(ex);
            return;
        }
        exception((Throwable) ex);
    }

    /**
     * {@inheritDoc} This implementation invokes the action specified by
     * {@link #onParseException(Consumer)}, or calls
     * {@link #exception(Throwable)} by default.
     */
    @Override
    public void exception(ParseException ex) {
        if (forParseException != null) {
            forParseException.accept(ex);
            return;
        }
        exception((Throwable) ex);
    }

    /**
     * {@inheritDoc} This implementation invokes the action specified by
     * {@link #onThrowable(Consumer)}.
     */
    @Override
    public void exception(Throwable ex) {
        if (forThrowable != null) {
            forThrowable.accept(ex);
            return;
        }
    }
}
