package ua.itaysonlab.catogram.ui;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;

public class CatogramChatMenuInjector {
    public static void injectIntoActionbar(ActionBarMenu menu, ActionBarMenuItem headerItem, ActionBarMenuItem searchItem, int search) {
        if (searchItem != null) {
            headerItem.addSubItem(search, R.drawable.msg_search, LocaleController.getString("Search", R.string.Search));
        }
    }
}
