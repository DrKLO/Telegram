/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

public class TimerDrawable extends Drawable {

    private TextPaint timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private StaticLayout timeLayout;
    private float timeWidth = 0;
    private int timeHeight = 0;
    private int time = 0;

    public TimerDrawable(Context context) {
        timePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        timePaint.setTextSize(AndroidUtilities.dp(11));

        linePaint.setStrokeWidth(AndroidUtilities.dp(1));
        linePaint.setStyle(Paint.Style.STROKE);
    }

    public void setTime(int value) {
        time = value;

        String timeString;
        if (time >= 1 && time < 60) {
            timeString = "" + value;
            if (timeString.length() < 2) {
                timeString += LocaleController.getString("SecretChatTimerSeconds", R.string.SecretChatTimerSeconds);
            }
        } else if (time >= 60 && time < 60 * 60) {
            timeString = "" + value / 60;
            if (timeString.length() < 2) {
                timeString += LocaleController.getString("SecretChatTimerMinutes", R.string.SecretChatTimerMinutes);
            }
        } else if (time >= 60 * 60 && time < 60 * 60 * 24) {
            timeString = "" + value / 60 / 60;
            if (timeString.length() < 2) {
                timeString += LocaleController.getString("SecretChatTimerHours", R.string.SecretChatTimerHours);
            }
        } else if (time >= 60 * 60 * 24 && time < 60 * 60 * 24 * 7) {
            timeString = "" + value / 60 / 60 / 24;
            if (timeString.length() < 2) {
                timeString += LocaleController.getString("SecretChatTimerDays", R.string.SecretChatTimerDays);
            }
        } else {
            timeString = "" + value / 60 / 60 / 24 / 7;
            if (timeString.length() < 2) {
                timeString += LocaleController.getString("SecretChatTimerWeeks", R.string.SecretChatTimerWeeks);
            } else if (timeString.length() > 2) {
                timeString = "c";
            }
        }
        /*
        <string name="SecretChatTimerDays">d</string>
    <string name="SecretChatTimerSeconds">s</string>
    <string name="SecretChatTimerMinutes">m</string>
         */

        timeWidth = timePaint.measureText(timeString);
        try {
            timeLayout = new StaticLayout(timeString, timePaint, (int)Math.ceil(timeWidth), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            timeHeight = timeLayout.getHeight();
        } catch (Exception e) {
            timeLayout = null;
            FileLog.e(e);
        }

        invalidateSelf();
    }

    @Override
    public void draw(Canvas canvas) {
        int width = getIntrinsicWidth();
        int height = getIntrinsicHeight();


        if (time == 0) {
            paint.setColor(Theme.getColor(Theme.key_chat_secretTimerBackground));
            linePaint.setColor(Theme.getColor(Theme.key_chat_secretTimerText));

            canvas.drawCircle(AndroidUtilities.dpf2(9), AndroidUtilities.dpf2(9), AndroidUtilities.dpf2(7.5f), paint);
            canvas.drawCircle(AndroidUtilities.dpf2(9), AndroidUtilities.dpf2(9), AndroidUtilities.dpf2(8), linePaint);

            paint.setColor(Theme.getColor(Theme.key_chat_secretTimerText));
            canvas.drawLine(AndroidUtilities.dp(9), AndroidUtilities.dp(9), AndroidUtilities.dp(13), AndroidUtilities.dp(9), linePaint);
            canvas.drawLine(AndroidUtilities.dp(9), AndroidUtilities.dp(5), AndroidUtilities.dp(9), AndroidUtilities.dp(9.5f), linePaint);

            canvas.drawRect(AndroidUtilities.dpf2(7), AndroidUtilities.dpf2(0), AndroidUtilities.dpf2(11), AndroidUtilities.dpf2(1.5f), paint);
        } else {
            paint.setColor(Theme.getColor(Theme.key_chat_secretTimerBackground));
            timePaint.setColor(Theme.getColor(Theme.key_chat_secretTimerText));
            canvas.drawCircle(AndroidUtilities.dp(9.5f), AndroidUtilities.dp(9.5f), AndroidUtilities.dp(9.5f), paint);
        }

        if (time != 0 && timeLayout != null) {
            int xOffxet = 0;
            if (AndroidUtilities.density == 3) {
                xOffxet = -1;
            }
            canvas.translate((int)(width / 2 - Math.ceil(timeWidth / 2)) + xOffxet, (height - timeHeight) / 2);
            timeLayout.draw(canvas);
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
        return PixelFormat.TRANSPARENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return AndroidUtilities.dp(19);
    }

    @Override
    public int getIntrinsicHeight() {
        return AndroidUtilities.dp(19);
    }
}
