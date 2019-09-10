/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.LocaleController;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ManageChatTextCell;
import org.telegram.ui.Cells.ManageChatUserCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class PrivacyUsersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, ContactsActivity.ContactsActivityDelegate {

    private RecyclerListView listView;
    private LinearLayoutManager layoutManager;
    private ListAdapter listViewAdapter;
    private EmptyTextProgressView emptyView;

    private int rowCount;
    private int blockUserRow;
    private int blockUserDetailRow;
    private int usersHeaderRow;
    private int usersStartRow;
    private int usersEndRow;
    private int usersDetailRow;

    private boolean blockedUsersActivity;

    private boolean isGroup;
    private ArrayList<Integer> uidArray;
    private boolean isAlwaysShare;

    private PrivacyActivityDelegate delegate;

    public interface PrivacyActivityDelegate {
        void didUpdateUserList(ArrayList<Integer> ids, boolean added);
    }

    public PrivacyUsersActivity() {
        super();
        blockedUsersActivity = true;
    }

    public PrivacyUsersActivity(ArrayList<Integer> users, boolean group, boolean always) {
        super();
        uidArray = users;
        isAlwaysShare = always;
        isGroup = group;
        blockedUsersActivity = false;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
        if (blockedUsersActivity) {
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.blockedUsersDidLoad);
        }
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        if (blockedUsersActivity) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.blockedUsersDidLoad);
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (blockedUsersActivity) {
            actionBar.setTitle(LocaleController.getString("BlockedUsers", R.string.BlockedUsers));
        } else {
            if (isGroup) {
                if (isAlwaysShare) {
                    actionBar.setTitle(LocaleController.getString("AlwaysAllow", R.string.AlwaysAllow));
                } else {
                    actionBar.setTitle(LocaleController.getString("NeverAllow", R.string.NeverAllow));
                }
            } else {
                if (isAlwaysShare) {
                    actionBar.setTitle(LocaleController.getString("AlwaysShareWithTitle", R.string.AlwaysShareWithTitle));
                } else {
                    actionBar.setTitle(LocaleController.getString("NeverShareWithTitle", R.string.NeverShareWithTitle));
                }
            }
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        emptyView = new EmptyTextProgressView(context);
        if (blockedUsersActivity) {
            emptyView.setText(LocaleController.getString("NoBlocked", R.string.NoBlocked));
        } else {
            emptyView.setText(LocaleController.getString("NoContacts", R.string.NoContacts));
        }
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView = new RecyclerListView(context);
        listView.setEmptyView(emptyView);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(listViewAdapter = new ListAdapter(context));
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            if (position == blockUserRow) {
                if (blockedUsersActivity) {
                    presentFragment(new DialogOrContactPickerActivity());
                } else {
                    Bundle args = new Bundle();
                    args.putBoolean(isAlwaysShare ? "isAlwaysShare" : "isNeverShare", true);
                    args.putBoolean("isGroup", isGroup);
                    GroupCreateActivity fragment = new GroupCreateActivity(args);
                    fragment.setDelegate(ids -> {
                        for (Integer id1 : ids) {
                            if (uidArray.contains(id1)) {
                                continue;
                            }
                            uidArray.add(id1);
                        }
                        updateRows();
                        if (delegate != null) {
                            delegate.didUpdateUserList(uidArray, true);
                        }
                    });
                    presentFragment(fragment);
                }
            } else if (position >= usersStartRow && position < usersEndRow) {
                if (blockedUsersActivity) {
                    Bundle args = new Bundle();
                    args.putInt("user_id", getMessagesController().blockedUsers.keyAt(position - usersStartRow));
                    presentFragment(new ProfileActivity(args));
                } else {
                    Bundle args = new Bundle();
                    Integer id = uidArray.get(position - usersStartRow);
                    if (id > 0) {
                        args.putInt("user_id", id);
                    } else {
                        args.putInt("chat_id", -id);
                    }
                    presentFragment(new ProfileActivity(args));
                }
            }
        });

        listView.setOnItemLongClickListener((view, position) -> {
            if (position >= usersStartRow && position < usersEndRow) {
                if (blockedUsersActivity) {
                    showUnblockAlert(getMessagesController().blockedUsers.keyAt(position - usersStartRow));
                } else {
                    showUnblockAlert(uidArray.get(position - usersStartRow));
                }
                return true;
            }
            return false;
        });

        if (blockedUsersActivity) {
            listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    if (getMessagesController().blockedEndReached) {
                        return;
                    }
                    int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                    int visibleItemCount = Math.abs(layoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;
                    int totalItemCount = recyclerView.getAdapter().getItemCount();
                    if (visibleItemCount > 0) {
                        if (layoutManager.findLastVisibleItemPosition() >= totalItemCount - 10) {
                            getMessagesController().getBlockedUsers(false);
                        }
                    }
                }
            });
        }

        if (getMessagesController().totalBlockedCount < 0) {
            emptyView.showProgress();
        } else {
            emptyView.showTextView();
        }
        updateRows();
        return fragmentView;
    }

    public void setDelegate(PrivacyActivityDelegate privacyActivityDelegate) {
        delegate = privacyActivityDelegate;
    }

    private void showUnblockAlert(int uid) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        CharSequence[] items;
        if (blockedUsersActivity) {
            items = new CharSequence[]{LocaleController.getString("Unblock", R.string.Unblock)};
        } else {
            items = new CharSequence[]{LocaleController.getString("Delete", R.string.Delete)};
        }
        builder.setItems(items, (dialogInterface, i) -> {
            if (i == 0) {
                if (blockedUsersActivity) {
                    getMessagesController().unblockUser(uid);
                } else {
                    uidArray.remove((Integer) uid);
                    updateRows();
                    if (delegate != null) {
                        delegate.didUpdateUserList(uidArray, false);
                    }
                    if (uidArray.isEmpty()) {
                        finishFragment();
                    }
                }
            }
        });
        showDialog(builder.create());
    }

    private void updateRows() {
        rowCount = 0;
        if (!blockedUsersActivity || getMessagesController().totalBlockedCount >= 0) {
            blockUserRow = rowCount++;
            blockUserDetailRow = rowCount++;

            int count;
            if (blockedUsersActivity) {
                count = getMessagesController().blockedUsers.size();
            } else {
                count = uidArray.size();
            }
            if (count != 0) {
                usersHeaderRow = rowCount++;
                usersStartRow = rowCount;
                rowCount += count;
                usersEndRow = rowCount;
                usersDetailRow = rowCount++;
            } else {
                usersHeaderRow = -1;
                usersStartRow = -1;
                usersEndRow = -1;
                usersDetailRow = -1;
            }
        }
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.updateInterfaces) {
            int mask = (Integer) args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0) {
                updateVisibleRows(mask);
            }
        } else if (id == NotificationCenter.blockedUsersDidLoad) {
            emptyView.showTextView();
            updateRows();
        }
    }

    private void updateVisibleRows(int mask) {
        if (listView == null) {
            return;
        }
        int count = listView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = listView.getChildAt(a);
            if (child instanceof ManageChatUserCell) {
                ((ManageChatUserCell) child).update(mask);
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void didSelectContact(final TLRPC.User user, String param, ContactsActivity activity) {
        if (user == null) {
            return;
        }
        getMessagesController().blockUser(user.id);
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int viewType = holder.getItemViewType();
            return viewType == 0 || viewType == 2;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new ManageChatUserCell(mContext, 7, 6, true);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    ((ManageChatUserCell) view).setDelegate((cell, click) -> {
                        if (click) {
                            showUnblockAlert((Integer) cell.getTag());
                        }
                        return true;
                    });
                    break;
                case 1:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case 2:
                    view = new ManageChatTextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                default:
                    HeaderCell headerCell = new HeaderCell(mContext, false, 21, 11, false);
                    headerCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    headerCell.setHeight(43);
                    view = headerCell;
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    ManageChatUserCell userCell = (ManageChatUserCell) holder.itemView;
                    int uid;
                    if (blockedUsersActivity) {
                        uid = getMessagesController().blockedUsers.keyAt(position - usersStartRow);
                    } else {
                        uid = uidArray.get(position - usersStartRow);
                    }
                    userCell.setTag(uid);
                    if (uid > 0) {
                        TLRPC.User user = getMessagesController().getUser(uid);
                        if (user != null) {
                            String number;
                            if (user.bot) {
                                number = LocaleController.getString("Bot", R.string.Bot).substring(0, 1).toUpperCase() + LocaleController.getString("Bot", R.string.Bot).substring(1);
                            } else if (user.phone != null && user.phone.length() != 0) {
                                number = PhoneFormat.getInstance().format("+" + user.phone);
                            } else {
                                number = LocaleController.getString("NumberUnknown", R.string.NumberUnknown);
                            }
                            userCell.setData(user, null, number, position != usersEndRow - 1);
                        }
                    } else {
                        TLRPC.Chat chat = getMessagesController().getChat(-uid);
                        if (chat != null) {
                            String subtitle;
                            if (chat.participants_count != 0) {
                                subtitle = LocaleController.formatPluralString("Members", chat.participants_count);
                            } else if (chat.has_geo) {
                                subtitle = LocaleController.getString("MegaLocation", R.string.MegaLocation);
                            } else if (TextUtils.isEmpty(chat.username)) {
                                subtitle = LocaleController.getString("MegaPrivate", R.string.MegaPrivate);
                            } else {
                                subtitle = LocaleController.getString("MegaPublic", R.string.MegaPublic);
                            }
                            userCell.setData(chat, null, subtitle, position != usersEndRow - 1);
                        }
                    }
                    break;
                case 1:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == blockUserDetailRow) {
                        if (blockedUsersActivity) {
                            privacyCell.setText(LocaleController.getString("BlockedUsersInfo", R.string.BlockedUsersInfo));
                        } else {
                            privacyCell.setText(null);
                        }
                        if (usersStartRow == -1) {
                            privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                        } else {
                            privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                        }
                    } else if (position == usersDetailRow) {
                        privacyCell.setText("");
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                case 2:
                    ManageChatTextCell actionCell = (ManageChatTextCell) holder.itemView;
                    actionCell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueButton);
                    if (blockedUsersActivity) {
                        actionCell.setText(LocaleController.getString("BlockUser", R.string.BlockUser), null, R.drawable.actions_addmember2, false);
                    } else {
                        actionCell.setText(LocaleController.getString("PrivacyAddAnException", R.string.PrivacyAddAnException), null, R.drawable.actions_addmember2, false);
                    }
                    break;
                case 3:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == usersHeaderRow) {
                        if (blockedUsersActivity) {
                            headerCell.setText(LocaleController.formatPluralString("BlockedUsersCount", getMessagesController().totalBlockedCount));
                        } else {
                            headerCell.setText(LocaleController.getString("PrivacyExceptions", R.string.PrivacyExceptions));
                        }
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == usersHeaderRow) {
                return 3;
            } else if (position == blockUserRow) {
                return 2;
            } else if (position == blockUserDetailRow || position == usersDetailRow) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (listView != null) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof ManageChatUserCell) {
                        ((ManageChatUserCell) child).update(0);
                    }
                }
            }
        };

        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ManageChatUserCell.class, ManageChatTextCell.class, HeaderCell.class}, null, null, null, Theme.key_windowBackgroundWhite),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(emptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_emptyListPlaceholder),
                new ThemeDescription(emptyView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),

                new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"statusColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteGrayText),
                new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, new String[]{"statusOnlineColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteBlueText),
                new ThemeDescription(listView, 0, new Class[]{ManageChatUserCell.class}, null, new Drawable[]{Theme.avatar_savedDrawable}, null, Theme.key_avatar_text),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink),

                new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader),

                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"imageView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueButton),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{ManageChatTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueIcon),
        };
    }
}
