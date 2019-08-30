/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

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
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.StickerSetCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.Components.URLSpanNoUnderline;

import java.util.ArrayList;
import java.util.Locale;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class StickersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    @SuppressWarnings("FieldCanBeLocal")
    private LinearLayoutManager layoutManager;

    private boolean needReorder;
    private int currentType;

    private int suggestRow;
    private int loopRow;
    private int suggestInfoRow;
    private int featuredRow;
    private int featuredInfoRow;
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
        sendReorder();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
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
        layoutManager = new LinearLayoutManager(context);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        listView.setLayoutManager(layoutManager);
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new TouchHelperCallback());
        itemTouchHelper.attachToRecyclerView(listView);

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position >= stickersStartRow && position < stickersEndRow && getParentActivity() != null) {
                sendReorder();
                final TLRPC.TL_messages_stickerSet stickerSet = MediaDataController.getInstance(currentAccount).getStickerSets(currentType).get(position - stickersStartRow);
                ArrayList<TLRPC.Document> stickers = stickerSet.documents;
                if (stickers == null || stickers.isEmpty()) {
                    return;
                }
                showDialog(new StickersAlert(getParentActivity(), StickersActivity.this, null, stickerSet, null));
            } else if (position == featuredRow) {
                sendReorder();
                presentFragment(new FeaturedStickersActivity());
            } else if (position == archivedRow) {
                sendReorder();
                presentFragment(new ArchivedStickersActivity(currentType));
            } else if (position == masksRow) {
                presentFragment(new StickersActivity(MediaDataController.TYPE_MASK));
            } else if (position == suggestRow) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setTitle(LocaleController.getString("SuggestStickers", R.string.SuggestStickers));
                CharSequence[] items = new CharSequence[]{
                        LocaleController.getString("SuggestStickersAll", R.string.SuggestStickersAll),
                        LocaleController.getString("SuggestStickersInstalled", R.string.SuggestStickersInstalled),
                        LocaleController.getString("SuggestStickersNone", R.string.SuggestStickersNone),
                };
                builder.setItems(items, (dialog, which) -> {
                    SharedConfig.setSuggestStickers(which);
                    listAdapter.notifyItemChanged(suggestRow);
                });
                showDialog(builder.create());
            } else if (position == loopRow) {
                SharedConfig.toggleLoopStickers();
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(SharedConfig.loopStickers);
                }
            }
        });

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.stickersDidLoad) {
            if ((Integer) args[0] == currentType) {
                updateRows();
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
        TLRPC.TL_messages_reorderStickerSets req = new TLRPC.TL_messages_reorderStickerSets();
        req.masks = currentType == MediaDataController.TYPE_MASK;
        ArrayList<TLRPC.TL_messages_stickerSet> arrayList = MediaDataController.getInstance(currentAccount).getStickerSets(currentType);
        for (int a = 0; a < arrayList.size(); a++) {
            req.order.add(arrayList.get(a).set.id);
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

        });
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.stickersDidLoad, currentType);
    }

    private void updateRows() {
        rowCount = 0;
        suggestInfoRow = -1;

        if (currentType == MediaDataController.TYPE_IMAGE) {
            suggestRow = rowCount++;
            loopRow = rowCount++;
            featuredRow = rowCount++;
            featuredInfoRow = rowCount++;
            masksRow = rowCount++;
            masksInfoRow = rowCount++;
        } else {
            featuredRow = -1;
            featuredInfoRow = -1;
            masksRow = -1;
            masksInfoRow = -1;
            loopRow = -1;
        }
        if (MediaDataController.getInstance(currentAccount).getArchivedStickersCount(currentType) != 0) {
            archivedRow = rowCount++;
            archivedInfoRow = rowCount++;
        } else {
            archivedRow = -1;
            archivedInfoRow = -1;
        }

        ArrayList<TLRPC.TL_messages_stickerSet> stickerSets = MediaDataController.getInstance(currentAccount).getStickerSets(currentType);
        if (!stickerSets.isEmpty()) {
            stickersStartRow = rowCount;
            stickersEndRow = rowCount + stickerSets.size();
            rowCount += stickerSets.size();
            stickersShadowRow = rowCount++;
        } else {
            stickersStartRow = -1;
            stickersEndRow = -1;
            stickersShadowRow = -1;
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
            if (i >= stickersStartRow && i < stickersEndRow) {
                ArrayList<TLRPC.TL_messages_stickerSet> arrayList = MediaDataController.getInstance(currentAccount).getStickerSets(currentType);
                return arrayList.get(i - stickersStartRow).set.id;
            } else if (i == suggestRow || i == suggestInfoRow || i == archivedRow || i == archivedInfoRow || i == featuredRow || i == featuredInfoRow || i == masksRow || i == masksInfoRow) {
                return Integer.MIN_VALUE;
            }
            return i;
        }

        private void processSelectionOption(int which, TLRPC.TL_messages_stickerSet stickerSet) {
            if (which == 0) {
                MediaDataController.getInstance(currentAccount).removeStickersSet(getParentActivity(), stickerSet.set, !stickerSet.set.archived ? 1 : 2, StickersActivity.this, true);
            } else if (which == 1) {
                MediaDataController.getInstance(currentAccount).removeStickersSet(getParentActivity(), stickerSet.set, 0, StickersActivity.this, true);
            } else if (which == 2) {
                try {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("text/plain");
                    intent.putExtra(Intent.EXTRA_TEXT, String.format(Locale.US, "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/addstickers/%s", stickerSet.set.short_name));
                    getParentActivity().startActivityForResult(Intent.createChooser(intent, LocaleController.getString("StickersShare", R.string.StickersShare)), 500);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            } else if (which == 3) {
                try {
                    android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("label", String.format(Locale.US, "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/addstickers/%s", stickerSet.set.short_name));
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(getParentActivity(), LocaleController.getString("LinkCopied", R.string.LinkCopied), Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    ArrayList<TLRPC.TL_messages_stickerSet> arrayList = MediaDataController.getInstance(currentAccount).getStickerSets(currentType);
                    int row = position - stickersStartRow;
                    ((StickerSetCell) holder.itemView).setStickersSet(arrayList.get(row), row != arrayList.size() - 1);
                    break;
                case 1:
                    if (position == featuredInfoRow) {
                        String text = LocaleController.getString("FeaturedStickersInfo", R.string.FeaturedStickersInfo);
                        String botName = "@stickers";
                        int index = text.indexOf(botName);
                        if (index != -1) {
                            try {
                                SpannableStringBuilder stringBuilder = new SpannableStringBuilder(text);
                                URLSpanNoUnderline spanNoUnderline = new URLSpanNoUnderline("@stickers") {
                                    @Override
                                    public void onClick(View widget) {
                                        MessagesController.getInstance(currentAccount).openByUserName("stickers", StickersActivity.this, 1);
                                    }
                                };
                                stringBuilder.setSpan(spanNoUnderline, index, index + botName.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                                ((TextInfoPrivacyCell) holder.itemView).setText(stringBuilder);
                            } catch (Exception e) {
                                FileLog.e(e);
                                ((TextInfoPrivacyCell) holder.itemView).setText(text);
                            }
                        } else {
                            ((TextInfoPrivacyCell) holder.itemView).setText(text);
                        }
                    } else if (position == archivedInfoRow) {
                        if (currentType == MediaDataController.TYPE_IMAGE) {
                            ((TextInfoPrivacyCell) holder.itemView).setText(LocaleController.getString("ArchivedStickersInfo", R.string.ArchivedStickersInfo));
                        } else {
                            ((TextInfoPrivacyCell) holder.itemView).setText(LocaleController.getString("ArchivedMasksInfo", R.string.ArchivedMasksInfo));
                        }
                    } else if (position == masksInfoRow) {
                        ((TextInfoPrivacyCell) holder.itemView).setText(LocaleController.getString("MasksInfo", R.string.MasksInfo));
                    }
                    break;
                case 2:
                    if (position == featuredRow) {
                        int count = MediaDataController.getInstance(currentAccount).getUnreadStickerSets().size();
                        ((TextSettingsCell) holder.itemView).setTextAndValue(LocaleController.getString("FeaturedStickers", R.string.FeaturedStickers), count != 0 ? String.format("%d", count) : "", false);
                    } else if (position == archivedRow) {
                        if (currentType == MediaDataController.TYPE_IMAGE) {
                            ((TextSettingsCell) holder.itemView).setText(LocaleController.getString("ArchivedStickers", R.string.ArchivedStickers), false);
                        } else {
                            ((TextSettingsCell) holder.itemView).setText(LocaleController.getString("ArchivedMasks", R.string.ArchivedMasks), false);
                        }
                    } else if (position == masksRow) {
                        ((TextSettingsCell) holder.itemView).setText(LocaleController.getString("Masks", R.string.Masks), false);
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
                        ((TextSettingsCell) holder.itemView).setTextAndValue(LocaleController.getString("SuggestStickers", R.string.SuggestStickers), value, true);
                    }
                    break;
                case 3:
                    if (position == stickersShadowRow) {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == suggestInfoRow) {
                        holder.itemView.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
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
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == 0 || type == 2 || type == 4;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 0:
                    view = new StickerSetCell(mContext, 1);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    ((StickerSetCell) view).setOnOptionsClick(v -> {
                        sendReorder();
                        StickerSetCell cell = (StickerSetCell) v.getParent();
                        final TLRPC.TL_messages_stickerSet stickerSet = cell.getStickersSet();
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setTitle(stickerSet.set.title);
                        CharSequence[] items;
                        final int[] options;
                        if (currentType == MediaDataController.TYPE_IMAGE) {
                            if (stickerSet.set.official) {
                                options = new int[]{0};
                                items = new CharSequence[]{
                                        LocaleController.getString("StickersHide", R.string.StickersHide)
                                };
                            } else {
                                options = new int[]{0, 1, 2, 3};
                                items = new CharSequence[]{
                                        LocaleController.getString("StickersHide", R.string.StickersHide),
                                        LocaleController.getString("StickersRemove", R.string.StickersRemove),
                                        LocaleController.getString("StickersShare", R.string.StickersShare),
                                        LocaleController.getString("StickersCopy", R.string.StickersCopy),
                                };
                            }
                        } else {
                            if (stickerSet.set.official) {
                                options = new int[]{0};
                                items = new CharSequence[]{
                                        LocaleController.getString("StickersRemove", R.string.StickersHide)
                                };
                            } else {
                                options = new int[]{0, 1, 2, 3};
                                items = new CharSequence[]{
                                        LocaleController.getString("StickersHide", R.string.StickersHide),
                                        LocaleController.getString("StickersRemove", R.string.StickersRemove),
                                        LocaleController.getString("StickersShare", R.string.StickersShare),
                                        LocaleController.getString("StickersCopy", R.string.StickersCopy)
                                };
                            }
                        }

                        builder.setItems(items, (dialog, which) -> processSelectionOption(options[which], stickerSet));
                        showDialog(builder.create());
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
            } else if (i == featuredInfoRow || i == archivedInfoRow || i == masksInfoRow) {
                return 1;
            } else if (i == featuredRow || i == archivedRow || i == masksRow || i == suggestRow) {
                return 2;
            } else if (i == stickersShadowRow || i == suggestInfoRow) {
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
            ArrayList<TLRPC.TL_messages_stickerSet> arrayList = MediaDataController.getInstance(currentAccount).getStickerSets(currentType);
            TLRPC.TL_messages_stickerSet from = arrayList.get(fromIndex - stickersStartRow);
            arrayList.set(fromIndex - stickersStartRow, arrayList.get(toIndex - stickersStartRow));
            arrayList.set(toIndex - stickersStartRow, from);
            notifyItemMoved(fromIndex, toIndex);
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{StickerSetCell.class, TextSettingsCell.class, TextCheckCell.class}, null, null, null, Theme.key_windowBackgroundWhite),
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider),

                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack),
                new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),
                new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4),
                new ThemeDescription(listView, ThemeDescription.FLAG_LINKCOLOR, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteLinkText),

                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText),

                new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow),

                new ThemeDescription(listView, 0, new Class[]{StickerSetCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, 0, new Class[]{StickerSetCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(listView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{StickerSetCell.class}, new String[]{"optionsButton"}, null, null, null, Theme.key_stickers_menuSelector),
                new ThemeDescription(listView, 0, new Class[]{StickerSetCell.class}, new String[]{"optionsButton"}, null, null, null, Theme.key_stickers_menu),
        };
    }
}
