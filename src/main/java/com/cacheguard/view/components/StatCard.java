package com.cacheguard.view.components;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Dimension;
import javax.swing.JPanel;

/**
 * A summary tile: small title, big value, optional caption.
 */
public final class StatCard extends RoundedPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel value = new JLabel("-");
    private final JLabel caption = Ui.muted(" ", 12f);

    public StatCard(String title) {
        super(new BorderLayout());
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        JLabel t = Ui.muted(title, 12.5f);
        value.setFont(value.getFont().deriveFont(Font.BOLD, 28f));
        t.setAlignmentX(LEFT_ALIGNMENT);
        value.setAlignmentX(LEFT_ALIGNMENT);
        caption.setAlignmentX(LEFT_ALIGNMENT);
        box.add(t);
        box.add(javax.swing.Box.createVerticalStrut(4));
        box.add(value);
        box.add(javax.swing.Box.createVerticalStrut(2));
        box.add(caption);
        add(box, BorderLayout.CENTER);
        setPreferredSize(new Dimension(180, 112));
    }

    public StatCard set(String valueText, String captionText) {
        value.setText(valueText);
        caption.setText(captionText == null || captionText.isEmpty() ? " " : captionText);
        return this;
    }

    public StatCard setValueColor(Color color) {
        value.setForeground(color);
        return this;
    }
}
