/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import org.telegram.android.AndroidUtilities;
import org.telegram.messenger.TLRPC;
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

    private int getBits(int bitOffset) {
        return (data[bitOffset / 8] >> (bitOffset % 8)) & 0x3;
    }

    public void setEncryptedChat(TLRPC.EncryptedChat encryptedChat) {
        data = encryptedChat.key_hash;
        if (data == null) {
            byte[] sha1 = Utilities.computeSHA1(encryptedChat.auth_key);
            encryptedChat.key_hash = data = new byte[16];
            System.arraycopy(sha1, 0, data, 0, data.length);
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
                int byteValue = getBits(bitPointer);
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
