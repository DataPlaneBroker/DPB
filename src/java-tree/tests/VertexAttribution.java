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

/**
 * 
 * 
 * @author simpsons
 */
public interface VertexAttribution<V> {
    /**
     * Get the mass.
     * 
     * @param vert the vertex to operate on
     * 
     * @return the vertex's mass
     */
    double mass(V vert);

    /**
     * Reset the force calculation.
     * 
     * @param vert the vertex to operate on
     */
    void resetForce(V vert);

    /**
     * Alter the force by a given vector.
     * 
     * @param vert the vertex to operate on
     * 
     * @param dx the X dimension of the vector
     * 
     * @param dy the Y dimension of the vector
     */
    void addForce(V vert, double dx, double dy);

    /**
     * Divide the force by the mass.
     * 
     * @param vert the vertex to operate on
     */
    void diminishForce(V vert);

    /**
     * Get the difference in Y co-ordinate of two vertices.
     * 
     * @param vert1 one of the vertices
     * 
     * @param vert2 the other vertex
     * 
     * @return the first vertices Y co-ordinate minus the second's
     * 
     * @default {@link #y(Object)} is invoked on each argument, and the
     * difference is returned.
     */
    default double yDiff(V vert1, V vert2) {
        return y(vert1) - y(vert2);
    }

    /**
     * Get the difference in X co-ordinate of two vertices.
     * 
     * @param vert1 one of the vertices
     * 
     * @param vert2 the other vertex
     * 
     * @return the first vertex's X co-ordinate minus the second's
     * 
     * @default {@link #x(Object)} is invoked on each argument, and the
     * difference is returned.
     */
    default double xDiff(V vert1, V vert2) {
        return x(vert1) - x(vert2);

    }

    /**
     * Get the X co-ordinate.
     * 
     * @param vert the vertex to operate on
     * 
     * @return the co-ordinate
     */
    double x(V vert);

    /**
     * Get the Y co-ordinate.
     * 
     * @param vert the vertex to operate on
     * 
     * @return the co-ordinate
     */
    double y(V vert);

    /**
     * Get the X dimension of the velocity.
     * 
     * @param vert the vertex to operate on
     * 
     * @return the velocity
     */
    double vx(V vert);

    /**
     * Get the Y dimension of the velocity.
     * 
     * @param vert the vertex to operate on
     * 
     * @return the velocity
     */
    double vy(V vert);

    /**
     * Get the speed.
     * 
     * @param vert the vertex to operate on
     * 
     * @return the speed
     * 
     * @default the square root of the sum of the squares of
     * {@link #vx(Object)} and {@link #vy(Object)} is returned
     */
    default double speed(V vert) {
        final double vx = vx(vert);
        final double vy = vy(vert);
        return Math.sqrt(vx * vx + vy * vy);
    }

    /**
     * Get the force/acceleration.
     * 
     * @param vert the vertex to operate on
     * 
     * @return the force/acceleration
     */
    double force(V vert);

    /**
     * Change the velocity by a given fraction of the force, and then
     * change the position by the same fraction of the velocity.
     * 
     * @param vert the vertex to operate on
     * 
     * @param delta the fraction
     */
    void step(V vert, double delta);

    /**
     * Translate the position.
     * 
     * @param vert the vertex to operate on
     * 
     * @param dx the change in the X dimension
     * 
     * @param dy the change in the Y dimension
     */
    void move(V vert, double dx, double dy);
}
