/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.SharedDocumentCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.PhotoPickerActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.StringTokenizer;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

public class ChatAttachAlertDocumentLayout extends ChatAttachAlert.AttachAlertLayout {

    public interface DocumentSelectActivityDelegate {
        void didSelectFiles(ArrayList<String> files, String caption, boolean notify, int scheduleDate);
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

    private boolean sendPressed;

    private boolean ignoreLayout;

    private LinearLayout emptyView;
    private ImageView emptyImageView;
    private TextView emptyTitleTextView;
    private TextView emptySubtitleTextView;

    private boolean hasFiles;

    private File currentDir;
    private ArrayList<ListItem> items = new ArrayList<>();
    private boolean receiverRegistered = false;
    private ArrayList<HistoryEntry> history = new ArrayList<>();
    private DocumentSelectActivityDelegate delegate;
    private HashMap<String, ListItem> selectedFiles = new HashMap<>();
    private ArrayList<String> selectedFilesOrder = new ArrayList<>();
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

    public ChatAttachAlertDocumentLayout(ChatAttachAlert alert, Context context, boolean music) {
        super(alert, context);
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
                searchAdapter.search(null);
            }

            @Override
            public void onTextChanged(EditText editText) {
                searchAdapter.search(editText.getText().toString());
            }
        });
        searchItem.setSearchFieldHint(LocaleController.getString("Search", R.string.Search));
        searchItem.setContentDescription(LocaleController.getString("Search", R.string.Search));
        EditTextBoldCursor editText = searchItem.getSearchField();
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintTextColor(Theme.getColor(Theme.key_chat_messagePanelHint));

        sortItem = menu.addItem(sort_button, sortByName ? R.drawable.contacts_sort_time : R.drawable.contacts_sort_name);
        sortItem.setContentDescription(LocaleController.getString("AccDescrContactSorting", R.string.AccDescrContactSorting));

        emptyView = new LinearLayout(context);
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        emptyView.setOnTouchListener((v, event) -> true);

        emptyImageView = new ImageView(context);
        emptyImageView.setImageResource(R.drawable.files_empty);
        emptyImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogEmptyImage), PorterDuff.Mode.MULTIPLY));
        emptyView.addView(emptyImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        emptyTitleTextView = new TextView(context);
        emptyTitleTextView.setTextColor(Theme.getColor(Theme.key_dialogEmptyText));
        emptyTitleTextView.setGravity(Gravity.CENTER);
        emptyTitleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        emptyTitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        emptyTitleTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), 0);
        emptyView.addView(emptyTitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 11, 0, 0));

        emptySubtitleTextView = new TextView(context);
        emptySubtitleTextView.setTextColor(Theme.getColor(Theme.key_dialogEmptyText));
        emptySubtitleTextView.setGravity(Gravity.CENTER);
        emptySubtitleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptySubtitleTextView.setPadding(AndroidUtilities.dp(40), 0, AndroidUtilities.dp(40), 0);
        emptyView.addView(emptySubtitleTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 6, 0, 0));

        listView = new RecyclerListView(context);
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
        listView.setEmptyView(emptyView);
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
            ListItem item;
            if (listView.getAdapter() == listAdapter) {
                item = listAdapter.getItem(position);
            } else {
                item = searchAdapter.getItem(position);
            }
            if (item == null) {
                return;
            }
            File file = item.file;
            if (file == null) {
                if (item.icon == R.drawable.files_gallery) {
                    HashMap<Object, Object> selectedPhotos = new HashMap<>();
                    ArrayList<Object> selectedPhotosOrder = new ArrayList<>();
                    ChatActivity chatActivity;
                    if (parentAlert.baseFragment instanceof ChatActivity) {
                        chatActivity = (ChatActivity) parentAlert.baseFragment;
                    } else {
                        chatActivity = null;
                    }

                    PhotoPickerActivity fragment = new PhotoPickerActivity(0, MediaController.allMediaAlbumEntry, selectedPhotos, selectedPhotosOrder, 0, chatActivity != null, chatActivity);
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
            } else {
                onItemClick(view, item);
            }
        });

        listView.setOnItemLongClickListener((view, position) -> {
            ListItem item;
            if (listView.getAdapter() == listAdapter) {
                item = listAdapter.getItem(position);
            } else {
                item = searchAdapter.getItem(position);
            }
            return onItemClick(view, item);
        });

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
        return selectedFiles.size();
    }

    @Override
    void sendSelectedItems(boolean notify, int scheduleDate) {
        if (selectedFiles.size() == 0 || delegate == null || sendPressed) {
            return;
        }
        sendPressed = true;
        ArrayList<String> files = new ArrayList<>(selectedFilesOrder);
        delegate.didSelectFiles(files, parentAlert.commentTextView.getText().toString(), notify, scheduleDate);
        parentAlert.dismiss();
    }

    private boolean onItemClick(View view, ListItem item) {
        if (item == null || item.file == null || item.file.isDirectory()) {
            return false;
        }
        String path = item.file.getAbsolutePath();
        boolean add;
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
            } else if (lhs.file == null && rhs.file == null) {
                return 0;
            }
            boolean isDir1 = lhs.file.isDirectory();
            boolean isDir2 = rhs.file.isDirectory();
            if (isDir1 != isDir2) {
                return isDir1 ? -1 : 1;
            } else if (isDir1 && isDir2 || sortByName) {
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
        emptyView.setTranslationY((emptyView.getMeasuredHeight() - getMeasuredHeight() + child.getTop()) / 2);
    }

    private void updateEmptyView() {
        if (searching) {
            emptyTitleTextView.setText(LocaleController.getString("NoFilesFound", R.string.NoFilesFound));
        } else {
            emptyTitleTextView.setText(LocaleController.getString("NoFilesFound", R.string.NoFilesFound));
            emptySubtitleTextView.setText(LocaleController.getString("NoFilesInfo", R.string.NoFilesInfo));
        }
        boolean visible;
        if (listView.getAdapter() == searchAdapter) {
            visible = searchAdapter.searchResult.isEmpty();
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
            searchItem.setVisibility(hasFiles ? View.VISIBLE : View.GONE);
        }
        if (history.isEmpty()) {
            searchItem.setSearchFieldHint(LocaleController.getString("SearchRecentFiles", R.string.SearchRecentFiles));
        } else {
            searchItem.setSearchFieldHint(LocaleController.getString("Search", R.string.Search));
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
        new AlertDialog.Builder(getContext()).setTitle(LocaleController.getString("AppName", R.string.AppName)).setMessage(error).setPositiveButton(LocaleController.getString("OK", R.string.OK), null).show();
    }

    @SuppressLint("NewApi")
    private void listRoots() {
        currentDir = null;
        hasFiles = false;
        items.clear();

        HashSet<String> paths = new HashSet<>();
        String defaultPath = Environment.getExternalStorageDirectory().getPath();
        boolean isDefaultPathRemovable = Environment.isExternalStorageRemovable();
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
        /*ListItem fs = new ListItem();
        fs.title = "/";
        fs.subtitle = LocaleController.getString("SystemRoot", R.string.SystemRoot);
        fs.icon = R.drawable.files_folder;
        fs.file = new File("/");
        items.add(fs);*/

        ListItem fs;
        try {
            File telegramPath = new File(Environment.getExternalStorageDirectory(), "Telegram");
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
                    view = new HeaderCell(mContext);
                    break;
                case 1:
                    view = new SharedDocumentCell(mContext, SharedDocumentCell.VIEW_TYPE_PICKER);
                    break;
                case 2:
                    view = new ShadowSectionCell(mContext);
                    Drawable drawable = Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow);
                    CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), drawable);
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

    public class SearchAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;
        private ArrayList<ListItem> searchResult = new ArrayList<>();
        private Runnable searchRunnable;
        private int reqId = 0;
        private int lastReqId;

        public SearchAdapter(Context context) {
            mContext = context;
        }

        public void search(final String query) {
            if (searchRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(searchRunnable);
                searchRunnable = null;
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
                AndroidUtilities.runOnUIThread(searchRunnable = () -> {
                    final ArrayList<ListItem> copy = new ArrayList<>(items);
                    if (history.isEmpty()) {
                        copy.addAll(0, recentItems);
                    }
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

                        updateSearchResults(resultArray, query);
                    });
                }, 300);
            }
        }

        private void updateSearchResults(final ArrayList<ListItem> result, String query) {
            AndroidUtilities.runOnUIThread(() -> {
                if (searching) {
                    if (listView.getAdapter() != searchAdapter) {
                        listView.setAdapter(searchAdapter);
                    }
                    emptySubtitleTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("NoFilesFoundInfo", R.string.NoFilesFoundInfo, query)));
                }
                searchResult = result;
                notifyDataSetChanged();
            });
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 0;
        }

        @Override
        public int getItemCount() {
            return searchResult.size() + 1;
        }

        public ListItem getItem(int position) {
            if (position < searchResult.size()) {
                return searchResult.get(position);
            }
            return null;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new SharedDocumentCell(mContext, SharedDocumentCell.VIEW_TYPE_PICKER);
                    break;
                case 1:
                default:
                    view = new View(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 0) {
                ListItem item = getItem(position);
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
            }
        }

        @Override
        public int getItemViewType(int i) {
            if (i < searchResult.size()) {
                return 0;
            }
            return 1;
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
            updateEmptyView();
        }
    }

    @Override
    ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        themeDescriptions.add(new ThemeDescription(searchItem.getSearchField(), ThemeDescription.FLAG_CURSORCOLOR, null, null, null, null, Theme.key_dialogTextBlack));
        themeDescriptions.add(new ThemeDescription(emptyImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_dialogEmptyImage));
        themeDescriptions.add(new ThemeDescription(emptyTitleTextView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_dialogEmptyText));
        themeDescriptions.add(new ThemeDescription(emptySubtitleTextView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_dialogEmptyText));

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
