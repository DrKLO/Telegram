package org.telegram.ui.QrShare;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.RLottieNative;

import java.io.File;

/**
 * The gift's pattern symbol: the first frame of its lottie as a white silhouette with mip levels (it is minified a
 * lot), rendered once in the background and coloured by the caller.
 */
final class QrGiftSymbol {

    private QrGiftSymbol() {
    }

    static final class Loader implements NotificationCenter.NotificationCenterDelegate {

        private final int currentAccount;
        private final TLRPC.Document pattern;
        private final int size;
        private final Utilities.Callback<Bitmap> onPattern;
        private boolean cancelled, started, done;

        /** @param onPattern receives the white silhouette; not called when it could not be rendered */
        Loader(int currentAccount, TLRPC.Document pattern, Object parentObject, int size, Utilities.Callback<Bitmap> onPattern) {
            this.currentAccount = currentAccount;
            this.pattern = pattern;
            this.size = size;
            this.onPattern = onPattern;
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileLoaded);
            if (!getFile().exists()) {
                FileLoader.getInstance(currentAccount).loadFile(pattern, parentObject, FileLoader.PRIORITY_HIGH, 1);
            }
            checkFile();
        }

        private File getFile() {
            return FileLoader.getInstance(currentAccount).getPathToAttach(pattern, true);
        }

        /** The symbol has been delivered (or failed). */
        boolean isDone() {
            return done;
        }

        void cancel() {
            cancelled = true;
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileLoaded);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.fileLoaded) {
                checkFile();
            }
        }

        private void checkFile() {
            if (cancelled || started || !getFile().exists()) {
                return;
            }
            started = true;
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileLoaded);
            final File file = getFile();
            Utilities.globalQueue.postRunnable(() -> {
                final Bitmap bitmap = render(file, size);
                AndroidUtilities.runOnUIThread(() -> {
                    done = true;
                    if (cancelled) {
                        if (bitmap != null) {
                            bitmap.recycle();
                        }
                    } else if (bitmap != null) {
                        onPattern.run(bitmap);
                    }
                });
            });
        }
    }

    @Nullable
    private static Bitmap render(File file, int size) {
        RLottieNative lottie = null;
        try {
            lottie = RLottieNative.createFromFile(file.getAbsolutePath());
            if (lottie == null) {
                return null;
            }
            final Bitmap frame = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            lottie.getFrame(0, frame, true);
            final Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
            paint.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            new Canvas(bitmap).drawBitmap(frame, 0, 0, paint);
            frame.recycle();
            bitmap.setHasMipMap(true);
            return bitmap;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        } finally {
            if (lottie != null) {
                lottie.recycle();
            }
        }
    }
}
