/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Canvas;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.RadioColorCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.StickerSetCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ReorderingBulletinLayout;
import org.telegram.ui.Components.ReorderingHintDrawable;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.Components.TrendingStickersAlert;
import org.telegram.ui.Components.TrendingStickersLayout;
import org.telegram.ui.Components.URLSpanNoUnderline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;

public class StickersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int MENU_ARCHIVE = 0;
    private static final int MENU_DELETE = 1;
    private static final int MENU_SHARE = 2;

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    @SuppressWarnings("FieldCanBeLocal")
    private LinearLayoutManager layoutManager;
    private DefaultItemAnimator itemAnimator;
    private ItemTouchHelper itemTouchHelper;
    private NumberTextView selectedCountTextView;
    private TrendingStickersAlert trendingStickersAlert;

    private ActionBarMenuItem archiveMenuItem;
    private ActionBarMenuItem deleteMenuItem;
    private ActionBarMenuItem shareMenuItem;

    private int activeReorderingRequests;
    private boolean needReorder;
    private final int currentType;

    private int suggestRow;
    private int loopRow;
    private int loopInfoRow;
    private int featuredRow;
    private int stickersBotInfo;
    private int masksRow;
    private int masksInfoRow;
    private int archivedRow;
    private int archivedInfoRow;
    private int stickersStartRow;
    private int stickersEndRow;
    private int stickersShadowRow;
    private int rowCount;

    public class TouchHelperCallback extends ItemTouchHelper.Callback {

        @Override
        public boolean isLongPressDragEnabled() {
            return listAdapter.hasSelected();
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
            if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                sendReorder();
            } else {
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

    public StickersActivity(int type) {
        super();
        currentType = type;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        MediaDataController.getInstance(currentAccount).checkStickers(currentType);
        if (currentType == MediaDataController.TYPE_IMAGE) {
            MediaDataController.getInstance(currentAccount).checkFeaturedStickers();
            MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_MASK);
        }
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.stickersDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.archivedStickersCountDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.featuredStickersDidLoad);
        updateRows();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.stickersDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.archivedStickersCountDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.featuredStickersDidLoad);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        if (currentType == MediaDataController.TYPE_IMAGE) {
            actionBar.setTitle(LocaleController.getString("StickersName", R.string.StickersName));
        } else {
            actionBar.setTitle(LocaleController.getString("Masks", R.string.Masks));
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (onBackPressed()) {
                        finishFragment();
                    }
                } else if (id == MENU_ARCHIVE || id == MENU_DELETE || id == MENU_SHARE) {
                    if (!needReorder) {
                        if (activeReorderingRequests == 0) {
                            listAdapter.processSelectionMenu(id);
                        }
                    } else {
                        sendReorder();
                    }
                }
            }
        });


        final ActionBarMenu actionMode = actionBar.createActionMode();
        selectedCountTextView = new NumberTextView(actionMode.getContext());
        selectedCountTextView.setTextSize(18);
        selectedCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        actionMode.addView(selectedCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
        selectedCountTextView.setOnTouchListener((v, event) -> true);

        shareMenuItem = actionMode.addItemWithWidth(MENU_SHARE, R.drawable.msg_share, AndroidUtilities.dp(54));
        archiveMenuItem = actionMode.addItemWithWidth(MENU_ARCHIVE, R.drawable.msg_archive, AndroidUtilities.dp(54));
        deleteMenuItem = actionMode.addItemWithWidth(MENU_DELETE, R.drawable.msg_delete, AndroidUtilities.dp(54));


        listAdapter = new ListAdapter(context, MediaDataController.getInstance(currentAccount).getStickerSets(currentType));

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setFocusable(true);
        listView.setTag(7);
        layoutManager = new LinearLayoutManager(context) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }

            @Override
            protected void calculateExtraLayoutSpace(@NonNull RecyclerView.State state, @NonNull int[] extraLayoutSpace) {
                extraLayoutSpace[1] = listView.getHeight();
            }
        };
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        listView.setLayoutManager(layoutManager);
        itemTouchHelper = new ItemTouchHelper(new TouchHelperCallback());
        itemTouchHelper.attachToRecyclerView(listView);
        itemAnimator = (DefaultItemAnimator) listView.getItemAnimator();
        itemAnimator.setSupportsChangeAnimations(false);

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position >= stickersStartRow && position < stickersEndRow && getParentActivity() != null) {
                if (!listAdapter.hasSelected()) {
                    final TLRPC.TL_messages_stickerSet stickerSet = listAdapter.stickerSets.get(position - stickersStartRow);
                    ArrayList<TLRPC.Document> stickers = stickerSet.documents;
                    if (stickers == null || stickers.isEmpty()) {
                        return;
                    }
                    showDialog(new StickersAlert(getParentActivity(), StickersActivity.this, null, stickerSet, null));
                } else {
                    listAdapter.toggleSelected(position);
                }
            } else if (position == featuredRow) {
                final TrendingStickersLayout.Delegate trendingDelegate = new TrendingStickersLayout.Delegate() {
                    @Override
                    public void onStickerSetAdd(TLRPC.StickerSetCovered stickerSet, boolean primary) {
                        MediaDataController.getInstance(currentAccount).toggleStickerSet(getParentActivity(), stickerSet, 2, StickersActivity.this, false, false);
                    }

                    @Override
                    public void onStickerSetRemove(TLRPC.StickerSetCovered stickerSet) {
                        MediaDataController.getInstance(currentAccount).toggleStickerSet(getParentActivity(), stickerSet, 0, StickersActivity.this, false, false);
                    }
                };
                trendingStickersAlert = new TrendingStickersAlert(context, this, new TrendingStickersLayout(context, trendingDelegate), null);
                trendingStickersAlert.show();
            } else if (position == archivedRow) {
                presentFragment(new ArchivedStickersActivity(currentType));
            } else if (position == masksRow) {
                presentFragment(new StickersActivity(MediaDataController.TYPE_MASK));
            } else if (position == suggestRow) {
                if (getParentActivity() == null) {
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("SuggestStickers", R.string.SuggestStickers));
                String[] items = new String[]{
                        LocaleController.getString("SuggestStickersAll", R.string.SuggestStickersAll),
                        LocaleController.getString("SuggestStickersInstalled", R.string.SuggestStickersInstalled),
                        LocaleController.getString("SuggestStickersNone", R.string.SuggestStickersNone),
                };

                final LinearLayout linearLayout = new LinearLayout(getParentActivity());
                linearLayout.setOrientation(LinearLayout.VERTICAL);
                builder.setView(linearLayout);

                for (int a = 0; a < items.length; a++) {
                    RadioColorCell cell = new RadioColorCell(getParentActivity());
                    cell.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
                    cell.setTag(a);
                    cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
                    cell.setTextAndValue(items[a], SharedConfig.suggestStickers == a);
                    linearLayout.addView(cell);
                    cell.setOnClickListener(v -> {
                        Integer which = (Integer) v.getTag();
                        SharedConfig.setSuggestStickers(which);
                        listAdapter.notifyItemChanged(suggestRow);
                        builder.getDismissRunnable().run();
                    });
                }
                showDialog(builder.create());
            } else if (position == loopRow) {
                SharedConfig.toggleLoopStickers();
                listAdapter.notifyItemChanged(loopRow, ListAdapter.UPDATE_LOOP_STICKERS);
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (!listAdapter.hasSelected() && position >= stickersStartRow && position < stickersEndRow) {
                listAdapter.toggleSelected(position);
                return true;
            } else {
                return false;
            }
        });

        return fragmentView;
    }



    @Override
    public boolean onBackPressed() {
        if (listAdapter.hasSelected()) {
            listAdapter.clearSelected();
            return false;
        }
        return super.onBackPressed();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.stickersDidLoad) {
            final int type = (int) args[0];
            if (type == currentType) {
                updateRows();
            } else if (currentType == MediaDataController.TYPE_IMAGE && type == MediaDataController.TYPE_MASK) {
                listAdapter.notifyItemChanged(masksRow);
            }
        } else if (id == NotificationCenter.featuredStickersDidLoad) {
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(0);
            }
        } else if (id == NotificationCenter.archivedStickersCountDidLoad) {
            if ((Integer) args[0] == currentType) {
                updateRows();
            }
        }
    }

    private void sendReorder() {
        if (!needReorder) {
            return;
        }
        MediaDataController.getInstance(currentAccount).calcNewHash(currentType);
        needReorder = false;
        activeReorderingRequests++;
        TLRPC.TL_messages_reorderStickerSets req = new TLRPC.TL_messages_reorderStickerSets();
        req.masks = currentType == MediaDataController.TYPE_MASK;
        for (int a = 0; a < listAdapter.stickerSets.size(); a++) {
            req.order.add(listAdapter.stickerSets.get(a).set.id);
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> activeReorderingRequests--));
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.stickersDidLoad, currentType);
    }

    private void updateRows() {
        final MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
        final List<TLRPC.TL_messages_stickerSet> newList = mediaDataController.getStickerSets(currentType);

        DiffUtil.DiffResult diffResult = null;

        if (listAdapter != null) {
            if (!isPaused) {
                diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {

                    final List<TLRPC.TL_messages_stickerSet> oldList = listAdapter.stickerSets;

                    @Override
                    public int getOldListSize() {
                        return oldList.size();
                    }

                    @Override
                    public int getNewListSize() {
                        return newList.size();
                    }

                    @Override
                    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                        return oldList.get(oldItemPosition).set.id == newList.get(newItemPosition).set.id;
                    }

                    @Override
                    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                        final TLRPC.StickerSet oldSet = oldList.get(oldItemPosition).set;
                        final TLRPC.StickerSet newSet = newList.get(newItemPosition).set;
                        return TextUtils.equals(oldSet.title, newSet.title) && oldSet.count == newSet.count;
                    }
                });
            }
            listAdapter.setStickerSets(newList);
        }

        rowCount = 0;

        if (currentType == MediaDataController.TYPE_IMAGE) {
            suggestRow = rowCount++;
            loopRow = rowCount++;
            loopInfoRow = rowCount++;
            featuredRow = rowCount++;
        } else {
            suggestRow = -1;
            loopRow = -1;
            loopInfoRow = -1;
            featuredRow = -1;
        }

        if (mediaDataController.getArchivedStickersCount(currentType) != 0) {
            final boolean inserted = archivedRow == -1;

            archivedRow = rowCount++;
            archivedInfoRow = currentType == MediaDataController.TYPE_MASK ? rowCount++ : -1;

            if (listAdapter != null && inserted) {
                listAdapter.notifyItemRangeInserted(archivedRow, archivedInfoRow != -1 ? 2 : 1);
            }
        } else {
            final int oldArchivedRow = archivedRow;
            final int oldArchivedInfoRow = archivedInfoRow;

            archivedRow = -1;
            archivedInfoRow = -1;

            if (listAdapter != null && oldArchivedRow != -1) {
                listAdapter.notifyItemRangeRemoved(oldArchivedRow, oldArchivedInfoRow != -1 ? 2 : 1);
            }
        }

        if (currentType == MediaDataController.TYPE_IMAGE) {
            masksRow = rowCount++;
            stickersBotInfo = rowCount++;
        } else {
            masksRow = -1;
            stickersBotInfo = -1;
        }

        final int stickerSetsCount = newList.size();
        if (stickerSetsCount > 0) {
            stickersStartRow = rowCount;
            rowCount += stickerSetsCount;
            stickersEndRow = rowCount;

            if (currentType != MediaDataController.TYPE_MASK) {
                stickersShadowRow = rowCount++;
                masksInfoRow = -1;
            } else {
                masksInfoRow = rowCount++;
                stickersShadowRow = -1;
            }
        } else {
            stickersStartRow = -1;
            stickersEndRow = -1;
            stickersShadowRow = -1;
            masksInfoRow = -1;
        }

        if (listAdapter != null) {
            if (diffResult != null) {
                final int startRow = stickersStartRow >= 0 ? stickersStartRow : rowCount;
                listAdapter.notifyItemRangeChanged(0, startRow);
                diffResult.dispatchUpdatesTo(new ListUpdateCallback() {
                    @Override
                    public void onInserted(int position, int count) {
                        listAdapter.notifyItemRangeInserted(startRow + position, count);
                    }

                    @Override
                    public void onRemoved(int position, int count) {
                        listAdapter.notifyItemRangeRemoved(startRow + position, count);
                    }

                    @Override
                    public void onMoved(int fromPosition, int toPosition) {
                    }

                    @Override
                    public void onChanged(int position, int count, @Nullable Object payload) {
                        listAdapter.notifyItemRangeChanged(startRow + position, count);
                    }
                });
            }
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

        public static final int UPDATE_LOOP_STICKERS = 0;
        public static final int UPDATE_SELECTION = 1;
        public static final int UPDATE_REORDERABLE = 2;
        public static final int UPDATE_DIVIDER = 3;

        private final LongSparseArray<Boolean> selectedItems = new LongSparseArray<>();
        private final List<TLRPC.TL_messages_stickerSet> stickerSets = new ArrayList<>();

        private Context mContext;

        public ListAdapter(Context context, List<TLRPC.TL_messages_stickerSet> stickerSets) {
            mContext = context;
            this.stickerSets.addAll(stickerSets);
        }

        public void setStickerSets(List<TLRPC.TL_messages_stickerSet> stickerSets) {
            this.stickerSets.clear();
            this.stickerSets.addAll(stickerSets);
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public long getItemId(int i) {
            if (i >= stickersStartRow && i < stickersEndRow) {
                return stickerSets.get(i - stickersStartRow).set.id;
            } else if (i == suggestRow || i == loopInfoRow || i == archivedRow || i == archivedInfoRow || i == featuredRow || i == stickersBotInfo || i == masksRow) {
                return Integer.MIN_VALUE;
            }
            return i;
        }

        private void processSelectionMenu(int which) {
            if (which == MENU_SHARE) {
                StringBuilder stringBuilder = new StringBuilder();
                for (int i = 0, size = stickerSets.size(); i < size; i++) {
                    final TLRPC.TL_messages_stickerSet stickerSet = stickerSets.get(i);
                    if (selectedItems.get(stickerSet.set.id, false)) {
                        if (stringBuilder.length() != 0) {
                            stringBuilder.append("\n");
                        }
                        stringBuilder.append(getLinkForSet(stickerSet));
                    }
                }
                String link = stringBuilder.toString();
                ShareAlert shareAlert = ShareAlert.createShareAlert(fragmentView.getContext(), null, link, false, link, false);
                shareAlert.setDelegate(new ShareAlert.ShareAlertDelegate() {
                    @Override
                    public void didShare() {
                        clearSelected();
                    }

                    @Override
                    public boolean didCopy() {
                        clearSelected();
                        return true;
                    }
                });
                shareAlert.show();
            } else if (which == MENU_ARCHIVE || which == MENU_DELETE) {
                final ArrayList<TLRPC.StickerSet> stickerSetList = new ArrayList<>(selectedItems.size());

                for (int i = 0, size = stickerSets.size(); i < size; i++) {
                    final TLRPC.StickerSet stickerSet = stickerSets.get(i).set;
                    if (selectedItems.get(stickerSet.id, false)) {
                        stickerSetList.add(stickerSet);
                    }
                }

                final int count = stickerSetList.size();

                switch (count) {
                    case 0:
                        break;
                    case 1:
                        for (int i = 0, size = stickerSets.size(); i < size; i++) {
                            final TLRPC.TL_messages_stickerSet stickerSet = stickerSets.get(i);
                            if (selectedItems.get(stickerSet.set.id, false)) {
                                processSelectionOption(which, stickerSet);
                                break;
                            }
                        }
                        listAdapter.clearSelected();
                        break;
                    default:
                        final AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());

                        final String buttonText;
                        if (which == MENU_DELETE) {
                            builder.setTitle(LocaleController.formatString("DeleteStickerSetsAlertTitle", R.string.DeleteStickerSetsAlertTitle, LocaleController.formatPluralString("StickerSets", count)));
                            builder.setMessage(LocaleController.formatString("DeleteStickersAlertMessage", R.string.DeleteStickersAlertMessage, count));
                            buttonText = LocaleController.getString("Delete", R.string.Delete);
                        } else {
                            builder.setTitle(LocaleController.formatString("ArchiveStickerSetsAlertTitle", R.string.ArchiveStickerSetsAlertTitle, LocaleController.formatPluralString("StickerSets", count)));
                            builder.setMessage(LocaleController.formatString("ArchiveStickersAlertMessage", R.string.ArchiveStickersAlertMessage, count));
                            buttonText = LocaleController.getString("Archive", R.string.Archive);
                        }
                        builder.setPositiveButton(buttonText, (dialog, which1) -> {
                            listAdapter.clearSelected();
                            MediaDataController.getInstance(currentAccount).toggleStickerSets(stickerSetList, currentType, which == MENU_DELETE ? 0 : 1, StickersActivity.this, true);
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);

                        final AlertDialog dialog = builder.create();
                        showDialog(dialog);
                        if (which == MENU_DELETE) {
                            final TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                            if (button != null) {
                                button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                            }
                        }
                        break;
                }
            }
        }

        private void processSelectionOption(int which, TLRPC.TL_messages_stickerSet stickerSet) {
            if (which == MENU_ARCHIVE) {
                MediaDataController.getInstance(currentAccount).toggleStickerSet(getParentActivity(), stickerSet, !stickerSet.set.archived ? 1 : 2, StickersActivity.this, true, true);
            } else if (which == MENU_DELETE) {
                MediaDataController.getInstance(currentAccount).toggleStickerSet(getParentActivity(), stickerSet, 0, StickersActivity.this, true, true);
            } else if (which == 2) {
                try {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TEXT, getLinkForSet(stickerSet));
                    getParentActivity().startActivityForResult(Intent.createChooser(intent, LocaleController.getString("StickersShare", R.string.StickersShare)), 500);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else if (which == 3) {
                try {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("label", String.format(Locale.US, "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/addstickers/%s", stickerSet.set.short_name));
                    clipboard.setPrimaryClip(clip);
                    BulletinFactory.createCopyLinkBulletin(StickersActivity.this).show();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else if (which == 4) {
                final int index = stickerSets.indexOf(stickerSet);
                if (index >= 0) {
                    listAdapter.toggleSelected(stickersStartRow + index);
                }
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    int row = position - stickersStartRow;
                    final StickerSetCell stickerSetCell = (StickerSetCell) holder.itemView;
                    stickerSetCell.setStickersSet(stickerSets.get(row), row != stickerSets.size() - 1);
                    stickerSetCell.setChecked(selectedItems.get(getItemId(position), false), false);
                    stickerSetCell.setReorderable(hasSelected(), false);
                    break;
                case 1:
                    final TextInfoPrivacyCell infoPrivacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == stickersBotInfo) {
                        infoPrivacyCell.setText(addStickersBotSpan(LocaleController.getString("StickersBotInfo", R.string.StickersBotInfo)));
                    } else if (position == archivedInfoRow) {
                        if (currentType == MediaDataController.TYPE_IMAGE) {
                            infoPrivacyCell.setText(LocaleController.getString("ArchivedStickersInfo", R.string.ArchivedStickersInfo));
                        } else {
                            infoPrivacyCell.setText(LocaleController.getString("ArchivedMasksInfo", R.string.ArchivedMasksInfo));
                        }
                    } else if (position == loopInfoRow) {
                        infoPrivacyCell.setText(LocaleController.getString("LoopAnimatedStickersInfo", R.string.LoopAnimatedStickersInfo));
                    } else if (position == masksInfoRow) {
                        infoPrivacyCell.setText(LocaleController.getString("MasksInfo", R.string.MasksInfo));
                    }
                    break;
                case 2:
                    final TextSettingsCell settingsCell = (TextSettingsCell) holder.itemView;
                    if (position == featuredRow) {
                        final int count = MediaDataController.getInstance(currentAccount).getFeaturedStickerSets().size();
                        settingsCell.setTextAndValue(LocaleController.getString("FeaturedStickers", R.string.FeaturedStickers), count > 0 ? Integer.toString(count) : "", true);
                    } else if (position == archivedRow) {
                        final int count = MediaDataController.getInstance(currentAccount).getArchivedStickersCount(currentType);
                        final String value = count > 0 ? Integer.toString(count) : "";
                        if (currentType == MediaDataController.TYPE_IMAGE) {
                            settingsCell.setTextAndValue(LocaleController.getString("ArchivedStickers", R.string.ArchivedStickers), value, true);
                        } else {
                            settingsCell.setTextAndValue(LocaleController.getString("ArchivedMasks", R.string.ArchivedMasks), value, true);
                        }
                    } else if (position == masksRow) {
                        final int type = MediaDataController.TYPE_MASK;
                        final MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
                        final int count = mediaDataController.getStickerSets(type).size() + mediaDataController.getArchivedStickersCount(type);
                        settingsCell.setTextAndValue(LocaleController.getString("Masks", R.string.Masks), count > 0 ? Integer.toString(count) : "", false);
                    } else if (position == suggestRow) {
                        String value;
                        switch (SharedConfig.suggestStickers) {
                            case 0:
                                value = LocaleController.getString("SuggestStickersAll", R.string.SuggestStickersAll);
                                break;
                            case 1:
                                value = LocaleController.getString("SuggestStickersInstalled", R.string.SuggestStickersInstalled);
                                break;
                            case 2:
                            default:
                                value = LocaleController.getString("SuggestStickersNone", R.string.SuggestStickersNone);
                                break;
                        }
                        settingsCell.setTextAndValue(LocaleController.getString("SuggestStickers", R.string.SuggestStickers), value, true);
                    }
                    break;
                case 3:
                    if (position == stickersShadowRow) {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                case 4:
                    if (position == loopRow) {
                        TextCheckCell cell = (TextCheckCell) holder.itemView;
                        cell.setTextAndCheck(LocaleController.getString("LoopAnimatedStickers", R.string.LoopAnimatedStickers), SharedConfig.loopStickers, true);
                    }
                    break;
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List payloads) {
            if (payloads.isEmpty()) {
                onBindViewHolder(holder, position);
            } else {
                switch (holder.getItemViewType()) {
                    case 0:
                        if (position >= stickersStartRow && position < stickersEndRow) {
                            final StickerSetCell stickerSetCell = (StickerSetCell) holder.itemView;
                            if (payloads.contains(UPDATE_SELECTION)) {
                                stickerSetCell.setChecked(selectedItems.get(getItemId(position), false));
                            }
                            if (payloads.contains(UPDATE_REORDERABLE)) {
                                stickerSetCell.setReorderable(hasSelected());
                            }
                            if (payloads.contains(UPDATE_DIVIDER)) {
                                stickerSetCell.setNeedDivider(position - stickersStartRow != stickerSets.size() - 1);
                            }
                        }
                        break;
                    case 4:
                        if (payloads.contains(UPDATE_LOOP_STICKERS) && position == loopRow) {
                            ((TextCheckCell) holder.itemView).setChecked(SharedConfig.loopStickers);
                        }
                        break;
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == 0 || type == 2 || type == 4;
        }

        @Override
        @SuppressLint("ClickableViewAccessibility")
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new StickerSetCell(mContext, 1);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    final StickerSetCell stickerSetCell = (StickerSetCell) view;
                    stickerSetCell.setOnReorderButtonTouchListener((v, event) -> {
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            itemTouchHelper.startDrag(listView.getChildViewHolder(stickerSetCell));
                        }
                        return false;
                    });
                    stickerSetCell.setOnOptionsClick(v -> {
                        StickerSetCell cell = (StickerSetCell) v.getParent();
                        final TLRPC.TL_messages_stickerSet stickerSet = cell.getStickersSet();
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(stickerSet.set.title);
                        final int[] options;
                        final CharSequence[] items;
                        final int[] icons;
                        if (stickerSet.set.official) {
                            options = new int[]{MENU_ARCHIVE, 4};
                            items = new CharSequence[]{
                                    LocaleController.getString("StickersHide", R.string.StickersHide),
                                    LocaleController.getString("StickersReorder", R.string.StickersReorder)
                            };
                            icons = new int[]{R.drawable.msg_archive, R.drawable.msg_reorder};
                        } else {
                            options = new int[]{MENU_ARCHIVE, 3, 4, 2, MENU_DELETE};
                            items = new CharSequence[]{
                                    LocaleController.getString("StickersHide", R.string.StickersHide),
                                    LocaleController.getString("StickersCopy", R.string.StickersCopy),
                                    LocaleController.getString("StickersReorder", R.string.StickersReorder),
                                    LocaleController.getString("StickersShare", R.string.StickersShare),
                                    LocaleController.getString("StickersRemove", R.string.StickersRemove),
                            };
                            icons = new int[]{
                                    R.drawable.msg_archive,
                                    R.drawable.msg_link,
                                    R.drawable.msg_reorder,
                                    R.drawable.msg_share,
                                    R.drawable.msg_delete
                            };
                        }
                        builder.setItems(items, icons, (dialog, which) -> processSelectionOption(options[which], stickerSet));

                        final AlertDialog dialog = builder.create();
                        showDialog(dialog);

                        if (options[options.length - 1] == MENU_DELETE) {
                            dialog.setItemColor(items.length - 1, Theme.getColor(Theme.key_dialogTextRed2), Theme.getColor(Theme.key_dialogRedIcon));
                        }
                    });
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
                    view = new ShadowSectionCell(mContext);
                    break;
                case 4:
                default:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int i) {
            if (i >= stickersStartRow && i < stickersEndRow) {
                return 0;
            } else if (i == stickersBotInfo || i == archivedInfoRow || i == loopInfoRow || i == masksInfoRow) {
                return 1;
            } else if (i == featuredRow || i == archivedRow || i == masksRow || i == suggestRow) {
                return 2;
            } else if (i == stickersShadowRow) {
                return 3;
            } else if (i == loopRow) {
                return 4;
            }
            return 0;
        }

        public void swapElements(int fromIndex, int toIndex) {
            if (fromIndex != toIndex) {
                needReorder = true;
            }

            final MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);

            final int index1 = fromIndex - stickersStartRow;
            final int index2 = toIndex - stickersStartRow;

            swapListElements(stickerSets, index1, index2);
            swapListElements(mediaDataController.getStickerSets(currentType), index1, index2);

            notifyItemMoved(fromIndex, toIndex);

            if (fromIndex == stickersEndRow - 1 || toIndex == stickersEndRow - 1) {
                notifyItemRangeChanged(fromIndex, UPDATE_DIVIDER);
                notifyItemRangeChanged(toIndex, UPDATE_DIVIDER);
            }
        }

        private void swapListElements(List<TLRPC.TL_messages_stickerSet> list, int index1, int index2) {
            final TLRPC.TL_messages_stickerSet set1 = list.get(index1);
            list.set(index1, list.get(index2));
            list.set(index2, set1);
        }

        public void toggleSelected(int position) {
            final long id = getItemId(position);
            selectedItems.put(id, !selectedItems.get(id, false));
            notifyItemChanged(position, UPDATE_SELECTION);
            checkActionMode();
        }

        public void clearSelected() {
            selectedItems.clear();
            notifyStickersItemsChanged(UPDATE_SELECTION);
            checkActionMode();
        }

        public boolean hasSelected() {
            return selectedItems.indexOfValue(true) != -1;
        }

        public int getSelectedCount() {
            int count = 0;
            for (int i = 0, size = selectedItems.size(); i < size; i++) {
                if (selectedItems.valueAt(i)) {
                    count++;
                }
            }
            return count;
        }

        private void checkActionMode() {
            final int selectedCount = listAdapter.getSelectedCount();
            final boolean actionModeShowed = actionBar.isActionModeShowed();
            if (selectedCount > 0) {
                checkActionModeIcons();
                selectedCountTextView.setNumber(selectedCount, actionModeShowed);
                if (!actionModeShowed) {
                    actionBar.showActionMode();
                    notifyStickersItemsChanged(UPDATE_REORDERABLE);
                    if (!SharedConfig.stickersReorderingHintUsed) {
                        SharedConfig.setStickersReorderingHintUsed(true);
                        final String stickersReorderHint = LocaleController.getString("StickersReorderHint", R.string.StickersReorderHint);
                        Bulletin.make(parentLayout, new ReorderingBulletinLayout(mContext, stickersReorderHint, null), ReorderingHintDrawable.DURATION * 2 + 250).show();
                    }
                }
            } else if (actionModeShowed) {
                actionBar.hideActionMode();
                notifyStickersItemsChanged(UPDATE_REORDERABLE);
            }
        }

        private void checkActionModeIcons() {
            if (hasSelected()) {
                boolean canDelete = true;
                for (int i = 0, size = stickerSets.size(); i < size; i++) {
                    if (selectedItems.get(stickerSets.get(i).set.id, false)) {
                        if (stickerSets.get(i).set.official) {
                            canDelete = false;
                            break;
                        }
                    }
                }
                final int visibility = canDelete ? View.VISIBLE : View.GONE;
                if (deleteMenuItem.getVisibility() != visibility) {
                    deleteMenuItem.setVisibility(visibility);
                }
            }
        }

        private void notifyStickersItemsChanged(Object payload) {
            notifyItemRangeChanged(stickersStartRow, stickersEndRow - stickersStartRow, payload);
        }

        private CharSequence addStickersBotSpan(String text) {
            final String botName = "@stickers";
            final int index = text.indexOf(botName);
            if (index != -1) {
                try {
                    SpannableStringBuilder stringBuilder = new SpannableStringBuilder(text);
                    URLSpanNoUnderline urlSpan = new URLSpanNoUnderline("@stickers") {
                        @Override
                        public void onClick(View widget) {
                            MessagesController.getInstance(currentAccount).openByUserName("stickers", StickersActivity.this, 3);
                        }
                    };
                    stringBuilder.setSpan(urlSpan, index, index + botName.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                    return stringBuilder;
                } catch (Exception e) {
                    FileLog.e(e);
                    return text;
                }
            } else {
                return text;
            }
        }
    }

    private String getLinkForSet(TLRPC.TL_messages_stickerSet stickerSet) {
        return String.format(Locale.US, "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/addstickers/%s", stickerSet.set.short_name);
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{StickerSetCell.class, TextSettingsCell.class, TextCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_BACKGROUND, null, null, null, null, Theme.key_actionBarActionModeDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_TOPBACKGROUND, null, null, null, null, Theme.key_actionBarActionModeDefaultTop));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultSelector));
        themeDescriptions.add(new ThemeDescription(selectedCountTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultIcon));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LINKCOLOR, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteLinkText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSetCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSetCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{StickerSetCell.class}, new String[]{"optionsButton"}, null, null, null, Theme.key_stickers_menuSelector));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSetCell.class}, new String[]{"optionsButton"}, null, null, null, Theme.key_stickers_menu));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{StickerSetCell.class}, new String[]{"reorderButton"}, null, null, null, Theme.key_stickers_menu));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOX, new Class[]{StickerSetCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{StickerSetCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));

        if (trendingStickersAlert != null) {
            themeDescriptions.addAll(trendingStickersAlert.getThemeDescriptions());
        }

        return themeDescriptions;
    }
}
