/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.FrameLayout;
import android.widget.TimePicker;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.Calendar;

public class ChannelRightsEditActivity extends BaseFragment {

    private ListAdapter listViewAdapter;
    private RecyclerListView listView;

    private int chatId;
    private TLRPC.User currentUser;
    private int currentType;
    private boolean isMegagroup;

    private boolean canEdit;
    private boolean isDemocracy;

    private TLRPC.TL_channelAdminRights adminRights;
    private TLRPC.TL_channelAdminRights myAdminRights;
    private TLRPC.TL_channelBannedRights bannedRights;

    private int rowCount;
    private int changeInfoRow;
    private int postMessagesRow;
    private int editMesagesRow;
    private int deleteMessagesRow;
    private int addAdminsRow;
    private int banUsersRow;
    private int addUsersRow;
    private int pinMessagesRow;
    private int rightsShadowRow;
    private int removeAdminRow;
    private int removeAdminShadowRow;
    private int cantEditInfoRow;

    private int viewMessagesRow;
    private int sendMessagesRow;
    private int sendMediaRow;
    private int sendStickersRow;
    private int embedLinksRow;
    private int untilDateRow;

    private ChannelRightsEditActivityDelegate delegate;

    public interface ChannelRightsEditActivityDelegate {
        void didSetRights(int rights, TLRPC.TL_channelAdminRights rightsAdmin, TLRPC.TL_channelBannedRights rightsBanned);
    }

    private final static int done_button = 1;

    public ChannelRightsEditActivity(int userId, int channelId, TLRPC.TL_channelAdminRights rightsAdmin, TLRPC.TL_channelBannedRights rightsBanned, int type, boolean edit) {
        super();
        chatId = channelId;
        currentUser = MessagesController.getInstance().getUser(userId);
        currentType = type;
        canEdit = edit;
        boolean initialIsSet;
        TLRPC.Chat chat = MessagesController.getInstance().getChat(chatId);
        if (chat != null) {
            isMegagroup = chat.megagroup;
            myAdminRights = chat.admin_rights;
        }
        if (myAdminRights == null) {
            myAdminRights = new TLRPC.TL_channelAdminRights();
            myAdminRights.change_info = myAdminRights.post_messages = myAdminRights.edit_messages =
            myAdminRights.delete_messages = myAdminRights.ban_users = myAdminRights.invite_users =
            myAdminRights.invite_link = myAdminRights.pin_messages = myAdminRights.add_admins = true;
        }
        if (type == 0) {
            adminRights = new TLRPC.TL_channelAdminRights();
            if (rightsAdmin == null) {
                adminRights.change_info = myAdminRights.change_info;
                adminRights.post_messages = myAdminRights.post_messages;
                adminRights.edit_messages = myAdminRights.edit_messages;
                adminRights.delete_messages = myAdminRights.delete_messages;
                adminRights.ban_users = myAdminRights.ban_users;
                adminRights.invite_users = myAdminRights.invite_users;
                adminRights.invite_link = myAdminRights.invite_link;
                adminRights.pin_messages = myAdminRights.pin_messages;
                initialIsSet = false;
            } else {
                adminRights.change_info = rightsAdmin.change_info;
                adminRights.post_messages = rightsAdmin.post_messages;
                adminRights.edit_messages = rightsAdmin.edit_messages;
                adminRights.delete_messages = rightsAdmin.delete_messages;
                adminRights.ban_users = rightsAdmin.ban_users;
                adminRights.invite_users = rightsAdmin.invite_users;
                adminRights.invite_link = rightsAdmin.invite_link;
                adminRights.pin_messages = rightsAdmin.pin_messages;
                adminRights.add_admins = rightsAdmin.add_admins;

                initialIsSet = adminRights.change_info || adminRights.post_messages || adminRights.edit_messages ||
                        adminRights.delete_messages || adminRights.ban_users || adminRights.invite_users ||
                        adminRights.invite_link || adminRights.pin_messages || adminRights.add_admins;
            }
        } else {
            bannedRights = new TLRPC.TL_channelBannedRights();
            if (rightsBanned == null) {
                bannedRights.view_messages = bannedRights.send_media = bannedRights.send_messages =
                bannedRights.embed_links = bannedRights.send_stickers = bannedRights.send_gifs =
                bannedRights.send_games = bannedRights.send_inline = true;
            } else {
                bannedRights.view_messages = rightsBanned.view_messages;
                bannedRights.send_messages = rightsBanned.send_messages;
                bannedRights.send_media = rightsBanned.send_media;
                bannedRights.send_stickers = rightsBanned.send_stickers;
                bannedRights.send_gifs = rightsBanned.send_gifs;
                bannedRights.send_games = rightsBanned.send_games;
                bannedRights.send_inline = rightsBanned.send_inline;
                bannedRights.embed_links = rightsBanned.embed_links;
                bannedRights.until_date = rightsBanned.until_date;
            }
            initialIsSet = rightsBanned == null || !rightsBanned.view_messages;
        }
        rowCount += 3;
        if (type == 0) {
            if (isMegagroup) {
                changeInfoRow = rowCount++;
                deleteMessagesRow = rowCount++;
                banUsersRow = rowCount++;
                addUsersRow = rowCount++;
                pinMessagesRow = rowCount++;
                addAdminsRow = rowCount++;
                isDemocracy = chat.democracy;
            } else {
                changeInfoRow = rowCount++;
                postMessagesRow = rowCount++;
                editMesagesRow = rowCount++;
                deleteMessagesRow = rowCount++;
                addUsersRow = rowCount++;
                addAdminsRow = rowCount++;
            }
        } else if (type == 1) {
            viewMessagesRow = rowCount++;
            sendMessagesRow = rowCount++;
            sendMediaRow = rowCount++;
            sendStickersRow = rowCount++;
            embedLinksRow = rowCount++;
            untilDateRow = rowCount++;
        }

        if (canEdit && initialIsSet) {
            rightsShadowRow = rowCount++;
            removeAdminRow = rowCount++;
            removeAdminShadowRow = rowCount++;
            cantEditInfoRow = -1;
        } else {
            removeAdminRow = -1;
            removeAdminShadowRow = -1;
            if (type == 0 && !canEdit) {
                rightsShadowRow = -1;
                cantEditInfoRow = rowCount++;
            } else {
                rightsShadowRow = rowCount++;
            }
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (currentType == 0) {
            actionBar.setTitle(LocaleController.getString("EditAdmin", R.string.EditAdmin));
        } else {
            actionBar.setTitle(LocaleController.getString("UserRestrictions", R.string.UserRestrictions));
        }

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == done_button) {
                    if (currentType == 0) {
                        if (isMegagroup) {
                            adminRights.post_messages = adminRights.edit_messages = false;
                        } else {
                            adminRights.pin_messages = adminRights.ban_users = false;
                        }
                        MessagesController.setUserAdminRole(chatId, currentUser, adminRights, isMegagroup, getFragmentForAlert(1));
                        if (delegate != null) {
                            delegate.didSetRights(
                                    adminRights.change_info || adminRights.post_messages || adminRights.edit_messages ||
                                    adminRights.delete_messages || adminRights.ban_users || adminRights.invite_users ||
                                    adminRights.invite_link || adminRights.pin_messages || adminRights.add_admins ? 1 : 0, adminRights, bannedRights);
                        }
                    } else if (currentType == 1) {
                        MessagesController.setUserBannedRole(chatId, currentUser, bannedRights, isMegagroup, getFragmentForAlert(1));
                        int rights;
                        if (bannedRights.view_messages) {
                            rights = 0;
                        } else if (bannedRights.send_messages || bannedRights.send_stickers || bannedRights.embed_links || bannedRights.send_media ||
                                bannedRights.send_gifs || bannedRights.send_games || bannedRights.send_inline) {
                            rights = 1;
                        } else {
                            bannedRights.until_date = 0;
                            rights = 2;
                        }
                        if (delegate != null) {
                            delegate.didSetRights(rights, adminRights, bannedRights);
                        }
                    }
                    finishFragment();
                }
            }
        });

        if (canEdit) {
            ActionBarMenu menu = actionBar.createMenu();
            menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56));
        }

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        };
        listView.setItemAnimator(null);
        listView.setLayoutAnimation(null);
        listView.setLayoutManager(linearLayoutManager);
        listView.setAdapter(listViewAdapter = new ListAdapter(context));
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (!canEdit) {
                    return;
                }
                if (position == 0) {
                    Bundle args = new Bundle();
                    args.putInt("user_id", currentUser.id);
                    presentFragment(new ProfileActivity(args));
                } else if (position == removeAdminRow) {
                    if (currentType == 0) {
                        MessagesController.setUserAdminRole(chatId, currentUser, new TLRPC.TL_channelAdminRights(), isMegagroup, getFragmentForAlert(0));
                    } else if (currentType == 1) {
                        bannedRights = new TLRPC.TL_channelBannedRights();
                        bannedRights.view_messages = true;
                        bannedRights.send_media = true;
                        bannedRights.send_messages = true;
                        bannedRights.send_stickers = true;
                        bannedRights.send_gifs = true;
                        bannedRights.send_games = true;
                        bannedRights.send_inline = true;
                        bannedRights.embed_links = true;
                        bannedRights.until_date = 0;
                        MessagesController.setUserBannedRole(chatId, currentUser, bannedRights, isMegagroup, getFragmentForAlert(0));
                    }
                    if (delegate != null) {
                        delegate.didSetRights(0, adminRights, bannedRights);
                    }
                    finishFragment();
                } else if (position == untilDateRow) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    Calendar calendar = Calendar.getInstance();
                    int year = calendar.get(Calendar.YEAR);
                    int monthOfYear = calendar.get(Calendar.MONTH);
                    int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
                    try {
                        DatePickerDialog dialog = new DatePickerDialog(getParentActivity(), new DatePickerDialog.OnDateSetListener() {
                            @Override
                            public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                                Calendar calendar = Calendar.getInstance();
                                calendar.clear();
                                calendar.set(year, month, dayOfMonth);
                                final int time = (int) (calendar.getTime().getTime() / 1000);
                                try {
                                    TimePickerDialog dialog = new TimePickerDialog(getParentActivity(), new TimePickerDialog.OnTimeSetListener() {
                                        @Override
                                        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
                                            bannedRights.until_date = time + hourOfDay * 3600 + minute * 60;
                                            listViewAdapter.notifyItemChanged(untilDateRow);
                                        }
                                    }, 0, 0, true);
                                    dialog.setButton(DialogInterface.BUTTON_POSITIVE, LocaleController.getString("Set", R.string.Set), dialog);
                                    dialog.setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString("Cancel", R.string.Cancel), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {

                                        }
                                    });
                                    showDialog(dialog);
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            }
                        }, year, monthOfYear, dayOfMonth);

                        final DatePicker datePicker = dialog.getDatePicker();

                        Calendar date = Calendar.getInstance();
                        date.setTimeInMillis(System.currentTimeMillis());
                        date.set(Calendar.HOUR_OF_DAY, date.getMinimum(Calendar.HOUR_OF_DAY));
                        date.set(Calendar.MINUTE, date.getMinimum(Calendar.MINUTE));
                        date.set(Calendar.SECOND, date.getMinimum(Calendar.SECOND));
                        date.set(Calendar.MILLISECOND, date.getMinimum(Calendar.MILLISECOND));
                        datePicker.setMinDate(date.getTimeInMillis());

                        date.setTimeInMillis(System.currentTimeMillis() + 31536000000L);
                        date.set(Calendar.HOUR_OF_DAY, date.getMaximum(Calendar.HOUR_OF_DAY));
                        date.set(Calendar.MINUTE, date.getMaximum(Calendar.MINUTE));
                        date.set(Calendar.SECOND, date.getMaximum(Calendar.SECOND));
                        date.set(Calendar.MILLISECOND, date.getMaximum(Calendar.MILLISECOND));
                        datePicker.setMaxDate(date.getTimeInMillis());

                        dialog.setButton(DialogInterface.BUTTON_POSITIVE, LocaleController.getString("Set", R.string.Set), dialog);
                        dialog.setButton(DialogInterface.BUTTON_NEUTRAL, LocaleController.getString("UserRestrictionsUntilForever", R.string.UserRestrictionsUntilForever), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                bannedRights.until_date = 0;
                                listViewAdapter.notifyItemChanged(untilDateRow);
                            }
                        });
                        dialog.setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString("Cancel", R.string.Cancel), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        });
                        if (Build.VERSION.SDK_INT >= 21) {
                            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                                @Override
                                public void onShow(DialogInterface dialog) {
                                    int count = datePicker.getChildCount();
                                    for (int a = 0; a < count; a++) {
                                        View child = datePicker.getChildAt(a);
                                        ViewGroup.LayoutParams layoutParams = child.getLayoutParams();
                                        layoutParams.width = LayoutHelper.MATCH_PARENT;
                                        child.setLayoutParams(layoutParams);
                                    }
                                }
                            });
                        }
                        showDialog(dialog);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                } else if (view instanceof TextCheckCell2) {
                    TextCheckCell2 checkCell = (TextCheckCell2) view;
                    if (!checkCell.isEnabled()) {
                        return;
                    }
                    checkCell.setChecked(!checkCell.isChecked());
                    if (position == changeInfoRow) {
                        adminRights.change_info = !adminRights.change_info;
                    } else if (position == postMessagesRow) {
                        adminRights.post_messages = !adminRights.post_messages;
                    } else if (position == editMesagesRow) {
                        adminRights.edit_messages = !adminRights.edit_messages;
                    } else if (position == deleteMessagesRow) {
                        adminRights.delete_messages = !adminRights.delete_messages;
                    } else if (position == addAdminsRow) {
                        adminRights.add_admins = !adminRights.add_admins;
                    } else if (position == banUsersRow) {
                        adminRights.ban_users = !adminRights.ban_users;
                    } else if (position == addUsersRow) {
                        adminRights.invite_users = adminRights.invite_link = !adminRights.invite_users;
                    } else if (position == pinMessagesRow) {
                        adminRights.pin_messages = !adminRights.pin_messages;
                    } else if (bannedRights != null) {
                        boolean disabled = !checkCell.isChecked();
                        if (position == viewMessagesRow) {
                            bannedRights.view_messages = !bannedRights.view_messages;
                        } else if (position == sendMessagesRow) {
                            bannedRights.send_messages = !bannedRights.send_messages;
                        } else if (position == sendMediaRow) {
                            bannedRights.send_media = !bannedRights.send_media;
                        }else if (position == sendStickersRow) {
                            bannedRights.send_stickers = bannedRights.send_games = bannedRights.send_gifs = bannedRights.send_inline = !bannedRights.send_stickers;
                        } else if (position == embedLinksRow) {
                            bannedRights.embed_links = !bannedRights.embed_links;
                        }
                        if (disabled) {
                            if (bannedRights.view_messages && !bannedRights.send_messages) {
                                bannedRights.send_messages = true;
                                RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(sendMessagesRow);
                                if (holder != null) {
                                    ((TextCheckCell2) holder.itemView).setChecked(false);
                                }
                            }
                            if ((bannedRights.view_messages || bannedRights.send_messages) && !bannedRights.send_media) {
                                bannedRights.send_media = true;
                                RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(sendMediaRow);
                                if (holder != null) {
                                    ((TextCheckCell2) holder.itemView).setChecked(false);
                                }
                            }
                            if ((bannedRights.view_messages || bannedRights.send_messages || bannedRights.send_media) && !bannedRights.send_stickers) {
                                bannedRights.send_stickers = bannedRights.send_games = bannedRights.send_gifs = bannedRights.send_inline = true;
                                RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(sendStickersRow);
                                if (holder != null) {
                                    ((TextCheckCell2) holder.itemView).setChecked(false);
                                }
                            }
                            if ((bannedRights.view_messages || bannedRights.send_messages || bannedRights.send_media) && !bannedRights.embed_links) {
                                bannedRights.embed_links = true;
                                RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(embedLinksRow);
                                if (holder != null) {
                                    ((TextCheckCell2) holder.itemView).setChecked(false);
                                }
                            }
                        } else {
                            if ((!bannedRights.send_messages || !bannedRights.embed_links || !bannedRights.send_inline || !bannedRights.send_media) && bannedRights.view_messages) {
                                bannedRights.view_messages = false;
                                RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(viewMessagesRow);
                                if (holder != null) {
                                    ((TextCheckCell2) holder.itemView).setChecked(true);
                                }
                            }
                            if ((!bannedRights.embed_links || !bannedRights.send_inline || !bannedRights.send_media) && bannedRights.send_messages) {
                                bannedRights.send_messages = false;
                                RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(sendMessagesRow);
                                if (holder != null) {
                                    ((TextCheckCell2) holder.itemView).setChecked(true);
                                }
                            }
                            if ((!bannedRights.send_inline || !bannedRights.embed_links) && bannedRights.send_media) {
                                bannedRights.send_media = false;
                                RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(sendMediaRow);
                                if (holder != null) {
                                    ((TextCheckCell2) holder.itemView).setChecked(true);
                                }
                            }
                        }
                    }
                }
            }
        });
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
    }

    public void setDelegate(ChannelRightsEditActivityDelegate channelRightsEditActivityDelegate) {
        delegate = channelRightsEditActivityDelegate;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (!canEdit) {
                return false;
            }
            int type = holder.getItemViewType();
            if (currentType == 0 && type == 4) {
                int position = holder.getAdapterPosition();
                if (position == changeInfoRow) {
                    return myAdminRights.change_info;
                } else if (position == postMessagesRow) {
                    return myAdminRights.post_messages;
                } else if (position == editMesagesRow) {
                    return myAdminRights.edit_messages;
                } else if (position == deleteMessagesRow) {
                    return myAdminRights.delete_messages;
                } else if (position == addAdminsRow) {
                    return myAdminRights.add_admins;
                } else if (position == banUsersRow) {
                    return myAdminRights.ban_users;
                } else if (position == addUsersRow) {
                    return myAdminRights.invite_users;
                } else if (position == pinMessagesRow) {
                    return myAdminRights.pin_messages;
                }
            }
            return type != 3 && type != 1 && type != 5;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new UserCell(mContext, 1, 0, false);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                case 2:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                    view = new TextCheckCell2(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 5:
                default:
                    view = new ShadowSectionCell(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    UserCell userCell = (UserCell) holder.itemView;
                    userCell.setData(currentUser, null, null, 0);
                    break;
                case 1:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == cantEditInfoRow) {
                        privacyCell.setText(LocaleController.getString("EditAdminCantEdit", R.string.EditAdminCantEdit));
                    }
                    break;
                case 2:
                    TextSettingsCell actionCell = (TextSettingsCell) holder.itemView;
                    if (position == removeAdminRow) {
                        actionCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText3));
                        actionCell.setTag(Theme.key_windowBackgroundWhiteRedText3);
                        if (currentType == 0) {
                            actionCell.setText(LocaleController.getString("EditAdminRemoveAdmin", R.string.EditAdminRemoveAdmin), false);
                        } else if (currentType == 1) {
                            actionCell.setText(LocaleController.getString("UserRestrictionsBlock", R.string.UserRestrictionsBlock), false);
                        }
                    } else if (position == untilDateRow) {
                        actionCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        actionCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        String value;
                        if (bannedRights.until_date == 0 || Math.abs(bannedRights.until_date - System.currentTimeMillis() / 1000) > 10 * 365 * 24 * 60 * 60) {
                            value = LocaleController.getString("UserRestrictionsUntilForever", R.string.UserRestrictionsUntilForever);
                        } else {
                            value = LocaleController.formatDateForBan(bannedRights.until_date);
                        }
                        actionCell.setTextAndValue(LocaleController.getString("UserRestrictionsUntil", R.string.UserRestrictionsUntil), value, false);
                    }
                    break;
                case 3:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (currentType == 0) {
                        headerCell.setText(LocaleController.getString("EditAdminWhatCanDo", R.string.EditAdminWhatCanDo));
                    } else if (currentType == 1) {
                        headerCell.setText(LocaleController.getString("UserRestrictionsCanDo", R.string.UserRestrictionsCanDo));
                    }
                    break;
                case 4:
                    TextCheckCell2 checkCell = (TextCheckCell2) holder.itemView;
                    if (position == changeInfoRow) {
                        if (isMegagroup) {
                            checkCell.setTextAndCheck(LocaleController.getString("EditAdminChangeGroupInfo", R.string.EditAdminChangeGroupInfo), adminRights.change_info, true);
                        } else {
                            checkCell.setTextAndCheck(LocaleController.getString("EditAdminChangeChannelInfo", R.string.EditAdminChangeChannelInfo), adminRights.change_info, true);
                        }
                    } else if (position == postMessagesRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("EditAdminPostMessages", R.string.EditAdminPostMessages), adminRights.post_messages, true);
                    } else if (position == editMesagesRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("EditAdminEditMessages", R.string.EditAdminEditMessages), adminRights.edit_messages, true);
                    } else if (position == deleteMessagesRow) {
                        if (isMegagroup) {
                            checkCell.setTextAndCheck(LocaleController.getString("EditAdminGroupDeleteMessages", R.string.EditAdminGroupDeleteMessages), adminRights.delete_messages, true);
                        } else {
                            checkCell.setTextAndCheck(LocaleController.getString("EditAdminDeleteMessages", R.string.EditAdminDeleteMessages), adminRights.delete_messages, true);
                        }
                    } else if (position == addAdminsRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("EditAdminAddAdmins", R.string.EditAdminAddAdmins), adminRights.add_admins, false);
                    } else if (position == banUsersRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("EditAdminBanUsers", R.string.EditAdminBanUsers), adminRights.ban_users, true);
                    } else if (position == addUsersRow) {
                        if (!isDemocracy) {
                            checkCell.setTextAndCheck(LocaleController.getString("EditAdminAddUsers", R.string.EditAdminAddUsers), adminRights.invite_users, true);
                        } else {
                            checkCell.setTextAndCheck(LocaleController.getString("EditAdminAddUsersViaLink", R.string.EditAdminAddUsersViaLink), adminRights.invite_users, true);
                        }
                    } else if (position == pinMessagesRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("EditAdminPinMessages", R.string.EditAdminPinMessages), adminRights.pin_messages, true);
                    } else if (position == viewMessagesRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsRead", R.string.UserRestrictionsRead), !bannedRights.view_messages, true);
                    } else if (position == sendMessagesRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsSend", R.string.UserRestrictionsSend), !bannedRights.send_messages, true);
                    } else if (position == sendMediaRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsSendMedia", R.string.UserRestrictionsSendMedia), !bannedRights.send_media, true);
                    } else if (position == sendStickersRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsSendStickers", R.string.UserRestrictionsSendStickers), !bannedRights.send_stickers, true);
                    } else if (position == embedLinksRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsEmbedLinks", R.string.UserRestrictionsEmbedLinks), !bannedRights.embed_links, true);
                    }
                    if (position == sendMediaRow || position == sendStickersRow || position == embedLinksRow) {
                        checkCell.setEnabled(!bannedRights.send_messages && !bannedRights.view_messages);
                    } else if (position == sendMessagesRow) {
                        checkCell.setEnabled(!bannedRights.view_messages);
                    }
                    break;
                case 5:
                    ShadowSectionCell shadowCell = (ShadowSectionCell) holder.itemView;
                    if (position == rightsShadowRow) {
                        shadowCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, removeAdminRow == -1 ? R.drawable.greydivider_bottom : R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == removeAdminShadowRow) {
                        shadowCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        shadowCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return 0;
            } else if (position == 1 || position == rightsShadowRow || position == removeAdminShadowRow) {
                return 5;
            } else if (position == 2) {
                return 3;
            } else if (position == changeInfoRow || position == postMessagesRow || position == editMesagesRow || position == deleteMessagesRow || position == addAdminsRow ||
                    position == banUsersRow || position == addUsersRow || position == pinMessagesRow ||
                    position == viewMessagesRow || position == sendMessagesRow || position == sendMediaRow || position == sendStickersRow || position == embedLinksRow) {
                return 4;
            } else if (position == cantEditInfoRow) {
                return 1;
            } else {
                return 2;
            }
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate сellDelegate = new ThemeDescription.ThemeDescriptionDelegate() {
            @Override
            public void didSetColor(int color) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof UserCell) {
                        ((UserCell) child).update(0);
                    }
                }
            }
        };

        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{UserCell.class, TextSettingsCell.class, TextCheckCell2.class, HeaderCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4),

                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText3),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText),
                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueImageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon),

                new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchThumb),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchThumbChecked),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),

                new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader),

                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"statusColor"}, null, null, сellDelegate, Theme.key_windowBackgroundWhiteGrayText),
                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, new String[]{"statusOnlineColor"}, null, null, сellDelegate, Theme.key_windowBackgroundWhiteBlueText),
                new ThemeDescription(listView, 0, new Class[]{UserCell.class}, null, new Drawable[]{Theme.avatar_photoDrawable, Theme.avatar_broadcastDrawable, Theme.avatar_savedDrawable}, null, Theme.key_avatar_text),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundRed),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundOrange),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundViolet),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundGreen),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundCyan),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundBlue),
                new ThemeDescription(null, 0, null, null, null, сellDelegate, Theme.key_avatar_backgroundPink),
        };
    }
}
