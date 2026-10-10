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

/**
 * A small pill showing the outcome of the last request: HIT, MISS or a neutral note.
 */
public final class StatusBadge extends JComponent {

    private static final long serialVersionUID = 1L;

    public enum Kind { IDLE, HIT, MISS, INFO }

    private Kind kind = Kind.IDLE;
    private String text = "READY";

    public StatusBadge() {
        setOpaque(false);
        setFont(Ui.baseFont().deriveFont(Font.BOLD, 12f));
    }

    public void show(Kind kind, String text) {
        this.kind = kind;
        this.text = text;
        revalidate();
        repaint();
    }

    private Color color() {
        ThemeManager.Palette p = ThemeManager.palette();
        return switch (kind) {
            case HIT -> p.hit();
            case MISS -> p.miss();
            case INFO -> p.accent();
            case IDLE -> p.muted();
        };
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(fm.stringWidth(text) + 28, fm.getHeight() + 10);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Color c = color();
            int w = getWidth() - 1;
            int h = getHeight() - 1;
            g2.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 38));
            g2.fillRoundRect(0, 0, w, h, h, h);
            g2.setColor(c);
            g2.drawRoundRect(0, 0, w, h, h, h);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, (getWidth() - fm.stringWidth(text)) / 2,
                    (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        } finally {
            g2.dispose();
        }
    }
}
