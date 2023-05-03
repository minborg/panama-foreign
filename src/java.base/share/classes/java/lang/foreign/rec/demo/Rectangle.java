package java.lang.foreign.rec.demo;

import java.lang.foreign.rec.Length;

/**
 * R
 * @param points pts
 */
public record Rectangle(@Length(4) Point[] points) {
}
