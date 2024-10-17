package org.telegram.ui.Stories;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
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

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.LoadingDrawable;
import org.telegram.ui.Components.ReplyMessageLine;
import org.telegram.ui.Components.StaticLayoutEx;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Components.URLSpanMono;
import org.telegram.ui.Components.spoilers.SpoilerEffect;
import org.telegram.ui.Components.spoilers.SpoilersClickDetector;
import org.telegram.ui.Stories.recorder.StoryEntry;

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

    private float startMotionX;
    private float startMotionY;
    private float lastMotionX;
    private float lastMotionY;

    private Method abortAnimatedScrollMethod;
    private OverScroller scroller;

    private boolean isLandscape;
    private int textHash, replytitleHash, replytextHash;
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
        setFadingEdgeLength(dp(12));
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

    public void onReplyClick(Reply reply) {

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
        final CharSequence text = textView.state[0].text;
        final CharSequence replytitle = textView.state[0].reply != null ? textView.state[0].reply.title : null;
        final CharSequence replytext = textView.state[0].reply != null ? textView.state[0].reply.text : null;

        final int textHash = text.hashCode();
        final int replytitleHash = replytitle != null ? replytitle.hashCode() : 0;
        final int replytextHash = replytext != null ? replytext.hashCode() : 0;
        final boolean isLandscape = AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y;

        if (this.textHash == textHash && this.replytitleHash == replytitleHash && this.replytextHash == replytextHash && this.isLandscape == isLandscape && this.prevHeight == height && !textView.updating) {
            return -1;
        }

        this.textHash = textHash;
        this.replytitleHash = replytitleHash;
        this.replytextHash = replytextHash;
        this.isLandscape = isLandscape;
        this.prevHeight = height;

        textView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));

        return textView.collapsedTextHeight(height);
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
        if (getScrollY() < dp(2)) {
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
        int maxHeight = Math.min(prevHeight, dp(40));
        return Utilities.clamp((getScrollY() - captionTextview.getTranslationY()) / maxHeight, 1f, 0);
    }

    public void expand() {
        expand(false);
    }

    boolean expanded;
    public void expand(boolean force) {
        if (expanded && !force) {
            return;
        }
        expanded = true;
        float fromScrollY = getScrollY();
        float fromP = captionTextview.progressToExpand;
        float toP = 1f;
        ValueAnimator valueAnimator = ValueAnimator.ofFloat(0, 1f);
        valueAnimator.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            final float toScrollY = Math.min(getMeasuredHeight() - blackoutBottomOffset - dp(64), captionContainer.getBottom() - getMeasuredHeight());
            setScrollY((int) lerp(fromScrollY, toScrollY, value));
            captionTextview.progressToExpand = lerp(fromP, toP, value);
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
            setScrollY((int) lerp(fromScrollY, toScrollY, value));
            captionTextview.progressToExpand = lerp(fromP, toP, value);
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
        if (textSelectionHelper.isInSelectionMode() && Math.abs(startMotionX - lastMotionX) < AndroidUtilities.touchSlop && Math.abs(startMotionY - lastMotionY) < AndroidUtilities.touchSlop) {
            textSelectionHelper.getOverlayView(getContext()).checkCancel(lastMotionX, lastMotionY, false);
        }
    }

    public static class Reply {
        private int currentAccount;
        public Long peerId;
        public Integer storyId;

        public Integer messageId;
        public boolean isRepostMessage;

        private boolean small = true;
        private final AnimatedFloat animatedSmall = new AnimatedFloat(0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        public final ButtonBounce bounce = new ButtonBounce(null);
        public final Drawable ripple = Theme.createRadSelectorDrawable(0x20ffffff, 0, 0);

        public CharSequence title, text;
        public boolean updateText;

        public Text titleLayout, textLayout;

        private boolean loaded, loading;
        private View view;
        public ReplyMessageLine repostLine;
        private Runnable whenLoaded;

        public void listen(View view, Runnable whenLoaded) {
            this.view = view;
            this.whenLoaded = whenLoaded;
            this.repostLine = new ReplyMessageLine(view);
            ripple.setCallback(view);
            animatedSmall.setParent(view);
            bounce.setView(view);
            load();
        }

        public void load() {
            if (!loaded && !loading && peerId != null && storyId != null && view != null) {
                loading = true;
                MessagesController.getInstance(currentAccount).getStoriesController().resolveStoryLink(peerId, storyId, fwdStoryItem -> {
                    loaded = true;
                    if (fwdStoryItem != null && fwdStoryItem.caption != null) {
                        updateText = true;
                        text = fwdStoryItem.caption;
                        small = TextUtils.isEmpty(text);
                        if (view != null) {
                            view.invalidate();
                        }
                        if (whenLoaded != null) {
                            whenLoaded.run();
                        }
                    }
                });
            }
        }

        public static Reply from(int currentAccount, TL_stories.StoryItem storyItem) {
            if (storyItem == null) {
                return null;
            }
            if (storyItem.fwd_from != null) {
                Reply reply = new Reply();
                reply.currentAccount = currentAccount;
                if (storyItem.fwd_from.from != null) {
                    long did = reply.peerId = DialogObject.getPeerDialogId(storyItem.fwd_from.from);
                    if (did >= 0) {
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                        reply.title = new SpannableStringBuilder(MessageObject.userSpan()).append(" ").append(UserObject.getUserName(user));
                    } else {
                        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
                        reply.title = new SpannableStringBuilder(ChatObject.isChannelAndNotMegaGroup(chat) ? MessageObject.channelSpan() : MessageObject.groupSpan()).append(" ").append(chat != null ? chat.title : "");
                    }
                } else if (storyItem.fwd_from.from_name != null) {
                    reply.title = new SpannableStringBuilder(MessageObject.userSpan()).append(" ").append(storyItem.fwd_from.from_name);
                }
                reply.small = true;
                if ((storyItem.fwd_from.flags & 4) != 0) {
                    reply.storyId = storyItem.fwd_from.story_id;
                }
                reply.load();
                return reply;
            }
            if (storyItem.media_areas != null) {
                TL_stories.TL_mediaAreaChannelPost postArea = null;
                for (int i = 0; i < storyItem.media_areas.size(); ++i) {
                    if (storyItem.media_areas.get(i) instanceof TL_stories.TL_mediaAreaChannelPost) {
                        postArea = (TL_stories.TL_mediaAreaChannelPost) storyItem.media_areas.get(i);
                    }
                }
                if (postArea != null) {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(postArea.channel_id);
                    if (chat != null) {
                        Reply reply = new Reply();
                        reply.peerId = -chat.id;
                        reply.isRepostMessage = true;
                        reply.currentAccount = currentAccount;
                        reply.small = true;
                        reply.messageId = postArea.msg_id;
                        reply.title = new SpannableStringBuilder(ChatObject.isChannelAndNotMegaGroup(chat) ? MessageObject.channelSpan() : MessageObject.groupSpan()).append(" ").append(chat.title);
                        return reply;
                    }
                }
            }
            return null;
        }

        public static Reply from(StoriesController.UploadingStory uploadingStory) {
            if (uploadingStory == null || uploadingStory.entry == null) {
                return null;
            }
            if (uploadingStory.entry.isRepost) {
                Reply reply = new Reply();
                reply.title = uploadingStory.entry.repostPeerName;
                reply.text = uploadingStory.entry.repostCaption;
                reply.small = TextUtils.isEmpty(reply.text);
                return reply;
            }
            if (uploadingStory.entry.isRepostMessage && uploadingStory.entry.messageObjects != null && uploadingStory.entry.messageObjects.size() > 0) {
                MessageObject messageObject = uploadingStory.entry.messageObjects.get(0);
                final long dialogId = StoryEntry.getRepostDialogId(messageObject);
                if (dialogId < 0) {
                    TLRPC.Chat chat = MessagesController.getInstance(messageObject.currentAccount).getChat(-dialogId);
                    if (chat != null) {
                        Reply reply = new Reply();
                        reply.peerId = dialogId;
                        reply.isRepostMessage = true;
                        reply.currentAccount = messageObject.currentAccount;
                        reply.small = true;
                        reply.messageId = StoryEntry.getRepostMessageId(messageObject);
                        reply.title = new SpannableStringBuilder(ChatObject.isChannelAndNotMegaGroup(chat) ? MessageObject.channelSpan() : MessageObject.groupSpan()).append(" ").append(chat.title);
                        return reply;
                    }
                }
            }
            return null;
        }

        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Path clipRipple = new Path();
        public final RectF bounds = new RectF();
        private int width;

        public int height() {
            return small ? dp(22) : dp(42);
        }

        public int width() {
            return width;
        }

        public void setPressed(boolean pressed, float x, float y) {
            bounce.setPressed(pressed);
            ripple.setState(pressed ? new int[] {android.R.attr.state_pressed, android.R.attr.state_enabled} : new int[] {});
            if (pressed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ripple.setHotspot(x, y);
            }
        }

        public void draw(Canvas canvas, float width) {
            if (titleLayout == null) {
                titleLayout = new Text(title == null ? "" : title, 14, AndroidUtilities.bold());
            }
            if (textLayout == null || updateText) {
                textLayout = new Text(text == null ? "" : text, 14);
            }

            final float smallT = animatedSmall.set(small);

            backgroundPaint.setColor(0x40000000);
            final int boxwidth = this.width = (int) Math.min(width, lerp(dp(20), dp(18), smallT) + Math.max(titleLayout.getCurrentWidth(), textLayout.getCurrentWidth()));
            final int boxheight = lerp(dp(42), dp(22), smallT);
            bounds.set(0, 0, boxwidth, boxheight);

            canvas.save();
            final float s = bounce.getScale(.02f);
            canvas.scale(s, s, bounds.centerX(), bounds.centerY());
            final float r = lerp(dp(5), dp(11), smallT);
            canvas.drawRoundRect(bounds, r, r, backgroundPaint);

            canvas.save();
            clipRipple.rewind();
            clipRipple.addRoundRect(bounds, r, r, Path.Direction.CW);
            canvas.clipPath(clipRipple);
            ripple.setBounds(0, 0, boxwidth, boxheight);
            ripple.draw(canvas);
            canvas.restore();

            canvas.save();
            canvas.clipRect(0, 0, dp(3), dp(42));
            AndroidUtilities.rectTmp.set(0, 0, dp(10), dp(42));
            linePaint.setColor(0xFFFFFFFF);
            linePaint.setAlpha((int) (0xFF * (1f - smallT)));
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(5), dp(5), linePaint);
            canvas.restore();
            int ellipsize = boxwidth - dp(20);
            if (boxwidth < width) {
                ellipsize = (int) Math.min(ellipsize + dp(12), width - dp(20));
            }
            titleLayout.ellipsize(ellipsize).draw(canvas, lerp(dp(10), dp(7), smallT), lerp(dp(12), dp(11), smallT), 0xFFFFFFFF, 1f);
            textLayout.ellipsize(ellipsize).draw(canvas, dp(10), dp(30), 0xFFFFFFFF, 1f - smallT);
            canvas.restore();
        }
    }

    public class StoryCaptionTextView extends View implements TextSelectionHelper.SimpleSelectabeleView {

        private final PorterDuffColorFilter emojiColorFilter;

        boolean shouldCollapse;
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        TextPaint showMorePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint xRefPaint = new Paint();
        private final Paint xRefGradinetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        float showMoreY;
        float showMoreX;

        public int collapsedTextHeight(int height) {
            return lerp(state[0].collapsedTextHeight(height), state[1] == null ? 0 : state[1].collapsedTextHeight(height), updateT);
        }

        public class TextState {
            private LinkSpanDrawable<CharacterStyle> pressedLink;
            private AnimatedEmojiSpan pressedEmoji;
            private final LinkSpanDrawable.LinkCollector links = new LinkSpanDrawable.LinkCollector(StoryCaptionTextView.this);

            private AnimatedEmojiSpan.EmojiGroupedSpans fullLayoutEmoji;
            StaticLayout fullLayout;
            private AnimatedEmojiSpan.EmojiGroupedSpans firstLayoutEmoji;
            StaticLayout firstLayout;
            LineInfo[] nextLinesLayouts;

            protected final List<SpoilerEffect> spoilers = new ArrayList<>();
            private final Stack<SpoilerEffect> spoilersPool = new Stack<>();
            private final SpoilersClickDetector clickDetector;

            int textHeight;
            CharSequence text = "";
            public Reply reply;

            public boolean translating;
            public final AnimatedFloat translateT = new AnimatedFloat(StoryCaptionView.this, 0, 400, CubicBezierInterpolator.EASE_OUT_QUINT);
            private final LoadingDrawable loadingDrawable;

            private Path loadingPath = new Path();

            public int collapsedTextHeight(int height) {
                final int replyOffset = reply != null ? reply.height() + dp(8) : 0;
                if (fullLayout == null) {
                    return height - (verticalPadding * 2 + textHeight);
                }
                final Layout layout = fullLayout;
                final int lineCount = layout.getLineCount();
                if (!shouldCollapse) {
                    return height - (verticalPadding * 2 + textHeight);
                }
                int i = Math.min(3, lineCount);
                final int lineHeight = textPaint.getFontMetricsInt(null);
                return height - lineHeight * (i + 1) - replyOffset;
            }

            public TextState() {
                clickDetector = new SpoilersClickDetector(StoryCaptionTextView.this, spoilers, (eff, x, y) -> {
                    if (isSpoilersRevealed) return;

                    eff.setOnRippleEndCallback(() -> post(() -> {
                        isSpoilersRevealed = true;
                        // invalidateSpoilers();
                    }));

                    float rad = (float) Math.sqrt(Math.pow(getWidth(), 2) + Math.pow(getHeight(), 2));
                    for (SpoilerEffect ef : spoilers)
                        ef.startRipple(x, y, rad);
                });

                loadingDrawable = new LoadingDrawable();
                loadingDrawable.usePath(loadingPath);
                loadingDrawable.setRadiiDp(4);
                loadingDrawable.setColors(
                        Theme.multAlpha(Color.WHITE, .3f),
                        Theme.multAlpha(Color.WHITE, .1f),
                        Theme.multAlpha(Color.WHITE, .2f),
                        Theme.multAlpha(Color.WHITE, .7f)
                );
                loadingDrawable.setCallback(StoryCaptionTextView.this);
            }

            public void setup(CharSequence text, Reply reply) {
                this.text = text;
                this.reply = reply;
                if (this.reply != null) {
                    this.reply.listen(StoryCaptionTextView.this, () -> {
                        sizeCached = 0;
                        requestLayout();
                        StoryCaptionView.this.updateTopMargin();
                        StoryCaptionView.this.requestLayout();
                    });
                }
                sizeCached = 0;
                requestLayout();
            }

            public void measure(int width) {
                if (TextUtils.isEmpty(text)) {
                    fullLayout = null;
                    textHeight = 0;
                    if (reply != null) {
                        textHeight += reply.height() + dp(4);
                    }
                    if (this == state[0]) {
                        showMore = null;
                    }
                    firstLayout = null;
                    spoilersPool.addAll(spoilers);
                    spoilers.clear();
                    return;
                }
                fullLayout = makeTextLayout(textPaint, text, width);
                textHeight = fullLayout.getHeight();
                int replyOffset = 0;
                if (reply != null) {
                    textHeight += (replyOffset = reply.height() + dp(8));
                }
                float space = textPaint.measureText(" ");
                shouldCollapse = fullLayout.getLineCount() > 3;
                if (shouldCollapse && fullLayout.getLineCount() == 4) {
                    int start = fullLayout.getLineStart(2);
                    int end = fullLayout.getLineEnd(2);
                    if (TextUtils.getTrimmedLength(text.subSequence(start, end)) == 0) {
                        shouldCollapse = false;
                    }
                }
                if (shouldCollapse) {
                    float collapsedY = fullLayout.getLineTop(2) + fullLayout.getTopPadding();
                    if (this == state[0]) {
                        String showMoreText = LocaleController.getString(R.string.ShowMore);
                        showMore = makeTextLayout(showMorePaint, showMoreText, width);

                        showMoreY = verticalPadding + replyOffset + collapsedY - AndroidUtilities.dpf2(0.3f);
                        showMoreX = width + horizontalPadding - showMorePaint.measureText(showMoreText);
                    }

                    firstLayout = makeTextLayout(textPaint, text.subSequence(0, fullLayout.getLineEnd(2)), width);
                    spoilersPool.addAll(spoilers);
                    spoilers.clear();
                    SpoilerEffect.addSpoilers(StoryCaptionView.this, fullLayout, spoilersPool, spoilers);

                    float x = fullLayout.getLineRight(2) + space;
                    if (nextLinesLayouts != null) {
                        for (int i = 0; i < nextLinesLayouts.length; i++) {
                            if (nextLinesLayouts[i] == null) {
                                continue;
                            }
                            AnimatedEmojiSpan.release(StoryCaptionView.this, nextLinesLayouts[i].layoutEmoji);
                        }
                    }
                    nextLinesLayouts = new LineInfo[fullLayout.getLineCount() - 3];

                    if (spoilers.isEmpty()) {
                        for (int line = 3; line < fullLayout.getLineCount(); ++line) {
                            int s = fullLayout.getLineStart(line), e = fullLayout.getLineEnd(line);
                            CharSequence sequence = text.subSequence(Math.min(s, e), Math.max(s, e));
                            if (TextUtils.isEmpty(sequence)) {
                                nextLinesLayouts[line - 3] = null;
                                continue;
                            }
                            final StaticLayout layout = makeTextLayout(textPaint, sequence, width);
                            LineInfo lineInfo = new LineInfo();
                            nextLinesLayouts[line - 3] = lineInfo;
                            lineInfo.staticLayout = layout;
                            lineInfo.finalX = fullLayout.getLineLeft(line);
                            lineInfo.finalY = fullLayout.getLineTop(line) + fullLayout.getTopPadding();
                            if (x < showMoreX - dp(16)) {
                                lineInfo.collapsedY = collapsedY;
                                lineInfo.collapsedX = x;
                                x += Math.abs(layout.getLineRight(0) - layout.getLineLeft(0)) + space;
                            } else {
                                lineInfo.collapsedY = lineInfo.finalY;
                                lineInfo.collapsedX = lineInfo.finalX;
                            }
                        }
                    }
                } else {
                    if (this == state[0]) {
                        showMore = null;
                    }
                    firstLayout = null;
                    spoilersPool.addAll(spoilers);
                    spoilers.clear();
                    SpoilerEffect.addSpoilers(StoryCaptionTextView.this, fullLayout, spoilersPool, spoilers);
                }
                clickDetector.setAdditionalOffsets(horizontalPadding, verticalPadding);
            }

            public void draw(Canvas canvas, float alpha) {
                final float loadingT = this.translateT.set(translating);
                if (alpha <= 0) {
                    return;
                }

                alpha = lerp(alpha, alpha * .7f, loadingT);
                if (alpha >= 1) {
                    drawInternal(canvas, loadingT);
                } else {
                    canvas.saveLayerAlpha(0, 0, StoryCaptionView.this.getWidth(), StoryCaptionView.this.getHeight(), (int) (0xFF * alpha), Canvas.ALL_SAVE_FLAG);
                    drawInternal(canvas, loadingT);
                    canvas.restore();
                }

                if (loadingT > 0 || translating) {
                    loadingDrawable.setAlpha((int) (0xFF * loadingT * alpha));
                    loadingDrawable.draw(canvas);
                    StoryCaptionTextView.this.invalidate();
                }
            }

            private void putLayoutRects(Layout layout, float tx, float ty) {
                float t = 0;
                for (int i = 0; i < layout.getLineCount(); ++i) {
                    float l = layout.getLineLeft(i) - horizontalPadding / 3f;
                    float r = layout.getLineRight(i) + horizontalPadding / 3f;
                    if (i == 0) {
                        t = layout.getLineTop(i) - verticalPadding / 3f;
                    }
                    float b = layout.getLineBottom(i);
                    if (i >= layout.getLineCount() - 1) {
                        b += verticalPadding / 3f;
                    }
                    loadingPath.addRect(tx + l, ty + t, tx + r, ty + b, Path.Direction.CW);
                    t = b;
                }
            }

            private void drawInternal(Canvas canvas, float loadingT) {
                int replyOffset = 0;
                if (reply != null) {
                    canvas.save();
                    canvas.translate(horizontalPadding, verticalPadding);
                    reply.draw(canvas, getWidth() - horizontalPadding - horizontalPadding);
                    replyOffset = reply.height() + dp(8);
                    canvas.restore();
                }

                canvas.save();
                canvas.translate(horizontalPadding, verticalPadding + replyOffset);
                if (links.draw(canvas)) {
                    invalidate();
                }
                canvas.restore();

                final boolean drawLoading = loadingT > 0;
                loadingPath.rewind();

                if (!spoilers.isEmpty() || firstLayout == null) {
                    if (fullLayout != null) {
                        canvas.save();
                        canvas.translate(horizontalPadding, verticalPadding + replyOffset);
                        if (textSelectionHelper.isInSelectionMode()) {
                            textSelectionHelper.draw(canvas);
                        }
                        drawLayout(fullLayout, canvas, spoilers);
                        fullLayoutEmoji = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, StoryCaptionTextView.this, fullLayoutEmoji, fullLayout);
                        AnimatedEmojiSpan.drawAnimatedEmojis(canvas, fullLayout, fullLayoutEmoji, 0, spoilers, 0, 0, 0, 1f, emojiColorFilter);
                        canvas.restore();

                        if (drawLoading) {
                            putLayoutRects(fullLayout, horizontalPadding, verticalPadding + replyOffset);
                        }
                    }
                } else {
                    if (textSelectionHelper.isInSelectionMode()) {
                        canvas.save();
                        canvas.translate(horizontalPadding, verticalPadding + replyOffset);
                        textSelectionHelper.draw(canvas);
                        canvas.restore();
                    }
                    if (firstLayout != null) {
                        canvas.save();
                        canvas.translate(horizontalPadding, verticalPadding + replyOffset);
                        drawLayout(firstLayout, canvas, spoilers);
                        firstLayoutEmoji = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, StoryCaptionTextView.this, firstLayoutEmoji, firstLayout);
                        AnimatedEmojiSpan.drawAnimatedEmojis(canvas, firstLayout, firstLayoutEmoji, 0, spoilers, 0, 0, 0, 1f, emojiColorFilter);
                        canvas.restore();

                        if (drawLoading) {
                            putLayoutRects(firstLayout, horizontalPadding, verticalPadding + replyOffset);
                        }
                    }

                    if (nextLinesLayouts != null) {
                        for (int i = 0; i < nextLinesLayouts.length; i++) {
                            LineInfo lineInfo = nextLinesLayouts[i];
                            if (lineInfo == null) {
                                continue;
                            }
                            canvas.save();
                            if (lineInfo.collapsedX == lineInfo.finalX) {
                                if (progressToExpand == 0) {
                                    continue;
                                }
                                canvas.translate(horizontalPadding + lineInfo.finalX, verticalPadding + replyOffset + lineInfo.finalY);
                                canvas.saveLayerAlpha(0, 0, lineInfo.staticLayout.getWidth(), lineInfo.staticLayout.getHeight(), (int) (255 * progressToExpand), Canvas.ALL_SAVE_FLAG);
                                drawLayout(lineInfo.staticLayout, canvas, spoilers);

                                if (drawLoading) {
                                    putLayoutRects(lineInfo.staticLayout, horizontalPadding + lineInfo.finalX, verticalPadding + replyOffset + lineInfo.finalY);
                                }

                                lineInfo.staticLayout.draw(canvas);
                                lineInfo.layoutEmoji = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, StoryCaptionTextView.this, lineInfo.layoutEmoji, lineInfo.staticLayout);
                                AnimatedEmojiSpan.drawAnimatedEmojis(canvas, lineInfo.staticLayout, lineInfo.layoutEmoji, 0, spoilers, 0, 0, 0, progressToExpand, emojiColorFilter);
                                canvas.restore();
                                //textPaint.setAlpha(255);
                            } else {
                                float offsetX = lerp(lineInfo.collapsedX, lineInfo.finalX, progressToExpand);
                                float offsetY = lerp(lineInfo.collapsedY, lineInfo.finalY, CubicBezierInterpolator.EASE_OUT.getInterpolation(progressToExpand));
                                canvas.translate(horizontalPadding + offsetX, verticalPadding + replyOffset + offsetY);
                                //drawLayout(lineInfo.staticLayout, canvas, -offsetX, -offsetY);
                                if (drawLoading) {
                                    putLayoutRects(lineInfo.staticLayout, horizontalPadding + offsetX, verticalPadding + replyOffset + offsetY);
                                }
                                lineInfo.staticLayout.draw(canvas);
                                lineInfo.layoutEmoji = AnimatedEmojiSpan.update(AnimatedEmojiDrawable.CACHE_TYPE_MESSAGES, StoryCaptionTextView.this, lineInfo.layoutEmoji, lineInfo.staticLayout);
                                AnimatedEmojiSpan.drawAnimatedEmojis(canvas, lineInfo.staticLayout, lineInfo.layoutEmoji, 0, spoilers, 0, 0, 0, 1f, emojiColorFilter);
                            }
                            canvas.restore();
                        }
                    }
                }
            }

            final AtomicReference<Layout> patchedLayout = new AtomicReference<>();

            private void drawLayout(StaticLayout staticLayout, Canvas canvas, List<SpoilerEffect> spoilers) {
                if (!spoilers.isEmpty()) {
                    SpoilerEffect.renderWithRipple(StoryCaptionTextView.this, false, Color.WHITE, 0, patchedLayout, 0, staticLayout, spoilers, canvas, false);
                } else {
                    staticLayout.draw(canvas);
                }
            }

            public boolean touch(MotionEvent event) {
                boolean allowIntercept = true;
                if (showMore != null) {
                    AndroidUtilities.rectTmp.set(showMoreX , showMoreY, showMoreX + showMore.getWidth(), showMoreY + showMore.getHeight());
                    if (AndroidUtilities.rectTmp.contains(event.getX(), event.getY())) {
                        allowIntercept = false;
                    }
                }
                boolean linkResult = false;
                if (allowIntercept && event.getAction() == MotionEvent.ACTION_DOWN || (pressedLink != null || pressedEmoji != null) && event.getAction() == MotionEvent.ACTION_UP) {
                    final int replyOffset = reply == null ? 0 : reply.height() + dp(8);
                    int x = (int) (event.getX() - horizontalPadding);
                    int y = (int) (event.getY() - verticalPadding - replyOffset);
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
                                pressedLink.setColor(Theme.multAlpha(Color.WHITE, 0.2f));
                                links.addLink(pressedLink);
                                int start = buffer.getSpanStart(pressedLink.getSpan());
                                int end = buffer.getSpanEnd(pressedLink.getSpan());
                                LinkPath path = pressedLink.obtainNewPath();
                                path.setCurrentLayout(fullLayout, start, getPaddingTop());
                                fullLayout.getSelectionPath(start, end, path);

                                final LinkSpanDrawable<CharacterStyle> savedPressedLink = pressedLink;
                                textSelectionHelper.clear();
                                postDelayed(() -> {

                                    if (savedPressedLink == pressedLink && pressedLink != null && pressedLink.getSpan() instanceof URLSpan) {
                                        onLinkLongPress((URLSpan) pressedLink.getSpan(), StoryCaptionTextView.this, links::clear);
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
                            onLinkClick(pressedLink.getSpan(), StoryCaptionView.this);
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
                return linkResult;
            }

            public void detach() {
                AnimatedEmojiSpan.release(StoryCaptionTextView.this, fullLayoutEmoji);
                AnimatedEmojiSpan.release(StoryCaptionTextView.this, firstLayoutEmoji);
                if (nextLinesLayouts != null) {
                    for (int i = 0; i < nextLinesLayouts.length; i++) {
                        if (nextLinesLayouts[i] == null) {
                            continue;
                        }
                        AnimatedEmojiSpan.release(StoryCaptionTextView.this, nextLinesLayouts[i].layoutEmoji);
                    }
                }
            }
        }

        @Override
        public CharSequence getText() {
            return state[0].text;
        }

        @Override
        public Layout getStaticTextLayout() {
            return state[0].fullLayout;
        }

        TextState[] state = new TextState[2];

        int sizeCached = 0;
        StaticLayout showMore;

        float progressToExpand;

        //spoilers
        private boolean isSpoilersRevealed;
        private Path path = new Path();
        public boolean allowClickSpoilers = true;

        int horizontalPadding;
        int verticalPadding;

        public StoryCaptionTextView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            state[0] = new TextState();
            state[1] = null;

            textPaint.setColor(Color.WHITE);
            textPaint.linkColor = Color.WHITE;//Theme.getColor(Theme.key_chat_messageLinkIn, resourcesProvider);
            textPaint.setTextSize(dp(15));

            showMorePaint.setColor(Color.WHITE);
            showMorePaint.setTypeface(AndroidUtilities.bold());
            showMorePaint.setTextSize(dp(16));

            xRefPaint.setColor(0xff000000);
            xRefPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

            xRefGradinetPaint.setShader(new LinearGradient(0, 0, dp(16), 0, new int[]{0, 0xffffffff}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            xRefGradinetPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));

            emojiColorFilter = new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        }

        @Override
        protected boolean verifyDrawable(@NonNull Drawable who) {
            if (state[0] != null && (state[0].loadingDrawable == who || state[0].reply != null && state[0].reply.ripple == who)) {
                return true;
            }
            if (state[1] != null && (state[1].loadingDrawable == who || state[1].reply != null && state[1].reply.ripple == who)) {
                return true;
            }
            return super.verifyDrawable(who);
        }

        public void setText(CharSequence text, Reply reply, boolean translating, boolean animated) {
            if (text == null) {
                text = "";
            }
            if (MediaDataController.stringsEqual(state[0].text, text) && state[0].reply == reply) {
                state[0].translating = translating;
                invalidate();
                return;
            }
            isSpoilersRevealed = false;
            if (updateAnimator != null) {
                updateAnimator.cancel();
            }
            updating = false;
            if (animated) {
                if (state[1] == null) {
                    state[1] = new TextState();
                }
                state[1].setup(state[0].text, state[0].reply);
                state[1].translating = state[0].translating;
                state[1].translateT.set(state[0].translateT.get(), true);
                state[0].setup(text, reply);
                state[0].translating = translating;
                state[0].translateT.set(0, true);
                updateT = 1;
                animateUpdate();
            } else {
                state[0].setup(text, reply);
                state[0].translating = translating;
                invalidate();
                updateT = 0;
            }
        }

        public float updateT;
        public boolean updating = false;
        private ValueAnimator updateAnimator;
        public void animateUpdate() {
            if (updateAnimator != null) {
                updateAnimator.cancel();
            }
            updating = true;
            updateAnimator = ValueAnimator.ofFloat(updateT, 0);
            updateAnimator.addUpdateListener(anm -> {
                updateT = (float) anm.getAnimatedValue();
                invalidate();
                StoryCaptionTextView.this.requestLayout();
                StoryCaptionView.this.requestLayout();
            });
            updateAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    updating = false;
                    updateT = 0;
                    invalidate();
                    StoryCaptionTextView.this.requestLayout();
                    StoryCaptionView.this.requestLayout();
                }
            });
            updateAnimator.setDuration(180);
            updateAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            updateAnimator.start();
        }

        @SuppressLint("DrawAllocation")
        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int size = widthMeasureSpec + heightMeasureSpec << 16;
            horizontalPadding = dp(16);
            verticalPadding = dp(8);
            if (sizeCached != size) {
                sizeCached = size;
                int width = Math.max(0, MeasureSpec.getSize(widthMeasureSpec) - horizontalPadding * 2);
                state[0].measure(width);
                if (state[1] != null) {
                    state[1].measure(width);
                }
            }
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(verticalPadding * 2 + lerp(state[0].textHeight, state[1] == null ? 0 : state[1].textHeight, updateT), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (showMore != null) {
                canvas.saveLayerAlpha(0, 0, getMeasuredWidth(), getMeasuredHeight(), 255, Canvas.ALL_SAVE_FLAG);
            } else {
                canvas.save();
            }

            state[0].draw(canvas, 1f - updateT);
            if (state[1] != null) {
                state[1].draw(canvas, updateT);
            }

            if (showMore != null) {
                float showMoreY = this.showMoreY + StoryCaptionView.this.getScrollY();
                float alpha = 1f - Utilities.clamp(progressToExpand / 0.5f, 1f, 0);
                xRefGradinetPaint.setAlpha((int) (255 * alpha));
                xRefPaint.setAlpha((int) (255 * alpha));
                showMorePaint.setAlpha((int) (255 * alpha));
                canvas.save();
                canvas.translate(showMoreX - dp(32), showMoreY);
                canvas.drawRect(0, 0, dp(32), showMore.getHeight() + verticalPadding, xRefGradinetPaint);
                canvas.restore();

                canvas.drawRect(showMoreX - dp(16), showMoreY, getMeasuredWidth(), showMoreY + showMore.getHeight() + verticalPadding, xRefPaint);
                canvas.save();
                canvas.translate(showMoreX, showMoreY);
                showMore.draw(canvas);
                canvas.restore();
            }

            canvas.restore();
        }

        private StaticLayout makeTextLayout(TextPaint textPaint, CharSequence string, int width) {
            if (Build.VERSION.SDK_INT >= 24) {
                return StaticLayout.Builder.obtain(string, 0, string.length(), textPaint, width)
                        .setBreakStrategy(StaticLayout.BREAK_STRATEGY_SIMPLE)
                        .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE)
                        .setAlignment(LocaleController.isRTL ? StaticLayoutEx.ALIGN_RIGHT() : StaticLayoutEx.ALIGN_LEFT())
                        .build();
            } else {
                return new StaticLayout(string, textPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }
        }

        public Paint getPaint() {
            return textPaint;
        }

        public class LineInfo {
            public AnimatedEmojiSpan.EmojiGroupedSpans layoutEmoji;
            StaticLayout staticLayout;
            float collapsedX, collapsedY;
            float finalX, finalY;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (disableTouches) {
                return false;
            }
            if (state == null || state[0].fullLayout == null) {
                return false;
            }
            boolean linkResult = state[0].touch(event);
            boolean b = linkResult || super.onTouchEvent(event);
            return b;
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            state[0].detach();
        }

        private void clearPressedLinks() {
            state[0].links.clear();
            state[0].pressedLink = null;
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
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                startMotionX = event.getX();
                startMotionY = event.getY();
            }
            lastMotionX = event.getX();
            lastMotionY = event.getY();
            if (showMore != null) {
                AndroidUtilities.rectTmp.set(showMoreX, showMoreY, showMoreX + showMore.getWidth(), showMoreY + showMore.getHeight());
                if (AndroidUtilities.rectTmp.contains(event.getX(), event.getY())) {
                    allowIntercept = false;
                }
            }
            boolean r = false;
            if (state[0] != null && state[0].reply != null) {
                AndroidUtilities.rectTmp.set(horizontalPadding, verticalPadding, horizontalPadding + state[0].reply.width(), verticalPadding + state[0].reply.height());
                final boolean hit = AndroidUtilities.rectTmp.contains(event.getX(), event.getY());
                if (hit) {
                    allowIntercept = false;
                }
                if (event.getAction() == MotionEvent.ACTION_DOWN && hit) {
                    state[0].reply.setPressed(true, event.getX(), event.getY());
                } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (event.getAction() == MotionEvent.ACTION_UP && state[0].reply.bounce.isPressed()) {
                        onReplyClick(state[0].reply);
                    }
                    state[0].reply.setPressed(false, event.getX(), event.getY());
                }
                r = hit;
            }
            if (allowIntercept && (expanded || state[0].firstLayout == null)) {
                final int replyOffset = state[0] != null && state[0].reply != null ? state[0].reply.height() + dp(8) : 0;
                textSelectionHelper.update(horizontalPadding, verticalPadding + replyOffset);
                textSelectionHelper.onTouchEvent(event);
            }
            if (!textSelectionHelper.isInSelectionMode() && allowIntercept && allowClickSpoilers && state[0].clickDetector.onTouchEvent(event)) {
                getParent().requestDisallowInterceptTouchEvent(true);
                textSelectionHelper.clear();
                return true;
            }
            return super.dispatchTouchEvent(event) || r;
        }
    }
}
