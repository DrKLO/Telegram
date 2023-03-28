package org.telegram.messenger;

import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.BackupManager;
import android.app.backup.FullBackupDataOutput;
import android.app.backup.RestoreObserver;
import android.app.backup.SharedPreferencesBackupHelper;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import org.telegram.tgnet.TLRPC;

import java.io.IOException;
import java.util.ArrayList;

public class BackupAgent extends BackupAgentHelper {

    private static BackupManager backupManager;

    @Override
    public void onCreate() {
        SharedPreferencesBackupHelper helper = new SharedPreferencesBackupHelper(this, "saved_tokens", "saved_tokens_login");
        addHelper("prefs", helper);
    }

    public static void requestBackup(Context context) {
        if (backupManager == null) {
            backupManager = new BackupManager(context);
        }
        backupManager.dataChanged();
    }
}
