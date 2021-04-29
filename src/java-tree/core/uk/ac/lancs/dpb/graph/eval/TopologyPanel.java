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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import javax.swing.JPanel;

/**
 * Displays a live topology.
 *
 * @author simpsons
 */
public class TopologyPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private static final BasicStroke DIAL_STROKE = new BasicStroke(5.0f);

    private static final BasicStroke AXIS_STROKE = new BasicStroke(2.0f);

    private static final BasicStroke EDGE_STROKE = new BasicStroke(2.0f);

    private final TopologyModel model;

    /**
     * Create a panel that displays the current state of a model.
     * 
     * @param model the model
     */
    public TopologyPanel(TopologyModel model) {
        this.model = model;
    }

    /**
     * Paint the model into the panel.
     * 
     * @param g the graphics context
     */
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

        /* How much space have we got to paint in? */
        final int pxmax = getWidth() + 1;
        final int pymax = getHeight() + 1;
        final int pxmin = 0, pymin = 0;
        final int pxmid = pxmin + (pxmax - pxmin) / 2;
        final int pymid = pymin + (pymax - pymin) / 2;

        /* Compute speed dial size. */
        final int sprad = (int) (Math.min(pxmax - pxmin, pymax - pymin) / 10.0);
        final double angle = Math.PI * model.speed();

        /* Get the dimensions of the model. */
        Rectangle2D.Double modelBounds = model.getBounds();
        if (modelBounds == null) return;
        final double xmin = modelBounds.getMinX();
        final double xmax = modelBounds.getMaxX();
        final double ymin = modelBounds.getMinY();
        final double ymax = modelBounds.getMaxY();

        final double xmid = xmin + (xmax - xmin) / 2.0;
        final double ymid = ymin + (ymax - ymin) / 2.0;

        /* How many units per pixel? */
        final double xrat = (xmax - xmin) / (pxmax - pxmin);
        final double yrat = (ymax - ymin) / (pymax - pymin);
        final double rat = Math.max(xrat, yrat);

        /* To convert vertex co-ordinates (x,y) into pixels (px,py), px
         * = (x - xmin) / rat + pxmin, and py = (y - ymin) / rat +
         * pymin. */

        /* Compute the axes for the centre of gravity. */
        final int px0 = (int) ((0 - xmid) / rat + pxmid);
        final int py0 = (int) ((0 - ymid) / rat + pymid);

        /* Draw the background. */
        g2.setColor(Color.WHITE);
        g2.fillRect(pxmin, pymin, pxmax - 1, pymax - 1);

        if (angle >= 0.0 && angle <= Math.PI) {
            /* Draw the speed dial. */
            g2.setColor(Color.BLUE);
            g2.setStroke(DIAL_STROKE);
            g2.drawArc(-sprad, 0, sprad * 2, sprad * 2, -90, +180);
            g2.drawLine(pxmin, sprad, (int) (pxmin + sprad * Math.sin(angle)),
                        (int) (sprad - sprad * Math.cos(angle)));
        }

        g2.setStroke(AXIS_STROKE);
        g2.setColor(Color.RED);
        g2.drawLine(px0, pymin, px0, pymax);
        g2.drawLine(pxmin, py0, pxmax, py0);

        /* Draw the edges. */
        g2.setStroke(EDGE_STROKE);
        g2.setColor(Color.BLACK);
        for (List<? extends Point2D.Double> edge : model.getEdges()) {
            final Point2D.Double first = edge.get(0);
            final Point2D.Double second = edge.get(1);
            final double x1 = first.x;
            final double y1 = first.y;
            final double x2 = second.x;
            final double y2 = second.y;
            final int px1 = (int) ((x1 - xmid) / rat + pxmid);
            final int py1 = (int) ((y1 - ymid) / rat + pymid);
            final int px2 = (int) ((x2 - xmid) / rat + pxmid);
            final int py2 = (int) ((y2 - ymid) / rat + pymid);
            g2.drawLine(px1, py1, px2, py2);
        }
    }
}
