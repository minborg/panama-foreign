package jdk.internal.foreign.mapper.component;

import jdk.internal.util.ArraysSupport;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.SequenceLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

record MultidimensionalSequenceLayoutInfo(List<SequenceLayout> sequences,
                                          MemoryLayout elementLayout,
                                          Class<?> type) {

    int[] dimensions() {
        return sequences().stream()
                .mapToLong(SequenceLayout::elementCount)
                .mapToInt(Math::toIntExact)
                .toArray();
    }

    int firstDimension() {
        return (int) sequences().getFirst().elementCount();
    }

    int lastDimension() {
        return (int) sequences().getLast().elementCount();
    }

    long layoutByteSize() {
        return sequences()
                .getFirst()
                .byteSize();
    }

    MultidimensionalSequenceLayoutInfo removeFirst() {
        var removed = new ArrayList<>(sequences);
        removed.removeFirst();
        return new MultidimensionalSequenceLayoutInfo(removed, elementLayout, type);
    }

    static MultidimensionalSequenceLayoutInfo of(SequenceLayout sequenceLayout,
                                                 Class<?> arrayComponent) {
        MemoryLayout current = sequenceLayout;
        List<SequenceLayout> sequences = new ArrayList<>();
        while (true) {
            if (current instanceof SequenceLayout element) {
                long count = element.elementCount();
                if (count > ArraysSupport.SOFT_MAX_ARRAY_LENGTH) {
                    throw new IllegalArgumentException("Unable to accommodate '" + element + "' in an array.");
                }
                current = element.elementLayout();
                sequences.add(element);
            } else {
                return new MultidimensionalSequenceLayoutInfo(
                        List.copyOf(sequences), current, deepArrayComponentType(arrayComponent));
            }
        }
    }

    private static Class<?> deepArrayComponentType(Class<?> arrayType) {
        Class<?> recordComponentType = arrayType;
        while (recordComponentType.isArray()) {
            recordComponentType = Objects.requireNonNull(recordComponentType.componentType());
        }
        return recordComponentType;
    }

}