package org.telegram.ui.Components;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Delegates.MemberRequestsDelegate;
import org.telegram.ui.LaunchActivity;

public class MemberRequestsBottomSheet extends UsersAlertBase {

    private final int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
    private final MemberRequestsDelegate delegate;
    private final FlickerLoadingView currentLoadingView;
    private final StickerEmptyView membersEmptyView;
    private final StickerEmptyView membersSearchEmptyView;

    private float yOffset;
    private boolean enterEventSent;

    public MemberRequestsBottomSheet(BaseFragment fragment, long chatId) {
        super(fragment.getParentActivity(), false, fragment.getCurrentAccount(), fragment.getResourceProvider());
        this.needSnapToTop = false;
        this.isEmptyViewVisible = false;
        this.delegate = new MemberRequestsDelegate(fragment, container, chatId, false) {
            @Override
            protected void onImportersChanged(String query, boolean fromCache, boolean fromHide) {
                if (!hasAllImporters()) {
                    if (membersEmptyView.getVisibility() != View.INVISIBLE) {
                        membersEmptyView.setVisibility(View.INVISIBLE);
                    }
//                    dismiss();
                } else if (fromHide) {
                    searchView.searchEditText.setText("");
                } else {
                    super.onImportersChanged(query, fromCache, fromHide);
                }
            }
        };
        this.delegate.setShowLastItemDivider(false);
        setDimBehindAlpha(75);

        searchView.searchEditText.setHint(LocaleController.getString(R.string.SearchMemberRequests));

        searchListViewAdapter = listViewAdapter = delegate.getAdapter();
        listView.setAdapter(listViewAdapter);
        delegate.setRecyclerView(listView);

        int position = ((ViewGroup) listView.getParent()).indexOfChild(listView);
        currentLoadingView = delegate.getLoadingView();
        containerView.addView(currentLoadingView, position, LayoutHelper.createFrame(MATCH_PARENT, MATCH_PARENT));

        membersEmptyView = delegate.getEmptyView();
        containerView.addView(membersEmptyView, position, LayoutHelper.createFrame(MATCH_PARENT, MATCH_PARENT));

        membersSearchEmptyView = delegate.getSearchEmptyView();
        containerView.addView(membersSearchEmptyView, position, LayoutHelper.createFrame(MATCH_PARENT, MATCH_PARENT));

        delegate.loadMembers();
    }

    @Override
    public void show() {
        if (delegate.isNeedRestoreList && scrollOffsetY == 0) {
            scrollOffsetY = AndroidUtilities.dp(8);
        }
        super.show();
        delegate.isNeedRestoreList = false;
    }

    @Override
    public void onBackPressed() {
        if (delegate.onBackPressed(true)) {
            super.onBackPressed();
        }
    }

    public boolean isNeedRestoreDialog() {
        return delegate.isNeedRestoreList;
    }

    @Override
    protected void setTranslationY(int newOffset) {
        super.setTranslationY(newOffset);
        currentLoadingView.setTranslationY(newOffset + frameLayout.getMeasuredHeight());
        membersEmptyView.setTranslationY(newOffset);
        membersSearchEmptyView.setTranslationY(newOffset);
    }

    @Override
    protected void updateLayout() {
        if (listView.getChildCount() <= 0) {
            int newOffset = listView.getVisibility() == View.VISIBLE
                    ? listView.getPaddingTop() - AndroidUtilities.dp(8)
                    : 0;
            if (scrollOffsetY != newOffset) {
                scrollOffsetY = newOffset;
                setTranslationY(newOffset);
            }
        } else {
            super.updateLayout();
        }
    }

    @Override
    protected void search(String text) {
        super.search(text);
        delegate.setQuery(text);
    }

    @Override
    protected void onSearchViewTouched(MotionEvent ev, EditTextBoldCursor searchEditText) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            yOffset = scrollOffsetY;
            delegate.setAdapterItemsEnabled(false);
        } else if (ev.getAction() == MotionEvent.ACTION_UP && Math.abs(scrollOffsetY - yOffset) < touchSlop) {
            if (!enterEventSent) {
                Activity activity = AndroidUtilities.findActivity(getContext());
                BaseFragment fragment = null;
                if (activity instanceof LaunchActivity) {
                    fragment = ((LaunchActivity) activity).getActionBarLayout().getFragmentStack().get(((LaunchActivity) activity).getActionBarLayout().getFragmentStack().size() - 1);
                }
                if (fragment instanceof ChatActivity) {
                    boolean keyboardVisible = ((ChatActivity) fragment).needEnterText();
                    enterEventSent = true;
                    AndroidUtilities.runOnUIThread(() -> {
                        setFocusable(true);
                        searchEditText.requestFocus();
                        AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(searchEditText));
                    }, keyboardVisible ? 200 : 0);
                } else {
                    enterEventSent = true;
                    setFocusable(true);
                    searchEditText.requestFocus();
                    AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(searchEditText));
                }
            }
        }
        if (ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {
            delegate.setAdapterItemsEnabled(true);
        }
    }
}
