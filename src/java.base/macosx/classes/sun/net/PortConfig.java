package sun.net;

import jdk.internal.include.sys.sysctl.SysctlH;

public final class PortConfig {

    private PortConfig() {}

    private static final int LOWER = getLower();
    private static final int UPPER = getUpper0();

    static int getLower0() {
        return requireNonNegativeOrElse(SysctlH.getValAsInt("net.inet.ip.portrange.first"), 49152);
    }

    static int getUpper0() {
        return requireNonNegativeOrElse(SysctlH.getValAsInt("net.inet.ip.portrange.last"), 65535);
    }

    private static int requireNonNegativeOrElse(int value, int def) {
        return (value >= 0)
                ? value
                : def;
    }

    public static int getLower() {
        return LOWER;
    }

    public static int getUpper() {
        return UPPER;
    }

}
