package com.yhan.cagrikaydedici;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/** "BOZOKLU / HAN" iki satırlı logo, aralarında ince çizgi, ince çerçeve içinde. */
public class LogoView extends View {
    private final Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    public LogoView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(1.2f * d);
        frame.setColor(0xFF2D3047);
        text.setColor(0xFF2D3047);
        text.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);
        line.setColor(0xFFFF6B6B);
        line.setStrokeWidth(1f * d);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float in = 1.5f * d;
        c.drawRoundRect(new RectF(in, in, w - in, h - in), 6 * d, 6 * d, frame);
        float size = h * 0.30f;
        text.setTextSize(size);
        float tw = text.measureText("BOZOKLU");
        if (tw > w * 0.80f) {
            size = size * (w * 0.80f) / tw;
            text.setTextSize(size);
        }
        text.setLetterSpacing(0.06f);
        c.drawText("BOZOKLU", w / 2f, h * 0.43f, text);
        c.drawLine(w * 0.18f, h * 0.53f, w * 0.82f, h * 0.53f, line);
        text.setLetterSpacing(0.30f);
        c.drawText("HAN", w / 2f + size * 0.15f, h * 0.53f + size * 1.12f, text);
    }
}
