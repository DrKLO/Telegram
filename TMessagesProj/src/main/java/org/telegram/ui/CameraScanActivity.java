package org.telegram.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.drawable.ShapeDrawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MrzRecognizer;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.camera.CameraController;
import org.telegram.messenger.camera.CameraSession;
import org.telegram.messenger.camera.CameraView;
import org.telegram.messenger.camera.Size;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

import java.nio.ByteBuffer;
import java.util.ArrayList;

@TargetApi(18)
public class CameraScanActivity extends BaseFragment implements Camera.PreviewCallback {

    private TextView titleTextView;
    private TextView descriptionText;
    private CameraView cameraView;
    private HandlerThread backgroundHandlerThread = new HandlerThread("ScanCamera");
    private Handler handler;
    private TextView recognizedMrzView;
    private Paint paint = new Paint();
    private Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Path path = new Path();
    private ImageView galleryButton;
    private ImageView flashButton;
    private AnimatorSet flashAnimator;

    private CameraScanActivityDelegate delegate;
    private boolean recognized;

    private QRCodeReader qrReader;
    private BarcodeDetector visionQrReader;

    private boolean needGalleryButton;

    private int currentType;

    public static final int TYPE_MRZ = 0;
    public static final int TYPE_QR = 1;

    public interface CameraScanActivityDelegate {
        default void didFindMrzInfo(MrzRecognizer.Result result) {

        }

        default void didFindQr(String text) {

        }
    }

    public static ActionBarLayout[] showAsSheet(BaseFragment parentFragment, boolean gallery, CameraScanActivityDelegate delegate) {
        if (parentFragment == null || parentFragment.getParentActivity() == null) {
            return null;
        }
        ActionBarLayout[] actionBarLayout = new ActionBarLayout[]{new ActionBarLayout(parentFragment.getParentActivity())};
        BottomSheet bottomSheet = new BottomSheet(parentFragment.getParentActivity(), false) {
            {
                actionBarLayout[0].init(new ArrayList<>());
                CameraScanActivity fragment = new CameraScanActivity(TYPE_QR) {
                    @Override
                    public void finishFragment() {
                        dismiss();
                    }

                    @Override
                    public void removeSelfFromStack() {
                        dismiss();
                    }
                };
                fragment.needGalleryButton = gallery;
                actionBarLayout[0].addFragmentToStack(fragment);
                actionBarLayout[0].showLastFragment();
                actionBarLayout[0].setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
                fragment.setDelegate(delegate);
                containerView = actionBarLayout[0];
                setApplyBottomPadding(false);
                setApplyBottomPadding(false);
                setOnDismissListener(dialog -> fragment.onFragmentDestroy());
            }

            @Override
            protected boolean canDismissWithSwipe() {
                return false;
            }

            @Override
            public void onBackPressed() {
                if (actionBarLayout[0] == null || actionBarLayout[0].fragmentsStack.size() <= 1) {
                    super.onBackPressed();
                } else {
                    actionBarLayout[0].onBackPressed();
                }
            }

            @Override
            public void dismiss() {
                super.dismiss();
                actionBarLayout[0] = null;
            }
        };

        bottomSheet.show();
        return actionBarLayout;
    }

    public CameraScanActivity(int type) {
        super();
        CameraController.getInstance().initCamera(() -> {
            if (cameraView != null) {
                cameraView.initCamera();
            }
        });
        currentType = type;
        if (currentType == TYPE_QR) {
            qrReader = new QRCodeReader();
            visionQrReader = new BarcodeDetector.Builder(ApplicationLoader.applicationContext).setBarcodeFormats(Barcode.QR_CODE).build();
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        destroy(false, null);
        if (getParentActivity() != null) {
            getParentActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
        if (visionQrReader != null) {
            visionQrReader.release();
        }
    }

    @Override
    public boolean onFragmentCreate() {
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarWhiteSelector), false);
        actionBar.setCastShadows(false);
        if (!AndroidUtilities.isTablet() && currentType != TYPE_QR) {
            actionBar.showActionModeTop();
        }
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        paint.setColor(0x7f000000);
        cornerPaint.setColor(0xffffffff);
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(AndroidUtilities.dp(4));
        cornerPaint.setStrokeJoin(Paint.Join.ROUND);

        ViewGroup viewGroup = new ViewGroup(context) {

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                int height = MeasureSpec.getSize(heightMeasureSpec);
                actionBar.measure(widthMeasureSpec, heightMeasureSpec);
                if (currentType == TYPE_MRZ) {
                    cameraView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) (width * 0.704f), MeasureSpec.EXACTLY));
                } else {
                    cameraView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                    recognizedMrzView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                    if (galleryButton != null) {
                        galleryButton.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(60), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(60), MeasureSpec.EXACTLY));
                    }
                    flashButton.measure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(60), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(60), MeasureSpec.EXACTLY));
                }
                titleTextView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                descriptionText.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.9f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));

                setMeasuredDimension(width, height);
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                int width = r - l;
                int height = b - t;

                int y = 0;
                if (currentType == TYPE_MRZ) {
                    cameraView.layout(0, y, cameraView.getMeasuredWidth(), y + cameraView.getMeasuredHeight());
                    y = (int) (height * 0.65f);
                    titleTextView.layout(0, y, titleTextView.getMeasuredWidth(), y + titleTextView.getMeasuredHeight());
                    recognizedMrzView.setTextSize(TypedValue.COMPLEX_UNIT_PX, cameraView.getMeasuredHeight() / 22);
                    recognizedMrzView.setPadding(0, 0, 0, cameraView.getMeasuredHeight() / 15);
                } else {
                    actionBar.layout(0, 0, actionBar.getMeasuredWidth(), actionBar.getMeasuredHeight());
                    cameraView.layout(0, 0, cameraView.getMeasuredWidth(), cameraView.getMeasuredHeight());
                    int size = (int) (Math.min(cameraView.getWidth(), cameraView.getHeight()) / 1.5f);
                    y = (cameraView.getMeasuredHeight() - size) / 2 - titleTextView.getMeasuredHeight() - AndroidUtilities.dp(30);
                    titleTextView.layout(0, y, titleTextView.getMeasuredWidth(), y + titleTextView.getMeasuredHeight());
                    recognizedMrzView.layout(0, getMeasuredHeight() - recognizedMrzView.getMeasuredHeight(), getMeasuredWidth(), getMeasuredHeight());

                    int x;
                    if (needGalleryButton) {
                        x = cameraView.getMeasuredWidth() / 2 + AndroidUtilities.dp(35);
                    } else {
                        x = cameraView.getMeasuredWidth() / 2 - flashButton.getMeasuredWidth() / 2;
                    }
                    y = (cameraView.getMeasuredHeight() - size) / 2 + size + AndroidUtilities.dp(30);
                    flashButton.layout(x, y, x + flashButton.getMeasuredWidth(), y + flashButton.getMeasuredHeight());

                    if (galleryButton != null) {
                        x = cameraView.getMeasuredWidth() / 2 - AndroidUtilities.dp(35) - galleryButton.getMeasuredWidth();
                        galleryButton.layout(x, y, x + galleryButton.getMeasuredWidth(), y + galleryButton.getMeasuredHeight());
                    }
                }

                y = (int) (height * 0.74f);
                int x = (int) (width * 0.05f);
                descriptionText.layout(x, y, x + descriptionText.getMeasuredWidth(), y + descriptionText.getMeasuredHeight());
            }

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (currentType == TYPE_QR && child == cameraView) {
                    int size = (int) (Math.min(child.getWidth(), child.getHeight()) / 1.5f);
                    int x = (child.getWidth() - size) / 2;
                    int y = (child.getHeight() - size) / 2;
                    canvas.drawRect(0, 0, child.getMeasuredWidth(), y, paint);
                    canvas.drawRect(0, y + size, child.getMeasuredWidth(), child.getMeasuredHeight(), paint);
                    canvas.drawRect(0, y, x, y + size, paint);
                    canvas.drawRect(x + size, y, child.getMeasuredWidth(), y + size, paint);

                    path.reset();
                    path.moveTo(x, y + AndroidUtilities.dp(20));
                    path.lineTo(x, y);
                    path.lineTo(x + AndroidUtilities.dp(20), y);
                    canvas.drawPath(path, cornerPaint);

                    path.reset();
                    path.moveTo(x + size, y + AndroidUtilities.dp(20));
                    path.lineTo(x + size, y);
                    path.lineTo(x + size - AndroidUtilities.dp(20), y);
                    canvas.drawPath(path, cornerPaint);

                    path.reset();
                    path.moveTo(x, y + size - AndroidUtilities.dp(20));
                    path.lineTo(x, y + size);
                    path.lineTo(x + AndroidUtilities.dp(20), y + size);
                    canvas.drawPath(path, cornerPaint);

                    path.reset();
                    path.moveTo(x + size, y + size - AndroidUtilities.dp(20));
                    path.lineTo(x + size, y + size);
                    path.lineTo(x + size - AndroidUtilities.dp(20), y + size);
                    canvas.drawPath(path, cornerPaint);
                }
                return result;
            }
        };
        viewGroup.setOnTouchListener((v, event) -> true);
        fragmentView = viewGroup;

        cameraView = new CameraView(context, false);
        cameraView.setUseMaxPreview(true);
        cameraView.setOptimizeForBarcode(true);
        cameraView.setDelegate(new CameraView.CameraViewDelegate() {
            @Override
            public void onCameraCreated(Camera camera) {

            }

            @Override
            public void onCameraInit() {
                startRecognizing();
            }
        });
        viewGroup.addView(cameraView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        if (currentType == TYPE_MRZ) {
            actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        } else {
            actionBar.setBackgroundDrawable(null);
            actionBar.setAddToContainer(false);
            actionBar.setItemsColor(0xffffffff, false);
            actionBar.setItemsBackgroundColor(0x22ffffff, false);
            viewGroup.setBackgroundColor(Theme.getColor(Theme.key_wallet_blackBackground));
            viewGroup.addView(actionBar);
        }

        titleTextView = new TextView(context);
        titleTextView.setGravity(Gravity.CENTER_HORIZONTAL);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        viewGroup.addView(titleTextView);

        descriptionText = new TextView(context);
        descriptionText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText6));
        descriptionText.setGravity(Gravity.CENTER_HORIZONTAL);
        descriptionText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        viewGroup.addView(descriptionText);

        recognizedMrzView = new TextView(context);
        recognizedMrzView.setTextColor(0xffffffff);
        recognizedMrzView.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        recognizedMrzView.setAlpha(0);

        if (currentType == TYPE_MRZ) {
            titleTextView.setText(LocaleController.getString("PassportScanPassport", R.string.PassportScanPassport));
            descriptionText.setText(LocaleController.getString("PassportScanPassportInfo", R.string.PassportScanPassportInfo));
            titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            recognizedMrzView.setTypeface(Typeface.MONOSPACE);
            cameraView.addView(recognizedMrzView);
        } else {
            if (needGalleryButton) {
                //titleTextView.setText(LocaleController.getString("WalletScanCode", R.string.WalletScanCode));
            } else {
                titleTextView.setText(LocaleController.getString("AuthAnotherClientScan", R.string.AuthAnotherClientScan));
            }
            titleTextView.setTextColor(0xffffffff);
            recognizedMrzView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            recognizedMrzView.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), AndroidUtilities.dp(10));
            if (needGalleryButton) {
                //recognizedMrzView.setText(LocaleController.getString("WalletScanCodeNotFound", R.string.WalletScanCodeNotFound));
            } else {
                recognizedMrzView.setText(LocaleController.getString("AuthAnotherClientNotFound", R.string.AuthAnotherClientNotFound));
            }
            viewGroup.addView(recognizedMrzView);

            if (needGalleryButton) {
                galleryButton = new ImageView(context);
                galleryButton.setScaleType(ImageView.ScaleType.CENTER);
                galleryButton.setImageResource(R.drawable.qr_gallery);
                galleryButton.setBackgroundDrawable(Theme.createSelectorDrawableFromDrawables(Theme.createCircleDrawable(AndroidUtilities.dp(60), 0x22ffffff), Theme.createCircleDrawable(AndroidUtilities.dp(60), 0x44ffffff)));
                viewGroup.addView(galleryButton);
                galleryButton.setOnClickListener(currentImage -> {
                    if (getParentActivity() == null) {
                        return;
                    }
                    if (Build.VERSION.SDK_INT >= 23) {
                        if (getParentActivity().checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            getParentActivity().requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 4);
                            return;
                        }
                    }
                    PhotoAlbumPickerActivity fragment = new PhotoAlbumPickerActivity(PhotoAlbumPickerActivity.SELECT_TYPE_QR, false, false, null);
                    fragment.setMaxSelectedPhotos(1, false);
                    fragment.setAllowSearchImages(false);
                    fragment.setDelegate(new PhotoAlbumPickerActivity.PhotoAlbumPickerActivityDelegate() {
                        @Override
                        public void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos, boolean notify, int scheduleDate) {
                            try {
                                if (!photos.isEmpty()) {
                                    SendMessagesHelper.SendingMediaInfo info = photos.get(0);
                                    if (info.path != null) {
                                        Point screenSize = AndroidUtilities.getRealScreenSize();
                                        Bitmap bitmap = ImageLoader.loadBitmap(info.path, null, screenSize.x, screenSize.y, true);
                                        String text = tryReadQr(null, null, 0, 0, 0, bitmap);
                                        if (text != null) {
                                            if (delegate != null) {
                                                delegate.didFindQr(text);
                                            }
                                            removeSelfFromStack();
                                        }
                                    }
                                }
                            } catch (Throwable e) {
                                FileLog.e(e);
                            }
                        }

                        @Override
                        public void startPhotoSelectActivity() {
                            try {
                                Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                                photoPickerIntent.setType("image/*");
                                getParentActivity().startActivityForResult(photoPickerIntent, 11);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    });
                    presentFragment(fragment);
                });
            }

            flashButton = new ImageView(context);
            flashButton.setScaleType(ImageView.ScaleType.CENTER);
            flashButton.setImageResource(R.drawable.qr_flashlight);
            flashButton.setBackgroundDrawable(Theme.createCircleDrawable(AndroidUtilities.dp(60), 0x22ffffff));
            viewGroup.addView(flashButton);
            flashButton.setOnClickListener(currentImage -> {
                CameraSession session = cameraView.getCameraSession();
                if (session != null) {
                    ShapeDrawable shapeDrawable = (ShapeDrawable) flashButton.getBackground();
                    if (flashAnimator != null) {
                        flashAnimator.cancel();
                        flashAnimator = null;
                    }
                    flashAnimator = new AnimatorSet();
                    ObjectAnimator animator = ObjectAnimator.ofInt(shapeDrawable, AnimationProperties.SHAPE_DRAWABLE_ALPHA, flashButton.getTag() == null ? 0x44 : 0x22);
                    animator.addUpdateListener(animation -> flashButton.invalidate());
                    flashAnimator.playTogether(animator);
                    flashAnimator.setDuration(200);
                    flashAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                    flashAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            flashAnimator = null;
                        }
                    });
                    flashAnimator.start();
                    if (flashButton.getTag() == null) {
                        flashButton.setTag(1);
                        session.setTorchEnabled(true);
                    } else {
                        flashButton.setTag(null);
                        session.setTorchEnabled(false);
                    }
                }
            });
        }

        if (getParentActivity() != null) {
            getParentActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        fragmentView.setKeepScreenOn(true);

        return fragmentView;
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 11) {
                if (data == null || data.getData() == null) {
                    return;
                }
                try {
                    Point screenSize = AndroidUtilities.getRealScreenSize();
                    Bitmap bitmap = ImageLoader.loadBitmap(null, data.getData(), screenSize.x, screenSize.y, true);
                    String text = tryReadQr(null, null, 0, 0, 0, bitmap);
                    if (text != null) {
                        if (delegate != null) {
                            delegate.didFindQr(text);
                        }
                        finishFragment();
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        }
    }

    public void setDelegate(CameraScanActivityDelegate cameraScanActivityDelegate) {
        delegate = cameraScanActivityDelegate;
    }

    public void destroy(boolean async, final Runnable beforeDestroyRunnable) {
        if (cameraView != null) {
            cameraView.destroy(async, beforeDestroyRunnable);
            cameraView = null;
        }
        backgroundHandlerThread.quitSafely();
    }

    private void startRecognizing() {
        backgroundHandlerThread.start();
        handler = new Handler(backgroundHandlerThread.getLooper());
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (cameraView != null && !recognized && cameraView.getCameraSession() != null) {
                    cameraView.getCameraSession().setOneShotPreviewCallback(org.telegram.ui.CameraScanActivity.this);
                    AndroidUtilities.runOnUIThread(this, 500);
                }
            }
        });
    }

    private void onNoQrFound() {
        AndroidUtilities.runOnUIThread(() -> {
            if (recognizedMrzView.getTag() != null) {
                recognizedMrzView.setTag(null);
                recognizedMrzView.animate().setDuration(200).alpha(0.0f).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            }
        });
    }

    @Override
    public void onPreviewFrame(final byte[] data, final Camera camera) {
        handler.post(() -> {
            try {
                Size size = cameraView.getPreviewSize();
                if (currentType == TYPE_MRZ) {
                    final MrzRecognizer.Result res = MrzRecognizer.recognize(data, size.getWidth(), size.getHeight(), cameraView.getCameraSession().getDisplayOrientation());
                    if (res != null && !TextUtils.isEmpty(res.firstName) && !TextUtils.isEmpty(res.lastName) && !TextUtils.isEmpty(res.number) && res.birthDay != 0 && (res.expiryDay != 0 || res.doesNotExpire) && res.gender != MrzRecognizer.Result.GENDER_UNKNOWN) {
                        recognized = true;
                        camera.stopPreview();
                        AndroidUtilities.runOnUIThread(() -> {
                            recognizedMrzView.setText(res.rawMRZ);
                            recognizedMrzView.animate().setDuration(200).alpha(1f).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                            if (delegate != null) {
                                delegate.didFindMrzInfo(res);
                            }
                            AndroidUtilities.runOnUIThread(this::finishFragment, 1200);
                        });
                    }
                } else {
                    int format = camera.getParameters().getPreviewFormat();
                    int side = (int) (Math.min(size.getWidth(), size.getHeight()) / 1.5f);
                    int x = (size.getWidth() - side) / 2;
                    int y = (size.getHeight() - side) / 2;

                    String text = tryReadQr(data, size, x, y, side, null);
                    if (text != null) {
                        recognized = true;
                        camera.stopPreview();
                        AndroidUtilities.runOnUIThread(() -> {
                            if (delegate != null) {
                                delegate.didFindQr(text);
                            }
                            finishFragment();
                        });
                    }
                }
            } catch (Throwable ignore) {
                onNoQrFound();
            }
        });
    }

    private String tryReadQr(byte[] data, Size size, int x, int y, int side, Bitmap bitmap) {
        try {
            String text;
            if (visionQrReader.isOperational()) {
                Frame frame;
                if (bitmap != null) {
                    frame = new Frame.Builder().setBitmap(bitmap).build();
                } else {
                    frame = new Frame.Builder().setImageData(ByteBuffer.wrap(data), size.getWidth(), size.getHeight(), ImageFormat.NV21).build();
                }
                SparseArray<Barcode> codes = visionQrReader.detect(frame);
                if (codes != null && codes.size() > 0) {
                    text = codes.valueAt(0).rawValue;
                } else {
                    text = null;
                }
            } else {
                LuminanceSource source;
                if (bitmap != null) {
                    int[] intArray = new int[bitmap.getWidth() * bitmap.getHeight()];
                    bitmap.getPixels(intArray, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
                    source = new RGBLuminanceSource(bitmap.getWidth(), bitmap.getHeight(), intArray);
                } else {
                    source = new PlanarYUVLuminanceSource(data, size.getWidth(), size.getHeight(), x, y, side, side, false);
                }

                Result result = qrReader.decode(new BinaryBitmap(new GlobalHistogramBinarizer(source)));
                if (result == null) {
                    onNoQrFound();
                    return null;
                }
                text = result.getText();
            }
            if (TextUtils.isEmpty(text)) {
                onNoQrFound();
                return null;
            }
            if (needGalleryButton) {
                if (!text.startsWith("ton://transfer/")) {
                    //onNoWalletFound(bitmap != null);
                    return null;
                }
                Uri uri = Uri.parse(text);
                String path = uri.getPath().replace("/", "");
            } else {
                if (!text.startsWith("tg://login?token=")) {
                    onNoQrFound();
                    return null;
                }
            }
            return text;
        } catch (Throwable ignore) {
            onNoQrFound();
        }
        return null;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        if (currentType == TYPE_QR) {
            return themeDescriptions;
        }

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarWhiteSelector));

        themeDescriptions.add(new ThemeDescription(titleTextView, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(descriptionText, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteGrayText6));

        return themeDescriptions;
    }
}
