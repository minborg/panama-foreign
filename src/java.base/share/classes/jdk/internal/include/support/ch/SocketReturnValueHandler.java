package jdk.internal.include.support.ch;

import jdk.internal.include.sys.SocketH;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.net.BindException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.ProtocolException;
import java.net.SocketException;
import java.util.Map;
import java.util.function.Function;

import static jdk.internal.include.ErrorNo.*;

public final class SocketReturnValueHandler {

    private SocketReturnValueHandler() {
    }

    // Todo: Move to support class

    public static int handleSocketError(int n, MemorySegment errCap) throws IOException {
        class Holder {
            // Todo: Maybe use a primitive IntMap that can be built up using plural cases
            private static final Map<Integer, Function<String, ? extends IOException>> INTEGER_SUPPLIER_MAP = Map.of(
                    // Todo: make EPROTO conditional
                    EPROTO(), ProtocolException::new,
                    ECONNREFUSED(), ConnectException::new,
                    ETIMEDOUT(), ConnectException::new,
                    ENOTCONN(), ConnectException::new,
                    EHOSTUNREACH(), NoRouteToHostException::new,
                    EADDRINUSE(), BindException::new,
                    EADDRNOTAVAIL(), BindException::new,
                    EACCES(), BindException::new
            );
        }
        if (n >= 0) {
            // No error
            return n;
        }
        int errno = SocketH.errno(errCap);
        if (errno == EINPROGRESS()) {
            // Non-blocking connect
            return 0;
        }

        throw Holder.INTEGER_SUPPLIER_MAP.getOrDefault(errno, SocketException::new)
                .apply("NioSocketError: n=" + n + ", errno=" + errno);
    }

}
