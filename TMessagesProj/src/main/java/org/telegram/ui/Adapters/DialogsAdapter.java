/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ArchiveHintCell;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.DialogMeUrlCell;
import org.telegram.ui.Cells.DialogsEmptyCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;
import java.util.Collections;

import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

public class DialogsAdapter extends RecyclerListView.SelectionAdapter {

    private Context mContext;
    private ArchiveHintCell archiveHintCell;
    private ArrayList<TLRPC.TL_contact> onlineContacts;
    private int prevContactsCount;
    private int dialogsType;
    private int folderId;
    private long openedDialogId;
    private int currentCount;
    private boolean isOnlySelect;
    private ArrayList<Long> selectedDialogs;
    private boolean hasHints;
    private int currentAccount = UserConfig.selectedAccount;
    private boolean dialogsListFrozen;
    private boolean showArchiveHint;
    private boolean isReordering;
    private long lastSortTime;

    public DialogsAdapter(Context context, int type, int folder, boolean onlySelect) {
        mContext = context;
        dialogsType = type;
        folderId = folder;
        isOnlySelect = onlySelect;
        hasHints = folder == 0 && type == 0 && !onlySelect;
        selectedDialogs = new ArrayList<>();
        if (folderId == 1) {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            showArchiveHint = preferences.getBoolean("archivehint", true);
            preferences.edit().putBoolean("archivehint", false).commit();
            if (showArchiveHint) {
                archiveHintCell = new ArchiveHintCell(context);
            }
        }
    }

    public void setOpenedDialogId(long id) {
        openedDialogId = id;
    }

    public boolean hasSelectedDialogs() {
        return selectedDialogs != null && !selectedDialogs.isEmpty();
    }

    public boolean addOrRemoveSelectedDialog(long did, View cell) {
        if (selectedDialogs.contains(did)) {
            selectedDialogs.remove(did);
            if (cell instanceof DialogCell) {
                ((DialogCell) cell).setChecked(false, true);
            }
            return false;
        } else {
            selectedDialogs.add(did);
            if (cell instanceof DialogCell) {
                ((DialogCell) cell).setChecked(true, true);
            }
            return true;
        }
    }

    public ArrayList<Long> getSelectedDialogs() {
        return selectedDialogs;
    }

    public void onReorderStateChanged(boolean reordering) {
        isReordering = reordering;
    }

    public int fixPosition(int position) {
        if (hasHints) {
            position -= 2 + MessagesController.getInstance(currentAccount).hintDialogs.size();
        }
        if (showArchiveHint) {
            position -= 2;
        }
        return position;
    }

    public boolean isDataSetChanged() {
        int current = currentCount;
        return current != getItemCount() || current == 1;
    }

    @Override
    public int getItemCount() {
        ArrayList<TLRPC.Dialog> array = DialogsActivity.getDialogsArray(currentAccount, dialogsType, folderId, dialogsListFrozen);
        int dialogsCount = array.size();
        if (dialogsCount == 0 && (folderId != 0 || MessagesController.getInstance(currentAccount).isLoadingDialogs(folderId))) {
            onlineContacts = null;
            if (folderId == 1 && showArchiveHint) {
                return (currentCount = 2);
            }
            return (currentCount = 0);
        }
        int count = dialogsCount;
        if (!MessagesController.getInstance(currentAccount).isDialogsEndReached(folderId) || dialogsCount == 0) {
            count++;
        }
        boolean hasContacts = false;
        if (hasHints) {
            count += 2 + MessagesController.getInstance(currentAccount).hintDialogs.size();
        } else if (dialogsType == 0 && dialogsCount == 0 && folderId == 0) {
            if (ContactsController.getInstance(currentAccount).contacts.isEmpty() && ContactsController.getInstance(currentAccount).isLoadingContacts()) {
                onlineContacts = null;
                return (currentCount = 0);
            }

            if (!ContactsController.getInstance(currentAccount).contacts.isEmpty()) {
                if (onlineContacts == null || prevContactsCount != ContactsController.getInstance(currentAccount).contacts.size()) {
                    onlineContacts = new ArrayList<>(ContactsController.getInstance(currentAccount).contacts);
                    prevContactsCount = onlineContacts.size();
                    int selfId = UserConfig.getInstance(currentAccount).clientUserId;
                    for (int a = 0, N = onlineContacts.size(); a < N; a++) {
                        if (onlineContacts.get(a).user_id == selfId) {
                            onlineContacts.remove(a);
                            break;
                        }
                    }
                    sortOnlineContacts(false);
                }
                count += onlineContacts.size() + 2;
                hasContacts = true;
            }
        }
        if (!hasContacts && onlineContacts != null) {
            onlineContacts = null;
        }
        if (folderId == 1 && showArchiveHint) {
            count += 2;
        }
        if (folderId == 0 && dialogsCount != 0) {
            count++;
        }
        currentCount = count;
        return count;
    }

    public TLObject getItem(int i) {
        if (onlineContacts != null) {
            i -= 3;
            if (i < 0 || i >= onlineContacts.size()) {
                return null;
            }
            return MessagesController.getInstance(currentAccount).getUser(onlineContacts.get(i).user_id);
        }
        if (showArchiveHint) {
            i -= 2;
        }
        ArrayList<TLRPC.Dialog> arrayList = DialogsActivity.getDialogsArray(currentAccount, dialogsType, folderId, dialogsListFrozen);
        if (hasHints) {
            int count = MessagesController.getInstance(currentAccount).hintDialogs.size();
            if (i < 2 + count) {
                return MessagesController.getInstance(currentAccount).hintDialogs.get(i - 1);
            } else {
                i -= count + 2;
            }
        }
        if (i < 0 || i >= arrayList.size()) {
            return null;
        }
        return arrayList.get(i);
    }

    public void sortOnlineContacts(boolean notify) {
        if (onlineContacts == null || notify && (SystemClock.uptimeMillis() - lastSortTime) < 2000) {
            return;
        }
        lastSortTime = SystemClock.uptimeMillis();
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
            if (notify) {
                notifyDataSetChanged();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void setDialogsListFrozen(boolean frozen) {
        dialogsListFrozen = frozen;
    }

    public ViewPager getArchiveHintCellPager() {
        return archiveHintCell != null ? archiveHintCell.getViewPager() : null;
    }

    @Override
    public void notifyDataSetChanged() {
        hasHints = folderId == 0 && dialogsType == 0 && !isOnlySelect && !MessagesController.getInstance(currentAccount).hintDialogs.isEmpty();
        super.notifyDataSetChanged();
    }

    @Override
    public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
        if (holder.itemView instanceof DialogCell) {
            DialogCell dialogCell = (DialogCell) holder.itemView;
            dialogCell.onReorderStateChanged(isReordering, false);
            int position = fixPosition(holder.getAdapterPosition());
            dialogCell.setDialogIndex(position);
            dialogCell.checkCurrentDialogIndex(dialogsListFrozen);
            dialogCell.setChecked(selectedDialogs.contains(dialogCell.getDialogId()), false);
        }
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        int viewType = holder.getItemViewType();
        return viewType != 1 && viewType != 5 && viewType != 3 && viewType != 8 && viewType != 7 && viewType != 9 && viewType != 10;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View view;
        switch (viewType) {
            case 0:
                view = new DialogCell(mContext, true, false);
                break;
            case 1:
                view = new LoadingCell(mContext);
                break;
            case 2: {
                HeaderCell headerCell = new HeaderCell(mContext);
                headerCell.setText(LocaleController.getString("RecentlyViewed", R.string.RecentlyViewed));

                TextView textView = new TextView(mContext);
                textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
                textView.setText(LocaleController.getString("RecentlyViewedHide", R.string.RecentlyViewedHide));
                textView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL);
                headerCell.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, 17, 15, 17, 0));
                textView.setOnClickListener(view1 -> {
                    MessagesController.getInstance(currentAccount).hintDialogs.clear();
                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    preferences.edit().remove("installReferer").commit();
                    notifyDataSetChanged();
                });

                view = headerCell;
                break;
            }
            case 3:
                FrameLayout frameLayout = new FrameLayout(mContext) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(12), MeasureSpec.EXACTLY));
                    }
                };
                frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
                View v = new View(mContext);
                v.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                frameLayout.addView(v, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                view = frameLayout;
                break;
            case 4:
                view = new DialogMeUrlCell(mContext);
                break;
            case 5:
                view = new DialogsEmptyCell(mContext);
                break;
            case 6:
                view = new UserCell(mContext, 8, 0, false);
                break;
            case 7:
                HeaderCell headerCell = new HeaderCell(mContext);
                headerCell.setText(LocaleController.getString("YourContacts", R.string.YourContacts));
                view = headerCell;
                break;
            case 8:
                view = new ShadowSectionCell(mContext);
                Drawable drawable = Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow);
                CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), drawable);
                combinedDrawable.setFullsize(true);
                view.setBackgroundDrawable(combinedDrawable);
                break;
            case 9:
                view = archiveHintCell;
                if (archiveHintCell.getParent() != null) {
                    ViewGroup parent = (ViewGroup) archiveHintCell.getParent();
                    parent.removeView(archiveHintCell);
                }
                break;
            case 10:
            default: {
                view = new View(mContext) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        int size = DialogsActivity.getDialogsArray(currentAccount, dialogsType, folderId, dialogsListFrozen).size();
                        boolean hasArchive = MessagesController.getInstance(currentAccount).dialogs_dict.get(DialogObject.makeFolderDialogId(1)) != null;
                        int height;
                        if (size == 0 || !hasArchive) {
                            height = 0;
                        } else {
                            height = MeasureSpec.getSize(heightMeasureSpec);
                            if (height == 0) {
                                View parent = (View) getParent();
                                if (parent != null) {
                                    height = parent.getMeasuredHeight();
                                }
                            }
                            if (height == 0) {
                                height = AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight() - (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
                            }
                            int cellHeight = AndroidUtilities.dp(SharedConfig.useThreeLinesLayout ? 78 : 72);
                            int dialogsHeight = size * cellHeight + (size - 1);
                            if (dialogsHeight < height) {
                                height = height - dialogsHeight + cellHeight + 1;
                            } else if (dialogsHeight - height < cellHeight + 1) {
                                height = cellHeight + 1 - (dialogsHeight - height);
                            } else {
                                height = 0;
                            }
                        }
                        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height);
                    }
                };
            }
        }
        view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, viewType == 5 ? RecyclerView.LayoutParams.MATCH_PARENT : RecyclerView.LayoutParams.WRAP_CONTENT));
        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int i) {
        switch (holder.getItemViewType()) {
            case 0: {
                DialogCell cell = (DialogCell) holder.itemView;
                TLRPC.Dialog dialog = (TLRPC.Dialog) getItem(i);
                TLRPC.Dialog nextDialog = (TLRPC.Dialog) getItem(i + 1);
                if (folderId == 0) {
                    cell.useSeparator = (i != getItemCount() - 2);
                } else {
                    cell.useSeparator = (i != getItemCount() - 1);
                }
                cell.fullSeparator = dialog.pinned && nextDialog != null && !nextDialog.pinned;
                if (dialogsType == 0) {
                    if (AndroidUtilities.isTablet()) {
                        cell.setDialogSelected(dialog.id == openedDialogId);
                    }
                }
                cell.setChecked(selectedDialogs.contains(dialog.id), false);
                cell.setDialog(dialog, dialogsType, folderId);
                break;
            }
            case 5: {
                DialogsEmptyCell cell = (DialogsEmptyCell) holder.itemView;
                cell.setType(onlineContacts != null ? 1 : 0);
                break;
            }
            case 4: {
                DialogMeUrlCell cell = (DialogMeUrlCell) holder.itemView;
                cell.setRecentMeUrl((TLRPC.RecentMeUrl) getItem(i));
                break;
            }
            case 6: {
                UserCell cell = (UserCell) holder.itemView;
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(onlineContacts.get(i - 3).user_id);
                cell.setData(user, null, null, 0);
                break;
            }
        }
    }

    @Override
    public int getItemViewType(int i) {
        if (onlineContacts != null) {
            if (i == 0) {
                return 5;
            } else if (i == 1) {
                return 8;
            } else if (i == 2) {
                return 7;
            } else {
                return 6;
            }
        } else if (hasHints) {
            int count = MessagesController.getInstance(currentAccount).hintDialogs.size();
            if (i < 2 + count) {
                if (i == 0) {
                    return 2;
                } else if (i == 1 + count) {
                    return 3;
                }
                return 4;
            } else {
                i -= 2 + count;
            }
        } else if (showArchiveHint) {
            if (i == 0) {
                return 9;
            } else if (i == 1) {
                return 8;
            } else {
                i -= 2;
            }
        }
        int size = DialogsActivity.getDialogsArray(currentAccount, dialogsType, folderId, dialogsListFrozen).size();
        if (i == size) {
            if (!MessagesController.getInstance(currentAccount).isDialogsEndReached(folderId)) {
                return 1;
            } else if (size == 0) {
                return 5;
            } else {
                return 10;
            }
        } else if (i > size) {
            return 10;
        }
        return 0;
    }

    @Override
    public void notifyItemMoved(int fromPosition, int toPosition) {
        ArrayList<TLRPC.Dialog> dialogs = DialogsActivity.getDialogsArray(currentAccount, dialogsType, folderId, false);
        int fromIndex = fixPosition(fromPosition);
        int toIndex = fixPosition(toPosition);
        TLRPC.Dialog fromDialog = dialogs.get(fromIndex);
        TLRPC.Dialog toDialog = dialogs.get(toIndex);
        int oldNum = fromDialog.pinnedNum;
        fromDialog.pinnedNum = toDialog.pinnedNum;
        toDialog.pinnedNum = oldNum;
        Collections.swap(dialogs, fromIndex, toIndex);
        super.notifyItemMoved(fromPosition, toPosition);
    }
}
