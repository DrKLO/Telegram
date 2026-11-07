package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.util.StateSet;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell.InstantViewButton;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.ReplyMessageLine;
import org.telegram.ui.Components.spoilers.SpoilerEffect;

import java.util.ArrayList;

class ChatMessageContact {

    private final ChatMessageCell cell;

    boolean visible, drawSendMessage, drawView, drawAdd;
    int drawnButtonsFlag = 0;
    ArrayList<InstantViewButton> buttons;
    boolean pressed;

    ChatMessageContact(ChatMessageCell cell) {
        this.cell = cell;
    }

    boolean checkMotionEvent(MotionEvent event) {
        if (cell.currentMessageObject.type != MessageObject.TYPE_CONTACT) {
            return false;
        }
        int x = (int) cell.getEventX(event);
        int y = (int) cell.getEventY(event);

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (buttons != null && buttons.size() > 1) {
                for (int i = 0; i < buttons.size(); i++) {
                    InstantViewButton instantViewButton = buttons.get(i);
                    if (instantViewButton.rect.contains(x, y)) {
                        if (instantViewButton.buttonBounce == null) {
                            instantViewButton.buttonBounce = new ButtonBounce(cell);
                        }
                        instantViewButton.buttonBounce.setPressed(true);
                        if (instantViewButton.selectorDrawable != null) {
                            instantViewButton.selectorDrawable.setHotspot(x, y);
                            instantViewButton.selectorDrawable.setState(cell.pressedState);
                        }
                        cell.invalidate();
                        return true;
                    }
                }
            }
            if (cell.contactRect.contains(x, y)) {
                pressed = true;
                cell.contactBounce.setPressed(true);
                if (cell.selectorDrawable[0] != null) {
                    cell.selectorDrawable[0].setHotspot(x, y);
                    cell.selectorDrawable[0].setState(cell.pressedState);
                }
                cell.invalidate();
                return true;
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (pressed) {
                if (cell.delegate != null) {
                    if (buttons != null && buttons.size() == 1) {
                        cell.delegate.didPressInstantButton(cell, buttons.get(0).type);
                    } else {
                        cell.delegate.didPressInstantButton(cell, ChatMessageCell.INSTANT_BUTTON_TYPE_CONTACT_VIEW);
                    }
                }
                cell.playSoundEffect(SoundEffectConstants.CLICK);
                if (cell.selectorDrawable[0] != null) {
                    cell.selectorDrawable[0].setState(StateSet.NOTHING);
                }
                pressed = false;
                cell.contactBounce.setPressed(false);
                cell.invalidate();
            } else if (buttons != null && buttons.size() > 1) {
                for (int i = 0; i < buttons.size(); i++) {
                    InstantViewButton instantViewButton = buttons.get(i);
                    if (instantViewButton.buttonBounce != null && instantViewButton.buttonBounce.isPressed()) {
                        if (cell.delegate != null) {
                            cell.delegate.didPressInstantButton(cell, instantViewButton.type);
                        }
                        if (instantViewButton.selectorDrawable != null) {
                            instantViewButton.selectorDrawable.setState(StateSet.NOTHING);
                        }
                        instantViewButton.buttonBounce.setPressed(false);
                        cell.playSoundEffect(SoundEffectConstants.CLICK);
                        cell.invalidate();
                    }
                }
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (pressed && cell.selectorDrawable[0] != null) {
                cell.selectorDrawable[0].setHotspot(x, y);
            } else if (buttons != null && buttons.size() > 1) {
                for (int i = 0; i < buttons.size(); i++) {
                    InstantViewButton instantViewButton = buttons.get(i);
                    if (instantViewButton.buttonBounce != null && instantViewButton.buttonBounce.isPressed()) {
                        if (instantViewButton.selectorDrawable != null) {
                            instantViewButton.selectorDrawable.setHotspot(x, y);
                        }
                        break;
                    }
                }
            }
        }
        return false;
    }

    void createButtons() {
        cell.createSelectorDrawable(0);
        if (visible) {
            int needDrawFlag = 0;
            int buttonsCount = 0;
            if (drawView) {
                needDrawFlag |= 1;
                buttonsCount++;
            }
            if (drawSendMessage) {
                needDrawFlag |= 2;
                buttonsCount++;
            }
            if (drawAdd) {
                needDrawFlag |= 4;
                buttonsCount++;
            }
            if (buttonsCount == 0) {
                buttons = null;
                drawnButtonsFlag = 0;
                return;
            }
            cell.totalHeight += dp(60);

            boolean needRecreate = needDrawFlag != drawnButtonsFlag;
            if (needRecreate) {
                drawnButtonsFlag = 0;
                int mWidth = (cell.backgroundWidth - dp(10 + 24 + 10 + 31)) / buttonsCount;
                float parentWidth = (cell.backgroundWidth - AndroidUtilities.dpf2(37)) / buttonsCount;

                if (buttons == null) {
                    buttons = new ArrayList<>(buttonsCount);
                } else {
                    buttons.clear();
                }

                if (drawView) {
                    drawnButtonsFlag |= 1;
                    String str = getString("ViewContact", R.string.ViewContact);
                    InstantViewButton instantViewButton = cell.createInstantViewButton(ChatMessageCell.INSTANT_BUTTON_TYPE_CONTACT_VIEW, str, mWidth, parentWidth);
                    buttons.add(instantViewButton);
                }
                if (drawSendMessage) {
                    drawnButtonsFlag |= 2;
                    String str = getString("SharedContactMessage", R.string.SharedContactMessage);
                    InstantViewButton instantViewButton = cell.createInstantViewButton(ChatMessageCell.INSTANT_BUTTON_TYPE_CONTACT_SEND_MESSAGE, str, mWidth, parentWidth);
                    buttons.add(instantViewButton);
                }
                if (drawAdd) {
                    drawnButtonsFlag |= 4;
                    String str = getString("SharedContactAdd", R.string.SharedContactAdd);
                    InstantViewButton instantViewButton = cell.createInstantViewButton(ChatMessageCell.INSTANT_BUTTON_TYPE_CONTACT_ADD, str, mWidth, parentWidth);
                    buttons.add(instantViewButton);
                }
            }
        }
    }

    void draw(Canvas canvas) {
        if (cell.contactLine == null) {
            cell.contactLine = new ReplyMessageLine(cell);
        }
        int nameColor = cell.contactLine.check(cell.currentMessageObject, cell.currentUser, cell.currentChat, cell.resourcesProvider, ReplyMessageLine.TYPE_CONTACT);
        if (cell.contactBounce == null) {
            cell.contactBounce = new ButtonBounce(cell, 2.0f, 2.0f);
        }
        int textX = (int) (cell.photoImage.getImageX() - dp(13));
        int instantY = cell.layoutHeight - dp(62);
        if (!cell.reactionsLayoutInBubble.isEmpty && !cell.reactionsLayoutInBubble.isSmall) {
            instantY -= cell.reactionsLayoutInBubble.totalHeight;
        }
        if (cell.drawCommentButton) {
            instantY -= dp(cell.shouldDrawTimeOnMedia() ? 39.3f : 41);
        }
        if (cell.contactRect == null) {
            cell.contactRect = new RectF();
        }
        float width = cell.getBackgroundDrawableRight() - (dp(10) + (cell.currentMessageObject.isOutOwner() && !cell.mediaBackground && !cell.drawPinnedBottom ? dp(6) : 0)) - cell.getExtraTextX();
        if (cell.transitionParams.animateBackgroundBoundsInner) {
            width += cell.transitionParams.deltaRight;
        }
        cell.contactRect.set(textX, cell.photoImage.getImageY() - dp(9), width, instantY + dp(38f));
        final float scale = cell.contactBounce.getScale(0.0125f);
        boolean needContactViewScale = scale != 1f;
        if (needContactViewScale) {
            canvas.save();
            canvas.scale(scale, scale, cell.contactRect.centerX(), cell.contactRect.centerY());
        }
        if (cell.selectorDrawable[0] != null) {
            cell.selectorDrawableMaskType[0] = 0;
            cell.selectorDrawable[0].setBounds(textX, (int) (cell.photoImage.getImageY() - dp(9)), (int) width, instantY + dp(38f));
            if (cell.selectorDrawableColor != Theme.multAlpha(cell.contactLine.getColor(), .1f)) {
                Theme.setSelectorDrawableColor(cell.selectorDrawable[0], cell.selectorDrawableColor = Theme.multAlpha(cell.contactLine.getColor(), .1f), true);
            }
            cell.selectorDrawable[0].draw(canvas);
        }
        float radF = (float) Math.floor(SharedConfig.bubbleRadius / 3f);
        int rad = (int) radF;
        cell.contactLine.drawBackground(canvas, cell.contactRect, radF, radF, radF, 1f);
        cell.contactLine.drawLine(canvas, cell.contactRect, 1f);
        Theme.chat_contactNamePaint.setColor(nameColor);
        Theme.chat_contactPhonePaint.setColor(cell.getThemedColor(Theme.key_chat_inContactPhoneSelectedText));
        if (cell.currentMessageObject.isOutOwner()) {
            Theme.chat_contactPhonePaint.setColor(cell.getThemedColor(Theme.key_chat_messageTextOut));
        } else {
            Theme.chat_contactPhonePaint.setColor(cell.getThemedColor(Theme.key_chat_messageTextIn));
        }
        if (cell.titleLayout != null) {
            canvas.save();
            canvas.translate(cell.photoImage.getImageX() + cell.photoImage.getImageWidth() + dp(9), dp(27) + cell.namesOffset);
            SpoilerEffect.layoutDrawMaybe(cell.titleLayout, canvas);
            canvas.restore();
        }
        if (cell.docTitleLayout != null) {
            canvas.save();
            canvas.translate(cell.photoImage.getImageX() + cell.photoImage.getImageWidth() + dp(9), dp(50) + cell.namesOffset);
            SpoilerEffect.layoutDrawMaybe(cell.docTitleLayout, canvas);
            canvas.restore();
        }
        if (buttons != null && buttons.size() > 0) {
            Theme.chat_instantViewPaint.setColor(nameColor);
            Theme.chat_instantViewButtonPaint.setColor(Theme.multAlpha(nameColor, .10f));
            int wasAlpha = Theme.chat_instantViewPaint.getAlpha();
            Theme.chat_instantViewPaint.setAlpha((int) (wasAlpha * .18f));
            canvas.drawRect(cell.contactRect.left + dp(3 + 7), instantY + dp(2), cell.contactRect.right - dp(7), instantY + dp(2) + Math.max(1, dp(0.66f)), Theme.chat_instantViewPaint);
            Theme.chat_instantViewPaint.setAlpha(wasAlpha);
            instantY += dp(2);
            float instantButtonLeft = textX + dp(3);
            boolean needCreateSelectorDrawable = buttons != null && buttons.size() > 1;
            int rippleColor = cell.contactLine.getBackgroundColor();
            for (int i = 0; i < buttons.size(); i++) {
                InstantViewButton instantViewButton = buttons.get(i);
                float right = Math.min(instantButtonLeft + instantViewButton.buttonWidth, cell.contactRect.right);
                instantViewButton.rect.set(instantButtonLeft, instantY, right, instantY + dp(36));
                if (needCreateSelectorDrawable && instantViewButton.selectorDrawable == null) {
                    instantViewButton.selectorDrawable = Theme.createRadSelectorDrawable(cell.linkPreviewSelectorColor = rippleColor, 0, 0, i == (buttons.size() - 1) ? rad : 0, 0);
                    instantViewButton.selectorDrawable.setCallback(cell);
                }
                if (instantViewButton.selectorDrawable != null) {
                    instantViewButton.selectorDrawable.setBounds((int) instantButtonLeft, instantY, (int) right, instantY + dp(36));
                    instantViewButton.selectorDrawable.draw(canvas);
                }
                float buttonScale = 1f;
                boolean needButtonScale = false;
                if (!needContactViewScale) {
                    if (instantViewButton.buttonBounce != null) {
                        buttonScale = instantViewButton.buttonBounce.getScale(0.02f);
                        needButtonScale = buttonScale != 1;
                    }
                }
                if (needButtonScale) {
                    canvas.save();
                    canvas.scale(buttonScale, buttonScale, instantViewButton.rect.centerX(), instantViewButton.rect.centerY());
                }
                if (instantViewButton.layout != null) {
                    canvas.save();
                    canvas.translate(instantButtonLeft + instantViewButton.textX, instantY + dp(10.5f));
                    instantViewButton.layout.draw(canvas);
                    canvas.restore();
                }
                if (needButtonScale) {
                    canvas.restore();
                }
                instantButtonLeft += instantViewButton.buttonWidth;
            }
        }
        if (needContactViewScale) {
            canvas.restore();
        }
    }
}
