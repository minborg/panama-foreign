package jdk.internal.include.netinet;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

import static jdk.internal.foreign.InternalMemoryLayout.*;
import static jdk.internal.foreign.LayoutTransformer.matching;

public final class Util {

    private Util() {
    }

    public static <T extends MemoryLayout> T networkOrder(T original) {
        return order(original, ByteOrder.BIG_ENDIAN);
    }

    @SuppressWarnings("unchecked")
    public static <T extends MemoryLayout> T order(T original, ByteOrder order) {
        return (T) transform(original, matching(ValueLayout.class, vl -> vl.withOrder(order)));
    }

}
