/*
 * Copyright (c) 2001, 2019, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4313882 4981129 8143610 8232673
 * @summary Unit test for datagram-socket-channel adaptors
 * @modules java.base/java.net:+open
 * @library .. /test/lib
 * @build jdk.test.lib.Utils TestServers
 * @run main AdaptorBasic
 * @key randomness
 */

import java.net.*;
import java.nio.channels.*;
import java.util.*;
import java.lang.reflect.Field;


public class AdaptorBasic {

    static java.io.PrintStream out = System.out;
    static Random rand = new Random();

    static String toString(DatagramPacket dp) {
        return ("DatagramPacket[off=" + dp.getOffset()
                + ", len=" + dp.getLength()
                + ", addr=" + dp.getAddress()
                + ", port=" + dp.getPort()
                + "]");
    }

    static int getBufLength(DatagramPacket p) throws Exception {
        Field f = DatagramPacket.class.getDeclaredField("bufLength");
        f.setAccessible(true);
        return (int) f.get(p);
    }

    static void test(DatagramSocket ds, InetSocketAddress dst, boolean shouldTimeout)
        throws Exception
    {
        DatagramPacket op = new DatagramPacket(new byte[100], 13, 42, dst);
        rand.nextBytes(op.getData());
        int bufLength = 100 - 19;
        DatagramPacket ip = new DatagramPacket(new byte[100], 19, bufLength);
        out.println("pre  op: " + toString(op) + "  ip: " + toString(ip));

        long start = System.currentTimeMillis();
        ds.send(op);

        for (;;) {
            try {
                ds.receive(ip);
            } catch (SocketTimeoutException x) {
                if (shouldTimeout) {
                    out.println("Receive timed out, as expected");
                    return;
                }
                throw x;
            }
            break;
        }

        out.println("rtt: " + (System.currentTimeMillis() - start));
        out.println("post op: " + toString(op) + "  ip: " + toString(ip));

        for (int i = 0; i < ip.getLength(); i++) {
            if (ip.getData()[ip.getOffset() + i]
                != op.getData()[op.getOffset() + i])
                throw new Exception("Incorrect data received");
        }

        System.out.println("ip.getSocketAddress() = " + ip.getSocketAddress());
        System.out.println("ip.getSocketAddress().getClass().getName() = " + ip.getSocketAddress().getClass().getName());
        var a = ((InetSocketAddress)ip.getSocketAddress()).getAddress();
        var bytes = a.getAddress();
        System.out.println("Arrays.toString(bytes) = " + Arrays.toString(bytes));

        if (!(ip.getSocketAddress().equals(dst))) {
            throw new Exception("Incorrect sender address, expected: " + dst
                + " actual: " + ip.getSocketAddress());
        }

        if (getBufLength(ip) != bufLength) {
            throw new Exception("DatagramPacket bufLength changed by receive!!!");
        }
    }

    static void test(InetSocketAddress dst,
                     int timeout, boolean shouldTimeout,
                     boolean connect)
        throws Exception
    {
        out.println();
        out.println("dst: " + dst);

        DatagramSocket ds;
        if (false) {
            // Original
            ds = new DatagramSocket();
        } else {
            DatagramChannel dc = DatagramChannel.open();
            ds = dc.socket();
            ds.bind(new InetSocketAddress(0));
        }

        out.println("socket: " + ds);
        if (connect) {
            ds.connect(dst);
            out.println("connect: " + ds);
        }
        InetSocketAddress src = new InetSocketAddress(ds.getLocalAddress(),
                                                      ds.getLocalPort());
        out.println("src: " + src);

        if (timeout > 0)
            ds.setSoTimeout(timeout);
        out.println("timeout: " + ds.getSoTimeout());

        for (int i = 0; i < 5; i++) {
            test(ds, dst, shouldTimeout);
        }

        // Leave the socket open so that we don't reuse the old src address
        //ds.close();

    }

    public static void main(String[] args) throws Exception {

        for (int i = 0; i < 10; i++) {
            DatagramChannel dc = DatagramChannel.open();
            DatagramSocket ds = dc.socket();
            ds.bind(new InetSocketAddress(0));
            System.out.println("ds.getLocalSocketAddress() = " + ds.getLocalSocketAddress());
            Thread.sleep(1000);
        }

        // need an UDP echo server
        try (TestServers.UdpEchoServer echoServer
                = TestServers.UdpEchoServer.startNewServer(100)) {
            final InetSocketAddress address
                = new InetSocketAddress(echoServer.getAddress(),
                                        echoServer.getPort());
            test(address, 0, false, false);
            test(address, 0, false, true);

            test(address, 0, false, false);

            /*

            It appears, DatagramSocket::receive0 does not always set the address...

            dst: /192.168.1.126:55803
socket: sun.nio.ch.DatagramSocketAdaptor@413a4b4
src: /[0:0:0:0:0:0:0:0]:56345
timeout: 0
pre  op: DatagramPacket[off=13, len=42, addr=/192.168.1.126, port=55803]  ip: DatagramPacket[off=19, len=81, addr=null, port=0]
path = java.base/sun.nio.ch.NativeSocketAddress$Impl.decode(NativeSocketAddress.java:132)
java.base/sun.nio.ch.DatagramChannelImpl2.sourceSocketAddress(DatagramChannelImpl2.java:812)
java.base/sun.nio.ch.DatagramChannelImpl2.trustedBlockingReceive(DatagramChannelImpl2.java:706)
java.base/sun.nio.ch.DatagramChannelImpl2.blockingReceive(DatagramChannelImpl2.java:667)
java.base/sun.nio.ch.DatagramSocketAdaptor.receive(DatagramSocketAdaptor.java:241)
AdaptorBasic.test(AdaptorBasic.java:73)
AdaptorBasic.test(AdaptorBasic.java:141)
AdaptorBasic.main(AdaptorBasic.java:168)
java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:104)
java.base/java.lang.reflect.Method.invoke(Method.java:578)
view = AF_INET6, address=/0:0:0:0:0:0:0:0, port=55803
1c 1e d9 fb 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
view.address() = /0:0:0:0:0:0:0:0
view.port() = 55803
ERROR DIRECT:
sender = /[0:0:0:0:0:0:0:0]:55803
Arrays.toString(sourceSockAddr.segment().toArray(ValueLayout.JAVA_BYTE)) = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
ERROR:
sender = /[0:0:0:0:0:0:0:0]:55803
rtt: 110
post op: DatagramPacket[off=13, len=42, addr=/192.168.1.126, port=55803]  ip: DatagramPacket[off=19, len=42, addr=/0:0:0:0:0:0:0:0, port=55803]
ip.getSocketAddress() = /[0:0:0:0:0:0:0:0]:55803
ip.getSocketAddress().getClass().getName() = java.net.InetSocketAddress
Arrays.toString(bytes) = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]
STDERR:
java.lang.Exception: Incorrect sender address, expected: /192.168.1.126:55803 actual: /[0:0:0:0:0:0:0:0]:55803
	at AdaptorBasic.test(AdaptorBasic.java:101)
	at AdaptorBasic.test(AdaptorBasic.java:141)
	at AdaptorBasic.main(AdaptorBasic.java:168)
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:104)
	at java.base/java.lang.reflect.Method.invoke(Method.java:578)
	at com.sun.javatest.regtest.agent.MainActionHelper$AgentVMRunnable.run(MainActionHelper.java:312)
	at java.base/java.lang.Thread.run(Thread.java:1623)

JavaTest Message: Test threw exception: java.lang.Exception
JavaTest Message: shutting down test

             */


            test(address, Integer.MAX_VALUE, false, false);
        }
        try (TestServers.UdpDiscardServer discardServer
                = TestServers.UdpDiscardServer.startNewServer()) {
            final InetSocketAddress address
                = new InetSocketAddress(discardServer.getAddress(),
                                        discardServer.getPort());
            test(address, 10, true, false);
        }
    }

}
