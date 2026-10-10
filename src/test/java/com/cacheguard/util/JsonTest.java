package com.cacheguard.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void parsesAllValueTypes() {
        Map<String, Object> m = Json.asObject(Json.parse(
                "{\"s\":\"hi\",\"i\":42,\"d\":-1.5e2,\"t\":true,\"f\":false,\"n\":null,\"a\":[1,2,[3]],\"o\":{}}"));
        assertEquals("hi", m.get("s"));
        assertEquals(42L, m.get("i"));
        assertEquals(-150.0, m.get("d"));
        assertEquals(true, m.get("t"));
        assertEquals(false, m.get("f"));
        assertNull(m.get("n"));
        assertTrue(m.containsKey("n"));
        assertEquals(List.of(1L, 2L, List.of(3L)), m.get("a"));
        assertTrue(Json.asObject(m.get("o")).isEmpty());
    }

    @Test
    void handlesEscapesAndUnicode() {
        assertEquals("a\"b\\c/d\n\t\u00e9\u4e2d", Json.parse("\"a\\\"b\\\\c\\/d\\n\\t\\u00e9\\u4e2d\""));
        assertEquals("\u00e9\u4e2d \uD83D\uDE00", Json.parse("\"\u00e9\u4e2d \uD83D\uDE00\""));
    }

    @Test
    void roundTripsThroughCompactAndPretty() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("text", "line1\nline2 \"quoted\" \\ \u0001");
        m.put("n", 7);
        m.put("big", Long.MAX_VALUE);
        m.put("d", 0.25);
        m.put("list", List.of(1, "x", true));
        m.put("empty", List.of());
        m.put("nothing", null);
        Object expected = Json.parse(Json.stringify(m));
        assertEquals(expected, Json.parse(Json.pretty(m)));
        assertEquals("line1\nline2 \"quoted\" \\ \u0001", Json.asObject(expected).get("text"));
        assertEquals(Long.MAX_VALUE, Json.asObject(expected).get("big"));
    }

    @Test
    void preservesKeyOrder() {
        Map<String, Object> m = Json.asObject(Json.parse("{\"z\":1,\"a\":2,\"m\":3}"));
        assertEquals(List.of("z", "a", "m"), List.copyOf(m.keySet()));
    }

    @Test
    void rejectsMalformedInputWithPosition() {
        String[] bad = {"", "{", "[1,]", "{\"a\":}", "{a:1}", "\"unterminated", "01x", "[1 2]", "{} extra",
            "\"bad\\q\"", "tru", "\"\\u12\""};
        for (String s : bad) {
            assertThrows(Json.JsonException.class, () -> Json.parse(s), "should reject: " + s);
        }
        Json.JsonException e = assertThrows(Json.JsonException.class, () -> Json.parse("{\n  \"a\": ?\n}"));
        assertTrue(e.getMessage().contains("line 2"), e.getMessage());
    }

    @Test
    void rejectsExcessiveNesting() {
        String deep = "[".repeat(500) + "]".repeat(500);
        assertThrows(Json.JsonException.class, () -> Json.parse(deep));
    }

    @Test
    void refusesToWriteNonFiniteNumbersAndUnknownTypes() {
        assertThrows(Json.JsonException.class, () -> Json.stringify(Double.NaN));
        assertThrows(Json.JsonException.class, () -> Json.stringify(new Object()));
    }

    @Test
    void skipsByteOrderMark() {
        assertEquals(1L, Json.parse("\uFEFF1"));
    }

    @Test
    void typedAccessorsFallBack() {
        Map<String, Object> m = Json.asObject(Json.parse("{\"n\":5,\"s\":\"x\",\"b\":true,\"huge\":99999999999}"));
        assertEquals(5, Json.intValue(m, "n", 0));
        assertEquals(7, Json.intValue(m, "s", 7));
        assertEquals(Integer.MAX_VALUE, Json.intValue(m, "huge", 0));
        assertEquals("fallback", Json.string(m, "n", "fallback"));
        assertEquals(true, Json.bool(m, "b", false));
        assertThrows(Json.JsonException.class, () -> Json.asArray(m));
    }
}
