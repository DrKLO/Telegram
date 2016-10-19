/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.SQLite;

import org.telegram.messenger.FileLog;
import org.telegram.tgnet.NativeByteBuffer;

import java.nio.ByteBuffer;

public class SQLitePreparedStatement {

	private boolean isFinalized = false;
	private int sqliteStatementHandle;
	private boolean finalizeAfterQuery = false;

    //private static HashMap<SQLitePreparedStatement, String> hashMap;

	public int getStatementHandle() {
		return sqliteStatementHandle;
	}

	public SQLitePreparedStatement(SQLiteDatabase db, String sql, boolean finalize) throws SQLiteException {
		finalizeAfterQuery = finalize;
		sqliteStatementHandle = prepare(db.getSQLiteHandle(), sql);
        /*if (BuildVars.DEBUG_VERSION) {
            if (hashMap == null) {
                hashMap = new HashMap<>();
            }
            hashMap.put(this, sql);
            for (HashMap.Entry<SQLitePreparedStatement, String> entry : hashMap.entrySet()) {
                FileLog.d("tmessages", "exist entry = " + entry.getValue());
            }
        }*/
	}


    public SQLiteCursor query(Object[] args) throws SQLiteException {
        if (args == null) {
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
            /*if (BuildVars.DEBUG_VERSION) {
                hashMap.remove(this);
            }*/
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

    public void bindByteBuffer(int index, ByteBuffer value) throws SQLiteException {
        bindByteBuffer(sqliteStatementHandle, index, value, value.limit());
    }

    public void bindByteBuffer(int index, NativeByteBuffer value) throws SQLiteException {
        bindByteBuffer(sqliteStatementHandle, index, value.buffer, value.limit());
    }

    public void bindString(int index, String value) throws SQLiteException {
        bindString(sqliteStatementHandle, index, value);
    }

    public void bindLong(int index, long value) throws SQLiteException {
        bindLong(sqliteStatementHandle, index, value);
    }

    public void bindNull(int index) throws SQLiteException {
        bindNull(sqliteStatementHandle, index);
    }

	native void bindByteBuffer(int statementHandle, int index, ByteBuffer value, int length) throws SQLiteException;
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
