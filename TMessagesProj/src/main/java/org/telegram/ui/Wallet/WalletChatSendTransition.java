package org.telegram.ui.Wallet;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.graphics.RectF;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

import org.telegram.tgnet.tl.TL_wallet;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.Premium.GLIcon.WalletDiamondTextureView;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.ChatActivity;

public final class WalletChatSendTransition {
    private final ViewGroup root;
    private final View outgoing;
    private final ChatAttachAlert sheet;
    private final WalletDiamondView source;
    private final ChatActivity chat;
    private final TL_wallet.walletTransaction transaction;
    private final Runnable completion;
    private final FrameLayout overlay;
    private final WalletDiamondView diamond;
    private final WalletSendParticles particles = new WalletSendParticles();
    private final PathInterpolator exitCurve = new PathInterpolator(.3f, 0f, .8f, .15f);
    private final float sourceAlpha;
    private final AnimatorSet animation = new AnimatorSet();
    private final RectF from;
    private final RectF to = new RectF();
    private ChatActionCell landing;
    private final WalletDiamondTextureView coolDiamond;
    private float progress;
    private boolean renderFailed;
    private boolean ready, started, finished;
    private final long created = SystemClock.uptimeMillis();
    private final Runnable awaitLanding = this::awaitLanding;

    WalletChatSendTransition(ViewGroup root, View outgoing, WalletDiamondView source, ChatActivity chat,
                         TL_wallet.walletTransaction transaction, Runnable completion) {
        this(root, outgoing, source, chat, transaction, completion, null);
    }

    public WalletChatSendTransition(ChatAttachAlert sheet, WalletDiamondView source, ChatActivity chat,
                                    TL_wallet.walletTransaction transaction, Runnable completion) {
        this(sheet.getContainer(), sheet.getSheetContainer(), source, chat, transaction, completion, sheet);
    }

    private WalletChatSendTransition(ViewGroup root, View outgoing, WalletDiamondView source, ChatActivity chat,
                                     TL_wallet.walletTransaction transaction, Runnable completion, ChatAttachAlert sheet) {
        this.sheet = sheet;
        this.root = root;
        this.outgoing = outgoing;
        this.source = source;
        sourceAlpha = source.getAlpha();
        this.chat = chat;
        this.transaction = transaction;
        this.completion = completion;
        from = WalletSendTransition.bounds(source, root);
        updateSourceBounds();
        overlay = new FrameLayout(root.getContext()) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                canvas.save();
                canvas.translate(-getLeft(), -getTop());
                particles.draw(canvas);
                canvas.restore();
                super.dispatchDraw(canvas);
                if (particles.isActive()) postInvalidateOnAnimation();
            }
        };
        overlay.setClipChildren(false);
        overlay.setClipToPadding(false);
        overlay.setClickable(true);
        diamond = new WalletDiamondView(root.getContext(), 60, true);
        diamond.setContinuousRotation(540f);
        diamond.copyRotationFrom(source);
        diamond.setAlpha(0f);
        overlay.addView(diamond, LayoutHelper.createFrame(60, 60, Gravity.TOP | Gravity.LEFT));
        coolDiamond = new WalletDiamondTextureView(root.getContext(), this::updateCoolPose) {
            @Override
            protected void onRenderError() {
                renderFailed = true;
            }
        };
        // TextureView needs a draw to report its first presented frame.
        coolDiamond.setAlpha(.001f);
        coolDiamond.setPaused(false);
        overlay.addView(coolDiamond, LayoutHelper.createFrame(60, 60, Gravity.TOP | Gravity.LEFT));
        root.addView(overlay, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        position(from.centerX(), from.centerY(), from.width());
        diamond.whenReady(() -> ready = true);
        ValueAnimator flight = ValueAnimator.ofFloat(0f, 1f);
        flight.setDuration(600);
        flight.setInterpolator(new LinearInterpolator());
        flight.addUpdateListener(a -> drawFrame((float) a.getAnimatedValue()));
        animation.playTogether(flight);
        animation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                // Hide from the still-focused sheet window, at the same time as
                // the diamond and sheet begin moving (including the fallback).
                if (sheet != null) org.telegram.messenger.AndroidUtilities.hideKeyboard(source);
            }

            @Override
            public void onAnimationEnd(Animator animator) { finish(true); }
            @Override
            public void onAnimationCancel(Animator animator) { finish(false); }
        });
        overlay.post(awaitLanding);
    }

    public AnimatorSet animator() { return animation; }

    private void awaitLanding() {
        if (finished || started) return;
        updateSourceBounds();
        position(from.centerX(), from.centerY(), from.width());
        ChatActionCell cell = chat.findWalletSendLanding(transaction);
        if (cell != null && cell.getWidth() > 0) {
            if (landing != cell) {
                if (landing != null) landing.walletActionLayout.holdDiamondForFlight(false);
                landing = cell;
            }
            View target = landing.walletActionLayout.getDiamondSurface();
            if (ready && coolDiamond.isReady() && target instanceof WalletDiamondTextureView
                    && ((WalletDiamondTextureView) target).isReady()
                    && target.getWidth() > 0 && !landing.isLayoutRequested()) {
                to.set(targetBounds(target));
                if (to.centerY() > 0 && to.centerY() < root.getHeight() && sourceAlpha > .01f) {
                    started = true;
                    landing.walletActionLayout.holdDiamondForFlight(true);
                    diamond.copyRotationFrom(source);
                    diamond.setAlpha(sourceAlpha);
                    source.setAlpha(0f);
                    particles.burst(from.centerX(), from.centerY());
                    animation.start();
                    return;
                }
                to.setEmpty();
            }
        }
        // Never hold navigation hostage to a slow GL driver, missing row, or destroyed window.
        if (SystemClock.uptimeMillis() - created > 700 || renderFailed || !root.isAttachedToWindow()) {
            started = true;
            diamond.setAlpha(0f);
            coolDiamond.setAlpha(0f);
            animation.setDuration(180);
            animation.start();
            return;
        }
        overlay.postOnAnimation(awaitLanding);
    }

    private void drawFrame(float progress) {
        if (finished) return;
        updateSourceBounds();
        float exitProgress = exitCurve.getInterpolation(Math.min(1f, progress * 2));
        if (sheet != null) sheet.setGramSendTransitionProgress(exitProgress);
        else outgoing.setTranslationY(outgoing.getHeight() * exitProgress);
        if (landing == null || to.isEmpty()) return;
        // RecyclerView can replace a cell while the transfer changes from pending to sent.
        ChatActionCell cell = chat.findWalletSendLanding(transaction);
        if (cell != landing) {
            landing.walletActionLayout.holdDiamondForFlight(false);
            landing = cell;
        }
        if (landing == null || renderFailed) {
            to.setEmpty();
            diamond.setAlpha(0f);
            coolDiamond.setAlpha(0f);
            return;
        }
        landing.walletActionLayout.holdDiamondForFlight(true);
        View target = landing.walletActionLayout.getDiamondSurface();
        if (target == null || !target.isAttachedToWindow()) {
            to.setEmpty();
            diamond.setAlpha(0f);
            coolDiamond.setAlpha(0f);
            return;
        }
        to.set(targetBounds(target));
        this.progress = progress;
        float fade = Math.max(0f, Math.min(1f, (progress - .25f) / .5f));
        fade = fade * fade * (3f - 2f * fade);
        diamond.setAlpha(sourceAlpha * (1f - fade));
        coolDiamond.setAlpha(fade);
        updateCoolPose();
        float q = 1f - progress;
        float controlX = from.centerX() + (to.centerX() - from.centerX()) * .35f;
        float controlY = Math.min(from.centerY(), to.centerY()) - dp(150);
        float x = q * q * from.centerX() + 2 * q * progress * controlX + progress * progress * to.centerX();
        float y = q * q * from.centerY() + 2 * q * progress * controlY + progress * progress * to.centerY();
        position(x, y, from.width() + (to.width() - from.width()) * progress);
        if (progress < .92f) particles.trail(x, y);
        overlay.invalidate();
    }

    private void updateSourceBounds() {
        if (!source.isAttachedToWindow() || source.getRootView() != root.getRootView()) return;
        if (sheet == null) {
            WalletSendTransition.updateSourceBounds(source, outgoing, root, from);
        } else {
            from.set(WalletSendTransition.bounds(source, root));
            // Include the keyboard's View/canvas pan, excluding only our sheet exit.
            from.offset(0, sheet.currentPanTranslationY - sheet.getGramSendExitTranslationY());
        }
    }

    private RectF targetBounds(View target) {
        if (sheet == null) return WalletSendTransition.bounds(target, root);
        ViewGroup targetRoot = (ViewGroup) target.getRootView();
        RectF rect = WalletSendTransition.bounds(target, targetRoot);
        int[] location = new int[2];
        targetRoot.getLocationOnScreen(location);
        rect.offset(location[0], location[1]);
        root.getLocationOnScreen(location);
        rect.offset(-location[0], -location[1]);
        return rect;
    }

    private void position(float x, float y, float width) {
        float side = dp(60);
        diamond.setTranslationX(x - overlay.getLeft() - side / 2f);
        diamond.setTranslationY(y - overlay.getTop() - side / 2f);
        diamond.setScaleX(width / side);
        diamond.setScaleY(width / side);
        coolDiamond.setTranslationX(diamond.getTranslationX());
        coolDiamond.setTranslationY(diamond.getTranslationY());
        coolDiamond.setScaleX(width / side);
        coolDiamond.setScaleY(width / side);
    }

    private void updateCoolPose() {
        if (coolDiamond == null) return;
        float yaw = diamond.getFlightYaw();
        float pitch = diamond.getFlightPitch();
        if (landing != null) {
            float blend = Math.max(0f, Math.min(1f, (progress - .55f) / .45f));
            blend = blend * blend * (3f - 2f * blend);
            yaw += (float) Math.IEEEremainder(landing.walletActionLayout.getFlightYaw() - yaw, Math.PI * 2) * blend;
            pitch += (landing.walletActionLayout.getFlightPitch() - pitch) * blend;
        }
        // Use the same .cool renderer and pose as the chat icon at the handoff.
        coolDiamond.setPose(yaw, pitch, true, true);
    }

    public void cancel() {
        if (finished) return;
        if (animation.isStarted()) animation.cancel();
        else finish(false);
    }

    private void finish(boolean landed) {
        if (finished) return;
        finished = true;
        overlay.removeCallbacks(awaitLanding);
        diamond.whenReady(null);
        if (landing != null) {
            landing.walletActionLayout.holdDiamondForFlight(false);
            if (landed && progress >= 1f && !to.isEmpty() && landing.isAttachedToWindow()
                    && GramWallet.messageMatchesTransaction(landing.getMessageObject(), transaction)) {
                float controlY = Math.min(from.centerY(), to.centerY()) - dp(150);
                float arrivalSpeed = 2f * (to.centerY() - controlY) / .6f
                        / org.telegram.messenger.AndroidUtilities.density;
                landing.walletActionLayout.playLanding(arrivalSpeed);
            }
        }
        coolDiamond.setPaused(true);
        overlay.removeView(coolDiamond);
        diamond.setPaused(true);
        overlay.removeView(diamond);
        overlay.setClickable(false);
        source.setAlpha(sourceAlpha);
        if (sheet == null) outgoing.setTranslationY(0f);
        else sheet.setGramSendTransitionProgress(1f);
        // Keep the last trail particles alive after the dialog window disappears.
        ViewGroup particleRoot = root;
        if (sheet != null && landed && particles.isActive() && chat.getParentLayout() != null) {
            ViewGroup chatRoot = chat.getParentLayout().getView();
            if (chatRoot.isAttachedToWindow()) {
                int[] sheetLocation = new int[2];
                int[] chatLocation = new int[2];
                root.getLocationOnScreen(sheetLocation);
                chatRoot.getLocationOnScreen(chatLocation);
                root.removeView(overlay);
                overlay.setTranslationX(sheetLocation[0] - chatLocation[0]);
                overlay.setTranslationY(sheetLocation[1] - chatLocation[1]);
                chatRoot.addView(overlay, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                particleRoot = chatRoot;
            }
        }
        completion.run();
        final ViewGroup cleanupRoot = particleRoot;
        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> cleanupRoot.removeView(overlay), 650);
    }
}
