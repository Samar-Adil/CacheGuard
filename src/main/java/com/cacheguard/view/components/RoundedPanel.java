package com.cacheguard.view.components;

import com.cacheguard.util.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;

/**
 * A card: rounded, bordered surface that follows the active theme.
 */
public class RoundedPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final int ARC = 16;

    public RoundedPanel(LayoutManager layout) {
        super(layout);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ThemeManager.Palette p = ThemeManager.palette();
            g2.setColor(p.card());
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
            g2.setColor(p.cardBorder());
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
