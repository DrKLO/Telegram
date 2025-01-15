package org.telegram.ui.Stories;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.appsearch.SearchResult;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.checkerframework.checker.units.qual.A;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.HashtagSearchController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.HashtagsSearchAdapter;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SharedMediaLayout;

import java.util.ArrayList;

public class PublicStoriesList extends FrameLayout {

    private final Theme.ResourcesProvider resourcesProvider;

    private SharedMediaLayout sharedMediaLayout;
    private String hashtag = "";
    private String username = null;
    private boolean tabs = true;

    private final FrameLayout topView;
    private final TextView topTitleView;
    private final TextView textTitleView;

    public void setTabs(boolean tabs) {
        if (this.tabs == tabs) return;
        this.tabs = tabs;
        requestLayout();
    }

    protected void onMessagesClick() {

    }

    public PublicStoriesList(BaseFragment fragment, Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));

        setPadding(0, AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() + dp(tabs ? 40 : 0), 0, dp(51));
        topView = new FrameLayout(context);
        topView.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), Theme.RIPPLE_MASK_ALL));
        topView.setOnClickListener(view -> {
            onMessagesClick();
        });

        topTitleView = new TextView(context);
        topTitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        topTitleView.setTypeface(AndroidUtilities.bold());
        topTitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        topView.addView(topTitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 13.66f, 6.66f, 13.66f, 0));

        textTitleView = new TextView(context);
        textTitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        textTitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        topView.addView(textTitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.FILL_HORIZONTAL | Gravity.TOP, 13.66f, 25, 13.66f, 0));

        View shadow = new View(context);
        shadow.setBackgroundColor(Theme.getColor(Theme.key_divider, resourcesProvider));
        topView.addView(shadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1.0f / AndroidUtilities.density, Gravity.FILL_HORIZONTAL | Gravity.BOTTOM));

        addView(topView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL_HORIZONTAL | Gravity.TOP));



        sharedMediaLayout = new SharedMediaLayout(context, 0, new SharedMediaLayout.SharedMediaPreloader(null), 0, null, null, null, SharedMediaLayout.TAB_STORIES, fragment, new SharedMediaLayout.Delegate() {
            @Override
            public void scrollToSharedMedia() {

            }

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
            public void updateSelectedMediaTabText() {
                // TODO
            }

        }, SharedMediaLayout.VIEW_TYPE_MEDIA_ACTIVITY, resourcesProvider) {
            @Override
            protected void onSelectedTabChanged() {
                super.onSelectedTabChanged();
                // TODO
            }

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
            protected void onSearchStateChanged(boolean expanded) {
//                AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
//                AndroidUtilities.updateViewVisibilityAnimated(avatarContainer, !expanded, 0.95f, true);
            }

            @Override
            protected void drawBackgroundWithBlur(Canvas canvas, float y, Rect rectTmp2, Paint backgroundPaint) {
//                fragmentView.drawBlurRect(canvas, getY() + y, rectTmp2, backgroundPaint, true);
            }

            @Override
            protected void invalidateBlur() {
//                fragmentView.invalidateBlur();
            }

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

            private AnimatorSet actionModeAnimation;

            @Override
            protected void showActionMode(boolean show) {

            }

            @Override
            protected void onActionModeSelectedUpdate(SparseArray<MessageObject> messageObjects) {

            }

            @Override
            protected void onTabProgress(float progress) {

            }

            @Override
            protected void onTabScroll(boolean scrolling) {

            }

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
            sharedMediaLayout.getSearchOptionsItem().setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.MULTIPLY));
        }
        sharedMediaLayout.setPinnedToTop(true);
        sharedMediaLayout.photoVideoOptionsItem.setTranslationY(0);
        if (sharedMediaLayout.getSearchOptionsItem() != null) {
            sharedMediaLayout.getSearchOptionsItem().setTranslationY(0);
        }

        addView(sharedMediaLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 48, 0, 64));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setPadding(0, AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() + dp(tabs ? 40 : 0), 0, dp(51));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void setQuery(String username, String query) {
        this.username = username;
        this.hashtag = query;
        setStoriesList(new StoriesController.SearchStoriesList(UserConfig.selectedAccount, username, query));
        updateTopView();
    }

    public void setStoriesList(StoriesController.StoriesList list) {
        sharedMediaLayout.updateStoriesList(list);
        list.load(true, 9, null);
        updateTopView();
    }

    public void updateTopView() {
        final HashtagSearchController.SearchResult searchResult = HashtagSearchController.getInstance(UserConfig.selectedAccount).getSearchResult(ChatActivity.SEARCH_PUBLIC_POSTS);
        if (searchResult != null) {
            String hashtag = searchResult.lastHashtag;
            String username = null;
            int index = hashtag.indexOf("@");
            if (index >= 0) {
                username = hashtag.substring(index + 1);
                hashtag = hashtag.substring(0, index);
            }
            if (username != null) {
                SpannableStringBuilder title = new SpannableStringBuilder(searchResult.count + " messages in ");
                int start = title.length();
                title.append("@").append(username);
                title.setSpan(new ForegroundColorSpan(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider)), start, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                topTitleView.setText(title);
            } else {
                topTitleView.setText(searchResult.count + " messages");
            }
            textTitleView.setText("View posts with " + hashtag);
        }
    }


}
