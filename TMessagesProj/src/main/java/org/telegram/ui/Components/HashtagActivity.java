package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

import org.checkerframework.checker.units.qual.A;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.HashtagSearchController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Adapters.MessagesSearchAdapter;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ChatActivityContainer;
import org.telegram.ui.Stories.StoriesController;

public class HashtagActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private final String query;
    private final String hashtag;
    private final String username;

    private final StoriesController.SearchStoriesList storiesList;

    public HashtagActivity(String query) {
        this(query, null);
    }
    public HashtagActivity(String query, Theme.ResourcesProvider resourcesProvider) {
        super();
        setResourceProvider(resourcesProvider);

        if (query == null) {
            query = "";
        }
        query = query.trim();
        if (!query.startsWith("#") && !query.startsWith("$"))
            query = "#" + query;
        int atIndex = query.indexOf("@");
        if (atIndex > 0) {
            hashtag = query.substring(0, atIndex);
            username = query.substring(atIndex + 1);
        } else {
            hashtag = query;
            username = null;
        }
        this.query = hashtag + (!TextUtils.isEmpty(username) ? "@" + username : "");

        storiesList = new StoriesController.SearchStoriesList(currentAccount, username, hashtag);
    }

    @Override
    public boolean onFragmentCreate() {
        getMessagesController().getStoriesController().attachedSearchLists.add(storiesList);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storiesListUpdated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.hashtagSearchUpdated);
        storiesList.load(true, 18);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        getMessagesController().getStoriesController().attachedSearchLists.remove(storiesList);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storiesListUpdated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.hashtagSearchUpdated);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.storiesListUpdated) {
            if (args[0] == storiesList) {
                if (storiesView != null) {
                    updateStoriesVisible(storiesView.set(storiesList), true);
                }
                if (storiesTotalTextView != null) {
                    storiesTotalTextView.setText(LocaleController.formatPluralString("FoundStories", storiesList.getCount()));
                }
            }
        } else if (id == NotificationCenter.hashtagSearchUpdated) {
            if (chatContainer == null || chatContainer.chatActivity == null) return;
            int guid = (Integer) args[0];
            if (guid != chatContainer.chatActivity.getClassGuid()) {
                return;
            }

            int count = (Integer) args[1];
            if (storiesView != null) {
                storiesView.setMessages(count, hashtag, username);
            }
        }
    }

    private FrameLayout contentView;
    private ChatActivityContainer chatContainer;
    private FrameLayout sharedMediaLayoutContainer;
    private SharedMediaLayout sharedMediaLayout;
    private MessagesSearchAdapter.StoriesView storiesView;
    private FrameLayout storiesTotal;
    private TextView storiesTotalTextView;


    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(query);
        actionBar.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        actionBar.setItemsColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText), false);
        actionBar.setItemsBackgroundColor(getThemedColor(Theme.key_actionBarWhiteSelector), false);
        actionBar.setTitleColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        actionBar.setCastShadows(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));

        contentView = new FrameLayout(context) {
            @Override
            public void setTranslationY(float translationY) {
                super.setTranslationY(translationY);
                setPadding(0, 0, (int) translationY, 0);
            }
        };
        frameLayout.addView(contentView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        HashtagSearchController.getInstance(currentAccount).clearSearchResults(ChatActivity.SEARCH_CHANNEL_POSTS);
        Bundle args = new Bundle();
        args.putInt("chatMode", ChatActivity.MODE_SEARCH);
        args.putInt("searchType", ChatActivity.SEARCH_CHANNEL_POSTS);
        args.putString("searchHashtag", query);
        chatContainer = new ChatActivityContainer(context, getParentLayout(), args) {
            boolean activityCreated = false;
            @Override
            protected void initChatActivity() {
                if (!activityCreated) {
                    activityCreated = true;
                    super.initChatActivity();
                }
            }
        };

        contentView.addView(chatContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        sharedMediaLayout = new SharedMediaLayout(context, 0, new SharedMediaLayout.SharedMediaPreloader(null), 0, null, null, null, SharedMediaLayout.TAB_STORIES, this, new SharedMediaLayout.Delegate() {
            @Override
            public void scrollToSharedMedia() {}
            @Override
            public boolean onMemberClick(TLRPC.ChatParticipant participant, boolean b, boolean resultOnly, View vi) {
                return false;
            }
            @Override
            public TLRPC.Chat getCurrentChat() {
                return null;
            }
            @Override
            public boolean isFragmentOpened() {
                return true;
            }
            @Override
            public RecyclerListView getListView() {
                return null;
            }
            @Override
            public boolean canSearchMembers() {
                return false;
            }
            @Override
            public void updateSelectedMediaTabText() {}
        }, SharedMediaLayout.VIEW_TYPE_MEDIA_ACTIVITY, resourceProvider) {
            @Override
            public String getStoriesHashtag() {
                return hashtag;
            }
            @Override
            public String getStoriesHashtagUsername() {
                return username;
            }
            @Override
            protected boolean canShowSearchItem() {
                return false;
            }
            @Override
            protected void onSearchStateChanged(boolean expanded) {}
            @Override
            protected void drawBackgroundWithBlur(Canvas canvas, float y, Rect rectTmp2, Paint backgroundPaint) {}
            @Override
            protected void invalidateBlur() {}
            @Override
            protected boolean isStoriesView() {
                return false;
            }
            protected boolean customTabs() {
                return true;
            }
            @Override
            protected boolean includeStories() {
                return false;
            }
            @Override
            protected boolean includeSavedDialogs() {
                return false;
            }
            @Override
            protected boolean isArchivedOnlyStoriesView() {
                return false;
            }
            @Override
            protected int getInitialTab() {
                return SharedMediaLayout.TAB_STORIES;
            }
            @Override
            protected void showActionMode(boolean show) {}
            @Override
            protected void onActionModeSelectedUpdate(SparseArray<MessageObject> messageObjects) {}
            @Override
            protected void onTabProgress(float progress) {}
            @Override
            protected void onTabScroll(boolean scrolling) {}
            @Override
            public boolean isSearchingStories() {
                return true;
            }
            @Override
            public boolean addActionButtons() {
                return false;
            }
        };
        if (sharedMediaLayout.getSearchOptionsItem() != null) {
            sharedMediaLayout.getSearchOptionsItem().setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider), PorterDuff.Mode.MULTIPLY));
        }
        sharedMediaLayout.setPinnedToTop(true);
        sharedMediaLayout.photoVideoOptionsItem.setTranslationY(0);
        if (sharedMediaLayout.getSearchOptionsItem() != null) {
            sharedMediaLayout.getSearchOptionsItem().setTranslationY(0);
        }
        sharedMediaLayout.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        sharedMediaLayout.updateStoriesList(storiesList);
        sharedMediaLayoutContainer = new FrameLayout(context);
        sharedMediaLayoutContainer.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        sharedMediaLayoutContainer.addView(sharedMediaLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 0, 0, 49));
        storiesTotal = new FrameLayout(context);
        storiesTotal.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
        storiesTotalTextView = new TextView(context);
        storiesTotalTextView.setTypeface(AndroidUtilities.bold());
        storiesTotalTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        storiesTotalTextView.setTextColor(getThemedColor(Theme.key_chat_searchPanelText));
        storiesTotalTextView.setText(LocaleController.formatPluralString("FoundStories", storiesList.getCount()));
        storiesTotal.addView(storiesTotalTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | Gravity.LEFT, 18, 0, 18, 0));
        View shadow = new View(context);
        shadow.setBackgroundColor(Theme.getColor(Theme.key_divider, resourceProvider));
        storiesTotal.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1.0f / AndroidUtilities.density, Gravity.FILL_HORIZONTAL | Gravity.TOP));
        sharedMediaLayoutContainer.addView(storiesTotal, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 49, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
        contentView.addView(sharedMediaLayoutContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        storiesView = new MessagesSearchAdapter.StoriesView(context, resourceProvider);
        storiesView.setBackground(Theme.createSelectorWithBackgroundDrawable(getThemedColor(Theme.key_windowBackgroundWhite), getThemedColor(Theme.key_listSelector)));
        storiesView.setOnClickListener(v -> {
            transit(!storiesVisible, true);
            storiesView.transition(storiesVisible);
        });
        updateStoriesVisible(storiesView.set(storiesList), false);
        storiesView.setMessages(HashtagSearchController.getInstance(currentAccount).getCount(ChatActivity.SEARCH_CHANNEL_POSTS), hashtag, username);
        frameLayout.addView(storiesView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        transit(false, false);

        return fragmentView;
    }

    private float contentViewValue;
    private ValueAnimator contentViewAnimator;
    private void updateStoriesVisible(boolean visible, boolean animated) {
        storiesView.animate().cancel();
        if (contentViewAnimator != null) {
            contentViewAnimator.cancel();
        }
        if (!animated) {
            storiesView.setVisibility(visible ? View.VISIBLE : View.GONE);
            storiesView.setTranslationY(visible ? 0 : -dp(48));
            contentView.setTranslationY(visible ? dp(48) : 0);
            contentView.setPadding(0, 0, 0, visible ? dp(48) : 0);
            return;
        }
        storiesView.setVisibility(View.VISIBLE);
        storiesView.animate().translationY(visible ? 0 : -dp(48)).withEndAction(() -> {
            if (!visible) {
                storiesView.setVisibility(View.GONE);
            }
        }).setDuration(320).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
        contentViewAnimator = ValueAnimator.ofFloat(contentViewValue, visible ? 1.0f : 0.0f);
        contentViewAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(@NonNull ValueAnimator animation) {
                contentViewValue = (float) animation.getAnimatedValue();
                contentView.setTranslationY(contentViewValue * dp(48));
                contentView.setPadding(0, 0, 0, (int) (contentViewValue * dp(48)));
            }
        });
        contentViewAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                contentViewValue = visible ? 1.0f : 0.0f;
                contentView.setTranslationY(contentViewValue * dp(48));
                contentView.setPadding(0, 0, 0, (int) (contentViewValue * dp(48)));
            }
        });
        contentViewAnimator.setDuration(320);
        contentViewAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        contentViewAnimator.start();
    }

    private boolean storiesVisible;
    private float transitValue;
    private ValueAnimator transitAnimator;
    private void transit(boolean stories, boolean animated) {
        if (transitAnimator != null) {
            transitAnimator.cancel();
        }
        if (!animated) {
            storiesVisible = stories;
            transitValue = stories ? 1.0f : 0.0f;
            sharedMediaLayout.setScaleX(stories ? 1.0f : 0.95f);
            sharedMediaLayout.setScaleY(stories ? 1.0f : 0.95f);
            sharedMediaLayoutContainer.setAlpha(stories ? 1.0f : 0.0f);
            sharedMediaLayoutContainer.setVisibility(stories ? View.VISIBLE : View.GONE);
            if (chatContainer != null && chatContainer.chatActivity != null && chatContainer.chatActivity.messagesSearchListView != null) {
                chatContainer.chatActivity.messagesSearchListView.setScaleX(AndroidUtilities.lerp(1.0f, 0.95f, transitValue));
                chatContainer.chatActivity.messagesSearchListView.setScaleY(AndroidUtilities.lerp(1.0f, 0.95f, transitValue));
            }
            return;
        }
        if (storiesVisible == stories) return;
        storiesVisible = stories;
        sharedMediaLayoutContainer.setVisibility(View.VISIBLE);
        transitAnimator = ValueAnimator.ofFloat(transitValue, stories ? 1.0f : 0.0f);
        transitAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(@NonNull ValueAnimator animation) {
                transitValue = (float) animation.getAnimatedValue();
                sharedMediaLayout.setScaleX(AndroidUtilities.lerp(0.95f, 1.0f, transitValue));
                sharedMediaLayout.setScaleY(AndroidUtilities.lerp(0.95f, 1.0f, transitValue));
                if (chatContainer != null && chatContainer.chatActivity != null && chatContainer.chatActivity.messagesSearchListView != null) {
                    chatContainer.chatActivity.messagesSearchListView.setScaleX(AndroidUtilities.lerp(1.0f, 0.95f, transitValue));
                    chatContainer.chatActivity.messagesSearchListView.setScaleY(AndroidUtilities.lerp(1.0f, 0.95f, transitValue));
                }
                sharedMediaLayoutContainer.setAlpha(transitValue);
            }
        });
        transitAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                transitValue = stories ? 1.0f : 0.0f;
                sharedMediaLayout.setScaleX(AndroidUtilities.lerp(0.95f, 1.0f, transitValue));
                sharedMediaLayout.setScaleY(AndroidUtilities.lerp(0.95f, 1.0f, transitValue));
                if (chatContainer != null && chatContainer.chatActivity != null && chatContainer.chatActivity.messagesSearchListView != null) {
                    chatContainer.chatActivity.messagesSearchListView.setScaleX(AndroidUtilities.lerp(1.0f, 0.95f, transitValue));
                    chatContainer.chatActivity.messagesSearchListView.setScaleY(AndroidUtilities.lerp(1.0f, 0.95f, transitValue));
                }
                sharedMediaLayoutContainer.setAlpha(transitValue);
                if (!stories) {
                    sharedMediaLayoutContainer.setVisibility(View.GONE);
                }
            }
        });
        transitAnimator.setDuration(320);
        transitAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        transitAnimator.start();
    }

    @Override
    public boolean isLightStatusBar() {
        int color = Theme.getColor(Theme.key_windowBackgroundWhite, null, true);
        return ColorUtils.calculateLuminance(color) > 0.7f;
    }
}
