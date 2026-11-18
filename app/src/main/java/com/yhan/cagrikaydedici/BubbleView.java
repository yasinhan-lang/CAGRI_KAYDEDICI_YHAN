package com.yhan.cagrikaydedici;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/** Görüşme sırasında ekranda duran küçük yüzen kayıt simgesi. */
public class BubbleView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final long t0 = SystemClock.uptimeMillis();

    public BubbleView(Context c) {
        super(c);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = w / 2f, r = w / 2f * 0.80f;
        float t = (SystemClock.uptimeMillis() - t0) / 1000f;
        CallRecorder rec = CallRecorder.get();
        boolean on = rec.isRecording();
        float lvl = Math.min(1f, rec.level() / 12000f);

        // ses seviyesi halkası
        if (on) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFF6B6B);
            p.setAlpha((int) (60 + 100 * lvl));
            c.drawCircle(cx, cy, r * (1.05f + 0.18f * lvl), p);
            p.setAlpha(255);
        }
        // gölge + gövde
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x33000000);
        c.drawCircle(cx, cy + r * 0.06f, r, p);
        p.setColor(on ? 0xFFFF6B6B : 0xFF8A8FA3);
        c.drawCircle(cx, cy, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r * 0.08f);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(cx, cy, r * 0.94f, p);

        // mikrofon
        float bw = r * 0.46f, bh = r * 0.72f;
        rf.set(cx - bw / 2, cy - bh * 0.62f, cx + bw / 2, cy + bh * 0.38f);
        p.setStyle(Paint.Style.FILL);
        c.drawRoundRect(rf, bw / 2, bw / 2, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(r * 0.09f);
        RectF arc = new RectF(cx - bw * 0.9f, rf.top + bh * 0.2f, cx + bw * 0.9f, rf.bottom + bh * 0.16f);
        c.drawArc(arc, 15, 150, false, p);
        c.drawLine(cx, arc.bottom, cx, arc.bottom + r * 0.14f, p);
        // gözler (sempatik)
        p.setStyle(Paint.Style.FILL);
        p.setColor(on ? 0xFFFF6B6B : 0xFF8A8FA3);
        float ey = rf.top + bh * 0.45f;
        boolean blink = (t % 3.0f) < 0.12f;
        float eh = blink ? r * 0.02f : r * 0.08f;
        c.drawOval(new RectF(cx - bw * 0.26f - r * 0.04f, ey - eh / 2, cx - bw * 0.26f + r * 0.04f, ey + eh / 2), p);
        c.drawOval(new RectF(cx + bw * 0.26f - r * 0.04f, ey - eh / 2, cx + bw * 0.26f + r * 0.04f, ey + eh / 2), p);

        // REC ışığı
        if (on && (t % 1f) < 0.6f) {
            p.setColor(0xFFE53935);
            c.drawCircle(cx + r * 0.72f, cy - r * 0.72f, r * 0.17f, p);
            p.setColor(0xFFFFFFFF);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(r * 0.05f);
            c.drawCircle(cx + r * 0.72f, cy - r * 0.72f, r * 0.17f, p);
            p.setStyle(Paint.Style.FILL);
        }

        // süre etiketi
        String label = on ? CallRecorder.fmtDur(System.currentTimeMillis() - rec.startedAt()) : "Kaydet";
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(w * 0.17f);
        p.setTextAlign(Paint.Align.CENTER);
        float tw = p.measureText(label) + w * 0.14f;
        float ty = w + (h - w) * 0.55f;
        p.setColor(0xE62D3047);
        c.drawRoundRect(new RectF(cx - tw / 2, w - w * 0.02f, cx + tw / 2, h - 1), h, h, p);
        p.setColor(0xFFFFFFFF);
        c.drawText(label, cx, ty + w * 0.06f, p);

        if (isAttachedToWindow()) postInvalidateDelayed(80);
    }
}
