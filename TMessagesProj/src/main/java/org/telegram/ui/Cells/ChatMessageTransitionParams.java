/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.graphics.Rect;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextUtils;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.RichMessageLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.EmptyStubSpan;

import java.util.ArrayList;
import java.util.HashSet;

public class ChatMessageTransitionParams {

    private final ChatMessageCell cell;

    public ChatMessageTransitionParams(ChatMessageCell cell) {
        this.cell = cell;
    }

    public float lastDrawingImageX, lastDrawingImageY, lastDrawingImageW, lastDrawingImageH;
    public float lastDrawingCaptionX, lastDrawingCaptionY;
    public boolean animateChange;
    public int animateFromRepliesTextWidth;
    public boolean messageEntering;
    public boolean animateLocationIsExpired;
    public boolean lastLocatinIsExpired;
    public String lastDrawLocationExpireText;
    public float lastDrawLocationExpireProgress;
    public StaticLayout lastDrawDocTitleLayout;
    public StaticLayout lastDrawInfoLayout;
    public boolean updatePhotoImageX;
    public boolean animateRoundVideoDotY;
    public float lastDrawRoundVideoDotY;
    public float animateFromRoundVideoDotY;
    public boolean animateReplyY;
    public float lastDrawReplyY;
    public float animateFromReplyY;
    public boolean lastDrawingSmallImage;
    public int lastDrawnMonoforumPadding;
    public int lastDrawnStarsPriceTopPadding;
    public int lastDrawnBottomActionPadding;
    public boolean lastDrawnStarsPriceText;
    public boolean lastDrawnBottomActionText;

    public boolean lastIsPinned;
    boolean animatePinned;
    public float lastTimeXPinned;
    public float animateFromTimeXPinned;

    public int lastRepliesCount;
    boolean animateReplies;
    public StaticLayout lastRepliesLayout;
    StaticLayout animateRepliesLayout;
    float animateFromTimeXReplies;
    float lastTimeXReplies;

    float animateFromTimeXViews;
    float lastTimeXViews;

    public int lastCommentsCount;
    public int lastTotalCommentWidth;
    public int lastCommentArrowX;
    public int lastCommentUnreadX;
    public boolean lastCommentDrawUnread;
    public float lastCommentX;
    public boolean lastDrawCommentNumber;
    public StaticLayout lastCommentLayout;
    boolean animateComments;
    StaticLayout animateCommentsLayout;
    float animateCommentX;
    int animateTotalCommentWidth;
    int animateCommentArrowX;
    int animateCommentUnreadX;
    boolean animateCommentDrawUnread;
    boolean animateDrawCommentNumber;

    boolean animateSign;
    float animateNameX;
    public String lastSignMessage;

    public boolean imageChangeBoundsTransition;
    public float deltaLeft;
    public float deltaRight;
    public float deltaBottom;
    public float deltaTop;

    // in animation, describe to what values deltaLeft and deltaRight moves to
    public float toDeltaLeft, toDeltaRight;

    public float animateToImageX, animateToImageY, animateToImageW, animateToImageH;
    public float captionFromX, captionFromY;
    boolean moveCaption;

    public int[] imageRoundRadius = new int[4];
    public float captionEnterProgress = 1f;

    public boolean wasDraw;
    public boolean animateBackgroundBoundsInner;
    public boolean animateBackgroundWidth;
    public boolean ignoreAlpha;
    public boolean drawPinnedBottomBackground;
    public float changePinnedBottomProgress = 1f;
    public int[] animateToRadius;
    public boolean animateRadius;
    public boolean transformGroupToSingleMessage;
    public Rect lastDrawingBackgroundRect = new Rect();
    public boolean lastDrawingSideMenuEnabled;
    public boolean animateDrawingSideMenuEnabled;

    public boolean animateMessageText;
    ArrayList<MessageObject.TextLayoutBlock> animateOutTextBlocks;
    public ArrayList<MessageObject.TextLayoutBlock> lastDrawingTextBlocks;
    private int lastDrawingTextWidth;

    public boolean animateRichLayout;
    RichMessageLayout animateOutRichLayout;
    public RichMessageLayout lastDrawingRichLayout;

    float animateOutTextXOffset;
    AnimatedEmojiSpan.EmojiGroupedSpans animateOutAnimateEmoji;

    public float lastDrawingReplyTextHeight;
    float animateFromReplyTextHeight;
    StaticLayout animateReplyTextLayout;
    AnimatedEmojiSpan.EmojiGroupedSpans animateOutAnimateEmojiReply;

    boolean animateEditedEnter;
    StaticLayout animateEditedLayout;
    StaticLayout animateTimeLayout;
    int animateTimeWidth;
    public int lastTimeWidth;
    int animateEditedWidthDiff;
    public boolean lastDrawingEdited;

    boolean animateReplaceCaptionLayout;
    MessageObject.TextLayoutBlocks animateOutCaptionLayout;
    public MessageObject.TextLayoutBlocks lastDrawingCaptionLayout;
    public boolean lastDrawingSummarized;
    public boolean lastDrawTime;
    public int lastTimeX;
    public int animateFromTimeX;
    public boolean shouldAnimateTimeX;
    public int lastBackgroundLeft, lastBackgroundRight;
    public boolean lastDrawBackground;
    public boolean animateDrawBackground;
    public boolean lastUseTranscribeButton;
    public boolean animateUseTranscribeButton;

    public float photoImageFromWidth, photoImageFromHeight;
    public float photoImageFromCenterX, photoImageFromCenterY;
    public boolean animateSmallImage;

    public int animateMonoforumPaddingFrom;
    public boolean animateMonoforumPadding;
    public boolean needsStopClipping;

    public boolean animateStarsPriceText;
    public int animateStarsPriceTopPaddingFrom;
    public boolean animateStarsPriceTopPadding;

    public boolean animateBottomActionText;
    public int animateBottomActionPaddingFrom;
    public boolean animateBottomActionPadding;

    public boolean lastDrawingLinkAbove;
    public boolean animateLinkAbove;
    public boolean lastDrawingMediaAbove;
    public boolean animateMediaAbove;

    public int lastDrawingLinkPreviewHeight;
    public int animateFromLinkPreviewHeight;
    public boolean animateLinkPreviewHeight;

    public boolean animateDrawingTimeAlpha;

    public float animateChangeProgress = 1f;
    private ArrayList<BotButton> lastDrawBotButtons = new ArrayList<>();
    ArrayList<BotButton> transitionBotButtons = new ArrayList<>();
    public boolean animateWidthForButton;
    public int animateFromWidthForButton;
    public int lastDrawnWidthForButtons;

    public final float crossfadeProgressK = 0.5f;
    public float oldProgress = 0.0f, newProgress = 1.0f;

    void updateCrossfadeProgress() {
        final float progress = Math.max(0.0f, Math.min(1.0f, animateChangeProgress));
        oldProgress = (float) Math.pow(1.0f - progress, crossfadeProgressK);
        newProgress = (float) Math.pow(progress, crossfadeProgressK);
    }

    public float lastButtonX;
    public float lastButtonY;
    float animateFromButtonX;
    float animateFromButtonY;
    boolean animateButton;

    public int lastMediaOffsetY;
    int animateFromMediaOffsetY;
    boolean animateMediaOffsetY;

    public int lastStatusDrawableParams = -1;

    public int lastViewsCount;
    public StaticLayout lastViewsLayout;
    StaticLayout animateViewsLayout;

    public boolean lastShouldDrawTimeOnMedia;
    boolean animateShouldDrawTimeOnMedia;
    public boolean lastShouldDrawMenuDrawable;
    boolean animateShouldDrawMenuDrawable;
    public StaticLayout lastTimeLayout;
    public boolean lastIsPlayingRound;
    public boolean animatePlayingRound;
    public boolean animateTextY;

    public float lastDrawingTextY;
    public float lastDrawingTextX;

    public float animateFromTextY;
    public float lastTextXOffset;

    public int lastDrawingLinkPreviewY;
    public int animateFromLinkPreviewY;
    public boolean animateLinkPreviewY;

    public int lastTopOffset;
    public boolean animateForwardedLayout;
    public int animateForwardedNamesOffset;
    public int lastForwardedNamesOffset;
    public boolean lastDrawnForwardedName;
    public StaticLayout[] lastDrawnForwardedNameLayout = new StaticLayout[2];
    public StaticLayout[] animatingForwardedNameLayout = new StaticLayout[2];
    float animateForwardNameX;
    public float lastForwardNameX;
    int animateForwardNameWidth;
    public int lastForwardNameWidth;
    boolean animateBotButtonsChanged;
    public StaticLayout lastDrawnReplyTextLayout;

    public boolean animateNamesOffset;
    public int animateNamesOffsetFrom;
    public int lastNamesOffset;

    public int lastReplyTextXOffset;
    public float animateReplyTextOffset;

    public boolean lastDrawingRecommendationsExpanded;
    public boolean animateRecommendationsExpanded;
    public boolean animateFromRecommendationsExpanded;

    public boolean lastDrawNameLayout;
    public boolean lastDrawAvatar;
    public boolean lastDrawTopic;
    public boolean animateDrawNameLayout;
    public boolean animateDrawAvatar;
    public boolean animateDrawTopic;

    public boolean lastDrawingFactCheck;
    public boolean animateFactCheck;
    public int lastDrawingFactCheckHeight;
    public int animateFactCheckHeightFrom;

    public boolean animatePollAddOptionHeight;
    public int lastDrawingPollAddOptionHeight;
    public int animatePollAddOptionHeightFrom;

    public boolean animateFactCheckHeight;
    public boolean lastDrawingFactCheckExpanded;
    public boolean animateFactCheckExpanded;

    public boolean lastDrawingSummaryReply;
    public boolean animateSummaryReply;

    public HashSet<Integer> lastDrawingExpandedQuotes;
    public HashSet<Integer> animateExpandedQuotesFrom;
    public boolean animateExpandedQuotes;

    public boolean lastDrawingExpandedExplanation;
    public boolean expandedExplanationFrom;
    public boolean animateExpandedExplanation;

    public boolean lastDrawnTranslated;
    public StaticLayout lastDrawnTitleLayout;
    public StaticLayout animateTitleLayout;
    AnimatedEmojiSpan.EmojiGroupedSpans animateTitleLayoutEmoji;

    public void recordDrawingState() {
        wasDraw = true;
        lastDrawingImageX = cell.photoImage.getImageX();
        lastDrawingImageY = cell.photoImage.getImageY();
        lastDrawingImageW = cell.photoImage.getImageWidth();
        lastDrawingImageH = cell.photoImage.getImageHeight();
        int[] r = cell.photoImage.getRoundRadius();
        System.arraycopy(r, 0, imageRoundRadius, 0, 4);
        if (cell.currentBackgroundDrawable != null) {
            lastDrawingBackgroundRect.set(cell.currentBackgroundDrawable.getBounds());
        }
        lastDrawingSideMenuEnabled = cell.isSideMenuEnabled;
        lastDrawingTextBlocks = cell.currentMessageObject != null ? cell.currentMessageObject.textLayoutBlocks : null;
        lastDrawingTextWidth = cell.currentMessageObject != null ? cell.currentMessageObject.textWidth : 0;
        lastDrawingEdited = cell.edited;
        lastDrawingRichLayout = cell.currentMessageObject != null ? cell.currentMessageObject.richLayout : null;

        lastDrawingCaptionX = cell.captionX;
        lastDrawingCaptionY = cell.captionY;

        lastDrawingCaptionLayout = cell.captionLayout;
        lastDrawingSummarized = cell.currentMessageObject != null ? cell.currentMessageObject.summarized : false;
        lastDrawBotButtons.clear();
        if (!cell.botButtons.isEmpty()) {
            lastDrawBotButtons.addAll(cell.botButtons);
        }
        lastDrawingSmallImage = cell.isSmallImage;
        lastDrawnMonoforumPadding = cell.topicSeparatorTopPadding;
        lastDrawnStarsPriceTopPadding = cell.starsPriceTopPadding;
        lastDrawnBottomActionPadding = cell.bottomActionPadding;
        lastDrawnStarsPriceText = cell.starsPriceText != null;
        lastDrawnBottomActionText = cell.bottomActionText != null;
        lastDrawingLinkPreviewHeight = cell.linkPreviewHeight;
        lastDrawingLinkAbove = cell.linkPreviewAbove;
        lastDrawingMediaAbove = cell.captionAbove;

        lastDrawingRecommendationsExpanded = cell.currentMessageObject != null && cell.currentMessageObject.type == MessageObject.TYPE_JOINED_CHANNEL && cell.channelRecommendationsCell != null && cell.channelRecommendationsCell.isExpanded();

        if (cell.commentLayout != null) {
            lastCommentsCount = cell.getRepliesCount();
            lastTotalCommentWidth = cell.totalCommentWidth;
            lastCommentLayout = cell.commentLayout;
            lastCommentArrowX = cell.commentArrowX;
            lastCommentUnreadX = cell.commentUnreadX;
            lastCommentDrawUnread = cell.commentDrawUnread;
            lastCommentX = cell.commentX;
            lastDrawCommentNumber = cell.drawCommentNumber;
        }

        lastRepliesCount = cell.getRepliesCount();
        this.lastViewsCount = cell.getMessageObject().messageOwner.views;
        lastRepliesLayout = cell.repliesLayout;
        lastViewsLayout = cell.viewsLayout;

        lastIsPinned = cell.isPinned;

        lastSignMessage = cell.lastPostAuthor;

        lastDrawBackground = cell.drawBackground;
        lastUseTranscribeButton = cell.useTranscribeButton;

        lastButtonX = cell.buttonX;
        lastButtonY = cell.buttonY;
        lastMediaOffsetY = cell.mediaOffsetY;

        lastDrawTime = !cell.forceNotDrawTime;
        lastTimeX = cell.timeX;
        lastTimeLayout = cell.timeLayout;
        lastTimeWidth = cell.timeWidth;

        lastShouldDrawTimeOnMedia = cell.shouldDrawTimeOnMedia();
        lastTopOffset = cell.getTopMediaOffset();
        lastShouldDrawMenuDrawable = cell.shouldDrawMenuDrawable();

        lastLocatinIsExpired = cell.locationExpired;
        lastIsPlayingRound = cell.isPlayingRound;

        lastDrawingTextY = cell.textY;
        lastDrawingTextX = cell.textX;
        lastDrawingLinkPreviewY = cell.linkPreviewY;

        lastDrawnWidthForButtons = cell.widthForButtons;
        lastDrawnForwardedNameLayout[0] = cell.forwardedNameLayout[0];
        lastDrawnForwardedNameLayout[1] = cell.forwardedNameLayout[1];
        lastDrawnForwardedName = cell.currentMessageObject != null && cell.currentMessageObject.needDrawForwarded();
        lastForwardNameX = cell.forwardNameX;
        lastNamesOffset = lastForwardedNamesOffset = cell.namesOffset;
        lastForwardNameWidth = cell.forwardedNameWidth;
        lastBackgroundLeft = cell.getCurrentBackgroundLeft();
        if (cell.currentBackgroundDrawable != null) {
            lastBackgroundRight = cell.currentBackgroundDrawable.getBounds().right;
        }
        lastTextXOffset = cell.currentMessageObject != null ? cell.currentMessageObject.textXOffset : 0;

        lastDrawingReplyTextHeight = cell.replyTextHeight;
        lastDrawnReplyTextLayout = cell.replyTextLayout;
        lastReplyTextXOffset = cell.replyTextOffset;

        cell.reactionsLayoutInBubble.recordDrawingState();
        if (cell.replyNameLayout != null) {
            lastDrawReplyY = cell.replyStartY;
        } else {
            lastDrawReplyY = 0;
        }

        lastDrawNameLayout = cell.drawNameLayout;
        lastDrawAvatar = cell.drawNameAvatar;
        lastDrawTopic = cell.drawTopic;

        lastDrawingFactCheckHeight = cell.factCheck != null ? cell.factCheck.contentHeight : 0;
        lastDrawingFactCheckExpanded = cell.getPrimaryMessageObject() != null && cell.getPrimaryMessageObject().factCheckExpanded;
        lastDrawingFactCheck = cell.hasFactCheck();
        lastDrawingPollAddOptionHeight = cell.pollAddButtonHeight;

        lastDrawingSummaryReply = cell.drawSummaryReply;

        lastDrawingExpandedQuotes = cell.getPrimaryMessageObject() != null ? cell.getPrimaryMessageObject().expandedQuotes : null;
        lastDrawingExpandedExplanation = cell.currentMessageObject != null && cell.currentMessageObject.expandedExplanation;

        lastDrawnTranslated = cell.currentMessageObject != null && cell.currentMessageObject.translated;
        lastDrawnTitleLayout = cell.titleLayout;
    }

    public void recordDrawingStatePreview() {
        lastDrawnForwardedNameLayout[0] = cell.forwardedNameLayout[0];
        lastDrawnForwardedNameLayout[1] = cell.forwardedNameLayout[1];
        lastDrawnForwardedName = cell.currentMessageObject.needDrawForwarded();
        lastForwardNameX = cell.forwardNameX;
        lastNamesOffset = lastForwardedNamesOffset = cell.namesOffset;
        lastForwardNameWidth = cell.forwardedNameWidth;
    }

    public boolean animateChange() {
        if (!wasDraw) {
            return false;
        }
        boolean changed = false;

        needsStopClipping = false;
        animateDrawingSideMenuEnabled = false;
        if (lastDrawingSideMenuEnabled != cell.isSideMenuEnabled) {
            animateDrawingSideMenuEnabled = true;
            changed = true;
        }

        animateMessageText = false;
        if (cell.currentMessageObject.textLayoutBlocks != lastDrawingTextBlocks) {
            boolean sameText = true;
            if (cell.currentMessageObject.textWidth != lastDrawingTextWidth && lastDrawingSideMenuEnabled != cell.isSideMenuEnabled) {
                sameText = false;
            }
            if (cell.currentMessageObject.textLayoutBlocks != null && lastDrawingTextBlocks != null && cell.currentMessageObject.textLayoutBlocks.size() == lastDrawingTextBlocks.size()) {
                for (int i = 0; i < lastDrawingTextBlocks.size(); i++) {
                    String newText = cell.currentMessageObject.textLayoutBlocks.get(i).textLayout == null ? null : cell.currentMessageObject.textLayoutBlocks.get(i).textLayout.getText().toString();
                    String oldText = lastDrawingTextBlocks.get(i).textLayout == null ? null : lastDrawingTextBlocks.get(i).textLayout.getText().toString();
                    if ((newText == null && oldText != null) || (newText != null && oldText == null) || !newText.equals(oldText)) {
                        sameText = false;
                        break;
                    } else {
                        if (cell.animatedEmojiStack != null) {
                            cell.animatedEmojiStack.replaceLayout(cell.currentMessageObject.textLayoutBlocks.get(i).textLayout, lastDrawingTextBlocks.get(i).textLayout);
                        }
                    }
                }
            } else {
                sameText = false;
            }
            if (!sameText) {
                animateMessageText = true;
                animateOutTextBlocks = lastDrawingTextBlocks;
                animateOutTextXOffset = lastTextXOffset;
                animateOutAnimateEmoji = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, cell, animateOutAnimateEmoji, lastDrawingTextBlocks, true);
                cell.animatedEmojiStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, cell, cell.animatedEmojiStack, cell.currentMessageObject.textLayoutBlocks);
                changed = true;
            } else {
                cell.animatedEmojiStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, cell, cell.animatedEmojiStack, cell.currentMessageObject.textLayoutBlocks);
            }
        }

        if (cell.currentMessageObject.richLayout != null && (cell.currentMessageObject.richLayout.detailsAnimating || cell.currentMessageObject.richLayout.blockquoteAnimating)) {
            needsStopClipping = true;
            changed = true;
        }

        animateRichLayout = false;
        if (cell.currentMessageObject.richLayout != lastDrawingRichLayout) {
            final boolean streaming = cell.botDraftTypingAnimator != null && cell.botDraftTypingAnimator.isRunning();
            if (animateOutRichLayout != null) {
                animateOutRichLayout.detach(cell);
                animateOutRichLayout = null;
            }
            if (streaming) {
                if (cell.currentMessageObject.richLayout != null && lastDrawingRichLayout != null) {
                    cell.currentMessageObject.richLayout.layout(lastDrawingRichLayout);
                }
            } else if (cell.currentMessageObject.richLayout != null && cell.currentMessageObject.richLayout.isSameWidget(lastDrawingRichLayout)) {
                // The same widget in a new copy of the message: the new block shows its frame at once, while a
                // crossfade of two blocks drawing one picture dips through the bubble.
            } else {
                animateRichLayout = true;
                animateOutRichLayout = lastDrawingRichLayout;
                if (animateOutRichLayout != null) {
                    animateOutRichLayout.attach(cell);
                }
            }
        }

        animateDrawNameLayout = false;
        if (cell.drawNameLayout != lastDrawNameLayout) {
            animateDrawNameLayout = true;
            changed = true;
        }
        animateDrawAvatar = false;
        if (cell.drawNameAvatar != lastDrawAvatar) {
            animateDrawAvatar = true;
            changed = true;
        }
        animateDrawTopic = false;
        if (cell.drawTopic != lastDrawTopic) {
            animateDrawTopic = true;
            changed = true;
        }
        if (cell.replyTextLayout != lastDrawnReplyTextLayout) {
            CharSequence newText = cell.replyTextLayout != null ? cell.replyTextLayout.getText() : null;
            CharSequence oldText = lastDrawnReplyTextLayout != null ? lastDrawnReplyTextLayout.getText() : null;
            if (!TextUtils.equals(newText, oldText)) {
                animateFromReplyTextHeight = lastDrawingReplyTextHeight;
                animateReplyTextLayout = lastDrawnReplyTextLayout;
                animateReplyTextOffset = lastReplyTextXOffset;
                animateOutAnimateEmojiReply = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, cell, false, animateOutAnimateEmojiReply, true, lastDrawnReplyTextLayout);
                changed = true;
            }
        }
        if (cell.edited && !lastDrawingEdited && cell.timeLayout != null) {
            String editedStr = getString("EditedMessage", R.string.EditedMessage);
            CharSequence text = cell.timeLayout.getText();
            int i = text.toString().indexOf(editedStr);
            if (i >= 0) {
                if (i == 0) {
                    animateEditedLayout = new StaticLayout(editedStr, Theme.chat_timePaint, cell.timeTextWidth + dp(100), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
                    spannableStringBuilder.append(editedStr);
                    spannableStringBuilder.append(text.subSequence(editedStr.length(), text.length()));
                    spannableStringBuilder.setSpan(new EmptyStubSpan(), 0, editedStr.length(), 0);
                    animateTimeLayout = new StaticLayout(spannableStringBuilder, Theme.chat_timePaint, cell.timeTextWidth + dp(100), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    animateEditedWidthDiff = cell.timeWidth - lastTimeWidth;
                } else {
                    animateEditedWidthDiff = 0;
                    animateEditedLayout = null;
                    animateTimeLayout = lastTimeLayout;
                }
                animateEditedEnter = true;
                animateTimeWidth = lastTimeWidth;
                animateFromTimeX = lastTimeX;
                shouldAnimateTimeX = true;
                changed = true;
            }
            cell.accessibilityText = null;
        } else if (!cell.edited && lastDrawingEdited && cell.timeLayout != null) {
            animateTimeLayout = lastTimeLayout;
            animateEditedWidthDiff = cell.timeWidth - lastTimeWidth;
            animateEditedEnter = true;
            animateTimeWidth = lastTimeWidth;
            animateFromTimeX = lastTimeX;
            shouldAnimateTimeX = true;
            changed = true;
        }

        animateDrawBackground = false;
        if (cell.drawBackground != lastDrawBackground) {
            animateDrawBackground = true;
            changed = true;
        }

        animateSmallImage = false;
        if (cell.isSmallImage != lastDrawingSmallImage) {
            animateSmallImage = true;
            photoImageFromWidth = lastDrawingImageW;
            photoImageFromHeight = lastDrawingImageH;
            photoImageFromCenterX = lastDrawingImageX + lastDrawingImageW / 2f;
            photoImageFromCenterY = lastDrawingImageY + lastDrawingImageH / 2f;
            changed = true;
        }
        animateMonoforumPadding = false;
        if (cell.topicSeparatorTopPadding != lastDrawnMonoforumPadding) {
            needsStopClipping = true;
            animateMonoforumPaddingFrom = lastDrawnMonoforumPadding;
            animateMonoforumPadding = true;
            changed = true;
        }

        animateStarsPriceTopPadding = false;
        if (cell.starsPriceTopPadding != lastDrawnStarsPriceTopPadding) {
            animateStarsPriceTopPaddingFrom = lastDrawnStarsPriceTopPadding;
            animateStarsPriceTopPadding = true;
            changed = true;
        }

        animateStarsPriceText = false;
        if ((cell.starsPriceText != null) != lastDrawnStarsPriceText) {
            animateStarsPriceText = true;
            changed = true;
        }

        animateBottomActionPadding = false;
        if (cell.bottomActionPadding != lastDrawnBottomActionPadding) {
            animateBottomActionPaddingFrom = lastDrawnBottomActionPadding;
            animateBottomActionPadding = true;
            changed = true;
        }

        animateBottomActionText = false;
        if ((cell.bottomActionText != null) != lastDrawnBottomActionText) {
            animateBottomActionText = true;
            changed = true;
        }

        animateRecommendationsExpanded = false;
        final boolean channelsExpanded = cell.currentMessageObject.type == MessageObject.TYPE_JOINED_CHANNEL && cell.channelRecommendationsCell != null && cell.channelRecommendationsCell.isExpanded();
        if (channelsExpanded != lastDrawingRecommendationsExpanded) {
            animateRecommendationsExpanded = true;
            animateFromRecommendationsExpanded = lastDrawingRecommendationsExpanded;
            changed = true;
        }

        animateLinkAbove = false;
        if (cell.linkPreviewAbove != lastDrawingLinkAbove) {
            animateLinkAbove = true;
            changed = true;
        }

        animateMediaAbove = false;
        if (cell.captionAbove != lastDrawingMediaAbove) {
            animateMediaAbove = true;
            changed = true;
        }

        animateLinkPreviewHeight = false;
        if (cell.hasLinkPreview && cell.linkPreviewHeight != lastDrawingLinkPreviewHeight) {
            animateLinkPreviewHeight = true;
            animateFromLinkPreviewHeight = lastDrawingLinkPreviewHeight;
            changed = true;
        }

        animateUseTranscribeButton = false;
        if (cell.useTranscribeButton != lastUseTranscribeButton) {
            animateUseTranscribeButton = true;
            changed = true;
        }

        boolean summarized = cell.currentMessageObject != null ? cell.currentMessageObject.summarized : false;
        if (cell.captionLayout != lastDrawingCaptionLayout) {
            String oldCaption = lastDrawingCaptionLayout == null ? null : lastDrawingCaptionLayout.text.toString();
            String currentCaption = cell.captionLayout == null ? null : cell.captionLayout.text.toString();
            if (
                (lastDrawingSideMenuEnabled != cell.isSideMenuEnabled || lastDrawingSummarized != summarized) && (cell.captionLayout == null ? 0 : cell.captionLayout.textWidth) != (lastDrawingCaptionLayout == null ? 0 : lastDrawingCaptionLayout.textWidth) ||
                (currentCaption == null) != (oldCaption == null) ||
                (oldCaption != null && !oldCaption.equals(currentCaption))
            ) {
                animateReplaceCaptionLayout = true;
                animateOutCaptionLayout = lastDrawingCaptionLayout;
                animateOutAnimateEmoji = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, cell, null, animateOutCaptionLayout == null ? null : animateOutCaptionLayout.textLayoutBlocks);
                cell.animatedEmojiStack = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, cell, cell.animatedEmojiStack, cell.captionLayout == null ? null : cell.captionLayout.textLayoutBlocks);
                if (lastDrawingSideMenuEnabled != cell.isSideMenuEnabled || lastDrawingSummarized != summarized) {
                    moveCaption = true;
                    captionFromX = lastDrawingCaptionX;
                    captionFromY = lastDrawingCaptionY;
                }
                changed = true;
            } else {
                cell.updateCaptionLayout();
                if (lastDrawingCaptionX != cell.captionX || lastDrawingCaptionY != cell.captionY) {
                    moveCaption = true;
                    captionFromX = lastDrawingCaptionX;
                    captionFromY = lastDrawingCaptionY;
                    changed = true;
                }
            }
        } else if (cell.captionLayout != null && lastDrawingCaptionLayout != null) {
            cell.updateCaptionLayout();
            if (lastDrawingCaptionX != cell.captionX || lastDrawingCaptionY != cell.captionY) {
                moveCaption = true;
                captionFromX = lastDrawingCaptionX;
                captionFromY = lastDrawingCaptionY;
                changed = true;
            }
        }
        if (!lastDrawBotButtons.isEmpty() || !cell.botButtons.isEmpty()) {
            if (lastDrawBotButtons.size() != cell.botButtons.size()) {
                animateBotButtonsChanged = true;
            }
            if (!animateBotButtonsChanged) {
                for (int i = 0; i < cell.botButtons.size(); i++) {
                    BotButton button1 = cell.botButtons.get(i);
                    BotButton button2 = lastDrawBotButtons.get(i);
                    if (button1.isSeparator || button2.isSeparator) {
                        continue;
                    }

                    if (Math.abs(button1.x - button2.x) > 0.01f || Math.abs(button1.width - button2.width) > 0.01f || !TextUtils.equals(button1.title.getText(), button2.title.getText())) {
                        animateBotButtonsChanged = true;
                        break;
                    }
                }
            }
            if (animateBotButtonsChanged) {
                transitionBotButtons.addAll(lastDrawBotButtons);
            }
        }
        if (lastDrawnWidthForButtons != cell.widthForButtons) {
            animateFromWidthForButton = lastDrawnWidthForButtons;
            animateWidthForButton = true;
        }

        if (cell.documentAttachType == ChatMessageCell.DOCUMENT_ATTACH_TYPE_MUSIC || cell.documentAttachType == ChatMessageCell.DOCUMENT_ATTACH_TYPE_AUDIO || cell.documentAttachType == ChatMessageCell.DOCUMENT_ATTACH_TYPE_DOCUMENT) {
            if (cell.buttonX != lastButtonX || cell.buttonY != lastButtonY) {
                animateFromButtonX = lastButtonX;
                animateFromButtonY = lastButtonY;
                animateButton = true;
                changed = true;
            }
        }
        
        if (cell.mediaOffsetY != lastMediaOffsetY) {
            animateFromMediaOffsetY = lastMediaOffsetY;
            animateMediaOffsetY = true;
            changed = true;
        }

        boolean timeDrawablesIsChanged = false;

        if (lastIsPinned != cell.isPinned) {
            animatePinned = true;
            changed = true;
            timeDrawablesIsChanged = true;
            cell.accessibilityText = null;
        }

        if ((lastRepliesLayout != null || cell.repliesLayout != null) && lastRepliesCount != cell.getRepliesCount()) {
            animateRepliesLayout = lastRepliesLayout;
            animateReplies = true;
            changed = true;
            timeDrawablesIsChanged = true;
            cell.accessibilityText = null;
        }

        if (lastViewsLayout != null && this.lastViewsCount != cell.getMessageObject().messageOwner.views) {
            animateViewsLayout = lastViewsLayout;
            changed = true;
            timeDrawablesIsChanged = true;
            cell.accessibilityText = null;
        }

        if (cell.commentLayout != null && lastCommentsCount != cell.getRepliesCount()) {
            if (lastCommentLayout != null && !TextUtils.equals(lastCommentLayout.getText(), cell.commentLayout.getText())) {
                animateCommentsLayout = lastCommentLayout;
            } else {
                animateCommentsLayout = null;
            }
            animateTotalCommentWidth = lastTotalCommentWidth;
            animateCommentX = lastCommentX;
            animateCommentArrowX = lastCommentArrowX;
            animateCommentUnreadX = lastCommentUnreadX;
            animateCommentDrawUnread = lastCommentDrawUnread;
            animateDrawCommentNumber = lastDrawCommentNumber;
            animateComments = true;
            changed = true;
        }

        if (!TextUtils.equals(lastSignMessage, cell.lastPostAuthor)) {
            animateSign = true;
            animateNameX = cell.nameX;
            changed = true;
        }

        if (lastDrawTime == cell.forceNotDrawTime) {
            animateDrawingTimeAlpha = true;
            animateViewsLayout = null;
            changed = true;
        } else if (lastShouldDrawTimeOnMedia != cell.shouldDrawTimeOnMedia()) {
            animateEditedEnter = false;
            animateShouldDrawTimeOnMedia = true;
            animateFromTimeX = lastTimeX;
            animateTimeLayout = lastTimeLayout;
            animateTimeWidth = lastTimeWidth;
            changed = true;
        } else if (timeDrawablesIsChanged || Math.abs(cell.timeX - lastTimeX) > 1 && lastDrawingSideMenuEnabled == cell.isSideMenuEnabled) {
            shouldAnimateTimeX = true;
            animateTimeWidth = lastTimeWidth;
            animateFromTimeX = lastTimeX;
            animateFromTimeXViews = lastTimeXViews;
            animateFromTimeXReplies = lastTimeXReplies;
            animateFromTimeXPinned = lastTimeXPinned;
        }

        if (lastShouldDrawMenuDrawable != cell.shouldDrawMenuDrawable()) {
            animateShouldDrawMenuDrawable = true;
        }

        if (lastLocatinIsExpired != cell.locationExpired) {
            animateLocationIsExpired = true;
        }

        if (lastIsPlayingRound != cell.isPlayingRound) {
            animatePlayingRound = true;
            changed = true;
        }

        if (lastDrawingTextY != cell.textY) {
            animateTextY = true;
            animateFromTextY = lastDrawingTextY;
            changed = true;
        }

        if (lastDrawingLinkPreviewY != cell.linkPreviewY) {
            animateLinkPreviewY = true;
            animateFromLinkPreviewY = lastDrawingLinkPreviewY;
            changed = true;
        }

        if (lastDrawingPollAddOptionHeight != cell.pollAddButtonHeight) {
            animatePollAddOptionHeight = true;
            animatePollAddOptionHeightFrom = lastDrawingPollAddOptionHeight;
            changed = true;
        }

        if (lastDrawingFactCheckHeight != (cell.factCheck != null ? cell.factCheck.contentHeight : 0)) {
            animateFactCheckHeight = true;
            animateFactCheckHeightFrom = lastDrawingFactCheckHeight;
            changed = true;
        }
        if (lastDrawingFactCheckExpanded != (cell.getPrimaryMessageObject() != null && cell.getPrimaryMessageObject().factCheckExpanded)) {
            animateFactCheckExpanded = true;
            changed = true;
        }
        if (lastDrawingFactCheck != cell.hasFactCheck()) {
            animateFactCheck = true;
            changed = true;
        }
        if (lastDrawingSummaryReply != cell.drawSummaryReply) {
            animateSummaryReply = true;
            changed = true;
        }

        if (!MessageObject.expandedQuotesEquals(lastDrawingExpandedQuotes, cell.currentMessageObject != null ? cell.currentMessageObject.expandedQuotes : null)) {
            animateExpandedQuotes = true;
            animateExpandedQuotesFrom = lastDrawingExpandedQuotes;
            changed = true;
        }

        if (lastDrawingExpandedExplanation != (cell.currentMessageObject != null && cell.currentMessageObject.expandedExplanation)) {
            animateExpandedExplanation = true;
            expandedExplanationFrom = lastDrawingExpandedExplanation;
            changed = true;
        }

        if (cell.currentMessageObject != null && lastDrawnForwardedName != cell.currentMessageObject.needDrawForwarded()) {
            animateForwardedLayout = true;
            animatingForwardedNameLayout[0] = lastDrawnForwardedNameLayout[0];
            animatingForwardedNameLayout[1] = lastDrawnForwardedNameLayout[1];
            animateForwardNameX = lastForwardNameX;
            animateForwardedNamesOffset = lastForwardedNamesOffset;
            animateForwardNameWidth = lastForwardNameWidth;
            changed = true;
        } else if (lastNamesOffset != cell.namesOffset) {
            animateNamesOffset = true;
            animateNamesOffsetFrom = lastNamesOffset;
            changed = true;
        }
        cell.updateReactionLayoutPosition();
        if (cell.reactionsLayoutInBubble.animateChange()) {
            changed = true;
        }
        if (cell.currentMessageObject.isRoundVideo()) {
            float y1 = cell.layoutHeight - dp(28 - (cell.drawPinnedBottom ? 2 : 0));
            if (!cell.reactionsLayoutInBubble.isEmpty) {
                y1 -= cell.reactionsLayoutInBubble.totalHeight;
            }
            if (y1 != lastDrawRoundVideoDotY) {
                animateRoundVideoDotY = true;
                animateFromRoundVideoDotY = lastDrawRoundVideoDotY;
                changed = true;
            }
        }

        if (cell.replyNameLayout != null && cell.replyStartX != lastDrawReplyY && lastDrawReplyY != 0) {
            animateReplyY = true;
            animateFromReplyY = lastDrawReplyY;
            changed = true;
        }

        final boolean translated = cell.currentMessageObject != null && cell.currentMessageObject.translated;
        if (translated != lastDrawnTranslated) {
            if (cell.titleLayout != null && lastDrawnTitleLayout != null) {
                animateTitleLayout = lastDrawnTitleLayout;
                animateTitleLayoutEmoji = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, cell, false, animateTitleLayoutEmoji, animateTitleLayout);
                changed = true;
            }
        }

        return changed;
    }

    public void onDetach() {
        wasDraw = false;
    }

    public void resetAnimation() {
        animateChange = false;
        animatePinned = false;
        animateBackgroundBoundsInner = false;
        animateBackgroundWidth = false;
        deltaLeft = 0;
        deltaRight = 0;
        deltaBottom = 0;
        deltaTop = 0;
        toDeltaLeft = 0;
        toDeltaRight = 0;
        if (imageChangeBoundsTransition && animateToImageW != 0 && animateToImageH != 0) {
            cell.photoImage.setImageCoords(animateToImageX, animateToImageY, animateToImageW, animateToImageH);
        }
        if (animateRadius) {
            cell.photoImage.setRoundRadius(animateToRadius);
        }
        animateToImageX = 0;
        animateToImageY = 0;
        animateToImageW = 0;
        animateToImageH = 0;
        imageChangeBoundsTransition = false;
        changePinnedBottomProgress = 1f;
        captionEnterProgress = 1f;
        animateRadius = false;
        animateChangeProgress = 1f;
        oldProgress = 0f;
        newProgress = 1f;
        animateMessageText = false;
        animateRichLayout = false;
        animateDrawingSideMenuEnabled = false;
        animateDrawNameLayout = false;
        animateDrawAvatar = false;
        animateDrawTopic = false;
        animateOutTextBlocks = null;
        if (animateOutRichLayout != null) {
            animateOutRichLayout.detach(cell);
        }
        animateOutRichLayout = null;
        animateEditedLayout = null;
        animateTimeLayout = null;
        animateEditedEnter = false;
        animateReplaceCaptionLayout = false;
        transformGroupToSingleMessage = false;
        animateOutCaptionLayout = null;
        AnimatedEmojiSpan.release(cell, animateOutAnimateEmoji);
        animateOutAnimateEmoji = null;
        moveCaption = false;
        animateDrawingTimeAlpha = false;
        transitionBotButtons.clear();
        animateButton = false;
        animateBotButtonsChanged = false;
        animateWidthForButton = false;
        animateMediaOffsetY = false;
        animateReplyTextLayout = null;

        animateReplies = false;
        animateRepliesLayout = null;

        animateComments = false;
        animateCommentsLayout = null;
        animateViewsLayout = null;
        animateShouldDrawTimeOnMedia = false;
        animateShouldDrawMenuDrawable = false;
        shouldAnimateTimeX = false;
        animateDrawBackground = false;
        animateSign = false;
        animateSmallImage = false;
        animateMonoforumPadding = false;
        animateStarsPriceTopPadding = false;
        animateStarsPriceText = false;
        animateBottomActionText = false;
        animateBottomActionPadding = false;
        needsStopClipping = false;
        animateLinkAbove = false;
        animateMediaAbove = false;
        animateRecommendationsExpanded = false;
        animateDrawingTimeAlpha = false;
        animateLocationIsExpired = false;
        animatePlayingRound = false;
        animateTextY = false;
        animateLinkPreviewY = false;
        animateFactCheckHeight = false;
        animateFactCheckExpanded = false;
        animateExpandedQuotes = false;
        animateExpandedExplanation = false;
        animateFactCheck = false;
        animateSummaryReply = false;
        animateForwardedLayout = false;
        animateNamesOffset = false;
        animatingForwardedNameLayout[0] = null;
        animatingForwardedNameLayout[1] = null;
        animateRoundVideoDotY = false;
        animateReplyY = false;
        cell.reactionsLayoutInBubble.resetAnimation();
        animateTitleLayout = null;
        AnimatedEmojiSpan.release(cell, animateTitleLayoutEmoji);
    }

    public boolean supportChangeAnimation() {
        return true;
    }

    public int createStatusDrawableParams() {
        if (cell.currentMessageObject.isOutOwner()) {
            boolean drawCheck1 = false;
            boolean drawCheck2 = false;
            boolean drawClock = false;
            boolean drawError = false;

            if (cell.currentMessageObject.isSending() || cell.currentMessageObject.isEditing()) {
                drawCheck2 = false;
                drawClock = true;
                drawError = false;
            } else if (cell.currentMessageObject.isSendError()) {
                drawCheck2 = false;
                drawClock = false;
                drawError = true;
            } else if (cell.currentMessageObject.isSent()) {
                if (!cell.currentMessageObject.scheduled && !cell.currentMessageObject.isUnread()) {
                    drawCheck1 = true;
                } else {
                    drawCheck1 = false;
                }
                drawCheck2 = true;
                drawClock = false;
                drawError = false;
            }
            if (cell.currentMessageObject.notime || cell.currentMessageObject.isQuickReply()) {
                drawCheck1 = false;
                drawCheck2 = false;
                drawClock = false;
            }
            return (drawCheck1 ? 1 : 0) | (drawCheck2 ? 2 : 0) | (drawClock ? 4 : 0) | (drawError ? 8 : 0);
        } else {
            boolean drawClock = cell.currentMessageObject.isSending() || cell.currentMessageObject.isEditing();
            boolean drawError = cell.currentMessageObject.isSendError();

            return (drawClock ? 4 : 0) | (drawError ? 8 : 0);
        }
    }
}
