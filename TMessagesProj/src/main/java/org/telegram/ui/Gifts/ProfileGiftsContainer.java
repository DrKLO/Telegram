package org.telegram.ui.Gifts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.MessagesController.getInstance;

import android.content.Context;
import android.graphics.Paint;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.checkerframework.checker.units.qual.A;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BirthdayController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.Premium.boosts.UserSelectorBottomSheet;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.bots.BotPreviewsEditContainer;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ProfileGiftsContainer extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private final int currentAccount;
    private final long userId;
    private final StarsController.GiftsList list;
    private final Theme.ResourcesProvider resourcesProvider;

    private final UniversalRecyclerView listView;
    private final FrameLayout buttonContainer;
    private final ButtonWithCounterView button;

    private long getRandomUserId() {
        final ConcurrentHashMap<Long, TLRPC.User> map = MessagesController.getInstance(currentAccount).getUsers();
        final int size = map.size();
        if (size == 0) {
            return 0;
        }
        final int randomIndex = Utilities.fastRandom.nextInt(size);
        int currentIndex = 0;
        for (Map.Entry<Long, TLRPC.User> entry : map.entrySet()) {
            if (currentIndex == randomIndex) {
                return entry.getValue().id;
            }
            currentIndex++;
        }
        return 0;
    }

    protected int processColor(int color) {
        return color;
    }

    public ProfileGiftsContainer(Context context, int currentAccount, long userId, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.currentAccount = currentAccount;
        this.userId = userId;
        this.list = StarsController.getInstance(currentAccount).getProfileGiftsList(userId);
        this.list.shown = true;
        this.list.load();
        this.resourcesProvider = resourcesProvider;

        setBackgroundColor(Theme.blendOver(
            Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider),
            Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 0.04f)
        ));

        listView = new UniversalRecyclerView(context, currentAccount, 0, false, this::fillItems, this::onItemClick, this::onItemLongPress, resourcesProvider, 3);
        listView.adapter.setApplyBackground(false);
        listView.setSelectorType(9);
        listView.setSelectorDrawableColor(0);
        listView.setPadding(dp(9), 0, dp(9), 0);
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (!listView.canScrollVertically(1) || isLoadingVisible()) {
                    list.load();
                }
            }
        });

        buttonContainer = new FrameLayout(context);
        buttonContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM));

        final View buttonShadow = new View(context);
        buttonShadow.setBackgroundColor(Theme.getColor(Theme.key_dialogGrayLine, resourcesProvider));
        buttonContainer.addView(buttonShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1f / AndroidUtilities.density, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        button = new ButtonWithCounterView(context, resourcesProvider);
        final SpannableStringBuilder sb = new SpannableStringBuilder("G  " + LocaleController.getString(R.string.ProfileGiftsSend));
        final ColoredImageSpan span = new ColoredImageSpan(R.drawable.filled_gift_premium);
        sb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        button.setText(sb, false);
        buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL, 10, 10 + 1f / AndroidUtilities.density, 10, 10));
        button.setOnClickListener(v -> {
            UserSelectorBottomSheet.open(UserSelectorBottomSheet.TYPE_STAR_GIFT, 0, BirthdayController.getInstance(currentAccount).getState());
        });
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.starUserGiftsLoaded) {
            if ((Long) args[0] == userId) {
                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
                if (!listView.canScrollVertically(1) || isLoadingVisible()) {
                    list.load();
                }
            }
        }
    }

    private boolean isLoadingVisible() {
        for (int i = 0; i < listView.getChildCount(); ++i) {
            if (listView.getChildAt(i) instanceof FlickerLoadingView)
                return true;
        }
        return false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.starUserGiftsLoaded);
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(false);
        }
        if (list != null) {
            list.shown = true;
            list.load();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.starUserGiftsLoaded);
        if (list != null) {
            list.shown = false;
        }
    }

    public int getGiftsCount() {
        if (list != null && list.totalCount > 0) return list.totalCount;
        final TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(userId);
        return userFull != null ? userFull.stargifts_count : 0;
    }

    public CharSequence getLastEmojis(Paint.FontMetricsInt fontMetricsInt) {
        if (list == null || list.gifts.isEmpty()) return "";

        final HashSet<Long> giftsIds = new HashSet<>();
        final ArrayList<TLRPC.Document> gifts = new ArrayList<>();
        for (int i = 0; gifts.size() < 3 && i < list.gifts.size(); ++i) {
            final TL_stars.UserStarGift gift = list.gifts.get(i);
            if (giftsIds.contains(gift.gift.sticker.id)) continue;
            giftsIds.add(gift.gift.sticker.id);
            gifts.add(gift.gift.sticker);
        }

        if (gifts.isEmpty()) return "";
        SpannableStringBuilder ssb = new SpannableStringBuilder(" ");
        for (int i = 0; i < gifts.size(); ++i) {
            SpannableStringBuilder emoji = new SpannableStringBuilder("x");
            emoji.setSpan(new AnimatedEmojiSpan(gifts.get(i), .9f, fontMetricsInt), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.append(emoji);
        }
        return ssb;
    }

    public long getLastEmojisHash() {
        if (list == null || list.gifts.isEmpty()) return 0;

        long hash = 0;
        int giftsCount = 0;
        final HashSet<Long> giftsIds = new HashSet<>();
        for (int i = 0; giftsCount < 3 && i < list.gifts.size(); ++i) {
            final TL_stars.UserStarGift gift = list.gifts.get(i);
            if (giftsIds.contains(gift.gift.sticker.id)) continue;
            giftsIds.add(gift.gift.sticker.id);
            hash = Objects.hash(hash, gift.gift.sticker.id);
            giftsCount++;
        }

        return hash;
    }

    private int visibleHeight = AndroidUtilities.displaySize.y;
    public void setVisibleHeight(int height) {
        visibleHeight = height;
//        buttonContainer.setTranslationY(-buttonContainer.getTop() + height - dp(48 + 10 + 10 + 1f / AndroidUtilities.density));
    }

    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final int spanCount = Math.max(1, list == null || list.totalCount == 0 ? 3 : Math.min(3, list.totalCount));
        if (listView != null) {
            listView.setSpanCount(spanCount);
        }
        items.add(UItem.asSpace(dp(12)));
        if (list != null) {
            int spanCountLeft = 3;
            for (TL_stars.UserStarGift userGift : list.gifts) {
                items.add(GiftSheet.GiftCell.Factory.asStarGift(0, userGift));
                spanCountLeft--;
                if (spanCountLeft == 0) {
                    spanCountLeft = 3;
                }
            }
            if (list.loading || !list.endReached) {
                for (int i = 0; i < (spanCountLeft <= 0 ? 3 : spanCountLeft); ++i) {
                    items.add(UItem.asFlicker(i, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                }
            }
        }
        items.add(UItem.asSpace(dp(20)));
        if (userId == UserConfig.getInstance(currentAccount).getClientUserId()) {
            items.add(TextFactory.asText(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider), Gravity.CENTER, 14, LocaleController.getString(R.string.ProfileGiftsInfo), true, dp(24)));
        }
        items.add(UItem.asSpace(dp(24 + 48 + 10)));
    }

    public void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.object instanceof TL_stars.UserStarGift) {
            final TL_stars.UserStarGift userGift = (TL_stars.UserStarGift) item.object;
            StarsIntroActivity.showGiftSheet(getContext(), currentAccount, userId, userId == UserConfig.getInstance(currentAccount).getClientUserId(), userGift, resourcesProvider);
        }
    }

    public boolean onItemLongPress(UItem item, View view, int position, float x, float y) {
        return false;
    }

    public RecyclerListView getCurrentListView() {
        return listView;
    }

    public static class TextFactory extends UItem.UItemFactory<LinkSpanDrawable.LinksTextView> {
        static { setup(new TextFactory()); }

        @Override
        public LinkSpanDrawable.LinksTextView createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new LinkSpanDrawable.LinksTextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(
                        MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                        heightMeasureSpec
                    );
                }
            };
        }

        @Override
        public void bindView(View view, UItem item, boolean divider) {
            final LinkSpanDrawable.LinksTextView textView = (LinkSpanDrawable.LinksTextView) view;
            textView.setGravity(item.intValue);
            textView.setTextColor((int) item.longValue);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, item.floatValue);
            textView.setTypeface(item.checked ? null : AndroidUtilities.bold());
            textView.setPadding(item.pad, 0, item.pad, 0);
            textView.setText(item.text);
        }

        public static UItem asBoldText(int color, int gravity, float textSizeDp, CharSequence text) {
            return asText(color, gravity, textSizeDp, text, true, 0);
        }

        public static UItem asText(int color, int gravity, float textSizeDp, CharSequence text) {
            return asText(color, gravity, textSizeDp, text, false, 0);
        }

        public static UItem asText(int color, int gravity, float textSizeDp, CharSequence text, boolean bold, int padding) {
            UItem item = UItem.ofFactory(TextFactory.class);
            item.text = text;
            item.intValue = gravity;
            item.longValue = color;
            item.floatValue = textSizeDp;
            item.pad = padding;
            item.checked = bold;
            return item;
        }
    }

    public void updateColors() {
        if (button != null) {
            button.setBackground(Theme.createRoundRectDrawable(dp(8), processColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider))));
        }
    }

}
