package com.cacheguard.view.components;

import com.cacheguard.util.ThemeManager;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

/**
 * A dependency-free bar chart: one bar per category, value labels above, grid behind.
 */
public final class BarChartPanel extends JComponent {

    private static final long serialVersionUID = 1L;

    /** One bar. */
    public record Bar(String label, double value, Color color) {
    }

    private List<Bar> bars = List.of();
    private double fixedMax;
    private String unit = "";
    private int decimals = 1;
    private String emptyMessage = "No data yet";

    public BarChartPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(360, 240));
    }

    /** Bars are drawn in order; pass an empty list to show the empty message. */
    public void setBars(List<Bar> bars) {
        this.bars = List.copyOf(bars);
        repaint();
    }

    /** Fixes the top of the value axis (for example 100 for percentages); 0 means automatic. */
    public void setFixedMax(double max) {
        this.fixedMax = max;
        repaint();
    }

    public void setUnit(String unit, int decimals) {
        this.unit = unit;
        this.decimals = decimals;
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
            g2.setFont(base.deriveFont(11f));
            double axisMax = fixedMax > 0 ? fixedMax
                    : niceMax(bars.stream().mapToDouble(Bar::value).max().orElse(1));
            int left = Math.max(46, g2.getFontMetrics().stringWidth(axisLabel(axisMax)) + 18);
            int right = 10;
            int top = 22;
            int bottom = 30;
            int w = getWidth() - left - right;
            int h = getHeight() - top - bottom;
            if (w < 20 || h < 20) {
                return;
            }
            if (bars.isEmpty()) {
                g2.setFont(base.deriveFont(13f));
                g2.setColor(p.muted());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(emptyMessage, (getWidth() - fm.stringWidth(emptyMessage)) / 2, getHeight() / 2);
                return;
            }
            double max = fixedMax > 0 ? fixedMax : niceMax(bars.stream().mapToDouble(Bar::value).max().orElse(1));

            g2.setFont(base.deriveFont(11f));
            FontMetrics fm = g2.getFontMetrics();
            for (int i = 0; i <= 4; i++) {
                double v = max * i / 4;
                int y = top + h - (int) Math.round(h * i / 4.0);
                g2.setColor(p.grid());
                g2.drawLine(left, y, left + w, y);
                g2.setColor(p.muted());
                String label = axisLabel(v);
                g2.drawString(label, left - 6 - fm.stringWidth(label), y + fm.getAscent() / 2 - 1);
            }

            int n = bars.size();
            double slot = (double) w / n;
            int barW = (int) Math.max(14, Math.min(64, slot * 0.58));
            for (int i = 0; i < n; i++) {
                Bar b = bars.get(i);
                int cx = left + (int) Math.round(slot * i + slot / 2);
                int bh = (int) Math.round(h * Math.min(1.0, b.value() / max));
                int x = cx - barW / 2;
                int y = top + h - bh;
                g2.setColor(b.color());
                if (bh > 0) {
                    g2.fillRoundRect(x, y, barW, bh + 6, 8, 8);
                    g2.setColor(p.card());
                    g2.fillRect(x - 1, top + h, barW + 2, 8);
                }
                g2.setColor(p.text());
                g2.setFont(base.deriveFont(Font.BOLD, 11.5f));
                String v = String.format("%,." + decimals + "f%s", b.value(), unit);
                FontMetrics vf = g2.getFontMetrics();
                g2.drawString(v, cx - vf.stringWidth(v) / 2, y - 5);
                g2.setColor(p.muted());
                g2.setFont(base.deriveFont(11.5f));
                FontMetrics lf = g2.getFontMetrics();
                String label = b.label();
                g2.drawString(label, cx - lf.stringWidth(label) / 2, top + h + 18);
            }
        } finally {
            g2.dispose();
        }
    }

    private String axisLabel(double v) {
        if (fixedMax > 0 || v == Math.rint(v)) {
            return String.format("%,.0f", v);
        }
        return String.format("%.1f", v);
    }

    /** Rounds up to 1, 2, 2.5, 5 or 10 times a power of ten, so axis ticks look tidy. */
    static double niceMax(double v) {
        if (v <= 0) {
            return 1;
        }
        double exp = Math.pow(10, Math.floor(Math.log10(v)));
        double f = v / exp;
        double nice = f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10;
        return nice * exp;
    }
}
