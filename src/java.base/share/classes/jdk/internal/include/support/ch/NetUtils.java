package jdk.internal.include.support.ch;

import jdk.internal.include.netinet.*;

import java.lang.foreign.MemoryLayout;


public final class NetUtils {

    // Items corresponding to items from net_utils.h and net_util_md.h

    public static final int MAX_PACKET_LEN = 65536;

    /**
     * {@snippet lang = c:
     * typedef union {
     *     struct sockaddr     sa;
     *     struct sockaddr_in  sa4;
     *     struct sockaddr_in6 sa6;
     * } SOCKETADDRESS;
     * }
     */
    public static final MemoryLayout SOCKET_ADDRESS = MemoryLayout.unionLayout(
            SockaddrStruct.layout().withName("sa"),
            SockaddrInStruct.layout().withName("sa4"),
            SockaddrIn6Struct.layout().withName("sa6")
    );

    private NetUtils() {}

}
