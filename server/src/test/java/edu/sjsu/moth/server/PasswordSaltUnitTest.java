package edu.sjsu.moth.server;

import org.junit.Assert;
import org.junit.Test;

import java.util.stream.IntStream;

public class PasswordSaltUnitTest {
    @Test
    public void testSalt() {
        IntStream.range(0, 100).parallel().forEach(i -> {
            final var testPassword = "test" + i;
            final var encoded = Util.encodePassword(testPassword);
            Assert.assertNotEquals(testPassword, encoded);
            Assert.assertTrue(Util.checkPassword(testPassword, encoded));
            Assert.assertFalse(Util.checkPassword("test", encoded));
            Assert.assertFalse(Util.checkPassword(testPassword, encoded.substring(1)));
            Assert.assertFalse(Util.checkPassword(testPassword, encoded.substring(0, encoded.length() - 1)));
        });
    }
}
