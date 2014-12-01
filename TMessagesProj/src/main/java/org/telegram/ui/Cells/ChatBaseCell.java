/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.ContactsController;
import org.telegram.android.LocaleController;
import org.telegram.messenger.TLRPC;
import org.telegram.android.MessagesController;
import org.telegram.messenger.R;
import org.telegram.android.MessageObject;
import org.telegram.android.ImageReceiver;
import org.telegram.ui.Components.AvatarDrawable;

public class ChatBaseCell extends BaseCell {

    public static interface ChatBaseCellDelegate {
        public abstract void didPressedUserAvatar(ChatBaseCell cell, TLRPC.User user);
        public abstract void didPressedCancelSendButton(ChatBaseCell cell);
        public abstract void didLongPressed(ChatBaseCell cell);
        public abstract boolean canPerformActions();
    }

    public boolean isChat = false;
    protected boolean isPressed = false;
    protected boolean forwardName = false;
    protected boolean media = false;
    private boolean isCheckPressed = true;
    private boolean wasLayout = false;
    protected boolean isAvatarVisible = false;
    protected MessageObject currentMessageObject;

    private static Drawable backgroundDrawableIn;
    private static Drawable backgroundDrawableInSelected;
    private static Drawable backgroundDrawableOut;
    private static Drawable backgroundDrawableOutSelected;
    private static Drawable backgroundMediaDrawableIn;
    private static Drawable backgroundMediaDrawableInSelected;
    private static Drawable backgroundMediaDrawableOut;
    private static Drawable backgroundMediaDrawableOutSelected;
    private static Drawable checkDrawable;
    private static Drawable halfCheckDrawable;
    private static Drawable clockDrawable;
    private static Drawable broadcastDrawable;
    private static Drawable checkMediaDrawable;
    private static Drawable halfCheckMediaDrawable;
    private static Drawable clockMediaDrawable;
    private static Drawable broadcastMediaDrawable;
    private static Drawable errorDrawable;
    protected static Drawable mediaBackgroundDrawable;
    private static TextPaint timePaintIn;
    private static TextPaint timePaintOut;
    private static TextPaint timeMediaPaint;
    private static TextPaint namePaint;
    private static TextPaint forwardNamePaint;

    protected int backgroundWidth = 100;

    protected int layoutWidth;
    protected int layoutHeight;

    private ImageReceiver avatarImage;
    private AvatarDrawable avatarDrawable;
    private boolean avatarPressed = false;
    private boolean forwardNamePressed = false;

    private StaticLayout nameLayout;
    protected int nameWidth;
    private float nameOffsetX = 0;
    protected boolean drawName = false;

    private StaticLayout forwardedNameLayout;
    protected int forwardedNameWidth;
    protected boolean drawForwardedName = false;
    private int forwardNameX;
    private int forwardNameY;
    private float forwardNameOffsetX = 0;

    private StaticLayout timeLayout;
    protected int timeWidth;
    private int timeX;
    private TextPaint currentTimePaint;
    private String currentTimeString;
    protected boolean drawTime = true;

    private TLRPC.User currentUser;
    private TLRPC.FileLocation currentPhoto;
    private String currentNameString;

    private TLRPC.User currentForwardUser;
    private String currentForwardNameString;

    protected ChatBaseCellDelegate delegate;

    protected int namesOffset = 0;

    private int last_send_state = 0;
    private int last_delete_date = 0;

    public ChatBaseCell(Context context) {
        super(context);
        if (backgroundDrawableIn == null) {
            backgroundDrawableIn = getResources().getDrawable(R.drawable.msg_in);
            backgroundDrawableInSelected = getResources().getDrawable(R.drawable.msg_in_selected);
            backgroundDrawableOut = getResources().getDrawable(R.drawable.msg_out);
            backgroundDrawableOutSelected = getResources().getDrawable(R.drawable.msg_out_selected);
            backgroundMediaDrawableIn = getResources().getDrawable(R.drawable.msg_in_photo);
            backgroundMediaDrawableInSelected = getResources().getDrawable(R.drawable.msg_in_photo_selected);
            backgroundMediaDrawableOut = getResources().getDrawable(R.drawable.msg_out_photo);
            backgroundMediaDrawableOutSelected = getResources().getDrawable(R.drawable.msg_out_photo_selected);
            checkDrawable = getResources().getDrawable(R.drawable.msg_check);
            halfCheckDrawable = getResources().getDrawable(R.drawable.msg_halfcheck);
            clockDrawable = getResources().getDrawable(R.drawable.msg_clock);
            checkMediaDrawable = getResources().getDrawable(R.drawable.msg_check_w);
            halfCheckMediaDrawable = getResources().getDrawable(R.drawable.msg_halfcheck_w);
            clockMediaDrawable = getResources().getDrawable(R.drawable.msg_clock_photo);
            errorDrawable = getResources().getDrawable(R.drawable.msg_warning);
            mediaBackgroundDrawable = getResources().getDrawable(R.drawable.phototime);
            broadcastDrawable = getResources().getDrawable(R.drawable.broadcast3);
            broadcastMediaDrawable = getResources().getDrawable(R.drawable.broadcast4);

            timePaintIn = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            timePaintIn.setTextSize(AndroidUtilities.dp(12));
            timePaintIn.setColor(0xffa1aab3);

            timePaintOut = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            timePaintOut.setTextSize(AndroidUtilities.dp(12));
            timePaintOut.setColor(0xff70b15c);

            timeMediaPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            timeMediaPaint.setTextSize(AndroidUtilities.dp(12));
            timeMediaPaint.setColor(0xffffffff);

            namePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            namePaint.setTextSize(AndroidUtilities.dp(15));

            forwardNamePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            forwardNamePaint.setTextSize(AndroidUtilities.dp(14));
        }
        avatarImage = new ImageReceiver(this);
        avatarImage.setRoundRadius(AndroidUtilities.dp(21));
        avatarDrawable = new AvatarDrawable();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        avatarImage.clearImage();
        currentPhoto = null;
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        invalidate();
    }

    public void setDelegate(ChatBaseCellDelegate delegate) {
        this.delegate = delegate;
    }

    public void setCheckPressed(boolean value, boolean pressed) {
        isCheckPressed = value;
        isPressed = pressed;
        invalidate();
    }

    protected boolean isUserDataChanged() {
        if (currentMessageObject == null || currentUser == null) {
            return false;
        }
        if (last_send_state != currentMessageObject.messageOwner.send_state) {
            return true;
        }
        if (last_delete_date != currentMessageObject.messageOwner.destroyTime) {
            return true;
        }

        TLRPC.User newUser = MessagesController.getInstance().getUser(currentMessageObject.messageOwner.from_id);
        TLRPC.FileLocation newPhoto = null;

        if (isAvatarVisible && newUser != null && newUser.photo != null) {
            newPhoto = newUser.photo.photo_small;
        }

        if (currentPhoto == null && newPhoto != null || currentPhoto != null && newPhoto == null || currentPhoto != null && newPhoto != null && (currentPhoto.local_id != newPhoto.local_id || currentPhoto.volume_id != newPhoto.volume_id)) {
            return true;
        }

        String newNameString = null;
        if (drawName && isChat && newUser != null && !currentMessageObject.isOut()) {
            newNameString = ContactsController.formatName(newUser.first_name, newUser.last_name);
        }

        if (currentNameString == null && newNameString != null || currentNameString != null && newNameString == null || currentNameString != null && newNameString != null && !currentNameString.equals(newNameString)) {
            return true;
        }

        newUser = MessagesController.getInstance().getUser(currentMessageObject.messageOwner.fwd_from_id);
        newNameString = null;
        if (newUser != null && drawForwardedName && currentMessageObject.messageOwner instanceof TLRPC.TL_messageForwarded) {
            newNameString = ContactsController.formatName(newUser.first_name, newUser.last_name);
        }
        return currentForwardNameString == null && newNameString != null || currentForwardNameString != null && newNameString == null || currentForwardNameString != null && newNameString != null && !currentForwardNameString.equals(newNameString);
    }

    public void setMessageObject(MessageObject messageObject) {
        currentMessageObject = messageObject;
        last_send_state = messageObject.messageOwner.send_state;
        last_delete_date = messageObject.messageOwner.destroyTime;
        isPressed = false;
        isCheckPressed = true;
        isAvatarVisible = false;
        wasLayout = false;

        currentUser = MessagesController.getInstance().getUser(messageObject.messageOwner.from_id);
        if (isChat && !messageObject.isOut()) {
            isAvatarVisible = true;
            if (currentUser != null) {
                if (currentUser.photo != null) {
                    currentPhoto = currentUser.photo.photo_small;
                } else {
                    currentPhoto = null;
                }
                avatarDrawable.setInfo(currentUser);
            } else {
                currentPhoto = null;
                avatarDrawable.setInfo(messageObject.messageOwner.from_id, null, null, false);
            }
            avatarImage.setImage(currentPhoto, "50_50", avatarDrawable, false);
        }

        if (!media) {
            if (currentMessageObject.isOut()) {
                currentTimePaint = timePaintOut;
            } else {
                currentTimePaint = timePaintIn;
            }
        } else {
            currentTimePaint = timeMediaPaint;
        }

        currentTimeString = LocaleController.formatterDay.format((long) (currentMessageObject.messageOwner.date) * 1000);
        timeWidth = (int)Math.ceil(currentTimePaint.measureText(currentTimeString));

        namesOffset = 0;

        if (drawName && isChat && currentUser != null && !currentMessageObject.isOut()) {
            currentNameString = ContactsController.formatName(currentUser.first_name, currentUser.last_name);
            nameWidth = getMaxNameWidth();

            CharSequence nameStringFinal = TextUtils.ellipsize(currentNameString.replace("\n", " "), namePaint, nameWidth - AndroidUtilities.dp(12), TextUtils.TruncateAt.END);
            nameLayout = new StaticLayout(nameStringFinal, namePaint, nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            if (nameLayout.getLineCount() > 0) {
                nameWidth = (int)Math.ceil(nameLayout.getLineWidth(0));
                namesOffset += AndroidUtilities.dp(18);
                nameOffsetX = nameLayout.getLineLeft(0);
            } else {
                nameWidth = 0;
            }
        } else {
            currentNameString = null;
            nameLayout = null;
            nameWidth = 0;
        }

        if (drawForwardedName && messageObject.messageOwner instanceof TLRPC.TL_messageForwarded) {
            currentForwardUser = MessagesController.getInstance().getUser(messageObject.messageOwner.fwd_from_id);
            if (currentForwardUser != null) {
                currentForwardNameString = ContactsController.formatName(currentForwardUser.first_name, currentForwardUser.last_name);

                forwardedNameWidth = getMaxNameWidth();

                CharSequence str = TextUtils.ellipsize(currentForwardNameString.replace("\n", " "), forwardNamePaint, forwardedNameWidth - AndroidUtilities.dp(40), TextUtils.TruncateAt.END);
                str = Html.fromHtml(String.format("%s<br>%s <b>%s</b>", LocaleController.getString("ForwardedMessage", R.string.ForwardedMessage), LocaleController.getString("From", R.string.From), str));
                forwardedNameLayout = new StaticLayout(str, forwardNamePaint, forwardedNameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                if (forwardedNameLayout.getLineCount() > 1) {
                    forwardedNameWidth = Math.max((int) Math.ceil(forwardedNameLayout.getLineWidth(0)), (int) Math.ceil(forwardedNameLayout.getLineWidth(1)));
                    namesOffset += AndroidUtilities.dp(36);
                    forwardNameOffsetX = Math.min(forwardedNameLayout.getLineLeft(0), forwardedNameLayout.getLineLeft(1));
                } else {
                    forwardedNameWidth = 0;
                }
            } else {
                currentForwardNameString = null;
                forwardedNameLayout = null;
                forwardedNameWidth = 0;
            }
        } else {
            currentForwardNameString = null;
            forwardedNameLayout = null;
            forwardedNameWidth = 0;
        }

        requestLayout();
    }

    public final MessageObject getMessageObject() {
        return currentMessageObject;
    }

    protected int getMaxNameWidth() {
        return backgroundWidth - AndroidUtilities.dp(8);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean result = false;
        float x = event.getX();
        float y = event.getY();
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (delegate == null || delegate.canPerformActions()) {
                if (isAvatarVisible && avatarImage.isInsideImage(x, y)) {
                    avatarPressed = true;
                    result = true;
                } else if (drawForwardedName && forwardedNameLayout != null) {
                    if (x >= forwardNameX && x <= forwardNameX + forwardedNameWidth && y >= forwardNameY && y <= forwardNameY + AndroidUtilities.dp(32)) {
                        forwardNamePressed = true;
                        result = true;
                    }
                }
                if (result) {
                    startCheckLongPress();
                }
            }
        } else {
            if (event.getAction() != MotionEvent.ACTION_MOVE) {
                cancelCheckLongPress();
            }
            if (avatarPressed) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    avatarPressed = false;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    if (delegate != null) {
                        delegate.didPressedUserAvatar(this, currentUser);
                    }
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    avatarPressed = false;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (isAvatarVisible && !avatarImage.isInsideImage(x, y)) {
                        avatarPressed = false;
                    }
                }
            } else if (forwardNamePressed) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    forwardNamePressed = false;
                    playSoundEffect(SoundEffectConstants.CLICK);
                    if (delegate != null) {
                        delegate.didPressedUserAvatar(this, currentForwardUser);
                    }
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    forwardNamePressed = false;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!(x >= forwardNameX && x <= forwardNameX + forwardedNameWidth && y >= forwardNameY && y <= forwardNameY + AndroidUtilities.dp(32))) {
                        forwardNamePressed = false;
                    }
                }
            }
        }
        return result;
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (currentMessageObject == null) {
            super.onLayout(changed, left, top, right, bottom);
            return;
        }

        if (changed || !wasLayout) {
            layoutWidth = getMeasuredWidth();
            layoutHeight = getMeasuredHeight();

            timeLayout = new StaticLayout(currentTimeString, currentTimePaint, timeWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            if (!media) {
                if (!currentMessageObject.isOut()) {
                    timeX = backgroundWidth - AndroidUtilities.dp(9) - timeWidth + (isChat ? AndroidUtilities.dp(52) : 0);
                } else {
                    timeX = layoutWidth - timeWidth - AndroidUtilities.dp(38.5f);
                }
            } else {
                if (!currentMessageObject.isOut()) {
                    timeX = backgroundWidth - AndroidUtilities.dp(4) - timeWidth + (isChat ? AndroidUtilities.dp(52) : 0);
                } else {
                    timeX = layoutWidth - timeWidth - AndroidUtilities.dp(42.0f);
                }
            }

            if (isAvatarVisible) {
                avatarImage.setImageCoords(AndroidUtilities.dp(6), layoutHeight - AndroidUtilities.dp(45), AndroidUtilities.dp(42), AndroidUtilities.dp(42));
            }

            wasLayout = true;
        }
    }

    protected void onAfterBackgroundDraw(Canvas canvas) {

    }

    @Override
    protected void onLongPress() {
        if (delegate != null) {
            delegate.didLongPressed(this);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (currentMessageObject == null) {
            return;
        }

        if (!wasLayout) {
            requestLayout();
            return;
        }

        if (isAvatarVisible) {
            avatarImage.draw(canvas);
        }

        Drawable currentBackgroundDrawable = null;
        if (currentMessageObject.isOut()) {
            if (isPressed() && isCheckPressed || !isCheckPressed && isPressed) {
                if (!media) {
                    currentBackgroundDrawable = backgroundDrawableOutSelected;
                } else {
                    currentBackgroundDrawable = backgroundMediaDrawableOutSelected;
                }
            } else {
                if (!media) {
                    currentBackgroundDrawable = backgroundDrawableOut;
                } else {
                    currentBackgroundDrawable = backgroundMediaDrawableOut;
                }
            }
            setDrawableBounds(currentBackgroundDrawable, layoutWidth - backgroundWidth - (!media ? 0 : AndroidUtilities.dp(9)), AndroidUtilities.dp(1), backgroundWidth, layoutHeight - AndroidUtilities.dp(2));
        } else {
            if (isPressed() && isCheckPressed || !isCheckPressed && isPressed) {
                if (!media) {
                    currentBackgroundDrawable = backgroundDrawableInSelected;
                } else {
                    currentBackgroundDrawable = backgroundMediaDrawableInSelected;
                }
            } else {
                if (!media) {
                    currentBackgroundDrawable = backgroundDrawableIn;
                } else {
                    currentBackgroundDrawable = backgroundMediaDrawableIn;
                }
            }
            if (isChat) {
                setDrawableBounds(currentBackgroundDrawable, AndroidUtilities.dp(52 + (!media ? 0 : 9)), AndroidUtilities.dp(1), backgroundWidth, layoutHeight - AndroidUtilities.dp(2));
            } else {
                setDrawableBounds(currentBackgroundDrawable, (!media ? 0 : AndroidUtilities.dp(9)), AndroidUtilities.dp(1), backgroundWidth, layoutHeight - AndroidUtilities.dp(2));
            }
        }
        currentBackgroundDrawable.draw(canvas);

        onAfterBackgroundDraw(canvas);

        if (drawName && nameLayout != null) {
            canvas.save();
            canvas.translate(currentBackgroundDrawable.getBounds().left + AndroidUtilities.dp(19) - nameOffsetX, AndroidUtilities.dp(10));
            namePaint.setColor(AvatarDrawable.getNameColorForId(currentUser.id));
            nameLayout.draw(canvas);
            canvas.restore();
        }

        if (drawForwardedName && forwardedNameLayout != null) {
            canvas.save();
            if (currentMessageObject.isOut()) {
                forwardNamePaint.setColor(0xff4a923c);
                forwardNameX = currentBackgroundDrawable.getBounds().left + AndroidUtilities.dp(10);
                forwardNameY = AndroidUtilities.dp(10 + (drawName ? 18 : 0));
            } else {
                forwardNamePaint.setColor(0xff006fc8);
                forwardNameX = currentBackgroundDrawable.getBounds().left + AndroidUtilities.dp(19);
                forwardNameY = AndroidUtilities.dp(10 + (drawName ? 18 : 0));
            }
            canvas.translate(forwardNameX - forwardNameOffsetX, forwardNameY);
            forwardedNameLayout.draw(canvas);
            canvas.restore();
        }

        if (drawTime) {
            if (media) {
                setDrawableBounds(mediaBackgroundDrawable, timeX - AndroidUtilities.dp(3), layoutHeight - AndroidUtilities.dp(27.5f), timeWidth + AndroidUtilities.dp(6 + (currentMessageObject.isOut() ? 20 : 0)), AndroidUtilities.dp(16.5f));
                mediaBackgroundDrawable.draw(canvas);

                canvas.save();
                canvas.translate(timeX, layoutHeight - AndroidUtilities.dp(12.0f) - timeLayout.getHeight());
                timeLayout.draw(canvas);
                canvas.restore();
            } else {
                canvas.save();
                canvas.translate(timeX, layoutHeight - AndroidUtilities.dp(6.5f) - timeLayout.getHeight());
                timeLayout.draw(canvas);
                canvas.restore();
            }

            if (currentMessageObject.isOut()) {
                boolean drawCheck1 = false;
                boolean drawCheck2 = false;
                boolean drawClock = false;
                boolean drawError = false;
                boolean isBroadcast = (int)(currentMessageObject.getDialogId() >> 32) == 1;

                if (currentMessageObject.isSending()) {
                    drawCheck1 = false;
                    drawCheck2 = false;
                    drawClock = true;
                    drawError = false;
                } else if (currentMessageObject.isSendError()) {
                    drawCheck1 = false;
                    drawCheck2 = false;
                    drawClock = false;
                    drawError = true;
                } else if (currentMessageObject.isSent()) {
                    if (!currentMessageObject.isUnread()) {
                        drawCheck1 = true;
                        drawCheck2 = true;
                    } else {
                        drawCheck1 = false;
                        drawCheck2 = true;
                    }
                    drawClock = false;
                    drawError = false;
                }

                if (drawClock) {
                    if (!media) {
                        setDrawableBounds(clockDrawable, layoutWidth - AndroidUtilities.dp(18.5f) - clockDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(8.5f) - clockDrawable.getIntrinsicHeight());
                        clockDrawable.draw(canvas);
                    } else {
                        setDrawableBounds(clockMediaDrawable, layoutWidth - AndroidUtilities.dp(22.0f) - clockMediaDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(13.0f) - clockMediaDrawable.getIntrinsicHeight());
                        clockMediaDrawable.draw(canvas);
                    }
                }
                if (isBroadcast) {
                    if (drawCheck1 || drawCheck2) {
                        if (!media) {
                            setDrawableBounds(broadcastDrawable, layoutWidth - AndroidUtilities.dp(20.5f) - broadcastDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(8.0f) - broadcastDrawable.getIntrinsicHeight());
                            broadcastDrawable.draw(canvas);
                        } else {
                            setDrawableBounds(broadcastMediaDrawable, layoutWidth - AndroidUtilities.dp(24.0f) - broadcastMediaDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(13.0f) - broadcastMediaDrawable.getIntrinsicHeight());
                            broadcastMediaDrawable.draw(canvas);
                        }
                    }
                } else {
                    if (drawCheck2) {
                        if (!media) {
                            if (drawCheck1) {
                                setDrawableBounds(checkDrawable, layoutWidth - AndroidUtilities.dp(22.5f) - checkDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(8.5f) - checkDrawable.getIntrinsicHeight());
                            } else {
                                setDrawableBounds(checkDrawable, layoutWidth - AndroidUtilities.dp(18.5f) - checkDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(8.5f) - checkDrawable.getIntrinsicHeight());
                            }
                            checkDrawable.draw(canvas);
                        } else {
                            if (drawCheck1) {
                                setDrawableBounds(checkMediaDrawable, layoutWidth - AndroidUtilities.dp(26.0f) - checkMediaDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(13.0f) - checkMediaDrawable.getIntrinsicHeight());
                            } else {
                                setDrawableBounds(checkMediaDrawable, layoutWidth - AndroidUtilities.dp(22.0f) - checkMediaDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(13.0f) - checkMediaDrawable.getIntrinsicHeight());
                            }
                            checkMediaDrawable.draw(canvas);
                        }
                    }
                    if (drawCheck1) {
                        if (!media) {
                            setDrawableBounds(halfCheckDrawable, layoutWidth - AndroidUtilities.dp(18) - halfCheckDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(8.5f) - halfCheckDrawable.getIntrinsicHeight());
                            halfCheckDrawable.draw(canvas);
                        } else {
                            setDrawableBounds(halfCheckMediaDrawable, layoutWidth - AndroidUtilities.dp(20.5f) - halfCheckMediaDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(13.0f) - halfCheckMediaDrawable.getIntrinsicHeight());
                            halfCheckMediaDrawable.draw(canvas);
                        }
                    }
                }
                if (drawError) {
                    if (!media) {
                        setDrawableBounds(errorDrawable, layoutWidth - AndroidUtilities.dp(18) - errorDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(6.5f) - errorDrawable.getIntrinsicHeight());
                        errorDrawable.draw(canvas);
                    } else {
                        setDrawableBounds(errorDrawable, layoutWidth - AndroidUtilities.dp(20.5f) - errorDrawable.getIntrinsicWidth(), layoutHeight - AndroidUtilities.dp(12.5f) - errorDrawable.getIntrinsicHeight());
                        errorDrawable.draw(canvas);
                    }
                }
            }
        }
    }
}
