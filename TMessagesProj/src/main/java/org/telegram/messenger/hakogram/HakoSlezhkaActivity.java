package org.telegram.messenger.hakogram;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

/**
 * Режим слежки main screen
 */
public class HakoSlezhkaActivity extends BaseFragment {

    private UniversalRecyclerView listView;

    @Override
    public boolean onFragmentCreate() {
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Режим слежки");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = listView;
        return fragmentView;
    }

    private void fillItems(java.util.ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("Слежка"));

        items.add(UItem.asCheck(201, "Смотреть в каких чатах находится пользователь").setChecked(HakoConfig.isSlezkaShowChats()));
        items.add(UItem.asCheck(202, "Сохранять последний онлайн пользователя").setChecked(HakoConfig.isSlezkaSaveLastOnline()));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader("Уведомления"));
        items.add(UItem.asCheck(203, "Уведомлять меня когда пользователь совершает определённые действия")
                .setChecked(HakoConfig.isSlezkaNotifyEnabled()));

        if (HakoConfig.isSlezkaNotifyEnabled()) {
            // sub toggles with pad indentation
            items.add(UItem.asCheck(211, "Прочитал сообщения").setChecked(HakoConfig.isSlezkaNotifyRead()).setPad(1));
            items.add(UItem.asCheck(212, "Появился в онлайне").setChecked(HakoConfig.isSlezkaNotifyOnline()).setPad(1));
            items.add(UItem.asCheck(213, "Печатает или отправляет сообщения").setChecked(HakoConfig.isSlezkaNotifyTyping()).setPad(1));
            items.add(UItem.asCheck(214, "Вышел из онлайна").setChecked(HakoConfig.isSlezkaNotifyOffline()).setPad(1));
            items.add(UItem.asCheck(215, "Заблокировал/разблокировал").setChecked(HakoConfig.isSlezkaNotifyBlock()).setPad(1));

            // two buttons with summaries
            String selectedUsersSummary = HakoConfig.getSlezkaSelectedUsersCsv().isEmpty() ? "Не выбрано" : HakoConfig.getSlezkaSelectedUserIds().size() + " выбрано";
            // Try to get names if possible
            try {
                String names = HakoConfig.getSlezkaSelectedUsersSummary(getContext(), currentAccount);
                if (names != null && !names.isEmpty()) selectedUsersSummary = names;
            } catch (Exception ignored) {}

            String methodsSummary = HakoConfig.getSlezkaNotifyMethodsSummary();
            if (methodsSummary.isEmpty()) methodsSummary = "Не выбрано";

            items.add(UItem.asButton(221, "Выбрать человека/людей", selectedUsersSummary));
            items.add(UItem.asButton(222, "Способ получения уведомлений", methodsSummary));
        }

        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader("Настройки сохраняются автоматически. Для уведомлений выберите пользователей и способ оповещения."));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case 201:
                HakoConfig.setSlezkaShowChats(!HakoConfig.isSlezkaShowChats());
                break;
            case 202:
                HakoConfig.setSlezkaSaveLastOnline(!HakoConfig.isSlezkaSaveLastOnline());
                break;
            case 203:
                HakoConfig.setSlezkaNotifyEnabled(!HakoConfig.isSlezkaNotifyEnabled());
                break;
            case 211:
                HakoConfig.setSlezkaNotifyRead(!HakoConfig.isSlezkaNotifyRead());
                break;
            case 212:
                HakoConfig.setSlezkaNotifyOnline(!HakoConfig.isSlezkaNotifyOnline());
                break;
            case 213:
                HakoConfig.setSlezkaNotifyTyping(!HakoConfig.isSlezkaNotifyTyping());
                break;
            case 214:
                HakoConfig.setSlezkaNotifyOffline(!HakoConfig.isSlezkaNotifyOffline());
                break;
            case 215:
                HakoConfig.setSlezkaNotifyBlock(!HakoConfig.isSlezkaNotifyBlock());
                break;
            case 221:
                presentFragment(new HakoUserSelectActivity());
                return;
            case 222:
                presentFragment(new HakoNotifyMethodsActivity());
                return;
            default:
                return;
        }
        // refresh list after toggle
        if (fragmentView instanceof UniversalRecyclerView) {
            ((UniversalRecyclerView) fragmentView).adapter.update(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // refresh summaries when returning from sub-screens
        if (fragmentView instanceof UniversalRecyclerView) {
            ((UniversalRecyclerView) fragmentView).adapter.update(true);
        }
    }
}
