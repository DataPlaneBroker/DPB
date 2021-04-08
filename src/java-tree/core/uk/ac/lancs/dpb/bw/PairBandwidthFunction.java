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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Expresses bandwidth requirements as ingress/egress pairs of ranges,
 * one per endpoint. This corresponds to DPB's original expression of
 * bandwidths.
 * 
 * @author simpsons
 */
public final class PairBandwidthFunction implements BandwidthFunction {
    private final BandwidthPair[] pairs;

    /**
     * Create a bandwidth function from an array of pairs. The number of
     * pairs is the degree of the function.
     * 
     * @param pairs the pairs for each endpoint
     */
    public PairBandwidthFunction(BandwidthPair... pairs) {
        this.pairs = Arrays.copyOf(pairs, pairs.length);
    }

    /**
     * Create a bandwidth function from a list of pairs. The number of
     * pairs is the degree of the function.
     * 
     * @param pairs the pairs for each endpoint
     */
    public PairBandwidthFunction(List<? extends BandwidthPair> pairs) {
        this.pairs = pairs.toArray(new BandwidthPair[pairs.size()]);
    }

    /**
     * {@inheritDoc}
     * 
     * @default In this implementation, all the ingress bandwidths of
     * the <cite>from</cite> set are summed, and all the egress
     * bandwidths of the complement are summed. The minimum of these is
     * chosen as the result.
     */
    @Override
    public BandwidthRange get(BitSet from) {
        /* [0] is generated bandwidth on 'from' side; [1] is accepted
         * bandwidth on 'to' side. The result will be the minimum of
         * these. */
        final BandwidthRange[] sum = new BandwidthRange[2];
        Arrays.fill(sum, BandwidthRange.at(0.0));
        for (int i = 0; i < pairs.length; i++) {
            final boolean isSender = from.get(i);
            sum[isSender ? 0 : 1] = BandwidthRange
                .add(sum[isSender ? 0 : 1],
                     isSender ? pairs[i].ingress : pairs[i].egress);
        }
        return BandwidthRange.min(sum[0], sum[1]);
    }

    /**
     * {@inheritDoc}
     * 
     * @default This implementation adds both forward and reverse
     * requirements in the same loop.
     */
    @Override
    public BandwidthPair getPair(BitSet from) {
        /* [0] is the forward ingress/generated sum; [1] is the forward
         * egress/accepted sum. The forward result will be the minimum
         * of these. */
        /* [2] is the reverse ingress/generated sum; [3] is the reverse
         * egress/accepted sum. The reverse result will be the minimum
         * of these. */
        final BandwidthRange[] sum = new BandwidthRange[4];
        Arrays.fill(sum, BandwidthRange.at(0.0));

        for (int i = 0; i < pairs.length; i++) {
            final boolean isSender = from.get(i);
            final int fwd = isSender ? 0 : 1;
            final int rev = isSender ? 3 : 2;
            sum[fwd] = BandwidthRange
                .add(sum[fwd], isSender ? pairs[i].ingress : pairs[i].egress);
            sum[rev] = BandwidthRange
                .add(sum[rev], isSender ? pairs[i].egress : pairs[i].ingress);
        }
        return BandwidthPair.of(BandwidthRange.min(sum[0], sum[1]),
                                BandwidthRange.min(sum[2], sum[3]));
    }

    @Override
    public int degree() {
        return pairs.length;
    }

    @Override
    public String asJavaScript() {
        return "{                                                    \n" + "  "
            + JAVASCRIPT_DEGREE_NAME + " : " + degree()
            + ",                          \n" + "  data : [ "
            + Arrays.asList(pairs).stream()
                .map(r -> "    [ [" + r.ingress.min() + ", " + r.ingress.max()
                    + "],\n" + "      [" + r.egress.min() + ", "
                    + r.egress.max() + "] ]")
                .collect(Collectors.joining(",\n"))
            + " ],                                                   \n"
            + "  add_ranges : function(a, b) {                       \n"
            + "      if (a == null) return b;                        \n"
            + "      if (b == null) return a;                        \n"
            + "      let min = a[0] + b[0];                          \n"
            + "      let max = a[1] == null ? null :                 \n"
            + "                b[1] == null ? null :                 \n"
            + "                 (a[1] + b[1]);                       \n"
            + "      return [ min, max ];                            \n"
            + "  },                                                  \n" + "  "
            + JAVASCRIPT_FUNCTION_NAME
            + " : function(bits) {                            \n"
            + "    var sum = [ null, null ];                         \n"
            + "    for (var i = 0; i < " + degree() + "; i++) {      \n"
            + "      let isRecv = (bits & (1 << i)) != 0;            \n"
            + "      sum[isRecv] =                                   \n"
            + "        this.add_ranges(sum[isRecv],                  \n"
            + "                        this.data[i][isRecv]);        \n"
            + "    }                                                 \n"
            + "    if (sum[0] == null) return [ 0, 0 ];              \n"
            + "    if (sum[1] == null) return [ 0, 0 ];              \n"
            + "    let min = sum[1][0] < sum[0][0] ?                 \n"
            + "      sum[1][0] : sum[0][0];                          \n"
            + "    if (sum[0][1] == null) return [ min, null ];      \n"
            + "    if (sum[1][1] == null) return [ min, null ];      \n"
            + "    let max = sum[1][1] < sum[0][1] ?                 \n"
            + "      sum[1][1] : sum[0][1];                          \n"
            + "    return [ min, max ];                              \n"
            + "  }                                                   \n"
            + "}\n";
    }

    /**
     * @undocumented
     */
    public static void main(String[] args) {
        List<BandwidthPair> pairs = new ArrayList<>();
        pairs.add(BandwidthPair.of(BandwidthRange.at(4.0),
                                   BandwidthRange.at(1.0)));
        pairs.add(BandwidthPair.of(BandwidthRange.at(2.0),
                                   BandwidthRange.at(2.0)));
        pairs.add(BandwidthPair.of(BandwidthRange.at(3.0),
                                   BandwidthRange.at(5.0)));
        pairs.add(BandwidthPair.of(BandwidthRange.at(5.0),
                                   BandwidthRange.at(2.0)));
        BandwidthFunction func = new PairBandwidthFunction(pairs);

        BitSet fwd = new BitSet();
        fwd.set(0);
        fwd.set(1);
        BitSet rev = new BitSet();
        rev.set(0, func.degree());
        rev.xor(fwd);
        System.out.printf("%s -> %s%n", fwd, func.get(fwd));
        System.out.printf("%s <- %s%n", fwd, func.get(rev));
        System.out.printf("%s = %s%n", fwd, func.getPair(fwd));
    }
}
