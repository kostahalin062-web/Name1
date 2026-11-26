package ru.para.budilnik;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    /** From the evening notification: set the alarms for this day (yyyy-MM-dd) right away. */
    static final String EXTRA_AUTO_SET = "autoSet";

    private static final int REQ_NOTIF = 1;
    private static final int REQ_SET = 3;
    private static final String[] DOW_FULL = {"Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"};
    private static final String[] MONTHS = {"января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"};

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Store store;
    private Ui ui;
    private Schedule schedule;

    /** Day the alarms are for (today or tomorrow — the Clock app can ring only within 24 hours). */
    private LocalDate day;
    /** Alarm window, in minutes from the start of {@link #day} (may be negative = the evening before). */
    private int fromMin = 5 * 60, toMin = 5 * 60 + 30;
    private boolean loading;
    private boolean fromSettings;

    // handing alarms to the Clock app one by one
    private final ArrayDeque<LocalDateTime> pending = new ArrayDeque<>();
    private Store.Series pendingSeries;
    private String pendingLabel;
    private int pendingTotal;
    private boolean waitingClock;
    private long launchedAt;

    // dynamic parts
    private LinearLayout warnings, hint, seriesList;
    private Button fromBtn, toBtn, setBtn;
    private TextView intervalLabel, preview, statusLine, scheduleInfo;
    private SeekBar intervalBar;
    private Switch showerSwitch;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new Store(this);
        ui = new Ui(this);
        schedule = Schedule.loadLocal(this);
        day = pickDay();
        applyHint();
        setContentView(build());
        render();
        refreshSchedule();
        Reminder.schedule(this);
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (warnings == null) return;
        if (fromSettings) {
            fromSettings = false;
            schedule = Schedule.loadLocal(this);
            showerSwitch.setText(showerText());
            applyHint();
        }
        LocalDate d = pickDay();
        if (!d.equals(day) && pending.isEmpty()) {
            day = d;
            applyHint();
        }
        render();
        // The Clock app did not report back (e.g. the user left it) — go on with the next alarm.
        if (waitingClock && SystemClock.elapsedRealtime() - launchedAt > 1500) {
            waitingClock = false;
            handler.postDelayed(this::launchNext, 400);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        bg.shutdownNow();
        super.onDestroy();
    }

    // ---------- time helpers ----------

    private LocalDateTime at(int minutes) { return day.atStartOfDay().plusMinutes(minutes); }

    private static long epoch(LocalDateTime t) { return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }

    private static LocalDateTime fromEpoch(long ms) { return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()); }

    private static String hm(LocalDateTime t) { return Plan.hm(t); }

    private static String hm(int minutes) {
        int m = ((minutes % 1440) + 1440) % 1440;
        return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60);
    }

    static String dur(int min) {
        if (min % 60 == 0) return (min / 60) + " ч";
        if (min < 60) return min + " мин";
        return (min / 60) + " ч " + (min % 60) + " мин";
    }

    static String prettyDay(LocalDate d) {
        LocalDate today = LocalDate.now();
        String base = d.getDayOfMonth() + " " + MONTHS[d.getMonthValue() - 1];
        if (d.equals(today)) return "Сегодня, " + base;
        if (d.equals(today.plusDays(1))) return "Завтра, " + base;
        return DOW_FULL[d.getDayOfWeek().getValue() - 1] + ", " + base;
    }

    private LocalDateTime wakeFor(LocalDate d) { return Plan.wakeFor(schedule, store, d); }

    private LocalDate pickDay() {
        LocalDate today = LocalDate.now();
        return Alarms.targetDay(LocalDateTime.now(), wakeFor(today));
    }

    /** "Get up by" time of the day becomes the last alarm. */
    private void applyHint() {
        LocalDateTime w = wakeFor(day);
        if (w == null) return;
        toMin = (int) Duration.between(day.atStartOfDay(), w).toMinutes();
        fromMin = toMin - store.spanMin();
    }

    private String showerText() { return "🚿 Помыться (ещё на " + dur(store.showerMin()) + " раньше)"; }

    // ---------- layout ----------

    private View build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(ui.bg);
        LinearLayout root = ui.column();
        root.setPadding(0, 0, 0, ui.dp(32));
        scroll.addView(root);

        // header with the settings button
        LinearLayout header = ui.row();
        header.setBackgroundColor(ui.dark ? 0xFF1A1D2E : 0xFF3B5BDB);
        header.setPadding(ui.dp(20), ui.dp(14), ui.dp(8), ui.dp(14));
        LinearLayout titles = ui.column();
        titles.addView(ui.text("⏰ Будильник на пары", 22, 0xFFFFFFFF, true));
        scheduleInfo = ui.text("", 13, 0xFFD5DCFF, false);
        scheduleInfo.setPadding(0, ui.dp(4), 0, 0);
        titles.addView(scheduleInfo);
        header.addView(titles, ui.weight(1, 0));
        TextView gear = ui.text("⚙", 26, 0xFFFFFFFF, false);
        gear.setGravity(Gravity.CENTER);
        gear.setContentDescription("Настройки");
        gear.setBackground(ui.round(0x22FFFFFF, 24));
        gear.setOnClickListener(v -> {
            fromSettings = true;
            startActivity(new Intent(this, SettingsActivity.class));
        });
        header.addView(gear, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        root.addView(header);

        warnings = ui.column();
        root.addView(warnings);

        // hint
        LinearLayout hintCard = ui.card();
        hint = ui.column();
        hintCard.addView(hint);
        showerSwitch = new Switch(this);
        showerSwitch.setText(showerText());
        showerSwitch.setTextColor(ui.text);
        showerSwitch.setTextSize(15);
        showerSwitch.setChecked(store.shower());
        showerSwitch.setPadding(0, ui.dp(8), 0, ui.dp(4));
        showerSwitch.setOnCheckedChangeListener((b, on) -> {
            store.setShower(on);
            applyHint();
            render();
        });
        hintCard.addView(showerSwitch, ui.full(6));
        root.addView(hintCard);

        // alarm window
        LinearLayout alarmCard = ui.card();
        alarmCard.addView(ui.title("Будильники"));
        LinearLayout times = ui.row();
        fromBtn = ui.button("", false, v -> pickTime(true));
        toBtn = ui.button("", false, v -> pickTime(false));
        fromBtn.setTextSize(20);
        toBtn.setTextSize(20);
        times.addView(labeled("с", fromBtn), ui.weight(1, 0));
        times.addView(labeled("по", toBtn), ui.weight(1, 12));
        alarmCard.addView(times);

        intervalLabel = ui.text("", 15, ui.text, false);
        intervalLabel.setPadding(0, ui.dp(16), 0, 0);
        alarmCard.addView(intervalLabel);
        intervalBar = new SeekBar(this);
        intervalBar.setMax(9);
        intervalBar.setProgress(store.intervalMin() - 1);
        intervalBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                store.setIntervalMin(p + 1);
                renderWindow();
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        alarmCard.addView(intervalBar, ui.full(4));
        LinearLayout ticks = ui.row();
        TextView t1 = ui.text("1 мин", 12, ui.muted, false);
        TextView t10 = ui.text("10 мин", 12, ui.muted, false);
        t10.setGravity(Gravity.END);
        ticks.addView(t1, ui.weight(1, 0));
        ticks.addView(t10, ui.weight(1, 0));
        alarmCard.addView(ticks);

        preview = ui.text("", 14, ui.muted, false);
        preview.setPadding(0, ui.dp(12), 0, 0);
        alarmCard.addView(preview);

        setBtn = ui.button("Поставить в «Часы»", true, v -> setAlarms());
        alarmCard.addView(setBtn, ui.full(14));
        alarmCard.addView(ui.text("Будильники появятся в системном приложении «Часы» и зазвонят, "
                + "даже если это приложение закрыто.", 12, ui.muted, false), ui.full(6));
        root.addView(alarmCard);

        // what was set
        LinearLayout listCard = ui.card();
        listCard.addView(ui.title("Поставлено"));
        statusLine = ui.text("", 14, ui.muted, false);
        listCard.addView(statusLine);
        seriesList = ui.column();
        listCard.addView(seriesList);
        listCard.addView(ui.button("Открыть «Часы» — изменить или удалить", false, v -> {
            if (!Alarms.openClock(this)) toast("Не нашёл приложение «Часы»");
        }), ui.full(12));
        root.addView(listCard);
        return scroll;
    }

    private View labeled(String label, View v) {
        LinearLayout l = ui.column();
        l.addView(ui.text(label, 13, ui.muted, false));
        l.addView(v, ui.full(4));
        return l;
    }

    // ---------- rendering ----------

    private void render() {
        renderHeader();
        renderWarnings();
        renderHint();
        renderWindow();
        renderSeries();
    }

    private void renderHeader() {
        String s = schedule.calName.isEmpty() ? "Расписание не загружено" : schedule.calName;
        long lu = store.lastUpdate();
        if (loading) s += " · обновляю…";
        else if (lu > 0) s += " · обновлено " + prettyDay(fromEpoch(lu).toLocalDate()).toLowerCase(Locale.ROOT) + " в " + hm(fromEpoch(lu));
        scheduleInfo.setText(s);
    }

    private void renderHint() {
        hint.removeAllViews();
        Schedule.Lesson l = schedule.firstByDay.get(day);
        hint.addView(ui.title(prettyDay(day)));
        if (l == null) {
            hint.addView(ui.text("В этот день пар нет — время можно выставить вручную.", 15, ui.text, false));
            return;
        }
        String what = l.subject + (l.type.isEmpty() ? "" : " · " + l.type) + (l.room.isEmpty() ? "" : " · ауд. " + l.room);
        hint.addView(ui.text("Первая пара в " + hm(LocalDateTime.of(day, l.start)), 18, ui.text, true));
        hint.addView(ui.text(what, 14, ui.muted, false));

        LocalDateTime w = wakeFor(day);
        StringBuilder calc = new StringBuilder();
        calc.append(hm(LocalDateTime.of(day, l.start)))
                .append(" − ").append(dur(store.roadMin())).append(" дорога")
                .append(" − ").append(dur(store.morningMin())).append(" сборы");
        if (store.shower()) calc.append(" − ").append(dur(store.showerMin())).append(" душ");
        calc.append("  (всего ").append(dur(store.prepMin())).append(")");

        LinearLayout box = ui.column();
        box.setBackground(ui.round(ui.accentSoft, 12));
        box.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(12));
        box.addView(ui.text("Встать нужно до", 13, ui.muted, false));
        box.addView(ui.text(hm(w) + (w.toLocalDate().isBefore(day) ? " (накануне)" : ""), 30, ui.accent, true));
        box.addView(ui.text(calc, 13, ui.muted, false));
        hint.addView(box, ui.full(10));
    }

    private void renderWindow() {
        if (fromBtn == null) return;
        fromBtn.setText(hm(fromMin));
        toBtn.setText(hm(toMin));
        int iv = store.intervalMin();
        intervalLabel.setText("Перерыв между будильниками: " + iv + " мин");
        if (intervalBar.getProgress() != iv - 1) intervalBar.setProgress(iv - 1);
        renderSetButton();

        if (fromMin > toMin) {
            preview.setText("Время «с» должно быть раньше, чем «по».");
            preview.setTextColor(ui.danger);
            return;
        }
        List<LocalDateTime> all = Alarms.times(at(fromMin), at(toMin), iv);
        List<String> list = new ArrayList<>();
        for (LocalDateTime t : all) list.add(hm(t));
        String shown = list.size() <= 12 ? String.join(", ", list)
                : String.join(", ", list.subList(0, 6)) + " … " + String.join(", ", list.subList(list.size() - 2, list.size()));
        String text = count(list.size()) + ": " + shown;
        int skipped = all.size() - settable(all).size();
        if (skipped > 0) {
            text += "\n" + (skipped == all.size() ? "Все" : count(skipped) + " из них")
                    + " не поставить: «Часы» умеют ставить только на ближайшие 24 часа.";
            preview.setTextColor(ui.danger);
        } else {
            preview.setTextColor(ui.muted);
        }
        if (list.size() > 20) text += "\nМного будильников — в «Часах» будет длинный список. Можно увеличить перерыв.";
        preview.setText(text);
    }

    private void renderSetButton() {
        if (setBtn == null) return;
        boolean busy = !pending.isEmpty() || waitingClock;
        setBtn.setEnabled(!busy);
        setBtn.setText(busy ? "Ставлю… " + (pendingTotal - pending.size()) + " из " + pendingTotal : "Поставить в «Часы»");
    }

    static String count(int n) {
        int m10 = n % 10, m100 = n % 100;
        String w = (m10 == 1 && m100 != 11) ? "будильник" : (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) ? "будильника" : "будильников";
        return n + " " + w;
    }

    private void renderSeries() {
        seriesList.removeAllViews();
        store.dropFinished(System.currentTimeMillis());
        AlarmManager am = getSystemService(AlarmManager.class);
        AlarmManager.AlarmClockInfo next = am == null ? null : am.getNextAlarmClock();
        if (next == null) {
            statusLine.setText("В «Часах» нет включённых будильников.");
        } else {
            LocalDateTime t = fromEpoch(next.getTriggerTime());
            statusLine.setText("Ближайший будильник в «Часах»: " + prettyDay(t.toLocalDate()).toLowerCase(Locale.ROOT) + " в " + hm(t));
        }
        for (Store.Series s : store.series()) {
            LocalDate d;
            try { d = LocalDate.parse(s.dateKey); } catch (RuntimeException e) { continue; } // old test entries
            LinearLayout info = ui.column();
            info.setPadding(0, ui.dp(10), 0, 0);
            info.addView(ui.text(prettyDay(d), 15, ui.text, true));
            info.addView(ui.text(hm(fromEpoch(s.start)) + " → " + hm(fromEpoch(s.lastTime()))
                    + " · каждые " + s.interval + " мин · " + count(s.count()), 14, ui.text, false));
            if (s.label != null && !s.label.isEmpty()) info.addView(ui.text(s.label, 12, ui.muted, false));
            seriesList.addView(info);
        }
    }

    private void renderWarnings() {
        warnings.removeAllViews();
        if (!Alarms.hasClock(this)) {
            warn("Не нашёл системное приложение «Часы» — без него будильники не поставить.", null, null);
        }
        if (store.reminderOn() && Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            warn("Разреши уведомления — вечером напомню поставить будильники на завтра.", "Разрешить",
                    () -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF));
        }
    }

    private void warn(String msg, String action, Runnable r) {
        LinearLayout c = ui.card();
        c.setBackground(ui.round(ui.dark ? 0xFF3A2E14 : 0xFFFFF4DB, 16));
        c.addView(ui.text("⚠️ " + msg, 14, ui.text, false));
        if (action != null) {
            Button b = ui.button(action, true, v -> r.run());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.topMargin = ui.dp(8);
            c.addView(b, lp);
        }
        warnings.addView(c);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        renderWarnings();
    }

    // ---------- actions ----------

    private void handleIntent(Intent i) {
        if (i == null) return;
        String date = i.getStringExtra(EXTRA_AUTO_SET);
        if (date == null) return;
        i.removeExtra(EXTRA_AUTO_SET);
        Reminder.cancelNotification(this);
        LocalDate d;
        try { d = LocalDate.parse(date); } catch (RuntimeException e) { return; }
        if (!d.equals(day)) {
            day = d;
            applyHint();
            render();
        }
        setAlarms();
    }

    private void pickTime(boolean from) {
        int cur = from ? fromMin : toMin;
        int m = ((cur % 1440) + 1440) % 1440;
        new TimePickerDialog(this, (v, h, min) -> {
            int val = h * 60 + min;
            if (from) fromMin = val; else toMin = val;
            if (toMin > fromMin) store.setSpanMin(toMin - fromMin);
            renderWindow();
        }, m / 60, m % 60, true).show();
    }

    private static List<LocalDateTime> settable(List<LocalDateTime> all) {
        LocalDateTime now = LocalDateTime.now();
        List<LocalDateTime> ok = new ArrayList<>();
        for (LocalDateTime t : all) if (Alarms.settable(t, now)) ok.add(t);
        return ok;
    }

    private void setAlarms() {
        if (!pending.isEmpty() || waitingClock) return;
        if (fromMin > toMin) { toast("Время «с» должно быть раньше, чем «по»"); return; }
        if (!Alarms.hasClock(this)) { toast("Не нашёл приложение «Часы»"); return; }
        List<LocalDateTime> all = Alarms.times(at(fromMin), at(toMin), store.intervalMin());
        List<LocalDateTime> ok = settable(all);
        if (ok.isEmpty()) {
            toast(at(toMin).isAfter(LocalDateTime.now())
                    ? "Пока рано: «Часы» ставят будильник только на ближайшие 24 часа"
                    : "Это время уже прошло");
            return;
        }
        Store.Series old = store.seriesFor(day.toString());
        if (old != null) {
            new AlertDialog.Builder(this)
                    .setTitle("Уже поставлено")
                    .setMessage("На " + prettyDay(day).toLowerCase(Locale.ROOT) + " уже стоят будильники "
                            + hm(fromEpoch(old.start)) + "–" + hm(fromEpoch(old.lastTime()))
                            + ". Новые добавятся к ним — лишние потом удали в «Часах».")
                    .setPositiveButton("Поставить", (d, w) -> start(ok))
                    .setNeutralButton("Открыть «Часы»", (d, w) -> Alarms.openClock(this))
                    .setNegativeButton("Отмена", null)
                    .show();
            return;
        }
        start(ok);
    }

    private void start(List<LocalDateTime> times) {
        Store.Series s = new Store.Series();
        s.id = day + "-" + System.nanoTime();
        s.dateKey = day.toString();
        s.start = epoch(times.get(0));
        s.end = epoch(times.get(times.size() - 1));
        s.interval = store.intervalMin();
        s.label = Plan.labelFor(schedule, day);
        pendingSeries = s;
        pendingLabel = s.label;
        pending.clear();
        pending.addAll(times);
        pendingTotal = times.size();
        launchNext();
    }

    /** Hands the next alarm to the Clock app; continues when the Clock returns. */
    private void launchNext() {
        if (isFinishing() || isDestroyed()) return;
        LocalDateTime t = pending.poll();
        if (t == null) {
            finishSetting();
            return;
        }
        try {
            waitingClock = true;
            launchedAt = SystemClock.elapsedRealtime();
            startActivityForResult(Alarms.setIntent(t, pendingLabel), REQ_SET);
        } catch (ActivityNotFoundException | SecurityException e) {
            waitingClock = false;
            pending.clear();
            toast("«Часы» не принимают будильники от других приложений");
        }
        renderSetButton();
    }

    private void finishSetting() {
        waitingClock = false;
        if (pendingSeries == null) return;
        store.putSeries(pendingSeries);
        LocalDateTime a = fromEpoch(pendingSeries.start), b = fromEpoch(pendingSeries.end);
        toast("Готово: " + count(pendingTotal) + " в «Часах», " + hm(a) + "–" + hm(b));
        pendingSeries = null;
        render();
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != REQ_SET || !waitingClock) return;
        waitingClock = false;
        // A short pause so the Clock app finishes saving before the next request.
        handler.postDelayed(this::launchNext, 600);
    }

    private void refreshSchedule() {
        if (loading) return;
        loading = true;
        renderHeader();
        bg.execute(() -> {
            Schedule s = Schedule.download(this, store);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                loading = false;
                if (s != null) {
                    schedule = s;
                    if (pending.isEmpty()) {
                        day = pickDay();
                        applyHint();
                    }
                    render();
                } else {
                    renderHeader();
                }
            });
        });
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
