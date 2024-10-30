package org.telegram.ui.Stories;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class SelfStoryViewsView extends FrameLayout {

    private final PagerAdapter pagerAdapter;
    public float toY;
    public float maxSelfStoriesViewsOffset;
    public float bottomPadding;
    ContainerView viewPagerContainer;
    float progressToOpen;
    SelfStoriesPreviewView selfStoriesPreviewView;
    float toHeight;
    StoryViewer storyViewer;
    private Drawable shadowDrawable;
    Theme.ResourcesProvider resourcesProvider;
    float selfStoriesViewsOffset;
    boolean listenPager;

    int keyboardHeight;
    int animatedKeyboardHeight;
    private long dialogId;

    ViewPagerInner viewPager;
    ArrayList<StoryItemInternal> storyItems = new ArrayList<>();
    ArrayList<SelfStoryViewsPage> itemViews = new ArrayList<>();
    private int currentState;
    SelfStoryViewsPage.FiltersState sharedFilterState = new SelfStoryViewsPage.FiltersState();
    float progressToKeyboard;

    public SelfStoryViewsView(@NonNull Context context, StoryViewer storyViewer) {
        super(context);
        this.resourcesProvider = storyViewer.resourcesProvider;
        this.storyViewer = storyViewer;
        selfStoriesPreviewView = new SelfStoriesPreviewView(getContext()) {

            @Override
            void onDragging() {
                listenPager = false;
            }

            @Override
            public void onClosestPositionChanged(int lastClosestPosition) {
                super.onClosestPositionChanged(lastClosestPosition);
                if (listenPager) {
                    return;
                }
                if (viewPager.getCurrentItem() != lastClosestPosition) {
                    try {
                        viewPager.setCurrentItem(lastClosestPosition, false);
                    } catch (Throwable e) {
                        FileLog.e(e);
                        viewPager.getAdapter().notifyDataSetChanged();
                        viewPager.setCurrentItem(lastClosestPosition, false);
                    }
                }
                if (storyViewer.storiesList != null && storyViewer.placeProvider != null) {
                    if (lastClosestPosition < 10) {
                        storyViewer.placeProvider.loadNext(false);
                    } else if (lastClosestPosition >= storyItems.size() - 10) {
                        storyViewer.placeProvider.loadNext(true);
                    }
                }
            }

            @Override
            public void onCenteredImageTap() {
                storyViewer.cancelSwipeToViews(false);
            }
        };
        //selfStoriesPreviewView.setAlpha(0.2f);

        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        viewPagerContainer = new ContainerView(context);

        viewPager = new ViewPagerInner(context) {

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (checkTopOffset(ev) && ev.getAction() == MotionEvent.ACTION_DOWN) {
                    return false;
                }
                return super.dispatchTouchEvent(ev);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (checkTopOffset(ev)) {
                    return false;
                }
                if (Math.abs(getCurrentTopOffset() - bottomPadding) > AndroidUtilities.dp(1)) {
                    return false;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                if (checkTopOffset(ev)) {
                    return false;
                }
                if (Math.abs(getCurrentTopOffset() - bottomPadding) > AndroidUtilities.dp(1)) {
                    return false;
                }
                return super.onTouchEvent(ev);
            }

            private boolean checkTopOffset(MotionEvent ev) {
                if (ev.getY() < getCurrentTopOffset()) {
                    return true;
                }
                return false;
            }
        };
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                if (listenPager) {
                    selfStoriesPreviewView.scrollToPositionWithOffset(position, positionOffset);
                }
            }

            @Override
            public void onPageSelected(int position) {
//                if (currentState == ViewPager.SCROLL_STATE_DRAGGING) {
//                    selfStoriesPreviewView.scrollToPosition(position, true, false);
//                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                currentState = state;
                if (currentState == ViewPager.SCROLL_STATE_DRAGGING) {
                    listenPager = true;
                }
//                if (currentState != ViewPager.SCROLL_STATE_DRAGGING) {
//                    selfStoriesPreviewView.scrollToPosition(viewPager.getCurrentItem(), true, true);
//                }
            }
        });
        viewPager.setAdapter(pagerAdapter = new PagerAdapter() {

            @Override
            public int getCount() {
                return storyItems.size();
            }

            @NonNull
            @Override
            public Object instantiateItem(@NonNull ViewGroup container, int position) {
                SelfStoryViewsPage item = new SelfStoryViewsPage(storyViewer, context, sharedFilterState, selfStoryViewsPage -> {
                    for (int i = 0; i < itemViews.size(); i++) {
                        if (selfStoryViewsPage != itemViews.get(i)) {
                            itemViews.get(i).updateSharedState();
                        }
                    }
                }) {
                    @Override
                    public void onTopOffsetChanged(int paddingTop) {
                        super.onTopOffsetChanged(paddingTop);
                        if ((Integer) getTag() == viewPager.getCurrentItem()) {
                            float progress = Utilities.clamp( (paddingTop / bottomPadding), 1f, 0);
                            selfStoriesPreviewView.setAlpha(progress);
                            selfStoriesPreviewView.setTranslationY(-(bottomPadding - paddingTop) / 2f);
                        }
                    }
                };
                item.setTag(position);
                item.setShadowDrawable(shadowDrawable);
                item.setPadding(0, AndroidUtilities.dp(16), 0 , 0);
                item.setStoryItem(dialogId,storyItems.get(position));
               // bottomPadding = (selfStoriesPreviewView.getTop() + toHeight + AndroidUtilities.dp(24));
                item.setListBottomPadding(bottomPadding);

                container.addView(item);

                itemViews.add(item);
                return item;
            }

            @Override
            public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
                container.removeView((View) object);
                itemViews.remove(object);
            }

            @Override
            public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
                return view == object;
            }
        });
        viewPagerContainer.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT,0, 0, 0, 0, 0));


//        buttonContainer = new FrameLayout(getContext());
//        buttonContainer.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
//        ButtonWithCounterView button = new ButtonWithCounterView(getContext(), resourcesProvider);
//
//        buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL_HORIZONTAL, 10, 10, 10, 10));
//        viewPagerContainer.addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
//        button.setText(LocaleController.getString(R.string.Close), false);
//        button.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                storyViewer.cancelSwipeToViews(false);
//            }
//        });

//        ImageView closeImageView = new ImageView(context);
//        closeImageView.setScaleType(ImageView.ScaleType.CENTER);
//        closeImageView.setImageResource(R.drawable.msg_close);
//        closeImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider), PorterDuff.Mode.MULTIPLY));
//        closeImageView.setOnClickListener(e -> storyViewer.cancelSwipeToViews(false));
//        viewPagerContainer.addView(closeImageView, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP, 0, 0, 2, 0));

        addView(selfStoriesPreviewView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        addView(viewPagerContainer);
        setVisibility(View.INVISIBLE);
    }

    private float getCurrentTopOffset() {
        float top = bottomPadding;
        SelfStoryViewsPage page = getCurrentPage();
        if (page != null) {
            top = page.getTopOffset();
        }
        return top;
    }

    public void setKeyboardHeight(int keyboardHeight) {
        boolean keyboardVisible = this.keyboardHeight >= AndroidUtilities.dp(20);
        boolean newKeyboardVisible = keyboardHeight >= AndroidUtilities.dp(20);
        if (newKeyboardVisible != keyboardVisible) {
            ValueAnimator keyboardAniamtor = ValueAnimator.ofFloat(progressToKeyboard, newKeyboardVisible ? 1f : 0);
            keyboardAniamtor.addUpdateListener(animation -> {
                progressToKeyboard = (float) animation.getAnimatedValue();
                updateTranslation();
            });
            keyboardAniamtor.setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator);
            keyboardAniamtor.setDuration(AdjustPanLayoutHelper.keyboardDuration);
            keyboardAniamtor.start();
        }
        this.keyboardHeight = keyboardHeight;
        if (keyboardHeight > 0) {
            SelfStoryViewsPage page = getCurrentPage();
            if (page != null) {
                page.onKeyboardShown();
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int topMargin = 0;//AndroidUtilities.dp(20);
        if (storyViewer.ATTACH_TO_FRAGMENT) {
            topMargin += AndroidUtilities.statusBarHeight;
        }
        int height = MeasureSpec.getSize(heightMeasureSpec);
        FrameLayout.LayoutParams layoutParams = (LayoutParams) selfStoriesPreviewView.getLayoutParams();
        layoutParams.topMargin = topMargin;
        toHeight = selfStoriesPreviewView.getFinalHeight();
        toY = topMargin + AndroidUtilities.dp(20);
        layoutParams = (LayoutParams) viewPagerContainer.getLayoutParams();
        layoutParams.topMargin = AndroidUtilities.statusBarHeight;
        bottomPadding = (topMargin + AndroidUtilities.dp(20) + toHeight + AndroidUtilities.dp(24)) -AndroidUtilities.statusBarHeight;
        maxSelfStoriesViewsOffset = height - bottomPadding;
        for (int i = 0; i < itemViews.size(); i++) {
            itemViews.get(i).setListBottomPadding(bottomPadding);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void setOffset(float selfStoriesViewsOffset) {
        if (this.selfStoriesViewsOffset == selfStoriesViewsOffset) {
            return;
        }
        this.selfStoriesViewsOffset = selfStoriesViewsOffset;
        updateTranslation();
        float oldProgressToOpen = progressToOpen;
        progressToOpen = Utilities.clamp(selfStoriesViewsOffset / maxSelfStoriesViewsOffset, 1f, 0);
        float alpha = Utilities.clamp(progressToOpen / 0.5f, 1f, 0);

        final PeerStoriesView currentView = storyViewer.getCurrentPeerView();
        if (oldProgressToOpen == 1f && progressToOpen != 1f) {
            if (storyViewer.storiesList != null) {
                int p = Utilities.clamp(selfStoriesPreviewView.getClosestPosition(), storyViewer.storiesList.messageObjects.size() - 1, 0);
                MessageObject object = storyViewer.storiesList.messageObjects.get(p);
                long date = StoriesController.StoriesList.day(object);
                if (storyViewer.transitionViewHolder.storyImage != null) {
                    storyViewer.transitionViewHolder.storyImage.setVisible(true, true);
                    storyViewer.transitionViewHolder.storyImage = null;
                }
                storyViewer.storiesViewPager.setCurrentDate(date, object.storyItem.id);
            } else {
                if (currentView != null) {
                    currentView.selectPosition(selfStoriesPreviewView.getClosestPosition());
                }
            }
            selfStoriesPreviewView.abortScroll();
        }
        if (currentView != null) {
            selfStoriesPreviewView.imagesFromY = currentView.storyContainer.getTop();
            selfStoriesPreviewView.imagesFromW = currentView.storyContainer.getMeasuredWidth();
            selfStoriesPreviewView.imagesFromH = currentView.storyContainer.getMeasuredHeight();
        }
        selfStoriesPreviewView.setProgressToOpen(progressToOpen);
        if (viewPager.gesturesEnabled && progressToOpen != 1f) {
            viewPager.onTouchEvent(AndroidUtilities.emptyMotionEvent());
        }
        setVisibility(progressToOpen == 0 ? View.INVISIBLE : View.VISIBLE);
        if (progressToOpen != 1f) {
            viewPager.gesturesEnabled = false;
        }
    }

    private void updateTranslation() {
        viewPagerContainer.setTranslationY(-bottomPadding + getMeasuredHeight() - selfStoriesViewsOffset);
    }

    public void setItems(long dialogId, ArrayList<TL_stories.StoryItem> storyItems, int selectedPosition) {
        this.storyItems.clear();
        this.dialogId = dialogId;
        for (int i = 0; i < storyItems.size(); i++) {
            this.storyItems.add(new StoryItemInternal(storyItems.get(i)));
        }
        long clientUserId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        ArrayList<StoriesController.UploadingStory> uploadingStories = MessagesController.getInstance(storyViewer.currentAccount).storiesController.getUploadingStories(clientUserId);
        if (uploadingStories != null) {
            for (int i = 0; i < uploadingStories.size(); i++) {
                this.storyItems.add(new StoryItemInternal(uploadingStories.get(i)));
            }
        }
        selfStoriesPreviewView.setItems(this.storyItems, selectedPosition);
        viewPager.setAdapter(null);
        viewPager.setAdapter(pagerAdapter);
        pagerAdapter.notifyDataSetChanged();
        viewPager.setCurrentItem(selectedPosition);
    }

    public SelfStoriesPreviewView.ImageHolder getCrossfadeToImage() {
        return selfStoriesPreviewView.getCenteredImageReciever();
    }

    public boolean onBackPressed() {
        if (keyboardHeight > 0) {
            AndroidUtilities.hideKeyboard(this);
            return true;
        }
        SelfStoryViewsPage page = getCurrentPage();
        if (page != null) {
            return page.onBackPressed();
        }
        return false;
    }

    public TL_stories.StoryItem getSelectedStory() {
        int p = selfStoriesPreviewView.getClosestPosition();
        if (p < 0 || p >= storyItems.size()) {
            return null;
        }
        return storyItems.get(p).storyItem;
    }

    private class ContainerView extends FrameLayout implements NestedScrollingParent3 {

        private final NestedScrollingParentHelper nestedScrollingParentHelper;

        public ContainerView(@NonNull Context context) {
            super(context);
            nestedScrollingParentHelper = new NestedScrollingParentHelper(this);
        }

        @Override
        public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes, int type) {
            if (keyboardHeight > 0) {
                return false;
            }
            if (axes == ViewCompat.SCROLL_AXIS_VERTICAL) {
                return true;
            }
            return false;
        }

        @Override
        public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes, int type) {
            nestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes);
        }

        @Override
        public void onStopNestedScroll(@NonNull View target, int type) {
            nestedScrollingParentHelper.onStopNestedScroll(target);
        }

        @Override
        public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type) {
           // onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type, null);
        }

        @Override
        public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type, @NonNull int[] consumed) {
            if (keyboardHeight > 0) {
                return;
            }
            if (dyUnconsumed != 0 && dyConsumed == 0) {
                float currentTranslation = storyViewer.selfStoriesViewsOffset;
                currentTranslation += dyUnconsumed;
                if (currentTranslation > storyViewer.selfStoriesViewsOffset) {
                    currentTranslation = storyViewer.selfStoriesViewsOffset;
                }
                setOffset(currentTranslation);
                storyViewer.setSelfStoriesViewsOffset(currentTranslation);
            }
        }


        @Override
        public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) {
            //AndroidUtilities.hideKeyboard(this);
            if (keyboardHeight > 0) {
                return;
            }
            float currentTranslation = storyViewer.selfStoriesViewsOffset;
            if (currentTranslation < maxSelfStoriesViewsOffset && dy > 0) {
                currentTranslation += dy;
                consumed[1] = dy;
                if (currentTranslation > maxSelfStoriesViewsOffset) {
                    currentTranslation = maxSelfStoriesViewsOffset;
                }
                setOffset(currentTranslation);
                storyViewer.setSelfStoriesViewsOffset(currentTranslation);
            }
        }
    }

    private class ViewPagerInner extends ViewPager {

        boolean gesturesEnabled;

        public ViewPagerInner(@NonNull Context context) {
            super(context);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                gesturesEnabled = true;
            }
            if (!gesturesEnabled || keyboardHeight > 0) {
                return false;
            }
            try {
                return super.onInterceptTouchEvent(ev);
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN) {
                gesturesEnabled = true;
            }
            if (!gesturesEnabled || keyboardHeight > 0) {
                return false;
            }
            return super.onTouchEvent(ev);
        }
    }

    public class StoryItemInternal {
        public TL_stories.StoryItem storyItem;
        public StoriesController.UploadingStory uploadingStory;

        public StoryItemInternal(TL_stories.StoryItem storyItem) {
            this.storyItem = storyItem;
        }

        public StoryItemInternal(StoriesController.UploadingStory uploadingStory) {
            this.uploadingStory = uploadingStory;
        }
    }

    public SelfStoryViewsPage getCurrentPage() {
        for (int i = 0; i < itemViews.size(); i++) {
            if ((Integer)itemViews.get(i).getTag() == viewPager.getCurrentItem()) {
                return itemViews.get(i);
            }
        }
        return null;
    }


}
