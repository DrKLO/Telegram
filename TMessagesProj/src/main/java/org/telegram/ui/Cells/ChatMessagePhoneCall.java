package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatPluralStringComma;
import static org.telegram.messenger.LocaleController.getString;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.view.Gravity;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarsDrawable;
import org.telegram.ui.Components.Text;
import java.util.ArrayList;
import java.util.HashSet;

class ChatMessagePhoneCall {

    private final ChatMessageCell cell;

    AvatarsDrawable participantsAvatars;
    Text participantsText;
    int conferenceDrawableColor;
    Drawable conferenceDrawable;

    ChatMessagePhoneCall(ChatMessageCell cell) {
        this.cell = cell;
    }

    void layoutCall(MessageObject messageObject, boolean drawAvatar) {
        cell.createSelectorDrawable(0);
        cell.drawName = false;
        cell.drawForwardedName = false;
        cell.drawPhotoImage = false;
        if (AndroidUtilities.isTablet()) {
            cell.backgroundWidth = Math.min(AndroidUtilities.getMinTabletSide() - dp(50 + (cell.isSideMenued ? ChatActivity.SIDE_MENU_WIDTH : drawAvatar ? 52 : 0)), dp(270));
        } else {
            cell.backgroundWidth = Math.min(cell.getParentWidth() - dp(50 + (cell.isSideMenued ? ChatActivity.SIDE_MENU_WIDTH : drawAvatar ? 52 : 0)), dp(270));
        }
        cell.availableTimeWidth = cell.backgroundWidth - dp(31);

        int maxWidth = cell.getMaxNameWidth() - dp(50);
        if (maxWidth < 0) {
            maxWidth = dp(10);
        }

        String time = LocaleController.getInstance().getFormatterDay().format((long) (messageObject.messageOwner.date) * 1000);
        final boolean isMissed, isBusy, video;
        final int duration;
        final ArrayList<TLRPC.Peer> other_participants;
        if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionPhoneCall) {
            final TLRPC.TL_messageActionPhoneCall call = (TLRPC.TL_messageActionPhoneCall) messageObject.messageOwner.action;
            isMissed = call.reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed;
            isBusy = call.reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy;
            video = call.video;
            duration = call.duration;
            other_participants = null;
        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionConferenceCall) {
            final TLRPC.TL_messageActionConferenceCall call = (TLRPC.TL_messageActionConferenceCall) messageObject.messageOwner.action;
            isMissed = call.missed;
            isBusy = !call.active;
            video = call.video;
            duration = call.duration;
            other_participants = call.other_participants;
        } else {
            isMissed = false;
            isBusy = false;
            video = false;
            duration = 0;
            other_participants = null;
        }
        String text = getCallMessageText(messageObject, isMissed, isBusy, video);

        if (duration > 0) {
            time += ", " + LocaleController.formatCallDuration(duration);
        }

        cell.titleLayout = new StaticLayout(TextUtils.ellipsize(text, Theme.chat_audioTitlePaint, maxWidth, TextUtils.TruncateAt.END), Theme.chat_audioTitlePaint, maxWidth + dp(2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        cell.docTitleLayout = new StaticLayout(TextUtils.ellipsize(time, Theme.chat_contactPhonePaint, maxWidth - dp(19), TextUtils.TruncateAt.END), Theme.chat_contactPhonePaint, maxWidth + dp(2), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        cell.docTitleWidth = (int) cell.docTitleLayout.getLineWidth(0);
        if (other_participants != null && other_participants.size() > 0) {
            if (participantsAvatars == null) {
                participantsAvatars = new AvatarsDrawable(cell, false);
                participantsAvatars.setSize(dp(14));
                participantsAvatars.setAvatarsTextSize(dp(18));
                participantsAvatars.width = dp(50);
                participantsAvatars.height = dp(16);
                if (cell.isCellAttachedToWindow()) {
                    participantsAvatars.onAttachedToWindow();
                }
            }
            final HashSet<Long> dialogIds = new HashSet();
//                    if (!currentMessageObject.isOutOwner()) {
                dialogIds.add(cell.currentMessageObject.getDialogId());
//                    }
            if (cell.currentMessageObject.isOutOwner()) {
                dialogIds.add(UserConfig.getInstance(cell.currentAccount).getClientUserId());
            }
            for (int i = 0; i < Math.min(3, other_participants.size()); ++i) {
                dialogIds.add(DialogObject.getPeerDialogId(other_participants.get(i)));
            }
            final int count = Math.min(3, dialogIds.size());
            participantsAvatars.setCount(count);
            int i = 0;
            for (long did : dialogIds) {
                participantsAvatars.setObject(i, cell.currentAccount, MessagesController.getInstance(cell.currentAccount).getUserOrChat(did));
                i++;
                if (i >= count) break;
            }
            participantsAvatars.commitTransition(false);
            participantsText = new Text(formatPluralStringComma("ConferenceCallOther", dialogIds.size()), 13);
        } else {
            participantsText = null;
        }
        final float timeWidth = cell.docTitleWidth + (participantsText == null ? 0 : participantsText.getCurrentWidth());
        if (timeWidth > (maxWidth - dp(19 + 30))) {
            final int needsMore = (int) (timeWidth - (maxWidth - dp(19 + 30)));
            cell.backgroundWidth += needsMore;
            cell.availableTimeWidth = cell.backgroundWidth - dp(31);
        }

        cell.setMessageObjectInternal(messageObject);

        cell.totalHeight = dp(65) + cell.namesOffset;
        if (cell.drawPinnedTop) {
            cell.namesOffset -= dp(1);
        }
        if (!cell.reactionsLayoutInBubble.isSmall) {
            if (!cell.reactionsLayoutInBubble.isEmpty) {
                cell.reactionsLayoutInBubble.measure(cell.backgroundWidth - dp(32), Gravity.LEFT);
                cell.reactionsLayoutInBubble.totalHeight = cell.reactionsLayoutInBubble.height;
                if (cell.backgroundWidth - dp(32) - cell.reactionsLayoutInBubble.lastLineX < timeWidth) {
                    cell.reactionsLayoutInBubble.totalHeight += dp(12);
                    cell.reactionsLayoutInBubble.positionOffsetY += -dp(12);
                }
                cell.totalHeight += dp(6) + cell.reactionsLayoutInBubble.totalHeight;
            }
        }
    }

    void drawCall(Canvas canvas) {
        Drawable icon;
        Drawable phone;
        int idx = cell.currentMessageObject.isVideoCall() ? 1 : 0;
        if (cell.currentMessageObject.isConferenceCall()) {
            if (cell.currentMessageObject.isOutOwner()) {
                icon = Theme.chat_msgCallUpGreenDrawable;
            } else {
                icon = Theme.chat_msgCallDownRedDrawable;
            }
            if (conferenceDrawable == null) {
                conferenceDrawable = cell.getContext().getResources().getDrawable(cell.currentMessageObject.isVideoCall() ? R.drawable.filled_video_group : R.drawable.filled_calls_users).mutate();
            }
            final int color = cell.getThemedColor(cell.currentMessageObject.isOutOwner() ? Theme.key_chat_outInstant : Theme.key_chat_inInstant);
            if (conferenceDrawableColor != color) {
                conferenceDrawable.setColorFilter(new PorterDuffColorFilter(conferenceDrawableColor = color, PorterDuff.Mode.SRC_IN));
            }
            phone = conferenceDrawable;
        } else if (cell.currentMessageObject.isOutOwner()) {
            icon = Theme.chat_msgCallUpGreenDrawable;
            if (cell.currentMessageObject.isVideoCall()) {
                phone = cell.getThemedDrawable(cell.isDrawSelectionBackground() ? Theme.key_drawable_msgOutCallVideoSelected : Theme.key_drawable_msgOutCallVideo);
            } else {
                phone = cell.getThemedDrawable(cell.isDrawSelectionBackground() ? Theme.key_drawable_msgOutCallAudioSelected : Theme.key_drawable_msgOutCallAudio);
            }
        } else {
            TLRPC.PhoneCallDiscardReason reason = cell.currentMessageObject.messageOwner.action.reason;
            if (reason instanceof TLRPC.TL_phoneCallDiscardReasonMissed || reason instanceof TLRPC.TL_phoneCallDiscardReasonBusy) {
                icon = Theme.chat_msgCallDownRedDrawable;
            } else {
                icon = Theme.chat_msgCallDownGreenDrawable;
            }
            phone = cell.isDrawSelectionBackground() ? Theme.chat_msgInCallSelectedDrawable[idx] : Theme.chat_msgInCallDrawable[idx];
        }

        if (cell.currentMessageObject.isOutOwner()) {
            Theme.chat_audioTitlePaint.setColor(cell.getThemedColor(Theme.key_chat_messageTextOut));
            Theme.chat_contactPhonePaint.setColor(cell.getThemedColor(cell.isDrawSelectionBackground() ? Theme.key_chat_outTimeSelectedText : Theme.key_chat_outTimeText));
        } else {
            Theme.chat_audioTitlePaint.setColor(cell.getThemedColor(Theme.key_chat_messageTextIn));
            Theme.chat_contactPhonePaint.setColor(cell.getThemedColor(cell.isDrawSelectionBackground() ? Theme.key_chat_inTimeSelectedText : Theme.key_chat_inTimeText));
        }
        cell.forceNotDrawTime = true;
        int x;
        if (cell.currentMessageObject.isOutOwner()) {
            x = cell.layoutWidth - cell.backgroundWidth + dp(16);
        } else {
            if (cell.needDrawAvatar()) {
                x = dp(74);
            } else {
                x = dp(25);
            }
        }
        cell.otherX = x;
        if (LocaleController.isRTL) {
            x += phone.getIntrinsicWidth() + dp(12);
        }
        if (cell.titleLayout != null) {
            canvas.save();
            canvas.translate(x, dp(12) + cell.namesOffset);
            cell.titleLayout.draw(canvas);
            canvas.restore();
        }
        if (cell.docTitleLayout != null) {
            canvas.save();
            canvas.translate(x + dp(LocaleController.isRTL ? -19 : 19), dp(37) + cell.namesOffset);
            cell.docTitleLayout.draw(canvas);
            canvas.restore();
        }
        if (participantsText != null && participantsAvatars != null) {
            canvas.save();
            canvas.translate(x + dp(LocaleController.isRTL ? -19 : 19) + cell.docTitleWidth + dp(6), dp(36) + cell.namesOffset);
            participantsAvatars.onDraw(canvas);
            canvas.translate(participantsAvatars.getMaxX() + dp(5), dp(8));
            participantsText.draw(canvas, 0, 0, Theme.chat_contactPhonePaint.getColor(), 1.0f);
            canvas.restore();
        }

        cell.setDrawableBounds(icon, x + (LocaleController.isRTL && cell.docTitleLayout != null ? cell.docTitleLayout.getWidth() - icon.getIntrinsicWidth() : 0) - dp(1), dp(37) + cell.namesOffset);
        icon.draw(canvas);

        if (cell.selectorDrawable[0] != null && cell.selectorDrawableMaskType[0] == 4) {
            cell.selectorDrawable[0].draw(canvas);
        }

        if (!cell.pinnedBottom && !cell.pinnedTop) {
            cell.otherY = dp(18.5f);
        } else if (cell.pinnedBottom && cell.pinnedTop) {
            cell.otherY = dp(18);
        } else if (!cell.pinnedBottom) {
            cell.otherY = dp(17);
        } else {
            cell.otherY = dp(19);
        }
        cell.setDrawableBounds(phone, cell.otherX + (LocaleController.isRTL ? 0 : cell.backgroundWidth - dp(70)) + dp((idx == 0 ? 1 : 0)), cell.otherY);
        phone.draw(canvas);
    }

    static String getCallMessageText(MessageObject messageObject, boolean isMissed, boolean isBusy, boolean video) {
        String text;
        if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionConferenceCall) {
            if (messageObject.isOutOwner()) {
                text = getString(R.string.ConferenceCallOutgoing);
            } else if (isMissed) {
                text = getString(R.string.ConferenceCallMissed);
            } else {
                text = getString(R.string.ConferenceCallIncoming);
            }
        } else if (messageObject.isOutOwner()) {
            if (isMissed) {
                if (video) {
                    text = getString(R.string.CallMessageVideoOutgoingMissed);
                } else {
                    text = getString(R.string.CallMessageOutgoingMissed);
                }
            } else {
                if (video) {
                    text = getString(R.string.CallMessageVideoOutgoing);
                } else {
                    text = getString(R.string.CallMessageOutgoing);
                }
            }
        } else {
            if (isMissed) {
                if (video) {
                    text = getString(R.string.CallMessageVideoIncomingMissed);
                } else {
                    text = getString(R.string.CallMessageIncomingMissed);
                }
            } else if (isBusy) {
                if (video) {
                    text = getString(R.string.CallMessageVideoIncomingDeclined);
                } else {
                    text = getString(R.string.CallMessageIncomingDeclined);
                }
            } else {
                if (video) {
                    text = getString(R.string.CallMessageVideoIncoming);
                } else {
                    text = getString(R.string.CallMessageIncoming);
                }
            }
        }
        return text;
    }
}
