package org.telegram.ui.Components;

import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;

/**
 * Says where a download stands, for what draws it as a button, a ring or a bar.
 *
 * A row of a list keeps it as its state: downloaded, not downloaded, stopped, or how far it has
 * got. The state is given with the row, and a change of it is said by itself while the row is
 * being read, a download every tenth of the way. Elsewhere, how far a download has got is said
 * as it goes while what holds it is being read.
 */
public class AccessibilityTransferAnnouncer {

    public static final int STATE_NONE = 0;
    public static final int STATE_NOT_DOWNLOADED = 1;
    public static final int STATE_DOWNLOADING = 2;
    public static final int STATE_PAUSED = 3;
    public static final int STATE_DOWNLOADED = 4;

    private static final int STEP_PERCENT = 10;
    private static final long MIN_INTERVAL = 1500;
    // a row can sit inside a row of its own that takes the focus in its place
    private static final int FOCUS_DEPTH = 3;

    private View view;
    private final Utilities.Callback0Return<Boolean> isReadCheck;
    private boolean announcedOnce;
    private int lastStep = -1;
    private long lastTime;

    private Object stateKey;
    private int state = STATE_NONE;
    private int stateStep = -1;
    private long stateTime;
    private CharSequence stateText;

    public AccessibilityTransferAnnouncer(View view) {
        this(view, null);
    }

    // for a download drawn inside a view that a screen reader reaches in parts of its own
    public AccessibilityTransferAnnouncer(View view, Utilities.Callback0Return<Boolean> isRead) {
        this.view = view;
        this.isReadCheck = isRead;
    }

    // the view that is read for a row, where it is not the one that draws it
    public void setView(View view) {
        if (this.view == view) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && this.view != null && stateText != null) {
            this.view.setStateDescription(null);
        }
        this.view = view;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && stateText != null) {
            view.setStateDescription(stateText);
        }
    }

    public void reset() {
        announcedOnce = false;
        lastStep = -1;
    }

    private boolean isRead() {
        if (!AndroidUtilities.isAccessibilityScreenReaderEnabled()) {
            return false;
        }
        if (isReadCheck != null) {
            return Boolean.TRUE.equals(isReadCheck.run());
        }
        View v = view;
        for (int i = 0; i < FOCUS_DEPTH && v != null; i++) {
            if (v.isAccessibilityFocused()) {
                return true;
            }
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        return false;
    }

    // as in a chat: a file, a gif or a video is said with how much of it has come, while music, a
    // voice and a photo are said by the percentage alone
    public void onProgress(long loadedSize, long totalSize, boolean withSize) {
        if (totalSize <= 0) {
            return;
        }
        if (!isRead()) {
            reset();
            return;
        }
        final int percent = (int) Math.min(100, Math.round(loadedSize * 100f / totalSize));
        final int step = percent / STEP_PERCENT;
        final long now = SystemClock.elapsedRealtime();
        if (announcedOnce && (step == lastStep || now - lastTime < MIN_INTERVAL)) {
            return;
        }
        lastStep = step;
        lastTime = now;
        final CharSequence text;
        if (announcedOnce) {
            text = withSize ? percent + "%, " + AndroidUtilities.formatFileSize(loadedSize) : percent + "%";
        } else {
            text = describeDownloading(percent, loadedSize, totalSize, withSize);
        }
        announcedOnce = true;
        view.announceForAccessibility(text);
    }

    // a download that was being followed says that it is done
    public void onSuccess() {
        if (announcedOnce && isRead()) {
            view.announceForAccessibility(LocaleController.getString(R.string.AccDescrMediaDownloaded));
        }
        reset();
    }

    /**
     * The state of a row, given every time it is drawn. A row given another file takes its state
     * without a word; the row being read says a change of its own file's state, and while it
     * downloads, every tenth of the way, no more than once a second and a half.
     */
    public void setState(Object key, int newState, float progress, long totalSize, boolean withSize) {
        final int percent = (int) Math.max(0, Math.min(100, Math.round(progress * 100)));
        final int step = percent / STEP_PERCENT;
        final long now = SystemClock.elapsedRealtime();
        final boolean sameFile = key != null && key.equals(stateKey);
        if (sameFile && newState == state && (newState != STATE_DOWNLOADING || step == stateStep || now - stateTime < MIN_INTERVAL)) {
            return;
        }
        stateKey = key;
        state = newState;
        stateStep = step;
        stateTime = now;
        stateText = describeState(newState, percent, (long) (totalSize * progress), totalSize, withSize);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // a change of the state of the row being read is said by the screen reader itself
            view.setStateDescription(stateText);
        } else if (sameFile && stateText != null && isRead()) {
            view.announceForAccessibility(stateText);
        }
    }

    // where a row cannot be given a state of its own, it is said after what the row says
    public void appendState(AccessibilityNodeInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R || TextUtils.isEmpty(stateText)) {
            return;
        }
        if (!TextUtils.isEmpty(info.getContentDescription())) {
            info.setContentDescription(TextUtils.concat(info.getContentDescription(), ", ", stateText));
        } else {
            final CharSequence text = info.getText();
            info.setText(TextUtils.isEmpty(text) ? stateText : TextUtils.concat(text, ", ", stateText));
        }
    }

    private static CharSequence describeState(int state, int percent, long loadedSize, long totalSize, boolean withSize) {
        switch (state) {
            case STATE_NOT_DOWNLOADED:
                return LocaleController.getString(R.string.AccDescrMediaNotDownloaded);
            case STATE_DOWNLOADING:
                return describeDownloading(percent, loadedSize, totalSize, withSize);
            case STATE_PAUSED:
                return LocaleController.getString(R.string.AccDescrDownloadPaused);
            case STATE_DOWNLOADED:
                return LocaleController.getString(R.string.AccDescrMediaDownloaded);
        }
        return null;
    }

    private static CharSequence describeDownloading(int percent, long loadedSize, long totalSize, boolean withSize) {
        if (withSize && totalSize > 0) {
            return LocaleController.getString(R.string.Downloading) + " " + percent + "%, "
                + LocaleController.formatString(R.string.OfS, AndroidUtilities.formatFileSize(loadedSize), AndroidUtilities.formatFileSize(totalSize));
        }
        return LocaleController.getString(R.string.Downloading) + " " + percent + "%";
    }
}
