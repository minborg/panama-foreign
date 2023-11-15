import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.mapper.SegmentMapper;

import static java.lang.foreign.ValueLayout.JAVA_INT;

public abstract class BaseTest {

    public static final GroupLayout POINT_LAYOUT = MemoryLayout.structLayout(
            JAVA_INT.withName("x"),
            JAVA_INT.withName("y"));

    public static final GroupLayout LINE_LAYOUT = MemoryLayout.structLayout(
            POINT_LAYOUT.withName("begin"),
            POINT_LAYOUT.withName("end"));

    public record Point(int x, int y){}

    public record TinyPoint(byte x, byte y){}

    public record Line(Point begin, Point end){}

    public interface PointAccessor {
        int x();
        void x(int x);
        int y();
        void y(int x);
    }

    public interface LineAccessor {
        Point begin();
        void begin(Point begin);
        Point end();
        void end(Point end);
    }

    public static final class LineBean {

        private int x;
        private int y;

        public LineBean(int x, int y) {
            this.x = x;
            this.y = y;
        }

        int x() {
            return x;
        }

        int y() {
            return y;
        }

        void x(int x) {
            this.x = x;
        }

        void y(int y) {
            this.y = y;
        }

    }

    public static final MemorySegment POINT_SEGMENT = MemorySegment.ofArray(new int[]{
                    3, 4,
                    6, 0,
                    0, 0})
            .asReadOnly();

    public static final SegmentMapper<Point> POINT_MAPPER = pointMapper();

    private static SegmentMapper<Point> pointMapper() {
        try {
            return SegmentMapper.ofRecord(Point.class, POINT_LAYOUT);
        } catch (Throwable th) {
            th.printStackTrace();
            throw th;
        }
    }

}
