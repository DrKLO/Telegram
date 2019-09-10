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
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LongSparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ShareDialogCell;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ShareAlert extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    private FrameLayout frameLayout;
    private FrameLayout frameLayout2;
    private EditTextEmoji commentTextView;
    private FrameLayout writeButtonContainer;
    private View selectedCountView;
    private TextView pickerBottomLayout;
    private AnimatorSet animatorSet;
    private RecyclerListView gridView;
    private GridLayoutManager layoutManager;
    private ShareDialogsAdapter listAdapter;
    private ShareSearchAdapter searchAdapter;
    private ArrayList<MessageObject> sendingMessageObjects;
    private String sendingText;
    private EmptyTextProgressView searchEmptyView;
    private Drawable shadowDrawable;
    private View[] shadow = new View[2];
    private AnimatorSet[] shadowAnimation = new AnimatorSet[2];
    private LongSparseArray<TLRPC.Dialog> selectedDialogs = new LongSparseArray<>();

    private RectF rect = new RectF();
    private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private int currentAccount = UserConfig.selectedAccount;

    private TLRPC.TL_exportedMessageLink exportedMessageLink;
    private boolean loadingLink;
    private boolean copyLinkOnEnd;

    private boolean isChannel;
    private String linkToCopy;

    private int scrollOffsetY;
    private int topBeforeSwitch;

    private class SearchField extends FrameLayout {

        private View searchBackground;
        private ImageView searchIconImageView;
        private ImageView clearSearchImageView;
        private CloseProgressDrawable2 progressDrawable;
        private EditTextBoldCursor searchEditText;
        private View backgroundView;

        public SearchField(Context context) {
            super(context);

            searchBackground = new View(context);
            searchBackground.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), Theme.getColor(Theme.key_dialogSearchBackground)));
            addView(searchBackground, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 14, 11, 14, 0));

            searchIconImageView = new ImageView(context);
            searchIconImageView.setScaleType(ImageView.ScaleType.CENTER);
            searchIconImageView.setImageResource(R.drawable.smiles_inputsearch);
            searchIconImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogSearchIcon), PorterDuff.Mode.MULTIPLY));
            addView(searchIconImageView, LayoutHelper.createFrame(36, 36, Gravity.LEFT | Gravity.TOP, 16, 11, 0, 0));

            clearSearchImageView = new ImageView(context);
            clearSearchImageView.setScaleType(ImageView.ScaleType.CENTER);
            clearSearchImageView.setImageDrawable(progressDrawable = new CloseProgressDrawable2());
            progressDrawable.setSide(AndroidUtilities.dp(7));
            clearSearchImageView.setScaleX(0.1f);
            clearSearchImageView.setScaleY(0.1f);
            clearSearchImageView.setAlpha(0.0f);
            clearSearchImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogSearchIcon), PorterDuff.Mode.MULTIPLY));
            addView(clearSearchImageView, LayoutHelper.createFrame(36, 36, Gravity.RIGHT | Gravity.TOP, 14, 11, 14, 0));
            clearSearchImageView.setOnClickListener(v -> {
                searchEditText.setText("");
                AndroidUtilities.showKeyboard(searchEditText);
            });

            searchEditText = new EditTextBoldCursor(context) {
                @Override
                public boolean dispatchTouchEvent(MotionEvent event) {
                    MotionEvent e = MotionEvent.obtain(event);
                    e.setLocation(e.getRawX(), e.getRawY() - containerView.getTranslationY());
                    gridView.dispatchTouchEvent(e);
                    e.recycle();
                    return super.dispatchTouchEvent(event);
                }
            };
            searchEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            searchEditText.setHintTextColor(Theme.getColor(Theme.key_dialogSearchHint));
            searchEditText.setTextColor(Theme.getColor(Theme.key_dialogSearchText));
            searchEditText.setBackgroundDrawable(null);
            searchEditText.setPadding(0, 0, 0, 0);
            searchEditText.setMaxLines(1);
            searchEditText.setLines(1);
            searchEditText.setSingleLine(true);
            searchEditText.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            searchEditText.setHint(LocaleController.getString("ShareSendTo", R.string.ShareSendTo));
            searchEditText.setCursorColor(Theme.getColor(Theme.key_featuredStickers_addedIcon));
            searchEditText.setCursorSize(AndroidUtilities.dp(20));
            searchEditText.setCursorWidth(1.5f);
            addView(searchEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.LEFT | Gravity.TOP, 16 + 38, 9, 16 + 30, 0));
            searchEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {

                }

                @Override
                public void afterTextChanged(Editable s) {
                    boolean show = searchEditText.length() > 0;
                    boolean showed = clearSearchImageView.getAlpha() != 0;
                    if (show != showed) {
                        clearSearchImageView.animate()
                                .alpha(show ? 1.0f : 0.0f)
                                .setDuration(150)
                                .scaleX(show ? 1.0f : 0.1f)
                                .scaleY(show ? 1.0f : 0.1f)
                                .start();
                    }
                    String text = searchEditText.getText().toString();
                    if (text.length() != 0) {
                        if (searchEmptyView != null) {
                            searchEmptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
                        }
                    } else {
                        if (gridView.getAdapter() != listAdapter) {
                            int top = getCurrentTop();
                            searchEmptyView.setText(LocaleController.getString("NoChats", R.string.NoChats));
                            searchEmptyView.showTextView();
                            gridView.setAdapter(listAdapter);
                            listAdapter.notifyDataSetChanged();
                            if (top > 0) {
                                layoutManager.scrollToPositionWithOffset(0, -top);
                            }
                        }
                    }
                    if (searchAdapter != null) {
                        searchAdapter.searchDialogs(text);
                    }
                }
            });
            searchEditText.setOnEditorActionListener((v, actionId, event) -> {
                if (event != null && (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_SEARCH || event.getAction() == KeyEvent.ACTION_DOWN && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    AndroidUtilities.hideKeyboard(searchEditText);
                }
                return false;
            });
        }

        public void hideKeyboard() {
            AndroidUtilities.hideKeyboard(searchEditText);
        }

        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            super.requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    public static ShareAlert createShareAlert(final Context context, MessageObject messageObject, final String text, boolean channel, final String copyLink, boolean fullScreen) {
        ArrayList<MessageObject> arrayList;
        if (messageObject != null) {
            arrayList = new ArrayList<>();
            arrayList.add(messageObject);
        } else {
            arrayList = null;
        }
        return new ShareAlert(context, arrayList, text, channel, copyLink, fullScreen);
    }

    public ShareAlert(final Context context, ArrayList<MessageObject> messages, final String text, boolean channel, final String copyLink, boolean fullScreen) {
        super(context, true, 1);

        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));

        isFullscreen = fullScreen;
        linkToCopy = copyLink;
        sendingMessageObjects = messages;
        searchAdapter = new ShareSearchAdapter(context);
        isChannel = channel;
        sendingText = text;

        if (channel) {
            loadingLink = true;
            TLRPC.TL_channels_exportMessageLink req = new TLRPC.TL_channels_exportMessageLink();
            req.id = messages.get(0).getId();
            req.channel = MessagesController.getInstance(currentAccount).getInputChannel(messages.get(0).messageOwner.to_id.channel_id);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (response != null) {
                    exportedMessageLink = (TLRPC.TL_exportedMessageLink) response;
                    if (copyLinkOnEnd) {
                        copyLink(context);
                    }
                }
                loadingLink = false;
            }));
        }


        SizeNotifierFrameLayout sizeNotifierFrameLayout = new SizeNotifierFrameLayout(context) {

            private boolean ignoreLayout = false;
            private RectF rect1 = new RectF();
            private boolean fullHeight;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int totalHeight = MeasureSpec.getSize(heightMeasureSpec);
                if (Build.VERSION.SDK_INT >= 21 && !isFullscreen) {
                    ignoreLayout = true;
                    setPadding(backgroundPaddingLeft, AndroidUtilities.statusBarHeight, backgroundPaddingLeft, 0);
                    ignoreLayout = false;
                }
                int availableHeight = totalHeight - getPaddingTop();
                int keyboardSize = getKeyboardHeight();
                if (!AndroidUtilities.isInMultiwindow && keyboardSize <= AndroidUtilities.dp(20)) {
                    availableHeight -= commentTextView.getEmojiPadding();
                }

                int size = Math.max(searchAdapter.getItemCount(), listAdapter.getItemCount());
                int contentSize = AndroidUtilities.dp(48) + Math.max(3, (int) Math.ceil(size / 4.0f)) * AndroidUtilities.dp(103) + backgroundPaddingTop;
                int padding = (contentSize < availableHeight ? 0 : availableHeight - (availableHeight / 5 * 3)) + AndroidUtilities.dp(8);
                if (gridView.getPaddingTop() != padding) {
                    ignoreLayout = true;
                    gridView.setPadding(0, padding, 0, AndroidUtilities.dp(48));
                    ignoreLayout = false;
                }
                fullHeight = contentSize >= totalHeight;
                onMeasureInternal(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Math.min(contentSize, totalHeight), MeasureSpec.EXACTLY));
            }

            private void onMeasureInternal(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);
                widthSize -= backgroundPaddingLeft * 2;

                int keyboardSize = getKeyboardHeight();
                if (keyboardSize <= AndroidUtilities.dp(20)) {
                    if (!AndroidUtilities.isInMultiwindow) {
                        heightSize -= commentTextView.getEmojiPadding();
                        heightMeasureSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY);
                    }
                    ignoreLayout = true;
                    int visibility = commentTextView.isPopupShowing() ? GONE : VISIBLE;
                    if (pickerBottomLayout != null) {
                        pickerBottomLayout.setVisibility(visibility);
                        shadow[1].setAlpha(frameLayout2.getVisibility() == VISIBLE || visibility == VISIBLE ? 1.0f : 0.0f);
                    }
                    ignoreLayout = false;
                } else {
                    ignoreLayout = true;
                    commentTextView.hideEmojiView();
                    if (pickerBottomLayout != null) {
                        pickerBottomLayout.setVisibility(GONE);
                        shadow[1].setAlpha(frameLayout2.getVisibility() == VISIBLE ? 1.0f : 0.0f);
                    }
                    ignoreLayout = false;
                }

                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE) {
                        continue;
                    }
                    if (commentTextView != null && commentTextView.isPopupView(child)) {
                        if (AndroidUtilities.isInMultiwindow || AndroidUtilities.isTablet()) {
                            if (AndroidUtilities.isTablet()) {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(AndroidUtilities.isTablet() ? 200 : 320), heightSize - AndroidUtilities.statusBarHeight + getPaddingTop()), MeasureSpec.EXACTLY));
                            } else {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize - AndroidUtilities.statusBarHeight + getPaddingTop(), MeasureSpec.EXACTLY));
                            }
                        } else {
                            child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(child.getLayoutParams().height, MeasureSpec.EXACTLY));
                        }
                    } else {
                        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                    }
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                final int count = getChildCount();

                int paddingBottom = getKeyboardHeight() <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet() ? commentTextView.getEmojiPadding() : 0;
                setBottomClip(paddingBottom);

                for (int i = 0; i < count; i++) {
                    final View child = getChildAt(i);
                    if (child.getVisibility() == GONE) {
                        continue;
                    }
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                    final int width = child.getMeasuredWidth();
                    final int height = child.getMeasuredHeight();

                    int childLeft;
                    int childTop;

                    int gravity = lp.gravity;
                    if (gravity == -1) {
                        gravity = Gravity.TOP | Gravity.LEFT;
                    }

                    final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                    final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                    switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                        case Gravity.CENTER_HORIZONTAL:
                            childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                            break;
                        case Gravity.RIGHT:
                            childLeft = (r - l) - width - lp.rightMargin - getPaddingRight() - backgroundPaddingLeft;
                            break;
                        case Gravity.LEFT:
                        default:
                            childLeft = lp.leftMargin + getPaddingLeft();
                    }

                    switch (verticalGravity) {
                        case Gravity.TOP:
                            childTop = lp.topMargin + getPaddingTop();
                            break;
                        case Gravity.CENTER_VERTICAL:
                            childTop = ((b - paddingBottom) - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                            break;
                        case Gravity.BOTTOM:
                            childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                            break;
                        default:
                            childTop = lp.topMargin;
                    }

                    if (commentTextView != null && commentTextView.isPopupView(child)) {
                        if (AndroidUtilities.isTablet()) {
                            childTop = getMeasuredHeight() - child.getMeasuredHeight();
                        } else {
                            childTop = getMeasuredHeight() + getKeyboardHeight() - child.getMeasuredHeight();
                        }
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }

                notifyHeightChanged();
                updateLayout();
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && scrollOffsetY != 0 && ev.getY() < scrollOffsetY - AndroidUtilities.dp(30)) {
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
                int height = getMeasuredHeight() + AndroidUtilities.dp(30) + backgroundPaddingTop;
                int statusBarHeight = 0;
                float radProgress = 1.0f;
                if (!isFullscreen && Build.VERSION.SDK_INT >= 21) {
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
                    rect1.set(backgroundPaddingLeft, backgroundPaddingTop + top, getMeasuredWidth() - backgroundPaddingLeft, backgroundPaddingTop + top + AndroidUtilities.dp(24));
                    canvas.drawRoundRect(rect1, AndroidUtilities.dp(12) * radProgress, AndroidUtilities.dp(12) * radProgress, Theme.dialogs_onlineCirclePaint);
                }

                int w = AndroidUtilities.dp(36);
                rect1.set((getMeasuredWidth() - w) / 2, y, (getMeasuredWidth() + w) / 2, y + AndroidUtilities.dp(4));
                Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_sheet_scrollUp));
                canvas.drawRoundRect(rect1, AndroidUtilities.dp(2), AndroidUtilities.dp(2), Theme.dialogs_onlineCirclePaint);

                if (statusBarHeight > 0) {
                    int color1 = Theme.getColor(Theme.key_dialogBackground);
                    int finalColor = Color.argb(0xff, (int) (Color.red(color1) * 0.8f), (int) (Color.green(color1) * 0.8f), (int) (Color.blue(color1) * 0.8f));
                    Theme.dialogs_onlineCirclePaint.setColor(finalColor);
                    canvas.drawRect(backgroundPaddingLeft, AndroidUtilities.statusBarHeight - statusBarHeight, getMeasuredWidth() - backgroundPaddingLeft, AndroidUtilities.statusBarHeight, Theme.dialogs_onlineCirclePaint);
                }
            }
        };
        containerView = sizeNotifierFrameLayout;
        containerView.setWillNotDraw(false);
        containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);

        frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));

        SearchField searchView = new SearchField(context);
        frameLayout.addView(searchView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));

        gridView = new RecyclerListView(context) {
            @Override
            protected boolean allowSelectChildAtPosition(float x, float y) {
                return y >= scrollOffsetY + AndroidUtilities.dp(48) + (Build.VERSION.SDK_INT >= 21 ? AndroidUtilities.statusBarHeight : 0);
            }
        };
        gridView.setTag(13);
        gridView.setPadding(0, 0, 0, AndroidUtilities.dp(48));
        gridView.setClipToPadding(false);
        gridView.setLayoutManager(layoutManager = new GridLayoutManager(getContext(), 4));
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (position == 0) {
                    return layoutManager.getSpanCount();
                }
                return 1;
            }
        });
        gridView.setHorizontalScrollBarEnabled(false);
        gridView.setVerticalScrollBarEnabled(false);
        gridView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(android.graphics.Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
                RecyclerListView.Holder holder = (RecyclerListView.Holder) parent.getChildViewHolder(view);
                if (holder != null) {
                    int pos = holder.getAdapterPosition();
                    outRect.left = pos % 4 == 0 ? 0 : AndroidUtilities.dp(4);
                    outRect.right = pos % 4 == 3 ? 0 : AndroidUtilities.dp(4);
                } else {
                    outRect.left = AndroidUtilities.dp(4);
                    outRect.right = AndroidUtilities.dp(4);
                }
            }
        });
        containerView.addView(gridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 0, 0));
        gridView.setAdapter(listAdapter = new ShareDialogsAdapter(context));
        gridView.setGlowColor(Theme.getColor(Theme.key_dialogScrollGlow));
        gridView.setOnItemClickListener((view, position) -> {
            if (position < 0) {
                return;
            }
            TLRPC.Dialog dialog;
            if (gridView.getAdapter() == listAdapter) {
                dialog = listAdapter.getItem(position);
            } else {
                dialog = searchAdapter.getItem(position);
            }
            if (dialog == null) {
                return;
            }
            ShareDialogCell cell = (ShareDialogCell) view;
            if (selectedDialogs.indexOfKey(dialog.id) >= 0) {
                selectedDialogs.remove(dialog.id);
                cell.setChecked(false, true);
                updateSelectedCount(1);
            } else {
                selectedDialogs.put(dialog.id, dialog);
                cell.setChecked(true, true);
                updateSelectedCount(2);
                int selfUserId = UserConfig.getInstance(currentAccount).clientUserId;
                if (gridView.getAdapter() == searchAdapter) {
                    TLRPC.Dialog existingDialog = listAdapter.dialogsMap.get(dialog.id);
                    if (existingDialog == null) {
                        listAdapter.dialogsMap.put(dialog.id, dialog);
                        listAdapter.dialogs.add(listAdapter.dialogs.isEmpty() ? 0 : 1, dialog);
                    } else if (existingDialog.id != selfUserId) {
                        listAdapter.dialogs.remove(existingDialog);
                        listAdapter.dialogs.add(listAdapter.dialogs.isEmpty() ? 0 : 1, existingDialog);
                    }
                    searchView.searchEditText.setText("");
                    gridView.setAdapter(listAdapter);
                    searchView.hideKeyboard();
                }
            }
        });
        gridView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                updateLayout();
            }
        });

        searchEmptyView = new EmptyTextProgressView(context);
        searchEmptyView.setShowAtCenter(true);
        searchEmptyView.showTextView();
        searchEmptyView.setText(LocaleController.getString("NoChats", R.string.NoChats));
        gridView.setEmptyView(searchEmptyView);
        containerView.addView(searchEmptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 52, 0, 0));

        FrameLayout.LayoutParams frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.TOP | Gravity.LEFT);
        frameLayoutParams.topMargin = AndroidUtilities.dp(58);
        shadow[0] = new View(context);
        shadow[0].setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
        shadow[0].setAlpha(0.0f);
        shadow[0].setTag(1);
        containerView.addView(shadow[0], frameLayoutParams);

        containerView.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 58, Gravity.LEFT | Gravity.TOP));

        frameLayoutParams = new FrameLayout.LayoutParams(LayoutHelper.MATCH_PARENT, AndroidUtilities.getShadowHeight(), Gravity.BOTTOM | Gravity.LEFT);
        frameLayoutParams.bottomMargin = AndroidUtilities.dp(48);
        shadow[1] = new View(context);
        shadow[1].setBackgroundColor(Theme.getColor(Theme.key_dialogShadowLine));
        containerView.addView(shadow[1], frameLayoutParams);

        if (isChannel || linkToCopy != null) {
            pickerBottomLayout = new TextView(context);
            pickerBottomLayout.setBackgroundDrawable(Theme.createSelectorWithBackgroundDrawable(Theme.getColor(Theme.key_dialogBackground), Theme.getColor(Theme.key_listSelector)));
            pickerBottomLayout.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
            pickerBottomLayout.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            pickerBottomLayout.setPadding(AndroidUtilities.dp(18), 0, AndroidUtilities.dp(18), 0);
            pickerBottomLayout.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            pickerBottomLayout.setGravity(Gravity.CENTER);
            pickerBottomLayout.setText(LocaleController.getString("CopyLink", R.string.CopyLink).toUpperCase());
            pickerBottomLayout.setOnClickListener(v -> {
                if (selectedDialogs.size() == 0 && (isChannel || linkToCopy != null)) {
                    if (linkToCopy == null && loadingLink) {
                        copyLinkOnEnd = true;
                        Toast.makeText(ShareAlert.this.getContext(), LocaleController.getString("Loading", R.string.Loading), Toast.LENGTH_SHORT).show();
                    } else {
                        copyLink(ShareAlert.this.getContext());
                    }
                    dismiss();
                }
            });
            containerView.addView(pickerBottomLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));
        } else {
            shadow[1].setAlpha(0.0f);
        }

        frameLayout2 = new FrameLayout(context);
        frameLayout2.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        frameLayout2.setAlpha(0.0f);
        frameLayout2.setVisibility(View.INVISIBLE);
        containerView.addView(frameLayout2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));
        frameLayout2.setOnTouchListener((v, event) -> true);

        commentTextView = new EditTextEmoji(context, sizeNotifierFrameLayout, null, EditTextEmoji.STYLE_DIALOG);
        commentTextView.setHint(LocaleController.getString("ShareComment", R.string.ShareComment));
        commentTextView.onResume();
        EditTextBoldCursor editText = commentTextView.getEditText();
        editText.setMaxLines(1);
        editText.setSingleLine(true);
        frameLayout2.addView(commentTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 0, 84, 0));

        writeButtonContainer = new FrameLayout(context);
        writeButtonContainer.setVisibility(View.INVISIBLE);
        writeButtonContainer.setScaleX(0.2f);
        writeButtonContainer.setScaleY(0.2f);
        writeButtonContainer.setAlpha(0.0f);
        writeButtonContainer.setContentDescription(LocaleController.getString("Send", R.string.Send));
        containerView.addView(writeButtonContainer, LayoutHelper.createFrame(60, 60, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 6, 10));
        writeButtonContainer.setOnClickListener(v -> {
            for (int a = 0; a < selectedDialogs.size(); a++) {
                long key = selectedDialogs.keyAt(a);
                if (AlertsCreator.checkSlowMode(getContext(), currentAccount, key, frameLayout2.getTag() != null && commentTextView.length() > 0)) {
                    return;
                }
            }

            if (sendingMessageObjects != null) {
                for (int a = 0; a < selectedDialogs.size(); a++) {
                    long key = selectedDialogs.keyAt(a);
                    if (frameLayout2.getTag() != null && commentTextView.length() > 0) {
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(commentTextView.getText().toString(), key, null, null, true, null, null, null, true, 0);
                    }
                    SendMessagesHelper.getInstance(currentAccount).sendMessage(sendingMessageObjects, key, true, 0);
                }
            } else if (sendingText != null) {
                for (int a = 0; a < selectedDialogs.size(); a++) {
                    long key = selectedDialogs.keyAt(a);
                    if (frameLayout2.getTag() != null && commentTextView.length() > 0) {
                        SendMessagesHelper.getInstance(currentAccount).sendMessage(commentTextView.getText().toString(), key, null, null, true, null, null, null, true, 0);
                    }
                    SendMessagesHelper.getInstance(currentAccount).sendMessage(sendingText, key, null, null, true, null, null, null, true, 0);
                }
            }
            dismiss();
        });

        ImageView writeButton = new ImageView(context);
        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_dialogFloatingButton), Theme.getColor(Theme.key_dialogFloatingButtonPressed));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow_profile).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
            combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
            drawable = combinedDrawable;
        }
        writeButton.setBackgroundDrawable(drawable);
        writeButton.setImageResource(R.drawable.attach_send);
        writeButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogFloatingIcon), PorterDuff.Mode.MULTIPLY));
        writeButton.setScaleType(ImageView.ScaleType.CENTER);
        if (Build.VERSION.SDK_INT >= 21) {
            writeButton.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                }
            });
        }
        writeButtonContainer.addView(writeButton, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, Gravity.LEFT | Gravity.TOP, Build.VERSION.SDK_INT >= 21 ? 2 : 0, 0, 0, 0));

        textPaint.setTextSize(AndroidUtilities.dp(12));
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        selectedCountView = new View(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                String text = String.format("%d", Math.max(1, selectedDialogs.size()));
                int textSize = (int) Math.ceil(textPaint.measureText(text));
                int size = Math.max(AndroidUtilities.dp(16) + textSize, AndroidUtilities.dp(24));
                int cx = getMeasuredWidth() / 2;
                int cy = getMeasuredHeight() / 2;

                textPaint.setColor(Theme.getColor(Theme.key_dialogRoundCheckBoxCheck));
                paint.setColor(Theme.getColor(Theme.key_dialogBackground));
                rect.set(cx - size / 2, 0, cx + size / 2, getMeasuredHeight());
                canvas.drawRoundRect(rect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), paint);

                paint.setColor(Theme.getColor(Theme.key_dialogRoundCheckBox));
                rect.set(cx - size / 2 + AndroidUtilities.dp(2), AndroidUtilities.dp(2), cx + size / 2 - AndroidUtilities.dp(2), getMeasuredHeight() - AndroidUtilities.dp(2));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(10), AndroidUtilities.dp(10), paint);

                canvas.drawText(text, cx - textSize / 2, AndroidUtilities.dp(16.2f), textPaint);
            }
        };
        selectedCountView.setAlpha(0.0f);
        selectedCountView.setScaleX(0.2f);
        selectedCountView.setScaleY(0.2f);
        containerView.addView(selectedCountView, LayoutHelper.createFrame(42, 24, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, -8, 9));

        updateSelectedCount(0);

        if (!DialogsActivity.dialogsLoaded[currentAccount]) {
            MessagesController.getInstance(currentAccount).loadDialogs(0, 0, 100, true);
            ContactsController.getInstance(currentAccount).checkInviteText();
            DialogsActivity.dialogsLoaded[currentAccount] = true;
        }
        if (listAdapter.dialogs.isEmpty()) {
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.dialogsNeedReload);
        }
    }

    private int getCurrentTop() {
        if (gridView.getChildCount() != 0) {
            View child = gridView.getChildAt(0);
            RecyclerListView.Holder holder = (RecyclerListView.Holder) gridView.findContainingViewHolder(child);
            if (holder != null) {
                return gridView.getPaddingTop() - (holder.getAdapterPosition() == 0 && child.getTop() >= 0 ? child.getTop() : 0);
            }
        }
        return -1000;
    }

    @Override
    public void dismissInternal() {
        super.dismissInternal();
        if (commentTextView != null) {
            commentTextView.onDestroy();
        }
    }

    @Override
    public void onBackPressed() {
        if (commentTextView != null && commentTextView.isPopupShowing()) {
            commentTextView.hidePopup(true);
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.dialogsNeedReload) {
            if (listAdapter != null) {
                listAdapter.fetchDialogs();
            }
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.dialogsNeedReload);
        }
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    @SuppressLint("NewApi")
    private void updateLayout() {
        if (gridView.getChildCount() <= 0) {
            return;
        }
        View child = gridView.getChildAt(0);
        RecyclerListView.Holder holder = (RecyclerListView.Holder) gridView.findContainingViewHolder(child);
        int top = child.getTop() - AndroidUtilities.dp(8);
        int newOffset = top > 0 && holder != null && holder.getAdapterPosition() == 0 ? top : 0;
        if (top >= 0 && holder != null && holder.getAdapterPosition() == 0) {
            newOffset = top;
            runShadowAnimation(0, false);
        } else {
            runShadowAnimation(0, true);
        }
        if (scrollOffsetY != newOffset) {
            gridView.setTopGlowOffset(scrollOffsetY = newOffset);
            frameLayout.setTranslationY(scrollOffsetY);
            searchEmptyView.setTranslationY(scrollOffsetY);
            containerView.invalidate();
        }
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

    private void copyLink(Context context) {
        if (exportedMessageLink == null && linkToCopy == null) {
            return;
        }
        try {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("label", linkToCopy != null ? linkToCopy : exportedMessageLink.link);
            clipboard.setPrimaryClip(clip);
            if (exportedMessageLink != null && exportedMessageLink.link.contains("/c/")) {
                Toast.makeText(ApplicationLoader.applicationContext, LocaleController.getString("LinkCopiedPrivate", R.string.LinkCopiedPrivate), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(ApplicationLoader.applicationContext, LocaleController.getString("LinkCopied", R.string.LinkCopied), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private boolean showCommentTextView(final boolean show) {
        if (show == (frameLayout2.getTag() != null)) {
            return false;
        }
        if (animatorSet != null) {
            animatorSet.cancel();
        }
        frameLayout2.setTag(show ? 1 : null);
        if (commentTextView.getEditText().isFocused()) {
            AndroidUtilities.hideKeyboard(commentTextView.getEditText());
        }
        commentTextView.hidePopup(true);
        if (show) {
            frameLayout2.setVisibility(View.VISIBLE);
            writeButtonContainer.setVisibility(View.VISIBLE);
        }
        animatorSet = new AnimatorSet();
        ArrayList<Animator> animators = new ArrayList<>();
        animators.add(ObjectAnimator.ofFloat(frameLayout2, View.ALPHA, show ? 1.0f : 0.0f));
        animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.SCALE_X, show ? 1.0f : 0.2f));
        animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.SCALE_Y, show ? 1.0f : 0.2f));
        animators.add(ObjectAnimator.ofFloat(writeButtonContainer, View.ALPHA, show ? 1.0f : 0.0f));
        animators.add(ObjectAnimator.ofFloat(selectedCountView, View.SCALE_X, show ? 1.0f : 0.2f));
        animators.add(ObjectAnimator.ofFloat(selectedCountView, View.SCALE_Y, show ? 1.0f : 0.2f));
        animators.add(ObjectAnimator.ofFloat(selectedCountView, View.ALPHA, show ? 1.0f : 0.0f));
        if (pickerBottomLayout == null || pickerBottomLayout.getVisibility() != View.VISIBLE) {
            animators.add(ObjectAnimator.ofFloat(shadow[1], View.ALPHA, show ? 1.0f : 0.0f));
        }

        animatorSet.playTogether(animators);
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.setDuration(180);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation.equals(animatorSet)) {
                    if (!show) {
                        frameLayout2.setVisibility(View.INVISIBLE);
                        writeButtonContainer.setVisibility(View.INVISIBLE);
                    }
                    animatorSet = null;
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (animation.equals(animatorSet)) {
                    animatorSet = null;
                }
            }
        });
        animatorSet.start();
        return true;
    }

    public void updateSelectedCount(int animated) {
        if (selectedDialogs.size() == 0) {
            selectedCountView.setPivotX(0);
            selectedCountView.setPivotY(0);
            showCommentTextView(false);
        } else {
            selectedCountView.invalidate();
            if (!showCommentTextView(true) && animated != 0) {
                selectedCountView.setPivotX(AndroidUtilities.dp(21));
                selectedCountView.setPivotY(AndroidUtilities.dp(12));
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(selectedCountView, View.SCALE_X, animated == 1 ? 1.1f : 0.9f, 1.0f),
                        ObjectAnimator.ofFloat(selectedCountView, View.SCALE_Y, animated == 1 ? 1.1f : 0.9f, 1.0f));
                animatorSet.setInterpolator(new OvershootInterpolator());
                animatorSet.setDuration(180);
                animatorSet.start();
            } else {
                selectedCountView.setPivotX(0);
                selectedCountView.setPivotY(0);
            }
        }
    }

    @Override
    public void dismiss() {
        if (commentTextView != null) {
            AndroidUtilities.hideKeyboard(commentTextView.getEditText());
        }
        super.dismiss();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.dialogsNeedReload);
    }

    private class ShareDialogsAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;
        private int currentCount;
        private ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>();
        private LongSparseArray<TLRPC.Dialog> dialogsMap = new LongSparseArray<>();

        public ShareDialogsAdapter(Context context) {
            this.context = context;
            fetchDialogs();
        }

        public void fetchDialogs() {
            dialogs.clear();
            dialogsMap.clear();
            int selfUserId = UserConfig.getInstance(currentAccount).clientUserId;
            if (!MessagesController.getInstance(currentAccount).dialogsForward.isEmpty()) {
                TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogsForward.get(0);
                dialogs.add(dialog);
                dialogsMap.put(dialog.id, dialog);
            }
            ArrayList<TLRPC.Dialog> allDialogs = MessagesController.getInstance(currentAccount).getAllDialogs();
            for (int a = 0; a < allDialogs.size(); a++) {
                TLRPC.Dialog dialog = allDialogs.get(a);
                if (!(dialog instanceof TLRPC.TL_dialog)) {
                    continue;
                }
                int lower_id = (int) dialog.id;
                if (lower_id == selfUserId) {
                    continue;
                }
                int high_id = (int) (dialog.id >> 32);
                if (lower_id != 0 && high_id != 1) {
                    if (lower_id > 0) {
                        dialogs.add(dialog);
                        dialogsMap.put(dialog.id, dialog);
                    } else {
                        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-lower_id);
                        if (!(chat == null || ChatObject.isNotInChat(chat) || ChatObject.isChannel(chat) && !chat.creator && (chat.admin_rights == null || !chat.admin_rights.post_messages) && !chat.megagroup)) {
                            dialogs.add(dialog);
                            dialogsMap.put(dialog.id, dialog);
                        }
                    }
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            int count = dialogs.size();
            if (count != 0) {
                count++;
            }
            return count;
        }

        public TLRPC.Dialog getItem(int position) {
            position--;
            if (position < 0 || position >= dialogs.size()) {
                return null;
            }
            return dialogs.get(position);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == 1) {
                return false;
            }
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0: {
                    view = new ShareDialogCell(context);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(100)));
                    break;
                }
                case 1:
                default: {
                    view = new View(context);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(56)));
                    break;
                }
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 0) {
                ShareDialogCell cell = (ShareDialogCell) holder.itemView;
                TLRPC.Dialog dialog = getItem(position);
                cell.setDialog((int) dialog.id, selectedDialogs.indexOfKey(dialog.id) >= 0, null);
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return 1;
            }
            return 0;
        }
    }

    public class ShareSearchAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;
        private ArrayList<DialogSearchResult> searchResult = new ArrayList<>();
        private Runnable searchRunnable;
        private String lastSearchText;
        private int reqId;
        private int lastReqId;
        private int lastSearchId;

        private class DialogSearchResult {
            public TLRPC.Dialog dialog = new TLRPC.TL_dialog();
            public TLObject object;
            public int date;
            public CharSequence name;
        }

        public ShareSearchAdapter(Context context) {
            this.context = context;
        }

        private void searchDialogsInternal(final String query, final int searchId) {
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                try {
                    String search1 = query.trim().toLowerCase();
                    if (search1.length() == 0) {
                        lastSearchId = -1;
                        updateSearchResults(new ArrayList<>(), lastSearchId);
                        return;
                    }
                    String search2 = LocaleController.getInstance().getTranslitString(search1);
                    if (search1.equals(search2) || search2.length() == 0) {
                        search2 = null;
                    }
                    String[] search = new String[1 + (search2 != null ? 1 : 0)];
                    search[0] = search1;
                    if (search2 != null) {
                        search[1] = search2;
                    }

                    ArrayList<Integer> usersToLoad = new ArrayList<>();
                    ArrayList<Integer> chatsToLoad = new ArrayList<>();
                    int resultCount = 0;

                    LongSparseArray<DialogSearchResult> dialogsResult = new LongSparseArray<>();
                    SQLiteCursor cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized("SELECT did, date FROM dialogs ORDER BY date DESC LIMIT 400");
                    while (cursor.next()) {
                        long id = cursor.longValue(0);
                        DialogSearchResult dialogSearchResult = new DialogSearchResult();
                        dialogSearchResult.date = cursor.intValue(1);
                        dialogsResult.put(id, dialogSearchResult);

                        int lower_id = (int) id;
                        int high_id = (int) (id >> 32);
                        if (lower_id != 0 && high_id != 1) {
                            if (lower_id > 0) {
                                if (!usersToLoad.contains(lower_id)) {
                                    usersToLoad.add(lower_id);
                                }
                            } else {
                                if (!chatsToLoad.contains(-lower_id)) {
                                    chatsToLoad.add(-lower_id);
                                }
                            }
                        }
                    }
                    cursor.dispose();

                    if (!usersToLoad.isEmpty()) {
                        cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, status, name FROM users WHERE uid IN(%s)", TextUtils.join(",", usersToLoad)));
                        while (cursor.next()) {
                            String name = cursor.stringValue(2);
                            String tName = LocaleController.getInstance().getTranslitString(name);
                            if (name.equals(tName)) {
                                tName = null;
                            }
                            String username = null;
                            int usernamePos = name.lastIndexOf(";;;");
                            if (usernamePos != -1) {
                                username = name.substring(usernamePos + 3);
                            }
                            int found = 0;
                            for (String q : search) {
                                if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                    found = 1;
                                } else if (username != null && username.startsWith(q)) {
                                    found = 2;
                                }
                                if (found != 0) {
                                    NativeByteBuffer data = cursor.byteBufferValue(0);
                                    if (data != null) {
                                        TLRPC.User user = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
                                        data.reuse();
                                        DialogSearchResult dialogSearchResult = dialogsResult.get((long) user.id);
                                        if (user.status != null) {
                                            user.status.expires = cursor.intValue(1);
                                        }
                                        if (found == 1) {
                                            dialogSearchResult.name = AndroidUtilities.generateSearchName(user.first_name, user.last_name, q);
                                        } else {
                                            dialogSearchResult.name = AndroidUtilities.generateSearchName("@" + user.username, null, "@" + q);
                                        }
                                        dialogSearchResult.object = user;
                                        dialogSearchResult.dialog.id = user.id;
                                        resultCount++;
                                    }
                                    break;
                                }
                            }
                        }
                        cursor.dispose();
                    }

                    if (!chatsToLoad.isEmpty()) {
                        cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, name FROM chats WHERE uid IN(%s)", TextUtils.join(",", chatsToLoad)));
                        while (cursor.next()) {
                            String name = cursor.stringValue(1);
                            String tName = LocaleController.getInstance().getTranslitString(name);
                            if (name.equals(tName)) {
                                tName = null;
                            }
                            for (int a = 0; a < search.length; a++) {
                                String q = search[a];
                                if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                    NativeByteBuffer data = cursor.byteBufferValue(0);
                                    if (data != null) {
                                        TLRPC.Chat chat = TLRPC.Chat.TLdeserialize(data, data.readInt32(false), false);
                                        data.reuse();
                                        if (!(chat == null || ChatObject.isNotInChat(chat) || ChatObject.isChannel(chat) && !chat.creator && (chat.admin_rights == null || !chat.admin_rights.post_messages) && !chat.megagroup)) {
                                            DialogSearchResult dialogSearchResult = dialogsResult.get(-(long) chat.id);
                                            dialogSearchResult.name = AndroidUtilities.generateSearchName(chat.title, null, q);
                                            dialogSearchResult.object = chat;
                                            dialogSearchResult.dialog.id = -chat.id;
                                            resultCount++;
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                        cursor.dispose();
                    }

                    ArrayList<DialogSearchResult> searchResults = new ArrayList<>(resultCount);
                    for (int a = 0; a < dialogsResult.size(); a++) {
                        DialogSearchResult dialogSearchResult = dialogsResult.valueAt(a);
                        if (dialogSearchResult.object != null && dialogSearchResult.name != null) {
                            searchResults.add(dialogSearchResult);
                        }
                    }

                    cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized("SELECT u.data, u.status, u.name, u.uid FROM users as u INNER JOIN contacts as c ON u.uid = c.uid");
                    while (cursor.next()) {
                        int uid = cursor.intValue(3);
                        if (dialogsResult.indexOfKey((long) uid) >= 0) {
                            continue;
                        }
                        String name = cursor.stringValue(2);
                        String tName = LocaleController.getInstance().getTranslitString(name);
                        if (name.equals(tName)) {
                            tName = null;
                        }
                        String username = null;
                        int usernamePos = name.lastIndexOf(";;;");
                        if (usernamePos != -1) {
                            username = name.substring(usernamePos + 3);
                        }
                        int found = 0;
                        for (String q : search) {
                            if (name.startsWith(q) || name.contains(" " + q) || tName != null && (tName.startsWith(q) || tName.contains(" " + q))) {
                                found = 1;
                            } else if (username != null && username.startsWith(q)) {
                                found = 2;
                            }
                            if (found != 0) {
                                NativeByteBuffer data = cursor.byteBufferValue(0);
                                if (data != null) {
                                    TLRPC.User user = TLRPC.User.TLdeserialize(data, data.readInt32(false), false);
                                    data.reuse();
                                    DialogSearchResult dialogSearchResult = new DialogSearchResult();
                                    if (user.status != null) {
                                        user.status.expires = cursor.intValue(1);
                                    }
                                    dialogSearchResult.dialog.id = user.id;
                                    dialogSearchResult.object = user;
                                    if (found == 1) {
                                        dialogSearchResult.name = AndroidUtilities.generateSearchName(user.first_name, user.last_name, q);
                                    } else {
                                        dialogSearchResult.name = AndroidUtilities.generateSearchName("@" + user.username, null, "@" + q);
                                    }
                                    searchResults.add(dialogSearchResult);
                                }
                                break;
                            }
                        }
                    }
                    cursor.dispose();

                    Collections.sort(searchResults, (lhs, rhs) -> {
                        if (lhs.date < rhs.date) {
                            return 1;
                        } else if (lhs.date > rhs.date) {
                            return -1;
                        }
                        return 0;
                    });

                    updateSearchResults(searchResults, searchId);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        }

        private void updateSearchResults(final ArrayList<DialogSearchResult> result, final int searchId) {
            AndroidUtilities.runOnUIThread(() -> {
                if (searchId != lastSearchId) {
                    return;
                }
                if (gridView.getAdapter() != searchAdapter) {
                    topBeforeSwitch = getCurrentTop();
                    gridView.setAdapter(searchAdapter);
                    searchAdapter.notifyDataSetChanged();
                }
                for (int a = 0; a < result.size(); a++) {
                    DialogSearchResult obj = result.get(a);
                    if (obj.object instanceof TLRPC.User) {
                        TLRPC.User user = (TLRPC.User) obj.object;
                        MessagesController.getInstance(currentAccount).putUser(user, true);
                    } else if (obj.object instanceof TLRPC.Chat) {
                        TLRPC.Chat chat = (TLRPC.Chat) obj.object;
                        MessagesController.getInstance(currentAccount).putChat(chat, true);
                    }
                }
                boolean becomeEmpty = !searchResult.isEmpty() && result.isEmpty();
                boolean isEmpty = searchResult.isEmpty() && result.isEmpty();
                if (becomeEmpty) {
                    topBeforeSwitch = getCurrentTop();
                }
                searchResult = result;
                notifyDataSetChanged();
                if (!isEmpty && !becomeEmpty && topBeforeSwitch > 0) {
                    layoutManager.scrollToPositionWithOffset(0, -topBeforeSwitch);
                    topBeforeSwitch = -1000;
                }
                searchEmptyView.showTextView();
            });
        }

        public void searchDialogs(final String query) {
            if (query != null && query.equals(lastSearchText)) {
                return;
            }
            lastSearchText = query;
            if (searchRunnable != null) {
                Utilities.searchQueue.cancelRunnable(searchRunnable);
                searchRunnable = null;
            }
            if (query == null || query.length() == 0) {
                searchResult.clear();
                topBeforeSwitch = getCurrentTop();
                lastSearchId = -1;
                notifyDataSetChanged();
            } else {
                final int searchId = ++lastSearchId;
                searchRunnable = () -> searchDialogsInternal(query, searchId);
                Utilities.searchQueue.postRunnable(searchRunnable, 300);
            }
        }

        @Override
        public int getItemCount() {
            int count = searchResult.size();
            if (count != 0) {
                count++;
            }
            return count;
        }

        public TLRPC.Dialog getItem(int position) {
            position--;
            if (position < 0 || position >= searchResult.size()) {
                return null;
            }
            return searchResult.get(position).dialog;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == 1) {
                return false;
            }
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0: {
                    view = new ShareDialogCell(context);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(100)));
                    break;
                }
                case 1:
                default: {
                    view = new View(context);
                    view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(56)));
                    break;
                }
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 0) {
                ShareDialogCell cell = (ShareDialogCell) holder.itemView;
                DialogSearchResult result = searchResult.get(position - 1);
                cell.setDialog((int) result.dialog.id, selectedDialogs.indexOfKey(result.dialog.id) >= 0, result.name);
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return 1;
            }
            return 0;
        }
    }
}
