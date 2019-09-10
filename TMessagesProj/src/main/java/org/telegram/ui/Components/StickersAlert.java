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
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.graphics.Rect;
import android.os.Build;
import android.text.Selection;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FileRefController;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.FeaturedStickerSetInfoCell;
import org.telegram.ui.Cells.StickerEmojiCell;
import org.telegram.ui.ContentPreviewViewer;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class StickersAlert extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    public interface StickersAlertDelegate {
        void onStickerSelected(TLRPC.Document sticker, Object parent, boolean clearsInputField, boolean notify, int scheduleDate);
        boolean canSchedule();
        boolean isInScheduleMode();
    }

    public interface StickersAlertInstallDelegate {
        void onStickerSetInstalled();
        void onStickerSetUninstalled();
    }

    private static class LinkMovementMethodMy extends LinkMovementMethod {
        @Override
        public boolean onTouchEvent(TextView widget, Spannable buffer, MotionEvent event) {
            try {
                boolean result = super.onTouchEvent(widget, buffer, event);
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    Selection.removeSelection(buffer);
                }
                return result;
            } catch (Exception e) {
                FileLog.e(e);
            }
            return false;
        }
    }

    private Pattern urlPattern;
    private RecyclerListView gridView;
    private GridAdapter adapter;
    private TextView titleTextView;
    private ActionBarMenuItem optionsButton;
    private TextView pickerBottomLayout;
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
    private int itemSize;

    private TLRPC.TL_messages_stickerSet stickerSet;
    private TLRPC.Document selectedSticker;
    private TLRPC.InputStickerSet inputStickerSet;
    private ArrayList<TLRPC.StickerSetCovered> stickerSetCovereds;

    private StickersAlertDelegate delegate;
    private StickersAlertInstallDelegate installDelegate;

    private int scrollOffsetY;
    private int reqId;
    private boolean ignoreLayout;
    private boolean showEmoji;

    private boolean clearsInputField;

    private ContentPreviewViewer.ContentPreviewViewerDelegate previewDelegate = new ContentPreviewViewer.ContentPreviewViewerDelegate() {
        @Override
        public void sendSticker(TLRPC.Document sticker, Object parent, boolean notify, int scheduleDate) {
            if (delegate == null) {
                return;
            }
            delegate.onStickerSelected(sticker, parent, clearsInputField, notify, scheduleDate);
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
        public boolean needSend() {
            return previewSendButton.getVisibility() == View.VISIBLE;
        }

        @Override
        public boolean needOpen() {
            return false;
        }
    };

    public StickersAlert(Context context, Object parentObject, TLRPC.Photo photo) {
        super(context, false, 1);
        parentActivity = (Activity) context;
        final TLRPC.TL_messages_getAttachedStickers req = new TLRPC.TL_messages_getAttachedStickers();
        TLRPC.TL_inputStickeredMediaPhoto inputStickeredMediaPhoto = new TLRPC.TL_inputStickeredMediaPhoto();
        inputStickeredMediaPhoto.id = new TLRPC.TL_inputPhoto();
        inputStickeredMediaPhoto.id.id = photo.id;
        inputStickeredMediaPhoto.id.access_hash = photo.access_hash;
        inputStickeredMediaPhoto.id.file_reference = photo.file_reference;
        if (inputStickeredMediaPhoto.id.file_reference == null) {
            inputStickeredMediaPhoto.id.file_reference = new byte[0];
        }
        req.media = inputStickeredMediaPhoto;
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

    public StickersAlert(Context context, BaseFragment baseFragment, TLRPC.InputStickerSet set, TLRPC.TL_messages_stickerSet loadedSet, StickersAlertDelegate stickersAlertDelegate) {
        super(context, false, 1);
        delegate = stickersAlertDelegate;
        inputStickerSet = set;
        stickerSet = loadedSet;
        parentFragment = baseFragment;
        loadStickerSet();
        init(context);
    }

    @Override
    public void show() {
        super.show();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.stopAllHeavyOperations, 2);
    }

    public void setClearsInputField(boolean value) {
        clearsInputField = value;
    }

    public boolean isClearsInputField() {
        return clearsInputField;
    }

    private void loadStickerSet() {
        if (inputStickerSet != null) {
            if (stickerSet == null && inputStickerSet.short_name != null) {
                stickerSet = MediaDataController.getInstance(currentAccount).getStickerSetByName(inputStickerSet.short_name);
            }
            if (stickerSet == null) {
                stickerSet = MediaDataController.getInstance(currentAccount).getStickerSetById(inputStickerSet.id);
            }
            if (stickerSet == null) {
                TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
                req.stickerset = inputStickerSet;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    reqId = 0;
                    if (error == null) {
                        optionsButton.setVisibility(View.VISIBLE);
                        stickerSet = (TLRPC.TL_messages_stickerSet) response;
                        showEmoji = !stickerSet.set.masks;
                        updateSendButton();
                        updateFields();
                        adapter.notifyDataSetChanged();
                    } else {
                        Toast.makeText(getContext(), LocaleController.getString("AddStickersNotFound", R.string.AddStickersNotFound), Toast.LENGTH_SHORT).show();
                        dismiss();
                    }
                }));
            } else if (adapter != null) {
                updateSendButton();
                updateFields();
                adapter.notifyDataSetChanged();
            }
        }
        if (stickerSet != null) {
            showEmoji = !stickerSet.set.masks;
        }
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
                itemSize = (MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(36)) / 5;
                int contentSize;
                if (stickerSetCovereds != null) {
                    contentSize = AndroidUtilities.dp(48 + 8) + AndroidUtilities.dp(60) * stickerSetCovereds.size() + adapter.stickersRowCount * AndroidUtilities.dp(82) + backgroundPaddingTop + AndroidUtilities.dp(24);
                } else {
                    contentSize = AndroidUtilities.dp(48 + 48) + Math.max(3, (stickerSet != null ? (int) Math.ceil(stickerSet.documents.size() / 5.0f) : 0)) * AndroidUtilities.dp(82) + backgroundPaddingTop + AndroidUtilities.statusBarHeight;
                }
                int padding = contentSize < (height / 5 * 3.2) ? 0 : (height / 5 * 2);
                if (padding != 0 && contentSize < height) {
                    padding -= (height - contentSize);
                }
                if (padding == 0) {
                    padding = backgroundPaddingTop;
                }
                if (stickerSetCovereds != null) {
                    padding += AndroidUtilities.dp(8);
                }
                if (gridView.getPaddingTop() != padding) {
                    ignoreLayout = true;
                    gridView.setPadding(AndroidUtilities.dp(10), padding, AndroidUtilities.dp(10), 0);
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
                    Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_dialogBackground));
                    rect.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + AndroidUtilities.dp(24));
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(12) * radProgress, AndroidUtilities.dp(12) * radProgress, Theme.dialogs_onlineCirclePaint);
                }

                int w = AndroidUtilities.dp(36);
                rect.set((getMeasuredWidth() - w) / 2, y, (getMeasuredWidth() + w) / 2, y + AndroidUtilities.dp(4));
                Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_sheet_scrollUp));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(2), AndroidUtilities.dp(2), Theme.dialogs_onlineCirclePaint);

                if (statusBarHeight > 0) {
                    int color1 = Theme.getColor(Theme.key_dialogBackground);
                    int finalColor = Color.argb(0xff, (int) (Color.red(color1) * 0.8f), (int) (Color.green(color1) * 0.8f), (int) (Color.blue(color1) * 0.8f));
                    Theme.dialogs_onlineCirclePaint.setColor(finalColor);
                    canvas.drawRect(backgroundPaddingLeft, AndroidUtilities.statusBarHeight - statusBarHeight, getMeasuredWidth() - backgroundPaddingLeft, AndroidUtilities.statusBarHeight, Theme.dialogs_onlineCirclePaint);
                }
            }
        };
        containerView.setWillNotDraw(false);
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

        FrameLayout.LayoutParams frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.TOP | Gravity.LEFT);
        frameLayoutParams.topMargin = AndroidUtilities.dp(48);
        shadow[0] = new View(context);
        shadow[0].setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
        shadow[0].setAlpha(0.0f);
        shadow[0].setVisibility(View.INVISIBLE);
        shadow[0].setTag(1);
        containerView.addView(shadow[0], frameLayoutParams);

        gridView = new RecyclerListView(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                boolean result = ContentPreviewViewer.getInstance().onInterceptTouchEvent(event, gridView, 0, previewDelegate);
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
        gridView.setLayoutManager(layoutManager = new GridLayoutManager(getContext(), 5));
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
        gridView.setGlowColor(Theme.getColor(Theme.key_dialogScrollGlow));
        gridView.setOnTouchListener((v, event) -> ContentPreviewViewer.getInstance().onTouch(event, gridView, 0, stickersOnItemClickListener, previewDelegate));
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
                    StickersAlert alert = new StickersAlert(parentActivity, parentFragment, inputStickerSetID, null, null);
                    alert.show();
                }
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

        titleTextView = new TextView(context);
        titleTextView.setLines(1);
        titleTextView.setSingleLine(true);
        titleTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleTextView.setLinkTextColor(Theme.getColor(Theme.key_dialogTextLink));
        titleTextView.setHighlightColor(Theme.getColor(Theme.key_dialogLinkSelection));
        titleTextView.setEllipsize(TextUtils.TruncateAt.END);
        titleTextView.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        titleTextView.setGravity(Gravity.CENTER_VERTICAL);
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        containerView.addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.LEFT | Gravity.TOP, 0, 0, 40, 0));

        optionsButton = new ActionBarMenuItem(context, null, 0, Theme.getColor(Theme.key_sheet_other));
        optionsButton.setLongClickEnabled(false);
        optionsButton.setSubMenuOpenSide(2);
        optionsButton.setIcon(R.drawable.ic_ab_other);
        optionsButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_player_actionBarSelector), 1));
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
        shadow[1].setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
        containerView.addView(shadow[1], frameLayoutParams);

        pickerBottomLayout = new TextView(context);
        pickerBottomLayout.setBackgroundDrawable(Theme.createSelectorWithBackgroundDrawable(Theme.getColor(Theme.key_dialogBackground), Theme.getColor(Theme.key_listSelector)));
        pickerBottomLayout.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        pickerBottomLayout.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        pickerBottomLayout.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        pickerBottomLayout.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        pickerBottomLayout.setGravity(Gravity.CENTER);
        containerView.addView(pickerBottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));

        stickerPreviewLayout = new FrameLayout(context);
        stickerPreviewLayout.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground) & 0xdfffffff);
        stickerPreviewLayout.setVisibility(View.GONE);
        stickerPreviewLayout.setSoundEffectsEnabled(false);
        containerView.addView(stickerPreviewLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        stickerPreviewLayout.setOnClickListener(v -> hidePreview());

        stickerImageView = new BackupImageView(context);
        stickerImageView.setAspectFit(true);
        stickerImageView.setLayerNum(3);
        stickerPreviewLayout.addView(stickerImageView);

        stickerEmojiTextView = new TextView(context);
        stickerEmojiTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 30);
        stickerEmojiTextView.setGravity(Gravity.BOTTOM | Gravity.RIGHT);
        stickerPreviewLayout.addView(stickerEmojiTextView);

        previewSendButton = new TextView(context);
        previewSendButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        previewSendButton.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        previewSendButton.setGravity(Gravity.CENTER);
        previewSendButton.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        previewSendButton.setPadding(AndroidUtilities.dp(29), 0, AndroidUtilities.dp(29), 0);
        previewSendButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        stickerPreviewLayout.addView(previewSendButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT));
        previewSendButton.setOnClickListener(v -> {
            delegate.onStickerSelected(selectedSticker, stickerSet, clearsInputField, true, 0);
            dismiss();
        });

        frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.BOTTOM | Gravity.LEFT);
        frameLayoutParams.bottomMargin = AndroidUtilities.dp(48);
        previewSendButtonShadow = new View(context);
        previewSendButtonShadow.setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
        stickerPreviewLayout.addView(previewSendButtonShadow, frameLayoutParams);

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiDidLoad);

        updateFields();
        updateSendButton();
        adapter.notifyDataSetChanged();
    }

    private void updateSendButton() {
        int size = (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / 2 / AndroidUtilities.density);
        if (delegate != null && (stickerSet == null || !stickerSet.set.masks)) {
            previewSendButton.setText(LocaleController.getString("SendSticker", R.string.SendSticker).toUpperCase());
            stickerImageView.setLayoutParams(LayoutHelper.createFrame(size, size, Gravity.CENTER, 0, 0, 0, 30));
            stickerEmojiTextView.setLayoutParams(LayoutHelper.createFrame(size, size, Gravity.CENTER, 0, 0, 0, 30));
            previewSendButton.setVisibility(View.VISIBLE);
            previewSendButtonShadow.setVisibility(View.VISIBLE);
        } else {
            previewSendButton.setText(LocaleController.getString("Close", R.string.Close).toUpperCase());
            stickerImageView.setLayoutParams(LayoutHelper.createFrame(size, size, Gravity.CENTER));
            stickerEmojiTextView.setLayoutParams(LayoutHelper.createFrame(size, size, Gravity.CENTER));
            previewSendButton.setVisibility(View.GONE);
            previewSendButtonShadow.setVisibility(View.GONE);
        }
    }

    public void setInstallDelegate(StickersAlertInstallDelegate stickersAlertInstallDelegate) {
        installDelegate = stickersAlertInstallDelegate;
    }

    private void onSubItemClick(int id) {
        if (stickerSet == null) {
            return;
        }
        String stickersUrl = "https://" + MessagesController.getInstance(currentAccount).linkPrefix + "/addstickers/" + stickerSet.set.short_name;
        if (id == 1) {
            ShareAlert alert = new ShareAlert(getContext(), null, stickersUrl, false, stickersUrl, false);
            if (parentFragment != null) {
                parentFragment.showDialog(alert);
            } else {
                alert.show();
            }
        } else if (id == 2) {
            try {
                AndroidUtilities.addToClipboard(stickersUrl);
                Toast.makeText(ApplicationLoader.applicationContext, LocaleController.getString("LinkCopied", R.string.LinkCopied), Toast.LENGTH_SHORT).show();
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
            try {
                if (urlPattern == null) {
                    urlPattern = Pattern.compile("@[a-zA-Z\\d_]{1,32}");
                }
                Matcher matcher = urlPattern.matcher(stickerSet.set.title);
                while (matcher.find()) {
                    if (stringBuilder == null) {
                        stringBuilder = new SpannableStringBuilder(stickerSet.set.title);
                        titleTextView.setMovementMethod(new LinkMovementMethodMy());
                    }
                    int start = matcher.start();
                    int end = matcher.end();
                    if (stickerSet.set.title.charAt(start) != '@') {
                        start++;
                    }
                    URLSpanNoUnderline url = new URLSpanNoUnderline(stickerSet.set.title.subSequence(start + 1, end).toString()) {
                        @Override
                        public void onClick(View widget) {
                            MessagesController.getInstance(currentAccount).openByUserName(getURL(), parentFragment, 1);
                            dismiss();
                        }
                    };
                    if (url != null) {
                        stringBuilder.setSpan(url, start, end, 0);
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            titleTextView.setText(stringBuilder != null ? stringBuilder : stickerSet.set.title);

            if (stickerSet.set == null || !MediaDataController.getInstance(currentAccount).isStickerPackInstalled(stickerSet.set.id)) {
                String text;
                if (stickerSet.set.masks) {
                    text = LocaleController.formatString("AddStickersCount", R.string.AddStickersCount, LocaleController.formatPluralString("MasksCount", stickerSet.documents.size())).toUpperCase();
                } else {
                    text = LocaleController.formatString("AddStickersCount", R.string.AddStickersCount, LocaleController.formatPluralString("Stickers", stickerSet.documents.size())).toUpperCase();
                }
                setButton(v -> {
                    dismiss();
                    if (installDelegate != null) {
                        installDelegate.onStickerSetInstalled();
                    }
                    TLRPC.TL_messages_installStickerSet req = new TLRPC.TL_messages_installStickerSet();
                    req.stickerset = inputStickerSet;
                    ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        try {
                            if (error == null) {
                                if (stickerSet.set.masks) {
                                    Toast.makeText(getContext(), LocaleController.getString("AddMasksInstalled", R.string.AddMasksInstalled), Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(getContext(), LocaleController.getString("AddStickersInstalled", R.string.AddStickersInstalled), Toast.LENGTH_SHORT).show();
                                }
                                if (response instanceof TLRPC.TL_messages_stickerSetInstallResultArchive) {
                                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.needReloadArchivedStickers);
                                    if (parentFragment != null && parentFragment.getParentActivity() != null) {
                                        StickersArchiveAlert alert = new StickersArchiveAlert(parentFragment.getParentActivity(), parentFragment, ((TLRPC.TL_messages_stickerSetInstallResultArchive) response).sets);
                                        parentFragment.showDialog(alert.create());
                                    }
                                }
                            } else {
                                Toast.makeText(getContext(), LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred), Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        MediaDataController.getInstance(currentAccount).loadStickers(stickerSet.set.masks ? MediaDataController.TYPE_MASK : MediaDataController.TYPE_IMAGE, false, true);
                    }));
                }, text, Theme.getColor(Theme.key_dialogTextBlue2));
            } else {
                String text;
                if (stickerSet.set.masks) {
                    text = LocaleController.formatString("RemoveStickersCount", R.string.RemoveStickersCount, LocaleController.formatPluralString("MasksCount", stickerSet.documents.size())).toUpperCase();
                } else {
                    text = LocaleController.formatString("RemoveStickersCount", R.string.RemoveStickersCount, LocaleController.formatPluralString("Stickers", stickerSet.documents.size())).toUpperCase();
                }
                if (stickerSet.set.official) {
                    setButton(v -> {
                        if (installDelegate != null) {
                            installDelegate.onStickerSetUninstalled();
                        }
                        dismiss();
                        MediaDataController.getInstance(currentAccount).removeStickersSet(getContext(), stickerSet.set, 1, parentFragment, true);
                    }, text, Theme.getColor(Theme.key_dialogTextRed));
                } else {
                    setButton(v -> {
                        if (installDelegate != null) {
                            installDelegate.onStickerSetUninstalled();
                        }
                        dismiss();
                        MediaDataController.getInstance(currentAccount).removeStickersSet(getContext(), stickerSet.set, 0, parentFragment, true);
                    }, text, Theme.getColor(Theme.key_dialogTextRed));
                }
            }
            adapter.notifyDataSetChanged();
        } else {
            String text = LocaleController.getString("Close", R.string.Close).toUpperCase();
            setButton((v) -> dismiss(), text, Theme.getColor(Theme.key_dialogTextBlue2));
        }
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    @SuppressLint("NewApi")
    private void updateLayout() {
        if (gridView.getChildCount() <= 0) {
            gridView.setTopGlowOffset(scrollOffsetY = gridView.getPaddingTop());
            if (stickerSetCovereds == null) {
                titleTextView.setTranslationY(scrollOffsetY);
                optionsButton.setTranslationY(scrollOffsetY);
                shadow[0].setTranslationY(scrollOffsetY);
            }
            containerView.invalidate();
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
        if (scrollOffsetY != newOffset) {
            gridView.setTopGlowOffset(scrollOffsetY = newOffset);
            if (stickerSetCovereds == null) {
                titleTextView.setTranslationY(scrollOffsetY);
                optionsButton.setTranslationY(scrollOffsetY);
                shadow[0].setTranslationY(scrollOffsetY);
            }
            containerView.invalidate();
        }
    }

    private void hidePreview() {
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(ObjectAnimator.ofFloat(stickerPreviewLayout, View.ALPHA, 0.0f));
        animatorSet.setDuration(200);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                stickerPreviewLayout.setVisibility(View.GONE);
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
    public void dismiss() {
        super.dismiss();
        if (reqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
            reqId = 0;
        }
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiDidLoad);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.startAllHeavyOperations, 2);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.emojiDidLoad) {

            if (gridView != null) {
                int count = gridView.getChildCount();
                for (int a = 0; a < count; a++) {
                    gridView.getChildAt(a).invalidate();
                }
            }
            if (ContentPreviewViewer.getInstance().isVisible()) {
                ContentPreviewViewer.getInstance().close();
            }
            ContentPreviewViewer.getInstance().reset();
        }
    }

    private void setButton(View.OnClickListener onClickListener, String title, int color) {
        pickerBottomLayout.setTextColor(color);
        pickerBottomLayout.setText(title.toUpperCase());
        pickerBottomLayout.setOnClickListener(onClickListener);
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
                    StickerEmojiCell cell = new StickerEmojiCell(context) {
                        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(MeasureSpec.makeMeasureSpec(itemSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(82), MeasureSpec.EXACTLY));
                        }
                    };
                    cell.getImageView().setLayerNum(3);
                    view = cell;
                    break;
                case 1:
                    view = new EmptyCell(context);
                    break;
                case 2:
                    view = new FeaturedStickerSetInfoCell(context, 8);
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
                        /*boolean installing = installingStickerSets.containsKey(stickerSetCovered.set.id);
                        boolean removing = removingStickerSets.containsKey(stickerSetCovered.set.id);
                        if (installing || removing) {
                            if (installing && cell.isInstalled()) {
                                installingStickerSets.remove(stickerSetCovered.set.id);
                                installing = false;
                            } else if (removing && !cell.isInstalled()) {
                                removingStickerSets.remove(stickerSetCovered.set.id);
                                removing = false;
                            }
                        }
                        cell.setDrawProgress(installing || removing);*/
                        break;
                }
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
                    if (pack.covers.isEmpty() && pack.cover == null) {
                        continue;
                    }
                    stickersRowCount += Math.ceil(stickerSetCovereds.size() / (float) stickersPerRow);
                    positionsToSets.put(totalItems, pack);
                    cache.put(totalItems++, a);
                    int startRow = totalItems / stickersPerRow;
                    int count;
                    if (!pack.covers.isEmpty()) {
                        count = (int) Math.ceil(pack.covers.size() / (float) stickersPerRow);
                        for (int b = 0; b < pack.covers.size(); b++) {
                            cache.put(b + totalItems, pack.covers.get(b));
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
            } else {
                totalItems = stickerSet != null ? stickerSet.documents.size() : 0;
            }
            super.notifyDataSetChanged();
        }
    }
}
