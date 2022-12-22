/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/sun.nio.ch
 * @modules java.base/jdk.internal.ref java.base/jdk.internal.include.sys java.base/jdk.internal.include.netinet
 * @run testng/othervm --enable-native-access=ALL-UNNAMED NativeSocketAddressTest
 */

import jdk.internal.include.netinet.SockaddrIn6Struct;
import jdk.internal.include.netinet.SockaddrInStruct;

import org.testng.annotations.*;
import sun.nio.ch.NativeSocketAddress;
import sun.nio.ch.NativeSocketAddressTriplet;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteOrder;
import java.util.HexFormat;
import java.util.Optional;

import static java.net.StandardProtocolFamily.INET;
import static java.net.StandardProtocolFamily.INET6;
import static jdk.internal.include.netinet.InH.AF_INET;
import static jdk.internal.include.netinet.InH.AF_INET6;
import static org.testng.Assert.*;

public class NativeSocketAddressTest {

    private static final InetSocketAddress LOCAL = new InetSocketAddress("localhost", 8080);

    @Test
    public void testNetworkOrder() {
        // If this fails, nothing will ever work.
        // The $LAYOUT() has to be manually edited to reflect "network order" (=big endian) rather than "native order"
        assertInNetworkOrder(SockaddrInStruct.layout(), "sin_port");
        assertInNetworkOrder(SockaddrIn6Struct.layout(), "sin6_port");
    }

    static void assertInNetworkOrder(MemoryLayout layout, String name) {
        Optional.of(layout.select(MemoryLayout.PathElement.groupElement(name)))
                .map(ValueLayout.class::cast)
                .filter(vl -> vl.order() == ByteOrder.BIG_ENDIAN)
                .orElseThrow(() -> new AssertionError(name + " is not in network order"));
    }

    @Test
    public void testToString() throws SocketException {
        test(nas -> {
            assertEquals(nas.toString(), "<unknown>");

            nas.encode(INET, LOCAL);
            assertEquals(nas.toString(), "AF_INET, address=/127.0.0.1, port=8080");

            nas.encode(INET6, LOCAL);
            assertEquals(nas.toString(), "AF_INET6, address=/127.0.0.1, port=8080");
        });
    }

    @Test
    public void testCodeEncode() {
        test(nas -> {
            nas.encode(INET, LOCAL);
            var encoded = nas.decode();
            assertEquals(encoded, LOCAL);
        });
    }

    @Test
    public void testPorts() {
        test(nas -> {
            for (int port = 0; port < 65535; port += 10) {
                var inetSocketAddress = new InetSocketAddress("localhost", port);
                nas.encode(INET, inetSocketAddress);
                var encoded = nas.decode();
                assertEquals(encoded, inetSocketAddress);
            }
        });
    }

    @Test
    public void testLoopbackAddress() {
        test(nas -> {
            for (int port = 0; port < 65535; port += 10) {
                var inetSocketAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), 12345);
                nas.encode(INET, inetSocketAddress);
                var encoded = nas.decode();
                assertEquals(encoded, inetSocketAddress);
            }
        });
    }

    @Test
    public void testBinaryContentInet4() {
        test(nas -> {
            var inetSocketAddress = new InetSocketAddress("128.129.130.131", 0xabcd);
            nas.encode(INET, inetSocketAddress);
            //final MemorySegment segment = MemorySegment.ofAddress(nas.address(), sockaddr_in.sizeof());
            final MemorySegment segment = nas.segment().asSlice(0, SockaddrInStruct.sizeof());
            final String actual = HexFormat.ofDelimiter(" ").formatHex(segment.toArray(ValueLayout.JAVA_BYTE));
            System.out.println("hex4 = " + actual);
            var expect = String.format("00 " + // sin_len
                    "%02x " + // sin_family
                    "ab cd " + // sin_port
                    "80 81 82 83 " + // sin_addr
                    "00 00 00 00 00 00 00 00" // sin_zero
                    , (byte) AF_INET());
            assertEquals(actual, expect);
            var decoded = nas.decode();
            assertEquals(decoded, inetSocketAddress);
        });
    }

    @Test
    public void testBinaryContentInet6() {
        test(nas -> {
            var inetSocketAddress = new InetSocketAddress("8000:8101:8202:8303:8404:8505:8606:8707", 0xabcd);
            nas.encode(INET6, inetSocketAddress);
            // final MemorySegment segment = MemorySegment.ofAddress(nas.address(), sockaddr_in6.sizeof());
            final MemorySegment segment = nas.segment().asSlice(0, SockaddrIn6Struct.sizeof());
            final String actual = HexFormat.ofDelimiter(" ").formatHex(segment.toArray(ValueLayout.JAVA_BYTE));
            System.out.println("hex6 = " + actual);
            var expect = String.format(
                    "00 " + // sin6_len
                            "%02x " + // sin6_family (AF_INET6)
                            "ab cd " + // sin6_port
                            "00 00 00 00 " + // sin6_flowinfo
                            "80 00 81 01 82 02 83 03 84 04 85 05 86 06 87 07 " + // sin6_addr
                            "00 00 00 00", //sin6_scope_id
                    (byte) AF_INET6());
            assertEquals(actual, expect);
            var decoded = nas.decode();
            assertEquals(decoded, inetSocketAddress);
        });
    }

    @Test
    public void testBinaryContentInet6WithInet4Address() {
        test(nas -> {
            var inetSocketAddress = new InetSocketAddress("128.129.130.131", 0xabcd);
            nas.encode(INET6, inetSocketAddress);
            //final MemorySegment segment = MemorySegment.ofAddress(nas.address(), sockaddr_in6.sizeof());
            final MemorySegment segment = nas.segment().asSlice(0, SockaddrIn6Struct.sizeof());
            final String actual = HexFormat.ofDelimiter(" ").formatHex(segment.toArray(ValueLayout.JAVA_BYTE));
            System.out.println("hex6With4 = " + actual);
            var expect = String.format(
                    "00 " + // sin6_len
                            "%02x " + // sin6_family (AF_INET6)
                            "ab cd " + // sin6_port
                            "00 00 00 00 " + // sin6_flowinfo
                            "00 00 00 00 00 00 00 00 00 00 ff ff 80 81 82 83 " + // sin6_addr (padded)
                            "00 00 00 00", //sin6_scope_id
                    (byte) AF_INET6());
            assertEquals(actual, expect);
            var decoded = nas.decode();
            assertEquals(decoded, inetSocketAddress);
        });
    }

    @Test
    public void testSuspect() {
        test(nas -> {
            var inetSocketAddress = new InetSocketAddress("fe80:0:0:0:1819:41bf:ea81:1516", 60499);
            nas.encode(INET6, inetSocketAddress);
            // final MemorySegment segment = MemorySegment.ofAddress(nas.address(), sockaddr_in6.sizeof());
            final MemorySegment segment = nas.segment().asSlice(0, SockaddrIn6Struct.sizeof());
            final String actual = HexFormat.ofDelimiter(" ").formatHex(segment.toArray(ValueLayout.JAVA_BYTE));
            System.out.println("hexSuspectedError = " + actual);
            var expect = String.format(
                    "00 " + // sin6_len
                            "%02x " + // sin6_family (AF_INET6)
                            "ec 53 " + // sin6_port
                            "00 00 00 00 " + // sin6_flowinfo
                            "fe 80 00 00 00 00 00 00 18 19 41 bf ea 81 15 16 " + // sin6_addr
                            "00 00 00 00", //sin6_scope_id
                    (byte) AF_INET6());
            assertEquals(actual, expect);
            var decoded = nas.decode();
            assertEquals(decoded, inetSocketAddress);
        });
    }


    private static void test(ThrowingConsumer<NativeSocketAddress> tester) {
        var nases = NativeSocketAddressTriplet.create();
        try {
            tester.accept(nases.source());
        } catch (SocketException socketException) {
            throw new AssertionError(socketException);
        } finally {
            nases.freeAll();
        }
    }

    interface ThrowingConsumer<T> {
        void accept(T t) throws SocketException;
    }

}
