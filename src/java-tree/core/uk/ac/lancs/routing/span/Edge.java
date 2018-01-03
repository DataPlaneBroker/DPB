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
package uk.ac.lancs.routing.span;

/**
 * Describes an undirected edge between two vertices.
 * 
 * <p>
 * Objects of this class are suitable as hash keys.
 * 
 * @param <V> the vertex type
 * 
 * @author simpsons
 */
public final class Edge<V> {
    /**
     * The first vertex
     */
    public final V first;

    /**
     * The second vertex
     */
    public final V second;

    /**
     * The stored hash code of a vertex
     */
    private final int firstHash, secondHash;

    /**
     * Create an undirected edge between two vertices. The supplied
     * arguments are canonicalized, and so might not match the eventual
     * field values.
     * 
     * @param first a vertex
     * 
     * @param second another vertex
     * 
     * @param <V> the vertex type
     */
    public static <V> Edge<V> of(V first, V second) {
        return new Edge<>(first, second);
    }

    private Edge(V first, V second) {
        if (first == null) throw new NullPointerException("first");
        if (second == null) throw new NullPointerException("second");
        int firstHash = first.hashCode();
        int secondHash = second.hashCode();
        if (firstHash < secondHash) {
            this.first = first;
            this.second = second;
            this.firstHash = firstHash;
            this.secondHash = secondHash;
        } else {
            this.first = second;
            this.second = first;
            this.firstHash = secondHash;
            this.secondHash = firstHash;
        }
    }

    /**
     * Get the hash code of this edge.
     * 
     * @return the hash code of this edge, a combination of the hash
     * codes of the vertices
     */
    @Override
    public int hashCode() {
        return firstHash * 31 + secondHash;
    }

    /**
     * Determine whether another object describes this edge.
     * 
     * @param other the other object
     * 
     * @return {@code true} iff the other object describes the same edge
     */
    @Override
    public boolean equals(Object other) {
        if (other == null) return false;
        if (!(other instanceof Edge)) return false;
        Edge<?> p = (Edge<?>) other;
        return first.equals(p.first) && second.equals(p.second);
    }

    /**
     * Get a string representation of this edge.
     * 
     * @return string representations of the two vertices, separated by
     * a comma, and surrounded by angle brackets
     */
    @Override
    public String toString() {
        return "<" + first + "," + second + ">";
    }
}
