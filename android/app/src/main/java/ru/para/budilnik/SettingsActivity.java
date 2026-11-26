package ru.para.budilnik;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Settings, opened by the ⚙ button on the main screen. */
public class SettingsActivity extends Activity {
    private static final int REQ_FILE = 2;

    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private Store store;
    private Ui ui;
    private Button reminderTime;
    private TextView totalLine;
    private boolean loading;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new Store(this);
        ui = new Ui(this);
        setContentView(build());
        renderTotal();
    }

    @Override
    protected void onDestroy() {
        bg.shutdownNow();
        super.onDestroy();
    }

    private View build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(ui.bg);
        LinearLayout root = ui.column();
        root.setPadding(0, 0, 0, ui.dp(32));
        scroll.addView(root);

        LinearLayout header = ui.row();
        header.setBackgroundColor(ui.dark ? 0xFF1A1D2E : 0xFF3B5BDB);
        header.setPadding(ui.dp(8), ui.dp(14), ui.dp(20), ui.dp(14));
        TextView back = ui.text("←", 24, 0xFFFFFFFF, false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Назад");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        header.addView(ui.text("Настройки", 22, 0xFFFFFFFF, true), ui.weight(1, 4));
        root.addView(header);

        // how early to get up
        LinearLayout prep = ui.card();
        prep.addView(ui.title("Во сколько вставать"));
        prep.addView(numberRow("Дорога до пары, мин", store.roadMin(), store::setRoadMin));
        prep.addView(numberRow("Утренние сборы, мин", store.morningMin(), store::setMorningMin));
        prep.addView(numberRow("Помыться, мин", store.showerMin(), store::setShowerMin));
        totalLine = ui.text("", 13, ui.muted, false);
        prep.addView(totalLine, ui.full(6));
        root.addView(prep);

        // evening reminder
        LinearLayout rem = ui.card();
        rem.addView(ui.title("Напоминание вечером"));
        Switch on = new Switch(this);
        on.setText("Предлагать поставить будильники на завтра");
        on.setTextColor(ui.text);
        on.setTextSize(15);
        on.setChecked(store.reminderOn());
        rem.addView(on, ui.full(0));
        LinearLayout r = ui.row();
        r.addView(ui.text("Во сколько", 15, ui.text, false), ui.weight(1, 0));
        reminderTime = ui.button(hm(store.reminderMin()), false, v -> pickReminderTime());
        r.addView(reminderTime);
        rem.addView(r, ui.full(8));
        reminderTime.setEnabled(store.reminderOn());
        on.setOnCheckedChangeListener((b, v) -> {
            store.setReminderOn(v);
            reminderTime.setEnabled(v);
            Reminder.schedule(this);
        });
        rem.addView(ui.text("Системные «Часы» ставят будильник только на ближайшие 24 часа, поэтому вечером "
                + "придёт уведомление — нажми «Поставить», и будильники на завтра встанут сами. "
                + "Если на завтра пар нет или будильники уже стоят, уведомления не будет.", 12, ui.muted, false), ui.full(8));
        rem.addView(ui.button("Показать, как выглядит", false, v -> {
            Reminder.show(this, java.time.LocalDate.now().plusDays(1),
                    "Завтра пара в 08:00 — поставить будильники?", "Так будет выглядеть напоминание.");
            toast("Уведомление отправлено");
        }), ui.full(8));
        root.addView(rem);

        // timetable
        LinearLayout sch = ui.card();
        sch.addView(ui.title("Расписание"));
        sch.addView(ui.text("Ссылка на расписание (.ics)", 13, ui.muted, false));
        EditText url = new EditText(this);
        url.setText(store.url());
        url.setTextSize(13);
        url.setTextColor(ui.text);
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        url.addTextChangedListener(watcher(s -> store.setUrl(s.trim().isEmpty() ? Store.DEFAULT_URL : s.trim())));
        sch.addView(url);
        sch.addView(ui.text("Берётся то же расписание, что в приложении «Моё ДЗ» (его копия обновляется каждый день). "
                + "Если ссылка недоступна — пробую сайт АлтГУ, а без интернета — сохранённую копию.", 12, ui.muted, false), ui.full(4));
        sch.addView(ui.button("⟳ Обновить расписание", false, v -> refreshSchedule()), ui.full(12));
        sch.addView(ui.button("Загрузить файл .ics вручную", false, v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
            try { startActivityForResult(i, REQ_FILE); } catch (RuntimeException e) { toast("Не нашёл менеджер файлов"); }
        }), ui.full(8));
        root.addView(sch);

        // check
        LinearLayout chk = ui.card();
        chk.addView(ui.title("Проверка"));
        chk.addView(ui.button("🔔 Поставить пробный будильник через 2 минуты", false, v -> testAlarm()), ui.full(0));
        chk.addView(ui.text("Будильник появится в «Часах». После проверки его можно удалить там.", 12, ui.muted, false), ui.full(6));
        root.addView(chk);
        return scroll;
    }

    private static String hm(int minutes) {
        int m = ((minutes % 1440) + 1440) % 1440;
        return String.format(Locale.ROOT, "%02d:%02d", m / 60, m % 60);
    }

    private void renderTotal() {
        totalLine.setText("Вставать за " + MainActivity.dur(store.prepMin()) + " до первой пары"
                + (store.shower() ? " (с душем)" : "") + ".");
    }

    private void pickReminderTime() {
        int m = store.reminderMin();
        new TimePickerDialog(this, (v, h, min) -> {
            store.setReminderMin(h * 60 + min);
            reminderTime.setText(hm(store.reminderMin()));
            Reminder.schedule(this);
        }, m / 60, m % 60, true).show();
    }

    private void testAlarm() {
        LocalDateTime t = LocalDateTime.now().plusMinutes(2).withSecond(0).withNano(0);
        try {
            startActivity(Alarms.setIntent(t, "Проверка будильника"));
            toast("Будильник на " + Plan.hm(t) + " отправлен в «Часы»");
        } catch (RuntimeException e) {
            toast("Не нашёл приложение «Часы»");
        }
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
                renderTotal();
            } catch (NumberFormatException ignored) { }
        }));
        r.addView(e);
        return r;
    }

    private void refreshSchedule() {
        if (loading) return;
        loading = true;
        toast("Обновляю расписание…");
        bg.execute(() -> {
            Schedule s = Schedule.download(this, store);
            runOnUiThread(() -> {
                loading = false;
                if (isFinishing() || isDestroyed()) return;
                toast(s != null ? "Расписание обновлено" : "Не удалось скачать — использую сохранённую копию");
            });
        });
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code != REQ_FILE || result != RESULT_OK || data == null || data.getData() == null) return;
        try (InputStream in = getContentResolver().openInputStream(data.getData())) {
            Schedule s = in == null ? null : Schedule.importText(this, store, Schedule.readAll(in));
            toast(s == null ? "Это не файл расписания .ics" : "Расписание загружено");
        } catch (Exception e) {
            toast("Не удалось прочитать файл");
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
}
