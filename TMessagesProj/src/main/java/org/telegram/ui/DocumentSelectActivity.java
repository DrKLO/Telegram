/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Environment;
import android.os.StatFs;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.GraySectionCell;
import org.telegram.ui.Cells.SharedDocumentCell;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.RecyclerListView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.StringTokenizer;

public class DocumentSelectActivity extends BaseFragment {

    public interface DocumentSelectActivityDelegate {
        void didSelectFiles(DocumentSelectActivity activity, ArrayList<String> files);
        void startDocumentSelectActivity();
    }

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private NumberTextView selectedMessagesCountTextView;
    private EmptyTextProgressView emptyView;
    private LinearLayoutManager layoutManager;

    private File currentDir;
    private ArrayList<ListItem> items = new ArrayList<>();
    private boolean receiverRegistered = false;
    private ArrayList<HistoryEntry> history = new ArrayList<>();
    private long sizeLimit = 1024 * 1024 * 1536;
    private DocumentSelectActivityDelegate delegate;
    private HashMap<String, ListItem> selectedFiles = new HashMap<>();
    private ArrayList<View> actionModeViews = new ArrayList<>();
    private boolean scrolling;
    private ArrayList<ListItem> recentItems = new ArrayList<>();

    private final static int done = 3;

    private class ListItem {
        int icon;
        String title;
        String subtitle = "";
        String ext = "";
        String thumb;
        File file;
        long date;
    }

    private class HistoryEntry {
        int scrollItem, scrollOffset;
        File dir;
        String title;
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context arg0, Intent intent) {
            Runnable r = new Runnable() {
                public void run() {
                    try {
                        if (currentDir == null) {
                            listRoots();
                        } else {
                            listFiles(currentDir);
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            };
            if (Intent.ACTION_MEDIA_UNMOUNTED.equals(intent.getAction())) {
                listView.postDelayed(r, 1000);
            } else {
                r.run();
            }
        }
    };

    @Override
    public boolean onFragmentCreate() {
        loadRecentFiles();
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        try {
            if (receiverRegistered) {
                ApplicationLoader.applicationContext.unregisterReceiver(receiver);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
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

        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("SelectFile", R.string.SelectFile));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (actionBar.isActionModeShowed()) {
                        selectedFiles.clear();
                        actionBar.hideActionMode();
                        int count = listView.getChildCount();
                        for (int a = 0; a < count; a++) {
                            View child = listView.getChildAt(a);
                            if (child instanceof SharedDocumentCell) {
                                ((SharedDocumentCell) child).setChecked(false, true);
                            }
                        }
                    } else {
                        finishFragment();
                    }
                } else if (id == done) {
                    if (delegate != null) {
                        ArrayList<String> files = new ArrayList<>();
                        files.addAll(selectedFiles.keySet());
                        delegate.didSelectFiles(DocumentSelectActivity.this, files);
                        for (ListItem item : selectedFiles.values()) {
                            item.date = System.currentTimeMillis();
                        }
                    }
                }
            }
        });
        selectedFiles.clear();
        actionModeViews.clear();

        final ActionBarMenu actionMode = actionBar.createActionMode();

        selectedMessagesCountTextView = new NumberTextView(actionMode.getContext());
        selectedMessagesCountTextView.setTextSize(18);
        selectedMessagesCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedMessagesCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        selectedMessagesCountTextView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });
        actionMode.addView(selectedMessagesCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 65, 0, 0, 0));

        actionModeViews.add(actionMode.addItemWithWidth(done, R.drawable.ic_ab_done, AndroidUtilities.dp(54)));

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        emptyView = new EmptyTextProgressView(context);
        emptyView.showTextView();
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView = new RecyclerListView(context);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setEmptyView(emptyView);
        listView.setAdapter(listAdapter = new ListAdapter(context));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                scrolling = newState != RecyclerView.SCROLL_STATE_IDLE;
            }
        });

        listView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListener() {
            @Override
            public boolean onItemClick(View view, int position) {
                if (actionBar.isActionModeShowed()) {
                    return false;
                }
                ListItem item = listAdapter.getItem(position);
                if (item == null) {
                    return false;
                }
                File file = item.file;
                if (file != null && !file.isDirectory()) {
                    if (!file.canRead()) {
                        showErrorBox(LocaleController.getString("AccessError", R.string.AccessError));
                        return false;
                    }
                    if (sizeLimit != 0) {
                        if (file.length() > sizeLimit) {
                            showErrorBox(LocaleController.formatString("FileUploadLimit", R.string.FileUploadLimit, AndroidUtilities.formatFileSize(sizeLimit)));
                            return false;
                        }
                    }
                    if (file.length() == 0) {
                        return false;
                    }
                    selectedFiles.put(file.toString(), item);
                    selectedMessagesCountTextView.setNumber(1, false);
                    AnimatorSet animatorSet = new AnimatorSet();
                    ArrayList<Animator> animators = new ArrayList<>();
                    for (int a = 0; a < actionModeViews.size(); a++) {
                        View view2 = actionModeViews.get(a);
                        AndroidUtilities.clearDrawableAnimation(view2);
                        animators.add(ObjectAnimator.ofFloat(view2, "scaleY", 0.1f, 1.0f));
                    }
                    animatorSet.playTogether(animators);
                    animatorSet.setDuration(250);
                    animatorSet.start();
                    scrolling = false;
                    if (view instanceof SharedDocumentCell) {
                        ((SharedDocumentCell) view).setChecked(true, true);
                    }
                    actionBar.showActionMode();
                }
                return true;
            }
        });

        listView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                ListItem item = listAdapter.getItem(position);
                if (item == null) {
                    return;
                }
                File file = item.file;
                if (file == null) {
                    if (item.icon == R.drawable.ic_storage_gallery) {
                        if (delegate != null) {
                            delegate.startDocumentSelectActivity();
                        }
                        finishFragment(false);
                    } else {
                        HistoryEntry he = history.remove(history.size() - 1);
                        actionBar.setTitle(he.title);
                        if (he.dir != null) {
                            listFiles(he.dir);
                        } else {
                            listRoots();
                        }
                        layoutManager.scrollToPositionWithOffset(he.scrollItem, he.scrollOffset);
                    }
                } else if (file.isDirectory()) {
                    HistoryEntry he = new HistoryEntry();
                    he.scrollItem = layoutManager.findLastVisibleItemPosition();
                    View topView = layoutManager.findViewByPosition(he.scrollItem);
                    if (topView != null) {
                        he.scrollOffset = topView.getTop();
                    }
                    he.dir = currentDir;
                    he.title = actionBar.getTitle();
                    history.add(he);
                    if (!listFiles(file)) {
                        history.remove(he);
                        return;
                    }
                    actionBar.setTitle(item.title);
                } else {
                    if (!file.canRead()) {
                        showErrorBox(LocaleController.getString("AccessError", R.string.AccessError));
                        file = new File("/mnt/sdcard");
                    }
                    if (sizeLimit != 0) {
                        if (file.length() > sizeLimit) {
                            showErrorBox(LocaleController.formatString("FileUploadLimit", R.string.FileUploadLimit, AndroidUtilities.formatFileSize(sizeLimit)));
                            return;
                        }
                    }
                    if (file.length() == 0) {
                        return;
                    }
                    if (actionBar.isActionModeShowed()) {
                        if (selectedFiles.containsKey(file.toString())) {
                            selectedFiles.remove(file.toString());
                        } else {
                            selectedFiles.put(file.toString(), item);
                        }
                        if (selectedFiles.isEmpty()) {
                            actionBar.hideActionMode();
                        } else {
                            selectedMessagesCountTextView.setNumber(selectedFiles.size(), true);
                        }
                        scrolling = false;
                        if (view instanceof SharedDocumentCell) {
                            ((SharedDocumentCell) view).setChecked(selectedFiles.containsKey(item.file.toString()), true);
                        }
                    } else {
                        if (delegate != null) {
                            ArrayList<String> files = new ArrayList<>();
                            files.add(file.getAbsolutePath());
                            delegate.didSelectFiles(DocumentSelectActivity.this, files);
                        }
                    }
                }
            }
        });

        listRoots();

        return fragmentView;
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
            Collections.sort(recentItems, new Comparator<ListItem>() {
                @Override
                public int compare(ListItem o1, ListItem o2) {
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
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        fixLayoutInternal();
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (listView != null) {
            ViewTreeObserver obs = listView.getViewTreeObserver();
            obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    listView.getViewTreeObserver().removeOnPreDrawListener(this);
                    fixLayoutInternal();
                    return true;
                }
            });
        }
    }

    private void fixLayoutInternal() {
        if (selectedMessagesCountTextView == null) {
            return;
        }
        if (!AndroidUtilities.isTablet() && ApplicationLoader.applicationContext.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            selectedMessagesCountTextView.setTextSize(18);
        } else {
            selectedMessagesCountTextView.setTextSize(20);
        }
    }

    @Override
    public boolean onBackPressed() {
        if (history.size() > 0) {
            HistoryEntry he = history.remove(history.size() - 1);
            actionBar.setTitle(he.title);
            if (he.dir != null) {
                listFiles(he.dir);
            } else {
                listRoots();
            }
            layoutManager.scrollToPositionWithOffset(he.scrollItem, he.scrollOffset);
            return false;
        }
        return super.onBackPressed();
    }

    public void setDelegate(DocumentSelectActivityDelegate delegate) {
        this.delegate = delegate;
    }

    private boolean listFiles(File dir) {
        if (!dir.canRead()) {
            if (dir.getAbsolutePath().startsWith(Environment.getExternalStorageDirectory().toString())
                    || dir.getAbsolutePath().startsWith("/sdcard")
                    || dir.getAbsolutePath().startsWith("/mnt/sdcard")) {
                if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)
                        && !Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
                    currentDir = dir;
                    items.clear();
                    String state = Environment.getExternalStorageState();
                    if (Environment.MEDIA_SHARED.equals(state)) {
                        emptyView.setText(LocaleController.getString("UsbActive", R.string.UsbActive));
                    } else {
                        emptyView.setText(LocaleController.getString("NotMounted", R.string.NotMounted));
                    }
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
        } catch(Exception e) {
            showErrorBox(e.getLocalizedMessage());
            return false;
        }
        if (files == null) {
            showErrorBox(LocaleController.getString("UnknownError", R.string.UnknownError));
            return false;
        }
        currentDir = dir;
        items.clear();
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File lhs, File rhs) {
                if (lhs.isDirectory() != rhs.isDirectory()) {
                    return lhs.isDirectory() ? -1 : 1;
                }
                return lhs.getName().compareToIgnoreCase(rhs.getName());
                /*long lm = lhs.lastModified();
                long rm = lhs.lastModified();
                if (lm == rm) {
                    return 0;
                } else if (lm > rm) {
                    return -1;
                } else {
                    return 1;
                }*/
            }
        });
        for (int a = 0; a < files.length; a++) {
            File file = files[a];
            if (file.getName().indexOf('.') == 0) {
                continue;
            }
            ListItem item = new ListItem();
            item.title = file.getName();
            item.file = file;
            if (file.isDirectory()) {
                item.icon = R.drawable.ic_directory;
                item.subtitle = LocaleController.getString("Folder", R.string.Folder);
            } else {
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
        item.icon = R.drawable.ic_directory;
        item.file = null;
        items.add(0, item);
        AndroidUtilities.clearDrawableAnimation(listView);
        scrolling = true;
        listAdapter.notifyDataSetChanged();
        return true;
    }

    private void showErrorBox(String error) {
        if (getParentActivity() == null) {
            return;
        }
        new AlertDialog.Builder(getParentActivity()).setTitle(LocaleController.getString("AppName", R.string.AppName)).setMessage(error).setPositiveButton(LocaleController.getString("OK", R.string.OK), null).show();
    }

    @SuppressLint("NewApi")
    private void listRoots() {
        currentDir = null;
        items.clear();

        HashSet<String> paths = new HashSet<>();
        String defaultPath = Environment.getExternalStorageDirectory().getPath();
        boolean isDefaultPathRemovable = Environment.isExternalStorageRemovable();
        String defaultPathState = Environment.getExternalStorageState();
        if (defaultPathState.equals(Environment.MEDIA_MOUNTED) || defaultPathState.equals(Environment.MEDIA_MOUNTED_READ_ONLY)) {
            ListItem ext = new ListItem();
            if (Environment.isExternalStorageRemovable()) {
                ext.title = LocaleController.getString("SdCard", R.string.SdCard);
                ext.icon = R.drawable.ic_external_storage;
            } else {
                ext.title = LocaleController.getString("InternalStorage", R.string.InternalStorage);
                ext.icon = R.drawable.ic_storage;
            }
            ext.subtitle = getRootSubtitle(defaultPath);
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
                    FileLog.e(line);
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
                                item.icon = R.drawable.ic_external_storage;
                                item.subtitle = getRootSubtitle(path);
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
        ListItem fs = new ListItem();
        fs.title = "/";
        fs.subtitle = LocaleController.getString("SystemRoot", R.string.SystemRoot);
        fs.icon = R.drawable.ic_directory;
        fs.file = new File("/");
        items.add(fs);

        try {
            File telegramPath = new File(Environment.getExternalStorageDirectory(), "Telegram");
            if (telegramPath.exists()) {
                fs = new ListItem();
                fs.title = "Telegram";
                fs.subtitle = telegramPath.toString();
                fs.icon = R.drawable.ic_directory;
                fs.file = telegramPath;
                items.add(fs);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        fs = new ListItem();
        fs.title = LocaleController.getString("Gallery", R.string.Gallery);
        fs.subtitle = LocaleController.getString("GalleryInfo", R.string.GalleryInfo);
        fs.icon = R.drawable.ic_storage_gallery;
        fs.file = null;
        items.add(fs);

        AndroidUtilities.clearDrawableAnimation(listView);
        scrolling = true;
        listAdapter.notifyDataSetChanged();
    }

    private String getRootSubtitle(String path) {
        try {
            StatFs stat = new StatFs(path);
            long total = (long)stat.getBlockCount() * (long)stat.getBlockSize();
            long free = (long)stat.getAvailableBlocks() * (long)stat.getBlockSize();
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
            return holder.getItemViewType() != 0;
        }

        @Override
        public int getItemCount() {
            int count = items.size();
            if (history.isEmpty() && !recentItems.isEmpty()) {
                count += recentItems.size() + 1;
            }
            return count;
        }

        public ListItem getItem(int position) {
            if (position < items.size()) {
                return items.get(position);
            } else if (history.isEmpty() && !recentItems.isEmpty() && position != items.size()) {
                position -= items.size() + 1;
                if (position < recentItems.size()) {
                    return recentItems.get(position);
                }
            }
            return null;
        }

        @Override
        public int getItemViewType(int position) {
            return getItem(position) != null ? 1 : 0;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new GraySectionCell(mContext);
                    ((GraySectionCell) view).setText(LocaleController.getString("Recent", R.string.Recent).toUpperCase());
                    break;
                case 1:
                default:
                    view = new SharedDocumentCell(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 1) {
                ListItem item = getItem(position);
                SharedDocumentCell documentCell = (SharedDocumentCell) holder.itemView;
                if (item.icon != 0) {
                    documentCell.setTextAndValueAndTypeAndThumb(item.title, item.subtitle, null, null, item.icon);
                } else {
                    String type = item.ext.toUpperCase().substring(0, Math.min(item.ext.length(), 4));
                    documentCell.setTextAndValueAndTypeAndThumb(item.title, item.subtitle, type, item.thumb, 0);
                }
                if (item.file != null && actionBar.isActionModeShowed()) {
                    documentCell.setChecked(selectedFiles.containsKey(item.file.toString()), !scrolling);
                } else {
                    documentCell.setChecked(false, !scrolling);
                }
            }
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),

                new ThemeDescription(emptyView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_emptyListPlaceholder),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_BACKGROUND, null, null, null, null, Theme.key_actionBarActionModeDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_TOPBACKGROUND, null, null, null, null, Theme.key_actionBarActionModeDefaultTop),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_AM_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultSelector),

                new ThemeDescription(selectedMessagesCountTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_actionBarActionModeDefaultIcon),

                new ThemeDescription(listView, 0, new Class[]{GraySectionCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2),
                new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{GraySectionCell.class}, null, null, null, Theme.key_graySection),

                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText),
                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"dateTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOX, new Class[]{SharedDocumentCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkbox),
                new ThemeDescription(listView, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{SharedDocumentCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_checkboxCheck),
                new ThemeDescription(listView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"thumbImageView"}, null, null, null, Theme.key_files_folderIcon),
                new ThemeDescription(listView, ThemeDescription.FLAG_IMAGECOLOR | ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{SharedDocumentCell.class}, new String[]{"thumbImageView"}, null, null, null, Theme.key_files_folderIconBackground),
                new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{SharedDocumentCell.class}, new String[]{"extTextView"}, null, null, null, Theme.key_files_iconText),
        };
    }
}
