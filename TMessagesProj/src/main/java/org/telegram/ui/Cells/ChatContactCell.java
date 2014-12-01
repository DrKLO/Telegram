/*
 * This is the source code of Telegram for Android v. 1.7.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.android.AndroidUtilities;
import org.telegram.android.ContactsController;
import org.telegram.android.ImageReceiver;
import org.telegram.android.LocaleController;
import org.telegram.android.MessageObject;
import org.telegram.android.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.AvatarDrawable;

public class ChatContactCell extends ChatBaseCell {

    public static interface ChatContactCellDelegate {
        public abstract void didClickAddButton(ChatContactCell cell, TLRPC.User user);
        public abstract void didClickPhone(ChatContactCell cell);
    }

    private static TextPaint namePaint;
    private static TextPaint phonePaint;
    private static Drawable addContactDrawableIn;
    private static Drawable addContactDrawableOut;

    private ImageReceiver avatarImage;
    private AvatarDrawable avatarDrawable;

    private StaticLayout nameLayout;
    private StaticLayout phoneLayout;

    private TLRPC.User contactUser;
    private TLRPC.FileLocation currentPhoto;

    private boolean avatarPressed = false;
    private boolean buttonPressed = false;
    private boolean drawAddButton = false;
    private int namesWidth = 0;

    private ChatContactCellDelegate contactDelegate = null;

    public ChatContactCell(Context context) {
        super(context);
        if (namePaint == null) {
            namePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            namePaint.setTextSize(AndroidUtilities.dp(15));

            phonePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            phonePaint.setTextSize(AndroidUtilities.dp(15));
            phonePaint.setColor(0xff212121);

            addContactDrawableIn = getResources().getDrawable(R.drawable.addcontact_blue);
            addContactDrawableOut = getResources().getDrawable(R.drawable.addcontact_green);
        }
        avatarImage = new ImageReceiver(this);
        avatarImage.setRoundRadius(AndroidUtilities.dp(21));
        avatarDrawable = new AvatarDrawable();
    }

    public void setContactDelegate(ChatContactCellDelegate delegate) {
        this.contactDelegate = delegate;
    }

    @Override
    protected boolean isUserDataChanged() {
        if (currentMessageObject == null) {
            return false;
        }

        int uid = currentMessageObject.messageOwner.media.user_id;
        boolean newDrawAdd = contactUser != null && uid != UserConfig.getClientUserId() && ContactsController.getInstance().contactsDict.get(uid) == null;
        if (newDrawAdd != drawAddButton) {
            return true;
        }

        contactUser = MessagesController.getInstance().getUser(currentMessageObject.messageOwner.media.user_id);

        TLRPC.FileLocation newPhoto = null;
        if (contactUser != null && contactUser.photo != null) {
            newPhoto = contactUser.photo.photo_small;
        }

        return currentPhoto == null && newPhoto != null || currentPhoto != null && newPhoto == null || currentPhoto != null && newPhoto != null && (currentPhoto.local_id != newPhoto.local_id || currentPhoto.volume_id != newPhoto.volume_id) || super.isUserDataChanged();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        boolean result = false;
        int side = AndroidUtilities.dp(36);
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (x >= avatarImage.getImageX() && x <= avatarImage.getImageX() + namesWidth + AndroidUtilities.dp(42) && y >= avatarImage.getImageY() && y <= avatarImage.getImageY() + avatarImage.getImageHeight()) {
                avatarPressed = true;
                result = true;
            } else if (x >= avatarImage.getImageX() + namesWidth + AndroidUtilities.dp(52) && y >= AndroidUtilities.dp(13) && x <= avatarImage.getImageX() + namesWidth + AndroidUtilities.dp(92) && y <= AndroidUtilities.dp(52)) {
                buttonPressed = true;
                result = true;
            }
            if (result) {
                startCheckLongPress();
            }
        } else {
            if (event.getAction() != MotionEvent.ACTION_MOVE) {
                cancelCheckLongPress();
            }
            if (avatarPressed) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    avatarPressed = false;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    if (contactUser != null) {
                        if (delegate != null) {
                            delegate.didPressedUserAvatar(this, contactUser);
                        }
                    } else {
                        if (contactDelegate != null) {
                            contactDelegate.didClickPhone(this);
                        }
                    }
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    avatarPressed = false;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!(x >= avatarImage.getImageX() && x <= avatarImage.getImageX() + namesWidth + AndroidUtilities.dp(42) && y >= avatarImage.getImageY() && y <= avatarImage.getImageY() + avatarImage.getImageHeight())) {
                        avatarPressed = false;
                    }
                }
            } else if (buttonPressed) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    buttonPressed = false;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    if (contactUser != null && contactDelegate != null) {
                        contactDelegate.didClickAddButton(this, contactUser);
                    }
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    buttonPressed = false;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!(x >= avatarImage.getImageX() + namesWidth + AndroidUtilities.dp(52) && y >= AndroidUtilities.dp(13) && x <= avatarImage.getImageX() + namesWidth + AndroidUtilities.dp(92) && y <= AndroidUtilities.dp(52))) {
                        buttonPressed = false;
                    }
                }
            }
        }
        if (!result) {
            result = super.onTouchEvent(event);
        }

        return result;
    }

    @Override
    public void setMessageObject(MessageObject messageObject) {
        if (currentMessageObject != messageObject || isUserDataChanged()) {

            int uid = messageObject.messageOwner.media.user_id;
            contactUser = MessagesController.getInstance().getUser(uid);

            drawAddButton = contactUser != null && uid != UserConfig.getClientUserId() && ContactsController.getInstance().contactsDict.get(uid) == null;

            int maxWidth;
            if (AndroidUtilities.isTablet()) {
                maxWidth = (int) (AndroidUtilities.getMinTabletSide() * 0.7f);
            } else {
                maxWidth = (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.7f);
            }
            maxWidth -= AndroidUtilities.dp(58 + (drawAddButton ? 42 : 0));

            if (contactUser != null) {
                if (contactUser.photo != null) {
                    currentPhoto = contactUser.photo.photo_small;
                } else {
                    currentPhoto = null;
                }
                avatarDrawable.setInfo(contactUser);
            } else {
                currentPhoto = null;
                avatarDrawable.setInfo(uid, null, null, false);
            }
            avatarImage.setImage(currentPhoto, "50_50", avatarDrawable, false);

            String currentNameString = ContactsController.formatName(messageObject.messageOwner.media.first_name, messageObject.messageOwner.media.last_name);
            int nameWidth = Math.min((int) Math.ceil(namePaint.measureText(currentNameString)), maxWidth);

            CharSequence stringFinal = TextUtils.ellipsize(currentNameString.replace("\n", " "), namePaint, nameWidth, TextUtils.TruncateAt.END);
            nameLayout = new StaticLayout(stringFinal, namePaint, nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            if (nameLayout.getLineCount() > 0) {
                nameWidth = (int)Math.ceil(nameLayout.getLineWidth(0));
            } else {
                nameWidth = 0;
            }

            String phone = messageObject.messageOwner.media.phone_number;
            if (phone != null && phone.length() != 0) {
                if (!phone.startsWith("+")) {
                    phone = "+" + phone;
                }
                phone = PhoneFormat.getInstance().format(phone);
            } else {
                phone = LocaleController.getString("NumberUnknown", R.string.NumberUnknown);
            }
            int phoneWidth = Math.min((int) Math.ceil(phonePaint.measureText(phone)), maxWidth);
            stringFinal = TextUtils.ellipsize(phone.replace("\n", " "), phonePaint, phoneWidth, TextUtils.TruncateAt.END);
            phoneLayout = new StaticLayout(stringFinal, phonePaint, phoneWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            if (phoneLayout.getLineCount() > 0) {
                phoneWidth = (int)Math.ceil(phoneLayout.getLineWidth(0));
            } else {
                phoneWidth = 0;
            }

            namesWidth = Math.max(nameWidth, phoneWidth);
            backgroundWidth = AndroidUtilities.dp(77 + (drawAddButton ? 42 : 0)) + namesWidth;

            super.setMessageObject(messageObject);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(71));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (currentMessageObject == null) {
            return;
        }

        int x;

        if (currentMessageObject.isOut()) {
            x = layoutWidth - backgroundWidth + AndroidUtilities.dp(8);
        } else {
            if (isChat) {
                x = AndroidUtilities.dp(69);
            } else {
                x = AndroidUtilities.dp(16);
            }
        }
        avatarImage.setImageCoords(x, AndroidUtilities.dp(9), AndroidUtilities.dp(42), AndroidUtilities.dp(42));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (currentMessageObject == null) {
            return;
        }

        avatarImage.draw(canvas);

        if (nameLayout != null) {
            canvas.save();
            canvas.translate(avatarImage.getImageX() + avatarImage.getImageWidth() + AndroidUtilities.dp(9), AndroidUtilities.dp(10));
            namePaint.setColor(AvatarDrawable.getColorForId(currentMessageObject.messageOwner.media.user_id));
            nameLayout.draw(canvas);
            canvas.restore();
        }
        if (phoneLayout != null) {
            canvas.save();
            canvas.translate(avatarImage.getImageX() + avatarImage.getImageWidth() + AndroidUtilities.dp(9), AndroidUtilities.dp(31));
            phoneLayout.draw(canvas);
            canvas.restore();
        }

        if (drawAddButton) {
            Drawable addContactDrawable;
            if (currentMessageObject.isOut()) {
                addContactDrawable = addContactDrawableOut;
            } else {
                addContactDrawable = addContactDrawableIn;
            }
            setDrawableBounds(addContactDrawable, avatarImage.getImageX() + namesWidth + AndroidUtilities.dp(78), AndroidUtilities.dp(13));
            addContactDrawable.draw(canvas);
        }
    }
}
