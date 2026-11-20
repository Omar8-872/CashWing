package com.example.quickpay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import java.util.Locale;

// simple bar chart drawn with Canvas (one bar per day)
public class BarChartView extends View {
    private double[] values = new double[0];
    private String[] labels = new String[0];
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    public BarChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;

        barPaint.setColor(ContextCompat.getColor(context, R.color.pay_primary));
        textPaint.setColor(ContextCompat.getColor(context, R.color.pay_gray));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(12 * density);
        linePaint.setColor(ContextCompat.getColor(context, R.color.pay_stroke));
        linePaint.setStrokeWidth(density);
    }

    public void setData(double[] values, String[] labels) {
        this.values = values;
        this.labels = labels;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = values.length;
        if (n == 0) {
            return;
        }

        float width = getWidth();
        float height = getHeight();
        float chartTop = 18 * density;
        float chartBottom = height - 22 * density;

        double max = 0;
        for (double v : values) {
            max = Math.max(max, v);
        }

        canvas.drawLine(0, chartBottom, width, chartBottom, linePaint);

        float slot = width / n;
        float barWidth = slot * 0.5f;
        for (int i = 0; i < n; i++) {
            float centerX = slot * i + slot / 2;

            if (values[i] > 0) {
                float barHeight = (float) (values[i] / max * (chartBottom - chartTop));
                barHeight = Math.max(barHeight, 3 * density);
                canvas.drawRoundRect(centerX - barWidth / 2, chartBottom - barHeight,
                        centerX + barWidth / 2, chartBottom, 4 * density, 4 * density, barPaint);
                canvas.drawText(shortMoney(values[i]), centerX,
                        chartBottom - barHeight - 4 * density, textPaint);
            }

            canvas.drawText(labels[i], centerX, height - 5 * density, textPaint);
        }

        if (max == 0) {
            canvas.drawText(getContext().getString(R.string.no_spending_yet), width / 2, (chartTop + chartBottom) / 2, textPaint);
        }
    }

    private String shortMoney(double value) {
        if (value >= 1000) {
            return String.format(Locale.US, "%.1fk", value / 1000);
        }
        return String.format(Locale.US, "%.0f", value);
    }
}
