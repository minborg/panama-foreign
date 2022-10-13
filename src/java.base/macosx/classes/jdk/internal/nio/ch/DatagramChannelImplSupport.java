package jdk.internal.nio.ch;

import jdk.internal.access.JavaIOFileDescriptorAccess;
import jdk.internal.access.SharedSecrets;

import java.io.FileDescriptor;
import java.io.IOException;

import static jdk.internal.include.sys.socket_h.*;
import static jdk.internal.nio.ch.SocketReturnValueHandler.throwIfError;

public final class DatagramChannelImplSupport {

    private static final JavaIOFileDescriptorAccess FD_ACCESS = SharedSecrets.getJavaIOFileDescriptorAccess();

    private DatagramChannelImplSupport() {
    }

    public static void disconnect0(FileDescriptor fd, boolean isIPv6) throws IOException {
        final int rv;
/*        if (*//*isMacOs*//* true) {*/
            rv = disconnectx(FD_ACCESS.get(fd), SAE_ASSOCID_ANY(), SAE_CONNID_ANY());
/*        } else {
            try (var session = MemorySession.openImplicit()) {
                var segment = session.allocate(sockaddr_in6.$LAYOUT());
                int len = sockaddr_in6.sin6_family$set(segment,
                        (byte) (isIPv6 ? AF_INET6() : AF_INET()),
                        (isIPv6 ? sockaddr_in6.sizeof() : sockaddr_in.sizeof());
                rv = socket_h.connect(FD_ACCESS.get(fd), segment, len);
            }
        }*/
        // Todo: add proper error handling here
        // Todo: investigate how to access errno
        throwIfError(rv);
    }
}
