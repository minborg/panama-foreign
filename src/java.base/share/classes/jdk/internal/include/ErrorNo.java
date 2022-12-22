package jdk.internal.include;

import com.sun.org.apache.xpath.internal.operations.Mod;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.*;

public final class ErrorNo {
    private ErrorNo() {}

    public static int ENOENT() {
        return errno_h.ENOENT();
    }

    public static int ESRCH() {
        return errno_h.ESRCH();
    }

    public static int EINTR() {
        return errno_h.EINTR();
    }

    public static int EBADF() {
        return errno_h.EBADF();
    }

    public static int ENOMEM() {
        return errno_h.ENOMEM();
    }

    public static int EACCES() {
        return errno_h.EACCES();
    }

    public static int EEXIST() {
        return errno_h.EEXIST();
    }

    public static int ENOTDIR() {
        return errno_h.ENOTDIR();
    }

    public static int EISDIR() {
        return errno_h.EISDIR();
    }

    public static int EINVAL() {
        return errno_h.EINVAL();
    }

    public static int ERANGE() {
        return errno_h.ERANGE();
    }

    public static int EAGAIN() {
        return errno_h.EAGAIN();
    }

    public static int EINPROGRESS() {
        return errno_h.EINPROGRESS();
    }

    public static int EADDRINUSE() {
        return errno_h.EADDRINUSE();
    }

    public static int EADDRNOTAVAIL() {
        return errno_h.EADDRNOTAVAIL();
    }

    public static int ENOTCONN() {
        return errno_h.ENOTCONN();
    }

    public static int ETIMEDOUT() {
        return errno_h.ETIMEDOUT();
    }

    public static int ECONNREFUSED() {
        return errno_h.ECONNREFUSED();
    }

    public static int ENAMETOOLONG() {
        return errno_h.ENAMETOOLONG();
    }

    public static int EHOSTUNREACH() {
        return errno_h.EHOSTUNREACH();
    }

    public static int EWOULDBLOCK() {
        return errno_h.EWOULDBLOCK();
    }

    public static int EPROTO() {
        return errno_h.EPROTO();
    }

    public static List<String> lookup(int value) {
        final class Holder {
            private static final Map<Integer, List<String>> LOOKUP_MAP =
                    Arrays.stream(ErrorNo.class.getMethods())
                            .filter(m -> m.getParameterCount() == 0)
                            .filter(m -> m.getReturnType() == int.class)
                            .filter(m -> Modifier.isStatic(m.getModifiers()) && Modifier.isPublic(m.getModifiers()))
                            .collect(groupingBy(m -> {
                                        try {
                                            return (int) m.invoke(null);
                                        } catch (Exception e) {
                                            return -1;
                                        }
                                    }
                                    , mapping(Method::getName, toList())));
        }
        return Holder.LOOKUP_MAP.getOrDefault(value, emptyList());
    }

}
