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

import java.util.function.Supplier;

/**
 * Provides fields parsed from the path of an HTTP request URI.
 * 
 * @author simpsons
 */
public interface RESTContext {
    /**
     * Get the value of the given field, or generate a default.
     * 
     * @param field the field whose value is sought
     * 
     * @param defaulter the generator of a default value
     * 
     * @return the parsed value of the field, or the default value
     * 
     * @default {@link #get(RESTField)} is invoked, and then
     * {@link Supplier#get()} on <samp>defaultValue</samp> only if the
     * result is {@code null}.
     */
    default <T> T get(RESTField<T> field, Supplier<T> defaulter) {
        T value = get(field);
        if (value == null) return defaulter.get();
        return value;
    }

    /**
     * Get the value of the given field.
     * 
     * @param field the field whose value is sought
     * 
     * @return the parsed value of the field, or {@code null} if the
     * field is absent
     */
    <T> T get(RESTField<T> field);

    /**
     * Get the value of the given field, or a default value.
     * 
     * @param field the field whose value is sought
     * 
     * @param defaultValue the default value
     * 
     * @return the value of the parsed field, or the default value if
     * the field is absent
     */
    default <T> T getOrDefault(RESTField<T> field, T defaultValue) {
        return get(field, () -> defaultValue);
    }
}
