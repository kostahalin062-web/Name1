package ru.para.budilnik;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Settings and the list of alarm series, kept in SharedPreferences. */
final class Store {
    static final String DEFAULT_URL = "https://kostahalin062-web.github.io/Name1/schedule.ics";
    static final String ASU_URL = "https://www.asu.ru/timetable/students/15/2129441855/?file=2129441855.ics";

    private final SharedPreferences p;

    Store(Context c) {
        p = c.getApplicationContext().getSharedPreferences("budilnik", Context.MODE_PRIVATE);
    }

    // ---------- settings ----------
    static final int DEFAULT_ROAD = 60, DEFAULT_MORNING = 30, DEFAULT_SHOWER = 60;

    int roadMin() { return p.getInt("road", DEFAULT_ROAD); }
    int morningMin() { return p.getInt("morning", DEFAULT_MORNING); }
    int showerMin() { return p.getInt("shower", DEFAULT_SHOWER); }
    boolean shower() { return p.getBoolean("showerOn", false); }
    int intervalMin() { return p.getInt("interval", 5); }
    /** How long before the "get up by" time the first alarm rings. */
    int spanMin() { return p.getInt("span", 30); }
    String url() { return p.getString("url", DEFAULT_URL); }
    long lastUpdate() { return p.getLong("lastUpdate", 0); }

    void setRoadMin(int v) { p.edit().putInt("road", v).apply(); }
    void setMorningMin(int v) { p.edit().putInt("morning", v).apply(); }
    void setShowerMin(int v) { p.edit().putInt("shower", v).apply(); }
    void setShower(boolean v) { p.edit().putBoolean("showerOn", v).apply(); }
    void setIntervalMin(int v) { p.edit().putInt("interval", v).apply(); }
    void setSpanMin(int v) { p.edit().putInt("span", v).apply(); }
    void setUrl(String v) { p.edit().putString("url", v).apply(); }
    void setLastUpdate(long v) { p.edit().putLong("lastUpdate", v).apply(); }

    /** Minutes to subtract from the first lesson to get the "get up by" time. */
    int prepMin() { return prep(roadMin(), morningMin(), showerMin(), shower()); }

    static int prep(int road, int morning, int showerMin, boolean showerOn) {
        return road + morning + (showerOn ? showerMin : 0);
    }

    // ---------- alarm series ----------
    static final class Series {
        String id;
        String dateKey;   // yyyy-MM-dd of the lesson day
        long start;       // first alarm, epoch ms
        long end;         // last alarm not later than this, epoch ms
        int interval;     // minutes
        String label;

        int count() { return (int) ((end - start) / (interval * 60000L)) + 1; }

        long lastTime() { return start + (count() - 1) * interval * 60000L; }

        /** First alarm of this series strictly after {@code after}, or -1. */
        long nextAfter(long after) {
            if (after < start) return start;
            long step = interval * 60000L;
            long k = (after - start) / step + 1;
            long t = start + k * step;
            return t <= end ? t : -1;
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("id", id).put("date", dateKey).put("start", start)
                    .put("end", end).put("interval", interval).put("label", label);
        }

        static Series fromJson(JSONObject o) {
            Series s = new Series();
            s.id = o.optString("id");
            s.dateKey = o.optString("date");
            s.start = o.optLong("start");
            s.end = o.optLong("end");
            s.interval = Math.max(1, o.optInt("interval", 5));
            s.label = o.optString("label");
            return s;
        }
    }

    synchronized List<Series> series() {
        List<Series> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString("series", "[]"));
            for (int i = 0; i < a.length(); i++) out.add(Series.fromJson(a.getJSONObject(i)));
        } catch (JSONException ignored) { }
        Collections.sort(out, (x, y) -> Long.compare(x.start, y.start));
        return out;
    }

    synchronized void saveSeries(List<Series> list) {
        JSONArray a = new JSONArray();
        try {
            for (Series s : list) a.put(s.toJson());
        } catch (JSONException ignored) { }
        p.edit().putString("series", a.toString()).commit();
    }

    /** Adds a series, replacing one already set for the same day. */
    synchronized void putSeries(Series s) {
        List<Series> list = series();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (s.dateKey != null && !s.dateKey.isEmpty() && s.dateKey.equals(list.get(i).dateKey)) list.remove(i);
        }
        list.add(s);
        saveSeries(list);
    }

    synchronized void removeSeries(String id) {
        List<Series> list = series();
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id.equals(id)) list.remove(i);
        saveSeries(list);
    }

    /** Drops series whose last alarm has already rung. */
    synchronized void dropFinished(long now) {
        List<Series> list = series();
        boolean changed = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).end < now - 60000L) { list.remove(i); changed = true; }
        }
        if (changed) saveSeries(list);
    }
}
