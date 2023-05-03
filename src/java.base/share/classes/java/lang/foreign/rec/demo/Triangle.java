package java.lang.foreign.rec.demo;

import java.lang.foreign.rec.Length;

/**
 * T
 * @param points pts
 */
public record Triangle(@Length(3) Point[] points) {}
