/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.internal.foreign.mapper;

import jdk.internal.foreign.mapper.accessor.AccessorInfo;
import jdk.internal.foreign.mapper.accessor.Accessors;
import jdk.internal.vm.annotation.ForceInline;
import sun.security.action.GetPropertyAction;

import java.lang.constant.ClassDesc;
import java.lang.foreign.CompoundAccessor;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class MapperUtil {

    private MapperUtil() {
    }

    private static final MethodHandles.Lookup LOCAL_LOOKUP = MethodHandles.lookup();

    private static final String DEBUG =
            GetPropertyAction.privilegedGetProperty("java.lang.foreign.mapper.debug", "");

    public static boolean isDebug() {
        return !DEBUG.isEmpty();
    }

    public static <T extends Record> Class<T> requireRecordType(Class<?> type) {
        Objects.requireNonNull(type);
        if (!type.isRecord()) {
            throw newIae(type, "is not a Record");
        }
        if (type.equals(Record.class)) {
            throw newIae(type, "not a real Record");
        }
        assertNotDeclaringTypeParameters(type);
        @SuppressWarnings("unchecked")
        Class<T> result = (Class<T>) type;
        return result;
    }

    private static void assertNotDeclaringTypeParameters(Class<?> type) {
        if (type.getTypeParameters().length != 0) {
            throw newIae(type, "directly declaring type parameters: " + type.toGenericString());
        }
    }

    static IllegalArgumentException newIae(Class<?> type, String trailingInfo) {
        return new IllegalArgumentException(type.getName() + " is " + trailingInfo);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Record> Class<T> castToRecordClass(Class<?> clazz) {
        return (Class<T>) clazz;
    }

    public static ClassDesc desc(Class<?> clazz) {
        return clazz.describeConstable()
                .orElseThrow();
    }

    static void assertMappingsCorrectAndTotal(Class<?> type,
                                              GroupLayout layout,
                                              Accessors accessors) {

        var nameMappingCounts = layout.memberLayouts().stream()
                .map(MemoryLayout::name)
                .flatMap(Optional::stream)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        List<AccessorInfo> allMethods = accessors.stream().toList();

        // Make sure we have all components distinctly mapped
        for (AccessorInfo component : allMethods) {
            String name = component.method().getName();
            switch (nameMappingCounts.getOrDefault(name, 0L).intValue()) {
                case 0 -> throw new IllegalArgumentException("No mapping for " +
                        type.getName() + "." + name +
                        " in layout " + layout);
                case 1 -> { /* Happy path */ }
                default -> throw new IllegalArgumentException("Duplicate mappings for " +
                        type.getName() + "." + name +
                        " in layout " + layout);
            }
        }

        // Make sure all methods of the type are mapped (totality)
        Set<Method> accessorMethods = allMethods.stream()
                .map(AccessorInfo::method)
                .collect(Collectors.toSet());

        var typeMethods = Arrays.stream(type.getRecordComponents())
                    .map(RecordComponent::getAccessor)
                    .toList();

        var missing = typeMethods.stream()
                .filter(Predicate.not(accessorMethods::contains))
                .toList();

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Unable to map methods: " + missing);
        }

    }

 //   /**
 //    * {@return a new instance of type T projected at the provided
 //    *          external {@code segment} at offset zero}
 //    * <p>
 //    * Calling this method is equivalent to the following code:
 //    * {@snippet lang = java:
 //    *    get(segment, 0L);
 //    * }
 //    *
 //    * @param segment the external segment to be projected to the new instance
 //    * @throws IllegalStateException if the {@linkplain MemorySegment#scope() scope}
 //    *         associated with the provided segment is not
 //    *         {@linkplain MemorySegment.Scope#isAlive() alive}
 //    * @throws WrongThreadException if this method is called from a thread {@code T},
 //    *         such that {@code isAccessibleBy(T) == false}
 //    * @throws IllegalArgumentException if the access operation is
 //    *         <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a>
 //    *         of the {@link CompoundAccessor#layout()}
 //    * @throws IndexOutOfBoundsException if
 //    *         {@code layout().byteSize() > segment.byteSize()}
 //    */
 //   @ForceInline
 //   public static <T> T get(MemorySegment segment, CompoundAccessor<T> accessor) {
 //       return get(segment, accessor, 0L);
 //   }

    /**
     * {@return a new instance of type T projected at the provided external
     *          {@code segment} at the given {@code index} scaled by the
     *          {@code layout().byteSize()}}
     * <p>
     * Calling this method is equivalent to the following code:
     * {@snippet lang = java:
     *    get(segment, layout().byteSize() * index);
     * }
     *
     * @param segment the external segment to be projected to the new instance
     * @param index a logical index, the offset in bytes (relative to the provided
     *              segment address) at which the access operation will occur can
     *              be expressed as {@code (index * layout().byteSize())}
     * @throws IllegalStateException if the {@linkplain MemorySegment#scope() scope}
     *         associated with the provided segment is not
     *         {@linkplain MemorySegment.Scope#isAlive() alive}
     * @throws WrongThreadException if this method is called from a thread {@code T},
     *         such that {@code isAccessibleBy(T) == false}
     * @throws IllegalArgumentException if the access operation is
     *         <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a>
     *         of the {@link CompoundAccessor#layout()}
     * @throws IndexOutOfBoundsException if {@code index * layout().byteSize()} overflows
     * @throws IndexOutOfBoundsException if
     *         {@code index * layout().byteSize() > segment.byteSize() - layout.byteSize()}
     */
    @ForceInline
    public static <T> T getAtIndex(MemorySegment segment, CompoundAccessor<T> accessor, long index) {
        return get(segment, accessor, accessor.layout().byteSize() * index);
    }

    /**
     * {@return a new instance of type T projected from at provided
     *          external {@code segment} at the provided {@code offset}}
     *
     * @param segment the external segment to be projected at the new instance
     * @param offset  from where in the segment to project the new instance
     * @throws IllegalStateException if the {@linkplain MemorySegment#scope() scope}
     *         associated with the provided segment is not
     *         {@linkplain MemorySegment.Scope#isAlive() alive}
     * @throws WrongThreadException if this method is called from a thread {@code T},
     *         such that {@code isAccessibleBy(T) == false}
     * @throws IllegalArgumentException if the access operation is
     *         <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a>
     *         of the {@link CompoundAccessor#layout()}
     * @throws IndexOutOfBoundsException if
     *         {@code offset > segment.byteSize() - layout().byteSize()}
     */
    @SuppressWarnings("unchecked")
    @ForceInline
    public static <T> T get(MemorySegment segment, CompoundAccessor<T> accessor, long offset) {
        try {
            return (T) accessor.getter()
                    .invokeExact(segment, offset);
        } catch (NullPointerException |
                 IndexOutOfBoundsException |
                 WrongThreadException |
                 IllegalStateException |
                 IllegalArgumentException rethrow) {
            throw rethrow;
        } catch (Throwable e) {
            throw new RuntimeException("Unable to invoke getHandle() with " +
                    "segment="  + segment +
                    ", offset=" + offset, e);
        }
    }

//    /**
//     * Writes the provided instance {@code t} of type T into the provided {@code segment}
//     * at offset zero.
//     * <p>
//     * Calling this method is equivalent to the following code:
//     * {@snippet lang = java:
//     *    set(segment, 0L, t);
//     * }
//     *
//     * @param segment in which to write the provided {@code t}
//     * @param t instance to write into the provided segment
//     * @throws IllegalStateException if the {@linkplain MemorySegment#scope() scope}
//     *         associated with this segment is not
//     *         {@linkplain MemorySegment.Scope#isAlive() alive}
//     * @throws WrongThreadException if this method is called from a thread {@code T},
//     *         such that {@code isAccessibleBy(T) == false}
//     * @throws IllegalArgumentException if the access operation is
//     *         <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a>
//     *         of the {@link CompoundAccessor#layout()}
//     * @throws IndexOutOfBoundsException if {@code layout().byteSize() > segment.byteSize()}
//     * @throws UnsupportedOperationException if this segment is
//     *         {@linkplain MemorySegment#isReadOnly() read-only}
//     * @throws UnsupportedOperationException if {@code value} is not a
//     *         {@linkplain MemorySegment#isNative() native} segment
//     * @throws IllegalArgumentException if an array length does not correspond to the
//     *         {@linkplain SequenceLayout#elementCount() element count} of a sequence layout
//     * @throws NullPointerException if a required parameter is {@code null}
//     */
//    @ForceInline
//    public static <T> void set(MemorySegment segment, CompoundAccessor<T> accessor, T t) {
//        set(segment, accessor, 0L, t);
//    }

    /**
     * Writes the provided {@code t} instance of type T into the provided {@code segment}
     * at the provided {@code index} scaled by the {@code layout().byteSize()}}.
     * <p>
     * Calling this method is equivalent to the following code:
     * {@snippet lang = java:
     *    set(segment, layout().byteSize() * index, t);
     * }
     * @param segment in which to write the provided {@code t}
     * @param index a logical index, the offset in bytes (relative to the provided
     *              segment address) at which the access operation will occur can be
     *              expressed as {@code (index * layout().byteSize())}
     * @param t instance to write into the provided segment
     * @throws IllegalStateException if the {@linkplain MemorySegment#scope() scope}
     *         associated with this segment is not
     *         {@linkplain MemorySegment.Scope#isAlive() alive}
     * @throws WrongThreadException if this method is called from a thread {@code T},
     *         such that {@code isAccessibleBy(T) == false}
     * @throws IllegalArgumentException if the access operation is
     *         <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a>
     *         of the {@link CompoundAccessor#layout()}
     * @throws IndexOutOfBoundsException if {@code offset > segment.byteSize() - layout.byteSize()}
     * @throws UnsupportedOperationException if this segment is
     *         {@linkplain MemorySegment#isReadOnly() read-only}
     * @throws UnsupportedOperationException if {@code value} is not a
     *         {@linkplain MemorySegment#isNative() native} segment
     * @throws IllegalArgumentException if an array length does not correspond to the
     *         {@linkplain SequenceLayout#elementCount() element count} of a sequence layout
     * @throws NullPointerException if a required parameter is {@code null}
     */
    @ForceInline
    public static <T> void setAtIndex(MemorySegment segment, CompoundAccessor<T> accessor, long index, T t) {
        set(segment, accessor, accessor.layout().byteSize() * index, t);
    }

    /**
     * Writes the provided instance {@code t} of type T into the provided {@code segment}
     * at the provided {@code offset}.
     *
     * @param segment in which to write the provided {@code t}
     * @param offset offset in bytes (relative to the provided segment address) at which
     *               this access operation will occur
     * @param t instance to write into the provided segment
     * @throws IllegalStateException if the {@linkplain MemorySegment#scope() scope}
     *         associated with this segment is not
     *         {@linkplain MemorySegment.Scope#isAlive() alive}
     * @throws WrongThreadException if this method is called from a thread {@code T},
     *         such that {@code isAccessibleBy(T) == false}
     * @throws IllegalArgumentException if the access operation is
     *         <a href="MemorySegment.html#segment-alignment">incompatible with the alignment constraint</a>
     *         of the {@link CompoundAccessor#layout()}
     * @throws IndexOutOfBoundsException if {@code offset > segment.byteSize() - layout.byteSize()}
     * @throws UnsupportedOperationException if
     *         this segment is {@linkplain MemorySegment#isReadOnly() read-only}
     * @throws UnsupportedOperationException if
     *         {@code value} is not a {@linkplain MemorySegment#isNative() native} segment // Todo: only for pointers
     * @throws IllegalArgumentException if an array length does not correspond to the
     *         {@linkplain SequenceLayout#elementCount() element count} of a sequence layout
     * @throws NullPointerException if a required parameter is {@code null}
     */
    @ForceInline
    public static <T> void set(MemorySegment segment, CompoundAccessor<T> accessor, long offset, T t) {
        try {
            accessor.setter()
                    .invokeExact(segment, offset, (Object) t);
        } catch (IndexOutOfBoundsException |
                 WrongThreadException |
                 IllegalStateException |
                 IllegalArgumentException |
                 UnsupportedOperationException |
                 NullPointerException rethrow) {
            throw rethrow;
        } catch (Throwable e) {
            throw new RuntimeException("Unable to invoke setHandle() with " +
                    "segment=" + segment +
                    ", offset=" + offset +
                    ", t=" + t, e);
        }
    }

    public static <T, R> CompoundAccessor<R> map(CompoundAccessor<T> original,
                                                 Class<R> newType,
                                                 Function<? super T, ? extends R> toMapper,
                                                 Function<? super R, ? extends T> fromMapper) {
        MethodHandle mapToHandle = findVirtual("mapTo");
        mapToHandle = MethodHandles.insertArguments(mapToHandle, 0, toMapper);
        MethodHandle getter = MethodHandles.filterReturnValue(original.getter(), mapToHandle);
        MethodHandle mapFromHandle = findVirtual("mapFrom");
        mapFromHandle = MethodHandles.insertArguments(mapFromHandle, 0, fromMapper);
        MethodHandle setter = MethodHandles.filterArguments(original.setter(), 2, mapFromHandle);
        return new CompoundAccessor<>(original.layout(), newType, getter, setter);
    }

    // Used reflective when obtaining a MethodHandle
    static <T, R> R mapTo(Function<? super T, ? extends R> toMapper, T t) {
        return toMapper.apply(t);
    }

    // Used reflective when obtaining a MethodHandle
    static <T, R> T mapFrom(Function<? super R, ? extends T> fromMapper, R r) {
        return fromMapper.apply(r);
    }

    private static MethodHandle findVirtual(String name) {
        try {
            var mt = MethodType.methodType(Object.class, Function.class, Object.class);
            return LOCAL_LOOKUP.findStatic(MapperUtil.class, name, mt);
        } catch (ReflectiveOperationException e) {
            // Should not happen
            throw new InternalError(e);
        }
    }

}
