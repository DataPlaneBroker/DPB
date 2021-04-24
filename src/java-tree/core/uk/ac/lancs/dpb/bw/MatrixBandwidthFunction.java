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

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Expresses bandwidth requirements as a matrix indexed by endpoint
 * number.
 * 
 * @author simpsons
 */
public final class MatrixBandwidthFunction implements BandwidthFunction {
    private static int index(int degree, int from, int to) {
        return from * (degree - 1) + to - (to > from ? 1 : 0);
    }

    /**
     * Start creating a function.
     * 
     * @return a fresh builder with no elements
     * 
     * @constructor
     */
    public static Builder start() {
        return new Builder();
    }

    /**
     * Define a tree-like demand
     * 
     * @param degree the degree of the resultant function
     * 
     * @param root the root goal
     * 
     * @param rootLeafDemand the bandwidth from root to each leaf
     * (ingress) and back (egress)
     * 
     * @param interLeafDemand the bandwidth between each leaf; or
     * {@code null} if not required
     * 
     * @return a matrix bandwidth function matching the specified
     * demands
     * 
     * @throws IllegalArgumentException if the root goal is negative or
     * not less than the degree
     */
    public static MatrixBandwidthFunction
        forTree(int degree, int root, BandwidthPair rootLeafDemand,
                BandwidthRange interLeafDemand) {
        if (root >= degree || root < 0)
            throw new IllegalArgumentException("bad goal: " + root);
        Builder builder = start().degree(degree);
        for (int i = 0; i < degree; i++) {
            if (i == root) continue;
            builder = builder.add(root, i, rootLeafDemand.ingress)
                .add(i, root, rootLeafDemand.egress);
            if (interLeafDemand != null) {
                for (int j = 0; j < degree; j++) {
                    if (j == i) continue;
                    if (j == root) continue;
                    builder = builder.add(i, j, interLeafDemand)
                        .add(j, i, interLeafDemand);
                }
            }
        }
        return builder.build();
    }

    /**
     * Constructs a matrix bandwidth function in stages.
     */
    public static final class Builder {
        int biggestIndex = 0;

        final Map<Integer, Map<Integer, BandwidthRange>> values =
            new HashMap<>();

        Builder() {}

        /**
         * Specify the bandwidth requirement from one endpoint to
         * another.
         * 
         * @param from the index of the source endpoint
         * 
         * @param to the index of the destination endpoint
         * 
         * @param value the rate to apply between from one endpoint to
         * the other
         * 
         * @return this object
         */
        public Builder add(int from, int to, BandwidthRange value) {
            if (from < 0)
                throw new IllegalArgumentException("-ve from: " + from);
            if (to < 0) throw new IllegalArgumentException("-ve to: " + to);
            if (to == from)
                throw new IllegalArgumentException("from = to = " + from);
            values.computeIfAbsent(from, k -> new HashMap<>()).put(to, value);
            biggestIndex = Integer.max(Integer.max(from, to), biggestIndex);
            return this;
        }

        /**
         * Ensure a minimum degree. If any entries previously or yet to
         * be added by {@link #add(int, int, BandwidthRange)} refer to
         * endpoints outside the range &#91;0, <var>n</var>&#41;, this
         * call will have no effect.
         * 
         * @param n the degree
         * 
         * @return this object
         */
        public Builder degree(int n) {
            this.biggestIndex = Integer.max(biggestIndex, n - 1);
            return this;
        }

        /**
         * Create a matrix from the current settings.
         * 
         * @return a new bandwidth function based on the current matrix
         * 
         * @constructor
         */
        public MatrixBandwidthFunction build() {
            final int degree = biggestIndex + 1;
            final int size = biggestIndex * degree;
            BandwidthRange[] array = new BandwidthRange[size];
            for (Map.Entry<Integer,
                           Map<Integer, BandwidthRange>> fromEntries : values
                               .entrySet()) {
                int from = fromEntries.getKey();
                Map<Integer, BandwidthRange> inner = fromEntries.getValue();
                for (Map.Entry<Integer, BandwidthRange> toEntries : inner
                    .entrySet()) {
                    int to = toEntries.getKey();
                    BandwidthRange value = toEntries.getValue();
                    array[index(degree, from, to)] = value;
                }
            }
            return new MatrixBandwidthFunction(degree, array);
        }
    }

    private MatrixBandwidthFunction(int degree, BandwidthRange[] array) {
        this.degree = degree;
        this.array = array;
    }

    private final int degree;

    private final BandwidthRange[] array;

    /**
     * {@inheritDoc}
     * 
     * @default This implementation iterates over all endpoints as
     * potential sources, omitting those not in the <cite>from</cite>
     * set. For each one, it iterates over all endpoints as
     * destinations, omitting those in the <cite>from</cite> set, and
     * skipping combinations where the source and destination are the
     * same. The corresponding rates of the selected source-destination
     * tuples are collected, and their sum is returned.
     */
    @Override
    public BandwidthRange get(BitSet fromSet) {
        BandwidthRange sum = BandwidthRange.at(0.0);
        for (int from = 0; from < degree; from++) {
            if (!fromSet.get(from)) continue;
            for (int to = 0; to < degree; to++) {
                if (to == from) continue;
                if (fromSet.get(to)) continue;
                sum = BandwidthRange.add(sum, array[index(degree, from, to)]);
            }
        }
        return sum;
    }

    /**
     * {@inheritDoc}
     * 
     * @default This method sums up the forward and reverse requirements
     * in a single loop.
     */
    @Override
    public BandwidthPair getPair(BitSet fromSet) {
        BandwidthRange fwdSum = BandwidthRange.at(0.0),
            revSum = BandwidthRange.at(0.0);
        for (int from = 0; from < degree; from++) {
            if (fromSet.get(from)) {
                for (int to = 0; to < degree; to++) {
                    if (to == from) continue;
                    if (fromSet.get(to)) continue;
                    fwdSum = BandwidthRange.add(fwdSum,
                                                array[index(degree, from, to)]);
                }
            } else {
                for (int to = 0; to < degree; to++) {
                    if (to == from) continue;
                    if (!fromSet.get(to)) continue;
                    revSum = BandwidthRange.add(revSum,
                                                array[index(degree, from, to)]);
                }
            }
        }
        BandwidthPair result = BandwidthPair.of(fwdSum, revSum);
        return result;
    }

    @Override
    public String asScript() {
        return DEGREE_FIELD_NAME + " = " + degree() + "                    \n"
            + "data = [                                                    \n"
            + IntStream.range(0, array.length)
                .mapToObj(i -> array[i] == null ? "    None" :
                    "    [ " + array[i].min() + ", " + array[i].max() + " ]")
                .collect(Collectors.joining(",\n"))
            + " ]                                                          \n"
            + "@staticmethod                                               \n"
            + "def add_ranges(a, b):                                       \n"
            + "    if a is None:                                           \n"
            + "        return b                                            \n"
            + "    if b is None:                                           \n"
            + "        return a                                            \n"
            + "    minv = a[0] + b[0]                                      \n"
            + "    maxv = None if a[1] is None else                      \\\n"
            + "      None if b[1] is None else (a[1] + b[1])               \n"
            + "    return [ minv, maxv ]                                   \n"
            + "@staticmethod                                               \n"
            + "def index(frm, to):                                         \n"
            + "    step = 1 if to > frm else 0                             \n"
            + "    return frm * " + (degree() - 1) + " + step              \n"
            + "@classmethod                                                \n"
            + "def " + GET_FUNCTION_NAME + "(cls, bits):                   \n"
            + "    sm = [ 0, 0 ]                                           \n"
            + "    for frm in range(0, " + degree() + "):                  \n"
            + "        if (bits & (1 << frm)) == 0:                        \n"
            + "            continue                                        \n"
            + "        for to in range(0, " + degree() + "):               \n"
            + "            if (to == frm):                                 \n"
            + "                continue                                    \n"
            + "            if (bits & (1 << to)) != 0:                     \n"
            + "                continue                                    \n"
            + "            extra = cls.data[cls.index(frm, to)]            \n"
            + "            sm = cls.add_ranges(sm, extra)                  \n"
            + "    return sm                                               \n";
    }

    @Override
    public int degree() {
        return degree;
    }

    /**
     * @undocumented
     */
    public static void main(String[] args) {
        BandwidthFunction bw = MatrixBandwidthFunction.start().degree(4)
            .add(0, 1, BandwidthRange.between(1, 2))
            .add(0, 2, BandwidthRange.between(1, 2))
            .add(0, 3, BandwidthRange.between(1, 2)).build();
        System.out.printf("Func:%n%s",
                          ScriptBandwidthFunction.indent(bw.asScript()));
    }
}
