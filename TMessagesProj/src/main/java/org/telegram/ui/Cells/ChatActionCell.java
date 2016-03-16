/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Spannable;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.ResourceLoader;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.Components.AvatarDrawable;

public class ChatActionCell extends BaseCell {

    public interface ChatActionCellDelegate {
        void didClickedImage(ChatActionCell cell);
        void didLongPressed(ChatActionCell cell);
        void needOpenUserProfile(int uid);
    }

    private static TextPaint textPaint;

    private URLSpan pressedLink;

    private ImageReceiver imageReceiver;
    private AvatarDrawable avatarDrawable;
    private StaticLayout textLayout;
    private int textWidth = 0;
    private int textHeight = 0;
    private int textX = 0;
    private int textY = 0;
    private int textXLeft = 0;
    private int previousWidth = 0;
    private boolean imagePressed = false;

    private boolean hasReplyMessage;

    private MessageObject currentMessageObject;

    private ChatActionCellDelegate delegate;

    public ChatActionCell(Context context) {
        super(context);
        if (textPaint == null) {
            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(0xffffffff);
            textPaint.linkColor = 0xffffffff;
        }
        imageReceiver = new ImageReceiver(this);
        imageReceiver.setRoundRadius(AndroidUtilities.dp(32));
        avatarDrawable = new AvatarDrawable();
        textPaint.setTextSize(AndroidUtilities.dp(MessagesController.getInstance().fontSize));
    }

    public void setDelegate(ChatActionCellDelegate delegate) {
        this.delegate = delegate;
    }

    public void setMessageObject(MessageObject messageObject) {
        if (currentMessageObject == messageObject && (hasReplyMessage || messageObject.replyMessageObject == null)) {
            return;
        }
        currentMessageObject = messageObject;
        hasReplyMessage = messageObject.replyMessageObject != null;
        previousWidth = 0;
        if (currentMessageObject.type == 11) {
            int id = 0;
            if (messageObject.messageOwner.to_id != null) {
                if (messageObject.messageOwner.to_id.chat_id != 0) {
                    id = messageObject.messageOwner.to_id.chat_id;
                } else if (messageObject.messageOwner.to_id.channel_id != 0) {
                    id = messageObject.messageOwner.to_id.channel_id;
                } else {
                    id = messageObject.messageOwner.to_id.user_id;
                    if (id == UserConfig.getClientUserId()) {
                        id = messageObject.messageOwner.from_id;
                    }
                }
            }
            avatarDrawable.setInfo(id, null, null, false);
            if (currentMessageObject.messageOwner.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                imageReceiver.setImage(currentMessageObject.messageOwner.action.newUserPhoto.photo_small, "50_50", avatarDrawable, null, false);
            } else {
                TLRPC.PhotoSize photo = FileLoader.getClosestPhotoSizeWithSize(currentMessageObject.photoThumbs, AndroidUtilities.dp(64));
                if (photo != null) {
                    imageReceiver.setImage(photo.location, "50_50", avatarDrawable, null, false);
                } else {
                    imageReceiver.setImageBitmap(avatarDrawable);
                }
            }
            imageReceiver.setVisible(!PhotoViewer.getInstance().isShowingImage(currentMessageObject), false);
        } else {
            imageReceiver.setImageBitmap((Bitmap)null);
        }
        requestLayout();
    }

    public MessageObject getMessageObject() {
        return currentMessageObject;
    }

    public ImageReceiver getPhotoImage() {
        return imageReceiver;
    }

    @Override
    protected void onLongPress() {
        if (delegate != null) {
            delegate.didLongPressed(this);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        boolean result = false;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (delegate != null) {
                if (currentMessageObject.type == 11 && imageReceiver.isInsideImage(x, y)) {
                    imagePressed = true;
                    result = true;
                }
                if (result) {
                    startCheckLongPress();
                }
            }
        } else {
            if (event.getAction() != MotionEvent.ACTION_MOVE) {
                cancelCheckLongPress();
            }
            if (imagePressed) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    imagePressed = false;
                    if (delegate != null) {
                        delegate.didClickedImage(this);
                        playSoundEffect(SoundEffectConstants.CLICK);
                    }
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                    imagePressed = false;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    if (!imageReceiver.isInsideImage(x, y)) {
                        imagePressed = false;
                    }
                }
            }
        }
        if (!result) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || pressedLink != null && event.getAction() == MotionEvent.ACTION_UP) {
                if (x >= textX && y >= textY && x <= textX + textWidth && y <= textY + textHeight) {
                    y -= textY;
                    x -= textXLeft;

                    final int line = textLayout.getLineForVertical((int)y);
                    final int off = textLayout.getOffsetForHorizontal(line, x);
                    final float left = textLayout.getLineLeft(line);
                    if (left <= x && left + textLayout.getLineWidth(line) >= x && currentMessageObject.messageText instanceof Spannable) {
                        Spannable buffer = (Spannable) currentMessageObject.messageText;
                        URLSpan[] link = buffer.getSpans(off, off, URLSpan.class);

                        if (link.length != 0) {
                            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                                pressedLink = link[0];
                                result = true;
                            } else {
                                if (link[0] == pressedLink) {
                                    if (delegate != null) {
                                        delegate.needOpenUserProfile(Integer.parseInt(link[0].getURL()));
                                    }
                                    result = true;
                                }
                            }
                        } else {
                            pressedLink = null;
                        }
                    } else {
                        pressedLink = null;
                    }
                } else {
                    pressedLink = null;
                }
            }
        }

        if (!result) {
            result = super.onTouchEvent(event);
        }

        return result;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (currentMessageObject == null) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), textHeight + AndroidUtilities.dp(14));
            return;
        }
        int width = Math.max(AndroidUtilities.dp(30), MeasureSpec.getSize(widthMeasureSpec));
        if (width != previousWidth) {
            previousWidth = width;
            int maxWidth = width - AndroidUtilities.dp(30);
            textLayout = new StaticLayout(currentMessageObject.messageText, textPaint, maxWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
            textHeight = 0;
            textWidth = 0;
            try {
                int linesCount = textLayout.getLineCount();
                for (int a = 0; a < linesCount; a++) {
                    float lineWidth;
                    try {
                        lineWidth = textLayout.getLineWidth(a);
                        if (lineWidth > maxWidth) {
                            lineWidth = maxWidth;
                        }
                        textHeight = (int)Math.max(textHeight, Math.ceil(textLayout.getLineBottom(a)));
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                        return;
                    }
                    textWidth = (int)Math.max(textWidth, Math.ceil(lineWidth));
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }

            textX = (width - textWidth) / 2;
            textY = AndroidUtilities.dp(7);
            textXLeft = (width - textLayout.getWidth()) / 2;

            if (currentMessageObject.type == 11) {
                imageReceiver.setImageCoords((width - AndroidUtilities.dp(64)) / 2, textHeight + AndroidUtilities.dp(15), AndroidUtilities.dp(64), AndroidUtilities.dp(64));
            }
        }
        setMeasuredDimension(width, textHeight + AndroidUtilities.dp(14 + (currentMessageObject.type == 11 ? 70 : 0)));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (currentMessageObject == null) {
            return;
        }

        Drawable backgroundDrawable;
        if (ApplicationLoader.isCustomTheme()) {
            backgroundDrawable = ResourceLoader.backgroundBlack;
        } else {
            backgroundDrawable = ResourceLoader.backgroundBlue;
        }
        backgroundDrawable.setBounds(textX - AndroidUtilities.dp(5), AndroidUtilities.dp(5), textX + textWidth + AndroidUtilities.dp(5), AndroidUtilities.dp(9) + textHeight);
        backgroundDrawable.draw(canvas);

        if (currentMessageObject.type == 11) {
            imageReceiver.draw(canvas);
        }

        if (textLayout != null) {
            canvas.save();
            canvas.translate(textXLeft, textY);
            textLayout.draw(canvas);
            canvas.restore();
        }
    }
}
