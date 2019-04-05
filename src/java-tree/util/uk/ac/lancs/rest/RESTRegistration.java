/*
 * Copyright 2018,2019, Regents of the University of Lancaster
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

import java.util.Collection;
import java.util.HashSet;
import java.util.regex.Pattern;

/**
 * Contains information necessary to match a REST request.
 * 
 * @author simpsons
 */
public final class RESTRegistration {
    private final Collection<String> methods = new HashSet<>();
    private Pattern pattern;
    private final Collection<RESTField<?>> fields = new HashSet<>();

    private RESTRegistration() {}

    /**
     * Register a handler with the given dispatcher using the current
     * settings.
     * 
     * @param mapper the dispatcher
     * 
     * @param handler the handler to invoke under matching conditions
     */
    public void register(RESTRequestHandlerMapper mapper,
                         RESTRequestHandler handler) {
        if (methods.isEmpty())
            throw new IllegalArgumentException("empty method set");
        if (pattern == null) throw new NullPointerException("pattern");
        mapper.register(methods, pattern, fields, handler);
    }

    /**
     * Deregister any handler under the current settings.
     * 
     * @param mapper the dispatcher
     */
    public void unregister(RESTRequestHandlerMapper mapper) {
        mapper.unregister(methods, pattern);
    }

    /**
     * Specify an additional method to match on.
     * 
     * @param method the additional method
     * 
     * @return this object
     */
    public RESTRegistration on(String method) {
        methods.add(method);
        return this;
    }

    /**
     * Specify a pattern to match the request URI path on. The set of
     * fields is cleared.
     * 
     * @param pattern the pattern to match against
     * 
     * @return this object
     */
    public RESTRegistration at(Pattern pattern) {
        this.pattern = pattern;
        fields.clear();
        return this;
    }

    /**
     * Specify a pattern to match the request URI path on. The set of
     * fields is cleared. The supplied pattern is prefixed with
     * <code>{@value RESTRegistration#PATTERN_PREFIX}</code> and
     * suffixed with
     * <code>{@value RESTRegistration#PATTERN_SUFFIX}</code> before
     * compilation.
     * 
     * @param pattern the pattern to match against
     * 
     * @return this object
     */
    public RESTRegistration at(String pattern) {
        return at(Pattern.compile(PATTERN_PREFIX + pattern + PATTERN_SUFFIX));
    }

    /**
     * Specify a field within the path pattern to extract and convert.
     * 
     * @param field the field descriptor
     * 
     * @return this object
     */
    public RESTRegistration with(RESTField<?> field) {
        fields.add(field);
        return this;
    }

    /**
     * A string prefixed to patterns supplied to {@link #at(String)}
     */
    public static final String PATTERN_PREFIX = "^";

    /**
     * A string suffixes to patterns supplied to {@link #at(String)}
     */
    public static final String PATTERN_SUFFIX = "(?:/|$)?";

    /**
     * Start building a registration.
     * 
     * @constructor
     * 
     * @return a fresh builder
     */
    public static RESTRegistration start() {
        return new RESTRegistration();
    }
}
