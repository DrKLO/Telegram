package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.voip.VoIPService;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.ContentPreviewViewer;

import java.util.ArrayList;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class JoinCallAlert extends BottomSheet {

    private Drawable shadowDrawable;
    private BottomSheetCell doneButton;
    private RecyclerListView listView;
    private TextView textView;
    private TextView messageTextView;

    private ArrayList<TLRPC.Peer> chats;

    private boolean ignoreLayout;

    private int scrollOffsetY;

    private int currentType;

    private int[] location = new int[2];

    private TLRPC.Peer selectedPeer;
    private TLRPC.Peer currentPeer;
    private TLRPC.InputPeer selectAfterDismiss;

    private boolean animationInProgress;

    private JoinCallAlertDelegate delegate;

    public static final int TYPE_CREATE = 0;
    public static final int TYPE_JOIN = 1;
    public static final int TYPE_DISPLAY = 2;

    private static ArrayList<TLRPC.Peer> cachedChats;
    private static long lastCacheTime;
    private static long lastCacheDid;
    private static int lastCachedAccount;

    public static void processDeletedChat(long did) {
        if (cachedChats == null || did > 0) {
            return;
        }
        for (int a = 0, N = cachedChats.size(); a < N; a++) {
            if (MessageObject.getPeerId(cachedChats.get(a)) == did) {
                cachedChats.remove(a);
                break;
            }
        }
        if (cachedChats.isEmpty()) {
            cachedChats = null;
        }
    }

    public interface JoinCallAlertDelegate {
        void didSelectChat(TLRPC.InputPeer peer, boolean hasFewPeers);
    }

    public class BottomSheetCell extends FrameLayout {

        private View background;
        private TextView[] textView = new TextView[2];
        private LinearLayout linearLayout;

        public BottomSheetCell(Context context) {
            super(context);

            background = new View(context);
            background.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), Theme.getColor(Theme.key_featuredStickers_addButton), Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
            addView(background, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 16, 16, 16, 16));

            for (int a = 0; a < 2; a++) {
                textView[a] = new TextView(context);
                textView[a].setLines(1);
                textView[a].setSingleLine(true);
                textView[a].setGravity(Gravity.CENTER_HORIZONTAL);
                textView[a].setEllipsize(TextUtils.TruncateAt.END);
                textView[a].setGravity(Gravity.CENTER);
                textView[a].setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
                textView[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                textView[a].setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                addView(textView[a], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
                if (a == 1) {
                    textView[a].setAlpha(0.0f);
                }
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80), MeasureSpec.EXACTLY));
        }

        public void setText(CharSequence text, boolean animated) {
            if (!animated) {
                textView[0].setText(text);
            } else {
                textView[1].setText(text);
                animationInProgress = true;
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.setDuration(180);
                animatorSet.setInterpolator(CubicBezierInterpolator.EASE_OUT);
                animatorSet.playTogether(
                        ObjectAnimator.ofFloat(textView[0], View.ALPHA, 1.0f, 0.0f),
                        ObjectAnimator.ofFloat(textView[0], View.TRANSLATION_Y, 0, -AndroidUtilities.dp(10)),
                        ObjectAnimator.ofFloat(textView[1], View.ALPHA, 0.0f, 1.0f),
                        ObjectAnimator.ofFloat(textView[1], View.TRANSLATION_Y, AndroidUtilities.dp(10), 0)
                );
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        animationInProgress = false;
                        TextView temp = textView[0];
                        textView[0] = textView[1];
                        textView[1] = temp;
                    }
                });
                animatorSet.start();
            }
        }
    }

    public static void checkFewUsers(Context context, int did, AccountInstance accountInstance, MessagesStorage.BooleanCallback callback) {
        if (lastCachedAccount == accountInstance.getCurrentAccount() && lastCacheDid == did && cachedChats != null && SystemClock.elapsedRealtime() - lastCacheTime < 4 * 60 * 1000) {
            callback.run(cachedChats.size() == 1);
            return;
        }
        final AlertDialog progressDialog = new AlertDialog(context, 3);
        TLRPC.TL_phone_getGroupCallJoinAs req = new TLRPC.TL_phone_getGroupCallJoinAs();
        req.peer = accountInstance.getMessagesController().getInputPeer(did);
        int reqId = accountInstance.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            try {
                progressDialog.dismiss();
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (response != null) {
                TLRPC.TL_phone_joinAsPeers res = (TLRPC.TL_phone_joinAsPeers) response;
                cachedChats = res.peers;
                lastCacheDid = did;
                lastCacheTime = SystemClock.elapsedRealtime();
                lastCachedAccount = accountInstance.getCurrentAccount();
                accountInstance.getMessagesController().putChats(res.chats, false);
                accountInstance.getMessagesController().putUsers(res.users, false);
                callback.run(res.peers.size() == 1);
            }
        }));
        progressDialog.setOnCancelListener(dialog -> accountInstance.getConnectionsManager().cancelRequest(reqId, true));
        try {
            progressDialog.showDelayed(500);
        } catch (Exception ignore) {

        }
    }

    public static void open(Context context, int did, AccountInstance accountInstance, BaseFragment fragment, int type, JoinCallAlertDelegate delegate) {
        if (context == null || delegate == null) {
            return;
        }
        if (lastCachedAccount == accountInstance.getCurrentAccount() && lastCacheDid == did && cachedChats != null && SystemClock.elapsedRealtime() - lastCacheTime < 5 * 60 * 1000) {
            if (cachedChats.size() == 1) {
                TLRPC.InputPeer peer = accountInstance.getMessagesController().getInputPeer(MessageObject.getPeerId(cachedChats.get(0)));
                delegate.didSelectChat(peer, false);
            } else {
                showAlert(context, cachedChats, fragment, type, delegate);
            }
        } else {
            final AlertDialog progressDialog = new AlertDialog(context, 3);
            TLRPC.TL_phone_getGroupCallJoinAs req = new TLRPC.TL_phone_getGroupCallJoinAs();
            req.peer = accountInstance.getMessagesController().getInputPeer(did);
            int reqId = accountInstance.getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                try {
                    progressDialog.dismiss();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                if (response != null) {
                    TLRPC.TL_phone_joinAsPeers res = (TLRPC.TL_phone_joinAsPeers) response;
                    if (res.peers.size() == 1) {
                        TLRPC.InputPeer peer = accountInstance.getMessagesController().getInputPeer(MessageObject.getPeerId(res.peers.get(0)));
                        delegate.didSelectChat(peer, false);
                        return;
                    }
                    cachedChats = res.peers;
                    lastCacheDid = did;
                    lastCacheTime = SystemClock.elapsedRealtime();
                    lastCachedAccount = accountInstance.getCurrentAccount();
                    accountInstance.getMessagesController().putChats(res.chats, false);
                    accountInstance.getMessagesController().putUsers(res.users, false);
                    showAlert(context, res.peers, fragment, type, delegate);
                }
            }));
            progressDialog.setOnCancelListener(dialog -> accountInstance.getConnectionsManager().cancelRequest(reqId, true));
            try {
                progressDialog.showDelayed(500);
            } catch (Exception ignore) {

            }
        }
    }

    private static void showAlert(Context context, ArrayList<TLRPC.Peer> peers, BaseFragment fragment, int type, JoinCallAlertDelegate delegate) {
        JoinCallAlert alert = new JoinCallAlert(context, peers, type, delegate);
        if (fragment != null) {
            if (fragment.getParentActivity() != null) {
                fragment.showDialog(alert);
            }
        } else {
            alert.show();
        }
    }

    private JoinCallAlert(Context context, ArrayList<TLRPC.Peer> chats, int type, JoinCallAlertDelegate delegate) {
        super(context, false);
        setApplyBottomPadding(false);
        this.chats = chats;
        this.delegate = delegate;
        currentType = type;


        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();
        if (type == TYPE_DISPLAY) {
            if (VoIPService.getSharedInstance() != null) {
                int did = VoIPService.getSharedInstance().getSelfId();
                for (int a = 0, N = chats.size(); a < N; a++) {
                    TLRPC.Peer p = chats.get(a);
                    if (MessageObject.getPeerId(p) == did) {
                        selectedPeer = currentPeer = p;
                        break;
                    }
                }
            } else {
                selectedPeer = chats.get(0);
            }
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_voipgroup_inviteMembersBackground), PorterDuff.Mode.MULTIPLY));
        } else {
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));
            selectedPeer = chats.get(0);
        }

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
                measureChildWithMargins(messageTextView, widthMeasureSpec, 0, heightMeasureSpec, 0);
                int h = messageTextView.getMeasuredHeight();
                ((LayoutParams) listView.getLayoutParams()).topMargin = AndroidUtilities.dp(65) + h;
                int measuredWidth = getMeasuredWidth();
                int padding;
                int contentSize = AndroidUtilities.dp(80) + chats.size() * AndroidUtilities.dp(58) + backgroundPaddingTop + AndroidUtilities.dp(55) + h;
                if (contentSize < height / 5 * 3) {
                    padding = height - contentSize;
                } else {
                    padding = height / 5 * 2;
                }
                if (listView.getPaddingTop() != padding) {
                    ignoreLayout = true;
                    listView.setPadding(0, padding, 0, 0);
                    ignoreLayout = false;
                }
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
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

        listView = new RecyclerListView(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent event) {
                boolean result = ContentPreviewViewer.getInstance().onInterceptTouchEvent(event, listView, 0, null);
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
        listView.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(new ListAdapter(context));
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setEnabled(true);
        listView.setGlowColor(Theme.getColor(Theme.key_dialogScrollGlow));
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                updateLayout();
            }
        });
        listView.setOnItemClickListener((view, position) -> {
            if (animationInProgress || chats.get(position) == selectedPeer) {
                return;
            }
            GroupCreateUserCell cell = (GroupCreateUserCell) view;
            selectedPeer = chats.get(position);
            cell.setChecked(true, true);
            for (int a = 0, N = listView.getChildCount(); a < N; a++) {
                GroupCreateUserCell child = (GroupCreateUserCell) listView.getChildAt(a);
                if (child != cell) {
                    child.setChecked(false, true);
                }
            }
            updateDoneButton(true);
        });
        containerView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 100, 0, 80));

        textView = new TextView(context);
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        if (type == TYPE_DISPLAY) {
            textView.setTextColor(Theme.getColor(Theme.key_voipgroup_nameText));
        } else {
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        }
        if (type == TYPE_DISPLAY) {
            textView.setText(LocaleController.getString("VoipGroupDisplayAs", R.string.VoipGroupDisplayAs));
        } else if (type == TYPE_CREATE) {
            textView.setText(LocaleController.getString("VoipGroupStartAs", R.string.VoipGroupStartAs));
        } else {
            textView.setText(LocaleController.getString("VoipGroupJoinAs", R.string.VoipGroupJoinAs));
        }
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        containerView.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 23, 0, 23, 0));

        messageTextView = new TextView(getContext());
        if (type == TYPE_DISPLAY) {
            messageTextView.setTextColor(Theme.getColor(Theme.key_voipgroup_lastSeenText));
        } else {
            messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        }
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        boolean hasGroup = false;
        for (int a = 0, N = chats.size(); a < N; a++) {
            int peerId = MessageObject.getPeerId(chats.get(a));
            if (peerId < 0) {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-peerId);
                if (!ChatObject.isChannel(chat) || chat.megagroup) {
                    hasGroup = true;
                    break;
                }
            }
        }
        if (hasGroup) {
            messageTextView.setText(LocaleController.getString("VoipGroupStartAsInfoGroup", R.string.VoipGroupStartAsInfoGroup));
        } else {
            messageTextView.setText(LocaleController.getString("VoipGroupStartAsInfo", R.string.VoipGroupStartAsInfo));
        }
        messageTextView.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
        messageTextView.setLinkTextColor(Theme.getColor(Theme.key_dialogTextLink));
        messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        containerView.addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 23, 0, 23, 5));

        doneButton = new BottomSheetCell(context);
        doneButton.setBackground(null);
        doneButton.background.setOnClickListener(v -> {
            TLRPC.InputPeer peer = MessagesController.getInstance(currentAccount).getInputPeer(MessageObject.getPeerId(selectedPeer));
            if (selectedPeer != currentPeer && currentType == TYPE_DISPLAY) {
                delegate.didSelectChat(peer, chats.size() > 1);
            } else {
                selectAfterDismiss = peer;
            }
            dismiss();
        });
        containerView.addView(doneButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 0));
        updateDoneButton(false);
    }

    private void updateDoneButton(boolean animated) {
        int did = MessageObject.getPeerId(selectedPeer);
        if (did > 0) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
            doneButton.setText(LocaleController.formatString("VoipGroupContinueAs", R.string.VoipGroupContinueAs, UserObject.getFirstName(user)), animated);
        } else {
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-did);
            doneButton.setText(LocaleController.formatString("VoipGroupContinueAs", R.string.VoipGroupContinueAs, chat.title), animated);
        }
    }

    private void updateLayout() {
        if (listView.getChildCount() <= 0) {
            listView.setTopGlowOffset(scrollOffsetY = listView.getPaddingTop());
            containerView.invalidate();
            return;
        }
        View child = listView.getChildAt(0);
        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findContainingViewHolder(child);
        int top = child.getTop() - AndroidUtilities.dp(9);
        int newOffset = top > 0 && holder != null && holder.getAdapterPosition() == 0 ? top : 0;
        if (scrollOffsetY != newOffset) {
            textView.setTranslationY(top + AndroidUtilities.dp(19));
            messageTextView.setTranslationY(top + AndroidUtilities.dp(56));
            listView.setTopGlowOffset(scrollOffsetY = newOffset);
            containerView.invalidate();
        }
    }

    @Override
    public void dismissInternal() {
        super.dismissInternal();
        if (selectAfterDismiss != null) {
            delegate.didSelectChat(selectAfterDismiss, chats.size() > 1);
        }
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return false;
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;

        public ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return chats.size();
        }

        @Override
        public int getItemViewType(int position) {
            return 0;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = new GroupCreateUserCell(context, 2, 0, false, currentType == TYPE_DISPLAY);
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
            Object object = cell.getObject();
            if (object != null) {
                int did = MessageObject.getPeerId(selectedPeer);
                int id;
                if (object instanceof TLRPC.Chat) {
                    id = -((TLRPC.Chat) object).id;
                } else {
                    id = ((TLRPC.User) object).id;
                }
                cell.setChecked(did == id, false);
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
            int did = MessageObject.getPeerId(chats.get(position));
            TLObject object;
            String status;
            if (did > 0) {
                object = MessagesController.getInstance(currentAccount).getUser(did);
                status = LocaleController.getString("VoipGroupPersonalAccount", R.string.VoipGroupPersonalAccount);
            } else {
                object = MessagesController.getInstance(currentAccount).getChat(-did);
                status = null;
            }
            cell.setObject(object, null, status, position != getItemCount() - 1);
        }
    }
}
