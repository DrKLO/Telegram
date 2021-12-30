/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Application;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Adapters.FiltersView;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.SharedDocumentCell;
import org.telegram.ui.Cells.TextCheckBoxCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.FilteredSearchView;
import org.telegram.ui.PhotoPickerActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.StringTokenizer;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

public class ChatAttachAlertDocumentLayout extends ChatAttachAlert.AttachAlertLayout {

    public interface DocumentSelectActivityDelegate {
        void didSelectFiles(ArrayList<String> files, String caption, ArrayList<MessageObject> fmessages, boolean notify, int scheduleDate);
        void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos, boolean notify, int scheduleDate);

        void startDocumentSelectActivity();

        default void startMusicSelectActivity() {
        }
    }

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private SearchAdapter searchAdapter;
    private LinearLayoutManager layoutManager;
    private ActionBarMenuItem searchItem;
    private ActionBarMenuItem sortItem;

    private FiltersView filtersView;
    private AnimatorSet filtersViewAnimator;
    private FlickerLoadingView loadingView;

    private boolean sendPressed;

    private boolean ignoreLayout;

    private StickerEmptyView emptyView;
    private float additionalTranslationY;

    private boolean hasFiles;

    private File currentDir;
    private ArrayList<ListItem> items = new ArrayList<>();
    private boolean receiverRegistered = false;
    private ArrayList<HistoryEntry> history = new ArrayList<>();
    private DocumentSelectActivityDelegate delegate;
    private HashMap<String, ListItem> selectedFiles = new HashMap<>();
    private ArrayList<String> selectedFilesOrder = new ArrayList<>();
    private HashMap<FilteredSearchView.MessageHashId, MessageObject> selectedMessages = new HashMap<>();
    private boolean scrolling;
    private ArrayList<ListItem> recentItems = new ArrayList<>();
    private int maxSelectedFiles = -1;
    private boolean canSelectOnlyImageFiles;
    private boolean allowMusic;

    private boolean searching;

    private boolean sortByName;

    private final static int search_button = 0;
    private final static int sort_button = 6;

    private static class ListItem {
        public int icon;
        public String title;
        public String subtitle = "";
        public String ext = "";
        public String thumb;
        public File file;
    }

    private static class HistoryEntry {
        int scrollItem, scrollOffset;
        File dir;
        String title;
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent intent) {
            Runnable r = () -> {
                try {
                    if (currentDir == null) {
                        listRoots();
                    } else {
                        listFiles(currentDir);
                    }
                    updateSearchButton();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            };
            if (Intent.ACTION_MEDIA_UNMOUNTED.equals(intent.getAction())) {
                listView.postDelayed(r, 1000);
            } else {
                r.run();
            }
        }
    };

    public ChatAttachAlertDocumentLayout(ChatAttachAlert alert, Context context, boolean music, Theme.ResourcesProvider resourcesProvider) {
        super(alert, context, resourcesProvider);
        allowMusic = music;
        sortByName = SharedConfig.sortFilesByName;
        loadRecentFiles();

        searching = false;

        if (!receiverRegistered) {
            receiverRegistered = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
            filter.addAction(Intent.ACTION_MEDIA_CHECKING);
            filter.addAction(Intent.ACTION_MEDIA_EJECT);
            filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            filter.addAction(Intent.ACTION_MEDIA_NOFS);
            filter.addAction(Intent.ACTION_MEDIA_REMOVED);
            filter.addAction(Intent.ACTION_MEDIA_SHARED);
            filter.addAction(Intent.ACTION_MEDIA_UNMOUNTABLE);
            filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
            filter.addDataScheme("file");
            ApplicationLoader.applicationContext.registerReceiver(receiver, filter);
        }

        ActionBarMenu menu = parentAlert.actionBar.createMenu();
        searchItem = menu.addItem(search_button, R.drawable.ic_ab_search).setIsSearchField(true).setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                searching = true;
                sortItem.setVisibility(View.GONE);
                parentAlert.makeFocusable(searchItem.getSearchField(), true);
            }

            @Override
            public void onSearchCollapse() {
                searching = false;
                sortItem.setVisibility(View.VISIBLE);
                if (listView.getAdapter() != listAdapter) {
                    listView.setAdapter(listAdapter);
                }
                listAdapter.notifyDataSetChanged();
                searchAdapter.search(null, true);
            }

            @Override
            public void onTextChanged(EditText editText) {
                searchAdapter.search(editText.getText().toString(), false);
            }

            @Override
            public void onSearchFilterCleared(FiltersView.MediaFilterData filterData) {
                searchAdapter.removeSearchFilter(filterData);
                searchAdapter.search(searchItem.getSearchField().getText().toString(), false);
                searchAdapter.updateFiltersView(true, null, null,true);
            }
        });
        searchItem.setSearchFieldHint(LocaleController.getString("Search", R.string.Search));
        searchItem.setContentDescription(LocaleController.getString("Search", R.string.Search));
        EditTextBoldCursor editText = searchItem.getSearchField();
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setCursorColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(getThemedColor(Theme.key_chat_messagePanelHint));

        sortItem = menu.addItem(sort_button, sortByName ? R.drawable.contacts_sort_time : R.drawable.contacts_sort_name);
        sortItem.setContentDescription(LocaleController.getString("AccDescrContactSorting", R.string.AccDescrContactSorting));

        addView(loadingView = new FlickerLoadingView(context, resourcesProvider));

        emptyView = new StickerEmptyView(context, loadingView, StickerEmptyView.STICKER_TYPE_SEARCH, resourcesProvider) {
            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY + additionalTranslationY);
            }

            @Override
            public float getTranslationY() {
                return super.getTranslationY() - additionalTranslationY;
            }
        };
        addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        emptyView.setVisibility(View.GONE);
        emptyView.setOnTouchListener((v, event) -> true);

        listView = new RecyclerListView(context, resourcesProvider);
        listView.setSectionsType(2);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(layoutManager = new FillLastLinearLayoutManager(context, LinearLayoutManager.VERTICAL, false, AndroidUtilities.dp(56), listView) {
            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                LinearSmoothScroller linearSmoothScroller = new LinearSmoothScroller(recyclerView.getContext()) {
                    @Override
                    public int calculateDyToMakeVisible(View view, int snapPreference) {
                        int dy = super.calculateDyToMakeVisible(view, snapPreference);
                        dy -= (listView.getPaddingTop() - AndroidUtilities.dp(56));
                        return dy;
                    }

                    @Override
                    protected int calculateTimeForDeceleration(int dx) {
                        return super.calculateTimeForDeceleration(dx) * 2;
                    }
                };
                linearSmoothScroller.setTargetPosition(position);
                startSmoothScroll(linearSmoothScroller);
            }
        });
        listView.setClipToPadding(false);
        listView.setAdapter(listAdapter = new ListAdapter(context));
        listView.setPadding(0, 0, 0, AndroidUtilities.dp(48));
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        searchAdapter = new SearchAdapter(context);

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                parentAlert.updateLayout(ChatAttachAlertDocumentLayout.this, true, dy);
                updateEmptyViewPosition();

                if (listView.getAdapter() == searchAdapter) {
                    int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                    int lastVisibleItem = layoutManager.findLastVisibleItemPosition();
                    int visibleItemCount = Math.abs(lastVisibleItem - firstVisibleItem) + 1;
                    int totalItemCount = recyclerView.getAdapter().getItemCount();
                    if (visibleItemCount > 0 && lastVisibleItem >= totalItemCount - 10) {
                        searchAdapter.loadMore();
                    }
                }
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    int offset = AndroidUtilities.dp(13);
                    int backgroundPaddingTop = parentAlert.getBackgroundPaddingTop();
                    int top = parentAlert.scrollOffsetY[0] - backgroundPaddingTop - offset;
                    if (top + backgroundPaddingTop < ActionBar.getCurrentActionBarHeight()) {
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(0);
                        if (holder != null && holder.itemView.getTop() > AndroidUtilities.dp(56)) {
                            listView.smoothScrollBy(0, holder.itemView.getTop() - AndroidUtilities.dp(56));
                        }
                    }
                }
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING && searching && listView.getAdapter() == searchAdapter) {
                    AndroidUtilities.hideKeyboard(parentAlert.getCurrentFocus());
                }
                scrolling = newState != RecyclerView.SCROLL_STATE_IDLE;
            }
        });

        listView.setOnItemClickListener((view, position) -> {
            Object object;
            if (listView.getAdapter() == listAdapter) {
                object = listAdapter.getItem(position);
            } else {
                object = searchAdapter.getItem(position);
            }
            if (object instanceof ListItem) {
                ListItem item = (ListItem) object;
                File file = item.file;
                boolean isExternalStorageManager = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    isExternalStorageManager = Environment.isExternalStorageManager();
                }
                if (!BuildVars.NO_SCOPED_STORAGE && (item.icon == R.drawable.files_storage || item.icon == R.drawable.files_internal) && !isExternalStorageManager) {
                    delegate.startDocumentSelectActivity();
                } else if (file == null) {
                    if (item.icon == R.drawable.files_gallery) {
                        HashMap<Object, Object> selectedPhotos = new HashMap<>();
                        ArrayList<Object> selectedPhotosOrder = new ArrayList<>();
                        ChatActivity chatActivity;
                        if (parentAlert.baseFragment instanceof ChatActivity) {
                            chatActivity = (ChatActivity) parentAlert.baseFragment;
                        } else {
                            chatActivity = null;
                        }

                        PhotoPickerActivity fragment = new PhotoPickerActivity(0, MediaController.allMediaAlbumEntry, selectedPhotos, selectedPhotosOrder, 0, chatActivity != null, chatActivity, false);
                        fragment.setDocumentsPicker(true);
                        fragment.setDelegate(new PhotoPickerActivity.PhotoPickerActivityDelegate() {
                            @Override
                            public void selectedPhotosChanged() {

                            }

                            @Override
                            public void actionButtonPressed(boolean canceled, boolean notify, int scheduleDate) {
                                if (!canceled) {
                                    sendSelectedPhotos(selectedPhotos, selectedPhotosOrder, notify, scheduleDate);
                                }
                            }

                            @Override
                            public void onCaptionChanged(CharSequence text) {

                            }

                            @Override
                            public void onOpenInPressed() {
                                delegate.startDocumentSelectActivity();
                            }
                        });
                        fragment.setMaxSelectedPhotos(maxSelectedFiles, false);
                        parentAlert.baseFragment.presentFragment(fragment);
                        parentAlert.dismiss();
                    } else if (item.icon == R.drawable.files_music) {
                        if (delegate != null) {
                            delegate.startMusicSelectActivity();
                        }
                    } else {
                        int top = getTopForScroll();
                        HistoryEntry he = history.remove(history.size() - 1);
                        parentAlert.actionBar.setTitle(he.title);
                        if (he.dir != null) {
                            listFiles(he.dir);
                        } else {
                            listRoots();
                        }
                        updateSearchButton();
                        layoutManager.scrollToPositionWithOffset(0, top);
                    }
                } else if (file.isDirectory()) {
                    HistoryEntry he = new HistoryEntry();
                    View child = listView.getChildAt(0);
                    RecyclerView.ViewHolder holder = listView.findContainingViewHolder(child);
                    if (holder != null) {
                        he.scrollItem = holder.getAdapterPosition();
                        he.scrollOffset = child.getTop();
                        he.dir = currentDir;
                        he.title = parentAlert.actionBar.getTitle();
                        history.add(he);
                        if (!listFiles(file)) {
                            history.remove(he);
                            return;
                        }
                        parentAlert.actionBar.setTitle(item.title);
                    }
                } else {
                    onItemClick(view, item);
                }
            } else {
                onItemClick(view, object);
            }
        });

        listView.setOnItemLongClickListener((view, position) -> {
            Object object;
            if (listView.getAdapter() == listAdapter) {
                object = listAdapter.getItem(position);
            } else {
                object = searchAdapter.getItem(position);
            }
            return onItemClick(view, object);
        });

        filtersView = new FiltersView(context, resourcesProvider);
        filtersView.setOnItemClickListener((view, position) -> {
            filtersView.cancelClickRunnables(true);
            searchAdapter.addSearchFilter(filtersView.getFilterAt(position));
        });
        filtersView.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
        addView(filtersView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        filtersView.setTranslationY(-AndroidUtilities.dp(44));
        filtersView.setVisibility(INVISIBLE);

        listRoots();
        updateSearchButton();
        updateEmptyView();
    }

    @Override
    void onDestroy() {
        try {
            if (receiverRegistered) {
                ApplicationLoader.applicationContext.unregisterReceiver(receiver);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        parentAlert.actionBar.closeSearchField();
        ActionBarMenu menu = parentAlert.actionBar.createMenu();
        menu.removeView(sortItem);
        menu.removeView(searchItem);
    }

    @Override
    void onMenuItemClick(int id) {
        if (id == sort_button) {
            SharedConfig.toggleSortFilesByName();
            sortByName = SharedConfig.sortFilesByName;
            sortRecentItems();
            sortFileItems();
            listAdapter.notifyDataSetChanged();
            sortItem.setIcon(sortByName ? R.drawable.contacts_sort_time : R.drawable.contacts_sort_name);
        }
    }

    @Override
    int needsActionBar() {
        return 1;
    }

    @Override
    int getCurrentItemTop() {
        if (listView.getChildCount() <= 0) {
            return Integer.MAX_VALUE;
        }
        View child = listView.getChildAt(0);
        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
        int top = (int) child.getY() - AndroidUtilities.dp(8);
        int newOffset = top > 0 && holder != null && holder.getAdapterPosition() == 0 ? top : 0;
        if (top >= 0 && holder != null && holder.getAdapterPosition() == 0) {
            newOffset = top;
        }
        return newOffset + AndroidUtilities.dp(13);
    }

    @Override
    public void setTranslationY(float translationY) {
        super.setTranslationY(translationY);
        parentAlert.getSheetContainer().invalidate();
    }

    @Override
    int getListTopPadding() {
        return listView.getPaddingTop();
    }

    @Override
    int getFirstOffset() {
        return getListTopPadding() + AndroidUtilities.dp(5);
    }

    @Override
    void onPreMeasure(int availableWidth, int availableHeight) {
        int padding;
        if (parentAlert.actionBar.isSearchFieldVisible() || parentAlert.sizeNotifierFrameLayout.measureKeyboardHeight() > AndroidUtilities.dp(20)) {
            padding = AndroidUtilities.dp(56);
            parentAlert.setAllowNestedScroll(false);
        } else {
            if (!AndroidUtilities.isTablet() && AndroidUtilities.displaySize.x > AndroidUtilities.displaySize.y) {
                padding = (int) (availableHeight / 3.5f);
            } else {
                padding = (availableHeight / 5 * 2);
            }
            padding -= AndroidUtilities.dp(1);
            if (padding < 0) {
                padding = 0;
            }
            parentAlert.setAllowNestedScroll(true);
        }
        if (listView.getPaddingTop() != padding) {
            ignoreLayout = true;
            listView.setPadding(0, padding, 0, AndroidUtilities.dp(48));
            ignoreLayout = false;
        }
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) filtersView.getLayoutParams();
        layoutParams.topMargin = ActionBar.getCurrentActionBarHeight();
    }

    @Override
    int getButtonsHideOffset() {
        return AndroidUtilities.dp(62);
    }

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    @Override
    void scrollToTop() {
        listView.smoothScrollToPosition(0);
    }

    @Override
    int getSelectedItemsCount() {
        return selectedFiles.size() + selectedMessages.size();
    }

    @Override
    void sendSelectedItems(boolean notify, int scheduleDate) {
        if (selectedFiles.size() == 0 && selectedMessages.size() == 0 || delegate == null || sendPressed) {
            return;
        }
        sendPressed = true;
        ArrayList<MessageObject> fmessages = new ArrayList<>();
        Iterator<FilteredSearchView.MessageHashId> idIterator = selectedMessages.keySet().iterator();
        while (idIterator.hasNext()) {
            FilteredSearchView.MessageHashId hashId = idIterator.next();
            fmessages.add(selectedMessages.get(hashId));
        }
        ArrayList<String> files = new ArrayList<>(selectedFilesOrder);
        delegate.didSelectFiles(files, parentAlert.commentTextView.getText().toString(), fmessages, notify, scheduleDate);

        parentAlert.dismiss();
    }

    private boolean onItemClick(View view, Object object) {
        boolean add;
        if (object instanceof ListItem) {
            ListItem item = (ListItem) object;
            if (item.file == null || item.file.isDirectory()) {
                return false;
            }
            String path = item.file.getAbsolutePath();
            if (selectedFiles.containsKey(path)) {
                selectedFiles.remove(path);
                selectedFilesOrder.remove(path);
                add = false;
            } else {
                if (!item.file.canRead()) {
                    showErrorBox(LocaleController.getString("AccessError", R.string.AccessError));
                    return false;
                }
                if (canSelectOnlyImageFiles && item.thumb == null) {
                    showErrorBox(LocaleController.formatString("PassportUploadNotImage", R.string.PassportUploadNotImage));
                    return false;
                }
                if (item.file.length() > FileLoader.MAX_FILE_SIZE) {
                    showErrorBox(LocaleController.formatString("FileUploadLimit", R.string.FileUploadLimit, AndroidUtilities.formatFileSize(FileLoader.MAX_FILE_SIZE)));
                    return false;
                }
                if (maxSelectedFiles >= 0 && selectedFiles.size() >= maxSelectedFiles) {
                    showErrorBox(LocaleController.formatString("PassportUploadMaxReached", R.string.PassportUploadMaxReached, LocaleController.formatPluralString("Files", maxSelectedFiles)));
                    return false;
                }
                if (item.file.length() == 0) {
                    return false;
                }
                selectedFiles.put(path, item);
                selectedFilesOrder.add(path);
                add = true;
            }
            scrolling = false;
        } else if (object instanceof MessageObject) {
            MessageObject message = (MessageObject) object;
            FilteredSearchView.MessageHashId hashId = new FilteredSearchView.MessageHashId(message.getId(), message.getDialogId());
            if (selectedMessages.containsKey(hashId)) {
                selectedMessages.remove(hashId);
                add = false;
            } else {
                if (selectedMessages.size() >= 100) {
                    return false;
                }
                selectedMessages.put(hashId, message);
                add = true;
            }
        } else {
            return false;
        }
        if (view instanceof SharedDocumentCell) {
            ((SharedDocumentCell) view).setChecked(add, true);
        }
        parentAlert.updateCountButton(add ? 1 : 2);
        return true;
    }

    public void setMaxSelectedFiles(int value) {
        maxSelectedFiles = value;
    }

    public void setCanSelectOnlyImageFiles(boolean value) {
        canSelectOnlyImageFiles = true;
    }

    private void sendSelectedPhotos(HashMap<Object, Object> photos, ArrayList<Object> order, boolean notify, int scheduleDate) {
        if (photos.isEmpty() || delegate == null || sendPressed) {
            return;
        }
        sendPressed = true;
        ArrayList<SendMessagesHelper.SendingMediaInfo> media = new ArrayList<>();
        for (int a = 0; a < order.size(); a++) {
            Object object = photos.get(order.get(a));
            SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
            media.add(info);
            if (object instanceof MediaController.PhotoEntry) {
                MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) object;
                if (photoEntry.imagePath != null) {
                    info.path = photoEntry.imagePath;
                } else {
                    info.path = photoEntry.path;
                }
                info.thumbPath = photoEntry.thumbPath;
                info.videoEditedInfo = photoEntry.editedInfo;
                info.isVideo = photoEntry.isVideo;
                info.caption = photoEntry.caption != null ? photoEntry.caption.toString() : null;
                info.entities = photoEntry.entities;
                info.masks = photoEntry.stickers;
                info.ttl = photoEntry.ttl;
            }
        }
        delegate.didSelectPhotos(media, notify, scheduleDate);
    }

    public void loadRecentFiles() {
        try {
            File[] files = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).listFiles();
            if (files != null) {
                for (int a = 0; a < files.length; a++) {
                    File file = files[a];
                    if (file.isDirectory()) {
                        continue;
                    }
                    ListItem item = new ListItem();
                    item.title = file.getName();
                    item.file = file;
                    String fname = file.getName();
                    String[] sp = fname.split("\\.");
                    item.ext = sp.length > 1 ? sp[sp.length - 1] : "?";
                    item.subtitle = AndroidUtilities.formatFileSize(file.length());
                    fname = fname.toLowerCase();
                    if (fname.endsWith(".jpg") || fname.endsWith(".png") || fname.endsWith(".gif") || fname.endsWith(".jpeg")) {
                        item.thumb = file.getAbsolutePath();
                    }
                    recentItems.add(item);
                }
            }
            sortRecentItems();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void sortRecentItems() {
        Collections.sort(recentItems, (o1, o2) -> {
            if (sortByName) {
                String lm = o1.file.getName();
                String rm = o2.file.getName();
                return lm.compareToIgnoreCase(rm);
            } else {
                long lm = o1.file.lastModified();
                long rm = o2.file.lastModified();
                if (lm == rm) {
                    return 0;
                } else if (lm > rm) {
                    return -1;
                } else {
                    return 1;
                }
            }
        });
    }

    private void sortFileItems() {
        if (currentDir == null) {
            return;
        }
        Collections.sort(items, (lhs, rhs) -> {
            if (lhs.file == null) {
                return -1;
            } else if (rhs.file == null) {
                return 1;
            }
            boolean isDir1 = lhs.file.isDirectory();
            boolean isDir2 = rhs.file.isDirectory();
            if (isDir1 != isDir2) {
                return isDir1 ? -1 : 1;
            } else if (isDir1 || sortByName) {
                return lhs.file.getName().compareToIgnoreCase(rhs.file.getName());
            } else {
                long lm = lhs.file.lastModified();
                long rm = rhs.file.lastModified();
                if (lm == rm) {
                    return 0;
                } else if (lm > rm) {
                    return -1;
                } else {
                    return 1;
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        if (searchAdapter != null) {
            searchAdapter.notifyDataSetChanged();
        }
    }

    @Override
    void onShow() {
        selectedFiles.clear();
        selectedMessages.clear();
        searchAdapter.currentSearchFilters.clear();
        selectedFilesOrder.clear();
        history.clear();
        listRoots();
        updateSearchButton();
        updateEmptyView();
        parentAlert.actionBar.setTitle(LocaleController.getString("SelectFile", R.string.SelectFile));
        sortItem.setVisibility(VISIBLE);
        layoutManager.scrollToPositionWithOffset(0, 0);
    }

    @Override
    void onHide() {
        sortItem.setVisibility(GONE);
        searchItem.setVisibility(GONE);
    }

    private void updateEmptyViewPosition() {
        if (emptyView.getVisibility() != VISIBLE) {
            return;
        }
        View child = listView.getChildAt(0);
        if (child == null) {
            return;
        }
        float oldTranslation = emptyView.getTranslationY();
        additionalTranslationY = (emptyView.getMeasuredHeight() - getMeasuredHeight() + child.getTop()) / 2;
        emptyView.setTranslationY(oldTranslation);
    }

    private void updateEmptyView() {
        boolean visible;
        if (listView.getAdapter() == searchAdapter) {
            visible = searchAdapter.searchResult.isEmpty() && searchAdapter.sections.isEmpty();
        } else {
            visible = listAdapter.getItemCount() == 1;
        }
        emptyView.setVisibility(visible ? VISIBLE : GONE);
        updateEmptyViewPosition();
    }

    private void updateSearchButton() {
        if (searchItem == null) {
            return;
        }
        if (!searchItem.isSearchFieldVisible()) {
            searchItem.setVisibility(hasFiles || history.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private int getTopForScroll() {
        View child = listView.getChildAt(0);
        RecyclerView.ViewHolder holder = listView.findContainingViewHolder(child);
        int top = -listView.getPaddingTop();
        if (holder != null && holder.getAdapterPosition() == 0) {
            top += child.getTop();
        }
        return top;
    }

    private boolean canClosePicker() {
        if (history.size() > 0) {
            HistoryEntry he = history.remove(history.size() - 1);
            parentAlert.actionBar.setTitle(he.title);
            int top = getTopForScroll();
            if (he.dir != null) {
                listFiles(he.dir);
            } else {
                listRoots();
            }
            updateSearchButton();
            layoutManager.scrollToPositionWithOffset(0, top);
            return false;
        }
        return true;
    }

    @Override
    public boolean onBackPressed() {
        if (!canClosePicker()) {
            return true;
        }
        return super.onBackPressed();
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateEmptyViewPosition();
    }

    public void setDelegate(DocumentSelectActivityDelegate documentSelectActivityDelegate) {
        delegate = documentSelectActivityDelegate;
    }

    private boolean listFiles(File dir) {
        hasFiles = false;
        if (!dir.canRead()) {
            if (dir.getAbsolutePath().startsWith(Environment.getExternalStorageDirectory().toString())
                    || dir.getAbsolutePath().startsWith("/sdcard")
                    || dir.getAbsolutePath().startsWith("/mnt/sdcard")) {
                if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)
                        && !Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
                    currentDir = dir;
                    items.clear();
                    String state = Environment.getExternalStorageState();
                    AndroidUtilities.clearDrawableAnimation(listView);
                    scrolling = true;
                    listAdapter.notifyDataSetChanged();
                    return true;
                }
            }
            showErrorBox(LocaleController.getString("AccessError", R.string.AccessError));
            return false;
        }
        File[] files;
        try {
            files = dir.listFiles();
        } catch (Exception e) {
            showErrorBox(e.getLocalizedMessage());
            return false;
        }
        if (files == null) {
            showErrorBox(LocaleController.getString("UnknownError", R.string.UnknownError));
            return false;
        }
        currentDir = dir;
        items.clear();
        for (int a = 0; a < files.length; a++) {
            File file = files[a];
            if (file.getName().indexOf('.') == 0) {
                continue;
            }
            ListItem item = new ListItem();
            item.title = file.getName();
            item.file = file;
            if (file.isDirectory()) {
                item.icon = R.drawable.files_folder;
                item.subtitle = LocaleController.getString("Folder", R.string.Folder);
            } else {
                hasFiles = true;
                String fname = file.getName();
                String[] sp = fname.split("\\.");
                item.ext = sp.length > 1 ? sp[sp.length - 1] : "?";
                item.subtitle = AndroidUtilities.formatFileSize(file.length());
                fname = fname.toLowerCase();
                if (fname.endsWith(".jpg") || fname.endsWith(".png") || fname.endsWith(".gif") || fname.endsWith(".jpeg")) {
                    item.thumb = file.getAbsolutePath();
                }
            }
            items.add(item);
        }
        ListItem item = new ListItem();
        item.title = "..";
        if (history.size() > 0) {
            HistoryEntry entry = history.get(history.size() - 1);
            if (entry.dir == null) {
                item.subtitle = LocaleController.getString("Folder", R.string.Folder);
            } else {
                item.subtitle = entry.dir.toString();
            }
        } else {
            item.subtitle = LocaleController.getString("Folder", R.string.Folder);
        }
        item.icon = R.drawable.files_folder;
        item.file = null;
        items.add(0, item);
        sortFileItems();
        updateSearchButton();
        AndroidUtilities.clearDrawableAnimation(listView);
        scrolling = true;
        int top = getTopForScroll();
        listAdapter.notifyDataSetChanged();
        layoutManager.scrollToPositionWithOffset(0, top);
        return true;
    }

    private void showErrorBox(String error) {
        new AlertDialog.Builder(getContext(), resourcesProvider).setTitle(LocaleController.getString("AppName", R.string.AppName)).setMessage(error).setPositiveButton(LocaleController.getString("OK", R.string.OK), null).show();
    }

    @SuppressLint("NewApi")
    private void listRoots() {
        currentDir = null;
        hasFiles = false;
        items.clear();

        HashSet<String> paths = new HashSet<>();
        boolean isExternalStorageManager = false;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            isExternalStorageManager = Environment.isExternalStorageManager();
        }
//        if (!BuildVars.NO_SCOPED_STORAGE && !isExternalStorageManager) {
//            ListItem ext = new ListItem();
//            ext.title = LocaleController.getString("InternalStorage", R.string.InternalStorage);
//            ext.icon = R.drawable.files_storage;
//            ext.subtitle = LocaleController.getString("InternalFolderInfo", R.string.InternalFolderInfo);
//            items.add(ext);
//        } else {
            String defaultPath = Environment.getExternalStorageDirectory().getPath();
            String defaultPathState = Environment.getExternalStorageState();
            if (defaultPathState.equals(Environment.MEDIA_MOUNTED) || defaultPathState.equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
                ListItem ext = new ListItem();
                if (Environment.isExternalStorageRemovable()) {
                    ext.title = LocaleController.getString("SdCard", R.string.SdCard);
                    ext.icon = R.drawable.files_internal;
                    ext.subtitle = LocaleController.getString("ExternalFolderInfo", R.string.ExternalFolderInfo);
                } else {
                    ext.title = LocaleController.getString("InternalStorage", R.string.InternalStorage);
                    ext.icon = R.drawable.files_storage;
                    ext.subtitle = LocaleController.getString("InternalFolderInfo", R.string.InternalFolderInfo);
                }
                ext.file = Environment.getExternalStorageDirectory();
                items.add(ext);
                paths.add(defaultPath);
            }

            BufferedReader bufferedReader = null;
            try {
                bufferedReader = new BufferedReader(new FileReader("/proc/mounts"));
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    if (line.contains("vfat") || line.contains("/mnt")) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d(line);
                        }
                        StringTokenizer tokens = new StringTokenizer(line, " ");
                        String unused = tokens.nextToken();
                        String path = tokens.nextToken();
                        if (paths.contains(path)) {
                            continue;
                        }
                        if (line.contains("/dev/block/vold")) {
                            if (!line.contains("/mnt/secure") && !line.contains("/mnt/asec") && !line.contains("/mnt/obb") && !line.contains("/dev/mapper") && !line.contains("tmpfs")) {
                                if (!new File(path).isDirectory()) {
                                    int index = path.lastIndexOf('/');
                                    if (index != -1) {
                                        String newPath = "/storage/" + path.substring(index + 1);
                                        if (new File(newPath).isDirectory()) {
                                            path = newPath;
                                        }
                                    }
                                }
                                paths.add(path);
                                try {
                                    ListItem item = new ListItem();
                                    if (path.toLowerCase().contains("sd")) {
                                        item.title = LocaleController.getString("SdCard", R.string.SdCard);
                                    } else {
                                        item.title = LocaleController.getString("ExternalStorage", R.string.ExternalStorage);
                                    }
                                    item.subtitle = LocaleController.getString("ExternalFolderInfo", R.string.ExternalFolderInfo);
                                    item.icon = R.drawable.files_internal;
                                    item.file = new File(path);
                                    items.add(item);
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                if (bufferedReader != null) {
                    try {
                        bufferedReader.close();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
        //}

        ListItem fs;
        try {
            File telegramPath = new File(ApplicationLoader.applicationContext.getExternalFilesDir(null), "Telegram");
            if (telegramPath.exists()) {
                fs = new ListItem();
                fs.title = "Telegram";
                fs.subtitle = LocaleController.getString("AppFolderInfo", R.string.AppFolderInfo);
                fs.icon = R.drawable.files_folder;
                fs.file = telegramPath;
                items.add(fs);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        fs = new ListItem();
        fs.title = LocaleController.getString("Gallery", R.string.Gallery);
        fs.subtitle = LocaleController.getString("GalleryInfo", R.string.GalleryInfo);
        fs.icon = R.drawable.files_gallery;
        fs.file = null;
        items.add(fs);

        if (allowMusic) {
            fs = new ListItem();
            fs.title = LocaleController.getString("AttachMusic", R.string.AttachMusic);
            fs.subtitle = LocaleController.getString("MusicInfo", R.string.MusicInfo);
            fs.icon = R.drawable.files_music;
            fs.file = null;
            items.add(fs);
        }
        if (!recentItems.isEmpty()) {
            hasFiles = true;
        }

        AndroidUtilities.clearDrawableAnimation(listView);
        scrolling = true;
        listAdapter.notifyDataSetChanged();
    }

    private String getRootSubtitle(String path) {
        try {
            StatFs stat = new StatFs(path);
            long total = (long) stat.getBlockCount() * (long) stat.getBlockSize();
            long free = (long) stat.getAvailableBlocks() * (long) stat.getBlockSize();
            if (total == 0) {
                return "";
            }
            return LocaleController.formatString("FreeOfTotal", R.string.FreeOfTotal, AndroidUtilities.formatFileSize(free), AndroidUtilities.formatFileSize(total));
        } catch (Exception e) {
            FileLog.e(e);
        }
        return path;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 1;
        }

        @Override
        public int getItemCount() {
            int count = items.size();
            if (history.isEmpty() && !recentItems.isEmpty()) {
                count += recentItems.size() + 2;
            }
            return count + 1;
        }

        public ListItem getItem(int position) {
            int itemsSize = items.size();
            if (position < itemsSize) {
                return items.get(position);
            } else if (history.isEmpty() && !recentItems.isEmpty() && position != itemsSize && position != itemsSize + 1) {
                position -= items.size() + 2;
                if (position < recentItems.size()) {
                    return recentItems.get(position);
                }
            }
            return null;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == getItemCount() - 1) {
                return 3;
            } else {
                int itemsSize = items.size();
                if (position == itemsSize) {
                    return 2;
                } else if (position == itemsSize + 1) {
                    return 0;
                }
            }
            return 1;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new HeaderCell(mContext, resourcesProvider);
                    break;
                case 1:
                    view = new SharedDocumentCell(mContext, SharedDocumentCell.VIEW_TYPE_PICKER, resourcesProvider);
                    break;
                case 2:
                    view = new ShadowSectionCell(mContext);
                    Drawable drawable = Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow);
                    CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(getThemedColor(Theme.key_windowBackgroundGray)), drawable);
                    combinedDrawable.setFullsize(true);
                    view.setBackgroundDrawable(combinedDrawable);
                    break;
                case 3:
                default:
                    view = new View(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (sortByName) {
                        headerCell.setText(LocaleController.getString("RecentFilesAZ", R.string.RecentFilesAZ));
                    } else {
                        headerCell.setText(LocaleController.getString("RecentFiles", R.string.RecentFiles));
                    }
                    break;
                case 1:
                    ListItem item = getItem(position);
                    SharedDocumentCell documentCell = (SharedDocumentCell) holder.itemView;
                    if (item.icon != 0) {
                        documentCell.setTextAndValueAndTypeAndThumb(item.title, item.subtitle, null, null, item.icon, position != items.size() - 1);
                    } else {
                        String type = item.ext.toUpperCase().substring(0, Math.min(item.ext.length(), 4));
                        documentCell.setTextAndValueAndTypeAndThumb(item.title, item.subtitle, type, item.thumb, 0, false);
                    }
                    if (item.file != null) {
                        documentCell.setChecked(selectedFiles.containsKey(item.file.toString()), !scrolling);
                    } else {
                        documentCell.setChecked(false, !scrolling);
                    }
                    break;
            }
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            updateEmptyView();
        }
    }

    public class SearchAdapter extends RecyclerListView.SectionsAdapter  {

        private Context mContext;

        private ArrayList<ListItem> searchResult = new ArrayList<>();
        private Runnable searchRunnable;
        private Runnable localSearchRunnable;

        private long currentSearchDialogId;
        private FiltersView.MediaFilterData currentSearchFilter;
        private long currentSearchMinDate;
        private long currentSearchMaxDate;

        private int searchIndex;
        private int nextSearchRate;

        private final FilteredSearchView.MessageHashId messageHashIdTmp = new FilteredSearchView.MessageHashId(0, 0);

        private String lastSearchFilterQueryString;
        private String lastMessagesSearchString;
        private String currentDataQuery;

        private ArrayList<Object> localTipChats = new ArrayList<>();
        private ArrayList<FiltersView.DateData> localTipDates = new ArrayList<>();

        public ArrayList<MessageObject> messages = new ArrayList<>();
        public SparseArray<MessageObject> messagesById = new SparseArray<>();
        public ArrayList<String> sections = new ArrayList<>();
        public HashMap<String, ArrayList<MessageObject>> sectionArrays = new HashMap<>();

        private ArrayList<FiltersView.MediaFilterData> currentSearchFilters = new ArrayList<>();

        private boolean isLoading;
        private int requestIndex;
        private boolean firstLoading = true;
        private int animationIndex = -1;
        private boolean endReached;

        private Runnable clearCurrentResultsRunnable = new Runnable() {
            @Override
            public void run() {
                if (isLoading) {
                    messages.clear();
                    sections.clear();
                    sectionArrays.clear();
                    notifyDataSetChanged();
                }
            }
        };

        public SearchAdapter(Context context) {
            mContext = context;
        }

        public void search(final String query, boolean reset) {
            if (localSearchRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(localSearchRunnable);
                localSearchRunnable = null;
            }
            if (TextUtils.isEmpty(query)) {
                if (!searchResult.isEmpty()) {
                    searchResult.clear();
                }
                if (listView.getAdapter() != listAdapter) {
                    listView.setAdapter(listAdapter);
                }
                notifyDataSetChanged();
            } else {
                AndroidUtilities.runOnUIThread(localSearchRunnable = () -> {
                    final ArrayList<ListItem> copy = new ArrayList<>(items);
                    if (history.isEmpty()) {
                        copy.addAll(0, recentItems);
                    }
                    boolean hasFilters = !currentSearchFilters.isEmpty();
                    Utilities.searchQueue.postRunnable(() -> {
                        String search1 = query.trim().toLowerCase();
                        if (search1.length() == 0) {
                            updateSearchResults(new ArrayList<>(), query);
                            return;
                        }
                        String search2 = LocaleController.getInstance().getTranslitString(search1);
                        if (search1.equals(search2) || search2.length() == 0) {
                            search2 = null;
                        }
                        String[] search = new String[1 + (search2 != null ? 1 : 0)];
                        search[0] = search1;
                        if (search2 != null) {
                            search[1] = search2;
                        }

                        ArrayList<ListItem> resultArray = new ArrayList<>();

                        if (!hasFilters) {
                            for (int a = 0; a < copy.size(); a++) {
                                ListItem entry = copy.get(a);
                                if (entry.file == null || entry.file.isDirectory()) {
                                    continue;
                                }
                                for (int b = 0; b < search.length; b++) {
                                    String q = search[b];

                                    boolean ok = false;
                                    if (entry.title != null) {
                                        ok = entry.title.toLowerCase().contains(q);
                                    }
                                    if (ok) {
                                        resultArray.add(entry);
                                        break;
                                    }
                                }
                            }
                        }

                        updateSearchResults(resultArray, query);
                    });
                }, 300);
            }

            if (!canSelectOnlyImageFiles && history.isEmpty()) {
                long dialogId = 0;
                long minDate = 0;
                long maxDate = 0;
                for (int i = 0; i < currentSearchFilters.size(); i++) {
                    FiltersView.MediaFilterData data = currentSearchFilters.get(i);
                    if (data.filterType == FiltersView.FILTER_TYPE_CHAT) {
                        if (data.chat instanceof TLRPC.User) {
                            dialogId = ((TLRPC.User) data.chat).id;
                        } else if (data.chat instanceof TLRPC.Chat) {
                            dialogId = -((TLRPC.Chat) data.chat).id;
                        }
                    } else if (data.filterType == FiltersView.FILTER_TYPE_DATE) {
                        minDate = data.dateData.minDate;
                        maxDate = data.dateData.maxDate;
                    }
                }

                searchGlobal(dialogId, minDate, maxDate, FiltersView.filters[2], query, reset);
            }
        }

        public void loadMore() {
            if (searchAdapter.isLoading || searchAdapter.endReached || currentSearchFilter == null) {
                return;
            }
            searchGlobal(currentSearchDialogId, currentSearchMinDate, currentSearchMaxDate, currentSearchFilter, lastMessagesSearchString, false);
        }

        public void removeSearchFilter(FiltersView.MediaFilterData filterData) {
            currentSearchFilters.remove(filterData);
        }

        public void clear() {
            currentSearchFilters.clear();
        }

        private void addSearchFilter(FiltersView.MediaFilterData filter) {
            if (!currentSearchFilters.isEmpty()) {
                for (int i = 0; i < currentSearchFilters.size(); i++) {
                    if (filter.isSameType(currentSearchFilters.get(i))) {
                        return;
                    }
                }
            }
            currentSearchFilters.add(filter);
            parentAlert.actionBar.setSearchFilter(filter);
            parentAlert.actionBar.setSearchFieldText("");
            updateFiltersView(true, null, null, true);
        }

        private void updateFiltersView(boolean showMediaFilters, ArrayList<Object> users, ArrayList<FiltersView.DateData> dates, boolean animated) {
            boolean hasMediaFilter = false;
            boolean hasUserFilter = false;
            boolean hasDataFilter = false;

            for (int i = 0; i < currentSearchFilters.size(); i++) {
                if (currentSearchFilters.get(i).isMedia()) {
                    hasMediaFilter = true;
                } else if (currentSearchFilters.get(i).filterType == FiltersView.FILTER_TYPE_CHAT) {
                    hasUserFilter = true;
                } else if (currentSearchFilters.get(i).filterType == FiltersView.FILTER_TYPE_DATE) {
                    hasDataFilter = true;
                }
            }

            boolean visible = false;
            boolean hasUsersOrDates = (users != null && !users.isEmpty()) || (dates != null && !dates.isEmpty());
            if (!hasMediaFilter && !hasUsersOrDates && showMediaFilters) {
            } else if (hasUsersOrDates) {
                ArrayList<Object> finalUsers = (users != null && !users.isEmpty() && !hasUserFilter) ? users : null;
                ArrayList<FiltersView.DateData> finalDates = (dates != null && !dates.isEmpty() && !hasDataFilter) ? dates : null;
                if (finalUsers != null || finalDates != null) {
                    visible = true;
                    filtersView.setUsersAndDates(finalUsers, finalDates, false);
                }
            }
            if (!visible) {
                filtersView.setUsersAndDates(null, null, false);
            }
            filtersView.setEnabled(visible);
            if (visible && filtersView.getTag() != null || !visible && filtersView.getTag() == null) {
                return;
            }
            filtersView.setTag(visible ? 1 : null);
            if (filtersViewAnimator != null) {
                filtersViewAnimator.cancel();
            }
            if (animated) {
                if (visible) {
                    filtersView.setVisibility(VISIBLE);
                }
                filtersViewAnimator = new AnimatorSet();
                filtersViewAnimator.playTogether(
                        ObjectAnimator.ofFloat(listView, View.TRANSLATION_Y, visible ? AndroidUtilities.dp(44) : 0),
                        ObjectAnimator.ofFloat(filtersView, View.TRANSLATION_Y, visible ? 0 : -AndroidUtilities.dp(44)),
                        ObjectAnimator.ofFloat(loadingView, View.TRANSLATION_Y, visible ? AndroidUtilities.dp(44) : 0),
                        ObjectAnimator.ofFloat(emptyView, View.TRANSLATION_Y, visible ? AndroidUtilities.dp(44) : 0));
                filtersViewAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (filtersView.getTag() == null) {
                            filtersView.setVisibility(INVISIBLE);
                        }
                        filtersViewAnimator = null;
                    }
                });
                filtersViewAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                filtersViewAnimator.setDuration(180);
                filtersViewAnimator.start();
            } else {
                filtersView.getAdapter().notifyDataSetChanged();
                listView.setTranslationY(visible ? AndroidUtilities.dp(44) : 0);
                filtersView.setTranslationY(visible ? 0 : -AndroidUtilities.dp(44));
                loadingView.setTranslationY(visible ? AndroidUtilities.dp(44) : 0);
                emptyView.setTranslationY(visible ? AndroidUtilities.dp(44) : 0);
                filtersView.setVisibility(visible ? VISIBLE : INVISIBLE);
            }
        }

        private void searchGlobal(long dialogId, long minDate, long maxDate, FiltersView.MediaFilterData searchFilter, String query, boolean clearOldResults) {
            String currentSearchFilterQueryString = String.format(Locale.ENGLISH, "%d%d%d%d%s", dialogId, minDate, maxDate, searchFilter.filterType, query);
            boolean filterAndQueryIsSame = lastSearchFilterQueryString != null && lastSearchFilterQueryString.equals(currentSearchFilterQueryString);
            boolean forceClear = !filterAndQueryIsSame && clearOldResults;
            boolean filterIsSame = dialogId == currentSearchDialogId && currentSearchMinDate == minDate && currentSearchMaxDate == maxDate;
            currentSearchFilter = searchFilter;
            this.currentSearchDialogId = dialogId;
            this.currentSearchMinDate = minDate;
            this.currentSearchMaxDate = maxDate;
            if (searchRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(searchRunnable);
            }
            AndroidUtilities.cancelRunOnUIThread(clearCurrentResultsRunnable);
            if (filterAndQueryIsSame && clearOldResults) {
                return;
            }
            if (forceClear) {
                messages.clear();
                sections.clear();
                sectionArrays.clear();
                isLoading = true;
                emptyView.setVisibility(View.VISIBLE);
                notifyDataSetChanged();
                requestIndex++;
                firstLoading = true;
                if (listView.getPinnedHeader() != null) {
                    listView.getPinnedHeader().setAlpha(0);
                }
                localTipChats.clear();
                localTipDates.clear();
            }
            isLoading = true;
            notifyDataSetChanged();

            if (!filterAndQueryIsSame) {
                clearCurrentResultsRunnable.run();
                emptyView.showProgress(true, !clearOldResults);
            }

            if (TextUtils.isEmpty(query)) {
                localTipDates.clear();
                localTipChats.clear();
                updateFiltersView(false, null, null, true);
                return;
            }
            requestIndex++;
            final int requestId = requestIndex;

            AccountInstance accountInstance = AccountInstance.getInstance(UserConfig.selectedAccount);

            AndroidUtilities.runOnUIThread(searchRunnable = () -> {
                TLObject request;

                ArrayList<Object> resultArray = null;
                if (dialogId != 0) {
                    final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
                    req.q = query;
                    req.limit = 20;
                    req.filter = currentSearchFilter.filter;
                    req.peer = accountInstance.getMessagesController().getInputPeer(dialogId);
                    if (minDate > 0) {
                        req.min_date = (int) (minDate / 1000);
                    }
                    if (maxDate > 0) {
                        req.max_date = (int) (maxDate / 1000);
                    }
                    if (filterAndQueryIsSame && query.equals(lastMessagesSearchString) && !messages.isEmpty()) {
                        MessageObject lastMessage = messages.get(messages.size() - 1);
                        req.offset_id = lastMessage.getId();
                    } else {
                        req.offset_id = 0;
                    }
                    request = req;
                } else {
                    if (!TextUtils.isEmpty(query)) {
                        resultArray = new ArrayList<>();
                        ArrayList<CharSequence> resultArrayNames = new ArrayList<>();
                        ArrayList<TLRPC.User> encUsers = new ArrayList<>();
                        accountInstance.getMessagesStorage().localSearch(0, query, resultArray, resultArrayNames, encUsers, -1);
                    }

                    final TLRPC.TL_messages_searchGlobal req = new TLRPC.TL_messages_searchGlobal();
                    req.limit = 20;
                    req.q = query;
                    req.filter = currentSearchFilter.filter;
                    if (minDate > 0) {
                        req.min_date = (int) (minDate / 1000);
                    }
                    if (maxDate > 0) {
                        req.max_date = (int) (maxDate / 1000);
                    }
                    if (filterAndQueryIsSame && query.equals(lastMessagesSearchString) && !messages.isEmpty()) {
                        MessageObject lastMessage = messages.get(messages.size() - 1);
                        req.offset_id = lastMessage.getId();
                        req.offset_rate = nextSearchRate;
                        long id;
                        if (lastMessage.messageOwner.peer_id.channel_id != 0) {
                            id = -lastMessage.messageOwner.peer_id.channel_id;
                        } else if (lastMessage.messageOwner.peer_id.chat_id != 0) {
                            id = -lastMessage.messageOwner.peer_id.chat_id;
                        } else {
                            id = lastMessage.messageOwner.peer_id.user_id;
                        }
                        req.offset_peer = accountInstance.getMessagesController().getInputPeer(id);
                    } else {
                        req.offset_rate = 0;
                        req.offset_id = 0;
                        req.offset_peer = new TLRPC.TL_inputPeerEmpty();
                    }
                    request = req;
                }

                lastMessagesSearchString = query;
                lastSearchFilterQueryString = currentSearchFilterQueryString;

                ArrayList<Object> finalResultArray = resultArray;
                final ArrayList<FiltersView.DateData> dateData = new ArrayList<>();
                FiltersView.fillTipDates(lastMessagesSearchString, dateData);
                accountInstance.getConnectionsManager().sendRequest(request, (response, error) -> {
                    ArrayList<MessageObject> messageObjects = new ArrayList<>();
                    if (error == null) {
                        TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                        int n = res.messages.size();
                        for (int i = 0; i < n; i++) {
                            MessageObject messageObject = new MessageObject(accountInstance.getCurrentAccount(), res.messages.get(i), false, true);
                            messageObject.setQuery(query);
                            messageObjects.add(messageObject);
                        }
                    }

                    AndroidUtilities.runOnUIThread(() -> {
                        if (requestId != requestIndex) {
                            return;
                        }
                        isLoading = false;
                        if (error != null) {
                            emptyView.title.setText(LocaleController.getString("SearchEmptyViewTitle2", R.string.SearchEmptyViewTitle2));
                            emptyView.subtitle.setVisibility(View.VISIBLE);
                            emptyView.subtitle.setText(LocaleController.getString("SearchEmptyViewFilteredSubtitle2", R.string.SearchEmptyViewFilteredSubtitle2));
                            emptyView.showProgress(false, true);
                            return;
                        }

                        emptyView.showProgress(false);

                        TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                        nextSearchRate = res.next_rate;
                        accountInstance.getMessagesStorage().putUsersAndChats(res.users, res.chats, true, true);
                        accountInstance.getMessagesController().putUsers(res.users, false);
                        accountInstance.getMessagesController().putChats(res.chats, false);
                        if (!filterAndQueryIsSame) {
                            messages.clear();
                            messagesById.clear();
                            sections.clear();
                            sectionArrays.clear();
                        }
                        int totalCount = res.count;
                        currentDataQuery = query;
                        int n = messageObjects.size();
                        for (int i = 0; i < n; i++) {
                            MessageObject messageObject = messageObjects.get(i);
                            ArrayList<MessageObject> messageObjectsByDate = sectionArrays.get(messageObject.monthKey);
                            if (messageObjectsByDate == null) {
                                messageObjectsByDate = new ArrayList<>();
                                sectionArrays.put(messageObject.monthKey, messageObjectsByDate);
                                sections.add(messageObject.monthKey);
                            }
                            messageObjectsByDate.add(messageObject);
                            messages.add(messageObject);
                            messagesById.put(messageObject.getId(), messageObject);
                        }
                        if (messages.size() > totalCount) {
                            totalCount = messages.size();
                        }
                        endReached = messages.size() >= totalCount;

                        if (messages.isEmpty()) {
                            if (TextUtils.isEmpty(currentDataQuery) && dialogId == 0 && minDate == 0) {
                                emptyView.title.setText(LocaleController.getString("SearchEmptyViewTitle", R.string.SearchEmptyViewTitle));
                                emptyView.subtitle.setVisibility(View.VISIBLE);
                                emptyView.subtitle.setText(LocaleController.getString("SearchEmptyViewFilteredSubtitleFiles", R.string.SearchEmptyViewFilteredSubtitleFiles));
                            } else {
                                emptyView.title.setText(LocaleController.getString("SearchEmptyViewTitle2", R.string.SearchEmptyViewTitle2));
                                emptyView.subtitle.setVisibility(View.VISIBLE);
                                emptyView.subtitle.setText(LocaleController.getString("SearchEmptyViewFilteredSubtitle2", R.string.SearchEmptyViewFilteredSubtitle2));
                            }
                        }

                        if (!filterAndQueryIsSame) {
                            localTipChats.clear();
                            if (finalResultArray != null) {
                                localTipChats.addAll(finalResultArray);
                            }
                            if (query.length() >= 3 && (LocaleController.getString("SavedMessages", R.string.SavedMessages).toLowerCase().startsWith(query) || "saved messages".startsWith(query))) {
                                boolean found = false;
                                for (int i = 0; i < localTipChats.size(); i++) {
                                    if (localTipChats.get(i) instanceof TLRPC.User)
                                        if (UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser().id == ((TLRPC.User) localTipChats.get(i)).id) {
                                            found = true;
                                            break;
                                        }
                                }
                                if (!found) {
                                    localTipChats.add(0, UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser());
                                }
                            }
                            localTipDates.clear();
                            localTipDates.addAll(dateData);
                            updateFiltersView(TextUtils.isEmpty(currentDataQuery), localTipChats, localTipDates, true);
                        }
                        firstLoading = false;
                        View progressView = null;
                        int progressViewPosition = -1;
                        for (int i = 0; i < n; i++) {
                            View child = listView.getChildAt(i);
                            if (child instanceof FlickerLoadingView) {
                                progressView = child;
                                progressViewPosition = listView.getChildAdapterPosition(child);
                            }
                        }
                        final View finalProgressView = progressView;
                        if (progressView != null) {
                            listView.removeView(progressView);
                        }
                        if (loadingView.getVisibility() == View.VISIBLE && listView.getChildCount() <= 1 || progressView != null) {
                            int finalProgressViewPosition = progressViewPosition;
                            getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                                @Override
                                public boolean onPreDraw() {
                                    getViewTreeObserver().removeOnPreDrawListener(this);
                                    int n = listView.getChildCount();
                                    AnimatorSet animatorSet = new AnimatorSet();
                                    for (int i = 0; i < n; i++) {
                                        View child = listView.getChildAt(i);
                                        if (finalProgressView != null) {
                                            if (listView.getChildAdapterPosition(child) < finalProgressViewPosition) {
                                                continue;
                                            }
                                        }
                                        child.setAlpha(0);
                                        int s = Math.min(listView.getMeasuredHeight(), Math.max(0, child.getTop()));
                                        int delay = (int) ((s / (float) listView.getMeasuredHeight()) * 100);
                                        ObjectAnimator a = ObjectAnimator.ofFloat(child, View.ALPHA, 0, 1f);
                                        a.setStartDelay(delay);
                                        a.setDuration(200);
                                        animatorSet.playTogether(a);
                                    }
                                    animatorSet.addListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            accountInstance.getNotificationCenter().onAnimationFinish(animationIndex);
                                        }
                                    });
                                    animationIndex = accountInstance.getNotificationCenter().setAnimationInProgress(animationIndex, null);
                                    animatorSet.start();

                                    if (finalProgressView != null && finalProgressView.getParent() == null) {
                                        listView.addView(finalProgressView);
                                        RecyclerView.LayoutManager layoutManager = listView.getLayoutManager();
                                        if (layoutManager != null) {
                                            layoutManager.ignoreView(finalProgressView);
                                            Animator animator = ObjectAnimator.ofFloat(finalProgressView, ALPHA, finalProgressView.getAlpha(), 0);
                                            animator.addListener(new AnimatorListenerAdapter() {
                                                @Override
                                                public void onAnimationEnd(Animator animation) {
                                                    finalProgressView.setAlpha(1f);
                                                    layoutManager.stopIgnoringView(finalProgressView);
                                                    listView.removeView(finalProgressView);
                                                }
                                            });
                                            animator.start();
                                        }
                                    }
                                    return true;
                                }
                            });
                        }
                        notifyDataSetChanged();
                    });
                });
            }, (filterAndQueryIsSame && !messages.isEmpty()) ? 0 : 350);
            loadingView.setViewType(FlickerLoadingView.FILES_TYPE);
        }

        private void updateSearchResults(final ArrayList<ListItem> result, String query) {
            AndroidUtilities.runOnUIThread(() -> {
                if (searching) {
                    if (listView.getAdapter() != searchAdapter) {
                        listView.setAdapter(searchAdapter);
                    }
                }
                searchResult = result;
                notifyDataSetChanged();
            });
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder, int section, int row) {
            int type = holder.getItemViewType();
            return type == 1 || type == 4;
        }

        @Override
        public int getSectionCount() {
            int count = 2;
            if (!sections.isEmpty()) {
                count += sections.size() + (endReached ? 0 : 1);
            }
            return count;
        }

        @Override
        public Object getItem(int section, int position) {
            if (section == 0) {
                if (position < searchResult.size()) {
                    return searchResult.get(position);
                }
            } else {
                section--;
                if (section < sections.size()) {
                    ArrayList<MessageObject> arrayList = sectionArrays.get(sections.get(section));
                    if (arrayList != null) {
                        return arrayList.get(position - (section == 0 && searchResult.isEmpty() ? 0 : 1));
                    }
                }
            }
            return null;
        }

        @Override
        public int getCountForSection(int section) {
            if (section == 0) {
                return searchResult.size();
            }
            section--;
            if (section < sections.size()) {
                ArrayList<MessageObject> arrayList = sectionArrays.get(sections.get(section));
                if (arrayList != null) {
                    return arrayList.size() + (section == 0 && searchResult.isEmpty() ? 0 : 1);
                } else {
                    return 0;
                }
            }
            return 1;
        }

        @Override
        public View getSectionHeaderView(int section, View view) {
            GraySectionCell sectionCell = (GraySectionCell) view;
            if (sectionCell == null) {
                sectionCell = new GraySectionCell(mContext, resourcesProvider);
                sectionCell.setBackgroundColor(getThemedColor(Theme.key_graySection) & 0xf2ffffff);
            }
            if (section == 0 || section == 1 && searchResult.isEmpty()) {
                sectionCell.setAlpha(0f);
                return sectionCell;
            }
            section--;
            if (section < sections.size()) {
                sectionCell.setAlpha(1.0f);
                String name = sections.get(section);
                ArrayList<MessageObject> messageObjects = sectionArrays.get(name);
                if (messageObjects != null) {
                    MessageObject messageObject = messageObjects.get(0);
                    String str;
                    if (section == 0 && !searchResult.isEmpty()) {
                        str = LocaleController.getString("GlobalSearch", R.string.GlobalSearch);
                    } else {
                        str = LocaleController.formatSectionDate(messageObject.messageOwner.date);
                    }
                    sectionCell.setText(str);
                }
            }
            return view;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new GraySectionCell(mContext, resourcesProvider);
                    break;
                case 1:
                case 4:
                    SharedDocumentCell documentCell = new SharedDocumentCell(mContext, viewType == 1 ? SharedDocumentCell.VIEW_TYPE_PICKER : SharedDocumentCell.VIEW_TYPE_GLOBAL_SEARCH, resourcesProvider);
                    documentCell.setDrawDownloadIcon(false);
                    view = documentCell;
                    break;
                case 2:
                    FlickerLoadingView flickerLoadingView = new FlickerLoadingView(mContext, resourcesProvider);
                    flickerLoadingView.setViewType(FlickerLoadingView.FILES_TYPE);
                    flickerLoadingView.setIsSingleCell(true);
                    view = flickerLoadingView;
                    break;
                case 3:
                default:
                    view = new View(mContext);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(int section, int position, RecyclerView.ViewHolder holder) {
            int viewType = holder.getItemViewType();
            if (viewType == 2 || viewType == 3) {
                return;
            }
            switch (viewType) {
                case 0: {
                    section--;
                    String name = sections.get(section);
                    ArrayList<MessageObject> messageObjects = sectionArrays.get(name);
                    if (messageObjects == null) {
                        return;
                    }
                    MessageObject messageObject = messageObjects.get(0);
                    String str;
                    if (section == 0 && !searchResult.isEmpty()) {
                        str = LocaleController.getString("GlobalSearch", R.string.GlobalSearch);
                    } else {
                        str = LocaleController.formatSectionDate(messageObject.messageOwner.date);
                    }
                    ((GraySectionCell) holder.itemView).setText(str);
                    break;
                }
                case 1:
                case 4: {
                    SharedDocumentCell sharedDocumentCell = (SharedDocumentCell) holder.itemView;
                    if (section == 0) {
                        ListItem item = (ListItem) getItem(position);
                        SharedDocumentCell documentCell = (SharedDocumentCell) holder.itemView;
                        if (item.icon != 0) {
                            documentCell.setTextAndValueAndTypeAndThumb(item.title, item.subtitle, null, null, item.icon, false);
                        } else {
                            String type = item.ext.toUpperCase().substring(0, Math.min(item.ext.length(), 4));
                            documentCell.setTextAndValueAndTypeAndThumb(item.title, item.subtitle, type, item.thumb, 0, false);
                        }
                        if (item.file != null) {
                            documentCell.setChecked(selectedFiles.containsKey(item.file.toString()), !scrolling);
                        } else {
                            documentCell.setChecked(false, !scrolling);
                        }
                    } else {
                        section--;
                        if (section != 0 || !searchResult.isEmpty()) {
                            position--;
                        }
                        String name = sections.get(section);
                        ArrayList<MessageObject> messageObjects = sectionArrays.get(name);
                        if (messageObjects == null) {
                            return;
                        }
                        MessageObject messageObject = messageObjects.get(position);
                        boolean animated = sharedDocumentCell.getMessage() != null && sharedDocumentCell.getMessage().getId() == messageObject.getId();
                        sharedDocumentCell.setDocument(messageObject, position != messageObjects.size() - 1 || section == sections.size() - 1 && isLoading);
                        sharedDocumentCell.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                            @Override
                            public boolean onPreDraw() {
                                sharedDocumentCell.getViewTreeObserver().removeOnPreDrawListener(this);
                                if (parentAlert.actionBar.isActionModeShowed()) {
                                    messageHashIdTmp.set(messageObject.getId(), messageObject.getDialogId());
                                    sharedDocumentCell.setChecked(selectedMessages.containsKey(messageHashIdTmp), animated);
                                } else {
                                    sharedDocumentCell.setChecked(false, animated);
                                }
                                return true;
                            }
                        });
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int section, int position) {
            if (section == 0) {
                return 1;
            } else if (section == getSectionCount() - 1) {
                return 3;
            }
            section--;
            if (section < sections.size()) {
                if ((section != 0 || !searchResult.isEmpty()) && position == 0) {
                    return 0;
                } else {
                    return 4;
                }
            }
            return 2;
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            updateEmptyView();
        }

        @Override
        public String getLetter(int position) {
            return null;
        }

        @Override
        public void getPositionForScrollProgress(RecyclerListView listView, float progress, int[] position) {
            position[0] = 0;
            position[1] = 0;
        }
    }

    @Override
    ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        themeDescriptions.add(new ThemeDescription(searchItem.getSearchField(), ThemeDescription.FLAG_CURSORCOLOR, null, null, null, null, Theme.key_dialogTextBlack));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_dialogScrollGlow));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"dateTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOX, new Class[]{SharedDocumentCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkbox));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{SharedDocumentCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"thumbImageView"}, null, null, null, Theme.key_files_folderIcon));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_IMAGECOLOR | ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{SharedDocumentCell.class}, new String[]{"thumbImageView"}, null, null, null, Theme.key_files_folderIconBackground));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"extTextView"}, null, null, null, Theme.key_files_iconText));
        return themeDescriptions;
    }
}
