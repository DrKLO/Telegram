package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LaunchActivity;

public abstract class BottomSheetWithRecyclerListView extends BottomSheet {

    public enum ActionBarType {
        FADING,
        SLIDING
    }

    private final Drawable headerShadowDrawable;
    protected LinearLayoutManager layoutManager;
    protected RecyclerListView recyclerListView;
    protected ActionBar actionBar;
    boolean wasDrawn;
    protected int contentHeight;
    private BaseFragment baseFragment;
    public final boolean hasFixedSize;
    public final boolean stackFromEnd;
    protected boolean clipToActionBar;
    public NestedSizeNotifierLayout nestedSizeNotifierLayout;

    public float topPadding = 0.4f;
    boolean showShadow = true;
    private float shadowAlpha = 1f;

    private boolean showHandle = false;
    protected boolean handleOffset;
    private RectF handleRect = new RectF();

    private ActionBarType actionBarType = ActionBarType.FADING;
    protected int headerTotalHeight = 0;
    protected int headerHeight = 0;
    protected int headerPaddingTop = 0;
    protected int headerPaddingBottom = 0;
    protected boolean actionBarIgnoreTouchEvents = false;
    protected AnimatedFloat actionBarSlideProgress;

    /*
    Take padding-view translationY into account when positioning background.
    If set to true requires that changes to its translation invalidate containerView.
     */
    protected boolean takeTranslationIntoAccount = false;

    public BottomSheetWithRecyclerListView(BaseFragment fragment, boolean needFocus, boolean hasFixedSize) {
        this(fragment, needFocus, hasFixedSize, false, fragment == null ? null : fragment.getResourceProvider());
    }

    public BottomSheetWithRecyclerListView(BaseFragment fragment, boolean needFocus, boolean hasFixedSize, boolean useNested, Theme.ResourcesProvider resourcesProvider) {
        this(fragment.getParentActivity(), fragment, needFocus, hasFixedSize, useNested, resourcesProvider);
    }

    public BottomSheetWithRecyclerListView(BaseFragment fragment, boolean needFocus, boolean hasFixedSize, ActionBarType actionBarType) {
        this(fragment.getParentActivity(), fragment, needFocus, hasFixedSize, false, actionBarType, fragment.getResourceProvider());
    }

    public BottomSheetWithRecyclerListView(Context context, BaseFragment fragment, boolean needFocus, boolean hasFixedSize, boolean useNested, Theme.ResourcesProvider resourcesProvider) {
        this(context, fragment, needFocus, hasFixedSize, useNested, ActionBarType.FADING, resourcesProvider);
    }

    @SuppressLint("AppCompatCustomView")
    public BottomSheetWithRecyclerListView(Context context, BaseFragment fragment, boolean needFocus, boolean hasFixedSize, boolean useNested, ActionBarType actionBarType, Theme.ResourcesProvider resourcesProvider) {
        this(context, fragment, needFocus, hasFixedSize, useNested, false, actionBarType, resourcesProvider);
    }

    EditTextEmoji editTextEmoji;
    public void setEditTextEmoji(EditTextEmoji editTextEmoji) {
        this.editTextEmoji = editTextEmoji;
    }

    @SuppressLint("AppCompatCustomView")
    public BottomSheetWithRecyclerListView(Context context, BaseFragment fragment, boolean needFocus, boolean hasFixedSize, boolean useNested, boolean stackFromEnd, ActionBarType actionBarType, Theme.ResourcesProvider resourcesProvider) {
        super(context, needFocus, resourcesProvider);
        this.baseFragment = fragment;
        this.hasFixedSize = hasFixedSize;
        this.stackFromEnd = stackFromEnd;
        headerShadowDrawable = ContextCompat.getDrawable(context, R.drawable.header_shadow).mutate();
        SizeNotifierFrameLayout containerView;
        if (useNested) {
            containerView = nestedSizeNotifierLayout = new NestedSizeNotifierLayout(context) {

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    contentHeight = MeasureSpec.getSize(heightMeasureSpec);
                    onPreMeasure(widthMeasureSpec, heightMeasureSpec);
                    if (stackFromEnd) {
                        heightMeasureSpec = MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.EXACTLY);
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }

                @Override
                protected void dispatchDraw(Canvas canvas) {
                    preDrawInternal(canvas, this);
                    super.dispatchDraw(canvas);
                    postDrawInternal(canvas, this);
                }

                @Override
                protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                    if (!hasFixedSize && clipToActionBar && child == recyclerListView) {
                        canvas.save();
                        canvas.clipRect(0, actionBar.getMeasuredHeight(), getMeasuredWidth(), getMeasuredHeight());
                        super.drawChild(canvas, child, drawingTime);
                        canvas.restore();
                        return true;
                    }
                    return super.drawChild(canvas, child, drawingTime);
                }

                @Override
                public boolean dispatchTouchEvent(MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN && event.getY() < shadowDrawable.getBounds().top) {
                        dismiss();
                    }
                    return super.dispatchTouchEvent(event);
                }
            };
        } else {
             containerView = new SizeNotifierFrameLayout(context) {
                 private boolean ignoreLayout = false;

                 @Override
                 protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                     contentHeight = MeasureSpec.getSize(heightMeasureSpec);
                     onPreMeasure(widthMeasureSpec, heightMeasureSpec);
                     if (stackFromEnd) {
                         heightMeasureSpec = MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.EXACTLY);
                     }
                     if (editTextEmoji != null) {
                         onMeasureInternal(widthMeasureSpec, heightMeasureSpec);
                     } else {
                         super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                     }
                 }

                 private void onMeasureInternal(int widthMeasureSpec, int heightMeasureSpec) {
                     int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                     int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                     setMeasuredDimension(widthSize, heightSize);
//                     widthSize -= backgroundPaddingLeft * 2;

                     int keyboardSize = 0;
                     if (editTextEmoji != null && !editTextEmoji.isWaitingForKeyboardOpen() && keyboardSize <= AndroidUtilities.dp(20) && !editTextEmoji.isPopupShowing() && !editTextEmoji.isAnimatePopupClosing()) {
                         ignoreLayout = true;
                         editTextEmoji.hideEmojiView();
                         ignoreLayout = false;
                     }

                     if (keyboardSize <= AndroidUtilities.dp(20)) {
                         int paddingBottom = 0;
                         if (!keyboardVisible) {
                             if (editTextEmoji != null) {
                                 paddingBottom = editTextEmoji.getEmojiPadding();
                             }
                         }
                         if (!AndroidUtilities.isInMultiwindow) {
                             heightSize -= paddingBottom;
                             heightMeasureSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY);
                         }
                     }

                     int childCount = getChildCount();
                     for (int i = 0; i < childCount; i++) {
                         View child = getChildAt(i);
                         if (child == null || child.getVisibility() == GONE) {
                             continue;
                         }
                         if (editTextEmoji != null && editTextEmoji.isPopupView(child)) {
//                             if (inBubbleMode) {
//                                 child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize + getPaddingTop(), MeasureSpec.EXACTLY));
//                             } else
                             if (AndroidUtilities.isInMultiwindow || AndroidUtilities.isTablet()) {
                                 if (AndroidUtilities.isTablet()) {
                                     child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(AndroidUtilities.isTablet() ? 200 : 320), heightSize - AndroidUtilities.statusBarHeight + getPaddingTop()), MeasureSpec.EXACTLY));
                                 } else {
                                     child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize - AndroidUtilities.statusBarHeight + getPaddingTop(), MeasureSpec.EXACTLY));
                                 }
                             } else {
                                 child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(child.getLayoutParams().height, MeasureSpec.EXACTLY));
                             }
                         } else {
                             measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                         }
                     }
                 }

                 @Override
                 protected void dispatchDraw(Canvas canvas) {
                     preDrawInternal(canvas, this);
                     super.dispatchDraw(canvas);
                     postDrawInternal(canvas, this);
                 }

                 @Override
                 protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                     if (!hasFixedSize && clipToActionBar && child == recyclerListView) {
                         canvas.save();
                         canvas.clipRect(0, actionBar.getMeasuredHeight(), getMeasuredWidth(), getMeasuredHeight());
                         super.drawChild(canvas, child, drawingTime);
                         canvas.restore();
                         return true;
                     }
                     return super.drawChild(canvas, child, drawingTime);
                 }

                 @Override
                 public boolean dispatchTouchEvent(MotionEvent event) {
                     if (event.getAction() == MotionEvent.ACTION_DOWN && event.getY() < shadowDrawable.getBounds().top) {
                          dismiss();
                     }
                     return super.dispatchTouchEvent(event);
                 }

                 @Override
                 protected void onLayout(boolean changed, int l, int t, int r, int b) {
                     if (editTextEmoji == null) {
                         super.onLayout(changed, l, t, r, b);
                         return;
                     }
                     final int count = getChildCount();
                     int keyboardSize = measureKeyboardHeight();
                     int paddingBottom = getPaddingBottom();
                     if (!keyboardVisible && editTextEmoji != null) {
                         if (keyboardSize <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet()) {
                             paddingBottom += editTextEmoji.getEmojiPadding();
                         }
                     }
                     setBottomClip(paddingBottom);

                     for (int i = 0; i < count; i++) {
                         final View child = getChildAt(i);
                         if (child.getVisibility() == GONE) {
                             continue;
                         }
                         final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                         final int width = child.getMeasuredWidth();
                         final int height = child.getMeasuredHeight();

                         int childLeft;
                         int childTop;

                         int gravity = lp.gravity;
                         if (gravity == -1) {
                             gravity = Gravity.TOP | Gravity.LEFT;
                         }

                         final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                         final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                         switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                             case Gravity.CENTER_HORIZONTAL:
                                 childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                                 break;
                             case Gravity.RIGHT:
                                 childLeft = (r - l) - width - lp.rightMargin - getPaddingRight() - backgroundPaddingLeft;
                                 break;
                             case Gravity.LEFT:
                             default:
                                 childLeft = lp.leftMargin + getPaddingLeft();
                         }

                         switch (verticalGravity) {
                             case Gravity.TOP:
                                 childTop = lp.topMargin + getPaddingTop();
                                 break;
                             case Gravity.CENTER_VERTICAL:
                                 childTop = ((b - paddingBottom) - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                                 break;
                             case Gravity.BOTTOM:
                                 childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                                 break;
                             default:
                                 childTop = lp.topMargin;
                         }

                         if (child instanceof EmojiView) {
                             if (AndroidUtilities.isTablet()) {
                                 childTop = getMeasuredHeight() - child.getMeasuredHeight();
                             } else {
                                 childTop = getMeasuredHeight() + keyboardSize - child.getMeasuredHeight();
                             }
                         }
                         child.layout(childLeft, childTop, childLeft + width, childTop + height);
                     }

                     notifyHeightChanged();
                 }
            };
        }
        recyclerListView = new RecyclerListView(context, resourcesProvider) {
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                applyScrolledPosition();
                super.onLayout(changed, l, t, r, b);
            }

            @Override
            protected boolean canHighlightChildAt(View child, float x, float y) {
                return BottomSheetWithRecyclerListView.this.canHighlightChildAt(child, x, y);
            }
        };
        layoutManager = new LinearLayoutManager(context);
        if (stackFromEnd) {
            layoutManager.setStackFromEnd(true);
        }
        recyclerListView.setLayoutManager(layoutManager);
        if (nestedSizeNotifierLayout != null) {
            nestedSizeNotifierLayout.setBottomSheetContainerView(getContainer());
            nestedSizeNotifierLayout.setTargetListView(recyclerListView);
        }

        if (hasFixedSize) {
            recyclerListView.setHasFixedSize(true);
            recyclerListView.setAdapter(createAdapter(recyclerListView));
            setCustomView(containerView);
            containerView.addView(recyclerListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else {
            resetAdapter(context);
            this.containerView = containerView;
            actionBar = new ActionBar(context) {
                @Override
                public void setAlpha(float alpha) {
                    if (getAlpha() != alpha) {
                        super.setAlpha(alpha);
                        containerView.invalidate();
                    }
                }

                @Override
                public void setTag(Object tag) {
                    super.setTag(tag);
                    updateStatusBar();
                }

                @Override
                public boolean dispatchTouchEvent(MotionEvent ev) {
                    if (actionBarIgnoreTouchEvents) {
                        return false;
                    }
                    return super.dispatchTouchEvent(ev);
                }
            };
            actionBar.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
            actionBar.setTitleColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarActionModeDefaultSelector), false);
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setItemsColor(getThemedColor(Theme.key_actionBarActionModeDefaultIcon), false);

            actionBar.setCastShadows(true);
            actionBar.setTitle(getTitle());
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        dismiss();
                    }
                }
            });

            containerView.addView(recyclerListView);
            containerView.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 6, 0));

            recyclerListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    containerView.invalidate();
                }
            });
        }
        if (actionBarType == ActionBarType.SLIDING) {
            setSlidingActionBar();
        }
        onViewCreated(containerView);
        updateStatusBar();
    }

    public void setSlidingActionBar() {
        if (hasFixedSize) {
            return;
        }
        actionBarType = ActionBarType.SLIDING;
        headerHeight = ActionBar.getCurrentActionBarHeight();
        headerTotalHeight = headerHeight + AndroidUtilities.statusBarHeight;
        headerPaddingTop = dp(16);
        headerPaddingBottom = dp(-20);
        actionBarSlideProgress = new AnimatedFloat(containerView, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        actionBar.backButtonImageView.setPivotX(0.0f);
        recyclerListView.setClipToPadding(true);
    }

    public boolean reverseLayout;

    protected void resetAdapter(Context context) {
        RecyclerListView.SelectionAdapter adapter = createAdapter(recyclerListView);
        RecyclerListView.SelectionAdapter wrapperAdapter = new RecyclerListView.SelectionAdapter() {

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return adapter.isEnabled(holder);
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                if (viewType == -1000) {
                    View view = new View(context) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            int h;
                            if (contentHeight == 0) {
                                h = dp(300);
                            } else {
                                h = (int) (contentHeight * topPadding);
                            }
                            h -= headerTotalHeight - headerHeight - headerPaddingTop - headerPaddingBottom;
                            if (h < 1) {
                                h = 1;
                            }
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
                        }

                        @Override
                        public void setTranslationY(float translationY) {
                            super.setTranslationY(translationY);
                            containerView.invalidate();
                        }
                    };
                    return new RecyclerListView.Holder(view);
                }
                return adapter.onCreateViewHolder(parent, viewType);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                if (position != (reverseLayout ? getItemCount() - 1 : 0)) {
                    adapter.onBindViewHolder(holder, position - (reverseLayout ? 0 : 1));
                }
            }

            @Override
            public int getItemViewType(int position) {
                if (position == (reverseLayout ? getItemCount() - 1 : 0)) {
                    return -1000;
                }
                return adapter.getItemViewType(position - (reverseLayout ? 0 : 1));
            }

            @Override
            public int getItemCount() {
                return 1 + adapter.getItemCount();
            }

            @Override
            public void registerAdapterDataObserver(@NonNull RecyclerView.AdapterDataObserver observer) {
                adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                    @Override
                    public void onChanged() {
                        observer.onChanged();
                    }

                    @Override
                    public void onItemRangeChanged(int positionStart, int itemCount) {
                        observer.onItemRangeChanged(positionStart + (reverseLayout ? 0 : 1), itemCount);
                    }

                    @Override
                    public void onItemRangeChanged(int positionStart, int itemCount, @Nullable Object payload) {
                        observer.onItemRangeChanged(positionStart + (reverseLayout ? 0 : 1), itemCount, payload);
                    }

                    @Override
                    public void onItemRangeInserted(int positionStart, int itemCount) {
                        observer.onItemRangeInserted(positionStart + (reverseLayout ? 0 : 1), itemCount);
                    }

                    @Override
                    public void onItemRangeRemoved(int positionStart, int itemCount) {
                        observer.onItemRangeRemoved(positionStart + (reverseLayout ? 0 : 1), itemCount);
                    }

                    @Override
                    public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                        observer.onItemRangeMoved(fromPosition + (reverseLayout ? 0 : 1), toPosition + (reverseLayout ? 0 : 1), itemCount);
                    }
                });
            }
        };
        recyclerListView.setAdapter(wrapperAdapter);
    }

    private void postDrawInternal(Canvas canvas, View parentView) {
        if (actionBarType == ActionBarType.FADING) {
            if (showShadow && shadowAlpha != 1f) {
                shadowAlpha += 16 / 150f;
                parentView.invalidate();
            } else if (!showShadow && shadowAlpha != 0) {
                shadowAlpha -= 16 / 150f;
                parentView.invalidate();
            }
            shadowAlpha = Utilities.clamp(shadowAlpha, 1f, 0f);
            if (actionBar != null && actionBar.getVisibility() == View.VISIBLE && actionBar.getAlpha() != 0 && shadowAlpha != 0) {
                headerShadowDrawable.setBounds(backgroundPaddingLeft, actionBar.getBottom(), parentView.getMeasuredWidth() - backgroundPaddingLeft, actionBar.getBottom() + headerShadowDrawable.getIntrinsicHeight());
                headerShadowDrawable.setAlpha((int) (255 * actionBar.getAlpha() * shadowAlpha));
                headerShadowDrawable.draw(canvas);
                if (headerShadowDrawable.getAlpha() < 255) {
                    parentView.invalidate();
                }
            }
            wasDrawn = true;
        } else if (actionBarType == ActionBarType.SLIDING) {
            if ((int) (255 * shadowAlpha) != 0) {
                headerShadowDrawable.setBounds(backgroundPaddingLeft, actionBar.getBottom() + (int) actionBar.getTranslationY(), parentView.getMeasuredWidth() - backgroundPaddingLeft, actionBar.getBottom() + (int) actionBar.getTranslationY() + headerShadowDrawable.getIntrinsicHeight());
                headerShadowDrawable.setAlpha((int) (255 * shadowAlpha));
                headerShadowDrawable.draw(canvas);
            }
        }
        if (restore) {
            canvas.restore();
            restore = false;
        }
    }

    private boolean restore;
    private void preDrawInternal(Canvas canvas, View parent) {
        restore = false;
        if (!hasFixedSize) {
            int top;
            if (reverseLayout) {
                top = recyclerListView.getHeight();
                for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                    View child = recyclerListView.getChildAt(i);
                    int pos = recyclerListView.getChildAdapterPosition(child);
                    if (pos != RecyclerView.NO_POSITION && pos != recyclerListView.getAdapter().getItemCount() - 1) {
                        top = Math.min(top, child.getTop() + (takeTranslationIntoAccount ? (int) child.getTranslationY() : 0));
                    }
                }
                top -= AndroidUtilities.dp(16);
            } else {
                RecyclerView.ViewHolder holder = recyclerListView.findViewHolderForAdapterPosition(0);
                top = -AndroidUtilities.dp(16);
                if (holder != null) {
                    top = holder.itemView.getBottom() - AndroidUtilities.dp(16);
                    if (takeTranslationIntoAccount) {
                        top += (int) holder.itemView.getTranslationY();
                    }
                }
            }
            top -= headerHeight + headerPaddingTop + headerPaddingBottom;
            if (showHandle && handleOffset) {
                top -= dp(actionBarType == ActionBarType.SLIDING ? 8 : 16);
            }

            float handleAlpha = 1.0f;
            float progressToFullView = 0.0f;
            if (actionBarType == ActionBarType.FADING) {
                progressToFullView = 1f - (top + AndroidUtilities.dp(16)) / (float) AndroidUtilities.dp(56);
                if (progressToFullView < 0) {
                    progressToFullView = 0;
                }
                AndroidUtilities.updateViewVisibilityAnimated(actionBar, progressToFullView != 0f, 1f, wasDrawn);
            } else if (actionBarType == ActionBarType.SLIDING) {
                float actionBarY = Math.max(top + dp(8) + headerPaddingTop - AndroidUtilities.statusBarHeight, 0.0f);
                float t = actionBarSlideProgress.set(actionBarY == 0.0f ? 1.0f : 0.0f);

                if (t != 0 && t != 1) {
                    canvas.save();
                    canvas.clipRect(0, actionBarY, containerView.getMeasuredWidth(), containerView.getMeasuredHeight());
                    restore = true;
                }
                progressToFullView = t;
                shadowAlpha = t;
                handleAlpha = AndroidUtilities.lerp(1.0f, 0.5f, t);
                actionBar.backButtonImageView.setAlpha(t);
                actionBar.backButtonImageView.setScaleX(t);
                actionBar.backButtonImageView.setPivotY(actionBar.backButtonImageView.getMeasuredHeight() / 2f);
                actionBar.backButtonImageView.setScaleY(t);
                SimpleTextView titleTextView = actionBar.getTitleTextView();
                titleTextView.setTranslationX(AndroidUtilities.lerp(dp(21) - titleTextView.getLeft(), 0.0f, t));
                actionBar.setTranslationY(actionBarY);
                top -= AndroidUtilities.lerp(0, headerTotalHeight - headerHeight - headerPaddingTop - headerPaddingBottom + dp(13), t);
                actionBar.getBackground().setBounds(0, AndroidUtilities.lerp(actionBar.getHeight(), 0, t), actionBar.getWidth(), actionBar.getHeight());

                if (t > 0.5f) {
                    if (actionBarIgnoreTouchEvents) {
                        actionBarIgnoreTouchEvents = false;
                        actionBar.setTag(1);
                    }
                } else {
                    if (!actionBarIgnoreTouchEvents) {
                        actionBarIgnoreTouchEvents = true;
                        actionBar.setTag(null);
                    }
                }
            }

            if (needPaddingShadow()) {
                shadowDrawable.setBounds(0, top, parent.getMeasuredWidth(), parent.getMeasuredHeight());
            } else {
                shadowDrawable.setBounds(-AndroidUtilities.dp(6), top, parent.getMeasuredWidth() + AndroidUtilities.dp(6), parent.getMeasuredHeight());
            }
            shadowDrawable.draw(canvas);

            if (showHandle && handleAlpha > 0) {
                int w = dp(36);
                int y = top + AndroidUtilities.dp(20);
                handleRect.set((parent.getMeasuredWidth() - w) / 2.0f, y, (parent.getMeasuredWidth() + w) / 2.0f, y + dp(4));
                int color = getThemedColor(Theme.key_sheet_scrollUp);
                Theme.dialogs_onlineCirclePaint.setColor(color);
                Theme.dialogs_onlineCirclePaint.setAlpha((int) (Theme.dialogs_onlineCirclePaint.getAlpha() * handleAlpha));
                canvas.drawRoundRect(handleRect, dp(2), dp(2), Theme.dialogs_onlineCirclePaint);
            }

            onPreDraw(canvas, top, progressToFullView);
        }
    }

    protected boolean needPaddingShadow() {
        return true;
    }

    protected void onPreMeasure(int widthMeasureSpec, int heightMeasureSpec) {

    }

    protected void onPreDraw(Canvas canvas, int top, float progressToFullView) {

    }

    @Override
    public boolean isAttachedLightStatusBar() {
        if (actionBar != null && actionBar.getTag() != null) {
            return isLightStatusBar();
        } else if (baseFragment != null) {
            return baseFragment.isLightStatusBar();
        }
        return isLightStatusBar();
    }

    private boolean isLightStatusBar() {
        return ColorUtils.calculateLuminance(Theme.getColor(Theme.key_dialogBackground, resourcesProvider)) > 0.7f;
    }

    public void onViewCreated(FrameLayout containerView) {

    }

    protected boolean canHighlightChildAt(View child, float x, float y) {
        return true;
    }

    protected abstract CharSequence getTitle();

    protected abstract RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView);

    public void notifyDataSetChanged() {
        recyclerListView.getAdapter().notifyDataSetChanged();
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    public BaseFragment getBaseFragment() {
        return baseFragment;
    }

    private void updateStatusBar() {
        if (attachedFragment != null) {
            LaunchActivity.instance.checkSystemBarColors(true, true, true, false);
        } else if (actionBar != null && actionBar.getTag() != null) {
            AndroidUtilities.setLightStatusBar(getWindow(), isLightStatusBar());
        } else if (baseFragment != null) {
            AndroidUtilities.setLightStatusBar(getWindow(), baseFragment.isLightStatusBar());
        }
    }

    public void updateTitle() {
        if (actionBar != null) {
            actionBar.setTitle(getTitle());
        }
    }

    public void updateTitleAnimated() {
        if (actionBar != null) {
            actionBar.setTitleAnimated(getTitle(), false, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        }
    }

    public void setShowShadow(boolean show) {
        showShadow = show;
        nestedSizeNotifierLayout.invalidate();
    }

    public void setShowHandle(boolean showHandle) {
        this.showHandle = showHandle;
    }


    private int savedScrollPosition = -1;
    private int savedScrollOffset;
    public void saveScrollPosition() {
        if (recyclerListView != null && layoutManager != null && recyclerListView.getChildCount() > 0) {
            View view = null;
            int position = -1;
            int top = Integer.MAX_VALUE;
            for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                View child = recyclerListView.getChildAt(i);
                int childPosition = recyclerListView.getChildAdapterPosition(child);
                if (childPosition > 0 && child.getTop() < top) {
                    view = child;
                    position = childPosition;
                    top = child.getTop();
                }
            }
            if (view != null) {
                savedScrollPosition = position;
                savedScrollOffset = view.getTop() + containerView.getTop();
                smoothContainerViewLayout();
            }
        }
    }

    public void applyScrolledPosition() {
        this.applyScrolledPosition(false);
    }

    public void applyScrolledPosition(boolean ignorePaddingView) {
        if (recyclerListView != null && recyclerListView.getLayoutManager() != null && savedScrollPosition >= 0) {
            int offset = savedScrollOffset - containerView.getTop() - recyclerListView.getPaddingTop();
            RecyclerView.ViewHolder paddingViewHolder = recyclerListView.findViewHolderForAdapterPosition(0);
            if (ignorePaddingView && paddingViewHolder != null) {
                View view = paddingViewHolder.itemView;
                offset -= Math.max(view.getBottom() - recyclerListView.getPaddingTop(), 0);
            }
            if (recyclerListView.getLayoutManager() instanceof LinearLayoutManager) {
                ((LinearLayoutManager) recyclerListView.getLayoutManager()).scrollToPositionWithOffset(savedScrollPosition, offset);
            }
            savedScrollPosition = -1;
        }
    }
}
