package jdk.internal.foreign;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;

/**
 * Temporary internal class for holding useful methods that could later be
 * exposed in some public class like java.util.Arrays.
 */
public final class InternalArrays {


    private InternalArrays() {
    }

    public static long hashCode(MemorySegment segment) {
        return hashCode(segment, 0, segment.byteSize());
    }

    public static long hashCode(MemorySegment segment, long offset, long bytes) {
        Objects.requireNonNull(segment);
        if (offset < 0) {
            throw new IllegalArgumentException("offset negative");
        }
        if (bytes < 0) {
            throw new IllegalArgumentException("bytes negative");
        }
        if (offset+bytes > segment.byteSize()) {
            throw new IllegalArgumentException("offset + bytes > segment size (" + segment.byteSize() + ")");
        }
        // Todo: Vectorize this
        long h = 7;
        for (long i = offset; i < bytes; i++) {
            h = 31 * h + segment.get(JAVA_BYTE, i);
        }
        return h;
    }

    public static int longHashCodeAsInt(long hashCode) {
        return Long.hashCode(hashCode);
    }

}
