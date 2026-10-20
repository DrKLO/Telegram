package org.telegram.messenger.hakogram;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

/**
 * HakoGram Categories screen - "Категории"
 * Shows 5 categories vertically in native Telegram style.
 */
public class HakoCategoriesActivity extends BaseFragment {

    private UniversalRecyclerView listView;

    @Override
    public boolean onFragmentCreate() {
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Категории");
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
        items.add(UItem.asHeader("HakoGram"));
        items.add(UItem.asButton(1, R.drawable.settings_privacy, "Режим фантома"));
        items.add(UItem.asButton(2, R.drawable.settings_devices, "Режим слежки"));
        items.add(UItem.asButton(3, R.drawable.settings_data, "Удалёнки"));
        items.add(UItem.asButton(4, R.drawable.settings_power, "Контроль"));
        items.add(UItem.asButton(5, R.drawable.settings_faq, "Докс (beta)"));
        items.add(UItem.asShadow(null));
        // info footer: use header as placeholder for info text via shadow
        items.add(UItem.asHeader("Выберите категорию для настройки"));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case 1:
                presentFragment(new HakoPhantomActivity());
                break;
            case 2:
                presentFragment(new HakoSlezhkaActivity());
                break;
            case 3:
            case 4:
            case 5:
                showInDevDialog();
                break;
        }
    }

    private void showInDevDialog() {
        if (getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("HakoGram");
        builder.setMessage("В разработке, будет в следующей версии");
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        showDialog(builder.create());
    }

    @Override
    public boolean isLightStatusBar() {
        return true;
    }
}
