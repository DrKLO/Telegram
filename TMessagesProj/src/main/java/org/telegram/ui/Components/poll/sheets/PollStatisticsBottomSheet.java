package org.telegram.ui.Components.poll.sheets;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.tl.TL_stats;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BottomSheetWithRecyclerListView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.StatisticActivity;

import java.util.ArrayList;

public class PollStatisticsBottomSheet extends BottomSheetWithRecyclerListView {

    private UniversalAdapter adapter;
    private final StatisticActivity.ChartViewData chartViewData;

    public PollStatisticsBottomSheet(Context context, Theme.ResourcesProvider resourcesProvider, TL_stats.TL_statsPollStats stats) {
        super(context, null, true, false, false, false, ActionBarType.SLIDING, resourcesProvider);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        occupyNavigationBar = true;
        drawNavigationBar = false;
        ignoreTouchActionBar = false;
        headerMoveTop = dp(12);

        chartViewData = StatisticActivity.createViewData(stats.votes_graph, getString(R.string.PollV2StatsVoteTimeline), 2);

        recyclerListView.setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, AndroidUtilities.navigationBarHeight);
        recyclerListView.setClipToPadding(false);
        recyclerListView.setSections(true);

        ActionBarMenu m = actionBar.createMenu();
        m.addItem(-1, R.drawable.ic_close_white);
        m.setTranslationX(-dp(5));

        adapter.update(false);
    }

    public static int loadStatistics(int currentAccount, long dialogId, int messageId, Utilities.Callback<TL_stats.TL_statsPollStats> onResult) {
        final TL_stats.TL_statsGetPollStats req = new TL_stats.TL_statsGetPollStats();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        req.msg_id = messageId;
        return ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (res, err) -> {
            onResult.run(res);
        });
    }

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.PollV2StatsPollStats);
    }

    @Override
    protected RecyclerListView.SelectionAdapter createAdapter(RecyclerListView listView) {
        adapter = new UniversalAdapter(listView, getContext(), currentAccount, 0, true, this::fillItems, resourcesProvider);
        adapter.setApplyBackground(false);
        return adapter;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (chartViewData != null) {
            items.add(UItem.asSpace(dp(12)));
            items.add(UItem.asChart(StatisticActivity.VIEW_TYPE_LINEAR, 0, chartViewData));
        }
    }
}
