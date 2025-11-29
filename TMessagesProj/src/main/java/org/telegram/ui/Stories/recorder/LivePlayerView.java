package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.voip.VideoCapturerDevice;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Stories.PeerStoriesView;
import org.telegram.ui.Stories.PeerStoriesView.VideoPlayerSharedScope;
import org.webrtc.EglBase;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.TextureViewRenderer;
import org.webrtc.VideoSink;

import java.io.File;
import java.io.FileOutputStream;

public class LivePlayerView extends FrameLayout implements RendererCommon.RendererEvents, NotificationCenter.NotificationCenterDelegate {

    private int currentAccount;
    public final EmptyView emptyView;

    public final SurfaceViewRenderer surfaceView;
    public final TextureViewRenderer textureView;
    public final BackupImageView thumb;

    private final TextureView blurRenderer;

    public LivePlayerView(Context context, int account, boolean isSurfaceView) {
        super(context);
        this.currentAccount = account;

        thumb = new BackupImageView(context);
        thumb.setAlpha(0.75f);
        addView(thumb, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        blurRenderer = new TextureView(context);
        addView(blurRenderer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        if (isSurfaceView) {
            surfaceView = new SurfaceViewRenderer(context);
            addView(surfaceView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
            surfaceView.setAlpha(1);

            textureView = null;
        } else {
            textureView = new TextureViewRenderer(context);
            textureView.setOpaque(false);
            textureView.setEnableHardwareScaler(true);
            textureView.setIsCamera(true);
            textureView.setRotateTextureWithScreen(true);
            textureView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
            addView(textureView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
            textureView.setAlpha(1);

            surfaceView = null;
        }

        emptyView = new EmptyView(context);
        emptyView.setAlpha(0);
        emptyView.setVisibility(View.GONE);
        addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
    }

    private View placeholderView;
    public View getPlaceholderView() {
        if (placeholderView == null) {
            placeholderView = new View(getContext());
            addView(placeholderView, LayoutHelper.createFrameMatchParent());
        }

        return placeholderView;
    }

    public void setAccount(int account) {
        if (currentAccount == account) return;
        if (isAttachedToWindow()) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.liveStoryUpdated);
            NotificationCenter.getInstance(currentAccount = account).addObserver(this, NotificationCenter.liveStoryUpdated);
        } else {
            currentAccount = account;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (surfaceView != null) {
            surfaceView.init(VideoCapturerDevice.getEglBase().getEglBaseContext(), this);
            // TODO: support blur on surfaceView
        }
        if (textureView != null) {
            textureView.init(VideoCapturerDevice.getEglBase().getEglBaseContext(), this);
            textureView.setBackgroundRenderer(blurRenderer);
        }

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.liveStoryUpdated);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        firstFrameRendered = false;
        setTextureVisible(false, false);
        if (surfaceView != null) {
            surfaceView.release();
        }
        if (textureView != null) {
            textureView.release();
        }
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.liveStoryUpdated);
    }

    private Runnable firstFrameCallback;
    public void setOnFirstFrameCallback(Runnable firstFrameCallback) {
        this.firstFrameCallback = firstFrameCallback;
    }

    private boolean firstFrameRendered;
    @Override
    public void onFirstFrameRendered() {
        if (!firstFrameRendered) {
            if (scope != null && !scope.firstFrameRendered) {
                scope.firstFrameRendered = true;
                scope.invalidate();
            }
            firstFrameRendered = true;
        }
        setTextureVisible(true, true);
        if (firstFrameCallback != null) {
            firstFrameCallback.run();
            firstFrameCallback = null;
        }
    }

    public void release() {
        if (textureView != null) {
            textureView.release();
        }
        if (surfaceView != null) {
            surfaceView.release();
        }
        firstFrameRendered = false;
        setTextureVisible(false, false);
    }

    private long dialogId;
    private VideoPlayerSharedScope scope;
    public void setScope(long dialogId, VideoPlayerSharedScope scope) {
        if (scope == null && this.dialogId != 0 && firstFrameRendered && textureView != null) {
            final long thisDialogId = this.dialogId;
            final File thumbFile = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "live" + thisDialogId + ".jpg");
            final Bitmap bitmap = textureView.getBitmap();
            if (bitmap != null) {
                final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                final int w, h;
                if (bitmap.getWidth() > bitmap.getHeight()) {
                    w = dp(100); h = (int) ((float) bitmap.getHeight() / bitmap.getWidth() * dp(100));
                } else {
                    h = dp(100); w = (int) ((float) bitmap.getWidth() / bitmap.getHeight() * dp(100));
                }
                Bitmap bitmap2 = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap2);
                final float s = (float) w / bitmap.getWidth();
                canvas.scale(s, s);
                canvas.drawBitmap(bitmap, 0, 0, paint);
                Utilities.stackBlurBitmap(bitmap2, dp(4));
                try {
                    bitmap2.compress(Bitmap.CompressFormat.JPEG, 87, new FileOutputStream(thumbFile));
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
        if (this.dialogId != dialogId) {
            if (dialogId == 0) {
                thumb.clearImage();
            } else {
                final String thumbPath = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "live" + dialogId + ".jpg").getAbsolutePath();
                if (dialogId > 0) {
                    final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                    final ImageLocation imageLocation = ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL);
                    final int color = user != null ? AvatarDrawable.getColorForId(user.id) : ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.2f);
                    final GradientDrawable gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{ColorUtils.blendARGB(color, Color.BLACK, 0.2f), ColorUtils.blendARGB(color, Color.BLACK, 0.4f)});
                    thumb.getImageReceiver().setImage(ImageLocation.getForPath(thumbPath), "500_500_nocache", imageLocation, "50_50_b2", null, null, gradientDrawable, 0, null, user, 0);
                } else {
                    final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                    final ImageLocation imageLocation = ImageLocation.getForChat(chat, ImageLocation.TYPE_SMALL);
                    final int color = chat != null ? AvatarDrawable.getColorForId(chat.id) : ColorUtils.blendARGB(Color.BLACK, Color.WHITE, 0.2f);
                    final GradientDrawable gradientDrawable = new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{ColorUtils.blendARGB(color, Color.BLACK, 0.2f), ColorUtils.blendARGB(color, Color.BLACK, 0.4f)});
                    thumb.getImageReceiver().setImage(ImageLocation.getForPath(thumbPath), "500_500_nocache", imageLocation, "50_50_b2", null, null, gradientDrawable, 0, null, chat, 0);
                }
            }
        }
        this.dialogId = dialogId;
        this.scope = scope;
        if (firstFrameRendered && scope != null && !scope.firstFrameRendered) {
            scope.firstFrameRendered = true;
            scope.invalidate();
        }
        setIsEmpty(
            scope != null && scope.livePlayer != null && scope.livePlayer.isEmptyStream(),
            scope != null && scope.livePlayer != null && scope.livePlayer.canContinueEmptyStream() ?
                scope.livePlayer::continueStreaming :
                null
        );
    }

    public VideoSink getSink() {
        if (textureView != null) {
            return textureView;
        }
        if (surfaceView != null) {
            return surfaceView;
        }
        return null;
    }

    public View getTextureView() {
        if (textureView != null) {
            return textureView;
        }
        if (surfaceView != null) {
            return surfaceView;
        }
        return null;
    }

    public boolean isAvailable() {
        if (textureView != null) {
            return textureView.isAvailable();
        }
        if (surfaceView != null) {
            return true;
        }
        return false;
    }

    public Bitmap getBitmap() {
        if (textureView != null) {
            return textureView.getBitmap();
        }
        if (surfaceView != null) {
            return null;
        }
        return null;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.liveStoryUpdated) {
            final long callId = (long) args[0];
            if (scope != null && scope.livePlayer != null && scope.livePlayer.getCallId() == callId) {
                setIsEmpty(scope.livePlayer.isEmptyStream(), scope.livePlayer.canContinueEmptyStream() ? scope.livePlayer::continueStreaming : null);
            }
        }
    }

    public void reset() {
        if (surfaceView != null) {
            surfaceView.clearImage();
        }
        if (textureView != null) {
            textureView.clearImage();
        }
        firstFrameRendered = false;
        setTextureVisible(false, false);
    }

    public void setSecure(boolean secure) {
        if (surfaceView != null) {
            surfaceView.setSecure(secure);
        }
    }

    @Override
    public void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {

    }

    private boolean ignoreLayout;
    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        ignoreLayout = true;
        final Display display = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        if (textureView != null) {
            textureView.setScreenRotation(display.getRotation());
        }
        if (surfaceView != null) {
            // TODO: surfaceView.setScreenRotation(display.getRotation());
        }
        ignoreLayout = false;

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        final View view = textureView != null ? textureView : surfaceView;
        blurRenderer.getLayoutParams().width = view.getMeasuredWidth();
        blurRenderer.getLayoutParams().height = view.getMeasuredHeight();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (surfaceView != null) {
            // TODO: surfaceView.updateRotation();
        }
        if (textureView != null) {
            textureView.updateRotation();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int W = right - left;
        final int H = bottom - top;

        thumb.layout(0, 0, W, H);
        emptyView.layout(0, 0, W, H);
        if (placeholderView != null) {
            placeholderView.layout(0, 0, W, H);
        }
        blurRenderer.layout(0, 0, blurRenderer.getMeasuredWidth(), blurRenderer.getMeasuredHeight());
        final View view = textureView != null ? textureView : surfaceView;
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());

        updateTranslations();
    }

    private void updateTranslations() {
        final int W = getMeasuredWidth();
        final int H = getMeasuredHeight();

        if (!isAttachedToWindow() || W <= 0 || H <= 0) return;
        final View view = textureView != null ? textureView : surfaceView;

        int w, h;
        float scale;
        w = blurRenderer.getMeasuredWidth();
        h = blurRenderer.getMeasuredHeight();
        blurRenderer.setPivotX(0);
        blurRenderer.setPivotY(0);
        scale = Math.max(W / (float) w, H / (float) h);
        blurRenderer.setScaleX(scale);
        blurRenderer.setScaleY(scale);
        blurRenderer.setTranslationX((W - w * scale) / 2f);
        blurRenderer.setTranslationY((H - h * scale) / 2f - keyboardOffset / 2f);

        w = view.getMeasuredWidth();
        h = view.getMeasuredHeight();
        scale = Math.max((float) w / W, (float) h / H);
        view.setScaleX(scale);
        view.setScaleY(scale);
        view.setTranslationX((W - w * scale) / 2f);
        view.setTranslationY((H - h * scale) / 2f - keyboardOffset / 2f);
    }

    private float keyboardOffset;
    public void setKeyboardOffset(float offset) {
        this.keyboardOffset = offset;
        updateTranslations();
    }

    @Override
    protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
        if (AndroidUtilities.makingGlobalBlurBitmap) {
            if (child == textureView) {
                final Bitmap bitmap = textureView.getBitmap();
                if (bitmap != null) {
                    canvas.save();
                    canvas.translate(textureView.getX(), textureView.getY());
                    canvas.scale(textureView.getWidth() * textureView.getScaleX() / bitmap.getWidth(), textureView.getHeight() * textureView.getScaleY() / bitmap.getHeight());
                    canvas.drawBitmap(bitmap, 0, 0, null);
                    canvas.restore();
                }
                return true;
            }
            if (child == blurRenderer) {
                final Bitmap bitmap = blurRenderer.getBitmap();
                if (bitmap != null) {
                    canvas.save();
                    canvas.translate(blurRenderer.getX(), blurRenderer.getY());
                    canvas.scale(blurRenderer.getWidth() * blurRenderer.getScaleX() / bitmap.getWidth(), blurRenderer.getHeight() * blurRenderer.getScaleY() / bitmap.getHeight());
                    canvas.drawBitmap(bitmap, 0, 0, null);
                    canvas.restore();
                }
                return true;
            }
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (AndroidUtilities.makingGlobalBlurBitmap) {
            if (blurRenderer != null) {
                final Bitmap bitmap = blurRenderer.getBitmap();
                if (bitmap != null) {
                    canvas.save();
                    canvas.translate(blurRenderer.getX(), blurRenderer.getY());
                    canvas.scale(blurRenderer.getWidth() * blurRenderer.getScaleX() / bitmap.getWidth(), blurRenderer.getHeight() * blurRenderer.getScaleY() / bitmap.getHeight());
                    canvas.drawBitmap(bitmap, 0, 0, null);
                    canvas.restore();
                }
            }
        } else {
            super.draw(canvas);
        }
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
    }

    private boolean isEmptyViewVisible;
    public void setIsEmpty(boolean empty, Runnable continueRunnable) {
        if (isEmptyViewVisible == empty) return;
        isEmptyViewVisible = empty;
        emptyView.setVisibility(View.VISIBLE);
        emptyView.animate()
            .alpha(isEmptyViewVisible ? 1 : 0)
            .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
            .setDuration(320)
            .withEndAction(() -> {
                emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
            })
            .start();
        emptyView.buttonView.setVisibility(empty && continueRunnable != null ? View.VISIBLE : View.GONE);
        emptyView.buttonView.setOnClickListener(continueRunnable == null ? null : v -> continueRunnable.run());
    }

    private boolean textureVisible;
    public void setTextureVisible(boolean visible, boolean animated) {
        if (textureVisible == visible && animated) return;
        if (animated) {
            getTextureView().animate()
                .alpha(visible ? 1 : 0)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(320)
                .start();
        } else {
            getTextureView().animate().cancel();
            getTextureView().setAlpha(visible ? 1 : 0);
        }
    }

    private static class EmptyView extends FrameLayout {

        public final LinearLayout layout;
        public final BackupImageView imageView;
        public final TextView textView;
        public final ButtonWithCounterView buttonView;
        private boolean hasSetImage;

        public EmptyView(Context context) {
            super(context);

            layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            addView(layout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

            imageView = new BackupImageView(context);
            layout.addView(imageView, LayoutHelper.createLinear(130, 130, Gravity.CENTER_HORIZONTAL));

            textView = new TextView(context);
            textView.setTextColor(0xFFFFFFFF);
            textView.setText(LocaleController.getString(R.string.LiveStoryDisconnected));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
            textView.setTypeface(AndroidUtilities.bold());
            layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 8, 0, 0));

            buttonView = new ButtonWithCounterView(context, null);
            buttonView.setText(LocaleController.getString(R.string.LiveStoryDisconnectedContinue), false);
            layout.addView(buttonView, LayoutHelper.createLinear((int) ((buttonView.text.getWidth() + dp(24)) / AndroidUtilities.density), 38, Gravity.CENTER_HORIZONTAL, 0, 18, 0, 0));
            buttonView.setOnClickListener(v -> {

            });

            setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] { 0xFF000000, 0xFF555555 }));
        }

        @Override
        public void setVisibility(int visibility) {
            super.setVisibility(visibility);
            if (visibility == View.VISIBLE) {
                if (!hasSetImage) {
                    final RLottieDrawable drawable = new RLottieDrawable(R.raw.utyan_empty2, "utyan_empty2", dp(130), dp(130));
                    imageView.setImageDrawable(drawable);
                    hasSetImage = true;
                }
            }
        }
    }
}
