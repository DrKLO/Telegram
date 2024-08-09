package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEncodingService;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RLottieDrawable;

import java.io.File;

public class DownloadButton extends ImageView {

    private int currentAccount;
    private FrameLayout container;
    private Theme.ResourcesProvider resourcesProvider;

    private boolean downloading;
    private boolean downloadingVideo;
    private boolean preparing;
    private CircularProgressDrawable progressDrawable;
    private Utilities.Callback<Runnable> prepare;

    private PreparingVideoToast toast;

    public DownloadButton(Context context, Utilities.Callback<Runnable> prepare, int currentAccount, FrameLayout container, Theme.ResourcesProvider resourcesProvider) {
        super(context);

        this.prepare = prepare;
        this.currentAccount = currentAccount;
        this.container = container;
        this.resourcesProvider = resourcesProvider;

        setScaleType(ImageView.ScaleType.CENTER);
        setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
        setBackground(Theme.createSelectorDrawable(0x20ffffff));
        setVisibility(View.GONE);
        setAlpha(0f);

        setOnClickListener(e -> onClick());

        progressDrawable = new CircularProgressDrawable(dp(18), dp(2), 0xffffffff);

        updateImage();
    }

    private StoryEntry currentEntry;
    private BuildingVideo buildingVideo;
    private Uri savedToGalleryUri;

    public void setEntry(StoryEntry entry) {
        savedToGalleryUri = null;
        currentEntry = entry;
        if (buildingVideo != null) {
            buildingVideo.stop(true);
            buildingVideo = null;
        }
        if (toast != null) {
            toast.hide();
            toast = null;
        }
        if (entry == null) {
            downloading = false;
            updateImage();
            return;
        }
    }

    private void onClick() {
        if (Build.VERSION.SDK_INT >= 23 && (Build.VERSION.SDK_INT <= 28 || BuildVars.NO_SCOPED_STORAGE) && getContext().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            final Activity activity = AndroidUtilities.findActivity(getContext());
            if (activity != null) {
                activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 113);
            }
            return;
        }
        if (downloading || currentEntry == null) {
            return;
        }
        if (savedToGalleryUri != null) {
            if (Build.VERSION.SDK_INT >= 30) {
                getContext().getContentResolver().delete(savedToGalleryUri, null);
                savedToGalleryUri = null;
            } else if (Build.VERSION.SDK_INT < 29) {
                try {
                    new File(savedToGalleryUri.toString()).delete();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                savedToGalleryUri = null;
            }
        }

        downloading = true;
        if (toast != null) {
            toast.hide();
            toast = null;
        }
        if (buildingVideo != null) {
            buildingVideo.stop(true);
            buildingVideo = null;
        }
        if (prepare != null) {
            preparing = true;
            prepare.run(this::onClickInternal);
        }
        updateImage();
        if (prepare == null) {
            onClickInternal();
        }
    }

    private void onClickInternal() {
        if (!preparing || currentEntry == null) {
            return;
        }
        preparing = false;
        if (currentEntry.wouldBeVideo()) {
            downloadingVideo = true;
            toast = new PreparingVideoToast(getContext());
            toast.setOnCancelListener(() -> {
                preparing = false;
                if (buildingVideo != null) {
                    buildingVideo.stop(true);
                    buildingVideo = null;
                }
                if (toast != null) {
                    toast.hide();
                }
                downloading = false;
                updateImage();
            });
            container.addView(toast);

            final File file = AndroidUtilities.generateVideoPath();
            buildingVideo = new BuildingVideo(currentAccount, currentEntry, file, () -> {
                if (!downloading || currentEntry == null) {
                    return;
                }
                MediaController.saveFile(file.getAbsolutePath(), getContext(), 1, null, null, uri -> {
                    if (!downloading || currentEntry == null) {
                        return;
                    }
                    toast.setDone(R.raw.ic_save_to_gallery, LocaleController.getString("VideoSavedHint"), 3500);
                    downloading = false;
                    updateImage();

                    savedToGalleryUri = uri;
                }, false);
            }, progress -> {
                if (toast != null) {
                    toast.setProgress(progress);
                }
            }, () -> {
                if (!downloading || currentEntry == null) {
                    return;
                }
                toast.setDone(R.raw.error, LocaleController.getString("VideoConvertFail"), 3500);
                downloading = false;
                updateImage();
            });
        } else {
            downloadingVideo = false;
            final File file = AndroidUtilities.generatePicturePath(false, "png");
            if (file == null) {
                toast.setDone(R.raw.error, LocaleController.getString("UnknownError"), 3500);
                downloading = false;
                updateImage();
                return;
            }
            Utilities.themeQueue.postRunnable(() -> {
                currentEntry.buildPhoto(file);
                if (!downloading || currentEntry == null) {
                    return;
                }
                AndroidUtilities.runOnUIThread(() -> {
                    MediaController.saveFile(file.getAbsolutePath(), getContext(), 0, null, null, uri -> {
                        downloading = false;
                        updateImage();
                        if (toast != null) {
                            toast.hide();
                            toast = null;
                        }
                        toast = new PreparingVideoToast(getContext());
                        toast.setDone(R.raw.ic_save_to_gallery, LocaleController.getString("PhotoSavedHint"), 2500);
                        container.addView(toast);

                        savedToGalleryUri = uri;
                    }, false);
                });
            });
        }
        updateImage();
    }

    private boolean wasImageDownloading = true;
    private boolean wasVideoDownloading = true;

    private void updateImage() {
        if (wasImageDownloading != (downloading && !downloadingVideo)) {
            if (wasImageDownloading = (downloading && !downloadingVideo)) {
                AndroidUtilities.updateImageViewImageAnimated(this, progressDrawable);
            } else {
                AndroidUtilities.updateImageViewImageAnimated(this, R.drawable.media_download);
            }
        }
        if (wasVideoDownloading != (downloading && downloadingVideo)) {
            clearAnimation();
            animate().alpha((wasVideoDownloading = (downloading && downloadingVideo)) ? .4f : 1f).start();
        }
    }

    public void showToast(int resId, CharSequence text) {
        if (toast != null) {
            toast.hide();
            toast = null;
        }
        toast = new PreparingVideoToast(getContext());
        toast.setDone(resId, text, 3500);
        container.addView(toast);
    }

    public void showFailedVideo() {
        showToast(R.raw.error, LocaleController.getString("VideoConvertFail"));
    }

    private static class BuildingVideo implements NotificationCenter.NotificationCenterDelegate {

        final int currentAccount;
        final StoryEntry entry;
        final File file;

        private MessageObject messageObject;
        private final Runnable onDone;
        private final Utilities.Callback<Float> onProgress;
        private final Runnable onCancel;

        public BuildingVideo(int account, StoryEntry entry, File file, @NonNull Runnable onDone, @Nullable Utilities.Callback<Float> onProgress, @NonNull Runnable onCancel) {
            this.currentAccount = account;
            this.entry = entry;
            this.file = file;
            this.onDone = onDone;
            this.onProgress = onProgress;
            this.onCancel = onCancel;

            start();
        }

        public void start() {
            if (messageObject != null) {
                return;
            }

            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.filePreparingStarted);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileNewChunkAvailable);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.filePreparingFailed);

            TLRPC.TL_message message = new TLRPC.TL_message();
            message.id = 1;
            message.attachPath = file.getAbsolutePath();
            messageObject = new MessageObject(currentAccount, message, (MessageObject) null, false, false);
            entry.getVideoEditedInfo(info -> {
                if (messageObject == null) {
                    return;
                }
                messageObject.videoEditedInfo = info;
                MediaController.getInstance().scheduleVideoConvert(messageObject);
            });
        }

        public void stop(boolean cancel) {
            if (messageObject == null) {
                return;
            }

            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.filePreparingStarted);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileNewChunkAvailable);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.filePreparingFailed);

            if (cancel) {
                MediaController.getInstance().cancelVideoConvert(messageObject);
            }
            messageObject = null;
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.filePreparingStarted) {
                if ((MessageObject) args[0] == messageObject) {

                }
            } else if (id == NotificationCenter.fileNewChunkAvailable) {
                if ((MessageObject) args[0] == messageObject) {
                    String finalPath = (String) args[1];
                    long availableSize = (Long) args[2];
                    long finalSize = (Long) args[3];
                    float progress = (float) args[4];

                    if (onProgress != null) {
                        onProgress.run(progress);
                    }

                    if (finalSize > 0) {
                        onDone.run();
                        VideoEncodingService.stop();
                        stop(false);
                    }
                }
            } else if (id == NotificationCenter.filePreparingFailed) {
                if ((MessageObject) args[0] == messageObject) {
                    stop(false);
                    try {
                        if (file != null) {
                            file.delete();
                        }
                    } catch (Exception ignore) {}
                    onCancel.run();
                }
            }
        }
    }

    public static class PreparingVideoToast extends View {

        private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint textPaint2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint greyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final ButtonBounce cancelButton = new ButtonBounce(this);

        private RLottieDrawable lottieDrawable;

        private final StaticLayout preparingLayout;
        private final float preparingLayoutWidth, preparingLayoutLeft;

        private StaticLayout doneLayout;
        private float doneLayoutWidth, doneLayoutLeft;

        public PreparingVideoToast(Context context) {
            this(context, LocaleController.getString(R.string.PreparingSticker));
        }

        public PreparingVideoToast(Context context, String text) {
            super(context);

            dimPaint.setColor(0x5a000000);
            textPaint.setColor(0xffffffff);
            textPaint2.setColor(0xffffffff);
            backgroundPaint.setColor(0xcc282828);
            whitePaint.setColor(0xffffffff);
            greyPaint.setColor(0x33ffffff);

            whitePaint.setStyle(Paint.Style.STROKE);
            whitePaint.setStrokeCap(Paint.Cap.ROUND);
            whitePaint.setStrokeWidth(dp(4));
            greyPaint.setStyle(Paint.Style.STROKE);
            greyPaint.setStrokeCap(Paint.Cap.ROUND);
            greyPaint.setStrokeWidth(dp(4));

            textPaint.setTextSize(dp(14));
            textPaint2.setTextSize(dpf2(14.66f));

            preparingLayout = new StaticLayout(text, textPaint, AndroidUtilities.displaySize.x, Layout.Alignment.ALIGN_NORMAL, 1f, 0, false);
            preparingLayoutWidth = preparingLayout.getLineCount() > 0 ? preparingLayout.getLineWidth(0) : 0;
            preparingLayoutLeft = preparingLayout.getLineCount() > 0 ? preparingLayout.getLineLeft(0) : 0;

            show();
        }

        @Override
        protected boolean verifyDrawable(@NonNull Drawable who) {
            return who == lottieDrawable || super.verifyDrawable(who);
        }

        private boolean shown = false;
        private final AnimatedFloat showT = new AnimatedFloat(0, this, 0, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

        private boolean preparing = true;
        private float progress = 0;
        private final AnimatedFloat t = new AnimatedFloat(this);
        private final AnimatedFloat progressT = new AnimatedFloat(this);

        private final RectF prepareRect = new RectF();
        private final RectF toastRect = new RectF();
        private final RectF currentRect = new RectF();
        private final RectF hiddenRect = new RectF();

        private boolean deleted;

        @Override
        protected void onDraw(Canvas canvas) {
            final int restore = canvas.getSaveCount();
            final float showT = this.showT.set(shown ? 1 : 0);
            final float t = this.t.set(preparing ? 0 : 1);

            dimPaint.setAlpha((int) (0x5a * (1f - t) * showT));
            canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);

            final float prepareWidth = Math.max(preparingLayoutWidth, dp(54)) + dp(21 + 21);
            final float prepareHeight = dp(21 + 54 + 18 + 18) + preparingLayout.getHeight();
            prepareRect.set(
                (getWidth() - prepareWidth) / 2f,
                (getHeight() - prepareHeight) / 2f,
                (getWidth() + prepareWidth) / 2f,
                (getHeight() + prepareHeight) / 2f
            );

            final float toastWidth = dp(9 + 36 + 7 + 22) + doneLayoutWidth;
            final float toastHeight = dp(6 + 36 + 6);
            toastRect.set(
                (getWidth() - toastWidth) / 2f,
                (getHeight() - toastHeight) / 2f,
                (getWidth() + toastWidth) / 2f,
                (getHeight() + toastHeight) / 2f
            );

            AndroidUtilities.lerp(prepareRect, toastRect, t, currentRect);
            if (showT < 1 && preparing) {
                hiddenRect.set(getWidth() / 2f, getHeight() / 2f, getWidth() / 2f, getHeight() / 2f);
                AndroidUtilities.lerp(hiddenRect, currentRect, showT, currentRect);
            }
            if (showT < 1 && !preparing) {
                canvas.scale(lerp(.8f, 1f, showT), lerp(.8f, 1f, showT), currentRect.centerX(), currentRect.centerY());
            }
            backgroundPaint.setAlpha((int) (0xcc * showT));
            canvas.drawRoundRect(currentRect, dp(10), dp(10), backgroundPaint);
            canvas.save();
            canvas.clipRect(currentRect);
            if (t < 1) {
                drawPreparing(canvas, showT * (1f - t));
            }
            if (t > 0) {
                drawToast(canvas, showT * t);
            }
            canvas.restoreToCount(restore);

            if (showT <= 0 && !shown && !deleted) {
                deleted = true;
                post(() -> {
                    if (getParent() instanceof ViewGroup) {
                        ((ViewGroup) getParent()).removeView(this);
                    }
                });
            }
        }

        private void drawPreparing(Canvas canvas, float alpha) {
            final float progress = this.progressT.set(this.progress);

            final float cx = prepareRect.centerX();
            final float cy = prepareRect.top + dp(21 + 27);
            final float r = dp(25);

            greyPaint.setAlpha((int) (0x33 * alpha));
            canvas.drawCircle(cx, cy, r, greyPaint);
            AndroidUtilities.rectTmp.set(cx - r, cy - r, cx + r, cy + r);
            whitePaint.setAlpha((int) (0xFF * alpha));
            whitePaint.setStrokeWidth(dp(4));
            canvas.drawArc(AndroidUtilities.rectTmp, -90, progress * 360, false, whitePaint);

            final float cancelButtonScale = cancelButton.getScale(.15f);
            canvas.save();
            canvas.scale(cancelButtonScale, cancelButtonScale, cx, cy);
            whitePaint.setStrokeWidth(dp(3.4f));
            canvas.drawLine(cx - dp(7), cy - dp(7), cx + dp(7), cy + dp(7), whitePaint);
            canvas.drawLine(cx - dp(7), cy + dp(7), cx + dp(7), cy - dp(7), whitePaint);
            canvas.restore();

            canvas.save();
            canvas.translate(
                prepareRect.left + dp(21) - preparingLayoutLeft,
                prepareRect.bottom - dp(18) - preparingLayout.getHeight()
            );
            textPaint.setAlpha((int) (0xFF * alpha));
            preparingLayout.draw(canvas);
            canvas.restore();
        }

        private void drawToast(Canvas canvas, float alpha) {
            if (lottieDrawable != null) {
                lottieDrawable.setAlpha((int) (0xFF * alpha));
                lottieDrawable.setBounds(
                    (int) (toastRect.left + dp(9)),
                    (int) (toastRect.top + dp(6)),
                    (int) (toastRect.left + dp(9 + 36)),
                    (int) (toastRect.top + dp(6 + 36))
                );
                lottieDrawable.draw(canvas);
            }

            if (doneLayout != null) {
                canvas.save();
                canvas.translate(toastRect.left + dp(9 + 36 + 7) - doneLayoutLeft, toastRect.centerY() - doneLayout.getHeight() / 2f);
                textPaint2.setAlpha((int) (0xFF * alpha));
                doneLayout.draw(canvas);
                canvas.restore();
            }
        }

        public void setProgress(float progress) {
            this.progress = progress;
            invalidate();
        }

        public void setDone(int resId, CharSequence text, int delay) {
            if (lottieDrawable != null) {
                lottieDrawable.setCallback(null);
                lottieDrawable.recycle(true);
            }

            lottieDrawable = new RLottieDrawable(resId, "" + resId, dp(36), dp(36));
            lottieDrawable.setCallback(this);
            lottieDrawable.start();

            doneLayout = new StaticLayout(text, textPaint2, AndroidUtilities.displaySize.x, Layout.Alignment.ALIGN_NORMAL, 1f, 0, false);
            doneLayoutWidth = doneLayout.getLineCount() > 0 ? doneLayout.getLineWidth(0) : 0;
            doneLayoutLeft = doneLayout.getLineCount() > 0 ? doneLayout.getLineLeft(0) : 0;

            preparing = false;
            invalidate();
            if (hideRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(hideRunnable);
            }
            AndroidUtilities.runOnUIThread(hideRunnable = this::hide, delay);
        }

        private Runnable hideRunnable;
        public void hide() {
            if (hideRunnable != null) {
                AndroidUtilities.cancelRunOnUIThread(hideRunnable);
                hideRunnable = null;
            }
            this.shown = false;
            invalidate();
        }

        public void show() {
            this.shown = true;
            invalidate();
        }

        private Runnable onCancel;
        public void setOnCancelListener(Runnable onCancel) {
            this.onCancel = onCancel;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            boolean hit = currentRect.contains(event.getX(), event.getY());
            if (event.getAction() == MotionEvent.ACTION_DOWN && (preparing || hit)) {
                cancelButton.setPressed(hit);
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                if (cancelButton.isPressed()) {
                    if (hit) {
                        if (preparing) {
                            if (onCancel != null) {
                                onCancel.run();
                            }
                        } else {
                            hide();
                        }
                    }
                    cancelButton.setPressed(false);
                    return true;
                }
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                cancelButton.setPressed(false);
                return true;
            }
            return super.onTouchEvent(event);
        }
    }
}
