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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 *
 * @author simpsons
 */
class MappedDemandFunction implements DemandFunction {
    private final DemandFunction base;

    private final int[] mapping;

    public MappedDemandFunction(DemandFunction base, int[] mapping) {
        this.base = base;

        this.mapping = mapping;
    }

    private BitSet map(BitSet from) {
        return from.stream().filter(i -> i < degree()).map(i -> mapping[i])
            .collect(BitSet::new, (s, i) -> s.set(i), (a, b) -> a.or(b));
    }

    @Override
    public Capacity get(BitSet from) {
        return base.get(map(from));
    }

    @Override
    public BidiCapacity getPair(BitSet from) {
        return base.getPair(map(from));
    }

    @Override
    public String asScript() {
        return "class Base:                                               \n"
            + ScriptDemandFunction.indent(base.asScript()) + "mapping = [ "
            + IntStream.of(mapping).mapToObj(Integer::toString)
                .collect(Collectors.joining(", "))
            + " ]                                                        \n"
            + "@classmethod                                              \n"
            + "def map(cls, a):                                          \n"
            + "    b = 0                                                 \n"
            + "    for i in range(0, " + degree() + "):                  \n"
            + "        if a & (1 << i):                                  \n"
            + "            b |= 1 << mapping[i]                          \n"
            + "    return b                                              \n"
            + "@classmethod                                              \n"
            + "def " + GET_FUNCTION_NAME + "(cls, bits):                 \n"
            + "    return Base." + GET_FUNCTION_NAME + "(map(bits))";
    }

    @Override
    public int degree() {
        return base.degree();
    }
}
