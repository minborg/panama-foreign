/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestJepExamplesUnions
 */

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.UnionLayout;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandles;
import java.nio.ByteOrder;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class TestJepExamplesUnions {

    // union IpConverter {
    //    unsigned int ipAddress;
    //    unsigned char ipBytes[4];
    //};


    private static final
    UnionLayout IP_CONVERTER = MemoryLayout.unionLayout(
            JAVA_INT.withName("ipAddress")/*.withOrder(ByteOrder.BIG_ENDIAN)*/,
            MemoryLayout.sequenceLayout(4, JAVA_BYTE).withName("ipBytes")
    );


    public record IpConverterInt(int ipAddress) {
    }

    public record IpConverterIpBytes(byte[] ipBytes) {
    }

    @Test
    void IpConverterInt() {
        MemorySegment segment = Arena.ofAuto().allocate(IP_CONVERTER);

        SegmentMapper<IpConverterInt> ipConverterIntMapper =
                SegmentMapper.ofRecord(IpConverterInt.class, IP_CONVERTER);

        SegmentMapper<IpConverterIpBytes> ipConverterIpBytesMapper =
                SegmentMapper.ofRecord(IpConverterIpBytes.class, IP_CONVERTER);

        // Localhost in network order (big endian)
        ipConverterIpBytesMapper.set(
                segment,
                new IpConverterIpBytes(new byte[]{0x01, 0x00, 0x00, 0x7f}));

        int localHostAddress = ipConverterIntMapper.get(segment).ipAddress();

        assertEquals(0x7f000001, localHostAddress);
    }

}
