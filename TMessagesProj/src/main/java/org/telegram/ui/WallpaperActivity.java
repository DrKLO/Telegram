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
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.util.Property;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.support.widget.LinearLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.ConnectionsManager;
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
import org.telegram.ui.Components.AnimationProperties;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.MediaActionDrawable;
import org.telegram.ui.Components.RadialProgress2;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.Components.WallpaperParallaxEffect;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

public class WallpaperActivity extends BaseFragment implements DownloadController.FileDownloadProgressListener {

    private RecyclerListView listView;
    private BackupImageView backgroundImage;
    private LinearLayout buttonsContainer;
    private File wallpaperFile;
    private Drawable themedWallpaper;
    private AnimatorSet motionAnimation;
    private RadialProgress2 radialProgress;
    private FrameLayout bottomOverlayChat;

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

    private WallpaperActivityDelegate delegate;

    public interface WallpaperActivityDelegate {
        void didSetNewBackground();
    }

    private class CheckBoxView extends View {

        private String currentText;
        private int currentTextSize;
        private int maxTextSize;
        private RectF rect;

        private boolean isChecked;
        private Bitmap drawBitmap;
        private Canvas drawCanvas;
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

        public CheckBoxView(Context context) {
            super(context);
            rect = new RectF();

            drawBitmap = Bitmap.createBitmap(AndroidUtilities.dp(18), AndroidUtilities.dp(18), Bitmap.Config.ARGB_4444);
            drawCanvas = new Canvas(drawBitmap);
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
    }

    public void setInitialModes(int modes) {
        isBlurred = (modes & 1) != 0;
        isMotion = (modes & 2) != 0;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

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
        backgroundPaint.setColor(0xffffffff);

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
                    if (currentWallpaper instanceof TLRPC.TL_wallPaper) {
                        TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) currentWallpaper;
                        link = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/bg/" + wallPaper.slug;
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
                        if (modes.length() > 0) {
                            link += "?mode=" + modes.toString();
                        }
                    } else if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
                        WallpapersListActivity.ColorWallpaper colorWallpaper = (WallpapersListActivity.ColorWallpaper) currentWallpaper;
                        link = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/bg/" + String.format("%02X%02X%02X", (byte) colorWallpaper.color, (byte) colorWallpaper.color >> 8, (byte) colorWallpaper.color >> 16);
                    } else {
                        return;
                    }
                    showDialog(new ShareAlert(getParentActivity(), null, link, false, link, false));
                }
            }
        });

        if (currentWallpaper != null && !WallpapersListActivity.disableFeatures) {
            ActionBarMenu menu = actionBar.createMenu();
            menu.addItem(share_item, R.drawable.ic_share_video);
        }

        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;


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
        };
        backgroundImage.getImageReceiver().setCrossfadeWithOldImage(true);
        backgroundImage.getImageReceiver().setForceCrossfade(true);
        frameLayout.addView(backgroundImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 48));
        backgroundImage.getImageReceiver().setDelegate((imageReceiver, set, thumb) -> {
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
                    updateBlurred();
                }
            }
        });

        radialProgress = new RadialProgress2(backgroundImage);
        radialProgress.setColors(Theme.key_chat_serviceBackground, Theme.key_chat_serviceBackground, Theme.key_chat_serviceText, Theme.key_chat_serviceText);

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, true));
        listView.setOverScrollMode(RecyclerListView.OVER_SCROLL_NEVER);
        listView.setAdapter(new ListAdapter(context));
        if (WallpapersListActivity.disableFeatures || currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
            listView.setPadding(0, 0, 0, AndroidUtilities.dp(4));
        } else {
            listView.setPadding(0, 0, 0, AndroidUtilities.dp(4 + 60));
        }
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
            File toFile = new File(ApplicationLoader.getFilesDirFixed(), "wallpaper.jpg");
            if (isBlurred) {
                try {
                    FileOutputStream stream = new FileOutputStream(toFile);
                    blurredBitmap.compress(Bitmap.CompressFormat.JPEG, 87, stream);
                    stream.close();
                    done = true;
                } catch (Throwable e) {
                    FileLog.e(e);
                    done = false;
                }
            } else {
                if (currentWallpaper instanceof TLRPC.TL_wallPaper) {
                    TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) currentWallpaper;
                    File f = FileLoader.getPathToAttach(wallPaper.document, true);
                    try {
                        done = AndroidUtilities.copyFile(f, toFile);
                    } catch (Exception e) {
                        done = false;
                        FileLog.e(e);
                    }
                } else if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
                    done = true;
                } else if (currentWallpaper instanceof WallpapersListActivity.FileWallpaper) {
                    WallpapersListActivity.FileWallpaper wallpaper = (WallpapersListActivity.FileWallpaper) currentWallpaper;
                    if (wallpaper.resId != 0 || wallpaper.resId == Theme.THEME_BACKGROUND_ID) {
                        done = true;
                    } else {
                        try {
                            done = AndroidUtilities.copyFile(wallpaper.path, toFile);
                        } catch (Exception e) {
                            done = false;
                            FileLog.e(e);
                        }
                    }
                } else {
                    done = false;
                }
            }
            long id;
            int color;
            if (currentWallpaper instanceof TLRPC.TL_wallPaper) {
                TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) currentWallpaper;
                id = wallPaper.id;
                color = 0;

                if (!WallpapersListActivity.disableFeatures) {
                    TLRPC.TL_account_saveWallPaper req = new TLRPC.TL_account_saveWallPaper();
                    TLRPC.TL_inputWallPaper inputWallPaper = new TLRPC.TL_inputWallPaper();
                    inputWallPaper.id = wallPaper.id;
                    inputWallPaper.access_hash = wallPaper.access_hash;
                    req.wallpaper = inputWallPaper;
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

                    });
                }
            } else if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
                WallpapersListActivity.ColorWallpaper wallPaper = (WallpapersListActivity.ColorWallpaper) currentWallpaper;
                id = wallPaper.id;
                color = wallPaper.color;
            } else if (currentWallpaper instanceof WallpapersListActivity.FileWallpaper) {
                WallpapersListActivity.FileWallpaper wallPaper = (WallpapersListActivity.FileWallpaper) currentWallpaper;
                id = wallPaper.id;
                color = 0;
            } else {
                id = 0;
                color = 0;
            }

            if (done) {
                Theme.serviceMessageColorBackup = Theme.getColor(Theme.key_chat_serviceBackground);
                SharedPreferences preferences = MessagesController.getGlobalMainSettings();
                SharedPreferences.Editor editor = preferences.edit();
                editor.putLong("selectedBackground2", id);
                editor.putBoolean("selectedBackgroundBlurred", isBlurred);
                editor.putBoolean("selectedBackgroundMotion", isMotion);
                editor.putInt("selectedColor", color);
                editor.putBoolean("overrideThemeWallpaper", id != Theme.THEME_BACKGROUND_ID);
                editor.commit();
                Theme.reloadWallpaper();
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

        buttonsContainer = new LinearLayout(context);
        buttonsContainer.setOrientation(LinearLayout.HORIZONTAL);
        frameLayout.addView(buttonsContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 32, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 50 + 16));

        String text1 = LocaleController.getString("BackgroundBlurred", R.string.BackgroundBlurred);
        String text2 = LocaleController.getString("BackgroundMotion", R.string.BackgroundMotion);
        int textSize1 = (int) Math.ceil(textPaint.measureText(text1));
        int textSize2 = (int) Math.ceil(textPaint.measureText(text2));

        for (int a = 0; a < 2; a++) {
            final int num = a;
            CheckBoxView view = new CheckBoxView(context);
            view.setText(a == 0 ? text1 : text2, a == 0 ? textSize1 : textSize2, Math.max(textSize1, textSize2));
            view.setChecked(a == 0 ? isBlurred : isMotion, false);
            buttonsContainer.addView(view, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, a == 1 ? 9 : 0, 0, 0, 0));
            view.setOnClickListener(v -> {
                if (!bottomOverlayChat.isEnabled()) {
                    return;
                }
                view.setChecked(!view.isChecked(), true);
                if (num == 0) {
                    isBlurred = view.isChecked();
                    updateBlurred();
                } else {
                    isMotion = view.isChecked();
                    parallaxEffect.setEnabled(isMotion);
                    animateMotionChange();
                }
            });
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

        if (WallpapersListActivity.disableFeatures || currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
            buttonsContainer.setVisibility(View.GONE);
            isBlurred = false;
            isMotion = false;
        }

        setCurrentImage(true);
        updateButtonState(false, false);

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isMotion) {
            parallaxEffect.setEnabled(true);
        }
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
        updateButtonState(true, canceled);
    }

    @Override
    public void onSuccessDownload(String fileName) {
        radialProgress.setProgress(1, progressVisible);
        updateButtonState(false,true);
    }

    @Override
    public void onProgressDownload(String fileName, float progress) {
        radialProgress.setProgress(progress, progressVisible);
        if (radialProgress.getIcon() != MediaActionDrawable.ICON_CANCEL_PERCENT) {
            updateButtonState(false, true);
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
                if (backgroundImage.getImageReceiver().hasNotThumb()) {
                    blurredBitmap = Utilities.blurWallpaper(backgroundImage.getImageReceiver().getBitmap());
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

    private void updateButtonState(boolean ifSame, boolean animated) {
        if (currentWallpaper instanceof TLRPC.TL_wallPaper) {
            if (animated && !progressVisible) {
                animated = false;
            }
            TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) currentWallpaper;
            String fileName = FileLoader.getAttachFileName(wallPaper.document);
            if (TextUtils.isEmpty(fileName)) {
                return;
            }
            boolean fileExists;
            File path = FileLoader.getPathToAttach(wallPaper.document, true);
            if (fileExists = path.exists()) {
                DownloadController.getInstance(currentAccount).removeLoadingFileObserver(this);
                actionBar.setSubtitle(AndroidUtilities.formatFileSize(wallPaper.document.size));
                radialProgress.setProgress(1, animated);
                radialProgress.setIcon(MediaActionDrawable.ICON_NONE, ifSame, animated);
                backgroundImage.invalidate();
            } else {
                DownloadController.getInstance(currentAccount).addLoadingFileObserver(fileName, null, this);
                boolean isLoading = FileLoader.getInstance(currentAccount).isLoadingFile(fileName);
                Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                if (progress != null) {
                    radialProgress.setProgress(progress, animated);
                } else {
                    radialProgress.setProgress(0, animated);
                }
                radialProgress.setIcon(MediaActionDrawable.ICON_CANCEL_PERCENT, ifSame, animated);
                actionBar.setSubtitle(LocaleController.getString("LoadingFullImage", R.string.LoadingFullImage));

                backgroundImage.invalidate();
            }
            buttonsContainer.setAlpha(fileExists ? 1.0f : 0.5f);
            bottomOverlayChat.setEnabled(fileExists);
            bottomOverlayChatText.setAlpha(fileExists ? 1.0f : 0.5f);
        } else {
            radialProgress.setIcon(MediaActionDrawable.ICON_NONE, false, false);
        }
    }

    public void setDelegate(WallpaperActivityDelegate wallpaperActivityDelegate) {
        delegate = wallpaperActivityDelegate;
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

    private void setCurrentImage(boolean setThumb) {
        if (currentWallpaper instanceof TLRPC.TL_wallPaper) {
            TLRPC.TL_wallPaper wallPaper = (TLRPC.TL_wallPaper) currentWallpaper;
            TLRPC.PhotoSize thumb = setThumb ? FileLoader.getClosestPhotoSizeWithSize(wallPaper.document.thumbs, 100) : null;
            backgroundImage.setImage(wallPaper.document, "1920_1080", thumb, "100_100_b", "jpg", wallPaper.document.size, 1, currentWallpaper);
        } else if (currentWallpaper instanceof WallpapersListActivity.ColorWallpaper) {
            WallpapersListActivity.ColorWallpaper wallPaper = (WallpapersListActivity.ColorWallpaper) currentWallpaper;
            backgroundImage.setImageDrawable(new ColorDrawable(wallPaper.color));
        } else if (currentWallpaper instanceof WallpapersListActivity.FileWallpaper) {
            if (currentWallpaperBitmap != null) {
                backgroundImage.setImageBitmap(currentWallpaperBitmap);
            } else {
                WallpapersListActivity.FileWallpaper wallPaper = (WallpapersListActivity.FileWallpaper) currentWallpaper;
                if (wallPaper.path != null) {
                    backgroundImage.setImage(wallPaper.path.getAbsolutePath(), "1920_1080", null);
                } else if (wallPaper.resId == Theme.THEME_BACKGROUND_ID) {
                    backgroundImage.setImageDrawable(Theme.getThemedWallpaper(false));
                } else if (wallPaper.resId != 0) {
                    backgroundImage.setImageResource(wallPaper.resId);
                }
            }
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
            message.message = "I can't even take you seriously right now.";//LocaleController.getString("BackgroundPreviewLine2", R.string.BackgroundPreviewLine2);
            message.date = date + 60;
            message.dialog_id = 1;
            message.flags = 259;
            message.from_id = UserConfig.getInstance(currentAccount).getClientUserId();
            message.id = 1;
            message.media = new TLRPC.TL_messageMediaEmpty();
            message.out = true;
            message.to_id = new TLRPC.TL_peerUser();
            message.to_id.user_id = 0;
            messages.add(new MessageObject(currentAccount, message, true));

            message = new TLRPC.TL_message();
            message.message = "Ah, you kids today with techno music! You should enjoy the classics, like Hasselhoff!";//LocaleController.getString("BackgroundPreviewLine1", R.string.BackgroundPreviewLine1);
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
            messages.add(new MessageObject(currentAccount, message, true));

            message = new TLRPC.TL_message();
            message.message = LocaleController.formatDateChat(date);
            message.id = 0;
            message.date = date;
            MessageObject messageObject = new MessageObject(currentAccount, message, false);
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
                    @Override
                    public void didPressShare(ChatMessageCell cell) {

                    }

                    @Override
                    public boolean needPlayMessage(MessageObject messageObject) {
                        return false;
                    }

                    @Override
                    public void didPressChannelAvatar(ChatMessageCell cell, TLRPC.Chat chat, int postId) {

                    }

                    @Override
                    public void didPressOther(ChatMessageCell cell) {

                    }

                    @Override
                    public void didPressUserAvatar(ChatMessageCell cell, TLRPC.User user) {

                    }

                    @Override
                    public void didPressBotButton(ChatMessageCell cell, TLRPC.KeyboardButton button) {

                    }

                    @Override
                    public void didPressVoteButton(ChatMessageCell cell, TLRPC.TL_pollAnswer button) {

                    }

                    @Override
                    public void didPressCancelSendButton(ChatMessageCell cell) {

                    }

                    @Override
                    public void didLongPress(ChatMessageCell cell) {

                    }

                    @Override
                    public boolean canPerformActions() {
                        return false;
                    }

                    @Override
                    public void didPressUrl(MessageObject messageObject, final CharacterStyle url, boolean longPress) {

                    }

                    @Override
                    public void needOpenWebView(String url, String title, String description, String originalUrl, int w, int h) {

                    }

                    @Override
                    public void didPressReplyMessage(ChatMessageCell cell, int id) {

                    }

                    @Override
                    public void didPressViaBot(ChatMessageCell cell, String username) {

                    }

                    @Override
                    public void didPressImage(ChatMessageCell cell) {

                    }

                    @Override
                    public void didPressInstantButton(ChatMessageCell cell, int type) {

                    }

                    @Override
                    public boolean isChatAdminCell(int uid) {
                        return false;
                    }
                });
            } else if (viewType == 1) {
                view = new ChatActionCell(mContext);
                ((ChatActionCell) view).setDelegate(new ChatActionCell.ChatActionCellDelegate() {
                    @Override
                    public void didClickedImage(ChatActionCell cell) {

                    }

                    @Override
                    public void didLongPressed(ChatActionCell cell) {

                    }

                    @Override
                    public void needOpenUserProfile(int uid) {

                    }

                    @Override
                    public void didPressedReplyMessage(ChatActionCell cell, int id) {

                    }

                    @Override
                    public void didPressedBotButton(MessageObject messageObject, TLRPC.KeyboardButton button) {

                    }
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
        return new ThemeDescription[]{
                new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite),

                new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle),
                new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector),

                new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector),
        };
    }
}
