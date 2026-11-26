package ru.para.budilnik;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small helpers for building the screens in code. */
final class Ui {
    final Context c;
    final boolean dark;
    final int bg, card, text, muted, accent, accentSoft, danger, onAccent;

    Ui(Context c) {
        this.c = c;
        dark = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        bg = dark ? 0xFF121420 : 0xFFF4F5F9;
        card = dark ? 0xFF1E2130 : 0xFFFFFFFF;
        text = dark ? 0xFFE8EAF2 : 0xFF1B1E28;
        muted = dark ? 0xFF9AA0B4 : 0xFF6B7080;
        accent = dark ? 0xFF7C95F2 : 0xFF3B5BDB;
        accentSoft = dark ? 0xFF2A3360 : 0xFFE5EAFB;
        danger = dark ? 0xFFFF8A80 : 0xFFD32F2F;
        onAccent = dark ? 0xFF0E1230 : 0xFFFFFFFF;
    }

    int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    GradientDrawable round(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    LinearLayout column() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout row() {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    LinearLayout card() {
        LinearLayout l = column();
        l.setBackground(round(card, 16));
        l.setPadding(dp(16), dp(14), dp(16), dp(16));
        l.setElevation(dp(1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(16), dp(12), dp(16), 0);
        l.setLayoutParams(lp);
        return l;
    }

    TextView text(CharSequence s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    TextView title(CharSequence s) {
        TextView t = text(s, 17, text, true);
        t.setPadding(0, 0, 0, dp(8));
        return t;
    }

    Button button(CharSequence s, boolean primary, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTextColor(primary ? onAccent : accent);
        b.setBackground(round(primary ? accent : accentSoft, 12));
        b.setStateListAnimator(null);
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        b.setMinHeight(dp(48));
        b.setOnClickListener(l);
        return b;
    }

    LinearLayout.LayoutParams full(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(topDp);
        return lp;
    }

    LinearLayout.LayoutParams weight(float w, int leftDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, w);
        lp.leftMargin = dp(leftDp);
        return lp;
    }
}
