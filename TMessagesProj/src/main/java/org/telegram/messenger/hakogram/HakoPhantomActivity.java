package org.telegram.messenger.hakogram;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

/**
 * Режим фантома - Phantom mode
 * Shows item "Фантом" that expands to 4 toggles.
 * Each toggle persists via HakoConfig and actually controls behavior via guards in MessagesController/StoriesController.
 */
public class HakoPhantomActivity extends BaseFragment {

    private UniversalRecyclerView listView;

    @Override
    public boolean onFragmentCreate() {
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Режим фантома");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = listView;
        return fragmentView;
    }

    private void fillItems(java.util.ArrayList<UItem> items, UniversalAdapter adapter) {
        boolean collapsed = HakoConfig.isPhantomCollapsed();

        items.add(UItem.asHeader("Фантом"));

        // Main expandable row - using button with collapse indication via shadow collapse button or custom?
        // We use asButton for "Фантом" with click to toggle collapsed state.
        // Visual indicator via icon: we alternate icon based on collapsed.
        // For Telegram native look, we add a shadow collapse button helper if available, but simpler to use Button.
        // We'll add a Check-like row that actually is expandable indicator.
        // Use UItem.asButton with value indicating expanded/collapsed
        String arrow = collapsed ? "▼" : "▲";
        items.add(UItem.asButton(10, "Фантом " + arrow));

        if (!collapsed) {
            items.add(UItem.asCheck(101, "Не читать сообщения").setChecked(HakoConfig.isNoReadMessages()));
            items.add(UItem.asCheck(102, "Не читать истории").setChecked(HakoConfig.isNoReadStories()));
            items.add(UItem.asCheck(103, "Не палить онлайн").setChecked(HakoConfig.isNoOnline()));
            items.add(UItem.asCheck(104, "Не палить «печатает» и другие индикаторы активности").setChecked(HakoConfig.isNoTyping()));
        }

        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader("Настройки сохраняются автоматически"));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 10) {
            boolean cur = HakoConfig.isPhantomCollapsed();
            HakoConfig.setPhantomCollapsed(!cur);
            if (listView != null) {
                ((UniversalRecyclerView) fragmentView).adapter.update(true);
            } else if (fragmentView instanceof UniversalRecyclerView) {
                ((UniversalRecyclerView) fragmentView).adapter.update(true);
            }
            return;
        }
        // handle toggles - UniversalAdapter will toggle checked via click?
        // For UItem.asCheck, click toggles checked state and we need to persist.
        switch (item.id) {
            case 101:
                HakoConfig.setNoReadMessages(!HakoConfig.isNoReadMessages());
                item.checked = HakoConfig.isNoReadMessages();
                break;
            case 102:
                HakoConfig.setNoReadStories(!HakoConfig.isNoReadStories());
                item.checked = HakoConfig.isNoReadStories();
                break;
            case 103:
                HakoConfig.setNoOnline(!HakoConfig.isNoOnline());
                item.checked = HakoConfig.isNoOnline();
                break;
            case 104:
                HakoConfig.setNoTyping(!HakoConfig.isNoTyping());
                item.checked = HakoConfig.isNoTyping();
                break;
        }
        // refresh
        if (fragmentView instanceof UniversalRecyclerView) {
            ((UniversalRecyclerView) fragmentView).adapter.update(true);
        }
    }
}
