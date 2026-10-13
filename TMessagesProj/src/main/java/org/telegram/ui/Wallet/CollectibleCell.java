package org.telegram.ui.Wallet;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;
import android.text.style.CharacterStyle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.WebFile;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_wallet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.Objects;

public class CollectibleCell extends FrameLayout implements Theme.Colorable {

    private final Theme.ResourcesProvider resourcesProvider;
    private final BackupImageView thumbView;
    private final TextView titleView;
    private final TextView subtitleView;

    private boolean needDivider;

    public CollectibleCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setWillNotDraw(false);

        thumbView = new BackupImageView(context);
        thumbView.setRoundRadius(dp(8));
        addView(thumbView, LayoutHelper.createFrame(
                46, 46,
                Gravity.LEFT | Gravity.CENTER_VERTICAL,
                15, 0, 0, 0
        ));

        final LinearLayout textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        textLayout.setGravity(Gravity.CENTER_VERTICAL);
        addView(textLayout, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT,
                Gravity.FILL,
                71, 0, 20, 0
        ));

        titleView = new TextView(context);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TruncateAt.END);
        titleView.setIncludeFontPadding(false);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 20));

        subtitleView = new TextView(context);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TruncateAt.END);
        subtitleView.setIncludeFontPadding(false);
        subtitleView.setGravity(Gravity.CENTER_VERTICAL);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 18, 0, 2, 0, 0));

        updateColors();
    }

    public void set(TL_wallet.nftItem item, boolean divider) {
        if (TextUtils.isEmpty(item.name)) {
            titleView.setText(LocaleController.getString(R.string.WalletCollectible));
        } else {
            SpannableStringBuilder ssb = new SpannableStringBuilder(item.name);
            int hashIndex = item.name.indexOf('#');
            if (hashIndex >= 0) {
                ssb.setSpan(new CharacterStyle() {
                    @Override
                    public void updateDrawState(TextPaint textPaint) {
                        textPaint.setAlpha((int) (textPaint.getAlpha() * 0.65f));
                    }
                }, hashIndex, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            titleView.setText(ssb);
        }
        final String model = getAttribute(item, "Model");
        final String backdrop = getAttribute(item, "Backdrop");
        if (!TextUtils.isEmpty(model) && !TextUtils.isEmpty(backdrop)) {
            subtitleView.setText(LocaleController.formatSpannable(R.string.WalletCollectibleModelBackdrop, model, backdrop));
        } else {
            subtitleView.setText(item.description);
        }
        final ImageLocation image = getImage(item, true);

        final int thumbColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
        final Drawable thumbIcon = getContext().getResources().getDrawable(R.drawable.wallet_nft_placeholder).mutate();
        thumbIcon.setColorFilter(new PorterDuffColorFilter(thumbColor, PorterDuff.Mode.SRC_IN));
        final CombinedDrawable thumb = new CombinedDrawable(
            Theme.createRoundRectDrawable(dp(8), Theme.multAlpha(thumbColor, 0.10f)),
            thumbIcon
        );

        if (image != null) {
            thumbView.setImage(image, "46_46", thumb, item);
        } else {
            thumbView.setImageDrawable(thumb);
        }

        needDivider = divider;
        updateColors();
        invalidate();
    }

    static ImageLocation getImage(TL_wallet.nftItem item, boolean small) {
        final TLRPC.WebDocument image = small && item.image_small != null ? item.image_small
                : item.image != null ? item.image : item.image_small;
        return image == null ? null : ImageLocation.getForWebFile(WebFile.createWithWebDocument(image));
    }

    private static String getAttribute(TL_wallet.nftItem item, String name) {
        for (TL_wallet.nftAttribute attribute : item.attributes) {
            if (TextUtils.equals(attribute.trait_type, name)) return attribute.value;
        }
        return null;
    }

    @Override
    public void updateColors() {
        setBackgroundColor(0x00000000);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(58), MeasureSpec.EXACTLY)
        );
    }

    @Override
    protected void onDraw(android.graphics.Canvas canvas) {
        super.onDraw(canvas);
        if (needDivider) {
            canvas.drawRect(
                    LocaleController.isRTL ? 0 : dp(71),
                    getHeight() - 1,
                    getWidth() - (LocaleController.isRTL ? dp(71) : 0),
                    getHeight(),
                    Theme.dividerPaint
            );
        }
    }

    public static class Factory extends UItem.UItemFactory<CollectibleCell> {
        static {
            setup(new Factory());
        }

        @Override
        public CollectibleCell createView(
                Context context,
                RecyclerListView listView,
                int currentAccount,
                int classGuid,
                Theme.ResourcesProvider resourcesProvider
        ) {
            return new CollectibleCell(context, resourcesProvider);
        }

        @Override
        public void bindView(
                View view,
                UItem item,
                boolean divider,
                UniversalAdapter adapter,
                UniversalRecyclerView listView
        ) {
            ((CollectibleCell) view).set((TL_wallet.nftItem) item.object, divider);
        }

        public static UItem of(TL_wallet.nftItem item) {
            final UItem uItem = UItem.ofFactory(Factory.class);
            uItem.object = item;
            return uItem;
        }

        public static TL_wallet.nftItem getCollectible(UItem item) {
            if (item == null || !(item.object instanceof TL_wallet.nftItem)) {
                return null;
            }
            return (TL_wallet.nftItem) item.object;
        }

        @Override
        public boolean equals(UItem first, UItem second) {
            final TL_wallet.nftItem firstItem = (TL_wallet.nftItem) first.object;
            final TL_wallet.nftItem secondItem = (TL_wallet.nftItem) second.object;
            if (firstItem == null || secondItem == null) {
                return firstItem == secondItem;
            }
            return TextUtils.equals(firstItem.address, secondItem.address);
        }

        @Override
        public boolean contentsEquals(UItem first, UItem second) {
            if (!equals(first, second)) return false;
            final TL_wallet.nftItem a = (TL_wallet.nftItem) first.object;
            final TL_wallet.nftItem b = (TL_wallet.nftItem) second.object;
            return a == b || a != null && b != null
                    && Objects.equals(a.name, b.name)
                    && Objects.equals(a.image, b.image)
                    && Objects.equals(a.image_small, b.image_small)
                    && Objects.equals(a.description, b.description)
                    && Objects.equals(a.attributes, b.attributes)
                    && Objects.equals(a.extra, b.extra);
        }

        @Override
        public boolean isClickable() {
            return true;
        }
    }
}
