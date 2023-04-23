package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Fetcher;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.Set;

public class StickerCategoriesListView extends RecyclerListView {

    @IntDef({CategoriesType.DEFAULT, CategoriesType.STATUS, CategoriesType.PROFILE_PHOTOS})
    @Retention(RetentionPolicy.SOURCE)
    public static @interface CategoriesType {
        int DEFAULT = 0;
        int STATUS = 1;
        int PROFILE_PHOTOS = 2;
    }

    private float shownButtonsAtStart = 6.5f;

    private static EmojiGroupFetcher fetcher = new EmojiGroupFetcher();
    public static Fetcher<String, TLRPC.TL_emojiList> search = new EmojiSearch();
    private EmojiCategory[] categories = null;

    private Adapter adapter;
    private LinearLayoutManager layoutManager;

    private AnimatedFloat leftBoundAlpha = new AnimatedFloat(this, 360, CubicBezierInterpolator.EASE_OUT_QUINT);
    private AnimatedFloat rightBoundAlpha = new AnimatedFloat(this, 360, CubicBezierInterpolator.EASE_OUT_QUINT);
    private Drawable leftBoundDrawable;
    private Drawable rightBoundDrawable;
    private Paint backgroundPaint;

    @CategoriesType
    private int categoriesType;
    private static Set<Integer> loadedIconsType = new HashSet<>();

    private Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int paddingWidth;
    private int dontOccupyWidth;
    private Utilities.Callback<Integer> onScrollIntoOccupiedWidth;
    private Utilities.Callback<Boolean> onScrollFully;
    private boolean scrolledIntoOccupiedWidth;
    private boolean scrolledFully;

    private View paddingView;

    public Integer layerNum;

    private int selectedCategoryIndex = -1;
    private Utilities.Callback<EmojiCategory> onCategoryClick;

    public static void preload(int account, @CategoriesType int type) {
        fetcher.fetch(account, type, emojiGroups -> {
            if (emojiGroups == null || emojiGroups.groups == null) {
                return;
            }
            for (TLRPC.TL_emojiGroup group : emojiGroups.groups) {
                AnimatedEmojiDrawable.getDocumentFetcher(account).fetchDocument(group.icon_emoji_id, null);
            }
        });
    }

    public StickerCategoriesListView(Context context, @CategoriesType int categoriesType) {
        this(context, null, categoriesType, null);
    }

    public StickerCategoriesListView(Context context, @CategoriesType int categoriesType, Theme.ResourcesProvider resourcesProvider) {
        this(context, null, categoriesType, resourcesProvider);
    }

    public StickerCategoriesListView(Context context, EmojiCategory[] additionalCategories, @CategoriesType int categoriesType) {
        this(context, additionalCategories, categoriesType, null);
    }

    public StickerCategoriesListView(Context context, EmojiCategory[] additionalCategories, @CategoriesType int categoriesType, Theme.ResourcesProvider resourcesProvider) {
        super(context, resourcesProvider);

        this.categoriesType = categoriesType;
        setPadding(0, 0, dp(2), 0);

        setAdapter(adapter = new Adapter());
        setLayoutManager(layoutManager = new LinearLayoutManager(context));
        layoutManager.setOrientation(HORIZONTAL);

//        setSelectorRadius(dp(15));
//        setSelectorType(Theme.RIPPLE_MASK_CIRCLE_20DP);
//        setSelectorDrawableColor(getThemedColor(Theme.key_listSelector));
        selectedPaint.setColor(getThemedColor(Theme.key_listSelector));

        setWillNotDraw(false);

        setOnItemClickListener((view, position) -> onItemClick(position, view));

        long start = System.currentTimeMillis();
        fetcher.fetch(UserConfig.selectedAccount, categoriesType, (emojiGroups) -> {
            if (emojiGroups != null) {
                categories = new EmojiCategory[(additionalCategories == null ? 0 : additionalCategories.length) + emojiGroups.groups.size()];
                int i = 0;
                if (additionalCategories != null) {
                    for (; i < additionalCategories.length; ++i) {
                        categories[i] = additionalCategories[i];
                    }
                }
                for (int j = 0; j < emojiGroups.groups.size(); ++j) {
                    categories[i + j] = EmojiCategory.remote(emojiGroups.groups.get(j));
                }
                adapter.notifyDataSetChanged();
                setCategoriesShownT(0);
                updateCategoriesShown(categoriesShouldShow, System.currentTimeMillis() - start > 16);
            }
        });
    }

    @Override
    public Integer getSelectorColor(int position) {
        return 0;
    }

    public void setShownButtonsAtStart(float buttonsCount) {
        shownButtonsAtStart = buttonsCount;
    }

    private void onItemClick(int position, View view) {
        if (position < 1) {
            return;
        }

        if (categories == null) {
            return;
        }

        EmojiCategory category = categories[position - 1];
        int minimumPadding = dp(64);
        if (getMeasuredWidth() - view.getRight() < minimumPadding) {
            smoothScrollBy((minimumPadding - (getMeasuredWidth() - view.getRight())), 0, CubicBezierInterpolator.EASE_OUT_QUINT);
        } else if (view.getLeft() < minimumPadding) {
            smoothScrollBy(-(minimumPadding - view.getLeft()), 0, CubicBezierInterpolator.EASE_OUT_QUINT);
        }
        if (onCategoryClick != null) {
            onCategoryClick.run(category);
        }
    }

    private int getScrollToStartWidth() {
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            if (child instanceof CategoryButton) {
                return paddingWidth + Math.max(0, (getChildAdapterPosition(child) - 1) * getHeight()) + (-child.getLeft());
            } else {
                return -child.getLeft();
            }
        }
        return 0;
    }

    public void scrollToStart() {
        smoothScrollBy(-getScrollToStartWidth(), 0, CubicBezierInterpolator.EASE_OUT_QUINT);
    }

    public void selectCategory(EmojiCategory category) {
        int index = -1;
        if (categories != null) {
            for (int i = 0; i < categories.length; ++i) {
                if (categories[i] == category) {
                    index = i;
                    break;
                }
            }
        }
        selectCategory(index);
    }

    public void selectCategory(int categoryIndex) {
        if (selectedCategoryIndex < 0) {
            selectedIndex.set(categoryIndex, true);
        }
        this.selectedCategoryIndex = categoryIndex;
        for (int i = 0; i < getChildCount(); ++i) {
            View child = getChildAt(i);
            if (child instanceof CategoryButton) {
                final int position = getChildAdapterPosition(child);
                ((CategoryButton) child).setSelected(selectedCategoryIndex == position - 1, true);
            }
        }
        invalidate();
    }

    public EmojiCategory getSelectedCategory() {
        if (categories == null || selectedCategoryIndex < 0 || selectedCategoryIndex >= categories.length) {
            return null;
        }
        return categories[selectedCategoryIndex];
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateCategoriesShown(categoriesShouldShow, false);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (paddingView != null) {
            paddingView.requestLayout();
        }
    }

    private float categoriesShownT = 0;
    private ValueAnimator categoriesShownAnimator;
    private boolean categoriesShouldShow = true;
    public void updateCategoriesShown(boolean show, boolean animated) {
        categoriesShouldShow = show;
        if (categories == null) {
            show = false;
        }

        if (categoriesShownT == (show ? 1 : 0)) {
            return;
        }

        if (categoriesShownAnimator != null) {
            categoriesShownAnimator.cancel();
            categoriesShownAnimator = null;
        }

        if (animated) {
            categoriesShownAnimator = ValueAnimator.ofFloat(categoriesShownT, show ? 1 : 0);
            categoriesShownAnimator.addUpdateListener(anm -> {
                setCategoriesShownT((float) anm.getAnimatedValue());
            });
            categoriesShownAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    setCategoriesShownT((float) categoriesShownAnimator.getAnimatedValue());
                    categoriesShownAnimator = null;
                }
            });
            categoriesShownAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            categoriesShownAnimator.setDuration((categories == null ? 5 : categories.length) * 120L);
            categoriesShownAnimator.start();
        } else {
            setCategoriesShownT(show ? 1 : 0);
        }
    }

    private void setCategoriesShownT(float t) {
        categoriesShownT = t;

        for (int i = 0; i < getChildCount(); ++i) {
            View child = getChildAt(i);
            if (child instanceof CategoryButton) {
                int position = getChildAdapterPosition(child);
                float childT = AndroidUtilities.cascade(t, getChildCount() - 1 - position, getChildCount() - 1, 3f);
                if (childT > 0 && child.getAlpha() <= 0) {
                    ((CategoryButton) child).play();
                }
                child.setAlpha(childT);
                child.setScaleX(childT);
                child.setScaleY(childT);
            }
        }

        invalidate();
    }

    public boolean isCategoriesShown() {
        return categoriesShownT > .5f;
    }

    @Override
    public void onScrolled(int dx, int dy) {
        super.onScrolled(dx, dy);

        boolean scrolledIntoOccupiedWidth = false;
        boolean scrolledFully = false;
        if (getChildCount() > 0) {
            View child = getChildAt(0);
            if (child instanceof CategoryButton) {
                scrolledIntoOccupiedWidth = true;
                scrolledFully = true;
            } else {
                scrolledIntoOccupiedWidth = child.getRight() <= dontOccupyWidth;
            }
        }
        if (this.scrolledIntoOccupiedWidth != scrolledIntoOccupiedWidth) {
            this.scrolledIntoOccupiedWidth = scrolledIntoOccupiedWidth;
            if (onScrollIntoOccupiedWidth != null) {
                onScrollIntoOccupiedWidth.run(this.scrolledIntoOccupiedWidth ? Math.max(0, getScrollToStartWidth() - (paddingWidth - dontOccupyWidth)) : 0);
            }
            invalidate();
        } else if (this.scrolledIntoOccupiedWidth && onScrollIntoOccupiedWidth != null) {
            onScrollIntoOccupiedWidth.run(Math.max(0, getScrollToStartWidth() - (paddingWidth - dontOccupyWidth)));
        }
        if (this.scrolledFully != scrolledFully) {
            this.scrolledFully = scrolledFully;
            if (onScrollFully != null) {
                onScrollFully.run(this.scrolledFully);
            }
            invalidate();
        }
    }

    public void setDontOccupyWidth(int dontOccupyWidth) {
        this.dontOccupyWidth = dontOccupyWidth;
    }

    public void setOnScrollIntoOccupiedWidth(Utilities.Callback<Integer> onScrollIntoOccupiedWidth) {
        this.onScrollIntoOccupiedWidth = onScrollIntoOccupiedWidth;
    }

    public void setOnScrollFully(Utilities.Callback<Boolean> onScrollFully) {
        this.onScrollFully = onScrollFully;
    }

    public void setOnCategoryClick(Utilities.Callback<EmojiCategory> onCategoryClick) {
        this.onCategoryClick = onCategoryClick;
    }

    public boolean isScrolledIntoOccupiedWidth() {
        return scrolledIntoOccupiedWidth;
    }

    @Override
    public void setBackgroundColor(int color) {
        if (backgroundPaint == null) {
            backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        }
        backgroundPaint.setColor(color);
        leftBoundDrawable = getContext().getResources().getDrawable(R.drawable.gradient_right).mutate();
        leftBoundDrawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
        rightBoundDrawable = getContext().getResources().getDrawable(R.drawable.gradient_left).mutate();
        rightBoundDrawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
    }

    private AnimatedFloat selectedAlpha = new AnimatedFloat(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
    private AnimatedFloat selectedIndex = new AnimatedFloat(this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

    @Override
    public void draw(Canvas canvas) {

        if (backgroundPaint != null) {
            int left = Integer.MAX_VALUE;
            int right = Integer.MIN_VALUE;

            for (int i = 0; i < getChildCount(); ++i) {
                final View child = getChildAt(i);
                if (child instanceof CategoryButton) {
                    left = Math.min(left, child.getLeft());
                    right = Math.max(right, child.getRight());
                }
            }

            if (left < right) {
                left += (getWidth() + dp(32)) * (1f - categoriesShownT);
                right += (getWidth() + dp(32)) * (1f - categoriesShownT);

//                if (left > 0 && rightBoundDrawable != null) {
//                    rightBoundDrawable.setAlpha(0xFF);
//                    rightBoundDrawable.setBounds(left - rightBoundDrawable.getIntrinsicWidth(), 0, left, getHeight());
//                    rightBoundDrawable.draw(canvas);
//                }
                canvas.drawRect(left, 0, right, getHeight(), backgroundPaint);
                if (right < getWidth() && leftBoundDrawable != null) {
                    leftBoundDrawable.setAlpha(0xFF);
                    leftBoundDrawable.setBounds(right, 0, right + leftBoundDrawable.getIntrinsicWidth(), getHeight());
                    leftBoundDrawable.draw(canvas);
                }
            }
        }

        drawSelectedHighlight(canvas);

        super.draw(canvas);

        if (leftBoundDrawable != null) {
            leftBoundDrawable.setAlpha((int) (0xFF * leftBoundAlpha.set(canScrollHorizontally(-1) && scrolledFully ? 1 : 0) * categoriesShownT));
            if (leftBoundDrawable.getAlpha() > 0) {
                leftBoundDrawable.setBounds(0, 0, leftBoundDrawable.getIntrinsicWidth(), getHeight());
                leftBoundDrawable.draw(canvas);
            }
        }

//        if (rightBoundDrawable != null) {
//            rightBoundDrawable.setAlpha((int) (0xFF * rightBoundAlpha.set(canScrollHorizontally(1) ? 1 : 0) * categoriesShownT));
//            if (rightBoundDrawable.getAlpha() > 0) {
//                rightBoundDrawable.setBounds(getWidth() - rightBoundDrawable.getIntrinsicWidth(), 0, getWidth(), getHeight());
//                rightBoundDrawable.draw(canvas);
//            }
//        }
    }

    private RectF rect1 = new RectF(), rect2 = new RectF(), rect3 = new RectF();
    private void drawSelectedHighlight(Canvas canvas) {
        float alpha = selectedAlpha.set(selectedCategoryIndex >= 0 ? 1 : 0);
        float index = selectedCategoryIndex >= 0 ? selectedIndex.set(selectedCategoryIndex) : selectedIndex.get();

        if (alpha <= 0) {
            return;
        }

        int fromPosition = Math.max(1, (int) Math.floor(index + 1));
        int toPosition = Math.max(1, (int) Math.ceil(index + 1));

        View fromChild = null, toChild = null;

        for (int i = 0; i < getChildCount(); ++i) {
            View child = getChildAt(i);
            int position = getChildAdapterPosition(child);

            if (position == fromPosition) {
                fromChild = child;
            }
            if (position == toPosition) {
                toChild = child;
            }

            if (fromChild != null && toChild != null) {
                break;
            }
        }

        int wasAlpha = selectedPaint.getAlpha();
        selectedPaint.setAlpha((int) (wasAlpha * alpha));
        if (fromChild != null && toChild != null) {
            float t = fromPosition == toPosition ? .5f : (index + 1 - fromPosition) / (toPosition - fromPosition);
            getChildBounds(fromChild, rect1);
            getChildBounds(toChild, rect2);
            AndroidUtilities.lerp(rect1, rect2, t, rect3);
//            float T = selectedIndex.getTransitionProgress();
//            float isMiddle = 4f * T * (1f - T);
//            float hw = rect3.width() / 2 * (1f + isMiddle * .05f);
//            float hh = rect3.height() / 2 * (1f - isMiddle * .1f);
//            rect3.set(rect3.centerX() - hw, rect3.centerY() - hh, rect3.centerX() + hw, rect3.centerY() + hh);
            canvas.drawRoundRect(rect3, AndroidUtilities.dp(15), AndroidUtilities.dp(15), selectedPaint);
        }
        selectedPaint.setAlpha(wasAlpha);
    }

    private void getChildBounds(View child, RectF rect) {
        float cx = (child.getRight() + child.getLeft()) / 2f;
        float cy = (child.getBottom() + child.getTop()) / 2f;
        float r = child.getWidth() / 2f - dp(1);
        float s = child instanceof CategoryButton ? ((CategoryButton) child).getScale() : 1f;
        rect.set(
            cx - r * s, cy - r * s,
            cx + r * s, cy + r * s
        );
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            final View child = findChildViewUnder(ev.getX(), ev.getY());
            if (!(child instanceof CategoryButton) || child.getAlpha() < .5f) {
                return false;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        private static final int VIEW_TYPE_PADDING = 0;
        private static final int VIEW_TYPE_CATEGORY = 1;

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == VIEW_TYPE_PADDING) {
                view = paddingView = new View(getContext()) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        int fullWidth = MeasureSpec.getSize(widthMeasureSpec);
                        if (fullWidth <= 0) {
                            fullWidth = ((View) getParent()).getMeasuredWidth();
                        }
                        final int BUTTON_WIDTH = MeasureSpec.getSize(heightMeasureSpec) - dp(4);
                        super.onMeasure(
                            MeasureSpec.makeMeasureSpec(
                                paddingWidth = Math.max(
                                    dontOccupyWidth > 0 ? dontOccupyWidth + dp(4) : 0,
                                    (int) (fullWidth - Math.min((getItemCount() - 1) * BUTTON_WIDTH + dp(4), shownButtonsAtStart * BUTTON_WIDTH))
                                ),
                                MeasureSpec.EXACTLY
                            ),
                            heightMeasureSpec
                        );
                    }
                };
            } else {
                view = new CategoryButton(getContext());
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (holder.getItemViewType() == VIEW_TYPE_CATEGORY && categories != null) {
                final EmojiCategory category = categories[position - 1];
                final CategoryButton button = (CategoryButton) holder.itemView;
                button.set(category, position - 1, selectedCategoryIndex == position - 1);
                button.setAlpha(categoriesShownT);
                button.setScaleX(categoriesShownT);
                button.setScaleY(categoriesShownT);
                button.play();
            }
        }

        @Override
        public void onViewAttachedToWindow(@NonNull ViewHolder holder) {
            if (holder.getItemViewType() == VIEW_TYPE_CATEGORY) {
                final CategoryButton button = (CategoryButton) holder.itemView;
                final int position = holder.getAdapterPosition();
                button.setSelected(selectedCategoryIndex == position - 1, false);
                button.play();
            }
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? VIEW_TYPE_PADDING : VIEW_TYPE_CATEGORY;
        }

        private int lastItemCount;

        @Override
        public int getItemCount() {
            final int itemCount = 1 + (categories == null ? 0 : categories.length);
            if (itemCount != lastItemCount) {
                if (paddingView != null) {
                    paddingView.requestLayout();
                }
                lastItemCount = itemCount;
            }
            return itemCount;
        }

        @Override
        public boolean isEnabled(ViewHolder holder) {
            return holder.getItemViewType() == VIEW_TYPE_CATEGORY;
        }
    }

    protected boolean isTabIconsAnimationEnabled(boolean loaded) {
        return LiteMode.isEnabled(LiteMode.FLAG_ANIMATED_EMOJI_KEYBOARD) && !loaded;
    }

    static int loadedCategoryIcons = 0;

    private class CategoryButton extends RLottieImageView {

        private int imageColor;
        private float selectedT;
        private ValueAnimator selectedAnimator;
        private int index;

        public CategoryButton(Context context) {
            super(context);

            setImageColor(getThemedColor(Theme.key_chat_emojiPanelIcon));
            setScaleType(ScaleType.CENTER);

            setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), Theme.RIPPLE_MASK_CIRCLE_20DP, dp(15)));

            setLayerNum(layerNum);
        }

        public void set(EmojiCategory category, int index, boolean selected) {
            this.index = index;
            if (loadAnimator != null) {
                loadAnimator.cancel();
                loadAnimator = null;
            }
            if (category.remote) {
                setImageResource(0);
//                cached = true;
                clearAnimationDrawable();
                boolean animated = isTabIconsAnimationEnabled(true);
                loaded = false;
                loadProgress = 1;
                AnimatedEmojiDrawable.getDocumentFetcher(UserConfig.selectedAccount)
                    .fetchDocument(category.documentId, document -> {
                        setOnlyLastFrame(!animated);
                        setAnimation(document, 24, 24);
                        playAnimation();
                    });
                AndroidUtilities.runOnUIThread(() -> {
                    if (!loaded) {
                        loadProgress = 0;
                    }
                }, 60);
            } else if (category.animated) {
                cached = false;
                setImageResource(0);
                setAnimation(category.iconResId, 24, 24);
                playAnimation();
                loadProgress = 1;
            } else {
                clearAnimationDrawable();
                setImageResource(category.iconResId);
                loadProgress = 1;
            }
            setSelected(selected, false);
        }

        private boolean loaded = false;

        @Override
        protected void onLoaded() {
            loaded = true;
            if (loadProgress < 1) {
                if (loadAnimator != null) {
                    loadAnimator.cancel();
                    loadAnimator = null;
                }
                loadAnimator = ValueAnimator.ofFloat(loadProgress, 1f);
                loadAnimator.addUpdateListener(anm -> {
                    loadProgress = (float) anm.getAnimatedValue();
                    invalidate();
                });
                loadAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        loadProgress = 1f;
                        invalidate();
                        loadAnimator = null;
                    }
                });
                loadAnimator.setDuration(320);
                loadAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                loadAnimator.start();
            }
        }

        public void setSelected(boolean selected, boolean animated) {
            if (Math.abs(selectedT - (selected ? 1 : 0)) > .01f) {
                if (selectedAnimator != null) {
                    selectedAnimator.cancel();
                    selectedAnimator = null;
                }

                if (animated) {
                    selectedAnimator = ValueAnimator.ofFloat(selectedT, selected ? 1 : 0);
                    selectedAnimator.addUpdateListener(anm -> {
                        updateSelectedT((float) anm.getAnimatedValue());
                    });
                    selectedAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            updateSelectedT((float) selectedAnimator.getAnimatedValue());
                            selectedAnimator = null;
                        }
                    });
                    selectedAnimator.setDuration(350);
                    selectedAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
                    selectedAnimator.start();
                } else {
                    updateSelectedT(selected ? 1 : 0);
                }
            }
        }

        private void updateSelectedT(float t) {
            selectedT = t;
            setImageColor(
                ColorUtils.blendARGB(
                    getThemedColor(Theme.key_chat_emojiPanelIcon),
                    getThemedColor(Theme.key_chat_emojiPanelIconSelected),
                    selectedT
                )
            );
            invalidate();
        }

        public void setImageColor(int color) {
            if (imageColor != color) {
                setColorFilter(new PorterDuffColorFilter(imageColor = color, PorterDuff.Mode.MULTIPLY));
            }
        }

        @Override
        public void draw(Canvas canvas) {
            updatePressedProgress();
            float scale = getScale();
            if (scale != 1) {
                canvas.save();
                canvas.scale(scale, scale, getMeasuredWidth() / 2f, getMeasuredHeight() / 2f);
            }
            super.draw(canvas);
            if (scale != 1) {
                canvas.restore();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int size = MeasureSpec.getSize(heightMeasureSpec);
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(size - dp(4), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
            );
        }

        private long lastPlayed;
        public void play() {
            if (System.currentTimeMillis() - lastPlayed > 250) {
                lastPlayed = System.currentTimeMillis();
                RLottieDrawable drawable = getAnimatedDrawable();
                if (drawable == null && getImageReceiver() != null) {
                    drawable = getImageReceiver().getLottieAnimation();
                }
                if (drawable != null) {
                    drawable.stop();
                    drawable.setCurrentFrame(0);
                    drawable.restart(true);
                } else if (drawable == null) {
                    setProgress(0);
                    playAnimation();
                }
            }
        }

        float loadProgress = 1f;
        float pressedProgress;
        ValueAnimator backAnimator;
        ValueAnimator loadAnimator;

        public void updatePressedProgress() {
            if (isPressed() && pressedProgress != 1f) {
                pressedProgress = Utilities.clamp(pressedProgress + (1000f / AndroidUtilities.screenRefreshRate) / 100f, 1f, 0);
                invalidate();
                StickerCategoriesListView.this.invalidate();
            }
        }

        public float getScale() {
            return (0.85f + 0.15f * (1f - pressedProgress)) * loadProgress;
        }

        @Override
        public void setPressed(boolean pressed) {
            if (isPressed() != pressed) {
                super.setPressed(pressed);
                invalidate();
                StickerCategoriesListView.this.invalidate();
                if (pressed) {
                    if (backAnimator != null) {
                        backAnimator.removeAllListeners();
                        backAnimator.cancel();
                    }
                }
                if (!pressed && pressedProgress != 0) {
                    backAnimator = ValueAnimator.ofFloat(pressedProgress, 0);
                    backAnimator.addUpdateListener(animation -> {
                        pressedProgress = (float) animation.getAnimatedValue();
                        invalidate();
                    });
                    backAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            backAnimator = null;
                        }
                    });
                    backAnimator.setInterpolator(new OvershootInterpolator(3.0f));
                    backAnimator.setDuration(350);
                    backAnimator.start();
                }
            }
        }
    }

    public static class EmojiCategory {

        public boolean animated;
        public int iconResId;
        public String emojis;

        public boolean remote;
        public long documentId;

        public String title;

        public static EmojiCategory withAnimatedIcon(int animatedIconResId, String emojis) {
            EmojiCategory category = new EmojiCategory();
            category.animated = true;
            category.iconResId = animatedIconResId;
            category.emojis = emojis;
            return category;
        }

        public static EmojiCategory withIcon(int iconResId, String emojis) {
            EmojiCategory category = new EmojiCategory();
            category.animated = false;
            category.iconResId = iconResId;
            category.emojis = emojis;
            return category;
        }

        public static EmojiCategory remote(TLRPC.TL_emojiGroup group) {
            EmojiCategory category = new EmojiCategory();
            category.remote = true;
            category.documentId = group.icon_emoji_id;
            category.emojis = TextUtils.concat(group.emoticons.toArray(new String[0])).toString();
            category.title = group.title;
            return category;
        }
    }

    private static class EmojiGroupFetcher extends Fetcher<Integer, TLRPC.TL_messages_emojiGroups> {

        @Override
        protected void getRemote(int currentAccount, @CategoriesType Integer type, long hash, Utilities.Callback3<Boolean, TLRPC.TL_messages_emojiGroups, Long> onResult) {
            TLObject req;
            if (type == CategoriesType.STATUS) {
                req = new TLRPC.TL_messages_getEmojiStatusGroups();
                ((TLRPC.TL_messages_getEmojiStatusGroups) req).hash = (int) hash;
            } else if (type == CategoriesType.PROFILE_PHOTOS) {
                req = new TLRPC.TL_messages_getEmojiProfilePhotoGroups();
                ((TLRPC.TL_messages_getEmojiProfilePhotoGroups) req).hash = (int) hash;
            } else {
                req = new TLRPC.TL_messages_getEmojiGroups();
                ((TLRPC.TL_messages_getEmojiGroups) req).hash = (int) hash;
            }

            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
                if (res instanceof TLRPC.TL_messages_emojiGroupsNotModified) {
                    onResult.run(true, null, 0L);
                } else if (res instanceof TLRPC.TL_messages_emojiGroups) {
                    TLRPC.TL_messages_emojiGroups result = (TLRPC.TL_messages_emojiGroups) res;
                    onResult.run(false, result, (long) result.hash);
                } else {
                    onResult.run(false, null, 0L);
                }
            });
        }

        @Override
        protected void getLocal(int currentAccount, Integer type, Utilities.Callback2<Long, TLRPC.TL_messages_emojiGroups> onResult) {
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                SQLiteCursor cursor = null;
                try {
                    SQLiteDatabase database = MessagesStorage.getInstance(currentAccount).getDatabase();
                    if (database != null) {
                        TLRPC.messages_EmojiGroups maybeResult = null;
                        cursor = database.queryFinalized("SELECT data FROM emoji_groups WHERE type = ?", type);
                        if (cursor.next()) {
                            NativeByteBuffer data = cursor.byteBufferValue(0);
                            if (data != null) {
                                maybeResult = TLRPC.messages_EmojiGroups.TLdeserialize(data, data.readInt32(false), true);
                                data.reuse();
                            }
                        }

                        if (!(maybeResult instanceof TLRPC.TL_messages_emojiGroups)) {
                            onResult.run(0L, null);
                        } else {
                            TLRPC.TL_messages_emojiGroups result = (TLRPC.TL_messages_emojiGroups) maybeResult;
                            onResult.run((long) result.hash, result);
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                    onResult.run(0L, null);
                } finally {
                    if (cursor != null) {
                        cursor.dispose();
                    }
                }
            });
        }

        @Override
        protected void setLocal(int currentAccount, Integer type, TLRPC.TL_messages_emojiGroups data, long hash) {
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                try {
                    SQLiteDatabase database = MessagesStorage.getInstance(currentAccount).getDatabase();
                    if (database != null) {
                        if (data == null) {
                            database.executeFast("DELETE FROM emoji_groups WHERE type = " + type).stepThis().dispose();
                        } else {
                            SQLitePreparedStatement state = database.executeFast("REPLACE INTO emoji_groups VALUES(?, ?)");
                            state.requery();
                            NativeByteBuffer buffer = new NativeByteBuffer(data.getObjectSize());
                            data.serializeToStream(buffer);
                            state.bindInteger(1, type);
                            state.bindByteBuffer(2, buffer);
                            state.step();
                            buffer.reuse();
                            state.dispose();
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        }
    }

    private static class EmojiSearch extends Fetcher<String, TLRPC.TL_emojiList> {
        @Override
        protected void getRemote(int currentAccount, String query, long hash, Utilities.Callback3<Boolean, TLRPC.TL_emojiList, Long> onResult) {
            TLRPC.TL_messages_searchCustomEmoji req = new TLRPC.TL_messages_searchCustomEmoji();
            req.emoticon = query;
            req.hash = hash;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> {
                if (res instanceof TLRPC.TL_emojiListNotModified) {
                    onResult.run(true, null, 0L);
                } else if (res instanceof TLRPC.TL_emojiList) {
                    TLRPC.TL_emojiList list = (TLRPC.TL_emojiList) res;
                    onResult.run(false, list, list.hash);
                } else {
                    onResult.run(false, null, 0L);
                }
            });
        }
    }
}