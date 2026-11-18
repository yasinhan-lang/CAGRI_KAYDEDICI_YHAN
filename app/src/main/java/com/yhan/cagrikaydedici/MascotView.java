package com.yhan.cagrikaydedici;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/** Sempatik mikrofon karakteri: uyuyor / hazır / kayıtta. */
public class MascotView extends View {
    public static final int SLEEP = 0, READY = 1, REC = 2;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final long t0 = SystemClock.uptimeMillis();
    private int state = READY;

    public MascotView(Context c) { super(c); }

    public void setState(int s) {
        if (s != state) { state = s; invalidate(); }
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f, r = Math.min(w, h) / 2f;
        float t = (SystemClock.uptimeMillis() - t0) / 1000f;

        p.setStyle(Paint.Style.FILL);
        p.setColor(state == REC ? 0xFFFFE0E0 : state == SLEEP ? 0xFFEFF0F5 : 0xFFFFEDE5);
        c.drawCircle(cx, cy, r * 0.96f, p);

        if (state == REC) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(r * 0.05f);
            for (int k = 0; k < 2; k++) {
                float ph = ((t * 0.7f) + k * 0.5f) % 1f;
                p.setColor(0xFFFF6B6B);
                p.setAlpha((int) ((1 - ph) * 170));
                c.drawCircle(cx, cy, r * (0.62f + ph * 0.32f), p);
            }
            p.setAlpha(255);
        }

        float bob = (float) Math.sin(t * (state == SLEEP ? 1.2 : 2.4)) * r * 0.035f;
        float bw = r * 0.62f, bh = r * 0.86f;
        rf.set(cx - bw / 2, cy - bh * 0.68f + bob, cx + bw / 2, cy + bh * 0.32f + bob);
        int body = state == SLEEP ? 0xFFE9A3A3 : 0xFFFF6B6B;

        // tutucu (U) ve ayak
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(r * 0.075f);
        p.setColor(0xFF2D3047);
        RectF arc = new RectF(cx - bw * 0.82f, rf.top + bh * 0.18f, cx + bw * 0.82f, rf.bottom + bh * 0.16f);
        c.drawArc(arc, 15, 150, false, p);
        c.drawLine(cx, arc.bottom, cx, arc.bottom + r * 0.13f, p);
        c.drawLine(cx - r * 0.2f, arc.bottom + r * 0.13f, cx + r * 0.2f, arc.bottom + r * 0.13f, p);

        // gövde
        p.setStyle(Paint.Style.FILL);
        p.setColor(body);
        c.drawRoundRect(rf, bw / 2, bw / 2, p);
        // parlama
        p.setColor(0x55FFFFFF);
        c.drawOval(new RectF(rf.left + bw * 0.14f, rf.top + bh * 0.08f, rf.left + bw * 0.36f, rf.top + bh * 0.30f), p);
        // ızgara çizgileri
        p.setColor(0x33FFFFFF);
        p.setStrokeWidth(r * 0.025f);
        p.setStyle(Paint.Style.STROKE);
        for (int i = 0; i < 3; i++) {
            float yy = rf.top + bh * (0.12f + i * 0.07f);
            c.drawLine(cx + bw * 0.02f, yy, cx + bw * 0.28f, yy, p);
        }

        // gözler
        float ey = rf.top + bh * 0.46f, ex = bw * 0.2f;
        p.setColor(0xFF2D3047);
        if (state == SLEEP) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(r * 0.04f);
            c.drawArc(new RectF(cx - ex - r * 0.07f, ey - r * 0.05f, cx - ex + r * 0.07f, ey + r * 0.05f), 0, 180, false, p);
            c.drawArc(new RectF(cx + ex - r * 0.07f, ey - r * 0.05f, cx + ex + r * 0.07f, ey + r * 0.05f), 0, 180, false, p);
        } else {
            float bp = t % 3.4f;
            float open = bp < 0.14f ? Math.abs(bp - 0.07f) / 0.07f : 1f;
            float eh = r * 0.10f * Math.max(0.12f, open), ew = r * 0.075f;
            p.setStyle(Paint.Style.FILL);
            c.drawOval(new RectF(cx - ex - ew / 2, ey - eh / 2, cx - ex + ew / 2, ey + eh / 2), p);
            c.drawOval(new RectF(cx + ex - ew / 2, ey - eh / 2, cx + ex + ew / 2, ey + eh / 2), p);
            if (open > 0.6f) {
                p.setColor(0xFFFFFFFF);
                c.drawCircle(cx - ex + ew * 0.18f, ey - eh * 0.2f, ew * 0.18f, p);
                c.drawCircle(cx + ex + ew * 0.18f, ey - eh * 0.2f, ew * 0.18f, p);
            }
        }
        // yanaklar
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x88FFC2C2);
        c.drawOval(new RectF(cx - ex - r * 0.13f, ey + r * 0.06f, cx - ex - r * 0.01f, ey + r * 0.13f), p);
        c.drawOval(new RectF(cx + ex + r * 0.01f, ey + r * 0.06f, cx + ex + r * 0.13f, ey + r * 0.13f), p);

        // ağız
        p.setColor(0xFF2D3047);
        float my = ey + r * 0.13f;
        if (state == REC) {
            float mo = r * (0.05f + 0.025f * (float) Math.abs(Math.sin(t * 7)));
            p.setStyle(Paint.Style.FILL);
            c.drawOval(new RectF(cx - r * 0.06f, my - mo / 2, cx + r * 0.06f, my + mo), p);
        } else if (state == READY) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(r * 0.04f);
            c.drawArc(new RectF(cx - r * 0.09f, my - r * 0.07f, cx + r * 0.09f, my + r * 0.07f), 20, 140, false, p);
        } else {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(r * 0.035f);
            c.drawLine(cx - r * 0.04f, my + r * 0.02f, cx + r * 0.04f, my + r * 0.02f, p);
            // z z
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF8A8FA3);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            float zt = (t * 0.6f) % 1f;
            p.setAlpha((int) (255 * (1 - zt)));
            p.setTextSize(r * (0.16f + zt * 0.08f));
            c.drawText("z", cx + bw * 0.55f + zt * r * 0.12f, rf.top + bh * 0.2f - zt * r * 0.2f, p);
            p.setAlpha(255);
        }

        // REC ışığı
        if (state == REC && (t % 1.0f) < 0.6f) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFE53935);
            c.drawCircle(cx + r * 0.58f, cy - r * 0.58f, r * 0.09f, p);
        }
        if (isShown()) postInvalidateOnAnimation();
    }
}
