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
package uk.ac.lancs.switches;

import java.util.Collection;
import java.util.Map;

import uk.ac.lancs.routing.span.Edge;

/**
 * Represents a physical network over which services can be established.
 * 
 * @author simpsons
 */
public interface NetworkControl {
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
