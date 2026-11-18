package com.yhan.cagrikaydedici;

import android.net.Uri;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Listede gösterilen bir kayıt. Dosya adı: yyyyMMdd_HHmmss__G|A__numara__isim.m4a */
public final class RecordingItem {
    public long id;
    public Uri uri;
    public String displayName = "";
    public long durationMs;
    public long dateMs;
    public long size;
    public String number = "";
    public String name = "";
    /** null: bilinmiyor */
    public Boolean incoming;

    public void parseName(long fallbackDateMs) {
        dateMs = fallbackDateMs;
        String base = displayName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        String[] p = base.split("__", -1);
        if (p.length >= 2) {
            try {
                Date d = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(p[0]);
                if (d != null) dateMs = d.getTime();
            } catch (Exception ignored) { }
            if ("G".equals(p[1])) incoming = Boolean.TRUE;
            else if ("A".equals(p[1])) incoming = Boolean.FALSE;
            if (p.length >= 3) number = p[2].trim();
            if (p.length >= 4) name = p[3].trim();
        }
    }

    public String title() {
        if (!name.isEmpty()) return name;
        if (!number.isEmpty()) return number;
        return "Bilinmeyen numara";
    }

    public boolean matches(String q) {
        if (q == null || q.isEmpty()) return true;
        String s = q.toLowerCase(new Locale("tr"));
        return name.toLowerCase(new Locale("tr")).contains(s)
                || number.contains(s)
                || displayName.toLowerCase(new Locale("tr")).contains(s);
    }
}
