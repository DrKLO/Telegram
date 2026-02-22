/*
 * This is the source code of Telegram for Android v. 5.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.ui.ActionBar.Theme.multAlpha;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
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
import android.util.Pair;
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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.util.Consumer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.GenericProvider;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.capture.IBlur3Capture;
import org.telegram.ui.Components.blur3.capture.IBlur3Hash;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundProvider;
import org.telegram.ui.FiltersSetupActivity;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("JavaReflectionMemberAccess")
public class RecyclerListView extends RecyclerView implements IBlur3Capture {
    public final static int SECTIONS_TYPE_SIMPLE = 0,
            SECTIONS_TYPE_STICKY_HEADERS = 1,
            SECTIONS_TYPE_DATE = 2,
            SECTIONS_TYPE_FAST_SCROLL_ONLY = 3;
    private boolean drawSelection = true;

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
    @Nullable
    protected Drawable selectorDrawable;
    protected int selectorPosition;
    protected View selectorView;
    protected android.graphics.Rect selectorRect = new android.graphics.Rect();
    private boolean isChildViewEnabled;
    private int translateSelector = -1;

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

        public void cleanupCache() {
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
            ArrayList<Integer> oldHashes = new ArrayList<>(hashes);
            updateHashes();

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

        public void updateHashes() {
            cleanupCache();

            hashes.clear();

            for (int i = 0, N = internalGetSectionCount(); i < N; i++) {
                int count = internalGetCountForSection(i);
                for (int j = 0; j < count; ++j) {
                    hashes.add(getHash(i, j));
                }
            }
        }

        public int getHash(int section, int position) {
            return Objects.hash(section * -49612, getItem(section, position));
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
        public boolean usePadding = true;

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
        public boolean isVisible;
        float touchSlop;
        Drawable fastScrollShadowDrawable;
        Drawable fastScrollBackgroundDrawable;

        BlurredBackgroundDrawable blurredCircleDrawable;
        BlurredBackgroundDrawable blurredTagDrawable;

        boolean isRtl;
        public int topOffset;

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
                letterPaint.setTypeface(AndroidUtilities.bold());
                paint2.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
                fastScrollBackgroundDrawable = ContextCompat.getDrawable(context, R.drawable.calendar_date).mutate();
                fastScrollBackgroundDrawable.setColorFilter(new PorterDuffColorFilter(ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), Color.WHITE, 0.1f), PorterDuff.Mode.MULTIPLY));
            }
            for (int a = 0; a < 8; a++) {
                radii[a] = AndroidUtilities.dp(44);
            }

            scrollX = isRtl ? dp(10) : dp((type == LETTER_TYPE ? 132 : 240) - 15);
            if (hasSections()) {
                scrollX += dp(isRtl ? -4 : 6);
            }
            updateColors();
            setFocusableInTouchMode(true);
            ViewConfiguration vc = ViewConfiguration.get(context);
            touchSlop = vc.getScaledTouchSlop();
            fastScrollShadowDrawable = ContextCompat.getDrawable(context, R.drawable.fast_scroll_shadow);
        }

        private void updateColors() {
            inactiveColor = type == LETTER_TYPE ? Theme.getColor(Theme.key_fastScrollInactive, resourcesProvider) : ColorUtils.setAlphaComponent(Color.BLACK, (int) (255 * 0.4f));
            activeColor = Theme.getColor(Theme.key_fastScrollActive, resourcesProvider);
            paint.setColor(inactiveColor);

            if (type == LETTER_TYPE) {
                letterPaint.setColor(Theme.getColor(Theme.key_fastScrollText, resourcesProvider));
            } else {
                letterPaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            }
            invalidate();
        }

        float startY;
        boolean isMoving;
        long startTime;
        float visibilityAlpha;
        float viewAlpha = 1f;

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
            int topPadding = usePadding ? getPaddingTop() : 0;
            int y = topPadding + (int) Math.ceil((getMeasuredHeight() - topPadding - AndroidUtilities.dp(24 + 30)) * progress);
            rect.set(scrollX, AndroidUtilities.dp(12) + y, scrollX + AndroidUtilities.dp(5), AndroidUtilities.dp(12 + 30) + y);
            if (type == LETTER_TYPE) {
                paint.setColor(ColorUtils.blendARGB(inactiveColor, activeColor, bubbleProgress));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), paint);
            } else {
                paint.setColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), Color.WHITE, 0.1f));

                float cy = y + AndroidUtilities.dp(12 + 15);
                if (blurredCircleDrawable != null) {
                    blurredCircleDrawable.setBounds(
                            scrollX + AndroidUtilities.dp(8 - 28),
                            y + AndroidUtilities.dp(12 + 15 - 28),
                            scrollX + AndroidUtilities.dp(8 + 28),
                            y + AndroidUtilities.dp(12 + 15 + 28));
                    blurredCircleDrawable.draw(canvas);
                } else {
                    fastScrollShadowDrawable.setBounds(getMeasuredWidth() - fastScrollShadowDrawable.getIntrinsicWidth(), (int) (cy - fastScrollShadowDrawable.getIntrinsicHeight() / 2), getMeasuredWidth(), (int) (cy + fastScrollShadowDrawable.getIntrinsicHeight() / 2));
                    fastScrollShadowDrawable.draw(canvas);
                    canvas.drawCircle(scrollX + AndroidUtilities.dp(8), y + AndroidUtilities.dp(12 + 15), AndroidUtilities.dp(24), paint);
                }

                paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
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

                    if (blurredTagDrawable != null) {
                        rect.round(AndroidUtilities.rectTmp2);
                        AndroidUtilities.rectTmp2.inset(-dp(4), -dp(4));
                        blurredTagDrawable.setBounds(AndroidUtilities.rectTmp2);
                        blurredTagDrawable.draw(canvas);
                    } else {
                        fastScrollBackgroundDrawable.setBounds((int) rect.left, (int) rect.top, (int) rect.right, (int) rect.bottom);
                        fastScrollBackgroundDrawable.setAlpha((int) (255 * floatingDateProgress));
                        fastScrollBackgroundDrawable.draw(canvas);
                    }

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


        public void applyBlurDrawables(
                BlurredBackgroundDrawableViewFactory factory,
                BlurredBackgroundProvider backgroundProvider
        ) {
            blurredCircleDrawable = factory.create(fastScroll, backgroundProvider);
            blurredCircleDrawable.setPadding(dp(4));
            blurredCircleDrawable.setRadius(dp(24));

            blurredTagDrawable = factory.create(fastScroll, backgroundProvider);
            blurredTagDrawable.setPadding(dp(6));
            blurredTagDrawable.setRadius(dp(14));
        }

        public boolean fillDrawablesRect(RectF rect) {
            if (blurredCircleDrawable != null || blurredTagDrawable != null)  {
                rect.set(blurredTagDrawable.getBounds());
                AndroidUtilities.rectTmp.set(blurredCircleDrawable.getBounds());
                rect.union(AndroidUtilities.rectTmp);
                return true;
            }
            return false;
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
                            try {
                                view.playSoundEffect(SoundEffectConstants.CLICK);
                            } catch (Exception ignore) {}
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
                                        try {
                                            view.playSoundEffect(SoundEffectConstants.CLICK);
                                        } catch (Exception ignore) {}
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
                            try {
                                child.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            } catch (Exception ignored) {}
                            child.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
                        }
                    } else {
                        if (onItemLongClickListenerExtended.onItemClick(currentChildView, currentChildPosition, event.getX() - currentChildView.getX(), event.getY() - currentChildView.getY())) {
                            try {
                                child.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            } catch (Exception ignored) {}
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
                public boolean hasDoubleTap(MotionEvent e) {
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
                                // todo: recursion search ???

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
                if ((child instanceof ChatMessageCell || child instanceof ChatActionCell) && child.getVisibility() == View.INVISIBLE) continue;
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

    private final AdapterDataObserver observer = new AdapterDataObserver() {
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
        this.edgeEffectTrackerFactory = new EdgeEffectTrackerFactory();

        setEdgeEffectFactory(edgeEffectTrackerFactory);
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
                    if (selectorDrawable != null) {
                        selectorDrawable.setBounds(selectorRect);
                    }
                    invalidate();
                } else {
                    selectorRect.setEmpty();
                }
                checkSection(false);
                if (dy != 0 && fastScroll != null) {
                    fastScroll.showFloatingDate();
                }
                if (pendingHighlightPosition != null) {
                    highlightRowInternal(pendingHighlightPosition, 700, false);
                }
            }
        });
        addOnItemTouchListener(new RecyclerListViewItemClickListener(context));
    }

    private Paint backgroundPaint;
    protected void drawSectionBackground(Canvas canvas, int fromAdapterPosition, int toAdapterPosition, int color) {
        drawSectionBackground(canvas, fromAdapterPosition, toAdapterPosition, color, 0, 0);
    }
    protected void drawSectionBackground(Canvas canvas, int fromAdapterPosition, int toAdapterPosition, int color, int topMargin, int bottomMargin) {
        if (toAdapterPosition < fromAdapterPosition || fromAdapterPosition < 0 || toAdapterPosition < 0) {
            return;
        }

        int top = Integer.MAX_VALUE;
        int bottom = Integer.MIN_VALUE;

        for (int i = 0; i < getChildCount(); ++i) {
            View child = getChildAt(i);
            if (child == null) {
                continue;
            }
            int position = getChildAdapterPosition(child);
            int y = (int) child.getTop();
            if (position >= fromAdapterPosition && position <= toAdapterPosition) {
                top = Math.min(y, top);
                bottom = Math.max((int) (y + child.getHeight() * child.getAlpha()), bottom);
            }
        }

        if (top < bottom) {
            if (backgroundPaint == null) {
                backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            }
            backgroundPaint.setColor(color);
            canvas.drawRect(0, top - topMargin, getWidth(), bottom + bottomMargin, backgroundPaint);
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
            int topPadding = fastScroll.usePadding ? getPaddingTop() : fastScroll.topOffset;
            int height = getMeasuredHeight() - topPadding - getPaddingBottom();
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
            int topPadding = fastScroll.usePadding ? getPaddingTop() : fastScroll.topOffset;
            t += topPadding;
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
        } else if (selectorType == 9) {
            selectorDrawable = null;
        } else if (topBottomSelectorRadius > 0) {
            selectorDrawable = Theme.createRadSelectorDrawable(color, topBottomSelectorRadius, topBottomSelectorRadius);
        } else if (selectorRadius > 0 && selectorType != Theme.RIPPLE_MASK_CIRCLE_20DP) {
            selectorDrawable = Theme.createSimpleSelectorRoundRectDrawable(selectorRadius, 0, color, 0xff000000);
        } else if (selectorType == 2) {
            selectorDrawable = Theme.getSelectorDrawable(color, false);
        } else {
            selectorDrawable = Theme.createSelectorDrawable(color, selectorType, selectorRadius);
        }
        if (selectorDrawable != null) {
            selectorDrawable.setCallback(this);
        }
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
                        int paddingTop = sectionsType == SECTIONS_TYPE_STICKY_HEADERS ? 0 : getPaddingTop();
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

    public void setListSelectorColor(Integer color) {
        Theme.setSelectorDrawableColor(selectorDrawable, color == null ? getThemedColor(hasSections() ? Theme.key_settings_listSelector : Theme.key_listSelector) : color, true);
    }

    private GenericProvider<Integer, Integer> getSelectorColor;
    public Integer getSelectorColor(int position) {
        if (getSelectorColor != null) {
            return getSelectorColor.provide(position);
        }
        return null;
    }

    public void setItemSelectorColorProvider(GenericProvider<Integer, Integer> getSelectorColor) {
        this.getSelectorColor = getSelectorColor;
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
            checkIfEmpty(false);
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
            View child = getChildAt(a);
            if (child instanceof Theme.Colorable) {
                ((Theme.Colorable) child).updateColors();
            }
            child.invalidate();
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
                if (selectorDrawable != null) {
                    selectorDrawable.setState(new int[]{});
                    invalidateDrawable(selectorDrawable);
                }
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
            positionSelector(highlightPosition = holder.getLayoutPosition(), holder.itemView, false, -1, -1, true);
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
                pendingHighlightPosition = null;
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
            requestDisallowInterceptTouchEvent(this, true);
        }
        return onInterceptTouchListener != null && onInterceptTouchListener.onInterceptTouchEvent(e) || super.onInterceptTouchEvent(e);
    }

    private int activeTouches;
    private boolean adaptiveOverScroll;

    public void setAdaptiveOverScroll() {
        adaptiveOverScroll = true;
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            if (activeTouches == 0 && adaptiveOverScroll) {
                setOverScrollMode(OVER_SCROLL_ALWAYS);
            }
            activeTouches++;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            activeTouches--;
            if (activeTouches == 0 && adaptiveOverScroll) {
                setOverScrollMode(OVER_SCROLL_NEVER);
            }
        }

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
        emptyViewUpdated(emptyViewVisible, animated);
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

    protected void emptyViewUpdated(boolean shown, boolean animated) {

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
        positionSelector(position, sel, false, -1, -1, false);
    }

    public void updateSelector() {
        if (selectorPosition != NO_POSITION && selectorView != null) {
            positionSelector(selectorPosition, selectorView);
            invalidate();
        }
    }

    private void positionSelector(int position, View sel, boolean manageHotspot, float x, float y, boolean highlight) {
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
//        selectorRect.offset((int) sel.getTranslationX(), (int) sel.getTranslationY());

        final boolean enabled = sel.isEnabled();
        if (isChildViewEnabled != enabled) {
            isChildViewEnabled = enabled;
        }

        if (positionChanged) {
            selectorDrawable.setVisible(false, false);
            selectorDrawable.setState(StateSet.NOTHING);
        }
        setListSelectorColor(getSelectorColor(position));
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

    public void setTranslateSelector(boolean value) {
        translateSelector = value ? -2 : -1;
    }

    public void setTranslateSelectorPosition(int position) {
        translateSelector = position <= 0 ? -1 : position;
    }

    private void drawSelectors2(Canvas canvas) {
        if (selectorRect.isEmpty() || selectorDrawable == null) {
            return;
        }

        if ((translateSelector == -2 || translateSelector == selectorPosition) && selectorView != null) {
            int bottomPadding;
            if (getAdapter() instanceof SelectionAdapter) {
                bottomPadding = ((SelectionAdapter) getAdapter()).getSelectionBottomPadding(selectorView);
            } else {
                bottomPadding = 0;
            }
            selectorDrawable.setBounds(selectorView.getLeft(), selectorView.getTop(), selectorView.getRight(), selectorView.getBottom() - bottomPadding);
        } else {
            selectorDrawable.setBounds(selectorRect);
        }
        canvas.save();
        if ((translateSelector == -2 || translateSelector == selectorPosition) && selectorTransformer != null) {
            selectorTransformer.accept(canvas);
        }
        if ((translateSelector == -2 || translateSelector == selectorPosition) && selectorView != null) {
            canvas.translate(selectorView.getX() - selectorRect.left, selectorView.getY() - selectorRect.top);
            selectorDrawable.setAlpha((int) (0xFF * selectorView.getAlpha()));
        }
        drawSelector(canvas);
        canvas.restore();
    }

    private void drawSelector(Canvas canvas) {
        if (hasSections()) {
            canvas.save();
            clipChild(canvas, selectorView);
            selectorDrawable.draw(canvas);
            canvas.restore();
        } else {
            selectorDrawable.draw(canvas);
        }
    }

    private boolean ignoreClipChild;

    @Override
    public boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (hasSections() && !ignoreClipChild) {
            canvas.save();
            clipChild(canvas, child);
            boolean r = super.drawChild(canvas, child, drawingTime);
            canvas.restore();
            return r;
        } else {
            return super.drawChild(canvas, child, drawingTime);
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        if (itemsEnterAnimator != null) {
            itemsEnterAnimator.dispatchDraw();
        }

//        drawSectionsBackgrounds(canvas);
        if (drawSelection && drawSelectorBehind) {
            drawSelectors2(canvas);
        }
        super.dispatchDraw(canvas);
        if (drawSelection && !drawSelectorBehind) {
            drawSelectors2(canvas);
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
        if (fastScrollAnimationRunning || ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    private boolean ignoreLayout;

    public void setPadding(int left, int top, int right, int bottom, boolean doNotRequestLayout) {
        if (doNotRequestLayout) {
            setPaddingWithoutRequestLayout(left, top, right, bottom);
        } else {
            setPadding(left, top, right, bottom);
        }
    }

    public void setPaddingWithoutRequestLayout(int left, int top, int right, int bottom) {
        if (getPaddingLeft() != left || getPaddingTop() != top || getPaddingRight() != right || getPaddingBottom() != bottom) {
            ignoreLayout = true;
            setPadding(left, top, right, bottom);
            ignoreLayout = false;
        }
    }

    public ViewParent getTouchParent() {
        return null;
    }
    private void requestDisallowInterceptTouchEvent(View view, boolean disallow) {
        if (view == null) return;
        ViewParent parent = view.getParent();
        if (parent == null) return;
        parent.requestDisallowInterceptTouchEvent(disallow);
        parent = getTouchParent();
        if (parent == null) return;
        parent.requestDisallowInterceptTouchEvent(disallow);
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

            requestDisallowInterceptTouchEvent(this, true);

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
                requestDisallowInterceptTouchEvent(this, true);
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
        requestDisallowInterceptTouchEvent(this, false);
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

    protected int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
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

    public void setDrawSelection(boolean drawSelection) {
        this.drawSelection = drawSelection;
    }


    /* Overscroll */

    private final @NonNull EdgeEffectTrackerFactory edgeEffectTrackerFactory;

    public boolean hasActiveEdgeEffects() {
        return edgeEffectTrackerFactory.hasVisibleEdges();
    }

    public void addEdgeEffectListener(Runnable listener) {
        addEdgeEffectListener((direction, isVisible) -> listener.run());
    }

    public void addEdgeEffectListener(EdgeEffectTrackerFactory.OnEdgeEffectListener listener) {
        edgeEffectTrackerFactory.addEdgeEffectListener(listener);
    }

    public void removeEdgeEffectListener(EdgeEffectTrackerFactory.OnEdgeEffectListener listener) {
        edgeEffectTrackerFactory.removeEdgeEffectListener(listener);
    }


    /* Blur3 */

    private Matrix selfTransformationsMatrix;

    @Override
    public void capture(Canvas canvas, RectF position) {
        final long drawingTime = SystemClock.uptimeMillis();

        if (hasActiveEdgeEffects() && getOverScrollMode() != OVER_SCROLL_NEVER) {
            if (selfTransformationsMatrix == null) {
                selfTransformationsMatrix = new Matrix();
            }

            canvas.save();
            final Matrix matrix = getMatrix();
            if (matrix.invert(selfTransformationsMatrix)){
                canvas.concat(selfTransformationsMatrix);
            }
            canvas.translate(-getX(), -getY());

            // hack: call drawChild(this) for access to internal render node
            try {
                super.drawChild(canvas, this, drawingTime);
            } catch (Throwable t) {
                FileLog.e(t);
            }
            canvas.restore();

            if (BuildConfig.DEBUG_PRIVATE_VERSION) {
            //     canvas.drawColor(0x80FF00FF);
            }
        } else {
            for (int i = 0, N = getChildCount(); i < N; i++) {
                final View child = getChildAt(i);

                final float left = child.getX();
                final float top = child.getY();
                final float right = left + child.getWidth();
                final float bottom = top + child.getHeight();

                if (!position.intersects(left, top, right, bottom)) {
                    continue;
                }

                ignoreClipChild = true;
                drawChild(canvas, child, drawingTime);
                ignoreClipChild = false;
            }
            for (int a = 0, N = getItemDecorationCount(); a < N; a++) {
                ItemDecoration itemDecoration = getItemDecorationAt(a);
                if (itemDecoration instanceof IBlur3Capture) {
                    final IBlur3Capture capture = (IBlur3Capture) itemDecoration;
                    capture.capture(canvas, position);
                }
            }
        }
    }

    @Override
    public void captureCalculateHash(IBlur3Hash builder, RectF position) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            builder.unsupported();
            return;
        }

        if (hasActiveEdgeEffects() && getOverScrollMode() != OVER_SCROLL_NEVER) {
            builder.unsupported();
            return;
        }

        for (int i = 0, N = getChildCount(); i < N; i++) {
            final View child = getChildAt(i);

            final float left = child.getX();
            final float top = child.getY();
            final float right = left + child.getWidth();
            final float bottom = top + child.getHeight();

            if (!position.intersects(left, top, right, bottom)) {
                continue;
            }

            builder.add(child);
        }

        for (int a = 0, N = getItemDecorationCount(); a < N; a++) {
            ItemDecoration itemDecoration = getItemDecorationAt(a);
            if (itemDecoration instanceof IBlur3Capture) {
                final IBlur3Capture capture = (IBlur3Capture) itemDecoration;
                capture.captureCalculateHash(builder, position);
            }
        }
    }

    public View findViewByPosition(int position) {
        if (position == NO_POSITION) return null;
        for (int i = 0; i < getChildCount(); ++i) {
            View child = getChildAt(i);
            int childPosition = getChildAdapterPosition(child);
            if (childPosition != NO_POSITION && childPosition == position) {
                return child;
            }
        }
        return null;
    }

    public boolean hasSections() {
        return sectionsItemDecoration != null;
    }

    private ListSectionsDecoration sectionsItemDecoration;
    private Utilities.CallbackReturn<Integer, Boolean> isViewTypeSection;
    private Utilities.Callback5<Canvas, RectF, Float, Float, Float> drawSectionBackground;
    public ArrayList<Long> forcedSections;
    private float sectionRadius;
    private float[] sectionRadiusTop, sectionRadiusBottom;
    public boolean applyPaddingToSections = false;

    public static final int TAG_NOT_SECTION = -33024;

    public void setSections() {
        setSections(dp(12), dp(16), false);
    }
    public void setSections(boolean topPadding) {
        setSections(dp(12), dp(16), topPadding);
    }
    public void setSections(int padding, float roundRadius, boolean topPadding) {
        setSections(
            view -> !(view instanceof TextInfoPrivacyCell || view instanceof ShadowSectionCell || view instanceof FiltersSetupActivity.HintInnerCell || view instanceof GraySectionCell) && !Objects.equals(view.getTag(), TAG_NOT_SECTION),
            padding,
            roundRadius,
            this::drawBackgroundRect,
            topPadding
        );
    }
    private static Pair<Utilities.CallbackReturn<View, Boolean>, Utilities.CallbackReturn<Integer, Boolean>> cachedIsViewTypeShadow(RecyclerListView listView, Utilities.CallbackReturn<View, Boolean> isSectionView) {
        SparseIntArray cache = new SparseIntArray();
        return new Pair<>(
            (view) -> {
                try {
                    if (view.getParent() != listView) return false;
                    final boolean isSection = isSectionView.run(view);
                    final ViewHolder viewHolder = listView.getChildViewHolder(view);
                    if (viewHolder != null) {
                        cache.put(viewHolder.getItemViewType(), isSection ? 1 : 0);
                    }
                    return isSection;
                } catch (Exception e) {
                    return false;
                }
            },
            (viewType) -> {
                int cached = cache.get(viewType, -1);
                if (cached == -1) return true;
                return cached == 1;
            }
        );
    }
    public void setSections(
        Utilities.CallbackReturn<View, Boolean> isSectionView,
        int padding,
        float roundRadius,
        Utilities.Callback5<Canvas, RectF, Float, Float, Float> drawSectionBackground,
        boolean topPadding
    ) {
        final Pair<Utilities.CallbackReturn<View, Boolean>, Utilities.CallbackReturn<Integer, Boolean>> callbacks = cachedIsViewTypeShadow(this, isSectionView);
        setSections(callbacks.first, callbacks.second, padding, roundRadius, drawSectionBackground, topPadding);
    }
    public void setSections(
        Utilities.CallbackReturn<View, Boolean> isSectionView,
        Utilities.CallbackReturn<Integer, Boolean> isViewTypeSection,
        int padding,
        float roundRadius,
        Utilities.Callback5<Canvas, RectF, Float, Float, Float> drawSectionBackground,
        boolean topPadding
    ) {
        setSelectorDrawableColor(getThemedColor(Theme.key_settings_listSelector));
        this.isViewTypeSection = isViewTypeSection;
        this.sectionRadius = roundRadius;
        this.sectionRadiusTop = new float[] {
            roundRadius, roundRadius,
            roundRadius, roundRadius,
            0, 0,
            0, 0
        };
        this.sectionRadiusBottom = new float[] {
            0, 0,
            0, 0,
            roundRadius, roundRadius,
            roundRadius, roundRadius
        };
        this.drawSectionBackground = drawSectionBackground;
        if (sectionsItemDecoration != null) {
            removeItemDecoration(sectionsItemDecoration);
        }
        addItemDecoration(sectionsItemDecoration = new ListSectionsDecoration(isSectionView, padding, topPadding));
//        if (getItemAnimator() != null) {
//            getItemAnimator().listenToAnimationUpdates(this::invalidate);
//        }
    }

    @Override
    public void setItemAnimator(@Nullable ItemAnimator animator) {
        super.setItemAnimator(animator);
//        if (hasSections() && getItemAnimator() != null) {
//            getItemAnimator().listenToAnimationUpdates(this::invalidate);
//        }
    }

    public static class ListSectionsDecoration extends RecyclerView.ItemDecoration {

        public final Utilities.CallbackReturn<View, Boolean> isSectionItem;
        private int padding;
        private boolean enableTopPadding;

        public ListSectionsDecoration(Utilities.CallbackReturn<View, Boolean> isSectionItem, int padding, boolean enableTopPadding) {
            this.isSectionItem = isSectionItem;
            this.padding = padding;
            this.enableTopPadding = enableTopPadding;
        }

        @Override
        public void getItemOffsets(
            @NonNull Rect outRect,
            @NonNull View view,
            @NonNull RecyclerView parent,
            @NonNull RecyclerView.State state
        ) {
            if (isSectionItem.run(view)) {
                outRect.left = outRect.right = padding;

                final ViewHolder viewHolder = parent.getChildViewHolder(view);
                final Adapter adapter = parent.getAdapter();
                if (viewHolder != null && adapter != null) {
                    final int position = viewHolder.getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        final boolean first = position == 0;
                        final boolean last = position == adapter.getItemCount() - 1;

                        if (first) outRect.top = enableTopPadding ? padding : dp(4);
                        if (last) outRect.bottom = padding;
                    }
                }
            }
        }

        @Override
        public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull State state) {
            if (parent instanceof RecyclerListView) {
                ((RecyclerListView) parent).drawSectionsBackgrounds(c);
            }
        }
    }

    private void drawSectionBackground(
        Canvas canvas,
        View from, View to,
        boolean hasAbove, boolean hasBelow
    ) {
        if (from == null || to == null) return;

        float bottomMargin = 0;
        if (to instanceof JoinToSendSettingsView) {
            bottomMargin = ((JoinToSendSettingsView) to).getBottomInfoMargin();
        }

        AndroidUtilities.rectTmp.set(
            from.getLeft(),
            Math.max(applyPaddingToSections ? getPaddingTop() : -sectionRadius, from.getY() - (hasAbove ? sectionRadius : 0)),
            from.getRight(),
            Math.min(getHeight() - (applyPaddingToSections ? getPaddingBottom() : -sectionRadius), to.getY() + to.getHeight() + (hasBelow ? sectionRadius : 0) - bottomMargin)
        );
        if (AndroidUtilities.rectTmp.bottom < AndroidUtilities.rectTmp.top) return;
        drawSectionBackground.run(canvas, AndroidUtilities.rectTmp, sectionRadius, sectionRadius, from.getAlpha());
    }

    private boolean hasAbove(View view, int index) {
        if (view == null || index > 0 || getAdapter() == null || isViewTypeSection == null) return false;
        final int position = getChildAdapterPosition(view);
        if (position == NO_POSITION || position == 0) return false;
        final int viewType = getAdapter().getItemViewType(position - 1);
        return isViewTypeSection.run(viewType);
    }
    private boolean hasBelow(View view, int index) {
        if (view == null || index < getChildCount() - 1 || getAdapter() == null || isViewTypeSection == null) return false;
        final int position = getChildAdapterPosition(view);
        if (position == NO_POSITION || position == getAdapter().getItemCount() - 1) return false;
        final int viewType = getAdapter().getItemViewType(position + 1);
        return isViewTypeSection.run(viewType);
    }
    private ArrayList<SectionsDrawer.Section> sections;
    public boolean isInsideForcedSection(int position) {
        if (forcedSections == null || position < 0) return false;
        for (int j = 0; j < forcedSections.size(); ++j) {
            final long section = forcedSections.get(j);
            final int beginPosition = AndroidUtilities.unpackA(section);
            final int endPosition = AndroidUtilities.unpackB(section);
            if (position >= beginPosition && position <= endPosition) {
                return true;
            }
        }
        return false;
    }
    public void drawSectionsBackgrounds(Canvas canvas) {
        if (drawSectionBackground == null) return;

        if (isAnimating()) {
            if (sections == null) {
                sections = new ArrayList<>();
            }
            for (int i = 0; i < getChildCount(); ++i) {
                final View child = getChildAt(i);
                if (
                    child == emptyView ||
                    child.getVisibility() != View.VISIBLE || child.getAlpha() <= 0 ||
                    !sectionsItemDecoration.isSectionItem.run(child)
                ) continue;

                float from = child.getY();
                float to = child.getY() + child.getHeight();
                final ViewHolder viewHolder = getChildViewHolder(child);
                if (viewHolder.isRemoved() && child.getAlpha() < 1) {
                    View nextStableChild = null;
                    if (viewHolder != null && viewHolder.isRemoved() && viewHolder.mOldCompoundPosition >= 0) {
                        final int nextStablePosition = (int) Math.ceil(viewHolder.mOldCompoundPosition / 1000.0) + 1;
                        for (int j = 0; j < getChildCount(); ++j) {
                            final View nchild = getChildAt(j);
                            if (nchild == null || nchild == child) continue;
                            if (getChildAdapterPosition(nchild) == nextStablePosition) {
                                nextStableChild = nchild;
                                break;
                            }
                        }
                        if (nextStableChild != null && to > nextStableChild.getY() && sectionsItemDecoration.isSectionItem.run(nextStableChild)) {
                            final ViewHolder nextStableHolder = getChildViewHolder(nextStableChild);
                            if (!nextStableHolder.isRemoved()) {
                                from--;
                                to = nextStableChild.getY();
                                if (to < from)
                                    continue;
                            }
                        }
                    }
                } else if (isInsideForcedSection(viewHolder.getAdapterPosition())) {
                    continue;
                }
                sections.add(new SectionsDrawer.Section(from, to, child.getAlpha()));
            }
            SectionsDrawer.draw(sections, sectionRadius, (from, to, topRoundRadius, bottomRoundRadius, alpha) -> {
                AndroidUtilities.rectTmp.set(sectionsItemDecoration.padding, from, getWidth() - sectionsItemDecoration.padding, to);
                drawSectionBackground.run(canvas, AndroidUtilities.rectTmp, topRoundRadius, bottomRoundRadius, alpha);
            });
            sections.clear();
        } else {
            int startIndex = -1, prevIndex = -1;
            View start = null, prev = null;
            for (int i = 0; i < getChildCount(); ++i) {
                final View child = getChildAt(i);
                if (
                    child == emptyView ||
                    child.getVisibility() != View.VISIBLE || child.getAlpha() <= 0 ||
                    !sectionsItemDecoration.isSectionItem.run(child) ||
                    isInsideForcedSection(getChildAdapterPosition(child))
                ) {
                    drawSectionBackground(canvas, start, prev, hasAbove(start, startIndex), hasBelow(prev, prevIndex));
                    startIndex = prevIndex = -1;
                    start = prev = null;
                    continue;
                }
                if (start != null && Math.abs(prev.getAlpha() - child.getAlpha()) > 0.1f) {
                    drawSectionBackground(canvas, start, prev, hasAbove(start, startIndex), hasBelow(prev, prevIndex));
                    startIndex = -1;
                    start = null;
                }
                if (start == null) {
                    startIndex = i;
                    start = child;
                }
                prevIndex = i;
                prev = child;
            }
            drawSectionBackground(canvas, start, prev, hasAbove(start, startIndex), hasBelow(prev, prevIndex));
        }

        if (forcedSections != null) {
            for (int j = 0; j < forcedSections.size(); ++j) {
                final long section = forcedSections.get(j);
                final int beginPosition = AndroidUtilities.unpackA(section);
                final int endPosition = AndroidUtilities.unpackB(section);

                float from = getHeight() + sectionRadius, to = -sectionRadius;
                for (int i = 0; i < getChildCount(); ++i) {
                    final View child = getChildAt(i);
                    final int position = getChildAdapterPosition(child);

                    if (position >= beginPosition && position <= endPosition) {
                        from = Math.min(from, child.getY());
                        to = Math.max(to, child.getY() + child.getHeight());
                    }
                }

                if (from < to) {
                    AndroidUtilities.rectTmp.set(
                        getPaddingLeft() + sectionsItemDecoration.padding,
                        from,
                        getWidth() - getPaddingRight() - sectionsItemDecoration.padding,
                        to
                    );
                    drawSectionBackground.run(canvas, AndroidUtilities.rectTmp, sectionRadius, sectionRadius, 1.0f);
                }
            }
        }
    }

    private final static Paint sectionBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final static Paint sectionBackgroundStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final static Path sectionBackgroundPath = new Path();
    private final static float[] radii = new float[8];
    public static void drawBackgroundRect(Canvas canvas, RectF rect, float topRadius, float bottomRadius, float alpha, Theme.ResourcesProvider resourcesProvider) {
        if (SharedConfig.shadowsInSections) {
            sectionBackgroundStrokePaint.setShadowLayer(dpf2(0.33f), 0, 0, multAlpha(0x0c000000, alpha));
            sectionBackgroundStrokePaint.setColor(0);
            sectionBackgroundPaint.setShadowLayer(dpf2(2), 0, dpf2(0.33f), multAlpha(0x0a000000, alpha));
        } else {
            sectionBackgroundPaint.setShadowLayer(0, 0, 0, 0);
        }
        sectionBackgroundPaint.setColor(multAlpha(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), alpha));
        if (topRadius == bottomRadius) {
            if (SharedConfig.shadowsInSections) {
                canvas.drawRoundRect(rect, topRadius, topRadius, sectionBackgroundStrokePaint);
            }
            canvas.drawRoundRect(rect, topRadius, topRadius, sectionBackgroundPaint);
        } else {
            sectionBackgroundPath.rewind();
            radii[0] = radii[1] = radii[2] = radii[3] = topRadius;
            radii[4] = radii[5] = radii[6] = radii[7] = bottomRadius;
            sectionBackgroundPath.addRoundRect(rect, radii, Path.Direction.CW);
            if (SharedConfig.shadowsInSections) {
                canvas.drawPath(sectionBackgroundPath, sectionBackgroundStrokePaint);
            }
            canvas.drawPath(sectionBackgroundPath, sectionBackgroundPaint);
        }
    }
    public void drawBackgroundRect(Canvas canvas, RectF rect, float topRadius, float bottomRadius, float alpha) {
        drawBackgroundRect(canvas, rect, topRadius, bottomRadius, alpha, resourcesProvider);
    }

    private final Path clipPath = new Path();
    private void clipChild(Canvas canvas, View child) {
        if (child == null || !sectionsItemDecoration.isSectionItem.run(child))
            return;

        boolean prev, next;
        int position = getChildAdapterPosition(child);
        if (position == RecyclerView.NO_POSITION) {
            prev = next = false;
        } else {
            final View prevChild = findViewByPosition(position - 1);
            final View nextChild = findViewByPosition(position + 1);
            prev = prevChild != null && sectionsItemDecoration.isSectionItem.run(prevChild);
            next = nextChild != null && sectionsItemDecoration.isSectionItem.run(nextChild);
        }

        AndroidUtilities.rectTmp.set(
            child.getX(),
            Math.max(applyPaddingToSections ? getPaddingTop() : -sectionRadius, child.getY()),
            child.getX() + child.getWidth(),
            Math.min(getHeight() - (applyPaddingToSections ? getPaddingBottom() : -sectionRadius), child.getY() + child.getHeight())
        );
        if (prev && next) {
            prev = child.getY() >= AndroidUtilities.rectTmp.top;
            next = child.getY() + child.getHeight() <= AndroidUtilities.rectTmp.bottom;
            if (prev && next) return;
        }
        if (!prev && !next) {
            clipPath.rewind();
            clipPath.addRoundRect(AndroidUtilities.rectTmp, sectionRadius, sectionRadius, Path.Direction.CW);
            canvas.clipPath(clipPath);
        } else if (!prev) {
            clipPath.rewind();
            clipPath.addRoundRect(AndroidUtilities.rectTmp, sectionRadiusTop, Path.Direction.CW);
            canvas.clipPath(clipPath);
        } else if (!next) {
            clipPath.rewind();
            clipPath.addRoundRect(AndroidUtilities.rectTmp, sectionRadiusBottom, Path.Direction.CW);
            canvas.clipPath(clipPath);
        }
    }

    public Drawable getClipBackground(View child) {
        if (child.getParent() != this || !hasSections() || !sectionsItemDecoration.isSectionItem.run(child)) return null;

        boolean prev, next;
        int position = getChildAdapterPosition(child);
        if (position == RecyclerView.NO_POSITION) {
            prev = next = false;
        } else {
            final View prevChild = findViewByPosition(position - 1);
            final View nextChild = findViewByPosition(position + 1);
            prev = prevChild != null && sectionsItemDecoration.isSectionItem.run(prevChild);
            next = nextChild != null && sectionsItemDecoration.isSectionItem.run(nextChild);
        }

        final RectF rect = new RectF();
        rect.set(
            child.getX(),
            Math.max(applyPaddingToSections ? getPaddingTop() : 0, child.getY()),
            child.getX() + child.getWidth(),
            Math.min(getHeight() - (applyPaddingToSections ? getPaddingBottom() : 0), child.getY() + child.getHeight())
        );
        if (prev && next) {
            prev = child.getY() >= rect.top;
            next = child.getY() + child.getHeight() <= rect.bottom;
            if (prev && next) return Theme.createRoundRectDrawable(0, Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        }
        final Path clipPath = new Path();
        if (!prev && !next) {
            clipPath.rewind();
            clipPath.addRoundRect(rect, sectionRadius, sectionRadius, Path.Direction.CW);
        } else if (!prev) {
            clipPath.rewind();
            clipPath.addRoundRect(rect, sectionRadiusTop, Path.Direction.CW);
        } else if (!next) {
            clipPath.rewind();
            clipPath.addRoundRect(rect, sectionRadiusBottom, Path.Direction.CW);
        }

        return new Drawable() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            public void draw(@NonNull Canvas canvas) {
                canvas.save();
                canvas.translate(-child.getX(), -child.getY());
                canvas.clipPath(clipPath);
                paint.setColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider), paint.getAlpha()));
                canvas.drawRect(rect, paint);
                canvas.restore();
            }

            @Override
            public void setAlpha(int alpha) {
                paint.setAlpha(alpha);
            }
            @Override
            public void setColorFilter(@Nullable ColorFilter colorFilter) {}
            @Override
            public int getOpacity() { return PixelFormat.TRANSPARENT; }
        };
    }

    public static class SectionsDrawer {

        public static class Section {
            public float from, to;
            public float alpha = 1.0f;

            public Section(float from, float to, float alpha) {
                this.from = from;
                this.to = to;
                this.alpha = alpha;
            }
        }

        private static final ArrayList<float[]> groups = new ArrayList<>();

        private static final float CONNECT_TOLERANCE = 1.5f;

        public static void draw(
            List<Section> sections,
            float roundRadius,
            Utilities.Callback5<Float, Float, Float, Float, Float> drawRoundRect
        ) {
            if (sections == null || sections.isEmpty()) return;

            Collections.sort(sections, (a, b) -> Float.compare(a.from, b.from));

            groups.clear();

            int groupStart = 0;
            while (groupStart < sections.size()) {
                float groupTo = sections.get(groupStart).to;
                int groupEnd = groupStart + 1;

                while (groupEnd < sections.size() && sections.get(groupEnd).from <= groupTo + CONNECT_TOLERANCE) {
                    groupTo = Math.max(groupTo, sections.get(groupEnd).to);
                    groupEnd++;
                }

                float[] groupData = calculateGroup(sections, groupStart, groupEnd, roundRadius);
                if (groupData != null) {
                    groups.add(groupData);
                }

                groupStart = groupEnd;
            }

            for (int i = 0; i < groups.size(); i++) {
                float[] g = groups.get(i);
                float drawFrom = g[0];
                float drawTo = g[1];
                float topRadius = g[2];
                float bottomRadius = g[3];
                float alpha = g[4];

                if (i > 0) {
                    float[] prev = groups.get(i - 1);
                    float gap = drawFrom - prev[1];
                    if (gap < roundRadius * 0.2f) {
                        float t = gap / (roundRadius * 0.2f);
                        topRadius = Math.min(topRadius, roundRadius * t);
                    }
                }

                if (i < groups.size() - 1) {
                    float[] next = groups.get(i + 1);
                    float gap = next[0] - drawTo;
                    if (gap < roundRadius * 0.2f) {
                        float t = gap / (roundRadius * 0.2f);
                        bottomRadius = Math.min(bottomRadius, roundRadius * t);
                    }
                }

                drawRoundRect.run(drawFrom, drawTo, topRadius, bottomRadius, alpha);
            }
        }

        private static float[] calculateGroup(List<Section> sections, int start, int end, float roundRadius) {
            float stableFrom = Float.MAX_VALUE;
            float stableTo = Float.MIN_VALUE;
            for (int i = start; i < end; i++) {
                Section s = sections.get(i);
                if (s.alpha >= 0.99f) {
                    stableFrom = Math.min(stableFrom, s.from);
                    stableTo = Math.max(stableTo, s.to);
                }
            }
            boolean hasStable = stableFrom != Float.MAX_VALUE;

            float minFrom = Float.MAX_VALUE;
            float maxTo = Float.MIN_VALUE;
            float maxAlpha = 0;
            for (int i = start; i < end; i++) {
                Section s = sections.get(i);
                minFrom = Math.min(minFrom, s.from);
                maxTo = Math.max(maxTo, s.to);
                maxAlpha = Math.max(maxAlpha, s.alpha);
            }

            if (maxAlpha < 0.001f) return null;

            float drawFrom, drawTo;
            float topRadius, bottomRadius;
            float alpha;

            if (!hasStable) {
                drawFrom = minFrom;
                drawTo = maxTo;
                alpha = maxAlpha;
                topRadius = roundRadius;
                bottomRadius = roundRadius;
            } else {
                alpha = 1f;

                drawFrom = stableFrom;
                drawTo = stableTo;
                topRadius = roundRadius;
                bottomRadius = roundRadius;

                Section topExtender = null;
                float topExtension = 0;
                for (int i = start; i < end; i++) {
                    Section s = sections.get(i);
                    if (s.alpha >= 0.99f) continue;
                    if (s.from < stableFrom) {
                        float extension = (stableFrom - s.from) * s.alpha;
                        if (extension > topExtension) {
                            topExtension = extension;
                            topExtender = s;
                        }
                    }
                }

                Section bottomExtender = null;
                float bottomExtension = 0;
                for (int i = start; i < end; i++) {
                    Section s = sections.get(i);
                    if (s.alpha >= 0.99f) continue;
                    if (s.to > stableTo) {
                        float extension = (s.to - stableTo) * s.alpha;
                        if (extension > bottomExtension) {
                            bottomExtension = extension;
                            bottomExtender = s;
                        }
                    }
                }

                if (topExtender != null && topExtender.alpha > 0.001f) {
                    float extendedFrom = lerp(stableFrom, topExtender.from, topExtender.alpha);
                    drawFrom = extendedFrom;

                    float t = (stableFrom - drawFrom) / (stableFrom - topExtender.from + 0.001f);
                    topRadius = lerp(roundRadius, roundRadius * topExtender.alpha, t);
                }
                if (bottomExtender != null && bottomExtender.alpha > 0.001f) {
                    float extendedTo = lerp(stableTo, bottomExtender.to, bottomExtender.alpha);
                    drawTo = extendedTo;

                    float t = (drawTo - stableTo) / (bottomExtender.to - stableTo + 0.001f);
                    bottomRadius = lerp(roundRadius, roundRadius * bottomExtender.alpha, t);
                }
            }

            if (drawTo <= drawFrom) return null;

            return new float[]{drawFrom, drawTo, topRadius, bottomRadius, alpha};
        }
    }
}