/* Local development host; uses Telegram's real theme-preview UI with sample data.
 * Licensed under GNU GPL v2 or later, like the surrounding Telegram source. */
package org.telegram.features;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ThemePreviewActivity;

import java.util.ArrayList;

/** No simulated login or account data. The offline manifest removes INTERNET. */
public final class FeaturesLabActivity extends Activity {
    private ActionBarLayout navigation;

    @Override
    protected void onCreate(Bundle state) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setTheme(R.style.Theme_TMessages);
        super.onCreate(state);
        ApplicationLoader.postInitApplication();
        AndroidUtilities.checkDisplaySize(this, getResources().getConfiguration());
        AndroidUtilities.fillStatusBarHeight(this, false);
        Theme.createCommonChatResources();
        Theme.createChatResources(this, false);
        Theme.createDialogsResources(this);
        navigation = new ActionBarLayout(this, false);
        navigation.setFragmentStack(new ArrayList<>());
        setContentView(navigation);
        navigation.presentFragment(new Playground());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (navigation != null) navigation.onResume();
    }

    @Override
    protected void onPause() {
        if (navigation != null) navigation.onPause();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        if (navigation != null && navigation.getFragmentStack().size() > 1) {
            navigation.onBackPressed();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        AndroidUtilities.checkDisplaySize(this, configuration);
        if (navigation != null) navigation.onConfigurationChanged(configuration);
    }

    @Override
    protected void onDestroy() {
        if (navigation != null) navigation.removeAllFragments();
        super.onDestroy();
    }

    /** Add future feature previews here, reusing the same components as the live app. */
    public static final class Playground extends BaseFragment {
        private LinearLayout content;
        private TextView appearance;

        @Override
        public View createView(Context context) {
            actionBar.setTitle("Features Lab");
            actionBar.setSubtitle("Offline playground");
            ScrollView scroll = new ScrollView(context);
            scroll.setFillViewport(true);
            content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            int padding = AndroidUtilities.dp(24);
            content.setPadding(padding, padding, padding, padding);
            scroll.addView(content);
            text(context, "Build your next Telegram idea", 26);
            text(context, "No account. No phone. Sample data stays on this virtual device.", 16);
            text(context, "Explore Telegram's actual chat list, message bubbles, and theme components. Swipe between the two preview pages.", 16);
            button(context, "Open Stars Privacy preview", () -> presentFragment(new StarsPrivacyPreview()));
            button(context, "Open chat & message preview", () -> presentFragment(new ThemePreviewActivity(Theme.getCurrentTheme())));
            appearance = text(context, "", 14);
            button(context, "Use light theme", () -> selectTheme("Blue"));
            button(context, "Use dark theme", () -> selectTheme("Dark Blue"));
            text(context, "This is a UI preview, not a signed-in Telegram session. Sending messages, calls, sync, and server features are unavailable here.", 14);
            fragmentView = scroll;
            recolor();
            return fragmentView;
        }

        private TextView text(Context context, String value, int size) {
            TextView view = new TextView(context);
            view.setText(value);
            view.setTextSize(size);
            view.setPadding(0, 0, 0, AndroidUtilities.dp(20));
            content.addView(view, new LinearLayout.LayoutParams(-1, -2));
            return view;
        }

        private void button(Context context, String label, Runnable action) {
            Button button = new Button(context);
            button.setText(label);
            button.setAllCaps(false);
            button.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(56));
            params.bottomMargin = AndroidUtilities.dp(12);
            content.addView(button, params);
            button.setOnClickListener(v -> action.run());
        }

        private void selectTheme(String key) {
            Theme.ThemeInfo theme = Theme.getTheme(key);
            if (theme == null) return;
            Theme.applyTheme(theme);
            recolor();
        }

        private void recolor() {
            if (content == null) return;
            fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            for (int i = 0; i < content.getChildCount(); i++) {
                View child = content.getChildAt(i);
                if (child instanceof TextView) {
                    ((TextView) child).setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                }
                if (child instanceof Button) {
                    child.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), Theme.getColor(Theme.key_windowBackgroundGray)));
                }
            }
            appearance.setText("Current theme: " + Theme.getCurrentThemeName());
            actionBar.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault));
            actionBar.setTitleColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
            actionBar.setSubtitleColor(Theme.getColor(Theme.key_actionBarDefaultSubtitle));
        }

        @Override
        public void onResume() {
            super.onResume();
            recolor();
        }
    }
}
