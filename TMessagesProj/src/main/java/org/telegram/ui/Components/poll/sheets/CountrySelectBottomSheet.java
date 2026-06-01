package org.telegram.ui.Components.poll.sheets;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.replaceTags;
import static org.telegram.messenger.AndroidUtilities.translitSafe;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.Editable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.utils.GradientProtectionDrawable;
import org.telegram.messenger.utils.TextWatcherImpl;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FragmentFloatingButton;
import org.telegram.ui.Components.FragmentSearchField;
import org.telegram.ui.Components.FragmentSpansContainer;
import org.telegram.ui.Components.GroupCreateSpan;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.boosts.BoostRepository;
import org.telegram.ui.Components.Premium.boosts.SelectorBottomSheet;
import org.telegram.ui.Components.Premium.boosts.cells.selector.SelectorCountryCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;

public class CountrySelectBottomSheet extends BottomSheetWithRecyclerListView implements FactorAnimator.Target {
    private static final int ANIMATOR_ID_SELECTED_CONTAINER_HEIGHT = 3;
    private static final int ANIMATOR_ID_TOP_SAVE_BUTTON_VISIBILITY = 4;

    private final FactorAnimator animatorSelectorContainerHeight = new FactorAnimator(ANIMATOR_ID_SELECTED_CONTAINER_HEIGHT,
            this, CubicBezierInterpolator.EASE_OUT_QUINT, 350);

    private final BoolAnimator animatorTopSaveButtonVisibility = new BoolAnimator(
        ANIMATOR_ID_TOP_SAVE_BUTTON_VISIBILITY, this, CubicBezierInterpolator.EASE_OUT_QUINT, 320);

    public interface Listener {
        void onCountrySelected(List<String> countries);
    }

    private static final int BOTTOM_HEIGHT_DP = 60;

    private final Map<String, List<TLRPC.TL_help_country>> countriesMap = new HashMap<>();
    private final List<String> countriesLetters = new ArrayList<>();
    private final List<TLRPC.TL_help_country> countriesList = new ArrayList<>();

    private String query;
    private UniversalAdapter adapter;
    private final FrameLayout buttonContainer;
    private final ButtonWithCounterView button;
    private final TextView doneItem;
    private final FrameLayout searchContainer;
    private final FragmentSearchField searchField;
    private final FragmentSpansContainer spansContainer;
    private final GraySectionCell graySectionCell;
    private final HashMap<String, GroupCreateSpan> selectedCountries = new HashMap<>();
    private Listener listener;
    private int selectedCountriesHeight;
    private final int maxCountriesCount;
    private final FrameLayout bulletinContainer;

    public CountrySelectBottomSheet(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, null, true, true, false, false, false, ActionBarType.SLIDING, resourcesProvider);
        occupyNavigationBar = true;
        drawNavigationBar = false;
        ignoreTouchActionBar = false;
        showShadow = false;

        maxCountriesCount = MessagesController.getInstance(currentAccount).config.pollCountriesMax.get();

        AndroidUtilities.enableEdgeToEdge(getWindow());
        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, AndroidUtilities.navigationBarHeight + dp(68));
        recyclerListView.setClipToPadding(false);
        recyclerListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                checkUi_searchFieldY();
            }
        });
        recyclerListView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (position == 0) {
                    return;
                }

                final TLRPC.TL_help_country country = (TLRPC.TL_help_country) adapter.getItem(position - 1).object;
                if (country == null) {
                    return;
                }

                final boolean checked;
                if (selectedCountries.containsKey(country.iso2)) {
                    final GroupCreateSpan span = selectedCountries.remove(country.iso2);
                    spansContainer.removeSpan(span);
                    checked = false;
                } else {
                    if (selectedCountries.size() >= maxCountriesCount) {
                        BulletinFactory.of(bulletinContainer, resourcesProvider).createSimpleBulletin(R.raw.info,
                            replaceTags(formatString(R.string.PollV2YouCanAddXCountriesOnly, maxCountriesCount))).show();
                        return;
                    }
                    final GroupCreateSpan span = new GroupCreateSpan(context, country);
                    span.setOnClickListener(CountrySelectBottomSheet.this::onSpanClick);
                    spansContainer.addSpan(span);
                    selectedCountries.put(country.iso2, span);
                    checked = true;
                }
                if (view instanceof SelectorCountryCell) {
                    ((SelectorCountryCell) view).setChecked(checked, true);
                }

                adapter.update(true);
                checkUi_buttonCounter();
            }
        });

        button = new ButtonWithCounterView(context, resourcesProvider);
        button.setRound();
        button.setCountFilled(true);
        button.setText(LocaleController.getString(R.string.Save));
        button.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCountrySelected(new ArrayList<>(selectedCountries.keySet()));
            }
            dismiss();
        });

        doneItem = new TextView(context) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(Canvas canvas) {
                p.setColor(getThemedColor(Theme.key_featuredStickers_addButton));
                canvas.drawRoundRect(0, getHeight() / 2f - dp(14),
                        getWidth(), getHeight() / 2f + dp(14), dp(14), dp(14), p);
                super.onDraw(canvas);
            }
        };
        doneItem.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
        doneItem.setText(getString(R.string.Save));
        doneItem.setTypeface(AndroidUtilities.bold());
        doneItem.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        doneItem.setGravity(Gravity.CENTER);
        doneItem.setPadding(dp(16), 0, dp(16), 0);
        doneItem.setVisibility(View.GONE);

        ScaleStateListAnimator.apply(doneItem);
        actionBar.createMenu().addView(doneItem, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 48, Gravity.CENTER_VERTICAL, 12, 0, 12, 0));
        doneItem.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCountrySelected(new ArrayList<>(selectedCountries.keySet()));
            }
            dismiss();
        });

        searchField = new FragmentSearchField(context, resourcesProvider);
        searchField.editText.setHint(LocaleController.getString(R.string.PollV2SearchHint));
        searchField.editText.addTextChangedListener(new TextWatcherImpl() {
            @Override
            public void afterTextChanged(Editable s) {
                saveScrollPosition();
                query = s.toString();
                adapter.update(true);
            }
        });

        spansContainer = new FragmentSpansContainer(context, currentAccount);
        spansContainer.setDelegate(height -> {
            int h = Math.min(height, dp(144));
            if (height > 0) {
                h -= dp(8);
            }
            if (selectedCountriesHeight != h) {
                selectedCountriesHeight = h;
                animatorSelectorContainerHeight.animateTo(h);
                spansContainer.postOnAnimation(() -> adapter.update(true));
            }
        });

        searchContainer = new FrameLayout(context) {
            final GradientProtectionDrawable gradientProtectionDrawableTop = new GradientProtectionDrawable(WindowInsetsCompat.Side.TOP);
            final GradientProtectionDrawable gradientProtectionDrawableBottom = new GradientProtectionDrawable(WindowInsetsCompat.Side.BOTTOM);

            @Override
            protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
                final boolean r = super.drawChild(canvas, child, drawingTime);
                final int f = (int) animatorSelectorContainerHeight.getFactor();

                if (child == spansContainer && f > 0) {
                    gradientProtectionDrawableTop.setBounds(0, dp(40), getWidth(), dp(48));
                    gradientProtectionDrawableTop.setColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
                    gradientProtectionDrawableTop.draw(canvas);

                    final int t = dp(48) + f;
                    gradientProtectionDrawableBottom.setBounds(0, t - dp(8), getWidth(), t);
                    gradientProtectionDrawableBottom.setColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
                    gradientProtectionDrawableBottom.draw(canvas);
                }
                return r;
            }
        };
        searchContainer.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
        searchContainer.addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.TOP, 10, 0, 10, 0));
        searchContainer.addView(spansContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 144, Gravity.TOP, -3, 40, -3, 0));

        graySectionCell = new GraySectionCell(context, 18, resourcesProvider);
        graySectionCell.setTranslationY(dp(48));
        graySectionCell.setText(LocaleController.getString(R.string.SearchCountriesTitle), LocaleController.getString(R.string.DeselectAll), v -> {
            selectedCountries.clear();
            spansContainer.removeAllSpans(true);
            adapter.update(true);
            checkUi_buttonCounter();
        });
        
        searchContainer.addView(graySectionCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32, Gravity.TOP));
        containerView.addView(searchContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40 + 144 + 32, Gravity.TOP));

        buttonContainer = new FrameLayout(context);
        buttonContainer.setPadding(backgroundPaddingLeft + dp(10), dp(10), backgroundPaddingLeft + dp(10), AndroidUtilities.navigationBarHeight + dp(10));
        buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
        containerView.addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        bulletinContainer = new FrameLayout(context);
        bulletinContainer.setTranslationY(-AndroidUtilities.navigationBarHeight - dp(68));
        containerView.addView(bulletinContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 150, Gravity.BOTTOM));

        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setDurations(350);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setSupportsChangeAnimations(false);
        recyclerListView.setItemAnimator(itemAnimator);
        recyclerListView.addItemDecoration(new RecyclerView.ItemDecoration() {
            final GradientProtectionDrawable gradientProtectionDrawable = new GradientProtectionDrawable(WindowInsetsCompat.Side.TOP);

            @Override
            public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                final int t = Math.max(0, (int) searchContainer.getTranslationY() + dp(40 + 8 + 32) + (int)animatorSelectorContainerHeight.getFactor());
                gradientProtectionDrawable.setColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
                gradientProtectionDrawable.setBounds(0, t, parent.getWidth(), t + dp(8));
                gradientProtectionDrawable.draw(c);

                checkUi_listViewClip();
                checkUi_searchFieldY();
            }
        });

        loadCountries();
        ViewCompat.setOnApplyWindowInsetsListener(getContainer(), this::onApplyWindowInsets);
    }

    public Set<String> getSelectedCountries() {
        return selectedCountries.keySet();
    }

    @NonNull
    private WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat insets) {
        processLegacyContainerInsets(insets.toWindowInsets());
        animatorTopSaveButtonVisibility.setValue(insets.getInsets(WindowInsetsCompat.Type.ime()).bottom > 0, true);
        return WindowInsetsCompat.CONSUMED;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    protected void onContainerLayout(int l, int t, int r, int b) {
        super.onContainerLayout(l, t, r, b);
        checkUi_listViewClip();
        checkUi_searchFieldY();
    }

    private void loadCountries() {
        BoostRepository.loadCountriesForPolls(arg -> {
            countriesMap.putAll(arg.first);
            countriesLetters.addAll(arg.second);
            countriesMap.forEach((s, list) -> countriesList.addAll(list));

            if (countriesToSelect != null) {
                for (String c : countriesToSelect) {
                    TLRPC.TL_help_country country = findCountry(c);
                    if (country != null) {
                        final GroupCreateSpan span = new GroupCreateSpan(getContext(), country);
                        span.setOnClickListener(CountrySelectBottomSheet.this::onSpanClick);
                        spansContainer.addSpan(span);
                        selectedCountries.put(country.iso2, span);
                    }
                }
            }

            adapter.update(true);
            checkUi_buttonCounter();
        });
    }

    private Set<String> countriesToSelect;

    public void prepare(List<String> selectedObjects) {
        query = null;
        countriesToSelect = new HashSet<>(selectedObjects);
    }

    private void onSearch(String text) {
        this.query = text;
    }

    private boolean isSearching() {
        return !TextUtils.isEmpty(query);
    }




    private final Rect listViewClipBounds = new Rect();

    private void checkUi_listViewClip() {
        final int t = AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() + dp(56)
                + (int) animatorSelectorContainerHeight.getFactor();// - dp(6);
        final int b = containerView.getMeasuredHeight() - AndroidUtilities.navigationBarHeight - dp(34);

        final boolean invalidate = listViewClipBounds.top != t || listViewClipBounds.bottom != b;
        listViewClipBounds.set(0, t, containerView.getMeasuredWidth(), b);
        recyclerListView.setClipBounds(listViewClipBounds);
        if (invalidate) {
            recyclerListView.invalidate();
        }
    }

    private void checkUi_searchFieldY() {
        float top = AndroidUtilities.displaySize.y;
        for (int i = 0; i < recyclerListView.getChildCount(); ++i) {
            final View child = recyclerListView.getChildAt(i);
            final int position = recyclerListView.getChildAdapterPosition(child);
            if (position >= 1 && child.getY() < top) {
                top = child.getY();
            }
        }

        final float ty = Math.max(AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight(), top + dp(8));
        if (searchContainer.getTranslationY() != ty) {
            searchContainer.setTranslationY(ty);
            recyclerListView.invalidate();
        }
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.BoostingSelectCountry);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
        adapter.setApplyBackground(false);
        return adapter;
    }


    private GroupCreateSpan currentDeletingSpan;

    private void onSpanClick(View v) {
        GroupCreateSpan span = (GroupCreateSpan) v;
        if (span.isDeleting()) {
            currentDeletingSpan = null;
            spansContainer.removeSpan(span);

            selectedCountries.remove(span.getCountryIso2());
            checkUi_buttonCounter();
            adapter.update(true);
        } else {
            if (currentDeletingSpan != null) {
                currentDeletingSpan.cancelDeleteAnimation();
            }
            currentDeletingSpan = span;
            span.startDeleteAnimation();
        }
    }


    private TLRPC.TL_help_country findCountry(String code) {
        for (String letter : countriesLetters) {
            for (TLRPC.TL_help_country country : countriesMap.get(letter)) {
                if (TextUtils.equals(code, country.iso2)) {
                    return country;
                }
            }
        }
        return null;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (countriesLetters == null || countriesLetters.isEmpty()) {
            return;
        }

        int ah = AndroidUtilities.displaySize.y
            //+ AndroidUtilities.statusBarHeight
            - ActionBar.getCurrentActionBarHeight()
            - dp(68)  // padding
            + dp(13); // just gap

        final int gapH = dp(8 + 40 + 8 + 32) + selectedCountriesHeight;
        items.add(UItem.asSpace(0, gapH));
        ah -= gapH;

        for (String letter : countriesLetters) {
            for (TLRPC.TL_help_country country : countriesMap.get(letter)) {
                if (isSearching()) {
                    String q = translitSafe(query).toLowerCase();
                    if (!SelectorBottomSheet.matchLocal(country, q)) continue;
                }
                ah -= dp(44);
                items.add(Factory.asCountry(country, selectedCountries.containsKey(country.iso2)));
            }
        }

        items.add(UItem.asSpace(1, Math.max(0, ah)));
    }

    private void checkUi_buttonCounter() {
        button.setCount(selectedCountries.size(), true);
    }

    public static class Factory extends UItem.UItemFactory<SelectorCountryCell> {
        static { setup(new Factory()); }

        @Override
        public SelectorCountryCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            SelectorCountryCell cell = new SelectorCountryCell(context, resourcesProvider);
            cell.setBackground(null);
            return cell;
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            SelectorCountryCell cell = (SelectorCountryCell) view;
            cell.setCountry((TLRPC.TL_help_country) item.object, divider);
            cell.setChecked(item.checked, false);
        }

        public static UItem asCountry(TLRPC.TL_help_country country, boolean checked) {
            UItem item = UItem.ofFactory(Factory.class);
            item.text = country.iso2;
            item.object = country;
            item.checked = checked;
            return item;
        }

        @Override
        public boolean equals(UItem a, UItem b) {
            return super.equals(a, b);
        }

        @Override
        public boolean contentsEquals(UItem a, UItem b) {
            return super.contentsEquals(a, b);
        }
    }

    /*
    @Override
    public void dismiss() {
        if (listener != null) {
            listener.onCountrySelected(new ArrayList<>(selectedCountries.keySet()));
        }
        super.dismiss();
    }
    */

    @Override
    public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
        if (id == ANIMATOR_ID_SELECTED_CONTAINER_HEIGHT) {
            checkUi_listViewClip();
            graySectionCell.setTranslationY(dp(48) + factor);
            searchContainer.invalidate();
        } else if (id == ANIMATOR_ID_TOP_SAVE_BUTTON_VISIBILITY) {
            FragmentFloatingButton.setAnimatedVisibility(doneItem, factor);
        }
    }
}
