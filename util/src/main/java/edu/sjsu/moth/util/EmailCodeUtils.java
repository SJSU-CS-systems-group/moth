package edu.sjsu.moth.util;

import lombok.extern.apachecommons.CommonsLog;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

@CommonsLog
public class EmailCodeUtils {

    public static final SimpleDateFormat jsonDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    public static DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    /* prepend 13 random bytes and append "frog" to the end. iterate 10,000 times (to make it slower) using SHA256*/
    public final static Pbkdf2PasswordEncoder PASSWORD_ENCODER = new Pbkdf2PasswordEncoder("frog", 13, 10_000,
                                                                                           Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
    // defining a new epoch gets a few decades of bits back!
    public static long NEW_EPOCH =
            new Calendar.Builder().setDate(2023, 1, 1).setTimeZone(TimeZone.getTimeZone("UTC")).build()
                    .getTimeInMillis();

    static {
        PASSWORD_ENCODER.setEncodeHashAsBase64(true);
    }

    public static String now() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));
        return now.format(dateFormatter);
    }

    /**
     * return a salted and hashed password
     */
    public static String encodePassword(String password) {
        return PASSWORD_ENCODER.encode(password);
    }

    /**
     * check a password against a salted and hashed password
     */
    public static boolean checkPassword(String password, String encodedPassword) {
        try {
            return PASSWORD_ENCODER.matches(password, encodedPassword);
        } catch (Exception ignore) {
            // strange exceptions can be thrown if the encoded password is messed up
            return false;
        }
    }

    static public String normalizeEmail(String email) {
        var parts = email.split("@");
        var user = parts[0].toLowerCase();
        var host = parts[1].toLowerCase();
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        user = user.replace(".", "");
        return user + '@' + host;
    }

    public static String epoch() {return jsonDateFormat.format(new Date(0));}

    /**
     * Uniquifier is in its own class since we are sychronizing on the class, so we want to isolate the
     * synchronization to just this logic.
     */
    public static class Uniquifier {
        // this is a counter that will increment to generate unique ids. note, we are assuming that there will be
        // less than
        // 256 unique ids generated per second
        private static int uniquifier = 0;
        private static long lastTime = 0;

        public static synchronized long generateId() {
            var time = (System.currentTimeMillis() - NEW_EPOCH) << 16;
            // we are going to reserve the lower 8 bits for parallel servers
            var id = time + uniquifier << 8;
            uniquifier = time != lastTime || uniquifier == 256 ? 0 : uniquifier + 1;
            lastTime = time;
            return id;
        }

    }
}
