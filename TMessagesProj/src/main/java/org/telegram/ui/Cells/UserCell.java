/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;

import org.telegram.android.AndroidUtilities;
import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.android.ContactsController;
import org.telegram.android.LocaleController;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.android.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.android.ImageReceiver;
import org.telegram.ui.Views.AvatarDrawable;

public class UserCell extends BaseCell {
    private static TextPaint namePaint;
    private static TextPaint onlinePaint;
    private static TextPaint offlinePaint;
    private static Paint linePaint;

    private CharSequence currentName;
    private ImageReceiver avatarImage;
    private AvatarDrawable avatarDrawable;
    private CharSequence subLabel;

    private TLRPC.User user = null;

    private String lastName = null;
    private int lastStatus = 0;
    private TLRPC.FileLocation lastAvatar = null;

    public boolean useSeparator = false;
    public float drawAlpha = 1;

    private int nameLeft;
    private int nameTop;
    private StaticLayout nameLayout;

    private int onlineLeft;
    private int onlineTop = AndroidUtilities.dp(36);
    private StaticLayout onlineLayout;

    public UserCell(Context context) {
        super(context);
        init();
        avatarImage = new ImageReceiver(this);
        avatarImage.setRoundRadius(AndroidUtilities.dp(24));
        avatarDrawable = new AvatarDrawable();
    }

    private void init() {
        if (namePaint == null) {
            namePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            namePaint.setTextSize(AndroidUtilities.dp(18));
            namePaint.setColor(0xff222222);

            onlinePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            onlinePaint.setTextSize(AndroidUtilities.dp(15));
            onlinePaint.setColor(0xff316f9f);

            offlinePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            offlinePaint.setTextSize(AndroidUtilities.dp(15));
            offlinePaint.setColor(0xff999999);

            linePaint = new Paint();
            linePaint.setColor(0xffdcdcdc);
        }
    }

    public void setData(TLRPC.User u, CharSequence n, CharSequence s) {
        currentName = n;
        user = u;
        subLabel = s;
        update(0);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (avatarImage != null) {
            avatarImage.clearImage();
            lastAvatar = null;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec) - getPaddingRight(), AndroidUtilities.dp(64));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (user == null) {
            super.onLayout(changed, left, top, right, bottom);
            return;
        }
        if (changed) {
            buildLayout();
        }
    }

    public void buildLayout() {
        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();

        onlineLeft = nameLeft = AndroidUtilities.dp(LocaleController.isRTL ? 11 : 72) + paddingLeft;
        int avatarLeft = paddingLeft + (LocaleController.isRTL ? (getMeasuredWidth() - AndroidUtilities.dp(61)) : AndroidUtilities.dp(11));
        int nameWidth = getMeasuredWidth() - nameLeft - AndroidUtilities.dp(LocaleController.isRTL ? 72 : 14);
        int avatarTop = AndroidUtilities.dp(8);

        CharSequence nameString = "";
        if (currentName != null) {
            nameString = currentName;
        } else {
            String nameString2 = "";
            if (user != null) {
                nameString2 = ContactsController.formatName(user.first_name, user.last_name);
            }
            nameString = nameString2.replace("\n", " ");
        }
        if (nameString.length() == 0) {
            if (user != null && user.phone != null && user.phone.length() != 0) {
                nameString = PhoneFormat.getInstance().format("+" + user.phone);
            } else {
                nameString = LocaleController.getString("HiddenName", R.string.HiddenName);
            }
        }
        CharSequence nameStringFinal = TextUtils.ellipsize(nameString, namePaint, nameWidth - AndroidUtilities.dp(12), TextUtils.TruncateAt.END);
        nameLayout = new StaticLayout(nameStringFinal, namePaint, nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

        CharSequence onlineString = "";
        TextPaint currentOnlinePaint = offlinePaint;
        if (subLabel != null) {
            onlineString = subLabel;
        } else {
            onlineString = LocaleController.formatUserStatus(user);
            if (user != null && (user.id == UserConfig.getClientUserId() || user.status != null && user.status.expires > ConnectionsManager.getInstance().getCurrentTime())) {
                currentOnlinePaint = onlinePaint;
                onlineString = LocaleController.getString("Online", R.string.Online);
            }
        }

        CharSequence onlineStringFinal = TextUtils.ellipsize(onlineString, currentOnlinePaint, nameWidth - AndroidUtilities.dp(12), TextUtils.TruncateAt.END);
        onlineLayout = new StaticLayout(onlineStringFinal, currentOnlinePaint, nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        nameTop = AndroidUtilities.dp(12);

        avatarImage.setImageCoords(avatarLeft, avatarTop, AndroidUtilities.dp(48), AndroidUtilities.dp(48));

        double widthpx = 0;
        float left = 0;
        if (LocaleController.isRTL) {
            if (nameLayout.getLineCount() > 0) {
                left = nameLayout.getLineLeft(0);
                if (left == 0) {
                    widthpx = Math.ceil(nameLayout.getLineWidth(0));
                    if (widthpx < nameWidth) {
                        nameLeft += (nameWidth - widthpx);
                    }
                }
            }
            if (onlineLayout != null && onlineLayout.getLineCount() > 0) {
                left = onlineLayout.getLineLeft(0);
                if (left == 0) {
                    widthpx = Math.ceil(onlineLayout.getLineWidth(0));
                    if (widthpx < nameWidth) {
                        onlineLeft += (nameWidth - widthpx);
                    }
                }
            }
        } else {
            if (nameLayout.getLineCount() > 0) {
                left = nameLayout.getLineRight(0);
                if (left == nameWidth) {
                    widthpx = Math.ceil(nameLayout.getLineWidth(0));
                    if (widthpx < nameWidth) {
                        nameLeft -= (nameWidth - widthpx);
                    }
                }
            }
            if (onlineLayout != null && onlineLayout.getLineCount() > 0) {
                left = onlineLayout.getLineRight(0);
                if (left == nameWidth) {
                    widthpx = Math.ceil(onlineLayout.getLineWidth(0));
                    if (widthpx < nameWidth) {
                        onlineLeft -= (nameWidth - widthpx);
                    }
                }
            }
        }
    }

    public void update(int mask) {
        TLRPC.FileLocation photo = null;
        if (user != null) {
            if (user.photo != null) {
                photo = user.photo.photo_small;
            }
            avatarDrawable.setInfo(user);
        } else {
            avatarDrawable.setInfo(0, null, null, false);
        }

        if (mask != 0) {
            boolean continueUpdate = false;
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 && user != null) {
                if (lastAvatar != null && photo == null || lastAvatar == null && photo != null && lastAvatar != null && photo != null && (lastAvatar.volume_id != photo.volume_id || lastAvatar.local_id != photo.local_id)) {
                    continueUpdate = true;
                }
            }
            if (!continueUpdate && (mask & MessagesController.UPDATE_MASK_STATUS) != 0 && user != null) {
                int newStatus = 0;
                if (user.status != null) {
                    newStatus = user.status.expires;
                }
                if (newStatus != lastStatus) {
                    continueUpdate = true;
                }
            }
            if (!continueUpdate && (mask & MessagesController.UPDATE_MASK_NAME) != 0 && user != null) {
                String newName = null;
                if (user != null) {
                    newName = user.first_name + user.last_name;
                }
                if (newName == null || !newName.equals(lastName)) {
                    continueUpdate = true;
                }
            }

            if (!continueUpdate) {
                return;
            }
        }

        if (user != null) {
            if (user.status != null) {
                lastStatus = user.status.expires;
            } else {
                lastStatus = 0;
            }
            lastName = user.first_name + user.last_name;
        }

        lastAvatar = photo;
        avatarImage.setImage(photo, "50_50", avatarDrawable, false);

        if (getMeasuredWidth() != 0 || getMeasuredHeight() != 0) {
            buildLayout();
        } else {
            requestLayout();
        }
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (user == null) {
            return;
        }

        if (useSeparator) {
            if (LocaleController.isRTL) {
                canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth() - AndroidUtilities.dp(72), getMeasuredHeight() - 1, linePaint);
            } else {
                canvas.drawLine(AndroidUtilities.dp(72), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, linePaint);
            }
        }

        if (drawAlpha != 1) {
            canvas.saveLayerAlpha(0, 0, canvas.getWidth(), canvas.getHeight(), (int)(255 * drawAlpha), Canvas.HAS_ALPHA_LAYER_SAVE_FLAG);
        }

        canvas.save();
        canvas.translate(nameLeft, nameTop);
        nameLayout.draw(canvas);
        canvas.restore();

        if (onlineLayout != null) {
            canvas.save();
            canvas.translate(onlineLeft, onlineTop);
            onlineLayout.draw(canvas);
            canvas.restore();
        }

        avatarImage.draw(canvas);
    }
}
