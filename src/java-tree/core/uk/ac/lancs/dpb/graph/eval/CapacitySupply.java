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

package uk.ac.lancs.dpb.graph.eval;

import java.util.Random;
import uk.ac.lancs.dpb.graph.BidiCapacity;
import uk.ac.lancs.dpb.graph.Capacity;

/**
 * Chooses modelled edge capacity based on cost and start/finish degree.
 */
@FunctionalInterface
public interface CapacitySupply {
    /**
     * Select a capacity based on the cost of the edge and the degrees
     * of its vertices.
     *
     * @param cost the cost of the edge
     *
     * @param startDegree the degree of the start vertex of the edge
     *
     * @param finishDegree the degree of the finish vertex of the edge
     * 
     * @param maxDegree the maximum degree of any vertex in the graph
     * 
     * @param maxCost the maximum cost of any edge in the graph
     *
     * @return a capacity to be assigned to the edge
     */
    BidiCapacity getCapacity(double cost, int startDegree, int finishDegree,
                             int maxDegree, double maxCost);

    /**
     * Define capacities based solely in random ranges.
     * 
     * @param rng the random-number generator
     * 
     * @param minGuaranteed the minimum guaranteed bandwidth
     * 
     * @param guaranteedRange a uniformly random range to add to the
     * minimum guaranteed bandwidth
     * 
     * @param minExcess the minimum excess bandwidth
     * 
     * @param excessRange a uniformly random range to add to the minimum
     * excess bandwidth
     * 
     * @return a supply of capacities based on the provided parameters
     */
    static CapacitySupply ranged(Random rng, double minGuaranteed,
                                 double guaranteedRange, double minExcess,
                                 double excessRange) {
        return (cost, startDegree, finishDegree, maxDegree,
                maxCost) -> BidiCapacity
                    .of(Capacity.base(minGuaranteed, guaranteedRange),
                        Capacity.base(minExcess, excessRange));
    }
}
