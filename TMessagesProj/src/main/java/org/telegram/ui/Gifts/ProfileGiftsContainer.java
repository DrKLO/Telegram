package org.telegram.ui.Gifts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Paint;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BirthdayController;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.Premium.boosts.UserSelectorBottomSheet;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Stars.StarGiftSheet;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;

public class ProfileGiftsContainer extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private final BaseFragment fragment;
    private final int currentAccount;
    private final long dialogId;
    private final StarsController.GiftsList list;
    private final Theme.ResourcesProvider resourcesProvider;

    private final FrameLayout emptyView;
    private final TextView emptyViewTitle;
    private final TextView emptyViewButton;

    private final UniversalRecyclerView listView;
    private final FrameLayout buttonContainer;
    private final View buttonShadow;
    private final ButtonWithCounterView button;
    private int buttonContainerHeightDp;

    private final LinearLayout checkboxLayout;
    private final TextView checkboxTextView;
    private final CheckBox2 checkbox;
    private final FrameLayout bulletinContainer;

    private int checkboxRequestId = -1;

    protected int processColor(int color) {
        return color;
    }

    public ProfileGiftsContainer(BaseFragment fragment, Context context, int currentAccount, long dialogId, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.fragment = fragment;

        this.currentAccount = currentAccount;
        this.dialogId = dialogId;
        this.list = StarsController.getInstance(currentAccount).getProfileGiftsList(dialogId);
        this.list.shown = true;
        this.list.resetFilters();
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

        emptyView = new FrameLayout(context);

        LinearLayout emptyViewLayout = new LinearLayout(context);
        emptyViewLayout.setOrientation(LinearLayout.VERTICAL);
        emptyView.addView(emptyViewLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        BackupImageView emptyViewImage = new BackupImageView(context);
        emptyViewImage.setImageDrawable(new RLottieDrawable(R.raw.utyan_empty, "utyan_empty", dp(120), dp(120)));
        emptyViewLayout.addView(emptyViewImage, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0));

        emptyViewTitle = new TextView(context);
        emptyViewTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        emptyViewTitle.setTypeface(AndroidUtilities.bold());
        emptyViewTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        emptyViewTitle.setText("No matching gifts");
        emptyViewLayout.addView(emptyViewTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));

        emptyViewButton = new TextView(context);
        emptyViewButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyViewButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
        emptyViewButton.setText("View All Gifts");
        emptyViewButton.setOnClickListener(v -> {
            list.resetFilters();
        });
        emptyViewButton.setPadding(dp(10), dp(4), dp(10), dp(4));
        emptyViewButton.setBackground(Theme.createRadSelectorDrawable(Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), .10f), 4, 4));
        ScaleStateListAnimator.apply(emptyViewButton);
        emptyViewLayout.addView(emptyViewButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));

        addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        listView.setEmptyView(emptyView);

        buttonContainer = new FrameLayout(context);
        buttonContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM));

        buttonShadow = new View(context);
        buttonShadow.setBackgroundColor(Theme.getColor(Theme.key_dialogGrayLine, resourcesProvider));
        buttonContainer.addView(buttonShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1f / AndroidUtilities.density, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        bulletinContainer = new FrameLayout(context);

        checkboxLayout = new LinearLayout(context);
        checkboxLayout.setPadding(dp(12), dp(8), dp(12), dp(8));
        checkboxLayout.setClipToPadding(false);
        checkboxLayout.setOrientation(LinearLayout.HORIZONTAL);
        checkboxLayout.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 6, 6));
        checkbox = new CheckBox2(context, 24, resourcesProvider);
        checkbox.setColor(Theme.key_radioBackgroundChecked, Theme.key_checkboxDisabled, Theme.key_checkboxCheck);
        checkbox.setDrawUnchecked(true);
        checkbox.setChecked(false, false);
        checkbox.setDrawBackgroundAsArc(10);
        checkboxLayout.addView(checkbox, LayoutHelper.createLinear(26, 26, Gravity.CENTER_VERTICAL, 0, 0, 0, 0));
        checkboxTextView = new TextView(context);
        checkboxTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        checkboxTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        checkboxTextView.setText(LocaleController.getString(R.string.Gift2ChannelNotify));
        checkboxLayout.addView(checkboxTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 9, 0, 0, 0));
        buttonContainer.addView(checkboxLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 38, Gravity.CENTER, 0, 1f / AndroidUtilities.density + 6, 0, 6));
        ScaleStateListAnimator.apply(checkboxLayout, 0.025f, 1.5f);
        checkboxLayout.setOnClickListener(v -> {
            checkbox.setChecked(!checkbox.isChecked(), true);
            final boolean willBeNotified = checkbox.isChecked();
            BulletinFactory.of(bulletinContainer, resourcesProvider)
                .createSimpleBulletinDetail(willBeNotified ? R.raw.silent_unmute : R.raw.silent_mute, getString(willBeNotified ? R.string.Gift2ChannelNotifyChecked : R.string.Gift2ChannelNotifyNotChecked))
                .show();

            list.chat_notifications_enabled = willBeNotified;
            if (checkboxRequestId >= 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(checkboxRequestId, true);
                checkboxRequestId = -1;
            }
            TL_stars.toggleChatStarGiftNotifications req = new TL_stars.toggleChatStarGiftNotifications();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
            req.enabled = willBeNotified;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                checkboxRequestId = -1;
                if (err != null) {
                    BulletinFactory.of(bulletinContainer, resourcesProvider).showForError(err);
                }
            }));
        });
        if (list.chat_notifications_enabled != null) {
            checkbox.setChecked(list.chat_notifications_enabled, false);
        }

        button = new ButtonWithCounterView(context, resourcesProvider);
        final SpannableStringBuilder sb = new SpannableStringBuilder("G " + LocaleController.getString(dialogId < 0 ? R.string.ProfileGiftsSendChannel : R.string.ProfileGiftsSend));
        final ColoredImageSpan span = new ColoredImageSpan(R.drawable.filled_gift_simple);
        sb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        button.setText(sb, false);
        buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL, 10, 10 + 1f / AndroidUtilities.density, 10, 10));
        button.setOnClickListener(v -> {
            UserSelectorBottomSheet.open(UserSelectorBottomSheet.TYPE_STAR_GIFT, 0, BirthdayController.getInstance(currentAccount).getState());
        });

        button.setVisibility(canSwitchNotify() ? View.GONE : View.VISIBLE);
        checkboxLayout.setVisibility(canSwitchNotify() ? View.VISIBLE : View.GONE);
        buttonContainerHeightDp = canSwitchNotify() ? 50 : 10 + 48 + 10;

        buttonContainer.setVisibility(dialogId >= 0 || ChatObject.canUserDoAction(MessagesController.getInstance(currentAccount).getChat(-dialogId), ChatObject.ACTION_POST) ? View.VISIBLE : View.GONE);

        addView(bulletinContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM));
    }

    public boolean canFilter() {
        if (dialogId >= 0) return false;
        final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        return ChatObject.canUserDoAction(chat, ChatObject.ACTION_POST);
    }

    public boolean canSwitchNotify() {
        if (dialogId >= 0) return false;
        return list.chat_notifications_enabled != null;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.starUserGiftsLoaded) {
            if ((Long) args[0] == dialogId) {
                button.setVisibility(canSwitchNotify() ? View.GONE : View.VISIBLE);
                checkboxLayout.setVisibility(canSwitchNotify() ? View.VISIBLE : View.GONE);
                buttonContainerHeightDp = canSwitchNotify() ? 50 : 10 + 48 + 10;
                if (list.chat_notifications_enabled != null) {
                    checkbox.setChecked(list.chat_notifications_enabled, true);
                }

                if (listView != null && listView.adapter != null) {
                    listView.adapter.update(true);
                }
                if (!listView.canScrollVertically(1) || isLoadingVisible()) {
                    list.load();
                }
            }
        } else if (id == NotificationCenter.updateInterfaces) {
            button.setVisibility(canSwitchNotify() ? View.GONE : View.VISIBLE);
            checkboxLayout.setVisibility(canSwitchNotify() ? View.VISIBLE : View.GONE);
            buttonContainerHeightDp = canSwitchNotify() ? 50 : 10 + 48 + 10;
            setVisibleHeight(visibleHeight);
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
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
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
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        if (list != null) {
            list.shown = false;
        }
    }

    public StarsController.GiftsList getList() {
        return list;
    }

    public int getGiftsCount() {
        if (list != null && list.totalCount > 0) return list.totalCount;
        if (dialogId >= 0) {
            final TLRPC.UserFull userFull = MessagesController.getInstance(currentAccount).getUserFull(dialogId);
            return userFull != null ? userFull.stargifts_count : 0;
        } else {
            final TLRPC.ChatFull chatFull = MessagesController.getInstance(currentAccount).getChatFull(-dialogId);
            return chatFull != null ? chatFull.stargifts_count : 0;
        }
    }

    public CharSequence getLastEmojis(Paint.FontMetricsInt fontMetricsInt) {
        if (list == null || list.gifts.isEmpty()) return "";

        final HashSet<Long> giftsIds = new HashSet<>();
        final ArrayList<TLRPC.Document> gifts = new ArrayList<>();
        for (int i = 0; gifts.size() < 3 && i < list.gifts.size(); ++i) {
            final TL_stars.SavedStarGift gift = list.gifts.get(i);
            final TLRPC.Document doc = gift.gift.getDocument();
            if (doc == null) continue;
            if (giftsIds.contains(doc.id)) continue;
            giftsIds.add(doc.id);
            gifts.add(doc);
        }

        if (gifts.isEmpty()) return "";
        final SpannableStringBuilder ssb = new SpannableStringBuilder(" ");
        for (int i = 0; i < gifts.size(); ++i) {
            final SpannableStringBuilder emoji = new SpannableStringBuilder("x");
            final TLRPC.Document sticker = gifts.get(i);
            final AnimatedEmojiSpan span = new AnimatedEmojiSpan(sticker, .9f, fontMetricsInt);
            emoji.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
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
            final TL_stars.SavedStarGift gift = list.gifts.get(i);
            final TLRPC.Document doc = gift.gift.getDocument();
            if (doc == null) continue;
            giftsIds.add(doc.id);
            hash = Objects.hash(hash, doc.id);
            giftsCount++;
        }

        return hash;
    }

    private int visibleHeight = AndroidUtilities.displaySize.y;
    public void setVisibleHeight(int height) {
        visibleHeight = height;
        if (canSwitchNotify()) {
            bulletinContainer.setTranslationY(-bulletinContainer.getTop() + height - dp(buttonContainerHeightDp) - 1 - dp(200));
            buttonContainer.setTranslationY(-buttonContainer.getTop() + height - dp(buttonContainerHeightDp) - 1);
        } else {
            bulletinContainer.setTranslationY(0);
            buttonContainer.setTranslationY(0);
        }
    }

    public void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (list.hasFilters() && list.gifts.size() <= 0 && list.endReached && !list.loading) {
            return;
        }
        final int spanCount = Math.max(1, list == null || list.totalCount == 0 ? 3 : Math.min(3, list.totalCount));
        if (listView != null) {
            listView.setSpanCount(spanCount);
        }
        items.add(UItem.asSpace(dp(12)));
        if (list != null) {
            int spanCountLeft = 3;
            for (TL_stars.SavedStarGift userGift : list.gifts) {
                items.add(GiftSheet.GiftCell.Factory.asStarGift(0, userGift, true));
                spanCountLeft--;
                if (spanCountLeft == 0) {
                    spanCountLeft = 3;
                }
            }
            if (list.loading || !list.endReached) {
                for (int i = 0; i < (spanCountLeft <= 0 ? 3 : spanCountLeft); ++i) {
                    items.add(UItem.asFlicker(1 + i, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
                }
            }
        }
        items.add(UItem.asSpace(dp(20)));
        if (dialogId == UserConfig.getInstance(currentAccount).getClientUserId()) {
            items.add(TextFactory.asText(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider), Gravity.CENTER, 14, LocaleController.getString(R.string.ProfileGiftsInfo), true, dp(24)));
        }
        items.add(UItem.asSpace(dp(24 + 48 + 10)));
    }

    public void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.object instanceof TL_stars.SavedStarGift) {
            final TL_stars.SavedStarGift userGift = (TL_stars.SavedStarGift) item.object;
            new StarGiftSheet(getContext(), currentAccount, dialogId, resourcesProvider)
                .set(userGift)
                .show();
        }
    }

    public boolean onItemLongPress(UItem item, View view, int position, float x, float y) {
        if (item.object instanceof TL_stars.SavedStarGift) {
            final TL_stars.SavedStarGift savedStarGift = (TL_stars.SavedStarGift) item.object;
            if (savedStarGift.gift instanceof TL_stars.TL_starGiftUnique) {
                final String link;
                if (savedStarGift.gift.slug != null) {
                    link = MessagesController.getInstance(currentAccount).linkPrefix + "/nft/" + savedStarGift.gift.slug;
                } else {
                    link = null;
                }
                final TL_stars.TL_starGiftUnique gift = (TL_stars.TL_starGiftUnique) savedStarGift.gift;
                final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
                final boolean canTransfer = DialogObject.getPeerDialogId(gift.owner_id) == selfId;
                final boolean myProfile = dialogId == selfId;
                final ItemOptions o = ItemOptions.makeOptions(fragment, view);
                o.addIf(link != null, R.drawable.msg_link, getString(R.string.CopyLink), () -> {
                    AndroidUtilities.addToClipboard(link);
                    BulletinFactory.of(fragment)
                        .createCopyLinkBulletin(false)
                        .show();
                });
                o.addIf(link != null, R.drawable.msg_share, getString(R.string.ShareFile), () -> {
                    new StarGiftSheet(getContext(), currentAccount, dialogId, resourcesProvider) {
                        @Override
                        protected BulletinFactory getBulletinFactory() {
                            return BulletinFactory.of(fragment);
                        }
                    }
                    .set(savedStarGift)
                    .onSharePressed(null);
                });
                o.addIf(canTransfer, R.drawable.menu_feature_transfer, getString(R.string.Gift2TransferOption), () -> {
                    new StarGiftSheet(getContext(), currentAccount, dialogId, resourcesProvider) {
                        @Override
                        protected BulletinFactory getBulletinFactory() {
                            return BulletinFactory.of(fragment);
                        }
                    }
                        .set(savedStarGift)
                        .openTransfer();
                });
                if (o.getItemsCount() <= 0) {
                    return false;
                }
                o.setGravity(Gravity.LEFT);
                o.setBlur(true);
                o.show();
                return true;
            }
        }
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
        button.updateColors();
        button.setBackground(Theme.createRoundRectDrawable(dp(8), processColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider))));
        emptyViewTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        emptyViewButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
        emptyViewButton.setBackground(Theme.createRadSelectorDrawable(Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider), .10f), 4, 4));
        buttonContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        buttonShadow.setBackgroundColor(Theme.getColor(Theme.key_dialogGrayLine, resourcesProvider));
        checkboxTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        checkboxLayout.setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 6, 6));
    }

}
