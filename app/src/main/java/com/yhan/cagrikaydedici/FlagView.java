package com.yhan.cagrikaydedici;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/** Dalgalanan Türk bayrağı. */
public class FlagView extends View {
    private static final int MW = 24, MH = 8;
    private final float[] verts = new float[(MW + 1) * (MH + 1) * 2];
    private final int[] colors = new int[(MW + 1) * (MH + 1)];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint pole = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long t0 = SystemClock.uptimeMillis();
    private final float d;
    private Bitmap flag;
    private float left, top, fw, fh, amp, poleW;

    public FlagView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (w <= 0 || h <= 0) return;
        poleW = 2.5f * d;
        amp = h * 0.07f;
        float availW = w - poleW - 2 * d - amp;
        float availH = h - 2 * amp - 2 * d;
        fh = Math.min(availH, availW * 2f / 3f);
        fw = fh * 1.5f;
        left = (w - (poleW + 1 * d + fw)) / 2f + poleW + 1 * d;
        top = (h - fh) / 2f + d;
        flag = buildFlag(Math.max(1, Math.round(fw * 2)), Math.max(1, Math.round(fh * 2)));
    }

    private static Bitmap buildFlag(int bw, int bh) {
        Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float g = bh;
        c.drawColor(0xFFE30A17);
        p.setColor(Color.WHITE);
        c.drawCircle(g * 0.53125f, g * 0.5f, g * 0.25f, p);
        p.setColor(0xFFE30A17);
        c.drawCircle(g * 0.59375f, g * 0.5f, g * 0.2f, p);
        p.setColor(Color.WHITE);
        float cx = g * 0.8542f, cy = g * 0.5f, R = g * 0.125f, r = R * 0.382f;
        Path s = new Path();
        for (int i = 0; i < 10; i++) {
            double a = Math.PI + i * Math.PI / 5.0;
            float rr = (i % 2 == 0) ? R : r;
            float x = cx + (float) (Math.cos(a) * rr);
            float y = cy + (float) (Math.sin(a) * rr);
            if (i == 0) s.moveTo(x, y); else s.lineTo(x, y);
        }
        s.close();
        c.drawPath(s, p);
        return b;
    }

    @Override
    protected void onDraw(Canvas c) {
        if (flag == null) return;
        float t = (SystemClock.uptimeMillis() - t0) / 1000f;
        float phase = t * 5.0f;
        int k = 0, ci = 0;
        for (int j = 0; j <= MH; j++) {
            float fy = j / (float) MH;
            for (int i = 0; i <= MW; i++) {
                float fx = i / (float) MW;
                double wave = Math.sin(phase - fx * Math.PI * 2.4 + fy * 0.6);
                float x = left + fx * fw - (float) (fx * amp * 0.25f * (1 + Math.cos(phase - fx * Math.PI * 2.4)));
                float y = top + fy * fh + (float) (amp * fx * wave);
                verts[k++] = x;
                verts[k++] = y;
                int shade = (int) (225 + 30 * Math.cos(phase - fx * Math.PI * 2.4 + 0.8) * fx);
                shade = Math.max(170, Math.min(255, shade));
                colors[ci++] = Color.rgb(shade, shade, shade);
            }
        }
        c.drawBitmapMesh(flag, MW, MH, verts, 0, colors, 0, paint);
        float px = left - poleW - 1 * d;
        pole.setColor(0xFF8A8FA3);
        c.drawRoundRect(new RectF(px, top - amp, px + poleW, getHeight() - d), poleW / 2, poleW / 2, pole);
        pole.setColor(0xFFFFC94D);
        c.drawCircle(px + poleW / 2, top - amp, poleW * 0.9f, pole);
        if (isShown()) postInvalidateOnAnimation();
    }
}
