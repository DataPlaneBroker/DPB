
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

import uk.ac.lancs.routing.span.DistanceVectorGraph;
import uk.ac.lancs.routing.span.HashableEdge;

public class TestDV {
    public static void main(String[] args) {
        DistanceVectorGraph<String> fibData = new DistanceVectorGraph<>();
        fibData.addTerminal("A");
        fibData.addTerminal("B");
        fibData.addTerminal("C");
        fibData.addEdge(HashableEdge.of("A", "D"), 1.0);
        fibData.addEdge(HashableEdge.of("B", "E"), 1.0);
        fibData.addEdge(HashableEdge.of("C", "F"), 1.0);
        fibData.addEdge(HashableEdge.of("D", "E"), 1.0);
        fibData.addEdge(HashableEdge.of("E", "F"), 1.0);
        fibData.addEdge(HashableEdge.of("F", "G"), 1.0);
        fibData.addEdge(HashableEdge.of("G", "H"), 1.0);
        fibData.addEdge(HashableEdge.of("H", "D"), 1.0);

        fibData.update();
        System.out.println("RTs: " + fibData.getFIBs());
        System.out.println("Loads: " + fibData.getEdgeLoads());

        fibData.removeEdge(HashableEdge.of("F", "E"));

        fibData.update();
        System.out.println("RTs: " + fibData.getFIBs());
        System.out.println("Loads: " + fibData.getEdgeLoads());
    }
}
