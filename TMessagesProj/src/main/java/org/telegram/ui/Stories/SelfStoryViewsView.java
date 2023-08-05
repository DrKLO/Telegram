package org.telegram.ui.Stories;

import android.content.Context;
import android.graphics.Canvas;
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
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
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

    ViewPagerInner viewPager;
    ArrayList<StoryItemInternal> storyItems = new ArrayList<>();
    ArrayList<SelfStoryViewsPage> itemViews = new ArrayList<>();
    private int currentState;

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

        viewPager = new ViewPagerInner(context);
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
                SelfStoryViewsPage item = new SelfStoryViewsPage(storyViewer, context) {
                    @Override
                    protected void dispatchDraw(Canvas canvas) {
                        shadowDrawable.setBounds(-AndroidUtilities.dp(6), 0, getMeasuredWidth() + AndroidUtilities.dp(6), getMeasuredHeight());
                        shadowDrawable.draw(canvas);
                        super.dispatchDraw(canvas);
                    }
                };
                item.setPadding(0, AndroidUtilities.dp(16), 0 , 0);
                item.setStoryItem(storyItems.get(position));
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
//        button.setText(LocaleController.getString("Close", R.string.Close), false);
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
        bottomPadding = (topMargin + AndroidUtilities.dp(20) + toHeight + AndroidUtilities.dp(24));
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
        viewPagerContainer.setTranslationY(getMeasuredHeight() - selfStoriesViewsOffset);
        float oldProgressToOpen = progressToOpen;
        progressToOpen = Utilities.clamp(selfStoriesViewsOffset / maxSelfStoriesViewsOffset, 1f, 0);
        float alpha = Utilities.clamp(progressToOpen / 0.5f, 1f, 0);
      //  selfStoriesPreviewView.setAlpha(alpha);

        final PeerStoriesView currentView = storyViewer.getCurrentPeerView();
        if (oldProgressToOpen == 1f && progressToOpen != 1f) {
            if (currentView != null) {
                currentView.selectPosition(selfStoriesPreviewView.getClosestPosition());
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

    public void setItems(ArrayList<TLRPC.StoryItem> storyItems, int selectedPosition) {
        this.storyItems.clear();
        for (int i = 0; i < storyItems.size(); i++) {
            this.storyItems.add(new StoryItemInternal(storyItems.get(i)));
        }
        ArrayList<StoriesController.UploadingStory> uploadingStories = MessagesController.getInstance(storyViewer.currentAccount).storiesController.getUploadingStories();
        for (int i = 0; i < uploadingStories.size(); i++) {
            this.storyItems.add(new StoryItemInternal(uploadingStories.get(i)));
        }
        selfStoriesPreviewView.setItems(this.storyItems, selectedPosition);
        viewPager.setAdapter(null);
        viewPager.setAdapter(pagerAdapter);
        pagerAdapter.notifyDataSetChanged();
        viewPager.setCurrentItem(selectedPosition);
    }

    public ImageReceiver getCrossfadeToImage() {
        return selfStoriesPreviewView.getCenteredImageReciever();
    }

    private class ContainerView extends FrameLayout implements NestedScrollingParent3{

        private final NestedScrollingParentHelper nestedScrollingParentHelper;

        public ContainerView(@NonNull Context context) {
            super(context);
            nestedScrollingParentHelper = new NestedScrollingParentHelper(this);
        }

        @Override
        public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes, int type) {
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
            if (!gesturesEnabled) {
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
            if (!gesturesEnabled) {
                return false;
            }
            return super.onTouchEvent(ev);
        }
    }

    public class StoryItemInternal {
        public TLRPC.StoryItem storyItem;
        public StoriesController.UploadingStory uploadingStory;

        public StoryItemInternal(TLRPC.StoryItem storyItem) {
            this.storyItem = storyItem;
        }

        public StoryItemInternal(StoriesController.UploadingStory uploadingStory) {
            this.uploadingStory = uploadingStory;
        }
    }


}
