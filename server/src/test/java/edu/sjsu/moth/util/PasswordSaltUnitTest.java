package edu.sjsu.moth.util;

import edu.sjsu.moth.server.util.Util;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

public class PasswordSaltUnitTest {
    @Test
    public void testSalt() {
        IntStream.range(0, 100).parallel().forEach(i -> {
            final var testPassword = "test" + i;
            final var encoded = Util.encodePassword(testPassword);
            Assertions.assertNotEquals(testPassword, encoded);
            Assertions.assertTrue(Util.checkPassword(testPassword, encoded));
            Assertions.assertFalse(Util.checkPassword("test", encoded));
            Assertions.assertFalse(Util.checkPassword(testPassword, encoded.substring(1)));
            Assertions.assertFalse(Util.checkPassword(testPassword, encoded.substring(0, encoded.length() - 1)));
        });
    }
}
