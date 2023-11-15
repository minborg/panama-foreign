package jdk.internal.foreign.mapper.component;

import jdk.internal.foreign.mapper.SegmentRecordMapper;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.RecordComponent;

abstract sealed class AbstractComponentHandle<T>
        implements ComponentHandle<T>
        permits GetComponentHandle, SetComponentHandle {

    final MethodHandles.Lookup lookup;
    final Class<T> type;
    final GroupLayout layout;
    final long offset;
    final int depth;

    AbstractComponentHandle(MethodHandles.Lookup lookup,
                            Class<T> type,
                            GroupLayout layout,
                            long offset,
                            final int depth) {
        this.lookup = lookup;
        this.type = type;
        this.layout = layout;
        this.offset = offset;
        this.depth = depth;
    }

    @Override
    public MethodHandle handle(RecordComponent component) {
        var pathElement = MemoryLayout.PathElement.groupElement(component.getName());
        var componentLayout = layout.select(pathElement);
        var byteOffset = layout.byteOffset(pathElement) + offset;
        try {
            return switch (componentLayout) {
                case ValueLayout vl    -> handle(vl, component, byteOffset);
                case GroupLayout gl    -> handle(gl, component, byteOffset);
                case SequenceLayout sl -> handle(sl, component, byteOffset);
                case PaddingLayout _   -> throw fail(component, componentLayout);
            };
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new InternalError(e);
        }
    }

    void assertTypesMatch(RecordComponent component,
                          Class<?> recordComponentType,
                          ValueLayout vl) {

        if (!(recordComponentType == vl.carrier())) {
            throw new IllegalArgumentException("Unable to match types because the component '" +
                    component.getName() + "' (in " + type.getName() + ") has the type of '" + component.getType() +
                    "' but the layout carrier is '" + vl.carrier() + "' (in " + layout + ")");
        }
    }

    IllegalArgumentException fail(RecordComponent component,
                                          MemoryLayout layout) {
        throw new IllegalArgumentException(
                "Unable to map " + layout + " to " + type.getName() + "." + component.getName());
    }

    static Class<? extends ValueLayout> topValueLayoutType(ValueLayout vl) {
        // All the permitted implementations OfXImpl of the ValueLayout interfaces declare
        // its main top interface OfX as the sole interface (e.g. OfIntImpl implements only OfInt directly)
        return vl.getClass().getInterfaces()[0].asSubclass(ValueLayout.class);
    }

    <R> SegmentRecordMapper<R> recordMapper(Class<R> componentType,
                                            GroupLayout gl,
                                            long byteOffset) {

        return new SegmentRecordMapper<>(lookup, componentType, gl, byteOffset, depth + 1);
    }

}
