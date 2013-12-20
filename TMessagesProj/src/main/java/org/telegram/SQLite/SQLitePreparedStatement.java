/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.SQLite;

import org.telegram.messenger.FileLog;

public class SQLitePreparedStatement {
	private boolean isFinalized = false;
	private int sqliteStatementHandle;

	private int queryArgsCount;
	private boolean finalizeAfterQuery = false;

	public int getStatementHandle() {
		return sqliteStatementHandle;
	}

	public SQLitePreparedStatement(SQLiteDatabase db, String sql, boolean finalize) throws SQLiteException {
		finalizeAfterQuery = finalize;
		sqliteStatementHandle = prepare(db.getSQLiteHandle(), sql);
	}

	public SQLiteCursor query(Object[] args) throws SQLiteException {
		if (args == null || args.length != queryArgsCount) {
			throw new IllegalArgumentException();
		}

		checkFinalized();

		reset(sqliteStatementHandle);

		int i = 1;
		for (Object obj : args) {
			if (obj == null) {
				bindNull(sqliteStatementHandle, i);
			} else if (obj instanceof Integer) {
				bindInt(sqliteStatementHandle, i, (Integer)obj);
			} else if (obj instanceof Double) {
				bindDouble(sqliteStatementHandle, i, (Double)obj);
			} else if (obj instanceof String) {
				bindString(sqliteStatementHandle, i, (String)obj);
			} else if (obj instanceof byte[]) {
				bindByteArray(sqliteStatementHandle, i, (byte[])obj);
			} else {
				throw new IllegalArgumentException();
			}
			i++;
		}

		return new SQLiteCursor(this);
	}

    public int step() throws SQLiteException {
        return step(sqliteStatementHandle);
    }

    public SQLitePreparedStatement stepThis() throws SQLiteException {
        step(sqliteStatementHandle);
        return this;
    }

	public void requery() throws SQLiteException {
		checkFinalized();
		reset(sqliteStatementHandle);
	}

	public void dispose() {
		if (finalizeAfterQuery) {
			finalizeQuery();
		}
	}

	void checkFinalized() throws SQLiteException {
		if (isFinalized) {
			throw new SQLiteException("Prepared query finalized");
		}
	}

	public void finalizeQuery() {
        if (isFinalized) {
            return;
        }
		try {
			isFinalized = true;
			finalize(sqliteStatementHandle);
		} catch (SQLiteException e) {
            FileLog.e("tmessages", e.getMessage(), e);
		}
	}

    public void bindInteger(int index, int value) throws SQLiteException {
        bindInt(sqliteStatementHandle, index, value);
    }

    public void bindDouble(int index, double value) throws SQLiteException {
        bindDouble(sqliteStatementHandle, index, value);
    }

    public void bindByteArray(int index, byte[] value) throws SQLiteException {
        bindByteArray(sqliteStatementHandle, index, value);
    }

    public void bindString(int index, String value) throws SQLiteException {
        bindString(sqliteStatementHandle, index, value);
    }

    public void bindLong(int index, long value) throws SQLiteException {
        bindLong(sqliteStatementHandle, index, value);
    }

	native void bindByteArray(int statementHandle, int index, byte[] value) throws SQLiteException;
	native void bindString(int statementHandle, int index, String value) throws SQLiteException;
	native void bindInt(int statementHandle, int index, int value) throws SQLiteException;
    native void bindLong(int statementHandle, int index, long value) throws SQLiteException;
	native void bindDouble(int statementHandle, int index, double value) throws SQLiteException;
	native void bindNull(int statementHandle, int index) throws SQLiteException;
	native void reset(int statementHandle) throws SQLiteException;
	native int prepare(int sqliteHandle, String sql) throws SQLiteException;
	native void finalize(int statementHandle) throws SQLiteException;
    native int step(int statementHandle) throws SQLiteException;
}
