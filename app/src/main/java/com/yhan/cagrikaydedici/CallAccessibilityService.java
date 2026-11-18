package com.yhan.cagrikaydedici;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * Android 10+ cihazlarda görüşme sırasında ses kaydı için erişilebilirlik hizmeti gerekir.
 * Bu hizmet ekran içeriğini okumaz; arama durumunu dinler, kaydı başlatır/durdurur ve
 * görüşme boyunca küçük bir yüzen kayıt simgesi gösterir.
 */
public class CallAccessibilityService extends AccessibilityService {

    private static CallAccessibilityService instance;

    private TelephonyManager tm;
    private Object callback;
    private int lastState = TelephonyManager.CALL_STATE_IDLE;
    private boolean ringing;
    private boolean callIncoming;
    private boolean inCall;
    private boolean speakerChanged;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private WindowManager wm;
    private BubbleView bubble;
    private WindowManager.LayoutParams blp;

    public static boolean isRunning() { return instance != null; }

    public static void refreshListener() {
        if (instance != null) instance.registerCallListener();
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        tm = getSystemService(TelephonyManager.class);
        wm = getSystemService(WindowManager.class);
        EventLog.add(this, "✅ Erişilebilirlik hizmeti çalışıyor");
        registerCallListener();
    }

    @SuppressWarnings("deprecation")
    private void registerCallListener() {
        if (callback != null || tm == null) return;
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            EventLog.add(this, "⚠️ Telefon izni yok, aramalar algılanamıyor");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                CallCb cb = new CallCb();
                tm.registerTelephonyCallback(getMainExecutor(), cb);
                callback = cb;
            } else {
                PhoneStateListener l = new PhoneStateListener() {
                    @Override
                    public void onCallStateChanged(int state, String phoneNumber) {
                        onState(state);
                    }
                };
                tm.listen(l, PhoneStateListener.LISTEN_CALL_STATE);
                callback = l;
            }
            EventLog.add(this, "📞 Arama dinleyicisi hazır");
        } catch (Throwable e) {
            callback = null;
            EventLog.add(this, "❌ Arama dinleyicisi kurulamadı: " + e.getClass().getSimpleName());
        }
    }

    @SuppressWarnings("deprecation")
    private void unregisterCallListener() {
        if (callback == null || tm == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                tm.unregisterTelephonyCallback((TelephonyCallback) callback);
            } else {
                tm.listen((PhoneStateListener) callback, PhoneStateListener.LISTEN_NONE);
            }
        } catch (Throwable ignored) { }
        callback = null;
    }

    private final class CallCb extends TelephonyCallback implements TelephonyCallback.CallStateListener {
        @Override
        public void onCallStateChanged(int state) {
            onState(state);
        }
    }

    private void onState(int state) {
        if (state == lastState) return;
        int prev = lastState;
        lastState = state;
        if (state == TelephonyManager.CALL_STATE_RINGING) {
            if (prev == TelephonyManager.CALL_STATE_IDLE) ringing = true;
            EventLog.add(this, "📳 Telefon çalıyor");
        } else if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
            if (inCall) return;
            inCall = true;
            callIncoming = ringing;
            EventLog.add(this, callIncoming ? "📞 Gelen arama açıldı" : "📞 Giden arama başladı");
            if (Prefs.bubble(this)) showBubble();
            if (!Prefs.enabled(this)) { EventLog.add(this, "ℹ️ Otomatik kayıt kapalı"); return; }
            if (callIncoming && !Prefs.incoming(this)) { EventLog.add(this, "ℹ️ Gelen aramalar kaydedilmiyor (ayar)"); return; }
            if (!callIncoming && !Prefs.outgoing(this)) { EventLog.add(this, "ℹ️ Giden aramalar kaydedilmiyor (ayar)"); return; }
            // Arama hattı açıldıktan kısa süre sonra başlat (ses yolu oturduktan sonra)
            handler.postDelayed(() -> { if (inCall) startRecording(); }, 700);
        } else if (state == TelephonyManager.CALL_STATE_IDLE) {
            EventLog.add(this, "📴 Arama bitti");
            ringing = false;
            inCall = false;
            stopRecording();
            hideBubble();
        }
    }

    @SuppressWarnings("deprecation")
    private void startRecording() {
        if (CallRecorder.get().isRecording()) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            EventLog.add(this, "❌ Mikrofon izni yok");
            Notif.show(this, "⚠️ Kayıt yapılamadı", "Mikrofon iznini uygulamadan verin.");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(Notif.ID_REC, Notif.recording(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(Notif.ID_REC, Notif.recording(this));
            }
        } catch (Throwable e) {
            EventLog.add(this, "ℹ️ Ön plan bildirimi açılamadı: " + e.getClass().getSimpleName());
        }

        boolean ok = CallRecorder.get().start(getApplicationContext(), callIncoming);
        if (!ok) {
            stopForegroundSafe();
            Notif.show(this, "⚠️ Kayıt başlatılamadı",
                    "Mikrofon kullanılamadı. Uygulamadaki Tanılama bölümüne bakın.");
            return;
        }
        if (Prefs.speaker(this)) {
            handler.postDelayed(() -> {
                try {
                    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    if (am != null && !am.isSpeakerphoneOn()) {
                        am.setSpeakerphoneOn(true);
                        speakerChanged = true;
                    }
                } catch (Throwable ignored) { }
            }, 1200);
        }
    }

    @SuppressWarnings("deprecation")
    private void stopRecording() {
        handler.removeCallbacksAndMessages(null);
        if (speakerChanged) {
            try {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (am != null) am.setSpeakerphoneOn(false);
            } catch (Throwable ignored) { }
            speakerChanged = false;
        }
        CallRecorder.get().stop(getApplicationContext());
        stopForegroundSafe();
    }

    private void stopForegroundSafe() {
        try { stopForeground(Service.STOP_FOREGROUND_REMOVE); } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------ yüzen simge

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @SuppressLint("ClickableViewAccessibility")
    private void showBubble() {
        if (bubble != null || wm == null) return;
        try {
            bubble = new BubbleView(this);
            int w = dp(62), h = dp(80);
            blp = new WindowManager.LayoutParams(w, h,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            blp.gravity = Gravity.TOP | Gravity.START;
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int x = Prefs.bubbleX(this), y = Prefs.bubbleY(this);
            blp.x = x >= 0 ? Math.min(x, dm.widthPixels - w) : dm.widthPixels - w - dp(8);
            blp.y = y >= 0 ? Math.min(y, dm.heightPixels - h) : dm.heightPixels / 3;

            final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
            bubble.setOnTouchListener((v, e) -> {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setTag(new float[]{e.getRawX(), e.getRawY(), blp.x, blp.y, 0});
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float[] s = (float[]) v.getTag();
                        float dx = e.getRawX() - s[0], dy = e.getRawY() - s[1];
                        if (Math.abs(dx) > slop || Math.abs(dy) > slop) s[4] = 1;
                        if (s[4] == 1) {
                            blp.x = (int) (s[2] + dx);
                            blp.y = (int) (s[3] + dy);
                            try { wm.updateViewLayout(bubble, blp); } catch (Throwable ignored) { }
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP: {
                        float[] s = (float[]) v.getTag();
                        if (s != null && s[4] == 1) {
                            Prefs.setBubblePos(this, blp.x, blp.y);
                        } else {
                            toggleManual();
                        }
                        return true;
                    }
                    default:
                        return false;
                }
            });
            wm.addView(bubble, blp);
        } catch (Throwable e) {
            bubble = null;
            EventLog.add(this, "ℹ️ Yüzen simge gösterilemedi: " + e.getClass().getSimpleName());
        }
    }

    private void toggleManual() {
        if (CallRecorder.get().isRecording()) {
            EventLog.add(this, "⏹️ Kayıt simgeden durduruldu");
            stopRecording();
        } else if (inCall) {
            EventLog.add(this, "⏺️ Kayıt simgeden başlatıldı");
            startRecording();
        }
    }

    private void hideBubble() {
        if (bubble == null || wm == null) return;
        try { wm.removeView(bubble); } catch (Throwable ignored) { }
        bubble = null;
    }

    // ------------------------------------------------------------------

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }

    @Override
    public boolean onUnbind(Intent intent) {
        shutdown();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        shutdown();
        super.onDestroy();
    }

    private void shutdown() {
        unregisterCallListener();
        if (CallRecorder.get().isRecording()) stopRecording();
        hideBubble();
        if (instance == this) {
            instance = null;
            EventLog.add(this, "⚠️ Erişilebilirlik hizmeti durdu");
        }
    }
}
