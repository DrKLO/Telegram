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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.google.android.exoplayer2.util.Log;

import org.checkerframework.checker.units.qual.A;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.support.LongSparseIntArray;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ArchiveHintCell;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.DialogMeUrlCell;
import org.telegram.ui.Cells.DialogsEmptyCell;
import org.telegram.ui.Cells.DialogsHintCell;
import org.telegram.ui.Cells.DialogsRequestedEmptyCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ProfileSearchCell;
import org.telegram.ui.Cells.RequestPeerRequirementsCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.UserCell;
import org.telegram.ui.Components.ArchiveHelp;
import org.telegram.ui.Components.BlurredRecyclerView;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.PullForegroundDrawable;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.Stories.DialogStoriesCell;
import org.telegram.ui.Stories.StoriesController;
import org.telegram.ui.Stories.StoriesListPlaceProvider;
import org.telegram.ui.Stories.StoriesUtilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;

public class DialogsAdapter extends RecyclerListView.SelectionAdapter implements DialogCell.DialogCellDelegate {
    public final static int VIEW_TYPE_DIALOG = 0,
            VIEW_TYPE_FLICKER = 1,
            VIEW_TYPE_RECENTLY_VIEWED = 2,
            VIEW_TYPE_DIVIDER = 3,
            VIEW_TYPE_ME_URL = 4,
            VIEW_TYPE_EMPTY = 5,
            VIEW_TYPE_USER = 6,
            VIEW_TYPE_HEADER = 7,
            VIEW_TYPE_SHADOW = 8,
    //            VIEW_TYPE_ARCHIVE = 9,
    VIEW_TYPE_LAST_EMPTY = 10,
            VIEW_TYPE_NEW_CHAT_HINT = 11,
            VIEW_TYPE_TEXT = 12,
            VIEW_TYPE_CONTACTS_FLICKER = 13,
            VIEW_TYPE_HEADER_2 = 14,
            VIEW_TYPE_REQUIREMENTS = 15,
            VIEW_TYPE_REQUIRED_EMPTY = 16,
            VIEW_TYPE_FOLDER_UPDATE_HINT = 17,
            VIEW_TYPE_STORIES = 18,
            VIEW_TYPE_ARCHIVE_FULLSCREEN = 19;

    private Context mContext;
    private ArchiveHintCell archiveHintCell;
    private ArrayList<TLRPC.TL_contact> onlineContacts;
    private boolean forceUpdatingContacts;
    private int dialogsCount;
    private int prevContactsCount;
    private int prevDialogsCount;
    private int dialogsType;
    private int folderId;
    private long openedDialogId;
    private int currentCount;
    private boolean isOnlySelect;
    private ArrayList<Long> selectedDialogs;
    private boolean hasHints;
    private boolean hasChatlistHint;
    private int currentAccount;
    private boolean dialogsListFrozen;
    private boolean isReordering;
    private long lastSortTime;
    private boolean collapsedView;
    private boolean firstUpdate = true;
    RecyclerListView recyclerListView;
    private PullForegroundDrawable pullForegroundDrawable;
    ArrayList<ItemInternal> itemInternals = new ArrayList<>();
    ArrayList<ItemInternal> oldItems = new ArrayList<>();

    private Drawable arrowDrawable;

    private DialogsPreloader preloader;
    private boolean forceShowEmptyCell;

    private DialogsActivity parentFragment;
    private boolean isTransitionSupport;

    private TLRPC.RequestPeerType requestPeerType;
    public boolean isEmpty;

    public DialogsAdapter(DialogsActivity fragment, Context context, int type, int folder, boolean onlySelect, ArrayList<Long> selected, int account, TLRPC.RequestPeerType requestPeerType) {
        mContext = context;
        parentFragment = fragment;
        dialogsType = type;
        folderId = folder;
        isOnlySelect = onlySelect;
        hasHints = folder == 0 && type == 0 && !onlySelect;
        selectedDialogs = selected;
        currentAccount = account;
        //  setHasStableIds(true);
        if (folder == 0) {
            this.preloader = new DialogsPreloader();
        }
        this.requestPeerType = requestPeerType;
    }

    public void setRecyclerListView(RecyclerListView recyclerListView) {
        this.recyclerListView = recyclerListView;
    }

    public void setOpenedDialogId(long id) {
        openedDialogId = id;
    }

    public void onReorderStateChanged(boolean reordering) {
        isReordering = reordering;
    }

    public int fixPosition(int position) {
        if (hasChatlistHint) {
            position--;
        }
        if (hasHints) {
            position -= 2 + MessagesController.getInstance(currentAccount).hintDialogs.size();
        }
        if (dialogsType == DialogsActivity.DIALOGS_TYPE_IMPORT_HISTORY_GROUPS || dialogsType == DialogsActivity.DIALOGS_TYPE_IMPORT_HISTORY) {
            position -= 2;
        } else if (dialogsType == DialogsActivity.DIALOGS_TYPE_IMPORT_HISTORY_USERS) {
            position -= 1;
        }
        return position;
    }

    public boolean isDataSetChanged() {
        return true;
    }

    public void setDialogsType(int type) {
        dialogsType = type;
        notifyDataSetChanged();
    }

    public int getDialogsType() {
        return dialogsType;
    }

    public int getDialogsCount() {
        return dialogsCount;
    }

    @Override
    public long getItemId(int position) {
        return itemInternals.get(position).stableId;
    }

    @Override
    public int getItemCount() {
        currentCount = itemInternals.size();
        return currentCount;
    }

    public int findDialogPosition(long dialogId) {
        for (int i = 0; i < itemInternals.size(); i++) {
            if (itemInternals.get(i).dialog != null && itemInternals.get(i).dialog.id == dialogId) {
                return i;
            }
        }
        return -1;
    }

    public int fixScrollGap(RecyclerListView animationSupportListView, int p, int offset, boolean hasHidenArchive, boolean hasStories, boolean hasTabs, boolean oppened) {
        int itemsToEnd = getItemCount() - p;
        int cellHeight = AndroidUtilities.dp(SharedConfig.useThreeLinesLayout ? 78 : 72);
        int bottom = offset + animationSupportListView.getPaddingTop() + itemsToEnd * cellHeight + itemsToEnd - 1;
        //fix height changed
        int top = offset + animationSupportListView.getPaddingTop() - p * cellHeight - p;
        int additionalHeight = 0;
        if (hasStories) {
            additionalHeight += AndroidUtilities.dp(DialogStoriesCell.HEIGHT_IN_DP);
        } else if (hasTabs) {
            additionalHeight += AndroidUtilities.dp(44);
        }
        if (oppened) {
            bottom -= additionalHeight;
        } else {
            bottom += additionalHeight;
        }
        if (hasHidenArchive) {
            top += cellHeight;
        }
        int paddingTop = animationSupportListView.getPaddingTop();
        if (top > paddingTop) {
            return offset + paddingTop - top;
        }
//        if (bottom < animationSupportListView.getMeasuredHeight()) {
//            return offset + (animationSupportListView.getMeasuredHeight() - bottom);
//        }
        return offset;
    }

    int stableIdPointer = 10;
    LongSparseIntArray dialogsStableIds = new LongSparseIntArray();

    private class ItemInternal extends AdapterWithDiffUtils.Item {

        TLRPC.Dialog dialog;
        TLRPC.RecentMeUrl recentMeUrl;
        TLRPC.TL_contact contact;
        boolean isForumCell;
        private boolean pinned;
        private boolean isFolder;
        TLRPC.TL_chatlists_chatlistUpdates chatlistUpdates;
        private int emptyType;

        public ItemInternal(TLRPC.TL_chatlists_chatlistUpdates updates) {
            super(VIEW_TYPE_FOLDER_UPDATE_HINT, true);
            this.chatlistUpdates = updates;
            stableId = stableIdPointer++;
        }

        private final int stableId;

        public ItemInternal(int viewType, TLRPC.Dialog dialog) {
            super(viewType, true);
            this.dialog = dialog;
            if (dialog != null) {
                int currentId = dialogsStableIds.get(dialog.id, -1);
                if (currentId >= 0) {
                    stableId = currentId;
                } else {
                    stableId = stableIdPointer++;
                    dialogsStableIds.put(dialog.id, stableId);
                }
            } else {
                if (viewType == VIEW_TYPE_ARCHIVE_FULLSCREEN) {
                    stableId = 5;
                } else {
                    stableId = stableIdPointer++;
                }
            }
            if (dialog != null) {
                if (dialogsType == 7 || dialogsType == 8) {
                    MessagesController.DialogFilter filter = MessagesController.getInstance(currentAccount).selectedDialogFilter[dialogsType == 8 ? 1 : 0];
                    pinned = filter != null && filter.pinnedDialogs.indexOfKey(dialog.id) >= 0;
                } else {
                    pinned = dialog.pinned;
                }
                isFolder = dialog.isFolder;
                isForumCell = MessagesController.getInstance(currentAccount).isForum(dialog.id);
            }
        }

        public ItemInternal(int viewTypeMeUrl, TLRPC.RecentMeUrl recentMeUrl) {
            super(viewTypeMeUrl, true);
            this.recentMeUrl = recentMeUrl;
            stableId = stableIdPointer++;
        }

        public ItemInternal(int viewTypeEmpty) {
            super(viewTypeEmpty, true);
            this.emptyType = emptyType;
            if (viewTypeEmpty == VIEW_TYPE_LAST_EMPTY) {
                stableId = 1;
            } else {
                if (viewType == VIEW_TYPE_ARCHIVE_FULLSCREEN) {
                    stableId = 5;
                } else {
                    stableId = stableIdPointer++;
                }
            }
        }

        public ItemInternal(int viewTypeEmpty, int emptyType) {
            super(viewTypeEmpty, true);
            this.emptyType = emptyType;
            stableId = stableIdPointer++;
        }

        public ItemInternal(int viewTypeUser, TLRPC.TL_contact tl_contact) {
            super(viewTypeUser, true);
            contact = tl_contact;
            if (contact != null) {
                int currentId = dialogsStableIds.get(contact.user_id, -1);
                if (currentId > 0) {
                    stableId = currentId;
                } else {
                    stableId = stableIdPointer++;
                    dialogsStableIds.put(contact.user_id, stableId);
                }
            } else {
                stableId = stableIdPointer++;
            }
        }

        boolean compare(ItemInternal itemInternal) {
            if (viewType != itemInternal.viewType) {
                return false;
            }
            if (viewType == VIEW_TYPE_DIALOG) {
                return dialog != null && itemInternal.dialog != null && dialog.id == itemInternal.dialog.id
                        && isFolder == itemInternal.isFolder &&
                        isForumCell == itemInternal.isForumCell &&
                        pinned == itemInternal.pinned;
            }
            if (viewType == VIEW_TYPE_HEADER_2) {
                return dialog != null && itemInternal.dialog != null && dialog.id == itemInternal.dialog.id && dialog.isFolder == itemInternal.dialog.isFolder;
            }
            if (viewType == VIEW_TYPE_ME_URL) {
                return recentMeUrl != null && itemInternal.recentMeUrl != null && recentMeUrl.url != null && recentMeUrl.url.equals(recentMeUrl.url);
            }
            if (viewType == VIEW_TYPE_USER) {
                return contact != null && itemInternal.contact != null && contact.user_id == itemInternal.contact.user_id;
            }
            if (viewType == VIEW_TYPE_EMPTY) {
                return emptyType == itemInternal.emptyType;
            }
            if (viewType == VIEW_TYPE_LAST_EMPTY) {
                return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(dialog, recentMeUrl, contact);
        }
    }

    public TLObject getItem(int i) {
        if (i < 0 || i >= itemInternals.size()) {
            return null;
        }
        if (itemInternals.get(i).dialog != null) {
            return itemInternals.get(i).dialog;
        } else if (itemInternals.get(i).contact != null) {
            return MessagesController.getInstance(currentAccount).getUser(itemInternals.get(i).contact.user_id);
        } else if (itemInternals.get(i).recentMeUrl != null) {
            return itemInternals.get(i).recentMeUrl;
        }
        return null;
    }

    public void sortOnlineContacts(boolean notify) {
        if (onlineContacts == null || notify && (SystemClock.elapsedRealtime() - lastSortTime) < 2000) {
            return;
        }
        lastSortTime = SystemClock.elapsedRealtime();
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
                } else if (status2 < 0 || status1 != 0) {
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

    public boolean getDialogsListIsFrozen() {
        return dialogsListFrozen;
    }

    public ViewPager getArchiveHintCellPager() {
        return archiveHintCell != null ? archiveHintCell.getViewPager() : null;
    }

    public void updateHasHints() {
        hasHints = folderId == 0 && dialogsType == DialogsActivity.DIALOGS_TYPE_DEFAULT && !isOnlySelect && !MessagesController.getInstance(currentAccount).hintDialogs.isEmpty();
    }

    public void updateList(RecyclerListView recyclerListView, boolean hasHiddenArchive, float tabsTranslation, boolean hasStories) {
        oldItems.clear();
        oldItems.addAll(itemInternals);
        updateItemList();

        if (recyclerListView != null && recyclerListView.getScrollState() == RecyclerView.SCROLL_STATE_IDLE && recyclerListView.getChildCount() > 0 && recyclerListView.getLayoutManager() != null) {
            LinearLayoutManager layoutManager = ((LinearLayoutManager) recyclerListView.getLayoutManager());
            View view = null;
            int position = -1;
            int top = Integer.MAX_VALUE;
            for (int i = 0; i < recyclerListView.getChildCount(); i++) {
                int childPosition = recyclerListView.getChildAdapterPosition(recyclerListView.getChildAt(i));
                View child = recyclerListView.getChildAt(i);
                if (childPosition != RecyclerListView.NO_POSITION && child != null && child.getTop() < top) {
                    view = child;
                    position = childPosition;
                    top = child.getTop();
                }
            }
            if (view != null) {
                float offset = view.getTop() - recyclerListView.getPaddingTop();
                if (!hasStories) {
                    //  offset += tabsTranslation;
                } else {
                    tabsTranslation = 0;
                }
                if (hasHiddenArchive && position == 0 && recyclerListView.getPaddingTop() - view.getTop() - view.getMeasuredHeight() + tabsTranslation < 0) {
                    position = 1;
                    offset = tabsTranslation;
                }
//                if (firstUpdate && hasStories) {
//                    offset -= AndroidUtilities.dp(DialogStoriesCell.HEIGHT_IN_DP);
//                }
//                firstUpdate = false;
                layoutManager.scrollToPositionWithOffset(position, (int) offset);
            }
        }
        DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size();
            }

            @Override
            public int getNewListSize() {
                return itemInternals.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldItems.get(oldItemPosition).compare(itemInternals.get(newItemPosition));
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return oldItems.get(oldItemPosition).viewType == itemInternals.get(newItemPosition).viewType;
            }
        }).dispatchUpdatesTo(this);
    }

    @Override
    public void notifyDataSetChanged() {
        updateItemList();
        super.notifyDataSetChanged();
    }


    @Override
    public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
        if (holder.itemView instanceof DialogCell) {
            DialogCell dialogCell = (DialogCell) holder.itemView;
            dialogCell.onReorderStateChanged(isReordering, false);
            dialogCell.checkCurrentDialogIndex(dialogsListFrozen);
            dialogCell.setChecked(selectedDialogs.contains(dialogCell.getDialogId()), false);
        }
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        int viewType = holder.getItemViewType();
        return viewType != VIEW_TYPE_FLICKER && viewType != VIEW_TYPE_EMPTY && viewType != VIEW_TYPE_DIVIDER &&
                viewType != VIEW_TYPE_SHADOW && viewType != VIEW_TYPE_HEADER &&
                viewType != VIEW_TYPE_LAST_EMPTY && viewType != VIEW_TYPE_NEW_CHAT_HINT && viewType != VIEW_TYPE_CONTACTS_FLICKER &&
                viewType != VIEW_TYPE_REQUIREMENTS && viewType != VIEW_TYPE_REQUIRED_EMPTY && viewType != VIEW_TYPE_STORIES && viewType != VIEW_TYPE_ARCHIVE_FULLSCREEN;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View view;
        switch (viewType) {
            case VIEW_TYPE_DIALOG:
                if (dialogsType == DialogsActivity.DIALOGS_TYPE_ADD_USERS_TO ||
                        dialogsType == DialogsActivity.DIALOGS_TYPE_BOT_REQUEST_PEER) {
                    view = new ProfileSearchCell(mContext);
                } else {
                    DialogCell dialogCell = new DialogCell(parentFragment, mContext, true, false, currentAccount, null);
                    dialogCell.setArchivedPullAnimation(pullForegroundDrawable);
                    dialogCell.setPreloader(preloader);
                    dialogCell.setDialogCellDelegate(this);
                    dialogCell.setIsTransitionSupport(isTransitionSupport);
                    view = dialogCell;
                }
                if (dialogsType == DialogsActivity.DIALOGS_TYPE_BOT_REQUEST_PEER) {
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                }
                break;
            case VIEW_TYPE_REQUIREMENTS:
                view = new RequestPeerRequirementsCell(mContext);
                break;
            case VIEW_TYPE_FLICKER:
            case VIEW_TYPE_CONTACTS_FLICKER:
                FlickerLoadingView flickerLoadingView = new FlickerLoadingView(mContext);
                flickerLoadingView.setIsSingleCell(true);
                int flickerType = viewType == VIEW_TYPE_CONTACTS_FLICKER ? FlickerLoadingView.CONTACT_TYPE : FlickerLoadingView.DIALOG_CELL_TYPE;
                flickerLoadingView.setViewType(flickerType);
                if (flickerType == FlickerLoadingView.CONTACT_TYPE) {
                    flickerLoadingView.setIgnoreHeightCheck(true);
                }
                if (viewType == VIEW_TYPE_CONTACTS_FLICKER) {
                    flickerLoadingView.setItemsCount((int) (AndroidUtilities.displaySize.y * 0.5f / AndroidUtilities.dp(64)));
                }
                view = flickerLoadingView;
                break;
            case VIEW_TYPE_RECENTLY_VIEWED: {
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
            case VIEW_TYPE_DIVIDER:
                FrameLayout frameLayout = new FrameLayout(mContext) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(12), MeasureSpec.EXACTLY));
                    }
                };
                frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
                View v = new View(mContext);
                v.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                frameLayout.addView(v, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                view = frameLayout;
                break;
            case VIEW_TYPE_ME_URL:
                view = new DialogMeUrlCell(mContext);
                break;
            case VIEW_TYPE_EMPTY:
                view = new DialogsEmptyCell(mContext);
                break;
            case VIEW_TYPE_REQUIRED_EMPTY:
                view = new DialogsRequestedEmptyCell(mContext) {
                    @Override
                    protected void onButtonClick() {
                        onCreateGroupForThisClick();
                    }
                };
                break;
            case VIEW_TYPE_USER:
                view = new UserCell(mContext, 8, 0, false);
                break;
            case VIEW_TYPE_HEADER:
                view = new HeaderCell(mContext);
                view.setPadding(0, 0, 0, AndroidUtilities.dp(12));
                break;
            case VIEW_TYPE_HEADER_2:
                HeaderCell cell = new HeaderCell(mContext, Theme.key_graySectionText, 16, 0, false);
                cell.setHeight(32);
                view = cell;
                view.setClickable(false);
                break;
            case VIEW_TYPE_SHADOW: {
                view = new ShadowSectionCell(mContext);
                Drawable drawable = Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow);
                CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), drawable);
                combinedDrawable.setFullsize(true);
                view.setBackgroundDrawable(combinedDrawable);
                break;
            }
            case VIEW_TYPE_ARCHIVE_FULLSCREEN:
                LastEmptyView lastEmptyView = new LastEmptyView(mContext);
                lastEmptyView.addView(
                        new ArchiveHelp(mContext, currentAccount, null, DialogsAdapter.this::onArchiveSettingsClick, null),
                        LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER, 0, -(int) (DialogStoriesCell.HEIGHT_IN_DP * .5f), 0, 0)
                );
                view = lastEmptyView;
                break;
            case VIEW_TYPE_LAST_EMPTY: {
                view = new LastEmptyView(mContext);
                break;
            }
            case VIEW_TYPE_NEW_CHAT_HINT: {
                view = new TextInfoPrivacyCell(mContext) {

                    private int movement;
                    private float moveProgress;
                    private long lastUpdateTime;
                    private int originalX;
                    private int originalY;

                    @Override
                    protected void afterTextDraw() {
                        if (arrowDrawable != null) {
                            Rect bounds = arrowDrawable.getBounds();
                            arrowDrawable.setBounds(originalX, originalY, originalX + bounds.width(), originalY + bounds.height());
                        }
                    }

                    @Override
                    protected void onTextDraw() {
                        if (arrowDrawable != null) {
                            Rect bounds = arrowDrawable.getBounds();
                            int dx = (int) (moveProgress * AndroidUtilities.dp(3));
                            originalX = bounds.left;
                            originalY = bounds.top;
                            arrowDrawable.setBounds(originalX + dx, originalY + AndroidUtilities.dp(1), originalX + dx + bounds.width(), originalY + AndroidUtilities.dp(1) + bounds.height());

                            long newUpdateTime = SystemClock.elapsedRealtime();
                            long dt = newUpdateTime - lastUpdateTime;
                            if (dt > 17) {
                                dt = 17;
                            }
                            lastUpdateTime = newUpdateTime;
                            if (movement == 0) {
                                moveProgress += dt / 664.0f;
                                if (moveProgress >= 1.0f) {
                                    movement = 1;
                                    moveProgress = 1.0f;
                                }
                            } else {
                                moveProgress -= dt / 664.0f;
                                if (moveProgress <= 0.0f) {
                                    movement = 0;
                                    moveProgress = 0.0f;
                                }
                            }
                            getTextView().invalidate();
                        }
                    }
                };
                Drawable drawable = Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow);
                CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), drawable);
                combinedDrawable.setFullsize(true);
                view.setBackgroundDrawable(combinedDrawable);
                break;
            }
            case VIEW_TYPE_FOLDER_UPDATE_HINT:
                view = new DialogsHintCell(mContext);
                break;
            case VIEW_TYPE_TEXT:
            default: {
                view = new TextCell(mContext);
                if (dialogsType == DialogsActivity.DIALOGS_TYPE_BOT_REQUEST_PEER) {
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                }
                break;
            }
            case VIEW_TYPE_STORIES: {
                view = new View(mContext) {
                    @Override
                    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(DialogStoriesCell.HEIGHT_IN_DP), MeasureSpec.EXACTLY));
                    }
                };
                break;
            }
        }
        view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, viewType == VIEW_TYPE_EMPTY || viewType == VIEW_TYPE_ARCHIVE_FULLSCREEN ? RecyclerView.LayoutParams.MATCH_PARENT : RecyclerView.LayoutParams.WRAP_CONTENT));
        return new RecyclerListView.Holder(view);
    }

    public void onCreateGroupForThisClick() {

    }

    protected void onArchiveSettingsClick() {

    }

    public int lastDialogsEmptyType = -1;

    public int dialogsEmptyType() {
        if (dialogsType == 7 || dialogsType == 8) {
            if (MessagesController.getInstance(currentAccount).isDialogsEndReached(folderId)) {
                return DialogsEmptyCell.TYPE_FILTER_NO_CHATS_TO_DISPLAY;
            } else {
                return DialogsEmptyCell.TYPE_FILTER_ADDING_CHATS;
            }
        } else if (folderId == 1) {
            return DialogsEmptyCell.TYPE_FILTER_NO_CHATS_TO_DISPLAY;
        } else {
            return onlineContacts != null ? DialogsEmptyCell.TYPE_WELCOME_WITH_CONTACTS : DialogsEmptyCell.TYPE_WELCOME_NO_CONTACTS;
        }
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int i) {
        switch (holder.getItemViewType()) {
            case VIEW_TYPE_DIALOG: {
                TLRPC.Dialog dialog = (TLRPC.Dialog) getItem(i);
                TLRPC.Dialog nextDialog = (TLRPC.Dialog) getItem(i + 1);
                if (dialogsType == DialogsActivity.DIALOGS_TYPE_ADD_USERS_TO || dialogsType == DialogsActivity.DIALOGS_TYPE_BOT_REQUEST_PEER) {
                    ProfileSearchCell cell = (ProfileSearchCell) holder.itemView;
                    long oldDialogId = cell.getDialogId();

                    TLObject object = null;
                    TLRPC.Chat chat = null;
                    CharSequence title = null;
                    CharSequence subtitle;
                    boolean isRecent = false;

                    if (dialog.id != 0) {
                        chat = MessagesController.getInstance(currentAccount).getChat(-dialog.id);
                        if (chat != null && chat.migrated_to != null) {
                            TLRPC.Chat chat2 = MessagesController.getInstance(currentAccount).getChat(chat.migrated_to.channel_id);
                            if (chat2 != null) {
                                chat = chat2;
                            }
                        }
                    }

                    if (chat != null) {
                        object = chat;
                        title = chat.title;
                        if (ChatObject.isChannel(chat) && !chat.megagroup) {
                            if (chat.participants_count != 0) {
                                subtitle = LocaleController.formatPluralStringComma("Subscribers", chat.participants_count);
                            } else {
                                if (!ChatObject.isPublic(chat)) {
                                    subtitle = LocaleController.getString("ChannelPrivate", R.string.ChannelPrivate).toLowerCase();
                                } else {
                                    subtitle = LocaleController.getString("ChannelPublic", R.string.ChannelPublic).toLowerCase();
                                }
                            }
                        } else {
                            if (chat.participants_count != 0) {
                                subtitle = LocaleController.formatPluralStringComma("Members", chat.participants_count);
                            } else {
                                if (chat.has_geo) {
                                    subtitle = LocaleController.getString("MegaLocation", R.string.MegaLocation);
                                } else if (!ChatObject.isPublic(chat)) {
                                    subtitle = LocaleController.getString("MegaPrivate", R.string.MegaPrivate).toLowerCase();
                                } else {
                                    subtitle = LocaleController.getString("MegaPublic", R.string.MegaPublic).toLowerCase();
                                }
                            }
                        }
                    } else {
                        subtitle = "";
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialog.id);
                        if (user != null) {
                            object = user;
                            title = UserObject.getUserName(user);
                            if (!UserObject.isReplyUser(user)) {
                                if (user.bot) {
                                    subtitle = LocaleController.getString("Bot", R.string.Bot);
                                } else {
                                    subtitle = LocaleController.formatUserStatus(currentAccount, user);
                                }
                            }
                        }
                    }
                    cell.useSeparator = nextDialog != null;
                    cell.setData(object, null, title, subtitle, isRecent, false);
                    cell.setChecked(selectedDialogs.contains(cell.getDialogId()), oldDialogId == cell.getDialogId());
                } else {
                    DialogCell cell = (DialogCell) holder.itemView;
                    cell.useSeparator = nextDialog != null;
                    cell.fullSeparator = dialog.pinned && nextDialog != null && !nextDialog.pinned;
                    if (dialogsType == DialogsActivity.DIALOGS_TYPE_DEFAULT) {
                        if (AndroidUtilities.isTablet()) {
                            cell.setDialogSelected(dialog.id == openedDialogId);
                        }
                    }
                    cell.setChecked(selectedDialogs.contains(dialog.id), false);
                    cell.setDialog(dialog, dialogsType, folderId);
                    cell.checkHeight();
                    if (cell.collapsed != collapsedView) {
                        cell.collapsed = collapsedView;
                        cell.requestLayout();
                    }
                    if (preloader != null && i < 10) {
                        preloader.add(dialog.id);
                    }
                }
                break;
            }
            case VIEW_TYPE_EMPTY: {
                DialogsEmptyCell cell = (DialogsEmptyCell) holder.itemView;
                int fromDialogsEmptyType = lastDialogsEmptyType;
                cell.setType(lastDialogsEmptyType = dialogsEmptyType(), isOnlySelect);
                if (dialogsType != 7 && dialogsType != 8) {
                    cell.setOnUtyanAnimationEndListener(() -> parentFragment.setScrollDisabled(false));
                    cell.setOnUtyanAnimationUpdateListener(progress -> parentFragment.setContactsAlpha(progress));
                    if (!cell.isUtyanAnimationTriggered() && dialogsCount == 0) {
                        parentFragment.setContactsAlpha(0f);
                        parentFragment.setScrollDisabled(true);
                    }
                    if (onlineContacts != null && fromDialogsEmptyType == DialogsEmptyCell.TYPE_WELCOME_NO_CONTACTS) {
                        if (!cell.isUtyanAnimationTriggered()) {
                            cell.startUtyanCollapseAnimation(true);
                        }
                    } else if (forceUpdatingContacts) {
                        if (dialogsCount == 0) {
                            cell.startUtyanCollapseAnimation(false);
                        }
                    } else if (cell.isUtyanAnimationTriggered() && lastDialogsEmptyType == DialogsEmptyCell.TYPE_WELCOME_NO_CONTACTS) {
                        cell.startUtyanExpandAnimation();
                    }
                }
                break;
            }
            case VIEW_TYPE_REQUIRED_EMPTY: {
                ((DialogsRequestedEmptyCell) holder.itemView).set(requestPeerType);
                break;
            }
            case VIEW_TYPE_ME_URL: {
                DialogMeUrlCell cell = (DialogMeUrlCell) holder.itemView;
                cell.setRecentMeUrl((TLRPC.RecentMeUrl) getItem(i));
                break;
            }
            case VIEW_TYPE_USER: {
                UserCell cell = (UserCell) holder.itemView;
                TLRPC.User user = (TLRPC.User) getItem(i);
                cell.setData(user, null, null, 0);
                break;
            }
            case VIEW_TYPE_HEADER: {
                HeaderCell cell = (HeaderCell) holder.itemView;
                if (
                        dialogsType == DialogsActivity.DIALOGS_TYPE_IMPORT_HISTORY_GROUPS ||
                                dialogsType == DialogsActivity.DIALOGS_TYPE_IMPORT_HISTORY_USERS ||
                                dialogsType == DialogsActivity.DIALOGS_TYPE_IMPORT_HISTORY
                ) {
                    if (i == 0) {
                        cell.setText(LocaleController.getString("ImportHeader", R.string.ImportHeader));
                    } else {
                        cell.setText(LocaleController.getString("ImportHeaderContacts", R.string.ImportHeaderContacts));
                    }
                } else {
                    cell.setText(LocaleController.getString(dialogsCount == 0 && forceUpdatingContacts ? R.string.ConnectingYourContacts : R.string.YourContacts));
                }
                break;
            }
            case VIEW_TYPE_HEADER_2: {
                HeaderCell cell = (HeaderCell) holder.itemView;
                cell.setTextSize(14);
                cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                cell.setBackgroundColor(Theme.getColor(Theme.key_graySection));
                switch (((DialogsActivity.DialogsHeader) getItem(i)).headerType) {
                    case DialogsActivity.DialogsHeader.HEADER_TYPE_MY_CHANNELS:
                        cell.setText(LocaleController.getString("MyChannels", R.string.MyChannels));
                        break;
                    case DialogsActivity.DialogsHeader.HEADER_TYPE_MY_GROUPS:
                        cell.setText(LocaleController.getString("MyGroups", R.string.MyGroups));
                        break;
                    case DialogsActivity.DialogsHeader.HEADER_TYPE_GROUPS:
                        cell.setText(LocaleController.getString("FilterGroups", R.string.FilterGroups));
                        break;
                }
                break;
            }
            case VIEW_TYPE_NEW_CHAT_HINT: {
                TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                cell.setText(LocaleController.getString("TapOnThePencil", R.string.TapOnThePencil));
                if (arrowDrawable == null) {
                    arrowDrawable = mContext.getResources().getDrawable(R.drawable.arrow_newchat);
                    arrowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4), PorterDuff.Mode.MULTIPLY));
                }
                TextView textView = cell.getTextView();
                textView.setCompoundDrawablePadding(AndroidUtilities.dp(4));
                textView.setCompoundDrawablesWithIntrinsicBounds(null, null, arrowDrawable, null);
                textView.getLayoutParams().width = LayoutHelper.WRAP_CONTENT;
                break;
            }
            case VIEW_TYPE_TEXT: {
                TextCell cell = (TextCell) holder.itemView;
                cell.setColors(Theme.key_windowBackgroundWhiteBlueText4, Theme.key_windowBackgroundWhiteBlueText4);
                if (requestPeerType != null) {
                    if (requestPeerType instanceof TLRPC.TL_requestPeerTypeBroadcast) {
                        cell.setTextAndIcon(LocaleController.getString("CreateChannelForThis", R.string.CreateChannelForThis), R.drawable.msg_channel_create, true);
                    } else {
                        cell.setTextAndIcon(LocaleController.getString("CreateGroupForThis", R.string.CreateGroupForThis), R.drawable.msg_groups_create, true);
                    }
                } else {
                    cell.setTextAndIcon(LocaleController.getString("CreateGroupForImport", R.string.CreateGroupForImport), R.drawable.msg_groups_create, dialogsCount != 0);
                }
                cell.setIsInDialogs();
                cell.setOffsetFromImage(75);
                break;
            }
            case VIEW_TYPE_REQUIREMENTS: {
                RequestPeerRequirementsCell cell = (RequestPeerRequirementsCell) holder.itemView;
                cell.set(requestPeerType);
                break;
            }
            case VIEW_TYPE_FOLDER_UPDATE_HINT: {
                DialogsHintCell hintCell = (DialogsHintCell) holder.itemView;
                ItemInternal item = itemInternals.get(i);
                if (item.chatlistUpdates != null) {
                    int count = item.chatlistUpdates.missing_peers.size();
                    hintCell.setText(
                            AndroidUtilities.replaceSingleTag(
                                    LocaleController.formatPluralString("FolderUpdatesTitle", count),
                                    Theme.key_windowBackgroundWhiteValueText,
                                    0,
                                    null
                            ),
                            LocaleController.formatPluralString("FolderUpdatesSubtitle", count)
                    );
                }
                break;
            }
        }
        if (i >= dialogsCount + 1) {
            holder.itemView.setAlpha(1f);
        }
    }

    public TLRPC.TL_chatlists_chatlistUpdates getChatlistUpdate() {
        ItemInternal item = itemInternals.get(0);
        if (item != null && item.viewType == VIEW_TYPE_FOLDER_UPDATE_HINT) {
            return item.chatlistUpdates;
        }
        return null;
    }

    public void setForceUpdatingContacts(boolean forceUpdatingContacts) {
        this.forceUpdatingContacts = forceUpdatingContacts;
    }

    @Override
    public int getItemViewType(int i) {
        return itemInternals.get(i).viewType;
    }

    public void moveDialogs(RecyclerListView recyclerView, int fromPosition, int toPosition) {
        ArrayList<TLRPC.Dialog> dialogs = parentFragment.getDialogsArray(currentAccount, dialogsType, folderId, false);
        int fromIndex = fixPosition(fromPosition);
        int toIndex = fixPosition(toPosition);
        TLRPC.Dialog fromDialog = dialogs.get(fromIndex);
        TLRPC.Dialog toDialog = dialogs.get(toIndex);
        if (dialogsType == 7 || dialogsType == 8) {
            MessagesController.DialogFilter filter = MessagesController.getInstance(currentAccount).selectedDialogFilter[dialogsType == 8 ? 1 : 0];
            int idx1 = filter.pinnedDialogs.get(fromDialog.id);
            int idx2 = filter.pinnedDialogs.get(toDialog.id);
            filter.pinnedDialogs.put(fromDialog.id, idx2);
            filter.pinnedDialogs.put(toDialog.id, idx1);
        } else {
            int oldNum = fromDialog.pinnedNum;
            fromDialog.pinnedNum = toDialog.pinnedNum;
            toDialog.pinnedNum = oldNum;
        }
        Collections.swap(dialogs, fromIndex, toIndex);
        updateList(recyclerView, false, 0, false);
    }

    @Override
    public void notifyItemMoved(int fromPosition, int toPosition) {
        super.notifyItemMoved(fromPosition, toPosition);
    }

    public void setArchivedPullDrawable(PullForegroundDrawable drawable) {
        pullForegroundDrawable = drawable;
    }

    public void didDatabaseCleared() {
        if (preloader != null) {
            preloader.clear();
        }
    }

    public void resume() {
        if (preloader != null) {
            preloader.resume();
        }
    }

    public void pause() {
        if (preloader != null) {
            preloader.pause();
        }
    }

    @Override
    public void onButtonClicked(DialogCell dialogCell) {

    }

    @Override
    public void onButtonLongPress(DialogCell dialogCell) {

    }

    @Override
    public boolean canClickButtonInside() {
        return selectedDialogs.isEmpty();
    }

    @Override
    public void openStory(DialogCell dialogCell, Runnable onDone) {
        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        if (MessagesController.getInstance(currentAccount).getStoriesController().hasStories(dialogCell.getDialogId())) {
            parentFragment.getOrCreateStoryViewer().doOnAnimationReady(onDone);
            parentFragment.getOrCreateStoryViewer().open(parentFragment.getContext(), dialogCell.getDialogId(), StoriesListPlaceProvider.of((RecyclerListView) dialogCell.getParent()));
            return;
        }
    }

    @Override
    public void showChatPreview(DialogCell cell) {
        parentFragment.showChatPreview(cell);
    }

    @Override
    public void openHiddenStories() {
        StoriesController storiesController = MessagesController.getInstance(currentAccount).getStoriesController();
        if (storiesController.getHiddenList().isEmpty()) {
            return;
        }
        boolean unreadOnly = storiesController.getUnreadState(storiesController.getHiddenList().get(0).user_id) != StoriesController.STATE_READ;
        ArrayList<Long> peerIds = new ArrayList<>();
        for (int i = 0; i < storiesController.getHiddenList().size(); i++) {
            if (!unreadOnly || storiesController.getUnreadState(storiesController.getHiddenList().get(i).user_id) != StoriesController.STATE_READ) {
                peerIds.add(storiesController.getHiddenList().get(i).user_id);
            }
        }

        parentFragment.getOrCreateStoryViewer().open(mContext, null, peerIds, 0, null, null, StoriesListPlaceProvider.of(recyclerListView, true), false);
    }

    public void setIsTransitionSupport() {
        this.isTransitionSupport = true;
    }

    public void setCollapsedView(boolean collapsedView, RecyclerListView listView) {
        this.collapsedView = collapsedView;
        for (int i = 0; i < listView.getChildCount(); i++) {
            if (listView.getChildAt(i) instanceof DialogCell) {
                ((DialogCell) listView.getChildAt(i)).collapsed = collapsedView;
            }
        }
        for (int i = 0; i < listView.getCachedChildCount(); i++) {
            if (listView.getCachedChildAt(i) instanceof DialogCell) {
                ((DialogCell) listView.getCachedChildAt(i)).collapsed = collapsedView;
            }
        }
        for (int i = 0; i < listView.getHiddenChildCount(); i++) {
            if (listView.getHiddenChildAt(i) instanceof DialogCell) {
                ((DialogCell) listView.getHiddenChildAt(i)).collapsed = collapsedView;
            }
        }
        for (int i = 0; i < listView.getAttachedScrapChildCount(); i++) {
            if (listView.getAttachedScrapChildAt(i) instanceof DialogCell) {
                ((DialogCell) listView.getAttachedScrapChildAt(i)).collapsed = collapsedView;
            }
        }
    }

    public static class DialogsPreloader {

        private final int MAX_REQUEST_COUNT = 4;
        private final int MAX_NETWORK_REQUEST_COUNT = 10 - MAX_REQUEST_COUNT;
        private final int NETWORK_REQUESTS_RESET_TIME = 60_000;

        HashSet<Long> dialogsReadyMap = new HashSet<>();
        HashSet<Long> preloadedErrorMap = new HashSet<>();

        HashSet<Long> loadingDialogs = new HashSet<>();
        ArrayList<Long> preloadDialogsPool = new ArrayList<>();
        int currentRequestCount;
        int networkRequestCount;

        boolean resumed;

        Runnable clearNetworkRequestCount = () -> {
            networkRequestCount = 0;
            start();
        };

        public void add(long dialog_id) {
            if (isReady(dialog_id) || preloadedErrorMap.contains(dialog_id) || loadingDialogs.contains(dialog_id) || preloadDialogsPool.contains(dialog_id)) {
                return;
            }
            preloadDialogsPool.add(dialog_id);
            start();
        }

        private void start() {
            if (!preloadIsAvilable() || !resumed || preloadDialogsPool.isEmpty() || currentRequestCount >= MAX_REQUEST_COUNT || networkRequestCount > MAX_NETWORK_REQUEST_COUNT) {
                return;
            }
            long dialog_id = preloadDialogsPool.remove(0);
            currentRequestCount++;
            loadingDialogs.add(dialog_id);
            MessagesController.getInstance(UserConfig.selectedAccount).ensureMessagesLoaded(dialog_id, 0, new MessagesController.MessagesLoadedCallback() {
                @Override
                public void onMessagesLoaded(boolean fromCache) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (!fromCache) {
                            networkRequestCount++;
                            if (networkRequestCount >= MAX_NETWORK_REQUEST_COUNT) {
                                AndroidUtilities.cancelRunOnUIThread(clearNetworkRequestCount);
                                AndroidUtilities.runOnUIThread(clearNetworkRequestCount, NETWORK_REQUESTS_RESET_TIME);
                            }
                        }
                        if (loadingDialogs.remove(dialog_id)) {
                            dialogsReadyMap.add(dialog_id);
                            updateList();
                            currentRequestCount--;
                            start();
                        }
                    });
                }

                @Override
                public void onError() {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (loadingDialogs.remove(dialog_id)) {
                            preloadedErrorMap.add(dialog_id);
                            currentRequestCount--;
                            start();
                        }
                    });
                }
            });
        }

        private boolean preloadIsAvilable() {
            return false;
            // return DownloadController.getInstance(UserConfig.selectedAccount).getCurrentDownloadMask() != 0;
        }

        public void updateList() {
        }

        public boolean isReady(long currentDialogId) {
            return dialogsReadyMap.contains(currentDialogId);
        }

        public boolean preloadedError(long currendDialogId) {
            return preloadedErrorMap.contains(currendDialogId);
        }

        public void remove(long currentDialogId) {
            preloadDialogsPool.remove(currentDialogId);
        }

        public void clear() {
            dialogsReadyMap.clear();
            preloadedErrorMap.clear();
            loadingDialogs.clear();
            preloadDialogsPool.clear();
            currentRequestCount = 0;
            networkRequestCount = 0;
            AndroidUtilities.cancelRunOnUIThread(clearNetworkRequestCount);
            updateList();
        }

        public void resume() {
            resumed = true;
            start();
        }

        public void pause() {
            resumed = false;
        }
    }

    public int getCurrentCount() {
        return currentCount;
    }

    public void setForceShowEmptyCell(boolean forceShowEmptyCell) {
        this.forceShowEmptyCell = forceShowEmptyCell;
    }

    public class LastEmptyView extends FrameLayout {

        public boolean moving;

        public LastEmptyView(Context context) {
            super(context);
        }

        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int size = itemInternals.size();
            boolean hasArchive = folderId == 0 && dialogsType == 0 && MessagesController.getInstance(currentAccount).dialogs_dict.get(DialogObject.makeFolderDialogId(1)) != null;
            View parent = (View) getParent();
            int height;
            int blurOffset = 0;
            if (parent instanceof BlurredRecyclerView) {
                blurOffset = ((BlurredRecyclerView) parent).blurTopPadding;
            }
            boolean collapsedView = DialogsAdapter.this.collapsedView;
            int paddingTop = parent.getPaddingTop();
            paddingTop -= blurOffset;
            if (folderId == 1 && size == 1 && itemInternals.get(0).viewType == VIEW_TYPE_ARCHIVE_FULLSCREEN) {
                height = MeasureSpec.getSize(heightMeasureSpec);
                if (height == 0) {
                    height = parent.getMeasuredHeight();
                }
                if (height == 0) {
                    height = AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight() - (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
                }
                if (parentFragment.hasStories) {
                    height += AndroidUtilities.dp(DialogStoriesCell.HEIGHT_IN_DP);
                }
            } else if (size == 0 || paddingTop == 0 && !hasArchive) {
                height = 0;
            } else {
                height = MeasureSpec.getSize(heightMeasureSpec);
                if (height == 0) {
                    height = parent.getMeasuredHeight();
                }
                if (height == 0) {
                    height = AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight() - (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
                }
                height -= blurOffset;
                int cellHeight = AndroidUtilities.dp(SharedConfig.useThreeLinesLayout ? 78 : 72);
                int dialogsHeight = 0;
                for (int i = 0; i < size; i++) {
                    if (itemInternals.get(i).viewType == VIEW_TYPE_DIALOG) {
                        if (itemInternals.get(i).isForumCell && !collapsedView) {
                            dialogsHeight += AndroidUtilities.dp(SharedConfig.useThreeLinesLayout ? 86 : 91);
                        } else {
                            dialogsHeight += cellHeight;
                        }
                    }
                }
                dialogsHeight += size - 1;
                if (onlineContacts != null) {
                    dialogsHeight += onlineContacts.size() * AndroidUtilities.dp(58) + (onlineContacts.size() - 1) + AndroidUtilities.dp(52);
                }
                int archiveHeight = (hasArchive ? cellHeight + 1 : 0);
                if (dialogsHeight < height) {
                    height = height - dialogsHeight + archiveHeight;
                    if (paddingTop != 0) {
                        height -= AndroidUtilities.statusBarHeight;
                        if (parentFragment.hasStories && !collapsedView && !isTransitionSupport) {
                            height -= ActionBar.getCurrentActionBarHeight();
                        } else if (collapsedView) {
                            height -= paddingTop;
                        }
                    }
                } else if (dialogsHeight - height < archiveHeight) {
                    height = archiveHeight - (dialogsHeight - height);
                    if (paddingTop != 0) {
                        height -= AndroidUtilities.statusBarHeight;
                        if (parentFragment.hasStories && !collapsedView && !isTransitionSupport) {
                            height -= ActionBar.getCurrentActionBarHeight();
                        } else if (collapsedView) {
                            height -= paddingTop;
                        }
                    }
                } else {
                    height = 0;
                }
            }
            if (height < 0) {
                height = 0;
            }
            if (isTransitionSupport) {
                height += AndroidUtilities.dp(1000);
            }
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        }
    }


    private void updateItemList() {
        itemInternals.clear();
        updateHasHints();

        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        ArrayList<TLRPC.Dialog> array = parentFragment.getDialogsArray(currentAccount, dialogsType, folderId, dialogsListFrozen);
        if (array == null) {
            array = new ArrayList<>();
        }
        dialogsCount = array.size();
        isEmpty = false;
        if (dialogsCount == 0 && parentFragment.isArchive()) {
            itemInternals.add(new ItemInternal(VIEW_TYPE_ARCHIVE_FULLSCREEN));
            return;
        }

        if (!hasHints && dialogsType == 0 && folderId == 0 && messagesController.isDialogsEndReached(folderId) && !forceUpdatingContacts) {
            if (messagesController.getAllFoldersDialogsCount() <= 10 && ContactsController.getInstance(currentAccount).doneLoadingContacts && !ContactsController.getInstance(currentAccount).contacts.isEmpty()) {
                onlineContacts = new ArrayList<>(ContactsController.getInstance(currentAccount).contacts);
                long selfId = UserConfig.getInstance(currentAccount).clientUserId;
                for (int a = 0, N = onlineContacts.size(); a < N; a++) {
                    long userId = onlineContacts.get(a).user_id;
                    if (userId == selfId || messagesController.dialogs_dict.get(userId) != null) {
                        onlineContacts.remove(a);
                        a--;
                        N--;
                    }
                }
                if (onlineContacts.isEmpty()) {
                    onlineContacts = null;
                } else {
                    sortOnlineContacts(false);
                }
            } else {
                onlineContacts = null;
            }
        }

        hasChatlistHint = false;
        if (dialogsType == 7 || dialogsType == 8) {
            MessagesController.DialogFilter filter = messagesController.selectedDialogFilter[dialogsType - 7];
            if (filter != null && filter.isChatlist()) {
                messagesController.checkChatlistFolderUpdate(filter.id, false);
                TLRPC.TL_chatlists_chatlistUpdates updates = messagesController.getChatlistFolderUpdates(filter.id);
                if (updates != null && updates.missing_peers.size() > 0) {
                    hasChatlistHint = true;
                    itemInternals.add(new ItemInternal(updates));
                }
            }
        }

        if (requestPeerType != null) {
            itemInternals.add(new ItemInternal(VIEW_TYPE_REQUIREMENTS));
        }

        if (collapsedView || isTransitionSupport) {
            for (int k = 0; k < array.size(); k++) {
                if (dialogsType == 2 && array.get(k) instanceof DialogsActivity.DialogsHeader) {
                    itemInternals.add(new ItemInternal(VIEW_TYPE_HEADER_2, array.get(k)));
                } else {
                    itemInternals.add(new ItemInternal(VIEW_TYPE_DIALOG, array.get(k)));
                }
            }
            itemInternals.add(new ItemInternal(VIEW_TYPE_LAST_EMPTY));
            return;
        }

        boolean stopUpdate = false;
        if (dialogsCount == 0 && forceUpdatingContacts) {
            isEmpty = true;
            if (requestPeerType != null) {
                itemInternals.add(new ItemInternal(VIEW_TYPE_REQUIRED_EMPTY));
            } else {
                itemInternals.add(new ItemInternal(VIEW_TYPE_EMPTY, dialogsEmptyType()));
            }
            itemInternals.add(new ItemInternal(VIEW_TYPE_SHADOW));
            itemInternals.add(new ItemInternal(VIEW_TYPE_HEADER));
            itemInternals.add(new ItemInternal(VIEW_TYPE_CONTACTS_FLICKER));
        } else if (onlineContacts != null && !onlineContacts.isEmpty() && dialogsType != 7 && dialogsType != 8) {
            if (dialogsCount == 0) {
                isEmpty = true;
                if (requestPeerType != null) {
                    itemInternals.add(new ItemInternal(VIEW_TYPE_REQUIRED_EMPTY));
                } else {
                    itemInternals.add(new ItemInternal(VIEW_TYPE_EMPTY, dialogsEmptyType()));
                }
                itemInternals.add(new ItemInternal(VIEW_TYPE_SHADOW));
                itemInternals.add(new ItemInternal(VIEW_TYPE_HEADER));
            } else {
                for (int k = 0; k < array.size(); k++) {
                    itemInternals.add(new ItemInternal(VIEW_TYPE_DIALOG, array.get(k)));
                }
                itemInternals.add(new ItemInternal(VIEW_TYPE_SHADOW));
                itemInternals.add(new ItemInternal(VIEW_TYPE_HEADER));
            }
            for (int k = 0; k < onlineContacts.size(); k++) {
                itemInternals.add(new ItemInternal(VIEW_TYPE_USER, onlineContacts.get(k)));
            }
            itemInternals.add(new ItemInternal(VIEW_TYPE_LAST_EMPTY));
            stopUpdate = true;
        } else if (hasHints) {
            int count = MessagesController.getInstance(currentAccount).hintDialogs.size();
            itemInternals.add(new ItemInternal(VIEW_TYPE_RECENTLY_VIEWED));
            for (int k = 0; k < count; k++) {
                itemInternals.add(new ItemInternal(VIEW_TYPE_ME_URL, MessagesController.getInstance(currentAccount).hintDialogs.get(k)));
            }
            itemInternals.add(new ItemInternal(VIEW_TYPE_DIVIDER));
        } else if (dialogsType == DialogsActivity.DIALOGS_TYPE_IMPORT_HISTORY_GROUPS || dialogsType == DialogsActivity.DIALOGS_TYPE_IMPORT_HISTORY) {
            itemInternals.add(new ItemInternal(VIEW_TYPE_HEADER));
            itemInternals.add(new ItemInternal(VIEW_TYPE_TEXT));
        } else if (dialogsType == DialogsActivity.DIALOGS_TYPE_IMPORT_HISTORY_USERS) {
            itemInternals.add(new ItemInternal(VIEW_TYPE_HEADER));
        }

        if ((requestPeerType instanceof TLRPC.TL_requestPeerTypeBroadcast || requestPeerType instanceof TLRPC.TL_requestPeerTypeChat) && dialogsCount > 0) {
            itemInternals.add(new ItemInternal(VIEW_TYPE_TEXT));
        }

        if (!stopUpdate) {
            for (int k = 0; k < array.size(); k++) {
                if (dialogsType == DialogsActivity.DIALOGS_TYPE_ADD_USERS_TO && array.get(k) instanceof DialogsActivity.DialogsHeader) {
                    itemInternals.add(new ItemInternal(VIEW_TYPE_HEADER_2, array.get(k)));
                } else {
                    itemInternals.add(new ItemInternal(VIEW_TYPE_DIALOG, array.get(k)));
                }
            }

            if (!forceShowEmptyCell && dialogsType != 7 && dialogsType != 8 && !MessagesController.getInstance(currentAccount).isDialogsEndReached(folderId)) {
                if (dialogsCount != 0) {
                    itemInternals.add(new ItemInternal(VIEW_TYPE_FLICKER));
                }
            } else if (dialogsCount == 0) {
                isEmpty = true;
                if (requestPeerType != null) {
                    itemInternals.add(new ItemInternal(VIEW_TYPE_REQUIRED_EMPTY));
                } else {
                    itemInternals.add(new ItemInternal(VIEW_TYPE_EMPTY, dialogsEmptyType()));
                }
            } else {
                if (folderId == 0 && dialogsCount > 10 && dialogsType == DialogsActivity.DIALOGS_TYPE_DEFAULT) {
                    itemInternals.add(new ItemInternal(VIEW_TYPE_NEW_CHAT_HINT));
                }
                itemInternals.add(new ItemInternal(VIEW_TYPE_LAST_EMPTY));
            }
        }

        if (!messagesController.hiddenUndoChats.isEmpty()) {
            for (int i = 0; i < itemInternals.size(); ++i) {
                ItemInternal item = itemInternals.get(i);
                if (item.viewType == VIEW_TYPE_DIALOG && item.dialog != null && messagesController.isHiddenByUndo(item.dialog.id)) {
                    itemInternals.remove(i);
                    i--;
                }
            }
        }
    }

    public int getItemHeight(int position) {
        int cellHeight = AndroidUtilities.dp(SharedConfig.useThreeLinesLayout ? 78 : 72);
        if (itemInternals.get(position).viewType == VIEW_TYPE_DIALOG) {
            if (itemInternals.get(position).isForumCell && !collapsedView) {
                return AndroidUtilities.dp(SharedConfig.useThreeLinesLayout ? 86 : 91);
            } else {
                return cellHeight;
            }
        }
        return 0;
    }
}
