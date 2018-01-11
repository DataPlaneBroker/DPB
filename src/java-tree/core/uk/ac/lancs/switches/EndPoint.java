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
package uk.ac.lancs.switches;

/**
 * Identifies a subdivision of a port, which could be the end point of a
 * connection.
 * 
 * @author simpsons
 */
public final class EndPoint {
    private final Port port;
    private final int label;

    private EndPoint(Port port, int label) {
        if (port == null) throw new NullPointerException("null port");
        this.port = port;
        this.label = label;
    }

    /**
     * Get an end point with a given label on a given port.
     * 
     * @param port the port the end point belongs to
     * 
     * @param label the label that distinguishes the end point from
     * others on the same port
     * 
     * @throws NullPointerException if the port is {@code null}
     */
    public static EndPoint of(Port port, int label) {
        return new EndPoint(port, label);
    }

    /**
     * Get the containing port of the end point.
     * 
     * @return the end point's containing port
     */
    public Port getPort() {
        return port;
    }

    /**
     * Get the label that subdivides the port to identify the
     * connection.
     * 
     * @return the end-point label
     */
    public int getLabel() {
        return label;
    }

    /**
     * Compute the hash code for this object.
     * 
     * @return the end point's hash code
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + label;
        result = prime * result + ((port == null) ? 0 : port.hashCode());
        return result;
    }

    /**
     * Determine whether this end point is equivalent to another object.
     * 
     * @param obj the object to be compared with
     * 
     * @return {@code true} if the other object is an end point and
     * identifies the same as this one
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        EndPoint other = (EndPoint) obj;
        if (label != other.label) return false;
        assert port != null;
        assert other.port != null;
        return port.equals(other.port);
    }

    /**
     * Get a string representation of this end point.
     * 
     * @return a string representation of this end point, consisting of
     * the port and the label
     */
    @Override
    public String toString() {
        return port + ":" + label;
    }
}
