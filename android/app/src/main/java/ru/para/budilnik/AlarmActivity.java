package ru.para.budilnik;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Full-screen "wake up" screen, shown over the lock screen. */
public class AlarmActivity extends Activity {
    private static WeakReference<AlarmActivity> current = new WeakReference<>(null);

    static void closeIfOpen() {
        AlarmActivity a = current.get();
        if (a != null) a.runOnUiThread(a::finish);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        current = new WeakReference<>(this);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (AlarmService.ringingSeries == null && AlarmService.ringingLabel == null) {
            finish();
            return;
        }
        build();
    }

    private void build() {
        Ui ui = new Ui(this);
        LinearLayout root = ui.column();
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF1E2A5A);
        root.setPadding(ui.dp(28), ui.dp(28), ui.dp(28), ui.dp(28));

        String hm = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(AlarmService.ringingTime));
        TextView icon = ui.text("⏰", 64, 0xFFFFFFFF, false);
        icon.setGravity(Gravity.CENTER);
        TextView time = ui.text(hm, 72, 0xFFFFFFFF, true);
        time.setGravity(Gravity.CENTER);
        TextView sub = ui.text("Пора вставать", 22, 0xFFDDE3FF, false);
        sub.setGravity(Gravity.CENTER);
        TextView label = ui.text(AlarmService.ringingLabel == null ? "" : AlarmService.ringingLabel, 16, 0xFFB8C2F0, false);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, ui.dp(8), 0, ui.dp(36));

        root.addView(icon);
        root.addView(time);
        root.addView(sub);
        root.addView(label);

        final String series = AlarmService.ringingSeries;
        Button stop = big(ui, "Выключить", 0xFFFFFFFF, 0xFF1E2A5A, () -> send(AlarmService.ACTION_STOP, null));
        Button stopAll = big(ui, "Я встал — убрать остальные", 0x33FFFFFF, 0xFFFFFFFF,
                () -> send(AlarmService.ACTION_STOP_ALL, series));
        root.addView(stop, ui.full(0));
        root.addView(stopAll, ui.full(12));

        TextView hint = ui.text("«Выключить» — замолчит до следующего звонка серии.", 13, 0xFF97A3D8, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, ui.dp(16), 0, 0);
        root.addView(hint);
        setContentView(root);
    }

    private Button big(Ui ui, String s, int bg, int fg, Runnable r) {
        Button b = ui.button(s, true, v -> r.run());
        b.setBackground(ui.round(bg, 18));
        b.setTextColor(fg);
        b.setTextSize(19);
        b.setMinHeight(ui.dp(64));
        return b;
    }

    private void send(String action, String series) {
        Intent i = new Intent(this, AlarmService.class).setAction(action);
        if (series != null) i.putExtra(Alarms.EXTRA_SERIES, series);
        startService(i);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (current.get() == this) current = new WeakReference<>(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // Back does not silence the alarm by accident; use the buttons.
    }
}
