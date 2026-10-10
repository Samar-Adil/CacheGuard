package com.cacheguard.view.components;

import com.cacheguard.util.ThemeManager;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Draws the cache as a row (or two rows) of blocks. Each block shows its key, a policy note
 * such as "freq 3", and whether it is next in line for eviction. The block touched by the
 * latest request pulses green for a hit or red for a miss.
 */
public final class CacheBlocksPanel extends JComponent {

    private static final long serialVersionUID = 1L;
    private static final int PAD = 12;
    private static final int GAP = 12;
    private static final int BLOCK_H = 84;
    private static final int LABEL_H = 18;
    private static final int FOOT_H = 18;
    private static final int PULSE_MS = 700;

    private int capacity = 4;
    private Integer[] keys = new Integer[4];
    private String[] notes = new String[4];
    private int victimSlot = -1;
    private int highlightSlot = -1;
    private boolean highlightHit;
    private Integer evictedKey;
    private long pulseStart;
    private final Timer pulseTimer = new Timer(30, e -> {
        if (System.currentTimeMillis() - pulseStart > PULSE_MS) {
            ((Timer) e.getSource()).stop();
        }
        repaint();
    });

    public CacheBlocksPanel() {
        setOpaque(false);
        setFont(Ui.baseFont().deriveFont(Font.PLAIN, 12f));
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
        this.keys = new Integer[capacity];
        this.notes = new String[capacity];
        this.victimSlot = -1;
        this.highlightSlot = -1;
        revalidate();
        repaint();
    }

    /**
     * @param keys      key per slot, {@code null} for empty
     * @param notes     optional note per slot
     * @param victimKey key that would be evicted next, or {@code null}
     */
    public void showState(Integer[] keys, String[] notes, Integer victimKey) {
        this.capacity = keys.length;
        this.keys = keys.clone();
        this.notes = notes.clone();
        this.victimSlot = -1;
        if (victimKey != null) {
            for (int i = 0; i < keys.length; i++) {
                if (victimKey.equals(keys[i])) {
                    victimSlot = i;
                }
            }
        }
        repaint();
    }

    /** Pulses {@code slot}; {@code evicted} is shown under it when a miss replaced a block. */
    public void highlight(int slot, boolean hit, Integer evicted) {
        this.highlightSlot = slot;
        this.highlightHit = hit;
        this.evictedKey = evicted;
        this.pulseStart = System.currentTimeMillis();
        pulseTimer.restart();
        repaint();
    }

    public void clearHighlight() {
        highlightSlot = -1;
        evictedKey = null;
        pulseTimer.stop();
        repaint();
    }

    private int columns() {
        return capacity <= 8 ? capacity : (capacity + 1) / 2;
    }

    private int rows() {
        return (capacity + columns() - 1) / columns();
    }

    private int rowHeight() {
        return LABEL_H + BLOCK_H + FOOT_H + 6;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(columns() * 80, PAD * 2 + rows() * rowHeight());
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(columns() * 64, PAD * 2 + rows() * rowHeight());
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            ThemeManager.Palette p = ThemeManager.palette();
            int cols = columns();
            int avail = getWidth() - 2 * PAD;
            int bw = Math.max(52, Math.min(110, (avail - GAP * (cols - 1)) / cols));
            int totalW = cols * bw + (cols - 1) * GAP;
            int x0 = (getWidth() - totalW) / 2;
            int y0 = PAD;
            float pulse = Math.max(0f, 1f - (System.currentTimeMillis() - pulseStart) / (float) PULSE_MS);
            Font base = getFont();

            for (int i = 0; i < capacity; i++) {
                int r = i / cols;
                int c = i % cols;
                int x = x0 + c * (bw + GAP);
                int top = y0 + r * rowHeight();
                int y = top + LABEL_H;

                g2.setFont(base.deriveFont(11f));
                g2.setColor(p.muted());
                centered(g2, "Block " + i, x, top + 12, bw);

                boolean empty = keys[i] == null;
                boolean lit = i == highlightSlot;
                Color accent = lit ? (highlightHit ? p.hit() : p.miss()) : p.accent();
                Color fill = empty ? p.empty() : blend(p.card(), p.accent(), 0.12f);
                if (lit) {
                    fill = blend(p.card(), accent, 0.14f + 0.22f * pulse);
                }
                g2.setColor(fill);
                g2.fillRoundRect(x, y, bw, BLOCK_H, 14, 14);

                if (empty) {
                    g2.setColor(p.cardBorder());
                    g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                            new float[] {5f, 4f}, 0f));
                } else {
                    g2.setColor(lit ? accent : blend(p.card(), p.accent(), 0.55f));
                    g2.setStroke(new BasicStroke(lit ? 2f + 2f * pulse : 1.4f));
                }
                g2.drawRoundRect(x, y, bw, BLOCK_H, 14, 14);
                g2.setStroke(new BasicStroke(1f));

                if (empty) {
                    g2.setFont(base.deriveFont(Font.ITALIC, 12f));
                    g2.setColor(p.muted());
                    centered(g2, "empty", x, y + BLOCK_H / 2 + 4, bw);
                } else {
                    g2.setFont(base.deriveFont(Font.BOLD, 28f));
                    g2.setColor(p.text());
                    centered(g2, String.valueOf(keys[i]), x, y + 44, bw);
                    if (notes[i] != null && !notes[i].isEmpty()) {
                        g2.setFont(base.deriveFont(11.5f));
                        g2.setColor(p.muted());
                        centered(g2, notes[i], x, y + 68, bw);
                    }
                }

                g2.setFont(base.deriveFont(Font.BOLD, 11f));
                int footY = y + BLOCK_H + 14;
                if (lit && !highlightHit && evictedKey != null) {
                    g2.setColor(p.miss());
                    centered(g2, "evicted " + evictedKey, x, footY, bw);
                } else if (i == victimSlot) {
                    g2.setColor(p.warning());
                    centered(g2, "next out", x, footY, bw);
                }
            }
        } finally {
            g2.dispose();
        }
    }

    private static void centered(Graphics2D g2, String text, int x, int baseline, int width) {
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, x + (width - fm.stringWidth(text)) / 2, baseline);
    }

    static Color blend(Color a, Color b, float t) {
        float u = 1f - t;
        return new Color(
                Math.round(a.getRed() * u + b.getRed() * t),
                Math.round(a.getGreen() * u + b.getGreen() * t),
                Math.round(a.getBlue() * u + b.getBlue() * t));
    }
}
