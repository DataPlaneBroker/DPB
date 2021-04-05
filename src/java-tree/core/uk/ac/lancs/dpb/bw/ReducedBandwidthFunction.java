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

package uk.ac.lancs.dpb.bw;

import java.math.BigInteger;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Expresses bandwidth requirements as an aggregate of those of a larger
 * set of endpoints, with some endpoints grouped together.
 * 
 * @author simpsons
 */
final class ReducedBandwidthFunction implements BandwidthFunction {
    private final BandwidthFunction base;

    private final List<BitSet> groups;

    /**
     * Express bandwidth requirements by reducing the number of
     * endpoints of another function. The number of groups is the degree
     * of the new function. However, it is permitted to supply one less
     * group than the degree; in that case, the final group is implied
     * as the complement of the union of the specified groups.
     * 
     * @param base the base function
     * 
     * @param groups groups of endpoints that shall be treated as single
     * endpoints
     * 
     * @throws IllegalArgumentException if a proposed group contains
     * more bits than indicated by the degree
     */
    public ReducedBandwidthFunction(BandwidthFunction base,
                                    Collection<? extends BitSet> groups) {
        this.base = base;

        /* Make sure that every bit is accounted for no more than
         * once. */
        BitSet remainder = new BitSet();
        remainder.set(0, base.degree());

        for (BitSet g : groups) {
            /* There must be no bits in the group outside of what's
             * left. */
            BitSet copy = remainder.get(0, base.degree());
            copy.or(g);
            if (!copy.equals(remainder))
                throw new IllegalArgumentException("over-used bits " + g
                    + " for degree " + base.degree());

            /* Account for these bits. */
            remainder.andNot(g);
        }

        if (remainder.isEmpty()) {
            /* All bits in the base were accounted for exactly once, so
             * we just use a copy */
            this.groups = groups.stream().map(e -> e.get(0, base.degree()))
                .collect(Collectors.toList());
        } else {
            /* Create a deep copy of the list, and append the
             * remainder. */
            this.groups = Stream
                .concat(groups.stream().map(e -> e.get(0, base.degree())),
                        Stream.of(remainder))
                .collect(Collectors.toList());
        }
    }

    private BitSet orBase(BitSet a, BitSet b) {
        BitSet c = a.get(0, base.degree());
        c.or(b);
        return c;
    }

    /**
     * {@inheritDoc}
     * 
     * @default This implementation translates each of bits in the
     * <cite>from</cite> set into the configured groups, computed their
     * union, and submits this union to the base function, whose result
     * is then the result of this function.
     */
    @Override
    public BandwidthRange get(BitSet from) {
        try {
            BitSet key = from.stream().mapToObj(i -> groups.get(i))
                .reduce(this::orBase).get();
            return base.get(key);
        } catch (NullPointerException | IndexOutOfBoundsException |
                 NoSuchElementException ex) {
            throw new IllegalArgumentException("invalid 'from' set " + from,
                                               ex);
        }
    }

    @Override
    public String asJavaScript() {
        return "{                                                       \n"
            + "  " + JAVASCRIPT_DEGREE_NAME + " : " + degree()
            + ",                             \n" + "  base : "
            + base.asJavaScript() + ",                    \n" + "  groups : [ "
            + groups.stream().map(JavaScriptBandwidthFunction::toBigInteger)
                .map(BigInteger::toString).collect(Collectors.joining(", "))
            + " ]                                                       \n"
            + "  " + JAVASCRIPT_FUNCTION_NAME
            + " : function(set) {                                \n"
            + "    bset = [];                                           \n"
            + "    for (var e = 0; e < " + degree() + "; e++)           \n"
            + "      if (set & (1 << e))                                \n"
            + "        bset |= this.groups[e];                          \n"
            + "    return this.base.apply(bset);                        \n"
            + "  },                                                     \n"
            + "}\n";
    }

    @Override
    public int degree() {
        return groups.size();
    }
}
