package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;

public class UpdateAppAlertDialog extends AlertDialog implements NotificationCenter.NotificationCenterDelegate {

    private TLRPC.TL_help_appUpdate appUpdate;
    private int accountNum;
    private String fileName;
    private RadialProgress radialProgress;
    private FrameLayout radialProgressView;
    private AnimatorSet progressAnimation;
    private Activity parentActivity;

    public UpdateAppAlertDialog(final Activity activity, TLRPC.TL_help_appUpdate update, int account) {
        super(activity, 0);
        appUpdate = update;
        accountNum = account;
        if (update.document instanceof TLRPC.TL_document) {
            fileName = FileLoader.getAttachFileName(update.document);
        }
        parentActivity = activity;

        setTopImage(R.drawable.update, Theme.getColor(Theme.key_dialogTopBackground));
        setTopHeight(175);
        setMessage(appUpdate.text);
        if (appUpdate.document instanceof TLRPC.TL_document) {
            setSecondTitle(AndroidUtilities.formatFileSize(appUpdate.document.size));
        }
        setDismissDialogByButtons(false);
        setTitle(LocaleController.getString("UpdateTelegram", R.string.UpdateTelegram));
        setPositiveButton(LocaleController.getString("UpdateNow", R.string.UpdateNow), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (!BlockingUpdateView.checkApkInstallPermissions(getContext())) {
                    return;
                }
                if (appUpdate.document instanceof TLRPC.TL_document) {
                    if (!BlockingUpdateView.openApkInstall(parentActivity, appUpdate.document)) {
                        FileLoader.getInstance(accountNum).loadFile(appUpdate.document, true, 1);
                        showProgress(true);
                    }
                } else if (appUpdate.url != null) {
                    Browser.openUrl(getContext(), appUpdate.url);
                    dialog.dismiss();
                }
            }
        });
        setNeutralButton(LocaleController.getString("Later", R.string.Later), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (appUpdate.document instanceof TLRPC.TL_document) {
                    FileLoader.getInstance(accountNum).cancelLoadFile(appUpdate.document);
                }
                dialog.dismiss();
            }
        });

        radialProgressView = new FrameLayout(parentActivity) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                int width = right - left;
                int height = bottom - top;
                int w = AndroidUtilities.dp(24);
                int l = (width - w) / 2;
                int t = (height - w) / 2 + AndroidUtilities.dp(2);
                radialProgress.setProgressRect(l, t, l + w, t + w);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                radialProgress.draw(canvas);
            }
        };
        radialProgressView.setWillNotDraw(false);
        radialProgressView.setAlpha(0.0f);
        radialProgressView.setScaleX(0.1f);
        radialProgressView.setScaleY(0.1f);
        radialProgressView.setVisibility(View.INVISIBLE);
        radialProgress = new RadialProgress(radialProgressView);
        radialProgress.setStrokeWidth(AndroidUtilities.dp(2));
        radialProgress.setBackground(null, true, false);
        radialProgress.setProgressColor(Theme.getColor(Theme.key_dialogButton));
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.FileDidLoaded) {
            String location = (String) args[0];
            if (fileName != null && fileName.equals(location)) {
                showProgress(false);
                BlockingUpdateView.openApkInstall(parentActivity, appUpdate.document);
            }
        } else if (id == NotificationCenter.FileDidFailedLoad) {
            String location = (String) args[0];
            if (fileName != null && fileName.equals(location)) {
                showProgress(false);
            }
        } else if (id == NotificationCenter.FileLoadProgressChanged) {
            String location = (String) args[0];
            if (fileName != null && fileName.equals(location)) {
                Float loadProgress = (Float) args[1];
                radialProgress.setProgress(loadProgress, true);
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NotificationCenter.getInstance(accountNum).addObserver(this, NotificationCenter.FileDidLoaded);
        NotificationCenter.getInstance(accountNum).addObserver(this, NotificationCenter.FileDidFailedLoad);
        NotificationCenter.getInstance(accountNum).addObserver(this, NotificationCenter.FileLoadProgressChanged);
        buttonsLayout.addView(radialProgressView, LayoutHelper.createFrame(36, 36));
    }

    @Override
    public void dismiss() {
        super.dismiss();
        NotificationCenter.getInstance(accountNum).removeObserver(this, NotificationCenter.FileDidLoaded);
        NotificationCenter.getInstance(accountNum).removeObserver(this, NotificationCenter.FileDidFailedLoad);
        NotificationCenter.getInstance(accountNum).removeObserver(this, NotificationCenter.FileLoadProgressChanged);
    }

    private void showProgress(final boolean show) {
        if (progressAnimation != null) {
            progressAnimation.cancel();
        }
        progressAnimation = new AnimatorSet();
        final View textButton = buttonsLayout.findViewWithTag(BUTTON_POSITIVE);
        if (show) {
            radialProgressView.setVisibility(View.VISIBLE);
            textButton.setEnabled(false);
            progressAnimation.playTogether(
                    ObjectAnimator.ofFloat(textButton, "scaleX", 0.1f),
                    ObjectAnimator.ofFloat(textButton, "scaleY", 0.1f),
                    ObjectAnimator.ofFloat(textButton, "alpha", 0.0f),
                    ObjectAnimator.ofFloat(radialProgressView, "scaleX", 1.0f),
                    ObjectAnimator.ofFloat(radialProgressView, "scaleY", 1.0f),
                    ObjectAnimator.ofFloat(radialProgressView, "alpha", 1.0f));
        } else {
            textButton.setVisibility(View.VISIBLE);
            textButton.setEnabled(true);
            progressAnimation.playTogether(
                    ObjectAnimator.ofFloat(radialProgressView, "scaleX", 0.1f),
                    ObjectAnimator.ofFloat(radialProgressView, "scaleY", 0.1f),
                    ObjectAnimator.ofFloat(radialProgressView, "alpha", 0.0f),
                    ObjectAnimator.ofFloat(textButton, "scaleX", 1.0f),
                    ObjectAnimator.ofFloat(textButton, "scaleY", 1.0f),
                    ObjectAnimator.ofFloat(textButton, "alpha", 1.0f));

        }
        progressAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (progressAnimation != null && progressAnimation.equals(animation)) {
                    if (!show) {
                        radialProgressView.setVisibility(View.INVISIBLE);
                    } else {
                        textButton.setVisibility(View.INVISIBLE);
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (progressAnimation != null && progressAnimation.equals(animation)) {
                    progressAnimation = null;
                }
            }
        });
        progressAnimation.setDuration(150);
        progressAnimation.start();
    }
}
