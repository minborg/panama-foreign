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
 * @enablePreview
 * @run testng/othervm MemoryLayoutTranslationTest
 */

import org.testng.annotations.*;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

import static java.lang.foreign.ValueLayout.*;
import static org.testng.Assert.*;

public class MemoryLayoutTranslationTest {

    // These tests check both compile-time and runtime properties.
    // withName() et al. should return the same type as the original object.

    private static final String NAME = "a";
    private static final long BIT_ALIGNMENT = Byte.SIZE;
    private static final ByteOrder BYTE_ORDER = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN
            ? ByteOrder.LITTLE_ENDIAN
            : ByteOrder.BIG_ENDIAN;

    @Test
    public void testSwapEndian() {
        var original = MemoryLayout.structLayout(
                JAVA_INT,
                JAVA_LONG
        );
        System.out.println(original);

        var transformed = Transformer.create()
                .withValues(MemoryLayoutTranslationTest::swapEndian)
                .transform(original);

        System.out.println(transformed);

    }

    static private ValueLayout swapEndian(ValueLayout original) {
        return original.withOrder(original.order() == ByteOrder.BIG_ENDIAN
                ? ByteOrder.LITTLE_ENDIAN
                : ByteOrder.BIG_ENDIAN);
    }


}
