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

import uk.ac.lancs.networks.Terminal;

/**
 * Indicates an error in managing an existing terminal. The terminal
 * might not belong to the identified network, but to a subnetwork which
 * it aggregates over.
 * 
 * @author simpsons
 */
public class TerminalManagementException extends NetworkManagementException {
    private static final long serialVersionUID = 1L;

    private final Terminal terminal;

    /**
     * Get the terminal to which this exception pertains.
     * 
     * @return the relevant terminal
     */
    public Terminal getTerminal() {
        return terminal;
    }

    /**
     * Create an exception with a detail message and a cause.
     * 
     * @param network the network originating this exception
     * 
     * @param message the detail message
     * 
     * @param cause the cause
     * 
     * @param terminal the terminal to which this exception pertains
     */
    public TerminalManagementException(Network network, Terminal terminal,
                                       String message, Throwable cause) {
        super(network, message, cause);
        this.terminal = terminal;
    }

    /**
     * Create an exception with a detail message.
     * 
     * @param network the network originating this exception
     * 
     * @param message the detail message
     * 
     * @param terminal the terminal to which this exception pertains
     */
    public TerminalManagementException(Network network, Terminal terminal,
                                       String message) {
        super(network, message);
        this.terminal = terminal;
    }

    /**
     * Create an exception with a cause.
     * 
     * @param network the network originating this exception
     * 
     * @param cause the cause
     * 
     * @param terminal the terminal to which this exception pertains
     */
    public TerminalManagementException(Network network, Terminal terminal,
                                       Throwable cause) {
        super(network, cause);
        this.terminal = terminal;
    }

    /**
     * Create an exception.
     * 
     * @param network the network originating this exception
     * 
     * @param terminal the terminal to which this exception pertains
     */
    public TerminalManagementException(Network network, Terminal terminal) {
        super(network);
        this.terminal = terminal;
    }
}
