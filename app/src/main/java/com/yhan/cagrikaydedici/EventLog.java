package com.yhan.cagrikaydedici;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Tanılama için son olayların kısa kaydı. */
public final class EventLog {
    private EventLog() {}
    private static final int MAX = 40;

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("olaylar", Context.MODE_PRIVATE);
    }

    public static synchronized void add(Context c, String msg) {
        if (c == null) return;
        String ts = new SimpleDateFormat("dd.MM HH:mm:ss", Locale.US).format(new Date());
        String old = sp(c).getString("log", "");
        String[] lines = old.isEmpty() ? new String[0] : old.split("\n");
        StringBuilder sb = new StringBuilder(ts).append("  ").append(msg);
        for (int i = 0; i < lines.length && i < MAX - 1; i++) sb.append('\n').append(lines[i]);
        sp(c).edit().putString("log", sb.toString()).apply();
    }

    public static String get(Context c) { return sp(c).getString("log", ""); }

    public static void clear(Context c) { sp(c).edit().remove("log").apply(); }
}
