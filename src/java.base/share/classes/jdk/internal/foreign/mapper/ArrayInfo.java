package jdk.internal.foreign.mapper;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.SequenceLayout;
import java.util.ArrayList;
import java.util.List;

record ArrayInfo(MemoryLayout elementLayout,
                 List<Long> dimensions) {

    static ArrayInfo of(SequenceLayout layout) {
        return recurse(new ArrayInfo(layout, new ArrayList<>()));
    }

    private static ArrayInfo recurse(ArrayInfo info) {
        if (!(info.elementLayout instanceof SequenceLayout sl)) {
            // We are done. Create an immutable record
            return new ArrayInfo(info.elementLayout(), List.copyOf(info.dimensions()));
        }
        info.dimensions().add(sl.elementCount());
        return recurse(new ArrayInfo(sl.elementLayout(), info.dimensions()));
    }

}
