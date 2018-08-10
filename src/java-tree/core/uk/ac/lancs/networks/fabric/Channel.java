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
package uk.ac.lancs.networks.fabric;

/**
 * Identifies a channel within a fabric interface.
 * 
 * @author simpsons
 */
public class Channel {
    private final Interface iface;
    private final int label;

    Channel(Interface iface, int label) {
        if (iface == null) throw new NullPointerException("null interface");
        this.iface = iface;
        this.label = label;
    }

    /**
     * Get the interface that this channel belongs to.
     * 
     * @return the channel's interface
     */
    public Interface getInterface() {
        return iface;
    }

    /**
     * Get the label distinguishing it from other channels in the same
     * label.
     * 
     * @return the channel's label
     */
    public int getLabel() {
        return label;
    }

    /**
     * Get the hash code of this channel identifier.
     * 
     * @return the channel's hash code
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((iface == null) ? 0 : iface.hashCode());
        result = prime * result + label;
        return result;
    }

    /**
     * Determine whether another object identifies the same channel.
     * 
     * @param obj the other object to test
     * 
     * @return {@code true} if the other object is a channel identifier
     * for the same channel as this one
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        Channel other = (Channel) obj;
        if (iface == null) {
            if (other.iface != null) return false;
        } else if (!iface.equals(other.iface)) return false;
        if (label != other.label) return false;
        return true;
    }

    /**
     * Get a string representation of this channel identifier.
     * 
     * @return the string representation of the channel's interface,
     * with a colon and theis channel's label appended
     */
    @Override
    public String toString() {
        return iface.toString() + ':' + label;
    }
}
