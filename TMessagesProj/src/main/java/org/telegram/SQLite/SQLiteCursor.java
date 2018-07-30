/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.SQLite;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.NativeByteBuffer;

public class SQLiteCursor {

	public static final int FIELD_TYPE_INT = 1;
	public static final int FIELD_TYPE_FLOAT = 2;
	public static final int FIELD_TYPE_STRING = 3;
	public static final int FIELD_TYPE_BYTEARRAY = 4;
	public static final int FIELD_TYPE_NULL = 5;

	private SQLitePreparedStatement preparedStatement;
	private boolean inRow = false;

	public SQLiteCursor(SQLitePreparedStatement stmt) {
		preparedStatement = stmt;
	}

	public boolean isNull(int columnIndex) throws SQLiteException {
		checkRow();
		return columnIsNull(preparedStatement.getStatementHandle(), columnIndex) == 1;
	}

	public int intValue(int columnIndex) throws SQLiteException {
		checkRow();
		return columnIntValue(preparedStatement.getStatementHandle(), columnIndex);
	}

	public double doubleValue(int columnIndex) throws SQLiteException {
		checkRow();
		return columnDoubleValue(preparedStatement.getStatementHandle(), columnIndex);
	}

	public long longValue(int columnIndex) throws SQLiteException {
		checkRow();
		return columnLongValue(preparedStatement.getStatementHandle(), columnIndex);
	}

	public String stringValue(int columnIndex) throws SQLiteException {
		checkRow();
		return columnStringValue(preparedStatement.getStatementHandle(), columnIndex);
	}

	public byte[] byteArrayValue(int columnIndex) throws SQLiteException {
		checkRow();
		return columnByteArrayValue(preparedStatement.getStatementHandle(), columnIndex);
	}

	public NativeByteBuffer byteBufferValue(int columnIndex) throws SQLiteException {
		checkRow();
		long ptr = columnByteBufferValue(preparedStatement.getStatementHandle(), columnIndex);
		if (ptr != 0) {
			return NativeByteBuffer.wrap(ptr);
		}
		return null;
	}

	public int getTypeOf(int columnIndex) throws SQLiteException {
		checkRow();
		return columnType(preparedStatement.getStatementHandle(), columnIndex);
	}

	public boolean next() throws SQLiteException {
		int res = preparedStatement.step(preparedStatement.getStatementHandle());
		if (res == -1) {
			int repeatCount = 6;
			while (repeatCount-- != 0) {
				try {
					if (BuildVars.LOGS_ENABLED) {
						FileLog.d("sqlite busy, waiting...");
					}
					Thread.sleep(500);
					res = preparedStatement.step();
					if (res == 0) {
						break;
					}
				} catch (Exception e) {
					FileLog.e(e);
				}
			}
			if (res == -1) {
				throw new SQLiteException("sqlite busy");
			}
		}
		inRow = (res == 0);
		return inRow;
	}

	public long getStatementHandle() {
		return preparedStatement.getStatementHandle();
	}

	public void dispose() {
		preparedStatement.dispose();
	}

	void checkRow() throws SQLiteException {
		if (!inRow) {
			throw new SQLiteException("You must call next before");
		}
	}

	native int columnType(long statementHandle, int columnIndex);
	native int columnIsNull(long statementHandle, int columnIndex);
	native int columnIntValue(long statementHandle, int columnIndex);
	native long columnLongValue(long statementHandle, int columnIndex);
	native double columnDoubleValue(long statementHandle, int columnIndex);
	native String columnStringValue(long statementHandle, int columnIndex);
	native byte[] columnByteArrayValue(long statementHandle, int columnIndex);
	native long columnByteBufferValue(long statementHandle, int columnIndex);
}
