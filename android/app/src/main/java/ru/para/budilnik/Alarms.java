package ru.para.budilnik;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.List;

/**
 * Only the nearest alarm is handed to the system (as a real "alarm clock",
 * shown in the status bar); when it rings, the next one is scheduled.
 */
final class Alarms {
    static final String ACTION_FIRE = "ru.para.budilnik.FIRE";
    static final String EXTRA_SERIES = "series";
    static final String EXTRA_TIME = "time";

    private Alarms() { }

    /** Nearest alarm time after {@code after} with its series, or null. */
    static Object[] next(List<Store.Series> list, long after) {
        long best = -1;
        Store.Series bestS = null;
        for (Store.Series s : list) {
            long t = s.nextAfter(after);
            if (t > 0 && (best < 0 || t < best)) { best = t; bestS = s; }
        }
        return bestS == null ? null : new Object[]{best, bestS};
    }

    static boolean canExact(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = c.getSystemService(AlarmManager.class);
        return am != null && am.canScheduleExactAlarms();
    }

    /** Re-arms the system alarm. {@code after}: only alarms later than this time. */
    static void reschedule(Context c, long after) {
        Context app = c.getApplicationContext();
        Store store = new Store(app);
        long now = System.currentTimeMillis();
        store.dropFinished(now);
        AlarmManager am = app.getSystemService(AlarmManager.class);
        if (am == null) return;

        Intent fire = new Intent(app, AlarmReceiver.class).setAction(ACTION_FIRE);
        PendingIntent cancel = PendingIntent.getBroadcast(app, 0, fire,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (cancel != null) { am.cancel(cancel); cancel.cancel(); }

        Object[] n = next(store.series(), Math.max(after, now - 1));
        if (n == null) return;
        long t = (Long) n[0];
        Store.Series s = (Store.Series) n[1];
        fire.putExtra(EXTRA_SERIES, s.id).putExtra(EXTRA_TIME, t);
        PendingIntent op = PendingIntent.getBroadcast(app, 0, fire,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent show = PendingIntent.getActivity(app, 1,
                new Intent(app, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            if (canExact(app)) {
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(t, show), op);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, op);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, op);
        }
    }

    /** Test alarm in a few seconds, to check sound and permissions. */
    static void test(Context c, int seconds) {
        Store.Series s = new Store.Series();
        s.id = "test-" + System.currentTimeMillis();
        s.dateKey = "test";
        s.start = System.currentTimeMillis() + seconds * 1000L;
        s.end = s.start;
        s.interval = 1;
        s.label = "Проверка будильника";
        new Store(c).putSeries(s);
        reschedule(c, 0);
    }
}
