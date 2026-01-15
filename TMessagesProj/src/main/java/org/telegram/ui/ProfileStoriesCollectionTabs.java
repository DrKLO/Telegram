package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BlurredFrameLayout;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.Stories.StoriesController;

import java.util.ArrayList;

@SuppressLint("ViewConstructor")
public class ProfileStoriesCollectionTabs extends BlurredFrameLayout implements NotificationCenter.NotificationCenterDelegate {
    private final ViewPagerFixed viewPager;
    private final ViewPagerFixed.TabsView tabsView;
    private final StoriesController.StoriesCollections collections;
    private final Adapter adapter;

    public interface Delegate {
        void onTabAlbumSelected(int albumId, boolean forward);
        void onTabAlbumScrollEnd(int albumId);
        void onTabAlbumAnimationUpdate(float progress);

        void onTabAlbumCreateCollection();
        void onTabAlbumLongClick(View view, int albumId);
    }

    public ProfileStoriesCollectionTabs(Context context, SizeNotifierFrameLayout sizeNotifierFrameLayout, StoriesController.StoriesCollections collections, Delegate delegate) {
        super(context, sizeNotifierFrameLayout);

        this.collections = collections;
        this.sendCollectionsOrder = collections::sendOrder;

        viewPager = new ViewPagerFixed(context) {
            @Override
            public void onTabAnimationUpdate(boolean manual) {
                super.onTabAnimationUpdate(manual);
                if (delegate != null) {
                    delegate.onTabAlbumAnimationUpdate(tabsView.getAnimatingIndicatorProgress());
                }
            }

            @Override
            protected void onTabScrollEnd(int position) {
                super.onTabScrollEnd(position);
                if (delegate != null) {
                    delegate.onTabAlbumScrollEnd(getAlbumIdByPosition(position));
                }
            }

            @Override
            protected void onTabPageSelected(int position, boolean forward) {
                if (delegate != null) {
                    delegate.onTabAlbumSelected(getAlbumIdByPosition(position), forward);
                }
            }
        };
        viewPager.setAllowDisallowInterceptTouch(true);

        adapter = new Adapter();
        adapter.canCreateNewAlbum = collections.canCreateNewAlbum();

        viewPager.setAdapter(adapter);
        viewPager.setTranslationY(dp(42));
        tabsView = viewPager.createTabsView(true, 9);
        tabsView.tabMarginDp = 12;
        tabsView.setPreTabClick((id, pos) -> {
            if (reorderingCollections) {
                return true;
            }

            if (id == -1) {
                if (delegate != null) {
                    delegate.onTabAlbumCreateCollection();
                }
                return true;
            }
            return false;
        });
        tabsView.setOnTabLongClick((albumId, view) -> {
            if (albumId == -1 || albumId == 0 || reorderingCollections)
                return false;

            if (delegate != null) {
                delegate.onTabAlbumLongClick(view, albumId);
            }

            return true;
        });
        addView(tabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 42, Gravity.TOP));

        setVisibility(!collections.collections.isEmpty(), false, true);
    }

    protected void updatedReordering(boolean reordering) {

    }


    int initialAlbumId;
    public void setInitialTabId(int albumId) {
        final int position = adapter.getItemPosition(albumId);
        if (position != -1) {
            AndroidUtilities.runOnUIThread(() -> {
                scrollToAlbumId(albumId);
            }, 500);

            // tabsView.selectTab(position, position, 0.0f);
        } else {
            initialAlbumId = albumId;
        }
    }

    public int getCurrentAlbumId() {
        return adapter.getItemId(tabsView.getCurrentPosition());
    }

    public int getNextAlbumId(boolean forward) {
        return tabsView.getNextPageId(forward);
    }

    private int getAlbumIdByPosition(int position) {
        return tabsView.getPageIdByPosition(position);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(collections.currentAccount)
            .addObserver(this, NotificationCenter.storyAlbumsCollectionsUpdate);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(collections.currentAccount)
            .addObserver(this, NotificationCenter.storyAlbumsCollectionsUpdate);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.storyAlbumsCollectionsUpdate) {
            if ((Long) args[0] != collections.dialogId) return;

            final int tabId = tabsView != null ? tabsView.getCurrentTabId() : 0;

            adapter.canCreateNewAlbum = collections.canCreateNewAlbum();
            viewPager.fillTabs(true);
            setVisibility(!collections.collections.isEmpty(), true, false);

            if (initialAlbumId > 0) {
                final int position = adapter.getItemPosition(initialAlbumId);
                if (position != -1) {
                    final int finalAlbumId = initialAlbumId;
                    AndroidUtilities.runOnUIThread(() -> {
                        scrollToAlbumId(finalAlbumId);
                    }, 500);

                    //tabsView.selectTab(position, position, 0.0f);
                    initialAlbumId = 0;
                }
            } else {
                if (tabsView != null && tabId > 0 && collections.findById(tabId) == null) {
                    tabsView.scrollToTab(0, 0);
                }
            }
        }
    }


    public void selectTabWithId(int albumId, float progress) {
        tabsView.selectTabWithId(albumId, progress);
    }

    public void scrollToAlbumId(int albumId) {
        tabsView.scrollToTab(albumId, adapter.getItemPosition(albumId));
    }



    private boolean reorderingCollections;

    public boolean isReordering() {
        return this.reorderingCollections;
    }

    public void resetReordering() {
        setReorderingAlbums(false);
    }

    public void setReorderingAlbums(boolean reordering) {
        if (this.reorderingCollections == reordering) return;
        this.reorderingCollections = reordering;
        tabsView.setReordering(reordering);
        updatedReordering(isReordering());

        if (reordering) {
            final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
            if (lastFragment instanceof ProfileActivity) {
                ((ProfileActivity) lastFragment).scrollToSharedMedia(false);
                AndroidUtilities.runOnUIThread(() -> {
                    ((ProfileActivity) lastFragment).scrollToSharedMedia(true);
                });
            }
        }
        if (!reordering) {
            AndroidUtilities.cancelRunOnUIThread(sendCollectionsOrder);
            // AndroidUtilities.runOnUIThread(sendCollectionsOrder);
            collections.reorderComplete(false);
            final int albumId = adapter.getItemId(tabsView.getCurrentPosition());

            viewPager.fillTabs(true);

            final int position = adapter.getItemPosition(albumId);
            tabsView.selectTab(position, position, 0.0f);
        }
    }

    private ValueAnimator visibilityAnimator;
    private float visibilityFactor;
    private boolean visibilityValue;

    private void setVisibility(boolean visibility, boolean animated, boolean force) {
        if (visibilityValue == visibility && !force) {
            return;
        }

        visibilityValue = visibility;
        setEnabled(visibility);

        if (visibilityAnimator != null) {
            visibilityAnimator.cancel();
            visibilityAnimator = null;
        }

        if (!animated) {
            visibilityFactor = visibility ? 1: 0;
            onVisibilityChange(visibilityFactor);
            return;
        }

        visibilityAnimator = ValueAnimator.ofFloat(visibilityFactor, visibility ? 1: 0);
        visibilityAnimator.setDuration(480L);
        visibilityAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        visibilityAnimator.addUpdateListener(a -> {
            visibilityFactor = (float) a.getAnimatedValue();
            onVisibilityChange(visibilityFactor);

        });
        visibilityAnimator.start();
    }

    protected void onVisibilityChange(float factor) {
        invalidate();
    }

    public float getVisibilityFactor() {
        return visibilityFactor;
    }

    public float getVisualHeight() {
        return getMeasuredHeight() * visibilityFactor;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        return visibilityValue && super.dispatchTouchEvent(ev);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (visibilityFactor == 0f) {
            return;
        }

        canvas.save();
        canvas.clipRect(0, 0, getMeasuredWidth(), getVisualHeight());
        super.draw(canvas);
        canvas.restore();
    }

    private final Runnable sendCollectionsOrder;

    private class Adapter extends ViewPagerFixed.Adapter {
        private boolean canCreateNewAlbum;

        @Override
        public int getItemCount() {
            return 1 + collections.collections.size() + (canCreateNewAlbum ? 1 : 0);
        }

        @Override
        public View createView(int viewType) {
            if (viewType == -1) {
                return null;
            }
            return new View(getContext());
        }

        @Override
        public boolean canReorder(int position) {
            if (position == 0)
                return false;
            if (canCreateNewAlbum && position == getItemCount() - 1)
                return false;
            return true;
        }

        @Override
        public void applyReorder(ArrayList<Integer> itemIds) {
            final ArrayList<Integer> collectionIds = new ArrayList<>();
            for (final int itemId : itemIds) {
                if (itemId == -1 || itemId == -2 || itemId == 0) continue;
                collectionIds.add(itemId);
            }

            final int albumId = getItemId(tabsView.getCurrentPosition());

            collections.reorderStep(collectionIds);
            if (albumId >= 0) {
                final int position = getItemPosition(albumId);
                tabsView.selectTab(position, position, 0.0f);
            }

            AndroidUtilities.cancelRunOnUIThread(sendCollectionsOrder);
            AndroidUtilities.runOnUIThread(sendCollectionsOrder, 1000);
        }

        public int getItemPosition(int albumId) {
            if (albumId == 0) {
                return 0;
            }
            int index = collections.indexOf(albumId);
            if (index == -1) {
                return -1;
            }
            return 1 + index;
        }

        @Override
        public int getItemId(int position) {
            if (position == 0) return 0;
            if (canCreateNewAlbum && position == getItemCount() - 1) return -1;
            return collections.collections.get(position - 1).album_id;
        }

        @Override
        public void bindView(View view, int position, int viewType) {

        }

        @Override
        public int getItemViewType(int position) {
            if (canCreateNewAlbum && position == getItemCount() - 1)
                return -1;
            return position;
        }

        @Override
        public CharSequence getItemTitle(int position) {
            if (position == 0) {
                return getString(R.string.StoriesAlbumNameAllStories);
            } else if (canCreateNewAlbum && position == getItemCount() - 1) {
                SpannableStringBuilder sb = new SpannableStringBuilder("+ ");
                sb.append(getString(R.string.StoriesAlbumAddAlbum));
                ColoredImageSpan span = new ColoredImageSpan(R.drawable.poll_add_plus);
                span.spaceScaleX = .8f;
                sb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                return sb;
            }

            StoriesController.StoryAlbum album = collections.collections.get(position - 1);
            return album.title;
        }
    }
}
