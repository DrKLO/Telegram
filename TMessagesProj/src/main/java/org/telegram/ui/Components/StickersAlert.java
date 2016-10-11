/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.*;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimatorListenerAdapterProxy;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.query.StickersQuery;
import org.telegram.messenger.support.widget.GridLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.FeaturedStickerSetInfoCell;
import org.telegram.ui.Cells.StickerEmojiCell;
import org.telegram.ui.StickerPreviewViewer;

import java.util.ArrayList;
import java.util.HashMap;

public class StickersAlert extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    public interface StickersAlertDelegate {
        void onStickerSelected(TLRPC.Document sticker);
    }

    public interface StickersAlertInstallDelegate {
        void onStickerSetInstalled();
        void onStickerSetUninstalled();
    }

    private RecyclerListView gridView;
    private GridAdapter adapter;
    private TextView titleTextView;
    private PickerBottomLayout pickerBottomLayout;
    private FrameLayout stickerPreviewLayout;
    private TextView previewSendButton;
    private View previewSendButtonShadow;
    private BackupImageView stickerImageView;
    private TextView stickerEmojiTextView;
    private RecyclerListView.OnItemClickListener stickersOnItemClickListener;
    private Drawable shadowDrawable;
    private AnimatorSet shadowAnimation[] = new AnimatorSet[2];
    private View shadow[] = new View[2];
    private FrameLayout emptyView;
    private BaseFragment parentFragment;
    private GridLayoutManager layoutManager;
    private Activity parentActivity;

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

    public StickersAlert(Context context, TLRPC.Photo photo) {
        super(context, false);
        parentActivity = (Activity) context;
        TLRPC.TL_messages_getAttachedStickers req = new TLRPC.TL_messages_getAttachedStickers();
        TLRPC.TL_inputStickeredMediaPhoto inputStickeredMediaPhoto = new TLRPC.TL_inputStickeredMediaPhoto();
        inputStickeredMediaPhoto.id = new TLRPC.TL_inputPhoto();
        inputStickeredMediaPhoto.id.id = photo.id;
        inputStickeredMediaPhoto.id.access_hash = photo.access_hash;
        req.media = inputStickeredMediaPhoto;
        reqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
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
                            Toast.makeText(getContext(), LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred) + "\n" + error.text, Toast.LENGTH_SHORT).show();
                            dismiss();
                        }
                    }
                });
            }
        });
        init(context);
    }

    public StickersAlert(Context context, BaseFragment baseFragment, TLRPC.InputStickerSet set, TLRPC.TL_messages_stickerSet loadedSet, StickersAlertDelegate stickersAlertDelegate) {
        super(context, false);
        delegate = stickersAlertDelegate;
        inputStickerSet = set;
        stickerSet = loadedSet;
        parentFragment = baseFragment;
        loadStickerSet();
        init(context);
    }

    private void loadStickerSet() {
        if (inputStickerSet != null) {
            if (stickerSet == null && inputStickerSet.short_name != null) {
                stickerSet = StickersQuery.getStickerSetByName(inputStickerSet.short_name);
            }
            if (stickerSet == null) {
                stickerSet = StickersQuery.getStickerSetById(inputStickerSet.id);
            }
            if (stickerSet == null) {
                TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
                req.stickerset = inputStickerSet;
                ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(final TLObject response, final TLRPC.TL_error error) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                reqId = 0;
                                if (error == null) {
                                    stickerSet = (TLRPC.TL_messages_stickerSet) response;
                                    showEmoji = !stickerSet.set.masks;
                                    updateSendButton();
                                    updateFields();
                                    adapter.notifyDataSetChanged();
                                } else {
                                    Toast.makeText(getContext(), LocaleController.getString("AddStickersNotFound", R.string.AddStickersNotFound), Toast.LENGTH_SHORT).show();
                                    dismiss();
                                }
                            }
                        });
                    }
                });
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
        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow);

        containerView = new FrameLayout(context) {

            private int lastNotifyWidth;

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
                    height -= AndroidUtilities.statusBarHeight;
                }
                int contentSize;
                if (stickerSetCovereds != null) {
                    contentSize = AndroidUtilities.dp(48 + 8) + AndroidUtilities.dp(60) * stickerSetCovereds.size() + adapter.stickersRowCount * AndroidUtilities.dp(82);
                } else {
                    contentSize = AndroidUtilities.dp(48 + 48) + Math.max(3, (stickerSet != null ? (int) Math.ceil(stickerSet.documents.size() / 5.0f) : 0)) * AndroidUtilities.dp(82) + backgroundPaddingTop;
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
                shadowDrawable.setBounds(0, scrollOffsetY - backgroundPaddingTop, getMeasuredWidth(), getMeasuredHeight());
                shadowDrawable.draw(canvas);
            }
        };
        containerView.setWillNotDraw(false);
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

        titleTextView = new TextView(context);
        titleTextView.setLines(1);
        titleTextView.setSingleLine(true);
        titleTextView.setTextColor(Theme.STICKERS_SHEET_TITLE_TEXT_COLOR);
        titleTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        titleTextView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        titleTextView.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        titleTextView.setGravity(Gravity.CENTER_VERTICAL);
        titleTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        containerView.addView(titleTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));
        titleTextView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        shadow[0] = new View(context);
        shadow[0].setBackgroundResource(R.drawable.header_shadow);
        shadow[0].setAlpha(0.0f);
        shadow[0].setVisibility(View.INVISIBLE);
        shadow[0].setTag(1);
        containerView.addView(shadow[0], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.TOP | Gravity.LEFT, 0, 48, 0, 0));

        gridView = new RecyclerListView(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                boolean result = StickerPreviewViewer.getInstance().onInterceptTouchEvent(event, gridView, 0);
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
        gridView.setGlowColor(0xfff5f6f7);
        gridView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return StickerPreviewViewer.getInstance().onTouch(event, gridView, 0, stickersOnItemClickListener);
            }
        });
        gridView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                updateLayout();
            }
        });
        stickersOnItemClickListener = new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
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
                        stickerEmojiTextView.setText(Emoji.replaceEmoji(StickersQuery.getEmojiForSticker(selectedSticker.id), stickerEmojiTextView.getPaint().getFontMetricsInt(), AndroidUtilities.dp(30), false));
                    }

                    stickerImageView.getImageReceiver().setImage(selectedSticker, null, selectedSticker.thumb.location, null, "webp", true);
                    FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) stickerPreviewLayout.getLayoutParams();
                    layoutParams.topMargin = scrollOffsetY;
                    stickerPreviewLayout.setLayoutParams(layoutParams);
                    stickerPreviewLayout.setVisibility(View.VISIBLE);
                    AnimatorSet animatorSet = new AnimatorSet();
                    animatorSet.playTogether(ObjectAnimator.ofFloat(stickerPreviewLayout, "alpha", 0.0f, 1.0f));
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
        emptyView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        ProgressBar progressView = new ProgressBar(context);
        emptyView.addView(progressView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        shadow[1] = new View(context);
        shadow[1].setBackgroundResource(R.drawable.header_shadow_reverse);
        containerView.addView(shadow[1], LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 48));

        pickerBottomLayout = new PickerBottomLayout(context, false);
        containerView.addView(pickerBottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));
        pickerBottomLayout.cancelButton.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        pickerBottomLayout.cancelButton.setTextColor(Theme.STICKERS_SHEET_CLOSE_TEXT_COLOR);
        pickerBottomLayout.cancelButton.setText(LocaleController.getString("Close", R.string.Close).toUpperCase());
        pickerBottomLayout.cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismiss();
            }
        });
        pickerBottomLayout.doneButton.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
        pickerBottomLayout.doneButtonBadgeTextView.setBackgroundResource(R.drawable.stickercounter);

        stickerPreviewLayout = new FrameLayout(context);
        stickerPreviewLayout.setBackgroundColor(0xdfffffff);
        stickerPreviewLayout.setVisibility(View.GONE);
        stickerPreviewLayout.setSoundEffectsEnabled(false);
        containerView.addView(stickerPreviewLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        stickerPreviewLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hidePreview();
            }
        });

        ImageView closeButton = new ImageView(context);
        closeButton.setImageResource(R.drawable.delete_reply);
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        if (Build.VERSION.SDK_INT >= 21) {
            closeButton.setBackgroundDrawable(Theme.createBarSelectorDrawable(Theme.INPUT_FIELD_SELECTOR_COLOR));
        }
        stickerPreviewLayout.addView(closeButton, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.TOP));
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hidePreview();
            }
        });

        stickerImageView = new BackupImageView(context);
        stickerImageView.setAspectFit(true);
        stickerPreviewLayout.addView(stickerImageView);

        stickerEmojiTextView = new TextView(context);
        stickerEmojiTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 30);
        stickerEmojiTextView.setGravity(Gravity.BOTTOM | Gravity.RIGHT);
        stickerPreviewLayout.addView(stickerEmojiTextView);

        previewSendButton = new TextView(context);
        previewSendButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        previewSendButton.setTextColor(Theme.STICKERS_SHEET_SEND_TEXT_COLOR);
        previewSendButton.setGravity(Gravity.CENTER);
        previewSendButton.setBackgroundColor(0xffffffff);
        previewSendButton.setPadding(AndroidUtilities.dp(29), 0, AndroidUtilities.dp(29), 0);
        previewSendButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        stickerPreviewLayout.addView(previewSendButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT));
        previewSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                delegate.onStickerSelected(selectedSticker);
                dismiss();
            }
        });

        previewSendButtonShadow = new View(context);
        previewSendButtonShadow.setBackgroundResource(R.drawable.header_shadow_reverse);
        stickerPreviewLayout.addView(previewSendButtonShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 48));
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.emojiDidLoaded);

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

    private void updateFields() {
        if (titleTextView == null) {
            return;
        }
        if (stickerSet != null) {
            titleTextView.setText(stickerSet.set.title);

            if (stickerSet.set == null || !StickersQuery.isStickerPackInstalled(stickerSet.set.id)) {
                setRightButton(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        dismiss();
                        if (installDelegate != null) {
                            installDelegate.onStickerSetInstalled();
                        }
                        TLRPC.TL_messages_installStickerSet req = new TLRPC.TL_messages_installStickerSet();
                        req.stickerset = inputStickerSet;
                        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                            @Override
                            public void run(final TLObject response, final TLRPC.TL_error error) {
                                AndroidUtilities.runOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            if (error == null) {
                                                if (stickerSet.set.masks) {
                                                    Toast.makeText(getContext(), LocaleController.getString("AddMasksInstalled", R.string.AddMasksInstalled), Toast.LENGTH_SHORT).show();
                                                } else {
                                                    Toast.makeText(getContext(), LocaleController.getString("AddStickersInstalled", R.string.AddStickersInstalled), Toast.LENGTH_SHORT).show();
                                                }
                                                if (response instanceof TLRPC.TL_messages_stickerSetInstallResultArchive) {
                                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.needReloadArchivedStickers);
                                                    if (parentFragment != null && parentFragment.getParentActivity() != null) {
                                                        StickersArchiveAlert alert = new StickersArchiveAlert(parentFragment.getParentActivity(), parentFragment, ((TLRPC.TL_messages_stickerSetInstallResultArchive) response).sets);
                                                        parentFragment.showDialog(alert.create());
                                                    }
                                                }
                                            } else {
                                                Toast.makeText(getContext(), LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred), Toast.LENGTH_SHORT).show();
                                            }
                                        } catch (Exception e) {
                                            FileLog.e("tmessages", e);
                                        }
                                        StickersQuery.loadStickers(stickerSet.set.masks ? StickersQuery.TYPE_MASK : StickersQuery.TYPE_IMAGE, false, true);
                                    }
                                });
                            }
                        });
                    }
                }, stickerSet != null && stickerSet.set.masks ? LocaleController.getString("AddMasks", R.string.AddMasks) : LocaleController.getString("AddStickers", R.string.AddStickers), Theme.STICKERS_SHEET_ADD_TEXT_COLOR, true);
            } else {
                if (stickerSet.set.official) {
                    setRightButton(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (installDelegate != null) {
                                installDelegate.onStickerSetUninstalled();
                            }
                            dismiss();
                            StickersQuery.removeStickersSet(getContext(), stickerSet.set, 1, parentFragment, true);
                        }
                    }, LocaleController.getString("StickersRemove", R.string.StickersHide), Theme.STICKERS_SHEET_REMOVE_TEXT_COLOR, false);
                } else {
                    setRightButton(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (installDelegate != null) {
                                installDelegate.onStickerSetUninstalled();
                            }
                            dismiss();
                            StickersQuery.removeStickersSet(getContext(), stickerSet.set, 0, parentFragment, true);
                        }
                    }, LocaleController.getString("StickersRemove", R.string.StickersRemove), Theme.STICKERS_SHEET_REMOVE_TEXT_COLOR, false);
                }
            }
            adapter.notifyDataSetChanged();
        } else {
            setRightButton(null, null, Theme.STICKERS_SHEET_REMOVE_TEXT_COLOR, false);
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
                shadow[0].setTranslationY(scrollOffsetY);
            }
            containerView.invalidate();
            return;
        }
        View child = gridView.getChildAt(0);
        GridAdapter.Holder holder = (GridAdapter.Holder) gridView.findContainingViewHolder(child);
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
                shadow[0].setTranslationY(scrollOffsetY);
            }
            containerView.invalidate();
        }
    }

    private void hidePreview() {
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(ObjectAnimator.ofFloat(stickerPreviewLayout, "alpha", 0.0f));
        animatorSet.setDuration(200);
        animatorSet.addListener(new AnimatorListenerAdapterProxy() {
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
            shadowAnimation[num].playTogether(ObjectAnimator.ofFloat(shadow[num], "alpha", show ? 1.0f : 0.0f));
            shadowAnimation[num].setDuration(150);
            shadowAnimation[num].addListener(new AnimatorListenerAdapterProxy() {
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
            ConnectionsManager.getInstance().cancelRequest(reqId, true);
            reqId = 0;
        }
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.emojiDidLoaded);
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.emojiDidLoaded) {

            if (gridView != null) {
                int count = gridView.getChildCount();
                for (int a = 0; a < count; a++) {
                    gridView.getChildAt(a).invalidate();
                }
            }
            if (StickerPreviewViewer.getInstance().isVisible()) {
                StickerPreviewViewer.getInstance().close();
            }
            StickerPreviewViewer.getInstance().reset();
        }
    }

    private void setRightButton(View.OnClickListener onClickListener, String title, int color, boolean showCircle) {
        if (title == null) {
            pickerBottomLayout.doneButton.setVisibility(View.GONE);
        } else {
            pickerBottomLayout.doneButton.setVisibility(View.VISIBLE);
            if (showCircle) {
                pickerBottomLayout.doneButtonBadgeTextView.setVisibility(View.VISIBLE);
                pickerBottomLayout.doneButtonBadgeTextView.setText(String.format("%d", stickerSet.documents.size()));
            } else {
                pickerBottomLayout.doneButtonBadgeTextView.setVisibility(View.GONE);
            }
            pickerBottomLayout.doneButtonTextView.setTextColor(color);
            pickerBottomLayout.doneButtonTextView.setText(title.toUpperCase());
            pickerBottomLayout.doneButton.setOnClickListener(onClickListener);
        }
    }

    private class GridAdapter extends RecyclerView.Adapter {

        private Context context;
        private int stickersPerRow;
        private HashMap<Integer, Object> cache = new HashMap<>();
        private HashMap<Integer, TLRPC.StickerSetCovered> positionsToSets = new HashMap<>();
        private int totalItems;
        private int stickersRowCount;

        public GridAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return totalItems;
        }

        private class Holder extends RecyclerView.ViewHolder {

            public Holder(View itemView) {
                super(itemView);
            }
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
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = null;
            switch (viewType) {
                case 0:
                    view = new StickerEmojiCell(context) {
                        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(82), MeasureSpec.EXACTLY));
                        }
                    };
                    break;
                case 1:
                    view = new EmptyCell(context);
                    break;
                case 2:
                    view = new FeaturedStickerSetInfoCell(context, 8);
                    break;
            }

            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (stickerSetCovereds != null) {
                switch (holder.getItemViewType()) {
                    case 0:
                        TLRPC.Document sticker = (TLRPC.Document) cache.get(position);
                        ((StickerEmojiCell) holder.itemView).setSticker(sticker, false);
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
                ((StickerEmojiCell) holder.itemView).setSticker(stickerSet.documents.get(position), showEmoji);
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
