
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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import uk.ac.lancs.routing.span.Edge;
import uk.ac.lancs.routing.span.Graphs;
import uk.ac.lancs.routing.span.Way;

/**
 * 
 * 
 * @author simpsons
 */
public class TestInitSpan {

    /**
     * @param args
     */
    public static void main(String[] args) {
        Map<Edge<String>, Double> graph = new HashMap<>();
        graph.put(Edge.of("A", "B"), 0.01);
        graph.put(Edge.of("B", "C"), 1.0);
        graph.put(Edge.of("D", "E"), 0.01);
        graph.put(Edge.of("E", "F"), 1.0);
        graph.put(Edge.of("G", "H"), 0.01);
        graph.put(Edge.of("H", "I"), 1.0);
        graph.put(Edge.of("J", "K"), 0.01);
        graph.put(Edge.of("K", "L"), 1.0);
        graph.put(Edge.of("C", "F"), 0.01);
        graph.put(Edge.of("C", "I"), 0.01);
        graph.put(Edge.of("C", "L"), 0.01);
        graph.put(Edge.of("I", "F"), 0.01);
        graph.put(Edge.of("L", "F"), 0.01);
        graph.put(Edge.of("L", "I"), 0.01);
        System.out.printf("Original graph: %s%n", graph);

        Collection<String> terminals = new HashSet<>(Arrays.asList("A", "J"));
        Graphs.prune(terminals, graph.keySet());
        System.out.printf("%nPruned graph: %s for %s%n", graph, terminals);
        System.out.println("  (should be unchanged)");

        Map<String, Map<String, Way<String>>> fibs =
            Graphs.route(terminals, graph);
        System.out.printf("%nFIBs: %s%n", fibs);

        Map<Edge<String>, Double> weights = Graphs.flatten(fibs);
        System.out.printf("%nSpan-weighted graph: %s%n", weights);

        Collection<Edge<String>> tree = Graphs.span(terminals, weights);
        System.out.printf("%nSpanning tree: %s%n", tree);
    }

}
