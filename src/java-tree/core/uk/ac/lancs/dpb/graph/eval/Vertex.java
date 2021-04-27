/*
 * Copyright (c) 2021, Regents of the University of Lancaster
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the
 *   distribution.
 *
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived
 *   from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *
 *  Author: Steven Simpson <s.simpson@lancaster.ac.uk>
 */

package uk.ac.lancs.dpb.graph.eval;

import uk.ac.lancs.dpb.graph.Edge;

/**
 * Models a vertex in two-dimensional space.
 * 
 * @author simpsons
 */
public abstract class Vertex {
    /**
     * Get the vertex's X co-ordinate.
     * 
     * @return the co-ordinate
     */
    public abstract double x();

    /**
     * Get the vertex's Y co-ordinate.
     * 
     * @return the co-ordinate
     */
    public abstract double y();

    /**
     * Get the distance between two vertices.
     * 
     * @param v0 one vertex
     * 
     * @param v1 the other vertex
     * 
     * @return the distance between the two vertices
     */
    static double distance(Vertex v0, Vertex v1) {
        final double dx = v1.x() - v0.x();
        final double dy = v1.y() - v0.y();
        return Math.hypot(dx, dy);
    }

    /**
     * Subtract one vertex from another. A new, immutable vertex is
     * returned.
     * 
     * @param v0 the superior vertex
     * 
     * @param v1 the inferior vertex
     * 
     * @return the superior vertex minus the inferior vertex
     */
    static Vertex difference(Vertex v0, Vertex v1) {
        final double x = v0.x() - v1.x();
        final double y = v0.y() - v1.y();
        return new Vertex() {
            @Override
            public double x() {
                return x;
            }

            @Override
            public double y() {
                return y;
            }
        };
    }

    /**
     * Determine whether two straight line segments cross. Note that if
     * the two lines share a vertex, this method returns {@code false}.
     * 
     * @param v0 one end of the first line
     * 
     * @param v1 the other end of the first line
     * 
     * @param w0 one end of the second line
     * 
     * @param w1 the other end of the second line
     * 
     * @return {@code true} if the lines cross within their lengths, and
     * are defined by four distinct vertices
     */
    static boolean edgesCross(Vertex v0, Vertex v1, Vertex w0, Vertex w1) {
        if (v0 == w0 || v0 == w1 || v1 == w0 || v1 == w1) return false;
        final double deter = (v0.x() - v1.x()) * (w0.y() - w1.y())
            - (v0.y() - v1.y()) * (w0.x() - w1.x());
        final double x1y2my1x2 = v0.x() * v1.y() - v0.y() * v1.x();
        final double x3y4my3x4 = w0.x() * w1.y() - w0.y() * w1.x();
        final double x3mx4 = w0.x() - w1.x();
        final double x1mx2 = v0.x() - v1.x();
        final double y3my4 = w0.y() - w1.y();
        final double y1my2 = v0.y() - v1.y();
        final double px = (x1y2my1x2 * x3mx4 - x1mx2 * x3y4my3x4) / deter;
        final double py = (x1y2my1x2 * y3my4 - y1my2 * x3y4my3x4) / deter;
        if (px < v0.x() && px < v1.x()) return false;
        if (px < w0.x() && px < w1.x()) return false;
        if (px > v0.x() && px > v1.x()) return false;
        if (px > w0.x() && px > w1.x()) return false;
        if (py < v0.y() && py < v1.y()) return false;
        if (py < w0.y() && py < w1.y()) return false;
        if (py > v0.y() && py > v1.y()) return false;
        if (py > w0.y() && py > w1.y()) return false;
        return true;
    }

    /**
     * Get a string representation of a vertex.
     * 
     * @return the co-ordinates, comma-separated, and in brackets
     */
    @Override
    public String toString() {
        return String.format("(%.0f,%.0f)", x(), y());
    }

    /**
     * Determine whether this vertex and another object are equal.
     * 
     * @param o the other object
     * 
     * @return {@code true} if the other object is a {@link Vertex} with
     * the same values for {@link #x()} and {@link #y()} as this vertex,
     * or if both are {@code null}; {@code false} otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (!(o instanceof Vertex)) return false;
        Vertex other = (Vertex) o;
        return x() == other.x() && y() == other.y();
    }

    /**
     * Define a vertex at specific co-ordinates.
     * 
     * @param x the X co-ordinate
     * 
     * @param y the Y co-ordinate
     * 
     * @return the new vertex
     */
    public static Vertex at(double x, double y) {
        return new Vertex() {
            public double x() {
                return x;
            }

            public double y() {
                return y;
            }
        };
    }

    /**
     * Get the length of an edge.
     * 
     * @param edge the edge
     * 
     * @return the edge's length
     */
    public static double length(Edge<Vertex> edge) {
        return distance(edge.start, edge.finish);
    }

    /**
     * Determine whether two edges abut. They abut if they share a
     * vertex.
     * 
     * @param e0 one edge
     * 
     * @param e1 the other edge
     * 
     * @return {@code true} if the edges abut
     */
    public static boolean abut(Edge<Vertex> e0, Edge<Vertex> e1) {
        if (e0 == e1) return false;
        if (e0.start == e1.start) return true;
        if (e0.start == e1.finish) return true;
        if (e0.finish == e1.start) return true;
        if (e0.finish == e1.finish) return true;
        return false;
    }

    /**
     * Determine whether two edges cross.
     * 
     * @param e1 one edge
     * 
     * @param e2 the other edge
     * 
     * @return {@code true} if the edges cross, as defined by
     * {@link #edgesCross(Vertex, Vertex, Vertex, Vertex)}
     */
    public static boolean cross(Edge<Vertex> e1, Edge<Vertex> e2) {
        return edgesCross(e1.start, e1.finish, e2.start, e2.finish);
    }

    /**
     * Get the hash code for this object.
     * 
     * @return this object's hash code
     */
    @Override
    public int hashCode() {
        int hash = 7;
        double x = x();
        hash = 59 * hash + (int) (Double.doubleToLongBits(x) ^
            (Double.doubleToLongBits(x) >>> 32));
        double y = y();
        hash = 59 * hash + (int) (Double.doubleToLongBits(y) ^
            (Double.doubleToLongBits(y) >>> 32));
        return hash;
    }
}
