/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.InviteUserCell;
import org.telegram.ui.Cells.LetterSectionCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.ContactsEmptyView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;

public class ContactsAdapter extends RecyclerListView.SectionsAdapter {

    public boolean includeSearch;
    private final int currentAccount = UserConfig.selectedAccount;
    private final Context mContext;
    private final int onlyUsers;
    private final boolean needPhonebook;
    private final LongSparseArray<TLRPC.User> ignoreUsers;
    private final LongSparseArray<TLRPC.User> selectedContacts;
    private ArrayList<TLRPC.TL_contact> onlineContacts;
    private final boolean isAdmin;
    private int sortType;
    private final boolean isChannel;
    private boolean disableSections;
    private boolean isEmpty;
    private boolean hasPhonebook;
    public boolean isEmptyWithMainTabs;

    BaseFragment fragment;

    public ContactsAdapter(Context context, BaseFragment fragment, int onlyUsersType, boolean showPhoneBook, LongSparseArray<TLRPC.User> usersToIgnore, LongSparseArray<TLRPC.User> selectedContacts, int flags) {
        mContext = context;
        onlyUsers = onlyUsersType;
        needPhonebook = showPhoneBook;
        ignoreUsers = usersToIgnore;
        this.selectedContacts = selectedContacts;
        isAdmin = flags != 0;
        isChannel = flags == 2;
        this.fragment = fragment;
    }

    public void setDisableSections(boolean value) {
        disableSections = value;
    }

    public static final int SORT_TYPE_NONE = 0;
    public static final int SORT_TYPE_BY_NAME = 1;
    public static final int SORT_TYPE_BY_TIME = 2;

    public void setSortType(int value, boolean force) {
        sortType = value;
        if (sortType == SORT_TYPE_BY_TIME) {
            if (onlineContacts == null || force) {
                onlineContacts = new ArrayList<>(ContactsController.getInstance(currentAccount).contacts);
                long selfId = UserConfig.getInstance(currentAccount).clientUserId;
                for (int a = 0, N = onlineContacts.size(); a < N; a++) {
                    if (onlineContacts.get(a).user_id == selfId) {
                        onlineContacts.remove(a);
                        break;
                    }
                }
            }
            sortOnlineContacts();
        } else {
            notifyDataSetChanged();
        }
    }

    public boolean isEmpty() {
        return isEmpty;
    }

    public void sortOnlineContacts() {
        if (onlineContacts == null) {
            return;
        }
        try {
            int currentTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            MessagesController messagesController = MessagesController.getInstance(currentAccount);
            Collections.sort(onlineContacts, (o1, o2) -> {
                TLRPC.User user1 = messagesController.getUser(o2.user_id);
                TLRPC.User user2 = messagesController.getUser(o1.user_id);
                int status1 = 0;
                int status2 = 0;
                if (user1 != null) {
                    if (user1.self) {
                        status1 = currentTime + 50000;
                    } else if (user1.status != null) {
                        status1 = user1.status.expires;
                    }
                }
                if (user2 != null) {
                    if (user2.self) {
                        status2 = currentTime + 50000;
                    } else if (user2.status != null) {
                        status2 = user2.status.expires;
                    }
                }
                if (status1 > 0 && status2 > 0) {
                    if (status1 > status2) {
                        return 1;
                    } else if (status1 < status2) {
                        return -1;
                    }
                    return 0;
                } else if (status1 < 0 && status2 < 0) {
                    if (status1 > status2) {
                        return 1;
                    } else if (status1 < status2) {
                        return -1;
                    }
                    return 0;
                } else if (status1 < 0 && status2 > 0 || status1 == 0 && status2 != 0) {
                    return -1;
                } else if (status2 < 0 && status1 > 0 || status2 == 0 && status1 != 0) {
                    return 1;
                }
                return 0;
            });
            notifyDataSetChanged();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public Object getItem(int section, int position) {
        if (isEmptyWithMainTabs && section == 1 && position > 1) {
            final int index = position - 2;
            if (index < ContactsController.getInstance(currentAccount).phoneBookContacts.size()) {
                return ContactsController.getInstance(currentAccount).phoneBookContacts.get(index);
            }
        }

        if (getItemViewType(section, position) == 2) {
            return "Header";
        }

        HashMap<String, ArrayList<TLRPC.TL_contact>> usersSectionsDict = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).usersMutualSectionsDict : ContactsController.getInstance(currentAccount).usersSectionsDict;
        ArrayList<String> sortedUsersSectionsArray = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray : ContactsController.getInstance(currentAccount).sortedUsersSectionsArray;

        if (onlyUsers != 0 && !isAdmin) {
            if (section < sortedUsersSectionsArray.size()) {
                ArrayList<TLRPC.TL_contact> arr = usersSectionsDict.get(sortedUsersSectionsArray.get(section));
                if (position < arr.size()) {
                    return MessagesController.getInstance(currentAccount).getUser(arr.get(position).user_id);
                }
            }
            return null;
        } else {
            if (section == 0) {
                return null;
            } else {
                if (sortType == SORT_TYPE_BY_TIME) {
                    if (section == 1) {
                        if (position < onlineContacts.size()) {
                            return MessagesController.getInstance(currentAccount).getUser(onlineContacts.get(position).user_id);
                        }
                        return null;
                    }
                } else {
                    if (section - 1 < sortedUsersSectionsArray.size()) {
                        ArrayList<TLRPC.TL_contact> arr = usersSectionsDict.get(sortedUsersSectionsArray.get(section - 1));
                        if (position < arr.size()) {
                            return MessagesController.getInstance(currentAccount).getUser(arr.get(position).user_id);
                        }
                        return null;
                    }
                }
            }
        }
        if (needPhonebook && position >= 0 && position < ContactsController.getInstance(currentAccount).phoneBookContacts.size()) {
            return ContactsController.getInstance(currentAccount).phoneBookContacts.get(position);
        }
        return null;
    }


    public int getHash(int section, int position) {
        int sectionIndex = section;
        Object item = getItem(section, position);
        return Objects.hash(sectionIndex * -49612, item);
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder, int section, int row) {
        if (isEmptyWithMainTabs) {
            return section == 1 && row > 1;
        }

        HashMap<String, ArrayList<TLRPC.TL_contact>> usersSectionsDict = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).usersMutualSectionsDict : ContactsController.getInstance(currentAccount).usersSectionsDict;
        ArrayList<String> sortedUsersSectionsArray = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray : ContactsController.getInstance(currentAccount).sortedUsersSectionsArray;

        if (onlyUsers != 0 && !isAdmin) {
            if (isEmpty) {
                return false;
            }
            ArrayList<TLRPC.TL_contact> arr = usersSectionsDict.get(sortedUsersSectionsArray.get(section));
            return row < arr.size();
        } else {
            if (section == 0) {
                if (isAdmin) {
                    return row < 1;
                } else if (needPhonebook) {
                    return row < 2;
                } else {
                    return row < 3;
                }
            } else {
                if (isEmpty) {
                    return false;
                }
                if (sortType == SORT_TYPE_BY_TIME) {
                    if (section == 1) {
                        return row < onlineContacts.size();
                    }
                } else {
                    if (section - 1 < sortedUsersSectionsArray.size()) {
                        ArrayList<TLRPC.TL_contact> arr = usersSectionsDict.get(sortedUsersSectionsArray.get(section - 1));
                        return row < arr.size();
                    }
                }
            }
        }
        return true;
    }

    @Override
    public int getSectionCount() {
        int count;
        isEmpty = false;
        if (sortType == SORT_TYPE_BY_TIME) {
            count = 1;
            isEmpty = onlineContacts.isEmpty();
        } else {
            ArrayList<String> sortedUsersSectionsArray = onlyUsers == 2 ?
                ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray :
                ContactsController.getInstance(currentAccount).sortedUsersSectionsArray;

            count = sortedUsersSectionsArray.size();
            if (count == 0) {
                isEmpty = true;
                count = 1;
            }
        }
        if (onlyUsers == 0) {
            count++;
        }
        if (isAdmin) {
            count++;
        }
        if (needPhonebook) {
            //count++;
        }

        hasPhonebook = !ContactsController.getInstance(currentAccount).phoneBookContacts.isEmpty();
        isEmptyWithMainTabs = isEmpty && needPhonebook && !isAdmin && onlyUsers == 0;
        if (isEmptyWithMainTabs) {
            // empty + invite friends
            return hasPhonebook ? 2 : 1;
        }

        return count;
    }

    @Override
    public int getCountForSection(int section) {
        return getCountForSectionInternal(section);
    }

    private int getCountForSectionInternal(int section) {
        if (isEmptyWithMainTabs) {
            if (section == 0) { // empty
                return (includeSearch ? 1 : 0) + 1;
            } else if (section == 1) {  // invite contacts
                return 2 + ContactsController.getInstance(currentAccount).phoneBookContacts.size();
            }
            return 0;
        }

        HashMap<String, ArrayList<TLRPC.TL_contact>> usersSectionsDict = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).usersMutualSectionsDict : ContactsController.getInstance(currentAccount).usersSectionsDict;
        ArrayList<String> sortedUsersSectionsArray = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray : ContactsController.getInstance(currentAccount).sortedUsersSectionsArray;

        if (onlyUsers != 0 && !isAdmin) {
            if (isEmpty) {
                return 1;
            }
            if (section < sortedUsersSectionsArray.size()) {
                ArrayList<TLRPC.TL_contact> arr = usersSectionsDict.get(sortedUsersSectionsArray.get(section));
                int count = arr.size();
                if (section != (sortedUsersSectionsArray.size() - 1) || needPhonebook) {
                    count++;
                }
                return count;
            }
        } else {
            if (section == 0) {
                if (isEmpty) {
                    return (includeSearch ? 1 : 0) + 1;
                }

                if (isAdmin) {
                    return (includeSearch ? 1 : 0) + 3;
                } else if (needPhonebook) {
                    return (includeSearch ? 1 : 0) + 4;
                } else {
                    return (includeSearch ? 1 : 0) + 4;
                }
            } else {
                if (isEmpty) {
                    return 1;
                }
                if (sortType == SORT_TYPE_BY_TIME) {
                    if (section == 1) {
                        return onlineContacts.isEmpty() ? 0 : onlineContacts.size(); // + 1;
                    }
                } else {
                    if (section - 1 < sortedUsersSectionsArray.size()) {
                        ArrayList<TLRPC.TL_contact> arr = usersSectionsDict.get(sortedUsersSectionsArray.get(section - 1));
                        int count = arr.size();
                        if (section - 1 != (sortedUsersSectionsArray.size() - 1) || needPhonebook) {
                        //    count++;
                        }
                        return count;
                    }
                }
            }
        }
        if (needPhonebook) {
            return ContactsController.getInstance(currentAccount).phoneBookContacts.size();
        }
        return 0;
    }

    @Override
    public View getSectionHeaderView(int section, View view) {
        HashMap<String, ArrayList<TLRPC.TL_contact>> usersSectionsDict = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).usersMutualSectionsDict : ContactsController.getInstance(currentAccount).usersSectionsDict;
        ArrayList<String> sortedUsersSectionsArray = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray : ContactsController.getInstance(currentAccount).sortedUsersSectionsArray;

        if (view == null) {
            view = new LetterSectionCell(mContext);
        }
        LetterSectionCell cell = (LetterSectionCell) view;
        if (sortType == SORT_TYPE_BY_TIME || disableSections || isEmpty) {
            cell.setLetter("");
        } else {
            if (onlyUsers != 0 && !isAdmin) {
                if (section < sortedUsersSectionsArray.size()) {
                    cell.setLetter(sortedUsersSectionsArray.get(section));
                } else {
                    cell.setLetter("");
                }
            } else {
                if (section == 0) {
                    cell.setLetter("");
                } else if (section - 1 < sortedUsersSectionsArray.size()) {
                    cell.setLetter(sortedUsersSectionsArray.get(section - 1));
                } else {
                    cell.setLetter("");
                }
            }
        }
        return view;
    }

    public static final int ID_SEARCH = 9;

    private static final int USER_CELL = 0;
    private static final int TEXT_CELL = 1;
    private static final int GRAY_CELL = 2;
    private static final int DIVIDER_CELL = 3;
    private static final int EMPTY_CELL = 4;
    private static final int SHADOW_CELL = 5;
    private static final int HEADER_CELL = 7;
    private static final int INVITE_CELL = 8;
    private static final int SEARCH_CELL = 9;

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case INVITE_CELL: {
                view = new InviteUserCell(mContext, false);
                break;
            }
            case HEADER_CELL: {
                view = new HeaderCell(mContext, Theme.key_windowBackgroundWhiteBlueHeader, 21, 14, 5, false, null);
                break;
            }
            case SEARCH_CELL: {
                view = new View(mContext) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(
                            MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(dp(52), MeasureSpec.EXACTLY)
                        );
                    }
                };
                view.setId(ID_SEARCH);
                view.setTag(RecyclerListView.TAG_NOT_SECTION);
                break;
            }
            case USER_CELL: {
                UserCell cell = new UserCell(mContext, 58, 1, false);
                cell.setCallCellStyle(58);
                view = cell;
                break;
            }
            case TEXT_CELL: {
                view = new TextCell(mContext);
                break;
            }
            case GRAY_CELL:
                view = new GraySectionCell(mContext);
                break;
            case DIVIDER_CELL:
                view = new DividerCell(mContext);
                view.setPadding(AndroidUtilities.dp(LocaleController.isRTL ? 28 : 72), AndroidUtilities.dp(8), AndroidUtilities.dp(LocaleController.isRTL ? 72 : 28), AndroidUtilities.dp(8));
                break;
            case EMPTY_CELL:
                FrameLayout frameLayout = new FrameLayout(mContext) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        if (isEmptyWithMainTabs && hasPhonebook) {
                            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                            return;
                        }

                        int height = MeasureSpec.getSize(heightMeasureSpec);
                        if (height == 0) {
                            height = parent.getMeasuredHeight();
                        }
                        if (height == 0) {
                            height = AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight() - AndroidUtilities.statusBarHeight;
                        }
                        int cellHeight = AndroidUtilities.dp(50);
                        int totalHeight = onlyUsers != 0 ? 0 : cellHeight + AndroidUtilities.dp(30);
                        if (!isAdmin && !needPhonebook) {
                            totalHeight += cellHeight;
                        }

                        height -= parent.getPaddingTop();
                        height -= parent.getPaddingBottom();

                        if (totalHeight < height) {
                            height = height - totalHeight;
                        } else {
                            height = 0;
                        }
                        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                    }
                };
                ContactsEmptyView emptyView = new ContactsEmptyView(mContext);
                frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
                frameLayout.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                frameLayout.setTag(RecyclerListView.TAG_NOT_SECTION);
                view = frameLayout;
                break;
            case SHADOW_CELL:
            default:
                view = new ShadowSectionCell(mContext);
                break;

        }
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(int section, int position, RecyclerView.ViewHolder holder) {
        if (section == 0 && includeSearch) {
            if (position == 0) return;
            position--;
        }
        switch (holder.getItemViewType()) {
            case EMPTY_CELL:
                holder.itemView.setPadding(0, dp(!hasPhonebook ? 96 : 25), 0, dp(18));
                break;
            case INVITE_CELL:
                InviteUserCell inviteUserCell = (InviteUserCell) holder.itemView;
                final int index = position - 2;
                if (index >= 0 && index < ContactsController.getInstance(currentAccount).phoneBookContacts.size()) {
                    ContactsController.Contact contact = ContactsController.getInstance(currentAccount).phoneBookContacts.get(index);
                    inviteUserCell.setUser(contact, null);
                }
                break;
            case USER_CELL:
                UserCell userCell = (UserCell) holder.itemView;
                userCell.storyParams.drawSegments = false;
                userCell.setAvatarPadding(sortType == SORT_TYPE_BY_TIME || disableSections ? 7 : 58, 1);
                ArrayList<TLRPC.TL_contact> arr;
                if (sortType == SORT_TYPE_BY_TIME) {
                    arr = onlineContacts;
                } else {
                    HashMap<String, ArrayList<TLRPC.TL_contact>> usersSectionsDict = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).usersMutualSectionsDict : ContactsController.getInstance(currentAccount).usersSectionsDict;
                    ArrayList<String> sortedUsersSectionsArray = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray : ContactsController.getInstance(currentAccount).sortedUsersSectionsArray;
                    arr = usersSectionsDict.get(sortedUsersSectionsArray.get(section - (onlyUsers != 0 && !isAdmin ? 0 : 1)));
                }
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(arr.get(position).user_id);
                userCell.setData(user, null, null, 0);
                userCell.setChecked(selectedContacts.indexOfKey(user.id) >= 0, false);
                if (ignoreUsers != null) {
                    if (ignoreUsers.indexOfKey(user.id) >= 0) {
                        userCell.setAlpha(0.5f);
                    } else {
                        userCell.setAlpha(1.0f);
                    }
                }
                break;
            case TEXT_CELL:
                TextCell textCell = (TextCell) holder.itemView;
                if (needPhonebook || !isAdmin) {
                    textCell.setColors(Theme.key_windowBackgroundWhiteBlackText, Theme.key_windowBackgroundWhiteBlackText);
                } else {
                    textCell.setColors(Theme.key_telegram_color_text, Theme.key_telegram_color_text);
                }
                if (section == 0) {
                    if (needPhonebook) {
                        if (position == 0) {
                            textCell.setTextAndValueAndColorfulIcon(getString(R.string.InviteFriends), "", false, R.drawable.settings_invite, 0xFF1CA5ED, 0xFF1488E1, false);
                        } else if (position == 1) {
                            textCell.setTextAndValueAndColorfulIcon(getString(R.string.RecentCalls), "", false, R.drawable.settings_calls, 0xFF55CA47, 0xFF27B434, false);
                        }
                    } else if (isAdmin) {
                        if (isChannel) {
                            textCell.setTextAndIcon(getString(R.string.ChannelInviteViaLink), R.drawable.msg_link2, false);
                        } else {
                            textCell.setTextAndIcon(getString(R.string.InviteToGroupByLink), R.drawable.msg_link2, false);
                        }
                    } else {
                        if (position == 0) {
                            textCell.setTextAndValueAndColorfulIcon(getString(R.string.NewGroup), "", false, R.drawable.settings_group, 0xFF1CA5ED, 0xFF1488E1, false);
                        } else if (position == 1) {
                            textCell.setTextAndValueAndColorfulIcon(getString(R.string.NewChannel), "", false, R.drawable.settings_channel, 0xFF55CA47, 0xFF27B434, false);
                        }
                    }
                } else {
                    ContactsController.Contact contact = ContactsController.getInstance(currentAccount).phoneBookContacts.get(position);
                    if (contact.first_name != null && contact.last_name != null) {
                        textCell.setText(contact.first_name + " " + contact.last_name, false);
                    } else if (contact.first_name != null && contact.last_name == null) {
                        textCell.setText(contact.first_name, false);
                    } else {
                        textCell.setText(contact.last_name, false);
                    }
                }
                break;
            case HEADER_CELL:
                HeaderCell cell = (HeaderCell) holder.itemView;
                if (isEmptyWithMainTabs && position == 1 && section == 1) {
                    cell.setText(getString(R.string.InviteFriends));
                } else if (sortType == SORT_TYPE_BY_NAME) {
                    cell.setText(getString(R.string.SortedByName));
                } else {
                    cell.setText(getString(R.string.SortedByLastSeen));
                }
                break;
            case GRAY_CELL:
                GraySectionCell sectionCell = (GraySectionCell) holder.itemView;
                if (sortType == SORT_TYPE_NONE) {
                    sectionCell.setText(getString(R.string.Contacts));
                } else if (sortType == SORT_TYPE_BY_NAME) {
                    sectionCell.setText(getString(R.string.SortedByName));
                } else {
                    sectionCell.setText(getString(R.string.SortedByLastSeen));
                }
                break;
        }
    }

    @Override
    public int getItemViewType(int section, int position) {
        if (section == 0 && includeSearch) {
            if (position == 0) return SEARCH_CELL;
            position--;
        }
        if (isEmptyWithMainTabs) {
            if (section == 0) {
                return EMPTY_CELL;
            }
            if (section == 1) {
                if (position == 0) {
                    return SHADOW_CELL;
                } else if (position == 1) {
                    return HEADER_CELL;
                }
            }
            return INVITE_CELL;
        }


        HashMap<String, ArrayList<TLRPC.TL_contact>> usersSectionsDict = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).usersMutualSectionsDict : ContactsController.getInstance(currentAccount).usersSectionsDict;
        ArrayList<String> sortedUsersSectionsArray = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray : ContactsController.getInstance(currentAccount).sortedUsersSectionsArray;
        if (onlyUsers != 0 && !isAdmin) {
            if (isEmpty) {
                return EMPTY_CELL;
            }
            ArrayList<TLRPC.TL_contact> arr = usersSectionsDict.get(sortedUsersSectionsArray.get(section));
            return position < arr.size() ? USER_CELL : DIVIDER_CELL;
        } else {
            if (section == 0) {
                if (isAdmin) {
                    if (position == 1) {
                        return SHADOW_CELL;
                    }
                    if (position == 2) {
                        return (sortType == SORT_TYPE_BY_NAME || sortType == SORT_TYPE_BY_TIME ? HEADER_CELL : GRAY_CELL);
                    }
                } else if (needPhonebook) {
                    if (position < 2) {
                        return TEXT_CELL;
                    }
                    if (position == 2) {
                        return SHADOW_CELL;
                    }
                    if (position == 3) {
                        return isEmpty ? SHADOW_CELL : (sortType == SORT_TYPE_BY_NAME || sortType == SORT_TYPE_BY_TIME ? HEADER_CELL : GRAY_CELL);
                    }
                } else {
                    if (position == 2) {
                        return SHADOW_CELL;
                    }
                    if (position == 3) {
                        return isEmpty ? SHADOW_CELL : (sortType == SORT_TYPE_BY_NAME || sortType == SORT_TYPE_BY_TIME ? HEADER_CELL : GRAY_CELL);
                    }
                }
            } else {
                if (isEmpty) {
                    return EMPTY_CELL;
                }
                if (sortType == SORT_TYPE_BY_TIME) {
                    if (section == 1) {
                        return position < onlineContacts.size() ? USER_CELL : DIVIDER_CELL;
                    }
                } else {
                    if (section - 1 < sortedUsersSectionsArray.size()) {
                        ArrayList<TLRPC.TL_contact> arr = usersSectionsDict.get(sortedUsersSectionsArray.get(section - 1));
                        return position < arr.size() ? USER_CELL : DIVIDER_CELL;
                    }
                }
            }
        }
        return TEXT_CELL;
    }

    @Override
    public String getLetter(int position) {
        if (includeSearch) {
            if (position == 0) return null;
            position--;
        }
        if (sortType == SORT_TYPE_BY_TIME || isEmpty) {
            return null;
        }
        ArrayList<String> sortedUsersSectionsArray = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray : ContactsController.getInstance(currentAccount).sortedUsersSectionsArray;
        int section = getSectionForPosition(position);
        if (section == -1) {
            section = sortedUsersSectionsArray.size() - 1;
        }
        if (onlyUsers != 0 && !isAdmin) {
            if (section >= 0 && section < sortedUsersSectionsArray.size()) {
                return sortedUsersSectionsArray.get(section);
            }
        } else {
            if (section > 0 && section <= sortedUsersSectionsArray.size()) {
                return sortedUsersSectionsArray.get(section - 1);
            }
        }
        return null;
    }

    @Override
    public void getPositionForScrollProgress(RecyclerListView listView, float progress, int[] position) {
        position[0] = (int) (getItemCount() * progress);
        position[1] = 0;
    }
}
