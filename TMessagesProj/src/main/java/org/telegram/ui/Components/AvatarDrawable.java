/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

public class AvatarDrawable extends Drawable {

    private TextPaint namePaint;
    private int color;
    private StaticLayout textLayout;
    private float textWidth;
    private float textHeight;
    private float textLeft;
    private boolean isProfile;
    private boolean drawBrodcast;
    private int savedMessages;
    private boolean drawPhoto;
    private StringBuilder stringBuilder = new StringBuilder(5);

    public AvatarDrawable() {
        super();

        namePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        namePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        namePaint.setTextSize(AndroidUtilities.dp(18));
    }

    public AvatarDrawable(TLRPC.User user) {
        this(user, false);
    }

    public AvatarDrawable(TLRPC.Chat chat) {
        this(chat, false);
    }

    public AvatarDrawable(TLRPC.User user, boolean profile) {
        this();
        isProfile = profile;
        if (user != null) {
            setInfo(user.id, user.first_name, user.last_name, false, null);
        }
    }

    public AvatarDrawable(TLRPC.Chat chat, boolean profile) {
        this();
        isProfile = profile;
        if (chat != null) {
            setInfo(chat.id, chat.title, null, chat.id < 0, null);
        }
    }

    public void setProfile(boolean value) {
        isProfile = value;
    }

    public static int getColorIndex(int id) {
        if (id >= 0 && id < 7) {
            return id;
        }
        return Math.abs(id % Theme.keys_avatar_background.length);
    }

    public static int getColorForId(int id) {
        return Theme.getColor(Theme.keys_avatar_background[getColorIndex(id)]);
    }

    public static int getButtonColorForId(int id) {
        return Theme.getColor(Theme.keys_avatar_actionBarSelector[getColorIndex(id)]);
    }

    public static int getIconColorForId(int id) {
        return Theme.getColor(Theme.keys_avatar_actionBarIcon[getColorIndex(id)]);
    }

    public static int getProfileColorForId(int id) {
        return Theme.getColor(Theme.keys_avatar_backgroundInProfile[getColorIndex(id)]);
    }

    public static int getProfileTextColorForId(int id) {
        return Theme.getColor(Theme.keys_avatar_subtitleInProfile[getColorIndex(id)]);
    }

    public static int getProfileBackColorForId(int id) {
        return Theme.getColor(Theme.keys_avatar_backgroundActionBar[getColorIndex(id)]);
    }

    public static int getNameColorForId(int id) {
        return Theme.getColor(Theme.keys_avatar_nameInMessage[getColorIndex(id)]);
    }

    public void setInfo(TLRPC.User user) {
        if (user != null) {
            setInfo(user.id, user.first_name, user.last_name, false, null);
        }
    }

    public void setSavedMessages(int value) {
        savedMessages = value;
        color = Theme.getColor(Theme.key_avatar_backgroundSaved);
    }

    public void setInfo(TLRPC.Chat chat) {
        if (chat != null) {
            setInfo(chat.id, chat.title, null, chat.id < 0, null);
        }
    }

    public void setColor(int value) {
        color = value;
    }

    public void setTextSize(int size) {
        namePaint.setTextSize(size);
    }

    public void setInfo(int id, String firstName, String lastName, boolean isBroadcast) {
        setInfo(id, firstName, lastName, isBroadcast, null);
    }

    public int getColor() {
        return color;
    }

    public void setInfo(int id, String firstName, String lastName, boolean isBroadcast, String custom) {
        if (isProfile) {
            color = getProfileColorForId(id);
        } else {
            color = getColorForId(id);
        }

        drawBrodcast = isBroadcast;
        savedMessages = 0;

        if (firstName == null || firstName.length() == 0) {
            firstName = lastName;
            lastName = null;
        }

        stringBuilder.setLength(0);
        if (custom != null) {
            stringBuilder.append(custom);
        } else {
            if (firstName != null && firstName.length() > 0) {
                stringBuilder.appendCodePoint(firstName.codePointAt(0));
            }
            if (lastName != null && lastName.length() > 0) {
                Integer lastch = null;
                for (int a = lastName.length() - 1; a >= 0; a--) {
                    if (lastch != null && lastName.charAt(a) == ' ') {
                        break;
                    }
                    lastch = lastName.codePointAt(a);
                }
                if (Build.VERSION.SDK_INT > 17) {
                    stringBuilder.append("\u200C");
                }
                stringBuilder.appendCodePoint(lastch);
            } else if (firstName != null && firstName.length() > 0) {
                for (int a = firstName.length() - 1; a >= 0; a--) {
                    if (firstName.charAt(a) == ' ') {
                        if (a != firstName.length() - 1 && firstName.charAt(a + 1) != ' ') {
                            if (Build.VERSION.SDK_INT > 17) {
                                stringBuilder.append("\u200C");
                            }
                            stringBuilder.appendCodePoint(firstName.codePointAt(a + 1));
                            break;
                        }
                    }
                }
            }
        }

        if (stringBuilder.length() > 0) {
            String text = stringBuilder.toString().toUpperCase();
            try {
                textLayout = new StaticLayout(text, namePaint, AndroidUtilities.dp(100), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                if (textLayout.getLineCount() > 0) {
                    textLeft = textLayout.getLineLeft(0);
                    textWidth = textLayout.getLineWidth(0);
                    textHeight = textLayout.getLineBottom(0);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        } else {
            textLayout = null;
        }
    }

    public void setDrawPhoto(boolean value) {
        drawPhoto = value;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds == null) {
            return;
        }
        int size = bounds.width();
        namePaint.setColor(Theme.getColor(Theme.key_avatar_text));
        Theme.avatar_backgroundPaint.setColor(color);
        canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.drawCircle(size / 2.0f, size / 2.0f, size / 2.0f, Theme.avatar_backgroundPaint);

        if (savedMessages != 0 && Theme.avatar_savedDrawable != null) {
            int w = Theme.avatar_savedDrawable.getIntrinsicWidth();
            int h = Theme.avatar_savedDrawable.getIntrinsicHeight();
            if (savedMessages == 2) {
                w *= 0.8f;
                h *= 0.8f;
            }
            int x = (size - w) / 2;
            int y = (size - h) / 2;
            Theme.avatar_savedDrawable.setBounds(x, y, x + w, y + h);
            Theme.avatar_savedDrawable.draw(canvas);
        } else if (drawBrodcast && Theme.avatar_broadcastDrawable != null) {
            int x = (size - Theme.avatar_broadcastDrawable.getIntrinsicWidth()) / 2;
            int y = (size - Theme.avatar_broadcastDrawable.getIntrinsicHeight()) / 2;
            Theme.avatar_broadcastDrawable.setBounds(x, y, x + Theme.avatar_broadcastDrawable.getIntrinsicWidth(), y + Theme.avatar_broadcastDrawable.getIntrinsicHeight());
            Theme.avatar_broadcastDrawable.draw(canvas);
        } else {
            if (textLayout != null) {
                canvas.translate((size - textWidth) / 2 - textLeft, (size - textHeight) / 2);
                textLayout.draw(canvas);
            } else if (drawPhoto && Theme.avatar_photoDrawable != null) {
                int x = (size - Theme.avatar_photoDrawable.getIntrinsicWidth()) / 2;
                int y = (size - Theme.avatar_photoDrawable.getIntrinsicHeight()) / 2;
                Theme.avatar_photoDrawable.setBounds(x, y, x + Theme.avatar_photoDrawable.getIntrinsicWidth(), y + Theme.avatar_photoDrawable.getIntrinsicHeight());
                Theme.avatar_photoDrawable.draw(canvas);
            }
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
        return PixelFormat.TRANSPARENT;
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
