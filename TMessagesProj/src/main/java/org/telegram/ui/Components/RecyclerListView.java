/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.SparseIntArray;
import android.util.StateSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewPropertyAnimator;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import androidx.annotation.IntDef;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.Theme;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;

@SuppressWarnings("JavaReflectionMemberAccess")
public class RecyclerListView extends RecyclerView {
    public final static int SECTIONS_TYPE_SIMPLE = 0,
            SECTIONS_TYPE_STICKY_HEADERS = 1,
            SECTIONS_TYPE_DATE = 2,
            SECTIONS_TYPE_FAST_SCROLL_ONLY = 3;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({
            SECTIONS_TYPE_SIMPLE,
            SECTIONS_TYPE_STICKY_HEADERS,
            SECTIONS_TYPE_DATE,
            SECTIONS_TYPE_FAST_SCROLL_ONLY
    })
    public @interface SectionsType {}

    public final static int EMPTY_VIEW_ANIMATION_TYPE_ALPHA_SCALE = 1;
    public final static int EMPTY_VIEW_ANIMATION_TYPE_ALPHA = 0;

    private OnItemClickListener onItemClickListener;
    private OnItemClickListenerExtended onItemClickListenerExtended;
    private OnItemLongClickListener onItemLongClickListener;
    private OnItemLongClickListenerExtended onItemLongClickListenerExtended;
    private boolean longPressCalled;
    private OnScrollListener onScrollListener;
    private OnInterceptTouchListener onInterceptTouchListener;
    private View emptyView;
    private FrameLayout overlayContainer;
    private Runnable selectChildRunnable;
    private FastScroll fastScroll;
    private SectionsAdapter sectionsAdapter;
    public boolean useLayoutPositionOnClick;

    private boolean isHidden;

    private boolean disableHighlightState;

    private boolean allowItemsInteractionDuringAnimation = true;

    private Drawable pinnedHeaderShadowDrawable;
    private float pinnedHeaderShadowAlpha;
    private float pinnedHeaderShadowTargetAlpha;
    private long lastAlphaAnimationTime;

    private ArrayList<View> headers;
    private ArrayList<View> headersCache;
    private View pinnedHeader;
    private int currentFirst = -1;
    private int currentVisible = -1;
    private int startSection;
    private int sectionsCount;
    private int sectionOffset;
    private boolean allowStopHeaveOperations;

    @SectionsType
    private int sectionsType;

    private boolean hideIfEmpty = true;

    private boolean drawSelectorBehind;
    private int selectorType = 2;
    protected Drawable selectorDrawable;
    protected int selectorPosition;
    protected View selectorView;
    protected android.graphics.Rect selectorRect = new android.graphics.Rect();
    private boolean isChildViewEnabled;

    private boolean selfOnLayout;

    public boolean scrollingByUser;
    public boolean scrolledByUserOnce;

    private GestureDetectorFixDoubleTap gestureDetector;
    private View currentChildView;
    private int currentChildPosition;
    private boolean interceptedByChild;
    private boolean wasPressed;
    private boolean disallowInterceptTouchEvents;
    private boolean instantClick;
    private Runnable clickRunnable;
    private boolean ignoreOnScroll;

    private boolean scrollEnabled = true;

    private IntReturnCallback pendingHighlightPosition;
    private Runnable removeHighlighSelectionRunnable;

    private static int[] attributes;
    private static boolean gotAttributes;

    private boolean hiddenByEmptyView;
    public boolean fastScrollAnimationRunning;
    private boolean animateEmptyView;
    private int emptyViewAnimationType;
    private int selectorRadius;
    private int topBottomSelectorRadius;
    private int touchSlop;

    boolean useRelativePositions;
    boolean multiSelectionGesture;
    boolean multiSelectionGestureStarted;
    int startSelectionFrom;
    int currentSelectedPosition;
    onMultiSelectionChanged multiSelectionListener;
    boolean multiselectScrollRunning;
    boolean multiselectScrollToTop;
    float lastX = Float.MAX_VALUE;
    float lastY = Float.MAX_VALUE;
    int[] listPaddings;
    HashSet<Integer> selectedPositions;
    RecyclerItemsEnterAnimator itemsEnterAnimator;

    protected Consumer<Canvas> selectorTransformer;

    protected final Theme.ResourcesProvider resourcesProvider;

    private boolean accessibilityEnabled = true;

    private final static Method initializeScrollbars;

    static {
        Method notSoFinalInitializeScrollbars;
        try {
            notSoFinalInitializeScrollbars = android.view.View.class.getDeclaredMethod("initializeScrollbars", TypedArray.class);
        } catch (Exception ignored) {
            notSoFinalInitializeScrollbars = null;
        }
        initializeScrollbars = notSoFinalInitializeScrollbars;
    }

    private AccessibilityDelegate accessibilityDelegate = new AccessibilityDelegate() {
        @Override
        public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            if (host.isEnabled()) {
                info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
            }
        }
    };

    public void setSelectorTransformer(Consumer<Canvas> transformer) {
        selectorTransformer = transformer;
    }

    public FastScroll getFastScroll() {
        return fastScroll;
    }

    public interface OnItemClickListener {
        void onItemClick(View view, int position);
    }

    public interface OnItemClickListenerExtended {

        default boolean hasDoubleTap(View view, int position) {
            return false;
        }

        default void onDoubleTap(View view, int position, float x, float y) {
        }

        void onItemClick(View view, int position, float x, float y);
    }

    public interface OnItemLongClickListener {
        boolean onItemClick(View view, int position);
    }

    public interface OnItemLongClickListenerExtended {
        boolean onItemClick(View view, int position, float x, float y);

        default void onMove(float dx, float dy) {
        }

        default void onLongClickRelease() {
        }
    }

    public interface OnInterceptTouchListener {
        boolean onInterceptTouchEvent(MotionEvent event);
    }

    public abstract static class SelectionAdapter extends Adapter {
        public abstract boolean isEnabled(ViewHolder holder);

        public int getSelectionBottomPadding(View view) {
            return 0;
        }
    }

    public abstract static class FastScrollAdapter extends SelectionAdapter {
        public abstract String getLetter(int position);

        public abstract void getPositionForScrollProgress(RecyclerListView listView, float progress, int[] position);

        public void onStartFastScroll() {

        }

        public void onFinishFastScroll(RecyclerListView listView) {

        }

        public int getTotalItemsCount() {
            return getItemCount();
        }

        public float getScrollProgress(RecyclerListView listView) {
            return listView.computeVerticalScrollOffset() / ((float) getTotalItemsCount() * listView.getChildAt(0).getMeasuredHeight() - listView.getMeasuredHeight());
        }

        public boolean fastScrollIsVisible(RecyclerListView listView) {
            return true;
        }

        public void onFastScrollSingleTap() {

        }
    }

    public interface IntReturnCallback {
        int run();
    }

    public abstract static class SectionsAdapter extends FastScrollAdapter {

        private SparseIntArray sectionPositionCache;
        private SparseIntArray sectionCache;
        private SparseIntArray sectionCountCache;
        private int sectionCount;
        private int count;

        private ArrayList<Integer> hashes = new ArrayList<>();

        private void cleanupCache() {
            if (sectionCache == null) {
                sectionCache = new SparseIntArray();
                sectionPositionCache = new SparseIntArray();
                sectionCountCache = new SparseIntArray();
            } else {
                sectionCache.clear();
                sectionPositionCache.clear();
                sectionCountCache.clear();
            }
            count = -1;
            sectionCount = -1;
        }

        public void notifySectionsChanged() {
            cleanupCache();
        }

        public SectionsAdapter() {
            super();
            cleanupCache();
        }

        @Override
        public void notifyDataSetChanged() {
            update(false);
        }

        @Override
        public boolean isEnabled(ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return isEnabled(holder, getSectionForPosition(position), getPositionInSectionForPosition(position));
        }

        @Override
        public int getItemCount() {
            if (count >= 0) {
                return count;
            }
            count = 0;
            for (int i = 0, N = internalGetSectionCount(); i < N; i++) {
                count += internalGetCountForSection(i);
            }
            return count;
        }

        public final Object getItem(int position) {
            return getItem(getSectionForPosition(position), getPositionInSectionForPosition(position));
        }

        public final int getItemViewType(int position) {
            return getItemViewType(getSectionForPosition(position), getPositionInSectionForPosition(position));
        }

        @Override
        public final void onBindViewHolder(ViewHolder holder, int position) {
            onBindViewHolder(getSectionForPosition(position), getPositionInSectionForPosition(position), holder);
        }

        private int internalGetCountForSection(int section) {
            int cachedSectionCount = sectionCountCache.get(section, Integer.MAX_VALUE);
            if (cachedSectionCount != Integer.MAX_VALUE) {
                return cachedSectionCount;
            }
            int sectionCount = getCountForSection(section);
            sectionCountCache.put(section, sectionCount);
            return sectionCount;
        }

        private int internalGetSectionCount() {
            if (sectionCount >= 0) {
                return sectionCount;
            }
            sectionCount = getSectionCount();
            return sectionCount;
        }

        public final int getSectionForPosition(int position) {
            int cachedSection = sectionCache.get(position, Integer.MAX_VALUE);
            if (cachedSection != Integer.MAX_VALUE) {
                return cachedSection;
            }
            int sectionStart = 0;
            for (int i = 0, N = internalGetSectionCount(); i < N; i++) {
                int sectionCount = internalGetCountForSection(i);
                int sectionEnd = sectionStart + sectionCount;
                if (position >= sectionStart && position < sectionEnd) {
                    sectionCache.put(position, i);
                    return i;
                }
                sectionStart = sectionEnd;
            }
            return -1;
        }

        public int getPositionInSectionForPosition(int position) {
            int cachedPosition = sectionPositionCache.get(position, Integer.MAX_VALUE);
            if (cachedPosition != Integer.MAX_VALUE) {
                return cachedPosition;
            }
            int sectionStart = 0;
            for (int i = 0, N = internalGetSectionCount(); i < N; i++) {
                int sectionCount = internalGetCountForSection(i);
                int sectionEnd = sectionStart + sectionCount;
                if (position >= sectionStart && position < sectionEnd) {
                    int positionInSection = position - sectionStart;
                    sectionPositionCache.put(position, positionInSection);
                    return positionInSection;
                }
                sectionStart = sectionEnd;
            }
            return -1;
        }

        public void update(boolean diff) {
            cleanupCache();

            ArrayList<Integer> oldHashes = new ArrayList<>(hashes);
            hashes.clear();

            for (int i = 0, N = internalGetSectionCount(); i < N; i++) {
                int count = internalGetCountForSection(i);
                for (int j = 0; j < count; ++j) {
                    hashes.add(Objects.hash(i * -49612, getItem(i, j)));
                }
            }

            if (diff) {
                DiffUtil.calculateDiff(new DiffUtil.Callback() {
                    @Override
                    public int getOldListSize() {
                        return oldHashes.size();
                    }

                    @Override
                    public int getNewListSize() {
                        return hashes.size();
                    }

                    @Override
                    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                        return Objects.equals(oldHashes.get(oldItemPosition), hashes.get(newItemPosition));
                    }

                    @Override
                    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                        return areItemsTheSame(oldItemPosition, newItemPosition);
                    }
                }, true).dispatchUpdatesTo(this);
            } else {
                super.notifyDataSetChanged();
            }
        }

        public abstract int getSectionCount();

        public abstract int getCountForSection(int section);

        public abstract boolean isEnabled(ViewHolder holder, int section, int row);

        public abstract int getItemViewType(int section, int position);

        public abstract Object getItem(int section, int position);

        public abstract void onBindViewHolder(int section, int position, ViewHolder holder);

        public abstract View getSectionHeaderView(int section, View view);
    }

    public static class Holder extends ViewHolder {

        public Holder(View itemView) {
            super(itemView);
        }
    }

    public class FastScroll extends View {

        public static final int LETTER_TYPE = 0;
        public static final int DATE_TYPE = 1;

        private RectF rect = new RectF();
        private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress;
        private float lastY;
        private float startDy;
        private boolean pressed;
        private StaticLayout letterLayout;
        private StaticLayout oldLetterLayout;
        private StaticLayout outLetterLayout;
        private StaticLayout inLetterLayout;
        private StaticLayout stableLetterLayout;
        private float replaceLayoutProgress = 1f;
        private boolean fromTop;
        private float lastLetterY;
        private float fromWidth;
        private TextPaint letterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private String currentLetter;
        private Path path = new Path();
        private Path arrowPath = new Path();
        private float[] radii = new float[8];
        private float textX;
        private float textY;
        private float bubbleProgress;
        private long lastUpdateTime;
        private int scrollX;
        private int type;
        private int inactiveColor;
        private int activeColor;
        private boolean floatingDateVisible;
        private float floatingDateProgress;
        private int[] positionWithOffset = new int[2];
        boolean isVisible;
        float touchSlop;
        Drawable fastScrollShadowDrawable;
        Drawable fastScrollBackgroundDrawable;
        boolean isRtl;

        Runnable hideFloatingDateRunnable = new Runnable() {
            @Override
            public void run() {
                if (pressed) {
                    AndroidUtilities.cancelRunOnUIThread(hideFloatingDateRunnable);
                    AndroidUtilities.runOnUIThread(hideFloatingDateRunnable, 4000);
                } else {
                    floatingDateVisible = false;
                    invalidate();
                }
            }
        };

        public FastScroll(Context context, int type) {
            super(context);
            this.type = type;
            if (type == LETTER_TYPE) {
                letterPaint.setTextSize(AndroidUtilities.dp(45));
                isRtl = LocaleController.isRTL;
            } else {
                isRtl = false;
                letterPaint.setTextSize(AndroidUtilities.dp(13));
                letterPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                paint2.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                fastScrollBackgroundDrawable = ContextCompat.getDrawable(context, R.drawable.calendar_date).mutate();
                fastScrollBackgroundDrawable.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhite), Color.WHITE, 0.1f), PorterDuff.Mode.MULTIPLY));
            }
            for (int a = 0; a < 8; a++) {
                radii[a] = AndroidUtilities.dp(44);
            }

            scrollX = isRtl ? AndroidUtilities.dp(10) : AndroidUtilities.dp((type == LETTER_TYPE ? 132 : 240) - 15);
            updateColors();
            setFocusableInTouchMode(true);
            ViewConfiguration vc = ViewConfiguration.get(context);
            touchSlop = vc.getScaledTouchSlop();
            fastScrollShadowDrawable = ContextCompat.getDrawable(context, R.drawable.fast_scroll_shadow);
        }

        private void updateColors() {
            inactiveColor = type == LETTER_TYPE ? Theme.getColor(Theme.key_fastScrollInactive) : ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.4f));
            activeColor = Theme.getColor(Theme.key_fastScrollActive);
            paint.setColor(inactiveColor);

            if (type == LETTER_TYPE) {
                letterPaint.setColor(Theme.getColor(Theme.key_fastScrollText));
            } else {
                letterPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            }
            invalidate();
        }

        float startY;
        boolean isMoving;
        long startTime;
        float visibilityAlpha;
        float viewAlpha;

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (!isVisible) {
                pressed = false;
                return false;
            }
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    float x = event.getX();
                    startY = lastY = event.getY();
                    float currentY = (float) Math.ceil((getMeasuredHeight() - AndroidUtilities.dp(24 + 30)) * progress) + AndroidUtilities.dp(12);
                    if (isRtl && x > AndroidUtilities.dp(25) || !isRtl && x < AndroidUtilities.dp(107) || lastY < currentY || lastY > currentY + AndroidUtilities.dp(30)) {
                        return false;
                    }
                    if (type == DATE_TYPE && !floatingDateVisible) {
                        if (isRtl && x > AndroidUtilities.dp(25) || !isRtl && x < (getMeasuredWidth() - AndroidUtilities.dp(25)) || lastY < currentY || lastY > currentY + AndroidUtilities.dp(30)) {
                            return false;
                        }
                    }
                    startDy = lastY - currentY;
                    startTime = System.currentTimeMillis();
                    pressed = true;
                    isMoving = false;
                    lastUpdateTime = System.currentTimeMillis();
                    invalidate();
                    Adapter adapter = getAdapter();
                    showFloatingDate();
                    if (adapter instanceof FastScrollAdapter) {
                        ((FastScrollAdapter) adapter).onStartFastScroll();
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!pressed) {
                        return true;
                    }
                    if (Math.abs(event.getY() - startY) > touchSlop) {
                        isMoving = true;
                    }
                    if (isMoving) {
                        float newY = event.getY();
                        float minY = AndroidUtilities.dp(12) + startDy;
                        float maxY = getMeasuredHeight() - AndroidUtilities.dp(12 + 30) + startDy;
                        if (newY < minY) {
                            newY = minY;
                        } else if (newY > maxY) {
                            newY = maxY;
                        }
                        float dy = newY - lastY;
                        lastY = newY;
                        progress += dy / (getMeasuredHeight() - AndroidUtilities.dp(24 + 30));
                        if (progress < 0) {
                            progress = 0;
                        } else if (progress > 1) {
                            progress = 1;
                        }
                        getCurrentLetter(true);
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    adapter = getAdapter();
                    if (pressed && !isMoving && System.currentTimeMillis() - startTime < 150) {
                        if (adapter instanceof FastScrollAdapter) {
                            ((FastScrollAdapter)adapter).onFastScrollSingleTap();
                        }
                    }
                    isMoving = false;
                    pressed = false;
                    lastUpdateTime = System.currentTimeMillis();
                    invalidate();
                    if (adapter instanceof FastScrollAdapter) {
                        ((FastScrollAdapter) adapter).onFinishFastScroll(RecyclerListView.this);
                    }
                    showFloatingDate();
                    return true;
            }
            return pressed;
        }

        private void getCurrentLetter(boolean updatePosition) {
            LayoutManager layoutManager = getLayoutManager();
            if (layoutManager instanceof LinearLayoutManager) {
                LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
                if (linearLayoutManager.getOrientation() == LinearLayoutManager.VERTICAL) {
                    Adapter adapter = getAdapter();
                    if (adapter instanceof FastScrollAdapter) {
                        FastScrollAdapter fastScrollAdapter = (FastScrollAdapter) adapter;
                        fastScrollAdapter.getPositionForScrollProgress(RecyclerListView.this, progress, positionWithOffset);
                        if (updatePosition) {
                            linearLayoutManager.scrollToPositionWithOffset(positionWithOffset[0], -positionWithOffset[1] + sectionOffset);
                        }

                        String newLetter = fastScrollAdapter.getLetter(positionWithOffset[0]);
                        if (newLetter == null) {
                            if (letterLayout != null) {
                                oldLetterLayout = letterLayout;
                            }
                            letterLayout = null;
                        } else if (!newLetter.equals(currentLetter)) {
                            currentLetter = newLetter;
                            if (type == LETTER_TYPE) {
                                letterLayout = new StaticLayout(newLetter, letterPaint, 1000, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                            } else {
                                outLetterLayout = letterLayout;
                                int w = ((int) letterPaint.measureText(newLetter)) + 1;
                                letterLayout = new StaticLayout(newLetter, letterPaint, w, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                                if (outLetterLayout != null) {
                                    String[] newSplits = newLetter.split(" ");
                                    String[] oldSplits = outLetterLayout.getText().toString().split(" ");
                                    if (newSplits != null && oldSplits != null && newSplits.length == 2 && oldSplits.length == 2 && newSplits[1].equals(oldSplits[1])) {
                                        String oldText = outLetterLayout.getText().toString();
                                        SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(oldText);
                                        spannableStringBuilder.setSpan(new EmptyStubSpan(), oldSplits[0].length(), oldText.length(), 0);
                                        int oldW = ((int) letterPaint.measureText(oldText)) + 1;
                                        outLetterLayout = new StaticLayout(spannableStringBuilder, letterPaint, oldW, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

                                        spannableStringBuilder = new SpannableStringBuilder(newLetter);
                                        spannableStringBuilder.setSpan(new EmptyStubSpan(), newSplits[0].length(), newLetter.length(), 0);
                                        inLetterLayout = new StaticLayout(spannableStringBuilder, letterPaint, w, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

                                        spannableStringBuilder = new SpannableStringBuilder(newLetter);
                                        spannableStringBuilder.setSpan(new EmptyStubSpan(), 0, newSplits[0].length(), 0);
                                        stableLetterLayout = new StaticLayout(spannableStringBuilder, letterPaint, w, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                                    } else {
                                        inLetterLayout = letterLayout;
                                        stableLetterLayout = null;
                                    }

                                    fromWidth = outLetterLayout.getWidth();
                                    replaceLayoutProgress = 0f;
                                    fromTop = getProgress() > lastLetterY;
                                }

                                lastLetterY = getProgress();
                            }
                            oldLetterLayout = null;
                            if (letterLayout.getLineCount() > 0) {
                                float lWidth = letterLayout.getLineWidth(0);
                                float lleft = letterLayout.getLineLeft(0);
                                if (isRtl) {
                                    textX = AndroidUtilities.dp(10) + (AndroidUtilities.dp(88) - letterLayout.getLineWidth(0)) / 2 - letterLayout.getLineLeft(0);
                                } else {
                                    textX = (AndroidUtilities.dp(88) - letterLayout.getLineWidth(0)) / 2 - letterLayout.getLineLeft(0);
                                }
                                textY = (AndroidUtilities.dp(88) - letterLayout.getHeight()) / 2;
                            }
                        }
                    }
                }
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(AndroidUtilities.dp(type == LETTER_TYPE ? 132 : 240), MeasureSpec.getSize(heightMeasureSpec));

            arrowPath.reset();
            arrowPath.setLastPoint(0, 0);
            arrowPath.lineTo(AndroidUtilities.dp(4), -AndroidUtilities.dp(4));
            arrowPath.lineTo(-AndroidUtilities.dp(4), -AndroidUtilities.dp(4));
            arrowPath.close();

        }

        @Override
        protected void onDraw(Canvas canvas) {
            int y = getPaddingTop() + (int) Math.ceil((getMeasuredHeight() - getPaddingTop() - AndroidUtilities.dp(24 + 30)) * progress);
            rect.set(scrollX, AndroidUtilities.dp(12) + y, scrollX + AndroidUtilities.dp(5), AndroidUtilities.dp(12 + 30) + y);
            if (type == LETTER_TYPE) {
                paint.setColor(ColorUtils.blendARGB(inactiveColor, activeColor, bubbleProgress));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), paint);
            } else {
                paint.setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhite), Color.WHITE, 0.1f));

                float cy = y + AndroidUtilities.dp(12 + 15);
                fastScrollShadowDrawable.setBounds(getMeasuredWidth() - fastScrollShadowDrawable.getIntrinsicWidth(), (int) (cy - fastScrollShadowDrawable.getIntrinsicHeight() / 2), getMeasuredWidth(), (int) (cy + fastScrollShadowDrawable.getIntrinsicHeight() / 2));
                fastScrollShadowDrawable.draw(canvas);
                canvas.drawCircle(scrollX + AndroidUtilities.dp(8), y + AndroidUtilities.dp(12 + 15), AndroidUtilities.dp(24), paint);

                paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                canvas.save();
                canvas.translate(scrollX + AndroidUtilities.dp(4), y + AndroidUtilities.dp(12 + 15 + 2 + 5) + AndroidUtilities.dp(2) * bubbleProgress);
                canvas.drawPath(arrowPath, paint);
                canvas.restore();

                canvas.save();
                canvas.translate(scrollX + AndroidUtilities.dp(4), y + AndroidUtilities.dp(12 + 15 + 2  - 5) - AndroidUtilities.dp(2) * bubbleProgress);
                canvas.rotate(180, 0, -AndroidUtilities.dp(2));
                canvas.drawPath(arrowPath, paint);
                canvas.restore();
            }
            if (type == LETTER_TYPE) {
                if ((isMoving || bubbleProgress != 0)) {
                    paint.setAlpha((int) (255 * bubbleProgress));
                    int progressY = y + AndroidUtilities.dp(30);
                    y -= AndroidUtilities.dp(46);
                    float diff = 0;
                    if (y <= AndroidUtilities.dp(12)) {
                        diff = AndroidUtilities.dp(12) - y;
                        y = AndroidUtilities.dp(12);
                    }
                    float raduisTop;
                    float raduisBottom;
                    canvas.translate(AndroidUtilities.dp(10), y);
                    if (diff <= AndroidUtilities.dp(29)) {
                        raduisTop = AndroidUtilities.dp(44);
                        raduisBottom = AndroidUtilities.dp(4) + (diff / AndroidUtilities.dp(29)) * AndroidUtilities.dp(40);
                    } else {
                        diff -= AndroidUtilities.dp(29);
                        raduisBottom = AndroidUtilities.dp(44);
                        raduisTop = AndroidUtilities.dp(4) + (1.0f - diff / AndroidUtilities.dp(29)) * AndroidUtilities.dp(40);
                    }
                    if (isRtl && (radii[0] != raduisTop || radii[6] != raduisBottom) || !isRtl && (radii[2] != raduisTop || radii[4] != raduisBottom)) {
                        if (isRtl) {
                            radii[0] = radii[1] = raduisTop;
                            radii[6] = radii[7] = raduisBottom;
                        } else {
                            radii[2] = radii[3] = raduisTop;
                            radii[4] = radii[5] = raduisBottom;
                        }
                        path.reset();
                        rect.set(isRtl ? AndroidUtilities.dp(10) : 0, 0, AndroidUtilities.dp(isRtl ? 98 : 88), AndroidUtilities.dp(88));
                        path.addRoundRect(rect, radii, Path.Direction.CW);
                        path.close();
                    }
                    StaticLayout layoutToDraw = letterLayout != null ? letterLayout : oldLetterLayout;
                    if (layoutToDraw != null) {
                        canvas.save();
                        canvas.scale(bubbleProgress, bubbleProgress, scrollX, progressY - y);
                        canvas.drawPath(path, paint);
                        canvas.translate(textX, textY);
                        layoutToDraw.draw(canvas);
                        canvas.restore();
                    }
                }
            } else if (type == DATE_TYPE) {
                if (letterLayout != null && floatingDateProgress != 0) {
                    canvas.save();
                    float s = 0.7f + 0.3f * floatingDateProgress;
                    canvas.scale(s, s, rect.right - AndroidUtilities.dp(12), rect.centerY());

                    float cy = rect.centerY();
                    float x = rect.left - AndroidUtilities.dp(30) * bubbleProgress - AndroidUtilities.dp(8);
                    float r = letterLayout.getHeight() / 2f + AndroidUtilities.dp(6);
                    float width = replaceLayoutProgress * letterLayout.getWidth() + fromWidth * (1f - replaceLayoutProgress);
                    rect.set(x - width - AndroidUtilities.dp(36), cy - letterLayout.getHeight() / 2f - AndroidUtilities.dp(8),  x - AndroidUtilities.dp(12), cy + letterLayout.getHeight() / 2f + AndroidUtilities.dp(8));

                    int oldAlpha1 = paint2.getAlpha();
                    int oldAlpha2 = letterPaint.getAlpha();
                    paint2.setAlpha((int) (oldAlpha1 * floatingDateProgress));

                    fastScrollBackgroundDrawable.setBounds((int) rect.left, (int) rect.top, (int) rect.right, (int) rect.bottom);
                    fastScrollBackgroundDrawable.setAlpha((int) (255 * floatingDateProgress));
                    fastScrollBackgroundDrawable.draw(canvas);

                    if (replaceLayoutProgress != 1f) {
                        replaceLayoutProgress += 16f / 150f;
                        if (replaceLayoutProgress > 1f) {
                            replaceLayoutProgress = 1f;
                        } else {
                            invalidate();
                        }
                    }

                    if (replaceLayoutProgress != 1f) {
                        canvas.save();
                        rect.inset(AndroidUtilities.dp(4), AndroidUtilities.dp(2));
                        canvas.clipRect(rect);
                        if (outLetterLayout != null) {
                            letterPaint.setAlpha((int) (oldAlpha2 * floatingDateProgress * (1f - replaceLayoutProgress)));
                            canvas.save();
                            canvas.translate(x - outLetterLayout.getWidth() - AndroidUtilities.dp(24), cy - outLetterLayout.getHeight() / 2f + (fromTop ? -1 : 1) * AndroidUtilities.dp(15) * replaceLayoutProgress);
                            outLetterLayout.draw(canvas);
                            canvas.restore();
                        }

                        if (inLetterLayout != null) {
                            letterPaint.setAlpha((int) (oldAlpha2 * floatingDateProgress * replaceLayoutProgress));
                            canvas.save();
                            canvas.translate(x - inLetterLayout.getWidth() - AndroidUtilities.dp(24), cy - inLetterLayout.getHeight() / 2f + (fromTop ? 1 : -1) * AndroidUtilities.dp(15) * (1f - replaceLayoutProgress));
                            inLetterLayout.draw(canvas);
                            canvas.restore();
                        }

                        if (stableLetterLayout != null) {
                            letterPaint.setAlpha((int) (oldAlpha2 * floatingDateProgress));
                            canvas.save();
                            canvas.translate(x - stableLetterLayout.getWidth() - AndroidUtilities.dp(24), cy - stableLetterLayout.getHeight() / 2f);
                            stableLetterLayout.draw(canvas);
                            canvas.restore();
                        }
                        canvas.restore();
                    } else {
                        letterPaint.setAlpha((int) (oldAlpha2 * floatingDateProgress));
                        canvas.save();
                        canvas.translate(x - letterLayout.getWidth() - AndroidUtilities.dp(24), cy - letterLayout.getHeight() / 2f + AndroidUtilities.dp(15) * (1f - replaceLayoutProgress));
                        letterLayout.draw(canvas);
                        canvas.restore();
                    }

                    paint2.setAlpha(oldAlpha1);
                    letterPaint.setAlpha(oldAlpha2);

                    canvas.restore();
                }
            }
            long newTime = System.currentTimeMillis();
            long dt = (newTime - lastUpdateTime);
            if (dt < 0 || dt > 17) {
                dt = 17;
            }
            if ((isMoving && letterLayout != null && bubbleProgress < 1.0f) || (!isMoving || letterLayout == null) && bubbleProgress > 0.0f) {
                lastUpdateTime = newTime;
                invalidate();
                if (isMoving && letterLayout != null) {
                    bubbleProgress += dt / 120.0f;
                    if (bubbleProgress > 1.0f) {
                        bubbleProgress = 1.0f;
                    }
                } else {
                    bubbleProgress -= dt / 120.0f;
                    if (bubbleProgress < 0.0f) {
                        bubbleProgress = 0.0f;
                    }
                }
            }


            if (floatingDateVisible && floatingDateProgress != 1f) {
                floatingDateProgress += dt / 120.0f;
                if (floatingDateProgress > 1.0f) {
                    floatingDateProgress = 1.0f;
                }
                invalidate();
            } else if (!floatingDateVisible && floatingDateProgress != 0) {
                floatingDateProgress -= dt / 120.0f;
                if (floatingDateProgress < 0.0f) {
                    floatingDateProgress = 0.0f;
                }
                invalidate();
            }
        }

        @Override
        public void layout(int l, int t, int r, int b) {
            if (!selfOnLayout) {
                return;
            }
            super.layout(l, t, r, b);
        }

        public void setProgress(float value) {
            progress = value;
            invalidate();
        }

        @Override
        public boolean isPressed() {
            return pressed;
        }

        public void showFloatingDate() {
            if (type != DATE_TYPE) {
                return;
            }
            if (!floatingDateVisible) {
                floatingDateVisible = true;
                invalidate();
            }
            AndroidUtilities.cancelRunOnUIThread(hideFloatingDateRunnable);
            AndroidUtilities.runOnUIThread(hideFloatingDateRunnable, 2000);
        }

        public void setIsVisible(boolean visible) {
            if (isVisible != visible) {
                this.isVisible = visible;
                visibilityAlpha = visible ? 1f : 0f;
                super.setAlpha(viewAlpha * visibilityAlpha);
            }
        }

        public void setVisibilityAlpha(float v) {
            if (visibilityAlpha != v) {
                visibilityAlpha = v;
                super.setAlpha(viewAlpha * visibilityAlpha);
            }
        }

        @Override
        public void setAlpha(float alpha) {
            if (viewAlpha != alpha) {
                viewAlpha = alpha;
                super.setAlpha(viewAlpha * visibilityAlpha);
            }
        }

        @Override
        public float getAlpha() {
            return viewAlpha;
        }

        public int getScrollBarY() {
            return (int) Math.ceil((getMeasuredHeight() - AndroidUtilities.dp(24 + 30)) * progress) + AndroidUtilities.dp(17);
        }

        public float getProgress() {
            return progress;
        }

    }

    private class RecyclerListViewItemClickListener implements OnItemTouchListener {

        public RecyclerListViewItemClickListener(Context context) {
            gestureDetector = new GestureDetectorFixDoubleTap(context, new GestureDetectorFixDoubleTap.OnGestureListener() {
                private View doubleTapView;

                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    if (currentChildView != null) {
                        if (onItemClickListenerExtended != null && onItemClickListenerExtended.hasDoubleTap(currentChildView, currentChildPosition)) {
                            doubleTapView = currentChildView;
                        } else {
                            onPressItem(currentChildView, e);
                            return false;
                        }
                    }
                    return false;
                }

                @Override
                public boolean onSingleTapConfirmed(MotionEvent e) {
                    if (doubleTapView != null && onItemClickListenerExtended != null) {
                        if (onItemClickListenerExtended.hasDoubleTap(doubleTapView, currentChildPosition)) {
                            onPressItem(doubleTapView, e);
                            doubleTapView = null;
                            return true;
                        }
                    }
                    return false;
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (doubleTapView != null && onItemClickListenerExtended != null && onItemClickListenerExtended.hasDoubleTap(doubleTapView, currentChildPosition)) {
                        onItemClickListenerExtended.onDoubleTap(doubleTapView, currentChildPosition, e.getX(), e.getY());
                        doubleTapView = null;
                        return true;
                    }
                    return false;
                }

                private void onPressItem(View cv, MotionEvent e) {
                    if (cv != null && (onItemClickListener != null || onItemClickListenerExtended != null)) {
                        final float x = e.getX();
                        final float y = e.getY();
                        onChildPressed(cv, x, y, true);
                        final View view = cv;
                        final int position = currentChildPosition;
                        if (instantClick && position != -1) {
                            view.playSoundEffect(SoundEffectConstants.CLICK);
                            view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
                            if (onItemClickListener != null) {
                                onItemClickListener.onItemClick(view, position);
                            } else if (onItemClickListenerExtended != null) {
                                onItemClickListenerExtended.onItemClick(view, position, x - view.getX(), y - view.getY());
                            }
                        }
                        AndroidUtilities.runOnUIThread(clickRunnable = new Runnable() {
                            @Override
                            public void run() {
                                if (this == clickRunnable) {
                                    clickRunnable = null;
                                }
                                if (view != null) {
                                    onChildPressed(view, 0, 0, false);
                                    if (!instantClick) {
                                        view.playSoundEffect(SoundEffectConstants.CLICK);
                                        view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
                                        if (position != -1) {
                                            if (onItemClickListener != null) {
                                                onItemClickListener.onItemClick(view, position);
                                            } else if (onItemClickListenerExtended != null) {
                                                onItemClickListenerExtended.onItemClick(view, position, x - view.getX(), y - view.getY());
                                            }
                                        }
                                    }
                                }
                            }
                        }, ViewConfiguration.getPressedStateDuration());

                        if (selectChildRunnable != null) {
                            AndroidUtilities.cancelRunOnUIThread(selectChildRunnable);
                            selectChildRunnable = null;
                            currentChildView = null;
                            interceptedByChild = false;
                            removeSelection(cv, e);
                        }
                    }
                }

                @Override
                public void onLongPress(MotionEvent event) {
                    if (currentChildView == null || currentChildPosition == -1 || onItemLongClickListener == null && onItemLongClickListenerExtended == null) {
                        return;
                    }
                    View child = currentChildView;
                    if (onItemLongClickListener != null) {
                        if (onItemLongClickListener.onItemClick(currentChildView, currentChildPosition)) {
                            child.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            child.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
                        }
                    } else {
                        if (onItemLongClickListenerExtended.onItemClick(currentChildView, currentChildPosition, event.getX() - currentChildView.getX(), event.getY() - currentChildView.getY())) {
                            child.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            child.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
                            longPressCalled = true;
                        }
                    }
                }

                @Override
                public boolean onDown(MotionEvent e) {
                    return false;
                }

                @Override
                public boolean hasDoubleTap() {
                    return onItemLongClickListenerExtended != null;
                }
            });
            gestureDetector.setIsLongpressEnabled(false);
        }

        @Override
        public boolean onInterceptTouchEvent(RecyclerView view, MotionEvent event) {
            int action = event.getActionMasked();
            boolean isScrollIdle = RecyclerListView.this.getScrollState() == RecyclerListView.SCROLL_STATE_IDLE;

            if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) && currentChildView == null && isScrollIdle) {
                float ex = event.getX();
                float ey = event.getY();
                longPressCalled = false;
                ItemAnimator animator = getItemAnimator();
                if ((allowItemsInteractionDuringAnimation || animator == null || !animator.isRunning()) && allowSelectChildAtPosition(ex, ey)) {
                    View v = findChildViewUnder(ex, ey);
                    if (v != null && allowSelectChildAtPosition(v)) {
                        currentChildView = v;
                    }
                }
                if (currentChildView instanceof ViewGroup) {
                    float x = event.getX() - currentChildView.getLeft();
                    float y = event.getY() - currentChildView.getTop();
                    ViewGroup viewGroup = (ViewGroup) currentChildView;
                    final int count = viewGroup.getChildCount();
                    for (int i = count - 1; i >= 0; i--) {
                        final View child = viewGroup.getChildAt(i);
                        if (x >= child.getLeft() && x <= child.getRight() && y >= child.getTop() && y <= child.getBottom()) {
                            if (child.isClickable()) {
                                currentChildView = null;
                                break;
                            }
                        }
                    }
                }
                currentChildPosition = -1;
                if (currentChildView != null) {
                    if (useLayoutPositionOnClick) {
                        currentChildPosition = view.getChildLayoutPosition(currentChildView);
                    } else {
                        currentChildPosition = view.getChildAdapterPosition(currentChildView);
                    }
                    MotionEvent childEvent = MotionEvent.obtain(0, 0, event.getActionMasked(), event.getX() - currentChildView.getLeft(), event.getY() - currentChildView.getTop(), 0);
                    if (currentChildView.onTouchEvent(childEvent)) {
                        interceptedByChild = true;
                    }
                    childEvent.recycle();
                }
            }

            if (currentChildView != null && !interceptedByChild) {
                try {
                    gestureDetector.onTouchEvent(event);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                if (!interceptedByChild && currentChildView != null) {
                    float x = event.getX();
                    float y = event.getY();
                    selectChildRunnable = () -> {
                        if (selectChildRunnable != null && currentChildView != null) {
                            onChildPressed(currentChildView, x, y, true);
                            selectChildRunnable = null;
                        }
                    };
                    AndroidUtilities.runOnUIThread(selectChildRunnable, ViewConfiguration.getTapTimeout());
                    if (currentChildView.isEnabled() && canHighlightChildAt(currentChildView, x - currentChildView.getX(), y - currentChildView.getY())) {
                        positionSelector(currentChildPosition, currentChildView);
                        if (selectorDrawable != null) {
                            final Drawable d = selectorDrawable.getCurrent();
                            if (d instanceof TransitionDrawable) {
                                if (onItemLongClickListener != null || onItemClickListenerExtended != null) {
                                    ((TransitionDrawable) d).startTransition(ViewConfiguration.getLongPressTimeout());
                                } else {
                                    ((TransitionDrawable) d).resetTransition();
                                }
                            }
                            if (Build.VERSION.SDK_INT >= 21) {
                                selectorDrawable.setHotspot(event.getX(), event.getY());
                            }
                        }
                        updateSelectorState();
                    } else {
                        selectorRect.setEmpty();
                    }
                } else {
                    selectorRect.setEmpty();
                }
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL || !isScrollIdle) {
                if (currentChildView != null) {
                    if (selectChildRunnable != null) {
                        AndroidUtilities.cancelRunOnUIThread(selectChildRunnable);
                        selectChildRunnable = null;
                    }
                    View pressedChild = currentChildView;
                    onChildPressed(currentChildView, 0, 0, false);
                    currentChildView = null;
                    interceptedByChild = false;
                    removeSelection(pressedChild, event);

                    if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) && onItemLongClickListenerExtended != null && longPressCalled) {
                        onItemLongClickListenerExtended.onLongClickRelease();
                        longPressCalled = false;
                    }
                }
            }
            return false;
        }

        @Override
        public void onTouchEvent(RecyclerView view, MotionEvent event) {

        }

        @Override
        public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            cancelClickRunnables(true);
        }
    }

    @Override
    public View findChildViewUnder(float x, float y) {
        final int count = getChildCount();
        for (int a = 0; a < 2; a++) {
            for (int i = count - 1; i >= 0; i--) {
                final View child = getChildAt(i);
                final float translationX = a == 0 ? child.getTranslationX() : 0;
                final float translationY = a == 0 ? child.getTranslationY() : 0;
                if (x >= child.getLeft() + translationX
                        && x <= child.getRight() + translationX
                        && y >= child.getTop() + translationY
                        && y <= child.getBottom() + translationY) {
                    return child;
                }
            }
        }
        return null;
    }

    protected boolean canHighlightChildAt(View child, float x, float y) {
        return true;
    }

    public void setDisableHighlightState(boolean value) {
        disableHighlightState = value;
    }

    public View getPressedChildView() {
        return currentChildView;
    }

    protected void onChildPressed(View child, float x, float y, boolean pressed) {
        if (disableHighlightState || child == null) {
            return;
        }
        child.setPressed(pressed);
    }

    protected boolean allowSelectChildAtPosition(float x, float y) {
        return true;
    }

    protected boolean allowSelectChildAtPosition(View child) {
        return true;
    }

    private void removeSelection(View pressedChild, MotionEvent event) {
        if (pressedChild == null || selectorRect.isEmpty()) {
            return;
        }
        if (pressedChild.isEnabled()) {
            positionSelector(currentChildPosition, pressedChild);
            if (selectorDrawable != null) {
                Drawable d = selectorDrawable.getCurrent();
                if (d instanceof TransitionDrawable) {
                    ((TransitionDrawable) d).resetTransition();
                }
                if (event != null && Build.VERSION.SDK_INT >= 21) {
                    selectorDrawable.setHotspot(event.getX(), event.getY());
                }
            }
        } else {
            selectorRect.setEmpty();
        }
        updateSelectorState();
    }

    public void cancelClickRunnables(boolean uncheck) {
        if (selectChildRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(selectChildRunnable);
            selectChildRunnable = null;
        }
        if (currentChildView != null) {
            View child = currentChildView;
            if (uncheck) {
                onChildPressed(currentChildView, 0, 0, false);
            }
            currentChildView = null;
            removeSelection(child, null);
        }
        selectorRect.setEmpty();
        if (clickRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(clickRunnable);
            clickRunnable = null;
        }
        interceptedByChild = false;
    }

    private boolean resetSelectorOnChanged = true;
    public void setResetSelectorOnChanged(boolean value) {
        resetSelectorOnChanged = value;
    }

    private AdapterDataObserver observer = new AdapterDataObserver() {
        @Override
        public void onChanged() {
            checkIfEmpty(true);
            if (resetSelectorOnChanged) {
                currentFirst = -1;
                if (removeHighlighSelectionRunnable == null) {
                    selectorRect.setEmpty();
                }
            }
            invalidate();
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            checkIfEmpty(true);
            if (pinnedHeader != null && pinnedHeader.getAlpha() == 0) {
                currentFirst = -1;
                invalidateViews();
            }
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            checkIfEmpty(true);
        }
    };

    public int[] getResourceDeclareStyleableIntArray(String packageName, String name) {
        try {
            Field f = Class.forName(packageName + ".R$styleable").getField(name);
            if (f != null) {
                return (int[]) f.get(null);
            }
        } catch (Throwable t) {
            //ignore
        }
        return null;
    }

    public RecyclerListView(Context context) {
        this(context, null);
    }

    @SuppressLint("PrivateApi")
    public RecyclerListView(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        setGlowColor(getThemedColor(Theme.key_actionBarDefault));
        selectorDrawable = Theme.getSelectorDrawable(getThemedColor(Theme.key_listSelector), false);
        selectorDrawable.setCallback(this);

        try {
            if (!gotAttributes) {
                attributes = getResourceDeclareStyleableIntArray("com.android.internal", "View");
                if (attributes == null) {
                    attributes = new int[0];
                }
                gotAttributes = true;
            }
            TypedArray a = context.getTheme().obtainStyledAttributes(attributes);
            if (initializeScrollbars != null) {
                initializeScrollbars.invoke(this, a);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        super.setOnScrollListener(new OnScrollListener() {

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                checkStopHeavyOperations(newState);
                if (newState != SCROLL_STATE_IDLE && currentChildView != null) {
                    if (selectChildRunnable != null) {
                        AndroidUtilities.cancelRunOnUIThread(selectChildRunnable);
                        selectChildRunnable = null;
                    }
                    MotionEvent event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0);
                    try {
                        gestureDetector.onTouchEvent(event);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    currentChildView.onTouchEvent(event);
                    event.recycle();
                    View child = currentChildView;
                    onChildPressed(currentChildView, 0, 0, false);
                    currentChildView = null;
                    removeSelection(child, null);
                    interceptedByChild = false;
                }
                if (onScrollListener != null) {
                    onScrollListener.onScrollStateChanged(recyclerView, newState);
                }
                scrollingByUser = newState == SCROLL_STATE_DRAGGING || newState == SCROLL_STATE_SETTLING;
                if (scrollingByUser) {
                    scrolledByUserOnce = true;
                }
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (onScrollListener != null) {
                    onScrollListener.onScrolled(recyclerView, dx, dy);
                }
                if (selectorPosition != NO_POSITION) {
                    selectorRect.offset(-dx, -dy);
                    selectorDrawable.setBounds(selectorRect);
                    invalidate();
                } else {
                    selectorRect.setEmpty();
                }
                checkSection(false);
                if (dy != 0 && fastScroll != null) {
                    fastScroll.showFloatingDate();
                }
            }
        });
        addOnItemTouchListener(new RecyclerListViewItemClickListener(context));
    }

    private Paint backgroundPaint;
    protected void drawSectionBackground(Canvas canvas, int fromAdapterPosition, int toAdapterPosition, int color) {
        int top = Integer.MAX_VALUE;
        int bottom = Integer.MIN_VALUE;

        for (int i = 0; i < getChildCount(); ++i) {
            View child = getChildAt(i);
            if (child == null) {
                continue;
            }
            int position = getChildAdapterPosition(child);
            if (position >= fromAdapterPosition && position <= toAdapterPosition) {
                top = Math.min((int) child.getY(), top);
                bottom = Math.max((int) child.getY() + child.getHeight(), bottom);
            }
        }

        if (top < bottom) {
            if (backgroundPaint == null) {
                backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            }
            backgroundPaint.setColor(color);
            canvas.drawRect(0, top, getWidth(), bottom, backgroundPaint);
        }
    }

    protected void drawSectionBackgroundExclusive(Canvas canvas, int fromAdapterPositionExclusive, int toAdapterPositionExclusive, int color) {
        int top = Integer.MAX_VALUE;
        int bottom = Integer.MIN_VALUE;

        for (int i = 0; i < getChildCount(); ++i) {
            View child = getChildAt(i);
            if (child == null) {
                continue;
            }
            int position = getChildAdapterPosition(child);
            if (position > fromAdapterPositionExclusive && position < toAdapterPositionExclusive) {
                top = Math.min((int) child.getY(), top);
                bottom = Math.max((int) child.getY() + child.getHeight(), bottom);
            } else if (position == fromAdapterPositionExclusive) {
                top = Math.min((int) child.getY() + child.getHeight(), top);
                bottom = Math.max((int) child.getY() + child.getHeight(), bottom);
            } else if (position == toAdapterPositionExclusive) {
                top = Math.min((int) child.getY(), top);
                bottom = Math.max((int) child.getY(), bottom);
            }
        }

        if (top < bottom) {
            if (backgroundPaint == null) {
                backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            }
            backgroundPaint.setColor(color);
            canvas.drawRect(0, top, getWidth(), bottom, backgroundPaint);
        }
    }

    protected void drawItemBackground(Canvas canvas, int adapterPosition, int color) {
        drawItemBackground(canvas, adapterPosition, -1, color);
    }

    protected void drawItemBackground(Canvas canvas, int adapterPosition, int height, int color) {
        int top = Integer.MAX_VALUE;
        int bottom = Integer.MIN_VALUE;

        for (int i = 0; i < getChildCount(); ++i) {
            View child = getChildAt(i);
            if (child == null) {
                continue;
            }
            int position = getChildAdapterPosition(child);
            if (position == adapterPosition) {
                top = (int) child.getY();
                if (height <= 0) {
                    bottom = top + child.getHeight();
                } else {
                    bottom = top + height;
                }
            }
        }

        if (top < bottom) {
            if (backgroundPaint == null) {
                backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            }
            backgroundPaint.setColor(color);
            canvas.drawRect(0, top, getWidth(), bottom, backgroundPaint);
        }
    }

    private boolean stoppedAllHeavyOperations;

    private void checkStopHeavyOperations(int newState) {
        if (newState == SCROLL_STATE_IDLE) {
            if (stoppedAllHeavyOperations) {
                stoppedAllHeavyOperations = false;
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
            }
        } else {
            if (!stoppedAllHeavyOperations && allowStopHeaveOperations) {
                stoppedAllHeavyOperations = true;
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 512);
            }
        }
    }

    @Override
    public void setVerticalScrollBarEnabled(boolean verticalScrollBarEnabled) {
        if (attributes != null) {
            super.setVerticalScrollBarEnabled(verticalScrollBarEnabled);
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        if (fastScroll != null && fastScroll.getLayoutParams() != null) {
            int height = getMeasuredHeight() - getPaddingTop() - getPaddingBottom();
            fastScroll.getLayoutParams().height = height;
            fastScroll.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(132), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
        touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (fastScroll != null) {
            selfOnLayout = true;
            t += getPaddingTop();
            if (fastScroll.isRtl) {
                fastScroll.layout(0, t, fastScroll.getMeasuredWidth(), t + fastScroll.getMeasuredHeight());
            } else {
                int x = getMeasuredWidth() - fastScroll.getMeasuredWidth();
                fastScroll.layout(x, t, x + fastScroll.getMeasuredWidth(), t + fastScroll.getMeasuredHeight());
            }
            selfOnLayout = false;
        }
        checkSection(false);
        if (pendingHighlightPosition != null) {
            highlightRowInternal(pendingHighlightPosition, 700, false);
        }
    }

    public void setSelectorType(int type) {
        selectorType = type;
    }

    public void setSelectorRadius(int radius) {
        selectorRadius = radius;
    }

    public void setTopBottomSelectorRadius(int radius) {
        topBottomSelectorRadius = radius;
    }

    public void setDrawSelectorBehind(boolean value) {
        drawSelectorBehind = value;
    }

    public void setSelectorDrawableColor(int color) {
        if (selectorDrawable != null) {
            selectorDrawable.setCallback(null);
        }
        if (selectorType == 8) {
            selectorDrawable = Theme.createRadSelectorDrawable(color, selectorRadius, 0);
        } else if (topBottomSelectorRadius > 0) {
            selectorDrawable = Theme.createRadSelectorDrawable(color, topBottomSelectorRadius, topBottomSelectorRadius);
        } else if (selectorRadius > 0 && selectorType != Theme.RIPPLE_MASK_CIRCLE_20DP) {
            selectorDrawable = Theme.createSimpleSelectorRoundRectDrawable(selectorRadius, 0, color, 0xff000000);
        } else if (selectorType == 2) {
            selectorDrawable = Theme.getSelectorDrawable(color, false);
        } else {
            selectorDrawable = Theme.createSelectorDrawable(color, selectorType, selectorRadius);
        }
        selectorDrawable.setCallback(this);
    }

    public Drawable getSelectorDrawable() {
        return selectorDrawable;
    }

    public void checkSection(boolean force) {
        if ((scrollingByUser || force) && fastScroll != null || sectionsType != SECTIONS_TYPE_SIMPLE && sectionsAdapter != null) {
            LayoutManager layoutManager = getLayoutManager();
            if (layoutManager instanceof LinearLayoutManager) {
                LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
                if (linearLayoutManager.getOrientation() == LinearLayoutManager.VERTICAL) {
                    if (sectionsAdapter != null) {
                        int paddingTop = getPaddingTop();
                        if (sectionsType == SECTIONS_TYPE_STICKY_HEADERS || sectionsType == SECTIONS_TYPE_FAST_SCROLL_ONLY) {
                            int childCount = getChildCount();
                            int maxBottom = 0;
                            int minBottom = Integer.MAX_VALUE;
                            View minChild = null;

                            int minBottomSection = Integer.MAX_VALUE;
                            for (int a = 0; a < childCount; a++) {
                                View child = getChildAt(a);
                                int bottom = child.getBottom();
                                if (bottom <= sectionOffset + paddingTop) {
                                    continue;
                                }
                                if (bottom < minBottom) {
                                    minBottom = bottom;
                                    minChild = child;
                                }
                                maxBottom = Math.max(maxBottom, bottom);
                                if (bottom < sectionOffset + paddingTop + AndroidUtilities.dp(32)) {
                                    continue;
                                }
                                if (bottom < minBottomSection) {
                                    minBottomSection = bottom;
                                }
                            }
                            if (minChild == null) {
                                return;
                            }
                            ViewHolder holder = getChildViewHolder(minChild);
                            if (holder == null) {
                                return;
                            }

                            int firstVisibleItem = holder.getAdapterPosition();
                            int lastVisibleItem = linearLayoutManager.findLastVisibleItemPosition();
                            int visibleItemCount = Math.abs(lastVisibleItem - firstVisibleItem) + 1;

                            if ((scrollingByUser || force) && fastScroll != null && !fastScroll.isPressed()) {
                                Adapter adapter = getAdapter();
                                if (adapter instanceof FastScrollAdapter) {
                                    fastScroll.setProgress(Math.min(1.0f, firstVisibleItem / (float) (sectionsAdapter.getTotalItemsCount() - visibleItemCount + 1)));
                                }
                            }

                            headersCache.addAll(headers);
                            headers.clear();
                            if (sectionsAdapter.getItemCount() == 0) {
                                return;
                            }
                            if (currentFirst != firstVisibleItem || currentVisible != visibleItemCount) {
                                currentFirst = firstVisibleItem;
                                currentVisible = visibleItemCount;

                                sectionsCount = 1;
                                startSection = sectionsAdapter.getSectionForPosition(firstVisibleItem);
                                int itemNum = firstVisibleItem + sectionsAdapter.getCountForSection(startSection) - sectionsAdapter.getPositionInSectionForPosition(firstVisibleItem);
                                while (itemNum < firstVisibleItem + visibleItemCount) {
                                    itemNum += sectionsAdapter.getCountForSection(startSection + sectionsCount);
                                    sectionsCount++;
                                }
                            }

                            if (sectionsType != SECTIONS_TYPE_FAST_SCROLL_ONLY) {
                                int itemNum = firstVisibleItem;
                                for (int a = startSection; a < startSection + sectionsCount; a++) {
                                    View header = null;
                                    if (!headersCache.isEmpty()) {
                                        header = headersCache.get(0);
                                        headersCache.remove(0);
                                    }
                                    header = getSectionHeaderView(a, header);
                                    headers.add(header);
                                    int count = sectionsAdapter.getCountForSection(a);
                                    if (a == startSection) {
                                        int pos = sectionsAdapter.getPositionInSectionForPosition(itemNum);
                                        if (pos == count - 1) {
                                            header.setTag(-header.getHeight() + paddingTop);
                                        } else if (pos == count - 2) {
                                            View child = getChildAt(itemNum - firstVisibleItem);
                                            int headerTop;
                                            if (child != null) {
                                                headerTop = child.getTop() + paddingTop;
                                            } else {
                                                headerTop = -AndroidUtilities.dp(100);
                                            }
                                            header.setTag(Math.min(headerTop, 0));
                                        } else {
                                            header.setTag(0);
                                        }
                                        itemNum += count - sectionsAdapter.getPositionInSectionForPosition(firstVisibleItem);
                                    } else {
                                        View child = getChildAt(itemNum - firstVisibleItem);
                                        if (child != null) {
                                            header.setTag(child.getTop() + paddingTop);
                                        } else {
                                            header.setTag(-AndroidUtilities.dp(100));
                                        }
                                        itemNum += count;
                                    }
                                }
                            }
                        } else if (sectionsType == SECTIONS_TYPE_DATE) {
                            pinnedHeaderShadowTargetAlpha = 0.0f;
                            if (sectionsAdapter.getItemCount() == 0) {
                                return;
                            }
                            int childCount = getChildCount();
                            int maxBottom = 0;
                            int minBottom = Integer.MAX_VALUE;
                            View minChild = null;

                            int minBottomSection = Integer.MAX_VALUE;
                            View minChildSection = null;
                            for (int a = 0; a < childCount; a++) {
                                View child = getChildAt(a);
                                int bottom = child.getBottom();
                                if (bottom <= sectionOffset + paddingTop) {
                                    continue;
                                }
                                if (bottom < minBottom) {
                                    minBottom = bottom;
                                    minChild = child;
                                }
                                maxBottom = Math.max(maxBottom, bottom);
                                if (bottom < sectionOffset + paddingTop + AndroidUtilities.dp(32)) {
                                    continue;
                                }
                                if (bottom < minBottomSection) {
                                    minBottomSection = bottom;
                                    minChildSection = child;
                                }
                            }
                            if (minChild == null) {
                                return;
                            }
                            ViewHolder holder = getChildViewHolder(minChild);
                            if (holder == null) {
                                return;
                            }
                            int firstVisibleItem = holder.getAdapterPosition();
                            int startSection = sectionsAdapter.getSectionForPosition(firstVisibleItem);
                            if (startSection < 0) {
                                return;
                            }
                            if (currentFirst != startSection || pinnedHeader == null) {
                                pinnedHeader = getSectionHeaderView(startSection, pinnedHeader);
                                pinnedHeader.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.UNSPECIFIED));
                                pinnedHeader.layout(0, 0, pinnedHeader.getMeasuredWidth(), pinnedHeader.getMeasuredHeight());
                                currentFirst = startSection;
                            }
                            if (pinnedHeader != null && minChildSection != null && minChildSection.getClass() != pinnedHeader.getClass()) {
                                pinnedHeaderShadowTargetAlpha = 1.0f;
                            }
                            int count = sectionsAdapter.getCountForSection(startSection);

                            int pos = sectionsAdapter.getPositionInSectionForPosition(firstVisibleItem);
                            int sectionOffsetY = maxBottom != 0 && maxBottom < (getMeasuredHeight() - getPaddingBottom()) ? 0 : sectionOffset;

                            if (pos == count - 1) {
                                int headerHeight = pinnedHeader.getHeight();
                                int headerTop = paddingTop;
                                if (minChild != null) {
                                    int available = minChild.getTop() - paddingTop - sectionOffset + minChild.getHeight();
                                    if (available < headerHeight) {
                                        headerTop = available - headerHeight;
                                    }
                                } else {
                                    headerTop = -AndroidUtilities.dp(100);
                                }
                                if (headerTop < 0) {
                                    pinnedHeader.setTag(paddingTop + sectionOffsetY + headerTop);
                                } else {
                                    pinnedHeader.setTag(paddingTop + sectionOffsetY);
                                }
                            } else {
                                pinnedHeader.setTag(paddingTop + sectionOffsetY);
                            }

                            invalidate();
                        }
                    } else {
                        int firstVisibleItem = linearLayoutManager.findFirstVisibleItemPosition();
                        int lastVisibleItem = linearLayoutManager.findLastVisibleItemPosition();
                        int visibleItemCount = Math.abs(lastVisibleItem - firstVisibleItem) + 1;


                        if (firstVisibleItem == NO_POSITION) {
                            return;
                        }
                        if ((scrollingByUser || force) && fastScroll != null && !fastScroll.isPressed()) {
                            Adapter adapter = getAdapter();

                            if (adapter instanceof FastScrollAdapter) {
                                float p = ((FastScrollAdapter) adapter).getScrollProgress(RecyclerListView.this);
                                boolean visible = ((FastScrollAdapter) adapter).fastScrollIsVisible(RecyclerListView.this);
                                fastScroll.setIsVisible(visible);
                                fastScroll.setProgress(Math.min(1.0f, p));
                                fastScroll.getCurrentLetter(false);
                            }
                        }
                    }
                }
            }
        }
    }

    public void setListSelectorColor(int color) {
        Theme.setSelectorDrawableColor(selectorDrawable, color, true);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        onItemClickListener = listener;
    }

    public void setOnItemClickListener(OnItemClickListenerExtended listener) {
        onItemClickListenerExtended = listener;
    }

    public OnItemClickListener getOnItemClickListener() {
        return onItemClickListener;
    }

    public void clickItem(View item, int position) {
        if (onItemClickListener != null) {
            onItemClickListener.onItemClick(item, position);
        } else if (onItemClickListenerExtended != null) {
            onItemClickListenerExtended.onItemClick(item, position, 0, 0);
        }
    }

    public boolean longClickItem(View item, int position) {
        if (onItemLongClickListener != null) {
            return onItemLongClickListener.onItemClick(item, position);
        } else if (onItemLongClickListenerExtended != null) {
            return onItemLongClickListenerExtended.onItemClick(item, position, 0, 0);
        }
        return false;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        setOnItemLongClickListener(listener, ViewConfiguration.getLongPressTimeout());
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener, long duration) {
        onItemLongClickListener = listener;
        gestureDetector.setIsLongpressEnabled(listener != null);
        gestureDetector.setLongpressDuration(duration);
    }

    public void setOnItemLongClickListener(OnItemLongClickListenerExtended listener) {
        setOnItemLongClickListener(listener, ViewConfiguration.getLongPressTimeout());
    }

    public void setOnItemLongClickListener(OnItemLongClickListenerExtended listener, long duration) {
        onItemLongClickListenerExtended = listener;
        gestureDetector.setIsLongpressEnabled(listener != null);
        gestureDetector.setLongpressDuration(duration);
    }

    public void setEmptyView(View view) {
        if (emptyView == view) {
            return;
        }
        if (emptyView != null) {
            emptyView.animate().setListener(null).cancel();
        }
        emptyView = view;
        if (animateEmptyView && emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }
        if (isHidden) {
            if (emptyView != null) {
                emptyViewAnimateToVisibility = GONE;
                emptyView.setVisibility(GONE);
            }
        } else {
            emptyViewAnimateToVisibility = -1;
            checkIfEmpty(updateEmptyViewAnimated());
        }
    }

    protected boolean updateEmptyViewAnimated() {
        return isAttachedToWindow();
    }

    public View getEmptyView() {
        return emptyView;
    }

    public void invalidateViews() {
        int count = getChildCount();
        for (int a = 0; a < count; a++) {
            getChildAt(a).invalidate();
        }
    }

    public void updateFastScrollColors() {
        if (fastScroll != null) {
            fastScroll.updateColors();
        }
    }

    public void setPinnedHeaderShadowDrawable(Drawable drawable) {
        pinnedHeaderShadowDrawable = drawable;
    }

    @Override
    public boolean canScrollVertically(int direction) {
        return scrollEnabled && super.canScrollVertically(direction);
    }

    public void setScrollEnabled(boolean value) {
        scrollEnabled = value;
    }

    public void highlightRow(RecyclerListView.IntReturnCallback callback) {
        highlightRowInternal(callback, 700, true);
    }

    public void highlightRow(RecyclerListView.IntReturnCallback callback, int removeAfter) {
        highlightRowInternal(callback, removeAfter, true);
    }

    private int highlightPosition;
    public void removeHighlightRow() {
        if (removeHighlighSelectionRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(removeHighlighSelectionRunnable);
            removeHighlighSelectionRunnable.run();
            removeHighlighSelectionRunnable = null;
            selectorView = null;
        } else {
            removeHighlighSelectionRunnable = null;
            pendingHighlightPosition = null;
            if (selectorView != null && highlightPosition != NO_POSITION) {
                positionSelector(highlightPosition, selectorView);
                selectorDrawable.setState(new int[]{});
                invalidateDrawable(selectorDrawable);
                selectorView = null;
                highlightPosition = NO_POSITION;
            } else {
                if (selectorDrawable != null) {
                    Drawable d = selectorDrawable.getCurrent();
                    if (d instanceof TransitionDrawable) {
                        ((TransitionDrawable) d).resetTransition();
                    }
                }
                if (selectorDrawable != null && selectorDrawable.isStateful()) {
                    if (selectorDrawable.setState(StateSet.NOTHING)) {
                        invalidateDrawable(selectorDrawable);
                    }
                }
            }
        }
    }

    private void highlightRowInternal(RecyclerListView.IntReturnCallback callback, int removeAfter, boolean canHighlightLater) {
        if (removeHighlighSelectionRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(removeHighlighSelectionRunnable);
            removeHighlighSelectionRunnable = null;
        }
        RecyclerView.ViewHolder holder = findViewHolderForAdapterPosition(callback.run());
        if (holder != null) {
            positionSelector(highlightPosition = holder.getLayoutPosition(), holder.itemView);
            if (selectorDrawable != null) {
                final Drawable d = selectorDrawable.getCurrent();
                if (d instanceof TransitionDrawable) {
                    if (onItemLongClickListener != null || onItemClickListenerExtended != null) {
                        ((TransitionDrawable) d).startTransition(ViewConfiguration.getLongPressTimeout());
                    } else {
                        ((TransitionDrawable) d).resetTransition();
                    }
                }
                if (Build.VERSION.SDK_INT >= 21) {
                    selectorDrawable.setHotspot(holder.itemView.getMeasuredWidth() / 2, holder.itemView.getMeasuredHeight() / 2);
                }
            }
            if (selectorDrawable != null && selectorDrawable.isStateful()) {
                if (selectorDrawable.setState(getDrawableStateForSelector())) {
                    invalidateDrawable(selectorDrawable);
                }
            }
            if (removeAfter > 0) {
                AndroidUtilities.runOnUIThread(removeHighlighSelectionRunnable = () -> {
                    removeHighlighSelectionRunnable = null;
                    pendingHighlightPosition = null;
                    if (selectorDrawable != null) {
                        Drawable d = selectorDrawable.getCurrent();
                        if (d instanceof TransitionDrawable) {
                            ((TransitionDrawable) d).resetTransition();
                        }
                    }
                    if (selectorDrawable != null && selectorDrawable.isStateful()) {
                        selectorDrawable.setState(StateSet.NOTHING);
                    }
                }, removeAfter);
            }
        } else if (canHighlightLater) {
            pendingHighlightPosition = callback;
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (!isEnabled()) {
            return false;
        }
        if (disallowInterceptTouchEvents) {
            requestDisallowInterceptTouchEvent(true);
        }
        return onInterceptTouchListener != null && onInterceptTouchListener.onInterceptTouchEvent(e) || super.onInterceptTouchEvent(e);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        FastScroll fastScroll = getFastScroll();
        if (fastScroll != null && fastScroll.isVisible && fastScroll.isMoving && (ev.getActionMasked() != MotionEvent.ACTION_UP && ev.getActionMasked() != MotionEvent.ACTION_CANCEL)) {
            return true;
        }
        if (sectionsAdapter != null && pinnedHeader != null && pinnedHeader.getAlpha() != 0 && pinnedHeader.dispatchTouchEvent(ev)) {
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    int emptyViewAnimateToVisibility;

    public void checkIfEmpty() {
        checkIfEmpty(updateEmptyViewAnimated());
    }

    private void checkIfEmpty(boolean animated) {
        if (isHidden) {
            return;
        }
        if (getAdapter() == null || emptyView == null) {
            if (hiddenByEmptyView && getVisibility() != VISIBLE) {
                setVisibility(VISIBLE);
                hiddenByEmptyView = false;
            }
            return;
        }
        boolean emptyViewVisible = emptyViewIsVisible();
        int newVisibility = emptyViewVisible ? VISIBLE : GONE;
        if (!animateEmptyView || !SharedConfig.animationsEnabled()) {
            animated = false;
        }
        if (animated) {
            if (emptyViewAnimateToVisibility != newVisibility) {
                emptyViewAnimateToVisibility = newVisibility;
                if (newVisibility == VISIBLE) {
                    emptyView.animate().setListener(null).cancel();
                    if (emptyView.getVisibility() == GONE) {
                        emptyView.setVisibility(VISIBLE);
                        emptyView.setAlpha(0);
                        if (emptyViewAnimationType == 1) {
                            emptyView.setScaleX(0.7f);
                            emptyView.setScaleY(0.7f);
                        }
                    }
                    emptyView.animate().alpha(1f).scaleX(1).scaleY(1).setDuration(150).start();
                } else {
                    if (emptyView.getVisibility() != GONE) {
                        ViewPropertyAnimator animator = emptyView.animate().alpha(0);
                        if (emptyViewAnimationType == 1) {
                            animator.scaleY(0.7f).scaleX(0.7f);
                        }
                        animator.setDuration(150).setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (emptyView != null) {
                                    emptyView.setVisibility(GONE);
                                }
                            }
                        }).start();
                    }
                }
            }
        } else {
            emptyViewAnimateToVisibility = newVisibility;
            emptyView.setVisibility(newVisibility);
            emptyView.setAlpha(1f);
        }
        if (hideIfEmpty) {
            newVisibility = emptyViewVisible ? INVISIBLE : VISIBLE;
            if (getVisibility() != newVisibility) {
                setVisibility(newVisibility);
            }
            hiddenByEmptyView = true;
        }
    }

    public boolean emptyViewIsVisible() {
        if (getAdapter() == null || isFastScrollAnimationRunning()) {
            return false;
        }
        return getAdapter().getItemCount() == 0;
    }

    public void hide() {
        if (isHidden) {
            return;
        }
        isHidden = true;
        if (getVisibility() != GONE) {
            setVisibility(GONE);
        }
        if (emptyView != null && emptyView.getVisibility() != GONE) {
            emptyView.setVisibility(GONE);
        }
    }

    public void show() {
        if (!isHidden) {
            return;
        }
        isHidden = false;
        checkIfEmpty(false);
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility != VISIBLE) {
            hiddenByEmptyView = false;
        }
    }

    @Override
    public void setOnScrollListener(OnScrollListener listener) {
        onScrollListener = listener;
    }

    public void setHideIfEmpty(boolean value) {
        hideIfEmpty = value;
    }

    public OnScrollListener getOnScrollListener() {
        return onScrollListener;
    }

    public void setOnInterceptTouchListener(OnInterceptTouchListener listener) {
        onInterceptTouchListener = listener;
    }

    public void setInstantClick(boolean value) {
        instantClick = value;
    }

    public void setDisallowInterceptTouchEvents(boolean value) {
        disallowInterceptTouchEvents = value;
    }

    public void setFastScrollEnabled(int type) {
        fastScroll = new FastScroll(getContext(), type);
        if (getParent() != null) {
            ((ViewGroup) getParent()).addView(fastScroll);
        }
    }

    public void setFastScrollVisible(boolean value) {
        if (fastScroll == null) {
            return;
        }
        fastScroll.setVisibility(value ? VISIBLE : GONE);
        fastScroll.isVisible = value;
    }

    public void setSectionsType(@SectionsType int type) {
        sectionsType = type;
        if (sectionsType == SECTIONS_TYPE_STICKY_HEADERS || sectionsType == SECTIONS_TYPE_FAST_SCROLL_ONLY) {
            headers = new ArrayList<>();
            headersCache = new ArrayList<>();
        }
    }

    public void setPinnedSectionOffsetY(int offset) {
        sectionOffset = offset;
        invalidate();
    }

    private void positionSelector(int position, View sel) {
        positionSelector(position, sel, false, -1, -1);
    }

    public void updateSelector() {
        if (selectorPosition != NO_POSITION && selectorView != null) {
            positionSelector(selectorPosition, selectorView);
            invalidate();
        }
    }

    private void positionSelector(int position, View sel, boolean manageHotspot, float x, float y) {
        if (removeHighlighSelectionRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(removeHighlighSelectionRunnable);
            removeHighlighSelectionRunnable = null;
            pendingHighlightPosition = null;
        }
        if (selectorDrawable == null) {
            return;
        }
        final boolean positionChanged = position != selectorPosition;
        int bottomPadding;
        if (getAdapter() instanceof SelectionAdapter) {
            bottomPadding = ((SelectionAdapter) getAdapter()).getSelectionBottomPadding(sel);
        } else {
            bottomPadding = 0;
        }
        if (position != NO_POSITION) {
            selectorPosition = position;
        }
        selectorView = sel;
        if (selectorType == 8) {
            Theme.setMaskDrawableRad(selectorDrawable, selectorRadius, 0);
        } else if (topBottomSelectorRadius > 0 && getAdapter() != null) {
            Theme.setMaskDrawableRad(selectorDrawable, position == 0 ? topBottomSelectorRadius : 0, position == getAdapter().getItemCount() - 2 ? topBottomSelectorRadius : 0);
        }
        selectorRect.set(sel.getLeft(), sel.getTop(), sel.getRight(), sel.getBottom() - bottomPadding);
        selectorRect.offset((int) sel.getTranslationX(), (int) sel.getTranslationY());

        final boolean enabled = sel.isEnabled();
        if (isChildViewEnabled != enabled) {
            isChildViewEnabled = enabled;
        }

        if (positionChanged) {
            selectorDrawable.setVisible(false, false);
            selectorDrawable.setState(StateSet.NOTHING);
        }
        selectorDrawable.setBounds(selectorRect);
        if (positionChanged) {
            if (getVisibility() == VISIBLE) {
                selectorDrawable.setVisible(true, false);
            }
        }
        if (Build.VERSION.SDK_INT >= 21 && manageHotspot) {
            selectorDrawable.setHotspot(x, y);
        }
    }

    public void setAllowItemsInteractionDuringAnimation(boolean value) {
        allowItemsInteractionDuringAnimation = value;
    }

    public void hideSelector(boolean animated) {
        if (currentChildView != null) {
            View child = currentChildView;
            onChildPressed(currentChildView, 0, 0, false);
            currentChildView = null;
            if (animated) {
                removeSelection(child, null);
            }
        }
        if (!animated) {
            selectorDrawable.setState(StateSet.NOTHING);
            selectorRect.setEmpty();
        }
    }

    private void updateSelectorState() {
        if (selectorDrawable != null && selectorDrawable.isStateful()) {
            if (currentChildView != null) {
                if (selectorDrawable.setState(getDrawableStateForSelector())) {
                    invalidateDrawable(selectorDrawable);
                }
            } else if (removeHighlighSelectionRunnable == null) {
                selectorDrawable.setState(StateSet.NOTHING);
            }
        }
    }

    private int[] getDrawableStateForSelector() {
        final int[] state = onCreateDrawableState(1);
        state[state.length - 1] = android.R.attr.state_pressed;
        return state;
    }

    @Override
    public void onChildAttachedToWindow(View child) {
        if (getAdapter() instanceof SelectionAdapter) {
            ViewHolder holder = findContainingViewHolder(child);
            if (holder != null) {
                child.setEnabled(((SelectionAdapter) getAdapter()).isEnabled(holder));
                if (accessibilityEnabled) {
                    child.setAccessibilityDelegate(accessibilityDelegate);
                }
            }
        } else {
            child.setEnabled(false);
            child.setAccessibilityDelegate(null);
        }
        super.onChildAttachedToWindow(child);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        updateSelectorState();
    }

    @Override
    public boolean verifyDrawable(Drawable drawable) {
        return selectorDrawable == drawable || super.verifyDrawable(drawable);
    }

    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (selectorDrawable != null) {
            selectorDrawable.jumpToCurrentState();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (fastScroll != null && fastScroll.getParent() != getParent()) {
            ViewGroup parent = (ViewGroup) fastScroll.getParent();
            if (parent != null) {
                parent.removeView(fastScroll);
            }
            parent = (ViewGroup) getParent();
            parent.addView(fastScroll);
        }
    }

    @Override
    public void setAdapter(Adapter adapter) {
        final Adapter oldAdapter = getAdapter();
        if (oldAdapter != null) {
            oldAdapter.unregisterAdapterDataObserver(observer);
        }
        if (headers != null) {
            headers.clear();
            headersCache.clear();
        }
        currentFirst = -1;
        selectorPosition = NO_POSITION;
        selectorView = null;
        selectorRect.setEmpty();
        pinnedHeader = null;
        if (adapter instanceof SectionsAdapter) {
            sectionsAdapter = (SectionsAdapter) adapter;
        } else {
            sectionsAdapter = null;
        }
        super.setAdapter(adapter);
        if (adapter != null) {
            adapter.registerAdapterDataObserver(observer);
        }
        checkIfEmpty(false);
    }

    @Override
    public void stopScroll() {
        try {
            super.stopScroll();
        } catch (NullPointerException ignore) {

        }
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow, int type) {
        if (longPressCalled) {
            if (onItemLongClickListenerExtended != null) {
                onItemLongClickListenerExtended.onMove(dx, dy);
            }
            consumed[0] = dx;
            consumed[1] = dy;
            return true;
        }
        return super.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type);
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private View getSectionHeaderView(int section, View oldView) {
        boolean shouldLayout = oldView == null;
        View view = sectionsAdapter.getSectionHeaderView(section, oldView);
        if (shouldLayout) {
            ensurePinnedHeaderLayout(view, false);
        }
        return view;
    }

    private void ensurePinnedHeaderLayout(View header, boolean forceLayout) {
        if (header == null) {
            return;
        }
        if (header.isLayoutRequested() || forceLayout) {
            if (sectionsType == SECTIONS_TYPE_STICKY_HEADERS) {
                ViewGroup.LayoutParams layoutParams = header.getLayoutParams();
                int heightSpec = MeasureSpec.makeMeasureSpec(layoutParams.height, MeasureSpec.EXACTLY);
                int widthSpec = MeasureSpec.makeMeasureSpec(layoutParams.width, MeasureSpec.EXACTLY);
                try {
                    header.measure(widthSpec, heightSpec);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else if (sectionsType == SECTIONS_TYPE_DATE) {
                int widthSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY);
                int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
                try {
                    header.measure(widthSpec, heightSpec);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            header.layout(0, 0, header.getMeasuredWidth(), header.getMeasuredHeight());
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (overlayContainer != null) {
            overlayContainer.requestLayout();
        }
        if (sectionsType == SECTIONS_TYPE_STICKY_HEADERS) {
            if (sectionsAdapter == null || headers.isEmpty()) {
                return;
            }
            for (int a = 0; a < headers.size(); a++) {
                View header = headers.get(a);
                ensurePinnedHeaderLayout(header, true);
            }
        } else if (sectionsType == SECTIONS_TYPE_DATE) {
            if (sectionsAdapter == null || pinnedHeader == null) {
                return;
            }
            ensurePinnedHeaderLayout(pinnedHeader, true);
        }
    }

    public Rect getSelectorRect() {
        return selectorRect;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (itemsEnterAnimator != null) {
            itemsEnterAnimator.dispatchDraw();
        }

        if (drawSelectorBehind && !selectorRect.isEmpty()) {
            selectorDrawable.setBounds(selectorRect);
            canvas.save();
            if (selectorTransformer != null) {
                selectorTransformer.accept(canvas);
            }
            selectorDrawable.draw(canvas);
            canvas.restore();
        }
        super.dispatchDraw(canvas);
        if (!drawSelectorBehind && !selectorRect.isEmpty()) {
            selectorDrawable.setBounds(selectorRect);
            canvas.save();
            if (selectorTransformer != null) {
                selectorTransformer.accept(canvas);
            }
            selectorDrawable.draw(canvas);
            canvas.restore();
        }
        if (overlayContainer != null) {
            overlayContainer.draw(canvas);
        }
        if (sectionsType == SECTIONS_TYPE_STICKY_HEADERS) {
            if (sectionsAdapter != null && !headers.isEmpty()) {
                for (int a = 0; a < headers.size(); a++) {
                    View header = headers.get(a);
                    int saveCount = canvas.save();
                    int top = (Integer) header.getTag();
                    canvas.translate(LocaleController.isRTL ? getWidth() - header.getWidth() : 0, top);
                    canvas.clipRect(0, 0, getWidth(), header.getMeasuredHeight());
                    header.draw(canvas);
                    canvas.restoreToCount(saveCount);
                }
            }
        } else if (sectionsType == SECTIONS_TYPE_DATE) {
            if (sectionsAdapter != null && pinnedHeader != null && pinnedHeader.getAlpha() != 0) {
                int saveCount = canvas.save();
                int top = (Integer) pinnedHeader.getTag();
                canvas.translate(LocaleController.isRTL ? getWidth() - pinnedHeader.getWidth() : 0, top);
                if (pinnedHeaderShadowDrawable != null) {
                    pinnedHeaderShadowDrawable.setBounds(0, pinnedHeader.getMeasuredHeight(), getWidth(), pinnedHeader.getMeasuredHeight() + pinnedHeaderShadowDrawable.getIntrinsicHeight());
                    pinnedHeaderShadowDrawable.setAlpha((int) (255 * pinnedHeaderShadowAlpha));
                    pinnedHeaderShadowDrawable.draw(canvas);

                    long newTime = SystemClock.elapsedRealtime();
                    long dt = Math.min(20, newTime - lastAlphaAnimationTime);
                    lastAlphaAnimationTime = newTime;
                    if (pinnedHeaderShadowAlpha < pinnedHeaderShadowTargetAlpha) {
                        pinnedHeaderShadowAlpha += dt / 180.0f;
                        if (pinnedHeaderShadowAlpha > pinnedHeaderShadowTargetAlpha) {
                            pinnedHeaderShadowAlpha = pinnedHeaderShadowTargetAlpha;
                        }
                        invalidate();
                    } else if (pinnedHeaderShadowAlpha > pinnedHeaderShadowTargetAlpha) {
                        pinnedHeaderShadowAlpha -= dt / 180.0f;
                        if (pinnedHeaderShadowAlpha < pinnedHeaderShadowTargetAlpha) {
                            pinnedHeaderShadowAlpha = pinnedHeaderShadowTargetAlpha;
                        }
                        invalidate();
                    }
                }
                canvas.clipRect(0, 0, getWidth(), pinnedHeader.getMeasuredHeight());
                pinnedHeader.draw(canvas);
                canvas.restoreToCount(saveCount);
            }
        }
    }

    public void relayoutPinnedHeader() {
        if (pinnedHeader != null) {
            pinnedHeader.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.UNSPECIFIED));
            pinnedHeader.layout(0, 0, pinnedHeader.getMeasuredWidth(), pinnedHeader.getMeasuredHeight());
            invalidate();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        selectorPosition = NO_POSITION;
        selectorView = null;
        selectorRect.setEmpty();
        if (itemsEnterAnimator != null) {
            itemsEnterAnimator.onDetached();
        }

        if (stoppedAllHeavyOperations) {
            stoppedAllHeavyOperations = false;
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 512);
        }
    }

    public void addOverlayView(View view, FrameLayout.LayoutParams layoutParams) {
        if (overlayContainer == null) {
            overlayContainer = new FrameLayout(getContext()) {
                @Override
                public void requestLayout() {
                    super.requestLayout();
                    try {
                        final int measuredWidth = RecyclerListView.this.getMeasuredWidth();
                        final int measuredHeight = RecyclerListView.this.getMeasuredHeight();
                        measure(MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(measuredHeight, MeasureSpec.EXACTLY));
                        layout(0, 0, overlayContainer.getMeasuredWidth(), overlayContainer.getMeasuredHeight());
                    } catch (Exception ignored) {
                    }
                }
            };
        }
        overlayContainer.addView(view, layoutParams);
    }

    public void removeOverlayView(View view) {
        if (overlayContainer != null) {
            overlayContainer.removeView(view);
        }
    }

    public ArrayList<View> getHeaders() {
        return headers;
    }

    public ArrayList<View> getHeadersCache() {
        return headersCache;
    }

    public View getPinnedHeader() {
        return pinnedHeader;
    }

    public boolean isFastScrollAnimationRunning() {
        return fastScrollAnimationRunning;
    }

    @Override
    public void requestLayout() {
        if (fastScrollAnimationRunning) {
            return;
        }
        super.requestLayout();
    }

    public void setAnimateEmptyView(boolean animate, int emptyViewAnimationType) {
        animateEmptyView = animate;
        this.emptyViewAnimationType = emptyViewAnimationType;
    }

    public static class FoucsableOnTouchListener implements OnTouchListener {
        private float x;
        private float y;
        private boolean onFocus;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            ViewParent parent = v.getParent();
            if (parent == null) {
                return false;
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                x = event.getX();
                y = event.getY();
                onFocus = true;
                parent.requestDisallowInterceptTouchEvent(true);
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                float dx = (x - event.getX());
                float dy = (y - event.getY());
                float touchSlop = ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
                if (onFocus && Math.sqrt(dx * dx + dy * dy) > touchSlop) {
                    onFocus = false;
                    parent.requestDisallowInterceptTouchEvent(false);
                }
            } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                onFocus = false;
                parent.requestDisallowInterceptTouchEvent(false);
            }
            return false;
        }
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        if (fastScroll != null) {
            fastScroll.setTranslationY(translationY);
        }
    }

    public void startMultiselect(int positionFrom, boolean useRelativePositions, onMultiSelectionChanged multiSelectionListener) {
        if (!multiSelectionGesture) {
            listPaddings = new int[2];
            selectedPositions = new HashSet<>();

            getParent().requestDisallowInterceptTouchEvent(true);

            this.multiSelectionListener = multiSelectionListener;
            multiSelectionGesture = true;
            startSelectionFrom = currentSelectedPosition = positionFrom;
        }
        this.useRelativePositions = useRelativePositions;
    }


    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (fastScroll != null && fastScroll.pressed) {
            return false;
        }
        if (multiSelectionGesture && e.getAction() != MotionEvent.ACTION_DOWN && e.getAction() != MotionEvent.ACTION_UP && e.getAction() != MotionEvent.ACTION_CANCEL) {
            if (lastX == Float.MAX_VALUE && lastY == Float.MAX_VALUE) {
                lastX = e.getX();
                lastY = e.getY();
            }
            if (!multiSelectionGestureStarted && Math.abs(e.getY() - lastY) > touchSlop) {
                multiSelectionGestureStarted = true;
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            if (multiSelectionGestureStarted) {
                chekMultiselect(e.getX(), e.getY());
                multiSelectionListener.getPaddings(listPaddings);
                if (e.getY() > getMeasuredHeight() - AndroidUtilities.dp(56) - listPaddings[1] && !(currentSelectedPosition < startSelectionFrom && multiSelectionListener.limitReached())) {
                    startMultiselectScroll(false);
                } else if (e.getY() < AndroidUtilities.dp(56) + listPaddings[0] && !(currentSelectedPosition > startSelectionFrom && multiSelectionListener.limitReached())) {
                    startMultiselectScroll(true);
                } else {
                    cancelMultiselectScroll();
                }
            }
            return true;
        }
        lastX = Float.MAX_VALUE;
        lastY = Float.MAX_VALUE;
        multiSelectionGesture = false;
        multiSelectionGestureStarted = false;
        getParent().requestDisallowInterceptTouchEvent(false);
        cancelMultiselectScroll();
        return super.onTouchEvent(e);
    }

    private boolean chekMultiselect(float x, float y) {
        y = Math.min(getMeasuredHeight() - listPaddings[1], Math.max(y, listPaddings[0]));
        x = Math.min(getMeasuredWidth(), Math.max(x, 0));
        for (int i = 0; i < getChildCount(); i++) {
            multiSelectionListener.getPaddings(listPaddings);
            if (useRelativePositions) {

            } else {
                View child = getChildAt(i);
                AndroidUtilities.rectTmp.set(child.getLeft(), child.getTop(), child.getLeft() + child.getMeasuredWidth(), child.getTop() + child.getMeasuredHeight());

                if (AndroidUtilities.rectTmp.contains(x, y)) {
                    int position = getChildLayoutPosition(child);

                    if (currentSelectedPosition != position) {
                        boolean selectionFromTop = currentSelectedPosition > startSelectionFrom || position > startSelectionFrom;
                        position = multiSelectionListener.checkPosition(position, selectionFromTop);

                        if (selectionFromTop) {
                            if (position > currentSelectedPosition) {
                                if (!multiSelectionListener.limitReached()) {
                                    for (int k = currentSelectedPosition + 1; k <= position; k++) {
                                        if (k == startSelectionFrom) {
                                            continue;
                                        }
                                        if (multiSelectionListener.canSelect(k)) {
                                            multiSelectionListener.onSelectionChanged(k, true, x, y);
                                        }
                                    }
                                }
                            } else {
                                for (int k = currentSelectedPosition; k > position; k--) {
                                    if (k == startSelectionFrom) {
                                        continue;
                                    }
                                    if (multiSelectionListener.canSelect(k)) {
                                        multiSelectionListener.onSelectionChanged(k, false, x, y);
                                    }
                                }
                            }
                        } else {
                            if (position > currentSelectedPosition) {
                                for (int k = currentSelectedPosition; k < position; k++) {
                                    if (k == startSelectionFrom) {
                                        continue;
                                    }
                                    if (multiSelectionListener.canSelect(k)) {
                                        multiSelectionListener.onSelectionChanged(k, false, x, y);
                                    }
                                }
                            } else {
                                if (!multiSelectionListener.limitReached()) {
                                    for (int k = currentSelectedPosition - 1; k >= position; k--) {
                                        if (k == startSelectionFrom) {
                                            continue;
                                        }
                                        if (multiSelectionListener.canSelect(k)) {
                                            multiSelectionListener.onSelectionChanged(k, true, x, y);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (!multiSelectionListener.limitReached()) {
                        currentSelectedPosition = position;
                    }
                    break;
                }
            }

        }
        return true;
    }

    private void cancelMultiselectScroll() {
        multiselectScrollRunning = false;
        AndroidUtilities.cancelRunOnUIThread(scroller);
    }

    Runnable scroller = new Runnable() {
        @Override
        public void run() {
            int dy;
            multiSelectionListener.getPaddings(listPaddings);
            if (multiselectScrollToTop) {
                dy = -AndroidUtilities.dp(12f);
                chekMultiselect(0, listPaddings[0]);
            } else {
                dy = AndroidUtilities.dp(12f);
                chekMultiselect(0, getMeasuredHeight() - listPaddings[1]);
            }
            multiSelectionListener.scrollBy(dy);
            if (multiselectScrollRunning) {
                AndroidUtilities.runOnUIThread(scroller);
            }
        }
    };

    private void startMultiselectScroll(boolean top) {
        multiselectScrollToTop = top;
        if (!multiselectScrollRunning) {
            multiselectScrollRunning = true;
            AndroidUtilities.cancelRunOnUIThread(scroller);
            AndroidUtilities.runOnUIThread(scroller);
        }
    }

    public boolean isMultiselect() {
        return multiSelectionGesture;
    }

    protected int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }

    protected Drawable getThemedDrawable(String key) {
        Drawable drawable = resourcesProvider != null ? resourcesProvider.getDrawable(key) : null;
        return drawable != null ? drawable : Theme.getThemeDrawable(key);
    }

    protected Paint getThemedPaint(String paintKey) {
        Paint paint = resourcesProvider != null ? resourcesProvider.getPaint(paintKey) : null;
        return paint != null ? paint : Theme.getThemePaint(paintKey);
    }

    public interface onMultiSelectionChanged {
        void onSelectionChanged(int position, boolean selected, float x, float y);
        boolean canSelect(int position);
        int checkPosition(int position, boolean selectionFromTop);
        boolean limitReached();
        void getPaddings(int paddings[]);
        void scrollBy(int dy);
    }

    public void setItemsEnterAnimator(RecyclerItemsEnterAnimator itemsEnterAnimator) {
        this.itemsEnterAnimator = itemsEnterAnimator;
    }

    public void setAccessibilityEnabled(boolean accessibilityEnabled) {
        this.accessibilityEnabled = accessibilityEnabled;
    }

    public void setAllowStopHeaveOperations(boolean allowStopHeaveOperations) {
        this.allowStopHeaveOperations = allowStopHeaveOperations;
    }
}