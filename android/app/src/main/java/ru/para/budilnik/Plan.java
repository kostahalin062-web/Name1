package ru.para.budilnik;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

/** "Get up by" time and alarm labels from the timetable — shared by the screen and the evening reminder. */
final class Plan {
    private Plan() { }

    static LocalDateTime wakeFor(Schedule s, Store st, LocalDate d) {
        Schedule.Lesson l = s.firstByDay.get(d);
        if (l == null) return null;
        return LocalDateTime.of(d, l.start).minusMinutes(st.prepMin());
    }

    static String hm(LocalDateTime t) {
        return String.format(Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute());
    }

    /** Short text shown by the Clock app when the alarm rings. */
    static String labelFor(Schedule s, LocalDate d) {
        Schedule.Lesson l = s.firstByDay.get(d);
        if (l == null) return "Подъём";
        return "Пара в " + hm(LocalDateTime.of(d, l.start)) + " · " + l.subject + (l.room.isEmpty() ? "" : " (" + l.room + ")");
    }
}
