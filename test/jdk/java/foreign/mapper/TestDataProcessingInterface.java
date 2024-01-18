/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @run junit/othervm --enable-native-access=ALL-UNNAMED TestDataProcessingInterface
 */

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.mapper.SegmentMapper;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.DoubleSummaryStatistics;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.foreign.ValueLayout.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class TestDataProcessingInterface {

    private static final String NL = System.lineSeparator();
    private static final double EPSILON = 1e-6;

    // See TestDataProcessingRecord first

    /*
     * The data in this demo reflects various measurements and is organized
     * in a table like this:
     *
     * +-----------+-------+-------+-------+
     * |       date|      a|      b|      c|
     * +-----------+-------+-------+-------+
     * |   20240101| 0.7276| 0.0547| 0.6832|
     * |   20240102| 0.0479| 0.3087| 0.9421|
     * |   20240103| 0.2771| 0.7077| 0.6655|
     * ...
     * |   20241231| 0.5021| 0.7753| 0.4005|
     * +-----------+-------+-------+-------+
     *
     * The date column is represented by an `int` (e.g. 20240101) whereas
     * the other data columns are represented with a number of `float` values.
     * The columns are stored in a large table in binary form in a MemorySegment:
     *
     * e5 d6 34 01  9d 41 3a 3f  a0 e8 5f 3d  bb e7 2e 3f
     * e6 d6 34 01  00 5c 44 3d  78 10 9e 3e  bb 2b 71 3f
     * e7 d6 34 01  3a dd 8d 3e  85 2c 35 3f  6a 61 2a 3f
     * ...
     *
     * There is also another table with just two columns:
     * +-----------+-------+
     * |       date|      d|
     * +-----------+-------+
     * |   20240101| 0.7276|
     * |   20240103| 0.0547|
     * |   20240105| 0.6832|
     * ...
     * +-----------+-------+
     *
     * As can be seen, this later smaller table does not have measurements for all days
     * but only for every second day.
     *
     */

    // 2024-01-01
    private static final Instant FIRST_DAY = Instant.parse("2024-01-01T12:00:00Z");

    // There are 366 days in 2024
    private static final long DAYS = 366;

    // Here is a layout that corresponds to the measurement data layout
    private static final GroupLayout MEASUREMENT_LAYOUT = MemoryLayout.structLayout(
            JAVA_INT.withName("date"),
            JAVA_FLOAT.withName("a"),
            JAVA_FLOAT.withName("b"),
            JAVA_FLOAT.withName("c")
    );

    // This might as well be a memory mapped file
    private static final MemorySegment SEGMENT = initMeasurements();


    // Tests

    // Todo: Avoid creating slices of the original segment

    // Common interface for both tables
    public interface Date {
        int date();
        void date(int date);
    }

    public interface Measurement extends Date {
        float a();
        float b();
        float c();

        void a(float a);
        void b(float b);
        void c(float c);
    }

    private static final SegmentMapper<Measurement> MAPPER =
            SegmentMapper.ofInterface(MethodHandles.lookup(), Measurement.class, MEASUREMENT_LAYOUT);


    @Test
    void dumpFirstTen() {
        MAPPER.stream(SEGMENT)      // Stream<Measurement>
                .limit(10)  // Stream<Measurement>
                .forEachOrdered(this::println);

        String expected = """
                Measurement[date()=20240101, a()=0.7275637, b()=0.054665208, c()=0.6832234]
                Measurement[date()=20240102, a()=0.0479393, b()=0.3087194, c()=0.9420735]
                Measurement[date()=20240103, a()=0.27707845, b()=0.70771056, c()=0.6655489]
                Measurement[date()=20240104, a()=0.09132457, b()=0.9033722, c()=0.45125717]
                Measurement[date()=20240105, a()=0.36878288, b()=0.38164306, c()=0.275748]
                Measurement[date()=20240106, a()=0.69042575, b()=0.46365356, c()=0.76209015]
                Measurement[date()=20240107, a()=0.78290176, b()=0.99817854, c()=0.91932774]
                Measurement[date()=20240108, a()=0.15195823, b()=0.43649095, c()=0.4397998]
                Measurement[date()=20240109, a()=0.7499061, b()=0.93063116, c()=0.38656682]
                Measurement[date()=20240110, a()=0.7982866, b()=0.17737848, c()=0.15054744]
                """;

        // The order is unspecified by the layout
        assertEquals(expected, lines());
    }

    @Test
    void printHead() {
        drawTable(Measurement.class, () ->
                MAPPER.stream(SEGMENT)      // Stream<Measurement>
                        .limit(10)  // Stream<Measurement>
        );

        String expected = """
                +-----------+-------+-------+-------+
                |       date|      a|      b|      c|
                +-----------+-------+-------+-------+
                |   20240101| 0.7276| 0.0547| 0.6832|
                |   20240102| 0.0479| 0.3087| 0.9421|
                |   20240103| 0.2771| 0.7077| 0.6655|
                |   20240104| 0.0913| 0.9034| 0.4513|
                |   20240105| 0.3688| 0.3816| 0.2757|
                |   20240106| 0.6904| 0.4637| 0.7621|
                |   20240107| 0.7829| 0.9982| 0.9193|
                |   20240108| 0.1520| 0.4365| 0.4398|
                |   20240109| 0.7499| 0.9306| 0.3866|
                |   20240110| 0.7983| 0.1774| 0.1505|
                +-----------+-------+-------+-------+
                """;

        assertEquals(expected, lines());
    }

    @Test
    void printTail() {
        // Better than using Stream::skip
        var slice = SEGMENT.asSlice(MEASUREMENT_LAYOUT.scale(0, DAYS - 10));

        drawTable(Measurement.class, () ->
                MAPPER.stream(slice)); // Stream<Measurement>

        String expected = """
                +-----------+-------+-------+-------+
                |       date|      a|      b|      c|
                +-----------+-------+-------+-------+
                |   20241222| 0.6121| 0.1197| 0.6491|
                |   20241223| 0.7344| 0.6947| 0.6889|
                |   20241224| 0.8151| 0.4915| 0.3905|
                |   20241225| 0.3028| 0.9186| 0.9861|
                |   20241226| 0.3850| 0.5947| 0.2771|
                |   20241227| 0.6848| 0.6436| 0.7479|
                |   20241228| 0.8556| 0.7485| 0.0617|
                |   20241229| 0.0978| 0.1765| 0.0334|
                |   20241230| 0.7185| 0.4277| 0.1412|
                |   20241231| 0.5021| 0.7753| 0.4005|
                +-----------+-------+-------+-------+
                """;

        assertEquals(expected, lines());
    }

    @Test
    void sumA() {
        double sumA = MAPPER.stream(SEGMENT)  // Stream<Measurement>
                .mapToDouble(Measurement::a)  // DoubleStream
                .sum();

        assertEquals(177.40841281414032, sumA, EPSILON);
    }

    @Test
    void statisticsB() {
        DoubleSummaryStatistics statB = MAPPER.stream(SEGMENT) // Stream<Measurement>
                .mapToDouble(Measurement::b)                   // DoubleStream
                .summaryStatistics();

        assertEquals(366, statB.getCount());
        assertEquals(192.677586, statB.getSum(), EPSILON);
        assertEquals(0.006300, statB.getMin(), EPSILON);
        assertEquals(0.526441, statB.getAverage(), EPSILON);
        assertEquals(0.998922, statB.getMax(), EPSILON);
        // statB = DoubleSummaryStatistics{count=366, sum=192.677586, min=0.006300, average=0.526441, max=0.998922}
    }

    @Test
    void paging() {
        drawTable(Measurement.class, () ->
                MAPPER.page(SEGMENT, 20, 3));

        String expected = """
                +-----------+-------+-------+-------+
                |       date|      a|      b|      c|
                +-----------+-------+-------+-------+
                |   20240301| 0.3151| 0.7959| 0.4427|
                |   20240302| 0.1254| 0.0965| 0.3965|
                |   20240303| 0.7452| 0.8860| 0.1709|
                |   20240304| 0.9127| 0.8054| 0.2901|
                |   20240305| 0.1398| 0.9387| 0.0929|
                |   20240306| 0.2974| 0.0270| 0.6605|
                |   20240307| 0.5483| 0.2539| 0.2822|
                |   20240308| 0.4050| 0.9675| 0.9853|
                |   20240309| 0.9045| 0.8986| 0.3439|
                |   20240310| 0.7296| 0.0141| 0.0090|
                |   20240311| 0.8096| 0.0171| 0.5215|
                |   20240312| 0.3031| 0.8352| 0.5599|
                |   20240313| 0.5835| 0.4245| 0.6882|
                |   20240314| 0.8664| 0.5564| 0.4803|
                |   20240315| 0.1158| 0.3107| 0.5885|
                |   20240316| 0.0762| 0.8951| 0.8887|
                |   20240317| 0.6265| 0.6611| 0.3826|
                |   20240318| 0.7062| 0.9036| 0.5906|
                |   20240319| 0.8337| 0.1468| 0.2602|
                |   20240320| 0.0076| 0.9622| 0.6406|
                +-----------+-------+-------+-------+
                """;

        assertEquals(expected, lines());
    }

    @Test
    void partitioning() {

        // Partitions even and odd days and calculates the average Measurement::a

        Map<Boolean, Double> evenOddDayAverageA = MAPPER.stream(SEGMENT)
                .collect(Collectors.partitioningBy(m -> m.date() % 2 == 0,
                        Collectors.averagingDouble(Measurement::a)));

        double oddDayAverageA = evenOddDayAverageA.get(false);
        double evenDayAverageA = evenOddDayAverageA.get(true);

        assertEquals(0.4854658880335762d, oddDayAverageA, EPSILON);
        assertEquals(0.48394576397688027d, evenDayAverageA, EPSILON);
    }

    @Test
    void grouping() {

        // For each month, computes summary statistics for Measurement:a

        Map<Integer, DoubleSummaryStatistics> groups = MAPPER.stream(SEGMENT)
                .collect(Collectors.groupingBy(
                        (Measurement m) -> month(m.date()),
                        Collectors.summarizingDouble(Measurement::a)));

        DoubleSummaryStatistics march = groups.get(3);

        assertEquals(31, march.getCount());
        assertEquals(15.841643, march.getSum(), EPSILON);
        assertEquals(0.007594, march.getMin(), EPSILON);
        assertEquals(0.511021, march.getAverage(), EPSILON);
        assertEquals(0.912659, march.getMax(), EPSILON);

        // march = DoubleSummaryStatistics{count=31, sum=15.841643, min=0.007594, average=0.511021, max=0.912659}
    }

    static int month(int date) {
        return (date / 100) % 100;
    }

    record PivotRow(int month, long r0to25, long r25to50, long r50to75, long r75to100) {
    }

    @Test
    void pivot() {

        // Creates a pivot table where Measurement::a is grouped into four different categories
        // [0, 25%), [25%, 50%), [50%, 75%) and [75%, 100%) and we count the occurrences in
        // each bracket for each distinct month

        Map<Integer, Map<Integer, Long>> pivot = MAPPER.stream(SEGMENT)
                .collect(Collectors.groupingBy(m -> month(m.date()),
                        Collectors.groupingBy(m -> (int) (m.a() * 4), Collectors.counting())));

        drawTable(PivotRow.class, () -> pivot.entrySet().stream()
                .map(e -> {
                    var map = e.getValue();
                    return new PivotRow(e.getKey(),
                            map.getOrDefault(0, 0L),
                            map.getOrDefault(1, 0L),
                            map.getOrDefault(2, 0L),
                            map.getOrDefault(3, 0L));
                }));

        String expected = """
                +-----------+-------------+-------------+-------------+-------------+
                |      month|       r0to25|      r25to50|      r50to75|     r75to100|
                +-----------+-------------+-------------+-------------+-------------+
                |          1|           10|           10|            4|            4|
                |          2|            3|            9|            8|            8|
                |          3|            7|            8|            8|            8|
                |          4|            8|            9|            6|            6|
                |          5|            5|            9|            6|            6|
                |          6|            9|            9|            3|            3|
                |          7|           10|            8|            4|            4|
                |          8|            9|            6|            5|            5|
                |          9|            6|            9|            7|            7|
                |         10|            7|            8|            7|            7|
                |         11|            6|           13|            6|            6|
                |         12|            6|            9|            7|            7|
                +-----------+-------------+-------------+-------------+-------------+
                """;

        assertEquals(expected, lines());
    }

    // Here, we introduce the concept of a smaller table that we intend to join with
    // the larger table
    private static final GroupLayout SMALL_MEASUREMENT_LAYOUT = MemoryLayout.structLayout(
            JAVA_INT.withName("date"),
            JAVA_FLOAT.withName("d")
    );

    public interface SmallMeasurement extends Date {
        float d();
        void d(float d);
    }

    private static final SegmentMapper<SmallMeasurement> SMALL_MAPPER =
            SegmentMapper.ofInterface(MethodHandles.lookup(), SmallMeasurement.class, SMALL_MEASUREMENT_LAYOUT);

    private static final MemorySegment SMALL_SEGMENT = initSmallMeasurements();

    @Test
    void dumpSmallRaw() {
        HexFormat f = HexFormat.ofDelimiter(" ");
        for (int i = 0; i < 10; i++) {
            byte[] segmentsAsArray = SMALL_SEGMENT
                    .asSlice(SMALL_MEASUREMENT_LAYOUT.scale(0, i), SMALL_MEASUREMENT_LAYOUT.byteSize())
                    .toArray(JAVA_BYTE);
            println(f.formatHex(segmentsAsArray));
        }

        String expected = """
                e5 d6 34 01 9d 41 3a 3f
                e7 d6 34 01 a0 e8 5f 3d
                e9 d6 34 01 bb e7 2e 3f
                eb d6 34 01 00 5c 44 3d
                ed d6 34 01 78 10 9e 3e
                ef d6 34 01 bb 2b 71 3f
                f1 d6 34 01 3a dd 8d 3e
                f3 d6 34 01 85 2c 35 3f
                f5 d6 34 01 6a 61 2a 3f
                f7 d6 34 01 60 08 bb 3d
                """;
            /* |   date    |     d     | */

        assertEquals(expected, lines());
    }

    @Test
    void dumpSmallFirstTen() {
        SMALL_MAPPER.stream(SMALL_SEGMENT)   // Stream<SmallMeasurement>
                .limit(10)           // Stream<SmallMeasurement>
                .forEachOrdered(this::println);

        String expected = """
                SmallMeasurement[date()=20240101, d()=0.7275637]
                SmallMeasurement[date()=20240103, d()=0.054665208]
                SmallMeasurement[date()=20240105, d()=0.6832234]
                SmallMeasurement[date()=20240107, d()=0.0479393]
                SmallMeasurement[date()=20240109, d()=0.3087194]
                SmallMeasurement[date()=20240111, d()=0.9420735]
                SmallMeasurement[date()=20240113, d()=0.27707845]
                SmallMeasurement[date()=20240115, d()=0.70771056]
                SmallMeasurement[date()=20240117, d()=0.6655489]
                SmallMeasurement[date()=20240119, d()=0.09132457]
                """;

        assertEquals(expected, lines());
    }

    @Test
    void printSmallHead() {
        drawTable(SmallMeasurement.class, () ->
                SMALL_MAPPER.stream(SMALL_SEGMENT)  // Stream<SmallMeasurement>
                        .limit(10)          // Stream<SmallMeasurement>
        );

        String expected = """
                +-----------+-------+
                |       date|      d|
                +-----------+-------+
                |   20240101| 0.7276|
                |   20240103| 0.0547|
                |   20240105| 0.6832|
                |   20240107| 0.0479|
                |   20240109| 0.3087|
                |   20240111| 0.9421|
                |   20240113| 0.2771|
                |   20240115| 0.7077|
                |   20240117| 0.6655|
                |   20240119| 0.0913|
                +-----------+-------+
                """;

        assertEquals(expected, lines());
    }

    // Joins

    // Table of measurements
    static Stream<Measurement> measurements() {
        return MAPPER.stream(SEGMENT);
    }

    // Table of small measurements
    static Stream<SmallMeasurement> smallMeasurements() {
        return SMALL_MAPPER.stream(SMALL_SEGMENT);
    }

    record Both(Measurement measurement, SmallMeasurement smallMeasurement) {
    }

    @Test
    void join() {

        Stream<Both> boths = join(
                TestDataProcessingInterface::measurements,
                TestDataProcessingInterface::smallMeasurements,
                Both::new,
                both -> both.measurement.date() == both.smallMeasurement.date()
        );

        drawTable(Both.class, () -> boths
                .limit(10)
        );


        String expected = """
                +-----------------------------------+-------------------+
                |                        measurement|   smallMeasurement|
                +-----------------------------------+-------------------+
                |   20240101| 0.7276| 0.0547| 0.6832|   20240101| 0.7276|
                |   20240103| 0.2771| 0.7077| 0.6655|   20240103| 0.0547|
                |   20240105| 0.3688| 0.3816| 0.2757|   20240105| 0.6832|
                |   20240107| 0.7829| 0.9982| 0.9193|   20240107| 0.0479|
                |   20240109| 0.7499| 0.9306| 0.3866|   20240109| 0.3087|
                |   20240111| 0.5943| 0.3383| 0.2098|   20240111| 0.9421|
                |   20240113| 0.1722| 0.1587| 0.5874|   20240113| 0.2771|
                |   20240115| 0.5710| 0.0803| 0.5800|   20240115| 0.7077|
                |   20240117| 0.0314| 0.3165| 0.3579|   20240117| 0.6655|
                |   20240119| 0.4177| 0.7695| 0.9740|   20240119| 0.0913|
                +-----------------------------------+-------------------+
                """;

        assertEquals(expected, lines());
    }

    interface Selection {
        int date();
        float a();
        float d();
    }

    @Test
    void joinWithProjection() {

        Stream<Both> boths = join(
                TestDataProcessingInterface::measurements,
                TestDataProcessingInterface::smallMeasurements,
                Both::new,
                both -> both.measurement.date() == both.smallMeasurement.date()
        );

        drawTable(Selection.class, () -> boths
                .limit(10)
                .map(both -> new Selection() {
                    @Override public int date() { return both.measurement().date(); }
                    @Override public float a()  { return both.measurement().a(); }
                    @Override public float d()  { return both.smallMeasurement().d(); }
                })
        );

        String expected = """
            +-----------+-------+-------+
            |       date|      a|      d|
            +-----------+-------+-------+
            |   20240101| 0.7276| 0.7276|
            |   20240103| 0.2771| 0.0547|
            |   20240105| 0.3688| 0.6832|
            |   20240107| 0.7829| 0.0479|
            |   20240109| 0.7499| 0.3087|
            |   20240111| 0.5943| 0.9421|
            |   20240113| 0.1722| 0.2771|
            |   20240115| 0.5710| 0.7077|
            |   20240117| 0.0314| 0.6655|
            |   20240119| 0.4177| 0.0913|
            +-----------+-------+-------+
            """;

        assertEquals(expected, lines());
    }


    // Support methods

    // Produces the cartesian product first x second and then
    // filters that is profoundly inefficient
    static <R, T, U> Stream<R> join(Supplier<Stream<T>> first,
                                    Supplier<Stream<U>> second,
                                    BiFunction<T, U, R> mapper,
                                    Predicate<R> criteria) {

        return first.get()
                .flatMap(f -> second.get()
                        .map(s -> mapper.apply(f, s)))
                .filter(criteria);
    }

    // Initialization of segments

    static MemorySegment initMeasurements() {
        var mapper = SegmentMapper.ofInterface(MethodHandles.lookup(), Measurement.class, MEASUREMENT_LAYOUT);
        MemorySegment segment = Arena.ofAuto().allocate(MEASUREMENT_LAYOUT, DAYS);
        Random rnd = new Random(42);
        for (int i = 0; i < DAYS; i++) {
            Instant day = FIRST_DAY.plusSeconds(24L * 3600 * i);
            int date = Integer.parseInt(day.toString().substring(0, 10).replace("-", ""));
            Measurement m = mapper.getAtIndex(segment, i);
            m.date(date);
            m.a(rnd.nextFloat());
            m.b(rnd.nextFloat());
            m.c(rnd.nextFloat());
        }
        return segment;
    }

    static MemorySegment initSmallMeasurements() {
        MemorySegment segment = Arena.ofAuto().allocate(SMALL_MEASUREMENT_LAYOUT, DAYS / 2);
        Random rnd = new Random(42);
        for (int i = 0; i < DAYS / 2; i++) {
            Instant day = FIRST_DAY.plusSeconds(24L * 3600 * i * 2);
            int date = Integer.parseInt(day.toString().substring(0, 10).replace("-", ""));
            SmallMeasurement m = SMALL_MAPPER.getAtIndex(segment, i);
            m.date(date);
            m.d(rnd.nextFloat());
        }
        return segment;
    }

    // Enables capturing of output for the tests

    private StringBuilder sb;

    @BeforeEach
    void setup() {
        sb = new StringBuilder();
    }

    private void println(Object line) {
        sb.append(line).append(NL);
    }

    private String lines() {
        return sb.toString();
    }

    // Utility methods for drawing

    <T> void drawTable(Class<T> type,
                       Supplier<Stream<T>> rowSupplier) {
        drawTable(this::println, type, rowSupplier);
    }

    static <T> void drawTable(Consumer<String> consumer,
                              Class<T> type,
                              /* Function<Class<?>, List<String>> orderLookup,*/
                              Supplier<Stream<T>> rowSupplier) {
        consumer.accept(delimiter(type));
        consumer.accept(header(type));
        consumer.accept(delimiter(type));
        rowSupplier.get()
                .map(TestDataProcessingInterface::asLine)
                .forEachOrdered(consumer);
        consumer.accept(delimiter(type));
    }

    static <T> String delimiter(Class<T> type) {
        return delimiters(type)
                .collect(Collectors.joining("+", "+", "+"));
    }

    static <T> String header(Class<T> type) {
        int[] columWidths = columWidths(type).toArray();
        List<String> names = headers(type).toList();
        return IntStream.range(0, names.size())
                .mapToObj(i -> " ".repeat(columWidths[i] - names.get(i).length()) + names.get(i))
                .collect(Collectors.joining("|", "|", "|"));
    }

    static <T> String asLine(T line) {
        return asLine0(line) + "|";
    }

    static <T> String asLine0(T line) {
        return switch (line) {
            // This is a bit of cheating. However, it would be possible to derive a printer function
            // from a record class.
            case Measurement m       -> String.format("|%11d| %.4f| %.4f| %.4f", m.date(), m.a(), m.b(), m.c());
            case SmallMeasurement sm -> String.format("|%11d| %.4f", sm.date(), sm.d());
            case Selection s         -> String.format("|%11d| %.4f| %.4f", s.date(), s.a(), s.d());
            case Both b              -> asLine0(b.measurement()) + asLine0(b.smallMeasurement());
            case PivotRow p          -> String.format("|%11d| %12d| %12d| %12d| %12d", p.month(), p.r0to25(), p.r25to50(), p.r75to100(), p.r75to100());
            default -> line.toString();
        };
    }

    static <T> Stream<String> delimiters(Class<T> type) {
        return columWidths(type)
                .mapToObj("-"::repeat);
    }

    static <T> IntStream columWidths(Class<T> type) {
        return getters(type)
                .map(Method::getReturnType)
                .map(Class::getSimpleName)
                .mapToInt(n -> switch(n) {
                    case "int" -> 11;
                    case "long" -> 13;
                    case "float" -> 7;
                    case "Measurement" -> 35;
                    case "SmallMeasurement" -> 19;
                    default -> 20;
                });
    }

    private static final Map<Class<?>, List<String>> METHOD_ORDERS =
            Map.of(Measurement.class, (List.of("date", "a", "b", "c")),
                    SmallMeasurement.class, List.of("date", "d"),
                    Both.class, List.of("measurement", "smallMeasurement"),
                    PivotRow.class, List.of("month", "r0to25", "r25to50", "r50to75", "r75to100"),
                    Selection.class, List.of("date", "a", "d")
            );

    static <T> Stream<Method> getters(Class<T> type) {
        return METHOD_ORDERS.get(type).stream()
                .flatMap(n -> Arrays.stream(type.getMethods()).filter(m -> m.getName().equals(n)))
                .filter(m -> Modifier.isAbstract(m.getModifiers()) || type.isRecord())
                .filter(m -> m.getReturnType() != void.class)
                .filter(m -> m.getParameterCount() == 0);
    }

    static <T> Stream<String> headers(Class<T> type) {
        return getters(type)
                .map(Method::getName);
    }

}
