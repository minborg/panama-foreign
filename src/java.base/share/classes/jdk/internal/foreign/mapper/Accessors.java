package jdk.internal.foreign.mapper;

import jdk.internal.ValueBased;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.ValueLayout;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class is used to create MethodInfo objects (which hold extensive additional information
 * for a method) and to organize them in a way they can easily be retrieved.
 */
@ValueBased
final class Accessors {

    private final Map<MethodInfo.Key, List<MethodInfo>> map;

    private Accessors(Map<MethodInfo.Key, List<MethodInfo>> map) {
        this.map = map;
    }

    List<MethodInfo> getOrEmpty(MethodInfo.Key key) {
        return map.getOrDefault(key, Collections.emptyList());
    }

    Stream<MethodInfo> stream(Set<MethodInfo.Key> set) {
        return set.stream()
                .map(map::get)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream);
    }

    Stream<MethodInfo> stream(Predicate<MethodInfo.Key> condition) {
        return Arrays.stream(MethodInfo.Key.values())
                .filter(condition)
                .map(map::get)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream);
    }

    Stream<MethodInfo> stream() {
        return map.values().stream()
                .flatMap(Collection::stream);
    }

    static Accessors of(Class<?> type, GroupLayout layout) {
        return new Accessors(Arrays.stream(type.getMethods())
                .filter(m -> !Modifier.isStatic(m.getModifiers()))
                .filter(m -> !m.isDefault())
                .map(m -> methodInfo(type, layout, m))
                .collect(Collectors.groupingBy(MethodInfo::key)));
    }

    private static MethodInfo methodInfo(Class<?> type, GroupLayout layout, Method method) {

        MethodInfo.AccessorType accessorType = isGetter(method)
                ? MethodInfo.AccessorType.GETTER
                : MethodInfo.AccessorType.SETTER;

        Class<?> targetType = (accessorType == MethodInfo.AccessorType.GETTER)
                ? method.getReturnType()
                : getterType(method);

        MethodInfo.ValueType valueType = valueTypeFor(method, targetType);

        var elementPath = MemoryLayout.PathElement.groupElement(method.getName());
        MemoryLayout element;
        try {
            element = layout.select(elementPath);
        } catch (IllegalArgumentException iae) {
            throw new IllegalArgumentException("Unable to resolve '" + method + "' in " + layout, iae);
        }
        var offset = layout.byteOffset(elementPath);

        return switch (element) {
            case ValueLayout vl -> {
                if (!targetType.equals(vl.carrier())) {
                    throw new IllegalArgumentException("The type " + targetType + " for method " + method +
                            "does not match " + element);
                }
                yield new MethodInfo(MethodInfo.Key.of(MethodInfo.Cardinality.SCALAR, valueType, accessorType),
                        method, targetType, LayoutInfo.of(vl), offset);
            }
            case GroupLayout gl ->
                    new MethodInfo(MethodInfo.Key.of(MethodInfo.Cardinality.SCALAR, valueType, accessorType),
                            method, targetType, LayoutInfo.of(gl), offset);
            case SequenceLayout sl -> {
                MethodInfo info = new MethodInfo(MethodInfo.Key.of(MethodInfo.Cardinality.ARRAY, valueType, accessorType)
                        , method, targetType, LayoutInfo.of(sl), offset);
                int noDimensions = info.layoutInfo().arrayInfo().orElseThrow().dimensions().size();
                // The last parameter for a setter is the new value
                int expectedParameterIndexCount = method.getParameterCount() - (accessorType == MethodInfo.AccessorType.SETTER ? 1 : 0);
                if (expectedParameterIndexCount != noDimensions) {
                    throw new IllegalArgumentException(
                            "Sequence layout has a dimension of " + noDimensions +
                                    " and so, the method parameter count does not" +
                                    " match for: " + method);
                }
                yield info;
            }
            default -> throw new IllegalArgumentException("Cannot map " + element + " for " + type);
        };
    }

    private static MethodInfo.ValueType valueTypeFor(Method method, Class<?> targetType) {
        MethodInfo.ValueType valueType;
        if (targetType.isPrimitive() || targetType.equals(MemorySegment.class)) {
            valueType = MethodInfo.ValueType.VALUE;
        } else if (targetType.isInterface()) {
            valueType = MethodInfo.ValueType.INTERFACE;
        } else if (targetType.isRecord()) {
            valueType = MethodInfo.ValueType.RECORD;
        } else {
            throw new IllegalArgumentException("Type " + targetType + " is neither a primitive value, an interface nor a record: " + method);
        }
        return valueType;
    }

    private static Class<?> getterType(Method method) {
        if (method.getParameterCount() == 0) {
            throw new IllegalArgumentException("A setter must take at least one argument: " + method);
        }
        return method.getParameterTypes()[method.getParameterCount() - 1];
    }

    private static boolean isGetter(Method method) {
        return method.getReturnType() != void.class;
    }

}
