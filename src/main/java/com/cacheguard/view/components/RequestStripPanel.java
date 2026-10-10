package com.cacheguard.view.components;

import com.cacheguard.model.Simulation;
import com.cacheguard.model.StepRecord;
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
import java.util.List;

/**
 * The request sequence as a strip of chips: green for hits, red for misses, outlined for
 * the one that comes next. Long sequences scroll so the current position stays in view.
 *
 * <p>The strip reads the simulation directly, so it must not be painted while a background
 * run is mutating it; call {@link #setBusy(boolean)} around runs.
 */
public final class RequestStripPanel extends JComponent {

    private static final long serialVersionUID = 1L;
    private static final int CHIP_W = 38;
    private static final int CHIP_H = 30;
    private static final int GAP = 6;

    private transient Simulation simulation;
    private boolean busy;

    public RequestStripPanel() {
        setOpaque(false);
        setFont(Ui.baseFont().deriveFont(Font.BOLD, 12.5f));
    }

    public void setSimulation(Simulation simulation) {
        this.simulation = simulation;
        repaint();
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(200, CHIP_H + 30);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            ThemeManager.Palette p = ThemeManager.palette();
            g2.setFont(getFont().deriveFont(12f));
            g2.setColor(p.muted());
            if (busy) {
                g2.drawString("Running...", 4, 16);
                return;
            }
            if (simulation == null) {
                g2.drawString("No valid request sequence", 4, 16);
                return;
            }
            int total = simulation.total();
            int position = simulation.position();
            int visible = Math.max(1, (getWidth() + GAP) / (CHIP_W + GAP));
            visible = Math.min(visible, total);
            int start = Math.max(0, Math.min(position - visible / 3, total - visible));
            String caption = "Requests " + (start + 1) + "-" + (start + visible) + " of " + String.format("%,d", total);
            g2.drawString(caption, 4, 14);

            List<StepRecord> steps = simulation.isDetailed() ? simulation.steps() : List.of();
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int y = 24;
            for (int i = 0; i < visible; i++) {
                int idx = start + i;
                int x = i * (CHIP_W + GAP);
                boolean done = idx < position;
                boolean next = idx == position;
                Color base = p.empty();
                Color border = p.cardBorder();
                Color textColor = p.muted();
                if (done) {
                    boolean known = idx < steps.size();
                    boolean hit = known && steps.get(idx).hit();
                    Color c = !known ? p.accent() : hit ? p.hit() : p.miss();
                    base = CacheBlocksPanel.blend(p.card(), c, 0.22f);
                    border = c;
                    textColor = p.text();
                } else if (next) {
                    border = p.accent();
                    textColor = p.text();
                }
                g2.setColor(base);
                g2.fillRoundRect(x, y, CHIP_W, CHIP_H, 10, 10);
                g2.setColor(border);
                g2.setStroke(new BasicStroke(next ? 2f : 1f));
                g2.drawRoundRect(x, y, CHIP_W, CHIP_H, 10, 10);
                g2.setColor(textColor);
                String s = String.valueOf(simulation.requestAt(idx));
                if (fm.stringWidth(s) > CHIP_W - 4) {
                    s = s.substring(0, 3) + "..";
                }
                g2.drawString(s, x + (CHIP_W - fm.stringWidth(s)) / 2, y + (CHIP_H + fm.getAscent()) / 2 - 2);
            }
        } finally {
            g2.dispose();
        }
    }
}
