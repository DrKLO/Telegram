package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatPluralStringComma;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.DialogsActivity.highlightFoundQuote;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DialogCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Premium.PremiumPreviewBottomSheet;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.Stars.StarsController;
import org.telegram.ui.Stars.StarsIntroActivity;
import org.telegram.ui.Stories.DarkThemeResourceProvider;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PostsSearchContainer extends FrameLayout {

    private final BaseFragment fragment;
    private final int currentAccount;
    private final UniversalRecyclerView listView;

    private TLRPC.SearchPostsFlood flood;

    private final ArrayList<MessageObject> newsMessages = new ArrayList<MessageObject>();
    private int newsMessagesLastRate;
    private boolean newsMessagesEndReached;

    private final ArrayList<MessageObject> messages = new ArrayList<MessageObject>();
    private int lastRate;
    private boolean endReached;

    private boolean loading;
    private String lastQuery;

    private final FrameLayout emptyParentView;
    private final LinearLayout emptyView;
    private final BackupImageView emptyImageView;
    private final TextView emptyTitleView;
    private final TextView emptyTextView;
    private final ButtonWithCounterView emptyButton;
    private final TextView emptyUnderButtonTextView;

    public PostsSearchContainer(Context context, BaseFragment fragment) {
        super(context);

        this.fragment = fragment;
        this.currentAccount = fragment.getCurrentAccount();

        listView = new UniversalRecyclerView(context, currentAccount, 0, this::fillItems, this::onItemClick, null, null);
        listView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                final boolean news = TextUtils.isEmpty(lastQuery);
                if (!(news ? newsMessages: messages).isEmpty() && (!listView.canScrollVertically(1) || isLoadingVisible())) {
                    load(false);
                }
                if (listView.scrollingByUser && !isEmpty && fragment != null && fragment.getParentActivity() != null) {
                    AndroidUtilities.hideKeyboard(fragment.getParentActivity().getCurrentFocus());
                }
            }
        });
        addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        emptyParentView = new FrameLayout(context);

        emptyView = new LinearLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(
                    MeasureSpec.makeMeasureSpec(
                        Math.min(dp(220), MeasureSpec.getSize(widthMeasureSpec)),
                        MeasureSpec.getMode(widthMeasureSpec)
                    ),
                    heightMeasureSpec
                );
            }
        };
        emptyView.setOrientation(LinearLayout.VERTICAL);
        emptyParentView.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 32, 0, 32, 0));

        emptyImageView = new BackupImageView(context);
        emptyImageView.setVisibility(View.GONE);
        emptyView.addView(emptyImageView, LayoutHelper.createLinear(130, 130, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 12));

        emptyTitleView = new TextView(context);
        emptyTitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        emptyTitleView.setTypeface(AndroidUtilities.bold());
        emptyTitleView.setGravity(Gravity.CENTER);
        emptyTitleView.setSingleLine(false);
        emptyTitleView.setMaxLines(4);
        emptyTitleView.setEllipsize(TextUtils.TruncateAt.END);
        emptyView.addView(emptyTitleView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        emptyTextView = new TextView(context);
        emptyTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyTextView.setGravity(Gravity.CENTER);
        emptyTextView.setSingleLine(false);
        emptyTextView.setMaxLines(4);
        emptyTextView.setEllipsize(TextUtils.TruncateAt.END);
        emptyView.addView(emptyTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 9, 0, 0));

        emptyButton = new ButtonWithCounterView(context, null);
        emptyView.addView(emptyButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 44, Gravity.FILL_HORIZONTAL, 0, 19, 0, 0));

        emptyUnderButtonTextView = new TextView(context);
        emptyUnderButtonTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        emptyUnderButtonTextView.setGravity(Gravity.CENTER);
        emptyView.addView(emptyUnderButtonTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

        addView(emptyParentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        listView.setHideIfEmpty(false);
        listView.setEmptyView(emptyParentView);
        listView.setAnimateEmptyView(true, RecyclerListView.EMPTY_VIEW_ANIMATION_TYPE_ALPHA);

        updateColors();
        updateEmptyView();
    }

    public void updateColors() {
        emptyParentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        emptyTitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        emptyUnderButtonTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        listView.setAdapter(null);
        listView.setAdapter(listView.adapter);
        if (colorSpan != null) {
            colorSpan = null;
            updateEmptyView();
        }
    }

    private boolean isLoadingVisible() {
        for (int i = 0; i < listView.getChildCount(); ++i) {
            if (listView.getChildAt(i) instanceof FlickerLoadingView)
                return true;
        }
        return false;
    }

    private void load(final boolean pay) {
        if (loading) return;

        final boolean news = TextUtils.isEmpty(lastQuery);
        if (news && newsMessagesEndReached) {
            return;
        }
        if (!news && endReached) {
            return;
        }
        if (!news && flood == null) {
            return;
        }

        loading = true;

        final MessagesController messagesController = MessagesController.getInstance(currentAccount);
        final ConnectionsManager connectionsManager = ConnectionsManager.getInstance(currentAccount);

        final TLRPC.TL_channels_searchPosts req = new TLRPC.TL_channels_searchPosts();
        req.flags |= 2;
        req.query = lastQuery;
        req.limit = 30;
        if (news) {
            if (!newsMessages.isEmpty()) {
                final MessageObject lastMessage = newsMessages.get(newsMessages.size() - 1);
                req.offset_rate = newsMessagesLastRate;
                req.offset_id = lastMessage.getRealId();
                req.offset_peer = messagesController.getInputPeer(lastMessage.messageOwner.peer_id);
            } else {
                req.offset_peer = new TLRPC.TL_inputPeerEmpty();
            }
        } else {
            if (!messages.isEmpty()) {
                final MessageObject lastMessage = messages.get(messages.size() - 1);
                req.offset_rate = lastRate;
                req.offset_id = lastMessage.getRealId();
                req.offset_peer = messagesController.getInputPeer(lastMessage.messageOwner.peer_id);
            } else {
                req.offset_peer = new TLRPC.TL_inputPeerEmpty();
            }
        }
        final long paying;
        if (pay && flood != null) {
            req.flags |= 4;
            req.allow_paid_stars = paying = flood.stars_amount;
        } else {
            paying = 0;
        }

        reqId = connectionsManager.sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            reqId = -1;
            loading = false;
            emptyButton.setLoading(false);
            if (res instanceof TLRPC.messages_Messages) {
                final TLRPC.messages_Messages r = (TLRPC.messages_Messages) res;
                messagesController.putUsers(r.users, false);
                messagesController.putChats(r.chats, false);

                if (r.search_flood != null) {
                    flood = r.search_flood;
                }

                final ArrayList<MessageObject> messages = news ? newsMessages : this.messages;
                final boolean firstMessages = messages.isEmpty();
                for (TLRPC.Message message : r.messages) {
                    final MessageObject msg = new MessageObject(currentAccount, message, false, false);
                    if (!news) {
                        msg.setQuery(req.query);
                    }
                    messages.add(msg);
                }

                if (!news) {
                    if (r instanceof TLRPC.TL_messages_messagesSlice) {
                        lastRate = r.next_rate;
                        endReached = (r.flags & 1) == 0;
                    } else if (r instanceof TLRPC.TL_messages_messages) {
                        lastRate = 0;
                        endReached = true;
                    } else if (r instanceof TLRPC.TL_messages_channelMessages) {
                        lastRate = 0;
                        endReached = true;
                    }
                } else {
                    if (r instanceof TLRPC.TL_messages_messagesSlice) {
                        newsMessagesLastRate = r.next_rate;
                        newsMessagesEndReached = (r.flags & 1) == 0;
                    } else if (r instanceof TLRPC.TL_messages_messages) {
                        newsMessagesLastRate = 0;
                        newsMessagesEndReached = true;
                    } else if (r instanceof TLRPC.TL_messages_channelMessages) {
                        newsMessagesLastRate = 0;
                        newsMessagesEndReached = true;
                    }
                }

                updateEmptyView();
                if (firstMessages) {
                    listView.scrollToPosition(0);
                }
                listView.adapter.update(true);
                if (!messages.isEmpty() && !(news ? newsMessagesEndReached : endReached)) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (!(news ? newsMessages : messages).isEmpty() && (!listView.canScrollVertically(1) || isLoadingVisible())) {
                            load(false);
                        }
                    });
                }

                if (pay && paying > 0 && !news) {
                    BulletinFactory.of(fragment)
                        .createSimpleBulletin(R.raw.stars_topup, AndroidUtilities.replaceTags(formatPluralStringComma("SearchPaidStars", (int) paying)))
                        .show();
                }

            } else if (err != null && err.text.startsWith("FLOOD_WAIT_") && err.text.contains("_OR_STARS_")) {
                final Pattern pattern = Pattern.compile("FLOOD_WAIT_(\\d+)_OR_STARS_(\\d+)");
                final Matcher matcher = pattern.matcher(err.text);
                if (matcher != null && matcher.matches()) {
                    final int waitSeconds = Integer.parseInt(matcher.group(1));
                    final int starsPrice = Integer.parseInt(matcher.group(2));

                    if (flood != null) {
                        flood.flags |= 2;
                        flood.wait_till = connectionsManager.getCurrentTime() + waitSeconds;
                        flood.stars_amount = starsPrice;
                    }

                    updateEmptyView();
                    listView.adapter.update(true);
                }
            } else if (err != null && "PREMIUM_ACCOUNT_REQUIRED".equalsIgnoreCase(err.text)) {
                updateEmptyView();
                listView.adapter.update(true);
            } else if (err != null && "BALANCE_TOO_LOW".equalsIgnoreCase(err.text)) {
                updateEmptyView();
                listView.adapter.update(true);
                StarsController.getInstance(currentAccount).getBalance(true, () -> {
                    final Activity activity = AndroidUtilities.getActivity();
                    final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();
                    final Theme.ResourcesProvider resourcesProvider = PhotoViewer.getInstance().isVisible() || lastFragment != null && lastFragment.hasShownSheet() ? new DarkThemeResourceProvider() : (lastFragment != null ? lastFragment.getResourceProvider() : null);
                    new StarsIntroActivity.StarsNeededSheet(activity, resourcesProvider, paying, StarsIntroActivity.StarsNeededSheet.TYPE_SEARCH, "", () -> {
                        load(true);
                    }, 0).show();
                }, true);
            }
        }), ConnectionsManager.RequestFlagDoNotWaitFloodWait);

        updateEmptyView();
        listView.adapter.update(true);
    }

    private int reqId = -1;
    private void cancel() {
        if (reqId >= 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
            reqId = -1;
        }
        loading = false;
        emptyButton.setLoading(false);
    }

    private int queryid = 0;
    public void search(String q) {
        if (TextUtils.equals(lastQuery, q))
            return;

        cancel();
        lastQuery = q;
        if (TextUtils.isEmpty(q)) {
            lastRate = 0;
            queryid++;
            endReached = false;
            messages.clear();

            load(false);
        } else {
            loadFlood(q);

            lastRate = 0;
            queryid++;
            endReached = false;
            messages.clear();
        }
        updateEmptyView();
        listView.scrollToPosition(0);
        listView.adapter.update(true);
    }

    private boolean floodLoading;
    private boolean wasEmptyOnFloodLoad;
    private int floodLoadingRequestId = -1;

    private void loadFlood(String query) {
        if (floodLoadingRequestId >= 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(floodLoadingRequestId, true);
            floodLoadingRequestId = -1;
        }
        if (!floodLoading) {
            wasEmptyOnFloodLoad = isEmpty && !(messages.isEmpty() && endReached);
        }
        floodLoading = true;
        final TLRPC.TL_channels_checkSearchPostsFlood req = new TLRPC.TL_channels_checkSearchPostsFlood();
        if (!TextUtils.isEmpty(query)) {
            req.flags |= 1;
            req.query = query;
        }
        floodLoadingRequestId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            floodLoading = false;
            if (res instanceof TLRPC.SearchPostsFlood) {
                flood = (TLRPC.SearchPostsFlood) res;
                if (flood.query_is_free) {
                    load(false);
                } else {
                    updateEmptyView();
                    listView.adapter.update(true);
                }
            }
        }));
    }

    private boolean wasOpen;
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (flood == null) {
            loadFlood(null);
        }
        if (!wasOpen) {
            wasOpen = true;
            MessagesController.getGlobalMainSettings().edit()
                .putInt("searchpostsnew", MessagesController.getGlobalMainSettings().getInt("searchpostsnew", 0) + 1)
                .apply();

            StarsController.getInstance(currentAccount).getBalance();
        }
    }

    private boolean isEmpty;
    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (flood == null) {
            items.add(UItem.asFlicker(-1, FlickerLoadingView.DIALOG_CELL_TYPE));
            items.add(UItem.asFlicker(-2, FlickerLoadingView.DIALOG_CELL_TYPE));
            items.add(UItem.asFlicker(-3, FlickerLoadingView.DIALOG_CELL_TYPE));
            isEmpty = false;
            return;
        }
        final boolean news = TextUtils.isEmpty(lastQuery);
        if (news) {
            if (!newsMessages.isEmpty()) {
                items.add(UItem.asGraySection(getString(R.string.SearchPostsHeaderNews)));
            }
            for (MessageObject msg : newsMessages) {
                items.add(UItem.asSearchMessage(msg));
            }
        } else {
            if (!messages.isEmpty()) {
                items.add(UItem.asGraySection(getString(R.string.SearchPostsHeaderFound)));
            }
            for (MessageObject msg : messages) {
                items.add(UItem.asSearchMessage(msg));
            }
        }
        if (
            loading ||
            floodLoading && !wasEmptyOnFloodLoad ||
            !news && !messages.isEmpty() && !endReached
        ) {
            items.add(UItem.asFlicker(queryid * 3 + 0, FlickerLoadingView.DIALOG_CELL_TYPE));
            items.add(UItem.asFlicker(queryid * 3 + 1, FlickerLoadingView.DIALOG_CELL_TYPE));
            items.add(UItem.asFlicker(queryid * 3 + 2, FlickerLoadingView.DIALOG_CELL_TYPE));
        }
        isEmpty = items.isEmpty();
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.object instanceof MessageObject) {
            final MessageObject msg = (MessageObject) item.object;
            Bundle args = new Bundle();
            if (msg.getDialogId() >= 0) {
                args.putLong("user_id", msg.getDialogId());
            } else {
                args.putLong("chat_id", -msg.getDialogId());
            }
            args.putInt("message_id", msg.getId());
            ChatActivity chatActivity = new ChatActivity(args);
            fragment.presentFragment(highlightFoundQuote(chatActivity, msg));
        }
    }

    private ColoredImageSpan searchSpan;
    private ColoredImageSpan arrowSpan;
    private ForegroundColorAlphaSpan colorSpan;
    private ColoredImageSpan[] starSpan = new ColoredImageSpan[1];
    private final Runnable updateEmptyViewRunnable = this::updateEmptyView;
    private void updateEmptyView() {
        AndroidUtilities.cancelRunOnUIThread(updateEmptyViewRunnable);
        final int now = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        if (!UserConfig.getInstance(currentAccount).isPremium()) {
            emptyImageView.setVisibility(View.GONE);
            emptyTitleView.setText(getString(R.string.SearchPostsTitle));
            emptyTextView.setText(getString(R.string.SearchPostsText));

            emptyButton.setVisibility(View.VISIBLE);
            emptyButton.setText(getString(R.string.SearchPostsButtonPremium), true);
            emptyButton.setSubText(null, true);
            emptyButton.setOnClickListener(v -> {
                fragment.presentFragment(
                    new PremiumPreviewFragment("search")
                );
            });
            emptyUnderButtonTextView.setVisibility(View.VISIBLE);
            emptyUnderButtonTextView.setText(getString(R.string.SearchPostsPremium));
        } else if (!TextUtils.isEmpty(lastQuery) && messages.isEmpty() && endReached) {
            if (emptyImageView.getImageReceiver().getImageDrawable() == null) {
                emptyImageView.setImageDrawable(new RLottieDrawable(R.raw.utyan_empty, "utyan_empty", dp(130), dp(130)));
            }
            emptyImageView.setVisibility(View.VISIBLE);
            emptyTitleView.setText(getString(R.string.SearchPostsNotFound));
            emptyTextView.setText(formatString(R.string.SearchPostsNotFoundText, TextUtils.ellipsize(lastQuery, emptyTextView.getPaint(), dp(100), TextUtils.TruncateAt.END)));
            emptyButton.setVisibility(View.GONE);
            emptyUnderButtonTextView.setVisibility(View.GONE);
        } else if (!TextUtils.isEmpty(lastQuery) && flood != null && (flood.flags & 2) != 0 && now < flood.wait_till) {
            emptyImageView.setVisibility(View.GONE);
            emptyTitleView.setText(getString(R.string.SearchPostsLimitReached));
            emptyTextView.setText(formatPluralStringComma("SearchPostsLimitReachedText", flood.total_daily));

            final int S = flood.wait_till - now;
            final int h = S / 3600;
            final int m = (S - h * 3600) / 60;
            final int s = S - h * 3600 - m * 60;

            emptyButton.setVisibility(View.VISIBLE);
            emptyButton.setText(StarsIntroActivity.replaceStars(formatPluralStringComma("SearchPostsButtonPay", (int) flood.stars_amount), 1.13f, starSpan), true);
            emptyButton.setSubText(formatString(R.string.SearchPostsFreeSearchUnlocksIn, (h > 0 ? h + ":" : "") + (m < 10 ? "0" + m : m) + ":" + (s < 10 ? "0" + s : s)), true);
            emptyButton.subText.setHacks(false, true, true);
            emptyButton.setOnClickListener(v -> {
                emptyButton.setLoading(true);
                load(true);
            });
            AndroidUtilities.runOnUIThread(updateEmptyViewRunnable, 1000);

            emptyUnderButtonTextView.setVisibility(View.GONE);
        } else if (messages.isEmpty() && !loading && !TextUtils.isEmpty(lastQuery)) {
            emptyImageView.setVisibility(View.GONE);
            emptyTitleView.setText(getString(R.string.SearchPostsTitle));
            emptyTextView.setText(getString(R.string.SearchPostsText));

            final SpannableStringBuilder sb = new SpannableStringBuilder("s ");
            if (searchSpan == null) {
                searchSpan = new ColoredImageSpan(R.drawable.smiles_tab_search);
                searchSpan.setScale(.79f, .79f);
            }
            if (colorSpan == null) {
                colorSpan = new ForegroundColorAlphaSpan(
                    Theme.blendOver(
                        Theme.getColor(Theme.key_featuredStickers_addButton),
                        Theme.multAlpha(Theme.getColor(Theme.key_featuredStickers_buttonText), .75f)
                    )
                );
            }
            sb.setSpan(searchSpan, 0, 1, 0);
            sb.append(getString(R.string.SearchPostsButton));
            sb.append(" ");
            int startIndex = sb.length();
            sb.append(TextUtils.ellipsize(lastQuery, emptyButton.getTextPaint(), dp(100), TextUtils.TruncateAt.END));
            sb.setSpan(colorSpan, startIndex, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append(" >");
            if (arrowSpan == null) {
                arrowSpan = new ColoredImageSpan(R.drawable.msg_mini_forumarrow);
                arrowSpan.setScale(1.05f, 1.05f);
            }
            sb.setSpan(arrowSpan, sb.length() - 1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            emptyButton.setVisibility(View.VISIBLE);
            emptyButton.setText(sb, true);
            emptyButton.text.setHacks(false, true, false);
            emptyButton.setSubText(null, true);
            emptyButton.setOnClickListener(v -> {
                emptyButton.setLoading(true);
                load(false);
            });
            if (flood != null) {
                emptyUnderButtonTextView.setVisibility(View.VISIBLE);
                emptyUnderButtonTextView.setText(formatPluralStringComma("SearchPostsFreeSearches", flood.remains < 1 ? flood.total_daily : flood.remains));
            } else {
                emptyUnderButtonTextView.setVisibility(View.GONE);
            }
        } else /*if (newsMessages.isEmpty() && newsMessagesEndReached)*/ {
            emptyImageView.setVisibility(View.GONE);
            emptyTitleView.setText(getString(R.string.SearchPostsTitle));
            emptyTextView.setText(getString(R.string.SearchPostsText));
            emptyButton.setVisibility(View.GONE);
            if (flood != null) {
                emptyUnderButtonTextView.setVisibility(View.VISIBLE);
                emptyUnderButtonTextView.setText(formatPluralStringComma("SearchPostsFreeSearches", flood.remains < 1 ? flood.total_daily : flood.remains));
            } else {
                emptyUnderButtonTextView.setVisibility(View.GONE);
            }
        }
    }

    public void setKeyboardHeight(int h) {
        emptyView.animate()
            .translationY(-h / 2.0f)
            .setDuration(AdjustPanLayoutHelper.keyboardDuration)
            .setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator)
            .start();
    }

    public static class ForegroundColorAlphaSpan extends CharacterStyle {
        private final int color;
        public ForegroundColorAlphaSpan(int color) {
            this.color = color;
        }

        @Override
        public void updateDrawState(TextPaint tp) {
            tp.setColor(
                Theme.multAlpha(color, tp.getAlpha() / 255f)
            );
        }
    }

}
