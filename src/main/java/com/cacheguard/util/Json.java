package com.cacheguard.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small, dependency-free JSON reader and writer (RFC 8259).
 *
 * <p>Parsed values are {@link Map} (insertion-ordered), {@link List}, {@link String},
 * {@link Long} / {@link Double}, {@link Boolean} or {@code null}.
 */
public final class Json {

    private static final int MAX_DEPTH = 128;

    private Json() {
    }

    /** Thrown for malformed JSON or unsupported values when writing. */
    public static final class JsonException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public JsonException(String message) {
            super(message);
        }
    }

    // ------------------------------------------------------------------ parsing

    public static Object parse(String text) {
        if (text == null) {
            throw new JsonException("No JSON text supplied");
        }
        Parser p = new Parser(text);
        p.skipBom();
        p.skipWhitespace();
        Object value = p.readValue(0);
        p.skipWhitespace();
        if (!p.atEnd()) {
            throw p.error("Unexpected content after the JSON value");
        }
        return value;
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return pos >= s.length();
        }

        void skipBom() {
            if (!atEnd() && s.charAt(0) == '﻿') {
                pos++;
            }
        }

        void skipWhitespace() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        JsonException error(String message) {
            int line = 1;
            int col = 1;
            for (int i = 0; i < Math.min(pos, s.length()); i++) {
                if (s.charAt(i) == '\n') {
                    line++;
                    col = 1;
                } else {
                    col++;
                }
            }
            return new JsonException(message + " (line " + line + ", column " + col + ")");
        }

        Object readValue(int depth) {
            if (depth > MAX_DEPTH) {
                throw error("Nesting is too deep");
            }
            if (atEnd()) {
                throw error("Unexpected end of input");
            }
            char c = s.charAt(pos);
            switch (c) {
                case '{':
                    return readObject(depth);
                case '[':
                    return readArray(depth);
                case '"':
                    return readString();
                case 't':
                    expectLiteral("true");
                    return Boolean.TRUE;
                case 'f':
                    expectLiteral("false");
                    return Boolean.FALSE;
                case 'n':
                    expectLiteral("null");
                    return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return readNumber();
                    }
                    throw error("Unexpected character '" + c + "'");
            }
        }

        private void expectLiteral(String literal) {
            if (!s.startsWith(literal, pos)) {
                throw error("Invalid literal, expected '" + literal + "'");
            }
            pos += literal.length();
        }

        private Map<String, Object> readObject(int depth) {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // {
            skipWhitespace();
            if (peek('}')) {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (!peek('"')) {
                    throw error("Expected a string key");
                }
                String key = readString();
                skipWhitespace();
                if (!peek(':')) {
                    throw error("Expected ':' after key");
                }
                pos++;
                skipWhitespace();
                map.put(key, readValue(depth + 1));
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                } else if (peek('}')) {
                    pos++;
                    return map;
                } else {
                    throw error("Expected ',' or '}' in object");
                }
            }
        }

        private List<Object> readArray(int depth) {
            List<Object> list = new ArrayList<>();
            pos++; // [
            skipWhitespace();
            if (peek(']')) {
                pos++;
                return list;
            }
            while (true) {
                skipWhitespace();
                list.add(readValue(depth + 1));
                skipWhitespace();
                if (peek(',')) {
                    pos++;
                } else if (peek(']')) {
                    pos++;
                    return list;
                } else {
                    throw error("Expected ',' or ']' in array");
                }
            }
        }

        private boolean peek(char c) {
            return pos < s.length() && s.charAt(pos) == c;
        }

        private String readString() {
            pos++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw error("Unterminated string");
                }
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c < 0x20) {
                    pos--;
                    throw error("Control character in string");
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (atEnd()) {
                    throw error("Unterminated escape sequence");
                }
                char e = s.charAt(pos++);
                switch (e) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        if (pos + 4 > s.length()) {
                            throw error("Incomplete unicode escape");
                        }
                        try {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error("Invalid unicode escape");
                        }
                        pos += 4;
                        break;
                    default:
                        pos--;
                        throw error("Invalid escape '\\" + e + "'");
                }
            }
        }

        private Object readNumber() {
            int start = pos;
            boolean fractional = false;
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c >= '0' && c <= '9' || c == '-' || c == '+') {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E') {
                    fractional = true;
                    pos++;
                } else {
                    break;
                }
            }
            String token = s.substring(start, pos);
            try {
                if (!fractional) {
                    try {
                        return Long.parseLong(token);
                    } catch (NumberFormatException overflow) {
                        return Double.parseDouble(token);
                    }
                }
                return Double.parseDouble(token);
            } catch (NumberFormatException ex) {
                pos = start;
                throw error("Invalid number '" + token + "'");
            }
        }
    }

    // ------------------------------------------------------------------ writing

    /** Compact single-line output. */
    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, -1, 0);
        return sb.toString();
    }

    /** Indented output (two spaces) suitable for files people may open. */
    public static String pretty(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 2, 0);
        sb.append('\n');
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v, int indent, int level) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String str) {
            writeString(sb, str);
        } else if (v instanceof Boolean b) {
            sb.append(b ? "true" : "false");
        } else if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                throw new JsonException("NaN and Infinity cannot be written as JSON");
            }
            sb.append(d);
        } else if (v instanceof Number n) {
            sb.append(n.longValue());
        } else if (v instanceof Enum<?> e) {
            writeString(sb, e.name());
        } else if (v instanceof Map<?, ?> map) {
            writeObject(sb, map, indent, level);
        } else if (v instanceof Iterable<?> it) {
            writeArray(sb, it, indent, level);
        } else {
            throw new JsonException("Unsupported type: " + v.getClass().getName());
        }
    }

    private static void writeObject(StringBuilder sb, Map<?, ?> map, int indent, int level) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            newline(sb, indent, level + 1);
            writeString(sb, String.valueOf(e.getKey()));
            sb.append(indent >= 0 ? ": " : ":");
            write(sb, e.getValue(), indent, level + 1);
        }
        newline(sb, indent, level);
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, Iterable<?> items, int indent, int level) {
        var it = items.iterator();
        if (!it.hasNext()) {
            sb.append("[]");
            return;
        }
        sb.append('[');
        boolean first = true;
        while (it.hasNext()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            newline(sb, indent, level + 1);
            write(sb, it.next(), indent, level + 1);
        }
        newline(sb, indent, level);
        sb.append(']');
    }

    private static void newline(StringBuilder sb, int indent, int level) {
        if (indent < 0) {
            return;
        }
        sb.append('\n');
        sb.append(" ".repeat(indent * level));
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------ typed access

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object value) {
        if (value instanceof Map<?, ?>) {
            return (Map<String, Object>) value;
        }
        throw new JsonException("Expected a JSON object");
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object value) {
        if (value instanceof List<?>) {
            return (List<Object>) value;
        }
        throw new JsonException("Expected a JSON array");
    }

    public static String string(Map<String, Object> m, String key, String fallback) {
        Object v = m.get(key);
        return v instanceof String s ? s : fallback;
    }

    public static long longValue(Map<String, Object> m, String key, long fallback) {
        Object v = m.get(key);
        return v instanceof Number n ? n.longValue() : fallback;
    }

    public static int intValue(Map<String, Object> m, String key, int fallback) {
        Object v = m.get(key);
        if (v instanceof Number n) {
            long l = n.longValue();
            return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, l));
        }
        return fallback;
    }

    public static boolean bool(Map<String, Object> m, String key, boolean fallback) {
        Object v = m.get(key);
        return v instanceof Boolean b ? b : fallback;
    }
}
