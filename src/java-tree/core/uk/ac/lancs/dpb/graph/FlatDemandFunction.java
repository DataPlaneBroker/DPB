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

package uk.ac.lancs.dpb.graph;

import java.util.BitSet;
import java.util.List;

/**
 * Expresses bandwidth requirements as a single range applying at all
 * edges.
 * 
 * @author simpsons
 */
public final class FlatDemandFunction implements DemandFunction {
    private final int degree;

    private final Capacity rate;

    /**
     * Create a flat bandwidth function.
     * 
     * @param degree the function's degree
     * 
     * @param rate the rate common to all edges
     */
    public FlatDemandFunction(int degree, Capacity rate) {
        this.degree = degree;
        this.rate = rate;
    }

    /**
     * {@inheritDoc}
     * 
     * @default This implementation simply returns the configured rate,
     * and ignores the <cite>from</cite> set.
     */
    @Override
    public Capacity get(BitSet from) {
        return rate;
    }

    /**
     * {@inheritDoc}
     * 
     * @default This implementation returns a pair with the forward and
     * reverse rate set to the configured rate.
     */
    @Override
    public BidiCapacity getPair(BitSet from) {
        return BidiCapacity.of(rate);
    }

    @Override
    public String asScript() {
        return DEGREE_FIELD_NAME + " = " + degree + "                 \n"
            + "@classmethod                                                \n"
            + "def " + GET_FUNCTION_NAME + "(cls, bits):            \n"
            + "    return [ " + rate.min() + ", " + rate.max() + " ]       \n";
    }

    @Override
    public int degree() {
        return degree;
    }

    private static BitSet of(int... membs) {
        BitSet r = new BitSet();
        for (int i : membs)
            r.set(i);
        return r;
    }

    /**
     * @undocumented
     */
    public static void main(String[] args) throws Exception {
        DemandFunction bw =
            new FlatDemandFunction(3, Capacity.between(2, 7));
        System.out.printf("Func:%n%s",
                          ScriptDemandFunction.indent(bw.asScript()));

        DemandFunction sbw = DemandFunction.fromScript(bw.asScript());
        System.out.printf("Func:%n%s",
                          ScriptDemandFunction.indent(sbw.asScript()));

        DemandFunction rbw = bw.reduce(List.of(of(1, 2), of(0)));
        System.out.printf("Func:%n%s",
                          ScriptDemandFunction.indent(rbw.asScript()));
    }
}
