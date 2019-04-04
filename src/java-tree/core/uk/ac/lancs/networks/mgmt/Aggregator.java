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
package uk.ac.lancs.networks.mgmt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import uk.ac.lancs.networks.NetworkControl;
import uk.ac.lancs.networks.Terminal;

/**
 * An aggregator consists of a set of inferior networks plus a set of
 * trunks connecting their terminals together.
 * 
 * <p>
 * An aggregator distinguishes between internal and external terminals.
 * External terminals are its own, and can be obtained from the
 * aggregator's {@link NetworkControl#getTerminal(String)} method
 * through {@link Network#getControl()}. Internal terminals belong to
 * inferior networks, and are used to define trunks, or can be
 * associated with external terminals. When creating (external)
 * terminals on an aggregator, they must be mapped to internal
 * terminals.
 * 
 * <p>
 * A trunk connects the terminals of two different inferior networks
 * together by calling {@link #addTrunk(TerminalId, TerminalId)}. To
 * implement a service, the aggregator uses its knowledge of trunk
 * topology to plot spanning trees over its trunks, and delegates
 * service requests to the inferior networks owning the terminals at the
 * ends of the trunks that contribute to the spanning tree.
 * 
 * @resume A network that is an aggregate of inferior networks
 * 
 * @author simpsons
 */
public interface Aggregator extends Network {
    /**
     * Create a trunk between two internal terminals within the network.
     * 
     * @param t1 the start terminal
     * 
     * @param t2 the end terminal
     * 
     * @return the newly created trunk
     * 
     * @throws NullPointerException if either terminal is null
     * 
     * @throws UnknownSubterminalException if an inferior terminal could
     * not be found
     * 
     * @throws UnknownSubnetworkException if an inferior network could
     * not be found
     * 
     * @throws SubterminalBusyException if an inferior terminal is
     * already in use for another purpose
     * 
     * @constructor
     */
    Trunk addTrunk(TerminalId t1, TerminalId t2)
        throws SubterminalBusyException,
            UnknownSubterminalException,
            UnknownSubnetworkException;

    /**
     * Remove and delete a trunk between two internal terminals with the
     * network.
     * 
     * @param subterm identifier for either of the trunk's terminals
     * 
     * @throws UnknownTrunkException if the terminal did not identify a
     * trunk managed by this aggregator
     * 
     * @throws UnknownSubterminalException if the inferior terminal
     * could not be found
     * 
     * @throws UnknownSubnetworkException if the inferior network could
     * not be found
     */
    void removeTrunk(TerminalId subterm)
        throws UnknownTrunkException,
            UnknownSubterminalException,
            UnknownSubnetworkException;

    /**
     * Find an existing trunk connected to a terminal.
     * 
     * @param subterm identifier for either of the trunk's terminals
     * 
     * @return the requested trunk, with the terminal as its start, or
     * {@code null} if none exist with that terminal
     * 
     * @throws UnknownSubnetworkException if there was an error in
     * identifying the inferior network
     * 
     * @throws UnknownSubterminalException if there was an error in
     * identifying the terminal within the inferior network
     */
    Trunk findTrunk(TerminalId subterm)
        throws UnknownSubnetworkException,
            UnknownSubterminalException;

    /**
     * Get an existing trunk connected to a terminal.
     * 
     * @param subterm identifier for either of the trunk's terminals
     * 
     * @return the requested trunk
     * 
     * @throws UnknownTrunkException if the terminal did not identify a
     * trunk managed by this aggregator
     * 
     * @throws UnknownSubnetworkException if there was an error in
     * identifying the inferior network
     * 
     * @throws UnknownSubterminalException if there was an error in
     * identifying the terminal within the inferior network
     * 
     * @default This implementation invokes
     * {@link #findTrunk(TerminalId)}, and returns the result. However,
     * if the result is {@code null}, a
     * {@link TerminalManagementException} is thrown.
     */
    default Trunk getTrunk(TerminalId subterm)
        throws UnknownTrunkException,
            UnknownSubterminalException,
            UnknownSubnetworkException {
        Trunk result = findTrunk(subterm);
        if (result == null)
            throw new UnknownTrunkException(this.getControl().name(),
                                            subterm);
        return result;
    }

    /**
     * Get each trunk's defining pair of terminals.
     * 
     * @return a set of terminal-id pairs defining each trunk
     */
    Collection<List<TerminalId>> getTrunks();

    /**
     * Add a new external terminal exposing an inferior network's
     * terminal.
     * 
     * @param name the local name of the terminal
     * 
     * @param subterm identifies the inferior network's terminal
     * 
     * @return the newly created terminal
     * 
     * @throws SubterminalBusyException if the inferior terminal is
     * already in use for another purpose
     * 
     * @throws TerminalExistsException if the proposed name is already
     * in use as a terminal identifier
     * 
     * @throws TerminalNameException if the proposed name is invalid
     * 
     * @throws UnknownSubnetworkException if there was an error in
     * identifying the inferior network
     * 
     * @throws UnknownSubterminalException if there was an error in
     * identifying the terminal within the inferior network
     */
    Terminal addTerminal(String name, TerminalId subterm)
        throws TerminalNameException,
            SubterminalBusyException,
            UnknownSubterminalException,
            UnknownSubnetworkException;

    /**
     * Get the mapping from this aggregator's terminals to inferior
     * networks' terminals.
     * 
     * @return the terminal mapping
     */
    Map<Terminal, TerminalId> getTerminals();
}
