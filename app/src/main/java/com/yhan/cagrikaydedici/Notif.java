package com.yhan.cagrikaydedici;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/** Bildirim yardımcıları. */
final class Notif {
    private Notif() {}

    static final String CH_REC = "kayit_suruyor";
    static final String CH_DONE = "kayit_bitti";
    static final int ID_REC = 7001;

    static void ensure(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel a = new NotificationChannel(CH_REC, "Kayıt sürüyor", NotificationManager.IMPORTANCE_LOW);
        a.setShowBadge(false);
        NotificationChannel b = new NotificationChannel(CH_DONE, "Kayıt tamamlandı", NotificationManager.IMPORTANCE_DEFAULT);
        b.setSound(null, null);
        nm.createNotificationChannel(a);
        nm.createNotificationChannel(b);
    }

    static PendingIntent open(Context c) {
        Intent i = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static Notification recording(Context c) {
        ensure(c);
        return new Notification.Builder(c, CH_REC)
                .setSmallIcon(R.drawable.ic_stat_mic)
                .setContentTitle("🎙️ Görüşme kaydediliyor")
                .setContentText("BozokluHAN Çağrı Kaydedici")
                .setColor(0xFFFF6B6B)
                .setOngoing(true)
                .setContentIntent(open(c))
                .build();
    }

    static void show(Context c, String title, String text) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ensure(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) return;
        Notification n = new Notification.Builder(c, CH_DONE)
                .setSmallIcon(R.drawable.ic_stat_mic)
                .setContentTitle(title)
                .setContentText(text)
                .setColor(0xFFFF6B6B)
                .setAutoCancel(true)
                .setContentIntent(open(c))
                .build();
        nm.notify((int) (System.currentTimeMillis() % 100000) + 10, n);
    }
}
