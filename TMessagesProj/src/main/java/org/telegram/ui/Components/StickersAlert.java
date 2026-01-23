/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
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
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.collection.LongSparseArray;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
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
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
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
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stories.recorder.StoryEntry;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StickersAlert extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    public final static boolean DISABLE_STICKER_EDITOR = false;
    public final static int STICKERS_MAX_COUNT = 120;

    public interface StickersAlertDelegate {
        void onStickerSelected(TLRPC.Document sticker, String query, Object parent, MessageObject.SendAnimationData sendAnimationData, boolean clearsInputField, boolean notify, int scheduleDate, int scheduleRepeatPeriod);
        boolean canSchedule();
        boolean isInScheduleMode();
    }

    public interface StickersAlertInstallDelegate {
        void onStickerSetInstalled();
        void onStickerSetUninstalled();
    }

    public interface StickersAlertCustomButtonDelegate {
        int getCustomButtonTextColorKey();
        int getCustomButtonRippleColorKey();
        int getCustomButtonColorKey();
        String getCustomButtonText();
        boolean onCustomButtonPressed();
    }

    private boolean wasLightStatusBar;

    private Pattern urlPattern;
    private RecyclerListView gridView;
    private GridAdapter adapter;
    private ItemTouchHelper dragAndDropHelper;
    private TLRPC.Document draggedDocument;
    private LinkSpanDrawable.LinksTextView titleTextView;
    private TextView descriptionTextView;
    private ActionBarMenuItem optionsButton;
    private ActionBarMenuSubItem deleteItem;
    private AnimatedTextView pickerBottomLayout;
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
    private boolean isEditModeEnabled;

    public TLRPC.TL_messages_stickerSet stickerSet;
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
    private int buttonTextColorKey;
    private final StickersShaker stickersShaker = new StickersShaker();

    private ContentPreviewViewer.ContentPreviewViewerDelegate previewDelegate = new ContentPreviewViewer.ContentPreviewViewerDelegate() {
        @Override
        public boolean can() {
            return stickerSet == null || stickerSet.set == null || !stickerSet.set.emojis;
        }

        @Override
        public void sendSticker(TLRPC.Document sticker, String query, Object parent, boolean notify, int scheduleDate, int scheduleRepeatPeriod) {
            if (delegate == null) {
                return;
            }
            delegate.onStickerSelected(sticker, query, parent, null, clearsInputField, notify, scheduleDate, 0);
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
        public boolean canDeleteSticker(TLRPC.Document document) {
            return true;
        }

        @Override
        public void deleteSticker(TLRPC.Document document) {
            stickerSet.documents.remove(document);
            boolean empty = stickerSet.documents.isEmpty();
            if (empty)
                dismiss();
            adapter.notifyDataSetChanged();
            AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER, resourcesProvider);
            progressDialog.showDelayed(350);
            TLRPC.TL_stickers_removeStickerFromSet req = new TLRPC.TL_stickers_removeStickerFromSet();
            req.sticker = MediaDataController.getInputStickerSetItem(document, "").document;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response instanceof TLRPC.TL_messages_stickerSet) {
                    MediaDataController.getInstance(UserConfig.selectedAccount).putStickerSet((TLRPC.TL_messages_stickerSet) response);
                    if (empty) {
                        MediaDataController.getInstance(UserConfig.selectedAccount).toggleStickerSet(null, response, 0, null, false, false);
                    } else {
                        stickerSet = (TLRPC.TL_messages_stickerSet) response;
                        loadStickerSet(false);
                        updateFields();
                    }
                }
                progressDialog.dismiss();
            }));
        }

        @Override
        public boolean canEditSticker() {
            return true;
        }

        @Override
        public void editSticker(TLRPC.Document document) {
            final ChatActivity chatActivity;
            if (parentFragment instanceof ChatActivity) {
                chatActivity = (ChatActivity) parentFragment;
            } else {
                chatActivity = null;
            }
            if (MessageObject.isStaticStickerDocument(document)) {
                final ArrayList<Object> photos = new ArrayList<>();
                File file = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(document, true);
                if (file == null || !file.exists()) {
                    return;
                }
                AndroidUtilities.runOnUIThread(() -> {
                    final MediaController.PhotoEntry entry = new MediaController.PhotoEntry(0, 0, 0, file.getAbsolutePath(), 0, false, 0, 0, 0);
                    photos.add(entry);
                    PhotoViewer.getInstance().setParentActivity(parentFragment.getParentActivity(), resourcesProvider);
                    PhotoViewer.getInstance().openPhotoForSelect(photos, 0, PhotoViewer.SELECT_TYPE_STICKER, false, new PhotoViewer.EmptyPhotoViewerProvider() {
                        @Override
                        public boolean allowCaption() {
                            return false;
                        }
                    }, chatActivity);
                    PhotoViewer.getInstance().enableStickerMode(document, false, null);
                    ContentPreviewViewer.getInstance().setStickerSetForCustomSticker(stickerSet);
                }, 300);
            } else {
                AndroidUtilities.runOnUIThread(() -> {
                    File file = StoryEntry.makeCacheFile(currentAccount, "webp");
                    int w = 512, h = 512;
                    int maxSide;
                    switch (SharedConfig.getDevicePerformanceClass()) {
                        case SharedConfig.PERFORMANCE_CLASS_LOW:
                            maxSide = 1280;
                            break;
                        default:
                        case SharedConfig.PERFORMANCE_CLASS_AVERAGE:
                            maxSide = 2560;
                            break;
                        case SharedConfig.PERFORMANCE_CLASS_HIGH:
                            maxSide = 3840;
                            break;
                    }
                    Size size = new Size(w, h);
                    size.width = maxSide;
                    size.height = (float) Math.floor(size.width * h / w);
                    if (size.height > maxSide) {
                        size.height = maxSide;
                        size.width = (float) Math.floor(size.height * w / h);
                    }
                    Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    try {
                        b.compress(Bitmap.CompressFormat.WEBP, 100, new FileOutputStream(file));
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                    b.recycle();

                    ArrayList<Object> arrayList = new ArrayList<>();
                    final MediaController.PhotoEntry entry = new MediaController.PhotoEntry(0, 0, 0, file.getAbsolutePath(), 0, false, 0, 0, 0);
                    arrayList.add(entry);
                    VideoEditedInfo.MediaEntity entity = new VideoEditedInfo.MediaEntity();
                    entity.type = VideoEditedInfo.MediaEntity.TYPE_STICKER;
                    entity.parentObject = stickerSet;
                    entity.text = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(document, true).getAbsolutePath();
                    entity.x = .5f - (float) Math.min(w, h) / w / 2f;
                    entity.y = .5f - (float) Math.min(w, h) / h / 2f;
                    entity.width = (float) Math.min(w, h) / w;
                    entity.height = (float) Math.min(w, h) / h;
                    float side = (float) Math.floor(size.width * 0.5);
                    entity.viewWidth = (int) side;
                    entity.viewHeight = (int) side;
                    entity.scale = 2f;
                    entity.document = document;
                    if (MessageObject.isAnimatedStickerDocument(document, true) || MessageObject.isVideoStickerDocument(document)) {
                        boolean isAnimatedSticker = MessageObject.isAnimatedStickerDocument(document, true);
                        entity.subType |= isAnimatedSticker ? 1 : 4;
                    }
                    entry.mediaEntities = new ArrayList<>();
                    entry.mediaEntities.add(entity);
                    entry.averageDuration = 3000L;
                    if (MessageObject.isAnimatedStickerDocument(document, true)) {
                        File stickerFile = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(document, true);
                        if (stickerFile != null) {
                            try {
                                entry.averageDuration = (long) (RLottieDrawable.getDuration(stickerFile.getAbsolutePath(), null) * 1000L);
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    } else if (MessageObject.isVideoStickerDocument(document)) {
                        entry.averageDuration = (long) (MessageObject.getDocumentDuration(document) * 1000L);
                    }
                    PhotoViewer.getInstance().setParentActivity(parentFragment.getParentActivity(), resourcesProvider);
                    PhotoViewer.getInstance().openPhotoForSelect(arrayList, 0, PhotoViewer.SELECT_TYPE_STICKER, false, new PhotoViewer.EmptyPhotoViewerProvider() {
                        @Override
                        public boolean allowCaption() {
                            return false;
                        }
                    }, chatActivity);
                    PhotoViewer.getInstance().enableStickerMode(document, true, null);
                    ContentPreviewViewer.getInstance().setStickerSetForCustomSticker(stickerSet);
                }, 300);
            }
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
        this.resourcesProvider = resourcesProvider;
        fixNavigationBar();
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
            if (error == null && response instanceof Vector) {
                Vector<TLRPC.StickerSetCovered> vector = (Vector<TLRPC.StickerSetCovered>) response;
                if (vector.objects.isEmpty()) {
                    dismiss();
                } else if (vector.objects.size() == 1) {
                    TLRPC.StickerSetCovered set = vector.objects.get(0);
                    inputStickerSet = new TLRPC.TL_inputStickerSetID();
                    inputStickerSet.id = set.set.id;
                    inputStickerSet.access_hash = set.set.access_hash;
                    loadStickerSet(false);
                } else {
                    stickerSetCovereds = new ArrayList<>();
                    stickerSetCovereds.addAll(vector.objects);
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

    public StickersAlert(Context context, Vector<TLRPC.StickerSetCovered> vector, Theme.ResourcesProvider resourcesProvider) {
        super(context, false, resourcesProvider);
        this.resourcesProvider = resourcesProvider;
        fixNavigationBar();
        parentActivity = (Activity) context;
        if (vector.objects.isEmpty()) {
            return;
        } else if (vector.objects.size() == 1) {
            final TLRPC.StickerSetCovered set = vector.objects.get(0);
            inputStickerSet = new TLRPC.TL_inputStickerSetID();
            inputStickerSet.id = set.set.id;
            inputStickerSet.access_hash = set.set.access_hash;
            loadStickerSet(false);
            init(context);
        } else {
            stickerSetCovereds = new ArrayList<>();
            stickerSetCovereds.addAll(vector.objects);
            init(context);
            gridView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 48));
            titleTextView.setVisibility(View.GONE);
            shadow[0].setVisibility(View.GONE);
            adapter.notifyDataSetChanged();
        }
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

    public StickersAlert(Context context, BaseFragment baseFragment, TLRPC.InputStickerSet set, TLRPC.TL_messages_stickerSet loadedSet, StickersAlertDelegate stickersAlertDelegate, boolean forceRequest) {
        this(context, baseFragment, set, loadedSet, stickersAlertDelegate, null, forceRequest);
    }

    public StickersAlert(Context context, BaseFragment baseFragment, TLRPC.InputStickerSet set, TLRPC.TL_messages_stickerSet loadedSet, StickersAlertDelegate stickersAlertDelegate, Theme.ResourcesProvider resourcesProvider, boolean forceRequest) {
        super(context, false, resourcesProvider);
        fixNavigationBar();
        delegate = stickersAlertDelegate;
        inputStickerSet = set;
        stickerSet = loadedSet;
        parentFragment = baseFragment;
        loadStickerSet(forceRequest);
        init(context);
    }

    public void setClearsInputField(boolean value) {
        clearsInputField = value;
    }

    public boolean isClearsInputField() {
        return clearsInputField;
    }

    public void loadStickerSet(boolean force) {
        if (inputStickerSet != null) {
            final MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
            if (!force) {
                if (stickerSet == null && inputStickerSet.short_name != null) {
                    stickerSet = mediaDataController.getStickerSetByName(inputStickerSet.short_name);
                }
                if (stickerSet == null) {
                    stickerSet = mediaDataController.getStickerSetById(inputStickerSet.id);
                }
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
                        mediaDataController.putStickerSet(stickerSet, false);
                        if (stickerSet != null && stickerSet.documents.isEmpty()) {
                            dismiss();
                            return;
                        }
                        showEmoji = stickerSet != null && stickerSet.set != null && !stickerSet.set.masks;
                        checkPremiumStickers();
                        mediaDataController.preloadStickerSetThumb(stickerSet);
                        updateSendButton();
                        updateFields();
                        updateDescription();
                        adapter.notifyDataSetChanged();
                    } else {
                        dismiss();
                        if (parentFragment != null) {
                            BulletinFactory.of(parentFragment).createErrorBulletin(LocaleController.getString(R.string.AddStickersNotFound)).show();
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

    public void updateStickerSet(TLRPC.TL_messages_stickerSet set) {
        stickerSet = set;
        if (adapter != null) {
            updateSendButton();
            updateFields();
            adapter.notifyDataSetChanged();
        }
        updateDescription();
        MediaDataController.getInstance(currentAccount).preloadStickerSetThumb(stickerSet);
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
                    adapter.stickersPerRow = Math.max(1, width / dp(AndroidUtilities.isTablet() ? 60 : 45));
                    itemSize = (int) ((MeasureSpec.getSize(widthMeasureSpec) - dp(36)) / adapter.stickersPerRow);
                    itemHeight = itemSize;
                } else {
                    adapter.stickersPerRow = 5;
                    itemSize = (int) ((MeasureSpec.getSize(widthMeasureSpec) - dp(36)) / adapter.stickersPerRow);
                    itemHeight = dp(82);
                }
                float spansCount = adapter.stickersPerRow;
                int contentSize;
                MarginLayoutParams params = (MarginLayoutParams) gridView.getLayoutParams();
                if (importingStickers != null) {
                    contentSize = dp(48) + params.bottomMargin + Math.max(3, (int) Math.ceil(importingStickers.size() / spansCount)) * itemHeight + backgroundPaddingTop + AndroidUtilities.statusBarHeight;
                } else if (stickerSetCovereds != null) {
                    contentSize = dp(8) + params.bottomMargin + dp(60) * stickerSetCovereds.size() + adapter.stickersRowCount * itemHeight + backgroundPaddingTop + dp(24);
                } else {
                    contentSize = dp(48) + params.bottomMargin + (Math.max(isEmoji() ? 2 : 3, (stickerSet != null ? (int) Math.ceil(stickerSet.documents.size() / spansCount) : 0))) * itemHeight + backgroundPaddingTop + AndroidUtilities.statusBarHeight;
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
                    padding += dp(32) + descriptionTextView.getMeasuredHeight();
                }
                if (stickerSetCovereds != null) {
                    padding += dp(8);
                }
                if (gridView.getPaddingTop() != padding) {
                    ignoreLayout = true;
                    gridView.setPadding(dp(10), padding, dp(10), dp(8));
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
                int statusBarHeight = 0;
                float radProgress = 1.0f;
                if (Build.VERSION.SDK_INT >= 21) {
                    top += AndroidUtilities.statusBarHeight;
                    y += AndroidUtilities.statusBarHeight;

                    if (fullHeight) {
                        if (top + backgroundPaddingTop < AndroidUtilities.statusBarHeight * 2) {
                            int diff = Math.min(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight * 2 - top - backgroundPaddingTop);
                            top -= diff;
                            radProgress = 1.0f - Math.min(1.0f, (diff * 2) / (float) AndroidUtilities.statusBarHeight);
                        }
                        if (top + backgroundPaddingTop < AndroidUtilities.statusBarHeight) {
                            statusBarHeight = Math.min(AndroidUtilities.statusBarHeight, AndroidUtilities.statusBarHeight - top - backgroundPaddingTop);
                        }
                    }
                }

                shadowDrawable.setBounds(0, top, getMeasuredWidth(), getMeasuredHeight());
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
                Theme.dialogs_onlineCirclePaint.setAlpha((int) (Theme.dialogs_onlineCirclePaint.getAlpha() * Math.max(0, Math.min(1f, (y - AndroidUtilities.statusBarHeight) / (float) AndroidUtilities.dp(16)))));
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
        frameLayoutParams.topMargin = dp(48);
        shadow[0] = new View(context);
        shadow[0].setBackgroundColor(getThemedColor(Theme.key_dialogShadowLine));
        shadow[0].setAlpha(0.0f);
        shadow[0].setVisibility(View.INVISIBLE);
        shadow[0].setTag(1);
        containerView.addView(shadow[0], frameLayoutParams);

        gridView = new RecyclerListView(context) {
            @Override
            public boolean drawChild(Canvas canvas, View child, long drawingTime) {
                if (child instanceof StickerEmojiCell) {
                    if (isEditModeEnabled) {
                        int pos = gridView.getChildViewHolder(child).getAdapterPosition();
                        canvas.save();
                        canvas.rotate(stickersShaker.getRotationValueForPos(pos), child.getLeft() + (child.getMeasuredWidth() / 2f), child.getTop() + (child.getMeasuredHeight() / 2f));
                        canvas.translate(stickersShaker.getTranslateXValueForPos(pos), stickersShaker.getTranslateYValueForPos(pos));
                        boolean result = super.drawChild(canvas, child, drawingTime);
                        canvas.restore();
                        invalidate();
                        return result;
                    }
                }
                return super.drawChild(canvas, child, drawingTime);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                if (isEditModeEnabled) {
                    return super.onInterceptTouchEvent(event);
                }
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
        dragAndDropHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT, 0) {

            private int movedPos = -1;

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target) {
                if (source.getItemViewType() == GridAdapter.TYPE_ADD_STICKER) {
                    return false;
                }
                if (source.getItemViewType() != target.getItemViewType()) {
                    return false;
                }
                if (stickerSet == null) {
                    return false;
                }
                int fromPosition = source.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                TLRPC.Document removed = stickerSet.documents.remove(fromPosition);
                stickerSet.documents.add(toPosition, removed);
                adapter.notifyItemMoved(fromPosition, toPosition);
                movedPos = toPosition;
                return true;
            }

            @Override
            public void onMoved(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, int fromPos, @NonNull RecyclerView.ViewHolder target, int toPos, int x, int y) {

            }

            @Override
            public int getDragDirs(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                if (viewHolder.getItemViewType() == GridAdapter.TYPE_ADD_STICKER) {
                    return 0;
                }
                return super.getDragDirs(recyclerView, viewHolder);
            }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_IDLE && draggedDocument != null && movedPos > 0) {
                    TLRPC.TL_stickers_changeStickerPosition req = new TLRPC.TL_stickers_changeStickerPosition();
                    req.position = movedPos;
                    req.sticker = MediaDataController.getInputStickerSetItem(draggedDocument, "").document;
                    movedPos = -1;
                    draggedDocument = null;
                } else if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    draggedDocument = ((StickerEmojiCell) viewHolder.itemView).getSticker();
                }
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {

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
        gridView.setPadding(dp(10), 0, dp(10), 0);
        gridView.setClipToPadding(false);
        gridView.setEnabled(true);
        gridView.setGlowColor(getThemedColor(Theme.key_dialogScrollGlow));

        gridView.setOnTouchListener((v, event) -> {
            if (isEditModeEnabled) {
                return false;
            }
            return ContentPreviewViewer.getInstance().onTouch(event, gridView, 0, stickersOnItemClickListener, previewDelegate, resourcesProvider);
        });
        gridView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                updateLayout();
            }
        });
        stickersOnItemClickListener = (view, position) -> {
            if (view instanceof AddStickerBtnView) {
                StickersDialogs.showAddStickerDialog(stickerSet, view, parentFragment, resourcesProvider);
                return;
            }
            if (isEditModeEnabled) {
                return;
            }
            if (stickerSetCovereds != null) {
                TLRPC.StickerSetCovered pack = adapter.positionsToSets.get(position);
                if (pack != null) {
                    ignoreMasterDismiss = true;
                    dismiss();
                    TLRPC.TL_inputStickerSetID inputStickerSetID = new TLRPC.TL_inputStickerSetID();
                    inputStickerSetID.access_hash = pack.set.access_hash;
                    inputStickerSetID.id = pack.set.id;
                    StickersAlert alert = new StickersAlert(parentActivity, parentFragment, inputStickerSetID, null, null, resourcesProvider, false);
                    if (masterDismissListener != null) {
                        alert.setOnDismissListener(di -> masterDismissListener.run());
                    }
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
                stickerEmojiTextView.setText(Emoji.replaceEmoji(selectedStickerPath.emoji, stickerEmojiTextView.getPaint().getFontMetricsInt(), false));
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
                            stickerEmojiTextView.setText(Emoji.replaceEmoji(attribute.alt, stickerEmojiTextView.getPaint().getFontMetricsInt(), false));
                            set = true;
                        }
                        break;
                    }
                }
                if (!set) {
                    stickerEmojiTextView.setText(Emoji.replaceEmoji(MediaDataController.getInstance(currentAccount).getEmojiForSticker(selectedSticker.id), stickerEmojiTextView.getPaint().getFontMetricsInt(), false));
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
        titleTextView.setPadding(dp(18), dp(6), dp(18), dp(6));
        titleTextView.setGravity(Gravity.CENTER_VERTICAL);
        titleTextView.setTypeface(AndroidUtilities.bold());
        containerView.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.LEFT | Gravity.TOP, 0, 0, 40, 0));

        optionsButton = new ActionBarMenuItem(context, null, 0, getThemedColor(Theme.key_sheet_other), resourcesProvider);
        optionsButton.setLongClickEnabled(false);
        optionsButton.setSubMenuOpenSide(2);
        optionsButton.setIcon(R.drawable.ic_ab_other);
        optionsButton.setBackgroundDrawable(Theme.createSelectorDrawable(getThemedColor(Theme.key_player_actionBarSelector), 1));
        containerView.addView(optionsButton, LayoutHelper.createFrame(40, 40, Gravity.TOP | Gravity.RIGHT, 0, 5, 5, 0));
        optionsButton.addSubItem(1, R.drawable.msg_share, LocaleController.getString(R.string.StickersShare));
        optionsButton.addSubItem(2, R.drawable.msg_link, LocaleController.getString(R.string.CopyLink));

        optionsButton.setOnClickListener(v -> {
            checkOptions();
            optionsButton.toggleSubMenu();
        });
        optionsButton.setDelegate(this::onSubItemClick);
        optionsButton.setContentDescription(LocaleController.getString(R.string.AccDescrMoreOptions));
        optionsButton.setVisibility(inputStickerSet != null ? View.VISIBLE : View.GONE);

        RadialProgressView progressView = new RadialProgressView(context);
        emptyView.addView(progressView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.BOTTOM | Gravity.LEFT);
        frameLayoutParams.bottomMargin = dp(48);
        shadow[1] = new View(context);
        shadow[1].setBackgroundColor(getThemedColor(Theme.key_dialogShadowLine));
        containerView.addView(shadow[1], frameLayoutParams);

        pickerBottomLayout = new AnimatedTextView(context);
        pickerBottomLayout.setBackground(Theme.createSelectorWithBackgroundDrawable(getThemedColor(Theme.key_dialogBackground), getThemedColor(Theme.key_listSelector)));
        pickerBottomLayout.setTextColor(getThemedColor(buttonTextColorKey = Theme.key_dialogTextBlue2));
        pickerBottomLayout.setTextSize(dp(14));
        pickerBottomLayout.setPadding(dp(18), 0, dp(18), 0);
        pickerBottomLayout.setTypeface(AndroidUtilities.bold());
        pickerBottomLayout.setGravity(Gravity.CENTER);

        pickerBottomFrameLayout = new FrameLayout(context);
        pickerBottomFrameLayout.addView(pickerBottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));
        containerView.addView(pickerBottomFrameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM));

        premiumButtonView = new PremiumButtonView(context, false, resourcesProvider);
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
        previewSendButton.setPadding(dp(29), 0, dp(29), 0);
        previewSendButton.setTypeface(AndroidUtilities.bold());
        stickerPreviewLayout.addView(previewSendButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT));
        previewSendButton.setOnClickListener(v -> {
            if (importingStickersPaths != null) {
                removeSticker(selectedStickerPath);
                hidePreview();
                selectedStickerPath = null;
            } else {
                delegate.onStickerSelected(selectedSticker, null, stickerSet, null, clearsInputField, true, 0, 0);
                dismiss();
            }
        });

        frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.BOTTOM | Gravity.LEFT);
        frameLayoutParams.bottomMargin = dp(48);
        previewSendButtonShadow = new View(context);
        previewSendButtonShadow.setBackgroundColor(getThemedColor(Theme.key_dialogShadowLine));
        stickerPreviewLayout.addView(previewSendButtonShadow, frameLayoutParams);

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        if (importingStickers != null) {
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileUploaded);
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileUploadFailed);
        }
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.stickersDidLoad);

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
//            descriptionTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.PremiumPreviewEmojiPack)));
//            containerView.addView(descriptionTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 50, 40, 0));
        }
    }

    private void checkOptions() {
        final MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
        boolean notInstalled = stickerSet == null || !mediaDataController.isStickerPackInstalled(stickerSet.set.id);
        if (stickerSet != null && stickerSet.set != null && stickerSet.set.creator && deleteItem == null && !DISABLE_STICKER_EDITOR) {
            optionsButton.addSubItem(3, R.drawable.tabs_reorder, LocaleController.getString(R.string.StickersReorder));
            optionsButton.addSubItem(4, R.drawable.msg_edit, LocaleController.getString(R.string.EditName));
            if (notInstalled) {
                deleteItem = optionsButton.addSubItem(5, R.drawable.msg_delete, LocaleController.getString(R.string.Delete));
            } else {
                ActionBarPopupWindow.ActionBarPopupWindowLayout moreDeleteOptions = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getContext(), 0, resourcesProvider);
                moreDeleteOptions.setFitItems(true);
                ActionBarMenuSubItem backItem = ActionBarMenuItem.addItem(moreDeleteOptions, R.drawable.msg_arrow_back, LocaleController.getString(R.string.Back), false, resourcesProvider);
                backItem.setOnClickListener(view -> optionsButton.getPopupLayout().getSwipeBack().closeForeground());
                ActionBarMenuSubItem deleteForeverItem = ActionBarMenuItem.addItem(moreDeleteOptions, 0, LocaleController.getString(R.string.StickersDeleteForEveryone), false, resourcesProvider);
                int redColor = getThemedColor(Theme.key_text_RedBold);
                deleteForeverItem.setColors(redColor, redColor);
                deleteForeverItem.setSelectorColor(Theme.multAlpha(redColor, .1f));
                ActionBarMenuSubItem deleteForMe = ActionBarMenuItem.addItem(moreDeleteOptions, 0, LocaleController.getString(R.string.StickersRemoveForMe), false, resourcesProvider);
                deleteForMe.setOnClickListener(v -> {
                    optionsButton.closeSubMenu();
                    dismiss();
                    MediaDataController.getInstance(currentAccount).toggleStickerSet(getContext(), stickerSet, 1, parentFragment, true, true);
                });
                deleteForeverItem.setOnClickListener(v -> {
                    optionsButton.closeSubMenu();
                    StickersDialogs.showDeleteForEveryOneDialog(stickerSet.set, resourcesProvider, getContext(), () -> {
                        dismiss();
                        MediaDataController.getInstance(currentAccount).toggleStickerSet(getContext(), stickerSet, 1, parentFragment, false, false);
                    });
                });
                deleteItem = optionsButton.addSwipeBackItem(R.drawable.msg_delete, null, LocaleController.getString(R.string.Delete), moreDeleteOptions);
            }
            optionsButton.addColoredGap();
            View stickersBotBtn = new MessageContainsEmojiButton(currentAccount, getContext(), resourcesProvider, new ArrayList<>(), MessageContainsEmojiButton.STICKERS_BOT_TYPE);
            stickersBotBtn.setOnClickListener(v -> {
                optionsButton.closeSubMenu();
                dismiss();
                AndroidUtilities.runOnUIThread(() -> MessagesController.getInstance(currentAccount).openByUserName("stickers", parentFragment, 1), 200);
            });
            stickersBotBtn.setTag(R.id.fit_width_tag, 1);
            optionsButton.addSubItem(stickersBotBtn, LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);

            int redColor = getThemedColor(Theme.key_text_RedBold);
            deleteItem.setColors(redColor, redColor);
            deleteItem.setSelectorColor(Theme.multAlpha(redColor, .1f));
            if (deleteItem.getRightIcon() != null) {
                deleteItem.getRightIcon().setColorFilter(redColor);
            }
        }
        if (optionsButton.getPopupLayout() != null) {
            optionsButton.getPopupLayout().requestLayout();
        }
    }

    private void updateSendButton() {
        int size = (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / 2 / AndroidUtilities.density);
        if (importingStickers != null) {
            previewSendButton.setText(LocaleController.getString(R.string.ImportStickersRemove));
            previewSendButton.setTextColor(getThemedColor(Theme.key_text_RedBold));
            stickerImageView.setLayoutParams(LayoutHelper.createFrame(size, size, Gravity.CENTER, 0, 0, 0, 30));
            stickerEmojiTextView.setLayoutParams(LayoutHelper.createFrame(size, size, Gravity.CENTER, 0, 0, 0, 30));
            previewSendButton.setVisibility(View.VISIBLE);
            previewSendButtonShadow.setVisibility(View.VISIBLE);
        } else if (delegate != null && (stickerSet == null || !stickerSet.set.masks)) {
            previewSendButton.setText(LocaleController.getString(R.string.SendSticker));
            stickerImageView.setLayoutParams(LayoutHelper.createFrame(size, size, Gravity.CENTER, 0, 0, 0, 30));
            stickerEmojiTextView.setLayoutParams(LayoutHelper.createFrame(size, size, Gravity.CENTER, 0, 0, 0, 30));
            previewSendButton.setVisibility(View.VISIBLE);
            previewSendButtonShadow.setVisibility(View.VISIBLE);
        } else {
            previewSendButton.setText(LocaleController.getString(R.string.Close));
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
                protected void onSend(LongSparseArray<TLRPC.Dialog> dids, int count, TLRPC.TL_forumTopic topic, boolean showToast) {
                    if (!showToast) return;
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
        } else if (id == 3) {
            if (isEditModeEnabled) {
                disableEditMode();
            } else {
                enableEditMode();
            }
        } else if (id == 4) {
            StickersDialogs.showNameEditorDialog(stickerSet.set, resourcesProvider, getContext(), (text, whenDone) -> {
                titleTextView.setText(text);
                TLRPC.TL_stickers_renameStickerSet req = new TLRPC.TL_stickers_renameStickerSet();
                req.stickerset = MediaDataController.getInputStickerSet(stickerSet.set);
                req.title = text.toString();
                ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    boolean success = false;
                    if (response instanceof TLRPC.TL_messages_stickerSet) {
                        TLRPC.TL_messages_stickerSet newset = (TLRPC.TL_messages_stickerSet) response;
                        MediaDataController.getInstance(UserConfig.selectedAccount).putStickerSet(newset);
                        if (!MediaDataController.getInstance(UserConfig.selectedAccount).isStickerPackInstalled(newset.set.id)) {
                            MediaDataController.getInstance(UserConfig.selectedAccount).toggleStickerSet(null, newset, 2, null, false, false);
                        }
                        success = true;
                    }
                    whenDone.run(success);
                }));
            });
        } else if (id == 5) {
            StickersDialogs.showDeleteForEveryOneDialog(stickerSet.set, resourcesProvider, getContext(), () -> {
                dismiss();
                MediaDataController.getInstance(currentAccount).toggleStickerSet(getContext(), stickerSet, 1, parentFragment, false, false);
            });
        }
    }

    private void updateFields() {
        if (titleTextView == null) {
            return;
        }
        if (stickerSet != null && stickerSet.documents != null && !stickerSet.documents.isEmpty()) {
            SpannableStringBuilder stringBuilder = null;
            CharSequence title = stickerSet.set.title;
            title = Emoji.replaceEmoji(title, titleTextView.getPaint().getFontMetricsInt(), false);
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
                adapter.stickersPerRow = Math.max(1, width / dp(AndroidUtilities.isTablet() ? 60 : 45));
            } else {
                adapter.stickersPerRow = 5;
            }
            layoutManager.setSpanCount(adapter.stickersPerRow);

            if (stickerSet != null && stickerSet.set != null && stickerSet.set.emojis && !UserConfig.getInstance(currentAccount).isPremium() && customButtonDelegate == null) {
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

                    setButton(null, null, -1);
                    premiumButtonView.setButton(LocaleController.getString(R.string.UnlockPremiumEmoji), e -> {
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

            final MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
            boolean notInstalled;
            if (stickerSet != null && stickerSet.set != null && stickerSet.set.emojis) {
                ArrayList<TLRPC.TL_messages_stickerSet> sets = mediaDataController.getStickerSets(MediaDataController.TYPE_EMOJIPACKS);
                boolean has = false;
                for (int i = 0; sets != null && i < sets.size(); ++i) {
                    if (sets.get(i) != null && sets.get(i).set != null && sets.get(i).set.id == stickerSet.set.id) {
                        has = true;
                        break;
                    }
                }
                notInstalled = !has;
            } else {
                notInstalled = stickerSet == null || stickerSet.set == null || !mediaDataController.isStickerPackInstalled(stickerSet.set.id);
            }

            if (customButtonDelegate != null) {
                setButton(v -> {
                    if (customButtonDelegate.onCustomButtonPressed()) {
                        dismiss();
                    }
                }, customButtonDelegate.getCustomButtonText(), customButtonDelegate.getCustomButtonTextColorKey(), customButtonDelegate.getCustomButtonColorKey(), customButtonDelegate.getCustomButtonRippleColorKey());
                return;
            }
            if (notInstalled) {
                int type = MediaDataController.TYPE_IMAGE;
                if (stickerSet != null && stickerSet.set != null && stickerSet.set.emojis) {
                    type = MediaDataController.TYPE_EMOJIPACKS;
                } else if (stickerSet != null && stickerSet.set != null && stickerSet.set.masks) {
                    type = MediaDataController.TYPE_MASK;
                }
                if (!mediaDataController.areStickersLoaded(type)) {
                    mediaDataController.checkStickers(type);
                    setButton(null, "", -1, -1, -1);
                    return;
                }
            }
            if (notInstalled) {
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
                        }
                        try {
                            if (error == null) {
                                if (showTooltipWhenToggle) {
                                    Bulletin.make(parentFragment, new StickerSetBulletinLayout(pickerBottomFrameLayout.getContext(), stickerSet, StickerSetBulletinLayout.TYPE_ADDED, null, resourcesProvider), Bulletin.DURATION_SHORT).show();
                                }
                                if (response instanceof TLRPC.TL_messages_stickerSetInstallResultArchive) {
                                    MediaDataController.getInstance(currentAccount).processStickerSetInstallResultArchive(parentFragment, true, type, (TLRPC.TL_messages_stickerSetInstallResultArchive) response);
                                }
                            } else {
                                Toast.makeText(getContext(), LocaleController.getString(R.string.ErrorOccurred), Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        MediaDataController.getInstance(currentAccount).loadStickers(type, false, true);
                    }));
                }, text, Theme.key_featuredStickers_buttonText, Theme.key_featuredStickers_addButton, Theme.key_featuredStickers_addButtonPressed);
            } else {
                String text;
                boolean isEditModeAvailable = stickerSet.set.creator && !DISABLE_STICKER_EDITOR;
                if (isEditModeAvailable) {
                    text = LocaleController.getString(isEditModeEnabled ? R.string.Done : R.string.EditStickers);
                } else if (stickerSet.set.masks) {
                    text = LocaleController.formatPluralString("RemoveManyMasksCount", stickerSet.documents.size());
                } else if (stickerSet.set.emojis) {
                    text = LocaleController.formatPluralString("RemoveManyEmojiCount", stickerSet.documents.size());
                } else {
                    text = LocaleController.formatPluralString("RemoveManyStickersCount", stickerSet.documents.size());
                }
                if (isEditModeAvailable) {
                    setButton(v -> {
                        if (isEditModeEnabled) {
                            disableEditMode();
                        } else {
                            enableEditMode();
                        }
                    }, text, Theme.key_featuredStickers_buttonText, Theme.key_featuredStickers_addButton, Theme.key_featuredStickers_addButtonPressed);
                } else if (stickerSet.set.official) {
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
                setButton(null, LocaleController.getString(R.string.ImportStickersProcessing), Theme.key_dialogTextGray2);
                pickerBottomLayout.setEnabled(false);
            }
        } else {
            String text = LocaleController.getString(R.string.Close);
            setButton((v) -> dismiss(), text, Theme.key_dialogTextBlue2);
        }
    }

    private void showNameEnterAlert() {
        Context context = getContext();

        int[] state = new int[]{0};
        FrameLayout fieldLayout = new FrameLayout(context);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(LocaleController.getString(R.string.ImportStickersEnterName));
        builder.setPositiveButton(LocaleController.getString(R.string.Next), (dialog, which) -> {

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
        textView.setPadding(0, dp(4), 0, 0);
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
        editText.setCursorSize(dp(20));
        editText.setCursorWidth(1.5f);
        editText.setPadding(0, dp(4), 0, 0);
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

        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (dialog, which) -> AndroidUtilities.hideKeyboard(editText));

        message.setText(AndroidUtilities.replaceTags(LocaleController.getString(R.string.ImportStickersEnterNameInfo)));
        message.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        message.setPadding(dp(23), dp(12), dp(23), dp(6));
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
                    editText.setPadding(textView.getMeasuredWidth(), dp(4), 0, 0);
                    if (!set) {
                        editText.setText("");
                    }
                    state[0] = 2;
                }));
            } else if (state[0] == 2) {
                state[0] = 3;
                if (!lastNameAvailable) {
                    AndroidUtilities.shakeView(editText);
                    try {
                        editText.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                    } catch (Exception ignored) {}
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
            message.setText(LocaleController.getString(R.string.ImportStickersLinkAvailable));
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
            message.setText(LocaleController.getString(R.string.ImportStickersEnterUrlInfo));
            message.setTextColor(getThemedColor(Theme.key_dialogTextGray2));
            return;
        }
        lastNameAvailable = false;
        if (text != null) {
            if (text.startsWith("_") || text.endsWith("_")) {
                message.setText(LocaleController.getString(R.string.ImportStickersLinkInvalid));
                message.setTextColor(getThemedColor(Theme.key_text_RedRegular));
                return;
            }
            for (int a = 0, N = text.length(); a < N; a++) {
                char ch = text.charAt(a);
                if (!(ch >= '0' && ch <= '9' || ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || ch == '_')) {
                    message.setText(LocaleController.getString(R.string.ImportStickersEnterUrlInfo));
                    message.setTextColor(getThemedColor(Theme.key_text_RedRegular));
                    return;
                }
            }
        }
        if (text == null || text.length() < 5) {
            message.setText(LocaleController.getString(R.string.ImportStickersLinkInvalidShort));
            message.setTextColor(getThemedColor(Theme.key_text_RedRegular));
            return;
        }
        if (text.length() > 32) {
            message.setText(LocaleController.getString(R.string.ImportStickersLinkInvalidLong));
            message.setTextColor(getThemedColor(Theme.key_text_RedRegular));
            return;
        }

        message.setText(LocaleController.getString(R.string.ImportStickersLinkChecking));
        message.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText8));
        lastCheckName = text;
        checkRunnable = () -> {
            TLRPC.TL_stickers_checkShortName req = new TLRPC.TL_stickers_checkShortName();
            req.short_name = text;
            checkReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                checkReqId = 0;
                if (lastCheckName != null && lastCheckName.equals(text)) {
                    if (error == null && response instanceof TLRPC.TL_boolTrue) {
                        message.setText(LocaleController.getString(R.string.ImportStickersLinkAvailable));
                        message.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGreenText));
                        lastNameAvailable = true;
                    } else {
                        message.setText(LocaleController.getString(R.string.ImportStickersLinkTaken));
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
        int firstChildPosition = -1;
        View firstChild = null;
        for (int i = 0; i < gridView.getChildCount(); ++i) {
            View child = gridView.getChildAt(i);
            int position = gridView.getChildAdapterPosition(child);
            if (firstChildPosition == -1 || firstChildPosition > position) {
                firstChild = child;
                firstChildPosition = position;
            }
        }
        int newOffset = 0;
        if (firstChild != null && firstChild.getTop() >= 0) {
            newOffset = (int) firstChild.getTop();
            runShadowAnimation(0, false);
        } else {
            runShadowAnimation(0, true);
        }

        runShadowAnimation(1, true);

        if (scrollOffsetY != newOffset) {
            setScrollOffsetY(newOffset);
        }
    }

    private void setScrollOffsetY(int newOffset) {
        scrollOffsetY = newOffset;
//        gridView.setTopGlowOffset(newOffset);
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
        stickersShaker.stopShake(false);
        if (!ignoreMasterDismiss && masterDismissListener != null) {
            masterDismissListener.run();
        }
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
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.stickersDidLoad);
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
        } else if (id == NotificationCenter.stickersDidLoad) {
            TLRPC.TL_messages_stickerSet newStickerSet = null;
            if (inputStickerSet != null) {
                final MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
                if (newStickerSet == null && inputStickerSet.short_name != null) {
                    newStickerSet = mediaDataController.getStickerSetByName(inputStickerSet.short_name);
                }
                if (newStickerSet == null) {
                    newStickerSet = mediaDataController.getStickerSetById(inputStickerSet.id);
                }
            }
            if (newStickerSet != null && newStickerSet != stickerSet) {
                stickerSet = newStickerSet;
                loadStickerSet(false);
            }
            updateFields();
        }
    }

    private void setButton(View.OnClickListener onClickListener, String title, int colorKey) {
        setButton(onClickListener, title, colorKey, -1, -1);
    }

    private void setButton(View.OnClickListener onClickListener, String title, int colorKey, int backgroundColorKey, int backgroundSelectorColorKey) {
        if (colorKey >= 0) {
            pickerBottomLayout.setTextColor(getThemedColor(buttonTextColorKey = colorKey));
        }
        pickerBottomLayout.setText(title, false);
        pickerBottomLayout.setOnClickListener(onClickListener);

        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) pickerBottomLayout.getLayoutParams();
        ViewGroup.MarginLayoutParams shadowParams = (ViewGroup.MarginLayoutParams) shadow[1].getLayoutParams();
        ViewGroup.MarginLayoutParams gridParams = (ViewGroup.MarginLayoutParams) gridView.getLayoutParams();
        ViewGroup.MarginLayoutParams emptyParams = (ViewGroup.MarginLayoutParams) emptyView.getLayoutParams();
        if (onClickListener == null) {
            pickerBottomLayout.setAlpha(0f);
        } else if (backgroundColorKey >= 0 && backgroundSelectorColorKey >= 0) {
            pickerBottomLayout.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(6), getThemedColor(backgroundColorKey), getThemedColor(backgroundSelectorColorKey)));
            pickerBottomFrameLayout.setBackgroundColor(getThemedColor(Theme.key_dialogBackground));
            params.leftMargin = params.topMargin = params.rightMargin = params.bottomMargin = dp(8);
            emptyParams.bottomMargin = gridParams.bottomMargin = shadowParams.bottomMargin = dp(64);
            if (pickerBottomLayout.getAlpha() < 1f) {
                pickerBottomLayout.animate().alpha(1f).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(240).start();
            }
        } else {
            pickerBottomLayout.setBackground(Theme.createSelectorWithBackgroundDrawable(getThemedColor(Theme.key_dialogBackground), Theme.multAlpha(getThemedColor(Theme.key_text_RedBold), .1f)));
            pickerBottomFrameLayout.setBackgroundColor(Color.TRANSPARENT);
            params.leftMargin = params.topMargin = params.rightMargin = params.bottomMargin = 0;
            emptyParams.bottomMargin = gridParams.bottomMargin = shadowParams.bottomMargin = dp(48);
            if (pickerBottomLayout.getAlpha() < 1f) {
                pickerBottomLayout.animate().alpha(1f).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).setDuration(240).start();
            }
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

        if (deleteItem != null) {
            int redColor = getThemedColor(Theme.key_text_RedBold);
            deleteItem.setColors(redColor, redColor);
            deleteItem.setSelectorColor(Theme.multAlpha(redColor, .1f));
            if (deleteItem.getRightIcon() != null) {
                deleteItem.getRightIcon().setColorFilter(redColor);
            }
        }

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

        public static final int TYPE_ADD_STICKER = 3;

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
            if (stickerSet != null && stickerSet.documents.size() == position) {
                return 3;
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
                    StickerEmojiCell cell = new StickerEmojiCell(context, false, resourcesProvider) {
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
                case TYPE_ADD_STICKER:
                    view = new AddStickerBtnView(context, resourcesProvider);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (stickerSetCovereds != null) {
                switch (holder.getItemViewType()) {
                    case 0:
                        TLRPC.Document sticker = (TLRPC.Document) cache.get(position);
                        StickerEmojiCell cell = (StickerEmojiCell) holder.itemView;
                        cell.setSticker(sticker, positionsToSets.get(position), false);
                        break;
                    case 1:
                        ((EmptyCell) holder.itemView).setHeight(dp(82));
                        break;
                    case 2:
                        TLRPC.StickerSetCovered stickerSetCovered = stickerSetCovereds.get((Integer) cache.get(position));
                        FeaturedStickerSetInfoCell cell2 = (FeaturedStickerSetInfoCell) holder.itemView;
                        cell2.setStickerSet(stickerSetCovered, false);
                        break;
                }
            } else if (importingStickers != null) {
                ((StickerEmojiCell) holder.itemView).setSticker(importingStickersPaths.get(position));
            } else {
                if (holder.getItemViewType() != TYPE_ADD_STICKER) {
                    StickerEmojiCell cell = (StickerEmojiCell) holder.itemView;
                    if (stickerSet == null) return;
                    cell.setSticker(stickerSet.documents.get(position), null, stickerSet, null, showEmoji, isEditModeEnabled);
                    cell.editModeIcon.setOnClickListener(v -> {
                        ContentPreviewViewer.getInstance().setDelegate(previewDelegate);
                        ContentPreviewViewer.getInstance().showMenuFor(cell);
                    });
                }
            }
        }

        @Override
        public void notifyDataSetChanged() {
            if (stickerSetCovereds != null) {
                int width = gridView.getMeasuredWidth();
                if (width == 0) {
                    width = AndroidUtilities.displaySize.x;
                }
                stickersPerRow = width / dp(72);
                layoutManager.setSpanCount(stickersPerRow);
                cache.clear();
                positionsToSets.clear();
                totalItems = 0;
                stickersRowCount = 0;
                for (int a = 0; a < stickerSetCovereds.size(); a++) {
                    TLRPC.StickerSetCovered pack = stickerSetCovereds.get(a);
                    List<TLRPC.Document> documents;
                    if (pack instanceof TLRPC.TL_stickerSetFullCovered) {
                        documents = ((TLRPC.TL_stickerSetFullCovered) pack).documents;
                    } else {
                        documents = pack.covers;
                    }
                    if (documents != null) {
                        documents = documents.subList(0, Math.min(documents.size(), stickersPerRow));
                    }
                    if (documents == null || documents.isEmpty() && pack.cover == null) {
                        continue;
                    }
                    stickersRowCount++;
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
                if (stickerSet != null && stickerSet.set.creator && stickerSet.documents.size() < STICKERS_MAX_COUNT && !DISABLE_STICKER_EDITOR && !stickerSet.set.masks && !stickerSet.set.emojis) {
                    totalItems++;
                }
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

    @SuppressLint("NotifyDataSetChanged")
    public void enableEditMode() {
        if (isEditModeEnabled) {
            return;
        }
        dragAndDropHelper.attachToRecyclerView(gridView);
        isEditModeEnabled = true;
        stickersShaker.startShake();
        AndroidUtilities.forEachViews(gridView, view -> {
            if (view instanceof StickerEmojiCell) {
                ((StickerEmojiCell) view).enableEditMode(true);
            }
        });
        optionsButton.postDelayed(() -> adapter.notifyDataSetChanged(), 200);
//        optionsButton.animate().alpha(0f).start();
        pickerBottomLayout.setText(LocaleController.getString(R.string.Done), true);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void disableEditMode() {
        if (!isEditModeEnabled) {
            return;
        }
        dragAndDropHelper.attachToRecyclerView(null);
        isEditModeEnabled = false;
        stickersShaker.stopShake(true);
        AndroidUtilities.forEachViews(gridView, view -> {
            if (view instanceof StickerEmojiCell) {
                ((StickerEmojiCell) view).disableEditMode(true);
            }
        });
        optionsButton.postDelayed(() -> adapter.notifyDataSetChanged(), 200);
//        optionsButton.animate().alpha(1f).start();
        pickerBottomLayout.setText(LocaleController.getString(R.string.EditStickers), true);
    }

    @Override
    public void onBackPressed() {
        if (ContentPreviewViewer.getInstance().isVisible()) {
            ContentPreviewViewer.getInstance().closeWithMenu();
            return;
        }
        super.onBackPressed();
    }

    private boolean ignoreMasterDismiss;
    private Runnable masterDismissListener;
    public void setOnMasterDismiss(Runnable listener) {
        masterDismissListener = listener;
    }

    private static class AddStickerBtnView extends FrameLayout {

        public AddStickerBtnView(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            View btnView = new View(context);
            Drawable circle = Theme.createRoundRectDrawable(dp(28), Theme.multAlpha(Theme.getColor(Theme.key_chat_emojiPanelIcon, resourcesProvider), .12f));
            Drawable drawable = context.getResources().getDrawable(R.drawable.filled_add_sticker).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_emojiPanelIcon, resourcesProvider), PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(circle, drawable);
            combinedDrawable.setCustomSize(dp(56), dp(56));
            combinedDrawable.setIconSize(dp(24), dp(24));
            btnView.setBackground(combinedDrawable);
            ScaleStateListAnimator.apply(btnView);
            addView(btnView, LayoutHelper.createFrame(56, 56, Gravity.CENTER));
        }
    }

    private static class StickersShaker {
        private static final int MAX_SHAKERS = 6;
        private final List<ValueAnimator> rotateAnimators = new ArrayList<>();
        private final List<ValueAnimator> translateXAnimators = new ArrayList<>();
        private final List<ValueAnimator> translateYAnimators = new ArrayList<>();

        private final List<Float> imageRotations = new ArrayList<>();
        private final List<Float> imageTranslationsX = new ArrayList<>();
        private final List<Float> imageTranslationsY = new ArrayList<>();

        private void init() {
            if (!imageRotations.isEmpty()) return;
            for (int i = 0; i < MAX_SHAKERS; i++) {
                imageRotations.add(0f);
                imageTranslationsX.add(0f);
                imageTranslationsY.add(0f);
            }
        }

        public float getRotationValueForPos(int pos) {
            if (imageRotations.isEmpty()) return 0;
            pos = pos - ((pos / MAX_SHAKERS) * MAX_SHAKERS);
            return imageRotations.get(pos);
        }

        public float getTranslateXValueForPos(int pos) {
            if (imageTranslationsX.isEmpty()) return 0;
            pos = pos - ((pos / MAX_SHAKERS) * MAX_SHAKERS);
            return imageTranslationsX.get(pos);
        }

        public float getTranslateYValueForPos(int pos) {
            if (imageTranslationsY.isEmpty()) return 0;
            pos = pos - ((pos / MAX_SHAKERS) * MAX_SHAKERS);
            return imageTranslationsY.get(pos);
        }

        public void startShake() {
            stopShake(false);
            init();
            for (int i = 0; i < MAX_SHAKERS; i++) {
                final int pos = i;
                int duration = 300;
                long currentTime = (long) (Utilities.random.nextFloat() * duration);

                ValueAnimator rotateAnimator = ValueAnimator.ofFloat(0, -2f, 0, 2f, 0f);
                rotateAnimator.addUpdateListener(animation -> {
                    imageRotations.set(pos, (float) animation.getAnimatedValue());
                });
                rotateAnimator.setRepeatCount(ValueAnimator.INFINITE);
                rotateAnimator.setRepeatMode(ValueAnimator.RESTART);
                rotateAnimator.setInterpolator(new LinearInterpolator());
                rotateAnimator.setCurrentPlayTime(currentTime);
                rotateAnimator.setDuration(duration);
                rotateAnimator.start();

                float max = dp(0.5f);
                ValueAnimator translateXAnimator = ValueAnimator.ofFloat(0, max, 0, -max, 0);
                translateXAnimator.addUpdateListener(animation -> {
                    imageTranslationsX.set(pos, (float) animation.getAnimatedValue());
                });
                translateXAnimator.setRepeatCount(ValueAnimator.INFINITE);
                translateXAnimator.setRepeatMode(ValueAnimator.RESTART);
                translateXAnimator.setInterpolator(new LinearInterpolator());
                translateXAnimator.setCurrentPlayTime(currentTime);
                translateXAnimator.setDuration((long) (duration * 1.2));
                translateXAnimator.start();

                ValueAnimator translateYAnimator = ValueAnimator.ofFloat(0, max, 0 - max, 0);
                translateYAnimator.addUpdateListener(animation -> {
                    imageTranslationsY.set(pos, (float) animation.getAnimatedValue());
                });
                translateYAnimator.setRepeatCount(ValueAnimator.INFINITE);
                translateYAnimator.setRepeatMode(ValueAnimator.RESTART);
                translateYAnimator.setInterpolator(new LinearInterpolator());
                translateYAnimator.setCurrentPlayTime(currentTime);
                translateYAnimator.setDuration(duration);
                translateYAnimator.start();

                rotateAnimators.add(rotateAnimator);
                translateXAnimators.add(translateXAnimator);
                translateYAnimators.add(translateYAnimator);
            }
        }

        public void stopShake(boolean animate) {
            for (int i = 0; i < rotateAnimators.size(); i++) {
                final int pos = i;
                rotateAnimators.get(i).cancel();
                if (animate) {
                    ValueAnimator animator = ValueAnimator.ofFloat(imageRotations.get(i), 0f);
                    animator.addUpdateListener(animation -> {
                        imageRotations.set(pos, (float) animation.getAnimatedValue());
                    });
                    animator.setDuration(100);
                    animator.start();
                }
            }

            for (int i = 0; i < translateXAnimators.size(); i++) {
                final int pos = i;
                translateXAnimators.get(i).cancel();
                if (animate) {
                    ValueAnimator animator = ValueAnimator.ofFloat(imageTranslationsX.get(i), 0f);
                    animator.addUpdateListener(animation -> {
                        imageTranslationsX.set(pos, (float) animation.getAnimatedValue());
                    });
                    animator.setDuration(100);
                    animator.start();
                }
            }

            for (int i = 0; i < translateYAnimators.size(); i++) {
                final int pos = i;
                translateYAnimators.get(i).cancel();
                if (animate) {
                    ValueAnimator animator = ValueAnimator.ofFloat(imageTranslationsY.get(i), 0f);
                    animator.addUpdateListener(animation -> {
                        imageTranslationsY.set(pos, (float) animation.getAnimatedValue());
                    });
                    animator.setDuration(100);
                    animator.start();
                }
            }

            translateYAnimators.clear();
            translateXAnimators.clear();
            rotateAnimators.clear();
        }
    }
}
