/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.jetbrains.annotations.NotNull;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.Cells.DrawerActionCell;
import org.telegram.ui.Cells.DrawerAddCell;
import org.telegram.ui.Cells.DrawerProfileCell;
import org.telegram.ui.Cells.DrawerUserCell;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SideMenultItemAnimator;

import java.util.ArrayList;
import java.util.Collections;

public class DrawerLayoutAdapter extends RecyclerListView.SelectionAdapter {
    private static final int VIEW_TYPE_PROFILE = 0;
    private static final int VIEW_TYPE_EMPTY = 1;
    private static final int VIEW_TYPE_DIVIDER = 2;
    private static final int VIEW_TYPE_ACTION = 3;
    private static final int VIEW_TYPE_ACCOUNT = 4;
    private static final int VIEW_TYPE_ADD_ACCOUNT = 5;

    private final Context mContext;
    private final DrawerLayoutContainer mDrawerLayoutContainer;
    private boolean accountsShown;
    public DrawerProfileCell profileCell;
    private final SideMenultItemAnimator itemAnimator;
    private RecyclerView attachedRecyclerView;

    // Use array for fastest access
    private AbstractItem[] items = new AbstractItem[0];
    private AbstractItem[] lastItems = new AbstractItem[0];

    public DrawerLayoutAdapter(Context context, SideMenultItemAnimator animator, DrawerLayoutContainer drawerLayoutContainer) {
        mContext = context;
        mDrawerLayoutContainer = drawerLayoutContainer;
        itemAnimator = animator;
        accountsShown = UserConfig.getActivatedAccountsCount() > 1 && MessagesController.getGlobalMainSettings().getBoolean("accountsShown", true);
        Theme.createCommonDialogResources(context);
        resetItems();
    }

    @Override
    public int getItemCount() {
        return items.length;
    }

    public void setAccountsShown(boolean value, boolean animated) {
        if (accountsShown == value || itemAnimator.isRunning()) {
            return;
        }
        accountsShown = value;
        MessagesController.getGlobalMainSettings().edit().putBoolean("accountsShown", accountsShown).commit();
        if (profileCell != null) {
            profileCell.setAccountsShown(accountsShown, false);
        }
        if (animated) {
            itemAnimator.setShouldClipChildren(false);
        }
        redrawAdapterData();
    }

    public boolean isAccountsShown() {
        return accountsShown;
    }

    private View.OnClickListener onPremiumDrawableClick;

    public void setOnPremiumDrawableClick(View.OnClickListener listener) {
        onPremiumDrawableClick = listener;
    }

    /**
     * @deprecated Use {@link #redrawAdapterData()} instead for better performance with DiffUtil
     */
    @Deprecated(since = "12.1.1", forRemoval = true)
    @Override
    public void notifyDataSetChanged() {
        redrawAdapterData();
    }

    /**
     * Updates the RecyclerView's data set and efficiently refreshes the view using DiffUtil to calculate the minimal set of changes
     * based on the differences between the previous and current data. After the update, the current data set is saved as the previous data set
     * for the next DiffUtil comparison.
     */
    public void redrawAdapterData() {
        resetItems();
        FullDrawerDiffCallback diffCallback = new FullDrawerDiffCallback(lastItems, items);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(diffCallback, true); // detectMoves = true for better accuracy
        lastItems = items.clone(); // fast clone

        if (profileCell != null) {
            int currentAccount = UserConfig.selectedAccount;
            profileCell.setUser(
                    MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId()),
                    accountsShown
            );
        }

        if (attachedRecyclerView != null) {
            attachedRecyclerView.post(() -> diff.dispatchUpdatesTo(DrawerLayoutAdapter.this));
        }
    }

    @Override
    public void onAttachedToRecyclerView(@NotNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        attachedRecyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NotNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        attachedRecyclerView = null;
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        int position = holder.getAdapterPosition();
        if (position == RecyclerView.NO_POSITION || position >= items.length) {
            return false;
        }
        int viewType = items[position].viewType;
        return viewType == VIEW_TYPE_ACTION || viewType == VIEW_TYPE_ACCOUNT || viewType == VIEW_TYPE_ADD_ACCOUNT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (attachedRecyclerView == null && parent instanceof RecyclerView) {
            attachedRecyclerView = (RecyclerView) parent;
        }

        View view;
        switch (viewType) {
            case VIEW_TYPE_PROFILE:
                view = profileCell = new DrawerProfileCell(mContext, mDrawerLayoutContainer) {
                    @Override
                    protected void onPremiumClick() {
                        if (onPremiumDrawableClick != null) {
                            onPremiumDrawableClick.onClick(this);
                        }
                    }
                };
                break;
            case VIEW_TYPE_DIVIDER:
                view = new DividerCell(mContext);
                break;
            case VIEW_TYPE_ACTION:
                view = new DrawerActionCell(mContext);
                break;
            case VIEW_TYPE_ACCOUNT:
                view = new DrawerUserCell(mContext);
                break;
            case VIEW_TYPE_ADD_ACCOUNT:
                view = new DrawerAddCell(mContext);
                break;
            case VIEW_TYPE_EMPTY:
            default:
                view = new EmptyCell(mContext, AndroidUtilities.dp(8));
                break;
        }
        view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        AbstractItem item = items[position];
        if (item instanceof ProfileItem) {
            DrawerProfileCell cell = (DrawerProfileCell) holder.itemView;
            int currentAccount = UserConfig.selectedAccount;
            cell.setUser(
                    MessagesController.getInstance(currentAccount).getUser(UserConfig.getInstance(currentAccount).getClientUserId()),
                    accountsShown
            );
        } else if (item instanceof AccountItem) {
            ((DrawerUserCell) holder.itemView).setAccount(((AccountItem) item).accountIndex);
        } else if (item instanceof Item) {
            ((Item) item).bind((DrawerActionCell) holder.itemView);
            holder.itemView.setPadding(0, 0, 0, 0);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return items[position].viewType;
    }

    public void swapElements(int fromIndex, int toIndex) {
        if (fromIndex < 0 || toIndex < 0 || fromIndex >= items.length || toIndex >= items.length) {
            return;
        }
        AbstractItem from = items[fromIndex];
        AbstractItem to = items[toIndex];
        if (!(from instanceof AccountItem) || !(to instanceof AccountItem)) {
            return;
        }
        AccountItem fromAccount = (AccountItem) from;
        AccountItem toAccount = (AccountItem) to;

        final UserConfig userConfig1 = UserConfig.getInstance(fromAccount.accountIndex);
        final UserConfig userConfig2 = UserConfig.getInstance(toAccount.accountIndex);
        final int tempLoginTime = userConfig1.loginTime;
        userConfig1.loginTime = userConfig2.loginTime;
        userConfig2.loginTime = tempLoginTime;
        userConfig1.saveConfig(false);
        userConfig2.saveConfig(false);

        // Swap in array
        AbstractItem[] newItems = items.clone();
        newItems[fromIndex] = to;
        newItems[toIndex] = from;
        items = newItems;

        notifyItemMoved(fromIndex, toIndex);
    }

    private void resetItems() {
        // Estimate initial size to avoid reallocations
        int estimatedSize = 15; // base items
        if (accountsShown) {
            estimatedSize += UserConfig.MAX_ACCOUNT_COUNT + 2; // accounts + add + divider
        }
        ArrayList<AbstractItem> tempItems = new ArrayList<>(estimatedSize);

        tempItems.add(new ProfileItem());
        tempItems.add(new EmptyItem());

        if (accountsShown) {
            ArrayList<Integer> accountNumbers = new ArrayList<>();
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    accountNumbers.add(a);
                }
            }
            Collections.sort(accountNumbers, (o1, o2) -> {
                long l1 = UserConfig.getInstance(o1).loginTime;
                long l2 = UserConfig.getInstance(o2).loginTime;
                return Long.compare(l1, l2);
            });

            for (int account : accountNumbers) {
                tempItems.add(new AccountItem(account));
            }

            if (accountNumbers.size() < UserConfig.MAX_ACCOUNT_COUNT) {
                tempItems.add(new AddAccountItem());
            }

            tempItems.add(new DividerItem());
        }

        int currentAccount = UserConfig.selectedAccount;
        if (!UserConfig.getInstance(currentAccount).isClientActivated()) {
            items = tempItems.toArray(new AbstractItem[0]);
            return;
        }

        int eventType = Theme.getEventType();
        int newGroupIcon;
//        int newSecretIcon;
//        int newChannelIcon;
        int contactsIcon;
        int callsIcon;
        int savedIcon;
        int settingsIcon;
        int inviteIcon;
        int helpIcon;
        if (eventType == 0) {
            newGroupIcon = R.drawable.msg_groups_ny;
            //newSecretIcon = R.drawable.msg_secret_ny;
            //newChannelIcon = R.drawable.msg_channel_ny;
            contactsIcon = R.drawable.msg_contacts_ny;
            callsIcon = R.drawable.msg_calls_ny;
            savedIcon = R.drawable.msg_saved_ny;
            settingsIcon = R.drawable.msg_settings_ny;
            inviteIcon = R.drawable.msg_invite_ny;
            helpIcon = R.drawable.msg_help_ny;
        } else if (eventType == 1) {
            newGroupIcon = R.drawable.msg_groups_14;
            //newSecretIcon = R.drawable.msg_secret_14;
            //newChannelIcon = R.drawable.msg_channel_14;
            contactsIcon = R.drawable.msg_contacts_14;
            callsIcon = R.drawable.msg_calls_14;
            savedIcon = R.drawable.msg_saved_14;
            settingsIcon = R.drawable.msg_settings_14;
            inviteIcon = R.drawable.msg_secret_ny;
            helpIcon = R.drawable.msg_help;
        } else if (eventType == 2) {
            newGroupIcon = R.drawable.msg_groups_hw;
            //newSecretIcon = R.drawable.msg_secret_hw;
            //newChannelIcon = R.drawable.msg_channel_hw;
            contactsIcon = R.drawable.msg_contacts_hw;
            callsIcon = R.drawable.msg_calls_hw;
            savedIcon = R.drawable.msg_saved_hw;
            settingsIcon = R.drawable.msg_settings_hw;
            inviteIcon = R.drawable.msg_invite_hw;
            helpIcon = R.drawable.msg_help_hw;
        } else {
            newGroupIcon = R.drawable.msg_groups;
            //newSecretIcon = R.drawable.msg_secret;
            //newChannelIcon = R.drawable.msg_channel;
            contactsIcon = R.drawable.msg_contacts;
            callsIcon = R.drawable.msg_calls;
            savedIcon = R.drawable.msg_saved;
            settingsIcon = R.drawable.msg_settings_old;
            inviteIcon = R.drawable.msg_invite;
            helpIcon = R.drawable.msg_help;
        }

        UserConfig me = UserConfig.getInstance(currentAccount);
        ArrayList<Item> mainMenuItems = new ArrayList<>(5);

        mainMenuItems.add(new Item(16, LocaleController.getString(R.string.MyProfile), R.drawable.left_status_profile));
        if (me != null && me.isPremium()) {
            if (me.getEmojiStatus() != null) {
                mainMenuItems.add(new Item(15, LocaleController.getString(R.string.ChangeEmojiStatus), R.drawable.msg_status_edit));
            } else {
                mainMenuItems.add(new Item(15, LocaleController.getString(R.string.SetEmojiStatus), R.drawable.msg_status_set));
            }
        }
//        if (MessagesController.getInstance(UserConfig.selectedAccount).storiesEnabled()) {
//            items.add(new Item(17, LocaleController.getString(R.string.ProfileStories), R.drawable.msg_menu_stories));
//            showDivider = true;
//        }
        if (ApplicationLoader.applicationLoaderInstance != null) {
            ApplicationLoader.applicationLoaderInstance.extendDrawer(mainMenuItems);
        }

        TLRPC.TL_attachMenuBots menuBots = MediaDataController.getInstance(currentAccount).getAttachMenuBots();
        if (menuBots != null && menuBots.bots != null) {
            for (int i = 0, size = menuBots.bots.size(); i < size; i++) {
                TLRPC.TL_attachMenuBot bot = menuBots.bots.get(i);
                if (bot.show_in_side_menu) {
                    mainMenuItems.add(new Item(bot));
                }
            }
        }

        for (int i = 0, size = mainMenuItems.size(); i < size; i++) {
            tempItems.add(mainMenuItems.get(i));
        }
        tempItems.add(new DividerItem());

        tempItems.add(new Item(2, LocaleController.getString(R.string.NewGroup), newGroupIcon));
        //tempItems.add(new Item(3, LocaleController.getString(R.string.NewSecretChat), newSecretIcon));
        //tempItems.add(new Item(4, LocaleController.getString(R.string.NewChannel), newChannelIcon));
        tempItems.add(new Item(6, LocaleController.getString(R.string.Contacts), contactsIcon));
        tempItems.add(new Item(10, LocaleController.getString(R.string.Calls), callsIcon));
        tempItems.add(new Item(11, LocaleController.getString(R.string.SavedMessages), savedIcon));
        tempItems.add(new Item(8, LocaleController.getString(R.string.Settings), settingsIcon));
        tempItems.add(new DividerItem());
        tempItems.add(new Item(7, LocaleController.getString(R.string.InviteFriends), inviteIcon));
        tempItems.add(new Item(13, LocaleController.getString(R.string.TelegramFeatures), helpIcon));

        items = tempItems.toArray(new AbstractItem[0]);
    }

    public boolean click(View view, int position) {
        if (position < 0 || position >= items.length) return false;
        AbstractItem item = items[position];
        if (item instanceof Item) {
            Item actionItem = (Item) item;
            if (actionItem.listener != null) {
                actionItem.listener.onClick(view);
                return true;
            }
        }
        return false;
    }

    public int getId(int position) {
        if (position < 0 || position >= items.length) return -1;
        AbstractItem item = items[position];
        if (item instanceof Item) {
            return ((Item) item).id;
        }
        return -1;
    }

    public int getFirstAccountPosition() {
        for (int i = 0, size = items.length; i < size; i++) {
            if (items[i] instanceof AccountItem) {
                return i;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    public int getLastAccountPosition() {
        for (int i = items.length - 1; i >= 0; i--) {
            if (items[i] instanceof AccountItem) {
                return i;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    public TLRPC.TL_attachMenuBot getAttachMenuBot(int position) {
        if (position < 0 || position >= items.length) return null;
        AbstractItem item = items[position];
        if (item instanceof Item) {
            return ((Item) item).bot;
        }
        return null;
    }

    public static class FullDrawerDiffCallback extends DiffUtil.Callback {
        private final AbstractItem[] oldList;
        private final AbstractItem[] newList;

        public FullDrawerDiffCallback(AbstractItem[] oldList, AbstractItem[] newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.length;
        }

        @Override
        public int getNewListSize() {
            return newList.length;
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            AbstractItem oldItem = oldList[oldItemPosition];
            AbstractItem newItem = newList[newItemPosition];
            if (oldItem.viewType != newItem.viewType) return false;

            if (oldItem instanceof Item && newItem instanceof Item) {
                return ((Item) oldItem).id == ((Item) newItem).id;
            }
            if (oldItem instanceof AccountItem && newItem instanceof AccountItem) {
                return ((AccountItem) oldItem).accountIndex == ((AccountItem) newItem).accountIndex;
            }
            return true;
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            AbstractItem oldItem = oldList[oldItemPosition];
            AbstractItem newItem = newList[newItemPosition];

            if (oldItem instanceof Item && newItem instanceof Item) {
                return ((Item) oldItem).areContentsTheSame((Item) newItem);
            }
            return true;
        }
    }

    // =============== Base element ===============

    public abstract static class AbstractItem {
        final int viewType;

        AbstractItem(int viewType) {
            this.viewType = viewType;
        }
    }

    private static class ProfileItem extends AbstractItem {
        ProfileItem() {
            super(VIEW_TYPE_PROFILE);
        }
    }

    private static class EmptyItem extends AbstractItem {
        EmptyItem() {
            super(VIEW_TYPE_EMPTY);
        }
    }

    private static class DividerItem extends AbstractItem {
        DividerItem() {
            super(VIEW_TYPE_DIVIDER);
        }
    }

    private static class AddAccountItem extends AbstractItem {
        AddAccountItem() {
            super(VIEW_TYPE_ADD_ACCOUNT);
        }
    }

    private static class AccountItem extends AbstractItem {
        final int accountIndex;

        AccountItem(int accountIndex) {
            super(VIEW_TYPE_ACCOUNT);
            this.accountIndex = accountIndex;
        }
    }

    public static class Item extends AbstractItem {
        public int icon;
        public CharSequence text;
        public int id;
        TLRPC.TL_attachMenuBot bot;
        View.OnClickListener listener;
        public boolean error;

        public Item(int id, CharSequence text, int icon) {
            super(VIEW_TYPE_ACTION);
            this.icon = icon;
            this.id = id;
            this.text = text;
        }

        /**
         * Compares the contents of this Item with another Item to determine if they are visually the same.
         * This method is used by DiffUtil to determine if an item in the RecyclerView has been modified
         * and needs to be updated.
         *
         * @param obj The Item to compare this Item to.  Returns false if obj is null.
         * @return True if the contents of both Items are the same, false otherwise.
         */
        public boolean areContentsTheSame(Item obj) {
            if (obj == null) return false;
            if (this.icon != obj.icon) return false;
            if (this.error != obj.error) return false;
            if (this.bot != obj.bot) return false;
            return android.text.TextUtils.equals(this.text, obj.text);
        }

        public Item(TLRPC.TL_attachMenuBot bot) {
            super(VIEW_TYPE_ACTION);
            this.bot = bot;
            this.id = (int) (100 + (bot.bot_id >> 16));
        }

        public void bind(DrawerActionCell actionCell) {
            if (this.bot != null) {
                actionCell.setBot(bot);
            } else {
                actionCell.setTextAndIcon(id, text, icon);
            }
            actionCell.setError(error);
        }

        @Keep
        public Item onClick(View.OnClickListener listener) {
            this.listener = listener;
            return this;
        }

        @Keep
        public Item withError() {
            this.error = true;
            return this;
        }
    }
}