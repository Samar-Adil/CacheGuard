package com.cacheguard.view.components;

import com.cacheguard.model.SessionRecord;

import javax.swing.table.AbstractTableModel;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Session history as a table, newest first.
 */
public final class SessionTableModel extends AbstractTableModel {

    private static final long serialVersionUID = 1L;
    private static final String[] COLUMNS = {"When", "Source", "Algorithm", "Blocks", "Requests", "Hit ratio"};
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final int limit;
    private List<SessionRecord> rows = List.of();

    /** @param limit maximum rows to show, or 0 for all */
    public SessionTableModel(int limit) {
        this.limit = limit;
    }

    /** @param historyOldestFirst the history as kept by the model */
    public void setHistory(List<SessionRecord> historyOldestFirst) {
        List<SessionRecord> copy = new ArrayList<>(historyOldestFirst);
        Collections.reverse(copy);
        rows = limit > 0 && copy.size() > limit ? List.copyOf(copy.subList(0, limit)) : List.copyOf(copy);
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return switch (column) {
            case 2 -> com.cacheguard.model.CacheAlgorithm.class;
            case 3, 4 -> Integer.class;
            case 5 -> Double.class;
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int row, int column) {
        SessionRecord s = rows.get(row);
        return switch (column) {
            case 0 -> TIME.format(s.time());
            case 1 -> s.source().substring(0, 1).toUpperCase() + s.source().substring(1);
            case 2 -> s.algorithm();
            case 3 -> s.capacity();
            case 4 -> s.requestCount();
            default -> s.hitPercent();
        };
    }
}
