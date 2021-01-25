/*
 * Copyright (c) 2021, Regents of the University of Lancaster
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the
 *   distribution.
 *
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived
 *   from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *
 *  Author: Steven Simpson <s.simpson@lancaster.ac.uk>
 */

package uk.ac.lancs.routing.metric.bandwidth;

import java.math.BigInteger;
import java.util.BitSet;
import java.util.List;

/**
 * Determines the bandwidth requirements of any edge in a tree, given
 * the endpoints reachable from one end of that edge.
 * 
 * @author simpsons
 */
public interface BandwidthFunction {
    /**
     * Get the bandwidth requirement for an edge.
     *
     * @param from the set of endpoints connected to the transmitting
     * vertex of the edge
     *
     * @return the bandwidth requirement
     *
     * @throws IllegalArgumentException if the set is invalid, e.g., it
     * contains members outside the range defined by {@link #degree()},
     * it contains all such members, or it is empty
     */
    BandwidthRange apply(BitSet from);

    /**
     * Get a JavaScript representation of the function. The string must
     * be an object declaration with two fields. One is
     * <samp>degree</samp>, giving the function degree. The other must
     * be a function called <samp>apply</samp>, taking a single
     * argument, a bit set, to be interpreted as the set of upstream
     * endpoints, and return a map of two numbers, the minimum and
     * maximum upstream bandwidth of the edge.
     * 
     * @return the JavaScript representation
     */
    String asJavaScript();

    /**
     * Get the function's degree. The argument to {@link #apply(BitSet)}
     * must only contain set bits in positions zero to one less than the
     * degree.
     *
     * @return the function's degree
     */
    int degree();

    private static void reverse(byte[] array) {
        for (int i = 0; i < array.length / 2; i++) {
            byte tmp = array[i];
            array[i] = array[array.length - 1 - i];
            array[array.length - 1 - i] = tmp;
        }
    }

    /**
     * Reduce this function by grouping together endpoints.
     * 
     * @param groups the proposed set of endpoint groups
     * 
     * @return a new function whose endpoint indices correspond to
     * groups of endpoints of this function
     * 
     * @throws IllegalArgumentException if a proposed group contains
     * more bits than indicated by the degree
     * 
     * @default The default behaviour is to create a
     * {@link ReducedBandwidthFunction} with the proposed groups using
     * this function as the base. Then, if the new function has a
     * sufficiently small degree, a {@link TableBandwidthFunction} is
     * derived from it, and returned. Otherwise, the reduced function is
     * returned.
     */
    default BandwidthFunction reduce(List<? extends BitSet> groups) {
        BandwidthFunction result = new ReducedBandwidthFunction(this, groups);
        if (result.degree() > 8) return result;
        return new TableBandwidthFunction(result);
    }

    static BigInteger toBigInteger(BitSet set) {
        byte[] bs = set.toByteArray();
        reverse(bs);
        return new BigInteger(bs);
    }
}
