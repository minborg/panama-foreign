
/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @enablePreview
 * @modules java.base/jdk.internal.foreign
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestInternalMemoryLayout
 */

import jdk.internal.foreign.InternalMemoryLayout;
import jdk.internal.foreign.LayoutTransformer;
import org.testng.annotations.*;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.foreign.ValueLayout.*;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static org.testng.Assert.*;

@Test
public class TestInternalMemoryLayout {

    private static final MemoryLayout STRUCT = MemoryLayout.structLayout(
                    JAVA_BOOLEAN.withName("boolean"),
                    JAVA_BYTE.withName("byte"),
                    JAVA_SHORT.withName("short"),
                    JAVA_INT.withName("int"),
                    JAVA_LONG.withName("long"),
                    JAVA_FLOAT.withName("float"),
                    JAVA_DOUBLE.withName("double"),
                    JAVA_CHAR.withName("char"),
                    ADDRESS.withName("address"),
                    MemoryLayout.paddingLayout(4)
            )
            .withName("struct")
            .withBitAlignment(Long.SIZE);

    private static final MemoryLayout SEQUENCE = MemoryLayout.sequenceLayout(8L, STRUCT)
            .withName("sequence")
            .withBitAlignment(Integer.SIZE);

    private static final MemoryLayout LAYOUT = MemoryLayout.unionLayout(
                    STRUCT,
                    SEQUENCE
            )
            .withName("layout")
            .withBitAlignment(Long.SIZE * 2);


    // Demonstrates instance of use for single case
    private static final LayoutTransformer TO_BIG_ENDIAN = l -> (l instanceof ValueLayout vl)
            ? vl.withOrder(ByteOrder.BIG_ENDIAN)
            : l;

    // Demonstrates pattern matching in switch
    private static final LayoutTransformer TO_LITTLE_ENDIAN = l -> switch (l) {
        case ValueLayout vl -> vl.withOrder(ByteOrder.LITTLE_ENDIAN);
        default -> l;
    };

    // Demonstrates matching. Useful for cases with only one match as these are.
    private static final LayoutTransformer TO_LITTLE_ENDIAN_MATCH =
            LayoutTransformer.matching(ValueLayout.class, vl -> vl.withOrder(ByteOrder.LITTLE_ENDIAN));

    @Test
    public void valueLayoutTest() {
        MemoryLayout le = InternalMemoryLayout.transform(LAYOUT, TO_LITTLE_ENDIAN);
        MemoryLayout be = InternalMemoryLayout.transform(LAYOUT, TO_BIG_ENDIAN);
        assertNotEquals(le, be);
        assertTrue(le.toString().equalsIgnoreCase(be.toString()));
    }

    @Test
    public void valueLayoutTestMatch() {
        MemoryLayout le = InternalMemoryLayout.transform(LAYOUT, TO_LITTLE_ENDIAN_MATCH);
        MemoryLayout be = InternalMemoryLayout.transform(LAYOUT, TO_BIG_ENDIAN);
        assertNotEquals(le, be);
        assertTrue(le.toString().equalsIgnoreCase(be.toString()));
    }

    @Test
    public void intTest() {
        MemoryLayout ri = InternalMemoryLayout.transform(LAYOUT,
                LayoutTransformer.matching(ValueLayout.OfInt.class, oi -> oi.withName("replacedInt")));
        long matchCount = matches("replacedInt", ri.toString())
                .count();
        assertEquals(matchCount, 2L);
    }

    @Test
    public void transformSequenceToStructTest() {

        Function<SequenceLayout, MemoryLayout> mapper = sl -> {
                MemoryLayout[] memoryLayouts = new MemoryLayout[(int) sl.elementCount()];
                Arrays.fill(memoryLayouts, sl.elementLayout());
                return MemoryLayout.structLayout(memoryLayouts);
        };

        MemoryLayout tl = InternalMemoryLayout.transform(LAYOUT, LayoutTransformer.matching(SequenceLayout.class, mapper));
        assertEquals(tl.toString(),

                "128%[" +
                        "[z8(boolean)b8(byte)s16(short)i32(int)j64(long)f32(float)d64(double)c16(char)a64(address)x4](struct)|" +
                        "[" +
                        "[z8(boolean)b8(byte)s16(short)i32(int)j64(long)f32(float)d64(double)c16(char)a64(address)x4](struct)" +
                        "[z8(boolean)b8(byte)s16(short)i32(int)j64(long)f32(float)d64(double)c16(char)a64(address)x4](struct)" +
                        "[z8(boolean)b8(byte)s16(short)i32(int)j64(long)f32(float)d64(double)c16(char)a64(address)x4](struct)" +
                        "[z8(boolean)b8(byte)s16(short)i32(int)j64(long)f32(float)d64(double)c16(char)a64(address)x4](struct)" +
                        "[z8(boolean)b8(byte)s16(short)i32(int)j64(long)f32(float)d64(double)c16(char)a64(address)x4](struct)" +
                        "[z8(boolean)b8(byte)s16(short)i32(int)j64(long)f32(float)d64(double)c16(char)a64(address)x4](struct)" +
                        "[z8(boolean)b8(byte)s16(short)i32(int)j64(long)f32(float)d64(double)c16(char)a64(address)x4](struct)" +
                        "[z8(boolean)b8(byte)s16(short)i32(int)j64(long)f32(float)d64(double)c16(char)a64(address)x4](struct)" +
                        "]" +
                        "](layout)");


        // Make sure recursive transformation is made
        MemoryLayout layout = MemoryLayout.sequenceLayout(2,
                MemoryLayout.sequenceLayout(2, JAVA_INT)
        );
        MemoryLayout tl2 = InternalMemoryLayout.transform(layout, LayoutTransformer.matching(SequenceLayout.class, mapper));
        assertEquals(tl2.toString(), "[[i32i32][i32i32]]");

        LayoutTransformer transformer = ml -> (ml instanceof SequenceLayout sl)
                ? MemoryLayout.structLayout(IntStream.range(0, (int) sl.elementCount())
                .mapToObj(i -> sl.elementLayout())
                .toArray(MemoryLayout[]::new))
                : ml;

    }

    private record Match(String text, int start, int end) {
        public Match(Matcher matcher) {
            this(matcher.group(), matcher.start(), matcher.end());
        }
    }

    private Stream<Match> matches(String regexp, String s) {
        final Pattern pattern = Pattern.compile(regexp);
        final Matcher matcher = pattern.matcher(s);
        final Stream.Builder<Match> builder = Stream.builder();
        while (matcher.find()) {
            builder.add(new Match(matcher));
        }
        return builder.build();
    }

}
