/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.ArchivedStickerSetCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StickersAlert;

import java.util.ArrayList;
import java.util.List;

public class ArchivedStickersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private final LongSparseArray<TLRPC.StickerSetCovered> installingStickerSets = new LongSparseArray<>();

    private ListAdapter listAdapter;
    private EmptyTextProgressView emptyView;
    private LinearLayoutManager layoutManager;
    private RecyclerListView listView;

    private ArrayList<TLRPC.StickerSetCovered> sets = new ArrayList<>();
    private boolean firstLoaded;
    private boolean endReached;

    private boolean isInTransition;
    private Runnable doOnTransitionEnd;

    private int archiveInfoRow;
    private int stickersStartRow;
    private int stickersEndRow;
    private int stickersLoadingRow;
    private int stickersShadowRow;
    private int rowCount;

    private int currentType;

    private boolean loadingStickers;

    public ArchivedStickersActivity(int type) {
        super();
        currentType = type;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        getStickers();
        updateRows();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.needAddArchivedStickers);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.stickersDidLoad);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.needAddArchivedStickers);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.stickersDidLoad);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (currentType == MediaDataController.TYPE_IMAGE) {
            actionBar.setTitle(LocaleController.getString("ArchivedStickers", R.string.ArchivedStickers));
        } else if (currentType == MediaDataController.TYPE_EMOJIPACKS) {
            actionBar.setTitle(LocaleController.getString("ArchivedEmojiPacks", R.string.ArchivedEmojiPacks));
        } else {
            actionBar.setTitle(LocaleController.getString("ArchivedMasks", R.string.ArchivedMasks));
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

        emptyView = new EmptyTextProgressView(context);
        if (currentType == MediaDataController.TYPE_IMAGE) {
            emptyView.setText(LocaleController.getString("ArchivedStickersEmpty", R.string.ArchivedStickersEmpty));
        } else {
            emptyView.setText(LocaleController.getString("ArchivedMasksEmpty", R.string.ArchivedMasksEmpty));
        }
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        if (loadingStickers) {
            emptyView.showProgress();
        } else {
            emptyView.showTextView();
        }

        listView = new RecyclerListView(context);
        listView.setFocusable(true);
        listView.setEmptyView(emptyView);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position >= stickersStartRow && position < stickersEndRow && getParentActivity() != null) {
                final TLRPC.StickerSetCovered stickerSet = sets.get(position - stickersStartRow);
                TLRPC.InputStickerSet inputStickerSet;
                if (stickerSet.set.id != 0) {
                    inputStickerSet = new TLRPC.TL_inputStickerSetID();
                    inputStickerSet.id = stickerSet.set.id;
                } else {
                    inputStickerSet = new TLRPC.TL_inputStickerSetShortName();
                    inputStickerSet.short_name = stickerSet.set.short_name;
                }
                inputStickerSet.access_hash = stickerSet.set.access_hash;
                final StickersAlert stickersAlert = new StickersAlert(getParentActivity(), ArchivedStickersActivity.this, inputStickerSet, null, null);
                stickersAlert.setInstallDelegate(new StickersAlert.StickersAlertInstallDelegate() {
                    @Override
                    public void onStickerSetInstalled() {
                        ((ArchivedStickerSetCell) view).setDrawProgress(true, true);
                        installingStickerSets.put(stickerSet.set.id, stickerSet);
                    }

                    @Override
                    public void onStickerSetUninstalled() {
                    }
                });
                showDialog(stickersAlert);
            }
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (!loadingStickers && !endReached && layoutManager.findLastVisibleItemPosition() > stickersLoadingRow - 2) {
                    getStickers();
                }
            }
        });

        return fragmentView;
    }

    private void updateRows() {
        rowCount = 0;
        if (!sets.isEmpty()) {
            archiveInfoRow = currentType == MediaDataController.TYPE_IMAGE || currentType == MediaDataController.TYPE_EMOJIPACKS ? rowCount++ : -1;
            stickersStartRow = rowCount;
            stickersEndRow = rowCount + sets.size();
            rowCount += sets.size();
            if (!endReached) {
                stickersLoadingRow = rowCount++;
                stickersShadowRow = -1;
            } else {
                stickersShadowRow = rowCount++;
                stickersLoadingRow = -1;
            }
        } else {
            archiveInfoRow = -1;
            stickersStartRow = -1;
            stickersEndRow = -1;
            stickersLoadingRow = -1;
            stickersShadowRow = -1;
        }
    }

    private void getStickers() {
        if (loadingStickers || endReached) {
            return;
        }
        loadingStickers = true;
        if (emptyView != null && !firstLoaded) {
            emptyView.showProgress();
        }
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        TLRPC.TL_messages_getArchivedStickers req = new TLRPC.TL_messages_getArchivedStickers();
        req.offset_id = sets.isEmpty() ? 0 : sets.get(sets.size() - 1).set.id;
        req.limit = 15;
        req.masks = currentType == MediaDataController.TYPE_MASK;
        req.emojis = currentType == MediaDataController.TYPE_EMOJIPACKS;
        int reqId = getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                processResponse((TLRPC.TL_messages_archivedStickers) response);
            }
        }));
        getConnectionsManager().bindRequestToGuid(reqId, classGuid);
    }

    private void processResponse(TLRPC.TL_messages_archivedStickers res) {
        if (!isInTransition) {
            sets.addAll(res.sets);
            endReached = res.sets.size() != 15;
            loadingStickers = false;
            firstLoaded = true;
            if (emptyView != null) {
                emptyView.showTextView();
            }
            updateRows();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        } else {
            doOnTransitionEnd = () -> processResponse(res);
        }
    }

    @Override
    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        isInTransition = true;
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        isInTransition = false;
        if (doOnTransitionEnd != null) {
            doOnTransitionEnd.run();
            doOnTransitionEnd = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.needAddArchivedStickers) {
            final List<TLRPC.StickerSetCovered> newSets = new ArrayList<>((List<TLRPC.StickerSetCovered>) args[0]);
            for (int i = newSets.size() - 1; i >= 0; i--) {
                for (int j = 0, size2 = sets.size(); j < size2; j++) {
                    if (sets.get(j).set.id == newSets.get(i).set.id) {
                        newSets.remove(i);
                        break;
                    }
                }
            }
            if (!newSets.isEmpty()) {
                sets.addAll(0, newSets);
                updateRows();
                if (listAdapter != null) {
                    listAdapter.notifyItemRangeInserted(stickersStartRow, newSets.size());
                }
            }
        } else if (id == NotificationCenter.stickersDidLoad) {
            if (listView != null) {
                for (int i = 0, size = listView.getChildCount(); i < size; i++) {
                    final View view = listView.getChildAt(i);
                    if (view instanceof ArchivedStickerSetCell) {
                        final ArchivedStickerSetCell cell = (ArchivedStickerSetCell) view;
                        final TLRPC.StickerSetCovered stickersSet = cell.getStickersSet();
                        if (stickersSet != null) {
                            final boolean isInstalled = MediaDataController.getInstance(currentAccount).isStickerPackInstalled(stickersSet.set.id);
                            if (isInstalled) {
                                installingStickerSets.remove(stickersSet.set.id);
                                cell.setDrawProgress(false, true);
                            }
                            cell.setChecked(isInstalled, true, false);
                        }
                    }
                }
            }
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
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (getItemViewType(position) == 0) {
                final int stickerSetPosition = position - stickersStartRow;
                ArchivedStickerSetCell cell = (ArchivedStickerSetCell) holder.itemView;
                TLRPC.StickerSetCovered stickerSet = sets.get(stickerSetPosition);
                cell.setStickersSet(stickerSet, stickerSetPosition != sets.size() - 1);
                final boolean isInstalled = MediaDataController.getInstance(currentAccount).isStickerPackInstalled(stickerSet.set.id);
                cell.setChecked(isInstalled, false, false);
                if (isInstalled) {
                    installingStickerSets.remove(stickerSet.set.id);
                    cell.setDrawProgress(false, false);
                } else {
                    cell.setDrawProgress(installingStickerSets.indexOfKey(stickerSet.set.id) >= 0, false);
                }
                cell.setOnCheckedChangeListener((c, isChecked) -> {
                    if (isChecked) {
                        c.setChecked(false, false, false);
                        if (installingStickerSets.indexOfKey(stickerSet.set.id) >= 0) {
                            return;
                        }
                        c.setDrawProgress(true, true);
                        installingStickerSets.put(stickerSet.set.id, stickerSet);
                    }
                    MediaDataController.getInstance(currentAccount).toggleStickerSet(getParentActivity(), stickerSet, !isChecked ? 1 : 2, ArchivedStickersActivity.this, false, false);
                });
            } else if (getItemViewType(position) == 2) {
                final TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                if (position == archiveInfoRow) {
                    cell.setTopPadding(17);
                    cell.setBottomPadding(10);
                    cell.setText(currentType == MediaDataController.TYPE_EMOJIPACKS ? LocaleController.getString("ArchivedEmojiInfo", R.string.ArchivedEmojiInfo) : LocaleController.getString("ArchivedStickersInfo", R.string.ArchivedStickersInfo));
                } else {
                    cell.setTopPadding(10);
                    cell.setBottomPadding(17);
                    cell.setText(null);
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 0;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 0:
                    view = new ArchivedStickerSetCell(mContext, true);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new LoadingCell(mContext);
                    view.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                case 2:
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackgroundDrawable(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int i) {
            if (i >= stickersStartRow && i < stickersEndRow) {
                return 0;
            } else if (i == stickersLoadingRow) {
                return 1;
            } else if (i == stickersShadowRow || i == archiveInfoRow) {
                return 2;
            }
            return 0;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ArchivedStickerSetCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{LoadingCell.class, TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(emptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_emptyListPlaceholder));
        themeDescriptions.add(new ThemeDescription(emptyView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{LoadingCell.class}, new String[]{"progressBar"}, null, null, null, Theme.key_progressCircle));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ArchivedStickerSetCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ArchivedStickerSetCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ArchivedStickerSetCell.class}, new String[]{"deleteButton"}, null, null, null, Theme.key_featuredStickers_removeButtonText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{ArchivedStickerSetCell.class}, new String[]{"deleteButton"}, null, null, null, Theme.key_featuredStickers_removeButtonText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{ArchivedStickerSetCell.class}, new String[]{"addButton"}, null, null, null, Theme.key_featuredStickers_buttonText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE, new Class[]{ArchivedStickerSetCell.class}, new String[]{"addButton"}, null, null, null, Theme.key_featuredStickers_addButton));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_USEBACKGROUNDDRAWABLE | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, new Class[]{ArchivedStickerSetCell.class}, new String[]{"addButton"}, null, null, null, Theme.key_featuredStickers_addButtonPressed));

        return themeDescriptions;
    }
}
