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
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
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
import org.telegram.ui.Cells.ShareDialogCell;

import java.util.ArrayList;

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
    private boolean schedule;

    private boolean animationInProgress;

    private JoinCallAlertDelegate delegate;

    public static final int TYPE_CREATE = 0;
    public static final int TYPE_JOIN = 1;
    public static final int TYPE_DISPLAY = 2;

    private static ArrayList<TLRPC.Peer> cachedChats;
    private static long lastCacheTime;
    private static long lastCacheDid;
    private static int lastCachedAccount;

    public static void resetCache() {
        cachedChats = null;
    }

    public static void processDeletedChat(int account, long did) {
        if (lastCachedAccount != account || cachedChats == null || did > 0) {
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
        void didSelectChat(TLRPC.InputPeer peer, boolean hasFewPeers, boolean schedule, boolean isRtmpStream);
    }

    public class BottomSheetCell extends FrameLayout {

        private View background;
        private TextView[] textView = new TextView[2];
        private boolean hasBackground;

        public BottomSheetCell(Context context, boolean withoutBackground) {
            super(context);

            hasBackground = !withoutBackground;
            setBackground(null);

            background = new View(context);
            if (hasBackground) {
                background.setBackground(Theme.AdaptiveRipple.filledRectByKey(Theme.key_featuredStickers_addButton, 4));
            }
            addView(background, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, 0, 16, withoutBackground ? 0 : 16, 16, 16));

            for (int a = 0; a < 2; a++) {
                textView[a] = new TextView(context);
                textView[a].setFocusable(false);
                textView[a].setLines(1);
                textView[a].setSingleLine(true);
                textView[a].setGravity(Gravity.CENTER_HORIZONTAL);
                textView[a].setEllipsize(TextUtils.TruncateAt.END);
                textView[a].setGravity(Gravity.CENTER);
                if (hasBackground) {
                    textView[a].setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
                    textView[a].setTypeface(AndroidUtilities.bold());
                } else {
                    textView[a].setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
                }
                textView[a].setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                textView[a].setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                textView[a].setPadding(0, 0, 0, hasBackground ? 0 : AndroidUtilities.dp(13));
                addView(textView[a], LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 24, 0, 24, 0));
                if (a == 1) {
                    textView[a].setAlpha(0.0f);
                }
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(hasBackground ? 80 : 50), MeasureSpec.EXACTLY));
        }

        private CharSequence text;

        public void setText(CharSequence text, boolean animated) {
            this.text = text;
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

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setClassName("android.widget.Button");
            info.setClickable(true);
            if (text != null) {
//                info.setText(text);
            }
        }
    }

    public static void checkFewUsers(Context context, long did, AccountInstance accountInstance, MessagesStorage.BooleanCallback callback) {
        if (lastCachedAccount == accountInstance.getCurrentAccount() && lastCacheDid == did && cachedChats != null && SystemClock.elapsedRealtime() - lastCacheTime < 4 * 60 * 1000) {
            callback.run(cachedChats.size() == 1);
            return;
        }
        final AlertDialog progressDialog = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
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

    public static void open(Context context, long did, AccountInstance accountInstance, BaseFragment fragment, int type, TLRPC.Peer scheduledPeer, JoinCallAlertDelegate delegate) {
        if (context == null || delegate == null) {
            return;
        }
        if (lastCachedAccount == accountInstance.getCurrentAccount() && lastCacheDid == did && cachedChats != null && SystemClock.elapsedRealtime() - lastCacheTime < 5 * 60 * 1000) {
            if (cachedChats.size() == 1 && type != TYPE_CREATE) {
                TLRPC.InputPeer peer = accountInstance.getMessagesController().getInputPeer(MessageObject.getPeerId(cachedChats.get(0)));
                delegate.didSelectChat(peer, false, false, false);
            } else {
                showAlert(context, did, cachedChats, fragment, type, scheduledPeer, delegate);
            }
        } else {
            final AlertDialog progressDialog = new AlertDialog(context, AlertDialog.ALERT_TYPE_SPINNER);
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
                        delegate.didSelectChat(peer, false, false, false);
                        return;
                    }
                    cachedChats = res.peers;
                    lastCacheDid = did;
                    lastCacheTime = SystemClock.elapsedRealtime();
                    lastCachedAccount = accountInstance.getCurrentAccount();
                    accountInstance.getMessagesController().putChats(res.chats, false);
                    accountInstance.getMessagesController().putUsers(res.users, false);
                    showAlert(context, did, res.peers, fragment, type, scheduledPeer, delegate);
                }
            }));
            progressDialog.setOnCancelListener(dialog -> accountInstance.getConnectionsManager().cancelRequest(reqId, true));
            try {
                progressDialog.showDelayed(500);
            } catch (Exception ignore) {

            }
        }
    }

    private static void showAlert(Context context, long dialogId, ArrayList<TLRPC.Peer> peers, BaseFragment fragment, int type, TLRPC.Peer scheduledPeer, JoinCallAlertDelegate delegate) {
        if (type == TYPE_CREATE) {
            CreateGroupCallBottomSheet.show(peers, fragment, dialogId, delegate);
            return;
        }
        JoinCallAlert alert = new JoinCallAlert(context, dialogId, peers, type, scheduledPeer, delegate);
        if (fragment != null) {
            if (fragment.getParentActivity() != null) {
                fragment.showDialog(alert);
            }
        } else {
            alert.show();
        }
    }

    private JoinCallAlert(Context context, long dialogId, ArrayList<TLRPC.Peer> arrayList, int type, TLRPC.Peer scheduledPeer, JoinCallAlertDelegate delegate) {
        super(context, false);
        setApplyBottomPadding(false);
        chats = new ArrayList<>(arrayList);
        this.delegate = delegate;
        currentType = type;

        int backgroundColor;
        shadowDrawable = context.getResources().getDrawable(R.drawable.sheet_shadow_round).mutate();
        if (type == TYPE_DISPLAY) {
            if (VoIPService.getSharedInstance() != null) {
                long did = VoIPService.getSharedInstance().getSelfId();
                for (int a = 0, N = chats.size(); a < N; a++) {
                    TLRPC.Peer p = chats.get(a);
                    if (MessageObject.getPeerId(p) == did) {
                        selectedPeer = currentPeer = p;
                        break;
                    }
                }
            } else if (scheduledPeer != null) {
                long did = MessageObject.getPeerId(scheduledPeer);
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
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(backgroundColor = Theme.getColor(Theme.key_voipgroup_inviteMembersBackground), PorterDuff.Mode.MULTIPLY));
        } else {
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(backgroundColor = Theme.getColor(Theme.key_dialogBackground), PorterDuff.Mode.MULTIPLY));
            selectedPeer = chats.get(0);
        }
        fixNavigationBar(backgroundColor);

        ViewGroup internalLayout;
        if (currentType == TYPE_CREATE) {
            LinearLayout linearLayout = new LinearLayout(context) {

                boolean sorted;

                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    if (currentType == TYPE_CREATE) {
                        int width = MeasureSpec.getSize(widthMeasureSpec);
                        int totalWidth = chats.size() * AndroidUtilities.dp(95);
                        LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) listView.getLayoutParams();
                        if (totalWidth > width) {
                            layoutParams.width = LayoutHelper.MATCH_PARENT;
                            layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
                            if (!sorted) {
                                if (selectedPeer != null) {
                                    chats.remove(selectedPeer);
                                    chats.add(0, selectedPeer);
                                }
                                sorted = true;
                            }
                        } else {
                            layoutParams.width = LayoutHelper.WRAP_CONTENT;
                            layoutParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                            if (!sorted) {
                                if (selectedPeer != null) {
                                    int idx;
                                    if (chats.size() % 2 == 0) {
                                        idx = Math.max(0, chats.size() / 2 - 1);
                                    } else {
                                        idx = chats.size() / 2;
                                    }
                                    chats.remove(selectedPeer);
                                    chats.add(idx, selectedPeer);
                                }
                                sorted = true;
                            }
                        }
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            };
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            NestedScrollView scrollView = new NestedScrollView(context);
            scrollView.addView(internalLayout = linearLayout);
            setCustomView(scrollView);
        } else {
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
            internalLayout = containerView;
            containerView.setWillNotDraw(false);
            containerView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
        }

        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);

        listView = new RecyclerListView(context) {
            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        listView.setLayoutManager(new LinearLayoutManager(getContext(), currentType == TYPE_CREATE ? LinearLayoutManager.HORIZONTAL : LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(new ListAdapter(context));
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setEnabled(true);
        listView.setSelectorDrawableColor(0);
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
            selectedPeer = chats.get(position);
            if (view instanceof GroupCreateUserCell) {
                ((GroupCreateUserCell) view).setChecked(true, true);
            } else if (view instanceof ShareDialogCell) {
                ((ShareDialogCell) view).setChecked(true, true);
                view.invalidate();
            }
            for (int a = 0, N = listView.getChildCount(); a < N; a++) {
                View child = listView.getChildAt(a);
                if (child != view) {
                    if (view instanceof GroupCreateUserCell) {
                        ((GroupCreateUserCell) child).setChecked(false, true);
                    } else if (view instanceof ShareDialogCell) {
                        ((ShareDialogCell) child).setChecked(false, true);
                    }
                }
            }
            if (currentType != TYPE_CREATE) {
                updateDoneButton(true, chat);
            }
        });
        if (type != TYPE_CREATE) {
            internalLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 100, 0, 80));
        } else {
            listView.setSelectorDrawableColor(0);
            listView.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        }

        if (type == TYPE_CREATE) {
            RLottieImageView imageView = new RLottieImageView(context);
            imageView.setAutoRepeat(true);
            imageView.setAnimation(R.raw.utyan_schedule, 120, 120);
            imageView.playAnimation();
            internalLayout.addView(imageView, LayoutHelper.createLinear(160, 160, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 17, 8, 17, 0));
        }

        textView = new TextView(context);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        if (type == TYPE_DISPLAY) {
            textView.setTextColor(Theme.getColor(Theme.key_voipgroup_nameText));
        } else {
            textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        }
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        if (type == TYPE_CREATE) {
            if (ChatObject.isChannelOrGiga(chat)) {
                textView.setText(LocaleController.getString(R.string.StartVoipChannelTitle));
            } else {
                textView.setText(LocaleController.getString(R.string.StartVoipChatTitle));
            }
            internalLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 23, 16, 23, 0));
        } else {
            if (type == TYPE_DISPLAY) {
                textView.setText(LocaleController.getString(R.string.VoipGroupDisplayAs));
            } else {
                if (ChatObject.isChannelOrGiga(chat)) {
                    textView.setText(LocaleController.getString(R.string.VoipChannelJoinAs));
                } else {
                    textView.setText(LocaleController.getString(R.string.VoipGroupJoinAs));
                }
            }
            internalLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 23, 8, 23, 0));
        }

        messageTextView = new TextView(getContext());
        if (type == TYPE_DISPLAY) {
            messageTextView.setTextColor(Theme.getColor(Theme.key_voipgroup_lastSeenText));
        } else {
            messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        }
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        boolean hasGroup = false;
        for (int a = 0, N = chats.size(); a < N; a++) {
            long peerId = MessageObject.getPeerId(chats.get(a));
            if (peerId < 0) {
                TLRPC.Chat peerChat = MessagesController.getInstance(currentAccount).getChat(-peerId);
                if (!ChatObject.isChannel(peerChat) || peerChat.megagroup) {
                    hasGroup = true;
                    break;
                }
            }
        }
        messageTextView.setMovementMethod(new AndroidUtilities.LinkMovementMethodMy());
        messageTextView.setLinkTextColor(Theme.getColor(Theme.key_dialogTextLink));
        if (type == TYPE_CREATE) {
            StringBuilder builder = new StringBuilder();
            if (ChatObject.isChannel(chat) && !chat.megagroup) {
                builder.append(LocaleController.getString(R.string.VoipChannelStart2));
            } else {
                builder.append(LocaleController.getString(R.string.VoipGroupStart2));
            }
            if (chats.size() > 1) {
                builder.append("\n\n").append(LocaleController.getString(R.string.VoipChatDisplayedAs));
            } else {
                listView.setVisibility(View.GONE);
            }
            messageTextView.setText(builder);
            messageTextView.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
            internalLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 23, 0, 23, 5));
        } else {
            if (hasGroup) {
                messageTextView.setText(LocaleController.getString(R.string.VoipGroupStartAsInfoGroup));
            } else {
                messageTextView.setText(LocaleController.getString(R.string.VoipGroupStartAsInfo));
            }
            messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
            internalLayout.addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 23, 0, 23, 5));
        }

        if (type == TYPE_CREATE) {
            internalLayout.addView(listView, LayoutHelper.createLinear(chats.size() < 5 ? LayoutHelper.WRAP_CONTENT : LayoutHelper.MATCH_PARENT, 95, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 6, 0, 0));
        }

        doneButton = new BottomSheetCell(context, false);
        doneButton.background.setOnClickListener(v -> {
            TLRPC.InputPeer peer = MessagesController.getInstance(currentAccount).getInputPeer(MessageObject.getPeerId(selectedPeer));
            if (currentType == TYPE_DISPLAY) {
                if (selectedPeer != currentPeer) {
                    delegate.didSelectChat(peer, chats.size() > 1, false, false);
                }
            } else {
                selectAfterDismiss = peer;
            }
            dismiss();
        });
        if (currentType == TYPE_CREATE) {
            internalLayout.addView(doneButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));

            BottomSheetCell scheduleButton = new BottomSheetCell(context, true);
            if (ChatObject.isChannelOrGiga(chat)) {
                scheduleButton.setText(LocaleController.getString(R.string.VoipChannelScheduleVoiceChat), false);
            } else {
                scheduleButton.setText(LocaleController.getString(R.string.VoipGroupScheduleVoiceChat), false);
            }
            scheduleButton.background.setOnClickListener(v -> {
                selectAfterDismiss = MessagesController.getInstance(currentAccount).getInputPeer(MessageObject.getPeerId(selectedPeer));
                schedule = true;
                dismiss();
            });
            internalLayout.addView(scheduleButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50, Gravity.LEFT | Gravity.TOP, 0, 0, 0, 0));
        } else {
            internalLayout.addView(doneButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 50, Gravity.LEFT | Gravity.BOTTOM, 0, 0, 0, 0));
        }
        updateDoneButton(false, chat);
    }

    private void updateDoneButton(boolean animated, TLRPC.Chat chat) {
        if (currentType == TYPE_CREATE) {
            if (ChatObject.isChannelOrGiga(chat)) {
                doneButton.setText(LocaleController.formatString("VoipChannelStartVoiceChat", R.string.VoipChannelStartVoiceChat), animated);
            } else {
                doneButton.setText(LocaleController.formatString("VoipGroupStartVoiceChat", R.string.VoipGroupStartVoiceChat), animated);
            }
        } else {
            long did = MessageObject.getPeerId(selectedPeer);
            if (DialogObject.isUserDialog(did)) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(did);
                doneButton.setText(LocaleController.formatString("VoipGroupContinueAs", R.string.VoipGroupContinueAs, UserObject.getFirstName(user)), animated);
            } else {
                TLRPC.Chat peerChat = MessagesController.getInstance(currentAccount).getChat(-did);
                doneButton.setText(LocaleController.formatString("VoipGroupContinueAs", R.string.VoipGroupContinueAs, peerChat != null ? peerChat.title : ""), animated);
            }
        }
    }

    private void updateLayout() {
        if (currentType == TYPE_CREATE) {
            return;
        }
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
            delegate.didSelectChat(selectAfterDismiss, chats.size() > 1, schedule, false);
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
            View view;
            if (currentType == TYPE_CREATE) {
                view = new ShareDialogCell(context, ShareDialogCell.TYPE_CREATE, null);
                view.setLayoutParams(new RecyclerView.LayoutParams(AndroidUtilities.dp(80), AndroidUtilities.dp(100)));
            } else {
                view = new GroupCreateUserCell(context, 2, 0, false, currentType == TYPE_DISPLAY, null);
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            long did = MessageObject.getPeerId(selectedPeer);
            if (holder.itemView instanceof GroupCreateUserCell) {
                GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
                Object object = cell.getObject();
                long id = 0;
                if (object != null) {
                    if (object instanceof TLRPC.Chat) {
                        id = -((TLRPC.Chat) object).id;
                    } else {
                        id = ((TLRPC.User) object).id;
                    }
                }
                cell.setChecked(did == id, false);
            } else {
                ShareDialogCell cell = (ShareDialogCell) holder.itemView;
                long id = cell.getCurrentDialog();
                cell.setChecked(did == id, false);
            }
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            long did = MessageObject.getPeerId(chats.get(position));
            TLObject object;
            String status;
            if (did > 0) {
                object = MessagesController.getInstance(currentAccount).getUser(did);
                status = LocaleController.getString(R.string.VoipGroupPersonalAccount);
            } else {
                object = MessagesController.getInstance(currentAccount).getChat(-did);
                status = null;
            }
            if (currentType == TYPE_CREATE) {
                ShareDialogCell cell = (ShareDialogCell) holder.itemView;
                cell.setDialog(did, did == MessageObject.getPeerId(selectedPeer), null);
            } else {
                GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
                cell.setObject(object, null, status, position != getItemCount() - 1);
            }
        }
    }
}
