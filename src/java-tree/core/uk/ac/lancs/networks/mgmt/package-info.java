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
 * A network is a set of terminals across which connectivity services
 * can be established with QoS guarantees. A switch is a network whose
 * terminals are physical interfaces, aggregate interfaces or VLAN tags
 * within those interfaces. An aggregator is a network that delegates to
 * other (inferior) networks connected by trunks, and is responsible for
 * finding spanning trees across the graph of trunks and inferior
 * switches.
 * 
 * <p>
 * A network in general is accessed through a
 * {@link uk.ac.lancs.networks.mgmt.Network} object, and an aggregator
 * through a specialization of that,
 * {@link uk.ac.lancs.networks.mgmt.Aggregator}. A switch is also
 * accessed through {@link uk.ac.lancs.networks.mgmt.Network}, needing
 * no special operations for use. A
 * {@link uk.ac.lancs.networks.mgmt.ManagedSwitch} interface allows
 * terminals to be added to and remove from a switch, mapping the
 * terminals to physical interfaces of an underlying fabric. A
 * {@link uk.ac.lancs.networks.mgmt.ManagedAggregator} allows terminals
 * to be added to and removed from an aggregator, mapping them to
 * inferior switches' terminals that do not form trunks.
 * 
 * @summary Interfaces for managing networks, switches, aggregators and
 * trunks
 * 
 * @author simpsons
 */
package uk.ac.lancs.networks.mgmt;
