import java.lang.foreign.MemorySegment;
import java.util.HexFormat;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.fail;

public final class MapperTestUtil {

    private MapperTestUtil() {}

    public static int[] segmentOf(int... ints) {
        return ints;
    }

    public static void assertContentEquals(int[] expected, MemorySegment actual) {
        assertContentEquals(MemorySegment.ofArray(expected), actual);
    }

    public static void assertContentEquals(MemorySegment expected, MemorySegment actual) {
        if (expected.mismatch(actual) != -1) {
            HexFormat hexFormat = HexFormat.ofDelimiter(" ");
            fail("Expected '" + hexFormat.formatHex(expected.toArray(JAVA_BYTE)) +
                    "' but got '" + hexFormat.formatHex(actual.toArray(JAVA_BYTE))+"'");
        }
    }

}
