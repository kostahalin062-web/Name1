package ru.para.budilnik;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Evening notification "Tomorrow the first lesson is at 8:00 — set the alarms?".
 * The Clock app can ring only within 24 hours, so alarms are set the evening before.
 * Uses an inexact daily alarm: a few minutes late is fine for a reminder.
 */
public class Reminder extends BroadcastReceiver {
    static final String ACTION = "ru.para.budilnik.REMINDER";
    static final String CHANNEL = "reminder";
    static final int NOTIF_ID = 7;

    @Override
    public void onReceive(Context c, Intent intent) {
        if (ACTION.equals(intent.getAction())) maybeNotify(c);
        schedule(c);
    }

    /** Next time the reminder fires (today at that time if still ahead, else tomorrow). */
    static LocalDateTime nextTime(LocalDateTime now, int minuteOfDay) {
        LocalDateTime t = now.toLocalDate().atTime(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60));
        return t.isAfter(now) ? t : t.plusDays(1);
    }

    private static PendingIntent pi(Context c) {
        Intent i = new Intent(c, Reminder.class).setAction(ACTION);
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** (Re-)arms the daily reminder or cancels it when switched off in the settings. */
    static void schedule(Context c) {
        Context app = c.getApplicationContext();
        AlarmManager am = app.getSystemService(AlarmManager.class);
        if (am == null) return;
        Store store = new Store(app);
        PendingIntent p = pi(app);
        am.cancel(p);
        if (!store.reminderOn()) return;
        LocalDateTime t = nextTime(LocalDateTime.now(), store.reminderMin());
        long ms = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, p);
    }

    private static void maybeNotify(Context c) {
        Store store = new Store(c);
        if (!store.reminderOn()) return;
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        if (store.seriesFor(tomorrow.toString()) != null) return; // already set
        Schedule s = Schedule.loadLocal(c);
        Schedule.Lesson l = s.firstByDay.get(tomorrow);
        if (l == null) return; // no lessons — nothing to remind about
        LocalDateTime wake = Plan.wakeFor(s, store, tomorrow);
        LocalDateTime from = wake.minusMinutes(store.spanMin());
        String title = "Завтра пара в " + Plan.hm(LocalDateTime.of(tomorrow, l.start)) + " — поставить будильники?";
        String text = Plan.hm(from) + "–" + Plan.hm(wake) + ", каждые " + store.intervalMin() + " мин · " + l.subject;
        show(c, tomorrow, title, text);
    }

    static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Напоминание вечером", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Вечером предлагает поставить будильники на завтра");
        nm.createNotificationChannel(ch);
    }

    static void show(Context c, LocalDate day, String title, String text) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        ensureChannel(c);
        PendingIntent open = PendingIntent.getActivity(c, 20,
                new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent set = PendingIntent.getActivity(c, 21,
                new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(MainActivity.EXTRA_AUTO_SET, day.toString()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .addAction(new Notification.Action.Builder(null, "Поставить", set).build())
                .build();
        try {
            nm.notify(NOTIF_ID, n);
        } catch (SecurityException ignored) { } // notifications not allowed
    }

    static void cancelNotification(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(NOTIF_ID);
    }
}
