package org.telegram.ui.Components;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.FileLoader;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.utils.camera.roundvideo.RoundVideoSession;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Bridges growing round-video output generations to Telegram's file uploader. */
final class TelegramRoundVideoUpload implements
        RoundVideoSession.OutputListener,
        NotificationCenter.NotificationCenterDelegate {

    static final class UploadInfo {
        final File file;
        final long size;
        final TLRPC.InputFile inputFile;
        final TLRPC.InputEncryptedFile encryptedFile;
        final byte[] key;
        final byte[] iv;

        UploadInfo(
                File file,
                long size,
                TLRPC.InputFile inputFile,
                TLRPC.InputEncryptedFile encryptedFile,
                byte[] key,
                byte[] iv
        ) {
            this.file = file;
            this.size = size;
            this.inputFile = inputFile;
            this.encryptedFile = encryptedFile;
            this.key = key;
            this.iv = iv;
        }
    }

    private static final class Generation {
        final long id;
        final File file;
        long availableSize;
        long finalSize;
        boolean uploadStarted;
        boolean invalidated;
        TLRPC.InputFile inputFile;
        TLRPC.InputEncryptedFile encryptedFile;
        byte[] key;
        byte[] iv;

        Generation(long id, File file) {
            this.id = id;
            this.file = file;
        }
    }

    private final int currentAccount;
    private final boolean encrypted;
    private final Map<Long, Generation> generations = new HashMap<>();
    private boolean released;

    TelegramRoundVideoUpload(int currentAccount, boolean encrypted) {
        this.currentAccount = currentAccount;
        this.encrypted = encrypted;
        NotificationCenter.getInstance(currentAccount).addObserver(
                this,
                NotificationCenter.fileUploaded
        );
    }

    @Override
    public synchronized void onOutputStarted(long outputId, @NonNull File file) {
        if (released) return;
        generations.put(outputId, new Generation(outputId, file));
    }

    @Override
    public synchronized void onBytesAvailable(
            long outputId,
            @NonNull File file,
            long offset,
            long length
    ) {
        Generation generation = generations.get(outputId);
        if (released || generation == null || generation.invalidated) return;
        startUpload(generation);
        generation.availableSize = Math.max(generation.availableSize, offset + length);
        FileLoader.getInstance(currentAccount).checkUploadNewDataAvailable(
                file.getAbsolutePath(),
                encrypted,
                generation.availableSize,
                0
        );
    }

    @Override
    public synchronized void onOutputInvalidated(
            long outputId,
            @NonNull RoundVideoSession.OutputInvalidationReason reason
    ) {
        Generation generation = generations.remove(outputId);
        if (generation == null) return;
        generation.invalidated = true;
        if (generation.uploadStarted) {
            FileLoader.getInstance(currentAccount).cancelFileUpload(
                    generation.file.getAbsolutePath(),
                    encrypted
            );
        }
    }

    @Override
    public synchronized void onOutputCompleted(
            long outputId,
            @NonNull File file,
            long finalSize,
            long durationMs,
            boolean hasAudio
    ) {
        Generation generation = generations.get(outputId);
        if (released || generation == null || generation.invalidated) return;
        startUpload(generation);
        generation.availableSize = Math.max(generation.availableSize, finalSize);
        generation.finalSize = finalSize;
        FileLoader.getInstance(currentAccount).checkUploadNewDataAvailable(
                file.getAbsolutePath(),
                encrypted,
                generation.availableSize,
                finalSize
        );
    }

    @Override
    public synchronized void onOutputError(long outputId, @NonNull Exception error) {
        onOutputInvalidated(outputId, RoundVideoSession.OutputInvalidationReason.ERROR);
    }

    @Override
    public synchronized void didReceivedNotification(int id, int account, Object... args) {
        if (released || id != NotificationCenter.fileUploaded || args.length < 6) return;
        String location = (String) args[0];
        for (Generation generation : generations.values()) {
            if (!generation.invalidated
                    && generation.file.getAbsolutePath().equals(location)) {
                generation.inputFile = (TLRPC.InputFile) args[1];
                generation.encryptedFile = (TLRPC.InputEncryptedFile) args[2];
                generation.key = (byte[]) args[3];
                generation.iv = (byte[]) args[4];
                generation.finalSize = Math.max(generation.finalSize, (Long) args[5]);
                break;
            }
        }
    }

    synchronized @Nullable UploadInfo getUploadInfo(long outputId, @NonNull File file) {
        Generation generation = generations.get(outputId);
        if (generation == null || generation.invalidated) {
            return new UploadInfo(file, file.length(), null, null, null, null);
        }
        long size = Math.max(generation.finalSize, file.length());
        return new UploadInfo(
                file,
                size,
                generation.inputFile,
                generation.encryptedFile,
                generation.key,
                generation.iv
        );
    }

    synchronized void release(boolean cancelUploads) {
        if (released) return;
        released = true;
        NotificationCenter.getInstance(currentAccount).removeObserver(
                this,
                NotificationCenter.fileUploaded
        );
        if (cancelUploads) {
            Iterator<Generation> iterator = generations.values().iterator();
            while (iterator.hasNext()) {
                Generation generation = iterator.next();
                if (generation.uploadStarted && !generation.invalidated) {
                    FileLoader.getInstance(currentAccount).cancelFileUpload(
                            generation.file.getAbsolutePath(),
                            encrypted
                    );
                }
                iterator.remove();
            }
        }
    }

    private void startUpload(@NonNull Generation generation) {
        if (generation.uploadStarted) return;
        generation.uploadStarted = true;
        FileLoader.getInstance(currentAccount).uploadFile(
                generation.file.getAbsolutePath(),
                encrypted,
                false,
                1,
                ConnectionsManager.FileTypeVideo,
                false
        );
    }
}
