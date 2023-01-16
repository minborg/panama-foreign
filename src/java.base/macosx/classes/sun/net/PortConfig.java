package sun.net;

import jdk.internal.include.sys.sysctl.SysctlH;

public final class PortConfig {

    private PortConfig() {}

    private static final int LOWER = getLower();
    private static final int UPPER = getUpper0();

    static int getLower0() {
        return SysctlH.getValAsInt("net.inet.ip.portrange.first");
    }

    static int getUpper0() {
        return SysctlH.getValAsInt("net.inet.ip.portrange.last");
    }

    public static int getLower() {
        return LOWER;
    }

    public static int getUpper() {
        return UPPER;
    }

}
