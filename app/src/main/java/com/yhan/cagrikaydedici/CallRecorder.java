package com.yhan.cagrikaydedici;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.MediaStore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArraySet;

/** Kaydı başlatan / durduran ve dosyayı Müzik klasörüne yazan çekirdek sınıf. */
public final class CallRecorder {

    public static final String FOLDER = "BozokluHAN Cagri Kayitlari";

    public interface Listener { void onRecordingChanged(boolean recording); }

    private static CallRecorder instance;
    private static final CopyOnWriteArraySet<Listener> LISTENERS = new CopyOnWriteArraySet<>();

    public static synchronized CallRecorder get() {
        if (instance == null) instance = new CallRecorder();
        return instance;
    }

    public static void addListener(Listener l) { LISTENERS.add(l); }
    public static void removeListener(Listener l) { LISTENERS.remove(l); }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaRecorder recorder;
    private ParcelFileDescriptor pfd;
    private Uri uri;
    private long startMs;
    private boolean incoming;
    private Context appCtx;
    private int[] chain = new int[0];
    private int chainIdx;
    private int usedSource = -1;
    private int level;
    private int silentSecs;
    private boolean heard;

    private CallRecorder() {}

    public synchronized boolean isRecording() { return recorder != null; }

    public synchronized long startedAt() { return startMs; }

    /** 0..32767 arası son ses seviyesi. */
    public synchronized int level() { return level; }

    private void notifyListeners() {
        final boolean r = recorder != null;
        handler.post(() -> {
            for (Listener l : LISTENERS) l.onRecordingChanged(r);
        });
    }

    public synchronized boolean start(Context ctx, boolean incomingCall) {
        if (recorder != null) return true;
        appCtx = ctx.getApplicationContext();
        incoming = incomingCall;
        startMs = System.currentTimeMillis();
        chain = Prefs.sourceChain(appCtx);
        level = 0;
        silentSecs = 0;
        heard = false;
        if (!openAndStart(0)) {
            EventLog.add(appCtx, "❌ Kayıt başlatılamadı (hiçbir ses kaynağı açılmadı)");
            return false;
        }
        notifyListeners();
        handler.removeCallbacks(monitor);
        handler.postDelayed(monitor, 1000);
        return true;
    }

    private boolean createEntry(ContentResolver cr) {
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, "kayit_" + System.currentTimeMillis() + ".m4a");
        v.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4");
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/" + FOLDER);
        v.put(MediaStore.MediaColumns.IS_PENDING, 1);
        v.put(MediaStore.Audio.Media.IS_MUSIC, 0);
        try {
            uri = cr.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
            if (uri == null) { EventLog.add(appCtx, "❌ Dosya oluşturulamadı"); return false; }
            pfd = cr.openFileDescriptor(uri, "rw");
            if (pfd == null) { cleanupFailed(cr); return false; }
            return true;
        } catch (Exception e) {
            EventLog.add(appCtx, "❌ Dosya hatası: " + e.getClass().getSimpleName());
            cleanupFailed(cr);
            return false;
        }
    }

    private boolean openAndStart(int fromIdx) {
        ContentResolver cr = appCtx.getContentResolver();
        if (!createEntry(cr)) return false;
        for (int i = fromIdx; i < chain.length; i++) {
            int src = chain[i];
            MediaRecorder r = newRecorder(appCtx);
            try {
                r.setAudioSource(src);
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                r.setAudioChannels(1);
                r.setAudioSamplingRate(44100);
                r.setAudioEncodingBitRate(96000);
                r.setOutputFile(pfd.getFileDescriptor());
                r.prepare();
                r.start();
                recorder = r;
                chainIdx = i;
                usedSource = src;
                EventLog.add(appCtx, "🎙️ Kayıt başladı · kaynak: " + Prefs.sourceName(src));
                return true;
            } catch (Throwable e) {
                EventLog.add(appCtx, "⚠️ Kaynak açılamadı: " + Prefs.sourceName(src) + " (" + e.getClass().getSimpleName() + ")");
                try { r.reset(); } catch (Throwable ignored) { }
                try { r.release(); } catch (Throwable ignored) { }
            }
        }
        cleanupFailed(cr);
        return false;
    }

    /** Her saniye ses seviyesini ölçer; hiç ses yoksa sıradaki kaynağa geçer. */
    private final Runnable monitor = new Runnable() {
        @Override
        public void run() {
            synchronized (CallRecorder.this) {
                if (recorder == null) return;
                int a = 0;
                try { a = recorder.getMaxAmplitude(); } catch (Throwable ignored) { }
                level = a;
                if (a > 0) {
                    if (!heard) EventLog.add(appCtx, "🔊 Ses alınıyor (" + Prefs.sourceName(usedSource) + ")");
                    heard = true;
                } else if (!heard) {
                    silentSecs++;
                    if (silentSecs >= 5) {
                        if (chainIdx + 1 < chain.length) {
                            EventLog.add(appCtx, "🔇 " + Prefs.sourceName(usedSource) + " sessiz, sonraki kaynak deneniyor");
                            discardCurrent();
                            silentSecs = 0;
                            if (!openAndStart(chainIdx + 1)) {
                                EventLog.add(appCtx, "❌ Sesli kaynak bulunamadı. Mikrofon izni ve erişilebilirlik hizmetini kontrol edin.");
                                notifyListeners();
                                return;
                            }
                        } else if (silentSecs == 5) {
                            EventLog.add(appCtx, "🔇 Hiçbir kaynaktan ses gelmiyor. Telefon mikrofonu bu uygulamaya kapatıyor olabilir.");
                        }
                    }
                }
            }
            handler.postDelayed(this, 1000);
        }
    };

    private void discardCurrent() {
        try { recorder.stop(); } catch (Throwable ignored) { }
        try { recorder.release(); } catch (Throwable ignored) { }
        recorder = null;
        cleanupFailed(appCtx.getContentResolver());
    }

    @SuppressWarnings("deprecation")
    private static MediaRecorder newRecorder(Context ctx) {
        if (Build.VERSION.SDK_INT >= 31) return new MediaRecorder(ctx);
        return new MediaRecorder();
    }

    private void cleanupFailed(ContentResolver cr) {
        try { if (pfd != null) pfd.close(); } catch (Exception ignored) { }
        pfd = null;
        try { if (uri != null) cr.delete(uri, null, null); } catch (Exception ignored) { }
        uri = null;
    }

    public synchronized void stop(Context ctx) {
        handler.removeCallbacks(monitor);
        level = 0;
        if (recorder == null) return;
        final Context app = ctx.getApplicationContext();
        final ContentResolver cr = app.getContentResolver();
        boolean ok = true;
        try { recorder.stop(); } catch (Throwable e) { ok = false; }
        try { recorder.release(); } catch (Throwable ignored) { }
        recorder = null;
        try { if (pfd != null) pfd.close(); } catch (Exception ignored) { }
        pfd = null;
        final Uri u = uri;
        uri = null;
        final long start = startMs;
        final long dur = System.currentTimeMillis() - startMs;
        final boolean inc = incoming;
        notifyListeners();
        if (u == null) return;
        if (!ok || dur < 1500) {
            EventLog.add(app, "⚠️ Kayıt çok kısa / hatalı, silindi");
            try { cr.delete(u, null, null); } catch (Exception ignored) { }
            return;
        }
        EventLog.add(app, "✅ Kayıt kaydedildi · " + fmtDur(dur) + (heard ? "" : " · (ses algılanmadı!)"));
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.IS_PENDING, 0);
        try { cr.update(u, v, null, null); } catch (Exception ignored) { }
        // Arama kaydı (call log) birkaç saniye içinde yazılır; sonra dosyayı adlandır.
        handler.postDelayed(() -> new Thread(() -> finish(app, u, start, dur, inc)).start(), 2500);
    }

    private static void finish(Context c, Uri u, long start, long dur, boolean incoming) {
        String number = "";
        String name = "";
        boolean inc = incoming;
        ContentResolver cr = c.getContentResolver();

        if (c.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
            try (Cursor cur = cr.query(CallLog.Calls.CONTENT_URI,
                    new String[]{CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE},
                    null, null, CallLog.Calls.DATE + " DESC")) {
                if (cur != null && cur.moveToFirst()) {
                    long d = cur.getLong(3);
                    if (Math.abs(d - start) < 10 * 60 * 1000L) {
                        number = nz(cur.getString(0));
                        int type = cur.getInt(1);
                        name = nz(cur.getString(2));
                        if (type == CallLog.Calls.OUTGOING_TYPE) inc = false;
                        else if (type == CallLog.Calls.INCOMING_TYPE) inc = true;
                    }
                }
            } catch (Exception ignored) { }
        }
        if (name.isEmpty() && !number.isEmpty()
                && c.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            try (Cursor cur = cr.query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                    new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
                if (cur != null && cur.moveToFirst()) name = nz(cur.getString(0));
            } catch (Exception ignored) { }
        }

        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date(start));
        String fileName = ts + "__" + (inc ? "G" : "A") + "__" + cleanNumber(number) + "__" + cleanName(name) + ".m4a";
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        try { cr.update(u, v, null, null); } catch (Exception ignored) { }

        String who = !name.isEmpty() ? name : (!number.isEmpty() ? number : "Bilinmeyen numara");
        Notif.show(c, "✅ Kayıt kaydedildi", (inc ? "Gelen" : "Giden") + " · " + who + " · " + fmtDur(dur));

        Handler h = new Handler(Looper.getMainLooper());
        h.post(() -> {
            for (Listener l : LISTENERS) l.onRecordingChanged(false);
        });
    }

    private static String nz(String s) { return s == null ? "" : s.trim(); }

    private static String cleanNumber(String s) {
        return s == null ? "" : s.replaceAll("[^0-9+*#]", "");
    }

    private static String cleanName(String s) {
        if (s == null) return "";
        String r = s.replaceAll("[\\\\/:*?\"<>|_\\r\\n\\t]", " ").replaceAll("\\s+", " ").trim();
        return r.length() > 60 ? r.substring(0, 60) : r;
    }

    public static String fmtDur(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, sec);
        return String.format(Locale.US, "%02d:%02d", m, sec);
    }
}
