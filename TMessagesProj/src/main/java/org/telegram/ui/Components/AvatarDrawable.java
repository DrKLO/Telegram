/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
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

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

public class AvatarDrawable extends Drawable {

    private TextPaint namePaint;
    private int color;
    private boolean needApplyColorAccent;
    private StaticLayout textLayout;
    private float textWidth;
    private float textHeight;
    private float textLeft;
    private boolean isProfile;
    private boolean drawDeleted;
    private int avatarType;
    private float archivedAvatarProgress;
    private boolean smallSize;
    private StringBuilder stringBuilder = new StringBuilder(5);

    public static final int AVATAR_TYPE_NORMAL = 0;
    public static final int AVATAR_TYPE_SAVED = 1;
    public static final int AVATAR_TYPE_ARCHIVED = 2;
    public static final int AVATAR_TYPE_SHARES = 3;
    public static final int AVATAR_TYPE_REPLIES = 12;

    public static final int AVATAR_TYPE_FILTER_CONTACTS = 4;
    public static final int AVATAR_TYPE_FILTER_NON_CONTACTS = 5;
    public static final int AVATAR_TYPE_FILTER_GROUPS = 6;
    public static final int AVATAR_TYPE_FILTER_CHANNELS = 7;
    public static final int AVATAR_TYPE_FILTER_BOTS = 8;
    public static final int AVATAR_TYPE_FILTER_MUTED = 9;
    public static final int AVATAR_TYPE_FILTER_READ = 10;
    public static final int AVATAR_TYPE_FILTER_ARCHIVED = 11;

    private int alpha = 255;

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
            setInfo(user.id, user.first_name, user.last_name, null);
            drawDeleted = UserObject.isDeleted(user);
        }
    }

    public AvatarDrawable(TLRPC.Chat chat, boolean profile) {
        this();
        isProfile = profile;
        if (chat != null) {
            setInfo(chat.id, chat.title, null, null);
        }
    }

    public void setProfile(boolean value) {
        isProfile = value;
    }

    private static int getColorIndex(int id) {
        if (id >= 0 && id < 7) {
            return id;
        }
        return Math.abs(id % Theme.keys_avatar_background.length);
    }

    public static int getColorForId(int id) {
        return Theme.getColor(Theme.keys_avatar_background[getColorIndex(id)]);
    }

    public static int getButtonColorForId(int id) {
        return Theme.getColor(Theme.key_avatar_actionBarSelectorBlue);
    }

    public static int getIconColorForId(int id) {
        return Theme.getColor(Theme.key_avatar_actionBarIconBlue);
    }

    public static int getProfileColorForId(int id) {
        return Theme.getColor(Theme.keys_avatar_background[getColorIndex(id)]);
    }

    public static int getProfileTextColorForId(int id) {
        return Theme.getColor(Theme.key_avatar_subtitleInProfileBlue);
    }

    public static int getProfileBackColorForId(int id) {
        return Theme.getColor(Theme.key_avatar_backgroundActionBarBlue);
    }

    public static int getNameColorForId(int id) {
        return Theme.getColor(Theme.keys_avatar_nameInMessage[getColorIndex(id)]);
    }

    public void setInfo(TLRPC.User user) {
        if (user != null) {
            setInfo(user.id, user.first_name, user.last_name, null);
            drawDeleted = UserObject.isDeleted(user);
        }
    }

    public void setInfo(TLObject object) {
        if (object instanceof TLRPC.User) {
            setInfo((TLRPC.User) object);
        } else if (object instanceof TLRPC.Chat) {
            setInfo((TLRPC.Chat) object);
        }
    }

    public void setSmallSize(boolean value) {
        smallSize = value;
    }

    public void setAvatarType(int value) {
        avatarType = value;
        if (avatarType == AVATAR_TYPE_ARCHIVED) {
            color = Theme.getColor(Theme.key_avatar_backgroundArchivedHidden);
        } else if (avatarType == AVATAR_TYPE_REPLIES) {
            color = Theme.getColor(Theme.key_avatar_backgroundSaved);
        } else if (avatarType == AVATAR_TYPE_SAVED) {
            color = Theme.getColor(Theme.key_avatar_backgroundSaved);
        } else if (avatarType == AVATAR_TYPE_SHARES) {
            color = getColorForId(5);
        } else {
            if (avatarType == AVATAR_TYPE_FILTER_CONTACTS) {
                color = getColorForId(5);
            } else if (avatarType == AVATAR_TYPE_FILTER_NON_CONTACTS) {
                color = getColorForId(4);
            } else if (avatarType == AVATAR_TYPE_FILTER_GROUPS) {
                color = getColorForId(3);
            } else if (avatarType == AVATAR_TYPE_FILTER_CHANNELS) {
                color = getColorForId(1);
            } else if (avatarType == AVATAR_TYPE_FILTER_BOTS) {
                color = getColorForId(0);
            } else if (avatarType == AVATAR_TYPE_FILTER_MUTED) {
                color = getColorForId(6);
            } else if (avatarType == AVATAR_TYPE_FILTER_READ) {
                color = getColorForId(5);
            } else {
                color = getColorForId(4);
            }
        }
        needApplyColorAccent = avatarType != AVATAR_TYPE_ARCHIVED && avatarType != AVATAR_TYPE_SAVED && avatarType != AVATAR_TYPE_REPLIES;
    }

    public void setArchivedAvatarHiddenProgress(float progress) {
        archivedAvatarProgress = progress;
    }

    public int getAvatarType() {
        return avatarType;
    }

    public void setInfo(TLRPC.Chat chat) {
        if (chat != null) {
            setInfo(chat.id, chat.title, null, null);
        }
    }

    public void setColor(int value) {
        color = value;
        needApplyColorAccent = false;
    }

    public void setTextSize(int size) {
        namePaint.setTextSize(size);
    }

    public void setInfo(int id, String firstName, String lastName) {
        setInfo(id, firstName, lastName, null);
    }

    public int getColor() {
        return needApplyColorAccent ? Theme.changeColorAccent(color) : color;
    }

    public void setInfo(int id, String firstName, String lastName, String custom) {
        if (isProfile) {
            color = getProfileColorForId(id);
        } else {
            color = getColorForId(id);
        }
        needApplyColorAccent = id == 5; // Tinting manually set blue color

        avatarType = AVATAR_TYPE_NORMAL;
        drawDeleted = false;

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

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds == null) {
            return;
        }
        int size = bounds.width();
        namePaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_avatar_text), alpha));
        Theme.avatar_backgroundPaint.setColor(ColorUtils.setAlphaComponent(getColor(), alpha));
        canvas.save();
        canvas.translate(bounds.left, bounds.top);
        canvas.drawCircle(size / 2.0f, size / 2.0f, size / 2.0f, Theme.avatar_backgroundPaint);

        if (avatarType == AVATAR_TYPE_ARCHIVED) {
            if (archivedAvatarProgress != 0) {
                Theme.avatar_backgroundPaint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_avatar_backgroundArchived), alpha));
                canvas.drawCircle(size / 2.0f, size / 2.0f, size / 2.0f * archivedAvatarProgress, Theme.avatar_backgroundPaint);
                if (Theme.dialogs_archiveAvatarDrawableRecolored) {
                    Theme.dialogs_archiveAvatarDrawable.beginApplyLayerColors();
                    Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow1.**", Theme.getNonAnimatedColor(Theme.key_avatar_backgroundArchived));
                    Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow2.**", Theme.getNonAnimatedColor(Theme.key_avatar_backgroundArchived));
                    Theme.dialogs_archiveAvatarDrawable.commitApplyLayerColors();
                    Theme.dialogs_archiveAvatarDrawableRecolored = false;
                }
            } else {
                if (!Theme.dialogs_archiveAvatarDrawableRecolored) {
                    Theme.dialogs_archiveAvatarDrawable.beginApplyLayerColors();
                    Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow1.**", color);
                    Theme.dialogs_archiveAvatarDrawable.setLayerColor("Arrow2.**", color);
                    Theme.dialogs_archiveAvatarDrawable.commitApplyLayerColors();
                    Theme.dialogs_archiveAvatarDrawableRecolored = true;
                }
            }
            int w = Theme.dialogs_archiveAvatarDrawable.getIntrinsicWidth();
            int h = Theme.dialogs_archiveAvatarDrawable.getIntrinsicHeight();
            int x = (size - w) / 2;
            int y = (size - h) / 2;
            canvas.save();
            Theme.dialogs_archiveAvatarDrawable.setBounds(x, y, x + w, y + h);
            Theme.dialogs_archiveAvatarDrawable.draw(canvas);
            canvas.restore();
        } else if (avatarType != 0) {
            Drawable drawable;

            if (avatarType == AVATAR_TYPE_REPLIES) {
                drawable = Theme.avatarDrawables[11];
            } else if (avatarType == AVATAR_TYPE_SAVED) {
                drawable = Theme.avatarDrawables[0];
            } else if (avatarType == AVATAR_TYPE_SHARES) {
                drawable = Theme.avatarDrawables[10];
            } else if (avatarType == AVATAR_TYPE_FILTER_CONTACTS) {
                drawable = Theme.avatarDrawables[2];
            } else if (avatarType == AVATAR_TYPE_FILTER_NON_CONTACTS) {
                drawable = Theme.avatarDrawables[3];
            } else if (avatarType == AVATAR_TYPE_FILTER_GROUPS) {
                drawable = Theme.avatarDrawables[4];
            } else if (avatarType == AVATAR_TYPE_FILTER_CHANNELS) {
                drawable = Theme.avatarDrawables[5];
            } else if (avatarType == AVATAR_TYPE_FILTER_BOTS) {
                drawable = Theme.avatarDrawables[6];
            } else if (avatarType == AVATAR_TYPE_FILTER_MUTED) {
                drawable = Theme.avatarDrawables[7];
            } else if (avatarType == AVATAR_TYPE_FILTER_READ) {
                drawable = Theme.avatarDrawables[8];
            } else {
                drawable = Theme.avatarDrawables[9];
            }
            if (drawable != null) {
                int w = drawable.getIntrinsicWidth();
                int h = drawable.getIntrinsicHeight();
                if (smallSize) {
                    w *= 0.8f;
                    h *= 0.8f;
                }
                int x = (size - w) / 2;
                int y = (size - h) / 2;
                drawable.setBounds(x, y, x + w, y + h);
                if (alpha != 255) {
                    drawable.setAlpha(alpha);
                    drawable.draw(canvas);
                    drawable.setAlpha(255);
                } else {
                    drawable.draw(canvas);
                }
            }
        } else if (drawDeleted && Theme.avatarDrawables[1] != null) {
            int w = Theme.avatarDrawables[1].getIntrinsicWidth();
            int h = Theme.avatarDrawables[1].getIntrinsicHeight();
            if (w > size - AndroidUtilities.dp(6) || h > size - AndroidUtilities.dp(6)) {
                float scale = size / (float) AndroidUtilities.dp(50);
                w *= scale;
                h *= scale;
            }
            int x = (size - w) / 2;
            int y = (size - h) / 2;
            Theme.avatarDrawables[1].setBounds(x, y, x + w, y + h);
            Theme.avatarDrawables[1].draw(canvas);
        } else {
            if (textLayout != null) {
                canvas.translate((size - textWidth) / 2 - textLeft, (size - textHeight) / 2);
                textLayout.draw(canvas);
            }
        }
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
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
