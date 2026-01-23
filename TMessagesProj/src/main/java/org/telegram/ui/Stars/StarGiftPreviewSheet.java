package org.telegram.ui.Stars;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.AndroidUtilities.replaceTags;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.tlutils.TlUtils;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ExtendedGridLayoutManager;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.DownscaleScrollableNoiseSuppressor;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProviderThemed;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceRenderNode;
import org.telegram.ui.Components.chat.ViewPositionWatcher;
import org.telegram.ui.Gifts.GiftSheet;
import org.telegram.ui.bots.AffiliateProgramFragment;

import java.util.ArrayList;

import me.vkryl.android.animator.BoolAnimator;
import me.vkryl.android.animator.FactorAnimator;
import me.vkryl.core.BitwiseUtils;

public class StarGiftPreviewSheet extends BottomSheetWithRecyclerListView {
    private static final int TAB_MODELS = 0;
    private static final int TAB_BACKDROPS = 1;
    private static final int TAB_PATTERNS = 2;

    private final int currentAccount;

    private final LinearLayout buttonsLayout;
    public final Button[] buttons;

    private final ArrayList<TL_stars.starGiftAttributeBackdrop> backdrops;
    private final ArrayList<TL_stars.starGiftAttributePattern> patterns;
    private final ArrayList<TL_stars.starGiftAttributeModel> models;
    private final BagRandomizer<TL_stars.starGiftAttributeBackdrop> rBackdrops;
    private final BagRandomizer<TL_stars.starGiftAttributePattern> rPatterns;
    private final BagRandomizer<TL_stars.starGiftAttributeModel> rModels;

    private Mode mode = Mode.RANDOM;

    private final ExtendedGridLayoutManager layoutManager;
    private final DefaultItemAnimator itemAnimator;
    private final TabsSelectorView tabsSelectorView;
    private UniversalAdapter adapter;

    private final FrameLayout headerView;
    private final ImageView backButton;
    private final ImageView headerPlay;
    private final StarGiftSheet.TopView topView;
    private final TextView giftNameTextView;
    private final TextView giftStatusTextView;
    private final TL_stars.StarGift gift;
    private final View gradientTop;


    private final DownscaleScrollableNoiseSuppressor scrollableViewNoiseSuppressor;
    private final @Nullable BlurredBackgroundSourceRenderNode glassSourceRenderNode;
    private final @Nullable BlurredBackgroundSourceColor glassSourceFallback;
    private final BlurredBackgroundDrawableViewFactory glassFactory;
    private @Nullable Attributes selectedAttributes;

    public StarGiftPreviewSheet(Context context, Theme.ResourcesProvider resourcesProvider, int currentAccount, TL_stars.StarGift gift, ArrayList<TL_stars.StarGiftAttribute> attributes) {
        super(context, null, false, false, false, resourcesProvider);
        this.currentAccount = currentAccount;
        this.gift = gift;

        this.backdrops = TlUtils.findAllInstances(attributes, TL_stars.starGiftAttributeBackdrop.class);
        this.rBackdrops = new BagRandomizer<>(backdrops);
        this.rBackdrops.setReshuffleIfEnd(false);
        this.patterns = TlUtils.findAllInstances(attributes, TL_stars.starGiftAttributePattern.class);
        this.rPatterns = new BagRandomizer<>(patterns);
        this.rPatterns.setReshuffleIfEnd(false);
        this.models = TlUtils.findAllInstances(attributes, TL_stars.starGiftAttributeModel.class);
        this.rModels = new BagRandomizer<>(models);
        this.rModels.setReshuffleIfEnd(false);

        // actionBar.setVisibility(View.GONE);
        ViewParent parent = actionBar.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(actionBar);
        }

        ignoreTouchActionBar = false;
        headerMoveTop = dp(6);
        occupyNavigationBar = true;

        setBackgroundColor(getBackgroundColor());
        fixNavigationBar();
        // setSlidingActionBar();

        glassSourceFallback = new BlurredBackgroundSourceColor();
        glassSourceFallback.setColor(getBackgroundColor());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SharedConfig.chatBlurEnabled()) {
            scrollableViewNoiseSuppressor = new DownscaleScrollableNoiseSuppressor();
            glassSourceRenderNode = new BlurredBackgroundSourceRenderNode(glassSourceFallback);
            glassSourceRenderNode.setOnDrawablesRelativePositionChangeListener(this::invalidateMergedVisibleBlurredPositionsAndSourcesPositions);
            glassFactory = new BlurredBackgroundDrawableViewFactory(glassSourceRenderNode);
            glassFactory.setLiquidGlassEffectAllowed(LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS));
        } else {
            scrollableViewNoiseSuppressor = null;
            glassSourceRenderNode = null;
            glassFactory = new BlurredBackgroundDrawableViewFactory(glassSourceFallback);
        }
        final ViewPositionWatcher viewPositionWatcher = new ViewPositionWatcher(container);
        glassFactory.setSourceRootView(viewPositionWatcher, container);







        layoutManager = new ExtendedGridLayoutManager(context, 3);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
            if (adapter == null || position == 0)
                return layoutManager.getSpanCount();
            final UItem item = adapter.getItem(position - 1);
            if (item == null || item.spanCount == UItem.MAX_SPAN_COUNT)
                return layoutManager.getSpanCount();
            return item.spanCount;
            }
        });
        recyclerListView.setPadding(dp(16), 0, dp(16), dp(56 + 9 + 9));
        recyclerListView.setClipToPadding(false);
        recyclerListView.setLayoutManager(layoutManager);
        recyclerListView.setSelectorType(9);
        recyclerListView.setSelectorDrawableColor(0);
        recyclerListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                updateTranslationHeader();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && scrollableViewNoiseSuppressor != null) {
                    scrollableViewNoiseSuppressor.onScrolled(dx, dy);
                    invalidateMergedVisibleBlurredPositionsAndSources(BLUR_INVALIDATE_FLAG_SCROLL);
                }
            }
        });
        itemAnimator = new DefaultItemAnimator() {
            @Override
            protected float animateByScale(View view) {
                return .3f;
            }
        };
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDurations(280);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayIncrement(30);
        recyclerListView.setItemAnimator(itemAnimator);

        headerView = new FrameLayout(context);
        headerView.setClipChildren(false);

        topView = new StarGiftSheet.TopView(context, resourcesProvider, this::onBackPressed, v -> {}, v -> {}, v -> {}, v -> {}, v -> {}, v -> {}) {
            @Override
            public float getRealHeight() {
                return dp(315);
            }

            @Override
            public int getFinalHeight() {
                return dp(315);
            }

            final float[] hsv = new float[3];

            @Override
            protected void updateButtonsBackgrounds(int color) {
                super.updateButtonsBackgrounds(color);
                if (backButton != null && Theme.setSelectorDrawableColor(backButton.getBackground(), color, false)) {
                    backButton.invalidate();
                }
                if (headerPlay != null && Theme.setSelectorDrawableColor(headerPlay.getBackground(), color, false)) {
                    headerPlay.invalidate();
                }
                for (StarGiftPreviewSheet.Button btn : StarGiftPreviewSheet.this.buttons) {
                    if (Theme.setSelectorDrawableColor(btn.getBackground(), color, false)) {
                        btn.invalidate();
                    }

                    Color.colorToHSV(ColorUtils.blendARGB(color, Color.WHITE, 0.33f), hsv);
                    hsv[1] = Math.min(1f, hsv[1] * 1.1f);
                    hsv[2] = Math.min(1f, hsv[2] * 1.1f);
                    int c = Color.HSVToColor(hsv);

                    if (Theme.setSelectorDrawableColor(btn.percentView.getSizeableBackground(), c, false)) {
                        btn.percentView.invalidate();
                    }
                }
            }

            @Override
            public void onSwitchPage(StarGiftSheet.PageTransition p) {
                super.onSwitchPage(p);
                updateHeaderAttributes(true);
            }

            Path path = new Path();
            float[] r = new float[8];

            @Override
            protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);

                r[0] = r[1] = r[2] = r[3] = dp(12);
                path.rewind();
                path.addRoundRect(0, 0, w, h, r, Path.Direction.CW);
            }

            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                canvas.save();
                canvas.clipPath(path);
                super.dispatchDraw(canvas);
                canvas.restore();
            }
        };
        topView.onSwitchPage(new StarGiftSheet.PageTransition(StarGiftSheet.PAGE_UPGRADE, StarGiftSheet.PAGE_UPGRADE, 1.0f));
        topView.setPreviewingAttributes(attributes);
        topView.hideCloseButton();
        headerView.addView(topView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        headerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

        backButton = new ImageView(context);
        backButton.setBackground(Theme.createRadSelectorDrawable(0, 0x10FFFFFF, 16, 16));
        backButton.setImageResource(R.drawable.ic_ab_back);
        backButton.setScaleType(ImageView.ScaleType.CENTER);
        backButton.setOnClickListener(v -> dismiss());
        ScaleStateListAnimator.apply(backButton);
        headerView.addView(backButton, LayoutHelper.createFrame(32, 32, Gravity.TOP | Gravity.LEFT, 12, 14, 0, 0));

        headerPlay = new ImageView(context);
        headerPlay.setBackground(Theme.createRadSelectorDrawable(0, 0x10FFFFFF, 16, 16));
        headerPlay.setImageResource(R.drawable.filled_gift_pause_24);
        headerPlay.setScaleType(ImageView.ScaleType.CENTER);
        headerPlay.setOnClickListener(v -> {
            if (mode == Mode.SELECTED) {
                topView.setPreviewingAttributes(attributes);
                setMode(Mode.RANDOM);
            } else if (mode == Mode.RANDOM) {
                selectedAttributes = new Attributes(
                    topView.getUpgradeBackdropAttribute(),
                    topView.getUpgradePatternAttribute(),
                    topView.getUpgradeImageViewAttribute()
                );
                topView.setPreviewAttributes(selectedAttributes);
                setMode(Mode.SELECTED);
            }
        });
        ScaleStateListAnimator.apply(headerPlay);
        headerView.addView(headerPlay, LayoutHelper.createFrame(32, 32, Gravity.TOP | Gravity.RIGHT, 0, 14, 12, 0));

        giftNameTextView = new TextView(context);
        giftNameTextView.setTypeface(AndroidUtilities.bold());
        giftNameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 21);
        giftNameTextView.setText(gift.title);
        giftNameTextView.setGravity(Gravity.CENTER);
        giftNameTextView.setTextColor(Color.WHITE);
        headerView.addView(giftNameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 16, 0, 16, 102));


        giftStatusTextView = new TextView(context);
        giftStatusTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        giftStatusTextView.setText(getString(R.string.Gift2PreviewRandomTraits));
        giftStatusTextView.setGravity(Gravity.CENTER);
        giftStatusTextView.setTextColor(0x8FFFFFFF);
        headerView.addView(giftStatusTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 16, 0, 16, 82));

        buttonsLayout = new LinearLayout(context);
        buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonsLayout.setClipChildren(false);
        buttons = new Button[3];
        tabsSelectorView = new TabsSelectorView(context, resourcesProvider, tab -> {
            itemAnimator.endAnimations();
            adapter.update(true);
        });

        for (int i = 0; i < buttons.length; ++i) {
            buttons[i] = new Button(context);
            switch (i) {
                case 0:
                    buttons[i].textView.setText(getString(R.string.GiftPreviewModel));
                    break;
                case 1:
                    buttons[i].textView.setText(getString(R.string.GiftPreviewBackdrop));
                    break;
                case 2:
                    buttons[i].textView.setText(getString(R.string.GiftPreviewSymbol));
                    break;
            }

            ScaleStateListAnimator.apply(buttons[i]);
            int finalI = i;
            buttons[i].setOnClickListener(v -> {
                tabsSelectorView.selectTab(finalI);
            });
            buttons[i].setBackground(Theme.createRadSelectorDrawable(0, 0x10FFFFFF, 10, 10));
            buttonsLayout.addView(buttons[i], LayoutHelper.createLinear(0, 42, 1, Gravity.FILL_HORIZONTAL, 0, 0, i != buttons.length - 1 ? 11 : 0, 0));
        }
        headerView.addView(buttonsLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 16, 0, 16, 18));
        containerView.addView(headerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 315, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        int color = getBackgroundColor();
        gradientTop = new View(context);
        gradientTop.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{ColorUtils.setAlphaComponent(color, 160), color & 0x00FFFFFF}));
        gradientTop.setAlpha(0f);
        FrameLayout.LayoutParams lp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 0, Gravity.TOP);
        lp.height = AndroidUtilities.statusBarHeight;
        containerView.addView(gradientTop, lp);


        tabsSelectorView.setPadding(dp(8), dp(8), dp(8), dp(8));
        BlurredBackgroundDrawable drawable = glassFactory.create(tabsSelectorView);
        drawable.setPadding(dp(4));
        drawable.setRadius(dp(28));
        drawable.setColorProvider(new BlurredBackgroundColorProviderThemed(resourcesProvider, Theme.key_windowBackgroundWhite));
        tabsSelectorView.setBackground(drawable);
        containerView.addView(tabsSelectorView, LayoutHelper.createFrame(268, 64, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 5));

        selectedAttributes = new Attributes(
            TlUtils.findFirstInstance(attributes, TL_stars.starGiftAttributeBackdrop.class),
            TlUtils.findFirstInstance(attributes, TL_stars.starGiftAttributePattern.class),
            TlUtils.findFirstInstance(attributes, TL_stars.starGiftAttributeModel.class)
        );

        adapter.update(false);
        updateHeaderAttributes(false);
    }

    @Override
    protected CharSequence getTitle() {
        return null;
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        adapter = new UniversalAdapter(recyclerListView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider) {
            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                super.onBindViewHolder(holder, position);
                if (!(holder.itemView instanceof GiftAttributeCell)) {
                    return;
                }

                final GiftAttributeCell cell = (GiftAttributeCell) holder.itemView;
                final Attributes attributes = cell.attributes;

                final boolean isSelected = isSelectedWithCurrentTab(attributes);
                cell.setSelected(isSelected, false);
                cell.setOnClickListener(v -> {
                    if (mode == Mode.RANDOM) {
                        selectedAttributes = new Attributes(
                            topView.getUpgradeBackdropAttribute(),
                            topView.getUpgradePatternAttribute(),
                            topView.getUpgradeImageViewAttribute()
                        );
                        setMode(Mode.SELECTED);
                    }

                    selectedAttributes = newSelectedWithCurrentTab(attributes);
                    topView.setPreviewAttributes(selectedAttributes);
                    updateSelectedForVisibleViews();
                });
            }
        };

        adapter.setApplyBackground(false);
        return adapter;
    }

    private void updateTranslationHeader() {
        boolean found = false;
        float top = 0; // Math.max(0, getHeight() - height());
        for (int i = recyclerListView.getChildCount() - 1; i >= 0; --i) {
            final View child = recyclerListView.getChildAt(i);
            int position = recyclerListView.getChildAdapterPosition(child);
            if (position < 0) continue;
            if (position == 2) {
                top = child.getY() - headerView.getMeasuredHeight();
                found = true;
                break;
            } else if (position == 1) {
                top = child.getY();
                found = true;
                break;
            } else if (position == 0) {
                top = child.getY() - headerView.getMeasuredHeight();
                found = true;
                break;
            }
        }

        final float bottom = top + headerView.getHeight();

        boolean newGradientVisible = !found || bottom < 0;

        if (gradientVisible != newGradientVisible) {
            gradientVisible = newGradientVisible;
            gradientTop.animate().alpha(newGradientVisible ? 1 : 0).setDuration(200).start();
        }

        headerMoveTop = top <= 0 ? 0 : dp(6);
        headerView.setVisibility(found ? View.VISIBLE : View.GONE);
        headerView.setTranslationY(top);
    }

    private boolean gradientVisible;

    private void updateHeaderAttributes(boolean animated) {
        if (topView.getUpgradeImageViewAttribute() == null || topView.getUpgradeBackdropAttribute() == null || topView.getUpgradePatternAttribute() == null) {
            return;
        }

        buttons[0].titleView.setText(topView.getUpgradeImageViewAttribute().name, animated);
        buttons[0].percentView.setText(AffiliateProgramFragment.percents(topView.getUpgradeImageViewAttribute().rarity_permille), animated);
        buttons[1].titleView.setText(topView.getUpgradeBackdropAttribute().name, animated);
        buttons[1].percentView.setText(AffiliateProgramFragment.percents(topView.getUpgradeBackdropAttribute().rarity_permille), animated);
        buttons[2].titleView.setText(topView.getUpgradePatternAttribute().name, animated);
        buttons[2].percentView.setText(AffiliateProgramFragment.percents(topView.getUpgradePatternAttribute().rarity_permille), animated);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (models == null || backdrops == null || patterns == null) {
            return;
        }

        items.add(UItem.asSpace(dp(315)));

        rBackdrops.reset();
        rPatterns.reset();
        rModels.reset();

        int tab = tabsSelectorView.getSelectedTab();
        if (tab == TAB_MODELS) {
            items.add(UItem.asCenterShadow(replaceTags(LocaleController.formatPluralStringComma("GiftPreviewCountModels", models.size()))));
            for (TL_stars.starGiftAttributeModel model : models) {
                items.add(GiftAttributeCell.Factory.asAttribute(tab, new Attributes(
                    rBackdrops.next(), rPatterns.next(), model)));
            }
        } else if (tab == TAB_BACKDROPS) {
            items.add(UItem.asCenterShadow(replaceTags(LocaleController.formatPluralStringComma("GiftPreviewCountBackdrops", backdrops.size()))));
            for (TL_stars.starGiftAttributeBackdrop backdrop : backdrops) {
                items.add(GiftAttributeCell.Factory.asAttribute(tab, new Attributes(
                    backdrop, rPatterns.next(), rModels.next())));
            }
        } else if (tab == TAB_PATTERNS) {
            items.add(UItem.asCenterShadow(replaceTags(LocaleController.formatPluralStringComma("GiftPreviewCountSymbols", patterns.size()))));
            for (TL_stars.starGiftAttributePattern pattern : patterns) {
                items.add(GiftAttributeCell.Factory.asAttribute(tab, new Attributes(
                    rBackdrops.next(), pattern, rModels.next())));
            }
        }
    }

    public static class Attributes {
        public final TL_stars.starGiftAttributeBackdrop backdrop;
        public final TL_stars.starGiftAttributePattern pattern;
        public final TL_stars.starGiftAttributeModel model;

        public Attributes(TL_stars.starGiftAttributeBackdrop backdrop, TL_stars.starGiftAttributePattern pattern, TL_stars.starGiftAttributeModel model) {
            this.backdrop = backdrop;
            this.pattern = pattern;
            this.model = model;
        }
    }


    @SuppressLint("ViewConstructor")
    public static class GiftAttributeCell extends FrameLayout implements FactorAnimator.Target {
        private final int currentAccount;
        private final Theme.ResourcesProvider resourcesProvider;

        private final FrameLayout cardBackgroundView;
        private final GiftSheet.CardBackground cardBackground;
        private final BackupImageView imageView;
        private final TextView textView;
        private final TextView percentageView;

        public GiftAttributeCell(@NonNull Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.currentAccount = currentAccount;
            this.resourcesProvider = resourcesProvider;

            cardBackgroundView = new FrameLayout(context);
            cardBackgroundView.setBackground(cardBackground = new GiftSheet.CardBackground(cardBackgroundView, resourcesProvider, true));
            addView(cardBackgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

            imageView = new BackupImageView(context);
            imageView.getImageReceiver().setAutoRepeat(0);
            addView(imageView, LayoutHelper.createFrame(90, 90, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 12, 0, 0));

            textView = new TextView(context);
            textView.setTypeface(AndroidUtilities.bold());
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            textView.setGravity(Gravity.CENTER);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setTextColor(Color.WHITE);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.NO_GRAVITY, 12, 106, 12, 14));

            percentageView = new TextView(context);
            percentageView.setClickable(false);
            percentageView.setTypeface(AndroidUtilities.bold());
            percentageView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            percentageView.setPadding(dp(4), dp(1), dp(5), dp(1));
            percentageView.setBackground(Theme.createRadSelectorDrawable(0, 0x10FFFFFF, 10, 10));
            addView(percentageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT, 0, 10, 10, 0));
        }

        public void setSelected(boolean isSelected, boolean animated) {
            cardBackground.setSelected(isSelected, animated);
            this.isSelected.setValue(isSelected, animated);
        }

        private TLRPC.Document lastDocument;
        private void setSticker(TLRPC.Document document, int sizeInDp, Object parentObject) {
            if (document == null) {
                imageView.clearImage();
                lastDocument = null;
                return;
            }

            if (lastDocument == document) return;
            lastDocument = document;

            TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, dp(100));
            SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundGray, 0.3f);
            final String filter = sizeInDp + "_" + sizeInDp;
            final int padding = (90 - sizeInDp) / 2;

            imageView.setLayoutParams(LayoutHelper.createFrame(sizeInDp, sizeInDp,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 12 + padding, 0, padding));
            imageView.setImage(
                ImageLocation.getForDocument(document), filter,
                ImageLocation.getForDocument(photoSize, document), filter,
                svgThumb,
                parentObject
            );
        }

        private final BoolAnimator isSelected = new BoolAnimator(0, this, CubicBezierInterpolator.EASE_OUT_QUINT, 320);

        @Override
        public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
            checkPercentageViewBackground();
        }

        private void checkPercentageViewBackground() {
            final int color;

            if (noPercentageBackground) {
                color = ColorUtils.blendARGB(ColorUtils.blendARGB(
                    Theme.getColor(Theme.key_windowBackgroundWhite),
                    Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), 0.05f),
                    Theme.getColor(Theme.key_featuredStickers_addButton), isSelected.getFloatValue());

                percentageView.setTextColor(ColorUtils.blendARGB(ColorUtils.blendARGB(
                        Theme.getColor(Theme.key_windowBackgroundWhite),
                        Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), 0.5f), Color.WHITE, isSelected.getFloatValue()));
            } else {
                color = ColorUtils.blendARGB(
                    ColorUtils.setAlphaComponent(attributes.backdrop.center_color, 255),
                    ColorUtils.setAlphaComponent(attributes.backdrop.pattern_color, 255), 0.5f);

                percentageView.setTextColor(Color.WHITE);
            }

            if (Theme.setSelectorDrawableColor(percentageView.getBackground(), color, false)) {
                percentageView.invalidate();
            }
        }

        private boolean noPercentageBackground;
        private Attributes attributes;

        public static class Factory extends UItem.UItemFactory<GiftAttributeCell> {
            static { setup(new GiftAttributeCell.Factory()); }

            @Override
            public GiftAttributeCell createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new GiftAttributeCell(context, currentAccount, resourcesProvider);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                final GiftAttributeCell cell = (GiftAttributeCell) view;
                final Attributes attributes = (Attributes) item.object;
                final int tab = item.intValue;
                int percent = 0;

                cell.noPercentageBackground = tab == TAB_MODELS;
                cell.attributes = attributes;

                if (tab == TAB_MODELS) {
                    cell.cardBackground.setBackdrop(null);
                    cell.cardBackground.setPattern(null);

                    cell.textView.setText(attributes.model.name);
                    cell.setSticker(attributes.model.document, 90, item.object);
                    cell.imageView.setColorFilter(null);
                    cell.cardBackground.selectedColorKey = Theme.key_featuredStickers_addButton;

                    percent = attributes.model.rarity_permille;
                } else if (tab == TAB_BACKDROPS) {
                    cell.cardBackground.setBackdrop(attributes.backdrop);
                    cell.cardBackground.setPattern(attributes.pattern);
                    cell.cardBackground.selectedColorKey = Theme.key_windowBackgroundWhite;

                    cell.textView.setText(attributes.backdrop.name);
                    cell.setSticker(attributes.pattern.document, 48, item.object);
                    cell.imageView.setColorFilter(new PorterDuffColorFilter(ColorUtils.setAlphaComponent(attributes.backdrop.pattern_color, 64), PorterDuff.Mode.SRC_IN));

                    percent = attributes.backdrop.rarity_permille;
                } else if (tab == TAB_PATTERNS) {
                    cell.cardBackground.setBackdrop(attributes.backdrop);
                    cell.cardBackground.setPattern(attributes.pattern);
                    cell.cardBackground.selectedColorKey = Theme.key_windowBackgroundWhite;

                    cell.textView.setText(attributes.pattern.name);
                    cell.setSticker(attributes.pattern.document, 64, item.object);
                    cell.imageView.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));

                    percent = attributes.pattern.rarity_permille;
                }

                cell.textView.setTextColor(tab == TAB_MODELS ? Theme.getColor(Theme.key_dialogTextBlack, cell.resourcesProvider) : Color.WHITE);
                cell.percentageView.setText(AffiliateProgramFragment.percents(percent));
                cell.checkPercentageViewBackground();
            }

            public static UItem asAttribute(int tab, Attributes attributes) {
                final UItem item = UItem.ofFactory(GiftAttributeCell.Factory.class).setSpanCount(1);
                item.intValue = tab;
                item.object = attributes;
                return item;
            }
        }
    }

    protected RecyclerListView createRecyclerView(Context context) {
        return new RecyclerListView(context, resourcesProvider) {
            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                applyScrolledPosition();
                super.onLayout(changed, l, t, r, b);
                invalidateMergedVisibleBlurredPositionsAndSourcesImpl(BLUR_INVALIDATE_FLAG_POSITIONS);
            }

            @Override
            protected boolean canHighlightChildAt(View child, float x, float y) {
                return StarGiftPreviewSheet.this.canHighlightChildAt(child, x, y);
            }
        };
    }

    @Override
    public void onOpenAnimationEnd() {
        super.onOpenAnimationEnd();
        invalidateMergedVisibleBlurredPositionsAndSourcesImpl(BLUR_INVALIDATE_FLAG_POSITIONS);
    }

    private static final int BLUR_INVALIDATE_FLAG_SCROLL = 1;
    private static final int BLUR_INVALIDATE_FLAG_POSITIONS = 1 << 1;

    private final RectF tabsRectF = new RectF();
    private final PointF tabsPosP = new PointF();
    private final ArrayList<RectF> blurredPositions = new ArrayList<>(1); {
        blurredPositions.add(tabsRectF);
    }

    @Override
    protected void mainContainerDispatchDraw(Canvas canvas) {
        super.mainContainerDispatchDraw(canvas);

        final int width = container.getWidth();
        final int height = container.getHeight();

        if (Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated() && scrollableViewNoiseSuppressor != null) {
            if (glassSourceRenderNode != null && !glassSourceRenderNode.inRecording()) {
                if (glassSourceRenderNode.needUpdateDisplayList(width, height) /*|| glassSourcesInvalidated*/) {
                    final Canvas c = glassSourceRenderNode.beginRecording(width, height);
                    c.drawColor(getThemedColor(Theme.key_dialogBackgroundGray));
                    scrollableViewNoiseSuppressor.draw(c, LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ?
                        DownscaleScrollableNoiseSuppressor.DRAW_GLASS:
                        DownscaleScrollableNoiseSuppressor.DRAW_FROSTED_GLASS);
                    glassSourceRenderNode.endRecording();
                }
            }

            // scrollableViewNoiseSuppressor.drawDebugPositions(canvas);
        }
    }

    private final RectF tmpViewRectF = new RectF();
    private final PointF tmpViewPointF = new PointF();
    private void drawList(Canvas canvas, RectF position) {
        final long drawingTime = SystemClock.uptimeMillis();
        ViewPositionWatcher.computeCoordinatesInParent(recyclerListView, container, tmpViewPointF);
        canvas.save();
        canvas.clipRect(position);
        canvas.translate(tmpViewPointF.x, tmpViewPointF.y);
        for (int i = 0; i < recyclerListView.getChildCount(); i++) {
            View child = recyclerListView.getChildAt(i);

            ViewPositionWatcher.computeCoordinatesInParent(child, container, tmpViewPointF);
            tmpViewRectF.set(tmpViewPointF.x, tmpViewPointF.y, tmpViewPointF.x + child.getWidth(), tmpViewPointF.y + child.getHeight());
            if (!tmpViewRectF.intersect(position)) {
                continue;
            }

            recyclerListView.drawChild(canvas, child, drawingTime);
        }
        canvas.restore();
    }

    private void invalidateMergedVisibleBlurredPositionsAndSourcesPositions() {
        invalidateMergedVisibleBlurredPositionsAndSources(BLUR_INVALIDATE_FLAG_POSITIONS);
    }

    private void invalidateMergedVisibleBlurredPositionsAndSources(int flags) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || scrollableViewNoiseSuppressor == null) {
            return;
        }

        invalidateMergedVisibleBlurredPositionsAndSourcesImpl(flags);
    }

    private void invalidateMergedVisibleBlurredPositionsAndSourcesImpl(int flags) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || scrollableViewNoiseSuppressor == null) {
            return;
        }

        if (BitwiseUtils.hasFlag(flags, BLUR_INVALIDATE_FLAG_POSITIONS)) {
            ViewPositionWatcher.computeCoordinatesInParent(tabsSelectorView, container, tabsPosP);
            tabsRectF.left = tabsPosP.x;
            tabsRectF.top = tabsPosP.y;
            tabsRectF.right = tabsRectF.left + tabsSelectorView.getMeasuredWidth();
            tabsRectF.bottom = Math.min(tabsRectF.top + tabsSelectorView.getMeasuredHeight(), container.getMeasuredHeight());
            if (tabsRectF.isEmpty()) {
                return;
            }

            final int inset = LiteMode.isEnabled(LiteMode.FLAG_LIQUID_GLASS) ? 0 : dp(48);
            tabsRectF.inset(-inset, -inset);

            scrollableViewNoiseSuppressor.setupRenderNodes(blurredPositions, 1);
        }

        if (scrollableViewNoiseSuppressor.getRenderNodesCount() == 0) {
            return;
        }

        final RectF position = scrollableViewNoiseSuppressor.getPosition(0);
        Canvas c = scrollableViewNoiseSuppressor.beginRecordingRect(0);
        c.save();
        c.translate(-position.left, -position.top);
        drawList(c, position);
        c.restore();
        scrollableViewNoiseSuppressor.endRecordingRect();

        scrollableViewNoiseSuppressor.invalidateResultRenderNodes(container.getWidth(), container.getHeight());
    }

    private int lastBottomInset;

    @Override
    protected void onInsetsChanged() {
        super.onInsetsChanged();
        applyBottomInset();
    }

    private void applyBottomInset() {
        int inset = getSystemBottomInset();
        if (lastBottomInset != inset) {
            lastBottomInset = inset;
            recyclerListView.setPadding(dp(16), 0, dp(16), inset + dp(56 + 9 + 9));
            ((ViewGroup.MarginLayoutParams) tabsSelectorView.getLayoutParams()).bottomMargin = lastBottomInset + dp(5);
            tabsSelectorView.requestLayout();
        }
    }

    private int getBackgroundColor() {
        return ColorUtils.blendARGB(
            getThemedColor(Theme.key_dialogBackgroundGray),
            getThemedColor(Theme.key_dialogBackground), 0.1f);
    }

    private static class Button extends FrameLayout {

        public TextView textView;
        public AnimatedTextView titleView;
        public AnimatedTextView percentView;

        public Button(Context context) {
            super(context);

            setClipChildren(false);

            titleView = new AnimatedTextView(context, true, false, false);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setTextSize(dp(13));
            titleView.setTextColor(0xFFFFFFFF);
            titleView.setGravity(Gravity.CENTER);

            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 16, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 4, 6, 4, 0));

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            textView.setTextColor(0x8FFFFFFF);
            textView.setGravity(Gravity.CENTER);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 4, 20, 4, 0));

            percentView = new AnimatedTextView(context);
            percentView.setText("WTF");
            percentView.setTypeface(AndroidUtilities.bold());
            percentView.setTextColor(Color.WHITE);
            percentView.setGravity(Gravity.RIGHT);
            percentView.setTextSize(dp(11));
            percentView.setPadding(dp(3), dp(1), dp(3), dp(1));
            percentView.setSizeableBackground(Theme.createRadSelectorDrawable(0, 0x10FFFFFF, 10, 10));
            addView(percentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 16, Gravity.TOP | Gravity.RIGHT, 0, -9, -4, 0));
        }
    }

    private static class Tab extends FrameLayout implements FactorAnimator.Target {
        private final TextView textView;
        private final ImageView imageView;

        private final BoolAnimator isSelectedAnimator = new BoolAnimator(0, this, CubicBezierInterpolator.EASE_OUT_QUINT, 300);
        private int color;

        public Tab(@NonNull Context context) {
            super(context);
            imageView = new ImageView(context);
            addView(imageView, LayoutHelper.createFrame(24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 6, 0, 0));

            imageView.setColorFilter(new PorterDuffColorFilter(Color.BLACK, PorterDuff.Mode.SRC_IN));

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            textView.setSingleLine();
            textView.setLines(1);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setTypeface(AndroidUtilities.bold());
            textView.setGravity(Gravity.CENTER);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 30, 0, 0));
        }

        public static Tab create(Context context, Theme.ResourcesProvider resourcesProvider, @DrawableRes int drawableRes, @StringRes int stringRes, Runnable onClick) {
            Tab tab = new Tab(context);
            tab.textView.setText(LocaleController.getString(stringRes));
            tab.imageView.setImageResource(drawableRes);
            tab.color = Theme.getColor(Theme.key_glass_defaultIcon, resourcesProvider);
            tab.setOnClickListener(v -> onClick.run());
            tab.updateColors();
            ScaleStateListAnimator.apply(tab);
            return tab;
        }

        @Override
        public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
            updateColors();
        }

        private void updateColors() {
            final int alpha = lerp(153, 255, isSelectedAnimator.getFloatValue());

            imageView.setColorFilter(new PorterDuffColorFilter(ColorUtils.setAlphaComponent(color, alpha), PorterDuff.Mode.SRC_IN));
            textView.setTextColor(ColorUtils.setAlphaComponent(color, alpha));
        }
    }

    private static class TabsSelectorView extends LinearLayout implements FactorAnimator.Target {
        public final FactorAnimator animator = new FactorAnimator(0, this, CubicBezierInterpolator.EASE_OUT_QUINT, 320);
        public final Utilities.Callback<Integer> onTabSelectListener;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Tab[] tabs;

        public TabsSelectorView(Context context, Theme.ResourcesProvider resourcesProvider, Utilities.Callback<Integer> onTabSelectListener) {
            super(context);
            setOrientation(HORIZONTAL);

            this.onTabSelectListener = onTabSelectListener;
            paint.setColor(Theme.getColor(Theme.key_glass_defaultIcon, resourcesProvider));
            paint.setAlpha(16);

            this.tabs = new Tab[]{
                Tab.create(context, resourcesProvider, R.drawable.filled_gift_models_24, R.string.GiftPreviewModels, () -> selectTab(0)),
                Tab.create(context, resourcesProvider, R.drawable.filled_gift_palette_24, R.string.GiftPreviewBackdrops, () -> selectTab(1)),
                Tab.create(context, resourcesProvider, R.drawable.filled_gift_symbols_24, R.string.GiftPreviewSymbols, () -> selectTab(2))
            };

            addView(tabs[0], LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));
            addView(tabs[1], LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));
            addView(tabs[2], LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f));

            tabs[0].isSelectedAnimator.setValue(true, false);
        }

        private int selectedTab;
        private void selectTab(int tab) {
            if (selectedTab != tab) {
                tabs[selectedTab].isSelectedAnimator.setValue(false, true);
                tabs[tab].isSelectedAnimator.setValue(true, true);

                selectedTab = tab;
                animator.animateTo(tab);
                onTabSelectListener.run(tab);
            }
        }

        public int getSelectedTab() {
            return selectedTab;
        }

        @Override
        protected void dispatchDraw(@NonNull Canvas canvas) {
            final float start = animator.getFactor();
            final float left = lerp(dp(8), getMeasuredWidth() - dp(8), start / 3);
            final float right = lerp(dp(8), getMeasuredWidth() - dp(8), (start + 1) / 3);

            canvas.drawRoundRect(left, dp(8), right, getMeasuredHeight() - dp(8), dp(24), dp(24), paint);

            super.dispatchDraw(canvas);
        }

        @Override
        public void onFactorChanged(int id, float factor, float fraction, FactorAnimator callee) {
            invalidate();
        }
    }


    private void setMode(Mode mode) {
        if (this.mode == mode) {
            return;
        }

        this.mode = mode;

        headerPlay.setImageResource(mode == Mode.SELECTED ?
            R.drawable.filled_gift_play_24:
            R.drawable.filled_gift_pause_24);

        giftStatusTextView.setText(getString(mode == Mode.SELECTED ?
            R.string.Gift2PreviewSelectedTraits:
            R.string.Gift2PreviewRandomTraits));


        updateSelectedForVisibleViews();
    }

    @Override
    protected boolean isTouchOutside(float x, float y) {
        return headerView.getVisibility() == View.VISIBLE && headerView.getY() > y;
    }

    private boolean isSelectedWithCurrentTab(Attributes attributes) {
        if (mode == Mode.RANDOM) {
            return false;
        }

        final int tab = tabsSelectorView.getSelectedTab();
        if (selectedAttributes != null) {
            if (tab == TAB_BACKDROPS) {
                return attributes.backdrop == selectedAttributes.backdrop;
            } else if (tab == TAB_PATTERNS) {
                return attributes.pattern == selectedAttributes.pattern;
            } else if (tab == TAB_MODELS) {
                return attributes.model == selectedAttributes.model;
            }
        }
        return false;
    }

    private Attributes newSelectedWithCurrentTab(Attributes attributes) {
        final int tab = tabsSelectorView.getSelectedTab();
        if (selectedAttributes != null) {
            if (tab == TAB_BACKDROPS) {
                return new Attributes(attributes.backdrop, selectedAttributes.pattern, selectedAttributes.model);
            } else if (tab == TAB_PATTERNS) {
                return new Attributes(selectedAttributes.backdrop, attributes.pattern, selectedAttributes.model);
            } else if (tab == TAB_MODELS) {
                return new Attributes(selectedAttributes.backdrop, selectedAttributes.pattern, attributes.model);
            }
        }
        return null;
    }

    private void updateSelectedForVisibleViews() {
        for (int a = 0, N = recyclerListView.getChildCount(); a < N; a++) {
            final View view = recyclerListView.getChildAt(a);
            if ((view instanceof GiftAttributeCell)) {
                final GiftAttributeCell cell = (GiftAttributeCell) view;
                final Attributes attributes = cell.attributes;
                if (attributes != null) {
                    boolean isSelected = isSelectedWithCurrentTab(attributes);
                    cell.setSelected(isSelected, true);
                }
            }
        }
    }

    private enum Mode {
        RANDOM, SELECTED
    }
}
