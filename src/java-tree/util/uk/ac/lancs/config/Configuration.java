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
package uk.ac.lancs.config;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Views a configuration structurally.
 * 
 * @author simpsons
 */
public interface Configuration {
    /**
     * Get a configuration parameter.
     * 
     * @param key the parameter key
     * 
     * @return the parameter's value, or {@code null} if not present
     */
    String get(String key);

    /**
     * Get a subview.
     * 
     * @param prefix the additional prefix to narrow down the available
     * parameters
     * 
     * @return the requested subview
     */
    Configuration subview(String prefix);

    /**
     * List keys in this configuration.
     * 
     * @return the keys
     */
    Iterable<String> keys();

    /**
     * List a subset of keys.
     * 
     * @param condition a condition selecting keys to include
     * 
     * @return the selected keys
     */
    default Iterable<String> keys(Predicate<? super String> condition) {
        return new Iterable<String>() {
            @Override
            public Iterator<String> iterator() {
                return new FilterIterator<>(keys().iterator(), condition);
            }
        };
    }

    /**
     * List transformed keys.
     * 
     * @param <K> the type to transform keys into
     * 
     * @param xform the transformation function
     * 
     * @return the transformed keys
     */
    default <K> Iterable<K>
        keys(Function<? super String, ? extends K> xform) {
        return new Iterable<K>() {
            @Override
            public Iterator<K> iterator() {
                return new TransformIterator<>(keys().iterator(), xform);
            }
        };
    }

    /**
     * List selected and then transformed keys.
     * 
     * @param precondition a condition selecting keys to include
     * 
     * @param <K> the type to transform keys into
     * 
     * @param xform the transformation function
     * 
     * @return the selected and then transformed keys
     */
    default <K> Iterable<K>
        keys(Predicate<? super String> precondition,
             Function<? super String, ? extends K> xform) {
        return new Iterable<K>() {
            @Override
            public Iterator<K> iterator() {
                return new TransformIterator<>(new FilterIterator<>(keys()
                    .iterator(), precondition), xform);
            }
        };
    }

    /**
     * List a selection of transformed keys.
     * 
     * @param <K> the type to transform keys into
     * 
     * @param xform the transformation function
     * 
     * @param postcondition a condition selecting transformed keys
     * 
     * @return the selected transformed keys
     */
    default <K> Iterable<K> keys(Function<? super String, ? extends K> xform,
                                 Predicate<? super K> postcondition) {
        return new Iterable<K>() {
            @Override
            public Iterator<K> iterator() {
                return new FilterIterator<>(new TransformIterator<>(keys()
                    .iterator(), xform), postcondition);
            }
        };
    }

    /**
     * List a selection of transformed selected keys.
     * 
     * @param precondition a condition selecting keys to include
     * 
     * @param <K> the type to transform keys into
     * 
     * @param xform the transformation function
     * 
     * @param postcondition a condition selecting transformed keys
     * 
     * @return the selected transformed keys
     */
    default <K> Iterable<K> keys(Predicate<? super String> precondition,
                                 Function<? super String, ? extends K> xform,
                                 Predicate<? super K> postcondition) {
        return new Iterable<K>() {
            @Override
            public Iterator<K> iterator() {
                return new FilterIterator<>(new TransformIterator<>(new FilterIterator<>(keys()
                    .iterator(), precondition), xform), postcondition);
            }
        };
    }

    /**
     * Normalize a node key. Double dots are condensed to single ones.
     * Leading and trailing dots are removed. <samp>!</samp> eliminates
     * one prior element, and <samp>!!</samp> eliminates all prior
     * elements.
     * 
     * @param key the node key to normalize
     * 
     * @return the normalized node key
     */
    public static String normalizeKey(String key) {
        List<String> parts =
            new LinkedList<>(Arrays.asList(key.split("\\.+")));
        for (ListIterator<String> iter = parts.listIterator(); iter
            .hasNext();) {
            String val = iter.next();
            switch (val) {
            case "":
                iter.remove();
                break;

            case "!":
                iter.remove();
                if (iter.hasPrevious()) {
                    iter.previous();
                    iter.remove();
                }
                break;

            case "!!":
                iter.remove();
                while (iter.hasPrevious()) {
                    iter.previous();
                    iter.remove();
                }
                break;
            }
        }
        return parts.stream().collect(Collectors.joining(".")) + ".";
    }
}
