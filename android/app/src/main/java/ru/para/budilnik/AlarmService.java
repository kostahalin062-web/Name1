package ru.para.budilnik;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Plays the alarm sound and keeps the "alarm" notification on screen. */
public class AlarmService extends Service {
    static final String ACTION_RING = "ru.para.budilnik.RING";
    static final String ACTION_STOP = "ru.para.budilnik.STOP";
    static final String ACTION_STOP_ALL = "ru.para.budilnik.STOP_ALL";
    static final String EXTRA_LABEL = "label";
    static final String CHANNEL = "alarm";
    static final int NOTIF_ID = 42;
    /** Ringing stops by itself after this long (the next alarm of the series rings anyway). */
    private static final long MAX_RING_MS = 3 * 60 * 1000L;

    /** For the full-screen activity: series of the alarm that is ringing now. */
    static volatile String ringingSeries;
    static volatile long ringingTime;
    static volatile String ringingLabel;
    /** True while the alarm sound is actually playing (used by tests). */
    static volatile boolean sounding;

    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wake;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoStop = this::stopRinging;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_RING.equals(action)) {
            String series = intent.getStringExtra(Alarms.EXTRA_SERIES);
            long time = intent.getLongExtra(Alarms.EXTRA_TIME, System.currentTimeMillis());
            String label = intent.getStringExtra(EXTRA_LABEL);
            ring(series, time, label);
            return START_NOT_STICKY;
        }
        if (ACTION_STOP_ALL.equals(action)) {
            String series = intent.getStringExtra(Alarms.EXTRA_SERIES);
            if (series != null) new Store(this).removeSeries(series);
            Alarms.reschedule(this, 0);
        }
        stopRinging();
        return START_NOT_STICKY;
    }

    private void ring(String series, long time, String label) {
        ringingSeries = series;
        ringingTime = time;
        ringingLabel = label;
        Notification n = buildNotification(this, series, time, label);
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }

        PowerManager pm = getSystemService(PowerManager.class);
        if (wake == null && pm != null) {
            wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "budilnik:ring");
            wake.acquire(MAX_RING_MS + 10000);
        }

        startSound();
        startVibration();

        // Try to open the alarm screen right away (works when the phone is unlocked
        // or the system allows it); the full-screen notification covers the rest.
        try {
            startActivity(new Intent(this, AlarmActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION));
        } catch (RuntimeException ignored) { }

        handler.removeCallbacks(autoStop);
        handler.postDelayed(autoStop, MAX_RING_MS);
    }

    private void startSound() {
        if (player != null) return;
        Uri uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepare();
            player.start();
            sounding = player.isPlaying();
        } catch (Exception e) {
            if (player != null) { player.release(); player = null; }
            try {
                player = MediaPlayer.create(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
                if (player != null) { player.setLooping(true); player.start(); sounding = player.isPlaying(); }
            } catch (Exception ignored) { player = null; }
        }
    }

    private void startVibration() {
        vibrator = getSystemService(Vibrator.class);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        long[] pattern = {0, 800, 600};
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0),
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
        } else {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
        }
    }

    private void stopRinging() {
        handler.removeCallbacks(autoStop);
        sounding = false;
        if (player != null) {
            try { player.stop(); } catch (RuntimeException ignored) { }
            player.release();
            player = null;
        }
        if (vibrator != null) { vibrator.cancel(); vibrator = null; }
        if (wake != null) {
            if (wake.isHeld()) wake.release();
            wake = null;
        }
        ringingSeries = null;
        AlarmActivity.closeIfOpen();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(NOTIF_ID + 1);
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE); else stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(autoStop);
        if (player != null) { player.release(); player = null; }
        if (vibrator != null) vibrator.cancel();
        if (wake != null && wake.isHeld()) wake.release();
        super.onDestroy();
    }

    // ---------- notification ----------

    static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Будильник", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Звонок будильника перед парами");
        ch.setSound(null, null); // the service plays the sound itself, on the alarm volume
        ch.enableVibration(false);
        ch.setBypassDnd(true);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    static Notification buildNotification(Context c, String series, long time, String label) {
        ensureChannel(c);
        String hm = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(time));
        PendingIntent full = PendingIntent.getActivity(c, 10,
                new Intent(c, AlarmActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(c, 11,
                new Intent(c, AlarmService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stopAll = PendingIntent.getService(c, 12,
                new Intent(c, AlarmService.class).setAction(ACTION_STOP_ALL).putExtra(Alarms.EXTRA_SERIES, series),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏰ " + hm + " — пора вставать")
                .setContentText(label == null ? "" : label)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(full)
                .setFullScreenIntent(full, true)
                .addAction(new Notification.Action.Builder(null, "Выключить", stop).build())
                .addAction(new Notification.Action.Builder(null, "Я встал — убрать остальные", stopAll).build());
        return b.build();
    }

    /** Used when the system refused to start the service. */
    static void showFallback(Context c, String series, long time, String label) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        Notification n = buildNotification(c, series, time, label);
        n.flags &= ~Notification.FLAG_ONGOING_EVENT;
        nm.notify(NOTIF_ID + 1, n);
    }
}
