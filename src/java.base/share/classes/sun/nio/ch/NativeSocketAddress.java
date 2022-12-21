/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.ch;

import java.lang.foreign.MemorySegment;
import java.lang.invoke.VarHandle;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

import jdk.internal.access.JavaNetInetAddressAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.foreign.InternalArrays;
import jdk.internal.include.netinet.SockaddrInStruct;
import jdk.internal.include.netinet.SockaddrIn6Struct;

import static java.lang.foreign.MemoryLayout.PathElement.groupElement;
import static java.lang.foreign.ValueLayout.*;
import static java.net.StandardProtocolFamily.INET;
import static java.net.StandardProtocolFamily.INET6;
import static java.util.stream.Collectors.joining;
import static jdk.internal.include.netinet.InH.AF_INET;
import static jdk.internal.include.netinet.InH.AF_INET6;
import static jdk.internal.include.netinet.SockaddrStruct.sa_family$get;
import static jdk.internal.include.netinet.SockaddrInStruct.*;
import static jdk.internal.include.netinet.SockaddrIn6Struct.*;

/**
 * Represents a native socket address buffer that is the union of
 * struct sockaddr, struct sockaddr_in, and struct sockaddr_in6.
 * <p>
 * The implementing class is not thread-safe.
 */
public sealed interface NativeSocketAddress {

    /**
     * Encodes the given InetSocketAddress into this native socket address's backing native memory.
     *
     * @param protocolFamily protocol family
     * @param isa            the InetSocketAddress to encode
     * @return the size of the socket address (sizeof sockaddr or sockaddr6)
     * @throws UnsupportedAddressTypeException if the address type is not supported
     */
    int encode(ProtocolFamily protocolFamily, InetSocketAddress isa);

    /**
     * {@return Decodes a InetSocketAddress from this native socket address's backing native memory}.
     *
     * @throws SocketException if the socket address is not AF_INET or AF_INET6
     */
    InetSocketAddress decode() throws SocketException;

    /**
     * {@return a read-only view of the backing native memory}.
     *
     */
    MemorySegment segment();

    final class Impl implements NativeSocketAddress {

        // The backing native memory
        private final MemorySegment segment;

        private final MemorySegment segmentAsReadOnly;

        // IPv4 view of the backing native memory
        private final SinView sin4View;

        // IPv6 view of the backing native memory
        private final SinView sin6View;

        public Impl(MemorySegment segment) {
            this.segment = Objects.requireNonNull(segment);
            this.segmentAsReadOnly = segment.asReadOnly();
            this.sin4View = new Sin4View();
            this.sin6View = new Sin6View();
        }

        @Override
        public int encode(ProtocolFamily protocolFamily, InetSocketAddress isa) {
            return viewOf(protocolFamily)
                    .assertInetAddressTypeCorrect(isa.getAddress())
                    .clear()
                    .putFamily()
                    .putPort(isa.getPort())
                    .putAddress(isa.getAddress())
                    .sizeOf();
        }

        /**
         * Return an InetSocketAddress to represent the socket address in this buffer.
         *
         * @throws SocketException if the socket address is not AF_INET or AF_INET6
         */
        public InetSocketAddress decode() throws SocketException {
            var view = currentViewOrThrow();
            try {
                return new InetSocketAddress(view.address(), view.port());
            } catch (Exception e) {
                // TODO: REMOVE
                System.err.println("view = " + view);
                HexFormat hexFormat = HexFormat.ofDelimiter(" ");
                System.err.println(hexFormat.formatHex(segment.toArray(JAVA_BYTE)));
                System.err.println("view.address() = " + view.address());
                System.err.println("view.port() = " + view.port());
                throw e;
            }
        }

        @Override
        public MemorySegment segment() {
            return segmentAsReadOnly;
        }

        @Override
        public boolean equals(Object other) {
            return (other instanceof Impl otherImpl) &&
                    mismatch(otherImpl) < 0; // The size of the segments are always the same
        }

        @Override
        public int hashCode() {
            return InternalArrays.longHashCodeAsInt(InternalArrays.hashCode(segment));
        }

        @Override
        public String toString() {
            return currentView()
                    .map(Objects::toString)
                    .orElse("<unknown>");
        }

        // Private members

        /**
         * {@return the appropriate view corresponding to the provided {@code protocolFamily}}.
         * @param protocolFamily to use
         * @throws UnsupportedOperationException if there is no appropriate view
         */
        private SinView viewOf(ProtocolFamily protocolFamily) {
            // Todo: Use switch expression when this becomes available
            if (INET == protocolFamily) {
                return sin4View;
            } else if (INET6 == protocolFamily) {
                return sin6View;
            }
            throw new UnsupportedOperationException("Protocol family " + protocolFamily + " not supported.");
        }

        /**
         * {@return the appropriate view corresponding to the sa_family stored in native memory}.
         * @throws SocketException if there is no sa_family set (e.g. encode() was never called)
         */
        private SinView currentViewOrThrow() throws SocketException {
/*          // Optimized version to consider
            byte saFamily = sa_family$get(segment);
            if (saFamily == AF_INET()) {
                return sin4View;
            } else if (saFamily == AF_INET6()) {
                return sin6View;
            }
            throw new SocketException("Socket family not recognized.");*/
            return currentView()
                    .orElseThrow(() -> new SocketException("Socket family not recognized."));
        }

        /**
         * {@return the appropriate view corresponding to the sa_family stored in native memory or else
         * {@link Optional#empty()}}.
         */
        private Optional<SinView> currentView() {
            byte saFamily = sa_family$get(segment);
            if (saFamily == AF_INET()) {
                return Optional.of(sin4View);
            } else if (saFamily == AF_INET6()) {
                return Optional.of(sin6View);
            }
            return Optional.empty();
        }

        /**
         * Finds and returns the relative offset, in bytes, of the first mismatch between this
         * native socket address's native memory and the other native socket address's native memory or else
         * returns -1 if no mismatch.
         *
         * @return the byte offset of the first mismatch or -1 if no mismatch
         */
        private int mismatch(Impl other) {
            return (int) MemorySegment.mismatch(segment, 0, segment.byteSize(),
                    other.segment, 0, other.segment.byteSize());
        }

        static {
            IOUtil.load();
        }

        private sealed interface SinView {

            /**
             * Asserts that the provided {@code address} is consisted with this view.
             * <p>
             * An address of type {@link Inet4Address} is consistent with an IPv4 address view.
             * An address of type {@link Inet6Address} is consistent with an IPv6 address view.
             *
             * @param address to check
             * @return this view
             * @throws UnsupportedAddressTypeException if the provided {@code address} is inconsistent with this
             *                                         view.
             */
            SinView assertInetAddressTypeCorrect(InetAddress address);

            /**
             * Clears the backing native memory in its entirety regardless of view type.
             *
             * @return this view
             */
            SinView clear();

            /**
             * Puts the family associated with this view type into the backing native memory.
             *
             * @return this view
             */
            SinView putFamily();

            /**
             * {@return the family for this view type}.
             */
            StandardProtocolFamily family();

            /**
             * Puts the provided {@code port} into the backing native memory.
             *
             * @param port to put
             * @return this view
             */
            SinView putPort(int port);

            /**
             * {@return the port from the backing native memory}.
             */
            int port();

            /**
             * Puts the provided {@code inetAddress} into the backing native memory.
             *
             * @param inetAddress to put
             * @return this view
             * @throws NullPointerException if the provided {@code inetAddress} is {@code null}.
             */
            SinView putAddress(InetAddress inetAddress);

            /**
             * {@return the address from the backing native memory.}
             */
            InetAddress address();

            /**
             * {@return the size of the backing native memory used by this view}.
             */
            int sizeOf();
        }

        private sealed abstract class AbstractSinView implements SinView {

            static final int SHORT_MASK = 0xffff;
            static final JavaNetInetAddressAccess JNINA = SharedSecrets.getJavaNetInetAddressAccess();

            @Override
            public final SinView clear() {
                segment.fill((byte) 0);
                return this;
            }

            @Override
            public final SinView putFamily() {
                sin_family$set(segment, afInet());
                return this;
            }

            @Override
            public final String toString() {
                return afInetName() + ", address=" + address() + ", port=" + port();
            }

            abstract String afInetName();

            abstract byte afInet();

        }

        private final class Sin4View extends AbstractSinView implements SinView {

            private static final VarHandle INT_ADDRESS_VH = SockaddrInStruct.layout().varHandle(
                    groupElement("sin_addr"),
                    groupElement("s_addr"));

            @Override
            public SinView assertInetAddressTypeCorrect(InetAddress address) {
                if (!(address instanceof Inet4Address)) {
                    // Cannot handle Inet6Address instances
                    throw new UnsupportedAddressTypeException();
                }
                return this;
            }

            @Override
            public StandardProtocolFamily family() {
                return INET;
            }

            @Override
            public SinView putPort(int port) {
                sin_port$set(segment, (short) port);
                return this;
            }

            @Override
            public int port() {
                return sin_port$get(segment) & SHORT_MASK;
            }

            @Override
            public SinView putAddress(InetAddress inetAddress) {
                // This cast is guaranteed to succeed as assertInetAddressTypeCorrect() passed
                int ipAddress = JNINA.addressValue((Inet4Address) inetAddress);
                INT_ADDRESS_VH.set(segment, ipAddress);
                return this;
            }

            @Override
            public InetAddress address() {
                try {
                    return InetAddress.getByAddress(sin_addr$slice(segment).toArray(JAVA_BYTE));
                } catch (UnknownHostException e) {
                    throw new InternalError(e);
                }
            }

            @Override
            public int sizeOf() {
                return (int) SockaddrInStruct.sizeof();
            }

            @Override
            String afInetName() {
                return "AF_INET";
            }

            @Override
            byte afInet() {
                return (byte) AF_INET();
            }
        }

        private final class Sin6View extends AbstractSinView implements SinView {

            private static final short FFFF = -1;
            private static final long OFFSET_SIN6_ADDR = SockaddrIn6Struct.layout()
                    .byteOffset(groupElement("sin6_addr"));

            private static final OfInt JAVA_INT_NETWORK_ORDER = JAVA_INT.withOrder(ByteOrder.BIG_ENDIAN);

            public SinView assertInetAddressTypeCorrect(InetAddress address) {
                // We can use either type for IPv6 so this assertion will always succeed
                return this;
            }

            @Override
            public StandardProtocolFamily family() {
                return INET6;
            }

            @Override
            public SinView putPort(int port) {
                sin6_port$set(segment, (short) port);
                return this;
            }

            @Override
            public int port() {
                return sin6_port$get(segment) & SHORT_MASK;
            }

            @Override
            public SinView putAddress(InetAddress inetAddress) {
                // Todo: Use pattern matching when this becomes available
                if (inetAddress instanceof Inet6Address inet6Address) {
                    // IPv6 address
                    byte[] address = JNINA.addressBytes(inet6Address);
                    MemorySegment.copy(address, 0, sin6_addr$slice(segment), JAVA_BYTE, 0, address.length);
                    sin6_scope_id$set(segment, inet6Address.getScopeId());
                } else {
                    // IPv4-mapped IPv6 address
                    segment.set(JAVA_SHORT, OFFSET_SIN6_ADDR + 10L, FFFF); // Byte order not significant for FFFF
                    segment.set(JAVA_INT_NETWORK_ORDER, OFFSET_SIN6_ADDR + 12L, JNINA.addressValue((Inet4Address) inetAddress));
                }
                return this;
            }

            @Override
            public InetAddress address() {
                byte[] bytes = sin6_addr$slice(segment).toArray(JAVA_BYTE);
                try {
                    int scopeId = sin6_scope_id$get(segment);
                    if (scopeId == 0) {
                        return Inet6Address.getByAddress(bytes);
                    } else {
                        return Inet6Address.getByAddress(null, bytes, scopeId);
                    }
                } catch (UnknownHostException e) {
                    throw new InternalError(e);
                }
            }

            @Override
            public int sizeOf() {
                return (int) SockaddrIn6Struct.sizeof();
            }

            @Override
            String afInetName() {
                return "AF_INET6";
            }

            @Override
            byte afInet() {
                return (byte) AF_INET6();
            }
        }
    }
}
