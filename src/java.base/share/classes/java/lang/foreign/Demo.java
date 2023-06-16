package java.lang.foreign;

import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Demo class, to be removed.
 */
public final class Demo {

    private Demo() {}

    /**
     * Holds a point
     * @param x coordinate
     * @param y coordinate
     */
    public record Point(int x, int y){}

    /**
     * Demo main method
     *
     * @param args not used
     */
    public static void main(String[] args) {
        var segment = MemorySegment.ofArray(new int[]{3, 4});

        // Record-centric approach: 1:1 mapping from the record
        // Todo: Factories from StructLayout.OfClass but used to be via MemoryLayout.structLayout()
        StructLayout.OfClass<Point> recordLayout = StructLayout.OfClass.ofRecord(Point.class); //
        Point point = segment.get(recordLayout, 0); // Point[3, 4]

        System.out.println("recordLayout = " + recordLayout);

        // Layout-centric approach: Partial mapping from the layout
        var layout = MemoryLayout.structLayout(
                JAVA_INT.withName("x"),
                JAVA_INT.withName("y"),
                JAVA_INT.withName("color")
        );

        /*
        var pointLayout = layout.ofRecord(Point.class);
        Point point1 = segment.get(pointLayout, 0);
         */


        // Unions
        UnionLayout.OfClass<Point> unionLayout = UnionLayout.OfClass.ofRecord(Point.class);
        Point pointU = segment.get(unionLayout, 0); // Point[3, 3]

    }

}
