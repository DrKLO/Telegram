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
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import org.telegram.android.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.TLRPC;

public class AvatarDrawable extends Drawable {

    private static Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static TextPaint namePaint;
    private static int[] arrColors = {0xffe56555, 0xfff28c48, 0xffeec764, 0xff76c84d, 0xff5fbed5, 0xff549cdd, 0xff8e85ee, 0xfff2749a};

    private int color;
    private StaticLayout textLayout;
    private float textWidth;
    private float textHeight;

    public AvatarDrawable() {
        super();

        if (namePaint == null) {
            namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            namePaint.setColor(0xffffffff);
            namePaint.setTextSize(AndroidUtilities.dp(20));
        }
    }

    public AvatarDrawable(TLRPC.User user) {
        this();
        if (user != null) {
            setInfo(user.id, user.first_name, user.last_name, false);
        }
    }

    public AvatarDrawable(TLRPC.Chat chat) {
        this();
        if (chat != null) {
            setInfo(chat.id, chat.title, null, chat.id < 0);
        }
    }

    public static int getColorForId(int id) {
        return arrColors[Math.abs(id) % arrColors.length];
    }

    public void setInfo(TLRPC.User user) {
        if (user != null) {
            setInfo(user.id, user.first_name, user.last_name, false);
        }
    }

    public void setInfo(TLRPC.Chat chat) {
        if (chat != null) {
            setInfo(chat.id, chat.title, null, chat.id < 0);
        }
    }

    public void setColor(int value) {
        color = value;
    }

    public void setInfo(int id, String firstName, String lastName, boolean isBroadcast) {
        color = arrColors[Math.abs(id) % arrColors.length];

        String text = "";
        if (firstName != null && firstName.length() > 0) {
            text += firstName.substring(0, 1);
        }
        if (lastName != null && lastName.length() > 0) {
            text += lastName.substring(0, 1);
        }
        if (text.length() > 0) {
            text = text.toUpperCase();
            try {
                textLayout = new StaticLayout(text, namePaint, AndroidUtilities.dp(100), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                if (textLayout.getLineCount() > 0) {
                    textWidth = textLayout.getLineWidth(0);
                    textHeight = textLayout.getLineBottom(0);
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        } else {
            textLayout = null;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds == null) {
            return;
        }
        int size = bounds.right - bounds.left;
        paint.setColor(color);

        canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.drawCircle(size / 2, size / 2, size / 2, paint);

        if (textLayout != null) {
            canvas.translate((size - textWidth) / 2, (size - textHeight) / 2);
            textLayout.draw(canvas);
        }
        canvas.restore();
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
        return 0;
    }

    @Override
    public int getIntrinsicHeight() {
        return 0;
    }
}
