package com.cacheguard.util;

import com.cacheguard.model.AppTheme;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.UIManager;
import java.awt.Color;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Installs the FlatLaf dark or light theme and hands custom-painted components
 * a matching colour palette. All methods must be called on the Swing event thread
 * (except {@link #palette()}, which only reads state).
 */
public final class ThemeManager {

    private static final Logger LOG = Logger.getLogger(ThemeManager.class.getName());

    /** Colours used by components that paint themselves. */
    public record Palette(Color accent, Color hit, Color miss, Color warning,
                          Color card, Color cardBorder, Color text, Color muted,
                          Color grid, Color empty, Color[] algorithmColors) {
    }

    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile AppTheme current = AppTheme.DARK;
    private static String lookAndFeelName = "not installed";

    private ThemeManager() {
    }

    public static AppTheme current() {
        return current;
    }

    public static String lookAndFeelName() {
        return lookAndFeelName;
    }

    /** Runs after every theme switch, once the new look and feel is installed. */
    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static void apply(AppTheme theme) {
        UIManager.put("Button.arc", 10);
        UIManager.put("Component.arc", 10);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("ProgressBar.arc", 10);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
        UIManager.put("TabbedPane.showTabSeparators", true);
        try {
            FlatLaf.setGlobalExtraDefaults(Collections.singletonMap("@accentColor", "#3B82F6"));
            boolean ok = theme == AppTheme.DARK ? FlatDarkLaf.setup() : FlatLightLaf.setup();
            if (!ok) {
                throw new IllegalStateException("FlatLaf refused to install");
            }
            FlatLaf.updateUI();
            lookAndFeelName = UIManager.getLookAndFeel().getName();
        } catch (RuntimeException | LinkageError e) {
            LOG.log(Level.WARNING, "FlatLaf unavailable, falling back to the system look and feel", e);
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                lookAndFeelName = UIManager.getLookAndFeel().getName() + " (fallback)";
            } catch (Exception ignored) {
                lookAndFeelName = "Swing default";
            }
        }
        current = theme;
        for (Runnable l : LISTENERS) {
            l.run();
        }
    }

    public static Palette palette() {
        boolean dark = current == AppTheme.DARK;
        Color base = UIManager.getColor("Panel.background");
        if (base == null) {
            base = dark ? new Color(0x2B2D30) : new Color(0xF2F2F2);
        }
        Color text = UIManager.getColor("Label.foreground");
        if (text == null) {
            text = dark ? new Color(0xDFE1E5) : new Color(0x1F2328);
        }
        Color card = dark ? shift(base, 12) : Color.WHITE;
        Color border = dark ? shift(base, 34) : new Color(0xD0D7DE);
        Color muted = dark ? new Color(0x9AA0A6) : new Color(0x656D76);
        Color grid = dark ? shift(base, 26) : new Color(0xE3E7EB);
        Color empty = dark ? shift(base, 22) : new Color(0xE9EDF1);
        Color[] algs = dark
                ? new Color[] {new Color(0x60A5FA), new Color(0xFBBF24), new Color(0xA78BFA),
                        new Color(0x2DD4BF), new Color(0xF472B6)}
                : new Color[] {new Color(0x2563EB), new Color(0xD97706), new Color(0x7C3AED),
                        new Color(0x0D9488), new Color(0xDB2777)};
        return new Palette(
                new Color(0x3B82F6),
                dark ? new Color(0x34D399) : new Color(0x16A34A),
                dark ? new Color(0xF87171) : new Color(0xDC2626),
                dark ? new Color(0xFBBF24) : new Color(0xD97706),
                card, border, text, muted, grid, empty, algs);
    }

    private static Color shift(Color c, int amount) {
        return new Color(clamp(c.getRed() + amount), clamp(c.getGreen() + amount), clamp(c.getBlue() + amount));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
