package org.telegram.messenger.ringtone;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.Components.Bulletin;

import java.io.File;

public class RingtoneUploader implements NotificationCenter.NotificationCenterDelegate {

    private int currentAccount;
    public final String filePath;
    private boolean canceled;

    public RingtoneUploader(String filePath, int currentAccount) {
        this.currentAccount = currentAccount;
        this.filePath = filePath;
        subscribe();
        FileLoader.getInstance(currentAccount).uploadFile(filePath, false, true, ConnectionsManager.FileTypeAudio);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.fileUploaded) {
            final String location = (String) args[0];
            if (canceled) {
                return;
            }
            if (location.equals(filePath)) {
                final TLRPC.InputFile file = (TLRPC.InputFile) args[1];
                TL_account.uploadRingtone req = new TL_account.uploadRingtone();
                req.file = file;
                req.file_name = file.name;
                req.mime_type = FileLoader.getFileExtension(new File(file.name));
                if ("ogg".equals(req.mime_type)) {
                    req.mime_type = "audio/ogg";
                } else {
                    req.mime_type = "audio/mpeg";
                }
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (response != null) {
                            onComplete((TLRPC.Document) response);
                        } else {
                            error(error);
                        }
                        unsubscribe();
                    });
                });
            }
        }
    }

    private void subscribe() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileUploaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileUploadFailed);
    }

    private void unsubscribe() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploadFailed);
    }

    private void onComplete(TLRPC.Document document) {
        MediaDataController.getInstance(currentAccount).onRingtoneUploaded(filePath, document, false);
    }

    public void cancel() {
        canceled = true;
        unsubscribe();
        FileLoader.getInstance(currentAccount).cancelFileUpload(filePath, false);
        MediaDataController.getInstance(currentAccount).onRingtoneUploaded(filePath, null, true);
    }

    public void error(TLRPC.TL_error error) {
        unsubscribe();
        MediaDataController.getInstance(currentAccount).onRingtoneUploaded(filePath, null, true);
        if (error != null) {
            NotificationCenter.getInstance(currentAccount).doOnIdle(() -> {
                if (error.text.equals("RINGTONE_DURATION_TOO_LONG")) {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_ERROR_SUBTITLE, LocaleController.formatString("TooLongError", R.string.TooLongError), LocaleController.formatString("ErrorRingtoneDurationTooLong", R.string.ErrorRingtoneDurationTooLong, MessagesController.getInstance(currentAccount).ringtoneDurationMax));
                } else if (error.text.equals("RINGTONE_SIZE_TOO_BIG")) {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_ERROR_SUBTITLE, LocaleController.formatString("TooLargeError", R.string.TooLargeError), LocaleController.formatString("ErrorRingtoneSizeTooBig", R.string.ErrorRingtoneSizeTooBig, (MessagesController.getInstance(currentAccount).ringtoneSizeMax / 1024)));
                } else {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, Bulletin.TYPE_ERROR_SUBTITLE, LocaleController.formatString("InvalidFormatError", R.string.InvalidFormatError), LocaleController.getString(R.string.ErrorRingtoneInvalidFormat));
                }
            });
        }
    }
}
