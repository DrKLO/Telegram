#include "sqlite.h"

jfieldID queryArgsCountField;

jint sqliteOnJNILoad(JavaVM *vm, void *reserved, JNIEnv *env) {
	jclass class = (*env)->FindClass(env, "org/telegram/SQLite/SQLitePreparedStatement");
	queryArgsCountField = (*env)->GetFieldID(env, class, "queryArgsCount", "I");
	return JNI_VERSION_1_6;
}

int Java_org_telegram_SQLite_SQLitePreparedStatement_step(JNIEnv *env, jobject object, int statementHandle) {
	sqlite3_stmt *handle = (sqlite3_stmt *) statementHandle;
    
    int errcode = sqlite3_step(handle);
    if (errcode == SQLITE_ROW)  {
        return 0;
    } else if(errcode == SQLITE_DONE) {
        return 1;
    }  else if(errcode == SQLITE_BUSY) {
        return -1;
    }
	throw_sqlite3_exception(env, sqlite3_db_handle(handle), errcode);
}

int Java_org_telegram_SQLite_SQLitePreparedStatement_prepare(JNIEnv *env, jobject object, int sqliteHandle, jstring sql) {
	sqlite3 *handle = (sqlite3 *) sqliteHandle;

    char const *sqlStr = (*env)->GetStringUTFChars(env, sql, 0);

    sqlite3_stmt *stmt_handle;

    int errcode = sqlite3_prepare_v2(handle, sqlStr, -1, &stmt_handle, 0);
    if (SQLITE_OK != errcode) {
    	throw_sqlite3_exception(env, handle, errcode);
    } else {
    	int argsCount = sqlite3_bind_parameter_count(stmt_handle);
    	(*env)->SetIntField(env, object, queryArgsCountField, argsCount);
    }

    if (sqlStr != 0) {
        (*env)->ReleaseStringUTFChars(env, sql, sqlStr);
    }

    return (int) stmt_handle;
}

void Java_org_telegram_SQLite_SQLitePreparedStatement_reset(JNIEnv *env, jobject object, int statementHandle) {
	sqlite3_stmt *handle = (sqlite3_stmt *) statementHandle;

	int errcode = sqlite3_reset(handle);
    if (SQLITE_OK != errcode) {
    	throw_sqlite3_exception(env, sqlite3_db_handle(handle), errcode);
    }
}

void Java_org_telegram_SQLite_SQLitePreparedStatement_finalize(JNIEnv *env, jobject object, int statementHandle) {
    sqlite3_finalize((sqlite3_stmt *) statementHandle);
}

void Java_org_telegram_SQLite_SQLitePreparedStatement_bindByteBuffer(JNIEnv *env, jobject object, int statementHandle, int index, jobject value, int length) {
	sqlite3_stmt *handle = (sqlite3_stmt *) statementHandle;
    jbyte *buf = (*env)->GetDirectBufferAddress(env, value);
    
	int errcode = sqlite3_bind_blob(handle, index, buf, length, SQLITE_STATIC);
    if (SQLITE_OK != errcode) {
    	throw_sqlite3_exception(env, sqlite3_db_handle(handle), errcode);
    }
}

void Java_org_telegram_SQLite_SQLitePreparedStatement_bindString(JNIEnv *env, jobject object, int statementHandle, int index, jstring value) {
	sqlite3_stmt *handle = (sqlite3_stmt *) statementHandle;

	char const *valueStr = (*env)->GetStringUTFChars(env, value, 0);

	int errcode = sqlite3_bind_text(handle, index, valueStr, -1, SQLITE_TRANSIENT);
    if (SQLITE_OK != errcode) {
    	throw_sqlite3_exception(env, sqlite3_db_handle(handle), errcode);
    }

	if (valueStr != 0) {
        (*env)->ReleaseStringUTFChars(env, value, valueStr);
    }
}

void Java_org_telegram_SQLite_SQLitePreparedStatement_bindInt(JNIEnv *env, jobject object, int statementHandle, int index, int value) {
	sqlite3_stmt *handle = (sqlite3_stmt *) statementHandle;

	int errcode = sqlite3_bind_int(handle, index, value);
    if (SQLITE_OK != errcode) {
    	throw_sqlite3_exception(env, sqlite3_db_handle(handle), errcode);
    }
}

void Java_org_telegram_SQLite_SQLitePreparedStatement_bindLong(JNIEnv *env, jobject object, int statementHandle, int index, long long value) {
	sqlite3_stmt *handle = (sqlite3_stmt *) statementHandle;
    
	int errcode = sqlite3_bind_int64(handle, index, value);
    if (SQLITE_OK != errcode) {
    	throw_sqlite3_exception(env, sqlite3_db_handle(handle), errcode);
    }
}

void Java_org_telegram_SQLite_SQLitePreparedStatement_bindDouble(JNIEnv *env, jobject object, int statementHandle, int index, double value) {
	sqlite3_stmt *handle = (sqlite3_stmt *) statementHandle;

	int errcode = sqlite3_bind_double(handle, index, value);
    if (SQLITE_OK != errcode) {
    	throw_sqlite3_exception(env, sqlite3_db_handle(handle), errcode);
    }
}

void Java_org_telegram_SQLite_SQLitePreparedStatement_bindNull(JNIEnv *env, jobject object, int statementHandle, int index) {
	sqlite3_stmt *handle = (sqlite3_stmt *) statementHandle;

	int errcode = sqlite3_bind_null(handle, index);
    if (SQLITE_OK != errcode) {
    	throw_sqlite3_exception(env, sqlite3_db_handle(handle), errcode);
    }
}

