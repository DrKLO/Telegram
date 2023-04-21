/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Parcelable;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.transition.TransitionValues;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FileRefController;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.FeaturedStickerSetInfoCell;
import org.telegram.ui.Cells.StickerEmojiCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Premium.PremiumButtonView;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.ContentPreviewViewer;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.ProfileActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StickersAlert extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    public interface StickersAlertDelegate {
        void onStickerSelected(TLRPC.Document sticker, String query, Object parent, MessageObject.SendAnimationData sendAnimationData, boolean clearsInputField, boolean notify, int scheduleDate);
        boolean canSchedule();
        boolean isInScheduleMode();
    }

    public interface StickersAlertInstallDelegate {
        void onStickerSetInstalled();
        void onStickerSetUninstalled();
    }

    public interface StickersAlertCustomButtonDelegate {
        String getCustomButtonTextColorKey();
        String getCustomButtonRippleColorKey();
        String getCustomButtonColorKey();
        String getCustomButtonText();
        boolean onCustomButtonPressed();
    }

    private boolean wasLightStatusBar;

    private Pattern urlPattern;
    private RecyclerListView gridView;
    private GridAdapter adapter;
    private LinkSpanDrawable.LinksTextView titleTextView;
    private TextView descriptionTextView;
    private ActionBarMenuItem optionsButton;
    private TextView pickerBottomLayout;
    private PremiumButtonView premiumButtonView;
    private FrameLayout pickerBottomFrameLayout;
    private FrameLayout stickerPreviewLayout;
    private TextView previewSendButton;
    private View previewSendButtonShadow;
    private BackupImageView stickerImageView;
    private TextView stickerEmojiTextView;
    private RecyclerListView.OnItemClickListener stickersOnItemClickListener;
    private AnimatorSet[] shadowAnimation = new AnimatorSet[2];
    private View[] shadow = new View[2];
    private FrameLayout emptyView;
    private BaseFragment parentFragment;
    private GridLayoutManager layoutManager;
    private Activity parentActivity;
    private int itemSize, itemHeight;
    public boolean probablyEmojis;

    private TLRPC.TL_messages_stickerSet stickerSet;
    private TLRPC.Document selectedSticker;
    private SendMessagesHelper.ImportingSticker selectedStickerPath;
    private TLRPC.InputStickerSet inputStickerSet;
    private ArrayList<TLRPC.StickerSetCovered> stickerSetCovereds;
    private ArrayList<Parcelable> importingStickers;
    private ArrayList<SendMessagesHelper.ImportingSticker> importingStickersPaths;
    private HashMap<String, SendMessagesHelper.ImportingSticker> uploadImportStickers;
    private String importingSoftware;

    private StickersAlertDelegate delegate;
    private StickersAlertInstallDelegate installDelegate;
    private StickersAlertCustomButtonDelegate customButtonDelegate;

    private int scrollOffsetY;
    private int reqId;
    private boolean ignoreLayout;
    private boolean showEmoji;

    private boolean clearsInputField;

    private boolean showTooltipWhenToggle = true;
    private String buttonTextColorKey;

    private ContentPreviewViewer.ContentPreviewViewerDelegate previewDelegate = new ContentPreviewViewer.ContentPreviewViewerDelegate() {
        @Override
        public boolean can() {
            return stickerSet == null || stickerSet.set == null || !stickerSet.set.emojis;
        }

        @Override
        public void sendSticker(TLRPC.Document sticker, String query, Object parent, boolean notify, int scheduleDate) {
            if (delegate == null) {
                return;
            }
            delegate.onStickerSelected(sticker, query, parent, null, clearsInputField, notify, scheduleDate);
            dismiss();
        }

        @Override
        public boolean canSchedule() {
            return delegate != null && delegate.canSchedule();
        }

        @Override
        public boolean isInScheduleMode() {
            return delegate != null && delegate.isInScheduleMode();
        }

        @Override
        public void openSet(TLRPC.InputStickerSet set, boolean clearsInputField) {

        }

        @Override
        public boolean needRemove() {
            return importingStickers != null;
        }

        @Override
        public void remove(SendMessagesHelper.ImportingSticker importingSticker) {
            removeSticker(importingSticker);
        }

        @Override
        public boolean needSend(int contentType) {
            return delegate != null;
        }

        @Override
        public boolean needOpen() {
            return false;
        }

        @Override
        public long getDialogId() {
            if (parentFragment instanceof ChatActivity) {
                return ((ChatActivity) parentFragment).getDialogId();
            }
            return 0;
        }
    };
    
    public StickersAlert(Context context, Object parentObject, TLObject object, Theme.ResourcesProvider resourcesProvider) {
        super(context, false, resourcesProvider);
        fixNavigationBar();
        this.resourcesProvider = resourcesProvider;
        parentActivity = (Activity) context;
        final TLRPC.TL_messages_getAttachedStickers req = new TLRPC.TL_messages_getAttachedStickers();
        if (object instanceof TLRPC.Photo) {
            TLRPC.Photo photo = (TLRPC.Photo) object;
            TLRPC.TL_inputStickeredMediaPhoto inputStickeredMediaPhoto = new TLRPC.TL_inputStickeredMediaPhoto();
            inputStickeredMediaPhoto.id = new TLRPC.TL_inputPhoto();
            inputStickeredMediaPhoto.id.id = photo.id;
            inputStickeredMediaPhoto.id.access_hash = photo.access_hash;
            inputStickeredMediaPhoto.id.file_reference = photo.file_reference;
            if (inputStickeredMediaPhoto.id.file_reference == null) {
                inputStickeredMediaPhoto.id.file_reference = new byte[0];
            }
            req.media = inputStickeredMediaPhoto;
        } else if (object instanceof TLRPC.Document) {
            TLRPC.Document document = (TLRPC.Document) object;
            TLRPC.TL_inputStickeredMediaDocument inputStickeredMediaDocument = new TLRPC.TL_inputStickeredMediaDocument();
            inputStickeredMediaDocument.id = new TLRPC.TL_inputDocument();
            inputStickeredMediaDocument.id.id = document.id;
            inputStickeredMediaDocument.id.access_hash = document.access_hash;
            inputStickeredMediaDocument.id.file_reference = document.file_reference;
            if (inputStickeredMediaDocument.id.file_reference == null) {
                inputStickeredMediaDocument.id.file_reference = new byte[0];
            }
            req.media = inputStickeredMediaDocument;
        }
        RequestDelegate requestDelegate = (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            reqId = 0;
            if (error == null) {
                TLRPC.Vector vector = (TLRPC.Vector) response;
                if (vector.objects.isEmpty()) {
                    dismiss();
                } else if (vector.objects.size() == 1) {
                    TLRPC.StickerSetCovered set = (TLRPC.StickerSetCovered) vector.objects.get(0);
                    inputStickerSet = new TLRPC.TL_inputStickerSetID();
                    inputStickerSet.id = set.set.id;
                    inputStickerSet.access_hash = set.set.access_hash;
                    loadStickerSet();
                } else {
                    stickerSetCovereds = new ArrayList<>();
                    for (int a = 0; a < vector.objects.size(); a++) {
                        stickerSetCovereds.add((TLRPC.StickerSetCovered) vector.objects.get(a));
                    }
                    gridView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 48));
                    titleTextView.setVisibility(View.GONE);
                    shadow[0].setVisibility(View.GONE);
                    adapter.notifyDataSetChanged();
                }
            } else {
                AlertsCreator.processError(currentAccount, error, parentFragment, req);
                dismiss();
            }
        });
        reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (error != null && FileRefController.isFileRefError(error.text) && parentObject != null) {
                FileRefController.getInstance(currentAccount).requestReference(parentObject, req, requestDelegate);
                return;
            }
            requestDelegate.run(response, error);
        });
        init(context);
    }

    public StickersAlert(Context context, String software, ArrayList<Parcelable> uris, ArrayList<String> emoji, Theme.ResourcesProvider resourcesProvider) {
        super(context, false, resourcesProvider);
        fixNavigationBar();
        parentActivity = (Activity) context;
        importingStickers = uris;
        importingSoftware = software;
        Utilities.globalQueue.postRunnable(() -> {
            ArrayList<SendMessagesHelper.ImportingSticker> stickers = new ArrayList<>();
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            Boolean isAnimated = null;
            for (int a = 0, N = uris.size(); a < N; a++) {
                Object obj = uris.get(a);
                if (obj instanceof Uri) {
                    Uri uri = (Uri) obj;
                    String ext = MediaController.getStickerExt(uri);
                    if (ext == null) {
                        continue;
                    }
                    boolean animated = "tgs".equals(ext);
                    if (isAnimated == null) {
                        isAnimated = animated;
                    } else if (isAnimated != animated) {
                        continue;
                    }
                    if (isDismissed()) {
                        return;
                    }
                    SendMessagesHelper.ImportingSticker importingSticker = new SendMessagesHelper.ImportingSticker();
                    importingSticker.animated = animated;
                    importingSticker.path = MediaController.copyFileToCache(uri, ext, (animated ? 64 : 512) * 1024);
                    if (importingSticker.path == null) {
                        continue;
                    }
                    if (!animated) {
                        BitmapFactory.decodeFile(importingSticker.path, opts);
                        if ((opts.outWidth != 512 || opts.outHeight <= 0 || opts.outHeight > 512) && (opts.outHeight != 512 || opts.outWidth <= 0 || opts.outWidth > 512)) {
                            continue;
                        }
                        importingSticker.mimeType = "image/" + ext;
                        importingSticker.validated = true;
                    } else {
                        importingSticker.mimeType = "application/x-tgsticker";
                    }
                    if (emoji != null && emoji.size() == N && emoji.get(a) instanceof String) {
                        importingSticker.emoji = emoji.get(a);
                    } else {
                        importingSticker.emoji = "#️⃣";
                    }
                    stickers.add(importingSticker);
                    if (stickers.size() >= 200) {
                        break;
                    }
                }
            }
            Boolean isAnimatedFinal = isAnimated;
            AndroidUtilities.runOnUIThread(() -> {
                importingStickersPaths = stickers;
                if (importingStickersPaths.isEmpty()) {
                    dismiss();
                } else {
                    adapter.notifyDataSetChanged();
                    if (isAnimatedFinal) {
                        uploadImportStickers = new HashMap<>();
                        for (int a = 0, N = importingStickersPaths.size(); a < N; a++) {
                            SendMessagesHelper.ImportingSticker sticker = importingStickersPaths.get(a);
                            uploadImportStickers.put(sticker.path, sticker);
                            FileLoader.getInstance(currentAccount).uploadFile(sticker.path, false, true, ConnectionsManager.FileTypeFile);
                        }
                    }
                    updateFields();
                }
            });
        });
        init(context);
    }

    public StickersAlert(Context context, BaseFragment baseFragment, TLRPC.InputStickerSet set, TLRPC.TL_messages_stickerSet loadedSet, StickersAlertDelegate stickersAlertDelegate) {
        this(context, baseFragment, set, loadedSet, stickersAlertDelegate, null);
    }

    public StickersAlert(Context context, BaseFragment baseFragment, TLRPC.InputStickerSet set, TLRPC.TL_messages_stickerSet loadedSet, StickersAlertDelegate stickersAlertDelegate, Theme.ResourcesProvider resourcesProvider) {
        super(context, false, resourcesProvider);
        fixNavigationBar();
        delegate = stickersAlertDelegate;
        inputStickerSet = set;
        stickerSet = loadedSet;
        parentFragment = baseFragment;
        loadStickerSet();
        init(context);
    }

    public void setClearsInputField(boolean value) {
        clearsInputField = value;
    }

    public boolean isClearsInputField() {
        return clearsInputField;
    }

    private void loadStickerSet() {
        if (inputStickerSet != null) {
            final MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
            if (stickerSet == null && inputStickerSet.short_name != null) {
                stickerSet = mediaDataController.getStickerSetByName(inputStickerSet.short_name);
            }
            if (stickerSet == null) {
                stickerSet = mediaDataController.getStickerSetById(inputStickerSet.id);
            }
            if (stickerSet == null) {
                TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
                req.stickerset = inputStickerSet;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    reqId = 0;
                    if (error == null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            Transition addTarget = new Transition() {

                                @Override
                                public void captureStartValues(TransitionValues transitionValues) {
                                    transitionValues.values.put("start", true);
                                    transitionValues.values.put("offset", containerView.getTop() + scrollOffsetY);
                                }

                                @Override
                                public void captureEndValues(TransitionValues transitionValues) {
                                    transitionValues.values.put("start", false);
                                    transitionValues.values.put("offset", containerView.getTop() + scrollOffsetY);
                                }

                                @Override
                                public Animator createAnimator(ViewGroup sceneRoot, TransitionValues startValues, TransitionValues endValues) {
                                    int scrollOffsetY = StickersAlert.this.scrollOffsetY;
                                    int startValue = (int) startValues.values.get("offset") - (int) endValues.values.get("offset");
                                    final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
                                    animator.setDuration(250);
                                    animator.addUpdateListener(a -> {
                                        float fraction = a.getAnimatedFraction();
                                        gridView.setAlpha(fraction);
                                        titleTextView.setAlpha(fraction);
                                        if (startValue != 0) {
                                            int value = (int) (startValue * (1f - fraction));
                                            setScrollOffsetY(scrollOffsetY + value);
                                            gridView.setTranslationY(value);
                                        }
                                    });
                                    return animator;
                                }
                            };
                            addTarget.addTarget(containerView);
                            TransitionManager.beginDelayedTransition(container, addTarget);
                        }
                        optionsButton.setVisibility(View.VISIBLE);
                        stickerSet = (TLRPC.TL_messages_stickerSet) response;
                        showEmoji = !stickerSet.set.masks;
                        checkPremiumStickers();
                        mediaDataController.preloadStickerSetThumb(stickerSet);
                        updateSendButton();
                        updateFields();
                        updateDescription();
                        adapter.notifyDataSetChanged();
                    } else {
                        dismiss();
                        if (parentFragment != null) {
                            BulletinFactory.of(parentFragment).createErrorBulletin(LocaleController.getString("AddStickersNotFound", R.string.AddStickersNotFound)).show();
                        }
                    }
                }));
            } else {
                if (adapter != null) {
                    updateSendButton();
                    updateFields();
                    adapter.notifyDataSetChanged();
                }
                updateDescription();
                mediaDataController.preloadStickerSetThumb(stickerSet);
                checkPremiumStickers();
            }
        }
        if (stickerSet != null) {
            showEmoji = !stickerSet.set.masks;
        }
        checkPremiumStickers();
    }

    private void checkPremiumStickers() {
        if (stickerSet != null) {
            stickerSet = MessagesController.getInstance(currentAccount).filterPremiumStickers(stickerSet);
            if (stickerSet == null) {
                dismiss();
            }
        }
    }

    private boolean isEmoji() {
        return stickerSet != null && stickerSet.set != null && stickerSet.set.emojis || stickerSet == null && probablyEmojis;
    }

    private void init(Context context) {
        containerView = new FrameLayout(context) {

            private int lastNotifyWidth;
            private RectF rect = new RectF();
            private boolean fullHeight;

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.getY() < scrollOffsetY) {
                    dismiss();
                    return true;
                }
                return super.onInterceptTouchEvent(ev);
            }

            @Override
            public boolean onTouchEvent(MotionEvent e) {
                return !isDismissed() && super.onTouchEvent(e);
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int height = MeasureSpec.getSize(heightMeasureSpec);
                if (Build.VERSION.SDK_INT >= 21) {
                    ignoreLayout = true;
                    setPadding(backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaddingLeft, 0);
                    ignoreLayout = false;
                }
                if (isEmoji()) {
                    int width = gridView.getMeasuredWidth();
                    if (width == 0) {
                        width = AndroidUtilities.displaySize.x;
                    }
                    adapter.stickersPerRow = Math.max(1, width / AndroidUtilities.dp(AndroidUtilities.isTablet() ? 60 : 45));
                    itemSize = (int) ((MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(36)) / adapter.stickersPerRow);
                    itemHeight = itemSize;
                } else {
                    adapter.stickersPerRow = 5;
                    itemSize = (int) ((MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(36)) / adapter.stickersPerRow);
                    itemHeight = AndroidUtilities.dp(82);
                }
                float spansCount = adapter.stickersPerRow;
                int contentSize;
                MarginLayoutParams params = (MarginLayoutParams) gridView.getLayoutParams();
                if (importingStickers != null) {
                    contentSize = AndroidUtilities.dp(48) + params.bottomMargin + Math.max(3, (int) Math.ceil(importingStickers.size() / spansCount)) * itemHeight + backgroundPaddingTop + AndroidUtilities.statusBarHeight;
                } else if (stickerSetCovereds != null) {
                    contentSize = AndroidUtilities.dp(8) + params.bottomMargin + AndroidUtilities.dp(60) * stickerSetCovereds.size() + adapter.stickersRowCount * itemHeight + backgroundPaddingTop + AndroidUtilities.dp(24);
                } else {
                    contentSize = AndroidUtilities.dp(48) + params.bottomMargin + (Math.max(isEmoji() ? 2 : 3, (stickerSet != null ? (int) Math.ceil(stickerSet.documents.size() / spansCount) : 0))) * itemHeight + backgroundPaddingTop + AndroidUtilities.statusBarHeight;
                }
                if (isEmoji()) {
                    contentSize += itemHeight * .15f;
                }
                if (descriptionTextView != null) {
                    descriptionTextView.measure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(9999, MeasureSpec.AT_MOST));
                    contentSize += descriptionTextView.getMeasuredHeight();
                }
                int padding = contentSize < (height / 5f * 3.2) ? 0 : (int) (height / 5f * 2);
                if (padding != 0 && contentSize < height) {
                    padding -= (height - contentSize);
                }
                if (padding == 0) {
                    padding = backgroundPaddingTop;
                }
                if (descriptionTextView != null) {
                    padding += AndroidUtilities.dp(32) + descriptionTextView.getMeasuredHeight();
                }
                if (stickerSetCovereds != null) {
                    padding += AndroidUtilities.dp(8);
                }
                if (gridView.getPaddingTop() != padding) {
                    ignoreLayout = true;
                    gridView.setPadding(AndroidUtilities.dp(10), padding, AndroidUtilities.dp(10), AndroidUtilities.dp(8));
                    emptyView.setPadding(0, padding, 0, 0);
                    ignoreLayout = false;
                }
                fullHeight = contentSize >= height;
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Math.min(contentSize, height), MeasureSpec.EXACTLY));
            }

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                if (lastNotifyWidth != right - left) {
                    lastNotifyWidth = right - left;
                    if (adapter != null && stickerSetCovereds != null) {
                        adapter.notifyDataSetChanged();
                    }
                }
                super.onLayout(changed, left, top, right, bottom);
                updateLayout();
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }

            private Boolean statusBarOpen;
            private void updateLightStatusBar(boolean open) {
                if (statusBarOpen != null && statusBarOpen == open) {
                    return;
                }
                boolean openBgLight = AndroidUtilities.computePerceivedBrightness(getThemedColor(Theme.key_dialogBackground)) > .721f;
                boolean closedBgLight = AndroidUtilities.computePerceivedBrightness(Theme.blendOver(getThemedColor(Theme.key_actionBarDefault), 0x33000000)) > .721f;
                boolean isLight = (statusBarOpen = open) ? openBgLight : closedBgLight;
                AndroidUtilities.setLightStatusBar(getWindow(), isLight);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                int y = scrollOffsetY - backgroundPaddingTop + AndroidUtilities.dp(6);
                int top = scrollOffsetY - backgroundPaddingTop - AndroidUtilities.dp(13);
                int height = getMeasuredHeight() + AndroidUtilities.dp(15) + backgroundPaddingTop;
                int statusBarHeight = 0;
                float radProgress = 1.0f;
                if (Build.VERSION.SDK_INT >= 21) {
                    top += AndroidUtilities.statusBarHeight;
                    y += AndroidUtilities.statusBarHeight;
                    height -= AndroidUtilities.statusBarHeight;

                    if (fullHeight) {
                        if (top + backgroundPaddingTop < AndroidUtilities.statusBarHeight * 2) {
                            int diff = Math.min(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight * 2 - top - backgroundPaddingTop);
                            top -= diff;
                            height += diff;
                            radProgress = 1.0f - Math.min(1.0f, (diff * 2) / (float) AndroidUtilities.statusBarHeight);
                        }
                        if (top + backgroundPaddingTop < AndroidUtilities.statusBarHeight) {
                            statusBarHeight = Math.min(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight - top - backgroundPaddingTop);
                        }
                    }
                }

                shadowDrawable.setBounds(0, top, getMeasuredWidth(), height);
                shadowDrawable.draw(canvas);

                if (radProgress != 1.0f) {
                    Theme.dialogs_onlineCirclePaint.setColor(getThemedColor(Theme.key_dialogBackground));
                    rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + AndroidUtilities.dp(24));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(12) * radProgress, AndroidUtilities.dp(12) * radProgress, Theme.dialogs_onlineCirclePaint);
                }

                float statusBarProgress = statusBarHeight / (float) AndroidUtilities.statusBarHeight;

                int w = AndroidUtilities.dp(36);
                rect.set((getMeasuredWidth() - w) / 2, y, (getMeasuredWidth() + w) / 2, y + AndroidUtilities.dp(4));
                Theme.dialogs_onlineCirclePaint.setColor(getThemedColor(Theme.key_sheet_scrollUp));
                Theme.dialogs_onlineCirclePaint.setAlpha((int) (255 * Math.max(0, Math.min(1f, (y - AndroidUtilities.statusBarHeight) / (float) AndroidUtilities.dp(16)))));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), Theme.dialogs_onlineCirclePaint);

                updateLightStatusBar(statusBarHeight > AndroidUtilities.statusBarHeight / 2);
                if (statusBarHeight > 0) {
                    Theme.dialogs_onlineCirclePaint.setColor(getThemedColor(Theme.key_dialogBackground));
                    canvas.drawRect(backgroundPaddingLeft, AndroidUtilities.statusBarHeight - statusBarHeight, getMeasuredWidth() - backgroundPaddingLeft, AndroidUtilities.statusBarHeight, Theme.dialogs_onlineCirclePaint);
                }
            }
        };
        containerView.setWillNotDraw(false);
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

        FrameLayout.LayoutParams frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.TOP | Gravity.LEFT);
        frameLayoutParams.topMargin = AndroidUtilities.dp(48);
        shadow[0] = new View(context);
        shadow[0].setBackgroundColor(getThemedColor(Theme.key_dialogShadowLine));
        shadow[0].setAlpha(0.0f);
        shadow[0].setVisibility(View.INVISIBLE);
        shadow[0].setTag(1);
        containerView.addView(shadow[0], frameLayoutParams);

        gridView = new RecyclerListView(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                boolean result = ContentPreviewViewer.getInstance().onInterceptTouchEvent(event, gridView, 0, previewDelegate, resourcesProvider);
                return super.onInterceptTouchEvent(event) || result;
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        gridView.setTag(14);
        gridView.setLayoutManager(layoutManager = new GridLayoutManager(getContext(), 5) {
            @Override
            protected boolean isLayoutRTL() {
                return stickerSetCovereds != null && LocaleController.isRTL;
            }
        });
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (stickerSetCovereds != null && adapter.cache.get(position) instanceof Integer || position == adapter.totalItems) {
                    return adapter.stickersPerRow;
                }
                return 1;
            }
        });
        gridView.setAdapter(adapter = new GridAdapter(context));
        gridView.setVerticalScrollBarEnabled(false);
        gridView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                outRect.left = 0;
                outRect.right = 0;
                outRect.bottom = 0;
                outRect.top = 0;
            }
        });
        gridView.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        gridView.setClipToPadding(false);
        gridView.setEnabled(true);
        gridView.setGlowColor(getThemedColor(Theme.key_dialogScrollGlow));
        gridView.setOnTouchListener((v, event) -> ContentPreviewViewer.getInstance().onTouch(event, gridView, 0, stickersOnItemClickListener, previewDelegate, resourcesProvider));
        gridView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                updateLayout();
            }
        });
        stickersOnItemClickListener = (view, position) -> {
            if (stickerSetCovereds != null) {
                TLRPC.StickerSetCovered pack = adapter.positionsToSets.get(position);
                if (pack != null) {
                    dismiss();
                    TLRPC.TL_inputStickerSetID inputStickerSetID = new TLRPC.TL_inputStickerSetID();
                    inputStickerSetID.access_hash = pack.set.access_hash;
                    inputStickerSetID.id = pack.set.id;
                    StickersAlert alert = new StickersAlert(parentActivity, parentFragment, inputStickerSetID, null, null, resourcesProvider);
                    alert.show();
                }
            } else if (importingStickersPaths != null) {
                if (position < 0 || position >= importingStickersPaths.size()) {
                    return;
                }
                selectedStickerPath = importingStickersPaths.get(position);
                if (!selectedStickerPath.validated) {
                    return;
                }
                stickerEmojiTextView.setText(Emoji.replaceEmoji(selectedStickerPath.emoji, stickerEmojiTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(30), false));
                stickerImageView.setImage(ImageLocation.getForPath(selectedStickerPath.path), null, null, null, null, null, selectedStickerPath.animated ? "tgs" : null, 0, null);
                FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) stickerPreviewLayout.getLayoutParams();
                layoutParams.topMargin = scrollOffsetY;
                stickerPreviewLayout.setLayoutParams(layoutParams);
                stickerPreviewLayout.setVisibility(View.VISIBLE);
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(ObjectAnimator.ofFloat(stickerPreviewLayout, View.ALPHA, 0.0f, 1.0f));
                animatorSet.setDuration(200);
                animatorSet.start();
            } else {
                if (stickerSet == null || position < 0 || position >= stickerSet.documents.size()) {
                    return;
                }
                selectedSticker = stickerSet.documents.get(position);

                boolean set = false;
                for (int a = 0; a < selectedSticker.attributes.size(); a++) {
                    TLRPC.DocumentAttribute attribute = selectedSticker.attributes.get(a);
                    if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                        if (attribute.alt != null && attribute.alt.length() > 0) {
                            stickerEmojiTextView.setText(Emoji.replaceEmoji(attribute.alt, stickerEmojiTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(30), false));
                            set = true;
                        }
                        break;
                    }
                }
                if (!set) {
                    stickerEmojiTextView.setText(Emoji.replaceEmoji(MediaDataController.getInstance(currentAccount).getEmojiForSticker(selectedSticker.id), stickerEmojiTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(30), false));
                }

                if ((stickerSet == null || stickerSet.set == null || !stickerSet.set.emojis) && !ContentPreviewViewer.getInstance().showMenuFor(view)) {
                    TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(selectedSticker.thumbs, 90);
                    stickerImageView.getImageReceiver().setImage(ImageLocation.getForDocument(selectedSticker), null, ImageLocation.getForDocument(thumb, selectedSticker), null, "webp", stickerSet, 1);
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) stickerPreviewLayout.getLayoutParams();
                    layoutParams.topMargin = scrollOffsetY;
                    stickerPreviewLayout.setLayoutParams(layoutParams);
                    stickerPreviewLayout.setVisibility(View.VISIBLE);
                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.playTogether(ObjectAnimator.ofFloat(stickerPreviewLayout, View.ALPHA, 0.0f, 1.0f));
                    animatorSet.setDuration(200);
                    animatorSet.start();
                }
            }
        };
        gridView.setOnItemClickListener(stickersOnItemClickListener);
        containerView.addView(gridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 48, 0, 48));

        emptyView = new FrameLayout(context) {
            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        containerView.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 48));
        gridView.setEmptyView(emptyView);
        emptyView.setOnTouchListener((v, event) -> true);

        titleTextView = new LinkSpanDrawable.LinksTextView(context);
        titleTextView.setLines(1);
        titleTextView.setSingleLine(true);
        titleTextView.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleTextView.setLinkTextColor(getThemedColor(Theme.key_dialogTextLink));
        titleTextView.setEllipsize(TextUtils.TruncateAt.END);
        titleTextView.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(6), AndroidUtilities.dp(18), AndroidUtilities.dp(6));
        titleTextView.setGravity(Gravity.CENTER_VERTICAL);
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        containerView.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.LEFT | Gravity.TOP, 0, 0, 40, 0));

        optionsButton = new ActionBarMenuItem(context, null, 0, getThemedColor(Theme.key_sheet_other), resourcesProvider);
        optionsButton.setLongClickEnabled(false);
        optionsButton.setSubMenuOpenSide(2);
        optionsButton.setIcon(R.drawable.ic_ab_other);
        optionsButton.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_player_actionBarSelector), 1));
        containerView.addView(optionsButton, LayoutHelper.createFrame(40, 40, Gravity.TOP | Gravity.RIGHT, 0, 5, 5, 0));
        optionsButton.addSubItem(1, R.drawable.msg_share, LocaleController.getString("StickersShare", R.string.StickersShare));
        optionsButton.addSubItem(2, R.drawable.msg_link, LocaleController.getString("CopyLink", R.string.CopyLink));
        optionsButton.setOnClickListener(v -> optionsButton.toggleSubMenu());
        optionsButton.setDelegate(this::onSubItemClick);
        optionsButton.setContentDescription(LocaleController.getString("AccDescrMoreOptions", R.string.AccDescrMoreOptions));
        optionsButton.setVisibility(inputStickerSet != null ? View.VISIBLE : View.GONE);

        RadialProgressView progressView = new RadialProgressView(context);
        emptyView.addView(progressView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.BOTTOM | Gravity.LEFT);
        frameLayoutParams.bottomMargin = AndroidUtilities.dp(48);
        shadow[1] = new View(context);
        shadow[1].setBackgroundColor(getThemedColor(Theme.key_dialogShadowLine));
        containerView.addView(shadow[1], frameLayoutParams);

        pickerBottomLayout = new TextView(context);
        pickerBottomLayout.setBackground(Theme.createSelectorWithBackgroundDrawable(getThemedColor(Theme.key_dialogBackground), getThemedColor(Theme.key_listSelector)));
        pickerBottomLayout.setTextColor(getThemedColor(buttonTextColorKey = Theme.key_dialogTextBlue2));
        pickerBottomLayout.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        pickerBottomLayout.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        pickerBottomLayout.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        pickerBottomLayout.setGravity(Gravity.CENTER);

        pickerBottomFrameLayout = new FrameLayout(context);
        pickerBottomFrameLayout.addView(pickerBottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
        containerView.addView(pickerBottomFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));

        premiumButtonView = new PremiumButtonView(context, false);
        premiumButtonView.setIcon(R.raw.unlock_icon);
        premiumButtonView.setVisibility(View.INVISIBLE);
        containerView.addView(premiumButtonView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 8, 0, 8, 8));

        stickerPreviewLayout = new FrameLayout(context);
        stickerPreviewLayout.setVisibility(View.GONE);
        stickerPreviewLayout.setSoundEffectsEnabled(false);
        containerView.addView(stickerPreviewLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        stickerPreviewLayout.setOnClickListener(v -> hidePreview());

        stickerImageView = new BackupImageView(context);
        stickerImageView.setAspectFit(true);
        stickerImageView.setLayerNum(7);
        stickerPreviewLayout.addView(stickerImageView);

        stickerEmojiTextView = new TextView(context);
        stickerEmojiTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 30);
        stickerEmojiTextView.setGravity(Gravity.BOTTOM | Gravity.RIGHT);
        stickerPreviewLayout.addView(stickerEmojiTextView);

        previewSendButton = new TextView(context);
        previewSendButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        previewSendButton.setTextColor(getThemedColor(Theme.key_dialogTextBlue2));
        previewSendButton.setBackground(Theme.createSelectorWithBackgroundDrawable(getThemedColor(Theme.key_dialogBackground), getThemedColor(Theme.key_listSelector)));
        previewSendButton.setGravity(Gravity.CENTER);
        previewSendButton.setPadding(AndroidUtilities.dp(29), 0, AndroidUtilities.dp(29), 0);
        previewSendButton.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        stickerPreviewLayout.addView(previewSendButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT));
        previewSendButton.setOnClickListener(v -> {
            if (importingStickersPaths != null) {
                removeSticker(selectedStickerPath);
                hidePreview();
                selectedStickerPath = null;
            } else {
                delegate.onStickerSelected(selectedSticker, null, stickerSet, null, clearsInputField, true, 0);
                dismiss();
            }
        });

        frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.BOTTOM | Gravity.LEFT);
        frameLayoutParams.bottomMargin = AndroidUtilities.dp(48);
        previewSendButtonShadow = new View(context);
        previewSendButtonShadow.setBackgroundColor(getThemedColor(Theme.key_dialogShadowLine));
        stickerPreviewLayout.addView(previewSendButtonShadow, frameLayoutParams);

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        if (importingStickers != null) {
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileUploaded);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileUploadFailed);
        }

        updateFields();
        updateSendButton();
        updateDescription();
        updateColors();
        adapter.notifyDataSetChanged();
    }

    private void updateDescription() {
        if (containerView == null) {
            return;
        }
        if (!UserConfig.getInstance(currentAccount).isPremium() && MessageObject.isPremiumEmojiPack(stickerSet)) {
//            descriptionTextView = new TextView(getContext());
//            descriptionTextView.setTextColor(getThemedColor(Theme.key_chat_emojiPanelTrendingDescription));
//            descriptionTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
//            descriptionTextView.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
//            descriptionTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString("PremiumPreviewEmojiPack", R.string.PremiumPreviewEmojiPack)));
//            containerView.addView(descriptionTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 50, 40, 0));
        }
    }

    private void updateSendButton() {
        int size = (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / 2 / AndroidUtilities.density);
        if (importingStickers != null) {
            previewSendButton.setText(LocaleController.getString("ImportStickersRemove", R.string.ImportStickersRemove));
            previewSendButton.setTextColor(getThemedColor(Theme.key_text_RedBold));
            stickerImageView.setLayoutParams(LayoutHelper.createFrame(size, size, Gravity.CENTER, 0, 0, 0, 30));
            stickerEmojiTextView.setLayoutParams(LayoutHelper.createFrame(size, size, Gravity.CENTER, 0, 0, 0, 30));
            previewSendButton.setVisibility(View.VISIBLE);
            previewSendButtonShadow.setVisibility(View.VISIBLE);
        } else if (delegate != null && (stickerSet == null || !stickerSet.set.masks)) {
            previewSendButton.setText(LocaleController.getString("SendSticker", R.string.SendSticker));
            stickerImageView.setLayoutParams(LayoutHelper.createFrame(size, size, Gravity.CENTER, 0, 0, 0, 30));
            stickerEmojiTextView.setLayoutParams(LayoutHelper.createFrame(size, size, Gravity.CENTER, 0, 0, 0, 30));
            previewSendButton.setVisibility(View.VISIBLE);
            previewSendButtonShadow.setVisibility(View.VISIBLE);
        } else {
            previewSendButton.setText(LocaleController.getString("Close", R.string.Close));
            stickerImageView.setLayoutParams(LayoutHelper.createFrame(size, size, Gravity.CENTER));
            stickerEmojiTextView.setLayoutParams(LayoutHelper.createFrame(size, size, Gravity.CENTER));
            previewSendButton.setVisibility(View.GONE);
            previewSendButtonShadow.setVisibility(View.GONE);
        }
    }

    private void removeSticker(SendMessagesHelper.ImportingSticker sticker) {
        int idx = importingStickersPaths.indexOf(sticker);
        if (idx >= 0) {
            importingStickersPaths.remove(idx);
            adapter.notifyItemRemoved(idx);
            if (importingStickersPaths.isEmpty()) {
                dismiss();
                return;
            }
            updateFields();
        }
    }

    public void setInstallDelegate(StickersAlertInstallDelegate stickersAlertInstallDelegate) {
        installDelegate = stickersAlertInstallDelegate;
    }

    public void setCustomButtonDelegate(StickersAlertCustomButtonDelegate customButtonDelegate) {
        this.customButtonDelegate = customButtonDelegate;
        updateFields();
    }

    private void onSubItemClick(int id) {
        if (stickerSet == null) {
            return;
        }
        String stickersUrl;
        if (stickerSet.set != null && stickerSet.set.emojis) {
            stickersUrl = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/addemoji/" + stickerSet.set.short_name;
        } else {
            stickersUrl = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/addstickers/" + stickerSet.set.short_name;
        }
        if (id == 1) {
            Context context = parentActivity;
            if (context == null && parentFragment != null) {
                context = parentFragment.getParentActivity();
            }
            if (context == null) {
                context = getContext();
            }
            ShareAlert alert = new ShareAlert(context, null, stickersUrl, false, stickersUrl, false, resourcesProvider) {
                @Override
                public void dismissInternal() {
                    super.dismissInternal();
                    if (parentFragment instanceof ChatActivity) {
                        AndroidUtilities.requestAdjustResize(parentFragment.getParentActivity(), parentFragment.getClassGuid());
                        if (((ChatActivity) parentFragment).getChatActivityEnterView().getVisibility() == View.VISIBLE) {
                            parentFragment.getFragmentView().requestLayout();
                        }
                    }
                }

                @Override
                protected void onSend(LongSparseArray<TLRPC.Dialog> dids, int count, TLRPC.TL_forumTopic topic) {
                    AndroidUtilities.runOnUIThread(() -> {
                        UndoView undoView;
                        if (parentFragment instanceof ChatActivity) {
                            undoView = ((ChatActivity) parentFragment).getUndoView();
                        } else if (parentFragment instanceof ProfileActivity) {
                            undoView = ((ProfileActivity) parentFragment).getUndoView();
                        } else {
                            undoView = null;
                        }
                        if (undoView != null) {
                            if (dids.size() == 1) {
                                undoView.showWithAction(dids.valueAt(0).id, UndoView.ACTION_FWD_MESSAGES, count);
                            } else {
                                undoView.showWithAction(0, UndoView.ACTION_FWD_MESSAGES, count, dids.size(), null, null);
                            }
                        }
                    }, 100);
                }
            };
            if (parentFragment != null) {
                parentFragment.showDialog(alert);
                if (parentFragment instanceof ChatActivity) {
                    alert.setCalcMandatoryInsets(((ChatActivity) parentFragment).isKeyboardVisible());
                }
            } else {
                alert.show();
            }
        } else if (id == 2) {
            try {
                AndroidUtilities.addToClipboard(stickersUrl);
                BulletinFactory.of((FrameLayout) containerView, resourcesProvider).createCopyLinkBulletin().show();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    private void updateFields() {
        if (titleTextView == null) {
            return;
        }
        if (stickerSet != null) {
            SpannableStringBuilder stringBuilder = null;
            CharSequence title = stickerSet.set.title;
            title = Emoji.replaceEmoji(title, titleTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(18), false);
            try {
                if (urlPattern == null) {
                    urlPattern = Pattern.compile("@[a-zA-Z\\d_]{1,32}");
                }
                Matcher matcher = urlPattern.matcher(title);
                while (matcher.find()) {
                    if (stringBuilder == null) {
                        stringBuilder = new SpannableStringBuilder(title);
                    }
                    int start = matcher.start();
                    int end = matcher.end();
                    if (stickerSet.set.title.charAt(start) != '@') {
                        start++;
                    }
                    URLSpanNoUnderline url = new URLSpanNoUnderline(title.subSequence(start + 1, end).toString()) {
                        @Override
                        public void onClick(View widget) {
                            MessagesController.getInstance(currentAccount).openByUserName(getURL(), parentFragment, 1);
                            dismiss();
                        }
                    };
                    stringBuilder.setSpan(url, start, end, 0);
                }
                if (stringBuilder != null) {
                    title = stringBuilder;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            titleTextView.setText(title);

            if (isEmoji()) {
                int width = gridView.getMeasuredWidth();
                if (width == 0) {
                    width = AndroidUtilities.displaySize.x;
                }
                adapter.stickersPerRow = Math.max(1, width / AndroidUtilities.dp(AndroidUtilities.isTablet() ? 60 : 45));
            } else {
                adapter.stickersPerRow = 5;
            }
            layoutManager.setSpanCount(adapter.stickersPerRow);

            if (stickerSet != null && stickerSet.set != null && stickerSet.set.emojis && !UserConfig.getInstance(currentAccount).isPremium()) {
                boolean hasPremiumEmoji = false;
                if (stickerSet.documents != null) {
                    for (int i = 0; i < stickerSet.documents.size(); ++i) {
                        if (!MessageObject.isFreeEmoji(stickerSet.documents.get(i))) {
                            hasPremiumEmoji = true;
                            break;
                        }
                    }
                }

                if (hasPremiumEmoji) {
                    premiumButtonView.setVisibility(View.VISIBLE);
                    pickerBottomLayout.setBackground(null);

                    setButton(null, null, null);
                    premiumButtonView.setButton(LocaleController.getString("UnlockPremiumEmoji", R.string.UnlockPremiumEmoji), e -> {
                        if (parentFragment != null) {
                            new PremiumFeatureBottomSheet(parentFragment, PremiumPreviewFragment.PREMIUM_FEATURE_ANIMATED_EMOJI, false).show();
                        } else if (getContext() instanceof LaunchActivity) {
                            ((LaunchActivity) getContext()).presentFragment(new PremiumPreviewFragment(null));
                        }
                    });

                    return;
                }
            } else {
                premiumButtonView.setVisibility(View.INVISIBLE);
            }

            boolean notInstalled;
            if (stickerSet != null && stickerSet.set != null && stickerSet.set.emojis) {
                ArrayList<TLRPC.TL_messages_stickerSet> sets = MediaDataController.getInstance(currentAccount).getStickerSets(MediaDataController.TYPE_EMOJIPACKS);
                boolean has = false;
                for (int i = 0; sets != null && i < sets.size(); ++i) {
                    if (sets.get(i) != null && sets.get(i).set != null && sets.get(i).set.id == stickerSet.set.id) {
                        has = true;
                        break;
                    }
                }
                notInstalled = !has;
            } else {
                notInstalled = stickerSet == null || stickerSet.set == null || !MediaDataController.getInstance(currentAccount).isStickerPackInstalled(stickerSet.set.id);
            }

            if (customButtonDelegate != null) {
                setButton(v -> {
                    if (customButtonDelegate.onCustomButtonPressed()) {
                        dismiss();
                    }
                }, customButtonDelegate.getCustomButtonText(), customButtonDelegate.getCustomButtonTextColorKey(), customButtonDelegate.getCustomButtonColorKey(), customButtonDelegate.getCustomButtonRippleColorKey());
            } else if (notInstalled) {
                String text;
                if (stickerSet != null && stickerSet.set != null && stickerSet.set.masks) {
                    text = LocaleController.formatPluralString("AddManyMasksCount", stickerSet.documents == null ? 0 : stickerSet.documents.size());
                } else if (stickerSet != null && stickerSet.set != null && stickerSet.set.emojis) {
                    text = LocaleController.formatPluralString("AddManyEmojiCount", stickerSet.documents == null ? 0 : stickerSet.documents.size());
                } else {
                    text = LocaleController.formatPluralString("AddManyStickersCount", stickerSet == null || stickerSet.documents == null ? 0 : stickerSet.documents.size());
                }
                setButton(v -> {
                    dismiss();
                    if (installDelegate != null) {
                        installDelegate.onStickerSetInstalled();
                    }
                    if (inputStickerSet == null || MediaDataController.getInstance(currentAccount).cancelRemovingStickerSet(inputStickerSet.id)) {
                        return;
                    }
                    TLRPC.TL_messages_installStickerSet req = new TLRPC.TL_messages_installStickerSet();
                    req.stickerset = inputStickerSet;
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        int type = MediaDataController.TYPE_IMAGE;
                        if (stickerSet.set.masks) {
                            type = MediaDataController.TYPE_MASK;
                        } else if (stickerSet.set.emojis) {
                            type = MediaDataController.TYPE_EMOJIPACKS;
                        };
                        try {
                            if (error == null) {
                                if (showTooltipWhenToggle) {
                                    Bulletin.make(parentFragment, new StickerSetBulletinLayout(pickerBottomFrameLayout.getContext(), stickerSet, StickerSetBulletinLayout.TYPE_ADDED, null, resourcesProvider), Bulletin.DURATION_SHORT).show();
                                }
                                if (response instanceof TLRPC.TL_messages_stickerSetInstallResultArchive) {
                                    MediaDataController.getInstance(currentAccount).processStickerSetInstallResultArchive(parentFragment, true, type, (TLRPC.TL_messages_stickerSetInstallResultArchive) response);
                                }
                            } else {
                                Toast.makeText(getContext(), LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred), Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        MediaDataController.getInstance(currentAccount).loadStickers(type, false, true);
                    }));
                }, text, Theme.key_featuredStickers_buttonText, Theme.key_featuredStickers_addButton, Theme.key_featuredStickers_addButtonPressed);
            } else {
                String text;
                if (stickerSet.set.masks) {
                    text = LocaleController.formatPluralString("RemoveManyMasksCount", stickerSet.documents.size());
                } else if (stickerSet.set.emojis) {
                    text = LocaleController.formatPluralString("RemoveManyEmojiCount", stickerSet.documents.size());
                } else {
                    text = LocaleController.formatPluralString("RemoveManyStickersCount", stickerSet.documents.size());
                }
                if (stickerSet.set.official) {
                    setButton(v -> {
                        if (installDelegate != null) {
                            installDelegate.onStickerSetUninstalled();
                        }
                        dismiss();
                        MediaDataController.getInstance(currentAccount).toggleStickerSet(getContext(), stickerSet, 1, parentFragment, true, showTooltipWhenToggle);
                    }, text, Theme.key_text_RedBold);
                } else {
                    setButton(v -> {
                        if (installDelegate != null) {
                            installDelegate.onStickerSetUninstalled();
                        }
                        dismiss();
                        MediaDataController.getInstance(currentAccount).toggleStickerSet(getContext(), stickerSet, 0, parentFragment, true, showTooltipWhenToggle);
                    }, text, Theme.key_text_RedBold);
                }
            }
            adapter.notifyDataSetChanged();
        } else if (importingStickers != null) {
            titleTextView.setText(LocaleController.formatPluralString("Stickers", importingStickersPaths != null ? importingStickersPaths.size() : importingStickers.size()));
            if (uploadImportStickers == null || uploadImportStickers.isEmpty()) {
                setButton(v -> showNameEnterAlert(), LocaleController.formatString("ImportStickers", R.string.ImportStickers, LocaleController.formatPluralString("Stickers", importingStickersPaths != null ? importingStickersPaths.size() : importingStickers.size())), Theme.key_dialogTextBlue2);
                pickerBottomLayout.setEnabled(true);
            } else {
                setButton(null, LocaleController.getString("ImportStickersProcessing", R.string.ImportStickersProcessing), Theme.key_dialogTextGray2);
                pickerBottomLayout.setEnabled(false);
            }
        } else {
            String text = LocaleController.getString("Close", R.string.Close);
            setButton((v) -> dismiss(), text, Theme.key_dialogTextBlue2);
        }
    }

    private void showNameEnterAlert() {
        Context context = getContext();

        int[] state = new int[]{0};
        FrameLayout fieldLayout = new FrameLayout(context);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString("ImportStickersEnterName", R.string.ImportStickersEnterName));
        builder.setPositiveButton(LocaleController.getString("Next", R.string.Next), (dialog, which) -> {

        });

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        builder.setView(linearLayout);

        linearLayout.addView(fieldLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP | Gravity.LEFT, 24, 6, 24, 0));

        TextView message = new TextView(context);

        TextView textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setTextColor(getThemedColor(Theme.key_dialogTextHint));
        textView.setMaxLines(1);
        textView.setLines(1);
        textView.setText("t.me/addstickers/");
        textView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        textView.setGravity(Gravity.LEFT | Gravity.TOP);
        textView.setSingleLine(true);
        textView.setVisibility(View.INVISIBLE);
        textView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        textView.setPadding(0, AndroidUtilities.dp(4), 0, 0);
        fieldLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 36, Gravity.TOP | Gravity.LEFT));

        EditTextBoldCursor editText = new EditTextBoldCursor(context);
        editText.setBackground(null);
        editText.setLineColors(Theme.getColor(Theme.key_dialogInputField), Theme.getColor(Theme.key_dialogInputFieldActivated), Theme.getColor(Theme.key_text_RedBold));
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setMaxLines(1);
        editText.setLines(1);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editText.setGravity(Gravity.LEFT | Gravity.TOP);
        editText.setSingleLine(true);
        editText.setImeOptions(EditorInfo.IME_ACTION_NEXT);
        editText.setCursorColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setCursorSize(AndroidUtilities.dp(20));
        editText.setCursorWidth(1.5f);
        editText.setPadding(0, AndroidUtilities.dp(4), 0, 0);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (state[0] != 2) {
                    return;
                }
                checkUrlAvailable(message, editText.getText().toString(), false);
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        fieldLayout.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.TOP | Gravity.LEFT));
        editText.setOnEditorActionListener((view, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_NEXT) {
                builder.create().getButton(AlertDialog.BUTTON_POSITIVE).callOnClick();
                return true;
            }
            return false;
        });
        editText.setSelection(editText.length());

        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), (dialog, which) -> AndroidUtilities.hideKeyboard(editText));

        message.setText(AndroidUtilities.replaceTags(LocaleController.getString("ImportStickersEnterNameInfo", R.string.ImportStickersEnterNameInfo)));
        message.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        message.setPadding(AndroidUtilities.dp(23), AndroidUtilities.dp(12), AndroidUtilities.dp(23), AndroidUtilities.dp(6));
        message.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
        linearLayout.addView(message, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final AlertDialog alertDialog = builder.create();
        alertDialog.setOnShowListener(dialog -> AndroidUtilities.runOnUIThread(() -> {
            editText.requestFocus();
            AndroidUtilities.showKeyboard(editText);
        }));
        alertDialog.show();
        editText.requestFocus();
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (state[0] == 1) {
                return;
            }
            if (state[0] == 0) {
                state[0] = 1;
                TLRPC.TL_stickers_suggestShortName req = new TLRPC.TL_stickers_suggestShortName();
                req.title = setTitle = editText.getText().toString();
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    boolean set = false;
                    if (response instanceof TLRPC.TL_stickers_suggestedShortName) {
                        TLRPC.TL_stickers_suggestedShortName res = (TLRPC.TL_stickers_suggestedShortName) response;
                        if (res.short_name != null) {
                            editText.setText(res.short_name);
                            editText.setSelection(0, editText.length());
                            checkUrlAvailable(message, editText.getText().toString(), true);
                            set = true;
                        }
                    }
                    textView.setVisibility(View.VISIBLE);
                    editText.setPadding(textView.getMeasuredWidth(), AndroidUtilities.dp(4), 0, 0);
                    if (!set) {
                        editText.setText("");
                    }
                    state[0] = 2;
                }));
            } else if (state[0] == 2) {
                state[0] = 3;
                if (!lastNameAvailable) {
                    AndroidUtilities.shakeView(editText);
                    editText.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                }
                AndroidUtilities.hideKeyboard(editText);
                SendMessagesHelper.getInstance(currentAccount).prepareImportStickers(setTitle, lastCheckName, importingSoftware, importingStickersPaths, (param) -> {
                    ImportingAlert importingAlert = new ImportingAlert(getContext(), lastCheckName, null, resourcesProvider);
                    importingAlert.show();
                });
                builder.getDismissRunnable().run();
                dismiss();
            }
        });
    }

    private Runnable checkRunnable;
    private String lastCheckName;
    private int checkReqId;
    private boolean lastNameAvailable;
    private String setTitle;
    private void checkUrlAvailable(TextView message, String text, boolean forceAvailable) {
        if (forceAvailable) {
            message.setText(LocaleController.getString("ImportStickersLinkAvailable", R.string.ImportStickersLinkAvailable));
            message.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGreenText));
            lastNameAvailable = true;
            lastCheckName = text;
            return;
        }
        if (checkRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(checkRunnable);
            checkRunnable = null;
            lastCheckName = null;
            if (checkReqId != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(checkReqId, true);
            }
        }
        if (TextUtils.isEmpty(text)) {
            message.setText(LocaleController.getString("ImportStickersEnterUrlInfo", R.string.ImportStickersEnterUrlInfo));
            message.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
            return;
        }
        lastNameAvailable = false;
        if (text != null) {
            if (text.startsWith("_") || text.endsWith("_")) {
                message.setText(LocaleController.getString("ImportStickersLinkInvalid", R.string.ImportStickersLinkInvalid));
                message.setTextColor(getThemedColor(Theme.key_text_RedRegular));
                return;
            }
            for (int a = 0, N = text.length(); a < N; a++) {
                char ch = text.charAt(a);
                if (!(ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch == '_')) {
                    message.setText(LocaleController.getString("ImportStickersEnterUrlInfo", R.string.ImportStickersEnterUrlInfo));
                    message.setTextColor(getThemedColor(Theme.key_text_RedRegular));
                    return;
                }
            }
        }
        if (text == null || text.length() < 5) {
            message.setText(LocaleController.getString("ImportStickersLinkInvalidShort", R.string.ImportStickersLinkInvalidShort));
            message.setTextColor(getThemedColor(Theme.key_text_RedRegular));
            return;
        }
        if (text.length() > 32) {
            message.setText(LocaleController.getString("ImportStickersLinkInvalidLong", R.string.ImportStickersLinkInvalidLong));
            message.setTextColor(getThemedColor(Theme.key_text_RedRegular));
            return;
        }

        message.setText(LocaleController.getString("ImportStickersLinkChecking", R.string.ImportStickersLinkChecking));
        message.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText8));
        lastCheckName = text;
        checkRunnable = () -> {
            TLRPC.TL_stickers_checkShortName req = new TLRPC.TL_stickers_checkShortName();
            req.short_name = text;
            checkReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                checkReqId = 0;
                if (lastCheckName != null && lastCheckName.equals(text)) {
                    if (error == null && response instanceof TLRPC.TL_boolTrue) {
                        message.setText(LocaleController.getString("ImportStickersLinkAvailable", R.string.ImportStickersLinkAvailable));
                        message.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGreenText));
                        lastNameAvailable = true;
                    } else {
                        message.setText(LocaleController.getString("ImportStickersLinkTaken", R.string.ImportStickersLinkTaken));
                        message.setTextColor(getThemedColor(Theme.key_text_RedRegular));
                        lastNameAvailable = false;
                    }
                }
            }), ConnectionsManager.RequestFlagFailOnServerErrors);
        };
        AndroidUtilities.runOnUIThread(checkRunnable, 300);
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    @SuppressLint("NewApi")
    private void updateLayout() {
        if (gridView.getChildCount() <= 0) {
            setScrollOffsetY(gridView.getPaddingTop());
            return;
        }
        View child = gridView.getChildAt(0);
        RecyclerListView.Holder holder = (RecyclerListView.Holder) gridView.findContainingViewHolder(child);
        int top = child.getTop();
        int newOffset = 0;
        if (top >= 0 && holder != null && holder.getAdapterPosition() == 0) {
            newOffset = top;
            runShadowAnimation(0, false);
        } else {
            runShadowAnimation(0, true);
        }

//        if (layoutManager.findLastCompletelyVisibleItemPosition() == adapter.getItemCount() - 1) {
//            runShadowAnimation(1, false);
//        } else {
        runShadowAnimation(1, true);
//        }

        if (scrollOffsetY != newOffset) {
            setScrollOffsetY(newOffset);
        }
    }

    private void setScrollOffsetY(int newOffset) {
        scrollOffsetY = newOffset;
        gridView.setTopGlowOffset(newOffset);
        if (stickerSetCovereds == null) {
            titleTextView.setTranslationY(newOffset);
            if (descriptionTextView != null) {
                descriptionTextView.setTranslationY(newOffset);
            }
            if (importingStickers == null) {
                optionsButton.setTranslationY(newOffset);
            }
            shadow[0].setTranslationY(newOffset);
        }
        containerView.invalidate();
    }

    private void hidePreview() {
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(ObjectAnimator.ofFloat(stickerPreviewLayout, View.ALPHA, 0.0f));
        animatorSet.setDuration(200);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                stickerPreviewLayout.setVisibility(View.GONE);
                stickerImageView.setImageDrawable(null);
            }
        });
        animatorSet.start();
    }

    private void runShadowAnimation(final int num, final boolean show) {
        if (stickerSetCovereds != null) {
            return;
        }
        if (show && shadow[num].getTag() != null || !show && shadow[num].getTag() == null) {
            shadow[num].setTag(show ? null : 1);
            if (show) {
                shadow[num].setVisibility(View.VISIBLE);
            }
            if (shadowAnimation[num] != null) {
                shadowAnimation[num].cancel();
            }
            shadowAnimation[num] = new AnimatorSet();
            shadowAnimation[num].playTogether(ObjectAnimator.ofFloat(shadow[num], View.ALPHA, show ? 1.0f : 0.0f));
            shadowAnimation[num].setDuration(150);
            shadowAnimation[num].addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (shadowAnimation[num] != null && shadowAnimation[num].equals(animation)) {
                        if (!show) {
                            shadow[num].setVisibility(View.INVISIBLE);
                        }
                        shadowAnimation[num] = null;
                    }
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                    if (shadowAnimation[num] != null && shadowAnimation[num].equals(animation)) {
                        shadowAnimation[num] = null;
                    }
                }
            });
            shadowAnimation[num].start();
        }
    }

    @Override
    public void show() {
        super.show();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 4);
    }

    private Runnable onDismissListener;
    public void setOnDismissListener(Runnable onDismissListener) {
        this.onDismissListener = onDismissListener;
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (onDismissListener != null) {
            onDismissListener.run();
        }
        if (reqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
            reqId = 0;
        }
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        if (importingStickers != null) {
            if (importingStickersPaths != null) {
                for (int a = 0, N = importingStickersPaths.size(); a < N; a++) {
                    SendMessagesHelper.ImportingSticker sticker = importingStickersPaths.get(a);
                    if (!sticker.validated) {
                        FileLoader.getInstance(currentAccount).cancelFileUpload(sticker.path, false);
                    }
                    if (sticker.animated) {
                        new File(sticker.path).delete();
                    }
                }
            }
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploaded);
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileUploadFailed);
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 4);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Bulletin.addDelegate((FrameLayout) containerView, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return pickerBottomFrameLayout != null ? pickerBottomFrameLayout.getHeight() : 0;
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        Bulletin.removeDelegate((FrameLayout) containerView);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiLoaded) {
            if (gridView != null) {
                int count = gridView.getChildCount();
                for (int a = 0; a < count; a++) {
                    gridView.getChildAt(a).invalidate();
                }
            }
        } else if (id == NotificationCenter.fileUploaded) {
            if (uploadImportStickers == null) {
                return;
            }
            String location = (String) args[0];
            SendMessagesHelper.ImportingSticker sticker = uploadImportStickers.get(location);
            if (sticker != null) {
                sticker.uploadMedia(currentAccount, (TLRPC.InputFile) args[1], () -> {
                    if (isDismissed()) {
                        return;
                    }
                    uploadImportStickers.remove(location);
                    if (!"application/x-tgsticker".equals(sticker.mimeType)) {
                        removeSticker(sticker);
                    } else {
                        sticker.validated = true;
                        int idx = importingStickersPaths.indexOf(sticker);
                        if (idx >= 0) {
                            RecyclerView.ViewHolder holder = gridView.findViewHolderForAdapterPosition(idx);
                            if (holder != null) {
                                ((StickerEmojiCell) holder.itemView).setSticker(sticker);
                            }
                        } else {
                            adapter.notifyDataSetChanged();
                        }
                    }
                    if (uploadImportStickers.isEmpty()) {
                        updateFields();
                    }
                });
            }
        } else if (id == NotificationCenter.fileUploadFailed) {
            if (uploadImportStickers == null) {
                return;
            }
            String location = (String) args[0];
            SendMessagesHelper.ImportingSticker sticker = uploadImportStickers.remove(location);
            if (sticker != null) {
                removeSticker(sticker);
            }
            if (uploadImportStickers.isEmpty()) {
                updateFields();
            }
        }
    }

    private void setButton(View.OnClickListener onClickListener, String title, String colorKey) {
        setButton(onClickListener, title, colorKey, null, null);
    }

    private void setButton(View.OnClickListener onClickListener, String title, String colorKey, String backgroundColorKey, String backgroundSelectorColorKey) {
        if (colorKey != null) {
            pickerBottomLayout.setTextColor(getThemedColor(buttonTextColorKey = colorKey));
        }
        pickerBottomLayout.setText(title);
        pickerBottomLayout.setOnClickListener(onClickListener);

        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) pickerBottomLayout.getLayoutParams();
        ViewGroup.MarginLayoutParams shadowParams = (ViewGroup.MarginLayoutParams) shadow[1].getLayoutParams();
        ViewGroup.MarginLayoutParams gridParams = (ViewGroup.MarginLayoutParams) gridView.getLayoutParams();
        ViewGroup.MarginLayoutParams emptyParams = (ViewGroup.MarginLayoutParams) emptyView.getLayoutParams();
        if (backgroundColorKey != null && backgroundSelectorColorKey != null) {
            pickerBottomLayout.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), getThemedColor(backgroundColorKey), getThemedColor(backgroundSelectorColorKey)));
            pickerBottomFrameLayout.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
            params.leftMargin = params.topMargin = params.rightMargin = params.bottomMargin = AndroidUtilities.dp(8);
            emptyParams.bottomMargin = gridParams.bottomMargin = shadowParams.bottomMargin = AndroidUtilities.dp(64);
        } else {
            pickerBottomLayout.setBackground(Theme.createSelectorWithBackgroundDrawable(getThemedColor(Theme.key_dialogBackground), Theme.multAlpha(getThemedColor(Theme.key_text_RedBold), .1f)));
            pickerBottomFrameLayout.setBackgroundColor(Color.TRANSPARENT);
            params.leftMargin = params.topMargin = params.rightMargin = params.bottomMargin = 0;
            emptyParams.bottomMargin = gridParams.bottomMargin = shadowParams.bottomMargin = AndroidUtilities.dp(48);
        }
        containerView.requestLayout();
    }

    public boolean isShowTooltipWhenToggle() {
        return showTooltipWhenToggle;
    }

    public void setShowTooltipWhenToggle(boolean showTooltipWhenToggle) {
        this.showTooltipWhenToggle = showTooltipWhenToggle;
    }

    public void updateColors() {
        updateColors(false);
    }

    private List<ThemeDescription> animatingDescriptions;

    public void updateColors(boolean applyDescriptions) {
        adapter.updateColors();

        titleTextView.setHighlightColor(getThemedColor(Theme.key_dialogLinkSelection));
        stickerPreviewLayout.setBackgroundColor(getThemedColor(Theme.key_dialogBackground) & 0xdfffffff);

        optionsButton.setIconColor(getThemedColor(Theme.key_sheet_other));
        optionsButton.setPopupItemsColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItem), false);
        optionsButton.setPopupItemsColor(getThemedColor(Theme.key_actionBarDefaultSubmenuItemIcon), true);
        optionsButton.setPopupItemsSelectorColor(getThemedColor(Theme.key_dialogButtonSelector));
        optionsButton.redrawPopup(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));

        if (applyDescriptions) {
            if (Theme.isAnimatingColor() && animatingDescriptions == null) {
                animatingDescriptions = getThemeDescriptions();
                for (int i = 0, N = animatingDescriptions.size(); i < N; i++) {
                    animatingDescriptions.get(i).setDelegateDisabled();
                }
            }
            for (int i = 0, N = animatingDescriptions.size(); i < N; i++) {
                final ThemeDescription description = animatingDescriptions.get(i);
                description.setColor(getThemedColor(description.getCurrentKey()), false, false);
            }
        }

        if (!Theme.isAnimatingColor() && animatingDescriptions != null) {
            animatingDescriptions = null;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        final ArrayList<ThemeDescription> descriptions = new ArrayList<>();
        final ThemeDescription.ThemeDescriptionDelegate delegate = this::updateColors;

        descriptions.add(new ThemeDescription(containerView, 0, null, null, new Drawable[]{shadowDrawable}, null, Theme.key_dialogBackground));
        descriptions.add(new ThemeDescription(containerView, 0, null, null, null, null, Theme.key_sheet_scrollUp));

        adapter.getThemeDescriptions(descriptions, delegate);

        descriptions.add(new ThemeDescription(shadow[0], ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogShadowLine));
        descriptions.add(new ThemeDescription(shadow[1], ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogShadowLine));
        descriptions.add(new ThemeDescription(gridView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_dialogScrollGlow));
        descriptions.add(new ThemeDescription(titleTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_dialogTextBlack));
        if (descriptionTextView != null) {
            descriptions.add(new ThemeDescription(descriptionTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_emojiPanelTrendingDescription));
        }
        descriptions.add(new ThemeDescription(titleTextView, ThemeDescription.FLAG_LINKCOLOR, null, null, null, null, Theme.key_dialogTextLink));
        descriptions.add(new ThemeDescription(optionsButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_player_actionBarSelector));
        descriptions.add(new ThemeDescription(pickerBottomLayout, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_dialogBackground));
        descriptions.add(new ThemeDescription(pickerBottomLayout, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_listSelector));
        descriptions.add(new ThemeDescription(pickerBottomLayout, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, buttonTextColorKey));
        descriptions.add(new ThemeDescription(previewSendButton, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_dialogTextBlue2));
        descriptions.add(new ThemeDescription(previewSendButton, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_dialogBackground));
        descriptions.add(new ThemeDescription(previewSendButton, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_listSelector));
        descriptions.add(new ThemeDescription(previewSendButtonShadow, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_dialogShadowLine));

        descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_dialogLinkSelection));
        descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_dialogBackground));

        descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_sheet_other));
        descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_actionBarDefaultSubmenuItem));
        descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_actionBarDefaultSubmenuItemIcon));
        descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_dialogButtonSelector));
        descriptions.add(new ThemeDescription(null, 0, null, null, null, delegate, Theme.key_actionBarDefaultSubmenuBackground));

        return descriptions;
    }

    private class GridAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;
        private int stickersPerRow;
        private SparseArray<Object> cache = new SparseArray<>();
        private SparseArray<TLRPC.StickerSetCovered> positionsToSets = new SparseArray<>();
        private int totalItems;
        private int stickersRowCount;

        public GridAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return totalItems;
        }

        @Override
        public int getItemViewType(int position) {
            if (stickerSetCovereds != null) {
                Object object = cache.get(position);
                if (object != null) {
                    if (object instanceof TLRPC.Document) {
                        return 0;
                    } else {
                        return 2;
                    }
                }
                return 1;
            }
            return 0;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 0:
                    StickerEmojiCell cell = new StickerEmojiCell(context, false) {
                        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(MeasureSpec.makeMeasureSpec(itemSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(itemSize/*AndroidUtilities.dp(82)*/, MeasureSpec.EXACTLY));
                        }
                    };
                    cell.getImageView().setLayerNum(7);
                    view = cell;
                    break;
                case 1:
                    view = new EmptyCell(context);
                    break;
                case 2:
                    view = new FeaturedStickerSetInfoCell(context, 8, true, false, resourcesProvider);
                    break;
            }

            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (stickerSetCovereds != null) {
                switch (holder.getItemViewType()) {
                    case 0:
                        TLRPC.Document sticker = (TLRPC.Document) cache.get(position);
                        ((StickerEmojiCell) holder.itemView).setSticker(sticker, positionsToSets.get(position), false);
                        break;
                    case 1:
                        ((EmptyCell) holder.itemView).setHeight(AndroidUtilities.dp(82));
                        break;
                    case 2:
                        TLRPC.StickerSetCovered stickerSetCovered = stickerSetCovereds.get((Integer) cache.get(position));
                        FeaturedStickerSetInfoCell cell = (FeaturedStickerSetInfoCell) holder.itemView;
                        cell.setStickerSet(stickerSetCovered, false);
                        break;
                }
            } else if (importingStickers != null) {
                ((StickerEmojiCell) holder.itemView).setSticker(importingStickersPaths.get(position));
            } else {
                ((StickerEmojiCell) holder.itemView).setSticker(stickerSet.documents.get(position), stickerSet, showEmoji);
            }
        }

        @Override
        public void notifyDataSetChanged() {
            if (stickerSetCovereds != null) {
                int width = gridView.getMeasuredWidth();
                if (width == 0) {
                    width = AndroidUtilities.displaySize.x;
                }
                stickersPerRow = width / AndroidUtilities.dp(72);
                layoutManager.setSpanCount(stickersPerRow);
                cache.clear();
                positionsToSets.clear();
                totalItems = 0;
                stickersRowCount = 0;
                for (int a = 0; a < stickerSetCovereds.size(); a++) {
                    TLRPC.StickerSetCovered pack = stickerSetCovereds.get(a);
                    ArrayList<TLRPC.Document> documents;
                    if (pack instanceof TLRPC.TL_stickerSetFullCovered) {
                        documents = ((TLRPC.TL_stickerSetFullCovered) pack).documents;
                    } else {
                        documents = pack.covers;
                    }
                    if (documents == null || documents.isEmpty() && pack.cover == null) {
                        continue;
                    }
                    stickersRowCount += Math.ceil(stickerSetCovereds.size() / (float) stickersPerRow);
                    positionsToSets.put(totalItems, pack);
                    cache.put(totalItems++, a);
                    int startRow = totalItems / stickersPerRow;
                    int count;
                    if (!documents.isEmpty()) {
                        count = (int) Math.ceil(documents.size() / (float) stickersPerRow);
                        for (int b = 0; b < documents.size(); b++) {
                            cache.put(b + totalItems, documents.get(b));
                        }
                    } else {
                        count = 1;
                        cache.put(totalItems, pack.cover);
                    }
                    for (int b = 0; b < count * stickersPerRow; b++) {
                        positionsToSets.put(totalItems + b, pack);
                    }
                    totalItems += count * stickersPerRow;
                }
            } else if (importingStickersPaths != null) {
                totalItems = importingStickersPaths.size();
            } else {
                totalItems = stickerSet != null ? stickerSet.documents.size() : 0;
            }
            super.notifyDataSetChanged();
        }

        @Override
        public void notifyItemRemoved(int position) {
            if (importingStickersPaths != null) {
                totalItems = importingStickersPaths.size();
            }
            super.notifyItemRemoved(position);
        }

        public void updateColors() {
            if (stickerSetCovereds != null) {
                for (int i = 0, size = gridView.getChildCount(); i < size; i++) {
                    final View child = gridView.getChildAt(i);
                    if (child instanceof FeaturedStickerSetInfoCell) {
                        ((FeaturedStickerSetInfoCell) child).updateColors();
                    }
                }
            }
        }

        public void getThemeDescriptions(List<ThemeDescription> descriptions, ThemeDescription.ThemeDescriptionDelegate delegate) {
            if (stickerSetCovereds != null) {
                FeaturedStickerSetInfoCell.createThemeDescriptions(descriptions, gridView, delegate);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (ContentPreviewViewer.getInstance().isVisible()) {
            ContentPreviewViewer.getInstance().closeWithMenu();
            return;
        }
        super.onBackPressed();
    }
}
