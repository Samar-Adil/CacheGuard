package com.cacheguard.view.components;

import com.cacheguard.util.ThemeManager;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.List;

/**
 * A dependency-free multi-series line chart for percentages (0-100 on the y axis).
 */
public final class LineChartPanel extends JComponent {

    private static final long serialVersionUID = 1L;

    /** One point: {@code x} is a position or index, {@code y} a percentage. */
    public record Point(double x, double y) {
    }

    /** A named line. */
    public record Series(String name, Color color, List<Point> points) {
    }

    private List<Series> series = List.of();
    private String xLabel = "";
    private String emptyMessage = "No data yet";

    public LineChartPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(420, 240));
    }

    public void setSeries(List<Series> series) {
        this.series = List.copyOf(series);
        repaint();
    }

    public void setXLabel(String label) {
        this.xLabel = label;
        repaint();
    }

    public void setEmptyMessage(String message) {
        this.emptyMessage = message;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            ThemeManager.Palette p = ThemeManager.palette();
            Font base = getFont();
            int left = 46;
            int right = 14;
            int top = 30;
            int bottom = 34;
            int w = getWidth() - left - right;
            int h = getHeight() - top - bottom;
            if (w < 20 || h < 20) {
                return;
            }
            boolean any = series.stream().anyMatch(s -> !s.points().isEmpty());
            if (!any) {
                g2.setFont(base.deriveFont(13f));
                g2.setColor(p.muted());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(emptyMessage, (getWidth() - fm.stringWidth(emptyMessage)) / 2, getHeight() / 2);
                return;
            }
            double minX = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            for (Series s : series) {
                for (Point pt : s.points()) {
                    minX = Math.min(minX, pt.x());
                    maxX = Math.max(maxX, pt.x());
                }
            }
            if (maxX - minX < 1e-9) {
                minX -= 1;
                maxX += 1;
            }

            g2.setFont(base.deriveFont(11f));
            FontMetrics fm = g2.getFontMetrics();
            for (int i = 0; i <= 4; i++) {
                int y = top + h - (int) Math.round(h * i / 4.0);
                g2.setColor(p.grid());
                g2.drawLine(left, y, left + w, y);
                g2.setColor(p.muted());
                String label = (i * 25) + "%";
                g2.drawString(label, left - 6 - fm.stringWidth(label), y + fm.getAscent() / 2 - 1);
            }
            for (int i = 0; i <= 4; i++) {
                double xv = minX + (maxX - minX) * i / 4;
                int x = left + (int) Math.round(w * i / 4.0);
                String label = String.format("%,.0f", xv);
                g2.setColor(p.muted());
                g2.drawString(label, x - fm.stringWidth(label) / 2, top + h + 15);
            }
            if (!xLabel.isEmpty()) {
                g2.drawString(xLabel, left + (w - fm.stringWidth(xLabel)) / 2, top + h + 30);
            }

            for (Series s : series) {
                if (s.points().isEmpty()) {
                    continue;
                }
                g2.setColor(s.color());
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Path2D path = new Path2D.Double();
                boolean first = true;
                for (Point pt : s.points()) {
                    double px = left + w * (pt.x() - minX) / (maxX - minX);
                    double py = top + h - h * Math.max(0, Math.min(100, pt.y())) / 100.0;
                    if (first) {
                        path.moveTo(px, py);
                        first = false;
                    } else {
                        path.lineTo(px, py);
                    }
                }
                g2.draw(path);
                if (s.points().size() <= 40) {
                    for (Point pt : s.points()) {
                        double px = left + w * (pt.x() - minX) / (maxX - minX);
                        double py = top + h - h * Math.max(0, Math.min(100, pt.y())) / 100.0;
                        g2.fillOval((int) Math.round(px) - 3, (int) Math.round(py) - 3, 7, 7);
                    }
                }
            }

            g2.setStroke(new BasicStroke(1f));
            g2.setFont(base.deriveFont(11.5f));
            FontMetrics lf = g2.getFontMetrics();
            int lx = left;
            for (Series s : series) {
                if (s.points().isEmpty()) {
                    continue;
                }
                g2.setColor(s.color());
                g2.fillRoundRect(lx, 10, 12, 12, 4, 4);
                g2.setColor(p.text());
                g2.drawString(s.name(), lx + 17, 10 + lf.getAscent() - 1);
                lx += 17 + lf.stringWidth(s.name()) + 18;
            }
        } finally {
            g2.dispose();
        }
    }
}
