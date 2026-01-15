package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.transition.ChangeBounds;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.transition.TransitionValues;
import android.transition.Visibility;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationNotificationsLocker;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.FiltersView;

import java.util.ArrayList;

import me.vkryl.android.AnimatorUtils;
import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

@SuppressLint("ViewConstructor")
public class FragmentSearchField extends FrameLayout implements FactorAnimator.Target {
    private static final int ANIMATOR_ID_CLOSE_BUTTON_VISIBLE = 0;
    private static final int ANIMATOR_ID_SEARCH_ICON_VISIBLE = 1;
    private static final int ANIMATOR_ID_SEARCH_FILTERS_WIDTH = 2;

    private final BoolAnimator animatorCloseIconVisible = new BoolAnimator(ANIMATOR_ID_CLOSE_BUTTON_VISIBLE, this, CubicBezierInterpolator.EASE_OUT_QUINT, 380, false);
    private final BoolAnimator animatorSearchIconVisible = new BoolAnimator(ANIMATOR_ID_SEARCH_ICON_VISIBLE, this, CubicBezierInterpolator.EASE_OUT_QUINT, 380, true);
    private final FactorAnimator animatorSearchFiltersWidth = new FactorAnimator(ANIMATOR_ID_SEARCH_FILTERS_WIDTH, this, AnimatorUtils.DECELERATE_INTERPOLATOR, 280);

    private final Theme.ResourcesProvider resourcesProvider;

    private final ImageView searchIcon;
    private final ImageView closeIcon;
    private final LinearLayout additionalIconsLayout;
    private boolean closeButtonForcedVisible;
    public final EditTextBoldCursor editText;

    public FragmentSearchField(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        editText = new EditTextBoldCursor(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                setPivotX(getPaddingLeft());
                setPivotY(getMeasuredHeight() / 2.0f);
            }

            @Override
            public boolean onKeyDown(int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_DEL && editText.length() == 0 && hasRemovableFilters()) {
                    if (hasRemovableFilters()) {
                        FiltersView.MediaFilterData filterToRemove = currentSearchFilters.get(currentSearchFilters.size() - 1);
                        if (searchFiltersListener != null) {
                            searchFiltersListener.onSearchFilterCleared(filterToRemove);
                        }
                        removeSearchFilter(filterToRemove);
                    }
                    return true;
                }
                return super.onKeyDown(keyCode, event);
            }
        };
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        editText.setCursorWidth(1.5f);
        editText.setInputType(InputType.TYPE_TEXT_VARIATION_FILTER | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editText.setSingleLine(true);
        editText.setBackground(null);
        editText.setVerticalScrollBarEnabled(false);
        editText.setHorizontalScrollBarEnabled(false);
        editText.setPadding(dp(48), 0, dp(48), 0);
        editText.setClipToPadding(true);
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        editText.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!currentSearchFilters.isEmpty()) {
                    if (s.length() > 0 && selectedFilterIndex >= 0) {
                        selectedFilterIndex = -1;
                        onFiltersChanged();
                    }
                }
                checkCloseButtonVisible();
            }
        });
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            editText.setLocalePreferredLineHeightForMinimumUsed(false);
        }
        addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 0, 0, 0));

        searchIcon = new ImageView(context);
        searchIcon.setScaleType(ImageView.ScaleType.CENTER);
        searchIcon.setImageResource(R.drawable.outline_search_1_24);
        addView(searchIcon, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 12, 0, 12, 0));

        additionalIconsLayout = new LinearLayout(context);
        additionalIconsLayout.setOrientation(LinearLayout.HORIZONTAL);
        addView(additionalIconsLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), 32, 0, 32, 0));

        closeIcon = new ImageView(context);
        closeIcon.setScaleType(ImageView.ScaleType.CENTER);
        closeIcon.setImageResource(R.drawable.miniplayer_close);
        closeIcon.setVisibility(GONE);
        closeIcon.setOnClickListener(v -> {
            if (hasRemovableFilters()) {
                if (searchFiltersListener != null) {
                    searchFiltersListener.hideActionMode();
                }
                for (int i = 0; i < currentSearchFilters.size(); i++) {
                    if (searchFiltersListener != null && currentSearchFilters.get(i).removable) {
                        searchFiltersListener.onSearchFilterCleared(currentSearchFilters.get(i));
                    }
                }
                clearSearchFilters();
            } else if (onCloseSearch != null) {
                onCloseSearch.run();
            } else {
                editText.getText().clear();
            }
        });
        addView(closeIcon, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), 12, 0, 12, 0));

        searchFilterLayout = new LinearLayout(getContext()) {
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                animatorSearchFiltersWidth.animateTo(getMeasuredWidth());
                super.onLayout(changed, l, t, r, b);
            }
        };
        searchFilterLayout.setOrientation(LinearLayout.HORIZONTAL);
        searchFilterLayout.setVisibility(View.VISIBLE);
        addView(searchFilterLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 32, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 4, 0, 4, 0));

        setWillNotDraw(false);
        checkUi_editTextPaddings();
        updateColors();
    }

    public void addAdditionalIcon(View icon) {
        additionalIconsLayout.addView(icon);
    }

    private float clipHeight = 1.0f;
    public void setClipHeight(float sy) {
        if (Math.abs(clipHeight - sy) < 0.01f)
            return;
        clipHeight = sy;
        invalidate();

        final float scale = lerp(0.75f, 1.0f, clipHeight);
        editText.setScaleX(scale);
        editText.setScaleY(scale);
        searchIcon.setScaleX(scale);
        searchIcon.setScaleY(scale);
        closeIcon.setScaleX(scale);
        closeIcon.setScaleY(scale);
    }

    private Drawable bg;

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        canvas.save();
        if (bg != null) {
            bg.setBounds(0, 0, getWidth(), (int) (getHeight() * clipHeight));
            bg.draw(canvas);
        }
        if (clipHeight < 1) {
            canvas.clipRect(0, 0, getWidth(), getHeight() * clipHeight);
            canvas.translate(0, -getHeight() * (1.0f - clipHeight) / 2.0f);
        }
        super.dispatchDraw(canvas);
        canvas.restore();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        checkUi_editTextPaddings();
    }

    private void checkUi_editTextPaddings() {
        final int filtersWidth = (int) animatorSearchFiltersWidth.getFactor() + dp(6); //searchFilterLayout.getWidth();
        final int pStart = Math.max(filtersWidth, dp(48));
        final int pEnd = dp(48) + additionalIconsLayout.getMeasuredWidth();

        final int pLeft = LocaleController.isRTL ? pEnd : pStart;
        final int pRight = LocaleController.isRTL ? pStart : pEnd;

        AndroidUtilities.rectTmp2.set(
                pLeft, 0,
                editText.getMeasuredWidth() - pRight,
                editText.getMeasuredHeight());
        editText.setClipBounds(AndroidUtilities.rectTmp2);
        editText.setPadding(pLeft, 0, pRight, 0);
    }

    public void updateColors() {
        bg = Theme.createRoundRectDrawable(dp(20), getThemedColor(Theme.key_windowBackgroundWhiteBlackText, 0.05f));
        searchIcon.setColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText, 0.6f), PorterDuff.Mode.MULTIPLY);
        closeIcon.setColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText, 0.6f), PorterDuff.Mode.MULTIPLY);
        closeIcon.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), 1, dp(17)));
        editText.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText, 0.5f));
        editText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setCursorColor(getThemedColor(Theme.key_groupcreate_cursor));

        for (int i = 0, N = additionalIconsLayout.getChildCount(); i < N; i++) {
            final View view = additionalIconsLayout.getChildAt(i);
            if (view instanceof ActionBarMenuItem) {
                final ActionBarMenuItem item = (ActionBarMenuItem) view;
                if (item.getIconView() != null) {
                    item.getIconView().setColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText, 0.6f), PorterDuff.Mode.MULTIPLY);
                }
                view.setBackground(Theme.createSelectorDrawable(getThemedColor(Theme.key_listSelector), 1, dp(17)));
            }
        }

        for (int i = 0, N = searchFilterLayout.getChildCount(); i < N; i++) {
            if (searchFilterLayout.getChildAt(i) instanceof ActionBarMenuItem.SearchFilterView) {
                ((ActionBarMenuItem.SearchFilterView) searchFilterLayout.getChildAt(i)).updateColors();
            }
        }

        invalidate();
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    private int getThemedColor(int key, float alpha) {
        return Theme.multAlpha(getThemedColor(key), alpha);
    }

    private Runnable onCloseSearch;

    public void setCloseButtonOnClickListener(Runnable onCloseSearch) {
        this.onCloseSearch = onCloseSearch;
    }

    public void setCloseButtonVisible(boolean visible) {
        closeButtonForcedVisible = visible;
        checkCloseButtonVisible();
    }

    private void checkCloseButtonVisible() {
        animatorCloseIconVisible.setValue(closeButtonForcedVisible || editText.length() > 0, true);
    }

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_CLOSE_BUTTON_VISIBLE) {
            FragmentFloatingButton.setAnimatedVisibility(closeIcon, factor);
            closeIcon.setRotation((1 - factor) * 90);
        } else if (id == ANIMATOR_ID_SEARCH_ICON_VISIBLE) {
            FragmentFloatingButton.setAnimatedVisibility(searchIcon, factor);
        } else if (id == ANIMATOR_ID_SEARCH_FILTERS_WIDTH) {
            checkUi_editTextPaddings();
        }
    }


    public interface SearchFiltersListener {
        void onSearchFilterCleared(FiltersView.MediaFilterData filterData);
        void hideActionMode();
    }

    private final AnimationNotificationsLocker notificationsLocker = new AnimationNotificationsLocker();
    private final ArrayList<FiltersView.MediaFilterData> currentSearchFilters = new ArrayList<>();
    private final LinearLayout searchFilterLayout;
    private SearchFiltersListener searchFiltersListener;
    private int selectedFilterIndex;

    private boolean hasRemovableFilters() {
        if (currentSearchFilters.isEmpty()) {
            return false;
        }
        for (int i = 0; i < currentSearchFilters.size(); i++) {
            if (currentSearchFilters.get(i).removable) {
                return true;
            }
        }
        return false;
    }

    public void setSearchFiltersListener(SearchFiltersListener searchFiltersListener) {
        this.searchFiltersListener = searchFiltersListener;
    }

    public void addSearchFilter(FiltersView.MediaFilterData filter) {
        currentSearchFilters.add(filter);
        if (true /*searchContainer.getTag() != null*/) {
            selectedFilterIndex = currentSearchFilters.size() - 1;
        }
        onFiltersChanged();
    }

    public void removeSearchFilter(FiltersView.MediaFilterData filter) {
        if (!filter.removable) {
            return;
        }
        currentSearchFilters.remove(filter);
        if (selectedFilterIndex < 0 || selectedFilterIndex > currentSearchFilters.size() - 1) {
            selectedFilterIndex = currentSearchFilters.size() - 1;
        }
        onFiltersChanged();
        if (searchFiltersListener != null) {
            searchFiltersListener.hideActionMode();
        }
    }

    public void clearSearchFiltersWithCallback() {
        if (!currentSearchFilters.isEmpty()) {
            if (searchFiltersListener != null) {
                for (int i = 0; i < currentSearchFilters.size(); i++) {
                    if ( currentSearchFilters.get(i).removable) {
                        searchFiltersListener.onSearchFilterCleared(currentSearchFilters.get(i));
                    }
                }
            }
//                clearSearchFilters();
        }
    }

    public void clearSearchFilters() {
        for (int i = 0; i < currentSearchFilters.size(); i++) {
            if (currentSearchFilters.get(i).removable) {
                currentSearchFilters.remove(i);
                i--;
            }
        }
        onFiltersChanged();
    }

    private void onFiltersChanged() {
        final boolean visible = !currentSearchFilters.isEmpty();

        animatorSearchIconVisible.setValue(!visible, true);


        ArrayList<FiltersView.MediaFilterData> localFilters = new ArrayList<>(currentSearchFilters);

        if (true /*searchContainer != null && searchContainer.getTag() != null*/) {
            TransitionSet transition = new TransitionSet();
            ChangeBounds changeBounds = new ChangeBounds();
            changeBounds.setDuration(150);
            transition.addTransition(new Visibility() {
                @Override
                public Animator onAppear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                    if (view instanceof ActionBarMenuItem.SearchFilterView) {
                        AnimatorSet set = new AnimatorSet();
                        set.playTogether(
                                ObjectAnimator.ofFloat(view, View.ALPHA, 0, 1f),
                                ObjectAnimator.ofFloat(view, View.SCALE_X, 0.5f, 1f),
                                ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.5f, 1f)
                        );
                        set.setInterpolator(CubicBezierInterpolator.DEFAULT);
                        return set;
                    }
                    return ObjectAnimator.ofFloat(view, View.ALPHA, 0, 1f);
                }
                @Override
                public Animator onDisappear(ViewGroup sceneRoot, View view, TransitionValues startValues, TransitionValues endValues) {
                    if (view instanceof ActionBarMenuItem.SearchFilterView) {
                        AnimatorSet set = new AnimatorSet();
                        set.playTogether(
                                ObjectAnimator.ofFloat(view, View.ALPHA, view.getAlpha(), 0f),
                                ObjectAnimator.ofFloat(view, View.SCALE_X,  view.getScaleX(), 0.5f),
                                ObjectAnimator.ofFloat(view, View.SCALE_Y,  view.getScaleX(), 0.5f)
                        );
                        set.setInterpolator(CubicBezierInterpolator.DEFAULT);
                        return set;
                    }
                    return ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0);
                }
            }.setDuration(150)).addTransition(changeBounds);
            transition.setOrdering(TransitionSet.ORDERING_TOGETHER);
            transition.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            transition.addListener(new Transition.TransitionListener() {
                @Override
                public void onTransitionStart(Transition transition) {
                    notificationsLocker.lock();
                }

                @Override
                public void onTransitionEnd(Transition transition) {
                    notificationsLocker.unlock();
                }

                @Override
                public void onTransitionCancel(Transition transition) {
                    notificationsLocker.unlock();
                }

                @Override
                public void onTransitionPause(Transition transition) {

                }

                @Override
                public void onTransitionResume(Transition transition) {

                }
            });
            TransitionManager.beginDelayedTransition(searchFilterLayout, transition);
        }


        for (int i = 0; i < searchFilterLayout.getChildCount(); i++) {
            boolean removed = localFilters.remove(((ActionBarMenuItem.SearchFilterView) searchFilterLayout.getChildAt(i)).getFilter());
            if (!removed) {
                searchFilterLayout.removeViewAt(i);
                i--;
            }
        }

        for (int i = 0; i < localFilters.size(); i++) {
            FiltersView.MediaFilterData filter = localFilters.get(i);
            ActionBarMenuItem.SearchFilterView searchFilterView;
            if (filter.reaction != null) {
                searchFilterView = new ActionBarMenuItem.ReactionFilterView(getContext(), resourcesProvider, true);
            } else {
                searchFilterView = new ActionBarMenuItem.SearchFilterView(getContext(), resourcesProvider, true);
            }

            searchFilterView.setData(filter);
            searchFilterView.setOnClickListener(view -> {
                int index = currentSearchFilters.indexOf(searchFilterView.getFilter());
                if (selectedFilterIndex != index) {
                    selectedFilterIndex = index;
                    onFiltersChanged();
                    return;
                }
                if (searchFilterView.getFilter().removable) {
                    if (!searchFilterView.isSelectedForDelete()) {
                        searchFilterView.setSelectedForDelete(true);
                    } else {
                        FiltersView.MediaFilterData filterToRemove = searchFilterView.getFilter();
                        removeSearchFilter(filterToRemove);


                        if (searchFiltersListener != null) {
                            searchFiltersListener.onSearchFilterCleared(filterToRemove);
                        }
                    }
                }
            });
            searchFilterLayout.addView(searchFilterView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, 0, LocaleController.isRTL ? 6 : 0, 0, LocaleController.isRTL ? 0 : 6, 0));
        }


        for (int i = 0; i < searchFilterLayout.getChildCount(); i++) {
            ((ActionBarMenuItem.SearchFilterView) searchFilterLayout.getChildAt(i)).setExpanded(i == selectedFilterIndex);
        }
        searchFilterLayout.setTag(visible ? 1 : null);
    }
}
