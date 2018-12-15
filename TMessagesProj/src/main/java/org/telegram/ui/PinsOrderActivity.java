/*
 * This file was created by 23rd.
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.messenger.support.widget.helper.ItemTouchHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public class PinsOrderActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private RecyclerListView listView;
    private ListAdapter listAdapter;

    private int pinnedInfoRow;
    private int pinnedStartRow;
    private int pinnedEndRow;
    private int pinnedShadowRow;
    private int rowCount;

    private ArrayList<TLRPC.TL_dialog> dialogs = new ArrayList<>();

    public class TouchHelperCallback extends ItemTouchHelper.Callback {

        @Override
        public boolean isLongPressDragEnabled() {
            return true;
        }

        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            if (viewHolder.getItemViewType() != 0) {
                return makeMovementFlags(0, 0);
            }
            return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder source, RecyclerView.ViewHolder target) {
            if (source.getItemViewType() != target.getItemViewType()) {
                return false;
            }
            listAdapter.swapElements(source.getAdapterPosition(), target.getAdapterPosition());
            return true;
        }

        @Override
        public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
                listView.cancelClickRunnables(false);
                viewHolder.itemView.setPressed(true);
            }
            super.onSelectedChanged(viewHolder, actionState);
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {

        }

        @Override
        public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            viewHolder.itemView.setPressed(false);
        }
    }

    public PinsOrderActivity() {
        super();
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("PinDialogsOrder", R.string.PinDialogsOrder));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setFocusable(true);
        listView.setTag(7);
        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        listView.setLayoutManager(layoutManager);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new TouchHelperCallback());
        itemTouchHelper.attachToRecyclerView(listView);

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        updateRows();
    }

    public void fetchPinnedDialogs() {
        dialogs.clear();
        for (int a = 0; a < MessagesController.getInstance(currentAccount).dialogsForward.size(); a++) {
            TLRPC.TL_dialog dialog = MessagesController.getInstance(currentAccount).dialogsForward.get(a);
            if (!dialog.pinned) {
                continue;
            }
            int lower_id = (int) dialog.id;
            int high_id = (int) (dialog.id >> 32);
            if (lower_id != 0 && high_id != 1) {
                if (lower_id > 0) {
                    dialogs.add(dialog);
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-lower_id);
                    if (!(chat == null || ChatObject.isNotInChat(chat) || ChatObject.isChannel(chat) && !chat.creator && (chat.admin_rights == null || !chat.admin_rights.post_messages) && !chat.megagroup)) {
                        dialogs.add(dialog);
                    }
                }
            }
        }
    }

    private void updateRows() {
        rowCount = 0;
        pinnedInfoRow = rowCount++;

        fetchPinnedDialogs();
        if (!dialogs.isEmpty()) {
            pinnedStartRow = rowCount;
            pinnedEndRow = rowCount + dialogs.size();
            rowCount += dialogs.size();
            pinnedShadowRow = rowCount++;
        } else {
            pinnedStartRow = -1;
            pinnedEndRow = -1;
            pinnedShadowRow = -1;
        }
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
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
        public long getItemId(int i) {
            if (i >= pinnedStartRow && i < pinnedEndRow) {
                ArrayList<TLRPC.TL_dialog> arrayList = dialogs;
                return arrayList.get(i - pinnedStartRow).id;
            } else if (i == pinnedInfoRow) {
                return Integer.MIN_VALUE;
            }
            return i;
        }

        public TLRPC.TL_dialog getItem(int i) {
            if (i < 0 || i >= dialogs.size()) {
                return null;
            }
            return dialogs.get(i);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    int row = position - pinnedStartRow;

                    fetchPinnedDialogs();
                    DialogCell cell = (DialogCell) holder.itemView;
                    TLRPC.TL_dialog dialog = (TLRPC.TL_dialog) getItem(row);
                    cell.setDialog(dialog, row, 0);
                    break;
                case 1:
                    if (position == pinnedInfoRow) {
                        ((TextInfoPrivacyCell) holder.itemView).setText(LocaleController.getString("PinOrderInfo", R.string.PinOrderInfo));
                    }
                    break;
                case 3:
                    if (position == pinnedShadowRow) {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == 0 || type == 2;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 0:
                    view = new DialogCell(mContext, false);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                case 3:
                    view = new ShadowSectionCell(mContext);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int i) {
            if (i >= pinnedStartRow && i < pinnedEndRow) {
                return 0;
            } else if (i == pinnedInfoRow) {
                return 1;
            } else if (i == pinnedShadowRow) {
                return 3;
            }
            return 0;
        }

        public void swapElements(int fromIndex, int toIndex) {
            ArrayList<TLRPC.TL_dialog> arrayList = dialogs;
            int index1 = fromIndex - pinnedStartRow;
            int index2 = toIndex - pinnedStartRow;

            TLRPC.TL_dialog dialog1 = arrayList.get(index1);
            TLRPC.TL_dialog dialog2 = arrayList.get(index2);

            int pinnedNum1 = dialog1.pinnedNum;
            int pinnedNum2 = dialog2.pinnedNum;

            dialog1.pinnedNum = pinnedNum2;
            dialog2.pinnedNum = pinnedNum1;

            arrayList.set(index1, dialog2);
            arrayList.set(index2, dialog1);

            MessagesStorage.getInstance(currentAccount).setDialogPinned(dialog1.id, dialog1.pinnedNum);
            MessagesStorage.getInstance(currentAccount).setDialogPinned(dialog2.id, dialog2.pinnedNum);

            dialogs = arrayList;

            MessagesController.getInstance(currentAccount).sortDialogs(null);
            notifyItemMoved(fromIndex, toIndex);
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
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

                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
        };
    }
}
