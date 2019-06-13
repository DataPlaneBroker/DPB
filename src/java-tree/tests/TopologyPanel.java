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

import java.awt.Color;
import java.awt.Graphics;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

import javax.swing.JPanel;

/**
 * 
 * 
 * @author simpsons
 */
final class TopologyPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final TopologyModel model;

    public TopologyPanel(TopologyModel model) {
        this.model = model;
    }

    @Override
    protected void paintComponent(Graphics g) {
        /* Get the dimensions of the model. */
        Rectangle2D.Double modelBounds = model.getBounds();
        final double xmin = modelBounds.getMinX();
        final double xmax = modelBounds.getMaxX();
        final double ymin = modelBounds.getMinY();
        final double ymax = modelBounds.getMaxY();

        /* How much space have we got to paint in? */
        final int pxmax = getWidth() + 1;
        final int pymax = getHeight() + 1;
        final int pxmin = 0, pymin = 0;

        /* How many units per pixel? */
        final double xrat = (xmax - xmin) / (pxmax - pxmin);
        final double yrat = (ymax - ymin) / (pymax - pymin);
        final double rat = Math.max(xrat, yrat);

        /* To convert vertex co-ordinates (x,y) into pixels (px,py), px
         * = (x - xmin) / rat + pxmin, and py = (y - ymin) / rat +
         * pymin. */

        /* Draw the background. */
        g.setColor(Color.WHITE);
        g.fillRect(pxmin, pymin, pxmax - 1, pymax - 1);

        /* Draw the edges. */
        g.setColor(Color.BLACK);
        for (List<? extends Point2D.Double> edge : model.getEdges()) {
            final Point2D.Double first = edge.get(0);
            final Point2D.Double second = edge.get(1);
            final double x1 = first.x;
            final double y1 = first.y;
            final double x2 = second.x;
            final double y2 = second.y;
            final int px1 = (int) ((x1 - xmin) / rat + pxmin);
            final int py1 = (int) ((y1 - ymin) / rat + pymin);
            final int px2 = (int) ((x2 - xmin) / rat + pxmin);
            final int py2 = (int) ((y2 - ymin) / rat + pymin);
            g.drawLine(px1, py1, px2, py2);
        }
    }
}
