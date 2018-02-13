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

import uk.ac.lancs.switches.Terminal;
import uk.ac.lancs.switches.Network;

/**
 * An aggregator consists of a set of inferior networks plus a set of
 * trunks connecting their terminals together. An aggregator
 * distinguishes between internal and external terminals. External
 * terminals are its own, and be obtained from the aggregator's
 * {@link Network#getTerminal(String)} method. Internal terminals belong
 * to inferior networks, and are used to define trunks. A trunk connects
 * the terminals of two different inferior terminals together by calling
 * {@link #addTrunk(Terminal, Terminal)}. The aggregator uses its
 * knowledge of trunks to plot spanning trees over them, and delegates
 * service requests to the inferior networks owning the terminals at the
 * ends of the trunks.
 * 
 * @summary A network that is an aggregate of inferior networks
 * 
 * @author simpsons
 */
public interface Aggregator extends Network {
    /**
     * Create a trunk between two internal terminals within the switch.
     * 
     * @param t1 one of the terminals
     * 
     * @param t2 the other terminal
     * 
     * @throws NullPointerException if either terminal is null
     */
    Trunk addTrunk(Terminal t1, Terminal t2);

    /**
     * Find an existing trunk connected to a terminal.
     * 
     * @param t one of the terminals of the trunk
     * 
     * @return the requested trunk, or {@code null} if none exist with
     * that end point
     * 
     * @throws IllegalArgumentException if the terminal does not belong
     * to the switch
     */
    Trunk findTrunk(Terminal t);
}
