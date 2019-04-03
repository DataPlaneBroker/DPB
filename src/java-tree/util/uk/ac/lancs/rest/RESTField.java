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

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;

/**
 * Identifies a portion of a request path, with an associated parser and
 * value type.
 * 
 * @param <E> the value type
 * 
 * @author simpsons
 */
public final class RESTField<E> {
    private final Function<? super String, E> parser;
    private final Function<? super Matcher, String> key;
    private final Supplier<? extends E> defaulter;

    private RESTField(Function<? super Matcher, String> key,
                      Function<? super String, E> parser,
                      Supplier<? extends E> defaultValue) {
        if (key == null) throw new NullPointerException("key");
        if (parser == null) throw new NullPointerException("key");
        if (defaultValue == null) throw new NullPointerException("edfaulter");
        this.parser = parser;
        this.key = key;
        this.defaulter = defaultValue;
    }

    /**
     * Collects configuration for a REST field in a request path.
     * 
     * @param <E> the value type
     * 
     * @author simpsons
     */
    public static final class Builder<E> {
        Function<? super String, E> parser;
        Function<? super Matcher, String> key;
        Supplier<? extends E> defaulter = () -> null;

        Builder(Function<? super String, E> parser) {
            this.parser = parser;
        }

        /**
         * Set a generator for a default value for the field.
         * 
         * @param defaulter the generator of a default value
         * 
         * @return this object
         */
        public Builder<E> or(Supplier<? extends E> defaulter) {
            this.defaulter = defaulter;
            return this;
        }

        /**
         * Set the default value for the field.
         * 
         * @param defaultValue the default value
         * 
         * @return this object
         */
        public Builder<E> or(E defaultValue) {
            this.defaulter = () -> defaultValue;
            return this;
        }

        Builder<E> from(Function<? super Matcher, String> key) {
            this.key = key;
            return this;
        }

        /**
         * Select the group that supplies the unparsed value.
         * 
         * @param key the group name
         * 
         * @return this object
         */
        public Builder<E> from(String key) {
            return from((m) -> m.group(key));
        }

        /**
         * Select the group that supplies the unparsed value.
         * 
         * @param key the group number
         * 
         * @return this object
         */
        public Builder<E> from(int key) {
            return from((m) -> m.group(key));
        }

        /**
         * Create the field with the current settings.
         * 
         * @return the created field
         */
        public RESTField<E> done() {
            return new RESTField<>(key, parser, defaulter);
        }
    }

    /**
     * Start building a field.
     * 
     * @param parser a converter from string to the required type
     * 
     * @return a builder to which other settings can be applied
     * 
     * @constructor
     */
    public static <T> Builder<T> of(Function<? super String, T> parser) {
        return new Builder<>(parser);
    }

    /**
     * Start building a plain string field.
     * 
     * @return a builder to which other settings can be applied
     * 
     * @constructor
     */
    public static Builder<String> ofString() {
        return new Builder<>(Function.identity());
    }

    /**
     * Start building an integer field.
     * 
     * @return a builder to which other settings can be applied
     * 
     * @constructor
     */
    public static Builder<Integer> ofInt(int radix) {
        return new Builder<>((s) -> Integer.valueOf(s, radix));
    }

    E resolve(Matcher m) {
        String text = key.apply(m);
        if (text == null) return defaulter.get();
        return parser.apply(text);
    }
}
