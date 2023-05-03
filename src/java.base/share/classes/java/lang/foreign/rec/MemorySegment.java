package java.lang.foreign.rec;

import java.util.stream.Stream;

/**
 * MS
 */
public interface MemorySegment {

    /**
     * {@return i}
     * @param layout l
     * @param offset o
     */
    int get(JavaDouble layout, long offset);

    /**
     * A
     * @param layout l
     * @param offset o
     * @param value v
     */
    void get(JavaDouble layout, long offset, int value);

    /**
     * {@return t}
     * @param layout l
     * @param offset o
     * @param <T> layout type
     */
    <T> T get(Class<T> layout, long offset);

    /**
     * {@return s}
     * @param layout l
     * @param <T> layout type
     */
    <T> Stream<T> stream(Class<T> layout);

    /**
     * {@return ms}
     * @param array to use
     */
    static MemorySegment ofArray(double[] array) {
        return null;
    }

}
