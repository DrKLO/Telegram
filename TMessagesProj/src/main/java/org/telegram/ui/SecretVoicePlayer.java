package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AudioVisualizerDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.SeekBar;
import org.telegram.ui.Components.SeekBarWaveform;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Components.ThanosEffect;
import org.telegram.ui.Components.VideoPlayer;
import org.telegram.ui.Stories.recorder.HintView2;
import org.telegram.ui.Stories.recorder.StoryRecorder;

import java.io.File;

public class SecretVoicePlayer extends Dialog {

    public final Context context;

//    WindowManager windowManager;
//    private final WindowManager.LayoutParams windowLayoutParams;
    private FrameLayout windowView;
    private FrameLayout containerView;

    private ThanosEffect thanosEffect;

    private final Rect insets = new Rect();
    private Bitmap blurBitmap;
    private BitmapShader blurBitmapShader;
    private Paint blurBitmapPaint;
    private Matrix blurMatrix;

    private boolean open;
    private float openProgress;
    private float openProgressLinear;

    private VideoPlayer player;

    private HintView2 hintView;
    private TextView closeButton;

    public SecretVoicePlayer(Context context) {
        super(context, R.style.TransparentDialog);
        this.context = context;

        windowView = new FrameLayout(context) {
            @Override
            protected void dispatchDraw(Canvas canvas) {
                if (openProgress > 0 && blurBitmapPaint != null) {
                    blurMatrix.reset();
                    final float s = (float) getWidth() / blurBitmap.getWidth();
                    blurMatrix.postScale(s, s);
                    blurBitmapShader.setLocalMatrix(blurMatrix);

                    blurBitmapPaint.setAlpha((int) (0xFF * openProgress));
                    canvas.drawRect(0, 0, getWidth(), getHeight(), blurBitmapPaint);
                }
                if (setCellInvisible && cell != null) {
                    cell.setVisibility(View.INVISIBLE);
                    setCellInvisible = false;
                }
                super.dispatchDraw(canvas);
            }

            @Override
            public boolean dispatchKeyEventPreIme(KeyEvent event) {
                if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    dismiss();
                    return true;
                }
                return super.dispatchKeyEventPreIme(event);
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                setupTranslation();
            }
        };
        windowView.setOnClickListener(v -> {
            if (closeAction == null) {
                dismiss();
            }
        });
        containerView = new FrameLayout(context) {
            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == myCell || child == hintView) {
                    canvas.save();
                    canvas.clipRect(0, AndroidUtilities.lerp(clipTop, 0, openProgress), getWidth(), AndroidUtilities.lerp(clipBottom, getHeight(), openProgress));
                    boolean r = super.drawChild(canvas, child, drawingTime);
                    canvas.restore();
                    return r;
                }
                return super.drawChild(canvas, child, drawingTime);
            }
        };
        windowView.addView(containerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        if (Build.VERSION.SDK_INT >= 21) {
            windowView.setFitsSystemWindows(true);
            windowView.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                @NonNull
                @Override
                public WindowInsets onApplyWindowInsets(@NonNull View v, @NonNull WindowInsets insets) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Insets r = insets.getInsets(WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.systemBars());
                        SecretVoicePlayer.this.insets.set(r.left, r.top, r.right, r.bottom);
                    } else {
                        SecretVoicePlayer.this.insets.set(insets.getStableInsetLeft(), insets.getStableInsetTop(), insets.getStableInsetRight(), insets.getStableInsetBottom());
                    }
                    containerView.setPadding(SecretVoicePlayer.this.insets.left, SecretVoicePlayer.this.insets.top, SecretVoicePlayer.this.insets.right, SecretVoicePlayer.this.insets.bottom);
                    windowView.requestLayout();
                    if (Build.VERSION.SDK_INT >= 30) {
                        return WindowInsets.CONSUMED;
                    } else {
                        return insets.consumeSystemWindowInsets();
                    }
                }
            });
        }

    }

    private void prepareBlur(View withoutView) {
        if (withoutView != null) {
            withoutView.setVisibility(View.INVISIBLE);
        }
        AndroidUtilities.makeGlobalBlurBitmap(bitmap -> {
            if (withoutView != null) {
                withoutView.setVisibility(View.VISIBLE);
            }
            blurBitmap = bitmap;

            blurBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            blurBitmapPaint.setShader(blurBitmapShader = new BitmapShader(blurBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            ColorMatrix colorMatrix = new ColorMatrix();
            AndroidUtilities.adjustSaturationColorMatrix(colorMatrix, Theme.isCurrentThemeDark() ? .05f : +.25f);
            AndroidUtilities.adjustBrightnessColorMatrix(colorMatrix, Theme.isCurrentThemeDark() ? -.02f : -.04f);
            blurBitmapPaint.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
            blurMatrix = new Matrix();
        }, 14);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setWindowAnimations(R.style.DialogNoAnimation);
        setContentView(windowView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WindowManager.LayoutParams params = window.getAttributes();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.FILL;
        params.dimAmount = 0;
        params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
        params.flags |= WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        if (Build.VERSION.SDK_INT >= 21) {
            params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR |
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS |
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION;
        }
        params.flags |= WindowManager.LayoutParams.FLAG_SECURE;
        params.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
        params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        if (Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        window.setAttributes(params);

        windowView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN);
        AndroidUtilities.setLightNavigationBar(windowView, !Theme.isCurrentThemeDark());
    }

    private Theme.ResourcesProvider resourcesProvider;
    private MessageObject messageObject;
    private ChatMessageCell myCell;
    private ChatMessageCell cell;

    private float tx, ty;
    private boolean hasTranslation;
    private float dtx, dty;
    private boolean hasDestTranslation;
    private void setupTranslation() {
        if (hasTranslation || windowView.getWidth() <= 0) return;
        if (cell != null) {
            int[] loc = new int[2];
            cell.getLocationOnScreen(loc);
            tx = loc[0] - insets.left - (windowView.getWidth() - insets.left - insets.right - cell.getWidth()) / 2f;
            ty = loc[1] - insets.top - (windowView.getHeight() - insets.top - insets.bottom - cell.getHeight()) / 2f;
            if (!hasDestTranslation) {
                hasDestTranslation = true;
                dtx = 0;
                float cy = Utilities.clamp(loc[1] + cell.getHeight() / 2f, windowView.getHeight() * .7f, windowView.getHeight() * .3f);
                dty = cy - cell.getHeight() / 2f - (windowView.getHeight() - cell.getHeight()) / 2f;
                dty = AndroidUtilities.lerp(0, dty, .78f);
            }
            updateTranslation();
        } else {
            tx = ty = 0;
        }
        hasTranslation = true;
    }
    private void updateTranslation() {
        if (thanosEffect != null) return;
        myCell.setTranslationX(AndroidUtilities.lerp(tx, dtx, openProgress));
        myCell.setTranslationY(AndroidUtilities.lerp(ty, dty, openProgress));
        if (hintView != null) {
            hintView.setTranslationX(AndroidUtilities.lerp(tx, dtx, openProgress));
            hintView.setTranslationY(AndroidUtilities.lerp(ty, dty, openProgress));
        }
    }

    private float clipTop = 0, clipBottom = 0;

    private AudioVisualizerDrawable audioVisualizerDrawable;
    private boolean setCellInvisible;
    private Runnable openAction, closeAction;
    public void setCell(ChatMessageCell messageCell, Runnable openAction, Runnable closeAction) {
        this.openAction = openAction;
        this.closeAction = closeAction;
        if (myCell != null) {
            containerView.removeView(myCell);
            myCell = null;
        }
        cell = messageCell;
        messageObject = cell != null ? cell.getMessageObject() : null;
        resourcesProvider = cell != null ? cell.getResourcesProvider() : null;
        if (cell != null) {

            clipTop = messageCell.parentBoundsTop;
            clipBottom = messageCell.parentBoundsBottom;
            if (messageCell.getParent() instanceof View) {
                View parent = (View) messageCell.getParent();
                clipTop += parent.getY();
                clipBottom += parent.getY();
            }

            myCell = new ChatMessageCell(getContext(), false, null, cell.getResourcesProvider()) {
                @Override
                public void setPressed(boolean pressed) {}
            };
            myCell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
                @Override
                public boolean canPerformActions() {
                    return false;
                }
            });
            myCell.setMessageObject(messageObject, cell.getCurrentMessagesGroup(), cell.pinnedBottom, cell.pinnedTop);
            audioVisualizerDrawable = new AudioVisualizerDrawable();
            audioVisualizerDrawable.setParentView(myCell);
            myCell.overrideAudioVisualizer(audioVisualizerDrawable);
            if (myCell.getSeekBarWaveform() != null) {
                myCell.getSeekBarWaveform().setExplosionRate(openProgressLinear);
            }
            hasTranslation = false;
            containerView.addView(myCell, new FrameLayout.LayoutParams(cell.getWidth(), cell.getHeight(), Gravity.CENTER));
        }

        MediaController.getInstance().pauseByRewind();

        if (player != null) {
            player.pause();
            player.releasePlayer(true);
            player = null;
        }
        if (cell != null && cell.getMessageObject() != null) {
            File file = FileLoader.getInstance(cell.getMessageObject().currentAccount).getPathToAttach(cell.getMessageObject().getDocument());
            if (file == null || !file.exists()) {
                file = FileLoader.getInstance(cell.getMessageObject().currentAccount).getPathToMessage(cell.getMessageObject().messageOwner);
            }
            if ((file == null || !file.exists()) && (cell.getMessageObject().messageOwner.attachPath != null)) {
                file = new File(cell.getMessageObject().messageOwner.attachPath);
            }
            if (file == null) {
                return;
            }
            player = new VideoPlayer();
            player.setDelegate(new VideoPlayer.VideoPlayerDelegate() {
                @Override
                public void onStateChanged(boolean playWhenReady, int playbackState) {
                    if (playbackState == ExoPlayer.STATE_ENDED) {
                        dismiss();
                    } else {
                        AndroidUtilities.cancelRunOnUIThread(checkTimeRunnable);
                        AndroidUtilities.runOnUIThread(checkTimeRunnable, 16);
                    }
                }

                @Override
                public void onError(VideoPlayer player, Exception e) {

                }

                @Override
                public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {

                }

                @Override
                public void onRenderedFirstFrame() {

                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

                }

                @Override
                public boolean onSurfaceDestroyed(SurfaceTexture surfaceTexture) {
                    return false;
                }
            });
            player.setAudioVisualizerDelegate(new VideoPlayer.AudioVisualizerDelegate() {
                @Override
                public void onVisualizerUpdate(boolean playing, boolean animate, float[] values) {
                    audioVisualizerDrawable.setWaveform(playing, animate, values);
                }

                @Override
                public boolean needUpdate() {
                    return audioVisualizerDrawable.getParentView() != null;
                }
            });
            player.preparePlayer(Uri.fromFile(file), "other");
            player.play();
        }

        if (hintView != null) {
            containerView.removeView(hintView);
            hintView = null;
        }
        final boolean isOut = messageObject != null && messageObject.isOutOwner();
        if (messageObject != null && messageObject.getDialogId() != UserConfig.getInstance(messageObject.currentAccount).getClientUserId()) {
            hintView = new HintView2(context, HintView2.DIRECTION_BOTTOM);
            hintView.setMultilineText(true);
            if (isOut) {
                String name = "";
                long did = messageObject.getDialogId();
                if (did > 0) {
                    TLRPC.User user = MessagesController.getInstance(messageObject.currentAccount).getUser(did);
                    if (user != null) {
                        name = UserObject.getFirstName(user);
                    }
                } else {
                    TLRPC.Chat chat = MessagesController.getInstance(messageObject.currentAccount).getChat(-did);
                    if (chat != null) {
                        name = chat.title;
                    }
                }
                hintView.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.VoiceOnceOutHint, name)));
            } else {
                hintView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.VoiceOnceHint)));
            }
            hintView.setRounding(12);
            hintView.setPadding(dp(!isOut && !cell.pinnedBottom ? 6 : 0), 0, 0, 0);
            hintView.setJointPx(0, dp(34));
            hintView.setTextSize(14);
            hintView.setMaxWidthPx(HintView2.cutInFancyHalf(hintView.getText(), hintView.getTextPaint()));
            containerView.addView(hintView, LayoutHelper.createFrame((int) (cell.getWidth() / AndroidUtilities.density * .6f), 150, Gravity.CENTER, (cell.getWidth() * -(1f - .6f) / 2f + cell.getBoundsLeft()) / AndroidUtilities.density + 1, -75 - (cell.getHeight() / AndroidUtilities.density) / 2f - 8, 0, 0));
            hintView.show();
        }

        if (closeButton != null) {
            containerView.removeView(closeButton);
            closeButton = null;
        }
        closeButton = new TextView(context);
        closeButton.setTextColor(0xFFFFFFFF);
        closeButton.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        if (Theme.isCurrentThemeDark()) {
            closeButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(64, 0x20ffffff, 0x33ffffff));
        } else {
            closeButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(64, 0x2e000000, 0x44000000));
        }
        closeButton.setPadding(dp(12), dp(6), dp(12), dp(6));
        ScaleStateListAnimator.apply(closeButton);
        closeButton.setText(LocaleController.getString(isOut ? R.string.VoiceOnceClose : R.string.VoiceOnceDeleteClose));
        closeButton.setOnClickListener(v -> {
            dismiss();
        });
        containerView.addView(closeButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER, 0, 0, 0, 18));

        if (!isOut && myCell != null && myCell.getMessageObject() != null && myCell.getMessageObject().messageOwner != null) {
            myCell.getMessageObject().messageOwner.media_unread = false;
            myCell.invalidate();
        }
    }

    @Override
    public void show() {
        super.show();

        prepareBlur(cell);
        setCellInvisible = true;
        animateOpenTo(open = true, null);

        if (this.openAction != null) {
            AndroidUtilities.runOnUIThread(this.openAction);
            this.openAction = null;
        }
    }

    private Runnable checkTimeRunnable = this::checkTime;
    private void checkTime() {
        if (player == null) {
            return;
        }
        float progress = player.getCurrentPosition() / (float) player.getDuration();
        if (myCell != null) {
            myCell.overrideDuration((player.getDuration() - player.getCurrentPosition()) / 1000L);
            myCell.updatePlayingMessageProgress();
            SeekBarWaveform seekBarWaveform = myCell.getSeekBarWaveform();
            if (seekBarWaveform != null) {
                seekBarWaveform.explodeAt(progress);
            }
        }

        if (player.isPlaying()) {
            AndroidUtilities.cancelRunOnUIThread(checkTimeRunnable);
            AndroidUtilities.runOnUIThread(checkTimeRunnable, 16);
        }
    }

    public boolean isShown() {
        return !dismissing;
    }

    private boolean dismissing = false;

    private AlertDialog backDialog;
    @Override
    public void onBackPressed() {
        if (backDialog != null) {
            backDialog.dismiss();
            backDialog = null;
            return;
        }
        if (!dismissing && messageObject != null && !messageObject.isOutOwner()) {
            backDialog = new AlertDialog.Builder(getContext(), resourcesProvider)
                    .setTitle(LocaleController.getString(R.string.VoiceOnceCloseTitle))
                    .setMessage(LocaleController.getString(R.string.VoiceOnceCloseMessage))
                    .setPositiveButton(LocaleController.getString(R.string.Continue), (di, w) -> {
                        if (backDialog != null) {
                            backDialog.dismiss();
                        }
                    })
                    .setNegativeButton(LocaleController.getString(R.string.Delete), (di, w) -> {
                        if (backDialog != null) {
                            backDialog.dismiss();
                            backDialog = null;
                        }
                        dismiss();
                    })
                    .create();
            backDialog.show();
            TextView button = (TextView) backDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
            if (button != null) {
                button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
            }
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void dismiss() {
        if (dismissing) return;
        if (backDialog != null) {
            backDialog.dismiss();
            backDialog = null;
        }
        dismissing = true;
        if (hintView != null) {
            hintView.hide();
        }
        if (player != null) {
            player.pause();
            player.releasePlayer(true);
            player = null;
        }
        if (myCell != null && myCell.getSeekBarWaveform() != null) {
            myCell.getSeekBarWaveform().setExplosionRate(openProgressLinear);
        }
        hasTranslation = false;
        setupTranslation();
        animateOpenTo(open = false, () -> {
            if (thanosEffect == null) {
                AndroidUtilities.runOnUIThread(super::dismiss);
                if (cell != null) {
                    cell.invalidate();
                    cell.setVisibility(View.VISIBLE);
                }
            }

            MediaController.getInstance().tryResumePausedAudio();
        });
        windowView.invalidate();

        if (this.closeAction != null) {
            AndroidUtilities.runOnUIThread(this.closeAction);
            this.closeAction = null;

            thanosEffect = new ThanosEffect(context, null);
            windowView.addView(thanosEffect, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
            thanosEffect.animate(myCell, () -> {
                super.dismiss();
                if (cell != null) {
                    cell.setVisibility(View.VISIBLE);
                    cell.invalidate();
                }
            });

            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            getWindow().setAttributes(params);
        }
    }

    private ValueAnimator openAnimator;
    private void animateOpenTo(boolean open, Runnable after) {
        if (openAnimator != null) {
            openAnimator.cancel();
        }
        setupTranslation();
        openAnimator = ValueAnimator.ofFloat(openProgressLinear, open ? 1 : 0);
        openAnimator.addUpdateListener(anm -> {
            openProgressLinear = (float) anm.getAnimatedValue();
            openProgress = open ? CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(openProgressLinear) : 1f - CubicBezierInterpolator.EASE_OUT_QUINT.getInterpolation(1f - openProgressLinear);
            windowView.invalidate();
            containerView.invalidate();
            updateTranslation();
            if (closeButton != null) {
                closeButton.setAlpha(openProgress);
            }
            if (myCell != null && myCell.getSeekBarWaveform() != null) {
                myCell.getSeekBarWaveform().setExplosionRate((open ? CubicBezierInterpolator.EASE_OUT : CubicBezierInterpolator.EASE_IN).getInterpolation(Utilities.clamp(openProgressLinear * 1.25f, 1f, 0f)));
            }
        });
        openAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                openProgress = openProgressLinear = open ? 1 : 0;
                windowView.invalidate();
                containerView.invalidate();
                updateTranslation();
                if (closeButton != null) {
                    closeButton.setAlpha(openProgress);
                }
                if (myCell != null && myCell.getSeekBarWaveform() != null) {
                    myCell.getSeekBarWaveform().setExplosionRate(openProgressLinear);
                }
                if (after != null) {
                    after.run();
                }
            }
        });
        openAnimator.setDuration(!open && closeAction == null ? 330 : 520);
        openAnimator.start();
    }
}
