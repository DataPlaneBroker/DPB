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
package uk.ac.lancs.networks.mgmt;

import java.io.PrintWriter;

import uk.ac.lancs.networks.NetworkControl;
import uk.ac.lancs.networks.Terminal;

/**
 * Operations include removing terminals, dumping status and obtaining
 * the control interface.
 * 
 * @resume A basic commandable network
 * 
 * @author simpsons
 */
public interface Network {
    /**
     * Remove a terminal from a network.
     * 
     * @param name the name of the terminal
     * 
     * @throws TerminalBusyException if the named terminal is in use
     * 
     * @throws UnknownTerminalException if no terminal was identified
     * with the given name
     */
    void removeTerminal(String name)
        throws UnknownTerminalException,
            TerminalBusyException;

    /**
     * Get a terminal on this network.
     * 
     * @param name the local name of the terminal
     * 
     * @return the identified terminal
     * 
     * @throws UnknownTerminalException if no terminal was found with
     * the given name
     * 
     * @default This implementation gets the control interface with
     * {@link #getControl()}, and invokes
     * {@link NetworkControl#getTerminal(String)} on it with the
     * supplied argument. If that returns {@code null}, an exception is
     * thrown.
     */
    default Terminal getTerminal(String name)
        throws UnknownTerminalException {
        Terminal result = getControl().getTerminal(name);
        if (result == null)
            throw new UnknownTerminalException(this.getControl().name(),
                                               name);
        return result;
    }

    /**
     * Dump status.
     * 
     * @param out the destination for the textual description of the
     * network's status
     */
    void dumpStatus(PrintWriter out);

    /**
     * Get the controlling interface for this network.
     * 
     * @return the network's control interface
     */
    NetworkControl getControl();
}
