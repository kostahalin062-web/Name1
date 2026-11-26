package ru.para.budilnik;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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

import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.function.BooleanSupplier;

/**
 * Runs on a real Android (emulator): alarms must really appear in the system Clock app
 * (seen through AlarmManager.getNextAlarmClock), settings open by the ⚙ button,
 * the evening reminder is shown.
 */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
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
        if (Build.VERSION.SDK_INT >= 33) {
            inst.getUiAutomation().grantRuntimePermission(PKG, Manifest.permission.POST_NOTIFICATIONS);
        }
        shell("mkdir -p /data/local/tmp/shots");
        store.saveSeries(new ArrayList<>());
        try { device.wakeUp(); } catch (Exception ignored) { }
        shell("wm dismiss-keyguard");
        SystemClock.sleep(500);
    }

    @Test
    public void a_clockAppAcceptsAlarmSilently() {
        assertTrue("a Clock app is installed", Alarms.hasClock(ctx));
        // ~24 h ahead, so later tests' alarms are always earlier than this one.
        LocalDateTime t = LocalDateTime.now().plusHours(24).minusMinutes(2).withSecond(0).withNano(0);
        ctx.startActivity(Alarms.setIntent(t, "Тест").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        assertTrue("the Clock app set the alarm, next = " + nextSystemAlarm(),
                waitFor(() -> nextSystemAlarm() == epoch(t), 15000));
        device.pressHome();
    }

    @Test
    public void b_mainScreenSetsAlarmsInClock() {
        openMain();
        screenshot("1-main");
        UiObject2 set = scrollTo("Поставить в «Часы»");
        set.click();
        UiObject2 again = device.wait(Until.findObject(By.text("Поставить").clazz("android.widget.Button")), 2000);
        if (again != null) again.click();

        assertTrue("series recorded after all alarms were handed over; on screen: " + visibleTexts(),
                waitFor(() -> store.series().size() == 1, 60000));
        Store.Series s = store.series().get(0);
        Log.i(TAG, "set " + s.count() + " alarms, first " + s.start);
        assertTrue("first alarm in the future", s.start > System.currentTimeMillis());
        assertTrue("Clock app has the first alarm, next = " + nextSystemAlarm() + ", expected " + s.start,
                waitFor(() -> nextSystemAlarm() == s.start, 10000));
        screenshot("2-main-after-set");
        device.pressHome();
    }

    @Test
    public void c_settingsOpenByGearButton() {
        openMain();
        UiObject2 gear = device.wait(Until.findObject(By.desc("Настройки")), 5000);
        assertNotNull("⚙ button on the main screen", gear);
        gear.click();
        assertTrue("settings screen opened", device.wait(Until.hasObject(By.text("Во сколько вставать")), 10000));
        screenshot("3-settings");
        device.pressBack();
        assertTrue("back to the main screen", device.wait(Until.hasObject(By.text("⏰ Будильник на пары")), 10000));
        device.pressHome();
    }

    @Test
    public void d_eveningReminderIsShown() {
        Reminder.show(ctx, LocalDate.now().plusDays(1), "Завтра пара в 08:00 — поставить будильники?", "тест");
        assertTrue("reminder notification shown", waitFor(() -> {
            for (StatusBarNotification n : ctx.getSystemService(NotificationManager.class).getActiveNotifications()) {
                if (n.getId() == Reminder.NOTIF_ID) return true;
            }
            return false;
        }, 5000));
        Reminder.cancelNotification(ctx);
    }

    // ---------- helpers ----------

    private void openMain() {
        Intent i = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        ctx.startActivity(i);
        assertTrue("main screen opened", device.wait(Until.hasObject(By.text("⏰ Будильник на пары")), 15000));
    }

    private UiObject2 scrollTo(String text) {
        UiObject2 o = null;
        for (int k = 0; k < 12 && o == null; k++) {
            o = device.findObject(By.text(text));
            if (o == null) {
                device.swipe(device.getDisplayWidth() / 2, device.getDisplayHeight() * 3 / 4,
                        device.getDisplayWidth() / 2, device.getDisplayHeight() / 4, 20);
                device.waitForIdle(1000);
            }
        }
        if (o == null) throw new AssertionError("\"" + text + "\" not found; on screen: " + visibleTexts());
        return o;
    }

    private static long epoch(LocalDateTime t) { return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }

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

    private static boolean waitFor(BooleanSupplier cond, long ms) {
        long until = SystemClock.uptimeMillis() + ms;
        while (SystemClock.uptimeMillis() < until) {
            if (cond.getAsBoolean()) return true;
            SystemClock.sleep(200);
        }
        return cond.getAsBoolean();
    }
}
