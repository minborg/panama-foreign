package jdk.internal.include.support.ch;

import java.io.IOException;
import java.net.BindException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.util.Map;
import java.util.function.Function;

import static jdk.internal.include.ErrorNo.*;
import static jdk.internal.include.ErrorNo.EINPROGRESS;

public final class SocketReturnValueHandler {

    private SocketReturnValueHandler() {
    }

    // Todo: Move to support class
    // Need to generate from asm/errorno.h
    public static int throwIfError(int rv) throws IOException {
        class Holder {
            // Todo: Use a primitive IntMap that can be built up using plural cases
            private static final Map<Integer, Function<String, ? extends IOException>> INTEGER_SUPPLIER_MAP = Map.of(
                    // Todo: Add conditional EPROTO
                    ECONNREFUSED(), ConnectException::new,
                    ETIMEDOUT(), ConnectException::new,
                    ENOTCONN(), ConnectException::new,
                    EHOSTUNREACH(), NoRouteToHostException::new,
                    EADDRINUSE(), BindException::new,
                    EADDRNOTAVAIL(), BindException::new,
                    EACCES(), BindException::new
            );
        }
        if (0 <= rv || EINPROGRESS() == rv) {
            // No error
            return rv;
        }
        throw Holder.INTEGER_SUPPLIER_MAP.getOrDefault(rv, SocketException::new)
                .apply("NioSocketError");
    }

}
