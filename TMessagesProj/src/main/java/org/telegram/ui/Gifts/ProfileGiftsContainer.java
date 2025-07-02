package org.telegram.ui.Gifts;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Stars.StarGiftSheet.getGiftName;
import static org.telegram.ui.Stars.StarGiftSheet.isMineWithActions;

import android.content.Context;
import android.graphics.Paint;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
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
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.BackupImageView;
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
import org.telegram.ui.Components.TextHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PeerColorActivity;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stars.StarGiftSheet;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.HashMap;
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
    private final ItemTouchHelper reorder;
    private final FrameLayout buttonContainer;
    private final View buttonShadow;
    private final ButtonWithCounterView button;
    private int buttonContainerHeightDp;
    private boolean reordering;

    private final LinearLayout checkboxLayout;
    private final TextView checkboxTextView;
    private final CheckBox2 checkbox;
    private final FrameLayout bulletinContainer;

    private int checkboxRequestId = -1;

    protected int processColor(int color) {
        return color;
    }

    public ProfileGiftsContainer(BaseFragment fragment, Context context, int currentAccount, long did, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.fragment = fragment;

        this.currentAccount = currentAccount;
        if (DialogObject.isEncryptedDialog(did)) {
            TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(DialogObject.getEncryptedChatId(did));
            if (encryptedChat != null) {
                this.dialogId = encryptedChat.user_id;
            } else {
                this.dialogId = did;
            }
        } else {
            this.dialogId = did;
        }
        StarsController.getInstance(currentAccount).invalidateProfileGifts(dialogId);
        this.list = StarsController.getInstance(currentAccount).getProfileGiftsList(dialogId);
        this.list.shown = true;
        this.list.resetFilters();
        this.list.load();
        this.resourcesProvider = resourcesProvider;

        setBackgroundColor(Theme.blendOver(
            Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider),
            Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), 0.04f)
        ));

        listView = new UniversalRecyclerView(context, currentAccount, 0, false, this::fillItems, this::onItemClick, this::onItemLongPress, resourcesProvider, 3, LinearLayoutManager.VERTICAL);
        listView.adapter.setApplyBackground(false);
        listView.setSelectorType(9);
        listView.setSelectorDrawableColor(0);
        listView.setPadding(dp(9), 0, dp(9), 0);
        listView.setClipToPadding(false);
        listView.setClipChildren(false);
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (!listView.canScrollVertically(1) || isLoadingVisible()) {
                    list.load();
                }
            }
        });

        reorder = new ItemTouchHelper(new ItemTouchHelper.Callback() {
            private TL_stars.SavedStarGift getSavedGift(RecyclerView.ViewHolder holder) {
                if (holder.itemView instanceof GiftSheet.GiftCell) {
                    final GiftSheet.GiftCell cell = (GiftSheet.GiftCell) holder.itemView;
                    return cell.getSavedGift();
                }
                return null;
            }
            private boolean isPinnedAndSaved(TL_stars.SavedStarGift gift) {
                return gift != null && gift.pinned_to_top && !gift.unsaved;
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return reordering;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return reordering;
            }

            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                final TL_stars.SavedStarGift savedStarGift = getSavedGift(viewHolder);
                if (reordering && savedStarGift != null && savedStarGift.pinned_to_top) {
                    return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0);
                }
                return makeMovementFlags(0, 0);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                if (!reordering) {
                    return false;
                }
                if (!isPinnedAndSaved(getSavedGift(viewHolder)) || !isPinnedAndSaved(getSavedGift(target))) {
                    return false;
                }
                final int fromPosition = viewHolder.getAdapterPosition();
                final int toPosition = target.getAdapterPosition();
                list.reorderPinned(fromPosition - 1, toPosition - 1);
                listView.adapter.notifyItemMoved(fromPosition, toPosition);
                listView.adapter.updateWithoutNotify();
                if (fragment instanceof ProfileActivity && ((ProfileActivity) fragment).giftsView != null) {
                    ((ProfileActivity) fragment).giftsView.update();
                }
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                    list.reorderDone();
                } else {
                    if (listView != null) {
                        listView.cancelClickRunnables(false);
                    }
                    if (viewHolder != null) {
                        viewHolder.itemView.setPressed(true);
                    }
                }
                super.onSelectedChanged(viewHolder, actionState);
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                viewHolder.itemView.setPressed(false);
            }
        });
        reorder.attachToRecyclerView(listView);


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
        emptyViewTitle.setText(LocaleController.getString(R.string.ProfileGiftsNotFoundTitle));
        emptyViewLayout.addView(emptyViewTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 12, 0, 0));

        emptyViewButton = new TextView(context);
        emptyViewButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyViewButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
        emptyViewButton.setText(LocaleController.getString(R.string.ProfileGiftsNotFoundButton));
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
            final TL_stars.toggleChatStarGiftNotifications req = new TL_stars.toggleChatStarGiftNotifications();
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

        final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
        final boolean sendToSpecificDialog = dialogId < 0 || user != null && !UserObject.isUserSelf(user) && !UserObject.isBot(user);
        button = new ButtonWithCounterView(context, resourcesProvider);
        final SpannableStringBuilder sb = new SpannableStringBuilder("G " + (sendToSpecificDialog ? (dialogId < 0 ? getString(R.string.ProfileGiftsSendChannel) : formatString(R.string.ProfileGiftsSendUser, DialogObject.getShortName(dialogId))) : getString(R.string.ProfileGiftsSend)));
        final ColoredImageSpan span = new ColoredImageSpan(R.drawable.filled_gift_simple);
        sb.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        button.setText(sb, false);
        buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL, 10, 10 + 1f / AndroidUtilities.density, 10, 10));
        button.setOnClickListener(v -> {
            if (sendToSpecificDialog) {
                new GiftSheet(getContext(), currentAccount, dialogId, null, null)
                    .setBirthday(BirthdayController.getInstance(currentAccount).isToday(dialogId))
                    .show();
            } else {
                UserSelectorBottomSheet.open(UserSelectorBottomSheet.TYPE_STAR_GIFT, 0, BirthdayController.getInstance(currentAccount).getState());
            }
        });

        button.setVisibility(canSwitchNotify() ? View.GONE : View.VISIBLE);
        checkboxLayout.setVisibility(canSwitchNotify() ? View.VISIBLE : View.GONE);
        buttonContainerHeightDp = canSwitchNotify() ? 50 : 10 + 48 + 10;

        addView(bulletinContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 200, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM));
    }

    private void setReordering(boolean reordering) {
        if (this.reordering == reordering) return;
        this.reordering = reordering;
        updatedReordering(reordering);
        for (int i = 0; i < listView.getChildCount(); ++i) {
            final View child = listView.getChildAt(i);
            if (child instanceof GiftSheet.GiftCell) {
                ((GiftSheet.GiftCell) child).setReordering(reordering, true);
            }
        }
        if (listView.adapter != null) {
            listView.adapter.updateWithoutNotify();
        }
        if (reordering) {
            if (fragment instanceof ProfileActivity) {
                ((ProfileActivity) fragment).scrollToSharedMedia(true);
            }
        }
    }

    protected void updatedReordering(boolean reordering) {

    }

    public void resetReordering() {
        if (!reordering) return;
        list.sendPinnedOrder();
        setReordering(false);
    }

    public boolean isReordering() {
        return reordering;
    }

    public boolean canFilter() {
        return true;
    }

    public boolean canFilterHidden() {
        if (dialogId == UserConfig.getInstance(currentAccount).getClientUserId()) return true;
        if (dialogId >= 0) return false;
        final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        return ChatObject.canUserDoAction(chat, ChatObject.ACTION_POST);
    }

    public boolean canReorder() {
        if (dialogId >= 0) return dialogId == 0 || dialogId == UserConfig.getInstance(currentAccount).getClientUserId();
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
        resetReordering();
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

    private final static HashMap<Pair<Integer, Long>, CharSequence> cachedLastEmojis = new HashMap<>();
    public CharSequence getLastEmojis(Paint.FontMetricsInt fontMetricsInt) {
        if (list == null) return "";
        final Pair<Integer, Long> key = new Pair<>(UserConfig.selectedAccount, dialogId);
        if (list.gifts.isEmpty()) {
            if (list.loading) {
                final CharSequence cached = cachedLastEmojis.get(key);
                if (cached != null) {
                    return cached;
                }
            }
            return "";
        }

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

        cachedLastEmojis.put(key, ssb);
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
                items.add(GiftSheet.GiftCell.Factory.asStarGift(0, userGift, true).setReordering(reordering));
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
            if (reordering) {
                if (!(userGift.gift instanceof TL_stars.TL_starGiftUnique)) {
                    return;
                }
                final boolean newPinned = !userGift.pinned_to_top;
                if (newPinned && userGift.unsaved) {
                    userGift.unsaved = false;

                    final TL_stars.saveStarGift req = new TL_stars.saveStarGift();
                    req.stargift = list.getInput(userGift);
                    req.unsave = userGift.unsaved;
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, null, ConnectionsManager.RequestFlagInvokeAfter);
                }
                if (list.togglePinned(userGift, newPinned, true)) {
                    BulletinFactory.of(fragment)
                        .createSimpleBulletin(R.raw.chats_infotip, LocaleController.formatPluralStringComma("GiftsPinLimit", MessagesController.getInstance(currentAccount).stargiftsPinnedToTopLimit))
                        .show();
                }
                if (newPinned) {
                    listView.scrollToPosition(0);
                }
            } else {
                new StarGiftSheet(getContext(), currentAccount, dialogId, resourcesProvider)
                    .setOnGiftUpdatedListener(() -> {
                        if (listView != null && listView.adapter != null) {
                            listView.adapter.update(false);
                        }
                    })
                    .setOnBoughtGift((boughtGift, dialogId) -> {
                        list.gifts.remove(userGift);
                        listView.adapter.update(true);

                        if (dialogId == UserConfig.getInstance(currentAccount).getClientUserId()) {
                            BulletinFactory.of(fragment)
                                .createSimpleBulletin(boughtGift.getDocument(), getString(R.string.BoughtResoldGiftTitle), formatString(R.string.BoughtResoldGiftText, boughtGift.title + " #" + LocaleController.formatNumber(boughtGift.num, ',')))
                                .hideAfterBottomSheet(false)
                                .show();
                        } else {
                            BulletinFactory.of(fragment)
                                .createSimpleBulletin(boughtGift.getDocument(), getString(R.string.BoughtResoldGiftToTitle), formatString(R.string.BoughtResoldGiftToText, DialogObject.getShortName(currentAccount, dialogId)))
                                .hideAfterBottomSheet(false)
                                .show();
                        }
                        if (LaunchActivity.instance != null) {
                            LaunchActivity.instance.getFireworksOverlay().start(true);
                        }
                    })
                    .set(userGift, list)
                    .show();
            }
        }
    }

    public boolean onItemLongPress(UItem item, View view, int position, float x, float y) {
        if (view instanceof GiftSheet.GiftCell && item.object instanceof TL_stars.SavedStarGift) {
            final GiftSheet.GiftCell cell = (GiftSheet.GiftCell) view;
            final TL_stars.SavedStarGift savedStarGift = (TL_stars.SavedStarGift) item.object;
            final ItemOptions o = ItemOptions.makeOptions(fragment, view);
//            o.setScrimViewDrawable(cell.makeDrawable(), listView.getWidth() - dp(18), (int) Math.min(dp(220), AndroidUtilities.displaySize.y * 0.3f));
            if (savedStarGift.gift instanceof TL_stars.TL_starGiftUnique) {
                if (canReorder() && (!savedStarGift.unsaved || !savedStarGift.pinned_to_top)) {
                    o.add(savedStarGift.pinned_to_top ? R.drawable.msg_unpin : R.drawable.msg_pin, savedStarGift.pinned_to_top ? getString(R.string.Gift2Unpin) : getString(R.string.Gift2Pin), () -> {
                        if (savedStarGift.unsaved) {
                            savedStarGift.unsaved = false;
                            cell.setStarsGift(savedStarGift, true);

                            final TL_stars.saveStarGift req = new TL_stars.saveStarGift();
                            req.stargift = list.getInput(savedStarGift);
                            req.unsave = savedStarGift.unsaved;
                            ConnectionsManager.getInstance(currentAccount).sendRequest(req, null, ConnectionsManager.RequestFlagInvokeAfter);
                        }

                        final boolean newPinned = !savedStarGift.pinned_to_top;
                        if (list.togglePinned(savedStarGift, newPinned, false)) {
                            new UnpinSheet(getContext(), dialogId, savedStarGift, resourcesProvider, () -> {
                                ((GiftSheet.GiftCell) view).setPinned(newPinned, true);
                                listView.scrollToPosition(0);
                                return BulletinFactory.of(fragment);
                            }).show();
                            return;
                        } else if (newPinned) {
                            BulletinFactory.of(fragment)
                                    .createSimpleBulletin(R.raw.ic_pin, getString(R.string.Gift2PinnedTitle), getString(R.string.Gift2PinnedSubtitle))
                                    .show();
                        } else {
                            BulletinFactory.of(fragment)
                                    .createSimpleBulletin(R.raw.ic_unpin, getString(R.string.Gift2Unpinned))
                                    .show();
                        }
                        ((GiftSheet.GiftCell) view).setPinned(newPinned, true);
                        listView.scrollToPosition(0);
                    });
                    o.addIf(savedStarGift.pinned_to_top, R.drawable.tabs_reorder, getString(R.string.Gift2Reorder), () -> {
                        setReordering(true);
                    });
                }

                final TL_stars.TL_starGiftUnique gift = (TL_stars.TL_starGiftUnique) savedStarGift.gift;
                final String link;
                if (savedStarGift.gift.slug != null) {
                    link = MessagesController.getInstance(currentAccount).linkPrefix + "/nft/" + savedStarGift.gift.slug;
                } else {
                    link = null;
                }
                if (isMineWithActions(currentAccount, DialogObject.getPeerDialogId(gift.owner_id))) {
                    final boolean worn = StarGiftSheet.isWorn(currentAccount, gift);
                    o.add(worn ? R.drawable.menu_takeoff : R.drawable.menu_wear, getString(worn ? R.string.Gift2Unwear : R.string.Gift2Wear), () -> {
                        new StarGiftSheet(getContext(), currentAccount, dialogId, resourcesProvider) {
                            @Override
                            public BulletinFactory getBulletinFactory() {
                                return BulletinFactory.of(fragment);
                            }
                        }
                            .set(savedStarGift, null)
                            .toggleWear(false);
                    });
                }
                o.addIf(link != null, R.drawable.msg_link2, getString(R.string.CopyLink), () -> {
                    AndroidUtilities.addToClipboard(link);
                    BulletinFactory.of(fragment)
                        .createCopyLinkBulletin(false)
                        .show();
                });
                o.addIf(link != null, R.drawable.msg_share, getString(R.string.ShareFile), () -> {
                    new StarGiftSheet(getContext(), currentAccount, dialogId, resourcesProvider) {
                        @Override
                        public BulletinFactory getBulletinFactory() {
                            return BulletinFactory.of(fragment);
                        }
                    }
                        .set(savedStarGift, null)
                        .onSharePressed(null);
                });
            }
            if (isMineWithActions(currentAccount, dialogId)) {
                o.add(savedStarGift.unsaved ? R.drawable.msg_message : R.drawable.menu_hide_gift, getString(savedStarGift.unsaved ? R.string.Gift2ShowGift : R.string.Gift2HideGift), () -> {
                    if (savedStarGift.pinned_to_top && !savedStarGift.unsaved) {
                        cell.setPinned(false, true);
                        list.togglePinned(savedStarGift, false, false);
                    }

                    savedStarGift.unsaved = !savedStarGift.unsaved;
                    cell.setStarsGift(savedStarGift, true);

                    final TL_stars.saveStarGift req = new TL_stars.saveStarGift();
                    req.stargift = list.getInput(savedStarGift);
                    req.unsave = savedStarGift.unsaved;
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
                });
            }
            if (savedStarGift.gift instanceof TL_stars.TL_starGiftUnique) {
                final TL_stars.TL_starGiftUnique gift = (TL_stars.TL_starGiftUnique) savedStarGift.gift;
                final long selfId = UserConfig.getInstance(currentAccount).getClientUserId();
                final boolean canTransfer = DialogObject.getPeerDialogId(gift.owner_id) == selfId;
                o.addIf(canTransfer, R.drawable.menu_transfer, getString(R.string.Gift2TransferOption), () -> {
                    new StarGiftSheet(getContext(), currentAccount, dialogId, resourcesProvider) {
                        @Override
                        public BulletinFactory getBulletinFactory() {
                            return BulletinFactory.of(fragment);
                        }
                    }
                        .set(savedStarGift, null)
                        .openTransfer();
                });
            }
            if (o.getItemsCount() <= 0) {
                return false;
            }
            o.setGravity(Gravity.RIGHT);
            o.setBlur(true);
            o.allowMoveScrim();
            final int min = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y);
            o.animateToSize(min - dp(32), (int) (min * .6f));
            o.hideScrimUnder();
            o.forceBottom(true);
            o.show();
            ((GiftSheet.GiftCell) view).imageView.getImageReceiver().startAnimation(true);
            return true;
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
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
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

    public static class UnpinSheet extends BottomSheet {
        long selectedGift = 0;
        public UnpinSheet(Context context, long dialogId, TL_stars.SavedStarGift newPinned, Theme.ResourcesProvider resourcesProvider, Utilities.Callback0Return<BulletinFactory> whenDone) {
            super(context, false, resourcesProvider);
            fixNavigationBar();

            final LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);

            final TextView titleView = TextHelper.makeTextView(context, 20, Theme.key_windowBackgroundWhiteBlackText, true, resourcesProvider);
            titleView.setText(getString(R.string.Gift2UnpinAlertTitle));
            layout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 12, 22, 0));

            final TextView subtitleView = TextHelper.makeTextView(context, 14, Theme.key_windowBackgroundWhiteGrayText, false, resourcesProvider);
            subtitleView.setText(getString(R.string.Gift2UnpinAlertSubtitle));
            layout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 4.33f, 22, 10));

            final ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider);

            final StarsController.GiftsList giftsList = StarsController.getInstance(currentAccount).getProfileGiftsList(dialogId);
            final UniversalRecyclerView listView = new UniversalRecyclerView(context, currentAccount, 0, (items, adapter) -> {
                for (TL_stars.SavedStarGift g : giftsList.gifts) {
                    if (g.pinned_to_top) {
                        items.add(PeerColorActivity.GiftCell.Factory.asGiftCell(g).setChecked(selectedGift == g.gift.id).setSpanCount(1));
                    }
                }
            }, (item, view, position, x, y) -> {
                final long id = ((TL_stars.SavedStarGift) item.object).gift.id;
                if (selectedGift == id) {
                    selectedGift = 0;
                } else {
                    selectedGift = id;
                }
                button.setEnabled(selectedGift != 0);
                if (view.getParent() instanceof ViewGroup) {
                    final ViewGroup p = (ViewGroup) view.getParent();
                    for (int i = 0; i < p.getChildCount(); ++i) {
                        final View child = p.getChildAt(i);
                        if (child instanceof PeerColorActivity.GiftCell) {
                            ((PeerColorActivity.GiftCell) child).setSelected(selectedGift == ((PeerColorActivity.GiftCell) child).getGiftId(), true);
                        }
                    }
                }
            }, null, resourcesProvider) {
                @Override
                public Integer getSelectorColor(int position) {
                    return 0;
                }
            };
            listView.setSpanCount(3);
            listView.setOverScrollMode(OVER_SCROLL_NEVER);
            listView.setScrollEnabled(false);
            layout.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 11, 0, 11, 0));

            button.setText(getString(R.string.Gift2UnpinAlertButton), false);
            layout.addView(button, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 22, 9, 22, 9));
            button.setEnabled(false);
            button.setOnClickListener(v -> {
                final ArrayList<TL_stars.SavedStarGift> pinned = giftsList.getPinned();
                int index = -1;
                TL_stars.SavedStarGift replacing = null;
                for (int i = 0; i < pinned.size(); ++i) {
                    if (pinned.get(i).gift.id == selectedGift) {
                        index = i;
                        replacing = pinned.get(i);
                        break;
                    }
                }
                if (replacing == null) return;

                replacing.pinned_to_top = false;
                pinned.set(index, newPinned);
                newPinned.pinned_to_top = true;
                giftsList.setPinned(pinned);

                dismiss();
                whenDone.run().createSimpleBulletin(R.raw.ic_pin, formatString(R.string.Gift2ReplacedPinTitle, getGiftName(newPinned.gift)), formatString(R.string.Gift2ReplacedPinSubtitle, getGiftName(replacing.gift))).show();
            });

            setCustomView(layout);
        }
    }

}
