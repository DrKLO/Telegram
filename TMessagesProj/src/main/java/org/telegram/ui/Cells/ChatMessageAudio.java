package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.view.Gravity;
import androidx.core.graphics.ColorUtils;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserObject;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.TranscribeButton;

class ChatMessageAudio {

    private final ChatMessageCell cell;

    float transcribeX, transcribeY;
    StaticLayout songLayout;
    int songX;
    StaticLayout performerLayout;
    int performerX;
    float toSeekBarProgress;

    ChatMessageAudio(ChatMessageCell cell) {
        this.cell = cell;
    }

    void setVoiceContent(MessageObject messageObject, boolean drawAvatar) {
        cell.drawForwardedName = !cell.isRepliesChat;
        cell.drawName = (
            cell.isSavedChat && !messageObject.isOutOwner() && (messageObject.getSavedDialogId() < 0 ||
            messageObject.getSavedDialogId() == UserObject.ANONYMOUS) ||
            messageObject.isFromGroup() && messageObject.isSupergroup() ||
            messageObject.isImportedForward() && messageObject.messageOwner.fwd_from.from_id == null ||
            cell.isSideMenuEnabled && !messageObject.isOutOwner() && (cell.isForum || cell.isMonoForum && cell.isAllChats)
        );
        int maxWidth;
        if (AndroidUtilities.isTablet()) {
            cell.backgroundWidth = maxWidth = Math.min(AndroidUtilities.getMinTabletSide() - dp(50 + (cell.isSideMenued ? ChatActivity.SIDE_MENU_WIDTH : drawAvatar ? 52 : 0)), dp(270));
        } else {
            cell.backgroundWidth = maxWidth = Math.min(cell.getParentWidth() - dp(50 + (cell.isSideMenued ? ChatActivity.SIDE_MENU_WIDTH : drawAvatar ? 52 : 0)), dp(270));
        }
        cell.createDocumentLayout(cell.backgroundWidth, messageObject);

        cell.setMessageObjectInternal(messageObject);

        cell.totalHeight = dp(70) + cell.namesOffset;
        if (cell.drawPinnedTop) {
            cell.namesOffset -= dp(1);
        }
        if (!cell.reactionsLayoutInBubble.isSmall) {
            cell.reactionsLayoutInBubble.measure(maxWidth - (messageObject.isOutOwner() ? dp(32) : dp(24)), Gravity.LEFT);
            if (!cell.reactionsLayoutInBubble.isEmpty) {
                cell.reactionsLayoutInBubble.totalHeight = cell.reactionsLayoutInBubble.height;
                if (TextUtils.isEmpty(messageObject.caption)) {
                    cell.reactionsLayoutInBubble.totalHeight += dp(12);
                } else {
                    cell.reactionsLayoutInBubble.totalHeight += dp(8);
                }
                if (cell.reactionsLayoutInBubble.width > cell.backgroundWidth) {
                    cell.backgroundWidth = cell.reactionsLayoutInBubble.width;
                }
                int timeMore = cell.timeWidth + dp(6);
                if (messageObject.isQuickReply() && !messageObject.isSendError()) {
                    timeMore -= dp(3);
                } else if (messageObject.isOutOwner()) {
                    timeMore += dp(20.5f);
                }
                timeMore += cell.getExtraTimeX();
                if (cell.reactionsLayoutInBubble.lastLineX + timeMore >= cell.backgroundWidth) {
                    cell.reactionsLayoutInBubble.totalHeight += dp(12);
                    cell.reactionsLayoutInBubble.positionOffsetY -= dp(12);
                }
                cell.totalHeight += cell.reactionsLayoutInBubble.totalHeight;
            }
        }
    }

    void setMusicContent(MessageObject messageObject, boolean drawAvatar) {
        cell.drawForwardedName = !cell.isRepliesChat && messageObject.messageOwner.fwd_from != null;
        cell.drawName = cell.isSavedChat && !messageObject.isOutOwner() && (messageObject.getSavedDialogId() < 0 || messageObject.getSavedDialogId() == UserObject.ANONYMOUS) || (messageObject.isFromGroup() && messageObject.isSupergroup() || messageObject.isImportedForward() && messageObject.messageOwner.fwd_from.from_id == null) && (cell.currentPosition == null || (cell.currentPosition.flags & MessageObject.POSITION_FLAG_TOP) != 0);
        int maxWidth;
        if (AndroidUtilities.isTablet()) {
            maxWidth = AndroidUtilities.getMinTabletSide() - dp(50 + (cell.isSideMenued ? ChatActivity.SIDE_MENU_WIDTH : drawAvatar ? 52 : 0));
            cell.backgroundWidth = Math.min(dp(270), maxWidth);
        } else {
            maxWidth = cell.getParentWidth() - dp(50 + (cell.isSideMenued ? ChatActivity.SIDE_MENU_WIDTH : drawAvatar ? 52 : 0));
            cell.backgroundWidth = Math.min(dp(270), maxWidth);
        }
        if (cell.currentPosition != null) {
            cell.backgroundWidth = maxWidth;
        }

        int durationWidth = cell.createDocumentLayout(maxWidth, messageObject);

        cell.setMessageObjectInternal(messageObject);

        cell.totalHeight = dp(82) + cell.namesOffset;
        if (dp(76) + durationWidth >= cell.backgroundWidth - cell.timeWidth - dp(12)) {
            cell.totalHeight += dp(14);
        }

        if (cell.currentPosition != null && cell.currentMessagesGroup != null && cell.currentMessagesGroup.messages.size() > 1) {
            if ((cell.currentPosition.flags & MessageObject.POSITION_FLAG_TOP) == 0) {
                cell.totalHeight -= dp(6);
                cell.mediaOffsetY -= dp(6);
            }
            if ((cell.currentPosition.flags & MessageObject.POSITION_FLAG_BOTTOM) == 0) {
                cell.totalHeight -= dp(6);
            }
        }
        if (cell.drawPinnedTop) {
            cell.namesOffset -= dp(1);
        }
        if (!cell.reactionsLayoutInBubble.isSmall) {
            cell.reactionsLayoutInBubble.measure(maxWidth - dp(24), Gravity.LEFT);
            if (!cell.reactionsLayoutInBubble.isEmpty) {
                cell.reactionsLayoutInBubble.totalHeight = cell.reactionsLayoutInBubble.height + dp(12);
                cell.measureTime(messageObject);
                if (cell.reactionsLayoutInBubble.width > cell.backgroundWidth) {
                    cell.backgroundWidth = cell.reactionsLayoutInBubble.width;
                }
                if (cell.reactionsLayoutInBubble.lastLineX + cell.timeWidth + dp(24) > cell.backgroundWidth) {
                    cell.reactionsLayoutInBubble.totalHeight += dp(12);
                    cell.reactionsLayoutInBubble.positionOffsetY -= dp(12);
                }
                if (!messageObject.isRestrictedMessage && messageObject.caption != null) {
                    cell.reactionsLayoutInBubble.positionOffsetY += dp(14);
                }
                cell.totalHeight += cell.reactionsLayoutInBubble.totalHeight;
            }
        }
    }

    void layoutMusic() {
        if (cell.currentMessageObject.isOutOwner()) {
            cell.seekBarX = cell.layoutWidth - cell.backgroundWidth + dp(56);
            cell.buttonX = cell.layoutWidth - cell.backgroundWidth + dp(14);
            cell.timeAudioX = cell.layoutWidth - cell.backgroundWidth + dp(67);
        } else {
            cell.seekBarX = dp(65);
            cell.buttonX = dp(23);
            cell.timeAudioX = dp(76);
            if (cell.isSideMenuLeftMargin()) {
                cell.seekBarX += dp(ChatActivity.SIDE_MENU_WIDTH);
                cell.buttonX += dp(ChatActivity.SIDE_MENU_WIDTH);
                cell.timeAudioX += dp(ChatActivity.SIDE_MENU_WIDTH);
            } else if (cell.needDrawAvatar()) {
                cell.seekBarX += dp(48);
                cell.buttonX += dp(48);
                cell.timeAudioX += dp(48);
            }
        }
        if (cell.hasLinkPreview) {
            cell.seekBarX += dp(10);
            cell.buttonX += dp(10);
            cell.timeAudioX += dp(10);
        }
        cell.updateSeekBarWaveformWidth(null);
        cell.seekBarY = dp(29) + cell.namesOffset + cell.mediaOffsetY + (cell.captionAbove && cell.captionLayout != null ? cell.captionLayout.textHeight(cell.transitionParams) : 0);
        cell.buttonY = dp(13) + cell.namesOffset + cell.mediaOffsetY + (cell.captionAbove && cell.captionLayout != null ? cell.captionLayout.textHeight(cell.transitionParams) : 0);
        cell.radialProgress.setProgressRect(cell.buttonX, cell.buttonY, cell.buttonX + dp(44), cell.buttonY + dp(44));

        cell.updatePlayingMessageProgress();
    }

    void drawMusic(Canvas canvas, float buttonX, float buttonY) {
        if (cell.currentMessageObject.isOutOwner()) {
            Theme.chat_audioTitlePaint.setColor(cell.getThemedColor(Theme.key_chat_outAudioTitleText));
            Theme.chat_audioPerformerPaint.setColor(cell.getThemedColor(cell.isDrawSelectionBackground() ? Theme.key_chat_outAudioPerformerSelectedText : Theme.key_chat_outAudioPerformerText));
            Theme.chat_audioTimePaint.setColor(cell.getThemedColor(cell.isDrawSelectionBackground() ? Theme.key_chat_outAudioDurationSelectedText : Theme.key_chat_outAudioDurationText));
            cell.radialProgress.setProgressColor(cell.getThemedColor(cell.isDrawSelectionBackground() || cell.buttonPressed != 0 ? Theme.key_chat_outAudioSelectedProgress : Theme.key_chat_outAudioProgress));
        } else if (cell.linkLine != null && cell.hasLinkPreview) {
            Theme.chat_audioTitlePaint.setColor(cell.linkLine.getColor());
            Theme.chat_audioPerformerPaint.setColor(Theme.adaptHue(cell.getThemedColor(cell.isDrawSelectionBackground() ? Theme.key_chat_inAudioPerformerSelectedText : Theme.key_chat_inAudioPerformerText), cell.linkLine.getColor()));
            Theme.chat_audioTimePaint.setColor(Theme.adaptHue(cell.getThemedColor(cell.isDrawSelectionBackground() ? Theme.key_chat_inAudioDurationSelectedText : Theme.key_chat_inAudioDurationText), cell.linkLine.getColor()));
            cell.radialProgress.setProgressColor(Theme.adaptHue(cell.getThemedColor(cell.isDrawSelectionBackground() || cell.buttonPressed != 0 ? Theme.key_chat_inAudioSelectedProgress : Theme.key_chat_inAudioProgress), cell.linkLine.getColor()));

            cell.seekBar.setColors(Theme.adaptHue(cell.getThemedColor(Theme.key_chat_inAudioSeekbar), cell.linkLine.getColor()), Theme.adaptHue(cell.getThemedColor(Theme.key_chat_inAudioCacheSeekbar), cell.linkLine.getColor()), Theme.adaptHue(cell.getThemedColor(Theme.key_chat_inAudioSeekbarFill), cell.linkLine.getColor()), Theme.adaptHue(cell.getThemedColor(Theme.key_chat_inAudioSeekbarFill), cell.linkLine.getColor()), Theme.adaptHue(cell.getThemedColor(Theme.key_chat_inAudioSeekbarSelected), cell.linkLine.getColor()));
        } else {
            Theme.chat_audioTitlePaint.setColor(cell.getThemedColor(Theme.key_chat_inAudioTitleText));
            Theme.chat_audioPerformerPaint.setColor(cell.getThemedColor(cell.isDrawSelectionBackground() ? Theme.key_chat_inAudioPerformerSelectedText : Theme.key_chat_inAudioPerformerText));
            Theme.chat_audioTimePaint.setColor(cell.getThemedColor(cell.isDrawSelectionBackground() ? Theme.key_chat_inAudioDurationSelectedText : Theme.key_chat_inAudioDurationText));
            cell.radialProgress.setProgressColor(cell.getThemedColor(cell.isDrawSelectionBackground() || cell.buttonPressed != 0 ? Theme.key_chat_inAudioSelectedProgress : Theme.key_chat_inAudioProgress));
        }
        float tx = 0;
        if (cell.transitionParams.animateBackgroundBoundsInner) {
            tx = cell.transitionParams.deltaLeft;
        }

        cell.radialProgress.setBackgroundDrawable(cell.isDrawSelectionBackground() ? cell.currentBackgroundSelectedDrawable : cell.currentBackgroundDrawable);
        cell.radialProgress.iconScale = 1f;
        if (cell.drawPhotoImage && cell.currentMessageObject != null && cell.currentMessageObject.hasMediaSpoilers() && cell.currentMessageObject.isSensitive() && (!cell.currentMessageObject.isMediaSpoilersRevealed || cell.mediaSpoilerRevealProgress != 0 && cell.mediaSpoilerRevealProgress < 1)) {
            cell.radialProgress.iconScale *= cell.mediaSpoilerRevealProgress;
        }
        cell.radialProgress.draw(canvas);

        canvas.save();
        canvas.translate(tx + cell.timeAudioX + songX, dp(13) + cell.namesOffset + cell.getMediaOffsetY());
        songLayout.draw(canvas);
        canvas.restore();

        boolean showSeekbar = MediaController.getInstance().isPlayingMessage(cell.currentMessageObject);
        if (showSeekbar && toSeekBarProgress != 1f) {
            toSeekBarProgress += 16f / 100f;
            if (toSeekBarProgress > 1f) {
                toSeekBarProgress = 1f;
            }
            cell.invalidate();
        } else if (!showSeekbar && toSeekBarProgress != 0) {
            toSeekBarProgress -= 16f / 100f;
            if (toSeekBarProgress < 0) {
                toSeekBarProgress = 0;
            }
            cell.invalidate();
        }
        if (toSeekBarProgress > 0) {
            float y = cell.seekBarY;
            if (cell.transitionParams.animateBackgroundBoundsInner) {
                y += cell.transitionParams.deltaTop;
            }
            if (toSeekBarProgress != 1f) {
                canvas.saveLayerAlpha(tx + cell.seekBarX, y, tx + cell.seekBarX + cell.seekBar.getWidth() + dp(24), y + dp(24), (int) (255 * (toSeekBarProgress)), Canvas.ALL_SAVE_FLAG);
            } else {
                canvas.save();
            }
            canvas.translate(tx + cell.seekBarX, y);
            cell.seekBar.draw(canvas);
            canvas.restore();
        }
        if (toSeekBarProgress < 1f) {
            float x = tx + cell.timeAudioX + performerX;
            float y = dp(35) + cell.namesOffset + cell.getMediaOffsetY();
            if (toSeekBarProgress != 0) {
                canvas.saveLayerAlpha(x, y, x + performerLayout.getWidth(), y + performerLayout.getHeight(), (int) (255 * (1f - toSeekBarProgress)), Canvas.ALL_SAVE_FLAG);
            } else {
                canvas.save();
            }
            if (toSeekBarProgress != 0) {
                float s = 0.7f + 0.3f * (1f - toSeekBarProgress);
                canvas.scale(s, s, x, y + performerLayout.getHeight() / 2f);
            }
            canvas.translate(x, y);
            performerLayout.draw(canvas);
            canvas.restore();
        }

        canvas.save();
        canvas.translate(tx + cell.timeAudioX, dp(57) + cell.namesOffset + cell.getMediaOffsetY());
        cell.durationLayout.draw(canvas);
        canvas.restore();

        if (cell.shouldDrawMenuDrawable()) {
            Drawable menuDrawable;
            if (cell.currentMessageObject.isOutOwner()) {
                menuDrawable = cell.getThemedDrawable(cell.isDrawSelectionBackground() ? Theme.key_drawable_msgOutMenuSelected : Theme.key_drawable_msgOutMenu);
            } else {
                menuDrawable = cell.isDrawSelectionBackground() ? Theme.chat_msgInMenuSelectedDrawable : Theme.chat_msgInMenuDrawable;
            }
            cell.setDrawableBounds(menuDrawable, cell.otherX = (int) buttonX + cell.backgroundWidth - dp(cell.currentMessageObject.type == MessageObject.TYPE_TEXT ? 58 : 48) - dp(cell.hasLinkPreview ? 18 : 0), cell.otherY = (int) buttonY - dp(2));
            if (cell.transitionParams.animateChangeProgress != 1f && cell.transitionParams.animateShouldDrawMenuDrawable) {
                menuDrawable.setAlpha((int) (255 * cell.transitionParams.animateChangeProgress));
            }
            menuDrawable.draw(canvas);
            if (cell.transitionParams.animateChangeProgress != 1f && cell.transitionParams.animateShouldDrawMenuDrawable) {
                menuDrawable.setAlpha(255);
            }
        }
    }

    void drawTranscribeButton(Canvas canvas, float transcribeAlpha, float playingRoundAlpha, int seekBarX) {
        canvas.save();
        int backgroundWidth = cell.getCurrentBackgroundRight() - cell.getCurrentBackgroundLeft() + dp(cell.pinnedBottom ? 6 : 0);
        int seekBarWidth = backgroundWidth - dp(92 + (cell.hasLinkPreview ? 10 : 0) + 36);
        if (cell.transcribeButton == null) {
            cell.transcribeButton = new TranscribeButton(cell, cell.seekBarWaveform) {
                @Override
                public void drawGradientBackground(Canvas canvas, Rect bounds, float alpha) {
                    Paint paint;
                    if (cell.currentMessageObject.shouldDrawWithoutBackground()) {
                        paint = cell.getThemedPaint(Theme.key_paint_chatActionBackground);
                    } else {
                        paint = cell.getThemedPaint(Theme.key_paint_chatTimeBackground);
                    }
                    int oldAlpha = paint.getAlpha();
                    paint.setAlpha((int) (oldAlpha * alpha));

                    cell.applyServiceShaderMatrix();
                    canvas.drawRect(bounds, paint);
                    if (paint == cell.getThemedPaint(Theme.key_paint_chatActionBackground) && cell.hasGradientService()) {
                        int oldAlpha2 = Theme.chat_actionBackgroundGradientDarkenPaint.getAlpha();
                        Theme.chat_actionBackgroundGradientDarkenPaint.setAlpha((int) (oldAlpha2 * alpha));
                        canvas.drawRect(bounds, Theme.chat_actionBackgroundGradientDarkenPaint);
                        Theme.chat_actionBackgroundGradientDarkenPaint.setAlpha(oldAlpha2);
                    }
                    paint.setAlpha(oldAlpha);
                }

                @Override
                protected void onOpen() {
                    cell.wasTranscriptionOpen = true;
                }
            };
            cell.transcribeButton.setOpen(cell.currentMessageObject.messageOwner != null && cell.currentMessageObject.messageOwner.voiceTranscriptionOpen && cell.currentMessageObject.messageOwner.voiceTranscriptionFinal && TranscribeButton.isVideoTranscriptionOpen(cell.currentMessageObject), false);
            cell.transcribeButton.setLoading(TranscribeButton.isTranscribing(cell.currentMessageObject), false);
            cell.transcribeButton.setLock(TranscribeButton.showTranscribeLock(cell.currentMessageObject), false);
        }
        if (cell.drawSideButton != 0) {
            transcribeX = AndroidUtilities.lerp(
                seekBarX + dp(13 + 8) + seekBarWidth,
                cell.sideStartX,
                1f - cell.getVideoTranscriptionProgress()
            );
            transcribeY = AndroidUtilities.lerp(
                cell.seekBarY + dp(3),
                cell.sideStartY - dp(32 + 8),
                1f - cell.getVideoTranscriptionProgress()
            );
        } else {
            transcribeX = AndroidUtilities.lerp(
                seekBarX + dp(13 + 8) + seekBarWidth,
                cell.currentMessageObject != null && cell.currentMessageObject.isOutOwner() ?
                    cell.getCurrentBackgroundLeft() - dp(32 + 8) + dp(28) * playingRoundAlpha :
                    cell.isCompactLayout() ?
                        // compact: next to the circle, also while it grows to its playing size
                        Math.min(cell.getPhotoImage().getImageX2() + dp(8), cell.getCompactRight() - dp(32)) :
                        cell.getCurrentBackgroundRight() + dp(8) - dp(40) * playingRoundAlpha,
                1f - cell.getVideoTranscriptionProgress()
            );
            float y = cell.layoutHeight + cell.transitionParams.deltaBottom - dp(28 - (cell.drawPinnedBottom ? 2 : 0));
            if (!cell.reactionsLayoutInBubble.isEmpty) {
                y -= cell.reactionsLayoutInBubble.getCurrentTotalHeight(cell.transitionParams.animateChangeProgress);
            }
            y = AndroidUtilities.lerp(y, dp(44) + cell.namesOffset + cell.getMediaOffsetY() - dp(1.7f), cell.getVideoTranscriptionProgress());
            y += dp(1.7f);
            transcribeY = AndroidUtilities.lerp(
                cell.seekBarY + dp(3),
                y - dp(12) - (cell.currentMessageObject.isOutOwner() ? 0 : dp(28) * playingRoundAlpha),
                1f - cell.getVideoTranscriptionProgress()
            );
        }
        cell.transcribeButton.setBounds(
            (int) transcribeX,
            (int) transcribeY,
            AndroidUtilities.lerp(dp(30), dp(32), 1f - cell.getVideoTranscriptionProgress()),
            AndroidUtilities.lerp(dp(24), dp(32), 1f - cell.getVideoTranscriptionProgress()),
            AndroidUtilities.lerp(dp(8), dp(16), 1f - cell.getVideoTranscriptionProgress())
        );
        cell.transcribeButton.setColor(
            ColorUtils.blendARGB(
                cell.getThemedColor(cell.isCompactLayout() ? Theme.key_chat_compact_sideButtonIcon : Theme.key_chat_serviceText),
                cell.getThemedColor(cell.currentMessageObject.isOutOwner() ? Theme.key_chat_outReactionButtonBackground : Theme.key_chat_inReactionButtonBackground),
                cell.documentAttachType == ChatMessageCell.DOCUMENT_ATTACH_TYPE_AUDIO ? 1 : cell.getVideoTranscriptionProgress()
            ),
            cell.getThemedColor(Theme.key_windowBackgroundWhiteGrayText),
            cell.currentMessageObject.isOut(),
            cell.documentAttachType == ChatMessageCell.DOCUMENT_ATTACH_TYPE_AUDIO ? 0 : 1f - cell.getVideoTranscriptionProgress()
        );
        cell.transcribeButton.draw(canvas, transcribeAlpha);
        canvas.restore();
    }

    void drawVoiceDuration(Canvas canvas, int timeAudioX, float yoffset) {
        canvas.save();
        canvas.translate(timeAudioX, dp(44) + cell.namesOffset + cell.getMediaOffsetY() + yoffset);
        cell.durationLayout.draw(canvas);
        canvas.restore();

        if (cell.currentMessageObject.type != MessageObject.TYPE_TEXT && cell.currentMessageObject.isContentUnread()) {
            Theme.chat_docBackPaint.setColor(cell.getThemedColor(cell.currentMessageObject.isOutOwner() ? Theme.key_chat_outVoiceSeekbarFill : Theme.key_chat_inVoiceSeekbarFill));
            canvas.drawCircle(timeAudioX + cell.timeWidthAudio + dp(6), dp(51) + cell.namesOffset + cell.getMediaOffsetY(), dp(3), Theme.chat_docBackPaint);
        }
    }
}
