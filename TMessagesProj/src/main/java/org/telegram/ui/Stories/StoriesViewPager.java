package org.telegram.ui.Stories;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.TreeSet;

public class StoriesViewPager extends ViewPager {

    long daysDialogId;
    ArrayList<ArrayList<Integer>> days;

    int currentAccount;
    PagerAdapter pagerAdapter;
    ArrayList<Long> dialogs = new ArrayList<>();
    PeerStoriesView.Delegate delegate;

    boolean updateDelegate;
    boolean touchEnabled = true;
    int keyboardHeight;
    public int currentState;
    public boolean dissallowInterceptCalled;

    Runnable doOnNextIdle;
    PeerStoriesView.SharedResources resources;

    int selectedPosition;
    int toPosition;
    float progress;
    private boolean touchLocked;
    Runnable lockTouchRunnable = new Runnable() {
        @Override
        public void run() {
            touchLocked = false;
        }
    };

    StoryViewer storyViewer;
    private int selectedPositionInPage;
    private int updateVisibleItemPosition = -1;

    public StoriesViewPager(int account, @NonNull Context context, StoryViewer storyViewer, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.currentAccount = account;
        resources = new PeerStoriesView.SharedResources(context);
        this.storyViewer = storyViewer;
        setAdapter(pagerAdapter = new PagerAdapter() {
            @Override
            public int getCount() {
                if (days != null) {
                    return days.size();
                }
                return dialogs.size();
            }

            private final ArrayList<PeerStoriesView> cachedViews = new ArrayList<>();

            @NonNull
            @Override
            public Object instantiateItem(@NonNull ViewGroup container, int position) {
                PageLayout pageLayout = new PageLayout(context);
                PeerStoriesView view;
                if (!cachedViews.isEmpty()) {
                    view = cachedViews.remove(0);
                    view.reset();
                } else {
                    view = new HwPeerStoriesView(context, storyViewer, resources, resourcesProvider) {
                        @Override
                        public boolean isSelectedPeer() {
                            if (getParent() == null) {
                                return false;
                            }
                            return ((Integer) ((View) getParent()).getTag()) == getCurrentItem();
                        }
                    };
                }
                pageLayout.peerStoryView = view;
                view.setAccount(currentAccount);
                view.setDelegate(delegate);
                view.setLongpressed(storyViewer.isLongpressed);
                pageLayout.setTag(position);
                if (days != null) {
                    pageLayout.day = days.get(storyViewer.reversed ? days.size() - 1 - position : position);
                    if (storyViewer.storiesList instanceof StoriesController.SearchStoriesList) {
                        MessageObject msg = storyViewer.storiesList.findMessageObject(pageLayout.day.get(0));
                        pageLayout.dialogId = msg == null ? daysDialogId : msg.getDialogId();
                    } else {
                        pageLayout.dialogId = daysDialogId;
                    }
                } else {
                    pageLayout.day = null;
                    pageLayout.dialogId = dialogs.get(position);
                }
                pageLayout.addView(view);
                view.requestLayout();
                container.addView(pageLayout);
                return pageLayout;
            }

            @Override
            public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
                FrameLayout child = (FrameLayout) object;
                container.removeView(child);
                PeerStoriesView peerStoriesView = (PeerStoriesView) child.getChildAt(0);
                AndroidUtilities.removeFromParent(peerStoriesView);
                cachedViews.add(peerStoriesView);
            }

            @Override
            public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
                return view == object;
            }
        });
        setPageTransformer(false, (page, position) -> {
            PageLayout pageLayout = (PageLayout) page;
            if (Math.abs(position) >= 1f) {
                pageLayout.setVisible(false);
                AndroidUtilities.runOnUIThread(() -> {
                    if (pageLayout.day != null) {
                        pageLayout.peerStoryView.day = pageLayout.day;
                    }
                    pageLayout.peerStoryView.preloadMainImage(pageLayout.dialogId);
                }, 16);
                return;
            }
            if (!pageLayout.isVisible) {
                pageLayout.setVisible(true);
                if (days != null) {
                    pageLayout.peerStoryView.setDay(pageLayout.dialogId, pageLayout.day, -1);
                } else {
                    pageLayout.peerStoryView.setDialogId(pageLayout.dialogId, -1);
                }
            }
            pageLayout.peerStoryView.setOffset(position);
            page.setCameraDistance(page.getWidth() * 15);
            page.setPivotX(position < 0f ? page.getWidth() : 0f);
            page.setPivotY(page.getHeight() * 0.5f);
            page.setRotationY(90f * position);
        });
        setOffscreenPageLimit(0);

        addOnPageChangeListener(new OnPageChangeListener() {

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                selectedPosition = position;
                toPosition = positionOffsetPixels > 0 ? selectedPosition + 1 : selectedPosition - 1;
                progress = positionOffset;

                final long me = UserConfig.getInstance(currentAccount).clientUserId;

                if (selectedPosition >= 0 && (days == null ? selectedPosition < dialogs.size() && dialogs.get(selectedPosition) == me : daysDialogId == me)) {
                    delegate.setHideEnterViewProgress(1f - progress);
                } else if (toPosition >= 0 && (days == null ? toPosition < dialogs.size() && dialogs.get(toPosition) == me : daysDialogId == me)) {
                    delegate.setHideEnterViewProgress(progress);
                } else {
                    delegate.setHideEnterViewProgress(0);
                }
            }

            @Override
            public void onPageSelected(int position) {
                final PeerStoriesView peerStoriesView = getCurrentPeerView();
                if (peerStoriesView == null) {
                    return;
                }
                delegate.onPeerSelected(peerStoriesView.getCurrentPeer(), peerStoriesView.getSelectedPosition());
                updateActiveStory();
                if (storyViewer.placeProvider != null) {
                    if (position < 3) {
                        storyViewer.placeProvider.loadNext(false);
                    } else if (position > pagerAdapter.getCount() - 4) {
                        storyViewer.placeProvider.loadNext(true);
                    }
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                delegate.setAllowTouchesByViewPager(state != SCROLL_STATE_IDLE);
                if (doOnNextIdle != null && state == SCROLL_STATE_IDLE) {
                    doOnNextIdle.run();
                    doOnNextIdle = null;
                }
                currentState = state;
                onStateChanged();
            }
        });
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    private void updateActiveStory() {
        for (int i = 0; i < getChildCount(); i++) {
            PeerStoriesView peerStoriesView = ((PeerStoriesView) ((FrameLayout) getChildAt(i)).getChildAt(0));
            peerStoriesView.setActive((Integer) getChildAt(i).getTag() == getCurrentItem() && !peerStoriesView.editOpened);
        }
    }

    public void checkAllowScreenshots() {
        boolean allowScreenshots = true;
        for (int i = 0; i < getChildCount(); i++) {
            PageLayout layout = (PageLayout) getChildAt(i);
            if (layout.isVisible && !layout.peerStoryView.currentStory.allowScreenshots()) {
                allowScreenshots = false;
                break;
            }
        }
        storyViewer.allowScreenshots(allowScreenshots);
    }

    public void onStateChanged() {

    }

    public boolean canScroll(float dx) {
        if (selectedPosition == 0 && progress == 0 && dx < 0) {
            return false;
        }
        if (selectedPosition == getAdapter().getCount() - 1 && progress == 0 && dx > 0) {
            return false;
        }
        return true;
    }

    @Nullable
    public PeerStoriesView getCurrentPeerView() {
        for (int i = 0; i < getChildCount(); i++) {
            if ((Integer) getChildAt(i).getTag() == getCurrentItem()) {
                return (PeerStoriesView) ((FrameLayout) getChildAt(i)).getChildAt(0);
            }
        }
        return null;
    }

    public void setPeerIds(ArrayList<Long> peerIds, int currentAccount, int position) {
        this.dialogs = peerIds;
        this.currentAccount = currentAccount;
        setAdapter(null);
        setAdapter(pagerAdapter);
        setCurrentItem(position);
        updateDelegate = true;
    }

    public void setDays(long dialogId, ArrayList<ArrayList<Integer>> days, int currentAccount) {
        if (this.daysDialogId == dialogId && eqA(this.days, days) && this.currentAccount == currentAccount) {
            return;
        }
        this.daysDialogId = dialogId;
        this.days = days;
        this.currentAccount = currentAccount;
        setAdapter(null);
        setAdapter(pagerAdapter);
        int position;
        for (position = 0; position < days.size(); ++position) {
            if (days.get(position).contains(storyViewer.dayStoryId)) {
                break;
            }
        }
        if (storyViewer.reversed) {
            position = days.size() - 1 - position;
        }
        setCurrentItem(position);
        updateDelegate = true;
    }

    private static boolean eqA(ArrayList<ArrayList<Integer>> a, ArrayList<ArrayList<Integer>> b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); ++i) {
            if (!eq(a.get(i), b.get(i)))
                return false;
        }
        return true;
    }
    public static boolean eq(ArrayList<Integer> a, ArrayList<Integer> b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); ++i) {
            if (a.get(i) != b.get(i))
                return false;
        }
        return true;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (updateDelegate) {
            updateDelegate = false;
            final PeerStoriesView peerStoriesView = getCurrentPeerView();
            if (peerStoriesView != null) {
                delegate.onPeerSelected(peerStoriesView.getCurrentPeer(), peerStoriesView.getSelectedPosition());
            }
        }
        checkPageVisibility();
        updateActiveStory();
    }

    public void checkPageVisibility() {
        if (updateVisibleItemPosition >= 0) {
            for (int i = 0; i < getChildCount(); i++) {
                if ((Integer) getChildAt(i).getTag() == getCurrentItem() && getCurrentItem() == updateVisibleItemPosition) {
                    PageLayout pageLayout = ((PageLayout) getChildAt(i));
                    if (!pageLayout.isVisible) {
                        updateVisibleItemPosition = -1;
                        pageLayout.setVisible(true);
                        if (days != null) {
                            pageLayout.peerStoryView.setDay(pageLayout.dialogId, pageLayout.day, selectedPositionInPage);
                        } else {
                            pageLayout.peerStoryView.setDialogId(pageLayout.dialogId, selectedPositionInPage);
                        }
                    }
                }
            }
        }
    }

    public void setDelegate(PeerStoriesView.Delegate delegate) {
        this.delegate = delegate;
    }

    public boolean useSurfaceInViewPagerWorkAround() {
        return storyViewer.USE_SURFACE_VIEW && Build.VERSION.SDK_INT < 33;
    }

    public boolean switchToNext(boolean forward) {
        if (forward && getCurrentItem() < (days != null ? days : dialogs).size() - 1) {
            setCurrentItem(getCurrentItem() + 1, !useSurfaceInViewPagerWorkAround());
            return true;
        }
        if (!forward && getCurrentItem() > 0) {
            setCurrentItem(getCurrentItem() - 1, !useSurfaceInViewPagerWorkAround());
            return true;
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!touchEnabled || touchLocked) {
            return false;
        }
        try {
            return super.onInterceptTouchEvent(ev);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!touchEnabled || touchLocked) {
            if (touchLocked && (ev.getAction() == MotionEvent.ACTION_DOWN || ev.getAction() == MotionEvent.ACTION_MOVE)) {
                return true;
            }
            return false;
        }
        return super.onTouchEvent(ev);
    }

    public void enableTouch(boolean enable) {
        touchEnabled = enable;
    }

    public void setPaused(boolean paused) {
        for (int i = 0; i < getChildCount(); i++) {
            ((PeerStoriesView) ((FrameLayout) getChildAt(i)).getChildAt(0)).setPaused(paused);
        }
    }

    public long getCurrentDialogId() {
        if (days != null) {
            return daysDialogId;
        }
        if (getCurrentItem() < dialogs.size()) {
            return dialogs.get(getCurrentItem());
        }
        return 0L;
    }

    public void onNextIdle(Runnable runnable) {
        this.doOnNextIdle = runnable;
    }

    public void setKeyboardHeight(int realKeyboardHeight) {
        if (keyboardHeight != realKeyboardHeight) {
            keyboardHeight = realKeyboardHeight;
            final View view = getCurrentPeerView();
            if (view != null) {
                view.requestLayout();
            }
        }
    }

    float lastProgressToDismiss;

    public void setHorizontalProgressToDismiss(float position) {
        if (Math.abs(position) > 1 || lastProgressToDismiss == position) {
            return;
        }
        lastProgressToDismiss = position;
        setCameraDistance(getWidth() * 15);
        setPivotX(position < 0f ? getWidth() : 0f);
        setPivotY(getHeight() * 0.5f);
        setRotationY(90f * position);
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        if (disallowIntercept) {
            dissallowInterceptCalled = true;
        }
        super.requestDisallowInterceptTouchEvent(disallowIntercept);
    }

    public void lockTouchEvent(long duration) {
        touchLocked = true;
        onTouchEvent(MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0, 0, 0));
        AndroidUtilities.cancelRunOnUIThread(lockTouchRunnable);
        AndroidUtilities.runOnUIThread(lockTouchRunnable, duration);
    }

    public ArrayList<Long> getDialogIds() {
        return dialogs;
    }

    private class PageLayout extends FrameLayout {

        public PeerStoriesView peerStoryView;

        long dialogId;
        ArrayList<Integer> day;

        boolean isVisible;

        public PageLayout(@NonNull Context context) {
            super(context);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (!isVisible) {
                return;
            }
            super.dispatchDraw(canvas);
        }

        public void setVisible(boolean visible) {
            if (isVisible != visible) {
                isVisible = visible;
                invalidate();
                peerStoryView.setIsVisible(visible);

                checkAllowScreenshots();
            }
        }
    }

    public void setCurrentDate(long day, int storyId) {
        for (int i = 0; i < days.size(); i++) {
            long currentDay = StoriesController.StoriesList.day(storyViewer.storiesList.findMessageObject(days.get(i).get(0)));
            if (day == currentDay) {
                int position = i;
                int positionInPage = 0;
                if (storyViewer.reversed) {
                    position = days.size() - 1 - position;
                }
                for (int j = 0; j < days.get(i).size(); j++) {
                    if (days.get(i).get(j) == storyId) {
                        positionInPage = j;
                        break;
                    }
                }
                if (getCurrentPeerView() == null || getCurrentItem() != position) {
                    setCurrentItem(position, false);
                    PeerStoriesView peerView = getCurrentPeerView();
                    if (peerView != null) {
                        PageLayout pageLayout = (PageLayout) peerView.getParent();
                        pageLayout.setVisible(true);
                        if (days != null) {
                            pageLayout.peerStoryView.setDay(pageLayout.dialogId, pageLayout.day, positionInPage);
                        } else {
                            pageLayout.peerStoryView.setDialogId(pageLayout.dialogId, positionInPage);
                        }
                    }
//                    updateVisibleItemPosition = position;
//                    selectedPositionInPage = positionInPage;
                } else {
                    getCurrentPeerView().selectPosition(positionInPage);
                }
                break;
            }
        }
    }
}
