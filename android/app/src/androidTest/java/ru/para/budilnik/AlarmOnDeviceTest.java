package ru.para.budilnik;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;

/**
 * Runs on a real Android (emulator): the alarm must actually ring at the set time,
 * play sound, show the "wake up" screen (also over the lock screen), and the buttons must work.
 */
@RunWith(AndroidJUnit4.class)
public class AlarmOnDeviceTest {
    private static final String TAG = "BudilnikTest";
    private static final String PKG = "ru.para.budilnik";

    private Instrumentation inst;
    private Context ctx;
    private UiDevice device;
    private Store store;

    @Before
    public void setUp() {
        inst = InstrumentationRegistry.getInstrumentation();
        ctx = inst.getTargetContext();
        device = UiDevice.getInstance(inst);
        store = new Store(ctx);

        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            Log.i(TAG, "full-screen allowed right after install: " + nm.canUseFullScreenIntent());
        }
        if (Build.VERSION.SDK_INT >= 33) {
            inst.getUiAutomation().grantRuntimePermission(PKG, Manifest.permission.POST_NOTIFICATIONS);
        }
        shell("appops set " + PKG + " USE_FULL_SCREEN_INTENT allow");
        shell("mkdir -p /data/local/tmp/shots");

        stopRinging();
        store.saveSeries(new ArrayList<>());
        Alarms.reschedule(ctx, 0);
        wakeUp();
    }

    @After
    public void tearDown() {
        stopRinging();
        store.saveSeries(new ArrayList<>());
        Alarms.reschedule(ctx, 0);
    }

    // ---------- tests ----------

    @Test
    public void ringsOnTimeWithSoundAndScreen_andStopAllRemovesTheRest() {
        Store.Series s = series(8, 60, 1); // now+8s, then every minute for a minute
        store.putSeries(s);
        Alarms.reschedule(ctx, 0);

        assertEquals("system alarm clock is set to the first alarm", s.start, nextSystemAlarm());
        assertFalse("does not ring early", waitFor(() -> AlarmService.ringingSeries != null, 5000));

        assertTrue("rings at the set time", waitFor(() -> s.id.equals(AlarmService.ringingSeries), 20000));
        long late = System.currentTimeMillis() - s.start;
        Log.i(TAG, "rang " + late + " ms after the set time");
        assertTrue("rings within 10 s of the set time, was " + late + " ms", late < 10000);

        assertTrue("sound is playing", waitFor(() -> AlarmService.sounding, 5000));
        assertTrue("alarm notification is shown", waitFor(this::alarmNotificationShown, 5000));
        assertTrue("\"wake up\" text is on screen", device.wait(Until.hasObject(By.textContains("ора вставать")), 10000));
        screenshot("1-alarm-unlocked");

        assertEquals("next alarm of the series is already scheduled", s.start + 60000, nextSystemAlarm());

        tap("Я встал — убрать остальные");
        assertEventually("sound stops", () -> !AlarmService.sounding && AlarmService.ringingSeries == null, 10000);
        assertTrue("remaining alarms of the series removed", store.series().isEmpty());
        assertEquals("no system alarm left", -1, nextSystemAlarm());
    }

    @Test
    public void stopSilencesButKeepsTheNextAlarm() {
        Store.Series s = series(6, 120, 2);
        store.putSeries(s);
        Alarms.reschedule(ctx, 0);
        assertTrue("rings", waitFor(() -> s.id.equals(AlarmService.ringingSeries), 25000));

        tap("Выключить");
        assertEventually("sound stops", () -> !AlarmService.sounding && AlarmService.ringingSeries == null, 10000);
        assertEquals("series stays", 1, store.series().size());
        assertEquals("next alarm in 2 minutes", s.start + 120000, nextSystemAlarm());
    }

    @Test
    public void ringsOverTheLockScreen() throws Exception {
        Store.Series s = series(12, 0, 5);
        store.putSeries(s);
        Alarms.reschedule(ctx, 0);

        device.sleep(); // screen off + locked
        assertTrue("screen is off before the alarm", waitFor(() -> !isScreenOn(), 5000));

        assertTrue("rings while locked", waitFor(() -> s.id.equals(AlarmService.ringingSeries), 30000));
        assertTrue("sound is playing", waitFor(() -> AlarmService.sounding, 5000));
        assertTrue("screen turns on by itself", waitFor(this::isScreenOn, 10000));
        assertTrue("alarm screen is shown over the lock screen",
                device.wait(Until.hasObject(By.text("Пора вставать")), 10000));
        screenshot("2-alarm-locked");

        tap("Выключить");
        assertEventually("sound stops", () -> !AlarmService.sounding, 10000);
    }

    @Test
    public void alarmsSurviveReboot() {
        Store.Series s = series(3600, 0, 5);
        store.putSeries(s);
        // Simulate what the system does after a reboot: all alarms are gone, then BOOT_COMPLETED.
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        am.cancel(android.app.PendingIntent.getBroadcast(ctx, 0,
                new Intent(ctx, AlarmReceiver.class).setAction(Alarms.ACTION_FIRE),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE));
        new BootReceiver().onReceive(ctx, new Intent(Intent.ACTION_BOOT_COMPLETED));
        assertEquals(s.start, nextSystemAlarm());
    }

    @Test
    public void mainScreenSetsAlarms() {
        Intent i = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ctx.startActivity(i);
        assertTrue("main screen opened", device.wait(Until.hasObject(By.text("⏰ Будильник на пары")), 15000));
        screenshot("3-main");

        UiObject2 set = null;
        for (int k = 0; k < 12 && set == null; k++) {
            set = device.findObject(By.text("Поставить будильники"));
            if (set == null) {
                device.swipe(device.getDisplayWidth() / 2, device.getDisplayHeight() * 3 / 4,
                        device.getDisplayWidth() / 2, device.getDisplayHeight() / 4, 20);
                device.waitForIdle(1000);
            }
        }
        if (set == null) throw new AssertionError("button \"Поставить будильники\" not found; on screen: " + visibleTexts());
        set.click();
        assertTrue("series saved", waitFor(() -> store.series().size() == 1, 5000));
        Store.Series s = store.series().get(0);
        assertTrue("in the future", s.end > System.currentTimeMillis());
        assertEquals("system alarm set to the first alarm of the series",
                s.nextAfter(System.currentTimeMillis() - 1), nextSystemAlarm());
        screenshot("4-main-after-set");
        device.pressHome();
    }

    // ---------- helpers ----------

    private Store.Series series(int inSeconds, int spanSeconds, int intervalMin) {
        Store.Series s = new Store.Series();
        s.id = "t-" + System.nanoTime();
        s.dateKey = "t" + s.id;
        s.start = System.currentTimeMillis() + inSeconds * 1000L;
        s.end = s.start + spanSeconds * 1000L;
        s.interval = intervalMin;
        s.label = "Тест";
        return s;
    }

    /**
     * Presses a button like a person would: on the alarm screen if it is open,
     * otherwise on the alarm notification.
     */
    private void tap(String text) {
        UiObject2 b = device.wait(Until.findObject(By.pkg(PKG).text(text)), 5000);
        String where = "alarm screen";
        if (b == null) {
            b = device.findObject(By.text(text));
            where = "notification";
        }
        if (b == null) throw new AssertionError("button \"" + text + "\" not found; on screen: " + visibleTexts());
        Log.i(TAG, "tap \"" + text + "\" on " + where);
        b.click();
    }

    private String visibleTexts() {
        StringBuilder b = new StringBuilder();
        for (UiObject2 o : device.findObjects(By.textContains(""))) {
            String t;
            try { t = o.getText(); } catch (RuntimeException stale) { continue; }
            if (t != null && !t.isEmpty()) b.append('[').append(t.replace('\n', ' ')).append("] ");
        }
        b.append(" | top package: ").append(device.getCurrentPackageName());
        return b.toString();
    }

    private long nextSystemAlarm() {
        AlarmManager.AlarmClockInfo info = ctx.getSystemService(AlarmManager.class).getNextAlarmClock();
        return info == null ? -1 : info.getTriggerTime();
    }

    private boolean alarmNotificationShown() {
        for (StatusBarNotification n : ctx.getSystemService(NotificationManager.class).getActiveNotifications()) {
            if (n.getId() == AlarmService.NOTIF_ID) return true;
        }
        return false;
    }

    private boolean isScreenOn() {
        return ctx.getSystemService(android.os.PowerManager.class).isInteractive();
    }

    private void stopRinging() {
        try {
            ctx.startService(new Intent(ctx, AlarmService.class).setAction(AlarmService.ACTION_STOP));
        } catch (RuntimeException ignored) { }
        waitFor(() -> !AlarmService.sounding && AlarmService.ringingSeries == null, 5000);
    }

    private void wakeUp() {
        try {
            device.wakeUp();
        } catch (Exception ignored) { }
        shell("wm dismiss-keyguard");
        SystemClock.sleep(500);
    }

    private void screenshot(String name) {
        shell("screencap -p /data/local/tmp/shots/" + name + ".png");
    }

    private String shell(String cmd) {
        ParcelFileDescriptor pfd = inst.getUiAutomation().executeShellCommand(cmd);
        try (FileInputStream in = new ParcelFileDescriptor.AutoCloseInputStream(pfd)) {
            return Schedule.readAll(in);
        } catch (IOException e) {
            return "";
        }
    }

    private void assertEventually(String what, BooleanSupplier cond, long ms) {
        if (!waitFor(cond, ms)) throw new AssertionError(what + "; on screen: " + visibleTexts());
    }

    private static boolean waitFor(BooleanSupplier cond, long ms) {
        long until = SystemClock.uptimeMillis() + ms;
        while (SystemClock.uptimeMillis() < until) {
            if (cond.getAsBoolean()) return true;
            SystemClock.sleep(200);
        }
        return cond.getAsBoolean();
    }
}
