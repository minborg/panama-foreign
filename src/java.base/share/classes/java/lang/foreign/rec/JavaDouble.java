package java.lang.foreign.rec;

/**
 * A
 * @param asDouble a
 */
public record JavaDouble(@BitAlignment(64) double asDouble) {}

// An alternative
// public record JavaDouble(@BitAlignment int bitAlignment, int asDouble) {}