package ua.itaysonlab.catogram.ui;

import android.content.SharedPreferences;
import android.widget.Toast;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;

public class CatogramToasts {
    private static boolean shownUnpinHint = false;

    static void shownUnpinHint() {
        shownUnpinHint = true;
        putBoolean("cg_toasts_unpin", true);
    }

    public static void notifyAboutUnpin() {
        //if (shownUnpinHint) return;
        Toast.makeText(ApplicationLoader.applicationContext, "You can show pinned message again by long-clicking on chat's avatar.", Toast.LENGTH_SHORT).show();
        shownUnpinHint();
    }

    public static void init(SharedPreferences preferences) {
        shownUnpinHint = preferences.getBoolean("cg_toasts_unpin", false);
    }

    private static void putBoolean(String key, boolean value) {
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(key, value);
        editor.commit();
    }
}
