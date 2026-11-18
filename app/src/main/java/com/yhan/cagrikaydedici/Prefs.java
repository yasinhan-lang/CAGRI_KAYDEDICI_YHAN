package com.yhan.cagrikaydedici;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.MediaRecorder;

/** Uygulama ayarları. */
public final class Prefs {
    private Prefs() {}

    public static final String[] SOURCE_NAMES = {
            "Otomatik", "Ses tanıma", "Mikrofon", "Görüşme hattı", "İletişim"
    };

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("ayarlar", Context.MODE_PRIVATE);
    }

    public static boolean enabled(Context c) { return sp(c).getBoolean("enabled", true); }
    public static void setEnabled(Context c, boolean v) { sp(c).edit().putBoolean("enabled", v).apply(); }

    public static boolean incoming(Context c) { return sp(c).getBoolean("incoming", true); }
    public static void setIncoming(Context c, boolean v) { sp(c).edit().putBoolean("incoming", v).apply(); }

    public static boolean outgoing(Context c) { return sp(c).getBoolean("outgoing", true); }
    public static void setOutgoing(Context c, boolean v) { sp(c).edit().putBoolean("outgoing", v).apply(); }

    public static boolean speaker(Context c) { return sp(c).getBoolean("speaker", false); }
    public static void setSpeaker(Context c, boolean v) { sp(c).edit().putBoolean("speaker", v).apply(); }

    public static boolean bubble(Context c) { return sp(c).getBoolean("bubble", true); }
    public static void setBubble(Context c, boolean v) { sp(c).edit().putBoolean("bubble", v).apply(); }

    public static int bubbleX(Context c) { return sp(c).getInt("bx", -1); }
    public static int bubbleY(Context c) { return sp(c).getInt("by", -1); }
    public static void setBubblePos(Context c, int x, int y) { sp(c).edit().putInt("bx", x).putInt("by", y).apply(); }

    public static String sourceName(int src) {
        switch (src) {
            case MediaRecorder.AudioSource.VOICE_RECOGNITION: return "Ses tanıma";
            case MediaRecorder.AudioSource.MIC: return "Mikrofon";
            case MediaRecorder.AudioSource.VOICE_CALL: return "Görüşme hattı";
            case MediaRecorder.AudioSource.VOICE_COMMUNICATION: return "İletişim";
            case MediaRecorder.AudioSource.CAMCORDER: return "Kamera mikrofonu";
            case MediaRecorder.AudioSource.DEFAULT: return "Varsayılan";
            default: return "Kaynak " + src;
        }
    }

    public static int source(Context c) {
        int s = sp(c).getInt("source", 0);
        return (s < 0 || s >= SOURCE_NAMES.length) ? 0 : s;
    }
    public static void setSource(Context c, int v) { sp(c).edit().putInt("source", v).apply(); }

    public static boolean askedPerms(Context c) { return sp(c).getBoolean("askedPerms", false); }
    public static void setAskedPerms(Context c) { sp(c).edit().putBoolean("askedPerms", true).apply(); }

    /** Seçilen kaynağa göre denenecek ses kaynakları (ilk çalışan kullanılır). */
    public static int[] sourceChain(Context c) {
        switch (source(c)) {
            case 1: return new int[]{MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC};
            case 2: return new int[]{MediaRecorder.AudioSource.MIC};
            case 3: return new int[]{MediaRecorder.AudioSource.VOICE_CALL, MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC};
            case 4: return new int[]{MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.MIC};
            default: return new int[]{MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.MIC,
                    MediaRecorder.AudioSource.CAMCORDER, MediaRecorder.AudioSource.VOICE_COMMUNICATION};
        }
    }
}
