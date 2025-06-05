package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ShapeDrawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.dynamicanimation.animation.FloatValueHolder;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
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
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.camera.CameraController;
import org.telegram.messenger.camera.CameraSessionWrapper;
import org.telegram.messenger.camera.CameraView;
import org.telegram.messenger.camera.Size;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.URLSpanNoUnderline;

import java.nio.ByteBuffer;
import java.util.ArrayList;

@TargetApi(18)
public class CameraScanActivity extends BaseFragment {

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
    private float backShadowAlpha = .5f;
    protected boolean shownAsBottomSheet = false;

    private SpringAnimation qrAppearing = null;
    private float qrAppearingValue = 0;

    private final PointF[] fromPoints = new PointF[4];
    private final PointF[] points = new PointF[4];
    private final PointF[] tmpPoints = new PointF[4];
    private final PointF[] tmp2Points = new PointF[4];
    {
        for (int i = 0; i < 4; ++i) {
            fromPoints[i] = new PointF(-1, -1);
            points[i] = new PointF(-1, -1);
            tmpPoints[i] = new PointF(-1, -1);
            tmp2Points[i] = new PointF(-1, -1);
        }
    }

    private final RectF fromBounds = new RectF();
    private final RectF bounds = new RectF();
    private long lastBoundsUpdate = 0;
    private final long boundsUpdateDuration = 75;

    private CameraScanActivityDelegate delegate;
    private boolean recognized;
    private long recognizedStart;
    private int recognizeFailed = 0;
    private int recognizeIndex = 0;
    private String recognizedText;

    private int sps; // samples per second (when already recognized)

    private boolean qrLoading = false;
    private boolean qrLoaded = false;

    private QRCodeReader qrReader = null;
    private BarcodeDetector visionQrReader = null;

    private boolean needGalleryButton;

    private int currentType;

    public static final int TYPE_MRZ = 0;
    public static final int TYPE_QR = 1;
    public static final int TYPE_QR_LOGIN = 2;
    public static final int TYPE_QR_WEB_BOT = 3;

    public interface CameraScanActivityDelegate {
        default void didFindMrzInfo(MrzRecognizer.Result result) {

        }

        default void didFindQr(String text) {

        }

        default boolean processQr(String text, Runnable onLoadEnd) {
            return false;
        }

        default String getSubtitleText() {
            return null;
        }

        default void onDismiss() {}
    }

    public static BottomSheet showAsSheet(BaseFragment parentFragment, boolean gallery, int type, CameraScanActivityDelegate cameraDelegate) {
        return showAsSheet(parentFragment.getParentActivity(), gallery, type, cameraDelegate);
    }

    public static BottomSheet showAsSheet(Activity parentActivity, boolean gallery, int type, CameraScanActivityDelegate cameraDelegate) {
        if (parentActivity == null) {
            return null;
        }
        INavigationLayout[] actionBarLayout = new INavigationLayout[]{INavigationLayout.newLayout(parentActivity, false)};
        BottomSheet bottomSheet = new BottomSheet(parentActivity, false) {
            CameraScanActivity fragment;
            {
                actionBarLayout[0].setFragmentStack(new ArrayList<>());
                fragment = new CameraScanActivity(type) {
                    @Override
                    public void finishFragment() {
                        setFinishing(true);
                        dismiss();
                    }

                    @Override
                    public void removeSelfFromStack() {
                        dismiss();
                    }
                };
                fragment.shownAsBottomSheet = true;
                fragment.needGalleryButton = gallery;
                actionBarLayout[0].addFragmentToStack(fragment);
                actionBarLayout[0].showLastFragment();
                actionBarLayout[0].getView().setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
                fragment.setDelegate(cameraDelegate);
                if (cameraDelegate.getSubtitleText() != null) {
                    fragment.descriptionText.setText(cameraDelegate.getSubtitleText());
                }
                containerView = actionBarLayout[0].getView();
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
                if (actionBarLayout[0] == null || actionBarLayout[0].getFragmentStack().size() <= 1) {
                    super.onBackPressed();
                } else {
                    actionBarLayout[0].onBackPressed();
                }
            }

            @Override
            public void dismiss() {
                super.dismiss();
                actionBarLayout[0] = null;
                cameraDelegate.onDismiss();
            }
        };
        bottomSheet.setUseLightStatusBar(false);
        AndroidUtilities.setLightNavigationBar(bottomSheet.getWindow(), false);
        AndroidUtilities.setNavigationBarColor(bottomSheet.getWindow(), 0xff000000, false);
        bottomSheet.setUseLightStatusBar(false);
        bottomSheet.getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        bottomSheet.show();
        return bottomSheet;
    }

    public CameraScanActivity(int type) {
        super();
        currentType = type;
        if (isQr()) {
            Utilities.globalQueue.postRunnable(() -> {
                qrReader = new QRCodeReader();
                visionQrReader = new BarcodeDetector.Builder(ApplicationLoader.applicationContext).setBarcodeFormats(Barcode.QR_CODE).build();
            });
        }

        switch (SharedConfig.getDevicePerformanceClass()) {
            case SharedConfig.PERFORMANCE_CLASS_LOW:
                sps = 8;
                break;
            case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                sps = 24;
                break;
            case SharedConfig.PERFORMANCE_CLASS_HIGH:
            default:
                sps = 40;
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
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        if (shownAsBottomSheet) {
            actionBar.setItemsColor(0xffffffff, false);
            actionBar.setItemsBackgroundColor(0xffffffff, false);
            actionBar.setTitleColor(0xffffffff);
        } else {
            actionBar.setItemsColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), false);
            actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarWhiteSelector), false);
            actionBar.setTitleColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
        }
        actionBar.setCastShadows(false);
        if (!AndroidUtilities.isTablet() && !isQr()) {
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
        cornerPaint.setStyle(Paint.Style.FILL);

        ViewGroup viewGroup = new ViewGroup(context) {

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int width = MeasureSpec.getSize(widthMeasureSpec);
                int height = MeasureSpec.getSize(heightMeasureSpec);
                actionBar.measure(widthMeasureSpec, heightMeasureSpec);
                if (currentType == TYPE_MRZ) {
                    if (cameraView != null) {
                        cameraView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((int) (width * 0.704f), MeasureSpec.EXACTLY));
                    }
                } else {
                    if (cameraView != null) {
                        cameraView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
                    }
                    recognizedMrzView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                    if (galleryButton != null) {
                        galleryButton.measure(MeasureSpec.makeMeasureSpec(dp(60), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(60), MeasureSpec.EXACTLY));
                    }
                    flashButton.measure(MeasureSpec.makeMeasureSpec(dp(60), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(60), MeasureSpec.EXACTLY));
                }
                titleTextView.measure(MeasureSpec.makeMeasureSpec(width - dp(72), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                if (currentType == TYPE_QR_WEB_BOT) {
                    descriptionText.measure(MeasureSpec.makeMeasureSpec(width - dp(72), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                } else {
                    descriptionText.measure(MeasureSpec.makeMeasureSpec((int) (width * 0.9f), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.UNSPECIFIED));
                }

                setMeasuredDimension(width, height);
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                int width = r - l;
                int height = b - t;

                int y = 0;
                if (currentType == TYPE_MRZ) {
                    if (cameraView != null) {
                        cameraView.layout(0, y, cameraView.getMeasuredWidth(), y + cameraView.getMeasuredHeight());
                    }
                    recognizedMrzView.setTextSize(TypedValue.COMPLEX_UNIT_PX, height / 22);
                    recognizedMrzView.setPadding(0, 0, 0, height / 15);
                    y = (int) (height * 0.65f);
                    titleTextView.layout(dp(36), y, dp(36) + titleTextView.getMeasuredWidth(), y + titleTextView.getMeasuredHeight());
                } else {
                    actionBar.layout(0, 0, actionBar.getMeasuredWidth(), actionBar.getMeasuredHeight());
                    if (cameraView != null) {
                        cameraView.layout(0, 0, cameraView.getMeasuredWidth(), cameraView.getMeasuredHeight());
                    }
                    int size = (int) (Math.min(width, height) / 1.5f);
                    if (currentType == TYPE_QR) {
                        y = (height - size) / 2 - titleTextView.getMeasuredHeight() - dp(30);
                    } else {
                        y = (height - size) / 2 - titleTextView.getMeasuredHeight() - dp(64);
                    }
                    titleTextView.layout(dp(36), y, dp(36) + titleTextView.getMeasuredWidth(), y + titleTextView.getMeasuredHeight());
                    if (currentType == TYPE_QR_WEB_BOT) {
                        y += titleTextView.getMeasuredHeight() + dp(8);
                        descriptionText.layout(dp(36), y, dp(36) + descriptionText.getMeasuredWidth(), y + descriptionText.getMeasuredHeight());
                    }
                    recognizedMrzView.layout(0, getMeasuredHeight() - recognizedMrzView.getMeasuredHeight(), getMeasuredWidth(), getMeasuredHeight());

                    int x;
                    if (needGalleryButton) {
                        x = width / 2 + dp(35);
                    } else {
                        x = width / 2 - flashButton.getMeasuredWidth() / 2;
                    }
                    y = (height - size) / 2 + size + dp(80);
                    flashButton.layout(x, y, x + flashButton.getMeasuredWidth(), y + flashButton.getMeasuredHeight());

                    if (galleryButton != null) {
                        x = width / 2 - dp(35) - galleryButton.getMeasuredWidth();
                        galleryButton.layout(x, y, x + galleryButton.getMeasuredWidth(), y + galleryButton.getMeasuredHeight());
                    }
                }

                if (currentType != TYPE_QR_WEB_BOT) {
                    y = (int) (height * 0.74f);
                    int x = (int) (width * 0.05f);
                    descriptionText.layout(x, y, x + descriptionText.getMeasuredWidth(), y + descriptionText.getMeasuredHeight());
                }

                updateNormalBounds();
            }

            Path path = new Path();

            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (isQr() && child == cameraView) {
                    RectF bounds = getBounds();
                    int sizex = (int) (child.getWidth() * bounds.width()),
                        sizey = (int) (child.getHeight() * bounds.height()),
                        cx = (int) (child.getWidth() * bounds.centerX()),
                        cy = (int) (child.getHeight() * bounds.centerY());

//                    PointF[] points = getPoints();
//                    path.rewind();
//                    for (int i = 0; i < points.length; ++i) {
//                        float x = child.getWidth() * points[i].x;
//                        float y = child.getHeight() * points[i].y;
//                        if (i == 0) path.moveTo(x, y);
//                        else path.lineTo(x, y);
//                    }
//                    Theme.DEBUG_RED.setAlpha(40);
//                    canvas.drawPath(path, Theme.DEBUG_RED);

                    sizex *= (.5f + qrAppearingValue * .5f);
                    sizey *= (.5f + qrAppearingValue * .5f);
                    int x = cx - sizex / 2,
                        y = cy - sizey / 2;

                    paint.setAlpha((int) (255 * (1f - (1f - backShadowAlpha) * Math.min(1, qrAppearingValue))));
                    canvas.drawRect(0, 0, child.getMeasuredWidth(), y, paint);
                    canvas.drawRect(0, y + sizey, child.getMeasuredWidth(), child.getMeasuredHeight(), paint);
                    canvas.drawRect(0, y, x, y + sizey, paint);
                    canvas.drawRect(x + sizex, y, child.getMeasuredWidth(), y + sizey, paint);
                    paint.setAlpha((int) (255 * Math.max(0, 1f - qrAppearingValue)));
                    canvas.drawRect(x, y, x + sizex, y + sizey, paint);

                    final int lineWidth = AndroidUtilities.lerp(0, dp(4), Math.min(1, qrAppearingValue * 20f)),
                              halfLineWidth = lineWidth / 2;
                    final int lineLength = AndroidUtilities.lerp(Math.min(sizex, sizey), dp(20), Math.min(1.2f, (float) Math.pow(qrAppearingValue, 1.8f)));

                    cornerPaint.setAlpha((int) (255 * Math.min(1, qrAppearingValue)));

                    path.reset();
                    path.arcTo(aroundPoint(x, y + lineLength, halfLineWidth), 0, 180);
                    path.arcTo(aroundPoint((int) (x + lineWidth * 1.5f), (int) (y + lineWidth * 1.5f), lineWidth * 2), 180, 90);
                    path.arcTo(aroundPoint(x + lineLength, y, halfLineWidth), 270, 180);
                    path.lineTo(x + halfLineWidth, y + halfLineWidth);
                    path.arcTo(aroundPoint((int) (x + lineWidth * 1.5f), (int) (y + lineWidth * 1.5f), lineWidth), 270, -90);
                    path.close();
                    canvas.drawPath(path, cornerPaint);

                    path.reset();
                    path.arcTo(aroundPoint(x + sizex, y + lineLength, halfLineWidth), 180, -180);
                    path.arcTo(aroundPoint((int) (x + sizex - lineWidth * 1.5f), (int) (y + lineWidth * 1.5f), lineWidth * 2), 0, -90);
                    path.arcTo(aroundPoint(x + sizex- lineLength, y, halfLineWidth), 270, -180);
                    path.arcTo(aroundPoint((int) (x + sizex - lineWidth * 1.5f), (int) (y + lineWidth * 1.5f), lineWidth), 270, 90);
                    path.close();
                    canvas.drawPath(path, cornerPaint);

                    path.reset();
                    path.arcTo(aroundPoint(x, y + sizey - lineLength, halfLineWidth), 0, -180);
                    path.arcTo(aroundPoint((int) (x + lineWidth * 1.5f), (int) (y + sizey - lineWidth * 1.5f), lineWidth * 2), 180, -90);
                    path.arcTo(aroundPoint(x + lineLength, y + sizey, halfLineWidth), 90, -180);
                    path.arcTo(aroundPoint((int) (x + lineWidth * 1.5f), (int) (y + sizey - lineWidth * 1.5f), lineWidth), 90, 90);
                    path.close();
                    canvas.drawPath(path, cornerPaint);

                    path.reset();
                    path.arcTo(aroundPoint(x + sizex, y + sizey - lineLength, halfLineWidth), 180, 180);
                    path.arcTo(aroundPoint((int) (x + sizex - lineWidth * 1.5f), (int) (y + sizey - lineWidth * 1.5f), lineWidth * 2), 0, 90);
                    path.arcTo(aroundPoint(x + sizex - lineLength, y + sizey, halfLineWidth), 90, 180);
                    path.arcTo(aroundPoint((int) (x + sizex - lineWidth * 1.5f), (int) (y + sizey - lineWidth * 1.5f), lineWidth), 90, -90);
                    path.close();

                    canvas.drawPath(path, cornerPaint);
                }
                return result;
            }

            private RectF aroundPoint(int x, int y, int r) {
                AndroidUtilities.rectTmp.set(x - r, y - r, x + r, y + r);
                return AndroidUtilities.rectTmp;
            }
        };
        viewGroup.setOnTouchListener((v, event) -> true);
        fragmentView = viewGroup;

        if (isQr()) {
            fragmentView.postDelayed(this::initCameraView, 450);
        } else {
            initCameraView();
        }

        if (currentType == TYPE_MRZ) {
            actionBar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        } else {
            actionBar.setBackgroundDrawable(null);
            actionBar.setAddToContainer(false);
            actionBar.setTitleColor(0xffffffff);
            actionBar.setItemsColor(0xffffffff, false);
            actionBar.setItemsBackgroundColor(0x22ffffff, false);
            viewGroup.setBackgroundColor(0xFF000000);
            viewGroup.addView(actionBar);
        }

        if (currentType == TYPE_QR_LOGIN || currentType == TYPE_QR_WEB_BOT) {
            actionBar.setTitle(LocaleController.getString(R.string.AuthAnotherClientScan));
        }

        Paint selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectionPaint.setPathEffect(LinkPath.getRoundedEffect());
        selectionPaint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, 40));
        titleTextView = new TextView(context) {
            LinkPath textPath;
            private LinkSpanDrawable<URLSpanNoUnderline> pressedLink;
            LinkSpanDrawable.LinkCollector links = new LinkSpanDrawable.LinkCollector(this);

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                if (getText() instanceof Spanned) {
                    Spanned spanned = (Spanned) getText();
                    URLSpanNoUnderline[] innerSpans = spanned.getSpans(0, spanned.length(), URLSpanNoUnderline.class);
                    if (innerSpans != null && innerSpans.length > 0) {
                        textPath = new LinkPath(true);
                        textPath.setAllowReset(false);
                        for (int a = 0; a < innerSpans.length; a++) {
                            int start = spanned.getSpanStart(innerSpans[a]);
                            int end = spanned.getSpanEnd(innerSpans[a]);
                            textPath.setCurrentLayout(getLayout(), start, 0);
                            int shift = getText() != null ? getPaint().baselineShift : 0;
                            textPath.setBaselineShift(shift != 0 ? shift + dp(shift > 0 ? 5 : -2) : 0);
                            getLayout().getSelectionPath(start, end, textPath);
                        }
                        textPath.setAllowReset(true);
                    }
                }
            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                final Layout textLayout = getLayout();
                int textX = 0, textY = 0;
                int x = (int) (e.getX() - textX);
                int y = (int) (e.getY() - textY);
                if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_UP) {
                    final int line = textLayout.getLineForVertical(y);
                    final int off = textLayout.getOffsetForHorizontal(line, x);

                    final float left = textLayout.getLineLeft(line);
                    if (left <= x && left + textLayout.getLineWidth(line) >= x && y >= 0 && y <= textLayout.getHeight()) {
                        Spannable buffer = (Spannable) textLayout.getText();
                        ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);
                        if (link.length != 0) {
                            links.clear();
                            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                                pressedLink = new LinkSpanDrawable(link[0], null, e.getX(), e.getY());
                                pressedLink.setColor(0x2dffffff);
                                links.addLink(pressedLink);
                                int start = buffer.getSpanStart(pressedLink.getSpan());
                                int end = buffer.getSpanEnd(pressedLink.getSpan());
                                LinkPath path = pressedLink.obtainNewPath();
                                path.setCurrentLayout(textLayout, start, textY);
                                textLayout.getSelectionPath(start, end, path);
                            } else if (e.getAction() == MotionEvent.ACTION_UP) {
                                if (pressedLink != null && pressedLink.getSpan() == link[0]) {
                                    link[0].onClick(this);
                                }
                                pressedLink = null;
                            }
                            return true;
                        }
                    }
                }
                if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                    links.clear();
                    pressedLink = null;
                }
                return super.onTouchEvent(e);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                if (textPath != null) {
                    canvas.drawPath(textPath, selectionPaint);
                }
                if (links.draw(canvas)) {
                    invalidate();
                }
                super.onDraw(canvas);
            }
        };
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
            titleTextView.setText(LocaleController.getString(R.string.PassportScanPassport));
            descriptionText.setText(LocaleController.getString(R.string.PassportScanPassportInfo));
            titleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            recognizedMrzView.setTypeface(Typeface.MONOSPACE);
        } else {
            if (needGalleryButton) {
                //titleTextView.setText(LocaleController.getString(R.string.WalletScanCode));
            } else {
                if (currentType == TYPE_QR || currentType == TYPE_QR_WEB_BOT) {
                    titleTextView.setText(LocaleController.getString(R.string.AuthAnotherClientScan));
                } else {
                    String text = LocaleController.getString(R.string.AuthAnotherClientInfo5);
                    SpannableStringBuilder spanned = new SpannableStringBuilder(text);

                    String[] links = new String[] {
                        LocaleController.getString(R.string.AuthAnotherClientDownloadClientUrl),
                        LocaleController.getString(R.string.AuthAnotherWebClientUrl)
                    };
                    for (int i = 0; i < links.length; ++i) {
                        text = spanned.toString();
                        int index1 = text.indexOf('*');
                        int index2 = text.indexOf('*', index1 + 1);

                        if (index1 != -1 && index2 != -1 && index1 != index2) {
                            titleTextView.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
                            spanned.replace(index2, index2 + 1, " ");
                            spanned.replace(index1, index1 + 1, " ");
                            index1 += 1;
                            index2 += 1;
                            spanned.setSpan(new URLSpanNoUnderline(links[i], true), index1, index2 - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            spanned.setSpan(new TypefaceSpan(AndroidUtilities.bold()), index1, index2 - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } else {
                            break;
                        }
                    }

                    titleTextView.setLinkTextColor(0xffffffff);

                    titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    titleTextView.setLineSpacing(dp(2), 1.0f);
                    titleTextView.setPadding(0, 0, 0, 0);
                    titleTextView.setText(spanned);
                }
            }
            titleTextView.setTextColor(0xffffffff);
            if (currentType == TYPE_QR_WEB_BOT) {
                descriptionText.setTextColor(0x99ffffff);
            }
            recognizedMrzView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            recognizedMrzView.setPadding(dp(10), 0, dp(10), dp(10));
            if (needGalleryButton) {
                //recognizedMrzView.setText(LocaleController.getString(R.string.WalletScanCodeNotFound));
            } else {
                recognizedMrzView.setText(LocaleController.getString(R.string.AuthAnotherClientNotFound));
            }
            viewGroup.addView(recognizedMrzView);

            if (needGalleryButton) {
                galleryButton = new ImageView(context);
                galleryButton.setScaleType(ImageView.ScaleType.CENTER);
                galleryButton.setImageResource(R.drawable.qr_gallery);
                galleryButton.setBackgroundDrawable(Theme.createSelectorDrawableFromDrawables(Theme.createCircleDrawable(dp(60), 0x22ffffff), Theme.createCircleDrawable(dp(60), 0x44ffffff)));
                viewGroup.addView(galleryButton);
                galleryButton.setOnClickListener(currentImage -> {
                    if (getParentActivity() == null) {
                        return;
                    }
                    final Activity activity = getParentActivity();
                    if (Build.VERSION.SDK_INT >= 33) {
                        if (activity.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                            activity.requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO}, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE);
                            return;
                        }
                    } else if (Build.VERSION.SDK_INT >= 23) {
                        if (activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                            activity.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE);
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
                                        QrResult res = tryReadQr(null, null, 0, 0, 0, bitmap);
                                        if (res != null) {
                                            if (delegate != null) {
                                                delegate.didFindQr(res.text);
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
            flashButton.setBackgroundDrawable(Theme.createCircleDrawable(dp(60), 0x22ffffff));
            viewGroup.addView(flashButton);
            flashButton.setOnClickListener(currentImage -> {
                if (cameraView == null) {
                    return;
                }
                CameraSessionWrapper session = cameraView.getCameraSession();
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
                        session.setCurrentFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    } else {
                        flashButton.setTag(null);
                        session.setCurrentFlashMode(Camera.Parameters.FLASH_MODE_OFF);
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

    private ValueAnimator recognizedAnimator;
    private float recognizedT = 0;
    private float newRecognizedT = 0;
    private SpringAnimation useRecognizedBoundsAnimator;
    private float useRecognizedBounds = 0;
    private void updateRecognized() {
        float wasNewRecognizedT = recognizedT;
        newRecognizedT = recognized ? 1f : 0f;
        if (wasNewRecognizedT != newRecognizedT) {
            if (recognizedAnimator != null) {
                recognizedAnimator.cancel();
            }
        } else {
            return;
        }

        recognizedAnimator = ValueAnimator.ofFloat(recognizedT, newRecognizedT);
        recognizedAnimator.addUpdateListener(a -> {
            recognizedT = (float) a.getAnimatedValue();
            titleTextView.setAlpha(1f - recognizedT);
            if (currentType == TYPE_QR_WEB_BOT) {
                descriptionText.setAlpha(1f - recognizedT);
            }
            flashButton.setAlpha(1f - recognizedT);
            backShadowAlpha = .5f + recognizedT * .25f;
            fragmentView.invalidate();
        });
        recognizedAnimator.setDuration((long) (300 * Math.abs(recognizedT - newRecognizedT)));
        recognizedAnimator.setInterpolator(CubicBezierInterpolator.DEFAULT);
        recognizedAnimator.start();

        if (useRecognizedBoundsAnimator != null) {
            useRecognizedBoundsAnimator.cancel();
        }
        final float force = 500f;
        useRecognizedBoundsAnimator = new SpringAnimation(new FloatValueHolder((recognized ? useRecognizedBounds : 1f - useRecognizedBounds) * force));
        useRecognizedBoundsAnimator.addUpdateListener((animation, value, velocity) -> {
            useRecognizedBounds = recognized ? value / force : (1f - value / force);
            fragmentView.invalidate();
        });
        useRecognizedBoundsAnimator.setSpring(new SpringForce(force));
        useRecognizedBoundsAnimator.getSpring().setDampingRatio(1f);
        useRecognizedBoundsAnimator.getSpring().setStiffness(500.0f);
        useRecognizedBoundsAnimator.start();
    }

    private void initCameraView() {
        if (fragmentView == null) {
            return;
        }
        CameraController.getInstance().initCamera(null);
        cameraView = new CameraView(fragmentView.getContext(), false);
        cameraView.setUseMaxPreview(true);
        cameraView.setOptimizeForBarcode(true);
        cameraView.setDelegate(() -> {
            startRecognizing();
            if (isQr()) {
                if (qrAppearing != null) {
                    qrAppearing.cancel();
                    qrAppearing = null;
                }

                qrAppearing = new SpringAnimation(new FloatValueHolder(0));
                qrAppearing.addUpdateListener((animation, value, velocity) -> {
                    qrAppearingValue = value / 500f;
                    fragmentView.invalidate();
                });
                qrAppearing.addEndListener((animation, canceled, value, velocity) -> {
                    if (qrAppearing != null) {
                        qrAppearing.cancel();
                        qrAppearing = null;
                    }
                });
                qrAppearing.setSpring(new SpringForce(500f));
                qrAppearing.getSpring().setDampingRatio(0.8f);
                qrAppearing.getSpring().setStiffness(250.0f);
                qrAppearing.start();
            }
        });
        ((ViewGroup) fragmentView).addView(cameraView, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        if (currentType == TYPE_MRZ && recognizedMrzView != null) {
            cameraView.addView(recognizedMrzView);
        }
    }

    private void setPointsFromBounds(RectF bounds, PointF[] points) {
        points[0].set(bounds.left, bounds.top);
        points[1].set(bounds.right, bounds.top);
        points[2].set(bounds.right, bounds.bottom);
        points[3].set(bounds.left, bounds.bottom);
    }

    private void updateRecognizedBounds(RectF newBounds, PointF[] newPoints) {
        final long now = SystemClock.elapsedRealtime();
        if (lastBoundsUpdate == 0) {
            // first update = set
            lastBoundsUpdate = now - boundsUpdateDuration;
            bounds.set(newBounds);
            fromBounds.set(newBounds);
            if (newPoints == null) {
                setPointsFromBounds(newBounds, fromPoints);
                setPointsFromBounds(newBounds, points);
            } else {
                for (int i = 0; i < 4; i++) {
                    fromPoints[i].set(newPoints[i].x, newPoints[i].y);
                    points[i].set(newPoints[i].x, newPoints[i].y);
                }
            }
        } else {
            // next updates = interpolate
            if (fromBounds != null && now - lastBoundsUpdate < boundsUpdateDuration) {
                float t = (now - lastBoundsUpdate) / (float) boundsUpdateDuration;
                t = Math.min(1, Math.max(0, t));
                AndroidUtilities.lerp(fromBounds, bounds, t, fromBounds);

                for (int i = 0; i < 4; ++i) {
                    fromPoints[i].set(
                        AndroidUtilities.lerp(fromPoints[i].x, points[i].x, t),
                        AndroidUtilities.lerp(fromPoints[i].y, points[i].y, t)
                    );
                }
            } else {
                fromBounds.set(bounds);
                for (int i = 0; i < 4; ++i) {
                    fromPoints[i].set(points[i].x, points[i].y);
                }
            }
            bounds.set(newBounds);
            if (newPoints == null) {
                setPointsFromBounds(bounds, points);
            } else {
                for (int i = 0; i < 4; ++i) {
                    points[i].set(newPoints[i].x, newPoints[i].y);
                }
            }
            lastBoundsUpdate = now;
        }
        fragmentView.invalidate();
    }

    private RectF getRecognizedBounds() {
        float t = (SystemClock.elapsedRealtime() - lastBoundsUpdate) / (float) boundsUpdateDuration;
        t = Math.min(1, Math.max(0, t));
        if (t < 1f) {
            fragmentView.invalidate();
        }
        AndroidUtilities.lerp(fromBounds, bounds, t, AndroidUtilities.rectTmp);
        return AndroidUtilities.rectTmp;
    }

    private PointF[] getRecognizedPoints() {
        float t = (SystemClock.elapsedRealtime() - lastBoundsUpdate) / (float) boundsUpdateDuration;
        t = Math.min(1, Math.max(0, t));
        if (t < 1f) {
            fragmentView.invalidate();
        }
        for (int i = 0; i < 4; ++i) {
            tmpPoints[i].set(
                AndroidUtilities.lerp(fromPoints[i].x, points[i].x, t),
                AndroidUtilities.lerp(fromPoints[i].y, points[i].y, t)
            );
        }
        return tmpPoints;
    }

    private RectF normalBounds;
    private void updateNormalBounds() {
        if (normalBounds == null) {
            normalBounds = new RectF();
        }
        int width = Math.max(AndroidUtilities.displaySize.x, fragmentView.getWidth()),
            height = Math.max(AndroidUtilities.displaySize.y, fragmentView.getHeight()),
            side = (int) (Math.min(width, height) / 1.5f);
        normalBounds.set(
            (width - side) / 2f / (float) width,
            (height - side) / 2f / (float) height,
            (width + side) / 2f / (float) width,
            (height + side) / 2f / (float) height
        );
    }

    private RectF getBounds() {
        RectF recognizedBounds = getRecognizedBounds();
        if (useRecognizedBounds < 1f) {
            if (normalBounds == null) {
                updateNormalBounds();
            }
            AndroidUtilities.lerp(normalBounds, recognizedBounds, useRecognizedBounds, recognizedBounds);
        }
        return recognizedBounds;
    }

    private PointF[] getPoints() {
        PointF[] recognizedPoints = getRecognizedPoints();
        if (useRecognizedBounds < 1f) {
            if (normalBounds == null) {
                updateNormalBounds();
            }
            setPointsFromBounds(normalBounds, tmp2Points);
            for (int i = 0; i < recognizedPoints.length; ++i) {
                recognizedPoints[i].set(
                    AndroidUtilities.lerp(tmp2Points[i].x, recognizedPoints[i].x, useRecognizedBounds),
                    AndroidUtilities.lerp(tmp2Points[i].y, recognizedPoints[i].y, useRecognizedBounds)
                );
            }
        }
        return recognizedPoints;
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && requestCode == 11 && data != null && data.getData() != null) {
            try {
                Point screenSize = AndroidUtilities.getRealScreenSize();
                Bitmap bitmap = ImageLoader.loadBitmap(null, data.getData(), screenSize.x, screenSize.y, true);
                QrResult res = tryReadQr(null, null, 0, 0, 0, bitmap);
                if (res != null) {
                    if (delegate != null) {
                        delegate.didFindQr(res.text);
                    }
                    finishFragment();
                }
            } catch (Throwable e) {
                FileLog.e(e);
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

    private final Runnable requestShot = new Runnable() {
        @Override
        public void run() {
            if (cameraView != null && !recognized && cameraView.getCameraSession() != null) {
                handler.post(() -> {
                    try {
                        cameraView.focusToPoint(cameraView.getWidth() / 2, cameraView.getHeight() / 2, false);
                    } catch (Exception ignore) {

                    }
                    if (cameraView != null) {
                        processShot(cameraView.getTextureView().getBitmap());
                    }
                });
            }
        }
    };

    private void startRecognizing() {
        backgroundHandlerThread.start();
        handler = new Handler(backgroundHandlerThread.getLooper());
        AndroidUtilities.runOnUIThread(requestShot, 0);
    }

    private void onNoQrFound() {
        AndroidUtilities.runOnUIThread(() -> {
            if (recognizedMrzView.getTag() != null) {
                recognizedMrzView.setTag(null);
                recognizedMrzView.animate().setDuration(200).alpha(0.0f).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
            }
        });
    }

    private float averageProcessTime = 0;
    private long processTimesCount = 0;
    public void processShot(Bitmap bitmap) {
        if (cameraView == null) {
            return;
        }
        final long from = SystemClock.elapsedRealtime();
        try {
            Size size = cameraView.getPreviewSize();
            if (currentType == TYPE_MRZ) {
                final MrzRecognizer.Result res = MrzRecognizer.recognize(bitmap, false);
                if (res != null && !TextUtils.isEmpty(res.firstName) && !TextUtils.isEmpty(res.lastName) && !TextUtils.isEmpty(res.number) && res.birthDay != 0 && (res.expiryDay != 0 || res.doesNotExpire) && res.gender != MrzRecognizer.Result.GENDER_UNKNOWN) {
                    recognized = true;
                    CameraController.getInstance().stopPreview(cameraView.getCameraSession());
                    AndroidUtilities.runOnUIThread(() -> {
                        recognizedMrzView.setText(res.rawMRZ);
                        recognizedMrzView.animate().setDuration(200).alpha(1f).setInterpolator(CubicBezierInterpolator.DEFAULT).start();
                        if (delegate != null) {
                            delegate.didFindMrzInfo(res);
                        }
                        AndroidUtilities.runOnUIThread(this::finishFragment, 1200);
                    });
                    return;
                }
            } else {
                int side = (int) (Math.min(size.getWidth(), size.getHeight()) / 1.5f);
                int x = (size.getWidth() - side) / 2;
                int y = (size.getHeight() - side) / 2;

                QrResult res = tryReadQr(null, size, x, y, side, bitmap);
                if (recognized) {
                    recognizeIndex++;
                }
                if (res != null) {
                    recognizeFailed = 0;
                    recognizedText = res.text;
                    if (!recognized) {
                        recognized = true;
                        qrLoading = delegate.processQr(recognizedText, () -> {
                            if (cameraView != null && cameraView.getCameraSession() != null) {
                                CameraController.getInstance().stopPreview(cameraView.getCameraSession());
                            }
                            AndroidUtilities.runOnUIThread(() -> {
                                if (delegate != null) {
                                    delegate.didFindQr(recognizedText);
                                }
                                finishFragment();
                            });
                        });
                        recognizedStart = SystemClock.elapsedRealtime();
                        AndroidUtilities.runOnUIThread(this::updateRecognized);
                    }
                    AndroidUtilities.runOnUIThread(() -> updateRecognizedBounds(res.bounds, res.cornerPoints));
                } else if (recognized) {
                    recognizeFailed++;
                    if (recognizeFailed > 4 && !qrLoading) {
                        recognized = false;
                        recognizeIndex = 0;
                        recognizedText = null;
                        AndroidUtilities.runOnUIThread(this::updateRecognized);
                        AndroidUtilities.runOnUIThread(requestShot, 500);
                        return;
                    }
                }

                if (( // finish because...
                      (recognizeIndex == 0 && res != null && res.bounds == null && !qrLoading) || // first recognition doesn't have bounds
                      (SystemClock.elapsedRealtime() - recognizedStart > 1000 && !qrLoading) // got more than 1 second and nothing is loading
                    ) && recognizedText != null) {
                    if (cameraView != null && cameraView.getCameraSession() != null && currentType != TYPE_QR_WEB_BOT) {
                        CameraController.getInstance().stopPreview(cameraView.getCameraSession());
                    }
                    String text = recognizedText;
                    AndroidUtilities.runOnUIThread(() -> {
                        if (delegate != null) {
                            delegate.didFindQr(text);
                        }
                        if (currentType != TYPE_QR_WEB_BOT) {
                            finishFragment();
                        }
                    });
                    if (currentType == TYPE_QR_WEB_BOT) {
                        AndroidUtilities.runOnUIThread(()->{
                            if (isFinishing()) {
                                return;
                            }

                            recognizedText = null;
                            recognized = false;
                            requestShot.run();
                            if (!recognized) {
                                AndroidUtilities.runOnUIThread(this::updateRecognized, 500);
                            }
                        });
                    }
                } else if (recognized) {
                    long delay = Math.max(16, 1000 / sps - (long) averageProcessTime);
                    handler.postDelayed(() -> {
                        if (cameraView != null) {
                            processShot(cameraView.getTextureView().getBitmap());
                        }
                    }, delay);
                }
            }
        } catch (Throwable ignore) {
            onNoQrFound();
        }
        final long to = SystemClock.elapsedRealtime();
        long timeout = to - from;
        averageProcessTime = (averageProcessTime * processTimesCount + timeout) / (++processTimesCount);
        processTimesCount = Math.max(processTimesCount, 30);

        if (!recognized) {
            AndroidUtilities.runOnUIThread(requestShot, 500);
        }
    }

    private Bitmap invert(Bitmap bitmap) {
        int height = bitmap.getHeight();
        int width = bitmap.getWidth();

        Bitmap newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(newBitmap);
        Paint paint = new Paint();

        ColorMatrix matrixGrayscale = new ColorMatrix();
        matrixGrayscale.setSaturation(0);
        ColorMatrix matrixInvert = new ColorMatrix();
        matrixInvert.set(new float[] {
            -1.0f, 0.0f, 0.0f, 0.0f, 255.0f,
            0.0f, -1.0f, 0.0f, 0.0f, 255.0f,
            0.0f, 0.0f, -1.0f, 0.0f, 255.0f,
            0.0f, 0.0f, 0.0f, 1.0f, 0.0f
        });
        matrixInvert.preConcat(matrixGrayscale);
        paint.setColorFilter(new ColorMatrixColorFilter(matrixInvert));
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return newBitmap;
    }

    private Bitmap monochrome(Bitmap bitmap, int threshold) {
        int height = bitmap.getHeight();
        int width = bitmap.getWidth();

        Bitmap newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(newBitmap);
        Paint paint = new Paint();

        paint.setColorFilter(new ColorMatrixColorFilter(createThresholdMatrix(threshold)));
        canvas.drawBitmap(bitmap, 0, 0, paint);

        return newBitmap;
    }
    public static ColorMatrix createThresholdMatrix(int threshold) {
        ColorMatrix matrix = new ColorMatrix(new float[] {
            85.f, 85.f, 85.f, 0.f, -255.f * threshold,
            85.f, 85.f, 85.f, 0.f, -255.f * threshold,
            85.f, 85.f, 85.f, 0.f, -255.f * threshold,
            0f, 0f, 0f, 1f, 0f
        });
        return matrix;
    }

    private class QrResult {
        String text;
        RectF bounds;
        PointF[] cornerPoints;
    }

    private static PointF[] toPointF(Point[] points, int w, int h) {
        PointF[] out = new PointF[points.length];
        for (int i = 0; i < points.length; ++i) {
            out[i] = new PointF(
                points[i].x / (float) w,
                points[i].y / (float) h
            );
        }
        return out;
    }

    private QrResult tryReadQr(byte[] data, Size size, int x, int y, int side, Bitmap bitmap) {
        try {
            String text;
            RectF bounds = new RectF();
            PointF[] cornerPoints = null;
            int width = 1, height = 1;
            if (visionQrReader != null && visionQrReader.isOperational()) {
                Frame frame;
                if (bitmap != null) {
                    frame = new Frame.Builder().setBitmap(bitmap).build();
                    width = bitmap.getWidth();
                    height = bitmap.getHeight();
                } else {
                    frame = new Frame.Builder().setImageData(ByteBuffer.wrap(data), size.getWidth(), size.getHeight(), ImageFormat.NV21).build();
                    width = size.getWidth();
                    height = size.getWidth();
                }
                SparseArray<Barcode> codes = visionQrReader.detect(frame);
                if (codes != null && codes.size() > 0) {
                    Barcode code = codes.valueAt(0);
                    text = code.rawValue;
                    cornerPoints = toPointF(code.cornerPoints, width, height);
                    if (code.cornerPoints == null || code.cornerPoints.length == 0) {
                        bounds = null;
                    } else {
                        float minX = Float.MAX_VALUE,
                              maxX = Float.MIN_VALUE,
                              minY = Float.MAX_VALUE,
                              maxY = Float.MIN_VALUE;
                        for (Point point : code.cornerPoints) {
                            minX = Math.min(minX, point.x);
                            maxX = Math.max(maxX, point.x);
                            minY = Math.min(minY, point.y);
                            maxY = Math.max(maxY, point.y);
                        }
                        bounds.set(minX, minY, maxX, maxY);
                    }
                } else if (bitmap != null) {
                    Bitmap inverted = invert(bitmap);
                    bitmap.recycle();
                    frame = new Frame.Builder().setBitmap(inverted).build();
                    width = inverted.getWidth();
                    height = inverted.getHeight();
                    codes = visionQrReader.detect(frame);
                    if (codes != null && codes.size() > 0) {
                        Barcode code = codes.valueAt(0);
                        text = code.rawValue;
                        cornerPoints = toPointF(code.cornerPoints, width, height);
                        if (code.cornerPoints == null || code.cornerPoints.length == 0) {
                            bounds = null;
                        } else {
                            float minX = Float.MAX_VALUE,
                                    maxX = Float.MIN_VALUE,
                                    minY = Float.MAX_VALUE,
                                    maxY = Float.MIN_VALUE;
                            for (Point point : code.cornerPoints) {
                                minX = Math.min(minX, point.x);
                                maxX = Math.max(maxX, point.x);
                                minY = Math.min(minY, point.y);
                                maxY = Math.max(maxY, point.y);
                            }
                            bounds.set(minX, minY, maxX, maxY);
                        }
                    } else {
                        Bitmap monochrome = monochrome(inverted, 90);
                        inverted.recycle();
                        frame = new Frame.Builder().setBitmap(monochrome).build();
                        width = inverted.getWidth();
                        height = inverted.getHeight();
                        codes = visionQrReader.detect(frame);
                        if (codes != null && codes.size() > 0) {
                            Barcode code = codes.valueAt(0);
                            text = code.rawValue;
                            cornerPoints = toPointF(code.cornerPoints, width, height);
                            if (code.cornerPoints == null || code.cornerPoints.length == 0) {
                                bounds = null;
                            } else {
                                float minX = Float.MAX_VALUE,
                                        maxX = Float.MIN_VALUE,
                                        minY = Float.MAX_VALUE,
                                        maxY = Float.MIN_VALUE;
                                for (Point point : code.cornerPoints) {
                                    minX = Math.min(minX, point.x);
                                    maxX = Math.max(maxX, point.x);
                                    minY = Math.min(minY, point.y);
                                    maxY = Math.max(maxY, point.y);
                                }
                                bounds.set(minX, minY, maxX, maxY);
                            }
                        } else {
                            text = null;
                        }
                    }
                } else {
                    text = null;
                }
            } else if (qrReader != null) {
                LuminanceSource source;
                if (bitmap != null) {
                    int[] intArray = new int[bitmap.getWidth() * bitmap.getHeight()];
                    bitmap.getPixels(intArray, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
                    source = new RGBLuminanceSource(bitmap.getWidth(), bitmap.getHeight(), intArray);
                    width = bitmap.getWidth();
                    height = bitmap.getHeight();
                } else {
                    source = new PlanarYUVLuminanceSource(data, size.getWidth(), size.getHeight(), x, y, side, side, false);
                    width = size.getWidth();
                    height = size.getHeight();
                }

                Result result = qrReader.decode(new BinaryBitmap(new GlobalHistogramBinarizer(source)));
                if (result == null) {
                    onNoQrFound();
                    return null;
                }
                text = result.getText();
                if (result.getResultPoints() == null || result.getResultPoints().length == 0) {
                    bounds = null;
                } else {
                    float minX = Float.MAX_VALUE,
                          maxX = Float.MIN_VALUE,
                          minY = Float.MAX_VALUE,
                          maxY = Float.MIN_VALUE;
                    for (ResultPoint point : result.getResultPoints()) {
                        minX = Math.min(minX, point.getX());
                        maxX = Math.max(maxX, point.getX());
                        minY = Math.min(minY, point.getY());
                        maxY = Math.max(maxY, point.getY());
                    }
                    bounds.set(minX, minY, maxX, maxY);
                    if (result.getResultPoints().length == 4) {
                        cornerPoints = new PointF[4];
                        for (int i = 0; i < 4; ++i) {
                            cornerPoints[i] = new PointF(
                                result.getResultPoints()[i].getX() / width,
                                result.getResultPoints()[i].getY() / height
                            );
                        }
                    }
                }
            } else {
                text = null;
            }
            if (TextUtils.isEmpty(text)) {
                onNoQrFound();
                return null;
            }
            if (needGalleryButton) {
                Uri uri = Uri.parse(text);
                String path = uri.getPath().replace("/", "");
            } else {
                if (currentType == TYPE_QR_LOGIN && !text.startsWith("tg://login?token=")) {
                    onNoQrFound();
                    return null;
                }
            }
            QrResult qrResult = new QrResult();
            if (bounds != null) {
                int paddingx = dp(25),
                    paddingy = dp(15);
                bounds.set(bounds.left - paddingx, bounds.top - paddingy, bounds.right + paddingx, bounds.bottom + paddingy);
                bounds.set(
                    bounds.left / (float) width, bounds.top / (float) height,
                    bounds.right / (float) width, bounds.bottom / (float) height
                );
            }
            qrResult.cornerPoints = cornerPoints;
            qrResult.bounds = bounds;
            qrResult.text = text;
            return qrResult;
        } catch (Throwable ignore) {
            onNoQrFound();
        }
        return null;
    }


    private boolean isQr() {
        return currentType == TYPE_QR || currentType == TYPE_QR_LOGIN || currentType == TYPE_QR_WEB_BOT;
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
        if (isQr()) {
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
