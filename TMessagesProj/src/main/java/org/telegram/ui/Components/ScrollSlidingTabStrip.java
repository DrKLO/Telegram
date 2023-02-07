/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.transition.AutoTransition;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.transition.TransitionValues;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.HashMap;

public class ScrollSlidingTabStrip extends HorizontalScrollView {

    public interface ScrollSlidingTabStripDelegate {
        void onPageSelected(int page);
    }

    public enum Type {
        LINE, TAB
    }

    private Type type = Type.LINE;
    private LinearLayout.LayoutParams defaultTabLayoutParams;
    private LinearLayout.LayoutParams defaultExpandLayoutParams;
    private LinearLayout tabsContainer;
    private ScrollSlidingTabStripDelegate delegate;
    private HashMap<String, View> tabTypes = new HashMap<>();
    private HashMap<String, View> prevTypes = new HashMap<>();
    private SparseArray<View> futureTabsPositions = new SparseArray<>();

    View draggingView;
    float draggingViewOutProgress;
    float draggingViewIndicatorOutProgress;

    private boolean shouldExpand;

    private int tabCount;

    private int currentPosition;
    private AnimatedFloat currentPositionAnimated = new AnimatedFloat(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private RectF leftTabBounds = new RectF();
    private RectF rightTabBounds = new RectF();
    private RectF tabBounds = new RectF();
    private boolean animateFromPosition;
    private float startAnimationPosition;
    private float positionAnimationProgress;
    private long lastAnimationTime;
    private float touchSlop;

    private Paint rectPaint;

    private int indicatorColor = 0xff666666;
    private int underlineColor = 0x1a000000;
    private int indicatorHeight;
    private GradientDrawable indicatorDrawable = new GradientDrawable();

    private int scrollOffset = AndroidUtilities.dp(StickerTabView.SMALL_WIDTH);
    private int underlineHeight = AndroidUtilities.dp(2);
    private int dividerPadding = AndroidUtilities.dp(12);
    private int tabPadding = AndroidUtilities.dp(24);

    private int lastScrollX = 0;
    private final Theme.ResourcesProvider resourcesProvider;

    SparseArray<StickerTabView> currentPlayingImages = new SparseArray<>();
    SparseArray<StickerTabView> currentPlayingImagesTmp = new SparseArray<>();
    private boolean dragEnabled;
    int startDragFromPosition;
    int currentDragPosition;
    float startDragFromX;
    float dragDx;
    float pressedX;
    float pressedY;
    boolean longClickRunning;

    float draggindViewXOnScreen;
    float draggindViewDxOnScreen;
    Runnable longClickRunnable = new Runnable() {
        @Override
        public void run() {
            longClickRunning = false;
            startDragFromX = getScrollX() + pressedX;
            dragDx = 0;

            int p = (int) Math.ceil(startDragFromX / getTabSize()) - 1;
            startDragFromPosition = currentDragPosition = p;
            if (!canSwap(p)) {
                return;
            }
            if (p >= 0 && p < tabsContainer.getChildCount()) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                draggindViewDxOnScreen = 0f;
                draggingViewOutProgress = 0f;
                draggingView = tabsContainer.getChildAt(p);
                draggindViewXOnScreen = draggingView.getX() - getScrollX();
                draggingView.invalidate();
                tabsContainer.invalidate();
                invalidateOverlays();
                invalidate();
            }
        }
    };

    public ScrollSlidingTabStrip(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        setFillViewport(true);
        setWillNotDraw(false);

        setHorizontalScrollBarEnabled(false);
        tabsContainer = new LinearLayout(context) {

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child instanceof StickerTabView) {
                    ((StickerTabView) child).updateExpandProgress(expandProgress);
                }
                if (child == draggingView) {
                    return true;
                }
                return super.drawChild(canvas, child, drawingTime);
            }
        };
        tabsContainer.setOrientation(LinearLayout.HORIZONTAL);
        tabsContainer.setPadding(AndroidUtilities.dp(9.5f), 0, AndroidUtilities.dp(9.5f), 0);
        addView(tabsContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL));

        rectPaint = new Paint();
        rectPaint.setAntiAlias(true);
        rectPaint.setStyle(Style.FILL);

        defaultTabLayoutParams = new LinearLayout.LayoutParams(AndroidUtilities.dp(StickerTabView.SMALL_WIDTH), LayoutHelper.MATCH_PARENT);
        defaultExpandLayoutParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0F);
    }

    public void setDelegate(ScrollSlidingTabStripDelegate scrollSlidingTabStripDelegate) {
        delegate = scrollSlidingTabStripDelegate;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        if (type != null && this.type != type) {
            this.type = type;
            switch (type) {
                case LINE:
                    indicatorDrawable.setCornerRadius(0);
                    break;
                case TAB:
                    float rad = AndroidUtilities.dpf2(3);
                    indicatorDrawable.setCornerRadii(new float[]{rad, rad, rad, rad, 0, 0, 0, 0});
                    break;
            }
        }
    }

    public void removeTabs() {
        tabsContainer.removeAllViews();
        tabTypes.clear();
        prevTypes.clear();
        futureTabsPositions.clear();
        tabCount = 0;
        currentPosition = 0;
        animateFromPosition = false;
    }

    public void beginUpdate(boolean animated) {
        prevTypes = tabTypes;
        tabTypes = new HashMap<>();
        futureTabsPositions.clear();
        tabCount = 0;
        if (animated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            final AutoTransition transition = new AutoTransition();
            transition.setDuration(250);
            transition.setOrdering(TransitionSet.ORDERING_TOGETHER);
            transition.addTransition(new Transition() {
                @Override
                public void captureStartValues(TransitionValues transitionValues) {
                }

                @Override
                public void captureEndValues(TransitionValues transitionValues) {
                }

                @Override
                public Animator createAnimator(ViewGroup sceneRoot, TransitionValues startValues, TransitionValues endValues) {
                    final ValueAnimator invalidateAnimator = ValueAnimator.ofFloat(0, 1);
                    invalidateAnimator.addUpdateListener(a -> invalidate());
                    return invalidateAnimator;
                }
            });
            TransitionManager.beginDelayedTransition(tabsContainer, transition);
        }
    }

    public void commitUpdate() {
        if (prevTypes != null) {
            for (HashMap.Entry<String, View> entry : prevTypes.entrySet()) {
                tabsContainer.removeView(entry.getValue());
            }
            prevTypes.clear();
        }
        for (int a = 0, N = futureTabsPositions.size(); a < N; a++) {
            int index = futureTabsPositions.keyAt(a);
            View view = futureTabsPositions.valueAt(a);
            int currentIndex = tabsContainer.indexOfChild(view);
            if (currentIndex != index) {
                tabsContainer.removeView(view);
                tabsContainer.addView(view, index);
            }
        }
        futureTabsPositions.clear();
    }

    public void selectTab(int num) {
        if (num < 0 || num >= tabCount) {
            return;
        }
        View tab = tabsContainer.getChildAt(num);
        tab.performClick();
    }

    private void checkViewIndex(String key, View view, int index) {
        if (prevTypes != null) {
            prevTypes.remove(key);
        }
        futureTabsPositions.put(index, view);
    }

    public TextView addIconTabWithCounter(int id, Drawable drawable) {
        String key = "textTab" + id;
        final int position = tabCount++;

        FrameLayout tab = (FrameLayout) prevTypes.get(key);
        TextView textView;
        if (tab != null) {
            textView = (TextView) tab.getChildAt(1);
            checkViewIndex(key, tab, position);
        } else {
            tab = new FrameLayout(getContext());
            tab.setFocusable(true);
            tabsContainer.addView(tab, position);

            ImageView imageView = new ImageView(getContext());
            imageView.setImageDrawable(drawable);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            tab.setOnClickListener(v -> delegate.onPageSelected((Integer) v.getTag(R.id.index_tag)));
            tab.addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            textView = new TextView(getContext());
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            textView.setTextColor(getThemedColor(Theme.key_chat_emojiPanelBadgeText));
            textView.setGravity(Gravity.CENTER);
            textView.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(9), getThemedColor(Theme.key_chat_emojiPanelBadgeBackground)));
            textView.setMinWidth(AndroidUtilities.dp(18));
            textView.setPadding(AndroidUtilities.dp(5), 0, AndroidUtilities.dp(5), AndroidUtilities.dp(1));
            tab.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 18, Gravity.TOP | Gravity.LEFT, 26, 6, 0, 0));
        }
        tab.setTag(R.id.index_tag, position);
        tab.setSelected(position == currentPosition);

        tabTypes.put(key, tab);
        return textView;
    }

    public FrameLayout addIconTab(int id, Drawable drawable) {
        String key = "tab" + id;
        final int position = tabCount++;

        FrameLayout tab = (FrameLayout) prevTypes.get(key);
        if (tab != null) {
            checkViewIndex(key, tab, position);
        } else {
            tab = new FrameLayout(getContext());
            ImageView imageView = new ImageView(getContext());
            imageView.setImageDrawable(drawable);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            tab.addView(imageView, LayoutHelper.createFrame(StickerTabView.IMAGE_ICON_SMALL_SIZE, StickerTabView.IMAGE_ICON_SMALL_SIZE, Gravity.CENTER));
            tab.setFocusable(true);
            tab.setOnClickListener(v -> delegate.onPageSelected((Integer) v.getTag(R.id.index_tag)));
            tabsContainer.addView(tab, position);
        }
        tab.setTag(R.id.index_tag, position);
        tab.setSelected(position == currentPosition);

        tabTypes.put(key, tab);
        return tab;
    }

    public StickerTabView addStickerIconTab(int id, Drawable drawable) {
        String key = "tab" + id;
        final int position = tabCount++;

        StickerTabView tab = (StickerTabView) prevTypes.get(key);
        if (tab != null) {
            checkViewIndex(key, tab, position);
        } else {
            tab = new StickerTabView(getContext(), StickerTabView.ICON_TYPE);
            tab.iconView.setImageDrawable(drawable);
            tab.setFocusable(true);
            tab.setOnClickListener(v -> delegate.onPageSelected((Integer) v.getTag(R.id.index_tag)));
            tab.setExpanded(expanded);
            tab.updateExpandProgress(expandProgress);
            tabsContainer.addView(tab, position);
        }
        tab.isChatSticker = false;
        tab.setTag(R.id.index_tag, position);
        tab.setSelected(position == currentPosition);

        tabTypes.put(key, tab);
        return tab;
    }

    public void addStickerTab(TLRPC.Chat chat) {
        String key = "chat" + chat.id;
        final int position = tabCount++;

        StickerTabView tab = (StickerTabView) prevTypes.get(key);
        if (tab != null) {
            checkViewIndex(key, tab, position);
        } else {
            StickerTabView stickerTabView = new StickerTabView(getContext(), StickerTabView.STICKER_TYPE);
            tab = stickerTabView;
            tab.setFocusable(true);
            tab.setOnClickListener(v -> delegate.onPageSelected((Integer) v.getTag(R.id.index_tag)));
            tabsContainer.addView(tab, position);
            stickerTabView.setRoundImage();

            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setTextSize(AndroidUtilities.dp(14));
            avatarDrawable.setInfo(chat);

            BackupImageView imageView = stickerTabView.imageView;
            imageView.setLayerNum(1);
            imageView.setForUserOrChat(chat, avatarDrawable);
            imageView.setAspectFit(true);

            stickerTabView.setExpanded(expanded);
            stickerTabView.updateExpandProgress(expandProgress);
            stickerTabView.textView.setText(chat.title);
        }
        tab.isChatSticker = true;
        tab.setTag(R.id.index_tag, position);
        tab.setSelected(position == currentPosition);

        tabTypes.put(key, tab);
    }

    public View addEmojiTab(int id, Emoji.EmojiDrawable emojiDrawable, TLRPC.Document emojiSticker) {
        String key = "tab" + id;
        final int position = tabCount++;
        StickerTabView tab = (StickerTabView) prevTypes.get(key);
        if (tab != null) {
            checkViewIndex(key, tab, position);
        } else {
            tab = new StickerTabView(getContext(), StickerTabView.EMOJI_TYPE);
            tab.setFocusable(true);
            tab.setOnClickListener(v -> delegate.onPageSelected((Integer) v.getTag(R.id.index_tag)));

            tab.setExpanded(expanded);
            tab.updateExpandProgress(expandProgress);

            tabsContainer.addView(tab, position);
        }
        tab.isChatSticker = false;
        tab.setTag(R.id.index_tag, position);
        tab.setTag(R.id.parent_tag, emojiDrawable);
        tab.setTag(R.id.object_tag, emojiSticker);
        tab.setSelected(position == currentPosition);

        tabTypes.put(key, tab);
        return tab;
    }

    public View addStickerTab(TLObject thumb, TLRPC.Document sticker, TLRPC.TL_messages_stickerSet parentObject) {
        String key = "set" + (parentObject == null ? sticker.id : parentObject.set.id);
        final int position = tabCount++;

        StickerTabView tab = (StickerTabView) prevTypes.get(key);
        if (tab != null) {
            checkViewIndex(key, tab, position);
        } else {
            tab = new StickerTabView(getContext(), StickerTabView.STICKER_TYPE);
            tab.setFocusable(true);
            tab.setOnClickListener(v -> delegate.onPageSelected((Integer) v.getTag(R.id.index_tag)));

            tab.setExpanded(expanded);
            tab.updateExpandProgress(expandProgress);

            tabsContainer.addView(tab, position);
        }
        tab.isChatSticker = false;
        tab.setTag(thumb);
        tab.setTag(R.id.index_tag, position);
        tab.setTag(R.id.parent_tag, parentObject);
        tab.setTag(R.id.object_tag, sticker);
        tab.setSelected(position == currentPosition);

        tabTypes.put(key, tab);
        return tab;
    }

    boolean expanded = false;
    boolean animateToExpanded;
    ValueAnimator expandStickerAnimator;
    float expandProgress;

    public static float EXPANDED_WIDTH = 64;
    private float stickerTabExpandedWidth = AndroidUtilities.dp(EXPANDED_WIDTH);
    private float stickerTabWidth = AndroidUtilities.dp(StickerTabView.SMALL_WIDTH);
    private float expandOffset;
    private int scrollByOnNextMeasure = -1;

    public void expandStickers(float x, boolean expanded) {
//        if (expandStickerAnimator != null || draggingView != null) {
//            return;
//        }
        if (this.expanded != expanded) {
            this.expanded = expanded;

            if (!expanded) {
                fling(0);
            }

            if (expandStickerAnimator != null) {
                expandStickerAnimator.removeAllListeners();
                expandStickerAnimator.cancel();
            }
            expandStickerAnimator = ValueAnimator.ofFloat(expandProgress, expanded ? 1f : 0f);
            expandStickerAnimator.addUpdateListener(valueAnimator -> {
                if (!expanded) {
                    float allSize = stickerTabWidth * tabsContainer.getChildCount();
                    float totalXRelative = (getScrollX() + x) / (stickerTabExpandedWidth * tabsContainer.getChildCount());
                    float maxXRelative = (allSize - getMeasuredWidth()) / allSize;
                    float additionalX = x;
                    if (totalXRelative > maxXRelative) {
                        totalXRelative = maxXRelative;
                        additionalX = 0;
                    }
                    float scrollToX = allSize * totalXRelative;
                    if (scrollToX - additionalX < 0) {
                        scrollToX = additionalX;
                    }
                    expandOffset = (getScrollX() + additionalX) - scrollToX;
                }
                expandProgress = (float) valueAnimator.getAnimatedValue();
                for (int i = 0; i < tabsContainer.getChildCount(); i++) {
                    tabsContainer.getChildAt(i).invalidate();
                }
                tabsContainer.invalidate();
                updatePosition();
            });
            expandStickerAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    expandStickerAnimator = null;
                    expandProgress = expanded ? 1f : 0f;
                    for (int i = 0; i < tabsContainer.getChildCount(); i++) {
                        tabsContainer.getChildAt(i).invalidate();
                    }
                    tabsContainer.invalidate();
                    updatePosition();
                    if (!expanded) {
                        float allSize = stickerTabWidth * tabsContainer.getChildCount();
                        float totalXRelative = (getScrollX() + x) / (stickerTabExpandedWidth * tabsContainer.getChildCount());
                        float maxXRelative = (allSize - getMeasuredWidth()) / allSize;
                        float additionalX = x;
                        if (totalXRelative > maxXRelative) {
                            totalXRelative = maxXRelative;
                            additionalX = 0;
                        }
                        float scrollToX = allSize * totalXRelative;
                        if (scrollToX - additionalX < 0) {
                            scrollToX = additionalX;
                        }
                        expandOffset = (getScrollX() + additionalX) - scrollToX;
                        scrollByOnNextMeasure = (int) (scrollToX - additionalX);

                        if (scrollByOnNextMeasure < 0) {
                            scrollByOnNextMeasure = 0;
                        }

                        for (int i = 0; i < tabsContainer.getChildCount(); i++) {
                            View child = tabsContainer.getChildAt(i);
                            if (child instanceof StickerTabView) {
                                ((StickerTabView) child).setExpanded(false);
                            }
                            child.getLayoutParams().width = AndroidUtilities.dp(StickerTabView.SMALL_WIDTH);
                        }
                        animateToExpanded = false;
                        getLayoutParams().height = AndroidUtilities.dp(StickerTabView.SMALL_HEIGHT);
                        tabsContainer.requestLayout();
                    }
                }
            });
            expandStickerAnimator.start();

            if (expanded) {
                animateToExpanded = true;
                for (int i = 0; i < tabsContainer.getChildCount(); i++) {
                    View child = tabsContainer.getChildAt(i);
                    if (child instanceof StickerTabView) {
                        ((StickerTabView) child).setExpanded(true);
                    }
                    child.getLayoutParams().width = AndroidUtilities.dp(EXPANDED_WIDTH);
                }

                tabsContainer.requestLayout();
                getLayoutParams().height = AndroidUtilities.dp(StickerTabView.SMALL_HEIGHT + 50);
            }

            if (expanded) {
                float totalXRelative = (getScrollX() + x) / (stickerTabWidth * tabsContainer.getChildCount());
                float scrollToX = stickerTabExpandedWidth * tabsContainer.getChildCount() * totalXRelative;

                expandOffset = scrollToX - (getScrollX() + x);
                scrollByOnNextMeasure = (int) (scrollToX - x);
            }
        }
    }

    protected void updatePosition() {

    }

    public float getExpandedOffset() {
        return animateToExpanded ? AndroidUtilities.dp(50) * expandProgress : 0;
    }

    public void updateTabStyles() {
        for (int i = 0; i < tabCount; i++) {
            View v = tabsContainer.getChildAt(i);
            if (shouldExpand) {
                v.setLayoutParams(defaultExpandLayoutParams);
            } else {
                v.setLayoutParams(defaultTabLayoutParams);
            }
        }
    }

    private void scrollToChild(int position) {
        if (tabCount == 0 || tabsContainer.getChildAt(position) == null) {
            return;
        }
        int newScrollX = tabsContainer.getChildAt(position).getLeft();
        if (position > 0) {
            newScrollX -= scrollOffset;
        }
        int currentScrollX = getScrollX();
        if (newScrollX != lastScrollX) {
            if (newScrollX < currentScrollX) {
                lastScrollX = newScrollX;
                smoothScrollTo(lastScrollX, 0);
            } else if (newScrollX + scrollOffset > currentScrollX + getWidth() - scrollOffset * 2) {
                lastScrollX = newScrollX - getWidth() + scrollOffset * 3;
                smoothScrollTo(lastScrollX, 0);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        setImages();
        if (scrollByOnNextMeasure >= 0) {
            scrollTo(scrollByOnNextMeasure, 0);
            scrollByOnNextMeasure = -1;
        }
    }

    public void setImages() {
        float tabSize = AndroidUtilities.dp(StickerTabView.SMALL_WIDTH) + AndroidUtilities.dp(EXPANDED_WIDTH - StickerTabView.SMALL_WIDTH) * expandProgress;
        float scrollOffset = animateToExpanded ? expandOffset * (1f - expandProgress) : 0;
        int start = (int) ((getScrollX() - scrollOffset - tabsContainer.getPaddingLeft()) / tabSize);
        int end = Math.min(tabsContainer.getChildCount(), start + (int) Math.ceil(getMeasuredWidth() / tabSize) + 1);
        if (animateToExpanded) {
            start -= 2;
            end += 2;
            if (start < 0) {
                start = 0;
            }
            if (end > tabsContainer.getChildCount()) {
                end = tabsContainer.getChildCount();
            }
        }
        currentPlayingImagesTmp.clear();
        for (int i = 0; i < currentPlayingImages.size(); i++) {
            currentPlayingImagesTmp.put(currentPlayingImages.valueAt(i).index, currentPlayingImages.valueAt(i));
        }
        currentPlayingImages.clear();

        for (int a = start; a < end; a++) {
            View child = tabsContainer.getChildAt(a);
            if (child instanceof StickerTabView) {
                StickerTabView tabView = (StickerTabView) child;
                if (tabView.type == StickerTabView.EMOJI_TYPE) {
                    Object thumb = tabView.getTag(R.id.parent_tag);
                    Object sticker = tabView.getTag(R.id.object_tag);
                    Drawable thumbDrawable = null;
                    if (thumb instanceof Drawable) {
                        thumbDrawable = (Drawable) thumb;
                    }
                    if (sticker instanceof TLRPC.Document) {
                        tabView.imageView.setImage(ImageLocation.getForDocument((TLRPC.Document) sticker), SharedConfig.getLiteMode().enabled() ? "36_36_firstframe" : "36_36_nolimit", (Drawable) null, null);
                    } else {
                        tabView.imageView.setImageDrawable(thumbDrawable);
                    }
                } else {
                    Object object = child.getTag();
                    Object parentObject = child.getTag(R.id.parent_tag);
                    TLRPC.Document sticker = (TLRPC.Document) child.getTag(R.id.object_tag);
                    ImageLocation imageLocation;

                    if (object instanceof TLRPC.Document) {
                        if (!tabView.inited) {
                            tabView.svgThumb = DocumentObject.getSvgThumb((TLRPC.Document) object, Theme.key_emptyListPlaceholder, 0.2f);
                        }
                        imageLocation = ImageLocation.getForDocument(sticker);
                    } else if (object instanceof TLRPC.PhotoSize) {
                        TLRPC.PhotoSize thumb = (TLRPC.PhotoSize) object;
                        int thumbVersion = 0;
                        if (parentObject instanceof TLRPC.TL_messages_stickerSet) {
                            thumbVersion = ((TLRPC.TL_messages_stickerSet) parentObject).set.thumb_version;
                        }
                        imageLocation = ImageLocation.getForSticker(thumb, sticker, thumbVersion);
                    } else {
                        continue;
                    }

                    if (!tabView.inited && tabView.svgThumb == null && sticker != null) {
                        tabView.svgThumb = DocumentObject.getSvgThumb(sticker, Theme.key_emptyListPlaceholder, 0.2f);
                    }

                    if (imageLocation == null) {
                        continue;
                    }
                    tabView.inited = true;
                    SvgHelper.SvgDrawable svgThumb = tabView.svgThumb;
                    BackupImageView imageView = tabView.imageView;
                    String imageFilter = SharedConfig.getLiteMode().enabled() ? "40_40_firstframe" : "40_40";
                    if (MessageObject.isVideoSticker(sticker) && sticker.thumbs != null && sticker.thumbs.size() > 0) {
                        if (SharedConfig.getLiteMode().enabled()) {
                            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(sticker.thumbs, 90);
                            imageView.setImage(ImageLocation.getForDocument(thumb, sticker), "40_40", svgThumb, 0, parentObject);
                        } else if (svgThumb != null) {
                            imageView.setImage(ImageLocation.getForDocument(sticker), imageFilter, svgThumb, 0, parentObject);
                        } else {
                            imageView.setImage(ImageLocation.getForDocument(sticker), imageFilter, imageLocation, null, 0, parentObject);
                        }
                    } else if (MessageObject.isAnimatedStickerDocument(sticker, true)) {
                        if (svgThumb != null) {
                            imageView.setImage(imageLocation, imageFilter, svgThumb, 0, parentObject);
                        } else {
                            imageView.setImage(imageLocation, imageFilter, imageLocation, null, 0, parentObject);
                        }
                    } else if (imageLocation.imageType == FileLoader.IMAGE_TYPE_LOTTIE) {
                        imageView.setImage(imageLocation, imageFilter, "tgs", svgThumb, parentObject);
                    } else {
                        imageView.setImage(imageLocation, imageFilter, "webp", svgThumb, parentObject);
                    }
                    String title = null;
                    if (parentObject instanceof TLRPC.TL_messages_stickerSet) {
                        title = ((TLRPC.TL_messages_stickerSet) parentObject).set.title;
                    }
                    tabView.textView.setText(title);
                }
                currentPlayingImages.put(tabView.index, tabView);
                currentPlayingImagesTmp.remove(tabView.index);
            }
        }

        for (int i = 0; i < currentPlayingImagesTmp.size(); i++) {
            StickerTabView stickerTabView = currentPlayingImagesTmp.valueAt(i);
            if (stickerTabView != draggingView) {
                currentPlayingImagesTmp.valueAt(i).imageView.setImageDrawable(null);
            }
        }
    }

    private int getTabSize() {
        return AndroidUtilities.dp(animateToExpanded ? EXPANDED_WIDTH : StickerTabView.SMALL_WIDTH);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        setImages();
    }

    private Paint selectorPaint = new Paint();

    private boolean showSelected = true;
    private AnimatedFloat showSelectedAlpha = new AnimatedFloat(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

    public void showSelected(boolean show) {
        this.showSelected = show;
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        final float dif = (stickerTabWidth - stickerTabExpandedWidth);
        final float offset = expandOffset * (1f - expandProgress);
        for (int i = 0; i < tabsContainer.getChildCount(); i++) {
            if (tabsContainer.getChildAt(i) instanceof StickerTabView) {
                StickerTabView stickerTabView = (StickerTabView) tabsContainer.getChildAt(i);
                stickerTabView.animateIfPositionChanged(this);
                if (animateToExpanded) {
                    stickerTabView.setTranslationX(dif * i * (1f - expandProgress) + offset + stickerTabView.dragOffset);
                } else {
                    stickerTabView.setTranslationX(stickerTabView.dragOffset);
                }
            }
        }

        float height = getHeight();
        if (animateToExpanded) {
            height = getHeight() - AndroidUtilities.dp(50) * (1f - expandProgress);
        }

        float selectedAlpha = showSelectedAlpha.set(showSelected ? 1 : 0);

        if (!(isInEditMode() || tabCount == 0) && indicatorHeight >= 0) {
            float position = currentPositionAnimated.set(currentPosition);
            int floorPosition = (int) Math.floor(position);
            int ceilPosition =  (int) Math.ceil(position);

            View leftTab = floorPosition < 0 || floorPosition >= tabsContainer.getChildCount() ? null : tabsContainer.getChildAt(floorPosition);
            View rightTab = ceilPosition < 0 || ceilPosition >= tabsContainer.getChildCount() ? null : tabsContainer.getChildAt(ceilPosition);

            float cy = height / 2f;
            float cx;

            float textWidth;

            if (leftTab != null && rightTab != null) {
                cx = AndroidUtilities.lerp(
                        leftTab.getLeft() + leftTab.getTranslationX() + AndroidUtilities.lerp(AndroidUtilities.dp(StickerTabView.SMALL_WIDTH), AndroidUtilities.dp(EXPANDED_WIDTH), expandProgress) / 2f,
                        rightTab.getLeft() + rightTab.getTranslationX() + AndroidUtilities.lerp(AndroidUtilities.dp(StickerTabView.SMALL_WIDTH), AndroidUtilities.dp(EXPANDED_WIDTH), expandProgress) / 2f,
                        position - floorPosition
                );
                textWidth = AndroidUtilities.lerp(
                        (leftTab instanceof StickerTabView ? ((StickerTabView) leftTab).getTextWidth() : 0),
                        (rightTab instanceof StickerTabView ? ((StickerTabView) rightTab).getTextWidth() : 0),
                        position - floorPosition
                );
            } else {
                if (leftTab != null) {
                    cx = leftTab.getLeft() + leftTab.getTranslationX() + AndroidUtilities.lerp(AndroidUtilities.dp(StickerTabView.SMALL_WIDTH), AndroidUtilities.dp(EXPANDED_WIDTH), expandProgress) / 2f;
                    textWidth = leftTab instanceof StickerTabView ? ((StickerTabView) leftTab).getTextWidth() : 0;
                } else if (rightTab != null) {
                    cx = rightTab.getLeft() + rightTab.getTranslationX() + AndroidUtilities.lerp(AndroidUtilities.dp(StickerTabView.SMALL_WIDTH), AndroidUtilities.dp(EXPANDED_WIDTH), expandProgress) / 2f;
                    textWidth = rightTab instanceof StickerTabView ? ((StickerTabView) rightTab).getTextWidth() : 0;
                } else {
                    cx = 0;
                    textWidth = 0;
                }
            }

            float sz = AndroidUtilities.dp(30);
            float w = sz * (1.25f - .25f * Math.abs(.5f - currentPositionAnimated.getTransitionProgressInterpolated()) * 2);
            float h = sz * (.9f + .1f * Math.abs(.5f - currentPositionAnimated.getTransitionProgressInterpolated()) * 2);
            float expandProgressInterpolated = CubicBezierInterpolator.EASE_IN.getInterpolation(expandProgress);
            w = AndroidUtilities.lerp(w, textWidth + AndroidUtilities.dp(10), expandProgressInterpolated);
            cy += AndroidUtilities.lerp(0, AndroidUtilities.dp(26), expandProgressInterpolated);
            h *= AndroidUtilities.lerp(1f, 0.55f, expandProgressInterpolated);
            tabBounds.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);

            selectorPaint.setColor(0x2effffff & getThemedColor(Theme.key_chat_emojiPanelIcon));
            selectorPaint.setAlpha((int) (selectorPaint.getAlpha() * selectedAlpha));
            canvas.drawRoundRect(tabBounds, AndroidUtilities.dp(8), AndroidUtilities.dp(8), selectorPaint);
        }

        super.dispatchDraw(canvas);

        if (!(isInEditMode() || tabCount == 0) && underlineHeight > 0) {
            rectPaint.setColor(underlineColor);
            canvas.drawRect(0, height - underlineHeight, tabsContainer.getWidth(), height, rectPaint);
        }
    }

    public void drawOverlays(Canvas canvas) {
        if (draggingView != null) {
            canvas.save();
            float x = draggindViewXOnScreen - draggindViewDxOnScreen;
            if (draggingViewOutProgress > 0) {
                x = x * (1f - draggingViewOutProgress) + (draggingView.getX() - getScrollX()) * draggingViewOutProgress;
            }
            canvas.translate(x, 0);
            draggingView.draw(canvas);
            canvas.restore();
        }
    }

    public void setShouldExpand(boolean value) {
        shouldExpand = value;
        requestLayout();
    }

    public int getCurrentPosition() {
        return currentPosition;
    }

    public void cancelPositionAnimation() {
        animateFromPosition = false;
        positionAnimationProgress = 1.0f;
    }

    public void onPageScrolled(int position, int first) {
        if (currentPosition == position) {
            return;
        }

        View currentTab = tabsContainer.getChildAt(currentPosition);
        if (currentTab != null) {
            startAnimationPosition = currentTab.getLeft();
            positionAnimationProgress = 0.0f;
            animateFromPosition = true;
            lastAnimationTime = SystemClock.elapsedRealtime();
        } else {
            animateFromPosition = false;
        }
        currentPosition = position;
        if (position >= tabsContainer.getChildCount()) {
            return;
        }
        positionAnimationProgress = 0.0f;
        for (int a = 0; a < tabsContainer.getChildCount(); a++) {
            tabsContainer.getChildAt(a).setSelected(a == position);
        }
        if (expandStickerAnimator == null) {
            if (first == position && position > 1) {
                scrollToChild(position - 1);
            } else {
                scrollToChild(position);
            }
        }
        invalidate();
    }

    public void invalidateTabs() {
        for (int a = 0, N = tabsContainer.getChildCount(); a < N; a++) {
            tabsContainer.getChildAt(a).invalidate();
        }
    }

    public void setCurrentPosition(int currentPosition) {
        this.currentPosition = currentPosition;
    }

    public void setIndicatorHeight(int value) {
        indicatorHeight = value;
        invalidate();
    }

    public void setIndicatorColor(int value) {
        indicatorColor = value;
        invalidate();
    }

    public void setUnderlineColor(int value) {
        underlineColor = value;
        invalidate();
    }

    public void setUnderlineColorResource(int resId) {
        underlineColor = getResources().getColor(resId);
        invalidate();
    }

    public void setUnderlineHeight(int value) {
        if (underlineHeight != value) {
            underlineHeight = value;
            invalidate();
        }
    }

    protected void invalidateOverlays() {

    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return checkLongPress(ev) || super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return checkLongPress(ev) || super.onTouchEvent(ev);
    }

    public boolean checkLongPress(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN && draggingView == null) {
            longClickRunning = true;
            AndroidUtilities.runOnUIThread(longClickRunnable, 500);
            pressedX = ev.getX();
            pressedY = ev.getY();
        }
        if (longClickRunning && ev.getAction() == MotionEvent.ACTION_MOVE) {
            if (Math.abs(ev.getX() - pressedX) > touchSlop || Math.abs(ev.getY() - pressedY) > touchSlop) {
                longClickRunning = false;
                AndroidUtilities.cancelRunOnUIThread(longClickRunnable);
            }
        }
        if (ev.getAction() == MotionEvent.ACTION_MOVE && draggingView != null) {
            float x = getScrollX() + ev.getX();
            int p = (int) Math.ceil(x / getTabSize()) - 1;
            if (p != currentDragPosition) {
                if (p < currentDragPosition) {
                    while (!canSwap(p) && p != currentDragPosition) {
                        p++;
                    }
                } else {
                    while (!canSwap(p) && p != currentDragPosition) {
                        p--;
                    }
                }
            }
            if (currentDragPosition != p && canSwap(p)) {
                for (int i = 0; i < tabsContainer.getChildCount(); i++) {
                    if (i == currentDragPosition) {
                        continue;
                    }
                    StickerTabView stickerTabView = (StickerTabView) tabsContainer.getChildAt(i);
                    stickerTabView.saveXPosition();
                }

                startDragFromX += (p - currentDragPosition) * getTabSize();
                currentDragPosition = p;
                tabsContainer.removeView(draggingView);
                tabsContainer.addView(draggingView, currentDragPosition);
                invalidate();
            }
            dragDx = x - startDragFromX;
            draggindViewDxOnScreen = pressedX - ev.getX();
            float viewScreenX = ev.getX();
            if (viewScreenX < draggingView.getMeasuredWidth() / 2f) {
                startScroll(false);
            } else if (viewScreenX > getMeasuredWidth() - draggingView.getMeasuredWidth() / 2f) {
                startScroll(true);
            } else {
                stopScroll();
            }
            tabsContainer.invalidate();
            invalidateOverlays();
            return true;
        }
        if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
            stopScroll();
            AndroidUtilities.cancelRunOnUIThread(longClickRunnable);
            if (draggingView != null) {
                if (startDragFromPosition != currentDragPosition) {
                    stickerSetPositionChanged(startDragFromPosition, currentDragPosition);
                    for (int i = 0; i < tabsContainer.getChildCount(); i++) {
                        tabsContainer.getChildAt(i).setTag(R.id.index_tag, i);
                    }
                }
                ValueAnimator dragViewOutAnimator = ValueAnimator.ofFloat(0, 1f);
                dragViewOutAnimator.addUpdateListener(valueAnimator -> {
                    draggingViewOutProgress = (float) valueAnimator.getAnimatedValue();
                    invalidateOverlays();
                });
                dragViewOutAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (draggingView != null) {
                            invalidateOverlays();
                            draggingView.invalidate();
                            tabsContainer.invalidate();
                            invalidate();
                            draggingView = null;
                        }
                    }
                });
                dragViewOutAnimator.start();
            }
            longClickRunning = false;
            invalidateOverlays();
        }
        return false;
    }

    protected void stickerSetPositionChanged(int fromPosition, int toPosition) {
    }

    private boolean canSwap(int p) {
        if (!dragEnabled) {
            return false;
        }
        if (p < 0 || p >= tabsContainer.getChildCount()) {
            return false;
        }
        View child = tabsContainer.getChildAt(p);
        if (child instanceof StickerTabView && ((StickerTabView) child).type == StickerTabView.STICKER_TYPE && !((StickerTabView) child).isChatSticker) {
            return true;
        }
        return false;
    }

    boolean scrollRight;
    long scrollStartTime;
    Runnable scrollRunnable = new Runnable() {
        @Override
        public void run() {
            long currentTime = System.currentTimeMillis() - scrollStartTime;
            int dx;
            if (currentTime < 3000) {
                dx = Math.max(1, AndroidUtilities.dp(1)) * (scrollRight ? 1 : -1);
            } else if (currentTime < 5000) {
                dx = Math.max(1, AndroidUtilities.dp(2)) * (scrollRight ? 1 : -1);
            } else {
                dx = Math.max(1, AndroidUtilities.dp(4)) * (scrollRight ? 1 : -1);
            }

            scrollBy(dx, 0);
            AndroidUtilities.runOnUIThread(scrollRunnable);
        }
    };

    private void startScroll(boolean scrollRight) {
        this.scrollRight = scrollRight;
        if (scrollStartTime <= 0) {
            scrollStartTime = System.currentTimeMillis();
        }
        AndroidUtilities.runOnUIThread(scrollRunnable, 16);
    }

    private void stopScroll() {
        scrollStartTime = -1;
        AndroidUtilities.cancelRunOnUIThread(scrollRunnable);
    }

    boolean isDragging() {
        return draggingView != null;
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        longClickRunning = false;
        AndroidUtilities.cancelRunOnUIThread(longClickRunnable);
    }

    public void setDragEnabled(boolean enabled) {
        dragEnabled = enabled;
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }
}
