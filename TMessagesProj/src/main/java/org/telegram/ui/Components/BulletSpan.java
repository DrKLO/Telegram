package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Parcel;
import android.text.Layout;
import android.text.Spanned;
import android.text.style.LeadingMarginSpan;

public class BulletSpan implements LeadingMarginSpan {

    private static final int STANDARD_BULLET_RADIUS = 4;
    public static final int STANDARD_GAP_WIDTH = 2;
    private static final int STANDARD_COLOR = 0;

    private final int mGapWidth;
    private final int mBulletRadius;
    private final int mColor;
    private final boolean mWantColor;

    /**
     * Creates a {@link BulletSpan} with the default values.
     */
    public BulletSpan() {
        this(STANDARD_GAP_WIDTH, STANDARD_COLOR, false, STANDARD_BULLET_RADIUS);
    }

    /**
     * Creates a {@link BulletSpan} based on a gap width
     *
     * @param gapWidth the distance, in pixels, between the bullet point and the paragraph.
     */
    public BulletSpan(int gapWidth) {
        this(gapWidth, STANDARD_COLOR, false, STANDARD_BULLET_RADIUS);
    }

    /**
     * Creates a {@link BulletSpan} based on a gap width and a color integer.
     *
     * @param gapWidth the distance, in pixels, between the bullet point and the paragraph.
     * @param color    the bullet point color, as a color integer
     * @see android.content.res.Resources#getColor(int, Resources.Theme)
     */
    public BulletSpan(int gapWidth, int color) {
        this(gapWidth, color, true, STANDARD_BULLET_RADIUS);
    }

    /**
     * Creates a {@link BulletSpan} based on a gap width and a color integer.
     *
     * @param gapWidth     the distance, in pixels, between the bullet point and the paragraph.
     * @param color        the bullet point color, as a color integer.
     * @param bulletRadius the radius of the bullet point, in pixels.
     * @see android.content.res.Resources#getColor(int, Resources.Theme)
     */
    public BulletSpan(int gapWidth, int color, int bulletRadius) {
        this(gapWidth, color, true, bulletRadius);
    }

    private BulletSpan(int gapWidth, int color, boolean wantColor, int bulletRadius) {
        mGapWidth = gapWidth;
        mBulletRadius = bulletRadius;
        mColor = color;
        mWantColor = wantColor;
    }

    /**
     * Creates a {@link BulletSpan} from a parcel.
     */
    public BulletSpan(Parcel src) {
        mGapWidth = src.readInt();
        mWantColor = src.readInt() != 0;
        mColor = src.readInt();
        mBulletRadius = src.readInt();
    }

    @Override
    public int getLeadingMargin(boolean first) {
        return 2 * mBulletRadius + mGapWidth;
    }

    /**
     * Get the distance, in pixels, between the bullet point and the paragraph.
     *
     * @return the distance, in pixels, between the bullet point and the paragraph.
     */
    public int getGapWidth() {
        return mGapWidth;
    }

    /**
     * Get the radius, in pixels, of the bullet point.
     *
     * @return the radius, in pixels, of the bullet point.
     */
    public int getBulletRadius() {
        return mBulletRadius;
    }

    /**
     * Get the bullet point color.
     *
     * @return the bullet point color
     */
    public int getColor() {
        return mColor;
    }

    @Override
    public void drawLeadingMargin(Canvas canvas, Paint paint, int x, int dir,
                                  int top, int baseline, int bottom,
                                  CharSequence text, int start, int end,
                                  boolean first, Layout layout) {
        if (((Spanned) text).getSpanStart(this) == start) {
            Paint.Style style = paint.getStyle();
            int oldcolor = 0;

            if (mWantColor) {
                oldcolor = paint.getColor();
                paint.setColor(mColor);
            }

            paint.setStyle(Paint.Style.FILL);

            if (layout != null) {
                // "bottom" position might include extra space as a result of line spacing
                // configuration. Subtract extra space in order to show bullet in the vertical
                // center of characters.
                final int line = layout.getLineForOffset(start);
                int spacing = line != layout.getLineCount() - 1 ? (int) layout.getSpacingAdd() : 0;
                bottom = bottom - spacing;
            }

            final float yPosition = (top + bottom) / 2f;
            final float xPosition = x + dir * mBulletRadius;

            canvas.drawCircle(xPosition, yPosition, mBulletRadius, paint);

            if (mWantColor) {
                paint.setColor(oldcolor);
            }

            paint.setStyle(style);
        }
    }
}
