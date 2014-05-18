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
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.objects.MessageObject;
import org.telegram.ui.Views.ImageReceiver;
import org.telegram.ui.Views.OnSwipeTouchListener;

import java.lang.ref.WeakReference;

public class ChatBaseCell extends BaseCell {

    public static interface ChatBaseCellDelegate {
        public abstract void didPressedUserAvatar(ChatBaseCell cell, TLRPC.User user);
        public abstract void didPressedCancelSendButton(ChatBaseCell cell);
        public abstract void didLongPressed(ChatBaseCell cell);
        public abstract boolean canPerformActions();
        public boolean onSwipeLeft();
        public boolean onSwipeRight();
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
    private static Drawable checkMediaDrawable;
    private static Drawable halfCheckMediaDrawable;
    private static Drawable clockMediaDrawable;
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
    protected int timeX;
    private TextPaint currentTimePaint;
    private String currentTimeString;

    private TLRPC.User currentUser;
    private TLRPC.FileLocation currentPhoto;
    private String currentNameString;

    private TLRPC.User currentForwardUser;
    private String currentForwardNameString;

    public ChatBaseCellDelegate delegate;

    protected int namesOffset = 0;

    private boolean checkingForLongPress = false;
    private int pressCount = 0;
    private CheckForLongPress pendingCheckForLongPress = null;
    private CheckForTap pendingCheckForTap = null;
    private OnSwipeTouchListener onSwipeTouchListener;

    private final class CheckForTap implements Runnable {
        public void run() {
            if (pendingCheckForLongPress == null) {
                pendingCheckForLongPress = new CheckForLongPress();
            }
            pendingCheckForLongPress.currentPressCount = ++pressCount;
            postDelayed(pendingCheckForLongPress, ViewConfiguration.getLongPressTimeout() - ViewConfiguration.getTapTimeout());
        }
    }

    class CheckForLongPress implements Runnable {
        public int currentPressCount;

        public void run() {
            if (checkingForLongPress && getParent() != null && currentPressCount == pressCount) {
                if (delegate != null) {
                    checkingForLongPress = false;
                    MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                    onTouchEvent(event);
                    event.recycle();
                    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    delegate.didLongPressed(ChatBaseCell.this);
                }
            }
        }
    }

    public ChatBaseCell(Context context, boolean isMedia) {
        super(context);
        init();
        media = isMedia;
        avatarImage = new ImageReceiver();
        avatarImage.parentView = new WeakReference<View>(this);
        onSwipeTouchListener = new OnSwipeTouchListener() {
            public void onSwipeRight() {
                if (delegate != null && delegate.onSwipeRight()) {
                    MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                    onTouchEvent(event);
                    event.recycle();
                }
            }

            public void onSwipeLeft() {
                if (delegate != null && delegate.onSwipeLeft()) {
                    MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                    onTouchEvent(event);
                    event.recycle();
                }
            }
        };
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        avatarImage.clearImage();
        currentPhoto = null;
    }

    private void init() {
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

            timePaintIn = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            timePaintIn.setTextSize(Utilities.dp(12));
            timePaintIn.setColor(0xffa1aab3);

            timePaintOut = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            timePaintOut.setTextSize(Utilities.dp(12));
            timePaintOut.setColor(0xff70b15c);

            timeMediaPaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            timeMediaPaint.setTextSize(Utilities.dp(12));
            timeMediaPaint.setColor(0xffffffff);

            namePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            namePaint.setTextSize(Utilities.dp(15));

            forwardNamePaint = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
            forwardNamePaint.setTextSize(Utilities.dp(14));
        }
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        invalidate();
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
        TLRPC.User newUser = MessagesController.getInstance().users.get(currentMessageObject.messageOwner.from_id);
        TLRPC.FileLocation newPhoto = null;

        if (isAvatarVisible && newUser != null && newUser.photo != null) {
            newPhoto = newUser.photo.photo_small;
        }

        if (currentPhoto == null && newPhoto != null || currentPhoto != null && newPhoto == null || currentPhoto != null && newPhoto != null && (currentPhoto.local_id != newPhoto.local_id || currentPhoto.volume_id != newPhoto.volume_id)) {
            return true;
        }

        String newNameString = null;
        if (drawName && isChat && newUser != null && !currentMessageObject.isOut()) {
            newNameString = Utilities.formatName(newUser.first_name, newUser.last_name);
        }

        if (currentNameString == null && newNameString != null || currentNameString != null && newNameString == null || currentNameString != null && newNameString != null && !currentNameString.equals(newNameString)) {
            return true;
        }

        newUser = MessagesController.getInstance().users.get(currentMessageObject.messageOwner.fwd_from_id);
        newNameString = null;
        if (drawForwardedName && currentMessageObject.messageOwner instanceof TLRPC.TL_messageForwarded) {
            newNameString = Utilities.formatName(newUser.first_name, newUser.last_name);
        }
        return currentForwardNameString == null && newNameString != null || currentForwardNameString != null && newNameString == null || currentForwardNameString != null && newNameString != null && !currentForwardNameString.equals(newNameString);
    }

    public void setMessageObject(MessageObject messageObject) {
        currentMessageObject = messageObject;
        isPressed = false;
        isCheckPressed = true;
        isAvatarVisible = false;
        wasLayout = false;

        if (currentMessageObject.messageOwner.id < 0 && currentMessageObject.messageOwner.send_state != MessagesController.MESSAGE_SEND_STATE_SEND_ERROR && currentMessageObject.messageOwner.send_state != MessagesController.MESSAGE_SEND_STATE_SENT) {
            if (MessagesController.getInstance().sendingMessages.get(currentMessageObject.messageOwner.id) == null) {
                currentMessageObject.messageOwner.send_state = MessagesController.MESSAGE_SEND_STATE_SEND_ERROR;
            }
        }

        currentUser = MessagesController.getInstance().users.get(messageObject.messageOwner.from_id);
        if (isChat && !messageObject.isOut()) {
            isAvatarVisible = true;
            if (currentUser != null) {
                if (currentUser.photo != null) {
                    currentPhoto = currentUser.photo.photo_small;
                } else {
                    currentPhoto = null;
                }
                avatarImage.setImage(currentPhoto, "50_50", getResources().getDrawable(Utilities.getUserAvatarForId(currentUser.id)));
            } else {
                avatarImage.setImage((TLRPC.FileLocation)null, "50_50", null);
            }
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
            currentNameString = Utilities.formatName(currentUser.first_name, currentUser.last_name);
            nameWidth = getMaxNameWidth();

            CharSequence nameStringFinal = TextUtils.ellipsize(currentNameString.replace("\n", " "), namePaint, nameWidth - Utilities.dp(12), TextUtils.TruncateAt.END);
            nameLayout = new StaticLayout(nameStringFinal, namePaint, nameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            if (nameLayout.getLineCount() > 0) {
                nameWidth = (int)Math.ceil(nameLayout.getLineWidth(0));
                namesOffset += Utilities.dp(18);
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
            currentForwardUser = MessagesController.getInstance().users.get(messageObject.messageOwner.fwd_from_id);
            if (currentForwardUser != null) {
                currentForwardNameString = Utilities.formatName(currentForwardUser.first_name, currentForwardUser.last_name);

                forwardedNameWidth = getMaxNameWidth();

                CharSequence str = TextUtils.ellipsize(currentForwardNameString.replace("\n", " "), forwardNamePaint, forwardedNameWidth - Utilities.dp(40), TextUtils.TruncateAt.END);
                str = Html.fromHtml(String.format("%s<br>%s <b>%s</b>", LocaleController.getString("ForwardedMessage", R.string.ForwardedMessage), LocaleController.getString("From", R.string.From), str));
                forwardedNameLayout = new StaticLayout(str, forwardNamePaint, forwardedNameWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                if (forwardedNameLayout.getLineCount() > 1) {
                    forwardedNameWidth = Math.max((int) Math.ceil(forwardedNameLayout.getLineWidth(0)), (int) Math.ceil(forwardedNameLayout.getLineWidth(1)));
                    namesOffset += Utilities.dp(36);
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
        return backgroundWidth - Utilities.dp(8);
    }

    protected void startCheckLongPress() {
        if (checkingForLongPress) {
            return;
        }
        checkingForLongPress = true;
        if (pendingCheckForTap == null) {
            pendingCheckForTap = new CheckForTap();
        }
        postDelayed(pendingCheckForTap, ViewConfiguration.getTapTimeout());
    }

    protected void cancelCheckLongPress() {
        checkingForLongPress = false;
        if (pendingCheckForLongPress != null) {
            removeCallbacks(pendingCheckForLongPress);
        }
        if (pendingCheckForTap != null) {
            removeCallbacks(pendingCheckForTap);
        }
    }

    protected void checkSwipes(MotionEvent event) {
        onSwipeTouchListener.onTouch(this, event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean result = false;
        float x = event.getX();
        float y = event.getY();
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (delegate == null || delegate.canPerformActions()) {
                if (isAvatarVisible && x >= avatarImage.imageX && x <= avatarImage.imageX + avatarImage.imageW && y >= avatarImage.imageY && y <= avatarImage.imageY + avatarImage.imageH) {
                    avatarPressed = true;
                    result = true;
                } else if (drawForwardedName && forwardedNameLayout != null) {
                    if (x >= forwardNameX && x <= forwardNameX + forwardedNameWidth && y >= forwardNameY && y <= forwardNameY + Utilities.dp(32)) {
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
                    if (isAvatarVisible && !(x >= avatarImage.imageX && x <= avatarImage.imageX + avatarImage.imageW && y >= avatarImage.imageY && y <= avatarImage.imageY + avatarImage.imageH)) {
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
                    if (!(x >= forwardNameX && x <= forwardNameX + forwardedNameWidth && y >= forwardNameY && y <= forwardNameY + Utilities.dp(32))) {
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
                    timeX = backgroundWidth - Utilities.dp(9) - timeWidth + (isChat ? Utilities.dp(52) : 0);
                } else {
                    timeX = layoutWidth - timeWidth - Utilities.dpf(38.5f);
                }
            } else {
                if (!currentMessageObject.isOut()) {
                    timeX = backgroundWidth - Utilities.dp(4) - timeWidth + (isChat ? Utilities.dp(52) : 0);
                } else {
                    timeX = layoutWidth - timeWidth - Utilities.dpf(42.0f);
                }
            }

            if (isAvatarVisible) {
                avatarImage.imageX = Utilities.dp(6);
                avatarImage.imageY = layoutHeight - Utilities.dp(45);
                avatarImage.imageW = Utilities.dp(42);
                avatarImage.imageH = Utilities.dp(42);
            }

            wasLayout = true;
        }
    }

    protected void onAfterBackgroundDraw(Canvas canvas) {

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
            avatarImage.draw(canvas, Utilities.dp(6), layoutHeight - Utilities.dp(45), Utilities.dp(42), Utilities.dp(42));
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
            setDrawableBounds(currentBackgroundDrawable, layoutWidth - backgroundWidth - (!media ? 0 : Utilities.dp(9)), Utilities.dp(1), backgroundWidth, layoutHeight - Utilities.dp(2));
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
                setDrawableBounds(currentBackgroundDrawable, Utilities.dp(52 + (!media ? 0 : 9)), Utilities.dp(1), backgroundWidth, layoutHeight - Utilities.dp(2));
            } else {
                setDrawableBounds(currentBackgroundDrawable, (!media ? 0 : Utilities.dp(9)), Utilities.dp(1), backgroundWidth, layoutHeight - Utilities.dp(2));
            }
        }
        currentBackgroundDrawable.draw(canvas);

        onAfterBackgroundDraw(canvas);

        if (drawName && nameLayout != null) {
            canvas.save();
            canvas.translate(currentBackgroundDrawable.getBounds().left + Utilities.dp(19) - nameOffsetX, Utilities.dp(10));
            namePaint.setColor(Utilities.getColorForId(currentUser.id));
            nameLayout.draw(canvas);
            canvas.restore();
        }

        if (drawForwardedName && forwardedNameLayout != null) {
            canvas.save();
            if (currentMessageObject.isOut()) {
                forwardNamePaint.setColor(0xff4a923c);
                forwardNameX = currentBackgroundDrawable.getBounds().left + Utilities.dp(10);
                forwardNameY = Utilities.dp(10 + (drawName ? 18 : 0));
            } else {
                forwardNamePaint.setColor(0xff006fc8);
                forwardNameX = currentBackgroundDrawable.getBounds().left + Utilities.dp(19);
                forwardNameY = Utilities.dp(10 + (drawName ? 18 : 0));
            }
            canvas.translate(forwardNameX - forwardNameOffsetX, forwardNameY);
            forwardedNameLayout.draw(canvas);
            canvas.restore();
        }

        if (media) {
            setDrawableBounds(mediaBackgroundDrawable, timeX - Utilities.dp(3), layoutHeight - Utilities.dpf(27.5f), timeWidth + Utilities.dp(6 + (currentMessageObject.isOut() ? 20 : 0)), Utilities.dpf(16.5f));
            mediaBackgroundDrawable.draw(canvas);

            canvas.save();
            canvas.translate(timeX, layoutHeight - Utilities.dpf(12.0f) - timeLayout.getHeight());
            timeLayout.draw(canvas);
            canvas.restore();
        } else {
            canvas.save();
            canvas.translate(timeX, layoutHeight - Utilities.dpf(6.5f) - timeLayout.getHeight());
            timeLayout.draw(canvas);
            canvas.restore();
        }

        if (currentMessageObject.isOut()) {
            boolean drawCheck1 = false;
            boolean drawCheck2 = false;
            boolean drawClock = false;
            boolean drawError = false;

            if (currentMessageObject.messageOwner.send_state == MessagesController.MESSAGE_SEND_STATE_SENDING) {
                drawCheck1 = false;
                drawCheck2 = false;
                drawClock = true;
                drawError = false;
            } else if (currentMessageObject.messageOwner.send_state == MessagesController.MESSAGE_SEND_STATE_SEND_ERROR) {
                drawCheck1 = false;
                drawCheck2 = false;
                drawClock = false;
                drawError = true;
            } else if (currentMessageObject.messageOwner.send_state == MessagesController.MESSAGE_SEND_STATE_SENT) {
                if (!currentMessageObject.messageOwner.unread) {
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
                    setDrawableBounds(clockDrawable, layoutWidth - Utilities.dpf(18.5f) - clockDrawable.getIntrinsicWidth(), layoutHeight - Utilities.dpf(8.5f) - clockDrawable.getIntrinsicHeight());
                    clockDrawable.draw(canvas);
                } else {
                    setDrawableBounds(clockMediaDrawable, layoutWidth - Utilities.dpf(22.0f) - clockMediaDrawable.getIntrinsicWidth(), layoutHeight - Utilities.dpf(13.0f) - clockMediaDrawable.getIntrinsicHeight());
                    clockMediaDrawable.draw(canvas);
                }
            }
            if (drawCheck2) {
                if (!media) {
                    if (drawCheck1) {
                        setDrawableBounds(checkDrawable, layoutWidth - Utilities.dpf(22.5f) - checkDrawable.getIntrinsicWidth(), layoutHeight - Utilities.dpf(8.5f) - checkDrawable.getIntrinsicHeight());
                    } else {
                        setDrawableBounds(checkDrawable, layoutWidth - Utilities.dpf(18.5f) - checkDrawable.getIntrinsicWidth(), layoutHeight - Utilities.dpf(8.5f) - checkDrawable.getIntrinsicHeight());
                    }
                    checkDrawable.draw(canvas);
                } else {
                    if (drawCheck1) {
                        setDrawableBounds(checkMediaDrawable, layoutWidth - Utilities.dpf(26.0f) - checkMediaDrawable.getIntrinsicWidth(), layoutHeight - Utilities.dpf(13.0f) - checkMediaDrawable.getIntrinsicHeight());
                    } else {
                        setDrawableBounds(checkMediaDrawable, layoutWidth - Utilities.dpf(22.0f) - checkMediaDrawable.getIntrinsicWidth(), layoutHeight - Utilities.dpf(13.0f) - checkMediaDrawable.getIntrinsicHeight());
                    }
                    checkMediaDrawable.draw(canvas);
                }
            }
            if (drawCheck1) {
                if (!media) {
                    setDrawableBounds(halfCheckDrawable, layoutWidth - Utilities.dp(18) - halfCheckDrawable.getIntrinsicWidth(), layoutHeight - Utilities.dpf(8.5f) - halfCheckDrawable.getIntrinsicHeight());
                    halfCheckDrawable.draw(canvas);
                } else {
                    setDrawableBounds(halfCheckMediaDrawable, layoutWidth - Utilities.dpf(20.5f) - halfCheckMediaDrawable.getIntrinsicWidth(), layoutHeight - Utilities.dpf(13.0f) - halfCheckMediaDrawable.getIntrinsicHeight());
                    halfCheckMediaDrawable.draw(canvas);
                }
            }
            if (drawError) {
                if (!media) {
                    setDrawableBounds(errorDrawable, layoutWidth - Utilities.dp(18) - errorDrawable.getIntrinsicWidth(), layoutHeight - Utilities.dpf(6.5f) - errorDrawable.getIntrinsicHeight());
                    errorDrawable.draw(canvas);
                } else {
                    setDrawableBounds(errorDrawable, layoutWidth - Utilities.dpf(20.5f) - errorDrawable.getIntrinsicWidth(), layoutHeight - Utilities.dpf(12.5f) - errorDrawable.getIntrinsicHeight());
                    errorDrawable.draw(canvas);
                }
            }
        }
    }
}
