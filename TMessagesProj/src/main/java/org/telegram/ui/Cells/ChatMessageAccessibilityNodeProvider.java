/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.messenger.MessageObject.getMedia;

import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.utils.code.highlight.CodeHighlighting;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.RichMessageLayout;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.MediaActionDrawable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

class ChatMessageAccessibilityNodeProvider extends AccessibilityNodeProvider {

    private final ChatMessageCell cell;

    ChatMessageAccessibilityNodeProvider(ChatMessageCell cell) {
        this.cell = cell;
    }

    public static final int RICH_MEDIA_START = 6000;
    public static final int PROFILE = 5000;
    public static final int LINK_IDS_START = 2000;
    public static final int LINK_CAPTION_IDS_START = 3000;
    public static final int BOT_BUTTONS_START = 1000;
    public static final int POLL_BUTTONS_START = 500;
    public static final int INSTANT_VIEW = 499;
    public static final int SHARE = 498;
    public static final int REPLY = 497;
    public static final int COMMENT = 496;
    public static final int POLL_HINT = 495;
    public static final int FORWARD = 494;
    public static final int TRANSCRIBE = 493;
    public static final int CONTACT = 492;
    public static final int CONTACT_VIEW = 491;
    public static final int CONTACT_ADD = 490;
    public static final int CONTACT_MESSAGE = 489;
    private Path linkPath = new Path();
    private RectF rectF = new RectF();
    private Rect rect = new Rect();

    private class ProfileSpan extends ClickableSpan {
        private TLRPC.User user;

        public ProfileSpan(TLRPC.User user) {
            this.user = user;
        }

        @Override
        public void onClick(@NonNull View view) {
            if (cell.delegate != null) {
                cell.delegate.didPressUserAvatar(cell, user, 0, 0, false);
            }
        }
    }

    private RichMessageLayout.RichBlock resolveRichElement(int virtualViewId, int[] outLocal) {
        if (cell.currentMessageObject == null || cell.currentMessageObject.richLayout == null) {
            return null;
        }
        final int globalIndex = virtualViewId - RICH_MEDIA_START;
        if (globalIndex < 0) {
            return null;
        }
        final RichMessageLayout richLayout = cell.currentMessageObject.richLayout;
        int acc = 0;
        for (int b = 0; b < richLayout.blocks.size(); b++) {
            final RichMessageLayout.RichBlock block = richLayout.blocks.get(b);
            if (!block.isVisible()) {
                continue;
            }
            final int count = block.getAccessibilityElementCount();
            if (globalIndex < acc + count) {
                outLocal[0] = globalIndex - acc;
                return block;
            }
            acc += count;
        }
        return null;
    }

    @Override
    public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
        int[] pos = {0, 0};
        cell.getLocationOnScreen(pos);
        if (virtualViewId == HOST_VIEW_ID) {
            AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain(cell);
            cell.onInitializeAccessibilityNodeInfo(info);
            final boolean unread = cell.currentMessageObject != null && cell.currentMessageObject.isOut() && !cell.currentMessageObject.scheduled && cell.currentMessageObject.isUnread();
            final boolean contentUnread = cell.currentMessageObject != null && cell.currentMessageObject.isContentUnread();
            final long fileSize = cell.currentMessageObject != null ? cell.currentMessageObject.loadedFileSize : 0;
            if (cell.accessibilityText == null || cell.accessibilityTextUnread != unread || cell.accessibilityTextContentUnread != contentUnread || cell.accessibilityTextFileSize != fileSize) {
                SpannableStringBuilder sb = new SpannableStringBuilder();
                if (cell.isChat && cell.currentUser != null && !cell.currentMessageObject.isOut()) {
                    sb.append(UserObject.getUserName(cell.currentUser));
                    sb.setSpan(new ProfileSpan(cell.currentUser), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    final CharSequence adminText = cell.getAdminAccessibilityText();
                    if (!TextUtils.isEmpty(adminText)) {
                        if (cell.adminLayoutIsTag) {
                            sb.append(' ').append(formatString(cell.adminLayoutIsAdmin ? R.string.AccDescrWithAdminTag : R.string.AccDescrWithMemberTag, adminText));
                        } else {
                            sb.append(", ").append(adminText);
                        }
                    }
                    sb.append('\n');
                }
                if (cell.drawForwardedName) {
                    for (int a = 0; a < 2; a++) {
                        if (cell.forwardedNameLayout[a] != null && cell.forwardedNameLayout[a].getText() != null) {
                            sb.append(cell.forwardedNameLayout[a].getText());
                            sb.append(a == 0 ? " " : "\n");
                        }
                    }
                }
                if (cell.documentAttach != null && cell.documentAttachType == ChatMessageCell.DOCUMENT_ATTACH_TYPE_DOCUMENT) {
                    String fileName = FileLoader.getAttachFileName(cell.documentAttach);
                    if (fileName.indexOf('.') != -1) {
                        sb.append(formatString(R.string.AccDescrDocumentType, fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT)));
                    }
                }
                if (cell.currentMessageObject.richLayout != null && !cell.currentMessageObject.richLayout.blocks.isEmpty()) {
                    for (RichMessageLayout.RichBlock block : cell.currentMessageObject.richLayout.blocks) {
                        if (!block.isVisible()) {
                            continue;
                        }
                        final int before = sb.length();
                        final SpannableStringBuilder blockText = new SpannableStringBuilder();
                        block.appendAccessibilityText(blockText);
                        final CharSequence blockLabel = block.getAccessibilityLabel();
                        final CharSequence listMarker = block.getAccessibilityListMarker();
                        if (!TextUtils.isEmpty(listMarker)) {
                            sb.append(listMarker);
                            if (!TextUtils.isEmpty(blockLabel) || blockText.length() > 0) {
                                sb.append(' ');
                            }
                        }
                        if (!TextUtils.isEmpty(blockLabel)) {
                            sb.append(blockLabel);
                            if (blockText.length() > 0) {
                                sb.append(", ");
                            }
                        }
                        sb.append(blockText);
                        if (sb.length() > before && sb.charAt(sb.length() - 1) != '\n') {
                            sb.append('\n');
                        }
                    }
                } else if (!TextUtils.isEmpty(cell.currentMessageObject.messageText)) {
                    CharSequence messageText = cell.currentMessageObject.messageText;
                    if (messageText instanceof Spanned) {
                        final Spanned spanned = (Spanned) messageText;
                        CodeHighlighting.Span[] codeSpans = spanned.getSpans(0, spanned.length(), CodeHighlighting.Span.class);
                        if (codeSpans != null && codeSpans.length > 0) {
                            SpannableStringBuilder ssb = new SpannableStringBuilder(messageText);
                            Arrays.sort(codeSpans, (a, b) -> spanned.getSpanStart(b) - spanned.getSpanStart(a));
                            for (CodeHighlighting.Span span : codeSpans) {
                                int start = spanned.getSpanStart(span);
                                if (start < 0) {
                                    continue;
                                }
                                final CharSequence label = TextUtils.isEmpty(span.lng)
                                    ? getString(R.string.AccDescrCodeBlock)
                                    : formatString(R.string.AccDescrCodeBlockLanguage, MessageObject.TextLayoutBlock.capitalizeLanguage(span.lng));
                                ssb.insert(start, label + ". ");
                            }
                            messageText = ssb;
                        }
                    }
                    sb.append(messageText);
                }
                if (cell.documentAttach != null && (cell.documentAttachType == ChatMessageCell.DOCUMENT_ATTACH_TYPE_DOCUMENT || cell.documentAttachType == ChatMessageCell.DOCUMENT_ATTACH_TYPE_GIF || cell.documentAttachType == ChatMessageCell.DOCUMENT_ATTACH_TYPE_VIDEO)) {
                    if (cell.buttonState == 1 && cell.loadingProgressLayout != null) {
                        sb.append("\n");
                        final boolean sending = cell.currentMessageObject.isSending();
                        final String key = sending ? "AccDescrUploadProgress" : "AccDescrDownloadProgress";
                        final int resId = sending ? R.string.AccDescrUploadProgress : R.string.AccDescrDownloadProgress;
                        sb.append(formatString(key, resId, AndroidUtilities.formatFileSize(cell.currentMessageObject.loadedFileSize), AndroidUtilities.formatFileSize(cell.lastLoadingSizeTotal)));
                    }
                }
                if (cell.currentMessageObject.isMusic()) {
                    sb.append("\n");
                    sb.append(formatString("AccDescrMusicInfo", R.string.AccDescrMusicInfo, cell.currentMessageObject.getMusicAuthor(), cell.currentMessageObject.getMusicTitle()));
                    sb.append(", ");
                    sb.append(LocaleController.formatDuration((int) cell.currentMessageObject.getDuration()));
                } else if (cell.currentMessageObject.isVoice() || cell.isRoundVideo) {
                    sb.append(", ");
                    sb.append(LocaleController.formatDuration((int) cell.currentMessageObject.getDuration()));
                    sb.append(", ");
                    if (cell.currentMessageObject.isContentUnread()) {
                        sb.append(getString("AccDescrMsgNotPlayed", R.string.AccDescrMsgNotPlayed));
                    } else {
                        sb.append(getString("AccDescrMsgPlayed", R.string.AccDescrMsgPlayed));
                    }
                }
                if (cell.lastPoll != null) {
                    sb.append(", ");
                    sb.append(cell.lastPoll.question.text);
                    sb.append(", ");
                    String title;
                    if (cell.pollClosed) {
                        title = getString("FinalResults", R.string.FinalResults);
                    } else {
                        if (cell.lastPoll.quiz) {
                            if (cell.lastPoll.public_voters) {
                                title = getString("QuizPoll", R.string.QuizPoll);
                            } else {
                                title = getString("AnonymousQuizPoll", R.string.AnonymousQuizPoll);
                            }
                        } else if (cell.lastPoll.public_voters) {
                            title = getString("PublicPoll", R.string.PublicPoll);
                        } else {
                            title = getString("AnonymousPoll", R.string.AnonymousPoll);
                        }
                    }
                    sb.append(title);
                }
                if (cell.documentAttach != null) {
                    if (cell.documentAttachType == ChatMessageCell.DOCUMENT_ATTACH_TYPE_VIDEO) {
                        sb.append(", ");
                        sb.append(LocaleController.formatDuration((int) cell.currentMessageObject.getDuration()));
                    }
                    if (cell.buttonState == 0 || cell.documentAttachType == ChatMessageCell.DOCUMENT_ATTACH_TYPE_DOCUMENT) {
                        sb.append(", ");
                        sb.append(AndroidUtilities.formatFileSize(cell.documentAttach.size));
                    }
                }
                if (cell.currentMessageObject.isVoiceTranscriptionOpen()) {
                    sb.append("\n");
                    sb.append(cell.currentMessageObject.getVoiceTranscription());
                } else {
                    if (MessageObject.getMedia(cell.currentMessageObject.messageOwner) != null && !TextUtils.isEmpty(cell.currentMessageObject.caption)) {
                        sb.append("\n");
                        sb.append(cell.currentMessageObject.caption);
                    }
                }
                if (cell.currentMessageObject.isOut()) {
                    if (cell.currentMessageObject.isSent()) {
                        sb.append("\n");
                        if (cell.currentMessageObject.scheduled) {
                            sb.append(formatString("AccDescrScheduledDate", R.string.AccDescrScheduledDate, cell.currentTimeString));
                        } else {
                            sb.append(formatString("AccDescrSentDate", R.string.AccDescrSentDate, getString("TodayAt", R.string.TodayAt) + " " + cell.currentTimeString));
                            sb.append(", ");
                            sb.append(cell.currentMessageObject.isUnread() ? getString("AccDescrMsgUnread", R.string.AccDescrMsgUnread) : getString("AccDescrMsgRead", R.string.AccDescrMsgRead));
                        }
                    } else if (cell.currentMessageObject.isSending()) {
                        sb.append("\n");
                        sb.append(getString("AccDescrMsgSending", R.string.AccDescrMsgSending));
                        final float sendingProgress = cell.radialProgress.getProgress();
                        if (sendingProgress > 0f) {
                            sb.append(Integer.toString(Math.round(sendingProgress * 100))).append("%");
                        }
                    } else if (cell.currentMessageObject.isSendError()) {
                        sb.append("\n");
                        sb.append(getString("AccDescrMsgSendingError", R.string.AccDescrMsgSendingError));
                    }
                } else {
                    sb.append("\n");
                    sb.append(formatString("AccDescrReceivedDate", R.string.AccDescrReceivedDate, getString("TodayAt", R.string.TodayAt) + " " + cell.currentTimeString));
                }
                if (cell.getRepliesCount() > 0 && !cell.hasCommentLayout()) {
                    sb.append("\n");
                    sb.append(formatPluralString("AccDescrNumberOfReplies", cell.getRepliesCount()));
                }
                if (cell.currentMessageObject.messageOwner.reactions != null && cell.currentMessageObject.messageOwner.reactions.results != null) {
                    if (cell.currentMessageObject.messageOwner.reactions.results.size() == 1) {
                        TLRPC.ReactionCount reaction = cell.currentMessageObject.messageOwner.reactions.results.get(0);
                        String emoticon = reaction.reaction instanceof TLRPC.TL_reactionEmoji ? ((TLRPC.TL_reactionEmoji) reaction.reaction).emoticon : "";
                        if (reaction.count == 1) {
                            sb.append("\n");
                            boolean isMe = false;
                            String userName = "";
                            if (cell.currentMessageObject.messageOwner.reactions.recent_reactions != null && cell.currentMessageObject.messageOwner.reactions.recent_reactions.size() == 1) {
                                TLRPC.MessagePeerReaction recentReaction = cell.currentMessageObject.messageOwner.reactions.recent_reactions.get(0);
                                if (recentReaction != null) {
                                    TLRPC.User user = MessagesController.getInstance(cell.currentAccount).getUser(MessageObject.getPeerId(recentReaction.peer_id));
                                    isMe = UserObject.isUserSelf(user);
                                    if (user != null) {
                                        userName = UserObject.getFirstName(user);
                                    }
                                }
                            }
                            if (isMe) {
                                sb.append(formatString("AccDescrYouReactedWith", R.string.AccDescrYouReactedWith, emoticon));
                            } else {
                                sb.append(formatString("AccDescrReactedWith", R.string.AccDescrReactedWith, userName, emoticon));
                            }
                        } else if (reaction.count > 1) {
                            sb.append("\n");
                            sb.append(formatPluralString("AccDescrNumberOfPeopleReactions", reaction.count, emoticon));
                        }
                    } else {
                        sb.append(getString("Reactions", R.string.Reactions)).append((": "));
                        final int count = cell.currentMessageObject.messageOwner.reactions.results.size();
                        for (int i = 0; i < count; ++i) {
                            TLRPC.ReactionCount reactionCount = cell.currentMessageObject.messageOwner.reactions.results.get(i);
                            String emoticon = reactionCount.reaction instanceof TLRPC.TL_reactionEmoji ? ((TLRPC.TL_reactionEmoji) reactionCount.reaction).emoticon : "";
                            if (reactionCount != null) {
                                sb.append(emoticon).append(" ").append(reactionCount.count + "");
                                if (i + 1 < count) {
                                    sb.append(", ");
                                }
                            }
                        }
                        sb.append("\n");
                    }
                }
                if ((cell.currentMessageObject.messageOwner.flags & TLRPC.MESSAGE_FLAG_HAS_VIEWS) != 0) {
                    sb.append("\n");
                    sb.append(formatPluralString("AccDescrNumberOfViews", cell.currentMessageObject.messageOwner.views));
                }
                sb.append("\n");

                CharacterStyle[] links = sb.getSpans(0, sb.length(), ClickableSpan.class);

                for (CharacterStyle link : links) {
                    int start = sb.getSpanStart(link);
                    int end = sb.getSpanEnd(link);
                    sb.removeSpan(link);

                    ClickableSpan underlineSpan = new ClickableSpan() {
                        @Override
                        public void onClick(View view) {
                            if (link instanceof ProfileSpan) {
                                ((ProfileSpan) link).onClick(view);
                            } else if (cell.delegate != null) {
                                cell.delegate.didPressUrl(cell, link, false);
                            }
                        }
                    };
                    sb.setSpan(underlineSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                cell.accessibilityText = sb;
                cell.accessibilityTextUnread = unread;
                cell.accessibilityTextContentUnread = contentUnread;
                cell.accessibilityTextFileSize = fileSize;
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                info.setContentDescription(cell.accessibilityText.toString());
            } else {
                info.setText(cell.accessibilityText);
            }

            info.setEnabled(true);
            AccessibilityNodeInfo.CollectionItemInfo itemInfo = info.getCollectionItemInfo();
            if (itemInfo != null) {
                info.setCollectionItemInfo(AccessibilityNodeInfo.CollectionItemInfo.obtain(itemInfo.getRowIndex(), 1, 0, 1, false));
            }
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.acc_action_msg_options, getString("AccActionMessageOptions", R.string.AccActionMessageOptions)));
            int icon = cell.getIconForCurrentState();
            CharSequence actionLabel = null;
            switch (icon) {
                case MediaActionDrawable.ICON_PLAY:
                    actionLabel = getString("AccActionPlay", R.string.AccActionPlay);
                    break;
                case MediaActionDrawable.ICON_PAUSE:
                    actionLabel = getString("AccActionPause", R.string.AccActionPause);
                    break;
                case MediaActionDrawable.ICON_FILE:
                    actionLabel = getString("AccActionOpenFile", R.string.AccActionOpenFile);
                    break;
                case MediaActionDrawable.ICON_DOWNLOAD:
                    actionLabel = getString("AccActionDownload", R.string.AccActionDownload);
                    break;
                case MediaActionDrawable.ICON_CANCEL:
                    actionLabel = getString("AccActionCancelDownload", R.string.AccActionCancelDownload);
                    break;
                default:
                    if (cell.currentMessageObject.type == MessageObject.TYPE_PHONE_CALL) {
                        actionLabel = getString("CallAgain", R.string.CallAgain);
                    }
            }
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, actionLabel));
            info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, getString("AccActionEnterSelectionMode", R.string.AccActionEnterSelectionMode)));
            int smallIcon = cell.getMiniIconForCurrentState();
            if (smallIcon == MediaActionDrawable.ICON_DOWNLOAD) {
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.acc_action_small_button, getString("AccActionDownload", R.string.AccActionDownload)));
            }
            if (cell.drawSummarizeButton || cell.drawSummaryReply) {
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.acc_action_summarize, getString("SummaryTitle", R.string.SummaryTitle)));
            }
            if (cell.currentMessageObject.textLayoutBlocks != null) {
                for (MessageObject.TextLayoutBlock block : cell.currentMessageObject.textLayoutBlocks) {
                    if (block.hasCodeCopyButton) {
                        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.acc_action_copy_code, getString("CopyCode", R.string.CopyCode)));
                        break;
                    }
                }
            }

            if ((cell.currentMessageObject.isVoice() || cell.currentMessageObject.isRoundVideo() || cell.currentMessageObject.isMusic()) && MediaController.getInstance().isPlayingMessage(cell.currentMessageObject)) {
                cell.seekBarAccessibilityDelegate.onInitializeAccessibilityNodeInfoInternal(info);
            }

            if (cell.useTranscribeButton && cell.transcribeButton != null) {
                info.addChild(cell, TRANSCRIBE);
            }

            int i;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                if (cell.isChat && cell.currentUser != null && !cell.currentMessageObject.isOut()) {
                    info.addChild(cell, PROFILE);
                }
                if (cell.currentMessageObject.messageText instanceof Spannable) {
                    Spannable buffer = (Spannable) cell.currentMessageObject.messageText;
                    CharacterStyle[] links = buffer.getSpans(0, buffer.length(), ClickableSpan.class);
                    i = 0;
                    for (CharacterStyle link : links) {
                        info.addChild(cell, LINK_IDS_START + i);
                        i++;
                    }
                }
                if (cell.currentMessageObject.caption instanceof Spannable && cell.captionLayout != null) {
                    Spannable buffer = (Spannable) cell.currentMessageObject.caption;
                    CharacterStyle[] links = buffer.getSpans(0, buffer.length(), ClickableSpan.class);
                    i = 0;
                    for (CharacterStyle link : links) {
                        info.addChild(cell, LINK_CAPTION_IDS_START + i);
                        i++;
                    }
                }
            }
            i = 0;
            for (BotButton button : cell.botButtons) {
                info.addChild(cell, BOT_BUTTONS_START + i);
                i++;
            }
            if (cell.hintButtonVisible && cell.pollHintX != -1 && cell.currentMessageObject.isPoll()) {
                info.addChild(cell, POLL_HINT);
            }
            i = 0;
            for (ChatMessageCell.PollButton button : cell.pollButtons) {
                info.addChild(cell, POLL_BUTTONS_START + i);
                i++;
            }
            if (cell.drawInstantView && !cell.instantButtonRect.isEmpty()) {
                info.addChild(cell, INSTANT_VIEW);
            }
            if (cell.contact != null && cell.contact.visible && cell.contactRect != null && !cell.contactRect.isEmpty()) {
                info.addChild(cell, CONTACT);
                if (cell.contact.buttons != null && cell.contact.buttons.size() > 1) {
                    for (ChatMessageCell.InstantViewButton instantViewButton : cell.contact.buttons) {
                        if (cell.contact.drawView && instantViewButton.type == ChatMessageCell.INSTANT_BUTTON_TYPE_CONTACT_VIEW && !instantViewButton.rect.isEmpty()) {
                            info.addChild(cell, CONTACT_VIEW);
                        }
                        if (cell.contact.drawAdd && instantViewButton.type == ChatMessageCell.INSTANT_BUTTON_TYPE_CONTACT_ADD && !instantViewButton.rect.isEmpty()) {
                            info.addChild(cell, CONTACT_ADD);
                        }
                        if (cell.contact.drawSendMessage && instantViewButton.type == ChatMessageCell.INSTANT_BUTTON_TYPE_CONTACT_SEND_MESSAGE && !instantViewButton.rect.isEmpty()) {
                            info.addChild(cell, CONTACT_MESSAGE);
                        }
                    }
                }
            }
            if (cell.commentLayout != null) {
                info.addChild(cell, COMMENT);
            }
            if (cell.drawSideButton == 1 || cell.drawSideButton == 2) {
                info.addChild(cell, SHARE);
            }
            if (cell.replyNameLayout != null) {
                info.addChild(cell, REPLY);
            }
            if (cell.currentMessageObject != null && cell.currentMessageObject.richLayout != null) {
                final RichMessageLayout richLayout = cell.currentMessageObject.richLayout;
                int acc = 0;
                for (int b = 0; b < richLayout.blocks.size(); b++) {
                    final RichMessageLayout.RichBlock block = richLayout.blocks.get(b);
                    if (!block.isVisible()) {
                        continue;
                    }
                    final int count = block.getAccessibilityElementCount();
                    for (int e = 0; e < count; e++) {
                        info.addChild(cell, RICH_MEDIA_START + acc + e);
                    }
                    acc += count;
                }
            }
            if (cell.forwardedNameLayout[0] != null && cell.forwardedNameLayout[1] != null) {
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(R.id.acc_action_open_forwarded_origin, getString("AccActionOpenForwardedOrigin", R.string.AccActionOpenForwardedOrigin)));
            }
            if (cell.drawSelectionBackground || cell.getBackground() != null) {
                info.setSelected(true);
            }
            return info;
        } else {
            AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
            info.setSource(cell, virtualViewId);
            info.setParent(cell);
            info.setPackageName(cell.getContext().getPackageName());
            if (virtualViewId == PROFILE) {
                if (cell.currentUser == null) {
                    return null;
                }
                String content = UserObject.getUserName(cell.currentUser);
                info.setText(content);
                rect.set((int) cell.nameX, (int) cell.nameY, (int) (cell.nameX + cell.nameWidth), (int) (cell.nameY + (cell.nameLayout != null ? cell.nameLayout.getHeight() : 10)));
                info.setBoundsInParent(rect);
                if (cell.accessibilityVirtualViewBounds.get(virtualViewId) == null) {
                    cell.accessibilityVirtualViewBounds.put(virtualViewId, new Rect(rect));
                }
                rect.offset(pos[0], pos[1]);
                info.setBoundsInScreen(rect);
                info.setClassName("android.widget.TextView");
                info.setEnabled(true);
                info.setClickable(true);
                info.setLongClickable(true);
                info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
                info.addAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
            } else if (virtualViewId >= RICH_MEDIA_START) {
                final int[] localElement = {0};
                final RichMessageLayout.RichBlock block = resolveRichElement(virtualViewId, localElement);
                if (block == null) {
                    return null;
                }
                info.setText(block.getAccessibilityElementText(localElement[0]));
                block.getAccessibilityElementBounds(localElement[0], rect);
                rect.offset(cell.textX, cell.textY);
                info.setBoundsInParent(rect);
                if (cell.accessibilityVirtualViewBounds.get(virtualViewId) == null) {
                    cell.accessibilityVirtualViewBounds.put(virtualViewId, new Rect(rect));
                }
                rect.offset(pos[0], pos[1]);
                info.setBoundsInScreen(rect);
                final boolean checkbox = block.isAccessibilityElementCheckbox(localElement[0]);
                if (checkbox) {
                    info.setClassName("android.widget.CheckBox");
                } else if (block.isAccessibilityElementText(localElement[0])) {
                    info.setClassName("android.widget.TextView");
                } else {
                    info.setClassName("android.widget.ImageView");
                }
                info.setEnabled(true);
                if (checkbox) {
                    info.setCheckable(true);
                    info.setChecked(block.isAccessibilityElementChecked(localElement[0]));
                }
                final boolean clickable = block.isAccessibilityElementClickable(localElement[0]);
                info.setClickable(clickable);
                if (clickable) {
                    info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
            } else if (virtualViewId >= LINK_CAPTION_IDS_START) {
                if (!(cell.currentMessageObject.caption instanceof Spannable) || cell.captionLayout == null) {
                    return null;
                }
                Spannable buffer = (Spannable) cell.currentMessageObject.caption;
                ClickableSpan link = getLinkById(virtualViewId, false);
                if (link == null) {
                    return null;
                }
                int[] linkPos = cell.getRealSpanStartAndEnd(buffer, link);
                String content = buffer.subSequence(linkPos[0], linkPos[1]).toString();
                info.setText(content);
                for (MessageObject.TextLayoutBlock block : cell.captionLayout.textLayoutBlocks) {
                    int length = block.textLayout.getText().length();
                    if (block.charactersOffset <= linkPos[0] && block.charactersOffset + length >= linkPos[1]) {
                        block.textLayout.getSelectionPath(linkPos[0] - block.charactersOffset, linkPos[1] - block.charactersOffset, linkPath);
                        linkPath.computeBounds(rectF, true);
                        rect.set((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom);
                        rect.offset(0, (int) block.textYOffset(cell.captionLayout.textLayoutBlocks, cell.transitionParams));
                        rect.offset(cell.textX, cell.textY);
                        info.setBoundsInParent(rect);
                        if (cell.accessibilityVirtualViewBounds.get(virtualViewId) == null) {
                            cell.accessibilityVirtualViewBounds.put(virtualViewId, new Rect(rect));
                        }
                        rect.offset(pos[0], pos[1]);
                        info.setBoundsInScreen(rect);
                        break;
                    }
                }

                info.setClassName("android.widget.TextView");
                info.setEnabled(true);
                info.setClickable(true);
                info.setLongClickable(true);
                info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
                info.addAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
            } else if (virtualViewId >= LINK_IDS_START) {
                if (!(cell.currentMessageObject.messageText instanceof Spannable)) {
                    return null;
                }
                Spannable buffer = (Spannable) cell.currentMessageObject.messageText;
                ClickableSpan link = getLinkById(virtualViewId, false);
                if (link == null) {
                    return null;
                }
                int[] linkPos = cell.getRealSpanStartAndEnd(buffer, link);
                String content = buffer.subSequence(linkPos[0], linkPos[1]).toString();
                info.setText(content);
                for (MessageObject.TextLayoutBlock block : cell.currentMessageObject.textLayoutBlocks) {
                    int length = block.textLayout.getText().length();
                    if (block.charactersOffset <= linkPos[0] && block.charactersOffset + length >= linkPos[1]) {
                        block.textLayout.getSelectionPath(linkPos[0] - block.charactersOffset, linkPos[1] - block.charactersOffset, linkPath);
                        linkPath.computeBounds(rectF, true);
                        rect.set((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom);
                        rect.offset(0, (int) block.textYOffset(cell.currentMessageObject.textLayoutBlocks, cell.transitionParams));
                        rect.offset(cell.textX, cell.textY);
                        info.setBoundsInParent(rect);
                        if (cell.accessibilityVirtualViewBounds.get(virtualViewId) == null) {
                            cell.accessibilityVirtualViewBounds.put(virtualViewId, new Rect(rect));
                        }
                        rect.offset(pos[0], pos[1]);
                        info.setBoundsInScreen(rect);
                        break;
                    }
                }

                info.setClassName("android.widget.TextView");
                info.setEnabled(true);
                info.setClickable(true);
                info.setLongClickable(true);
                info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
                info.addAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
            } else if (virtualViewId >= BOT_BUTTONS_START) {
                int buttonIndex = virtualViewId - BOT_BUTTONS_START;
                if (buttonIndex >= cell.botButtons.size()) {
                    return null;
                }
                BotButton button = cell.botButtons.get(buttonIndex);
                if (button.isSeparator) {
                    return null;
                }

                info.setText(button.title.getText());
                info.setClassName("android.widget.Button");
                info.setEnabled(true);
                info.setClickable(true);
                info.addAction(AccessibilityNodeInfo.ACTION_CLICK);

                rect.set((int) (button.x * cell.widthForButtons), button.y, (int) ((button.x + button.width) * cell.widthForButtons), button.y + button.height);
                int addX;
                if (cell.currentMessageObject.isOutOwner()) {
                    addX = cell.getMeasuredWidth() - cell.getWidthForButtons() - dp(10);
                } else {
                    addX = cell.backgroundDrawableLeft + dp(cell.mediaBackground ? 1 : 7);
                }
                rect.offset(addX, cell.layoutHeight);
                info.setBoundsInParent(rect);
                if (cell.accessibilityVirtualViewBounds.get(virtualViewId) == null) {
                    cell.accessibilityVirtualViewBounds.put(virtualViewId, new Rect(rect));
                }
                rect.offset(pos[0], pos[1]);
                info.setBoundsInScreen(rect);
            } else if (virtualViewId >= POLL_BUTTONS_START) {
                int buttonIndex = virtualViewId - POLL_BUTTONS_START;
                if (buttonIndex >= cell.pollButtons.size()) {
                    return null;
                }
                ChatMessageCell.PollButton button = cell.pollButtons.get(buttonIndex);
                StringBuilder sb = new StringBuilder(button.title.getText());
                if (!cell.pollVoted) {
                    info.setClassName("android.widget.Button");
                } else {
                    info.setSelected(button.chosen);
                    sb.append(", ").append(button.percent).append("%");
                    if (cell.lastPoll != null && cell.lastPoll.quiz && (button.chosen || button.correct)) {
                        sb.append(", ").append(button.correct ? getString("AccDescrQuizCorrectAnswer", R.string.AccDescrQuizCorrectAnswer) : getString("AccDescrQuizIncorrectAnswer", R.string.AccDescrQuizIncorrectAnswer));
                    }
                }
                info.setText(sb);
                info.setEnabled(true);
                info.addAction(AccessibilityNodeInfo.ACTION_CLICK);

                final int y = button.y + cell.namesOffset;
                final int w = cell.backgroundWidth - dp(76);
                rect.set(button.x, y, button.x + w, y + button.height);
                info.setBoundsInParent(rect);
                if (cell.accessibilityVirtualViewBounds.get(virtualViewId) == null) {
                    cell.accessibilityVirtualViewBounds.put(virtualViewId, new Rect(rect));
                }
                rect.offset(pos[0], pos[1]);
                info.setBoundsInScreen(rect);

                info.setClickable(true);
            } else if (virtualViewId == POLL_HINT) {
                info.setClassName("android.widget.Button");
                info.setEnabled(true);
                info.setText(getString(R.string.AccDescrQuizExplanation));
                info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
                rect.set(cell.pollHintX - dp(8), cell.pollHintY - dp(8), cell.pollHintX + dp(32), cell.pollHintY + dp(32));
                info.setBoundsInParent(rect);
                if (cell.accessibilityVirtualViewBounds.get(virtualViewId) == null || !cell.accessibilityVirtualViewBounds.get(virtualViewId).equals(rect)) {
                    cell.accessibilityVirtualViewBounds.put(virtualViewId, new Rect(rect));
                }
                rect.offset(pos[0], pos[1]);
                info.setBoundsInScreen(rect);
                info.setClickable(true);
            } else if (virtualViewId == INSTANT_VIEW) {
                info.setClassName("android.widget.Button");
                info.setEnabled(true);
                if (cell.instantViewLayout != null) {
                    info.setText(cell.instantViewLayout.getText());
                }
                info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
                cell.instantButtonRect.round(rect);
                info.setBoundsInParent(rect);
                if (cell.accessibilityVirtualViewBounds.get(virtualViewId) == null || !cell.accessibilityVirtualViewBounds.get(virtualViewId).equals(rect)) {
                    cell.accessibilityVirtualViewBounds.put(virtualViewId, new Rect(rect));
                }
                rect.offset(pos[0], pos[1]);
                info.setBoundsInScreen(rect);
                info.setClickable(true);
            } else if (virtualViewId == CONTACT) {
                info.setClassName("android.widget.Button");
                info.setEnabled(true);
                if (cell.titleLayout != null) {
                    info.setText(cell.titleLayout.getText());
                }
                info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
                cell.contactRect.round(rect);
                if (cell.contact != null && cell.contact.buttons != null && cell.contact.buttons.size() > 1) {
                    ChatMessageCell.InstantViewButton instantViewButton = cell.contact.buttons.get(0);
                    if (!instantViewButton.rect.isEmpty()) {
                        rect.set(rect.left, rect.top, rect.right, (int) (rect.bottom - instantViewButton.rect.height()));
                    }
                }
                info.setBoundsInParent(rect);
                if (cell.accessibilityVirtualViewBounds.get(virtualViewId) == null || !cell.accessibilityVirtualViewBounds.get(virtualViewId).equals(rect)) {
                    cell.accessibilityVirtualViewBounds.put(virtualViewId, new Rect(rect));
                }
                rect.offset(pos[0], pos[1]);
                info.setBoundsInScreen(rect);
                info.setClickable(true);
            } else if (virtualViewId == CONTACT_VIEW || virtualViewId == CONTACT_ADD || virtualViewId == CONTACT_MESSAGE) {
                int requiredType;
                if (virtualViewId == CONTACT_VIEW) {
                    requiredType = ChatMessageCell.INSTANT_BUTTON_TYPE_CONTACT_VIEW;
                } else if (virtualViewId == CONTACT_ADD) {
                    requiredType = ChatMessageCell.INSTANT_BUTTON_TYPE_CONTACT_ADD;
                } else {
                    requiredType = ChatMessageCell.INSTANT_BUTTON_TYPE_CONTACT_SEND_MESSAGE;
                }
                for (int i = 0; cell.contact != null && i < cell.contact.buttons.size(); i++) {
                    ChatMessageCell.InstantViewButton instantViewButton = cell.contact.buttons.get(i);
                    if (instantViewButton.type == requiredType) {
                        info.setClassName("android.widget.Button");
                        info.setEnabled(true);
                        if (instantViewButton.layout != null) {
                            info.setText(instantViewButton.layout.getText());
                        }
                        info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
                        instantViewButton.rect.round(rect);
                        info.setBoundsInParent(rect);
                        if (cell.accessibilityVirtualViewBounds.get(virtualViewId) == null || !cell.accessibilityVirtualViewBounds.get(virtualViewId).equals(rect)) {
                            cell.accessibilityVirtualViewBounds.put(virtualViewId, new Rect(rect));
                        }
                        rect.offset(pos[0], pos[1]);
                        info.setBoundsInScreen(rect);
                        info.setClickable(true);
                        break;
                    }
                }
            } else if (virtualViewId == SHARE) {
                info.setClassName("android.widget.ImageButton");
                info.setEnabled(true);
                if (cell.isOpenChatByShare(cell.currentMessageObject)) {
                    info.setContentDescription(getString("AccDescrOpenChat", R.string.AccDescrOpenChat));
                } else {
                    info.setContentDescription(getString("ShareFile", R.string.ShareFile));
                }
                info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
                rect.set((int) cell.sideStartX, (int) cell.sideStartY, (int) cell.sideStartX + dp(40), (int) cell.sideStartY + dp(32));
                info.setBoundsInParent(rect);
                if (cell.accessibilityVirtualViewBounds.get(virtualViewId) == null || !cell.accessibilityVirtualViewBounds.get(virtualViewId).equals(rect)) {
                    cell.accessibilityVirtualViewBounds.put(virtualViewId, new Rect(rect));
                }
                rect.offset(pos[0], pos[1]);
                info.setBoundsInScreen(rect);
                info.setClickable(true);
            } else if (virtualViewId == REPLY) {
                info.setEnabled(true);
                StringBuilder sb = new StringBuilder();
                sb.append(getString("Reply", R.string.Reply));
                sb.append(", ");
                if (cell.replyNameLayout != null) {
                    sb.append(cell.replyNameLayout.getText());
                    sb.append(", ");
                }
                if (cell.replyTextLayout != null) {
                    sb.append(cell.replyTextLayout.getText());
                }
                info.setContentDescription(sb.toString());
                info.addAction(AccessibilityNodeInfo.ACTION_CLICK);

                rect.set(cell.replyStartX, cell.replyStartY, cell.replyStartX + Math.max(cell.replyNameWidth, cell.replyTextWidth), cell.replyStartY + (int) cell.replyHeight);
                info.setBoundsInParent(rect);
                if (cell.accessibilityVirtualViewBounds.get(virtualViewId) == null || !cell.accessibilityVirtualViewBounds.get(virtualViewId).equals(rect)) {
                    cell.accessibilityVirtualViewBounds.put(virtualViewId, new Rect(rect));
                }
                rect.offset(pos[0], pos[1]);
                info.setBoundsInScreen(rect);
                info.setClickable(true);
            } else if (virtualViewId == FORWARD) {
                info.setEnabled(true);
                StringBuilder sb = new StringBuilder();
                if (cell.forwardedNameLayout[0] != null && cell.forwardedNameLayout[1] != null) {
                    for (int a = 0; a < 2; a++) {
                        sb.append(cell.forwardedNameLayout[a].getText());
                        sb.append(a == 0 ? " " : "\n");
                    }
                }
                info.setContentDescription(sb.toString());
                info.addAction(AccessibilityNodeInfo.ACTION_CLICK);

                int x = (int) Math.min(cell.forwardNameX - cell.forwardNameOffsetX[0], cell.forwardNameX - cell.forwardNameOffsetX[1]);
                rect.set(x, cell.forwardNameY, x + cell.forwardedNameWidth, cell.forwardNameY + cell.forwardHeight);
                info.setBoundsInParent(rect);
                if (cell.accessibilityVirtualViewBounds.get(virtualViewId) == null || !cell.accessibilityVirtualViewBounds.get(virtualViewId).equals(rect)) {
                    cell.accessibilityVirtualViewBounds.put(virtualViewId, new Rect(rect));
                }
                rect.offset(pos[0], pos[1]);
                info.setBoundsInScreen(rect);
                info.setClickable(true);
            } else if (virtualViewId == COMMENT) {
                info.setClassName("android.widget.Button");
                info.setEnabled(true);
                int commentCount = cell.getRepliesCount();
                String comment = null;
                if (cell.currentMessageObject != null && !cell.currentMessageObject.shouldDrawWithoutBackground() && !cell.currentMessageObject.isAnimatedEmoji()) {
                    if (cell.isRepliesChat) {
                        comment = getString("ViewInChat", R.string.ViewInChat);
                    } else {
                        comment = commentCount == 0 ? getString("LeaveAComment", R.string.LeaveAComment) : formatPluralString("CommentsCount", commentCount);
                    }
                } else if (!cell.isRepliesChat && commentCount > 0) {
                    comment = LocaleController.formatShortNumber(commentCount, null);
                }
                if (comment != null) {
                    info.setText(comment);
                }
                info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
                rect.set(cell.commentButtonRect);
                info.setBoundsInParent(rect);
                if (cell.accessibilityVirtualViewBounds.get(virtualViewId) == null || !cell.accessibilityVirtualViewBounds.get(virtualViewId).equals(rect)) {
                    cell.accessibilityVirtualViewBounds.put(virtualViewId, new Rect(rect));
                }
                rect.offset(pos[0], pos[1]);
                info.setBoundsInScreen(rect);
                info.setClickable(true);
            } else if (virtualViewId == TRANSCRIBE) {
                info.setClassName("android.widget.Button");
                info.setEnabled(true);
                info.setText(cell.currentMessageObject.isVoiceTranscriptionOpen() ? getString("AccActionCloseTranscription", R.string.AccActionCloseTranscription) : getString("AccActionOpenTranscription", R.string.AccActionOpenTranscription));
                info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
                if (cell.transcribeButton != null) {
                    rect.set((int) cell.ensureAudio().transcribeX, (int) cell.ensureAudio().transcribeY, (int) (cell.ensureAudio().transcribeX + cell.transcribeButton.width()), (int) (cell.ensureAudio().transcribeY + cell.transcribeButton.height()));
                }
                info.setBoundsInParent(rect);
                rect.offset(pos[0], pos[1]);
                info.setBoundsInScreen(rect);
                info.setClickable(true);
            }
            info.setFocusable(true);
            info.setVisibleToUser(true);
            return info;
        }
    }

    @Override
    public boolean performAction(int virtualViewId, int action, Bundle arguments) {
        if (virtualViewId == HOST_VIEW_ID) {
            cell.performAccessibilityAction(action, arguments);
        } else {
            if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
                cell.sendAccessibilityEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
            } else if (action == AccessibilityNodeInfo.ACTION_CLICK) {
                if (virtualViewId == PROFILE) {
                    if (cell.delegate != null) {
                        cell.delegate.didPressUserAvatar(cell, cell.currentUser, 0, 0, false);
                    }
                } else if (virtualViewId >= RICH_MEDIA_START) {
                    final int[] localElement = {0};
                    final RichMessageLayout.RichBlock block = resolveRichElement(virtualViewId, localElement);
                    if (block != null && block.onAccessibilityElementClick(localElement[0], cell)) {
                        cell.sendAccessibilityEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED);
                        AndroidUtilities.makeAccessibilityAnnouncement(block.getAccessibilityElementStateDescription(localElement[0]));
                    }
                } else if (virtualViewId >= LINK_CAPTION_IDS_START) {
                    ClickableSpan link = getLinkById(virtualViewId, true);
                    if (link != null) {
                        cell.delegate.didPressUrl(cell, link, false);
                        cell.sendAccessibilityEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED);
                    }
                } else if (virtualViewId >= LINK_IDS_START) {
                    ClickableSpan link = getLinkById(virtualViewId, false);
                    if (link != null) {
                        cell.delegate.didPressUrl(cell, link, false);
                        cell.sendAccessibilityEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED);
                    }
                } else if (virtualViewId >= BOT_BUTTONS_START) {
                    int buttonIndex = virtualViewId - BOT_BUTTONS_START;
                    if (buttonIndex >= cell.botButtons.size()) {
                        return false;
                    }
                    BotButton button = cell.botButtons.get(buttonIndex);
                    if (cell.delegate != null && !button.isLocked) {
                        if (button.buttonCustom != null) {
                            cell.delegate.didPressCustomBotButton(cell, button.buttonCustom);
                        } else if (button.button != null) {
                            cell.delegate.didPressBotButton(cell, button.button);
                        }
                    }
                    cell.sendAccessibilityEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED);
                } else if (virtualViewId >= POLL_BUTTONS_START) {
                    int buttonIndex = virtualViewId - POLL_BUTTONS_START;
                    if (buttonIndex >= cell.pollButtons.size()) {
                        return false;
                    }
                    ChatMessageCell.PollButton button = cell.pollButtons.get(buttonIndex);
                    if (cell.delegate != null) {
                        ArrayList<TLRPC.PollAnswer> answers = new ArrayList<>();
                        answers.add(button.answer);
                        cell.delegate.didPressVoteButtons(cell, answers, -1, 0, 0);
                    }
                    cell.sendAccessibilityEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED);
                } else if (virtualViewId == POLL_HINT) {
                    cell.didPressVoteHint();
                } else if (virtualViewId == INSTANT_VIEW) {
                    if (cell.delegate != null) {
                        cell.delegate.didPressInstantButton(cell, cell.drawInstantViewType);
                    }
                } else if (virtualViewId == CONTACT) {
                    if (cell.delegate != null) {
                        cell.delegate.didPressInstantButton(cell, ChatMessageCell.INSTANT_BUTTON_TYPE_CONTACT_VIEW);
                    }
                } else if (virtualViewId == CONTACT_VIEW) {
                    if (cell.delegate != null) {
                        cell.delegate.didPressInstantButton(cell, ChatMessageCell.INSTANT_BUTTON_TYPE_CONTACT_VIEW);
                    }
                } else if (virtualViewId == CONTACT_ADD) {
                    if (cell.delegate != null) {
                        cell.delegate.didPressInstantButton(cell, ChatMessageCell.INSTANT_BUTTON_TYPE_CONTACT_ADD);
                    }
                } else if (virtualViewId == CONTACT_MESSAGE) {
                    if (cell.delegate != null) {
                        cell.delegate.didPressInstantButton(cell, ChatMessageCell.INSTANT_BUTTON_TYPE_CONTACT_SEND_MESSAGE);
                    }
                } else if (virtualViewId == SHARE) {
                    if (cell.delegate != null) {
                        cell.delegate.didPressSideButton(cell);
                    }
                } else if (virtualViewId == REPLY) {
                    if (cell.delegate != null && (!cell.isThreadChat || cell.isMonoForum || cell.currentMessageObject.getReplyTopMsgId() != 0) && (cell.currentMessageObject.hasValidReplyMessageObject() || cell.hasReplyQuote || cell.currentMessageObject.messageOwner != null && cell.currentMessageObject.messageOwner.reply_to != null && cell.currentMessageObject.messageOwner.reply_to.reply_from != null)) {
                        cell.delegate.didPressReplyMessage(cell, cell.currentMessageObject.getReplyMsgId(), 0, 0, false);
                    }
                } else if (virtualViewId == FORWARD) {
                    if (cell.delegate != null) {
                        if (cell.currentForwardChannel != null) {
                            cell.delegate.didPressChannelAvatar(cell, cell.currentForwardChannel, cell.currentMessageObject.messageOwner.fwd_from.channel_post, cell.lastTouchX, cell.lastTouchY, false);
                        } else if (cell.currentForwardUser != null) {
                            cell.delegate.didPressUserAvatar(cell, cell.currentForwardUser, cell.lastTouchX, cell.lastTouchY, false);
                        } else if (cell.currentForwardName != null) {
                            cell.delegate.didPressHiddenForward(cell);
                        }
                    }
                } else if (virtualViewId == COMMENT) {
                    if (cell.delegate != null) {
                        if (cell.isRepliesChat) {
                            cell.delegate.didPressSideButton(cell);
                        } else {
                            cell.delegate.didPressCommentButton(cell);
                        }
                    }
                } else if (virtualViewId == TRANSCRIBE && cell.transcribeButton != null) {
                    cell.transcribeButton.onTap();
                }
            } else if (action == AccessibilityNodeInfo.ACTION_LONG_CLICK) {
                ClickableSpan link = getLinkById(virtualViewId, virtualViewId >= LINK_CAPTION_IDS_START);
                if (link != null && cell.delegate != null) {
                    cell.delegate.didPressUrl(cell, link, true);
                    cell.sendAccessibilityEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
                }
            }
        }
        return true;
    }

    private ClickableSpan getLinkById(int id, boolean caption) {
        if (id == PROFILE) {
            return null;
        }
        if (caption) {
            id -= LINK_CAPTION_IDS_START;
            if (!(cell.currentMessageObject.caption instanceof Spannable) || id < 0) {
                return null;
            }
            Spannable buffer = (Spannable) cell.currentMessageObject.caption;
            ClickableSpan[] links = buffer.getSpans(0, buffer.length(), ClickableSpan.class);
            if (links.length <= id) {
                return null;
            }
            return links[id];
        } else {
            id -= LINK_IDS_START;
            if (!(cell.currentMessageObject.messageText instanceof Spannable) || id < 0) {
                return null;
            }
            Spannable buffer = (Spannable) cell.currentMessageObject.messageText;
            ClickableSpan[] links = buffer.getSpans(0, buffer.length(), ClickableSpan.class);
            if (links.length <= id) {
                return null;
            }
            return links[id];
        }
    }
}
