package traffic.desktop;

import traffic.model.RoadNetwork;

import java.awt.*;
import java.awt.geom.*;

/** Stateless world-space road artwork, independent of application controls. */
final class RoadRenderer {
    private static final Color ROAD = new Color(55, 65, 76);
    private static final Color WHITE = new Color(237, 244, 238);

    void draw(Graphics2D g) {
        double e = RoadNetwork.EXTENT + 20;
        g.setColor(new Color(183, 197, 190));
        g.fill(new java.awt.geom.Rectangle2D.Double(-e, -6.1, 2 * e, 12.2));
        g.fill(new java.awt.geom.Rectangle2D.Double(-6.1, -e, 12.2, 2 * e));
        g.setColor(new Color(242, 242, 229));
        g.fill(new java.awt.geom.Rectangle2D.Double(-e, -5.3, 2 * e, 10.6));
        g.fill(new java.awt.geom.Rectangle2D.Double(-5.3, -e, 10.6, 2 * e));
        g.setColor(ROAD);
        g.fill(new java.awt.geom.Rectangle2D.Double(-e, -3.5, 2 * e, 7));
        g.fill(new java.awt.geom.Rectangle2D.Double(-3.5, -e, 7, 2 * e));
        g.fill(new RoundRectangle2D.Double(-11, -11, 22, 22, 5, 5));
        g.setColor(new Color(161, 174, 164));
        g.setStroke(new BasicStroke(.25f));
        g.draw(new java.awt.geom.Rectangle2D.Double(-e, -5.3, 2 * e, 10.6));
        g.draw(new java.awt.geom.Rectangle2D.Double(-5.3, -e, 10.6, 2 * e));
        for (RoadNetwork.Arm arm : RoadNetwork.Arm.values()) {
            Graphics2D a = (Graphics2D) g.create();
            a.rotate(Math.atan2(arm.y, arm.x));
            a.setColor(new Color(233, 205, 111));
            a.setStroke(new BasicStroke(.13f));
            a.drawLine(12, 0, (int) e, 0);
            a.setColor(WHITE);
            a.setStroke(
                    new BasicStroke(
                            .15f,
                            BasicStroke.CAP_BUTT,
                            BasicStroke.JOIN_MITER,
                            10,
                            new float[] {2.5f, 3.5f},
                            0));
            a.draw(new java.awt.geom.Line2D.Double(13, -3.1, e, -3.1));
            a.draw(new java.awt.geom.Line2D.Double(13, 3.1, e, 3.1));
            a.setStroke(new BasicStroke(.45f));
            a.draw(new java.awt.geom.Line2D.Double(11, .25, 11, 3.3));
            a.setStroke(new BasicStroke(.2f));
            a.draw(new java.awt.geom.Line2D.Double(22, 1.75, 16, 1.75));
            Path2D arrow = new Path2D.Double();
            arrow.moveTo(16, 1.75);
            arrow.lineTo(18, 1.1);
            arrow.moveTo(16, 1.75);
            arrow.lineTo(18, 2.4);
            a.draw(arrow);
            a.dispose();
        }
        g.setColor(new Color(230, 234, 230, 65));
        g.setStroke(
                new BasicStroke(
                        .12f,
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND,
                        10,
                        new float[] {.8f, 1.3f},
                        0));
        g.draw(new java.awt.geom.Rectangle2D.Double(-8, -8, 16, 16));
    }
}
