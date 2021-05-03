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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import uk.ac.lancs.dpb.graph.BidiCapacity;
import uk.ac.lancs.dpb.graph.Capacity;
import uk.ac.lancs.dpb.graph.QualifiedEdge;

/**
 * Holds a graph suitable for testing.
 * 
 * @author simpsons
 */
public final class Graph {
    /**
     * The width of the graph in vertex co-ordinates
     */
    public final int width;

    /**
     * The height of the graph in vertex co-ordinates
     */
    public final int height;

    /**
     * An immutable set of edges and their capacities
     */
    public final Collection<QualifiedEdge<Vertex>> edges;

    /**
     * An immutable set of vertices derived from the edges and goals
     */
    public final Collection<Vertex> vertexes;

    /**
     * The maximum capacity derived from the edges
     */
    public final Capacity maxCapacity;

    /**
     * Create a challenge. The inputs will be copied, and a vertex set
     * will be derived.
     * 
     * @param goals a set of goals
     * 
     * @param edges a set of edges and their capacities
     */
    public Graph(int width, int height,
                 Collection<? extends QualifiedEdge<Vertex>> edges) {
        this.width = width;
        this.height = height;
        this.edges = Set.copyOf(edges);

        Collection<Vertex> vertexes =
            Collections.newSetFromMap(new IdentityHashMap<>());
        Capacity max = Capacity.at(0.0);
        for (var edge : this.edges) {
            vertexes.add(edge.start);
            vertexes.add(edge.finish);
            max = Capacity.max(max, edge.capacity.ingress);
            max = Capacity.max(max, edge.capacity.egress);
        }
        this.maxCapacity = max;
        this.vertexes = Collections.unmodifiableCollection(vertexes);
    }

    /**
     * Choose goals randomly from the set of vertices. The number chosen
     * may be less than requested if there are insufficient vertices.
     * 
     * @param amount the maximum number of goals to choose
     * 
     * @param rng a random-number generator
     * 
     * @return the goals in chosen order
     */
    public List<Vertex> chooseGoals(int amount, Random rng) {
        List<Vertex> options = new ArrayList<>(vertexes);
        List<Vertex> result = new ArrayList<>(amount);
        int remaining = amount;
        while (remaining > 0 && !options.isEmpty()) {
            int pos = rng.nextInt(options.size());
            Vertex item = options.remove(pos);
            result.add(item);
            remaining--;
        }
        assert result.size() == Math.min(amount, vertexes.size());
        return List.copyOf(result);
    }

    /**
     * Display the graph and a solution as an SVG.
     * 
     * @param out the destination for the SVG
     * 
     * @param goals the subset of vertices that should be highlighted as
     * goals; or {@code null} if not required
     * 
     * @param tree the selected edges of the solution and the demand
     * imposed on them; or {@code null} if not required
     * 
     * @param vertexRadius the size of each vertex, less than 0.5
     * 
     * @param goalScale the ratio of goal vertices to ordinary ones,
     * minus 1. Set to 0.1 to make goals 10% bigger than other vertices,
     * for example.
     */
    public void drawSVG(PrintWriter out,
                        final Collection<? extends Vertex> goals,
                        final Map<? extends QualifiedEdge<Vertex>,
                                  ? extends BidiCapacity> tree,
                        final double vertexRadius, final double goalScale) {
        final double maxCap = maxCapacity.min();
        final double goalRadius = vertexRadius * (1.0 + goalScale);

        out.println("<?xml version=\"1.0\" " + "standalone=\"no\"?>\n");
        out.println("<!DOCTYPE svg PUBLIC");
        out.println(" \"-//W3C//DTD SVG 20000303 Stylable//EN\"");
        out.println(" \"http://www.w3.org/TR/2000/03/"
            + "WD-SVG-20000303/DTD/svg-20000303-stylable.dtd\">");
        out.println("<svg xmlns=\"http://www.w3.org/2000/svg\"");
        out.println(" xmlns:xlink=\"http://www.w3.org/1999/xlink\"");
        out.printf(" viewBox='%g %g %g %g'>%n", 0.0, 0.0, width + 0.0,
                   height + 0.0);

        /* Create the background. */
        out.printf("<rect fill='white' stroke='none'"
            + " x='%g' y='%g' width='%g' height='%g'/>%n", 0.0, 0.0,
                   width + 0.0, height + 0.0);

        /* Create the grid. */
        out.printf("<g fill='none' stroke='#ccc' stroke-width='0.03'>%n");
        for (int i = 0; i < width; i++)
            out.printf("<line x1='%g' y1='%g' x2='%g' y2='%g'/>%n", i + 0.5,
                       0.0, i + 0.5, height + 0.0);
        for (int i = 0; i < height; i++)
            out.printf("<line x1='%g' y1='%g' x2='%g' y2='%g'/>%n", 0.0,
                       i + 0.5, width + 0.0, i + 0.5);
        out.println("</g>");

        /* Create the border. */
        if (false)
            out.printf("<rect fill='none' stroke='red' stroke-width='0.2'"
                + " x='%g' y='%g' width='%g' height='%g'/>%n", 0.0, 0.0,
                       width + 0.0, height + 0.0);

        /* Highlight the goals. */
        if (goals != null && !goals.isEmpty()) {
            out.printf("<g fill='red' stroke='none'>%n");
            for (Vertex g : goals) {
                out.printf("<circle cx='%g' cy='%g' r='%g' />%n", g.x() + 0.5,
                           g.y() + 0.5, goalRadius);
            }
            out.println("</g>");
        }

        /* Draw out the edge capacities. */
        out.printf("<g fill='#ccc' stroke='none'>%n");
        for (QualifiedEdge<Vertex> e : edges) {
            final double len = Vertex.length(e);
            final double dx = e.finish.x() - e.start.x();
            final double dy = e.finish.y() - e.start.y();
            final double startFrac =
                e.capacity.ingress.min() / maxCap * vertexRadius;
            final double endFrac =
                e.capacity.egress.min() / maxCap * vertexRadius;
            out.printf("<path d='M%g %g L%g %g L%g %g L%g %g z' />%n",
                       e.start.x() - startFrac * dy / len + 0.5,
                       e.start.y() + startFrac * dx / len + 0.5,
                       e.finish.x() - endFrac * dy / len + 0.5,
                       e.finish.y() + endFrac * dx / len + 0.5,
                       e.finish.x() + endFrac * dy / len + 0.5,
                       e.finish.y() - endFrac * dx / len + 0.5,
                       e.start.x() + startFrac * dy / len + 0.5,
                       e.start.y() - startFrac * dx / len + 0.5);
        }
        out.println("</g>");

        if (tree != null && !tree.isEmpty()) {
            /* Draw out the tree. */
            out.printf("<g fill='black' stroke='none'>%n");
            for (var entry : tree.entrySet()) {
                QualifiedEdge<Vertex> e = entry.getKey();
                BidiCapacity bw = entry.getValue();
                final double len = Vertex.length(e);
                final double dx = e.finish.x() - e.start.x();
                final double dy = e.finish.y() - e.start.y();
                final double startFrac =
                    bw.ingress.min() / maxCap * vertexRadius;
                final double endFrac = bw.egress.min() / maxCap * vertexRadius;
                out.printf("<path d='M%g %g L%g %g L%g %g L%g %g z' />%n",
                           e.start.x() - startFrac * dy / len + 0.5,
                           e.start.y() + startFrac * dx / len + 0.5,
                           e.finish.x() - endFrac * dy / len + 0.5,
                           e.finish.y() + endFrac * dx / len + 0.5,
                           e.finish.x() + endFrac * dy / len + 0.5,
                           e.finish.y() - endFrac * dx / len + 0.5,
                           e.start.x() + startFrac * dy / len + 0.5,
                           e.start.y() - startFrac * dx / len + 0.5);
            }
            out.println("</g>");
        }

        /* Draw the vertices. */
        out.printf("<g fill='black' stroke='none'>%n");
        for (Vertex g : vertexes) {
            out.printf("<circle cx='%g' cy='%g' r='%g' />%n", g.x() + 0.5,
                       g.y() + 0.5, vertexRadius);
        }
        out.println("</g>");

        out.println("</svg>");
    }
}
