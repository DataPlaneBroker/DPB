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

package uk.ac.lancs.networks.services;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import uk.ac.lancs.networks.ChordMetrics;
import uk.ac.lancs.networks.CircuitLogicException;
import uk.ac.lancs.networks.NetworkLogicException;
import uk.ac.lancs.networks.Terminal;
import uk.ac.lancs.networks.TerminalLogicException;
import uk.ac.lancs.routing.span.Edge;

/**
 * 
 * 
 * @author simpsons
 */
public interface Service {
    /**
     * Get the identifier for this service.
     * 
     * @return the service identifier
     */
    UUID id();

    /**
     * Get the provider of this service.
     * 
     * @return the service's provider
     */
    Provider provider();

    /**
     * Explain this service for diagnosic purposes.
     * 
     * @param reason the new explanation
     */
    void explain(String reason);

    /**
     * Redefine this service to consist of the specified segments. A new
     * service by default has no segments. A service is effectively
     * deleted by defining it with no segments and deactivating it.
     * 
     * <p>
     * Defining a service clears all faults, as returned by
     * {@link #faults()}.
     * 
     * @param segments the set of segments to be established
     * 
     * @throws CircuitLogicException if a circuit is unavailable
     * 
     * @throws TerminalLogicException if a referenced terminal does not
     * belong to this service's network
     * 
     * @throws NetworkLogicException if the segment description is
     * invalid in some other way
     */
    void define(Collection<? extends Segment> segments)
        throws NetworkLogicException;

    /**
     * Get the current set of segments to be established.
     * 
     * @return the current segments
     */
    Collection<? extends Segment> definition();

    /**
     * Get the current set of asynchronous faults. Calling
     * {@link #define(Collection)} clears all faults.
     * 
     * @return the faults
     */
    Collection<? extends Fault> faults();

    /**
     * Set the activation intent. When {@code false}, resources are
     * allocated, but not actively forwarding packets.
     * 
     * @param status the new activation status
     */
    void activate(boolean status);

    /**
     * Get the activation status.
     * 
     * @return the current activation status
     */
    Activation activation();

    /**
     * Get the completion status.
     * 
     * @return the current completion status.
     */
    Completion completion();

    /**
     * Add a listener to receive updates to the service's status.
     * 
     * @param listener the listener to be added
     */
    void addListener(ServiceListener listener);

    /**
     * Stop a listener from receiving updates to the service's status.
     * 
     * @param listener the listener to be removed
     */
    void removeListener(ServiceListener listener);

    /**
     * Get a model of the network that this service is a part of,
     * assuming that this service's resources may be re-used.
     * 
     * @param minimumBandwidth the threshold below which internal links
     * shall not be included in computing the model
     * 
     * @return a mesh of weighted edges between this network's external
     * ports summarizing the internal connectivity of the network
     */
    Map<Edge<Terminal>, ChordMetrics> getModel(double minimumBandwidth);
}
