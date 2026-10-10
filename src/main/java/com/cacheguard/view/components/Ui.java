package com.cacheguard.view.components;

import com.cacheguard.util.ThemeManager;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;

/**
 * Small factory methods that keep typography and colours consistent across screens.
 * Colours that depend on the theme are looked up at paint time, so labels follow a
 * theme switch without being rebuilt.
 */
public final class Ui {

    private Ui() {
    }

    /** The default UI font; safe to call before a component is attached to a window. */
    public static Font baseFont() {
        Font f = javax.swing.UIManager.getFont("Label.font");
        return f != null ? f : new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    }

    public static JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 26f));
        return l;
    }

    public static JLabel section(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 15f));
        return l;
    }

    public static JLabel muted(String text) {
        return muted(text, 12.5f);
    }

    public static JLabel muted(String text, float size) {
        JLabel l = new JLabel(text) {
            private static final long serialVersionUID = 1L;

            @Override
            public Color getForeground() {
                return ThemeManager.palette().muted();
            }
        };
        l.setFont(l.getFont().deriveFont(size));
        return l;
    }

    /** A label that shows validation messages in the theme's "miss" red. */
    public static JLabel errorLabel() {
        JLabel l = new JLabel(" ") {
            private static final long serialVersionUID = 1L;

            @Override
            public Color getForeground() {
                return ThemeManager.palette().miss();
            }
        };
        l.setFont(l.getFont().deriveFont(12.5f));
        return l;
    }

    public static JButton primaryButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(new Color(0x3B82F6));
        b.setForeground(Color.WHITE);
        b.setFont(b.getFont().deriveFont(Font.BOLD));
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.putClientProperty("FlatLaf.style",
                "hoverBackground: lighten(#3B82F6,6%); pressedBackground: darken(#3B82F6,6%)");
        return b;
    }

    public static JButton button(String text) {
        JButton b = new JButton(text);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** A placeholder hint inside an empty text field (FlatLaf only; harmless elsewhere). */
    public static void placeholder(javax.swing.JComponent field, String text) {
        field.putClientProperty("JTextField.placeholderText", text);
    }

    /** Right-aligns the given table columns and returns the table for chaining. */
    public static JTable alignRight(JTable table, int... columns) {
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        for (int c : columns) {
            table.getColumnModel().getColumn(c).setCellRenderer(right);
        }
        return table;
    }

    public static JTable style(JTable table) {
        table.setRowHeight(30);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setIntercellSpacing(new java.awt.Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.setBorder(BorderFactory.createEmptyBorder());
        return table;
    }
}
