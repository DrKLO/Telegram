/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.LetterSectionCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.ContactsEmptyView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Stories.DialogStoriesCell;
import org.telegram.ui.Stories.StoriesController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;

public class ContactsAdapter extends RecyclerListView.SectionsAdapter {

    private int currentAccount = UserConfig.selectedAccount;
    private Context mContext;
    private int onlyUsers;
    private boolean needPhonebook;
    private LongSparseArray<TLRPC.User> ignoreUsers;
    private LongSparseArray<TLRPC.User> selectedContacts;
    private ArrayList<TLRPC.TL_contact> onlineContacts;
    private boolean scrolling;
    private boolean isAdmin;
    private int sortType;
    private boolean isChannel;
    private boolean disableSections;
    public boolean isEmpty;
    public boolean hasStories;
    public ArrayList<TL_stories.PeerStories> userStories = new ArrayList<>();

    DialogStoriesCell dialogStoriesCell;
    BaseFragment fragment;

    public ContactsAdapter(Context context, BaseFragment fragment, int onlyUsersType, boolean showPhoneBook, LongSparseArray<TLRPC.User> usersToIgnore, LongSparseArray<TLRPC.User> selectedContacts, int flags, boolean gps) {
        mContext = context;
        onlyUsers = onlyUsersType;
        needPhonebook = showPhoneBook;
        ignoreUsers = usersToIgnore;
        this.selectedContacts = selectedContacts;
        isAdmin = flags != 0;
        isChannel = flags == 2;
        this.fragment = fragment;
    }

    public void setStories(ArrayList<TL_stories.PeerStories> stories, boolean animated) {
//        boolean hasStories = !stories.isEmpty();
//        userStories.clear();
//        userStories.addAll(stories);
//        if (this.hasStories != hasStories) {
//            this.hasStories = hasStories;
//        }
//        update(true);
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

    public void setIsScrolling(boolean value) {
        scrolling = value;
    }

    public Object getItem(int section, int position) {
        if (getItemViewType(section, position) == 2) {
            if (hasStories) {
                return "Stories";
            } else {
                return "Header";
            }
        }
        if (hasStories && section == 1) {
            if (position == userStories.size()) {
                return "Header";
            } else {
                return DialogObject.getPeerDialogId(userStories.get(position).peer);
            }
        } else if (hasStories && section > 1) {
            section--;
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
        if (hasStories && section == 1) {
            if (position == userStories.size()) {
                return Objects.hash(sectionIndex * -49612, getItem(section, position));
            }
            return Objects.hash(section * -54323, getItem(section, position));
        } else if (hasStories && section > 1) {
            sectionIndex--;
        }
        Object item = getItem(section, position);
        return Objects.hash(sectionIndex * -49612, item);
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder, int section, int row) {
        if (hasStories && section == 1) {
            if (row == userStories.size()) {
                return false;
            } else {
                return true;
            }
        } else if (hasStories && section > 1) {
            section--;
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
                    return row != 1;
                } else if (needPhonebook) {
                    return row != 1;
                } else {
                    return row != 3;
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
            ArrayList<String> sortedUsersSectionsArray = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray : ContactsController.getInstance(currentAccount).sortedUsersSectionsArray;
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
        if (hasStories) {
            count++;
        }
        return count;
    }

    @Override
    public int getCountForSection(int section) {
        return getCountForSectionInternal(section);
    }

    private int getCountForSectionInternal(int section) {
        HashMap<String, ArrayList<TLRPC.TL_contact>> usersSectionsDict = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).usersMutualSectionsDict : ContactsController.getInstance(currentAccount).usersSectionsDict;
        ArrayList<String> sortedUsersSectionsArray = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray : ContactsController.getInstance(currentAccount).sortedUsersSectionsArray;

        if (hasStories && section == 1) {
            return userStories.size() + 1;
        } else if (hasStories && section > 1) {
            section--;
        }
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
                if (isAdmin) {
                    return 2;
                } else if (needPhonebook) {
                    return 2;
                } else {
                    return 4;
                }
            } else {
                if (isEmpty) {
                    return 1;
                }
                if (sortType == SORT_TYPE_BY_TIME) {
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
        if (hasStories && section == 1) {
            cell.setLetter("");
            return cell;
        } else if (hasStories && section > 1) {
            section--;
        }
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
                view = new DividerCell(mContext);
                view.setPadding(AndroidUtilities.dp(LocaleController.isRTL ? 28 : 72), AndroidUtilities.dp(8), AndroidUtilities.dp(LocaleController.isRTL ? 72 : 28), AndroidUtilities.dp(8));
                break;
            case 4:
                FrameLayout frameLayout = new FrameLayout(mContext) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        int height;
                        height = MeasureSpec.getSize(heightMeasureSpec);
                        if (height == 0) {
                            height = parent.getMeasuredHeight();
                        }
                        if (height == 0) {
                            height = AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight() - (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
                        }
                        int cellHeight = AndroidUtilities.dp(50);
                        int totalHeight = onlyUsers != 0 ? 0 : cellHeight + AndroidUtilities.dp(30);
                        if (!isAdmin && !needPhonebook) {
                            totalHeight += cellHeight;
                        }
                        if (totalHeight < height) {
                            height = height - totalHeight;
                        } else {
                            height = 0;
                        }
                        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                    }
                };
                ContactsEmptyView emptyView = new ContactsEmptyView(mContext);
                frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
                view = frameLayout;
                break;
            case 6:
                if (dialogStoriesCell == null) {
                    dialogStoriesCell = new DialogStoriesCell(mContext, fragment, currentAccount, DialogStoriesCell.TYPE_ARCHIVE) {
                        @Override
                        public void onUserLongPressed(View view, long dialogId) {
                            onStoryLongPressed(view, dialogId);
                        }
                    };
                    dialogStoriesCell.setProgressToCollapse(0, false);
                } else {
                    AndroidUtilities.removeFromParent(dialogStoriesCell);
                }
                FrameLayout storiesContainer = new FrameLayout(mContext);
                storiesContainer.addView(dialogStoriesCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0, 0));
                view = storiesContainer;
                break;
            case 5:
            default:
                view = new ShadowSectionCell(mContext);
                Drawable drawable = Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow);
                CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), drawable);
                combinedDrawable.setFullsize(true);
                view.setBackgroundDrawable(combinedDrawable);
                break;

        }
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(int section, int position, RecyclerView.ViewHolder holder) {
        if (hasStories && section == 1) {
            switch (holder.getItemViewType()) {
                case 0:
                    UserCell userCell = (UserCell) holder.itemView;
                    userCell.setAvatarPadding(6);
                    userCell.storyParams.drawSegments = true;
                    StoriesController storiesController = MessagesController.getInstance(currentAccount).getStoriesController();
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(DialogObject.getPeerDialogId(userStories.get(position).peer));
                    if (storiesController.hasUnreadStories(user.id)) {
                        int newStories = storiesController.getUnreadStoriesCount(user.id);
                        userCell.setData(user, ContactsController.formatName(user), LocaleController.formatPluralString("NewStories", newStories, newStories).toLowerCase(), 0);
                    } else {
                        int storiesCount = userStories.get(position).stories.size();
                        userCell.setData(user, ContactsController.formatName(user), LocaleController.formatPluralString("Stories", storiesCount, storiesCount).toLowerCase(), 0);
                    }

                    break;
                case 2:
                    GraySectionCell sectionCell = (GraySectionCell) holder.itemView;
                    if (sortType == SORT_TYPE_NONE) {
                        sectionCell.setText(LocaleController.getString(R.string.Contacts));
                    } else if (sortType == SORT_TYPE_BY_NAME) {
                        sectionCell.setText(LocaleController.getString(R.string.SortedByName));
                    } else {
                        sectionCell.setText(LocaleController.getString(R.string.SortedByLastSeen));
                    }
                    break;
            }
            return;
        } else if (hasStories && section > 1) {
            section--;
        }
        switch (holder.getItemViewType()) {
            case 0:
                UserCell userCell = (UserCell) holder.itemView;
                userCell.storyParams.drawSegments = false;
                userCell.setAvatarPadding(sortType == SORT_TYPE_BY_TIME || disableSections ? 6 : 58);
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
            case 1:
                TextCell textCell = (TextCell) holder.itemView;
                if (section == 0) {
                    if (needPhonebook) {
                        if (position == 0) {
                            textCell.setTextAndIcon(LocaleController.getString(R.string.InviteFriends), R.drawable.msg_invite, false);
                        }
                    } else if (isAdmin) {
                        if (isChannel) {
                            textCell.setTextAndIcon(LocaleController.getString(R.string.ChannelInviteViaLink), R.drawable.msg_link2, false);
                        } else {
                            textCell.setTextAndIcon(LocaleController.getString(R.string.InviteToGroupByLink), R.drawable.msg_link2, false);
                        }
                    } else {
                        if (position == 0) {
                            textCell.setTextAndIcon(LocaleController.getString(R.string.NewGroup), R.drawable.msg_groups, false);
                        } else if (position == 1) {
                            textCell.setTextAndIcon(LocaleController.getString(R.string.NewContact), R.drawable.msg_addcontact, false);
                        } else if (position == 2) {
                            textCell.setTextAndIcon(LocaleController.getString(R.string.NewChannel), R.drawable.msg_channel, false);
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
                if (hasStories) {
                    sectionCell.setText(LocaleController.getString(R.string.HiddenStories));
                } else if (sortType == SORT_TYPE_NONE) {
                    sectionCell.setText(LocaleController.getString(R.string.Contacts));
                } else if (sortType == SORT_TYPE_BY_NAME) {
                    sectionCell.setText(LocaleController.getString(R.string.SortedByName));
                } else {
                    sectionCell.setText(LocaleController.getString(R.string.SortedByLastSeen));
                }
                break;
        }
    }

    @Override
    public int getItemViewType(int section, int position) {
        HashMap<String, ArrayList<TLRPC.TL_contact>> usersSectionsDict = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).usersMutualSectionsDict : ContactsController.getInstance(currentAccount).usersSectionsDict;
        ArrayList<String> sortedUsersSectionsArray = onlyUsers == 2 ? ContactsController.getInstance(currentAccount).sortedUsersMutualSectionsArray : ContactsController.getInstance(currentAccount).sortedUsersSectionsArray;
        if (hasStories && section == 1) {
            if (position == userStories.size()) {
                return 2;
            } else {
                return 0;
            }
        } else if (hasStories && section > 1) {
            section--;
        }
        if (onlyUsers != 0 && !isAdmin) {
            if (isEmpty) {
                return 4;
            }
            ArrayList<TLRPC.TL_contact> arr = usersSectionsDict.get(sortedUsersSectionsArray.get(section));
            return position < arr.size() ? 0 : 3;
        } else {
            if (section == 0) {
                if (isAdmin) {
                    if (position == 1) {
                        return 2;
                    }
                } else if (needPhonebook) {
                    if (position == 1) {
                        return isEmpty ? 5 : 2;
                    }
                } else if (position == 3) {
                    return isEmpty ? 5 : 2;
                }
            } else {
                if (isEmpty) {
                    return 4;
                }
                if (sortType == SORT_TYPE_BY_TIME) {
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

    public void onStoryLongPressed(View view, long dialogId) {

    }

    public void removeStory(long dialogId) {
        for (int i = 0; i < userStories.size(); i++) {
            if (DialogObject.getPeerDialogId(userStories.get(i).peer) == dialogId) {
                userStories.remove(i);

                if (userStories.isEmpty()) {
                    notifyItemRangeRemoved(getCountForSection(0) + i - 1, 2);
                    hasStories = false;
                    updateHashes();
                } else {
                    notifyItemRemoved(getCountForSection(0) + i);
                }
                break;
            }
        }
    }
}
