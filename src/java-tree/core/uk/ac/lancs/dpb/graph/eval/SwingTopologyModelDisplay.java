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

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import uk.ac.lancs.dpb.graph.Edge;

/**
 * Stores and presents graph data to a Swing component.
 * 
 * @author simpsons
 */
public class SwingTopologyModelDisplay
    implements TopologyModel, TopologyDisplay<Vertex> {
    private Collection<List<Point2D.Double>> edges;

    private Rectangle2D.Double bounds;

    private JComponent widget;

    private double speed = 0.2;

    /**
     * Set the Swing component so that it can be informed of updates.
     * 
     * @param widget the Swing component
     */
    synchronized void setComponent(JComponent widget) {
        this.widget = widget;
    }

    @Override
    public void setData(double speed,
                        Collection<? extends Edge<? extends Vertex>> edges) {
        /* Convert the edges into Swing/AWT terms. */
        Collection<List<Point2D.Double>> newEdges = new HashSet<>();
        Map<Vertex, Point2D.Double> map = new HashMap<>();
        for (Edge<? extends Vertex> edge : edges) {
            Vertex ov1 = edge.start;
            Vertex ov2 = edge.finish;
            Point2D.Double v1 =
                map.computeIfAbsent(ov1, v -> new Point2D.Double(v.x(), v.y()));
            Point2D.Double v2 =
                map.computeIfAbsent(ov2, v -> new Point2D.Double(v.x(), v.y()));
            newEdges.add(Arrays.asList(v1, v2));
        }

        /* Compute the new bounds in model co-ordinates. */
        double xmin = +Double.MAX_VALUE, ymin = +Double.MAX_VALUE;
        double xmax = +Double.MIN_VALUE, ymax = +Double.MIN_VALUE;
        for (Point2D.Double pt : map.values()) {
            if (pt.x < xmin) xmin = pt.x;
            if (pt.x > xmax) xmax = pt.x;
            if (pt.y < ymin) ymin = pt.y;
            if (pt.y > ymax) ymax = pt.y;
        }
        Rectangle2D.Double newBounds =
            new Rectangle2D.Double(xmin, ymin, xmax - xmin, ymax - ymin);

        /* Thread-safely apply the new data, and repaint the display. */
        final JComponent widget;
        synchronized (this) {
            this.bounds = newBounds;
            this.edges = newEdges;
            this.speed = speed;
            widget = this.widget;
        }
        if (widget != null) SwingUtilities.invokeLater(() -> widget.repaint());
    }

    @Override
    public synchronized Rectangle2D.Double getBounds() {
        return bounds;
    }

    @Override
    public synchronized Collection<? extends List<? extends Point2D.Double>>
        getEdges() {
        return edges;
    }

    @Override
    public double speed() {
        return speed;
    }
}
