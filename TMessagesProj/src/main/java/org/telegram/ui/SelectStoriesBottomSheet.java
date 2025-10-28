package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.SharedPhotoVideoCell2;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.ExtendedGridLayoutManager;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Stories.StoriesController;
import org.telegram.ui.Stories.recorder.ButtonWithCounterView;

import java.util.ArrayList;
import java.util.HashMap;

public class SelectStoriesBottomSheet extends BottomSheetWithRecyclerListView implements NotificationCenter.NotificationCenterDelegate {
    private final ExtendedGridLayoutManager layoutManager;

    private final FrameLayout buttonContainer;
    private final ButtonWithCounterView button;

    private final StoriesController.StoriesList storiesList;
    private final HashMap<Integer, TL_stories.StoryItem> selectedStoriesIds = new HashMap<>();

    private final long dialogId;
    private final int columnsCount;

    public SelectStoriesBottomSheet(
            BaseFragment fragment,
            long dialogId,
            int columnsCount,
            Utilities.Callback<ArrayList<TL_stories.StoryItem>> whenSelected
    ) {
        super(fragment, false, false, ActionBarType.SLIDING);

        this.dialogId = dialogId;
        this.columnsCount = columnsCount;

        storiesList = MessagesController.getInstance(fragment.getCurrentAccount()).getStoriesController().getStoriesList(dialogId, StoriesController.StoriesList.TYPE_ARCHIVE);
        storiesList.load(false, 30);

        headerMoveTop = dp(12);

        fixNavigationBar();
        setSlidingActionBar();

        buttonContainer = new FrameLayout(getContext());
        buttonContainer.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        buttonContainer.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
        containerView.addView(buttonContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 0, 0, 0, 0));

        View buttonShadow = new View(getContext());
        buttonShadow.setBackgroundColor(Theme.getColor(Theme.key_divider, resourcesProvider));
        buttonContainer.addView(buttonShadow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 1.0f / AndroidUtilities.density, Gravity.FILL_HORIZONTAL | Gravity.TOP));

        button = new ButtonWithCounterView(getContext(), resourcesProvider);
        button.setText(getString(R.string.StoriesAlbumMenuAddStories), false);
        button.setEnabled(false);
        button.setOnClickListener(v -> {
            if (storiesList.getCount() == 0) return;

            whenSelected.run(new ArrayList<>(selectedStoriesIds.values()));
            dismiss();
        });
        buttonContainer.addView(button, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL, 10, 10 + (1.0f / AndroidUtilities.density), 10, 10));

        layoutManager = new ExtendedGridLayoutManager(getContext(), columnsCount);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (adapter == null)
                    return layoutManager.getSpanCount();
                final UItem item = adapter.getItem(position - 1);
                if (item == null || item.spanCount == UItem.MAX_SPAN_COUNT)
                    return layoutManager.getSpanCount();
                return item.spanCount;
            }
        });
        this.recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
        this.recyclerListView.setSelectorType(9);
        this.recyclerListView.setSelectorDrawableColor(0);
        this.recyclerListView.setLayoutManager(layoutManager);
        this.recyclerListView.setOnItemClickListener(this::onItemClick);
        this.recyclerListView.setOnItemLongClickListener(this::onItemClick);
        this.recyclerListView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                checkLoadMoreScroll();
            }
        });

        adapter.update(true);
    }

    private int id;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        id = storiesList.link();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.storiesListUpdated);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        storiesList.unlink(id);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.storiesListUpdated);
    }

    private boolean onItemClick(View view, int position) {
        if (adapter == null)
            return false;

        if (position == 0) {
            return false;
        }

        final UItem item = adapter.getItem(position - 1);
        if (item == null) {
            return false;
        }

        if (item.object instanceof MessageObject) {
            MessageObject g = (MessageObject) item.object;
            final int id = g.getId();
            if (selectedStoriesIds.containsKey(id)) {
                selectedStoriesIds.remove(id);
                ((SharedPhotoVideoCell2) view).setChecked(item.checked = false, true);
            } else {
                selectedStoriesIds.put(id, g.storyItem);
                ((SharedPhotoVideoCell2) view).setChecked(item.checked = true, true);
            }

            button.setEnabled(!selectedStoriesIds.isEmpty());
            button.setCount(selectedStoriesIds.size(), true);
        }

        return true;
    }


    @Override
    protected CharSequence getTitle() {
        return getString(R.string.StoriesAlbumMenuAddStories);
    }

    private UniversalAdapter adapter;

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, this::fillItems, resourcesProvider);
        adapter.setApplyBackground(false);
        return adapter;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (storiesList == null) return;

        items.add(UItem.asSpace(dp(16)));

        int spanCountLeft = columnsCount;
        for (MessageObject g : storiesList.messageObjects) {

            items.add(StoryCellFactory.asStory(0, g, columnsCount, true)
                    .setChecked(selectedStoriesIds.containsKey(g.getId()))
                    .setSpanCount(1)
            );
            spanCountLeft--;
            if (spanCountLeft == 0) {
                spanCountLeft = columnsCount;
            }
        }

        if (storiesList.isLoading() || !storiesList.isFull()) {
            for (int i = 0; i < (spanCountLeft <= 0 ? columnsCount : spanCountLeft); ++i) {
                items.add(UItem.asFlicker(1 + i, FlickerLoadingView.STAR_GIFT).setSpanCount(1));
            }
        }

        items.add(UItem.asSpace(dp(10 + 48 + 10)));
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.storiesListUpdated) {
            StoriesController.StoriesList list = (StoriesController.StoriesList) args[0];
            if (list == storiesList) {
                adapter.update(false);
                checkLoadMoreScroll();
            }
        }
    }

    private void checkLoadMoreScroll() {
        final int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
        final int visibleItemCount = firstVisibleItem == RecyclerView.NO_POSITION ? 0 : Math.abs(layoutManager.findLastVisibleItemPosition() - firstVisibleItem) + 1;

        if (storiesList != null && firstVisibleItem + visibleItemCount > storiesList.getLoadedCount() - columnsCount) {
            final int count = Math.min(100, Math.max(1, columnsCount / 2) * columnsCount * columnsCount);
            storiesList.load(false, count);
        }
    }
}
