package org.telegram.ui.Components.voip;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Components.LayoutHelper;
import org.webrtc.RendererCommon;
import org.webrtc.TextureViewRenderer;

import java.io.File;
import java.io.FileOutputStream;

public class VoIPTextureView extends FrameLayout {

    final Path path = new Path();
    final RectF rectF = new RectF();
    final Paint xRefPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    final boolean isCamera;

    float roundRadius;

    public final TextureViewRenderer renderer;
    public final ImageView imageView;
    public View backgroundView;

    public Bitmap cameraLastBitmap;
    public float stubVisibleProgress = 1f;

    public VoIPTextureView(@NonNull Context context, boolean isCamera) {
        super(context);
        this.isCamera = isCamera;
        imageView = new ImageView(context);
        renderer = new TextureViewRenderer(context) {
            @Override
            public void onFirstFrameRendered() {
                super.onFirstFrameRendered();
                VoIPTextureView.this.invalidate();
            }

            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                super.onMeasure(widthSpec, heightSpec);
            }
        };
        renderer.setEnableHardwareScaler(true);
        renderer.setIsCamera(isCamera);
        if (!isCamera) {
            backgroundView = new View(context);
            backgroundView.setBackgroundColor(0xff1b1f23);
            addView(backgroundView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
            addView(renderer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        } else {
            addView(renderer);
        }
        addView(imageView);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setOutlineProvider(new ViewOutlineProvider() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void getOutline(View view, Outline outline) {
                    if (roundRadius < 1) {
                        outline.setRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
                    } else {
                        outline.setRoundRect(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight(), roundRadius);
                    }
                }
            });
            setClipToOutline(true);
        } else {
            xRefPaint.setColor(0xff000000);
            xRefPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }

        if (isCamera) {
            if (cameraLastBitmap == null) {
                try {
                    File file = new File(ApplicationLoader.getFilesDirFixed(), "voip_icthumb.jpg");
                    cameraLastBitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                    if (cameraLastBitmap == null) {
                        file = new File(ApplicationLoader.getFilesDirFixed(), "icthumb.jpg");
                        cameraLastBitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                    }
                    imageView.setImageBitmap(cameraLastBitmap);
                    imageView.setScaleType(ImageView.ScaleType.FIT_XY);
                } catch (Throwable ignore) {

                }
            }
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (roundRadius > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                super.dispatchDraw(canvas);
                canvas.drawPath(path, xRefPaint);
            } catch (Exception ignore) {

            }
        } else {
            super.dispatchDraw(canvas);
        }

        if (imageView.getVisibility() == View.VISIBLE && renderer.isFirstFrameRendered()) {
            stubVisibleProgress -= 16f / 150f;
            if (stubVisibleProgress <= 0) {
                stubVisibleProgress = 0;
                imageView.setVisibility(View.GONE);
            } else {
                invalidate();
                imageView.setAlpha(stubVisibleProgress);
            }
        }
    }

    public void setRoundCorners(float radius) {
        roundRadius = radius;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            invalidateOutline();
        } else {
            invalidate();
        }
    }

    public void saveCameraLastBitmap() {
        Bitmap bitmap = renderer.getBitmap(150, 150);
        if (bitmap != null && bitmap.getPixel(0, 0) != 0) {
            Utilities.blurBitmap(bitmap, 3, 1, bitmap.getWidth(), bitmap.getHeight(), bitmap.getRowBytes());
            try {
                File file = new File(ApplicationLoader.getFilesDirFixed(), "voip_icthumb.jpg");
                FileOutputStream stream = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                stream.close();
            } catch (Throwable ignore) {

            }
        }
    }

    public void setStub(VoIPTextureView from) {
        Bitmap bitmap = from.renderer.getBitmap();
        if (bitmap == null || bitmap.getPixel(0,0) == 0) {
            imageView.setImageDrawable(from.imageView.getDrawable());
        } else {
            imageView.setImageBitmap(bitmap);
        }
        stubVisibleProgress = 1f;
        imageView.setVisibility(View.VISIBLE);
        imageView.setAlpha(1f);
    }

}
