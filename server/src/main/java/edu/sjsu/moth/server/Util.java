package edu.sjsu.moth.server;

import java.util.Enumeration;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * simple utility methods
 */
public class Util {
    /**
     * super gross code to convert and enumeration to a stream. (should be built into java!)
     *
     * @param en  the enumeration to stream
     * @param <T> the type of objects being streamed
     * @return a stream of the enumeration
     */
    public static <T> Stream<T> enumerationToStream(Enumeration<T> en) {
        return StreamSupport.stream(Spliterators.spliterator(en.asIterator(), Long.MAX_VALUE,
                Spliterator.IMMUTABLE | Spliterator.NONNULL), false);
    }
}