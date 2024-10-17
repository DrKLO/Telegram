/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Region;
import android.net.Uri;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LetterDrawable;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.spoilers.SpoilerEffect;
import org.telegram.ui.FilteredSearchView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;

public class SharedLinkCell extends FrameLayout {
    private final static int SPOILER_TYPE_LINK = 0,
            SPOILER_TYPE_DESCRIPTION = 1,
            SPOILER_TYPE_DESCRIPTION2 = 2;

    public interface SharedLinkCellDelegate {
        void needOpenWebView(TLRPC.WebPage webPage, MessageObject messageObject);
        boolean canPerformActions();
        void onLinkPress(final String urlFinal, boolean longPress);
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
                if (pressedLinkIndex >= 0) {
                    delegate.onLinkPress(links.get(pressedLinkIndex).toString(), true);
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

    private LinkSpanDrawable.LinkCollector linksCollector = new LinkSpanDrawable.LinkCollector(this);
    private boolean linkPreviewPressed;
    private int pressedLinkIndex;
    private LinkSpanDrawable pressedLink;

    private ImageReceiver linkImageView;
    private boolean drawLinkImageView;
    private LetterDrawable letterDrawable;
    private CheckBox2 checkBox;

    private SharedLinkCellDelegate delegate;

    private boolean needDivider;

    ArrayList<CharSequence> links = new ArrayList<>();
    private int linkY;
    private ArrayList<StaticLayout> linkLayout = new ArrayList<>();
    private SparseArray<List<SpoilerEffect>> linkSpoilers = new SparseArray<>();
    private List<SpoilerEffect> descriptionLayoutSpoilers = new ArrayList<>();
    private List<SpoilerEffect> descriptionLayout2Spoilers = new ArrayList<>();
    private Stack<SpoilerEffect> spoilersPool = new Stack<>();
    private Path path = new Path();
    private SpoilerEffect spoilerPressed;
    private int spoilerTypePressed = -1;

    private int titleY = AndroidUtilities.dp(10);
    private StaticLayout titleLayout;

    private int descriptionY = AndroidUtilities.dp(30);
    private StaticLayout descriptionLayout;
    private AtomicReference<Layout> patchedDescriptionLayout = new AtomicReference<>();

    private int description2Y = AndroidUtilities.dp(30);
    private StaticLayout descriptionLayout2;
    private AtomicReference<Layout> patchedDescriptionLayout2 = new AtomicReference<>();

    private int captionY = AndroidUtilities.dp(30);
    private StaticLayout captionLayout;

    private MessageObject message;

    private TextPaint titleTextPaint;
    private TextPaint descriptionTextPaint;
    private TextPaint description2TextPaint;
    private TextPaint captionTextPaint;

    private int dateLayoutX;
    private StaticLayout dateLayout;
    private int fromInfoLayoutY = AndroidUtilities.dp(30);
    private StaticLayout fromInfoLayout;
    private AnimatedEmojiSpan.EmojiGroupedSpans fromInfoLayoutEmojis;

    private Theme.ResourcesProvider resourcesProvider;
    private int viewType;
    public final static int VIEW_TYPE_DEFAULT = 0;
    public final static int VIEW_TYPE_GLOBAL_SEARCH = 1;

    public SharedLinkCell(Context context) {
        this(context, VIEW_TYPE_DEFAULT, null);
    }

    public SharedLinkCell(Context context, int viewType) {
        this(context, viewType, null);
    }

    public SharedLinkCell(Context context, int viewType, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        this.viewType = viewType;
        setFocusable(true);

        titleTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titleTextPaint.setTypeface(AndroidUtilities.bold());
        titleTextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));

        descriptionTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

        titleTextPaint.setTextSize(AndroidUtilities.dp(14));
        descriptionTextPaint.setTextSize(AndroidUtilities.dp(14));

        setWillNotDraw(false);
        linkImageView = new ImageReceiver(this);
        linkImageView.setRoundRadius(AndroidUtilities.dp(4));
        letterDrawable = new LetterDrawable(resourcesProvider, LetterDrawable.STYLE_DEFAULT);

        checkBox = new CheckBox2(context, 21, resourcesProvider);
        checkBox.setVisibility(INVISIBLE);
        checkBox.setColor(-1, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
        checkBox.setDrawUnchecked(false);
        checkBox.setDrawBackgroundAsArc(2);
        addView(checkBox, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 44, 44, LocaleController.isRTL ? 44 : 0, 0));

        if (viewType == VIEW_TYPE_GLOBAL_SEARCH) {
            description2TextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            description2TextPaint.setTextSize(AndroidUtilities.dp(13));
        }

        captionTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        captionTextPaint.setTextSize(AndroidUtilities.dp(13));
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        drawLinkImageView = false;
        descriptionLayout = null;
        titleLayout = null;
        descriptionLayout2 = null;
        captionLayout = null;
        linkLayout.clear();
        links.clear();

        int maxWidth = MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(AndroidUtilities.leftBaseline) - AndroidUtilities.dp(8);

        String title = null;
        CharSequence description = null;
        CharSequence description2 = null;
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
                            SpannableStringBuilder st = SpannableStringBuilder.valueOf(message.messageOwner.message);
                            MediaDataController.addTextStyleRuns(message, st);
                            description2 = st;
                        }
                    } else {
                        SpannableStringBuilder st = SpannableStringBuilder.valueOf(message.messageOwner.message);
                        MediaDataController.addTextStyleRuns(message, st);
                        description2 = st;
                    }
                }
                try {
                    CharSequence link = null;
                    if (entity instanceof TLRPC.TL_messageEntityTextUrl || entity instanceof TLRPC.TL_messageEntityUrl) {
                        if (entity instanceof TLRPC.TL_messageEntityUrl) {
                            link = message.messageOwner.message.substring(entity.offset, entity.offset + entity.length);
                        } else {
                            link = entity.url;
                        }
                        if (title == null || title.length() == 0) {
                            title = link.toString();
                            Uri uri = Uri.parse(title);
                            title = uri.getHost();
                            if (title == null) {
                                title = link.toString();
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
                                SpannableStringBuilder st = SpannableStringBuilder.valueOf(message.messageOwner.message);
                                MediaDataController.addTextStyleRuns(message, st);
                                description = st;
                            }
                        }
                    } else if (entity instanceof TLRPC.TL_messageEntityEmail) {
                        if (title == null || title.length() == 0) {
                            link = "mailto:" + message.messageOwner.message.substring(entity.offset, entity.offset + entity.length);
                            title = message.messageOwner.message.substring(entity.offset, entity.offset + entity.length);
                            if (entity.offset != 0 || entity.length != message.messageOwner.message.length()) {
                                SpannableStringBuilder st = SpannableStringBuilder.valueOf(message.messageOwner.message);
                                MediaDataController.addTextStyleRuns(message, st);
                                description = st;
                            }
                        }
                    }
                    if (link != null) {
                        CharSequence lobj;
                        int offset = 0;
                        if (!AndroidUtilities.charSequenceContains(link, "://") && link.toString().toLowerCase().indexOf("http") != 0 && link.toString().toLowerCase().indexOf("mailto") != 0) {
                            String prefix = "http://";
                            lobj = prefix + link;
                            offset += prefix.length();
                        } else {
                            lobj = link;
                        }
                        SpannableString sb = SpannableString.valueOf(lobj);
                        int start = entity.offset, end = entity.offset + entity.length;
                        for (TLRPC.MessageEntity e : message.messageOwner.entities) {
                            int ss = e.offset, se = e.offset + e.length;
                            if (e instanceof TLRPC.TL_messageEntitySpoiler && start <= se && end >= ss) {
                                TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                                run.flags |= TextStyleSpan.FLAG_STYLE_SPOILER;
                                sb.setSpan(new TextStyleSpan(run), Math.max(start, ss), Math.min(end, se) + offset, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                        }
                        links.add(sb);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        if (webPageLink != null && links.isEmpty()) {
            links.add(webPageLink);
        }

        int dateWidth = 0;
        if (viewType == VIEW_TYPE_GLOBAL_SEARCH) {
            String str = LocaleController.stringForMessageListDate(message.messageOwner.date);
            int width = (int) Math.ceil(description2TextPaint.measureText(str));
            dateLayout = ChatMessageCell.generateStaticLayout(str, description2TextPaint, width, width, 0, 1);
            dateLayoutX = maxWidth - width - AndroidUtilities.dp(8);
            dateWidth = width + AndroidUtilities.dp(12);
        }

        if (title != null) {
            try {
                CharSequence titleFinal = title;
                CharSequence titleH = AndroidUtilities.highlightText(titleFinal, message.highlightedWords, null);
                if (titleH != null) {
                    titleFinal = titleH;
                }

                titleLayout = ChatMessageCell.generateStaticLayout(titleFinal, titleTextPaint, maxWidth - dateWidth - AndroidUtilities.dp(4), maxWidth - dateWidth - AndroidUtilities.dp(4), 0, 3);
                if (titleLayout.getLineCount() > 0) {
                    descriptionY = titleY + titleLayout.getLineBottom(titleLayout.getLineCount() - 1) + AndroidUtilities.dp(4);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            letterDrawable.setTitle(title);
        }
        description2Y = descriptionY;
        int desctiptionLines = Math.max(1, 4 - (titleLayout != null ? titleLayout.getLineCount() : 0));

        if (viewType == VIEW_TYPE_GLOBAL_SEARCH) {
            description = null;
            description2 = null;
        }

        if (description != null) {
            try {
                descriptionLayout = ChatMessageCell.generateStaticLayout(description, descriptionTextPaint, maxWidth, maxWidth, 0, desctiptionLines);
                if (descriptionLayout.getLineCount() > 0) {
                    description2Y = descriptionY + descriptionLayout.getLineBottom(descriptionLayout.getLineCount() - 1) + AndroidUtilities.dp(5);
                }
                spoilersPool.addAll(descriptionLayoutSpoilers);
                descriptionLayoutSpoilers.clear();
                if (!message.isSpoilersRevealed)
                    SpoilerEffect.addSpoilers(this, descriptionLayout, spoilersPool, descriptionLayoutSpoilers);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        if (description2 != null) {
            try {
                descriptionLayout2 = ChatMessageCell.generateStaticLayout(description2, descriptionTextPaint, maxWidth, maxWidth, 0, desctiptionLines);
                if (descriptionLayout != null) {
                    description2Y += AndroidUtilities.dp(10);
                }
                spoilersPool.addAll(descriptionLayout2Spoilers);
                descriptionLayout2Spoilers.clear();
                if (!message.isSpoilersRevealed)
                    SpoilerEffect.addSpoilers(this, descriptionLayout2, spoilersPool, descriptionLayout2Spoilers);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        if (message != null && !TextUtils.isEmpty(message.messageOwner.message)) {
            CharSequence caption = Emoji.replaceEmoji(message.messageOwner.message.replace("\n", " ").replaceAll(" +", " ").trim(), Theme.chat_msgTextPaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
            CharSequence sequence = AndroidUtilities.highlightText(caption, message.highlightedWords, null);
            if (sequence != null) {
                sequence = TextUtils.ellipsize(AndroidUtilities.ellipsizeCenterEnd(sequence, message.highlightedWords.get(0), maxWidth, captionTextPaint, 130), captionTextPaint, maxWidth, TextUtils.TruncateAt.END);
                captionLayout = new StaticLayout(sequence, captionTextPaint, maxWidth + AndroidUtilities.dp(4), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }
        }

        if (captionLayout != null) {
            captionY = descriptionY;
            descriptionY += captionLayout.getLineBottom(captionLayout.getLineCount() - 1) + AndroidUtilities.dp(5);
            description2Y = descriptionY;
        }

        if (!links.isEmpty()) {
            for (int i = 0; i < linkSpoilers.size(); i++)
                spoilersPool.addAll(linkSpoilers.get(i));
            linkSpoilers.clear();
            for (int a = 0; a < links.size(); a++) {
                try {
                    CharSequence link = links.get(a);
                    int width = (int) Math.ceil(descriptionTextPaint.measureText(link, 0, link.length()));
                    CharSequence linkFinal = TextUtils.ellipsize(AndroidUtilities.replaceNewLines(SpannableStringBuilder.valueOf(link)), descriptionTextPaint, Math.min(width, maxWidth), TextUtils.TruncateAt.MIDDLE);
                    StaticLayout layout = new StaticLayout(linkFinal, descriptionTextPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    linkY = description2Y;
                    if (descriptionLayout2 != null && descriptionLayout2.getLineCount() != 0) {
                        linkY += descriptionLayout2.getLineBottom(descriptionLayout2.getLineCount() - 1) + AndroidUtilities.dp(5);
                    }
                    if (!message.isSpoilersRevealed) {
                        List<SpoilerEffect> l = new ArrayList<>();
                        if (linkFinal instanceof Spannable)
                            SpoilerEffect.addSpoilers(this, layout, (Spannable) linkFinal, spoilersPool, l);
                        linkSpoilers.put(a, l);
                    }
                    linkLayout.add(layout);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }

        int maxPhotoWidth = AndroidUtilities.dp(52);
        int x = LocaleController.isRTL ? MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(10) - maxPhotoWidth : AndroidUtilities.dp(10);
        letterDrawable.setBounds(x, AndroidUtilities.dp(11), x + maxPhotoWidth, AndroidUtilities.dp(63));

        if (hasPhoto) {
            TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, maxPhotoWidth, true);
            TLRPC.PhotoSize currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, 80);
            if (currentPhotoObjectThumb == currentPhotoObject) {
                currentPhotoObjectThumb = null;
            }
            if (currentPhotoObject != null) {
                currentPhotoObject.size = -1;
            }
            if (currentPhotoObjectThumb != null) {
                currentPhotoObjectThumb.size = -1;
            }
            linkImageView.setImageCoords(x, AndroidUtilities.dp(11), maxPhotoWidth, maxPhotoWidth);
            String fileName = FileLoader.getAttachFileName(currentPhotoObject);
            String filter = String.format(Locale.US, "%d_%d", maxPhotoWidth, maxPhotoWidth);
            String thumbFilter = String.format(Locale.US, "%d_%d_b", maxPhotoWidth, maxPhotoWidth);
            linkImageView.setImage(ImageLocation.getForObject(currentPhotoObject, message.photoThumbsObject), filter, ImageLocation.getForObject(currentPhotoObjectThumb, message.photoThumbsObject), thumbFilter, 0, null, message, 0);
            drawLinkImageView = true;
        }

        if (viewType == VIEW_TYPE_GLOBAL_SEARCH) {
            fromInfoLayout = ChatMessageCell.generateStaticLayout(FilteredSearchView.createFromInfoString(message, true, 2, description2TextPaint), description2TextPaint, maxWidth, maxWidth, 0, desctiptionLines);
            fromInfoLayoutEmojis = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, fromInfoLayoutEmojis, fromInfoLayout);
        }

        int height = 0;
        if (titleLayout != null && titleLayout.getLineCount() != 0) {
            height += titleLayout.getLineBottom(titleLayout.getLineCount() - 1) + AndroidUtilities.dp(4);
        }
        if (captionLayout != null && captionLayout.getLineCount() != 0) {
            height += captionLayout.getLineBottom(captionLayout.getLineCount() - 1) + AndroidUtilities.dp(5);
        }
        if (descriptionLayout != null && descriptionLayout.getLineCount() != 0) {
            height += descriptionLayout.getLineBottom(descriptionLayout.getLineCount() - 1) + AndroidUtilities.dp(5);
        }
        if (descriptionLayout2 != null && descriptionLayout2.getLineCount() != 0) {
            height += descriptionLayout2.getLineBottom(descriptionLayout2.getLineCount() - 1) + AndroidUtilities.dp(5);
            if (descriptionLayout != null) {
                height += AndroidUtilities.dp(10);
            }
        }
        int linksHeight = 0;
        for (int a = 0; a < linkLayout.size(); a++) {
            StaticLayout layout = linkLayout.get(a);
            if (layout.getLineCount() > 0) {
                linksHeight += layout.getLineBottom(layout.getLineCount() - 1);
            }
        }
        height += linksHeight;

        if (fromInfoLayout != null) {
            fromInfoLayoutY = linkY + linksHeight + AndroidUtilities.dp(5);
            height += fromInfoLayout.getLineBottom(fromInfoLayout.getLineCount() - 1) + AndroidUtilities.dp(5);
        }
        checkBox.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY));
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), Math.max(AndroidUtilities.dp(76), height + AndroidUtilities.dp(17)) + (needDivider ? 1 : 0));
    }

    public void setLink(MessageObject messageObject, boolean divider) {
        needDivider = divider;
        resetPressedLink();
        message = messageObject;

        requestLayout();
    }

    public ImageReceiver getLinkImageView() {
        return linkImageView;
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
        AnimatedEmojiSpan.release(this, fromInfoLayoutEmojis);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (drawLinkImageView) {
            linkImageView.onAttachedToWindow();
        }
        fromInfoLayoutEmojis = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, fromInfoLayoutEmojis, fromInfoLayout);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean result = false;
        if (message != null && !linkLayout.isEmpty() && delegate != null && delegate.canPerformActions()) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || (linkPreviewPressed || spoilerPressed != null) && event.getAction() == MotionEvent.ACTION_UP) {
                int x = (int) event.getX();
                int y = (int) event.getY();
                int offset = 0;
                boolean ok = false;
                for (int a = 0; a < linkLayout.size(); a++) {
                    StaticLayout layout = linkLayout.get(a);
                    if (layout.getLineCount() > 0) {
                        int height = layout.getLineBottom(layout.getLineCount() - 1);
                        int linkPosX = AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline);
                        if (
                            x >= linkPosX + layout.getLineLeft(0) && x <= linkPosX + layout.getLineWidth(0) &&
                            y >= linkY + offset && y <= linkY + offset + height
                        ) {
                            ok = true;
                            if (event.getAction() == MotionEvent.ACTION_DOWN) {

                                spoilerPressed = null;
                                if (linkSpoilers.get(a, null) != null) {
                                    for (SpoilerEffect eff : linkSpoilers.get(a)) {
                                        if (eff.getBounds().contains(x - linkPosX, y - linkY - offset)) {
                                            resetPressedLink();
                                            spoilerPressed = eff;
                                            spoilerTypePressed = SPOILER_TYPE_LINK;
                                            break;
                                        }
                                    }
                                }

                                if (spoilerPressed != null) {
                                    result = true;
                                } else {
                                    if (pressedLinkIndex != a || pressedLink == null || !linkPreviewPressed) {
                                        resetPressedLink();
                                        pressedLinkIndex = a;
                                        pressedLink = new LinkSpanDrawable(null, resourcesProvider, x - linkPosX, y - linkY - offset);
                                        LinkPath urlPath = pressedLink.obtainNewPath();
                                        linkPreviewPressed = true;
                                        linksCollector.addLink(pressedLink);
                                        startCheckLongPress();
                                        try {
                                            urlPath.setCurrentLayout(layout, 0, linkPosX, linkY + offset);
                                            layout.getSelectionPath(0, layout.getText().length(), urlPath);
                                        } catch (Exception e) {
                                            FileLog.e(e);
                                        }
                                    }
                                    result = true;
                                }
                            } else if (linkPreviewPressed) {
                                try {
                                    TLRPC.WebPage webPage = pressedLinkIndex == 0 && message.messageOwner.media != null ? message.messageOwner.media.webpage : null;
                                    if (webPage != null && webPage.embed_url != null && webPage.embed_url.length() != 0) {
                                        delegate.needOpenWebView(webPage, message);
                                    } else {
                                        delegate.onLinkPress(links.get(pressedLinkIndex).toString(), false);
                                    }
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                                resetPressedLink();
                                result = true;
                            } else if (spoilerPressed != null) {
                                startSpoilerRipples(x, y, offset);
                                result = true;
                            }
                            break;
                        }
                        offset += height;
                    }
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    int offX = AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline);
                    if (descriptionLayout != null && x >= offX && x <= offX + descriptionLayout.getWidth() && y >= descriptionY && y <= descriptionY + descriptionLayout.getHeight()) {
                        for (SpoilerEffect eff : descriptionLayoutSpoilers) {
                            if (eff.getBounds().contains(x - offX, y - descriptionY)) {
                                spoilerPressed = eff;
                                spoilerTypePressed = SPOILER_TYPE_DESCRIPTION;
                                ok = true;
                                result = true;
                                break;
                            }
                        }
                    }
                    if (descriptionLayout2 != null && x >= offX && x <= offX + descriptionLayout2.getWidth() && y >= description2Y && y <= description2Y + descriptionLayout2.getHeight()) {
                        for (SpoilerEffect eff : descriptionLayout2Spoilers) {
                            if (eff.getBounds().contains(x - offX, y - description2Y)) {
                                spoilerPressed = eff;
                                spoilerTypePressed = SPOILER_TYPE_DESCRIPTION2;
                                ok = true;
                                result = true;
                                break;
                            }
                        }
                    }
                } else if (event.getAction() == MotionEvent.ACTION_UP && spoilerPressed != null) {
                    startSpoilerRipples(x, y, 0);
                    ok = true;
                    result = true;
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

    private void startSpoilerRipples(int x, int y, int offset) {
        int linkPosX = AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline);
        resetPressedLink();
        SpoilerEffect eff = spoilerPressed;
        eff.setOnRippleEndCallback(() -> post(() -> {
            message.isSpoilersRevealed = true;
            linkSpoilers.clear();
            descriptionLayoutSpoilers.clear();
            descriptionLayout2Spoilers.clear();
            invalidate();
        }));

        int nx = x - linkPosX;
        float rad = (float) Math.sqrt(Math.pow(getWidth(), 2) + Math.pow(getHeight(), 2));
        float offY = 0;
        switch (spoilerTypePressed) {
            case SPOILER_TYPE_LINK:
                for (int i = 0; i < linkLayout.size(); i++) {
                    Layout lt = linkLayout.get(i);
                    offY += lt.getLineBottom(lt.getLineCount() - 1);
                    for (SpoilerEffect e : linkSpoilers.get(i)) {
                        e.startRipple(nx, y - getYOffsetForType(SPOILER_TYPE_LINK) - offset + offY, rad);
                    }
                }
                break;
            case SPOILER_TYPE_DESCRIPTION:
                for (SpoilerEffect sp : descriptionLayoutSpoilers)
                    sp.startRipple(nx, y - getYOffsetForType(SPOILER_TYPE_DESCRIPTION), rad);
                break;
            case SPOILER_TYPE_DESCRIPTION2:
                for (SpoilerEffect sp : descriptionLayout2Spoilers)
                    sp.startRipple(nx, y - getYOffsetForType(SPOILER_TYPE_DESCRIPTION2), rad);
                break;
        }
        for (int i = SPOILER_TYPE_LINK; i <= SPOILER_TYPE_DESCRIPTION2; i++) {
            if (i != spoilerTypePressed) {
                switch (i) {
                    case SPOILER_TYPE_LINK:
                        for (int j = 0; j < linkLayout.size(); j++) {
                            Layout lt = linkLayout.get(j);
                            offY += lt.getLineBottom(lt.getLineCount() - 1);
                            for (SpoilerEffect e : linkSpoilers.get(j)) {
                                e.startRipple(e.getBounds().centerX(), e.getBounds().centerY(), rad);
                            }
                        }
                        break;
                    case SPOILER_TYPE_DESCRIPTION:
                        for (SpoilerEffect sp : descriptionLayoutSpoilers)
                            sp.startRipple(sp.getBounds().centerX(), sp.getBounds().centerY(), rad);
                        break;
                    case SPOILER_TYPE_DESCRIPTION2:
                        for (SpoilerEffect sp : descriptionLayout2Spoilers)
                            sp.startRipple(sp.getBounds().centerX(), sp.getBounds().centerY(), rad);
                        break;
                }
            }
        }

        spoilerTypePressed = -1;
        spoilerPressed = null;
    }

    private int getYOffsetForType(int type) {
        switch (type) {
            default:
            case SPOILER_TYPE_LINK:
                return linkY;
            case SPOILER_TYPE_DESCRIPTION:
                return descriptionY;
            case SPOILER_TYPE_DESCRIPTION2:
                return description2Y;
        }
    }

    public String getLink(int num) {
        if (num < 0 || num >= links.size()) {
            return null;
        }
        return links.get(num).toString();
    }

    protected void resetPressedLink() {
        linksCollector.clear(true);
        pressedLinkIndex = -1;
        pressedLink = null;
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
        if (viewType == VIEW_TYPE_GLOBAL_SEARCH) {
            description2TextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3, resourcesProvider));
        }
        if (dateLayout != null) {
            canvas.save();
            canvas.translate(AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline) + (LocaleController.isRTL ? 0 : dateLayoutX), titleY);
            dateLayout.draw(canvas);
            canvas.restore();
        }
        if (titleLayout != null) {
            canvas.save();
            float x = AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline);
            if (LocaleController.isRTL) {
                x += dateLayout == null ? 0 : (dateLayout.getWidth() + AndroidUtilities.dp(4));
            }
            canvas.translate(x, titleY);
            titleLayout.draw(canvas);
            canvas.restore();
        }

        if (captionLayout != null) {
            captionTextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            canvas.save();
            canvas.translate(AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline), captionY);
            captionLayout.draw(canvas);
            canvas.restore();
        }
        if (descriptionLayout != null) {
            descriptionTextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            canvas.save();
            canvas.translate(AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline), descriptionY);
            SpoilerEffect.renderWithRipple(this, false, descriptionTextPaint.getColor(), -AndroidUtilities.dp(2), patchedDescriptionLayout, 0, descriptionLayout, descriptionLayoutSpoilers, canvas, false);
            canvas.restore();
        }

        if (descriptionLayout2 != null) {
            descriptionTextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            canvas.save();
            canvas.translate(AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline), description2Y);
            SpoilerEffect.renderWithRipple(this, false, descriptionTextPaint.getColor(), -AndroidUtilities.dp(2), patchedDescriptionLayout2, 0, descriptionLayout2, descriptionLayout2Spoilers, canvas, false);
            canvas.restore();
        }

        if (!linkLayout.isEmpty()) {
            descriptionTextPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteLinkText, resourcesProvider));
            int offset = 0;
            for (int a = 0; a < linkLayout.size(); a++) {
                StaticLayout layout = linkLayout.get(a);
                List<SpoilerEffect> spoilers = linkSpoilers.get(a);
                if (layout.getLineCount() > 0) {
                    canvas.save();
                    canvas.translate(AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline), linkY + offset);

                    path.rewind();
                    if (spoilers != null) {
                        for (SpoilerEffect eff : spoilers) {
                            Rect b = eff.getBounds();
                            path.addRect(b.left, b.top, b.right, b.bottom, Path.Direction.CW);
                        }
                    }
                    canvas.save();
                    canvas.clipPath(path, Region.Op.DIFFERENCE);
                    layout.draw(canvas);
                    canvas.restore();

                    canvas.save();
                    canvas.clipPath(path);
                    path.rewind();
                    if (spoilers != null && !spoilers.isEmpty())
                        spoilers.get(0).getRipplePath(path);
                    canvas.clipPath(path);
                    layout.draw(canvas);
                    canvas.restore();

                    if (spoilers != null)
                        for (SpoilerEffect eff : spoilers) eff.draw(canvas);

                    canvas.restore();
                    offset += layout.getLineBottom(layout.getLineCount() - 1);
                }
            }

            if (linksCollector.draw(canvas)) {
                invalidate();
            }
        }

        if (fromInfoLayout != null) {
            canvas.save();
            canvas.translate(AndroidUtilities.dp(LocaleController.isRTL ? 8 : AndroidUtilities.leftBaseline), fromInfoLayoutY );
            fromInfoLayout.draw(canvas);
            AnimatedEmojiSpan.drawAnimatedEmojis(canvas, fromInfoLayout, fromInfoLayoutEmojis, 0, null, 0, 0, 0, 1f);
            canvas.restore();
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

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        StringBuilder sb = new StringBuilder();
        if (titleLayout != null) {
            sb.append(titleLayout.getText());
        }
        if (descriptionLayout != null) {
            sb.append(", ");
            sb.append(descriptionLayout.getText());
        }
        if (descriptionLayout2 != null) {
            sb.append(", ");
            sb.append(descriptionLayout2.getText());
        }
        info.setText(sb.toString());
        if (checkBox.isChecked()) {
            info.setChecked(true);
            info.setCheckable(true);
        }
    }
}
