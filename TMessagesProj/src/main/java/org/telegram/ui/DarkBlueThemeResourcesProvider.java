package org.telegram.ui;

import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Stories.DarkThemeResourceProvider;

public class DarkBlueThemeResourcesProvider extends DarkThemeResourceProvider {

    public DarkBlueThemeResourcesProvider() {
        super();

        sparseIntArray.put(Theme.key_windowBackgroundWhite, 0xff222b33);
        sparseIntArray.put(Theme.key_dialogBackground, 0xff222B33);
        sparseIntArray.put(Theme.key_windowBackgroundGray, 0xff303B47);
        sparseIntArray.put(Theme.key_graySection, 0xff28323B);
        sparseIntArray.put(Theme.key_graySectionText, 0xff848D94);
        sparseIntArray.put(Theme.key_groupcreate_spanBackground, 0xff28323B);

        sparseIntArray.put(Theme.key_actionBarDefaultSubmenuBackground, 0xFF303B47);
        sparseIntArray.put(Theme.key_actionBarDefaultSubmenuItemIcon, -1);
        sparseIntArray.put(Theme.key_actionBarDefaultSubmenuItem, -1);

        sparseIntArray.put(Theme.key_undo_background, -231982259);

        sparseIntArray.put(Theme.key_windowBackgroundWhiteBlueIcon, 0xFF4DB8FF);
        sparseIntArray.put(Theme.key_windowBackgroundWhiteBlueButton, 0xFF5DAFEE);

        sparseIntArray.put(Theme.key_checkboxSquareBackground, -12692893);
        sparseIntArray.put(Theme.key_checkbox, 0xFF1A9CFF);
    }

}
