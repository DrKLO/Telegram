package org.telegram.ui.Wallet;

import org.telegram.messenger.LocaleController;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.graphics.Canvas;
import android.app.ActivityManager;
import android.view.ViewGroup;
import android.animation.ValueAnimator;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LoadingDrawable;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.Premium.GLIcon.WalletDiamondTextureView;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Components.TextStyleSpan;
import org.telegram.ui.Components.spoilers.SpoilerEffect;
import org.telegram.ui.Gifts.GiftSheet;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Stack;

public class WalletActionLayout {

    private final View parentView;
    private final Theme.ResourcesProvider resourcesProvider;
    private final ArrayList<SpoilerEffect> commentSpoilers = new ArrayList<>();
    private final Stack<SpoilerEffect> commentSpoilersPool = new Stack<>();
    private boolean spoilersSuppressed;
    private final RectF cardRect = new RectF();
    private final RectF loadingRect = new RectF();
    private final Path loadingPath = new Path();
    private final Drawable cardDrawable;
    private final Path cardClip = new Path();
    private final WalletCardMotion cardMotion;
    private final RLottieDrawable gramDrawable;
    private WalletDiamondTextureView diamondSurface;
    private boolean surfaceEnabled, surfaceFailed;
    private float surfaceOriginX, surfaceOriginY;
    private float spinOffset, spinSpeed;
    private float sentSpinMomentum;
    private static final float SENT_SPIN_DECAY = .75f;
    private final WalletSendParticles sentParticles = new WalletSendParticles();
    private long spinUpdated;
    private static final float IDLE_SPEED = (float) (2 * Math.PI / 18 * 1.70775);
    private final Rect diamondBounds = new Rect();
    private int diamondPointer = MotionEvent.INVALID_POINTER_ID;
    private float diamondX, diamondY, diamondYaw, diamondPitch, diamondScale = 1f;
    private boolean diamondReturning;
    private boolean diamondHeldForFlight;
    private SpringAnimation diamondSpring;
    private ValueAnimator diamondReturn;
    private boolean gramAnimationStarted;
    private static final int RIBBON_SENT_COLOR = 0xFF15BE00;

    private final GiftSheet.RibbonDrawable ribbon;
    private final AnimatedFloat ribbonColorAnimation;
    private final AnimatedFloat ribbonRevealAnimation;
    private final Paint ribbonHighlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean ribbonHighlightPending;
    private long ribbonHighlightStarted;
    private static final long RIBBON_HIGHLIGHT_DELAY = 500;
    private static final long RIBBON_HIGHLIGHT_DURATION = 600;
    private final Paint sendingClockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long sendingClockStarted;
    private final AnimatedFloat sendingClockReveal;
    private float sendingGradientRotation;
    private long sendingGradientUpdated;
    private int ribbonColorFrom;
    private int ribbonColorTo;
    private int ribbonColorCurrent;
    private final Path ribbonInnerStrokePath = new Path();
    private final Paint ribbonInnerStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ButtonBounce bounce;
    private SpringAnimation sentSpring;
    private float sentScale = 1f;
    private SpringAnimation landingSpring;
    private float landingPress;
    private LoadingDrawable loadingDrawable;
    private long highlightShimmerStarted;
    private boolean highlightShimmerDisappearing;

    private boolean out;
    private boolean wasSending;
    private boolean waitingForReceive;
    private ValueAnimator receiveAnimator;
    private Runnable onReceiveAnimationEnd;
    private TLRPC.TL_messageActionGramTransfer action;
    private boolean preparingCommentRevealed;
    private Text amountText;
    private Text addressText;
    private Text peerLinkText;
    private Text commentText;
    private boolean touchCaptured;
    private Runnable onClick;

    public WalletActionLayout(View parentView) {
        this(parentView, null);
    }

    public WalletActionLayout(View parentView, Theme.ResourcesProvider resourcesProvider) {
        this.parentView = parentView;
        this.resourcesProvider = resourcesProvider;
        cardDrawable = parentView.getResources().getDrawable(R.drawable.wallet_action_card_gradient).mutate();
        cardMotion = WalletCardMotion.get(parentView.getContext());
        gramDrawable = new RLottieDrawable(R.raw.wallet_diamond_white, dp(42), dp(42), false, null);
        gramDrawable.setMasterParent(parentView);
        gramDrawable.setAutoRepeat(0);
        parentView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                if (diamondSurface != null) diamondSurface.setPaused(!surfaceEnabled);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                finishReceiveAnimation();
                resetRibbonHighlight();
                resetDiamond();
                resetSentBounce();
                resetLanding();
                cardMotion.detach(parentView);
                if (diamondSurface != null) diamondSurface.setPaused(true);
                gramDrawable.stop();
                gramAnimationStarted = false;
            }
        });

        sendingClockPaint.setColor(0xFF66E6FF);
        sendingClockPaint.setStyle(Paint.Style.STROKE);
        sendingClockPaint.setStrokeWidth(dpf2(1));
        sendingClockReveal = new AnimatedFloat(1f, parentView::invalidate, 0, 180, CubicBezierInterpolator.EASE_OUT_QUINT);
        ribbonRevealAnimation = new AnimatedFloat(parentView::invalidate, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        ribbonColorAnimation = new AnimatedFloat(parentView::invalidate, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
        ribbon = new GiftSheet.RibbonDrawable(parentView, 1.0f) {
            @Override
            public void invalidateSelf() {
                super.invalidateSelf();
                parentView.invalidate();
            }
        };
        ribbon.setTextColor(0xFFFFFFFF);
        GiftSheet.RibbonDrawable.fillRibbonPath(ribbonInnerStrokePath, 1.0f, false);
        ribbonInnerStrokePaint.setStyle(Paint.Style.STROKE);
        ribbonInnerStrokePaint.setStrokeWidth(dpf2(4));
        ribbonInnerStrokePaint.setPathEffect(new CornerPathEffect(dp(2.33f)));
        ribbonInnerStrokePaint.setShader(new LinearGradient(
            0, 0, dp(48), dp(48),
            new int[]{ 0x00FFFFFF, Theme.multAlpha(0xFFFFFF, .35f), 0x00FFFFFF },
            new float[]{ 0f, .5f, 1f },
            Shader.TileMode.CLAMP));
        ribbonHighlightPaint.setShader(new LinearGradient(
            -dp(10), 0, dp(10), 0,
            new int[]{ 0x00FFFFFF, 0x80FFFFFF, 0x00FFFFFF },
            new float[]{ 0f, .5f, 1f }, Shader.TileMode.CLAMP));
        bounce = new ButtonBounce(parentView);
    }

    public void set(boolean out, TLRPC.TL_messageActionGramTransfer action) {
        set(out, action, null);
    }

    public void set(boolean out, TLRPC.TL_messageActionGramTransfer action, String peerLink) {
        set(out, action, peerLink, MessageObject.MESSAGE_SEND_STATE_SENT);
    }

    public void set(boolean out, TLRPC.TL_messageActionGramTransfer action, String peerLink, int sendState) {
        set(out, action, peerLink, sendState, this.action != action);
    }

    public void set(boolean out, TLRPC.TL_messageActionGramTransfer action, String peerLink, int sendState, boolean messageChanged) {
        set(out, action, peerLink, sendState, messageChanged, false);
    }

    public void set(boolean out, TLRPC.TL_messageActionGramTransfer action, String peerLink, int sendState, boolean messageChanged, boolean waitingForReceive) {
        if (messageChanged) {
            finishReceiveAnimation();
            resetRibbonHighlight();
            wasSending = false;
            resetDiamond();
            resetSentBounce();
            resetLanding();
            sendingGradientRotation = 0;
            sendingGradientUpdated = 0;
            gramDrawable.stop();
            gramAnimationStarted = false;
        }
        this.out = out;
        this.action = action;
        this.waitingForReceive = !out && waitingForReceive;
        if (this.waitingForReceive) {
            forceRibbonReveal(0);
        } else if (messageChanged) {
            forceRibbonReveal(1);
        }
        preparingCommentRevealed = false;
        final boolean failed = out && sendState == MessageObject.MESSAGE_SEND_STATE_SEND_ERROR;
        final boolean sending = out && sendState == MessageObject.MESSAGE_SEND_STATE_SENDING;
        if (wasSending && out && sendState == MessageObject.MESSAGE_SEND_STATE_SENT) {
            animateSentBounce();
            // Advance the previous pose before adding momentum, avoiding an angle jump.
            getDiamondRestingYaw();
            sentSpinMomentum = 18f;
            sentParticles.sent(0f, 0f);
            parentView.invalidate();
        }
        if (sending) {
            if (!wasSending) {
                sendingClockStarted = SystemClock.elapsedRealtime();
            }
            forceRibbonReveal(0);
        } else if (failed) {
            forceRibbonReveal(1);
        }
        updateSendingGradient();
        wasSending = sending;
        sendingGradientUpdated = sending ? SystemClock.elapsedRealtime() : 0;
        if (sending) {
            ensureLoadingDrawable();
            loadingDrawable.setSingleCycleDuration(0);
            if (loadingDrawable.isDisappearing() || loadingDrawable.isDisappeared()) {
                loadingDrawable.reset();
                loadingDrawable.resetDisappear();
            }
            parentView.invalidate();
        } else if (loadingDrawable != null && highlightShimmerStarted == 0 && !loadingDrawable.isDisappearing() && !loadingDrawable.isDisappeared()) {
            loadingDrawable.disappear();
            parentView.invalidate();
        }
        final int newRibbonColor = failed ? Theme.getColor(Theme.key_text_RedBold, resourcesProvider)
            : out ? RIBBON_SENT_COLOR : 0xFF4BB9FE;
        if (ribbonColorTo == 0) {
            ribbonColorFrom = ribbonColorTo = ribbonColorCurrent = newRibbonColor;
        } else if (newRibbonColor != ribbonColorTo) {
            ribbonColorFrom = ribbonColorCurrent;
            ribbonColorTo = newRibbonColor;
            ribbonColorAnimation.force(0);
            parentView.invalidate();
        }
        // The sending state has no ribbon text to transition from.
        final String ribbonText = failed ? LocaleController.getString(R.string.WalletTransferStatusFailed)
            : out ? LocaleController.getString(R.string.WalletTransferStatusSent)
            : LocaleController.getString(R.string.WalletTransferStatusReceived);
        if (sending) {
            ribbon.setText(10, ribbonText, true);
        } else {
            ribbon.setTextAnimated(10, ribbonText, true);
        }

        final SpannableStringBuilder amount = GramWallet.formatGramsScaled(
            Math.abs(action.amount),
            12.0f / 16.0f
        );
        final RelativeSizeSpan[] sizeSpans = amount.getSpans(0, amount.length(), RelativeSizeSpan.class);
        for (RelativeSizeSpan span : sizeSpans) {
            amount.removeSpan(span);
        }
        amount.insert(0, out ? "–" : "+");
        final int point = amount.toString().indexOf('.');
        if (point >= 0) {
            amount.setSpan(
                new RelativeSizeSpan(12.0f / 16.0f),
                point,
                amount.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        final Object numberSpan = new Object();
        amount.setSpan(numberSpan, 0, amount.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        final SpannableStringBuilder localizedAmount = new SpannableStringBuilder(GramWallet.formatGramsWithUnit(amount));
        final int numberStart = localizedAmount.getSpanStart(numberSpan);
        final int numberEnd = localizedAmount.getSpanEnd(numberSpan);
        localizedAmount.removeSpan(numberSpan);
        if (numberStart > 0) {
            localizedAmount.setSpan(new ForegroundColorSpan(0xFF5DE3FF), 0, numberStart, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (numberEnd >= 0 && numberEnd < localizedAmount.length()) {
            localizedAmount.setSpan(new ForegroundColorSpan(0xFF5DE3FF), numberEnd, localizedAmount.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        amountText = new Text(localizedAmount, 16, AndroidUtilities.bold());

        final String formattedAddress = WalletCardComponent.formatCardAddress(
            WalletCardComponent.formatAddress(action.peer_address.toUpperCase(Locale.US))
        );
        addressText = new Text(
            "",
            9.3f,
            AndroidUtilities.getTypeface("fonts/rmono.ttf")
        ).multiline(2);
        addressText.setText(formattedAddress);

        if (TextUtils.isEmpty(peerLink)) {
            peerLinkText = null;
        } else {
            peerLinkText = new Text(
                "",
                10,
                Typeface.create(
                    AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MONO),
                    Typeface.BOLD
                )
            );
            peerLinkText.setText(TextUtils.ellipsize(
                AndroidUtilities.replaceNewLines(peerLink.toUpperCase(Locale.US)),
                peerLinkText.paint,
                getWidth() - dp(24),
                TextUtils.TruncateAt.END
            ));
        }

        commentSpoilersPool.addAll(commentSpoilers);
        commentSpoilers.clear();
        if (TextUtils.isEmpty(action.comment)) {
            commentText = null;
        } else {
            commentText = new Text("", 12)
                .multiline(Integer.MAX_VALUE)
                .align(Layout.Alignment.ALIGN_CENTER)
                .setMaxWidth(dp(192 - 8));
            if ((action.comment_encrypted || action.comment_encrypted_preparing) && !preparingCommentRevealed) {
                final SpannableStringBuilder placeholder = new SpannableStringBuilder("a".repeat(25));
                final TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
                run.flags |= TextStyleSpan.FLAG_STYLE_SPOILER;
                placeholder.setSpan(new TextStyleSpan(run), 0, placeholder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                commentText.setText(placeholder);
                SpoilerEffect.addSpoilers(parentView, commentText.getLayout(), commentSpoilersPool, commentSpoilers);
                setSpoilersSuppressed(spoilersSuppressed);
            } else {
                commentText.setText(Emoji.replaceEmoji(action.comment, commentText.paint.getFontMetricsInt(), false));
            }
        }
    }

    public int getWidth() {
        return dp(206);
    }

    public int getHeight() {
        if (commentText == null) {
            return dp(140);
        }
        return dp(150) + (int) Math.ceil(commentText.getHeight());
    }

    public void draw(Canvas canvas) {
        canvas.save();
        final float scale = bounce.getScale(0.025f) * sentScale;
        final float scaleX = scale * (1f + .012f * landingPress);
        final float scaleY = scale * (1f - .04f * landingPress);
        final float landingOffset = dp(4) * landingPress;
        canvas.translate(0, landingOffset);
        canvas.scale(scaleX, scaleY, getWidth() / 2.0f, dp(70));

        final float padding = dp(4);
        cardRect.set(padding, padding, getWidth() - padding, dp(140) - padding);

        if (!canvas.quickReject(cardRect, Canvas.EdgeType.AA)) cardMotion.drawn(parentView);
        cardClip.rewind();
        cardClip.addRoundRect(cardRect, dp(16), dp(16), Path.Direction.CW);
        canvas.save();
        canvas.clipPath(cardClip);
        canvas.translate(cardRect.centerX() + dp(10) * cardMotion.x, cardRect.centerY() + dp(8) * cardMotion.y);
        updateSendingGradient();
        canvas.rotate(cardMotion.x * 14f + cardMotion.y * 6f + sendingGradientRotation);
        // Keep the rectangular asset's aspect ratio, with its shorter side covering
        // the card's diagonal plus parallax at every angle of a full rotation.
        final float gradientHalf = (float) Math.hypot(cardRect.width(), cardRect.height()) / 2f + dp(16);
        final int gradientWidth = Math.max(1, cardDrawable.getIntrinsicWidth());
        final int gradientHeight = Math.max(1, cardDrawable.getIntrinsicHeight());
        final float gradientScale = gradientHalf / Math.min(gradientWidth, gradientHeight);
        final int gradientHalfWidth = (int) Math.ceil(gradientWidth * gradientScale);
        final int gradientHalfHeight = (int) Math.ceil(gradientHeight * gradientScale);
        cardDrawable.setBounds(-gradientHalfWidth, -gradientHalfHeight, gradientHalfWidth, gradientHalfHeight);
        cardDrawable.draw(canvas);
        canvas.restore();

        final int iconSize = dp(42);
        final int iconLeft = Math.round(cardRect.centerX() - iconSize / 2f);
        final float enlargement = Math.max(0f, Math.min(1f, (diamondScale - 1f) / 2f));
        final float restingTop = cardRect.top + dp(8);
        // Preserve the original icon/amount order at rest; center only the
        // enlarged icon. Device tilt never changes the icon's position.
        final int iconTop = Math.round(restingTop
                + (cardRect.centerY() - iconSize / 2f - restingTop) * enlargement);
        gramDrawable.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);
        final boolean iconVisible = !canvas.quickReject(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize, Canvas.EdgeType.AA);
        diamondBounds.set(gramDrawable.getBounds());
        if (sentParticles.isActive()) {
            canvas.save();
            canvas.clipPath(cardClip);
            canvas.translate(diamondBounds.exactCenterX(), diamondBounds.exactCenterY());
            sentParticles.draw(canvas);
            canvas.restore();
            parentView.postInvalidateOnAnimation();
        }
        updateDiamondPose();
        if (diamondSurface != null) {
            final float centerX = getWidth() / 2f + (diamondBounds.exactCenterX() - getWidth() / 2f) * scaleX;
            final float centerY = landingOffset + dp(70) + (diamondBounds.exactCenterY() - dp(70)) * scaleY;
            diamondSurface.setTranslationX(surfaceOriginX + centerX - iconSize / 2f);
            diamondSurface.setTranslationY(surfaceOriginY + centerY - iconSize / 2f);
            diamondSurface.setScaleX(scaleX * diamondScale);
            diamondSurface.setScaleY(scaleY * diamondScale);
        }
        final boolean nativeDiamond = iconVisible && diamondSurface != null && diamondSurface.isReady();
        if (nativeDiamond) {
            gramDrawable.stop();
            gramAnimationStarted = false;
        } else if (!gramAnimationStarted && iconVisible) {
            gramAnimationStarted = true;
            gramDrawable.setProgress(0);
            gramDrawable.start();
        }
        if (!nativeDiamond && !diamondHeldForFlight) {
            canvas.save();
            if (!out && (receiveAnimator != null || spinOffset != 0f)) {
                // Keep the momentum visible on devices using the Lottie fallback.
                if (diamondSurface == null) getDiamondRestingYaw();
                canvas.rotate((float) Math.toDegrees(spinOffset), diamondBounds.exactCenterX(), diamondBounds.exactCenterY());
            }
            gramDrawable.draw(canvas);
            canvas.restore();
        }

        if (amountText != null) {
            final float amountWidth = amountText.getWidth();
            final float amountMaxWidth = cardRect.width() - dp(16);
            final float amountScale = amountWidth > amountMaxWidth ? amountMaxWidth / amountWidth : 1.0f;
            final float amountCenterY = cardRect.top + dp(55) + amountText.getHeight() / 2.0f;
            canvas.save();
            canvas.scale(amountScale, amountScale, cardRect.centerX(), amountCenterY);
            amountText.draw(
                canvas,
                cardRect.centerX() - amountWidth / 2.0f,
                amountCenterY,
                0xFFFFFFFF,
                1.0f
            );
            canvas.restore();
        }

        if (addressText != null) {
            final float addressX = (getWidth() - addressText.getWidth()) / 2.0f;
            final float addressTop = cardRect.bottom - dp(7) - addressText.getHeight();
            if (peerLinkText != null) {
                peerLinkText.draw(
                    canvas,
                    (getWidth() - peerLinkText.getWidth()) / 2.0f,
                    addressTop - dp(4) - peerLinkText.getHeight() / 2.0f,
                    0xFF5DE3FF,
                    1.0f
                );
            }
            addressText.draw(canvas, addressX, addressTop + dp(1), 0x0FFFFFFF, 1.0f);
            addressText.draw(canvas, addressX, addressTop, 0xFF0145D0, 1.0f);
        }

        if (loadingDrawable != null && !loadingDrawable.isDisappeared()) {
            if (highlightShimmerStarted != 0 && !wasSending && !highlightShimmerDisappearing
                    && SystemClock.elapsedRealtime() - highlightShimmerStarted >= 600) {
                loadingDrawable.disappear();
                highlightShimmerDisappearing = true;
            }
            final float inset = loadingDrawable.strokePaint.getStrokeWidth() / 2.0f;
            loadingRect.set(cardRect);
            loadingRect.inset(inset, inset);
            final float radius = Math.max(0, dp(16) - inset);
            loadingPath.rewind();
            loadingPath.addRoundRect(loadingRect, radius, radius, Path.Direction.CW);
            loadingDrawable.setBounds(cardRect);
            loadingDrawable.draw(canvas);
            if (highlightShimmerStarted != 0 && loadingDrawable.isDisappeared()) {
                highlightShimmerStarted = 0;
            }
        }

        final boolean receiveRibbonDelayed = receiveAnimator != null && receiveAnimator.getCurrentPlayTime() < 1000;
        final float ribbonRevealProgress = ribbonRevealAnimation.set(wasSending || waitingForReceive || receiveRibbonDelayed ? 0 : 1);
        if (!wasSending && !waitingForReceive && !receiveRibbonDelayed) {
            drawRibbon(canvas, ribbonRevealProgress);
        }
        final float clockReveal = sendingClockReveal.set(diamondHeldForFlight ? 0f : 1f);
        if (out && !diamondHeldForFlight && ribbonRevealProgress < 1f && clockReveal > 0f) {
            drawSendingClock(canvas, (1f - ribbonRevealProgress) * clockReveal);
        }
        canvas.restore();

        if (commentText != null) {
            if ((action.comment_encrypted || action.comment_encrypted_preparing) && !preparingCommentRevealed) {
                canvas.save();
                canvas.translate((getWidth() - commentText.getWidth()) / 2.0f, dp(143));
                for (SpoilerEffect spoiler : commentSpoilers) {
                    spoiler.setColor(0xFFFFFFFF);
                    spoiler.draw(canvas);
                }
                canvas.restore();
            } else {
                commentText.draw(
                    canvas,
                    (getWidth() - commentText.getWidth()) / 2.0f,
                    dp(143),
                    0xFFFFFFFF,
                    1.0f
                );
            }
        }
    }

    public boolean playReceiveAnimation(Runnable onEnd) {
        if (!waitingForReceive || receiveAnimator != null) return false;
        waitingForReceive = false;
        onReceiveAnimationEnd = onEnd;
        forceRibbonReveal(0);
        animateSentBounce();
        getDiamondRestingYaw();
        sentSpinMomentum = 18f;
        sentParticles.sent(0f, 0f);
        gramDrawable.setProgress(0);
        gramDrawable.start();
        gramAnimationStarted = true;
        final float initialRotation = sendingGradientRotation;
        receiveAnimator = ValueAnimator.ofFloat(0f, 1f);
        receiveAnimator.setDuration(2400);
        receiveAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        receiveAnimator.addUpdateListener(animation -> {
            sendingGradientRotation = (initialRotation + 240f * (float) animation.getAnimatedValue()) % 360f;
            parentView.invalidate();
        });
        receiveAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                finishReceiveAnimation();
            }
        });
        receiveAnimator.start();
        parentView.invalidate();
        return true;
    }

    private void finishReceiveAnimation() {
        if (receiveAnimator == null) return;
        ValueAnimator animator = receiveAnimator;
        receiveAnimator = null;
        animator.removeAllListeners();
        animator.cancel();
        if (ribbonRevealAnimation.isInProgress()) resetRibbonHighlight();
        ribbonRevealAnimation.force(1);
        sentSpinMomentum = 0f;
        sentParticles.clear();
        Runnable onEnd = onReceiveAnimationEnd;
        onReceiveAnimationEnd = null;
        parentView.invalidate();
        if (onEnd != null) AndroidUtilities.runOnUIThread(onEnd, 1);
    }

    void playLanding(float velocityDpPerSecond) {
        resetLanding();
        // Match the impact impulse and squash of the wallet transaction row.
        landingSpring = new SpringAnimation(new FloatValueHolder(0));
        landingSpring.setSpring(new SpringForce(0).setDampingRatio(.65f).setStiffness(200f));
        landingSpring.setMinimumVisibleChange(.001f);
        landingSpring.setStartVelocity(Math.max(20f, Math.min(36f, velocityDpPerSecond * .04f)));
        landingSpring.addUpdateListener((animation, value, velocity) -> {
            landingPress = value;
            parentView.invalidate();
        });
        landingSpring.start();
    }

    private void resetLanding() {
        if (landingSpring != null) { landingSpring.cancel(); landingSpring = null; }
        landingPress = 0f;
        parentView.invalidate();
    }

    private void animateSentBounce() {
        if (parentView.isShown()) AndroidUtilities.vibrateCursor(parentView);
        if (sentSpring != null) sentSpring.cancel();
        sentSpring = new SpringAnimation(new FloatValueHolder(sentScale));
        sentSpring.setSpring(new SpringForce(1f).setDampingRatio(.55f).setStiffness(280f));
        sentSpring.setMinimumVisibleChange(.001f);
        // Compress from the current scale instead of jumping straight to 0.9.
        sentSpring.setStartVelocity(-3f);
        sentSpring.addUpdateListener((animation, value, velocity) -> {
            sentScale = value;
            parentView.invalidate();
        });
        sentSpring.start();
    }

    private void resetSentBounce() {
        if (sentSpring != null) { sentSpring.cancel(); sentSpring = null; }
        sentScale = 1f;
    }

    public void animateHighlight() {
        if (wasSending || (highlightShimmerStarted != 0 && loadingDrawable != null && !loadingDrawable.isDisappeared())) {
            return;
        }
        ensureLoadingDrawable();
        loadingDrawable.setSingleCycleDuration(600);
        loadingDrawable.reset();
        loadingDrawable.resetDisappear();
        highlightShimmerStarted = SystemClock.elapsedRealtime();
        highlightShimmerDisappearing = false;
        parentView.invalidate();
    }

    private void ensureLoadingDrawable() {
        if (loadingDrawable != null) return;
        loadingDrawable = new LoadingDrawable(resourcesProvider) {
            private final Paint numberGradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            {
                numberGradientPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            }
            @Override
            protected void drawContent(Canvas canvas, Path path) {
                super.drawContent(canvas, path);
                if (addressText != null) {
                    canvas.saveLayer(cardRect, null);
                    addressText.draw(canvas,
                        (getWidth() - addressText.getWidth()) / 2.0f,
                        cardRect.bottom - dp(7) - addressText.getHeight(),
                        0xFFFFFFFF, 1.0f);
                    numberGradientPaint.setShader(strokePaint.getShader());
                    canvas.drawRect(cardRect, numberGradientPaint);
                    canvas.restore();
                }
            }
            @Override
            public void invalidateSelf() {
                super.invalidateSelf();
                parentView.invalidate();
            }
        };
        loadingDrawable.setColors(0x004BD5F9, Theme.multAlpha(0xFF4BD5F9, .125f), 0x004BD5F9, Theme.multAlpha(0xFF4BD5F9, .75f));
        loadingDrawable.strokePaint.setStrokeWidth(dpf2(2));
        loadingDrawable.setGradientScale(2f);
        loadingDrawable.usePath(loadingPath);
        loadingDrawable.setAppearByGradient(true);
    }

    private float getDiamondRestingYaw() {
        final long now = SystemClock.uptimeMillis();
        if (spinUpdated != 0) {
            float dt = Math.min(.1f, (now - spinUpdated) / 1000f);
            float target = wasSending ? IDLE_SPEED * 3 : 0;
            float decay = (float) Math.exp(-dt / .2f);
            spinOffset = (spinOffset + target * dt + (spinSpeed - target) * .2f * (1 - decay)) % (float) (Math.PI * 2);
            spinSpeed = target + (spinSpeed - target) * decay;
            float momentumDecay = (float) Math.exp(-dt / SENT_SPIN_DECAY);
            spinOffset = (spinOffset + sentSpinMomentum * SENT_SPIN_DECAY * (1f - momentumDecay)) % (float) (Math.PI * 2);
            sentSpinMomentum *= momentumDecay;
            if (sentSpinMomentum < .001f) sentSpinMomentum = 0f;
        }
        spinUpdated = now;
        return (float) ((now / 1000.0 * IDLE_SPEED) % (Math.PI * 2)) + spinOffset + cardMotion.x * .14f;
    }

    private float getDiamondRestingPitch() {
        return cardMotion.y * .14f;
    }

    private void updateDiamondPose() {
        if (diamondSurface == null) return;
        if (!isDiamondDragging() && !diamondReturning) {
            diamondYaw = getDiamondRestingYaw();
            diamondPitch = getDiamondRestingPitch();
        }
        boolean interacting = isDiamondDragging() || diamondReturning || diamondScale > 1.001f;
        diamondSurface.setPose(diamondYaw, diamondPitch,
                interacting || wasSending || spinOffset != 0 || spinSpeed != 0 || sentSpinMomentum != 0, interacting);
    }

    public void setSurfaceOrigin(float x, float y) {
        surfaceOriginX = x;
        surfaceOriginY = y;
    }

    void holdDiamondForFlight(boolean hold) {
        if (hold) {
            sendingClockReveal.force(0f);
        } else if (diamondHeldForFlight && wasSending) {
            sendingClockStarted = SystemClock.elapsedRealtime();
        }
        diamondHeldForFlight = hold;
        if (diamondSurface != null) diamondSurface.setAlpha(hold ? 0f : 1f);
        parentView.invalidate();
    }

    float getFlightYaw() {
        return getDiamondRestingYaw();
    }

    float getFlightPitch() {
        return getDiamondRestingPitch();
    }

    public View getDiamondSurface() { return diamondSurface; }

    public void setSurfaceEnabled(boolean enabled) {
        surfaceEnabled = enabled;
        if (enabled && diamondSurface == null && !surfaceFailed && parentView instanceof ViewGroup
                && SharedConfig.getDevicePerformanceClass() != SharedConfig.PERFORMANCE_CLASS_LOW) {
            ActivityManager manager = (ActivityManager) parentView.getContext().getSystemService(android.content.Context.ACTIVITY_SERVICE);
            if (manager != null && manager.getDeviceConfigurationInfo().reqGlEsVersion >= 0x30000) {
                diamondSurface = new WalletDiamondTextureView(parentView.getContext(), this::updateDiamondPose) {
                    @Override
                    protected void onRenderError() {
                        surfaceFailed = true;
                        setPaused(true);
                        setVisibility(View.GONE);
                        parentView.invalidate();
                    }
                };
                ((ViewGroup) parentView).addView(diamondSurface, new ViewGroup.LayoutParams(dp(42), dp(42)));
            }
        }
        if (diamondSurface != null) {
            diamondSurface.setVisibility(enabled && !surfaceFailed ? View.VISIBLE : View.GONE);
            diamondSurface.setPaused(!enabled || surfaceFailed);
        }
        if (!enabled) {
            finishReceiveAnimation();
            resetDiamond();
        }
    }

    private void updateSendingGradient() {
        if (!wasSending || sendingGradientUpdated == 0) return;
        final long now = SystemClock.elapsedRealtime();
        // One full turn every four seconds; retain the final angle once sending ends.
        sendingGradientRotation = (sendingGradientRotation + (now - sendingGradientUpdated) * 360f / 4000f) % 360f;
        sendingGradientUpdated = now;
    }

    private void drawSendingClock(Canvas canvas, float alpha) {
        sendingClockPaint.setAlpha(Math.round(255 * alpha));
        final float centerX = cardRect.right - dpf2(10 + 5.5f);
        final float centerY = cardRect.top + dpf2(10 + 5.5f);
        // Account for the stroke so the outside diameter is exactly 11dp.
        canvas.drawCircle(centerX, centerY, dpf2(5), sendingClockPaint);
        final long elapsed = SystemClock.elapsedRealtime() - sendingClockStarted;
        final float minuteAngle = (elapsed % 2000L) / 2000f * 360f;
        final float hourAngle = (elapsed % 24000L) / 24000f * 360f;
        canvas.save();
        canvas.rotate(minuteAngle, centerX, centerY);
        canvas.drawLine(centerX, centerY, centerX, centerY - dpf2(4), sendingClockPaint);
        canvas.rotate(hourAngle - minuteAngle, centerX, centerY);
        canvas.drawLine(centerX, centerY, centerX + dpf2(2.5f), centerY, sendingClockPaint);
        canvas.restore();
        parentView.postInvalidateOnAnimation();
    }

    private void resetRibbonHighlight() {
        ribbonHighlightPending = false;
        ribbonHighlightStarted = 0;
    }

    private void forceRibbonReveal(float progress) {
        ribbonRevealAnimation.force(progress);
        resetRibbonHighlight();
    }

    private void drawRibbonHighlight(Canvas canvas, float revealProgress) {
        if (revealProgress < 1f || ribbonRevealAnimation.isInProgress()) {
            ribbonHighlightPending = true;
            ribbonHighlightStarted = 0;
            return;
        }
        final long now = SystemClock.elapsedRealtime();
        if (ribbonHighlightPending) {
            ribbonHighlightPending = false;
            ribbonHighlightStarted = now + RIBBON_HIGHLIGHT_DELAY;
            parentView.postInvalidateDelayed(RIBBON_HIGHLIGHT_DELAY);
        }
        if (ribbonHighlightStarted == 0 || now < ribbonHighlightStarted) return;
        final float progress = (now - ribbonHighlightStarted) / (float) RIBBON_HIGHLIGHT_DURATION;
        if (progress >= 1f) {
            ribbonHighlightStarted = 0;
            return;
        }
        final Rect bounds = ribbon.getBounds();
        canvas.save();
        canvas.translate(bounds.right - dp(48), bounds.top);
        canvas.clipPath(ribbonInnerStrokePath);
        // Sweep along the same diagonal used by the center-out reveal.
        canvas.rotate(45);
        final float halfWidth = dp(10);
        final float length = dp(48) * (float) Math.sqrt(2);
        canvas.translate(-halfWidth + (length + 2 * halfWidth) * progress, 0);
        canvas.drawRect(-halfWidth, -dp(48), halfWidth, dp(48), ribbonHighlightPaint);
        canvas.restore();
        parentView.postInvalidateOnAnimation();
    }

    private void drawRibbon(Canvas canvas, float revealProgress) {
        final float colorProgress = ribbonColorAnimation.set(1);
        ribbonColorCurrent = colorProgress >= 1f
            ? ribbonColorTo
            : ColorUtils.blendARGB(ribbonColorFrom, ribbonColorTo, colorProgress);
        ribbon.setColor(ribbonColorCurrent);
        ribbon.setBounds(
            (int) cardRect.right - dp(48 - 2),
            (int) cardRect.top - dp(2),
            (int) cardRect.right + dp(2),
            (int) cardRect.top + dp(48 - 2)
        );
        canvas.save();
        if (revealProgress < 1f) {
            final float centerX = ribbon.getBounds().exactCenterX();
            final float centerY = ribbon.getBounds().exactCenterY();
            // Clip along the ribbon's diagonal, expanding equally toward both ends.
            final float halfLength = dp(48) * (float) Math.sqrt(0.5) * revealProgress;
            canvas.rotate(45, centerX, centerY);
            canvas.clipRect(centerX - halfLength, centerY - dp(48), centerX + halfLength, centerY + dp(48));
            canvas.rotate(-45, centerX, centerY);
        }
        ribbon.draw(canvas);
        final float innerStrokeAlpha = ribbonColorTo == RIBBON_SENT_COLOR ? colorProgress
            : ribbonColorFrom == RIBBON_SENT_COLOR ? 1f - colorProgress : 0f;
        if (innerStrokeAlpha > 0f) {
            final Rect ribbonBounds = ribbon.getBounds();
            canvas.save();
            canvas.translate(ribbonBounds.right - dp(48), ribbonBounds.top);
            canvas.clipPath(ribbonInnerStrokePath);
            ribbonInnerStrokePaint.setAlpha((int) (255 * innerStrokeAlpha));
            canvas.drawPath(ribbonInnerStrokePath, ribbonInnerStrokePaint);
            canvas.restore();
        }
        drawRibbonHighlight(canvas, revealProgress);
        canvas.restore();
    }

    public boolean onTouchEvent(float offsetX, float offsetY, MotionEvent event) {
        final float x = event.getX() - offsetX;
        final float y = event.getY() - offsetY;
        if (onDiamondTouch(offsetX, offsetY, event)) return true;
        final float padding = dp(4);
        final boolean cardHit = x >= padding && x <= getWidth() - padding
            && y >= padding && y <= dp(140) - padding;
        boolean commentHit = false;
        if (commentText != null && (action.comment_encrypted || action.comment_encrypted_preparing) && !preparingCommentRevealed) {
            final float commentX = x - (getWidth() - commentText.getWidth()) / 2.0f;
            final float commentY = y - dp(143);
            for (SpoilerEffect spoiler : commentSpoilers) {
                if (spoiler.getBounds().contains((int) commentX, (int) commentY)) {
                    commentHit = true;
                    break;
                }
            }
        }
        final boolean hit = cardHit || commentHit;

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            touchCaptured = hit;
            bounce.setPressed(hit);
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (touchCaptured) {
                bounce.setPressed(hit);
            }
        } else if (event.getAction() == MotionEvent.ACTION_UP
            || event.getAction() == MotionEvent.ACTION_CANCEL) {
            final boolean handled = touchCaptured;
            final boolean clicked = event.getAction() == MotionEvent.ACTION_UP && handled && hit;
            touchCaptured = false;
            bounce.setPressed(false);
            if (clicked && commentHit && action.comment_encrypted_preparing) {
                preparingCommentRevealed = true;
                commentText.setText(Emoji.replaceEmoji(action.comment, commentText.paint.getFontMetricsInt(), false));
                commentSpoilersPool.addAll(commentSpoilers);
                commentSpoilers.clear();
                parentView.requestLayout();
                parentView.invalidate();
            } else if (clicked && onClick != null) {
                onClick.run();
            }
            return handled;
        }
        return touchCaptured;
    }

    public void setOnClickListener(Runnable onClick) {
        this.onClick = onClick;
    }

    public boolean isDiamondDragging() {
        return diamondPointer != MotionEvent.INVALID_POINTER_ID;
    }

    public boolean isDiamondTouch(float x, float y) {
        return diamondSurface != null && diamondSurface.isReady() && diamondBounds.contains((int) x, (int) y);
    }

    private boolean onDiamondTouch(float offsetX, float offsetY, MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (!isDiamondTouch(event.getX() - offsetX, event.getY() - offsetY)) return false;
            if (!diamondReturning && diamondScale <= 1.001f) {
                diamondYaw = getDiamondRestingYaw();
                diamondPitch = getDiamondRestingPitch();
            }
            if (diamondReturn != null) { diamondReturn.cancel(); diamondReturn = null; }
            diamondReturning = false;
            diamondPointer = event.getPointerId(0);
            diamondX = event.getX(); diamondY = event.getY();
            touchCaptured = false;
            bounce.setPressed(false);
            if (parentView.getParent() != null) parentView.getParent().requestDisallowInterceptTouchEvent(true);
            animateDiamondScale(3f);
            return true;
        }
        if (!isDiamondDragging()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                int index = event.findPointerIndex(diamondPointer);
                if (index < 0) { releaseDiamond(); return true; }
                float radiansPerPixel = (float) Math.toRadians(.8f) / AndroidUtilities.density;
                diamondYaw += (event.getX(index) - diamondX) * radiansPerPixel;
                diamondPitch += (event.getY(index) - diamondY) * radiansPerPixel;
                diamondX = event.getX(index); diamondY = event.getY(index);
                parentView.invalidate();
                break;
            case MotionEvent.ACTION_POINTER_UP:
                if (event.getPointerId(event.getActionIndex()) == diamondPointer) {
                    int next = event.getActionIndex() == 0 ? 1 : 0;
                    diamondPointer = event.getPointerId(next);
                    diamondX = event.getX(next); diamondY = event.getY(next);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                releaseDiamond();
                break;
        }
        return true;
    }

    private void animateDiamondScale(float scale) {
        if (diamondSpring == null) {
            diamondSpring = new SpringAnimation(new FloatValueHolder(diamondScale));
            diamondSpring.setSpring(new SpringForce(scale).setDampingRatio(.55f).setStiffness(280f));
            diamondSpring.setMinimumVisibleChange(.001f);
            diamondSpring.addUpdateListener((animation, value, velocity) -> {
                diamondScale = value;
                parentView.invalidate();
            });
        }
        diamondSpring.animateToFinalPosition(scale);
    }

    private void releaseDiamond() {
        diamondPointer = MotionEvent.INVALID_POINTER_ID;
        if (parentView.getParent() != null) parentView.getParent().requestDisallowInterceptTouchEvent(false);
        final float yawOffset = (float) Math.IEEEremainder(diamondYaw - getDiamondRestingYaw(), Math.PI * 2);
        final float pitchOffset = (float) Math.IEEEremainder(diamondPitch - getDiamondRestingPitch(), Math.PI * 2);
        diamondReturning = true;
        diamondReturn = ValueAnimator.ofFloat(0, 1);
        diamondReturn.setDuration(350);
        diamondReturn.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        diamondReturn.addUpdateListener(animation -> {
            float remaining = 1f - (float) animation.getAnimatedValue();
            diamondYaw = getDiamondRestingYaw() + yawOffset * remaining;
            diamondPitch = getDiamondRestingPitch() + pitchOffset * remaining;
            diamondReturning = remaining > 0;
            parentView.invalidate();
        });
        diamondReturn.start();
        animateDiamondScale(1f);
    }

    private void resetDiamond() {
        holdDiamondForFlight(false);
        sendingClockReveal.force(1f);
        if (isDiamondDragging() && parentView.getParent() != null) parentView.getParent().requestDisallowInterceptTouchEvent(false);
        diamondPointer = MotionEvent.INVALID_POINTER_ID;
        if (diamondReturn != null) { diamondReturn.cancel(); diamondReturn = null; }
        if (diamondSpring != null) { diamondSpring.cancel(); diamondSpring = null; }
        diamondReturning = false;
        diamondScale = 1;
        diamondPitch = 0;
        spinUpdated = 0;
        spinOffset = spinSpeed = sentSpinMomentum = 0;
        sentParticles.clear();
        if (diamondSurface != null) {
            diamondSurface.setScaleX(1);
            diamondSurface.setScaleY(1);
        }
    }

    public void setSpoilersSuppressed(boolean suppressed) {
        spoilersSuppressed = suppressed;
        for (SpoilerEffect spoiler : commentSpoilers) {
            spoiler.setSuppressUpdates(suppressed);
        }
    }

}
