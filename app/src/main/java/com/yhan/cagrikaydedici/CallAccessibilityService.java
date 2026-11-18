package com.yhan.cagrikaydedici;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * Android 10+ cihazlarda üçüncü taraf uygulamaların görüşme sırasında ses kaydedebilmesi
 * için erişilebilirlik hizmeti gerekir. Bu hizmet ekran içeriğini okumaz; yalnızca
 * arama durumunu dinler ve kaydı başlatır/durdurur.
 */
public class CallAccessibilityService extends AccessibilityService {

    private static CallAccessibilityService instance;

    private TelephonyManager tm;
    private Object callback;
    private int lastState = TelephonyManager.CALL_STATE_IDLE;
    private boolean ringing;
    private boolean speakerChanged;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public static boolean isRunning() { return instance != null; }

    public static void refreshListener() {
        if (instance != null) instance.registerCallListener();
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        tm = getSystemService(TelephonyManager.class);
        registerCallListener();
    }

    @SuppressWarnings("deprecation")
    private void registerCallListener() {
        if (callback != null || tm == null) return;
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return;
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
        } catch (Throwable e) {
            callback = null;
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
        } else if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
            boolean incoming = ringing;
            if (!Prefs.enabled(this)) return;
            if (incoming && !Prefs.incoming(this)) return;
            if (!incoming && !Prefs.outgoing(this)) return;
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
            startRecording(incoming);
        } else if (state == TelephonyManager.CALL_STATE_IDLE) {
            ringing = false;
            stopRecording();
        }
    }

    @SuppressWarnings("deprecation")
    private void startRecording(boolean incoming) {
        if (CallRecorder.get().isRecording()) return;
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(Notif.ID_REC, Notif.recording(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(Notif.ID_REC, Notif.recording(this));
            }
        } catch (Throwable ignored) { }

        boolean ok = CallRecorder.get().start(getApplicationContext(), incoming);
        if (!ok) {
            stopForegroundSafe();
            Notif.show(this, "⚠️ Kayıt başlatılamadı",
                    "Mikrofon kullanılamadı. Uygulamadaki ayarlardan farklı bir ses kaynağı deneyin.");
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
        if (instance == this) instance = null;
    }
}
