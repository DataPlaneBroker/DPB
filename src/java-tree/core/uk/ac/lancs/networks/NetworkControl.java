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
package uk.ac.lancs.networks;

import java.util.Collection;
import java.util.Map;

import uk.ac.lancs.routing.span.Edge;

/**
 * Represents a physical or abstract network over which services can be
 * established.
 * 
 * <p>
 * This interface is designed to be used hierarchically, modelling both
 * a physical switch as well as a graph of connected switches forming a
 * network. As a physical switch, the network's terminals are the
 * switch's ports or interfaces (including VLAN-tagged ones, or port
 * aggregates). With several physical switches' ports physically
 * connected, the remaining unconnected ports can be regarded as the
 * terminals of an aggregate network, over which services can be
 * instantiated. As the abstraction is the same at either level, the
 * physical switches within the aggregate network are just inferior
 * networks, and could instead be aggregates themselves.
 * 
 * @author simpsons
 */
public interface NetworkControl {
    /**
     * Get this network's name.
     * 
     * @return the network's name
     */
    String name();

    /**
     * Get a terminal on this network.
     * 
     * @param id the local terminal name
     * 
     * @return the requested terminal, or {@code null} if no such
     * terminal exists
     */
    Terminal getTerminal(String id);

    /**
     * Get a set of all terminals on this network.
     * 
     * @return a mutable collection of names of terminals created by
     * {@link #getTerminal(String)}
     */
    Collection<String> getTerminals();

    /**
     * Create a service.
     */
    Service newService();

    /**
     * Get an existing service.
     * 
     * @param id the service identifier
     * 
     * @return the service with the requested id, or {@code null} if it
     * does not exist
     */
    Service getService(int id);

    /**
     * Get ids of all open services.
     * 
     * @return a set of all open service ids, modifiable by the user
     */
    Collection<Integer> getServiceIds();

    /**
     * Get a model of port interconnectivity given a bandwidth
     * requirement. Returned weights should always be positive. Atomic
     * networks should use small values rather than zero.
     * 
     * @param minimumBandwidth the threshold below which internal links
     * shall not be included in computing the model
     * 
     * @return a mesh of weighted edges between this network's external
     * ports summarizing the internal connectivity of the network
     */
    Map<Edge<Terminal>, Double> getModel(double minimumBandwidth);
}
