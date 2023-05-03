package java.lang.foreign.rec.demo;

import java.lang.foreign.rec.JavaDouble;
import java.lang.foreign.rec.LayoutPath;
import java.lang.foreign.rec.Length;
import java.lang.foreign.rec.MemorySegment;
import java.lang.foreign.rec.ValueLayouts;
import java.lang.invoke.VarHandle;
import java.util.Objects;

/**
 * D
 */
public class DemoInter {

    /**
     * C
     */
    public DemoInter() {
    }

    /**
     * Main
     * @param args a
     */
    public static void main(String[] args) {

        // Using interfaces provides two benefits over records: real Unions and Laziness (*)

        // This needs a new Object::declaredMethods with a deterministic order or some other
        // way (like annotations) to specify the order

        /* Structs */

        interface Point { double x(); double y(); }

        var pointLayout = Point.class;

        var pointXPath = LayoutPath.of(Point::x);

        LayoutPath<Point, Double> pointYPath = LayoutPath.of(Point::y);

        assertEquals(Point.class, pointXPath.sourceLayout());
        assertEquals(double.class, pointXPath.targetLayout());

        assertEquals(0, pointXPath.offset());
        assertEquals(8, pointYPath.offset());

        VarHandle pointXHandle = LayoutPath.of(Point::x).handle();

        /* Nested Structs */

        interface Line { Point begin(); Point end();}

        var lineLayout = Line.class;

        LayoutPath<Line, Double> lineEndXPath =
                LayoutPath.of(Line::end)
                        .andThen(Point::x);

        assertEquals(Line.class, lineEndXPath.sourceLayout());
        assertEquals(double.class, lineEndXPath.targetLayout());

        assertEquals(16, lineEndXPath.offset());

        System.out.println(lineEndXPath.methods());
        // [public Point Line.end(), public double Point.x()]

        /* Sequences */

        interface Triangle {@Length(3) Point[] trianglePoints();}

        var triangleLayout = Triangle.class;

        var triangleMiddleXPath =
                LayoutPath.of(Triangle::trianglePoints)
                        // the @Length invariant can be asserted early here
                        .andThen(1, Point.class)
                        .andThen(Point::x);

        /* Unions */

        interface Rectangle {@Length(4) Point[] rectanglePoints();}

        interface AngleUnion extends Triangle, Rectangle {}

        var triangleEndXPath =
                LayoutPath.of(AngleUnion::trianglePoints)
                        .andThen(2, Point.class)
                        .andThen(Point::x);

        var rectangleEndXPath =
                LayoutPath.of(AngleUnion::rectanglePoints)
                        .andThen(3, Point.class)
                        .andThen(Point::x);


        /* MemorySegment */

        MemorySegment memorySegment = MemorySegment.ofArray(
                new double[]{3d, 4d, 6d, 0d, 0d, 0d}
        );

        double x = (double) pointXHandle.get(memorySegment); // 3  (as before)

        double xx = memorySegment.get(ValueLayouts.JAVA_DOUBLE, 0); // 3 (as before)

        // Creates or reuses a dynamic proxy with underlying VarHandles
        // with lazy in-situ deserialization. The proxy is just a thin wrapper
        // around a memory segment.
        // A counter-argument is to use VarHandles instead and that laziness is a minor thing
        Point p = memorySegment.get(Point.class, 0); // Proxy without fields
        System.out.println(p.x()); // Invokes the X VarHandle lazily

        memorySegment.stream(JavaDouble.class)
                .mapToDouble(JavaDouble::asDouble)
                .forEach(System.out::println); // 3, 4, 6, 0, 0, 0

        memorySegment.stream(Point.class)
                .forEach(System.out::println); // Point{x=3, y=4}
                                               // Point{x=6, y=0}
                                               // Point{x=0, y=0}

        Triangle t = memorySegment.get(Triangle.class, 0);
        // Triangle{trianglePoints=[Point{x=3, y=4}, Point{x=6, y=0}, Point{x=0, y=0}]}

    }

    private static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError();
        }
    }


}
