/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.SQLite;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ApplicationLoader;

public class SQLiteDatabase {

	private final long sqliteHandle;

	private boolean isOpen;
    private boolean inTransaction;

	public long getSQLiteHandle() {
		return sqliteHandle;
	}

	public SQLiteDatabase(String fileName) throws SQLiteException {
		sqliteHandle = opendb(fileName, ApplicationLoader.getFilesDirFixed().getPath());
		isOpen = true;
	}

	public boolean tableExists(String tableName) throws SQLiteException {
		checkOpened();
		String s = "SELECT rowid FROM sqlite_master WHERE type='table' AND name=?;";
		return executeInt(s, tableName) != null;
	}

    public SQLitePreparedStatement executeFast(String sql) throws SQLiteException {
        return new SQLitePreparedStatement(this, sql);
    }

	public Integer executeInt(String sql, Object... args) throws SQLiteException {
		checkOpened();
		SQLiteCursor cursor = queryFinalized(sql, args);
		try {
			if (!cursor.next()) {
				return null;
			}
			return cursor.intValue(0);
		} finally {
			cursor.dispose();
		}
	}

	public void explainQuery(String sql, Object... args) throws SQLiteException {
		checkOpened();
		SQLiteCursor cursor = new SQLitePreparedStatement(this, "EXPLAIN QUERY PLAN " + sql).query(args);
		while (cursor.next()) {
			int count = cursor.getColumnCount();
			StringBuilder builder = new StringBuilder();
			for (int a = 0; a < count; a++) {
			    builder.append(cursor.stringValue(a)).append(", ");
            }
            FileLog.d("EXPLAIN QUERY PLAN " + builder.toString());
		}
		cursor.dispose();
	}

	public SQLiteCursor queryFinalized(String sql, Object... args) throws SQLiteException {
		checkOpened();
		return new SQLitePreparedStatement(this, sql).query(args);
	}

	public void close() {
		if (isOpen) {
			try {
                commitTransaction();
				closedb(sqliteHandle);
			} catch (SQLiteException e) {
				if (BuildVars.LOGS_ENABLED) {
					FileLog.e(e.getMessage(), e);
				}
			}
			isOpen = false;
		}
	}

	void checkOpened() throws SQLiteException {
		if (!isOpen) {
			throw new SQLiteException("Database closed");
		}
	}

	public void finalize() throws Throwable {
        super.finalize();
		close();
	}

    public void beginTransaction() throws SQLiteException {
        if (inTransaction) {
            throw new SQLiteException("database already in transaction");
        }
        inTransaction = true;
        beginTransaction(sqliteHandle);
    }

    public void commitTransaction() {
        if (!inTransaction) {
            return;
        }
        inTransaction = false;
        commitTransaction(sqliteHandle);
    }

	native long opendb(String fileName, String tempDir) throws SQLiteException;
	native void closedb(long sqliteHandle) throws SQLiteException;
    native void beginTransaction(long sqliteHandle);
    native void commitTransaction(long sqliteHandle);
}
