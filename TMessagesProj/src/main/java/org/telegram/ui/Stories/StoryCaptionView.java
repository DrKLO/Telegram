package org.telegram.ui.Stories;

import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.widget.NestedScrollView;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.checkerframework.checker.units.qual.A;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.StaticLayoutEx;
import org.telegram.ui.Components.URLSpanMono;
import org.telegram.ui.Components.spoilers.SpoilerEffect;
import org.telegram.ui.Components.spoilers.SpoilersClickDetector;
import org.telegram.ui.Components.spoilers.SpoilersTextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;

public class StoryCaptionView extends NestedScrollView {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    TextSelectionHelper.SimpleTextSelectionHelper textSelectionHelper;

    private final SpringAnimation springAnimation;
    public StoryCaptionTextView captionTextview;

    private boolean nestedScrollStarted;
    private float overScrollY;
    private float velocitySign;
    private float velocityY;

    private float lastMotionX;
    private float lastMotionY;

    private Method abortAnimatedScrollMethod;
    private OverScroller scroller;

    private boolean isLandscape;
    private int textHash;
    private int prevHeight;

    private float backgroundAlpha = 1f;
    private boolean dontChangeTopMargin;
    private int pendingTopMargin = -1;
    FrameLayout captionContainer;

    public boolean disableTouches;
    private boolean disableDraw;

    public int blackoutBottomOffset;

    int gradientColor = ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.2f));
    GradientDrawable topOverlayGradient = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0, gradientColor});

    public StoryCaptionView(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.captionContainer = new FrameLayout(context);
        setClipChildren(false);
        setOverScrollMode(View.OVER_SCROLL_NEVER);

        NotificationCenter.listenEmojiLoading(this);

        captionTextview = new StoryCaptionTextView(getContext(), resourcesProvider);
        textSelectionHelper = new TextSelectionHelper.SimpleTextSelectionHelper(captionTextview, resourcesProvider);
        textSelectionHelper.useMovingOffset = false;
        captionContainer.addView(captionTextview, LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        addView(captionContainer, new ViewGroup.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        paint.setColor(Color.BLACK);
        setFadingEdgeLength(AndroidUtilities.dp(12));
        setVerticalFadingEdgeEnabled(true);
        setWillNotDraw(false);

        springAnimation = new SpringAnimation(captionTextview, DynamicAnimation.TRANSLATION_Y, 0);
        springAnimation.getSpring().setStiffness(100f);
        springAnimation.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS);
        springAnimation.addUpdateListener((animation, value, velocity) -> {
            overScrollY = value;
            velocityY = velocity;
        });
        springAnimation.getSpring().setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY);

        try {
            abortAnimatedScrollMethod = NestedScrollView.class.getDeclaredMethod("abortAnimatedScroll");
            abortAnimatedScrollMethod.setAccessible(true);
        } catch (Exception e) {
            abortAnimatedScrollMethod = null;
            FileLog.e(e);
        }

        try {
            final Field scrollerField = NestedScrollView.class.getDeclaredField("mScroller");
            scrollerField.setAccessible(true);
            scroller = (OverScroller) scrollerField.get(this);
        } catch (Exception e) {
            scroller = null;
            FileLog.e(e);
        }
    }

    public void onLinkLongPress(URLSpan span, View spoilersTextView, Runnable done) {

    }

    public void onLinkClick(CharacterStyle span, View spoilersTextView) {

    }

    public void onEmojiClick(AnimatedEmojiSpan span) {

    }

    @Override
    public boolean onInterceptTouchEvent(@NonNull MotionEvent ev) {
        if (captionTextview.progressToExpand != 1f || disableTouches || ev.getAction() == MotionEvent.ACTION_DOWN && ev.getY() < captionContainer.getTop() - getScrollY() + captionTextview.getTranslationY()) {
            if (touched) {
                touched = false;
                invalidate();
            }
            return false;
        }
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            touched = true;
            invalidate();
        } else if (touched && (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL)) {
            touched = false;
            invalidate();
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (captionTextview.progressToExpand != 1f || disableTouches || ev.getAction() == MotionEvent.ACTION_DOWN && ev.getY() < captionContainer.getTop() - getScrollY() + captionTextview.getTranslationY()) {
            if (touched) {
                touched = false;
                invalidate();
            }
            return false;
        }
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            touched = true;
            invalidate();
        } else if (touched && (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL)) {
            touched = false;
            invalidate();
        }
        return super.onTouchEvent(ev);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        updateTopMargin(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void applyPendingTopMargin() {
        dontChangeTopMargin = false;
        if (pendingTopMargin >= 0) {
            ((MarginLayoutParams) captionContainer.getLayoutParams()).topMargin = pendingTopMargin;
            pendingTopMargin = -1;
            requestLayout();
        }
    }

    public int getPendingMarginTopDiff() {
        if (pendingTopMargin >= 0) {
            return pendingTopMargin - ((MarginLayoutParams) captionContainer.getLayoutParams()).topMargin;
        } else {
            return 0;
        }
    }

    public void updateTopMargin() {
        updateTopMargin(getWidth(), getHeight());
    }

    private void updateTopMargin(int width, int height) {
        final int marginTop = calculateNewContainerMarginTop(width, height);
        if (marginTop >= 0) {
            if (dontChangeTopMargin) {
                pendingTopMargin = marginTop;
            } else {
                ((MarginLayoutParams) captionContainer.getLayoutParams()).topMargin = marginTop;
                pendingTopMargin = -1;
            }
        }
    }

    public int calculateNewContainerMarginTop(int width, int height) {
        if (width == 0 || height == 0) {
            return -1;
        }

        final StoryCaptionTextView textView = captionTextview;
        final CharSequence text = textView.text;

        final int textHash = text.hashCode();
        final boolean isLandscape = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y;

        if (this.textHash == textHash && this.isLandscape == isLandscape && this.prevHeight == height) {
            return -1;
        }

        this.textHash = textHash;
        this.isLandscape = isLandscape;
        this.prevHeight = height;

        textView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));

        final Layout layout = textView.fullLayout;
        final int lineCount = layout.getLineCount();

        if (lineCount <= 3) {
            return height - textView.getMeasuredHeight();
        }

        int i = Math.min(3, lineCount);

        final int lineHeight = textView.textPaint.getFontMetricsInt(null);
        return height - lineHeight * (i + 1);// - AndroidUtilities.dp(8);
    }

    public void reset() {
        scrollTo(0, 0);
        expanded = false;
        captionTextview.progressToExpand = 0f;
        captionTextview.invalidate();
    }

    public void stopScrolling() {
        if (abortAnimatedScrollMethod != null) {
            try {
                abortAnimatedScrollMethod.invoke(this);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    @Override
    public void fling(int velocityY) {
        super.fling(velocityY);
        this.velocitySign = Math.signum(velocityY);
        this.velocityY = 0f;
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow, int type) {
        consumed[1] = 0;

        if (nestedScrollStarted && (overScrollY > 0 && dy > 0 || overScrollY < 0 && dy < 0)) {
            final float delta = overScrollY - dy;

            if (overScrollY > 0) {
                if (delta < 0) {
                    overScrollY = 0;
                    consumed[1] += dy + delta;
                } else {
                    overScrollY = delta;
                    consumed[1] += dy;
                }
            } else {
                if (delta > 0) {
                    overScrollY = 0;
                    consumed[1] += dy + delta;
                } else {
                    overScrollY = delta;
                    consumed[1] += dy;
                }
            }

            captionTextview.setTranslationY(overScrollY);
            textSelectionHelper.invalidate();
            return true;
        }

        return false;
    }

    @Override
    public void dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, @Nullable int[] offsetInWindow, int type, @NonNull int[] consumed) {
        if (dyUnconsumed != 0) {
            final int topMargin = 0;//(isStatusBarVisible() ? AndroidUtilities.statusBarHeight : 0) + ActionBar.getCurrentActionBarHeight();
            final int dy = Math.round(dyUnconsumed * (1f - Math.abs((-overScrollY / (captionContainer.getTop() - topMargin)))));

            if (dy != 0) {
                if (!nestedScrollStarted) {
                    if (!springAnimation.isRunning()) {
                        int consumedY;
                        float velocity = scroller != null ? scroller.getCurrVelocity() : Float.NaN;
                        if (!Float.isNaN(velocity)) {
                            final float clampedVelocity = Math.min(AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y ? 3000 : 5000, velocity);
                            consumedY = (int) (dy * clampedVelocity / velocity);
                            velocity = clampedVelocity * -velocitySign;
                        } else {
                            consumedY = dy;
                            velocity = 0;
                        }
                        if (consumedY != 0) {
                            overScrollY -= consumedY;
                            captionTextview.setTranslationY(overScrollY);
                        }
                        startSpringAnimationIfNotRunning(velocity);
                    }
                } else {
                    overScrollY -= dy;
                    captionTextview.setTranslationY(overScrollY);
                }
            }
        }
        textSelectionHelper.invalidate();
    }

    private void startSpringAnimationIfNotRunning(float velocityY) {
        if (!springAnimation.isRunning()) {
            springAnimation.setStartVelocity(velocityY);
            springAnimation.start();
        }
        if (getScrollY() < AndroidUtilities.dp(2)) {
            collapse();
        }
    }

    @Override
    public boolean startNestedScroll(int axes, int type) {
        if (type == ViewCompat.TYPE_TOUCH) {
            springAnimation.cancel();
            nestedScrollStarted = true;
            overScrollY = captionTextview.getTranslationY();
        }
        return true;
    }

    @Override
    public void computeScroll() {
        super.computeScroll();
        if (!nestedScrollStarted && overScrollY != 0 && scroller != null && scroller.isFinished()) {
            startSpringAnimationIfNotRunning(0);
        }
    }

    @Override
    public void stopNestedScroll(int type) {
        if (nestedScrollStarted && type == ViewCompat.TYPE_TOUCH) {
            nestedScrollStarted = false;
            if (overScrollY != 0 && scroller != null && scroller.isFinished()) {
                startSpringAnimationIfNotRunning(velocityY);
            }
        }
    }

    @Override
    protected float getTopFadingEdgeStrength() {
        return 1f;
    }

    @Override
    protected float getBottomFadingEdgeStrength() {
        return 1f;
    }


    @Override
    public void draw(Canvas canvas) {
        if (disableDraw) {
            return;
        }

        // captionTextview.allowClickSpoilers = !canScrollVertically(1);

        final int width = getWidth();
        final int height = getHeight();
        final int scrollY = getScrollY();

        final int saveCount = canvas.save();
        canvas.clipRect(0, scrollY, width, height + scrollY + blackoutBottomOffset);

//        int gradientHeight = AndroidUtilities.dp(24);
//        int gradientTop = (int) (captionContainer.getTop() + captionTextview.getTranslationY() - AndroidUtilities.dp(4));
//        int gradientBottom = gradientTop + gradientHeight;
//        paint.setColor(gradientColor);
//        topOverlayGradient.setBounds(0, gradientTop, getMeasuredWidth(), gradientBottom);
//        topOverlayGradient.draw(canvas);
//        canvas.drawRect(0, gradientBottom, width, height + scrollY + blackoutBottomOffset, paint);

        canvas.clipRect(0, scrollY, width, height + scrollY);
        super.draw(canvas);
        canvas.restoreToCount(saveCount);
    }

    public float getTextTop() {
        return captionContainer.getTop() + captionTextview.getTranslationY() - getScrollY();
    }

    public float getMaxTop() {
        return captionContainer.getTop() - (captionContainer.getBottom() - getMeasuredHeight());
    }

    public boolean allowInterceptTouchEvent(float x, float y) {
        if (captionTextview.progressToExpand == 1f && !disableTouches && y > captionContainer.getTop() - getScrollY() + captionTextview.getTranslationY()) {
            return true;
        }
        return false;
    }

    @Override
    public void scrollBy(int x, int y) {
        super.scrollBy(x, y);
        invalidate();
    }


    @Override
    public void invalidate() {
        super.invalidate();
        if (getParent() != null) {
            ((View) getParent()).invalidate();
        }
        textSelectionHelper.invalidate();
    }

    public float getProgressToBlackout() {
        int maxHeight = Math.min(prevHeight, AndroidUtilities.dp(40));
        return Utilities.clamp((getScrollY() - captionTextview.getTranslationY()) / maxHeight, 1f, 0);
    }

    boolean expanded;
    public void expand() {
        if (expanded) {
            return;
        }
        expanded = true;
        float fromScrollY = getScrollY();
        float toScrollY = (captionContainer.getBottom() - getMeasuredHeight());
        float fromP = captionTextview.progressToExpand;
        float toP = 1f;
        ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
        valueAnimator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            setScrollY((int) AndroidUtilities.lerp(fromScrollY, toScrollY, value));
            captionTextview.progressToExpand = AndroidUtilities.lerp(fromP, toP, value);
            captionTextview.invalidate();
        });
        valueAnimator.setDuration(250);
        valueAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        valueAnimator.start();
        //fullScroll(View.FOCUS_DOWN);
    }

    public void collapse() {
        if (!expanded) {
            return;
        }
        expanded = false;
        float fromScrollY = getScrollY();
        float toScrollY = 0;
        float fromP = captionTextview.progressToExpand;
        float toP = 0;
        ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
        valueAnimator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            setScrollY((int) AndroidUtilities.lerp(fromScrollY, toScrollY, value));
            captionTextview.progressToExpand = AndroidUtilities.lerp(fromP, toP, value);
            captionTextview.invalidate();
        });
        valueAnimator.setDuration(250);
        valueAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        valueAnimator.start();
    }

    public void disableDraw(boolean disableDraw) {
        if (this.disableDraw != disableDraw) {
            this.disableDraw = disableDraw;
            invalidate();
        }
    }

    public boolean isTouched() {
        return touched;
    }

    boolean touched;

    public void cancelTouch() {
        //captionTextview.clearPressedLinks();
        touched = false;
    }

    public boolean hasScroll() {
        return captionContainer.getBottom() - getMeasuredHeight() > 0;
    }

    public void checkCancelTextSelection() {
        if (textSelectionHelper.isInSelectionMode()) {
            textSelectionHelper.getOverlayView(getContext()).checkCancel(lastMotionX, lastMotionY, false);
        }
    }

    public class StoryCaptionTextView extends View implements TextSelectionHelper.SelectableView, TextSelectionHelper.SimpleSelectabeleView {

        private final PorterDuffColorFilter emojiColorFilter;
        private LinkSpanDrawable<CharacterStyle> pressedLink;
        private AnimatedEmojiSpan pressedEmoji;
        private LinkSpanDrawable.LinkCollector links = new LinkSpanDrawable.LinkCollector(this);

        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        TextPaint showMorePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint xRefPaint = new Paint();
        private final Paint xRefGradinetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        int sizeCached = 0;
        StaticLayout showMore;
        StaticLayout fullLayout;
        StaticLayout firstLayout;
        LineInfo[] nextLinesLayouts;

        CharSequence text = "";
        int textHeight;
        float textX;
        float textY;
        float progressToExpand;
        float showMoreY;
        float showMoreX;

        //spoilers
        private SpoilersClickDetector clickDetector;
        protected List<SpoilerEffect> spoilers = new ArrayList<>();
        private Stack<SpoilerEffect> spoilersPool = new Stack<>();
        private boolean isSpoilersRevealed;
        private Path path = new Path();
        public boolean allowClickSpoilers = true;

        int horizontalPadding;
        int verticalPadding;
        private AnimatedEmojiSpan.EmojiGroupedSpans fullLayoutEmoji;
        private AnimatedEmojiSpan.EmojiGroupedSpans firstLayoutEmoji;


        public StoryCaptionTextView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            textPaint.setColor(Color.WHITE);
            textPaint.linkColor = Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider);
            textPaint.setTextSize(AndroidUtilities.dp(15));

            showMorePaint.setColor(Color.WHITE);
            showMorePaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            showMorePaint.setTextSize(AndroidUtilities.dp(16));

            xRefPaint.setColor(0xff000000);
            xRefPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

            xRefGradinetPaint.setShader(new LinearGradient(0, 0, AndroidUtilities.dp(16), 0, new int[]{0, 0xffffffff}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            xRefGradinetPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

            clickDetector = new SpoilersClickDetector(this, spoilers, (eff, x, y) -> {
                if (isSpoilersRevealed) return;

                eff.setOnRippleEndCallback(() -> post(() -> {
                    isSpoilersRevealed = true;
                    // invalidateSpoilers();
                }));

                float rad = (float) Math.sqrt(Math.pow(getWidth(), 2) + Math.pow(getHeight(), 2));
                for (SpoilerEffect ef : spoilers)
                    ef.startRipple(x, y, rad);
            });

            emojiColorFilter = new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        }

        public void setText(CharSequence text) {
            if (text == null) {
                text = "";
            }
            isSpoilersRevealed = false;
            //  invalidateSpoilers();
            this.text = text;
            sizeCached = 0;
            if (getMeasuredWidth() > 0) {
                createLayout(getMeasuredWidth());
            }
            requestLayout();
            invalidate();
        }

        @SuppressLint("DrawAllocation")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int size = widthMeasureSpec + heightMeasureSpec << 16;
            horizontalPadding = AndroidUtilities.dp(16);
            verticalPadding = AndroidUtilities.dp(8);
            if (sizeCached != size) {
                sizeCached = size;
                createLayout(MeasureSpec.getSize(widthMeasureSpec));
            }
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(verticalPadding * 2 + textHeight, MeasureSpec.EXACTLY));
        }

        private void createLayout(int measuredWidth) {
            int width = measuredWidth - horizontalPadding * 2;
            fullLayout = makeTextLayout(textPaint, text, width);
            textHeight = fullLayout.getHeight();
            textX = horizontalPadding;
            textY = verticalPadding;
            float space = textPaint.measureText(" ");
            if (fullLayout.getLineCount() > 3) {
                String showMoreText = LocaleController.getString("ShowMore", R.string.ShowMore);
                showMore = makeTextLayout(showMorePaint, showMoreText, width);

                float collapsedY = fullLayout.getLineTop(2) + fullLayout.getTopPadding();
                showMoreY = textY + collapsedY - AndroidUtilities.dpf2(0.3f);
                showMoreX = width - horizontalPadding - showMorePaint.measureText(showMoreText);
                firstLayout = makeTextLayout(textPaint, text.subSequence(0, fullLayout.getLineEnd(2)), width);
                spoilersPool.addAll(spoilers);
                spoilers.clear();
                SpoilerEffect.addSpoilers(this, fullLayout, spoilersPool, spoilers);

                float x = fullLayout.getLineRight(2) + space;
                if (nextLinesLayouts != null) {
                    for (int i = 0; i < nextLinesLayouts.length; i++) {
                        if (nextLinesLayouts[i] == null) {
                            continue;
                        }
                        AnimatedEmojiSpan.release(this, nextLinesLayouts[i].layoutEmoji);
                    }
                }
                nextLinesLayouts = new LineInfo[fullLayout.getLineCount() - 3];

                if (spoilers.isEmpty()) {
                    for (int line = 3; line < fullLayout.getLineCount(); ++line) {
                        int s = fullLayout.getLineStart(line), e = fullLayout.getLineEnd(line);
                        final StaticLayout layout = makeTextLayout(textPaint, text.subSequence(Math.min(s, e), Math.max(s, e)), width);
                        LineInfo lineInfo = new LineInfo();
                        nextLinesLayouts[line - 3] = lineInfo;
                        lineInfo.staticLayout = layout;
                        lineInfo.finalX = fullLayout.getLineLeft(line);
                        lineInfo.finalY = fullLayout.getLineTop(line) + fullLayout.getTopPadding();
                        if (x < showMoreX - AndroidUtilities.dp(16)) {
                            lineInfo.collapsedY = collapsedY;
                            lineInfo.collapsedX = x;
                            x += layout.getLineRight(0) + space;
                        } else {
                            lineInfo.collapsedY = lineInfo.finalY;
                            lineInfo.collapsedX = lineInfo.finalX;
                        }
                    }
                }
            } else {
                showMore = null;
                firstLayout = null;
                spoilersPool.addAll(spoilers);
                spoilers.clear();
                SpoilerEffect.addSpoilers(this, fullLayout, spoilersPool, spoilers);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (showMore != null) {
                canvas.saveLayerAlpha(textX - horizontalPadding, textY, getMeasuredWidth(), getMeasuredHeight() - verticalPadding, 255, Canvas.ALL_SAVE_FLAG);
            } else {
                canvas.save();
            }

            canvas.save();
            canvas.translate(textX, textY);
            if (links.draw(canvas)) {
                invalidate();
            }
            canvas.restore();

            if (!spoilers.isEmpty() || firstLayout == null) {
                if (fullLayout != null) {
                    canvas.save();
                    canvas.translate(textX, textY);
                    if (textSelectionHelper.isInSelectionMode()) {
                        textSelectionHelper.draw(canvas);
                    }
                    drawLayout(fullLayout, canvas);
                    fullLayoutEmoji = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, fullLayoutEmoji, fullLayout);
                    AnimatedEmojiSpan.drawAnimatedEmojis(canvas, fullLayout, fullLayoutEmoji, 0, spoilers, 0, 0, 0, 1f, emojiColorFilter);
                    canvas.restore();
                }
            } else {
                if (textSelectionHelper.isInSelectionMode()) {
                    canvas.save();
                    canvas.translate(textX, textY);
                    textSelectionHelper.draw(canvas);
                    canvas.restore();
                }
                if (firstLayout != null) {
                    canvas.save();
                    canvas.translate(textX, textY);
                    drawLayout(firstLayout, canvas);
                    firstLayoutEmoji = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, firstLayoutEmoji, firstLayout);
                    AnimatedEmojiSpan.drawAnimatedEmojis(canvas, firstLayout, firstLayoutEmoji, 0, spoilers, 0, 0, 0, 1f, emojiColorFilter);
                    canvas.restore();
                }

                if (nextLinesLayouts != null) {
                    for (int i = 0; i < nextLinesLayouts.length; i++) {
                        LineInfo lineInfo = nextLinesLayouts[i];
                        canvas.save();
                        if (lineInfo.collapsedX == lineInfo.finalX) {
                            textPaint.setAlpha((int) (255 * progressToExpand));
                            canvas.translate(textX + lineInfo.finalX, textY + lineInfo.finalY);
                            drawLayout(lineInfo.staticLayout, canvas);
                            lineInfo.staticLayout.draw(canvas);
                            lineInfo.layoutEmoji = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, lineInfo.layoutEmoji, lineInfo.staticLayout);
                            AnimatedEmojiSpan.drawAnimatedEmojis(canvas, lineInfo.staticLayout, lineInfo.layoutEmoji, 0, spoilers, 0, 0, 0, progressToExpand, emojiColorFilter);
                            textPaint.setAlpha(255);
                        } else {
                            float offsetX = AndroidUtilities.lerp(lineInfo.collapsedX, lineInfo.finalX, progressToExpand);
                            float offsetY = AndroidUtilities.lerp(lineInfo.collapsedY, lineInfo.finalY, CubicBezierInterpolator.EASE_OUT.getInterpolation(progressToExpand));
                            canvas.translate(textX + offsetX, textY + offsetY);
                            //drawLayout(lineInfo.staticLayout, canvas, -offsetX, -offsetY);
                            lineInfo.staticLayout.draw(canvas);
                            lineInfo.layoutEmoji = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, this, lineInfo.layoutEmoji, lineInfo.staticLayout);
                            AnimatedEmojiSpan.drawAnimatedEmojis(canvas, lineInfo.staticLayout, lineInfo.layoutEmoji, 0, spoilers, 0, 0, 0, 1f, emojiColorFilter);
                        }
                        canvas.restore();
                    }
                }
            }

            if (showMore != null) {
                float showMoreY = this.showMoreY + StoryCaptionView.this.getScrollY();
                float alpha = 1f - Utilities.clamp(progressToExpand / 0.5f, 1f, 0);
                xRefGradinetPaint.setAlpha((int) (255 * alpha));
                xRefPaint.setAlpha((int) (255 * alpha));
                showMorePaint.setAlpha((int) (255 * alpha));
                canvas.save();
                canvas.translate(showMoreX - AndroidUtilities.dp(32), showMoreY);
                canvas.drawRect(0, 0, AndroidUtilities.dp(32), showMore.getHeight() + verticalPadding, xRefGradinetPaint);
                canvas.restore();

                canvas.drawRect(showMoreX - AndroidUtilities.dp(16), showMoreY, getMeasuredWidth(), showMoreY + showMore.getHeight() + verticalPadding, xRefPaint);
                canvas.save();
                canvas.translate(showMoreX, showMoreY);
                showMore.draw(canvas);
                canvas.restore();
            }
            canvas.restore();
        }

        AtomicReference<Layout> patchedLayout = new AtomicReference<>();

        private void drawLayout(StaticLayout staticLayout, Canvas canvas) {
            if (!spoilers.isEmpty()) {
                SpoilerEffect.renderWithRipple(this, false, Color.WHITE, 0, patchedLayout, staticLayout, spoilers, canvas, false);
            } else {
                staticLayout.draw(canvas);
            }
        }

        private StaticLayout makeTextLayout(TextPaint textPaint, CharSequence string, int width) {
            if (Build.VERSION.SDK_INT >= 24) {
                return StaticLayout.Builder.obtain(string, 0, string.length(), textPaint, width).setBreakStrategy(StaticLayout.BREAK_STRATEGY_SIMPLE).setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE).setAlignment(LocaleController.isRTL ? StaticLayoutEx.ALIGN_RIGHT() : StaticLayoutEx.ALIGN_LEFT()).build();
            } else {
                return new StaticLayout(string, textPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }
        }

        public Paint getPaint() {
            return textPaint;
        }

        @Override
        public CharSequence getText() {
            return text;
        }

        @Override
        public StaticLayout getStaticTextLayout() {
            return fullLayout;
        }

        public class LineInfo {
            private AnimatedEmojiSpan.EmojiGroupedSpans layoutEmoji;
            StaticLayout staticLayout;
            float collapsedX, collapsedY;
            float finalX, finalY;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (fullLayout == null || disableTouches) {
                return false;
            }
            boolean allowIntercept = true;
            if (showMore != null) {
                AndroidUtilities.rectTmp.set(showMoreX , showMoreY, showMoreX + showMore.getWidth(), showMoreY + showMore.getHeight());
                if (AndroidUtilities.rectTmp.contains(event.getX(), event.getY())) {
                    allowIntercept = false;
                }
            }
            boolean linkResult = false;
            if (allowIntercept && event.getAction() == MotionEvent.ACTION_DOWN || (pressedLink != null || pressedEmoji != null) && event.getAction() == MotionEvent.ACTION_UP) {
                int x = (int) (event.getX() - textX);
                int y = (int) (event.getY() - textY);
                final int line = fullLayout.getLineForVertical(y);
                final int off = fullLayout.getOffsetForHorizontal(line, x);
                final float left = fullLayout.getLineLeft(line);

                CharacterStyle touchLink = null;
                AnimatedEmojiSpan touchEmoji = null;
                if (left <= x && left + fullLayout.getLineWidth(line) >= x && y >= 0 && y <= fullLayout.getHeight()) {
                    Spannable buffer = new SpannableString(text);
                    CharacterStyle[] link = buffer.getSpans(off, off, ClickableSpan.class);
                    if (link == null || link.length == 0) {
                        link = buffer.getSpans(off, off, URLSpanMono.class);
                    }
                    if (link != null && link.length != 0) {
                        touchLink = link[0];
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            linkResult = true;
                            links.clear();
                            pressedEmoji = null;
                            pressedLink = new LinkSpanDrawable<>(link[0], null, event.getX(), event.getY());
                            pressedLink.setColor(0x6662a9e3);
                            links.addLink(pressedLink);
                            int start = buffer.getSpanStart(pressedLink.getSpan());
                            int end = buffer.getSpanEnd(pressedLink.getSpan());
                            LinkPath path = pressedLink.obtainNewPath();
                            path.setCurrentLayout(fullLayout, start, getPaddingTop());
                            fullLayout.getSelectionPath(start, end, path);

                            final LinkSpanDrawable<CharacterStyle> savedPressedLink = pressedLink;
                            postDelayed(() -> {
                                if (savedPressedLink == pressedLink && pressedLink != null && pressedLink.getSpan() instanceof URLSpan) {
                                    onLinkLongPress((URLSpan) pressedLink.getSpan(), this, () -> links.clear());
                                    pressedLink = null;
                                }
                            }, ViewConfiguration.getLongPressTimeout());
                        }
                    }
                    if (pressedLink == null && !linkResult) {
                        AnimatedEmojiSpan[] emoji = buffer.getSpans(off, off, AnimatedEmojiSpan.class);
                        if (emoji != null && emoji.length != 0) {
                            touchEmoji = emoji[0];
                            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                                linkResult = true; // links.clear();
                                pressedLink = null;
                                pressedEmoji = emoji[0];
                            }
                        }
                    }
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    links.clear();
                    if (pressedLink != null && pressedLink.getSpan() == touchLink) {
                        onLinkClick(pressedLink.getSpan(), this);
                    } else if (pressedEmoji != null && pressedEmoji == touchEmoji) {
                        onEmojiClick(pressedEmoji);
                    }
                    pressedLink = null;
                    pressedEmoji = null;
                    linkResult = true;
                }
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                clearPressedLinks();
                pressedEmoji = null;
                linkResult = true;
            }

            boolean b = linkResult || super.onTouchEvent(event);
            return b;
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            AnimatedEmojiSpan.release(this, fullLayoutEmoji);
            AnimatedEmojiSpan.release(this, firstLayoutEmoji);
            if (nextLinesLayouts != null) {
                for (int i = 0; i < nextLinesLayouts.length; i++) {
                    if (nextLinesLayouts[i] == null) {
                        continue;
                    }
                    AnimatedEmojiSpan.release(this, nextLinesLayouts[i].layoutEmoji);
                }
            }
        }

        private void clearPressedLinks() {
            links.clear();
            pressedLink = null;
            invalidate();
        }

        @Override
        public void setPressed(boolean pressed) {
            final boolean needsRefresh = pressed != isPressed();
            super.setPressed(pressed);
            if (needsRefresh) {
                invalidate();
            }
        }

        @Override
        public void setTranslationY(float translationY) {
            if (getTranslationY() != translationY) {
                super.setTranslationY(translationY);
                StoryCaptionView.this.invalidate();
            }
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            boolean allowIntercept = true;
            lastMotionX = event.getX();
            lastMotionY = event.getY();
            if (showMore != null) {
                AndroidUtilities.rectTmp.set(showMoreX , showMoreY, showMoreX + showMore.getWidth(), showMoreY + showMore.getHeight());
                if (AndroidUtilities.rectTmp.contains(event.getX(), event.getY())) {
                    allowIntercept = false;
                }
            }
            if (allowIntercept && allowClickSpoilers && clickDetector.onTouchEvent(event)) return true;
//            if (allowIntercept && (expanded || firstLayout == null)) {
//                textSelectionHelper.update(textX, textY);
//                textSelectionHelper.onTouchEvent(event);
//            }
            return super.dispatchTouchEvent(event);
        }
    }
}
