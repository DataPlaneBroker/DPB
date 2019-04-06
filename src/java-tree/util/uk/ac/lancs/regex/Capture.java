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

package uk.ac.lancs.regex;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses captures of regular expressions.
 * 
 * @param <T> the value type
 * 
 * @author simpsons
 */
public class Capture<T> {
    private final Function<? super String, ? extends T> transformer;

    private Capture(Function<? super String, ? extends T> transformer) {
        this.transformer = transformer;
    }

    /**
     * Create a capture.
     * 
     * @param <T> the value type
     * 
     * @param transformer Converts text into the value type.
     * 
     * @return the requested capture
     */
    public static <T> Capture<T>
        of(Function<? super String, ? extends T> transformer) {
        return new Capture<>(transformer);
    }

    /**
     * Create a capture for an integer of a given base.
     * 
     * @param radix the base
     * 
     * @return the requested capture
     */
    public static Capture<Integer> ofInt(int radix) {
        return new Capture<>((s) -> Integer.valueOf(s, radix));
    }

    /**
     * Create a capture for a decimal integer.
     * 
     * @return the requested capture
     */
    public static Capture<Integer> ofInt() {
        return ofInt(10);
    }

    /**
     * Create a capture for a hexadecimal integer.
     * 
     * @return the requested capture
     */
    public static Capture<Integer> ofHexInt() {
        return ofInt(0);
    }

    /**
     * Create a capture for an untransformed string.
     * 
     * @return the requested capture
     */
    public static Capture<String> ofString() {
        return new Capture<>(Function.identity());
    }

    /**
     * Get the transformed value of the capture from a match.
     * 
     * @param m the matching context
     * 
     * @return the transformed value, or {@code null} if not present
     */
    public T get(Matcher m) {
        Pattern p = m.pattern();
        int position = lookup(p);
        String text = m.group(position);
        if (text == null) return null;
        return transformer.apply(text);
    }

    void store(Pattern p, int pos) {
        Map<Capture<?>, Integer> index =
            map.computeIfAbsent(p, (k) -> Collections
                .synchronizedMap(new WeakHashMap<>()));

        Integer already = index.putIfAbsent(this, pos);
        if (already != null)
            throw new IllegalArgumentException("already captured from " + p);
    }

    private int lookup(Pattern p) {
        Map<Capture<?>, Integer> index = map.get(p);
        if (index == null)
            throw new IllegalArgumentException("not captured from " + p);
        Integer pos = index.get(this);
        if (pos == null)
            throw new IllegalArgumentException("not captured from " + p);
        return pos;
    }

    private static Map<Pattern, Map<Capture<?>, Integer>> map =
        Collections.synchronizedMap(new WeakHashMap<>());
}
