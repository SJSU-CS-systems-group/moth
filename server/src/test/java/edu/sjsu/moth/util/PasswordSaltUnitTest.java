package edu.sjsu.moth.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

public class PasswordSaltUnitTest {
    @Test
    public void testSalt() {
        IntStream.range(0, 100).parallel().forEach(i -> {
            final var testPassword = "test" + i;
            final var encoded = EmailCodeUtils.encodePassword(testPassword);
            Assertions.assertNotEquals(testPassword, encoded);
            Assertions.assertTrue(EmailCodeUtils.checkPassword(testPassword, encoded));
            Assertions.assertFalse(EmailCodeUtils.checkPassword("test", encoded));
            Assertions.assertFalse(EmailCodeUtils.checkPassword(testPassword, encoded.substring(1)));
            Assertions.assertFalse(
                    EmailCodeUtils.checkPassword(testPassword, encoded.substring(0, encoded.length() - 1)));
        });
    }
}
