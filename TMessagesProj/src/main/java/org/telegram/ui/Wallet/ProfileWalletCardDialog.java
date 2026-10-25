package org.telegram.ui.Wallet;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_wallet;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ProfileBalanceView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.ScrimOptions;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProviderThemed;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceBitmap;
import org.telegram.ui.Components.blur3.utils.Blur3Utils;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.GradientClip;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;
import org.telegram.ui.Stories.recorder.HintView2;

import java.math.BigDecimal;

/** A read-only view of the card published on a user's profile. */
public class ProfileWalletCardDialog extends Dialog {
    private final BaseFragment fragment;
    private final ProfileBalanceView anchor;
    private final TL_wallet.card profileCard;
    private final boolean showSendMoney;
    private final boolean showHowItWorks;
    private final boolean isSelf;
    private final RectF source = new RectF();
    private final FrameLayout root;
    private final View blurView;
    private final WalletCardComponent card;
    private final WalletGiftStickers stickers;
    private final ProfileActivity.Button2 sendPill;
    private final TextView publicNote;
    private final Paint blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF blurBounds = new RectF();
    private final BlurredBackgroundSourceBitmap buttonBlur = new BlurredBackgroundSourceBitmap();
    private final BlurredBackgroundDrawableViewFactory buttonBlurFactory;
    private Bitmap blurBitmap;
    private Insets insets = Insets.NONE;
    private float progress;
    private float clipTop;
    private boolean springy;
    private final GradientClip clip = new GradientClip();
    private final RectF clipRect = new RectF();
    private final RectF cardLayerBounds = new RectF();
    private ValueAnimator animator, gyroscopeAnimator;
    private float gyroscope;
    private boolean dismissing, closed, opening;
    private Runnable afterDismiss;
    private final int[] rootLocation = new int[2];

    public ProfileWalletCardDialog(BaseFragment fragment, ProfileBalanceView anchor,
                                  TLRPC.User user, TL_wallet.card profileCard) {
        super(fragment.getParentActivity(), R.style.TransparentDialog);
        this.fragment = fragment;
        this.anchor = anchor;
        this.profileCard = profileCard;
        isSelf = user != null && user.id == UserConfig.getInstance(fragment.getCurrentAccount()).getClientUserId();
        showSendMoney = user != null && !isSelf && MessagesController.getInstance(fragment.getCurrentAccount()).config.walletAvailable.get();
        showHowItWorks = user != null && !isSelf && !showSendMoney;
        anchor.getButtonBoundsOnScreen(source);
        AndroidUtilities.enableEdgeToEdge(getWindow());

        root = new FrameLayout(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                final int width = MeasureSpec.getSize(widthMeasureSpec);
                final int height = MeasureSpec.getSize(heightMeasureSpec);
                setMeasuredDimension(width, height);
                final int safeWidth = Math.max(1, width - insets.left - insets.right);
                sendPill.measure(MeasureSpec.makeMeasureSpec(Math.max(1, safeWidth - dp(24)), MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(dp(60), MeasureSpec.EXACTLY));
                publicNote.measure(MeasureSpec.makeMeasureSpec(Math.max(1, safeWidth - dp(48)), MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int cardWidth = Math.min(dp(420), Math.max(1, safeWidth - dp(24)));
                final int maxHeight = Math.max(1, height - safeTop() - insets.bottom - dp(showButton() ? 120 : 48) - noteHeight());
                card.measure(MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                if (card.getMeasuredHeight() > maxHeight) {
                    final int horizontalPadding = card.getPaddingLeft() + card.getPaddingRight();
                    final int verticalPadding = card.getPaddingTop() + card.getPaddingBottom();
                    cardWidth = horizontalPadding + Math.round((cardWidth - horizontalPadding)
                            * Math.max(0, maxHeight - verticalPadding)
                            / (float) Math.max(1, card.getMeasuredHeight() - verticalPadding));
                    card.measure(MeasureSpec.makeMeasureSpec(Math.max(1, cardWidth), MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                }
                stickers.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                blurView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                getLocationOnScreen(rootLocation);
                final int centerX = insets.left + (getWidth() - insets.left - insets.right) / 2;
                final int buttonTop = getHeight() - insets.bottom - dp(16) - (showButton() ? sendPill.getMeasuredHeight() : 0);
                sendPill.layout(centerX - sendPill.getMeasuredWidth() / 2, buttonTop,
                        centerX + (sendPill.getMeasuredWidth() + 1) / 2, buttonTop + sendPill.getMeasuredHeight());
                final int noteTop = safeTop() + dp(36);
                publicNote.layout(centerX - publicNote.getMeasuredWidth() / 2, noteTop,
                        centerX + (publicNote.getMeasuredWidth() + 1) / 2, noteTop + publicNote.getMeasuredHeight());
                updateNoteColor();
                final int minTop = safeTop() + dp(24) + noteHeight();
                final int maxTop = Math.max(minTop, buttonTop - dp(24) - card.getMeasuredHeight());
                final int cardTop = Math.max(minTop, Math.min(maxTop,
                        (getHeight() - card.getMeasuredHeight()) / 2));
                final int cardLeft = centerX - card.getMeasuredWidth() / 2;
                card.layout(cardLeft, cardTop, cardLeft + card.getMeasuredWidth(), cardTop + card.getMeasuredHeight());
                stickers.layout(0, 0, getWidth(), getHeight());
                blurView.layout(0, 0, getWidth(), getHeight());
                Blur3Utils.checkBitmapSourceMatrixScale(buttonBlur, this);
                updateProgress();
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == card && clipTop > 0) {
                    // The mask affects only the transformed card, not a full-screen render target.
                    cardLayerBounds.set(0, 0, card.getWidth(), card.getHeight());
                    card.getMatrix().mapRect(cardLayerBounds);
                    cardLayerBounds.offset(card.getLeft(), card.getTop());
                    cardLayerBounds.inset(-dp(4), -dp(4));
                    final float fade = dp(28);
                    if (cardLayerBounds.top < clipTop + fade
                            && cardLayerBounds.intersect(0, 0, getWidth(), getHeight())) {
                        int layer = canvas.saveLayer(cardLayerBounds, null);
                        final boolean result = super.drawChild(canvas, child, drawingTime);
                        clipRect.set(cardLayerBounds.left, cardLayerBounds.top,
                                cardLayerBounds.right, Math.min(cardLayerBounds.bottom, clipTop));
                        if (clipRect.height() > 0) canvas.drawRect(clipRect, clip.getPaint(GradientClip.TOP, 1f));
                        clipRect.set(cardLayerBounds.left, clipTop, cardLayerBounds.right, clipTop + fade);
                        clip.draw(canvas, clipRect, GradientClip.TOP, 1f);
                        canvas.restoreToCount(layer);
                        return result;
                    }
                }
                return super.drawChild(canvas, child, drawingTime);
            }

        };
        // Its own view: fading is a RenderNode alpha change, the bitmap draw is recorded once.
        blurView = new View(getContext()) {
            @Override
            protected void onDraw(Canvas canvas) {
                if (blurBitmap != null && !blurBitmap.isRecycled()) {
                    blurBounds.set(0, 0, getWidth(), getHeight());
                    canvas.drawBitmap(blurBitmap, null, blurBounds, blurPaint);
                }
            }

            @Override
            public boolean hasOverlappingRendering() {
                return false;
            }
        };
        blurView.setAlpha(0);
        root.addView(blurView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setOnClickListener(v -> dismiss());
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            root.requestLayout();
            return WindowInsetsCompat.CONSUMED;
        });

        card = new WalletCardComponent(getContext());
        card.setDiamondOnCard(true);
        card.setProfileCard(fragment.getCurrentAccount(), user, profileCard);
        card.setCardOnClickListener(v -> {
            if (isSelf && !dismissing) {
                WalletActivity.openWalletQRAlert(getContext(), fragment.getCurrentAccount(), fragment.getResourceProvider());
            }
        });
        card.setOnClickListener(v -> { });
        card.setAlpha(0);
        root.addView(card, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        stickers = new WalletGiftStickers(fragment, card, profileCard);
        root.addView(stickers, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        buttonBlurFactory = new BlurredBackgroundDrawableViewFactory(buttonBlur);
        buttonBlurFactory.setSourceRootView(new ViewPositionWatcher(root), root);
        sendPill = new ProfileActivity.Button2(getContext());
        BlurredBackgroundDrawable background = buttonBlurFactory.create(sendPill,
                new BlurredBackgroundColorProviderThemed(fragment.getResourceProvider(), Theme.key_windowBackgroundWhite));
        background.setPadding(dp(8));
        background.setRadius(dp(22));
        sendPill.setBackground(background);
        ScaleStateListAnimator.apply(sendPill, .02f, 1.2f);
        ButtonWithCounterView send = new ButtonWithCounterView(getContext(), fragment.getResourceProvider());
        send.setUseWrapContent(true);
        send.setPadding(dp(16), 0, dp(16), 0);
        send.setRoundRadius(19);
        final String buttonText = getString(isSelf ? R.string.WalletOpenMyMoney : showSendMoney ? R.string.WalletSendMoney : R.string.HowDoesItWork);
        if (!showSendMoney) {
            send.setText(buttonText, false);
        } else {
            SpannableStringBuilder label = new SpannableStringBuilder("G  ").append(buttonText);
            ColoredImageSpan icon = new ColoredImageSpan(R.drawable.mini_gram_24);
            icon.setSize(dp(20));
            icon.setOverrideColor(Color.WHITE);
            label.setSpan(icon, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            send.setText(label, false);
        }
        send.setStateListAnimator(null);
        sendPill.addView(send, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
        sendPill.setContentDescription(buttonText);
        sendPill.setOnClickListener(v -> {
            if (dismissing) return;
            if (isSelf) {
                afterDismiss = () -> fragment.presentFragment(new WalletActivity());
            } else if (showHowItWorks) {
                afterDismiss = () -> WalletActivity.openWalletHowItWorksAlert(fragment.getContext(), fragment.getResourceProvider());
            } else {
                if (!showSendMoney || TextUtils.isEmpty(profileCard.address)) return;
                afterDismiss = () -> fragment.presentFragment(new WalletSendEnterAmount(fragment.getCurrentAccount(), profileCard.address));
            }
            dismiss();
        });
        sendPill.setAlpha(0);
        sendPill.setVisibility(showButton() ? View.VISIBLE : View.GONE);
        root.addView(sendPill, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 60, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        publicNote = new TextView(getContext());
        publicNote.setTextSize(14);
        publicNote.setTypeface(AndroidUtilities.bold());
        publicNote.setGravity(Gravity.CENTER);
        publicNote.setLineSpacing(dp(18) - publicNote.getPaint().getFontMetricsInt(null), 1f);
        updateNoteColor();
        publicNote.setAlpha(0);
        if (isSelf) {
            publicNote.setText(getString(R.string.WalletPublicBalanceNote));
        } else if (user != null) {
            final String name = UserObject.getFirstName(user);
            final boolean empty = new BigDecimal(GramWallet.formatGramsBalance(profileCard.balance, false)).signum() == 0;
            final String text = empty ?
                    LocaleController.formatString(R.string.WalletProfileOwnsNoGrams, name) :
                    LocaleController.formatString(R.string.WalletProfileOwnsGrams, name,
                            GramWallet.formatGramsWithUnit(WalletCardComponent.formatBalance(profileCard.balance).toString()).toString().replace(' ', '\u00A0'));
            publicNote.setText(HintView2.cutInFancyHalfText(text, publicNote.getPaint()));
        }
        publicNote.setVisibility(user != null ? View.VISIBLE : View.GONE);
        root.addView(publicNote, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
    }

    /** Picks black or white note text by the brightness of the blur under it. */
    private void updateNoteColor() {
        boolean dark = Theme.isCurrentThemeDark();
        if (blurBitmap != null && !blurBitmap.isRecycled() && root.getWidth() > 0 && root.getHeight() > 0) {
            final int x = Utilities.clamp((int) ((publicNote.getLeft() + publicNote.getRight()) / 2f / root.getWidth() * blurBitmap.getWidth()), blurBitmap.getWidth() - 1, 0);
            final int y = Utilities.clamp((int) ((publicNote.getTop() + publicNote.getBottom()) / 2f / root.getHeight() * blurBitmap.getHeight()), blurBitmap.getHeight() - 1, 0);
            dark = AndroidUtilities.computePerceivedBrightness(blurBitmap.getPixel(x, y)) < .5f;
        }
        publicNote.setTextColor(Theme.multAlpha(dark ? Color.WHITE : Color.BLACK, .41f));
    }

    /** Extra top space taken by the public balance note above the card. */
    private int noteHeight() {
        return publicNote.getVisibility() == View.VISIBLE ? dp(12) + publicNote.getMeasuredHeight() : 0;
    }

    private boolean showButton() {
        return showSendMoney || showHowItWorks || isSelf;
    }

    private int safeTop() {
        return Math.max(insets.top, AndroidUtilities.statusBarHeight);
    }

    private void updateProgress() {
        // Phase 1: the card, standing vertically, slides down out of the balance button (clipped at the button).
        // Phase 2: it moves to its place, scales up and rotates back to horizontal.
        final float slide = CubicBezierInterpolator.EASE_OUT.getInterpolation(clamp01(progress / .4f));
        final float settleT = clamp01((progress - .3f) / .7f);
        final float settle = CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(settleT);
        // Opening overshoots a bit on move, scale and rotation; closing just eases back.
        final float bounce = springy ? spring(settleT) : settle;
        final float w = card.getWidth(), h = card.getHeight();
        final float startScale = Math.max(.2f, Math.min(1f, source.width() / Math.max(1, h)));
        final float scale = AndroidUtilities.lerp(startScale, 1f, bounce);
        final float clipY = source.centerY() - rootLocation[1];
        final float verticalHeight = w * startScale;
        final float startCenterX = source.centerX() - rootLocation[0];
        final float startCenterY = clipY - verticalHeight / 2f + verticalHeight * slide;
        final float endCenterX = card.getLeft() + w / 2f, endCenterY = card.getTop() + h / 2f;
        final float centerX = AndroidUtilities.lerp(startCenterX, endCenterX, bounce);
        final float centerY = AndroidUtilities.lerp(startCenterY, endCenterY, bounce);
        final float rotation = -90 * (1 - bounce);
        final float cos = (float) Math.cos(Math.toRadians(rotation)), sin = (float) Math.sin(Math.toRadians(rotation));
        // Pivot the tilt on the balance slot, so the part still near it stays small and the part already out
        // grows toward the viewer. The pivot drifts back to the card center as it settles.
        final float slotDx = startCenterX - centerX, slotDy = clipY - centerY;
        final float slotPivotX = w / 2f + (cos * slotDx + sin * slotDy) / scale;
        final float slotPivotY = h / 2f + (-sin * slotDx + cos * slotDy) / scale;
        final float pivotX = AndroidUtilities.lerp(slotPivotX, w / 2f, settle);
        final float pivotY = AndroidUtilities.lerp(slotPivotY, h / 2f, settle);
        final float fromCenterX = pivotX - w / 2f, fromCenterY = pivotY - h / 2f;
        card.setPivotX(pivotX);
        card.setPivotY(pivotY);
        card.setScaleX(scale);
        card.setScaleY(scale);
        card.setRotation(rotation);
        card.setRotationX(35 * (1 - bounce));
        card.setTranslationX(centerX - card.getLeft() - pivotX + scale * (cos * fromCenterX - sin * fromCenterY));
        card.setTranslationY(centerY - card.getTop() - pivotY + scale * (sin * fromCenterX + cos * fromCenterY));
        card.setAlpha(progress > 0 ? 1 : 0);
        // Live tilt only after the open finishes: a perspective face mid-flight costs extra GPU work.
        card.setUseGyroscope(gyroscope * settle);
        clipTop = Math.max(0, clipY * (1 - settle));
        anchor.setEjectProgress(slide);
        blurView.setAlpha(progress);
        stickers.setAlpha(settle);
        sendPill.setAlpha(settle);
        publicNote.setAlpha(settle);
        publicNote.setTranslationY(-dp(8) * (1 - settle));
        sendPill.setTranslationY(dp(16) * (1 - settle));
        sendPill.setScaleX(AndroidUtilities.lerp(.8f, 1f, settle));
        sendPill.setScaleY(AndroidUtilities.lerp(.8f, 1f, settle));
        root.invalidate();
    }

    /** Underdamped spring step response, settled by t = 1. */
    private static float spring(float t) {
        final float damping = .75f, omega = 10f;
        final float omegaD = omega * (float) Math.sqrt(1 - damping * damping);
        final float envelope = (float) Math.exp(-damping * omega * t);
        return 1 - envelope * ((float) Math.cos(omegaD * t) + damping * omega / omegaD * (float) Math.sin(omegaD * t));
    }

    private static float clamp01(float value) {
        return Math.max(0, Math.min(1, value));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setWindowAnimations(R.style.DialogNoAnimation);
        setContentView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.FILL;
        params.dimAmount = 0;
        params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR
                | WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        window.setAttributes(params);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        AndroidUtilities.setLightNavigationBar(root, !Theme.isCurrentThemeDark());
        AndroidUtilities.setLightStatusBar(window, !Theme.isCurrentThemeDark());
    }

    @Override
    public void show() {
        if (!AndroidUtilities.isSafeToShow(getContext())) return;
        // Capture before attaching this window, so the card never enters its own blur.
        ScrimOptions.makeGlobalBlurBitmaps((background, button) -> {
            if (closed || dismissing) {
                background.recycle();
                button.recycle();
                return;
            }
            blurBitmap = background;
            blurView.invalidate();
            updateNoteColor();
            buttonBlur.setBitmap(button);
            root.post(() -> {
                if (closed || dismissing) return;
                Blur3Utils.checkBitmapSourceMatrixScale(buttonBlur, root);
                buttonBlurFactory.invalidateAllLinkedViews();
                card.whenMaterialReady(() -> {
                    if (!closed && !dismissing) animateTo(1);
                });
            });
        });
        super.show();
        ViewCompat.requestApplyInsets(root);
    }

    private void animateTo(float target) {
        if (animator != null) animator.cancel();
        if (target == 1 && opening) return;
        if (target == 1) opening = true;
        springy = target == 1;
        if (card.isUse2D()) card.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(target == 1 ? 650 : 380);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            updateProgress();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (card.isUse2D()) card.setLayerType(View.LAYER_TYPE_NONE, null);
                if (target == 0) finishDismiss();
                else if (!dismissing) startGyroscope();
            }
        });
        animator.start();
    }

    private void startGyroscope() {
        if (gyroscopeAnimator != null) gyroscopeAnimator.cancel();
        gyroscopeAnimator = ValueAnimator.ofFloat(gyroscope, 1);
        gyroscopeAnimator.setDuration(320);
        gyroscopeAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        gyroscopeAnimator.addUpdateListener(a -> {
            gyroscope = (float) a.getAnimatedValue();
            card.setUseGyroscope(gyroscope);
        });
        gyroscopeAnimator.start();
    }

    @Override
    public void dismiss() {
        if (closed || dismissing) return;
        dismissing = true;
        // Freeze the ramp; the closing animation fades the current tilt out with settle.
        if (gyroscopeAnimator != null) gyroscopeAnimator.cancel();
        sendPill.setEnabled(false);
        if (!root.isAttachedToWindow() || progress == 0) finishDismiss();
        else animateTo(0);
    }

    private void finishDismiss() {
        if (closed) return;
        closed = true;
        card.setLayerType(View.LAYER_TYPE_NONE, null);
        anchor.setEjectProgress(0);
        super.dismiss();
        stickers.dispose();
        buttonBlur.setBitmap(null);
        // Drop references after the window is detached; RenderThread may still hold the last frame.
        blurBitmap = null;
        if (afterDismiss != null && fragment.getParentActivity() != null) {
            Runnable action = afterDismiss;
            afterDismiss = null;
            AndroidUtilities.runOnUIThread(action);
        }
    }
}
