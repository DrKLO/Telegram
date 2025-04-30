package org.telegram.ui.Components.voip;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.RLottieDrawable;

public class AcceptDeclineView extends View {

    private final FabBackgroundDrawable acceptDrawable;
    private final FabBackgroundDrawable declineDrawable;
    private final Drawable callDrawable;
    private final Drawable cancelDrawable;
    private final Paint acceptCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final StaticLayout acceptLayout;
    private final StaticLayout declineLayout;
    private final StaticLayout retryLayout;

    private final ButtonBounce acceptBounce = new ButtonBounce(this);
    private final ButtonBounce declineBounce = new ButtonBounce(this);

    private AcceptDeclineAccessibilityNodeProvider accessibilityNodeProvider;

    private int buttonWidth;

    float smallRadius;
    float bigRadius;
    boolean expandSmallRadius = true;
    boolean expandBigRadius = true;

    boolean startDrag;
    boolean captured;
    long capturedTime;
    boolean leftDrag;
    float startX;
    float startY;
    float touchSlop;
    float leftOffsetX;
    float rigthOffsetX;
    float maxOffset;

    Rect acceptRect = new Rect();
    Rect declineRect = new Rect();
    Animator leftAnimator;
    Animator rightAnimator;

    Listener listener;

    boolean retryMod;
    Drawable rippleDrawable;
    Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean isVideo;// = true;
    private RLottieDrawable acceptVoiceDrawable;
    private Drawable acceptVideoDrawable;
    private final ImageWithWavesView.AvatarWavesDrawable avatarWavesDrawable;
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public AcceptDeclineView(@NonNull Context context) {
        super(context);
        avatarWavesDrawable = new ImageWithWavesView.AvatarWavesDrawable(dp(45), dp(50), dp(8), 4);
        avatarWavesDrawable.muteToStatic = true;
        avatarWavesDrawable.muteToStaticProgress = 0f;
        avatarWavesDrawable.wavesEnter = 0;
        avatarWavesDrawable.setAmplitude(0);

        maskPaint.setColor(Color.BLACK);
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        buttonWidth = dp(60);
        acceptDrawable = new FabBackgroundDrawable();
        acceptDrawable.setColor(0xFF40C749);

        declineDrawable = new FabBackgroundDrawable();
        declineDrawable.setColor(0xFFF01D2C);

        declineDrawable.setBounds(0, 0, buttonWidth, buttonWidth);
        acceptDrawable.setBounds(0, 0, buttonWidth, buttonWidth);

        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(dp(11));
        textPaint.setColor(Color.WHITE);

        String acceptStr = LocaleController.getString(R.string.AcceptCall);
        String declineStr = LocaleController.getString(R.string.DeclineCall);
        String retryStr = LocaleController.getString(R.string.RetryCall);

        acceptLayout = new StaticLayout(acceptStr, textPaint, (int) textPaint.measureText(acceptStr), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        declineLayout = new StaticLayout(declineStr, textPaint, (int) textPaint.measureText(declineStr), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

        retryLayout = new StaticLayout(retryStr, textPaint, (int) textPaint.measureText(retryStr), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

        callDrawable = ContextCompat.getDrawable(context, R.drawable.calls_decline).mutate();
        cancelDrawable = ContextCompat.getDrawable(context, R.drawable.ic_close_white).mutate();
        cancelDrawable.setColorFilter(new PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.MULTIPLY));
        acceptVoiceDrawable = new RLottieDrawable(R.raw.call_accept, "" + R.raw.call_accept, dp(48), dp(48), true, null);
        acceptVoiceDrawable.setAutoRepeat(1);
        acceptVoiceDrawable.setCustomEndFrame(90);
        acceptVoiceDrawable.setMasterParent(this);
        acceptVideoDrawable = ContextCompat.getDrawable(context, R.drawable.calls_video).mutate();
        acceptCirclePaint.setColor(Color.WHITE);
        acceptCirclePaint.setAlpha(20);
        rippleDrawable = Theme.createSimpleSelectorCircleDrawable(dp(52), 0, ColorUtils.setAlphaComponent(Color.WHITE, (int) (255 * 0.3f)));
        rippleDrawable.setCallback(this);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        maxOffset = getMeasuredWidth() / 2f - (buttonWidth / 2f + dp(46));

        int padding = (buttonWidth - dp(28)) / 2;
        callDrawable.setBounds(padding, padding, padding + dp(28), padding + dp(28));
        cancelDrawable.setBounds(padding, padding, padding + dp(28), padding + dp(28));

        linePaint.setStrokeWidth(dp(3));
        linePaint.setColor(Color.WHITE);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                startX = event.getX();
                startY = event.getY();
                if (leftAnimator == null && declineRect.contains((int) event.getX(), (int) event.getY())) {
                    rippleDrawable = Theme.createSimpleSelectorCircleDrawable(dp(52), 0, retryMod ? Theme.getColor(Theme.key_listSelector) : 0xFFFF3846);
                    captured = true;
                    leftDrag = true;
                    declineBounce.setPressed(true);
                    acceptBounce.setPressed(false);
                    setPressed(true);
                    invalidate();
                    return true;
                }
                if (rightAnimator == null && acceptRect.contains((int) event.getX(), (int) event.getY())) {
                    rippleDrawable = Theme.createSimpleSelectorCircleDrawable(dp(52), 0, 0xFF4DD156);
                    captured = true;
                    leftDrag = false;
                    declineBounce.setPressed(false);
                    acceptBounce.setPressed(true);
                    setPressed(true);
                    if (rightAnimator != null) {
                        rightAnimator.cancel();
                    }
                    invalidate();
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (captured) {
                    return true;
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                float dy = event.getY() - startY;
                if (captured) {
                    if (leftDrag) {
                        ValueAnimator animator = ValueAnimator.ofFloat(leftOffsetX, 0);
                        animator.addUpdateListener(valueAnimator -> {
                            leftOffsetX = (float) valueAnimator.getAnimatedValue();
                            invalidate();
                            leftAnimator = null;
                        });
                        animator.start();
                        leftAnimator = animator;
                        if (listener != null) {
                            if ((!startDrag && Math.abs(dy) < touchSlop) || leftOffsetX > maxOffset * 0.8f) {
                                listener.onDecline();
                            }
                        }
                    } else {
                        ValueAnimator animator = ValueAnimator.ofFloat(rigthOffsetX, 0);
                        animator.addUpdateListener(valueAnimator -> {
                            rigthOffsetX = (float) valueAnimator.getAnimatedValue();
                            invalidate();
                            rightAnimator = null;
                        });
                        animator.start();
                        rightAnimator = animator;
                        if (listener != null) {
                            if ((!startDrag && Math.abs(dy) < touchSlop) || -rigthOffsetX > maxOffset * 0.8f) {
                                listener.onAccept();
                            }
                        }
                    }
                }
                getParent().requestDisallowInterceptTouchEvent(false);
                captured = false;
                startDrag = false;
                declineBounce.setPressed(false);
                acceptBounce.setPressed(false);
                setPressed(false);
                break;
        }
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!retryMod) {
            if (expandSmallRadius) {
                smallRadius += dp(2) * 0.04f;
                if (smallRadius > dp(4)) {
                    smallRadius = dp(4);
                    expandSmallRadius = false;
                }
            } else {
                smallRadius -= dp(2) * 0.04f;
                if (smallRadius < 0) {
                    smallRadius = 0;
                    expandSmallRadius = true;
                }
            }

            if (expandBigRadius) {
                bigRadius += dp(4) * 0.03f;
                if (bigRadius > dp(10)) {
                    bigRadius = dp(10);
                    expandBigRadius = false;
                }
            } else {
                bigRadius -= dp(5) * 0.03f;
                if (bigRadius < dp(5)) {
                    bigRadius = dp(5);
                    expandBigRadius = true;
                }
            }
            invalidate();
        }

        bigRadius += dp(8) * 0.005f;
        declineRect.set(getMeasuredWidth() - dp(46) - buttonWidth, dp(40), getMeasuredWidth() - dp(46), dp(40) + buttonWidth);
        canvas.save();
        canvas.translate(0, dp(40));
        canvas.save();
        float s = declineBounce.getScale(0.1f);
        canvas.scale(s, s, declineRect.centerX(), declineRect.top + buttonWidth / 2f);
        canvas.translate(rigthOffsetX + getMeasuredWidth() - dp(46) - buttonWidth, 0);

        if (retryMod) {
            canvas.saveLayer(0, 0, getMeasuredWidth(), getMeasuredHeight(), linePaint, Canvas.ALL_SAVE_FLAG);
            declineDrawable.draw(canvas);
            if (cancelDrawable instanceof BitmapDrawable) {
                BitmapDrawable bitmapDrawable = (BitmapDrawable) cancelDrawable;
                if (bitmapDrawable.getBitmap() != null) {
                    canvas.drawBitmap(bitmapDrawable.getBitmap(), null, bitmapDrawable.getBounds(), maskPaint);
                }
            }
            canvas.restore();
        } else {
            declineDrawable.draw(canvas);
            callDrawable.draw(canvas);
        }

        canvas.save();
        canvas.translate(buttonWidth / 2f - declineLayout.getWidth() / 2f, buttonWidth + dp(4));
        declineLayout.draw(canvas);
        canvas.restore();

        if (leftDrag) {
            rippleDrawable.setBounds(dp(4), dp(4), buttonWidth - dp(4), buttonWidth - dp(4));
            rippleDrawable.draw(canvas);
        }

        canvas.restore();

        acceptRect.set(dp(46), dp(40), dp(46) + buttonWidth, dp(40) + buttonWidth);
        canvas.save();
        s = acceptBounce.getScale(0.1f);
        canvas.scale(s, s, acceptRect.centerX(), acceptRect.top + buttonWidth / 2f);
        canvas.translate(leftOffsetX + dp(46), 0);
        if (!retryMod) {
            avatarWavesDrawable.update();
            int cx = (int) (buttonWidth / 2f);
            avatarWavesDrawable.draw(canvas, cx, cx, this);
        }
        acceptDrawable.draw(canvas);

        if (retryMod) {
            canvas.save();
            canvas.translate(buttonWidth / 2f - retryLayout.getWidth() / 2f, buttonWidth + dp(4));
            retryLayout.draw(canvas);
            canvas.restore();
        } else {
            canvas.save();
            canvas.translate(buttonWidth / 2f - acceptLayout.getWidth() / 2f, buttonWidth + dp(4));
            acceptLayout.draw(canvas);
            canvas.restore();
        }

        canvas.save();
        canvas.translate(dp(6), dp(6));
        if (isVideo) {
            final int sz = dp(28);
            canvas.translate((dp(48) - sz) / 2f, (dp(48) - sz) / 2f);
            acceptVideoDrawable.setBounds(0, 0, sz, sz);
            acceptVideoDrawable.draw(canvas);
        } else {
            acceptVoiceDrawable.draw(canvas);
        }
        canvas.restore();

        if (!leftDrag) {
            rippleDrawable.setBounds(dp(4), dp(4), buttonWidth - dp(4), buttonWidth - dp(4));
            rippleDrawable.draw(canvas);
        }

        canvas.restore();
        canvas.restore();

        if (captured) {
            invalidate();
        }
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public interface Listener {
        void onAccept();

        void onDecline();
    }

    private ValueAnimator callAnimator;

    public void setRetryMod(boolean retryMod) {
        this.retryMod = retryMod;
        if (retryMod) {
            declineDrawable.setColor(Color.WHITE);
        } else {
            acceptVoiceDrawable.start();
            avatarWavesDrawable.setShowWaves(true, this);
            declineDrawable.setColor(0xFFF01D2C);

            callAnimator = ValueAnimator.ofInt(0, 60, 0, 0, 60, 0, 0, 0, 0);
            callAnimator.addUpdateListener(a -> {
                avatarWavesDrawable.setAmplitude((int) a.getAnimatedValue());
            });

            callAnimator.setDuration(1500);
            callAnimator.setRepeatMode(ValueAnimator.RESTART);
            callAnimator.setRepeatCount(ValueAnimator.INFINITE);
            callAnimator.start();
        }
    }

    public void stopAnimations() {
        if (callAnimator != null) {
            callAnimator.cancel();
            callAnimator = null;
            acceptVoiceDrawable.stop();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimations();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        rippleDrawable.setState(getDrawableState());
    }

    @Override
    public boolean verifyDrawable(@NonNull Drawable drawable) {
        return rippleDrawable == drawable || super.verifyDrawable(drawable);
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (rippleDrawable != null) {
            rippleDrawable.jumpToCurrentState();
        }
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        if (accessibilityNodeProvider != null && accessibilityNodeProvider.onHoverEvent(event)) {
            return true;
        }
        return super.onHoverEvent(event);
    }

    @Override
    public AccessibilityNodeProvider getAccessibilityNodeProvider() {
        if (accessibilityNodeProvider == null) {
            accessibilityNodeProvider = new AcceptDeclineAccessibilityNodeProvider(this, 2) {

                private static final int ACCEPT_VIEW_ID = 0;
                private static final int DECLINE_VIEW_ID = 1;

                private final int[] coords = {0, 0};

                @Override
                protected CharSequence getVirtualViewText(int virtualViewId) {
                    if (virtualViewId == ACCEPT_VIEW_ID) {
                        if (retryMod) {
                            if (retryLayout != null) {
                                return retryLayout.getText();
                            }
                        } else {
                            if (acceptLayout != null) {
                                return acceptLayout.getText();
                            }
                        }
                    } else if (virtualViewId == DECLINE_VIEW_ID) {
                        if (declineLayout != null) {
                            return declineLayout.getText();
                        }
                    }
                    return null;
                }

                @Override
                protected void getVirtualViewBoundsInScreen(int virtualViewId, Rect outRect) {
                    getVirtualViewBoundsInParent(virtualViewId, outRect);
                    getLocationOnScreen(coords);
                    outRect.offset(coords[0], coords[1]);
                }

                @Override
                protected void getVirtualViewBoundsInParent(int virtualViewId, Rect outRect) {
                    if (virtualViewId == ACCEPT_VIEW_ID) {
                        outRect.set(acceptRect);
                    } else if (virtualViewId == DECLINE_VIEW_ID) {
                        outRect.set(declineRect);
                    } else {
                        outRect.setEmpty();
                    }
                }

                @Override
                protected void onVirtualViewClick(int virtualViewId) {
                    if (listener != null) {
                        if (virtualViewId == ACCEPT_VIEW_ID) {
                            listener.onAccept();
                        } else if (virtualViewId == DECLINE_VIEW_ID) {
                            listener.onDecline();
                        }
                    }
                }
            };
        }
        return accessibilityNodeProvider;
    }

    public static abstract class AcceptDeclineAccessibilityNodeProvider extends AccessibilityNodeProvider {

        private final View hostView;
        private final int virtualViewsCount;
        private final Rect rect = new Rect();
        private final AccessibilityManager accessibilityManager;

        private int currentFocusedVirtualViewId = View.NO_ID;

        protected AcceptDeclineAccessibilityNodeProvider(View hostView, int virtualViewsCount) {
            this.hostView = hostView;
            this.virtualViewsCount = virtualViewsCount;
            this.accessibilityManager = ContextCompat.getSystemService(hostView.getContext(), AccessibilityManager.class);
        }

        @Override
        public AccessibilityNodeInfo createAccessibilityNodeInfo(int virtualViewId) {
            AccessibilityNodeInfo nodeInfo;
            if (virtualViewId == HOST_VIEW_ID) {
                nodeInfo = AccessibilityNodeInfo.obtain(hostView);
                nodeInfo.setPackageName(hostView.getContext().getPackageName());
                for (int i = 0; i < virtualViewsCount; i++) {
                    nodeInfo.addChild(hostView, i);
                }
            } else {
                nodeInfo = AccessibilityNodeInfo.obtain(hostView, virtualViewId);
                nodeInfo.setPackageName(hostView.getContext().getPackageName());
                if (Build.VERSION.SDK_INT >= 21) {
                    nodeInfo.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
                }
                nodeInfo.setText(getVirtualViewText(virtualViewId));
                nodeInfo.setClassName(Button.class.getName());
                if (Build.VERSION.SDK_INT >= 24) {
                    nodeInfo.setImportantForAccessibility(true);
                }
                nodeInfo.setVisibleToUser(true);
                nodeInfo.setClickable(true);
                nodeInfo.setEnabled(true);
                nodeInfo.setParent(hostView);
                getVirtualViewBoundsInScreen(virtualViewId, rect);
                nodeInfo.setBoundsInScreen(rect);
            }
            return nodeInfo;
        }

        @Override
        public boolean performAction(int virtualViewId, int action, Bundle arguments) {
            if (virtualViewId == HOST_VIEW_ID) {
                return hostView.performAccessibilityAction(action, arguments);
            } else {
                if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) {
                    sendAccessibilityEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
                } else if (action == AccessibilityNodeInfo.ACTION_CLICK) {
                    onVirtualViewClick(virtualViewId);
                    return true;
                }
            }
            return false;
        }

        public boolean onHoverEvent(MotionEvent event) {
            final int x = (int) event.getX();
            final int y = (int) event.getY();
            if (event.getAction() == MotionEvent.ACTION_HOVER_ENTER || event.getAction() == MotionEvent.ACTION_HOVER_MOVE) {
                for (int i = 0; i < virtualViewsCount; i++) {
                    getVirtualViewBoundsInParent(i, rect);
                    if (rect.contains(x, y)) {
                        if (i != currentFocusedVirtualViewId) {
                            currentFocusedVirtualViewId = i;
                            sendAccessibilityEventForVirtualView(i, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED);
                        }
                        return true;
                    }
                }
            } else if (event.getAction() == MotionEvent.ACTION_HOVER_EXIT) {
                if (currentFocusedVirtualViewId != View.NO_ID) {
                    currentFocusedVirtualViewId = View.NO_ID;
                    return true;
                }
            }
            return false;
        }

        private void sendAccessibilityEventForVirtualView(int virtualViewId, int eventType) {
            if (accessibilityManager.isTouchExplorationEnabled()) {
                final ViewParent parent = hostView.getParent();
                if (parent != null) {
                    final AccessibilityEvent event = AccessibilityEvent.obtain(eventType);
                    event.setPackageName(hostView.getContext().getPackageName());
                    event.setSource(hostView, virtualViewId);
                    parent.requestSendAccessibilityEvent(hostView, event);
                }
            }
        }

        protected abstract CharSequence getVirtualViewText(int virtualViewId);

        protected abstract void getVirtualViewBoundsInScreen(int virtualViewId, Rect outRect);

        protected abstract void getVirtualViewBoundsInParent(int virtualViewId, Rect outRect);

        protected abstract void onVirtualViewClick(int virtualViewId);
    }
}
