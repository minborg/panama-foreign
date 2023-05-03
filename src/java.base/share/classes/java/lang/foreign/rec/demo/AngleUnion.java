package java.lang.foreign.rec.demo;

import java.lang.foreign.rec.Union;

/**
 * U
 * @param triangle t
 * @param rectangle r
 */
@Union
public record AngleUnion(Triangle triangle, Rectangle rectangle) {
}
