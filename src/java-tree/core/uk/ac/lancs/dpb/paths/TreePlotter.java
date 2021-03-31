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

package uk.ac.lancs.dpb.paths;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import uk.ac.lancs.dpb.bw.BandwidthFunction;
import uk.ac.lancs.dpb.bw.BandwidthPair;

/**
 * Plots trees over a graph connecting specific vertices.
 *
 * @author simpsons
 */
public interface TreePlotter {
    /**
     * Find subsets of edges that form trees connecting specific
     * vertices.
     * 
     * @param <V> the vertex type
     * 
     * @param goalOrder the set of vertices to be connected
     * 
     * @param edges the edges of the graph from which to form trees,
     * including their capacities and cumulative costs
     * 
     * @param bwreq the bandwidth requirements, indexed by goal
     * 
     * @return a means to iterate over the solutions
     */
    <V> Iterable<? extends Map<? extends Edge<V>, ? extends BandwidthPair>>
        plot(List<? extends V> goalOrder, BandwidthFunction bwreq,
             Collection<? extends Edge<V>> edges);
}
