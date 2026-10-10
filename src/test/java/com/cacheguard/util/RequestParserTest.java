package com.cacheguard.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RequestParserTest {

    @Test
    void acceptsCommasSpacesAndSemicolons() {
        assertArrayEquals(new int[] {1, 2, 3, 4, 1, 2, 5}, RequestParser.parse("1, 2, 3, 4, 1, 2, 5"));
        assertArrayEquals(new int[] {1, 2, 3}, RequestParser.parse(" 1;2   3 ,, "));
        assertArrayEquals(new int[] {-4, 0, 12}, RequestParser.parse("-4\n0\t12"));
    }

    @Test
    void rejectsEmptyAndBlank() {
        assertThrows(IllegalArgumentException.class, () -> RequestParser.parse(null));
        assertThrows(IllegalArgumentException.class, () -> RequestParser.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> RequestParser.parse(" , ; "));
    }

    @Test
    void explainsWhichItemIsWrong() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> RequestParser.parse("1, 2, abc, 4"));
        assertTrue(e.getMessage().contains("'abc'"), e.getMessage());
        assertTrue(e.getMessage().contains("item 3"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> RequestParser.parse("1, 2.5"));
        assertThrows(IllegalArgumentException.class, () -> RequestParser.parse("99999999999"));
    }

    @Test
    void parsesLargeInputs() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200_000; i++) {
            sb.append(i % 97).append(i % 2 == 0 ? ", " : " ");
        }
        int[] r = RequestParser.parse(sb.toString());
        assertEquals(200_000, r.length);
        assertEquals(199_999 % 97, r[199_999]);
    }

    @Test
    void formatTruncates() {
        assertEquals("1, 2, 3", RequestParser.format(new int[] {1, 2, 3}, 10));
        assertEquals("1, 2, ... (3 total)", RequestParser.format(new int[] {1, 2, 3}, 2));
    }
}
