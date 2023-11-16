package jdk.internal.foreign.mapper.component;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.RecordComponent;
import java.util.Objects;

import static jdk.internal.foreign.layout.MemoryLayoutUtil.requireNonNegative;


/**
 * Interface to model the resolving of get and set method handles.
 */
public sealed interface ComponentHandle<T>
        permits AbstractComponentHandle, GetComponentHandle, SetComponentHandle {

    MethodHandle handle(RecordComponent component);

    MethodHandle handle(ValueLayout vl,
                        RecordComponent component,
                        long byteOffset) throws NoSuchMethodException, IllegalAccessException;

    MethodHandle handle(GroupLayout gl,
                        RecordComponent component,
                        long byteOffset) throws NoSuchMethodException, IllegalAccessException;

    MethodHandle handle(SequenceLayout sl,
                        RecordComponent component,
                        long byteOffset) throws NoSuchMethodException, IllegalAccessException;

    static <T> ComponentHandle<T> ofGet(MethodHandles.Lookup lookup,
                                        Class<T> type,
                                        GroupLayout initialLayout,
                                        long offset) {
        Objects.requireNonNull(lookup);
        Objects.requireNonNull(type);
        Objects.requireNonNull(initialLayout);
        requireNonNegative(offset);
        return new GetComponentHandle<>(lookup, type, initialLayout, offset, 0);
    }

    static <T> ComponentHandle<T> ofSet(MethodHandles.Lookup lookup,
                                        Class<T> type,
                                        GroupLayout initialLayout,
                                        long offset) {
        Objects.requireNonNull(lookup);
        Objects.requireNonNull(type);
        Objects.requireNonNull(initialLayout);
        requireNonNegative(offset);
        return new SetComponentHandle<>(lookup, type, initialLayout, offset, 0);
    }

}
