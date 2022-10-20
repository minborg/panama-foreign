package jdk.internal.include.common;

/**
 * Specifying the current Os in a static final field allows the
 * Java compiler to discard unused branch byte code at compile time.
 */
public enum Os {
    AIX, LINUX, MAC_OS, UNIX, WINDOWS
}
