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
import java.util.List;

public class DrawerLayoutAdapter extends RecyclerListView.SelectionAdapter {
    private static final int VIEW_TYPE_PROFILE = 0;
    private static final int VIEW_TYPE_EMPTY = 1;
    private static final int VIEW_TYPE_DIVIDER = 2;
    private static final int VIEW_TYPE_ACTION = 3;
    private static final int VIEW_TYPE_ACCOUNT = 4;
    private static final int VIEW_TYPE_ADD_ACCOUNT = 5;

    private Context mContext;
    private DrawerLayoutContainer mDrawerLayoutContainer;
    private boolean accountsShown;
    public DrawerProfileCell profileCell;
    private SideMenultItemAnimator itemAnimator;
    private RecyclerView attachedRecyclerView;

    // SINGLE source of data
    private List<AbstractItem> items = new ArrayList<>();
    private List<AbstractItem> lastItems = new ArrayList<>();

    public DrawerLayoutAdapter(Context context, SideMenultItemAnimator animator, DrawerLayoutContainer drawerLayoutContainer) {
        mContext = context;
        mDrawerLayoutContainer = drawerLayoutContainer;
        itemAnimator = animator;
        accountsShown = UserConfig.getActivatedAccountsCount() > 1 && MessagesController.getGlobalMainSettings().getBoolean("accountsShown", true);
        Theme.createCommonDialogResources(context);
        resetItems();
        lastItems = new ArrayList<>(items);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void setAccountsShown(boolean value, boolean animated) {
        if (accountsShown == value || itemAnimator.isRunning()) {
            return;
        }
        accountsShown = value;
        MessagesController.getGlobalMainSettings().edit().putBoolean("accountsShown", accountsShown).commit();
        if (profileCell != null) {
            profileCell.setAccountsShown(accountsShown, false); // Animation is handled by DiffUtil
        }
        if (animated) {
            itemAnimator.setShouldClipChildren(false);
        }
        redrawAdapterData(); // Now safe and efficient
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
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(diffCallback);
        lastItems = new ArrayList<>(items);

        if (profileCell != null) {
            profileCell.setUser(MessagesController.getInstance(UserConfig.selectedAccount).getUser(UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId()), accountsShown);
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
        if (position == RecyclerView.NO_POSITION) {
            return false;
        }
        int viewType = getItemViewType(position);
        return viewType == VIEW_TYPE_ACTION || viewType == VIEW_TYPE_ACCOUNT || viewType == VIEW_TYPE_ADD_ACCOUNT;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // Capture RecyclerView reference if not already done
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
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        AbstractItem item = items.get(position);
        if (item instanceof ProfileItem) {
            DrawerProfileCell cell = (DrawerProfileCell) holder.itemView;
            cell.setUser(MessagesController.getInstance(UserConfig.selectedAccount).getUser(UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId()), accountsShown);
        } else if (item instanceof AccountItem) {
            ((DrawerUserCell) holder.itemView).setAccount(((AccountItem) item).accountIndex);
        } else if (item instanceof ActionItem) {
            Item actionItem = ((ActionItem) item).item;
            actionItem.bind((DrawerActionCell) holder.itemView);
            holder.itemView.setPadding(0, 0, 0, 0);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).viewType;
    }

    public void swapElements(int fromIndex, int toIndex) {
        AbstractItem from = items.get(fromIndex);
        AbstractItem to = items.get(toIndex);
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

        Collections.swap(items, fromIndex, toIndex);
        notifyItemMoved(fromIndex, toIndex);
    }

    private void resetItems() {
        items.clear();

        // 1. Profile
        items.add(new ProfileItem());
        // 2. Empty spacer
        items.add(new EmptyItem());

        // 3. Accounts section
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
                if (l1 > l2) {
                    return 1;
                } else if (l1 < l2) {
                    return -1;
                }
                return 0;
            });

            for (int account : accountNumbers) {
                items.add(new AccountItem(account));
            }

            if (accountNumbers.size() < UserConfig.MAX_ACCOUNT_COUNT) {
                items.add(new AddAccountItem());
            }

            items.add(new DividerItem());
        }

        // 4. Main menu items
        if (!UserConfig.getInstance(UserConfig.selectedAccount).isClientActivated()) {
            return;
        }
        int eventType = Theme.getEventType();
        int newGroupIcon;
        int newSecretIcon;
        int newChannelIcon;
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
        UserConfig me = UserConfig.getInstance(UserConfig.selectedAccount);
        boolean showDivider = false;

        // Main menu for extendDrawer
        ArrayList<Item> mainMenuItems = new ArrayList<>();
        mainMenuItems.add(new Item(16, LocaleController.getString(R.string.MyProfile), R.drawable.left_status_profile));
        if (me != null && me.isPremium()) {
            if (me.getEmojiStatus() != null) {
                mainMenuItems.add(new Item(15, LocaleController.getString(R.string.ChangeEmojiStatus), R.drawable.msg_status_edit));
            } else {
                mainMenuItems.add(new Item(15, LocaleController.getString(R.string.SetEmojiStatus), R.drawable.msg_status_set));
            }
            showDivider = true;
        }
//        if (MessagesController.getInstance(UserConfig.selectedAccount).storiesEnabled()) {
//            items.add(new Item(17, LocaleController.getString(R.string.ProfileStories), R.drawable.msg_menu_stories));
//            showDivider = true;
//        }
        showDivider = true;
        if (ApplicationLoader.applicationLoaderInstance != null) {
            if (ApplicationLoader.applicationLoaderInstance.extendDrawer(mainMenuItems)) {
                showDivider = true;
            }
        }
        TLRPC.TL_attachMenuBots menuBots = MediaDataController.getInstance(UserConfig.selectedAccount).getAttachMenuBots();
        if (menuBots != null && menuBots.bots != null) {
            for (int i = 0; i < menuBots.bots.size(); i++) {
                TLRPC.TL_attachMenuBot bot = menuBots.bots.get(i);
                if (bot.show_in_side_menu) {
                    mainMenuItems.add(new Item(bot));
                    showDivider = true;
                }
            }
        }

        if (showDivider) {
            for (Item item : mainMenuItems) {
                items.add(new ActionItem(item));
            }
            items.add(new DividerItem());
        } else {
            for (Item item : mainMenuItems) {
                items.add(new ActionItem(item));
            }
        }

        items.add(new ActionItem(new Item(2, LocaleController.getString(R.string.NewGroup), newGroupIcon)));
        //items.add(new Item(3, LocaleController.getString(R.string.NewSecretChat), newSecretIcon));
        //items.add(new Item(4, LocaleController.getString(R.string.NewChannel), newChannelIcon));
        items.add(new ActionItem(new Item(6, LocaleController.getString(R.string.Contacts), contactsIcon)));
        items.add(new ActionItem(new Item(10, LocaleController.getString(R.string.Calls), callsIcon)));
        items.add(new ActionItem(new Item(11, LocaleController.getString(R.string.SavedMessages), savedIcon)));
        items.add(new ActionItem(new Item(8, LocaleController.getString(R.string.Settings), settingsIcon)));
        items.add(new DividerItem());
        items.add(new ActionItem(new Item(7, LocaleController.getString(R.string.InviteFriends), inviteIcon)));
        items.add(new ActionItem(new Item(13, LocaleController.getString(R.string.TelegramFeatures), helpIcon)));
    }

    public boolean click(View view, int position) {
        if (position < 0 || position >= items.size()) return false;
        AbstractItem item = items.get(position);
        if (item instanceof ActionItem) {
            Item actionItem = ((ActionItem) item).item;
            if (actionItem != null && actionItem.listener != null) {
                actionItem.listener.onClick(view);
                return true;
            }
        }
        return false;
    }

    public int getId(int position) {
        if (position < 0 || position >= items.size()) return -1;
        AbstractItem item = items.get(position);
        if (item instanceof ActionItem) {
            return ((ActionItem) item).item.id;
        }
        return -1;
    }

    public int getFirstAccountPosition() {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof AccountItem) {
                return i;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    public int getLastAccountPosition() {
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i) instanceof AccountItem) {
                return i;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    public TLRPC.TL_attachMenuBot getAttachMenuBot(int position) {
        if (position < 0 || position >= items.size()) return null;
        AbstractItem item = items.get(position);
        if (item instanceof ActionItem) {
            return ((ActionItem) item).item.bot;
        }
        return null;
    }

    public static class FullDrawerDiffCallback extends DiffUtil.Callback {
        private final List<AbstractItem> oldList;
        private final List<AbstractItem> newList;

        public FullDrawerDiffCallback(List<AbstractItem> oldList, List<AbstractItem> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            AbstractItem oldItem = oldList.get(oldItemPosition);
            AbstractItem newItem = newList.get(newItemPosition);
            if (oldItem.viewType != newItem.viewType) return false;

            if (oldItem instanceof ActionItem && newItem instanceof ActionItem) {
                return ((ActionItem) oldItem).item.id == ((ActionItem) newItem).item.id;
            }
            if (oldItem instanceof AccountItem && newItem instanceof AccountItem) {
                return ((AccountItem) oldItem).accountIndex == ((AccountItem) newItem).accountIndex;
            }
            // For Profile, Empty, Divider, AddAccount - type determines identity
            return true;
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            AbstractItem oldItem = oldList.get(oldItemPosition);
            AbstractItem newItem = newList.get(newItemPosition);

            if (oldItem instanceof ActionItem && newItem instanceof ActionItem) {
                return ((ActionItem) oldItem).item.areContentsTheSame(((ActionItem) newItem).item);
            }
            if (oldItem instanceof AccountItem && newItem instanceof AccountItem) {
                // Accounts don't have "content" - only index, which is checked in areItemsTheSame
                return true;
            }
            // Other types don't have changeable content
            return true;
        }
    }

    // =============== Base element ===============

    private abstract static class AbstractItem {
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

    private static class ActionItem extends AbstractItem {
        final Item item;

        ActionItem(Item item) {
            super(VIEW_TYPE_ACTION);
            this.item = item;
        }
    }

    // =============== Legacy Item (for compatibility) ===============

    public static class Item {
        public int icon;
        public CharSequence text;
        public int id;
        TLRPC.TL_attachMenuBot bot;
        View.OnClickListener listener;
        public boolean error;

        public Item(int id, CharSequence text, int icon) {
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
            if (obj == null) {
                return false;
            }
            if (this.icon != obj.icon) {
                return false;
            }
            if (this.text != obj.text) {
                return false;
            }
            if (this.error != obj.error) {
                return false;
            }
            if (this.bot != obj.bot) {
                return false;
            }
            return true;
        }

        public Item(TLRPC.TL_attachMenuBot bot) {
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