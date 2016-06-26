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
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.StickerEmojiCell;
import org.telegram.ui.StickerPreviewViewer;

public class StickersAlert extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    public interface StickersAlertDelegate {
        void onStickerSelected(TLRPC.Document sticker);
    }

    private RecyclerListView gridView;
    private GridLayoutManager layoutManager;
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

    private TLRPC.TL_messages_stickerSet stickerSet;
    private TLRPC.Document selectedSticker;
    private TLRPC.InputStickerSet inputStickerSet;

    private StickersAlertDelegate delegate;

    private int scrollOffsetY;
    private int reqId;
    private boolean ignoreLayout;

    public StickersAlert(Context context, TLRPC.InputStickerSet set, TLRPC.TL_messages_stickerSet loadedSet, StickersAlertDelegate stickersAlertDelegate) {
        super(context, false);

        delegate = stickersAlertDelegate;
        inputStickerSet = set;
        stickerSet = loadedSet;
        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow);

        containerView = new FrameLayout(context) {
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
                int contentSize = AndroidUtilities.dp(48 + 48) + Math.max(3, (stickerSet != null ? (int) Math.ceil(stickerSet.documents.size() / 5.0f) : 0)) * AndroidUtilities.dp(82) + backgroundPaddingTop;
                int padding = contentSize < (height / 5 * 3.2) ? 0 : (height / 5 * 2);
                if (padding != 0 && contentSize < height) {
                    padding -= (height - contentSize);
                }
                if (padding == 0) {
                    padding = backgroundPaddingTop;
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
        int size = (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) / 2 / AndroidUtilities.density);
        stickerPreviewLayout.addView(stickerImageView, LayoutHelper.createFrame(size, size, Gravity.CENTER));

        stickerEmojiTextView = new TextView(context);
        stickerEmojiTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 30);
        stickerEmojiTextView.setGravity(Gravity.BOTTOM | Gravity.RIGHT);
        stickerPreviewLayout.addView(stickerEmojiTextView, LayoutHelper.createFrame(size, size, Gravity.CENTER));

        previewSendButton = new TextView(context);
        previewSendButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        previewSendButton.setTextColor(Theme.STICKERS_SHEET_SEND_TEXT_COLOR);
        previewSendButton.setGravity(Gravity.CENTER);
        previewSendButton.setBackgroundColor(0xffffffff);
        previewSendButton.setPadding(AndroidUtilities.dp(29), 0, AndroidUtilities.dp(29), 0);
        previewSendButton.setText(LocaleController.getString("Close", R.string.Close).toUpperCase());
        previewSendButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        previewSendButton.setVisibility(View.GONE);
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
        previewSendButtonShadow.setVisibility(View.GONE);
        stickerPreviewLayout.addView(previewSendButtonShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 48));

        if (delegate != null) {
            previewSendButton.setText(LocaleController.getString("SendSticker", R.string.SendSticker).toUpperCase());
            stickerImageView.setLayoutParams(LayoutHelper.createFrame(size, size, Gravity.CENTER, 0, 0, 0, 30));
            stickerEmojiTextView.setLayoutParams(LayoutHelper.createFrame(size, size, Gravity.CENTER, 0, 0, 0, 30));
            previewSendButton.setVisibility(View.VISIBLE);
            previewSendButtonShadow.setVisibility(View.VISIBLE);
        }

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
        }
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.emojiDidLoaded);
        updateFields();
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
                        TLRPC.TL_messages_installStickerSet req = new TLRPC.TL_messages_installStickerSet();
                        req.stickerset = inputStickerSet;
                        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                            @Override
                            public void run(TLObject response, final TLRPC.TL_error error) {
                                AndroidUtilities.runOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            if (error == null) {
                                                Toast.makeText(getContext(), LocaleController.getString("AddStickersInstalled", R.string.AddStickersInstalled), Toast.LENGTH_SHORT).show();
                                            } else {
                                                if (error.text.equals("STICKERSETS_TOO_MUCH")) {
                                                    Toast.makeText(getContext(), LocaleController.getString("TooMuchStickersets", R.string.TooMuchStickersets), Toast.LENGTH_SHORT).show();
                                                } else {
                                                    Toast.makeText(getContext(), LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred), Toast.LENGTH_SHORT).show();
                                                }
                                            }
                                        } catch (Exception e) {
                                            FileLog.e("tmessages", e);
                                        }
                                        StickersQuery.loadStickers(false, true);
                                    }
                                });
                            }
                        });
                    }
                }, LocaleController.getString("AddStickers", R.string.AddStickers), Theme.STICKERS_SHEET_ADD_TEXT_COLOR, true);
            } else {
                if (stickerSet.set.official) {
                    setRightButton(null, null, Theme.STICKERS_SHEET_REMOVE_TEXT_COLOR, false);
                } else {
                    setRightButton(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            dismiss();
                            StickersQuery.removeStickersSet(getContext(), stickerSet.set, 0);
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
            titleTextView.setTranslationY(scrollOffsetY);
            shadow[0].setTranslationY(scrollOffsetY);
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
            titleTextView.setTranslationY(scrollOffsetY);
            shadow[0].setTranslationY(scrollOffsetY);
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
                gridView.invalidateViews();
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

        Context context;

        public GridAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return stickerSet != null ? stickerSet.documents.size() : 0;
        }

        private class Holder extends RecyclerView.ViewHolder {

            public Holder(View itemView) {
                super(itemView);
            }
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = new StickerEmojiCell(context);
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(82)));
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((StickerEmojiCell) holder.itemView).setSticker(stickerSet.documents.get(position), true);
        }
    }
}
