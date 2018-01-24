
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
import uk.ac.lancs.routing.span.Spans;
import uk.ac.lancs.routing.span.Way;

/**
 * 
 * 
 * @author simpsons
 */
public class TestOddSpan {
    /**
     * @undocumented
     */
    public static void main(String[] args) throws Exception {
        Map<Edge<String>, Double> graph = new HashMap<>();
        graph.put(Edge.of("A", "D"), 1.0);
        graph.put(Edge.of("E", "D"), 1.0);
        graph.put(Edge.of("E", "F"), 1.0);
        graph.put(Edge.of("E", "G"), 1.0);
        graph.put(Edge.of("G", "F"), 1.0);
        graph.put(Edge.of("G", "H"), 1.0);
        graph.put(Edge.of("H", "I"), 1.0);
        graph.put(Edge.of("I", "D"), 1.0);
        graph.put(Edge.of("I", "J"), 1.0);
        graph.put(Edge.of("J", "H"), 1.0);
        graph.put(Edge.of("F", "C"), 1.0);
        graph.put(Edge.of("B", "H"), 1.0);
        System.out.printf("Original graph: %s%n", graph);

        Collection<String> terminals =
            new HashSet<>(Arrays.asList("A", "B", "C"));
        Spans.prune(terminals, graph.keySet());
        System.out.printf("%nPruned graph: %s for %s%n", graph, terminals);
        System.out.println("  (should be unchanged)");

        Map<String, Map<String, Way<String>>> fibs =
            Spans.route(terminals, graph);
        System.out.printf("%nFIBs: %s%n", fibs);

        Map<Edge<String>, Double> weights =
            Spans.flatten(fibs, Edge::of);
        System.out.printf("%nSpan-weighted graph: %s%n", weights);

        Collection<Edge<String>> tree =
            Spans.span(terminals, weights);
        System.out.printf("%nSpanning tree: %s%n", tree);
    }
}
