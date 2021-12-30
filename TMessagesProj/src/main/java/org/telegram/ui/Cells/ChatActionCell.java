/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.Layout;
import android.text.Spannable;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.spoilers.SpoilerEffect;
import org.telegram.ui.PhotoViewer;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class ChatActionCell extends BaseCell implements DownloadController.FileDownloadProgressListener, NotificationCenter.NotificationCenterDelegate {

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.startSpoilers) {
            setSpoilersSuppressed(false);
        } else if (id == NotificationCenter.stopSpoilers) {
            setSpoilersSuppressed(true);
        }
    }

    public void setSpoilersSuppressed(boolean s) {
        for (SpoilerEffect eff : spoilers)
            eff.setSuppressUpdates(s);
    }

    private boolean canDrawInParent;

    public interface ChatActionCellDelegate {
        default void didClickImage(ChatActionCell cell) {
        }

        default void didLongPress(ChatActionCell cell, float x, float y) {
        }

        default void needOpenUserProfile(long uid) {
        }

        default void didPressBotButton(MessageObject messageObject, TLRPC.KeyboardButton button) {
        }

        default void didPressReplyMessage(ChatActionCell cell, int id) {
        }

        default void needOpenInviteLink(TLRPC.TL_chatInviteExported invite) {

        }
    }

    public interface ThemeDelegate extends Theme.ResourcesProvider {

        int getCurrentColor();
    }

    private int TAG;

    private URLSpan pressedLink;
    private int currentAccount = UserConfig.selectedAccount;
    private ImageReceiver imageReceiver;
    private AvatarDrawable avatarDrawable;
    private StaticLayout textLayout;
    private int textWidth;
    private int textHeight;
    private int textX;
    private int textY;
    private int textXLeft;
    private int previousWidth;
    private boolean imagePressed;

    public List<SpoilerEffect> spoilers = new ArrayList<>();
    private Stack<SpoilerEffect> spoilersPool = new Stack<>();

    TextPaint textPaint;

    private float viewTop;
    private int backgroundHeight;
    private boolean visiblePartSet;

    private ImageLocation currentVideoLocation;

    private float lastTouchX;
    private float lastTouchY;

    private boolean wasLayout;

    private boolean hasReplyMessage;

    private MessageObject currentMessageObject;
    private int customDate;
    private CharSequence customText;

    private String overrideBackground;
    private String overrideText;
    private Paint overrideBackgroundPaint;
    private TextPaint overrideTextPaint;
    private int overrideColor;
    private ArrayList<Integer> lineWidths = new ArrayList<>();
    private ArrayList<Integer> lineHeights = new ArrayList<>();
    private Path backgroundPath = new Path();
    private RectF rect = new RectF();
    private boolean invalidatePath = true;
    private boolean invalidateColors = false;

    private ChatActionCellDelegate delegate;
    private ThemeDelegate themeDelegate;

    public ChatActionCell(Context context) {
        this(context, false, null);
    }

    public ChatActionCell(Context context, boolean canDrawInParent, ThemeDelegate themeDelegate) {
        super(context);
        this.canDrawInParent = canDrawInParent;
        this.themeDelegate = themeDelegate;
        imageReceiver = new ImageReceiver(this);
        imageReceiver.setRoundRadius(AndroidUtilities.roundMessageSize / 2);
        avatarDrawable = new AvatarDrawable();
        TAG = DownloadController.getInstance(currentAccount).generateObserverTag();
    }

    public void setDelegate(ChatActionCellDelegate delegate) {
        this.delegate = delegate;
    }

    public void setCustomDate(int date, boolean scheduled, boolean inLayout) {
        if (customDate == date || customDate / 3600 == date / 3600) {
            return;
        }
        CharSequence newText;
        if (scheduled) {
            if (date == 0x7ffffffe) {
                newText = LocaleController.getString("MessageScheduledUntilOnline", R.string.MessageScheduledUntilOnline);
            } else {
                newText = LocaleController.formatString("MessageScheduledOn", R.string.MessageScheduledOn, LocaleController.formatDateChat(date));
            }
        } else {
            newText = LocaleController.formatDateChat(date);
        }
        if (customText != null && TextUtils.equals(newText, customText)) {
            return;
        }
        customDate = date;
        customText = newText;
        updateTextInternal(inLayout);
    }

    private void updateTextInternal(boolean inLayout) {
        if (getMeasuredWidth() != 0) {
            createLayout(customText, getMeasuredWidth());
            invalidate();
        }
        if (!wasLayout) {
            if (inLayout) {
                AndroidUtilities.runOnUIThread(this::requestLayout);
            } else {
                requestLayout();
            }
        } else {
            buildLayout();
        }
    }

    public void setCustomText(CharSequence text) {
        customText = text;
        if (customText != null) {
            updateTextInternal(false);
        }
    }

    public void setOverrideColor(String background, String text) {
        overrideBackground = background;
        overrideText = text;
    }

    public void setMessageObject(MessageObject messageObject) {
        if (currentMessageObject == messageObject && (textLayout == null || TextUtils.equals(textLayout.getText(), messageObject.messageText)) && (hasReplyMessage || messageObject.replyMessageObject == null)) {
            return;
        }
        currentMessageObject = messageObject;
        hasReplyMessage = messageObject.replyMessageObject != null;
        DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
        previousWidth = 0;
        if (currentMessageObject.type == 11) {
            long id = messageObject.getDialogId();
            avatarDrawable.setInfo(id, null, null);
            if (currentMessageObject.messageOwner.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                imageReceiver.setImage(null, null, avatarDrawable, null, currentMessageObject, 0);
            } else {
                TLRPC.PhotoSize strippedPhotoSize = null;
                for (int a = 0, N = currentMessageObject.photoThumbs.size(); a < N; a++) {
                    TLRPC.PhotoSize photoSize = currentMessageObject.photoThumbs.get(a);
                    if (photoSize instanceof TLRPC.TL_photoStrippedSize) {
                        strippedPhotoSize = photoSize;
                        break;
                    }
                }
                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(currentMessageObject.photoThumbs, 640);
                if (photoSize != null) {
                    TLRPC.Photo photo = messageObject.messageOwner.action.photo;
                    TLRPC.VideoSize videoSize = null;
                    if (!photo.video_sizes.isEmpty() && SharedConfig.autoplayGifs) {
                        videoSize = photo.video_sizes.get(0);
                        if (!messageObject.mediaExists && !DownloadController.getInstance(currentAccount).canDownloadMedia(DownloadController.AUTODOWNLOAD_TYPE_VIDEO, videoSize.size)) {
                            currentVideoLocation = ImageLocation.getForPhoto(videoSize, photo);
                            String fileName = FileLoader.getAttachFileName(videoSize);
                            DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, currentMessageObject, this);
                            videoSize = null;
                        }
                    }
                    if (videoSize != null) {
                        imageReceiver.setImage(ImageLocation.getForPhoto(videoSize, photo), ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForObject(strippedPhotoSize, currentMessageObject.photoThumbsObject), "50_50_b", avatarDrawable, 0, null, currentMessageObject, 1);
                    } else {
                        imageReceiver.setImage(ImageLocation.getForObject(photoSize, currentMessageObject.photoThumbsObject), "150_150", ImageLocation.getForObject(strippedPhotoSize, currentMessageObject.photoThumbsObject), "50_50_b", avatarDrawable, 0, null, currentMessageObject, 1);
                    }
                } else {
                    imageReceiver.setImageBitmap(avatarDrawable);
                }
            }
            imageReceiver.setVisible(!PhotoViewer.isShowingImage(currentMessageObject), false);
        } else {
            imageReceiver.setImageBitmap((Bitmap) null);
        }
        requestLayout();
    }

    public MessageObject getMessageObject() {
        return currentMessageObject;
    }

    public ImageReceiver getPhotoImage() {
        return imageReceiver;
    }

    public void setVisiblePart(float visibleTop, int parentH) {
        visiblePartSet = true;
        backgroundHeight = parentH;
        viewTop = visibleTop;
    }

    @Override
    protected boolean onLongPress() {
        if (delegate != null) {
            delegate.didLongPress(this, lastTouchX, lastTouchY);
        }
        return true;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {

    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
        imageReceiver.onDetachedFromWindow();
        wasLayout = false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        imageReceiver.onAttachedToWindow();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (currentMessageObject == null) {
            return super.onTouchEvent(event);
        }
        float x = lastTouchX = event.getX();
        float y = lastTouchY = event.getY();

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
                        delegate.didClickImage(this);
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

                    final int line = textLayout.getLineForVertical((int) y);
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
                                        String url = link[0].getURL();
                                        if (url.startsWith("invite") && pressedLink instanceof URLSpanNoUnderline) {
                                            URLSpanNoUnderline spanNoUnderline = (URLSpanNoUnderline) pressedLink;
                                            TLObject object = spanNoUnderline.getObject();
                                            if (object instanceof TLRPC.TL_chatInviteExported) {
                                                TLRPC.TL_chatInviteExported invite = (TLRPC.TL_chatInviteExported) object;
                                                delegate.needOpenInviteLink(invite);
                                            }
                                        } else if (url.startsWith("game")) {
                                            delegate.didPressReplyMessage(this, currentMessageObject.getReplyMsgId());
                                            /*TLRPC.KeyboardButton gameButton = null;
                                            MessageObject messageObject = currentMessageObject.replyMessageObject;
                                            if (messageObject != null && messageObject.messageOwner.reply_markup != null) {
                                                for (int a = 0; a < messageObject.messageOwner.reply_markup.rows.size(); a++) {
                                                    TLRPC.TL_keyboardButtonRow row = messageObject.messageOwner.reply_markup.rows.get(a);
                                                    for (int b = 0; b < row.buttons.size(); b++) {
                                                        TLRPC.KeyboardButton button = row.buttons.get(b);
                                                        if (button instanceof TLRPC.TL_keyboardButtonGame && button.game_id == currentMessageObject.messageOwner.action.game_id) {
                                                            gameButton = button;
                                                            break;
                                                        }
                                                    }
                                                    if (gameButton != null) {
                                                        break;
                                                    }
                                                }
                                            }
                                            if (gameButton != null) {
                                                delegate.didPressBotButton(messageObject, gameButton);
                                            }*/
                                        } else if (url.startsWith("http")) {
                                            Browser.openUrl(getContext(), url);
                                        } else {
                                            delegate.needOpenUserProfile(Long.parseLong(url));
                                        }
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

    private void createLayout(CharSequence text, int width) {
        int maxWidth = width - AndroidUtilities.dp(30);
        invalidatePath = true;
        textLayout = new StaticLayout(text, (TextPaint) getThemedPaint(Theme.key_paint_chatActionText), maxWidth, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);

        spoilersPool.addAll(spoilers);
        spoilers.clear();
        if (text instanceof Spannable)
            SpoilerEffect.addSpoilers(this, textLayout, (Spannable) text, spoilersPool, spoilers);

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
                    textHeight = (int) Math.max(textHeight, Math.ceil(textLayout.getLineBottom(a)));
                } catch (Exception e) {
                    FileLog.e(e);
                    return;
                }
                textWidth = (int) Math.max(textWidth, Math.ceil(lineWidth));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        textX = (width - textWidth) / 2;
        textY = AndroidUtilities.dp(7);
        textXLeft = (width - textLayout.getWidth()) / 2;
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (currentMessageObject == null && customText == null) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), textHeight + AndroidUtilities.dp(14));
            return;
        }
        int width = Math.max(AndroidUtilities.dp(30), MeasureSpec.getSize(widthMeasureSpec));
        if (previousWidth != width) {
            wasLayout = true;
            previousWidth = width;
            buildLayout();
        }
        setMeasuredDimension(width, textHeight + (currentMessageObject != null && currentMessageObject.type == 11 ? AndroidUtilities.roundMessageSize + AndroidUtilities.dp(10) : 0) + AndroidUtilities.dp(14));
    }

    private void buildLayout() {
        CharSequence text;
        if (currentMessageObject != null) {
            if (currentMessageObject.messageOwner != null && currentMessageObject.messageOwner.media != null && currentMessageObject.messageOwner.media.ttl_seconds != 0) {
                if (currentMessageObject.messageOwner.media.photo instanceof TLRPC.TL_photoEmpty) {
                    text = LocaleController.getString("AttachPhotoExpired", R.string.AttachPhotoExpired);
                } else if (currentMessageObject.messageOwner.media.document instanceof TLRPC.TL_documentEmpty) {
                    text = LocaleController.getString("AttachVideoExpired", R.string.AttachVideoExpired);
                } else {
                    text = currentMessageObject.messageText;
                }
            } else {
                text = currentMessageObject.messageText;
            }
        } else {
            text = customText;
        }
        createLayout(text, previousWidth);
        if (currentMessageObject != null && currentMessageObject.type == 11) {
            imageReceiver.setImageCoords((previousWidth - AndroidUtilities.roundMessageSize) / 2, textHeight + AndroidUtilities.dp(19), AndroidUtilities.roundMessageSize, AndroidUtilities.roundMessageSize);
        }
    }

    public int getCustomDate() {
        return customDate;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (currentMessageObject != null && currentMessageObject.type == 11) {
            imageReceiver.draw(canvas);
        }

        if (textLayout == null) {
            return;
        }

        drawBackground(canvas, false);

        if (textPaint != null) {
            canvas.save();
            canvas.translate(textXLeft, textY);
            if (textLayout.getPaint() != textPaint) {
                buildLayout();
            }
            canvas.save();
            SpoilerEffect.clipOutCanvas(canvas, spoilers);
            textLayout.draw(canvas);
            canvas.restore();

            for (SpoilerEffect eff : spoilers) {
                eff.setColor(textLayout.getPaint().getColor());
                eff.draw(canvas);
            }

            canvas.restore();
        }
    }

    public void drawBackground(Canvas canvas, boolean fromParent) {
        if (canDrawInParent) {
            if (hasGradientService() && !fromParent) {
                return;
            }
            if (!hasGradientService() && fromParent) {
                return;
            }
        }
        Paint backgroundPaint = getThemedPaint(Theme.key_paint_chatActionBackground);
        textPaint = (TextPaint) getThemedPaint(Theme.key_paint_chatActionText);
        if (overrideBackground != null) {
            int color = getThemedColor(overrideBackground);
            if (overrideBackgroundPaint == null) {
                overrideBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                overrideBackgroundPaint.setColor(color);
                overrideTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                overrideTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                overrideTextPaint.setTextSize(AndroidUtilities.dp(Math.max(16, SharedConfig.fontSize) - 2));
                overrideTextPaint.setColor(getThemedColor(overrideText));
            }
            backgroundPaint = overrideBackgroundPaint;
            textPaint = overrideTextPaint;
        }
        if (invalidatePath) {
            invalidatePath = false;
            lineWidths.clear();
            final int count = textLayout.getLineCount();
            final int corner = AndroidUtilities.dp(11);
            final int cornerIn = AndroidUtilities.dp(8);

            int prevLineWidth = 0;
            for (int a = 0; a < count; a++) {
                int lineWidth = (int) Math.ceil(textLayout.getLineWidth(a));
                if (a != 0) {
                    int diff = prevLineWidth - lineWidth;
                    if (diff > 0 && diff <= corner + cornerIn) {
                        lineWidth = prevLineWidth;
                    }
                }
                lineWidths.add(lineWidth);
                prevLineWidth = lineWidth;
            }
            for (int a = count - 2; a >= 0; a--) {
                int lineWidth = lineWidths.get(a);
                int diff = prevLineWidth - lineWidth;
                if (diff > 0 && diff <= corner + cornerIn) {
                    lineWidth = prevLineWidth;
                }
                lineWidths.set(a, lineWidth);
                prevLineWidth = lineWidth;
            }

            int y = AndroidUtilities.dp(4);
            int x = getMeasuredWidth() / 2;
            int previousLineBottom = 0;

            final int cornerOffset = AndroidUtilities.dp(3);
            final int cornerInSmall = AndroidUtilities.dp(6);
            final int cornerRest = corner - cornerOffset;

            lineHeights.clear();
            backgroundPath.reset();
            backgroundPath.moveTo(x, y);

            for (int a = 0; a < count; a++) {
                int lineWidth = lineWidths.get(a);
                int lineBottom = textLayout.getLineBottom(a);
                int nextLineWidth = a < count - 1 ? lineWidths.get(a + 1) : 0;

                int height = lineBottom - previousLineBottom;
                if (a == 0 || lineWidth > prevLineWidth) {
                    height += AndroidUtilities.dp(3);
                }
                if (a == count - 1 || lineWidth > nextLineWidth) {
                    height += AndroidUtilities.dp(3);
                }

                previousLineBottom = lineBottom;

                float startX = x + lineWidth / 2.0f;

                int innerCornerRad;
                if (a != count - 1 && lineWidth < nextLineWidth && a != 0 && lineWidth < prevLineWidth) {
                    innerCornerRad = cornerInSmall;
                } else {
                    innerCornerRad = cornerIn;
                }

                if (a == 0 || lineWidth > prevLineWidth) {
                    rect.set(startX - cornerOffset - corner, y, startX + cornerRest, y + corner * 2);
                    backgroundPath.arcTo(rect, -90, 90);
                } else if (lineWidth < prevLineWidth) {
                    rect.set(startX + cornerRest, y, startX + cornerRest + innerCornerRad * 2, y + innerCornerRad * 2);
                    backgroundPath.arcTo(rect, -90, -90);
                }
                y += height;
                int yOffset = y;
                if (a != count - 1 && lineWidth < nextLineWidth) {
                    y -= AndroidUtilities.dp(3);
                    height -= AndroidUtilities.dp(3);
                }
                if (a != 0 && lineWidth < prevLineWidth) {
                    y -= AndroidUtilities.dp(3);
                    height -= AndroidUtilities.dp(3);
                }
                lineHeights.add(height);

                if (a == count - 1 || lineWidth > nextLineWidth) {
                    rect.set(startX - cornerOffset - corner, y - corner * 2, startX + cornerRest, y);
                    backgroundPath.arcTo(rect, 0, 90);
                } else if (lineWidth < nextLineWidth) {
                    rect.set(startX + cornerRest, y - innerCornerRad * 2, startX + cornerRest + innerCornerRad * 2, y);
                    backgroundPath.arcTo(rect, 180, -90);
                }

                prevLineWidth = lineWidth;
            }
            for (int a = count - 1; a >= 0; a--) {
                prevLineWidth = a != 0 ? lineWidths.get(a - 1) : 0;
                int lineWidth = lineWidths.get(a);
                int nextLineWidth = a != count - 1 ? lineWidths.get(a + 1) : 0;
                int lineBottom = textLayout.getLineBottom(a);
                float startX = x - lineWidth / 2;

                int innerCornerRad;
                if (a != count - 1 && lineWidth < nextLineWidth && a != 0 && lineWidth < prevLineWidth) {
                    innerCornerRad = cornerInSmall;
                } else {
                    innerCornerRad = cornerIn;
                }

                if (a == count - 1 || lineWidth > nextLineWidth) {
                    rect.set(startX - cornerRest, y - corner * 2, startX + cornerOffset + corner, y);
                    backgroundPath.arcTo(rect, 90, 90);
                } else if (lineWidth < nextLineWidth) {
                    rect.set(startX - cornerRest - innerCornerRad * 2, y - innerCornerRad * 2, startX - cornerRest, y);
                    backgroundPath.arcTo(rect, 90, -90);
                }

                y -= lineHeights.get(a);

                if (a == 0 || lineWidth > prevLineWidth) {
                    rect.set(startX - cornerRest, y, startX + cornerOffset + corner, y + corner * 2);
                    backgroundPath.arcTo(rect, 180, 90);
                } else if (lineWidth < prevLineWidth) {
                    rect.set(startX - cornerRest - innerCornerRad * 2, y, startX - cornerRest, y + innerCornerRad * 2);
                    backgroundPath.arcTo(rect, 0, -90);
                }
            }
            backgroundPath.close();
        }
        if (!visiblePartSet) {
            ViewGroup parent = (ViewGroup) getParent();
            backgroundHeight = parent.getMeasuredHeight();
        }
        if (themeDelegate != null) {
            themeDelegate.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, 0, viewTop + AndroidUtilities.dp(4));
        } else {
            Theme.applyServiceShaderMatrix(getMeasuredWidth(), backgroundHeight, 0, viewTop + AndroidUtilities.dp(4));
        }

        int oldAlpha = -1;
        int oldAlpha2 = -1;
        if (fromParent && getAlpha() != 1f) {
            oldAlpha = backgroundPaint.getAlpha();
            oldAlpha2 = Theme.chat_actionBackgroundGradientDarkenPaint.getAlpha();
            backgroundPaint.setAlpha((int) (oldAlpha * getAlpha()));
            Theme.chat_actionBackgroundGradientDarkenPaint.setAlpha((int) (oldAlpha2 * getAlpha()));
        }
        canvas.drawPath(backgroundPath, backgroundPaint);
        if (hasGradientService()) {
            canvas.drawPath(backgroundPath, Theme.chat_actionBackgroundGradientDarkenPaint);
        }

        if (oldAlpha >= 0) {
            backgroundPaint.setAlpha(oldAlpha);
            Theme.chat_actionBackgroundGradientDarkenPaint.setAlpha(oldAlpha2);
        }
    }

    public boolean hasGradientService() {
        return overrideBackgroundPaint == null && (themeDelegate != null ? themeDelegate.hasGradientService() : Theme.hasGradientService());
    }

    @Override
    public void onFailedDownload(String fileName, boolean canceled) {

    }

    @Override
    public void onSuccessDownload(String fileName) {
        if (currentMessageObject != null && currentMessageObject.type == 11) {
            TLRPC.PhotoSize strippedPhotoSize = null;
            for (int a = 0, N = currentMessageObject.photoThumbs.size(); a < N; a++) {
                TLRPC.PhotoSize photoSize = currentMessageObject.photoThumbs.get(a);
                if (photoSize instanceof TLRPC.TL_photoStrippedSize) {
                    strippedPhotoSize = photoSize;
                    break;
                }
            }
            imageReceiver.setImage(currentVideoLocation, ImageLoader.AUTOPLAY_FILTER, ImageLocation.getForObject(strippedPhotoSize, currentMessageObject.photoThumbsObject), "50_50_b", avatarDrawable, 0, null, currentMessageObject, 1);
            DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
        }
    }

    @Override
    public void onProgressDownload(String fileName, long downloadSize, long totalSize) {

    }

    @Override
    public void onProgressUpload(String fileName, long downloadSize, long totalSize, boolean isEncrypted) {

    }

    @Override
    public int getObserverTag() {
        return TAG;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (TextUtils.isEmpty(customText) && currentMessageObject == null) {
            return;
        }
        info.setText(!TextUtils.isEmpty(customText) ? customText : currentMessageObject.messageText);
        info.setEnabled(true);
    }

    public void setInvalidateColors(boolean invalidate) {
        if (invalidateColors == invalidate) {
            return;
        }
        invalidateColors = invalidate;
        invalidate();
    }

    private int getThemedColor(String key) {
        Integer color = themeDelegate != null ? themeDelegate.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }

    private Paint getThemedPaint(String paintKey) {
        Paint paint = themeDelegate != null ? themeDelegate.getPaint(paintKey) : null;
        return paint != null ? paint : Theme.getThemePaint(paintKey);
    }
}
