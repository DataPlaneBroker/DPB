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
package uk.ac.lancs.routing.hier;

import java.util.Map;

import uk.ac.lancs.routing.span.Edge;

/**
 * Represents a physical or virtual switch.
 * 
 * @author simpsons
 */
public interface SwitchControl {
    /**
     * Find the end point of this switch with the given identifier.
     * 
     * @param id the end-point identifier
     * 
     * @return the identified end point, or {@code null} if not found
     */
    EndPoint findEndPoint(String id);

    /**
     * Create a connection.
     * 
     * @param request a description of the required connection
     */
    Connection newConnection();

    /**
     * Get an existing connection.
     * 
     * @param id the connection identifier
     * 
     * @return the connection with the requested id, or {@code null} if
     * it does not exist
     */
    Connection getConnection(int id);

    /**
     * Get a model of port connections given a bandwidth requirement.
     * 
     * @param minimumBandwidth the threshold below which internal links
     * shall not be included in computing the model
     * 
     * @return a mesh of weighted edges between this switch's external
     * ports summarizing the internal connectivity of the switch
     */
    Map<Edge<Port>, Double> getModel(double minimumBandwidth);
}
