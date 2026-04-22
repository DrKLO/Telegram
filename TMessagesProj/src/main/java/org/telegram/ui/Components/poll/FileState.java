package org.telegram.ui.Components.poll;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.Text;

import java.io.File;

public class FileState {

    public final int currentAccount;
    public final @NonNull TLRPC.Document document;
    public final @Nullable String attachPath;
    public final @NonNull MessageObject messageObject;

    private final String attachFileName;
    private boolean isExists;
    private boolean isLoading;

    public FileState(int currentAccount, @NonNull MessageObject messageObject, @NonNull TLRPC.Document document, @Nullable String attachPath) {
        this.currentAccount = currentAccount;
        this.messageObject = messageObject;
        this.document = document;
        this.attachPath = attachPath;

        this.attachFileName = !TextUtils.isEmpty(attachPath) ? attachPath : FileLoader.getAttachFileName(document);
        checkState();
    }

    public void checkState() {
        boolean exists = false;
        if (attachPath != null) {
            exists = new File(attachPath).exists();
        }
        if (!exists) {
            exists = FileLoader.getInstance(currentAccount).getPathToAttach(document).exists();
        }
        this.isExists = exists;
        this.isLoading = !TextUtils.isEmpty(attachFileName) && (FileLoader.getInstance(currentAccount).isLoadingFile(attachFileName));
    }

    public void downloadStart() {
        FileLoader.getInstance(currentAccount).loadFile(document, messageObject, FileLoader.PRIORITY_NORMAL_UP, 0);
        checkState();
    }

    public void downloadCancel() {
        FileLoader.getInstance(currentAccount).cancelLoadFile(document);
        checkState();
    }

    public boolean isLoading() {
        return isLoading;
    }

    public boolean isExists() {
        return isExists;
    }
}
