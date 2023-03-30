package org.telegram.messenger;

import android.util.SparseArray;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AutoDeleteMediaTask {

    public static Set<String> usingFilePaths = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static void run() {
        int time = (int) (System.currentTimeMillis() / 1000);
        if (Math.abs(time - SharedConfig.lastKeepMediaCheckTime) < 24 * 60 * 60) {
            return;
        }
        SharedConfig.lastKeepMediaCheckTime = time;
        File cacheDir = FileLoader.checkDirectory(FileLoader.MEDIA_DIR_CACHE);

        Utilities.cacheClearQueue.postRunnable(() -> {
            long startTime = System.currentTimeMillis();
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("checkKeepMedia start task");
            }
            boolean hasExceptions = false;
            ArrayList<CacheByChatsController> cacheByChatsControllers = new ArrayList<>();
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                if (UserConfig.getInstance(account).isClientActivated()) {
                    CacheByChatsController cacheByChatsController = UserConfig.getInstance(account).getMessagesController().getCacheByChatsController();
                    cacheByChatsControllers.add(cacheByChatsController);
                    if (cacheByChatsController.getKeepMediaExceptionsByDialogs().size() > 0) {
                        hasExceptions = true;
                    }
                }
            }

            int[] keepMediaByTypes = new int[3];
            boolean allKeepMediaTypesForever = true;
            long keepMediaMinSeconds = Long.MAX_VALUE;
            for (int i = 0; i < 3; i++) {
                keepMediaByTypes[i] = SharedConfig.getPreferences().getInt("keep_media_type_" + i, CacheByChatsController.getDefault(i));
                if (keepMediaByTypes[i] != CacheByChatsController.KEEP_MEDIA_FOREVER) {
                    allKeepMediaTypesForever = false;
                }
                long days = CacheByChatsController.getDaysInSeconds(keepMediaByTypes[i]);
                if (days < keepMediaMinSeconds) {
                    keepMediaMinSeconds = days;
                }
            }
            if (hasExceptions) {
                allKeepMediaTypesForever = false;
            }
            int autoDeletedFiles = 0;
            long autoDeletedFilesSize = 0;

            int deletedFilesBySize = 0;
            long deletedFilesBySizeSize = 0;
            int skippedFiles = 0;

            if (!allKeepMediaTypesForever) {
                //long currentTime = time - 60 * 60 * 24 * days;
                final SparseArray<File> paths = ImageLoader.getInstance().createMediaPaths();
                for (int a = 0; a < paths.size(); a++) {
                    boolean isCacheDir = false;
                    if (paths.keyAt(a) == FileLoader.MEDIA_DIR_CACHE) {
                        isCacheDir = true;
                    }
                    File dir = paths.valueAt(a);
                    try {
                        File[] files = dir.listFiles();
                        ArrayList<CacheByChatsController.KeepMediaFile> keepMediaFiles = new ArrayList<>();
                        if (files != null) {
                            for (int i = 0; i < files.length; i++) {
                                if (files[i].isDirectory() || usingFilePaths.contains(files[i].getAbsolutePath())) {
                                    continue;
                                }
                                keepMediaFiles.add(new CacheByChatsController.KeepMediaFile(files[i]));
                            }
                        }
                        for (int i = 0; i < cacheByChatsControllers.size(); i++) {
                            cacheByChatsControllers.get(i).lookupFiles(keepMediaFiles);
                        }
                        for (int i = 0; i < keepMediaFiles.size(); i++) {
                            CacheByChatsController.KeepMediaFile file = keepMediaFiles.get(i);
                            if (file.keepMedia == CacheByChatsController.KEEP_MEDIA_FOREVER) {
                                continue;
                            }
                            long seconds;
                            if (file.keepMedia >= 0) {
                                seconds = CacheByChatsController.getDaysInSeconds(file.keepMedia);
                            } else if (file.dialogType >= 0) {
                                seconds = CacheByChatsController.getDaysInSeconds(keepMediaByTypes[file.dialogType]);
                            } else if (isCacheDir) {
                                continue;
                            } else {
                                seconds = keepMediaMinSeconds;
                            }
                            if (seconds == Long.MAX_VALUE) {
                                continue;
                            }
                            long lastUsageTime = Utilities.getLastUsageFileTime(file.file.getAbsolutePath());
                            long timeLocal = time - seconds;
                            boolean needDelete = lastUsageTime > 316000000 && lastUsageTime < timeLocal && !usingFilePaths.contains(file.file.getPath());
                            if (needDelete) {
                                try {
                                    if (BuildVars.LOGS_ENABLED) {
                                        autoDeletedFiles++;
                                        autoDeletedFilesSize += file.file.length();
                                    }
                                    if (BuildVars.DEBUG_PRIVATE_VERSION) {
                                        FileLog.d("delete file " + file.file.getPath() + " last_usage_time=" + lastUsageTime + " time_local=" + timeLocal);
                                    }
                                    file.file.delete();
                                } catch (Exception exception) {
                                    FileLog.e(exception);
                                }
                            }
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
            }

            int maxCacheGb = SharedConfig.getPreferences().getInt("cache_limit", Integer.MAX_VALUE);
            if (maxCacheGb != Integer.MAX_VALUE) {
                long maxCacheSize;
                if (maxCacheGb == 1) {
                    maxCacheSize = 1024L * 1024L * 300L;
                } else {
                    maxCacheSize = maxCacheGb * 1024L * 1024L * 1000L;
                }
                final SparseArray<File> paths = ImageLoader.getInstance().createMediaPaths();
                long totalSize = 0;
                for (int a = 0; a < paths.size(); a++) {
                    totalSize += Utilities.getDirSize(paths.valueAt(a).getAbsolutePath(), 0, true);
                }
                if (totalSize > maxCacheSize) {
                    ArrayList<FileInfoInternal> allFiles = new ArrayList<>();
                    for (int a = 0; a < paths.size(); a++) {
                        File dir = paths.valueAt(a);
                        fillFilesRecursive(dir, allFiles);
                    }
                    for (int i = 0; i < cacheByChatsControllers.size(); i++) {
                        cacheByChatsControllers.get(i).lookupFiles(allFiles);
                    }
                    Collections.sort(allFiles, (o1, o2) -> {
                        if (o2.lastUsageDate > o1.lastUsageDate) {
                            return -1;
                        } else if (o2.lastUsageDate < o1.lastUsageDate) {
                            return 1;
                        }
                        return 0;
                    });

                    for (int i = 0; i < allFiles.size(); i++) {
                        if (allFiles.get(i).keepMedia == CacheByChatsController.KEEP_MEDIA_FOREVER) {
                            continue;
                        }
                        if (allFiles.get(i).lastUsageDate <= 0) {
                            skippedFiles++;
                            continue;
                        }
                        long size = allFiles.get(i).file.length();
                        totalSize -= size;

                        try {
                            deletedFilesBySize++;
                            deletedFilesBySizeSize += size;
                            allFiles.get(i).file.delete();
                        } catch (Exception e) {

                        }

                        if (totalSize < maxCacheSize) {
                            break;
                        }
                    }
                }
            }

            File stickersPath = new File(cacheDir, "acache");
            if (stickersPath.exists()) {
                long currentTime = time - 60 * 60 * 24;
                try {
                    Utilities.clearDir(stickersPath.getAbsolutePath(), 0, currentTime, false);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
            MessagesController.getGlobalMainSettings().edit()
                    .putInt("lastKeepMediaCheckTime", SharedConfig.lastKeepMediaCheckTime)
                    .apply();

            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("checkKeepMedia task end time " + (System.currentTimeMillis() - startTime) + " auto deleted info: files " + autoDeletedFiles + " size " + AndroidUtilities.formatFileSize(autoDeletedFilesSize) + "   deleted by size limit info: files " + deletedFilesBySize + " size " + AndroidUtilities.formatFileSize(deletedFilesBySizeSize) + " unknownTimeFiles " + skippedFiles);
            }
        });
    }

    private static void fillFilesRecursive(final File fromFolder, ArrayList<FileInfoInternal> fileInfoList) {
        if (fromFolder == null) {
            return;
        }
        File[] files = fromFolder.listFiles();
        if (files == null) {
            return;
        }
        for (final File fileEntry : files) {
            if (fileEntry.isDirectory()) {
                fillFilesRecursive(fileEntry, fileInfoList);
            } else {
                if (fileEntry.getName().equals(".nomedia")) {
                    continue;
                }
                if (usingFilePaths.contains(fileEntry.getAbsolutePath())) {
                    continue;
                }
                fileInfoList.add(new FileInfoInternal(fileEntry));
            }
        }
    }

    private static class FileInfoInternal extends CacheByChatsController.KeepMediaFile {
        final long lastUsageDate;

        private FileInfoInternal(File file) {
            super(file);
            this.lastUsageDate = Utilities.getLastUsageFileTime(file.getAbsolutePath());
        }
    }

    public static void lockFile(File file) {
        if (file == null) {
            return;
        }
        lockFile(file.getAbsolutePath());
    }

    public static void unlockFile(File file) {
        if (file == null) {
            return;
        }
        unlockFile(file.getAbsolutePath());
    }

    public static void lockFile(String file) {
        if (file == null) {
            return;
        }
        usingFilePaths.add(file);
    }

    public static void unlockFile(String file) {
        if (file == null) {
            return;
        }
        usingFilePaths.remove(file);
    }

}
