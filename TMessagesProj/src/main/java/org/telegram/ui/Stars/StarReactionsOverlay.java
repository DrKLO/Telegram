package org.telegram.ui.Stars;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.scaleRect;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.BaseCell;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.GradientClip;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;

public class StarReactionsOverlay extends View {

    private final ChatActivity chatActivity;

    private BaseCell cell;
    private int messageId;

//    private final Camera camera = new Camera();
//    private final Matrix matrix = new Matrix();
    private final int[] pos = new int[2];
    private final int[] pos2 = new int[2];
    private final RectF reactionBounds = new RectF();
    private final RectF clickBounds = new RectF();
    private final Paint shadowPaint = new Paint();
    private final Paint redPaint = new Paint();

    private boolean counterShown;
//    private final AnimatedFloat counterX = new AnimatedFloat(this, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedFloat counterAlpha = new AnimatedFloat(this, 0, 420, CubicBezierInterpolator.EASE_OUT_QUINT);
    private final AnimatedTextView.AnimatedTextDrawable counter = new AnimatedTextView.AnimatedTextDrawable();

    private final GradientClip clip = new GradientClip();

    public StarReactionsOverlay(ChatActivity chatActivity) {
        super(chatActivity.getContext());
        this.chatActivity = chatActivity;

        counter.setCallback(this);
        counter.setHacks(false, true, true);
        counter.setTextSize(dp(40));
        counter.setTypeface(AndroidUtilities.getTypeface("fonts/num.otf"));
        counter.setShadowLayer(dp(12), 0, dp(3.5f), 0x00000000);
        counter.setOverrideFullWidth(AndroidUtilities.displaySize.x);
        counter.setTextColor(0xFFFFFFFF);
        counter.setGravity(Gravity.CENTER);

        hideCounterRunnable = () -> {
            counterShown = false;
            invalidate();
            checkBalance();
            hide();
        };

        longPressRunnable = () -> {
            if (cell == null) return;
            try {
                cell.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            } catch (Exception ignored) {}
            onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0));

            ArrayList<TLRPC.MessageReactor> reactors = null;
            final MessageObject msg;
            if (cell instanceof ChatMessageCell) {
                final ChatMessageCell messageCell = (ChatMessageCell) cell;
                msg = messageCell.getPrimaryMessageObject();
                if (msg == null) return;
                if (msg != null && msg.messageOwner != null && msg.messageOwner.reactions != null) {
                    reactors = msg.messageOwner.reactions.top_reactors;
                }
            } else if (cell instanceof ChatActionCell) {
                final ChatActionCell actionCell = (ChatActionCell) cell;
                msg = actionCell.getMessageObject();
                if (msg == null) return;
                if (msg != null && msg.messageOwner != null && msg.messageOwner.reactions != null) {
                    reactors = msg.messageOwner.reactions.top_reactors;
                }
            } else {
                return;
            }

            StarsController.getInstance(msg.currentAccount).commitPaidReaction();

            final TLRPC.ChatFull chatFull = chatActivity.getCurrentChatInfo();
            final StarsReactionsSheet sheet = new StarsReactionsSheet(getContext(), chatActivity.getCurrentAccount(), chatActivity.getDialogId(), chatActivity, msg, reactors, chatFull == null || chatFull.paid_reactions_available, false, 0, chatActivity.getResourceProvider());
            sheet.setMessageCell(chatActivity, msg.getId(), cell);
            sheet.show();
        };
    }

    private MessageObject getMessageObject() {
        if (cell instanceof ChatMessageCell) {
            return ((ChatMessageCell) cell).getPrimaryMessageObject();
        } else if (cell instanceof ChatActionCell) {
            return ((ChatActionCell) cell).getMessageObject();
        } else {
            return null;
        }
    }

    private void checkBalance() {
        if (getMessageObject() != null) {
            final MessageObject msg = getMessageObject();
            final StarsController starsController = StarsController.getInstance(chatActivity.getCurrentAccount());
            final long totalStars = starsController.getPendingPaidReactions(msg);
            if (starsController.balanceAvailable() && starsController.getBalance(false) < totalStars) {
                StarsController.getInstance(chatActivity.getCurrentAccount()).undoPaidReaction();
                final long dialogId = chatActivity.getDialogId();
                String name;
                if (dialogId >= 0) {
                    TLRPC.User user = chatActivity.getMessagesController().getUser(dialogId);
                    name = UserObject.getForcedFirstName(user);
                } else {
                    TLRPC.Chat chat = chatActivity.getMessagesController().getChat(-dialogId);
                    name = chat == null ? "" : chat.title;
                }
                new StarsIntroActivity.StarsNeededSheet(chatActivity.getContext(), chatActivity.getResourceProvider(), totalStars, StarsIntroActivity.StarsNeededSheet.TYPE_REACTIONS, name, () -> {
                    starsController.sendPaidReaction(msg, chatActivity, totalStars, true, true, null);
                }, 0).show();
            }
        }
    }

    public void setMessageCell(BaseCell cell) {
        if (this.cell == cell) return;
        if (this.cell instanceof ChatMessageCell) {
            ((ChatMessageCell) this.cell).setScrimReaction(null);
            ((ChatMessageCell) this.cell).setInvalidateListener(null);
            this.cell.invalidate();
        } else if (this.cell instanceof ChatActionCell) {
            ((ChatActionCell) this.cell).setScrimReaction(null);
            ((ChatActionCell) this.cell).setInvalidateListener(null);
            this.cell.invalidate();
        }
        this.cell = cell;
        this.messageId = getMessageObject() == null ? 0 : getMessageObject().getId();
        if (this.cell instanceof ChatMessageCell) {
            this.cell.invalidate();
            ((ChatMessageCell) this.cell).setInvalidateListener(this::invalidate);
        } else if (this.cell instanceof ChatActionCell) {
            this.cell.invalidate();
            ((ChatActionCell) this.cell).setInvalidateListener(this::invalidate);
        }
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (cell instanceof ChatMessageCell) {
            if (!((ChatMessageCell) cell).isCellAttachedToWindow()) {
                return;
            }
        } else if (cell instanceof ChatActionCell) {
            if (!((ChatActionCell) cell).isCellAttachedToWindow()) {
                return;
            }
        }
        final MessageObject msg = getMessageObject();
        if ((msg != null ? msg.getId() : 0) != messageId) {
            setMessageCell(null);
            return;
        }
        final ReactionsLayoutInBubble reactionsLayoutInBubble = getReactionsLayoutInBubble();
        if (reactionsLayoutInBubble == null) {
            setMessageCell(null);
            return;
        }

        final float s = AndroidUtilities.lerp(1, 1.8f, focus);

        float clipTop = chatActivity.getClipTop(), clipBottom = chatActivity.getClipBottom();
        canvas.save();
        canvas.clipRect(0, clipTop * (1f - focus), getWidth(), getHeight() - clipBottom * (1f - focus));

        getLocationInWindow(pos2);
        cell.getLocationInWindow(pos);
        pos[1] += (int) chatActivity.drawingChatListViewYoffset;
        canvas.save();
//        canvas.saveLayerAlpha(cell.getBackgroundDrawableLeft(), 0, cell.getBackgroundDrawableRight(), cell.getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);

        ReactionsLayoutInBubble.ReactionButton btn = reactionsLayoutInBubble.getReactionButton("stars");
        Integer hash = null;
        if (btn != null) {
            final int btnX = pos[0] - pos2[0] + reactionsLayoutInBubble.x + btn.x;
            final int btnY = pos[1] - pos2[1] + reactionsLayoutInBubble.y + btn.y;

            reactionBounds.set(btnX, btnY, btnX + btn.width, btnY + btn.height);
            scaleRect(reactionBounds, s, btnX + btn.width * .1f, btnY + btn.height / 2f);

            shadowPaint.setColor(0);
            shadowPaint.setShadowLayer(dp(12), 0, dp(3), Theme.multAlpha(0x55000000, focus));
            canvas.drawRoundRect(reactionBounds, reactionBounds.height() / 2f, reactionBounds.height() / 2f, shadowPaint);

            canvas.scale(s, s, btnX + btn.width * .1f, btnY + btn.height / 2f);

            hash = btn.reaction.hashCode();
        }
        canvas.translate(pos[0] - pos2[0], pos[1] - pos2[1] + cell.getPaddingTop());
        if (cell instanceof ChatMessageCell) {
            final ChatMessageCell messageCell = (ChatMessageCell) cell;
            messageCell.setScrimReaction(null);
            messageCell.drawReactionsLayout(canvas, 1f, hash);
            messageCell.drawReactionsLayoutOverlay(canvas, 1f);
            messageCell.setScrimReaction(hash);
        } else if (cell instanceof ChatActionCell) {
            final ChatActionCell actionCell = (ChatActionCell) cell;
            actionCell.setScrimReaction(null);
            actionCell.drawReactionsLayout(canvas, true, hash);
            actionCell.drawReactionsLayoutOverlay(canvas, true);
            actionCell.setScrimReaction(hash);
        }
        canvas.restore();

        canvas.restore();

        if (btn != null) {
            clickBounds.set(reactionBounds);
            clickBounds.inset(-dp(42), -dp(42));

            final int effectSize = (int) (dp(90) * s);
            for (int i = 0; i < effects.size(); ++i) {
                RLottieDrawable drawable = effects.get(i);
                if (drawable.getCurrentFrame() >= drawable.getFramesCount()) {
                    effects.remove(i);
                    i--;
                    continue;
                }

                drawable.setBounds(
                    (int) (reactionBounds.left + dp(4 + 11) * s - effectSize / 2f),
                    (int) (reactionBounds.centerY() - effectSize / 2f),
                    (int) (reactionBounds.left + dp(4 + 11) * s + effectSize / 2f),
                    (int) (reactionBounds.centerY() + effectSize / 2f)
                );
                drawable.setAlpha((int) (0xFF * focus));
                drawable.draw(canvas);
            }

            final float cx = reactionBounds.centerX();
            final float cy = reactionBounds.top - dp(12 + 24);
            canvas.save();
            float t = counterAlpha.set(counterShown);
            canvas.translate(0, counterShown ? dp(60) * (1f - t) : -dp(30) * (1f - t));
            final float counterScale = AndroidUtilities.lerp(counterShown ? 1.8f : 1.3f, 1f, t);
            canvas.scale(counterScale, counterScale, cx, cy);
            counter.setAlpha((int) (0xFF * t));
            counter.setShadowLayer(dp(12), 0, dp(3.5f), Theme.multAlpha(0xAA000000, t));
            counter.setBounds(cx - dp(100), reactionBounds.top - dp(24 + 24), cx + dp(100), reactionBounds.top - dp(24));
            counter.draw(canvas);
            canvas.restore();

        }

        if (!counterShown) {
            checkBalance();
        }

        invalidate();
    }

    public ReactionsLayoutInBubble getReactionsLayoutInBubble() {
        if (this.cell instanceof ChatMessageCell) {
            return ((ChatMessageCell) this.cell).reactionsLayoutInBubble;
        } else if (this.cell instanceof ChatActionCell) {
            return ((ChatActionCell) this.cell).reactionsLayoutInBubble;
        } else {
            return null;
        }
    }

    private boolean pressed;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (cell == null || hidden) return false;
        final ReactionsLayoutInBubble reactionsLayoutInBubble = getReactionsLayoutInBubble();
        if (reactionsLayoutInBubble == null) return false;
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (clickBounds.contains(event.getX(), event.getY())) {
                pressed = true;
                final ReactionsLayoutInBubble.ReactionButton btn = reactionsLayoutInBubble.getReactionButton("stars");
                if (btn != null) btn.bounce.setPressed(true);
                AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
                AndroidUtilities.runOnUIThread(longPressRunnable, ViewConfiguration.getLongPressTimeout());
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            final ReactionsLayoutInBubble.ReactionButton btn = reactionsLayoutInBubble.getReactionButton("stars");
            if (event.getAction() == MotionEvent.ACTION_UP) {
                tap(event.getX(), event.getY(), true, true);
            }
            if (btn != null) btn.bounce.setPressed(false);
            pressed = false;
            AndroidUtilities.cancelRunOnUIThread(longPressRunnable);
        }
        return pressed;
    }

    private final Runnable longPressRunnable;

    private float focus;
    private ValueAnimator focusAnimator;
    public void focusTo(float dst, Runnable whenDone) {
        if (focusAnimator != null) {
            ValueAnimator anm = focusAnimator;
            focusAnimator = null;
            anm.cancel();
        }
        focusAnimator = ValueAnimator.ofFloat(focus, dst);
        focusAnimator.addUpdateListener(anm -> {
            focus = (float) anm.getAnimatedValue();
            invalidate();
        });
        focusAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                focus = dst;
                invalidate();
                if (animation == focusAnimator && whenDone != null) {
                    whenDone.run();
                }
            }
        });
        focusAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        focusAnimator.setDuration(320);
        focusAnimator.start();
    }

    public void tap(float x, float y, boolean send, boolean ripple) {
        if (cell == null || hidden) return;

        final MessageObject msg = getMessageObject();
        final ReactionsLayoutInBubble reactionsLayoutInBubble = getReactionsLayoutInBubble();
        if (msg == null || reactionsLayoutInBubble == null) return;
        final StarsController starsController = StarsController.getInstance(chatActivity.getCurrentAccount());

        playEffect();
        ReactionsLayoutInBubble.ReactionButton btn = reactionsLayoutInBubble.getReactionButton("stars");
        if (btn != null) btn.startAnimation();
        if (send) {
            try {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
            } catch (Exception ignore) {}
            StarsController.getInstance(chatActivity.getCurrentAccount()).sendPaidReaction(msg, chatActivity, +1, true, false, null);
        }
        counter.cancelAnimation();
        counter.setText("+" + starsController.getPendingPaidReactions(msg));
        counterShown = true;
        AndroidUtilities.cancelRunOnUIThread(hideCounterRunnable);
        AndroidUtilities.runOnUIThread(hideCounterRunnable, 1500);

        if (ripple) {
            final long now = System.currentTimeMillis();
            if (now - lastRippleTime < 100) {
                accumulatedRippleIntensity += .5f;
            } else {
                accumulatedRippleIntensity *= Utilities.clamp(1f - (now - lastRippleTime - 100) / 200f, 1f, 0f);
                if (getMeasuredWidth() == 0 && chatActivity.getLayoutContainer() != null) {
                    chatActivity.getLayoutContainer().getLocationInWindow(pos2);
                } else {
                    getLocationInWindow(pos2);
                }
                LaunchActivity.makeRipple(pos2[0] + x, pos2[1] + y, Utilities.clamp(accumulatedRippleIntensity, 0.9f, 0.3f));
                accumulatedRippleIntensity = 0;
                lastRippleTime = now;
            }
        }
    }

    private long lastRippleTime;
    private float accumulatedRippleIntensity;

    private Runnable hideCounterRunnable;

    public boolean hidden;
    public void hide() {
        hidden = true;
        AndroidUtilities.cancelRunOnUIThread(hideCounterRunnable);
        counter.setText("");
        counterShown = false;
        invalidate();
        focusTo(0f, () -> {
            setMessageCell(null);
            clearEffects();
        });
    }

    public boolean isShowing(MessageObject obj) {
        return obj != null && obj.getId() == messageId;
    }

    public void show() {
        hidden = false;
        focusTo(1f, null);
    }

    private final ArrayList<RLottieDrawable> effects = new ArrayList<>();
    private final int[] effectAssets = new int[] {
        R.raw.star_reaction_effect1,
        R.raw.star_reaction_effect2,
        R.raw.star_reaction_effect3,
        R.raw.star_reaction_effect4,
        R.raw.star_reaction_effect5
    };

    public void playEffect() {
        while (effects.size() > 4) {
            RLottieDrawable drawable = effects.remove(0);
            drawable.recycle(true);
        }
        final int asset = effectAssets[Utilities.fastRandom.nextInt(effectAssets.length)];
        RLottieDrawable drawable = new RLottieDrawable(asset, "" + asset, dp(70), dp(70));
        drawable.setMasterParent(this);
        drawable.setAllowDecodeSingleFrame(true);
        drawable.setAutoRepeat(0);
        drawable.start();
        effects.add(drawable);
        invalidate();
    }

    public void clearEffects() {
        for (RLottieDrawable effect : effects) {
            effect.recycle(true);
        }
        effects.clear();
    }


}
