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
package uk.ac.lancs.networks.openflow;

import java.util.AbstractList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @resume An immutable identifier for a virtual circuit supporting
 * double-, single- and un-tagged VLAN traffic.
 * 
 * @author simpsons
 */
public final class VLANCircuitId {
    private final int port;
    private final int vlan;
    private final int innerVlan;

    /**
     * Create a circuit id from a list of numbers.
     * 
     * @param in the source list
     * 
     * @throws IllegalArgumentException if the list does not have 1, 2
     * or 3 elements, or any of them are out-of-bounds
     */
    public VLANCircuitId(List<? extends Number> in) {
        int port, vlan = -1, innerVlan = -1;
        switch (in.size()) {
        case 0:
        default:
            throw new IllegalArgumentException("wrong list size "
                + "for circuit id: " + in.size());

        case 3:
            innerVlan = in.get(2).intValue();
            if (innerVlan < 0 || innerVlan >= 4096)
                throw new IllegalArgumentException("bad inner VLAN: "
                    + innerVlan);
        case 2:
            vlan = in.get(1).intValue();
            if (vlan < 0 || vlan >= 4096)
                throw new IllegalArgumentException("bad VLAN: " + vlan);
        case 1:
            port = in.get(0).intValue();
            if (port < 0 || port >= 8192) // TODO: Use OF max port?
                throw new IllegalArgumentException("bad port: " + port);
            break;
        }
        this.port = port;
        this.vlan = vlan;
        this.innerVlan = innerVlan;
    }

    /**
     * Create a circuit id from an array of integers.
     * 
     * @param in the source array
     * 
     * @throws IllegalArgumentException if the list does not have 1, 2
     * or 3 elements, or any of them are out-of-bounds
     */
    public VLANCircuitId(int... in) {
        this(IntStream.of(in).boxed().collect(Collectors.toList()));
    }

    private static final String PORT_GROUP_NAME = "port";
    private static final String VLAN_GROUP_NAME = "vlan";
    private static final String INNER_VLAN_GROUP_NAME = "innerVlan";
    private static final String TUPLE_PATTERN_TEXT =
        "(?<" + PORT_GROUP_NAME + ">[0-9]+)(?:\\.(?<" + VLAN_GROUP_NAME
            + ">[0-9]+)(?:\\\\.(?<" + INNER_VLAN_GROUP_NAME + ">[0-9]+))?)?";
    /**
     * Matches strings identifying circuits. The groups
     * <samp>{@value #PORT_GROUP_NAME}</samp>,
     * <samp>{@value #VLAN_GROUP_NAME}</samp> and
     * <samp>{@value #INNER_VLAN_GROUP_NAME}</samp> are present if
     * specified. The pattern is compatible with the result of
     * {@link #toString()}:
     * 
     * <pre>
     * {@value #TUPLE_PATTERN_TEXT}
     * </pre>
     * 
     * @resume A regular expression matching circuit ids
     */
    public static final Pattern TUPLE_PATTERN =
        Pattern.compile(TUPLE_PATTERN_TEXT);

    /**
     * Create a circuit id from a string representation. The format is
     * defined by {@link #TUPLE_PATTERN}.
     * 
     * @param in the string representation
     */
    public VLANCircuitId(CharSequence in) {
        Matcher m = TUPLE_PATTERN.matcher(in);
        if (!m.matches())
            throw new IllegalArgumentException("bad tuple: " + in);
        this.port = Integer.parseInt(m.group(PORT_GROUP_NAME));
        String vlanText = m.group(VLAN_GROUP_NAME);
        if (vlanText == null) {
            this.vlan = this.innerVlan = -1;
        } else {
            this.vlan = Integer.parseInt(vlanText);
            String innerVlanText = m.group(INNER_VLAN_GROUP_NAME);
            if (innerVlanText == null)
                this.innerVlan = -1;
            else
                this.innerVlan = Integer.parseInt(innerVlanText);
        }
        if (port < 0 || port >= 8192) // TODO: Use OF max port?
            throw new IllegalArgumentException("bad port: " + port);
        if (vlan < 0 || vlan >= 4096)
            throw new IllegalArgumentException("bad VLAN: " + vlan);
        if (innerVlan < 0 || innerVlan >= 4096)
            throw new IllegalArgumentException("bad inner VLAN: "
                + innerVlan);
    }

    /**
     * Get the hash code for this circuit id.
     * 
     * @return the circuit id's hash code
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + innerVlan;
        result = prime * result + port;
        result = prime * result + vlan;
        return result;
    }

    /**
     * Determine whether another object identifies the same circuit.
     * 
     * @param obj the other object
     * 
     * @return {@code true} if the other object is a VLAN circuit
     * identifier, and identifies the same circuit
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        VLANCircuitId other = (VLANCircuitId) obj;
        if (innerVlan != other.innerVlan) return false;
        if (port != other.port) return false;
        if (vlan != other.vlan) return false;
        return true;
    }

    /**
     * Get a string representation of this circuit id. The result is
     * compatible with {@link #TUPLE_PATTERN}.
     * 
     * @return a string of the form <samp>5</samp>, <samp>5.100</samp>
     * or <samp>5.100.20</samp>
     */
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append(port);
        if (vlan >= 0) {
            result.append('.').append(vlan);
            if (innerVlan >= 0) result.append('.').append(innerVlan);
        }
        return result.toString();
    }

    /**
     * Get a list view of this tuple.
     * 
     * @return an immutable list of the tuple's components
     */
    public List<Integer> asList() {
        if (vlan < 0) return Collections.singletonList(port);
        if (innerVlan < 0) return new AbstractList<Integer>() {
            @Override
            public Integer get(int index) {
                switch (index) {
                case 0:
                    return port;
                case 1:
                    return vlan;
                default:
                    throw new IndexOutOfBoundsException("in " + this + ": "
                        + index);
                }
            }

            @Override
            public int size() {
                return 2;
            }
        };
        return new AbstractList<Integer>() {
            @Override
            public Integer get(int index) {
                switch (index) {
                case 0:
                    return port;
                case 1:
                    return vlan;
                case 2:
                    return innerVlan;
                default:
                    throw new IndexOutOfBoundsException("in " + this + ": "
                        + index);
                }
            }

            @Override
            public int size() {
                return 3;
            }
        };
    }
}
