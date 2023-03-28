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
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.util.LongSparseArray;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotWebViewVibrationEffect;
import org.telegram.messenger.CacheByChatsController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FilePathDatabase;
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
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckBoxCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CacheChart;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.HideViewAfterAnimation;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ListView.AdapterWithDiffUtils;
import org.telegram.ui.Components.LoadingDrawable;
import org.telegram.ui.Components.NestedSizeNotifierLayout;
import org.telegram.ui.Components.RLottieImageView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SlideChooseView;
import org.telegram.ui.Components.StorageDiagramView;
import org.telegram.ui.Components.StorageUsageView;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.UndoView;
import org.telegram.ui.Storage.CacheModel;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
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
    private static final int VIEW_TYPE_KEEP_MEDIA_CELL = 7;
    private static final int VIEW_TYPE_TEXT_SETTINGS = 0;
    private static final int VIEW_TYPE_CACHE_VIEW_PAGER = 8;

    private static final int VIEW_TYPE_CHART = 9;
    private static final int VIEW_TYPE_CHART_HEADER = 10;
    public static final int VIEW_TYPE_SECTION = 11;
    private static final int VIEW_TYPE_SECTION_LOADING = 12;
    private static final int VIEW_TYPE_CLEAR_CACHE_BUTTON = 13;
    private static final int VIEW_TYPE_MAX_CACHE_SIZE = 14;

    public static final int KEEP_MEDIA_TYPE_USER = 0;
    public static final int KEEP_MEDIA_TYPE_GROUP = 1;
    public static final int KEEP_MEDIA_TYPE_CHANNEL = 2;

    public static final long UNKNOWN_CHATS_DIALOG_ID = Long.MAX_VALUE;


    private ListAdapter listAdapter;
    private RecyclerListView listView;
    @SuppressWarnings("FieldCanBeLocal")
    private LinearLayoutManager layoutManager;
    AlertDialog progressDialog;

    private boolean[] selected = new boolean[] { true, true, true, true, true, true, true, true, true };
    private long databaseSize = -1;
    private long cacheSize = -1, cacheEmojiSize = -1, cacheTempSize = -1;
    private long documentsSize = -1;
    private long audioSize = -1;
    private long musicSize = -1;
    private long photoSize = -1;
    private long videoSize = -1;
    private long stickersCacheSize = -1;
    private long totalSize = -1;
    private long totalDeviceSize = -1;
    private long totalDeviceFreeSize = -1;
    private long migrateOldFolderRow = -1;
    private boolean calculating = true;
    private boolean collapsed = true;
    private CachedMediaLayout cachedMediaLayout;

    private int[] percents;
    private float[] tempSizes;

    private int sectionsStartRow = -1;
    private int sectionsEndRow = -1;

    private CacheChart cacheChart;
    private CacheChartHeader cacheChartHeader;
    private ClearCacheButtonInternal clearCacheButton;

    public static volatile boolean canceled = false;

    private View bottomSheetView;
    private BottomSheet bottomSheet;
    private View actionTextView;

    private UndoView cacheRemovedTooltip;

    long fragmentCreateTime;

    private boolean updateDatabaseSize;
    public final static int TYPE_PHOTOS = 0;
    public final static int TYPE_VIDEOS = 1;
    public final static int TYPE_DOCUMENTS = 2;
    public final static int TYPE_MUSIC = 3;
    public final static int TYPE_VOICE = 4;
    public final static int TYPE_ANIMATED_STICKERS_CACHE = 5;
    public final static int TYPE_OTHER = 6;

    private static final int delete_id = 1;
    private static final int other_id = 2;
    private static final int clear_database_id = 3;
    private boolean loadingDialogs;
    private NestedSizeNotifierLayout nestedSizeNotifierLayout;

    private ActionBarMenuSubItem clearDatabaseItem;
    private void updateDatabaseItemSize() {
        if (clearDatabaseItem != null) {
            SpannableStringBuilder string = new SpannableStringBuilder();
            string.append(LocaleController.getString("ClearLocalDatabase", R.string.ClearLocalDatabase));
//            string.append("\t");
//            SpannableString databaseSizeString = new SpannableString(AndroidUtilities.formatFileSize(databaseSize));
//            databaseSizeString.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)), 0, databaseSizeString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//            string.append(databaseSizeString);
            clearDatabaseItem.setText(string);
        }
    }

    private static long lastTotalSizeCalculatedTime;
    private static Long lastTotalSizeCalculated;
    private static Long lastDeviceTotalSize, lastDeviceTotalFreeSize;

    public static void calculateTotalSize(Utilities.Callback<Long> onDone) {
        if (onDone == null) {
            return;
        }
        if (lastTotalSizeCalculated != null) {
            onDone.run(lastTotalSizeCalculated);
            if (System.currentTimeMillis() - lastTotalSizeCalculatedTime < 5000) {
                return;
            }
        }
        Utilities.globalQueue.postRunnable(() -> {
            canceled = false;
            long cacheSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), 5);
            long cacheTempSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), 4);
            long photoSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_IMAGE), 0);
            photoSize += getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_IMAGE_PUBLIC), 0);
            long videoSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_VIDEO), 0);
            videoSize += getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_VIDEO_PUBLIC), 0);
            long documentsSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_DOCUMENT), 1);
            documentsSize += getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_FILES), 1);
            long musicSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_DOCUMENT), 2);
            musicSize += getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_FILES), 2);
            long stickersCacheSize = getDirectorySize(new File(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), "acache"), 0);
            stickersCacheSize += getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), 3);
            long audioSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_AUDIO), 0);
            final long totalSize = lastTotalSizeCalculated = cacheSize + cacheTempSize + videoSize + audioSize + photoSize + documentsSize + musicSize + stickersCacheSize;
            lastTotalSizeCalculatedTime = System.currentTimeMillis();
            if (!canceled) {
                AndroidUtilities.runOnUIThread(() -> {
                    onDone.run(totalSize);
                });
            }
        });
    }

    public static void resetCalculatedTotalSIze() {
        lastTotalSizeCalculated = null;
    }

    public static void getDeviceTotalSize(Utilities.Callback2<Long, Long> onDone) {
        if (lastDeviceTotalSize != null && lastDeviceTotalFreeSize != null) {
            if (onDone != null) {
                onDone.run(lastDeviceTotalSize, lastDeviceTotalFreeSize);
            }
            return;
        }
        File path;
        if (Build.VERSION.SDK_INT >= 19) {
            ArrayList<File> storageDirs = AndroidUtilities.getRootDirs();
            String dir = (path = storageDirs.get(0)).getAbsolutePath();
            if (!TextUtils.isEmpty(SharedConfig.storageCacheDir)) {
                for (int a = 0, N = storageDirs.size(); a < N; a++) {
                    File file = storageDirs.get(a);
                    if (file.getAbsolutePath().startsWith(SharedConfig.storageCacheDir) && file.canWrite()) {
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

            lastDeviceTotalSize = blocksTotal * blockSize;
            lastDeviceTotalFreeSize = availableBlocks * blockSize;
            if (onDone != null) {
                onDone.run(lastDeviceTotalSize, lastDeviceTotalFreeSize);
            }
            return;
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        canceled = false;
        getNotificationCenter().addObserver(this, NotificationCenter.didClearDatabase);
        databaseSize = MessagesStorage.getInstance(currentAccount).getDatabaseSize();
        loadingDialogs = true;

        Utilities.globalQueue.postRunnable(() -> {
            cacheSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), 5);
            if (canceled) {
                return;
            }

            cacheTempSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), 4);
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
            stickersCacheSize = getDirectorySize(new File(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), "acache"), 0);
            if (canceled) {
                return;
            }
            cacheEmojiSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), 3);
            if (canceled) {
                return;
            }
            stickersCacheSize += cacheEmojiSize;
            audioSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_AUDIO), 0);
            if (canceled) {
                return;
            }
            totalSize = lastTotalSizeCalculated = cacheSize + cacheTempSize + videoSize + audioSize + photoSize + documentsSize + musicSize + stickersCacheSize;
            lastTotalSizeCalculatedTime = System.currentTimeMillis();

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

                updateRows(true);
                updateChart();
            });

            loadDialogEntities();
        });

        fragmentCreateTime = System.currentTimeMillis();
        updateRows(false);
        updateChart();
        return true;
    }

    private void updateChart() {
        if (cacheChart != null) {
            if (!calculating && totalSize > 0) {
                CacheChart.SegmentSize[] segments = new CacheChart.SegmentSize[9];
                for (int i = 0; i < itemInners.size(); ++i) {
                    ItemInner item = itemInners.get(i);
                    if (item.viewType == VIEW_TYPE_SECTION) {
                        if (item.index < 0) {
                            if (collapsed) {
                                segments[8] = CacheChart.SegmentSize.of(item.size, selected[8]);
                            }
                        } else {
                            segments[item.index] = CacheChart.SegmentSize.of(item.size, selected[item.index]);
                        }
                    }
                }
                if (System.currentTimeMillis() - fragmentCreateTime < 80) {
                    cacheChart.loadingFloat.set(0, true);
                }
                cacheChart.setSegments(totalSize, true, segments);
            } else if (calculating) {
                cacheChart.setSegments(-1, true);
            } else {
                cacheChart.setSegments(0, true);
            }
        }
        if (clearCacheButton != null && !calculating) {
            clearCacheButton.updateSize();
        }
    }

    private void loadDialogEntities() {
        getFileLoader().getFileDatabase().getQueue().postRunnable(() -> {
            getFileLoader().getFileDatabase().ensureDatabaseCreated();
            CacheModel cacheModel = new CacheModel(false);
            LongSparseArray<DialogFileEntities> dilogsFilesEntities = new LongSparseArray<>();

            fillDialogsEntitiesRecursive(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), TYPE_OTHER, dilogsFilesEntities, null);

            fillDialogsEntitiesRecursive(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_IMAGE), TYPE_PHOTOS, dilogsFilesEntities, cacheModel);
            fillDialogsEntitiesRecursive(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_IMAGE_PUBLIC), TYPE_PHOTOS, dilogsFilesEntities, cacheModel);

            fillDialogsEntitiesRecursive(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_VIDEO), TYPE_VIDEOS, dilogsFilesEntities, cacheModel);
            fillDialogsEntitiesRecursive(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_VIDEO_PUBLIC), TYPE_VIDEOS, dilogsFilesEntities, cacheModel);

            fillDialogsEntitiesRecursive(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_AUDIO), TYPE_VOICE, dilogsFilesEntities, cacheModel);
            fillDialogsEntitiesRecursive(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_DOCUMENT), TYPE_DOCUMENTS, dilogsFilesEntities, cacheModel);
            fillDialogsEntitiesRecursive(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_FILES), TYPE_DOCUMENTS, dilogsFilesEntities, cacheModel);

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
            cacheModel.sortBySize();
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
                    cacheModel.setEntities(entities);

                    if (!canceled) {
                        setCacheModel(cacheModel);
                        updateRows();
                        updateChart();
                        if (cacheChartHeader != null && !calculating && System.currentTimeMillis() - fragmentCreateTime > 120) {
                            cacheChartHeader.setData(
                                    totalSize > 0,
                                    totalDeviceSize <= 0 ? 0 : (float) totalSize / totalDeviceSize,
                                    totalDeviceFreeSize <= 0 || totalDeviceSize <= 0 ? 0 : (float) (totalDeviceSize - totalDeviceFreeSize) / totalDeviceSize
                            );
                        }
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

    CacheModel cacheModel;

    public void setCacheModel(CacheModel cacheModel) {
        this.cacheModel = cacheModel;
        if (cachedMediaLayout != null) {
            cachedMediaLayout.setCacheModel(cacheModel);
        }
    }

    public void fillDialogsEntitiesRecursive(final File fromFolder, int type, LongSparseArray<DialogFileEntities> dilogsFilesEntities, CacheModel cacheModel) {
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
                fillDialogsEntitiesRecursive(fileEntry, type, dilogsFilesEntities, cacheModel);
            } else {
                if (fileEntry.getName().equals(".nomedia")) {
                    continue;
                }
                FilePathDatabase.FileMeta fileMetadata = getFileLoader().getFileDatabase().getFileDialogId(fileEntry, null);
                int addToType = type;
                String fileName = fileEntry.getName().toLowerCase();
                if (fileName.endsWith(".mp3") || fileName.endsWith(".m4a") ) {
                    addToType = TYPE_MUSIC;
                }
                CacheModel.FileInfo fileInfo = new CacheModel.FileInfo(fileEntry);
                fileInfo.type = addToType;
                if (fileMetadata != null) {
                    fileInfo.dialogId = fileMetadata.dialogId;
                    fileInfo.messageId = fileMetadata.messageId;
                    fileInfo.messageType = fileMetadata.messageType;
                }
                fileInfo.size = fileEntry.length();
                if (fileInfo.dialogId != 0) {
                    DialogFileEntities dilogEntites = dilogsFilesEntities.get(fileInfo.dialogId, null);
                    if (dilogEntites == null) {
                        dilogEntites = new DialogFileEntities(fileInfo.dialogId);
                        dilogsFilesEntities.put(fileInfo.dialogId, dilogEntites);
                    }
                    dilogEntites.addFile(fileInfo, addToType);
                }
                if (cacheModel != null) {
                    cacheModel.add(addToType, fileInfo);
                }
                //TODO measure for other accounts
//                for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
//                    if (i != currentAccount && UserConfig.getInstance(currentAccount).isClientActivated()) {
//                        FileLoader.getInstance(currentAccount).getFileDatabase().getFileDialogId(fileEntry);
//                    }
//                }
            }
        }
    }

    private ArrayList<ItemInner> oldItems = new ArrayList<>();
    private ArrayList<ItemInner> itemInners = new ArrayList<>();

    private String formatPercent(float k) {
        return formatPercent(k, true);
    }

    private String formatPercent(float k, boolean minimize) {
        if (minimize && k < 0.001f) {
            return String.format("<%.1f%%", 0.1f);
        }
        final float p = Math.round(k * 100f);
        if (minimize && p <= 0) {
            return String.format("<%d%%", 1);
        }
        return String.format("%d%%", (int) p);
    }

    private CharSequence getCheckBoxTitle(CharSequence header, int percent) {
        return getCheckBoxTitle(header, percent, false);
    }

    private CharSequence getCheckBoxTitle(CharSequence header, int percent, boolean addArrow) {
        String percentString = percent <= 0 ? String.format("<%.1f%%", 1f) : String.format("%d%%", percent);
        SpannableString percentStr = new SpannableString(percentString);
        percentStr.setSpan(new RelativeSizeSpan(.834f), 0, percentStr.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        percentStr.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf")), 0, percentStr.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        SpannableStringBuilder string = new SpannableStringBuilder(header);
        string.append("  ");
        string.append(percentStr);
        return string;
    }

    private void updateRows() {
        updateRows(true);
    }

    private void updateRows(boolean animated) {
        if (animated && System.currentTimeMillis() - fragmentCreateTime < 80) {
            animated = false;
        }

        oldItems.clear();
        oldItems.addAll(itemInners);

        itemInners.clear();
        itemInners.add(new ItemInner(VIEW_TYPE_CHART, null, null));
        itemInners.add(new ItemInner(VIEW_TYPE_CHART_HEADER, null, null));

        sectionsStartRow = itemInners.size();
        boolean hasCache = false;
        if (calculating) {
            itemInners.add(new ItemInner(VIEW_TYPE_SECTION_LOADING, null, null));
            itemInners.add(new ItemInner(VIEW_TYPE_SECTION_LOADING, null, null));
            itemInners.add(new ItemInner(VIEW_TYPE_SECTION_LOADING, null, null));
            itemInners.add(new ItemInner(VIEW_TYPE_SECTION_LOADING, null, null));
            itemInners.add(new ItemInner(VIEW_TYPE_SECTION_LOADING, null, null));
            hasCache = true;
        } else {
            ArrayList<ItemInner> sections = new ArrayList<>();
            if (photoSize > 0) {
                sections.add(ItemInner.asCheckBox(LocaleController.getString(R.string.LocalPhotoCache), 0, photoSize, Theme.key_statisticChartLine_lightblue));
            }
            if (videoSize > 0) {
                sections.add(ItemInner.asCheckBox(LocaleController.getString(R.string.LocalVideoCache), 1, videoSize, Theme.key_statisticChartLine_blue));
            }
            if (documentsSize > 0) {
                sections.add(ItemInner.asCheckBox(LocaleController.getString(R.string.LocalDocumentCache), 2, documentsSize, Theme.key_statisticChartLine_green));
            }
            if (musicSize > 0) {
                sections.add(ItemInner.asCheckBox(LocaleController.getString(R.string.LocalMusicCache), 3, musicSize, Theme.key_statisticChartLine_red));
            }
            if (audioSize > 0) {
                sections.add(ItemInner.asCheckBox(LocaleController.getString(R.string.LocalAudioCache), 4, audioSize, Theme.key_statisticChartLine_lightgreen));
            }
            if (stickersCacheSize > 0) {
                sections.add(ItemInner.asCheckBox(LocaleController.getString(R.string.LocalStickersCache), 5, stickersCacheSize, Theme.key_statisticChartLine_orange));
            }
            if (cacheSize > 0) {
                sections.add(ItemInner.asCheckBox(LocaleController.getString(R.string.LocalProfilePhotosCache), 6, cacheSize, Theme.key_statisticChartLine_cyan));
            }
            if (cacheTempSize > 0) {
                sections.add(ItemInner.asCheckBox(LocaleController.getString(R.string.LocalMiscellaneousCache), 7, cacheTempSize, Theme.key_statisticChartLine_purple));
            }
            if (!sections.isEmpty()) {
                Collections.sort(sections, (a, b) -> Long.compare(b.size, a.size));
                sections.get(sections.size() - 1).last = true;
                hasCache = true;

                if (tempSizes == null) {
                    tempSizes = new float[9];
                }
                for (int i = 0; i < tempSizes.length; ++i) {
                    tempSizes[i] = (float) size(i);
                }
                if (percents == null) {
                    percents = new int[9];
                }
                AndroidUtilities.roundPercents(tempSizes, percents);

                final int MAX_NOT_COLLAPSED = 4;
                if (sections.size() > MAX_NOT_COLLAPSED + 1) {
                    itemInners.addAll(sections.subList(0, MAX_NOT_COLLAPSED));
                    int sumPercents = 0;
                    long sum = 0;
                    for (int i = MAX_NOT_COLLAPSED; i < sections.size(); ++i) {
                        sections.get(i).pad = true;
                        sum += sections.get(i).size;
                        sumPercents += percents[sections.get(i).index];
                    }
                    percents[8] = sumPercents;
                    itemInners.add(ItemInner.asCheckBox(LocaleController.getString(R.string.LocalOther), -1, sum, Theme.key_statisticChartLine_golden));
                    if (!collapsed) {
                        itemInners.addAll(sections.subList(MAX_NOT_COLLAPSED, sections.size()));
                    }
                } else {
                    itemInners.addAll(sections);
                }
            }
        }

        if (hasCache) {
            sectionsEndRow = itemInners.size();
            itemInners.add(new ItemInner(VIEW_TYPE_CLEAR_CACHE_BUTTON, null, null));
            itemInners.add(ItemInner.asInfo(LocaleController.getString("StorageUsageInfo", R.string.StorageUsageInfo)));
        } else {
            sectionsEndRow = -1;
        }

        itemInners.add(new ItemInner(VIEW_TYPE_HEADER, LocaleController.getString("AutoDeleteCachedMedia", R.string.AutoDeleteCachedMedia), null));
        itemInners.add(new ItemInner(VIEW_TYPE_KEEP_MEDIA_CELL, KEEP_MEDIA_TYPE_USER));
        itemInners.add(new ItemInner(VIEW_TYPE_KEEP_MEDIA_CELL, KEEP_MEDIA_TYPE_GROUP));
        itemInners.add(new ItemInner(VIEW_TYPE_KEEP_MEDIA_CELL, KEEP_MEDIA_TYPE_CHANNEL));
        itemInners.add(ItemInner.asInfo(LocaleController.getString("KeepMediaInfoPart", R.string.KeepMediaInfoPart)));

        if (totalDeviceSize > 0) {
            itemInners.add(new ItemInner(VIEW_TYPE_HEADER, LocaleController.getString("MaxCacheSize", R.string.MaxCacheSize), null));
            itemInners.add(new ItemInner(VIEW_TYPE_MAX_CACHE_SIZE));
            itemInners.add(ItemInner.asInfo(LocaleController.getString("MaxCacheSizeInfo", R.string.MaxCacheSizeInfo)));
        }

        if (hasCache && cacheModel != null && !cacheModel.isEmpty()) {
            itemInners.add(new ItemInner(VIEW_TYPE_CACHE_VIEW_PAGER, null, null));
        }

        if (listAdapter != null) {
            if (animated) {
                listAdapter.setItems(oldItems, itemInners);
            } else {
                listAdapter.notifyDataSetChanged();
            }
        }
        if (cachedMediaLayout != null) {
            cachedMediaLayout.update();
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

    private static long getDirectorySize(File dir, int documentsMusicType) {
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

    private void cleanupFolders(Utilities.Callback2<Float, Boolean> onProgress, Runnable onDone) {
        if (cacheModel != null) {
            cacheModel.clearSelection();
        }
        if (cachedMediaLayout != null) {
            cachedMediaLayout.updateVisibleRows();
            cachedMediaLayout.showActionMode(false);
        }

//        progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
//        progressDialog.setCanCancel(false);
//        progressDialog.showDelayed(500);
        getFileLoader().cancelLoadAllFiles();
        getFileLoader().getFileLoaderQueue().postRunnable(() -> Utilities.globalQueue.postRunnable(() -> {
            cleanupFoldersInternal(onProgress, onDone);
        }));
        setCacheModel(null);
        loadingDialogs = true;
//        updateRows();
    }

    private static int LISTDIR_DOCTYPE_ALL = 0;
    private static int LISTDIR_DOCTYPE_OTHER_THAN_MUSIC = 1;
    private static int LISTDIR_DOCTYPE_MUSIC = 2;

    private static int LISTDIR_DOCTYPE2_EMOJI = 3;
    private static int LISTDIR_DOCTYPE2_TEMP = 4;
    private static int LISTDIR_DOCTYPE2_OTHER = 5;

    public static int countDirJava(String fileName, int docType) {
        int count = 0;
        File dir = new File(fileName);
        if (dir.exists()) {
            File[] entries = dir.listFiles();
            for (int i = 0; i < entries.length; ++i) {
                File entry = entries[i];
                String name = entry.getName();
                if (".".equals(name)) {
                    continue;
                }

                if (docType > 0 && name.length() >= 4) {
                    String namelc = name.toLowerCase();
                    boolean isMusic = namelc.endsWith(".mp3") || namelc.endsWith(".m4a");
                    boolean isEmoji = namelc.endsWith(".tgs") || namelc.endsWith(".webm");
                    boolean isTemp = namelc.endsWith(".tmp") || namelc.endsWith(".temp") || namelc.endsWith(".preload");

                    if (
                        isMusic && docType == LISTDIR_DOCTYPE_OTHER_THAN_MUSIC ||
                        !isMusic && docType == LISTDIR_DOCTYPE_MUSIC ||
                        isEmoji && docType == LISTDIR_DOCTYPE2_OTHER ||
                        !isEmoji && docType == LISTDIR_DOCTYPE2_EMOJI ||
                        isTemp && docType == LISTDIR_DOCTYPE2_OTHER ||
                        !isTemp && docType == LISTDIR_DOCTYPE2_TEMP
                    ) {
                        continue;
                    }
                }

                if (entry.isDirectory()) {
                    count += countDirJava(fileName + "/" + name, docType);
                } else {
                    count++;
                }
            }
        }
        return count;
    }

    public static void cleanDirJava(String fileName, int docType, int[] p, Utilities.Callback<Float> onProgress) {
        int count = countDirJava(fileName, docType);
        if (p == null) {
            p = new int[] { 0 };
        }
        File dir = new File(fileName);
        if (dir.exists()) {
            File[] entries = dir.listFiles();
            for (int i = 0; i < entries.length; ++i) {
                File entry = entries[i];
                String name = entry.getName();
                if (".".equals(name)) {
                    continue;
                }

                if (docType > 0 && name.length() >= 4) {
                    String namelc = name.toLowerCase();
                    boolean isMusic = namelc.endsWith(".mp3") || namelc.endsWith(".m4a");
                    boolean isEmoji = namelc.endsWith(".tgs") || namelc.endsWith(".webm");
                    boolean isTemp = namelc.endsWith(".tmp") || namelc.endsWith(".temp") || namelc.endsWith(".preload");

                    if (
                        isMusic && docType == LISTDIR_DOCTYPE_OTHER_THAN_MUSIC ||
                        !isMusic && docType == LISTDIR_DOCTYPE_MUSIC ||
                        isEmoji && docType == LISTDIR_DOCTYPE2_OTHER ||
                        !isEmoji && docType == LISTDIR_DOCTYPE2_EMOJI ||
                        isTemp && docType == LISTDIR_DOCTYPE2_OTHER ||
                        !isTemp && docType == LISTDIR_DOCTYPE2_TEMP
                    ) {
                        continue;
                    }
                }

                if (entry.isDirectory()) {
                    cleanDirJava(fileName + "/" + name, docType, p, onProgress);
                } else {
                    entry.delete();

                    p[0]++;
                    onProgress.run(p[0] / (float) count);
                }
            }
        }
    }

    private void cleanupFoldersInternal(Utilities.Callback2<Float, Boolean> onProgress, Runnable onDone) {
        boolean imagesCleared = false;
        long clearedSize = 0;
        boolean allItemsClear = true;
        final int[] clearDirI = new int[] { 0 };
        int clearDirCount = (selected[0] ? 2 : 0) + (selected[1] ? 2 : 0) + (selected[2] ? 2 : 0) + (selected[3] ? 2 : 0) + (selected[4] ? 1 : 0) + (selected[5] ? 2 : 0) + (selected[6] ? 1 : 0) + (selected[7] ? 1 : 0);
        long time = System.currentTimeMillis();
        Utilities.Callback<Float> updateProgress = t -> {
            onProgress.run(clearDirI[0] / (float) clearDirCount + (1f / clearDirCount) * MathUtils.clamp(t, 0, 1), false);
        };
        Runnable next = () -> {
            final long now = System.currentTimeMillis();
            onProgress.run(clearDirI[0] / (float) clearDirCount, now - time > 250);
        };
        for (int a = 0; a < 8; a++) {
            if (!selected[a]) {
                allItemsClear = false;
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
                clearedSize += stickersCacheSize + cacheEmojiSize;
            } else if (a == 6) {
                clearedSize += cacheSize;
                documentsMusicType = 5;
                type = FileLoader.MEDIA_DIR_CACHE;
            } else if (a == 7) {
                clearedSize += cacheTempSize;
                documentsMusicType = 4;
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
                cleanDirJava(file.getAbsolutePath(), documentsMusicType, null, updateProgress);
            }
            clearDirI[0]++;
            next.run();
            if (type == 100) {
                file = FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE);
                if (file != null) {
                    cleanDirJava(file.getAbsolutePath(), 3, null, updateProgress);
                }
                clearDirI[0]++;
                next.run();
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
                    cleanDirJava(file.getAbsolutePath(), documentsMusicType, null, updateProgress);
                }
                clearDirI[0]++;
                next.run();
            }
            if (type == FileLoader.MEDIA_DIR_DOCUMENT) {
                file = FileLoader.checkDirectory(FileLoader.MEDIA_DIR_FILES);
                if (file != null) {
                    cleanDirJava(file.getAbsolutePath(), documentsMusicType, null, updateProgress);
                }
                clearDirI[0]++;
                next.run();
            }

            if (type == FileLoader.MEDIA_DIR_CACHE) {
                cacheSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), 5);
                cacheTempSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), 4);
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
                stickersCacheSize = getDirectorySize(new File(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), "acache"), documentsMusicType);
                cacheEmojiSize = getDirectorySize(FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE), 3);
                stickersCacheSize += cacheEmojiSize;
            }
        }
        final boolean imagesClearedFinal = imagesCleared;
        totalSize = lastTotalSizeCalculated = cacheSize + cacheTempSize + videoSize + audioSize + photoSize + documentsSize + musicSize + stickersCacheSize;
        lastTotalSizeCalculatedTime = System.currentTimeMillis();
        Arrays.fill(selected, true);

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
            try {
                if (progressDialog != null) {
                    progressDialog.dismiss();
                    progressDialog = null;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }

            getMediaDataController().ringtoneDataStore.checkRingtoneSoundsLoaded();
            AndroidUtilities.runOnUIThread(() -> {
                cacheRemovedTooltip.setInfoText(LocaleController.formatString("CacheWasCleared", R.string.CacheWasCleared, AndroidUtilities.formatFileSize(finalClearedSize)));
                cacheRemovedTooltip.showWithAction(0, UndoView.ACTION_CACHE_WAS_CLEARED, null, null);
            }, 150);
            MediaDataController.getInstance(currentAccount).chekAllMedia(true);

            loadDialogEntities();

            if (onDone != null) {
                onDone.run();
            }
        });
    }

    private boolean changeStatusBar;

    @Override
    public void onTransitionAnimationProgress(boolean isOpen, float progress) {
        if (progress > .5f && !changeStatusBar) {
            changeStatusBar = true;
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.needCheckSystemBarColors);
        }
        super.onTransitionAnimationProgress(isOpen, progress);
    }

    @Override
    public boolean isLightStatusBar() {
        if (!changeStatusBar) {
            return super.isLightStatusBar();
        }
        return AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_windowBackgroundGray)) > 0.721f;
    }

    private long size(int type) {
        switch (type) {
            case 0: return photoSize;
            case 1: return videoSize;
            case 2: return documentsSize;
            case 3: return musicSize;
            case 4: return audioSize;
            case 5: return stickersCacheSize;
            case 6: return cacheSize;
            case 7: return cacheTempSize;
            default: return 0;
        }
    }

    private int sectionsSelected() {
        int count = 0;
        for (int i = 0; i < 8; ++i) {
            if (selected[i] && size(i) > 0) {
                count++;
            }
        }
        return count;
    }

    private ActionBarMenu actionMode;
    private AnimatedTextView actionModeTitle;
    private AnimatedTextView actionModeSubtitle;
    private TextView actionModeClearButton;

    @Override
    public View createView(Context context) {
        actionBar.setBackgroundDrawable(null);
        actionBar.setCastShadows(false);
        actionBar.setAddToContainer(false);
        actionBar.setOccupyStatusBar(true);
        actionBar.setTitleColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), 0));
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_listSelector), false);
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(LocaleController.getString("StorageUsage", R.string.StorageUsage));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (actionBar.isActionModeShowed()) {
                        if (cacheModel != null) {
                            cacheModel.clearSelection();
                        }
                        if (cachedMediaLayout != null) {
                            cachedMediaLayout.showActionMode(false);
                            cachedMediaLayout.updateVisibleRows();
                        }
                        return;
                    }
                    finishFragment();
                } else if (id == delete_id) {
                   clearSelectedFiles();
                } else if (id == clear_database_id) {
                    clearDatabase();
                }
            }
        });

        actionMode = actionBar.createActionMode();
        FrameLayout actionModeLayout = new FrameLayout(context);
        actionMode.addView(actionModeLayout, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));

        actionModeTitle = new AnimatedTextView(context, true, true, true);
        actionModeTitle.setAnimationProperties(.35f, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        actionModeTitle.setTextSize(AndroidUtilities.dp(18));
        actionModeTitle.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        actionModeTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        actionModeLayout.addView(actionModeTitle, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 18, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, -11, 0, 0));

        actionModeSubtitle = new AnimatedTextView(context, true, true, true);
        actionModeSubtitle.setAnimationProperties(.35f, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
        actionModeSubtitle.setTextSize(AndroidUtilities.dp(14));
        actionModeSubtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        actionModeLayout.addView(actionModeSubtitle, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 18, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, 10, 0, 0));

        actionModeClearButton = new TextView(context);
        actionModeClearButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        actionModeClearButton.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);
        actionModeClearButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        actionModeClearButton.setBackground(Theme.AdaptiveRipple.filledRect(Theme.key_featuredStickers_addButton, 6));
        actionModeClearButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        actionModeClearButton.setGravity(Gravity.CENTER);
        actionModeClearButton.setText(LocaleController.getString("CacheClear", R.string.CacheClear));
        actionModeClearButton.setOnClickListener(e -> clearSelectedFiles());
        actionModeLayout.addView(actionModeClearButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 28, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        ActionBarMenuItem otherItem = actionBar.createMenu().addItem(other_id, R.drawable.ic_ab_other);
        clearDatabaseItem = otherItem.addSubItem(clear_database_id, R.drawable.msg_delete, LocaleController.getString("ClearLocalDatabase", R.string.ClearLocalDatabase));
        clearDatabaseItem.setIconColor(Theme.getColor(Theme.key_dialogRedIcon));
        clearDatabaseItem.setTextColor(Theme.getColor(Theme.key_dialogTextRed));
        updateDatabaseItemSize();

        listAdapter = new ListAdapter(context);

        nestedSizeNotifierLayout = new NestedSizeNotifierLayout(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                boolean show = !isPinnedToTop();
                if (!show && actionBarShadowAlpha != 0) {
                    actionBarShadowAlpha -= 16f / 100f;
                    invalidate();
                } else if (show && actionBarShadowAlpha != 1f) {
                    actionBarShadowAlpha += 16f / 100f;
                    invalidate();
                }
                actionBarShadowAlpha = Utilities.clamp(actionBarShadowAlpha, 1f, 0);
                if (parentLayout != null) {
                    parentLayout.drawHeaderShadow(canvas, (int) (0xFF * actionBarShownT * actionBarShadowAlpha), AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight());
                }
            }
        };
        fragmentView = nestedSizeNotifierLayout;
        FrameLayout frameLayout = nestedSizeNotifierLayout;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (sectionsStartRow >= 0 && sectionsEndRow >= 0) {
                    drawSectionBackgroundExclusive(canvas, sectionsStartRow - 1, sectionsEndRow, Theme.getColor(Theme.key_windowBackgroundWhite));
                }
                super.dispatchDraw(canvas);
            }

            @Override
            protected boolean allowSelectChildAtPosition(View child) {
                return child != cacheChart;
            }
        };

        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setPadding(0, AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() / 2, 0, 0);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
            @Override
            protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                listView.invalidate();
            }
        };
        itemAnimator.setDurations(350);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setSupportsChangeAnimations(false);
        listView.setItemAnimator(itemAnimator);
        listView.setOnItemClickListener((view, position, x, y) -> {
            if (getParentActivity() == null) {
                return;
            }
            if (position < 0 || position >= itemInners.size()) {
                return;
            }
            ItemInner item = itemInners.get(position);
//            if (position == databaseRow) {
//                clearDatabase();
//            } else
            if (item.viewType == VIEW_TYPE_SECTION && view instanceof CheckBoxCell) {
                if (item.index < 0) {
                    collapsed = !collapsed;
                    updateRows();
                    updateChart();
                    return;
                }
                toggleSection(item, view);
            } else if (item.entities != null) {
//                if (view instanceof UserCell && selectedDialogs.size() > 0) {
//                    selectDialog((UserCell) view, itemInners.get(position).entities.dialogId);
//                    return;
//                }
                showClearCacheDialog(item.entities);
            } else if (item.keepMediaType >= 0) {
                KeepMediaPopupView windowLayout = new KeepMediaPopupView(this, view.getContext());
                ActionBarPopupWindow popupWindow = AlertsCreator.createSimplePopup(CacheControlActivity.this, windowLayout, view, x, y);
                windowLayout.update(itemInners.get(position).keepMediaType);
                windowLayout.setParentWindow(popupWindow);
                windowLayout.setCallback((type, keepMedia) -> {
                    AndroidUtilities.updateVisibleRows(listView);
                });
            }
        });
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {

            boolean pinned;
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                updateActionBar(layoutManager.findFirstVisibleItemPosition() > 0 || actionBar.isActionModeShowed());
                if (pinned != nestedSizeNotifierLayout.isPinnedToTop()) {
                    pinned = nestedSizeNotifierLayout.isPinnedToTop();
                    nestedSizeNotifierLayout.invalidate();
                }
            }
        });

        frameLayout.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        cacheRemovedTooltip = new UndoView(context);
        frameLayout.addView(cacheRemovedTooltip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 8, 8));
        nestedSizeNotifierLayout.setTargetListView(listView);
        return fragmentView;
    }

    private void clearSelectedFiles() {
        if (cacheModel.getSelectedFiles() == 0 || getParentActivity() == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(LocaleController.getString("ClearCache", R.string.ClearCache));
        builder.setMessage(LocaleController.getString("ClearCacheForChats", R.string.ClearCacheForChats));
        builder.setPositiveButton(LocaleController.getString("Clear", R.string.Clear), (di, which) -> {
            DialogFileEntities mergedEntities = cacheModel.removeSelectedFiles();
            if (mergedEntities.totalSize > 0) {
                cleanupDialogFiles(mergedEntities, null, null);
            }
            cacheModel.clearSelection();
            if (cachedMediaLayout != null) {
                cachedMediaLayout.update();
                cachedMediaLayout.showActionMode(false);
            }
            updateRows();
            updateChart();
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_dialogTextRed));
        }
    }

    private ValueAnimator actionBarAnimator;
    private float actionBarShownT;
    private boolean actionBarShown;
    private float actionBarShadowAlpha = 1f;
    private void updateActionBar(boolean show) {
        if (show != actionBarShown) {
            if (actionBarAnimator != null) {
                actionBarAnimator.cancel();
            }

            actionBarAnimator = ValueAnimator.ofFloat(actionBarShownT, (actionBarShown = show) ? 1f : 0f);
            actionBarAnimator.addUpdateListener(anm -> {
                actionBarShownT = (float) anm.getAnimatedValue();
                actionBar.setTitleColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), (int) (255 * actionBarShownT)));
                actionBar.setBackgroundColor(ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhite), (int) (255 * actionBarShownT)));
                fragmentView.invalidate();
            });
            actionBarAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            actionBarAnimator.setDuration(380);
            actionBarAnimator.start();
        }
    }

    private void showClearCacheDialog(DialogFileEntities entities) {
        if (totalSize <= 0 || getParentActivity() == null) {
            return;
        }

        bottomSheet = new DilogCacheBottomSheet(CacheControlActivity.this, entities, entities.createCacheModel(), new DilogCacheBottomSheet.Delegate() {
            @Override
            public void onAvatarClick() {
                bottomSheet.dismiss();
                Bundle args = new Bundle();
                if (entities.dialogId > 0) {
                    args.putLong("user_id", entities.dialogId);
                } else {
                    args.putLong("chat_id", -entities.dialogId);
                }
                presentFragment(new ProfileActivity(args, null));
            }

            @Override
            public void cleanupDialogFiles(DialogFileEntities entities, StorageDiagramView.ClearViewData[] clearViewData, CacheModel cacheModel) {
                CacheControlActivity.this.cleanupDialogFiles(entities, clearViewData, cacheModel);
            }
        });
        showDialog(bottomSheet);
    }

    private void cleanupDialogFiles(DialogFileEntities dialogEntities, StorageDiagramView.ClearViewData[] clearViewData, CacheModel dialogCacheModel) {
        final AlertDialog progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progressDialog.setCanCancel(false);
        progressDialog.showDelayed(500);

        HashSet<CacheModel.FileInfo> filesToRemove = new HashSet<>();
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
                stickersCacheSize -= entitiesToDelete.totalSize;
            } else {
                cacheSize -= entitiesToDelete.totalSize;
            }
        }
        if (dialogEntities.entitiesByType.size() == 0) {
            cacheModel.remove(dialogEntities);
        }
        updateRows();
        if (dialogCacheModel != null) {
            for (CacheModel.FileInfo fileInfo : dialogCacheModel.selectedFiles) {
                if (!filesToRemove.contains(fileInfo)) {
                    totalSize -= fileInfo.size;
                    totalDeviceFreeSize += fileInfo.size;
                    filesToRemove.add(fileInfo);
                    dialogEntities.removeFile(fileInfo);
                    if (fileInfo.type == TYPE_PHOTOS) {
                        photoSize -= fileInfo.size;
                    } else if (fileInfo.type == TYPE_VIDEOS) {
                        videoSize -= fileInfo.size;
                    } else if (fileInfo.size == TYPE_DOCUMENTS) {
                        documentsSize -= fileInfo.size;
                    } else if (fileInfo.size == TYPE_MUSIC) {
                        musicSize -= fileInfo.size;
                    } else if (fileInfo.size == TYPE_VOICE) {
                        audioSize -= fileInfo.size;
                    }
                }
            }
        }
        for (CacheModel.FileInfo fileInfo : filesToRemove) {
            this.cacheModel.onFileDeleted(fileInfo);
        }

        cacheRemovedTooltip.setInfoText(LocaleController.formatString("CacheWasCleared", R.string.CacheWasCleared, AndroidUtilities.formatFileSize(totalSizeBefore - totalSize)));
        cacheRemovedTooltip.showWithAction(0, UndoView.ACTION_CACHE_WAS_CLEARED, null, null);

        ArrayList<CacheModel.FileInfo> fileInfos = new ArrayList<>(filesToRemove);
        getFileLoader().getFileDatabase().removeFiles(fileInfos);
        getFileLoader().cancelLoadAllFiles();
        getFileLoader().getFileLoaderQueue().postRunnable(() -> {
            for (int i = 0; i < fileInfos.size(); i++) {
                fileInfos.get(i).file.delete();
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
        SpannableStringBuilder message = new SpannableStringBuilder();
        message.append(LocaleController.getString("LocalDatabaseClearText", R.string.LocalDatabaseClearText));
        message.append("\n\n");
        message.append(AndroidUtilities.replaceTags(LocaleController.formatString("LocalDatabaseClearText2", R.string.LocalDatabaseClearText2, AndroidUtilities.formatFileSize(databaseSize))));
        builder.setMessage(message);
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString("CacheClear", R.string.CacheClear), (dialogInterface, i) -> {
            if (getParentActivity() == null) {
                return;
            }
            progressDialog = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
            progressDialog.setCanCancel(false);
            progressDialog.showDelayed(500);
            MessagesController.getInstance(currentAccount).clearQueryTime();
            getMessagesStorage().clearLocalDatabase();
        });
        AlertDialog alertDialog = builder.create();
        showDialog(alertDialog);
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_dialogTextRed));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        listAdapter.notifyDataSetChanged();
        if (!calculating) {
//            loadDialogEntities();
        }
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
                updateDatabaseItemSize();
                updateRows();
            }
        }
    }

    class CacheChartHeader extends FrameLayout {

        AnimatedTextView title;
        TextView[] subtitle = new TextView[3];
        View bottomImage;

        RectF progressRect = new RectF();
        LoadingDrawable loadingDrawable = new LoadingDrawable();

        Float percent, usedPercent;
        AnimatedFloat percentAnimated = new AnimatedFloat(this, 450, CubicBezierInterpolator.EASE_OUT_QUINT);
        AnimatedFloat usedPercentAnimated = new AnimatedFloat(this, 450, CubicBezierInterpolator.EASE_OUT_QUINT);
        AnimatedFloat loadingFloat = new AnimatedFloat(this, 450, CubicBezierInterpolator.EASE_OUT_QUINT);

        Paint loadingBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint percentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint usedPercentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean firstSet = true;

        public CacheChartHeader(Context context) {
            super(context);

            title = new AnimatedTextView(context);
            title.setAnimationProperties(.35f, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);
            title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            title.setTextSize(AndroidUtilities.dp(20));
            title.setText(LocaleController.getString("StorageUsage", R.string.StorageUsage));
            title.setGravity(Gravity.CENTER);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(title, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 26, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

            for (int i = 0; i < 3; ++i) {
                subtitle[i] = new TextView(context);
                subtitle[i].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                subtitle[i].setGravity(Gravity.CENTER);
                subtitle[i].setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), 0);
                if (i == 0) {
                    subtitle[i].setText(LocaleController.getString("StorageUsageCalculating", R.string.StorageUsageCalculating));
                } else if (i == 1) {
                    subtitle[i].setAlpha(0);
                    subtitle[i].setText(LocaleController.getString("StorageUsageTelegram", R.string.StorageUsageTelegram));
                    subtitle[i].setVisibility(View.INVISIBLE);
                } else if (i == 2) {
                    subtitle[i].setText(LocaleController.getString("StorageCleared2", R.string.StorageCleared2));
                    subtitle[i].setAlpha(0);
                    subtitle[i].setVisibility(View.INVISIBLE);
                }
                subtitle[i].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
                addView(subtitle[i], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, i == 2 ? 12 : -6, 0, 0));
            }

            bottomImage = new View(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec) + getPaddingLeft() + getPaddingRight(), MeasureSpec.EXACTLY), heightMeasureSpec);
                }
            };
            Drawable bottomImageDrawable = getContext().getResources().getDrawable(R.drawable.popup_fixed_alert2).mutate();
            bottomImageDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhite), PorterDuff.Mode.MULTIPLY));
            bottomImage.setBackground(bottomImageDrawable);
            MarginLayoutParams bottomImageParams = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 24, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
            bottomImageParams.leftMargin = -bottomImage.getPaddingLeft();
            bottomImageParams.bottomMargin = -AndroidUtilities.dp(11);
            bottomImageParams.rightMargin = -bottomImage.getPaddingRight();
            addView(bottomImage, bottomImageParams);

            int color = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4);
            loadingDrawable.setColors(
                Theme.getColor(Theme.key_actionBarActionModeDefaultSelector),
                Theme.multAlpha(color, .2f)
            );
            loadingDrawable.setRadiiDp(4);
            loadingDrawable.setCallback(this);
        }

        public void setData(boolean hasCache, float percent, float usedPercent) {
            title.setText(
                hasCache ?
                    LocaleController.getString("StorageUsage", R.string.StorageUsage) :
                    LocaleController.getString("StorageCleared", R.string.StorageCleared)
            );
            if (hasCache) {
                if (percent < 0.01f) {
                    subtitle[1].setText(LocaleController.formatString("StorageUsageTelegramLess", R.string.StorageUsageTelegramLess, formatPercent(percent)));
                } else {
                    subtitle[1].setText(LocaleController.formatString("StorageUsageTelegram", R.string.StorageUsageTelegram, formatPercent(percent)));
                }
                switchSubtitle(1);
            } else {
                switchSubtitle(2);
            }
            bottomImage.animate().cancel();
            if (firstSet) {
                bottomImage.setAlpha(hasCache ? 1 : 0);
            } else {
                bottomImage.animate().alpha(hasCache ? 1 : 0).setDuration(365).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
            }
            firstSet = false;
            this.percent = percent;
            this.usedPercent = usedPercent;
            invalidate();
        }

        private void switchSubtitle(int type) {
            boolean animated = System.currentTimeMillis() - fragmentCreateTime > 40;
            updateViewVisible(subtitle[0], type == 0, animated);
            updateViewVisible(subtitle[1], type == 1, animated);
            updateViewVisible(subtitle[2], type == 2, animated);
        }

        private void updateViewVisible(View view, boolean show, boolean animated) {
            if (view == null) {
                return;
            }
            if (view.getParent() == null) {
                animated = false;
            }

            view.animate().setListener(null).cancel();
            if (!animated) {
                view.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
                view.setTag(show ? 1 : null);
                view.setAlpha(show ? 1f : 0f);
                view.setTranslationY(show ? 0 : AndroidUtilities.dp(8));
                invalidate();
            } else if (show) {
                if (view.getVisibility() != View.VISIBLE) {
                    view.setVisibility(View.VISIBLE);
                    view.setAlpha(0f);
                    view.setTranslationY(AndroidUtilities.dp(8));
                }
                view.animate().alpha(1f).translationY(0).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(340).setUpdateListener(anm -> invalidate()).start();
            } else {
                view.animate().alpha(0).translationY(AndroidUtilities.dp(8)).setListener(new HideViewAfterAnimation(view)).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(340).setUpdateListener(anm -> invalidate()).start();
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int fullWidth = MeasureSpec.getSize(widthMeasureSpec);
            int width = (int) Math.min(AndroidUtilities.dp(174), fullWidth * .8);

            super.measureChildren(MeasureSpec.makeMeasureSpec(fullWidth, MeasureSpec.EXACTLY), heightMeasureSpec);
            int height = AndroidUtilities.dp(90 - 18);
            int maxSubtitleHeight = 0;
            for (int i = 0; i < subtitle.length; ++i) {
                maxSubtitleHeight = Math.max(maxSubtitleHeight, subtitle[i].getMeasuredHeight() - (i == 2 ? AndroidUtilities.dp(16) : 0));
            }
            height += maxSubtitleHeight;
            setMeasuredDimension(fullWidth, height);

            progressRect.set(
                (fullWidth - width) / 2f,
                height - AndroidUtilities.dp(30),
                (fullWidth + width) / 2f,
                height - AndroidUtilities.dp(30 - 4)
            );
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            float barAlpha = 1f - subtitle[2].getAlpha();

            float loading = this.loadingFloat.set(this.percent == null ? 1f : 0f);
            float percent = this.percentAnimated.set(this.percent == null ? 0 : this.percent);
            float usedPercent = this.usedPercentAnimated.set(this.usedPercent == null ? 0 : this.usedPercent);

            loadingBackgroundPaint.setColor(Theme.getColor(Theme.key_actionBarActionModeDefaultSelector));
            loadingBackgroundPaint.setAlpha((int) (loadingBackgroundPaint.getAlpha() * barAlpha));
            AndroidUtilities.rectTmp.set(
                Math.max(
                    progressRect.left + (1f - loading) * Math.max(AndroidUtilities.dp(4), usedPercent * progressRect.width()),
                    progressRect.left + (1f - loading) * Math.max(AndroidUtilities.dp(4), percent * progressRect.width())
                ) + AndroidUtilities.dp(1), progressRect.top,
                progressRect.right, progressRect.bottom
            );
            if (AndroidUtilities.rectTmp.left < AndroidUtilities.rectTmp.right && AndroidUtilities.rectTmp.width() > AndroidUtilities.dp(3)) {
                drawRoundRect(canvas, AndroidUtilities.rectTmp, AndroidUtilities.dp(AndroidUtilities.lerp(1, 2, loading)), AndroidUtilities.dp(2), loadingBackgroundPaint);
            }

            loadingDrawable.setBounds(progressRect);
            loadingDrawable.setAlpha((int) (0xFF * barAlpha * loading));
            loadingDrawable.draw(canvas);

            usedPercentPaint.setColor(Theme.percentSV(Theme.getColor(Theme.key_radioBackgroundChecked), Theme.getColor(Theme.key_actionBarActionModeDefaultSelector), .922f, 1.8f));
            usedPercentPaint.setAlpha((int) (usedPercentPaint.getAlpha() * barAlpha));
            AndroidUtilities.rectTmp.set(
                progressRect.left + (1f - loading) * Math.max(AndroidUtilities.dp(4), percent * progressRect.width()) + AndroidUtilities.dp(1),
                progressRect.top,
                progressRect.left + (1f - loading) * Math.max(AndroidUtilities.dp(4), usedPercent * progressRect.width()),
                progressRect.bottom
            );
            if (AndroidUtilities.rectTmp.width() > AndroidUtilities.dp(3)) {
                drawRoundRect(canvas, AndroidUtilities.rectTmp, AndroidUtilities.dp(1), AndroidUtilities.dp(usedPercent > .97f ? 2 : 1), usedPercentPaint);
            }

            percentPaint.setColor(Theme.getColor(Theme.key_radioBackgroundChecked));
            percentPaint.setAlpha((int) (percentPaint.getAlpha() * barAlpha));
            AndroidUtilities.rectTmp.set(progressRect.left, progressRect.top, progressRect.left + (1f - loading) * Math.max(AndroidUtilities.dp(4), percent * progressRect.width()), progressRect.bottom);
            drawRoundRect(canvas, AndroidUtilities.rectTmp, AndroidUtilities.dp(2), AndroidUtilities.dp(percent > .97f ? 2 : 1), percentPaint);

            if (loading > 0 || this.percentAnimated.isInProgress()) {
                invalidate();
            }

            super.dispatchDraw(canvas);
        }

        private Path roundPath;
        private float[] radii;

        private void drawRoundRect(Canvas canvas, RectF rect, float left, float right, Paint paint) {
            if (roundPath == null) {
                roundPath = new Path();
            } else {
                roundPath.rewind();
            }
            if (radii == null) {
                radii = new float[8];
            }
            radii[0] = radii[1] = radii[6] = radii[7] = left;
            radii[2] = radii[3] = radii[4] = radii[5] = right;
            roundPath.addRoundRect(rect, radii, Path.Direction.CW);
            canvas.drawPath(roundPath, paint);
        }
    }

    private class ClearingCacheView extends FrameLayout {

        RLottieImageView imageView;
        AnimatedTextView percentsTextView;
        ProgressView progressView;
        TextView title, subtitle;

        public ClearingCacheView(Context context) {
            super(context);

            imageView = new RLottieImageView(context);
            imageView.setAutoRepeat(true);
            imageView.setAnimation(R.raw.utyan_cache, 150, 150);
            addView(imageView, LayoutHelper.createFrame(150, 150, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 16, 0, 0));
            imageView.playAnimation();

            percentsTextView = new AnimatedTextView(context, false, true, true);
            percentsTextView.setAnimationProperties(.35f, 0, 120, CubicBezierInterpolator.EASE_OUT);
            percentsTextView.setGravity(Gravity.CENTER_HORIZONTAL);
            percentsTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            percentsTextView.setTextSize(AndroidUtilities.dp(24));
            percentsTextView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            addView(percentsTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 16 + 150 + 16 - 6, 0, 0));

            progressView = new ProgressView(context);
            addView(progressView, LayoutHelper.createFrame(240, 5, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 16 + 150 + 16 + 28 + 16, 0, 0));

            title = new TextView(context);
            title.setGravity(Gravity.CENTER_HORIZONTAL);
            title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            title.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            title.setText(LocaleController.getString("ClearingCache", R.string.ClearingCache));
            addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 16 + 150 + 16 + 28 + 16 + 5 + 30, 0, 0));

            subtitle = new TextView(context);
            subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
            subtitle.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            subtitle.setText(LocaleController.getString("ClearingCacheDescription", R.string.ClearingCacheDescription));
            addView(subtitle, LayoutHelper.createFrame(240, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 16 + 150 + 16 + 28 + 16 + 5 + 30 + 18 + 10, 0, 0));

            setProgress(0);
        }

        public void setProgress(float t) {
            percentsTextView.cancelAnimation();
            percentsTextView.setText(String.format("%d%%", (int) Math.ceil(MathUtils.clamp(t, 0, 1) * 100)), !LocaleController.isRTL);
            progressView.setProgress(t);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(350), MeasureSpec.EXACTLY)
            );
        }

        class ProgressView extends View {

            Paint in = new Paint(Paint.ANTI_ALIAS_FLAG), out = new Paint(Paint.ANTI_ALIAS_FLAG);

            public ProgressView(Context context) {
                super(context);

                in.setColor(Theme.getColor(Theme.key_switchTrackChecked));
                out.setColor(Theme.multAlpha(Theme.getColor(Theme.key_switchTrackChecked), .2f));
            }

            float progress;
            AnimatedFloat progressT = new AnimatedFloat(this, 350, CubicBezierInterpolator.EASE_OUT);

            public void setProgress(float t) {
                this.progress = t;
                invalidate();
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);

                AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(3), AndroidUtilities.dp(3), out);

                AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth() * progressT.set(this.progress), getMeasuredHeight());
                canvas.drawRoundRect(AndroidUtilities.rectTmp, AndroidUtilities.dp(3), AndroidUtilities.dp(3), in);
            }
        }
    }

    private class ClearCacheButtonInternal extends ClearCacheButton {

        public ClearCacheButtonInternal(Context context) {
            super(context);
            ((MarginLayoutParams) button.getLayoutParams()).topMargin = AndroidUtilities.dp(5);
            button.setOnClickListener(e -> {
                BottomSheet bottomSheet = new BottomSheet(getContext(), false) {
                    @Override
                    protected boolean canDismissWithTouchOutside() {
                        return false;
                    }
                };
                bottomSheet.fixNavigationBar();
                bottomSheet.setCanDismissWithSwipe(false);
                bottomSheet.setCancelable(false);
                ClearingCacheView cacheView = new ClearingCacheView(getContext());
                bottomSheet.setCustomView(cacheView);

                final boolean[] done = new boolean[] { false };
                final float[] progress = new float[] { 0 };
                final boolean[] nextSection = new boolean[] { false };
                Runnable updateProgress = () -> {
                    cacheView.setProgress(progress[0]);
                    if (nextSection[0]) {
                        updateRows();
                    }
                };

                AndroidUtilities.runOnUIThread(() -> {
                    if (!done[0]) {
                        showDialog(bottomSheet);
                    }
                }, 150);

                cleanupFolders(
                    (progressValue, next) -> {
                        progress[0] = progressValue;
                        nextSection[0] = next;
                        AndroidUtilities.cancelRunOnUIThread(updateProgress);
                        AndroidUtilities.runOnUIThread(updateProgress);
                    },
                    () -> AndroidUtilities.runOnUIThread(() -> {
                        done[0] = true;
                        cacheView.setProgress(1F);
                        bottomSheet.dismiss();
                    })
                );
            });
        }

        public void updateSize() {
            long size = (
                (selected[0] ? photoSize : 0) +
                (selected[1] ? videoSize : 0) +
                (selected[2] ? documentsSize : 0) +
                (selected[3] ? musicSize : 0) +
                (selected[4] ? audioSize : 0) +
                (selected[5] ? stickersCacheSize : 0) +
                (selected[6] ? cacheSize : 0) +
                (selected[7] ? cacheTempSize : 0)
            );
            setSize(
                isAllSectionsSelected(),
                size
            );
        }
    }

    private boolean isAllSectionsSelected() {
        for (int i = 0; i < itemInners.size(); ++i) {
            ItemInner item = itemInners.get(i);
            if (item.viewType != VIEW_TYPE_SECTION) {
                continue;
            }
            int index = item.index;
            if (index < 0) {
                index = selected.length - 1;
            }
            if (!selected[index]) {
                return false;
            }
        }
        return true;
    }

    public static class ClearCacheButton extends FrameLayout {
        FrameLayout button;
        AnimatedTextView.AnimatedTextDrawable textView;
        AnimatedTextView.AnimatedTextDrawable valueTextView;

        TextView rtlTextView;

        public ClearCacheButton(Context context) {
            super(context);

            button = new FrameLayout(context) {
                @Override
                protected void dispatchDraw(Canvas canvas) {
                    final int margin = AndroidUtilities.dp(8);
                    int x = (getMeasuredWidth() - margin - (int) valueTextView.getCurrentWidth() + (int) textView.getCurrentWidth()) / 2;

                    if (LocaleController.isRTL) {
                        super.dispatchDraw(canvas);
                    } else {
                        textView.setBounds(0, 0, x, getHeight());
                        textView.draw(canvas);

                        valueTextView.setBounds(x + AndroidUtilities.dp(8), 0, getWidth(), getHeight());
                        valueTextView.draw(canvas);
                    }
                }

                @Override
                protected boolean verifyDrawable(@NonNull Drawable who) {
                    return who == valueTextView || who == textView || super.verifyDrawable(who);
                }

                @Override
                public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(info);
                    info.setClassName("android.widget.Button");
                }
            };
            button.setBackground(Theme.AdaptiveRipple.filledRect(Theme.key_featuredStickers_addButton, 8));
            button.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

            if (LocaleController.isRTL) {
                rtlTextView = new TextView(context);
                rtlTextView.setText(LocaleController.getString("ClearCache", R.string.ClearCache));
                rtlTextView.setGravity(Gravity.CENTER);
                rtlTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                rtlTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                rtlTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
                button.addView(rtlTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
            }

            textView = new AnimatedTextView.AnimatedTextDrawable(true, true, true);
            textView.setAnimationProperties(.25f, 0, 300, CubicBezierInterpolator.EASE_OUT_QUINT);
            textView.setCallback(button);
            textView.setTextSize(AndroidUtilities.dp(14));
            textView.setText(LocaleController.getString("ClearCache", R.string.ClearCache));
            textView.setGravity(Gravity.RIGHT);
            textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            textView.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));

            valueTextView = new AnimatedTextView.AnimatedTextDrawable(true, true, true);
            valueTextView.setAnimationProperties(.25f, 0, 300, CubicBezierInterpolator.EASE_OUT_QUINT);
            valueTextView.setCallback(button);
            valueTextView.setTextSize(AndroidUtilities.dp(14));
            valueTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            valueTextView.setTextColor(Theme.adaptHSV(Theme.getColor(Theme.key_featuredStickers_addButton), -.46f, +.08f));
            valueTextView.setText("");

            button.setContentDescription(TextUtils.concat(textView.getText(), "\t", valueTextView.getText()));

            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL, 16, 16, 16, 16));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                heightMeasureSpec
            );
        }

        public void setSize(boolean allSelected, long size) {
            textView.setText((
                allSelected ?
                    LocaleController.getString("ClearCache", R.string.ClearCache) :
                    LocaleController.getString("ClearSelectedCache", R.string.ClearSelectedCache)
            ));
            valueTextView.setText(size <= 0 ? "" : AndroidUtilities.formatFileSize(size));
            setDisabled(size <= 0);
            button.invalidate();

            button.setContentDescription(TextUtils.concat(textView.getText(), "\t", valueTextView.getText()));
        }

        public void setDisabled(boolean disabled) {
            button.animate().cancel();
            button.animate().alpha(disabled ? .65f : 1f).start();
            button.setClickable(!disabled);
        }
    }

    private boolean isOtherSelected() {
        boolean[] indexes = new boolean[CacheControlActivity.this.selected.length];
        for (int i = 0; i < itemInners.size(); ++i) {
            ItemInner item2 = itemInners.get(i);
            if (item2.viewType == VIEW_TYPE_SECTION && !item2.pad && item2.index >= 0) {
                indexes[item2.index] = true;
            }
        }
        for (int i = 0; i < indexes.length; ++i) {
            if (!indexes[i] && !CacheControlActivity.this.selected[i]) {
                return false;
            }
        }
        return true;
    }

    private void toggleSection(ItemInner item, View cell) {
        if (item.index < 0) {
            toggleOtherSelected(cell);
            return;
        }
        if (selected[item.index] && sectionsSelected() <= 1) {
            BotWebViewVibrationEffect.APP_ERROR.vibrate();
            if (cell != null) {
                AndroidUtilities.shakeViewSpring(cell, -3);
            }
            return;
        }
        if (cell instanceof CheckBoxCell) {
            ((CheckBoxCell) cell).setChecked(selected[item.index] = !selected[item.index], true);
        } else {
            selected[item.index] = !selected[item.index];
            int position = itemInners.indexOf(item);
            if (position >= 0) {
                for (int i = 0; i < listView.getChildCount(); ++i) {
                    View child = listView.getChildAt(i);
                    if (child instanceof CheckBoxCell && position == listView.getChildAdapterPosition(child)) {
                        ((CheckBoxCell) child).setChecked(selected[item.index], true);
                    }
                }
            }
        }
        if (item.pad) {
            for (int i = 0; i < listView.getChildCount(); ++i) {
                View child = listView.getChildAt(i);
                if (child instanceof CheckBoxCell) {
                    int pos = listView.getChildAdapterPosition(child);
                    if (pos >= 0 && pos < itemInners.size() && itemInners.get(pos).index < 0) {
                        ((CheckBoxCell) child).setChecked(isOtherSelected(), true);
                        break;
                    }
                }
            }
        }
        updateChart();
    }

    private void toggleOtherSelected(View cell) {
        boolean selected = isOtherSelected();

        if (selected) {
            boolean hasNonOtherSelected = false;
            for (int i = 0; i < itemInners.size(); ++i) {
                ItemInner item2 = itemInners.get(i);
                if (item2.viewType == VIEW_TYPE_SECTION && !item2.pad && item2.index >= 0 && CacheControlActivity.this.selected[item2.index]) {
                    hasNonOtherSelected = true;
                    break;
                }
            }

            if (!hasNonOtherSelected) {
                BotWebViewVibrationEffect.APP_ERROR.vibrate();
                if (cell != null) {
                    AndroidUtilities.shakeViewSpring(cell, -3);
                }
                return;
            }
        }

        if (collapsed) {
            boolean[] indexes = new boolean[CacheControlActivity.this.selected.length];
            for (int i = 0; i < itemInners.size(); ++i) {
                ItemInner item2 = itemInners.get(i);
                if (item2.viewType == VIEW_TYPE_SECTION && !item2.pad && item2.index >= 0) {
                    indexes[item2.index] = true;
                }
            }
            for (int i = 0; i < indexes.length; ++i) {
                if (!indexes[i]) {
                    CacheControlActivity.this.selected[i] = !selected;
                }
            }
        } else {
            for (int i = 0; i < itemInners.size(); ++i) {
                ItemInner item2 = itemInners.get(i);
                if (item2.viewType == VIEW_TYPE_SECTION && item2.pad && item2.index >= 0) {
                    CacheControlActivity.this.selected[item2.index] = !selected;
                }
            }
        }

        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            if (child instanceof CheckBoxCell) {
                int pos = listView.getChildAdapterPosition(child);
                if (pos >= 0) {
                    ItemInner item2 = itemInners.get(pos);
                    if (item2.viewType == VIEW_TYPE_SECTION) {
                        if (item2.index < 0) {
                            ((CheckBoxCell) child).setChecked(!selected, true);
                        } else {
                            ((CheckBoxCell) child).setChecked(CacheControlActivity.this.selected[item2.index], true);
                        }
                    }
                }
            }
        }
        updateChart();
    }

    private class ListAdapter extends AdapterWithDiffUtils {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == migrateOldFolderRow || (holder.getItemViewType() == VIEW_TYPE_STORAGE && (totalSize > 0) && !calculating) || holder.getItemViewType() == VIEW_TYPE_CHAT || holder.getItemViewType() == VIEW_TYPE_KEEP_MEDIA_CELL || holder.getItemViewType() == VIEW_TYPE_SECTION;
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
                case VIEW_TYPE_KEEP_MEDIA_CELL:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_CHART:
                    view = cacheChart = new CacheChart(mContext) {
                        @Override
                        protected void onSectionClick(int index) {
//                            if (index == 8) {
//                                index = -1;
//                            }
//                            for (int i = 0; i < itemInners.size(); ++i) {
//                                ItemInner item = itemInners.get(i);
//                                if (item != null && item.index == index) {
//                                    toggleSection(item, null);
//                                    return;
//                                }
//                            }
                        }

                        @Override
                        protected void onSectionDown(int index, boolean down) {
                            if (!down) {
                                listView.removeHighlightRow();
                                return;
                            }
                            if (index == 8) {
                                index = -1;
                            }
                            int position = -1;
                            for (int i = 0; i < itemInners.size(); ++i) {
                                ItemInner item2 = itemInners.get(i);
                                if (item2 != null && item2.viewType == VIEW_TYPE_SECTION && item2.index == index) {
                                    position = i;
                                    break;
                                }
                            }

                            if (position >= 0) {
                                final int finalPosition = position;
                                listView.highlightRow(() -> finalPosition, 0);
                            } else {
                                listView.removeHighlightRow();
                            }
                        }
                    };
                    break;
                case VIEW_TYPE_CHART_HEADER:
                    view = cacheChartHeader = new CacheChartHeader(mContext);
                    break;
                case VIEW_TYPE_SECTION_LOADING:
                    FlickerLoadingView flickerLoadingView2 = new FlickerLoadingView(getContext());
                    flickerLoadingView2.setIsSingleCell(true);
                    flickerLoadingView2.setItemsCount(1);
                    flickerLoadingView2.setIgnoreHeightCheck(true);
                    flickerLoadingView2.setViewType(FlickerLoadingView.CHECKBOX_TYPE);
                    flickerLoadingView2.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = flickerLoadingView2;
                    break;
                case VIEW_TYPE_SECTION:
                    view = new CheckBoxCell(mContext, 4, 21, getResourceProvider());
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_INFO:
                default:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case VIEW_TYPE_CACHE_VIEW_PAGER:
                    view = cachedMediaLayout = new CachedMediaLayout(mContext, CacheControlActivity.this) {
                        @Override
                        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightMeasureSpec) - (ActionBar.getCurrentActionBarHeight() / 2), MeasureSpec.EXACTLY));
                        }

                        @Override
                        protected void showActionMode(boolean show) {
                            if (show) {
                                updateActionBar(true);
                                actionBar.showActionMode();
                            } else {
                                actionBar.hideActionMode();
                            }
                        }

                        @Override
                        protected boolean actionModeIsVisible() {
                            return actionBar.isActionModeShowed();
                        }
                    };
                    cachedMediaLayout.setDelegate(new CachedMediaLayout.Delegate() {
                        @Override
                        public void onItemSelected(DialogFileEntities entities, CacheModel.FileInfo fileInfo, boolean longPress) {
                            if (entities != null) {
                                if ((cacheModel.getSelectedFiles() > 0 || longPress)) {
                                    cacheModel.toggleSelect(entities);
                                    cachedMediaLayout.updateVisibleRows();
                                    updateActionMode();
                                } else {
                                    showClearCacheDialog(entities);
                                }
                                return;
                            }
                            if (fileInfo != null) {
                                cacheModel.toggleSelect(fileInfo);
                                cachedMediaLayout.updateVisibleRows();
                                updateActionMode();
                            }
                        }

                        @Override
                        public void clear() {
                            clearSelectedFiles();
                        }

                        @Override
                        public void clearSelection() {
                            if (cacheModel != null && cacheModel.getSelectedFiles() > 0) {
                                cacheModel.clearSelection();
                                if (cachedMediaLayout != null) {
                                    cachedMediaLayout.showActionMode(false);
                                    cachedMediaLayout.updateVisibleRows();
                                }
                                return;
                            }
                        }
                    });
                    cachedMediaLayout.setCacheModel(cacheModel);
                    nestedSizeNotifierLayout.setChildLayout(cachedMediaLayout);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                    break;
                case VIEW_TYPE_CLEAR_CACHE_BUTTON:
                    view = clearCacheButton = new ClearCacheButtonInternal(mContext);
                    break;
                case VIEW_TYPE_MAX_CACHE_SIZE:
                    SlideChooseView slideChooseView2 = new SlideChooseView(mContext);
                    view = slideChooseView2;
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

                    float totalSizeInGb = (int) (totalDeviceSize / 1024L / 1024L) / 1000.0f;
                    ArrayList<Integer> options = new ArrayList<>();
//                    if (BuildVars.DEBUG_PRIVATE_VERSION) {
//                        options.add(1);
//                    }
                    if (totalSizeInGb <= 17) {
                        options.add(2);
                    }
                    if (totalSizeInGb > 5) {
                        options.add(5);
                    }
                    if (totalSizeInGb > 16) {
                        options.add(16);
                    }
                    if (totalSizeInGb > 32) {
                        options.add(32);
                    }
                    options.add(Integer.MAX_VALUE);
                    String[] values = new String[options.size()];
                    for (int i = 0; i < options.size(); i++) {
                        if (options.get(i) == 1) {
                            values[i] = String.format("300 MB");
                        } else if (options.get(i) == Integer.MAX_VALUE) {
                            values[i] = LocaleController.getString("NoLimit", R.string.NoLimit);
                        } else {
                            values[i] = String.format("%d GB", options.get(i));
                        }
                    }
                    slideChooseView2.setCallback(i -> {
                        SharedConfig.getPreferences().edit().putInt("cache_limit", options.get(i)).apply();
                    });
                    int currentLimit = SharedConfig.getPreferences().getInt("cache_limit", Integer.MAX_VALUE);
                    int i = options.indexOf(currentLimit);
                    if (i < 0) {
                        i = options.size() - 1;
                    }
                    slideChooseView2.setOptions(i, values);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            final ItemInner item = itemInners.get(position);
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_CHART:
//                    updateChart();
                    break;
                case VIEW_TYPE_CHART_HEADER:
                    if (cacheChartHeader != null && !calculating) {
                        cacheChartHeader.setData(
                            totalSize > 0,
                            totalDeviceSize <= 0 ? 0 : (float) totalSize / totalDeviceSize,
                            totalDeviceFreeSize <= 0 || totalDeviceSize <= 0 ? 0 : (float) (totalDeviceSize - totalDeviceFreeSize) / totalDeviceSize
                        );
                    }
                    break;
                case VIEW_TYPE_SECTION:
                    CheckBoxCell cell = (CheckBoxCell) holder.itemView;
                    final boolean selected;
                    if (item.index < 0) {
                        selected = isOtherSelected();
                    } else {
                        selected = CacheControlActivity.this.selected[item.index];
                    }
                    cell.setText(getCheckBoxTitle(item.headerName, percents[item.index < 0 ? 8 : item.index], item.index < 0), AndroidUtilities.formatFileSize(item.size), selected, item.index < 0 ? !collapsed : !item.last);
                    cell.setCheckBoxColor(item.colorKey, Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_checkboxCheck);
                    cell.setCollapsed(item.index < 0 ? collapsed : null);
                    if (item.index == -1) {
                        cell.setOnSectionsClickListener(e -> {
                            collapsed = !collapsed;
                            updateRows();
                            updateChart();
                        }, e -> toggleOtherSelected(cell));
                    } else {
                        cell.setOnSectionsClickListener(null, null);
                    }
                    cell.setPad(item.pad ? 1 : 0);
                    break;
                case VIEW_TYPE_KEEP_MEDIA_CELL:
                    TextCell textCell2 = (TextCell) holder.itemView;
                    CacheByChatsController cacheByChatsController = getMessagesController().getCacheByChatsController();
                    int keepMediaType = item.keepMediaType;
                    int exceptionsCount = cacheByChatsController.getKeepMediaExceptions(itemInners.get(position).keepMediaType).size();
                    String subtitle = null;
                    if (exceptionsCount > 0) {
                        subtitle = LocaleController.formatPluralString("ExceptionShort", exceptionsCount, exceptionsCount);
                    }
                    String value = CacheByChatsController.getKeepMediaString(cacheByChatsController.getKeepMedia(keepMediaType));
                    if (itemInners.get(position).keepMediaType == KEEP_MEDIA_TYPE_USER) {
                        textCell2.setTextAndValueAndColorfulIcon(LocaleController.getString("PrivateChats", R.string.PrivateChats), value, true, R.drawable.msg_filled_menu_users, getThemedColor(Theme.key_statisticChartLine_lightblue), true);
                    } else if (itemInners.get(position).keepMediaType == KEEP_MEDIA_TYPE_GROUP) {
                        textCell2.setTextAndValueAndColorfulIcon(LocaleController.getString("GroupChats", R.string.GroupChats), value, true, R.drawable.msg_filled_menu_groups, getThemedColor(Theme.key_statisticChartLine_green), true);
                    } else if (itemInners.get(position).keepMediaType == KEEP_MEDIA_TYPE_CHANNEL) {
                        textCell2.setTextAndValueAndColorfulIcon(LocaleController.getString("CacheChannels", R.string.CacheChannels), value, true, R.drawable.msg_filled_menu_channels, getThemedColor(Theme.key_statisticChartLine_golden), true);
                    }
                    textCell2.setSubtitle(subtitle);
                    break;
                case VIEW_TYPE_TEXT_SETTINGS:
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
//                    if (position == databaseRow) {
//                        textCell.setTextAndValue(LocaleController.getString("ClearLocalDatabase", R.string.ClearLocalDatabase), AndroidUtilities.formatFileSize(databaseSize), updateDatabaseSize, false);
//                        updateDatabaseSize = false;
//                    } else
                    if (position == migrateOldFolderRow) {
                        textCell.setTextAndValue(LocaleController.getString("MigrateOldFolder", R.string.MigrateOldFolder), null, false);
                    }
                    break;
                case VIEW_TYPE_INFO:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
//                    if (position == databaseInfoRow) {
//                        privacyCell.setText(LocaleController.getString("LocalDatabaseInfo", R.string.LocalDatabaseInfo));
//                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
//                    } else if (position == keepMediaInfoRow) {
//                        privacyCell.setText(AndroidUtilities.replaceTags(LocaleController.getString("KeepMediaInfo", R.string.KeepMediaInfo)));
//                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
//                    } else {
                        privacyCell.setText(AndroidUtilities.replaceTags(item.text));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
//                    }
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

            }
        }

        @Override
        public int getItemViewType(int i) {
            return itemInners.get(i).viewType;
        }
    }

    private void updateActionMode() {
        if (cacheModel.getSelectedFiles() > 0) {
            if (cachedMediaLayout != null) {
                String filesString;
                if (!cacheModel.selectedDialogs.isEmpty()) {
                    int filesInChats = 0;
                    for (CacheControlActivity.DialogFileEntities entity : cacheModel.entities) {
                        if (cacheModel.selectedDialogs.contains(entity.dialogId)) {
                            filesInChats += entity.filesCount;
                        }
                    }
                    int filesNotInChat = cacheModel.getSelectedFiles() - filesInChats;
                    if (filesNotInChat > 0) {
                        filesString = String.format("%s, %s",
                                LocaleController.formatPluralString("Chats", cacheModel.selectedDialogs.size(), cacheModel.selectedDialogs.size()),
                                LocaleController.formatPluralString("Files", filesNotInChat, filesNotInChat)
                        );
                    } else {
                        filesString = LocaleController.formatPluralString("Chats", cacheModel.selectedDialogs.size(), cacheModel.selectedDialogs.size());
                    }
                } else {
                    filesString = LocaleController.formatPluralString("Files", cacheModel.getSelectedFiles(), cacheModel.getSelectedFiles());
                }
                String sizeString = AndroidUtilities.formatFileSize(cacheModel.getSelectedFilesSize());
                actionModeTitle.setText(sizeString);
                actionModeSubtitle.setText(filesString);
                cachedMediaLayout.showActionMode(true);
            }
        } else {
            cachedMediaLayout.showActionMode(false);
            return;
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

    public static class UserCell extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

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

    public static class DialogFileEntities {

        public long dialogId;
        int filesCount;
        long totalSize;
        public final SparseArray<FileEntities> entitiesByType = new SparseArray<>();

        public DialogFileEntities(long dialogId) {
            this.dialogId = dialogId;
        }

        public void addFile(CacheModel.FileInfo file, int type) {
            FileEntities entities = entitiesByType.get(type, null);
            if (entities == null) {
                entities = new FileEntities();
                entitiesByType.put(type, entities);
            }
            entities.count++;
            long fileSize = file.size;
            entities.totalSize += fileSize;
            totalSize += fileSize;
            filesCount++;
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
            filesCount += dialogEntities.filesCount;
        }

        public void removeFile(CacheModel.FileInfo fileInfo) {
            FileEntities entities = entitiesByType.get(fileInfo.type, null);
            if (entities == null) {
                return;
            }
            if (entities.files.remove(fileInfo)) {
                entities.count--;
                entities.totalSize -= fileInfo.size;
                totalSize -= fileInfo.size;
                filesCount--;
            }
        }

        public boolean isEmpty() {
            return totalSize <= 0;
        }

        public CacheModel createCacheModel() {
            CacheModel cacheModel = new CacheModel(true);
            if (entitiesByType.get(TYPE_PHOTOS) != null) {
                cacheModel.media.addAll(entitiesByType.get(TYPE_PHOTOS).files);
            }
            if (entitiesByType.get(TYPE_VIDEOS) != null) {
                cacheModel.media.addAll(entitiesByType.get(TYPE_VIDEOS).files);
            }
            if (entitiesByType.get(TYPE_DOCUMENTS) != null) {
                cacheModel.documents.addAll(entitiesByType.get(TYPE_DOCUMENTS).files);
            }
            if (entitiesByType.get(TYPE_MUSIC) != null) {
                cacheModel.music.addAll(entitiesByType.get(TYPE_MUSIC).files);
            }
            if (entitiesByType.get(TYPE_VOICE) != null) {
                cacheModel.voice.addAll(entitiesByType.get(TYPE_VOICE).files);
            }
            cacheModel.selectAllFiles();
            cacheModel.sortBySize();
            return cacheModel;
        }
    }

    public static class FileEntities {
        public long totalSize;
        public int count;
        public ArrayList<CacheModel.FileInfo> files = new ArrayList<>();
    }

    public static class ItemInner extends AdapterWithDiffUtils.Item {

        int headerTopMargin = 15;
        int headerBottomMargin = 0;
        int keepMediaType = -1;
        CharSequence headerName;
        String text;
        DialogFileEntities entities;

        public int index;
        public long size;
        String colorKey;
        public boolean pad;
        boolean last;

        public ItemInner(int viewType, String headerName, DialogFileEntities dialogFileEntities) {
            super(viewType, true);
            this.headerName = headerName;
            this.entities = dialogFileEntities;
        }

        public ItemInner(int viewType, int keepMediaType) {
            super(viewType, true);
            this.keepMediaType = keepMediaType;
        }

        public ItemInner(int viewType, String headerName, int headerTopMargin, int headerBottomMargin, DialogFileEntities dialogFileEntities) {
            super(viewType, true);
            this.headerName = headerName;
            this.headerTopMargin = headerTopMargin;
            this.headerBottomMargin = headerBottomMargin;
            this.entities = dialogFileEntities;
        }

        private ItemInner(int viewType) {
            super(viewType, true);
        }

        public static ItemInner asCheckBox(CharSequence text, int index, long size, String colorKey) {
            return asCheckBox(text, index, size, colorKey, false);
        }

        public static ItemInner asCheckBox(CharSequence text, int index, long size, String colorKey, boolean last) {
            ItemInner item = new ItemInner(VIEW_TYPE_SECTION);
            item.index = index;
            item.headerName = text;
            item.size = size;
            item.colorKey = colorKey;
            item.last = last;
            return item;
        }

        public static ItemInner asInfo(String text) {
            ItemInner item = new ItemInner(VIEW_TYPE_INFO);
            item.text = text;
            return item;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ItemInner itemInner = (ItemInner) o;
            if (viewType == itemInner.viewType) {
                if (viewType == VIEW_TYPE_CHART || viewType == VIEW_TYPE_CHART_HEADER) {
                    return true;
                }
                if (viewType == VIEW_TYPE_CHAT && entities != null && itemInner.entities != null) {
                    return entities.dialogId == itemInner.entities.dialogId;
                }
                if (viewType == VIEW_TYPE_CACHE_VIEW_PAGER || viewType == VIEW_TYPE_CHOOSER || viewType == VIEW_TYPE_STORAGE || viewType == VIEW_TYPE_TEXT_SETTINGS || viewType == VIEW_TYPE_CLEAR_CACHE_BUTTON) {
                    return true;
                }
                if (viewType == VIEW_TYPE_HEADER) {
                    return Objects.equals(headerName, itemInner.headerName);
                }
                if (viewType == VIEW_TYPE_INFO) {
                    return Objects.equals(text, itemInner.text);
                }
                if (viewType == VIEW_TYPE_SECTION) {
                    return index == itemInner.index && size == itemInner.size;
                }
                if (viewType == VIEW_TYPE_KEEP_MEDIA_CELL) {
                    return keepMediaType == itemInner.keepMediaType;
                }
                return false;
            }
            return false;
        }
    }

    AnimatedTextView selectedDialogsCountTextView;

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        if (cachedMediaLayout != null) {
            cachedMediaLayout.getHitRect(AndroidUtilities.rectTmp2);
            if (!AndroidUtilities.rectTmp2.contains((int) event.getX(), (int) event.getY() - actionBar.getMeasuredHeight())) {
                return true;
            } else {
                return cachedMediaLayout.viewPagerFixed.isCurrentTabFirst();
            }
        }
        return true;
    }

    @Override
    public boolean onBackPressed() {
        if (cacheModel != null && !cacheModel.selectedFiles.isEmpty()) {
            cacheModel.clearSelection();
            if (cachedMediaLayout != null) {
                cachedMediaLayout.showActionMode(false);
                cachedMediaLayout.updateVisibleRows();
            }
            return false;
        }
        return super.onBackPressed();
    }
}
