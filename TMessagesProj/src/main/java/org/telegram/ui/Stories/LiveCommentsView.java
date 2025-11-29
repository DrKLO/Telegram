package org.telegram.ui.Stories;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Stories.HighlightMessageSheet.TIER_COLOR1;
import static org.telegram.ui.Stories.HighlightMessageSheet.TIER_COLOR2;
import static org.telegram.ui.Stories.HighlightMessageSheet.TIER_COLOR_BACKGROUND;
import static org.telegram.ui.Stories.HighlightMessageSheet.TIER_EMOJIS;
import static org.telegram.ui.Stories.HighlightMessageSheet.TIER_LENGTH;
import static org.telegram.ui.Stories.HighlightMessageSheet.TIER_PERIOD;
import static org.telegram.ui.Stories.HighlightMessageSheet.getTierOption;
import static org.telegram.messenger.MessagesController.findUpdatesAndRemove;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ReplacementSpan;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_phone;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.Components.spoilers.SpoilersTextView;
import org.telegram.ui.GradientClip;
import org.telegram.ui.ProfileActivity;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stars.StarsReactionsSheet;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class LiveCommentsView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public static final int MAX_MESSAGES_COUNT = 2_000;

    public static class Message {
        public int id;
        public boolean fromAdmin;
        public long dialogId;
        public int date;
        public boolean isReaction;
        public TLRPC.TL_textWithEntities text;
        public long stars;
        public int place;
    }

    public static class TopSender {
        public int currentAccount;
        public long dialogId;
        public int lastSentDate;
        private long max_stars;
        public int place;

        public ArrayList<Message> messages = new ArrayList<>();

        public int getStars() {
            return getStars(ConnectionsManager.getInstance(currentAccount).getCurrentTime());
        }
        public int getStars(int now) {
            int stars = 0;
            for (Message msg : messages) {
                if (msg.stars > 0 && now - msg.date <= getTierOption(currentAccount, (int) msg.stars, TIER_PERIOD)) {
                    stars += (int) msg.stars;
                }
            }
            max_stars = Math.max(max_stars, stars);
            return stars;
        }

        public float getProgress() {
            return getProgress(ConnectionsManager.getInstance(currentAccount).getCurrentTime());
        }
        public float getProgress(int now) {
            int startDate = now, endDate = 0;
            for (Message msg : messages) {
                if (msg.stars > 0) {
                    startDate = Math.min(startDate, msg.date);
                    endDate = Math.max(endDate, msg.date + getTierOption(currentAccount, (int) msg.stars, TIER_PERIOD));
                }
            }
            return AndroidUtilities.ilerp(now, endDate, startDate);
        }

        public int expiresAfter(int now) {
            int startDate = now, endDate = 0;
            for (Message msg : messages) {
                if (msg.stars > 0) {
                    startDate = Math.min(startDate, msg.date);
                    endDate = Math.max(endDate, msg.date + getTierOption(currentAccount, (int) msg.stars, TIER_PERIOD));
                }
            }
            return Math.max(0, endDate - now);
        }

        public boolean isExpired(int now) {
            for (Message msg : messages) {
                if (msg.stars > 0 && now - msg.date <= getTierOption(currentAccount, (int) msg.stars, TIER_PERIOD)) {
                    return false;
                }
            }
            return true;
        }

        public void updateLastSentDate() {
            updateLastSentDate(ConnectionsManager.getInstance(currentAccount).getCurrentTime());
        }
        public void updateLastSentDate(int now) {
            int startDate = now;
            for (Message msg : messages) {
                if (msg.stars > 0) {
                    startDate = Math.min(startDate, msg.date);
                }
            }
            lastSentDate = startDate;
        }
    }

    private final View shadowView;
    private final StoryViewer storyViewer;
    private final FrameLayout topBulletinContainer;

    public final RecyclerListView listView;
    private final LinearLayoutManager layoutManager;
    private final UniversalAdapter adapter;

    public final ImageView arrowButton;

    public final RecyclerListView topListView;
    private final LinearLayoutManager topLayoutManager;
    private final UniversalAdapter topAdapter;

    private final ArrayList<Message> messages = new ArrayList<>();
    private final ArrayList<TopSender> topMessages = new ArrayList<>();
    private final HashMap<Long, Integer> topPlaces = new HashMap<Long, Integer>();

    private long highlightingDialog;
    private int highlightingMessageId;
    private boolean callHighlight;

    private Runnable removeTopSendersRunnable;
    private void removeTopSenders() {
        if (removeTopSendersRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(removeTopSendersRunnable);
            removeTopSendersRunnable = null;
        }

        final int now = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        for (int i = topMessages.size() - 1; i >= 0; --i) {
            if (topMessages.get(i).isExpired(now)) {
                topMessages.remove(i);
            }
        }
        lastNow = now;
        Collections.sort(topMessages, this::sortTopMessages);
        topAdapter.update(true);

        updateTopMessages(true);
        scheduleRemovingTopSenders();
    }
    private void scheduleRemovingTopSenders() {
        if (removeTopSendersRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(removeTopSendersRunnable);
            removeTopSendersRunnable = null;
        }
        final int now = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        long delay = Long.MAX_VALUE;
        for (TopSender s : topMessages) {
            delay = Math.min(delay, s.expiresAfter(now) * 1000L);
        }

        if (delay >= Long.MAX_VALUE) {
            return;
        }
        AndroidUtilities.runOnUIThread(removeTopSendersRunnable = () -> removeTopSenders(), delay);
    }

    public int maxReadId = -1;

    public LiveCommentsView(Context context, StoryViewer storyViewer, ViewGroup container, View shadowView, FrameLayout topBulletinContainer) {
        super(context);

        this.shadowView = shadowView;
        this.storyViewer = storyViewer;
        this.topBulletinContainer = topBulletinContainer;

        shadowView.setAlpha(0.5f);

        listView = new RecyclerListView(context) {
            @Override
            public void invalidate() {
                super.invalidate();
                LiveCommentsView.this.invalidate();
            }

            @Override
            public Integer getSelectorColor(int position) {
                return 0;
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (isCollapsed()) {
                    return false;
                }
                return super.dispatchTouchEvent(ev);
            }

            public int getMaxVisibleId() {
                if (collapsed)
                    return -1;
                for (int i = 0; i < getChildCount(); ++i) {
                    final View child = getChildAt(i);
                    if (child instanceof LiveCommentView && ((LiveCommentView) child).message != null) {
                        return ((LiveCommentView) child).message.id;
                    }
                }
                return -1;
            }

            @Override
            protected void dispatchDraw(Canvas canvas) {
                final int maxVisibleId = getMaxVisibleId();
                if (maxVisibleId > maxReadId) {
                    maxReadId = maxVisibleId;
                    onMessagesCountUpdated();
                }

                super.dispatchDraw(canvas);
            }

            @Override
            protected void onMeasure(int widthSpec, int heightSpec) {
                int w = MeasureSpec.getSize(widthSpec);
                int h = MeasureSpec.getSize(heightSpec);
                super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(w, MeasureSpec.getMode(heightSpec)));
            }
        };
        listView.setWillNotDraw(false);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, true));
        listView.setAdapter(adapter = new UniversalAdapter(listView, context, currentAccount, 0, false, this::fillItems, new DarkThemeResourceProvider()) {
            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                super.onBindViewHolder(holder, position);
                if (callHighlight && holder.itemView instanceof LiveCommentView) {
                    final LiveCommentView commentView = (LiveCommentView) holder.itemView;
                    if (commentView.message != null && commentView.message.id == highlightingMessageId) {
                        commentView.highlight();
                        callHighlight = false;
                    }
                }
            }
            @Override
            public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
                super.onViewAttachedToWindow(holder);
                if (callHighlight && holder.itemView instanceof LiveCommentView) {
                    final LiveCommentView commentView = (LiveCommentView) holder.itemView;
                    if (commentView.message != null && commentView.message.id == highlightingMessageId) {
                        commentView.highlight();
                        callHighlight = false;
                    }
                }
            }
        });
        adapter.setApplyBackground(false);
        listView.setPadding(dp(8), dp(7.5f), dp(8), dp(7.5f));
        listView.setClipToPadding(false);
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM, 0, 0, 0, 34));
        listView.setOnItemClickListener((v, position) -> {
            final LiveCommentView commentView = (LiveCommentView) v;
            final Message message = commentView.message;

            final ItemOptions o = ItemOptions.makeOptions(container, new DarkThemeResourceProvider(), v);
            o.addText(LocaleController.formatString(R.string.LiveStoryMessageSent, LocaleController.formatDateTime(message.date, true)), 15);
            o.addGap();
            o.add(R.drawable.msg_openprofile, getString(R.string.OpenProfile), () -> {
                storyViewer.presentFragment(ProfileActivity.of(message.dialogId));
            });
            o.addIf(!message.isReaction, R.drawable.msg_copy, getString(R.string.Copy), () -> {
                AndroidUtilities.addToClipboard(commentView.text);
                if (AndroidUtilities.shouldShowClipboardToast()) {
                    Toast.makeText(getContext(), LocaleController.getString(R.string.TextCopied), Toast.LENGTH_SHORT).show();
                }
            });
            o.addIf(
                    dialogId == UserConfig.getInstance(currentAccount).getClientUserId() || isAdmin(),
                    R.drawable.msg_delete, getString(R.string.Delete), () -> {
                        if (isMe(message.dialogId)) {
                            final TL_phone.deleteGroupCallMessages req = new TL_phone.deleteGroupCallMessages();
                            req.call = inputCall;
                            req.messages.add(message.id);
                            ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
                            delete(message.id);
                            return;
                        }
                        openDeleteMessage(getContext(), message.dialogId, (report, deleteAll, ban) -> {
                            if (deleteAll) {
                                final TL_phone.deleteGroupCallParticipantMessages req = new TL_phone.deleteGroupCallParticipantMessages();
                                req.call = inputCall;
                                req.participant = MessagesController.getInstance(currentAccount).getInputPeer(message.dialogId);
                                req.report_spam = report;
                                ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);

                                deleteAllFrom(message.dialogId);
                            } else {
                                final TL_phone.deleteGroupCallMessages req = new TL_phone.deleteGroupCallMessages();
                                req.call = inputCall;
                                req.messages.add(message.id);
                                ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);

                                delete(message.id);
                            }

                            if (ban) {
                                if (dialogId >= 0) {
                                    MessagesController.getInstance(currentAccount).blockPeer(dialogId);
                                } else {
                                    MessagesController.getInstance(currentAccount).deleteParticipantFromChat(-dialogId, MessagesController.getInstance(currentAccount).getInputPeer(message.dialogId), false, true);
                                }
                            }
                        });
                    });
            o.show();
        });

        DefaultItemAnimator itemAnimator = new DefaultItemAnimator() {
            @Override
            protected float animateByScale(View view) {
                return 0.5f;
            }

            @Override
            protected void onMoveAnimationUpdate(RecyclerView.ViewHolder holder) {
                super.onMoveAnimationUpdate(holder);
                listView.invalidate();
            }

            @Override
            protected void onAddAnimationUpdate(RecyclerView.ViewHolder holder) {
                super.onAddAnimationUpdate(holder);
                listView.invalidate();
            }
        };
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(280);
        itemAnimator.setDelayIncrement(14);
        listView.setItemAnimator(itemAnimator);

        arrowButton = new ImageView(context);
        arrowButton.setImageResource(R.drawable.msg_arrowright);
        arrowButton.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        arrowButton.setRotation(90.0f);
        arrowButton.setBackground(Theme.createSelectorDrawable(0x40FFFFFF));
//        addView(arrowButton, LayoutHelper.createFrame(26, 26, Gravity.LEFT | Gravity.BOTTOM, 10, 9, 10, 9));
        arrowButton.setOnClickListener(v -> {
            setCollapsed(!collapsed, true);
        });

        topListView = new RecyclerListView(context) {
            @Override
            public Integer getSelectorColor(int position) {
                return 0;
            }
        };
        topListView.setWillNotDraw(false);
        topListView.setLayoutManager(topLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        topListView.setAdapter(topAdapter = new UniversalAdapter(topListView, context, currentAccount, 0, this::fillTopItems, null));
        topAdapter.setApplyBackground(false);
        topListView.setPadding(dp(8), 0, dp(8), 0);
        topListView.setClipToPadding(false);
        addView(topListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 26, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 0, 0, 0, 9.66f));
        topListView.setOnItemClickListener((v, position, x, y) -> {
            final LiveTopSenderView view = (LiveTopSenderView) v;
            final TopSender sender = view.sender;

            final int now = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            final HashSet<Integer> messageIds = new HashSet<>();
            for (int i = 0; i < sender.messages.size(); ++i) {
                final Message msg = sender.messages.get(i);
                if (msg.stars > 0 && now - msg.date <= getTierOption(currentAccount, (int) msg.stars, TIER_PERIOD)) {
                    messageIds.add(msg.id);
                }
            }

            final long minStars = livePlayer == null ? 0 : livePlayer.getSendPaidMessagesStars();
            int msg_id = -1;
            int msg_position = 0;
            for (int i = 0; i < messages.size(); ++i) {
                final Message msg = messages.get(i);
                if (!(!msg.fromAdmin && msg.isReaction && msg.stars < minStars)) {
                    if (messageIds.contains(msg.id) && (highlightingDialog != sender.dialogId || highlightingMessageId == 0 || msg.id < highlightingMessageId)) {
                        msg_id = msg.id;
                        break;
                    }
                    msg_position++;
                }
            }
            if (msg_id < 0) {
                msg_id = -1;
                msg_position = 0;
                for (int i = 0; i < messages.size(); ++i) {
                    final Message msg = messages.get(i);
                    if (!(!msg.fromAdmin && msg.isReaction && msg.stars < minStars)) {
                        if (messageIds.contains(msg.id)) {
                            msg_id = msg.id;
                            break;
                        }
                        msg_position++;
                    }
                }
            }
            if (msg_id < 0) return;

            highlightingDialog = sender.dialogId;
            highlightingMessageId = msg_id;
            callHighlight = true;

            final RecyclerView.ItemAnimator ia = listView.getItemAnimator();
            listView.setItemAnimator(null);
            layoutManager.scrollToPositionWithOffset(msg_position, listView.getHeight() / 2, true);
            adapter.notifyItemChanged(msg_position);
            listView.setItemAnimator(ia);

//            final ItemOptions o = ItemOptions.makeOptions(container, new DarkThemeResourceProvider(), v);
//            o.addDialog(currentAccount, sender.dialogId, () -> {
//                storyViewer.presentFragment(ProfileActivity.of(sender.dialogId));
//            });
//            o.show();
        });

        itemAnimator = new DefaultItemAnimator() {
            @Override
            protected float animateByScale(View view) {
                return 0.5f;
            }
        };
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(350);
        topListView.setItemAnimator(itemAnimator);

        updateTopMessages(false);
    }

    public int getListViewContentTop() {
        int top = listView.getHeight();
        for (int i = 0; i < listView.getChildCount(); ++i) {
            top = Math.min(listView.getChildAt(i).getTop(), top);
        }
        return top;
    }

    public float top() {
        return listView.getY() + Math.max(Math.max(0, keyboardOffset - listView.getTop()), getListViewContentTop());
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getY() < top()) {
            return false;
        }
        return super.onInterceptTouchEvent(ev);
    }

    private boolean allowTouches = true;
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!allowTouches) return false;
        if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getY() < top()) {
            return false;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getY() < top()) {
            return false;
        }
        return super.onTouchEvent(ev);
    }

    public void setAllowTouches(boolean allowTouches) {
        this.allowTouches = allowTouches;
    }

    private long lastMinStars;
    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final long minStars = lastMinStars = livePlayer == null ? 0 : livePlayer.getSendPaidMessagesStars();
        for (int i = 0; i < messages.size(); ++i) {
            final Message msg = messages.get(i);
            // do not show reaction messages lower than required minimum for a message
            if (!(!msg.fromAdmin && msg.isReaction && msg.stars < minStars)) {
                items.add(LiveCommentView.Factory.of(msg));
            }
        }
    }

    private void fillTopItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        for (int i = 0; i < topMessages.size(); ++i) {
            items.add(LiveTopSenderView.Factory.of(topMessages.get(i)));
        }
    }

    private int getListViewTop() {
        int top = listView.getHeight();
        for (int i = 0; i < listView.getChildCount(); ++i) {
            final View c = listView.getChildAt(i);
            top = Math.min(c.getTop(), top);
        }
        return listView.getHeight() - top;
    }

    private final GradientClip gradientClip = new GradientClip();

    @Override
    protected boolean drawChild(@NonNull Canvas canvas, View child, long drawingTime) {
        if (child == listView) {
            if (listView.getAlpha() <= 0) {
                return true;
            }

            final float top = listView.getY() + Math.max(0, keyboardOffset - listView.getTop());

            canvas.saveLayerAlpha(listView.getX(), listView.getY(), listView.getX() + listView.getWidth(), listView.getY() + listView.getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
            canvas.save();

            canvas.translate(0, (1.0f - listView.getAlpha()) * Math.min((listView.getY() + listView.getHeight()) - top, getListViewTop()));

            canvas.clipRect(0, top, getWidth(), getHeight());
            boolean r = super.drawChild(canvas, child, drawingTime);
            canvas.restore();

            AndroidUtilities.rectTmp.set(0, top, getWidth(), top + dp(12));
            gradientClip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.TOP, 1.0f);

            AndroidUtilities.rectTmp.set(0, listView.getY() + listView.getHeight() - dp(12), getWidth(), listView.getBottom() + listView.getHeight());
            gradientClip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.BOTTOM, 1.0f);

            canvas.restore();

            return r;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    private boolean hasTopMessages;
    private void updateTopMessages(boolean animated) {
        if (animated && hasTopMessages == (!topMessages.isEmpty())) return;

        hasTopMessages = !topMessages.isEmpty();

        if (animated) {
            listView.animate()
                .translationY(hasTopMessages ? 0 : dp(26 + 9))
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setUpdateListener(a -> {
                    invalidate();
                })
                .setDuration(420)
                .start();
            topListView.animate()
                .translationY(hasTopMessages ? 0 : dp(26 + 9))
                .alpha(hasTopMessages ? 1.0f : 0.0f)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(420)
                .start();
        } else {
            listView.setTranslationY(hasTopMessages ? 0 : dp(26 + 9));
            topListView.setTranslationY(hasTopMessages ? 0 : dp(26 + 9));
            topListView.setAlpha(hasTopMessages ? 1.0f : 0.0f);
            invalidate();
        }
    }

    private float keyboardT;
    private float keyboardOffset;
    private float keyboardFinalOffset;
    public void setKeyboardOffset(float t, float offset, float finalOffset) {
        keyboardT = t;
        keyboardOffset = offset;
        if (Math.abs(keyboardFinalOffset - finalOffset) > 0.1f) {
            keyboardFinalOffset = finalOffset;
            listView.setPadding(dp(8), dp(7.5f) + Math.max(0, (int) finalOffset - listView.getTop()), dp(8), dp(7.5f));

            if (!listView.canScrollVertically(1)) {
                layoutManager.scrollToPositionWithOffset(0, dp(100));
            }
        }
        setTranslationY(-keyboardOffset);
        invalidate();
    }

    public void clear() {
        messages.clear();
        adapter.update(true);
    }

    private long dialogId;
    private final int currentAccount = UserConfig.selectedAccount;
    private TLRPC.InputGroupCall inputCall;
    public boolean setup(long dialogId, final TLRPC.InputGroupCall inputCall) {
        boolean changed = false;
        if ((this.inputCall == null ? 0 : this.inputCall.id) != (inputCall == null ? 0 : inputCall.id)) {
            this.clear();
            changed = true;
        }
        if (this.inputCall != null) {
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.liveStoryMessageUpdate);
        }
        this.dialogId = dialogId;
        this.inputCall = inputCall;
        if (this.inputCall != null) {
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.liveStoryMessageUpdate);
        }
        if (changed) {
            closeBulletin.run();
            if (inputCall == null) {
                AndroidUtilities.cancelRunOnUIThread(pollStarsRunnable);
            } else {
                pollStarsRunnable.run();
            }
        }
        return changed;
    }

    public void saveHistory() {
        if (livePlayer != null) {
            livePlayer.messages = messages;
            livePlayer.topMessages = topMessages;
        }
    }

    private LivePlayer livePlayer;
    public void setLivePlayer(LivePlayer livePlayer) {
        final boolean hadNoPlayer = this.livePlayer == null;
        this.livePlayer = livePlayer;
        if (
            hadNoPlayer && livePlayer != null &&
            livePlayer.messages != null && livePlayer.topMessages != null &&
            livePlayer.messages != this.messages && livePlayer.topMessages != this.topMessages &&
            messages.isEmpty() && topMessages.isEmpty()
        ) {
            messages.addAll(livePlayer.messages);
            topMessages.addAll(livePlayer.topMessages);
            adapter.update(true);
            lastNow = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            Collections.sort(topMessages, this::sortTopMessages);
            topAdapter.update(true);
            updateTopMessages(false);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        setAllowTouches(true);
        super.onAttachedToWindow();
        if (inputCall != null) {
            AndroidUtilities.cancelRunOnUIThread(pollStarsRunnable);
            AndroidUtilities.runOnUIThread(pollStarsRunnable);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (inputCall != null) {
            AndroidUtilities.cancelRunOnUIThread(pollStarsRunnable);
        }
    }

    private long totalStars;
    private long localStars;
    private boolean sentStars;
    private ArrayList<TL_phone.groupCallDonor> topDonors = new ArrayList<>();

    private boolean polling;
    private Runnable pollStarsRunnable = () -> pollStars();
    private void pollStars() {
        if (inputCall == null || polling) return;

        AndroidUtilities.cancelRunOnUIThread(pollStarsRunnable);
        polling = true;

        final TL_phone.getGroupCallStars req = new TL_phone.getGroupCallStars();
        req.call = inputCall;
        ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
            polling = false;
            if (inputCall == null || inputCall.id != req.call.id) {
                return;
            }

            if (res != null) {
                MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                MessagesController.getInstance(currentAccount).putChats(res.chats, false);

                boolean sentStars = false;
                for (int i = 0; i < res.top_donors.size(); ++i) {
                    if (res.top_donors.get(i).my) {
                        sentStars = res.top_donors.get(i).stars > 0;
                        break;
                    }
                }
                boolean starsUpdated = res.total_stars != totalStars || this.sentStars != sentStars;
                totalStars = res.total_stars;
                topDonors = res.top_donors;
                this.sentStars = sentStars;

                if (starsUpdated) {
                    onStarsCountUpdated();
                }
                updateMessagesPlaces();
            }

            if (isAttachedToWindow()) {
                AndroidUtilities.cancelRunOnUIThread(pollStarsRunnable);
                AndroidUtilities.runOnUIThread(pollStarsRunnable, 5_000);
            }
        });
    }

    public LiveCommentView findComment(int messageId) {
        for (int i = 0; i < listView.getChildCount(); ++i) {
            View child = listView.getChildAt(i);
            if (child instanceof LiveCommentView) {
                LiveCommentView commentView = (LiveCommentView) child;
                if (commentView.message != null && commentView.message.id == messageId) {
                    return commentView;
                }
            }
        }
        return null;
    }

    public static final long REACTIONS_TIMEOUT = 5_000;
    private Bulletin starsBulletin;
    private Bulletin.TwoLineAnimatedLottieLayout bulletinLayout;
    private Bulletin.UndoButton bulletinButton;
    private Bulletin.TimerView timerView;

    public void sendStars(long stars, boolean withEffects) {
        if (starsBulletin == null || !starsBulletin.isShowing()) {
            final Theme.ResourcesProvider resourcesProvider = new DarkThemeResourceProvider();
            bulletinLayout = new Bulletin.TwoLineAnimatedLottieLayout(getContext(), resourcesProvider);
            bulletinLayout.setAnimation(R.raw.stars_topup);
            bulletinLayout.titleTextView.setText(getStarsToastTitle());
            bulletinButton = new Bulletin.UndoButton(getContext(), true, false, resourcesProvider);
            bulletinButton.setText(LocaleController.getString(R.string.StarsSentUndo));
            bulletinButton.setUndoAction(this::cancelStars);
            timerView = new Bulletin.TimerView(getContext(), resourcesProvider);
            timerView.timeLeft = REACTIONS_TIMEOUT;
            timerView.setColor(Theme.getColor(Theme.key_undo_cancelColor, resourcesProvider));
            bulletinButton.addView(timerView, LayoutHelper.createFrame(20, 20, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 12, 0));
            bulletinButton.undoTextView.setPadding(dp(12), dp(8), dp(20 + 10), dp(8));
            bulletinLayout.setButton(bulletinButton);
            starsBulletin = BulletinFactory.of(topBulletinContainer, resourcesProvider).create(bulletinLayout, -1);
            starsBulletin.hideAfterBottomSheet = false;
            starsBulletin.show(true);
            starsBulletin.setOnHideListener(closeBulletin);
        }

        localStars += stars;
        onCancelledStarReaction(getDefaultPeerId());
        onStarReaction(getDefaultPeerId(), getTotalMyStars(), (int) localStars);

        bulletinLayout.titleTextView.setText(getStarsToastTitle());
        bulletinLayout.subtitleTextView.setText(getStarsToastSubtitle());
        timerView.timeLeft = REACTIONS_TIMEOUT;

        AndroidUtilities.cancelRunOnUIThread(closeBulletin);
        AndroidUtilities.runOnUIThread(closeBulletin, REACTIONS_TIMEOUT);

        onStarsButtonPressed(localStars, withEffects);
        onStarsCountUpdated();
    }

    private int getTotalMyStars() {
        int stars = 0;
        stars += localStars;
        for (int i = 0; i < topDonors.size(); ++i) {
            if (topDonors.get(i).my) {
                stars += topDonors.get(i).stars;
            }
        }
        return stars;
    }

    public void openStarsSheet(boolean disabledPaidFeatures) {
        closeBulletin.run();
        final ArrayList<TLRPC.MessageReactor> reactors = new ArrayList<>();
        if (topDonors != null) {
            for (int i = 0; i < topDonors.size(); ++i) {
                final TL_phone.groupCallDonor donor = topDonors.get(i);
                final TLRPC.TL_messageReactor r = new TLRPC.TL_messageReactor();
                r.anonymous = donor.anonymous;
                r.my = donor.my;
                r.count = (int) donor.stars;
                r.peer_id = donor.peer_id;
                reactors.add(r);
            }
        }
        long send_as = UserConfig.getInstance(currentAccount).getClientUserId();
        final TLRPC.Peer sendAsPeer = getDefaultSendAs();
        if (sendAsPeer != null)
            send_as = DialogObject.getPeerDialogId(sendAsPeer);
        final StarsReactionsSheet sheet = new StarsReactionsSheet(getContext(), currentAccount, dialogId, null, null, reactors, !disabledPaidFeatures, true, send_as, new DarkThemeResourceProvider() {
            @Override
            public void appendColors() {
                sparseIntArray.put(Theme.key_divider, 0x14FFFFFF);
            }
        });
        sheet.setLiveCommentsView(this);
        sheet.setOnSend((peer, stars) -> {
            closeBulletin.run();
            localStars = stars;
            Bulletin b = BulletinFactory.of(topBulletinContainer, new DarkThemeResourceProvider())
                .createSimpleBulletin(R.raw.stars_topup, getStarsToastTitle(), getStarsToastSubtitle());
            b.hideAfterBottomSheet = false;
            b.show(true);

            localStars = 0;
            sentStars = true;

            int msg_id = send(new TLRPC.TL_textWithEntities(), stars);

            final long minStars = livePlayer == null ? 0 : livePlayer.getSendPaidMessagesStars();
            final boolean fromAdmin = getDefaultPeerId() == this.dialogId && isAdmin();
            if (stars < minStars && !fromAdmin) {
                return Integer.MIN_VALUE;
            }

            return msg_id;
        });
        sheet.show();
    }

    private Runnable closeBulletin = () -> {
        AndroidUtilities.cancelRunOnUIThread(this.closeBulletin);
        if (starsBulletin != null) {
            starsBulletin.hide();
            starsBulletin = null;
        }
        if (localStars > 0) {
            final long stars = localStars;
            localStars = 0;
            sentStars = true;
            send(new TLRPC.TL_textWithEntities(), stars);
        } else {
            onStarsCountUpdated();
        }
    };

    public void cancelStars() {
        localStars = 0;
        onCancelledStarReaction(getDefaultPeerId());
        onStarsButtonCancelled();
        onStarsCountUpdated();
    }

    private String getStarsToastTitle() {
//        if (isAnonymous()) {
//            return getString(R.string.StarsSentAnonymouslyTitle);
//        } else if (getPeerId() != 0 && getPeerId() != UserConfig.getInstance(currentAccount).getClientUserId()) {
//            return formatString(R.string.StarsSentTitleChannel, DialogObject.getShortName(getPeerId()));
//        } else {
            return getString(R.string.StarsSentTitle);
//        }
    }

    private CharSequence getStarsToastSubtitle() {
        return AndroidUtilities.replaceTags(LocaleController.formatPluralStringComma("PaidMessageSentSubtitle", Math.max(0, (int) localStars)));
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    private ValueAnimator collapseAnimator;
    private boolean collapsed = false;
    public void setCollapsed(boolean collapsed, boolean animated) {
        if (animated && this.collapsed == collapsed)
            return;

        this.collapsed = collapsed;
        if (collapseAnimator != null) {
            collapseAnimator.cancel();
            collapseAnimator = null;
        }
        listView.invalidate();

        if (animated) {
            collapseAnimator = ValueAnimator.ofFloat(listView.getAlpha(), collapsed ? 0 : 1);
            collapseAnimator.addUpdateListener(a -> {
                final float t = (float) a.getAnimatedValue();
                listView.setAlpha(t);
                shadowView.setAlpha(lerp(0, 0.5f, t));
                invalidate();
            });
            collapseAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    listView.setAlpha(collapsed ? 0 : 1);
                    shadowView.setAlpha(collapsed ? 0 : 0.5f);
                    invalidate();
                }
            });
            collapseAnimator.setDuration(420);
            collapseAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            collapseAnimator.start();
        } else {
            shadowView.setAlpha(collapsed ? 0 : 0.5f);
            listView.setAlpha(collapsed ? 0.0f : 1.0f);
        }
        invalidate();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.liveStoryMessageUpdate) {
            final long callId = (long) args[0];
            final TLObject obj = (TLObject) args[1];
            final boolean isHistory = (boolean) args[2];
            if (obj instanceof TLRPC.TL_updateGroupCallMessage) {
                final TLRPC.TL_updateGroupCallMessage update = (TLRPC.TL_updateGroupCallMessage) obj;
                if (inputCall != null && inputCall.id == callId) {
                    push(update.message.date, update.message.id, update.message.from_admin, DialogObject.getPeerDialogId(update.message.from_id), update.message.message, update.message.paid_message_stars, isHistory);
                }
            } else if (obj instanceof TLRPC.TL_updateDeleteGroupCallMessages) {
                final TLRPC.TL_updateDeleteGroupCallMessages update = (TLRPC.TL_updateDeleteGroupCallMessages) obj;
                if (inputCall != null && inputCall.id == callId) {
                    for (int msgId : update.messages) {
                        delete(msgId);
                    }
                }
            }
        }
    }

    public void delete(int id) {
        int index = -1;
        Message message = null;
        for (int i = 0; i < messages.size(); ++i) {
            if (messages.get(i).id == id) {
                message = messages.get(i);
                index = i;
                break;
            }
        }
        if (message == null) return;

        if (message.id < 0 && message.isReaction && message.stars > 0) {
            totalStars -= message.stars;
            onStarsCountUpdated();
        }

        boolean updatedTopMessages = false;
        for (int i = 0; i < topMessages.size(); ++i) {
            if (topMessages.get(i).messages.contains(message)) {
                topMessages.get(i).messages.remove(message);
                if (topMessages.get(i).messages.isEmpty()) {
                    topMessages.remove(i);
                    updatedTopMessages = true;
                } else {
                    topMessages.get(i).updateLastSentDate();
                    scheduleRemovingTopSenders();
                }
                break;
            }
        }

        messages.remove(index);
        adapter.update(true);

        if (updatedTopMessages) {
            lastNow = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            Collections.sort(topMessages, this::sortTopMessages);
            topAdapter.update(true);
            updateMessagesPlaces();

            updateTopMessages(true);
        }
    }

    private int lastNow;
    private int sortTopMessages(TopSender a, TopSender b) {
        return b.lastSentDate - a.lastSentDate;
        // return b.getStars(lastNow) - a.getStars(lastNow);
    }

    public void deleteAllFrom(long peer) {
        boolean updatedTopMessages = false;
        for (int i = 0; i < messages.size(); ++i) {
            if (messages.get(i).dialogId == peer) {
                final Message message = messages.get(i);

                for (int j = 0; j < topMessages.size(); ++j) {
                    if (topMessages.get(j).messages.contains(message)) {
                        topMessages.get(j).messages.remove(message);
                        if (topMessages.get(j).messages.isEmpty()) {
                            topMessages.remove(j);
                            updatedTopMessages = true;
                        } else {
                            scheduleRemovingTopSenders();
                        }
                        break;
                    }
                }

                messages.remove(i);
                adapter.update(true);
                i--;
            }
        }

        if (updatedTopMessages) {
            lastNow = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            Collections.sort(topMessages, this::sortTopMessages);
            topAdapter.update(true);
            updateMessagesPlaces();
        }
    }

    protected TLRPC.Peer getDefaultSendAs() {
        return null;
    }

    protected boolean isMe(long dialogId) {
        return dialogId == UserConfig.getInstance(currentAccount).getClientUserId();
    }

    public boolean isAdmin() {
        if (getDefaultPeerId() < 0 && getDefaultPeerId() != dialogId)
            return false;
        if (dialogId >= 0) {
            return dialogId == UserConfig.getInstance(currentAccount).getClientUserId();
        } else {
            if (livePlayer != null && (inputCall != null && inputCall.id == livePlayer.getCallId()) && livePlayer.isCreator()) {
                return true;
            }
            final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
            return ChatObject.canManageCalls(chat);
        }
    }

    private long getDefaultPeerId() {
        final TLRPC.Peer defPeer = getDefaultSendAs();
        if (livePlayer != null && livePlayer.isAdmin() && livePlayer.sendAsDisabled()) {
            return dialogId;
        }
        return defPeer == null ? UserConfig.getInstance(currentAccount).getClientUserId() : DialogObject.getPeerDialogId(defPeer);
    }

    public int send(TLRPC.TL_textWithEntities text, long stars) {
        return send(getDefaultPeerId(), text, stars);
    }

    public int send(long send_as, TLRPC.TL_textWithEntities text, long stars) {
        final int id = UserConfig.getInstance(currentAccount).getNewMessageId();
        final TL_phone.sendGroupCallMessage req = new TL_phone.sendGroupCallMessage();
        req.call = inputCall;
        req.message = text;
        if (stars > 0) {
            req.flags |= TLObject.FLAG_0;
            req.allow_paid_stars = stars;
        }
        req.random_id = Utilities.random.nextLong();
        req.flags |= TLObject.FLAG_1;
        req.send_as = MessagesController.getInstance(currentAccount).getInputPeer(send_as);
        ConnectionsManager.getInstance(UserConfig.selectedAccount).sendRequest(req, (res, err) -> {
            if (res instanceof TLRPC.Updates) {
                final TLRPC.Updates updates = (TLRPC.Updates) res;
                for (TLRPC.TL_updateMessageID u : findUpdatesAndRemove(updates, TLRPC.TL_updateMessageID.class)) {
                    if (req.random_id == u.random_id) {
                        updateMessageId(id, u.id);
                    }
                }
                MessagesController.getInstance(currentAccount).processUpdates(updates, false);
            } else if (err != null) {
                AndroidUtilities.runOnUIThread(() -> {
                    delete(id);
                    if ("BALANCE_TOO_LOW".equalsIgnoreCase(err.text)) {
                        new StarsIntroActivity.StarsNeededSheet(getContext(), new DarkThemeResourceProvider(), stars, StarsIntroActivity.StarsNeededSheet.TYPE_LIVE_COMMENTS, "", () -> send(send_as, text, stars), dialogId).show();
                    } else if ("GROUPCALL_INVALID".equalsIgnoreCase(err.text)) {
                        if (livePlayer != null) {
                            livePlayer.storyDeleted();
                        }
                    } else {
                        BulletinFactory.of(topBulletinContainer, new DarkThemeResourceProvider()).showForError(err, true);
                    }
                });
            }
        });

        if (topDonors != null && stars > 0) {
            TL_phone.groupCallDonor myDonor = null;
            for (int i = 0; i < topDonors.size(); ++i) {
                if (topDonors.get(i).my) {
                    myDonor = topDonors.get(i);
                    break;
                }
            }
            if (myDonor != null) {
                myDonor.stars += stars;
            } else {
                myDonor = new TL_phone.groupCallDonor();
                myDonor.my = true;
                myDonor.anonymous = false;
                myDonor.peer_id = MessagesController.getInstance(currentAccount).getPeer(send_as);
                myDonor.stars = stars;
                topDonors.add(myDonor);
            }
        }

        push(
            ConnectionsManager.getInstance(UserConfig.selectedAccount).getCurrentTime(),
            id,
            send_as == this.dialogId || isAdmin(),
            send_as,
            text,
            stars,
            false
        );

        setCollapsed(false, true);
        return id;
    }

    private void updateMessageId(int fromId, int toId) {
        for (Message msg : messages) {
            if (msg.id == fromId) {
                msg.id = toId;
                break;
            }
        }
    }

    public int getMessagesCount() {
        return messages.size();
    }

    public int getUnreadMessagesCount() {
        if (maxReadId < 0)
            return 0;
        final long minStars = livePlayer == null ? 0 : livePlayer.getSendPaidMessagesStars();
        int count = 0;
        for (int i = 0; i < messages.size(); ++i) {
            final Message msg = messages.get(i);
            final int id = msg.id;
            if (id >= 0 && id > maxReadId && !(!msg.fromAdmin && msg.isReaction && msg.stars < minStars)) {
                count++;
            }
        }
        return count;
    }

    public void updatedMinStars() {
        final long minStars = livePlayer == null ? 0 : livePlayer.getSendPaidMessagesStars();
        if (lastMinStars != minStars) {
            adapter.update(true);
        }
    }

    public long getStarsCount() {
        return totalStars + localStars;
    }

    public boolean didSendStars() {
        return sentStars || localStars > 0;
    }

    public boolean areSendingStars() {
        return starsBulletin != null;
    }

    protected void onMessagesCountUpdated() {

    }

    protected void onStarsButtonPressed(long sendingStars, boolean withEffects) {

    }

    protected void onStarsButtonCancelled() {

    }

    protected void onStarsCountUpdated() {

    }

    protected void onStarReaction(long dialogId, int totalStars, int stars) {

    }

    protected void onCancelledStarReaction(long dialogId) {

    }

    public void push(
        int date,
        int id,
        boolean fromAdmin,
        long dialogId,
        TLRPC.TL_textWithEntities text,
        long stars,
        boolean isHistory
    ) {
        for (int i = 0; i < messages.size(); ++i) {
            if (messages.get(i).id == id) {
                return;
            }
        }

        final int now = ConnectionsManager.getInstance(currentAccount).getCurrentTime();

        final Message message = new Message();
        message.date = date;
        message.fromAdmin = fromAdmin;
        message.dialogId = dialogId;
        message.text = text;
        message.stars = stars;
        message.id = id;
        message.isReaction = TextUtils.isEmpty(text.text);

        final int messagePeriod = getTierOption(currentAccount, (int) message.stars, TIER_PERIOD);
        if (message.stars > 0 && messagePeriod > 0 && now - message.date <= messagePeriod) {
            TopSender sender = null;
            for (int i = 0; i < topMessages.size(); ++i) {
                if (topMessages.get(i).dialogId == dialogId) {
                    sender = topMessages.get(i);
                    break;
                }
            }
            boolean addedTopMessage = false;
            if (sender == null) {
                sender = new TopSender();
                sender.currentAccount = currentAccount;
                sender.dialogId = dialogId;
                sender.messages.add(message);
                topMessages.add(0, sender);
                addedTopMessage = true;
            } else {
                sender.messages.add(message);
                topListView.invalidateViews();
            }
            sender.updateLastSentDate();
            updateTopMessages(true);
            scheduleRemovingTopSenders();

            lastNow = now;
            Collections.sort(topMessages, this::sortTopMessages);
            if (!isHistory) {
                topAdapter.update(true);
            }
            if (addedTopMessage) {
                topLayoutManager.scrollToPosition(0);
            }
        }

        if (!isHistory && message.isReaction && message.stars > 0) {
            totalStars += message.stars;
            onStarsCountUpdated();
        }

        int position = 0;
        if (message.id >= 0) {
            for (int i = messages.size() - 1; i >= 0; --i) {
                if (message.id < messages.get(i).id) {
                    position = i + 1;
                    break;
                }
            }
        }
        messages.add(position, message);
        if (!isHistory) {
            if (messages.size() > MAX_MESSAGES_COUNT) {
                messages.subList(MAX_MESSAGES_COUNT, messages.size()).clear();
            }
            adapter.update(true);
        }

        if (position <= 0 && !isHistory && (!listView.canScrollVertically(1) || message.id < 0)) {
            layoutManager.scrollToPositionWithOffset(0, dp(100));
            if (message.id > 0) {
                maxReadId = message.id;
            }
        }

        invalidate();
        onMessagesCountUpdated();

        if (!isHistory && id > 0 && message.stars > 0) {
            int totalStars = (int) message.stars;

            TL_phone.groupCallDonor donor = null;
            for (int i = 0; i < topDonors.size(); ++i) {
                if (DialogObject.getPeerDialogId(topDonors.get(i).peer_id) == message.dialogId) {
                    donor = topDonors.get(i);
                    break;
                }
            }
            if (donor == null) {
                donor = new TL_phone.groupCallDonor();
                donor.my = UserConfig.getInstance(currentAccount).getClientUserId() == message.dialogId;
                donor.peer_id = MessagesController.getInstance(currentAccount).getPeer(message.dialogId);
                donor.stars = 0;
                for (int i = 0; i < topMessages.size(); ++i) {
                    if (topMessages.get(i).dialogId == message.dialogId) {
                        topMessages.get(i).getStars();
                        donor.stars += topMessages.get(i).max_stars;
                    }
                }
                topDonors.add(donor);
            }
            donor.stars += message.stars;
            totalStars = (int) donor.stars;

            onStarReaction(message.dialogId, totalStars, (int) message.stars);
        }
        updateMessagesPlaces();

        if (isHistory) {
            AndroidUtilities.cancelRunOnUIThread(updateAdapters);
            AndroidUtilities.runOnUIThread(updateAdapters, 100);
        }

        saveHistory();
    }

    private final Runnable updateAdapters = () -> {
        LiveCommentsView.this.adapter.update(true);
        LiveCommentsView.this.topAdapter.update(true);
    };

    private void updateMessagesPlaces() {
        topPlaces.clear();
        final List<TL_phone.groupCallDonor> sorted = new ArrayList<>();
        if (topDonors != null) {
            sorted.addAll(topDonors);
        }
        Collections.sort(sorted, (a, b) -> (int) (b.stars - a.stars));

        int currentPlace = 0;
        int lastStars = Integer.MIN_VALUE;
        for (TL_phone.groupCallDonor sender : sorted) {
            int stars = (int) sender.stars;
            if (stars != lastStars) {
                currentPlace++;
                lastStars = stars;
            }
            if (currentPlace > 3) {
                break;
            }
            topPlaces.put(DialogObject.getPeerDialogId(sender.peer_id), currentPlace);
        }

        for (int i = 0; i < listView.getChildCount(); ++i) {
            final View child = listView.getChildAt(i);
            if (child instanceof LiveCommentView) {
                final LiveCommentView commentView = (LiveCommentView) child;
                if (commentView.message != null) {
                    final int place = getPlace(commentView.message.dialogId);
                    if (place != commentView.message.place) {
                        commentView.message.place = place;
                        commentView.set(commentView.message);
                    }
                }
            }
        }
        for (int i = 0; i < messages.size(); ++i) {
            final Message message = messages.get(i);
            final int place = getPlace(message.dialogId);
            if (place != message.place) {
                message.place = place;
            }
        }

        for (int i = 0; i < topListView.getChildCount(); ++i) {
            final View child = topListView.getChildAt(i);
            if (child instanceof LiveTopSenderView) {
                final LiveTopSenderView senderView = (LiveTopSenderView) child;
                if (senderView.sender != null) {
                    final int place = getPlace(senderView.sender.dialogId);
                    if (place != senderView.sender.place) {
                        senderView.sender.place = place;
                        senderView.set(senderView.sender);
                    }
                }
            }
        }
        for (int i = 0; i < topMessages.size(); ++i) {
            final TopSender sender = topMessages.get(i);
            final int place = getPlace(sender.dialogId);
            if (place != sender.place) {
                sender.place = place;
            }
        }
    }

    private int getPlace(long dialogId) {
        return topPlaces.getOrDefault(dialogId, 0);
    }

    public static class LiveCommentView extends FrameLayout implements ItemOptions.ScrimView {

        private boolean drawParticles = false;
        private boolean drawStar = true;
        private final int currentAccount;
        private final boolean filled;

        public Drawable background;
        public float backgroundViewAlpha = 0.5f;
        public final LinearLayout layout;

        public final LinearLayout textLayout;
        public final LinearLayout adminLayout;
        public final SpoilersTextView adminNameView;
        public final SpoilersTextView adminRoleView;

        public CharSequence text;
        public final BackupImageView avatarView;
        public final AvatarDrawable avatarDrawable;
        public final SpoilersTextView textView;
        public final TextView starsView;
        public final TextView smallStarsView;

        private final ColoredImageSpan[] starsViewCache = new ColoredImageSpan[1];
        private final ColoredImageSpan[] smallStarsViewCache = new ColoredImageSpan[1];

        public void setDrawStar(boolean drawStar) {
            this.drawStar = drawStar;
            if (starsViewCache[0] != null && starsViewCache[0].draw != drawStar) {
                starsViewCache[0].draw = drawStar;
                starsView.invalidate();
            }
        }

        public void getStarLocation(RectF out) {
            if (starsViewCache[0] == null) return;
            final Layout layout = starsView.getLayout();
            if (layout == null) return;
            final float x = starsView.getX() + starsView.getPaddingLeft() + starsViewCache[0].translateX;
            final float y = starsView.getY() + starsView.getPaddingTop() + starsViewCache[0].translateY;
            out.set(x, y, x + starsViewCache[0].drawable.getBounds().width(), y + starsViewCache[0].drawable.getBounds().height());
        }

        public LiveCommentView(Context context, int currentAccount, boolean filled) {
            super(context);
            this.currentAccount = currentAccount;
            this.filled = filled;

            layout = new LinearLayout(context) {
                StarsReactionsSheet.Particles particles;
                Path clipPath = new Path();

                @Override
                protected void dispatchDraw(@NonNull Canvas canvas) {
                    if (drawParticles) {
                        clipPath.rewind();
                        AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                        clipPath.addRoundRect(AndroidUtilities.rectTmp, dp(13), dp(13), Path.Direction.CW);
                        canvas.save();
                        canvas.clipPath(clipPath);

                        if (particles == null) {
                            particles = new StarsReactionsSheet.Particles(StarsReactionsSheet.Particles.TYPE_RADIAL, 250);
                        }
                        particles.setBounds(0, 0, getWidth(), getHeight());
                        particles.setSpeed(30.0f);
                        particles.process();
                        particles.draw(canvas, 0xFFFFFFFF, 0.85f);
                        invalidate();

                        canvas.restore();
                    }
                    super.dispatchDraw(canvas);
                }
            };
            layout.setOrientation(LinearLayout.HORIZONTAL);
            addView(layout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 0.5f, 0, 0.5f));

            avatarDrawable = new AvatarDrawable();
            avatarView = new BackupImageView(context);
            avatarView.setRoundRadius(dp(11));
            layout.addView(avatarView, LayoutHelper.createLinear(22, 22, 0, Gravity.LEFT | Gravity.TOP, 3, 2, 3, 2));

            textLayout = new LinearLayout(context);
            textLayout.setOrientation(LinearLayout.VERTICAL);
            layout.addView(textLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 1, Gravity.LEFT | Gravity.TOP, 4, 3, 7, 3));

            adminLayout = new LinearLayout(context);
            adminLayout.setOrientation(LinearLayout.HORIZONTAL);
            adminLayout.setVisibility(View.GONE);
            textLayout.addView(adminLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            adminNameView = new SpoilersTextView(context);
            adminNameView.setTextColor(0xFFFFFFFF);
            adminNameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            adminNameView.setGravity(Gravity.LEFT);
            adminNameView.setTypeface(AndroidUtilities.bold());
            adminLayout.addView(adminNameView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 1, Gravity.LEFT | Gravity.TOP, 0, 0, 16, 0));

            adminRoleView = new SpoilersTextView(context);
            adminRoleView.setTextColor(Theme.multAlpha(0xFFFFFFFF, 0.55f));
            adminRoleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            adminRoleView.setGravity(Gravity.RIGHT);
            adminLayout.addView(adminRoleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.RIGHT | Gravity.TOP, 0, 0, 0, 0));

            textView = new SpoilersTextView(context);
            textView.setTextColor(0xFFFFFFFF);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setShadowLayer(dp(2.5f), 0, dp(1.5f), Theme.multAlpha(0xFF000000, 0.6f));
            NotificationCenter.listenEmojiLoading(textView);
            textLayout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            starsView = new TextView(context);
            starsView.setTextColor(0xFFFFFFFF);
            starsView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            starsView.setPadding(dp(4.66f), 0, dp(4.66f), 0);
            starsView.setVisibility(View.GONE);
            layout.addView(starsView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 16, 0, Gravity.RIGHT | Gravity.CENTER_VERTICAL, -3, 0, 6, 0));

            smallStarsView = new TextView(context);
            smallStarsView.setTextColor(0xFFFFFFFF);
            smallStarsView.setAlpha(0.65f);
            smallStarsView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            smallStarsView.setVisibility(View.GONE);
            layout.addView(smallStarsView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, Gravity.RIGHT | Gravity.BOTTOM, 0, 3, 10, 0));
        }

        private int highlightingMessageId;
        private ValueAnimator highlightAnimator;
        public void highlight() {
            if (highlightAnimator != null) {
                highlightAnimator.cancel();
                highlightAnimator = null;
                if (background != null) {
                    background.setAlpha((int) (0xFF * backgroundViewAlpha));
                    layout.invalidate();
                }
            }
            if (message == null || background == null) return;

            highlightingMessageId = message.id;

            highlightAnimator = ValueAnimator.ofFloat(0, 1);
            highlightAnimator.addUpdateListener(a -> {
                final float t = (float) a.getAnimatedValue();
                if (background != null) {
                    background.setAlpha((int) (0xFF * lerp(backgroundViewAlpha, 1.0f, t)));
                    layout.invalidate();
                }
            });
            highlightAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (message == null || highlightingMessageId != message.id) return;
                    highlightAnimator = ValueAnimator.ofFloat(0, 1);
                    highlightAnimator.addUpdateListener(a -> {
                        final float t = (float) a.getAnimatedValue();
                        if (background != null) {
                            background.setAlpha((int) (0xFF * lerp(1.0f, backgroundViewAlpha, t)));
                            layout.invalidate();
                        }
                    });
                    highlightAnimator.setStartDelay(3000);
                    highlightAnimator.setDuration(550);
                    highlightAnimator.setInterpolator(new LinearInterpolator());
                    highlightAnimator.start();
                }
            });
            highlightAnimator.setDuration(350);
            highlightAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            highlightAnimator.start();
        }

        private Message message;

        public void set(Message message) {
            this.message = message;

            if ((message == null || highlightingMessageId != message.id) && highlightAnimator != null) {
                highlightAnimator.cancel();
                highlightAnimator = null;
                if (background != null) {
                    background.setAlpha((int) (0xFF * backgroundViewAlpha));
                    layout.invalidate();
                }
            }

            final String title;
            int backgroundColor1, backgroundColor2;
            if (message.dialogId >= 0) {
                final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(message.dialogId);
                avatarDrawable.setInfo(user);
                avatarView.setForUserOrChat(user, avatarDrawable);
                title = UserObject.getForcedFirstName(user);
            } else {
                final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-message.dialogId);
                avatarDrawable.setInfo(chat);
                avatarView.setForUserOrChat(chat, avatarDrawable);
                title = chat == null ? "" : chat.title;
            }
            backgroundColor1 = getTierOption(currentAccount, (int) message.stars, TIER_COLOR1);
            backgroundColor2 = getTierOption(currentAccount, (int) message.stars, TIER_COLOR2);
            final int darkerBackgroundColor = getTierOption(currentAccount, (int) message.stars, TIER_COLOR_BACKGROUND);

            final SpannableStringBuilder sb = new SpannableStringBuilder();
            if (!message.fromAdmin || message.stars > 0) {
                if (message.place > 0) {
                    sb.append("#" + message.place);
                    final ColoredImageSpan span = new ColoredImageSpan(new CrownDrawable(getContext(), message.place));
                    span.setTranslateY(dp(1));
                    sb.setSpan(span, 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.append(" ");
                }
                sb.append(TextUtils.ellipsize(title, textView.getPaint(), dp(100), TextUtils.TruncateAt.END));
                if (filled) {
                    sb.setSpan(new AlphaSpan(0.75f), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                sb.setSpan(new TypefaceSpan(AndroidUtilities.bold()), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.append(" ");
            }

            final int maxLength = getTierOption(currentAccount, (int) message.stars, TIER_LENGTH);
            final int maxEmojis = getTierOption(currentAccount, (int) message.stars, TIER_EMOJIS);

            if (message.text != null) {
                text = MessageObject.formatTextWithEntities(message.text, false, textView.getPaint());
                text = AndroidUtilities.superTrim(text);

                if (text.length() > maxLength && !message.fromAdmin) {
                    text = text.subSequence(0, maxLength);
                }
                if (text instanceof Spannable) {
                    final Spannable spannable = (Spannable) text;
                    final AnimatedEmojiSpan[] animatedEmojis = spannable.getSpans(0, text.length(), AnimatedEmojiSpan.class);
                    final Emoji.EmojiSpan[] emojis = spannable.getSpans(0, text.length(), Emoji.EmojiSpan.class);

                    if (animatedEmojis.length + emojis.length > maxEmojis && !message.fromAdmin) {
                        ArrayList<Pair<Integer, Integer>> spans = new ArrayList<>();
                        for (int i = 0; i < animatedEmojis.length; ++i) {
                            spans.add(new Pair<>(spannable.getSpanStart(animatedEmojis[i]), spannable.getSpanEnd(animatedEmojis[i])));
                        }
                        for (int i = 0; i < emojis.length; ++i) {
                            spans.add(new Pair<>(spannable.getSpanStart(emojis[i]), spannable.getSpanEnd(emojis[i])));
                        }
                        Collections.sort(spans, (a, b) -> a.first - b.first);
                        if (!(text instanceof SpannableStringBuilder)) {
                            text = new SpannableStringBuilder(text);
                        }
                        for (int i = spans.size() - 1; i >= maxEmojis; --i) {
                            final Pair<Integer, Integer> spanRange = spans.get(i);
                            ((SpannableStringBuilder) text).replace(spanRange.first, spanRange.second, "");
                        }
                    }
                }
                if (!message.fromAdmin) {
                    text = AndroidUtilities.replaceNewLines(text);
                }
                sb.append(text);
            } else {
                text = "";
            }
            textView.setText(Emoji.replaceEmoji(sb, textView.getPaint().getFontMetricsInt(), false));

            background = null;
            adminLayout.setVisibility(message.fromAdmin && message.stars <= 0 ? View.VISIBLE : View.GONE);
            if (message.stars > 0) {
                layout.setWillNotDraw(!(drawParticles = message.stars >= 250));
                layout.invalidate();

                textView.setShadowLayer(0, 0, 0, 0);
                layout.setBackground(background = Theme.createRoundRectGradientDrawable(dp(13), backgroundColor1, backgroundColor2));
                background.setAlpha((int) (0xFF * (backgroundViewAlpha = !filled ? 0.65f : 1.0f)));
                if (!message.isReaction) {
                    smallStarsView.setVisibility(View.VISIBLE);
                    smallStarsView.setText(StarsIntroActivity.replaceStars("⭐️ " + LocaleController.formatNumber(message.stars, ','), 0.75f, smallStarsViewCache, 0, 0, 1.0f));
                    starsView.setVisibility(View.GONE);
                    starsView.setText("");
                } else {
                    smallStarsView.setVisibility(View.GONE);
                    smallStarsView.setText("");
                    starsView.setVisibility(View.VISIBLE);
                    starsView.setBackground(Theme.createRoundRectDrawable(dp(13), Theme.multAlpha(darkerBackgroundColor, 0.25f)));
                    starsView.setText(StarsIntroActivity.replaceStars("⭐️ " + LocaleController.formatNumber(message.stars, ','), 0.75f, starsViewCache, 0, dp(0.66f), 1.0f));
                    if (starsViewCache[0] != null) {
                        starsViewCache[0].draw = drawStar;
                    }
                }
            } else if (message.fromAdmin) {
                layout.setWillNotDraw(!(drawParticles = false));
                layout.setBackground(background = Theme.createRoundRectDrawable(dp(13), Color.BLACK));
                background.setAlpha((int) (0xFF * (backgroundViewAlpha = 0.5f)));
                textView.setShadowLayer(0, 0, 0, 0);

                final SpannableStringBuilder name = new SpannableStringBuilder();
                name.append(DialogObject.getName(currentAccount, message.dialogId));
                name.append(" ");
                final int index = name.length();
                name.append(getString(R.string.LiveStoryBadge));
                name.setSpan(new ReplacementSpan() {
                    private final RectF rect = new RectF();
                    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
                    private final Text text = new Text(getString(R.string.LiveStoryBadge), 8, AndroidUtilities.bold());
                    @Override
                    public int getSize(@NonNull Paint paint, CharSequence charSequence, int i, int i1, @Nullable Paint.FontMetricsInt fontMetricsInt) {
                        return (int) (text.getWidth() + dp(8));
                    }
                    @Override
                    public void draw(@NonNull Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, @NonNull Paint paint) {
                        final float cy = (top + bottom) / 2f + dp(0);
                        rect.set(x, cy - dp(6), x + this.text.getWidth() + dp(8), cy + dp(6));
                        bg.setColor(0xFFF7424E);
                        canvas.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, bg);
                        this.text.draw(canvas, dp(4) + x, cy, 0xFFFFFFFF, 1.0f);
                    }
                }, index, name.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                adminNameView.setText(name);
                adminRoleView.setText(getString(R.string.LiveStoryAdminRole));

                smallStarsView.setVisibility(View.GONE);
                starsView.setVisibility(View.GONE);
            } else {
                layout.setWillNotDraw(!(drawParticles = false));
                textView.setShadowLayer(dp(2.5f), 0, dp(1.5f), Theme.multAlpha(0xFF000000, 0.6f));
                layout.setBackground(background = null);
                smallStarsView.setVisibility(View.GONE);
                starsView.setVisibility(View.GONE);
            }
            layout.invalidate();
        }

        public static final class AlphaSpan extends CharacterStyle {
            private final float alpha;
            public AlphaSpan(float alpha) {
                this.alpha = alpha;
            }
            @Override
            public void updateDrawState(TextPaint textPaint) {
                textPaint.setAlpha((int) (alpha * textPaint.getAlpha()));
            }
        }

        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override
        public void drawScrim(Canvas canvas, float progress) {
            if (layout.getBackground() == null) {
                backgroundPaint.setColor(Theme.multAlpha(0xFF000000, 0.50f * progress));
                AndroidUtilities.rectTmp.set(layout.getX(), layout.getY(), layout.getX() + layout.getWidth(), layout.getY() + layout.getHeight());
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(13), dp(13), backgroundPaint);
            }
            this.draw(canvas);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            setPivotX(0);
            setPivotY(getMeasuredHeight());
        }

        public static class Factory extends UItem.UItemFactory<LiveCommentView> {
            static { setup(new Factory()); }

            @Override
            public LiveCommentView createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                LiveCommentView view = new LiveCommentView(context, currentAccount, false);
                view.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return view;
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((LiveCommentView) view).set((Message) item.object);
            }

            public static UItem of(Message message) {
                final UItem item = UItem.ofFactory(Factory.class);
                item.object = message;
                return item;
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a.object == b.object;
            }
        }
    }

    public static class LiveTopSenderView extends FrameLayout {

        public final LinearLayout layout;

        public final BackupImageView avatarView;
        public final AvatarDrawable avatarDrawable;
        public final ImageView crownView;
        public final TextView textView;

        public LiveTopSenderView(Context context) {
            super(context);
            ScaleStateListAnimator.apply(this);

            layout = new LinearLayout(context) {
                StarsReactionsSheet.Particles particles;
                final Path clipPath = new Path();
                final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                long lastDialogId = 0;
                final AnimatedFloat animatedProgress = new AnimatedFloat(this, 0, 1000, new LinearInterpolator());

                @Override
                protected void dispatchDraw(@NonNull Canvas canvas) {
                    clipPath.rewind();
                    AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                    clipPath.addRoundRect(AndroidUtilities.rectTmp, dp(13), dp(13), Path.Direction.CW);
                    canvas.save();
                    canvas.clipPath(clipPath);

                    if (sender != null) {
                        final int color = getTierOption(sender.currentAccount, sender.getStars(), TIER_COLOR1);
                        final int backgroundColor = getTierOption(sender.currentAccount, sender.getStars(), TIER_COLOR_BACKGROUND);
                        canvas.drawColor(color);

                        if (lastDialogId != sender.dialogId) {
                            animatedProgress.force(sender.getProgress());
                        }
                        final float progress = animatedProgress.set(sender.getProgress());
                        lastDialogId = sender.dialogId;
                        fillPaint.setColor(backgroundColor);
                        fillPaint.setAlpha((int) (0xFF * 0.50f));
                        canvas.drawRect(getWidth() * progress, 0, getWidth(), getHeight(), fillPaint);
                    }

                    if (particles == null) {
                        particles = new StarsReactionsSheet.Particles(StarsReactionsSheet.Particles.TYPE_RADIAL, 250);
                    }
                    particles.setBounds(0, 0, getWidth(), getHeight());
                    particles.setSpeed(30.0f);
                    particles.process();
                    particles.draw(canvas, 0xFFFFFFFF, 0.85f);
                    invalidate();

                    canvas.restore();

                    super.dispatchDraw(canvas);
                }
            };
            layout.setOrientation(LinearLayout.HORIZONTAL);
            addView(layout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL, 0, 0, 6, 0));

            avatarDrawable = new AvatarDrawable();
            avatarView = new BackupImageView(context);
            avatarView.setRoundRadius(dp(11));
            layout.addView(avatarView, LayoutHelper.createLinear(22, 22, 0, Gravity.LEFT | Gravity.TOP, 3, 2, 7, 2));

            crownView = new ImageView(context);
            crownView.setVisibility(View.GONE);
            layout.addView(crownView, LayoutHelper.createLinear(18, 18, Gravity.CENTER_VERTICAL | Gravity.LEFT, 0, 0, 3, 0));

            textView = new TextView(context) {
                @Override
                protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                    super.onMeasure(MeasureSpec.makeMeasureSpec(dp(100), MeasureSpec.AT_MOST), heightMeasureSpec);
//                    setMeasuredDimension(
//                        Math.min(MeasureSpec.getSize(widthMeasureSpec), dp(200)),
//                        MeasureSpec.getSize(heightMeasureSpec)
//                    );
                }

                private final GradientClip clip = new GradientClip();
                @Override
                protected void onDraw(@NonNull Canvas canvas) {
                    canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), 0xFF, Canvas.ALL_SAVE_FLAG);
                    super.onDraw(canvas);
                    canvas.save();
                    AndroidUtilities.rectTmp.set(getWidth() - dp(15), 0, getWidth(), getHeight());
                    clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.RIGHT, 1.0f);
                    canvas.restore();
                    canvas.restore();
                }
            };
            textView.setLines(1);
            textView.setSingleLine();
            textView.setTextColor(0xFFFFFFFF);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTypeface(AndroidUtilities.bold());
            layout.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 0, 0, 7, 0));
        }

        private TopSender sender;
        public void set(TopSender sender) {
            this.sender = sender;
            if (sender.dialogId >= 0) {
                final TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(sender.dialogId);
                avatarDrawable.setInfo(user);
                avatarView.setForUserOrChat(user, avatarDrawable);
            } else {
                final TLRPC.Chat chat = MessagesController.getInstance(UserConfig.selectedAccount).getChat(-sender.dialogId);
                avatarDrawable.setInfo(chat);
                avatarView.setForUserOrChat(chat, avatarDrawable);
            }
            if (sender.place > 0) {
                crownView.setImageDrawable(new CrownDrawable(getContext(), sender.place));
                crownView.setVisibility(View.VISIBLE);
            } else {
                crownView.setVisibility(View.GONE);
            }
            textView.setText(DialogObject.getName(sender.dialogId));
            layout.invalidate();
        }

        public static class Factory extends UItem.UItemFactory<LiveTopSenderView> {
            static { setup(new Factory()); }

            @Override
            public LiveTopSenderView createView(Context context, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
                return new LiveTopSenderView(context);
            }

            @Override
            public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
                ((LiveTopSenderView) view).set((TopSender) item.object);
            }

            public static UItem of(TopSender sender) {
                final UItem item = UItem.ofFactory(Factory.class);
                item.object = sender;
                return item;
            }

            @Override
            public boolean equals(UItem a, UItem b) {
                return a.object == b.object;
            }
        }
    }

    public static class CrownDrawable extends Drawable {

        private final float scale;
        private final Drawable crown;
        private final Text text;

        public CrownDrawable(Context context, int place) {
            scale = 0.75f;
            crown = context.getResources().getDrawable(R.drawable.filled_stream_crown).mutate();
            text = new Text("" + place, 8, AndroidUtilities.getTypeface("fonts/num.otf"));
            text.paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            final Rect bounds = getBounds();
            canvas.saveLayerAlpha(bounds.left, bounds.top, bounds.right, bounds.bottom, 0xFF, Canvas.ALL_SAVE_FLAG);
            crown.setBounds(bounds);
            crown.draw(canvas);
            text.draw(canvas, bounds.centerX() - text.getCurrentWidth() / 2.0f, bounds.centerY() + dp(0.15f), 0xFFFFFFFF, (crown.getAlpha() / 255.0f));
            canvas.restore();
        }

        @Override
        public int getIntrinsicWidth() {
            return (int) (crown.getIntrinsicWidth() * scale);
        }

        @Override
        public int getIntrinsicHeight() {
            return (int) (crown.getIntrinsicHeight() * scale);
        }

        @Override
        public int getAlpha() {
            return crown.getAlpha();
        }

        @Override
        public void setAlpha(int i) {
            crown.setAlpha(i);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            crown.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSPARENT;
        }
    }

    public static void openDeleteMessage(Context context, long dialogId, Utilities.Callback3<Boolean, Boolean, Boolean> done) {
        final Theme.ResourcesProvider resourcesProvider = new DarkThemeResourceProvider() {
            @Override
            public void appendColors() {
                sparseIntArray.append(Theme.key_dialogBackground, 0xFF202020);
            }
        };
        final BottomSheet sheet = new BottomSheet(context, false, resourcesProvider);
        sheet.fixNavigationBar();

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        final TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        title.setTypeface(AndroidUtilities.bold());
        title.setText(getString(R.string.DeleteSingleMessagesTitle));
        layout.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 22, 12, 22, 0));

        HeaderCell header = new HeaderCell(context, resourcesProvider);
        header.setText(getString(R.string.DeleteAdditionalActions));
        layout.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        final CheckBoxCell check1 = new CheckBoxCell(context, CheckBoxCell.TYPE_CHECK_BOX_ROUND, 21, true, resourcesProvider);
        check1.getCheckBoxRound().setColor(Theme.key_switch2TrackChecked, Theme.key_radioBackground, Theme.key_checkboxCheck);
        check1.setText(getString(R.string.DeleteReportSpam), null, false, true);
        check1.setOnClickListener(v -> {
            check1.setChecked(!check1.isChecked(), true);
        });
        check1.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), Theme.RIPPLE_MASK_ALL));
        layout.addView(check1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final CheckBoxCell check2 = new CheckBoxCell(context, CheckBoxCell.TYPE_CHECK_BOX_ROUND, 21, true, resourcesProvider);
        check2.getCheckBoxRound().setColor(Theme.key_switch2TrackChecked, Theme.key_radioBackground, Theme.key_checkboxCheck);
        check2.setText(formatString(R.string.DeleteAllFrom, DialogObject.getName(dialogId)), null, false, true);
        check2.setOnClickListener(v -> {
            check2.setChecked(!check2.isChecked(), true);
        });
        check2.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), Theme.RIPPLE_MASK_ALL));
        layout.addView(check2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final CheckBoxCell check3 = new CheckBoxCell(context, CheckBoxCell.TYPE_CHECK_BOX_ROUND, 21, true, resourcesProvider);
        check3.getCheckBoxRound().setColor(Theme.key_switch2TrackChecked, Theme.key_radioBackground, Theme.key_checkboxCheck);
        check3.setText(formatString(R.string.DeleteBan, DialogObject.getName(dialogId)), null, false, false);
        check3.setOnClickListener(v -> {
            check3.setChecked(!check3.isChecked(), true);
        });
        check3.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), Theme.RIPPLE_MASK_ALL));
        layout.addView(check3, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final TextInfoPrivacyCell infoView = new TextInfoPrivacyCell(context, resourcesProvider);
        infoView.setBackgroundColor(0xFF000000);
        infoView.setFixedSize(12);
        layout.addView(infoView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final FrameLayout buttonContainer = new FrameLayout(context);
        final ButtonWithCounterView button = new ButtonWithCounterView(context, resourcesProvider);
        button.setText(getString(R.string.DeleteProceedBtn), false);
        button.setOnClickListener(v -> {
            final boolean report = check1.isChecked();
            final boolean deleteAll = check2.isChecked();
            final boolean ban = check3.isChecked();

            done.run(report, deleteAll, ban);
            sheet.dismiss();
        });
        buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL, 16, 16, 16, 16));
        layout.addView(buttonContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        sheet.setCustomView(layout);
        sheet.show();
    }

}
