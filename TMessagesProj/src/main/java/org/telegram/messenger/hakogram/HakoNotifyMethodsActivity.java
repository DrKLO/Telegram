package org.telegram.messenger.hakogram;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.View;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.HashSet;
import java.util.Set;

/**
 * Способ получения уведомлений - 4 variants with multi-select and Save.
 */
public class HakoNotifyMethodsActivity extends BaseFragment {

    private UniversalRecyclerView listView;
    private Set<Integer> selectedMethods;

    @Override
    public boolean onFragmentCreate() {
        selectedMethods = new HashSet<>(HakoConfig.getSlezkaNotifyMethods());
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Способ получения уведомлений");
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
        items.add(UItem.asHeader("Выберите способы уведомления"));
        items.add(UItem.asCheck(1, "SMS").setChecked(selectedMethods.contains(1)));
        items.add(UItem.asCheck(2, "Telegram внутри приложения").setChecked(selectedMethods.contains(2)));
        items.add(UItem.asCheck(3, "Вибрация").setChecked(selectedMethods.contains(3)));
        items.add(UItem.asCheck(4, "Фонарик").setChecked(selectedMethods.contains(4)));
        items.add(UItem.asShadow(null));
        items.add(UItem.asButton(999, "Сохранить"));
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader("SMS требует разрешения на отправку сообщений. Фонарик — доступ к камере. Запрос будет показан при выборе."));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == 999) {
            HakoConfig.setSlezkaNotifyMethods(selectedMethods);
            finishFragment();
            return;
        }
        if (item.id >= 1 && item.id <= 4) {
            boolean willEnable = !selectedMethods.contains(item.id);
            if (willEnable) {
                // check permissions before enabling
                if (item.id == 1) {
                    // SMS
                    if (!hasPermission(Manifest.permission.SEND_SMS)) {
                        showPermissionExplainAndRequest("Для отправки SMS-уведомлений приложению нужно разрешение на отправку SMS. Разрешить?", new String[]{Manifest.permission.SEND_SMS}, item.id);
                        return;
                    }
                } else if (item.id == 3) {
                    if (!hasPermission(Manifest.permission.VIBRATE)) {
                        // VIBRATE is normal permission, granted at install, but check anyway
                    }
                } else if (item.id == 4) {
                    if (!hasPermission(Manifest.permission.CAMERA)) {
                        showPermissionExplainAndRequest("Для управления фонариком нужен доступ к камере. Разрешить?", new String[]{Manifest.permission.CAMERA}, item.id);
                        return;
                    }
                }
                selectedMethods.add(item.id);
            } else {
                selectedMethods.remove(item.id);
            }
            if (fragmentView instanceof UniversalRecyclerView) {
                ((UniversalRecyclerView) fragmentView).adapter.update(true);
            }
        }
    }

    private boolean hasPermission(String perm) {
        if (getParentActivity() == null) return false;
        return ContextCompat.checkSelfPermission(getParentActivity(), perm) == PackageManager.PERMISSION_GRANTED;
    }

    private void showPermissionExplainAndRequest(String message, String[] perms, int methodId) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle("Разрешение");
        b.setMessage(message);
        b.setPositiveButton("Разрешить", (d, w) -> {
            try {
                getParentActivity().requestPermissions(perms, 1000 + methodId);
                // optimistic: add method after request; if denied, user can retry and we handle denial silently
                selectedMethods.add(methodId);
                if (fragmentView instanceof UniversalRecyclerView) {
                    ((UniversalRecyclerView) fragmentView).adapter.update(true);
                }
            } catch (Exception ignored) {}
        });
        b.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(b.create());
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        // For methods 1 and 4, if denied we remove from selected
        if (requestCode >= 1001 && requestCode <= 1004) {
            int methodId = requestCode - 1000;
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                selectedMethods.remove(methodId);
                AndroidUtilities.runOnUIThread(() -> {
                    if (fragmentView instanceof UniversalRecyclerView) {
                        ((UniversalRecyclerView) fragmentView).adapter.update(true);
                    }
                    AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
                    b.setTitle("Разрешение не получено");
                    b.setMessage("Функция не будет работать без разрешения.");
                    b.setPositiveButton(LocaleController.getString(R.string.OK), null);
                    showDialog(b.create());
                });
            } else {
                // already added
            }
        }
        super.onRequestPermissionsResultFragment(requestCode, permissions, grantResults);
    }
}
