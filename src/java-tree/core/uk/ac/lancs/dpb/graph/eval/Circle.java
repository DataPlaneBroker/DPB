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

/**
 * Models a circle with a centre and a radius.
 * 
 * @author simpsons
 */
public abstract class Circle {
    /**
     * Get the centre of the circle.
     * 
     * @return the circle's centre
     */
    public abstract Vertex center();

    /**
     * Get the circle's radius.
     * 
     * @return the circle's radius
     */
    public abstract double radius();

    /**
     * Determine whether a point is strictly within the circle.
     * 
     * @param point the point to test
     * 
     * @return {@code true} if the distance between the point and the
     * centre is less than the radius
     */
    public final boolean contains(Vertex point) {
        return Vertex.distance(center(), point) < radius();
    }

    /**
     * Get the circumcircle of a triangle.
     * 
     * @param a a vertex of the triangle
     * 
     * @param b another vertex of the triangle
     * 
     * @param c yet another vertex of the triangle
     * 
     * @return a circle passing through all three vertices
     */
    public static Circle circumcircle(Vertex a, Vertex b, Vertex c) {
        Vertex bp = Vertex.difference(b, a);
        Vertex cp = Vertex.difference(c, a);
        final double bp2 = bp.x() * bp.x() + bp.y() * bp.y();
        final double cp2 = cp.x() * cp.x() + cp.y() * cp.y();
        final double dp = 2.0 * (bp.x() * cp.y() - bp.y() * cp.x());
        final double upx = (cp.y() * bp2 - bp.y() * cp2) / dp;
        final double upy = (bp.x() * cp2 - cp.x() * bp2) / dp;
        final double r = Math.hypot(upx, upy);
        final double x = upx + a.x();
        final double y = upy + a.y();
        final Vertex center = new Vertex() {
            @Override
            public double x() {
                return x;
            }

            @Override
            public double y() {
                return y;
            }
        };
        return new Circle() {
            @Override
            public Vertex center() {
                return center;
            }

            @Override
            public double radius() {
                return r;
            }
        };
    }

    /**
     * Get a string representation of this circle.
     * 
     * @return the string representation of this circle's centre
     * followed by its radius
     */
    @Override
    public String toString() {
        return String.format("%s%.0f", super.toString(), radius());
    }

    /**
     * Determine whether another object equals this circle.
     * 
     * @param obj the other object
     * 
     * @return {@code true} if the other object is a circle with the
     * same radius and centre as this circle
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!(obj instanceof Circle)) return false;
        Circle other = (Circle) obj;
        return super.equals(obj) && this.radius() == other.radius();
    }

    /**
     * Get the hash code for this object.
     * 
     * @return this object's hash code
     */
    @Override
    public int hashCode() {
        int hash = super.hashCode();
        double radius = radius();
        hash = 67 * hash + (int) (Double.doubleToLongBits(radius) ^
            (Double.doubleToLongBits(radius) >>> 32));
        return hash;
    }
}
