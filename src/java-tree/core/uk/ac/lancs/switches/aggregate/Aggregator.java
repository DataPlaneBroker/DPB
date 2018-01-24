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
package uk.ac.lancs.switches.aggregate;

import uk.ac.lancs.switches.Port;
import uk.ac.lancs.switches.Switch;

/**
 * Configures a virtual switch that aggregates other switches. An
 * aggregator distinguishes between internal and external ports.
 * External ports are its own, and be obtained from the aggregator's
 * {@link Switch#getPort(String)} method. Internal ports belong to
 * inferior switches, and are used to define trunks. A trunk connects
 * the ports of two different inferior switches together by calling
 * {@link #addTrunk(Port, Port)}. The aggregator uses its knowledge of
 * trunks to plot spanning trees over them, and delegates connections to
 * the switches holding the ports at the ends of the trunks.
 * 
 * @author simpsons
 */
public interface Aggregator extends Switch {
    /**
     * Create a trunk between two internal ports within the switch.
     * 
     * @param p1 one of the ports
     * 
     * @param p2 the other port
     * 
     * @throws NullPointerException if either port is null
     */
    Trunk addTrunk(Port p1, Port p2);

    /**
     * Find an existing trunk connected to a port.
     * 
     * @param p one of the ports of the trunk
     * 
     * @return the requested trunk, or {@code null} if none exist with
     * that end point
     * 
     * @throws IllegalArgumentException if the port does not belong to
     * the switch
     */
    Trunk findTrunk(Port p);
}
