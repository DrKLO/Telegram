/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.android.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Adapters.BaseFragmentAdapter;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.AnimationCompat.AnimatorSetProxy;
import org.telegram.ui.AnimationCompat.ObjectAnimatorProxy;
import org.telegram.ui.Cells.TextDetailDocumentsCell;
import org.telegram.ui.ActionBar.BaseFragment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

public class DocumentSelectActivity extends BaseFragment {

    public static abstract interface DocumentSelectActivityDelegate {
        public void didSelectFiles(DocumentSelectActivity activity, ArrayList<String> files);
        public void startDocumentSelectActivity();
    }

    private ListView listView;
    private ListAdapter listAdapter;
    private TextView selectedMessagesCountTextView;
    private TextView emptyView;

    private File currentDir;
    private ArrayList<ListItem> items = new ArrayList<ListItem>();
    private boolean receiverRegistered = false;
    private ArrayList<HistoryEntry> history = new ArrayList<HistoryEntry>();
    private long sizeLimit = 1024 * 1024 * 1024;
    private DocumentSelectActivityDelegate delegate;
    private HashMap<String, ListItem> selectedFiles = new HashMap<String, ListItem>();
    private ArrayList<View> actionModeViews = new ArrayList<View>();
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
                getParentActivity().unregisterReceiver(receiver);
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        super.onFragmentDestroy();
    }

    @Override
    public View createView(LayoutInflater inflater, ViewGroup container) {
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
            getParentActivity().registerReceiver(receiver, filter);
        }

        if (fragmentView == null) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setAllowOverlayTitle(true);
            actionBar.setTitle(LocaleController.getString("SelectFile", R.string.SelectFile));
            actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == 1) {
                        if (delegate != null) {
                            delegate.startDocumentSelectActivity();
                        }
                        finishFragment(false);
                    } else if (id == -2) {
                        selectedFiles.clear();
                        actionBar.hideActionMode();
                        listView.invalidateViews();
                    } else if (id == done) {
                        if (delegate != null) {
                            ArrayList<String> files = new ArrayList<String>();
                            files.addAll(selectedFiles.keySet());
                            delegate.didSelectFiles(DocumentSelectActivity.this, files);
                        }
                    }
                }
            });
            ActionBarMenu menu = actionBar.createMenu();
            final ActionBarMenuItem item = menu.addItem(1, R.drawable.ic_ab_other);

            selectedFiles.clear();
            actionModeViews.clear();

            final ActionBarMenu actionMode = actionBar.createActionMode();
            actionModeViews.add(actionMode.addItem(-2, R.drawable.ic_ab_back_grey, R.drawable.bar_selector_mode, null, AndroidUtilities.dp(54)));

            selectedMessagesCountTextView = new TextView(actionMode.getContext());
            selectedMessagesCountTextView.setTextSize(18);
            selectedMessagesCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            selectedMessagesCountTextView.setTextColor(0xff737373);
            selectedMessagesCountTextView.setSingleLine(true);
            selectedMessagesCountTextView.setLines(1);
            selectedMessagesCountTextView.setEllipsize(TextUtils.TruncateAt.END);
            selectedMessagesCountTextView.setPadding(AndroidUtilities.dp(11), 0, 0, AndroidUtilities.dp(2));
            selectedMessagesCountTextView.setGravity(Gravity.CENTER_VERTICAL);
            selectedMessagesCountTextView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });
            actionMode.addView(selectedMessagesCountTextView);
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams)selectedMessagesCountTextView.getLayoutParams();
            layoutParams.weight = 1;
            layoutParams.width = 0;
            layoutParams.height = LinearLayout.LayoutParams.MATCH_PARENT;
            selectedMessagesCountTextView.setLayoutParams(layoutParams);

            actionModeViews.add(actionMode.addItem(done, R.drawable.ic_ab_done_gray, R.drawable.bar_selector_mode, null, AndroidUtilities.dp(54)));

            fragmentView = inflater.inflate(R.layout.document_select_layout, container, false);
            listAdapter = new ListAdapter(getParentActivity());
            emptyView = (TextView)fragmentView.findViewById(R.id.searchEmptyView);
            emptyView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });
            listView = (ListView)fragmentView.findViewById(R.id.listView);
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
                                showErrorBox(LocaleController.formatString("FileUploadLimit", R.string.FileUploadLimit, Utilities.formatFileSize(sizeLimit)));
                                return false;
                            }
                        }
                        if (file.length() == 0) {
                            return false;
                        }
                        selectedFiles.put(file.toString(), item);
                        selectedMessagesCountTextView.setText(String.format("%d", selectedFiles.size()));
                        if (Build.VERSION.SDK_INT >= 11) {
                            AnimatorSetProxy animatorSet = new AnimatorSetProxy();
                            ArrayList<Object> animators = new ArrayList<Object>();
                            for (int a = 0; a < actionModeViews.size(); a++) {
                                View view2 = actionModeViews.get(a);
                                AndroidUtilities.clearDrawableAnimation(view2);
                                if (a < 1) {
                                    animators.add(ObjectAnimatorProxy.ofFloat(view2, "translationX", -AndroidUtilities.dp(56), 0));
                                } else {
                                    animators.add(ObjectAnimatorProxy.ofFloat(view2, "scaleY", 0.1f, 1.0f));
                                }
                            }
                            animatorSet.playTogether(animators);
                            animatorSet.setDuration(250);
                            animatorSet.start();
                        }
                        scrolling = false;
                        if (view instanceof TextDetailDocumentsCell) {
                            ((TextDetailDocumentsCell) view).setChecked(true, true);
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
                        HistoryEntry he = history.remove(history.size() - 1);
                        actionBar.setTitle(he.title);
                        if (he.dir != null) {
                            listFiles(he.dir);
                        } else {
                            listRoots();
                        }
                        listView.setSelectionFromTop(he.scrollItem, he.scrollOffset);
                    } else if (file.isDirectory()) {
                        HistoryEntry he = new HistoryEntry();
                        he.scrollItem = listView.getFirstVisiblePosition();
                        he.scrollOffset = listView.getChildAt(0).getTop();
                        he.dir = currentDir;
                        he.title = actionBar.getTitle().toString();
                        if (!listFiles(file)) {
                            return;
                        }
                        history.add(he);
                        actionBar.setTitle(item.title);
                        listView.setSelection(0);
                    } else {
                        if (!file.canRead()) {
                            showErrorBox(LocaleController.getString("AccessError", R.string.AccessError));
                            return;
                        }
                        if (sizeLimit != 0) {
                            if (file.length() > sizeLimit) {
                                showErrorBox(LocaleController.formatString("FileUploadLimit", R.string.FileUploadLimit, Utilities.formatFileSize(sizeLimit)));
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
                                selectedMessagesCountTextView.setText(String.format("%d", selectedFiles.size()));
                            }
                            scrolling = false;
                            if (view instanceof TextDetailDocumentsCell) {
                                ((TextDetailDocumentsCell) view).setChecked(selectedFiles.containsKey(item.file.toString()), true);
                            }
                        } else {
                            if (delegate != null) {
                                ArrayList<String> files = new ArrayList<String>();
                                files.add(file.getAbsolutePath());
                                delegate.didSelectFiles(DocumentSelectActivity.this, files);
                            }
                        }
                    }
                }
            });

            listRoots();
        } else {
            ViewGroup parent = (ViewGroup)fragmentView.getParent();
            if (parent != null) {
                parent.removeView(fragmentView);
            }
        }
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
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
        File[] files = null;
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
        for (File file : files) {
            if (file.getName().startsWith(".")) {
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
                item.subtitle = Utilities.formatFileSize(file.length());
                fname = fname.toLowerCase();
                if (fname.endsWith(".jpg") || fname.endsWith(".png") || fname.endsWith(".gif") || fname.endsWith(".jpeg")) {
                    item.thumb = file.getAbsolutePath();
                }
            }
            items.add(item);
        }
        ListItem item = new ListItem();
        item.title = "..";
        item.subtitle = LocaleController.getString("Folder", R.string.Folder);
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
        new AlertDialog.Builder(getParentActivity()).setTitle(LocaleController.getString("AppName", R.string.AppName)).setMessage(error).setPositiveButton(R.string.OK, null).show();
    }

    private void listRoots() {
        currentDir = null;
        items.clear();
        String extStorage = Environment.getExternalStorageDirectory().getAbsolutePath();
        ListItem ext = new ListItem();
        if (Build.VERSION.SDK_INT < 9 || Environment.isExternalStorageRemovable()) {
            ext.title = LocaleController.getString("SdCard", R.string.SdCard);
        } else {
            ext.title = LocaleController.getString("InternalStorage", R.string.InternalStorage);
        }
        ext.icon = Build.VERSION.SDK_INT < 9 || Environment.isExternalStorageRemovable() ? R.drawable.ic_external_storage : R.drawable.ic_storage;
        ext.subtitle = getRootSubtitle(extStorage);
        ext.file = Environment.getExternalStorageDirectory();
        items.add(ext);
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/mounts"));
            String line;
            HashMap<String, ArrayList<String>> aliases = new HashMap<String, ArrayList<String>>();
            ArrayList<String> result = new ArrayList<String>();
            String extDevice = null;
            while ((line = reader.readLine()) != null) {
                if ((!line.contains("/mnt") && !line.contains("/storage") && !line.contains("/sdcard")) || line.contains("asec") || line.contains("tmpfs") || line.contains("none")) {
                    continue;
                }
                String[] info = line.split(" ");
                if (!aliases.containsKey(info[0])) {
                    aliases.put(info[0], new ArrayList<String>());
                }
                aliases.get(info[0]).add(info[1]);
                if (info[1].equals(extStorage)) {
                    extDevice=info[0];
                }
                result.add(info[1]);
            }
            reader.close();
            if (extDevice != null) {
                result.removeAll(aliases.get(extDevice));
                for (String path : result) {
                    try {
                        ListItem item = new ListItem();
                        if (path.toLowerCase().contains("sd")) {
                            ext.title = LocaleController.getString("SdCard", R.string.SdCard);
                        } else {
                            ext.title = LocaleController.getString("ExternalStorage", R.string.ExternalStorage);
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
        } catch (Exception e) {
            FileLog.e("tmessages", e);
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

        AndroidUtilities.clearDrawableAnimation(listView);
        scrolling = true;
        listAdapter.notifyDataSetChanged();
    }

    private String getRootSubtitle(String path) {
        StatFs stat = new StatFs(path);
        long total = (long)stat.getBlockCount() * (long)stat.getBlockSize();
        long free = (long)stat.getAvailableBlocks() * (long)stat.getBlockSize();
        if (total == 0) {
            return "";
        }
        return LocaleController.formatString("FreeOfTotal", R.string.FreeOfTotal, Utilities.formatFileSize(free), Utilities.formatFileSize(total));
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
                convertView = new TextDetailDocumentsCell(mContext);
            }
            TextDetailDocumentsCell textDetailCell = (TextDetailDocumentsCell) convertView;
            ListItem item = items.get(position);
            if (item.icon != 0) {
                ((TextDetailDocumentsCell) convertView).setTextAndValueAndTypeAndThumb(item.title, item.subtitle, null, null, item.icon);
            } else {
                String type = item.ext.toUpperCase().substring(0, Math.min(item.ext.length(), 4));
                ((TextDetailDocumentsCell) convertView).setTextAndValueAndTypeAndThumb(item.title, item.subtitle, type, item.thumb, 0);
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
