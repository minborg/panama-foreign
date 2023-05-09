/*
 *  Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License version 2 only, as
 *  published by the Free Software Foundation.  Oracle designates this
 *  particular file as subject to the "Classpath" exception as provided
 *  by Oracle in the LICENSE file that accompanied this code.
 *
 *  This code is distributed in the hope that it will be useful, but WITHOUT
 *  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 *  FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  version 2 for more details (a copy is included in the LICENSE file that
 *  accompanied this code).
 *
 *  You should have received a copy of the GNU General Public License version
 *  2 along with this work; if not, write to the Free Software Foundation,
 *  Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *   Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 *  or visit www.oracle.com if you need additional information or have any
 *  questions.
 *
 */
package jdk.internal.foreign;

import jdk.internal.foreign.layout.ValueLayouts;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public final class MemorySegmentMappers {

    private static final Map<Class<?>, MemorySegment.Mapper<?>> MAPPERS = Map.of(
            LocalTime.class, LocalTimeMapper.INSTANCE,
            LocalDate.class, LocalDateMapper.INSTANCE,
            LocalDateTime.class, LocalDateTimeMapper.INSTANCE
    );

    private MemorySegmentMappers() {
    }

    // Todo: Consider a pluggable architecture via ServiceLoader

    @SuppressWarnings("unchecked")
    public static <T> Optional<MemorySegment.Mapper<T>> of(Class<T> type) {
        Objects.requireNonNull(type);
        return Optional.ofNullable((MemorySegment.Mapper<T>) MAPPERS.get(type));
    }

    public static Set<Class<?>> types() {
        return MAPPERS.keySet();
    }

    private static abstract class AbstractMapper<T>
            implements MemorySegment.Mapper<T> {

        static final VarHandle BYTE_HANDLE = ((ValueLayouts.OfByteImpl) ValueLayout.JAVA_BYTE).accessHandle();

        private final Class<T> type;
        private final GroupLayout layout;

        protected AbstractMapper(Class<T> type, GroupLayout layout) {
            this.type = type;
            this.layout = layout;
        }

        @Override
        public final T get(MemorySegment segment, long offset) {
            BYTE_HANDLE.getVolatile(segment, offset);
            return get0(segment, offset);
        }

        @Override
        public final void set(MemorySegment segment, long offset, T value) {
            set0(segment, offset, value);
            // CAS(0, 0) instead?
            BYTE_HANDLE.setVolatile(segment, offset, BYTE_HANDLE.get(segment, offset));
        }

        protected abstract T get0(MemorySegment segment, long offset);

        protected abstract void set0(MemorySegment segment, long offset, T value);

        @Override
        public final GroupLayout layout() {
            return layout;
        }

        @Override
        public String toString() {
            return getClass().getName()+"{type=" + type + ", layout=" + layout + "}";
        }
    }

    private static final class LocalTimeMapper
            extends AbstractMapper<LocalTime>
            implements MemorySegment.Mapper<LocalTime> {

        static final GroupLayout LAYOUT = MemoryLayout.structLayout(
                Stream.of("hour", "minute", "second", "nanoOfSecond")
                        .map(ValueLayout.JAVA_INT::withName)
                        .toArray(ValueLayout.OfInt[]::new)
        );

        static final LocalTimeMapper INSTANCE = new LocalTimeMapper();

        private LocalTimeMapper() {
            super(LocalTime.class, LAYOUT);
        }

        @Override
        protected LocalTime get0(MemorySegment segment, long offset) {
            // Todo: Should we store in the exposed "int" format or the internal "byte" format?
            return LocalTime.of(
                    segment.get(ValueLayout.JAVA_INT, offset),
                    segment.get(ValueLayout.JAVA_INT, offset + 4),
                    segment.get(ValueLayout.JAVA_INT, offset + 8),
                    segment.get(ValueLayout.JAVA_INT, offset + 12)
            );
        }

        @Override
        protected void set0(MemorySegment segment, long offset, LocalTime value) {
            segment.set(ValueLayout.JAVA_INT, offset, value.getHour());
            segment.set(ValueLayout.JAVA_INT, offset + 4, value.getMinute());
            segment.set(ValueLayout.JAVA_INT, offset + 8, value.getSecond());
            segment.set(ValueLayout.JAVA_INT, offset + 12, value.getNano());
        }
    }

    private static final class LocalDateMapper
            extends AbstractMapper<LocalDate>
            implements MemorySegment.Mapper<LocalDate> {

        static final GroupLayout LAYOUT = MemoryLayout.structLayout(
                Stream.of("year", "month", "dayOfMonth")
                        .map(ValueLayout.JAVA_INT::withName)
                        .toArray(ValueLayout.OfInt[]::new)
        );

        static final LocalDateMapper INSTANCE = new LocalDateMapper();

        private LocalDateMapper() {
            super(LocalDate.class, LAYOUT);
        }

        @Override
        protected LocalDate get0(MemorySegment segment, long offset) {
            // Todo: Should we store in the exposed "int" format or the internal "byte" format?
            return LocalDate.of(
                    segment.get(ValueLayout.JAVA_INT, offset),
                    segment.get(ValueLayout.JAVA_INT, offset + 4),
                    segment.get(ValueLayout.JAVA_INT, offset + 8)
            );
        }

        @Override
        protected void set0(MemorySegment segment, long offset, LocalDate value) {
            segment.set(ValueLayout.JAVA_INT, offset, value.getYear());
            segment.set(ValueLayout.JAVA_INT, offset + 4, value.getMonthValue());
            segment.set(ValueLayout.JAVA_INT, offset + 8, value.getDayOfMonth());
        }
    }

    private static final class LocalDateTimeMapper
            extends AbstractMapper<LocalDateTime>
            implements MemorySegment.Mapper<LocalDateTime> {

        static final GroupLayout LAYOUT = MemoryLayout.structLayout(
                LocalDateMapper.LAYOUT.withName("date"),
                LocalTimeMapper.LAYOUT.withName("time")
        );

        static final LocalDateTimeMapper INSTANCE = new LocalDateTimeMapper();

        private LocalDateTimeMapper() {
            super(LocalDateTime.class, LAYOUT);
        }

        @Override
        protected LocalDateTime get0(MemorySegment segment, long offset) {
            return LocalDateTime.of(
                    LocalDateMapper.INSTANCE.get(segment, offset),
                    LocalTimeMapper.INSTANCE.get(segment, offset + LocalDateMapper.INSTANCE.layout().byteSize()));
        }

        @Override
        protected void set0(MemorySegment segment, long offset, LocalDateTime value) {
            LocalDateMapper.INSTANCE.set(segment, offset, value.toLocalDate());
            LocalTimeMapper.INSTANCE.set(segment, offset + LocalDateMapper.INSTANCE.layout().byteSize(), value.toLocalTime());
        }
    }

}
