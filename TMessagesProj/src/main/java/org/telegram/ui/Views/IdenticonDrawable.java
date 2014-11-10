/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Views;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import org.telegram.android.AndroidUtilities;
import org.telegram.messenger.Utilities;

public class IdenticonDrawable extends Drawable {
    private byte[] data;
    private Paint paint = new Paint();
    private int colors[] = {
            0xffffffff,
            0xffd5e6f3,
            0xff2d5775,
            0xff2f99c9
    };

    int get_bits(int bitOffset, int numBits) {
        numBits = (int)Math.pow(2, numBits) - 1;
        int offset = bitOffset / 8;
        bitOffset %= 8;
        int val = data[offset + 3] << 24 | data[offset + 2] << 16 | data[offset + 1] << 8 | data[offset];
        return (val >> bitOffset) & numBits;
    }

    public void setBytes(byte[] bytes) {
        if (bytes == null) {
            return;
        }
        data = Utilities.computeSHA1(bytes);
        if (data.length < 128) {
            byte[] temp = new byte[128];
            System.arraycopy(data, 0, temp, 0, data.length);
            data = temp;
        }
        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        if (data == null) {
            return;
        }

        int bitPointer = 0;
        float rectSize = (float)Math.floor(Math.min(getBounds().width(), getBounds().height()) / 8.0f);
        float xOffset = Math.max(0, (getBounds().width() - rectSize * 8) / 2);
        float yOffset = Math.max(0, (getBounds().height() - rectSize * 8) / 2);
        for (int iy = 0; iy < 8; iy++) {
            for (int ix = 0; ix < 8; ix++) {
                int byteValue = get_bits(bitPointer, 2);
                bitPointer += 2;
                int colorIndex = Math.abs(byteValue) % 4;
                paint.setColor(colors[colorIndex]);
                canvas.drawRect(xOffset + ix * rectSize, iy * rectSize + yOffset, xOffset + ix * rectSize + rectSize, iy * rectSize + rectSize + yOffset, paint);
            }
        }
    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(ColorFilter cf) {

    }

    @Override
    public int getOpacity() {
        return 0;
    }

    @Override
    public int getIntrinsicWidth() {
        return AndroidUtilities.dp(32);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(32);
    }
}
