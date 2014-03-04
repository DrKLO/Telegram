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
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.support.v7.app.ActionBar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Views.BackupImageView;
import org.telegram.ui.Views.BaseFragment;
import org.telegram.ui.Views.OnSwipeTouchListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;

public class DocumentSelectActivity extends BaseFragment {

    public static abstract interface DocumentSelectActivityDelegate {
        public void didSelectFile(DocumentSelectActivity activity, String path, String name, String ext, long size);
    }

    private ListView listView;
    private ListAdapter listAdapter;
    private File currentDir;
    private TextView emptyView;
    private ArrayList<ListItem> items = new ArrayList<ListItem>();
    private boolean receiverRegistered = false;
    private ArrayList<HistoryEntry> history = new ArrayList<HistoryEntry>();
    private long sizeLimit = 1024 * 1024 * 1024;
    public DocumentSelectActivityDelegate delegate;

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
                        if (currentDir == null){
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
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        try {
            if (receiverRegistered) {
                parentActivity.unregisterReceiver(receiver);
            }
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        super.onFragmentDestroy();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
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
            parentActivity.registerReceiver(receiver, filter);
        }

        if (fragmentView == null) {
            fragmentView = inflater.inflate(R.layout.document_select_layout, container, false);
            listAdapter = new ListAdapter(parentActivity);
            emptyView = (TextView)fragmentView.findViewById(R.id.searchEmptyView);
            listView = (ListView)fragmentView.findViewById(R.id.listView);
            listView.setEmptyView(emptyView);
            listView.setAdapter(listAdapter);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    ListItem item = items.get(i);
                    File file = item.file;
                    if (file.isDirectory()) {
                        HistoryEntry he = new HistoryEntry();
                        he.scrollItem = listView.getFirstVisiblePosition();
                        he.scrollOffset = listView.getChildAt(0).getTop();
                        he.dir = currentDir;
                        ActionBar actionBar = parentActivity.getSupportActionBar();
                        he.title = actionBar.getTitle().toString();
                        if (!listFiles(file)){
                            return;
                        }
                        history.add(he);
                        actionBar.setTitle(item.title);
                        listView.setSelection(0);
                    } else {
                        if (!file.canRead()) {
                            showErrorBox(getString(R.string.AccessError));
                            return;
                        }
                        if (sizeLimit != 0) {
                            if (file.length() > sizeLimit) {
                                showErrorBox(getString(R.string.FileUploadLimit, Utilities.formatFileSize(sizeLimit)));
                                return;
                            }
                        }
                        if (file.length() == 0) {
                            return;
                        }
                        if (delegate != null) {
                            delegate.didSelectFile(DocumentSelectActivity.this, file.getAbsolutePath(), item.title, item.ext, file.length());
                        }
                    }
                }
            });

            listView.setOnTouchListener(new OnSwipeTouchListener() {
                public void onSwipeRight() {
                    finishFragment(true);
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
    public void applySelfActionBar() {
        if (parentActivity == null) {
            return;
        }
        ActionBar actionBar = parentActivity.getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setDisplayShowCustomEnabled(false);
        actionBar.setSubtitle(null);
        actionBar.setCustomView(null);
        actionBar.setTitle(getStringEntry(R.string.SelectFile));

        TextView title = (TextView)parentActivity.findViewById(R.id.action_bar_title);
        if (title == null) {
            final int subtitleId = parentActivity.getResources().getIdentifier("action_bar_title", "id", "android");
            title = (TextView)parentActivity.findViewById(subtitleId);
        }
        if (title != null) {
            title.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            title.setCompoundDrawablePadding(0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isFinish) {
            return;
        }
        if (getActivity() == null) {
            return;
        }
        if (!firstStart && listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
        firstStart = false;
        ((LaunchActivity)parentActivity).showActionBar();
        ((LaunchActivity)parentActivity).updateActionBar();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case android.R.id.home:
                finishFragment();
                break;
        }
        return true;
    }

    @Override
    public boolean onBackPressed() {
        if (history.size() > 0){
            HistoryEntry he = history.remove(history.size() - 1);
            ActionBar actionBar = parentActivity.getSupportActionBar();
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
                    if (Environment.MEDIA_SHARED.equals(state)){
                        emptyView.setText(R.string.UsbActive);
                    } else {
                        emptyView.setText(R.string.NotMounted);
                    }
                    listAdapter.notifyDataSetChanged();
                    return true;
                }
            }
            showErrorBox(getString(R.string.AccessError));
            return false;
        }
        emptyView.setText(R.string.NoFiles);
        File[] files = null;
        try {
            files = dir.listFiles();
        } catch(Exception e) {
            showErrorBox(e.getLocalizedMessage());
            return false;
        }
        if (files == null) {
            showErrorBox(getString(R.string.UnknownError));
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
        listAdapter.notifyDataSetChanged();
        return true;
    }

    private void showErrorBox(String error){
        new AlertDialog.Builder(parentActivity)
                .setTitle(R.string.AppName)
                .setMessage(error)
                .setPositiveButton(R.string.OK, null)
                .show();
    }

    private void listRoots() {
        currentDir = null;
        items.clear();
        String extStorage = Environment.getExternalStorageDirectory().getAbsolutePath();
        ListItem ext = new ListItem();
        ext.title = getString(Build.VERSION.SDK_INT < 9 || Environment.isExternalStorageRemovable() ? R.string.SdCard : R.string.InternalStorage);
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
                        boolean isSd = path.toLowerCase().contains("sd");
                        ListItem item = new ListItem();
                        item.title = getString(isSd ? R.string.SdCard : R.string.ExternalStorage);
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
        fs.subtitle = getString(R.string.SystemRoot);
        fs.icon = R.drawable.ic_directory;
        fs.file = new File("/");
        items.add(fs);
        listAdapter.notifyDataSetChanged();
    }

    private String getRootSubtitle(String path){
        StatFs stat = new StatFs(path);
        long total = (long)stat.getBlockCount() * (long)stat.getBlockSize();
        long free = (long)stat.getAvailableBlocks() * (long)stat.getBlockSize();
        if (total == 0) {
            return "";
        }
        return getString(R.string.FreeOfTotal, Utilities.formatFileSize(free), Utilities.formatFileSize(total));
    }

    private class ListAdapter extends BaseAdapter {
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

        public int getViewTypeCount(){
            return 2;
        }

        public int getItemViewType(int pos){
            return items.get(pos).subtitle.length() > 0 ? 0 : 1;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            ListItem item = items.get(position);
            if (v == null) {
                v = View.inflate(mContext, R.layout.document_item, null);
                if (item.subtitle.length() == 0) {
                    v.findViewById(R.id.docs_item_info).setVisibility(View.GONE);
                }
            }
            TextView typeTextView = (TextView)v.findViewById(R.id.docs_item_type);
            ((TextView)v.findViewById(R.id.docs_item_title)).setText(item.title);

            ((TextView)v.findViewById(R.id.docs_item_info)).setText(item.subtitle);
            BackupImageView imageView = (BackupImageView)v.findViewById(R.id.docs_item_thumb);
            if (item.thumb != null) {
                imageView.setImageBitmap(null);
                typeTextView.setText(item.ext.toUpperCase().substring(0, Math.min(item.ext.length(), 4)));
                imageView.setImage(item.thumb, "55_42", 0);
                imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imageView.setVisibility(View.VISIBLE);
                typeTextView.setVisibility(View.VISIBLE);
            } else if (item.icon != 0) {
                imageView.setImageResource(item.icon);
                imageView.setScaleType(ImageView.ScaleType.CENTER);
                imageView.setVisibility(View.VISIBLE);
                typeTextView.setVisibility(View.GONE);
            } else {
                typeTextView.setText(item.ext.toUpperCase().substring(0, Math.min(item.ext.length(), 4)));
                imageView.setVisibility(View.GONE);
                typeTextView.setVisibility(View.VISIBLE);
            }
            return v;
        }
    }
}
