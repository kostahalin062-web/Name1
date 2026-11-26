package ru.para.budilnik;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.AlarmClock;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Alarms are not rung by this app: they are handed to the phone's own "Clock" app
 * (AlarmClock.ACTION_SET_ALARM). The system clock only knows "hours:minutes" and
 * rings at the next such moment, so alarms can be set only for the next 24 hours.
 */
final class Alarms {
    private Alarms() { }

    /** Ring times of a series: from, from+interval, ... not later than to. */
    static List<LocalDateTime> times(LocalDateTime from, LocalDateTime to, int interval) {
        List<LocalDateTime> out = new ArrayList<>();
        if (interval < 1 || from.isAfter(to)) return out;
        for (LocalDateTime t = from; !t.isAfter(to); t = t.plusMinutes(interval)) out.add(t);
        return out;
    }

    /** Can the system clock ring at {@code t}, i.e. is it within the next 24 hours? */
    static boolean settable(LocalDateTime t, LocalDateTime now) {
        return t.isAfter(now) && !t.isAfter(now.plusHours(24));
    }

    /**
     * Day the alarms are for: today, if today's "get up by" time is still ahead,
     * otherwise tomorrow.
     */
    static LocalDate targetDay(LocalDateTime now, LocalDateTime todayWake) {
        if (todayWake != null && todayWake.isAfter(now)) return now.toLocalDate();
        return now.toLocalDate().plusDays(1);
    }

    /** Intent for the Clock app: a one-time alarm, set silently when the Clock supports it. */
    static Intent setIntent(LocalDateTime t, String label) {
        Intent i = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, t.getHour())
                .putExtra(AlarmClock.EXTRA_MINUTES, t.getMinute())
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .putExtra(AlarmClock.EXTRA_VIBRATE, true);
        if (label != null && !label.isEmpty()) i.putExtra(AlarmClock.EXTRA_MESSAGE, label);
        return i;
    }

    static boolean hasClock(Context c) {
        return c.getPackageManager().resolveActivity(
                new Intent(AlarmClock.ACTION_SET_ALARM), PackageManager.MATCH_DEFAULT_ONLY) != null;
    }

    /** Opens the list of alarms in the Clock app. */
    static boolean openClock(Context c) {
        try {
            c.startActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
