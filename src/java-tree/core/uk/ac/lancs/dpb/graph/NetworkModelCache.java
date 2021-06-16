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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Caches results from a network model. The model represents the
 * capacity of an inferior network with respect to a sub-service that
 * currently forms part of a service being defined by the superior. The
 * superior is searching for solutions across a topology formed from
 * vertices which are inferior networks, and in evaluating potential
 * solutions, it must incorporate (estimations of) costs of each
 * solution traversing an inferior. This class references a network
 * model, and the demand function the superior is using to find
 * solutions. Given a particular solution, the superior knows which
 * ports of an inferior are connected, and which of its goals are to be
 * reached from each of those ports.
 * 
 * <p>
 * For example, the superior might be connecting 4 goals [0,3], with a
 * demand function (necessarily) of degree 4. It first obtains a model
 * from each inferior, and then searches for solutions using its
 * topology and the demand function. Suppose one inferior has 6 ports
 * [0,5], and that a candidate solution traverses a vertex (an inferior)
 * such that goals 1 and 2 are reachable from port 4, goal 0 from port
 * 5, and goal 3 from port 2. This reachability can be expressed as the
 * map &#123; 2 &#8614; &#123; 3 &#125;, 4 &#8614; &#123; 1, 2 &#125;, 5
 * &#8614; &#123; 0 &#125; &#125;. (Note that the union of these range
 * values is the set of all goals. The domain is a subset of the
 * inferior's port set.)
 * 
 * <p>
 * The three goal sets can used to reduce the demand function (see
 * {@link DemandFunction#reduce(List)}, and the domain set can be passed
 * with the reduced function to the model to yield the cost of
 * traversing that vertex in the candidate solution. (The goal sets and
 * the ports must be provided as sequences with the same length and
 * order.)
 * 
 * <p>
 * The class helps by caching the result using the reachability map as a
 * key. A
 * {@link ConcurrentHashMap#computeIfAbsent(Object, java.util.function.Function)}
 * is used to allow the cache to be used concurrently.
 *
 * @author simpsons
 */
public final class NetworkModelCache {
    private final NetworkModel base;

    private final DemandFunction demand;

    public NetworkModelCache(NetworkModel base, DemandFunction demand) {
        this.base = base;
        this.demand = demand;
    }

    private final Map<Map<Integer, BitSet>, Double> cache =
        new ConcurrentHashMap<>();

    /**
     * Evaluate the model, given the sets of superior goals reachable
     * from each port, caching the result. Only ports with non-empty
     * sets need to be included.
     * 
     * @param groups the sets of goals of the demand function that are
     * reachable on each of the model's ports
     * 
     * @return the evaluation of the model with the specified inputs
     */
    public double evaluate(Map<? extends Number, ? extends BitSet> groups) {
        /* Create an immutable copy of the group specification, to be
         * used as a key. Strip out empty */
        Map<Integer, BitSet> key =
            groups.entrySet().stream().filter(e -> !e.getValue().isEmpty())
                .collect(Collectors
                    .toMap(e -> e.getKey().intValue(),
                           e -> e.getValue().get(0, demand.degree())));

        return cache.computeIfAbsent(key, this::compute);
    }

    private double compute(Map<Integer, BitSet> key) {
        /* Get the entries in a specific order. */
        List<Map.Entry<Integer, BitSet>> entries =
            key.entrySet().stream().collect(Collectors.toList());
        List<Integer> ports = entries.stream().map(Map.Entry::getKey)
            .collect(Collectors.toList());
        List<BitSet> wang = entries.stream().map(Map.Entry::getValue)
            .collect(Collectors.toList());
        DemandFunction subdemand = demand.reduce(wang);
        return base.evaluate(ports, subdemand);
    }
}
