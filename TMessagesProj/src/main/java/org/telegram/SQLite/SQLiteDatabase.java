/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.SQLite;

import org.telegram.messenger.FileLog;
import org.telegram.ui.ApplicationLoader;

import java.util.HashMap;
import java.util.Map;

public class SQLiteDatabase {
	private final int sqliteHandle;

	private final Map<String, SQLitePreparedStatement> preparedMap;
	private boolean isOpen = false;
    private boolean inTransaction = false;

	public int getSQLiteHandle() {
		return sqliteHandle;
	}

	public SQLiteDatabase(String fileName) throws SQLiteException {
		sqliteHandle = opendb(fileName, ApplicationLoader.applicationContext.getFilesDir().getPath());
		isOpen = true;
		preparedMap = new HashMap<String, SQLitePreparedStatement>();
	}

	public boolean tableExists(String tableName) throws SQLiteException {
		checkOpened();
		String s = "SELECT rowid FROM sqlite_master WHERE type='table' AND name=?;";
		return executeInt(s, tableName) != null;
	}

	public void execute(String sql, Object... args) throws SQLiteException {
		checkOpened();
		SQLiteCursor cursor = query(sql, args);
		try {
			cursor.next();
		} finally {
			cursor.dispose();
		}
	}

    public SQLitePreparedStatement executeFast(String sql) throws SQLiteException{
        return new SQLitePreparedStatement(this, sql, true);
    }

	public Integer executeInt(String sql, Object... args) throws SQLiteException {
		checkOpened();
		SQLiteCursor cursor = query(sql, args);
		try {
			if (!cursor.next()) {
				return null;
			}
			return cursor.intValue(0);
		} finally {
			cursor.dispose();
		}
	}

	public int executeIntOrThrow(String sql, Object... args) throws SQLiteException, SQLiteNoRowException {
		checkOpened();
		Integer val = executeInt(sql, args);
		if (val != null) {
			return val;
		}

		throw new SQLiteNoRowException();
	}

	public String executeString(String sql, Object... args) throws SQLiteException {
		checkOpened();
		SQLiteCursor cursor = query(sql, args);
		try {
			if (!cursor.next()) {
				return null;
			}
			return cursor.stringValue(0);
		} finally {
			cursor.dispose();
		}
	}

	public SQLiteCursor query(String sql, Object... args) throws SQLiteException {
		checkOpened();
		SQLitePreparedStatement stmt = preparedMap.get(sql);

		if (stmt == null) {
			stmt = new SQLitePreparedStatement(this, sql, false);
			preparedMap.put(sql, stmt);
		}

		return stmt.query(args);
	}

	public SQLiteCursor queryFinalized(String sql, Object... args) throws SQLiteException {
		checkOpened();
		return new SQLitePreparedStatement(this, sql, true).query(args);
	}

	public void close() {
		if (isOpen) {
			try {
				for (SQLitePreparedStatement stmt : preparedMap.values()) {
					stmt.finalizeQuery();
				}
				closedb(sqliteHandle);
			} catch (SQLiteException e) {
                FileLog.e("tmessages", e.getMessage(), e);
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

    private StackTraceElement[] temp;
    public void beginTransaction() throws SQLiteException {
        if (inTransaction) {
            throw new SQLiteException("database already in transaction");
        }
        inTransaction = true;
        beginTransaction(sqliteHandle);
    }

    public void commitTransaction() {
        inTransaction = false;
        commitTransaction(sqliteHandle);
    }

	native int opendb(String fileName, String tempDir) throws SQLiteException;
	native void closedb(int sqliteHandle) throws SQLiteException;
    native void beginTransaction(int sqliteHandle);
    native void commitTransaction(int sqliteHandle);
}
