/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.text.TextUtils;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.LongSparseArray;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FilesMigrationService;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckBoxCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.MediaActivity;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SlideChooseView;
import org.telegram.ui.Components.StorageDiagramView;
import org.telegram.ui.Components.StorageUsageView;
import org.telegram.ui.Components.UndoView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;

public class CacheControlActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int VIEW_TYPE_INFO = 1;
    private static final int VIEW_TYPE_STORAGE = 2;
    private static final int VIEW_TYPE_HEADER = 3;
    private static final int VIEW_TYPE_CHOOSER = 4;
    private static final int VIEW_TYPE_CHAT = 5;
    private static final int VIEW_FLICKER_LOADING_DIALOG = 6;
    private static final int VIEW_TYPE_TEXT_SETTINGS = 0;

    public static final long UNKNOWN_CHATS_DIALOG_ID = Long.MAX_VALUE;

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    @SuppressWarnings("FieldCanBeLocal")
    private LinearLayoutManager layoutManager;
    AlertDialog progressDialog;

    private int databaseRow;
    private int databaseInfoRow;
    private int keepMediaHeaderRow;
    private int keepMediaInfoRow;
    private int cacheInfoRow;
    private int deviseStorageHeaderRow;
    private int storageUsageRow;
    private int keepMediaChooserRow;
    private int chatsHeaderRow;
    private int chatsStartRow;
    private int chatsEndRow;
    private int rowCount;
    private int dialogsStartRow, dialogsEndRow;

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
    private long migrateOldFolderRow = -1;
    private StorageDiagramView.ClearViewData[] clearViewData = new StorageDiagramView.ClearViewData[7];
    private boolean calculating = true;

    private volatile boolean canceled = false;

    private View bottomSheetView;
    private BottomSheet bottomSheet;
    private View actionTextView;

    private UndoView cacheRemovedTooltip;

    long fragmentCreateTime;

    private boolean updateDatabaseSize;
    private final int TYPE_PHOTOS = 0;
    private final int TYPE_VIDEOS = 1;
    private final int TYPE_DOCUMENTS = 2;
    private final int TYPE_MUSIC = 3;
    private final int TYPE_VOICE = 4;
    private final int TYPE_ANIMATED_STICKERS_CACHE = 5;
    private final int TYPE_OTHER = 6;

    HashSet<Long> selectedDialogs = new HashSet();
    private static final int delete_id = 1;
    private boolean loadingDialogs;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        getNotificationCenter().addObserver(this, NotificationCenter.didClearDatabase);
        databaseSize = MessagesStorage.getInstance(currentAccount).getDatabaseSize();
        loadingDialogs = true;

        Utilities.globalQueue.postRunnable(() -> {
            cacheSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), 0);
            if (canceled) {
                return;
            }

            photoSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_IMAGE), 0);
            photoSize += getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_IMAGE_PUBLIC), 0);
            if (canceled) {
                return;
            }
            videoSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_VIDEO), 0);
            videoSize += getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_VIDEO_PUBLIC), 0);
            if (canceled) {
                return;
            }
            documentsSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_DOCUMENT), 1);
            documentsSize += getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_FILES), 1);
            if (canceled) {
                return;
            }
            musicSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_DOCUMENT), 2);
            musicSize += getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_FILES), 2);
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    blockSize = stat.getBlockSizeLong();
                } else {
                    blockSize = stat.getBlockSize();
                }
                long availableBlocks;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    availableBlocks = stat.getAvailableBlocksLong();
                } else {
                    availableBlocks = stat.getAvailableBlocks();
                }
                long blocksTotal;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
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
                resumeDelayedFragmentAnimation();
                calculating = false;
                updateStorageUsageRow();
            });
            loadDialogEntities();
        });

        fragmentCreateTime = System.currentTimeMillis();
        updateRows();
        return true;
    }

    private void loadDialogEntities() {
        getFileLoader().getFileDatabase().getQueue().postRunnable(() -> {
            LongSparseArray<DialogFileEntities> dilogsFilesEntities = new LongSparseArray<>();

            fillDialogsEntitiesRecursive(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), TYPE_OTHER, dilogsFilesEntities);
            fillDialogsEntitiesRecursive(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_IMAGE), TYPE_PHOTOS, dilogsFilesEntities);
            fillDialogsEntitiesRecursive(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_AUDIO), TYPE_VOICE, dilogsFilesEntities);
            fillDialogsEntitiesRecursive(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_IMAGE_PUBLIC), TYPE_PHOTOS, dilogsFilesEntities);
            fillDialogsEntitiesRecursive(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_VIDEO), TYPE_VIDEOS, dilogsFilesEntities);
            fillDialogsEntitiesRecursive(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_VIDEO_PUBLIC), TYPE_VIDEOS, dilogsFilesEntities);
            fillDialogsEntitiesRecursive(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_DOCUMENT), TYPE_DOCUMENTS, dilogsFilesEntities);
            fillDialogsEntitiesRecursive(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_FILES), TYPE_DOCUMENTS, dilogsFilesEntities);

            ArrayList<DialogFileEntities> entities = new ArrayList<>();
            ArrayList<Long> unknownUsers = new ArrayList<>();
            ArrayList<Long> unknownChats = new ArrayList<>();
            for (int i = 0; i < dilogsFilesEntities.size(); i++) {
                DialogFileEntities dialogEntities = dilogsFilesEntities.valueAt(i);
                entities.add(dialogEntities);
                if (getMessagesController().getUserOrChat(entities.get(i).dialogId) == null) {
                    if (dialogEntities.dialogId > 0) {
                        unknownUsers.add(dialogEntities.dialogId);
                    } else {
                        unknownChats.add(dialogEntities.dialogId);
                    }
                }
            }
            getMessagesStorage().getStorageQueue().postRunnable(() -> {
                ArrayList<TLRPC.User> users = new ArrayList<>();
                ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                if (!unknownUsers.isEmpty()) {
                    try {
                        getMessagesStorage().getUsersInternal(TextUtils.join(",", unknownUsers), users);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                if (!unknownChats.isEmpty()) {
                    try {
                        getMessagesStorage().getChatsInternal(TextUtils.join(",", unknownChats), chats);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
                for (int i = 0; i < entities.size(); i++) {
                    if (entities.get(i).totalSize <= 0) {
                        entities.remove(i);
                        i--;
                    }
                }
                sort(entities);
                AndroidUtilities.runOnUIThread(() -> {
                    loadingDialogs = false;
                    getMessagesController().putUsers(users, true);
                    getMessagesController().putChats(chats, true);
                    DialogFileEntities unknownChatsEntity = null;
                    for (int i = 0; i < entities.size(); i++) {
                        DialogFileEntities dialogEntities = entities.get(i);
                        boolean changed = false;
                        if (getMessagesController().getUserOrChat(dialogEntities.dialogId) == null) {
                            dialogEntities.dialogId = UNKNOWN_CHATS_DIALOG_ID;
                            if (unknownChatsEntity != null) {
                                changed = true;
                                unknownChatsEntity.merge(dialogEntities);
                                entities.remove(i);
                                i--;
                            } else {
                                unknownChatsEntity = dialogEntities;
                            }
                            if (changed) {
                                sort(entities);
                            }
                        }
                    }
                    if (!canceled) {
                        this.dialogsFilesEntities = entities;
                        updateRows();
                    }
                });
            });
        });
    }

    private void sort(ArrayList<DialogFileEntities> entities) {
        Collections.sort(entities, (o1, o2) -> {
            if (o2.totalSize > o1.totalSize) {
                return 1;
            } else if (o2.totalSize < o1.totalSize)  {
                return -1;
            }
            return 0;
        });
    }


    ArrayList<DialogFileEntities> dialogsFilesEntities = null;

    public void fillDialogsEntitiesRecursive(final File fromFolder, int type, LongSparseArray<DialogFileEntities> dilogsFilesEntities) {
        if (fromFolder == null) {
            return;
        }
        File[] files = fromFolder.listFiles();
        if (files == null) {
            return;
        }
        for (final File fileEntry : files) {
            if (canceled) {
                return;
            }
            if (fileEntry.isDirectory()) {
                fillDialogsEntitiesRecursive(fileEntry, type, dilogsFilesEntities);
            } else {
                long dialogId = getFileLoader().getFileDatabase().getFileDialogId(fileEntry);
                if (dialogId != 0) {
                    DialogFileEntities dilogEntites = dilogsFilesEntities.get(dialogId, null);
                    if (dilogEntites == null) {
                        dilogEntites = new DialogFileEntities(dialogId);
                        dilogsFilesEntities.put(dialogId, dilogEntites);
                    }
                    int addToType = type;
                    String fileName = fileEntry.getName().toLowerCase();
                    if (fileName.endsWith(".mp3") || fileName.endsWith(".m4a") ) {
                        addToType = TYPE_MUSIC;
                    }
                    dilogEntites.addFile(fileEntry, addToType);
                }
            }
        }
    }

    private ArrayList<ItemInner> oldItems = new ArrayList<>();
    private ArrayList<ItemInner> itemInners = new ArrayList<>();

    private void updateRows() {
        rowCount = 0;
        oldItems.clear();
        oldItems.addAll(itemInners);

        itemInners.clear();
        itemInners.add(new ItemInner(VIEW_TYPE_HEADER, LocaleController.getString("KeepMedia", R.string.KeepMedia), null));
        itemInners.add(new ItemInner(VIEW_TYPE_CHOOSER, null, null));
        keepMediaInfoRow = itemInners.size();
        itemInners.add(new ItemInner(VIEW_TYPE_INFO, "keep media", null));
        itemInners.add(new ItemInner(VIEW_TYPE_HEADER, LocaleController.getString("DeviceStorage", R.string.DeviceStorage), null));
        storageUsageRow = itemInners.size();
        itemInners.add(new ItemInner(VIEW_TYPE_STORAGE, null, null));
//        cacheInfoRow = itemInners.size();
//        itemInners.add(new ItemInner(VIEW_TYPE_INFO, "cache", null));
        databaseRow = itemInners.size();
        itemInners.add(new ItemInner(VIEW_TYPE_TEXT_SETTINGS, null, null));
        databaseInfoRow = itemInners.size();
        itemInners.add(new ItemInner(VIEW_TYPE_INFO, "database", null));

        if (loadingDialogs) {
            itemInners.add(new ItemInner(VIEW_TYPE_HEADER, LocaleController.getString("DataUsageByChats", R.string.DataUsageByChats), 15, 4, null));
            itemInners.add(new ItemInner(VIEW_FLICKER_LOADING_DIALOG, null, null));
        } else if (dialogsFilesEntities != null && dialogsFilesEntities.size() > 0) {
            itemInners.add(new ItemInner(VIEW_TYPE_HEADER, LocaleController.getString("DataUsageByChats", R.string.DataUsageByChats), 15, 4, null));
            dialogsStartRow = itemInners.size();
            for (int i = 0; i < dialogsFilesEntities.size(); i++) {
                itemInners.add(new ItemInner(VIEW_TYPE_CHAT, null, dialogsFilesEntities.get(i)));
            }
            dialogsEndRow = itemInners.size() - 1;
            itemInners.add(new ItemInner(VIEW_TYPE_INFO, null, null));
        }
        if (listAdapter != null) {
            listAdapter.setItems(oldItems, itemInners);
        }
    }

    private void updateStorageUsageRow() {
        View view = layoutManager.findViewByPosition(storageUsageRow);
        if (view instanceof StorageUsageView) {
            StorageUsageView storageUsageView = ((StorageUsageView) view);
            long currentTime = System.currentTimeMillis();
            if (currentTime - fragmentCreateTime > 150) {
                TransitionSet transition = new TransitionSet();
                ChangeBounds changeBounds = new ChangeBounds();
                changeBounds.setDuration(250);
                changeBounds.excludeTarget(storageUsageView.legendLayout, true);
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
            storageUsageView.setStorageUsage(calculating, databaseSize, totalSize, totalDeviceFreeSize, totalDeviceSize);
            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(storageUsageRow);
            if (holder != null) {
                storageUsageView.setEnabled(listAdapter.isEnabled(holder));
            }
        } else {
            updateRows();
        }
    }

    @Override
    public boolean needDelayOpenAnimation() {
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.didClearDatabase);
        try {
            if (progressDialog != null) {
                progressDialog.dismiss();
            }

        } catch (Exception e) {

        }
        progressDialog = null;
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
        if (selectedDialogs.size() > 0) {
            actionBar.hideActionMode();
            selectedDialogs.clear();
        }
        progressDialog = new AlertDialog(getParentActivity(), 3);
        progressDialog.setCanCancel(false);
        progressDialog.showDelayed(500);
        getFileLoader().cancelLoadAllFiles();
        getFileLoader().getFileLoaderQueue().postRunnable(() -> Utilities.globalQueue.postRunnable(() -> {
            cleanupFoldersInternal();
        }));
        dialogsFilesEntities = null;
        loadingDialogs = true;
        updateRows();

    }

    private void cleanupFoldersInternal() {
        boolean imagesCleared = false;
        long clearedSize = 0;
        boolean allItemsClear = true;
        for (int a = 0; a < 7; a++) {
            if (clearViewData[a] == null || !clearViewData[a].clear) {
                if (clearViewData[a] != null) {
                    allItemsClear = false;
                }
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
            if (type == FileLoader.MEDIA_DIR_IMAGE || type == FileLoader.MEDIA_DIR_VIDEO) {
                int publicDirectoryType;
                if (type == FileLoader.MEDIA_DIR_IMAGE) {
                    publicDirectoryType = FileLoader.MEDIA_DIR_IMAGE_PUBLIC;
                } else {
                    publicDirectoryType = FileLoader.MEDIA_DIR_VIDEO_PUBLIC;
                }
                file = FileLoader.checkDirectory(publicDirectoryType);

                if (file != null) {
                    Utilities.clearDir(file.getAbsolutePath(), documentsMusicType, Long.MAX_VALUE, false);
                }
            }
            if (type == FileLoader.MEDIA_DIR_DOCUMENT) {
                file = FileLoader.checkDirectory(FileLoader.MEDIA_DIR_FILES);
                if (file != null) {
                    Utilities.clearDir(file.getAbsolutePath(), documentsMusicType, Long.MAX_VALUE, false);
                }
            }

            if (type == FileLoader.MEDIA_DIR_CACHE) {
                cacheSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), documentsMusicType);
                imagesCleared = true;
            } else if (type == FileLoader.MEDIA_DIR_AUDIO) {
                audioSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_AUDIO), documentsMusicType);
            } else if (type == FileLoader.MEDIA_DIR_DOCUMENT) {
                if (documentsMusicType == 1) {
                    documentsSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_DOCUMENT), documentsMusicType);
                    documentsSize += getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_FILES), documentsMusicType);
                } else {
                    musicSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_DOCUMENT), documentsMusicType);
                    musicSize += getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_FILES), documentsMusicType);
                }
            } else if (type == FileLoader.MEDIA_DIR_IMAGE) {
                imagesCleared = true;
                photoSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_IMAGE), documentsMusicType);
                photoSize += getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_IMAGE_PUBLIC), documentsMusicType);
            } else if (type == FileLoader.MEDIA_DIR_VIDEO) {
                videoSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_VIDEO), documentsMusicType);
                videoSize += getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_VIDEO_PUBLIC), documentsMusicType);
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

        if (allItemsClear) {
            FileLoader.getInstance(currentAccount).clearFilePaths();
        }
        FileLoader.getInstance(currentAccount).checkCurrentDownloadsFiles();

        AndroidUtilities.runOnUIThread(() -> {
            if (imagesClearedFinal) {
                ImageLoader.getInstance().clearMemory();
            }
            if (listAdapter != null) {
                updateStorageUsageRow();
            }
            try {
                if (progressDialog != null) {
                    progressDialog.dismiss();
                    progressDialog = null;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }

            getMediaDataController().ringtoneDataStore.checkRingtoneSoundsLoaded();
            cacheRemovedTooltip.setInfoText(LocaleController.formatString("CacheWasCleared", R.string.CacheWasCleared, AndroidUtilities.formatFileSize(finalClearedSize)));
            cacheRemovedTooltip.showWithAction(0, UndoView.ACTION_CACHE_WAS_CLEARED, null, null);
            MediaDataController.getInstance(currentAccount).chekAllMedia(true);

            loadDialogEntities();
        });
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("StorageUsage", R.string.StorageUsage));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (selectedDialogs.size() > 0) {
                        selectedDialogs.clear();
                        actionBar.hideActionMode();
                        listAdapter.notifyItemRangeChanged(0, listAdapter.getItemCount());
                        return;
                    }
                    finishFragment();
                } else if (id == delete_id) {
                    if (selectedDialogs.isEmpty() || getParentActivity() == null) {
                        return;
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle(LocaleController.getString("ClearCache", R.string.ClearCache));
                    builder.setMessage(LocaleController.getString("ClearCacheForChats", R.string.ClearCacheForChats));
                    builder.setPositiveButton(LocaleController.getString("ClearButton", R.string.ClearButton), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            DialogFileEntities mergedEntities = new DialogFileEntities(0);
                            for (int i = 0; i < dialogsFilesEntities.size(); i++) {
                                if (selectedDialogs.contains(dialogsFilesEntities.get(i).dialogId)) {
                                    mergedEntities.merge(dialogsFilesEntities.get(i));
                                    dialogsFilesEntities.remove(i);
                                    i--;
                                }
                            }
                            if (mergedEntities.totalSize > 0) {
                                cleanupDialogFiles(mergedEntities, null);
                            }
                            selectedDialogs.clear();
                            actionBar.hideActionMode();
                            updateRows();
                        }
                    });
                    builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    AlertDialog dialog = builder.create();
                    showDialog(dialog);
                    TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                    if (button != null) {
                        button.setTextColor(Theme.getColor(Theme.key_dialogTextRed2));
                    }
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
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setSupportsChangeAnimations(false);
        listView.setItemAnimator(itemAnimator);
        listView.setOnItemClickListener((view, position) -> {
            if (getParentActivity() == null) {
                return;
            }
            if (position == databaseRow) {
                clearDatabase();
            } else if (position == storageUsageRow) {
                showClearCacheDialog(null);
            } else if (itemInners.get(position).entities != null) {
                if (view instanceof UserCell && selectedDialogs.size() > 0) {
                    selectDialog((UserCell) view, itemInners.get(position).entities.dialogId);
                    return;
                }
                showClearCacheDialog(itemInners.get(position).entities);
            }
        });
        listView.setOnItemLongClickListener(new RecyclerListView.OnItemLongClickListener() {
            @Override
            public boolean onItemClick(View view, int position) {
                if (view instanceof UserCell && itemInners.get(position).entities != null) {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    selectDialog((UserCell) view, itemInners.get(position).entities.dialogId);
                }
                return false;
            }
        });

        cacheRemovedTooltip = new UndoView(context);
        frameLayout.addView(cacheRemovedTooltip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));

        return fragmentView;
    }

    private void selectDialog(UserCell userCell, long dialogId) {
        boolean selected = false;
        if (selectedDialogs.contains(dialogId)) {
            selectedDialogs.remove(dialogId);
        } else {
            selectedDialogs.add(dialogId);
            selected = true;
        }
        userCell.setChecked(selected, true);

        if (selectedDialogs.size() > 0) {
            checkActionMode();
            actionBar.showActionMode(true);
            selectedDialogsCountTextView.setNumber(selectedDialogs.size(), true);
        } else {
            actionBar.hideActionMode();
            return;
        }

    }

    private void showClearCacheDialog(DialogFileEntities entities) {
        if (totalSize <= 0 || getParentActivity() == null) {
            return;
        }

        bottomSheet = new BottomSheet(getParentActivity(), false) {
            @Override
            protected boolean canDismissWithSwipe() {
                return false;
            }
        };
        bottomSheet.fixNavigationBar();
        bottomSheet.setAllowNestedScroll(true);
        bottomSheet.setApplyBottomPadding(false);
        LinearLayout linearLayout = new LinearLayout(getParentActivity());
        bottomSheetView = linearLayout;
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        StorageDiagramView circleDiagramView;
        if (entities != null) {
            circleDiagramView = new StorageDiagramView(getContext(), entities.dialogId) {
                @Override
                protected void onAvatarClick() {
                    bottomSheet.dismiss();
                    Bundle args = new Bundle();
//                    if (UserConfig.getInstance(currentAccount).getClientUserId() == entities.dialogId) {
                        args.putLong("dialog_id", entities.dialogId);
                        MediaActivity fragment = new MediaActivity(args, null);
                        fragment.setChatInfo(null);
                        presentFragment(fragment);
//                    } else {
//                        if (entities.dialogId < 0) {
//                            args.putLong("chat_id", -entities.dialogId);
//                        } else {
//                            args.putLong("user_id", entities.dialogId);
//                        }
//                        presentFragment(new ProfileActivity(args));
//                    }
                }
            };
        } else {
            circleDiagramView = new StorageDiagramView(getContext());
        }
        linearLayout.addView(circleDiagramView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 16));
        CheckBoxCell lastCreatedCheckbox = null;
        for (int a = 0; a < 7; a++) {
            long size;
            String name;
            String color;

            if (a == TYPE_PHOTOS) {
                size = photoSize;
                name = LocaleController.getString("LocalPhotoCache", R.string.LocalPhotoCache);
                color = Theme.key_statisticChartLine_blue;
            } else if (a == TYPE_VIDEOS) {
                size = videoSize;
                name = LocaleController.getString("LocalVideoCache", R.string.LocalVideoCache);
                color = Theme.key_statisticChartLine_golden;
            } else if (a == TYPE_DOCUMENTS) {
                size = documentsSize;
                name = LocaleController.getString("LocalDocumentCache", R.string.LocalDocumentCache);
                color = Theme.key_statisticChartLine_green;
            } else if (a == TYPE_MUSIC) {
                size = musicSize;
                name = LocaleController.getString("LocalMusicCache", R.string.LocalMusicCache);
                color = Theme.key_statisticChartLine_indigo;
            } else if (a == TYPE_VOICE) {
                size = audioSize;
                name = LocaleController.getString("LocalAudioCache", R.string.LocalAudioCache);
                color = Theme.key_statisticChartLine_red;
            } else if (a == TYPE_ANIMATED_STICKERS_CACHE) {
                size = stickersSize;
                name = LocaleController.getString("AnimatedStickers", R.string.AnimatedStickers);
                color = Theme.key_statisticChartLine_lightgreen;
            } else {
                size = cacheSize;
                name = LocaleController.getString("LocalCache", R.string.LocalCache);
                color = Theme.key_statisticChartLine_lightblue;
            }
            if (entities != null) {
                FileEntities fileEntities = entities.entitiesByType.get(a);
                if (fileEntities != null) {
                    size = fileEntities.totalSize;
                } else {
                    size = 0;
                }
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
                        BotWebViewVibrationEffect.APP_ERROR.vibrate();
                        AndroidUtilities.shakeViewSpring(((CheckBoxCell) v).getCheckBoxView(), -3);
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
            if (entities == null) {
                cleanupFolders();
            } else {
                cleanupDialogFiles(entities, clearViewData);
            }
        });
        linearLayout.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50));
        NestedScrollView scrollView = new NestedScrollView(getContext());
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.addView(linearLayout);
        bottomSheet.setCustomView(scrollView);
        showDialog(bottomSheet);
    }

    private void cleanupDialogFiles(DialogFileEntities dialogEntities, StorageDiagramView.ClearViewData[] clearViewData) {
        final AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
        progressDialog.setCanCancel(false);
        progressDialog.showDelayed(500);

        ArrayList<File> filesToRemove = new ArrayList<>();
        long totalSizeBefore = totalSize;
        for (int a = 0; a < 7; a++) {
            if (clearViewData != null) {
                if (clearViewData[a] == null || !clearViewData[a].clear) {
                    continue;
                }
            }
            FileEntities entitiesToDelete = dialogEntities.entitiesByType.get(a);
            if (entitiesToDelete == null) {
                continue;
            }
            filesToRemove.addAll(entitiesToDelete.files);
            dialogEntities.totalSize -= entitiesToDelete.totalSize;
            totalSize -= entitiesToDelete.totalSize;
            totalDeviceFreeSize += entitiesToDelete.totalSize;
            dialogEntities.entitiesByType.delete(a);
            if (a == TYPE_PHOTOS) {
                photoSize -= entitiesToDelete.totalSize;
            } else if (a == TYPE_VIDEOS) {
                videoSize -= entitiesToDelete.totalSize;
            } else if (a == TYPE_DOCUMENTS) {
                documentsSize -= entitiesToDelete.totalSize;
            } else if (a == TYPE_MUSIC) {
                musicSize -= entitiesToDelete.totalSize;
            } else if (a == TYPE_VOICE) {
                audioSize -= entitiesToDelete.totalSize;
            } else if (a == TYPE_ANIMATED_STICKERS_CACHE) {
                stickersSize -= entitiesToDelete.totalSize;
            } else {
                cacheSize -= entitiesToDelete.totalSize;
            }
        }
        if (dialogEntities.entitiesByType.size() == 0) {
            dialogsFilesEntities.remove(dialogEntities);
        }
        updateRows();

        cacheRemovedTooltip.setInfoText(LocaleController.formatString("CacheWasCleared", R.string.CacheWasCleared, AndroidUtilities.formatFileSize(totalSizeBefore - totalSize)));
        cacheRemovedTooltip.showWithAction(0, UndoView.ACTION_CACHE_WAS_CLEARED, null, null);

        getFileLoader().getFileDatabase().removeFiles(filesToRemove);
        getFileLoader().cancelLoadAllFiles();
        getFileLoader().getFileLoaderQueue().postRunnable(() -> {
            for (int i = 0; i < filesToRemove.size(); i++) {
                filesToRemove.get(i).delete();
            }

            AndroidUtilities.runOnUIThread(() -> {
                FileLoader.getInstance(currentAccount).checkCurrentDownloadsFiles();
                try {
                    progressDialog.dismiss();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        });
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private void migrateOldFolder() {
        FilesMigrationService.checkBottomSheet(this);
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
            progressDialog = new AlertDialog(getParentActivity(), 3);
            progressDialog.setCanCancel(false);
            progressDialog.showDelayed(500);
            MessagesController.getInstance(currentAccount).clearQueryTime();
            getMessagesStorage().clearLocalDatabase();
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
        loadDialogEntities();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didClearDatabase) {
            try {
                if (progressDialog != null) {
                    progressDialog.dismiss();
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            progressDialog = null;
            if (listAdapter != null) {
                databaseSize = MessagesStorage.getInstance(currentAccount).getDatabaseSize();
                updateDatabaseSize = true;
                updateRows();
            }
        }
    }

    private class ListAdapter extends AdapterWithDiffUtils {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == migrateOldFolderRow || position == databaseRow || (holder.getItemViewType() == VIEW_TYPE_STORAGE && (totalSize > 0) && !calculating) || holder.getItemViewType() == VIEW_TYPE_CHAT;
        }

        @Override
        public int getItemCount() {
            return itemInners.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_TEXT_SETTINGS:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_STORAGE:
                    view = new StorageUsageView(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_HEADER:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_CHOOSER:
                    SlideChooseView slideChooseView = new SlideChooseView(mContext);
                    view = slideChooseView;
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
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
                case VIEW_TYPE_CHAT:
                    UserCell userCell = new UserCell(getContext(), getResourceProvider());
                    userCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = userCell;
                    break;
                case VIEW_FLICKER_LOADING_DIALOG:
                    FlickerLoadingView flickerLoadingView = new FlickerLoadingView(getContext());
                    flickerLoadingView.setIsSingleCell(true);
                    flickerLoadingView.setItemsCount(3);
                    flickerLoadingView.setIgnoreHeightCheck(true);
                    flickerLoadingView.setViewType(FlickerLoadingView.DIALOG_CACHE_CONTROL);
                    flickerLoadingView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = flickerLoadingView;
                    break;
                case VIEW_TYPE_INFO:
                default:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_TEXT_SETTINGS:
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    if (position == databaseRow) {
                        textCell.setTextAndValue(LocaleController.getString("ClearLocalDatabase", R.string.ClearLocalDatabase), AndroidUtilities.formatFileSize(databaseSize), updateDatabaseSize, false);
                        updateDatabaseSize = false;
                    } else if (position == migrateOldFolderRow) {
                        textCell.setTextAndValue(LocaleController.getString("MigrateOldFolder", R.string.MigrateOldFolder), null, false);
                    }
                    break;
                case VIEW_TYPE_INFO:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == databaseInfoRow) {
                        privacyCell.setText(LocaleController.getString("LocalDatabaseInfo", R.string.LocalDatabaseInfo));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == keepMediaInfoRow) {
                        privacyCell.setText(AndroidUtilities.replaceTags(LocaleController.getString("KeepMediaInfo", R.string.KeepMediaInfo)));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        privacyCell.setText("");
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                case VIEW_TYPE_STORAGE:
                    StorageUsageView storageUsageView = (StorageUsageView) holder.itemView;
                    storageUsageView.setStorageUsage(calculating, databaseSize, totalSize, totalDeviceFreeSize, totalDeviceSize);
                    break;
                case VIEW_TYPE_HEADER:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    headerCell.setText(itemInners.get(position).headerName);
                    headerCell.setTopMargin(itemInners.get(position).headerTopMargin);
                    headerCell.setBottomMargin(itemInners.get(position).headerBottomMargin);
                    break;
                case VIEW_TYPE_CHAT:
                    UserCell userCell = (UserCell) holder.itemView;
                    DialogFileEntities dialogFileEntities = itemInners.get(position).entities;
                    TLObject object = getMessagesController().getUserOrChat(dialogFileEntities.dialogId);
                    String title;
                    boolean animated = userCell.dialogFileEntities != null && userCell.dialogFileEntities.dialogId == dialogFileEntities.dialogId;
                    if (dialogFileEntities.dialogId == UNKNOWN_CHATS_DIALOG_ID) {
                        title = LocaleController.getString("CacheOtherChats", R.string.CacheOtherChats);
                        userCell.getImageView().getAvatarDrawable().setAvatarType(AvatarDrawable.AVATAR_TYPE_OTHER_CHATS);
                        userCell.getImageView().setForUserOrChat(null, userCell.getImageView().getAvatarDrawable());
                    } else {
                        title = DialogObject.setDialogPhotoTitle(userCell.getImageView(), object);
                    }
                    userCell.dialogFileEntities = dialogFileEntities;
                    userCell.getImageView().setRoundRadius(AndroidUtilities.dp(object instanceof TLRPC.Chat && ((TLRPC.Chat) object).forum ? 12 : 19));
                    userCell.setTextAndValue(title, AndroidUtilities.formatFileSize(dialogFileEntities.totalSize), position < getItemCount() - 2);
                    userCell.setChecked(selectedDialogs.contains(dialogFileEntities.dialogId), animated);
                    break;
            }
        }

        @Override
        public int getItemViewType(int i) {
            return itemInners.get(i).viewType;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ThemeDescription.ThemeDescriptionDelegate deldegagte = () -> {
            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
            }

            if (actionTextView != null) {
                actionTextView.setBackground(Theme.AdaptiveRipple.filledRect(Theme.key_featuredStickers_addButton, 4));
            }
        };
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();

        arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, SlideChooseView.class, StorageUsageView.class, HeaderCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
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
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{StorageUsageView.class}, new String[]{"paintFill"}, null, null, null, Theme.key_player_progressBackground));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{StorageUsageView.class}, new String[]{"paintProgress"}, null, null, null, Theme.key_player_progress));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{StorageUsageView.class}, new String[]{"telegramCacheTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{StorageUsageView.class}, new String[]{"freeSizeTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{StorageUsageView.class}, new String[]{"calculationgTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{StorageUsageView.class}, new String[]{"paintProgress2"}, null, null, null, Theme.key_player_progressBackground2));

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

    public class UserCell extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

        public DialogFileEntities dialogFileEntities;
        private Theme.ResourcesProvider resourcesProvider;
        private TextView textView;
        private AnimatedTextView valueTextView;
        private BackupImageView imageView;
        private boolean needDivider;
        private boolean canDisable;

        protected CheckBox2 checkBox;

        public UserCell(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            textView = new TextView(context);
            textView.setSingleLine();
            textView.setLines(1);
            textView.setMaxLines(1);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setEllipsize(TextUtils.TruncateAt.END);
//            textView.setEllipsizeByGradient(true);
            textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 21 : 72, 0, LocaleController.isRTL ? 72 : 21, 0));

            valueTextView = new AnimatedTextView(context, true, true, !LocaleController.isRTL);
            valueTextView.setAnimationProperties(.55f, 0, 320, CubicBezierInterpolator.EASE_OUT_QUINT);
            valueTextView.setTextSize(AndroidUtilities.dp(16));
            valueTextView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL);
            valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText, resourcesProvider));
            addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, LocaleController.isRTL ? 21 : 72, 0, LocaleController.isRTL ? 72 : 21, 0));

            imageView = new BackupImageView(context);
            imageView.getAvatarDrawable().setScaleSize(.8f);
            addView(imageView, LayoutHelper.createFrame(38, 38, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 17, 0, 17, 0));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(50) + (needDivider ? 1 : 0));

            int availableWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - AndroidUtilities.dp(34);
            int width = availableWidth / 2;

            if (imageView.getVisibility() == VISIBLE) {
                imageView.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(38), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(38), MeasureSpec.EXACTLY));
            }

            if (valueTextView.getVisibility() == VISIBLE) {
                valueTextView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));
                width = availableWidth - valueTextView.getMeasuredWidth() - AndroidUtilities.dp(8);
            } else {
                width = availableWidth;
            }
            int padding = valueTextView.getMeasuredWidth() + AndroidUtilities.dp(12);
            if (LocaleController.isRTL) {
                ((MarginLayoutParams) textView.getLayoutParams()).leftMargin = padding;
            } else {
                ((MarginLayoutParams) textView.getLayoutParams()).rightMargin = padding;
            }
            textView.measure(MeasureSpec.makeMeasureSpec(width - padding, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.EXACTLY));

            if (checkBox != null) {
                checkBox.measure(
                        MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(24), MeasureSpec.EXACTLY)
                );
            }
        }

        public BackupImageView getImageView() {
            return imageView;
        }

        public TextView getTextView() {
            return textView;
        }

        public void setCanDisable(boolean value) {
            canDisable = value;
        }

        public AnimatedTextView getValueTextView() {
            return valueTextView;
        }

        public void setTextColor(int color) {
            textView.setTextColor(color);
        }

        public void setTextValueColor(int color) {
            valueTextView.setTextColor(color);
        }

        public void setText(CharSequence text, boolean divider) {
            text = Emoji.replaceEmoji(text, textView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(16), false);
            textView.setText(text);
            valueTextView.setVisibility(INVISIBLE);
            needDivider = divider;
            setWillNotDraw(!divider);
        }

        public void setTextAndValue(CharSequence text, CharSequence value, boolean divider) {
            setTextAndValue(text, value, false, divider);
        }

        public void setTextAndValue(CharSequence text, CharSequence value, boolean animated, boolean divider) {
            text = Emoji.replaceEmoji(text, textView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(16), false);
            textView.setText(text);
            if (value != null) {
                valueTextView.setText(value, animated);
                valueTextView.setVisibility(VISIBLE);
            } else {
                valueTextView.setVisibility(INVISIBLE);
            }
            needDivider = divider;
            setWillNotDraw(!divider);
            requestLayout();
        }

        public void setEnabled(boolean value, ArrayList<Animator> animators) {
            setEnabled(value);
            if (animators != null) {
                animators.add(ObjectAnimator.ofFloat(textView, "alpha", value ? 1.0f : 0.5f));
                if (valueTextView.getVisibility() == VISIBLE) {
                    animators.add(ObjectAnimator.ofFloat(valueTextView, "alpha", value ? 1.0f : 0.5f));
                }
            } else {
                textView.setAlpha(value ? 1.0f : 0.5f);
                if (valueTextView.getVisibility() == VISIBLE) {
                    valueTextView.setAlpha(value ? 1.0f : 0.5f);
                }
            }
        }

        @Override
        public void setEnabled(boolean value) {
            super.setEnabled(value);
            textView.setAlpha(value || !canDisable ? 1.0f : 0.5f);
            if (valueTextView.getVisibility() == VISIBLE) {
                valueTextView.setAlpha(value || !canDisable ? 1.0f : 0.5f);
            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            if (needDivider) {
                canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(72), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(72) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setText(textView.getText() + (valueTextView != null && valueTextView.getVisibility() == View.VISIBLE ? "\n" + valueTextView.getText() : ""));
            info.setEnabled(isEnabled());
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.emojiLoaded) {
                if (textView != null) {
                    textView.invalidate();
                }
            }
        }

        public void setChecked(boolean checked, boolean animated) {
            if (checkBox == null && !checked) {
                return;
            }
            if (checkBox == null) {
                checkBox = new CheckBox2(getContext(), 21, resourcesProvider);
                checkBox.setColor(null, Theme.key_windowBackgroundWhite, Theme.key_checkboxCheck);
                checkBox.setDrawUnchecked(false);
                checkBox.setDrawBackgroundAsArc(3);
                addView(checkBox, LayoutHelper.createFrame(24, 24, 0, 38, 25, 0, 0));
            }
            checkBox.setChecked(checked, animated);
        }
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 4) {
            boolean allGranted = true;
            for (int a = 0; a < grantResults.length; a++) {
                if (grantResults[a] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && FilesMigrationService.filesMigrationBottomSheet != null) {
                FilesMigrationService.filesMigrationBottomSheet.migrateOldFolder();
            }

        }
    }

    private class DialogFileEntities {

        long dialogId;
        long totalSize;
        SparseArray<FileEntities> entitiesByType = new SparseArray<>();

        public DialogFileEntities(long dialogId) {
            this.dialogId = dialogId;
        }

        public void addFile(File file, int type) {
            FileEntities entities = entitiesByType.get(type, null);
            if (entities == null) {
                entities = new FileEntities();
                entitiesByType.put(type, entities);
            }
            entities.count++;
            long fileSize = file.length();
            entities.totalSize += fileSize;
            totalSize += fileSize;
            entities.files.add(file);
        }

        public void merge(DialogFileEntities dialogEntities) {
            for (int i = 0; i < dialogEntities.entitiesByType.size(); i++) {
                int type = dialogEntities.entitiesByType.keyAt(i);
                FileEntities entitesToMerge = dialogEntities.entitiesByType.valueAt(i);
                FileEntities entities = entitiesByType.get(type, null);
                if (entities == null) {
                    entities = new FileEntities();
                    entitiesByType.put(type, entities);
                }
                entities.count += entitesToMerge.count;
                entities.totalSize += entitesToMerge.totalSize;
                totalSize += entitesToMerge.totalSize;
                entities.files.addAll(entitesToMerge.files);
            }
        }
    }

    private class FileEntities {
        long totalSize;
        int count;
        ArrayList<File> files = new ArrayList<>();
    }

    private class ItemInner extends AdapterWithDiffUtils.Item {

        int headerTopMargin = 15;
        int headerBottomMargin = 0;
        String headerName;
        DialogFileEntities entities;

        public ItemInner(int viewType, String headerName, DialogFileEntities dialogFileEntities) {
            super(viewType, true);
            this.headerName = headerName;
            this.entities = dialogFileEntities;
        }

        public ItemInner(int viewType, String headerName, int headerTopMargin, int headerBottomMargin, DialogFileEntities dialogFileEntities) {
            super(viewType, true);
            this.headerName = headerName;
            this.headerTopMargin = headerTopMargin;
            this.headerBottomMargin = headerBottomMargin;
            this.entities = dialogFileEntities;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ItemInner itemInner = (ItemInner) o;
            if (viewType == itemInner.viewType) {
                if (viewType == VIEW_TYPE_CHAT && entities != null && itemInner.entities != null) {
                    return entities.dialogId == itemInner.entities.dialogId;
                }
                if (viewType == VIEW_TYPE_CHOOSER || viewType == VIEW_TYPE_STORAGE || viewType == VIEW_TYPE_TEXT_SETTINGS) {
                    return true;
                }
                if (viewType == VIEW_TYPE_HEADER || viewType == VIEW_TYPE_INFO) {
                    return Objects.equals(headerName, itemInner.headerName);
                }
                return false;
            }
            return false;
        }
    }

    NumberTextView selectedDialogsCountTextView;
    private void checkActionMode() {
        if (actionBar.actionModeIsExist(null)) {
            return;
        }
        final ActionBarMenu actionMode = actionBar.createActionMode(false, null);

        if (inPreviewMode) {
            actionMode.setBackgroundColor(Color.TRANSPARENT);
            actionMode.drawBlur = false;
        }
        selectedDialogsCountTextView = new NumberTextView(actionMode.getContext());
        selectedDialogsCountTextView.setTextSize(18);
        selectedDialogsCountTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        selectedDialogsCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        actionMode.addView(selectedDialogsCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
        selectedDialogsCountTextView.setOnTouchListener((v, event) -> true);


        ActionBarMenuItem deleteItem = actionMode.addItemWithWidth(delete_id, R.drawable.msg_clear, AndroidUtilities.dp(54), LocaleController.getString("ClearCache", R.string.ClearCache));
    }
}
