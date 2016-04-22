/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.messenger.AnimationCompat.AnimatorSetProxy;
import org.telegram.messenger.AnimationCompat.ObjectAnimatorProxy;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.SharedDocumentCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.NumberTextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.StringTokenizer;

public class DocumentSelectActivity extends BaseFragment {

    public interface DocumentSelectActivityDelegate {
        void didSelectFiles(DocumentSelectActivity activity, ArrayList<String> files);
        void startDocumentSelectActivity();
    }

    private ListView listView;
    private ListAdapter listAdapter;
    private NumberTextView selectedMessagesCountTextView;
    private TextView emptyView;

    private File currentDir;
    private ArrayList<ListItem> items = new ArrayList<>();
    private boolean receiverRegistered = false;
    private ArrayList<HistoryEntry> history = new ArrayList<>();
    private long sizeLimit = 1024 * 1024 * 1536;
    private DocumentSelectActivityDelegate delegate;
    private HashMap<String, ListItem> selectedFiles = new HashMap<>();
    private ArrayList<View> actionModeViews = new ArrayList<>();
    private boolean scrolling;

    private final static int done = 3;

    private class ListItem {
        int icon;
        String title;
        String subtitle = "";
        String ext = "";
        String thumb;
        File file;
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
                        FileLog.e("tmessages", e);
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
    public void onFragmentDestroy() {
        try {
            if (receiverRegistered) {
                ApplicationLoader.applicationContext.unregisterReceiver(receiver);
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
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
                        listView.invalidateViews();
                    } else {
                        finishFragment();
                    }
                } else if (id == done) {
                    if (delegate != null) {
                        ArrayList<String> files = new ArrayList<>();
                        files.addAll(selectedFiles.keySet());
                        delegate.didSelectFiles(DocumentSelectActivity.this, files);
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
        selectedMessagesCountTextView.setTextColor(0xff737373);
        selectedMessagesCountTextView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });
        actionMode.addView(selectedMessagesCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 65, 0, 0, 0));

        actionModeViews.add(actionMode.addItem(done, R.drawable.ic_ab_done_gray, Theme.ACTION_BAR_MODE_SELECTOR_COLOR, null, AndroidUtilities.dp(54)));

        fragmentView = getParentActivity().getLayoutInflater().inflate(R.layout.document_select_layout, null, false);
        listAdapter = new ListAdapter(context);
        emptyView = (TextView) fragmentView.findViewById(R.id.searchEmptyView);
        emptyView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });
        listView = (ListView) fragmentView.findViewById(R.id.listView);
        listView.setEmptyView(emptyView);
        listView.setAdapter(listAdapter);

        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                scrolling = scrollState != SCROLL_STATE_IDLE;
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int i, long id) {
                if (actionBar.isActionModeShowed() || i < 0 || i >= items.size()) {
                    return false;
                }
                ListItem item = items.get(i);
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
                    if (Build.VERSION.SDK_INT >= 11) {
                        AnimatorSetProxy animatorSet = new AnimatorSetProxy();
                        ArrayList<Object> animators = new ArrayList<>();
                        for (int a = 0; a < actionModeViews.size(); a++) {
                            View view2 = actionModeViews.get(a);
                            AndroidUtilities.clearDrawableAnimation(view2);
                            animators.add(ObjectAnimatorProxy.ofFloat(view2, "scaleY", 0.1f, 1.0f));
                        }
                        animatorSet.playTogether(animators);
                        animatorSet.setDuration(250);
                        animatorSet.start();
                    }
                    scrolling = false;
                    if (view instanceof SharedDocumentCell) {
                        ((SharedDocumentCell) view).setChecked(true, true);
                    }
                    actionBar.showActionMode();
                }
                return true;
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                if (i < 0 || i >= items.size()) {
                    return;
                }
                ListItem item = items.get(i);
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
                        listView.setSelectionFromTop(he.scrollItem, he.scrollOffset);
                    }
                } else if (file.isDirectory()) {
                    HistoryEntry he = new HistoryEntry();
                    he.scrollItem = listView.getFirstVisiblePosition();
                    he.scrollOffset = listView.getChildAt(0).getTop();
                    he.dir = currentDir;
                    he.title = actionBar.getTitle();
                    history.add(he);
                    if (!listFiles(file)) {
                        history.remove(he);
                        return;
                    }
                    actionBar.setTitle(item.title);
                    listView.setSelection(0);
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
            listView.setSelectionFromTop(he.scrollItem, he.scrollOffset);
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
        emptyView.setText(LocaleController.getString("NoFiles", R.string.NoFiles));
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
        boolean isDefaultPathRemovable = Build.VERSION.SDK_INT >= 9 && Environment.isExternalStorageRemovable();
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
                    FileLog.e("tmessages", line);
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
                                FileLog.e("tmessages", e);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
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
            FileLog.e("tmessages", e);
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
            FileLog.e("tmessages", e);
        }
        return path;
    }

    private class ListAdapter extends BaseFragmentAdapter {
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        public int getViewTypeCount() {
            return 2;
        }

        public int getItemViewType(int pos) {
            return items.get(pos).subtitle.length() > 0 ? 0 : 1;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = new SharedDocumentCell(mContext);
            }
            SharedDocumentCell textDetailCell = (SharedDocumentCell) convertView;
            ListItem item = items.get(position);
            if (item.icon != 0) {
                ((SharedDocumentCell) convertView).setTextAndValueAndTypeAndThumb(item.title, item.subtitle, null, null, item.icon);
            } else {
                String type = item.ext.toUpperCase().substring(0, Math.min(item.ext.length(), 4));
                ((SharedDocumentCell) convertView).setTextAndValueAndTypeAndThumb(item.title, item.subtitle, type, item.thumb, 0);
            }
            if (item.file != null && actionBar.isActionModeShowed()) {
                textDetailCell.setChecked(selectedFiles.containsKey(item.file.toString()), !scrolling);
            } else {
                textDetailCell.setChecked(false, !scrolling);
            }
            return convertView;
        }
    }
}
