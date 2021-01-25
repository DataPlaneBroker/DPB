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
 * Specifies ways to express bandwidth requirements of endpoints for
 * computing requirements of tree edges. The primary interface is
 * {@link BandwidthFunction}. Its primary implementations are:
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
 * requirements is then the sum of all cells that are in the 'from' set
 * but not the 'to' set, and vice versa. This allows a much richer
 * bandwidth expression, without using an inordinate amount of memory.
 * 
 * </ul>
 */
package uk.ac.lancs.routing.metric.bandwidth;
