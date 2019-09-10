/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Property;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.R;

import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ColorPicker;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SeekBarView;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.WallpaperParallaxEffect;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class WallpaperActivity extends BaseFragment implements DownloadController.FileDownloadProgressListener, NotificationCenter.NotificationCenterDelegate {

    private RecyclerListView listView;
    private BackupImageView backgroundImage;
    private FrameLayout buttonsContainer;
    private AnimatorSet motionAnimation;
    private RadialProgress2 radialProgress;
    private FrameLayout bottomOverlayChat;
    private CheckBoxView[] checkBoxView;

    private FrameLayout[] patternLayout = new FrameLayout[3];
    private TextView[] patternsCancelButton = new TextView[2];
    private TextView[] patternsSaveButton = new TextView[2];
    private FrameLayout[] patternsButtonsContainer = new FrameLayout[2];

    private RecyclerListView patternsListView;
    private PatternsAdapter patternsAdapter;
    private LinearLayoutManager patternsLayoutManager;
    private HeaderCell intensityCell;
    private SeekBarView intensitySeekBar;

    private ColorPicker colorPicker;

    private ArrayList<Object> patterns;
    private TLRPC.TL_wallPaper selectedPattern;
    private TLRPC.TL_wallPaper previousSelectedPattern;
    private int backgroundColor;
    private int patternColor;
    private int previousBackgroundColor;
    private float currentIntensity = 0.4f;
    private float previousIntensity;

    private PorterDuff.Mode blendMode = PorterDuff.Mode.SRC_IN;

    private int TAG;

    private WallpaperParallaxEffect parallaxEffect;
    private Bitmap blurredBitmap;
    private float parallaxScale = 1.0f;

    private TextView bottomOverlayChatText;

    private String loadingFile = null;
    private File loadingFileObject = null;
    private TLRPC.PhotoSize loadingSize = null;

    private Object currentWallpaper;
    private Bitmap currentWallpaperBitmap;

    private final static int share_item = 1;
    private boolean isMotion;
    private boolean isBlurred;

    private TextPaint textPaint;
    private Paint eraserPaint;
    private Paint checkPaint;
    private Paint backgroundPaint;

    private boolean progressVisible;

    private String imageFilter = "640_360";
    private int maxWallpaperSize = 1920;

    private WallpaperActivityDelegate delegate;

    public interface WallpaperActivityDelegate {
        void didSetNewBackground();
    }

    private class PatternCell extends BackupImageView implements DownloadController.FileDownloadProgressListener {

        private RectF rect = new RectF();
        private RadialProgress2 radialProgress;
        private boolean wasSelected;
        private TLRPC.TL_wallPaper currentPattern;

        private int TAG;

        public PatternCell(Context context) {
            super(context);
            setRoundRadius(AndroidUtilities.dp(6));

            radialProgress = new RadialProgress2(this);
            radialProgress.setProgressRect(AndroidUtilities.dp(30), AndroidUtilities.dp(30), AndroidUtilities.dp(70), AndroidUtilities.dp(70));

            TAG = DownloadController.getInstance(currentAccount).generateObserverTag();
        }

        private void setPattern(TLRPC.TL_wallPaper wallPaper) {
            currentPattern = wallPaper;
            if (wallPaper != null) {
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(wallPaper.document.thumbs, 100);
                setImage(ImageLocation.getForDocument(thumb, wallPaper.document), "100_100", null, null, "jpg", 0, 1, wallPaper);
            } else {
                setImageDrawable(null);
            }
            updateSelected(false);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            updateSelected(false);
        }

        public void updateSelected(boolean animated) {
            boolean isSelected = currentPattern == null && selectedPattern == null || selectedPattern != null && currentPattern != null && currentPattern.id == selectedPattern.id;
            if (isSelected) {
                updateButtonState(radialProgress, selectedPattern, this, false, animated);
            } else {
                radialProgress.setIcon(MediaActionDrawable.ICON_NONE, false, animated);
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            getImageReceiver().setAlpha(0.8f);

            backgroundPaint.setColor(backgroundColor);
            rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), backgroundPaint);

            super.onDraw(canvas);

            radialProgress.setColors(patternColor, patternColor, 0xffffffff, 0xffffffff);
            radialProgress.draw(canvas);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(AndroidUtilities.dp(100), AndroidUtilities.dp(100));
        }

        @Override
        public void onFailedDownload(String fileName, boolean canceled) {
            updateButtonState(radialProgress, currentPattern, this, true, canceled);
        }

        @Override
        public void onSuccessDownload(String fileName) {
            radialProgress.setProgress(1, progressVisible);
            updateButtonState(radialProgress, currentPattern, this, false,true);
        }

        @Override
        public void onProgressDownload(String fileName, float progress) {
            radialProgress.setProgress(progress, progressVisible);
            if (radialProgress.getIcon() != MediaActionDrawable.ICON_EMPTY) {
                updateButtonState(radialProgress, currentPattern, this, false, true);
            }
        }

        @Override
        public void onProgressUpload(String fileName, float progress, boolean isEncrypted) {

        }

        @Override
        public int getObserverTag() {
            return TAG;
        }
    }

    private class CheckBoxView extends View {

        private String currentText;
        private int currentTextSize;
        private int maxTextSize;
        private RectF rect;

        private boolean isChecked;
        private Canvas drawCanvas;
        private Bitmap drawBitmap;
        private float progress;
        private ObjectAnimator checkAnimator;

        private final static float progressBounceDiff = 0.2f;

        public final Property<CheckBoxView, Float> PROGRESS_PROPERTY = new AnimationProperties.FloatProperty<CheckBoxView>("progress") {
            @Override
            public void setValue(CheckBoxView object, float value) {
                progress = value;
                invalidate();
            }

            @Override
            public Float get(CheckBoxView object) {
                return progress;
            }
        };

        public CheckBoxView(Context context, boolean check) {
            super(context);
            rect = new RectF();

            if (check) {
                drawBitmap = Bitmap.createBitmap(AndroidUtilities.dp(18), AndroidUtilities.dp(18), Bitmap.Config.ARGB_4444);
                drawCanvas = new Canvas(drawBitmap);
            }
        }

        public void setText(String text, int current, int max) {
            currentText = text;
            currentTextSize = current;
            maxTextSize = max;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(maxTextSize + AndroidUtilities.dp(14 * 2 + 28), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(32), MeasureSpec.EXACTLY));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), Theme.chat_actionBackgroundPaint);

            int x = (getMeasuredWidth() - currentTextSize - AndroidUtilities.dp(28)) / 2;
            canvas.drawText(currentText, x + AndroidUtilities.dp(28), AndroidUtilities.dp(21), textPaint);

            canvas.save();
            canvas.translate(x, AndroidUtilities.dp(7));
            if (drawBitmap != null) {
                float checkProgress;
                float bounceProgress;
                if (progress <= 0.5f) {
                    bounceProgress = checkProgress = progress / 0.5f;
                } else {
                    bounceProgress = 2.0f - progress / 0.5f;
                    checkProgress = 1.0f;
                }

                float bounce = AndroidUtilities.dp(1) * bounceProgress;
                rect.set(bounce, bounce, AndroidUtilities.dp(18) - bounce, AndroidUtilities.dp(18) - bounce);

                drawBitmap.eraseColor(0);
                backgroundPaint.setColor(0xffffffff);
                drawCanvas.drawRoundRect(rect, rect.width() / 2, rect.height() / 2, backgroundPaint);

                if (checkProgress != 1) {
                    float rad = Math.min(AndroidUtilities.dp(7), AndroidUtilities.dp(7) * checkProgress + bounce);
                    rect.set(AndroidUtilities.dp(2) + rad, AndroidUtilities.dp(2) + rad, AndroidUtilities.dp(16) - rad, AndroidUtilities.dp(16) - rad);
                    drawCanvas.drawRoundRect(rect, rect.width() / 2, rect.height() / 2, eraserPaint);
                }

                if (progress > 0.5f) {
                    int endX = (int) (AndroidUtilities.dp(7.3f) - AndroidUtilities.dp(2.5f) * (1.0f - bounceProgress));
                    int endY = (int) (AndroidUtilities.dp(13) - AndroidUtilities.dp(2.5f) * (1.0f - bounceProgress));
                    drawCanvas.drawLine(AndroidUtilities.dp(7.3f), AndroidUtilities.dp(13), endX, endY, checkPaint);
                    endX = (int) (AndroidUtilities.dp(7.3f) + AndroidUtilities.dp(6) * (1.0f - bounceProgress));
                    endY = (int) (AndroidUtilities.dp(13) - AndroidUtilities.dp(6) * (1.0f - bounceProgress));
                    drawCanvas.drawLine(AndroidUtilities.dp(7.3f), AndroidUtilities.dp(13), endX, endY, checkPaint);
                }
                canvas.drawBitmap(drawBitmap, 0, 0, null);
            } else {
                backgroundPaint.setColor(backgroundColor);
                rect.set(0, 0, AndroidUtilities.dp(18), AndroidUtilities.dp(18));
                canvas.drawRoundRect(rect, rect.width() / 2, rect.height() / 2, backgroundPaint);
            }
            canvas.restore();
        }

        private void setProgress(float value) {
            if (progress == value) {
                return;
            }
            progress = value;
            invalidate();
        }

        private void cancelCheckAnimator() {
            if (checkAnimator != null) {
                checkAnimator.cancel();
            }
        }

        private void animateToCheckedState(boolean newCheckedState) {
            checkAnimator = ObjectAnimator.ofFloat(this, PROGRESS_PROPERTY, newCheckedState ? 1.0f : 0.0f);
            checkAnimator.setDuration(300);
            checkAnimator.start();
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
        }

        public void setChecked(boolean checked, boolean animated) {
            if (checked == isChecked) {
                return;
            }
            isChecked = checked;
            if (animated) {
                animateToCheckedState(checked);
            } else {
                cancelCheckAnimator();
                progress = checked ? 1.0f : 0.0f;
                invalidate();
            }
        }

        public boolean isChecked() {
            return isChecked;
        }
    }

    public WallpaperActivity(Object wallPaper, Bitmap bitmap) {
        super();
        currentWallpaper = wallPaper;
        currentWallpaperBitmap = bitmap;
        if (currentWallpaper instanceof TLRPC.TL_wallPaper) {
            TLRPC.TL_wallPaper object = (TLRPC.TL_wallPaper) currentWallpaper;
            /*if (object.settings != null) {
                isBlurred = object.settings.blur;
                isMotion = object.settings.motion;
            }*/
        } else if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
            WallpapersListActivity.ColorWallpaper object = (WallpapersListActivity.ColorWallpaper) currentWallpaper;
            isMotion = object.motion;
            selectedPattern = object.pattern;
            if (selectedPattern != null) {
                currentIntensity = object.intensity;
            }
        }
    }

    public void setInitialModes(boolean blur, boolean motion) {
        isBlurred = blur;
        isMotion = motion;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        imageFilter = (int) (1080 / AndroidUtilities.density) + "_" + (int) (1920 / AndroidUtilities.density) + "_f";
        maxWallpaperSize = Math.min(1920, Math.max(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y));

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.wallpapersNeedReload);
        TAG = DownloadController.getInstance(currentAccount).generateObserverTag();

        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xffffffff);
        textPaint.setTextSize(AndroidUtilities.dp(14));
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        checkPaint.setStyle(Paint.Style.STROKE);
        checkPaint.setStrokeWidth(AndroidUtilities.dp(2));
        checkPaint.setColor(0);
        checkPaint.setStrokeCap(Paint.Cap.ROUND);
        checkPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        eraserPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        eraserPaint.setColor(0);
        eraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (blurredBitmap != null) {
            blurredBitmap.recycle();
            blurredBitmap = null;
        }
        Theme.applyChatServiceMessageColor();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.wallpapersNeedReload);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("BackgroundPreview", R.string.BackgroundPreview));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == share_item) {
                    if (getParentActivity() == null) {
                        return;
                    }
                    String link;
                    StringBuilder modes = new StringBuilder();
                    if (isBlurred) {
                        modes.append("blur");
                    }
                    if (isMotion) {
                        if (modes.length() > 0) {
                            modes.append("+");
                        }
                        modes.append("motion");
                    }
                    if (currentWallpaper instanceof TLRPC.TL_wallPaper) {
                        TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) currentWallpaper;
                        link = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/bg/" + wallPaper.slug;
                        if (modes.length() > 0) {
                            link += "?mode=" + modes.toString();
                        }
                    } else if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
                        WallpapersListActivity.ColorWallpaper colorWallpaper = (WallpapersListActivity.ColorWallpaper) currentWallpaper;
                        String color = String.format("%02x%02x%02x", (byte) (backgroundColor >> 16) & 0xff, (byte) (backgroundColor >> 8) & 0xff, (byte) (backgroundColor & 0xff)).toLowerCase();
                        if (selectedPattern != null) {
                            link = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/bg/" + selectedPattern.slug + "?intensity=" + (int) (currentIntensity * 100) + "&bg_color=" + color;
                            if (modes.length() > 0) {
                                link += "&mode=" + modes.toString();
                            }
                        } else {
                            link = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/bg/" + color;
                        }
                    } else {
                        return;
                    }
                    showDialog(new ShareAlert(getParentActivity(), null, link, false, link, false));
                }
            }
        });

        if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper || currentWallpaper instanceof TLRPC.TL_wallPaper) {
            ActionBarMenu menu = actionBar.createMenu();
            menu.addItem(share_item, R.drawable.ic_share_video);
        }

        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;
        hasOwnBackground = true;

        backgroundImage = new BackupImageView(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                parallaxScale = parallaxEffect.getScale(getMeasuredWidth(), getMeasuredHeight());
                if (isMotion) {
                    setScaleX(parallaxScale);
                    setScaleY(parallaxScale);
                }
                if (radialProgress != null) {
                    int size = AndroidUtilities.dp(44);
                    int x = (getMeasuredWidth() - size) / 2;
                    int y = (getMeasuredHeight() - size) / 2;
                    radialProgress.setProgressRect(x, y, x + size, y + size);
                }

                progressVisible = getMeasuredWidth() <= getMeasuredHeight();
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (progressVisible && radialProgress != null) {
                    radialProgress.draw(canvas);
                }
            }

            @Override
            public void setAlpha(float alpha) {
                radialProgress.setOverrideAlpha(alpha);
            }
        };
        int textsCount;
        int startIndex;
        boolean buttonsAvailable;
        if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
            textsCount = 3;
            startIndex = patterns != null ? 0 : 2;
            buttonsAvailable = patterns != null || selectedPattern != null;
        } else {
            textsCount = 2;
            startIndex = 0;
            buttonsAvailable = true;
        }
        frameLayout.addView(backgroundImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 48));
        backgroundImage.getImageReceiver().setDelegate((imageReceiver, set, thumb) -> {
            if (!(currentWallpaper instanceof WallpapersListActivity.ColorWallpaper)) {
                Drawable drawable = imageReceiver.getDrawable();
                if (set && drawable != null) {
                    Theme.applyChatServiceMessageColor(AndroidUtilities.calcDrawableColor(drawable));
                    listView.invalidateViews();
                    for (int a = 0, N = buttonsContainer.getChildCount(); a < N; a++) {
                        buttonsContainer.getChildAt(a).invalidate();
                    }
                    if (radialProgress != null) {
                        radialProgress.setColors(Theme.key_chat_serviceBackground, Theme.key_chat_serviceBackground, Theme.key_chat_serviceText, Theme.key_chat_serviceText);
                    }
                    if (!thumb && isBlurred && blurredBitmap == null) {
                        backgroundImage.getImageReceiver().setCrossfadeWithOldImage(false);
                        updateBlurred();
                        backgroundImage.getImageReceiver().setCrossfadeWithOldImage(true);
                    }
                }
            }
        });

        radialProgress = new RadialProgress2(backgroundImage);
        radialProgress.setColors(Theme.key_chat_serviceBackground, Theme.key_chat_serviceBackground, Theme.key_chat_serviceText, Theme.key_chat_serviceText);

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, true));
        listView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
        listView.setAdapter(new ListAdapter(context));
        listView.setPadding(0, 0, 0, AndroidUtilities.dp(buttonsAvailable ? 64 : 4));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 48));

        bottomOverlayChat = new FrameLayout(context) {
            @Override
            public void onDraw(Canvas canvas) {
                int bottom = Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                Theme.chat_composeShadowDrawable.setBounds(0, 0, getMeasuredWidth(), bottom);
                Theme.chat_composeShadowDrawable.draw(canvas);
                canvas.drawRect(0, bottom, getMeasuredWidth(), getMeasuredHeight(), Theme.chat_composeBackgroundPaint);
            }
        };
        bottomOverlayChat.setWillNotDraw(false);
        bottomOverlayChat.setPadding(0, AndroidUtilities.dp(3), 0, 0);
        frameLayout.addView(bottomOverlayChat, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM));
        bottomOverlayChat.setOnClickListener(view -> {
            boolean done;
            boolean sameFile = false;
            File toFile = new File(ApplicationLoader.getFilesDirFixed(), isBlurred ? "wallpaper_original.jpg" : "wallpaper.jpg");
            if (currentWallpaper instanceof TLRPC.TL_wallPaper) {
                try {
                    Bitmap bitmap = backgroundImage.getImageReceiver().getBitmap();
                    FileOutputStream stream = new FileOutputStream(toFile);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
                    stream.close();
                    done = true;
                } catch (Exception e) {
                    done = false;
                    FileLog.e(e);
                }
                if (!done) {
                    TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) currentWallpaper;
                    File f = FileLoader.getPathToAttach(wallPaper.document, true);
                    try {
                        done = AndroidUtilities.copyFile(f, toFile);
                    } catch (Exception e) {
                        done = false;
                        FileLog.e(e);
                    }
                }
            } else if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
                if (selectedPattern != null) {
                    try {
                        WallpapersListActivity.ColorWallpaper wallPaper = (WallpapersListActivity.ColorWallpaper) currentWallpaper;
                        Bitmap bitmap = backgroundImage.getImageReceiver().getBitmap();
                        @SuppressLint("DrawAllocation")
                        Bitmap dst = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(dst);
                        canvas.drawColor(backgroundColor);
                        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
                        paint.setColorFilter(new PorterDuffColorFilter(patternColor, blendMode));
                        paint.setAlpha((int) (255 * currentIntensity));
                        canvas.drawBitmap(bitmap, 0, 0, paint);

                        FileOutputStream stream = new FileOutputStream(toFile);
                        dst.compress(Bitmap.CompressFormat.JPEG, 87, stream);
                        stream.close();
                        done = true;
                    } catch (Throwable e) {
                        FileLog.e(e);
                        done = false;
                    }
                } else {
                    done = true;
                }
            } else if (currentWallpaper instanceof WallpapersListActivity.FileWallpaper) {
                WallpapersListActivity.FileWallpaper wallpaper = (WallpapersListActivity.FileWallpaper) currentWallpaper;
                if (wallpaper.resId != 0 || wallpaper.resId == Theme.THEME_BACKGROUND_ID) {
                    done = true;
                } else {
                    try {
                        File fromFile = wallpaper.originalPath != null ? wallpaper.originalPath : wallpaper.path;
                        if (sameFile = fromFile.equals(toFile)) {
                            done = true;
                        } else {
                            done = AndroidUtilities.copyFile(fromFile, toFile);
                        }
                    } catch (Exception e) {
                        done = false;
                        FileLog.e(e);
                    }
                }
            } else if (currentWallpaper instanceof MediaController.SearchImage) {
                MediaController.SearchImage wallpaper = (MediaController.SearchImage) currentWallpaper;
                File f;
                if (wallpaper.photo != null) {
                    TLRPC.PhotoSize image = FileLoader.getClosestPhotoSizeWithSize(wallpaper.photo.sizes, maxWallpaperSize, true);
                    f = FileLoader.getPathToAttach(image, true);
                } else {
                    f = ImageLoader.getHttpFilePath(wallpaper.imageUrl, "jpg");
                }
                try {
                    done = AndroidUtilities.copyFile(f, toFile);
                } catch (Exception e) {
                    done = false;
                    FileLog.e(e);
                }
            } else {
                done = false;
            }
            if (isBlurred) {
                try {
                    File blurredFile = new File(ApplicationLoader.getFilesDirFixed(), "wallpaper.jpg");
                    FileOutputStream stream = new FileOutputStream(blurredFile);
                    blurredBitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
                    stream.close();
                    done = true;
                } catch (Throwable e) {
                    FileLog.e(e);
                    done = false;
                }
            }
            long id;
            String slug = null;
            long saveId = 0;
            long access_hash = 0;
            int color = 0;
            long pattern = 0;
            File path = null;

            if (currentWallpaper instanceof TLRPC.TL_wallPaper) {
                TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) currentWallpaper;
                saveId = id = wallPaper.id;
                access_hash = wallPaper.access_hash;
                slug = wallPaper.slug;
            } else if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
                WallpapersListActivity.ColorWallpaper wallPaper = (WallpapersListActivity.ColorWallpaper) currentWallpaper;
                if (selectedPattern != null) {
                    saveId = selectedPattern.id;
                    access_hash = selectedPattern.access_hash;
                    if (wallPaper.id == wallPaper.patternId && backgroundColor == wallPaper.color && (wallPaper.intensity - currentIntensity) <= 0.001f) {
                        id = selectedPattern.id;
                    } else {
                        id = -1;
                    }
                    pattern = selectedPattern.id;
                    slug = selectedPattern.slug;
                } else {
                    id = -1;
                }
                color = backgroundColor;
            } else if (currentWallpaper instanceof WallpapersListActivity.FileWallpaper) {
                WallpapersListActivity.FileWallpaper wallPaper = (WallpapersListActivity.FileWallpaper) currentWallpaper;
                id = wallPaper.id;
                path = wallPaper.path;
            } else if (currentWallpaper instanceof MediaController.SearchImage) {
                MediaController.SearchImage wallPaper = (MediaController.SearchImage) currentWallpaper;
                if (wallPaper.photo != null) {
                    TLRPC.PhotoSize image = FileLoader.getClosestPhotoSizeWithSize(wallPaper.photo.sizes, maxWallpaperSize, true);
                    path = FileLoader.getPathToAttach(image, true);
                } else {
                    path = ImageLoader.getHttpFilePath(wallPaper.imageUrl, "jpg");
                }
                id = -1;
            } else {
                id = 0;
                color = 0;
            }

            MessagesController.getInstance(currentAccount).saveWallpaperToServer(path, saveId, slug, access_hash, isBlurred, isMotion, color, currentIntensity, access_hash != 0, 0);

            if (done) {
                Theme.serviceMessageColorBackup = Theme.getColor(Theme.key_chat_serviceBackground);
                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                SharedPreferences.Editor editor = preferences.edit();
                editor.putLong("selectedBackground2", id);
                if (!TextUtils.isEmpty(slug)) {
                    editor.putString("selectedBackgroundSlug", slug);
                } else {
                    editor.remove("selectedBackgroundSlug");
                }
                editor.putBoolean("selectedBackgroundBlurred", isBlurred);
                editor.putBoolean("selectedBackgroundMotion", isMotion);
                editor.putInt("selectedColor", color);
                editor.putFloat("selectedIntensity", currentIntensity);
                editor.putLong("selectedPattern", pattern);
                editor.putBoolean("overrideThemeWallpaper", id != Theme.THEME_BACKGROUND_ID);
                editor.commit();
                Theme.reloadWallpaper();
                if (!sameFile) {
                    ImageLoader.getInstance().removeImage(ImageLoader.getHttpFileName(toFile.getAbsolutePath()) + "@100_100");
                }
            }
            if (delegate != null) {
                delegate.didSetNewBackground();
            }
            finishFragment();
        });

        bottomOverlayChatText = new TextView(context);
        bottomOverlayChatText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        bottomOverlayChatText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        bottomOverlayChatText.setTextColor(Theme.getColor(Theme.key_chat_fieldOverlayText));
        bottomOverlayChatText.setText(LocaleController.getString("SetBackground", R.string.SetBackground));
        bottomOverlayChat.addView(bottomOverlayChatText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        buttonsContainer = new FrameLayout(context);
        frameLayout.addView(buttonsContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 32, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 50 + 16));

        String[] texts = new String[textsCount];
        int[] textSizes = new int[textsCount];
        checkBoxView = new CheckBoxView[textsCount];
        if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
            texts[0] = LocaleController.getString("BackgroundColor", R.string.BackgroundColor);
            texts[1] = LocaleController.getString("BackgroundPattern", R.string.BackgroundPattern);
            texts[2] = LocaleController.getString("BackgroundMotion", R.string.BackgroundMotion);
        } else {
            texts[0] = LocaleController.getString("BackgroundBlurred", R.string.BackgroundBlurred);
            texts[1] = LocaleController.getString("BackgroundMotion", R.string.BackgroundMotion);
        }
        int maxTextSize = 0;
        for (int a = 0; a < texts.length; a++) {
            textSizes[a] = (int) Math.ceil(textPaint.measureText(texts[a]));
            maxTextSize = Math.max(maxTextSize, textSizes[a]);
        }

        for (int a = startIndex; a < textsCount; a++) {
            final int num = a;
            checkBoxView[a] = new CheckBoxView(context, !(currentWallpaper instanceof WallpapersListActivity.ColorWallpaper && a == 0));
            checkBoxView[a].setText(texts[a], textSizes[a], maxTextSize);

            if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
                if (a == 1) {
                    checkBoxView[a].setChecked(selectedPattern != null, false);
                } else if (a == 2) {
                    checkBoxView[a].setChecked(isMotion, false);
                }
            } else {
                checkBoxView[a].setChecked(a == 0 ? isBlurred : isMotion, false);
            }
            int width = maxTextSize + AndroidUtilities.dp(14 * 2 + 28);
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, width);
            layoutParams.gravity = Gravity.LEFT | Gravity.TOP;
            layoutParams.leftMargin = a == 1 ? width + AndroidUtilities.dp(9) : 0;
            buttonsContainer.addView(checkBoxView[a], layoutParams);
            CheckBoxView view = checkBoxView[a];
            checkBoxView[a].setOnClickListener(v -> {
                if (buttonsContainer.getAlpha() != 1.0f) {
                    return;
                }
                if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
                    if (num == 2) {
                        view.setChecked(!view.isChecked(), true);
                        isMotion = view.isChecked();
                        parallaxEffect.setEnabled(isMotion);
                        animateMotionChange();
                    } else {
                        if (num == 1 && patternLayout[num].getVisibility() == View.VISIBLE) {
                            backgroundImage.setImageDrawable(null);
                            selectedPattern = null;
                            isMotion = false;
                            updateButtonState(radialProgress, null, WallpaperActivity.this, false, true);
                            updateSelectedPattern(true);
                            checkBoxView[1].setChecked(false, true);
                            patternsListView.invalidateViews();
                        }
                        showPatternsView(num, patternLayout[num].getVisibility() != View.VISIBLE);
                    }
                } else {
                    view.setChecked(!view.isChecked(), true);
                    if (num == 0) {
                        isBlurred = view.isChecked();
                        updateBlurred();
                    } else {
                        isMotion = view.isChecked();
                        parallaxEffect.setEnabled(isMotion);
                        animateMotionChange();
                    }
                }
            });
            if (startIndex == 0 && a == 2) {
                checkBoxView[a].setAlpha(0.0f);
                checkBoxView[a].setVisibility(View.INVISIBLE);
            }
        }

        if (!buttonsAvailable) {
            buttonsContainer.setVisibility(View.GONE);
        }

        parallaxEffect = new WallpaperParallaxEffect(context);
        parallaxEffect.setCallback((offsetX, offsetY) -> {
            if (!isMotion) {
                return;
            }
            float progress;
            if (motionAnimation != null) {
                progress = (backgroundImage.getScaleX() - 1.0f) / (parallaxScale - 1.0f);
            } else {
                progress = 1.0f;
            }
            backgroundImage.setTranslationX(offsetX * progress);
            backgroundImage.setTranslationY(offsetY * progress);
        });

        if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
            isBlurred = false;

            for (int a = 0; a < 2; a++) {
                final int num = a;

                patternLayout[a] = new FrameLayout(context) {
                    @Override
                    public void onDraw(Canvas canvas) {
                        int bottom = Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                        Theme.chat_composeShadowDrawable.setBounds(0, 0, getMeasuredWidth(), bottom);
                        Theme.chat_composeShadowDrawable.draw(canvas);
                        canvas.drawRect(0, bottom, getMeasuredWidth(), getMeasuredHeight(), Theme.chat_composeBackgroundPaint);
                    }
                };
                patternLayout[a].setVisibility(View.INVISIBLE);
                patternLayout[a].setWillNotDraw(false);
                frameLayout.addView(patternLayout[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, a == 0 ? 390 : 242, Gravity.LEFT | Gravity.BOTTOM));

                patternsButtonsContainer[a] = new FrameLayout(context) {
                    @Override
                    public void onDraw(Canvas canvas) {
                        int bottom = Theme.chat_composeShadowDrawable.getIntrinsicHeight();
                        Theme.chat_composeShadowDrawable.setBounds(0, 0, getMeasuredWidth(), bottom);
                        Theme.chat_composeShadowDrawable.draw(canvas);
                        canvas.drawRect(0, bottom, getMeasuredWidth(), getMeasuredHeight(), Theme.chat_composeBackgroundPaint);
                    }
                };
                patternsButtonsContainer[a].setWillNotDraw(false);
                patternsButtonsContainer[a].setPadding(0, AndroidUtilities.dp(3), 0, 0);
                patternLayout[a].addView(patternsButtonsContainer[a], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 51, Gravity.BOTTOM));

                patternsCancelButton[a] = new TextView(context);
                patternsCancelButton[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                patternsCancelButton[a].setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                patternsCancelButton[a].setTextColor(Theme.getColor(Theme.key_chat_fieldOverlayText));
                patternsCancelButton[a].setText(LocaleController.getString("Cancel", R.string.Cancel).toUpperCase());
                patternsCancelButton[a].setGravity(Gravity.CENTER);
                patternsCancelButton[a].setPadding(AndroidUtilities.dp(21), 0, AndroidUtilities.dp(21), 0);
                patternsCancelButton[a].setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 0));
                patternsButtonsContainer[a].addView(patternsCancelButton[a], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));
                patternsCancelButton[a].setOnClickListener(v -> {
                    if (num == 0) {
                        setBackgroundColor(previousBackgroundColor);
                    } else {
                        selectedPattern = previousSelectedPattern;
                        if (selectedPattern == null) {
                            backgroundImage.setImageDrawable(null);
                        } else {
                            backgroundImage.setImage(ImageLocation.getForDocument(selectedPattern.document), imageFilter, null, null, "jpg", selectedPattern.document.size, 1, selectedPattern);
                        }
                        checkBoxView[1].setChecked(selectedPattern != null, false);

                        currentIntensity = previousIntensity;
                        intensitySeekBar.setProgress(currentIntensity);
                        backgroundImage.getImageReceiver().setAlpha(currentIntensity);
                        updateButtonState(radialProgress, null, WallpaperActivity.this, false, true);
                        updateSelectedPattern(true);
                    }
                    showPatternsView(num, false);
                });

                patternsSaveButton[a] = new TextView(context);
                patternsSaveButton[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                patternsSaveButton[a].setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                patternsSaveButton[a].setTextColor(Theme.getColor(Theme.key_chat_fieldOverlayText));
                patternsSaveButton[a].setText(LocaleController.getString("Save", R.string.Save).toUpperCase());
                patternsSaveButton[a].setGravity(Gravity.CENTER);
                patternsSaveButton[a].setPadding(AndroidUtilities.dp(21), 0, AndroidUtilities.dp(21), 0);
                patternsSaveButton[a].setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 0));
                patternsButtonsContainer[a].addView(patternsSaveButton[a], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.TOP));
                patternsSaveButton[a].setOnClickListener(v -> showPatternsView(num, false));

                if (a == 1) {
                    patternsListView = new RecyclerListView(context) {
                        @Override
                        public boolean onTouchEvent(MotionEvent event) {
                            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                                getParent().requestDisallowInterceptTouchEvent(true);
                            }
                            return super.onTouchEvent(event);
                        }
                    };
                    patternsListView.setLayoutManager(patternsLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
                    patternsListView.setAdapter(patternsAdapter = new PatternsAdapter(context));
                    patternsListView.addItemDecoration(new RecyclerView.ItemDecoration() {
                        @Override
                        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                            int position = parent.getChildAdapterPosition(view);
                            outRect.left = AndroidUtilities.dp(12);
                            outRect.bottom = outRect.top = 0;
                            if (position == state.getItemCount() - 1) {
                                outRect.right = AndroidUtilities.dp(12);
                            }
                        }
                    });
                    patternLayout[a].addView(patternsListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.LEFT | Gravity.TOP, 0, 14, 0, 0));
                    patternsListView.setOnItemClickListener((view, position) -> {
                        boolean previousMotion = selectedPattern != null;
                        if (position == 0) {
                            backgroundImage.setImageDrawable(null);
                            selectedPattern = null;
                            isMotion = false;
                            updateButtonState(radialProgress, null, WallpaperActivity.this, false, true);
                        } else {
                            TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) patterns.get(position - 1);
                            backgroundImage.setImage(ImageLocation.getForDocument(wallPaper.document), imageFilter, null, null, "jpg", wallPaper.document.size, 1, wallPaper);
                            selectedPattern = wallPaper;
                            isMotion = checkBoxView[2].isChecked();
                            updateButtonState(radialProgress, null, WallpaperActivity.this, false, true);
                        }
                        if (previousMotion == (selectedPattern == null)) {
                            animateMotionChange();
                            updateMotionButton();
                        }
                        updateSelectedPattern(true);
                        checkBoxView[1].setChecked(selectedPattern != null, true);
                        patternsListView.invalidateViews();
                    });

                    intensityCell = new HeaderCell(context);
                    intensityCell.setText(LocaleController.getString("BackgroundIntensity", R.string.BackgroundIntensity));
                    patternLayout[a].addView(intensityCell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 113, 0, 0));

                    intensitySeekBar = new SeekBarView(context) {
                        @Override
                        public boolean onTouchEvent(MotionEvent event) {
                            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                                getParent().requestDisallowInterceptTouchEvent(true);
                            }
                            return super.onTouchEvent(event);
                        }
                    };
                    intensitySeekBar.setProgress(currentIntensity);
                    intensitySeekBar.setReportChanges(true);
                    intensitySeekBar.setDelegate(progress -> {
                        currentIntensity = progress;
                        backgroundImage.getImageReceiver().setAlpha(currentIntensity);
                        backgroundImage.invalidate();
                        patternsListView.invalidateViews();
                    });
                    patternLayout[a].addView(intensitySeekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, Gravity.TOP | Gravity.LEFT, 9, 153, 9, 0));
                } else {
                    colorPicker = new ColorPicker(context, this::setBackgroundColor);
                    patternLayout[a].addView(colorPicker, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 48));
                }
            }
        }

        setCurrentImage(true);
        updateButtonState(radialProgress, null, this, false, false);
        if (!backgroundImage.getImageReceiver().hasBitmapImage()) {
            fragmentView.setBackgroundColor(0xff000000);
        }

        if (!(currentWallpaper instanceof WallpapersListActivity.ColorWallpaper)) {
            backgroundImage.getImageReceiver().setCrossfadeWithOldImage(true);
            backgroundImage.getImageReceiver().setForceCrossfade(true);
        }

        return fragmentView;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.wallpapersNeedReload) {
            if (currentWallpaper instanceof WallpapersListActivity.FileWallpaper) {
                WallpapersListActivity.FileWallpaper fileWallpaper = (WallpapersListActivity.FileWallpaper) currentWallpaper;
                if (fileWallpaper.id == -1) {
                    fileWallpaper.id = (Long) args[0];
                }
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isMotion) {
            parallaxEffect.setEnabled(true);
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isMotion) {
            parallaxEffect.setEnabled(false);
        }
    }

    @Override
    public void onFailedDownload(String fileName, boolean canceled) {
        updateButtonState(radialProgress, null, this, true, canceled);
    }

    @Override
    public void onSuccessDownload(String fileName) {
        radialProgress.setProgress(1, progressVisible);
        updateButtonState(radialProgress, null, this, false,true);
    }

    @Override
    public void onProgressDownload(String fileName, float progress) {
        radialProgress.setProgress(progress, progressVisible);
        if (radialProgress.getIcon() != MediaActionDrawable.ICON_EMPTY) {
            updateButtonState(radialProgress, null, this, false, true);
        }
    }

    @Override
    public void onProgressUpload(String fileName, float progress, boolean isEncrypted) {

    }

    @Override
    public int getObserverTag() {
        return TAG;
    }

    private void updateBlurred() {
        if (isBlurred && blurredBitmap == null) {
            if (currentWallpaperBitmap != null) {
                blurredBitmap = Utilities.blurWallpaper(currentWallpaperBitmap);
            } else {
                ImageReceiver imageReceiver = backgroundImage.getImageReceiver();
                if (imageReceiver.hasNotThumb() || imageReceiver.hasStaticThumb()) {
                    blurredBitmap = Utilities.blurWallpaper(imageReceiver.getBitmap());
                }
            }
        }
        if (isBlurred) {
            if (blurredBitmap != null) {
                backgroundImage.setImageBitmap(blurredBitmap);
            }
        } else {
            setCurrentImage(false);
        }
    }

    private void updateButtonState(RadialProgress2 radial, Object image, DownloadController.FileDownloadProgressListener listener, boolean ifSame, boolean animated) {
        Object object;
        if (listener == this) {
            if (selectedPattern != null) {
                object = selectedPattern;
            } else {
                object = currentWallpaper;
            }
        } else {
            object = image;
        }
        if (object instanceof TLRPC.TL_wallPaper || object instanceof MediaController.SearchImage) {
            if (image == null) {
                if (animated && !progressVisible) {
                    animated = false;
                }
            }
            boolean fileExists;
            File path;
            int size;
            String fileName;
            if (object instanceof TLRPC.TL_wallPaper) {
                TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) object;
                fileName = FileLoader.getAttachFileName(wallPaper.document);
                if (TextUtils.isEmpty(fileName)) {
                    return;
                }
                path = FileLoader.getPathToAttach(wallPaper.document, true);
                size = wallPaper.document.size;
            } else {
                MediaController.SearchImage wallPaper = (MediaController.SearchImage) object;
                if (wallPaper.photo != null) {
                    TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(wallPaper.photo.sizes, maxWallpaperSize, true);
                    path = FileLoader.getPathToAttach(photoSize, true);
                    fileName = FileLoader.getAttachFileName(photoSize);
                    size = photoSize.size;
                } else {
                    path = ImageLoader.getHttpFilePath(wallPaper.imageUrl, "jpg");
                    fileName = path.getName();
                    size = wallPaper.size;
                }
                if (TextUtils.isEmpty(fileName)) {
                    return;
                }
            }
            if (fileExists = path.exists()) {
                DownloadController.getInstance(currentAccount).removeLoadingFileObserver(listener);
                radial.setProgress(1, animated);
                radial.setIcon(image == null ? MediaActionDrawable.ICON_NONE : MediaActionDrawable.ICON_CHECK, ifSame, animated);
                if (image == null) {
                    backgroundImage.invalidate();
                    if (size != 0) {
                        actionBar.setSubtitle(AndroidUtilities.formatFileSize(size));
                    } else {
                        actionBar.setSubtitle(null);
                    }
                }
            } else {
                DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, null, listener);
                boolean isLoading = FileLoader.getInstance(currentAccount).isLoadingFile(fileName);
                Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                if (progress != null) {
                    radial.setProgress(progress, animated);
                } else {
                    radial.setProgress(0, animated);
                }
                radial.setIcon(MediaActionDrawable.ICON_EMPTY, ifSame, animated);
                if (image == null) {
                    actionBar.setSubtitle(LocaleController.getString("LoadingFullImage", R.string.LoadingFullImage));
                    backgroundImage.invalidate();
                }
            }
            if (image == null) {
                if (selectedPattern == null) {
                    buttonsContainer.setAlpha(fileExists ? 1.0f : 0.5f);
                }
                bottomOverlayChat.setEnabled(fileExists);
                bottomOverlayChatText.setAlpha(fileExists ? 1.0f : 0.5f);
            }
        } else {
            radial.setIcon(listener == this ? MediaActionDrawable.ICON_NONE : MediaActionDrawable.ICON_CHECK, ifSame, animated);
        }
    }

    public void setDelegate(WallpaperActivityDelegate wallpaperActivityDelegate) {
        delegate = wallpaperActivityDelegate;
    }

    public void setPatterns(ArrayList<Object> arrayList) {
        patterns = arrayList;
        if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
            WallpapersListActivity.ColorWallpaper wallPaper = (WallpapersListActivity.ColorWallpaper) currentWallpaper;
            if (wallPaper.patternId != 0) {
                for (int a = 0, N = patterns.size(); a < N; a++) {
                    TLRPC.TL_wallPaper pattern = (TLRPC.TL_wallPaper) patterns.get(a);
                    if (pattern.id == wallPaper.patternId) {
                        selectedPattern = pattern;
                        break;
                    }
                }
                currentIntensity = wallPaper.intensity;
            }
        }
    }

    private void updateSelectedPattern(boolean animated) {
        int count = patternsListView.getChildCount();
        for (int a = 0; a < count; a++) {
            View child = patternsListView.getChildAt(a);
            if (child instanceof PatternCell) {
                ((PatternCell) child).updateSelected(animated);
            }
        }
    }

    private void updateMotionButton() {
        checkBoxView[selectedPattern != null ? 2 : 0].setVisibility(View.VISIBLE);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(checkBoxView[2], View.ALPHA, selectedPattern != null ? 1.0f : 0.0f),
                ObjectAnimator.ofFloat(checkBoxView[0], View.ALPHA, selectedPattern != null ? 0.0f : 1.0f));
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                checkBoxView[selectedPattern != null ? 0 : 2].setVisibility(View.INVISIBLE);
            }
        });
        animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        animatorSet.setDuration(200);
        animatorSet.start();
    }

    private void showPatternsView(int num, boolean show) {
        boolean showMotion = show && num == 1 && selectedPattern != null;
        if (show) {
            if (num == 0) {
                previousBackgroundColor = backgroundColor;
                colorPicker.setColor(backgroundColor);
            } else {
                previousSelectedPattern = selectedPattern;
                previousIntensity = currentIntensity;
                patternsAdapter.notifyDataSetChanged();
                if (patterns != null) {
                    int index;
                    if (selectedPattern == null) {
                        index = 0;
                    } else {
                        index = patterns.indexOf(selectedPattern) + 1;
                    }
                    patternsLayoutManager.scrollToPositionWithOffset(index, (patternsListView.getMeasuredWidth() - AndroidUtilities.dp(100) - AndroidUtilities.dp(12)) / 2);
                }
            }
        }
        checkBoxView[showMotion ? 2 : 0].setVisibility(View.VISIBLE);
        AnimatorSet animatorSet = new AnimatorSet();
        ArrayList<Animator> animators = new ArrayList<>();
        int otherNum = num == 0 ? 1 : 0;
        if (show) {
            patternLayout[num].setVisibility(View.VISIBLE);
            animators.add(ObjectAnimator.ofFloat(listView, View.TRANSLATION_Y, -patternLayout[num].getMeasuredHeight() + AndroidUtilities.dp(48)));
            animators.add(ObjectAnimator.ofFloat(buttonsContainer, View.TRANSLATION_Y, -patternLayout[num].getMeasuredHeight() + AndroidUtilities.dp(48)));
            animators.add(ObjectAnimator.ofFloat(checkBoxView[2], View.ALPHA, showMotion ? 1.0f : 0.0f));
            animators.add(ObjectAnimator.ofFloat(checkBoxView[0], View.ALPHA, showMotion ? 0.0f : 1.0f));
            animators.add(ObjectAnimator.ofFloat(backgroundImage, View.ALPHA, 0.0f));
            if (patternLayout[otherNum].getVisibility() == View.VISIBLE) {
                animators.add(ObjectAnimator.ofFloat(patternLayout[otherNum], View.ALPHA, 0.0f));
                animators.add(ObjectAnimator.ofFloat(patternLayout[num], View.ALPHA, 0.0f, 1.0f));
                patternLayout[num].setTranslationY(0);
            } else {
                animators.add(ObjectAnimator.ofFloat(patternLayout[num], View.TRANSLATION_Y, patternLayout[num].getMeasuredHeight(), 0));
            }
        } else {
            animators.add(ObjectAnimator.ofFloat(listView, View.TRANSLATION_Y, 0));
            animators.add(ObjectAnimator.ofFloat(buttonsContainer, View.TRANSLATION_Y, 0));
            animators.add(ObjectAnimator.ofFloat(patternLayout[num], View.TRANSLATION_Y, patternLayout[num].getMeasuredHeight()));
            animators.add(ObjectAnimator.ofFloat(checkBoxView[0], View.ALPHA, 1.0f));
            animators.add(ObjectAnimator.ofFloat(checkBoxView[2], View.ALPHA, 0.0f));
            animators.add(ObjectAnimator.ofFloat(backgroundImage, View.ALPHA, 1.0f));
        }
        animatorSet.playTogether(animators);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (show && patternLayout[otherNum].getVisibility() == View.VISIBLE) {
                    patternLayout[otherNum].setAlpha(1.0f);
                    patternLayout[otherNum].setVisibility(View.INVISIBLE);
                } else if (!show) {
                    patternLayout[num].setVisibility(View.INVISIBLE);
                }
                checkBoxView[showMotion ? 0 : 2].setVisibility(View.INVISIBLE);
            }
        });
        animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        animatorSet.setDuration(200);
        animatorSet.start();
    }

    private void animateMotionChange() {
        if (motionAnimation != null) {
            motionAnimation.cancel();
        }
        motionAnimation = new AnimatorSet();
        if (isMotion) {
            motionAnimation.playTogether(
                    ObjectAnimator.ofFloat(backgroundImage, View.SCALE_X, parallaxScale),
                    ObjectAnimator.ofFloat(backgroundImage, View.SCALE_Y, parallaxScale));
        } else {
            motionAnimation.playTogether(
                    ObjectAnimator.ofFloat(backgroundImage, View.SCALE_X, 1.0f),
                    ObjectAnimator.ofFloat(backgroundImage, View.SCALE_Y, 1.0f),
                    ObjectAnimator.ofFloat(backgroundImage, View.TRANSLATION_X, 0.0f),
                    ObjectAnimator.ofFloat(backgroundImage, View.TRANSLATION_Y, 0.0f));
        }
        motionAnimation.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        motionAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                motionAnimation = null;
            }
        });
        motionAnimation.start();
    }

    private void setBackgroundColor(int color) {
        backgroundColor = color;
        backgroundImage.setBackgroundColor(backgroundColor);
        if (checkBoxView[0] != null) {
            checkBoxView[0].invalidate();
        }
        patternColor = AndroidUtilities.getPatternColor(backgroundColor);
        Theme.applyChatServiceMessageColor(new int[]{patternColor, patternColor, patternColor, patternColor});

        if (backgroundImage != null) {
            backgroundImage.getImageReceiver().setColorFilter(new PorterDuffColorFilter(patternColor, blendMode));
            backgroundImage.getImageReceiver().setAlpha(currentIntensity);
            backgroundImage.invalidate();
        }
        if (listView != null) {
            listView.invalidateViews();
        }
        if (buttonsContainer != null) {
            for (int a = 0, N = buttonsContainer.getChildCount(); a < N; a++) {
                buttonsContainer.getChildAt(a).invalidate();
            }
        }
        if (radialProgress != null) {
            radialProgress.setColors(Theme.key_chat_serviceBackground, Theme.key_chat_serviceBackground, Theme.key_chat_serviceText, Theme.key_chat_serviceText);
        }
    }

    private void setCurrentImage(boolean setThumb) {
        if (currentWallpaper instanceof TLRPC.TL_wallPaper) {
            TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) currentWallpaper;
            TLRPC.PhotoSize thumb = setThumb ? FileLoader.getClosestPhotoSizeWithSize(wallPaper.document.thumbs, 100) : null;
            backgroundImage.setImage(ImageLocation.getForDocument(wallPaper.document), imageFilter, ImageLocation.getForDocument(thumb, wallPaper.document), "100_100_b", "jpg", wallPaper.document.size, 1, wallPaper);
        } else if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
            WallpapersListActivity.ColorWallpaper wallPaper = (WallpapersListActivity.ColorWallpaper) currentWallpaper;
            setBackgroundColor(wallPaper.color);
            if (selectedPattern != null) {
                backgroundImage.setImage(ImageLocation.getForDocument(selectedPattern.document), imageFilter, null, null, "jpg", selectedPattern.document.size, 1, selectedPattern);
            }
        } else if (currentWallpaper instanceof WallpapersListActivity.FileWallpaper) {
            if (currentWallpaperBitmap != null) {
                backgroundImage.setImageBitmap(currentWallpaperBitmap);
            } else {
                WallpapersListActivity.FileWallpaper wallPaper = (WallpapersListActivity.FileWallpaper) currentWallpaper;
                if (wallPaper.originalPath != null) {
                    backgroundImage.setImage(wallPaper.originalPath.getAbsolutePath(), imageFilter, null);
                } else if (wallPaper.path != null) {
                    backgroundImage.setImage(wallPaper.path.getAbsolutePath(), imageFilter, null);
                } else if (wallPaper.resId == Theme.THEME_BACKGROUND_ID) {
                    backgroundImage.setImageDrawable(Theme.getThemedWallpaper(false));
                } else if (wallPaper.resId != 0) {
                    backgroundImage.setImageResource(wallPaper.resId);
                }
            }
        } else if (currentWallpaper instanceof MediaController.SearchImage) {
            MediaController.SearchImage wallPaper = (MediaController.SearchImage) currentWallpaper;
            if (wallPaper.photo != null) {
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(wallPaper.photo.sizes, 100);
                TLRPC.PhotoSize image = FileLoader.getClosestPhotoSizeWithSize(wallPaper.photo.sizes, maxWallpaperSize, true);
                if (image == thumb) {
                    image = null;
                }
                int size = image != null ? image.size : 0;
                backgroundImage.setImage(ImageLocation.getForPhoto(image, wallPaper.photo), imageFilter, ImageLocation.getForPhoto(thumb, wallPaper.photo), "100_100_b", "jpg", size, 1, wallPaper);
            } else {
                backgroundImage.setImage(wallPaper.imageUrl, imageFilter, wallPaper.thumbUrl, "100_100_b");
            }
        }
    }

    private class PatternsAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public PatternsAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemViewType(int position) {
            return super.getItemViewType(position);
        }

        @Override
        public int getItemCount() {
            return (patterns != null ? patterns.size() : 0) + 1;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            PatternCell view = new PatternCell(mContext);
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            PatternCell view = (PatternCell) holder.itemView;
            if (position == 0) {
                view.setPattern(null);
            } else {
                position--;
                view.setPattern((TLRPC.TL_wallPaper) patterns.get(position));
            }
            view.getImageReceiver().setColorFilter(new PorterDuffColorFilter(patternColor, blendMode));
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private ArrayList<MessageObject> messages;
        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;

            messages = new ArrayList<>();

            int date = (int) (System.currentTimeMillis() / 1000) - 60 * 60;

            TLRPC.Message message;

            message = new TLRPC.TL_message();
            if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
                message.message = LocaleController.getString("BackgroundColorSinglePreviewLine2", R.string.BackgroundColorSinglePreviewLine2);
                //message.message = LocaleController.getString("BackgroundColorPreviewLine2", R.string.BackgroundColorPreviewLine2);
            } else {
                message.message = LocaleController.getString("BackgroundPreviewLine2", R.string.BackgroundPreviewLine2);
            }
            message.date = date + 60;
            message.dialog_id = 1;
            message.flags = 259;
            message.from_id = UserConfig.getInstance(currentAccount).getClientUserId();
            message.id = 1;
            message.media = new TLRPC.TL_messageMediaEmpty();
            message.out = true;
            message.to_id = new TLRPC.TL_peerUser();
            message.to_id.user_id = 0;
            MessageObject messageObject = new MessageObject(currentAccount, message, true);
            messageObject.eventId = 1;
            messageObject.resetLayout();
            messages.add(messageObject);

            message = new TLRPC.TL_message();
            if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
                message.message = LocaleController.getString("BackgroundColorSinglePreviewLine1", R.string.BackgroundColorSinglePreviewLine1);
                //message.message = LocaleController.getString("BackgroundColorPreviewLine1", R.string.BackgroundColorPreviewLine1);
            } else {
                message.message = LocaleController.getString("BackgroundPreviewLine1", R.string.BackgroundPreviewLine1);
            }
            message.date = date + 60;
            message.dialog_id = 1;
            message.flags = 257 + 8;
            message.from_id = 0;
            message.id = 1;
            message.reply_to_msg_id = 5;
            message.media = new TLRPC.TL_messageMediaEmpty();
            message.out = false;
            message.to_id = new TLRPC.TL_peerUser();
            message.to_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
            messageObject = new MessageObject(currentAccount, message, true);
            messageObject.eventId = 1;
            messageObject.resetLayout();
            messages.add(messageObject);

            message = new TLRPC.TL_message();
            message.message = LocaleController.formatDateChat(date);
            message.id = 0;
            message.date = date;
            messageObject = new MessageObject(currentAccount, message, false);
            messageObject.type = 10;
            messageObject.contentType = 1;
            messageObject.isDateObject = true;
            messages.add(messageObject);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
            View view = null;
            if (viewType == 0) {
                view = new ChatMessageCell(mContext);
                ChatMessageCell chatMessageCell = (ChatMessageCell) view;
                chatMessageCell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {

                });
            } else if (viewType == 1) {
                view = new ChatActionCell(mContext);
                ((ChatActionCell) view).setDelegate(new ChatActionCell.ChatActionCellDelegate() {

                });
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int i) {
            if (i >= 0 && i < messages.size()) {
                return messages.get(i).contentType;
            }
            return 4;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            MessageObject message = messages.get(position);
            View view = holder.itemView;

            if (view instanceof ChatMessageCell) {
                ChatMessageCell messageCell = (ChatMessageCell) view;
                messageCell.isChat = false;
                int nextType = getItemViewType(position - 1);
                int prevType = getItemViewType(position + 1);
                boolean pinnedBotton;
                boolean pinnedTop;
                if (!(message.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) && nextType == holder.getItemViewType()) {
                    MessageObject nextMessage = messages.get(position - 1);
                    pinnedBotton = nextMessage.isOutOwner() == message.isOutOwner() && Math.abs(nextMessage.messageOwner.date - message.messageOwner.date) <= 5 * 60;
                } else {
                    pinnedBotton = false;
                }
                if (prevType == holder.getItemViewType()) {
                    MessageObject prevMessage = messages.get(position + 1);
                    pinnedTop = !(prevMessage.messageOwner.reply_markup instanceof TLRPC.TL_replyInlineMarkup) && prevMessage.isOutOwner() == message.isOutOwner() && Math.abs(prevMessage.messageOwner.date - message.messageOwner.date) <= 5 * 60;
                } else {
                    pinnedTop = false;
                }
                messageCell.setFullyDraw(true);
                messageCell.setMessageObject(message, null, pinnedBotton, pinnedTop);
            } else if (view instanceof ChatActionCell) {
                ChatActionCell actionCell = (ChatActionCell) view;
                actionCell.setMessageObject(message);
                actionCell.setAlpha(1.0f);
            }
        }
    }

    @Override
    public ThemeDescription[] getThemeDescriptions() {
        ArrayList<ThemeDescription> arrayList = new ArrayList<>();

        arrayList.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        arrayList.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        arrayList.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        for (int a = 0; a < patternLayout.length; a++) {
            arrayList.add(new ThemeDescription(patternLayout[a], 0, null, null, new Drawable[]{Theme.chat_composeShadowDrawable}, null, Theme.key_chat_messagePanelShadow));
            arrayList.add(new ThemeDescription(patternLayout[a], 0, null, Theme.chat_composeBackgroundPaint, null, null, Theme.key_chat_messagePanelBackground));
        }

        for (int a = 0; a < patternsButtonsContainer.length; a++) {
            arrayList.add(new ThemeDescription(patternsButtonsContainer[a], 0, null, null, new Drawable[]{Theme.chat_composeShadowDrawable}, null, Theme.key_chat_messagePanelShadow));
            arrayList.add(new ThemeDescription(patternsButtonsContainer[a], 0, null, Theme.chat_composeBackgroundPaint, null, null, Theme.key_chat_messagePanelBackground));
        }


        arrayList.add(new ThemeDescription(bottomOverlayChat, 0, null, null, new Drawable[]{Theme.chat_composeShadowDrawable}, null, Theme.key_chat_messagePanelShadow));
        arrayList.add(new ThemeDescription(bottomOverlayChat, 0, null, Theme.chat_composeBackgroundPaint, null, null, Theme.key_chat_messagePanelBackground));
        arrayList.add(new ThemeDescription(bottomOverlayChatText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_fieldOverlayText));

        for (int a = 0; a < patternsSaveButton.length; a++) {
            arrayList.add(new ThemeDescription(patternsSaveButton[a], ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_fieldOverlayText));
        }
        for (int a = 0; a < patternsCancelButton.length; a++) {
            arrayList.add(new ThemeDescription(patternsCancelButton[a], ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_fieldOverlayText));
        }

        if (colorPicker != null) {
            colorPicker.provideThemeDescriptions(arrayList);
        }

        arrayList.add(new ThemeDescription(intensitySeekBar, 0, new Class[]{SeekBarView.class}, new String[]{"innerPaint1"}, null, null, null, Theme.key_player_progressBackground));
        arrayList.add(new ThemeDescription(intensitySeekBar, 0, new Class[]{SeekBarView.class}, new String[]{"outerPaint1"}, null, null, null, Theme.key_player_progress));

        arrayList.add(new ThemeDescription(intensityCell, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgInDrawable, Theme.chat_msgInMediaDrawable}, null, Theme.key_chat_inBubble));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgInSelectedDrawable, Theme.chat_msgInMediaSelectedDrawable}, null, Theme.key_chat_inBubbleSelected));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgInShadowDrawable, Theme.chat_msgInMediaShadowDrawable}, null, Theme.key_chat_inBubbleShadow));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutDrawable, Theme.chat_msgOutMediaDrawable}, null, Theme.key_chat_outBubble));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutSelectedDrawable, Theme.chat_msgOutMediaSelectedDrawable}, null, Theme.key_chat_outBubbleSelected));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutShadowDrawable, Theme.chat_msgOutMediaShadowDrawable}, null, Theme.key_chat_outBubbleShadow));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_messageTextIn));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_messageTextOut));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckDrawable}, null, Theme.key_chat_outSentCheck));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckSelectedDrawable}, null, Theme.key_chat_outSentCheckSelected));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckReadDrawable, Theme.chat_msgOutHalfCheckDrawable}, null, Theme.key_chat_outSentCheckRead));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgOutCheckReadSelectedDrawable, Theme.chat_msgOutHalfCheckSelectedDrawable}, null, Theme.key_chat_outSentCheckReadSelected));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ChatMessageCell.class}, null, new Drawable[]{Theme.chat_msgMediaCheckDrawable, Theme.chat_msgMediaHalfCheckDrawable}, null, Theme.key_chat_mediaSentCheck));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inReplyLine));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outReplyLine));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inReplyNameText));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outReplyNameText));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inReplyMessageText));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outReplyMessageText));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inReplyMediaMessageSelectedText));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outReplyMediaMessageSelectedText));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inTimeText));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outTimeText));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_inTimeSelectedText));
        arrayList.add(new ThemeDescription(listView, 0, new Class[]{ChatMessageCell.class}, null, null, null, Theme.key_chat_outTimeSelectedText));

        return arrayList.toArray(new ThemeDescription[0]);
    }
}
