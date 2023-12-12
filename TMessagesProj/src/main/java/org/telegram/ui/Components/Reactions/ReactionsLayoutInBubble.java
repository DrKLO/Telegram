package org.telegram.ui.Components.Reactions;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.ChatListItemAnimator;

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
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarsDrawable;
import org.telegram.ui.Components.CounterView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Objects;

public class ReactionsLayoutInBubble {

    private final static int ANIMATION_TYPE_IN = 1;
    private final static int ANIMATION_TYPE_OUT = 2;
    private final static int ANIMATION_TYPE_MOVE = 3;
    public float drawServiceShaderBackground;

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
    HashMap<VisibleReaction, ImageReceiver> animatedReactions = new HashMap<>();
    private int lastDrawTotalHeight;
    private int animateFromTotalHeight;
    public boolean hasUnreadReactions;

    private static int pointer = 1;

    private final static Comparator<TLObject> usersComparator = (user1, user2) -> (int) (getPeerId(user1) - getPeerId(user2));

    private static long getPeerId(TLObject object) {
        if (object instanceof TLRPC.User) {
            return ((TLRPC.User) object).id;
        }
        if (object instanceof TLRPC.Chat) {
            return ((TLRPC.Chat) object).id;
        }
        return 0;
    }

    public ReactionsLayoutInBubble(ChatMessageCell parentView) {
        this.parentView = parentView;
        currentAccount = UserConfig.selectedAccount;
        paint.setColor(Theme.getColor(Theme.key_chat_inLoader, resourcesProvider));
        textPaint.setColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
        textPaint.setTextSize(AndroidUtilities.dp(12));
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        touchSlop = ViewConfiguration.get(ApplicationLoader.applicationContext).getScaledTouchSlop();
    }

    public static boolean equalsTLReaction(TLRPC.Reaction reaction, TLRPC.Reaction reaction1) {
        if (reaction instanceof TLRPC.TL_reactionEmoji && reaction1 instanceof TLRPC.TL_reactionEmoji) {
            return TextUtils.equals(((TLRPC.TL_reactionEmoji) reaction).emoticon, ((TLRPC.TL_reactionEmoji) reaction1).emoticon);
        }
        if (reaction instanceof TLRPC.TL_reactionCustomEmoji && reaction1 instanceof TLRPC.TL_reactionCustomEmoji) {
            return ((TLRPC.TL_reactionCustomEmoji) reaction).document_id == ((TLRPC.TL_reactionCustomEmoji) reaction1).document_id;
        }
        return false;
    }

    public void setMessage(MessageObject messageObject, boolean isSmall, Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
        this.isSmall = isSmall;
        this.messageObject = messageObject;
        ArrayList<ReactionButton> oldButtons = new ArrayList<>(reactionButtons);
        hasUnreadReactions = false;
        reactionButtons.clear();
        if (messageObject != null) {
            comparator.dialogId = messageObject.getDialogId();
            if (messageObject.messageOwner.reactions != null && messageObject.messageOwner.reactions.results != null) {
                int totalCount = 0;
                for (int i = 0; i < messageObject.messageOwner.reactions.results.size(); i++) {
                    totalCount += messageObject.messageOwner.reactions.results.get(i).count;
                }
                for (int i = 0; i < messageObject.messageOwner.reactions.results.size(); i++) {
                    TLRPC.ReactionCount reactionCount = messageObject.messageOwner.reactions.results.get(i);
                    ReactionButton old = null;
//                    for (int j = 0; j < oldButtons.size(); ++j) {
//                        ReactionButton btn = oldButtons.get(j);
//                        if (btn.reaction.equals(reactionCount.reaction)) {
//                            old = btn;
//                            break;
//                        }
//                    }
                    ReactionButton button = new ReactionButton(old, reactionCount, isSmall);
                    reactionButtons.add(button);
                    if (!isSmall && messageObject.messageOwner.reactions.recent_reactions != null) {
                        ArrayList<TLObject> users = null;

                        if (messageObject.getDialogId() > 0 && !UserObject.isReplyUser(messageObject.getDialogId())) {
                            users = new ArrayList<>();
                            TLRPC.User me = UserConfig.getInstance(currentAccount).getCurrentUser();
                            TLRPC.User dialogUser = MessagesController.getInstance(currentAccount).getUser(messageObject.getDialogId());
                            if (reactionCount.count == 2) {
                                if (me != null) {
                                    users.add(me);
                                }
                                if (dialogUser != null) {
                                    users.add(dialogUser);
                                }
                            } else {
                                if (reactionCount.chosen) {
                                    if (me != null) {
                                        users.add(me);
                                    }
                                } else {
                                    if (dialogUser != null) {
                                        users.add(dialogUser);
                                    }
                                }
                            }
                            button.setUsers(users);
                            if (users != null && !users.isEmpty()) {
                                button.count = 0;
                                button.counterDrawable.setCount(0, false);
                            }
                        } else if (reactionCount.count <= 3 && totalCount <= 3) {
                            for (int j = 0; j < messageObject.messageOwner.reactions.recent_reactions.size(); j++) {
                                TLRPC.MessagePeerReaction recent = messageObject.messageOwner.reactions.recent_reactions.get(j);
                                VisibleReaction visibleReactionPeer = VisibleReaction.fromTLReaction(recent.reaction);
                                VisibleReaction visibleReactionCount = VisibleReaction.fromTLReaction(reactionCount.reaction);
                                TLObject object = MessagesController.getInstance(currentAccount).getUserOrChat(MessageObject.getPeerId(recent.peer_id));
                                if (visibleReactionPeer.equals(visibleReactionCount) && object != null) {
                                    if (users == null) {
                                        users = new ArrayList<>();
                                    }
                                    users.add(object);
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
                        // TODO: also reuse here
                        reactionButtons.add(new ReactionButton(null, reactionCount, isSmall));
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
            if (!isSmall && !reactionButtons.isEmpty()) {
                comparator.currentAccount = currentAccount;
                Collections.sort(reactionButtons, comparator);
                for (int i = 0; i < reactionButtons.size(); i++) {
                    reactionButtons.get(i).reactionCount.lastDrawnPosition = pointer++;
                }
            }
            hasUnreadReactions = MessageObject.hasUnreadReactions(messageObject.messageOwner);
        }
        for (int i = 0; i < oldButtons.size(); i++) {
            oldButtons.get(i).detach();
        }
        isEmpty = reactionButtons.isEmpty();
    }

    public void measure(int availableWidth, int gravity) {
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
            if (button.isSmall) {
                button.width = AndroidUtilities.dp(14);
                button.height = AndroidUtilities.dp(14);
            } else {
                button.width = (int) (AndroidUtilities.dp(8) + AndroidUtilities.dp(20) + AndroidUtilities.dp(4));
                if (button.avatarsDrawable != null && button.users.size() > 0) {
                    button.users.size();
                    int c1 = 1;
                    int c2 = button.users.size() > 1 ? button.users.size() - 1 : 0;
                    button.width += AndroidUtilities.dp(2) + c1 * AndroidUtilities.dp(20) + c2 * AndroidUtilities.dp(20) * 0.8f + AndroidUtilities.dp(1);
                    button.avatarsDrawable.height = AndroidUtilities.dp(26);
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
        if (gravity == Gravity.RIGHT && !reactionButtons.isEmpty()) {
            int fromP = 0;
            int startY = reactionButtons.get(0).y;
            for (int i = 0; i < reactionButtons.size(); i++) {
                if (reactionButtons.get(i).y != startY) {
                    int lineOffset = availableWidth - (reactionButtons.get(i - 1).x + reactionButtons.get(i - 1).width);
                    for (int k = fromP; k < i; k++) {
                        reactionButtons.get(k).x += lineOffset;
                    }
                    fromP = i;
                }
            }
            int last = reactionButtons.size() - 1;
            int lineOffset = availableWidth - (reactionButtons.get(last).x + reactionButtons.get(last).width);
            for (int k = fromP; k <= last; k++) {
                reactionButtons.get(k).x += lineOffset;
            }
        }
        lastLineX = currentX;
        if (gravity == Gravity.RIGHT) {
            width = availableWidth;
        } else {
            width = maxWidth;
        }
        height = currentY + (reactionButtons.size() == 0 ? 0 : AndroidUtilities.dp(26));
        drawServiceShaderBackground = 0f;
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
            float alpha = 1f;
            if (animationProgress != 1f && reactionButton.animationType == ANIMATION_TYPE_IN) {
                float s = 0.5f + 0.5f * animationProgress;
                alpha = animationProgress;
                canvas.scale(s, s, totalX + x + reactionButton.width / 2f, totalY + y + reactionButton.height / 2f);
            }
            reactionButton.draw(canvas, totalX + x, totalY + y, reactionButton.animationType == ANIMATION_TYPE_MOVE ? animationProgress : 1f, alpha, drawOnlyReaction != null);
            canvas.restore();
        }

        for (int i = 0; i < outButtons.size(); i++) {
            ReactionButton reactionButton = outButtons.get(i);
            float s = 0.5f + 0.5f * (1f - animationProgress);
            canvas.save();
            canvas.scale(s, s, totalX + reactionButton.x + reactionButton.width / 2f, totalY + reactionButton.y + reactionButton.height / 2f);
            outButtons.get(i).draw(canvas, totalX + reactionButton.x, totalY + reactionButton.y, 1f, (1f - animationProgress), false);
            canvas.restore();
        }
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
            ReactionButton lastButton = lastDrawingReactionButtonsTmp.get(button.key);
            if (lastButton != null && button.isSmall != lastButton.isSmall) {
                lastButton = null;
            }
            if (lastButton != null) {
                lastDrawingReactionButtonsTmp.remove(button.key);
                if (button.x != lastButton.x || button.y != lastButton.y || button.width != lastButton.width || button.count != lastButton.count || button.choosen != lastButton.choosen || button.avatarsDrawable != null || lastButton.avatarsDrawable != null) {
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
                    if (button.avatarsDrawable != null || lastButton.avatarsDrawable != null) {
                        if (button.avatarsDrawable == null) {
                            button.setUsers(new ArrayList<>());
                        }
                        if (lastButton.avatarsDrawable == null) {
                            lastButton.setUsers(new ArrayList<>());
                        }
                        if (!equalsUsersList(lastButton.users, button.users)) {
                            button.avatarsDrawable.animateFromState(lastButton.avatarsDrawable, currentAccount, false);
                        }
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

    private boolean equalsUsersList(ArrayList<TLObject> users, ArrayList<TLObject> users1) {
        if (users == null || users1 == null || users.size() != users1.size()) {
            return false;
        }
        for (int i = 0; i < users.size(); i++) {
            TLObject user1 = users.get(i);
            TLObject user2 = users1.get(i);
            if (user1 == null || user2 == null || getPeerId(user1) != getPeerId(user2)) {
                return false;
            }
        }
        return true;
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

    public ReactionButton getReactionButton(VisibleReaction visibleReaction) {
        String hash = visibleReaction.emojicon != null ? visibleReaction.emojicon : Long.toString(visibleReaction.documentId);
        if (isSmall) {
            ReactionButton button = lastDrawingReactionButtons.get(hash + "_");
            if (button != null) {
                return button;
            }
        }
        return lastDrawingReactionButtons.get(hash);
    }

    public void setScrimReaction(String scrimViewReaction) {
        this.scrimViewReaction = scrimViewReaction;
    }

    public class ReactionButton {

        private final TLRPC.ReactionCount reactionCount;
        private final boolean isSmall;
        public int animationType;
        public int animateFromX;
        public int animateFromY;
        public int animateFromWidth;
        public int fromBackgroundColor;
        public int fromTextColor;
        public int realCount;
        public int choosenOrder;
        public boolean drawImage = true;
        public boolean lastImageDrawn;
        public boolean wasDrawn;
        public String key;
        public boolean choosen;

        String countText;
        TLRPC.Reaction reaction;
        VisibleReaction visibleReaction;
        android.graphics.Rect drawingImageRect = new Rect();

        int count;
        public int x;
        public int y;
        public int width;
        public int height;
        ImageReceiver imageReceiver;
        AnimatedEmojiDrawable animatedEmojiDrawable;
        int animatedEmojiDrawableColor;
        CounterView.CounterDrawable counterDrawable;
        int backgroundColor;
        int textColor;
        int serviceBackgroundColor;
        int serviceTextColor;

        int lastDrawnTextColor;
        int lastDrawnBackgroundColor;
        boolean isSelected;
        AvatarsDrawable avatarsDrawable;
        ArrayList<TLObject> users;

        public ReactionButton(ReactionButton reuseFrom, TLRPC.ReactionCount reactionCount, boolean isSmall) {
            if (reuseFrom != null) {
                counterDrawable = reuseFrom.counterDrawable;
            }
            if (imageReceiver == null) {
                imageReceiver = new ImageReceiver();
            }
            if (counterDrawable == null) {
                counterDrawable = new CounterView.CounterDrawable(parentView, false, null);
            }
            this.reactionCount = reactionCount;
            this.reaction = reactionCount.reaction;
            this.visibleReaction = VisibleReaction.fromTLReaction(reactionCount.reaction);
            this.count = reactionCount.count;
            this.choosen = reactionCount.chosen;
            this.realCount = reactionCount.count;
            this.choosenOrder = reactionCount.chosen_order;
            this.isSmall = isSmall;
            if (this.reaction instanceof TLRPC.TL_reactionEmoji) {
                this.key = ((TLRPC.TL_reactionEmoji) this.reaction).emoticon;
            } else if (this.reaction instanceof TLRPC.TL_reactionCustomEmoji) {
                this.key = Long.toString(((TLRPC.TL_reactionCustomEmoji) this.reaction).document_id);
            } else {
                throw new RuntimeException("unsupported");
            }
            countText = Integer.toString(reactionCount.count);
            imageReceiver.setParentView(parentView);
            isSelected = reactionCount.chosen;
            counterDrawable.updateVisibility = false;
            counterDrawable.shortFormat = true;

            if (reaction != null) {
                if (visibleReaction.emojicon != null) {
                    TLRPC.TL_availableReaction r = MediaDataController.getInstance(currentAccount).getReactionsMap().get(visibleReaction.emojicon);
                    if (r != null) {
                        //imageReceiver.setImage(ImageLocation.getForDocument(r.static_icon), "40_40", svgThumb, "webp", r, 1);
                        SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(r.static_icon, Theme.key_windowBackgroundGray, 1.0f);
                        imageReceiver.setImage(ImageLocation.getForDocument(r.center_icon), "40_40_lastreactframe", svgThumb, "webp", r, 1);
                    }
                } else if (visibleReaction.documentId != 0) {
                    animatedEmojiDrawable = new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW, currentAccount, visibleReaction.documentId);
                }
            }

            counterDrawable.setSize(AndroidUtilities.dp(26), AndroidUtilities.dp(100));
            counterDrawable.textPaint = textPaint;
            counterDrawable.setCount(count, false);
            counterDrawable.setType(CounterView.CounterDrawable.TYPE_CHAT_REACTIONS);
            counterDrawable.gravity = Gravity.LEFT;
        }

        public void draw(Canvas canvas, float x, float y, float progress, float alpha, boolean drawOverlayScrim) {
            wasDrawn = true;
            ImageReceiver imageReceiver = animatedEmojiDrawable != null ? animatedEmojiDrawable.getImageReceiver() : this.imageReceiver;
            if (isSmall && imageReceiver != null) {
                imageReceiver.setAlpha(alpha);
                drawingImageRect.set((int) x, (int) y, AndroidUtilities.dp(14), AndroidUtilities.dp(14));
                imageReceiver.setImageCoords(drawingImageRect);
                imageReceiver.setRoundRadius(0);
                drawImage(canvas, alpha);
                return;
            }

            if (choosen) {
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
            updateColors(progress);
            textPaint.setColor(lastDrawnTextColor);
            paint.setColor(lastDrawnBackgroundColor);


            if (alpha != 1f) {
                textPaint.setAlpha((int) (textPaint.getAlpha() * alpha));
                paint.setAlpha((int) (paint.getAlpha() * alpha));
            }
            if (imageReceiver != null) {
                imageReceiver.setAlpha(alpha);
            }

            int w = width;
            if (progress != 1f && animationType == ANIMATION_TYPE_MOVE) {
                w = (int) (width * progress + animateFromWidth * (1f - progress));
            }
            AndroidUtilities.rectTmp.set(x, y, x + w, y + height);
            float rad = height / 2f;
            if (drawServiceShaderBackground > 0) {
                Paint paint1 = getThemedPaint(Theme.key_paint_chatActionBackground);
                Paint paint2 = getThemedPaint(Theme.key_paint_chatActionBackgroundDarken);
                int oldAlpha = paint1.getAlpha();
                int oldAlpha2 = paint2.getAlpha();
                paint1.setAlpha((int) (oldAlpha * alpha * drawServiceShaderBackground));
                paint2.setAlpha((int) (oldAlpha2 * alpha * drawServiceShaderBackground));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, rad, rad, paint1);
                if (hasGradientService()) {
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, rad, rad, paint2);
                }
                paint1.setAlpha(oldAlpha);
                paint2.setAlpha(oldAlpha2);
            }
            if (drawServiceShaderBackground < 1 && drawOverlayScrim) {
                Theme.MessageDrawable messageBackground = parentView.getCurrentBackgroundDrawable(false);
                if (messageBackground != null) {
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, rad, rad, messageBackground.getPaint());
                }
            }
            canvas.drawRoundRect(AndroidUtilities.rectTmp, rad, rad, paint);

            if (imageReceiver != null) {
                int size, X;
                if (animatedEmojiDrawable != null) {
                    size = AndroidUtilities.dp(24);
                    X = AndroidUtilities.dp(6);
                    imageReceiver.setRoundRadius(AndroidUtilities.dp(6));
                } else {
                    size = AndroidUtilities.dp(20);
                    X = AndroidUtilities.dp(8);
                    imageReceiver.setRoundRadius(0);
                }
                int Y = (int) ((height - size) / 2f);
                drawingImageRect.set((int) x + X, (int) y + Y, (int) x + X + size, (int) y + Y + size);
                imageReceiver.setImageCoords(drawingImageRect);
                drawImage(canvas, alpha);
            }

            if (counterDrawable != null && (count != 0 || counterDrawable.countChangeProgress != 1f)) {
                canvas.save();
                canvas.translate(x + AndroidUtilities.dp(8) + AndroidUtilities.dp(20) + AndroidUtilities.dp(2), y);
                counterDrawable.draw(canvas);
                canvas.restore();
            }

            if (avatarsDrawable != null) {
                canvas.save();
                canvas.translate(x + AndroidUtilities.dp(10) + AndroidUtilities.dp(20) + AndroidUtilities.dp(2), y);
                avatarsDrawable.setAlpha(alpha);
                avatarsDrawable.setTransitionProgress(progress);
                avatarsDrawable.onDraw(canvas);
                canvas.restore();
            }
        }

        private void updateColors(float progress) {
            lastDrawnTextColor = ColorUtils.blendARGB(fromTextColor, ColorUtils.blendARGB(textColor, serviceTextColor, drawServiceShaderBackground), progress);
            lastDrawnBackgroundColor = ColorUtils.blendARGB(fromBackgroundColor, ColorUtils.blendARGB(backgroundColor, serviceBackgroundColor, drawServiceShaderBackground), progress);
        }

        private void drawImage(Canvas canvas, float alpha) {
            ImageReceiver imageReceiver = animatedEmojiDrawable != null ? animatedEmojiDrawable.getImageReceiver() : this.imageReceiver;
            if (animatedEmojiDrawable != null && animatedEmojiDrawableColor != lastDrawnTextColor) {
                animatedEmojiDrawable.setColorFilter(new PorterDuffColorFilter(animatedEmojiDrawableColor = lastDrawnTextColor, PorterDuff.Mode.SRC_IN));
            }
            if (drawImage && ((realCount > 1 || !ReactionsEffectOverlay.isPlaying(messageObject.getId(), messageObject.getGroupId(), visibleReaction)) || !isSelected)) {
                ImageReceiver imageReceiver2 = animatedReactions.get(visibleReaction);
                boolean drawStaticImage = true;
                if (imageReceiver2 != null) {
                    if (imageReceiver2.getLottieAnimation() != null && imageReceiver2.getLottieAnimation().hasBitmap()) {
                        drawStaticImage = false;
                    }
                    if (alpha != 1f) {
                        imageReceiver2.setAlpha(alpha);
                        if (alpha <= 0) {
                            imageReceiver2.onDetachedFromWindow();
                            animatedReactions.remove(visibleReaction);
                        }
                    } else {
                        if (imageReceiver2.getLottieAnimation() != null && !imageReceiver2.getLottieAnimation().isRunning()) {
                            drawStaticImage = true;
                            float alpha1 = imageReceiver2.getAlpha() - 16f / 200;
                            if (alpha1 <= 0) {
                                imageReceiver2.onDetachedFromWindow();
                                animatedReactions.remove(visibleReaction);
                            } else {
                                imageReceiver2.setAlpha(alpha1);
                            }
                            parentView.invalidate();
                        }
                    }
                    imageReceiver2.setImageCoords(imageReceiver.getImageX() - imageReceiver.getImageWidth() / 2, imageReceiver.getImageY() - imageReceiver.getImageWidth() / 2, imageReceiver.getImageWidth() * 2, imageReceiver.getImageHeight() * 2);
                    imageReceiver2.draw(canvas);
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

        public void setUsers(ArrayList<TLObject> users) {
            this.users = users;
            if (users != null) {
                Collections.sort(users, usersComparator);
                if (avatarsDrawable == null) {
                    avatarsDrawable = new AvatarsDrawable(parentView, false);
                    avatarsDrawable.transitionDuration = ChatListItemAnimator.DEFAULT_DURATION;
                    avatarsDrawable.transitionInterpolator = ChatListItemAnimator.DEFAULT_INTERPOLATOR;
                    avatarsDrawable.setSize(AndroidUtilities.dp(20));
                    avatarsDrawable.width = AndroidUtilities.dp(100);
                    avatarsDrawable.height = height;
                    avatarsDrawable.setAvatarsTextSize(AndroidUtilities.dp(22));
                }
                if (attached) {
                    avatarsDrawable.onAttachedToWindow();
                }
                for (int i = 0; i < users.size(); i++) {
                    if (i == 3) {
                        break;
                    }
                    avatarsDrawable.setObject(i, currentAccount, users.get(i));
                }
                avatarsDrawable.commitTransition(false);
            }
        }

        public void attach() {
            if (imageReceiver != null) {
                imageReceiver.onAttachedToWindow();
            }
            if (avatarsDrawable != null) {
                avatarsDrawable.onAttachedToWindow();
            }
            if (animatedEmojiDrawable != null) {
                animatedEmojiDrawable.addView(parentView);
            }
        }

        public void detach() {
            if (imageReceiver != null) {
                imageReceiver.onDetachedFromWindow();
            }
            if (avatarsDrawable != null) {
                avatarsDrawable.onDetachedFromWindow();
            }
            if (animatedEmojiDrawable != null) {
                animatedEmojiDrawable.removeView(parentView);
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
                    if (messageObject.messageOwner.reactions.can_see_list || messageObject.getDialogId() >= 0) {
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
        long dialogId;

        @Override
        public int compare(ReactionButton o1, ReactionButton o2) {
            if (dialogId >= 0) {
                if (o1.isSelected != o2.isSelected) {
                    return o1.isSelected ? -1 : 1;
                } else if (o1.isSelected) {
                    if (o1.choosenOrder != o2.choosenOrder) {
                        return o1.choosenOrder - o2.choosenOrder;
                    }
                }
                return o1.reactionCount.lastDrawnPosition - o2.reactionCount.lastDrawnPosition;
            } else {
                if (o1.realCount != o2.realCount) {
                    return o2.realCount - o1.realCount;
                }
            }
//            TLRPC.TL_availableReaction availableReaction1 = MediaDataController.getInstance(currentAccount).getReactionsMap().get(o1.reaction);
//            TLRPC.TL_availableReaction availableReaction2 = MediaDataController.getInstance(currentAccount).getReactionsMap().get(o2.reaction);
//            if (availableReaction1 != null && availableReaction2 != null) {
//                return availableReaction1.positionInList - availableReaction2.positionInList;
//            }
            return o1.reactionCount.lastDrawnPosition - o2.reactionCount.lastDrawnPosition;
        }
    }

    public void onAttachToWindow() {
        attached = true;
        for (int i = 0; i < reactionButtons.size(); i++) {
            reactionButtons.get(i).attach();
        }
    }

    public void onDetachFromWindow() {
        attached = false;
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

    public void animateReaction(VisibleReaction reaction) {
        if (reaction.documentId == 0 && animatedReactions.get(reaction) == null) {
            ImageReceiver imageReceiver = new ImageReceiver();
            imageReceiver.setParentView(parentView);
            imageReceiver.setUniqKeyPrefix(Integer.toString(animationUniq++));
            TLRPC.TL_availableReaction r = MediaDataController.getInstance(currentAccount).getReactionsMap().get(reaction.emojicon);
            if (r != null) {
                imageReceiver.setImage(ImageLocation.getForDocument(r.center_icon), "40_40_nolimit", null, "tgs", r, 1);
            }
            imageReceiver.setAutoRepeat(0);
            imageReceiver.onAttachedToWindow();
            animatedReactions.put(reaction, imageReceiver);
        }
    }

    public static class VisibleReaction {

        public String emojicon;
        public long documentId;

        public long hash;

        public static VisibleReaction fromTLReaction(TLRPC.Reaction reaction) {
            VisibleReaction visibleReaction = new VisibleReaction();
            if (reaction instanceof TLRPC.TL_reactionEmoji) {
                visibleReaction.emojicon = ((TLRPC.TL_reactionEmoji) reaction).emoticon;
                visibleReaction.hash = visibleReaction.emojicon.hashCode();
            } else if (reaction instanceof TLRPC.TL_reactionCustomEmoji) {
                visibleReaction.documentId = ((TLRPC.TL_reactionCustomEmoji) reaction).document_id;
                visibleReaction.hash = visibleReaction.documentId;
            }

            return visibleReaction;
        }

        public static VisibleReaction fromEmojicon(TLRPC.TL_availableReaction reaction) {
            VisibleReaction visibleReaction = new VisibleReaction();
            visibleReaction.emojicon = reaction.reaction;
            visibleReaction.hash = visibleReaction.emojicon.hashCode();
            return visibleReaction;
        }

        public static VisibleReaction fromEmojicon(String reaction) {
            if (reaction == null) {
                reaction = "";
            }

            VisibleReaction visibleReaction = new VisibleReaction();
            if (reaction.startsWith("animated_")) {
                try {
                    visibleReaction.documentId = Long.parseLong(reaction.substring(9));
                    visibleReaction.hash = visibleReaction.documentId;
                } catch (Exception ignore) {
                    visibleReaction.emojicon = reaction;
                    visibleReaction.hash = visibleReaction.emojicon.hashCode();
                }
            } else {
                visibleReaction.emojicon = reaction;
                visibleReaction.hash = visibleReaction.emojicon.hashCode();
            }
            return visibleReaction;
        }

        public static VisibleReaction fromCustomEmoji(Long documentId) {
            VisibleReaction visibleReaction = new VisibleReaction();
            visibleReaction.documentId = documentId;
            visibleReaction.hash = visibleReaction.documentId;
            return visibleReaction;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VisibleReaction that = (VisibleReaction) o;
            return documentId == that.documentId && Objects.equals(emojicon, that.emojicon);
        }

        @Override
        public int hashCode() {
            return Objects.hash(emojicon, documentId);
        }

        public boolean isSame(TLRPC.Reaction reaction) {
            return false;
        }
    }
}
