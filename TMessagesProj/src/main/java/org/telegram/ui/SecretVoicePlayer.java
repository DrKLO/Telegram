package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
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
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.exoplayer2.ExoPlayer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
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
import org.telegram.ui.Components.EarListener;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.SeekBarWaveform;
import org.telegram.ui.Components.ThanosEffect;
import org.telegram.ui.Components.TimerParticles;
import org.telegram.ui.Components.VideoPlayer;
import org.telegram.ui.Stories.recorder.HintView2;

import java.io.File;

public class SecretVoicePlayer extends Dialog {

    public final Context context;

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
    private float openProgress2;

    private VideoPlayer player;

    private HintView2 hintView;
    private TextView closeButton;

    private EarListener earListener;

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
            private final Path clipPath = new Path();
            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child == myCell || child == hintView) {
                    canvas.save();
                    canvas.clipRect(0, AndroidUtilities.lerp(clipTop, 0, openProgress), getWidth(), AndroidUtilities.lerp(clipBottom, getHeight(), openProgress));
                    boolean r = super.drawChild(canvas, child, drawingTime);
                    canvas.restore();
                    return r;
                }
                if (child == textureView) {
                    canvas.save();

                    clipPath.rewind();
                    clipPath.addCircle(myCell.getX() + rect.centerX(), myCell.getY() + rect.centerY(), rect.width() / 2f, Path.Direction.CW);
                    canvas.clipPath(clipPath);
                    canvas.clipRect(0, AndroidUtilities.lerp(clipTop, 0, openProgress), getWidth(), AndroidUtilities.lerp(clipBottom, getHeight(), openProgress));
                    canvas.translate(
                        - textureView.getX(),
                        - textureView.getY()
                    );
                    canvas.translate(
                        myCell.getX() + rect.left,
                        myCell.getY() + rect.top
                    );
                    canvas.scale(
                        rect.width() / textureView.getMeasuredWidth(),
                        rect.height() / textureView.getMeasuredHeight(),
                        textureView.getX(),
                        textureView.getY()
                    );
                    boolean r = super.drawChild(canvas, child, drawingTime);
                    canvas.restore();
                    return r;
                }
                return super.drawChild(canvas, child, drawingTime);
            }
        };
        containerView.setClipToPadding(false);
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
                        SecretVoicePlayer.this.insets.set(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
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

        if (SharedConfig.raiseToListen) {
            earListener = new EarListener(context);
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
        if (!BuildVars.DEBUG_PRIVATE_VERSION) {
            params.flags |= WindowManager.LayoutParams.FLAG_SECURE;
        }
        params.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
        params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        if (Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        window.setAttributes(params);

        windowView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_FULLSCREEN);
        AndroidUtilities.setLightNavigationBar(windowView, !Theme.isCurrentThemeDark());
    }

    private float tx, ty;
    private boolean hasTranslation;
    private float dtx, dty;
    private boolean hasDestTranslation;
    private float heightdiff;
    private void setupTranslation() {
        if (hasTranslation || windowView.getWidth() <= 0) return;
        if (cell != null) {
            int[] loc = new int[2];
            cell.getLocationOnScreen(loc);
            tx = loc[0] - insets.left - (windowView.getWidth() - insets.left - insets.right - cell.getWidth()) / 2f;
            ty = loc[1] - insets.top - (windowView.getHeight() - insets.top - insets.bottom - cell.getHeight() - heightdiff) / 2f;
            if (!hasDestTranslation) {
                hasDestTranslation = true;
                dtx = 0;
                float cy = Utilities.clamp(loc[1] + cell.getHeight() / 2f, windowView.getHeight() * .7f, windowView.getHeight() * .3f);
                dty = cy - cell.getHeight() / 2f - (windowView.getHeight() - cell.getHeight()) / 2f;
                if (isRound) {
                    dty = 0;
                } else {
                    dty = AndroidUtilities.lerp(0, dty, .78f);
                }
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

    private Theme.ResourcesProvider resourcesProvider;
    private MessageObject messageObject;
    private ChatMessageCell myCell;
    private ChatMessageCell cell;
    private TextureView textureView;
    private boolean renderedFirstFrame;
    private final RectF rect = new RectF();
    private boolean isRound;

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
        isRound = messageObject != null && messageObject.isRoundVideo();
        resourcesProvider = cell != null ? cell.getResourcesProvider() : null;
        int videoDp = 360;
        if (cell != null) {

            clipTop = messageCell.parentBoundsTop;
            clipBottom = messageCell.parentBoundsBottom;
            if (messageCell.getParent() instanceof View) {
                View parent = (View) messageCell.getParent();
                clipTop += parent.getY();
                clipBottom += parent.getY();
            }

            int width = cell.getWidth();
            int height = cell.getHeight();
            if (isRound) {
                height = (int) Math.min(dp(360), Math.min(width, AndroidUtilities.displaySize.y));
            }
            heightdiff = height - cell.getHeight();
            final int finalWidth = width;
            final int finalHeight = height;
            videoDp = (int) Math.ceil(Math.min(width, height) * .92f / AndroidUtilities.density);

            myCell = new ChatMessageCell(getContext(), UserConfig.selectedAccount, false, null, cell.getResourcesProvider()) {

                @Override
                public int getBoundsLeft() {
                    return 0;
                }

                @Override
                public int getBoundsRight() {
                    return getWidth();
                }

                @Override
                public void setPressed(boolean pressed) {}

                private boolean setRect = false;
                final RectF fromRect = new RectF();
                final RectF toRect = new RectF();

                private RadialGradient radialGradient;
                private Paint radialPaint;
                private Matrix radialMatrix;

                @Override
                protected void onDraw(Canvas canvas) {
                    if (isRound) {
                        if (!setRect) {
                            fromRect.set(getPhotoImage().getImageX(), getPhotoImage().getImageY(), getPhotoImage().getImageX2(), getPhotoImage().getImageY2());
                            final float sz = Math.min(getMeasuredWidth(), getMeasuredHeight()) * .92f;
                            toRect.set((getMeasuredWidth() - sz) / 2f, (getMeasuredHeight() - sz) / 2f, (getMeasuredWidth() + sz) / 2f, (getMeasuredHeight() + sz) / 2f);
                            setRect = true;

                            radialGradient = new RadialGradient(0, 0, 48, new int[] { 0xffffffff, 0xffffffff, 0 }, new float[] { 0, 0.8f, 1 }, Shader.TileMode.CLAMP);
                            radialPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                            radialPaint.setShader(radialGradient);
                            radialPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
                            radialMatrix = new Matrix();
                        }

                        AndroidUtilities.lerp(fromRect, toRect, openProgress, rect);
                        setImageCoords(rect.left, rect.top, rect.width(), rect.height());
                        getPhotoImage().setRoundRadius((int) rect.width());

                        if (openProgress > 0 && renderedFirstFrame) {
                            canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
                        }

                        radialProgressAlpha = 1f - openProgress;
                    }
                    super.onDraw(canvas);
                    if (isRound && openProgress > 0 && renderedFirstFrame) {
                        canvas.restore();
                    }
                }

                @Override
                public void drawReactionsLayout(Canvas canvas, float alpha, Integer only) {
                    canvas.save();
                    canvas.translate(lerp(0, -reactionsLayoutInBubble.x, openProgress), lerp(cell.getBackgroundDrawableBottom() - getBackgroundDrawableBottom(), reactionsLayoutInBubble.totalHeight, openProgress));
                    super.drawReactionsLayout(canvas, (1.0f - openProgress) * alpha, only);
                    canvas.restore();
                }

                @Override
                public void drawTime(Canvas canvas, float alpha, boolean fromParent) {
                    canvas.save();
                    if (isRound) {
                        final float timeWidth = this.timeWidth + AndroidUtilities.dp(8 + (messageObject != null && messageObject.isOutOwner() ? 20 + (messageObject != null && messageObject.type == MessageObject.TYPE_EMOJIS ? 4 : 0) : 0));
                        canvas.translate((toRect.right - timeWidth - timeX) * openProgress, 0);
                    }
                    super.drawTime(canvas, alpha, fromParent);
                    canvas.restore();
                }

                @Override
                protected void drawRadialProgress(Canvas canvas) {
                    super.drawRadialProgress(canvas);
                }

                @Override
                public void setVisibility(int visibility) {
                    super.setVisibility(visibility);
                    if (textureView != null && visibility == View.GONE) {
                        textureView.setVisibility(visibility);
                    }
                }

                private Path clipPath = new Path();
                private Paint clipPaint;
                private Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                private TimerParticles timerParticles;
                private AnimatedFloat renderedFirstFrameT = new AnimatedFloat(0, this, 0, 120, new LinearInterpolator());

                private Paint getClipPaint() {
                    if (clipPaint == null) {
                        clipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        clipPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                    }
                    return clipPaint;
                }

                @Override
                public void drawBlurredPhoto(Canvas canvas) {
                    if (radialPaint != null) {
                        if (openProgress > 0) {
                            if (renderedFirstFrame) {
                                if (drawingToBitmap) {
                                    Bitmap bitmap = textureView.getBitmap();
                                    if (bitmap != null) {
                                        canvas.save();
                                        clipPath.rewind();
                                        clipPath.addCircle(rect.centerX(), rect.centerY(), rect.width() / 2f, Path.Direction.CW);
                                        canvas.clipPath(clipPath);
                                        canvas.scale(rect.width() / bitmap.getWidth(), rect.height() / bitmap.getHeight());
                                        canvas.translate(rect.left, rect.top);
                                        canvas.drawBitmap(bitmap, 0, 0, null);
                                        canvas.restore();
                                        bitmap.recycle();
                                    }
                                } else {
                                    canvas.drawCircle(rect.centerX(), rect.centerY(), rect.width() / 2f, getClipPaint());
                                }
                                getPhotoImage().setAlpha(Math.max(1f - renderedFirstFrameT.set(renderedFirstFrame), 1f - openProgress));
                                getPhotoImage().draw(canvas);
                            } else {
                                getPhotoImage().draw(canvas);
                            }
                        }
                        radialMatrix.reset();
                        final float s = rect.width() / (96f * 0.8f) * openProgress2;
                        radialMatrix.postScale(s, s);
                        radialMatrix.postTranslate(rect.centerX(), rect.centerY());
                        radialGradient.setLocalMatrix(radialMatrix);
                        canvas.saveLayerAlpha(rect, 0xFF, Canvas.ALL_SAVE_FLAG);
                        super.drawBlurredPhoto(canvas);
                        canvas.save();
                        canvas.drawRect(rect, radialPaint);
                        canvas.restore();
                        canvas.restore();
                    } else {
                        super.drawBlurredPhoto(canvas);
                    }
                    canvas.saveLayerAlpha(rect, (int) (0xB2 * openProgress2), Canvas.ALL_SAVE_FLAG);
                    progressPaint.setStyle(Paint.Style.STROKE);
                    progressPaint.setStrokeWidth(dp(3.33f));
                    progressPaint.setColor(0xffffffff);
                    progressPaint.setStrokeCap(Paint.Cap.ROUND);
                    AndroidUtilities.rectTmp.set(rect);
                    AndroidUtilities.rectTmp.inset(dp(7), dp(7));
                    canvas.drawArc(AndroidUtilities.rectTmp, -90, -360 * (1f - progress), false, progressPaint);
                    if (timerParticles == null) {
                        timerParticles = new TimerParticles(120);
                        timerParticles.big = true;
                    }
                    progressPaint.setStrokeWidth(dp(2.8f));
                    timerParticles.draw(canvas, progressPaint, AndroidUtilities.rectTmp, (1f - progress) * -360, 1f);
                    canvas.restore();
                }

                @Override
                public void drawBlurredPhotoParticles(Canvas canvas) {
                    final float s = AndroidUtilities.lerp(1, 1.5f, openProgress2);
//                    canvas.scale(s, s, rect.centerX(), rect.centerY());
                    super.drawBlurredPhotoParticles(canvas);
                }

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    setMeasuredDimension(finalWidth, finalHeight);
                }
            };
            cell.copyVisiblePartTo(myCell);
            myCell.copySpoilerEffect2AttachIndexFrom(cell);
            myCell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
                @Override
                public boolean canPerformActions() {
                    return false;
                }
            });
            myCell.setMessageObject(messageObject, cell.getCurrentMessagesGroup(), cell.pinnedBottom, cell.pinnedTop, false);
            if (!isRound) {
                audioVisualizerDrawable = new AudioVisualizerDrawable();
                audioVisualizerDrawable.setParentView(myCell);
                myCell.overrideAudioVisualizer(audioVisualizerDrawable);
                if (myCell.getSeekBarWaveform() != null) {
                    myCell.getSeekBarWaveform().setExplosionRate(openProgress);
                }
            }
            hasTranslation = false;
            containerView.addView(myCell, new FrameLayout.LayoutParams(cell.getWidth(), height, Gravity.CENTER));
        }

        if (textureView != null) {
            containerView.removeView(textureView);
            textureView = null;
        }
        if (isRound) {
            renderedFirstFrame = false;
            textureView = new TextureView(context);
            containerView.addView(textureView, 0, LayoutHelper.createFrame(videoDp, videoDp));
        }

        MediaController.getInstance().pauseByRewind();

        if (player != null) {
            player.pause();
            player.releasePlayer(true);
            player = null;
        }
        if (cell != null && cell.getMessageObject() != null) {
            File file = FileLoader.getInstance(cell.getMessageObject().currentAccount).getPathToAttach(cell.getMessageObject().getDocument());
            if (file != null && !file.exists()) {
                file = new File(file.getPath() + ".enc");
            }
            if (file == null || !file.exists()) {
                file = FileLoader.getInstance(cell.getMessageObject().currentAccount).getPathToMessage(cell.getMessageObject().messageOwner);
                if (file != null && !file.exists()) {
                    file = new File(file.getPath() + ".enc");
                }
            }
            if ((file == null || !file.exists()) && (cell.getMessageObject().messageOwner.attachPath != null)) {
                file = new File(cell.getMessageObject().messageOwner.attachPath);
            }
            if (file == null || !file.exists()) {
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
                    AndroidUtilities.runOnUIThread(() -> {
                        renderedFirstFrame = true;
                        myCell.invalidate();
                    });
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

                }

                @Override
                public boolean onSurfaceDestroyed(SurfaceTexture surfaceTexture) {
                    return false;
                }
            });
            if (audioVisualizerDrawable != null) {
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
            }
            if (isRound) {
                player.setTextureView(textureView);
            }
            player.preparePlayer(Uri.fromFile(file), "other");
            player.play();
            if (earListener != null) {
                earListener.attachPlayer(player);
            }
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
                hintView.setText(AndroidUtilities.replaceTags(LocaleController.formatString(isRound ? R.string.VideoOnceOutHint : R.string.VoiceOnceOutHint, name)));
            } else {
                hintView.setText(AndroidUtilities.replaceTags(LocaleController.getString(isRound ? R.string.VideoOnceHint : R.string.VoiceOnceHint)));
            }
            hintView.setRounding(12);
            hintView.setPadding(dp(!isOut && !cell.pinnedBottom ? 6 : 0), 0, 0, 0);
            if (isRound) {
                hintView.setJointPx(0.5f, 0);
                hintView.setTextAlign(Layout.Alignment.ALIGN_CENTER);
            } else {
                hintView.setJointPx(0, dp(34));
                hintView.setTextAlign(Layout.Alignment.ALIGN_NORMAL);
            }
            hintView.setTextSize(14);
            hintView.setMaxWidthPx(HintView2.cutInFancyHalf(hintView.getText(), hintView.getTextPaint()));
            if (isRound) {
                containerView.addView(hintView, LayoutHelper.createFrame((int) (cell.getWidth() / AndroidUtilities.density * .6f), 150, Gravity.CENTER, 0, -75 - ((cell.getHeight() + heightdiff) / AndroidUtilities.density) / 2f, 0, 0));
            } else {
                containerView.addView(hintView, LayoutHelper.createFrame((int) (cell.getWidth() / AndroidUtilities.density * .6f), 150, Gravity.CENTER, (cell.getWidth() * -(1f - .6f) / 2f + cell.getBoundsLeft()) / AndroidUtilities.density + 1, -75 - (cell.getHeight() / AndroidUtilities.density) / 2f - 8, 0, 0));
            }
            hintView.show();
        }

        if (closeButton != null) {
            containerView.removeView(closeButton);
            closeButton = null;
        }
        closeButton = new TextView(context);
        closeButton.setTextColor(0xFFFFFFFF);
        closeButton.setTypeface(AndroidUtilities.bold());
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
        if (!AndroidUtilities.isSafeToShow(getContext())) return;
        super.show();

        prepareBlur(cell);
        setCellInvisible = true;
        animateOpenTo(open = true, null);

        if (this.openAction != null) {
            AndroidUtilities.runOnUIThread(this.openAction);
            this.openAction = null;
        }

        if (earListener != null) {
            earListener.attach();
        }
    }

    private Runnable checkTimeRunnable = this::checkTime;
    private float progress = 0;

    private void checkTime() {
        if (player == null) {
            return;
        }
        progress = player.getCurrentPosition() / (float) player.getDuration();
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
                    .setTitle(LocaleController.getString(isRound ? R.string.VideoOnceCloseTitle : R.string.VoiceOnceCloseTitle))
                    .setMessage(LocaleController.getString(isRound ? R.string.VideoOnceCloseMessage : R.string.VoiceOnceCloseMessage))
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
        if (!isRound && myCell != null && myCell.getSeekBarWaveform() != null) {
            myCell.getSeekBarWaveform().setExplosionRate(openProgress);
        }
        hasTranslation = false;
        setupTranslation();
        animateOpenTo(open = false, () -> {
            if (thanosEffect == null) {
                AndroidUtilities.runOnUIThread(super::dismiss);
                if (cell != null) {
                    cell.setVisibility(View.VISIBLE);
                    cell.invalidate();
                }
            }

            MediaController.getInstance().tryResumePausedAudio();
        });
        windowView.invalidate();

        if (this.closeAction != null) {
            if (cell != null) {
                cell.makeVisibleAfterChange = true;
//                AndroidUtilities.runOnUIThread(() -> {
//                    cell.makeVisibleAfterChange = false;
//                    cell.setVisibility(View.VISIBLE);
//                    cell.invalidate();
//                }, ChatListItemAnimator.DEFAULT_DURATION);
            }

            AndroidUtilities.runOnUIThread(this.closeAction);
            this.closeAction = null;

//            myCell.setOverrideInvalidate(() -> {});
            thanosEffect = new ThanosEffect(context, null);
            windowView.addView(thanosEffect, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
            thanosEffect.animate(myCell, 1.5f, super::dismiss);

            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            getWindow().setAttributes(params);
        }

        if (earListener != null) {
            earListener.detach();
        }
    }

    private ValueAnimator openAnimator;
    private ValueAnimator open2Animator;
    private void animateOpenTo(boolean open, Runnable after) {
        if (openAnimator != null) {
            openAnimator.cancel();
        }
        if (open2Animator != null) {
            open2Animator.cancel();
        }
        setupTranslation();
        openAnimator = ValueAnimator.ofFloat(openProgress, open ? 1 : 0);
        openAnimator.addUpdateListener(anm -> {
            openProgress = (float) anm.getAnimatedValue();
            windowView.invalidate();
            containerView.invalidate();
            if (isRound) {
                myCell.invalidate();
            }
            updateTranslation();
            if (closeButton != null) {
                closeButton.setAlpha(openProgress);
            }
            if (!isRound && myCell != null && myCell.getSeekBarWaveform() != null) {
                myCell.getSeekBarWaveform().setExplosionRate((open ? CubicBezierInterpolator.EASE_OUT : CubicBezierInterpolator.EASE_IN).getInterpolation(Utilities.clamp(openProgress * 1.25f, 1f, 0f)));
            }
        });
        openAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                openProgress = open ? 1 : 0;
                windowView.invalidate();
                containerView.invalidate();
                updateTranslation();
                if (closeButton != null) {
                    closeButton.setAlpha(openProgress);
                }
                if (isRound) {
                    myCell.invalidate();
                }
                if (!isRound && myCell != null && myCell.getSeekBarWaveform() != null) {
                    myCell.getSeekBarWaveform().setExplosionRate(openProgress);
                }
                if (after != null) {
                    after.run();
                }
            }
        });
        final long duration = !open && closeAction == null ? 330 : 520;
        openAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        openAnimator.setDuration(duration);
        openAnimator.start();

        open2Animator = ValueAnimator.ofFloat(openProgress2, open ? 1 : 0);
        open2Animator.addUpdateListener(anm -> {
            openProgress2 = (float) anm.getAnimatedValue();
            if (isRound) {
                myCell.invalidate();
            }
        });
        open2Animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                openProgress2 = open ? 1 : 0;
                if (isRound) {
                    myCell.invalidate();
                }
            }
        });
        open2Animator.setDuration((long) (1.5f * duration));
        open2Animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        open2Animator.start();
    }
}
