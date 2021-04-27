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

/**
 * Defines graphs of vertices, ports, edges, edge capacities, demands
 * and costs. These in turn model physical networks, their capabilities,
 * and demands place upon them.
 * 
 * <p>
 * A unidirectional capacity (such as available bandwidth in one
 * direction along an edge) is expressed as a {@link Capacity}. It
 * includes a minimum guaranteed capacity (which may be as low as zero),
 * and a maximum permitted capacity (which may be infinite).
 * 
 * <p>
 * A bidirectional capacity is expressed as a {@link BidiCapacity}, and
 * can model both an edge and the I/O of a port.
 * 
 * <p>
 * An {@link Edge} is a connection of two ports, each of which belongs
 * to a vertex. Although the edge is bidirectional, it has an
 * orientation with respect to its ports. An edge has an
 * ambi-directional cost (which could model, say, latency), and a
 * bidirectional capacity (which could model bandwidth in each
 * direction).
 * 
 * <p>
 * A {@link DemandFunction} describes the demand placed on an edge's
 * capacity, assuming that it contributes in a certain way to a tree
 * connecting a subset of vertices identified as goals. As part of a
 * tree, and without traversing the edge, a subset of goals will be
 * reachable from its start vertex, while the complement of the goals
 * will be reachable from its finish. A demand function has two
 * properties relating to its utility in hierarchical network models.
 * First, it can be expressed as a self-contained script, and so be
 * transmitted for remote execution. Second, it can be reduced, i.e.,
 * its goals can be aggregated.
 * 
 * <p>
 * The following implementations of {@link DemandFunction} are available
 * to the user:
 * 
 * <ul>
 * 
 * <li>{@link FlatDemandFunction} &mdash; All edges have the same
 * bandwidth range. This is likely to create bottlenecks when trees are
 * attenuated over a graph.
 * 
 * <li>{@link PairDemandFunction} &mdash; Each endpoint is assigned
 * upstream and downstream requirements, and edge bandwidth requirement
 * is derived as the minimum of the sum of the upstream ingress
 * bandwidths and the sum of the downstream egress bandwidths. This is
 * the original form of DPB bandwidth expression.
 * 
 * <li>{@link MatrixDemandFunction} &mdash; A matrix enumerates
 * bandwidth requirements between any pair of endpoints. An edge's
 * requirements is then the sum of all cells that are in the
 * <cite>from</cite> set but not the <cite>to</cite> set, and vice
 * versa. This allows a much richer bandwidth expression, without using
 * an inordinate amount of memory.
 * 
 * </ul>
 * 
 * <p>
 * A user can, of course, define their own.
 */
package uk.ac.lancs.dpb.graph;
