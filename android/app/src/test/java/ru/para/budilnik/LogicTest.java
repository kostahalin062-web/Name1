package ru.para.budilnik;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Calculations that do not need a phone: timetable, "get up by" time, series of alarms. */
public class LogicTest {

    private static final String ICS = "BEGIN:VCALENDAR\r\n"
            + "X-WR-CALNAME:Группа К.405с9-2\\, АлтГУ\r\n"
            + "BEGIN:VEVENT\r\n"
            + "DTSTART;TZID=Asia/Krasnoyarsk:20261127T113000\r\n"
            + "LOCATION:314Н\r\n"
            + "STATUS:CONFIRMED\r\n"
            + "SUMMARY:11:30 лек. Экономика отрасли (314Н)\r\n"
            + "END:VEVENT\r\n"
            + "BEGIN:VEVENT\r\n"
            + "DTSTART;TZID=Asia/Krasnoyarsk:20261127T080000\r\n"
            + "LOCATION:212Н\r\n"
            + "STATUS:CONFIRMED\r\n"
            + "SUMMARY:08:00 пр.з. Разработка клиентских\r\n"
            + "  приложений (212Н)\r\n"
            + "END:VEVENT\r\n"
            + "BEGIN:VEVENT\r\n"
            + "DTSTART;TZID=Asia/Krasnoyarsk:20261128T080000\r\n"
            + "STATUS:CANCELLED\r\n"
            + "SUMMARY:08:00 лек. Отменённая пара\r\n"
            + "END:VEVENT\r\n"
            + "BEGIN:VEVENT\r\n"
            + "DTSTART;TZID=Asia/Krasnoyarsk:20261128T150000\r\n"
            + "LOCATION:Н\r\n"
            + "SUMMARY:15:00 лаб. Базы данных (Н)\r\n"
            + "END:VEVENT\r\n"
            + "END:VCALENDAR\r\n";

    @Test public void firstLessonOfTheDayIsTheEarliest() {
        Schedule s = Schedule.parse(ICS);
        assertEquals("Группа К.405с9-2, АлтГУ", s.calName);
        Schedule.Lesson l = s.firstByDay.get(LocalDate.of(2026, 11, 27));
        assertEquals(LocalTime.of(8, 0), l.start);
        assertEquals("Разработка клиентских приложений", l.subject);
        assertEquals("практика", l.type);
        assertEquals("212Н", l.room);
    }

    @Test public void cancelledLessonIsSkipped() {
        Schedule.Lesson l = Schedule.parse(ICS).firstByDay.get(LocalDate.of(2026, 11, 28));
        assertEquals(LocalTime.of(15, 0), l.start);
        assertEquals("Базы данных", l.subject);
        assertEquals("лабораторная", l.type);
    }

    @Test public void dayWithoutLessons() {
        assertNull(Schedule.parse(ICS).firstByDay.get(LocalDate.of(2026, 11, 29)));
    }

    @Test public void getUpOneAndAHalfHoursBeforeWithoutShower() {
        int prep = Store.prep(Store.DEFAULT_ROAD, Store.DEFAULT_MORNING, Store.DEFAULT_SHOWER, false);
        assertEquals(90, prep);
        assertEquals(LocalTime.of(6, 30), LocalTime.of(8, 0).minusMinutes(prep));
    }

    @Test public void getUpTwoAndAHalfHoursBeforeWithShower() {
        int prep = Store.prep(Store.DEFAULT_ROAD, Store.DEFAULT_MORNING, Store.DEFAULT_SHOWER, true);
        assertEquals(150, prep);
        assertEquals(LocalTime.of(5, 30), LocalTime.of(8, 0).minusMinutes(prep));
    }

    private static Store.Series series(int fromMin, int toMin, int interval) {
        Store.Series s = new Store.Series();
        s.id = "x";
        s.start = fromMin * 60000L;
        s.end = toMin * 60000L;
        s.interval = interval;
        return s;
    }

    private static List<Long> ringTimes(Store.Series s) {
        List<Long> out = new ArrayList<>();
        for (long t = s.nextAfter(-1); t >= 0; t = s.nextAfter(t)) out.add(t / 60000L);
        return out;
    }

    @Test public void everyFiveMinutesFromFiveToHalfPast() {
        Store.Series s = series(300, 330, 5); // 05:00 .. 05:30
        assertEquals(7, s.count());
        assertEquals(Arrays.asList(300L, 305L, 310L, 315L, 320L, 325L, 330L), ringTimes(s));
    }

    @Test public void lastAlarmNeverAfterEnd() {
        Store.Series s = series(300, 330, 7); // 05:00, 05:07 ... 05:28
        assertEquals(Arrays.asList(300L, 307L, 314L, 321L, 328L), ringTimes(s));
        assertEquals(328L, s.lastTime() / 60000L);
    }

    @Test public void everyIntervalFromOneToTen() {
        for (int iv = 1; iv <= 10; iv++) {
            List<Long> t = ringTimes(series(0, 60, iv));
            assertEquals("interval " + iv, 60 / iv + 1, t.size());
            for (int i = 1; i < t.size(); i++) assertEquals(iv, t.get(i) - t.get(i - 1));
        }
    }

    @Test public void singleAlarmWhenFromEqualsTo() {
        assertEquals(Arrays.asList(300L), ringTimes(series(300, 300, 5)));
    }

    @Test public void nearestAlarmAcrossSeries() {
        Store.Series a = series(300, 330, 5);
        a.id = "a";
        Store.Series b = series(302, 302, 5);
        b.id = "b";
        Object[] n = Alarms.next(Arrays.asList(a, b), 300 * 60000L);
        assertEquals(302L, (Long) n[0] / 60000L);
        assertEquals("b", ((Store.Series) n[1]).id);
        assertNull(Alarms.next(Arrays.asList(a, b), 330 * 60000L));
    }

    /** The real timetable file of the "Моё ДЗ" app parses and has lessons. */
    @Test public void realScheduleFile() throws Exception {
        File f = new File("../../docs/schedule.ics");
        if (!f.exists()) return;
        Schedule s = Schedule.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        assertTrue(s.firstByDay.size() > 0);
        for (Schedule.Lesson l : s.firstByDay.values()) {
            assertNotNull(l.start);
            assertTrue(l.subject, !l.subject.isEmpty() && !l.subject.matches("^\\d.*"));
        }
    }
}
