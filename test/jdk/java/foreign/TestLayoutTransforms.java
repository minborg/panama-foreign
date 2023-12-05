/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @run junit TestLayoutTransforms
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.ValueLayout;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.lang.foreign.ValueLayout.*;
import static org.junit.jupiter.api.Assertions.*;

final class TestLayoutTransforms {

    @Test
    void javaInt() {
        var intTransformer = Transformer.of(OfInt.class, MemoryLayout::withoutName);
        var actual = JAVA_INT.withName("A").transform(intTransformer);
        assertEquals(JAVA_INT, actual);
    }

    @ParameterizedTest
    @MethodSource("composites")
    void testMemoryLayoutTransformer(MemoryLayout compositeLayout) {
        var transformer = Transformer.of(MemoryLayout.class, MemoryLayout::withoutName);
        var actual = compositeLayout.transform(transformer);
        assertDeeply(actual, l -> {
            if (l.name().isPresent()) {
                fail("There is a name for " + l);
            }
        });
    }

    @ParameterizedTest
    @MethodSource("composites")
    void testOfIntTransformer(MemoryLayout compositeLayout) {
        var transformer = Transformer.of(OfInt.class, MemoryLayout::withoutName);
        var actual = compositeLayout.transform(transformer);
        assertDeeply(actual, l -> {
            if (l instanceof OfInt in) {
                // Only OfInt shall be affected
                if (in.name().isPresent()) {
                    fail("There is a name for OfInt " + in);
                }
            } else {
                if (l.name().isEmpty()) {
                    fail("There is no name for non OfInt" + l);
                }
            }
        });
    }

    static Stream<Arguments> composites() {
        return Stream.of(
                MemoryLayout.sequenceLayout(10, JAVA_INT.withName("A")).withName("sequence"),
                MemoryLayout.structLayout(JAVA_INT.withName("I"), MemoryLayout.paddingLayout(4).withName("P"), JAVA_LONG.withName("L")).withName("struct"),
                MemoryLayout.unionLayout(JAVA_LONG.withName("I"), JAVA_DOUBLE.withName("D")).withName("union")
        ).map(Arguments::of);
    }

    static ValueLayout[] BASIC_LAYOUTS = {
            JAVA_BOOLEAN,
            JAVA_BYTE,
            JAVA_CHAR,
            JAVA_SHORT,
            JAVA_INT,
            JAVA_FLOAT,
            JAVA_LONG,
            JAVA_DOUBLE
    };

    void assertDeeply(MemoryLayout layout,
                      Consumer<MemoryLayout> asserter) {
        asserter.accept(layout);
        switch (layout) {
            case SequenceLayout sl -> assertDeeply(sl.elementLayout(), asserter);
            case GroupLayout gl -> gl.memberLayouts().forEach(ml -> assertDeeply(ml, asserter));
            default -> {
            }
        }
    }

}
