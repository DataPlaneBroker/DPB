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
 * Identifies a numbered virtual circuit within a terminal of other
 * circuits. Every circuit belongs to a terminal, and is distinguished
 * from other circuits of the same terminal by an integer label. Get a
 * circuit by calling {@link Terminal#circuit(int)}.
 * 
 * @resume A numbered division of a terminal
 * 
 * @author simpsons
 */
public final class Circuit {
    private final Terminal circuit;
    private final int label;

    Circuit(Terminal terminal, int label) {
        if (terminal == null) throw new NullPointerException("null terminal");
        this.circuit = terminal;
        this.label = label;
    }

    /**
     * Get the containing terminal of the circuit.
     * 
     * @return the circuit's containing terminal
     */
    public Terminal getTerminal() {
        return circuit;
    }

    /**
     * Get the label that subdivides the terminal to identify the
     * connection.
     * 
     * @return the circuit label
     */
    public int getLabel() {
        return label;
    }

    /**
     * Compute the hash code for this object.
     * 
     * @return the circuit's hash code
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + label;
        result =
            prime * result + ((circuit == null) ? 0 : circuit.hashCode());
        return result;
    }

    /**
     * Determine whether this circuit is equivalent to another object.
     * 
     * @param obj the object to be compared with
     * 
     * @return {@code true} if the other object is a circuit and
     * identifies the same as this one
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        Circuit other = (Circuit) obj;
        if (label != other.label) return false;
        assert circuit != null;
        assert other.circuit != null;
        return circuit.equals(other.circuit);
    }

    /**
     * Get a string representation of this circuit.
     * 
     * @return a string representation of this circuit, consisting of
     * the terminal and the label
     */
    @Override
    public String toString() {
        return circuit + ":" + label;
    }
}
