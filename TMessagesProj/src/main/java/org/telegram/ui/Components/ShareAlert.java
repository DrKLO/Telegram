/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.LongSparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import org.telegram.messenger.support.widget.GridLayoutManager;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ShareDialogCell;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class ShareAlert extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    private FrameLayout frameLayout;
    private FrameLayout frameLayout2;
    private TextView doneButtonBadgeTextView;
    private TextView doneButtonTextView;
    private LinearLayout doneButton;
    private EditTextBoldCursor nameTextView;
    private EditTextBoldCursor commentTextView;
    private View shadow;
    private View shadow2;
    private AnimatorSet animatorSet;
    private RecyclerListView gridView;
    private GridLayoutManager layoutManager;
    private ShareDialogsAdapter listAdapter;
    private ShareSearchAdapter searchAdapter;
    private ArrayList<MessageObject> sendingMessageObjects;
    private String sendingText;
    private EmptyTextProgressView searchEmptyView;
    private Drawable shadowDrawable;
    private LongSparseArray<TLRPC.TL_dialog> selectedDialogs = new LongSparseArray<>();

    private int currentAccount = UserConfig.selectedAccount;

    private TLRPC.TL_exportedMessageLink exportedMessageLink;
    private boolean loadingLink;
    private boolean copyLinkOnEnd;

    private boolean isPublicChannel;
    private String linkToCopy;

    private int scrollOffsetY;
    private int topBeforeSwitch;


    public static ShareAlert createShareAlert(final Context context, MessageObject messageObject, final String text, boolean publicChannel, final String copyLink, boolean fullScreen) {
        ArrayList<MessageObject> arrayList;
        if (messageObject != null) {
            arrayList = new ArrayList<>();
            arrayList.add(messageObject);
        } else {
            arrayList = null;
        }
        return new ShareAlert(context, arrayList, text, publicChannel, copyLink, fullScreen);
    }

    public ShareAlert(final Context context, ArrayList<MessageObject> messages, final String text, boolean publicChannel, final String copyLink, boolean fullScreen) {
        super(context, true);

        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow).mutate();
        shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));

        linkToCopy = copyLink;
        sendingMessageObjects = messages;
        searchAdapter = new ShareSearchAdapter(context);
        isPublicChannel = publicChannel;
        sendingText = text;

        if (publicChannel) {
            loadingLink = true;
            TLRPC.TL_channels_exportMessageLink req = new TLRPC.TL_channels_exportMessageLink();
            req.id = messages.get(0).getId();
            req.channel = MessagesController.getInstance(currentAccount).getInputChannel(messages.get(0).messageOwner.to_id.channel_id);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (response != null) {
                                exportedMessageLink = (TLRPC.TL_exportedMessageLink) response;
                                if (copyLinkOnEnd) {
                                    copyLink(context);
                                }
                            }
                            loadingLink = false;
                        }
                    });
                }
            });
        }

        containerView = new FrameLayout(context) {

            private boolean ignoreLayout = false;

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
                int size = Math.max(searchAdapter.getItemCount(), listAdapter.getItemCount());
                int contentSize = AndroidUtilities.dp(48) + Math.max(3, (int) Math.ceil(size / 4.0f)) * AndroidUtilities.dp(100) + backgroundPaddingTop;
                int padding = contentSize < height ? 0 : height - (height / 5 * 3) + AndroidUtilities.dp(8);
                if (gridView.getPaddingTop() != padding) {
                    ignoreLayout = true;
                    gridView.setPadding(0, padding, 0, AndroidUtilities.dp(frameLayout2.getTag() != null ? 56 : 8));
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

        frameLayout = new FrameLayout(context);
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        frameLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        doneButton = new LinearLayout(context);
        doneButton.setOrientation(LinearLayout.HORIZONTAL);
        doneButton.setBackgroundDrawable(Theme.createSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), 0));
        doneButton.setPadding(AndroidUtilities.dp(21), 0, AndroidUtilities.dp(21), 0);
        frameLayout.addView(doneButton, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.RIGHT));
        doneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedDialogs.size() == 0 && (isPublicChannel || linkToCopy != null)) {
                    if (linkToCopy == null && loadingLink) {
                        copyLinkOnEnd = true;
                        Toast.makeText(ShareAlert.this.getContext(), LocaleController.getString("Loading", R.string.Loading), Toast.LENGTH_SHORT).show();
                    } else {
                        copyLink(ShareAlert.this.getContext());
                    }
                    dismiss();
                } else {
                    if (sendingMessageObjects != null) {
                        for (int a = 0; a < selectedDialogs.size(); a++) {
                            long key = selectedDialogs.keyAt(a);
                            if (frameLayout2.getTag() != null && commentTextView.length() > 0) {
                                SendMessagesHelper.getInstance(currentAccount).sendMessage(commentTextView.getText().toString(), key, null, null, true, null, null, null);
                            }
                            SendMessagesHelper.getInstance(currentAccount).sendMessage(sendingMessageObjects, key);
                        }
                    } else if (sendingText != null) {
                        for (int a = 0; a < selectedDialogs.size(); a++) {
                            long key = selectedDialogs.keyAt(a);
                            if (frameLayout2.getTag() != null && commentTextView.length() > 0) {
                                SendMessagesHelper.getInstance(currentAccount).sendMessage(commentTextView.getText().toString(), key, null, null, true, null, null, null);
                            }
                            SendMessagesHelper.getInstance(currentAccount).sendMessage(sendingText, key, null, null, true, null, null, null);
                        }
                    }
                    dismiss();
                }
            }
        });

        doneButtonBadgeTextView = new TextView(context);
        doneButtonBadgeTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        doneButtonBadgeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        doneButtonBadgeTextView.setTextColor(Theme.getColor(Theme.key_dialogBadgeText));
        doneButtonBadgeTextView.setGravity(Gravity.CENTER);
        doneButtonBadgeTextView.setBackgroundDrawable(Theme.createRoundRectDrawable(AndroidUtilities.dp(12.5f), Theme.getColor(Theme.key_dialogBadgeBackground)));
        doneButtonBadgeTextView.setMinWidth(AndroidUtilities.dp(23));
        doneButtonBadgeTextView.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), AndroidUtilities.dp(1));
        doneButton.addView(doneButtonBadgeTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 23, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

        doneButtonTextView = new TextView(context);
        doneButtonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        doneButtonTextView.setGravity(Gravity.CENTER);
        doneButtonTextView.setCompoundDrawablePadding(AndroidUtilities.dp(8));
        doneButtonTextView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        doneButton.addView(doneButtonTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        ImageView imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.ic_ab_search);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogIcon), PorterDuff.Mode.MULTIPLY));
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setPadding(0, AndroidUtilities.dp(2), 0, 0);
        frameLayout.addView(imageView, LayoutHelper.createFrame(48, 48, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        nameTextView = new EditTextBoldCursor(context);
        nameTextView.setHint(LocaleController.getString("ShareSendTo", R.string.ShareSendTo));
        nameTextView.setMaxLines(1);
        nameTextView.setSingleLine(true);
        nameTextView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameTextView.setBackgroundDrawable(null);
        nameTextView.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        nameTextView.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        nameTextView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        nameTextView.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        nameTextView.setCursorSize(AndroidUtilities.dp(20));
        nameTextView.setCursorWidth(1.5f);
        nameTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        frameLayout.addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 48, 2, 96, 0));
        nameTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                String text = nameTextView.getText().toString();
                if (text.length() != 0) {
                    if (gridView.getAdapter() != searchAdapter) {
                        topBeforeSwitch = getCurrentTop();
                        gridView.setAdapter(searchAdapter);
                        searchAdapter.notifyDataSetChanged();
                    }
                    if (searchEmptyView != null) {
                        searchEmptyView.setText(LocaleController.getString("NoResult", R.string.NoResult));
                    }
                } else {
                    if (gridView.getAdapter() != listAdapter) {
                        int top = getCurrentTop();
                        searchEmptyView.setText(LocaleController.getString("NoChats", R.string.NoChats));
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

        gridView = new RecyclerListView(context);
        gridView.setTag(13);
        gridView.setPadding(0, 0, 0, AndroidUtilities.dp(8));
        gridView.setClipToPadding(false);
        gridView.setLayoutManager(layoutManager = new GridLayoutManager(getContext(), 4));
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
        containerView.addView(gridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 48, 0, 0));
        gridView.setAdapter(listAdapter = new ShareDialogsAdapter(context));
        gridView.setGlowColor(Theme.getColor(Theme.key_dialogScrollGlow));
        gridView.setOnItemClickListener(new RecyclerListView.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                if (position < 0) {
                    return;
                }
                TLRPC.TL_dialog dialog;
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
                } else {
                    selectedDialogs.put(dialog.id, dialog);
                    cell.setChecked(true, true);
                }
                updateSelectedCount();
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
        containerView.addView(searchEmptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 48, 0, 0));

        containerView.addView(frameLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.TOP));

        shadow = new View(context);
        shadow.setBackgroundResource(R.drawable.header_shadow);
        containerView.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.TOP | Gravity.LEFT, 0, 48, 0, 0));

        frameLayout2 = new FrameLayout(context);
        frameLayout2.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground));
        frameLayout2.setTranslationY(AndroidUtilities.dp(53));
        containerView.addView(frameLayout2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.BOTTOM));
        frameLayout2.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        commentTextView = new EditTextBoldCursor(context);
        commentTextView.setHint(LocaleController.getString("ShareComment", R.string.ShareComment));
        commentTextView.setMaxLines(1);
        commentTextView.setSingleLine(true);
        commentTextView.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        commentTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        commentTextView.setBackgroundDrawable(null);
        commentTextView.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        commentTextView.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        commentTextView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        commentTextView.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        commentTextView.setCursorSize(AndroidUtilities.dp(20));
        commentTextView.setCursorWidth(1.5f);
        commentTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        frameLayout2.addView(commentTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 8, 1, 8, 0));

        shadow2 = new View(context);
        shadow2.setBackgroundResource(R.drawable.header_shadow_reverse);
        shadow2.setTranslationY(AndroidUtilities.dp(53));
        containerView.addView(shadow2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 48));

        updateSelectedCount();

        if (!DialogsActivity.dialogsLoaded[currentAccount]) {
            MessagesController.getInstance(currentAccount).loadDialogs(0, 100, true);
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
        if (scrollOffsetY != newOffset) {
            gridView.setTopGlowOffset(scrollOffsetY = newOffset);
            frameLayout.setTranslationY(scrollOffsetY);
            shadow.setTranslationY(scrollOffsetY);
            searchEmptyView.setTranslationY(scrollOffsetY);
            containerView.invalidate();
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
            Toast.makeText(context, LocaleController.getString("LinkCopied", R.string.LinkCopied), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private void showCommentTextView(final boolean show) {
        if (show == (frameLayout2.getTag() != null)) {
            return;
        }
        if (animatorSet != null) {
            animatorSet.cancel();
        }
        frameLayout2.setTag(show ? 1 : null);
        AndroidUtilities.hideKeyboard(commentTextView);
        animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(shadow2, "translationY", AndroidUtilities.dp(show ? 0 : 53)),
                ObjectAnimator.ofFloat(frameLayout2, "translationY", AndroidUtilities.dp(show ? 0 : 53)));
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.setDuration(180);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation.equals(animatorSet)) {
                    gridView.setPadding(0, 0, 0, AndroidUtilities.dp(show ? 56 : 8));
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
    }

    public void updateSelectedCount() {
        if (selectedDialogs.size() == 0) {
            showCommentTextView(false);
            doneButtonBadgeTextView.setVisibility(View.GONE);
            if (!isPublicChannel && linkToCopy == null) {
                doneButtonTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray4));
                doneButton.setEnabled(false);
                doneButtonTextView.setText(LocaleController.getString("Send", R.string.Send).toUpperCase());
            } else {
                doneButtonTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
                doneButton.setEnabled(true);
                doneButtonTextView.setText(LocaleController.getString("CopyLink", R.string.CopyLink).toUpperCase());
            }
        } else {
            showCommentTextView(true);
            doneButtonTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            doneButtonBadgeTextView.setVisibility(View.VISIBLE);
            doneButtonBadgeTextView.setText(String.format("%d", selectedDialogs.size()));
            doneButtonTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue3));
            doneButton.setEnabled(true);
            doneButtonTextView.setText(LocaleController.getString("Send", R.string.Send).toUpperCase());
        }
    }

    @Override
    public void dismiss() {
        super.dismiss();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.dialogsNeedReload);
    }

    private class ShareDialogsAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;
        private int currentCount;
        private ArrayList<TLRPC.TL_dialog> dialogs = new ArrayList<>();

        public ShareDialogsAdapter(Context context) {
            this.context = context;
            fetchDialogs();
        }

        public void fetchDialogs() {
            dialogs.clear();
            for (int a = 0; a < MessagesController.getInstance(currentAccount).dialogsForward.size(); a++) {
                TLRPC.TL_dialog dialog = MessagesController.getInstance(currentAccount).dialogsForward.get(a);
                int lower_id = (int) dialog.id;
                int high_id = (int) (dialog.id >> 32);
                if (lower_id != 0 && high_id != 1) {
                    if (lower_id > 0) {
                        dialogs.add(dialog);
                    } else {
                        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-lower_id);
                        if (!(chat == null || ChatObject.isNotInChat(chat) || ChatObject.isChannel(chat) && !chat.creator && (chat.admin_rights == null || !chat.admin_rights.post_messages) && !chat.megagroup)) {
                            dialogs.add(dialog);
                        }
                    }
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return dialogs.size();
        }

        public TLRPC.TL_dialog getItem(int i) {
            if (i < 0 || i >= dialogs.size()) {
                return null;
            }
            return dialogs.get(i);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = new ShareDialogCell(context);
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(100)));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ShareDialogCell cell = (ShareDialogCell) holder.itemView;
            TLRPC.TL_dialog dialog = getItem(position);
            cell.setDialog((int) dialog.id, selectedDialogs.indexOfKey(dialog.id) >= 0, null);
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }
    }

    public class ShareSearchAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;
        private Timer searchTimer;
        private ArrayList<DialogSearchResult> searchResult = new ArrayList<>();
        private String lastSearchText;
        private int reqId = 0;
        private int lastReqId;
        private int lastSearchId = 0;

        private class DialogSearchResult {
            public TLRPC.TL_dialog dialog = new TLRPC.TL_dialog();
            public TLObject object;
            public int date;
            public CharSequence name;
        }

        public ShareSearchAdapter(Context context) {
            this.context = context;
        }

        private void searchDialogsInternal(final String query, final int searchId) {
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    try {
                        String search1 = query.trim().toLowerCase();
                        if (search1.length() == 0) {
                            lastSearchId = -1;
                            updateSearchResults(new ArrayList<DialogSearchResult>(), lastSearchId);
                            return;
                        }
                        String search2 = LocaleController.getInstance().getTranslitString(search1);
                        if (search1.equals(search2) || search2.length() == 0) {
                            search2 = null;
                        }
                        String search[] = new String[1 + (search2 != null ? 1 : 0)];
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

                        Collections.sort(searchResults, new Comparator<DialogSearchResult>() {
                            @Override
                            public int compare(DialogSearchResult lhs, DialogSearchResult rhs) {
                                if (lhs.date < rhs.date) {
                                    return 1;
                                } else if (lhs.date > rhs.date) {
                                    return -1;
                                }
                                return 0;
                            }
                        });

                        updateSearchResults(searchResults, searchId);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            });
        }

        private void updateSearchResults(final ArrayList<DialogSearchResult> result, final int searchId) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (searchId != lastSearchId) {
                        return;
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
                }
            });
        }

        public void searchDialogs(final String query) {
            if (query != null && lastSearchText != null && query.equals(lastSearchText)) {
                return;
            }
            lastSearchText = query;
            try {
                if (searchTimer != null) {
                    searchTimer.cancel();
                    searchTimer = null;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (query == null || query.length() == 0) {
                searchResult.clear();
                topBeforeSwitch = getCurrentTop();
                notifyDataSetChanged();
            } else {
                final int searchId = ++lastSearchId;
                searchTimer = new Timer();
                searchTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            cancel();
                            searchTimer.cancel();
                            searchTimer = null;
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        searchDialogsInternal(query, searchId);
                    }
                }, 200, 300);
            }
        }

        @Override
        public int getItemCount() {
            return searchResult.size();
        }

        public TLRPC.TL_dialog getItem(int i) {
            if (i < 0 || i >= searchResult.size()) {
                return null;
            }
            return searchResult.get(i).dialog;
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = new ShareDialogCell(context);
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(100)));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ShareDialogCell cell = (ShareDialogCell) holder.itemView;
            DialogSearchResult result = searchResult.get(position);
            cell.setDialog((int) result.dialog.id, selectedDialogs.indexOfKey(result.dialog.id) >= 0, result.name);
        }

        @Override
        public int getItemViewType(int i) {
            return 0;
        }
    }
}
