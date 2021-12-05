/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckBoxCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SlideChooseView;
import org.telegram.ui.Components.StorageDiagramView;
import org.telegram.ui.Components.StroageUsageView;
import org.telegram.ui.Components.UndoView;

import java.io.File;
import java.util.ArrayList;

public class CacheControlActivity extends BaseFragment {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    @SuppressWarnings("FieldCanBeLocal")
    private LinearLayoutManager layoutManager;

    private int databaseRow;
    private int databaseInfoRow;
    private int keepMediaHeaderRow;
    private int keepMediaInfoRow;
    private int cacheInfoRow;
    private int deviseStorageHeaderRow;
    private int storageUsageRow;
    private int keepMediaChooserRow;
    private int rowCount;

    private long databaseSize = -1;
    private long cacheSize = -1;
    private long documentsSize = -1;
    private long audioSize = -1;
    private long musicSize = -1;
    private long photoSize = -1;
    private long videoSize = -1;
    private long stickersSize = -1;
    private long totalSize = -1;
    private long totalDeviceSize = -1;
    private long totalDeviceFreeSize = -1;
    private StorageDiagramView.ClearViewData[] clearViewData = new StorageDiagramView.ClearViewData[7];
    private boolean calculating = true;

    private volatile boolean canceled = false;

    private View bottomSheetView;
    private BottomSheet bottomSheet;
    private View actionTextView;

    private UndoView cacheRemovedTooltip;

    long fragmentCreateTime;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        rowCount = 0;

        keepMediaHeaderRow = rowCount++;
        keepMediaChooserRow = rowCount++;
        keepMediaInfoRow = rowCount++;
        deviseStorageHeaderRow = rowCount++;
        storageUsageRow = rowCount++;

        cacheInfoRow = rowCount++;
        databaseRow = rowCount++;
        databaseInfoRow = rowCount++;

        databaseSize = MessagesStorage.getInstance(currentAccount).getDatabaseSize();

        Utilities.globalQueue.postRunnable(() -> {
            cacheSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), 0);
            if (canceled) {
                return;
            }
            photoSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_IMAGE), 0);
            if (canceled) {
                return;
            }
            videoSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_VIDEO), 0);
            if (canceled) {
                return;
            }
            documentsSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_DOCUMENT), 1);
            if (canceled) {
                return;
            }
            musicSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_DOCUMENT), 2);
            if (canceled) {
                return;
            }
            stickersSize = getDirectorySize(new File(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), "acache"), 0);
            if (canceled) {
                return;
            }
            audioSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_AUDIO), 0);
            totalSize = cacheSize + videoSize + audioSize + photoSize + documentsSize + musicSize + stickersSize;

            File path;
            if (Build.VERSION.SDK_INT >= 19) {
                ArrayList<File> storageDirs = AndroidUtilities.getRootDirs();
                String dir = (path = storageDirs.get(0)).getAbsolutePath();
                if (!TextUtils.isEmpty(SharedConfig.storageCacheDir)) {
                    for (int a = 0, N = storageDirs.size(); a < N; a++) {
                        File file = storageDirs.get(a);
                        if (file.getAbsolutePath().startsWith(SharedConfig.storageCacheDir)) {
                            path = file;
                            break;
                        }
                    }
                }
            } else {
                path = new File(SharedConfig.storageCacheDir);
            }
            try {
                StatFs stat = new StatFs(path.getPath());
                long blockSize;
                long blockSizeExternal;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    blockSize = stat.getBlockSizeLong();
                } else {
                    blockSize = stat.getBlockSize();
                }
                long availableBlocks;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    availableBlocks = stat.getAvailableBlocksLong();
                } else {
                    availableBlocks = stat.getAvailableBlocks();
                }
                long blocksTotal;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    blocksTotal = stat.getBlockCountLong();
                } else {
                    blocksTotal = stat.getBlockCount();
                }

                totalDeviceSize = blocksTotal * blockSize;
                totalDeviceFreeSize = availableBlocks * blockSize;
            } catch (Exception e) {
                FileLog.e(e);
            }

            AndroidUtilities.runOnUIThread(() -> {
                calculating = false;
                updateStorageUsageRow();
            });
        });

        fragmentCreateTime = System.currentTimeMillis();
        return true;
    }

    private void updateStorageUsageRow() {
        View view = layoutManager.findViewByPosition(storageUsageRow);
        if (view instanceof StroageUsageView) {
            StroageUsageView stroageUsageView = ((StroageUsageView) view);
            long currentTime =  System.currentTimeMillis();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && currentTime - fragmentCreateTime > 250) {
                TransitionSet transition = new TransitionSet();
                ChangeBounds changeBounds = new ChangeBounds();
                changeBounds.setDuration(250);
                changeBounds.excludeTarget(stroageUsageView.legendLayout,true);
                Fade in = new Fade(Fade.IN);
                in.setDuration(290);
                transition
                        .addTransition(new Fade(Fade.OUT).setDuration(250))
                        .addTransition(changeBounds)
                        .addTransition(in);
                transition.setOrdering(TransitionSet.ORDERING_TOGETHER);
                transition.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                TransitionManager.beginDelayedTransition(listView, transition);
            }
            stroageUsageView.setStorageUsage(calculating, databaseSize, totalSize, totalDeviceFreeSize, totalDeviceSize);
            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(storageUsageRow);
            if (holder != null) {
                stroageUsageView.setEnabled(listAdapter.isEnabled(holder));
            }
        } else {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        canceled = true;
    }

    private long getDirectorySize(File dir, int documentsMusicType) {
        if (dir == null || canceled) {
            return 0;
        }
        long size = 0;
        if (dir.isDirectory()) {
            size = Utilities.getDirSize(dir.getAbsolutePath(), documentsMusicType, false);
        } else if (dir.isFile()) {
            size += dir.length();
        }
        return size;
    }

    private void cleanupFolders() {
        final AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
        progressDialog.setCanCacnel(false);
        progressDialog.showDelayed(500);
        Utilities.globalQueue.postRunnable(() -> {
            boolean imagesCleared = false;
            long clearedSize = 0;
            for (int a = 0; a < 7; a++) {
                if (clearViewData[a] == null || !clearViewData[a].clear) {
                    continue;
                }
                int type = -1;
                int documentsMusicType = 0;
                if (a == 0) {
                    type = FileLoader.MEDIA_DIR_IMAGE;
                    clearedSize += photoSize;
                } else if (a == 1) {
                    type = FileLoader.MEDIA_DIR_VIDEO;
                    clearedSize += videoSize;
                } else if (a == 2) {
                    type = FileLoader.MEDIA_DIR_DOCUMENT;
                    documentsMusicType = 1;
                    clearedSize += documentsSize;
                } else if (a == 3) {
                    type = FileLoader.MEDIA_DIR_DOCUMENT;
                    documentsMusicType = 2;
                    clearedSize += musicSize;
                } else if (a == 4) {
                    type = FileLoader.MEDIA_DIR_AUDIO;
                    clearedSize += audioSize;
                } else if (a == 5) {
                    type = 100;
                    clearedSize += stickersSize;
                } else if (a == 6) {
                    clearedSize += cacheSize;
                    type = FileLoader.MEDIA_DIR_CACHE;
                }
                if (type == -1) {
                    continue;
                }
                File file;
                if (type == 100) {
                    file = new File(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), "acache");
                } else {
                    file = FileLoader.checkDirectory(type);
                }
                if (file != null) {
                    Utilities.clearDir(file.getAbsolutePath(), documentsMusicType, Long.MAX_VALUE, false);
                }
                if (type == FileLoader.MEDIA_DIR_CACHE) {
                    cacheSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), documentsMusicType);
                    imagesCleared = true;
                } else if (type == FileLoader.MEDIA_DIR_AUDIO) {
                    audioSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_AUDIO), documentsMusicType);
                } else if (type == FileLoader.MEDIA_DIR_DOCUMENT) {
                    if (documentsMusicType == 1) {
                        documentsSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_DOCUMENT), documentsMusicType);
                    } else {
                        musicSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_DOCUMENT), documentsMusicType);
                    }
                } else if (type == FileLoader.MEDIA_DIR_IMAGE) {
                    imagesCleared = true;
                    photoSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_IMAGE), documentsMusicType);
                } else if (type == FileLoader.MEDIA_DIR_VIDEO) {
                    videoSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_VIDEO), documentsMusicType);
                } else if (type == 100) {
                    imagesCleared = true;
                    stickersSize = getDirectorySize(new File(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), "acache"), documentsMusicType);
                }
            }
            final boolean imagesClearedFinal = imagesCleared;
            totalSize = cacheSize + videoSize + audioSize + photoSize + documentsSize + musicSize + stickersSize;

            File path = Environment.getDataDirectory();
            StatFs stat = new StatFs(path.getPath());
            long blockSize;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                blockSize = stat.getBlockSizeLong();
            } else {
                blockSize = stat.getBlockSize();
            }
            long availableBlocks;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                availableBlocks = stat.getAvailableBlocksLong();
            } else {
                availableBlocks = stat.getAvailableBlocks();
            }
            long blocksTotal;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                blocksTotal = stat.getBlockCountLong();
            } else {
                blocksTotal = stat.getBlockCount();
            }

            totalDeviceSize = blocksTotal * blockSize;
            totalDeviceFreeSize = availableBlocks * blockSize;
            long finalClearedSize = clearedSize;
            AndroidUtilities.runOnUIThread(() -> {
                if (imagesClearedFinal) {
                    ImageLoader.getInstance().clearMemory();
                }
                if (listAdapter != null) {
                    updateStorageUsageRow();
                }
                try {
                    progressDialog.dismiss();
                } catch (Exception e) {
                    FileLog.e(e);
                }

                cacheRemovedTooltip.setInfoText(LocaleController.formatString("CacheWasCleared", R.string.CacheWasCleared, AndroidUtilities.formatFileSize(finalClearedSize)));
                cacheRemovedTooltip.showWithAction(0, UndoView.ACTION_CACHE_WAS_CLEARED, null, null);
            });
        });
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("StorageUsage", R.string.StorageUsage));
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
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (getParentActivity() == null) {
                return;
            }
            if (position == databaseRow) {
                clearDatabase();
            } else if (position == storageUsageRow) {
                if (totalSize <= 0 || getParentActivity() == null) {
                    return;
                }
                bottomSheet = new BottomSheet(getParentActivity(), false) {
                    @Override
                    protected boolean canDismissWithSwipe() {
                        return false;
                    }
                };
                bottomSheet.setAllowNestedScroll(true);
                bottomSheet.setApplyBottomPadding(false);
                LinearLayout linearLayout = new LinearLayout(getParentActivity());
                bottomSheetView = linearLayout;
                linearLayout.setOrientation(LinearLayout.VERTICAL);
                StorageDiagramView circleDiagramView = new StorageDiagramView(context);
                linearLayout.addView(circleDiagramView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 16));
                CheckBoxCell lastCreatedCheckbox = null;
                for (int a = 0; a < 7; a++) {
                    long size;
                    String name;
                    String color;
                    if (a == 0) {
                        size = photoSize;
                        name = LocaleController.getString("LocalPhotoCache", R.string.LocalPhotoCache);
                        color = Theme.key_statisticChartLine_blue;
                    } else if (a == 1) {
                        size = videoSize;
                        name = LocaleController.getString("LocalVideoCache", R.string.LocalVideoCache);
                        color = Theme.key_statisticChartLine_golden;
                    } else if (a == 2) {
                        size = documentsSize;
                        name = LocaleController.getString("LocalDocumentCache", R.string.LocalDocumentCache);
                        color = Theme.key_statisticChartLine_green;
                    } else if (a == 3) {
                        size = musicSize;
                        name = LocaleController.getString("LocalMusicCache", R.string.LocalMusicCache);
                        color = Theme.key_statisticChartLine_indigo;
                    } else if (a == 4) {
                        size = audioSize;
                        name = LocaleController.getString("LocalAudioCache", R.string.LocalAudioCache);
                        color = Theme.key_statisticChartLine_red;
                    } else if (a == 5) {
                        size = stickersSize;
                        name = LocaleController.getString("AnimatedStickers", R.string.AnimatedStickers);
                        color = Theme.key_statisticChartLine_lightgreen;
                    } else {
                        size = cacheSize;
                        name = LocaleController.getString("LocalCache", R.string.LocalCache);
                        color = Theme.key_statisticChartLine_lightblue;
                    }
                    if (size > 0) {
                        clearViewData[a] = new StorageDiagramView.ClearViewData(circleDiagramView);
                        clearViewData[a].size = size;
                        clearViewData[a].color = color;
                        CheckBoxCell checkBoxCell = new CheckBoxCell(getParentActivity(), 4, 21, null);
                        lastCreatedCheckbox = checkBoxCell;
                        checkBoxCell.setTag(a);
                        checkBoxCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                        linearLayout.addView(checkBoxCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
                        checkBoxCell.setText(name, AndroidUtilities.formatFileSize(size), true, true);
                        checkBoxCell.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                        checkBoxCell.setCheckBoxColor(color, Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_checkboxCheck);
                        checkBoxCell.setOnClickListener(v -> {
                            int enabledCount = 0;
                            for (int i = 0; i < clearViewData.length; i++) {
                                if (clearViewData[i] != null && clearViewData[i].clear) {
                                    enabledCount++;
                                }
                            }
                            CheckBoxCell cell = (CheckBoxCell) v;
                            int num = (Integer) cell.getTag();
                            if (enabledCount == 1 && clearViewData[num].clear) {
                                AndroidUtilities.shakeView(((CheckBoxCell) v).getCheckBoxView(), 2, 0);
                                return;
                            }


                            clearViewData[num].setClear(!clearViewData[num].clear);
                            cell.setChecked(clearViewData[num].clear, true);
                        });
                    } else {
                        clearViewData[a] = null;
                    }
                }
                if (lastCreatedCheckbox != null) {
                    lastCreatedCheckbox.setNeedDivider(false);
                }
                circleDiagramView.setData(clearViewData);
                BottomSheet.BottomSheetCell cell = new BottomSheet.BottomSheetCell(getParentActivity(), 2);
                cell.setTextAndIcon(LocaleController.getString("ClearMediaCache", R.string.ClearMediaCache), 0);
                actionTextView = cell.getTextView();
                cell.getTextView().setOnClickListener(v -> {
                    try {
                        if (visibleDialog != null) {
                            visibleDialog.dismiss();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    cleanupFolders();
                });
                linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
                NestedScrollView scrollView = new NestedScrollView(context);
                scrollView.setVerticalScrollBarEnabled(false);
                scrollView.addView(linearLayout);
                bottomSheet.setCustomView(scrollView);
                showDialog(bottomSheet);
            }
        });

        cacheRemovedTooltip = new UndoView(context);
        frameLayout.addView(cacheRemovedTooltip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));

        return fragmentView;
    }

    private void clearDatabase() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("LocalDatabaseClearTextTitle", R.string.LocalDatabaseClearTextTitle));
        builder.setMessage(LocaleController.getString("LocalDatabaseClearText", R.string.LocalDatabaseClearText));
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString("CacheClear", R.string.CacheClear), (dialogInterface, i) -> {
            if (getParentActivity() == null) {
                return;
            }
            final AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
            progressDialog.setCanCacnel(false);
            progressDialog.showDelayed(500);
            MessagesController.getInstance(currentAccount).clearQueryTime();
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                try {
                    SQLiteDatabase database = MessagesStorage.getInstance(currentAccount).getDatabase();
                    ArrayList<Long> dialogsToCleanup = new ArrayList<>();

                    SQLiteCursor cursor = database.queryFinalized("SELECT did FROM dialogs WHERE 1");
                    StringBuilder ids = new StringBuilder();
                    while (cursor.next()) {
                        long did = cursor.longValue(0);
                        if (!DialogObject.isEncryptedDialog(did)) {
                            dialogsToCleanup.add(did);
                        }
                    }
                    cursor.dispose();

                    SQLitePreparedStatement state5 = database.executeFast("REPLACE INTO messages_holes VALUES(?, ?, ?)");
                    SQLitePreparedStatement state6 = database.executeFast("REPLACE INTO media_holes_v2 VALUES(?, ?, ?, ?)");

                    database.beginTransaction();
                    for (int a = 0; a < dialogsToCleanup.size(); a++) {
                        Long did = dialogsToCleanup.get(a);
                        int messagesCount = 0;
                        cursor = database.queryFinalized("SELECT COUNT(mid) FROM messages_v2 WHERE uid = " + did);
                        if (cursor.next()) {
                            messagesCount = cursor.intValue(0);
                        }
                        cursor.dispose();
                        if (messagesCount <= 2) {
                            continue;
                        }

                        cursor = database.queryFinalized("SELECT last_mid_i, last_mid FROM dialogs WHERE did = " + did);
                        int messageId = -1;
                        if (cursor.next()) {
                            long last_mid_i = cursor.longValue(0);
                            long last_mid = cursor.longValue(1);
                            SQLiteCursor cursor2 = database.queryFinalized("SELECT data FROM messages_v2 WHERE uid = " + did + " AND mid IN (" + last_mid_i + "," + last_mid + ")");
                            try {
                                while (cursor2.next()) {
                                    NativeByteBuffer data = cursor2.byteBufferValue(0);
                                    if (data != null) {
                                        TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                        if (message != null) {
                                            messageId = message.id;
                                            message.readAttachPath(data, UserConfig.getInstance(currentAccount).clientUserId);
                                        }
                                        data.reuse();
                                    }
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            cursor2.dispose();

                            database.executeFast("DELETE FROM messages_v2 WHERE uid = " + did + " AND mid != " + last_mid_i + " AND mid != " + last_mid).stepThis().dispose();
                            database.executeFast("DELETE FROM messages_holes WHERE uid = " + did).stepThis().dispose();
                            database.executeFast("DELETE FROM bot_keyboard WHERE uid = " + did).stepThis().dispose();
                            database.executeFast("DELETE FROM media_counts_v2 WHERE uid = " + did).stepThis().dispose();
                            database.executeFast("DELETE FROM media_v4 WHERE uid = " + did).stepThis().dispose();
                            database.executeFast("DELETE FROM media_holes_v2 WHERE uid = " + did).stepThis().dispose();
                            MediaDataController.getInstance(currentAccount).clearBotKeyboard(did, null);
                            if (messageId != -1) {
                                MessagesStorage.createFirstHoles(did, state5, state6, messageId);
                            }
                        }
                        cursor.dispose();
                    }

                    state5.dispose();
                    state6.dispose();
                    database.commitTransaction();
                    database.executeFast("PRAGMA journal_size_limit = 0").stepThis().dispose();
                    database.executeFast("VACUUM").stepThis().dispose();
                    database.executeFast("PRAGMA journal_size_limit = -1").stepThis().dispose();
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    AndroidUtilities.runOnUIThread(() -> {
                        try {
                            progressDialog.dismiss();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        if (listAdapter != null) {
                            databaseSize = MessagesStorage.getInstance(currentAccount).getDatabaseSize();
                            listAdapter.notifyDataSetChanged();
                        }
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didClearDatabase);
                    });
                }
            });
        });
        AlertDialog alertDialog = builder.create();
        showDialog(alertDialog);
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
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
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == databaseRow || (position == storageUsageRow && (totalSize > 0) && !calculating);
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new StroageUsageView(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 4:
                    SlideChooseView slideChooseView = new SlideChooseView(mContext);
                    view = slideChooseView;
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                    slideChooseView.setCallback(index -> {
                        if (index == 0) {
                            SharedConfig.setKeepMedia(3);
                        } else if (index == 1) {
                            SharedConfig.setKeepMedia(0);
                        } else if (index == 2) {
                            SharedConfig.setKeepMedia(1);
                        } else if (index == 3) {
                            SharedConfig.setKeepMedia(2);
                        }
                    });
                    int keepMedia = SharedConfig.keepMedia;
                    int index;
                    if (keepMedia == 3) {
                        index = 0;
                    } else {
                        index = keepMedia + 1;
                    }
                    slideChooseView.setOptions(index, LocaleController.formatPluralString("Days", 3), LocaleController.formatPluralString("Weeks", 1), LocaleController.formatPluralString("Months", 1), LocaleController.getString("KeepMediaForever", R.string.KeepMediaForever));
                    break;
                case 1:
                default:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0:
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    if (position == databaseRow) {
                        textCell.setTextAndValue(LocaleController.getString("ClearLocalDatabase", R.string.ClearLocalDatabase), AndroidUtilities.formatFileSize(databaseSize), false);
                    }
                    break;
                case 1:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == databaseInfoRow) {
                        privacyCell.setText(LocaleController.getString("LocalDatabaseInfo", R.string.LocalDatabaseInfo));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == cacheInfoRow) {
                        privacyCell.setText("");
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == keepMediaInfoRow) {
                        privacyCell.setText(AndroidUtilities.replaceTags(LocaleController.getString("KeepMediaInfo", R.string.KeepMediaInfo)));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                case 2:
                    StroageUsageView stroageUsageView = (StroageUsageView) holder.itemView;
                    stroageUsageView.setStorageUsage(calculating, databaseSize, totalSize, totalDeviceFreeSize, totalDeviceSize);
                    break;
                case 3:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == keepMediaHeaderRow) {
                        headerCell.setText(LocaleController.getString("KeepMedia", R.string.KeepMedia));
                    } else if (position == deviseStorageHeaderRow) {
                        headerCell.setText(LocaleController.getString("DeviceStorage", R.string.DeviceStorage));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int i) {
            if (i == databaseInfoRow || i == cacheInfoRow || i == keepMediaInfoRow) {
                return 1;
            }
            if (i == storageUsageRow) {
                return 2;
            }
            if (i == keepMediaHeaderRow || i == deviseStorageHeaderRow) {
                return 3;
            }
            if (i == keepMediaChooserRow) {
                return 4;
            }
            return 0;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate deldegagte = () -> {
            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
            }

            if (actionTextView != null) {
                actionTextView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
            }
        };
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();

        arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, SlideChooseView.class, StroageUsageView.class, HeaderCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        arrayList.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{StroageUsageView.class}, new String[]{"paintFill"}, null, null, null, Theme.key_player_progressBackground));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{StroageUsageView.class}, new String[]{"paintProgress"}, null, null, null, Theme.key_player_progress));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{StroageUsageView.class}, new String[]{"telegramCacheTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{StroageUsageView.class}, new String[]{"freeSizeTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{StroageUsageView.class}, new String[]{"calculationgTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{StroageUsageView.class}, new String[]{"paintProgress2"}, null, null, null, Theme.key_player_progressBackground2));

        arrayList.add(new ThemeDescription(listView, 0, new Class[]{SlideChooseView.class}, null, null, null, Theme.key_switchTrack));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{SlideChooseView.class}, null, null, null, Theme.key_switchTrackChecked));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{SlideChooseView.class}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));

        arrayList.add(new ThemeDescription(bottomSheetView, 0, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        arrayList.add(new ThemeDescription(bottomSheetView, 0, new Class[]{CheckBoxCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(bottomSheetView, 0, new Class[]{CheckBoxCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));
        arrayList.add(new ThemeDescription(bottomSheetView, 0, new Class[]{CheckBoxCell.class}, Theme.dividerPaint, null, null, Theme.key_divider));
        arrayList.add(new ThemeDescription(bottomSheetView, 0, new Class[]{StorageDiagramView.class}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));

        arrayList.add(new ThemeDescription(null, 0, new Class[]{TextCheckBoxCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        arrayList.add(new ThemeDescription(null, 0, null, null, null, deldegagte, Theme.key_dialogBackground));

        arrayList.add(new ThemeDescription(bottomSheetView, 0, null, null, null, null, Theme.key_statisticChartLine_blue));
        arrayList.add(new ThemeDescription(bottomSheetView, 0, null, null, null, null, Theme.key_statisticChartLine_green));
        arrayList.add(new ThemeDescription(bottomSheetView, 0, null, null, null, null, Theme.key_statisticChartLine_red));
        arrayList.add(new ThemeDescription(bottomSheetView, 0, null, null, null, null, Theme.key_statisticChartLine_golden));
        arrayList.add(new ThemeDescription(bottomSheetView, 0, null, null, null, null, Theme.key_statisticChartLine_lightblue));
        arrayList.add(new ThemeDescription(bottomSheetView, 0, null, null, null, null, Theme.key_statisticChartLine_lightgreen));
        arrayList.add(new ThemeDescription(bottomSheetView, 0, null, null, null, null, Theme.key_statisticChartLine_orange));
        arrayList.add(new ThemeDescription(bottomSheetView, 0, null, null, null, null, Theme.key_statisticChartLine_indigo));
        return arrayList;
    }
}
