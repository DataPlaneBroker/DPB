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

import java.math.BigInteger;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;

/**
 * Identifies a portion of a request path, with an associated parser and
 * value type. A field can usually be defined statically, e.g.:
 * 
 * <pre>
 * static final RESTField&lt;String&gt;
 *         FOO_FIELD = RESTField.ofString()
 *                              .from("foo")
 *                              .done();
 * </pre>
 * 
 * <p>
 * A field's value is obtained through
 * {@link RESTContext#get(RESTField)} and
 * {@link RESTContext#getText(RESTField)}:
 * 
 * <pre>
 * RESTHandler handler = (req, rsp, ctxt, rest) -&gt; {
 *     String foo = rest.get(FOO_FIELD);
 *     <var>...</var>
 * }
 * </pre>
 * 
 * <p>
 * This looks for a capture named <samp>foo</samp> on the request URI,
 * and returns its raw value as a string. For more structured data, use
 * one of the other <code>of<var>Format</var></code> methods, or use the
 * generic {@link #of(Function)} to perform conversions to other types.
 * 
 * @param <T> the type of the field's value
 * 
 * @author simpsons
 */
public final class RESTField<T> {
    private final Function<? super String, T> parser;
    private final Function<? super Matcher, String> key;
    private final Supplier<? extends T> defaulter;

    private RESTField(Function<? super Matcher, String> key,
                      Function<? super String, T> parser,
                      Supplier<? extends T> defaultValue) {
        if (key == null) throw new NullPointerException("key");
        if (parser == null) throw new NullPointerException("parser");
        if (defaultValue == null)
            throw new NullPointerException("defaultValue");
        this.parser = parser;
        this.key = key;
        this.defaulter = defaultValue;
    }

    /**
     * Collects configuration for a REST field in a request path. A set
     * of static fields on {@link RESTField} create a builder for a
     * field of a specific type (see {@link RESTField#of(Function)},
     * {@link RESTField#ofInt()}, {@link RESTField#ofString()}, and
     * others), and then methods of the builder configure how the raw
     * text of the field is extracted from a URI path (see
     * {@link #from(String)} and {@link #from(int)}), and what default
     * value to return if the field isn't present
     * (#{@link #or(Supplier)} and {@link #or(Object)}). Finally,
     * {@link #done()} should be called to yield a concept of the field
     * that the user is building. This can be performed in a static
     * context, e.g.:
     * 
     * <pre>
     * static final RESTField&lt;UUID&gt; UUID_FIELD =
     *     RESTField.ofUUID()
     *              .from("uuid")
     *              .or(UUID.fromString("0000000000000000")
     *              .done();
     * </pre>
     * 
     * @param <T> the type of the field's value
     * 
     * @author simpsons
     */
    public static final class Builder<T> {
        Function<? super String, T> parser;
        Function<? super Matcher, String> key;
        Supplier<? extends T> defaulter = () -> null;

        Builder(Function<? super String, T> parser) {
            this.parser = parser;
        }

        /**
         * Set a generator for a default value for the field.
         * 
         * @param defaulter the generator of a default value
         * 
         * @return this object
         */
        public Builder<T> or(Supplier<? extends T> defaulter) {
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
        public Builder<T> or(T defaultValue) {
            this.defaulter = () -> defaultValue;
            return this;
        }

        Builder<T> from(Function<? super Matcher, String> key) {
            this.key = key;
            return this;
        }

        /**
         * Set the field to a fixed value, independent of the request
         * URI path.
         * 
         * @param value the fixed value
         * 
         * @return this object
         */
        public Builder<T> fixed(T value) {
            return from(m -> null).or(value);
        }

        /**
         * Select the group that supplies the unparsed value.
         * 
         * @param key the group name
         * 
         * @return this object
         */
        public Builder<T> from(String key) {
            return from((m) -> m.group(key));
        }

        /**
         * Select the group that supplies the unparsed value.
         * 
         * @param key the group number
         * 
         * @return this object
         */
        public Builder<T> from(int key) {
            return from((m) -> m.group(key));
        }

        /**
         * Create the field with the current settings.
         * 
         * @return the created field
         * 
         * @constructor
         */
        public RESTField<T> done() {
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
     * Start building a plain string field. The supplied string is
     * yielded as-is.
     * 
     * @return a builder to which other settings can be applied
     * 
     * @constructor
     */
    public static Builder<String> ofString() {
        return new Builder<>(Function.identity());
    }

    /**
     * Start building an infinite-precision integer field. The string is
     * parsed using {@link BigInteger#BigInteger(String, int)} with the
     * given radix.
     * 
     * @param radix the radix for parsing the string
     * 
     * @return a builder to which other settings can be applied
     * 
     * @constructor
     */
    public static Builder<BigInteger> ofBigInteger(int radix) {
        return new Builder<>(s -> new BigInteger(s, radix));
    }

    /**
     * Start building an infinite-precision decimal integer field. The
     * string is parsed using {@link BigInteger#BigInteger(String, int)}
     * with a radix of 10.
     * 
     * @return a builder to which other settings can be applied
     * 
     * @constructor
     */
    public static Builder<BigInteger> ofBigInteger() {
        return ofBigInteger(10);
    }

    /**
     * Start building an infinite-precision hexdecimal integer field.
     * The string is parsed using
     * {@link BigInteger#BigInteger(String, int)} with a radix of 16.
     * 
     * @return a builder to which other settings can be applied
     * 
     * @constructor
     */
    public static Builder<BigInteger> ofHexBigInteger() {
        return ofBigInteger(16);
    }

    /**
     * Start building an integer field. The string is parsed using
     * {@link Integer#valueOf(String, int)} with the given radix.
     * 
     * @param radix the radix for parsing the string
     * 
     * @return a builder to which other settings can be applied
     * 
     * @constructor
     */
    public static Builder<Integer> ofInt(int radix) {
        return new Builder<>((s) -> Integer.valueOf(s, radix));
    }

    /**
     * Start building a decimal integer field. The string is parsed
     * using {@link Integer#valueOf(String, int)} with a radix of 10.
     * 
     * @return a builder to which other settings can be applied
     * 
     * @constructor
     */
    public static Builder<Integer> ofInt() {
        return ofInt(10);
    }

    /**
     * Start building a hexadecimal integer field. The string is parsed
     * using {@link Integer#valueOf(String, int)} with a radix of 16.
     * 
     * @return a builder to which other settings can be applied
     * 
     * @constructor
     */
    public static Builder<Integer> ofHexInt() {
        return ofInt(16);
    }

    /**
     * Start building a long integer field. The string is parsed using
     * {@link Long#valueOf(String, int)} with the given radix.
     * 
     * @param radix the radix for parsing the string
     * 
     * @return a builder to which other settings can be applied
     * 
     * @constructor
     */
    public static Builder<Long> ofLong(int radix) {
        return new Builder<>((s) -> Long.valueOf(s, radix));
    }

    /**
     * Start building a long decimal integer field. The string is parsed
     * using {@link Long#valueOf(String, int)} with a radix of 10.
     * 
     * @return a builder to which other settings can be applied
     * 
     * @constructor
     */
    public static Builder<Long> ofLong() {
        return ofLong(10);
    }

    /**
     * Start building a long hexdecimal integer field. The string is
     * parsed using {@link Long#valueOf(String, int)} with a radix of
     * 16.
     * 
     * @return a builder to which other settings can be applied
     * 
     * @constructor
     */
    public static Builder<Long> ofHexLong() {
        return ofLong(16);
    }

    /**
     * Start building a UUID field. The string is parsed using
     * {@link UUID#fromString(String)}.
     * 
     * @return a builder to which other settings can be applied
     * 
     * @constructor
     * 
     * @see #UUID_PATTERN
     */
    public static Builder<UUID> ofUUID() {
        return new Builder<>(UUID::fromString);
    }

    /**
     * Sixteen pairs of hex digits separated by optional dashes are
     * recognized. The value is:
     * 
     * <pre>{@value}</pre>
     * 
     * @resume A regex pattern fragment matching a UUID
     */
    public static final String UUID_PATTERN =
        "[0-9a-fA-F]{2}(?:-?[0-9a-fA-F]{2}){15}";

    /**
     * Get the raw field of the pattern match.
     * 
     * @param m the matcher that matched a pattern
     * 
     * @return the raw (unparsed) value of the field, or {@code null} if
     * not present
     */
    String getText(Matcher m) {
        return key.apply(m);
    }

    /**
     * Get the transformed field of the pattern match.
     * 
     * @param m the matcher that matched a pattern
     * 
     * @return the value of the field, or the value provided by the
     * default generator if not present
     */
    T get(Matcher m) {
        String text = key.apply(m);
        if (text == null) return defaulter.get();
        return parser.apply(text);
    }
}
