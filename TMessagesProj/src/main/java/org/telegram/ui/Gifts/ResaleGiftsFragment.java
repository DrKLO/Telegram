package org.telegram.ui.Gifts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatPluralStringComma;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Stars.StarsController.findAttributes;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.FireworksOverlay;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.Stars.StarGiftSheet;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

public class ResaleGiftsFragment extends BaseFragment {

    private final long dialogId;
    private final long gift_id;
    private final String gift_name;
    private final ResaleGiftsList list;

    public ResaleGiftsFragment(long dialogId, String gift_name, long gift_id, Theme.ResourcesProvider resourcesProvider) {
        this.dialogId = dialogId;
        this.gift_name = gift_name;
        this.gift_id = gift_id;
        this.resourceProvider = resourcesProvider;
        list = new ResaleGiftsList(currentAccount, gift_id, this::updateList);
        list.load();
    }

    private Runnable closeParentSheet;
    public ResaleGiftsFragment setCloseParentSheet(Runnable closeParentSheet) {
        this.closeParentSheet = closeParentSheet;
        return this;
    }

    private BackDrawable backDrawable;
    private HorizontalScrollView filterScrollView;
    private View filtersDivider;
    private LinearLayout filtersContainer;
    private UniversalRecyclerView listView;
    private FrameLayout clearFiltersContainer;
    private TextView clearFiltersButton;
    private LargeEmptyView emptyView;
    private boolean emptyViewVisible;

    private Filter sortButton;
    private Filter modelButton;
    private Filter backdropButton;
    private Filter patternButton;

    private FireworksOverlay fireworksOverlay;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(backDrawable = new BackDrawable(false));
        backDrawable.setAnimationTime(240);
        actionBar.setCastShadows(false);
        actionBar.setAddToContainer(false);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        actionBar.setTitle(gift_name);
        actionBar.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        actionBar.setItemsColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), true);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarActionModeDefaultSelector), false);
        actionBar.setTitleColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setSubtitleColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));

        SizeNotifierFrameLayout fragmentView = new SizeNotifierFrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                LayoutParams lp = (LayoutParams) filterScrollView.getLayoutParams();
                lp.topMargin = ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0);

                lp = (LayoutParams) filtersDivider.getLayoutParams();
                lp.topMargin = ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0) + dp(47);

                lp = (LayoutParams) listView.getLayoutParams();
                lp.topMargin = ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0);

                lp = (LayoutParams) emptyView.getLayoutParams();
                lp.topMargin = ActionBar.getCurrentActionBarHeight() + (actionBar.getOccupyStatusBar() ? AndroidUtilities.statusBarHeight : 0);

                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        final int backgroundColor = Theme.blendOver(
            Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider),
            Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider), 0.04f)
        );
        fragmentView.setBackgroundColor(backgroundColor);
        this.fragmentView = fragmentView;

        StarsIntroActivity.StarsBalanceView balanceView = new StarsIntroActivity.StarsBalanceView(context, currentAccount, resourceProvider);
        ScaleStateListAnimator.apply(balanceView);
        balanceView.setOnClickListener(v -> {
            if (balanceView.lastBalance <= 0) return;
            presentFragment(new StarsIntroActivity());
        });
        actionBar.addView(balanceView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.RIGHT, 0, 0, 4, 0));

        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, this::onItemLongClick) {
            @Override
            public Integer getSelectorColor(int position) {
                return 0;
            }
        };
        listView.adapter.setApplyBackground(false);
        listView.setSpanCount(3);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (isLoadingVisible()) {
                    list.load();
                }
                filtersDivider.animate()
                    .alpha(!filtersShown || !listView.canScrollVertically(-1) ? 0.0f : 1.0f)
                    .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                    .setDuration(320)
                    .start();
            }
        });
        listView.setPadding(0, dp(45), 0, dp(56 + 45));
        listView.setClipToPadding(false);
        fragmentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 7.33f, 0, 7.33f, -45));
        fragmentView.addView(actionBar);

        emptyView = new LargeEmptyView(context, v -> {
            list.notSelectedBackdropAttributes.clear();
            list.notSelectedModelAttributes.clear();
            list.notSelectedPatternAttributes.clear();
            list.reload();
        }, resourceProvider);
        emptyViewVisible = false;
        emptyView.setAlpha(0.0f);
        emptyView.setScaleX(0.95f);
        emptyView.setScaleY(0.95f);
        emptyView.setVisibility(View.GONE);
        fragmentView.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 0, 0, -45));

        filtersContainer = new LinearLayout(context);
        filtersContainer.setPadding(dp(11), 0, dp(11), 0);
        filtersContainer.setOrientation(LinearLayout.HORIZONTAL);

        filterScrollView = new HorizontalScrollView(context);
        filterScrollView.setHorizontalScrollBarEnabled(false);
        filterScrollView.addView(filtersContainer);
        filterScrollView.setBackgroundColor(backgroundColor);
        fragmentView.addView(filterScrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 47, Gravity.TOP | Gravity.FILL_HORIZONTAL));

        filtersDivider = new View(context);
        filtersDivider.setBackgroundColor(getThemedColor(Theme.key_divider));
        filtersDivider.setAlpha(0.0f);
        fragmentView.addView(filtersDivider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 2.0f / AndroidUtilities.density, Gravity.TOP | Gravity.FILL_HORIZONTAL));

        clearFiltersContainer = new FrameLayout(context);
        fragmentView.addView(clearFiltersContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 49, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM));

        clearFiltersButton = new TextView(context);
        SpannableStringBuilder sb = new SpannableStringBuilder("x");
        sb.setSpan(new ColoredImageSpan(R.drawable.msg_clearcache), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(" ").append(getString(R.string.Gift2ResaleFiltersClear));
        clearFiltersButton.setText(sb);
        clearFiltersButton.setTextColor(getThemedColor(Theme.key_featuredStickers_addButton));
        clearFiltersButton.setTypeface(AndroidUtilities.bold());
        clearFiltersButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(0, getThemedColor(Theme.key_windowBackgroundWhite), Theme.blendOver(getThemedColor(Theme.key_windowBackgroundWhite), Theme.multAlpha(getThemedColor(Theme.key_featuredStickers_addButton), 0.10f))));
        clearFiltersButton.setGravity(Gravity.CENTER);
        clearFiltersButton.setOnClickListener(v -> {
            list.notSelectedBackdropAttributes.clear();
            list.notSelectedModelAttributes.clear();
            list.notSelectedPatternAttributes.clear();
            list.reload();
        });
        clearFiltersContainer.addView(clearFiltersButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        View clearFiltersDivider = new View(context);
        clearFiltersDivider.setBackgroundColor(getThemedColor(Theme.key_divider));
        clearFiltersContainer.addView(clearFiltersDivider, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1.0f / AndroidUtilities.density, Gravity.FILL_HORIZONTAL | Gravity.TOP));
        setClearFiltersShown(false, false);



        sortButton = new Filter(context, resourceProvider);
        sortButton.setSorting(list.getSorting());
        filtersContainer.addView(sortButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));
        sortButton.setOnClickListener(v -> {
            if (!filtersShown) return;
            ItemOptions.makeOptions(this, sortButton)
                .add(R.drawable.menu_sort_value, getString(ResaleGiftsList.Sorting.BY_PRICE.buttonStringResId), () -> {
                    list.setSorting(ResaleGiftsList.Sorting.BY_PRICE);
                })
                .add(R.drawable.menu_sort_date, getString(ResaleGiftsList.Sorting.BY_DATE.buttonStringResId), () -> {
                    list.setSorting(ResaleGiftsList.Sorting.BY_DATE);
                })
                .add(R.drawable.menu_sort_number, getString(ResaleGiftsList.Sorting.BY_NUMBER.buttonStringResId), () -> {
                    list.setSorting(ResaleGiftsList.Sorting.BY_NUMBER);
                })
                .setDrawScrim(false)
                .setOnTopOfScrim()
                .translate(0, dp(-8))
                .show();
        });

        modelButton = new Filter(context, resourceProvider);
        modelButton.setValue(getString(R.string.Gift2AttributeModel));
        filtersContainer.addView(modelButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));
        modelButton.setOnClickListener(v -> {
            if (!filtersShown) return;
            if (list.modelAttributes.isEmpty()) return;
            final ItemOptions ie = ItemOptions.makeOptions(this, modelButton, false, true)
                .setDrawScrim(false)
                .setOnTopOfScrim()
                .translate(0, dp(-8))
                .needsFocus();
            ie.setOnDismiss(() -> {
                if (ie.actionBarPopupWindow != null) {
                    AndroidUtilities.hideKeyboard(ie.actionBarPopupWindow.getContentView());
                }
            });

            final String[] query = new String[] { "" };
            final ArrayList<TL_stars.starGiftAttributeModel> attributes = new ArrayList<>(list.modelAttributes);
            Collections.sort(attributes, (a, b) -> {
                final Integer aCount = list.modelAttributesCounter.get(a.document.id);
                final Integer bCount = list.modelAttributesCounter.get(b.document.id);
                if (aCount == null) return 1;
                if (bCount == null) return -1;
                return bCount - aCount;
            });
            final UniversalRecyclerView listView = new UniversalRecyclerView(this, (items, adapter) -> {
                final String q = query[0].toLowerCase(), tq = AndroidUtilities.translitSafe(q);
                final boolean allSelected = list.notSelectedModelAttributes.isEmpty();
                for (TL_stars.starGiftAttributeModel attr : attributes) {
                    final boolean checked = !list.notSelectedModelAttributes.contains(attr.document.id);
                    if (TextUtils.isEmpty(q) || attr.name.toLowerCase().startsWith(q) || attr.name.toLowerCase().startsWith(tq) || attr.name.toLowerCase().contains(" " + q) || attr.name.toLowerCase().contains(" " + tq)) {
                        final Integer counter = list.modelAttributesCounter.get(attr.document.id);
                        items.add(ModelItem.Factory.asModel(attr, counter == null ? 0 : counter, q).setChecked(!TextUtils.isEmpty(q) ? !allSelected && checked : checked));
                    }
                }
                if (items.isEmpty()) {
                    items.add(EmptyView.Factory.asEmptyView(getString(R.string.Gift2ResaleFiltersModelEmpty)));
                }
            }, (item, view, position, x, y) -> {
                final TL_stars.starGiftAttributeModel pattern = (TL_stars.starGiftAttributeModel) item.object;
                final long document_id = pattern.document.id;
                final boolean checked = !list.notSelectedModelAttributes.contains(document_id);
                if (checked) {
                    if (list.notSelectedModelAttributes.isEmpty()) {
                        for (TL_stars.starGiftAttributeModel attr1 : list.modelAttributes) {
                            if (attr1.document.id != document_id) {
                                list.notSelectedModelAttributes.add(attr1.document.id);
                            }
                        }
                    } else {
                        list.notSelectedModelAttributes.add(document_id);
                    }
                } else {
                    list.notSelectedModelAttributes.remove(document_id);
                }
                list.reload();
                ie.dismiss();
            }, null) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                        Math.min((int) (AndroidUtilities.displaySize.y * 0.35f), MeasureSpec.getSize(heightMeasureSpec)),
                        MeasureSpec.getMode(heightMeasureSpec)
                    ));
                }
            };
            listView.adapter.setApplyBackground(false);

            FrameLayout searchLayout = new FrameLayout(context);
            ImageView searchIcon = new ImageView(context);
            searchIcon.setScaleType(ImageView.ScaleType.CENTER);
            searchIcon.setImageResource(R.drawable.smiles_inputsearch);
            searchIcon.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_actionBarDefaultSubmenuItemIcon), PorterDuff.Mode.SRC_IN));
            searchLayout.addView(searchIcon, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | Gravity.LEFT, 10, 0, 0, 0));
            EditTextCaption editText = new EditTextCaption(context, resourceProvider);
            editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            editText.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editText.setRawInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3, resourceProvider));
            editText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            editText.setCursorSize(dp(19));
            editText.setCursorWidth(1.5f);
            editText.setHint(getString(R.string.Gift2ResaleFiltersSearch));
            editText.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourceProvider));
            editText.setBackground(null);
            searchLayout.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 43, 0, 8, 0));
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    query[0] = s.toString();
                    listView.adapter.update(true);
                }
            });
            if (attributes.size() > 8) {
                ie.addView(searchLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
                ie.addGap();
            }
            if (!list.notSelectedModelAttributes.isEmpty()) {
                ie.add(R.drawable.msg_select, getString(R.string.SelectAll), () -> {
                    if (list.notSelectedModelAttributes.isEmpty()) return;
                    list.notSelectedModelAttributes.clear();
                    list.reload();
                });
            }
            ie.addView(listView);
            ie.show();
        });

        backdropButton = new Filter(context, resourceProvider);
        backdropButton.setValue(getString(R.string.Gift2AttributeBackdrop));
        filtersContainer.addView(backdropButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));
        backdropButton.setOnClickListener(v -> {
            if (!filtersShown) return;
            if (list.backdropAttributes.isEmpty()) return;
            final ItemOptions ie = ItemOptions.makeOptions(this, backdropButton, false, true)
                .setDrawScrim(false)
                .setOnTopOfScrim()
                .translate(0, dp(-8))
                .needsFocus();
            ie.setOnDismiss(() -> {
                if (ie.actionBarPopupWindow != null) {
                    AndroidUtilities.hideKeyboard(ie.actionBarPopupWindow.getContentView());
                }
            });

            final String[] query = new String[] { "" };
            final ArrayList<TL_stars.starGiftAttributeBackdrop> attributes = new ArrayList<>(list.backdropAttributes);
            Collections.sort(attributes, (a, b) -> {
                final Integer aCount = list.backdropAttributesCounter.get(a.backdrop_id);
                final Integer bCount = list.backdropAttributesCounter.get(b.backdrop_id);
                if (aCount == null) return 1;
                if (bCount == null) return -1;
                return bCount - aCount;
            });
            final UniversalRecyclerView listView = new UniversalRecyclerView(this, (items, adapter) -> {
                final String q = query[0].toLowerCase(), tq = AndroidUtilities.translitSafe(q);
                final boolean allSelected = list.notSelectedBackdropAttributes.isEmpty();
                for (TL_stars.starGiftAttributeBackdrop attr : attributes) {
                    final boolean checked = !list.notSelectedBackdropAttributes.contains(attr.backdrop_id);
                    if (TextUtils.isEmpty(q) || attr.name.toLowerCase().startsWith(q) || attr.name.toLowerCase().startsWith(tq) || attr.name.toLowerCase().contains(" " + q) || attr.name.toLowerCase().contains(" " + tq)) {
                        final Integer counter = list.backdropAttributesCounter.get(attr.backdrop_id);
                        items.add(BackdropItem.Factory.asBackdrop(attr, counter == null ? 0 : counter, q).setChecked(!TextUtils.isEmpty(q) ? !allSelected && checked : checked));
                    }
                }
                if (items.isEmpty()) {
                    items.add(EmptyView.Factory.asEmptyView(getString(R.string.Gift2ResaleFiltersBackdropEmpty)));
                }
            }, (item, view, position, x, y) -> {
                final TL_stars.starGiftAttributeBackdrop backdrop = (TL_stars.starGiftAttributeBackdrop) item.object;
                final int backdrop_id = backdrop.backdrop_id;
                final boolean checked = !list.notSelectedBackdropAttributes.contains(backdrop_id);
                if (checked) {
                    if (list.notSelectedBackdropAttributes.isEmpty()) {
                        for (TL_stars.starGiftAttributeBackdrop attr1 : list.backdropAttributes) {
                            if (attr1.backdrop_id != backdrop_id) {
                                list.notSelectedBackdropAttributes.add(attr1.backdrop_id);
                            }
                        }
                    } else {
                        list.notSelectedBackdropAttributes.add(backdrop_id);
                    }
                } else {
                    list.notSelectedBackdropAttributes.remove(backdrop_id);
                }
                list.reload();
                ie.dismiss();
            }, null) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                        Math.min((int) (AndroidUtilities.displaySize.y * 0.35f), MeasureSpec.getSize(heightMeasureSpec)),
                        MeasureSpec.getMode(heightMeasureSpec)
                    ));
                }
            };
            listView.adapter.setApplyBackground(false);

            FrameLayout searchLayout = new FrameLayout(context);
            ImageView searchIcon = new ImageView(context);
            searchIcon.setScaleType(ImageView.ScaleType.CENTER);
            searchIcon.setImageResource(R.drawable.smiles_inputsearch);
            searchIcon.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_actionBarDefaultSubmenuItemIcon), PorterDuff.Mode.SRC_IN));
            searchLayout.addView(searchIcon, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | Gravity.LEFT, 10, 0, 0, 0));
            EditTextCaption editText = new EditTextCaption(context, resourceProvider);
            editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            editText.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editText.setRawInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3, resourceProvider));
            editText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            editText.setCursorSize(dp(19));
            editText.setCursorWidth(1.5f);
            editText.setHint(getString(R.string.Gift2ResaleFiltersSearch));
            editText.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourceProvider));
            editText.setBackground(null);
            searchLayout.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 43, 0, 8, 0));
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    query[0] = s.toString();
                    listView.adapter.update(true);
                }
            });
            if (attributes.size() > 8) {
                ie.addView(searchLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
                ie.addGap();
            }
            if (!list.notSelectedBackdropAttributes.isEmpty()) {
                ie.add(R.drawable.msg_select, getString(R.string.SelectAll), () -> {
                    if (list.notSelectedBackdropAttributes.isEmpty()) return;
                    list.notSelectedBackdropAttributes.clear();
                    list.reload();
                });
            }
            ie.addView(listView);
            ie.show();
        });

        patternButton = new Filter(context, resourceProvider);
        patternButton.setValue(getString(R.string.Gift2AttributeSymbol));
        filtersContainer.addView(patternButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 0, 0));
        patternButton.setOnClickListener(v -> {
            if (!filtersShown) return;
            if (list.patternAttributes.isEmpty()) return;
            final ItemOptions ie = ItemOptions.makeOptions(this, patternButton, false, true)
                .setDrawScrim(false)
                .setOnTopOfScrim()
                .translate(0, dp(-8))
                .needsFocus();
            ie.setOnDismiss(() -> {
                if (ie.actionBarPopupWindow != null) {
                    AndroidUtilities.hideKeyboard(ie.actionBarPopupWindow.getContentView());
                }
            });

            final String[] query = new String[] { "" };
            final ArrayList<TL_stars.starGiftAttributePattern> attributes = new ArrayList<>(list.patternAttributes);
            Collections.sort(attributes, (a, b) -> {
                final Integer aCount = list.patternAttributesCounter.get(a.document.id);
                final Integer bCount = list.patternAttributesCounter.get(b.document.id);
                if (aCount == null) return 1;
                if (bCount == null) return -1;
                return bCount - aCount;
            });
            final UniversalRecyclerView listView = new UniversalRecyclerView(this, (items, adapter) -> {
                final String q = query[0].toLowerCase(), tq = AndroidUtilities.translitSafe(q);
                final boolean allSelected = list.notSelectedPatternAttributes.isEmpty();
                for (TL_stars.starGiftAttributePattern attr : attributes) {
                    final boolean checked = !list.notSelectedPatternAttributes.contains(attr.document.id);
                    if (TextUtils.isEmpty(q) || attr.name.toLowerCase().startsWith(q) || attr.name.toLowerCase().startsWith(tq) || attr.name.toLowerCase().contains(" " + q) || attr.name.toLowerCase().contains(" " + tq)) {
                        final Integer counter = list.patternAttributesCounter.get(attr.document.id);
                        items.add(PatternItem.Factory.asPattern(attr, counter == null ? 0 : counter, q).setChecked(!TextUtils.isEmpty(q) ? !allSelected && checked : checked));
                    }
                }
                if (items.isEmpty()) {
                    items.add(EmptyView.Factory.asEmptyView(getString(R.string.Gift2ResaleFiltersSymbolEmpty)));
                }
            }, (item, view, position, x, y) -> {
                final TL_stars.starGiftAttributePattern pattern = (TL_stars.starGiftAttributePattern) item.object;
                final long document_id = pattern.document.id;
                final boolean checked = !list.notSelectedPatternAttributes.contains(document_id);
                if (checked) {
                    if (list.notSelectedPatternAttributes.isEmpty()) {
                        for (TL_stars.starGiftAttributePattern attr1 : list.patternAttributes) {
                            if (attr1.document.id != document_id) {
                                list.notSelectedPatternAttributes.add(attr1.document.id);
                            }
                        }
                    } else {
                        list.notSelectedPatternAttributes.add(document_id);
                    }
                } else {
                    list.notSelectedPatternAttributes.remove(document_id);
                }
                list.reload();
                ie.dismiss();
            }, null) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                        Math.min((int) (AndroidUtilities.displaySize.y * 0.35f), MeasureSpec.getSize(heightMeasureSpec)),
                        MeasureSpec.getMode(heightMeasureSpec)
                    ));
                }
            };
            listView.adapter.setApplyBackground(false);

            FrameLayout searchLayout = new FrameLayout(context);
            ImageView searchIcon = new ImageView(context);
            searchIcon.setScaleType(ImageView.ScaleType.CENTER);
            searchIcon.setImageResource(R.drawable.smiles_inputsearch);
            searchIcon.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_actionBarDefaultSubmenuItemIcon), PorterDuff.Mode.SRC_IN));
            searchLayout.addView(searchIcon, LayoutHelper.createFrame(24, 24, Gravity.CENTER_VERTICAL | Gravity.LEFT, 10, 0, 0, 0));
            EditTextCaption editText = new EditTextCaption(context, resourceProvider);
            editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            editText.setInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editText.setRawInputType(InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3, resourceProvider));
            editText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            editText.setCursorSize(dp(19));
            editText.setCursorWidth(1.5f);
            editText.setHint(getString(R.string.Gift2ResaleFiltersSearch));
            editText.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourceProvider));
            editText.setBackground(null);
            searchLayout.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 43, 0, 8, 0));
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    query[0] = s.toString();
                    listView.adapter.update(true);
                }
            });
            if (attributes.size() > 8) {
                ie.addView(searchLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44));
                ie.addGap();
            }
            if (!list.notSelectedPatternAttributes.isEmpty()) {
                ie.add(R.drawable.msg_select, getString(R.string.SelectAll), () -> {
                    if (list.notSelectedPatternAttributes.isEmpty()) return;
                    list.notSelectedPatternAttributes.clear();
                    list.reload();
                });
            }
            ie.addView(listView);
            ie.show();
        });

        fireworksOverlay = new FireworksOverlay(getContext());
        fragmentView.addView(fireworksOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        setFiltersShown(false, false);

        return fragmentView;
    }

    private boolean filtersShown = true;
    private void setFiltersShown(boolean show, boolean animated) {
        if (filtersShown == show) return;
        filtersShown = show;
        if (animated) {
            filterScrollView.setVisibility(View.VISIBLE);
            filterScrollView.animate()
                .translationY(show ? 0 : -dp(45))
                .alpha(show ? 1.0f : 0.0f)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(420)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!show) {
                            filterScrollView.setVisibility(View.GONE);
                        }
                    }
                })
                .start();
            filtersDivider.animate()
                .translationY(show ? 0 : -dp(45))
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(420)
                .start();
            listView.animate()
                .translationY(show ? 0 : -dp(45 - 6))
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(420)
                .start();
        } else {
            filterScrollView.setVisibility(show ? View.VISIBLE : View.GONE);
            filterScrollView.setTranslationY(show ? 0 : -dp(45));
            filterScrollView.setAlpha(show ? 1.0f : 0.0f);
            filtersDivider.setTranslationY(show ? 0 : -dp(45));
            listView.setTranslationY(show ? 0 : -dp(45 - 6));
        }
    }

    private boolean clearFiltersShown = true;
    private void setClearFiltersShown(boolean show, boolean animated) {
        if (clearFiltersShown == show) return;
        clearFiltersShown = show;
        if (animated) {
            clearFiltersContainer.animate()
                .translationY(show ? 0 : dp(49))
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(420)
                .start();
        } else {
            clearFiltersContainer.setTranslationY(show ? 0 : dp(49));
        }
    }

    private void updateList(boolean first) {
        if (list.getTotalCount() > 12) {
            setFiltersShown(true, true);
        }
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
            if (first) {
                listView.scrollToPosition(0);
            }
        }
        if (actionBar != null) {
            actionBar.setTitle(gift_name);
            actionBar.setSubtitle(list.getTotalCount() <= 0 ? getString(R.string.Gift2ResaleNoCount) : LocaleController.formatPluralStringComma("Gift2ResaleCount", list.getTotalCount()));
        }
        if (sortButton != null) {
            sortButton.setSorting(list.getSorting());
        }
        if (modelButton != null) {
            final int selectedCount = list.modelAttributes.size() - list.notSelectedModelAttributes.size();
            modelButton.setValue(selectedCount <= 0 || selectedCount == list.modelAttributes.size() ? getString(R.string.Gift2ResaleFilterModel) : formatPluralStringComma("Gift2ResaleFilterModels", selectedCount));
        }
        if (backdropButton != null) {
            final int selectedCount = list.backdropAttributes.size() - list.notSelectedBackdropAttributes.size();
            backdropButton.setValue(selectedCount <= 0 || selectedCount == list.backdropAttributes.size() ? getString(R.string.Gift2ResaleFilterBackdrop) : formatPluralStringComma("Gift2ResaleFilterBackdrops", selectedCount));
        }
        if (patternButton != null) {
            final int selectedCount = list.patternAttributes.size() - list.notSelectedPatternAttributes.size();
            patternButton.setValue(selectedCount <= 0 || selectedCount == list.patternAttributes.size() ? getString(R.string.Gift2ResaleFilterSymbol) : formatPluralStringComma("Gift2ResaleFilterSymbols", selectedCount));
        }
        if (isLoadingVisible()) {
            list.load();
        }
        setClearFiltersShown((list.loading || list.getTotalCount() > 0) && (!list.notSelectedModelAttributes.isEmpty() || !list.notSelectedBackdropAttributes.isEmpty() || !list.notSelectedPatternAttributes.isEmpty()), true);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        for (TL_stars.TL_starGiftUnique gift : list.gifts) {
            items.add(GiftSheet.GiftCell.Factory.asStarGift(0, gift, false, false, false, true));
        }
        if (list.loading || !list.endReached) {
            items.add(UItem.asFlicker(-1, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
            items.add(UItem.asFlicker(-2, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
            items.add(UItem.asFlicker(-3, FlickerLoadingView.STAR_GIFT).setSpanCount(1));

            if (list.gifts.isEmpty()) {
                items.add(UItem.asFlicker(-4, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(-5, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(-6, FlickerLoadingView.STAR_GIFT).setSpanCount(1));

                items.add(UItem.asFlicker(-7, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(-8, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(-9, FlickerLoadingView.STAR_GIFT).setSpanCount(1));

                items.add(UItem.asFlicker(-10, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(-11, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(-12, FlickerLoadingView.STAR_GIFT).setSpanCount(1));

                items.add(UItem.asFlicker(-13, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(-14, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                items.add(UItem.asFlicker(-15, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
            }
        }
        updateEmptyView(items.isEmpty() && !list.loading);
    }

    private void updateEmptyView(boolean visible) {
        if (emptyViewVisible == visible) return;
        emptyViewVisible = visible;
        emptyView.setVisibility(View.VISIBLE);
        emptyView.animate()
            .alpha(visible ? 1.0f : 0.0f)
            .scaleX(visible ? 1.0f : 0.95f)
            .scaleY(visible ? 1.0f : 0.95f)
            .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
            .setDuration(320)
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!visible) {
                        emptyView.setVisibility(View.GONE);
                    }
                }
            })
            .start();
    }

    private boolean isLoadingVisible() {
        for (int i = 0; i < listView.getChildCount(); ++i) {
            if (listView.getChildAt(i) instanceof FlickerLoadingView)
                return true;
        }
        return false;
    }


    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.object instanceof TL_stars.TL_starGiftUnique) {
            final TL_stars.TL_starGiftUnique gift = (TL_stars.TL_starGiftUnique) item.object;

            final StarGiftSheet sheet = new StarGiftSheet(getContext(), currentAccount, dialogId, resourceProvider);
            sheet.set(gift.slug, gift, list);
            sheet.setOnBoughtGift((boughtGift, dialogId) -> {
                if (dialogId == UserConfig.getInstance(currentAccount).getClientUserId()) {
                    list.gifts.remove(boughtGift);
                    updateList(false);
                    if (dialogId == UserConfig.getInstance(currentAccount).getClientUserId()) {
                        BulletinFactory.of(this)
                            .createSimpleBulletin(boughtGift.getDocument(), getString(R.string.BoughtResoldGiftTitle), formatString(R.string.BoughtResoldGiftText, boughtGift.title + " #" + LocaleController.formatNumber(boughtGift.num, ',')))
                            .hideAfterBottomSheet(false)
                            .show();
                    } else {
                        BulletinFactory.of(this)
                            .createSimpleBulletin(boughtGift.getDocument(), getString(R.string.BoughtResoldGiftToTitle), formatString(R.string.BoughtResoldGiftToText, DialogObject.getShortName(currentAccount, dialogId)))
                            .hideAfterBottomSheet(false)
                            .show();
                    }
                    fireworksOverlay.start(true);
                } else {
                    Bundle args = new Bundle();
                    if (dialogId >= 0) {
                        args.putLong("user_id", dialogId);
                    } else {
                        args.putLong("chat_id", -dialogId);
                    }
                    ChatActivity chatActivity = new ChatActivity(args) {
                        private boolean shownToast = false;
                        @Override
                        public void onBecomeFullyVisible() {
                            super.onBecomeFullyVisible();
                            if (!shownToast) {
                                shownToast = true;

                                BulletinFactory.of(this)
                                    .createSimpleBulletin(boughtGift.getDocument(), getString(R.string.BoughtResoldGiftToTitle), formatString(R.string.BoughtResoldGiftToText, DialogObject.getShortName(currentAccount, dialogId)))
                                    .hideAfterBottomSheet(false)
                                    .show();
                                if (super.fireworksOverlay != null) {
                                    super.fireworksOverlay.start(true);
                                }
                            }
                        }
                    };
                    if (parentLayout != null && parentLayout.isSheet()) {
                        finishFragment();
                        BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                        if (lastFragment != null) {
                            lastFragment.presentFragment(chatActivity);
                        }
                    } else {
                        presentFragment(chatActivity, true);
                    }

                    if (closeParentSheet != null) {
                        closeParentSheet.run();
                    }
                }
            });
            showDialog(sheet);
        }
    }

    private boolean onItemLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    public static class ResaleGiftsList implements StarsController.IGiftsList {
        private final int account;
        public final long gift_id;
        private final Utilities.Callback<Boolean> onUpdate;

        public final ArrayList<TL_stars.TL_starGiftUnique> gifts = new ArrayList<>();
        private int totalCount;

        public final ArrayList<TL_stars.starGiftAttributeModel> modelAttributes = new ArrayList<>();
        public final ArrayList<TL_stars.starGiftAttributeBackdrop> backdropAttributes = new ArrayList<>();
        public final ArrayList<TL_stars.starGiftAttributePattern> patternAttributes = new ArrayList<>();
        private long attributes_hash;

        public final HashSet<Long> notSelectedModelAttributes       = new HashSet<>();
        public final HashSet<Integer> notSelectedBackdropAttributes = new HashSet<>();
        public final HashSet<Long> notSelectedPatternAttributes     = new HashSet<>();

        public final HashMap<Long, Integer> modelAttributesCounter = new HashMap<>();
        public final HashMap<Integer, Integer> backdropAttributesCounter = new HashMap<>();
        public final HashMap<Long, Integer> patternAttributesCounter = new HashMap<>();

        public enum Sorting {
            BY_PRICE (R.string.ResellGiftFilterSortPriceShort,  R.string.ResellGiftFilterSortPrice),
            BY_DATE  (R.string.ResellGiftFilterSortDateShort,   R.string.ResellGiftFilterSortDate),
            BY_NUMBER(R.string.ResellGiftFilterSortNumberShort, R.string.ResellGiftFilterSortNumber);

            public int shortResId;
            public int buttonStringResId;

            Sorting(int shortResId, int buttonStringResId) {
                this.shortResId = shortResId;
                this.buttonStringResId = buttonStringResId;
            }
        }
        private Sorting sorting = Sorting.BY_PRICE;

        public Sorting getSorting() {
            return sorting;
        }
        public void setSorting(Sorting newSorting) {
            if (sorting != newSorting) {
                sorting = newSorting;
                reload();
            }
        }

        @Override
        public void notifyUpdate() {

        }

        private String last_offset;

        public ResaleGiftsList(int account, long gift_id, Utilities.Callback<Boolean> onUpdate) {
            this.account = account;
            this.gift_id = gift_id;
            this.onUpdate = onUpdate;
        }

        @Override
        public int findGiftToUpgrade(int from) {
            return -1;
        }

        @Override
        public void set(int index, Object obj) {

        }

        public boolean loading;
        public boolean endReached = false;
        private int reqId = -1;
        public void load() {
            load(false);
        }
        public void load(boolean force) {
            if (loading || !force && endReached) return;
            loading = true;
            final TL_stars.getResaleStarGifts req = new TL_stars.getResaleStarGifts();
            req.gift_id = gift_id;
            req.offset = last_offset == null ? "" : last_offset;
            req.limit = 15;
            if (sorting == Sorting.BY_NUMBER) {
                req.sort_by_num = true;
                req.sort_by_price = false;
            } else if (sorting == Sorting.BY_DATE) {
                req.sort_by_num = false;
                req.sort_by_price = false;
            } else if (sorting == Sorting.BY_PRICE) {
                req.sort_by_num = false;
                req.sort_by_price = true;
            }
            if (attributes_hash != 0) {
                req.flags |= 1;
                req.attributes_hash = attributes_hash;
            } else if (modelAttributes.isEmpty() && backdropAttributes.isEmpty() && patternAttributes.isEmpty()) {
                req.flags |= 1;
                req.attributes_hash = 0;
            }
            if (!notSelectedModelAttributes.isEmpty() || !notSelectedBackdropAttributes.isEmpty() || !notSelectedPatternAttributes.isEmpty()) {
                req.flags |= 8;
                if (!notSelectedModelAttributes.isEmpty()) {
                    for (TL_stars.starGiftAttributeModel attr : modelAttributes) {
                        if (!notSelectedModelAttributes.contains(attr.document.id)) {
                            final TL_stars.starGiftAttributeIdModel attrId = new TL_stars.starGiftAttributeIdModel();
                            attrId.document_id = attr.document.id;
                            req.attributes.add(attrId);
                        }
                    }
                }
                if (!notSelectedBackdropAttributes.isEmpty()) {
                    for (TL_stars.starGiftAttributeBackdrop attr : backdropAttributes) {
                        if (!notSelectedBackdropAttributes.contains(attr.backdrop_id)) {
                            final TL_stars.starGiftAttributeIdBackdrop attrId = new TL_stars.starGiftAttributeIdBackdrop();
                            attrId.backdrop_id = attr.backdrop_id;
                            req.attributes.add(attrId);
                        }
                    }
                }
                if (!notSelectedPatternAttributes.isEmpty()) {
                    for (TL_stars.starGiftAttributePattern attr : patternAttributes) {
                        if (!notSelectedPatternAttributes.contains(attr.document.id)) {
                            final TL_stars.starGiftAttributeIdPattern attrId = new TL_stars.starGiftAttributeIdPattern();
                            attrId.document_id = attr.document.id;
                            req.attributes.add(attrId);
                        }
                    }
                }
            }
            reqId = ConnectionsManager.getInstance(account).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                reqId = -1;

                if (res instanceof TL_stars.resaleStarGifts) {
                    final TL_stars.resaleStarGifts r = (TL_stars.resaleStarGifts) res;
                    MessagesController.getInstance(account).putUsers(r.users, false);
                    MessagesController.getInstance(account).putChats(r.chats, false);

                    boolean first = false;
                    totalCount = r.count;
                    if (TextUtils.isEmpty(req.offset)) {
                        first = true;
                        gifts.clear();
                    }
                    for (TL_stars.StarGift starGift : r.gifts) {
                        if (starGift instanceof TL_stars.TL_starGiftUnique) {
                            final TL_stars.TL_starGiftUnique gift = (TL_stars.TL_starGiftUnique) starGift;
                            gifts.add(gift);
                        }
                    }
                    endReached = gifts.size() >= totalCount || TextUtils.isEmpty(r.next_offset);
                    last_offset = r.next_offset;
                    loading = false;
                    if (r.attributes != null && !r.attributes.isEmpty()) {
                        modelAttributes.clear();
                        backdropAttributes.clear();
                        patternAttributes.clear();

                        modelAttributes.addAll(findAttributes(r.attributes, TL_stars.starGiftAttributeModel.class));
                        backdropAttributes.addAll(findAttributes(r.attributes, TL_stars.starGiftAttributeBackdrop.class));
                        patternAttributes.addAll(findAttributes(r.attributes, TL_stars.starGiftAttributePattern.class));
                        attributes_hash = r.attributes_hash;
                    }
                    if (!r.counters.isEmpty()) {
                        backdropAttributesCounter.clear();
                        patternAttributesCounter.clear();
                        modelAttributesCounter.clear();
                        for (final TL_stars.starGiftAttributeCounter counter : r.counters) {
                            if (counter.attribute instanceof TL_stars.starGiftAttributeIdBackdrop) {
                                backdropAttributesCounter.put(counter.attribute.backdrop_id, counter.count);
                            } else if (counter.attribute instanceof TL_stars.starGiftAttributeIdPattern) {
                                patternAttributesCounter.put(counter.attribute.document_id, counter.count);
                            } else if (counter.attribute instanceof TL_stars.starGiftAttributeIdModel) {
                                modelAttributesCounter.put(counter.attribute.document_id, counter.count);
                            }
                        }
                    }

                    if (onUpdate != null) {
                        onUpdate.run(first);
                    }
                }
            }));
        }

        public void cancel() {
            if (reqId >= 0) {
                ConnectionsManager.getInstance(account).cancelRequest(reqId, true);
                reqId = -1;
            }
            loading = false;
        }

        public void reload() {
            cancel();
            last_offset = null;
            gifts.clear();
            load(true);
            if (onUpdate != null) {
                onUpdate.run(true);
            }
        }

        public int getTotalCount() {
            return totalCount;
        }
        public int getLoadedCount() {
            return gifts.size();
        }
        public Object get(int index) {
            return gifts.get(index);
        }
        public int indexOf(Object o) {
            return gifts.indexOf(o);
        }
    }

    @Override
    public boolean isLightStatusBar() {
        if (getLastStoryViewer() != null && getLastStoryViewer().isShown()) {
            return false;
        }
        int color = Theme.getColor(Theme.key_windowBackgroundWhite);
        if (actionBar.isActionModeShowed()) {
            color = Theme.getColor(Theme.key_actionBarActionModeDefault);
        }
        return ColorUtils.calculateLuminance(color) > 0.7f;
    }

    public static class Filter extends TextView {
        private ColoredImageSpan span;
        public Filter(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            final int color = Theme.getColor(Theme.key_actionBarActionModeDefaultIcon, resourcesProvider);
            setTextColor(color);
            setBackground(Theme.createRadSelectorDrawable(Theme.multAlpha(color, 0.08f), Theme.multAlpha(color, 0.15f), dp(13), dp(13)));
            setPadding(dp(11), 0, dp(11), 0);
            setGravity(Gravity.CENTER);
            setTypeface(AndroidUtilities.bold());
            ScaleStateListAnimator.apply(this);

            span = new ColoredImageSpan(R.drawable.arrows_select);
            span.spaceScaleX = 0.8f;
            span.translate(0, dp(1));
        }

        public void setSorting(ResaleGiftsList.Sorting sorting) {
            final SpannableStringBuilder sb = new SpannableStringBuilder("v ");
            ColoredImageSpan imageSpan = null;
            if (sorting == ResaleGiftsList.Sorting.BY_DATE) {
                sb.setSpan(imageSpan = new ColoredImageSpan(R.drawable.mini_gift_sorting_date), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.append(getString(R.string.ResellGiftFilterSortDateShort));
            } else if (sorting == ResaleGiftsList.Sorting.BY_PRICE) {
                sb.setSpan(imageSpan = new ColoredImageSpan(R.drawable.mini_gift_sorting_price), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.append(getString(R.string.ResellGiftFilterSortPriceShort));
            } else if (sorting == ResaleGiftsList.Sorting.BY_NUMBER) {
                sb.setSpan(imageSpan = new ColoredImageSpan(R.drawable.mini_gift_sorting_num), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.append(getString(R.string.ResellGiftFilterSortNumberShort));
            }
            if (imageSpan != null) {
                imageSpan.translate(0, dp(1));
            }
            setText(sb);
        }

        public void setValue(CharSequence text) {
            final SpannableStringBuilder sb = new SpannableStringBuilder(text).append(" v");
            sb.setSpan(span, sb.length() - 1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            setText(sb);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(26), MeasureSpec.EXACTLY));
        }
    }

    public static class EmptyView extends LinearLayout {
        private final BackupImageView imageView;
        private final TextView textView;
        public EmptyView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            setOrientation(VERTICAL);

            imageView = new BackupImageView(context);
            imageView.setImageDrawable(new RLottieDrawable(R.raw.utyan_empty, "utyan_empty", dp(130), dp(130)));
            addView(imageView, LayoutHelper.createLinear(64, 64, Gravity.CENTER, 0, 32, 0, 0));

            textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3, resourcesProvider));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setGravity(Gravity.CENTER);
            addView(textView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.FILL_HORIZONTAL, 12, 12, 12, 24));
        }

        public void set(CharSequence text) {
            textView.setText(text);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {
                width = dp(250);
            }
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                heightMeasureSpec
            );
        }

        public static class Factory extends UItem.UItemFactory<EmptyView> {
            static { setup(new Factory()); }

            @Override
            public EmptyView createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new EmptyView(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((EmptyView) view).set(item.text);
            }

            public static UItem asEmptyView(CharSequence text) {
                UItem item = UItem.ofFactory(EmptyView.Factory.class);
                item.text = text;
                return item;
            }
        }
    }

    public static class LargeEmptyView extends FrameLayout {
        private final LinearLayout layout;
        private final BackupImageView imageView;
        private final TextView titleView;
        private final TextView subtitleView;
        private final TextView buttonView;
        public LargeEmptyView(Context context, View.OnClickListener onClearFiltersButton, Theme.ResourcesProvider resourcesProvider) {
            super(context);

            layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.FILL_HORIZONTAL));

            imageView = new BackupImageView(context);
            imageView.setImageDrawable(new RLottieDrawable(R.raw.utyan_empty, "utyan_empty", dp(130), dp(130)));
            layout.addView(imageView, LayoutHelper.createLinear(130, 130, Gravity.CENTER));

            titleView = new TextView(context);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            titleView.setGravity(Gravity.CENTER);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setText(getString(R.string.Gift2ResaleFiltersEmptyTitle));
            layout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 32, 12, 32, 9));

            subtitleView = new LinkSpanDrawable.LinksTextView(context);
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3, resourcesProvider));
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitleView.setGravity(Gravity.CENTER);
            subtitleView.setText(getString(R.string.Gift2ResaleFiltersEmptySubtitle));
            subtitleView.setMaxWidth(dp(200));
            layout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 32, 0, 32, 12));

            buttonView = new TextView(context);
            buttonView.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
            buttonView.setBackground(Theme.createRadSelectorDrawable(Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), .10f), 6, 6));
            buttonView.setGravity(Gravity.CENTER);
            buttonView.setText(getString(R.string.Gift2ResaleFiltersEmptyClear));
            buttonView.setPadding(dp(13), 0, dp(13), 0);
            ScaleStateListAnimator.apply(buttonView);
            layout.addView(buttonView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 27, Gravity.CENTER, 32, 0, 32, 12));
            buttonView.setOnClickListener(onClearFiltersButton);
        }
    }

    public static class ModelItem extends ActionBarMenuSubItem {
        private final int currentAccount;
        public ModelItem(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
            super(context, false, false, resourcesProvider);
            this.currentAccount = currentAccount;

            setPadding(dp(18), 0, dp(18), 0);
            setColors(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider), Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider));
            setIconColor(0xFFFFFFFF);
            imageView.setTranslationX(dp(2));
            imageView.setScaleX(1.2f);
            imageView.setScaleY(1.2f);
            makeCheckView(2);
            setBackground(null);
            imageView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(@NonNull View v) {
                    if (emojiDrawable != null) {
                        emojiDrawable.addView(imageView);
                    }
                }
                @Override
                public void onViewDetachedFromWindow(@NonNull View v) {
                    if (emojiDrawable != null) {
                        emojiDrawable.removeView(imageView);
                    }
                }
            });
        }

        private long emojiDrawableId;
        private AnimatedEmojiDrawable emojiDrawable;
        public void set(TL_stars.starGiftAttributeModel pattern, int counter, String query, boolean checked) {
            if (emojiDrawable == null || emojiDrawableId != pattern.document.id) {
                emojiDrawableId = pattern.document.id;
                if (emojiDrawable != null) {
                    emojiDrawable.removeView(imageView);
                }
                emojiDrawable = new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW, currentAccount, pattern.document) {
                    @Override
                    public int getIntrinsicHeight() {
                        return dp(24);
                    }
                    @Override
                    public int getIntrinsicWidth() {
                        return dp(24);
                    }
                };
            }
            if (imageView.isAttachedToWindow()) {
                emojiDrawable.addView(imageView);
            }

            CharSequence name = pattern.name;//new SpannableStringBuilder(/*" ").append(*/);
            if (!TextUtils.isEmpty(query)) {
                name = AndroidUtilities.highlightText(name, query, resourcesProvider);
            }
            if (counter > 0) {
                SpannableStringBuilder sb = new SpannableStringBuilder(name);
                sb.append("  ");
                final int fromIndex = sb.length();
                sb.append(Integer.toString(counter));
                sb.setSpan(new TypefaceSpan(AndroidUtilities.bold()), fromIndex, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                name = sb;
            }
            setTextAndIcon(name, 0, emojiDrawable);
            setChecked(checked);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {
                width = dp(250);
            }
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                heightMeasureSpec
            );
        }

        public static class Factory extends UItem.UItemFactory<ModelItem> {
            static { setup(new Factory()); }

            @Override
            public ModelItem createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new ModelItem(context, currentAccount, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((ModelItem) view).set((TL_stars.starGiftAttributeModel) item.object, item.intValue, (String) item.text, item.checked);
            }

            public static UItem asModel(TL_stars.starGiftAttributeModel model, int counter, String query) {
                UItem item = UItem.ofFactory(Factory.class);
                item.object = model;
                item.text = query;
                item.intValue = counter;
                return item;
            }
        }
    }

    public static class PatternItem extends ActionBarMenuSubItem {
        private final int currentAccount;
        public PatternItem(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
            super(context, false, false, resourcesProvider);
            this.currentAccount = currentAccount;

            setPadding(dp(18), 0, dp(18), 0);
            setColors(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider), Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider));
            setIconColor(0xFFFFFFFF);
            imageView.setTranslationX(dp(2));
            makeCheckView(2);
            setBackground(null);
            imageView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(@NonNull View v) {
                    if (emojiDrawable != null) {
                        emojiDrawable.addView(imageView);
                    }
                }
                @Override
                public void onViewDetachedFromWindow(@NonNull View v) {
                    if (emojiDrawable != null) {
                        emojiDrawable.removeView(imageView);
                    }
                }
            });
        }

        private long emojiDrawableId;
        private AnimatedEmojiDrawable emojiDrawable;
        public void set(TL_stars.starGiftAttributePattern pattern, int counter, String query, boolean checked) {
            if (emojiDrawable == null || emojiDrawableId != pattern.document.id) {
                emojiDrawableId = pattern.document.id;
                if (emojiDrawable != null) {
                    emojiDrawable.removeView(imageView);
                }
                emojiDrawable = new AnimatedEmojiDrawable(AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW, currentAccount, pattern.document) {
                    @Override
                    public int getIntrinsicHeight() {
                        return dp(24);
                    }
                    @Override
                    public int getIntrinsicWidth() {
                        return dp(24);
                    }
                };
                emojiDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider), PorterDuff.Mode.SRC_IN));
            }
            if (imageView.isAttachedToWindow()) {
                emojiDrawable.addView(imageView);
            }

            CharSequence name = pattern.name;//new SpannableStringBuilder(/*" ").append(*/);
            if (!TextUtils.isEmpty(query)) {
                name = AndroidUtilities.highlightText(name, query, resourcesProvider);
            }
            if (counter > 0) {
                SpannableStringBuilder sb = new SpannableStringBuilder(name);
                sb.append("  ");
                final int fromIndex = sb.length();
                sb.append(Integer.toString(counter));
                sb.setSpan(new TypefaceSpan(AndroidUtilities.bold()), fromIndex, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                name = sb;
            }
            setTextAndIcon(name, 0, emojiDrawable);
            setChecked(checked);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {
                width = dp(250);
            }
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                heightMeasureSpec
            );
        }

        public static class Factory extends UItem.UItemFactory<PatternItem> {
            static { setup(new Factory()); }

            @Override
            public PatternItem createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new PatternItem(context, currentAccount, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((PatternItem) view).set((TL_stars.starGiftAttributePattern) item.object, item.intValue, (String) item.text, item.checked);
            }

            public static UItem asPattern(TL_stars.starGiftAttributePattern backdrop, int counter, String query) {
                UItem item = UItem.ofFactory(Factory.class);
                item.object = backdrop;
                item.text = query;
                item.intValue = counter;
                return item;
            }
        }
    }

    public static class BackdropItem extends ActionBarMenuSubItem {
        public BackdropItem(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context, false, false, resourcesProvider);
            setPadding(dp(18), 0, dp(18), 0);
            setColors(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider), Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon, resourcesProvider));
            setIconColor(0xFFFFFFFF);
            imageView.setTranslationX(dp(2));
            makeCheckView(2);
            setBackground(null);
        }

        public void set(TL_stars.starGiftAttributeBackdrop backdrop, int counter, String query, boolean checked) {
            final Drawable circle = Theme.createCircleDrawable(dp(20), backdrop.center_color | 0xFF000000);
            CharSequence name = backdrop.name;//new SpannableStringBuilder(/*" ").append(*/);
            if (!TextUtils.isEmpty(query)) {
                name = AndroidUtilities.highlightText(name, query, resourcesProvider);
            }
            if (counter > 0) {
                SpannableStringBuilder sb = new SpannableStringBuilder(name);
                sb.append("  ");
                final int fromIndex = sb.length();
                sb.append(Integer.toString(counter));
                sb.setSpan(new TypefaceSpan(AndroidUtilities.bold()), fromIndex, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                name = sb;
            }
            setTextAndIcon(name, 0, circle);
            setChecked(checked);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {
                width = dp(250);
            }
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                heightMeasureSpec
            );
        }

        public static class Factory extends UItem.UItemFactory<BackdropItem> {
            static { setup(new Factory()); }

            @Override
            public BackdropItem createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new BackdropItem(context, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((BackdropItem) view).set((TL_stars.starGiftAttributeBackdrop) item.object, item.intValue, (String) item.text, item.checked);
            }

            public static UItem asBackdrop(TL_stars.starGiftAttributeBackdrop backdrop, int counter, String query) {
                UItem item = UItem.ofFactory(Factory.class);
                item.object = backdrop;
                item.text = query;
                item.intValue = counter;
                return item;
            }
        }
    }



}
