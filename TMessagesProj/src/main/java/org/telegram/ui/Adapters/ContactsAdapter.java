/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.LetterSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import androidx.recyclerview.widget.RecyclerView;

public class ContactsAdapter extends RecyclerListView.SectionsAdapter {

    private int currentAccount = UserConfig.selectedAccount;
    private Context mContext;
    private int onlyUsers;
    private boolean needPhonebook;
    private SparseArray<TLRPC.User> ignoreUsers;
    private SparseArray<?> checkedMap;
    private ArrayList<TLRPC.TL_contact> onlineContacts;
    private boolean scrolling;
    private boolean isAdmin;
    private int sortType;
    private boolean isChannel;
    private boolean disableSections;
    private boolean hasGps;

    public ContactsAdapter(Context context, int onlyUsersType, boolean arg2, SparseArray<TLRPC.User> arg3, int arg4, boolean gps) {
        mContext = context;
        onlyUsers = onlyUsersType;
        needPhonebook = arg2;
        ignoreUsers = arg3;
        isAdmin = arg4 != 0;
        isChannel = arg4 == 2;
        hasGps = gps;
    }

    public void setDisableSections(boolean value) {
        disableSections = value;
    }

    public void setSortType(int value) {
        sortType = value;
        if (sortType == 2) {
            if (onlineContacts == null) {
                onlineContacts = new ArrayList<>(ContactsController.getInstance(currentAccount).contacts);
                int selfId = UserConfig.getInstance(currentAccount).clientUserId;
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

    public void setCheckedMap(SparseArray<?> map) {
        checkedMap = map;
    }

    public void setIsScrolling(boolean value) {
        scrolling = value;
    }

    public Object getItem(int section, int position) {
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
                if (sortType == 2) {
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
        if (needPhonebook) {
            return ContactsController.getInstance(currentAccount).phoneBookContacts.get(position);
        }
        return null;
    }

    @Override
    public boolean isEnabled(int section, int row) {
        HashMap<String, ArrayList<TLRPC.TL_contact>> usersSectionsDict = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).usersMutualSectionsDict : ContactsController.getInstance(currentAccount).usersSectionsDict;
        ArrayList<String> sortedUsersSectionsArray = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray : ContactsController.getInstance(currentAccount).sortedUsersSectionsArray;

        if (onlyUsers != 0 && !isAdmin) {
            ArrayList<TLRPC.TL_contact> arr = usersSectionsDict.get(sortedUsersSectionsArray.get(section));
            return row < arr.size();
        } else {
            if (section == 0) {
                if (isAdmin) {
                    return row != 1;
                } else if (needPhonebook) {
                    return hasGps && row != 2 || !hasGps && row != 1;
                } else {
                    return row != 3;
                }
            } else {
                if (sortType == 2) {
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
        if (sortType == 2) {
            count = 1;
        } else {
            ArrayList<String> sortedUsersSectionsArray = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray : ContactsController.getInstance(currentAccount).sortedUsersSectionsArray;
            count = sortedUsersSectionsArray.size();
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
        return count;
    }

    @Override
    public int getCountForSection(int section) {
        HashMap<String, ArrayList<TLRPC.TL_contact>> usersSectionsDict = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).usersMutualSectionsDict : ContactsController.getInstance(currentAccount).usersSectionsDict;
        ArrayList<String> sortedUsersSectionsArray = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray : ContactsController.getInstance(currentAccount).sortedUsersSectionsArray;

        if (onlyUsers != 0 && !isAdmin) {
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
                if (isAdmin) {
                    return 2;
                } else if (needPhonebook) {
                    return hasGps ? 3 : 2;
                } else {
                    return 4;
                }
            } else {
                if (sortType == 2) {
                    if (section == 1) {
                        return onlineContacts.isEmpty() ? 0 : onlineContacts.size() + 1;
                    }
                } else {
                    if (section - 1 < sortedUsersSectionsArray.size()) {
                        ArrayList<TLRPC.TL_contact> arr = usersSectionsDict.get(sortedUsersSectionsArray.get(section - 1));
                        int count = arr.size();
                        if (section - 1 != (sortedUsersSectionsArray.size() - 1) || needPhonebook) {
                            count++;
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
        if (sortType == 2 || disableSections) {
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

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        switch (viewType) {
            case 0:
                view = new UserCell(mContext, 58, 1, false);
                break;
            case 1:
                view = new TextCell(mContext);
                break;
            case 2:
                view = new GraySectionCell(mContext);
                break;
            case 3:
            default:
                view = new DividerCell(mContext);
                view.setPadding(AndroidUtilities.dp(LocaleController.isRTL ? 28 : 72), AndroidUtilities.dp(8), AndroidUtilities.dp(LocaleController.isRTL ? 72 : 28), AndroidUtilities.dp(8));
                break;
        }
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(int section, int position, RecyclerView.ViewHolder holder) {
        switch (holder.getItemViewType()) {
            case 0:
                UserCell userCell = (UserCell) holder.itemView;
                userCell.setAvatarPadding(sortType == 2 || disableSections ? 6 : 58);
                ArrayList<TLRPC.TL_contact> arr;
                if (sortType == 2) {
                    arr = onlineContacts;
                } else {
                    HashMap<String, ArrayList<TLRPC.TL_contact>> usersSectionsDict = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).usersMutualSectionsDict : ContactsController.getInstance(currentAccount).usersSectionsDict;
                    ArrayList<String> sortedUsersSectionsArray = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray : ContactsController.getInstance(currentAccount).sortedUsersSectionsArray;
                    arr = usersSectionsDict.get(sortedUsersSectionsArray.get(section - (onlyUsers != 0 && !isAdmin ? 0 : 1)));
                }
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(arr.get(position).user_id);
                userCell.setData(user, null, null, 0);
                if (checkedMap != null) {
                    userCell.setChecked(checkedMap.indexOfKey(user.id) >= 0, !scrolling);
                }
                if (ignoreUsers != null) {
                    if (ignoreUsers.indexOfKey(user.id) >= 0) {
                        userCell.setAlpha(0.5f);
                    } else {
                        userCell.setAlpha(1.0f);
                    }
                }
                break;
            case 1:
                TextCell textCell = (TextCell) holder.itemView;
                if (section == 0) {
                    if (needPhonebook) {
                        if (position == 0) {
                            textCell.setTextAndIcon(LocaleController.getString("InviteFriends", R.string.InviteFriends), R.drawable.menu_invite, false);
                        } else if (position == 1) {
                            textCell.setTextAndIcon(LocaleController.getString("AddPeopleNearby", R.string.AddPeopleNearby), R.drawable.menu_location, false);
                        }
                    } else if (isAdmin) {
                        if (isChannel) {
                            textCell.setTextAndIcon(LocaleController.getString("ChannelInviteViaLink", R.string.ChannelInviteViaLink), R.drawable.profile_link, false);
                        } else {
                            textCell.setTextAndIcon(LocaleController.getString("InviteToGroupByLink", R.string.InviteToGroupByLink), R.drawable.profile_link, false);
                        }
                    } else {
                        if (position == 0) {
                            textCell.setTextAndIcon(LocaleController.getString("NewGroup", R.string.NewGroup), R.drawable.menu_groups, false);
                        } else if (position == 1) {
                            textCell.setTextAndIcon(LocaleController.getString("NewSecretChat", R.string.NewSecretChat), R.drawable.menu_secret, false);
                        } else if (position == 2) {
                            textCell.setTextAndIcon(LocaleController.getString("NewChannel", R.string.NewChannel), R.drawable.menu_broadcast, false);
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
            case 2:
                GraySectionCell sectionCell = (GraySectionCell) holder.itemView;
                if (sortType == 0) {
                    sectionCell.setText(LocaleController.getString("Contacts", R.string.Contacts));
                } else if (sortType == 1) {
                    sectionCell.setText(LocaleController.getString("SortedByName", R.string.SortedByName));
                } else {
                    sectionCell.setText(LocaleController.getString("SortedByLastSeen", R.string.SortedByLastSeen));
                }
                break;
        }
    }

    @Override
    public int getItemViewType(int section, int position) {
        HashMap<String, ArrayList<TLRPC.TL_contact>> usersSectionsDict = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).usersMutualSectionsDict : ContactsController.getInstance(currentAccount).usersSectionsDict;
        ArrayList<String> sortedUsersSectionsArray = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray : ContactsController.getInstance(currentAccount).sortedUsersSectionsArray;
        if (onlyUsers != 0 && !isAdmin) {
            ArrayList<TLRPC.TL_contact> arr = usersSectionsDict.get(sortedUsersSectionsArray.get(section));
            return position < arr.size() ? 0 : 3;
        } else {
            if (section == 0) {
                if (isAdmin) {
                    if (position == 1) {
                        return 2;
                    }
                } else if (needPhonebook) {
                    if (hasGps && position == 2 || !hasGps && position == 1) {
                        return 2;
                    }
                } else if (position == 3) {
                    return 2;
                }
            } else {
                if (sortType == 2) {
                    if (section == 1) {
                        return position < onlineContacts.size() ? 0 : 3;
                    }
                } else {
                    if (section - 1 < sortedUsersSectionsArray.size()) {
                        ArrayList<TLRPC.TL_contact> arr = usersSectionsDict.get(sortedUsersSectionsArray.get(section - 1));
                        return position < arr.size() ? 0 : 3;
                    }
                }
            }
        }
        return 1;
    }

    @Override
    public String getLetter(int position) {
        if (sortType == 2) {
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
    public int getPositionForScrollProgress(float progress) {
        return (int) (getItemCount() * progress);
    }
}
