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

import java.util.AbstractList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Describes an undirected edge between two vertices and is suitable as
 * a hash key. This class defines a canonical order for vertices, based
 * simply on their hash codes. This class is suitable as a hash key
 * provided {@code V} is suitable.
 * 
 * @param <V> the vertex type
 * 
 * @author simpsons
 */
public final class Edge<V> extends AbstractList<V> {
    /**
     * An unmodifiable pair of vertices in a canonical order that form
     * the edge
     */
    @SuppressWarnings("unchecked")
    private final V[] vertices = (V[]) new Object[2];

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
     * 
     * @return an edge connecting the two vertices
     */
    public static <V> Edge<V> of(V first, V second) {
        return new Edge<>(first, second, null);
    }

    /**
     * Create an undirected edge between two vertices, reversing an
     * associated pair to match the edge's canonical order. The supplied
     * arguments are canonicalized, and so might not match the eventual
     * field values.
     * 
     * @param first a vertex
     * 
     * @param second another vertex
     * 
     * @param list a pair of items to be reversed if the supplied
     * vertices have to be reversed to form canonical order
     * 
     * @param <V> the vertex type
     * 
     * @return an edge connecting the two vertices
     */
    public static <V> Edge<V> of(V first, V second, List<?> list) {
        return new Edge<>(first, second, list);
    }

    /**
     * Create an undirected edge between two vertices in a list. The
     * supplied arguments are canonicalized, and so might not match the
     * eventual field values.
     * 
     * @param vertices a list of exactly two vertices
     * 
     * @param <V> the vertex type
     * 
     * @return an edge connecting the two vertices
     */
    public static <V> Edge<V> of(List<? extends V> vertices) {
        return new Edge<>(vertices, null);
    }

    /**
     * Create an undirected edge between two vertices in a list,
     * reversing an associated pair to match the edge's canonical order.
     * The supplied arguments are canonicalized, and so might not match
     * the eventual field values.
     * 
     * @param vertices a list of exactly two vertices
     * 
     * @param list a pair of items to be reversed if the supplied
     * vertices have to be reversed to form canonical order
     * 
     * @param <V> the vertex type
     * 
     * @return an edge connecting the two vertices
     */
    public static <V> Edge<V> of(List<? extends V> vertices, List<?> list) {
        return new Edge<>(vertices, list);
    }

    /**
     * Create an undirected edge between two vertices in a list. The
     * supplied arguments are canonicalized, and so might not match the
     * eventual field values.
     * 
     * @param vertices a list of exactly two vertices
     * 
     * @param list an optional list of two elements to be reversed if
     * the vertices have to be reversed
     */
    private Edge(List<? extends V> vertices, List<?> list) {
        this(vertices.get(0), vertices.get(1), list);
        if (vertices.size() > 2)
            throw new IllegalArgumentException("too many vertices: "
                + vertices.size());
    }

    /**
     * Create an undirected edge between two vertices. The supplied
     * arguments are canonicalized, and so might not match the eventual
     * field values.
     * 
     * @param first a vertex
     * 
     * @param second another vertex
     * 
     * @param list an optional list of two elements to be reversed if
     * the vertices have to be reversed
     */
    private Edge(V first, V second, List<?> list) {
        if (first == null) throw new NullPointerException("first");
        if (second == null) throw new NullPointerException("second");
        int firstHash = first.hashCode();
        int secondHash = second.hashCode();
        if (firstHash < secondHash) {
            vertices[0] = first;
            vertices[1] = second;
            this.firstHash = firstHash;
            this.secondHash = secondHash;
        } else {
            vertices[1] = first;
            vertices[0] = second;
            this.firstHash = secondHash;
            this.secondHash = firstHash;
            if (list != null) Collections.reverse(list.subList(0, 2));
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
        return first().equals(p.first()) && second().equals(p.second());
    }

    /**
     * Get the first vertex.
     * 
     * @return the first vertex
     */
    public V first() {
        return vertices[0];
    }

    /**
     * Get the second vertex.
     * 
     * @return the second vertex
     */
    public V second() {
        return vertices[1];
    }

    /**
     * Get a string representation of this edge.
     * 
     * @return string representations of the two vertices, separated by
     * a comma, and surrounded by angle brackets
     */
    @Override
    public String toString() {
        return "<" + first() + "," + second() + ">";
    }

    /**
     * Get the number of elements in the edge.
     * 
     * @return 2
     */
    @Override
    public int size() {
        return 2;
    }

    /**
     * Get a vertex.
     * 
     * @param index 0 or 1
     * 
     * @return one of the vertices forming this edge, in canonical order
     */
    @Override
    public V get(int index) {
        if (index < 0 || index >= 2)
            throw new NoSuchElementException("index: " + index);
        return vertices[index];
    }
}
