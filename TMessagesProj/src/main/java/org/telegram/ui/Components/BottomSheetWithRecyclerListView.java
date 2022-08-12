package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

public abstract class BottomSheetWithRecyclerListView extends BottomSheet {

    protected RecyclerListView recyclerListView;
    protected ActionBar actionBar;
    boolean wasDrawn;
    private int contentHeight;
    private BaseFragment baseFragment;
    public final boolean hasFixedSize;
    protected boolean clipToActionBar;

    public float topPadding = 0.4f;

    public BottomSheetWithRecyclerListView(BaseFragment fragment, boolean needFocus, boolean hasFixedSize) {
        super(fragment.getParentActivity(), needFocus);
        this.baseFragment = fragment;
        this.hasFixedSize = hasFixedSize;
        Context context = fragment.getParentActivity();
        Drawable headerShadowDrawable = ContextCompat.getDrawable(context, R.drawable.header_shadow).mutate();
        FrameLayout containerView = new FrameLayout(context) {

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                contentHeight = MeasureSpec.getSize(heightMeasureSpec);
                onPreMeasure(widthMeasureSpec, heightMeasureSpec);
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (!hasFixedSize) {
                    RecyclerView.ViewHolder holder = recyclerListView.findViewHolderForAdapterPosition(0);
                    int top = -AndroidUtilities.dp(16);
                    if (holder != null) {
                        top = holder.itemView.getBottom() - AndroidUtilities.dp(16);
                    }

                    float progressToFullView = 1f - (top + AndroidUtilities.dp(16)) / (float) AndroidUtilities.dp(56);
                    if (progressToFullView < 0) {
                        progressToFullView = 0;
                    }

                    AndroidUtilities.updateViewVisibilityAnimated(actionBar, progressToFullView != 0f, 1f, wasDrawn);
                    shadowDrawable.setBounds(0, top, getMeasuredWidth(), getMeasuredHeight());
                    shadowDrawable.draw(canvas);

                    onPreDraw(canvas, top, progressToFullView);
                }
                super.dispatchDraw(canvas);
                if (actionBar != null && actionBar.getVisibility() == View.VISIBLE && actionBar.getAlpha() != 0) {
                    headerShadowDrawable.setBounds(0, actionBar.getBottom(), getMeasuredWidth(), actionBar.getBottom() +  headerShadowDrawable.getIntrinsicHeight());
                    headerShadowDrawable.setAlpha((int) (255 * actionBar.getAlpha()));
                    headerShadowDrawable.draw(canvas);
                }
                wasDrawn = true;
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
        recyclerListView = new RecyclerListView(context);
        recyclerListView.setLayoutManager(new LinearLayoutManager(context));

        RecyclerListView.SelectionAdapter adapter = createAdapter();

        if (hasFixedSize) {
            recyclerListView.setHasFixedSize(true);
            recyclerListView.setAdapter(adapter);
            setCustomView(containerView);
            containerView.addView(recyclerListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else {
            recyclerListView.setAdapter(new RecyclerListView.SelectionAdapter() {

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
                                    h = AndroidUtilities.dp(300);
                                } else {
                                    h = (int) (contentHeight * topPadding);
                                }
                                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
                            }
                        };
                        return new RecyclerListView.Holder(view);
                    }
                    return adapter.onCreateViewHolder(parent, viewType);
                }

                @Override
                public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                    if (position != 0) {
                        adapter.onBindViewHolder(holder, position - 1);
                    }
                }

                @Override
                public int getItemViewType(int position) {
                    if (position == 0) {
                        return -1000;
                    }
                    return adapter.getItemViewType(position - 1);
                }

                @Override
                public int getItemCount() {
                    return 1 + adapter.getItemCount();
                }
            });
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
            };
            actionBar.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
            actionBar.setTitleColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarActionModeDefaultSelector), false);
            actionBar.setItemsColor(getThemedColor(Theme.key_actionBarActionModeDefaultIcon), false);

            actionBar.setCastShadows(true);
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setTitle(getTitle());
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick(){
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
        onViewCreated(containerView);
        updateStatusBar();
    }

    protected void onPreMeasure(int widthMeasureSpec, int heightMeasureSpec) {

    }

    protected void onPreDraw(Canvas canvas, int top, float progressToFullView) {

    }

    private boolean isLightStatusBar() {
        return ColorUtils.calculateLuminance(Theme.getColor(Theme.key_dialogBackground)) > 0.7f;
    }

    public void onViewCreated(FrameLayout containerView) {

    }

    protected abstract CharSequence getTitle();

    protected abstract RecyclerListView.SelectionAdapter createAdapter();

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
        if (actionBar != null && actionBar.getTag() != null) {
            AndroidUtilities.setLightStatusBar(getWindow(), isLightStatusBar());
        } else if (baseFragment != null) {
            AndroidUtilities.setLightStatusBar(getWindow(), baseFragment.isLightStatusBar());
        }
    }

}
