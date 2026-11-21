package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.StateSet;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.ViewConfiguration;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotInlineKeyboard;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.tlutils.TLKeyboardHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_keyboard;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Stars.StarsIntroActivity;

import java.util.ArrayList;
import java.util.HashMap;

class ChatMessageBotButtons {

    private final ChatMessageCell cell;

    private Path path = new Path();
    private float[] radii = new float[8];
    HashMap<String, BotButton> buttonsByData = new HashMap<>();
    HashMap<String, BotButton> buttonsByPosition = new HashMap<>();
    String buttonsLayout;

    private Paint buttonPaint;

    ChatMessageBotButtons(ChatMessageCell cell) {
        this.cell = cell;
    }

    void layoutButtons(MessageObject messageObject, BotInlineKeyboard.Source inlineButtons, int separatorHeight, boolean drawAvatar) {
        int rows, separators;

        if (inlineButtons != null) {
            rows = inlineButtons.getRowsCount();
            separators = 0;
            for (int a = 0; a < rows; a++) {
                if (inlineButtons.hasSeparator(a)) {
                    separators++;
                }
            }
        } else {
            rows = 1;
            separators = 0;
        }
        cell.substractBackgroundHeight = cell.keyboardHeight = dp(44 + 4) * rows + dp(1) + separatorHeight * separators;
        cell.widthForButtons = cell.backgroundWidth - dp(cell.mediaBackground ? 0 : 9);
        boolean fullWidth = false;
        if (messageObject.wantedBotKeyboardWidth > cell.widthForButtons) {
            int maxButtonWidth = -dp(10 + (cell.isSideMenued ? ChatActivity.SIDE_MENU_WIDTH : drawAvatar ? 52 : 0));
            if (AndroidUtilities.isTablet()) {
                maxButtonWidth += AndroidUtilities.getMinTabletSide();
            } else {
                maxButtonWidth += Math.min(cell.getParentWidth(), AndroidUtilities.displaySize.y) - dp(5);
            }
            cell.widthForButtons = Math.max(cell.backgroundWidth, Math.min(messageObject.wantedBotKeyboardWidth, maxButtonWidth));
        }
        if (cell.isCompactLayout()) {
            // compact: under bare media the buttons match the media, otherwise they span the text width
            // buttons fill widthForButtons minus 2dp, so add it back to end exactly at the target edge
            cell.widthForButtons = (cell.mediaBackground ? cell.backgroundWidth - dp(8) : cell.getCompactContentWidth()) + dp(2);
        }

        int maxButtonsWidth = 0;
        HashMap<String, BotButton> oldByData = new HashMap<>(buttonsByData);
        HashMap<String, BotButton> oldByPosition;
        if (messageObject.botButtonsLayout != null && buttonsLayout != null && buttonsLayout.equals(messageObject.botButtonsLayout.toString())) {
            oldByPosition = new HashMap<>(buttonsByPosition);
        } else {
            if (messageObject.botButtonsLayout != null) {
                buttonsLayout = messageObject.botButtonsLayout.toString();
            }
            oldByPosition = null;
        }
        buttonsByData.clear();
        if (inlineButtons != null) {
            int separatorsHeight = 0;
            for (int row = 0; row < rows; row++) {
                int buttonsCount = inlineButtons.getColumnsCount(row);
                if (buttonsCount == 0) {
                    continue;
                }
                int buttonWidth = (cell.widthForButtons - dp(5) * (buttonsCount - 1) - dp(2)) / buttonsCount;
                for (int column = 0; column < buttonsCount; column++) {
                    BotInlineKeyboard.Button inlineButton = inlineButtons.getButton(row, column);
                    BotButton botButton = new BotButton(cell::invalidateOutbounds);
                    botButton.buttonImpl = inlineButton;
                    if (inlineButton instanceof BotInlineKeyboard.ButtonBot) {
                        botButton.button = ((BotInlineKeyboard.ButtonBot) inlineButton).button;
                    } else if (inlineButton instanceof BotInlineKeyboard.ButtonCustom) {
                        botButton.buttonCustom = (BotInlineKeyboard.ButtonCustom) inlineButton;
                        if (cell.currentMessageObject != null && ChatObject.canManageMonoForum(cell.currentAccount, cell.currentMessageObject.getDialogId()) && !ChatObject.canUserDoChannelDirectAdminAction(cell.currentAccount, cell.currentMessageObject.getDialogId(), ChatObject.ACTION_POST) && (botButton.buttonCustom.id == BotInlineKeyboard.ButtonCustom.SUGGESTION_ACCEPT || botButton.buttonCustom.id == BotInlineKeyboard.ButtonCustom.SUGGESTION_EDIT)) {
                            botButton.isLocked = true;
                        }
                    }

                    final long emojiId = inlineButton.getIconEmoji();
                    final int iconRes = inlineButton.getIconRes();
                    if (emojiId != 0) {
                        botButton.animatedEmojiDrawable = new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, cell.currentAccount, emojiId);
                        botButton.animatedEmojiDrawable.addView(cell::invalidateOutbounds);
                        botButton.animatedEmojiDrawable.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
                    } else if (iconRes != 0) {
                        botButton.iconDrawable = cell.getResources().getDrawable(iconRes);
                        botButton.iconDrawable.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
                    }

                    String key = botButton.button != null ? Utilities.bytesToHex(botButton.button.getData()) : "";
                    String position = row + "_" + column;
                    BotButton oldButton;
                    if (oldByPosition != null) {
                        oldButton = oldByPosition.get(position);
                    } else {
                        oldButton = oldByData.get(key);
                    }
                    if (oldButton != null) {
                        botButton.progressAlpha = oldButton.progressAlpha;
                        botButton.angle = oldButton.angle;
                        botButton.lastUpdateTime = oldButton.lastUpdateTime;
                    } else {
                        botButton.lastUpdateTime = System.currentTimeMillis();
                    }
                    buttonsByData.put(key, botButton);
                    buttonsByPosition.put(position, botButton);
                    botButton.x = (float) column * (buttonWidth + dp(5)) / cell.widthForButtons;
                    botButton.y = row * dp(44 + 4) + dp(2.5f) + separatorsHeight;
                    botButton.width = (float) buttonWidth / cell.widthForButtons;
                    botButton.height = dp(44);
                    if (column == 0) {
                        botButton.positionFlags |= MessageObject.POSITION_FLAG_LEFT;
                    }
                    if (column == buttonsCount - 1) {
                        botButton.positionFlags |= MessageObject.POSITION_FLAG_RIGHT;
                    }
                    if (row == 0) {
                        botButton.positionFlags |= MessageObject.POSITION_FLAG_TOP;
                    }
                    if (row == rows - 1) {
                        botButton.positionFlags |= MessageObject.POSITION_FLAG_BOTTOM;
                    }
                    CharSequence buttonText;
                    TextPaint buttonPaint = (TextPaint) cell.getThemedPaint(Theme.key_paint_chatBotButton);
                    if (TLKeyboardHelper.isType(botButton.button, TL_keyboard.TL_inlineButtonTypeBuy.class) && (MessageObject.getMedia(messageObject.messageOwner).flags & 4) != 0) {
                        buttonText = getString(R.string.PaymentReceipt);
                    } else {
                        buttonText = inlineButton.getText();
                    }

                    if (inlineButton instanceof BotInlineKeyboard.ButtonCustom) {
                        if (((BotInlineKeyboard.ButtonCustom) inlineButton).id == BotInlineKeyboard.ButtonCustom.OPEN_MESSAGE_THREAD) {
                            buttonText = new SpannableStringBuilder(buttonText).append(">");
                            final ColoredImageSpan span = new ColoredImageSpan(R.drawable.msg_inputarrow);
                            span.setScale(.8f, .8f);
                            span.setAlpha(0.6f);
                            ((SpannableStringBuilder) buttonText).setSpan(span, buttonText.length() - 1, buttonText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    }

                    if (TLKeyboardHelper.isType(botButton.button, TL_keyboard.TL_inlineButtonTypeBuy.class) && MessageObject.getMedia(messageObject.messageOwner) instanceof TLRPC.TL_messageMediaInvoice) {
                        buttonText = StarsIntroActivity.replaceStars(buttonText);
                    }
                    if (TLKeyboardHelper.isType(botButton.button, TL_keyboard.TL_inlineButtonTypeCopy.class)) {
                        buttonText = new SpannableStringBuilder("c ").append(buttonText);
                        final ColoredImageSpan span = new ColoredImageSpan(R.drawable.menu_copy_s);
                        span.setScale(.9f, .9f);
                        ((SpannableStringBuilder) buttonText).setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    buttonText = Emoji.replaceEmoji(buttonText, buttonPaint.getFontMetricsInt(), false);
                    if (oldButton != null && oldButton.title != null && TextUtils.equals(oldButton.title.getText(), buttonText)) {
                        botButton.title = oldButton.title;
                    } else {
                        botButton.title = new Text(buttonText, buttonPaint);
                    }
                    cell.botButtons.add(botButton);
                    if (column == buttonsCount - 1) {
                        maxButtonsWidth = Math.max(maxButtonsWidth, (int) (botButton.x * cell.widthForButtons) + (int) (botButton.width * cell.widthForButtons));
                    }
                    final TL_keyboard.TL_inlineButtonTypeUrl buttonTypeUrl = TLKeyboardHelper.getType(botButton.button, TL_keyboard.TL_inlineButtonTypeUrl.class);
                    if (messageObject.isFromUser() && buttonTypeUrl != null) {
                        try {
                            final Uri uri = Uri.parse(buttonTypeUrl.url);
                            final String host = uri.getHost().toLowerCase();
                            botButton.isInviteButton = (uri.getQueryParameter("startgroup") != null && (
                                    ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && ("t.me".equals(host) || "telegram.me".equals(host) || "telegram.dog".equals(host)) ||
                                            "tg".equals(uri.getScheme()) && (buttonTypeUrl.url.startsWith("tg:resolve") || buttonTypeUrl.url.startsWith("tg://resolve"))
                            ));
                        } catch (Exception ignore) {
                        }
                    }
                }
                if (inlineButtons.hasSeparator(row)) {
                    BotButton botButton = new BotButton(cell::invalidateParentForce);
                    botButton.isSeparator = true;
                    botButton.y = row * dp(44 + 4) + dp(2.5f) + separatorsHeight + dp(44 + 5);
                    botButton.height = dp(2);
                    botButton.width = (float) (cell.widthForButtons - dp(2)) / cell.widthForButtons;;
                    cell.botButtons.add(botButton);

                    separatorsHeight += separatorHeight;
                }
            }
        }
        if (!cell.isCompactLayout()) {
            // button positions are fractions of the width they were laid out in; shrinking it would pull the right edge in
            cell.widthForButtons = maxButtonsWidth;
        }
    }

    boolean checkMotionEvent(MotionEvent event) {
        if (cell.botButtons.isEmpty()) {
            return false;
        }

        int x = (int) cell.getEventX(event);
        int y = (int) cell.getEventY(event);

        boolean result = false;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            final int widthForButtons = cell.getWidthForButtons();
            int addX;
            if (cell.currentMessageObject.isOutOwner()) {
                addX = cell.getMeasuredWidth() - widthForButtons - dp(10);
            } else if (cell.isCompactLayout()) {
                addX = cell.getCompactLeft();
            } else {
                addX = cell.backgroundDrawableLeft + dp(cell.mediaBackground ? 1 : 7);
            }
            for (int a = 0; a < cell.botButtons.size(); a++) {
                BotButton button = cell.botButtons.get(a);
                int y2 = button.y + cell.layoutHeight - dp(2);
                if (x >= button.x * widthForButtons + addX && x <= button.x * widthForButtons + addX + button.width * widthForButtons && y >= y2 && y <= y2 + button.height) {
                    cell.pressedBotButton = a;
                    cell.invalidateOutbounds();
                    result = true;
                    if (button.selectorDrawable == null) {
                        button.selectorDrawable = Theme.createRadSelectorDrawable(cell.getThemedColor(Theme.key_chat_serviceBackgroundSelector), 6, 6);
                        button.selectorDrawable.setCallback(cell);
                        button.selectorDrawable.setBounds((int) (button.x * widthForButtons) + addX, y2, (int) (button.x * widthForButtons) + addX + (int) (button.width * widthForButtons), y2 + button.height);
                    }
                    button.selectorDrawable.setHotspot(x, y);
                    button.selectorDrawable.setState(cell.pressedState);
                    button.setPressed(!button.isLocked);
                    final int longPressedBotButton = cell.pressedBotButton;
                    cell.postDelayed(() -> {
                        if (longPressedBotButton == cell.pressedBotButton) {
                            BotButton button2 = cell.botButtons.get(cell.pressedBotButton);
                            if (button2 != null) {
                                if (button2.selectorDrawable != null) {
                                    button2.selectorDrawable.setState(StateSet.NOTHING);
                                }
                                button2.setPressed(false);
                                if (!cell.currentMessageObject.scheduled) {
                                    if (button2.buttonCustom != null) {
                                        cell.cancelCheckLongPress();
                                        if (cell.delegate != null) {
                                            cell.delegate.didLongPressCustomBotButton(cell, button2.buttonCustom);
                                        }
                                    } else if (button2.button != null) {
                                        cell.cancelCheckLongPress();
                                        if (cell.delegate != null) {
                                            cell.delegate.didLongPressBotButton(cell, button2.button);
                                        }
                                    }
                                }
                            }
                            cell.pressedBotButton = -1;
                            cell.invalidateOutbounds();
                        }
                    }, ViewConfiguration.getLongPressTimeout() - 1);
                    break;
                }
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (cell.pressedBotButton != -1) {
                cell.playSoundEffect(SoundEffectConstants.CLICK);
                BotButton button = cell.botButtons.get(cell.pressedBotButton);
                if (button.selectorDrawable != null) {
                    button.selectorDrawable.setState(StateSet.NOTHING);
                }
                button.setPressed(false);
                if (cell.currentMessageObject.scheduled) {
                    Toast.makeText(cell.getContext(), getString(R.string.MessageScheduledBotAction), Toast.LENGTH_LONG).show();
                } else {
                    if (cell.delegate != null && !button.isLocked) {
                        if (button.buttonCustom != null) {
                            cell.delegate.didPressCustomBotButton(cell, button.buttonCustom);
                        } else if (button.button != null) {
                            cell.delegate.didPressBotButton(cell, button.button);
                        }
                    }
                }
                cell.pressedBotButton = -1;
                cell.invalidateOutbounds();
            }
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (cell.pressedBotButton != -1) {
                BotButton button = cell.botButtons.get(cell.pressedBotButton);
                if (button.selectorDrawable != null) {
                    button.selectorDrawable.setState(StateSet.NOTHING);
                }
                button.setPressed(false);
                cell.pressedBotButton = -1;
                cell.invalidateOutbounds();
            }
        }
        return result;
    }

    void drawButtons(Canvas canvas, ArrayList<BotButton> botButtons, int alpha) {
        if (SizeNotifierFrameLayout.drawingBlur) {
            return;
        }
        final int widthForButtons = cell.getWidthForButtons();
        int addX;
        if (cell.currentMessageObject != null && cell.currentMessageObject.isOutOwner()) {
            addX = cell.getMeasuredWidth() - widthForButtons - dp(10);
        } else if (cell.isCompactLayout()) {
            addX = cell.getCompactLeft();
        } else {
            addX = cell.backgroundDrawableLeft + dp(cell.mediaBackground || cell.drawPinnedBottom ? 1 : 7);
        }

        if (cell.transitionParams.animateBackgroundBoundsInner) {
            addX += cell.transitionParams.deltaLeft;
        }

        float top = cell.layoutHeight - dp(2) + cell.transitionParams.deltaBottom;
        float height = 0;
        for (int a = 0; a < botButtons.size(); a++) {
            BotButton button = botButtons.get(a);
            int bottom = button.y + button.height;
            if (bottom > height) {
                height = bottom;
            }
        }
        cell.rect.set(0, top, cell.getMeasuredWidth(), top + height);
        if (alpha != 0xFF) {
            canvas.saveLayerAlpha(cell.rect, alpha, Canvas.ALL_SAVE_FLAG);
        } else {
            canvas.save();
        }

        boolean invalidate = false;
        for (int a = 0; a < botButtons.size(); a++) {
            BotButton button = botButtons.get(a);
            float y = button.y + cell.layoutHeight - dp(2) + cell.transitionParams.deltaBottom;
            float s = button.getPressScale();

            if (button.isSeparator) {
                final float x1 = button.x * widthForButtons + addX;
                final float x2 = button.x * widthForButtons + addX + button.width * widthForButtons;
                final float totalWidth = x2 - x1;

                final int blockWidth = dp(3.66f);
                final int separatorsCount = Math.round((totalWidth - blockWidth) / dp(8.66f));
                final float partWidth = (totalWidth - blockWidth) / separatorsCount;

                cell.applyServiceShaderMatrix();
                final float br = dp(1);
                for (int n = 0; n < separatorsCount + 1; n++) {
                    cell.rect.set(x1 + partWidth * n, y, x1 + partWidth * n + blockWidth, y + button.height);
                    canvas.drawRoundRect(cell.rect, br, br, cell.getThemedPaint(Theme.key_paint_chatActionBackground));
                    if (cell.hasGradientService()) {
                        canvas.drawRoundRect(cell.rect, br, br, Theme.chat_actionBackgroundGradientDarkenPaint);
                    }
                }

                continue;
            }

            cell.rect.set(
                button.x * widthForButtons + addX,
                y,
                button.x * widthForButtons + addX + button.width * widthForButtons,
                y + button.height
            );
            cell.applyServiceShaderMatrix();

            final TL_keyboard.TL_inlineButtonTypeUrl buttonTypeUrl = TLKeyboardHelper.getType(button.button, TL_keyboard.TL_inlineButtonTypeUrl.class);
            boolean drawProgress = cell.drawButtonProgress(button.button);
            if (button.buttonCustom != null && cell.currentMessageObject != null) {
                if (button.buttonCustom.id == BotInlineKeyboard.ButtonCustom.SUGGESTION_ACCEPT) {
                    drawProgress |= MessagesController.getInstance(cell.currentAccount).isSendingSuggestedMessageApproval(cell.currentMessageObject.getDialogId(), cell.currentMessageObject.getId(), true);
                } else if (button.buttonCustom.id == BotInlineKeyboard.ButtonCustom.SUGGESTION_DECLINE) {
                    drawProgress |= MessagesController.getInstance(cell.currentAccount).isSendingSuggestedMessageApproval(cell.currentMessageObject.getDialogId(), cell.currentMessageObject.getId(), false);
                }
            }

            if (button.draw(canvas, cell.rect, drawProgress, cell.hasInvoicePreview && cell.hasInvoicePrice, cell.isCompactLayout(), cell.resourcesProvider)) {
                invalidate = true;
            }
        }
        canvas.restore();

        if (invalidate) {
            cell.invalidateOutbounds();
        }
    }
}
