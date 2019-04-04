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

/**
 * Identifies an inferior network's terminal by its local name and the
 * name of the inferior network.
 * 
 * @author simpsons
 */
public final class TerminalId {
    /**
     * The name of the network owning the terminal
     */
    public final String network;

    /**
     * The local name of the terminal within its network
     */
    public final String terminal;

    private TerminalId(String network, String terminal) {
        this.network = network;
        this.terminal = terminal;
    }

    /**
     * Identify a terminal by name.
     * 
     * @param network the name of the network owning the terminal
     * 
     * @param terminal the local name of the terminal within its network
     * 
     * @return an identifier for the terminal, or {@code null} if either
     * name component is {@code null}
     */
    public static TerminalId of(String network, String terminal) {
        if (network == null || terminal == null) return null;
        return new TerminalId(network, terminal);
    }

    /**
     * Get the hash code for this identifier.
     * 
     * @return a hash code incorporating the network and terminal names
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result =
            prime * result + ((network == null) ? 0 : network.hashCode());
        result =
            prime * result + ((terminal == null) ? 0 : terminal.hashCode());
        return result;
    }

    /**
     * Determine whether another object identifies the same terminal as
     * this object.
     * 
     * @param obj the object to be compared with
     * 
     * @return {@code true} iff the other object identifies the same
     * terminal
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        TerminalId other = (TerminalId) obj;
        if (network == null) {
            if (other.network != null) return false;
        } else if (!network.equals(other.network)) return false;
        if (terminal == null) {
            if (other.terminal != null) return false;
        } else if (!terminal.equals(other.terminal)) return false;
        return true;
    }

    /**
     * Get a string representation of this identifier.
     * 
     * @return a string representation of this identifier, incorporating
     * the network and terminal names
     */
    @Override
    public String toString() {
        return "[" + network + ", " + terminal + "]";
    }
}
