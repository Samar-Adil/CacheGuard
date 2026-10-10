package com.cacheguard.view;

import com.cacheguard.view.components.Ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;

/**
 * Common frame for every screen: a title block on top and a scrolling body below.
 * The body tracks the viewport width, so content reflows instead of scrolling sideways.
 */
abstract class ScreenPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JPanel body = new Body();

    protected ScreenPanel(String title, String subtitle) {
        super(new BorderLayout());
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBorder(BorderFactory.createEmptyBorder(24, 28, 8, 28));
        var h = Ui.heading(title);
        var s = Ui.muted(subtitle, 13.5f);
        h.setAlignmentX(LEFT_ALIGNMENT);
        s.setAlignmentX(LEFT_ALIGNMENT);
        header.add(h);
        header.add(Box.createVerticalStrut(4));
        header.add(s);
        add(header, BorderLayout.NORTH);

        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(BorderFactory.createEmptyBorder(8, 28, 24, 28));
        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
    }

    /** Add content here; components are stacked vertically and stretch to the full width. */
    protected final JPanel body() {
        return body;
    }

    protected final void addRow(java.awt.Component c) {
        if (c instanceof javax.swing.JComponent jc) {
            jc.setAlignmentX(LEFT_ALIGNMENT);
        }
        body.add(c);
        body.add(Box.createVerticalStrut(16));
    }

    /** Called when the screen becomes visible; override to refresh. */
    protected void onShown() {
    }

    void shown() {
        onShown();
    }

    private static final class Body extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visibleRect.height - 40 : visibleRect.width - 40;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
