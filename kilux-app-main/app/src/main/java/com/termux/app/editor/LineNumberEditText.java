package com.termux.app.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.Layout;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.appcompat.widget.AppCompatEditText;

/**
 * EditText with line-number gutter drawn in onDraw().
 * Mirrors AmrDeveloper/CodeView (MIT license):
 *   - Draw line numbers at x=0 before super.onDraw()
 *   - setPadding() after drawing to reserve gutter space
 *   - canvas.translate(+scrollX, 0) counteracts horizontal scroll
 *     so the gutter stays fixed at the left edge
 */
public class LineNumberEditText extends AppCompatEditText {

    private boolean lineNumbersEnabled = true;

    private final Rect lineNumberRect;
    private final Paint lineNumberPaint;
    private final Paint gutterBgPaint;
    private final Paint dividerPaint;

    private int gutterWidth;
    private int lastLogicalLineCount = -1;
    private final float density;

    public LineNumberEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        setHorizontallyScrolling(true);

        density = getResources().getDisplayMetrics().density;
        float textSize = getTextSize();

        lineNumberRect = new Rect();

        lineNumberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        lineNumberPaint.setStyle(Paint.Style.FILL);
        lineNumberPaint.setTextSize(textSize);
        lineNumberPaint.setTypeface(Typeface.MONOSPACE);
        // medium gray — visible on both light and dark backgrounds
        lineNumberPaint.setColor(0xFF888888);

        // Gutter background: try theme color, fallback to white
        TypedValue bgTv = new TypedValue();
        if (!context.getTheme().resolveAttribute(android.R.attr.colorBackground, bgTv, true)) {
            bgTv.data = 0xFFFFFFFF;
        }

        gutterBgPaint = new Paint();
        gutterBgPaint.setStyle(Paint.Style.FILL);
        gutterBgPaint.setColor(bgTv.data);

        dividerPaint = new Paint();
        dividerPaint.setStyle(Paint.Style.STROKE);
        dividerPaint.setStrokeWidth(1f * density);
        dividerPaint.setColor(0xFF888888);
    }

    public LineNumberEditText(Context context) {
        this(context, null);
    }

    public void setLineNumbersEnabled(boolean enabled) {
        if (lineNumbersEnabled == enabled) return;
        lineNumbersEnabled = enabled;
        if (!enabled) {
            setPadding(0, getPaddingTop(), getPaddingRight(), getPaddingBottom());
            gutterWidth = 0;
        } else {
            lastLogicalLineCount = -1;
        }
        invalidate();
    }

    public void setLineNumberColor(int color) {
        lineNumberPaint.setColor(color);
        invalidate();
    }

    public void setLineNumberTextSize(float sizePx) {
        lineNumberPaint.setTextSize(sizePx);
        lastLogicalLineCount = -1;
        invalidate();
    }

    public void setGutterBackgroundColor(int color) {
        gutterBgPaint.setColor(color);
        invalidate();
    }

    public void setGutterDividerColor(int color) {
        dividerPaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!lineNumbersEnabled) {
            super.onDraw(canvas);
            return;
        }

        Layout layout = getLayout();
        if (layout == null) {
            super.onDraw(canvas);
            return;
        }

        final int lineCount = getLineCount();
        CharSequence fullText = getText();
        int scrollX = getScrollX();
        int scrollY = getScrollY();

        // Recompute gutter width when logical line count changes
        int logicalCount = countLogicalLines(layout);
        if (logicalCount != lastLogicalLineCount) {
            lastLogicalLineCount = logicalCount;
            final int digitCount = Math.max(2, (int) (Math.log10(logicalCount) + 1));
            gutterWidth = (int) (lineNumberPaint.getTextSize() * digitCount);
        }

        // 1. Draw gutter — counter-scroll by +scrollX so it stays fixed at left
        canvas.save();
        canvas.translate(scrollX, 0);

        // Gutter background (covers text that might overlap during transition)
        canvas.drawRect(0, scrollY, gutterWidth, scrollY + getHeight(), gutterBgPaint);

        // Line numbers
        for (int i = 0; i < lineCount; ++i) {
            final int baseline = getLineBounds(i, null);
            if (i == 0 || fullText.charAt(layout.getLineStart(i) - 1) == '\n') {
                canvas.drawText(" " + (i + 1), lineNumberRect.left, baseline, lineNumberPaint);
            }
        }

        // Divider line
        float divX = gutterWidth - dividerPaint.getStrokeWidth() / 2f;
        canvas.drawLine(divX, scrollY, divX, scrollY + getHeight(), dividerPaint);

        canvas.restore();

        // 2. Reserve padding for gutter (takes effect next layout pass)
        setPadding(gutterWidth, getPaddingTop(), getPaddingRight(), getPaddingBottom());

        // 3. Draw text (respects padding and edittext scroll)
        super.onDraw(canvas);
    }

    private int countLogicalLines(Layout layout) {
        int count = 0;
        CharSequence text = getText();
        for (int i = 0; i < layout.getLineCount(); i++) {
            if (i == 0 || text.charAt(layout.getLineStart(i) - 1) == '\n') {
                count++;
            }
        }
        return Math.max(1, count);
    }
}
