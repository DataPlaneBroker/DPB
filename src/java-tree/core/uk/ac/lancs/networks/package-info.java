/*
 * Copyright 2018,2019, Regents of the University of Lancaster
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
 * The primary class is {@link uk.ac.lancs.networks.NetworkControl},
 * which allows the creation of {@link uk.ac.lancs.networks.Service}s
 * across it, connecting {@link uk.ac.lancs.networks.Circuit}s with
 * certain QoS requirements. Circuits are numbered subdivisions of
 * {@link uk.ac.lancs.networks.Terminal}s, points of access for traffic
 * into and out of a network.
 * 
 * <p>
 * This API is intended for an ordinary user who has no control over the
 * internal topology of the underlay he is using to build his overlays.
 * At this level, all network types (aggregators and switches) look the
 * same. A network has a number of terminals, and the user may request a
 * service that connects a subset of circuits of these terminals
 * together.
 * 
 * <p>
 * Port and link management should be done through
 * {@link uk.ac.lancs.networks.mgmt}.
 * 
 * @resume Hierarchical out-of-band management of connectivity services
 * sliced from a physical network
 * 
 * @author simpsons
 */
package uk.ac.lancs.networks;
