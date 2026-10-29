package com.halogallery.offline;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.os.Build;
import android.view.WindowInsets;
import android.view.View;
import android.widget.TextView;

final class Ui {
    static final int BG = Color.rgb(250, 250, 252);
    static final int INK = Color.rgb(30, 31, 42);
    static final int MUTED = Color.rgb(119, 122, 139);
    static final int ACCENT = Color.rgb(92, 100, 221);
    static final int LINE = Color.rgb(232, 233, 239);

    private Ui() {}

    static int dp(Context c, float value) {
        return (int) (value * c.getResources().getDisplayMetrics().density + .5f);
    }

    static GradientDrawable bg(int color, int radiusDp, Context c) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static TextView text(Context c, String text, int size, int color, boolean bold) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    static TextView action(Context c, String text, int size) {
        TextView v = text(c, text, size, INK, true);
        v.setGravity(Gravity.CENTER);
        v.setBackground(bg(Color.WHITE, 18, c));
        v.setElevation(dp(c, 1));
        v.setClickable(true);
        v.setFocusable(true);
        return v;
    }

    // Android 15+ enforces edge-to-edge for apps targeting API 35.
    static void respectSystemBars(View v) {
        if (Build.VERSION.SDK_INT >= 35) {
            v.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
            v.requestApplyInsets();
        }
    }

    static void pad(View v, Context c, int left, int top, int right, int bottom) {
        v.setPadding(dp(c,left), dp(c,top), dp(c,right), dp(c,bottom));
    }
}
