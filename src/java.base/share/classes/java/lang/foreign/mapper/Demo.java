package java.lang.foreign.mapper;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.util.stream.Collectors;

/**
 * A
 */
public final class Demo {

    /**
     * A
     */
    public Demo() {
    }

    /**
     * A
     * @param args a
     */
    public static void main(String[] args) {

        record Point(int x, int y){}

        StructLayout pointLayout = RecordMapper.nominalLayout(Point.class);
        System.out.println(pointLayout);
        // [i32(x)i32(y)](java.lang.foreign.mapper.Demo$1Point)

        record Line(Point begin, Point end){}

        var lineLayout = RecordMapper.nominalLayout(Line.class);
        System.out.println(lineLayout);
        // [[i32(x)i32(y)](begin)[i32(x)i32(y)](end)](java.lang.foreign.mapper.Demo$1Line)

        var pointExtractor = RecordMapper.recordMapper(Point.class, pointLayout);

        MemorySegment segment = MemorySegment.ofArray(new int[]{3, 4,   6, 0,   0, 0});

        Point point = pointExtractor.apply(segment);
        System.out.println(point);
        // Point[x=3, y=4]

        // Todo: Consider overload with offset?

        System.out.println(pointExtractor.apply(segment.asSlice(8)));
        // Point[x=6, y=0]

        // Line line = RecordMapper.recordMapper(Line.class).apply(segment);
        // System.out.println(line);
        // soon: Line[begin=Point[x=3, y=4], end=Point[x=6, y=0]]

        segment.elements(pointLayout)
                .map(pointExtractor)
                .forEach(System.out::println);

        // Point[x=3, y=4]
        // Point[x=6, y=0]
        // Point[x=0, y=0]

        record MyCustomPoint(int x, int y) {
            @Override
            public String toString() {
                return String.format("(%d, %d)", x, y);
            }

            double origoDistance() {
                return Math.sqrt((double) x * x + (double) y * y);
            }
        }
        segment.elements(pointLayout)
                .map(RecordMapper.recordMapper(MyCustomPoint.class, pointLayout))
                .forEach(System.out::println);
        // (3, 4)
        // (6, 0)
        // (0, 0)

        // Example with hexdump
        // Micro service

    }
}
