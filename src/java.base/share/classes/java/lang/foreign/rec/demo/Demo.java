package java.lang.foreign.rec.demo;

import java.lang.foreign.rec.LayoutPath;
import java.lang.foreign.rec.Length;
import java.lang.foreign.rec.MemorySegment;
import java.lang.foreign.rec.MethodReference;
import java.lang.foreign.rec.Union;
import java.lang.foreign.rec.ValueLayouts;
import java.lang.invoke.VarHandle;
import java.util.Objects;

/**
 * A
 */
public class Demo {

    /**
     * C
     */
    public Demo() {
    }

    /**
     * Main
     * @param args prog args
     */
    public static void main(String[] args) {

        // "Expressing Memory Layouts via the Existing Type System"
        // A First Sketch ...

        /* Prologue */

        /*

        Method Reference with metadata

        @FunctionalInterface
        public interface MethodReference<T, R> extends Function<T, R>, ... {

            @Override
            R apply(T target);

            default Method method() {
                return ...
            };
        }
        */

        MethodReference<String, Integer> stringLength = String::length;
        System.out.println(stringLength.method()); // public int java.lang.String.length()

        /* Modelling Memory Layouts */

        /* Structs */

        record Point(double x, double y) {}

        // someMethod(Point.class).asMemoryLayout();

        var pointLayout = Point.class;

        // LayoutPath {
        //     public static <S, T> LayoutPath<S, T> of(MethodReference<S, T> extractor) {...}
        // }

        // Typesafe layouts
        var pointXPath = LayoutPath.of(Point::x);

        LayoutPath<Point, Double> pointYPath = LayoutPath.of(Point::y);

        assertEquals(Point.class, pointXPath.sourceLayout());
        assertEquals(double.class, pointXPath.targetLayout());

        assertEquals(0, pointXPath.offset());
        assertEquals(8, pointYPath.offset());

        VarHandle pointXHandle = LayoutPath.of(Point::x).handle();

        /* Nested Structs */

        record Line(Point begin, Point end) {}

        var lineLayout = Line.class;

        // Typesafe composition
        LayoutPath<Line, Double> lineEndXPath =
                LayoutPath.of(Line::end)
                        .andThen(Point::x);

        assertEquals(Line.class, lineEndXPath.sourceLayout());
        assertEquals(double.class, lineEndXPath.targetLayout());

        assertEquals(16, lineEndXPath.offset());

        System.out.println(lineEndXPath.methods());
        // [public Point Line.end(), public double Point.x()]

        /* Sequences */

        record Triangle(@Length(3) Point[] points) {}

        var triangleLayout = Triangle.class;

        var triangleMiddleXPath =
                LayoutPath.of(Triangle::points)
                        // the @Length invariant can be asserted early here
                        .andThen(1, Point.class)
                        .andThen(Point::x);

        /* Unions */

        @Union
        record AngleUnion(Triangle triangle, Rectangle rectangle){}

        var triangleEndXPath =
                LayoutPath.of(AngleUnion::triangle)
                        .andThen(Triangle::points)
                        .andThen(2, Point.class)
                        .andThen(Point::x);

        var rectangleEndXPath =
                LayoutPath.of(AngleUnion::rectangle)
                        .andThen(Rectangle::points)
                        .andThen(3, Point.class)
                        .andThen(Point::x);


        /* MemorySegment */

        MemorySegment memorySegment = MemorySegment.ofArray(
                new double[]{3d, 4d, 6d, 0d, 0d, 0d}
        );

        double x = (double) pointXHandle.get(memorySegment); // 3  (as before)

        double xx = memorySegment.get(ValueLayouts.JAVA_DOUBLE, 0); // 3 (as before)

        // New seamless "Bridge" between layouts and normal Java classes
        Point p = memorySegment.get(Point.class, 0); // Point{x=3, y =4}

        memorySegment.stream(Point.class)
                .forEach(System.out::println); // Point{x=3, y=4}
                                               // Point{x=6, y=0}
                                               // Point{x=0, y=0}

        Triangle t = memorySegment.get(Triangle.class, 0);
        // Triangle{points=[Point{x=3, y=4}, Point{x=6, y=0}, Point{x=0, y=0}]}

        // Add-hook viewing of a memory segment
        record MyTempType(double foo, byte bar){
            @Override
            public String toString() {
                // Todo: Use string templates
                return String.format("MyTempType{foo=%f, bar=0x%2x}", foo(), bar());
            }
        }
        System.out.println(memorySegment.get(MyTempType.class, 0));
        // MyTempType{foo=3, bar=0xf3}

        // Special linker annotation(s)
        record Float80(short exponent, long fraction){}
        // Valhalla ...
    }


    private static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError();
        }
    }


}
