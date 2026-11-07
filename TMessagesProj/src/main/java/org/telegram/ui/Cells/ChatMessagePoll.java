/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatPluralString;
import static org.telegram.messenger.LocaleController.formatPluralStringComma;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.messenger.MessageObject.getMedia;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.DynamicDrawableSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.URLSpan;
import android.util.StateSet;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.widget.Toast;

import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.utils.CountdownTimer;
import org.telegram.messenger.utils.DrawableUtils;
import org.telegram.messenger.utils.tlutils.TlUtils;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.MessageDrawable;
import org.telegram.ui.Cells.ChatMessageCell.PollButton;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.CheckBoxBase;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.ReplyMessageLine;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Components.TimerParticles;
import org.telegram.ui.Components.URLSpanBotCommand;
import org.telegram.ui.Components.URLSpanMono;
import org.telegram.ui.Components.AvatarsListDrawable;
import org.telegram.ui.Components.poll.PollAttachedMediaPack;
import org.telegram.ui.Components.poll.PollUtils;
import org.telegram.ui.Components.poll.buttons.PollAddButtonDrawable;
import org.telegram.ui.Components.poll.buttons.PollButtonDrawable;
import org.telegram.ui.Components.poll.PollContentDrawable;
import org.telegram.ui.Components.poll.buttons.PollInstantButtonDrawable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import me.vkryl.core.BitwiseUtils;
class ChatMessagePoll {

    private final ChatMessageCell cell;

    boolean instantViewTouchesBottom;
    StaticLayout explanationTitleLayout;
    CharSequence currentExplanation;
    private int explanationContentWidth;
    private int explanationContentHeight;
    int explanationHeight;
    private Drawable closeExplanationDrawable;
    private int closeExplanationDrawableColor;
    CountdownTimer countDownTimer;
    PollInstantButtonDrawable instantButtonDrawable;
    PollAddButtonDrawable addButtonDrawable;
    PollContentDrawable contentDrawable;
    PollContentDrawable explanationDrawable;
    private int contentWidth;
    int contentHeight;
    int contentHeightWithOffset;
    private float animationProgress;
    boolean allowAdding;
    boolean inInputNewOption;
    private boolean withMediaInAnswers;
    private boolean hideResults;
    private boolean hasResults;
    private boolean hasVoteRestrictions;
    private long lastCloseTime;
    private String closeTimeText;
    private int closeTimeWidth;
    private boolean animateAnswer;
    boolean animateAnswerAlpha;
    private long voteLastUpdateTime;
    private float voteRadOffset;
    private boolean pressedVoteIsMedia;
    int mediaPressedIndex = PollAttachedMediaPack.INDEX_NONE;
    private float timerTransitionProgress;
    private TLRPC.PollResults lastResultsObj;
    AvatarsListDrawable recentVotersDrawable;
    private Paint cutAvatarPaint;
    private Paint checkPaint;
    private Path checkPath;

    ChatMessagePoll(ChatMessageCell cell) {
        this.cell = cell;
    }

    void setMessageContentIfPoll(final MessageObject messageObject, boolean messageIdChanged) {
        if (cell.timerParticles == null) {
            cell.timerParticles = new TimerParticles();
        }
        cell.pollResultsPreview = false;
        cell.createSelectorDrawable(0);
        cell.drawName = true;
        cell.drawForwardedName = !cell.isRepliesChat;
        cell.drawPhotoImage = false;
        int maxWidth = Math.min(dp(500), messageObject.getMaxMessageTextWidth());
        cell.backgroundWidth = maxWidth + dp(31);

        TLRPC.MessageMedia m = MessageObject.getMedia(messageObject.messageOwner);
        TLRPC.TL_textWithEntities title;
        String subtitle;
        boolean todo, quiz, public_voters, multiple_choice;
        int total_voters;
        if (m instanceof TLRPC.TL_messageMediaPoll) {
            TLRPC.TL_messageMediaPoll media = (TLRPC.TL_messageMediaPoll) m;
            todo = false;
            quiz = media.poll.quiz;
            public_voters = media.poll.public_voters;
            multiple_choice = media.poll.multiple_choice;
            total_voters = media.results.total_voters;
            timerTransitionProgress = media.poll.close_date - ConnectionsManager.getInstance(cell.currentAccount).getCurrentTime() < 60 ? 0.0f : 1.0f;
            cell.pollClosed = media.poll.closed;
            cell.pollResultsPreview = messageObject.forceShowPollResults;
            hideResults = media.poll.hide_results_until_close;
            title = media.poll.question;
            if (cell.pollClosed) {
                subtitle = getString(R.string.FinalResults);
            } else if (media.poll.quiz) {
                subtitle = getString(media.poll.public_voters ? R.string.QuizPoll : R.string.AnonymousQuizPoll);
            } else if (media.poll.public_voters) {
                subtitle = getString(R.string.PublicPoll);
            } else {
                subtitle = getString(R.string.AnonymousPoll);
            }
            cell.lastPoll = media.poll;
            cell.lastPollResults = media.results.results;
            lastResultsObj = media.results;
            cell.lastPollResultsVoters = media.results.total_voters;
            currentExplanation = messageObject.quizExplanation;
            if (currentExplanation == null && !TextUtils.isEmpty(media.results.solution)) {
                messageObject.generateExplanation();
                currentExplanation = messageObject.quizExplanation;
            }
            if (currentExplanation != null) {
                if (closeExplanationDrawable == null) {
                    closeExplanationDrawable = cell.getContext().getResources().getDrawable(R.drawable.msg_close).mutate();
                }
                int explanationWidth = cell.backgroundWidth - dp(38);
                explanationContentWidth = explanationWidth - dp(10 + 8);

                explanationContentHeight = 0;
                if (media.results != null && media.results.solution_media != null) {
                    if (explanationDrawable == null) {
                        explanationDrawable = new PollContentDrawable(cell.currentAccount, cell, cell.resourcesProvider, true);
                    }
                    String attachPath = PollAttachedMediaPack.getAttachPath(messageObject.messageOwner, PollAttachedMediaPack.INDEX_EXPLANATION);
                    explanationDrawable.setMedia(messageObject, media.results.solution_media, messageObject, explanationWidth, attachPath, !messageIdChanged);
                    if (explanationDrawable.isHasMedia()) {
                        explanationContentHeight = explanationDrawable.getHeightForWidth(explanationWidth);
                    }

                    if (cell.isCellAttachedToWindow()) {
                        explanationDrawable.attach();
                    }
                }

                explanationTitleLayout = new StaticLayout(
                        TextUtils.ellipsize(getString(R.string.QuizExplanationTitle), Theme.chat_replyNamePaint, maxWidth - dp(30), TextUtils.TruncateAt.END),
                        Theme.chat_replyNamePaint, maxWidth - dp(30), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                cell.explanationLayout = new MessageObject.TextLayoutBlocks(messageObject, currentExplanation, Theme.chat_quoteTextPaint, explanationWidth - dp(11));
                explanationHeight = cell.explanationLayout.textHeight() + dp(26 + 8 + 3) + (explanationContentHeight > 0 ? explanationContentHeight + dp(8 + 6) : 0);
                cell.animatedEmojiPollExplanation = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, cell, false, cell.animatedEmojiPollExplanation, cell.explanationLayout.textLayoutBlocks);
            }
        } else {
            TLRPC.TL_messageMediaToDo media = (TLRPC.TL_messageMediaToDo) m;
            todo = true;
            quiz = false;
            public_voters = true;
            multiple_choice = true;
            timerTransitionProgress = 1.0f;
            cell.pollClosed = false;
            hideResults = false;
            title = media.todo.title;
            total_voters = 0;
            subtitle = getString(media.todo.others_can_complete && messageObject.getDialogId() != UserConfig.getInstance(cell.currentAccount).getClientUserId() ? R.string.MessageGroupTodoList : R.string.MessageTodoList);
        }
        if (messageObject.translated && messageObject.messageOwner != null && messageObject.messageOwner.translatedPoll != null && messageObject.messageOwner.translatedPoll.question != null) {
            title = messageObject.messageOwner.translatedPoll.question;
        }

        allowAdding = false;
        inInputNewOption = false;
        withMediaInAnswers = false;
        cell.pollVoted = messageObject.isVoted();
        hasResults = messageObject.hasVoteResults();
        hasVoteRestrictions = BitwiseUtils.hasFlag(PollUtils.getVoteRestrictedFlags(messageObject),
            PollUtils.VOTE_RESTRICTED_BY_COUNTRY_FLAG |
            PollUtils.VOTE_RESTRICTED_NOT_SUBSCRIBED_FLAG |
            PollUtils.VOTE_RESTRICTED_NOT_SUBSCRIBED_24H_FLAG);

        if (cell.pollVoted) {
            messageObject.checkedVotes.clear();
        }
        CharSequence questionText = new SpannableStringBuilder(title.text);
        questionText = Emoji.replaceEmoji(questionText, Theme.chat_audioTitlePaint.getFontMetricsInt(), false);
        if (title.entities != null) {
            questionText = MessageObject.replaceAnimatedEmoji(questionText, title.entities, Theme.chat_audioTitlePaint.getFontMetricsInt(), true);
            MessageObject.addEntitiesToText(questionText, title.entities, cell.currentMessageObject.isOutOwner(), false, false, false);
        }
        cell.titleLayout = new StaticLayout(questionText, Theme.chat_audioTitlePaint, maxWidth + dp(2) - cell.getExtraTextX() * 2, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        cell.animatedEmojiPollQuestion = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, cell, false, cell.animatedEmojiPollQuestion, cell.titleLayout);
        boolean titleRtl = false;
        if (cell.titleLayout != null) {
            for (int a = 0, N = cell.titleLayout.getLineCount(); a < N; a++) {
                if (cell.titleLayout.getLineLeft(a) > 0) {
                    titleRtl = true;
                    break;
                }
            }
        }
        cell.docTitleLayout = new StaticLayout(TextUtils.ellipsize(subtitle, Theme.chat_timePaint, maxWidth, TextUtils.TruncateAt.END), Theme.chat_timePaint, maxWidth + dp(2) - cell.getExtraTextX() * 2, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        if (cell.docTitleLayout != null && cell.docTitleLayout.getLineCount() > 0) {
            if (titleRtl && !LocaleController.isRTL) {
                cell.docTitleOffsetX = (int) Math.ceil(maxWidth - cell.docTitleLayout.getLineWidth(0));
            } else if (!titleRtl && LocaleController.isRTL) {
                cell.docTitleOffsetX = -(int) Math.ceil(cell.docTitleLayout.getLineLeft(0));
            } else {
                cell.docTitleOffsetX = 0;
            }
        }
        int w = maxWidth - dp(messageObject.isOutOwner() ? 28 : 8);

        if (!cell.isBot) {
            TextPaint textPaint = !public_voters && !multiple_choice ? Theme.chat_livePaint : Theme.chat_locationAddressPaint;
            CharSequence votes;
            if (todo) {
                TLRPC.TL_messageMediaToDo media = (TLRPC.TL_messageMediaToDo) m;
                if (!cell.currentMessageObject.isOutOwner() && cell.currentMessageObject.getDialogId() >= 0 && !media.todo.others_can_complete) {
                    final String by = DialogObject.getName(cell.currentMessageObject.getFromChatId());
                    votes = formatPluralStringComma("TodoCompletedBy", media.todo.list.size(), MessageObject.getCompletionsCount(media), by);
                } else {
                    votes = formatPluralStringComma("TodoCompleted", media.todo.list.size(), MessageObject.getCompletionsCount(media));
                }
            } else if (quiz) {
                votes = TextUtils.ellipsize(total_voters == 0 ? getString(R.string.NoVotesQuiz) : formatPluralString("Answer", total_voters), textPaint, w, TextUtils.TruncateAt.END);
            } else {
                votes = TextUtils.ellipsize(total_voters == 0 ? getString(R.string.NoVotes) : formatPluralString("Vote", total_voters), textPaint, w, TextUtils.TruncateAt.END);
            }

            boolean hasPollCloseTimer = false;
            if (!cell.pollClosed && m instanceof TLRPC.TL_messageMediaPoll) {
                TLRPC.TL_messageMediaPoll media = (TLRPC.TL_messageMediaPoll) m;
                if (media.poll.close_date > 0) {
                    final int timeDiff = media.poll.close_date - ConnectionsManager.getInstance(cell.currentAccount).getCurrentTime();
                    if (timeDiff > 0) {
                        final boolean resultsHidden = media.poll.hide_results_until_close && !media.poll.creator;
                        hasPollCloseTimer = true;
                        if (countDownTimer == null) {
                            countDownTimer = new CountdownTimer((sec) -> {
                                if (cell.animatedInfoLayout2 != null) {
                                    cell.animatedInfoLayout2.setText(LocaleController.formatPollEndTime((int) sec, resultsHidden), true);
                                }
                            });
                        }

                        countDownTimer.start(timeDiff);
                        if (cell.animatedInfoLayout2 == null) {
                            cell.animatedInfoLayout2 = new AnimatedTextView.AnimatedTextDrawable(false, true, true);
                            cell.animatedInfoLayout2.setCallback(cell);
                            cell.animatedInfoLayout2.setGravity(Gravity.CENTER);
                            cell.animatedInfoLayout2.setOverrideFullWidth(AndroidUtilities.displaySize.x);
                        }
                        cell.animatedInfoLayout2.setTextSize(dp(11));
                        cell.animatedInfoLayout2.setTextColor((!public_voters && !multiple_choice ? Theme.chat_livePaint : Theme.chat_locationAddressPaint).getColor());
                        cell.animatedInfoLayout2.setText(LocaleController.formatPollEndTime(timeDiff, resultsHidden), !messageIdChanged);
                    }
                }
            }
            if (!hasPollCloseTimer) {
                if (countDownTimer != null) {
                    countDownTimer.stop();
                }
                cell.animatedInfoLayout2 = null;
            }

            if (cell.animatedInfoLayout == null) {
                cell.animatedInfoLayout = new AnimatedTextView.AnimatedTextDrawable(false, true, true);
                cell.animatedInfoLayout.setCallback(cell);
                cell.animatedInfoLayout.setGravity(Gravity.CENTER);
                cell.animatedInfoLayout.setOverrideFullWidth(AndroidUtilities.displaySize.x);
            }
            cell.animatedInfoLayout.setTextSize(dp(13));
            cell.animatedInfoLayout.setTextColor((!public_voters && !multiple_choice ? Theme.chat_livePaint : Theme.chat_locationAddressPaint).getColor());
            cell.animatedInfoLayout.setText(votes, !messageIdChanged);
            cell.infoLayout = null;
        }
        cell.measureTime(messageObject);

        createPollUI(todo ? 30 : MessagesController.getInstance(cell.currentAccount).config.pollAnswersMax.get());
        if (m instanceof TLRPC.TL_messageMediaPoll && ((TLRPC.TL_messageMediaPoll) m).results != null) {
            final TLRPC.TL_messageMediaPoll media = (TLRPC.TL_messageMediaPoll) m;
            if (recentVotersDrawable != null) {
                recentVotersDrawable.set(media.results.recent_voters, !messageIdChanged);
            }
        } else if (recentVotersDrawable != null) {
            recentVotersDrawable.set(null, false);
        }

        int maxVote = 0;
        if (!animateAnswer && cell.pollVoteInProgress && cell.vibrateOnPollVote) {
            try {
                cell.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            } catch (Exception ignored) {}
        }
        animateAnswer = cell.attachedToWindow && (cell.pollVoteInProgress || cell.pollUnvoteInProgress);
        animateAnswerAlpha = animateAnswer && m instanceof TLRPC.TL_messageMediaPoll;
        ArrayList<PollButton> previousPollButtons = cell.oldPollButtons, previousPollButtons2 = null;
        ArrayList<PollButton> sortedPollButtons = new ArrayList<>();
        if (!cell.pollButtons.isEmpty()) {
            previousPollButtons = previousPollButtons2 = new ArrayList<>(cell.pollButtons);
            for (PollButton btn : cell.pollButtons) {
                btn.destroy();
            }
            cell.pollButtons.clear();
            if (!animateAnswer) {
                animateAnswer = cell.attachedToWindow && (cell.pollVoted || cell.pollClosed || cell.pollResultsPreview);
            }
            if (animationProgress > 0 && animationProgress < 1.0f) {
                for (int b = 0, N2 = previousPollButtons.size(); b < N2; b++) {
                    PollButton button = previousPollButtons.get(b);
                    button.percent = (int) Math.ceil(button.prevPercent + (button.percent - button.prevPercent) * animationProgress);
                    button.percentProgress = button.prevPercentProgress + (button.percentProgress - button.prevPercentProgress) * animationProgress;
                }
            }
        }

        animationProgress = animateAnswer ? 0.0f : 1.0f;
        byte[] votingFor;
        if (!animateAnswerAlpha) {
            cell.pollVoteInProgress = false;
            cell.pollVoteInProgressNum = -1;
            votingFor = SendMessagesHelper.getInstance(cell.currentAccount).isSendingVote(cell.currentMessageObject);
        } else {
            votingFor = null;
        }

        int height = (messageObject.expandedExplanation ? explanationHeight : 0) + (cell.titleLayout != null ? cell.titleLayout.getHeight() : 0);
        int restPercent = 100;
        boolean hasDifferent = false;
        int previousPercent = 0;
        int emojisCount = 0;
        if (m instanceof TLRPC.TL_messageMediaPoll) {
            cell.captionAbove = true;
            TLRPC.TL_messageMediaPoll media = (TLRPC.TL_messageMediaPoll) m;
            if (media.attached_media != null) {
                if (contentDrawable == null) {
                    contentDrawable = new PollContentDrawable(cell.currentAccount, cell, cell.resourcesProvider, false);
                    contentDrawable.imageReceiver.setRoundRadius(cell.pollPhotoImageRadius);
                }

                final int mediaWidth = cell.backgroundWidth - dp(13) - dp(2) - dp(2);
                String attachPath = PollAttachedMediaPack.getAttachPath(messageObject.messageOwner, PollAttachedMediaPack.INDEX_DESCRIPTION);
                contentDrawable.setMedia(messageObject, media.attached_media, messageObject, mediaWidth, attachPath, !messageIdChanged);
                if (contentDrawable.isHasMedia()) {
                    contentWidth = mediaWidth;
                    contentHeight = contentDrawable.getHeightForWidth(contentWidth);
                    contentHeightWithOffset = contentHeight + dp(4) + dp(4);
                    height += contentHeightWithOffset;
                }

                if (cell.isCellAttachedToWindow()) {
                    contentDrawable.attach();
                }
            }

            TlUtils.calculateAnswerShuffleHash(media.poll, UserConfig.getInstance(cell.currentAccount).getClientUserId());
            final ArrayList<TLRPC.PollAnswer> answers = media.poll.shuffled_answers != null ?
                media.poll.shuffled_answers :  media.poll.answers;

            for (int a = 0, N = media.poll.answers.size(); a < N; a++) {
                final TLRPC.PollAnswer pollAnswer = media.poll.answers.get(a);
                if (pollAnswer.media != null) {
                    withMediaInAnswers = true;
                    break;
                }
            }

            for (int a = 0, N = answers.size(); a < N; a++) {
                TLRPC.PollAnswer pollAnswer = answers.get(a);
                boolean translated = false;
                if (cell.currentMessageObject.translated && cell.currentMessageObject.messageOwner != null && cell.currentMessageObject.messageOwner.translatedPoll != null) {
                    for (TLRPC.PollAnswer translatedPollAnswer : cell.currentMessageObject.messageOwner.translatedPoll.answers) {
                        if (Arrays.equals(translatedPollAnswer.option, pollAnswer.option)) {
                            translated = true;
                            pollAnswer = translatedPollAnswer;
                            break;
                        }
                    }
                }
                CharSequence answerText = new SpannableStringBuilder(pollAnswer.text.text);
                answerText = Emoji.replaceEmoji(answerText, Theme.chat_audioTitlePaint.getFontMetricsInt(), false, null, DynamicDrawableSpan.ALIGN_BOTTOM, 1.0f, emojisCount);
                if (pollAnswer.text.entities != null) {
                    answerText = MessageObject.replaceAnimatedEmoji(answerText, pollAnswer.text.entities, Theme.chat_audioPerformerPaint.getFontMetricsInt(), true, 1.2f, emojisCount);
                    MessageObject.addEntitiesToText(answerText, pollAnswer.text.entities, cell.currentMessageObject.isOutOwner(), false, false, false);
                }
                emojisCount += (answerText instanceof Spannable ? ((Spannable) answerText).getSpans(0, answerText.length(), Emoji.EmojiSpan.class).length + ((Spannable) answerText).getSpans(0, answerText.length(), AnimatedEmojiSpan.class).length : 0);

                PollButton prevButton = null;
                if (previousPollButtons != null) {
                    for (int i = 0; i < previousPollButtons.size(); ++i) {
                        PollButton b = previousPollButtons.get(i);
                        if (b.answer == pollAnswer || b.answer != null && Arrays.equals(previousPollButtons.get(i).answer.option, pollAnswer.option)) {
                            prevButton = previousPollButtons.get(i);
                            break;
                        }
                    }
                }
                PollButton button = cell.new PollButton();
                if (prevButton != null) {
                    button.selectorDrawableColor = prevButton.selectorDrawableColor;
                    button.selectorDrawable = prevButton.selectorDrawable;
                } else {
                    button.selectorDrawable = Theme.createRadSelectorDrawable(button.selectorDrawableColor = cell.getThemedColor(Theme.key_listSelector), 0, 0);
                }
                if (prevButton != null) {
                    button.pollButtonDrawable = prevButton.pollButtonDrawable;
                    prevButton.pollButtonDrawable = null;
                }
                if (button.pollButtonDrawable == null) {
                    button.pollButtonDrawable = new PollButtonDrawable(cell.currentAccount, cell);
                }

                String attachPath = PollAttachedMediaPack.getAttachPath(messageObject.messageOwner, pollAnswer.unshuffled_index);
                button.pollButtonDrawable.setHasMediaPadding(withMediaInAnswers);
                button.pollButtonDrawable.setMedia(messageObject, pollAnswer.media, messageObject, attachPath, !messageIdChanged);

                if (cell.isCellAttachedToWindow()) {
                    button.attach();
                }
                button.selectorDrawable.setCallback(cell);
                button.answer = pollAnswer;
                final int titleMaxWidth = maxWidth - dp(48 + (withMediaInAnswers ? 48 : 0));
                button.title = new StaticLayout(answerText, Theme.chat_audioPerformerPaint, titleMaxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                button.moveTitleByCounter = false;
                button.animatedEmoji = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, cell, false, button.animatedEmoji, button.title);
                button.y = height + dp(52);
                button.height = button.title.getHeight();
                button.translated = translated;
                if (prevButton != null && prevButton.translated != translated) {
                    button.animateTitle = prevButton.title;
                    button.animateTitleEmoji = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, cell, false, button.animateTitleEmoji, button.animateTitle);
                    button.animateY = prevButton.y;
                    button.animateHeight = prevButton.height;
                }
                cell.pollButtons.add(button);
                sortedPollButtons.add(button);
                height += button.height + dp(26.66f);

                final int titleLinesCount = button.title.getLineCount();
                final float titleLastLineWidth = titleLinesCount > 0 ?
                        button.title.getLineWidth(titleLinesCount - 1) : 0;
                final boolean isTitleRtl = titleLinesCount > 0 && button.title.getParagraphDirection(0) == Layout.DIR_RIGHT_TO_LEFT;
                final float titleLastLineAvail = titleMaxWidth - dp(10) - titleLastLineWidth;

                boolean votersWasSet = false;
                if (!media.results.results.isEmpty()) {
                    for (int b = 0, N2 = media.results.results.size(); b < N2; b++) {
                        TLRPC.PollAnswerVoters answer = media.results.results.get(b);
                        if (Arrays.equals(button.answer.option, answer.option)) {
                            if (button.pollButtonDrawable != null) {
                                final boolean isCouterVisible = !cell.isBot && (cell.pollVoted || cell.pollResultsPreview || cell.pollClosed || (hasVoteRestrictions && (!hideResults || hasResults)));
                                button.pollButtonDrawable.setVotersVisible(isCouterVisible, prevButton != null && !messageIdChanged);
                                button.pollButtonDrawable.setVotersCount(answer.voters, prevButton != null && !messageIdChanged);
                                button.pollButtonDrawable.setRecentVoters(answer.recent_voters, prevButton != null && !messageIdChanged);

                                final float votersCountWidth = button.pollButtonDrawable.getVotersCountTargetWidth();
                                button.moveTitleByCounter = isTitleRtl && titleLinesCount == 1 && (titleLastLineAvail - dp(10)) > votersCountWidth;

                                if (isCouterVisible && (votersCountWidth > titleLastLineAvail || isTitleRtl && !button.moveTitleByCounter)) {
                                    button.height += dp(22);
                                    height += dp(22);
                                }
                                votersWasSet = true;
                            }

                            button.chosen = answer.chosen;
                            button.count = answer.voters;
                            button.correct = answer.correct;
                            if ((cell.pollVoted || cell.pollClosed || cell.pollResultsPreview || (hasVoteRestrictions && (!hideResults || hasResults))) && media.results.total_voters > 0) {
                                float percent = MathUtils.clamp(answer.voters / (float) media.results.total_voters, 0, 1);
                                button.decimal = 100 * percent;
                                button.percent = (int) button.decimal;
                                button.decimal -= button.percent;
                            } else {
                                button.percent = 0;
                                button.decimal = 0;
                            }
                            if (previousPercent == 0) {
                                previousPercent = button.percent;
                            } else if (button.percent != 0 && previousPercent != button.percent) {
                                hasDifferent = true;
                            }
                            restPercent -= button.percent;
                            maxVote = Math.max(button.percent, maxVote);
                            break;
                        }
                    }
                }

                if (!votersWasSet) {
                    button.pollButtonDrawable.setVotersVisible(false, prevButton != null && !messageIdChanged);
                }

                if (previousPollButtons2 != null) {
                    for (int b = 0, N2 = previousPollButtons2.size(); b < N2; b++) {
                        PollButton prevButton2 = previousPollButtons2.get(b);
                        if (Arrays.equals(button.answer.option, prevButton2.answer.option)) {
                            button.prevPercent = prevButton2.percent;
                            button.prevPercentProgress = prevButton2.percentProgress;
                            button.prevChosen = prevButton2.chosen;
                            break;
                        }
                    }
                }
                if (votingFor != null && button.answer.option.length > 0 && Arrays.binarySearch(votingFor, button.answer.option[0]) >= 0) {
                    cell.pollVoteInProgressNum = a;
                    cell.pollVoteInProgress = true;
                    cell.vibrateOnPollVote = true;
                    votingFor = null;
                }

                final boolean isVotedButHiddenResults = cell.pollVoted && !hasResults && hideResults;

                if (isVotedButHiddenResults) {
                    cell.pollCheckBox[a].setChecked(button.chosen, false);
                } else if (cell.currentMessageObject.checkedVotes.contains(button.answer)) {
                    cell.pollCheckBox[a].setChecked(true, false);
                } else {
                    cell.pollCheckBox[a].setChecked(false, false);
                }
                cell.pollCheckBox[a].checkScale = 0.85f;
            }

            final boolean canAddPollAnswers = cell.delegate != null && cell.delegate.allowAddPollOptions()
                && media.poll.open_answers && !cell.pollClosed && !messageObject.scheduled && !messageObject.isForwarded()
                && (media.poll.answers.size() < MessagesController.getInstance(cell.currentAccount).config.pollAnswersMax.get());

            if (canAddPollAnswers) {
                if (addButtonDrawable == null) {
                    addButtonDrawable = new PollAddButtonDrawable(cell.getContext(), cell.resourcesProvider);
                    addButtonDrawable.setupCallbacks(cell);
                }

                allowAdding = true;
                final int pollAddHeight = cell.delegate != null ? cell.delegate.getAddPollOptionInputFieldHeight(cell) : 0;
                inInputNewOption = pollAddHeight > 0;

                addButtonDrawable.setIsEditEnabled(inInputNewOption, !messageIdChanged);
                cell.pollAddButtonHeight = Math.max(dp(44.33f), pollAddHeight);
                height += cell.pollAddButtonHeight;
            }

            for (PollButton btn : previousPollButtons) {
                btn.detach();
                btn.pollButtonDrawable = null;
            }

            checkInstantButtonForPoll(!messageIdChanged);
        } else if (m instanceof TLRPC.TL_messageMediaToDo) {
            TLRPC.TL_messageMediaToDo media = (TLRPC.TL_messageMediaToDo) m;
            for (int a = 0, N = media.todo.list.size(); a < N; a++) {
                TLRPC.TodoItem task = media.todo.list.get(a);
                boolean translated = false;
//                        if (currentMessageObject.translated && currentMessageObject.messageOwner != null && currentMessageObject.messageOwner.translatedPoll != null) {
//                            for (TLRPC.PollAnswer translatedPollAnswer : currentMessageObject.messageOwner.translatedPoll.answers) {
//                                if (Arrays.equals(translatedPollAnswer.option, pollAnswer.option)) {
//                                    translated = true;
//                                    pollAnswer = translatedPollAnswer;
//                                    break;
//                                }
//                            }
//                        }
                CharSequence answerText = new SpannableStringBuilder(task.title.text);
                answerText = Emoji.replaceEmoji(answerText, Theme.chat_audioTitlePaint.getFontMetricsInt(), false, null, DynamicDrawableSpan.ALIGN_BOTTOM, 1.0f, emojisCount);
                if (task.title.entities != null) {
                    answerText = MessageObject.replaceAnimatedEmoji(answerText, task.title.entities, Theme.chat_audioPerformerPaint.getFontMetricsInt(), true, 1.2f, emojisCount);
                    MessageObject.addEntitiesToText(answerText, task.title.entities, cell.currentMessageObject.isOutOwner(), false, false, false);
                }
                emojisCount += (answerText instanceof Spannable ? ((Spannable) answerText).getSpans(0, answerText.length(), Emoji.EmojiSpan.class).length + ((Spannable) answerText).getSpans(0, answerText.length(), AnimatedEmojiSpan.class).length : 0);

                PollButton prevButton = null;
                if (previousPollButtons != null) {
                    for (int i = 0; i < previousPollButtons.size(); ++i) {
                        PollButton b = previousPollButtons.get(i);
                        if (b.task == task || b.task != null && b.task.id == task.id) {
                            prevButton = b;
                            break;
                        }
                    }
                }
                Boolean sending = SendMessagesHelper.getInstance(cell.currentAccount).getSendingTodoValue(messageObject, task);
                PollButton button = cell.new PollButton();
                button.author = prevButton != null ? prevButton.author : null;
                if (prevButton != null) {
                    button.selectorDrawableColor = prevButton.selectorDrawableColor;
                    button.selectorDrawable = prevButton.selectorDrawable;
                } else {
                    button.selectorDrawable = Theme.createRadSelectorDrawable(button.selectorDrawableColor = cell.getThemedColor(Theme.key_listSelector), 0, 0);
                }
                button.selectorDrawable.setCallback(cell);
                if (prevButton != null) {
                    button.avatarDrawable = prevButton.avatarDrawable;
                    button.avatarImageReceiver = prevButton.avatarImageReceiver;
                    prevButton.avatarDrawable = null;
                    prevButton.avatarImageReceiver = null;
                }
                if (button.avatarDrawable == null) {
                    button.avatarDrawable = new AvatarDrawable();
                }
                if (button.avatarImageReceiver == null) {
                    button.avatarImageReceiver = new ImageReceiver(cell);
                    button.avatarImageReceiver.setRoundRadius(dp(20));
                }
                if (cell.isCellAttachedToWindow()) {
                    button.attach();
                }
                button.chosen = false;
                if (!media.completions.isEmpty()) {
                    for (TLRPC.TodoCompletion completion : media.completions) {
                        if (completion.id == task.id) {
                            button.chosen = true;
                            TLObject obj = MessagesController.getInstance(cell.currentAccount).getUserOrChat(DialogObject.getPeerDialogId(completion.completed_by));
                            button.avatarDrawable.setInfo(obj);
                            button.avatarImageReceiver.setForUserOrChat(obj, button.avatarDrawable);
                            button.author = new Text(DialogObject.getName(obj), 12);
                            break;
                        }
                    }
                }
                if (sending != null) {
                    button.chosen = sending;
                }
                if (button.chosen && !messageObject.canCompleteTodo()) {
                    if (!(answerText instanceof SpannableStringBuilder)) {
                        answerText = new SpannableStringBuilder(answerText);
                    }
                    ((SpannableStringBuilder) answerText).setSpan(new StrikethroughSpan(), 0, answerText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                button.task = task;
                button.title = new StaticLayout(answerText, Theme.chat_audioPerformerPaint, maxWidth - dp(48 - 2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                button.animatedEmoji = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, cell, false, button.animatedEmoji, button.title);
                button.y = height + dp(52);
                button.height = button.title.getHeight();
                button.translated = translated;
                if (prevButton != null && prevButton.translated != translated) {
                    button.animateTitle = prevButton.title;
                    button.animateTitleEmoji = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, cell, false, button.animateTitleEmoji, button.animateTitle);
                    button.animateY = prevButton.y;
                    button.animateHeight = prevButton.height;
                }
                cell.pollButtons.add(button);
                sortedPollButtons.add(button);
                height += button.height + dp(26.66f);
                cell.pollCheckBox[a].setChecked(button.chosen, !messageIdChanged);
                cell.pollCheckBox[a].checkScale = 1f;
            }
            for (PollButton btn : previousPollButtons) {
                if (btn.avatarImageReceiver != null) {
                    btn.avatarImageReceiver.onDetachedFromWindow();
                    btn.avatarImageReceiver = null;
                }
            }
        }
        normalizePollPercents(hasDifferent, restPercent, sortedPollButtons);
        int width = cell.backgroundWidth - dp(76);
        for (int b = 0, N2 = cell.pollButtons.size(); b < N2; b++) {
            PollButton button = cell.pollButtons.get(b);
            int div = 100; // maxVote;
            button.percentProgress = Math.max(dp(5) / (float) width, div != 0 ? button.percent / (float) div : 0);
        }

        cell.setMessageObjectInternal(messageObject);

        instantViewTouchesBottom = false;
        if (cell.isBot && !cell.drawInstantView) {
            height -= dp(10);
        } else if (public_voters || multiple_choice || true) {
            instantViewTouchesBottom = true;
            height += dp(13);
        }
        cell.totalHeight = dp(46 + 27) + cell.namesOffset + height;
        if (cell.drawPinnedTop) {
            cell.namesOffset -= dp(1);
        }
        cell.instantTextNewLine = false;
        if (public_voters || multiple_choice) {
            int instantTextWidth = 0;
            for (int a = 0; a < 3; a++) {
                String str;
                if (a == 0) {
                    str = getString(R.string.PollViewResultsNoCaps);
                } else if (a == 1) {
                    str = getString(R.string.PollSubmitVotesNoCaps);
                } else {
                    str = getString(R.string.NoVotes);
                }
                instantTextWidth = Math.max(instantTextWidth, (int) Math.ceil(Theme.chat_instantViewPaint.measureText(str)));
            }
            int timeWidthTotal = cell.timeWidth + (messageObject.isOutOwner() ? dp(20) : 0) + cell.getExtraTimeX();
            if (!cell.reactionsLayoutInBubble.isSmall && cell.reactionsLayoutInBubble.isEmpty && timeWidthTotal >= (cell.backgroundWidth - dp(76) - instantTextWidth) / 2) {
                cell.totalHeight += dp(18);
                cell.instantTextNewLine = true;
            }
        }
        if (!cell.reactionsLayoutInBubble.isSmall) {
            if (!cell.reactionsLayoutInBubble.isEmpty) {
                cell.reactionsLayoutInBubble.measure(maxWidth, Gravity.LEFT);
                cell.totalHeight += cell.reactionsLayoutInBubble.height + dp(12);
                int timeWidthTotal = cell.timeWidth + (messageObject.isOutOwner() ? dp(20) : 0) + cell.getExtraTimeX();
                if (timeWidthTotal >= (cell.backgroundWidth - dp(24) - cell.reactionsLayoutInBubble.lastLineX)) {
                    cell.totalHeight += dp(16);
                    cell.reactionsLayoutInBubble.positionOffsetY -= dp(16);
                }
            }
        }
    }

    void createPollUI(int answersCount) {
        if (cell.pollCheckBox == null || cell.pollCheckBox.length != answersCount) {
            if (cell.pollCheckBox != null) {
                for (int a = 0; a < cell.pollCheckBox.length; a++) {
                    cell.pollCheckBox[a].onDetachedFromWindow();
                }
            }
            cell.pollCheckBox = new CheckBoxBase[answersCount];
            for (int a = 0; a < cell.pollCheckBox.length; a++) {
                cell.pollCheckBox[a] = new CheckBoxBase(cell, 20, cell.resourcesProvider);
                cell.pollCheckBox[a].setDrawUnchecked(false);
                cell.pollCheckBox[a].setCuttingCheck(true);
                cell.pollCheckBox[a].setBackgroundType(9);
            }
        }

        if (recentVotersDrawable == null) {
            recentVotersDrawable = new AvatarsListDrawable(cell.currentAccount, cell, dp(16), dp(7f), dpf2(1));
        }
    }

    void checkInstantButtonForPoll(boolean animated) {
        if (cell.currentMessageObject == null) {
            return;
        }

        final MessageObject messageObject = cell.currentMessageObject;
        final TLRPC.MessageMedia m = getMedia(messageObject);
        final TLRPC.TL_messageMediaPoll media;
        if (m instanceof TLRPC.TL_messageMediaPoll) {
            media = (TLRPC.TL_messageMediaPoll) m;
        } else {
            return;
        }

        CharSequence instantViewText = null;
        int instantViewType = 0;

        boolean hasCheckedCheckboxes = false;
        if (cell.pollCheckBox != null) {
            for (int a = 0, N = Math.min(media.poll.answers.size(), cell.pollCheckBox.length); a < N; a++) {
                if (cell.pollCheckBox[a] != null && cell.pollCheckBox[a].isChecked()) {
                    hasCheckedCheckboxes = true;
                }
            }
        }

        final TLRPC.PollResults results = media.results;
        final boolean canViewHiddenResultsWithoutVote = !cell.pollVoted && !cell.pollClosed && hasResults && results != null && results.total_voters != 0 && media.poll.creator; //pollHideResults;
        final boolean canVote = !cell.pollVoted && !cell.pollClosed && !hasVoteRestrictions;
        final boolean isAnonymousPoll = !media.poll.public_voters;


        if (inInputNewOption) {
            instantViewType = ChatMessageCell.INSTANT_BUTTON_TYPE_ADD_OPTION;
            instantViewText = getString(R.string.PollButtonSave);
        } else {
            if (canViewHiddenResultsWithoutVote && !(media.poll.multiple_choice && canVote && hasCheckedCheckboxes)) {
                if (isAnonymousPoll) {
                    if (messageObject.forceShowPollResults) {
                        instantViewType = ChatMessageCell.INSTANT_BUTTON_TYPE_BACK_TO_VOTE;
                        instantViewText = AndroidUtilities.replaceArrows(getString(R.string.PollViewBack), false, -dp(8f / 3f), 0, 1.0f);
                    } else {
                        instantViewType = ChatMessageCell.INSTANT_BUTTON_TYPE_VIEW_VOTES_AS_AUTHOR_LIST;
                        instantViewText = AndroidUtilities.replaceArrows(formatPluralString("PollViewVotesAsAdmin", results.total_voters), false);
                    }
                } else {
                    instantViewType = ChatMessageCell.INSTANT_BUTTON_TYPE_VIEW_VOTES_PUBLIC_LIST;
                    instantViewText = formatString(R.string.PollViewVotesX, results.total_voters);
                }
            } else if (media.poll.multiple_choice && canVote) {
                instantViewType = ChatMessageCell.INSTANT_BUTTON_TYPE_VOTE_MULTISELECT;
                instantViewText = getString(R.string.PollSubmitVotesNoCaps);
            } else if (!cell.isBot && media.poll.public_voters && (cell.pollVoted && (!hideResults || hasResults) || cell.pollClosed && media.results.total_voters != 0)) {
                instantViewType = ChatMessageCell.INSTANT_BUTTON_TYPE_VIEW_VOTES_PUBLIC_LIST;
                instantViewText = formatString(R.string.PollViewVotesX, results.total_voters);
            }
        }

        if (instantViewType != 0) {
            if (instantButtonDrawable == null) {
                instantButtonDrawable = new PollInstantButtonDrawable(cell, cell.resourcesProvider);
                instantButtonDrawable.setupCallbacks(cell);
            }

            cell.instantViewButtonText = instantViewText;
            instantButtonDrawable.setButtonText(instantViewText, animated);
            cell.drawInstantView = true;
            cell.drawInstantViewType = instantViewType;
            cell.createInstantViewButton();
        }
        cell.checkPollVoteSendingStatus(animated);
    }

    boolean checkPollButtonMotionEvent(MotionEvent event) {
        if (cell.currentMessageObject.eventId != 0 || (cell.pollVoteInProgress || cell.pollUnvoteInProgress) && !cell.currentMessageObject.isTodo() || cell.pollButtons.isEmpty() || cell.currentMessageObject.type != MessageObject.TYPE_POLL || !cell.currentMessageObject.isSent()) {
            return false;
        }
        int x = (int) cell.getEventX(event);
        int y = (int) cell.getEventY(event);

        boolean result = false;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            cell.pressedVoteButton = -1;
            cell.pollHintPressed = false;
            cell.pollAddButtonPressed = -1;
            cell.closeExplanationPressed = false;
            cell.resetPressedLink(4);
            if (cell.closeExplanationX != -1 && cell.closeExplanationY != -1 && Math.abs(x - cell.closeExplanationX) < dp(20) && Math.abs(y - cell.closeExplanationY) < dp(20)) {
                cell.closeExplanationPressed = true;
                result = true;
                resetPollButtonSelectors();
                cell.invalidate();
            } else if (cell.hintButtonVisible && cell.pollHintX != -1 && x >= cell.pollHintX && x <= cell.pollHintX + dp(40) && y >= cell.pollHintY && y <= cell.pollHintY + dp(40)) {
                cell.pollHintPressed = true;
                result = true;
                cell.selectorDrawableMaskType[0] = 3;
                if (cell.selectorDrawable[0] != null) {
                    cell.selectorDrawable[0].setBounds(cell.pollHintX - dp(8), cell.pollHintY - dp(8), cell.pollHintX + dp(32), cell.pollHintY + dp(32));
                    cell.selectorDrawable[0].setHotspot(x, y);
                    cell.selectorDrawable[0].setState(cell.pressedState);
                }
                resetPollButtonSelectors();
                cell.invalidate();
            } else if (cell.currentMessageObject.forceShowPollResults) {
                // make buttons is non clickable when preview results
                resetPollButtonSelectors();
                cell.invalidate();
            } else {
                resetPollButtonSelectors();
                for (int a = 0; a < cell.pollButtons.size(); a++) {
                    PollButton button = cell.pollButtons.get(a);
                    int y2 = button.y + cell.namesOffset - dp(13);

                    if (button.title != null && button.title.getText() instanceof Spannable && x >= button.titleX && y >= button.titleY && !cell.currentMessageObject.preview) {
                        try {
                            int checkX = x - (int) button.titleX;
                            int checkY = y - (int) button.titleY;
                            if (checkY <= button.title.getHeight()) {
                                final int line = button.title.getLineForVertical(checkY);
                                final int off = button.title.getOffsetForHorizontal(line, checkX);

                                final float left = button.title.getLineLeft(line);
                                if (left <= checkX && left + button.title.getLineWidth(line) >= checkX) {
                                    Spannable buffer = (Spannable) button.title.getText();
                                    ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);
                                    boolean ignore = false;
                                    if (link.length == 0 || link[0] instanceof URLSpanBotCommand && !URLSpanBotCommand.enabled) {
                                        ignore = true;
                                    }
                                    if (!ignore && !AndroidUtilities.isAccessibilityScreenReaderEnabled()) {
                                        if (cell.pressedLink == null || cell.pressedLink.getSpan() != link[0]) {
                                            cell.links.removeLink(cell.pressedLink);
                                            cell.pressedLink = new LinkSpanDrawable(link[0], cell.resourcesProvider, x, y, cell.spanSupportsLongPress(link[0]));
                                            cell.pressedLink.setColor(cell.getThemedColor(cell.currentMessageObject.isOutOwner() ? Theme.key_chat_outLinkSelectBackground : Theme.key_chat_linkSelectBackground));
                                            cell.linkBlockNum = a;
                                            cell.pressedLinkType = 4;
                                            cell.startCheckLongPress();
                                            try {
                                                LinkPath path = cell.pressedLink.obtainNewPath();
                                                int[] pos = cell.getRealSpanStartAndEnd(buffer, cell.pressedLink.getSpan());
                                                path.setCurrentLayout(button.title, pos[0], 0);
                                                button.title.getSelectionPath(pos[0], pos[1], path);
                                            } catch (Exception e) {
                                                FileLog.e(e);
                                            }
                                            cell.links.addLink(cell.pressedLink, 4);
                                        }
                                        result = true;
                                        cell.invalidate();
                                        break;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                    if (x >= button.x && x <= button.x + cell.backgroundWidth - dp(31) && y >= y2 && y <= y2 + button.height + dp(26)) {
                        cell.pressedVoteButton = a;
                        pressedVoteIsMedia = button.pollButtonDrawable != null && button.pollButtonDrawable.isHasMedia()
                            && x > button.x + cell.backgroundWidth - dp(31 + 36 + 9 + 5);
                        if (/*!pollVoted && !pollClosed &&*/ !pressedVoteIsMedia) {
                            cell.selectorDrawableMaskType[0] = 1;
//                            if (Build.VERSION.SDK_INT >= 21 && selectorDrawable[0] != null) {
//                                selectorDrawable[0].setBounds(button.x - dp(9), y2, button.x + backgroundWidth - dp(22), y2 + button.height + dp(26));
//                                selectorDrawable[0].setHotspot(x, y);
//                                selectorDrawable[0].setState(pressedState);
//                            }
                            button.selectorDrawable.setHotspot(x, y);
                            button.selectorDrawable.setState(cell.pressedState);
                            cell.invalidate();
                        }
                        result = true;
                        break;
                    }
                }
                if (!result && allowAdding && addButtonDrawable != null) {
                    cell.pollAddButtonPressed = addButtonDrawable.checkMotionPressed(x, y);
                    if (cell.pollAddButtonPressed != -1) {
                        result = true;
                    }
                }
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (cell.pollAddButtonPressed != PollAddButtonDrawable.MOTION_NONE) {
                cell.playSoundEffect(SoundEffectConstants.CLICK);
                if (cell.currentMessageObject != null && cell.delegate != null) {
                    if (cell.pollAddButtonPressed == PollAddButtonDrawable.MOTION_ALL) {
                        cell.delegate.didPressAddPollOptionButton(cell);
                    }
                }
                cell.pollAddButtonPressed = PollAddButtonDrawable.MOTION_NONE;
                resetPollButtonSelectors();
            } else if (cell.closeExplanationPressed) {
                cell.playSoundEffect(SoundEffectConstants.CLICK);
                cell.didPressVoteHint();
                cell.closeExplanationPressed = false;
                resetPollButtonSelectors();
            } else if (cell.pollHintPressed) {
                cell.playSoundEffect(SoundEffectConstants.CLICK);
                cell.didPressVoteHint();
                cell.pollHintPressed = false;
                cell.pollAddButtonPressed = -1;
                resetPollButtonSelectors();
            } else if (cell.pressedLink != null) {
                if (cell.pressedLink.getSpan() instanceof URLSpan) {
                    cell.delegate.didPressUrl(cell, cell.pressedLink.getSpan(), false);
                } else if (cell.pressedLink.getSpan() instanceof ClickableSpan) {
                    ((ClickableSpan) cell.pressedLink.getSpan()).onClick(cell);
                }
                cell.resetPressedLink(4);
            } else if (cell.pressedVoteButton != -1) {
                cell.playSoundEffect(SoundEffectConstants.CLICK);
                resetPollButtonSelectors();
                if (pressedVoteIsMedia) {
                    PollButton button = cell.pollButtons.get(cell.pressedVoteButton);
                    cell.didClickedPollImage(cell, button.pollButtonDrawable.getImageReceiver(), button.answer, button.answer.media, event.getX(), event.getY(), button.answer.unshuffled_index);
                } else if (cell.currentMessageObject.scheduled) {
                    Toast.makeText(cell.getContext(), getString(cell.currentMessageObject.isTodo() ? R.string.MessageScheduledTodo : R.string.MessageScheduledVote), Toast.LENGTH_LONG).show();
                } else {
                    PollButton button = cell.pollButtons.get(cell.pressedVoteButton);
                    TLRPC.PollAnswer answer = button.answer;
                    if (cell.pollVoted || cell.pollClosed) {
                        ArrayList<TLRPC.PollAnswer> answers = new ArrayList<>();
                        answers.add(answer);
                        cell.delegate.didLongPressPollOption(cell, answer);
                    } else if (hasVoteRestrictions) {
                        ArrayList<TLRPC.PollAnswer> answers = new ArrayList<>();
                        answers.add(answer);
                        cell.delegate.didPressVoteButtons(cell, answers, button.count, button.x + dp(50), button.y + cell.namesOffset);
                    } else {
                        if (cell.lastPoll != null && cell.lastPoll.multiple_choice) {
                            if (cell.currentMessageObject.checkedVotes.contains(answer)) {
                                cell.currentMessageObject.checkedVotes.remove(answer);
                                cell.pollCheckBox[cell.pressedVoteButton].setChecked(false, true);
                            } else {
                                cell.currentMessageObject.checkedVotes.add(answer);
                                cell.pollCheckBox[cell.pressedVoteButton].setChecked(true, true);
                            }
                            checkInstantButtonForPoll(true);
                        } else {
                            cell.pollVoteInProgressNum = cell.pressedVoteButton;
                            cell.pollVoteInProgress = true;
                            cell.voteCurrentProgressTime = 0.0f;
                            cell.firstCircleLength = true;
                            cell.voteCurrentCircleLength = 360;
                            cell.voteRisingCircleLength = false;
                            if (button.task != null) {
                                cell.toggleTodoCheck(cell.pressedVoteButton, true);
                            } else {
                                cell.vibrateOnPollVote = true;
                                ArrayList<TLRPC.PollAnswer> answers = new ArrayList<>();
                                answers.add(answer);
                                cell.delegate.didPressVoteButtons(cell, answers, -1, 0, 0);
                            }
                        }
                    }
                }
                cell.pressedVoteButton = -1;
                cell.invalidate();
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if ((cell.pressedVoteButton != -1 || cell.pollHintPressed) && cell.selectorDrawable[0] != null) {
                cell.selectorDrawable[0].setHotspot(x, y);
            }
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            resetPollButtonSelectors();
        }
        return result;
    }

    void resetPollButtonSelectors() {
        if (cell.pollButtons != null) {
            for (int a = 0; a < cell.pollButtons.size(); a++) {
                PollButton button = cell.pollButtons.get(a);
                button.selectorDrawable.setState(StateSet.NOTHING);
            }
        }
        if (addButtonDrawable != null) {
            addButtonDrawable.resetSelectors();
        }
    }

    boolean checkPollMediaMotionEvent(MotionEvent event) {
        if (cell.currentMessageObject == null || cell.currentMessageObject.type != MessageObject.TYPE_POLL || cell.currentMessageObject.isSending()) {
            return false;
        }
        final int action = event.getAction();
        final int x = (int) cell.getEventX(event);
        final int y = (int) cell.getEventY(event);
        boolean result = false;


        if (contentDrawable != null) {
            if (contentDrawable.isMusic() && contentDrawable.isPlaying()) {
                if (contentDrawable.seekBarOnTouch(action, x, y)) {
                    if (action == MotionEvent.ACTION_DOWN) {
                        cell.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    cell.disallowLongPress = true;
                    cell.invalidate();
                    return true;
                }
            }
            if (contentDrawable.isMusic() || contentDrawable.isFile()) {
                if (contentDrawable.miniButtonOnTouch(action, x, y)) {
                    cell.disallowLongPress = true;
                    cell.invalidate();
                    return true;
                }
            }
        }
        if (cell.currentMessageObject.expandedExplanation && explanationDrawable != null) {
            if (explanationDrawable.isMusic() && explanationDrawable.isPlaying()) {
                if (explanationDrawable.seekBarOnTouch(action, x, y)) {
                    if (action == MotionEvent.ACTION_DOWN) {
                        cell.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    cell.disallowLongPress = true;
                    cell.invalidate();
                    return true;
                }
            }
            if (explanationDrawable.isMusic() || explanationDrawable.isFile()) {
                if (explanationDrawable.miniButtonOnTouch(action, x, y)) {
                    cell.disallowLongPress = true;
                    cell.invalidate();
                    return true;
                }
            }
        }

        
        if (action == MotionEvent.ACTION_DOWN) {
            mediaPressedIndex = PollAttachedMediaPack.INDEX_NONE;
            if (contentDrawable != null && contentDrawable.isHasMedia() && contentDrawable.getBounds().contains(x, y)) {
                mediaPressedIndex = PollAttachedMediaPack.INDEX_DESCRIPTION;
                result = true;
                cell.invalidate();
            } else if (cell.currentMessageObject.expandedExplanation && explanationDrawable != null && explanationDrawable.isHasMedia() && explanationDrawable.getBounds().contains(x, y)) {
                mediaPressedIndex = PollAttachedMediaPack.INDEX_EXPLANATION;
                result = true;
                cell.invalidate();
            }
        } else if (action == MotionEvent.ACTION_MOVE) {
            result = mediaPressedIndex != PollAttachedMediaPack.INDEX_NONE;
        } else if (action == MotionEvent.ACTION_UP) {
            if (mediaPressedIndex != PollAttachedMediaPack.INDEX_NONE) {
                cell.playSoundEffect(SoundEffectConstants.CLICK);
                if (mediaPressedIndex == PollAttachedMediaPack.INDEX_DESCRIPTION) {
                    cell.didClickedPollImage(cell, contentDrawable.getImageReceiver(), null, contentDrawable.getMedia(), x, y, PollAttachedMediaPack.INDEX_DESCRIPTION);
                } else if (mediaPressedIndex == PollAttachedMediaPack.INDEX_EXPLANATION) {
                    cell.didClickedPollImage(cell, explanationDrawable.getImageReceiver(), null, explanationDrawable.getMedia(), x, y, PollAttachedMediaPack.INDEX_EXPLANATION);
                }
                mediaPressedIndex = PollAttachedMediaPack.INDEX_NONE;
                cell.invalidate();
                result = true;
            }
        }
        return result;
    }

    boolean checkExplanationMotionEvent(MotionEvent event) {
        if (!(currentExplanation instanceof Spannable) || cell.explanationLayout == null || cell.pressedBlock != -1 || cell.touchedByCaption) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_UP && (cell.pressedLinkType == 1 || cell.pressedCopyCode != null)) {
            int x = (int) cell.getEventX(event);
            int y = (int) cell.getEventY(event);
            if (x >= cell.lastDrawExplanationX && y >= cell.lastDrawExplanationY && x <= cell.lastDrawExplanationX + cell.explanationLayout.textWidth && y <= cell.lastDrawExplanationY + cell.explanationLayout.textHeight(cell.transitionParams)) {
                y -= cell.lastDrawExplanationY;
                int blockNum = 0;
                for (int a = 0; a < cell.explanationLayout.textLayoutBlocks.size(); a++) {
                    if (cell.explanationLayout.textLayoutBlocks.get(a).textYOffset(cell.explanationLayout.textLayoutBlocks, cell.transitionParams) > y) {
                        break;
                    }
                    blockNum = a;
                }
                try {
                    MessageObject.TextLayoutBlock block = cell.explanationLayout.textLayoutBlocks.get(blockNum);
                    x -= cell.lastDrawExplanationX - (block.isRtl() ? cell.explanationLayout.textXOffset : 0);
                    y -= block.textYOffset(cell.explanationLayout.textLayoutBlocks, cell.transitionParams);

                    final int line = block.textLayout.getLineForVertical(y);
                    final int off = block.charactersOffset + block.textLayout.getOffsetForHorizontal(line, x);

                    final float left = block.textLayout.getLineLeft(line);
                    if (left <= x && left + block.textLayout.getLineWidth(line) >= x) {
                        Spannable buffer = (Spannable) currentExplanation;
                        CharacterStyle[] link = buffer.getSpans(off, off, ClickableSpan.class);
                        boolean isMono = false;
                        if (link == null || link.length == 0) {
                            link = buffer.getSpans(off, off, URLSpanMono.class);
                            isMono = true;
                        }
                        if (link == null || link.length == 0) {
                            link = buffer.getSpans(off, off, AnimatedEmojiSpan.class);
                            isMono = false;
                        }
                        boolean ignore = false;
                        if (link.length == 0 || link[0] instanceof URLSpanBotCommand && !URLSpanBotCommand.enabled) {
                            ignore = true;
                        }
                        if (!ignore && !AndroidUtilities.isAccessibilityScreenReaderEnabled()) {
                            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                                if (link[0] instanceof AnimatedEmojiSpan) {
                                    if (cell.pressedEmoji == null || cell.pressedEmoji != link[0]) {
                                        cell.resetPressedLink(1);
                                        cell.pressedEmoji = (AnimatedEmojiSpan) link[0];
                                        cell.pressedLinkType = 1;
                                    }
                                } else if (cell.pressedLink == null || cell.pressedLink.getSpan() != link[0]) {
                                    cell.links.removeLink(cell.pressedLink);
                                    cell.pressedLink = new LinkSpanDrawable(link[0], cell.resourcesProvider, x, y, cell.spanSupportsLongPress(link[0]));
                                    cell.pressedLink.setColor(cell.getThemedColor(cell.currentMessageObject.isOutOwner() ? Theme.key_chat_outLinkSelectBackground : Theme.key_chat_linkSelectBackground));
                                    if (block.quote && cell.quoteLine != null) {
                                        cell.pressedLink.setColor(Theme.multAlpha(cell.quoteLine.getColor(), Theme.isCurrentThemeDark() ? .13f : .10f));
                                    }
                                    cell.linkExplanationBlockNum = blockNum;
                                    cell.linkBlockNum = -1;
                                    cell.pressedLinkType = 1;
                                    try {
                                        LinkPath path = cell.pressedLink.obtainNewPath();
                                        int[] pos = cell.getRealSpanStartAndEnd(buffer, cell.pressedLink.getSpan());
                                        pos[0] -= block.charactersOffset;
                                        pos[1] -= block.charactersOffset;
                                        path.setCurrentLayout(block.textLayout, pos[0], 0);
                                        block.textLayout.getSelectionPath(pos[0], pos[1], path);
                                        if (pos[1] >= block.charactersEnd) {
                                            for (int a = blockNum + 1; a < cell.explanationLayout.textLayoutBlocks.size(); a++) {
                                                MessageObject.TextLayoutBlock nextBlock = cell.explanationLayout.textLayoutBlocks.get(a);
                                                CharacterStyle[] nextLink;
                                                if (isMono) {
                                                    nextLink = buffer.getSpans(nextBlock.charactersOffset, nextBlock.charactersOffset, URLSpanMono.class);
                                                } else {
                                                    nextLink = buffer.getSpans(nextBlock.charactersOffset, nextBlock.charactersOffset, ClickableSpan.class);
                                                }
                                                if (nextLink == null || nextLink.length == 0 || nextLink[0] != cell.pressedLink.getSpan()) {
                                                    break;
                                                }
                                                path = cell.pressedLink.obtainNewPath();
                                                path.setCurrentLayout(nextBlock.textLayout, 0, nextBlock.textYOffset(cell.explanationLayout.textLayoutBlocks, cell.transitionParams) - block.textYOffset(cell.explanationLayout.textLayoutBlocks, cell.transitionParams));
                                                int p1 = pos[1] + block.charactersOffset - nextBlock.charactersOffset;
                                                nextBlock.textLayout.getSelectionPath(0, p1, path);
                                                if (p1 < nextBlock.charactersEnd - 1) {
                                                    break;
                                                }
                                            }
                                        }
                                        if (pos[0] <= block.charactersOffset) {
                                            int offsetY = 0;
                                            for (int a = blockNum - 1; a >= 0; a--) {
                                                MessageObject.TextLayoutBlock nextBlock = cell.explanationLayout.textLayoutBlocks.get(a);
                                                CharacterStyle[] nextLink;
                                                if (isMono) {
                                                    nextLink = buffer.getSpans(nextBlock.charactersEnd - 1, nextBlock.charactersEnd - 1, URLSpanMono.class);
                                                } else {
                                                    nextLink = buffer.getSpans(nextBlock.charactersEnd - 1, nextBlock.charactersEnd - 1, ClickableSpan.class);
                                                }
                                                if (nextLink == null || nextLink.length == 0 || nextLink[0] != cell.pressedLink.getSpan()) {
                                                    break;
                                                }
                                                path = cell.pressedLink.obtainNewPath();
                                                offsetY -= nextBlock.height;
                                                int p0 = pos[0] + block.charactersOffset - nextBlock.charactersOffset;
                                                int p1 = pos[1] + block.charactersOffset - nextBlock.charactersOffset;
                                                path.setCurrentLayout(nextBlock.textLayout, p0, offsetY);
                                                nextBlock.textLayout.getSelectionPath(p0, p1, path);
                                                if (p0 > nextBlock.charactersOffset) {
                                                    break;
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        FileLog.e(e);
                                    }
                                    cell.links.addLink(cell.pressedLink, 1);
                                }
                                cell.touchedByExpanation = true;
                                cell.invalidate();
                                return true;
                            } else {
                                if (link[0] instanceof AnimatedEmojiSpan && cell.pressedEmoji == link[0]) {
                                    if (cell.delegate.didPressAnimatedEmoji(cell, cell.pressedEmoji)) {
                                        cell.resetPressedLink(1);
                                        cell.pressedEmoji = null;
                                        return true;
                                    }
                                    cell.resetPressedLink(1);
                                    cell.pressedEmoji = null;
                                } else if (cell.pressedLink != null && link[0] == cell.pressedLink.getSpan()) {
                                    cell.delegate.didPressUrl(cell, cell.pressedLink.getSpan(), false);
                                    cell.resetPressedLink(1);
                                    return true;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else {
                cell.resetPressedLink(1);
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            cell.pressedCopyCode = null;
            cell.resetCodeSelectors();
        }

        return false;
    }

    void updatePollAnimations(long dt) {
        if (cell.pollVoteInProgress) {
            voteRadOffset += 360 * dt / 2000.0f;
            int count = (int) (voteRadOffset / 360);
            voteRadOffset -= count * 360;

            cell.voteCurrentProgressTime += dt;
            if (cell.voteCurrentProgressTime >= 500.0f) {
                cell.voteCurrentProgressTime = 500.0f;
            }
            if (cell.voteRisingCircleLength) {
                cell.voteCurrentCircleLength = 4 + 266 * AndroidUtilities.accelerateInterpolator.getInterpolation(cell.voteCurrentProgressTime / 500.0f);
            } else {
                cell.voteCurrentCircleLength = 4 - (cell.firstCircleLength ? 360 : 270) * (1.0f - AndroidUtilities.decelerateInterpolator.getInterpolation(cell.voteCurrentProgressTime / 500.0f));
            }
            if (cell.voteCurrentProgressTime == 500.0f) {
                if (cell.voteRisingCircleLength) {
                    voteRadOffset += 270;
                    cell.voteCurrentCircleLength = -266;
                }
                cell.voteRisingCircleLength = !cell.voteRisingCircleLength;
                if (cell.firstCircleLength) {
                    cell.firstCircleLength = false;
                }
                cell.voteCurrentProgressTime = 0;
            }
            cell.invalidate();
        }
        final boolean showHintButton = cell.hintButtonVisible && (cell.currentMessageObject == null || !cell.currentMessageObject.expandedExplanation);
        if (showHintButton && cell.hintButtonProgress < 1.0f) {
            cell.hintButtonProgress += dt / 180.0f;
            if (cell.hintButtonProgress > 1.0f) {
                cell.hintButtonProgress = 1.0f;
            }
            cell.invalidate();
        } else if (!showHintButton && cell.hintButtonProgress > 0.0f) {
            cell.hintButtonProgress -= dt / 180.0f;
            if (cell.hintButtonProgress < 0.0f) {
                cell.hintButtonProgress = 0.0f;
            }
            cell.invalidate();
        }
        if (animateAnswer) {
            cell.pollAnimationProgressTime += dt;
            if (cell.pollAnimationProgressTime >= 300.0f) {
                cell.pollAnimationProgressTime = 300.0f;
            }
            animationProgress = AndroidUtilities.decelerateInterpolator.getInterpolation(cell.pollAnimationProgressTime / 300.0f);
            if (animationProgress >= 1.0f) {
                animationProgress = 1.0f;
                animateAnswer = false;
                animateAnswerAlpha = false;
                cell.pollVoteInProgress = false;
                if (cell.pollUnvoteInProgress && cell.animatePollAvatars) {
                    if (recentVotersDrawable != null) {
                        recentVotersDrawable.set(null, false);
                    }
                }
                cell.pollUnvoteInProgress = false;
                for (int a = 0, N = cell.pollButtons.size(); a < N; a++) {
                    PollButton button = cell.pollButtons.get(a);
                    button.prevChosen = false;
                }
            }
            cell.invalidate();
        }
    }

    static void normalizePollPercents(boolean hasDifferent, int restPercent, ArrayList<PollButton> sortedPollButtons) {
        if (!hasDifferent || restPercent == 0 || sortedPollButtons == null || sortedPollButtons.isEmpty()) {
            return;
        }

        Collections.sort(sortedPollButtons, (o1, o2) -> {
            if (o1.decimal > o2.decimal) {
                return -1;
            } else if (o1.decimal < o2.decimal) {
                return 1;
            }
            if (o1.percent > o2.percent) {
                return 1;
            } else if (o1.percent < o2.percent) {
                return -1;
            }
            return 0;
        });
        for (int i = 0, n = sortedPollButtons.size(); i < n; i++) {
            if (restPercent <= 0) {
                break;
            }

            PollButton b = sortedPollButtons.get(i);
            if (b.percent > 0) {
                b.percent += 1;
                restPercent--;
            }
        }
    }

    void drawPollOverlays(Canvas canvas) {
        long newTime = System.currentTimeMillis();
        long dt = newTime - voteLastUpdateTime;
        if (dt > 17) {
            dt = 17;
        }
        voteLastUpdateTime = newTime;

        int color1;
        int color2;
        int linkColor;
        if (cell.currentMessageObject.isOutOwner()) {
            color1 = cell.getThemedColor(Theme.key_chat_messageTextOut);
            color2 = cell.getThemedColor(cell.isDrawSelectionBackground() ? Theme.key_chat_outTimeSelectedText : Theme.key_chat_outTimeText);
            linkColor = cell.getThemedColor(Theme.key_chat_messageLinkOut);
        } else {
            color1 = cell.getThemedColor(Theme.key_chat_messageTextIn);
            color2 = cell.getThemedColor(cell.isDrawSelectionBackground() ? Theme.key_chat_inTimeSelectedText : Theme.key_chat_inTimeText);
            linkColor = cell.getThemedColor(Theme.key_chat_messageLinkIn);
        }
        Theme.chat_audioTitlePaint.setColor(color1);
        Theme.chat_audioPerformerPaint.linkColor = linkColor;
        Theme.chat_audioPerformerPaint.setColor(color1);
        Theme.chat_instantViewPaint.setColor(color1);
        Theme.chat_timePaint.setColor(color2);
        Theme.chat_livePaint.setColor(color2);
        Theme.chat_locationAddressPaint.setColor(color2);

        canvas.save();

        final int targetExplanationHeight = cell.currentMessageObject.expandedExplanation ? explanationHeight : 0;
        final float visualExplanationHeight;
        float animateExplanationOffset = 0;
        if (cell.transitionParams.animateExpandedExplanation) {
            visualExplanationHeight = lerp(
                cell.transitionParams.expandedExplanationFrom ? explanationHeight : 0f,
                targetExplanationHeight,
                cell.transitionParams.animateChangeProgress);
            animateExplanationOffset = visualExplanationHeight - targetExplanationHeight;
            canvas.translate(0, animateExplanationOffset);
        } else {
            visualExplanationHeight = targetExplanationHeight;
        }

        if (cell.transitionParams.animateNamesOffset) {
            canvas.translate(0, -cell.namesOffset + lerp(cell.transitionParams.animateNamesOffsetFrom, cell.namesOffset, cell.transitionParams.animateChangeProgress));
        } else if (cell.transitionParams.animateForwardedLayout) {
            float y = cell.namesOffset * cell.transitionParams.animateChangeProgress + cell.transitionParams.animateForwardedNamesOffset * (1f - cell.transitionParams.animateChangeProgress);
            if (cell.currentMessageObject.needDrawForwarded()) {
                y -= cell.namesOffset;
            }
            canvas.translate(0, y);
        }
        if (cell.transitionParams.animateBackgroundBoundsInner) {
            canvas.translate(0, cell.transitionParams.deltaTop);
        }
        float x;
        if (cell.currentMessageObject.isOutOwner()) {
            x = cell.layoutWidth - cell.backgroundWidth + dp(11);
        } else {
            if (cell.isSideMenuEnabled) {
                x = dp(20 + ChatActivity.SIDE_MENU_WIDTH);
            } else if (cell.needDrawAvatar()) {
                x = dp(20 + 48);
            } else {
                x = dp(20);
            }
        }
        if (cell.transitionParams.animateBackgroundBoundsInner) {
            x += cell.transitionParams.deltaLeft;
        }
        float bwidth = cell.backgroundWidth;
        if (cell.transitionParams.animateBackgroundBoundsInner) {
            bwidth += cell.transitionParams.deltaRight - cell.transitionParams.deltaLeft;
        }

        final int pollButtonLeft = (int) (x - dp(9));
        final int pollButtonRight = (int) (x + bwidth - dp(22));

        if (visualExplanationHeight > dp(8)) {
            if (cell.quoteLine == null) {
                cell.quoteLine = new ReplyMessageLine(cell);
            }

            AndroidUtilities.rectTmp.set(0, dp(5), bwidth - dp(23 + 10), visualExplanationHeight - dp(3));
            AndroidUtilities.rectTmp.offset(x + dp(1), dp(10) + cell.namesOffset + contentHeightWithOffset + cell.captionHeight + targetExplanationHeight - visualExplanationHeight);
            cell.quoteLine.check(cell.currentMessageObject, cell.currentUser, cell.currentChat, cell.resourcesProvider, ReplyMessageLine.TYPE_LINK);
            cell.quoteLine.drawBackground(canvas, AndroidUtilities.rectTmp, 5, 5, 5, 1, true, false);
            cell.quoteLine.drawLine(canvas, AndroidUtilities.rectTmp, 1);
            if (cell.explanationLayout != null) {
                final int c = Theme.chat_quoteTextPaint.getColor();
                Theme.chat_quoteTextPaint.setColor(color1);
                canvas.save();
                canvas.clipRect(AndroidUtilities.rectTmp);
                if (explanationDrawable != null && explanationDrawable.isHasMedia()) {
                    final int dl = (int) AndroidUtilities.rectTmp.left + dp(10);
                    final int dt2 = (int) (AndroidUtilities.rectTmp.top + (explanationHeight - dp(8))) - explanationContentHeight - dp(8);
                    explanationDrawable.setBounds(dl, dt2, dl + explanationContentWidth, dt2 + explanationContentHeight);
                    explanationDrawable.checkColors(cell.currentMessageObject.isOut());
                    explanationDrawable.setColors(color2, color1, color2, cell.getThemedColor(cell.currentMessageObject.isOut() ? Theme.key_chat_outLoader : Theme.key_chat_inLoader));
                    explanationDrawable.draw(canvas);
                }
                if (explanationTitleLayout != null) {
                    final int color = Theme.chat_replyNamePaint.getColor();
                    Theme.chat_replyNamePaint.setColor(cell.quoteLine.getColor());
                    canvas.save();
                    canvas.translate(AndroidUtilities.rectTmp.left + dp(10), AndroidUtilities.rectTmp.top + dp(6));
                    explanationTitleLayout.draw(canvas);
                    canvas.restore();
                    Theme.chat_replyNamePaint.setColor(color);
                    if (closeExplanationDrawable != null) {
                        if (closeExplanationDrawableColor != cell.quoteLine.getColor()) {
                            closeExplanationDrawableColor = cell.quoteLine.getColor();
                            closeExplanationDrawable.setColorFilter(new PorterDuffColorFilter(closeExplanationDrawableColor, PorterDuff.Mode.SRC_IN));
                        }

                        cell.closeExplanationX = (int) AndroidUtilities.rectTmp.right - dp(16.33f);
                        cell.closeExplanationY = (int) AndroidUtilities.rectTmp.top + dp(16.33f);
                        canvas.save();
                        canvas.scale(0.85f, 0.85f, cell.closeExplanationX, cell.closeExplanationY);
                        final int xd = cell.closeExplanationX - closeExplanationDrawable.getIntrinsicWidth() / 2;
                        final int yd = cell.closeExplanationY - closeExplanationDrawable.getIntrinsicHeight() / 2;
                        closeExplanationDrawable.setBounds(xd, yd,
                            xd + closeExplanationDrawable.getIntrinsicWidth(),
                            yd + closeExplanationDrawable.getIntrinsicHeight()
                        );
                        closeExplanationDrawable.draw(canvas);
                        canvas.restore();
                    }
                }

                Theme.chat_explanationTextPaint.setColor(Theme.chat_quoteTextPaint.getColor());
                Theme.chat_explanationTextPaint.linkColor = linkColor;
                cell.drawMessageText(cell.lastDrawExplanationX = (AndroidUtilities.rectTmp.left + dp(10)), cell.lastDrawExplanationY = (AndroidUtilities.rectTmp.top + dp(25)), canvas, cell.explanationLayout.textLayoutBlocks, cell.explanationLayout.textXOffset, true, 1, true, false, false, true);
                cell.drawAnimatedEmojiMessageText(AndroidUtilities.rectTmp.left + dp(10), AndroidUtilities.rectTmp.top + dp(25), canvas, cell.explanationLayout.textLayoutBlocks, cell.animatedEmojiPollExplanation, true, 1, cell.explanationLayout.textXOffset, true);
                canvas.restore();
                Theme.chat_quoteTextPaint.setColor(c);
            }
        }

        int titleLayoutHeight = 0;
        if (cell.titleLayout != null) {
            canvas.save();
            canvas.translate(x + cell.getExtraTextX(),  dp(15) + cell.namesOffset + contentHeightWithOffset + cell.captionHeight + targetExplanationHeight);
            int oldAlpha = Theme.chat_audioTitlePaint.getAlpha();
            if (cell.transitionParams.animateTitleLayout != null) {
                Theme.chat_audioTitlePaint.setAlpha((int) (cell.transitionParams.animateChangeProgress * oldAlpha));
            }
            cell.titleLayout.draw(canvas);
            AnimatedEmojiSpan.drawAnimatedEmojis(canvas, cell.titleLayout, cell.animatedEmojiPollQuestion, 0, null, 0, 0, 0, cell.transitionParams.animateTitleLayout != null ? cell.transitionParams.animateChangeProgress : 1.0f, cell.getAdaptiveEmojiColorFilter(1, cell.titleLayout.getPaint().getColor()));
            if (cell.transitionParams.animateTitleLayout != null) {
                Theme.chat_audioTitlePaint.setAlpha(oldAlpha);
            }
            titleLayoutHeight = cell.titleLayout.getHeight();
            if (cell.transitionParams.animateTitleLayout != null) {
                Theme.chat_audioTitlePaint.setAlpha((int) ((1.0f - cell.transitionParams.animateChangeProgress) * oldAlpha));
                cell.transitionParams.animateTitleLayout.draw(canvas);
                Theme.chat_audioTitlePaint.setAlpha(oldAlpha);
                AnimatedEmojiSpan.drawAnimatedEmojis(canvas, cell.transitionParams.animateTitleLayout, cell.transitionParams.animateTitleLayoutEmoji, 0, null, 0, 0, 0, 1.0f - cell.transitionParams.animateChangeProgress, cell.getAdaptiveEmojiColorFilter(1, cell.transitionParams.animateTitleLayout.getPaint().getColor()));
                titleLayoutHeight = AndroidUtilities.lerp(cell.transitionParams.animateTitleLayout.getHeight(), titleLayoutHeight, cell.transitionParams.animateChangeProgress);
            }
            canvas.restore();
        }

        if (contentDrawable != null && contentDrawable.isHasMedia()) {
            final int y = dp(cell.namesOffset > 0 ? 11 : 5) + cell.namesOffset - (int) animateExplanationOffset;
            final int x1 = (int) x - dp(9) + dp(2);
            contentDrawable.setBounds(x1, y, x1 + contentWidth, y + contentHeight);
            contentDrawable.checkColors(cell.currentMessageObject.isOut());
            contentDrawable.setColors(color2, color1, color2, cell.getThemedColor(cell.currentMessageObject.isOut() ? Theme.key_chat_outLoader : Theme.key_chat_inLoader));
            contentDrawable.draw(canvas);
        }

        int y = titleLayoutHeight + dp(20) + cell.namesOffset + contentHeightWithOffset + cell.captionHeight + targetExplanationHeight;
        if (cell.docTitleLayout != null) {
            canvas.save();
            canvas.translate(x + cell.docTitleOffsetX + cell.getExtraTextX(), y);
            cell.docTitleLayout.draw(canvas);
            canvas.restore();

            TLRPC.MessageMedia m = MessageObject.getMedia(cell.currentMessageObject.messageOwner);
            if (m instanceof TLRPC.TL_messageMediaPoll) {
                TLRPC.TL_messageMediaPoll media = (TLRPC.TL_messageMediaPoll) m;
                if (media.poll.quiz && (cell.pollVoted || cell.pollClosed || cell.pollResultsPreview || (hasVoteRestrictions && (!hideResults || hasResults))) && !TextUtils.isEmpty(media.results.solution)) {
                    Drawable drawable = cell.getThemedDrawable(cell.currentMessageObject.isOutOwner() ? Theme.key_drawable_chat_pollHintDrawableOut : Theme.key_drawable_chat_pollHintDrawableIn);
                    if (cell.pollVoteInProgress) {
                        drawable.setAlpha((int) (255 * animationProgress));
                    } else {
                        drawable.setAlpha(255);
                    }
                    cell.pollHintX = (int) (x + bwidth - dp(23) - drawable.getIntrinsicWidth() - dp(8));
                    cell.pollHintY = y - dp(6);
                    int cx = cell.pollHintX + drawable.getIntrinsicWidth() / 2;
                    int cy = cell.pollHintY + drawable.getIntrinsicHeight() / 2;
                    float scale = cell.hintButtonVisible && cell.hintButtonProgress < 1 ? AnimationProperties.overshootInterpolator.getInterpolation(cell.hintButtonProgress) : cell.hintButtonProgress;
                    int w = (int) (drawable.getIntrinsicWidth() * scale);
                    int h = (int) (drawable.getIntrinsicHeight() * scale);
                    drawable.setBounds(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
                    drawable.draw(canvas);
                } else {
                    cell.pollHintX = -1;
                }
            } else {
                cell.pollHintX = -1;
            }

            if (recentVotersDrawable != null && !cell.isBot) {
                int toAdd;
                float ax;
                int lineLeft = (int) Math.ceil(cell.docTitleLayout.getLineLeft(0));
                if (cell.docTitleOffsetX != 0 || lineLeft != 0) {
                    toAdd = -dp(13);
                    if (cell.docTitleOffsetX != 0) {
                        ax = x + cell.docTitleOffsetX - dp(7 + 16) - cell.getExtraTextX();
                    } else {
                        ax = x + lineLeft - dp(7 + 16) - cell.getExtraTextX();
                    }
                } else {
                    toAdd = dp(13);
                    ax = x + (int) Math.ceil(cell.docTitleLayout.getLineWidth(0)) + dp(7) + cell.getExtraTextX();
                }
                if (cell.animatePollAvatars && animateAnswerAlpha) {
                    recentVotersDrawable.setAlpha((int) (255 * MathUtils.clamp(cell.pollUnvoteInProgress ? (1.0f - animationProgress) / 0.3f : animationProgress, 0f, 1.0f)));
                } else {
                    recentVotersDrawable.setAlpha(255);
                }
                recentVotersDrawable.setBounds((int) ax, y - dp(1), (int) (ax + recentVotersDrawable.getAnimatedWidth()), y - dp(1) + dp(16));
                recentVotersDrawable.draw(canvas, cell.currentBackgroundDrawable.getPaint());
            }
        }
        if ((!cell.pollClosed && !cell.pollVoted && !cell.pollResultsPreview || cell.pollVoteInProgress) && cell.lastPoll != null && cell.lastPoll.quiz && cell.lastPoll.close_period != 0 && cell.isBot) {
            long currentTime = ConnectionsManager.getInstance(cell.currentAccount).getCurrentTimeMillis();
            long time = Math.max(0, ((long) cell.lastPoll.close_date) * 1000 - currentTime);
            if (closeTimeText == null || lastCloseTime != time) {
                closeTimeText = AndroidUtilities.formatDurationNoHours((int) Math.ceil(time / 1000.0f), false);
                closeTimeWidth = (int) Math.ceil(Theme.chat_timePaint.measureText(closeTimeText));
                lastCloseTime = time;
            }
            if (time <= 0 && !cell.pollClosed) {
                if (cell.currentMessageObject.pollLastCheckTime + 1000 < SystemClock.elapsedRealtime()) {
                    cell.currentMessageObject.pollLastCheckTime = 0;
                }
                cell.delegate.needReloadPolls();
            }
            int tx = cell.currentBackgroundDrawable.getBounds().right - closeTimeWidth - dp(cell.currentMessageObject.isOutOwner() ? 40 : 34);
            if (time <= 5000) {
                Theme.chat_timePaint.setColor(cell.getThemedColor(cell.currentMessageObject.isOutOwner() ? Theme.key_chat_outPollWrongAnswer : Theme.key_chat_inPollWrongAnswer));
            }
            if (animateAnswer) {
                Theme.chat_timePaint.setAlpha((int) (255 * (1.0f - animationProgress)));
            }
            canvas.drawText(closeTimeText, tx, y + dp(11), Theme.chat_timePaint);
            Theme.chat_pollTimerPaint.setColor(Theme.chat_timePaint.getColor());
            tx += closeTimeWidth + dp(13);
            int rad = dp(5.1f);
            int ty = y + dp(6);
            if (time <= 60000) {
                cell.rect.set(tx - rad, ty - rad, tx + rad, ty + rad);
                float radProgress = -360 * (time / (Math.min(60, cell.lastPoll.close_period) * 1000.0f));
                canvas.drawArc(cell.rect, -90, radProgress, false, Theme.chat_pollTimerPaint);
                cell.timerParticles.draw(canvas, Theme.chat_pollTimerPaint, cell.rect, radProgress, cell.pollVoteInProgress ? (1.0f - animationProgress) : 1.0f);
            } else {
                canvas.drawCircle(tx, ty, rad, Theme.chat_pollTimerPaint);
            }
            if (time > 60000 || timerTransitionProgress != 0.0f) {
                Theme.chat_pollTimerPaint.setAlpha((int) (255 * timerTransitionProgress));
                canvas.drawLine(tx - dp(2.1f) * timerTransitionProgress, ty - dp(7.5f), tx + dp(2.1f) * timerTransitionProgress, ty - dp(7.5f), Theme.chat_pollTimerPaint);
                canvas.drawLine(tx, ty - dp(3) * timerTransitionProgress, tx, ty, Theme.chat_pollTimerPaint);
                if (time <= 60000) {
                    timerTransitionProgress -= dt / 180.0f;
                    if (timerTransitionProgress < 0) {
                        timerTransitionProgress = 0;
                    }
                }
            }
            cell.invalidate();
        }
        if (cell.selectorDrawable[0] != null && (cell.selectorDrawableMaskType[0] == 1 || cell.selectorDrawableMaskType[0] == 3)) {
            if (cell.selectorDrawableMaskType[0] == 3) {
                canvas.save();
                canvas.scale(cell.hintButtonProgress, cell.hintButtonProgress, cell.selectorDrawable[0].getBounds().centerX(), cell.selectorDrawable[0].getBounds().centerY());
            }
            cell.selectorDrawable[0].draw(canvas);
            if (cell.selectorDrawableMaskType[0] == 3) {
                canvas.restore();
            }
        }
        final boolean todo = cell.currentMessageObject.isTodo();
        final boolean multipleChoice = cell.lastPoll != null && cell.lastPoll.multiple_choice;
        final boolean todoAllowed = todo && cell.currentMessageObject.canCompleteTodo();
        final boolean isVotedButHiddenResults = cell.pollVoted && !hasResults && hideResults;
        final int buttonAlpha = (int) (animateAnswerAlpha && !isVotedButHiddenResults ? 255 * Math.min((cell.pollUnvoteInProgress ? 1.0f - animationProgress : animationProgress) / 0.3f, 1.0f) : 255);

        for (int a = Math.max(cell.firstVisiblePollButton, 0); a <= Math.min(cell.lastVisiblePollButton == -1 ? Integer.MAX_VALUE : cell.lastVisiblePollButton, cell.pollButtons.size() - 1); a++) {
            PollButton button = cell.pollButtons.get(a);
            button.x = (int) x;
            int by = button.y, bh = button.height;
            if (button.animateTitle != null) {
                by = AndroidUtilities.lerp(button.animateY, by, cell.transitionParams.animateChangeProgress);
                bh = AndroidUtilities.lerp(button.animateHeight, bh, cell.transitionParams.animateChangeProgress);
            }
            if (button.task != null && button.task.id == cell.doNotDrawTaskId) {
                continue;
            }
            if (cell.drawOnlyPollId != null && !Arrays.equals(button.answer.option, cell.drawOnlyPollId)) {
                continue;
            }
            if (button.answer != null && button.answer.option != null && Arrays.equals(button.answer.option, cell.doNotDrawPollId)) {
                continue;
            }
            canvas.save();
            int selectorColor = Theme.multAlpha(color2, 0.12f);
            if (button.selectorDrawableColor != selectorColor) {
                Theme.setSelectorDrawableColor(button.selectorDrawable, button.selectorDrawableColor = selectorColor, false);
            }
            button.selectorDrawable.setBounds((int) (x - dp(8)), (int) (by + cell.namesOffset - dp(13)), (int) (x + bwidth - dp(23)), (int) (by + cell.namesOffset + bh + dp(13)));
            button.selectorDrawable.draw(canvas);
            canvas.restore();

            if (button.pollButtonDrawable != null) {
                button.pollButtonDrawable.setBounds(
                        pollButtonLeft, by + cell.namesOffset - dp(13),
                        pollButtonRight, by + cell.namesOffset + bh + dp(13));
                button.pollButtonDrawable.setVotersCountTextColor(color1);
                button.pollButtonDrawable.draw(canvas, cell.currentBackgroundDrawable.getPaint());
            }

            canvas.save();
            button.titleX = x + dp(todoAllowed ? 48 : 35);
            button.titleY = by + cell.namesOffset;
            canvas.translate(x + dp(todoAllowed ? 48 : 35), by + cell.namesOffset);
            if (cell.transitionParams.animateBackgroundBoundsInner) {
                canvas.translate(0, cell.transitionParams.deltaTop);
            }
            int oldAlpha3 = Theme.chat_audioPerformerPaint.getAlpha();
            if (button.animateTitle != null) {
                Theme.chat_audioPerformerPaint.setAlpha((int) (oldAlpha3 * cell.transitionParams.animateChangeProgress));
            }
            canvas.save();
            if (todo && todoAllowed && button.author != null && cell.pollCheckBox[a] != null) {
                final float progress = cell.pollCheckBox[a].getProgress();
                canvas.translate(0, -dp(7.66f) * progress);
                button.titleY += -dp(7.66f) * progress;
            }
            if (a == cell.linkBlockNum && cell.links.draw(canvas, 4)) {
                cell.invalidate();
            }

            if (button.moveTitleByCounter && button.pollButtonDrawable != null) {
                canvas.translate(-button.pollButtonDrawable.getVotersCountAnimatedWidth(dp(7)), 0);
            }

            button.title.draw(canvas);
            AnimatedEmojiSpan.drawAnimatedEmojis(canvas, button.title, button.animatedEmoji, 0, null, 0, 0, 0, button.animateTitle != null ? cell.transitionParams.animateChangeProgress : 1.0f, cell.getAdaptiveEmojiColorFilter(1, button.title.getPaint().getColor()));
            if (button.animateTitle != null) {
                Theme.chat_audioPerformerPaint.setAlpha((int) (oldAlpha3 * (1.0f - cell.transitionParams.animateChangeProgress)));
                button.animateTitle.draw(canvas);
                AnimatedEmojiSpan.drawAnimatedEmojis(canvas, button.animateTitle, button.animateTitleEmoji, 0, null, 0, 0, 0, 1.0f - cell.transitionParams.animateChangeProgress, cell.getAdaptiveEmojiColorFilter(1, button.animateTitle.getPaint().getColor()));
                Theme.chat_audioPerformerPaint.setAlpha(oldAlpha3);
            }
            canvas.restore();
            if (todo && todoAllowed && button.author != null && cell.pollCheckBox[a] != null) {
                final float progress = cell.pollCheckBox[a].getProgress();
                if (progress > 0.0f) {
                    button.author
                        .ellipsize(bwidth - dp(8))
                        .draw(canvas, 0, button.title.getHeight() - dp(6) + button.author.getHeight() / 2f, color2, progress);
                }
            }
            Theme.chat_audioPerformerPaint.setAlpha(oldAlpha3);

            if (!todo && (cell.pollVoted && (hasResults || !hideResults) || cell.pollClosed || cell.pollResultsPreview || (hasVoteRestrictions && (!hideResults || hasResults)) || animateAnswerAlpha && !isVotedButHiddenResults)) {
                if (cell.lastPoll != null && cell.lastPoll.quiz/* && pollVoted*/) {
                    int key;
                    if (button.correct) {
                        key = cell.currentMessageObject.isOutOwner() ? Theme.key_chat_outPollCorrectAnswer : Theme.key_chat_inPollCorrectAnswer;
                    } else {
                        key = cell.currentMessageObject.isOutOwner() ? Theme.key_chat_outPollWrongAnswer : Theme.key_chat_inPollWrongAnswer;
                    }
                    if (!cell.currentBackgroundDrawable.hasGradient() || Theme.hasThemeKey(key)) {
                        Theme.chat_docBackPaint.setColor(cell.getThemedColor(key));
                    } else {
                        Theme.chat_docBackPaint.setColor(cell.getThemedColor(cell.currentMessageObject.isOutOwner() ? Theme.key_chat_outAudioSeekbarFill : Theme.key_chat_inAudioSeekbarFill));
                    }
                } else {
                    Theme.chat_docBackPaint.setColor(cell.getThemedColor(cell.currentMessageObject.isOutOwner() ? Theme.key_chat_outAudioSeekbarFill : Theme.key_chat_inAudioSeekbarFill));
                }
                if (animateAnswerAlpha && !isVotedButHiddenResults) {
                    float oldAlpha = Theme.chat_instantViewPaint.getAlpha() / 255.0f;
                    Theme.chat_instantViewPaint.setAlpha((int) (buttonAlpha * oldAlpha));
                    oldAlpha = Theme.chat_docBackPaint.getAlpha() / 255.0f;
                    Theme.chat_docBackPaint.setAlpha((int) (buttonAlpha * oldAlpha));
                }

                final boolean drawChosenCheckbox = button.chosen || button.prevChosen || cell.lastPoll != null && cell.lastPoll.quiz && button.correct && (cell.pollVoted || cell.pollClosed || cell.pollResultsPreview || (hasVoteRestrictions && (!hideResults || hasResults)));

                int currentPercent = MathUtils.clamp((int) Math.ceil(button.prevPercent + (button.percent - button.prevPercent) * animationProgress), 0, 100);
                String text = String.format("%d%%", currentPercent);
                int width = (int) Math.ceil(Theme.chat_instantViewPaint.measureText(text));
                canvas.drawText(text, -dp(6.5f) - width, dp(14) + (drawChosenCheckbox && button.title != null && button.title.getLineCount() < 2 ? cell.resultsPollButtonOffset : 0), Theme.chat_instantViewPaint);

                width = cell.backgroundWidth - dp(76);
                float currentPercentProgress = button.prevPercentProgress + (button.percentProgress - button.prevPercentProgress) * animationProgress;

                if (!isVotedButHiddenResults) {
                    final int lineColor = Theme.chat_docBackPaint.getColor();
                    final int lineAlpha = Color.alpha(lineColor);
                    final float lineWidth = width - (withMediaInAnswers ? dp(37.33f) : 0);
                    Theme.chat_docBackPaint.setColor(ColorUtils.setAlphaComponent(color1, 16 * lineAlpha / 255));
                    cell.rect.set(Math.max(lineWidth * currentPercentProgress - dp(2), 0), bh + dp(6), lineWidth, bh + dp(11));
                    canvas.drawRoundRect(cell.rect, dp(2), dp(2), Theme.chat_docBackPaint);
                    Theme.chat_docBackPaint.setColor(lineColor);

                    cell.rect.set(0, bh + dp(6), lineWidth * currentPercentProgress, bh + dp(11));
                    canvas.drawRoundRect(cell.rect, dp(2), dp(2), Theme.chat_docBackPaint);
                }

                if (drawChosenCheckbox) {
                    float cx = cell.rect.left - dp(15.5f);
                    float cy = cell.rect.centerY() + cell.resultsPollButtonOffset;
                    float s = dp(7);
                    float r = dp(multipleChoice ? 4.5f : 7);

                    canvas.saveLayerAlpha(cx - s, cy - s, cx + s, cy + s, buttonAlpha);
                    Drawable drawable;
                    if (cell.lastPoll != null && cell.lastPoll.quiz && !button.correct) {
                        drawable = Theme.chat_pollCrossDrawable[cell.currentMessageObject.isOutOwner() ? 1 : 0];
                    } else {
                        drawable = Theme.chat_pollCheckDrawable[cell.currentMessageObject.isOutOwner() ? 1 : 0];
                    }
                    cell.setDrawableBounds(drawable, cx - drawable.getIntrinsicWidth() / 2, cy - drawable.getIntrinsicHeight() / 2);
                    drawable.draw(canvas);

                    if (cell.srcOutPaint == null) {
                        cell.srcOutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        cell.srcOutPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OUT));
                    }
                    cell.srcOutPaint.setColor(Theme.chat_docBackPaint.getColor());
                    canvas.drawRoundRect(cx - s, cy - s, cx + s, cy + s, r, r, cell.srcOutPaint);
                    canvas.restore();
                }
            }


            if (todo || !cell.pollVoted && !cell.pollClosed && !cell.pollResultsPreview && !(hasVoteRestrictions && (!hideResults || hasResults)) || isVotedButHiddenResults || animateAnswerAlpha) {
                if (cell.isDrawSelectionBackground()) {
                    Theme.chat_replyLinePaint.setColor(cell.getThemedColor(cell.currentMessageObject.isOutOwner() ? Theme.key_chat_outVoiceSeekbarSelected : Theme.key_chat_inVoiceSeekbarSelected));
                } else {
                    Theme.chat_replyLinePaint.setColor(cell.getThemedColor(cell.currentMessageObject.isOutOwner() ? Theme.key_chat_outVoiceSeekbar : Theme.key_chat_inVoiceSeekbar));
                }
                if (animateAnswerAlpha && !isVotedButHiddenResults) {
                    float oldAlpha = Theme.chat_replyLinePaint.getAlpha() / 255.0f;
                    Theme.chat_replyLinePaint.setAlpha((int) ((255 - buttonAlpha) * oldAlpha));
                }
                canvas.drawLine(-dp(2), bh + dp(13), bwidth - dp(todoAllowed ? 71 : 58), bh + dp(13), Theme.chat_replyLinePaint);
                if (todo && !todoAllowed) {
                    if (button.chosen) {
                        if (checkPath == null) {
                            checkPath = new Path();
                            checkPath.moveTo(-dp(3.66f), dp(.16f));
                            checkPath.lineTo(dp(-1), dp(3));
                            checkPath.lineTo(dp(3.66f), dp(-3));
                        }
                        if (checkPaint == null) {
                            checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                            checkPaint.setStyle(Paint.Style.STROKE);
                            checkPaint.setStrokeCap(Paint.Cap.ROUND);
                            checkPaint.setStrokeJoin(Paint.Join.ROUND);
                        }
                        checkPaint.setStrokeWidth(dp(1.833f));
                        checkPaint.setColor(cell.getThemedColor(cell.currentMessageObject.isOutOwner() ? Theme.key_chat_outAudioSeekbarFill : Theme.key_chat_inAudioSeekbarFill));
                        canvas.save();
                        canvas.translate(-dp(todoAllowed ? 35 : 22), dp(9));
                        canvas.drawPath(checkPath, checkPaint);
                        canvas.restore();
                    } else {
                        Theme.chat_replyLinePaint.setColor(cell.getThemedColor(cell.currentMessageObject.isOutOwner() ? Theme.key_chat_outAudioSeekbarFill : Theme.key_chat_inAudioSeekbarFill));
                        canvas.drawCircle(-dp(todoAllowed ? 35 : 22), dp(9), dp(2.5f), Theme.chat_replyLinePaint);
                    }
                } else if (!todo && cell.pollVoteInProgress && a == cell.pollVoteInProgressNum && !isVotedButHiddenResults) {
                    Theme.chat_instantViewRectPaint.setColor(cell.getThemedColor(cell.currentMessageObject.isOutOwner() ? Theme.key_chat_outAudioSeekbarFill : Theme.key_chat_inAudioSeekbarFill));
                    if (animateAnswerAlpha && !isVotedButHiddenResults) {
                        float oldAlpha = Theme.chat_instantViewRectPaint.getAlpha() / 255.0f;
                        Theme.chat_instantViewRectPaint.setAlpha((int) ((255 - buttonAlpha) * oldAlpha));
                    }
                    cell.rect.set(-dp(todoAllowed ? 35 : 22) - dp(8.5f), dp(9) - dp(8.5f), -dp(23) + dp(8.5f), dp(9) + dp(8.5f));
                    canvas.drawArc(cell.rect, voteRadOffset, cell.voteCurrentCircleLength, false, Theme.chat_instantViewRectPaint);
                } else if (!(hasVoteRestrictions && (!hideResults || hasResults))) {
                    if (cell.currentMessageObject.isOutOwner()) {
                        Theme.chat_instantViewRectPaint.setColor(cell.getThemedColor(cell.isDrawSelectionBackground() ? Theme.key_chat_outMenuSelected : Theme.key_chat_outMenu));
                    } else {
                        Theme.chat_instantViewRectPaint.setColor(cell.getThemedColor(cell.isDrawSelectionBackground() ? Theme.key_chat_inMenuSelected : Theme.key_chat_inMenu));
                    }
                    if (animateAnswerAlpha && !isVotedButHiddenResults) {
                        float oldAlpha = Theme.chat_instantViewRectPaint.getAlpha() / 255.0f;
                        Theme.chat_instantViewRectPaint.setAlpha((int) ((255 - buttonAlpha) * oldAlpha));
                    }
                    {
                        final float cx = -dp(todoAllowed ? 35 : 22);
                        final float cy = dp(9);
                        final float r = dp(multipleChoice ? 8f : 8.5f);
                        if (multipleChoice) {
                            canvas.drawRoundRect(cx - r, cy - r, cx + r, cy + r,
                                dp(4), dp(4), Theme.chat_instantViewRectPaint);
                        } else {
                            canvas.drawCircle(cx, cy, r, Theme.chat_instantViewRectPaint);
                        }
                    }
                    if (multipleChoice || isVotedButHiddenResults || todo) {
                        int size = dp(multipleChoice ? 8f : 8.5f);
                        int key = Theme.key_checkboxCheck;
                        if (cell.currentMessageObject.isOutOwner()) {
                            if (cell.getThemedColor(key) == 0xffffffff) {
                                key = Theme.key_chat_outBubble;
                            }
                        }
                        cell.pollCheckBox[a].setColor(-1, cell.currentMessageObject.isOutOwner() ? Theme.key_chat_outAudioSeekbarFill : Theme.key_chat_inAudioSeekbarFill, key);
                        cell.pollCheckBox[a].setBounds(-dp(todoAllowed ? 35 : 22) - size / 2, dp(9) - size / 2, size, size);
                        cell.pollCheckBox[a].setCustomRadius(multipleChoice ? dp(5) : 0);
                        cell.pollCheckBox[a].setSize(multipleChoice ? 19 : 20);
                        cell.pollCheckBox[a].draw(canvas);
                        if (button.task != null && button.avatarImageReceiver != null) {
                            if (cutAvatarPaint == null) {
                                cutAvatarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                                cutAvatarPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                            }
                            final float avatarAlpha = cell.pollCheckBox[a].getProgress();
                            final float avatarScale = lerp(.5f, 1.f, avatarAlpha);
                            button.avatarImageReceiver.setImageCoords(-dp(todoAllowed ? 35 : 22) - size / 2 + dp(8), dp(9) - dp(20) / 2, dp(20), dp(20));
                            canvas.saveLayerAlpha(button.avatarImageReceiver.getImageX(), button.avatarImageReceiver.getImageY(), button.avatarImageReceiver.getImageX2(), button.avatarImageReceiver.getImageY2(), 0xFF, Canvas.ALL_SAVE_FLAG);
                            canvas.save();
                            canvas.scale(avatarScale, avatarScale, button.avatarImageReceiver.getImageX(), button.avatarImageReceiver.getCenterY());
                            button.avatarImageReceiver.setAlpha(avatarAlpha);
                            button.avatarImageReceiver.draw(canvas);
                            canvas.restore();
                            canvas.drawCircle(-dp(todoAllowed ? 35 : 22), dp(9), dp(11), cutAvatarPaint);
                            canvas.restore();
                        }
                    }
                }
            }
            canvas.restore();
        }
        int lastVoteY = 0;
        if (!cell.pollButtons.isEmpty()) {
            final PollButton btn = cell.pollButtons.get(cell.pollButtons.size() - 1);
            int by = btn.y, bh = btn.height;
            if (btn.animateTitle != null) {
                by = AndroidUtilities.lerp(btn.animateY, by, cell.transitionParams.animateChangeProgress);
                bh = AndroidUtilities.lerp(btn.animateHeight, bh, cell.transitionParams.animateChangeProgress);
            }
            lastVoteY = cell.namesOffset + by + bh;
        }

        final int pollAddButtonAnimatedHeight;
        if (allowAdding) {
            pollAddButtonAnimatedHeight = cell.transitionParams.animatePollAddOptionHeight ?
                lerp(cell.transitionParams.animatePollAddOptionHeightFrom, cell.pollAddButtonHeight, cell.transitionParams.animateChangeProgress) :
                cell.pollAddButtonHeight;
        } else {
            pollAddButtonAnimatedHeight = 0;
        }

        if (allowAdding && addButtonDrawable != null) {
            int by = lastVoteY + dp(13);
            int bh = dp(44.33f);
            addButtonDrawable.setTextColor((cell.lastPoll != null && !cell.lastPoll.public_voters && !cell.lastPoll.multiple_choice ? Theme.chat_livePaint : Theme.chat_locationAddressPaint).getColor());
            addButtonDrawable.setBounds(pollButtonLeft, by, pollButtonRight, by + bh);
            addButtonDrawable.draw(canvas);
            if (cell.delegate != null) {
                canvas.save();
                canvas.translate(pollButtonLeft, by);
                cell.delegate.drawPollMode(canvas, cell);
                canvas.restore();
            }

            if (!cell.pollVoted && !cell.pollClosed && !cell.pollResultsPreview || isVotedButHiddenResults || animateAnswerAlpha) {
                final int oldPaintColor = Theme.chat_replyLinePaint.getColor();
                if (cell.isDrawSelectionBackground()) {
                    Theme.chat_replyLinePaint.setColor(cell.getThemedColor(cell.currentMessageObject.isOutOwner() ? Theme.key_chat_outVoiceSeekbarSelected : Theme.key_chat_inVoiceSeekbarSelected));
                } else {
                    Theme.chat_replyLinePaint.setColor(cell.getThemedColor(cell.currentMessageObject.isOutOwner() ? Theme.key_chat_outVoiceSeekbar : Theme.key_chat_inVoiceSeekbar));
                }
                if (animateAnswerAlpha && !isVotedButHiddenResults) {
                    float oldAlpha = Theme.chat_replyLinePaint.getAlpha() / 255.0f;
                    Theme.chat_replyLinePaint.setAlpha((int) ((255 - buttonAlpha) * oldAlpha));
                }
                canvas.drawLine(x + dp(35) - dp(2), by + pollAddButtonAnimatedHeight, x + dp(35) + bwidth - dp(58), by + pollAddButtonAnimatedHeight, Theme.chat_replyLinePaint);
                Theme.chat_replyLinePaint.setColor(oldPaintColor);
            }
        }

        if (allowAdding) {
            lastVoteY += pollAddButtonAnimatedHeight;
        }

        final float progressVisibility = instantButtonDrawable != null ? instantButtonDrawable.getProgressFactor() : 0;
        final float timerVisibility = !todo && cell.animatedInfoLayout2 != null && cell.animatedInfoLayout2.isVisible() ? 1 : 0;
        final float instantViewVisibility = cell.drawInstantView ? 1 : 0;
        if (cell.drawInstantView) {
            int textX = cell.getCurrentBackgroundLeft() + dp(cell.currentMessageObject.isOutOwner() || cell.mediaBackground || cell.drawPinnedBottom ? 2 : 8);
            int instantY = lastVoteY + dp(13);
            if (cell.currentMessageObject.isOutOwner()) {
                Theme.chat_instantViewPaint.setColor(cell.getThemedColor(Theme.key_chat_outPreviewInstantText));
            } else {
                Theme.chat_instantViewPaint.setColor(cell.getThemedColor(Theme.key_chat_inPreviewInstantText));
            }

            cell.instantButtonRect.set(textX, instantY, textX + cell.instantWidth, instantY + dp(44));
            if (cell.selectorDrawable[0] != null && cell.selectorDrawableMaskType[0] == 2) {
                cell.selectorDrawable[0].setBounds(textX - dp(instantViewTouchesBottom ? 6 : 0), instantY, textX + cell.instantWidth, instantY + dp(44));
                cell.selectorDrawable[0].draw(canvas);
            }
            if (cell.currentMessageObject.isPoll() && instantButtonDrawable != null) {
                instantButtonDrawable.setButtonTextColor(cell.getThemedColor(cell.currentMessageObject.isOutOwner() ?
                        Theme.key_chat_outPreviewInstantText : Theme.key_chat_inPreviewInstantText));
                instantButtonDrawable.setBounds(pollButtonLeft, instantY, pollButtonRight, instantY + dp(44));
                instantButtonDrawable.setTextOffsetY(-dp(7) * timerVisibility);
                instantButtonDrawable.draw(canvas);
            } else if (cell.instantViewLayout != null) {
                canvas.save();
                canvas.translate(textX + cell.instantTextX, instantY + dp(14.5f));
                cell.instantViewLayout.draw(canvas);
                canvas.restore();
            }
        } else {
            if (cell.animatedInfoLayout != null) {
                final float lvy = lastVoteY + dp(13 + 6 + 6) + dp(6) * (1f - timerVisibility);
                if (cell.lastPoll != null && (cell.lastPoll.public_voters || cell.lastPoll.multiple_choice)) {
                   // lastVoteY += dp(6);
                }
                canvas.save();
                if (cell.transitionParams.animateBackgroundBoundsInner) {
                    canvas.translate(((cell.getBackgroundDrawableLeft() + cell.transitionParams.deltaLeft) + (cell.getBackgroundDrawableRight() + cell.transitionParams.deltaRight)) / 2.0f, lvy);
                } else {
                    canvas.translate((cell.getBackgroundDrawableLeft() + cell.getBackgroundDrawableRight()) / 2.0f, lvy);
                }
                cell.animatedInfoLayout.setBounds(-1, -1, 1, 1);
                cell.animatedInfoLayout.setTextColor((cell.lastPoll != null && !cell.lastPoll.public_voters && !cell.lastPoll.multiple_choice ? Theme.chat_livePaint : Theme.chat_locationAddressPaint).getColor());
                cell.animatedInfoLayout.draw(canvas);
                canvas.restore();
            }
        }
        if (!todo && cell.animatedInfoLayout2 != null) {
            final float lvy = lastVoteY + dp(13 + 6 + 6 + 16) + dp(6) * (1f - timerVisibility) + (dp(3) * instantViewVisibility);
            canvas.save();
            if (cell.transitionParams.animateBackgroundBoundsInner) {
                canvas.translate(((cell.getBackgroundDrawableLeft() + cell.transitionParams.deltaLeft) + (cell.getBackgroundDrawableRight() + cell.transitionParams.deltaRight)) / 2.0f, lvy);
            } else {
                canvas.translate((pollButtonLeft + pollButtonRight) / 2.0f, lvy);
            }
            cell.animatedInfoLayout2.setBounds(-1, -1, 1, 1);
            cell.animatedInfoLayout2.setTextColor((cell.lastPoll != null && !cell.lastPoll.public_voters && !cell.lastPoll.multiple_choice ? Theme.chat_livePaint : Theme.chat_locationAddressPaint).getColor());
            DrawableUtils.drawWithScale(canvas, cell.animatedInfoLayout2, 1f - progressVisibility);
            canvas.restore();
        }
        updatePollAnimations(dt);
        canvas.restore();

        if (progressVisibility >= 1f) {
            cell.checkPollVoteSendingStatus(true);
        }

        if (cell.quoteHighlight != null && (cell.quoteHighlight.todo || cell.quoteHighlight.poll)) {
            MessageDrawable backgroundDrawable = cell.currentBackgroundDrawable;
            if (backgroundDrawable != null) {
                canvas.save();
                canvas.clipPath(backgroundDrawable.makePath());
                cell.quoteHighlight.paint.setColor(Theme.multAlpha(cell.getThemedColor(cell.currentMessageObject != null && cell.currentMessageObject.isOutOwner() && !cell.currentMessageObject.preview ? Theme.key_chat_outReplyLine : Theme.key_chat_inReplyLine), Theme.isCurrentThemeDark() ? .2f : .2f));
                cell.quoteHighlight.draw(canvas, cell.textX, cell.textY, backgroundDrawable.getBounds(), cell.getHighlightAlpha(true));
                canvas.restore();
            }
        }
    }
}
