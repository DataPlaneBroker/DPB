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

package uk.ac.lancs.networks;

/**
 * Indicates an error in attempting to use a terminal, such as
 * referencing a non-existent terminal.
 * 
 * @author simpsons
 */
public class TerminalLogicException extends NetworkLogicException {
    private static final long serialVersionUID = 1L;

    private final String terminalName;

    /**
     * Identify the terminal to which this error pertains.
     * 
     * @return the terminal's name
     */
    public String getTerminalName() {
        return terminalName;
    }

    /**
     * Create an exception.
     * 
     * @param networkName the name of the network to which this error
     * pertains
     * 
     * @param terminalName the name of the terminal to which this error
     * pertains
     */
    public TerminalLogicException(String networkName, String terminalName) {
        super(networkName);
        this.terminalName = networkName;
    }

    /**
     * Create an exception with a detail message.
     * 
     * @param message the detail message
     * 
     * @param networkName the name of the network to which this error
     * pertains
     * 
     * @param terminalName the name of the terminal to which this error
     * pertains
     */
    public TerminalLogicException(String networkName, String terminalName,
                                  String message) {
        super(networkName, message);
        this.terminalName = networkName;
    }

    /**
     * Create an exception with a cause.
     * 
     * @param cause the cause
     * 
     * @param networkName the name of the network to which this error
     * pertains
     * 
     * @param terminalName the name of the terminal to which this error
     * pertains
     */
    public TerminalLogicException(String networkName, String terminalName,
                                  Throwable cause) {
        super(networkName, cause);
        this.terminalName = networkName;
    }

    /**
     * Create an exception with a detail message and a cause.
     * 
     * @param message the detail message
     * 
     * @param cause the cause
     * 
     * @param networkName the name of the network to which this error
     * pertains
     * 
     * @param terminalName the name of the terminal to which this error
     * pertains
     */
    public TerminalLogicException(String networkName, String terminalName,
                                  String message, Throwable cause) {
        super(networkName, message, cause);
        this.terminalName = networkName;
    }
}
