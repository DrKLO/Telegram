package org.telegram.messenger;

import android.os.Looper;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLiteException;
import org.telegram.SQLite.SQLitePreparedStatement;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

public class FilePathDatabase {

    private final DispatchQueue dispatchQueue;
    private final int currentAccount;

    private SQLiteDatabase database;
    private File cacheFile;
    private File shmCacheFile;

    private final static int LAST_DB_VERSION = 2;

    private final static String DATABASE_NAME = "file_to_path";
    private final static String DATABASE_BACKUP_NAME = "file_to_path_backup";

    public FilePathDatabase(int currentAccount) {
        this.currentAccount = currentAccount;
        dispatchQueue = new DispatchQueue("files_database_queue_" + currentAccount);
        dispatchQueue.postRunnable(() -> {
            createDatabase(0, false);
        });
    }

    public void createDatabase(int tryCount, boolean fromBackup) {
        File filesDir = ApplicationLoader.getFilesDirFixed();
        if (currentAccount != 0) {
            filesDir = new File(filesDir, "account" + currentAccount + "/");
            filesDir.mkdirs();
        }
        cacheFile = new File(filesDir, DATABASE_NAME + ".db");
        shmCacheFile = new File(filesDir, DATABASE_NAME + ".db-shm");


        boolean createTable = false;


        if (!cacheFile.exists()) {
            createTable = true;
        }
        try {
            database = new SQLiteDatabase(cacheFile.getPath());
            database.executeFast("PRAGMA secure_delete = ON").stepThis().dispose();
            database.executeFast("PRAGMA temp_store = MEMORY").stepThis().dispose();

            if (createTable) {
                database.executeFast("CREATE TABLE paths(document_id INTEGER, dc_id INTEGER, type INTEGER, path TEXT, PRIMARY KEY(document_id, dc_id, type));").stepThis().dispose();
                database.executeFast("CREATE INDEX IF NOT EXISTS path_in_paths ON paths(path);").stepThis().dispose();
                database.executeFast("PRAGMA user_version = " + LAST_DB_VERSION).stepThis().dispose();
            } else {
                int version = database.executeInt("PRAGMA user_version");
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("current files db version = " + version);
                }
                if (version == 0) {
                    throw new Exception("malformed");
                }
                migrateDatabase(version);
                //migration
            }
            if (!fromBackup) {
                createBackup();
            }
            FileLog.d("files db created from_backup= " + fromBackup);
        } catch (Exception e) {
            if (tryCount < 4) {
                if (!fromBackup && restoreBackup()) {
                    createDatabase(tryCount + 1, true);
                    return;
                } else {
                    cacheFile.delete();
                    shmCacheFile.delete();
                    createDatabase(tryCount + 1, false);
                }
            }
            if (BuildVars.DEBUG_VERSION) {
                FileLog.e(e);
            }
        }
    }

    private void migrateDatabase(int version) throws SQLiteException {
        if (version == 1) {
            database.executeFast("CREATE INDEX IF NOT EXISTS path_in_paths ON paths(path);").stepThis().dispose();
            database.executeFast("PRAGMA user_version = " + 2).stepThis().dispose();
            version = 2;
        }
    }

    private void createBackup() {
        File filesDir = ApplicationLoader.getFilesDirFixed();
        if (currentAccount != 0) {
            filesDir = new File(filesDir, "account" + currentAccount + "/");
            filesDir.mkdirs();
        }
        File backupCacheFile = new File(filesDir, DATABASE_BACKUP_NAME + ".db");
        try {
            AndroidUtilities.copyFile(cacheFile, backupCacheFile);
            FileLog.d("file db backup created " + backupCacheFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean restoreBackup() {
        File filesDir = ApplicationLoader.getFilesDirFixed();
        if (currentAccount != 0) {
            filesDir = new File(filesDir, "account" + currentAccount + "/");
            filesDir.mkdirs();
        }
        File backupCacheFile = new File(filesDir, DATABASE_BACKUP_NAME + ".db");
        if (!backupCacheFile.exists()) {
            return false;
        }
        try {
            return AndroidUtilities.copyFile(backupCacheFile, cacheFile);
        } catch (IOException e) {
            FileLog.e(e);
        }
        return false;
    }

    public String getPath(long documentId, int dc, int type, boolean useQueue) {
        if (useQueue) {
            if (BuildVars.DEBUG_VERSION) {
                if (dispatchQueue.getHandler() != null && Thread.currentThread() == dispatchQueue.getHandler().getLooper().getThread()) {
                    throw new RuntimeException("Error, lead to infinity loop");
                }
            }

            CountDownLatch syncLatch = new CountDownLatch(1);
            String[] res = new String[1];
            long time = System.currentTimeMillis();

            dispatchQueue.postRunnable(() -> {
                SQLiteCursor cursor = null;
                try {
                    cursor = database.queryFinalized("SELECT path FROM paths WHERE document_id = " + documentId + " AND dc_id = " + dc + " AND type = " + type);
                    if (cursor.next()) {
                        res[0] = cursor.stringValue(0);
                        if (BuildVars.DEBUG_VERSION) {
                            FileLog.d("get file path id=" + documentId + " dc=" + dc + " type=" + type + " path=" + res[0]);
                        }
                    }
                } catch (SQLiteException e) {
                    FileLog.e(e);
                } finally {
                    if (cursor != null) {
                        cursor.dispose();
                    }
                }
                syncLatch.countDown();
            });
            try {
                syncLatch.await();
            } catch (Exception ignore) {
            }
            return res[0];
        } else {
            SQLiteCursor cursor = null;
            String res = null;
            try {
                cursor = database.queryFinalized("SELECT path FROM paths WHERE document_id = " + documentId + " AND dc_id = " + dc + " AND type = " + type);
                if (cursor.next()) {
                    res = cursor.stringValue(0);
                    if (BuildVars.DEBUG_VERSION) {
                        FileLog.d("get file path id=" + documentId + " dc=" + dc + " type=" + type + " path=" + res);
                    }
                }
            } catch (SQLiteException e) {
                FileLog.e(e);
            } finally {
                if (cursor != null) {
                    cursor.dispose();
                }
            }
            return res;
        }
    }

    public void putPath(long id, int dc, int type, String path) {
        dispatchQueue.postRunnable(() -> {
            if (BuildVars.DEBUG_VERSION) {
                FileLog.d("put file path id=" + id + " dc=" + dc + " type=" + type + " path=" + path);
            }
            SQLitePreparedStatement state = null;
            SQLitePreparedStatement deleteState = null;
            try {
                if (path != null) {
                    deleteState = database.executeFast("DELETE FROM paths WHERE path = ?");
                    deleteState.bindString(1, path);
                    deleteState.step();

                    state = database.executeFast("REPLACE INTO paths VALUES(?, ?, ?, ?)");
                    state.requery();
                    state.bindLong(1, id);
                    state.bindInteger(2, dc);
                    state.bindInteger(3, type);
                    state.bindString(4, path);
                    state.step();
                    state.dispose();
                } else {
                    database.executeFast("DELETE FROM paths WHERE document_id = " + id + " AND dc_id = " + dc + " AND type = " + type).stepThis().dispose();
                }
            } catch (SQLiteException e) {
                FileLog.e(e);
            } finally {
                if (deleteState != null) {
                    deleteState.dispose();
                }
                if (state != null) {
                    state.dispose();
                }
            }
        });
    }

    public void checkMediaExistance(ArrayList<MessageObject> messageObjects) {
        if (messageObjects.isEmpty()) {
            return;
        }
        ArrayList<MessageObject> arrayListFinal = new ArrayList<>(messageObjects);

        CountDownLatch syncLatch = new CountDownLatch(1);
        long time = System.currentTimeMillis();
        dispatchQueue.postRunnable(() -> {
            try {
                for (int i = 0; i < arrayListFinal.size(); i++) {
                    MessageObject messageObject = arrayListFinal.get(i);
                    messageObject.checkMediaExistance(false);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            syncLatch.countDown();
        });

        try {
            syncLatch.await();
        } catch (InterruptedException e) {
            FileLog.e(e);
        }

        FileLog.d("checkMediaExistance size=" + messageObjects.size() + " time=" + (System.currentTimeMillis() - time));

        if (BuildVars.DEBUG_VERSION) {
            if (Thread.currentThread() == Looper.getMainLooper().getThread()) {
                FileLog.e(new Exception("warning, not allowed in main thread"));
            }
        }
    }

    public void clear() {
        dispatchQueue.postRunnable(() -> {
            try {
                database.executeFast("DELETE FROM paths WHERE 1").stepThis().dispose();
            } catch (SQLiteException e) {
                FileLog.e(e);
            }
        });
    }

    public boolean hasAnotherRefOnFile(String path) {
        CountDownLatch syncLatch = new CountDownLatch(1);
        boolean[] res = new boolean[]{false};
        dispatchQueue.postRunnable(() -> {
            try {
                SQLiteCursor cursor = database.queryFinalized("SELECT document_id FROM paths WHERE path = '" + path + "'");
                if (cursor.next()) {
                    res[0] = true;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            syncLatch.countDown();
        });

        try {
            syncLatch.await();
        } catch (InterruptedException e) {
            FileLog.e(e);
        }
        return res[0];
    }

    public static class PathData {
        public final long id;
        public final int dc;
        public final int type;

        public PathData(long documentId, int dcId, int type) {
            this.id = documentId;
            this.dc = dcId;
            this.type = type;
        }
    }
}
