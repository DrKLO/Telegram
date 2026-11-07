package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DiffUtil;
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
import org.telegram.messenger.SavedMessagesController;
import org.telegram.messenger.TopicsController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.Forum.ForumUtilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/**
 * The topics as a panel that slides in from the right, over the chat it pushes aside: the sidebar, apart from the
 * topics tabs, opened from the action bar or by a swipe. In saved messages it has the saved chats.
 */
public class TopicsSidePanel extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    public static final int PANEL_WIDTH = 288;
    private static final int PANEL_RADIUS = 24;

    public interface Delegate {
        /** 0 for all messages; a topic id, or the peer's dialog id in a monoforum or saved messages. */
        void onTopicSelected(long topicId);
        /** The button over the chat, in a bot forum: back from the sidebar to the tabs. */
        void onProgressChanged(float progress);
        void onNewTopic();
        /** The avatar and the title: the profile of whose topics they are. */
        void onOpenProfile();
        /** The chat's own menu, from the button at the right of the title. */
        void onOpenMenu(View button);
        /** The topic's menu, as on its tab. */
        default boolean onTopicLongClick(TLRPC.TL_forumTopic topic, View row) { return false; }
        /** Drawn over the dim, under the panel: the sidebar's button of the action bar stays seen. */
        default void drawOverDim(Canvas canvas) {}
        /** The room at the right of the screen the sidebar's button needs when pushed with the chat: the panel leaves it. */
        default int getButtonReserve() { return 0; }
    }

    private final int currentAccount;
    private final long dialogId;
    private final boolean mono;
    private final boolean saved;
    private final Theme.ResourcesProvider resourcesProvider;
    private final Delegate delegate;

    private final FrameLayout panel;
    private ImageView menuButton;
    private final RecyclerListView listView;
    private final Adapter adapter;
    private final EditText searchField;
    private final Paint dimPaint = new Paint();

    private float progress;
    private View newTopicIsland, topFade, bottomFade;
    private int bottomInset = -1;
    private int rightInset;
    private int contentWidth = dp(PANEL_WIDTH);
    private ValueAnimator animator;
    private long currentTopicId;
    private String query = "";
    // the server's matches for the query, under the local ones (not in saved messages or direct messages: no search there)
    private final ArrayList<TLRPC.TL_forumTopic> serverResults = new ArrayList<>();
    private int serverSearchReqId;
    private Runnable serverSearch;

    /** With the switch, a button over the chat at the left goes back to the tabs. */
    public TopicsSidePanel(Context context, int currentAccount, long dialogId, Theme.ResourcesProvider resourcesProvider, Delegate delegate) {
        super(context);
        this.currentAccount = currentAccount;
        this.dialogId = dialogId;
        this.resourcesProvider = resourcesProvider;
        this.delegate = delegate;
        final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        this.mono = ChatObject.isMonoForum(chat);
        this.saved = dialogId == UserConfig.getInstance(currentAccount).getClientUserId();
        setWillNotDraw(false);

        panel = new FrameLayout(context) {
            @Override
            protected void dispatchDraw(@NonNull Canvas canvas) {
                super.dispatchDraw(canvas);
            }
        };
        panel.setElevation(dp(9));
        // rounded on the side it slides in from
        panel.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth() + dp(PANEL_RADIUS), view.getHeight(), dp(PANEL_RADIUS));
            }
        });
        panel.setClipToOutline(true);
        panel.setOnTouchListener((v, e) -> true);
        addView(panel, LayoutHelper.createFrame(PANEL_WIDTH, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.TOP));

        final int top = AndroidUtilities.statusBarHeight;

        // header: who the topics belong to, and what they are
        final MessagesController controller = MessagesController.getInstance(currentAccount);
        final BackupImageView avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(dp(20));
        final AvatarDrawable avatarDrawable = new AvatarDrawable();
        CharSequence title = "";
        CharSequence subtitle;
        if (saved) {
            avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
            // the icons keep their own size: scaled to the avatar, as in the chat's header (ChatAvatarContainer)
            avatarDrawable.setScaleSize(.75f);
            avatarView.setImageDrawable(avatarDrawable);
            title = getString(R.string.SavedMessages);
            final int count = controller.getSavedMessagesController().allDialogs.size();
            subtitle = LocaleController.formatPluralString("Chats", count);
        } else if (mono) {
            // a channel's direct messages: the channel itself
            final TLRPC.Chat channel = chat != null ? controller.getChat(chat.linked_monoforum_id) : null;
            final TLRPC.Chat shown = channel != null ? channel : chat;
            if (shown != null) {
                avatarDrawable.setInfo(currentAccount, shown);
                avatarView.setForUserOrChat(shown, avatarDrawable);
                title = shown.title;
            }
            subtitle = getString(R.string.TopicsPanelDirectMessages);
        } else if (chat != null) {
            avatarDrawable.setInfo(currentAccount, chat);
            avatarView.setForUserOrChat(chat, avatarDrawable);
            title = chat.title;
            final TLRPC.ChatFull chatFull = controller.getChatFull(chat.id);
            final int members = chatFull != null && chatFull.participants_count > 0 ? chatFull.participants_count : chat.participants_count;
            subtitle = members > 0 ? LocaleController.formatPluralStringComma("Members", members) : getString(R.string.Topics);
        } else {
            final TLRPC.User user = controller.getUser(dialogId);
            if (user != null) {
                avatarDrawable.setInfo(currentAccount, user);
                avatarView.setForUserOrChat(user, avatarDrawable);
                title = UserObject.getUserName(user);
            }
            subtitle = getString(R.string.Topics);
        }
        // the avatar, the title and the subtitle open the profile; the button at the right opens the chat's menu
        final FrameLayout headerView = new FrameLayout(context) {
            private final ButtonBounce bounce = new ButtonBounce(this);
            @Override
            public void setPressed(boolean pressed) {
                super.setPressed(pressed);
                bounce.setPressed(pressed);
            }
            @Override
            public void draw(@NonNull Canvas canvas) {
                final float scale = bounce.getScale(.03f);
                canvas.save();
                canvas.scale(scale, scale, getWidth() / 2f, getHeight() / 2f);
                super.draw(canvas);
                canvas.restore();
            }
        };
        headerView.setBackground(Theme.createRadSelectorDrawable(Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), .06f), 24, 24));
        headerView.setOnClickListener(v -> {
            if (delegate != null) {
                delegate.onOpenProfile();
            }
        });
        panel.addView(headerView, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.TOP, dp(6), top - dp(1), dp(52), 0));
        headerView.addView(avatarView, LayoutHelper.createFrameMarginPx(40, 40, Gravity.LEFT | Gravity.TOP, dp(6), dp(4), 0, 0));

        final ImageView menuButton = new ImageView(context);
        menuButton.setScaleType(ImageView.ScaleType.CENTER);
        menuButton.setImageResource(R.drawable.ic_ab_other);
        menuButton.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), PorterDuff.Mode.SRC_IN));
        menuButton.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), .06f), Theme.RIPPLE_MASK_CIRCLE_20DP));
        menuButton.setContentDescription(getString(R.string.AccDescrMoreOptions));
        menuButton.setOnClickListener(v -> {
            if (delegate != null) {
                delegate.onOpenMenu(v);
            }
        });
        panel.addView(menuButton, LayoutHelper.createFrameMarginPx(40, 40, Gravity.RIGHT | Gravity.TOP, 0, top + dp(3), dp(8), 0));
        this.menuButton = menuButton;

        final TextView titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setSingleLine();
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setText(title);
        titleView.setIncludeFontPadding(false);
        headerView.addView(titleView, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, dp(58), dp(7), dp(6), 0));

        final TextView subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setSingleLine();
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitleView.setText(subtitle);
        subtitleView.setIncludeFontPadding(false);
        headerView.addView(subtitleView, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, dp(58), dp(26), dp(6), 0));

        // search field
        // a raised surface pill: the view is 4dp larger on each side so its shadow isn't clipped
        final FrameLayout searchContainer = new FrameLayout(context);
        final int shadowPad = dp(4);
        searchContainer.setPadding(shadowPad, shadowPad, shadowPad, shadowPad);
        searchContainer.setBackground(new Drawable() {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF rect = new RectF();
            @Override
            public void draw(@NonNull Canvas canvas) {
                rect.set(getBounds());
                rect.inset(shadowPad, shadowPad);
                final float r = rect.height() / 2f;
                paint.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
                paint.setShadowLayer(dp(1.5f), 0, dp(.5f), 0x1A000000);
                canvas.drawRoundRect(rect, r, r, paint);
                paint.setShadowLayer(dp(.5f), 0, 0, 0x1F000000);
                canvas.drawRoundRect(rect, r, r, paint);
            }
            @Override public void setAlpha(int alpha) {}
            @Override public void setColorFilter(ColorFilter colorFilter) {}
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        });
        final ImageView searchIcon = new ImageView(context);
        searchIcon.setScaleType(ImageView.ScaleType.CENTER);
        searchIcon.setImageResource(R.drawable.outline_header_search);
        searchIcon.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_windowBackgroundWhiteGrayText), PorterDuff.Mode.SRC_IN));
        searchContainer.addView(searchIcon, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 12, 0, 0, 0));
        searchField = new EditText(context);
        searchField.setBackground(null);
        searchField.setPadding(0, 0, 0, 0);
        searchField.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        searchField.setSingleLine();
        searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        searchField.setHint(getString(R.string.Search));
        searchField.setHintTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        searchField.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase();
                if (query.startsWith("@")) query = query.substring(1);
                scheduleServerSearch();
                update();
            }
        });
        searchContainer.addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 48, 0, 12, 0));
        panel.addView(searchContainer, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, 48, Gravity.LEFT | Gravity.TOP, dp(12) - shadowPad, top + dp(56) - shadowPad, dp(8) - shadowPad, 0));

        listView = new RecyclerListView(context, resourcesProvider);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setPadding(0, dp(4), 0, dp(8));
        listView.setClipToPadding(false);
        listView.setVerticalScrollBarEnabled(false);
        // rows move when the order changes (pinning); content changes rebind in place
        final DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setSupportsChangeAnimations(false);
        itemAnimator.setDelayAnimations(false);
        itemAnimator.setMoveInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        itemAnimator.setDurations(350);
        listView.setItemAnimator(itemAnimator);
        // the list's selector is the rows' only ripple, rounded like their highlight
        listView.setSelectorRadius(dp(16));
        listView.setSelectorDrawableColor(Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), .06f));
        listView.setAdapter(adapter = new Adapter());
        listView.setOnItemClickListener((view, position) -> {
            final Item item = adapter.items.get(position);
            if (delegate != null) {
                delegate.onTopicSelected(item.getId());
            }
            // an opened result ends the search: the full list is back next time
            if (searchField.length() > 0) {
                searchField.setText("");
            }
            searchField.clearFocus();
            close();
        });
        listView.setOnItemLongClickListener((view, position) -> {
            final Item item = adapter.items.get(position);
            return item.topic != null && delegate != null && delegate.onTopicLongClick(item.topic, view);
        });
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING && searchField.hasFocus()) {
                    AndroidUtilities.hideKeyboard(searchField);
                    searchField.clearFocus();
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                updateFades();
                final LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm != null && lm.findLastVisibleItemPosition() >= adapter.getItemCount() - 3) {
                    if (saved) {
                        MessagesController.getInstance(currentAccount).getSavedMessagesController().loadDialogs(false);
                        return;
                    }
                    final TopicsController controller = MessagesController.getInstance(currentAccount).getTopicsController();
                    if (!controller.endIsReached(-dialogId)) {
                        controller.loadTopics(-dialogId);
                    }
                }
            }
        });
        panel.addView(listView, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, dp(8), top + dp(100), dp(8), 0));

        // the list fades out at its edges
        final int background = getThemedColor(Theme.key_windowBackgroundWhite);
        final View topFade = new View(context);
        topFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] { background, ColorUtils.setAlphaComponent(background, 0) }));
        panel.addView(topFade, LayoutHelper.createFrameMarginPx(LayoutHelper.MATCH_PARENT, dp(16), Gravity.LEFT | Gravity.TOP, 0, top + dp(100), 0, 0));
        bottomFade = new View(context);
        bottomFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[] { background, ColorUtils.setAlphaComponent(background, 0) }));
        panel.addView(bottomFade, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 24, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
        this.topFade = topFade;
        updateFades();

        // new topic: a glass island over the bottom of the list
        final TLRPC.User user = dialogId > 0 ? MessagesController.getInstance(currentAccount).getUser(dialogId) : null;
        final boolean canCreateTopic = !mono && !saved && (chat != null && ChatObject.canCreateTopic(chat) || UserObject.isBotForumWithEditableTopics(user));
        if (canCreateTopic) {

            final FrameLayout island = new FrameLayout(context);
            final GradientDrawable islandBackground = new GradientDrawable();
            islandBackground.setCornerRadius(dp(25));
            islandBackground.setColor(ColorUtils.setAlphaComponent(background, 0xE0));
            island.setBackground(islandBackground);
            island.setElevation(dp(2));
            island.setPadding(dp(3), dp(3), dp(3), dp(3));

            final TextView button = new TextView(context);
            button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            button.setTypeface(AndroidUtilities.bold());
            button.setTextColor(getThemedColor(Theme.key_featuredStickers_buttonText));
            button.setText(getString(R.string.NewTopic));
            button.setGravity(Gravity.CENTER);
            button.setPadding(dp(20), 0, dp(22), 0);
            button.setCompoundDrawablePadding(dp(8));
            final Drawable icon = ContextCompat.getDrawable(context, R.drawable.filled_topic_new_24).mutate();
            icon.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_featuredStickers_buttonText), PorterDuff.Mode.SRC_IN));
            button.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
            button.setBackground(Theme.createRoundRectDrawable(dp(22), getThemedColor(Theme.key_featuredStickers_addButton)));
            ScaleStateListAnimator.apply(button);
            button.setOnClickListener(v -> {
                close();
                if (delegate != null) {
                    delegate.onNewTopic();
                }
            });
            island.addView(button, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 44));
            panel.addView(island, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 50, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 8));
            newTopicIsland = island;
        }

        panel.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        setProgress(0);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        updateRightInset();
        updateContentWidth(MeasureSpec.getSize(widthMeasureSpec));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        updateBottomInset();
        super.onLayout(changed, left, top, right, bottom);
    }

    // in landscape the panel goes under the navigation bar (or the cutout) at the right, its content stays out of it
    private void updateRightInset() {
        final androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(this);
        final int inset = insets != null ? AndroidUtilities.getDefaultWindowInsets(insets, false).right : 0;
        if (inset == rightInset) return;
        rightInset = inset;
        panel.getLayoutParams().width = getPanelWidth();
        panel.setPadding(0, 0, inset, 0);
        panel.setTranslationX(getPanelWidth() * (1f - progress));
    }

    // on a narrow screen the panel is narrower: the sidebar's button pushed with the chat stays at its left
    private void updateContentWidth(int width) {
        final int reserve = delegate != null ? delegate.getButtonReserve() : 0;
        final int w = Math.max(dp(200), Math.min(dp(PANEL_WIDTH), width - rightInset - reserve));
        if (w == contentWidth) return;
        contentWidth = w;
        panel.getLayoutParams().width = getPanelWidth();
        panel.setTranslationX(getPanelWidth() * (1f - progress));
        adapter.notifyDataSetChanged();
    }

    /** The panel's width without the inset at the right: how far the chat is pushed. */
    public int getContentWidth() {
        return contentWidth;
    }

    private int getPanelWidth() {
        return contentWidth + rightInset;
    }

    // the list and the new topic button stay above the navigation bar
    private void updateBottomInset() {
        final androidx.core.view.WindowInsetsCompat insets = androidx.core.view.ViewCompat.getRootWindowInsets(this);
        final int inset = insets != null ? insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom : 0;
        if (inset == bottomInset) return;
        bottomInset = inset;
        if (newTopicIsland != null) {
            ((FrameLayout.LayoutParams) newTopicIsland.getLayoutParams()).bottomMargin = dp(8) + inset;
            bottomFade.getLayoutParams().height = dp(64) + inset;
            listView.setPadding(0, dp(4), 0, dp(8 + 50 + 8) + inset);
        } else {
            bottomFade.getLayoutParams().height = dp(24) + inset;
            listView.setPadding(0, dp(4), 0, dp(8) + inset);
        }
        panel.requestLayout();
    }

    // a fade shows only where there is more of the list to scroll to; the new topic island always has one under it
    private void updateFades() {
        if (topFade == null || bottomFade == null) return;
        topFade.setAlpha(Utilities.clamp(listView.computeVerticalScrollOffset() / (float) dp(16), 1f, 0f));
        if (newTopicIsland != null) {
            bottomFade.setAlpha(1f);
        } else {
            final int remaining = listView.computeVerticalScrollRange() - listView.computeVerticalScrollOffset() - listView.computeVerticalScrollExtent();
            bottomFade.setAlpha(Utilities.clamp(remaining / (float) dp(16), 1f, 0f));
        }
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    private long getTopicId(TLRPC.TL_forumTopic topic) {
        return mono ? DialogObject.getPeerDialogId(topic.from_id) : topic.id;
    }

    private CharSequence getSavedDialogTitle(long savedDialogId) {
        final MessagesController controller = MessagesController.getInstance(currentAccount);
        if (savedDialogId == UserConfig.getInstance(currentAccount).getClientUserId()) {
            return getString(R.string.MyNotes);
        }
        if (savedDialogId == UserObject.ANONYMOUS) {
            return getString(R.string.AnonymousForward);
        }
        if (savedDialogId >= 0) {
            return UserObject.getUserName(controller.getUser(savedDialogId));
        }
        final TLRPC.Chat chat = controller.getChat(-savedDialogId);
        return chat != null ? chat.title : "";
    }

    /** A saved chat opened from the sidebar has no menu of its own. */
    public void setMenuAvailable(boolean available) {
        if (menuButton != null) {
            menuButton.setVisibility(available ? VISIBLE : GONE);
        }
    }

    public void setCurrentTopic(long topicId) {
        currentTopicId = topicId;
        adapter.notifyDataSetChanged();
    }

    public float getProgress() {
        return progress;
    }

    public boolean isOpen() {
        return progress > 0;
    }

    // a gesture that started on the panel stays its own until the finger is lifted, even once the panel is closed
    private boolean touching;

    public boolean holdsGesture() {
        return progress > 0 || touching;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        final int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            touching = true;
        }
        final boolean result = super.dispatchTouchEvent(ev);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            touching = false;
            setVisibility(progress > 0 ? VISIBLE : GONE);
        }
        return result;
    }

    public void setProgress(float value) {
        progress = Utilities.clamp01(value);
        panel.setTranslationX(getPanelWidth() * (1f - progress));
        setVisibility(progress > 0 || touching ? VISIBLE : GONE);
        invalidate();
        if (delegate != null) {
            delegate.onProgressChanged(progress);
        }
    }

    public void open() {
        update();
        animateTo(1f, 0);
    }

    public void close() {
        animateTo(0f, 0);
        AndroidUtilities.hideKeyboard(searchField);
    }

    /** After a drag: settles by its velocity in px/s, positive to the right (closing). */
    public void settle(float velocityX) {
        final boolean open = Math.abs(velocityX) > dp(300) ? velocityX < 0 : progress > .5f;
        if (open) update();
        animateTo(open ? 1f : 0f, velocityX);
        if (!open) AndroidUtilities.hideKeyboard(searchField);
    }

    public void cancelAnimation() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    private void animateTo(float to, float velocityX) {
        cancelAnimation();
        if (progress == to) {
            setProgress(to);
            return;
        }
        animator = ValueAnimator.ofFloat(progress, to);
        animator.addUpdateListener(a -> setProgress((float) a.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animator == animation) {
                    animator = null;
                }
            }
        });
        final float distance = Math.abs(to - progress) * getPanelWidth();
        long duration = 320;
        if (Math.abs(velocityX) > dp(300)) {
            duration = (long) Math.max(150, Math.min(320, distance / Math.abs(velocityX) * 1000 * 2));
        }
        animator.setDuration(duration);
        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animator.start();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        dimPaint.setColor(Theme.multAlpha(0x29000000, progress));
        canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);
        if (delegate != null && progress > 0) {
            delegate.drawOverDim(canvas);
        }
        super.dispatchDraw(canvas);
    }

    // a tap on the dimmed chat closes the panel, a drag to the right takes it away
    private float downX, downY;
    private boolean dragging, tracking;
    private float dragStartProgress;
    private android.view.VelocityTracker velocityTracker;

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            downX = e.getX();
            downY = e.getY();
            dragging = false;
            tracking = true;
            dragStartProgress = progress;
            if (velocityTracker != null) velocityTracker.recycle();
            velocityTracker = android.view.VelocityTracker.obtain();
            velocityTracker.addMovement(e);
            return false;
        }
        if (e.getActionMasked() == MotionEvent.ACTION_MOVE && tracking) {
            if (velocityTracker != null) velocityTracker.addMovement(e);
            final float dx = e.getX() - downX, dy = e.getY() - downY;
            if (dx > ViewConfiguration.get(getContext()).getScaledTouchSlop() && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                dragging = true;
                downX = e.getX();
                return true;
            }
        }
        return false;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (progress <= 0) return false;
        if (velocityTracker == null) velocityTracker = android.view.VelocityTracker.obtain();
        velocityTracker.addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                dragging = false;
                dragStartProgress = progress;
                return true;
            case MotionEvent.ACTION_MOVE: {
                final float dx = e.getX() - downX;
                if (!dragging && Math.abs(dx) > ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
                    dragging = true;
                    downX = e.getX();
                    dragStartProgress = progress;
                }
                if (dragging) {
                    cancelAnimation();
                    setProgress(dragStartProgress - (e.getX() - downX) / contentWidth);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    velocityTracker.computeCurrentVelocity(1000);
                    settle(velocityTracker.getXVelocity());
                } else if (e.getActionMasked() == MotionEvent.ACTION_UP && e.getX() < getWidth() - getPanelWidth() * progress) {
                    close();
                }
                dragging = false;
                tracking = false;
                velocityTracker.recycle();
                velocityTracker = null;
                return true;
        }
        return true;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.topicsDidLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.savedMessagesDialogsUpdate);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.topicsDidLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.savedMessagesDialogsUpdate);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);
        cancelServerSearch();
        // a screen opened over the chat detaches it mid-animation: the panel is left where it was going, not halfway
        if (animator != null) {
            animator.end();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.topicsDidLoaded) {
            if ((Long) args[0] == -dialogId) update();
        } else if (id == NotificationCenter.updateInterfaces) {
            update();
        } else if (id == NotificationCenter.savedMessagesDialogsUpdate) {
            if (saved) update();
        } else if (id == NotificationCenter.emojiLoaded) {
            // the rows' layouts keep their emoji spans: redrawing picks up the loaded images
            AndroidUtilities.forEachViews(listView, View::invalidate);
        }
    }

    private void update() {
        if (getVisibility() != VISIBLE && progress <= 0 && adapter.getItemCount() > 0) return;
        adapter.update();
    }

    /** The user or chat a row stands for: a saved chat, or the sender of a direct messages topic. */
    private TLObject getItemPeer(Item item) {
        final MessagesController controller = MessagesController.getInstance(currentAccount);
        final long peerId;
        if (item.savedDialog != null) {
            peerId = item.savedDialog.dialogId;
        } else if (item.topic != null && mono) {
            peerId = DialogObject.getPeerDialogId(item.topic.from_id);
        } else {
            return null;
        }
        return peerId >= 0 ? controller.getUser(peerId) : controller.getChat(-peerId);
    }

    private String getItemTitle(Item item) {
        if (item.savedDialog != null) {
            final CharSequence title = getSavedDialogTitle(item.savedDialog.dialogId);
            return title == null ? "" : title.toString();
        }
        final TLObject peer = getItemPeer(item);
        if (peer instanceof TLRPC.User) return UserObject.getUserName((TLRPC.User) peer);
        if (peer instanceof TLRPC.Chat) return ((TLRPC.Chat) peer).title;
        return item.topic != null && item.topic.title != null ? item.topic.title : "";
    }

    private void cancelServerSearch() {
        if (serverSearch != null) {
            AndroidUtilities.cancelRunOnUIThread(serverSearch);
            serverSearch = null;
        }
        if (serverSearchReqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(serverSearchReqId, true);
            serverSearchReqId = 0;
        }
    }

    private void scheduleServerSearch() {
        cancelServerSearch();
        serverResults.clear();
        if (saved || mono || TextUtils.isEmpty(query)) return;
        final String q = query;
        serverSearch = () -> {
            serverSearch = null;
            serverSearchReqId = MessagesController.getInstance(currentAccount).getTopicsController().searchTopics(-dialogId, q, topics -> {
                serverSearchReqId = 0;
                if (!q.equals(query)) return;
                serverResults.clear();
                serverResults.addAll(topics);
                update();
            });
        };
        AndroidUtilities.runOnUIThread(serverSearch, 350);
    }

    private String getItemUsername(Item item) {
        final TLObject peer = getItemPeer(item);
        if (peer instanceof TLRPC.User) return UserObject.getPublicUsername((TLRPC.User) peer);
        if (peer instanceof TLRPC.Chat) return ChatObject.getPublicUsername((TLRPC.Chat) peer);
        return null;
    }

    private boolean titleMatches(Item item) {
        return getItemTitle(item).toLowerCase().contains(query);
    }

    private boolean usernameMatches(Item item) {
        final String username = getItemUsername(item);
        return username != null && username.toLowerCase().contains(query);
    }

    private boolean matchesQuery(Item item) {
        return TextUtils.isEmpty(query) || titleMatches(item) || usernameMatches(item);
    }

    private class Item {
        TLRPC.TL_forumTopic topic; // null for all messages
        SavedMessagesController.SavedDialog savedDialog; // in saved messages

        long getId() {
            if (savedDialog != null) return savedDialog.dialogId;
            return topic == null ? 0 : getTopicId(topic);
        }
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        final ArrayList<Item> items = new ArrayList<>();

        private String lastQuery;

        void update() {
            // the same query on a shown list animates: rows move to their new places
            final boolean animated = TextUtils.equals(lastQuery, query) && progress > 0 && !items.isEmpty();
            lastQuery = query;
            final ArrayList<Item> old = new ArrayList<>(items);
            items.clear();
            if (TextUtils.isEmpty(query)) {
                items.add(new Item());
            }
            if (saved) {
                for (SavedMessagesController.SavedDialog d : MessagesController.getInstance(currentAccount).getSavedMessagesController().allDialogs) {
                    final Item item = new Item();
                    item.savedDialog = d;
                    if (!matchesQuery(item)) continue;
                    items.add(item);
                }
                dispatch(old, animated);
                return;
            }
            final HashSet<Long> shown = new HashSet<>();
            final ArrayList<TLRPC.TL_forumTopic> topics = MessagesController.getInstance(currentAccount).getTopicsController().getTopics(-dialogId);
            if (topics != null) {
                for (TLRPC.TL_forumTopic topic : topics) {
                    final Item item = new Item();
                    item.topic = topic;
                    if (!matchesQuery(item)) continue;
                    items.add(item);
                    shown.add(item.getId());
                }
            }
            if (!TextUtils.isEmpty(query)) {
                for (TLRPC.TL_forumTopic topic : serverResults) {
                    final Item item = new Item();
                    item.topic = topic;
                    if (!shown.add(item.getId())) continue;
                    items.add(item);
                }
            }
            dispatch(old, animated);
        }

        private void dispatch(ArrayList<Item> old, boolean animated) {
            if (!animated) {
                notifyDataSetChanged();
                return;
            }
            DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return old.size();
                }

                @Override
                public int getNewListSize() {
                    return items.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    return old.get(oldItemPosition).getId() == items.get(newItemPosition).getId();
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    return false; // every row rebinds: counters, previews and dividers may have changed
                }
            }).dispatchUpdatesTo(this);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final TopicRow row = new TopicRow(parent.getContext());
            row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(row);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            final Item item = items.get(position);
            ((TopicRow) holder.itemView).set(item, position < items.size() - 1);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    // previews are built from the top messages once per message
    private final HashMap<Long, CharSequence> previews = new HashMap<>();

    private CharSequence getPreview(TLRPC.Message message, boolean withSender) {
        if (message == null) return "";
        final long key = ((long) message.id << 1) ^ message.dialog_id * 31 ^ (withSender ? 1 : 0);
        CharSequence preview = previews.get(key);
        if (preview != null) return preview;
        final MessageObject messageObject = new MessageObject(currentAccount, message, false, false);
        CharSequence text = messageObject.caption != null ? messageObject.caption : messageObject.messageText;
        // plain text: no bold, italic, links, monospace or other entities, only emoji added back below
        text = text == null ? "" : text.toString().replace('\n', ' ');
        if (withSender && !messageObject.isOut()) {
            final long fromId = MessageObject.getFromChatId(message);
            String name = null;
            if (fromId > 0) {
                final TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(fromId);
                if (user != null) name = UserObject.getFirstName(user);
            } else if (fromId < 0 && fromId != dialogId) {
                final TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-fromId);
                if (chat != null) name = chat.title;
            }
            if (!TextUtils.isEmpty(name)) {
                text = TextUtils.concat(name, ": ", text);
            }
        }
        preview = Emoji.replaceEmoji(text, previewPaint.getFontMetricsInt(), false);
        previews.put(key, preview);
        return preview;
    }

    private final TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint timePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint previewPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint counterPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    {
        titlePaint.setTextSize(dp(16));
        titlePaint.setTypeface(AndroidUtilities.bold());
        timePaint.setTextSize(dp(13));
        previewPaint.setTextSize(dp(15));
        counterPaint.setTextSize(dp(12));
        counterPaint.setTypeface(AndroidUtilities.bold());
    }

    private class TopicRow extends FrameLayout implements ItemOptions.ScrimView {
        private final BackupImageView iconView;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private TLRPC.TL_forumTopic topic;
        private boolean divider;
        private StaticLayout titleLayout, previewLayout, timeLayout;
        private String counter;
        private boolean muted, pinned, selected;
        private Drawable muteDrawable, pinDrawable;

        TopicRow(Context context) {
            super(context);
            setWillNotDraw(false);
            iconView = new BackupImageView(context);
            addView(iconView, LayoutHelper.createFrame(20, 20, Gravity.LEFT | Gravity.TOP, 12, 10, 0, 0));
        }

        void set(Item item, boolean divider) {
            final TLRPC.TL_forumTopic topic = item.topic;
            this.topic = topic;
            this.divider = divider;
            final MessagesController controller = MessagesController.getInstance(currentAccount);
            iconView.setAnimatedEmojiDrawable(null);
            iconView.clearImage();
            iconView.setScaleX(1f);
            iconView.setScaleY(1f);
            iconView.setRoundRadius(0);
            final int gray = getThemedColor(Theme.key_windowBackgroundWhiteGrayText);
            iconView.setColorFilter(null);
            TLRPC.Message top;
            CharSequence title;
            int unread = 0;
            if (item.savedDialog != null) {
                final SavedMessagesController.SavedDialog d = item.savedDialog;
                title = getSavedDialogTitle(d.dialogId);
                top = d.message != null ? d.message.messageOwner : null;
                unread = (int) d.unreadCount;
                muted = false;
                pinned = d.pinned;
                final AvatarDrawable avatarDrawable = new AvatarDrawable();
                iconView.setRoundRadius(dp(10));
                if (d.dialogId == UserConfig.getInstance(currentAccount).getClientUserId()) {
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_MY_NOTES);
                    avatarDrawable.setScaleSize(.38f);
                    iconView.setImageDrawable(avatarDrawable);
                } else if (d.dialogId == UserObject.ANONYMOUS) {
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_ANONYMOUS);
                    avatarDrawable.setScaleSize(.38f);
                    iconView.setImageDrawable(avatarDrawable);
                } else {
                    final TLObject peer = d.dialogId > 0 ? controller.getUser(d.dialogId) : controller.getChat(-d.dialogId);
                    avatarDrawable.setInfo(currentAccount, peer);
                    iconView.setForUserOrChat(peer, avatarDrawable);
                }
            } else if (topic == null) {
                title = LocaleController.getString(R.string.TopicsPanelAllMessages);
                if (saved) {
                    // all of saved messages: their own icon, as in the header
                    final AvatarDrawable avatarDrawable = new AvatarDrawable();
                    avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                    avatarDrawable.setScaleSize(.38f);
                    iconView.setRoundRadius(dp(10));
                    iconView.setImageDrawable(avatarDrawable);
                } else {
                    iconView.setImageResource(R.drawable.other_chats);
                    iconView.setColorFilter(new PorterDuffColorFilter(gray, PorterDuff.Mode.SRC_IN));
                }
                final ArrayList<MessageObject> last = controller.dialogMessage.get(dialogId);
                top = last != null && !last.isEmpty() ? last.get(0).messageOwner : null;
                final TLRPC.Dialog dialog = controller.dialogs_dict.get(dialogId);
                if (dialog != null) unread = dialog.unread_count;
                muted = controller.isDialogMuted(dialogId, 0);
                pinned = false;
            } else {
                title = topic.title;
                top = topic.topMessage;
                unread = topic.unread_count;
                muted = controller.isDialogMuted(dialogId, topic.id);
                pinned = topic.pinned;
                if (mono) {
                    final long peerId = DialogObject.getPeerDialogId(topic.from_id);
                    final TLObject peer = peerId > 0 ? controller.getUser(peerId) : controller.getChat(-peerId);
                    final AvatarDrawable avatarDrawable = new AvatarDrawable();
                    avatarDrawable.setInfo(currentAccount, peer);
                    iconView.setRoundRadius(dp(10));
                    iconView.setForUserOrChat(peer, avatarDrawable);
                    if (peer instanceof TLRPC.User) title = UserObject.getUserName((TLRPC.User) peer);
                    else if (peer instanceof TLRPC.Chat) title = ((TLRPC.Chat) peer).title;
                } else if (topic.id == 1) {
                    iconView.setImageResource(R.drawable.msg_filled_general);
                    iconView.setColorFilter(new PorterDuffColorFilter(gray, PorterDuff.Mode.SRC_IN));
                } else if (topic.icon_emoji_id != 0) {
                    iconView.setAnimatedEmojiDrawable(AnimatedEmojiDrawable.make(UserConfig.selectedAccount, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW, topic.icon_emoji_id));
                } else {
                    iconView.setImageDrawable(ForumUtilities.createTopicDrawable(topic, false));
                }
            }
            selected = item.getId() == currentTopicId;
            counter = unread > 0 ? (unread > 999 ? "999+" : String.valueOf(unread)) : null;

            titlePaint.setColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            timePaint.setColor(gray);
            previewPaint.setColor(Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), .6f));

            final int width = contentWidth - dp(8 + 8 + 24);
            final String time = top != null ? LocaleController.stringForMessageListDate(top.date) : "";
            timeLayout = new StaticLayout(time, timePaint, (int) Math.ceil(timePaint.measureText(time)), Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
            int titleWidth = width - dp(26) - timeLayout.getWidth() - dp(6) - (muted ? dp(18) : 0) - (pinned ? dp(19) : 0);
            CharSequence shownTitle = title == null ? "" : title.toString().replace('\n', ' ');
            CharSequence preview = getPreview(top, !mono && !saved);
            if (!TextUtils.isEmpty(query)) {
                // the matched part is highlighted; a row found by its username shows it instead of the last message
                if (titleMatches(item)) {
                    final CharSequence highlighted = AndroidUtilities.highlightText(shownTitle, query, resourcesProvider);
                    if (highlighted != null) shownTitle = highlighted;
                } else if (usernameMatches(item)) {
                    final CharSequence highlighted = AndroidUtilities.highlightText("@" + getItemUsername(item), query, resourcesProvider);
                    if (highlighted != null) preview = highlighted;
                }
            }
            final CharSequence titleText = TextUtils.ellipsize(Emoji.replaceEmoji(shownTitle, titlePaint.getFontMetricsInt(), false), titlePaint, Math.max(0, titleWidth), TextUtils.TruncateAt.END);
            titleLayout = new StaticLayout(titleText, titlePaint, Math.max(1, titleWidth), Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
            final int previewWidth = width - (counter != null ? (int) counterPaint.measureText(counter) + dp(16 + 6) : 0);
            previewLayout = StaticLayoutEx.createStaticLayout(preview, previewPaint, Math.max(1, previewWidth), Layout.Alignment.ALIGN_NORMAL, 1f, dp(1), false, TextUtils.TruncateAt.END, Math.max(1, previewWidth), 2);
            if (muted && muteDrawable == null) muteDrawable = ContextCompat.getDrawable(getContext(), R.drawable.list_mute).mutate();
            if (pinned && pinDrawable == null) pinDrawable = ContextCompat.getDrawable(getContext(), R.drawable.list_pin).mutate();
            requestLayout();
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int previewHeight = previewLayout != null && previewLayout.getText().length() > 0 ? previewLayout.getHeight() : 0;
            final int height = dp(10 + 20 + 4) + previewHeight + dp(previewHeight > dp(24) ? 6 : 10);
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(Math.max(dp(48), height), MeasureSpec.EXACTLY));
        }

        @Override
        public void drawScrim(Canvas canvas, float progress) {
            // the menu's copy is opaque: its semi-transparent texts don't add up with the row under it
            paint.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
            AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
            canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(16), dp(16), paint);
            draw(canvas);
        }

        @Override
        public void setTranslationY(float translationY) {
            // the moving background is drawn by onDraw: redraw when it appears or goes
            if ((getTranslationY() != 0) != (translationY != 0)) invalidate();
            super.setTranslationY(translationY);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            if (getTranslationY() != 0) {
                // a moving row covers the rows it passes over
                paint.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
                canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            }
            if (selected) {
                paint.setColor(Theme.multAlpha(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), .06f));
                AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(16), dp(16), paint);
            }
            final int left = dp(12), right = getWidth() - dp(12);
            if (titleLayout != null) {
                canvas.save();
                canvas.translate(left + dp(26), dp(10) + (dp(20) - titleLayout.getHeight()) / 2f);
                titleLayout.draw(canvas);
                canvas.restore();
            }
            float x = right;
            if (timeLayout != null) {
                x -= timeLayout.getWidth();
                canvas.save();
                canvas.translate(x, dp(10) + (dp(20) - timeLayout.getHeight()) / 2f);
                timeLayout.draw(canvas);
                canvas.restore();
                x -= dp(6);
            }
            final int iconColor = getThemedColor(Theme.key_windowBackgroundWhiteGrayText);
            if (pinned && pinDrawable != null) {
                pinDrawable.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
                final int s = dp(13);
                pinDrawable.setBounds((int) x - s, dp(14), (int) x, dp(14) + s);
                pinDrawable.draw(canvas);
                x -= s + dp(6);
            }
            if (muted && muteDrawable != null) {
                muteDrawable.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
                final int s = dp(12);
                muteDrawable.setBounds((int) x - s, dp(14), (int) x, dp(14) + s);
                muteDrawable.draw(canvas);
            }
            if (previewLayout != null) {
                canvas.save();
                canvas.translate(left, dp(34));
                previewLayout.draw(canvas);
                canvas.restore();
            }
            if (counter != null) {
                final float w = Math.max(dp(23), counterPaint.measureText(counter) + dp(14));
                paint.setColor(getThemedColor(muted ? Theme.key_chats_unreadCounterMuted : Theme.key_chats_unreadCounter));
                AndroidUtilities.rectTmp.set(right - w, dp(34), right, dp(34 + 23));
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(11.5f), dp(11.5f), paint);
                counterPaint.setColor(getThemedColor(Theme.key_chats_unreadCounterText));
                canvas.drawText(counter, AndroidUtilities.rectTmp.centerX() - counterPaint.measureText(counter) / 2f, AndroidUtilities.rectTmp.centerY() - (counterPaint.descent() + counterPaint.ascent()) / 2f, counterPaint);
            }
            if (divider && !selected) {
                paint.setColor(Theme.getColor(Theme.key_divider, resourcesProvider));
                canvas.drawRect(left, getHeight() - 1, right, getHeight(), paint);
            }
        }
    }
}
