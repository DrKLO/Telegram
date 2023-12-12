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
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.StickerSetCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StickerEmptyView;
import org.telegram.ui.Components.StickersAlert;
import org.telegram.ui.Components.URLSpanNoUnderline;
import org.telegram.ui.Components.VerticalPositionAutoAnimator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class GroupStickersActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private FrameLayout emptyFrameView;
    private StickerEmptyView emptyView;
    private FlickerLoadingView loadingView;

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private SearchAdapter searchAdapter;
    private LinearLayoutManager layoutManager;

    private int selectedStickerSetIndex = -1;

    private TLRPC.TL_messages_stickerSet selectedStickerSet;
    private boolean removeStickerSet;

    private TLRPC.ChatFull info;
    private long chatId;

    private int infoRow;
    private int headerRow;
    private int stickersStartRow;
    private int stickersEndRow;
    private int rowCount;

    private ActionBarMenuItem searchItem;
    private boolean searching;

    public GroupStickersActivity(long id) {
        super();
        chatId = id;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_IMAGE);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.stickersDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.groupStickersDidLoad);
        updateRows();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.stickersDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatInfoDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.groupStickersDidLoad);

        if (selectedStickerSet != null || removeStickerSet) {
            saveStickerSet();
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("GroupStickers", R.string.GroupStickers));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        searchItem = menu.addItem(0, R.drawable.ic_ab_search);
        searchItem.setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {}

            @Override
            public void onSearchCollapse() {
                if (searching) {
                    searchAdapter.onSearchStickers(null);
                    searching = false;
                    listView.setAdapter(listAdapter);
                }
            }

            @Override
            public void onTextChanged(EditText editText) {
                String text = editText.getText().toString();
                searchAdapter.onSearchStickers(text);

                boolean newSearching = !TextUtils.isEmpty(text);
                if (newSearching != searching) {
                    searching = newSearching;

                    if (listView != null) {
                        listView.setAdapter(searching ? searchAdapter : listAdapter);
                    }
                }
            }
        });
        searchItem.setSearchFieldHint(LocaleController.getString(R.string.Search));

        listAdapter = new ListAdapter(context);
        searchAdapter = new SearchAdapter(context);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        DefaultItemAnimator defaultItemAnimator = new DefaultItemAnimator();
        defaultItemAnimator.setSupportsChangeAnimations(true);
        listView.setItemAnimator(defaultItemAnimator);
        layoutManager = new LinearLayoutManager(context);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        listView.setLayoutManager(layoutManager);

        emptyFrameView = new FrameLayout(context);
        emptyFrameView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        loadingView = new FlickerLoadingView(context, getResourceProvider());
        loadingView.setViewType(FlickerLoadingView.STICKERS_TYPE);
        loadingView.setIsSingleCell(true);
        loadingView.setItemsCount((int) Math.ceil(AndroidUtilities.displaySize.y / AndroidUtilities.dpf2(58)));
        emptyFrameView.addView(loadingView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new StickerEmptyView(context, loadingView, StickerEmptyView.STICKER_TYPE_SEARCH);
        VerticalPositionAutoAnimator.attach(emptyView);

        emptyFrameView.addView(emptyView);
        frameLayout.addView(emptyFrameView);
        emptyFrameView.setVisibility(View.GONE);
        listView.setEmptyView(emptyFrameView);

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (getParentActivity() == null) {
                return;
            }
            if (searching) {
                if (position > searchAdapter.searchEntries.size()) {
                    onStickerSetClicked(view, searchAdapter.localSearchEntries.get(position - searchAdapter.searchEntries.size() - 1), false);
                } else if (position != searchAdapter.searchEntries.size()) {
                    onStickerSetClicked(view, searchAdapter.searchEntries.get(position), true);
                }
                return;
            }

            if (position >= stickersStartRow && position < stickersEndRow) {
                TLRPC.TL_messages_stickerSet stickerSet = MediaDataController.getInstance(currentAccount).getStickerSets(MediaDataController.TYPE_IMAGE).get(position - stickersStartRow);
                onStickerSetClicked(view, stickerSet, false);
            }
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                }
            }
        });

        return fragmentView;
    }

    private void onStickerSetClicked(View view, TLRPC.TL_messages_stickerSet stickerSet, boolean remote) {
        TLRPC.InputStickerSet inputStickerSet = null;
        if (remote) {
            TLRPC.TL_inputStickerSetShortName inputStickerSetShortName = new TLRPC.TL_inputStickerSetShortName();
            inputStickerSetShortName.short_name = stickerSet.set.short_name;
            inputStickerSet = inputStickerSetShortName;
        }
        StickersAlert stickersAlert = new StickersAlert(getParentActivity(), GroupStickersActivity.this, inputStickerSet, !remote ? stickerSet : null, null);
        boolean isSelected = ((StickerSetCell) view).isChecked();
        stickersAlert.setCustomButtonDelegate(new StickersAlert.StickersAlertCustomButtonDelegate() {
            @Override
            public int getCustomButtonTextColorKey() {
                return isSelected ? Theme.key_text_RedBold : Theme.key_featuredStickers_buttonText;
            }

            @Override
            public int getCustomButtonRippleColorKey() {
                return !isSelected ? Theme.key_featuredStickers_addButtonPressed : -1;
            }

            @Override
            public int getCustomButtonColorKey() {
                return !isSelected ? Theme.key_featuredStickers_addButton : -1;
            }

            @Override
            public String getCustomButtonText() {
                return LocaleController.getString(isSelected ? R.string.RemoveGroupStickerSet : R.string.SetAsGroupStickerSet);
            }

            @Override
            public boolean onCustomButtonPressed() {
                int row = layoutManager.findFirstVisibleItemPosition();
                int top = Integer.MAX_VALUE;
                RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(row);
                if (holder != null) {
                    top = holder.itemView.getTop();
                }
                int prevIndex = selectedStickerSetIndex;
                if (isSelected) {
                    selectedStickerSet = null;
                    removeStickerSet = true;
                } else {
                    selectedStickerSet = stickerSet;
                    removeStickerSet = false;
                }
                updateSelectedStickerSetIndex();

                if (prevIndex != -1) {
                    boolean found = false;
                    if (!searching) {
                        for (int i = 0; i < listView.getChildCount(); i++) {
                            View ch = listView.getChildAt(i);
                            if (listView.getChildViewHolder(ch).getAdapterPosition() == stickersStartRow + prevIndex) {
                                ((StickerSetCell) ch).setChecked(false, true);
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found) {
                        listAdapter.notifyItemChanged(prevIndex);
                    }
                }
                if (selectedStickerSetIndex != -1) {
                    boolean found = false;
                    if (!searching) {
                        for (int i = 0; i < listView.getChildCount(); i++) {
                            View ch = listView.getChildAt(i);
                            if (listView.getChildViewHolder(ch).getAdapterPosition() == stickersStartRow + selectedStickerSetIndex) {
                                ((StickerSetCell) ch).setChecked(true, true);
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found) {
                        listAdapter.notifyItemChanged(selectedStickerSetIndex);
                    }
                }

                if (top != Integer.MAX_VALUE) {
                    layoutManager.scrollToPositionWithOffset(row + 1, top);
                }
                if (searching) {
                    searchItem.setSearchFieldText("", false);
                    actionBar.closeSearchField(true);
                }
                return true;
            }
        });
        stickersAlert.show();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.stickersDidLoad) {
            if ((Integer) args[0] == MediaDataController.TYPE_IMAGE) {
                updateRows();
            }
        } else if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (chatFull.id == chatId) {
                if (info == null && chatFull.stickerset != null) {
                    selectedStickerSet = MediaDataController.getInstance(currentAccount).getGroupStickerSetById(chatFull.stickerset);
                }
                info = chatFull;
                updateRows();
            }
        } else if (id == NotificationCenter.groupStickersDidLoad) {
            long setId = (Long) args[0];
            if (info != null && info.stickerset != null && info.stickerset.id == setId) {
                updateRows();
            }
        }
    }

    public void setInfo(TLRPC.ChatFull chatFull) {
        info = chatFull;
        if (info != null && info.stickerset != null) {
            selectedStickerSet = MediaDataController.getInstance(currentAccount).getGroupStickerSetById(info.stickerset);
        }
    }

    private void saveStickerSet() {
        if (info == null || info.stickerset != null && selectedStickerSet != null && selectedStickerSet.set.id == info.stickerset.id || info.stickerset == null && selectedStickerSet == null) {
            return;
        }
        TLRPC.TL_channels_setStickers req = new TLRPC.TL_channels_setStickers();
        req.channel = MessagesController.getInstance(currentAccount).getInputChannel(chatId);
        if (removeStickerSet) {
            req.stickerset = new TLRPC.TL_inputStickerSetEmpty();
        } else {
            MessagesController.getEmojiSettings(currentAccount).edit().remove("group_hide_stickers_" + info.id).apply();
            req.stickerset = new TLRPC.TL_inputStickerSetID();
            req.stickerset.id = selectedStickerSet.set.id;
            req.stickerset.access_hash = selectedStickerSet.set.access_hash;
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                if (selectedStickerSet == null) {
                    info.stickerset = null;
                } else {
                    info.stickerset = selectedStickerSet.set;
                    MediaDataController.getInstance(currentAccount).putGroupStickerSet(selectedStickerSet);
                }
                updateSelectedStickerSetIndex();

                if (info.stickerset == null) {
                    info.flags |= 256;
                } else {
                    info.flags = info.flags &~ 256;
                }
                MessagesStorage.getInstance(currentAccount).updateChatInfo(info, false);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoDidLoad, info, 0, true, false);
                finishFragment();
            } else {
                if (getParentActivity() != null) {
                    Toast.makeText(getParentActivity(), LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error.text, Toast.LENGTH_SHORT).show();
                }
            }
        }));
    }

    private void updateSelectedStickerSetIndex() {
        ArrayList<TLRPC.TL_messages_stickerSet> stickerSets = MediaDataController.getInstance(currentAccount).getStickerSets(MediaDataController.TYPE_IMAGE);
        selectedStickerSetIndex = -1;

        long selectedSet;

        if (removeStickerSet) {
            selectedSet = 0;
        } else if (selectedStickerSet != null) {
            selectedSet = selectedStickerSet.set.id;
        } else if (info != null && info.stickerset != null) {
            selectedSet = info.stickerset.id;
        } else {
            selectedSet = 0;
        }

        if (selectedSet != 0) {
            for (int i = 0; i < stickerSets.size(); i++) {
                TLRPC.TL_messages_stickerSet set = stickerSets.get(i);
                if (set.set.id == selectedSet) {
                    selectedStickerSetIndex = i;
                    break;
                }
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void updateRows() {
        rowCount = 0;
        ArrayList<TLRPC.TL_messages_stickerSet> stickerSets = MediaDataController.getInstance(currentAccount).getStickerSets(MediaDataController.TYPE_IMAGE);
        if (!stickerSets.isEmpty()) {
            headerRow = rowCount++;
            stickersStartRow = rowCount;
            stickersEndRow = rowCount + stickerSets.size();
            rowCount += stickerSets.size();
        } else {
            headerRow = -1;
            stickersStartRow = -1;
            stickersEndRow = -1;
        }
        infoRow = rowCount++;
        updateSelectedStickerSetIndex();

        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private class SearchAdapter extends RecyclerListView.SelectionAdapter {
        private final static int TYPE_STICKER_SET = 0,
            TYPE_MY_STICKERS_HEADER = 1;

        private Context mContext;
        private List<TLRPC.TL_messages_stickerSet> searchEntries = new ArrayList<>();
        private List<TLRPC.TL_messages_stickerSet> localSearchEntries = new ArrayList<>();

        private Runnable lastCallback;
        private String lastQuery;
        private int reqId;

        public SearchAdapter(Context context) {
            mContext = context;
            setHasStableIds(true);
        }

        @Override
        public long getItemId(int position) {
            if (getItemViewType(position) == TYPE_STICKER_SET) {
                List<TLRPC.TL_messages_stickerSet> arrayList = position > searchEntries.size() ? localSearchEntries : searchEntries;
                int row = position > searchEntries.size() ? position - searchEntries.size() - 1 : position;

                return arrayList.get(row).set.id;
            }
            return -1;
        }

        @SuppressLint("NotifyDataSetChanged")
        private void onSearchStickers(String query) {
            if (reqId != 0) {
                getConnectionsManager().cancelRequest(reqId, true);
                reqId = 0;
            }

            if (lastCallback != null) {
                AndroidUtilities.cancelRunOnUIThread(lastCallback);
                lastCallback = null;
            }
            lastQuery = null;

            int count = getItemCount();
            if (count > 0) {
                searchEntries.clear();
                localSearchEntries.clear();
                notifyItemRangeRemoved(0, count);
            }

            if (TextUtils.isEmpty(query)) {
                emptyView.setVisibility(View.GONE);
                emptyView.showProgress(false, true);
                return;
            }

            if (emptyView.getVisibility() != View.VISIBLE) {
                emptyView.setVisibility(View.VISIBLE);
                emptyView.showProgress(true, false);
            } else {
                emptyView.showProgress(true, true);
            }
            AndroidUtilities.runOnUIThread(lastCallback = ()-> {
                lastQuery = query;

                TLRPC.TL_messages_searchStickerSets searchStickerSets = new TLRPC.TL_messages_searchStickerSets();
                searchStickerSets.q = query;
                reqId = getConnectionsManager().sendRequest(searchStickerSets, (response, error) -> {
                    if (!Objects.equals(lastQuery, searchStickerSets.q)) {
                        return;
                    }

                    if (response instanceof TLRPC.TL_messages_foundStickerSets) {
                        List<TLRPC.TL_messages_stickerSet> newSearchEntries = new ArrayList<>();
                        TLRPC.TL_messages_foundStickerSets foundStickerSets = (TLRPC.TL_messages_foundStickerSets) response;
                        for (TLRPC.StickerSetCovered stickerSetCovered : foundStickerSets.sets) {
                            TLRPC.TL_messages_stickerSet set = new TLRPC.TL_messages_stickerSet();
                            set.set = stickerSetCovered.set;
                            set.documents = stickerSetCovered.covers;
                            newSearchEntries.add(set);
                        }
                        String lowQuery = query.toLowerCase(Locale.ROOT).trim();
                        List<TLRPC.TL_messages_stickerSet> newLocalEntries = new ArrayList<>();
                        for (TLRPC.TL_messages_stickerSet localSet : MediaDataController.getInstance(currentAccount).getStickerSets(MediaDataController.TYPE_IMAGE)) {
                            if (localSet.set.short_name.toLowerCase(Locale.ROOT).contains(lowQuery) || localSet.set.title.toLowerCase(Locale.ROOT).contains(lowQuery)) {
                                newLocalEntries.add(localSet);
                            }
                        }
                        AndroidUtilities.runOnUIThread(()->{
                            searchEntries = newSearchEntries;
                            localSearchEntries = newLocalEntries;
                            notifyDataSetChanged();

                            emptyView.title.setVisibility(View.GONE);
                            emptyView.subtitle.setText(LocaleController.formatString(R.string.ChooseStickerNoResultsFound, query));
                            emptyView.showProgress(false, true);
                        });
                    }
                }, ConnectionsManager.RequestFlagInvokeAfter | ConnectionsManager.RequestFlagFailOnServerErrors);
            }, 300);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case TYPE_STICKER_SET:
                    view = new StickerSetCell(mContext, 3);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                default:
                case TYPE_MY_STICKERS_HEADER:
                    view = new HeaderCell(mContext, Theme.key_windowBackgroundWhiteGrayText4, 21, 0, 0, false, getResourceProvider());
                    view.setBackground(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    ((HeaderCell) view).setText(LocaleController.getString(R.string.ChooseStickerMyStickerSets));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (getItemViewType(position)) {
                case TYPE_STICKER_SET: {
                    boolean local = position > searchEntries.size();
                    List<TLRPC.TL_messages_stickerSet> arrayList = local ? localSearchEntries : searchEntries;
                    int row = local ? position - searchEntries.size() - 1 : position;
                    StickerSetCell cell = (StickerSetCell) holder.itemView;
                    TLRPC.TL_messages_stickerSet set = arrayList.get(row);
                    cell.setStickersSet(set, row != arrayList.size() - 1, !local);
                    cell.setSearchQuery(set, lastQuery != null ? lastQuery.toLowerCase(Locale.ROOT) : "", getResourceProvider());
                    long id;
                    if (selectedStickerSet != null) {
                        id = selectedStickerSet.set.id;
                    } else if (info != null && info.stickerset != null) {
                        id = info.stickerset.id;
                    } else {
                        id = 0;
                    }
                    cell.setChecked(set.set.id == id, false);
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            return searchEntries.size() == position ? TYPE_MY_STICKERS_HEADER : TYPE_STICKER_SET;
        }

        @Override
        public int getItemCount() {
            return searchEntries.size() + localSearchEntries.size() + (localSearchEntries.isEmpty() ? 0 : 1);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int viewType = getItemViewType(holder.getAdapterPosition());
            return viewType == TYPE_STICKER_SET;
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final static int TYPE_STICKER_SET = 0,
                TYPE_INFO = 1,
                TYPE_CHOOSE_HEADER = 4;

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
            switch (holder.getItemViewType()) {
                case TYPE_STICKER_SET: {
                    ArrayList<TLRPC.TL_messages_stickerSet> arrayList = MediaDataController.getInstance(currentAccount).getStickerSets(MediaDataController.TYPE_IMAGE);
                    int row = position - stickersStartRow;
                    StickerSetCell cell = (StickerSetCell) holder.itemView;
                    TLRPC.TL_messages_stickerSet set = arrayList.get(row);
                    cell.setStickersSet(arrayList.get(row), row != arrayList.size() - 1);
                    long id;
                    if (selectedStickerSet != null) {
                        id = selectedStickerSet.set.id;
                    } else if (info != null && info.stickerset != null) {
                        id = info.stickerset.id;
                    } else {
                        id = 0;
                    }
                    cell.setChecked(set.set.id == id, false);
                    break;
                }
                case TYPE_INFO: {
                    if (position == infoRow) {
                        String text = LocaleController.getString("ChooseStickerSetMy", R.string.ChooseStickerSetMy);
                        String botName = "@stickers";
                        int index = text.indexOf(botName);
                        if (index != -1) {
                            try {
                                SpannableStringBuilder stringBuilder = new SpannableStringBuilder(text);
                                URLSpanNoUnderline spanNoUnderline = new URLSpanNoUnderline("@stickers") {
                                    @Override
                                    public void onClick(View widget) {
                                        MessagesController.getInstance(currentAccount).openByUserName("stickers", GroupStickersActivity.this, 1);
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
                    }
                    break;
                }
                case TYPE_CHOOSE_HEADER: {
                    ((HeaderCell) holder.itemView).setText(LocaleController.getString(R.string.ChooseStickerSetHeader));
                    break;
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == TYPE_STICKER_SET;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case TYPE_STICKER_SET:
                    view = new StickerSetCell(mContext, 3);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_INFO:
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackground(Theme.getThemedDrawableByKey(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                case TYPE_CHOOSE_HEADER:
                default:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int i) {
            if (i >= stickersStartRow && i < stickersEndRow) {
                return TYPE_STICKER_SET;
            } else if (i == headerRow) {
                return TYPE_CHOOSE_HEADER;
            } else if (i == infoRow) {
                return TYPE_INFO;
            }
            return TYPE_STICKER_SET;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{StickerSetCell.class, TextSettingsCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

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

        return themeDescriptions;
    }
}
