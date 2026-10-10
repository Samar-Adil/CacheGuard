package com.cacheguard.view.components;

import com.cacheguard.model.CacheAlgorithm;
import com.cacheguard.util.ThemeManager;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Cell renderers shared by the tables.
 */
public final class TableRenderers {

    private TableRenderers() {
    }

    /** Integers and longs with thousands separators, right aligned. */
    public static TableCellRenderer number() {
        return new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            {
                setHorizontalAlignment(SwingConstants.RIGHT);
            }

            @Override
            protected void setValue(Object value) {
                setText(value instanceof Number n ? String.format("%,d", n.longValue()) : "");
            }
        };
    }

    /** Doubles with a fixed number of decimals, right aligned. */
    public static TableCellRenderer decimal(int decimals) {
        return new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            {
                setHorizontalAlignment(SwingConstants.RIGHT);
            }

            @Override
            protected void setValue(Object value) {
                setText(value instanceof Number n ? String.format("%,." + decimals + "f", n.doubleValue()) : "");
            }
        };
    }

    /** "HIT" green and "MISS" red, bold. */
    public static TableCellRenderer outcome() {
        return new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                super.getTableCellRendererComponent(table, value, selected, focus, row, column);
                ThemeManager.Palette p = ThemeManager.palette();
                if (!selected) {
                    setForeground("HIT".equals(value) ? p.hit() : p.miss());
                }
                setFont(getFont().deriveFont(Font.BOLD));
                return this;
            }
        };
    }

    /** Algorithm name with a small dot in the algorithm's chart colour. */
    public static TableCellRenderer algorithm() {
        return new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                super.getTableCellRendererComponent(table, value, selected, focus, row, column);
                if (value instanceof CacheAlgorithm a) {
                    setText(a.shortName());
                    Color c = ThemeManager.palette().algorithmColors()[a.ordinal()];
                    setIcon(new DotIcon(c));
                    setIconTextGap(8);
                }
                return this;
            }
        };
    }

    /** A percentage (0-100) drawn as a bar with the number on top. */
    public static TableCellRenderer percentBar() {
        return new PercentBar();
    }

    private record DotIcon(Color color) implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(x, y + 1, 10, 10);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 10;
        }

        @Override
        public int getIconHeight() {
            return 12;
        }
    }

    private static final class PercentBar extends JComponent implements TableCellRenderer {
        private static final long serialVersionUID = 1L;
        private double value;
        private boolean selected;
        private Color background;
        private Color foreground;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object v, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            value = v instanceof Number n ? n.doubleValue() : 0;
            selected = isSelected;
            background = isSelected ? table.getSelectionBackground() : table.getBackground();
            foreground = isSelected ? table.getSelectionForeground() : table.getForeground();
            setFont(table.getFont());
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(background);
                g2.fillRect(0, 0, getWidth(), getHeight());
                ThemeManager.Palette p = ThemeManager.palette();
                int pad = 8;
                int bh = getHeight() - 14;
                int bw = getWidth() - 2 * pad;
                g2.setColor(p.empty());
                g2.fillRoundRect(pad, 7, bw, bh, bh, bh);
                Color bar = selected ? p.accent() : CacheBlocksPanel.blend(p.card(), p.hit(), 0.55f);
                int fill = (int) Math.round(bw * Math.max(0, Math.min(100, value)) / 100.0);
                if (fill > 0) {
                    g2.setColor(bar);
                    g2.fillRoundRect(pad, 7, Math.max(fill, bh), bh, bh, bh);
                }
                g2.setColor(selected ? foreground : p.text());
                g2.setFont(getFont().deriveFont(Font.BOLD));
                FontMetrics fm = g2.getFontMetrics();
                String text = String.format("%.2f%%", value);
                g2.drawString(text, (getWidth() - fm.stringWidth(text)) / 2,
                        (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            } finally {
                g2.dispose();
            }
        }
    }
}
