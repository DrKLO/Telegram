/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.CheckBox;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LetterDrawable;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Locale;

public class SharedLinkCell extends FrameLayout {

    public interface SharedLinkCellDelegate {
        void needOpenWebView(TLRPC.WebPage webPage);
        boolean canPerformActions();
        void onLinkLongPress(final String urlFinal);
    }

    private boolean checkingForLongPress = false;
    private CheckForLongPress pendingCheckForLongPress = null;
    private int pressCount = 0;
    private CheckForTap pendingCheckForTap = null;

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
                checkingForLongPress = false;
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (pressedLink >= 0) {
                    delegate.onLinkLongPress(links.get(pressedLink));
                }
                MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                onTouchEvent(event);
                event.recycle();
            }
        }
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

    private boolean linkPreviewPressed;
    private LinkPath urlPath = new LinkPath();
    private int pressedLink;

    private ImageReceiver linkImageView;
    private boolean drawLinkImageView;
    private LetterDrawable letterDrawable;
    private CheckBox checkBox;

    private SharedLinkCellDelegate delegate;

    private boolean needDivider;

    ArrayList<String> links = new ArrayList<>();
    private int linkY;
    private ArrayList<StaticLayout> linkLayout = new ArrayList<>();

    private int titleY = AndroidUtilities.dp(7);
    private StaticLayout titleLayout;

    private int descriptionY = AndroidUtilities.dp(27);
    private StaticLayout descriptionLayout;

    private int description2Y = AndroidUtilities.dp(27);
    private StaticLayout descriptionLayout2;

    private MessageObject message;

    private TextPaint titleTextPaint;
    private TextPaint descriptionTextPaint;

    public SharedLinkCell(Context context) {
        super(context);

        titleTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titleTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleTextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));

        descriptionTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        titleTextPaint.setTextSize(AndroidUtilities.dp(16));
        descriptionTextPaint.setTextSize(AndroidUtilities.dp(16));

        setWillNotDraw(false);
        linkImageView = new ImageReceiver(this);
        letterDrawable = new LetterDrawable();

        checkBox = new CheckBox(context, R.drawable.round_check2);
        checkBox.setVisibility(INVISIBLE);
        checkBox.setColor(Theme.getColor(Theme.key_checkbox), Theme.getColor(Theme.key_checkboxCheck));
        addView(checkBox, LayoutHelper.createFrame(22, 22, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 44, 44, LocaleController.isRTL ? 44 : 0, 0));
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        drawLinkImageView = false;
        descriptionLayout = null;
        titleLayout = null;
        descriptionLayout2 = null;
        description2Y = descriptionY;
        linkLayout.clear();
        links.clear();

        int maxWidth = MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(AndroidUtilities.leftBaseline) - AndroidUtilities.dp(8);

        String title = null;
        String description = null;
        String description2 = null;
        String webPageLink = null;
        boolean hasPhoto = false;

        if (message.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && message.messageOwner.media.webpage instanceof TLRPC.TL_webPage) {
            TLRPC.WebPage webPage = message.messageOwner.media.webpage;
            if (message.photoThumbs == null && webPage.photo != null) {
                message.generateThumbs(true);
            }
            hasPhoto = webPage.photo != null && message.photoThumbs != null;
            title = webPage.title;
            if (title == null) {
                title = webPage.site_name;
            }
            description = webPage.description;
            webPageLink = webPage.url;
        }
        if (message != null && !message.messageOwner.entities.isEmpty()) {
            for (int a = 0; a < message.messageOwner.entities.size(); a++) {
                TLRPC.MessageEntity entity = message.messageOwner.entities.get(a);
                if (entity.length <= 0 || entity.offset < 0 || entity.offset >= message.messageOwner.message.length()) {
                    continue;
                } else if (entity.offset + entity.length > message.messageOwner.message.length()) {
                    entity.length = message.messageOwner.message.length() - entity.offset;
                }
                if (a == 0 && webPageLink != null && !(entity.offset == 0 && entity.length == message.messageOwner.message.length())) {
                    if (message.messageOwner.entities.size() == 1) {
                        if (description == null) {
                            description2 = message.messageOwner.message;
                        }
                    } else {
                        description2 = message.messageOwner.message;
                    }
                }
                try {
                    String link = null;
                    if (entity instanceof TLRPC.TL_messageEntityTextUrl || entity instanceof TLRPC.TL_messageEntityUrl) {
                        if (entity instanceof TLRPC.TL_messageEntityUrl) {
                            link = message.messageOwner.message.substring(entity.offset, entity.offset + entity.length);
                        } else {
                            link = entity.url;
                        }
                        if (title == null || title.length() == 0) {
                            title = link;
                            Uri uri = Uri.parse(title);
                            title = uri.getHost();
                            if (title == null) {
                                title = link;
                            }
                            int index;
                            if (title != null && (index = title.lastIndexOf('.')) >= 0) {
                                title = title.substring(0, index);
                                if ((index = title.lastIndexOf('.')) >= 0) {
                                    title = title.substring(index + 1);
                                }
                                title = title.substring(0, 1).toUpperCase() + title.substring(1);
                            }
                            if (entity.offset != 0 || entity.length != message.messageOwner.message.length()) {
                                description = message.messageOwner.message;
                            }
                        }
                    } else if (entity instanceof TLRPC.TL_messageEntityEmail) {
                        if (title == null || title.length() == 0) {
                            link = "mailto:" + message.messageOwner.message.substring(entity.offset, entity.offset + entity.length);
                            title = message.messageOwner.message.substring(entity.offset, entity.offset + entity.length);
                            if (entity.offset != 0 || entity.length != message.messageOwner.message.length()) {
                                description = message.messageOwner.message;
                            }
                        }
                    }
                    if (link != null) {
                        if (link.toLowerCase().indexOf("http") != 0 && link.toLowerCase().indexOf("mailto") != 0) {
                            links.add("http://" + link);
                        } else {
                            links.add(link);
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        if (webPageLink != null && links.isEmpty()) {
            links.add(webPageLink);
        }

        if (title != null) {
            try {
                int width = (int) Math.ceil(titleTextPaint.measureText(title));
                CharSequence titleFinal = TextUtils.ellipsize(title.replace('\n', ' '), titleTextPaint, Math.min(width, maxWidth), TextUtils.TruncateAt.END);
                titleLayout = new StaticLayout(titleFinal, titleTextPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            } catch (Exception e) {
                FileLog.e(e);
            }
            letterDrawable.setTitle(title);
        }

        if (description != null) {
            try {
                descriptionLayout = ChatMessageCell.generateStaticLayout(description, descriptionTextPaint, maxWidth, maxWidth, 0, 3);
                if (descriptionLayout.getLineCount() > 0) {
                    description2Y = descriptionY + descriptionLayout.getLineBottom(descriptionLayout.getLineCount() - 1) + AndroidUtilities.dp(1);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        if (description2 != null) {
            try {
                descriptionLayout2 = ChatMessageCell.generateStaticLayout(description2, descriptionTextPaint, maxWidth, maxWidth, 0, 3);
                int height = descriptionLayout2.getLineBottom(descriptionLayout2.getLineCount() - 1);
                if (descriptionLayout != null) {
                    description2Y += AndroidUtilities.dp(10);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        if (!links.isEmpty()) {
            for (int a = 0; a < links.size(); a++) {
                try {
                    String link = links.get(a);
                    int width = (int) Math.ceil(descriptionTextPaint.measureText(link));
                    CharSequence linkFinal = TextUtils.ellipsize(link.replace('\n', ' '), descriptionTextPaint, Math.min(width, maxWidth), TextUtils.TruncateAt.MIDDLE);
                    StaticLayout layout = new StaticLayout(linkFinal, descriptionTextPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    linkY = description2Y;
                    if (descriptionLayout2 != null && descriptionLayout2.getLineCount() != 0) {
                        linkY += descriptionLayout2.getLineBottom(descriptionLayout2.getLineCount() - 1) + AndroidUtilities.dp(1);
                    }
                    linkLayout.add(layout);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }

        int maxPhotoWidth = AndroidUtilities.dp(52);
        int x = LocaleController.isRTL ? MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(10) - maxPhotoWidth : AndroidUtilities.dp(10);
        letterDrawable.setBounds(x, AndroidUtilities.dp(10), x + maxPhotoWidth, AndroidUtilities.dp(62));

        if (hasPhoto) {
            TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, maxPhotoWidth, true);
            TLRPC.PhotoSize currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, 80);
            if (currentPhotoObjectThumb == currentPhotoObject) {
                currentPhotoObjectThumb = null;
            }
            currentPhotoObject.size = -1;
            if (currentPhotoObjectThumb != null) {
                currentPhotoObjectThumb.size = -1;
            }
            linkImageView.setImageCoords(x, AndroidUtilities.dp(10), maxPhotoWidth, maxPhotoWidth);
            String fileName = FileLoader.getAttachFileName(currentPhotoObject);
            String filter = String.format(Locale.US, "%d_%d", maxPhotoWidth, maxPhotoWidth);
            linkImageView.setImage(currentPhotoObject.location, filter, currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null, String.format(Locale.US, "%d_%d_b", maxPhotoWidth, maxPhotoWidth), 0, null, 0);
            drawLinkImageView = true;
        }

        int height = 0;
        if (titleLayout != null && titleLayout.getLineCount() != 0) {
            height += titleLayout.getLineBottom(titleLayout.getLineCount() - 1);
        }
        if (descriptionLayout != null && descriptionLayout.getLineCount() != 0) {
            height += descriptionLayout.getLineBottom(descriptionLayout.getLineCount() - 1);
        }
        if (descriptionLayout2 != null && descriptionLayout2.getLineCount() != 0) {
            height += descriptionLayout2.getLineBottom(descriptionLayout2.getLineCount() - 1);
            if (descriptionLayout != null) {
                height += AndroidUtilities.dp(10);
            }
        }
        for (int a = 0; a < linkLayout.size(); a++) {
            StaticLayout layout = linkLayout.get(a);
            if (layout.getLineCount() > 0) {
                height += layout.getLineBottom(layout.getLineCount() - 1);
            }
        }
        if (hasPhoto) {
            height = Math.max(AndroidUtilities.dp(48), height);
        }
        checkBox.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(22), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(22), MeasureSpec.EXACTLY));
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Math.max(AndroidUtilities.dp(72), height + AndroidUtilities.dp(16)) + (needDivider ? 1 : 0));
    }

    public void setLink(MessageObject messageObject, boolean divider) {
        needDivider = divider;
        resetPressedLink();
        message = messageObject;

        requestLayout();
    }

    public void setDelegate(SharedLinkCellDelegate sharedLinkCellDelegate) {
        delegate = sharedLinkCellDelegate;
    }

    public MessageObject getMessage() {
        return message;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (drawLinkImageView) {
            linkImageView.onDetachedFromWindow();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (drawLinkImageView) {
            linkImageView.onAttachedToWindow();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean result = false;
        if (message != null && !linkLayout.isEmpty() && delegate != null && delegate.canPerformActions()) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || linkPreviewPressed && event.getAction() == MotionEvent.ACTION_UP) {
                int x = (int) event.getX();
                int y = (int) event.getY();
                int offset = 0;
                boolean ok = false;
                for (int a = 0; a < linkLayout.size(); a++) {
                    StaticLayout layout = linkLayout.get(a);
                    if (layout.getLineCount() > 0) {
                        int height = layout.getLineBottom(layout.getLineCount() - 1);
                        int linkPosX = AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline);
                        if (x >= linkPosX + layout.getLineLeft(0) && x <= linkPosX + layout.getLineWidth(0) && y >= linkY + offset && y <= linkY + offset + height) {
                            ok = true;
                            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                                resetPressedLink();
                                pressedLink = a;
                                linkPreviewPressed = true;
                                startCheckLongPress();
                                try {
                                    urlPath.setCurrentLayout(layout, 0, 0);
                                    layout.getSelectionPath(0, layout.getText().length(), urlPath);
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                                result = true;
                            } else if (linkPreviewPressed) {
                                try {
                                    TLRPC.WebPage webPage = pressedLink == 0 && message.messageOwner.media != null ? message.messageOwner.media.webpage : null;
                                    if (webPage != null && webPage.embed_url != null && webPage.embed_url.length() != 0) {
                                        delegate.needOpenWebView(webPage);
                                    } else {
                                        Browser.openUrl(getContext(), links.get(pressedLink));
                                    }
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                                resetPressedLink();
                                result = true;
                            }
                            break;
                        }
                        offset += height;
                    }
                }
                if (!ok) {
                    resetPressedLink();
                }
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                resetPressedLink();
            }
        } else {
            resetPressedLink();
        }
        return result || super.onTouchEvent(event);
    }

    public String getLink(int num) {
        if (num < 0 || num >= links.size()) {
            return null;
        }
        return links.get(num);
    }

    protected void resetPressedLink() {
        pressedLink = -1;
        linkPreviewPressed = false;
        cancelCheckLongPress();
        invalidate();
    }

    public void setChecked(boolean checked, boolean animated) {
        if (checkBox.getVisibility() != VISIBLE) {
            checkBox.setVisibility(VISIBLE);
        }
        checkBox.setChecked(checked, animated);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (titleLayout != null) {
            canvas.save();
            canvas.translate(AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline), titleY);
            titleLayout.draw(canvas);
            canvas.restore();
        }

        if (descriptionLayout != null) {
            descriptionTextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            canvas.save();
            canvas.translate(AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline), descriptionY);
            descriptionLayout.draw(canvas);
            canvas.restore();
        }

        if (descriptionLayout2 != null) {
            descriptionTextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            canvas.save();
            canvas.translate(AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline), description2Y);
            descriptionLayout2.draw(canvas);
            canvas.restore();
        }

        if (!linkLayout.isEmpty()) {
            descriptionTextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText));
            int offset = 0;
            for (int a = 0; a < linkLayout.size(); a++) {
                StaticLayout layout = linkLayout.get(a);
                if (layout.getLineCount() > 0) {
                    canvas.save();
                    canvas.translate(AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline), linkY + offset);
                    if (pressedLink == a) {
                        canvas.drawPath(urlPath, Theme.linkSelectionPaint);
                    }
                    layout.draw(canvas);
                    canvas.restore();
                    offset += layout.getLineBottom(layout.getLineCount() - 1);
                }
            }
        }

        letterDrawable.draw(canvas);
        if (drawLinkImageView) {
            linkImageView.draw(canvas);
        }

        if (needDivider) {
            if (LocaleController.isRTL) {
                canvas.drawLine(0, getMeasuredHeight() - 1, getMeasuredWidth() - AndroidUtilities.dp(AndroidUtilities.leftBaseline), getMeasuredHeight() - 1, Theme.dividerPaint);
            } else {
                canvas.drawLine(AndroidUtilities.dp(AndroidUtilities.leftBaseline), getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }
    }
}
