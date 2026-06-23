package org.Tajgram.ui.ActionBar.theme;

import org.Tajgram.tgnet.TLRPC;

public interface ITheme {
    long getThemeId();

    TLRPC.ThemeSettings getThemeSettings(int settingsIndex);
    TLRPC.WallPaper getThemeWallPaper(int settingsIndex);
}
