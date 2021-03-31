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
 * Specifies ways to express bandwidth requirements of endpoints, from
 * which the requirements of any edge of a tree connecting those
 * endpoints can be computed.
 * 
 * <p>
 * A single bandwidth requirement is expressed as a
 * {@link BandwidthRange}. It includes a minimum guaranteed bandwidth
 * (which may be as low as zero), and a maximum permitted bandwidth
 * (which may be infinite).
 * 
 * <p>
 * An aggregate network consists of a graph of edges with inferior
 * networks (some of which might also be aggregates) at the vertices,
 * and the available capacities of the inferiors and the connecting
 * edges are known to the aggregate. A user of the aggregate requests
 * that a tree be formed from the graph edges such that it connects a
 * set of at least two endpoints, and that sufficient bandwidth should
 * be allocated on each edge so that the endpoints experience a certain
 * minimum capacity. Regardless of the algorithm used by the aggregate
 * to select a suitable tree, the algorithm needs to know what the
 * demands on an individual edge are, given that a non-empty subset of
 * endpoints will be reachable in one direction, and a non-empty
 * complement of endpoints will be reachable in the other. The interface
 * {@link BandwidthFunction} abstracts that computation, allowing the
 * user to determine it without knowledge of the algorithm. The
 * constraints imposed on implementations allow it:
 * 
 * <ul>
 * 
 * <li>to be transmitted as a self-contained script, so that the user
 * who defines it and the network that uses it can be separate; and
 * 
 * <li>to be reduced, so that an aggregate that delegates connection
 * establishment to an inferior network can derive a new function to
 * submit to the inferior using endpoints that are meaningful to the
 * inferior.
 * 
 * </ul>
 * 
 * <p>
 * The following implementations of {@link BandwidthFunction} are
 * available to the user:
 * 
 * <ul>
 * 
 * <li>{@link FlatBandwidthFunction} &mdash; All edges have the same
 * bandwidth range. This is likely to create bottlenecks when trees are
 * attenuated over a graph.
 * 
 * <li>{@link PairBandwidthFunction} &mdash; Each endpoint is assigned
 * upstream and downstream requirements, and edge bandwidth requirement
 * is derived as the minimum of the sum of the upstream ingress
 * bandwidths and the sum of the downstream egress bandwidths. This is
 * the original form of DPB bandwidth expression.
 * 
 * <li>{@link MatrixBandwidthFunction} &mdash; A matrix enumerates
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
package uk.ac.lancs.dpb.bw;
