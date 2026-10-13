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
import android.view.ViewParent;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

import org.telegram.tgnet.tl.TL_wallet;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ChatAttachAlert;

public final class WalletSendTransition {
    private final ChatAttachAlert sheet;
    private final ViewGroup root;
    private final View outgoing;
    private final WalletDiamondView source;
    private final WalletActivity wallet;
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
    private TransactionCell landing;
    private boolean ready, started, finished;
    private final long created = SystemClock.uptimeMillis();
    private final Runnable awaitLanding = this::awaitLanding;

    WalletSendTransition(ViewGroup root, View outgoing, WalletDiamondView source, WalletActivity wallet,
                         TL_wallet.walletTransaction transaction, Runnable completion) {
        this(root, outgoing, source, wallet, transaction, completion, null);
    }

    public WalletSendTransition(ChatAttachAlert sheet, WalletDiamondView source, WalletActivity wallet,
                                TL_wallet.walletTransaction transaction, Runnable completion) {
        this(sheet.getContainer(), sheet.getSheetContainer(), source, wallet, transaction, completion, sheet);
    }

    private WalletSendTransition(ViewGroup root, View outgoing, WalletDiamondView source, WalletActivity wallet,
                                 TL_wallet.walletTransaction transaction, Runnable completion, ChatAttachAlert sheet) {
        this.sheet = sheet;
        this.root = root;
        this.outgoing = outgoing;
        this.source = source;
        sourceAlpha = source.getAlpha();
        this.wallet = wallet;
        this.transaction = transaction;
        this.completion = completion;
        from = bounds(source, root);
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
        diamond = new WalletDiamondView(root.getContext(), 60);
        diamond.setContinuousRotation(540f);
        diamond.copyRotationFrom(source);
        diamond.setAlpha(0f);
        overlay.addView(diamond, LayoutHelper.createFrame(60, 60, Gravity.TOP | Gravity.LEFT));
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
                if (sheet != null) org.telegram.messenger.AndroidUtilities.hideKeyboard(source);
            }
            @Override
            public void onAnimationEnd(Animator animator) { finish(); }
            @Override
            public void onAnimationCancel(Animator animator) { finish(); }
        });
        wallet.prepareSendLanding(transaction);
        overlay.post(awaitLanding);
    }

    public AnimatorSet animator() { return animation; }

    private void awaitLanding() {
        if (finished || started) return;
        updateSourceBounds();
        position(from.centerX(), from.centerY(), from.width());
        TransactionCell cell = wallet.findSendLanding(transaction);
        if (cell != null && cell.getWidth() > 0) {
            if (landing != cell) {
                if (landing != null) landing.holdForFlight(false);
                landing = cell;
                landing.holdForFlight(true);
            }
            WalletDiamondView target = landing.getPendingDiamond();
            if (ready && target != null && target.getWidth() > 0 && !landing.isLayoutRequested()) {
                to.set(targetBounds(target));
                if (to.centerY() > 0 && to.centerY() < root.getHeight()) {
                    started = true;
                    diamond.copyRotationFrom(source);
                    diamond.setAlpha(1f);
                    source.setAlpha(0f);
                    particles.burst(from.centerX(), from.centerY());
                    animation.start();
                    return;
                }
            }
        }
        // Never hold navigation hostage to a slow GL driver, missing row, or destroyed window.
        if (SystemClock.uptimeMillis() - created > 450 || !root.isAttachedToWindow()) {
            started = true;
            diamond.setAlpha(0f);
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
        WalletDiamondView target = landing.getPendingDiamond();
        if (target != null && landing.matches(transaction) && target.isAttachedToWindow()) {
            to.set(targetBounds(target));
        }
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
        if (sheet == null) {
            updateSourceBounds(source, outgoing, root, from);
        } else if (source.isAttachedToWindow() && source.getRootView() == root.getRootView()) {
            from.set(bounds(source, root));
            from.offset(0, sheet.currentPanTranslationY - sheet.getGramSendExitTranslationY());
        }
    }

    private RectF targetBounds(View target) {
        if (sheet == null) return bounds(target, root);
        ViewGroup targetRoot = (ViewGroup) target.getRootView();
        RectF rect = bounds(target, targetRoot);
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
    }

    static void updateSourceBounds(View source, View outgoing, ViewGroup root, RectF result) {
        if (!source.isAttachedToWindow() || source.getWidth() <= 0 || source.getHeight() <= 0) return;
        RectF rect = new RectF(0, 0, source.getWidth(), source.getHeight());
        for (View current = source; current != root;) {
            current.getMatrix().mapRect(rect);
            // Follow keyboard/layout motion, but don't pull the flight down with
            // the outgoing container's own exit animation.
            if (current == outgoing) rect.offset(0, -outgoing.getTranslationY());
            rect.offset(current.getLeft(), current.getTop());
            ViewParent parent = current.getParent();
            if (!(parent instanceof View)) return;
            current = (View) parent;
            rect.offset(-current.getScrollX(), -current.getScrollY());
        }
        // Keep the last valid origin if the source detaches or leaves this root.
        result.set(rect);
    }

    static RectF bounds(View view, ViewGroup root) {
        RectF rect = new RectF(0, 0, view.getWidth(), view.getHeight());
        for (View current = view; current != root;) {
            current.getMatrix().mapRect(rect);
            rect.offset(current.getLeft(), current.getTop());
            ViewParent parent = current.getParent();
            if (!(parent instanceof View)) break;
            current = (View) parent;
            rect.offset(-current.getScrollX(), -current.getScrollY());
        }
        return rect;
    }

    public void cancel() {
        if (finished) return;
        if (animation.isStarted()) animation.cancel();
        else finish();
    }

    private void finish() {
        if (finished) return;
        finished = true;
        overlay.removeCallbacks(awaitLanding);
        diamond.whenReady(null);
        if (landing != null && landing.matches(transaction)) {
            WalletDiamondView target = landing.getPendingDiamond();
            if (target != null) target.copyRotationFrom(diamond);
            landing.holdForFlight(false);
            if (!to.isEmpty()) {
                float controlY = Math.min(from.centerY(), to.centerY()) - dp(150);
                float arrivalSpeed = 2f * (to.centerY() - controlY) / .6f / org.telegram.messenger.AndroidUtilities.density;
                landing.playLanding(arrivalSpeed);
            }
        }
        diamond.setPaused(true);
        overlay.removeView(diamond);
        overlay.setClickable(false);
        source.setAlpha(sourceAlpha);
        if (sheet == null) outgoing.setTranslationY(0f);
        else sheet.setGramSendTransitionProgress(1f);
        // Keep the last trail particles alive after the sheet window disappears.
        ViewGroup particleRoot = root;
        if (sheet != null && particles.isActive() && wallet.getParentLayout() != null) {
            ViewGroup walletRoot = wallet.getParentLayout().getView();
            if (walletRoot.isAttachedToWindow()) {
                int[] sheetLocation = new int[2];
                int[] walletLocation = new int[2];
                root.getLocationOnScreen(sheetLocation);
                walletRoot.getLocationOnScreen(walletLocation);
                root.removeView(overlay);
                overlay.setTranslationX(sheetLocation[0] - walletLocation[0]);
                overlay.setTranslationY(sheetLocation[1] - walletLocation[1]);
                walletRoot.addView(overlay, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                particleRoot = walletRoot;
            }
        }
        completion.run();
        final ViewGroup cleanupRoot = particleRoot;
        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> cleanupRoot.removeView(overlay), 650);
    }
}
