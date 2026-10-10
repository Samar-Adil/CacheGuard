package com.cacheguard.util;

import java.util.Arrays;

/**
 * Turns text such as {@code "1, 2, 3, 4, 1, 2, 5"} into an array of block numbers.
 * Commas, semicolons and whitespace all separate items.
 */
public final class RequestParser {

    public static final int MAX_REQUESTS = 5_000_000;

    private RequestParser() {
    }

    /**
     * @throws IllegalArgumentException with a message fit to show to the user
     */
    public static int[] parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Enter at least one request, for example: 1, 2, 3, 4, 1, 2, 5");
        }
        int[] out = new int[Math.min(1024, MAX_REQUESTS)];
        int count = 0;
        int n = text.length();
        int i = 0;
        while (i < n) {
            while (i < n && isSeparator(text.charAt(i))) {
                i++;
            }
            if (i >= n) {
                break;
            }
            int start = i;
            while (i < n && !isSeparator(text.charAt(i))) {
                i++;
            }
            if (count == MAX_REQUESTS) {
                throw new IllegalArgumentException(
                        "Too many requests: the limit is " + String.format("%,d", MAX_REQUESTS));
            }
            if (count == out.length) {
                out = Arrays.copyOf(out, (int) Math.min((long) out.length * 2, MAX_REQUESTS));
            }
            out[count++] = parseToken(text, start, i, count);
        }
        if (count == 0) {
            throw new IllegalArgumentException("Enter at least one request, for example: 1, 2, 3, 4, 1, 2, 5");
        }
        return count == out.length ? out : Arrays.copyOf(out, count);
    }

    private static boolean isSeparator(char c) {
        return c == ',' || c == ';' || Character.isWhitespace(c);
    }

    private static int parseToken(String text, int start, int end, int position) {
        try {
            return Integer.parseInt(text, start, end, 10);
        } catch (NumberFormatException e) {
            String token = text.substring(start, Math.min(end, start + 20));
            throw new IllegalArgumentException(
                    "'" + token + "' (item " + position + ") is not a whole number. "
                            + "Use numbers separated by commas, e.g. 1, 2, 3");
        }
    }

    /** Formats at most {@code limit} items for display, adding an ellipsis when truncated. */
    public static String format(int[] requests, int limit) {
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(requests.length, Math.max(1, limit));
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(requests[i]);
        }
        if (shown < requests.length) {
            sb.append(", ... (").append(String.format("%,d", requests.length)).append(" total)");
        }
        return sb.toString();
    }
}
