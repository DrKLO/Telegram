package org.telegram.ui.Components.Reactions;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.core.graphics.ColorUtils;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.AvatarsDarawable;
import org.telegram.ui.Components.CounterView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class ReactionsLayoutInBubble {

    private final static int ANIMATION_TYPE_IN = 1;
    private final static int ANIMATION_TYPE_OUT = 2;
    private final static int ANIMATION_TYPE_MOVE = 3;
    public boolean drawServiceShaderBackground;

    private static Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    public boolean isSmall;

    public int x;
    public int y;
    private float fromX;
    private float fromY;
    private float lastDrawnX;
    private float lastDrawnY;
    private boolean wasDrawn;
    private boolean animateMove;
    private boolean animateWidth;
    private boolean animateHeight;
    public int positionOffsetY;
    int currentAccount;
    public int height;
    public int totalHeight;
    public int width;
    public int fromWidth;
    public boolean isEmpty;
    private float touchSlop;
    public int lastLineX;
    ArrayList<ReactionButton> reactionButtons = new ArrayList<>();
    ArrayList<ReactionButton> outButtons = new ArrayList<>();
    HashMap<String, ReactionButton> lastDrawingReactionButtons = new HashMap<>();
    HashMap<String, ReactionButton> lastDrawingReactionButtonsTmp = new HashMap<>();
    ChatMessageCell parentView;
    MessageObject messageObject;
    Theme.ResourcesProvider resourcesProvider;
    private String scrimViewReaction;

    int availableWidth;
    private int lastDrawnWidth;
    boolean attached;
    private static int animationUniq;

    private final static ButtonsComparator comparator = new ButtonsComparator();
    HashMap<String, ImageReceiver> animatedReactions = new HashMap<>();
    private int lastDrawTotalHeight;
    private int animateFromTotalHeight;
    public boolean hasUnreadReactions;

    public ReactionsLayoutInBubble(ChatMessageCell parentView) {
        this.parentView = parentView;
        currentAccount = UserConfig.selectedAccount;
        paint.setColor(Theme.getColor(Theme.key_chat_inLoader));
        textPaint.setColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        textPaint.setTextSize(AndroidUtilities.dp(12));
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        touchSlop = ViewConfiguration.get(ApplicationLoader.applicationContext).getScaledTouchSlop();
    }

    public void setMessage(MessageObject messageObject, boolean isSmall, Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
        this.isSmall = isSmall;
        this.messageObject = messageObject;
        for (int i = 0; i < reactionButtons.size(); i++) {
            reactionButtons.get(i).detach();
        }
        hasUnreadReactions = false;
        reactionButtons.clear();
        if (messageObject != null) {
            if (messageObject.messageOwner.reactions != null && messageObject.messageOwner.reactions.results != null) {
                int totalCount = 0;
                for (int i = 0; i < messageObject.messageOwner.reactions.results.size(); i++) {
                    totalCount += messageObject.messageOwner.reactions.results.get(i).count;
                }
                for (int i = 0; i < messageObject.messageOwner.reactions.results.size(); i++) {
                    TLRPC.TL_reactionCount reactionCount = messageObject.messageOwner.reactions.results.get(i);
                    ReactionButton button = new ReactionButton(reactionCount);
                    reactionButtons.add(button);
                    if (!isSmall && messageObject.messageOwner.reactions.recent_reactions != null) {
                        ArrayList<TLRPC.User> users = null;
                        if (reactionCount.count <= 3 && totalCount <= 3) {
                            for (int j = 0; j < messageObject.messageOwner.reactions.recent_reactions.size(); j++) {
                                TLRPC.TL_messagePeerReaction reccent = messageObject.messageOwner.reactions.recent_reactions.get(j);
                                if (reccent.reaction.equals(reactionCount.reaction) && MessagesController.getInstance(currentAccount).getUser(MessageObject.getPeerId(reccent.peer_id)) != null) {
                                    if (users == null) {
                                        users = new ArrayList<>();
                                    }
                                    users.add(MessagesController.getInstance(currentAccount).getUser(MessageObject.getPeerId(reccent.peer_id)));
                                }
                            }
                            button.setUsers(users);
                            if (users != null && !users.isEmpty()) {
                                button.count = 0;
                                button.counterDrawable.setCount(0, false);
                            }
                        }
                    }
                    if (isSmall && reactionCount.count > 1 && reactionCount.chosen) {
                        reactionButtons.add(new ReactionButton(reactionCount));
                        reactionButtons.get(0).isSelected = false;
                        reactionButtons.get(1).isSelected = true;
                        reactionButtons.get(0).realCount = 1;
                        reactionButtons.get(1).realCount = 1;
                        reactionButtons.get(1).key = reactionButtons.get(1).key + "_";
                        break;
                    }
                    if (isSmall && i == 2) {
                        break;
                    }
                    if (attached) {
                        button.attach();
                    }
                }
            }
            if (!isSmall) {
                comparator.currentAccount = currentAccount;
                Collections.sort(reactionButtons, comparator);
            }
            hasUnreadReactions = MessageObject.hasUnreadReactions(messageObject.messageOwner);
        }
        isEmpty = reactionButtons.isEmpty();
    }

    public void measure(int availableWidth) {
        height = 0;
        width = 0;
        positionOffsetY = 0;
        totalHeight = 0;
        if (isEmpty) {
            return;
        }
        this.availableWidth = availableWidth;
        int maxWidth = 0;
        int currentX = 0;
        int currentY = 0;
        for (int i = 0; i < reactionButtons.size(); i++) {
            ReactionButton button = reactionButtons.get(i);
            if (isSmall) {
                button.width = AndroidUtilities.dp(14);
                button.height = AndroidUtilities.dp(14);
            } else {
                button.width = (int) (AndroidUtilities.dp(8) + AndroidUtilities.dp(20) + AndroidUtilities.dp(4));
                if (button.avatarsDarawable != null && button.users.size() > 0) {
                    button.users.size();
                    int c1 = 1;
                    int c2 = button.users.size() > 1 ? button.users.size() - 1 : 0;
                    button.width += AndroidUtilities.dp(2) + c1 * AndroidUtilities.dp(20) + c2 * AndroidUtilities.dp(20) * 0.8f + AndroidUtilities.dp(1);
                    button.avatarsDarawable.height = AndroidUtilities.dp(26);
                } else {
                    button.width += button.counterDrawable.textPaint.measureText(button.countText) + AndroidUtilities.dp(8);
                }
                button.height = AndroidUtilities.dp(26);
            }

            if (currentX + button.width > availableWidth) {
                currentX = 0;
                currentY += button.height + AndroidUtilities.dp(4);
            }
            button.x = currentX;
            button.y = currentY;
            currentX += button.width + AndroidUtilities.dp(4);
            if (currentX > maxWidth) {
                maxWidth = currentX;
            }

        }
        lastLineX = currentX;
        width = maxWidth;
        height = currentY + (reactionButtons.size() == 0 ? 0 : AndroidUtilities.dp(26));
        drawServiceShaderBackground = false;
    }

    public void draw(Canvas canvas, float animationProgress, String drawOnlyReaction) {
        if (isEmpty && outButtons.isEmpty()) {
            return;
        }
        float totalX = this.x;
        float totalY = this.y;
        if (isEmpty) {
            totalX = lastDrawnX;
            totalY = lastDrawnY;
        } else if (animateMove) {
            totalX = totalX * (animationProgress) + fromX * (1f - animationProgress);
            totalY = totalY * (animationProgress) + fromY * (1f - animationProgress);
        }
        canvas.save();
        canvas.translate(totalX, totalY);
        for (int i = 0; i < reactionButtons.size(); i++) {
            ReactionButton reactionButton = reactionButtons.get(i);
            if (reactionButton.reaction.equals(scrimViewReaction) || (drawOnlyReaction != null && !reactionButton.reaction.equals(drawOnlyReaction))) {
                continue;
            }
            canvas.save();
            float x = reactionButton.x;
            float y = reactionButton.y;
            if (animationProgress != 1f && reactionButton.animationType == ANIMATION_TYPE_MOVE) {
                x = reactionButton.x * animationProgress + reactionButton.animateFromX * (1f - animationProgress);
                y = reactionButton.y * animationProgress + reactionButton.animateFromY * (1f - animationProgress);
            }
            canvas.translate(x, y);
            float alpha = 1f;
            if (animationProgress != 1f && reactionButton.animationType == ANIMATION_TYPE_IN) {
                float s = 0.5f + 0.5f * animationProgress;
                alpha = animationProgress;
                canvas.scale(s, s, reactionButton.width / 2f, reactionButton.height / 2f);
            }
            reactionButton.draw(canvas, reactionButton.animationType == ANIMATION_TYPE_MOVE ? animationProgress : 1f, alpha, drawOnlyReaction != null);
            canvas.restore();
        }

        for (int i = 0; i < outButtons.size(); i++) {
            ReactionButton reactionButton = outButtons.get(i);
            canvas.save();
            canvas.translate(reactionButton.x, reactionButton.y);
            float s = 0.5f + 0.5f * (1f - animationProgress);
            canvas.scale(s, s, reactionButton.width / 2f, reactionButton.height / 2f);
            outButtons.get(i).draw(canvas, 1f, (1f - animationProgress), false);
            canvas.restore();
        }
        canvas.restore();
    }

    public void recordDrawingState() {
        lastDrawingReactionButtons.clear();
        for (int i = 0; i < reactionButtons.size(); i++) {
            lastDrawingReactionButtons.put(reactionButtons.get(i).key, reactionButtons.get(i));
        }
        wasDrawn = !isEmpty;
        lastDrawnX = x;
        lastDrawnY = y;
        lastDrawnWidth = width;
        lastDrawTotalHeight = totalHeight;
    }

    public boolean animateChange() {
        if (messageObject == null) {
            return false;
        }
        boolean changed = false;
        lastDrawingReactionButtonsTmp.clear();
        for (int i = 0; i < outButtons.size(); i++) {
            outButtons.get(i).detach();
        }
        outButtons.clear();
        lastDrawingReactionButtonsTmp.putAll(lastDrawingReactionButtons);
        for (int i = 0; i < reactionButtons.size(); i++) {
            ReactionButton button = reactionButtons.get(i);
            ReactionButton lastButton = lastDrawingReactionButtonsTmp.remove(button.key);
            if (lastButton != null) {
                if (button.x != lastButton.x || button.y != lastButton.y || button.width != lastButton.width || button.count != lastButton.count || button.backgroundColor != lastButton.backgroundColor) {
                    button.animateFromX = lastButton.x;
                    button.animateFromY = lastButton.y;
                    button.animateFromWidth = lastButton.width;

                    button.fromTextColor = lastButton.lastDrawnTextColor;
                    button.fromBackgroundColor = lastButton.lastDrawnBackgroundColor;
                    button.animationType = ANIMATION_TYPE_MOVE;

                    if (button.count != lastButton.count) {
                        button.counterDrawable.setCount(lastButton.count, false);
                        button.counterDrawable.setCount(button.count, true);
                    }
                    if (button.avatarsDarawable != null || lastButton.avatarsDarawable != null) {
                        if (button.avatarsDarawable == null) {
                            button.setUsers(new ArrayList<>());
                        }
                        if (lastButton.avatarsDarawable == null) {
                            lastButton.setUsers(new ArrayList<>());
                        }
                        button.avatarsDarawable.animateFromState(lastButton.avatarsDarawable, currentAccount);
                    }
                    changed = true;
                } else {
                    button.animationType = 0;
                }
            } else {
                changed = true;
                button.animationType = ANIMATION_TYPE_IN;
            }
        }
        if (!lastDrawingReactionButtonsTmp.isEmpty()) {
            changed = true;
            outButtons.addAll(lastDrawingReactionButtonsTmp.values());
            for (int i = 0; i < outButtons.size(); i++) {
                outButtons.get(i).drawImage = outButtons.get(i).lastImageDrawn;
                outButtons.get(i).attach();
            }
        }

        if (wasDrawn && (lastDrawnX != x || lastDrawnY != y)) {
            animateMove = true;
            fromX = lastDrawnX;
            fromY = lastDrawnY;
            changed = true;
        }

        if (lastDrawnWidth != width) {
            animateWidth = true;
            fromWidth = lastDrawnWidth;
            changed = true;
        }

        if (lastDrawTotalHeight != totalHeight) {
            animateHeight = true;
            animateFromTotalHeight = lastDrawTotalHeight;
            changed = true;
        }

        return changed;
    }

    public void resetAnimation() {
        for (int i = 0; i < outButtons.size(); i++) {
            outButtons.get(i).detach();
        }
        outButtons.clear();
        animateMove = false;
        animateWidth = false;
        animateHeight = false;
        for (int i = 0; i < reactionButtons.size(); i++) {
            reactionButtons.get(i).animationType = 0;
        }
    }

    public ReactionButton getReactionButton(String reaction) {
        if (isSmall) {
            ReactionButton button = lastDrawingReactionButtons.get(reaction + "_");
            if (button != null) {
                return button;
            }
        }
        return lastDrawingReactionButtons.get(reaction);
    }

    public void setScrimReaction(String scrimViewReaction) {
        this.scrimViewReaction = scrimViewReaction;
    }

    public class ReactionButton {

        private final TLRPC.TL_reactionCount reactionCount;
        public int animationType;
        public int animateFromX;
        public int animateFromY;
        public int animateFromWidth;
        public int fromBackgroundColor;
        public int fromTextColor;
        public int realCount;
        public boolean drawImage = true;
        public boolean lastImageDrawn;
        public boolean wasDrawn;
        public String key;

        String countText;
        String reaction;

        int count;
        public int x;
        public int y;
        public int width;
        public int height;
        ImageReceiver imageReceiver = new ImageReceiver();
        CounterView.CounterDrawable counterDrawable = new CounterView.CounterDrawable(parentView, false, null);
        int backgroundColor;
        int textColor;
        int serviceBackgroundColor;
        int serviceTextColor;

        int lastDrawnTextColor;
        int lastDrawnBackgroundColor;
        boolean isSelected;
        AvatarsDarawable avatarsDarawable;
        ArrayList<TLRPC.User> users;

        public ReactionButton(TLRPC.TL_reactionCount reactionCount) {
            this.reactionCount = reactionCount;
            this.reaction = reactionCount.reaction;
            this.count = reactionCount.count;
            this.realCount = reactionCount.count;
            this.key = this.reaction;
            countText = Integer.toString(reactionCount.count);
            imageReceiver.setParentView(parentView);
            isSelected = reactionCount.chosen;
            counterDrawable.updateVisibility = false;
            counterDrawable.shortFormat = true;
            if (reactionCount.chosen) {
                backgroundColor = Theme.getColor(messageObject.isOutOwner() ? Theme.key_chat_outReactionButtonBackground : Theme.key_chat_inReactionButtonBackground, resourcesProvider);
                textColor = Theme.getColor(messageObject.isOutOwner() ? Theme.key_chat_outReactionButtonTextSelected : Theme.key_chat_inReactionButtonTextSelected, resourcesProvider);
                serviceTextColor = Theme.getColor(messageObject.isOutOwner() ? Theme.key_chat_outReactionButtonBackground : Theme.key_chat_inReactionButtonBackground, resourcesProvider);
                serviceBackgroundColor = Theme.getColor(messageObject.isOutOwner() ? Theme.key_chat_outBubble : Theme.key_chat_inBubble);
            } else {
                textColor = Theme.getColor(messageObject.isOutOwner() ? Theme.key_chat_outReactionButtonText : Theme.key_chat_inReactionButtonText, resourcesProvider);
                backgroundColor = Theme.getColor(messageObject.isOutOwner() ? Theme.key_chat_outReactionButtonBackground : Theme.key_chat_inReactionButtonBackground, resourcesProvider);
                backgroundColor = ColorUtils.setAlphaComponent(backgroundColor, (int) (Color.alpha(backgroundColor) * 0.156f));
                serviceTextColor = Theme.getColor(Theme.key_chat_serviceText, resourcesProvider);
                serviceBackgroundColor = Color.TRANSPARENT;
            }


            if (reaction != null) {
                TLRPC.TL_availableReaction r = MediaDataController.getInstance(currentAccount).getReactionsMap().get(reaction);
                if (r != null) {
                    SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(r.static_icon, Theme.key_windowBackgroundGray, 1.0f);
                    imageReceiver.setImage(ImageLocation.getForDocument(r.static_icon), "40_40", svgThumb, "webp", r, 1);
                }
            }

            counterDrawable.setSize(AndroidUtilities.dp(26), AndroidUtilities.dp(100));
            counterDrawable.textPaint = textPaint;
            counterDrawable.setCount(count, false);
            counterDrawable.setType(CounterView.CounterDrawable.TYPE_CHAT_REACTIONS);
            counterDrawable.gravity = Gravity.LEFT;
        }

        public void draw(Canvas canvas, float progress, float alpha, boolean drawOverlayScrim) {
            wasDrawn = true;
            if (isSmall) {
                imageReceiver.setAlpha(alpha);
                imageReceiver.setImageCoords(0, 0, AndroidUtilities.dp(14), AndroidUtilities.dp(14));
                drawImage(canvas);
                return;
            }
            updateColors(progress);
            textPaint.setColor(lastDrawnTextColor);
            paint.setColor(lastDrawnBackgroundColor);

            if (alpha != 1f) {
                textPaint.setAlpha((int) (textPaint.getAlpha() * alpha));
                paint.setAlpha((int) (paint.getAlpha() * alpha));
            }
            imageReceiver.setAlpha(alpha);

            int w = width;
            if (progress != 1f && animationType == ANIMATION_TYPE_MOVE) {
                w = (int) (width * progress + animateFromWidth * (1f - progress));
            }
            AndroidUtilities.rectTmp.set(0, 0, w, height);
            float rad = height / 2f;
            if (drawServiceShaderBackground) {
                Paint paint1 = getThemedPaint(Theme.key_paint_chatActionBackground);
                Paint paint2 = Theme.chat_actionBackgroundGradientDarkenPaint;
                int oldAlpha = paint1.getAlpha();
                int oldAlpha2 = paint2.getAlpha();
                paint1.setAlpha((int) (oldAlpha * alpha));
                paint2.setAlpha((int) (oldAlpha2 * alpha));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, rad, rad, paint1);
                if (hasGradientService()) {
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, rad, rad, paint2);
                }
                paint1.setAlpha(oldAlpha);
                paint2.setAlpha(oldAlpha2);
            }
            if (!drawServiceShaderBackground && drawOverlayScrim) {
                Theme.MessageDrawable messageBackground = parentView.getCurrentBackgroundDrawable(false);
                if (messageBackground != null) {
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, rad, rad, messageBackground.getPaint());
                }
            }
            canvas.drawRoundRect(AndroidUtilities.rectTmp, rad, rad, paint);

            imageReceiver.setImageCoords(AndroidUtilities.dp(8), (height - AndroidUtilities.dp(20)) / 2f, AndroidUtilities.dp(20), AndroidUtilities.dp(20));
            drawImage(canvas);

            if (count != 0 || counterDrawable.countChangeProgress != 1f) {
                canvas.save();
                canvas.translate(AndroidUtilities.dp(8) + AndroidUtilities.dp(20) + AndroidUtilities.dp(2), 0);
                counterDrawable.draw(canvas);
                canvas.restore();
            }

            if (avatarsDarawable != null) {
                canvas.save();
                canvas.translate(AndroidUtilities.dp(10) + AndroidUtilities.dp(20) + AndroidUtilities.dp(2), 0);
                avatarsDarawable.setAlpha(alpha);
                avatarsDarawable.onDraw(canvas);
                canvas.restore();
            }
        }

        private void updateColors(float progress) {
            if (drawServiceShaderBackground) {
                lastDrawnTextColor = ColorUtils.blendARGB(fromTextColor, serviceTextColor, progress);
                lastDrawnBackgroundColor = ColorUtils.blendARGB(fromBackgroundColor, serviceBackgroundColor, progress);
            } else {
                lastDrawnTextColor = ColorUtils.blendARGB(fromTextColor, textColor, progress);
                lastDrawnBackgroundColor = ColorUtils.blendARGB(fromBackgroundColor, backgroundColor, progress);
            }
        }

        private void drawImage(Canvas canvas) {
            if (drawImage && ((realCount > 1 || !ReactionsEffectOverlay.isPlaying(messageObject.getId(), messageObject.getGroupId(), reaction)) || !isSelected)) {
                ImageReceiver imageReceiver2 = animatedReactions.get(reaction);
                boolean drawStaticImage = true;
                if (imageReceiver2 != null) {
                    imageReceiver2.setAlpha(imageReceiver.getAlpha());
                    imageReceiver2.setImageCoords(imageReceiver.getImageX() - imageReceiver.getImageWidth() / 2, imageReceiver.getImageY() - imageReceiver.getImageWidth() / 2, imageReceiver.getImageWidth() * 2, imageReceiver.getImageHeight() * 2);
                    imageReceiver2.draw(canvas);
                    if (imageReceiver2.getLottieAnimation() != null && imageReceiver2.getLottieAnimation().hasBitmap()) {
                        drawStaticImage = false;
                    }
                    if (imageReceiver2.getLottieAnimation() != null && !imageReceiver2.getLottieAnimation().isRunning()) {
                        float alpha = imageReceiver2.getAlpha() - 16f / 200;
                        if (alpha < 0) {
                            imageReceiver2.onDetachedFromWindow();
                            animatedReactions.remove(reaction);
                        } else {
                            imageReceiver2.setAlpha(alpha);
                        }
                    }
                }
                if (drawStaticImage) {
                    imageReceiver.draw(canvas);
                }
                lastImageDrawn = true;
            } else {
                imageReceiver.setAlpha(0);
                imageReceiver.draw(canvas);
                lastImageDrawn = false;
            }
        }

        public void setUsers(ArrayList<TLRPC.User> users) {
            this.users = users;
            if (users != null) {
                if (avatarsDarawable == null) {
                    avatarsDarawable = new AvatarsDarawable(parentView, false);
                    avatarsDarawable.setSize(AndroidUtilities.dp(20));
                    avatarsDarawable.width = AndroidUtilities.dp(100);
                    avatarsDarawable.height = height;
                    if (attached) {
                        avatarsDarawable.onAttachedToWindow();
                    }
                }
                for (int i = 0; i < users.size(); i++) {
                    if (i == 3) {
                        break;
                    }
                    avatarsDarawable.setObject(i, currentAccount, users.get(i));
                }
                avatarsDarawable.commitTransition(false);
            }
        }

        public void attach() {
            if (imageReceiver != null) {
                imageReceiver.onAttachedToWindow();
            }
            if (avatarsDarawable != null) {
                avatarsDarawable.onAttachedToWindow();
            }
        }

        public void detach() {
            if (imageReceiver != null) {
                imageReceiver.onDetachedFromWindow();
            }
            if (avatarsDarawable != null) {
                avatarsDarawable.onDetachedFromWindow();
            }
        }
    }

    float lastX;
    float lastY;
    ReactionButton lastSelectedButton;
    boolean pressed;
    Runnable longPressRunnable;

    public boolean chekTouchEvent(MotionEvent event) {
        if (isEmpty || isSmall || messageObject == null || messageObject.messageOwner == null || messageObject.messageOwner.reactions == null) {
            return false;
        }
        float x = event.getX() - this.x;
        float y = event.getY() - this.y;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            for (int i = 0, n = reactionButtons.size(); i < n; i++) {
                if (x > reactionButtons.get(i).x && x < reactionButtons.get(i).x + reactionButtons.get(i).width &&
                        y > reactionButtons.get(i).y && y < reactionButtons.get(i).y + reactionButtons.get(i).height) {
                    lastX = event.getX();
                    lastY = event.getY();
                    lastSelectedButton = reactionButtons.get(i);
                    if (longPressRunnable != null) {
                        AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                        longPressRunnable = null;
                    }

                    final ReactionButton selectedButtonFinal = lastSelectedButton;
                    if (messageObject.messageOwner.reactions.can_see_list) {
                        AndroidUtilities.runOnUIThread(longPressRunnable = () -> {
                            parentView.getDelegate().didPressReaction(parentView, selectedButtonFinal.reactionCount, true);
                            longPressRunnable = null;
                        }, ViewConfiguration.getLongPressTimeout());
                    }
                    pressed = true;
                    break;
                }
            }
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (pressed && Math.abs(event.getX() - lastX) > touchSlop || Math.abs(event.getY() - lastY) > touchSlop) {
                pressed = false;
                lastSelectedButton = null;
                if (longPressRunnable != null) {
                    AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                    longPressRunnable = null;
                }
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (longPressRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                longPressRunnable = null;
            }
            if (pressed && lastSelectedButton != null && event.getAction() == MotionEvent.ACTION_UP) {
                if (parentView.getDelegate() != null) {
                    parentView.getDelegate().didPressReaction(parentView, lastSelectedButton.reactionCount, false);
                }
            }
            pressed = false;
            lastSelectedButton = null;
        }
        return pressed;
    }

    private boolean hasGradientService() {
        return resourcesProvider != null ? resourcesProvider.hasGradientService() : Theme.hasGradientService();
    }

    private Paint getThemedPaint(String paintKey) {
        Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(paintKey) : null;
        return paint != null ? paint : Theme.getThemePaint(paintKey);
    }

    public float getCurrentWidth(float transitionProgress) {
        if (animateWidth) {
            return fromWidth * (1f - transitionProgress) + width * transitionProgress;
        }
        return width;
    }

    public float getCurrentTotalHeight(float transitionProgress) {
        if (animateHeight) {
            return animateFromTotalHeight * (1f - transitionProgress) + totalHeight * transitionProgress;
        }
        return totalHeight;
    }

    private static class ButtonsComparator implements Comparator<ReactionButton> {

        int currentAccount;

        @Override
        public int compare(ReactionButton o1, ReactionButton o2) {
            if (o1.realCount != o2.realCount) {
                return o2.realCount - o1.realCount;
            }
            TLRPC.TL_availableReaction availableReaction1 = MediaDataController.getInstance(currentAccount).getReactionsMap().get(o1.reaction);
            TLRPC.TL_availableReaction availableReaction2 = MediaDataController.getInstance(currentAccount).getReactionsMap().get(o2.reaction);
            if (availableReaction1 != null && availableReaction2 != null) {
                return availableReaction1.positionInList - availableReaction2.positionInList;
            }
            return 0;
        }
    }

    public void onAttachToWindow() {
        for (int i = 0; i < reactionButtons.size(); i++) {
            reactionButtons.get(i).attach();
        }
    }

    public void onDetachFromWindow() {
        for (int i = 0; i < reactionButtons.size(); i++) {
            reactionButtons.get(i).detach();
        }
        if (!animatedReactions.isEmpty()) {
            for (ImageReceiver imageReceiver : animatedReactions.values()) {
                imageReceiver.onDetachedFromWindow();
            }
        }
        animatedReactions.clear();
    }

    public void animateReaction(String reaction) {
        if (animatedReactions.get(reaction) == null) {
            ImageReceiver imageReceiver = new ImageReceiver();
            imageReceiver.setParentView(parentView);
            imageReceiver.setUniqKeyPrefix(Integer.toString(animationUniq++));
            if (reaction != null) {
                TLRPC.TL_availableReaction r = MediaDataController.getInstance(currentAccount).getReactionsMap().get(reaction);
                if (r != null) {
                    imageReceiver.setImage(ImageLocation.getForDocument(r.center_icon), "40_40_nolimit", null, "tgs", r, 1);
                }
            }
            imageReceiver.setAutoRepeat(0);
            imageReceiver.onAttachedToWindow();
            animatedReactions.put(reaction, imageReceiver);
        }
    }

}
