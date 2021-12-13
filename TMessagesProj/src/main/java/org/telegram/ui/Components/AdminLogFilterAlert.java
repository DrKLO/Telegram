/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.CheckBoxUserCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.ContentPreviewViewer;

import java.util.ArrayList;
import java.util.regex.Pattern;

import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class AdminLogFilterAlert extends BottomSheet {

    public interface AdminLogFilterAlertDelegate {
        void didSelectRights(TLRPC.TL_channelAdminLogEventsFilter filter, LongSparseArray<TLRPC.User> admins);
    }

    private Pattern urlPattern;
    private RecyclerListView listView;
    private ListAdapter adapter;
    private FrameLayout pickerBottomLayout;
    private Drawable shadowDrawable;
    private BottomSheet.BottomSheetCell saveButton;

    private AdminLogFilterAlertDelegate delegate;

    private int scrollOffsetY;
    private int reqId;
    private boolean ignoreLayout;

    private TLRPC.TL_channelAdminLogEventsFilter currentFilter;
    private ArrayList<TLRPC.ChannelParticipant> currentAdmins;
    private LongSparseArray<TLRPC.User> selectedAdmins;
    private boolean isMegagroup;

    private int restrictionsRow;
    private int adminsRow;
    private int membersRow;
    private int invitesRow;
    private int infoRow;
    private int deleteRow;
    private int editRow;
    private int pinnedRow;
    private int leavingRow;
    private int callsRow;
    private int allAdminsRow;

    public AdminLogFilterAlert(Context context, TLRPC.TL_channelAdminLogEventsFilter filter, LongSparseArray<TLRPC.User> admins, boolean megagroup) {
        super(context, false);
        if (filter != null) {
            currentFilter = new TLRPC.TL_channelAdminLogEventsFilter();
            currentFilter.join = filter.join;
            currentFilter.leave = filter.leave;
            currentFilter.invite = filter.invite;
            currentFilter.ban = filter.ban;
            currentFilter.unban = filter.unban;
            currentFilter.kick = filter.kick;
            currentFilter.unkick = filter.unkick;
            currentFilter.promote = filter.promote;
            currentFilter.demote = filter.demote;
            currentFilter.info = filter.info;
            currentFilter.settings = filter.settings;
            currentFilter.pinned = filter.pinned;
            currentFilter.edit = filter.edit;
            currentFilter.delete = filter.delete;
            currentFilter.group_call = filter.group_call;
            currentFilter.invites = filter.invites;
        }
        if (admins != null) {
            selectedAdmins = admins.clone();
        }
        isMegagroup = megagroup;

        int rowCount = 1;
        if (isMegagroup) {
            restrictionsRow = rowCount++;
        } else {
            restrictionsRow = -1;
        }
        adminsRow = rowCount++;
        membersRow = rowCount++;
        invitesRow = rowCount++;
        infoRow = rowCount++;
        deleteRow = rowCount++;
        editRow = rowCount++;
        if (isMegagroup) {
            pinnedRow = rowCount++;
        } else {
            pinnedRow = -1;
        }
        leavingRow = rowCount++;
        callsRow = rowCount++;
        rowCount += 1;
        allAdminsRow = rowCount;

        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));

        containerView = new FrameLayout(context) {

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.getY() < scrollOffsetY) {
                    dismiss();
                    return true;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                return !isDismissed() && super.onTouchEvent(e);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int height = MeasureSpec.getSize(heightMeasureSpec);
                if (Build.VERSION.SDK_INT >= 21) {
                    height -= AndroidUtilities.statusBarHeight;
                }
                int measuredWidth = getMeasuredWidth();
                int contentSize = AndroidUtilities.dp(48) + (isMegagroup ? 11 : 8) * AndroidUtilities.dp(48) + backgroundPaddingTop + AndroidUtilities.dp(17);
                if (currentAdmins != null) {
                    contentSize += (currentAdmins.size() + 1) * AndroidUtilities.dp(48) + AndroidUtilities.dp(20);
                }

                int padding = contentSize < (height / 5 * 3.2f) ? 0 : (height / 5 * 2);
                if (padding != 0 && contentSize < height) {
                    padding -= (height - contentSize);
                }
                if (padding == 0) {
                    padding = backgroundPaddingTop;
                }
                if (listView.getPaddingTop() != padding) {
                    ignoreLayout = true;
                    listView.setPadding(0, padding, 0, 0);
                    ignoreLayout = false;
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Math.min(contentSize, height), MeasureSpec.EXACTLY));
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                updateLayout();
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            @Override
            protected void onDraw(Canvas canvas) {
                shadowDrawable.setBounds(0, scrollOffsetY - backgroundPaddingTop, getMeasuredWidth(), getMeasuredHeight());
                shadowDrawable.draw(canvas);
            }
        };
        containerView.setWillNotDraw(false);
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

        listView = new RecyclerListView(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                boolean result = ContentPreviewViewer.getInstance().onInterceptTouchEvent(event, listView, 0, null, resourcesProvider);
                return super.onInterceptTouchEvent(event) || result;
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        listView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setEnabled(true);
        listView.setGlowColor(Theme.getColor(Theme.key_dialogScrollGlow));
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                updateLayout();
            }
        });
        listView.setOnItemClickListener((view, position) -> {
            if (view instanceof CheckBoxCell) {
                CheckBoxCell cell = (CheckBoxCell) view;
                boolean isChecked = cell.isChecked();
                cell.setChecked(!isChecked, true);
                if (position == 0) {
                    if (isChecked) {
                        currentFilter = new TLRPC.TL_channelAdminLogEventsFilter();
                        currentFilter.join = currentFilter.leave = currentFilter.invite = currentFilter.ban =
                        currentFilter.unban = currentFilter.kick = currentFilter.unkick = currentFilter.promote =
                        currentFilter.demote = currentFilter.info = currentFilter.settings = currentFilter.pinned =
                        currentFilter.edit = currentFilter.delete = currentFilter.group_call = currentFilter.invites = false;
                    } else {
                        currentFilter = null;
                    }
                    int count = listView.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View child = listView.getChildAt(a);
                        RecyclerView.ViewHolder holder = listView.findContainingViewHolder(child);
                        int pos = holder.getAdapterPosition();
                        if (holder.getItemViewType() == 0 && pos > 0 && pos < allAdminsRow - 1) {
                            ((CheckBoxCell) child).setChecked(!isChecked, true);
                        }
                    }
                } else if (position == allAdminsRow) {
                    if (isChecked) {
                        selectedAdmins = new LongSparseArray<>();
                    } else {
                        selectedAdmins = null;
                    }
                    int count = listView.getChildCount();
                    for (int a = 0; a < count; a++) {
                        View child = listView.getChildAt(a);
                        RecyclerView.ViewHolder holder = listView.findContainingViewHolder(child);
                        int pos = holder.getAdapterPosition();
                        if (holder.getItemViewType() == 2) {
                            CheckBoxUserCell userCell = (CheckBoxUserCell) child;
                            userCell.setChecked(!isChecked, true);
                        }
                    }
                } else {
                    if (currentFilter == null) {
                        currentFilter = new TLRPC.TL_channelAdminLogEventsFilter();
                        currentFilter.join = currentFilter.leave = currentFilter.invite = currentFilter.ban =
                        currentFilter.unban = currentFilter.kick = currentFilter.unkick = currentFilter.promote =
                        currentFilter.demote = currentFilter.info = currentFilter.settings = currentFilter.pinned =
                        currentFilter.edit = currentFilter.delete = currentFilter.group_call = currentFilter.invites = true;
                        RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(0);
                        if (holder != null) {
                            ((CheckBoxCell) holder.itemView).setChecked(false, true);
                        }
                    }
                    if (position == restrictionsRow) {
                        currentFilter.kick = currentFilter.ban = currentFilter.unkick = currentFilter.unban = !currentFilter.kick;
                    } else if (position == adminsRow) {
                        currentFilter.promote = currentFilter.demote = !currentFilter.demote;
                    } else if (position == membersRow) {
                        currentFilter.invite = currentFilter.join = !currentFilter.join;
                    } else if (position == infoRow) {
                        currentFilter.info = currentFilter.settings = !currentFilter.info;
                    } else if (position == deleteRow) {
                        currentFilter.delete = !currentFilter.delete;
                    } else if (position == editRow) {
                        currentFilter.edit = !currentFilter.edit;
                    } else if (position == pinnedRow) {
                        currentFilter.pinned = !currentFilter.pinned;
                    } else if (position == leavingRow) {
                        currentFilter.leave = !currentFilter.leave;
                    } else if (position == callsRow) {
                        currentFilter.group_call = !currentFilter.group_call;
                    } else if (position == invitesRow) {
                        currentFilter.invites = !currentFilter.invites;
                    }
                }
                if (currentFilter != null &&
                        !currentFilter.join && !currentFilter.leave && !currentFilter.invite && !currentFilter.ban &&
                        !currentFilter.invites && !currentFilter.unban && !currentFilter.kick && !currentFilter.unkick &&
                        !currentFilter.promote && !currentFilter.demote && !currentFilter.info && !currentFilter.settings &&
                        !currentFilter.pinned && !currentFilter.edit && !currentFilter.delete && !currentFilter.group_call) {
                    saveButton.setEnabled(false);
                    saveButton.setAlpha(0.5f);
                } else {
                    saveButton.setEnabled(true);
                    saveButton.setAlpha(1.0f);
                }
            } else if (view instanceof CheckBoxUserCell) {
                CheckBoxUserCell checkBoxUserCell = (CheckBoxUserCell) view;
                if (selectedAdmins == null) {
                    selectedAdmins = new LongSparseArray<>();
                    RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(allAdminsRow);
                    if (holder != null) {
                        ((CheckBoxCell) holder.itemView).setChecked(false, true);
                    }
                    for (int a = 0; a < currentAdmins.size(); a++) {
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(MessageObject.getPeerId(currentAdmins.get(a).peer));
                        selectedAdmins.put(user.id, user);
                    }
                }
                boolean isChecked = checkBoxUserCell.isChecked();
                TLRPC.User user = checkBoxUserCell.getCurrentUser();
                if (isChecked) {
                    selectedAdmins.remove(user.id);
                } else {
                    selectedAdmins.put(user.id, user);
                }
                checkBoxUserCell.setChecked(!isChecked, true);
            }
        });
        containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 48));

        View shadow = new View(context);
        shadow.setBackgroundResource(R.drawable.header_shadow_reverse);
        containerView.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 48));

        saveButton = new BottomSheet.BottomSheetCell(context, 1);
        saveButton.setBackgroundDrawable(Theme.getSelectorDrawable(false));
        saveButton.setTextAndIcon(LocaleController.getString("Save", R.string.Save).toUpperCase(), 0);
        saveButton.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        saveButton.setOnClickListener(v -> {
            delegate.didSelectRights(currentFilter, selectedAdmins);
            dismiss();
        });
        containerView.addView(saveButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));

        adapter.notifyDataSetChanged();
    }

    public void setCurrentAdmins(ArrayList<TLRPC.ChannelParticipant> admins) {
        currentAdmins = admins;
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    public void setAdminLogFilterAlertDelegate(AdminLogFilterAlertDelegate adminLogFilterAlertDelegate) {
        delegate = adminLogFilterAlertDelegate;
    }

    @SuppressLint("NewApi")
    private void updateLayout() {
        if (listView.getChildCount() <= 0) {
            listView.setTopGlowOffset(scrollOffsetY = listView.getPaddingTop());
            containerView.invalidate();
            return;
        }
        View child = listView.getChildAt(0);
        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
        int top = child.getTop() - AndroidUtilities.dp(8);
        int newOffset = top > 0 && holder != null && holder.getAdapterPosition() == 0 ? top : 0;
        if (scrollOffsetY != newOffset) {
            listView.setTopGlowOffset(scrollOffsetY = newOffset);
            containerView.invalidate();
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;

        public ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return (isMegagroup ? 11 : 8) + (currentAdmins != null ? 2 + currentAdmins.size() : 0);
        }

        @Override
        public int getItemViewType(int position) {
            if (position < allAdminsRow - 1 || position == allAdminsRow) {
                return 0;
            } else if (position == allAdminsRow - 1) {
                return 1;
            } else {
                return 2;
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() != 1;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            FrameLayout view = null;
            switch (viewType) {
                case 0:
                    view = new CheckBoxCell(context, 1, 21, resourcesProvider);
                    break;
                case 1:
                    ShadowSectionCell shadowSectionCell = new ShadowSectionCell(context, 18);
                    view = new FrameLayout(context);
                    view.addView(shadowSectionCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                    view.setBackgroundColor(Theme.getColor(Theme.key_dialogBackgroundGray));
                    break;
                case 2:
                    view = new CheckBoxUserCell(context, true);
                    break;
            }

            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            switch (holder.getItemViewType()) {
                case 0: {
                    CheckBoxCell cell = (CheckBoxCell) holder.itemView;
                    if (position == 0) {
                        cell.setChecked(currentFilter == null, false);
                    } else if (position == restrictionsRow) {
                        cell.setChecked(currentFilter == null || currentFilter.kick && currentFilter.ban && currentFilter.unkick && currentFilter.unban, false);
                    } else if (position == adminsRow) {
                        cell.setChecked(currentFilter == null || currentFilter.promote && currentFilter.demote, false);
                    } else if (position == membersRow) {
                        cell.setChecked(currentFilter == null || currentFilter.invite && currentFilter.join, false);
                    } else if (position == infoRow) {
                        cell.setChecked(currentFilter == null || currentFilter.info, false);
                    } else if (position == deleteRow) {
                        cell.setChecked(currentFilter == null || currentFilter.delete, false);
                    } else if (position == editRow) {
                        cell.setChecked(currentFilter == null || currentFilter.edit, false);
                    } else if (position == pinnedRow) {
                        cell.setChecked(currentFilter == null || currentFilter.pinned, false);
                    } else if (position == leavingRow) {
                        cell.setChecked(currentFilter == null || currentFilter.leave, false);
                    } else if (position == callsRow) {
                        cell.setChecked(currentFilter == null || currentFilter.group_call, false);
                    } else if (position == invitesRow) {
                        cell.setChecked(currentFilter == null || currentFilter.invites, false);
                    } else if (position == allAdminsRow) {
                        cell.setChecked(selectedAdmins == null, false);
                    }
                    break;
                }
                case 2: {
                    CheckBoxUserCell userCell = (CheckBoxUserCell) holder.itemView;
                    long userId = MessageObject.getPeerId(currentAdmins.get(position - allAdminsRow - 1).peer);
                    userCell.setChecked(selectedAdmins == null || selectedAdmins.indexOfKey(userId) >= 0, false);
                    break;
                }
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: {
                    CheckBoxCell cell = (CheckBoxCell) holder.itemView;
                    if (position == 0) {
                        cell.setText(LocaleController.getString("EventLogFilterAll", R.string.EventLogFilterAll), "", currentFilter == null, true);
                    } else if (position == restrictionsRow) {
                        cell.setText(LocaleController.getString("EventLogFilterNewRestrictions", R.string.EventLogFilterNewRestrictions), "", currentFilter == null || currentFilter.kick && currentFilter.ban && currentFilter.unkick && currentFilter.unban, true);
                    } else if (position == adminsRow) {
                        cell.setText(LocaleController.getString("EventLogFilterNewAdmins", R.string.EventLogFilterNewAdmins), "", currentFilter == null || currentFilter.promote && currentFilter.demote, true);
                    } else if (position == membersRow) {
                        cell.setText(LocaleController.getString("EventLogFilterNewMembers", R.string.EventLogFilterNewMembers), "", currentFilter == null || currentFilter.invite && currentFilter.join, true);
                    } else if (position == infoRow) {
                        if (isMegagroup) {
                            cell.setText(LocaleController.getString("EventLogFilterGroupInfo", R.string.EventLogFilterGroupInfo), "", currentFilter == null || currentFilter.info, true);
                        } else {
                            cell.setText(LocaleController.getString("EventLogFilterChannelInfo", R.string.EventLogFilterChannelInfo), "", currentFilter == null || currentFilter.info, true);
                        }
                    } else if (position == deleteRow) {
                        cell.setText(LocaleController.getString("EventLogFilterDeletedMessages", R.string.EventLogFilterDeletedMessages), "", currentFilter == null || currentFilter.delete, true);
                    } else if (position == editRow) {
                        cell.setText(LocaleController.getString("EventLogFilterEditedMessages", R.string.EventLogFilterEditedMessages), "", currentFilter == null || currentFilter.edit, true);
                    } else if (position == pinnedRow) {
                        cell.setText(LocaleController.getString("EventLogFilterPinnedMessages", R.string.EventLogFilterPinnedMessages), "", currentFilter == null || currentFilter.pinned, true);
                    } else if (position == leavingRow) {
                        cell.setText(LocaleController.getString("EventLogFilterLeavingMembers", R.string.EventLogFilterLeavingMembers), "", currentFilter == null || currentFilter.leave, callsRow != -1);
                    } else if (position == callsRow) {
                        cell.setText(LocaleController.getString("EventLogFilterCalls", R.string.EventLogFilterCalls), "", currentFilter == null || currentFilter.group_call, false);
                    } else if (position == invitesRow) {
                        cell.setText(LocaleController.getString("EventLogFilterInvites", R.string.EventLogFilterInvites), "", currentFilter == null || currentFilter.invites, true);
                    } else if (position == allAdminsRow) {
                        cell.setText(LocaleController.getString("EventLogAllAdmins", R.string.EventLogAllAdmins), "", selectedAdmins == null, true);
                    }
                    break;
                }
                case 2: {
                    CheckBoxUserCell userCell = (CheckBoxUserCell) holder.itemView;
                    long userId = MessageObject.getPeerId(currentAdmins.get(position - allAdminsRow - 1).peer);
                    userCell.setUser(MessagesController.getInstance(currentAccount).getUser(userId), selectedAdmins == null || selectedAdmins.indexOfKey(userId) >= 0, position != getItemCount() - 1);
                    break;
                }
            }
        }
    }
}
