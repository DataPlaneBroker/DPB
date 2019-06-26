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

class Vertex {
    public final int id;

    @Override
    public String toString() {
        return Integer.toString(id);
    }

    Vertex() {
        id = nextId++;
        x = 0.3 * id * Math.cos(id);
        y = 0.3 * id * Math.sin(id);
    }

    public double x, y;

    /**
     * Velocity
     */
    public double vx = 0.0, vy = 0.0;

    public double fx = 0.0, fy = 0.0;

    double mass = 1.0;

    private static int nextId;

    static final VertexAttribution<Vertex> ATTRIBUTION =
        new VertexAttribution<Vertex>() {
            @Override
            public double y(Vertex vert) {
                return vert.y;
            }

            @Override
            public double x(Vertex vert) {
                return vert.x;
            }

            @Override
            public double vy(Vertex vert) {
                return vert.vy;
            }

            @Override
            public double vx(Vertex vert) {
                return vert.vx;
            }

            @Override
            public void step(Vertex vert, double delta) {
                vert.vx += vert.fx * delta;
                vert.vy += vert.fy * delta;
                vert.x += vert.vx * delta;
                vert.y += vert.vy * delta;
            }

            @Override
            public void resetForce(Vertex vert) {
                vert.fx = 0.0;
                vert.fy = 0.0;
            }

            @Override
            public void move(Vertex vert, double dx, double dy) {
                vert.x += dx;
                vert.y += dy;
            }

            @Override
            public double mass(Vertex vert) {
                return vert.mass;
            }

            @Override
            public double force(Vertex vert) {
                final double fx = vert.fx;
                final double fy = vert.fy;
                return Math.sqrt(fx * fx + fy * fy);
            }

            @Override
            public void diminishForce(Vertex vert) {
                vert.fx /= vert.mass;
                vert.fy /= vert.mass;
            }

            @Override
            public void addForce(Vertex vert, double dx, double dy) {
                vert.fx += dx;
                vert.fy += dy;
            }
        };
}
