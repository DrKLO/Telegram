/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package androidx.recyclerview.widget;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;
import static androidx.core.view.ViewCompat.TYPE_NON_TOUCH;
import static androidx.core.view.ViewCompat.TYPE_TOUCH;

import android.animation.LayoutTransition;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Observable;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.FocusFinder;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.Interpolator;
import android.widget.EdgeEffect;
import android.widget.LinearLayout;
import android.widget.OverScroller;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;

import androidx.annotation.CallSuper;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.Px;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.core.os.TraceCompat;
import androidx.core.util.Preconditions;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.InputDeviceCompat;
import androidx.core.view.MotionEventCompat;
import androidx.core.view.NestedScrollingChild2;
import androidx.core.view.NestedScrollingChild3;
import androidx.core.view.NestedScrollingChildHelper;
import androidx.core.view.ScrollingView;
import androidx.core.view.ViewCompat;
import androidx.core.view.ViewConfigurationCompat;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.widget.EdgeEffectCompat;
import androidx.customview.view.AbsSavedState;
import androidx.recyclerview.widget.RecyclerView.ItemAnimator.ItemHolderInfo;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A flexible view for providing a limited window into a large data set.
 *
 * <h3>Glossary of terms:</h3>
 *
 * <ul>
 *     <li><em>Adapter:</em> A subclass of {@link Adapter} responsible for providing views
 *     that represent items in a data set.</li>
 *     <li><em>Position:</em> The position of a data item within an <em>Adapter</em>.</li>
 *     <li><em>Index:</em> The index of an attached child view as used in a call to
 *     {@link ViewGroup#getChildAt}. Contrast with <em>Position.</em></li>
 *     <li><em>Binding:</em> The process of preparing a child view to display data corresponding
 *     to a <em>position</em> within the adapter.</li>
 *     <li><em>Recycle (view):</em> A view previously used to display data for a specific adapter
 *     position may be placed in a cache for later reuse to display the same type of data again
 *     later. This can drastically improve performance by skipping initial layout inflation
 *     or construction.</li>
 *     <li><em>Scrap (view):</em> A child view that has entered into a temporarily detached
 *     state during layout. Scrap views may be reused without becoming fully detached
 *     from the parent RecyclerView, either unmodified if no rebinding is required or modified
 *     by the adapter if the view was considered <em>dirty</em>.</li>
 *     <li><em>Dirty (view):</em> A child view that must be rebound by the adapter before
 *     being displayed.</li>
 * </ul>
 *
 * <h3>Positions in RecyclerView:</h3>
 * <p>
 * RecyclerView introduces an additional level of abstraction between the {@link Adapter} and
 * {@link LayoutManager} to be able to detect data set changes in batches during a layout
 * calculation. This saves LayoutManager from tracking adapter changes to calculate animations.
 * It also helps with performance because all view bindings happen at the same time and unnecessary
 * bindings are avoided.
 * <p>
 * For this reason, there are two types of <code>position</code> related methods in RecyclerView:
 * <ul>
 *     <li>layout position: Position of an item in the latest layout calculation. This is the
 *     position from the LayoutManager's perspective.</li>
 *     <li>adapter position: Position of an item in the adapter. This is the position from
 *     the Adapter's perspective.</li>
 * </ul>
 * <p>
 * These two positions are the same except the time between dispatching <code>adapter.notify*
 * </code> events and calculating the updated layout.
 * <p>
 * Methods that return or receive <code>*LayoutPosition*</code> use position as of the latest
 * layout calculation (e.g. {@link ViewHolder#getLayoutPosition()},
 * {@link #findViewHolderForLayoutPosition(int)}). These positions include all changes until the
 * last layout calculation. You can rely on these positions to be consistent with what user is
 * currently seeing on the screen. For example, if you have a list of items on the screen and user
 * asks for the 5<sup>th</sup> element, you should use these methods as they'll match what user
 * is seeing.
 * <p>
 * The other set of position related methods are in the form of
 * <code>*AdapterPosition*</code>. (e.g. {@link ViewHolder#getAdapterPosition()},
 * {@link #findViewHolderForAdapterPosition(int)}) You should use these methods when you need to
 * work with up-to-date adapter positions even if they may not have been reflected to layout yet.
 * For example, if you want to access the item in the adapter on a ViewHolder click, you should use
 * {@link ViewHolder#getAdapterPosition()}. Beware that these methods may not be able to calculate
 * adapter positions if {@link Adapter#notifyDataSetChanged()} has been called and new layout has
 * not yet been calculated. For this reasons, you should carefully handle {@link #NO_POSITION} or
 * <code>null</code> results from these methods.
 * <p>
 * When writing a {@link LayoutManager} you almost always want to use layout positions whereas when
 * writing an {@link Adapter}, you probably want to use adapter positions.
 * <p>
 * <h3>Presenting Dynamic Data</h3>
 * To display updatable data in a RecyclerView, your adapter needs to signal inserts, moves, and
 * deletions to RecyclerView. You can build this yourself by manually calling
 * {@code adapter.notify*} methods when content changes, or you can use one of the easier solutions
 * RecyclerView provides:
 * <p>
 * <h4>List diffing with DiffUtil</h4>
 * If your RecyclerView is displaying a list that is re-fetched from scratch for each update (e.g.
 * from the network, or from a database), {@link DiffUtil} can calculate the difference between
 * versions of the list. {@code DiffUtil} takes both lists as input and computes the difference,
 * which can be passed to RecyclerView to trigger minimal animations and updates to keep your UI
 * performant, and animations meaningful. This approach requires that each list is represented in
 * memory with immutable content, and relies on receiving updates as new instances of lists. This
 * approach is also ideal if your UI layer doesn't implement sorting, it just presents the data in
 * the order it's given.
 * <p>
 * The best part of this approach is that it extends to any arbitrary changes - item updates,
 * moves, addition and removal can all be computed and handled the same way. Though you do have
 * to keep two copies of the list in memory while diffing, and must avoid mutating them, it's
 * possible to share unmodified elements between list versions.
 * <p>
 * There are three primary ways to do this for RecyclerView. We recommend you start with
 * {@link ListAdapter}, the higher-level API that builds in {@link List} diffing on a background
 * thread, with minimal code. {@link AsyncListDiffer} also provides this behavior, but without
 * defining an Adapter to subclass. If you want more control, {@link DiffUtil} is the lower-level
 * API you can use to compute the diffs yourself. Each approach allows you to specify how diffs
 * should be computed based on item data.
 * <p>
 * <h4>List mutation with SortedList</h4>
 * If your RecyclerView receives updates incrementally, e.g. item X is inserted, or item Y is
 * removed, you can use {@link SortedList} to manage your list. You define how to order items,
 * and it will automatically trigger update signals that RecyclerView can use. SortedList works
 * if you only need to handle insert and remove events, and has the benefit that you only ever
 * need to have a single copy of the list in memory. It can also compute differences with
 * {@link SortedList#replaceAll(Object[])}, but this method is more limited than the list diffing
 * behavior above.
 * <p>
 * <h4>Paging Library</h4>
 * The <a href="https://developer.android.com/topic/libraries/architecture/paging/">Paging
 * library</a> extends the diff-based approach to additionally support paged loading. It provides
 * the {@link androidx.paging.PagedList} class that operates as a self-loading list, provided a
 * source of data like a database, or paginated network API. It provides convenient list diffing
 * support out of the box, similar to {@code ListAdapter} and {@code AsyncListDiffer}. For more
 * information about the Paging library, see the
 * <a href="https://developer.android.com/topic/libraries/architecture/paging/">library
 * documentation</a>.
 *
 * {@link androidx.recyclerview.R.attr#layoutManager}
 */
public class RecyclerView extends ViewGroup implements ScrollingView,
        NestedScrollingChild2, NestedScrollingChild3 {

    static final String TAG = "RecyclerView";

    static final boolean DEBUG = BuildVars.DEBUG_VERSION;

    static final boolean VERBOSE_TRACING = false;

    private static final int[]  NESTED_SCROLLING_ATTRS =
            {16843830 /* android.R.attr.nestedScrollingEnabled */};

    private static final int[] CLIP_TO_PADDING_ATTR = {android.R.attr.clipToPadding};

    /**
     * On Kitkat and JB MR2, there is a bug which prevents DisplayList from being invalidated if
     * a View is two levels deep(wrt to ViewHolder.itemView). DisplayList can be invalidated by
     * setting View's visibility to INVISIBLE when View is detached. On Kitkat and JB MR2, Recycler
     * recursively traverses itemView and invalidates display list for each ViewGroup that matches
     * this criteria.
     */
    static final boolean FORCE_INVALIDATE_DISPLAY_LIST = Build.VERSION.SDK_INT == 18
            || Build.VERSION.SDK_INT == 19 || Build.VERSION.SDK_INT == 20;
    /**
     * On M+, an unspecified measure spec may include a hint which we can use. On older platforms,
     * this value might be garbage. To save LayoutManagers from it, RecyclerView sets the size to
     * 0 when mode is unspecified.
     */
    static final boolean ALLOW_SIZE_IN_UNSPECIFIED_SPEC = Build.VERSION.SDK_INT >= 23;

    static final boolean POST_UPDATES_ON_ANIMATION = Build.VERSION.SDK_INT >= 16;

    /**
     * On L+, with RenderThread, the UI thread has idle time after it has passed a frame off to
     * RenderThread but before the next frame begins. We schedule prefetch work in this window.
     */
    static final boolean ALLOW_THREAD_GAP_WORK = Build.VERSION.SDK_INT >= 21;

    /**
     * FocusFinder#findNextFocus is broken on ICS MR1 and older for View.FOCUS_BACKWARD direction.
     * We convert it to an absolute direction such as FOCUS_DOWN or FOCUS_LEFT.
     */
    private static final boolean FORCE_ABS_FOCUS_SEARCH_DIRECTION = Build.VERSION.SDK_INT <= 15;

    /**
     * on API 15-, a focused child can still be considered a focused child of RV even after
     * it's being removed or its focusable flag is set to false. This is because when this focused
     * child is detached, the reference to this child is not removed in clearFocus. API 16 and above
     * properly handle this case by calling ensureInputFocusOnFirstFocusable or rootViewRequestFocus
     * to request focus on a new child, which will clear the focus on the old (detached) child as a
     * side-effect.
     */
    private static final boolean IGNORE_DETACHED_FOCUSED_CHILD = Build.VERSION.SDK_INT <= 15;

    static final boolean DISPATCH_TEMP_DETACH = false;

    /** @hide */
    @RestrictTo(LIBRARY_GROUP_PREFIX)
    @IntDef({HORIZONTAL, VERTICAL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Orientation {}

    public static final int HORIZONTAL = LinearLayout.HORIZONTAL;
    public static final int VERTICAL = LinearLayout.VERTICAL;

    static final int DEFAULT_ORIENTATION = VERTICAL;
    public static final int NO_POSITION = -1;
    public static final long NO_ID = -1;
    public static final int INVALID_TYPE = -1;

    /**
     * Constant for use with {@link #setScrollingTouchSlop(int)}. Indicates
     * that the RecyclerView should use the standard touch slop for smooth,
     * continuous scrolling.
     */
    public static final int TOUCH_SLOP_DEFAULT = 0;

    /**
     * Constant for use with {@link #setScrollingTouchSlop(int)}. Indicates
     * that the RecyclerView should use the standard touch slop for scrolling
     * widgets that snap to a page or other coarse-grained barrier.
     */
    public static final int TOUCH_SLOP_PAGING = 1;

    static final int UNDEFINED_DURATION = Integer.MIN_VALUE;

    static final int MAX_SCROLL_DURATION = 2000;

    /**
     * RecyclerView is calculating a scroll.
     * If there are too many of these in Systrace, some Views inside RecyclerView might be causing
     * it. Try to avoid using EditText, focusable views or handle them with care.
     */
    static final String TRACE_SCROLL_TAG = "RV Scroll";

    /**
     * OnLayout has been called by the View system.
     * If this shows up too many times in Systrace, make sure the children of RecyclerView do not
     * update themselves directly. This will cause a full re-layout but when it happens via the
     * Adapter notifyItemChanged, RecyclerView can avoid full layout calculation.
     */
    private static final String TRACE_ON_LAYOUT_TAG = "RV OnLayout";

    /**
     * NotifyDataSetChanged or equal has been called.
     * If this is taking a long time, try sending granular notify adapter changes instead of just
     * calling notifyDataSetChanged or setAdapter / swapAdapter. Adding stable ids to your adapter
     * might help.
     */
    private static final String TRACE_ON_DATA_SET_CHANGE_LAYOUT_TAG = "RV FullInvalidate";

    /**
     * RecyclerView is doing a layout for partial adapter updates (we know what has changed)
     * If this is taking a long time, you may have dispatched too many Adapter updates causing too
     * many Views being rebind. Make sure all are necessary and also prefer using notify*Range
     * methods.
     */
    private static final String TRACE_HANDLE_ADAPTER_UPDATES_TAG = "RV PartialInvalidate";

    /**
     * RecyclerView is rebinding a View.
     * If this is taking a lot of time, consider optimizing your layout or make sure you are not
     * doing extra operations in onBindViewHolder call.
     */
    static final String TRACE_BIND_VIEW_TAG = "RV OnBindView";

    /**
     * RecyclerView is attempting to pre-populate off screen views.
     */
    static final String TRACE_PREFETCH_TAG = "RV Prefetch";

    /**
     * RecyclerView is attempting to pre-populate off screen itemviews within an off screen
     * RecyclerView.
     */
    static final String TRACE_NESTED_PREFETCH_TAG = "RV Nested Prefetch";

    /**
     * RecyclerView is creating a new View.
     * If too many of these present in Systrace:
     * - There might be a problem in Recycling (e.g. custom Animations that set transient state and
     * prevent recycling or ItemAnimator not implementing the contract properly. ({@link
     * > Adapter#onFailedToRecycleView(ViewHolder)})
     *
     * - There might be too many item view types.
     * > Try merging them
     *
     * - There might be too many itemChange animations and not enough space in RecyclerPool.
     * >Try increasing your pool size and item cache size.
     */
    static final String TRACE_CREATE_VIEW_TAG = "RV CreateView";
    private static final Class<?>[] LAYOUT_MANAGER_CONSTRUCTOR_SIGNATURE =
            new Class[]{Context.class, AttributeSet.class, int.class, int.class};

    private final RecyclerViewDataObserver mObserver = new RecyclerViewDataObserver();

    public final Recycler mRecycler = new Recycler();

    private SavedState mPendingSavedState;

    /**
     * Handles adapter updates
     */
    AdapterHelper mAdapterHelper;

    /**
     * Handles abstraction between LayoutManager children and RecyclerView children
     */
    public ChildHelper mChildHelper;

    /**
     * Keeps data about views to be used for animations
     */
    final ViewInfoStore mViewInfoStore = new ViewInfoStore();

    /**
     * Prior to L, there is no way to query this variable which is why we override the setter and
     * track it here.
     */
    boolean mClipToPadding;

    /**
     * Note: this Runnable is only ever posted if:
     * 1) We've been through first layout
     * 2) We know we have a fixed size (mHasFixedSize)
     * 3) We're attached
     */
    final Runnable mUpdateChildViewsRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mFirstLayoutComplete || isLayoutRequested()) {
                // a layout request will happen, we should not do layout here.
                return;
            }
            if (!mIsAttached) {
                requestLayout();
                // if we are not attached yet, mark us as requiring layout and skip
                return;
            }
            if (mLayoutSuppressed) {
                mLayoutWasDefered = true;
                return; //we'll process updates when ice age ends.
            }
            consumePendingUpdateOperations();
        }
    };

    final Rect mTempRect = new Rect();
    private final Rect mTempRect2 = new Rect();
    final RectF mTempRectF = new RectF();
    Adapter mAdapter;
    @VisibleForTesting LayoutManager mLayout;
    RecyclerListener mRecyclerListener;
    final ArrayList<ItemDecoration> mItemDecorations = new ArrayList<>();
    private final ArrayList<OnItemTouchListener> mOnItemTouchListeners =
            new ArrayList<>();
    private OnItemTouchListener mInterceptingOnItemTouchListener;
    boolean mIsAttached;
    boolean mHasFixedSize;
    boolean mEnableFastScroller;
    @VisibleForTesting boolean mFirstLayoutComplete;

    /**
     * The current depth of nested calls to {@link #startInterceptRequestLayout()} (number of
     * calls to {@link #startInterceptRequestLayout()} - number of calls to
     * {@link #stopInterceptRequestLayout(boolean)} .  This is used to signal whether we
     * should defer layout operations caused by layout requests from children of
     * {@link RecyclerView}.
     */
    private int mInterceptRequestLayoutDepth = 0;

    /**
     * True if a call to requestLayout was intercepted and prevented from executing like normal and
     * we plan on continuing with normal execution later.
     */
    boolean mLayoutWasDefered;

    boolean mLayoutSuppressed;
    private boolean mIgnoreMotionEventTillDown;

    // binary OR of change events that were eaten during a layout or scroll.
    private int mEatenAccessibilityChangeFlags;
    boolean mAdapterUpdateDuringMeasure;

    private final AccessibilityManager mAccessibilityManager;
    private List<OnChildAttachStateChangeListener> mOnChildAttachStateListeners;

    /**
     * True after an event occurs that signals that the entire data set has changed. In that case,
     * we cannot run any animations since we don't know what happened until layout.
     *
     * Attached items are invalid until next layout, at which point layout will animate/replace
     * items as necessary, building up content from the (effectively) new adapter from scratch.
     *
     * Cached items must be discarded when setting this to true, so that the cache may be freely
     * used by prefetching until the next layout occurs.
     *
     * @see #processDataSetCompletelyChanged(boolean)
     */
    boolean mDataSetHasChangedAfterLayout = false;

    /**
     * True after the data set has completely changed and
     * {@link LayoutManager#onItemsChanged(RecyclerView)} should be called during the subsequent
     * measure/layout.
     *
     * @see #processDataSetCompletelyChanged(boolean)
     */
    boolean mDispatchItemsChangedEvent = false;

    /**
     * This variable is incremented during a dispatchLayout and/or scroll.
     * Some methods should not be called during these periods (e.g. adapter data change).
     * Doing so will create hard to find bugs so we better check it and throw an exception.
     *
     * @see #assertInLayoutOrScroll(String)
     * @see #assertNotInLayoutOrScroll(String)
     */
    private int mLayoutOrScrollCounter = 0;

    /**
     * Similar to mLayoutOrScrollCounter but logs a warning instead of throwing an exception
     * (for API compatibility).
     * <p>
     * It is a bad practice for a developer to update the data in a scroll callback since it is
     * potentially called during a layout.
     */
    private int mDispatchScrollCounter = 0;

    @NonNull
    private EdgeEffectFactory mEdgeEffectFactory = new EdgeEffectFactory();
    private EdgeEffect mLeftGlow, mTopGlow, mRightGlow, mBottomGlow;

    ItemAnimator mItemAnimator = new DefaultItemAnimator();

    private static final int INVALID_POINTER = -1;

    /**
     * The RecyclerView is not currently scrolling.
     * @see #getScrollState()
     */
    public static final int SCROLL_STATE_IDLE = 0;

    /**
     * The RecyclerView is currently being dragged by outside input such as user touch input.
     * @see #getScrollState()
     */
    public static final int SCROLL_STATE_DRAGGING = 1;

    /**
     * The RecyclerView is currently animating to a final position while not under
     * outside control.
     * @see #getScrollState()
     */
    public static final int SCROLL_STATE_SETTLING = 2;

    static final long FOREVER_NS = Long.MAX_VALUE;

    // Touch/scrolling handling

    private int mScrollState = SCROLL_STATE_IDLE;
    private int mScrollPointerId = INVALID_POINTER;
    private VelocityTracker mVelocityTracker;
    private int mInitialTouchX;
    private int mInitialTouchY;
    private int mLastTouchX;
    private int mLastTouchY;
    private int mTouchSlop;
    private OnFlingListener mOnFlingListener;
    private final int mMinFlingVelocity;
    private final int mMaxFlingVelocity;

    // This value is used when handling rotary encoder generic motion events.
    private float mScaledHorizontalScrollFactor = Float.MIN_VALUE;
    private float mScaledVerticalScrollFactor = Float.MIN_VALUE;

    private boolean mPreserveFocusAfterLayout = true;

    final ViewFlinger mViewFlinger = new ViewFlinger();

    GapWorker mGapWorker;
    GapWorker.LayoutPrefetchRegistryImpl mPrefetchRegistry =
            ALLOW_THREAD_GAP_WORK ? new GapWorker.LayoutPrefetchRegistryImpl() : null;

    final State mState = new State();

    private OnScrollListener mScrollListener;
    private List<OnScrollListener> mScrollListeners;

    // For use in item animations
    boolean mItemsAddedOrRemoved = false;
    boolean mItemsChanged = false;
    private ItemAnimator.ItemAnimatorListener mItemAnimatorListener =
            new ItemAnimatorRestoreListener();
    boolean mPostedAnimatorRunner = false;
    RecyclerViewAccessibilityDelegate mAccessibilityDelegate;
    private ChildDrawingOrderCallback mChildDrawingOrderCallback;

    // simple array to keep min and max child position during a layout calculation
    // preserved not to create a new one in each layout pass
    private final int[] mMinMaxLayoutPositions = new int[2];

    private NestedScrollingChildHelper mScrollingChildHelper;
    private final int[] mScrollOffset = new int[2];
    private final int[] mNestedOffsets = new int[2];

    private int topGlowOffset = 0;
    private int bottomGlowOffset = 0;
    private Integer glowColor = null;

    public void setTopGlowOffset(int offset) {
        topGlowOffset = offset;
    }

    public void setBottomGlowOffset(int offset) {
        bottomGlowOffset = offset;
    }

    public void setGlowColor(int color) {
        glowColor = color;
    }

    public int getAttachedScrapChildCount() {
        return mRecycler.getScrapCount();
    }

    public View getAttachedScrapChildAt(int index) {
        if (index < 0 || index >= mRecycler.mAttachedScrap.size()) {
            return null;
        }
        return mRecycler.getScrapViewAt(index);
    }

    public int getCachedChildCount() {
        return mRecycler.mCachedViews.size();
    }

    public View getCachedChildAt(int index) {
        if (index < 0 || index >= mRecycler.mCachedViews.size()) {
            return null;
        }
        return mRecycler.mCachedViews.get(index).itemView;
    }

    public int getHiddenChildCount() {
        return mChildHelper.getHiddenChildCount();
    }

    public View getHiddenChildAt(int index) {
        return mChildHelper.getHiddenChildAt(index);
    }

    void applyEdgeEffectColor(EdgeEffect edgeEffect) {
        if (edgeEffect != null && Build.VERSION.SDK_INT >= 21 && glowColor != null) {
            edgeEffect.setColor(glowColor);
        }
    }

    // Reusable int array to be passed to method calls that mutate it in order to "return" two ints.
    final int[] mReusableIntPair = new int[2];

    /**
     * These are views that had their a11y importance changed during a layout. We defer these events
     * until the end of the layout because a11y service may make sync calls back to the RV while
     * the View's state is undefined.
     */
    @VisibleForTesting
    final List<ViewHolder> mPendingAccessibilityImportanceChange = new ArrayList<>();

    private Runnable mItemAnimatorRunner = new Runnable() {
        @Override
        public void run() {
            if (mItemAnimator != null) {
                mItemAnimator.runPendingAnimations();
            }
            mPostedAnimatorRunner = false;
        }
    };

    static final Interpolator sQuinticInterpolator = new Interpolator() {
        @Override
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t * t * t + 1.0f;
        }
    };

    /**
     * The callback to convert view info diffs into animations.
     */
    private final ViewInfoStore.ProcessCallback mViewInfoProcessCallback =
            new ViewInfoStore.ProcessCallback() {
                @Override
                public void processDisappeared(ViewHolder viewHolder, @NonNull ItemHolderInfo info,
                        @Nullable ItemHolderInfo postInfo) {
                    mRecycler.unscrapView(viewHolder);
                    animateDisappearance(viewHolder, info, postInfo);
                }
                @Override
                public void processAppeared(ViewHolder viewHolder,
                        ItemHolderInfo preInfo, ItemHolderInfo info) {
                    animateAppearance(viewHolder, preInfo, info);
                }

                @Override
                public void processPersistent(ViewHolder viewHolder,
                        @NonNull ItemHolderInfo preInfo, @NonNull ItemHolderInfo postInfo) {
                    viewHolder.setIsRecyclable(false);
                    if (mDataSetHasChangedAfterLayout) {
                        // since it was rebound, use change instead as we'll be mapping them from
                        // stable ids. If stable ids were false, we would not be running any
                        // animations
                        if (mItemAnimator.animateChange(viewHolder, viewHolder, preInfo,
                                postInfo)) {
                            postAnimationRunner();
                        }
                    } else if (mItemAnimator.animatePersistence(viewHolder, preInfo, postInfo)) {
                        postAnimationRunner();
                    }
                }
                @Override
                public void unused(ViewHolder viewHolder) {
                    mLayout.removeAndRecycleView(viewHolder.itemView, mRecycler);
                }
            };

    public RecyclerView(@NonNull Context context) {
        this(context, null);
    }

    public RecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public RecyclerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, CLIP_TO_PADDING_ATTR, defStyle, 0);
            mClipToPadding = a.getBoolean(0, true);
            a.recycle();
        } else {
            mClipToPadding = true;
        }
        setScrollContainer(true);
        setFocusableInTouchMode(true);

        final ViewConfiguration vc = ViewConfiguration.get(context);
        mTouchSlop = vc.getScaledTouchSlop();
        mScaledHorizontalScrollFactor =
                ViewConfigurationCompat.getScaledHorizontalScrollFactor(vc, context);
        mScaledVerticalScrollFactor =
                ViewConfigurationCompat.getScaledVerticalScrollFactor(vc, context);
        mMinFlingVelocity = vc.getScaledMinimumFlingVelocity();
        mMaxFlingVelocity = vc.getScaledMaximumFlingVelocity();
        setWillNotDraw(getOverScrollMode() == View.OVER_SCROLL_NEVER);

        mItemAnimator.setListener(mItemAnimatorListener);
        initAdapterManager();
        initChildrenHelper();
        initAutofill();
        // If not explicitly specified this view is important for accessibility.
        if (ViewCompat.getImportantForAccessibility(this)
                == ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            ViewCompat.setImportantForAccessibility(this,
                    ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        mAccessibilityManager = (AccessibilityManager) getContext()
                .getSystemService(Context.ACCESSIBILITY_SERVICE);
        setAccessibilityDelegateCompat(new RecyclerViewAccessibilityDelegate(this));
        // Create the layoutManager if specified.

        boolean nestedScrollingEnabled = true;
        setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);

        // Re-set whether nested scrolling is enabled so that it is set on all API levels
        setNestedScrollingEnabled(nestedScrollingEnabled);
    }

    /**
     * Label appended to all public exception strings, used to help find which RV in an app is
     * hitting an exception.
     */
    String exceptionLabel() {
        return " " + super.toString()
                + ", adapter:" + mAdapter
                + ", layout:" + mLayout
                + ", context:" + getContext();
    }

    /**
     * If not explicitly specified, this view and its children don't support autofill.
     * <p>
     * This is done because autofill's means of uniquely identifying views doesn't work out of the
     * box with View recycling.
     */
    @SuppressLint("InlinedApi")
    private void initAutofill() {
        if (ViewCompat.getImportantForAutofill(this) == View.IMPORTANT_FOR_AUTOFILL_AUTO) {
            ViewCompat.setImportantForAutofill(this,
                    View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        }
    }

    /**
     * Returns the accessibility delegate compatibility implementation used by the RecyclerView.
     * @return An instance of AccessibilityDelegateCompat used by RecyclerView
     */
    @Nullable
    public RecyclerViewAccessibilityDelegate getCompatAccessibilityDelegate() {
        return mAccessibilityDelegate;
    }

    /**
     * Sets the accessibility delegate compatibility implementation used by RecyclerView.
     * @param accessibilityDelegate The accessibility delegate to be used by RecyclerView.
     */
    public void setAccessibilityDelegateCompat(
            @Nullable RecyclerViewAccessibilityDelegate accessibilityDelegate) {
        mAccessibilityDelegate = accessibilityDelegate;
        ViewCompat.setAccessibilityDelegate(this, mAccessibilityDelegate);
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return "androidx.recyclerview.widget.RecyclerView";
    }

    /**
     * Instantiate and set a LayoutManager, if specified in the attributes.
     */
    private void createLayoutManager(Context context, String className, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        if (className != null) {
            className = className.trim();
            if (!className.isEmpty()) {
                className = getFullClassName(context, className);
                try {
                    ClassLoader classLoader;
                    if (isInEditMode()) {
                        // Stupid layoutlib cannot handle simple class loaders.
                        classLoader = this.getClass().getClassLoader();
                    } else {
                        classLoader = context.getClassLoader();
                    }
                    Class<? extends LayoutManager> layoutManagerClass =
                            Class.forName(className, false, classLoader)
                                    .asSubclass(LayoutManager.class);
                    Constructor<? extends LayoutManager> constructor;
                    Object[] constructorArgs = null;
                    try {
                        constructor = layoutManagerClass
                                .getConstructor(LAYOUT_MANAGER_CONSTRUCTOR_SIGNATURE);
                        constructorArgs = new Object[]{context, attrs, defStyleAttr, defStyleRes};
                    } catch (NoSuchMethodException e) {
                        try {
                            constructor = layoutManagerClass.getConstructor();
                        } catch (NoSuchMethodException e1) {
                            e1.initCause(e);
                            throw new IllegalStateException(attrs.getPositionDescription()
                                    + ": Error creating LayoutManager " + className, e1);
                        }
                    }
                    constructor.setAccessible(true);
                    setLayoutManager(constructor.newInstance(constructorArgs));
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(attrs.getPositionDescription()
                            + ": Unable to find LayoutManager " + className, e);
                } catch (InvocationTargetException e) {
                    throw new IllegalStateException(attrs.getPositionDescription()
                            + ": Could not instantiate the LayoutManager: " + className, e);
                } catch (InstantiationException e) {
                    throw new IllegalStateException(attrs.getPositionDescription()
                            + ": Could not instantiate the LayoutManager: " + className, e);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(attrs.getPositionDescription()
                            + ": Cannot access non-public constructor " + className, e);
                } catch (ClassCastException e) {
                    throw new IllegalStateException(attrs.getPositionDescription()
                            + ": Class is not a LayoutManager " + className, e);
                }
            }
        }
    }

    private String getFullClassName(Context context, String className) {
        if (className.charAt(0) == '.') {
            return context.getPackageName() + className;
        }
        if (className.contains(".")) {
            return className;
        }
        return RecyclerView.class.getPackage().getName() + '.' + className;
    }

    private void initChildrenHelper() {
        mChildHelper = new ChildHelper(new ChildHelper.Callback() {
            @Override
            public int getChildCount() {
                return RecyclerView.this.getChildCount();
            }

            @Override
            public void addView(View child, int index) {
                if (VERBOSE_TRACING) {
                    TraceCompat.beginSection("RV addView");
                }
                RecyclerView.this.addView(child, index);
                if (VERBOSE_TRACING) {
                    TraceCompat.endSection();
                }
                dispatchChildAttached(child);
            }

            @Override
            public int indexOfChild(View view) {
                return RecyclerView.this.indexOfChild(view);
            }

            @Override
            public void removeViewAt(int index) {
                final View child = RecyclerView.this.getChildAt(index);
                if (child != null) {
                    dispatchChildDetached(child);

                    // Clear any android.view.animation.Animation that may prevent the item from
                    // detaching when being removed. If a child is re-added before the
                    // lazy detach occurs, it will receive invalid attach/detach sequencing.
                    child.clearAnimation();
                }
                if (VERBOSE_TRACING) {
                    TraceCompat.beginSection("RV removeViewAt");
                }
                RecyclerView.this.removeViewAt(index);
                if (VERBOSE_TRACING) {
                    TraceCompat.endSection();
                }
            }

            @Override
            public View getChildAt(int offset) {
                return RecyclerView.this.getChildAt(offset);
            }

            @Override
            public void removeAllViews() {
                final int count = getChildCount();
                for (int i = 0; i < count; i++) {
                    View child = getChildAt(i);
                    dispatchChildDetached(child);

                    // Clear any android.view.animation.Animation that may prevent the item from
                    // detaching when being removed. If a child is re-added before the
                    // lazy detach occurs, it will receive invalid attach/detach sequencing.
                    child.clearAnimation();
                }
                RecyclerView.this.removeAllViews();
            }

            @Override
            public ViewHolder getChildViewHolder(View view) {
                return getChildViewHolderInt(view);
            }

            @Override
            public void attachViewToParent(View child, int index,
                    ViewGroup.LayoutParams layoutParams) {
                final ViewHolder vh = getChildViewHolderInt(child);
                if (vh != null) {
                    if (!vh.isTmpDetached() && !vh.shouldIgnore()) {
                        throw new IllegalArgumentException("Called attach on a child which is not"
                                + " detached: " + vh + exceptionLabel());
                    }
                    if (DEBUG) {
                        Log.d(TAG, "reAttach " + vh);
                    }
                    vh.clearTmpDetachFlag();
                }
                RecyclerView.this.attachViewToParent(child, index, layoutParams);
            }

            @Override
            public void detachViewFromParent(int offset) {
                final View view = getChildAt(offset);
                if (view != null) {
                    final ViewHolder vh = getChildViewHolderInt(view);
                    if (vh != null) {
                        if (vh.isTmpDetached() && !vh.shouldIgnore()) {
                            throw new IllegalArgumentException("called detach on an already"
                                    + " detached child " + vh + exceptionLabel());
                        }
                        if (DEBUG) {
                            Log.d(TAG, "tmpDetach " + vh);
                        }
                        vh.addFlags(ViewHolder.FLAG_TMP_DETACHED);
                    }
                }
                RecyclerView.this.detachViewFromParent(offset);
            }

            @Override
            public void onEnteredHiddenState(View child) {
                final ViewHolder vh = getChildViewHolderInt(child);
                if (vh != null) {
                    vh.onEnteredHiddenState(RecyclerView.this);
                }
            }

            @Override
            public void onLeftHiddenState(View child) {
                final ViewHolder vh = getChildViewHolderInt(child);
                if (vh != null) {
                    vh.onLeftHiddenState(RecyclerView.this);
                }
            }
        });
    }

    void initAdapterManager() {
        mAdapterHelper = new AdapterHelper(new AdapterHelper.Callback() {
            @Override
            public ViewHolder findViewHolder(int position) {
                final ViewHolder vh = findViewHolderForPosition(position, true);
                if (vh == null) {
                    return null;
                }
                // ensure it is not hidden because for adapter helper, the only thing matter is that
                // LM thinks view is a child.
                if (mChildHelper.isHidden(vh.itemView)) {
                    if (DEBUG) {
                        Log.d(TAG, "assuming view holder cannot be find because it is hidden");
                    }
                    return null;
                }
                return vh;
            }

            @Override
            public void offsetPositionsForRemovingInvisible(int start, int count) {
                offsetPositionRecordsForRemove(start, count, true);
                mItemsAddedOrRemoved = true;
                mState.mDeletedInvisibleItemCountSincePreviousLayout += count;
            }

            @Override
            public void offsetPositionsForRemovingLaidOutOrNewView(
                    int positionStart, int itemCount) {
                offsetPositionRecordsForRemove(positionStart, itemCount, false);
                mItemsAddedOrRemoved = true;
            }


            @Override
            public void markViewHoldersUpdated(int positionStart, int itemCount, Object payload) {
                viewRangeUpdate(positionStart, itemCount, payload);
                mItemsChanged = true;
            }

            @Override
            public void onDispatchFirstPass(AdapterHelper.UpdateOp op) {
                dispatchUpdate(op);
            }

            void dispatchUpdate(AdapterHelper.UpdateOp op) {
                switch (op.cmd) {
                    case AdapterHelper.UpdateOp.ADD:
                        mLayout.onItemsAdded(RecyclerView.this, op.positionStart, op.itemCount);
                        break;
                    case AdapterHelper.UpdateOp.REMOVE:
                        mLayout.onItemsRemoved(RecyclerView.this, op.positionStart, op.itemCount);
                        break;
                    case AdapterHelper.UpdateOp.UPDATE:
                        mLayout.onItemsUpdated(RecyclerView.this, op.positionStart, op.itemCount,
                                op.payload);
                        break;
                    case AdapterHelper.UpdateOp.MOVE:
                        mLayout.onItemsMoved(RecyclerView.this, op.positionStart, op.itemCount, 1);
                        break;
                }
            }

            @Override
            public void onDispatchSecondPass(AdapterHelper.UpdateOp op) {
                dispatchUpdate(op);
            }

            @Override
            public void offsetPositionsForAdd(int positionStart, int itemCount) {
                offsetPositionRecordsForInsert(positionStart, itemCount);
                mItemsAddedOrRemoved = true;
            }

            @Override
            public void offsetPositionsForMove(int from, int to) {
                offsetPositionRecordsForMove(from, to);
                // should we create mItemsMoved ?
                mItemsAddedOrRemoved = true;
            }
        });
    }

    /**
     * RecyclerView can perform several optimizations if it can know in advance that RecyclerView's
     * size is not affected by the adapter contents. RecyclerView can still change its size based
     * on other factors (e.g. its parent's size) but this size calculation cannot depend on the
     * size of its children or contents of its adapter (except the number of items in the adapter).
     * <p>
     * If your use of RecyclerView falls into this category, set this to {@code true}. It will allow
     * RecyclerView to avoid invalidating the whole layout when its adapter contents change.
     *
     * @param hasFixedSize true if adapter changes cannot affect the size of the RecyclerView.
     */
    public void setHasFixedSize(boolean hasFixedSize) {
        mHasFixedSize = hasFixedSize;
    }

    /**
     * @return true if the app has specified that changes in adapter content cannot change
     * the size of the RecyclerView itself.
     */
    public boolean hasFixedSize() {
        return mHasFixedSize;
    }

    @Override
    public void setClipToPadding(boolean clipToPadding) {
        if (clipToPadding != mClipToPadding) {
            invalidateGlows();
        }
        mClipToPadding = clipToPadding;
        super.setClipToPadding(clipToPadding);
        if (mFirstLayoutComplete) {
            requestLayout();
        }
    }

    /**
     * Returns whether this RecyclerView will clip its children to its padding, and resize (but
     * not clip) any EdgeEffect to the padded region, if padding is present.
     * <p>
     * By default, children are clipped to the padding of their parent
     * RecyclerView. This clipping behavior is only enabled if padding is non-zero.
     *
     * @return true if this RecyclerView clips children to its padding and resizes (but doesn't
     *         clip) any EdgeEffect to the padded region, false otherwise.
     *
     * @attr name android:clipToPadding
     */
    @Override
    public boolean getClipToPadding() {
        return mClipToPadding;
    }

    /**
     * Configure the scrolling touch slop for a specific use case.
     *
     * Set up the RecyclerView's scrolling motion threshold based on common usages.
     * Valid arguments are {@link #TOUCH_SLOP_DEFAULT} and {@link #TOUCH_SLOP_PAGING}.
     *
     * @param slopConstant One of the <code>TOUCH_SLOP_</code> constants representing
     *                     the intended usage of this RecyclerView
     */
    public void setScrollingTouchSlop(int slopConstant) {
        final ViewConfiguration vc = ViewConfiguration.get(getContext());
        switch (slopConstant) {
            default:
                Log.w(TAG, "setScrollingTouchSlop(): bad argument constant "
                        + slopConstant + "; using default value");
                // fall-through
            case TOUCH_SLOP_DEFAULT:
                mTouchSlop = vc.getScaledTouchSlop();
                break;

            case TOUCH_SLOP_PAGING:
                mTouchSlop = vc.getScaledPagingTouchSlop();
                break;
        }
    }

    /**
     * Swaps the current adapter with the provided one. It is similar to
     * {@link #setAdapter(Adapter)} but assumes existing adapter and the new adapter uses the same
     * {@link ViewHolder} and does not clear the RecycledViewPool.
     * <p>
     * Note that it still calls onAdapterChanged callbacks.
     *
     * @param adapter The new adapter to set, or null to set no adapter.
     * @param removeAndRecycleExistingViews If set to true, RecyclerView will recycle all existing
     *                                      Views. If adapters have stable ids and/or you want to
     *                                      animate the disappearing views, you may prefer to set
     *                                      this to false.
     * @see #setAdapter(Adapter)
     */
    public void swapAdapter(@Nullable Adapter adapter, boolean removeAndRecycleExistingViews) {
        // bail out if layout is frozen
        setLayoutFrozen(false);
        setAdapterInternal(adapter, true, removeAndRecycleExistingViews);
        processDataSetCompletelyChanged(true);
        requestLayout();
    }
    /**
     * Set a new adapter to provide child views on demand.
     * <p>
     * When adapter is changed, all existing views are recycled back to the pool. If the pool has
     * only one adapter, it will be cleared.
     *
     * @param adapter The new adapter to set, or null to set no adapter.
     * @see #swapAdapter(Adapter, boolean)
     */
    public void setAdapter(@Nullable Adapter adapter) {
        // bail out if layout is frozen
        setLayoutFrozen(false);
        setAdapterInternal(adapter, false, true);
        processDataSetCompletelyChanged(false);
        requestLayout();
    }

    /**
     * Removes and recycles all views - both those currently attached, and those in the Recycler.
     */
    void removeAndRecycleViews() {
        // end all running animations
        if (mItemAnimator != null) {
            mItemAnimator.endAnimations();
        }
        // Since animations are ended, mLayout.children should be equal to
        // recyclerView.children. This may not be true if item animator's end does not work as
        // expected. (e.g. not release children instantly). It is safer to use mLayout's child
        // count.
        if (mLayout != null) {
            mLayout.removeAndRecycleAllViews(mRecycler);
            mLayout.removeAndRecycleScrapInt(mRecycler);
        }
        // we should clear it here before adapters are swapped to ensure correct callbacks.
        mRecycler.clear();
    }

    /**
     * Replaces the current adapter with the new one and triggers listeners.
     * @param adapter The new adapter
     * @param compatibleWithPrevious If true, the new adapter is using the same View Holders and
     *                               item types with the current adapter (helps us avoid cache
     *                               invalidation).
     * @param removeAndRecycleViews  If true, we'll remove and recycle all existing views. If
     *                               compatibleWithPrevious is false, this parameter is ignored.
     */
    private void setAdapterInternal(@Nullable Adapter adapter, boolean compatibleWithPrevious,
            boolean removeAndRecycleViews) {
        if (mAdapter != null) {
            mAdapter.unregisterAdapterDataObserver(mObserver);
            mAdapter.onDetachedFromRecyclerView(this);
        }
        if (!compatibleWithPrevious || removeAndRecycleViews) {
            removeAndRecycleViews();
        }
        mAdapterHelper.reset();
        final Adapter oldAdapter = mAdapter;
        mAdapter = adapter;
        if (adapter != null) {
            adapter.registerAdapterDataObserver(mObserver);
            adapter.onAttachedToRecyclerView(this);
        }
        if (mLayout != null) {
            mLayout.onAdapterChanged(oldAdapter, mAdapter);
        }
        mRecycler.onAdapterChanged(oldAdapter, mAdapter, compatibleWithPrevious);
        mState.mStructureChanged = true;
    }

    public void prepareForFastScroll() {
        stopScroll();
        removeAndRecycleViews();
        mAdapterHelper.reset();
        mRecycler.onAdapterChanged(mAdapter, mAdapter, false);
        mState.mStructureChanged = true;
        mChildHelper.removeAllViewsUnfiltered();
        mRecycler.updateViewCacheSize();
    }

    /**
     * Retrieves the previously set adapter or null if no adapter is set.
     *
     * @return The previously set adapter
     * @see #setAdapter(Adapter)
     */
    @Nullable
    public Adapter getAdapter() {
        return mAdapter;
    }

    /**
     * Register a listener that will be notified whenever a child view is recycled.
     *
     * <p>This listener will be called when a LayoutManager or the RecyclerView decides
     * that a child view is no longer needed. If an application associates expensive
     * or heavyweight data with item views, this may be a good place to release
     * or free those resources.</p>
     *
     * @param listener Listener to register, or null to clear
     */
    public void setRecyclerListener(@Nullable RecyclerListener listener) {
        mRecyclerListener = listener;
    }

    /**
     * <p>Return the offset of the RecyclerView's text baseline from the its top
     * boundary. If the LayoutManager of this RecyclerView does not support baseline alignment,
     * this method returns -1.</p>
     *
     * @return the offset of the baseline within the RecyclerView's bounds or -1
     *         if baseline alignment is not supported
     */
    @Override
    public int getBaseline() {
        if (mLayout != null) {
            return mLayout.getBaseline();
        } else {
            return super.getBaseline();
        }
    }

    /**
     * Register a listener that will be notified whenever a child view is attached to or detached
     * from RecyclerView.
     *
     * <p>This listener will be called when a LayoutManager or the RecyclerView decides
     * that a child view is no longer needed. If an application associates expensive
     * or heavyweight data with item views, this may be a good place to release
     * or free those resources.</p>
     *
     * @param listener Listener to register
     */
    public void addOnChildAttachStateChangeListener(
            @NonNull OnChildAttachStateChangeListener listener) {
        if (mOnChildAttachStateListeners == null) {
            mOnChildAttachStateListeners = new ArrayList<>();
        }
        mOnChildAttachStateListeners.add(listener);
    }

    /**
     * Removes the provided listener from child attached state listeners list.
     *
     * @param listener Listener to unregister
     */
    public void removeOnChildAttachStateChangeListener(
            @NonNull OnChildAttachStateChangeListener listener) {
        if (mOnChildAttachStateListeners == null) {
            return;
        }
        mOnChildAttachStateListeners.remove(listener);
    }

    /**
     * Removes all listeners that were added via
     * {@link #addOnChildAttachStateChangeListener(OnChildAttachStateChangeListener)}.
     */
    public void clearOnChildAttachStateChangeListeners() {
        if (mOnChildAttachStateListeners != null) {
            mOnChildAttachStateListeners.clear();
        }
    }

    /**
     * Set the {@link LayoutManager} that this RecyclerView will use.
     *
     * <p>In contrast to other adapter-backed views such as {@link android.widget.ListView}
     * or {@link android.widget.GridView}, RecyclerView allows client code to provide custom
     * layout arrangements for child views. These arrangements are controlled by the
     * {@link LayoutManager}. A LayoutManager must be provided for RecyclerView to function.</p>
     *
     * <p>Several default strategies are provided for common uses such as lists and grids.</p>
     *
     * @param layout LayoutManager to use
     */
    public void setLayoutManager(@Nullable LayoutManager layout) {
        if (layout == mLayout) {
            return;
        }
        stopScroll();
        // TODO We should do this switch a dispatchLayout pass and animate children. There is a good
        // chance that LayoutManagers will re-use views.
        if (mLayout != null) {
            // end all running animations
            if (mItemAnimator != null) {
                mItemAnimator.endAnimations();
            }
            mLayout.removeAndRecycleAllViews(mRecycler);
            mLayout.removeAndRecycleScrapInt(mRecycler);
            mRecycler.clear();

            if (mIsAttached) {
                mLayout.dispatchDetachedFromWindow(this, mRecycler);
            }
            mLayout.setRecyclerView(null);
            mLayout = null;
        } else {
            mRecycler.clear();
        }
        // this is just a defensive measure for faulty item animators.
        mChildHelper.removeAllViewsUnfiltered();
        mLayout = layout;
        if (layout != null) {
            if (layout.mRecyclerView != null) {
                throw new IllegalArgumentException("LayoutManager " + layout
                        + " is already attached to a RecyclerView:"
                        + layout.mRecyclerView.exceptionLabel());
            }
            mLayout.setRecyclerView(this);
            if (mIsAttached) {
                mLayout.dispatchAttachedToWindow(this);
            }
        }
        mRecycler.updateViewCacheSize();
        requestLayout();
    }

    /**
     * Set a {@link OnFlingListener} for this {@link RecyclerView}.
     * <p>
     * If the {@link OnFlingListener} is set then it will receive
     * calls to {@link #fling(int,int)} and will be able to intercept them.
     *
     * @param onFlingListener The {@link OnFlingListener} instance.
     */
    public void setOnFlingListener(@Nullable OnFlingListener onFlingListener) {
        mOnFlingListener = onFlingListener;
    }

    /**
     * Get the current {@link OnFlingListener} from this {@link RecyclerView}.
     *
     * @return The {@link OnFlingListener} instance currently set (can be null).
     */
    @Nullable
    public OnFlingListener getOnFlingListener() {
        return mOnFlingListener;
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        SavedState state = new SavedState(super.onSaveInstanceState());
        if (mPendingSavedState != null) {
            state.copyFrom(mPendingSavedState);
        } else if (mLayout != null) {
            state.mLayoutState = mLayout.onSaveInstanceState();
        } else {
            state.mLayoutState = null;
        }

        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }

        mPendingSavedState = (SavedState) state;
        super.onRestoreInstanceState(mPendingSavedState.getSuperState());
        if (mLayout != null && mPendingSavedState.mLayoutState != null) {
            mLayout.onRestoreInstanceState(mPendingSavedState.mLayoutState);
        }
    }

    /**
     * Override to prevent freezing of any views created by the adapter.
     */
    @Override
    protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
        dispatchFreezeSelfOnly(container);
    }

    /**
     * Override to prevent thawing of any views created by the adapter.
     */
    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        dispatchThawSelfOnly(container);
    }

    /**
     * Adds a view to the animatingViews list.
     * mAnimatingViews holds the child views that are currently being kept around
     * purely for the purpose of being animated out of view. They are drawn as a regular
     * part of the child list of the RecyclerView, but they are invisible to the LayoutManager
     * as they are managed separately from the regular child views.
     * @param viewHolder The ViewHolder to be removed
     */
    private void addAnimatingView(ViewHolder viewHolder) {
        final View view = viewHolder.itemView;
        final boolean alreadyParented = view.getParent() == this;
        mRecycler.unscrapView(getChildViewHolder(view));
        if (viewHolder.isTmpDetached()) {
            // re-attach
            mChildHelper.attachViewToParent(view, -1, view.getLayoutParams(), true);
        } else if (!alreadyParented) {
            mChildHelper.addView(view, true);
        } else {
            mChildHelper.hide(view);
        }
    }

    /**
     * Removes a view from the animatingViews list.
     * @param view The view to be removed
     * @see #addAnimatingView(RecyclerView.ViewHolder)
     * @return true if an animating view is removed
     */
    boolean removeAnimatingView(View view) {
        startInterceptRequestLayout();
        final boolean removed = mChildHelper.removeViewIfHidden(view);
        if (removed) {
            final ViewHolder viewHolder = getChildViewHolderInt(view);
            mRecycler.unscrapView(viewHolder);
            mRecycler.recycleViewHolderInternal(viewHolder);
            if (DEBUG) {
                Log.d(TAG, "after removing animated view: " + view + ", " + this);
            }
        }
        // only clear request eaten flag if we removed the view.
        stopInterceptRequestLayout(!removed);
        return removed;
    }

    /**
     * Return the {@link LayoutManager} currently responsible for
     * layout policy for this RecyclerView.
     *
     * @return The currently bound LayoutManager
     */
    @Nullable
    public LayoutManager getLayoutManager() {
        return mLayout;
    }

    /**
     * Retrieve this RecyclerView's {@link RecycledViewPool}. This method will never return null;
     * if no pool is set for this view a new one will be created. See
     * {@link #setRecycledViewPool(RecycledViewPool) setRecycledViewPool} for more information.
     *
     * @return The pool used to store recycled item views for reuse.
     * @see #setRecycledViewPool(RecycledViewPool)
     */
    @NonNull
    public RecycledViewPool getRecycledViewPool() {
        return mRecycler.getRecycledViewPool();
    }

    /**
     * Recycled view pools allow multiple RecyclerViews to share a common pool of scrap views.
     * This can be useful if you have multiple RecyclerViews with adapters that use the same
     * view types, for example if you have several data sets with the same kinds of item views
     * displayed by a {@link androidx.viewpager.widget.ViewPager}.
     *
     * @param pool Pool to set. If this parameter is null a new pool will be created and used.
     */
    public void setRecycledViewPool(@Nullable RecycledViewPool pool) {
        mRecycler.setRecycledViewPool(pool);
    }

    /**
     * Sets a new {@link ViewCacheExtension} to be used by the Recycler.
     *
     * @param extension ViewCacheExtension to be used or null if you want to clear the existing one.
     *
     * @see ViewCacheExtension#getViewForPositionAndType(Recycler, int, int)
     */
    public void setViewCacheExtension(@Nullable ViewCacheExtension extension) {
        mRecycler.setViewCacheExtension(extension);
    }

    /**
     * Set the number of offscreen views to retain before adding them to the potentially shared
     * {@link #getRecycledViewPool() recycled view pool}.
     *
     * <p>The offscreen view cache stays aware of changes in the attached adapter, allowing
     * a LayoutManager to reuse those views unmodified without needing to return to the adapter
     * to rebind them.</p>
     *
     * @param size Number of views to cache offscreen before returning them to the general
     *             recycled view pool
     */
    public void setItemViewCacheSize(int size) {
        mRecycler.setViewCacheSize(size);
    }

    /**
     * Return the current scrolling state of the RecyclerView.
     *
     * @return {@link #SCROLL_STATE_IDLE}, {@link #SCROLL_STATE_DRAGGING} or
     * {@link #SCROLL_STATE_SETTLING}
     */
    public int getScrollState() {
        return mScrollState;
    }

    void setScrollState(int state) {
        if (state == mScrollState) {
            return;
        }
        if (false) {
            Log.d(TAG, "setting scroll state to " + state + " from " + mScrollState,
                    new Exception());
        }
        mScrollState = state;
        if (state != SCROLL_STATE_SETTLING) {
            stopScrollersInternal();
        }
        dispatchOnScrollStateChanged(state);
    }

    /**
     * Add an {@link ItemDecoration} to this RecyclerView. Item decorations can
     * affect both measurement and drawing of individual item views.
     *
     * <p>Item decorations are ordered. Decorations placed earlier in the list will
     * be run/queried/drawn first for their effects on item views. Padding added to views
     * will be nested; a padding added by an earlier decoration will mean further
     * item decorations in the list will be asked to draw/pad within the previous decoration's
     * given area.</p>
     *
     * @param decor Decoration to add
     * @param index Position in the decoration chain to insert this decoration at. If this value
     *              is negative the decoration will be added at the end.
     */
    public void addItemDecoration(@NonNull ItemDecoration decor, int index) {
        if (mLayout != null) {
            mLayout.assertNotInLayoutOrScroll("Cannot add item decoration during a scroll  or"
                    + " layout");
        }
        if (mItemDecorations.isEmpty()) {
            setWillNotDraw(false);
        }
        if (index < 0) {
            mItemDecorations.add(decor);
        } else {
            mItemDecorations.add(index, decor);
        }
        markItemDecorInsetsDirty();
        requestLayout();
    }

    /**
     * Add an {@link ItemDecoration} to this RecyclerView. Item decorations can
     * affect both measurement and drawing of individual item views.
     *
     * <p>Item decorations are ordered. Decorations placed earlier in the list will
     * be run/queried/drawn first for their effects on item views. Padding added to views
     * will be nested; a padding added by an earlier decoration will mean further
     * item decorations in the list will be asked to draw/pad within the previous decoration's
     * given area.</p>
     *
     * @param decor Decoration to add
     */
    public void addItemDecoration(@NonNull ItemDecoration decor) {
        addItemDecoration(decor, -1);
    }

    /**
     * Returns an {@link ItemDecoration} previously added to this RecyclerView.
     *
     * @param index The index position of the desired ItemDecoration.
     * @return the ItemDecoration at index position
     * @throws IndexOutOfBoundsException on invalid index
     */
    @NonNull
    public ItemDecoration getItemDecorationAt(int index) {
        final int size = getItemDecorationCount();
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(index + " is an invalid index for size " + size);
        }

        return mItemDecorations.get(index);
    }

    /**
     * Returns the number of {@link ItemDecoration} currently added to this RecyclerView.
     *
     * @return number of ItemDecorations currently added added to this RecyclerView.
     */
    public int getItemDecorationCount() {
        return mItemDecorations.size();
    }

    /**
     * Removes the {@link ItemDecoration} associated with the supplied index position.
     *
     * @param index The index position of the ItemDecoration to be removed.
     */
    public void removeItemDecorationAt(int index) {
        final int size = getItemDecorationCount();
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException(index + " is an invalid index for size " + size);
        }

        removeItemDecoration(getItemDecorationAt(index));
    }

    /**
     * Remove an {@link ItemDecoration} from this RecyclerView.
     *
     * <p>The given decoration will no longer impact the measurement and drawing of
     * item views.</p>
     *
     * @param decor Decoration to remove
     * @see #addItemDecoration(ItemDecoration)
     */
    public void removeItemDecoration(@NonNull ItemDecoration decor) {
        if (mLayout != null) {
            mLayout.assertNotInLayoutOrScroll("Cannot remove item decoration during a scroll  or"
                    + " layout");
        }
        mItemDecorations.remove(decor);
        if (mItemDecorations.isEmpty()) {
            setWillNotDraw(getOverScrollMode() == View.OVER_SCROLL_NEVER);
        }
        markItemDecorInsetsDirty();
        requestLayout();
    }

    /**
     * Sets the {@link ChildDrawingOrderCallback} to be used for drawing children.
     * <p>
     * See {@link ViewGroup#getChildDrawingOrder(int, int)} for details. Calling this method will
     * always call {@link ViewGroup#setChildrenDrawingOrderEnabled(boolean)}. The parameter will be
     * true if childDrawingOrderCallback is not null, false otherwise.
     * <p>
     * Note that child drawing order may be overridden by View's elevation.
     *
     * @param childDrawingOrderCallback The ChildDrawingOrderCallback to be used by the drawing
     *                                  system.
     */
    public void setChildDrawingOrderCallback(
            @Nullable ChildDrawingOrderCallback childDrawingOrderCallback) {
        if (childDrawingOrderCallback == mChildDrawingOrderCallback) {
            return;
        }
        mChildDrawingOrderCallback = childDrawingOrderCallback;
        setChildrenDrawingOrderEnabled(mChildDrawingOrderCallback != null);
    }

    /**
     * Set a listener that will be notified of any changes in scroll state or position.
     *
     * @param listener Listener to set or null to clear
     *
     * @deprecated Use {@link #addOnScrollListener(OnScrollListener)} and
     *             {@link #removeOnScrollListener(OnScrollListener)}
     */
    @Deprecated
    public void setOnScrollListener(@Nullable OnScrollListener listener) {
        mScrollListener = listener;
    }

    /**
     * Add a listener that will be notified of any changes in scroll state or position.
     *
     * <p>Components that add a listener should take care to remove it when finished.
     * Other components that take ownership of a view may call {@link #clearOnScrollListeners()}
     * to remove all attached listeners.</p>
     *
     * @param listener listener to set
     */
    public void addOnScrollListener(@NonNull OnScrollListener listener) {
        if (mScrollListeners == null) {
            mScrollListeners = new ArrayList<>();
        }
        mScrollListeners.add(listener);
    }

    /**
     * Remove a listener that was notified of any changes in scroll state or position.
     *
     * @param listener listener to set or null to clear
     */
    public void removeOnScrollListener(@NonNull OnScrollListener listener) {
        if (mScrollListeners != null) {
            mScrollListeners.remove(listener);
        }
    }

    /**
     * Remove all secondary listener that were notified of any changes in scroll state or position.
     */
    public void clearOnScrollListeners() {
        if (mScrollListeners != null) {
            mScrollListeners.clear();
        }
    }

    /**
     * Convenience method to scroll to a certain position.
     *
     * RecyclerView does not implement scrolling logic, rather forwards the call to
     * {@link RecyclerView.LayoutManager#scrollToPosition(int)}
     * @param position Scroll to this adapter position
     * @see RecyclerView.LayoutManager#scrollToPosition(int)
     */
    public void scrollToPosition(int position) {
        if (mLayoutSuppressed) {
            return;
        }
        stopScroll();
        if (mLayout == null) {
            Log.e(TAG, "Cannot scroll to position a LayoutManager set. "
                    + "Call setLayoutManager with a non-null argument.");
            return;
        }
        mLayout.scrollToPosition(position);
        awakenScrollBars();
    }

    void jumpToPositionForSmoothScroller(int position) {
        if (mLayout == null) {
            return;
        }

        // If we are jumping to a position, we are in fact scrolling the contents of the RV, so
        // we should be sure that we are in the settling state.
        setScrollState(SCROLL_STATE_SETTLING);
        mLayout.scrollToPosition(position);
        awakenScrollBars();
    }

    /**
     * Starts a smooth scroll to an adapter position.
     * <p>
     * To support smooth scrolling, you must override
     * {@link LayoutManager#smoothScrollToPosition(RecyclerView, State, int)} and create a
     * {@link SmoothScroller}.
     * <p>
     * {@link LayoutManager} is responsible for creating the actual scroll action. If you want to
     * provide a custom smooth scroll logic, override
     * {@link LayoutManager#smoothScrollToPosition(RecyclerView, State, int)} in your
     * LayoutManager.
     *
     * @param position The adapter position to scroll to
     * @see LayoutManager#smoothScrollToPosition(RecyclerView, State, int)
     */
    public void smoothScrollToPosition(int position) {
        if (mLayoutSuppressed) {
            return;
        }
        if (mLayout == null) {
            Log.e(TAG, "Cannot smooth scroll without a LayoutManager set. "
                    + "Call setLayoutManager with a non-null argument.");
            return;
        }
        mLayout.smoothScrollToPosition(this, mState, position);
    }

    @Override
    public void scrollTo(int x, int y) {
        Log.w(TAG, "RecyclerView does not support scrolling to an absolute position. "
                + "Use scrollToPosition instead");
    }

    @Override
    public void scrollBy(int x, int y) {
        if (mLayout == null) {
            Log.e(TAG, "Cannot scroll without a LayoutManager set. "
                    + "Call setLayoutManager with a non-null argument.");
            return;
        }
        if (mLayoutSuppressed) {
            return;
        }
        final boolean canScrollHorizontal = mLayout.canScrollHorizontally();
        final boolean canScrollVertical = mLayout.canScrollVertically();
        if (canScrollHorizontal || canScrollVertical) {
            scrollByInternal(canScrollHorizontal ? x : 0, canScrollVertical ? y : 0, null);
        }
    }

    /**
     * Scrolls the RV by 'dx' and 'dy' via calls to
     * {@link LayoutManager#scrollHorizontallyBy(int, Recycler, State)} and
     * {@link LayoutManager#scrollVerticallyBy(int, Recycler, State)}.
     *
     * Also sets how much of the scroll was actually consumed in 'consumed' parameter (indexes 0 and
     * 1 for the x axis and y axis, respectively).
     *
     * This method should only be called in the context of an existing scroll operation such that
     * any other necessary operations (such as a call to {@link #consumePendingUpdateOperations()})
     * is already handled.
     */
    void scrollStep(int dx, int dy, @Nullable int[] consumed) {
        startInterceptRequestLayout();
        onEnterLayoutOrScroll();

        TraceCompat.beginSection(TRACE_SCROLL_TAG);
        fillRemainingScrollValues(mState);

        int consumedX = 0;
        int consumedY = 0;
        if (dx != 0) {
            consumedX = mLayout.scrollHorizontallyBy(dx, mRecycler, mState);
        }
        if (dy != 0) {
            consumedY = mLayout.scrollVerticallyBy(dy, mRecycler, mState);
        }

        TraceCompat.endSection();
        repositionShadowingViews();

        onExitLayoutOrScroll();
        stopInterceptRequestLayout(false);

        if (consumed != null) {
            consumed[0] = consumedX;
            consumed[1] = consumedY;
        }
    }

    /**
     * Helper method reflect data changes to the state.
     * <p>
     * Adapter changes during a scroll may trigger a crash because scroll assumes no data change
     * but data actually changed.
     * <p>
     * This method consumes all deferred changes to avoid that case.
     */
    void consumePendingUpdateOperations() {
        if (!mFirstLayoutComplete || mDataSetHasChangedAfterLayout) {
            TraceCompat.beginSection(TRACE_ON_DATA_SET_CHANGE_LAYOUT_TAG);
            dispatchLayout();
            TraceCompat.endSection();
            return;
        }
        if (!mAdapterHelper.hasPendingUpdates()) {
            return;
        }

        // if it is only an item change (no add-remove-notifyDataSetChanged) we can check if any
        // of the visible items is affected and if not, just ignore the change.
        if (mAdapterHelper.hasAnyUpdateTypes(AdapterHelper.UpdateOp.UPDATE) && !mAdapterHelper
                .hasAnyUpdateTypes(AdapterHelper.UpdateOp.ADD | AdapterHelper.UpdateOp.REMOVE
                        | AdapterHelper.UpdateOp.MOVE)) {
            TraceCompat.beginSection(TRACE_HANDLE_ADAPTER_UPDATES_TAG);
            startInterceptRequestLayout();
            onEnterLayoutOrScroll();
            mAdapterHelper.preProcess();
            if (!mLayoutWasDefered) {
                if (hasUpdatedView()) {
                    dispatchLayout();
                } else {
                    // no need to layout, clean state
                    mAdapterHelper.consumePostponedUpdates();
                }
            }
            stopInterceptRequestLayout(true);
            onExitLayoutOrScroll();
            TraceCompat.endSection();
        } else if (mAdapterHelper.hasPendingUpdates()) {
            TraceCompat.beginSection(TRACE_ON_DATA_SET_CHANGE_LAYOUT_TAG);
            dispatchLayout();
            TraceCompat.endSection();
        }
    }

    /**
     * @return True if an existing view holder needs to be updated
     */
    private boolean hasUpdatedView() {
        final int childCount = mChildHelper.getChildCount();
        for (int i = 0; i < childCount; i++) {
            final ViewHolder holder = getChildViewHolderInt(mChildHelper.getChildAt(i));
            if (holder == null || holder.shouldIgnore()) {
                continue;
            }
            if (holder.isUpdated()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Does not perform bounds checking. Used by internal methods that have already validated input.
     * <p>
     * It also reports any unused scroll request to the related EdgeEffect.
     *
     * @param x The amount of horizontal scroll request
     * @param y The amount of vertical scroll request
     * @param ev The originating MotionEvent, or null if not from a touch event.
     *
     * @return Whether any scroll was consumed in either direction.
     */
    boolean scrollByInternal(int x, int y, MotionEvent ev) {
        int unconsumedX = 0;
        int unconsumedY = 0;
        int consumedX = 0;
        int consumedY = 0;

        consumePendingUpdateOperations();
        if (mAdapter != null) {
            mReusableIntPair[0] = 0;
            mReusableIntPair[1] = 0;
            scrollStep(x, y, mReusableIntPair);
            consumedX = mReusableIntPair[0];
            consumedY = mReusableIntPair[1];
            unconsumedX = x - consumedX;
            unconsumedY = y - consumedY;
        }
        if (!mItemDecorations.isEmpty()) {
            invalidate();
        }

        mReusableIntPair[0] = 0;
        mReusableIntPair[1] = 0;
        dispatchNestedScroll(consumedX, consumedY, unconsumedX, unconsumedY, mScrollOffset,
                TYPE_TOUCH, mReusableIntPair);
        unconsumedX -= mReusableIntPair[0];
        unconsumedY -= mReusableIntPair[1];

        // Update the last touch co-ords, taking any scroll offset into account
        mLastTouchX -= mScrollOffset[0];
        mLastTouchY -= mScrollOffset[1];
        if (ev != null) {
            ev.offsetLocation(mScrollOffset[0], mScrollOffset[1]);
        }
        mNestedOffsets[0] += mScrollOffset[0];
        mNestedOffsets[1] += mScrollOffset[1];

        if (getOverScrollMode() != View.OVER_SCROLL_NEVER) {
            if (ev != null && !MotionEventCompat.isFromSource(ev, InputDevice.SOURCE_MOUSE)) {
                pullGlows(ev.getX(), unconsumedX, ev.getY(), unconsumedY);
            }
            considerReleasingGlowsOnScroll(x, y);
        }
        if (consumedX != 0 || consumedY != 0) {
            dispatchOnScrolled(consumedX, consumedY);
        }
        if (!awakenScrollBars()) {
            invalidate();
        }
        return consumedX != 0 || consumedY != 0;
    }

    /**
     * <p>Compute the horizontal offset of the horizontal scrollbar's thumb within the horizontal
     * range. This value is used to compute the length of the thumb within the scrollbar's track.
     * </p>
     *
     * <p>The range is expressed in arbitrary units that must be the same as the units used by
     * {@link #computeHorizontalScrollRange()} and {@link #computeHorizontalScrollExtent()}.</p>
     *
     * <p>Default implementation returns 0.</p>
     *
     * <p>If you want to support scroll bars, override
     * {@link RecyclerView.LayoutManager#computeHorizontalScrollOffset(RecyclerView.State)} in your
     * LayoutManager. </p>
     *
     * @return The horizontal offset of the scrollbar's thumb
     * @see RecyclerView.LayoutManager#computeHorizontalScrollOffset
     * (RecyclerView.State)
     */
    @Override
    public int computeHorizontalScrollOffset() {
        if (mLayout == null) {
            return 0;
        }
        return mLayout.canScrollHorizontally() ? mLayout.computeHorizontalScrollOffset(mState) : 0;
    }

    /**
     * <p>Compute the horizontal extent of the horizontal scrollbar's thumb within the
     * horizontal range. This value is used to compute the length of the thumb within the
     * scrollbar's track.</p>
     *
     * <p>The range is expressed in arbitrary units that must be the same as the units used by
     * {@link #computeHorizontalScrollRange()} and {@link #computeHorizontalScrollOffset()}.</p>
     *
     * <p>Default implementation returns 0.</p>
     *
     * <p>If you want to support scroll bars, override
     * {@link RecyclerView.LayoutManager#computeHorizontalScrollExtent(RecyclerView.State)} in your
     * LayoutManager.</p>
     *
     * @return The horizontal extent of the scrollbar's thumb
     * @see RecyclerView.LayoutManager#computeHorizontalScrollExtent(RecyclerView.State)
     */
    @Override
    public int computeHorizontalScrollExtent() {
        if (mLayout == null) {
            return 0;
        }
        return mLayout.canScrollHorizontally() ? mLayout.computeHorizontalScrollExtent(mState) : 0;
    }

    /**
     * <p>Compute the horizontal range that the horizontal scrollbar represents.</p>
     *
     * <p>The range is expressed in arbitrary units that must be the same as the units used by
     * {@link #computeHorizontalScrollExtent()} and {@link #computeHorizontalScrollOffset()}.</p>
     *
     * <p>Default implementation returns 0.</p>
     *
     * <p>If you want to support scroll bars, override
     * {@link RecyclerView.LayoutManager#computeHorizontalScrollRange(RecyclerView.State)} in your
     * LayoutManager.</p>
     *
     * @return The total horizontal range represented by the vertical scrollbar
     * @see RecyclerView.LayoutManager#computeHorizontalScrollRange(RecyclerView.State)
     */
    @Override
    public int computeHorizontalScrollRange() {
        if (mLayout == null) {
            return 0;
        }
        return mLayout.canScrollHorizontally() ? mLayout.computeHorizontalScrollRange(mState) : 0;
    }

    /**
     * <p>Compute the vertical offset of the vertical scrollbar's thumb within the vertical range.
     * This value is used to compute the length of the thumb within the scrollbar's track. </p>
     *
     * <p>The range is expressed in arbitrary units that must be the same as the units used by
     * {@link #computeVerticalScrollRange()} and {@link #computeVerticalScrollExtent()}.</p>
     *
     * <p>Default implementation returns 0.</p>
     *
     * <p>If you want to support scroll bars, override
     * {@link RecyclerView.LayoutManager#computeVerticalScrollOffset(RecyclerView.State)} in your
     * LayoutManager.</p>
     *
     * @return The vertical offset of the scrollbar's thumb
     * @see RecyclerView.LayoutManager#computeVerticalScrollOffset
     * (RecyclerView.State)
     */
    @Override
    public int computeVerticalScrollOffset() {
        if (mLayout == null) {
            return 0;
        }
        return mLayout.canScrollVertically() ? mLayout.computeVerticalScrollOffset(mState) : 0;
    }

    /**
     * <p>Compute the vertical extent of the vertical scrollbar's thumb within the vertical range.
     * This value is used to compute the length of the thumb within the scrollbar's track.</p>
     *
     * <p>The range is expressed in arbitrary units that must be the same as the units used by
     * {@link #computeVerticalScrollRange()} and {@link #computeVerticalScrollOffset()}.</p>
     *
     * <p>Default implementation returns 0.</p>
     *
     * <p>If you want to support scroll bars, override
     * {@link RecyclerView.LayoutManager#computeVerticalScrollExtent(RecyclerView.State)} in your
     * LayoutManager.</p>
     *
     * @return The vertical extent of the scrollbar's thumb
     * @see RecyclerView.LayoutManager#computeVerticalScrollExtent(RecyclerView.State)
     */
    @Override
    public int computeVerticalScrollExtent() {
        if (mLayout == null) {
            return 0;
        }
        return mLayout.canScrollVertically() ? mLayout.computeVerticalScrollExtent(mState) : 0;
    }

    /**
     * <p>Compute the vertical range that the vertical scrollbar represents.</p>
     *
     * <p>The range is expressed in arbitrary units that must be the same as the units used by
     * {@link #computeVerticalScrollExtent()} and {@link #computeVerticalScrollOffset()}.</p>
     *
     * <p>Default implementation returns 0.</p>
     *
     * <p>If you want to support scroll bars, override
     * {@link RecyclerView.LayoutManager#computeVerticalScrollRange(RecyclerView.State)} in your
     * LayoutManager.</p>
     *
     * @return The total vertical range represented by the vertical scrollbar
     * @see RecyclerView.LayoutManager#computeVerticalScrollRange(RecyclerView.State)
     */
    @Override
    public int computeVerticalScrollRange() {
        if (mLayout == null) {
            return 0;
        }
        return mLayout.canScrollVertically() ? mLayout.computeVerticalScrollRange(mState) : 0;
    }

    /**
     * This method should be called before any code that may trigger a child view to cause a call to
     * {@link RecyclerView#requestLayout()}.  Doing so enables {@link RecyclerView} to avoid
     * reacting to additional redundant calls to {@link #requestLayout()}.
     * <p>
     * A call to this method must always be accompanied by a call to
     * {@link #stopInterceptRequestLayout(boolean)} that follows the code that may trigger a
     * child View to cause a call to {@link RecyclerView#requestLayout()}.
     *
     * @see #stopInterceptRequestLayout(boolean)
     */
    void startInterceptRequestLayout() {
        mInterceptRequestLayoutDepth++;
        if (mInterceptRequestLayoutDepth == 1 && !mLayoutSuppressed) {
            mLayoutWasDefered = false;
        }
    }

    /**
     * This method should be called after any code that may trigger a child view to cause a call to
     * {@link RecyclerView#requestLayout()}.
     * <p>
     * A call to this method must always be accompanied by a call to
     * {@link #startInterceptRequestLayout()} that precedes the code that may trigger a child
     * View to cause a call to {@link RecyclerView#requestLayout()}.
     *
     * @see #startInterceptRequestLayout()
     */
    void stopInterceptRequestLayout(boolean performLayoutChildren) {
        if (mInterceptRequestLayoutDepth < 1) {
            //noinspection PointlessBooleanExpression
            if (DEBUG) {
                throw new IllegalStateException("stopInterceptRequestLayout was called more "
                        + "times than startInterceptRequestLayout."
                        + exceptionLabel());
            }
            mInterceptRequestLayoutDepth = 1;
        }
        if (!performLayoutChildren && !mLayoutSuppressed) {
            // Reset the layout request eaten counter.
            // This is necessary since eatRequest calls can be nested in which case the other
            // call will override the inner one.
            // for instance:
            // eat layout for process adapter updates
            //   eat layout for dispatchLayout
            //     a bunch of req layout calls arrive

            mLayoutWasDefered = false;
        }
        if (mInterceptRequestLayoutDepth == 1) {
            // when layout is frozen we should delay dispatchLayout()
            if (performLayoutChildren && mLayoutWasDefered && !mLayoutSuppressed
                    && mLayout != null && mAdapter != null) {
                dispatchLayout();
            }
            if (!mLayoutSuppressed) {
                mLayoutWasDefered = false;
            }
        }
        mInterceptRequestLayoutDepth--;
    }

    /**
     * Tells this RecyclerView to suppress all layout and scroll calls until layout
     * suppression is disabled with a later call to suppressLayout(false).
     * When layout suppression is disabled, a requestLayout() call is sent
     * if requestLayout() was attempted while layout was being suppressed.
     * <p>
     * In addition to the layout suppression {@link #smoothScrollBy(int, int)},
     * {@link #scrollBy(int, int)}, {@link #scrollToPosition(int)} and
     * {@link #smoothScrollToPosition(int)} are dropped; TouchEvents and GenericMotionEvents are
     * dropped; {@link LayoutManager#onFocusSearchFailed(View, int, Recycler, State)} will not be
     * called.
     *
     * <p>
     * <code>suppressLayout(true)</code> does not prevent app from directly calling {@link
     * LayoutManager#scrollToPosition(int)}, {@link LayoutManager#smoothScrollToPosition(
     * RecyclerView, State, int)}.
     * <p>
     * {@link #setAdapter(Adapter)} and {@link #swapAdapter(Adapter, boolean)} will automatically
     * stop suppressing.
     * <p>
     * Note: Running ItemAnimator is not stopped automatically,  it's caller's
     * responsibility to call ItemAnimator.end().
     *
     * @param suppress true to suppress layout and scroll, false to re-enable.
     */
    public final void suppressLayout(boolean suppress) {
        if (suppress != mLayoutSuppressed) {
            assertNotInLayoutOrScroll("Do not suppressLayout in layout or scroll");
            if (!suppress) {
                mLayoutSuppressed = false;
                if (mLayoutWasDefered && mLayout != null && mAdapter != null) {
                    requestLayout();
                }
                mLayoutWasDefered = false;
            } else {
                final long now = SystemClock.uptimeMillis();
                MotionEvent cancelEvent = MotionEvent.obtain(now, now,
                        MotionEvent.ACTION_CANCEL, 0.0f, 0.0f, 0);
                onTouchEvent(cancelEvent);
                mLayoutSuppressed = true;
                mIgnoreMotionEventTillDown = true;
                stopScroll();
            }
        }
    }

    /**
     * Returns whether layout and scroll calls on this container are currently being
     * suppressed, due to an earlier call to {@link #suppressLayout(boolean)}.
     *
     * @return true if layout and scroll are currently suppressed, false otherwise.
     */
    public final boolean isLayoutSuppressed() {
        return mLayoutSuppressed;
    }

    /**
     * Enable or disable layout and scroll.  After <code>setLayoutFrozen(true)</code> is called,
     * Layout requests will be postponed until <code>setLayoutFrozen(false)</code> is called;
     * child views are not updated when RecyclerView is frozen, {@link #smoothScrollBy(int, int)},
     * {@link #scrollBy(int, int)}, {@link #scrollToPosition(int)} and
     * {@link #smoothScrollToPosition(int)} are dropped; TouchEvents and GenericMotionEvents are
     * dropped; {@link LayoutManager#onFocusSearchFailed(View, int, Recycler, State)} will not be
     * called.
     *
     * <p>
     * <code>setLayoutFrozen(true)</code> does not prevent app from directly calling {@link
     * LayoutManager#scrollToPosition(int)}, {@link LayoutManager#smoothScrollToPosition(
     * RecyclerView, State, int)}.
     * <p>
     * {@link #setAdapter(Adapter)} and {@link #swapAdapter(Adapter, boolean)} will automatically
     * stop frozen.
     * <p>
     * Note: Running ItemAnimator is not stopped automatically,  it's caller's
     * responsibility to call ItemAnimator.end().
     *
     * @param frozen   true to freeze layout and scroll, false to re-enable.
     *
     * @deprecated Use {@link #suppressLayout(boolean)}.
     */
    @Deprecated
    public void setLayoutFrozen(boolean frozen) {
        suppressLayout(frozen);
    }

    /**
     * @return true if layout and scroll are frozen
     *
     * @deprecated Use {@link #isLayoutSuppressed()}.
     */
    @Deprecated
    public boolean isLayoutFrozen() {
        return isLayoutSuppressed();
    }

    /**
     * @deprecated Use {@link #setItemAnimator(ItemAnimator)} ()}.
     */
    @Deprecated
    @Override
    public void setLayoutTransition(LayoutTransition transition) {
        if (transition == null) {
            super.setLayoutTransition(null);
        } else {
            throw new IllegalArgumentException("Providing a LayoutTransition into RecyclerView is "
                    + "not supported. Please use setItemAnimator() instead for animating changes "
                    + "to the items in this RecyclerView");
        }
    }

    /**
     * Animate a scroll by the given amount of pixels along either axis.
     *
     * @param dx Pixels to scroll horizontally
     * @param dy Pixels to scroll vertically
     */
    public void smoothScrollBy(@Px int dx, @Px int dy) {
        smoothScrollBy(dx, dy, null);
    }

    /**
     * Animate a scroll by the given amount of pixels along either axis.
     *
     * @param dx Pixels to scroll horizontally
     * @param dy Pixels to scroll vertically
     * @param interpolator {@link Interpolator} to be used for scrolling. If it is
     *                     {@code null}, RecyclerView is going to use the default interpolator.
     */
    public void smoothScrollBy(@Px int dx, @Px int dy, @Nullable Interpolator interpolator) {
        if (mLayout == null) {
            Log.e(TAG, "Cannot smooth scroll without a LayoutManager set. "
                    + "Call setLayoutManager with a non-null argument.");
            return;
        }
        if (mLayoutSuppressed) {
            return;
        }
        if (!mLayout.canScrollHorizontally()) {
            dx = 0;
        }
        if (!mLayout.canScrollVertically()) {
            dy = 0;
        }
        if (dx != 0 || dy != 0) {
            mViewFlinger.smoothScrollBy(dx, dy, UNDEFINED_DURATION, interpolator);
        }
    }

    /**
     * Begin a standard fling with an initial velocity along each axis in pixels per second.
     * If the velocity given is below the system-defined minimum this method will return false
     * and no fling will occur.
     *
     * @param velocityX Initial horizontal velocity in pixels per second
     * @param velocityY Initial vertical velocity in pixels per second
     * @return true if the fling was started, false if the velocity was too low to fling or
     * LayoutManager does not support scrolling in the axis fling is issued.
     *
     * @see LayoutManager#canScrollVertically()
     * @see LayoutManager#canScrollHorizontally()
     */
    public boolean fling(int velocityX, int velocityY) {
        if (mLayout == null) {
            Log.e(TAG, "Cannot fling without a LayoutManager set. "
                    + "Call setLayoutManager with a non-null argument.");
            return false;
        }
        if (mLayoutSuppressed) {
            return false;
        }

        final boolean canScrollHorizontal = mLayout.canScrollHorizontally();
        final boolean canScrollVertical = mLayout.canScrollVertically();

        if (!canScrollHorizontal || Math.abs(velocityX) < mMinFlingVelocity) {
            velocityX = 0;
        }
        if (!canScrollVertical || Math.abs(velocityY) < mMinFlingVelocity) {
            velocityY = 0;
        }
        if (velocityX == 0 && velocityY == 0) {
            // If we don't have any velocity, return false
            return false;
        }

        if (!dispatchNestedPreFling(velocityX, velocityY)) {
            final boolean canScroll = canScrollHorizontal || canScrollVertical;
            dispatchNestedFling(velocityX, velocityY, canScroll);

            if (mOnFlingListener != null && mOnFlingListener.onFling(velocityX, velocityY)) {
                return true;
            }

            if (canScroll) {
                int nestedScrollAxis = ViewCompat.SCROLL_AXIS_NONE;
                if (canScrollHorizontal) {
                    nestedScrollAxis |= ViewCompat.SCROLL_AXIS_HORIZONTAL;
                }
                if (canScrollVertical) {
                    nestedScrollAxis |= ViewCompat.SCROLL_AXIS_VERTICAL;
                }
                startNestedScroll(nestedScrollAxis, TYPE_NON_TOUCH);

                velocityX = Math.max(-mMaxFlingVelocity, Math.min(velocityX, mMaxFlingVelocity));
                velocityY = Math.max(-mMaxFlingVelocity, Math.min(velocityY, mMaxFlingVelocity));
                mViewFlinger.fling(velocityX, velocityY);
                return true;
            }
        }
        return false;
    }

    /**
     * Stop any current scroll in progress, such as one started by
     * {@link #smoothScrollBy(int, int)}, {@link #fling(int, int)} or a touch-initiated fling.
     */
    public void stopScroll() {
        setScrollState(SCROLL_STATE_IDLE);
        stopScrollersInternal();
    }

    /**
     * Similar to {@link #stopScroll()} but does not set the state.
     */
    private void stopScrollersInternal() {
        mViewFlinger.stop();
        if (mLayout != null) {
            mLayout.stopSmoothScroller();
        }
    }

    /**
     * Returns the minimum velocity to start a fling.
     *
     * @return The minimum velocity to start a fling
     */
    public int getMinFlingVelocity() {
        return mMinFlingVelocity;
    }


    /**
     * Returns the maximum fling velocity used by this RecyclerView.
     *
     * @return The maximum fling velocity used by this RecyclerView.
     */
    public int getMaxFlingVelocity() {
        return mMaxFlingVelocity;
    }

    /**
     * Apply a pull to relevant overscroll glow effects
     */
    private void pullGlows(float x, float overscrollX, float y, float overscrollY) {
        boolean invalidate = false;
        if (overscrollX < 0) {
            ensureLeftGlow();
            EdgeEffectCompat.onPull(mLeftGlow, -overscrollX / getWidth(), 1f - y  / getHeight());
            invalidate = true;
        } else if (overscrollX > 0) {
            ensureRightGlow();
            EdgeEffectCompat.onPull(mRightGlow, overscrollX / getWidth(), y / getHeight());
            invalidate = true;
        }

        if (overscrollY < 0) {
            ensureTopGlow();
            EdgeEffectCompat.onPull(mTopGlow, -overscrollY / getHeight(), x / getWidth());
            invalidate = true;
        } else if (overscrollY > 0) {
            ensureBottomGlow();
            EdgeEffectCompat.onPull(mBottomGlow, overscrollY / getHeight(), 1f - x / getWidth());
            invalidate = true;
        }

        if (invalidate || overscrollX != 0 || overscrollY != 0) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    private void releaseGlows() {
        boolean needsInvalidate = false;
        if (mLeftGlow != null) {
            mLeftGlow.onRelease();
            needsInvalidate = mLeftGlow.isFinished();
        }
        if (mTopGlow != null) {
            mTopGlow.onRelease();
            needsInvalidate |= mTopGlow.isFinished();
        }
        if (mRightGlow != null) {
            mRightGlow.onRelease();
            needsInvalidate |= mRightGlow.isFinished();
        }
        if (mBottomGlow != null) {
            mBottomGlow.onRelease();
            needsInvalidate |= mBottomGlow.isFinished();
        }
        if (needsInvalidate) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    void considerReleasingGlowsOnScroll(int dx, int dy) {
        boolean needsInvalidate = false;
        if (mLeftGlow != null && !mLeftGlow.isFinished() && dx > 0) {
            mLeftGlow.onRelease();
            needsInvalidate = mLeftGlow.isFinished();
        }
        if (mRightGlow != null && !mRightGlow.isFinished() && dx < 0) {
            mRightGlow.onRelease();
            needsInvalidate |= mRightGlow.isFinished();
        }
        if (mTopGlow != null && !mTopGlow.isFinished() && dy > 0) {
            mTopGlow.onRelease();
            needsInvalidate |= mTopGlow.isFinished();
        }
        if (mBottomGlow != null && !mBottomGlow.isFinished() && dy < 0) {
            mBottomGlow.onRelease();
            needsInvalidate |= mBottomGlow.isFinished();
        }
        if (needsInvalidate) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    void absorbGlows(int velocityX, int velocityY) {
        if (velocityX < 0) {
            ensureLeftGlow();
            if (mLeftGlow.isFinished()) {
                mLeftGlow.onAbsorb(-velocityX);
            }
        } else if (velocityX > 0) {
            ensureRightGlow();
            if (mRightGlow.isFinished()) {
                mRightGlow.onAbsorb(velocityX);
            }
        }

        if (velocityY < 0) {
            ensureTopGlow();
            if (mTopGlow.isFinished()) {
                mTopGlow.onAbsorb(-velocityY);
            }
        } else if (velocityY > 0) {
            ensureBottomGlow();
            if (mBottomGlow.isFinished()) {
                mBottomGlow.onAbsorb(velocityY);
            }
        }

        if (velocityX != 0 || velocityY != 0) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    void ensureLeftGlow() {
        if (mLeftGlow != null) {
            return;
        }
        mLeftGlow = mEdgeEffectFactory.createEdgeEffect(this, EdgeEffectFactory.DIRECTION_LEFT);
        if (mClipToPadding) {
            mLeftGlow.setSize(getMeasuredHeight() - getPaddingTop() - getPaddingBottom(),
                    getMeasuredWidth() - getPaddingLeft() - getPaddingRight());
        } else {
            mLeftGlow.setSize(getMeasuredHeight(), getMeasuredWidth());
        }
        applyEdgeEffectColor(mLeftGlow);
    }

    void ensureRightGlow() {
        if (mRightGlow != null) {
            return;
        }
        mRightGlow = mEdgeEffectFactory.createEdgeEffect(this, EdgeEffectFactory.DIRECTION_RIGHT);
        if (mClipToPadding) {
            mRightGlow.setSize(getMeasuredHeight() - getPaddingTop() - getPaddingBottom(),
                    getMeasuredWidth() - getPaddingLeft() - getPaddingRight());
        } else {
            mRightGlow.setSize(getMeasuredHeight(), getMeasuredWidth());
        }
        applyEdgeEffectColor(mRightGlow);
    }

    void ensureTopGlow() {
        if (mTopGlow != null) {
            return;
        }
        mTopGlow = mEdgeEffectFactory.createEdgeEffect(this, EdgeEffectFactory.DIRECTION_TOP);
        if (mClipToPadding) {
            mTopGlow.setSize(getMeasuredWidth() - getPaddingLeft() - getPaddingRight(),
                    getMeasuredHeight() - getPaddingTop() - getPaddingBottom());
        } else {
            mTopGlow.setSize(getMeasuredWidth(), getMeasuredHeight());
        }
        applyEdgeEffectColor(mTopGlow);
    }

    void ensureBottomGlow() {
        if (mBottomGlow != null) {
            return;
        }
        mBottomGlow = mEdgeEffectFactory.createEdgeEffect(this, EdgeEffectFactory.DIRECTION_BOTTOM);
        if (mClipToPadding) {
            mBottomGlow.setSize(getMeasuredWidth() - getPaddingLeft() - getPaddingRight(),
                    getMeasuredHeight() - getPaddingTop() - getPaddingBottom());
        } else {
            mBottomGlow.setSize(getMeasuredWidth(), getMeasuredHeight());
        }
        applyEdgeEffectColor(mBottomGlow);
    }

    void invalidateGlows() {
        mLeftGlow = mRightGlow = mTopGlow = mBottomGlow = null;
    }

    /**
     * Set a {@link EdgeEffectFactory} for this {@link RecyclerView}.
     * <p>
     * When a new {@link EdgeEffectFactory} is set, any existing over-scroll effects are cleared
     * and new effects are created as needed using
     * {@link EdgeEffectFactory#createEdgeEffect(RecyclerView, int)}
     *
     * @param edgeEffectFactory The {@link EdgeEffectFactory} instance.
     */
    public void setEdgeEffectFactory(@NonNull EdgeEffectFactory edgeEffectFactory) {
        Preconditions.checkNotNull(edgeEffectFactory);
        mEdgeEffectFactory = edgeEffectFactory;
        invalidateGlows();
    }

    /**
     * Retrieves the previously set {@link EdgeEffectFactory} or the default factory if nothing
     * was set.
     *
     * @return The previously set {@link EdgeEffectFactory}
     * @see #setEdgeEffectFactory(EdgeEffectFactory)
     */
    @NonNull
    public EdgeEffectFactory getEdgeEffectFactory() {
        return mEdgeEffectFactory;
    }

    /**
     * Since RecyclerView is a collection ViewGroup that includes virtual children (items that are
     * in the Adapter but not visible in the UI), it employs a more involved focus search strategy
     * that differs from other ViewGroups.
     * <p>
     * It first does a focus search within the RecyclerView. If this search finds a View that is in
     * the focus direction with respect to the currently focused View, RecyclerView returns that
     * child as the next focus target. When it cannot find such child, it calls
     * {@link LayoutManager#onFocusSearchFailed(View, int, Recycler, State)} to layout more Views
     * in the focus search direction. If LayoutManager adds a View that matches the
     * focus search criteria, it will be returned as the focus search result. Otherwise,
     * RecyclerView will call parent to handle the focus search like a regular ViewGroup.
     * <p>
     * When the direction is {@link View#FOCUS_FORWARD} or {@link View#FOCUS_BACKWARD}, a View that
     * is not in the focus direction is still valid focus target which may not be the desired
     * behavior if the Adapter has more children in the focus direction. To handle this case,
     * RecyclerView converts the focus direction to an absolute direction and makes a preliminary
     * focus search in that direction. If there are no Views to gain focus, it will call
     * {@link LayoutManager#onFocusSearchFailed(View, int, Recycler, State)} before running a
     * focus search with the original (relative) direction. This allows RecyclerView to provide
     * better candidates to the focus search while still allowing the view system to take focus from
     * the RecyclerView and give it to a more suitable child if such child exists.
     *
     * @param focused The view that currently has focus
     * @param direction One of {@link View#FOCUS_UP}, {@link View#FOCUS_DOWN},
     * {@link View#FOCUS_LEFT}, {@link View#FOCUS_RIGHT}, {@link View#FOCUS_FORWARD},
     * {@link View#FOCUS_BACKWARD} or 0 for not applicable.
     *
     * @return A new View that can be the next focus after the focused View
     */
    @Override
    public View focusSearch(View focused, int direction) {
        View result = mLayout.onInterceptFocusSearch(focused, direction);
        if (result != null) {
            return result;
        }
        final boolean canRunFocusFailure = mAdapter != null && mLayout != null
                && !isComputingLayout() && !mLayoutSuppressed;

        final FocusFinder ff = FocusFinder.getInstance();
        if (canRunFocusFailure
                && (direction == View.FOCUS_FORWARD || direction == View.FOCUS_BACKWARD)) {
            // convert direction to absolute direction and see if we have a view there and if not
            // tell LayoutManager to add if it can.
            boolean needsFocusFailureLayout = false;
            if (mLayout.canScrollVertically()) {
                final int absDir =
                        direction == View.FOCUS_FORWARD ? View.FOCUS_DOWN : View.FOCUS_UP;
                final View found = ff.findNextFocus(this, focused, absDir);
                needsFocusFailureLayout = found == null;
                if (FORCE_ABS_FOCUS_SEARCH_DIRECTION) {
                    // Workaround for broken FOCUS_BACKWARD in API 15 and older devices.
                    direction = absDir;
                }
            }
            if (!needsFocusFailureLayout && mLayout.canScrollHorizontally()) {
                boolean rtl = mLayout.getLayoutDirection() == ViewCompat.LAYOUT_DIRECTION_RTL;
                final int absDir = (direction == View.FOCUS_FORWARD) ^ rtl
                        ? View.FOCUS_RIGHT : View.FOCUS_LEFT;
                final View found = ff.findNextFocus(this, focused, absDir);
                needsFocusFailureLayout = found == null;
                if (FORCE_ABS_FOCUS_SEARCH_DIRECTION) {
                    // Workaround for broken FOCUS_BACKWARD in API 15 and older devices.
                    direction = absDir;
                }
            }
            if (needsFocusFailureLayout) {
                consumePendingUpdateOperations();
                final View focusedItemView = findContainingItemView(focused);
                if (focusedItemView == null) {
                    // panic, focused view is not a child anymore, cannot call super.
                    return null;
                }
                startInterceptRequestLayout();
                mLayout.onFocusSearchFailed(focused, direction, mRecycler, mState);
                stopInterceptRequestLayout(false);
            }
            result = ff.findNextFocus(this, focused, direction);
        } else {
            result = ff.findNextFocus(this, focused, direction);
            if (result == null && canRunFocusFailure) {
                consumePendingUpdateOperations();
                final View focusedItemView = findContainingItemView(focused);
                if (focusedItemView == null) {
                    // panic, focused view is not a child anymore, cannot call super.
                    return null;
                }
                startInterceptRequestLayout();
                result = mLayout.onFocusSearchFailed(focused, direction, mRecycler, mState);
                stopInterceptRequestLayout(false);
            }
        }
        if (result != null && !result.hasFocusable()) {
            if (getFocusedChild() == null) {
                // Scrolling to this unfocusable view is not meaningful since there is no currently
                // focused view which RV needs to keep visible.
                return super.focusSearch(focused, direction);
            }
            // If the next view returned by onFocusSearchFailed in layout manager has no focusable
            // views, we still scroll to that view in order to make it visible on the screen.
            // If it's focusable, framework already calls RV's requestChildFocus which handles
            // bringing this newly focused item onto the screen.
            requestChildOnScreen(result, null);
            return focused;
        }
        return isPreferredNextFocus(focused, result, direction)
                ? result : super.focusSearch(focused, direction);
    }

    /**
     * Checks if the new focus candidate is a good enough candidate such that RecyclerView will
     * assign it as the next focus View instead of letting view hierarchy decide.
     * A good candidate means a View that is aligned in the focus direction wrt the focused View
     * and is not the RecyclerView itself.
     * When this method returns false, RecyclerView will let the parent make the decision so the
     * same View may still get the focus as a result of that search.
     */
    private boolean isPreferredNextFocus(View focused, View next, int direction) {
        if (next == null || next == this) {
            return false;
        }
        // panic, result view is not a child anymore, maybe workaround b/37864393
        if (findContainingItemView(next) == null) {
            return false;
        }
        if (focused == null) {
            return true;
        }
        // panic, focused view is not a child anymore, maybe workaround b/37864393
        if (findContainingItemView(focused) == null) {
            return true;
        }

        mTempRect.set(0, 0, focused.getWidth(), focused.getHeight());
        mTempRect2.set(0, 0, next.getWidth(), next.getHeight());
        offsetDescendantRectToMyCoords(focused, mTempRect);
        offsetDescendantRectToMyCoords(next, mTempRect2);
        final int rtl = mLayout.getLayoutDirection() == ViewCompat.LAYOUT_DIRECTION_RTL ? -1 : 1;
        int rightness = 0;
        if ((mTempRect.left < mTempRect2.left
                || mTempRect.right <= mTempRect2.left)
                && mTempRect.right < mTempRect2.right) {
            rightness = 1;
        } else if ((mTempRect.right > mTempRect2.right
                || mTempRect.left >= mTempRect2.right)
                && mTempRect.left > mTempRect2.left) {
            rightness = -1;
        }
        int downness = 0;
        if ((mTempRect.top < mTempRect2.top
                || mTempRect.bottom <= mTempRect2.top)
                && mTempRect.bottom < mTempRect2.bottom) {
            downness = 1;
        } else if ((mTempRect.bottom > mTempRect2.bottom
                || mTempRect.top >= mTempRect2.bottom)
                && mTempRect.top > mTempRect2.top) {
            downness = -1;
        }
        switch (direction) {
            case View.FOCUS_LEFT:
                return rightness < 0;
            case View.FOCUS_RIGHT:
                return rightness > 0;
            case View.FOCUS_UP:
                return downness < 0;
            case View.FOCUS_DOWN:
                return downness > 0;
            case View.FOCUS_FORWARD:
                return downness > 0 || (downness == 0 && rightness * rtl >= 0);
            case View.FOCUS_BACKWARD:
                return downness < 0 || (downness == 0 && rightness * rtl <= 0);
        }
        throw new IllegalArgumentException("Invalid direction: " + direction + exceptionLabel());
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        if (!mLayout.onRequestChildFocus(this, mState, child, focused) && focused != null) {
            requestChildOnScreen(child, focused);
        }
        super.requestChildFocus(child, focused);
    }

    /**
     * Requests that the given child of the RecyclerView be positioned onto the screen. This method
     * can be called for both unfocusable and focusable child views. For unfocusable child views,
     * the {@param focused} parameter passed is null, whereas for a focusable child, this parameter
     * indicates the actual descendant view within this child view that holds the focus.
     * @param child The child view of this RecyclerView that wants to come onto the screen.
     * @param focused The descendant view that actually has the focus if child is focusable, null
     *                otherwise.
     */
    protected void requestChildOnScreen(@NonNull View child, @Nullable View focused) {
        View rectView = (focused != null) ? focused : child;
        mTempRect.set(0, 0, rectView.getWidth(), rectView.getHeight());

        // get item decor offsets w/o refreshing. If they are invalid, there will be another
        // layout pass to fix them, then it is LayoutManager's responsibility to keep focused
        // View in viewport.
        final ViewGroup.LayoutParams focusedLayoutParams = rectView.getLayoutParams();
        if (focusedLayoutParams instanceof LayoutParams) {
            // if focused child has item decors, use them. Otherwise, ignore.
            final LayoutParams lp = (LayoutParams) focusedLayoutParams;
            if (!lp.mInsetsDirty) {
                final Rect insets = lp.mDecorInsets;
                mTempRect.left -= insets.left;
                mTempRect.right += insets.right;
                mTempRect.top -= insets.top;
                mTempRect.bottom += insets.bottom;
            }
        }

        if (focused != null) {
            offsetDescendantRectToMyCoords(focused, mTempRect);
            offsetRectIntoDescendantCoords(child, mTempRect);
        }
        mLayout.requestChildRectangleOnScreen(this, child, mTempRect, !mFirstLayoutComplete,
                (focused == null));
    }

    @Override
    public boolean requestChildRectangleOnScreen(View child, Rect rect, boolean immediate) {
        return mLayout.requestChildRectangleOnScreen(this, child, rect, immediate);
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int focusableMode) {
        if (mLayout == null || !mLayout.onAddFocusables(this, views, direction, focusableMode)) {
            super.addFocusables(views, direction, focusableMode);
        }
    }

    @Override
    protected boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        if (isComputingLayout()) {
            // if we are in the middle of a layout calculation, don't let any child take focus.
            // RV will handle it after layout calculation is finished.
            return false;
        }
        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mLayoutOrScrollCounter = 0;
        mIsAttached = true;
        mFirstLayoutComplete = mFirstLayoutComplete && !isLayoutRequested();
        if (mLayout != null) {
            mLayout.dispatchAttachedToWindow(this);
        }
        mPostedAnimatorRunner = false;

        if (ALLOW_THREAD_GAP_WORK) {
            // Register with gap worker
            mGapWorker = GapWorker.sGapWorker.get();
            if (mGapWorker == null) {
                mGapWorker = new GapWorker();

                // break 60 fps assumption if data from display appears valid
                // NOTE: we only do this query once, statically, because it's very expensive (> 1ms)
                Display display = ViewCompat.getDisplay(this);
                float refreshRate = 60.0f;
                if (!isInEditMode() && display != null) {
                    float displayRefreshRate = display.getRefreshRate();
                    if (displayRefreshRate >= 30.0f) {
                        refreshRate = displayRefreshRate;
                    }
                }
                mGapWorker.mFrameIntervalNs = (long) (1000000000 / refreshRate);
                GapWorker.sGapWorker.set(mGapWorker);
            }
            mGapWorker.add(this);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mItemAnimator != null) {
            mItemAnimator.endAnimations();
        }
        stopScroll();
        mIsAttached = false;
        if (mLayout != null) {
            mLayout.dispatchDetachedFromWindow(this, mRecycler);
        }
        mPendingAccessibilityImportanceChange.clear();
        removeCallbacks(mItemAnimatorRunner);
        mViewInfoStore.onDetach();

        if (ALLOW_THREAD_GAP_WORK && mGapWorker != null) {
            // Unregister with gap worker
            mGapWorker.remove(this);
            mGapWorker = null;
        }
    }

    /**
     * Returns true if RecyclerView is attached to window.
     */
    @Override
    public boolean isAttachedToWindow() {
        return mIsAttached;
    }

    /**
     * Checks if RecyclerView is in the middle of a layout or scroll and throws an
     * {@link IllegalStateException} if it <b>is not</b>.
     *
     * @param message The message for the exception. Can be null.
     * @see #assertNotInLayoutOrScroll(String)
     */
    void assertInLayoutOrScroll(String message) {
        if (!isComputingLayout()) {
            if (message == null) {
                throw new IllegalStateException("Cannot call this method unless RecyclerView is "
                        + "computing a layout or scrolling" + exceptionLabel());
            }
            throw new IllegalStateException(message + exceptionLabel());

        }
    }

    /**
     * Checks if RecyclerView is in the middle of a layout or scroll and throws an
     * {@link IllegalStateException} if it <b>is</b>.
     *
     * @param message The message for the exception. Can be null.
     * @see #assertInLayoutOrScroll(String)
     */
    void assertNotInLayoutOrScroll(String message) {
        if (isComputingLayout()) {
            if (message == null) {
                throw new IllegalStateException("Cannot call this method while RecyclerView is "
                        + "computing a layout or scrolling" + exceptionLabel());
            }
            throw new IllegalStateException(message);
        }
        if (mDispatchScrollCounter > 0) {
            Log.w(TAG, "Cannot call this method in a scroll callback. Scroll callbacks might"
                            + "be run during a measure & layout pass where you cannot change the"
                            + "RecyclerView data. Any method call that might change the structure"
                            + "of the RecyclerView or the adapter contents should be postponed to"
                            + "the next frame.",
                    new IllegalStateException("" + exceptionLabel()));
        }
    }

    /**
     * Add an {@link OnItemTouchListener} to intercept touch events before they are dispatched
     * to child views or this view's standard scrolling behavior.
     *
     * <p>Client code may use listeners to implement item manipulation behavior. Once a listener
     * returns true from
     * {@link OnItemTouchListener#onInterceptTouchEvent(RecyclerView, MotionEvent)} its
     * {@link OnItemTouchListener#onTouchEvent(RecyclerView, MotionEvent)} method will be called
     * for each incoming MotionEvent until the end of the gesture.</p>
     *
     * @param listener Listener to add
     * @see SimpleOnItemTouchListener
     */
    public void addOnItemTouchListener(@NonNull OnItemTouchListener listener) {
        mOnItemTouchListeners.add(listener);
    }

    /**
     * Remove an {@link OnItemTouchListener}. It will no longer be able to intercept touch events.
     *
     * @param listener Listener to remove
     */
    public void removeOnItemTouchListener(@NonNull OnItemTouchListener listener) {
        mOnItemTouchListeners.remove(listener);
        if (mInterceptingOnItemTouchListener == listener) {
            mInterceptingOnItemTouchListener = null;
        }
    }

    /**
     * Dispatches the motion event to the intercepting OnItemTouchListener or provides opportunity
     * for OnItemTouchListeners to intercept.
     * @param e The MotionEvent
     * @return True if handled by an intercepting OnItemTouchListener.
     */
    private boolean dispatchToOnItemTouchListeners(MotionEvent e) {

        // OnItemTouchListeners should receive calls to their methods in the same pattern that
        // ViewGroups do. That pattern is a bit confusing, which in turn makes the below code a
        // bit confusing.  Here are rules for the pattern:
        //
        // 1. A single MotionEvent should not be passed to either OnInterceptTouchEvent or
        // OnTouchEvent twice.
        // 2. ACTION_DOWN MotionEvents may be passed to both onInterceptTouchEvent and
        // onTouchEvent.
        // 3. All other MotionEvents should be passed to either onInterceptTouchEvent or
        // onTouchEvent, not both.

        // Side Note: We don't currently perfectly mimic how MotionEvents work in the view system.
        // If we were to do so, for every MotionEvent, any OnItemTouchListener that is before the
        // intercepting OnItemTouchEvent should still have a chance to intercept, and if it does,
        // the previously intercepting OnItemTouchEvent should get an ACTION_CANCEL event.

        if (mInterceptingOnItemTouchListener == null) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                return false;
            }
            return findInterceptingOnItemTouchListener(e);
        } else {
            mInterceptingOnItemTouchListener.onTouchEvent(this, e);
            final int action = e.getAction();
            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
                mInterceptingOnItemTouchListener = null;
            }
            return true;
        }
    }

    /**
     * Looks for an OnItemTouchListener that wants to intercept.
     *
     * <p>Calls {@link OnItemTouchListener#onInterceptTouchEvent(RecyclerView, MotionEvent)} on each
     * of the registered {@link OnItemTouchListener}s, passing in the
     * MotionEvent. If one returns true and the action is not ACTION_CANCEL, saves the intercepting
     * OnItemTouchListener to be called for future {@link RecyclerView#onTouchEvent(MotionEvent)}
     * and immediately returns true. If none want to intercept or the action is ACTION_CANCEL,
     * returns false.
     *
     * @param e The MotionEvent
     * @return true if an OnItemTouchListener is saved as intercepting.
     */
    private boolean findInterceptingOnItemTouchListener(MotionEvent e) {
        int action = e.getAction();
        final int listenerCount = mOnItemTouchListeners.size();
        for (int i = 0; i < listenerCount; i++) {
            final OnItemTouchListener listener = mOnItemTouchListeners.get(i);
            if (listener.onInterceptTouchEvent(this, e) && action != MotionEvent.ACTION_CANCEL) {
                mInterceptingOnItemTouchListener = listener;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (mLayoutSuppressed) {
            // When layout is suppressed,  RV does not intercept the motion event.
            // A child view e.g. a button may still get the click.
            return false;
        }

        // Clear the active onInterceptTouchListener.  None should be set at this time, and if one
        // is, it's because some other code didn't follow the standard contract.
        mInterceptingOnItemTouchListener = null;
        if (findInterceptingOnItemTouchListener(e)) {
            cancelScroll();
            return true;
        }

        if (mLayout == null) {
            return false;
        }

        final boolean canScrollHorizontally = mLayout.canScrollHorizontally();
        final boolean canScrollVertically = mLayout.canScrollVertically();

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(e);

        final int action = e.getActionMasked();
        final int actionIndex = e.getActionIndex();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (mIgnoreMotionEventTillDown) {
                    mIgnoreMotionEventTillDown = false;
                }
                mScrollPointerId = e.getPointerId(0);
                mInitialTouchX = mLastTouchX = (int) (e.getX() + 0.5f);
                mInitialTouchY = mLastTouchY = (int) (e.getY() + 0.5f);

                if (mScrollState == SCROLL_STATE_SETTLING) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                    setScrollState(SCROLL_STATE_DRAGGING);
                    stopNestedScroll(TYPE_NON_TOUCH);
                }

                // Clear the nested offsets
                mNestedOffsets[0] = mNestedOffsets[1] = 0;

                int nestedScrollAxis = ViewCompat.SCROLL_AXIS_NONE;
                if (canScrollHorizontally) {
                    nestedScrollAxis |= ViewCompat.SCROLL_AXIS_HORIZONTAL;
                }
                if (canScrollVertically) {
                    nestedScrollAxis |= ViewCompat.SCROLL_AXIS_VERTICAL;
                }
                startNestedScroll(nestedScrollAxis, TYPE_TOUCH);
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                mScrollPointerId = e.getPointerId(actionIndex);
                mInitialTouchX = mLastTouchX = (int) (e.getX(actionIndex) + 0.5f);
                mInitialTouchY = mLastTouchY = (int) (e.getY(actionIndex) + 0.5f);
                break;

            case MotionEvent.ACTION_MOVE: {
                final int index = e.findPointerIndex(mScrollPointerId);
                if (index < 0) {
                    Log.e(TAG, "Error processing scroll; pointer index for id "
                            + mScrollPointerId + " not found. Did any MotionEvents get skipped?");
                    return false;
                }

                final int x = (int) (e.getX(index) + 0.5f);
                final int y = (int) (e.getY(index) + 0.5f);
                if (mScrollState != SCROLL_STATE_DRAGGING) {
                    final int dx = x - mInitialTouchX;
                    final int dy = y - mInitialTouchY;
                    boolean startScroll = false;
                    if (canScrollHorizontally && Math.abs(dx) > mTouchSlop) {
                        mLastTouchX = x;
                        startScroll = true;
                    }
                    if (canScrollVertically && Math.abs(dy) > mTouchSlop) {
                        mLastTouchY = y;
                        startScroll = true;
                    }
                    if (startScroll) {
                        setScrollState(SCROLL_STATE_DRAGGING);
                    }
                }
            } break;

            case MotionEvent.ACTION_POINTER_UP: {
                onPointerUp(e);
            } break;

            case MotionEvent.ACTION_UP: {
                mVelocityTracker.clear();
                stopNestedScroll(TYPE_TOUCH);
            } break;

            case MotionEvent.ACTION_CANCEL: {
                cancelScroll();
            }
        }
        return mScrollState == SCROLL_STATE_DRAGGING;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        final int listenerCount = mOnItemTouchListeners.size();
        for (int i = 0; i < listenerCount; i++) {
            final OnItemTouchListener listener = mOnItemTouchListeners.get(i);
            listener.onRequestDisallowInterceptTouchEvent(disallowIntercept);
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (mLayoutSuppressed || mIgnoreMotionEventTillDown) {
            return false;
        }
        if (dispatchToOnItemTouchListeners(e)) {
            cancelScroll();
            return true;
        }

        if (mLayout == null) {
            return false;
        }

        final boolean canScrollHorizontally = mLayout.canScrollHorizontally();
        final boolean canScrollVertically = mLayout.canScrollVertically();

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        boolean eventAddedToVelocityTracker = false;

        final MotionEvent vtev = MotionEvent.obtain(e);
        final int action = e.getActionMasked();
        final int actionIndex = e.getActionIndex();

        if (action == MotionEvent.ACTION_DOWN) {
            mNestedOffsets[0] = mNestedOffsets[1] = 0;
        }
        vtev.offsetLocation(mNestedOffsets[0], mNestedOffsets[1]);

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                mScrollPointerId = e.getPointerId(0);
                mInitialTouchX = mLastTouchX = (int) (e.getX() + 0.5f);
                mInitialTouchY = mLastTouchY = (int) (e.getY() + 0.5f);

                int nestedScrollAxis = ViewCompat.SCROLL_AXIS_NONE;
                if (canScrollHorizontally) {
                    nestedScrollAxis |= ViewCompat.SCROLL_AXIS_HORIZONTAL;
                }
                if (canScrollVertically) {
                    nestedScrollAxis |= ViewCompat.SCROLL_AXIS_VERTICAL;
                }
                startNestedScroll(nestedScrollAxis, TYPE_TOUCH);
            } break;

            case MotionEvent.ACTION_POINTER_DOWN: {
                mScrollPointerId = e.getPointerId(actionIndex);
                mInitialTouchX = mLastTouchX = (int) (e.getX(actionIndex) + 0.5f);
                mInitialTouchY = mLastTouchY = (int) (e.getY(actionIndex) + 0.5f);
            } break;

            case MotionEvent.ACTION_MOVE: {
                final int index = e.findPointerIndex(mScrollPointerId);
                if (index < 0) {
                    Log.e(TAG, "Error processing scroll; pointer index for id "
                            + mScrollPointerId + " not found. Did any MotionEvents get skipped?");
                    return false;
                }

                final int x = (int) (e.getX(index) + 0.5f);
                final int y = (int) (e.getY(index) + 0.5f);
                int dx = mLastTouchX - x;
                int dy = mLastTouchY - y;

                mReusableIntPair[0] = 0;
                mReusableIntPair[1] = 0;
                if (dispatchNestedPreScroll(dx, dy, mReusableIntPair, mScrollOffset, TYPE_TOUCH)) {
                    dx -= mReusableIntPair[0];
                    dy -= mReusableIntPair[1];
                    vtev.offsetLocation(mScrollOffset[0], mScrollOffset[1]);
                    // Updated the nested offsets
                    mNestedOffsets[0] += mScrollOffset[0];
                    mNestedOffsets[1] += mScrollOffset[1];
                }

                if (mScrollState != SCROLL_STATE_DRAGGING) {
                    boolean startScroll = false;
                    if (canScrollHorizontally && Math.abs(dx) > mTouchSlop) {
                        if (dx > 0) {
                            dx -= mTouchSlop;
                        } else {
                            dx += mTouchSlop;
                        }
                        startScroll = true;
                    }
                    if (canScrollVertically && Math.abs(dy) > mTouchSlop) {
                        if (dy > 0) {
                            dy -= mTouchSlop;
                        } else {
                            dy += mTouchSlop;
                        }
                        startScroll = true;
                    }
                    if (startScroll) {
                        setScrollState(SCROLL_STATE_DRAGGING);
                    }
                }

                if (mScrollState == SCROLL_STATE_DRAGGING) {
                    mLastTouchX = x - mScrollOffset[0];
                    mLastTouchY = y - mScrollOffset[1];

                    if (scrollByInternal(
                            canScrollHorizontally ? dx : 0,
                            canScrollVertically ? dy : 0,
                            vtev)) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (mGapWorker != null && (dx != 0 || dy != 0)) {
                        mGapWorker.postFromTraversal(this, dx, dy);
                    }
                }
            } break;

            case MotionEvent.ACTION_POINTER_UP: {
                onPointerUp(e);
            } break;

            case MotionEvent.ACTION_UP: {
                mVelocityTracker.addMovement(vtev);
                eventAddedToVelocityTracker = true;
                mVelocityTracker.computeCurrentVelocity(1000, mMaxFlingVelocity);
                final float xvel = canScrollHorizontally
                        ? -mVelocityTracker.getXVelocity(mScrollPointerId) : 0;
                final float yvel = canScrollVertically
                        ? -mVelocityTracker.getYVelocity(mScrollPointerId) : 0;
                if (!((xvel != 0 || yvel != 0) && fling((int) xvel, (int) yvel))) {
                    setScrollState(SCROLL_STATE_IDLE);
                }
                resetScroll();
            } break;

            case MotionEvent.ACTION_CANCEL: {
                cancelScroll();
            } break;
        }

        if (!eventAddedToVelocityTracker) {
            mVelocityTracker.addMovement(vtev);
        }
        vtev.recycle();

        return true;
    }

    private void resetScroll() {
        if (mVelocityTracker != null) {
            mVelocityTracker.clear();
        }
        stopNestedScroll(TYPE_TOUCH);
        releaseGlows();
    }

    private void cancelScroll() {
        resetScroll();
        setScrollState(SCROLL_STATE_IDLE);
    }

    private void onPointerUp(MotionEvent e) {
        final int actionIndex = e.getActionIndex();
        if (e.getPointerId(actionIndex) == mScrollPointerId) {
            // Pick a new pointer to pick up the slack.
            final int newIndex = actionIndex == 0 ? 1 : 0;
            mScrollPointerId = e.getPointerId(newIndex);
            mInitialTouchX = mLastTouchX = (int) (e.getX(newIndex) + 0.5f);
            mInitialTouchY = mLastTouchY = (int) (e.getY(newIndex) + 0.5f);
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (mLayout == null) {
            return false;
        }
        if (mLayoutSuppressed) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_SCROLL) {
            final float vScroll, hScroll;
            if ((event.getSource() & InputDeviceCompat.SOURCE_CLASS_POINTER) != 0) {
                if (mLayout.canScrollVertically()) {
                    // Inverse the sign of the vertical scroll to align the scroll orientation
                    // with AbsListView.
                    vScroll = -event.getAxisValue(MotionEvent.AXIS_VSCROLL);
                } else {
                    vScroll = 0f;
                }
                if (mLayout.canScrollHorizontally()) {
                    hScroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL);
                } else {
                    hScroll = 0f;
                }
            } else if ((event.getSource() & InputDeviceCompat.SOURCE_ROTARY_ENCODER) != 0) {
                final float axisScroll = event.getAxisValue(MotionEventCompat.AXIS_SCROLL);
                if (mLayout.canScrollVertically()) {
                    // Invert the sign of the vertical scroll to align the scroll orientation
                    // with AbsListView.
                    vScroll = -axisScroll;
                    hScroll = 0f;
                } else if (mLayout.canScrollHorizontally()) {
                    vScroll = 0f;
                    hScroll = axisScroll;
                } else {
                    vScroll = 0f;
                    hScroll = 0f;
                }
            } else {
                vScroll = 0f;
                hScroll = 0f;
            }

            if (vScroll != 0 || hScroll != 0) {
                scrollByInternal((int) (hScroll * mScaledHorizontalScrollFactor),
                        (int) (vScroll * mScaledVerticalScrollFactor), event);
            }
        }
        return false;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        if (mLayout == null) {
            defaultOnMeasure(widthSpec, heightSpec);
            return;
        }
        if (mLayout.isAutoMeasureEnabled()) {
            final int widthMode = MeasureSpec.getMode(widthSpec);
            final int heightMode = MeasureSpec.getMode(heightSpec);

            /**
             * This specific call should be considered deprecated and replaced with
             * {@link #defaultOnMeasure(int, int)}. It can't actually be replaced as it could
             * break existing third party code but all documentation directs developers to not
             * override {@link LayoutManager#onMeasure(int, int)} when
             * {@link LayoutManager#isAutoMeasureEnabled()} returns true.
             */
            mLayout.onMeasure(mRecycler, mState, widthSpec, heightSpec);

            final boolean measureSpecModeIsExactly =
                    widthMode == MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY;
            if (measureSpecModeIsExactly || mAdapter == null) {
                return;
            }

            if (mState.mLayoutStep == State.STEP_START) {
                dispatchLayoutStep1();
            }
            // set dimensions in 2nd step. Pre-layout should happen with old dimensions for
            // consistency
            mLayout.setMeasureSpecs(widthSpec, heightSpec);
            mState.mIsMeasuring = true;
            dispatchLayoutStep2();

            // now we can get the width and height from the children.
            mLayout.setMeasuredDimensionFromChildren(widthSpec, heightSpec);

            // if RecyclerView has non-exact width and height and if there is at least one child
            // which also has non-exact width & height, we have to re-measure.
            if (mLayout.shouldMeasureTwice()) {
                mLayout.setMeasureSpecs(
                        MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
                mState.mIsMeasuring = true;
                dispatchLayoutStep2();
                // now we can get the width and height from the children.
                mLayout.setMeasuredDimensionFromChildren(widthSpec, heightSpec);
            }
        } else {
            if (mHasFixedSize) {
                mLayout.onMeasure(mRecycler, mState, widthSpec, heightSpec);
                return;
            }
            // custom onMeasure
            if (mAdapterUpdateDuringMeasure) {
                startInterceptRequestLayout();
                onEnterLayoutOrScroll();
                processAdapterUpdatesAndSetAnimationFlags();
                onExitLayoutOrScroll();

                if (mState.mRunPredictiveAnimations) {
                    mState.mInPreLayout = true;
                } else {
                    // consume remaining updates to provide a consistent state with the layout pass.
                    mAdapterHelper.consumeUpdatesInOnePass();
                    mState.mInPreLayout = false;
                }
                mAdapterUpdateDuringMeasure = false;
                stopInterceptRequestLayout(false);
            } else if (mState.mRunPredictiveAnimations) {
                // If mAdapterUpdateDuringMeasure is false and mRunPredictiveAnimations is true:
                // this means there is already an onMeasure() call performed to handle the pending
                // adapter change, two onMeasure() calls can happen if RV is a child of LinearLayout
                // with layout_width=MATCH_PARENT. RV cannot call LM.onMeasure() second time
                // because getViewForPosition() will crash when LM uses a child to measure.
                setMeasuredDimension(getMeasuredWidth(), getMeasuredHeight());
                return;
            }

            if (mAdapter != null) {
                mState.mItemCount = mAdapter.getItemCount();
            } else {
                mState.mItemCount = 0;
            }
            startInterceptRequestLayout();
            mLayout.onMeasure(mRecycler, mState, widthSpec, heightSpec);
            stopInterceptRequestLayout(false);
            mState.mInPreLayout = false; // clear
        }
    }

    /**
     * An implementation of {@link View#onMeasure(int, int)} to fall back to in various scenarios
     * where this RecyclerView is otherwise lacking better information.
     */
    void defaultOnMeasure(int widthSpec, int heightSpec) {
        // calling LayoutManager here is not pretty but that API is already public and it is better
        // than creating another method since this is internal.
        final int width = LayoutManager.chooseSize(widthSpec,
                getPaddingLeft() + getPaddingRight(),
                ViewCompat.getMinimumWidth(this));
        final int height = LayoutManager.chooseSize(heightSpec,
                getPaddingTop() + getPaddingBottom(),
                ViewCompat.getMinimumHeight(this));

        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w != oldw || h != oldh) {
            invalidateGlows();
            // layout's w/h are updated during measure/layout steps.
        }
    }

    /**
     * Sets the {@link ItemAnimator} that will handle animations involving changes
     * to the items in this RecyclerView. By default, RecyclerView instantiates and
     * uses an instance of {@link DefaultItemAnimator}. Whether item animations are
     * enabled for the RecyclerView depends on the ItemAnimator and whether
     * the LayoutManager {@link LayoutManager#supportsPredictiveItemAnimations()
     * supports item animations}.
     *
     * @param animator The ItemAnimator being set. If null, no animations will occur
     * when changes occur to the items in this RecyclerView.
     */
    public void setItemAnimator(@Nullable ItemAnimator animator) {
        if (mItemAnimator != null) {
            mItemAnimator.endAnimations();
            mItemAnimator.setListener(null);
        }
        mItemAnimator = animator;
        if (mItemAnimator != null) {
            mItemAnimator.setListener(mItemAnimatorListener);
        }
    }

    void onEnterLayoutOrScroll() {
        mLayoutOrScrollCounter++;
    }

    void onExitLayoutOrScroll() {
        onExitLayoutOrScroll(true);
    }

    void onExitLayoutOrScroll(boolean enableChangeEvents) {
        mLayoutOrScrollCounter--;
        if (mLayoutOrScrollCounter < 1) {
            if (DEBUG && mLayoutOrScrollCounter < 0) {
                throw new IllegalStateException("layout or scroll counter cannot go below zero."
                        + "Some calls are not matching" + exceptionLabel());
            }
            mLayoutOrScrollCounter = 0;
            if (enableChangeEvents) {
                dispatchContentChangedIfNecessary();
                dispatchPendingImportantForAccessibilityChanges();
            }
        }
    }

    boolean isAccessibilityEnabled() {
        return mAccessibilityManager != null && mAccessibilityManager.isEnabled();
    }

    private void dispatchContentChangedIfNecessary() {
        final int flags = mEatenAccessibilityChangeFlags;
        mEatenAccessibilityChangeFlags = 0;
        if (flags != 0 && isAccessibilityEnabled()) {
            final AccessibilityEvent event = AccessibilityEvent.obtain();
            event.setEventType(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
            AccessibilityEventCompat.setContentChangeTypes(event, flags);
            sendAccessibilityEventUnchecked(event);
        }
    }

    /**
     * Returns whether RecyclerView is currently computing a layout.
     * <p>
     * If this method returns true, it means that RecyclerView is in a lockdown state and any
     * attempt to update adapter contents will result in an exception because adapter contents
     * cannot be changed while RecyclerView is trying to compute the layout.
     * <p>
     * It is very unlikely that your code will be running during this state as it is
     * called by the framework when a layout traversal happens or RecyclerView starts to scroll
     * in response to system events (touch, accessibility etc).
     * <p>
     * This case may happen if you have some custom logic to change adapter contents in
     * response to a View callback (e.g. focus change callback) which might be triggered during a
     * layout calculation. In these cases, you should just postpone the change using a Handler or a
     * similar mechanism.
     *
     * @return <code>true</code> if RecyclerView is currently computing a layout, <code>false</code>
     *         otherwise
     */
    public boolean isComputingLayout() {
        return mLayoutOrScrollCounter > 0;
    }

    /**
     * Returns true if an accessibility event should not be dispatched now. This happens when an
     * accessibility request arrives while RecyclerView does not have a stable state which is very
     * hard to handle for a LayoutManager. Instead, this method records necessary information about
     * the event and dispatches a window change event after the critical section is finished.
     *
     * @return True if the accessibility event should be postponed.
     */
    boolean shouldDeferAccessibilityEvent(AccessibilityEvent event) {
        if (isComputingLayout()) {
            int type = 0;
            if (event != null) {
                type = AccessibilityEventCompat.getContentChangeTypes(event);
            }
            if (type == 0) {
                type = AccessibilityEventCompat.CONTENT_CHANGE_TYPE_UNDEFINED;
            }
            mEatenAccessibilityChangeFlags |= type;
            return true;
        }
        return false;
    }

    @Override
    public void sendAccessibilityEventUnchecked(AccessibilityEvent event) {
        if (shouldDeferAccessibilityEvent(event)) {
            return;
        }
        super.sendAccessibilityEventUnchecked(event);
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        onPopulateAccessibilityEvent(event);
        return true;
    }

    /**
     * Gets the current ItemAnimator for this RecyclerView. A null return value
     * indicates that there is no animator and that item changes will happen without
     * any animations. By default, RecyclerView instantiates and
     * uses an instance of {@link DefaultItemAnimator}.
     *
     * @return ItemAnimator The current ItemAnimator. If null, no animations will occur
     * when changes occur to the items in this RecyclerView.
     */
    @Nullable
    public ItemAnimator getItemAnimator() {
        return mItemAnimator;
    }

    /**
     * Post a runnable to the next frame to run pending item animations. Only the first such
     * request will be posted, governed by the mPostedAnimatorRunner flag.
     */
    void postAnimationRunner() {
        if (!mPostedAnimatorRunner && mIsAttached) {
            ViewCompat.postOnAnimation(this, mItemAnimatorRunner);
            mPostedAnimatorRunner = true;
        }
    }

    private boolean predictiveItemAnimationsEnabled() {
        return (mItemAnimator != null && mLayout.supportsPredictiveItemAnimations());
    }

    /**
     * Consumes adapter updates and calculates which type of animations we want to run.
     * Called in onMeasure and dispatchLayout.
     * <p>
     * This method may process only the pre-layout state of updates or all of them.
     */
    private void processAdapterUpdatesAndSetAnimationFlags() {
        if (mDataSetHasChangedAfterLayout) {
            // Processing these items have no value since data set changed unexpectedly.
            // Instead, we just reset it.
            mAdapterHelper.reset();
            if (mDispatchItemsChangedEvent) {
                mLayout.onItemsChanged(this);
            }
        }
        // simple animations are a subset of advanced animations (which will cause a
        // pre-layout step)
        // If layout supports predictive animations, pre-process to decide if we want to run them
        if (predictiveItemAnimationsEnabled()) {
            mAdapterHelper.preProcess();
        } else {
            mAdapterHelper.consumeUpdatesInOnePass();
        }
        boolean animationTypeSupported = mItemsAddedOrRemoved || mItemsChanged;
        mState.mRunSimpleAnimations = mFirstLayoutComplete
                && mItemAnimator != null
                && (mDataSetHasChangedAfterLayout
                || animationTypeSupported
                || mLayout.mRequestedSimpleAnimations)
                && (!mDataSetHasChangedAfterLayout
                || mAdapter.hasStableIds());
        mState.mRunPredictiveAnimations = mState.mRunSimpleAnimations
                && animationTypeSupported
                && !mDataSetHasChangedAfterLayout
                && predictiveItemAnimationsEnabled();
    }

    /**
     * Wrapper around layoutChildren() that handles animating changes caused by layout.
     * Animations work on the assumption that there are five different kinds of items
     * in play:
     * PERSISTENT: items are visible before and after layout
     * REMOVED: items were visible before layout and were removed by the app
     * ADDED: items did not exist before layout and were added by the app
     * DISAPPEARING: items exist in the data set before/after, but changed from
     * visible to non-visible in the process of layout (they were moved off
     * screen as a side-effect of other changes)
     * APPEARING: items exist in the data set before/after, but changed from
     * non-visible to visible in the process of layout (they were moved on
     * screen as a side-effect of other changes)
     * The overall approach figures out what items exist before/after layout and
     * infers one of the five above states for each of the items. Then the animations
     * are set up accordingly:
     * PERSISTENT views are animated via
     * {@link ItemAnimator#animatePersistence(ViewHolder, ItemHolderInfo, ItemHolderInfo)}
     * DISAPPEARING views are animated via
     * {@link ItemAnimator#animateDisappearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)}
     * APPEARING views are animated via
     * {@link ItemAnimator#animateAppearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)}
     * and changed views are animated via
     * {@link ItemAnimator#animateChange(ViewHolder, ViewHolder, ItemHolderInfo, ItemHolderInfo)}.
     */
    void dispatchLayout() {
        if (mAdapter == null) {
            Log.e(TAG, "No adapter attached; skipping layout");
            // leave the state in START
            return;
        }
        if (mLayout == null) {
            Log.e(TAG, "No layout manager attached; skipping layout");
            // leave the state in START
            return;
        }
        mState.mIsMeasuring = false;
        if (mState.mLayoutStep == State.STEP_START) {
            dispatchLayoutStep1();
            mLayout.setExactMeasureSpecsFrom(this);
            dispatchLayoutStep2();
        } else if (mAdapterHelper.hasUpdates() || mLayout.getWidth() != getWidth()
                || mLayout.getHeight() != getHeight()) {
            // First 2 steps are done in onMeasure but looks like we have to run again due to
            // changed size.
            mLayout.setExactMeasureSpecsFrom(this);
            dispatchLayoutStep2();
        } else {
            // always make sure we sync them (to ensure mode is exact)
            mLayout.setExactMeasureSpecsFrom(this);
        }
        dispatchLayoutStep3();
    }

    private void saveFocusInfo() {
        View child = null;
        if (mPreserveFocusAfterLayout && hasFocus() && mAdapter != null) {
            child = getFocusedChild();
        }

        final ViewHolder focusedVh = child == null ? null : findContainingViewHolder(child);
        if (focusedVh == null) {
            resetFocusInfo();
        } else {
            mState.mFocusedItemId = mAdapter.hasStableIds() ? focusedVh.getItemId() : NO_ID;
            // mFocusedItemPosition should hold the current adapter position of the previously
            // focused item. If the item is removed, we store the previous adapter position of the
            // removed item.
            mState.mFocusedItemPosition = mDataSetHasChangedAfterLayout ? NO_POSITION
                    : (focusedVh.isRemoved() ? focusedVh.mOldPosition
                            : focusedVh.getAdapterPosition());
            mState.mFocusedSubChildId = getDeepestFocusedViewWithId(focusedVh.itemView);
        }
    }

    private void resetFocusInfo() {
        mState.mFocusedItemId = NO_ID;
        mState.mFocusedItemPosition = NO_POSITION;
        mState.mFocusedSubChildId = View.NO_ID;
    }

    /**
     * Finds the best view candidate to request focus on using mFocusedItemPosition index of the
     * previously focused item. It first traverses the adapter forward to find a focusable candidate
     * and if no such candidate is found, it reverses the focus search direction for the items
     * before the mFocusedItemPosition'th index;
     * @return The best candidate to request focus on, or null if no such candidate exists. Null
     * indicates all the existing adapter items are unfocusable.
     */
    @Nullable
    private View findNextViewToFocus() {
        int startFocusSearchIndex = mState.mFocusedItemPosition != -1 ? mState.mFocusedItemPosition
                : 0;
        ViewHolder nextFocus;
        final int itemCount = mState.getItemCount();
        for (int i = startFocusSearchIndex; i < itemCount; i++) {
            nextFocus = findViewHolderForAdapterPosition(i);
            if (nextFocus == null) {
                break;
            }
            if (nextFocus.itemView.hasFocusable()) {
                return nextFocus.itemView;
            }
        }
        final int limit = Math.min(itemCount, startFocusSearchIndex);
        for (int i = limit - 1; i >= 0; i--) {
            nextFocus = findViewHolderForAdapterPosition(i);
            if (nextFocus == null) {
                return null;
            }
            if (nextFocus.itemView.hasFocusable()) {
                return nextFocus.itemView;
            }
        }
        return null;
    }

    private void recoverFocusFromState() {
        if (!mPreserveFocusAfterLayout || mAdapter == null || !hasFocus()
                || getDescendantFocusability() == FOCUS_BLOCK_DESCENDANTS
                || (getDescendantFocusability() == FOCUS_BEFORE_DESCENDANTS && isFocused())) {
            // No-op if either of these cases happens:
            // 1. RV has no focus, or 2. RV blocks focus to its children, or 3. RV takes focus
            // before its children and is focused (i.e. it already stole the focus away from its
            // descendants).
            return;
        }
        // only recover focus if RV itself has the focus or the focused view is hidden
        if (!isFocused()) {
            final View focusedChild = getFocusedChild();
            if (IGNORE_DETACHED_FOCUSED_CHILD
                    && (focusedChild.getParent() == null || !focusedChild.hasFocus())) {
                // Special handling of API 15-. A focused child can be invalid because mFocus is not
                // cleared when the child is detached (mParent = null),
                // This happens because clearFocus on API 15- does not invalidate mFocus of its
                // parent when this child is detached.
                // For API 16+, this is not an issue because requestFocus takes care of clearing the
                // prior detached focused child. For API 15- the problem happens in 2 cases because
                // clearChild does not call clearChildFocus on RV: 1. setFocusable(false) is called
                // for the current focused item which calls clearChild or 2. when the prior focused
                // child is removed, removeDetachedView called in layout step 3 which calls
                // clearChild. We should ignore this invalid focused child in all our calculations
                // for the next view to receive focus, and apply the focus recovery logic instead.
                if (mChildHelper.getChildCount() == 0) {
                    // No children left. Request focus on the RV itself since one of its children
                    // was holding focus previously.
                    requestFocus();
                    return;
                }
            } else if (!mChildHelper.isHidden(focusedChild)) {
                // If the currently focused child is hidden, apply the focus recovery logic.
                // Otherwise return, i.e. the currently (unhidden) focused child is good enough :/.
                return;
            }
        }
        ViewHolder focusTarget = null;
        // RV first attempts to locate the previously focused item to request focus on using
        // mFocusedItemId. If such an item no longer exists, it then makes a best-effort attempt to
        // find the next best candidate to request focus on based on mFocusedItemPosition.
        if (mState.mFocusedItemId != NO_ID && mAdapter.hasStableIds()) {
            focusTarget = findViewHolderForItemId(mState.mFocusedItemId);
        }
        View viewToFocus = null;
        if (focusTarget == null || mChildHelper.isHidden(focusTarget.itemView)
                || !focusTarget.itemView.hasFocusable()) {
            if (mChildHelper.getChildCount() > 0) {
                // At this point, RV has focus and either of these conditions are true:
                // 1. There's no previously focused item either because RV received focused before
                // layout, or the previously focused item was removed, or RV doesn't have stable IDs
                // 2. Previous focus child is hidden, or 3. Previous focused child is no longer
                // focusable. In either of these cases, we make sure that RV still passes down the
                // focus to one of its focusable children using a best-effort algorithm.
                viewToFocus = findNextViewToFocus();
            }
        } else {
            // looks like the focused item has been replaced with another view that represents the
            // same item in the adapter. Request focus on that.
            viewToFocus = focusTarget.itemView;
        }

        if (viewToFocus != null) {
            if (mState.mFocusedSubChildId != NO_ID) {
                View child = viewToFocus.findViewById(mState.mFocusedSubChildId);
                if (child != null && child.isFocusable()) {
                    viewToFocus = child;
                }
            }
            viewToFocus.requestFocus();
        }
    }

    private int getDeepestFocusedViewWithId(View view) {
        int lastKnownId = view.getId();
        while (!view.isFocused() && view instanceof ViewGroup && view.hasFocus()) {
            view = ((ViewGroup) view).getFocusedChild();
            final int id = view.getId();
            if (id != View.NO_ID) {
                lastKnownId = view.getId();
            }
        }
        return lastKnownId;
    }

    final void fillRemainingScrollValues(State state) {
        if (getScrollState() == SCROLL_STATE_SETTLING) {
            final OverScroller scroller = mViewFlinger.mOverScroller;
            state.mRemainingScrollHorizontal = scroller.getFinalX() - scroller.getCurrX();
            state.mRemainingScrollVertical = scroller.getFinalY() - scroller.getCurrY();
        } else {
            state.mRemainingScrollHorizontal = 0;
            state.mRemainingScrollVertical = 0;
        }
    }

    /**
     * The first step of a layout where we;
     * - process adapter updates
     * - decide which animation should run
     * - save information about current views
     * - If necessary, run predictive layout and save its information
     */
    private void dispatchLayoutStep1() {
        mState.assertLayoutStep(State.STEP_START);
        fillRemainingScrollValues(mState);
        mState.mIsMeasuring = false;
        startInterceptRequestLayout();
        mViewInfoStore.clear();
        onEnterLayoutOrScroll();
        processAdapterUpdatesAndSetAnimationFlags();
        saveFocusInfo();
        mState.mTrackOldChangeHolders = mState.mRunSimpleAnimations && mItemsChanged;
        mItemsAddedOrRemoved = mItemsChanged = false;
        mState.mInPreLayout = mState.mRunPredictiveAnimations;
        mState.mItemCount = mAdapter.getItemCount();
        findMinMaxChildLayoutPositions(mMinMaxLayoutPositions);

        if (mState.mRunSimpleAnimations) {
            // Step 0: Find out where all non-removed items are, pre-layout
            int count = mChildHelper.getChildCount();
            for (int i = 0; i < count; ++i) {
                final ViewHolder holder = getChildViewHolderInt(mChildHelper.getChildAt(i));
                if (holder.shouldIgnore() || (holder.isInvalid() && !mAdapter.hasStableIds())) {
                    continue;
                }
                final ItemHolderInfo animationInfo = mItemAnimator
                        .recordPreLayoutInformation(mState, holder,
                                ItemAnimator.buildAdapterChangeFlagsForAnimations(holder),
                                holder.getUnmodifiedPayloads());
                mViewInfoStore.addToPreLayout(holder, animationInfo);
                if (mState.mTrackOldChangeHolders && holder.isUpdated() && !holder.isRemoved()
                        && !holder.shouldIgnore() && !holder.isInvalid()) {
                    long key = getChangedHolderKey(holder);
                    // This is NOT the only place where a ViewHolder is added to old change holders
                    // list. There is another case where:
                    //    * A VH is currently hidden but not deleted
                    //    * The hidden item is changed in the adapter
                    //    * Layout manager decides to layout the item in the pre-Layout pass (step1)
                    // When this case is detected, RV will un-hide that view and add to the old
                    // change holders list.
                    mViewInfoStore.addToOldChangeHolders(key, holder);
                }
            }
        }
        if (mState.mRunPredictiveAnimations) {
            // Step 1: run prelayout: This will use the old positions of items. The layout manager
            // is expected to layout everything, even removed items (though not to add removed
            // items back to the container). This gives the pre-layout position of APPEARING views
            // which come into existence as part of the real layout.

            // Save old positions so that LayoutManager can run its mapping logic.
            saveOldPositions();
            final boolean didStructureChange = mState.mStructureChanged;
            mState.mStructureChanged = false;
            // temporarily disable flag because we are asking for previous layout
            mLayout.onLayoutChildren(mRecycler, mState);
            mState.mStructureChanged = didStructureChange;

            for (int i = 0; i < mChildHelper.getChildCount(); ++i) {
                final View child = mChildHelper.getChildAt(i);
                final ViewHolder viewHolder = getChildViewHolderInt(child);
                if (viewHolder.shouldIgnore()) {
                    continue;
                }
                if (!mViewInfoStore.isInPreLayout(viewHolder)) {
                    int flags = ItemAnimator.buildAdapterChangeFlagsForAnimations(viewHolder);
                    boolean wasHidden = viewHolder
                            .hasAnyOfTheFlags(ViewHolder.FLAG_BOUNCED_FROM_HIDDEN_LIST);
                    if (!wasHidden) {
                        flags |= ItemAnimator.FLAG_APPEARED_IN_PRE_LAYOUT;
                    }
                    final ItemHolderInfo animationInfo = mItemAnimator.recordPreLayoutInformation(
                            mState, viewHolder, flags, viewHolder.getUnmodifiedPayloads());
                    if (wasHidden) {
                        recordAnimationInfoIfBouncedHiddenView(viewHolder, animationInfo);
                    } else {
                        mViewInfoStore.addToAppearedInPreLayoutHolders(viewHolder, animationInfo);
                    }
                }
            }
            // we don't process disappearing list because they may re-appear in post layout pass.
            clearOldPositions();
        } else {
            clearOldPositions();
        }
        onExitLayoutOrScroll();
        stopInterceptRequestLayout(false);
        mState.mLayoutStep = State.STEP_LAYOUT;
    }

    /**
     * The second layout step where we do the actual layout of the views for the final state.
     * This step might be run multiple times if necessary (e.g. measure).
     */
    private void dispatchLayoutStep2() {
        startInterceptRequestLayout();
        onEnterLayoutOrScroll();
        mState.assertLayoutStep(State.STEP_LAYOUT | State.STEP_ANIMATIONS);
        mAdapterHelper.consumeUpdatesInOnePass();
        mState.mItemCount = mAdapter.getItemCount();
        mState.mDeletedInvisibleItemCountSincePreviousLayout = 0;

        // Step 2: Run layout
        mState.mInPreLayout = false;
        mLayout.onLayoutChildren(mRecycler, mState);

        mState.mStructureChanged = false;
        mPendingSavedState = null;

        // onLayoutChildren may have caused client code to disable item animations; re-check
        mState.mRunSimpleAnimations = mState.mRunSimpleAnimations && mItemAnimator != null;
        mState.mLayoutStep = State.STEP_ANIMATIONS;
        onExitLayoutOrScroll();
        stopInterceptRequestLayout(false);
    }

    /**
     * The final step of the layout where we save the information about views for animations,
     * trigger animations and do any necessary cleanup.
     */
    private void dispatchLayoutStep3() {
        mState.assertLayoutStep(State.STEP_ANIMATIONS);
        startInterceptRequestLayout();
        onEnterLayoutOrScroll();
        mState.mLayoutStep = State.STEP_START;
        if (mState.mRunSimpleAnimations) {
            // Step 3: Find out where things are now, and process change animations.
            // traverse list in reverse because we may call animateChange in the loop which may
            // remove the target view holder.
            try {
                for (int i = mChildHelper.getChildCount() - 1; i >= 0; i--) {
                    ViewHolder holder = getChildViewHolderInt(mChildHelper.getChildAt(i));
                    if (holder.shouldIgnore()) {
                        continue;
                    }
                    long key = getChangedHolderKey(holder);
                    final ItemHolderInfo animationInfo = mItemAnimator
                            .recordPostLayoutInformation(mState, holder);
                    ViewHolder oldChangeViewHolder = mViewInfoStore.getFromOldChangeHolders(key);
                    if (oldChangeViewHolder != null && !oldChangeViewHolder.shouldIgnore()) {
                        // run a change animation

                        // If an Item is CHANGED but the updated version is disappearing, it creates
                        // a conflicting case.
                        // Since a view that is marked as disappearing is likely to be going out of
                        // bounds, we run a change animation. Both views will be cleaned automatically
                        // once their animations finish.
                        // On the other hand, if it is the same view holder instance, we run a
                        // disappearing animation instead because we are not going to rebind the updated
                        // VH unless it is enforced by the layout manager.
                        final boolean oldDisappearing = mViewInfoStore.isDisappearing(
                                oldChangeViewHolder);
                        final boolean newDisappearing = mViewInfoStore.isDisappearing(holder);
                        if (oldDisappearing && oldChangeViewHolder == holder) {
                            // run disappear animation instead of change
                            mViewInfoStore.addToPostLayout(holder, animationInfo);
                        } else {
                            final ItemHolderInfo preInfo = mViewInfoStore.popFromPreLayout(
                                    oldChangeViewHolder);
                            // we add and remove so that any post info is merged.
                            mViewInfoStore.addToPostLayout(holder, animationInfo);
                            ItemHolderInfo postInfo = mViewInfoStore.popFromPostLayout(holder);
                            if (preInfo == null) {
                                handleMissingPreInfoForChangeError(key, holder, oldChangeViewHolder);
                            } else {
                                animateChange(oldChangeViewHolder, holder, preInfo, postInfo,
                                        oldDisappearing, newDisappearing);
                            }
                        }
                    } else {
                        mViewInfoStore.addToPostLayout(holder, animationInfo);
                    }
                }
            } catch (Exception e) {
                StringBuilder builder = new StringBuilder();
                for (int i = mChildHelper.getChildCount() - 1; i >= 0; i--) {
                    ViewHolder holder = getChildViewHolderInt(mChildHelper.getChildAt(i));
                    if (holder.shouldIgnore()) {
                        continue;
                    }
                    builder.append("Holder at" + i + " " + holder + "\n");
                }
                throw new RuntimeException(builder.toString(), e);
            }

            // Step 4: Process view info lists and trigger animations
            mViewInfoStore.process(mViewInfoProcessCallback);
        }

        mLayout.removeAndRecycleScrapInt(mRecycler);
        mState.mPreviousLayoutItemCount = mState.mItemCount;
        mDataSetHasChangedAfterLayout = false;
        mDispatchItemsChangedEvent = false;
        mState.mRunSimpleAnimations = false;

        mState.mRunPredictiveAnimations = false;
        mLayout.mRequestedSimpleAnimations = false;
        if (mRecycler.mChangedScrap != null) {
            mRecycler.mChangedScrap.clear();
        }
        if (mLayout.mPrefetchMaxObservedInInitialPrefetch) {
            // Initial prefetch has expanded cache, so reset until next prefetch.
            // This prevents initial prefetches from expanding the cache permanently.
            mLayout.mPrefetchMaxCountObserved = 0;
            mLayout.mPrefetchMaxObservedInInitialPrefetch = false;
            mRecycler.updateViewCacheSize();
        }

        mLayout.onLayoutCompleted(mState);
        onExitLayoutOrScroll();
        stopInterceptRequestLayout(false);
        mViewInfoStore.clear();
        if (didChildRangeChange(mMinMaxLayoutPositions[0], mMinMaxLayoutPositions[1])) {
            dispatchOnScrolled(0, 0);
        }
        recoverFocusFromState();
        resetFocusInfo();
    }

    /**
     * This handles the case where there is an unexpected VH missing in the pre-layout map.
     * <p>
     * We might be able to detect the error in the application which will help the developer to
     * resolve the issue.
     * <p>
     * If it is not an expected error, we at least print an error to notify the developer and ignore
     * the animation.
     *
     * https://code.google.com/p/android/issues/detail?id=193958
     *
     * @param key The change key
     * @param holder Current ViewHolder
     * @param oldChangeViewHolder Changed ViewHolder
     */
    private void handleMissingPreInfoForChangeError(long key,
            ViewHolder holder, ViewHolder oldChangeViewHolder) {
        // check if two VH have the same key, if so, print that as an error
        final int childCount = mChildHelper.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View view = mChildHelper.getChildAt(i);
            ViewHolder other = getChildViewHolderInt(view);
            if (other == holder) {
                continue;
            }
            final long otherKey = getChangedHolderKey(other);
            if (otherKey == key) {
                if (mAdapter != null && mAdapter.hasStableIds()) {
                    throw new IllegalStateException("Two different ViewHolders have the same stable"
                            + " ID. Stable IDs in your adapter MUST BE unique and SHOULD NOT"
                            + " change.\n ViewHolder 1:" + other + " \n View Holder 2:" + holder
                            + exceptionLabel());
                } else {
                    throw new IllegalStateException("Two different ViewHolders have the same change"
                            + " ID. This might happen due to inconsistent Adapter update events or"
                            + " if the LayoutManager lays out the same View multiple times."
                            + "\n ViewHolder 1:" + other + " \n View Holder 2:" + holder
                            + exceptionLabel());
                }
            }
        }
        // Very unlikely to happen but if it does, notify the developer.
        Log.e(TAG, "Problem while matching changed view holders with the new"
                + "ones. The pre-layout information for the change holder " + oldChangeViewHolder
                + " cannot be found but it is necessary for " + holder + exceptionLabel());
    }

    /**
     * Records the animation information for a view holder that was bounced from hidden list. It
     * also clears the bounce back flag.
     */
    void recordAnimationInfoIfBouncedHiddenView(ViewHolder viewHolder,
            ItemHolderInfo animationInfo) {
        // looks like this view bounced back from hidden list!
        viewHolder.setFlags(0, ViewHolder.FLAG_BOUNCED_FROM_HIDDEN_LIST);
        if (mState.mTrackOldChangeHolders && viewHolder.isUpdated()
                && !viewHolder.isRemoved() && !viewHolder.shouldIgnore()) {
            long key = getChangedHolderKey(viewHolder);
            mViewInfoStore.addToOldChangeHolders(key, viewHolder);
        }
        mViewInfoStore.addToPreLayout(viewHolder, animationInfo);
    }

    private void findMinMaxChildLayoutPositions(int[] into) {
        final int count = mChildHelper.getChildCount();
        if (count == 0) {
            into[0] = NO_POSITION;
            into[1] = NO_POSITION;
            return;
        }
        int minPositionPreLayout = Integer.MAX_VALUE;
        int maxPositionPreLayout = Integer.MIN_VALUE;
        for (int i = 0; i < count; ++i) {
            final ViewHolder holder = getChildViewHolderInt(mChildHelper.getChildAt(i));
            if (holder.shouldIgnore()) {
                continue;
            }
            final int pos = holder.getLayoutPosition();
            if (pos < minPositionPreLayout) {
                minPositionPreLayout = pos;
            }
            if (pos > maxPositionPreLayout) {
                maxPositionPreLayout = pos;
            }
        }
        into[0] = minPositionPreLayout;
        into[1] = maxPositionPreLayout;
    }

    private boolean didChildRangeChange(int minPositionPreLayout, int maxPositionPreLayout) {
        findMinMaxChildLayoutPositions(mMinMaxLayoutPositions);
        return mMinMaxLayoutPositions[0] != minPositionPreLayout
                || mMinMaxLayoutPositions[1] != maxPositionPreLayout;
    }

    @Override
    protected void removeDetachedView(View child, boolean animate) {
        ViewHolder vh = getChildViewHolderInt(child);
        if (vh != null) {
            if (vh.isTmpDetached()) {
                vh.clearTmpDetachFlag();
            } else if (!vh.shouldIgnore()) {
                throw new IllegalArgumentException("Called removeDetachedView with a view which"
                        + " is not flagged as tmp detached." + vh + exceptionLabel());
            }
        }

        // Clear any android.view.animation.Animation that may prevent the item from
        // detaching when being removed. If a child is re-added before the
        // lazy detach occurs, it will receive invalid attach/detach sequencing.
        child.clearAnimation();

        dispatchChildDetached(child);
        super.removeDetachedView(child, animate);
    }

    /**
     * Returns a unique key to be used while handling change animations.
     * It might be child's position or stable id depending on the adapter type.
     */
    long getChangedHolderKey(ViewHolder holder) {
        return mAdapter.hasStableIds() ? holder.getItemId() : holder.mPosition;
    }

    void animateAppearance(@NonNull ViewHolder itemHolder,
            @Nullable ItemHolderInfo preLayoutInfo, @NonNull ItemHolderInfo postLayoutInfo) {
        itemHolder.setIsRecyclable(false);
        if (mItemAnimator.animateAppearance(itemHolder, preLayoutInfo, postLayoutInfo)) {
            postAnimationRunner();
        }
    }

    void animateDisappearance(@NonNull ViewHolder holder,
            @NonNull ItemHolderInfo preLayoutInfo, @Nullable ItemHolderInfo postLayoutInfo) {
        addAnimatingView(holder);
        holder.setIsRecyclable(false);
        if (mItemAnimator.animateDisappearance(holder, preLayoutInfo, postLayoutInfo)) {
            postAnimationRunner();
        }
    }

    private void animateChange(@NonNull ViewHolder oldHolder, @NonNull ViewHolder newHolder,
            @NonNull ItemHolderInfo preInfo, @NonNull ItemHolderInfo postInfo,
            boolean oldHolderDisappearing, boolean newHolderDisappearing) {
        oldHolder.setIsRecyclable(false);
        if (oldHolderDisappearing) {
            addAnimatingView(oldHolder);
        }
        if (oldHolder != newHolder) {
            if (newHolderDisappearing) {
                addAnimatingView(newHolder);
            }
            oldHolder.mShadowedHolder = newHolder;
            // old holder should disappear after animation ends
            addAnimatingView(oldHolder);
            mRecycler.unscrapView(oldHolder);
            newHolder.setIsRecyclable(false);
            newHolder.mShadowingHolder = oldHolder;
        }
        if (mItemAnimator.animateChange(oldHolder, newHolder, preInfo, postInfo)) {
            postAnimationRunner();
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        TraceCompat.beginSection(TRACE_ON_LAYOUT_TAG);
        dispatchLayout();
        TraceCompat.endSection();
        mFirstLayoutComplete = true;
    }

    @Override
    public void requestLayout() {
        if (mInterceptRequestLayoutDepth == 0 && !mLayoutSuppressed) {
            super.requestLayout();
        } else {
            mLayoutWasDefered = true;
        }
    }

    void markItemDecorInsetsDirty() {
        final int childCount = mChildHelper.getUnfilteredChildCount();
        for (int i = 0; i < childCount; i++) {
            final View child = mChildHelper.getUnfilteredChildAt(i);
            ((LayoutParams) child.getLayoutParams()).mInsetsDirty = true;
        }
        mRecycler.markItemDecorInsetsDirty();
    }

    @Override
    public void draw(Canvas c) {
        super.draw(c);

        final int count = mItemDecorations.size();
        for (int i = 0; i < count; i++) {
            mItemDecorations.get(i).onDrawOver(c, this, mState);
        }
        // TODO If padding is not 0 and clipChildrenToPadding is false, to draw glows properly, we
        // need find children closest to edges. Not sure if it is worth the effort.
        boolean needsInvalidate = false;
        if (glowColor == null || glowColor != 0) {
            if (mLeftGlow != null && !mLeftGlow.isFinished()) {
                final int restore = c.save();
                final int padding = mClipToPadding ? getPaddingBottom() : 0;
                c.rotate(270);
                c.translate(-getHeight() + padding, 0);
                needsInvalidate = mLeftGlow != null && mLeftGlow.draw(c);
                c.restoreToCount(restore);
            }
            if (mTopGlow != null && !mTopGlow.isFinished()) {
                final int restore = c.save();
                if (mClipToPadding) {
                    c.translate(getPaddingLeft(), getPaddingTop());
                }
                c.translate(0, topGlowOffset);
                needsInvalidate |= mTopGlow != null && mTopGlow.draw(c);
                c.restoreToCount(restore);
            }
            if (mRightGlow != null && !mRightGlow.isFinished()) {
                final int restore = c.save();
                final int width = getWidth();
                final int padding = mClipToPadding ? getPaddingTop() : 0;
                c.rotate(90);
                c.translate(-padding, -width);
                needsInvalidate |= mRightGlow != null && mRightGlow.draw(c);
                c.restoreToCount(restore);
            }
            if (mBottomGlow != null && !mBottomGlow.isFinished()) {
                final int restore = c.save();
                c.rotate(180);
                if (mClipToPadding) {
                    c.translate(-getWidth() + getPaddingRight(), -getHeight() + getPaddingBottom());
                } else {
                    c.translate(-getWidth(), -getHeight() + bottomGlowOffset);
                }
                needsInvalidate |= mBottomGlow != null && mBottomGlow.draw(c);
                c.restoreToCount(restore);
            }
        }

        // If some views are animating, ItemDecorators are likely to move/change with them.
        // Invalidate RecyclerView to re-draw decorators. This is still efficient because children's
        // display lists are not invalidated.
        if (!needsInvalidate && mItemAnimator != null && mItemDecorations.size() > 0
                && mItemAnimator.isRunning()) {
            needsInvalidate = true;
        }

        if (needsInvalidate) {
            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    @Override
    public void onDraw(Canvas c) {
        super.onDraw(c);

        final int count = mItemDecorations.size();
        for (int i = 0; i < count; i++) {
            mItemDecorations.get(i).onDraw(c, this, mState);
        }
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams && mLayout.checkLayoutParams((LayoutParams) p);
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        if (mLayout == null) {
            throw new IllegalStateException("RecyclerView has no LayoutManager" + exceptionLabel());
        }
        return mLayout.generateDefaultLayoutParams();
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        if (mLayout == null) {
            throw new IllegalStateException("RecyclerView has no LayoutManager" + exceptionLabel());
        }
        return mLayout.generateLayoutParams(getContext(), attrs);
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        if (mLayout == null) {
            throw new IllegalStateException("RecyclerView has no LayoutManager" + exceptionLabel());
        }
        return mLayout.generateLayoutParams(p);
    }

    /**
     * Returns true if RecyclerView is currently running some animations.
     * <p>
     * If you want to be notified when animations are finished, use
     * {@link ItemAnimator#isRunning(ItemAnimator.ItemAnimatorFinishedListener)}.
     *
     * @return True if there are some item animations currently running or waiting to be started.
     */
    public boolean isAnimating() {
        return mItemAnimator != null && mItemAnimator.isRunning();
    }

    void saveOldPositions() {
        final int childCount = mChildHelper.getUnfilteredChildCount();
        for (int i = 0; i < childCount; i++) {
            final ViewHolder holder = getChildViewHolderInt(mChildHelper.getUnfilteredChildAt(i));
            if (DEBUG && holder.mPosition == -1 && !holder.isRemoved()) {
                FileLog.e(new IllegalStateException("view holder cannot have position -1 unless it"
                        + " is removed" + exceptionLabel()));
            }
            if (!holder.shouldIgnore()) {
                holder.saveOldPosition();
            }
        }
    }

    void clearOldPositions() {
        final int childCount = mChildHelper.getUnfilteredChildCount();
        for (int i = 0; i < childCount; i++) {
            final ViewHolder holder = getChildViewHolderInt(mChildHelper.getUnfilteredChildAt(i));
            if (!holder.shouldIgnore()) {
                holder.clearOldPosition();
            }
        }
        mRecycler.clearOldPositions();
    }

    void offsetPositionRecordsForMove(int from, int to) {
        final int childCount = mChildHelper.getUnfilteredChildCount();
        final int start, end, inBetweenOffset;
        if (from < to) {
            start = from;
            end = to;
            inBetweenOffset = -1;
        } else {
            start = to;
            end = from;
            inBetweenOffset = 1;
        }

        for (int i = 0; i < childCount; i++) {
            final ViewHolder holder = getChildViewHolderInt(mChildHelper.getUnfilteredChildAt(i));
            if (holder == null || holder.mPosition < start || holder.mPosition > end) {
                continue;
            }
            if (DEBUG) {
                Log.d(TAG, "offsetPositionRecordsForMove attached child " + i + " holder "
                        + holder);
            }
            if (holder.mPosition == from) {
                holder.offsetPosition(to - from, false);
            } else {
                holder.offsetPosition(inBetweenOffset, false);
            }

            mState.mStructureChanged = true;
        }
        mRecycler.offsetPositionRecordsForMove(from, to);
        requestLayout();
    }

    void offsetPositionRecordsForInsert(int positionStart, int itemCount) {
        final int childCount = mChildHelper.getUnfilteredChildCount();
        for (int i = 0; i < childCount; i++) {
            final ViewHolder holder = getChildViewHolderInt(mChildHelper.getUnfilteredChildAt(i));
            if (holder != null && !holder.shouldIgnore() && holder.mPosition >= positionStart) {
                if (DEBUG) {
                    Log.d(TAG, "offsetPositionRecordsForInsert attached child " + i + " holder "
                            + holder + " now at position " + (holder.mPosition + itemCount));
                }
                holder.offsetPosition(itemCount, false);
                mState.mStructureChanged = true;
            }
        }
        mRecycler.offsetPositionRecordsForInsert(positionStart, itemCount);
        requestLayout();
    }

    void offsetPositionRecordsForRemove(int positionStart, int itemCount,
            boolean applyToPreLayout) {
        final int positionEnd = positionStart + itemCount;
        final int childCount = mChildHelper.getUnfilteredChildCount();
        for (int i = 0; i < childCount; i++) {
            final ViewHolder holder = getChildViewHolderInt(mChildHelper.getUnfilteredChildAt(i));
            if (holder != null && !holder.shouldIgnore()) {
                if (holder.mPosition >= positionEnd) {
                    if (DEBUG) {
                        Log.d(TAG, "offsetPositionRecordsForRemove attached child " + i
                                + " holder " + holder + " now at position "
                                + (holder.mPosition - itemCount));
                    }
                    holder.offsetPosition(-itemCount, applyToPreLayout);
                    mState.mStructureChanged = true;
                } else if (holder.mPosition >= positionStart) {
                    if (DEBUG) {
                        Log.d(TAG, "offsetPositionRecordsForRemove attached child " + i
                                + " holder " + holder + " now REMOVED");
                    }
                    holder.flagRemovedAndOffsetPosition(positionStart - 1, -itemCount,
                            applyToPreLayout);
                    mState.mStructureChanged = true;
                }
            }
        }
        mRecycler.offsetPositionRecordsForRemove(positionStart, itemCount, applyToPreLayout);
        requestLayout();
    }

    /**
     * Rebind existing views for the given range, or create as needed.
     *
     * @param positionStart Adapter position to start at
     * @param itemCount Number of views that must explicitly be rebound
     */
    void viewRangeUpdate(int positionStart, int itemCount, Object payload) {
        final int childCount = mChildHelper.getUnfilteredChildCount();
        final int positionEnd = positionStart + itemCount;

        for (int i = 0; i < childCount; i++) {
            final View child = mChildHelper.getUnfilteredChildAt(i);
            final ViewHolder holder = getChildViewHolderInt(child);
            if (holder == null || holder.shouldIgnore()) {
                continue;
            }
            if (holder.mPosition >= positionStart && holder.mPosition < positionEnd) {
                // We re-bind these view holders after pre-processing is complete so that
                // ViewHolders have their final positions assigned.
                holder.addFlags(ViewHolder.FLAG_UPDATE);
                holder.addChangePayload(payload);
                // lp cannot be null since we get ViewHolder from it.
                ((LayoutParams) child.getLayoutParams()).mInsetsDirty = true;
            }
        }
        mRecycler.viewRangeUpdate(positionStart, itemCount);
    }

    boolean canReuseUpdatedViewHolder(ViewHolder viewHolder) {
        return mItemAnimator == null || mItemAnimator.canReuseUpdatedViewHolder(viewHolder,
                viewHolder.getUnmodifiedPayloads());
    }

    /**
     * Processes the fact that, as far as we can tell, the data set has completely changed.
     *
     * <ul>
     *   <li>Once layout occurs, all attached items should be discarded or animated.
     *   <li>Attached items are labeled as invalid.
     *   <li>Because items may still be prefetched between a "data set completely changed"
     *       event and a layout event, all cached items are discarded.
     * </ul>
     *
     * @param dispatchItemsChanged Whether to call
     * {@link LayoutManager#onItemsChanged(RecyclerView)} during measure/layout.
     */
    void processDataSetCompletelyChanged(boolean dispatchItemsChanged) {
        mDispatchItemsChangedEvent |= dispatchItemsChanged;
        mDataSetHasChangedAfterLayout = true;
        markKnownViewsInvalid();
    }

    /**
     * Mark all known views as invalid. Used in response to a, "the whole world might have changed"
     * data change event.
     */
    void markKnownViewsInvalid() {
        final int childCount = mChildHelper.getUnfilteredChildCount();
        for (int i = 0; i < childCount; i++) {
            final ViewHolder holder = getChildViewHolderInt(mChildHelper.getUnfilteredChildAt(i));
            if (holder != null && !holder.shouldIgnore()) {
                holder.addFlags(ViewHolder.FLAG_UPDATE | ViewHolder.FLAG_INVALID);
            }
        }
        markItemDecorInsetsDirty();
        mRecycler.markKnownViewsInvalid();
    }

    /**
     * Invalidates all ItemDecorations. If RecyclerView has item decorations, calling this method
     * will trigger a {@link #requestLayout()} call.
     */
    public void invalidateItemDecorations() {
        if (mItemDecorations.size() == 0) {
            return;
        }
        if (mLayout != null) {
            mLayout.assertNotInLayoutOrScroll("Cannot invalidate item decorations during a scroll"
                    + " or layout");
        }
        markItemDecorInsetsDirty();
        requestLayout();
    }

    /**
     * Returns true if the RecyclerView should attempt to preserve currently focused Adapter Item's
     * focus even if the View representing the Item is replaced during a layout calculation.
     * <p>
     * By default, this value is {@code true}.
     *
     * @return True if the RecyclerView will try to preserve focused Item after a layout if it loses
     * focus.
     *
     * @see #setPreserveFocusAfterLayout(boolean)
     */
    public boolean getPreserveFocusAfterLayout() {
        return mPreserveFocusAfterLayout;
    }

    /**
     * Set whether the RecyclerView should try to keep the same Item focused after a layout
     * calculation or not.
     * <p>
     * Usually, LayoutManagers keep focused views visible before and after layout but sometimes,
     * views may lose focus during a layout calculation as their state changes or they are replaced
     * with another view due to type change or animation. In these cases, RecyclerView can request
     * focus on the new view automatically.
     *
     * @param preserveFocusAfterLayout Whether RecyclerView should preserve focused Item during a
     *                                 layout calculations. Defaults to true.
     *
     * @see #getPreserveFocusAfterLayout()
     */
    public void setPreserveFocusAfterLayout(boolean preserveFocusAfterLayout) {
        mPreserveFocusAfterLayout = preserveFocusAfterLayout;
    }

    /**
     * Retrieve the {@link ViewHolder} for the given child view.
     *
     * @param child Child of this RecyclerView to query for its ViewHolder
     * @return The child view's ViewHolder
     */
    public ViewHolder getChildViewHolder(@NonNull View child) {
        final ViewParent parent = child.getParent();
        if (parent != null && parent != this) {
            throw new IllegalArgumentException("View " + child + " is not a direct child of "
                    + this);
        }
        return getChildViewHolderInt(child);
    }

    /**
     * Traverses the ancestors of the given view and returns the item view that contains it and
     * also a direct child of the RecyclerView. This returned view can be used to get the
     * ViewHolder by calling {@link #getChildViewHolder(View)}.
     *
     * @param view The view that is a descendant of the RecyclerView.
     *
     * @return The direct child of the RecyclerView which contains the given view or null if the
     * provided view is not a descendant of this RecyclerView.
     *
     * @see #getChildViewHolder(View)
     * @see #findContainingViewHolder(View)
     */
    @Nullable
    public View findContainingItemView(@NonNull View view) {
        ViewParent parent = view.getParent();
        while (parent != null && parent != this && parent instanceof View) {
            view = (View) parent;
            parent = view.getParent();
        }
        return parent == this ? view : null;
    }

    /**
     * Returns the ViewHolder that contains the given view.
     *
     * @param view The view that is a descendant of the RecyclerView.
     *
     * @return The ViewHolder that contains the given view or null if the provided view is not a
     * descendant of this RecyclerView.
     */
    @Nullable
    public ViewHolder findContainingViewHolder(@NonNull View view) {
        View itemView = findContainingItemView(view);
        return itemView == null ? null : getChildViewHolder(itemView);
    }


    static ViewHolder getChildViewHolderInt(View child) {
        if (child == null) {
            return null;
        }
        return ((LayoutParams) child.getLayoutParams()).mViewHolder;
    }

    /**
     * @deprecated use {@link #getChildAdapterPosition(View)} or
     * {@link #getChildLayoutPosition(View)}.
     */
    @Deprecated
    public int getChildPosition(@NonNull View child) {
        return getChildAdapterPosition(child);
    }

    /**
     * Return the adapter position that the given child view corresponds to.
     *
     * @param child Child View to query
     * @return Adapter position corresponding to the given view or {@link #NO_POSITION}
     */
    public int getChildAdapterPosition(@NonNull View child) {
        final ViewHolder holder = getChildViewHolderInt(child);
        return holder != null ? holder.getAdapterPosition() : NO_POSITION;
    }

    /**
     * Return the adapter position of the given child view as of the latest completed layout pass.
     * <p>
     * This position may not be equal to Item's adapter position if there are pending changes
     * in the adapter which have not been reflected to the layout yet.
     *
     * @param child Child View to query
     * @return Adapter position of the given View as of last layout pass or {@link #NO_POSITION} if
     * the View is representing a removed item.
     */
    public int getChildLayoutPosition(@NonNull View child) {
        final ViewHolder holder = getChildViewHolderInt(child);
        return holder != null ? holder.getLayoutPosition() : NO_POSITION;
    }

    /**
     * Return the stable item id that the given child view corresponds to.
     *
     * @param child Child View to query
     * @return Item id corresponding to the given view or {@link #NO_ID}
     */
    public long getChildItemId(@NonNull View child) {
        if (mAdapter == null || !mAdapter.hasStableIds()) {
            return NO_ID;
        }
        final ViewHolder holder = getChildViewHolderInt(child);
        return holder != null ? holder.getItemId() : NO_ID;
    }

    /**
     * @deprecated use {@link #findViewHolderForLayoutPosition(int)} or
     * {@link #findViewHolderForAdapterPosition(int)}
     */
    @Deprecated
    @Nullable
    public ViewHolder findViewHolderForPosition(int position) {
        return findViewHolderForPosition(position, false);
    }

    /**
     * Return the ViewHolder for the item in the given position of the data set as of the latest
     * layout pass.
     * <p>
     * This method checks only the children of RecyclerView. If the item at the given
     * <code>position</code> is not laid out, it <em>will not</em> create a new one.
     * <p>
     * Note that when Adapter contents change, ViewHolder positions are not updated until the
     * next layout calculation. If there are pending adapter updates, the return value of this
     * method may not match your adapter contents. You can use
     * #{@link ViewHolder#getAdapterPosition()} to get the current adapter position of a ViewHolder.
     * <p>
     * When the ItemAnimator is running a change animation, there might be 2 ViewHolders
     * with the same layout position representing the same Item. In this case, the updated
     * ViewHolder will be returned.
     *
     * @param position The position of the item in the data set of the adapter
     * @return The ViewHolder at <code>position</code> or null if there is no such item
     */
    @Nullable
    public ViewHolder findViewHolderForLayoutPosition(int position) {
        return findViewHolderForPosition(position, false);
    }

    /**
     * Return the ViewHolder for the item in the given position of the data set. Unlike
     * {@link #findViewHolderForLayoutPosition(int)} this method takes into account any pending
     * adapter changes that may not be reflected to the layout yet. On the other hand, if
     * {@link Adapter#notifyDataSetChanged()} has been called but the new layout has not been
     * calculated yet, this method will return <code>null</code> since the new positions of views
     * are unknown until the layout is calculated.
     * <p>
     * This method checks only the children of RecyclerView. If the item at the given
     * <code>position</code> is not laid out, it <em>will not</em> create a new one.
     * <p>
     * When the ItemAnimator is running a change animation, there might be 2 ViewHolders
     * representing the same Item. In this case, the updated ViewHolder will be returned.
     *
     * @param position The position of the item in the data set of the adapter
     * @return The ViewHolder at <code>position</code> or null if there is no such item
     */
    @Nullable
    public ViewHolder findViewHolderForAdapterPosition(int position) {
        if (mDataSetHasChangedAfterLayout) {
            return null;
        }
        final int childCount = mChildHelper.getUnfilteredChildCount();
        // hidden VHs are not preferred but if that is the only one we find, we rather return it
        ViewHolder hidden = null;
        for (int i = 0; i < childCount; i++) {
            final ViewHolder holder = getChildViewHolderInt(mChildHelper.getUnfilteredChildAt(i));
            if (holder != null && !holder.isRemoved()
                    && getAdapterPositionFor(holder) == position) {
                if (mChildHelper.isHidden(holder.itemView)) {
                    hidden = holder;
                } else {
                    return holder;
                }
            }
        }
        return hidden;
    }

    @Nullable
    ViewHolder findViewHolderForPosition(int position, boolean checkNewPosition) {
        final int childCount = mChildHelper.getUnfilteredChildCount();
        ViewHolder hidden = null;
        for (int i = 0; i < childCount; i++) {
            final ViewHolder holder = getChildViewHolderInt(mChildHelper.getUnfilteredChildAt(i));
            if (holder != null && !holder.isRemoved()) {
                if (checkNewPosition) {
                    if (holder.mPosition != position) {
                        continue;
                    }
                } else if (holder.getLayoutPosition() != position) {
                    continue;
                }
                if (mChildHelper.isHidden(holder.itemView)) {
                    hidden = holder;
                } else {
                    return holder;
                }
            }
        }
        // This method should not query cached views. It creates a problem during adapter updates
        // when we are dealing with already laid out views. Also, for the public method, it is more
        // reasonable to return null if position is not laid out.
        return hidden;
    }

    /**
     * Return the ViewHolder for the item with the given id. The RecyclerView must
     * use an Adapter with {@link Adapter#setHasStableIds(boolean) stableIds} to
     * return a non-null value.
     * <p>
     * This method checks only the children of RecyclerView. If the item with the given
     * <code>id</code> is not laid out, it <em>will not</em> create a new one.
     *
     * When the ItemAnimator is running a change animation, there might be 2 ViewHolders with the
     * same id. In this case, the updated ViewHolder will be returned.
     *
     * @param id The id for the requested item
     * @return The ViewHolder with the given <code>id</code> or null if there is no such item
     */
    public ViewHolder findViewHolderForItemId(long id) {
        if (mAdapter == null || !mAdapter.hasStableIds()) {
            return null;
        }
        final int childCount = mChildHelper.getUnfilteredChildCount();
        ViewHolder hidden = null;
        for (int i = 0; i < childCount; i++) {
            final ViewHolder holder = getChildViewHolderInt(mChildHelper.getUnfilteredChildAt(i));
            if (holder != null && !holder.isRemoved() && holder.getItemId() == id) {
                if (mChildHelper.isHidden(holder.itemView)) {
                    hidden = holder;
                } else {
                    return holder;
                }
            }
        }
        return hidden;
    }

    /**
     * Find the topmost view under the given point.
     *
     * @param x Horizontal position in pixels to search
     * @param y Vertical position in pixels to search
     * @return The child view under (x, y) or null if no matching child is found
     */
    @Nullable
    public View findChildViewUnder(float x, float y) {
        final int count = mChildHelper.getChildCount();
        for (int i = count - 1; i >= 0; i--) {
            final View child = mChildHelper.getChildAt(i);
            final float translationX = child.getTranslationX();
            final float translationY = child.getTranslationY();
            if (x >= child.getLeft() + translationX
                    && x <= child.getRight() + translationX
                    && y >= child.getTop() + translationY
                    && y <= child.getBottom() + translationY) {
                return child;
            }
        }
        return null;
    }

    @Override
    public boolean drawChild(Canvas canvas, View child, long drawingTime) {
        return super.drawChild(canvas, child, drawingTime);
    }

    /**
     * Offset the bounds of all child views by <code>dy</code> pixels.
     * Useful for implementing simple scrolling in {@link LayoutManager LayoutManagers}.
     *
     * @param dy Vertical pixel offset to apply to the bounds of all child views
     */
    public void offsetChildrenVertical(@Px int dy) {
        final int childCount = mChildHelper.getChildCount();
        for (int i = 0; i < childCount; i++) {
            mChildHelper.getChildAt(i).offsetTopAndBottom(dy);
        }
    }

    /**
     * Called when an item view is attached to this RecyclerView.
     *
     * <p>Subclasses of RecyclerView may want to perform extra bookkeeping or modifications
     * of child views as they become attached. This will be called before a
     * {@link LayoutManager} measures or lays out the view and is a good time to perform these
     * changes.</p>
     *
     * @param child Child view that is now attached to this RecyclerView and its associated window
     */
    public void onChildAttachedToWindow(@NonNull View child) {
    }

    /**
     * Called when an item view is detached from this RecyclerView.
     *
     * <p>Subclasses of RecyclerView may want to perform extra bookkeeping or modifications
     * of child views as they become detached. This will be called as a
     * {@link LayoutManager} fully detaches the child view from the parent and its window.</p>
     *
     * @param child Child view that is now detached from this RecyclerView and its associated window
     */
    public void onChildDetachedFromWindow(@NonNull View child) {
    }

    /**
     * Offset the bounds of all child views by <code>dx</code> pixels.
     * Useful for implementing simple scrolling in {@link LayoutManager LayoutManagers}.
     *
     * @param dx Horizontal pixel offset to apply to the bounds of all child views
     */
    public void offsetChildrenHorizontal(@Px int dx) {
        final int childCount = mChildHelper.getChildCount();
        for (int i = 0; i < childCount; i++) {
            mChildHelper.getChildAt(i).offsetLeftAndRight(dx);
        }
    }

    /**
     * Returns the bounds of the view including its decoration and margins.
     *
     * @param view The view element to check
     * @param outBounds A rect that will receive the bounds of the element including its
     *                  decoration and margins.
     */
    public void getDecoratedBoundsWithMargins(@NonNull View view, @NonNull Rect outBounds) {
        getDecoratedBoundsWithMarginsInt(view, outBounds);
    }

    static void getDecoratedBoundsWithMarginsInt(View view, Rect outBounds) {
        final LayoutParams lp = (LayoutParams) view.getLayoutParams();
        final Rect insets = lp.mDecorInsets;
        outBounds.set(view.getLeft() - insets.left - lp.leftMargin,
                view.getTop() - insets.top - lp.topMargin,
                view.getRight() + insets.right + lp.rightMargin,
                view.getBottom() + insets.bottom + lp.bottomMargin);
    }

    Rect getItemDecorInsetsForChild(View child) {
        final LayoutParams lp = (LayoutParams) child.getLayoutParams();
        if (!lp.mInsetsDirty) {
            return lp.mDecorInsets;
        }

        if (mState.isPreLayout() && (lp.isItemChanged() || lp.isViewInvalid())) {
            // changed/invalid items should not be updated until they are rebound.
            return lp.mDecorInsets;
        }
        final Rect insets = lp.mDecorInsets;
        insets.set(0, 0, 0, 0);
        final int decorCount = mItemDecorations.size();
        for (int i = 0; i < decorCount; i++) {
            mTempRect.set(0, 0, 0, 0);
            mItemDecorations.get(i).getItemOffsets(mTempRect, child, this, mState);
            insets.left += mTempRect.left;
            insets.top += mTempRect.top;
            insets.right += mTempRect.right;
            insets.bottom += mTempRect.bottom;
        }
        lp.mInsetsDirty = false;
        return insets;
    }

    /**
     * Called when the scroll position of this RecyclerView changes. Subclasses should use
     * this method to respond to scrolling within the adapter's data set instead of an explicit
     * listener.
     *
     * <p>This method will always be invoked before listeners. If a subclass needs to perform
     * any additional upkeep or bookkeeping after scrolling but before listeners run,
     * this is a good place to do so.</p>
     *
     * <p>This differs from {@link View#onScrollChanged(int, int, int, int)} in that it receives
     * the distance scrolled in either direction within the adapter's data set instead of absolute
     * scroll coordinates. Since RecyclerView cannot compute the absolute scroll position from
     * any arbitrary point in the data set, <code>onScrollChanged</code> will always receive
     * the current {@link View#getScrollX()} and {@link View#getScrollY()} values which
     * do not correspond to the data set scroll position. However, some subclasses may choose
     * to use these fields as special offsets.</p>
     *
     * @param dx horizontal distance scrolled in pixels
     * @param dy vertical distance scrolled in pixels
     */
    public void onScrolled(@Px int dx, @Px int dy) {
        // Do nothing
    }

    void dispatchOnScrolled(int hresult, int vresult) {
        mDispatchScrollCounter++;
        // Pass the current scrollX/scrollY values; no actual change in these properties occurred
        // but some general-purpose code may choose to respond to changes this way.
        final int scrollX = getScrollX();
        final int scrollY = getScrollY();
        onScrollChanged(scrollX, scrollY, scrollX, scrollY);

        // Pass the real deltas to onScrolled, the RecyclerView-specific method.
        onScrolled(hresult, vresult);

        // Invoke listeners last. Subclassed view methods always handle the event first.
        // All internal state is consistent by the time listeners are invoked.
        if (mScrollListener != null) {
            mScrollListener.onScrolled(this, hresult, vresult);
        }
        if (mScrollListeners != null) {
            for (int i = mScrollListeners.size() - 1; i >= 0; i--) {
                mScrollListeners.get(i).onScrolled(this, hresult, vresult);
            }
        }
        mDispatchScrollCounter--;
    }

    /**
     * Called when the scroll state of this RecyclerView changes. Subclasses should use this
     * method to respond to state changes instead of an explicit listener.
     *
     * <p>This method will always be invoked before listeners, but after the LayoutManager
     * responds to the scroll state change.</p>
     *
     * @param state the new scroll state, one of {@link #SCROLL_STATE_IDLE},
     *              {@link #SCROLL_STATE_DRAGGING} or {@link #SCROLL_STATE_SETTLING}
     */
    public void onScrollStateChanged(int state) {
        // Do nothing
    }

    void dispatchOnScrollStateChanged(int state) {
        // Let the LayoutManager go first; this allows it to bring any properties into
        // a consistent state before the RecyclerView subclass responds.
        if (mLayout != null) {
            mLayout.onScrollStateChanged(state);
        }

        // Let the RecyclerView subclass handle this event next; any LayoutManager property
        // changes will be reflected by this time.
        onScrollStateChanged(state);

        // Listeners go last. All other internal state is consistent by this point.
        if (mScrollListener != null) {
            mScrollListener.onScrollStateChanged(this, state);
        }
        if (mScrollListeners != null) {
            for (int i = mScrollListeners.size() - 1; i >= 0; i--) {
                mScrollListeners.get(i).onScrollStateChanged(this, state);
            }
        }
    }

    /**
     * Returns whether there are pending adapter updates which are not yet applied to the layout.
     * <p>
     * If this method returns <code>true</code>, it means that what user is currently seeing may not
     * reflect them adapter contents (depending on what has changed).
     * You may use this information to defer or cancel some operations.
     * <p>
     * This method returns true if RecyclerView has not yet calculated the first layout after it is
     * attached to the Window or the Adapter has been replaced.
     *
     * @return True if there are some adapter updates which are not yet reflected to layout or false
     * if layout is up to date.
     */
    public boolean hasPendingAdapterUpdates() {
        return !mFirstLayoutComplete || mDataSetHasChangedAfterLayout
                || mAdapterHelper.hasPendingUpdates();
    }

    class ViewFlinger implements Runnable {
        private int mLastFlingX;
        private int mLastFlingY;
        OverScroller mOverScroller;
        Interpolator mInterpolator = sQuinticInterpolator;

        // When set to true, postOnAnimation callbacks are delayed until the run method completes
        private boolean mEatRunOnAnimationRequest = false;

        // Tracks if postAnimationCallback should be re-attached when it is done
        private boolean mReSchedulePostAnimationCallback = false;

        ViewFlinger() {
            mOverScroller = new OverScroller(getContext(), sQuinticInterpolator);
        }

        @Override
        public void run() {
            if (mLayout == null) {
                stop();
                return; // no layout, cannot scroll.
            }

            mReSchedulePostAnimationCallback = false;
            mEatRunOnAnimationRequest = true;

            consumePendingUpdateOperations();

            // TODO(72745539): After reviewing the code, it seems to me we may actually want to
            // update the reference to the OverScroller after onAnimation.  It looks to me like
            // it is possible that a new OverScroller could be created (due to a new Interpolator
            // being used), when the current OverScroller knows it's done after
            // scroller.computeScrollOffset() is called.  If that happens, and we don't update the
            // reference, it seems to me that we could prematurely stop the newly created scroller
            // due to setScrollState(SCROLL_STATE_IDLE) being called below.

            // Keep a local reference so that if it is changed during onAnimation method, it won't
            // cause unexpected behaviors
            final OverScroller scroller = mOverScroller;
            if (scroller.computeScrollOffset()) {
                final int x = scroller.getCurrX();
                final int y = scroller.getCurrY();
                int unconsumedX = x - mLastFlingX;
                int unconsumedY = y - mLastFlingY;
                mLastFlingX = x;
                mLastFlingY = y;
                int consumedX = 0;
                int consumedY = 0;

                // Nested Pre Scroll
                mReusableIntPair[0] = 0;
                mReusableIntPair[1] = 0;
                if (dispatchNestedPreScroll(unconsumedX, unconsumedY, mReusableIntPair, null,
                        TYPE_NON_TOUCH)) {
                    unconsumedX -= mReusableIntPair[0];
                    unconsumedY -= mReusableIntPair[1];
                }

                // Based on movement, we may want to trigger the hiding of existing over scroll
                // glows.
                if (getOverScrollMode() != View.OVER_SCROLL_NEVER) {
                    considerReleasingGlowsOnScroll(unconsumedX, unconsumedY);
                }

                // Local Scroll
                if (mAdapter != null) {
                    mReusableIntPair[0] = 0;
                    mReusableIntPair[1] = 0;
                    scrollStep(unconsumedX, unconsumedY, mReusableIntPair);
                    consumedX = mReusableIntPair[0];
                    consumedY = mReusableIntPair[1];
                    unconsumedX -= consumedX;
                    unconsumedY -= consumedY;

                    // If SmoothScroller exists, this ViewFlinger was started by it, so we must
                    // report back to SmoothScroller.
                    SmoothScroller smoothScroller = mLayout.mSmoothScroller;
                    if (smoothScroller != null && !smoothScroller.isPendingInitialRun()
                            && smoothScroller.isRunning()) {
                        final int adapterSize = mState.getItemCount();
                        if (adapterSize == 0) {
                            smoothScroller.stop();
                        } else if (smoothScroller.getTargetPosition() >= adapterSize) {
                            smoothScroller.setTargetPosition(adapterSize - 1);
                            smoothScroller.onAnimation(consumedX, consumedY);
                        } else {
                            smoothScroller.onAnimation(consumedX, consumedY);
                        }
                    }
                }

                if (!mItemDecorations.isEmpty()) {
                    invalidate();
                }

                // Nested Post Scroll
                mReusableIntPair[0] = 0;
                mReusableIntPair[1] = 0;
                dispatchNestedScroll(consumedX, consumedY, unconsumedX, unconsumedY, null,
                        TYPE_NON_TOUCH, mReusableIntPair);
                unconsumedX -= mReusableIntPair[0];
                unconsumedY -= mReusableIntPair[1];

                if (consumedX != 0 || consumedY != 0) {
                    dispatchOnScrolled(consumedX, consumedY);
                }

                if (!awakenScrollBars()) {
                    invalidate();
                }

                // We are done scrolling if scroller is finished, or for both the x and y dimension,
                // we are done scrolling or we can't scroll further (we know we can't scroll further
                // when we have unconsumed scroll distance).  It's possible that we don't need
                // to also check for scroller.isFinished() at all, but no harm in doing so in case
                // of old bugs in Overscroller.
                boolean scrollerFinishedX = scroller.getCurrX() == scroller.getFinalX();
                boolean scrollerFinishedY = scroller.getCurrY() == scroller.getFinalY();
                final boolean doneScrolling = scroller.isFinished()
                        || ((scrollerFinishedX || unconsumedX != 0)
                        && (scrollerFinishedY || unconsumedY != 0));

                // Get the current smoothScroller. It may have changed by this point and we need to
                // make sure we don't stop scrolling if it has changed and it's pending an initial
                // run.
                SmoothScroller smoothScroller = mLayout.mSmoothScroller;
                boolean smoothScrollerPending =
                        smoothScroller != null && smoothScroller.isPendingInitialRun();

                if (!smoothScrollerPending && doneScrolling) {
                    // If we are done scrolling and the layout's SmoothScroller is not pending,
                    // do the things we do at the end of a scroll and don't postOnAnimation.

                    if (getOverScrollMode() != View.OVER_SCROLL_NEVER) {
                        final int vel = (int) scroller.getCurrVelocity();
                        int velX = unconsumedX < 0 ? -vel : unconsumedX > 0 ? vel : 0;
                        int velY = unconsumedY < 0 ? -vel : unconsumedY > 0 ? vel : 0;
                        absorbGlows(velX, velY);
                    }

                    if (ALLOW_THREAD_GAP_WORK) {
                        mPrefetchRegistry.clearPrefetchPositions();
                    }
                } else {
                    // Otherwise continue the scroll.

                    postOnAnimation();
                    if (mGapWorker != null) {
                        mGapWorker.postFromTraversal(RecyclerView.this, unconsumedX, unconsumedY);
                    }
                }
            }

            SmoothScroller smoothScroller = mLayout.mSmoothScroller;
            // call this after the onAnimation is complete not to have inconsistent callbacks etc.
            if (smoothScroller != null && smoothScroller.isPendingInitialRun()) {
                smoothScroller.onAnimation(0, 0);
            }

            mEatRunOnAnimationRequest = false;
            if (mReSchedulePostAnimationCallback) {
                internalPostOnAnimation();
            } else {
                setScrollState(SCROLL_STATE_IDLE);
                stopNestedScroll(TYPE_NON_TOUCH);
            }
        }

        void postOnAnimation() {
            if (mEatRunOnAnimationRequest) {
                mReSchedulePostAnimationCallback = true;
            } else {
                internalPostOnAnimation();
            }
        }

        private void internalPostOnAnimation() {
            removeCallbacks(this);
            ViewCompat.postOnAnimation(RecyclerView.this, this);
        }

        public void fling(int velocityX, int velocityY) {
            setScrollState(SCROLL_STATE_SETTLING);
            mLastFlingX = mLastFlingY = 0;
            // Because you can't define a custom interpolator for flinging, we should make sure we
            // reset ourselves back to the teh default interpolator in case a different call
            // changed our interpolator.
            if (mInterpolator != sQuinticInterpolator) {
                mInterpolator = sQuinticInterpolator;
                mOverScroller = new OverScroller(getContext(), sQuinticInterpolator);
            }
            mOverScroller.fling(0, 0, velocityX, velocityY,
                    Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE);
            postOnAnimation();
        }

        public void smoothScrollBy(int dx, int dy, int duration,
                @Nullable Interpolator interpolator) {

            // Handle cases where parameter values aren't defined.
            if (duration == UNDEFINED_DURATION) {
                duration = computeScrollDuration(dx, dy, 0, 0);
            }
            if (interpolator == null) {
                interpolator = sQuinticInterpolator;
            }

            // If the Interpolator has changed, create a new OverScroller with the new
            // interpolator.
            if (mInterpolator != interpolator) {
                mInterpolator = interpolator;
                mOverScroller = new OverScroller(getContext(), interpolator);
            }

            // Reset the last fling information.
            mLastFlingX = mLastFlingY = 0;

            // Set to settling state and start scrolling.
            setScrollState(SCROLL_STATE_SETTLING);
            mOverScroller.startScroll(0, 0, dx, dy, duration);

            if (Build.VERSION.SDK_INT < 23) {
                // b/64931938 before API 23, startScroll() does not reset getCurX()/getCurY()
                // to start values, which causes fillRemainingScrollValues() put in obsolete values
                // for LayoutManager.onLayoutChildren().
                mOverScroller.computeScrollOffset();
            }

            postOnAnimation();
        }

        private float distanceInfluenceForSnapDuration(float f) {
            f -= 0.5f; // center the values about 0.
            f *= 0.3f * (float) Math.PI / 2.0f;
            return (float) Math.sin(f);
        }

        private int computeScrollDuration(int dx, int dy, int vx, int vy) {
            final int absDx = Math.abs(dx);
            final int absDy = Math.abs(dy);
            final boolean horizontal = absDx > absDy;
            final int velocity = (int) Math.sqrt(vx * vx + vy * vy);
            final int delta = (int) Math.sqrt(dx * dx + dy * dy);
            final int containerSize = horizontal ? getWidth() : getHeight();
            final int halfContainerSize = containerSize / 2;
            final float distanceRatio = Math.min(1.f, 1.f * delta / containerSize);
            final float distance = halfContainerSize + halfContainerSize
                    * distanceInfluenceForSnapDuration(distanceRatio);

            final int duration;
            if (velocity > 0) {
                duration = 4 * Math.round(1000 * Math.abs(distance / velocity));
            } else {
                float absDelta = (float) (horizontal ? absDx : absDy);
                duration = (int) (((absDelta / containerSize) + 1) * 300);
            }
            return Math.min(duration, MAX_SCROLL_DURATION);
        }

        public void stop() {
            removeCallbacks(this);
            mOverScroller.abortAnimation();
        }

    }

    void repositionShadowingViews() {
        // Fix up shadow views used by change animations
        int count = mChildHelper.getChildCount();
        for (int i = 0; i < count; i++) {
            View view = mChildHelper.getChildAt(i);
            ViewHolder holder = getChildViewHolder(view);
            if (holder != null && holder.mShadowingHolder != null) {
                View shadowingView = holder.mShadowingHolder.itemView;
                int left = view.getLeft();
                int top = view.getTop();
                if (left != shadowingView.getLeft() ||  top != shadowingView.getTop()) {
                    shadowingView.layout(left, top,
                            left + shadowingView.getWidth(),
                            top + shadowingView.getHeight());
                }
            }
        }
    }

    private class RecyclerViewDataObserver extends AdapterDataObserver {
        RecyclerViewDataObserver() {
        }

        @Override
        public void onChanged() {
            assertNotInLayoutOrScroll(null);
            mState.mStructureChanged = true;

            processDataSetCompletelyChanged(true);
            if (!mAdapterHelper.hasPendingUpdates()) {
                requestLayout();
            }
        }

        @Override
        public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
            assertNotInLayoutOrScroll(null);
            if (mAdapterHelper.onItemRangeChanged(positionStart, itemCount, payload)) {
                triggerUpdateProcessor();
            }
        }

        @Override
        public void onItemRangeInserted(int positionStart, int itemCount) {
            assertNotInLayoutOrScroll(null);
            if (mAdapterHelper.onItemRangeInserted(positionStart, itemCount)) {
                triggerUpdateProcessor();
            }
        }

        @Override
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            assertNotInLayoutOrScroll(null);
            if (mAdapterHelper.onItemRangeRemoved(positionStart, itemCount)) {
                triggerUpdateProcessor();
            }
        }

        @Override
        public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
            assertNotInLayoutOrScroll(null);
            if (mAdapterHelper.onItemRangeMoved(fromPosition, toPosition, itemCount)) {
                triggerUpdateProcessor();
            }
        }

        void triggerUpdateProcessor() {
            if (POST_UPDATES_ON_ANIMATION && mHasFixedSize && mIsAttached) {
                ViewCompat.postOnAnimation(RecyclerView.this, mUpdateChildViewsRunnable);
            } else {
                mAdapterUpdateDuringMeasure = true;
                requestLayout();
            }
        }
    }

    /**
     * EdgeEffectFactory lets you customize the over-scroll edge effect for RecyclerViews.
     *
     * @see RecyclerView#setEdgeEffectFactory(EdgeEffectFactory)
     */
    public static class EdgeEffectFactory {

        @Retention(RetentionPolicy.SOURCE)
        @IntDef({DIRECTION_LEFT, DIRECTION_TOP, DIRECTION_RIGHT, DIRECTION_BOTTOM})
        public @interface EdgeDirection {}

        /**
         * Direction constant for the left edge
         */
        public static final int DIRECTION_LEFT = 0;

        /**
         * Direction constant for the top edge
         */
        public static final int DIRECTION_TOP = 1;

        /**
         * Direction constant for the right edge
         */
        public static final int DIRECTION_RIGHT = 2;

        /**
         * Direction constant for the bottom edge
         */
        public static final int DIRECTION_BOTTOM = 3;

        /**
         * Create a new EdgeEffect for the provided direction.
         */
        protected @NonNull EdgeEffect createEdgeEffect(@NonNull RecyclerView view,
                @EdgeDirection int direction) {
            return new EdgeEffect(view.getContext());
        }
    }

    /**
     * RecycledViewPool lets you share Views between multiple RecyclerViews.
     * <p>
     * If you want to recycle views across RecyclerViews, create an instance of RecycledViewPool
     * and use {@link RecyclerView#setRecycledViewPool(RecycledViewPool)}.
     * <p>
     * RecyclerView automatically creates a pool for itself if you don't provide one.
     */
    public static class RecycledViewPool {
        private static final int DEFAULT_MAX_SCRAP = 20;

        /**
         * Tracks both pooled holders, as well as create/bind timing metadata for the given type.
         *
         * Note that this tracks running averages of create/bind time across all RecyclerViews
         * (and, indirectly, Adapters) that use this pool.
         *
         * 1) This enables us to track average create and bind times across multiple adapters. Even
         * though create (and especially bind) may behave differently for different Adapter
         * subclasses, sharing the pool is a strong signal that they'll perform similarly, per type.
         *
         * 2) If {@link #willBindInTime(int, long, long)} returns false for one view, it will return
         * false for all other views of its type for the same deadline. This prevents items
         * constructed by {@link GapWorker} prefetch from being bound to a lower priority prefetch.
         */
        static class ScrapData {
            final ArrayList<ViewHolder> mScrapHeap = new ArrayList<>();
            int mMaxScrap = DEFAULT_MAX_SCRAP;
            long mCreateRunningAverageNs = 0;
            long mBindRunningAverageNs = 0;
        }
        SparseArray<ScrapData> mScrap = new SparseArray<>();

        private int mAttachCount = 0;

        /**
         * Discard all ViewHolders.
         */
        public void clear() {
            for (int i = 0; i < mScrap.size(); i++) {
                ScrapData data = mScrap.valueAt(i);
                data.mScrapHeap.clear();
            }
        }

        /**
         * Sets the maximum number of ViewHolders to hold in the pool before discarding.
         *
         * @param viewType ViewHolder Type
         * @param max Maximum number
         */
        public void setMaxRecycledViews(int viewType, int max) {
            ScrapData scrapData = getScrapDataForType(viewType);
            scrapData.mMaxScrap = max;
            final ArrayList<ViewHolder> scrapHeap = scrapData.mScrapHeap;
            while (scrapHeap.size() > max) {
                scrapHeap.remove(scrapHeap.size() - 1);
            }
        }

        /**
         * Returns the current number of Views held by the RecycledViewPool of the given view type.
         */
        public int getRecycledViewCount(int viewType) {
            return getScrapDataForType(viewType).mScrapHeap.size();
        }

        /**
         * Acquire a ViewHolder of the specified type from the pool, or {@code null} if none are
         * present.
         *
         * @param viewType ViewHolder type.
         * @return ViewHolder of the specified type acquired from the pool, or {@code null} if none
         * are present.
         */
        @Nullable
        public ViewHolder getRecycledView(int viewType) {
            final ScrapData scrapData = mScrap.get(viewType);
            if (scrapData != null && !scrapData.mScrapHeap.isEmpty()) {
                final ArrayList<ViewHolder> scrapHeap = scrapData.mScrapHeap;
                for (int i = scrapHeap.size() - 1; i >= 0; i--) {
                    if (!scrapHeap.get(i).isAttachedToTransitionOverlay()) {
                        return scrapHeap.remove(i);
                    }
                }
            }
            return null;
        }

        /**
         * Total number of ViewHolders held by the pool.
         *
         * @return Number of ViewHolders held by the pool.
         */
        int size() {
            int count = 0;
            for (int i = 0; i < mScrap.size(); i++) {
                ArrayList<ViewHolder> viewHolders = mScrap.valueAt(i).mScrapHeap;
                if (viewHolders != null) {
                    count += viewHolders.size();
                }
            }
            return count;
        }

        /**
         * Add a scrap ViewHolder to the pool.
         * <p>
         * If the pool is already full for that ViewHolder's type, it will be immediately discarded.
         *
         * @param scrap ViewHolder to be added to the pool.
         */
        public void putRecycledView(ViewHolder scrap) {
            final int viewType = scrap.getItemViewType();
            final ArrayList<ViewHolder> scrapHeap = getScrapDataForType(viewType).mScrapHeap;
            if (mScrap.get(viewType).mMaxScrap <= scrapHeap.size()) {
                return;
            }
            if (DEBUG && scrapHeap.contains(scrap)) {
                throw new IllegalArgumentException("this scrap item already exists");
            }
            scrap.resetInternal();
            scrapHeap.add(scrap);
        }

        long runningAverage(long oldAverage, long newValue) {
            if (oldAverage == 0) {
                return newValue;
            }
            return (oldAverage / 4 * 3) + (newValue / 4);
        }

        void factorInCreateTime(int viewType, long createTimeNs) {
            ScrapData scrapData = getScrapDataForType(viewType);
            scrapData.mCreateRunningAverageNs = runningAverage(
                    scrapData.mCreateRunningAverageNs, createTimeNs);
        }

        void factorInBindTime(int viewType, long bindTimeNs) {
            ScrapData scrapData = getScrapDataForType(viewType);
            scrapData.mBindRunningAverageNs = runningAverage(
                    scrapData.mBindRunningAverageNs, bindTimeNs);
        }

        boolean willCreateInTime(int viewType, long approxCurrentNs, long deadlineNs) {
            long expectedDurationNs = getScrapDataForType(viewType).mCreateRunningAverageNs;
            return expectedDurationNs == 0 || (approxCurrentNs + expectedDurationNs < deadlineNs);
        }

        boolean willBindInTime(int viewType, long approxCurrentNs, long deadlineNs) {
            long expectedDurationNs = getScrapDataForType(viewType).mBindRunningAverageNs;
            return expectedDurationNs == 0 || (approxCurrentNs + expectedDurationNs < deadlineNs);
        }

        void attach() {
            mAttachCount++;
        }

        void detach() {
            mAttachCount--;
        }


        /**
         * Detaches the old adapter and attaches the new one.
         * <p>
         * RecycledViewPool will clear its cache if it has only one adapter attached and the new
         * adapter uses a different ViewHolder than the oldAdapter.
         *
         * @param oldAdapter The previous adapter instance. Will be detached.
         * @param newAdapter The new adapter instance. Will be attached.
         * @param compatibleWithPrevious True if both oldAdapter and newAdapter are using the same
         *                               ViewHolder and view types.
         */
        void onAdapterChanged(Adapter oldAdapter, Adapter newAdapter,
                boolean compatibleWithPrevious) {
            if (oldAdapter != null) {
                detach();
            }
            if (!compatibleWithPrevious && mAttachCount == 0) {
                clear();
            }
            if (newAdapter != null) {
                attach();
            }
        }

        private ScrapData getScrapDataForType(int viewType) {
            ScrapData scrapData = mScrap.get(viewType);
            if (scrapData == null) {
                scrapData = new ScrapData();
                mScrap.put(viewType, scrapData);
            }
            return scrapData;
        }
    }

    /**
     * Utility method for finding an internal RecyclerView, if present
     */
    @Nullable
    static RecyclerView findNestedRecyclerView(@NonNull View view) {
        if (!(view instanceof ViewGroup)) {
            return null;
        }
        if (view instanceof RecyclerView) {
            return (RecyclerView) view;
        }
        final ViewGroup parent = (ViewGroup) view;
        final int count = parent.getChildCount();
        for (int i = 0; i < count; i++) {
            final View child = parent.getChildAt(i);
            final RecyclerView descendant = findNestedRecyclerView(child);
            if (descendant != null) {
                return descendant;
            }
        }
        return null;
    }

    /**
     * Utility method for clearing holder's internal RecyclerView, if present
     */
    static void clearNestedRecyclerViewIfNotNested(@NonNull ViewHolder holder) {
        if (holder.mNestedRecyclerView != null) {
            View item = holder.mNestedRecyclerView.get();
            while (item != null) {
                if (item == holder.itemView) {
                    return; // match found, don't need to clear
                }

                ViewParent parent = item.getParent();
                if (parent instanceof View) {
                    item = (View) parent;
                } else {
                    item = null;
                }
            }
            holder.mNestedRecyclerView = null; // not nested
        }
    }

    /**
     * Time base for deadline-aware work scheduling. Overridable for testing.
     *
     * Will return 0 to avoid cost of System.nanoTime where deadline-aware work scheduling
     * isn't relevant.
     */
    long getNanoTime() {
        if (ALLOW_THREAD_GAP_WORK) {
            return System.nanoTime();
        } else {
            return 0;
        }
    }

    /**
     * A Recycler is responsible for managing scrapped or detached item views for reuse.
     *
     * <p>A "scrapped" view is a view that is still attached to its parent RecyclerView but
     * that has been marked for removal or reuse.</p>
     *
     * <p>Typical use of a Recycler by a {@link LayoutManager} will be to obtain views for
     * an adapter's data set representing the data at a given position or item ID.
     * If the view to be reused is considered "dirty" the adapter will be asked to rebind it.
     * If not, the view can be quickly reused by the LayoutManager with no further work.
     * Clean views that have not {@link android.view.View#isLayoutRequested() requested layout}
     * may be repositioned by a LayoutManager without remeasurement.</p>
     */
    public final class Recycler {
        final ArrayList<ViewHolder> mAttachedScrap = new ArrayList<>();
        ArrayList<ViewHolder> mChangedScrap = null;

        final ArrayList<ViewHolder> mCachedViews = new ArrayList<ViewHolder>();

        private final List<ViewHolder>
                mUnmodifiableAttachedScrap = Collections.unmodifiableList(mAttachedScrap);

        private int mRequestedCacheMax = DEFAULT_CACHE_SIZE;
        int mViewCacheMax = DEFAULT_CACHE_SIZE;

        RecycledViewPool mRecyclerPool;

        private ViewCacheExtension mViewCacheExtension;

        static final int DEFAULT_CACHE_SIZE = 2;

        /**
         * Clear scrap views out of this recycler. Detached views contained within a
         * recycled view pool will remain.
         */
        public void clear() {
            mAttachedScrap.clear();
            recycleAndClearCachedViews();
        }

        /**
         * Set the maximum number of detached, valid views we should retain for later use.
         *
         * @param viewCount Number of views to keep before sending views to the shared pool
         */
        public void setViewCacheSize(int viewCount) {
            mRequestedCacheMax = viewCount;
            updateViewCacheSize();
        }

        void updateViewCacheSize() {
            int extraCache = mLayout != null ? mLayout.mPrefetchMaxCountObserved : 0;
            mViewCacheMax = mRequestedCacheMax + extraCache;

            // first, try the views that can be recycled
            for (int i = mCachedViews.size() - 1;
                    i >= 0 && mCachedViews.size() > mViewCacheMax; i--) {
                recycleCachedViewAt(i);
            }
        }

        /**
         * Returns an unmodifiable list of ViewHolders that are currently in the scrap list.
         *
         * @return List of ViewHolders in the scrap list.
         */
        @NonNull
        public List<ViewHolder> getScrapList() {
            return mUnmodifiableAttachedScrap;
        }

        /**
         * Helper method for getViewForPosition.
         * <p>
         * Checks whether a given view holder can be used for the provided position.
         *
         * @param holder ViewHolder
         * @return true if ViewHolder matches the provided position, false otherwise
         */
        boolean validateViewHolderForOffsetPosition(ViewHolder holder) {
            // if it is a removed holder, nothing to verify since we cannot ask adapter anymore
            // if it is not removed, verify the type and id.
            if (holder.isRemoved()) {
                if (DEBUG && !mState.isPreLayout()) {
                    throw new IllegalStateException("should not receive a removed view unless it"
                            + " is pre layout" + exceptionLabel());
                }
                return mState.isPreLayout();
            }
            if (holder.mPosition < 0 || holder.mPosition >= mAdapter.getItemCount()) {
                throw new IndexOutOfBoundsException("Inconsistency detected. Invalid view holder "
                        + "adapter position" + holder + exceptionLabel());
            }
            if (!mState.isPreLayout()) {
                // don't check type if it is pre-layout.
                final int type = mAdapter.getItemViewType(holder.mPosition);
                if (type != holder.getItemViewType()) {
                    return false;
                }
            }
            if (mAdapter.hasStableIds()) {
                return holder.getItemId() == mAdapter.getItemId(holder.mPosition);
            }
            return true;
        }

        /**
         * Attempts to bind view, and account for relevant timing information. If
         * deadlineNs != FOREVER_NS, this method may fail to bind, and return false.
         *
         * @param holder Holder to be bound.
         * @param offsetPosition Position of item to be bound.
         * @param position Pre-layout position of item to be bound.
         * @param deadlineNs Time, relative to getNanoTime(), by which bind/create work should
         *                   complete. If FOREVER_NS is passed, this method will not fail to
         *                   bind the holder.
         * @return
         */
        private boolean tryBindViewHolderByDeadline(@NonNull ViewHolder holder, int offsetPosition,
                int position, long deadlineNs) {
            holder.mOwnerRecyclerView = RecyclerView.this;
            final int viewType = holder.getItemViewType();
            long startBindNs = getNanoTime();
            if (deadlineNs != FOREVER_NS
                    && !mRecyclerPool.willBindInTime(viewType, startBindNs, deadlineNs)) {
                // abort - we have a deadline we can't meet
                return false;
            }
            mAdapter.bindViewHolder(holder, offsetPosition);
            long endBindNs = getNanoTime();
            mRecyclerPool.factorInBindTime(holder.getItemViewType(), endBindNs - startBindNs);
            attachAccessibilityDelegateOnBind(holder);
            if (mState.isPreLayout()) {
                holder.mPreLayoutPosition = position;
            }
            return true;
        }

        /**
         * Binds the given View to the position. The View can be a View previously retrieved via
         * {@link #getViewForPosition(int)} or created by
         * {@link Adapter#onCreateViewHolder(ViewGroup, int)}.
         * <p>
         * Generally, a LayoutManager should acquire its views via {@link #getViewForPosition(int)}
         * and let the RecyclerView handle caching. This is a helper method for LayoutManager who
         * wants to handle its own recycling logic.
         * <p>
         * Note that, {@link #getViewForPosition(int)} already binds the View to the position so
         * you don't need to call this method unless you want to bind this View to another position.
         *
         * @param view The view to update.
         * @param position The position of the item to bind to this View.
         */
        public void bindViewToPosition(@NonNull View view, int position) {
            ViewHolder holder = getChildViewHolderInt(view);
            if (holder == null) {
                throw new IllegalArgumentException("The view does not have a ViewHolder. You cannot"
                        + " pass arbitrary views to this method, they should be created by the "
                        + "Adapter" + exceptionLabel());
            }
            final int offsetPosition = mAdapterHelper.findPositionOffset(position);
            if (offsetPosition < 0 || offsetPosition >= mAdapter.getItemCount()) {
                throw new IndexOutOfBoundsException("Inconsistency detected. Invalid item "
                        + "position " + position + "(offset:" + offsetPosition + ")."
                        + "state:" + mState.getItemCount() + exceptionLabel());
            }
            tryBindViewHolderByDeadline(holder, offsetPosition, position, FOREVER_NS);

            final ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
            final LayoutParams rvLayoutParams;
            if (lp == null) {
                rvLayoutParams = (LayoutParams) generateDefaultLayoutParams();
                holder.itemView.setLayoutParams(rvLayoutParams);
            } else if (!checkLayoutParams(lp)) {
                rvLayoutParams = (LayoutParams) generateLayoutParams(lp);
                holder.itemView.setLayoutParams(rvLayoutParams);
            } else {
                rvLayoutParams = (LayoutParams) lp;
            }

            rvLayoutParams.mInsetsDirty = true;
            rvLayoutParams.mViewHolder = holder;
            rvLayoutParams.mPendingInvalidate = holder.itemView.getParent() == null;
        }

        /**
         * RecyclerView provides artificial position range (item count) in pre-layout state and
         * automatically maps these positions to {@link Adapter} positions when
         * {@link #getViewForPosition(int)} or {@link #bindViewToPosition(View, int)} is called.
         * <p>
         * Usually, LayoutManager does not need to worry about this. However, in some cases, your
         * LayoutManager may need to call some custom component with item positions in which
         * case you need the actual adapter position instead of the pre layout position. You
         * can use this method to convert a pre-layout position to adapter (post layout) position.
         * <p>
         * Note that if the provided position belongs to a deleted ViewHolder, this method will
         * return -1.
         * <p>
         * Calling this method in post-layout state returns the same value back.
         *
         * @param position The pre-layout position to convert. Must be greater or equal to 0 and
         *                 less than {@link State#getItemCount()}.
         */
        public int convertPreLayoutPositionToPostLayout(int position) {
            if (position < 0 || position >= mState.getItemCount()) {
                throw new IndexOutOfBoundsException("invalid position " + position + ". State "
                        + "item count is " + mState.getItemCount() + exceptionLabel());
            }
            if (!mState.isPreLayout()) {
                return position;
            }
            return mAdapterHelper.findPositionOffset(position);
        }

        /**
         * Obtain a view initialized for the given position.
         *
         * This method should be used by {@link LayoutManager} implementations to obtain
         * views to represent data from an {@link Adapter}.
         * <p>
         * The Recycler may reuse a scrap or detached view from a shared pool if one is
         * available for the correct view type. If the adapter has not indicated that the
         * data at the given position has changed, the Recycler will attempt to hand back
         * a scrap view that was previously initialized for that data without rebinding.
         *
         * @param position Position to obtain a view for
         * @return A view representing the data at <code>position</code> from <code>adapter</code>
         */
        @NonNull
        public View getViewForPosition(int position) {
            return getViewForPosition(position, false);
        }

        View getViewForPosition(int position, boolean dryRun) {
            return tryGetViewHolderForPositionByDeadline(position, dryRun, FOREVER_NS).itemView;
        }

        /**
         * Attempts to get the ViewHolder for the given position, either from the Recycler scrap,
         * cache, the RecycledViewPool, or creating it directly.
         * <p>
         * If a deadlineNs other than {@link #FOREVER_NS} is passed, this method early return
         * rather than constructing or binding a ViewHolder if it doesn't think it has time.
         * If a ViewHolder must be constructed and not enough time remains, null is returned. If a
         * ViewHolder is aquired and must be bound but not enough time remains, an unbound holder is
         * returned. Use {@link ViewHolder#isBound()} on the returned object to check for this.
         *
         * @param position Position of ViewHolder to be returned.
         * @param dryRun True if the ViewHolder should not be removed from scrap/cache/
         * @param deadlineNs Time, relative to getNanoTime(), by which bind/create work should
         *                   complete. If FOREVER_NS is passed, this method will not fail to
         *                   create/bind the holder if needed.
         *
         * @return ViewHolder for requested position
         */
        @Nullable
        ViewHolder tryGetViewHolderForPositionByDeadline(int position,
                boolean dryRun, long deadlineNs) {
            if (position < 0 || position >= mState.getItemCount()) {
                throw new IndexOutOfBoundsException("Invalid item position " + position
                        + "(" + position + "). Item count:" + mState.getItemCount()
                        + exceptionLabel());
            }
            boolean fromScrapOrHiddenOrCache = false;
            ViewHolder holder = null;
            // 0) If there is a changed scrap, try to find from there
            if (mState.isPreLayout()) {
                holder = getChangedScrapViewForPosition(position);
                fromScrapOrHiddenOrCache = holder != null;
            }
            // 1) Find by position from scrap/hidden list/cache
            if (holder == null) {
                holder = getScrapOrHiddenOrCachedHolderForPosition(position, dryRun);
                if (holder != null) {
                    if (!validateViewHolderForOffsetPosition(holder)) {
                        // recycle holder (and unscrap if relevant) since it can't be used
                        if (!dryRun) {
                            // we would like to recycle this but need to make sure it is not used by
                            // animation logic etc.
                            holder.addFlags(ViewHolder.FLAG_INVALID);
                            if (holder.isScrap()) {
                                removeDetachedView(holder.itemView, false);
                                holder.unScrap();
                            } else if (holder.wasReturnedFromScrap()) {
                                holder.clearReturnedFromScrapFlag();
                            }
                            recycleViewHolderInternal(holder);
                        }
                        holder = null;
                    } else {
                        fromScrapOrHiddenOrCache = true;
                    }
                }
            }
            if (holder == null) {
                final int offsetPosition = mAdapterHelper.findPositionOffset(position);
                if (offsetPosition < 0 || offsetPosition >= mAdapter.getItemCount()) {
                    throw new IndexOutOfBoundsException("Inconsistency detected. Invalid item "
                            + "position " + position + "(offset:" + offsetPosition + ")."
                            + "state:" + mState.getItemCount() + exceptionLabel());
                }

                final int type = mAdapter.getItemViewType(offsetPosition);
                // 2) Find from scrap/cache via stable ids, if exists
                if (mAdapter.hasStableIds()) {
                    holder = getScrapOrCachedViewForId(mAdapter.getItemId(offsetPosition),
                            type, dryRun);
                    if (holder != null) {
                        // update position
                        holder.mPosition = offsetPosition;
                        fromScrapOrHiddenOrCache = true;
                    }
                }
                if (holder == null && mViewCacheExtension != null) {
                    // We are NOT sending the offsetPosition because LayoutManager does not
                    // know it.
                    final View view = mViewCacheExtension
                            .getViewForPositionAndType(this, position, type);
                    if (view != null) {
                        holder = getChildViewHolder(view);
                        if (holder == null) {
                            throw new IllegalArgumentException("getViewForPositionAndType returned"
                                    + " a view which does not have a ViewHolder"
                                    + exceptionLabel());
                        } else if (holder.shouldIgnore()) {
                            throw new IllegalArgumentException("getViewForPositionAndType returned"
                                    + " a view that is ignored. You must call stopIgnoring before"
                                    + " returning this view." + exceptionLabel());
                        }
                    }
                }
                if (holder == null) { // fallback to pool
                    if (DEBUG) {
                        Log.d(TAG, "tryGetViewHolderForPositionByDeadline("
                                + position + ") fetching from shared pool");
                    }
                    holder = getRecycledViewPool().getRecycledView(type);
                    if (holder != null) {
                        holder.resetInternal();
                        if (FORCE_INVALIDATE_DISPLAY_LIST) {
                            invalidateDisplayListInt(holder);
                        }
                    }
                }
                if (holder == null) {
                    long start = getNanoTime();
                    if (deadlineNs != FOREVER_NS
                            && !mRecyclerPool.willCreateInTime(type, start, deadlineNs)) {
                        // abort - we have a deadline we can't meet
                        return null;
                    }
                    holder = mAdapter.createViewHolder(RecyclerView.this, type);
                    if (ALLOW_THREAD_GAP_WORK) {
                        // only bother finding nested RV if prefetching
                        RecyclerView innerView = findNestedRecyclerView(holder.itemView);
                        if (innerView != null) {
                            holder.mNestedRecyclerView = new WeakReference<>(innerView);
                        }
                    }

                    long end = getNanoTime();
                    mRecyclerPool.factorInCreateTime(type, end - start);
                    if (DEBUG) {
                        Log.d(TAG, "tryGetViewHolderForPositionByDeadline created new ViewHolder");
                    }
                }
            }

            // This is very ugly but the only place we can grab this information
            // before the View is rebound and returned to the LayoutManager for post layout ops.
            // We don't need this in pre-layout since the VH is not updated by the LM.
            if (fromScrapOrHiddenOrCache && !mState.isPreLayout() && holder
                    .hasAnyOfTheFlags(ViewHolder.FLAG_BOUNCED_FROM_HIDDEN_LIST)) {
                holder.setFlags(0, ViewHolder.FLAG_BOUNCED_FROM_HIDDEN_LIST);
                if (mState.mRunSimpleAnimations) {
                    int changeFlags = ItemAnimator
                            .buildAdapterChangeFlagsForAnimations(holder);
                    changeFlags |= ItemAnimator.FLAG_APPEARED_IN_PRE_LAYOUT;
                    final ItemHolderInfo info = mItemAnimator.recordPreLayoutInformation(mState,
                            holder, changeFlags, holder.getUnmodifiedPayloads());
                    recordAnimationInfoIfBouncedHiddenView(holder, info);
                }
            }

            boolean bound = false;
            if (mState.isPreLayout() && holder.isBound()) {
                // do not update unless we absolutely have to.
                holder.mPreLayoutPosition = position;
            } else if (!holder.isBound() || holder.needsUpdate() || holder.isInvalid()) {
                if (DEBUG && holder.isRemoved()) {
                    throw new IllegalStateException("Removed holder should be bound and it should"
                            + " come here only in pre-layout. Holder: " + holder
                            + exceptionLabel());
                }
                final int offsetPosition = mAdapterHelper.findPositionOffset(position);
                bound = tryBindViewHolderByDeadline(holder, offsetPosition, position, deadlineNs);
            }

            final ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
            final LayoutParams rvLayoutParams;
            if (lp == null) {
                rvLayoutParams = (LayoutParams) generateDefaultLayoutParams();
                holder.itemView.setLayoutParams(rvLayoutParams);
            } else if (!checkLayoutParams(lp)) {
                rvLayoutParams = (LayoutParams) generateLayoutParams(lp);
                holder.itemView.setLayoutParams(rvLayoutParams);
            } else {
                rvLayoutParams = (LayoutParams) lp;
            }
            rvLayoutParams.mViewHolder = holder;
            rvLayoutParams.mPendingInvalidate = fromScrapOrHiddenOrCache && bound;
            return holder;
        }

        private void attachAccessibilityDelegateOnBind(ViewHolder holder) {
            if (isAccessibilityEnabled()) {
                final View itemView = holder.itemView;
                if (ViewCompat.getImportantForAccessibility(itemView)
                        == ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
                    ViewCompat.setImportantForAccessibility(itemView,
                            ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
                }
                AccessibilityDelegateCompat delegate =
                        ViewCompat.getAccessibilityDelegate(itemView);
                if (delegate == null
                        || delegate.getClass().equals(AccessibilityDelegateCompat.class)) {
                    holder.addFlags(ViewHolder.FLAG_SET_A11Y_ITEM_DELEGATE);
                    ViewCompat.setAccessibilityDelegate(itemView,
                            mAccessibilityDelegate.getItemDelegate());
                }
            }
        }

        private void invalidateDisplayListInt(ViewHolder holder) {
            if (holder.itemView instanceof ViewGroup) {
                invalidateDisplayListInt((ViewGroup) holder.itemView, false);
            }
        }

        private void invalidateDisplayListInt(ViewGroup viewGroup, boolean invalidateThis) {
            for (int i = viewGroup.getChildCount() - 1; i >= 0; i--) {
                final View view = viewGroup.getChildAt(i);
                if (view instanceof ViewGroup) {
                    invalidateDisplayListInt((ViewGroup) view, true);
                }
            }
            if (!invalidateThis) {
                return;
            }
            // we need to force it to become invisible
            if (viewGroup.getVisibility() == View.INVISIBLE) {
                viewGroup.setVisibility(View.VISIBLE);
                viewGroup.setVisibility(View.INVISIBLE);
            } else {
                final int visibility = viewGroup.getVisibility();
                viewGroup.setVisibility(View.INVISIBLE);
                viewGroup.setVisibility(visibility);
            }
        }

        /**
         * Recycle a detached view. The specified view will be added to a pool of views
         * for later rebinding and reuse.
         *
         * <p>A view must be fully detached (removed from parent) before it may be recycled. If the
         * View is scrapped, it will be removed from scrap list.</p>
         *
         * @param view Removed view for recycling
         * @see LayoutManager#removeAndRecycleView(View, Recycler)
         */
        public void recycleView(@NonNull View view) {
            // This public recycle method tries to make view recycle-able since layout manager
            // intended to recycle this view (e.g. even if it is in scrap or change cache)
            ViewHolder holder = getChildViewHolderInt(view);
            if (holder.isTmpDetached()) {
                removeDetachedView(view, false);
            }
            if (holder.isScrap()) {
                holder.unScrap();
            } else if (holder.wasReturnedFromScrap()) {
                holder.clearReturnedFromScrapFlag();
            }
            recycleViewHolderInternal(holder);
            // In most cases we dont need call endAnimation() because when view is detached,
            // ViewPropertyAnimation will end. But if the animation is based on ObjectAnimator or
            // if the ItemAnimator uses "pending runnable" and the ViewPropertyAnimation has not
            // started yet, the ItemAnimatior on the view may not be cleared.
            // In b/73552923, the View is removed by scroll pass while it's waiting in
            // the "pending moving" list of DefaultItemAnimator and DefaultItemAnimator later in
            // a post runnable, incorrectly performs postDelayed() on the detached view.
            // To fix the issue, we issue endAnimation() here to make sure animation of this view
            // finishes.
            //
            // Note the order: we must call endAnimation() after recycleViewHolderInternal()
            // to avoid recycle twice. If ViewHolder isRecyclable is false,
            // recycleViewHolderInternal() will not recycle it, endAnimation() will reset
            // isRecyclable flag and recycle the view.
            if (mItemAnimator != null && !holder.isRecyclable()) {
                mItemAnimator.endAnimation(holder);
            }
        }

        void recycleAndClearCachedViews() {
            final int count = mCachedViews.size();
            for (int i = count - 1; i >= 0; i--) {
                recycleCachedViewAt(i);
            }
            mCachedViews.clear();
            if (ALLOW_THREAD_GAP_WORK) {
                mPrefetchRegistry.clearPrefetchPositions();
            }
        }

        /**
         * Recycles a cached view and removes the view from the list. Views are added to cache
         * if and only if they are recyclable, so this method does not check it again.
         * <p>
         * A small exception to this rule is when the view does not have an animator reference
         * but transient state is true (due to animations created outside ItemAnimator). In that
         * case, adapter may choose to recycle it. From RecyclerView's perspective, the view is
         * still recyclable since Adapter wants to do so.
         *
         * @param cachedViewIndex The index of the view in cached views list
         */
        void recycleCachedViewAt(int cachedViewIndex) {
            if (DEBUG) {
                Log.d(TAG, "Recycling cached view at index " + cachedViewIndex);
            }
            ViewHolder viewHolder = mCachedViews.get(cachedViewIndex);
            if (DEBUG) {
                Log.d(TAG, "CachedViewHolder to be recycled: " + viewHolder);
            }
            addViewHolderToRecycledViewPool(viewHolder, true);
            mCachedViews.remove(cachedViewIndex);
        }

        /**
         * internal implementation checks if view is scrapped or attached and throws an exception
         * if so.
         * Public version un-scraps before calling recycle.
         */
        void recycleViewHolderInternal(ViewHolder holder) {
            if (holder.isScrap() || holder.itemView.getParent() != null) {
                throw new IllegalArgumentException(
                        "Scrapped or attached views may not be recycled. isScrap:"
                                + holder.isScrap() + " isAttached:"
                                + (holder.itemView.getParent() != null) + exceptionLabel());
            }

            if (holder.isTmpDetached()) {
                throw new IllegalArgumentException("Tmp detached view should be removed "
                        + "from RecyclerView before it can be recycled: " + holder
                        + exceptionLabel());
            }

            if (holder.shouldIgnore()) {
                throw new IllegalArgumentException("Trying to recycle an ignored view holder. You"
                        + " should first call stopIgnoringView(view) before calling recycle."
                        + exceptionLabel());
            }
            final boolean transientStatePreventsRecycling = holder
                    .doesTransientStatePreventRecycling();
            @SuppressWarnings("unchecked")
            final boolean forceRecycle = mAdapter != null
                    && transientStatePreventsRecycling
                    && mAdapter.onFailedToRecycleView(holder);
            boolean cached = false;
            boolean recycled = false;
            if (DEBUG && mCachedViews.contains(holder)) {
                throw new IllegalArgumentException("cached view received recycle internal? "
                        + holder + exceptionLabel());
            }
            if (forceRecycle || holder.isRecyclable()) {
                if (mViewCacheMax > 0
                        && !holder.hasAnyOfTheFlags(ViewHolder.FLAG_INVALID
                        | ViewHolder.FLAG_REMOVED
                        | ViewHolder.FLAG_UPDATE
                        | ViewHolder.FLAG_ADAPTER_POSITION_UNKNOWN)) {
                    // Retire oldest cached view
                    int cachedViewSize = mCachedViews.size();
                    if (cachedViewSize >= mViewCacheMax && cachedViewSize > 0) {
                        recycleCachedViewAt(0);
                        cachedViewSize--;
                    }

                    int targetCacheIndex = cachedViewSize;
                    if (ALLOW_THREAD_GAP_WORK
                            && cachedViewSize > 0
                            && !mPrefetchRegistry.lastPrefetchIncludedPosition(holder.mPosition)) {
                        // when adding the view, skip past most recently prefetched views
                        int cacheIndex = cachedViewSize - 1;
                        while (cacheIndex >= 0) {
                            int cachedPos = mCachedViews.get(cacheIndex).mPosition;
                            if (!mPrefetchRegistry.lastPrefetchIncludedPosition(cachedPos)) {
                                break;
                            }
                            cacheIndex--;
                        }
                        targetCacheIndex = cacheIndex + 1;
                    }
                    mCachedViews.add(targetCacheIndex, holder);
                    cached = true;
                }
                if (!cached) {
                    addViewHolderToRecycledViewPool(holder, true);
                    recycled = true;
                }
            } else {
                // NOTE: A view can fail to be recycled when it is scrolled off while an animation
                // runs. In this case, the item is eventually recycled by
                // ItemAnimatorRestoreListener#onAnimationFinished.

                // TODO: consider cancelling an animation when an item is removed scrollBy,
                // to return it to the pool faster
                if (DEBUG) {
                    Log.d(TAG, "trying to recycle a non-recycleable holder. Hopefully, it will "
                            + "re-visit here. We are still removing it from animation lists"
                            + exceptionLabel());
                }
            }
            // even if the holder is not removed, we still call this method so that it is removed
            // from view holder lists.
            mViewInfoStore.removeViewHolder(holder);
            if (!cached && !recycled && transientStatePreventsRecycling) {
                holder.mOwnerRecyclerView = null;
            }
        }

        /**
         * Prepares the ViewHolder to be removed/recycled, and inserts it into the RecycledViewPool.
         *
         * Pass false to dispatchRecycled for views that have not been bound.
         *
         * @param holder Holder to be added to the pool.
         * @param dispatchRecycled True to dispatch View recycled callbacks.
         */
        void addViewHolderToRecycledViewPool(@NonNull ViewHolder holder, boolean dispatchRecycled) {
            clearNestedRecyclerViewIfNotNested(holder);
            if (holder.hasAnyOfTheFlags(ViewHolder.FLAG_SET_A11Y_ITEM_DELEGATE)) {
                holder.setFlags(0, ViewHolder.FLAG_SET_A11Y_ITEM_DELEGATE);
                ViewCompat.setAccessibilityDelegate(holder.itemView, null);
            }
            if (dispatchRecycled) {
                dispatchViewRecycled(holder);
            }
            holder.mOwnerRecyclerView = null;
            getRecycledViewPool().putRecycledView(holder);
        }

        /**
         * Used as a fast path for unscrapping and recycling a view during a bulk operation.
         * The caller must call {@link #clearScrap()} when it's done to update the recycler's
         * internal bookkeeping.
         */
        void quickRecycleScrapView(View view) {
            final ViewHolder holder = getChildViewHolderInt(view);
            holder.mScrapContainer = null;
            holder.mInChangeScrap = false;
            holder.clearReturnedFromScrapFlag();
            recycleViewHolderInternal(holder);
        }

        /**
         * Mark an attached view as scrap.
         *
         * <p>"Scrap" views are still attached to their parent RecyclerView but are eligible
         * for rebinding and reuse. Requests for a view for a given position may return a
         * reused or rebound scrap view instance.</p>
         *
         * @param view View to scrap
         */
        void scrapView(View view) {
            final ViewHolder holder = getChildViewHolderInt(view);
            if (holder.hasAnyOfTheFlags(ViewHolder.FLAG_REMOVED | ViewHolder.FLAG_INVALID)
                    || !holder.isUpdated() || canReuseUpdatedViewHolder(holder)) {
                if (holder.isInvalid() && !holder.isRemoved() && !mAdapter.hasStableIds()) {
                    throw new IllegalArgumentException("Called scrap view with an invalid view."
                            + " Invalid views cannot be reused from scrap, they should rebound from"
                            + " recycler pool." + exceptionLabel());
                }
                holder.setScrapContainer(this, false);
                mAttachedScrap.add(holder);
            } else {
                if (mChangedScrap == null) {
                    mChangedScrap = new ArrayList<ViewHolder>();
                }
                holder.setScrapContainer(this, true);
                mChangedScrap.add(holder);
            }
        }

        /**
         * Remove a previously scrapped view from the pool of eligible scrap.
         *
         * <p>This view will no longer be eligible for reuse until re-scrapped or
         * until it is explicitly removed and recycled.</p>
         */
        void unscrapView(ViewHolder holder) {
            if (holder.mInChangeScrap) {
                mChangedScrap.remove(holder);
            } else {
                mAttachedScrap.remove(holder);
            }
            holder.mScrapContainer = null;
            holder.mInChangeScrap = false;
            holder.clearReturnedFromScrapFlag();
        }

        int getScrapCount() {
            return mAttachedScrap.size();
        }

        View getScrapViewAt(int index) {
            return mAttachedScrap.get(index).itemView;
        }

        void clearScrap() {
            mAttachedScrap.clear();
            if (mChangedScrap != null) {
                mChangedScrap.clear();
            }
        }

        ViewHolder getChangedScrapViewForPosition(int position) {
            // If pre-layout, check the changed scrap for an exact match.
            final int changedScrapSize;
            if (mChangedScrap == null || (changedScrapSize = mChangedScrap.size()) == 0) {
                return null;
            }
            // find by position
            for (int i = 0; i < changedScrapSize; i++) {
                final ViewHolder holder = mChangedScrap.get(i);
                if (!holder.wasReturnedFromScrap() && holder.getLayoutPosition() == position) {
                    holder.addFlags(ViewHolder.FLAG_RETURNED_FROM_SCRAP);
                    return holder;
                }
            }
            // find by id
            if (mAdapter.hasStableIds()) {
                final int offsetPosition = mAdapterHelper.findPositionOffset(position);
                if (offsetPosition > 0 && offsetPosition < mAdapter.getItemCount()) {
                    final long id = mAdapter.getItemId(offsetPosition);
                    for (int i = 0; i < changedScrapSize; i++) {
                        final ViewHolder holder = mChangedScrap.get(i);
                        if (!holder.wasReturnedFromScrap() && holder.getItemId() == id) {
                            holder.addFlags(ViewHolder.FLAG_RETURNED_FROM_SCRAP);
                            return holder;
                        }
                    }
                }
            }
            return null;
        }

        /**
         * Returns a view for the position either from attach scrap, hidden children, or cache.
         *
         * @param position Item position
         * @param dryRun  Does a dry run, finds the ViewHolder but does not remove
         * @return a ViewHolder that can be re-used for this position.
         */
        ViewHolder getScrapOrHiddenOrCachedHolderForPosition(int position, boolean dryRun) {
            final int scrapCount = mAttachedScrap.size();

            // Try first for an exact, non-invalid match from scrap.
            for (int i = 0; i < scrapCount; i++) {
                final ViewHolder holder = mAttachedScrap.get(i);
                if (!holder.wasReturnedFromScrap() && holder.getLayoutPosition() == position
                        && !holder.isInvalid() && (mState.mInPreLayout || !holder.isRemoved())) {
                    holder.addFlags(ViewHolder.FLAG_RETURNED_FROM_SCRAP);
                    return holder;
                }
            }

            if (!dryRun) {
                View view = mChildHelper.findHiddenNonRemovedView(position);
                if (view != null) {
                    // This View is good to be used. We just need to unhide, detach and move to the
                    // scrap list.
                    final ViewHolder vh = getChildViewHolderInt(view);
                    mChildHelper.unhide(view);
                    int layoutIndex = mChildHelper.indexOfChild(view);
                    if (layoutIndex == RecyclerView.NO_POSITION) {
                        throw new IllegalStateException("layout index should not be -1 after "
                                + "unhiding a view:" + vh + exceptionLabel());
                    }
                    mChildHelper.detachViewFromParent(layoutIndex);
                    scrapView(view);
                    vh.addFlags(ViewHolder.FLAG_RETURNED_FROM_SCRAP
                            | ViewHolder.FLAG_BOUNCED_FROM_HIDDEN_LIST);
                    return vh;
                }
            }

            // Search in our first-level recycled view cache.
            final int cacheSize = mCachedViews.size();
            for (int i = 0; i < cacheSize; i++) {
                final ViewHolder holder = mCachedViews.get(i);
                // invalid view holders may be in cache if adapter has stable ids as they can be
                // retrieved via getScrapOrCachedViewForId
                if (!holder.isInvalid() && holder.getLayoutPosition() == position
                        && !holder.isAttachedToTransitionOverlay()) {
                    if (!dryRun) {
                        mCachedViews.remove(i);
                    }
                    if (DEBUG) {
                        Log.d(TAG, "getScrapOrHiddenOrCachedHolderForPosition(" + position
                                + ") found match in cache: " + holder);
                    }
                    return holder;
                }
            }
            return null;
        }

        ViewHolder getScrapOrCachedViewForId(long id, int type, boolean dryRun) {
            // Look in our attached views first
            final int count = mAttachedScrap.size();
            for (int i = count - 1; i >= 0; i--) {
                final ViewHolder holder = mAttachedScrap.get(i);
                if (holder.getItemId() == id && !holder.wasReturnedFromScrap()) {
                    if (type == holder.getItemViewType()) {
                        holder.addFlags(ViewHolder.FLAG_RETURNED_FROM_SCRAP);
                        if (holder.isRemoved()) {
                            // this might be valid in two cases:
                            // > item is removed but we are in pre-layout pass
                            // >> do nothing. return as is. make sure we don't rebind
                            // > item is removed then added to another position and we are in
                            // post layout.
                            // >> remove removed and invalid flags, add update flag to rebind
                            // because item was invisible to us and we don't know what happened in
                            // between.
                            if (!mState.isPreLayout()) {
                                holder.setFlags(ViewHolder.FLAG_UPDATE, ViewHolder.FLAG_UPDATE
                                        | ViewHolder.FLAG_INVALID | ViewHolder.FLAG_REMOVED);
                            }
                        }
                        return holder;
                    } else if (!dryRun) {
                        // if we are running animations, it is actually better to keep it in scrap
                        // but this would force layout manager to lay it out which would be bad.
                        // Recycle this scrap. Type mismatch.
                        mAttachedScrap.remove(i);
                        removeDetachedView(holder.itemView, false);
                        quickRecycleScrapView(holder.itemView);
                    }
                }
            }

            // Search the first-level cache
            final int cacheSize = mCachedViews.size();
            for (int i = cacheSize - 1; i >= 0; i--) {
                final ViewHolder holder = mCachedViews.get(i);
                if (holder.getItemId() == id && !holder.isAttachedToTransitionOverlay()) {
                    if (type == holder.getItemViewType()) {
                        if (!dryRun) {
                            mCachedViews.remove(i);
                        }
                        return holder;
                    } else if (!dryRun) {
                        recycleCachedViewAt(i);
                        return null;
                    }
                }
            }
            return null;
        }

        void dispatchViewRecycled(@NonNull ViewHolder holder) {
            if (mRecyclerListener != null) {
                mRecyclerListener.onViewRecycled(holder);
            }
            if (mAdapter != null) {
                mAdapter.onViewRecycled(holder);
            }
            if (mState != null) {
                mViewInfoStore.removeViewHolder(holder);
            }
            if (DEBUG) Log.d(TAG, "dispatchViewRecycled: " + holder);
        }

        void onAdapterChanged(Adapter oldAdapter, Adapter newAdapter,
                boolean compatibleWithPrevious) {
            clear();
            getRecycledViewPool().onAdapterChanged(oldAdapter, newAdapter, compatibleWithPrevious);
        }

        void offsetPositionRecordsForMove(int from, int to) {
            final int start, end, inBetweenOffset;
            if (from < to) {
                start = from;
                end = to;
                inBetweenOffset = -1;
            } else {
                start = to;
                end = from;
                inBetweenOffset = 1;
            }
            final int cachedCount = mCachedViews.size();
            for (int i = 0; i < cachedCount; i++) {
                final ViewHolder holder = mCachedViews.get(i);
                if (holder == null || holder.mPosition < start || holder.mPosition > end) {
                    continue;
                }
                if (holder.mPosition == from) {
                    holder.offsetPosition(to - from, false);
                } else {
                    holder.offsetPosition(inBetweenOffset, false);
                }
                if (DEBUG) {
                    Log.d(TAG, "offsetPositionRecordsForMove cached child " + i + " holder "
                            + holder);
                }
            }
        }

        void offsetPositionRecordsForInsert(int insertedAt, int count) {
            final int cachedCount = mCachedViews.size();
            for (int i = 0; i < cachedCount; i++) {
                final ViewHolder holder = mCachedViews.get(i);
                if (holder != null && holder.mPosition >= insertedAt) {
                    if (DEBUG) {
                        Log.d(TAG, "offsetPositionRecordsForInsert cached " + i + " holder "
                                + holder + " now at position " + (holder.mPosition + count));
                    }
                    holder.offsetPosition(count, true);
                }
            }
        }

        /**
         * @param removedFrom Remove start index
         * @param count Remove count
         * @param applyToPreLayout If true, changes will affect ViewHolder's pre-layout position, if
         *                         false, they'll be applied before the second layout pass
         */
        void offsetPositionRecordsForRemove(int removedFrom, int count, boolean applyToPreLayout) {
            final int removedEnd = removedFrom + count;
            final int cachedCount = mCachedViews.size();
            for (int i = cachedCount - 1; i >= 0; i--) {
                final ViewHolder holder = mCachedViews.get(i);
                if (holder != null) {
                    if (holder.mPosition >= removedEnd) {
                        if (DEBUG) {
                            Log.d(TAG, "offsetPositionRecordsForRemove cached " + i
                                    + " holder " + holder + " now at position "
                                    + (holder.mPosition - count));
                        }
                        holder.offsetPosition(-count, applyToPreLayout);
                    } else if (holder.mPosition >= removedFrom) {
                        // Item for this view was removed. Dump it from the cache.
                        holder.addFlags(ViewHolder.FLAG_REMOVED);
                        recycleCachedViewAt(i);
                    }
                }
            }
        }

        void setViewCacheExtension(ViewCacheExtension extension) {
            mViewCacheExtension = extension;
        }

        void setRecycledViewPool(RecycledViewPool pool) {
            if (mRecyclerPool != null) {
                mRecyclerPool.detach();
            }
            mRecyclerPool = pool;
            if (mRecyclerPool != null && getAdapter() != null) {
                mRecyclerPool.attach();
            }
        }

        RecycledViewPool getRecycledViewPool() {
            if (mRecyclerPool == null) {
                mRecyclerPool = new RecycledViewPool();
            }
            return mRecyclerPool;
        }

        void viewRangeUpdate(int positionStart, int itemCount) {
            final int positionEnd = positionStart + itemCount;
            final int cachedCount = mCachedViews.size();
            for (int i = cachedCount - 1; i >= 0; i--) {
                final ViewHolder holder = mCachedViews.get(i);
                if (holder == null) {
                    continue;
                }

                final int pos = holder.mPosition;
                if (pos >= positionStart && pos < positionEnd) {
                    holder.addFlags(ViewHolder.FLAG_UPDATE);
                    recycleCachedViewAt(i);
                    // cached views should not be flagged as changed because this will cause them
                    // to animate when they are returned from cache.
                }
            }
        }

        void markKnownViewsInvalid() {
            final int cachedCount = mCachedViews.size();
            for (int i = 0; i < cachedCount; i++) {
                final ViewHolder holder = mCachedViews.get(i);
                if (holder != null) {
                    holder.addFlags(ViewHolder.FLAG_UPDATE | ViewHolder.FLAG_INVALID);
                    holder.addChangePayload(null);
                }
            }

            if (mAdapter == null || !mAdapter.hasStableIds()) {
                // we cannot re-use cached views in this case. Recycle them all
                recycleAndClearCachedViews();
            }
        }

        void clearOldPositions() {
            final int cachedCount = mCachedViews.size();
            for (int i = 0; i < cachedCount; i++) {
                final ViewHolder holder = mCachedViews.get(i);
                holder.clearOldPosition();
            }
            final int scrapCount = mAttachedScrap.size();
            for (int i = 0; i < scrapCount; i++) {
                mAttachedScrap.get(i).clearOldPosition();
            }
            if (mChangedScrap != null) {
                final int changedScrapCount = mChangedScrap.size();
                for (int i = 0; i < changedScrapCount; i++) {
                    mChangedScrap.get(i).clearOldPosition();
                }
            }
        }

        void markItemDecorInsetsDirty() {
            final int cachedCount = mCachedViews.size();
            for (int i = 0; i < cachedCount; i++) {
                final ViewHolder holder = mCachedViews.get(i);
                LayoutParams layoutParams = (LayoutParams) holder.itemView.getLayoutParams();
                if (layoutParams != null) {
                    layoutParams.mInsetsDirty = true;
                }
            }
        }
    }

    /**
     * ViewCacheExtension is a helper class to provide an additional layer of view caching that can
     * be controlled by the developer.
     * <p>
     * When {@link Recycler#getViewForPosition(int)} is called, Recycler checks attached scrap and
     * first level cache to find a matching View. If it cannot find a suitable View, Recycler will
     * call the {@link #getViewForPositionAndType(Recycler, int, int)} before checking
     * {@link RecycledViewPool}.
     * <p>
     * Note that, Recycler never sends Views to this method to be cached. It is developers
     * responsibility to decide whether they want to keep their Views in this custom cache or let
     * the default recycling policy handle it.
     */
    public abstract static class ViewCacheExtension {

        /**
         * Returns a View that can be binded to the given Adapter position.
         * <p>
         * This method should <b>not</b> create a new View. Instead, it is expected to return
         * an already created View that can be re-used for the given type and position.
         * If the View is marked as ignored, it should first call
         * {@link LayoutManager#stopIgnoringView(View)} before returning the View.
         * <p>
         * RecyclerView will re-bind the returned View to the position if necessary.
         *
         * @param recycler The Recycler that can be used to bind the View
         * @param position The adapter position
         * @param type     The type of the View, defined by adapter
         * @return A View that is bound to the given position or NULL if there is no View to re-use
         * @see LayoutManager#ignoreView(View)
         */
        @Nullable
        public abstract View getViewForPositionAndType(@NonNull Recycler recycler, int position,
                int type);
    }

    /**
     * Base class for an Adapter
     *
     * <p>Adapters provide a binding from an app-specific data set to views that are displayed
     * within a {@link RecyclerView}.</p>
     *
     * @param <VH> A class that extends ViewHolder that will be used by the adapter.
     */
    public abstract static class Adapter<VH extends ViewHolder> {
        private final AdapterDataObservable mObservable = new AdapterDataObservable();
        private boolean mHasStableIds = false;

        /**
         * Called when RecyclerView needs a new {@link ViewHolder} of the given type to represent
         * an item.
         * <p>
         * This new ViewHolder should be constructed with a new View that can represent the items
         * of the given type. You can either create a new View manually or inflate it from an XML
         * layout file.
         * <p>
         * The new ViewHolder will be used to display items of the adapter using
         * {@link #onBindViewHolder(ViewHolder, int, List)}. Since it will be re-used to display
         * different items in the data set, it is a good idea to cache references to sub views of
         * the View to avoid unnecessary {@link View#findViewById(int)} calls.
         *
         * @param parent The ViewGroup into which the new View will be added after it is bound to
         *               an adapter position.
         * @param viewType The view type of the new View.
         *
         * @return A new ViewHolder that holds a View of the given view type.
         * @see #getItemViewType(int)
         * @see #onBindViewHolder(ViewHolder, int)
         */
        @NonNull
        public abstract VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType);

        /**
         * Called by RecyclerView to display the data at the specified position. This method should
         * update the contents of the {@link ViewHolder#itemView} to reflect the item at the given
         * position.
         * <p>
         * Note that unlike {@link android.widget.ListView}, RecyclerView will not call this method
         * again if the position of the item changes in the data set unless the item itself is
         * invalidated or the new position cannot be determined. For this reason, you should only
         * use the <code>position</code> parameter while acquiring the related data item inside
         * this method and should not keep a copy of it. If you need the position of an item later
         * on (e.g. in a click listener), use {@link ViewHolder#getAdapterPosition()} which will
         * have the updated adapter position.
         *
         * Override {@link #onBindViewHolder(ViewHolder, int, List)} instead if Adapter can
         * handle efficient partial bind.
         *
         * @param holder The ViewHolder which should be updated to represent the contents of the
         *        item at the given position in the data set.
         * @param position The position of the item within the adapter's data set.
         */
        public abstract void onBindViewHolder(@NonNull VH holder, int position);

        /**
         * Called by RecyclerView to display the data at the specified position. This method
         * should update the contents of the {@link ViewHolder#itemView} to reflect the item at
         * the given position.
         * <p>
         * Note that unlike {@link android.widget.ListView}, RecyclerView will not call this method
         * again if the position of the item changes in the data set unless the item itself is
         * invalidated or the new position cannot be determined. For this reason, you should only
         * use the <code>position</code> parameter while acquiring the related data item inside
         * this method and should not keep a copy of it. If you need the position of an item later
         * on (e.g. in a click listener), use {@link ViewHolder#getAdapterPosition()} which will
         * have the updated adapter position.
         * <p>
         * Partial bind vs full bind:
         * <p>
         * The payloads parameter is a merge list from {@link #notifyItemChanged(int, Object)} or
         * {@link #notifyItemRangeChanged(int, int, Object)}.  If the payloads list is not empty,
         * the ViewHolder is currently bound to old data and Adapter may run an efficient partial
         * update using the payload info.  If the payload is empty,  Adapter must run a full bind.
         * Adapter should not assume that the payload passed in notify methods will be received by
         * onBindViewHolder().  For example when the view is not attached to the screen, the
         * payload in notifyItemChange() will be simply dropped.
         *
         * @param holder The ViewHolder which should be updated to represent the contents of the
         *               item at the given position in the data set.
         * @param position The position of the item within the adapter's data set.
         * @param payloads A non-null list of merged payloads. Can be empty list if requires full
         *                 update.
         */
        public void onBindViewHolder(@NonNull VH holder, int position,
                @NonNull List<Object> payloads) {
            onBindViewHolder(holder, position);
        }

        /**
         * This method calls {@link #onCreateViewHolder(ViewGroup, int)} to create a new
         * {@link ViewHolder} and initializes some private fields to be used by RecyclerView.
         *
         * @see #onCreateViewHolder(ViewGroup, int)
         */
        @NonNull
        public final VH createViewHolder(@NonNull ViewGroup parent, int viewType) {
            try {
                TraceCompat.beginSection(TRACE_CREATE_VIEW_TAG);
                final VH holder = onCreateViewHolder(parent, viewType);
                if (holder.itemView.getParent() != null) {
                    throw new IllegalStateException("ViewHolder views must not be attached when"
                            + " created. Ensure that you are not passing 'true' to the attachToRoot"
                            + " parameter of LayoutInflater.inflate(..., boolean attachToRoot)");
                }
                holder.mItemViewType = viewType;
                return holder;
            } finally {
                TraceCompat.endSection();
            }
        }

        /**
         * This method internally calls {@link #onBindViewHolder(ViewHolder, int)} to update the
         * {@link ViewHolder} contents with the item at the given position and also sets up some
         * private fields to be used by RecyclerView.
         *
         * @see #onBindViewHolder(ViewHolder, int)
         */
        public final void bindViewHolder(@NonNull VH holder, int position) {
            holder.mPosition = position;
            if (hasStableIds()) {
                holder.mItemId = getItemId(position);
            }
            holder.setFlags(ViewHolder.FLAG_BOUND,
                    ViewHolder.FLAG_BOUND | ViewHolder.FLAG_UPDATE | ViewHolder.FLAG_INVALID
                            | ViewHolder.FLAG_ADAPTER_POSITION_UNKNOWN);
            TraceCompat.beginSection(TRACE_BIND_VIEW_TAG);
            onBindViewHolder(holder, position, holder.getUnmodifiedPayloads());
            holder.clearPayload();
            final ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
            if (layoutParams instanceof RecyclerView.LayoutParams) {
                ((LayoutParams) layoutParams).mInsetsDirty = true;
            }
            TraceCompat.endSection();
        }

        /**
         * Return the view type of the item at <code>position</code> for the purposes
         * of view recycling.
         *
         * <p>The default implementation of this method returns 0, making the assumption of
         * a single view type for the adapter. Unlike ListView adapters, types need not
         * be contiguous. Consider using id resources to uniquely identify item view types.
         *
         * @param position position to query
         * @return integer value identifying the type of the view needed to represent the item at
         *                 <code>position</code>. Type codes need not be contiguous.
         */
        public int getItemViewType(int position) {
            return 0;
        }

        /**
         * Indicates whether each item in the data set can be represented with a unique identifier
         * of type {@link java.lang.Long}.
         *
         * @param hasStableIds Whether items in data set have unique identifiers or not.
         * @see #hasStableIds()
         * @see #getItemId(int)
         */
        public void setHasStableIds(boolean hasStableIds) {
            if (hasObservers()) {
                throw new IllegalStateException("Cannot change whether this adapter has "
                        + "stable IDs while the adapter has registered observers.");
            }
            mHasStableIds = hasStableIds;
        }

        /**
         * Return the stable ID for the item at <code>position</code>. If {@link #hasStableIds()}
         * would return false this method should return {@link #NO_ID}. The default implementation
         * of this method returns {@link #NO_ID}.
         *
         * @param position Adapter position to query
         * @return the stable ID of the item at position
         */
        public long getItemId(int position) {
            return NO_ID;
        }

        /**
         * Returns the total number of items in the data set held by the adapter.
         *
         * @return The total number of items in this adapter.
         */
        public abstract int getItemCount();

        /**
         * Returns true if this adapter publishes a unique <code>long</code> value that can
         * act as a key for the item at a given position in the data set. If that item is relocated
         * in the data set, the ID returned for that item should be the same.
         *
         * @return true if this adapter's items have stable IDs
         */
        public final boolean hasStableIds() {
            return mHasStableIds;
        }

        /**
         * Called when a view created by this adapter has been recycled.
         *
         * <p>A view is recycled when a {@link LayoutManager} decides that it no longer
         * needs to be attached to its parent {@link RecyclerView}. This can be because it has
         * fallen out of visibility or a set of cached views represented by views still
         * attached to the parent RecyclerView. If an item view has large or expensive data
         * bound to it such as large bitmaps, this may be a good place to release those
         * resources.</p>
         * <p>
         * RecyclerView calls this method right before clearing ViewHolder's internal data and
         * sending it to RecycledViewPool. This way, if ViewHolder was holding valid information
         * before being recycled, you can call {@link ViewHolder#getAdapterPosition()} to get
         * its adapter position.
         *
         * @param holder The ViewHolder for the view being recycled
         */
        public void onViewRecycled(@NonNull VH holder) {
        }

        /**
         * Called by the RecyclerView if a ViewHolder created by this Adapter cannot be recycled
         * due to its transient state. Upon receiving this callback, Adapter can clear the
         * animation(s) that effect the View's transient state and return <code>true</code> so that
         * the View can be recycled. Keep in mind that the View in question is already removed from
         * the RecyclerView.
         * <p>
         * In some cases, it is acceptable to recycle a View although it has transient state. Most
         * of the time, this is a case where the transient state will be cleared in
         * {@link #onBindViewHolder(ViewHolder, int)} call when View is rebound to a new position.
         * For this reason, RecyclerView leaves the decision to the Adapter and uses the return
         * value of this method to decide whether the View should be recycled or not.
         * <p>
         * Note that when all animations are created by {@link RecyclerView.ItemAnimator}, you
         * should never receive this callback because RecyclerView keeps those Views as children
         * until their animations are complete. This callback is useful when children of the item
         * views create animations which may not be easy to implement using an {@link ItemAnimator}.
         * <p>
         * You should <em>never</em> fix this issue by calling
         * <code>holder.itemView.setHasTransientState(false);</code> unless you've previously called
         * <code>holder.itemView.setHasTransientState(true);</code>. Each
         * <code>View.setHasTransientState(true)</code> call must be matched by a
         * <code>View.setHasTransientState(false)</code> call, otherwise, the state of the View
         * may become inconsistent. You should always prefer to end or cancel animations that are
         * triggering the transient state instead of handling it manually.
         *
         * @param holder The ViewHolder containing the View that could not be recycled due to its
         *               transient state.
         * @return True if the View should be recycled, false otherwise. Note that if this method
         * returns <code>true</code>, RecyclerView <em>will ignore</em> the transient state of
         * the View and recycle it regardless. If this method returns <code>false</code>,
         * RecyclerView will check the View's transient state again before giving a final decision.
         * Default implementation returns false.
         */
        public boolean onFailedToRecycleView(@NonNull VH holder) {
            return false;
        }

        /**
         * Called when a view created by this adapter has been attached to a window.
         *
         * <p>This can be used as a reasonable signal that the view is about to be seen
         * by the user. If the adapter previously freed any resources in
         * {@link #onViewDetachedFromWindow(RecyclerView.ViewHolder) onViewDetachedFromWindow}
         * those resources should be restored here.</p>
         *
         * @param holder Holder of the view being attached
         */
        public void onViewAttachedToWindow(@NonNull VH holder) {
        }

        /**
         * Called when a view created by this adapter has been detached from its window.
         *
         * <p>Becoming detached from the window is not necessarily a permanent condition;
         * the consumer of an Adapter's views may choose to cache views offscreen while they
         * are not visible, attaching and detaching them as appropriate.</p>
         *
         * @param holder Holder of the view being detached
         */
        public void onViewDetachedFromWindow(@NonNull VH holder) {
        }

        /**
         * Returns true if one or more observers are attached to this adapter.
         *
         * @return true if this adapter has observers
         */
        public final boolean hasObservers() {
            return mObservable.hasObservers();
        }

        /**
         * Register a new observer to listen for data changes.
         *
         * <p>The adapter may publish a variety of events describing specific changes.
         * Not all adapters may support all change types and some may fall back to a generic
         * {@link RecyclerView.AdapterDataObserver#onChanged()
         * "something changed"} event if more specific data is not available.</p>
         *
         * <p>Components registering observers with an adapter are responsible for
         * {@link #unregisterAdapterDataObserver(RecyclerView.AdapterDataObserver)
         * unregistering} those observers when finished.</p>
         *
         * @param observer Observer to register
         *
         * @see #unregisterAdapterDataObserver(RecyclerView.AdapterDataObserver)
         */
        public void registerAdapterDataObserver(@NonNull AdapterDataObserver observer) {
            mObservable.registerObserver(observer);
        }

        /**
         * Unregister an observer currently listening for data changes.
         *
         * <p>The unregistered observer will no longer receive events about changes
         * to the adapter.</p>
         *
         * @param observer Observer to unregister
         *
         * @see #registerAdapterDataObserver(RecyclerView.AdapterDataObserver)
         */
        public void unregisterAdapterDataObserver(@NonNull AdapterDataObserver observer) {
            mObservable.unregisterObserver(observer);
        }

        /**
         * Called by RecyclerView when it starts observing this Adapter.
         * <p>
         * Keep in mind that same adapter may be observed by multiple RecyclerViews.
         *
         * @param recyclerView The RecyclerView instance which started observing this adapter.
         * @see #onDetachedFromRecyclerView(RecyclerView)
         */
        public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        }

        /**
         * Called by RecyclerView when it stops observing this Adapter.
         *
         * @param recyclerView The RecyclerView instance which stopped observing this adapter.
         * @see #onAttachedToRecyclerView(RecyclerView)
         */
        public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        }

        /**
         * Notify any registered observers that the data set has changed.
         *
         * <p>There are two different classes of data change events, item changes and structural
         * changes. Item changes are when a single item has its data updated but no positional
         * changes have occurred. Structural changes are when items are inserted, removed or moved
         * within the data set.</p>
         *
         * <p>This event does not specify what about the data set has changed, forcing
         * any observers to assume that all existing items and structure may no longer be valid.
         * LayoutManagers will be forced to fully rebind and relayout all visible views.</p>
         *
         * <p><code>RecyclerView</code> will attempt to synthesize visible structural change events
         * for adapters that report that they have {@link #hasStableIds() stable IDs} when
         * this method is used. This can help for the purposes of animation and visual
         * object persistence but individual item views will still need to be rebound
         * and relaid out.</p>
         *
         * <p>If you are writing an adapter it will always be more efficient to use the more
         * specific change events if you can. Rely on <code>notifyDataSetChanged()</code>
         * as a last resort.</p>
         *
         * @see #notifyItemChanged(int)
         * @see #notifyItemInserted(int)
         * @see #notifyItemRemoved(int)
         * @see #notifyItemRangeChanged(int, int)
         * @see #notifyItemRangeInserted(int, int)
         * @see #notifyItemRangeRemoved(int, int)
         */
        public void notifyDataSetChanged() {
            mObservable.notifyChanged();
        }

        /**
         * Notify any registered observers that the item at <code>position</code> has changed.
         * Equivalent to calling <code>notifyItemChanged(position, null);</code>.
         *
         * <p>This is an item change event, not a structural change event. It indicates that any
         * reflection of the data at <code>position</code> is out of date and should be updated.
         * The item at <code>position</code> retains the same identity.</p>
         *
         * @param position Position of the item that has changed
         *
         * @see #notifyItemRangeChanged(int, int)
         */
        public void notifyItemChanged(int position) {
            mObservable.notifyItemRangeChanged(position, 1);
        }

        /**
         * Notify any registered observers that the item at <code>position</code> has changed with
         * an optional payload object.
         *
         * <p>This is an item change event, not a structural change event. It indicates that any
         * reflection of the data at <code>position</code> is out of date and should be updated.
         * The item at <code>position</code> retains the same identity.
         * </p>
         *
         * <p>
         * Client can optionally pass a payload for partial change. These payloads will be merged
         * and may be passed to adapter's {@link #onBindViewHolder(ViewHolder, int, List)} if the
         * item is already represented by a ViewHolder and it will be rebound to the same
         * ViewHolder. A notifyItemRangeChanged() with null payload will clear all existing
         * payloads on that item and prevent future payload until
         * {@link #onBindViewHolder(ViewHolder, int, List)} is called. Adapter should not assume
         * that the payload will always be passed to onBindViewHolder(), e.g. when the view is not
         * attached, the payload will be simply dropped.
         *
         * @param position Position of the item that has changed
         * @param payload Optional parameter, use null to identify a "full" update
         *
         * @see #notifyItemRangeChanged(int, int)
         */
        public void notifyItemChanged(int position, @Nullable Object payload) {
            mObservable.notifyItemRangeChanged(position, 1, payload);
        }

        /**
         * Notify any registered observers that the <code>itemCount</code> items starting at
         * position <code>positionStart</code> have changed.
         * Equivalent to calling <code>notifyItemRangeChanged(position, itemCount, null);</code>.
         *
         * <p>This is an item change event, not a structural change event. It indicates that
         * any reflection of the data in the given position range is out of date and should
         * be updated. The items in the given range retain the same identity.</p>
         *
         * @param positionStart Position of the first item that has changed
         * @param itemCount Number of items that have changed
         *
         * @see #notifyItemChanged(int)
         */
        public void notifyItemRangeChanged(int positionStart, int itemCount) {
            mObservable.notifyItemRangeChanged(positionStart, itemCount);
        }

        /**
         * Notify any registered observers that the <code>itemCount</code> items starting at
         * position <code>positionStart</code> have changed. An optional payload can be
         * passed to each changed item.
         *
         * <p>This is an item change event, not a structural change event. It indicates that any
         * reflection of the data in the given position range is out of date and should be updated.
         * The items in the given range retain the same identity.
         * </p>
         *
         * <p>
         * Client can optionally pass a payload for partial change. These payloads will be merged
         * and may be passed to adapter's {@link #onBindViewHolder(ViewHolder, int, List)} if the
         * item is already represented by a ViewHolder and it will be rebound to the same
         * ViewHolder. A notifyItemRangeChanged() with null payload will clear all existing
         * payloads on that item and prevent future payload until
         * {@link #onBindViewHolder(ViewHolder, int, List)} is called. Adapter should not assume
         * that the payload will always be passed to onBindViewHolder(), e.g. when the view is not
         * attached, the payload will be simply dropped.
         *
         * @param positionStart Position of the first item that has changed
         * @param itemCount Number of items that have changed
         * @param payload  Optional parameter, use null to identify a "full" update
         *
         * @see #notifyItemChanged(int)
         */
        public void notifyItemRangeChanged(int positionStart, int itemCount,
                @Nullable Object payload) {
            mObservable.notifyItemRangeChanged(positionStart, itemCount, payload);
        }

        /**
         * Notify any registered observers that the item reflected at <code>position</code>
         * has been newly inserted. The item previously at <code>position</code> is now at
         * position <code>position + 1</code>.
         *
         * <p>This is a structural change event. Representations of other existing items in the
         * data set are still considered up to date and will not be rebound, though their
         * positions may be altered.</p>
         *
         * @param position Position of the newly inserted item in the data set
         *
         * @see #notifyItemRangeInserted(int, int)
         */
        public void notifyItemInserted(int position) {
            mObservable.notifyItemRangeInserted(position, 1);
        }

        /**
         * Notify any registered observers that the item reflected at <code>fromPosition</code>
         * has been moved to <code>toPosition</code>.
         *
         * <p>This is a structural change event. Representations of other existing items in the
         * data set are still considered up to date and will not be rebound, though their
         * positions may be altered.</p>
         *
         * @param fromPosition Previous position of the item.
         * @param toPosition New position of the item.
         */
        public void notifyItemMoved(int fromPosition, int toPosition) {
            mObservable.notifyItemMoved(fromPosition, toPosition);
        }

        /**
         * Notify any registered observers that the currently reflected <code>itemCount</code>
         * items starting at <code>positionStart</code> have been newly inserted. The items
         * previously located at <code>positionStart</code> and beyond can now be found starting
         * at position <code>positionStart + itemCount</code>.
         *
         * <p>This is a structural change event. Representations of other existing items in the
         * data set are still considered up to date and will not be rebound, though their positions
         * may be altered.</p>
         *
         * @param positionStart Position of the first item that was inserted
         * @param itemCount Number of items inserted
         *
         * @see #notifyItemInserted(int)
         */
        public void notifyItemRangeInserted(int positionStart, int itemCount) {
            mObservable.notifyItemRangeInserted(positionStart, itemCount);
        }

        /**
         * Notify any registered observers that the item previously located at <code>position</code>
         * has been removed from the data set. The items previously located at and after
         * <code>position</code> may now be found at <code>oldPosition - 1</code>.
         *
         * <p>This is a structural change event. Representations of other existing items in the
         * data set are still considered up to date and will not be rebound, though their positions
         * may be altered.</p>
         *
         * @param position Position of the item that has now been removed
         *
         * @see #notifyItemRangeRemoved(int, int)
         */
        public void notifyItemRemoved(int position) {
            mObservable.notifyItemRangeRemoved(position, 1);
        }

        /**
         * Notify any registered observers that the <code>itemCount</code> items previously
         * located at <code>positionStart</code> have been removed from the data set. The items
         * previously located at and after <code>positionStart + itemCount</code> may now be found
         * at <code>oldPosition - itemCount</code>.
         *
         * <p>This is a structural change event. Representations of other existing items in the data
         * set are still considered up to date and will not be rebound, though their positions
         * may be altered.</p>
         *
         * @param positionStart Previous position of the first item that was removed
         * @param itemCount Number of items removed from the data set
         */
        public void notifyItemRangeRemoved(int positionStart, int itemCount) {
            mObservable.notifyItemRangeRemoved(positionStart, itemCount);
        }
    }

    void dispatchChildDetached(View child) {
        final ViewHolder viewHolder = getChildViewHolderInt(child);
        onChildDetachedFromWindow(child);
        if (mAdapter != null && viewHolder != null) {
            mAdapter.onViewDetachedFromWindow(viewHolder);
        }
        if (mOnChildAttachStateListeners != null) {
            final int cnt = mOnChildAttachStateListeners.size();
            for (int i = cnt - 1; i >= 0; i--) {
                mOnChildAttachStateListeners.get(i).onChildViewDetachedFromWindow(child);
            }
        }
    }

    void dispatchChildAttached(View child) {
        final ViewHolder viewHolder = getChildViewHolderInt(child);
        onChildAttachedToWindow(child);
        if (mAdapter != null && viewHolder != null) {
            mAdapter.onViewAttachedToWindow(viewHolder);
        }
        if (mOnChildAttachStateListeners != null) {
            final int cnt = mOnChildAttachStateListeners.size();
            for (int i = cnt - 1; i >= 0; i--) {
                mOnChildAttachStateListeners.get(i).onChildViewAttachedToWindow(child);
            }
        }
    }

    /**
     * A <code>LayoutManager</code> is responsible for measuring and positioning item views
     * within a <code>RecyclerView</code> as well as determining the policy for when to recycle
     * item views that are no longer visible to the user. By changing the <code>LayoutManager</code>
     * a <code>RecyclerView</code> can be used to implement a standard vertically scrolling list,
     * a uniform grid, staggered grids, horizontally scrolling collections and more. Several stock
     * layout managers are provided for general use.
     * <p/>
     * If the LayoutManager specifies a default constructor or one with the signature
     * ({@link Context}, {@link AttributeSet}, {@code int}, {@code int}), RecyclerView will
     * instantiate and set the LayoutManager when being inflated. Most used properties can
     * be then obtained from {@link #getProperties(Context, AttributeSet, int, int)}. In case
     * a LayoutManager specifies both constructors, the non-default constructor will take
     * precedence.
     *
     */
    public abstract static class LayoutManager {
        ChildHelper mChildHelper;
        RecyclerView mRecyclerView;

        /**
         * The callback used for retrieving information about a RecyclerView and its children in the
         * horizontal direction.
         */
        private final ViewBoundsCheck.Callback mHorizontalBoundCheckCallback =
                new ViewBoundsCheck.Callback() {
                    @Override
                    public View getChildAt(int index) {
                        return LayoutManager.this.getChildAt(index);
                    }

                    @Override
                    public int getParentStart() {
                        return LayoutManager.this.getPaddingLeft();
                    }

                    @Override
                    public int getParentEnd() {
                        return LayoutManager.this.getWidth() - LayoutManager.this.getPaddingRight();
                    }

                    @Override
                    public int getChildStart(View view) {
                        final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams)
                                view.getLayoutParams();
                        return LayoutManager.this.getDecoratedLeft(view) - params.leftMargin;
                    }

                    @Override
                    public int getChildEnd(View view) {
                        final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams)
                                view.getLayoutParams();
                        return LayoutManager.this.getDecoratedRight(view) + params.rightMargin;
                    }
                };

        /**
         * The callback used for retrieving information about a RecyclerView and its children in the
         * vertical direction.
         */
        private final ViewBoundsCheck.Callback mVerticalBoundCheckCallback =
                new ViewBoundsCheck.Callback() {
                    @Override
                    public View getChildAt(int index) {
                        return LayoutManager.this.getChildAt(index);
                    }

                    @Override
                    public int getParentStart() {
                        return LayoutManager.this.getPaddingTop();
                    }

                    @Override
                    public int getParentEnd() {
                        return LayoutManager.this.getHeight()
                                - LayoutManager.this.getPaddingBottom();
                    }

                    @Override
                    public int getChildStart(View view) {
                        final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams)
                                view.getLayoutParams();
                        return LayoutManager.this.getDecoratedTop(view) - params.topMargin;
                    }

                    @Override
                    public int getChildEnd(View view) {
                        final RecyclerView.LayoutParams params = (RecyclerView.LayoutParams)
                                view.getLayoutParams();
                        return LayoutManager.this.getDecoratedBottom(view) + params.bottomMargin;
                    }
                };

        /**
         * Utility objects used to check the boundaries of children against their parent
         * RecyclerView.
         * @see #isViewPartiallyVisible(View, boolean, boolean),
         * {@link LinearLayoutManager#findOneVisibleChild(int, int, boolean, boolean)},
         * and {@link LinearLayoutManager#findOnePartiallyOrCompletelyInvisibleChild(int, int)}.
         */
        ViewBoundsCheck mHorizontalBoundCheck = new ViewBoundsCheck(mHorizontalBoundCheckCallback);
        ViewBoundsCheck mVerticalBoundCheck = new ViewBoundsCheck(mVerticalBoundCheckCallback);

        @Nullable
        SmoothScroller mSmoothScroller;

        boolean mRequestedSimpleAnimations = false;

        boolean mIsAttachedToWindow = false;

        /**
         * This field is only set via the deprecated {@link #setAutoMeasureEnabled(boolean)} and is
         * only accessed via {@link #isAutoMeasureEnabled()} for backwards compatability reasons.
         */
        boolean mAutoMeasure = false;

        /**
         * LayoutManager has its own more strict measurement cache to avoid re-measuring a child
         * if the space that will be given to it is already larger than what it has measured before.
         */
        private boolean mMeasurementCacheEnabled = true;

        private boolean mItemPrefetchEnabled = true;

        /**
         * Written by {@link GapWorker} when prefetches occur to track largest number of view ever
         * requested by a {@link #collectInitialPrefetchPositions(int, LayoutPrefetchRegistry)} or
         * {@link #collectAdjacentPrefetchPositions(int, int, State, LayoutPrefetchRegistry)} call.
         *
         * If expanded by a {@link #collectInitialPrefetchPositions(int, LayoutPrefetchRegistry)},
         * will be reset upon layout to prevent initial prefetches (often large, since they're
         * proportional to expected child count) from expanding cache permanently.
         */
        int mPrefetchMaxCountObserved;

        /**
         * If true, mPrefetchMaxCountObserved is only valid until next layout, and should be reset.
         */
        boolean mPrefetchMaxObservedInInitialPrefetch;

        /**
         * These measure specs might be the measure specs that were passed into RecyclerView's
         * onMeasure method OR fake measure specs created by the RecyclerView.
         * For example, when a layout is run, RecyclerView always sets these specs to be
         * EXACTLY because a LayoutManager cannot resize RecyclerView during a layout pass.
         * <p>
         * Also, to be able to use the hint in unspecified measure specs, RecyclerView checks the
         * API level and sets the size to 0 pre-M to avoid any issue that might be caused by
         * corrupt values. Older platforms have no responsibility to provide a size if they set
         * mode to unspecified.
         */
        private int mWidthMode, mHeightMode;
        private int mWidth, mHeight;


        /**
         * Interface for LayoutManagers to request items to be prefetched, based on position, with
         * specified distance from viewport, which indicates priority.
         *
         * @see LayoutManager#collectAdjacentPrefetchPositions(int, int, State, LayoutPrefetchRegistry)
         * @see LayoutManager#collectInitialPrefetchPositions(int, LayoutPrefetchRegistry)
         */
        public interface LayoutPrefetchRegistry {
            /**
             * Requests an an item to be prefetched, based on position, with a specified distance,
             * indicating priority.
             *
             * @param layoutPosition Position of the item to prefetch.
             * @param pixelDistance Distance from the current viewport to the bounds of the item,
             *                      must be non-negative.
             */
            void addPosition(int layoutPosition, int pixelDistance);
        }

        void setRecyclerView(RecyclerView recyclerView) {
            if (recyclerView == null) {
                mRecyclerView = null;
                mChildHelper = null;
                mWidth = 0;
                mHeight = 0;
            } else {
                mRecyclerView = recyclerView;
                mChildHelper = recyclerView.mChildHelper;
                mWidth = recyclerView.getWidth();
                mHeight = recyclerView.getHeight();
            }
            mWidthMode = MeasureSpec.EXACTLY;
            mHeightMode = MeasureSpec.EXACTLY;
        }

        void setMeasureSpecs(int wSpec, int hSpec) {
            mWidth = MeasureSpec.getSize(wSpec);
            mWidthMode = MeasureSpec.getMode(wSpec);
            if (mWidthMode == MeasureSpec.UNSPECIFIED && !ALLOW_SIZE_IN_UNSPECIFIED_SPEC) {
                mWidth = 0;
            }

            mHeight = MeasureSpec.getSize(hSpec);
            mHeightMode = MeasureSpec.getMode(hSpec);
            if (mHeightMode == MeasureSpec.UNSPECIFIED && !ALLOW_SIZE_IN_UNSPECIFIED_SPEC) {
                mHeight = 0;
            }
        }

        /**
         * Called after a layout is calculated during a measure pass when using auto-measure.
         * <p>
         * It simply traverses all children to calculate a bounding box then calls
         * {@link #setMeasuredDimension(Rect, int, int)}. LayoutManagers can override that method
         * if they need to handle the bounding box differently.
         * <p>
         * For example, GridLayoutManager override that method to ensure that even if a column is
         * empty, the GridLayoutManager still measures wide enough to include it.
         *
         * @param widthSpec The widthSpec that was passing into RecyclerView's onMeasure
         * @param heightSpec The heightSpec that was passing into RecyclerView's onMeasure
         */
        void setMeasuredDimensionFromChildren(int widthSpec, int heightSpec) {
            final int count = getChildCount();
            if (count == 0) {
                mRecyclerView.defaultOnMeasure(widthSpec, heightSpec);
                return;
            }
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;

            for (int i = 0; i < count; i++) {
                View child = getChildAt(i);
                final Rect bounds = mRecyclerView.mTempRect;
                getDecoratedBoundsWithMargins(child, bounds);
                if (bounds.left < minX) {
                    minX = bounds.left;
                }
                if (bounds.right > maxX) {
                    maxX = bounds.right;
                }
                if (bounds.top < minY) {
                    minY = bounds.top;
                }
                if (bounds.bottom > maxY) {
                    maxY = bounds.bottom;
                }
            }
            mRecyclerView.mTempRect.set(minX, minY, maxX, maxY);
            setMeasuredDimension(mRecyclerView.mTempRect, widthSpec, heightSpec);
        }

        /**
         * Sets the measured dimensions from the given bounding box of the children and the
         * measurement specs that were passed into {@link RecyclerView#onMeasure(int, int)}. It is
         * only called if a LayoutManager returns <code>true</code> from
         * {@link #isAutoMeasureEnabled()} and it is called after the RecyclerView calls
         * {@link LayoutManager#onLayoutChildren(Recycler, State)} in the execution of
         * {@link RecyclerView#onMeasure(int, int)}.
         * <p>
         * This method must call {@link #setMeasuredDimension(int, int)}.
         * <p>
         * The default implementation adds the RecyclerView's padding to the given bounding box
         * then caps the value to be within the given measurement specs.
         *
         * @param childrenBounds The bounding box of all children
         * @param wSpec The widthMeasureSpec that was passed into the RecyclerView.
         * @param hSpec The heightMeasureSpec that was passed into the RecyclerView.
         *
         * @see #isAutoMeasureEnabled()
         * @see #setMeasuredDimension(int, int)
         */
        public void setMeasuredDimension(Rect childrenBounds, int wSpec, int hSpec) {
            int usedWidth = childrenBounds.width() + getPaddingLeft() + getPaddingRight();
            int usedHeight = childrenBounds.height() + getPaddingTop() + getPaddingBottom();
            int width = chooseSize(wSpec, usedWidth, getMinimumWidth());
            int height = chooseSize(hSpec, usedHeight, getMinimumHeight());
            setMeasuredDimension(width, height);
        }

        /**
         * Calls {@code RecyclerView#requestLayout} on the underlying RecyclerView
         */
        public void requestLayout() {
            if (mRecyclerView != null) {
                mRecyclerView.requestLayout();
            }
        }

        /**
         * Checks if RecyclerView is in the middle of a layout or scroll and throws an
         * {@link IllegalStateException} if it <b>is not</b>.
         *
         * @param message The message for the exception. Can be null.
         * @see #assertNotInLayoutOrScroll(String)
         */
        public void assertInLayoutOrScroll(String message) {
            if (mRecyclerView != null) {
                mRecyclerView.assertInLayoutOrScroll(message);
            }
        }

        /**
         * Chooses a size from the given specs and parameters that is closest to the desired size
         * and also complies with the spec.
         *
         * @param spec The measureSpec
         * @param desired The preferred measurement
         * @param min The minimum value
         *
         * @return A size that fits to the given specs
         */
        public static int chooseSize(int spec, int desired, int min) {
            final int mode = View.MeasureSpec.getMode(spec);
            final int size = View.MeasureSpec.getSize(spec);
            switch (mode) {
                case View.MeasureSpec.EXACTLY:
                    return size;
                case View.MeasureSpec.AT_MOST:
                    return Math.min(size, Math.max(desired, min));
                case View.MeasureSpec.UNSPECIFIED:
                default:
                    return Math.max(desired, min);
            }
        }

        /**
         * Checks if RecyclerView is in the middle of a layout or scroll and throws an
         * {@link IllegalStateException} if it <b>is</b>.
         *
         * @param message The message for the exception. Can be null.
         * @see #assertInLayoutOrScroll(String)
         */
        public void assertNotInLayoutOrScroll(String message) {
            if (mRecyclerView != null) {
                mRecyclerView.assertNotInLayoutOrScroll(message);
            }
        }

        /**
         * Defines whether the measuring pass of layout should use the AutoMeasure mechanism of
         * {@link RecyclerView} or if it should be done by the LayoutManager's implementation of
         * {@link LayoutManager#onMeasure(Recycler, State, int, int)}.
         *
         * @param enabled <code>True</code> if layout measurement should be done by the
         *                RecyclerView, <code>false</code> if it should be done by this
         *                LayoutManager.
         *
         * @see #isAutoMeasureEnabled()
         *
         * @deprecated Implementors of LayoutManager should define whether or not it uses
         *             AutoMeasure by overriding {@link #isAutoMeasureEnabled()}.
         */
        @Deprecated
        public void setAutoMeasureEnabled(boolean enabled) {
            mAutoMeasure = enabled;
        }

        /**
         * Returns whether the measuring pass of layout should use the AutoMeasure mechanism of
         * {@link RecyclerView} or if it should be done by the LayoutManager's implementation of
         * {@link LayoutManager#onMeasure(Recycler, State, int, int)}.
         * <p>
         * This method returns false by default (it actually returns the value passed to the
         * deprecated {@link #setAutoMeasureEnabled(boolean)}) and should be overridden to return
         * true if a LayoutManager wants to be auto measured by the RecyclerView.
         * <p>
         * If this method is overridden to return true,
         * {@link LayoutManager#onMeasure(Recycler, State, int, int)} should not be overridden.
         * <p>
         * AutoMeasure is a RecyclerView mechanism that handles the measuring pass of layout in a
         * simple and contract satisfying way, including the wrapping of children laid out by
         * LayoutManager. Simply put, it handles wrapping children by calling
         * {@link LayoutManager#onLayoutChildren(Recycler, State)} during a call to
         * {@link RecyclerView#onMeasure(int, int)}, and then calculating desired dimensions based
         * on children's dimensions and positions. It does this while supporting all existing
         * animation capabilities of the RecyclerView.
         * <p>
         * More specifically:
         * <ol>
         * <li>When {@link RecyclerView#onMeasure(int, int)} is called, if the provided measure
         * specs both have a mode of {@link View.MeasureSpec#EXACTLY}, RecyclerView will set its
         * measured dimensions accordingly and return, allowing layout to continue as normal
         * (Actually, RecyclerView will call
         * {@link LayoutManager#onMeasure(Recycler, State, int, int)} for backwards compatibility
         * reasons but it should not be overridden if AutoMeasure is being used).</li>
         * <li>If one of the layout specs is not {@code EXACT}, the RecyclerView will start the
         * layout process. It will first process all pending Adapter updates and
         * then decide whether to run a predictive layout. If it decides to do so, it will first
         * call {@link #onLayoutChildren(Recycler, State)} with {@link State#isPreLayout()} set to
         * {@code true}. At this stage, {@link #getWidth()} and {@link #getHeight()} will still
         * return the width and height of the RecyclerView as of the last layout calculation.
         * <p>
         * After handling the predictive case, RecyclerView will call
         * {@link #onLayoutChildren(Recycler, State)} with {@link State#isMeasuring()} set to
         * {@code true} and {@link State#isPreLayout()} set to {@code false}. The LayoutManager can
         * access the measurement specs via {@link #getHeight()}, {@link #getHeightMode()},
         * {@link #getWidth()} and {@link #getWidthMode()}.</li>
         * <li>After the layout calculation, RecyclerView sets the measured width & height by
         * calculating the bounding box for the children (+ RecyclerView's padding). The
         * LayoutManagers can override {@link #setMeasuredDimension(Rect, int, int)} to choose
         * different values. For instance, GridLayoutManager overrides this value to handle the case
         * where if it is vertical and has 3 columns but only 2 items, it should still measure its
         * width to fit 3 items, not 2.</li>
         * <li>Any following calls to {@link RecyclerView#onMeasure(int, int)} will run
         * {@link #onLayoutChildren(Recycler, State)} with {@link State#isMeasuring()} set to
         * {@code true} and {@link State#isPreLayout()} set to {@code false}. RecyclerView will
         * take care of which views are actually added / removed / moved / changed for animations so
         * that the LayoutManager should not worry about them and handle each
         * {@link #onLayoutChildren(Recycler, State)} call as if it is the last one.</li>
         * <li>When measure is complete and RecyclerView's
         * {@link #onLayout(boolean, int, int, int, int)} method is called, RecyclerView checks
         * whether it already did layout calculations during the measure pass and if so, it re-uses
         * that information. It may still decide to call {@link #onLayoutChildren(Recycler, State)}
         * if the last measure spec was different from the final dimensions or adapter contents
         * have changed between the measure call and the layout call.</li>
         * <li>Finally, animations are calculated and run as usual.</li>
         * </ol>
         *
         * @return <code>True</code> if the measuring pass of layout should use the AutoMeasure
         * mechanism of {@link RecyclerView} or <code>False</code> if it should be done by the
         * LayoutManager's implementation of
         * {@link LayoutManager#onMeasure(Recycler, State, int, int)}.
         *
         * @see #setMeasuredDimension(Rect, int, int)
         * @see #onMeasure(Recycler, State, int, int)
         */
        public boolean isAutoMeasureEnabled() {
            return mAutoMeasure;
        }

        /**
         * Returns whether this LayoutManager supports "predictive item animations".
         * <p>
         * "Predictive item animations" are automatically created animations that show
         * where items came from, and where they are going to, as items are added, removed,
         * or moved within a layout.
         * <p>
         * A LayoutManager wishing to support predictive item animations must override this
         * method to return true (the default implementation returns false) and must obey certain
         * behavioral contracts outlined in {@link #onLayoutChildren(Recycler, State)}.
         * <p>
         * Whether item animations actually occur in a RecyclerView is actually determined by both
         * the return value from this method and the
         * {@link RecyclerView#setItemAnimator(ItemAnimator) ItemAnimator} set on the
         * RecyclerView itself. If the RecyclerView has a non-null ItemAnimator but this
         * method returns false, then only "simple item animations" will be enabled in the
         * RecyclerView, in which views whose position are changing are simply faded in/out. If the
         * RecyclerView has a non-null ItemAnimator and this method returns true, then predictive
         * item animations will be enabled in the RecyclerView.
         *
         * @return true if this LayoutManager supports predictive item animations, false otherwise.
         */
        public boolean supportsPredictiveItemAnimations() {
            return false;
        }

        /**
         * Sets whether the LayoutManager should be queried for views outside of
         * its viewport while the UI thread is idle between frames.
         *
         * <p>If enabled, the LayoutManager will be queried for items to inflate/bind in between
         * view system traversals on devices running API 21 or greater. Default value is true.</p>
         *
         * <p>On platforms API level 21 and higher, the UI thread is idle between passing a frame
         * to RenderThread and the starting up its next frame at the next VSync pulse. By
         * prefetching out of window views in this time period, delays from inflation and view
         * binding are much less likely to cause jank and stuttering during scrolls and flings.</p>
         *
         * <p>While prefetch is enabled, it will have the side effect of expanding the effective
         * size of the View cache to hold prefetched views.</p>
         *
         * @param enabled <code>True</code> if items should be prefetched in between traversals.
         *
         * @see #isItemPrefetchEnabled()
         */
        public final void setItemPrefetchEnabled(boolean enabled) {
            if (enabled != mItemPrefetchEnabled) {
                mItemPrefetchEnabled = enabled;
                mPrefetchMaxCountObserved = 0;
                if (mRecyclerView != null) {
                    mRecyclerView.mRecycler.updateViewCacheSize();
                }
            }
        }

        /**
         * Sets whether the LayoutManager should be queried for views outside of
         * its viewport while the UI thread is idle between frames.
         *
         * @see #setItemPrefetchEnabled(boolean)
         *
         * @return true if item prefetch is enabled, false otherwise
         */
        public final boolean isItemPrefetchEnabled() {
            return mItemPrefetchEnabled;
        }

        /**
         * Gather all positions from the LayoutManager to be prefetched, given specified momentum.
         *
         * <p>If item prefetch is enabled, this method is called in between traversals to gather
         * which positions the LayoutManager will soon need, given upcoming movement in subsequent
         * traversals.</p>
         *
         * <p>The LayoutManager should call {@link LayoutPrefetchRegistry#addPosition(int, int)} for
         * each item to be prepared, and these positions will have their ViewHolders created and
         * bound, if there is sufficient time available, in advance of being needed by a
         * scroll or layout.</p>
         *
         * @param dx X movement component.
         * @param dy Y movement component.
         * @param state State of RecyclerView
         * @param layoutPrefetchRegistry PrefetchRegistry to add prefetch entries into.
         *
         * @see #isItemPrefetchEnabled()
         * @see #collectInitialPrefetchPositions(int, LayoutPrefetchRegistry)
         */
        public void collectAdjacentPrefetchPositions(int dx, int dy, State state,
                LayoutPrefetchRegistry layoutPrefetchRegistry) {}

        /**
         * Gather all positions from the LayoutManager to be prefetched in preperation for its
         * RecyclerView to come on screen, due to the movement of another, containing RecyclerView.
         *
         * <p>This method is only called when a RecyclerView is nested in another RecyclerView.</p>
         *
         * <p>If item prefetch is enabled for this LayoutManager, as well in another containing
         * LayoutManager, this method is called in between draw traversals to gather
         * which positions this LayoutManager will first need, once it appears on the screen.</p>
         *
         * <p>For example, if this LayoutManager represents a horizontally scrolling list within a
         * vertically scrolling LayoutManager, this method would be called when the horizontal list
         * is about to come onscreen.</p>
         *
         * <p>The LayoutManager should call {@link LayoutPrefetchRegistry#addPosition(int, int)} for
         * each item to be prepared, and these positions will have their ViewHolders created and
         * bound, if there is sufficient time available, in advance of being needed by a
         * scroll or layout.</p>
         *
         * @param adapterItemCount number of items in the associated adapter.
         * @param layoutPrefetchRegistry PrefetchRegistry to add prefetch entries into.
         *
         * @see #isItemPrefetchEnabled()
         * @see #collectAdjacentPrefetchPositions(int, int, State, LayoutPrefetchRegistry)
         */
        public void collectInitialPrefetchPositions(int adapterItemCount,
                LayoutPrefetchRegistry layoutPrefetchRegistry) {}

        void dispatchAttachedToWindow(RecyclerView view) {
            mIsAttachedToWindow = true;
            onAttachedToWindow(view);
        }

        void dispatchDetachedFromWindow(RecyclerView view, Recycler recycler) {
            mIsAttachedToWindow = false;
            onDetachedFromWindow(view, recycler);
        }

        /**
         * Returns whether LayoutManager is currently attached to a RecyclerView which is attached
         * to a window.
         *
         * @return True if this LayoutManager is controlling a RecyclerView and the RecyclerView
         * is attached to window.
         */
        public boolean isAttachedToWindow() {
            return mIsAttachedToWindow;
        }

        /**
         * Causes the Runnable to execute on the next animation time step.
         * The runnable will be run on the user interface thread.
         * <p>
         * Calling this method when LayoutManager is not attached to a RecyclerView has no effect.
         *
         * @param action The Runnable that will be executed.
         *
         * @see #removeCallbacks
         */
        public void postOnAnimation(Runnable action) {
            if (mRecyclerView != null) {
                ViewCompat.postOnAnimation(mRecyclerView, action);
            }
        }

        /**
         * Removes the specified Runnable from the message queue.
         * <p>
         * Calling this method when LayoutManager is not attached to a RecyclerView has no effect.
         *
         * @param action The Runnable to remove from the message handling queue
         *
         * @return true if RecyclerView could ask the Handler to remove the Runnable,
         *         false otherwise. When the returned value is true, the Runnable
         *         may or may not have been actually removed from the message queue
         *         (for instance, if the Runnable was not in the queue already.)
         *
         * @see #postOnAnimation
         */
        public boolean removeCallbacks(Runnable action) {
            if (mRecyclerView != null) {
                return mRecyclerView.removeCallbacks(action);
            }
            return false;
        }
        /**
         * Called when this LayoutManager is both attached to a RecyclerView and that RecyclerView
         * is attached to a window.
         * <p>
         * If the RecyclerView is re-attached with the same LayoutManager and Adapter, it may not
         * call {@link #onLayoutChildren(Recycler, State)} if nothing has changed and a layout was
         * not requested on the RecyclerView while it was detached.
         * <p>
         * Subclass implementations should always call through to the superclass implementation.
         *
         * @param view The RecyclerView this LayoutManager is bound to
         *
         * @see #onDetachedFromWindow(RecyclerView, Recycler)
         */
        @CallSuper
        public void onAttachedToWindow(RecyclerView view) {
        }

        /**
         * @deprecated
         * override {@link #onDetachedFromWindow(RecyclerView, Recycler)}
         */
        @Deprecated
        public void onDetachedFromWindow(RecyclerView view) {

        }

        /**
         * Called when this LayoutManager is detached from its parent RecyclerView or when
         * its parent RecyclerView is detached from its window.
         * <p>
         * LayoutManager should clear all of its View references as another LayoutManager might be
         * assigned to the RecyclerView.
         * <p>
         * If the RecyclerView is re-attached with the same LayoutManager and Adapter, it may not
         * call {@link #onLayoutChildren(Recycler, State)} if nothing has changed and a layout was
         * not requested on the RecyclerView while it was detached.
         * <p>
         * If your LayoutManager has View references that it cleans in on-detach, it should also
         * call {@link RecyclerView#requestLayout()} to ensure that it is re-laid out when
         * RecyclerView is re-attached.
         * <p>
         * Subclass implementations should always call through to the superclass implementation.
         *
         * @param view The RecyclerView this LayoutManager is bound to
         * @param recycler The recycler to use if you prefer to recycle your children instead of
         *                 keeping them around.
         *
         * @see #onAttachedToWindow(RecyclerView)
         */
        @CallSuper
        public void onDetachedFromWindow(RecyclerView view, Recycler recycler) {
            onDetachedFromWindow(view);
        }

        /**
         * Check if the RecyclerView is configured to clip child views to its padding.
         *
         * @return true if this RecyclerView clips children to its padding, false otherwise
         */
        public boolean getClipToPadding() {
            return mRecyclerView != null && mRecyclerView.mClipToPadding;
        }

        /**
         * Lay out all relevant child views from the given adapter.
         *
         * The LayoutManager is in charge of the behavior of item animations. By default,
         * RecyclerView has a non-null {@link #getItemAnimator() ItemAnimator}, and simple
         * item animations are enabled. This means that add/remove operations on the
         * adapter will result in animations to add new or appearing items, removed or
         * disappearing items, and moved items. If a LayoutManager returns false from
         * {@link #supportsPredictiveItemAnimations()}, which is the default, and runs a
         * normal layout operation during {@link #onLayoutChildren(Recycler, State)}, the
         * RecyclerView will have enough information to run those animations in a simple
         * way. For example, the default ItemAnimator, {@link DefaultItemAnimator}, will
         * simply fade views in and out, whether they are actually added/removed or whether
         * they are moved on or off the screen due to other add/remove operations.
         *
         * <p>A LayoutManager wanting a better item animation experience, where items can be
         * animated onto and off of the screen according to where the items exist when they
         * are not on screen, then the LayoutManager should return true from
         * {@link #supportsPredictiveItemAnimations()} and add additional logic to
         * {@link #onLayoutChildren(Recycler, State)}. Supporting predictive animations
         * means that {@link #onLayoutChildren(Recycler, State)} will be called twice;
         * once as a "pre" layout step to determine where items would have been prior to
         * a real layout, and again to do the "real" layout. In the pre-layout phase,
         * items will remember their pre-layout positions to allow them to be laid out
         * appropriately. Also, {@link LayoutParams#isItemRemoved() removed} items will
         * be returned from the scrap to help determine correct placement of other items.
         * These removed items should not be added to the child list, but should be used
         * to help calculate correct positioning of other views, including views that
         * were not previously onscreen (referred to as APPEARING views), but whose
         * pre-layout offscreen position can be determined given the extra
         * information about the pre-layout removed views.</p>
         *
         * <p>The second layout pass is the real layout in which only non-removed views
         * will be used. The only additional requirement during this pass is, if
         * {@link #supportsPredictiveItemAnimations()} returns true, to note which
         * views exist in the child list prior to layout and which are not there after
         * layout (referred to as DISAPPEARING views), and to position/layout those views
         * appropriately, without regard to the actual bounds of the RecyclerView. This allows
         * the animation system to know the location to which to animate these disappearing
         * views.</p>
         *
         * <p>The default LayoutManager implementations for RecyclerView handle all of these
         * requirements for animations already. Clients of RecyclerView can either use one
         * of these layout managers directly or look at their implementations of
         * onLayoutChildren() to see how they account for the APPEARING and
         * DISAPPEARING views.</p>
         *
         * @param recycler         Recycler to use for fetching potentially cached views for a
         *                         position
         * @param state            Transient state of RecyclerView
         */
        public void onLayoutChildren(Recycler recycler, State state) {
            Log.e(TAG, "You must override onLayoutChildren(Recycler recycler, State state) ");
        }

        /**
         * Called after a full layout calculation is finished. The layout calculation may include
         * multiple {@link #onLayoutChildren(Recycler, State)} calls due to animations or
         * layout measurement but it will include only one {@link #onLayoutCompleted(State)} call.
         * This method will be called at the end of {@link View#layout(int, int, int, int)} call.
         * <p>
         * This is a good place for the LayoutManager to do some cleanup like pending scroll
         * position, saved state etc.
         *
         * @param state Transient state of RecyclerView
         */
        public void onLayoutCompleted(State state) {
        }

        /**
         * Create a default <code>LayoutParams</code> object for a child of the RecyclerView.
         *
         * <p>LayoutManagers will often want to use a custom <code>LayoutParams</code> type
         * to store extra information specific to the layout. Client code should subclass
         * {@link RecyclerView.LayoutParams} for this purpose.</p>
         *
         * <p><em>Important:</em> if you use your own custom <code>LayoutParams</code> type
         * you must also override
         * {@link #checkLayoutParams(LayoutParams)},
         * {@link #generateLayoutParams(android.view.ViewGroup.LayoutParams)} and
         * {@link #generateLayoutParams(android.content.Context, android.util.AttributeSet)}.</p>
         *
         * @return A new LayoutParams for a child view
         */
        public abstract LayoutParams generateDefaultLayoutParams();

        /**
         * Determines the validity of the supplied LayoutParams object.
         *
         * <p>This should check to make sure that the object is of the correct type
         * and all values are within acceptable ranges. The default implementation
         * returns <code>true</code> for non-null params.</p>
         *
         * @param lp LayoutParams object to check
         * @return true if this LayoutParams object is valid, false otherwise
         */
        public boolean checkLayoutParams(LayoutParams lp) {
            return lp != null;
        }

        /**
         * Create a LayoutParams object suitable for this LayoutManager, copying relevant
         * values from the supplied LayoutParams object if possible.
         *
         * <p><em>Important:</em> if you use your own custom <code>LayoutParams</code> type
         * you must also override
         * {@link #checkLayoutParams(LayoutParams)},
         * {@link #generateLayoutParams(android.view.ViewGroup.LayoutParams)} and
         * {@link #generateLayoutParams(android.content.Context, android.util.AttributeSet)}.</p>
         *
         * @param lp Source LayoutParams object to copy values from
         * @return a new LayoutParams object
         */
        public LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
            if (lp instanceof LayoutParams) {
                return new LayoutParams((LayoutParams) lp);
            } else if (lp instanceof MarginLayoutParams) {
                return new LayoutParams((MarginLayoutParams) lp);
            } else {
                return new LayoutParams(lp);
            }
        }

        /**
         * Create a LayoutParams object suitable for this LayoutManager from
         * an inflated layout resource.
         *
         * <p><em>Important:</em> if you use your own custom <code>LayoutParams</code> type
         * you must also override
         * {@link #checkLayoutParams(LayoutParams)},
         * {@link #generateLayoutParams(android.view.ViewGroup.LayoutParams)} and
         * {@link #generateLayoutParams(android.content.Context, android.util.AttributeSet)}.</p>
         *
         * @param c Context for obtaining styled attributes
         * @param attrs AttributeSet describing the supplied arguments
         * @return a new LayoutParams object
         */
        public LayoutParams generateLayoutParams(Context c, AttributeSet attrs) {
            return new LayoutParams(c, attrs);
        }

        /**
         * Scroll horizontally by dx pixels in screen coordinates and return the distance traveled.
         * The default implementation does nothing and returns 0.
         *
         * @param dx            distance to scroll by in pixels. X increases as scroll position
         *                      approaches the right.
         * @param recycler      Recycler to use for fetching potentially cached views for a
         *                      position
         * @param state         Transient state of RecyclerView
         * @return The actual distance scrolled. The return value will be negative if dx was
         * negative and scrolling proceeeded in that direction.
         * <code>Math.abs(result)</code> may be less than dx if a boundary was reached.
         */
        public int scrollHorizontallyBy(int dx, Recycler recycler, State state) {
            return 0;
        }

        /**
         * Scroll vertically by dy pixels in screen coordinates and return the distance traveled.
         * The default implementation does nothing and returns 0.
         *
         * @param dy            distance to scroll in pixels. Y increases as scroll position
         *                      approaches the bottom.
         * @param recycler      Recycler to use for fetching potentially cached views for a
         *                      position
         * @param state         Transient state of RecyclerView
         * @return The actual distance scrolled. The return value will be negative if dy was
         * negative and scrolling proceeeded in that direction.
         * <code>Math.abs(result)</code> may be less than dy if a boundary was reached.
         */
        public int scrollVerticallyBy(int dy, Recycler recycler, State state) {
            return 0;
        }

        /**
         * Query if horizontal scrolling is currently supported. The default implementation
         * returns false.
         *
         * @return True if this LayoutManager can scroll the current contents horizontally
         */
        public boolean canScrollHorizontally() {
            return false;
        }

        /**
         * Query if vertical scrolling is currently supported. The default implementation
         * returns false.
         *
         * @return True if this LayoutManager can scroll the current contents vertically
         */
        public boolean canScrollVertically() {
            return false;
        }

        /**
         * Scroll to the specified adapter position.
         *
         * Actual position of the item on the screen depends on the LayoutManager implementation.
         * @param position Scroll to this adapter position.
         */
        public void scrollToPosition(int position) {
            if (DEBUG) {
                Log.e(TAG, "You MUST implement scrollToPosition. It will soon become abstract");
            }
        }

        /**
         * <p>Smooth scroll to the specified adapter position.</p>
         * <p>To support smooth scrolling, override this method, create your {@link SmoothScroller}
         * instance and call {@link #startSmoothScroll(SmoothScroller)}.
         * </p>
         * @param recyclerView The RecyclerView to which this layout manager is attached
         * @param state    Current State of RecyclerView
         * @param position Scroll to this adapter position.
         */
        public void smoothScrollToPosition(RecyclerView recyclerView, State state,
                int position) {
            Log.e(TAG, "You must override smoothScrollToPosition to support smooth scrolling");
        }

        /**
         * Starts a smooth scroll using the provided {@link SmoothScroller}.
         *
         * <p>Each instance of SmoothScroller is intended to only be used once. Provide a new
         * SmoothScroller instance each time this method is called.
         *
         * <p>Calling this method will cancel any previous smooth scroll request.
         *
         * @param smoothScroller Instance which defines how smooth scroll should be animated
         */
        public void startSmoothScroll(SmoothScroller smoothScroller) {
            if (mSmoothScroller != null && smoothScroller != mSmoothScroller
                    && mSmoothScroller.isRunning()) {
                mSmoothScroller.stop();
            }
            mSmoothScroller = smoothScroller;
            mSmoothScroller.start(mRecyclerView, this);
        }

        /**
         * @return true if RecyclerView is currently in the state of smooth scrolling.
         */
        public boolean isSmoothScrolling() {
            return mSmoothScroller != null && mSmoothScroller.isRunning();
        }

        /**
         * Returns the resolved layout direction for this RecyclerView.
         *
         * @return {@link androidx.core.view.ViewCompat#LAYOUT_DIRECTION_RTL} if the layout
         * direction is RTL or returns
         * {@link androidx.core.view.ViewCompat#LAYOUT_DIRECTION_LTR} if the layout direction
         * is not RTL.
         */
        public int getLayoutDirection() {
            return ViewCompat.getLayoutDirection(mRecyclerView);
        }

        /**
         * Ends all animations on the view created by the {@link ItemAnimator}.
         *
         * @param view The View for which the animations should be ended.
         * @see RecyclerView.ItemAnimator#endAnimations()
         */
        public void endAnimation(View view) {
            if (mRecyclerView.mItemAnimator != null) {
                mRecyclerView.mItemAnimator.endAnimation(getChildViewHolderInt(view));
            }
        }

        /**
         * To be called only during {@link #onLayoutChildren(Recycler, State)} to add a view
         * to the layout that is known to be going away, either because it has been
         * {@link Adapter#notifyItemRemoved(int) removed} or because it is actually not in the
         * visible portion of the container but is being laid out in order to inform RecyclerView
         * in how to animate the item out of view.
         * <p>
         * Views added via this method are going to be invisible to LayoutManager after the
         * dispatchLayout pass is complete. They cannot be retrieved via {@link #getChildAt(int)}
         * or won't be included in {@link #getChildCount()} method.
         *
         * @param child View to add and then remove with animation.
         */
        public void addDisappearingView(View child) {
            addDisappearingView(child, -1);
        }

        /**
         * To be called only during {@link #onLayoutChildren(Recycler, State)} to add a view
         * to the layout that is known to be going away, either because it has been
         * {@link Adapter#notifyItemRemoved(int) removed} or because it is actually not in the
         * visible portion of the container but is being laid out in order to inform RecyclerView
         * in how to animate the item out of view.
         * <p>
         * Views added via this method are going to be invisible to LayoutManager after the
         * dispatchLayout pass is complete. They cannot be retrieved via {@link #getChildAt(int)}
         * or won't be included in {@link #getChildCount()} method.
         *
         * @param child View to add and then remove with animation.
         * @param index Index of the view.
         */
        public void addDisappearingView(View child, int index) {
            addViewInt(child, index, true);
        }

        /**
         * Add a view to the currently attached RecyclerView if needed. LayoutManagers should
         * use this method to add views obtained from a {@link Recycler} using
         * {@link Recycler#getViewForPosition(int)}.
         *
         * @param child View to add
         */
        public void addView(View child) {
            addView(child, -1);
        }

        /**
         * Add a view to the currently attached RecyclerView if needed. LayoutManagers should
         * use this method to add views obtained from a {@link Recycler} using
         * {@link Recycler#getViewForPosition(int)}.
         *
         * @param child View to add
         * @param index Index to add child at
         */
        public void addView(View child, int index) {
            addViewInt(child, index, false);
        }

        private void addViewInt(View child, int index, boolean disappearing) {
            final ViewHolder holder = getChildViewHolderInt(child);
            if (disappearing || holder.isRemoved()) {
                // these views will be hidden at the end of the layout pass.
                mRecyclerView.mViewInfoStore.addToDisappearedInLayout(holder);
            } else {
                // This may look like unnecessary but may happen if layout manager supports
                // predictive layouts and adapter removed then re-added the same item.
                // In this case, added version will be visible in the post layout (because add is
                // deferred) but RV will still bind it to the same View.
                // So if a View re-appears in post layout pass, remove it from disappearing list.
                mRecyclerView.mViewInfoStore.removeFromDisappearedInLayout(holder);
            }
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            if (holder.wasReturnedFromScrap() || holder.isScrap()) {
                if (holder.isScrap()) {
                    holder.unScrap();
                } else {
                    holder.clearReturnedFromScrapFlag();
                }
                mChildHelper.attachViewToParent(child, index, child.getLayoutParams(), false);
                if (DISPATCH_TEMP_DETACH) {
                    ViewCompat.dispatchFinishTemporaryDetach(child);
                }
            } else if (child.getParent() == mRecyclerView) { // it was not a scrap but a valid child
                // ensure in correct position
                int currentIndex = mChildHelper.indexOfChild(child);
                if (index == -1) {
                    index = mChildHelper.getChildCount();
                }
                if (currentIndex == -1) {
                    throw new IllegalStateException("Added View has RecyclerView as parent but"
                            + " view is not a real child. Unfiltered index:"
                            + mRecyclerView.indexOfChild(child) + mRecyclerView.exceptionLabel());
                }
                if (currentIndex != index) {
                    mRecyclerView.mLayout.moveView(currentIndex, index);
                }
            } else {
                mChildHelper.addView(child, index, false);
                lp.mInsetsDirty = true;
                if (mSmoothScroller != null && mSmoothScroller.isRunning()) {
                    mSmoothScroller.onChildAttachedToWindow(child);
                }
            }
            if (lp.mPendingInvalidate) {
                if (DEBUG) {
                    Log.d(TAG, "consuming pending invalidate on child " + lp.mViewHolder);
                }
                holder.itemView.invalidate();
                lp.mPendingInvalidate = false;
            }
        }

        /**
         * Remove a view from the currently attached RecyclerView if needed. LayoutManagers should
         * use this method to completely remove a child view that is no longer needed.
         * LayoutManagers should strongly consider recycling removed views using
         * {@link Recycler#recycleView(android.view.View)}.
         *
         * @param child View to remove
         */
        public void removeView(View child) {
            mChildHelper.removeView(child);
        }

        /**
         * Remove a view from the currently attached RecyclerView if needed. LayoutManagers should
         * use this method to completely remove a child view that is no longer needed.
         * LayoutManagers should strongly consider recycling removed views using
         * {@link Recycler#recycleView(android.view.View)}.
         *
         * @param index Index of the child view to remove
         */
        public void removeViewAt(int index) {
            final View child = getChildAt(index);
            if (child != null) {
                mChildHelper.removeViewAt(index);
            }
        }

        /**
         * Remove all views from the currently attached RecyclerView. This will not recycle
         * any of the affected views; the LayoutManager is responsible for doing so if desired.
         */
        public void removeAllViews() {
            // Only remove non-animating views
            final int childCount = getChildCount();
            for (int i = childCount - 1; i >= 0; i--) {
                mChildHelper.removeViewAt(i);
            }
        }

        /**
         * Returns offset of the RecyclerView's text baseline from the its top boundary.
         *
         * @return The offset of the RecyclerView's text baseline from the its top boundary; -1 if
         * there is no baseline.
         */
        public int getBaseline() {
            return -1;
        }

        /**
         * Returns the adapter position of the item represented by the given View. This does not
         * contain any adapter changes that might have happened after the last layout.
         *
         * @param view The view to query
         * @return The adapter position of the item which is rendered by this View.
         */
        public int getPosition(@NonNull View view) {
            return ((RecyclerView.LayoutParams) view.getLayoutParams()).getViewLayoutPosition();
        }

        /**
         * Returns the View type defined by the adapter.
         *
         * @param view The view to query
         * @return The type of the view assigned by the adapter.
         */
        public int getItemViewType(@NonNull View view) {
            return getChildViewHolderInt(view).getItemViewType();
        }

        /**
         * Traverses the ancestors of the given view and returns the item view that contains it
         * and also a direct child of the LayoutManager.
         * <p>
         * Note that this method may return null if the view is a child of the RecyclerView but
         * not a child of the LayoutManager (e.g. running a disappear animation).
         *
         * @param view The view that is a descendant of the LayoutManager.
         *
         * @return The direct child of the LayoutManager which contains the given view or null if
         * the provided view is not a descendant of this LayoutManager.
         *
         * @see RecyclerView#getChildViewHolder(View)
         * @see RecyclerView#findContainingViewHolder(View)
         */
        @Nullable
        public View findContainingItemView(@NonNull View view) {
            if (mRecyclerView == null) {
                return null;
            }
            View found = mRecyclerView.findContainingItemView(view);
            if (found == null) {
                return null;
            }
            if (mChildHelper.isHidden(found)) {
                return null;
            }
            return found;
        }

        /**
         * Finds the view which represents the given adapter position.
         * <p>
         * This method traverses each child since it has no information about child order.
         * Override this method to improve performance if your LayoutManager keeps data about
         * child views.
         * <p>
         * If a view is ignored via {@link #ignoreView(View)}, it is also ignored by this method.
         *
         * @param position Position of the item in adapter
         * @return The child view that represents the given position or null if the position is not
         * laid out
         */
        @Nullable
        public View findViewByPosition(int position) {
            final int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                ViewHolder vh = getChildViewHolderInt(child);
                if (vh == null) {
                    continue;
                }
                if (vh.getLayoutPosition() == position && !vh.shouldIgnore()
                        && (mRecyclerView.mState.isPreLayout() || !vh.isRemoved())) {
                    return child;
                }
            }
            return null;
        }

        /**
         * Temporarily detach a child view.
         *
         * <p>LayoutManagers may want to perform a lightweight detach operation to rearrange
         * views currently attached to the RecyclerView. Generally LayoutManager implementations
         * will want to use {@link #detachAndScrapView(android.view.View, RecyclerView.Recycler)}
         * so that the detached view may be rebound and reused.</p>
         *
         * <p>If a LayoutManager uses this method to detach a view, it <em>must</em>
         * {@link #attachView(android.view.View, int, RecyclerView.LayoutParams) reattach}
         * or {@link #removeDetachedView(android.view.View) fully remove} the detached view
         * before the LayoutManager entry point method called by RecyclerView returns.</p>
         *
         * @param child Child to detach
         */
        public void detachView(@NonNull View child) {
            final int ind = mChildHelper.indexOfChild(child);
            if (ind >= 0) {
                detachViewInternal(ind, child);
            }
        }

        /**
         * Temporarily detach a child view.
         *
         * <p>LayoutManagers may want to perform a lightweight detach operation to rearrange
         * views currently attached to the RecyclerView. Generally LayoutManager implementations
         * will want to use {@link #detachAndScrapView(android.view.View, RecyclerView.Recycler)}
         * so that the detached view may be rebound and reused.</p>
         *
         * <p>If a LayoutManager uses this method to detach a view, it <em>must</em>
         * {@link #attachView(android.view.View, int, RecyclerView.LayoutParams) reattach}
         * or {@link #removeDetachedView(android.view.View) fully remove} the detached view
         * before the LayoutManager entry point method called by RecyclerView returns.</p>
         *
         * @param index Index of the child to detach
         */
        public void detachViewAt(int index) {
            detachViewInternal(index, getChildAt(index));
        }

        private void detachViewInternal(int index, @NonNull View view) {
            if (DISPATCH_TEMP_DETACH) {
                ViewCompat.dispatchStartTemporaryDetach(view);
            }
            mChildHelper.detachViewFromParent(index);
        }

        /**
         * Reattach a previously {@link #detachView(android.view.View) detached} view.
         * This method should not be used to reattach views that were previously
         * {@link #detachAndScrapView(android.view.View, RecyclerView.Recycler)}  scrapped}.
         *
         * @param child Child to reattach
         * @param index Intended child index for child
         * @param lp LayoutParams for child
         */
        public void attachView(@NonNull View child, int index, LayoutParams lp) {
            ViewHolder vh = getChildViewHolderInt(child);
            if (vh.isRemoved()) {
                mRecyclerView.mViewInfoStore.addToDisappearedInLayout(vh);
            } else {
                mRecyclerView.mViewInfoStore.removeFromDisappearedInLayout(vh);
            }
            mChildHelper.attachViewToParent(child, index, lp, vh.isRemoved());
            if (DISPATCH_TEMP_DETACH)  {
                ViewCompat.dispatchFinishTemporaryDetach(child);
            }
        }

        /**
         * Reattach a previously {@link #detachView(android.view.View) detached} view.
         * This method should not be used to reattach views that were previously
         * {@link #detachAndScrapView(android.view.View, RecyclerView.Recycler)}  scrapped}.
         *
         * @param child Child to reattach
         * @param index Intended child index for child
         */
        public void attachView(@NonNull View child, int index) {
            attachView(child, index, (LayoutParams) child.getLayoutParams());
        }

        /**
         * Reattach a previously {@link #detachView(android.view.View) detached} view.
         * This method should not be used to reattach views that were previously
         * {@link #detachAndScrapView(android.view.View, RecyclerView.Recycler)}  scrapped}.
         *
         * @param child Child to reattach
         */
        public void attachView(@NonNull View child) {
            attachView(child, -1);
        }

        /**
         * Finish removing a view that was previously temporarily
         * {@link #detachView(android.view.View) detached}.
         *
         * @param child Detached child to remove
         */
        public void removeDetachedView(@NonNull View child) {
            mRecyclerView.removeDetachedView(child, false);
        }

        /**
         * Moves a View from one position to another.
         *
         * @param fromIndex The View's initial index
         * @param toIndex The View's target index
         */
        public void moveView(int fromIndex, int toIndex) {
            View view = getChildAt(fromIndex);
            if (view == null) {
                throw new IllegalArgumentException("Cannot move a child from non-existing index:"
                        + fromIndex + mRecyclerView.toString());
            }
            detachViewAt(fromIndex);
            attachView(view, toIndex);
        }

        /**
         * Detach a child view and add it to a {@link Recycler Recycler's} scrap heap.
         *
         * <p>Scrapping a view allows it to be rebound and reused to show updated or
         * different data.</p>
         *
         * @param child Child to detach and scrap
         * @param recycler Recycler to deposit the new scrap view into
         */
        public void detachAndScrapView(@NonNull View child, @NonNull Recycler recycler) {
            int index = mChildHelper.indexOfChild(child);
            scrapOrRecycleView(recycler, index, child);
        }

        /**
         * Detach a child view and add it to a {@link Recycler Recycler's} scrap heap.
         *
         * <p>Scrapping a view allows it to be rebound and reused to show updated or
         * different data.</p>
         *
         * @param index Index of child to detach and scrap
         * @param recycler Recycler to deposit the new scrap view into
         */
        public void detachAndScrapViewAt(int index, @NonNull Recycler recycler) {
            final View child = getChildAt(index);
            scrapOrRecycleView(recycler, index, child);
        }

        /**
         * Remove a child view and recycle it using the given Recycler.
         *
         * @param child Child to remove and recycle
         * @param recycler Recycler to use to recycle child
         */
        public void removeAndRecycleView(@NonNull View child, @NonNull Recycler recycler) {
            removeView(child);
            recycler.recycleView(child);
        }

        /**
         * Remove a child view and recycle it using the given Recycler.
         *
         * @param index Index of child to remove and recycle
         * @param recycler Recycler to use to recycle child
         */
        public void removeAndRecycleViewAt(int index, @NonNull Recycler recycler) {
            final View view = getChildAt(index);
            removeViewAt(index);
            recycler.recycleView(view);
        }

        /**
         * Return the current number of child views attached to the parent RecyclerView.
         * This does not include child views that were temporarily detached and/or scrapped.
         *
         * @return Number of attached children
         */
        public int getChildCount() {
            return mChildHelper != null ? mChildHelper.getChildCount() : 0;
        }

        /**
         * Return the child view at the given index
         * @param index Index of child to return
         * @return Child view at index
         */
        @Nullable
        public View getChildAt(int index) {
            return mChildHelper != null ? mChildHelper.getChildAt(index) : null;
        }

        /**
         * Return the width measurement spec mode that is currently relevant to the LayoutManager.
         *
         * <p>This value is set only if the LayoutManager opts into the AutoMeasure api via
         * {@link #setAutoMeasureEnabled(boolean)}.
         *
         * <p>When RecyclerView is running a layout, this value is always set to
         * {@link View.MeasureSpec#EXACTLY} even if it was measured with a different spec mode.
         *
         * @return Width measure spec mode
         *
         * @see View.MeasureSpec#getMode(int)
         */
        public int getWidthMode() {
            return mWidthMode;
        }

        /**
         * Return the height measurement spec mode that is currently relevant to the LayoutManager.
         *
         * <p>This value is set only if the LayoutManager opts into the AutoMeasure api via
         * {@link #setAutoMeasureEnabled(boolean)}.
         *
         * <p>When RecyclerView is running a layout, this value is always set to
         * {@link View.MeasureSpec#EXACTLY} even if it was measured with a different spec mode.
         *
         * @return Height measure spec mode
         *
         * @see View.MeasureSpec#getMode(int)
         */
        public int getHeightMode() {
            return mHeightMode;
        }

        /**
         * Returns the width that is currently relevant to the LayoutManager.
         *
         * <p>This value is usually equal to the laid out width of the {@link RecyclerView} but may
         * reflect the current {@link android.view.View.MeasureSpec} width if the
         * {@link LayoutManager} is using AutoMeasure and the RecyclerView is in the process of
         * measuring. The LayoutManager must always use this method to retrieve the width relevant
         * to it at any given time.
         *
         * @return Width in pixels
         */
        @Px
        public int getWidth() {
            return mWidth;
        }

        /**
         * Returns the height that is currently relevant to the LayoutManager.
         *
         * <p>This value is usually equal to the laid out height of the {@link RecyclerView} but may
         * reflect the current {@link android.view.View.MeasureSpec} height if the
         * {@link LayoutManager} is using AutoMeasure and the RecyclerView is in the process of
         * measuring. The LayoutManager must always use this method to retrieve the height relevant
         * to it at any given time.
         *
         * @return Height in pixels
         */
        @Px
        public int getHeight() {
            return mHeight;
        }

        /**
         * Return the left padding of the parent RecyclerView
         *
         * @return Padding in pixels
         */
        @Px
        public int getPaddingLeft() {
            return mRecyclerView != null ? mRecyclerView.getPaddingLeft() : 0;
        }

        /**
         * Return the top padding of the parent RecyclerView
         *
         * @return Padding in pixels
         */
        @Px
        public int getPaddingTop() {
            return mRecyclerView != null ? mRecyclerView.getPaddingTop() : 0;
        }

        /**
         * Return the right padding of the parent RecyclerView
         *
         * @return Padding in pixels
         */
        @Px
        public int getPaddingRight() {
            return mRecyclerView != null ? mRecyclerView.getPaddingRight() : 0;
        }

        /**
         * Return the bottom padding of the parent RecyclerView
         *
         * @return Padding in pixels
         */
        @Px
        public int getPaddingBottom() {
            return mRecyclerView != null ? mRecyclerView.getPaddingBottom() : 0;
        }

        /**
         * Return the start padding of the parent RecyclerView
         *
         * @return Padding in pixels
         */
        @Px
        public int getPaddingStart() {
            return mRecyclerView != null ? ViewCompat.getPaddingStart(mRecyclerView) : 0;
        }

        /**
         * Return the end padding of the parent RecyclerView
         *
         * @return Padding in pixels
         */
        @Px
        public int getPaddingEnd() {
            return mRecyclerView != null ? ViewCompat.getPaddingEnd(mRecyclerView) : 0;
        }

        /**
         * Returns true if the RecyclerView this LayoutManager is bound to has focus.
         *
         * @return True if the RecyclerView has focus, false otherwise.
         * @see View#isFocused()
         */
        public boolean isFocused() {
            return mRecyclerView != null && mRecyclerView.isFocused();
        }

        /**
         * Returns true if the RecyclerView this LayoutManager is bound to has or contains focus.
         *
         * @return true if the RecyclerView has or contains focus
         * @see View#hasFocus()
         */
        public boolean hasFocus() {
            return mRecyclerView != null && mRecyclerView.hasFocus();
        }

        /**
         * Returns the item View which has or contains focus.
         *
         * @return A direct child of RecyclerView which has focus or contains the focused child.
         */
        @Nullable
        public View getFocusedChild() {
            if (mRecyclerView == null) {
                return null;
            }
            final View focused = mRecyclerView.getFocusedChild();
            if (focused == null || mChildHelper.isHidden(focused)) {
                return null;
            }
            return focused;
        }

        /**
         * Returns the number of items in the adapter bound to the parent RecyclerView.
         * <p>
         * Note that this number is not necessarily equal to
         * {@link State#getItemCount() State#getItemCount()}. In methods where {@link State} is
         * available, you should use {@link State#getItemCount() State#getItemCount()} instead.
         * For more details, check the documentation for
         * {@link State#getItemCount() State#getItemCount()}.
         *
         * @return The number of items in the bound adapter
         * @see State#getItemCount()
         */
        public int getItemCount() {
            final Adapter a = mRecyclerView != null ? mRecyclerView.getAdapter() : null;
            return a != null ? a.getItemCount() : 0;
        }

        /**
         * Offset all child views attached to the parent RecyclerView by dx pixels along
         * the horizontal axis.
         *
         * @param dx Pixels to offset by
         */
        public void offsetChildrenHorizontal(@Px int dx) {
            if (mRecyclerView != null) {
                mRecyclerView.offsetChildrenHorizontal(dx);
            }
        }

        /**
         * Offset all child views attached to the parent RecyclerView by dy pixels along
         * the vertical axis.
         *
         * @param dy Pixels to offset by
         */
        public void offsetChildrenVertical(@Px int dy) {
            if (mRecyclerView != null) {
                mRecyclerView.offsetChildrenVertical(dy);
            }
        }

        /**
         * Flags a view so that it will not be scrapped or recycled.
         * <p>
         * Scope of ignoring a child is strictly restricted to position tracking, scrapping and
         * recyling. Methods like {@link #removeAndRecycleAllViews(Recycler)} will ignore the child
         * whereas {@link #removeAllViews()} or {@link #offsetChildrenHorizontal(int)} will not
         * ignore the child.
         * <p>
         * Before this child can be recycled again, you have to call
         * {@link #stopIgnoringView(View)}.
         * <p>
         * You can call this method only if your LayoutManger is in onLayout or onScroll callback.
         *
         * @param view View to ignore.
         * @see #stopIgnoringView(View)
         */
        public void ignoreView(@NonNull View view) {
            if (view.getParent() != mRecyclerView || mRecyclerView.indexOfChild(view) == -1) {
                // checking this because calling this method on a recycled or detached view may
                // cause loss of state.
                throw new IllegalArgumentException("View should be fully attached to be ignored"
                        + mRecyclerView.exceptionLabel());
            }
            final ViewHolder vh = getChildViewHolderInt(view);
            vh.addFlags(ViewHolder.FLAG_IGNORE);
            mRecyclerView.mViewInfoStore.removeViewHolder(vh);
        }

        /**
         * View can be scrapped and recycled again.
         * <p>
         * Note that calling this method removes all information in the view holder.
         * <p>
         * You can call this method only if your LayoutManger is in onLayout or onScroll callback.
         *
         * @param view View to ignore.
         */
        public void stopIgnoringView(@NonNull View view) {
            final ViewHolder vh = getChildViewHolderInt(view);
            vh.stopIgnoring();
            vh.resetInternal();
            vh.addFlags(ViewHolder.FLAG_INVALID);
        }

        /**
         * Temporarily detach and scrap all currently attached child views. Views will be scrapped
         * into the given Recycler. The Recycler may prefer to reuse scrap views before
         * other views that were previously recycled.
         *
         * @param recycler Recycler to scrap views into
         */
        public void detachAndScrapAttachedViews(@NonNull Recycler recycler) {
            final int childCount = getChildCount();
            for (int i = childCount - 1; i >= 0; i--) {
                final View v = getChildAt(i);
                scrapOrRecycleView(recycler, i, v);
            }
        }

        private void scrapOrRecycleView(Recycler recycler, int index, View view) {
            final ViewHolder viewHolder = getChildViewHolderInt(view);
            if (viewHolder.shouldIgnore()) {
                if (DEBUG) {
                    Log.d(TAG, "ignoring view " + viewHolder);
                }
                return;
            }
            if (viewHolder.isInvalid() && !viewHolder.isRemoved()
                    && !mRecyclerView.mAdapter.hasStableIds()) {
                removeViewAt(index);
                recycler.recycleViewHolderInternal(viewHolder);
            } else {
                detachViewAt(index);
                recycler.scrapView(view);
                mRecyclerView.mViewInfoStore.onViewDetached(viewHolder);
            }
        }

        /**
         * Recycles the scrapped views.
         * <p>
         * When a view is detached and removed, it does not trigger a ViewGroup invalidate. This is
         * the expected behavior if scrapped views are used for animations. Otherwise, we need to
         * call remove and invalidate RecyclerView to ensure UI update.
         *
         * @param recycler Recycler
         */
        void removeAndRecycleScrapInt(Recycler recycler) {
            final int scrapCount = recycler.getScrapCount();
            // Loop backward, recycler might be changed by removeDetachedView()
            for (int i = scrapCount - 1; i >= 0; i--) {
                final View scrap = recycler.getScrapViewAt(i);
                final ViewHolder vh = getChildViewHolderInt(scrap);
                if (vh.shouldIgnore()) {
                    continue;
                }
                // If the scrap view is animating, we need to cancel them first. If we cancel it
                // here, ItemAnimator callback may recycle it which will cause double recycling.
                // To avoid this, we mark it as not recycleable before calling the item animator.
                // Since removeDetachedView calls a user API, a common mistake (ending animations on
                // the view) may recycle it too, so we guard it before we call user APIs.
                vh.setIsRecyclable(false);
                if (vh.isTmpDetached()) {
                    mRecyclerView.removeDetachedView(scrap, false);
                }
                if (mRecyclerView.mItemAnimator != null) {
                    mRecyclerView.mItemAnimator.endAnimation(vh);
                }
                vh.setIsRecyclable(true);
                recycler.quickRecycleScrapView(scrap);
            }
            recycler.clearScrap();
            if (scrapCount > 0) {
                mRecyclerView.invalidate();
            }
        }


        /**
         * Measure a child view using standard measurement policy, taking the padding
         * of the parent RecyclerView and any added item decorations into account.
         *
         * <p>If the RecyclerView can be scrolled in either dimension the caller may
         * pass 0 as the widthUsed or heightUsed parameters as they will be irrelevant.</p>
         *
         * @param child Child view to measure
         * @param widthUsed Width in pixels currently consumed by other views, if relevant
         * @param heightUsed Height in pixels currently consumed by other views, if relevant
         */
        public void measureChild(@NonNull View child, int widthUsed, int heightUsed) {
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            final Rect insets = mRecyclerView.getItemDecorInsetsForChild(child);
            widthUsed += insets.left + insets.right;
            heightUsed += insets.top + insets.bottom;
            final int widthSpec = getChildMeasureSpec(getWidth(), getWidthMode(),
                    getPaddingLeft() + getPaddingRight() + widthUsed, lp.width,
                    canScrollHorizontally());
            final int heightSpec = getChildMeasureSpec(getHeight(), getHeightMode(),
                    getPaddingTop() + getPaddingBottom() + heightUsed, lp.height,
                    canScrollVertically());
            if (shouldMeasureChild(child, widthSpec, heightSpec, lp)) {
                child.measure(widthSpec, heightSpec);
            }
        }

        /**
         * RecyclerView internally does its own View measurement caching which should help with
         * WRAP_CONTENT.
         * <p>
         * Use this method if the View is already measured once in this layout pass.
         */
        boolean shouldReMeasureChild(View child, int widthSpec, int heightSpec, LayoutParams lp) {
            return !mMeasurementCacheEnabled
                    || !isMeasurementUpToDate(child.getMeasuredWidth(), widthSpec, lp.width)
                    || !isMeasurementUpToDate(child.getMeasuredHeight(), heightSpec, lp.height);
        }

        // we may consider making this public
        /**
         * RecyclerView internally does its own View measurement caching which should help with
         * WRAP_CONTENT.
         * <p>
         * Use this method if the View is not yet measured and you need to decide whether to
         * measure this View or not.
         */
        boolean shouldMeasureChild(View child, int widthSpec, int heightSpec, LayoutParams lp) {
            return child.isLayoutRequested()
                    || !mMeasurementCacheEnabled
                    || !isMeasurementUpToDate(child.getWidth(), widthSpec, lp.width)
                    || !isMeasurementUpToDate(child.getHeight(), heightSpec, lp.height);
        }

        /**
         * In addition to the View Framework's measurement cache, RecyclerView uses its own
         * additional measurement cache for its children to avoid re-measuring them when not
         * necessary. It is on by default but it can be turned off via
         * {@link #setMeasurementCacheEnabled(boolean)}.
         *
         * @return True if measurement cache is enabled, false otherwise.
         *
         * @see #setMeasurementCacheEnabled(boolean)
         */
        public boolean isMeasurementCacheEnabled() {
            return mMeasurementCacheEnabled;
        }

        /**
         * Sets whether RecyclerView should use its own measurement cache for the children. This is
         * a more aggressive cache than the framework uses.
         *
         * @param measurementCacheEnabled True to enable the measurement cache, false otherwise.
         *
         * @see #isMeasurementCacheEnabled()
         */
        public void setMeasurementCacheEnabled(boolean measurementCacheEnabled) {
            mMeasurementCacheEnabled = measurementCacheEnabled;
        }

        private static boolean isMeasurementUpToDate(int childSize, int spec, int dimension) {
            final int specMode = MeasureSpec.getMode(spec);
            final int specSize = MeasureSpec.getSize(spec);
            if (dimension > 0 && childSize != dimension) {
                return false;
            }
            switch (specMode) {
                case MeasureSpec.UNSPECIFIED:
                    return true;
                case MeasureSpec.AT_MOST:
                    return specSize >= childSize;
                case MeasureSpec.EXACTLY:
                    return  specSize == childSize;
            }
            return false;
        }

        /**
         * Measure a child view using standard measurement policy, taking the padding
         * of the parent RecyclerView, any added item decorations and the child margins
         * into account.
         *
         * <p>If the RecyclerView can be scrolled in either dimension the caller may
         * pass 0 as the widthUsed or heightUsed parameters as they will be irrelevant.</p>
         *
         * @param child Child view to measure
         * @param widthUsed Width in pixels currently consumed by other views, if relevant
         * @param heightUsed Height in pixels currently consumed by other views, if relevant
         */
        public void measureChildWithMargins(@NonNull View child, int widthUsed, int heightUsed) {
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            final Rect insets = mRecyclerView.getItemDecorInsetsForChild(child);
            widthUsed += insets.left + insets.right;
            heightUsed += insets.top + insets.bottom;

            final int widthSpec = getChildMeasureSpec(getWidth(), getWidthMode(),
                    getPaddingLeft() + getPaddingRight()
                            + lp.leftMargin + lp.rightMargin + widthUsed, lp.width,
                    canScrollHorizontally());
            final int heightSpec = getChildMeasureSpec(getHeight(), getHeightMode(),
                    getPaddingTop() + getPaddingBottom()
                            + lp.topMargin + lp.bottomMargin + heightUsed, lp.height,
                    canScrollVertically());
            if (shouldMeasureChild(child, widthSpec, heightSpec, lp)) {
                child.measure(widthSpec, heightSpec);
            }
        }

        /**
         * Calculate a MeasureSpec value for measuring a child view in one dimension.
         *
         * @param parentSize Size of the parent view where the child will be placed
         * @param padding Total space currently consumed by other elements of the parent
         * @param childDimension Desired size of the child view, or MATCH_PARENT/WRAP_CONTENT.
         *                       Generally obtained from the child view's LayoutParams
         * @param canScroll true if the parent RecyclerView can scroll in this dimension
         *
         * @return a MeasureSpec value for the child view
         * @deprecated use {@link #getChildMeasureSpec(int, int, int, int, boolean)}
         */
        @Deprecated
        public static int getChildMeasureSpec(int parentSize, int padding, int childDimension,
                boolean canScroll) {
            int size = Math.max(0, parentSize - padding);
            int resultSize = 0;
            int resultMode = 0;
            if (canScroll) {
                if (childDimension >= 0) {
                    resultSize = childDimension;
                    resultMode = MeasureSpec.EXACTLY;
                } else {
                    // MATCH_PARENT can't be applied since we can scroll in this dimension, wrap
                    // instead using UNSPECIFIED.
                    resultSize = 0;
                    resultMode = MeasureSpec.UNSPECIFIED;
                }
            } else {
                if (childDimension >= 0) {
                    resultSize = childDimension;
                    resultMode = MeasureSpec.EXACTLY;
                } else if (childDimension == LayoutParams.MATCH_PARENT) {
                    resultSize = size;
                    // TODO this should be my spec.
                    resultMode = MeasureSpec.EXACTLY;
                } else if (childDimension == LayoutParams.WRAP_CONTENT) {
                    resultSize = size;
                    resultMode = MeasureSpec.AT_MOST;
                }
            }
            return MeasureSpec.makeMeasureSpec(resultSize, resultMode);
        }

        /**
         * Calculate a MeasureSpec value for measuring a child view in one dimension.
         *
         * @param parentSize Size of the parent view where the child will be placed
         * @param parentMode The measurement spec mode of the parent
         * @param padding Total space currently consumed by other elements of parent
         * @param childDimension Desired size of the child view, or MATCH_PARENT/WRAP_CONTENT.
         *                       Generally obtained from the child view's LayoutParams
         * @param canScroll true if the parent RecyclerView can scroll in this dimension
         *
         * @return a MeasureSpec value for the child view
         */
        public static int getChildMeasureSpec(int parentSize, int parentMode, int padding,
                int childDimension, boolean canScroll) {
            int size = Math.max(0, parentSize - padding);
            int resultSize = 0;
            int resultMode = 0;
            if (canScroll) {
                if (childDimension >= 0) {
                    resultSize = childDimension;
                    resultMode = MeasureSpec.EXACTLY;
                } else if (childDimension == LayoutParams.MATCH_PARENT) {
                    switch (parentMode) {
                        case MeasureSpec.AT_MOST:
                        case MeasureSpec.EXACTLY:
                            resultSize = size;
                            resultMode = parentMode;
                            break;
                        case MeasureSpec.UNSPECIFIED:
                            resultSize = 0;
                            resultMode = MeasureSpec.UNSPECIFIED;
                            break;
                    }
                } else if (childDimension == LayoutParams.WRAP_CONTENT) {
                    resultSize = 0;
                    resultMode = MeasureSpec.UNSPECIFIED;
                }
            } else {
                if (childDimension >= 0) {
                    resultSize = childDimension;
                    resultMode = MeasureSpec.EXACTLY;
                } else if (childDimension == LayoutParams.MATCH_PARENT) {
                    resultSize = size;
                    resultMode = parentMode;
                } else if (childDimension == LayoutParams.WRAP_CONTENT) {
                    resultSize = size;
                    if (parentMode == MeasureSpec.AT_MOST || parentMode == MeasureSpec.EXACTLY) {
                        resultMode = MeasureSpec.AT_MOST;
                    } else {
                        resultMode = MeasureSpec.UNSPECIFIED;
                    }

                }
            }
            //noinspection WrongConstant
            return MeasureSpec.makeMeasureSpec(resultSize, resultMode);
        }

        /**
         * Returns the measured width of the given child, plus the additional size of
         * any insets applied by {@link ItemDecoration ItemDecorations}.
         *
         * @param child Child view to query
         * @return child's measured width plus <code>ItemDecoration</code> insets
         *
         * @see View#getMeasuredWidth()
         */
        public int getDecoratedMeasuredWidth(@NonNull View child) {
            final Rect insets = ((LayoutParams) child.getLayoutParams()).mDecorInsets;
            return child.getMeasuredWidth() + insets.left + insets.right;
        }

        /**
         * Returns the measured height of the given child, plus the additional size of
         * any insets applied by {@link ItemDecoration ItemDecorations}.
         *
         * @param child Child view to query
         * @return child's measured height plus <code>ItemDecoration</code> insets
         *
         * @see View#getMeasuredHeight()
         */
        public int getDecoratedMeasuredHeight(@NonNull View child) {
            final Rect insets = ((LayoutParams) child.getLayoutParams()).mDecorInsets;
            return child.getMeasuredHeight() + insets.top + insets.bottom;
        }

        /**
         * Lay out the given child view within the RecyclerView using coordinates that
         * include any current {@link ItemDecoration ItemDecorations}.
         *
         * <p>LayoutManagers should prefer working in sizes and coordinates that include
         * item decoration insets whenever possible. This allows the LayoutManager to effectively
         * ignore decoration insets within measurement and layout code. See the following
         * methods:</p>
         * <ul>
         *     <li>{@link #layoutDecoratedWithMargins(View, int, int, int, int)}</li>
         *     <li>{@link #getDecoratedBoundsWithMargins(View, Rect)}</li>
         *     <li>{@link #measureChild(View, int, int)}</li>
         *     <li>{@link #measureChildWithMargins(View, int, int)}</li>
         *     <li>{@link #getDecoratedLeft(View)}</li>
         *     <li>{@link #getDecoratedTop(View)}</li>
         *     <li>{@link #getDecoratedRight(View)}</li>
         *     <li>{@link #getDecoratedBottom(View)}</li>
         *     <li>{@link #getDecoratedMeasuredWidth(View)}</li>
         *     <li>{@link #getDecoratedMeasuredHeight(View)}</li>
         * </ul>
         *
         * @param child Child to lay out
         * @param left Left edge, with item decoration insets included
         * @param top Top edge, with item decoration insets included
         * @param right Right edge, with item decoration insets included
         * @param bottom Bottom edge, with item decoration insets included
         *
         * @see View#layout(int, int, int, int)
         * @see #layoutDecoratedWithMargins(View, int, int, int, int)
         */
        public void layoutDecorated(@NonNull View child, int left, int top, int right, int bottom) {
            final Rect insets = ((LayoutParams) child.getLayoutParams()).mDecorInsets;
            child.layout(left + insets.left, top + insets.top, right - insets.right,
                    bottom - insets.bottom);
        }

        /**
         * Lay out the given child view within the RecyclerView using coordinates that
         * include any current {@link ItemDecoration ItemDecorations} and margins.
         *
         * <p>LayoutManagers should prefer working in sizes and coordinates that include
         * item decoration insets whenever possible. This allows the LayoutManager to effectively
         * ignore decoration insets within measurement and layout code. See the following
         * methods:</p>
         * <ul>
         *     <li>{@link #layoutDecorated(View, int, int, int, int)}</li>
         *     <li>{@link #measureChild(View, int, int)}</li>
         *     <li>{@link #measureChildWithMargins(View, int, int)}</li>
         *     <li>{@link #getDecoratedLeft(View)}</li>
         *     <li>{@link #getDecoratedTop(View)}</li>
         *     <li>{@link #getDecoratedRight(View)}</li>
         *     <li>{@link #getDecoratedBottom(View)}</li>
         *     <li>{@link #getDecoratedMeasuredWidth(View)}</li>
         *     <li>{@link #getDecoratedMeasuredHeight(View)}</li>
         * </ul>
         *
         * @param child Child to lay out
         * @param left Left edge, with item decoration insets and left margin included
         * @param top Top edge, with item decoration insets and top margin included
         * @param right Right edge, with item decoration insets and right margin included
         * @param bottom Bottom edge, with item decoration insets and bottom margin included
         *
         * @see View#layout(int, int, int, int)
         * @see #layoutDecorated(View, int, int, int, int)
         */
        public void layoutDecoratedWithMargins(@NonNull View child, int left, int top, int right,
                int bottom) {
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();
            final Rect insets = lp.mDecorInsets;
            child.layout(left + insets.left + lp.leftMargin, top + insets.top + lp.topMargin,
                    right - insets.right - lp.rightMargin,
                    bottom - insets.bottom - lp.bottomMargin);
        }

        /**
         * Calculates the bounding box of the View while taking into account its matrix changes
         * (translation, scale etc) with respect to the RecyclerView.
         * <p>
         * If {@code includeDecorInsets} is {@code true}, they are applied first before applying
         * the View's matrix so that the decor offsets also go through the same transformation.
         *
         * @param child The ItemView whose bounding box should be calculated.
         * @param includeDecorInsets True if the decor insets should be included in the bounding box
         * @param out The rectangle into which the output will be written.
         */
        public void getTransformedBoundingBox(@NonNull View child, boolean includeDecorInsets,
                @NonNull Rect out) {
            if (includeDecorInsets) {
                Rect insets = ((LayoutParams) child.getLayoutParams()).mDecorInsets;
                out.set(-insets.left, -insets.top,
                        child.getWidth() + insets.right, child.getHeight() + insets.bottom);
            } else {
                out.set(0, 0, child.getWidth(), child.getHeight());
            }

            if (mRecyclerView != null) {
                final Matrix childMatrix = child.getMatrix();
                if (childMatrix != null && !childMatrix.isIdentity()) {
                    final RectF tempRectF = mRecyclerView.mTempRectF;
                    tempRectF.set(out);
                    childMatrix.mapRect(tempRectF);
                    out.set(
                            (int) Math.floor(tempRectF.left),
                            (int) Math.floor(tempRectF.top),
                            (int) Math.ceil(tempRectF.right),
                            (int) Math.ceil(tempRectF.bottom)
                    );
                }
            }
            out.offset(child.getLeft(), child.getTop());
        }

        /**
         * Returns the bounds of the view including its decoration and margins.
         *
         * @param view The view element to check
         * @param outBounds A rect that will receive the bounds of the element including its
         *                  decoration and margins.
         */
        public void getDecoratedBoundsWithMargins(@NonNull View view, @NonNull Rect outBounds) {
            RecyclerView.getDecoratedBoundsWithMarginsInt(view, outBounds);
        }

        /**
         * Returns the left edge of the given child view within its parent, offset by any applied
         * {@link ItemDecoration ItemDecorations}.
         *
         * @param child Child to query
         * @return Child left edge with offsets applied
         * @see #getLeftDecorationWidth(View)
         */
        public int getDecoratedLeft(@NonNull View child) {
            return child.getLeft() - getLeftDecorationWidth(child);
        }

        /**
         * Returns the top edge of the given child view within its parent, offset by any applied
         * {@link ItemDecoration ItemDecorations}.
         *
         * @param child Child to query
         * @return Child top edge with offsets applied
         * @see #getTopDecorationHeight(View)
         */
        public int getDecoratedTop(@NonNull View child) {
            return child.getTop() - getTopDecorationHeight(child);
        }

        /**
         * Returns the right edge of the given child view within its parent, offset by any applied
         * {@link ItemDecoration ItemDecorations}.
         *
         * @param child Child to query
         * @return Child right edge with offsets applied
         * @see #getRightDecorationWidth(View)
         */
        public int getDecoratedRight(@NonNull View child) {
            return child.getRight() + getRightDecorationWidth(child);
        }

        /**
         * Returns the bottom edge of the given child view within its parent, offset by any applied
         * {@link ItemDecoration ItemDecorations}.
         *
         * @param child Child to query
         * @return Child bottom edge with offsets applied
         * @see #getBottomDecorationHeight(View)
         */
        public int getDecoratedBottom(@NonNull View child) {
            return child.getBottom() + getBottomDecorationHeight(child);
        }

        /**
         * Calculates the item decor insets applied to the given child and updates the provided
         * Rect instance with the inset values.
         * <ul>
         *     <li>The Rect's left is set to the total width of left decorations.</li>
         *     <li>The Rect's top is set to the total height of top decorations.</li>
         *     <li>The Rect's right is set to the total width of right decorations.</li>
         *     <li>The Rect's bottom is set to total height of bottom decorations.</li>
         * </ul>
         * <p>
         * Note that item decorations are automatically calculated when one of the LayoutManager's
         * measure child methods is called. If you need to measure the child with custom specs via
         * {@link View#measure(int, int)}, you can use this method to get decorations.
         *
         * @param child The child view whose decorations should be calculated
         * @param outRect The Rect to hold result values
         */
        public void calculateItemDecorationsForChild(@NonNull View child, @NonNull Rect outRect) {
            if (mRecyclerView == null) {
                outRect.set(0, 0, 0, 0);
                return;
            }
            Rect insets = mRecyclerView.getItemDecorInsetsForChild(child);
            outRect.set(insets);
        }

        /**
         * Returns the total height of item decorations applied to child's top.
         * <p>
         * Note that this value is not updated until the View is measured or
         * {@link #calculateItemDecorationsForChild(View, Rect)} is called.
         *
         * @param child Child to query
         * @return The total height of item decorations applied to the child's top.
         * @see #getDecoratedTop(View)
         * @see #calculateItemDecorationsForChild(View, Rect)
         */
        public int getTopDecorationHeight(@NonNull View child) {
            return ((LayoutParams) child.getLayoutParams()).mDecorInsets.top;
        }

        /**
         * Returns the total height of item decorations applied to child's bottom.
         * <p>
         * Note that this value is not updated until the View is measured or
         * {@link #calculateItemDecorationsForChild(View, Rect)} is called.
         *
         * @param child Child to query
         * @return The total height of item decorations applied to the child's bottom.
         * @see #getDecoratedBottom(View)
         * @see #calculateItemDecorationsForChild(View, Rect)
         */
        public int getBottomDecorationHeight(@NonNull View child) {
            return ((LayoutParams) child.getLayoutParams()).mDecorInsets.bottom;
        }

        /**
         * Returns the total width of item decorations applied to child's left.
         * <p>
         * Note that this value is not updated until the View is measured or
         * {@link #calculateItemDecorationsForChild(View, Rect)} is called.
         *
         * @param child Child to query
         * @return The total width of item decorations applied to the child's left.
         * @see #getDecoratedLeft(View)
         * @see #calculateItemDecorationsForChild(View, Rect)
         */
        public int getLeftDecorationWidth(@NonNull View child) {
            return ((LayoutParams) child.getLayoutParams()).mDecorInsets.left;
        }

        /**
         * Returns the total width of item decorations applied to child's right.
         * <p>
         * Note that this value is not updated until the View is measured or
         * {@link #calculateItemDecorationsForChild(View, Rect)} is called.
         *
         * @param child Child to query
         * @return The total width of item decorations applied to the child's right.
         * @see #getDecoratedRight(View)
         * @see #calculateItemDecorationsForChild(View, Rect)
         */
        public int getRightDecorationWidth(@NonNull View child) {
            return ((LayoutParams) child.getLayoutParams()).mDecorInsets.right;
        }

        /**
         * Called when searching for a focusable view in the given direction has failed
         * for the current content of the RecyclerView.
         *
         * <p>This is the LayoutManager's opportunity to populate views in the given direction
         * to fulfill the request if it can. The LayoutManager should attach and return
         * the view to be focused, if a focusable view in the given direction is found.
         * Otherwise, if all the existing (or the newly populated views) are unfocusable, it returns
         * the next unfocusable view to become visible on the screen. This unfocusable view is
         * typically the first view that's either partially or fully out of RV's padded bounded
         * area in the given direction. The default implementation returns null.</p>
         *
         * @param focused   The currently focused view
         * @param direction One of {@link View#FOCUS_UP}, {@link View#FOCUS_DOWN},
         *                  {@link View#FOCUS_LEFT}, {@link View#FOCUS_RIGHT},
         *                  {@link View#FOCUS_BACKWARD}, {@link View#FOCUS_FORWARD}
         *                  or 0 for not applicable
         * @param recycler  The recycler to use for obtaining views for currently offscreen items
         * @param state     Transient state of RecyclerView
         * @return The chosen view to be focused if a focusable view is found, otherwise an
         * unfocusable view to become visible onto the screen, else null.
         */
        @Nullable
        public View onFocusSearchFailed(@NonNull View focused, int direction,
                @NonNull Recycler recycler, @NonNull State state) {
            return null;
        }

        /**
         * This method gives a LayoutManager an opportunity to intercept the initial focus search
         * before the default behavior of {@link FocusFinder} is used. If this method returns
         * null FocusFinder will attempt to find a focusable child view. If it fails
         * then {@link #onFocusSearchFailed(View, int, RecyclerView.Recycler, RecyclerView.State)}
         * will be called to give the LayoutManager an opportunity to add new views for items
         * that did not have attached views representing them. The LayoutManager should not add
         * or remove views from this method.
         *
         * @param focused The currently focused view
         * @param direction One of {@link View#FOCUS_UP}, {@link View#FOCUS_DOWN},
         *                  {@link View#FOCUS_LEFT}, {@link View#FOCUS_RIGHT},
         *                  {@link View#FOCUS_BACKWARD}, {@link View#FOCUS_FORWARD}
         * @return A descendant view to focus or null to fall back to default behavior.
         *         The default implementation returns null.
         */
        @Nullable
        public View onInterceptFocusSearch(@NonNull View focused, int direction) {
            return null;
        }

        /**
         * Returns the scroll amount that brings the given rect in child's coordinate system within
         * the padded area of RecyclerView.
         * @param child The direct child making the request.
         * @param rect The rectangle in the child's coordinates the child
         *             wishes to be on the screen.
         * @return The array containing the scroll amount in x and y directions that brings the
         * given rect into RV's padded area.
         */
        protected int[] getChildRectangleOnScreenScrollAmount(View child, Rect rect) {
            int[] out = new int[2];
            final int parentLeft = getPaddingLeft();
            final int parentTop = getPaddingTop();
            final int parentRight = getWidth() - getPaddingRight();
            final int parentBottom = getHeight() - getPaddingBottom();
            final int childLeft = child.getLeft() + rect.left - child.getScrollX();
            final int childTop = child.getTop() + rect.top - child.getScrollY();
            final int childRight = childLeft + rect.width();
            final int childBottom = childTop + rect.height();

            final int offScreenLeft = Math.min(0, childLeft - parentLeft);
            final int offScreenTop = Math.min(0, childTop - parentTop);
            final int offScreenRight = Math.max(0, childRight - parentRight);
            final int offScreenBottom = Math.max(0, childBottom - parentBottom);

            // Favor the "start" layout direction over the end when bringing one side or the other
            // of a large rect into view. If we decide to bring in end because start is already
            // visible, limit the scroll such that start won't go out of bounds.
            final int dx;
            if (getLayoutDirection() == ViewCompat.LAYOUT_DIRECTION_RTL) {
                dx = offScreenRight != 0 ? offScreenRight
                        : Math.max(offScreenLeft, childRight - parentRight);
            } else {
                dx = offScreenLeft != 0 ? offScreenLeft
                        : Math.min(childLeft - parentLeft, offScreenRight);
            }

            // Favor bringing the top into view over the bottom. If top is already visible and
            // we should scroll to make bottom visible, make sure top does not go out of bounds.
            final int dy = offScreenTop != 0 ? offScreenTop
                    : Math.min(childTop - parentTop, offScreenBottom);
            out[0] = dx;
            out[1] = dy;
            return out;
        }
        /**
         * Called when a child of the RecyclerView wants a particular rectangle to be positioned
         * onto the screen. See {@link ViewParent#requestChildRectangleOnScreen(android.view.View,
         * android.graphics.Rect, boolean)} for more details.
         *
         * <p>The base implementation will attempt to perform a standard programmatic scroll
         * to bring the given rect into view, within the padded area of the RecyclerView.</p>
         *
         * @param child The direct child making the request.
         * @param rect  The rectangle in the child's coordinates the child
         *              wishes to be on the screen.
         * @param immediate True to forbid animated or delayed scrolling,
         *                  false otherwise
         * @return Whether the group scrolled to handle the operation
         */
        public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent,
                @NonNull View child, @NonNull Rect rect, boolean immediate) {
            return requestChildRectangleOnScreen(parent, child, rect, immediate, false);
        }

        /**
         * Requests that the given child of the RecyclerView be positioned onto the screen. This
         * method can be called for both unfocusable and focusable child views. For unfocusable
         * child views, focusedChildVisible is typically true in which case, layout manager
         * makes the child view visible only if the currently focused child stays in-bounds of RV.
         * @param parent The parent RecyclerView.
         * @param child The direct child making the request.
         * @param rect The rectangle in the child's coordinates the child
         *              wishes to be on the screen.
         * @param immediate True to forbid animated or delayed scrolling,
         *                  false otherwise
         * @param focusedChildVisible Whether the currently focused view must stay visible.
         * @return Whether the group scrolled to handle the operation
         */
        public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent,
                @NonNull View child, @NonNull Rect rect, boolean immediate,
                boolean focusedChildVisible) {
            int[] scrollAmount = getChildRectangleOnScreenScrollAmount(child, rect
            );
            int dx = scrollAmount[0];
            int dy = scrollAmount[1];
            if (!focusedChildVisible || isFocusedChildVisibleAfterScrolling(parent, dx, dy)) {
                if (dx != 0 || dy != 0) {
                    if (immediate) {
                        parent.scrollBy(dx, dy);
                    } else {
                        parent.smoothScrollBy(dx, dy);
                    }
                    return true;
                }
            }
            return false;
        }

        /**
         * Returns whether the given child view is partially or fully visible within the padded
         * bounded area of RecyclerView, depending on the input parameters.
         * A view is partially visible if it has non-zero overlap with RV's padded bounded area.
         * If acceptEndPointInclusion flag is set to true, it's also considered partially
         * visible if it's located outside RV's bounds and it's hitting either RV's start or end
         * bounds.
         *
         * @param child The child view to be examined.
         * @param completelyVisible If true, the method returns true if and only if the child is
         *                          completely visible. If false, the method returns true if and
         *                          only if the child is only partially visible (that is it will
         *                          return false if the child is either completely visible or out
         *                          of RV's bounds).
         * @param acceptEndPointInclusion If the view's endpoint intersection with RV's start of end
         *                                bounds is enough to consider it partially visible,
         *                                false otherwise.
         * @return True if the given child is partially or fully visible, false otherwise.
         */
        public boolean isViewPartiallyVisible(@NonNull View child, boolean completelyVisible,
                boolean acceptEndPointInclusion) {
            int boundsFlag = (ViewBoundsCheck.FLAG_CVS_GT_PVS | ViewBoundsCheck.FLAG_CVS_EQ_PVS
                    | ViewBoundsCheck.FLAG_CVE_LT_PVE | ViewBoundsCheck.FLAG_CVE_EQ_PVE);
            boolean isViewFullyVisible = mHorizontalBoundCheck.isViewWithinBoundFlags(child,
                    boundsFlag)
                    && mVerticalBoundCheck.isViewWithinBoundFlags(child, boundsFlag);
            if (completelyVisible) {
                return isViewFullyVisible;
            } else {
                return !isViewFullyVisible;
            }
        }

        /**
         * Returns whether the currently focused child stays within RV's bounds with the given
         * amount of scrolling.
         * @param parent The parent RecyclerView.
         * @param dx The scrolling in x-axis direction to be performed.
         * @param dy The scrolling in y-axis direction to be performed.
         * @return {@code false} if the focused child is not at least partially visible after
         *         scrolling or no focused child exists, {@code true} otherwise.
         */
        private boolean isFocusedChildVisibleAfterScrolling(RecyclerView parent, int dx, int dy) {
            final View focusedChild = parent.getFocusedChild();
            if (focusedChild == null) {
                return false;
            }
            final int parentLeft = getPaddingLeft();
            final int parentTop = getPaddingTop();
            final int parentRight = getWidth() - getPaddingRight();
            final int parentBottom = getHeight() - getPaddingBottom();
            final Rect bounds = mRecyclerView.mTempRect;
            getDecoratedBoundsWithMargins(focusedChild, bounds);

            if (bounds.left - dx >= parentRight || bounds.right - dx <= parentLeft
                    || bounds.top - dy >= parentBottom || bounds.bottom - dy <= parentTop) {
                return false;
            }
            return true;
        }

        /**
         * @deprecated Use {@link #onRequestChildFocus(RecyclerView, State, View, View)}
         */
        @Deprecated
        public boolean onRequestChildFocus(@NonNull RecyclerView parent, @NonNull View child,
                @Nullable View focused) {
            // eat the request if we are in the middle of a scroll or layout
            return isSmoothScrolling() || parent.isComputingLayout();
        }

        /**
         * Called when a descendant view of the RecyclerView requests focus.
         *
         * <p>A LayoutManager wishing to keep focused views aligned in a specific
         * portion of the view may implement that behavior in an override of this method.</p>
         *
         * <p>If the LayoutManager executes different behavior that should override the default
         * behavior of scrolling the focused child on screen instead of running alongside it,
         * this method should return true.</p>
         *
         * @param parent  The RecyclerView hosting this LayoutManager
         * @param state   Current state of RecyclerView
         * @param child   Direct child of the RecyclerView containing the newly focused view
         * @param focused The newly focused view. This may be the same view as child or it may be
         *                null
         * @return true if the default scroll behavior should be suppressed
         */
        public boolean onRequestChildFocus(@NonNull RecyclerView parent, @NonNull State state,
                @NonNull View child, @Nullable View focused) {
            return onRequestChildFocus(parent, child, focused);
        }

        /**
         * Called if the RecyclerView this LayoutManager is bound to has a different adapter set via
         * {@link RecyclerView#setAdapter(Adapter)} or
         * {@link RecyclerView#swapAdapter(Adapter, boolean)}. The LayoutManager may use this
         * opportunity to clear caches and configure state such that it can relayout appropriately
         * with the new data and potentially new view types.
         *
         * <p>The default implementation removes all currently attached views.</p>
         *
         * @param oldAdapter The previous adapter instance. Will be null if there was previously no
         *                   adapter.
         * @param newAdapter The new adapter instance. Might be null if
         *                   {@link #setAdapter(RecyclerView.Adapter)} is called with {@code null}.
         */
        public void onAdapterChanged(@Nullable Adapter oldAdapter, @Nullable Adapter newAdapter) {
        }

        /**
         * Called to populate focusable views within the RecyclerView.
         *
         * <p>The LayoutManager implementation should return <code>true</code> if the default
         * behavior of {@link ViewGroup#addFocusables(java.util.ArrayList, int)} should be
         * suppressed.</p>
         *
         * <p>The default implementation returns <code>false</code> to trigger RecyclerView
         * to fall back to the default ViewGroup behavior.</p>
         *
         * @param recyclerView The RecyclerView hosting this LayoutManager
         * @param views List of output views. This method should add valid focusable views
         *              to this list.
         * @param direction One of {@link View#FOCUS_UP}, {@link View#FOCUS_DOWN},
         *                  {@link View#FOCUS_LEFT}, {@link View#FOCUS_RIGHT},
         *                  {@link View#FOCUS_BACKWARD}, {@link View#FOCUS_FORWARD}
         * @param focusableMode The type of focusables to be added.
         *
         * @return true to suppress the default behavior, false to add default focusables after
         *         this method returns.
         *
         * @see #FOCUSABLES_ALL
         * @see #FOCUSABLES_TOUCH_MODE
         */
        public boolean onAddFocusables(@NonNull RecyclerView recyclerView,
                @NonNull ArrayList<View> views, int direction, int focusableMode) {
            return false;
        }

        /**
         * Called in response to a call to {@link Adapter#notifyDataSetChanged()} or
         * {@link RecyclerView#swapAdapter(Adapter, boolean)} ()} and signals that the the entire
         * data set has changed.
         *
         * @param recyclerView
         */
        public void onItemsChanged(@NonNull RecyclerView recyclerView) {
        }

        /**
         * Called when items have been added to the adapter. The LayoutManager may choose to
         * requestLayout if the inserted items would require refreshing the currently visible set
         * of child views. (e.g. currently empty space would be filled by appended items, etc.)
         *
         * @param recyclerView
         * @param positionStart
         * @param itemCount
         */
        public void onItemsAdded(@NonNull RecyclerView recyclerView, int positionStart,
                int itemCount) {
        }

        /**
         * Called when items have been removed from the adapter.
         *
         * @param recyclerView
         * @param positionStart
         * @param itemCount
         */
        public void onItemsRemoved(@NonNull RecyclerView recyclerView, int positionStart,
                int itemCount) {
        }

        /**
         * Called when items have been changed in the adapter.
         * To receive payload,  override {@link #onItemsUpdated(RecyclerView, int, int, Object)}
         * instead, then this callback will not be invoked.
         *
         * @param recyclerView
         * @param positionStart
         * @param itemCount
         */
        public void onItemsUpdated(@NonNull RecyclerView recyclerView, int positionStart,
                int itemCount) {
        }

        /**
         * Called when items have been changed in the adapter and with optional payload.
         * Default implementation calls {@link #onItemsUpdated(RecyclerView, int, int)}.
         *
         * @param recyclerView
         * @param positionStart
         * @param itemCount
         * @param payload
         */
        public void onItemsUpdated(@NonNull RecyclerView recyclerView, int positionStart,
                int itemCount, @Nullable Object payload) {
            onItemsUpdated(recyclerView, positionStart, itemCount);
        }

        /**
         * Called when an item is moved withing the adapter.
         * <p>
         * Note that, an item may also change position in response to another ADD/REMOVE/MOVE
         * operation. This callback is only called if and only if {@link Adapter#notifyItemMoved}
         * is called.
         *
         * @param recyclerView
         * @param from
         * @param to
         * @param itemCount
         */
        public void onItemsMoved(@NonNull RecyclerView recyclerView, int from, int to,
                int itemCount) {

        }


        /**
         * <p>Override this method if you want to support scroll bars.</p>
         *
         * <p>Read {@link RecyclerView#computeHorizontalScrollExtent()} for details.</p>
         *
         * <p>Default implementation returns 0.</p>
         *
         * @param state Current state of RecyclerView
         * @return The horizontal extent of the scrollbar's thumb
         * @see RecyclerView#computeHorizontalScrollExtent()
         */
        public int computeHorizontalScrollExtent(@NonNull State state) {
            return 0;
        }

        /**
         * <p>Override this method if you want to support scroll bars.</p>
         *
         * <p>Read {@link RecyclerView#computeHorizontalScrollOffset()} for details.</p>
         *
         * <p>Default implementation returns 0.</p>
         *
         * @param state Current State of RecyclerView where you can find total item count
         * @return The horizontal offset of the scrollbar's thumb
         * @see RecyclerView#computeHorizontalScrollOffset()
         */
        public int computeHorizontalScrollOffset(@NonNull State state) {
            return 0;
        }

        /**
         * <p>Override this method if you want to support scroll bars.</p>
         *
         * <p>Read {@link RecyclerView#computeHorizontalScrollRange()} for details.</p>
         *
         * <p>Default implementation returns 0.</p>
         *
         * @param state Current State of RecyclerView where you can find total item count
         * @return The total horizontal range represented by the vertical scrollbar
         * @see RecyclerView#computeHorizontalScrollRange()
         */
        public int computeHorizontalScrollRange(@NonNull State state) {
            return 0;
        }

        /**
         * <p>Override this method if you want to support scroll bars.</p>
         *
         * <p>Read {@link RecyclerView#computeVerticalScrollExtent()} for details.</p>
         *
         * <p>Default implementation returns 0.</p>
         *
         * @param state Current state of RecyclerView
         * @return The vertical extent of the scrollbar's thumb
         * @see RecyclerView#computeVerticalScrollExtent()
         */
        public int computeVerticalScrollExtent(@NonNull State state) {
            return 0;
        }

        /**
         * <p>Override this method if you want to support scroll bars.</p>
         *
         * <p>Read {@link RecyclerView#computeVerticalScrollOffset()} for details.</p>
         *
         * <p>Default implementation returns 0.</p>
         *
         * @param state Current State of RecyclerView where you can find total item count
         * @return The vertical offset of the scrollbar's thumb
         * @see RecyclerView#computeVerticalScrollOffset()
         */
        public int computeVerticalScrollOffset(@NonNull State state) {
            return 0;
        }

        /**
         * <p>Override this method if you want to support scroll bars.</p>
         *
         * <p>Read {@link RecyclerView#computeVerticalScrollRange()} for details.</p>
         *
         * <p>Default implementation returns 0.</p>
         *
         * @param state Current State of RecyclerView where you can find total item count
         * @return The total vertical range represented by the vertical scrollbar
         * @see RecyclerView#computeVerticalScrollRange()
         */
        public int computeVerticalScrollRange(@NonNull State state) {
            return 0;
        }

        /**
         * Measure the attached RecyclerView. Implementations must call
         * {@link #setMeasuredDimension(int, int)} before returning.
         * <p>
         * It is strongly advised to use the AutoMeasure mechanism by overriding
         * {@link #isAutoMeasureEnabled()} to return true as AutoMeasure handles all the standard
         * measure cases including when the RecyclerView's layout_width or layout_height have been
         * set to wrap_content.  If {@link #isAutoMeasureEnabled()} is overridden to return true,
         * this method should not be overridden.
         * <p>
         * The default implementation will handle EXACTLY measurements and respect
         * the minimum width and height properties of the host RecyclerView if measured
         * as UNSPECIFIED. AT_MOST measurements will be treated as EXACTLY and the RecyclerView
         * will consume all available space.
         *
         * @param recycler Recycler
         * @param state Transient state of RecyclerView
         * @param widthSpec Width {@link android.view.View.MeasureSpec}
         * @param heightSpec Height {@link android.view.View.MeasureSpec}
         *
         * @see #isAutoMeasureEnabled()
         * @see #setMeasuredDimension(int, int)
         */
        public void onMeasure(@NonNull Recycler recycler, @NonNull State state, int widthSpec,
                int heightSpec) {
            mRecyclerView.defaultOnMeasure(widthSpec, heightSpec);
        }

        /**
         * {@link View#setMeasuredDimension(int, int) Set the measured dimensions} of the
         * host RecyclerView.
         *
         * @param widthSize Measured width
         * @param heightSize Measured height
         */
        public void setMeasuredDimension(int widthSize, int heightSize) {
            mRecyclerView.setMeasuredDimension(widthSize, heightSize);
        }

        /**
         * @return The host RecyclerView's {@link View#getMinimumWidth()}
         */
        @Px
        public int getMinimumWidth() {
            return ViewCompat.getMinimumWidth(mRecyclerView);
        }

        /**
         * @return The host RecyclerView's {@link View#getMinimumHeight()}
         */
        @Px
        public int getMinimumHeight() {
            return ViewCompat.getMinimumHeight(mRecyclerView);
        }
        /**
         * <p>Called when the LayoutManager should save its state. This is a good time to save your
         * scroll position, configuration and anything else that may be required to restore the same
         * layout state if the LayoutManager is recreated.</p>
         * <p>RecyclerView does NOT verify if the LayoutManager has changed between state save and
         * restore. This will let you share information between your LayoutManagers but it is also
         * your responsibility to make sure they use the same parcelable class.</p>
         *
         * @return Necessary information for LayoutManager to be able to restore its state
         */
        @Nullable
        public Parcelable onSaveInstanceState() {
            return null;
        }


        public void onRestoreInstanceState(Parcelable state) {

        }

        void stopSmoothScroller() {
            if (mSmoothScroller != null) {
                mSmoothScroller.stop();
            }
        }

        void onSmoothScrollerStopped(SmoothScroller smoothScroller) {
            if (mSmoothScroller == smoothScroller) {
                mSmoothScroller = null;
            }
        }

        /**
         * RecyclerView calls this method to notify LayoutManager that scroll state has changed.
         *
         * @param state The new scroll state for RecyclerView
         */
        public void onScrollStateChanged(int state) {
        }

        /**
         * Removes all views and recycles them using the given recycler.
         * <p>
         * If you want to clean cached views as well, you should call {@link Recycler#clear()} too.
         * <p>
         * If a View is marked as "ignored", it is not removed nor recycled.
         *
         * @param recycler Recycler to use to recycle children
         * @see #removeAndRecycleView(View, Recycler)
         * @see #removeAndRecycleViewAt(int, Recycler)
         * @see #ignoreView(View)
         */
        public void removeAndRecycleAllViews(@NonNull Recycler recycler) {
            for (int i = getChildCount() - 1; i >= 0; i--) {
                final View view = getChildAt(i);
                if (!getChildViewHolderInt(view).shouldIgnore()) {
                    removeAndRecycleViewAt(i, recycler);
                }
            }
        }

        // called by accessibility delegate
        void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfoCompat info) {
            onInitializeAccessibilityNodeInfo(mRecyclerView.mRecycler, mRecyclerView.mState, info);
        }

        /**
         * Called by the AccessibilityDelegate when the information about the current layout should
         * be populated.
         * <p>
         * Default implementation adds a {@link
         * androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionInfoCompat}.
         * <p>
         * You should override
         * {@link #getRowCountForAccessibility(RecyclerView.Recycler, RecyclerView.State)},
         * {@link #getColumnCountForAccessibility(RecyclerView.Recycler, RecyclerView.State)},
         * {@link #isLayoutHierarchical(RecyclerView.Recycler, RecyclerView.State)} and
         * {@link #getSelectionModeForAccessibility(RecyclerView.Recycler, RecyclerView.State)} for
         * more accurate accessibility information.
         *
         * @param recycler The Recycler that can be used to convert view positions into adapter
         *                 positions
         * @param state    The current state of RecyclerView
         * @param info     The info that should be filled by the LayoutManager
         * @see View#onInitializeAccessibilityNodeInfo(
         *android.view.accessibility.AccessibilityNodeInfo)
         * @see #getRowCountForAccessibility(RecyclerView.Recycler, RecyclerView.State)
         * @see #getColumnCountForAccessibility(RecyclerView.Recycler, RecyclerView.State)
         * @see #isLayoutHierarchical(RecyclerView.Recycler, RecyclerView.State)
         * @see #getSelectionModeForAccessibility(RecyclerView.Recycler, RecyclerView.State)
         */
        public void onInitializeAccessibilityNodeInfo(@NonNull Recycler recycler,
                @NonNull State state, @NonNull AccessibilityNodeInfoCompat info) {
            if (mRecyclerView.canScrollVertically(-1) || mRecyclerView.canScrollHorizontally(-1)) {
                info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD);
                info.setScrollable(true);
            }
            if (mRecyclerView.canScrollVertically(1) || mRecyclerView.canScrollHorizontally(1)) {
                info.addAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
                info.setScrollable(true);
            }
            final AccessibilityNodeInfoCompat.CollectionInfoCompat collectionInfo =
                    AccessibilityNodeInfoCompat.CollectionInfoCompat
                            .obtain(getRowCountForAccessibility(recycler, state),
                                    getColumnCountForAccessibility(recycler, state),
                                    isLayoutHierarchical(recycler, state),
                                    getSelectionModeForAccessibility(recycler, state));
            info.setCollectionInfo(collectionInfo);
        }

        // called by accessibility delegate
        public void onInitializeAccessibilityEvent(@NonNull AccessibilityEvent event) {
            onInitializeAccessibilityEvent(mRecyclerView.mRecycler, mRecyclerView.mState, event);
        }

        /**
         * Called by the accessibility delegate to initialize an accessibility event.
         * <p>
         * Default implementation adds item count and scroll information to the event.
         *
         * @param recycler The Recycler that can be used to convert view positions into adapter
         *                 positions
         * @param state    The current state of RecyclerView
         * @param event    The event instance to initialize
         * @see View#onInitializeAccessibilityEvent(android.view.accessibility.AccessibilityEvent)
         */
        public void onInitializeAccessibilityEvent(@NonNull Recycler recycler, @NonNull State state,
                @NonNull AccessibilityEvent event) {
            if (mRecyclerView == null || event == null) {
                return;
            }
            event.setScrollable(mRecyclerView.canScrollVertically(1)
                    || mRecyclerView.canScrollVertically(-1)
                    || mRecyclerView.canScrollHorizontally(-1)
                    || mRecyclerView.canScrollHorizontally(1));

            if (mRecyclerView.mAdapter != null) {
                event.setItemCount(mRecyclerView.mAdapter.getItemCount());
            }
        }

        // called by accessibility delegate
        void onInitializeAccessibilityNodeInfoForItem(View host, AccessibilityNodeInfoCompat info) {
            final ViewHolder vh = getChildViewHolderInt(host);
            // avoid trying to create accessibility node info for removed children
            if (vh != null && !vh.isRemoved() && !mChildHelper.isHidden(vh.itemView)) {
                onInitializeAccessibilityNodeInfoForItem(mRecyclerView.mRecycler,
                        mRecyclerView.mState, host, info);
            }
        }

        /**
         * Called by the AccessibilityDelegate when the accessibility information for a specific
         * item should be populated.
         * <p>
         * Default implementation adds basic positioning information about the item.
         *
         * @param recycler The Recycler that can be used to convert view positions into adapter
         *                 positions
         * @param state    The current state of RecyclerView
         * @param host     The child for which accessibility node info should be populated
         * @param info     The info to fill out about the item
         * @see android.widget.AbsListView#onInitializeAccessibilityNodeInfoForItem(View, int,
         * android.view.accessibility.AccessibilityNodeInfo)
         */
        public void onInitializeAccessibilityNodeInfoForItem(@NonNull Recycler recycler,
                @NonNull State state, @NonNull View host,
                @NonNull AccessibilityNodeInfoCompat info) {
            int rowIndexGuess = canScrollVertically() ? getPosition(host) : 0;
            int columnIndexGuess = canScrollHorizontally() ? getPosition(host) : 0;
            final AccessibilityNodeInfoCompat.CollectionItemInfoCompat itemInfo =
                    AccessibilityNodeInfoCompat.CollectionItemInfoCompat.obtain(rowIndexGuess, 1,
                            columnIndexGuess, 1, false, false);
            info.setCollectionItemInfo(itemInfo);
        }

        /**
         * A LayoutManager can call this method to force RecyclerView to run simple animations in
         * the next layout pass, even if there is not any trigger to do so. (e.g. adapter data
         * change).
         * <p>
         * Note that, calling this method will not guarantee that RecyclerView will run animations
         * at all. For example, if there is not any {@link ItemAnimator} set, RecyclerView will
         * not run any animations but will still clear this flag after the layout is complete.
         *
         */
        public void requestSimpleAnimationsInNextLayout() {
            mRequestedSimpleAnimations = true;
        }

        /**
         * Returns the selection mode for accessibility. Should be
         * {@link AccessibilityNodeInfoCompat.CollectionInfoCompat#SELECTION_MODE_NONE},
         * {@link AccessibilityNodeInfoCompat.CollectionInfoCompat#SELECTION_MODE_SINGLE} or
         * {@link AccessibilityNodeInfoCompat.CollectionInfoCompat#SELECTION_MODE_MULTIPLE}.
         * <p>
         * Default implementation returns
         * {@link AccessibilityNodeInfoCompat.CollectionInfoCompat#SELECTION_MODE_NONE}.
         *
         * @param recycler The Recycler that can be used to convert view positions into adapter
         *                 positions
         * @param state    The current state of RecyclerView
         * @return Selection mode for accessibility. Default implementation returns
         * {@link AccessibilityNodeInfoCompat.CollectionInfoCompat#SELECTION_MODE_NONE}.
         */
        public int getSelectionModeForAccessibility(@NonNull Recycler recycler,
                @NonNull State state) {
            return AccessibilityNodeInfoCompat.CollectionInfoCompat.SELECTION_MODE_NONE;
        }

        /**
         * Returns the number of rows for accessibility.
         * <p>
         * Default implementation returns the number of items in the adapter if LayoutManager
         * supports vertical scrolling or 1 if LayoutManager does not support vertical
         * scrolling.
         *
         * @param recycler The Recycler that can be used to convert view positions into adapter
         *                 positions
         * @param state    The current state of RecyclerView
         * @return The number of rows in LayoutManager for accessibility.
         */
        public int getRowCountForAccessibility(@NonNull Recycler recycler, @NonNull State state) {
            if (mRecyclerView == null || mRecyclerView.mAdapter == null) {
                return 1;
            }
            return canScrollVertically() ? mRecyclerView.mAdapter.getItemCount() : 1;
        }

        /**
         * Returns the number of columns for accessibility.
         * <p>
         * Default implementation returns the number of items in the adapter if LayoutManager
         * supports horizontal scrolling or 1 if LayoutManager does not support horizontal
         * scrolling.
         *
         * @param recycler The Recycler that can be used to convert view positions into adapter
         *                 positions
         * @param state    The current state of RecyclerView
         * @return The number of rows in LayoutManager for accessibility.
         */
        public int getColumnCountForAccessibility(@NonNull Recycler recycler,
                @NonNull State state) {
            if (mRecyclerView == null || mRecyclerView.mAdapter == null) {
                return 1;
            }
            return canScrollHorizontally() ? mRecyclerView.mAdapter.getItemCount() : 1;
        }

        /**
         * Returns whether layout is hierarchical or not to be used for accessibility.
         * <p>
         * Default implementation returns false.
         *
         * @param recycler The Recycler that can be used to convert view positions into adapter
         *                 positions
         * @param state    The current state of RecyclerView
         * @return True if layout is hierarchical.
         */
        public boolean isLayoutHierarchical(@NonNull Recycler recycler, @NonNull State state) {
            return false;
        }

        // called by accessibility delegate
        boolean performAccessibilityAction(int action, @Nullable Bundle args) {
            return performAccessibilityAction(mRecyclerView.mRecycler, mRecyclerView.mState,
                    action, args);
        }

        /**
         * Called by AccessibilityDelegate when an action is requested from the RecyclerView.
         *
         * @param recycler  The Recycler that can be used to convert view positions into adapter
         *                  positions
         * @param state     The current state of RecyclerView
         * @param action    The action to perform
         * @param args      Optional action arguments
         * @see View#performAccessibilityAction(int, android.os.Bundle)
         */
        public boolean performAccessibilityAction(@NonNull Recycler recycler, @NonNull State state,
                int action, @Nullable Bundle args) {
            if (mRecyclerView == null) {
                return false;
            }
            int vScroll = 0, hScroll = 0;
            switch (action) {
                case AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD:
                    if (mRecyclerView.canScrollVertically(-1)) {
                        vScroll = -(getHeight() - getPaddingTop() - getPaddingBottom());
                    }
                    if (mRecyclerView.canScrollHorizontally(-1)) {
                        hScroll = -(getWidth() - getPaddingLeft() - getPaddingRight());
                    }
                    break;
                case AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD:
                    if (mRecyclerView.canScrollVertically(1)) {
                        vScroll = getHeight() - getPaddingTop() - getPaddingBottom();
                    }
                    if (mRecyclerView.canScrollHorizontally(1)) {
                        hScroll = getWidth() - getPaddingLeft() - getPaddingRight();
                    }
                    break;
            }
            if (vScroll == 0 && hScroll == 0) {
                return false;
            }
            mRecyclerView.smoothScrollBy(hScroll, vScroll);
            return true;
        }

        // called by accessibility delegate
        boolean performAccessibilityActionForItem(@NonNull View view, int action,
                @Nullable Bundle args) {
            return performAccessibilityActionForItem(mRecyclerView.mRecycler, mRecyclerView.mState,
                    view, action, args);
        }

        /**
         * Called by AccessibilityDelegate when an accessibility action is requested on one of the
         * children of LayoutManager.
         * <p>
         * Default implementation does not do anything.
         *
         * @param recycler The Recycler that can be used to convert view positions into adapter
         *                 positions
         * @param state    The current state of RecyclerView
         * @param view     The child view on which the action is performed
         * @param action   The action to perform
         * @param args     Optional action arguments
         * @return true if action is handled
         * @see View#performAccessibilityAction(int, android.os.Bundle)
         */
        public boolean performAccessibilityActionForItem(@NonNull Recycler recycler,
                @NonNull State state, @NonNull View view, int action, @Nullable Bundle args) {
            return false;
        }

        void setExactMeasureSpecsFrom(RecyclerView recyclerView) {
            setMeasureSpecs(
                    MeasureSpec.makeMeasureSpec(recyclerView.getWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(recyclerView.getHeight(), MeasureSpec.EXACTLY)
            );
        }

        /**
         * Internal API to allow LayoutManagers to be measured twice.
         * <p>
         * This is not public because LayoutManagers should be able to handle their layouts in one
         * pass but it is very convenient to make existing LayoutManagers support wrapping content
         * when both orientations are undefined.
         * <p>
         * This API will be removed after default LayoutManagers properly implement wrap content in
         * non-scroll orientation.
         */
        boolean shouldMeasureTwice() {
            return false;
        }

        boolean hasFlexibleChildInBothOrientations() {
            final int childCount = getChildCount();
            for (int i = 0; i < childCount; i++) {
                final View child = getChildAt(i);
                final ViewGroup.LayoutParams lp = child.getLayoutParams();
                if (lp.width < 0 && lp.height < 0) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Some general properties that a LayoutManager may want to use.
         */
        public static class Properties {
            /** {@link android.R.attr#orientation} */
            public int orientation;
            /** {@link androidx.recyclerview.R.attr#spanCount} */
            public int spanCount;
            /** {@link androidx.recyclerview.R.attr#reverseLayout} */
            public boolean reverseLayout;
            /** {@link androidx.recyclerview.R.attr#stackFromEnd} */
            public boolean stackFromEnd;
        }
    }

    /**
     * An ItemDecoration allows the application to add a special drawing and layout offset
     * to specific item views from the adapter's data set. This can be useful for drawing dividers
     * between items, highlights, visual grouping boundaries and more.
     *
     * <p>All ItemDecorations are drawn in the order they were added, before the item
     * views (in {@link ItemDecoration#onDraw(Canvas, RecyclerView, RecyclerView.State) onDraw()}
     * and after the items (in {@link ItemDecoration#onDrawOver(Canvas, RecyclerView,
     * RecyclerView.State)}.</p>
     */
    public abstract static class ItemDecoration {
        /**
         * Draw any appropriate decorations into the Canvas supplied to the RecyclerView.
         * Any content drawn by this method will be drawn before the item views are drawn,
         * and will thus appear underneath the views.
         *
         * @param c Canvas to draw into
         * @param parent RecyclerView this ItemDecoration is drawing into
         * @param state The current state of RecyclerView
         */
        public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull State state) {
            onDraw(c, parent);
        }

        /**
         * @deprecated
         * Override {@link #onDraw(Canvas, RecyclerView, RecyclerView.State)}
         */
        @Deprecated
        public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent) {
        }

        /**
         * Draw any appropriate decorations into the Canvas supplied to the RecyclerView.
         * Any content drawn by this method will be drawn after the item views are drawn
         * and will thus appear over the views.
         *
         * @param c Canvas to draw into
         * @param parent RecyclerView this ItemDecoration is drawing into
         * @param state The current state of RecyclerView.
         */
        public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent,
                @NonNull State state) {
            onDrawOver(c, parent);
        }

        /**
         * @deprecated
         * Override {@link #onDrawOver(Canvas, RecyclerView, RecyclerView.State)}
         */
        @Deprecated
        public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent) {
        }


        /**
         * @deprecated
         * Use {@link #getItemOffsets(Rect, View, RecyclerView, State)}
         */
        @Deprecated
        public void getItemOffsets(@NonNull Rect outRect, int itemPosition,
                @NonNull RecyclerView parent) {
            outRect.set(0, 0, 0, 0);
        }

        /**
         * Retrieve any offsets for the given item. Each field of <code>outRect</code> specifies
         * the number of pixels that the item view should be inset by, similar to padding or margin.
         * The default implementation sets the bounds of outRect to 0 and returns.
         *
         * <p>
         * If this ItemDecoration does not affect the positioning of item views, it should set
         * all four fields of <code>outRect</code> (left, top, right, bottom) to zero
         * before returning.
         *
         * <p>
         * If you need to access Adapter for additional data, you can call
         * {@link RecyclerView#getChildAdapterPosition(View)} to get the adapter position of the
         * View.
         *
         * @param outRect Rect to receive the output.
         * @param view    The child view to decorate
         * @param parent  RecyclerView this ItemDecoration is decorating
         * @param state   The current state of RecyclerView.
         */
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                @NonNull RecyclerView parent, @NonNull State state) {
            getItemOffsets(outRect, ((LayoutParams) view.getLayoutParams()).getViewLayoutPosition(),
                    parent);
        }
    }

    /**
     * An OnItemTouchListener allows the application to intercept touch events in progress at the
     * view hierarchy level of the RecyclerView before those touch events are considered for
     * RecyclerView's own scrolling behavior.
     *
     * <p>This can be useful for applications that wish to implement various forms of gestural
     * manipulation of item views within the RecyclerView. OnItemTouchListeners may intercept
     * a touch interaction already in progress even if the RecyclerView is already handling that
     * gesture stream itself for the purposes of scrolling.</p>
     *
     * @see SimpleOnItemTouchListener
     */
    public interface OnItemTouchListener {
        /**
         * Silently observe and/or take over touch events sent to the RecyclerView
         * before they are handled by either the RecyclerView itself or its child views.
         *
         * <p>The onInterceptTouchEvent methods of each attached OnItemTouchListener will be run
         * in the order in which each listener was added, before any other touch processing
         * by the RecyclerView itself or child views occurs.</p>
         *
         * @param e MotionEvent describing the touch event. All coordinates are in
         *          the RecyclerView's coordinate system.
         * @return true if this OnItemTouchListener wishes to begin intercepting touch events, false
         *         to continue with the current behavior and continue observing future events in
         *         the gesture.
         */
        boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e);

        /**
         * Process a touch event as part of a gesture that was claimed by returning true from
         * a previous call to {@link #onInterceptTouchEvent}.
         *
         * @param e MotionEvent describing the touch event. All coordinates are in
         *          the RecyclerView's coordinate system.
         */
        void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e);

        /**
         * Called when a child of RecyclerView does not want RecyclerView and its ancestors to
         * intercept touch events with
         * {@link ViewGroup#onInterceptTouchEvent(MotionEvent)}.
         *
         * @param disallowIntercept True if the child does not want the parent to
         *            intercept touch events.
         * @see ViewParent#requestDisallowInterceptTouchEvent(boolean)
         */
        void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept);
    }

    /**
     * An implementation of {@link RecyclerView.OnItemTouchListener} that has empty method bodies
     * and default return values.
     * <p>
     * You may prefer to extend this class if you don't need to override all methods. Another
     * benefit of using this class is future compatibility. As the interface may change, we'll
     * always provide a default implementation on this class so that your code won't break when
     * you update to a new version of the support library.
     */
    public static class SimpleOnItemTouchListener implements RecyclerView.OnItemTouchListener {
        @Override
        public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
            return false;
        }

        @Override
        public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
        }

        @Override
        public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        }
    }


    /**
     * An OnScrollListener can be added to a RecyclerView to receive messages when a scrolling event
     * has occurred on that RecyclerView.
     * <p>
     * @see RecyclerView#addOnScrollListener(OnScrollListener)
     * @see RecyclerView#clearOnChildAttachStateChangeListeners()
     *
     */
    public abstract static class OnScrollListener {
        /**
         * Callback method to be invoked when RecyclerView's scroll state changes.
         *
         * @param recyclerView The RecyclerView whose scroll state has changed.
         * @param newState     The updated scroll state. One of {@link #SCROLL_STATE_IDLE},
         *                     {@link #SCROLL_STATE_DRAGGING} or {@link #SCROLL_STATE_SETTLING}.
         */
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState){}

        /**
         * Callback method to be invoked when the RecyclerView has been scrolled. This will be
         * called after the scroll has completed.
         * <p>
         * This callback will also be called if visible item range changes after a layout
         * calculation. In that case, dx and dy will be 0.
         *
         * @param recyclerView The RecyclerView which scrolled.
         * @param dx The amount of horizontal scroll.
         * @param dy The amount of vertical scroll.
         */
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy){}
    }

    /**
     * A RecyclerListener can be set on a RecyclerView to receive messages whenever
     * a view is recycled.
     *
     * @see RecyclerView#setRecyclerListener(RecyclerListener)
     */
    public interface RecyclerListener {

        /**
         * This method is called whenever the view in the ViewHolder is recycled.
         *
         * RecyclerView calls this method right before clearing ViewHolder's internal data and
         * sending it to RecycledViewPool. This way, if ViewHolder was holding valid information
         * before being recycled, you can call {@link ViewHolder#getAdapterPosition()} to get
         * its adapter position.
         *
         * @param holder The ViewHolder containing the view that was recycled
         */
        void onViewRecycled(@NonNull ViewHolder holder);
    }

    /**
     * A Listener interface that can be attached to a RecylcerView to get notified
     * whenever a ViewHolder is attached to or detached from RecyclerView.
     */
    public interface OnChildAttachStateChangeListener {

        /**
         * Called when a view is attached to the RecyclerView.
         *
         * @param view The View which is attached to the RecyclerView
         */
        void onChildViewAttachedToWindow(@NonNull View view);

        /**
         * Called when a view is detached from RecyclerView.
         *
         * @param view The View which is being detached from the RecyclerView
         */
        void onChildViewDetachedFromWindow(@NonNull View view);
    }

    /**
     * A ViewHolder describes an item view and metadata about its place within the RecyclerView.
     *
     * <p>{@link Adapter} implementations should subclass ViewHolder and add fields for caching
     * potentially expensive {@link View#findViewById(int)} results.</p>
     *
     * <p>While {@link LayoutParams} belong to the {@link LayoutManager},
     * {@link ViewHolder ViewHolders} belong to the adapter. Adapters should feel free to use
     * their own custom ViewHolder implementations to store data that makes binding view contents
     * easier. Implementations should assume that individual item views will hold strong references
     * to <code>ViewHolder</code> objects and that <code>RecyclerView</code> instances may hold
     * strong references to extra off-screen item views for caching purposes</p>
     */
    public abstract static class ViewHolder {
        @NonNull
        public final View itemView;
        WeakReference<RecyclerView> mNestedRecyclerView;
        int mPosition = NO_POSITION;
        int mOldPosition = NO_POSITION;
        long mItemId = NO_ID;
        int mItemViewType = INVALID_TYPE;
        int mPreLayoutPosition = NO_POSITION;

        // The item that this holder is shadowing during an item change event/animation
        ViewHolder mShadowedHolder = null;
        // The item that is shadowing this holder during an item change event/animation
        ViewHolder mShadowingHolder = null;

        /**
         * This ViewHolder has been bound to a position; mPosition, mItemId and mItemViewType
         * are all valid.
         */
        static final int FLAG_BOUND = 1 << 0;

        /**
         * The data this ViewHolder's view reflects is stale and needs to be rebound
         * by the adapter. mPosition and mItemId are consistent.
         */
        static final int FLAG_UPDATE = 1 << 1;

        /**
         * This ViewHolder's data is invalid. The identity implied by mPosition and mItemId
         * are not to be trusted and may no longer match the item view type.
         * This ViewHolder must be fully rebound to different data.
         */
        static final int FLAG_INVALID = 1 << 2;

        /**
         * This ViewHolder points at data that represents an item previously removed from the
         * data set. Its view may still be used for things like outgoing animations.
         */
        static final int FLAG_REMOVED = 1 << 3;

        /**
         * This ViewHolder should not be recycled. This flag is set via setIsRecyclable()
         * and is intended to keep views around during animations.
         */
        static final int FLAG_NOT_RECYCLABLE = 1 << 4;

        /**
         * This ViewHolder is returned from scrap which means we are expecting an addView call
         * for this itemView. When returned from scrap, ViewHolder stays in the scrap list until
         * the end of the layout pass and then recycled by RecyclerView if it is not added back to
         * the RecyclerView.
         */
        static final int FLAG_RETURNED_FROM_SCRAP = 1 << 5;

        /**
         * This ViewHolder is fully managed by the LayoutManager. We do not scrap, recycle or remove
         * it unless LayoutManager is replaced.
         * It is still fully visible to the LayoutManager.
         */
        static final int FLAG_IGNORE = 1 << 7;

        /**
         * When the View is detached form the parent, we set this flag so that we can take correct
         * action when we need to remove it or add it back.
         */
        static final int FLAG_TMP_DETACHED = 1 << 8;

        /**
         * Set when we can no longer determine the adapter position of this ViewHolder until it is
         * rebound to a new position. It is different than FLAG_INVALID because FLAG_INVALID is
         * set even when the type does not match. Also, FLAG_ADAPTER_POSITION_UNKNOWN is set as soon
         * as adapter notification arrives vs FLAG_INVALID is set lazily before layout is
         * re-calculated.
         */
        static final int FLAG_ADAPTER_POSITION_UNKNOWN = 1 << 9;

        /**
         * Set when a addChangePayload(null) is called
         */
        static final int FLAG_ADAPTER_FULLUPDATE = 1 << 10;

        /**
         * Used by ItemAnimator when a ViewHolder's position changes
         */
        static final int FLAG_MOVED = 1 << 11;

        /**
         * Used by ItemAnimator when a ViewHolder appears in pre-layout
         */
        static final int FLAG_APPEARED_IN_PRE_LAYOUT = 1 << 12;

        static final int PENDING_ACCESSIBILITY_STATE_NOT_SET = -1;

        /**
         * Used when a ViewHolder starts the layout pass as a hidden ViewHolder but is re-used from
         * hidden list (as if it was scrap) without being recycled in between.
         *
         * When a ViewHolder is hidden, there are 2 paths it can be re-used:
         *   a) Animation ends, view is recycled and used from the recycle pool.
         *   b) LayoutManager asks for the View for that position while the ViewHolder is hidden.
         *
         * This flag is used to represent "case b" where the ViewHolder is reused without being
         * recycled (thus "bounced" from the hidden list). This state requires special handling
         * because the ViewHolder must be added to pre layout maps for animations as if it was
         * already there.
         */
        static final int FLAG_BOUNCED_FROM_HIDDEN_LIST = 1 << 13;

        /**
         * Flags that RecyclerView assigned {@link RecyclerViewAccessibilityDelegate
         * #getItemDelegate()} in onBindView when app does not provide a delegate.
         */
        static final int FLAG_SET_A11Y_ITEM_DELEGATE = 1 << 14;

        int mFlags;

        private static final List<Object> FULLUPDATE_PAYLOADS = Collections.emptyList();

        List<Object> mPayloads = null;
        List<Object> mUnmodifiedPayloads = null;

        private int mIsRecyclableCount = 0;

        // If non-null, view is currently considered scrap and may be reused for other data by the
        // scrap container.
        Recycler mScrapContainer = null;
        // Keeps whether this ViewHolder lives in Change scrap or Attached scrap
        boolean mInChangeScrap = false;

        // Saves isImportantForAccessibility value for the view item while it's in hidden state and
        // marked as unimportant for accessibility.
        private int mWasImportantForAccessibilityBeforeHidden =
                ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
        // set if we defer the accessibility state change of the view holder
        @VisibleForTesting
        int mPendingAccessibilityState = PENDING_ACCESSIBILITY_STATE_NOT_SET;

        /**
         * Is set when VH is bound from the adapter and cleaned right before it is sent to
         * {@link RecycledViewPool}.
         */
        RecyclerView mOwnerRecyclerView;

        public ViewHolder(@NonNull View itemView) {
            if (itemView == null) {
                throw new IllegalArgumentException("itemView may not be null");
            }
            this.itemView = itemView;
        }

        void flagRemovedAndOffsetPosition(int mNewPosition, int offset, boolean applyToPreLayout) {
            addFlags(ViewHolder.FLAG_REMOVED);
            offsetPosition(offset, applyToPreLayout);
            mPosition = mNewPosition;
        }

        void offsetPosition(int offset, boolean applyToPreLayout) {
            if (mOldPosition == NO_POSITION) {
                mOldPosition = mPosition;
            }
            if (mPreLayoutPosition == NO_POSITION) {
                mPreLayoutPosition = mPosition;
            }
            if (applyToPreLayout) {
                mPreLayoutPosition += offset;
            }
            mPosition += offset;
            if (itemView.getLayoutParams() != null) {
                ((LayoutParams) itemView.getLayoutParams()).mInsetsDirty = true;
            }
        }

        void clearOldPosition() {
            mOldPosition = NO_POSITION;
            mPreLayoutPosition = NO_POSITION;
        }

        void saveOldPosition() {
            if (mOldPosition == NO_POSITION) {
                mOldPosition = mPosition;
            }
        }

        boolean shouldIgnore() {
            return (mFlags & FLAG_IGNORE) != 0;
        }

        /**
         * @deprecated This method is deprecated because its meaning is ambiguous due to the async
         * handling of adapter updates. You should use {@link #getLayoutPosition()} or
         * {@link #getAdapterPosition()} depending on your use case.
         *
         * @see #getLayoutPosition()
         * @see #getAdapterPosition()
         */
        @Deprecated
        public final int getPosition() {
            return mPreLayoutPosition == NO_POSITION ? mPosition : mPreLayoutPosition;
        }

        /**
         * Returns the position of the ViewHolder in terms of the latest layout pass.
         * <p>
         * This position is mostly used by RecyclerView components to be consistent while
         * RecyclerView lazily processes adapter updates.
         * <p>
         * For performance and animation reasons, RecyclerView batches all adapter updates until the
         * next layout pass. This may cause mismatches between the Adapter position of the item and
         * the position it had in the latest layout calculations.
         * <p>
         * LayoutManagers should always call this method while doing calculations based on item
         * positions. All methods in {@link RecyclerView.LayoutManager}, {@link RecyclerView.State},
         * {@link RecyclerView.Recycler} that receive a position expect it to be the layout position
         * of the item.
         * <p>
         * If LayoutManager needs to call an external method that requires the adapter position of
         * the item, it can use {@link #getAdapterPosition()} or
         * {@link RecyclerView.Recycler#convertPreLayoutPositionToPostLayout(int)}.
         *
         * @return Returns the adapter position of the ViewHolder in the latest layout pass.
         * @see #getAdapterPosition()
         */
        public final int getLayoutPosition() {
            return mPreLayoutPosition == NO_POSITION ? mPosition : mPreLayoutPosition;
        }

        /**
         * Returns the Adapter position of the item represented by this ViewHolder.
         * <p>
         * Note that this might be different than the {@link #getLayoutPosition()} if there are
         * pending adapter updates but a new layout pass has not happened yet.
         * <p>
         * RecyclerView does not handle any adapter updates until the next layout traversal. This
         * may create temporary inconsistencies between what user sees on the screen and what
         * adapter contents have. This inconsistency is not important since it will be less than
         * 16ms but it might be a problem if you want to use ViewHolder position to access the
         * adapter. Sometimes, you may need to get the exact adapter position to do
         * some actions in response to user events. In that case, you should use this method which
         * will calculate the Adapter position of the ViewHolder.
         * <p>
         * Note that if you've called {@link RecyclerView.Adapter#notifyDataSetChanged()}, until the
         * next layout pass, the return value of this method will be {@link #NO_POSITION}.
         *
         * @return The adapter position of the item if it still exists in the adapter.
         * {@link RecyclerView#NO_POSITION} if item has been removed from the adapter,
         * {@link RecyclerView.Adapter#notifyDataSetChanged()} has been called after the last
         * layout pass or the ViewHolder has already been recycled.
         */
        public final int getAdapterPosition() {
            if (mOwnerRecyclerView == null) {
                return NO_POSITION;
            }
            return mOwnerRecyclerView.getAdapterPositionFor(this);
        }

        /**
         * When LayoutManager supports animations, RecyclerView tracks 3 positions for ViewHolders
         * to perform animations.
         * <p>
         * If a ViewHolder was laid out in the previous onLayout call, old position will keep its
         * adapter index in the previous layout.
         *
         * @return The previous adapter index of the Item represented by this ViewHolder or
         * {@link #NO_POSITION} if old position does not exists or cleared (pre-layout is
         * complete).
         */
        public final int getOldPosition() {
            return mOldPosition;
        }

        /**
         * Returns The itemId represented by this ViewHolder.
         *
         * @return The item's id if adapter has stable ids, {@link RecyclerView#NO_ID}
         * otherwise
         */
        public final long getItemId() {
            return mItemId;
        }

        /**
         * @return The view type of this ViewHolder.
         */
        public final int getItemViewType() {
            return mItemViewType;
        }

        boolean isScrap() {
            return mScrapContainer != null;
        }

        void unScrap() {
            mScrapContainer.unscrapView(this);
        }

        boolean wasReturnedFromScrap() {
            return (mFlags & FLAG_RETURNED_FROM_SCRAP) != 0;
        }

        void clearReturnedFromScrapFlag() {
            mFlags = mFlags & ~FLAG_RETURNED_FROM_SCRAP;
        }

        void clearTmpDetachFlag() {
            mFlags = mFlags & ~FLAG_TMP_DETACHED;
        }

        void stopIgnoring() {
            mFlags = mFlags & ~FLAG_IGNORE;
        }

        void setScrapContainer(Recycler recycler, boolean isChangeScrap) {
            mScrapContainer = recycler;
            mInChangeScrap = isChangeScrap;
        }

        boolean isInvalid() {
            return (mFlags & FLAG_INVALID) != 0;
        }

        boolean needsUpdate() {
            return (mFlags & FLAG_UPDATE) != 0;
        }

        boolean isBound() {
            return (mFlags & FLAG_BOUND) != 0;
        }

        boolean isRemoved() {
            return (mFlags & FLAG_REMOVED) != 0;
        }

        boolean hasAnyOfTheFlags(int flags) {
            return (mFlags & flags) != 0;
        }

        boolean isTmpDetached() {
            return (mFlags & FLAG_TMP_DETACHED) != 0;
        }

        boolean isAttachedToTransitionOverlay() {
            return itemView.getParent() != null && itemView.getParent() != mOwnerRecyclerView;
        }

        boolean isAdapterPositionUnknown() {
            return (mFlags & FLAG_ADAPTER_POSITION_UNKNOWN) != 0 || isInvalid();
        }

        void setFlags(int flags, int mask) {
            mFlags = (mFlags & ~mask) | (flags & mask);
        }

        void addFlags(int flags) {
            mFlags |= flags;
        }

        void addChangePayload(Object payload) {
            if (payload == null) {
                addFlags(FLAG_ADAPTER_FULLUPDATE);
            } else if ((mFlags & FLAG_ADAPTER_FULLUPDATE) == 0) {
                createPayloadsIfNeeded();
                mPayloads.add(payload);
            }
        }

        private void createPayloadsIfNeeded() {
            if (mPayloads == null) {
                mPayloads = new ArrayList<Object>();
                mUnmodifiedPayloads = Collections.unmodifiableList(mPayloads);
            }
        }

        void clearPayload() {
            if (mPayloads != null) {
                mPayloads.clear();
            }
            mFlags = mFlags & ~FLAG_ADAPTER_FULLUPDATE;
        }

        List<Object> getUnmodifiedPayloads() {
            if ((mFlags & FLAG_ADAPTER_FULLUPDATE) == 0) {
                if (mPayloads == null || mPayloads.size() == 0) {
                    // Initial state,  no update being called.
                    return FULLUPDATE_PAYLOADS;
                }
                // there are none-null payloads
                return mUnmodifiedPayloads;
            } else {
                // a full update has been called.
                return FULLUPDATE_PAYLOADS;
            }
        }

        void resetInternal() {
            mFlags = 0;
            mPosition = NO_POSITION;
            mOldPosition = NO_POSITION;
            mItemId = NO_ID;
            mPreLayoutPosition = NO_POSITION;
            mIsRecyclableCount = 0;
            mShadowedHolder = null;
            mShadowingHolder = null;
            clearPayload();
            mWasImportantForAccessibilityBeforeHidden = ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
            mPendingAccessibilityState = PENDING_ACCESSIBILITY_STATE_NOT_SET;
            clearNestedRecyclerViewIfNotNested(this);
        }

        /**
         * Called when the child view enters the hidden state
         */
        void onEnteredHiddenState(RecyclerView parent) {
            // While the view item is in hidden state, make it invisible for the accessibility.
            if (mPendingAccessibilityState != PENDING_ACCESSIBILITY_STATE_NOT_SET) {
                mWasImportantForAccessibilityBeforeHidden = mPendingAccessibilityState;
            } else {
                mWasImportantForAccessibilityBeforeHidden =
                        ViewCompat.getImportantForAccessibility(itemView);
            }
            parent.setChildImportantForAccessibilityInternal(this,
                    ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }

        /**
         * Called when the child view leaves the hidden state
         */
        void onLeftHiddenState(RecyclerView parent) {
            parent.setChildImportantForAccessibilityInternal(this,
                    mWasImportantForAccessibilityBeforeHidden);
            mWasImportantForAccessibilityBeforeHidden = ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("ViewHolder{"
                    + Integer.toHexString(hashCode()) + " position=" + mPosition + " id=" + mItemId
                    + ", oldPos=" + mOldPosition + ", pLpos:" + mPreLayoutPosition);
            if (isScrap()) {
                sb.append(" scrap ")
                        .append(mInChangeScrap ? "[changeScrap]" : "[attachedScrap]");
            }
            if (isInvalid()) sb.append(" invalid");
            if (!isBound()) sb.append(" unbound");
            if (needsUpdate()) sb.append(" update");
            if (isRemoved()) sb.append(" removed");
            if (shouldIgnore()) sb.append(" ignored");
            if (isTmpDetached()) sb.append(" tmpDetached");
            if (!isRecyclable()) sb.append(" not recyclable(" + mIsRecyclableCount + ")");
            if (isAdapterPositionUnknown()) sb.append(" undefined adapter position");

            if (itemView.getParent() == null) sb.append(" no parent");
            sb.append("}");
            return sb.toString();
        }

        /**
         * Informs the recycler whether this item can be recycled. Views which are not
         * recyclable will not be reused for other items until setIsRecyclable() is
         * later set to true. Calls to setIsRecyclable() should always be paired (one
         * call to setIsRecyclabe(false) should always be matched with a later call to
         * setIsRecyclable(true)). Pairs of calls may be nested, as the state is internally
         * reference-counted.
         *
         * @param recyclable Whether this item is available to be recycled. Default value
         * is true.
         *
         * @see #isRecyclable()
         */
        public final void setIsRecyclable(boolean recyclable) {
            mIsRecyclableCount = recyclable ? mIsRecyclableCount - 1 : mIsRecyclableCount + 1;
            if (mIsRecyclableCount < 0) {
                mIsRecyclableCount = 0;
                if (BuildVars.DEBUG_VERSION) {
                    throw new RuntimeException("isRecyclable decremented below 0: "
                            + "unmatched pair of setIsRecyable() calls for " + this);
                }
                Log.e(VIEW_LOG_TAG, "isRecyclable decremented below 0: "
                        + "unmatched pair of setIsRecyable() calls for " + this);
            } else if (!recyclable && mIsRecyclableCount == 1) {
                mFlags |= FLAG_NOT_RECYCLABLE;
            } else if (recyclable && mIsRecyclableCount == 0) {
                mFlags &= ~FLAG_NOT_RECYCLABLE;
            }
            if (DEBUG) {
                Log.d(TAG, "setIsRecyclable val:" + recyclable + ":" + this);
            }
        }

        /**
         * @return true if this item is available to be recycled, false otherwise.
         *
         * @see #setIsRecyclable(boolean)
         */
        public final boolean isRecyclable() {
            return (mFlags & FLAG_NOT_RECYCLABLE) == 0
                    && !ViewCompat.hasTransientState(itemView);
        }

        /**
         * Returns whether we have animations referring to this view holder or not.
         * This is similar to isRecyclable flag but does not check transient state.
         */
        boolean shouldBeKeptAsChild() {
            return (mFlags & FLAG_NOT_RECYCLABLE) != 0;
        }

        /**
         * @return True if ViewHolder is not referenced by RecyclerView animations but has
         * transient state which will prevent it from being recycled.
         */
        boolean doesTransientStatePreventRecycling() {
            return (mFlags & FLAG_NOT_RECYCLABLE) == 0 && ViewCompat.hasTransientState(itemView);
        }

        boolean isUpdated() {
            return (mFlags & FLAG_UPDATE) != 0;
        }
    }

    /**
     * This method is here so that we can control the important for a11y changes and test it.
     */
    @VisibleForTesting
    boolean setChildImportantForAccessibilityInternal(ViewHolder viewHolder,
            int importantForAccessibility) {
        if (isComputingLayout()) {
            viewHolder.mPendingAccessibilityState = importantForAccessibility;
            mPendingAccessibilityImportanceChange.add(viewHolder);
            return false;
        }
        ViewCompat.setImportantForAccessibility(viewHolder.itemView, importantForAccessibility);
        return true;
    }

    void dispatchPendingImportantForAccessibilityChanges() {
        for (int i = mPendingAccessibilityImportanceChange.size() - 1; i >= 0; i--) {
            ViewHolder viewHolder = mPendingAccessibilityImportanceChange.get(i);
            if (viewHolder.itemView.getParent() != this || viewHolder.shouldIgnore()) {
                continue;
            }
            int state = viewHolder.mPendingAccessibilityState;
            if (state != ViewHolder.PENDING_ACCESSIBILITY_STATE_NOT_SET) {
                //noinspection WrongConstant
                ViewCompat.setImportantForAccessibility(viewHolder.itemView, state);
                viewHolder.mPendingAccessibilityState =
                        ViewHolder.PENDING_ACCESSIBILITY_STATE_NOT_SET;
            }
        }
        mPendingAccessibilityImportanceChange.clear();
    }

    int getAdapterPositionFor(ViewHolder viewHolder) {
        if (viewHolder.hasAnyOfTheFlags(ViewHolder.FLAG_INVALID
                | ViewHolder.FLAG_REMOVED | ViewHolder.FLAG_ADAPTER_POSITION_UNKNOWN)
                || !viewHolder.isBound()) {
            return RecyclerView.NO_POSITION;
        }
        return mAdapterHelper.applyPendingUpdatesToPosition(viewHolder.mPosition);
    }

    @VisibleForTesting
    void initFastScroller(StateListDrawable verticalThumbDrawable,
            Drawable verticalTrackDrawable, StateListDrawable horizontalThumbDrawable,
            Drawable horizontalTrackDrawable) {
        if (verticalThumbDrawable == null || verticalTrackDrawable == null
                || horizontalThumbDrawable == null || horizontalTrackDrawable == null) {
            throw new IllegalArgumentException(
                "Trying to set fast scroller without both required drawables." + exceptionLabel());
        }

        Resources resources = getContext().getResources();
        new FastScroller(this, verticalThumbDrawable, verticalTrackDrawable,
                horizontalThumbDrawable, horizontalTrackDrawable,
                AndroidUtilities.dp(8),
                AndroidUtilities.dp(50),
                0);
    }

    // NestedScrollingChild

    @Override
    public void setNestedScrollingEnabled(boolean enabled) {
        getScrollingChildHelper().setNestedScrollingEnabled(enabled);
    }

    @Override
    public boolean isNestedScrollingEnabled() {
        return getScrollingChildHelper().isNestedScrollingEnabled();
    }

    @Override
    public boolean startNestedScroll(int axes) {
        return getScrollingChildHelper().startNestedScroll(axes);
    }

    @Override
    public boolean startNestedScroll(int axes, int type) {
        return getScrollingChildHelper().startNestedScroll(axes, type);
    }

    @Override
    public void stopNestedScroll() {
        getScrollingChildHelper().stopNestedScroll();
    }

    @Override
    public void stopNestedScroll(int type) {
        getScrollingChildHelper().stopNestedScroll(type);
    }

    @Override
    public boolean hasNestedScrollingParent() {
        return getScrollingChildHelper().hasNestedScrollingParent();
    }

    @Override
    public boolean hasNestedScrollingParent(int type) {
        return getScrollingChildHelper().hasNestedScrollingParent(type);
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
            int dyUnconsumed, int[] offsetInWindow) {
        return getScrollingChildHelper().dispatchNestedScroll(dxConsumed, dyConsumed,
                dxUnconsumed, dyUnconsumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
            int dyUnconsumed, int[] offsetInWindow, int type) {
        return getScrollingChildHelper().dispatchNestedScroll(dxConsumed, dyConsumed,
                dxUnconsumed, dyUnconsumed, offsetInWindow, type);
    }

    @Override
    public final void dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed,
            int dyUnconsumed, int[] offsetInWindow, int type, int[] consumed) {
        getScrollingChildHelper().dispatchNestedScroll(dxConsumed, dyConsumed,
                dxUnconsumed, dyUnconsumed, offsetInWindow, type, consumed);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow) {
        return getScrollingChildHelper().dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow,
            int type) {
        return getScrollingChildHelper().dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow,
                type);
    }

    @Override
    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return getScrollingChildHelper().dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return getScrollingChildHelper().dispatchNestedPreFling(velocityX, velocityY);
    }

    /**
     * {@link android.view.ViewGroup.MarginLayoutParams LayoutParams} subclass for children of
     * {@link RecyclerView}. Custom {@link LayoutManager layout managers} are encouraged
     * to create their own subclass of this <code>LayoutParams</code> class
     * to store any additional required per-child view metadata about the layout.
     */
    public static class LayoutParams extends android.view.ViewGroup.MarginLayoutParams {
        public ViewHolder mViewHolder;
        public final Rect mDecorInsets = new Rect();
        boolean mInsetsDirty = true;
        // Flag is set to true if the view is bound while it is detached from RV.
        // In this case, we need to manually call invalidate after view is added to guarantee that
        // invalidation is populated through the View hierarchy
        boolean mPendingInvalidate = false;

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(MarginLayoutParams source) {
            super(source);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }

        public LayoutParams(LayoutParams source) {
            super((ViewGroup.LayoutParams) source);
        }

        /**
         * Returns true if the view this LayoutParams is attached to needs to have its content
         * updated from the corresponding adapter.
         *
         * @return true if the view should have its content updated
         */
        public boolean viewNeedsUpdate() {
            return mViewHolder.needsUpdate();
        }

        /**
         * Returns true if the view this LayoutParams is attached to is now representing
         * potentially invalid data. A LayoutManager should scrap/recycle it.
         *
         * @return true if the view is invalid
         */
        public boolean isViewInvalid() {
            return mViewHolder.isInvalid();
        }

        /**
         * Returns true if the adapter data item corresponding to the view this LayoutParams
         * is attached to has been removed from the data set. A LayoutManager may choose to
         * treat it differently in order to animate its outgoing or disappearing state.
         *
         * @return true if the item the view corresponds to was removed from the data set
         */
        public boolean isItemRemoved() {
            return mViewHolder.isRemoved();
        }

        /**
         * Returns true if the adapter data item corresponding to the view this LayoutParams
         * is attached to has been changed in the data set. A LayoutManager may choose to
         * treat it differently in order to animate its changing state.
         *
         * @return true if the item the view corresponds to was changed in the data set
         */
        public boolean isItemChanged() {
            return mViewHolder.isUpdated();
        }

        /**
         * @deprecated use {@link #getViewLayoutPosition()} or {@link #getViewAdapterPosition()}
         */
        @Deprecated
        public int getViewPosition() {
            return mViewHolder.getPosition();
        }

        /**
         * Returns the adapter position that the view this LayoutParams is attached to corresponds
         * to as of latest layout calculation.
         *
         * @return the adapter position this view as of latest layout pass
         */
        public int getViewLayoutPosition() {
            return mViewHolder.getLayoutPosition();
        }

        /**
         * Returns the up-to-date adapter position that the view this LayoutParams is attached to
         * corresponds to.
         *
         * @return the up-to-date adapter position this view. It may return
         * {@link RecyclerView#NO_POSITION} if item represented by this View has been removed or
         * its up-to-date position cannot be calculated.
         */
        public int getViewAdapterPosition() {
            return mViewHolder.getAdapterPosition();
        }
    }

    /**
     * Observer base class for watching changes to an {@link Adapter}.
     * See {@link Adapter#registerAdapterDataObserver(AdapterDataObserver)}.
     */
    public abstract static class AdapterDataObserver {
        public void onChanged() {
            // Do nothing
        }

        public void onItemRangeChanged(int positionStart, int itemCount) {
            // do nothing
        }

        public void onItemRangeChanged(int positionStart, int itemCount, @Nullable Object payload) {
            // fallback to onItemRangeChanged(positionStart, itemCount) if app
            // does not override this method.
            onItemRangeChanged(positionStart, itemCount);
        }

        public void onItemRangeInserted(int positionStart, int itemCount) {
            // do nothing
        }

        public void onItemRangeRemoved(int positionStart, int itemCount) {
            // do nothing
        }

        public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
            // do nothing
        }
    }

    /**
     * Base class for smooth scrolling. Handles basic tracking of the target view position and
     * provides methods to trigger a programmatic scroll.
     *
     * <p>An instance of SmoothScroller is only intended to be used once.  You should create a new
     * instance for each call to {@link LayoutManager#startSmoothScroll(SmoothScroller)}.
     *
     * @see LinearSmoothScroller
     */
    public abstract static class SmoothScroller {

        private int mTargetPosition = RecyclerView.NO_POSITION;

        private RecyclerView mRecyclerView;

        private LayoutManager mLayoutManager;

        private boolean mPendingInitialRun;

        private boolean mRunning;

        private View mTargetView;

        private final Action mRecyclingAction;

        private boolean mStarted;

        public SmoothScroller() {
            mRecyclingAction = new Action(0, 0);
        }

        /**
         * Starts a smooth scroll for the given target position.
         * <p>In each animation step, {@link RecyclerView} will check
         * for the target view and call either
         * {@link #onTargetFound(android.view.View, RecyclerView.State, SmoothScroller.Action)} or
         * {@link #onSeekTargetStep(int, int, RecyclerView.State, SmoothScroller.Action)} until
         * SmoothScroller is stopped.</p>
         *
         * <p>Note that if RecyclerView finds the target view, it will automatically stop the
         * SmoothScroller. This <b>does not</b> mean that scroll will stop, it only means it will
         * stop calling SmoothScroller in each animation step.</p>
         */
        void start(RecyclerView recyclerView, LayoutManager layoutManager) {

            // Stop any previous ViewFlinger animations now because we are about to start a new one.
            recyclerView.mViewFlinger.stop();

            if (mStarted) {
                Log.w(TAG, "An instance of " + this.getClass().getSimpleName() + " was started "
                        + "more than once. Each instance of" + this.getClass().getSimpleName() + " "
                        + "is intended to only be used once. You should create a new instance for "
                        + "each use.");
            }

            mRecyclerView = recyclerView;
            mLayoutManager = layoutManager;
            if (mTargetPosition == RecyclerView.NO_POSITION) {
                throw new IllegalArgumentException("Invalid target position");
            }
            mRecyclerView.mState.mTargetPosition = mTargetPosition;
            mRunning = true;
            mPendingInitialRun = true;
            mTargetView = findViewByPosition(getTargetPosition());
            onStart();
            mRecyclerView.mViewFlinger.postOnAnimation();

            mStarted = true;
        }

        public void setTargetPosition(int targetPosition) {
            mTargetPosition = targetPosition;
        }

        /**
         * Compute the scroll vector for a given target position.
         * <p>
         * This method can return null if the layout manager cannot calculate a scroll vector
         * for the given position (e.g. it has no current scroll position).
         *
         * @param targetPosition the position to which the scroller is scrolling
         *
         * @return the scroll vector for a given target position
         */
        @Nullable
        public PointF computeScrollVectorForPosition(int targetPosition) {
            LayoutManager layoutManager = getLayoutManager();
            if (layoutManager instanceof ScrollVectorProvider) {
                return ((ScrollVectorProvider) layoutManager)
                        .computeScrollVectorForPosition(targetPosition);
            }
            Log.w(TAG, "You should override computeScrollVectorForPosition when the LayoutManager"
                    + " does not implement " + ScrollVectorProvider.class.getCanonicalName());
            return null;
        }

        /**
         * @return The LayoutManager to which this SmoothScroller is attached. Will return
         * <code>null</code> after the SmoothScroller is stopped.
         */
        @Nullable
        public LayoutManager getLayoutManager() {
            return mLayoutManager;
        }

        /**
         * Stops running the SmoothScroller in each animation callback. Note that this does not
         * cancel any existing {@link Action} updated by
         * {@link #onTargetFound(android.view.View, RecyclerView.State, SmoothScroller.Action)} or
         * {@link #onSeekTargetStep(int, int, RecyclerView.State, SmoothScroller.Action)}.
         */
        protected final void stop() {
            if (!mRunning) {
                return;
            }
            mRunning = false;
            onStop();
            mRecyclerView.mState.mTargetPosition = RecyclerView.NO_POSITION;
            mTargetView = null;
            mTargetPosition = RecyclerView.NO_POSITION;
            mPendingInitialRun = false;
            // trigger a cleanup
            mLayoutManager.onSmoothScrollerStopped(this);
            // clear references to avoid any potential leak by a custom smooth scroller
            mLayoutManager = null;
            mRecyclerView = null;
        }

        /**
         * Returns true if SmoothScroller has been started but has not received the first
         * animation
         * callback yet.
         *
         * @return True if this SmoothScroller is waiting to start
         */
        public boolean isPendingInitialRun() {
            return mPendingInitialRun;
        }


        /**
         * @return True if SmoothScroller is currently active
         */
        public boolean isRunning() {
            return mRunning;
        }

        /**
         * Returns the adapter position of the target item
         *
         * @return Adapter position of the target item or
         * {@link RecyclerView#NO_POSITION} if no target view is set.
         */
        public int getTargetPosition() {
            return mTargetPosition;
        }

        void onAnimation(int dx, int dy) {
            final RecyclerView recyclerView = mRecyclerView;
            if (mTargetPosition == RecyclerView.NO_POSITION || recyclerView == null) {
                stop();
            }

            // The following if block exists to have the LayoutManager scroll 1 pixel in the correct
            // direction in order to cause the LayoutManager to draw two pages worth of views so
            // that the target view may be found before scrolling any further.  This is done to
            // prevent an initial scroll distance from scrolling past the view, which causes a
            // jittery looking animation.
            if (mPendingInitialRun && mTargetView == null && mLayoutManager != null) {
                PointF pointF = computeScrollVectorForPosition(mTargetPosition);
                if (pointF != null && (pointF.x != 0 || pointF.y != 0)) {
                    recyclerView.scrollStep(
                            (int) Math.signum(pointF.x),
                            (int) Math.signum(pointF.y),
                            null);
                }
            }

            mPendingInitialRun = false;

            if (mTargetView != null) {
                // verify target position
                if (getChildPosition(mTargetView) == mTargetPosition) {
                    onTargetFound(mTargetView, recyclerView.mState, mRecyclingAction);
                    mRecyclingAction.runIfNecessary(recyclerView);
                    stop();
                } else {
                    Log.e(TAG, "Passed over target position while smooth scrolling.");
                    mTargetView = null;
                }
            }
            if (mRunning) {
                onSeekTargetStep(dx, dy, recyclerView.mState, mRecyclingAction);
                boolean hadJumpTarget = mRecyclingAction.hasJumpTarget();
                mRecyclingAction.runIfNecessary(recyclerView);
                if (hadJumpTarget) {
                    // It is not stopped so needs to be restarted
                    if (mRunning) {
                        mPendingInitialRun = true;
                        recyclerView.mViewFlinger.postOnAnimation();
                    }
                }
            }
        }

        /**
         * @see RecyclerView#getChildLayoutPosition(android.view.View)
         */
        public int getChildPosition(View view) {
            return mRecyclerView.getChildLayoutPosition(view);
        }

        /**
         * @see RecyclerView.LayoutManager#getChildCount()
         */
        public int getChildCount() {
            return mRecyclerView.mLayout.getChildCount();
        }

        /**
         * @see RecyclerView.LayoutManager#findViewByPosition(int)
         */
        public View findViewByPosition(int position) {
            return mRecyclerView.mLayout.findViewByPosition(position);
        }

        /**
         * @see RecyclerView#scrollToPosition(int)
         * @deprecated Use {@link Action#jumpTo(int)}.
         */
        @Deprecated
        public void instantScrollToPosition(int position) {
            mRecyclerView.scrollToPosition(position);
        }

        protected void onChildAttachedToWindow(View child) {
            if (getChildPosition(child) == getTargetPosition()) {
                mTargetView = child;
                if (DEBUG) {
                    Log.d(TAG, "smooth scroll target view has been attached");
                }
            }
        }

        /**
         * Normalizes the vector.
         * @param scrollVector The vector that points to the target scroll position
         */
        protected void normalize(@NonNull PointF scrollVector) {
            final float magnitude = (float) Math.sqrt(scrollVector.x * scrollVector.x
                    + scrollVector.y * scrollVector.y);
            scrollVector.x /= magnitude;
            scrollVector.y /= magnitude;
        }

        /**
         * Called when smooth scroll is started. This might be a good time to do setup.
         */
        protected abstract void onStart();

        /**
         * Called when smooth scroller is stopped. This is a good place to cleanup your state etc.
         * @see #stop()
         */
        protected abstract void onStop();

        /**
         * <p>RecyclerView will call this method each time it scrolls until it can find the target
         * position in the layout.</p>
         * <p>SmoothScroller should check dx, dy and if scroll should be changed, update the
         * provided {@link Action} to define the next scroll.</p>
         *
         * @param dx        Last scroll amount horizontally
         * @param dy        Last scroll amount vertically
         * @param state     Transient state of RecyclerView
         * @param action    If you want to trigger a new smooth scroll and cancel the previous one,
         *                  update this object.
         */
        protected abstract void onSeekTargetStep(@Px int dx, @Px int dy, @NonNull State state,
                @NonNull Action action);

        /**
         * Called when the target position is laid out. This is the last callback SmoothScroller
         * will receive and it should update the provided {@link Action} to define the scroll
         * details towards the target view.
         * @param targetView    The view element which render the target position.
         * @param state         Transient state of RecyclerView
         * @param action        Action instance that you should update to define final scroll action
         *                      towards the targetView
         */
        protected abstract void onTargetFound(@NonNull View targetView, @NonNull State state,
                @NonNull Action action);

        /**
         * Holds information about a smooth scroll request by a {@link SmoothScroller}.
         */
        public static class Action {

            public static final int UNDEFINED_DURATION = RecyclerView.UNDEFINED_DURATION;

            private int mDx;

            private int mDy;

            private int mDuration;

            private int mJumpToPosition = NO_POSITION;

            private Interpolator mInterpolator;

            private boolean mChanged = false;

            // we track this variable to inform custom implementer if they are updating the action
            // in every animation callback
            private int mConsecutiveUpdates = 0;

            /**
             * @param dx Pixels to scroll horizontally
             * @param dy Pixels to scroll vertically
             */
            public Action(@Px int dx, @Px int dy) {
                this(dx, dy, UNDEFINED_DURATION, null);
            }

            /**
             * @param dx       Pixels to scroll horizontally
             * @param dy       Pixels to scroll vertically
             * @param duration Duration of the animation in milliseconds
             */
            public Action(@Px int dx, @Px int dy, int duration) {
                this(dx, dy, duration, null);
            }

            /**
             * @param dx           Pixels to scroll horizontally
             * @param dy           Pixels to scroll vertically
             * @param duration     Duration of the animation in milliseconds
             * @param interpolator Interpolator to be used when calculating scroll position in each
             *                     animation step
             */
            public Action(@Px int dx, @Px int dy, int duration,
                    @Nullable Interpolator interpolator) {
                mDx = dx;
                mDy = dy;
                mDuration = duration;
                mInterpolator = interpolator;
            }

            /**
             * Instead of specifying pixels to scroll, use the target position to jump using
             * {@link RecyclerView#scrollToPosition(int)}.
             * <p>
             * You may prefer using this method if scroll target is really far away and you prefer
             * to jump to a location and smooth scroll afterwards.
             * <p>
             * Note that calling this method takes priority over other update methods such as
             * {@link #update(int, int, int, Interpolator)}, {@link #setX(float)},
             * {@link #setY(float)} and #{@link #setInterpolator(Interpolator)}. If you call
             * {@link #jumpTo(int)}, the other changes will not be considered for this animation
             * frame.
             *
             * @param targetPosition The target item position to scroll to using instant scrolling.
             */
            public void jumpTo(int targetPosition) {
                mJumpToPosition = targetPosition;
            }

            boolean hasJumpTarget() {
                return mJumpToPosition >= 0;
            }

            void runIfNecessary(RecyclerView recyclerView) {
                if (mJumpToPosition >= 0) {
                    final int position = mJumpToPosition;
                    mJumpToPosition = NO_POSITION;
                    recyclerView.jumpToPositionForSmoothScroller(position);
                    mChanged = false;
                    return;
                }
                if (mChanged) {
                    validate();
                    recyclerView.mViewFlinger.smoothScrollBy(mDx, mDy, mDuration, mInterpolator);
                    mConsecutiveUpdates++;
                    if (mConsecutiveUpdates > 10) {
                        // A new action is being set in every animation step. This looks like a bad
                        // implementation. Inform developer.
                        Log.e(TAG, "Smooth Scroll action is being updated too frequently. Make sure"
                                + " you are not changing it unless necessary");
                    }
                    mChanged = false;
                } else {
                    mConsecutiveUpdates = 0;
                }
            }

            private void validate() {
                if (mInterpolator != null && mDuration < 1) {
                    throw new IllegalStateException("If you provide an interpolator, you must"
                            + " set a positive duration");
                } else if (mDuration < 1) {
                    throw new IllegalStateException("Scroll duration must be a positive number");
                }
            }

            @Px
            public int getDx() {
                return mDx;
            }

            public void setDx(@Px int dx) {
                mChanged = true;
                mDx = dx;
            }

            @Px
            public int getDy() {
                return mDy;
            }

            public void setDy(@Px int dy) {
                mChanged = true;
                mDy = dy;
            }

            public int getDuration() {
                return mDuration;
            }

            public void setDuration(int duration) {
                mChanged = true;
                mDuration = duration;
            }

            @Nullable
            public Interpolator getInterpolator() {
                return mInterpolator;
            }

            /**
             * Sets the interpolator to calculate scroll steps
             * @param interpolator The interpolator to use. If you specify an interpolator, you must
             *                     also set the duration.
             * @see #setDuration(int)
             */
            public void setInterpolator(@Nullable Interpolator interpolator) {
                mChanged = true;
                mInterpolator = interpolator;
            }

            /**
             * Updates the action with given parameters.
             * @param dx Pixels to scroll horizontally
             * @param dy Pixels to scroll vertically
             * @param duration Duration of the animation in milliseconds
             * @param interpolator Interpolator to be used when calculating scroll position in each
             *                     animation step
             */
            public void update(@Px int dx, @Px int dy, int duration,
                    @Nullable Interpolator interpolator) {
                mDx = dx;
                mDy = dy;
                mDuration = duration;
                mInterpolator = interpolator;
                mChanged = true;
            }
        }

        /**
         * An interface which is optionally implemented by custom {@link RecyclerView.LayoutManager}
         * to provide a hint to a {@link SmoothScroller} about the location of the target position.
         */
        public interface ScrollVectorProvider {
            /**
             * Should calculate the vector that points to the direction where the target position
             * can be found.
             * <p>
             * This method is used by the {@link LinearSmoothScroller} to initiate a scroll towards
             * the target position.
             * <p>
             * The magnitude of the vector is not important. It is always normalized before being
             * used by the {@link LinearSmoothScroller}.
             * <p>
             * LayoutManager should not check whether the position exists in the adapter or not.
             *
             * @param targetPosition the target position to which the returned vector should point
             *
             * @return the scroll vector for a given position.
             */
            @Nullable
            PointF computeScrollVectorForPosition(int targetPosition);
        }
    }

    static class AdapterDataObservable extends Observable<AdapterDataObserver> {
        public boolean hasObservers() {
            return !mObservers.isEmpty();
        }

        public void notifyChanged() {
            // since onChanged() is implemented by the app, it could do anything, including
            // removing itself from {@link mObservers} - and that could cause problems if
            // an iterator is used on the ArrayList {@link mObservers}.
            // to avoid such problems, just march thru the list in the reverse order.
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onChanged();
            }
        }

        public void notifyItemRangeChanged(int positionStart, int itemCount) {
            notifyItemRangeChanged(positionStart, itemCount, null);
        }

        public void notifyItemRangeChanged(int positionStart, int itemCount,
                @Nullable Object payload) {
            // since onItemRangeChanged() is implemented by the app, it could do anything, including
            // removing itself from {@link mObservers} - and that could cause problems if
            // an iterator is used on the ArrayList {@link mObservers}.
            // to avoid such problems, just march thru the list in the reverse order.
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onItemRangeChanged(positionStart, itemCount, payload);
            }
        }

        public void notifyItemRangeInserted(int positionStart, int itemCount) {
            // since onItemRangeInserted() is implemented by the app, it could do anything,
            // including removing itself from {@link mObservers} - and that could cause problems if
            // an iterator is used on the ArrayList {@link mObservers}.
            // to avoid such problems, just march thru the list in the reverse order.
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onItemRangeInserted(positionStart, itemCount);
            }
        }

        public void notifyItemRangeRemoved(int positionStart, int itemCount) {
            // since onItemRangeRemoved() is implemented by the app, it could do anything, including
            // removing itself from {@link mObservers} - and that could cause problems if
            // an iterator is used on the ArrayList {@link mObservers}.
            // to avoid such problems, just march thru the list in the reverse order.
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onItemRangeRemoved(positionStart, itemCount);
            }
        }

        public void notifyItemMoved(int fromPosition, int toPosition) {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onItemRangeMoved(fromPosition, toPosition, 1);
            }
        }
    }

    /**
     * This is public so that the CREATOR can be accessed on cold launch.
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP_PREFIX)
    public static class SavedState extends AbsSavedState {

        Parcelable mLayoutState;

        /**
         * called by CREATOR
         */
        SavedState(Parcel in, ClassLoader loader) {
            super(in, loader);
            mLayoutState = in.readParcelable(
                    loader != null ? loader : LayoutManager.class.getClassLoader());
        }

        /**
         * Called by onSaveInstanceState
         */
        SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeParcelable(mLayoutState, 0);
        }

        void copyFrom(SavedState other) {
            mLayoutState = other.mLayoutState;
        }

        public static final Creator<SavedState> CREATOR = new ClassLoaderCreator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                return new SavedState(in, loader);
            }

            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in, null);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    /**
     * <p>Contains useful information about the current RecyclerView state like target scroll
     * position or view focus. State object can also keep arbitrary data, identified by resource
     * ids.</p>
     * <p>Often times, RecyclerView components will need to pass information between each other.
     * To provide a well defined data bus between components, RecyclerView passes the same State
     * object to component callbacks and these components can use it to exchange data.</p>
     * <p>If you implement custom components, you can use State's put/get/remove methods to pass
     * data between your components without needing to manage their lifecycles.</p>
     */
    public static class State {
        static final int STEP_START = 1;
        static final int STEP_LAYOUT = 1 << 1;
        static final int STEP_ANIMATIONS = 1 << 2;

        void assertLayoutStep(int accepted) {
            if ((accepted & mLayoutStep) == 0) {
                throw new IllegalStateException("Layout state should be one of "
                        + Integer.toBinaryString(accepted) + " but it is "
                        + Integer.toBinaryString(mLayoutStep));
            }
        }


        /** Owned by SmoothScroller */
        int mTargetPosition = RecyclerView.NO_POSITION;

        private SparseArray<Object> mData;

        ////////////////////////////////////////////////////////////////////////////////////////////
        // Fields below are carried from one layout pass to the next
        ////////////////////////////////////////////////////////////////////////////////////////////

        /**
         * Number of items adapter had in the previous layout.
         */
        int mPreviousLayoutItemCount = 0;

        /**
         * Number of items that were NOT laid out but has been deleted from the adapter after the
         * previous layout.
         */
        int mDeletedInvisibleItemCountSincePreviousLayout = 0;

        ////////////////////////////////////////////////////////////////////////////////////////////
        // Fields below must be updated or cleared before they are used (generally before a pass)
        ////////////////////////////////////////////////////////////////////////////////////////////

        @IntDef(flag = true, value = {
                STEP_START, STEP_LAYOUT, STEP_ANIMATIONS
        })
        @Retention(RetentionPolicy.SOURCE)
        @interface LayoutState {}

        @LayoutState
        int mLayoutStep = STEP_START;

        /**
         * Number of items adapter has.
         */
        int mItemCount = 0;

        boolean mStructureChanged = false;

        /**
         * True if the associated {@link RecyclerView} is in the pre-layout step where it is having
         * its {@link LayoutManager} layout items where they will be at the beginning of a set of
         * predictive item animations.
         */
        boolean mInPreLayout = false;

        boolean mTrackOldChangeHolders = false;

        boolean mIsMeasuring = false;

        ////////////////////////////////////////////////////////////////////////////////////////////
        // Fields below are always reset outside of the pass (or passes) that use them
        ////////////////////////////////////////////////////////////////////////////////////////////

        boolean mRunSimpleAnimations = false;

        boolean mRunPredictiveAnimations = false;

        /**
         * This data is saved before a layout calculation happens. After the layout is finished,
         * if the previously focused view has been replaced with another view for the same item, we
         * move the focus to the new item automatically.
         */
        int mFocusedItemPosition;
        long mFocusedItemId;
        // when a sub child has focus, record its id and see if we can directly request focus on
        // that one instead
        int mFocusedSubChildId;

        int mRemainingScrollHorizontal;
        int mRemainingScrollVertical;

        ////////////////////////////////////////////////////////////////////////////////////////////

        /**
         * Prepare for a prefetch occurring on the RecyclerView in between traversals, potentially
         * prior to any layout passes.
         *
         * <p>Don't touch any state stored between layout passes, only reset per-layout state, so
         * that Recycler#getViewForPosition() can function safely.</p>
         */
        void prepareForNestedPrefetch(Adapter adapter) {
            mLayoutStep = STEP_START;
            mItemCount = adapter.getItemCount();
            mInPreLayout = false;
            mTrackOldChangeHolders = false;
            mIsMeasuring = false;
        }

        /**
         * Returns true if the RecyclerView is currently measuring the layout. This value is
         * {@code true} only if the LayoutManager opted into the auto measure API and RecyclerView
         * has non-exact measurement specs.
         * <p>
         * Note that if the LayoutManager supports predictive animations and it is calculating the
         * pre-layout step, this value will be {@code false} even if the RecyclerView is in
         * {@code onMeasure} call. This is because pre-layout means the previous state of the
         * RecyclerView and measurements made for that state cannot change the RecyclerView's size.
         * LayoutManager is always guaranteed to receive another call to
         * {@link LayoutManager#onLayoutChildren(Recycler, State)} when this happens.
         *
         * @return True if the RecyclerView is currently calculating its bounds, false otherwise.
         */
        public boolean isMeasuring() {
            return mIsMeasuring;
        }

        /**
         * Returns true if the {@link RecyclerView} is in the pre-layout step where it is having its
         * {@link LayoutManager} layout items where they will be at the beginning of a set of
         * predictive item animations.
         */
        public boolean isPreLayout() {
            return mInPreLayout;
        }

        /**
         * Returns whether RecyclerView will run predictive animations in this layout pass
         * or not.
         *
         * @return true if RecyclerView is calculating predictive animations to be run at the end
         *         of the layout pass.
         */
        public boolean willRunPredictiveAnimations() {
            return mRunPredictiveAnimations;
        }

        /**
         * Returns whether RecyclerView will run simple animations in this layout pass
         * or not.
         *
         * @return true if RecyclerView is calculating simple animations to be run at the end of
         *         the layout pass.
         */
        public boolean willRunSimpleAnimations() {
            return mRunSimpleAnimations;
        }

        /**
         * Removes the mapping from the specified id, if there was any.
         * @param resourceId Id of the resource you want to remove. It is suggested to use R.id.* to
         *                   preserve cross functionality and avoid conflicts.
         */
        public void remove(int resourceId) {
            if (mData == null) {
                return;
            }
            mData.remove(resourceId);
        }

        /**
         * Gets the Object mapped from the specified id, or <code>null</code>
         * if no such data exists.
         *
         * @param resourceId Id of the resource you want to remove. It is suggested to use R.id.*
         *                   to
         *                   preserve cross functionality and avoid conflicts.
         */
        @SuppressWarnings("TypeParameterUnusedInFormals")
        public <T> T get(int resourceId) {
            if (mData == null) {
                return null;
            }
            return (T) mData.get(resourceId);
        }

        /**
         * Adds a mapping from the specified id to the specified value, replacing the previous
         * mapping from the specified key if there was one.
         *
         * @param resourceId Id of the resource you want to add. It is suggested to use R.id.* to
         *                   preserve cross functionality and avoid conflicts.
         * @param data       The data you want to associate with the resourceId.
         */
        public void put(int resourceId, Object data) {
            if (mData == null) {
                mData = new SparseArray<Object>();
            }
            mData.put(resourceId, data);
        }

        /**
         * If scroll is triggered to make a certain item visible, this value will return the
         * adapter index of that item.
         * @return Adapter index of the target item or
         * {@link RecyclerView#NO_POSITION} if there is no target
         * position.
         */
        public int getTargetScrollPosition() {
            return mTargetPosition;
        }

        /**
         * Returns if current scroll has a target position.
         * @return true if scroll is being triggered to make a certain position visible
         * @see #getTargetScrollPosition()
         */
        public boolean hasTargetScrollPosition() {
            return mTargetPosition != RecyclerView.NO_POSITION;
        }

        /**
         * @return true if the structure of the data set has changed since the last call to
         *         onLayoutChildren, false otherwise
         */
        public boolean didStructureChange() {
            return mStructureChanged;
        }

        /**
         * Returns the total number of items that can be laid out. Note that this number is not
         * necessarily equal to the number of items in the adapter, so you should always use this
         * number for your position calculations and never access the adapter directly.
         * <p>
         * RecyclerView listens for Adapter's notify events and calculates the effects of adapter
         * data changes on existing Views. These calculations are used to decide which animations
         * should be run.
         * <p>
         * To support predictive animations, RecyclerView may rewrite or reorder Adapter changes to
         * present the correct state to LayoutManager in pre-layout pass.
         * <p>
         * For example, a newly added item is not included in pre-layout item count because
         * pre-layout reflects the contents of the adapter before the item is added. Behind the
         * scenes, RecyclerView offsets {@link Recycler#getViewForPosition(int)} calls such that
         * LayoutManager does not know about the new item's existence in pre-layout. The item will
         * be available in second layout pass and will be included in the item count. Similar
         * adjustments are made for moved and removed items as well.
         * <p>
         * You can get the adapter's item count via {@link LayoutManager#getItemCount()} method.
         *
         * @return The number of items currently available
         * @see LayoutManager#getItemCount()
         */
        public int getItemCount() {
            return mInPreLayout
                    ? (mPreviousLayoutItemCount - mDeletedInvisibleItemCountSincePreviousLayout)
                    : mItemCount;
        }

        /**
         * Returns remaining horizontal scroll distance of an ongoing scroll animation(fling/
         * smoothScrollTo/SmoothScroller) in pixels. Returns zero if {@link #getScrollState()} is
         * other than {@link #SCROLL_STATE_SETTLING}.
         *
         * @return Remaining horizontal scroll distance
         */
        public int getRemainingScrollHorizontal() {
            return mRemainingScrollHorizontal;
        }

        /**
         * Returns remaining vertical scroll distance of an ongoing scroll animation(fling/
         * smoothScrollTo/SmoothScroller) in pixels. Returns zero if {@link #getScrollState()} is
         * other than {@link #SCROLL_STATE_SETTLING}.
         *
         * @return Remaining vertical scroll distance
         */
        public int getRemainingScrollVertical() {
            return mRemainingScrollVertical;
        }

        @Override
        public String toString() {
            return "State{"
                    + "mTargetPosition=" + mTargetPosition
                    + ", mData=" + mData
                    + ", mItemCount=" + mItemCount
                    + ", mIsMeasuring=" + mIsMeasuring
                    + ", mPreviousLayoutItemCount=" + mPreviousLayoutItemCount
                    + ", mDeletedInvisibleItemCountSincePreviousLayout="
                    + mDeletedInvisibleItemCountSincePreviousLayout
                    + ", mStructureChanged=" + mStructureChanged
                    + ", mInPreLayout=" + mInPreLayout
                    + ", mRunSimpleAnimations=" + mRunSimpleAnimations
                    + ", mRunPredictiveAnimations=" + mRunPredictiveAnimations
                    + '}';
        }
    }

    /**
     * This class defines the behavior of fling if the developer wishes to handle it.
     * <p>
     * Subclasses of {@link OnFlingListener} can be used to implement custom fling behavior.
     *
     * @see #setOnFlingListener(OnFlingListener)
     */
    public abstract static class OnFlingListener {

        /**
         * Override this to handle a fling given the velocities in both x and y directions.
         * Note that this method will only be called if the associated {@link LayoutManager}
         * supports scrolling and the fling is not handled by nested scrolls first.
         *
         * @param velocityX the fling velocity on the X axis
         * @param velocityY the fling velocity on the Y axis
         *
         * @return true if the fling was handled, false otherwise.
         */
        public abstract boolean onFling(int velocityX, int velocityY);
    }

    /**
     * Internal listener that manages items after animations finish. This is how items are
     * retained (not recycled) during animations, but allowed to be recycled afterwards.
     * It depends on the contract with the ItemAnimator to call the appropriate dispatch*Finished()
     * method on the animator's listener when it is done animating any item.
     */
    private class ItemAnimatorRestoreListener implements ItemAnimator.ItemAnimatorListener {

        ItemAnimatorRestoreListener() {
        }

        @Override
        public void onAnimationFinished(ViewHolder item) {
            item.setIsRecyclable(true);
            if (item.mShadowedHolder != null && item.mShadowingHolder == null) { // old vh
                item.mShadowedHolder = null;
            }
            // always null this because an OldViewHolder can never become NewViewHolder w/o being
            // recycled.
            item.mShadowingHolder = null;
            if (!item.shouldBeKeptAsChild()) {
                if (!removeAnimatingView(item.itemView) && item.isTmpDetached()) {
                    removeDetachedView(item.itemView, false);
                }
            }
        }
    }

    /**
     * This class defines the animations that take place on items as changes are made
     * to the adapter.
     *
     * Subclasses of ItemAnimator can be used to implement custom animations for actions on
     * ViewHolder items. The RecyclerView will manage retaining these items while they
     * are being animated, but implementors must call {@link #dispatchAnimationFinished(ViewHolder)}
     * when a ViewHolder's animation is finished. In other words, there must be a matching
     * {@link #dispatchAnimationFinished(ViewHolder)} call for each
     * {@link #animateAppearance(ViewHolder, ItemHolderInfo, ItemHolderInfo) animateAppearance()},
     * {@link #animateChange(ViewHolder, ViewHolder, ItemHolderInfo, ItemHolderInfo)
     * animateChange()}
     * {@link #animatePersistence(ViewHolder, ItemHolderInfo, ItemHolderInfo) animatePersistence()},
     * and
     * {@link #animateDisappearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)
     * animateDisappearance()} call.
     *
     * <p>By default, RecyclerView uses {@link DefaultItemAnimator}.</p>
     *
     * @see #setItemAnimator(ItemAnimator)
     */
    @SuppressWarnings("UnusedParameters")
    public abstract static class ItemAnimator {

        /**
         * The Item represented by this ViewHolder is updated.
         * <p>
         * @see #recordPreLayoutInformation(State, ViewHolder, int, List)
         */
        public static final int FLAG_CHANGED = ViewHolder.FLAG_UPDATE;

        /**
         * The Item represented by this ViewHolder is removed from the adapter.
         * <p>
         * @see #recordPreLayoutInformation(State, ViewHolder, int, List)
         */
        public static final int FLAG_REMOVED = ViewHolder.FLAG_REMOVED;

        /**
         * Adapter {@link Adapter#notifyDataSetChanged()} has been called and the content
         * represented by this ViewHolder is invalid.
         * <p>
         * @see #recordPreLayoutInformation(State, ViewHolder, int, List)
         */
        public static final int FLAG_INVALIDATED = ViewHolder.FLAG_INVALID;

        /**
         * The position of the Item represented by this ViewHolder has been changed. This flag is
         * not bound to {@link Adapter#notifyItemMoved(int, int)}. It might be set in response to
         * any adapter change that may have a side effect on this item. (e.g. The item before this
         * one has been removed from the Adapter).
         * <p>
         * @see #recordPreLayoutInformation(State, ViewHolder, int, List)
         */
        public static final int FLAG_MOVED = ViewHolder.FLAG_MOVED;

        /**
         * This ViewHolder was not laid out but has been added to the layout in pre-layout state
         * by the {@link LayoutManager}. This means that the item was already in the Adapter but
         * invisible and it may become visible in the post layout phase. LayoutManagers may prefer
         * to add new items in pre-layout to specify their virtual location when they are invisible
         * (e.g. to specify the item should <i>animate in</i> from below the visible area).
         * <p>
         * @see #recordPreLayoutInformation(State, ViewHolder, int, List)
         */
        public static final int FLAG_APPEARED_IN_PRE_LAYOUT =
                ViewHolder.FLAG_APPEARED_IN_PRE_LAYOUT;

        /**
         * The set of flags that might be passed to
         * {@link #recordPreLayoutInformation(State, ViewHolder, int, List)}.
         */
        @IntDef(flag = true, value = {
                FLAG_CHANGED, FLAG_REMOVED, FLAG_MOVED, FLAG_INVALIDATED,
                FLAG_APPEARED_IN_PRE_LAYOUT
        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface AdapterChanges {}
        private ItemAnimatorListener mListener = null;
        private ArrayList<ItemAnimatorFinishedListener> mFinishedListeners =
                new ArrayList<ItemAnimatorFinishedListener>();

        private long mAddDuration = 120;
        private long mRemoveDuration = 120;
        private long mMoveDuration = 250;
        private long mChangeDuration = 250;

        /**
         * Gets the current duration for which all move animations will run.
         *
         * @return The current move duration
         */
        public long getMoveDuration() {
            return mMoveDuration;
        }

        /**
         * Sets the duration for which all move animations will run.
         *
         * @param moveDuration The move duration
         */
        public void setMoveDuration(long moveDuration) {
            mMoveDuration = moveDuration;
        }

        /**
         * Gets the current duration for which all add animations will run.
         *
         * @return The current add duration
         */
        public long getAddDuration() {
            return mAddDuration;
        }

        /**
         * Sets the duration for which all add animations will run.
         *
         * @param addDuration The add duration
         */
        public void setAddDuration(long addDuration) {
            mAddDuration = addDuration;
        }

        /**
         * Gets the current duration for which all remove animations will run.
         *
         * @return The current remove duration
         */
        public long getRemoveDuration() {
            return mRemoveDuration;
        }

        /**
         * Sets the duration for which all remove animations will run.
         *
         * @param removeDuration The remove duration
         */
        public void setRemoveDuration(long removeDuration) {
            mRemoveDuration = removeDuration;
        }

        /**
         * Gets the current duration for which all change animations will run.
         *
         * @return The current change duration
         */
        public long getChangeDuration() {
            return mChangeDuration;
        }

        /**
         * Sets the duration for which all change animations will run.
         *
         * @param changeDuration The change duration
         */
        public void setChangeDuration(long changeDuration) {
            mChangeDuration = changeDuration;
        }

        /**
         * Internal only:
         * Sets the listener that must be called when the animator is finished
         * animating the item (or immediately if no animation happens). This is set
         * internally and is not intended to be set by external code.
         *
         * @param listener The listener that must be called.
         */
        void setListener(ItemAnimatorListener listener) {
            mListener = listener;
        }

        /**
         * Called by the RecyclerView before the layout begins. Item animator should record
         * necessary information about the View before it is potentially rebound, moved or removed.
         * <p>
         * The data returned from this method will be passed to the related <code>animate**</code>
         * methods.
         * <p>
         * Note that this method may be called after pre-layout phase if LayoutManager adds new
         * Views to the layout in pre-layout pass.
         * <p>
         * The default implementation returns an {@link ItemHolderInfo} which holds the bounds of
         * the View and the adapter change flags.
         *
         * @param state       The current State of RecyclerView which includes some useful data
         *                    about the layout that will be calculated.
         * @param viewHolder  The ViewHolder whose information should be recorded.
         * @param changeFlags Additional information about what changes happened in the Adapter
         *                    about the Item represented by this ViewHolder. For instance, if
         *                    item is deleted from the adapter, {@link #FLAG_REMOVED} will be set.
         * @param payloads    The payload list that was previously passed to
         *                    {@link Adapter#notifyItemChanged(int, Object)} or
         *                    {@link Adapter#notifyItemRangeChanged(int, int, Object)}.
         *
         * @return An ItemHolderInfo instance that preserves necessary information about the
         * ViewHolder. This object will be passed back to related <code>animate**</code> methods
         * after layout is complete.
         *
         * @see #recordPostLayoutInformation(State, ViewHolder)
         * @see #animateAppearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)
         * @see #animateDisappearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)
         * @see #animateChange(ViewHolder, ViewHolder, ItemHolderInfo, ItemHolderInfo)
         * @see #animatePersistence(ViewHolder, ItemHolderInfo, ItemHolderInfo)
         */
        public @NonNull ItemHolderInfo recordPreLayoutInformation(@NonNull State state,
                @NonNull ViewHolder viewHolder, @AdapterChanges int changeFlags,
                @NonNull List<Object> payloads) {
            return obtainHolderInfo().setFrom(viewHolder);
        }

        /**
         * Called by the RecyclerView after the layout is complete. Item animator should record
         * necessary information about the View's final state.
         * <p>
         * The data returned from this method will be passed to the related <code>animate**</code>
         * methods.
         * <p>
         * The default implementation returns an {@link ItemHolderInfo} which holds the bounds of
         * the View.
         *
         * @param state      The current State of RecyclerView which includes some useful data about
         *                   the layout that will be calculated.
         * @param viewHolder The ViewHolder whose information should be recorded.
         *
         * @return An ItemHolderInfo that preserves necessary information about the ViewHolder.
         * This object will be passed back to related <code>animate**</code> methods when
         * RecyclerView decides how items should be animated.
         *
         * @see #recordPreLayoutInformation(State, ViewHolder, int, List)
         * @see #animateAppearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)
         * @see #animateDisappearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)
         * @see #animateChange(ViewHolder, ViewHolder, ItemHolderInfo, ItemHolderInfo)
         * @see #animatePersistence(ViewHolder, ItemHolderInfo, ItemHolderInfo)
         */
        public @NonNull ItemHolderInfo recordPostLayoutInformation(@NonNull State state,
                @NonNull ViewHolder viewHolder) {
            return obtainHolderInfo().setFrom(viewHolder);
        }

        /**
         * Called by the RecyclerView when a ViewHolder has disappeared from the layout.
         * <p>
         * This means that the View was a child of the LayoutManager when layout started but has
         * been removed by the LayoutManager. It might have been removed from the adapter or simply
         * become invisible due to other factors. You can distinguish these two cases by checking
         * the change flags that were passed to
         * {@link #recordPreLayoutInformation(State, ViewHolder, int, List)}.
         * <p>
         * Note that when a ViewHolder both changes and disappears in the same layout pass, the
         * animation callback method which will be called by the RecyclerView depends on the
         * ItemAnimator's decision whether to re-use the same ViewHolder or not, and also the
         * LayoutManager's decision whether to layout the changed version of a disappearing
         * ViewHolder or not. RecyclerView will call
         * {@link #animateChange(ViewHolder, ViewHolder, ItemHolderInfo, ItemHolderInfo)
         * animateChange} instead of {@code animateDisappearance} if and only if the ItemAnimator
         * returns {@code false} from
         * {@link #canReuseUpdatedViewHolder(ViewHolder) canReuseUpdatedViewHolder} and the
         * LayoutManager lays out a new disappearing view that holds the updated information.
         * Built-in LayoutManagers try to avoid laying out updated versions of disappearing views.
         * <p>
         * If LayoutManager supports predictive animations, it might provide a target disappear
         * location for the View by laying it out in that location. When that happens,
         * RecyclerView will call {@link #recordPostLayoutInformation(State, ViewHolder)} and the
         * response of that call will be passed to this method as the <code>postLayoutInfo</code>.
         * <p>
         * ItemAnimator must call {@link #dispatchAnimationFinished(ViewHolder)} when the animation
         * is complete (or instantly call {@link #dispatchAnimationFinished(ViewHolder)} if it
         * decides not to animate the view).
         *
         * @param viewHolder    The ViewHolder which should be animated
         * @param preLayoutInfo The information that was returned from
         *                      {@link #recordPreLayoutInformation(State, ViewHolder, int, List)}.
         * @param postLayoutInfo The information that was returned from
         *                       {@link #recordPostLayoutInformation(State, ViewHolder)}. Might be
         *                       null if the LayoutManager did not layout the item.
         *
         * @return true if a later call to {@link #runPendingAnimations()} is requested,
         * false otherwise.
         */
        public abstract boolean animateDisappearance(@NonNull ViewHolder viewHolder,
                @NonNull ItemHolderInfo preLayoutInfo, @Nullable ItemHolderInfo postLayoutInfo);

        /**
         * Called by the RecyclerView when a ViewHolder is added to the layout.
         * <p>
         * In detail, this means that the ViewHolder was <b>not</b> a child when the layout started
         * but has  been added by the LayoutManager. It might be newly added to the adapter or
         * simply become visible due to other factors.
         * <p>
         * ItemAnimator must call {@link #dispatchAnimationFinished(ViewHolder)} when the animation
         * is complete (or instantly call {@link #dispatchAnimationFinished(ViewHolder)} if it
         * decides not to animate the view).
         *
         * @param viewHolder     The ViewHolder which should be animated
         * @param preLayoutInfo  The information that was returned from
         *                       {@link #recordPreLayoutInformation(State, ViewHolder, int, List)}.
         *                       Might be null if Item was just added to the adapter or
         *                       LayoutManager does not support predictive animations or it could
         *                       not predict that this ViewHolder will become visible.
         * @param postLayoutInfo The information that was returned from {@link
         *                       #recordPreLayoutInformation(State, ViewHolder, int, List)}.
         *
         * @return true if a later call to {@link #runPendingAnimations()} is requested,
         * false otherwise.
         */
        public abstract boolean animateAppearance(@NonNull ViewHolder viewHolder,
                @Nullable ItemHolderInfo preLayoutInfo, @NonNull ItemHolderInfo postLayoutInfo);

        /**
         * Called by the RecyclerView when a ViewHolder is present in both before and after the
         * layout and RecyclerView has not received a {@link Adapter#notifyItemChanged(int)} call
         * for it or a {@link Adapter#notifyDataSetChanged()} call.
         * <p>
         * This ViewHolder still represents the same data that it was representing when the layout
         * started but its position / size may be changed by the LayoutManager.
         * <p>
         * If the Item's layout position didn't change, RecyclerView still calls this method because
         * it does not track this information (or does not necessarily know that an animation is
         * not required). Your ItemAnimator should handle this case and if there is nothing to
         * animate, it should call {@link #dispatchAnimationFinished(ViewHolder)} and return
         * <code>false</code>.
         * <p>
         * ItemAnimator must call {@link #dispatchAnimationFinished(ViewHolder)} when the animation
         * is complete (or instantly call {@link #dispatchAnimationFinished(ViewHolder)} if it
         * decides not to animate the view).
         *
         * @param viewHolder     The ViewHolder which should be animated
         * @param preLayoutInfo  The information that was returned from
         *                       {@link #recordPreLayoutInformation(State, ViewHolder, int, List)}.
         * @param postLayoutInfo The information that was returned from {@link
         *                       #recordPreLayoutInformation(State, ViewHolder, int, List)}.
         *
         * @return true if a later call to {@link #runPendingAnimations()} is requested,
         * false otherwise.
         */
        public abstract boolean animatePersistence(@NonNull ViewHolder viewHolder,
                @NonNull ItemHolderInfo preLayoutInfo, @NonNull ItemHolderInfo postLayoutInfo);

        /**
         * Called by the RecyclerView when an adapter item is present both before and after the
         * layout and RecyclerView has received a {@link Adapter#notifyItemChanged(int)} call
         * for it. This method may also be called when
         * {@link Adapter#notifyDataSetChanged()} is called and adapter has stable ids so that
         * RecyclerView could still rebind views to the same ViewHolders. If viewType changes when
         * {@link Adapter#notifyDataSetChanged()} is called, this method <b>will not</b> be called,
         * instead, {@link #animateAppearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)} will be
         * called for the new ViewHolder and the old one will be recycled.
         * <p>
         * If this method is called due to a {@link Adapter#notifyDataSetChanged()} call, there is
         * a good possibility that item contents didn't really change but it is rebound from the
         * adapter. {@link DefaultItemAnimator} will skip animating the View if its location on the
         * screen didn't change and your animator should handle this case as well and avoid creating
         * unnecessary animations.
         * <p>
         * When an item is updated, ItemAnimator has a chance to ask RecyclerView to keep the
         * previous presentation of the item as-is and supply a new ViewHolder for the updated
         * presentation (see: {@link #canReuseUpdatedViewHolder(ViewHolder, List)}.
         * This is useful if you don't know the contents of the Item and would like
         * to cross-fade the old and the new one ({@link DefaultItemAnimator} uses this technique).
         * <p>
         * When you are writing a custom item animator for your layout, it might be more performant
         * and elegant to re-use the same ViewHolder and animate the content changes manually.
         * <p>
         * When {@link Adapter#notifyItemChanged(int)} is called, the Item's view type may change.
         * If the Item's view type has changed or ItemAnimator returned <code>false</code> for
         * this ViewHolder when {@link #canReuseUpdatedViewHolder(ViewHolder, List)} was called, the
         * <code>oldHolder</code> and <code>newHolder</code> will be different ViewHolder instances
         * which represent the same Item. In that case, only the new ViewHolder is visible
         * to the LayoutManager but RecyclerView keeps old ViewHolder attached for animations.
         * <p>
         * ItemAnimator must call {@link #dispatchAnimationFinished(ViewHolder)} for each distinct
         * ViewHolder when their animation is complete
         * (or instantly call {@link #dispatchAnimationFinished(ViewHolder)} if it decides not to
         * animate the view).
         * <p>
         *  If oldHolder and newHolder are the same instance, you should call
         * {@link #dispatchAnimationFinished(ViewHolder)} <b>only once</b>.
         * <p>
         * Note that when a ViewHolder both changes and disappears in the same layout pass, the
         * animation callback method which will be called by the RecyclerView depends on the
         * ItemAnimator's decision whether to re-use the same ViewHolder or not, and also the
         * LayoutManager's decision whether to layout the changed version of a disappearing
         * ViewHolder or not. RecyclerView will call
         * {@code animateChange} instead of
         * {@link #animateDisappearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)
         * animateDisappearance} if and only if the ItemAnimator returns {@code false} from
         * {@link #canReuseUpdatedViewHolder(ViewHolder) canReuseUpdatedViewHolder} and the
         * LayoutManager lays out a new disappearing view that holds the updated information.
         * Built-in LayoutManagers try to avoid laying out updated versions of disappearing views.
         *
         * @param oldHolder     The ViewHolder before the layout is started, might be the same
         *                      instance with newHolder.
         * @param newHolder     The ViewHolder after the layout is finished, might be the same
         *                      instance with oldHolder.
         * @param preLayoutInfo  The information that was returned from
         *                       {@link #recordPreLayoutInformation(State, ViewHolder, int, List)}.
         * @param postLayoutInfo The information that was returned from {@link
         *                       #recordPreLayoutInformation(State, ViewHolder, int, List)}.
         *
         * @return true if a later call to {@link #runPendingAnimations()} is requested,
         * false otherwise.
         */
        public abstract boolean animateChange(@NonNull ViewHolder oldHolder,
                @NonNull ViewHolder newHolder,
                @NonNull ItemHolderInfo preLayoutInfo, @NonNull ItemHolderInfo postLayoutInfo);

        @AdapterChanges static int buildAdapterChangeFlagsForAnimations(ViewHolder viewHolder) {
            int flags = viewHolder.mFlags & (FLAG_INVALIDATED | FLAG_REMOVED | FLAG_CHANGED);
            if (viewHolder.isInvalid()) {
                return FLAG_INVALIDATED;
            }
            if ((flags & FLAG_INVALIDATED) == 0) {
                final int oldPos = viewHolder.getOldPosition();
                final int pos = viewHolder.getAdapterPosition();
                if (oldPos != NO_POSITION && pos != NO_POSITION && oldPos != pos) {
                    flags |= FLAG_MOVED;
                }
            }
            return flags;
        }

        /**
         * Called when there are pending animations waiting to be started. This state
         * is governed by the return values from
         * {@link #animateAppearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)
         * animateAppearance()},
         * {@link #animateChange(ViewHolder, ViewHolder, ItemHolderInfo, ItemHolderInfo)
         * animateChange()}
         * {@link #animatePersistence(ViewHolder, ItemHolderInfo, ItemHolderInfo)
         * animatePersistence()}, and
         * {@link #animateDisappearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)
         * animateDisappearance()}, which inform the RecyclerView that the ItemAnimator wants to be
         * called later to start the associated animations. runPendingAnimations() will be scheduled
         * to be run on the next frame.
         */
        public abstract void runPendingAnimations();

        /**
         * Method called when an animation on a view should be ended immediately.
         * This could happen when other events, like scrolling, occur, so that
         * animating views can be quickly put into their proper end locations.
         * Implementations should ensure that any animations running on the item
         * are canceled and affected properties are set to their end values.
         * Also, {@link #dispatchAnimationFinished(ViewHolder)} should be called for each finished
         * animation since the animations are effectively done when this method is called.
         *
         * @param item The item for which an animation should be stopped.
         */
        public abstract void endAnimation(@NonNull ViewHolder item);

        /**
         * Method called when all item animations should be ended immediately.
         * This could happen when other events, like scrolling, occur, so that
         * animating views can be quickly put into their proper end locations.
         * Implementations should ensure that any animations running on any items
         * are canceled and affected properties are set to their end values.
         * Also, {@link #dispatchAnimationFinished(ViewHolder)} should be called for each finished
         * animation since the animations are effectively done when this method is called.
         */
        public abstract void endAnimations();

        /**
         * Method which returns whether there are any item animations currently running.
         * This method can be used to determine whether to delay other actions until
         * animations end.
         *
         * @return true if there are any item animations currently running, false otherwise.
         */
        public abstract boolean isRunning();

        /**
         * Method to be called by subclasses when an animation is finished.
         * <p>
         * For each call RecyclerView makes to
         * {@link #animateAppearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)
         * animateAppearance()},
         * {@link #animatePersistence(ViewHolder, ItemHolderInfo, ItemHolderInfo)
         * animatePersistence()}, or
         * {@link #animateDisappearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)
         * animateDisappearance()}, there
         * should
         * be a matching {@link #dispatchAnimationFinished(ViewHolder)} call by the subclass.
         * <p>
         * For {@link #animateChange(ViewHolder, ViewHolder, ItemHolderInfo, ItemHolderInfo)
         * animateChange()}, subclass should call this method for both the <code>oldHolder</code>
         * and <code>newHolder</code>  (if they are not the same instance).
         *
         * @param viewHolder The ViewHolder whose animation is finished.
         * @see #onAnimationFinished(ViewHolder)
         */
        public final void dispatchAnimationFinished(@NonNull ViewHolder viewHolder) {
            onAnimationFinished(viewHolder);
            if (mListener != null) {
                mListener.onAnimationFinished(viewHolder);
            }
        }

        /**
         * Called after {@link #dispatchAnimationFinished(ViewHolder)} is called by the
         * ItemAnimator.
         *
         * @param viewHolder The ViewHolder whose animation is finished. There might still be other
         *                   animations running on this ViewHolder.
         * @see #dispatchAnimationFinished(ViewHolder)
         */
        public void onAnimationFinished(@NonNull ViewHolder viewHolder) {
        }

        /**
         * Method to be called by subclasses when an animation is started.
         * <p>
         * For each call RecyclerView makes to
         * {@link #animateAppearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)
         * animateAppearance()},
         * {@link #animatePersistence(ViewHolder, ItemHolderInfo, ItemHolderInfo)
         * animatePersistence()}, or
         * {@link #animateDisappearance(ViewHolder, ItemHolderInfo, ItemHolderInfo)
         * animateDisappearance()}, there should be a matching
         * {@link #dispatchAnimationStarted(ViewHolder)} call by the subclass.
         * <p>
         * For {@link #animateChange(ViewHolder, ViewHolder, ItemHolderInfo, ItemHolderInfo)
         * animateChange()}, subclass should call this method for both the <code>oldHolder</code>
         * and <code>newHolder</code> (if they are not the same instance).
         * <p>
         * If your ItemAnimator decides not to animate a ViewHolder, it should call
         * {@link #dispatchAnimationFinished(ViewHolder)} <b>without</b> calling
         * {@link #dispatchAnimationStarted(ViewHolder)}.
         *
         * @param viewHolder The ViewHolder whose animation is starting.
         * @see #onAnimationStarted(ViewHolder)
         */
        public final void dispatchAnimationStarted(@NonNull ViewHolder viewHolder) {
            onAnimationStarted(viewHolder);
        }

        /**
         * Called when a new animation is started on the given ViewHolder.
         *
         * @param viewHolder The ViewHolder which started animating. Note that the ViewHolder
         *                   might already be animating and this might be another animation.
         * @see #dispatchAnimationStarted(ViewHolder)
         */
        public void onAnimationStarted(@NonNull ViewHolder viewHolder) {

        }

        /**
         * Like {@link #isRunning()}, this method returns whether there are any item
         * animations currently running. Additionally, the listener passed in will be called
         * when there are no item animations running, either immediately (before the method
         * returns) if no animations are currently running, or when the currently running
         * animations are {@link #dispatchAnimationsFinished() finished}.
         *
         * <p>Note that the listener is transient - it is either called immediately and not
         * stored at all, or stored only until it is called when running animations
         * are finished sometime later.</p>
         *
         * @param listener A listener to be called immediately if no animations are running
         * or later when currently-running animations have finished. A null listener is
         * equivalent to calling {@link #isRunning()}.
         * @return true if there are any item animations currently running, false otherwise.
         */
        public final boolean isRunning(@Nullable ItemAnimatorFinishedListener listener) {
            boolean running = isRunning();
            if (listener != null) {
                if (!running) {
                    listener.onAnimationsFinished();
                } else {
                    mFinishedListeners.add(listener);
                }
            }
            return running;
        }

        /**
         * When an item is changed, ItemAnimator can decide whether it wants to re-use
         * the same ViewHolder for animations or RecyclerView should create a copy of the
         * item and ItemAnimator will use both to run the animation (e.g. cross-fade).
         * <p>
         * Note that this method will only be called if the {@link ViewHolder} still has the same
         * type ({@link Adapter#getItemViewType(int)}). Otherwise, ItemAnimator will always receive
         * both {@link ViewHolder}s in the
         * {@link #animateChange(ViewHolder, ViewHolder, ItemHolderInfo, ItemHolderInfo)} method.
         * <p>
         * If your application is using change payloads, you can override
         * {@link #canReuseUpdatedViewHolder(ViewHolder, List)} to decide based on payloads.
         *
         * @param viewHolder The ViewHolder which represents the changed item's old content.
         *
         * @return True if RecyclerView should just rebind to the same ViewHolder or false if
         *         RecyclerView should create a new ViewHolder and pass this ViewHolder to the
         *         ItemAnimator to animate. Default implementation returns <code>true</code>.
         *
         * @see #canReuseUpdatedViewHolder(ViewHolder, List)
         */
        public boolean canReuseUpdatedViewHolder(@NonNull ViewHolder viewHolder) {
            return true;
        }

        /**
         * When an item is changed, ItemAnimator can decide whether it wants to re-use
         * the same ViewHolder for animations or RecyclerView should create a copy of the
         * item and ItemAnimator will use both to run the animation (e.g. cross-fade).
         * <p>
         * Note that this method will only be called if the {@link ViewHolder} still has the same
         * type ({@link Adapter#getItemViewType(int)}). Otherwise, ItemAnimator will always receive
         * both {@link ViewHolder}s in the
         * {@link #animateChange(ViewHolder, ViewHolder, ItemHolderInfo, ItemHolderInfo)} method.
         *
         * @param viewHolder The ViewHolder which represents the changed item's old content.
         * @param payloads A non-null list of merged payloads that were sent with change
         *                 notifications. Can be empty if the adapter is invalidated via
         *                 {@link RecyclerView.Adapter#notifyDataSetChanged()}. The same list of
         *                 payloads will be passed into
         *                 {@link RecyclerView.Adapter#onBindViewHolder(ViewHolder, int, List)}
         *                 method <b>if</b> this method returns <code>true</code>.
         *
         * @return True if RecyclerView should just rebind to the same ViewHolder or false if
         *         RecyclerView should create a new ViewHolder and pass this ViewHolder to the
         *         ItemAnimator to animate. Default implementation calls
         *         {@link #canReuseUpdatedViewHolder(ViewHolder)}.
         *
         * @see #canReuseUpdatedViewHolder(ViewHolder)
         */
        public boolean canReuseUpdatedViewHolder(@NonNull ViewHolder viewHolder,
                @NonNull List<Object> payloads) {
            return canReuseUpdatedViewHolder(viewHolder);
        }

        /**
         * This method should be called by ItemAnimator implementations to notify
         * any listeners that all pending and active item animations are finished.
         */
        public final void dispatchAnimationsFinished() {
            final int count = mFinishedListeners.size();
            for (int i = 0; i < count; ++i) {
                mFinishedListeners.get(i).onAnimationsFinished();
            }
            mFinishedListeners.clear();
        }

        /**
         * Returns a new {@link ItemHolderInfo} which will be used to store information about the
         * ViewHolder. This information will later be passed into <code>animate**</code> methods.
         * <p>
         * You can override this method if you want to extend {@link ItemHolderInfo} and provide
         * your own instances.
         *
         * @return A new {@link ItemHolderInfo}.
         */
        @NonNull
        public ItemHolderInfo obtainHolderInfo() {
            return new ItemHolderInfo();
        }

        /**
         * The interface to be implemented by listeners to animation events from this
         * ItemAnimator. This is used internally and is not intended for developers to
         * create directly.
         */
        interface ItemAnimatorListener {
            void onAnimationFinished(@NonNull ViewHolder item);
        }

        /**
         * This interface is used to inform listeners when all pending or running animations
         * in an ItemAnimator are finished. This can be used, for example, to delay an action
         * in a data set until currently-running animations are complete.
         *
         * @see #isRunning(ItemAnimatorFinishedListener)
         */
        public interface ItemAnimatorFinishedListener {
            /**
             * Notifies when all pending or running animations in an ItemAnimator are finished.
             */
            void onAnimationsFinished();
        }

        /**
         * A simple data structure that holds information about an item's bounds.
         * This information is used in calculating item animations. Default implementation of
         * {@link #recordPreLayoutInformation(RecyclerView.State, ViewHolder, int, List)} and
         * {@link #recordPostLayoutInformation(RecyclerView.State, ViewHolder)} returns this data
         * structure. You can extend this class if you would like to keep more information about
         * the Views.
         * <p>
         * If you want to provide your own implementation but still use `super` methods to record
         * basic information, you can override {@link #obtainHolderInfo()} to provide your own
         * instances.
         */
        public static class ItemHolderInfo {

            /**
             * The left edge of the View (excluding decorations)
             */
            public int left;

            /**
             * The top edge of the View (excluding decorations)
             */
            public int top;

            /**
             * The right edge of the View (excluding decorations)
             */
            public int right;

            /**
             * The bottom edge of the View (excluding decorations)
             */
            public int bottom;

            /**
             * The change flags that were passed to
             * {@link #recordPreLayoutInformation(RecyclerView.State, ViewHolder, int, List)}.
             */
            @AdapterChanges
            public int changeFlags;

            public ItemHolderInfo() {
            }

            /**
             * Sets the {@link #left}, {@link #top}, {@link #right} and {@link #bottom} values from
             * the given ViewHolder. Clears all {@link #changeFlags}.
             *
             * @param holder The ViewHolder whose bounds should be copied.
             * @return This {@link ItemHolderInfo}
             */
            @NonNull
            public ItemHolderInfo setFrom(@NonNull RecyclerView.ViewHolder holder) {
                return setFrom(holder, 0);
            }

            /**
             * Sets the {@link #left}, {@link #top}, {@link #right} and {@link #bottom} values from
             * the given ViewHolder and sets the {@link #changeFlags} to the given flags parameter.
             *
             * @param holder The ViewHolder whose bounds should be copied.
             * @param flags  The adapter change flags that were passed into
             *               {@link #recordPreLayoutInformation(RecyclerView.State, ViewHolder, int,
             *               List)}.
             * @return This {@link ItemHolderInfo}
             */
            @NonNull
            public ItemHolderInfo setFrom(@NonNull RecyclerView.ViewHolder holder,
                    @AdapterChanges int flags) {
                final View view = holder.itemView;
                this.left = view.getLeft();
                this.top = view.getTop();
                this.right = view.getRight();
                this.bottom = view.getBottom();
                return this;
            }
        }
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        if (mChildDrawingOrderCallback == null) {
            return super.getChildDrawingOrder(childCount, i);
        } else {
            return mChildDrawingOrderCallback.onGetChildDrawingOrder(childCount, i);
        }
    }

    /**
     * A callback interface that can be used to alter the drawing order of RecyclerView children.
     * <p>
     * It works using the {@link ViewGroup#getChildDrawingOrder(int, int)} method, so any case
     * that applies to that method also applies to this callback. For example, changing the drawing
     * order of two views will not have any effect if their elevation values are different since
     * elevation overrides the result of this callback.
     */
    public interface ChildDrawingOrderCallback {
        /**
         * Returns the index of the child to draw for this iteration. Override this
         * if you want to change the drawing order of children. By default, it
         * returns i.
         *
         * @param i The current iteration.
         * @return The index of the child to draw this iteration.
         *
         * @see RecyclerView#setChildDrawingOrderCallback(RecyclerView.ChildDrawingOrderCallback)
         */
        int onGetChildDrawingOrder(int childCount, int i);
    }

    private NestedScrollingChildHelper getScrollingChildHelper() {
        if (mScrollingChildHelper == null) {
            mScrollingChildHelper = new NestedScrollingChildHelper(this);
        }
        return mScrollingChildHelper;
    }
}
