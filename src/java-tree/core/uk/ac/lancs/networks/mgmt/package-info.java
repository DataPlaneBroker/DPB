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
 * A network is a set of terminals across which connectivity services
 * can be established with QoS guarantees. A switch is a network whose
 * terminals are physical interfaces, aggregate interfaces, or labelled
 * divisions (such as VLANs) within those interfaces. An aggregator is a
 * network that delegates to other (<dfn>inferior</dfn>) networks
 * connected by trunks, and is responsible for finding spanning trees
 * across the graph of trunks and inferior switches.
 * 
 * <p>
 * A network in general is managed through a
 * {@link uk.ac.lancs.networks.mgmt.Network} object, permitting basic
 * operations of removal of terminals and acquisition of the control
 * interface {@link uk.ac.lancs.networks.NetworkControl}. An aggregator
 * is managed through a specialization of that,
 * {@link uk.ac.lancs.networks.mgmt.Aggregator}, supporting mapping of
 * the aggregator's terminals to those of its inferior networks, and
 * trunk management. A switch is managed through
 * {@link uk.ac.lancs.networks.mgmt.Switch}, supporting mapping of
 * terminals to interfaces.
 * 
 * <p>
 * The {@link uk.ac.lancs.networks.apps.Commander} application
 * implements a framework for instantiating networks. Network
 * implementations can be provided through
 * {@link uk.ac.lancs.agent.AgentFactory}s, which are supplied with
 * textual configuration and run-time resources to build networks.
 * 
 * @resume Interfaces for managing networks, switches, aggregators and
 * trunks
 * 
 * @author simpsons
 */
package uk.ac.lancs.networks.mgmt;
