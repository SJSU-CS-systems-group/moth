package edu.sjsu.moth.util;

import edu.sjsu.moth.server.util.Util;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UtilTest {
    @Test
    public void testEscapeRegexMatchesLiterally() {
        var input = "a.b(c";
        var escaped = Util.escapeRegex(input);
        assertTrue("a.b(c".matches(".*" + escaped + ".*"));
        assertFalse("aXb(c".matches(".*" + escaped + ".*"));
    }

    @Test
    public void testEscapeRegexProducesValidRegex() {
        // these would throw PatternSyntaxException (or match incorrectly) unescaped
        for (var input : new String[] { "((((", "a)b", "[z-a]", "x{1,", "\\", "a|b", "^$.*+?<>" }) {
            var escaped = Util.escapeRegex(input);
            assertTrue(input.matches(escaped), "escaped pattern should match its own input: " + input);
        }
    }

    @Test
    public void testEscapeRegexNull() {
        assertEquals("", Util.escapeRegex(null));
    }

    @Test
    public void testClamp() {
        assertEquals(1, Util.clamp(-5, 1, 40));
        assertEquals(40, Util.clamp(999999999, 1, 40));
        assertEquals(20, Util.clamp(20, 1, 40));
        assertEquals(1, Util.clamp(1, 1, 40));
        assertEquals(40, Util.clamp(40, 1, 40));
    }

    @Test
    public void testGeneratePasswordSmoke() {
        var seen = new HashSet<String>();
        for (int i = 0; i < 32; i++) {
            var p = Util.generatePassword();
            assertFalse(p.isEmpty());
            seen.add(p);
        }
        // 32 distinct 6-byte random passwords; any collision indicates broken randomness
        assertEquals(32, seen.size());
    }
}
