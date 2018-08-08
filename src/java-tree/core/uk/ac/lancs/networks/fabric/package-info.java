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

/**
 * Provides classes for adapting a network abstraction to a physical
 * switch.
 * 
 * <p>
 * The terminals of a physical switch are <dfn>interfaces</dfn>, which
 * might correspond to physical ports, or to ports with some sort of
 * tagging, or to port aggregations. An interface is described by an
 * implemenation-defined string, and
 * {@link uk.ac.lancs.networks.fabric.Fabric#getInterface(String)} can
 * be used to obtain one.
 * 
 * <p>
 * A physical switch establishes a set of <dfn>bridges</dfn>, each
 * connecting {@link uk.ac.lancs.networks.fabric.Channel}s of a subset
 * of its interfaces with outgoing shaping and incoming metering of
 * bandwidth (an {@link uk.ac.lancs.networks.TrafficFlow}). A switch can
 * be asked to <em>ensure</em> that a bridge exists with
 * {@link uk.ac.lancs.networks.fabric.Fabric#bridge(BridgeListener, Map)}.
 * Bridges should be removed by asking the switch to <em>retain</em> all
 * others, allowing the remote management software of a switch to
 * restart after breakdown without disrupting any existing bridges.
 * 
 * @resume Switching-fabric API
 * 
 * @author simpsons
 */
package uk.ac.lancs.networks.fabric;
