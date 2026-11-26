package ru.para.budilnik;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_NOTIF = 1;
    private static final int REQ_FILE = 2;
    private static final int DAYS_AHEAD = 14;
    private static final String[] DOW = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
    private static final String[] DOW_FULL = {"Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"};
    private static final String[] MONTHS = {"января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"};

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private Store store;
    private Ui ui;
    private Schedule schedule;

    private LocalDate day;
    /** Alarm window, in minutes from the start of {@link #day} (may be negative = the evening before). */
    private int fromMin = 5 * 60, toMin = 5 * 60 + 30;
    private boolean loading;

    // dynamic parts
    private LinearLayout warnings, days, hint, seriesList;
    private Button fromBtn, toBtn;
    private TextView intervalLabel, preview, statusLine, scheduleInfo;
    private SeekBar intervalBar;
    private Switch showerSwitch;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new Store(this);
        ui = new Ui(this);
        AlarmService.ensureChannel(this);
        schedule = Schedule.loadLocal(this);
        day = pickDefaultDay();
        applyHint();
        setContentView(build());
        render();
        refreshSchedule(false);
        Alarms.reschedule(this, 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (warnings != null) {
            renderWarnings();
            renderSeries();
        }
    }

    @Override
    protected void onDestroy() {
        bg.shutdownNow();
        super.onDestroy();
    }

    // ---------- time helpers ----------

    private LocalDateTime at(int minutes) { return day.atStartOfDay().plusMinutes(minutes); }

    private static long epoch(LocalDateTime t) { return t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(); }

    private static LocalDateTime fromEpoch(long ms) { return LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()); }

    private static String hm(LocalDateTime t) { return String.format(Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute()); }

    private static String hm(int minutes) {
        int m = ((minutes % 1440) + 1440) % 1440;
        return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60);
    }

    private static String dur(int min) {
        if (min % 60 == 0) return (min / 60) + " ч";
        if (min < 60) return min + " мин";
        return (min / 60) + " ч " + (min % 60) + " мин";
    }

    private static String prettyDay(LocalDate d) {
        LocalDate today = LocalDate.now();
        String base = d.getDayOfMonth() + " " + MONTHS[d.getMonthValue() - 1];
        if (d.equals(today)) return "Сегодня, " + base;
        if (d.equals(today.plusDays(1))) return "Завтра, " + base;
        return DOW_FULL[d.getDayOfWeek().getValue() - 1] + ", " + base;
    }

    private LocalDateTime wakeFor(LocalDate d) {
        Schedule.Lesson l = schedule.firstByDay.get(d);
        if (l == null) return null;
        return LocalDateTime.of(d, l.start).minusMinutes(store.prepMin());
    }

    private LocalDate pickDefaultDay() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < DAYS_AHEAD; i++) {
            LocalDate d = today.plusDays(i);
            LocalDateTime w = wakeFor(d);
            if (w != null && w.isAfter(now)) return d;
        }
        return today.plusDays(1);
    }

    /** "Get up by" time of the selected day becomes the last alarm. */
    private void applyHint() {
        LocalDateTime w = wakeFor(day);
        if (w == null) return;
        toMin = (int) java.time.Duration.between(day.atStartOfDay(), w).toMinutes();
        fromMin = toMin - store.spanMin();
    }

    // ---------- layout ----------

    private View build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(ui.bg);
        LinearLayout root = ui.column();
        root.setPadding(0, 0, 0, ui.dp(32));
        scroll.addView(root);

        // header
        LinearLayout header = ui.column();
        header.setBackgroundColor(ui.dark ? 0xFF1A1D2E : 0xFF3B5BDB);
        header.setPadding(ui.dp(20), ui.dp(18), ui.dp(20), ui.dp(18));
        header.addView(ui.text("⏰ Будильник на пары", 22, 0xFFFFFFFF, true));
        scheduleInfo = ui.text("", 13, 0xFFD5DCFF, false);
        scheduleInfo.setPadding(0, ui.dp(4), 0, 0);
        header.addView(scheduleInfo);
        root.addView(header);

        warnings = ui.column();
        root.addView(warnings);

        // day picker
        LinearLayout dayCard = ui.card();
        dayCard.addView(ui.title("День"));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        days = ui.row();
        hs.addView(days);
        dayCard.addView(hs);
        root.addView(dayCard);

        // hint
        LinearLayout hintCard = ui.card();
        hintCard.addView(ui.title("Подсказка по расписанию"));
        hint = ui.column();
        hintCard.addView(hint);
        showerSwitch = new Switch(this);
        showerSwitch.setText("🚿 Помыться (ещё на " + dur(store.showerMin()) + " раньше)");
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

        alarmCard.addView(ui.button("Поставить будильники", true, v -> setForDay()), ui.full(14));
        alarmCard.addView(ui.button("Поставить на все дни с парами (неделя)", false, v -> setForWeek()), ui.full(8));
        root.addView(alarmCard);

        // set alarms
        LinearLayout listCard = ui.card();
        listCard.addView(ui.title("Поставлено"));
        statusLine = ui.text("", 14, ui.muted, false);
        listCard.addView(statusLine);
        seriesList = ui.column();
        listCard.addView(seriesList);
        root.addView(listCard);

        root.addView(buildSettings());
        return scroll;
    }

    private View labeled(String label, View v) {
        LinearLayout l = ui.column();
        l.addView(ui.text(label, 13, ui.muted, false));
        l.addView(v, ui.full(4));
        return l;
    }

    private View buildSettings() {
        LinearLayout c = ui.card();
        c.addView(ui.title("Настройки"));
        c.addView(numberRow("Дорога до пары, мин", store.roadMin(), v -> store.setRoadMin(v)));
        c.addView(numberRow("Утренние сборы, мин", store.morningMin(), v -> store.setMorningMin(v)));
        c.addView(numberRow("Помыться, мин", store.showerMin(), v -> {
            store.setShowerMin(v);
            showerSwitch.setText("🚿 Помыться (ещё на " + dur(v) + " раньше)");
        }));

        c.addView(ui.text("Ссылка на расписание (.ics)", 13, ui.muted, false), ui.full(12));
        EditText url = new EditText(this);
        url.setText(store.url());
        url.setTextSize(13);
        url.setTextColor(ui.text);
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        url.addTextChangedListener(watcher(s -> store.setUrl(s.trim().isEmpty() ? Store.DEFAULT_URL : s.trim())));
        c.addView(url);
        TextView note = ui.text("Берётся то же расписание, что в приложении «Моё ДЗ» (его копия обновляется каждый день). "
                + "Если ссылка недоступна — пробую сайт АлтГУ, а без интернета — сохранённую копию.", 12, ui.muted, false);
        c.addView(note, ui.full(4));

        c.addView(ui.button("⟳ Обновить расписание", false, v -> refreshSchedule(true)), ui.full(12));
        c.addView(ui.button("Загрузить файл .ics вручную", false, v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
            try { startActivityForResult(i, REQ_FILE); } catch (RuntimeException e) { toast("Не нашёл менеджер файлов"); }
        }), ui.full(8));
        c.addView(ui.button("🔔 Проверить звонок (через 10 сек)", false, v -> {
            Alarms.test(this, 10);
            toast("Звонок через 10 секунд — можно заблокировать экран");
            renderSeries();
        }), ui.full(8));
        return c;
    }

    private interface IntSink { void put(int v); }

    private interface StrSink { void put(String v); }

    private TextWatcher watcher(StrSink sink) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) { sink.put(e.toString()); }
        };
    }

    private View numberRow(String label, int value, IntSink sink) {
        LinearLayout r = ui.row();
        r.addView(ui.text(label, 15, ui.text, false), ui.weight(1, 0));
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setText(String.valueOf(value));
        e.setTextColor(ui.text);
        e.setGravity(Gravity.END);
        e.setMinWidth(ui.dp(72));
        e.addTextChangedListener(watcher(s -> {
            try {
                int v = Integer.parseInt(s.trim());
                if (v < 0 || v > 600) return;
                sink.put(v);
                applyHint();
                render();
            } catch (NumberFormatException ignored) { }
        }));
        r.addView(e);
        return r;
    }

    // ---------- rendering ----------

    private void render() {
        renderHeader();
        renderWarnings();
        renderDays();
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

    private void renderDays() {
        days.removeAllViews();
        LocalDate today = LocalDate.now();
        for (int i = 0; i < DAYS_AHEAD; i++) {
            final LocalDate d = today.plusDays(i);
            Schedule.Lesson l = schedule.firstByDay.get(d);
            boolean sel = d.equals(day);
            LinearLayout chip = ui.column();
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8));
            chip.setBackground(ui.round(sel ? ui.accent : ui.accentSoft, 14));
            int fg = sel ? ui.onAccent : ui.text;
            String top = i == 0 ? "Сег" : i == 1 ? "Завт" : DOW[d.getDayOfWeek().getValue() - 1];
            TextView a = ui.text(top, 12, sel ? ui.onAccent : ui.muted, false);
            a.setGravity(Gravity.CENTER);
            TextView b = ui.text(String.valueOf(d.getDayOfMonth()), 18, fg, true);
            b.setGravity(Gravity.CENTER);
            TextView c = ui.text(l == null ? "—" : hm(LocalDateTime.of(d, l.start)), 12, sel ? ui.onAccent : (l == null ? ui.muted : ui.accent), l != null);
            c.setGravity(Gravity.CENTER);
            chip.addView(a);
            chip.addView(b);
            chip.addView(c);
            chip.setMinimumWidth(ui.dp(58));
            chip.setOnClickListener(v -> {
                day = d;
                applyHint();
                render();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = ui.dp(8);
            days.addView(chip, lp);
        }
    }

    private void renderHint() {
        hint.removeAllViews();
        Schedule.Lesson l = schedule.firstByDay.get(day);
        hint.addView(ui.text(prettyDay(day), 15, ui.muted, false));
        if (l == null) {
            hint.addView(ui.text("В этот день пар нет — время можно выставить вручную.", 15, ui.text, false), ui.full(6));
            return;
        }
        String what = l.subject + (l.type.isEmpty() ? "" : " · " + l.type) + (l.room.isEmpty() ? "" : " · ауд. " + l.room);
        hint.addView(ui.text("Первая пара в " + hm(LocalDateTime.of(day, l.start)), 18, ui.text, true), ui.full(4));
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
        TextView big = ui.text(hm(w) + (w.toLocalDate().isBefore(day) ? " (накануне)" : ""), 30, ui.accent, true);
        box.addView(big);
        box.addView(ui.text(calc, 13, ui.muted, false));
        hint.addView(box, ui.full(10));
        if (!w.isAfter(LocalDateTime.now())) {
            hint.addView(ui.text("Это время уже прошло.", 13, ui.danger, false), ui.full(6));
        }
    }

    private void renderWindow() {
        if (fromBtn == null) return;
        fromBtn.setText(hm(fromMin));
        toBtn.setText(hm(toMin));
        int iv = store.intervalMin();
        intervalLabel.setText("Перерыв между будильниками: " + iv + " мин");
        if (intervalBar.getProgress() != iv - 1) intervalBar.setProgress(iv - 1);

        if (fromMin > toMin) {
            preview.setText("Время «с» должно быть раньше, чем «по».");
            preview.setTextColor(ui.danger);
            return;
        }
        preview.setTextColor(ui.muted);
        List<String> list = new ArrayList<>();
        for (int t = fromMin; t <= toMin; t += iv) list.add(hm(t));
        String shown = list.size() <= 12 ? String.join(", ", list)
                : String.join(", ", list.subList(0, 6)) + " … " + String.join(", ", list.subList(list.size() - 2, list.size()));
        preview.setText(count(list.size()) + ": " + shown);
    }

    private static String count(int n) {
        int m10 = n % 10, m100 = n % 100;
        String w = (m10 == 1 && m100 != 11) ? "будильник" : (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) ? "будильника" : "будильников";
        return n + " " + w;
    }

    private void renderSeries() {
        seriesList.removeAllViews();
        long now = System.currentTimeMillis();
        store.dropFinished(now);
        List<Store.Series> all = store.series();
        Object[] next = Alarms.next(all, now);
        if (next == null) {
            statusLine.setText("Будильников нет.");
        } else {
            LocalDateTime t = fromEpoch((Long) next[0]);
            statusLine.setText("Ближайший звонок: " + prettyDay(t.toLocalDate()).toLowerCase(Locale.ROOT) + " в " + hm(t));
        }
        for (Store.Series s : all) {
            LinearLayout r = ui.row();
            r.setPadding(0, ui.dp(10), 0, 0);
            LinearLayout info = ui.column();
            LocalDateTime st = fromEpoch(s.start), en = fromEpoch(s.lastTime());
            String dayName = "test".equals(s.dateKey) ? "Проверка" : prettyDay(LocalDate.parse(s.dateKey));
            info.addView(ui.text(dayName, 15, ui.text, true));
            info.addView(ui.text(hm(st) + " → " + hm(en) + " · каждые " + s.interval + " мин · " + count(s.count()), 14, ui.text, false));
            if (s.label != null && !s.label.isEmpty()) info.addView(ui.text(s.label, 12, ui.muted, false));
            r.addView(info, ui.weight(1, 0));
            Button del = ui.button("✕", false, v -> {
                store.removeSeries(s.id);
                Alarms.reschedule(this, 0);
                renderSeries();
            });
            del.setTextColor(ui.danger);
            del.setMinWidth(ui.dp(48));
            r.addView(del, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
            seriesList.addView(r);
        }
    }

    private void renderWarnings() {
        warnings.removeAllViews();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            warn("Разреши уведомления — без них будильник не покажется.", "Разрешить",
                    () -> requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF));
        }
        if (!Alarms.canExact(this) && Build.VERSION.SDK_INT >= 31) {
            warn("Разреши «Будильники и напоминания», чтобы звонок был точно вовремя.", "Открыть",
                    () -> open(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName()))));
        }
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && !nm.canUseFullScreenIntent()) {
                warn("Разреши показ на весь экран — чтобы будильник открывался поверх блокировки.", "Открыть",
                        () -> open(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + getPackageName()))));
            }
        }
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            warn("Желательно: отключи экономию батареи для приложения, чтобы система его не усыпляла.", "Отключить",
                    () -> open(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + getPackageName()))));
        }
    }

    private void warn(String msg, String action, Runnable r) {
        LinearLayout c = ui.card();
        c.setBackground(ui.round(ui.dark ? 0xFF3A2E14 : 0xFFFFF4DB, 16));
        c.addView(ui.text("⚠️ " + msg, 14, ui.text, false));
        Button b = ui.button(action, true, v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = ui.dp(8);
        c.addView(b, lp);
        warnings.addView(c);
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (RuntimeException e) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
            } catch (RuntimeException ignored) { }
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        renderWarnings();
    }

    // ---------- actions ----------

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

    private String labelFor(LocalDate d) {
        Schedule.Lesson l = schedule.firstByDay.get(d);
        if (l == null) return "Будильник";
        return "Пара в " + hm(LocalDateTime.of(d, l.start)) + " · " + l.subject + (l.room.isEmpty() ? "" : " (" + l.room + ")");
    }

    private Store.Series makeSeries(LocalDate d, LocalDateTime from, LocalDateTime to) {
        Store.Series s = new Store.Series();
        s.id = d + "-" + System.nanoTime();
        s.dateKey = d.toString();
        s.start = epoch(from);
        s.end = epoch(to);
        s.interval = store.intervalMin();
        s.label = labelFor(d);
        return s;
    }

    private void setForDay() {
        if (fromMin > toMin) { toast("Время «с» должно быть раньше, чем «по»"); return; }
        LocalDateTime to = at(toMin);
        if (!to.isAfter(LocalDateTime.now())) { toast("Это время уже прошло — выбери другой день"); return; }
        store.putSeries(makeSeries(day, at(fromMin), to));
        Alarms.reschedule(this, 0);
        renderSeries();
        toast("Готово: " + prettyDay(day).toLowerCase(Locale.ROOT) + ", " + hm(fromMin) + "–" + hm(toMin));
    }

    private void setForWeek() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        int n = 0;
        for (int i = 0; i < 7; i++) {
            LocalDate d = today.plusDays(i);
            LocalDateTime w = wakeFor(d);
            if (w == null || !w.isAfter(now)) continue;
            store.putSeries(makeSeries(d, w.minusMinutes(store.spanMin()), w));
            n++;
        }
        Alarms.reschedule(this, 0);
        renderSeries();
        toast(n == 0 ? "На ближайшую неделю пар не нашёл" : "Поставил на " + n + " дн. (по " + store.spanMin() + " мин до подъёма)");
    }

    private void refreshSchedule(boolean manual) {
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
                    if (schedule.firstByDay.get(day) != null) applyHint();
                    render();
                    if (manual) toast("Расписание обновлено");
                } else {
                    renderHeader();
                    if (manual) toast("Не удалось скачать — использую сохранённую копию");
                }
            });
        });
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != REQ_FILE || result != RESULT_OK || data == null || data.getData() == null) return;
        try (InputStream in = getContentResolver().openInputStream(data.getData())) {
            Schedule s = in == null ? null : Schedule.importText(this, store, Schedule.readAll(in));
            if (s == null) { toast("Это не файл расписания .ics"); return; }
            schedule = s;
            applyHint();
            render();
            toast("Расписание загружено");
        } catch (Exception e) {
            toast("Не удалось прочитать файл");
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
