package ru.para.budilnik;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Timetable from the ASU (АлтГУ) .ics export — the same file the "Моё ДЗ" app uses.
 * Times are wall-clock times of the university (Barnaul), used as phone-local times.
 */
final class Schedule {

    static final class Lesson {
        LocalDate date;
        LocalTime start;
        String subject;
        String type;
        String room;
    }

    private static final Map<String, String> TYPES = new HashMap<>();
    static {
        TYPES.put("лек.", "лекция");
        TYPES.put("пр.з.", "практика");
        TYPES.put("лаб.", "лабораторная");
        TYPES.put("сем.", "семинар");
        TYPES.put("конс.", "консультация");
        TYPES.put("зач.", "зачёт");
        TYPES.put("экз.", "экзамен");
    }

    private static final Pattern DT = Pattern.compile("^(\\d{4})(\\d{2})(\\d{2})T(\\d{2})(\\d{2})");
    private static final Pattern TIME_PREFIX = Pattern.compile("^\\d{1,2}:\\d{2}\\s+");
    private static final Pattern TYPE_PREFIX = Pattern.compile("^([а-яёА-ЯЁ.]+\\.)\\s+");

    /** The earliest lesson of every day. */
    final TreeMap<LocalDate, Lesson> firstByDay = new TreeMap<>();
    final String calName;

    private Schedule(List<Lesson> lessons, String calName) {
        this.calName = calName;
        for (Lesson l : lessons) {
            Lesson cur = firstByDay.get(l.date);
            if (cur == null || l.start.isBefore(cur.start)) firstByDay.put(l.date, l);
        }
    }

    boolean isEmpty() { return firstByDay.isEmpty(); }

    static Schedule parse(String text) {
        String unfolded = text.replace("\r\n", "\n").replace("\r", "\n").replaceAll("\n[ \t]", "");
        List<Lesson> lessons = new ArrayList<>();
        String calName = "";
        Map<String, String> ev = null;
        for (String line : unfolded.split("\n")) {
            if (line.equals("BEGIN:VEVENT")) { ev = new HashMap<>(); continue; }
            if (line.equals("END:VEVENT")) {
                if (ev != null) {
                    Lesson l = toLesson(ev);
                    if (l != null) lessons.add(l);
                }
                ev = null;
                continue;
            }
            int idx = line.indexOf(':');
            if (idx < 0) continue;
            String name = line.substring(0, idx).split(";")[0].toUpperCase();
            String value = line.substring(idx + 1);
            if (ev == null) {
                if (name.equals("X-WR-CALNAME")) calName = unescape(value);
            } else {
                ev.put(name, name.startsWith("DT") ? value : unescape(value));
            }
        }
        return new Schedule(lessons, calName);
    }

    private static String unescape(String s) {
        return s.replaceAll("\\\\[nN]", "\n").replaceAll("\\\\([,;\\\\])", "$1");
    }

    private static Lesson toLesson(Map<String, String> ev) {
        String dt = ev.get("DTSTART");
        if (dt == null) return null;
        if ("CANCELLED".equalsIgnoreCase(String.valueOf(ev.get("STATUS")).trim())) return null;
        Matcher m = DT.matcher(dt);
        if (!m.find()) return null;
        Lesson l = new Lesson();
        try {
            l.date = LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            l.start = LocalTime.of(Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)));
        } catch (RuntimeException e) {
            return null;
        }
        l.room = ev.containsKey("LOCATION") ? ev.get("LOCATION").trim() : "";
        String summary = ev.containsKey("SUMMARY") ? ev.get("SUMMARY").trim() : "";
        summary = TIME_PREFIX.matcher(summary).replaceFirst("");
        Matcher tm = TYPE_PREFIX.matcher(summary);
        String typeShort = "";
        if (tm.find()) {
            typeShort = tm.group(1);
            summary = summary.substring(tm.end());
        }
        if (!l.room.isEmpty() && summary.endsWith("(" + l.room + ")")) {
            summary = summary.substring(0, summary.length() - l.room.length() - 2).trim();
        }
        l.subject = summary;
        String t = TYPES.get(typeShort);
        l.type = t != null ? t : typeShort.replaceAll("\\.$", "");
        return l;
    }

    // ---------- loading ----------

    private static File cacheFile(Context c) { return new File(c.getFilesDir(), "schedule.ics"); }

    /** Cached copy, or the copy bundled into the APK. Never null. */
    static Schedule loadLocal(Context c) {
        File f = cacheFile(c);
        if (f.exists()) {
            try (InputStream in = new java.io.FileInputStream(f)) {
                Schedule s = parse(readAll(in));
                if (!s.isEmpty()) return s;
            } catch (IOException ignored) { }
        }
        try (InputStream in = c.getAssets().open("schedule.ics")) {
            return parse(readAll(in));
        } catch (IOException e) {
            return parse("");
        }
    }

    /**
     * Downloads the timetable (user URL first, then the university site) and caches it.
     * Runs on a background thread. Returns null when every source failed.
     */
    static Schedule download(Context c, Store store) {
        String[] urls = {store.url(), Store.DEFAULT_URL, Store.ASU_URL};
        for (String u : urls) {
            if (u == null || u.trim().isEmpty()) continue;
            try {
                String text = fetch(u.trim());
                if (!text.contains("BEGIN:VCALENDAR")) continue;
                Schedule s = parse(text);
                if (s.isEmpty() && !text.contains("BEGIN:VEVENT")) continue;
                saveCache(c, text);
                store.setLastUpdate(System.currentTimeMillis());
                return s;
            } catch (IOException | RuntimeException ignored) { }
        }
        return null;
    }

    /** Saves an .ics the user picked by hand. Returns the parsed schedule or null if it is not a timetable. */
    static Schedule importText(Context c, Store store, String text) {
        if (text == null || !text.contains("BEGIN:VCALENDAR")) return null;
        Schedule s = parse(text);
        try {
            saveCache(c, text);
        } catch (IOException ignored) { }
        store.setLastUpdate(System.currentTimeMillis());
        return s;
    }

    private static void saveCache(Context c, String text) throws IOException {
        File tmp = new File(c.getFilesDir(), "schedule.ics.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        if (!tmp.renameTo(cacheFile(c))) throw new IOException("rename failed");
    }

    private static String fetch(String u) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(u).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(20000);
        con.setInstanceFollowRedirects(true);
        con.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Budilnik");
        con.setRequestProperty("Cache-Control", "no-cache");
        try {
            if (con.getResponseCode() / 100 != 2) throw new IOException("HTTP " + con.getResponseCode());
            try (InputStream in = con.getInputStream()) {
                return readAll(in);
            }
        } finally {
            con.disconnect();
        }
    }

    static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }
}
