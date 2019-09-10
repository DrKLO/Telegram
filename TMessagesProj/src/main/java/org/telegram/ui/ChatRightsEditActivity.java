/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.DatePicker;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.DialogRadioCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.PollEditTextCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Cells.TextDetailCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Cells.UserCell2;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.Calendar;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ChatRightsEditActivity extends BaseFragment {

    private ListAdapter listViewAdapter;
    private RecyclerListView listView;

    private int chatId;
    private TLRPC.User currentUser;
    private TLRPC.Chat currentChat;
    private int currentType;
    private boolean isChannel;

    private boolean canEdit;

    private TLRPC.TL_chatAdminRights adminRights;
    private TLRPC.TL_chatAdminRights myAdminRights;
    private TLRPC.TL_chatBannedRights bannedRights;
    private TLRPC.TL_chatBannedRights defaultBannedRights;
    private String currentBannedRights = "";
    private String currentRank;
    private String initialRank;

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
    private int transferOwnerShadowRow;
    private int transferOwnerRow;
    private int rankHeaderRow;
    private int rankRow;
    private int rankInfoRow;

    private int sendMessagesRow;
    private int sendMediaRow;
    private int sendStickersRow;
    private int sendPollsRow;
    private int embedLinksRow;
    private int untilSectionRow;
    private int untilDateRow;

    private ChatRightsEditActivityDelegate delegate;

    private boolean isAddingNew;
    private boolean initialIsSet;

    public static final int TYPE_ADMIN = 0;
    public static final int TYPE_BANNED = 1;

    public interface ChatRightsEditActivityDelegate {
        void didSetRights(int rights, TLRPC.TL_chatAdminRights rightsAdmin, TLRPC.TL_chatBannedRights rightsBanned, String rank);
        void didChangeOwner(TLRPC.User user);
    }

    private final static int done_button = 1;

    public ChatRightsEditActivity(int userId, int channelId, TLRPC.TL_chatAdminRights rightsAdmin, TLRPC.TL_chatBannedRights rightsBannedDefault, TLRPC.TL_chatBannedRights rightsBanned, String rank, int type, boolean edit, boolean addingNew) {
        super();
        isAddingNew = addingNew;
        chatId = channelId;
        currentUser = MessagesController.getInstance(currentAccount).getUser(userId);
        currentType = type;
        canEdit = edit;
        currentChat = MessagesController.getInstance(currentAccount).getChat(chatId);
        if (rank == null) {
            rank = "";
        }
        initialRank = currentRank = rank;
        if (currentChat != null) {
            isChannel = ChatObject.isChannel(currentChat) && !currentChat.megagroup;
            myAdminRights = currentChat.admin_rights;
        }
        if (myAdminRights == null) {
            myAdminRights = new TLRPC.TL_chatAdminRights();
            myAdminRights.change_info = myAdminRights.post_messages = myAdminRights.edit_messages =
            myAdminRights.delete_messages = myAdminRights.ban_users = myAdminRights.invite_users =
            myAdminRights.pin_messages = myAdminRights.add_admins = true;
        }
        if (type == TYPE_ADMIN) {
            adminRights = new TLRPC.TL_chatAdminRights();
            if (rightsAdmin == null) {
                adminRights.change_info = myAdminRights.change_info;
                adminRights.post_messages = myAdminRights.post_messages;
                adminRights.edit_messages = myAdminRights.edit_messages;
                adminRights.delete_messages = myAdminRights.delete_messages;
                adminRights.ban_users = myAdminRights.ban_users;
                adminRights.invite_users = myAdminRights.invite_users;
                adminRights.pin_messages = myAdminRights.pin_messages;
                initialIsSet = false;
            } else {
                adminRights.change_info = rightsAdmin.change_info;
                adminRights.post_messages = rightsAdmin.post_messages;
                adminRights.edit_messages = rightsAdmin.edit_messages;
                adminRights.delete_messages = rightsAdmin.delete_messages;
                adminRights.ban_users = rightsAdmin.ban_users;
                adminRights.invite_users = rightsAdmin.invite_users;
                adminRights.pin_messages = rightsAdmin.pin_messages;
                adminRights.add_admins = rightsAdmin.add_admins;

                initialIsSet = adminRights.change_info || adminRights.post_messages || adminRights.edit_messages ||
                        adminRights.delete_messages || adminRights.ban_users || adminRights.invite_users ||
                        adminRights.pin_messages || adminRights.add_admins;
            }
        } else {
            defaultBannedRights = rightsBannedDefault;
            if (defaultBannedRights == null) {
                defaultBannedRights = new TLRPC.TL_chatBannedRights();
                defaultBannedRights.view_messages = defaultBannedRights.send_media = defaultBannedRights.send_messages =
                defaultBannedRights.embed_links = defaultBannedRights.send_stickers = defaultBannedRights.send_gifs =
                defaultBannedRights.send_games = defaultBannedRights.send_inline = defaultBannedRights.send_polls =
                defaultBannedRights.invite_users = defaultBannedRights.change_info = defaultBannedRights.pin_messages = false;
            }

            bannedRights = new TLRPC.TL_chatBannedRights();
            if (rightsBanned == null) {
                bannedRights.view_messages = bannedRights.send_media = bannedRights.send_messages =
                bannedRights.embed_links = bannedRights.send_stickers = bannedRights.send_gifs =
                bannedRights.send_games = bannedRights.send_inline = bannedRights.send_polls =
                bannedRights.invite_users = bannedRights.change_info = bannedRights.pin_messages = false;
            } else {
                bannedRights.view_messages = rightsBanned.view_messages;
                bannedRights.send_messages = rightsBanned.send_messages;
                bannedRights.send_media = rightsBanned.send_media;
                bannedRights.send_stickers = rightsBanned.send_stickers;
                bannedRights.send_gifs = rightsBanned.send_gifs;
                bannedRights.send_games = rightsBanned.send_games;
                bannedRights.send_inline = rightsBanned.send_inline;
                bannedRights.embed_links = rightsBanned.embed_links;
                bannedRights.send_polls = rightsBanned.send_polls;
                bannedRights.invite_users = rightsBanned.invite_users;
                bannedRights.change_info = rightsBanned.change_info;
                bannedRights.pin_messages = rightsBanned.pin_messages;
                bannedRights.until_date = rightsBanned.until_date;
            }
            if (defaultBannedRights.view_messages) {
                bannedRights.view_messages = true;
            }
            if (defaultBannedRights.send_messages) {
                bannedRights.send_messages = true;
            }
            if (defaultBannedRights.send_media) {
                bannedRights.send_media = true;
            }
            if (defaultBannedRights.send_stickers) {
                bannedRights.send_stickers = true;
            }
            if (defaultBannedRights.send_gifs) {
                bannedRights.send_gifs = true;
            }
            if (defaultBannedRights.send_games) {
                bannedRights.send_games = true;
            }
            if (defaultBannedRights.send_inline) {
                bannedRights.send_inline = true;
            }
            if (defaultBannedRights.embed_links) {
                bannedRights.embed_links = true;
            }
            if (defaultBannedRights.send_polls) {
                bannedRights.send_polls = true;
            }
            if (defaultBannedRights.invite_users) {
                bannedRights.invite_users = true;
            }
            if (defaultBannedRights.change_info) {
                bannedRights.change_info = true;
            }
            if (defaultBannedRights.pin_messages) {
                bannedRights.pin_messages = true;
            }

            currentBannedRights = ChatObject.getBannedRightsString(bannedRights);

            initialIsSet = rightsBanned == null || !rightsBanned.view_messages;
        }
        updateRows(false);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (currentType == TYPE_ADMIN) {
            actionBar.setTitle(LocaleController.getString("EditAdmin", R.string.EditAdmin));
        } else {
            actionBar.setTitle(LocaleController.getString("UserRestrictions", R.string.UserRestrictions));
        }

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (checkDiscard()) {
                        finishFragment();
                    }
                } else if (id == done_button) {
                    onDonePressed();
                }
            }
        });

        if (canEdit || !isChannel && currentChat.creator && UserObject.isUserSelf(currentUser)) {
            ActionBarMenu menu = actionBar.createMenu();
            menu.addItemWithWidth(done_button, R.drawable.ic_done, AndroidUtilities.dp(56), LocaleController.getString("Done", R.string.Done));
        }

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        fragmentView.setFocusableInTouchMode(true);

        listView = new RecyclerListView(context);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        ((DefaultItemAnimator) listView.getItemAnimator()).setDelayAnimations(false);
        listView.setLayoutManager(linearLayoutManager);
        listView.setAdapter(listViewAdapter = new ListAdapter(context));
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            if (!canEdit) {
                return;
            }
            if (position == 0) {
                Bundle args = new Bundle();
                args.putInt("user_id", currentUser.id);
                presentFragment(new ProfileActivity(args));
            } else if (position == removeAdminRow) {
                if (currentType == TYPE_ADMIN) {
                    MessagesController.getInstance(currentAccount).setUserAdminRole(chatId, currentUser, new TLRPC.TL_chatAdminRights(), currentRank, isChannel, getFragmentForAlert(0), isAddingNew);
                } else if (currentType == TYPE_BANNED) {
                    bannedRights = new TLRPC.TL_chatBannedRights();
                    bannedRights.view_messages = true;
                    bannedRights.send_media = true;
                    bannedRights.send_messages = true;
                    bannedRights.send_stickers = true;
                    bannedRights.send_gifs = true;
                    bannedRights.send_games = true;
                    bannedRights.send_inline = true;
                    bannedRights.embed_links = true;
                    bannedRights.pin_messages = true;
                    bannedRights.send_polls = true;
                    bannedRights.invite_users = true;
                    bannedRights.change_info = true;
                    bannedRights.until_date = 0;
                    MessagesController.getInstance(currentAccount).setUserBannedRole(chatId, currentUser, bannedRights, isChannel, getFragmentForAlert(0));
                }
                if (delegate != null) {
                    delegate.didSetRights(0, adminRights, bannedRights, currentRank);
                }
                finishFragment();
            } else if (position == transferOwnerRow) {
                initTransfer(null, null);
            } else if (position == untilDateRow) {
                if (getParentActivity() == null) {
                    return;
                }
                BottomSheet.Builder builder = new BottomSheet.Builder(context);
                builder.setApplyTopPadding(false);

                LinearLayout linearLayout = new LinearLayout(context);
                linearLayout.setOrientation(LinearLayout.VERTICAL);

                HeaderCell headerCell = new HeaderCell(context, true, 23, 15, false);
                headerCell.setHeight(47);
                headerCell.setText(LocaleController.getString("UserRestrictionsDuration", R.string.UserRestrictionsDuration));
                linearLayout.addView(headerCell);

                LinearLayout linearLayoutInviteContainer = new LinearLayout(context);
                linearLayoutInviteContainer.setOrientation(LinearLayout.VERTICAL);
                linearLayout.addView(linearLayoutInviteContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                BottomSheet.BottomSheetCell[] buttons = new BottomSheet.BottomSheetCell[5];

                for (int a = 0; a < buttons.length; a++) {
                    buttons[a] = new BottomSheet.BottomSheetCell(context, 0);
                    buttons[a].setPadding(AndroidUtilities.dp(7), 0, AndroidUtilities.dp(7), 0);
                    buttons[a].setTag(a);
                    buttons[a].setBackgroundDrawable(Theme.getSelectorDrawable(false));
                    String text;
                    switch (a) {
                        case 0:
                            text = LocaleController.getString("UserRestrictionsUntilForever", R.string.UserRestrictionsUntilForever);
                            break;
                        case 1:
                            text = LocaleController.formatPluralString("Days", 1);
                            break;
                        case 2:
                            text = LocaleController.formatPluralString("Weeks", 1);
                            break;
                        case 3:
                            text = LocaleController.formatPluralString("Months", 1);
                            break;
                        case 4:
                        default:
                            text = LocaleController.getString("UserRestrictionsCustom", R.string.UserRestrictionsCustom);
                            break;
                    }
                    buttons[a].setTextAndIcon(text, 0);
                    linearLayoutInviteContainer.addView(buttons[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    buttons[a].setOnClickListener(v2 -> {
                        Integer tag = (Integer) v2.getTag();
                        switch (tag) {
                            case 0:
                                bannedRights.until_date = 0;
                                listViewAdapter.notifyItemChanged(untilDateRow);
                                break;
                            case 1:
                                bannedRights.until_date = ConnectionsManager.getInstance(currentAccount).getCurrentTime() + 60 * 60 * 24;
                                listViewAdapter.notifyItemChanged(untilDateRow);
                                break;
                            case 2:
                                bannedRights.until_date = ConnectionsManager.getInstance(currentAccount).getCurrentTime() + 60 * 60 * 24 * 7;
                                listViewAdapter.notifyItemChanged(untilDateRow);
                                break;
                            case 3:
                                bannedRights.until_date = ConnectionsManager.getInstance(currentAccount).getCurrentTime() + 60 * 60 * 24 * 30;
                                listViewAdapter.notifyItemChanged(untilDateRow);
                                break;
                            case 4: {
                                Calendar calendar = Calendar.getInstance();
                                int year = calendar.get(Calendar.YEAR);
                                int monthOfYear = calendar.get(Calendar.MONTH);
                                int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
                                try {
                                    DatePickerDialog dialog = new DatePickerDialog(getParentActivity(), (view1, year1, month, dayOfMonth1) -> {
                                        Calendar calendar1 = Calendar.getInstance();
                                        calendar1.clear();
                                        calendar1.set(year1, month, dayOfMonth1);
                                        final int time = (int) (calendar1.getTime().getTime() / 1000);
                                        try {
                                            TimePickerDialog dialog13 = new TimePickerDialog(getParentActivity(), (view11, hourOfDay, minute) -> {
                                                bannedRights.until_date = time + hourOfDay * 3600 + minute * 60;
                                                listViewAdapter.notifyItemChanged(untilDateRow);
                                            }, 0, 0, true);
                                            dialog13.setButton(DialogInterface.BUTTON_POSITIVE, LocaleController.getString("Set", R.string.Set), dialog13);
                                            dialog13.setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString("Cancel", R.string.Cancel), (dialog131, which) -> {

                                            });
                                            showDialog(dialog13);
                                        } catch (Exception e) {
                                            FileLog.e(e);
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
                                    dialog.setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString("Cancel", R.string.Cancel), (dialog1, which) -> {

                                    });
                                    if (Build.VERSION.SDK_INT >= 21) {
                                        dialog.setOnShowListener(dialog12 -> {
                                            int count = datePicker.getChildCount();
                                            for (int b = 0; b < count; b++) {
                                                View child = datePicker.getChildAt(b);
                                                ViewGroup.LayoutParams layoutParams = child.getLayoutParams();
                                                layoutParams.width = LayoutHelper.MATCH_PARENT;
                                                child.setLayoutParams(layoutParams);
                                            }
                                        });
                                    }
                                    showDialog(dialog);
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                                break;
                            }
                        }
                        builder.getDismissRunnable().run();
                    });
                }
                builder.setCustomView(linearLayout);
                showDialog(builder.create());
            } else if (view instanceof TextCheckCell2) {
                TextCheckCell2 checkCell = (TextCheckCell2) view;
                if (checkCell.hasIcon()) {
                    Toast.makeText(getParentActivity(), LocaleController.getString("UserRestrictionsDisabled", R.string.UserRestrictionsDisabled), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!checkCell.isEnabled()) {
                    return;
                }
                checkCell.setChecked(!checkCell.isChecked());
                if (position == changeInfoRow) {
                    if (currentType == TYPE_ADMIN) {
                        adminRights.change_info = !adminRights.change_info;
                    } else {
                        bannedRights.change_info = !bannedRights.change_info;
                    }
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
                    if (currentType == TYPE_ADMIN) {
                        adminRights.invite_users = !adminRights.invite_users;
                    } else {
                        bannedRights.invite_users = !bannedRights.invite_users;
                    }
                } else if (position == pinMessagesRow) {
                    if (currentType == TYPE_ADMIN) {
                        adminRights.pin_messages = !adminRights.pin_messages;
                    } else {
                        bannedRights.pin_messages = !bannedRights.pin_messages;
                    }
                } else if (bannedRights != null) {
                    boolean disabled = !checkCell.isChecked();
                    if (position == sendMessagesRow) {
                        bannedRights.send_messages = !bannedRights.send_messages;
                    } else if (position == sendMediaRow) {
                        bannedRights.send_media = !bannedRights.send_media;
                    } else if (position == sendStickersRow) {
                        bannedRights.send_stickers = bannedRights.send_games = bannedRights.send_gifs = bannedRights.send_inline = !bannedRights.send_stickers;
                    } else if (position == embedLinksRow) {
                        bannedRights.embed_links = !bannedRights.embed_links;
                    } else if (position == sendPollsRow) {
                        bannedRights.send_polls = !bannedRights.send_polls;
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
                        if ((bannedRights.view_messages || bannedRights.send_messages) && !bannedRights.send_polls) {
                            bannedRights.send_polls = true;
                            RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(sendPollsRow);
                            if (holder != null) {
                                ((TextCheckCell2) holder.itemView).setChecked(false);
                            }
                        }
                        if ((bannedRights.view_messages || bannedRights.send_messages) && !bannedRights.send_stickers) {
                            bannedRights.send_stickers = bannedRights.send_games = bannedRights.send_gifs = bannedRights.send_inline = true;
                            RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(sendStickersRow);
                            if (holder != null) {
                                ((TextCheckCell2) holder.itemView).setChecked(false);
                            }
                        }
                        if ((bannedRights.view_messages || bannedRights.send_messages) && !bannedRights.embed_links) {
                            bannedRights.embed_links = true;
                            RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(embedLinksRow);
                            if (holder != null) {
                                ((TextCheckCell2) holder.itemView).setChecked(false);
                            }
                        }
                    } else {
                        if ((!bannedRights.send_messages || !bannedRights.embed_links || !bannedRights.send_inline || !bannedRights.send_media || !bannedRights.send_polls) && bannedRights.view_messages) {
                            bannedRights.view_messages = false;
                        }
                        if ((!bannedRights.embed_links || !bannedRights.send_inline || !bannedRights.send_media || !bannedRights.send_polls) && bannedRights.send_messages) {
                            bannedRights.send_messages = false;
                            RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(sendMessagesRow);
                            if (holder != null) {
                                ((TextCheckCell2) holder.itemView).setChecked(true);
                            }
                        }
                    }
                }
                updateRows(true);
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
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    private boolean isDefaultAdminRights() {
        return adminRights.change_info && adminRights.delete_messages && adminRights.ban_users && adminRights.invite_users && adminRights.pin_messages && !adminRights.add_admins ||
                !adminRights.change_info && !adminRights.delete_messages && !adminRights.ban_users && !adminRights.invite_users && !adminRights.pin_messages && !adminRights.add_admins;
    }

    private boolean hasAllAdminRights() {
        if (isChannel) {
            return adminRights.change_info && adminRights.post_messages && adminRights.edit_messages && adminRights.delete_messages && adminRights.invite_users && adminRights.add_admins;
        } else {
            return adminRights.change_info && adminRights.delete_messages && adminRights.ban_users && adminRights.invite_users && adminRights.pin_messages && adminRights.add_admins;
        }
    }

    private void initTransfer(TLRPC.InputCheckPasswordSRP srp, TwoStepVerificationActivity passwordFragment) {
        if (getParentActivity() == null) {
            return;
        }
        if (srp != null && !ChatObject.isChannel(currentChat)) {
            MessagesController.getInstance(currentAccount).convertToMegaGroup(getParentActivity(), chatId, this, param -> {
                chatId = param;
                currentChat = MessagesController.getInstance(currentAccount).getChat(param);
                initTransfer(srp, passwordFragment);
            });
            return;
        }
        TLRPC.TL_channels_editCreator req = new TLRPC.TL_channels_editCreator();
        if (ChatObject.isChannel(currentChat)) {
            req.channel = new TLRPC.TL_inputChannel();
            req.channel.channel_id = currentChat.id;
            req.channel.access_hash = currentChat.access_hash;
        } else {
            req.channel = new TLRPC.TL_inputChannelEmpty();
        }
        req.password = srp != null ? srp : new TLRPC.TL_inputCheckPasswordEmpty();
        req.user_id = getMessagesController().getInputUser(currentUser);
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null) {
                if (getParentActivity() == null) {
                    return;
                }
                if ("PASSWORD_HASH_INVALID".equals(error.text)) {
                    if (srp == null) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        if (isChannel) {
                            builder.setTitle(LocaleController.getString("EditAdminChannelTransfer", R.string.EditAdminChannelTransfer));
                        } else {
                            builder.setTitle(LocaleController.getString("EditAdminGroupTransfer", R.string.EditAdminGroupTransfer));
                        }
                        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("EditAdminTransferReadyAlertText", R.string.EditAdminTransferReadyAlertText, currentChat.title, UserObject.getFirstName(currentUser))));
                        builder.setPositiveButton(LocaleController.getString("EditAdminTransferChangeOwner", R.string.EditAdminTransferChangeOwner), (dialogInterface, i) -> {
                            TwoStepVerificationActivity fragment = new TwoStepVerificationActivity(0);
                            fragment.setDelegate(password -> initTransfer(password, fragment));
                            presentFragment(fragment);
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showDialog(builder.create());
                    }
                } else if ("PASSWORD_MISSING".equals(error.text) || error.text.startsWith("PASSWORD_TOO_FRESH_") || error.text.startsWith("SESSION_TOO_FRESH_")) {
                    if (passwordFragment != null) {
                        passwordFragment.needHideProgress();
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("EditAdminTransferAlertTitle", R.string.EditAdminTransferAlertTitle));

                    LinearLayout linearLayout = new LinearLayout(getParentActivity());
                    linearLayout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(2), AndroidUtilities.dp(24), 0);
                    linearLayout.setOrientation(LinearLayout.VERTICAL);
                    builder.setView(linearLayout);

                    TextView messageTextView = new TextView(getParentActivity());
                    messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                    if (isChannel) {
                        messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("EditChannelAdminTransferAlertText", R.string.EditChannelAdminTransferAlertText, UserObject.getFirstName(currentUser))));
                    } else {
                        messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("EditAdminTransferAlertText", R.string.EditAdminTransferAlertText, UserObject.getFirstName(currentUser))));
                    }
                    linearLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                    LinearLayout linearLayout2 = new LinearLayout(getParentActivity());
                    linearLayout2.setOrientation(LinearLayout.HORIZONTAL);
                    linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

                    ImageView dotImageView = new ImageView(getParentActivity());
                    dotImageView.setImageResource(R.drawable.list_circle);
                    dotImageView.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(11) : 0, AndroidUtilities.dp(9), LocaleController.isRTL ? 0 : AndroidUtilities.dp(11), 0);
                    dotImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));

                    messageTextView = new TextView(getParentActivity());
                    messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString("EditAdminTransferAlertText1", R.string.EditAdminTransferAlertText1)));
                    if (LocaleController.isRTL) {
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT));
                    } else {
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }

                    linearLayout2 = new LinearLayout(getParentActivity());
                    linearLayout2.setOrientation(LinearLayout.HORIZONTAL);
                    linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

                    dotImageView = new ImageView(getParentActivity());
                    dotImageView.setImageResource(R.drawable.list_circle);
                    dotImageView.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(11) : 0, AndroidUtilities.dp(9), LocaleController.isRTL ? 0 : AndroidUtilities.dp(11), 0);
                    dotImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));

                    messageTextView = new TextView(getParentActivity());
                    messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString("EditAdminTransferAlertText2", R.string.EditAdminTransferAlertText2)));
                    if (LocaleController.isRTL) {
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT));
                    } else {
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }

                    if ("PASSWORD_MISSING".equals(error.text)) {
                        builder.setPositiveButton(LocaleController.getString("EditAdminTransferSetPassword", R.string.EditAdminTransferSetPassword), (dialogInterface, i) -> presentFragment(new TwoStepVerificationActivity(0)));
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    } else {
                        messageTextView = new TextView(getParentActivity());
                        messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                        messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                        messageTextView.setText(LocaleController.getString("EditAdminTransferAlertText3", R.string.EditAdminTransferAlertText3));
                        linearLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

                        builder.setNegativeButton(LocaleController.getString("OK", R.string.OK), null);
                    }
                    showDialog(builder.create());
                } else if ("SRP_ID_INVALID".equals(error.text)) {
                    TLRPC.TL_account_getPassword getPasswordReq = new TLRPC.TL_account_getPassword();
                    ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                        if (error2 == null) {
                            TLRPC.TL_account_password currentPassword = (TLRPC.TL_account_password) response2;
                            passwordFragment.setCurrentPasswordInfo(null, currentPassword);
                            TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
                            initTransfer(passwordFragment.getNewSrpPassword(), passwordFragment);
                        }
                    }), ConnectionsManager.RequestFlagWithoutLogin);
                } else {
                    if (passwordFragment != null) {
                        passwordFragment.needHideProgress();
                        passwordFragment.finishFragment();
                    }
                    AlertsCreator.showAddUserAlert(error.text, ChatRightsEditActivity.this, isChannel, req);
                }
            } else {
                if (srp != null) {
                    delegate.didChangeOwner(currentUser);
                    removeSelfFromStack();
                    passwordFragment.needHideProgress();
                    passwordFragment.finishFragment();
                }
            }
        }));
    }

    private void updateRows(boolean update) {
        int transferOwnerShadowRowPrev = Math.min(transferOwnerShadowRow, transferOwnerRow);

        changeInfoRow = -1;
        postMessagesRow = -1;
        editMesagesRow = -1;
        deleteMessagesRow = -1;
        addAdminsRow = -1;
        banUsersRow = -1;
        addUsersRow = -1;
        pinMessagesRow = -1;
        rightsShadowRow = -1;
        removeAdminRow = -1;
        removeAdminShadowRow = -1;
        cantEditInfoRow = -1;
        transferOwnerShadowRow = -1;
        transferOwnerRow = -1;
        rankHeaderRow = -1;
        rankRow = -1;
        rankInfoRow = -1;

        sendMessagesRow = -1;
        sendMediaRow = -1;
        sendStickersRow = -1;
        sendPollsRow = -1;
        embedLinksRow = -1;
        untilSectionRow = -1;
        untilDateRow = -1;

        rowCount = 3;
        if (currentType == TYPE_ADMIN) {
            if (isChannel) {
                changeInfoRow = rowCount++;
                postMessagesRow = rowCount++;
                editMesagesRow = rowCount++;
                deleteMessagesRow = rowCount++;
                addUsersRow = rowCount++;
                addAdminsRow = rowCount++;
            } else {
                changeInfoRow = rowCount++;
                deleteMessagesRow = rowCount++;
                banUsersRow = rowCount++;
                addUsersRow = rowCount++;
                pinMessagesRow = rowCount++;
                addAdminsRow = rowCount++;
            }
        } else if (currentType == TYPE_BANNED) {
            sendMessagesRow = rowCount++;
            sendMediaRow = rowCount++;
            sendStickersRow = rowCount++;
            sendPollsRow = rowCount++;
            embedLinksRow = rowCount++;
            addUsersRow = rowCount++;
            pinMessagesRow = rowCount++;
            changeInfoRow = rowCount++;
            untilSectionRow = rowCount++;
            untilDateRow = rowCount++;
        }

        if (canEdit) {
            if (!isChannel && currentType == TYPE_ADMIN) {
                rightsShadowRow = rowCount++;
                rankHeaderRow = rowCount++;
                rankRow = rowCount++;
                rankInfoRow = rowCount++;
            }
            if (currentChat != null && currentChat.creator && currentType == TYPE_ADMIN && hasAllAdminRights() && !currentUser.bot) {
                if (rightsShadowRow == -1) {
                    transferOwnerShadowRow = rowCount++;
                }
                transferOwnerRow = rowCount++;
                if (rightsShadowRow != -1) {
                    transferOwnerShadowRow = rowCount++;
                }
            }
            if (initialIsSet) {
                if (rightsShadowRow == -1) {
                    rightsShadowRow = rowCount++;
                }
                removeAdminRow = rowCount++;
                removeAdminShadowRow = rowCount++;
            }
        } else {
            if (currentType == TYPE_ADMIN) {
                if (!isChannel && currentType == TYPE_ADMIN && (!currentRank.isEmpty() || currentChat.creator && UserObject.isUserSelf(currentUser))) {
                    rightsShadowRow = rowCount++;
                    rankHeaderRow = rowCount++;
                    rankRow = rowCount++;
                    rankInfoRow = rowCount++;
                } else {
                    cantEditInfoRow = rowCount++;
                }
            } else {
                rightsShadowRow = rowCount++;
            }
        }
        if (update) {
            if (transferOwnerShadowRowPrev == -1 && transferOwnerShadowRow != -1) {
                listViewAdapter.notifyItemRangeInserted(Math.min(transferOwnerShadowRow, transferOwnerRow), 2);
            } else if (transferOwnerShadowRowPrev != -1 && transferOwnerShadowRow == -1) {
                listViewAdapter.notifyItemRangeRemoved(transferOwnerShadowRowPrev, 2);
            }
        }
    }

    private void onDonePressed() {
        if (!ChatObject.isChannel(currentChat) && (currentType == TYPE_BANNED || currentType == TYPE_ADMIN && (!isDefaultAdminRights() || rankRow != -1 && currentRank.codePointCount(0, currentRank.length()) > MAX_RANK_LENGTH))) {
            MessagesController.getInstance(currentAccount).convertToMegaGroup(getParentActivity(), chatId, this, param -> {
                chatId = param;
                currentChat = MessagesController.getInstance(currentAccount).getChat(param);
                onDonePressed();
            });
            return;
        }
        if (currentType == TYPE_ADMIN) {
            if (rankRow != -1 && currentRank.codePointCount(0, currentRank.length()) > MAX_RANK_LENGTH) {
                listView.smoothScrollToPosition(rankRow);
                Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    v.vibrate(200);
                }
                RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(rankHeaderRow);
                if (holder != null) {
                    AndroidUtilities.shakeView(holder.itemView, 2, 0);
                }
                return;
            }
            if (isChannel) {
                adminRights.pin_messages = adminRights.ban_users = false;
            } else {
                adminRights.post_messages = adminRights.edit_messages = false;
            }
            MessagesController.getInstance(currentAccount).setUserAdminRole(chatId, currentUser, adminRights, currentRank, isChannel, getFragmentForAlert(1), isAddingNew);
            if (delegate != null) {
                delegate.didSetRights(
                        adminRights.change_info || adminRights.post_messages || adminRights.edit_messages ||
                        adminRights.delete_messages || adminRights.ban_users || adminRights.invite_users ||
                        adminRights.pin_messages || adminRights.add_admins ? 1 : 0, adminRights, bannedRights, currentRank);
            }
        } else if (currentType == TYPE_BANNED) {
            MessagesController.getInstance(currentAccount).setUserBannedRole(chatId, currentUser, bannedRights, isChannel, getFragmentForAlert(1));
            int rights;
            if (bannedRights.send_messages || bannedRights.send_stickers || bannedRights.embed_links || bannedRights.send_media ||
                    bannedRights.send_gifs || bannedRights.send_games || bannedRights.send_inline) {
                rights = 1;
            } else {
                bannedRights.until_date = 0;
                rights = 2;
            }
            if (delegate != null) {
                delegate.didSetRights(rights, adminRights, bannedRights, currentRank);
            }
        }
        finishFragment();
    }

    public void setDelegate(ChatRightsEditActivityDelegate channelRightsEditActivityDelegate) {
        delegate = channelRightsEditActivityDelegate;
    }

    private boolean checkDiscard() {
        boolean changed;
        if (currentType == TYPE_BANNED) {
            String newBannedRights = ChatObject.getBannedRightsString(bannedRights);
            changed = !currentBannedRights.equals(newBannedRights);
        } else {
            changed = !initialRank.equals(currentRank);
        }
        if (changed) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("UserRestrictionsApplyChanges", R.string.UserRestrictionsApplyChanges));
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(chatId);
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("UserRestrictionsApplyChangesText", R.string.UserRestrictionsApplyChangesText, chat.title)));
            builder.setPositiveButton(LocaleController.getString("ApplyTheme", R.string.ApplyTheme), (dialogInterface, i) -> onDonePressed());
            builder.setNegativeButton(LocaleController.getString("PassportDiscard", R.string.PassportDiscard), (dialog, which) -> finishFragment());
            showDialog(builder.create());
            return false;
        }
        return true;
    }

    private final static int MAX_RANK_LENGTH = 16;

    private void setTextLeft(View cell) {
        if (cell instanceof HeaderCell) {
            HeaderCell headerCell = (HeaderCell) cell;
            int left = MAX_RANK_LENGTH - (currentRank != null ? currentRank.codePointCount(0, currentRank.length()) : 0);
            if (left <= MAX_RANK_LENGTH - MAX_RANK_LENGTH * 0.7f) {
                headerCell.setText2(String.format("%d", left));
                SimpleTextView textView = headerCell.getTextView2();
                String key = left < 0 ? Theme.key_windowBackgroundWhiteRedText5 : Theme.key_windowBackgroundWhiteGrayText3;
                textView.setTextColor(Theme.getColor(key));
                textView.setTag(key);
            } else {
                headerCell.setText2("");
            }
        }
    }

    @Override
    public boolean onBackPressed() {
        return checkDiscard();
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private boolean ignoreTextChange;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (!canEdit) {
                return false;
            }
            int type = holder.getItemViewType();
            if (currentType == TYPE_ADMIN && type == 4) {
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
                    view = new UserCell2(mContext, 4, 0);
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
                    view = new HeaderCell(mContext, false, 21, 15, true);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                    view = new TextCheckCell2(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 5:
                    view = new ShadowSectionCell(mContext);
                    break;
                case 6:
                    view = new TextDetailCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 7:
                default:
                    PollEditTextCell cell = new PollEditTextCell(mContext, null);
                    cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    cell.addTextWatcher(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {

                        }

                        @Override
                        public void afterTextChanged(Editable s) {
                            if (ignoreTextChange) {
                                return;
                            }
                            currentRank = s.toString();
                            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(rankHeaderRow);
                            if (holder != null) {
                                setTextLeft(holder.itemView);
                            }
                        }
                    });
                    view = cell;
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    UserCell2 userCell2 = (UserCell2) holder.itemView;
                    userCell2.setData(currentUser, null, null, 0);
                    break;
                case 1:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == cantEditInfoRow) {
                        privacyCell.setText(LocaleController.getString("EditAdminCantEdit", R.string.EditAdminCantEdit));
                    } else if (position == rankInfoRow) {
                        String hint;
                        if (UserObject.isUserSelf(currentUser) && currentChat.creator) {
                            hint = LocaleController.getString("ChannelCreator", R.string.ChannelCreator);
                        } else {
                            hint = LocaleController.getString("ChannelAdmin", R.string.ChannelAdmin);
                        }
                        privacyCell.setText(LocaleController.formatString("EditAdminRankInfo", R.string.EditAdminRankInfo, hint));
                    }
                    break;
                case 2:
                    TextSettingsCell actionCell = (TextSettingsCell) holder.itemView;
                    if (position == removeAdminRow) {
                        actionCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText5));
                        actionCell.setTag(Theme.key_windowBackgroundWhiteRedText5);
                        if (currentType == TYPE_ADMIN) {
                            actionCell.setText(LocaleController.getString("EditAdminRemoveAdmin", R.string.EditAdminRemoveAdmin), false);
                        } else if (currentType == TYPE_BANNED) {
                            actionCell.setText(LocaleController.getString("UserRestrictionsBlock", R.string.UserRestrictionsBlock), false);
                        }
                    } else if (position == transferOwnerRow) {
                        actionCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        actionCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        if (isChannel) {
                            actionCell.setText(LocaleController.getString("EditAdminChannelTransfer", R.string.EditAdminChannelTransfer), false);
                        } else {
                            actionCell.setText(LocaleController.getString("EditAdminGroupTransfer", R.string.EditAdminGroupTransfer), false);
                        }
                    }
                    break;
                case 3:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == 2) {
                        if (currentType == TYPE_ADMIN) {
                            headerCell.setText(LocaleController.getString("EditAdminWhatCanDo", R.string.EditAdminWhatCanDo));
                        } else if (currentType == TYPE_BANNED) {
                            headerCell.setText(LocaleController.getString("UserRestrictionsCanDo", R.string.UserRestrictionsCanDo));
                        }
                    } else if (position == rankHeaderRow) {
                        headerCell.setText(LocaleController.getString("EditAdminRank", R.string.EditAdminRank));
                    }
                    break;
                case 4:
                    TextCheckCell2 checkCell = (TextCheckCell2) holder.itemView;
                    if (position == changeInfoRow) {
                        if (currentType == TYPE_ADMIN) {
                            if (isChannel) {
                                checkCell.setTextAndCheck(LocaleController.getString("EditAdminChangeChannelInfo", R.string.EditAdminChangeChannelInfo), adminRights.change_info, true);
                            } else {
                                checkCell.setTextAndCheck(LocaleController.getString("EditAdminChangeGroupInfo", R.string.EditAdminChangeGroupInfo), adminRights.change_info, true);
                            }
                        } else if (currentType == TYPE_BANNED) {
                            checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsChangeInfo", R.string.UserRestrictionsChangeInfo), !bannedRights.change_info && !defaultBannedRights.change_info, false);
                            checkCell.setIcon(defaultBannedRights.change_info ? R.drawable.permission_locked : 0);
                        }
                    } else if (position == postMessagesRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("EditAdminPostMessages", R.string.EditAdminPostMessages), adminRights.post_messages, true);
                    } else if (position == editMesagesRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("EditAdminEditMessages", R.string.EditAdminEditMessages), adminRights.edit_messages, true);
                    } else if (position == deleteMessagesRow) {
                        if (isChannel) {
                            checkCell.setTextAndCheck(LocaleController.getString("EditAdminDeleteMessages", R.string.EditAdminDeleteMessages), adminRights.delete_messages, true);
                        } else {
                            checkCell.setTextAndCheck(LocaleController.getString("EditAdminGroupDeleteMessages", R.string.EditAdminGroupDeleteMessages), adminRights.delete_messages, true);
                        }
                    } else if (position == addAdminsRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("EditAdminAddAdmins", R.string.EditAdminAddAdmins), adminRights.add_admins, false);
                    } else if (position == banUsersRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("EditAdminBanUsers", R.string.EditAdminBanUsers), adminRights.ban_users, true);
                    } else if (position == addUsersRow) {
                        if (currentType == TYPE_ADMIN) {
                            if (ChatObject.isActionBannedByDefault(currentChat, ChatObject.ACTION_INVITE)) {
                                checkCell.setTextAndCheck(LocaleController.getString("EditAdminAddUsers", R.string.EditAdminAddUsers), adminRights.invite_users, true);
                            } else {
                                checkCell.setTextAndCheck(LocaleController.getString("EditAdminAddUsersViaLink", R.string.EditAdminAddUsersViaLink), adminRights.invite_users, true);
                            }
                        } else if (currentType == TYPE_BANNED) {
                            checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsInviteUsers", R.string.UserRestrictionsInviteUsers), !bannedRights.invite_users && !defaultBannedRights.invite_users, true);
                            checkCell.setIcon(defaultBannedRights.invite_users ? R.drawable.permission_locked : 0);
                        }
                    } else if (position == pinMessagesRow) {
                        if (currentType == TYPE_ADMIN) {
                            checkCell.setTextAndCheck(LocaleController.getString("EditAdminPinMessages", R.string.EditAdminPinMessages), adminRights.pin_messages, true);
                        } else if (currentType == TYPE_BANNED) {
                            checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsPinMessages", R.string.UserRestrictionsPinMessages), !bannedRights.pin_messages && !defaultBannedRights.pin_messages, true);
                            checkCell.setIcon(defaultBannedRights.pin_messages ? R.drawable.permission_locked : 0);
                        }
                    } else if (position == sendMessagesRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsSend", R.string.UserRestrictionsSend), !bannedRights.send_messages && !defaultBannedRights.send_messages, true);
                        checkCell.setIcon(defaultBannedRights.send_messages ? R.drawable.permission_locked : 0);
                    } else if (position == sendMediaRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsSendMedia", R.string.UserRestrictionsSendMedia), !bannedRights.send_media && !defaultBannedRights.send_media, true);
                        checkCell.setIcon(defaultBannedRights.send_media ? R.drawable.permission_locked : 0);
                    } else if (position == sendStickersRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsSendStickers", R.string.UserRestrictionsSendStickers), !bannedRights.send_stickers && !defaultBannedRights.send_stickers, true);
                        checkCell.setIcon(defaultBannedRights.send_stickers ? R.drawable.permission_locked : 0);
                    } else if (position == embedLinksRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsEmbedLinks", R.string.UserRestrictionsEmbedLinks), !bannedRights.embed_links && !defaultBannedRights.embed_links, true);
                        checkCell.setIcon(defaultBannedRights.embed_links ? R.drawable.permission_locked : 0);
                    } else if (position == sendPollsRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsSendPolls", R.string.UserRestrictionsSendPolls), !bannedRights.send_polls && !defaultBannedRights.send_polls, true);
                        checkCell.setIcon(defaultBannedRights.send_polls ? R.drawable.permission_locked : 0);
                    }

                    if (position == sendMediaRow || position == sendStickersRow || position == embedLinksRow || position == sendPollsRow) {
                        checkCell.setEnabled(!bannedRights.send_messages && !bannedRights.view_messages && !defaultBannedRights.send_messages && !defaultBannedRights.view_messages);
                    } else if (position == sendMessagesRow) {
                        checkCell.setEnabled(!bannedRights.view_messages && !defaultBannedRights.view_messages);
                    }
                    break;
                case 5:
                    ShadowSectionCell shadowCell = (ShadowSectionCell) holder.itemView;
                    if (position == rightsShadowRow) {
                        shadowCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, removeAdminRow == -1 && rankRow == -1 ? R.drawable.greydivider_bottom : R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == removeAdminShadowRow) {
                        shadowCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == rankInfoRow) {
                        shadowCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, canEdit ? R.drawable.greydivider : R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        shadowCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                case 6:
                    TextDetailCell detailCell = (TextDetailCell) holder.itemView;
                    if (position == untilDateRow) {
                        String value;
                        if (bannedRights.until_date == 0 || Math.abs(bannedRights.until_date - System.currentTimeMillis() / 1000) > 10 * 365 * 24 * 60 * 60) {
                            value = LocaleController.getString("UserRestrictionsUntilForever", R.string.UserRestrictionsUntilForever);
                        } else {
                            value = LocaleController.formatDateForBan(bannedRights.until_date);
                        }
                        detailCell.setTextAndValue(LocaleController.getString("UserRestrictionsDuration", R.string.UserRestrictionsDuration), value, false);
                    }
                    break;
                case 7:
                    PollEditTextCell textCell = (PollEditTextCell) holder.itemView;
                    String hint;
                    if (UserObject.isUserSelf(currentUser) && currentChat.creator) {
                        hint = LocaleController.getString("ChannelCreator", R.string.ChannelCreator);
                    } else {
                        hint = LocaleController.getString("ChannelAdmin", R.string.ChannelAdmin);
                    }
                    ignoreTextChange = true;
                    textCell.getTextView().setEnabled(canEdit || currentChat.creator);
                    textCell.getTextView().setSingleLine(true);
                    textCell.getTextView().setImeOptions(EditorInfo.IME_ACTION_DONE);
                    textCell.setTextAndHint(currentRank, hint, false);
                    ignoreTextChange = false;
                    break;
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            if (holder.getAdapterPosition() == rankHeaderRow) {
                setTextLeft(holder.itemView);
            }
        }

        @Override
        public void onViewDetachedFromWindow(RecyclerView.ViewHolder holder) {
            if (holder.getAdapterPosition() == rankRow && getParentActivity() != null) {
                AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return 0;
            } else if (position == 1 || position == rightsShadowRow || position == removeAdminShadowRow || position == untilSectionRow || position == transferOwnerShadowRow) {
                return 5;
            } else if (position == 2 || position == rankHeaderRow) {
                return 3;
            } else if (position == changeInfoRow || position == postMessagesRow || position == editMesagesRow || position == deleteMessagesRow ||
                    position == addAdminsRow || position == banUsersRow || position == addUsersRow || position == pinMessagesRow ||
                    position == sendMessagesRow || position == sendMediaRow || position == sendStickersRow || position == embedLinksRow ||
                    position == sendPollsRow) {
                return 4;
            } else if (position == cantEditInfoRow || position == rankInfoRow) {
                return 1;
            } else if (position == untilDateRow) {
                return 6;
            } else if (position == rankRow) {
                return 7;
            } else {
                return 2;
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
                    if (child instanceof UserCell2) {
                        ((UserCell2) child).update(0);
                    }
                }
            }
        };

        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{UserCell2.class, TextSettingsCell.class, TextCheckCell2.class, HeaderCell.class, TextDetailCell.class, PollEditTextCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
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

                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText5),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText),
                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueImageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon),

                new ThemeDescription(listView, 0, new Class[]{TextDetailCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextDetailCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),

                new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switch2Track),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switch2TrackChecked),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),

                new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{HeaderCell.class}, new String[]{"textView2"}, null, null, null, Theme.key_windowBackgroundWhiteRedText5),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{HeaderCell.class}, new String[]{"textView2"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),

                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{PollEditTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, ThemeDescription.FLAG_HINTTEXTCOLOR, new Class[]{PollEditTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteHintText),

                new ThemeDescription(listView, 0, new Class[]{UserCell2.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{UserCell2.class}, new String[]{"statusColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteGrayText),
                new ThemeDescription(listView, 0, new Class[]{UserCell2.class}, new String[]{"statusOnlineColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteBlueText),
                new ThemeDescription(listView, 0, new Class[]{UserCell2.class}, null, new Drawable[]{Theme.avatar_savedDrawable}, null, Theme.key_avatar_text),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue),
                new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink),

                new ThemeDescription(null, 0, new Class[]{DialogRadioCell.class}, new String[]{"textView"}, null, null, null, Theme.key_dialogTextBlack),
                new ThemeDescription(null, 0, new Class[]{DialogRadioCell.class}, new String[]{"textView"}, null, null, null, Theme.key_dialogTextGray2),
                new ThemeDescription(null, ThemeDescription.FLAG_CHECKBOX, new Class[]{DialogRadioCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_dialogRadioBackground),
                new ThemeDescription(null, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{DialogRadioCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_dialogRadioBackgroundChecked),
        };
    }
}
