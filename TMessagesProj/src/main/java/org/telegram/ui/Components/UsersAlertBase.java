package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Property;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.GroupCallTextCell;
import org.telegram.ui.Cells.GroupCallUserCell;

public class UsersAlertBase extends BottomSheet {

    protected FrameLayout frameLayout;
    protected RecyclerListView listView;
    protected RecyclerView.Adapter searchListViewAdapter;
    protected RecyclerView.Adapter listViewAdapter;
    protected Drawable shadowDrawable;
    protected View shadow;
    protected AnimatorSet shadowAnimation;
    protected StickerEmptyView emptyView;
    protected FlickerLoadingView flickerLoadingView;
    protected SearchField searchView;

    private RectF rect = new RectF();

    protected int scrollOffsetY;

    private float colorProgress;
    private int backgroundColor;

    protected boolean needSnapToTop = true;
    protected boolean isEmptyViewVisible = true;

    protected String keyScrollUp = Theme.key_sheet_scrollUp;
    protected String keyListSelector = Theme.key_listSelector;
    protected String keySearchBackground = Theme.key_dialogSearchBackground;
    protected String keyInviteMembersBackground = Theme.key_windowBackgroundWhite;
    protected String keyListViewBackground = Theme.key_windowBackgroundWhite;
    protected String keyActionBarUnscrolled = Theme.key_windowBackgroundWhite;
    protected String keyNameText = Theme.key_windowBackgroundWhiteBlackText;
    protected String keyLastSeenText = Theme.key_windowBackgroundWhiteGrayText;
    protected String keyLastSeenTextUnscrolled = Theme.key_windowBackgroundWhiteGrayText;
    protected String keySearchPlaceholder = Theme.key_dialogSearchHint;
    protected String keySearchText = Theme.key_dialogSearchText;
    protected String keySearchIcon = Theme.key_dialogSearchIcon;
    protected String keySearchIconUnscrolled = Theme.key_dialogSearchIcon;
    protected final FillLastLinearLayoutManager layoutManager;


    public UsersAlertBase(Context context, boolean needFocus, int account, Theme.ResourcesProvider resourcesProvider) {
        super(context, needFocus, resourcesProvider);
        updateColorKeys();
        setDimBehindAlpha(75);

        currentAccount = account;

        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();

        containerView = createContainerView(context);
        containerView.setWillNotDraw(false);
        containerView.setClipChildren(false);
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

        frameLayout = new FrameLayout(context);

        searchView = new SearchField(context);
        frameLayout.addView(searchView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        flickerLoadingView = new FlickerLoadingView(context);
        flickerLoadingView.setViewType(FlickerLoadingView.USERS_TYPE);
        flickerLoadingView.showDate(false);
        flickerLoadingView.setUseHeaderOffset(true);
        flickerLoadingView.setColors(keyInviteMembersBackground, keySearchBackground, keyActionBarUnscrolled);

        emptyView = new StickerEmptyView(context, flickerLoadingView, StickerEmptyView.STICKER_TYPE_SEARCH);
        emptyView.addView(flickerLoadingView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 0, 2, 0, 0));
        emptyView.title.setText(LocaleController.getString("NoResult", R.string.NoResult));
        emptyView.subtitle.setText(LocaleController.getString("SearchEmptyViewFilteredSubtitle2", R.string.SearchEmptyViewFilteredSubtitle2));
        emptyView.setVisibility(View.GONE);
        emptyView.setAnimateLayoutChange(true);
        emptyView.showProgress(true, false);
        emptyView.setColors(keyNameText, keyLastSeenText, keyInviteMembersBackground, keySearchBackground);
        containerView.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 58 + 4, 0, 0));

        listView = new RecyclerListView(context) {
            @Override
            protected boolean allowSelectChildAtPosition(float x, float y) {
                return isAllowSelectChildAtPosition(x, y);
            }

            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                int[] ii = new int[2];
                getLocationInWindow(ii);
            }

            @Override
            protected boolean emptyViewIsVisible() {
                if (getAdapter() == null) {
                    return false;
                }
                return isEmptyViewVisible && getAdapter().getItemCount() <= 2;
            }
        };
        listView.setTag(13);
        listView.setPadding(0, 0, 0, AndroidUtilities.dp(48));
        listView.setClipToPadding(false);
        listView.setHideIfEmpty(false);
        listView.setSelectorDrawableColor(Theme.getColor(keyListSelector));
        layoutManager = new FillLastLinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false, AndroidUtilities.dp(8), listView);
        layoutManager.setBind(false);
        listView.setLayoutManager(layoutManager);
        listView.setHorizontalScrollBarEnabled(false);
        listView.setVerticalScrollBarEnabled(false);
        containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                updateLayout();
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE && needSnapToTop) {
                    if (scrollOffsetY + backgroundPaddingTop + AndroidUtilities.dp(13) < AndroidUtilities.statusBarHeight * 2 && listView.canScrollVertically(1)) {
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(0);
                        if (holder != null && holder.itemView.getTop() > 0) {
                             listView.smoothScrollBy(0, holder.itemView.getTop());
                        }
                    }
                }
            }
        });

        FrameLayout.LayoutParams frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.TOP | Gravity.LEFT);
        frameLayoutParams.topMargin = AndroidUtilities.dp(58);
        shadow = new View(context);
        shadow.setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
        shadow.setAlpha(0.0f);
        shadow.setTag(1);
        containerView.addView(shadow, frameLayoutParams);

        containerView.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.LEFT | Gravity.TOP));

        setColorProgress(0.0f);

        listView.setEmptyView(emptyView);
        listView.setAnimateEmptyView(true, 0);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        AndroidUtilities.statusBarHeight = AndroidUtilities.getStatusBarHeight(getContext());
    }

    protected ContainerView createContainerView(Context context) {
        return new ContainerView(context);
    }

    protected boolean isAllowSelectChildAtPosition(float x, float y) {
        return y >= AndroidUtilities.dp(58) + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
    }

    protected void updateColorKeys() {

    }

    @SuppressWarnings("FieldCanBeLocal")
    protected class SearchField extends FrameLayout {

        private final View searchBackground;
        private final ImageView searchIconImageView;
        private final ImageView clearSearchImageView;
        private final CloseProgressDrawable2 progressDrawable;
        protected EditTextBoldCursor searchEditText;

        public SearchField(Context context) {
            super(context);

            searchBackground = new View(context);
            searchBackground.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), Theme.getColor(keySearchBackground)));
            addView(searchBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 14, 11, 14, 0));

            searchIconImageView = new ImageView(context);
            searchIconImageView.setScaleType(ImageView.ScaleType.CENTER);
            searchIconImageView.setImageResource(R.drawable.smiles_inputsearch);
            searchIconImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(keySearchPlaceholder), PorterDuff.Mode.MULTIPLY));
            addView(searchIconImageView, LayoutHelper.createFrame(36, 36, Gravity.LEFT | Gravity.TOP, 16, 11, 0, 0));

            clearSearchImageView = new ImageView(context);
            clearSearchImageView.setScaleType(ImageView.ScaleType.CENTER);
            clearSearchImageView.setImageDrawable(progressDrawable = new CloseProgressDrawable2());
            progressDrawable.setSide(AndroidUtilities.dp(7));
            clearSearchImageView.setScaleX(0.1f);
            clearSearchImageView.setScaleY(0.1f);
            clearSearchImageView.setAlpha(0.0f);
            clearSearchImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(keySearchPlaceholder), PorterDuff.Mode.MULTIPLY));
            addView(clearSearchImageView, LayoutHelper.createFrame(36, 36, Gravity.RIGHT | Gravity.TOP, 14, 11, 14, 0));
            clearSearchImageView.setOnClickListener(v -> {
                searchEditText.setText("");
                AndroidUtilities.showKeyboard(searchEditText);
            });

            searchEditText = new EditTextBoldCursor(context) {
                @Override
                public boolean dispatchTouchEvent(MotionEvent event) {
                    MotionEvent e = MotionEvent.obtain(event);
                    e.setLocation(e.getRawX(), e.getRawY() - containerView.getTranslationY());
                    if (e.getAction() == MotionEvent.ACTION_UP) {
                        e.setAction(MotionEvent.ACTION_CANCEL);
                    }
                    listView.dispatchTouchEvent(e);
                    e.recycle();
                    return super.dispatchTouchEvent(event);
                }
            };
            searchEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            searchEditText.setHintTextColor(Theme.getColor(keySearchPlaceholder));
            searchEditText.setTextColor(Theme.getColor(keySearchText));
            searchEditText.setBackgroundDrawable(null);
            searchEditText.setPadding(0, 0, 0, 0);
            searchEditText.setMaxLines(1);
            searchEditText.setLines(1);
            searchEditText.setSingleLine(true);
            searchEditText.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            searchEditText.setHint(LocaleController.getString("VoipGroupSearchMembers", R.string.VoipGroupSearchMembers));
            searchEditText.setCursorColor(Theme.getColor(keySearchText));
            searchEditText.setCursorSize(AndroidUtilities.dp(20));
            searchEditText.setCursorWidth(1.5f);
            addView(searchEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.LEFT | Gravity.TOP, 16 + 38, 9, 16 + 30, 0));
            searchEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    boolean show = searchEditText.length() > 0;
                    boolean showed = clearSearchImageView.getAlpha() != 0;
                    if (show != showed) {
                        clearSearchImageView.animate()
                                .alpha(show ? 1.0f : 0.0f)
                                .setDuration(150)
                                .scaleX(show ? 1.0f : 0.1f)
                                .scaleY(show ? 1.0f : 0.1f)
                                .start();
                    }
                    String text = searchEditText.getText().toString();
                    int oldItemsCount = listView.getAdapter() == null ? 0 : listView.getAdapter().getItemCount();
                    search(text);
                    if (TextUtils.isEmpty(text) && listView != null && listView.getAdapter() != listViewAdapter) {
                        listView.setAnimateEmptyView(false, 0);
                        listView.setAdapter(listViewAdapter);
                        listView.setAnimateEmptyView(true, 0);
                        if (oldItemsCount == 0) {
                            showItemsAnimated(0);
                        }
                    }
                    flickerLoadingView.setVisibility(View.VISIBLE);
                }
            });
            searchEditText.setOnEditorActionListener((v, actionId, event) -> {
                if (event != null && (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_SEARCH || event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    AndroidUtilities.hideKeyboard(searchEditText);
                }
                return false;
            });
        }

        public void hideKeyboard() {
            AndroidUtilities.hideKeyboard(searchEditText);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            onSearchViewTouched(ev, searchEditText);
            return super.onInterceptTouchEvent(ev);
        }

        public void closeSearch() {
            clearSearchImageView.callOnClick();
            AndroidUtilities.hideKeyboard(searchEditText);
        }
    }

    protected void onSearchViewTouched(MotionEvent ev, EditTextBoldCursor searchEditText) {

    }

    protected void search(String text) {

    }

    public static final Property<UsersAlertBase, Float> COLOR_PROGRESS = new AnimationProperties.FloatProperty<UsersAlertBase>("colorProgress") {
        @Override
        public void setValue(UsersAlertBase object, float value) {
            object.setColorProgress(value);
        }

        @Override
        public Float get(UsersAlertBase object) {
            return object.getColorProgress();
        }
    };

    private float getColorProgress() {
        return colorProgress;
    }

    protected void setColorProgress(float progress) {
        colorProgress = progress;
        backgroundColor = AndroidUtilities.getOffsetColor(Theme.getColor(keyInviteMembersBackground), Theme.getColor(keyListViewBackground), progress, 1.0f);
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(backgroundColor, PorterDuff.Mode.MULTIPLY));
        frameLayout.setBackgroundColor(backgroundColor);
        navBarColor = backgroundColor;
        listView.setGlowColor(backgroundColor);

        int color = AndroidUtilities.getOffsetColor(Theme.getColor(keyLastSeenTextUnscrolled), Theme.getColor(keyLastSeenText), progress, 1.0f);
        int color2 = AndroidUtilities.getOffsetColor(Theme.getColor(keySearchIconUnscrolled), Theme.getColor(keySearchIcon), progress, 1.0f);//
        for (int a = 0, N = listView.getChildCount(); a < N; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof GroupCallTextCell) {
                GroupCallTextCell cell = (GroupCallTextCell) child;
                cell.setColors(color, color);
            } else if (child instanceof GroupCallUserCell) {
                GroupCallUserCell cell = (GroupCallUserCell) child;
                cell.setGrayIconColor(shadow.getTag() != null ? keySearchIcon : keySearchIconUnscrolled, color2);
            }
        }
        containerView.invalidate();
        listView.invalidate();
        container.invalidate();
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    @Override
    public void dismiss() {
        AndroidUtilities.hideKeyboard(searchView.searchEditText);
        super.dismiss();
    }

    @SuppressLint("NewApi")
    protected void updateLayout() {
        if (listView.getChildCount() <= 0) {
            return;
        }

        RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(0);
        int top;
        if (holder != null) {
            top = holder.itemView.getTop() - AndroidUtilities.dp(8);
        } else {
            top = 0;
        }
        int newOffset = top > 0 && holder != null && holder.getAdapterPosition() == 0 ? top : 0;
        if (top >= 0 && holder != null && holder.getAdapterPosition() == 0) {
            newOffset = top;
            runShadowAnimation(false);
        } else {
            runShadowAnimation(true);
        }
        if (scrollOffsetY != newOffset) {
            scrollOffsetY = newOffset;
            setTranslationY(newOffset);
        }
    }

    protected void setTranslationY(int newOffset) {
        listView.setTopGlowOffset(newOffset);
        frameLayout.setTranslationY(newOffset);
        emptyView.setTranslationY(newOffset);
        containerView.invalidate();
    }

    private void runShadowAnimation(final boolean show) {
        if (show && shadow.getTag() != null || !show && shadow.getTag() == null) {
            shadow.setTag(show ? null : 1);
            if (show) {
                shadow.setVisibility(View.VISIBLE);
            }
            if (shadowAnimation != null) {
                shadowAnimation.cancel();
            }
            shadowAnimation = new AnimatorSet();
            shadowAnimation.playTogether(ObjectAnimator.ofFloat(shadow, View.ALPHA, show ? 1.0f : 0.0f));
            shadowAnimation.setDuration(150);
            shadowAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (shadowAnimation != null && shadowAnimation.equals(animation)) {
                        if (!show) {
                            shadow.setVisibility(View.INVISIBLE);
                        }
                        shadowAnimation = null;
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (shadowAnimation != null && shadowAnimation.equals(animation)) {
                        shadowAnimation = null;
                    }
                }
            });
            shadowAnimation.start();
        }
    }

    protected void showItemsAnimated(int from) {
        if (!isShowing()) {
            return;
        }
        listView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                listView.getViewTreeObserver().removeOnPreDrawListener(this);
                int n = listView.getChildCount();
                AnimatorSet animatorSet = new AnimatorSet();
                for (int i = 0; i < n; i++) {
                    View child = listView.getChildAt(i);
                    int position = listView.getChildAdapterPosition(child);
                    if (position < from) {
                        continue;
                    }
                    if (position == 1 && listView.getAdapter() == searchListViewAdapter && child instanceof GraySectionCell) {
                        child = ((GraySectionCell) child).getTextView();
                    }
                    child.setAlpha(0);
                    int s = Math.min(listView.getMeasuredHeight(), Math.max(0, child.getTop()));
                    int delay = (int) ((s / (float) listView.getMeasuredHeight()) * 100);
                    ObjectAnimator a = ObjectAnimator.ofFloat(child, View.ALPHA, 0, 1f);
                    a.setStartDelay(delay);
                    a.setDuration(200);
                    animatorSet.playTogether(a);
                }
                animatorSet.start();
                return true;
            }
        });
    }

    protected class ContainerView extends FrameLayout {

        ValueAnimator valueAnimator;
        float snapToTopOffset;

        public ContainerView(@NonNull Context context) {
            super(context);
        }

        private boolean ignoreLayout = false;

        @Override
        public void setTranslationY(float translationY) {
            super.setTranslationY(translationY);
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int totalHeight = View.MeasureSpec.getSize(heightMeasureSpec);

            if (Build.VERSION.SDK_INT >= 21) {
                ignoreLayout = true;
                setPadding(backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaddingLeft, 0);
                ignoreLayout = false;
            }
            int availableHeight = totalHeight - getPaddingTop();
            int padding;
            if (keyboardVisible) {
                padding = AndroidUtilities.dp(8);
                setAllowNestedScroll(false);
                if (scrollOffsetY != 0) {
                    snapToTopOffset = scrollOffsetY;
                    setTranslationY(snapToTopOffset);
                    if (valueAnimator != null) {
                        valueAnimator.removeAllListeners();
                        valueAnimator.cancel();
                    }
                    valueAnimator = ValueAnimator.ofFloat(snapToTopOffset, 0);
                    valueAnimator.addUpdateListener(valueAnimator -> {
                        snapToTopOffset = (float) valueAnimator.getAnimatedValue();
                        setTranslationY(snapToTopOffset);
                    });
                    valueAnimator.setDuration(AdjustPanLayoutHelper.keyboardDuration);
                    valueAnimator.setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator);
                    valueAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            snapToTopOffset = 0;
                            setTranslationY(0);
                            valueAnimator = null;
                        }
                    });
                    valueAnimator.start();
                } else if (valueAnimator != null) {
                    setTranslationY(snapToTopOffset);
                }
            } else {
                padding = availableHeight - (availableHeight / 5 * 3) + AndroidUtilities.dp(8);
                setAllowNestedScroll(true);
            }
            if (listView.getPaddingTop() != padding) {
                ignoreLayout = true;
                listView.setPadding(0, padding, 0, 0);
                ignoreLayout = false;
            }
            super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(totalHeight, View.MeasureSpec.EXACTLY));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
            updateLayout();
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getY() < scrollOffsetY) {
                dismiss();
                return true;
            }
            return super.onInterceptTouchEvent(ev);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            return !isDismissed() && super.onTouchEvent(e);
        }

        @Override
        public void requestLayout() {
            if (ignoreLayout) {
                return;
            }
            super.requestLayout();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.save();
            int y = scrollOffsetY - backgroundPaddingTop + AndroidUtilities.dp(6);
            int top = scrollOffsetY - backgroundPaddingTop - AndroidUtilities.dp(13);
            int height = getMeasuredHeight() + AndroidUtilities.dp(50) + backgroundPaddingTop;
            int statusBarHeight = 0;
            float radProgress = 1.0f;
            if (Build.VERSION.SDK_INT >= 21) {
                top += AndroidUtilities.statusBarHeight;
                y += AndroidUtilities.statusBarHeight;
                height -= AndroidUtilities.statusBarHeight;

                if (top + backgroundPaddingTop + getTranslationY() < AndroidUtilities.statusBarHeight * 2) {
                    int diff = (int) Math.min(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight * 2 - top - backgroundPaddingTop - getTranslationY());
                    top -= diff;
                    height += diff;
                    radProgress = 1.0f - Math.min(1.0f, (diff * 2) / (float) AndroidUtilities.statusBarHeight);
                }
                if (top + backgroundPaddingTop + getTranslationY() < AndroidUtilities.statusBarHeight) {
                    statusBarHeight = (int) Math.min(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight - top - backgroundPaddingTop - getTranslationY());
                }
            }

            shadowDrawable.setBounds(0, top, getMeasuredWidth(), height);
            shadowDrawable.draw(canvas);

            if (radProgress != 1.0f) {
                Theme.dialogs_onlineCirclePaint.setColor(backgroundColor);
                rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + AndroidUtilities.dp(24));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(12) * radProgress, AndroidUtilities.dp(12) * radProgress, Theme.dialogs_onlineCirclePaint);
            }

            int w = AndroidUtilities.dp(36);
            rect.set((getMeasuredWidth() - w) / 2, y, (getMeasuredWidth() + w) / 2, y + AndroidUtilities.dp(4));
            Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(keyScrollUp));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), Theme.dialogs_onlineCirclePaint);

            if (statusBarHeight > 0) {
                int finalColor = Color.argb(0xff, (int) (Color.red(backgroundColor) * 0.8f), (int) (Color.green(backgroundColor) * 0.8f), (int) (Color.blue(backgroundColor) * 0.8f));
                Theme.dialogs_onlineCirclePaint.setColor(finalColor);
                canvas.drawRect(backgroundPaddingLeft, AndroidUtilities.statusBarHeight - statusBarHeight - getTranslationY(), getMeasuredWidth() - backgroundPaddingLeft, AndroidUtilities.statusBarHeight - getTranslationY(), Theme.dialogs_onlineCirclePaint);
            }
            canvas.restore();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            canvas.save();
            canvas.clipRect(0, getPaddingTop(), getMeasuredWidth(), getMeasuredHeight());
            super.dispatchDraw(canvas);
            canvas.restore();
        }

    }
}
