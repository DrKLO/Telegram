package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class RichCommand {

    private static ArrayList<RichCommand> cmds;
    public static ArrayList<RichCommand> get() {
        if (cmds != null) return cmds;

        cmds = new ArrayList<>();

        cmds.add(new RichCommand(R.drawable.iv_h1, getString(R.string.ArticleHeading1), "#", "/h1", "/header", "/title", "/heading"));
        cmds.add(new RichCommand(R.drawable.iv_h2, getString(R.string.ArticleHeading2), "##", "/h2"));
        cmds.add(new RichCommand(R.drawable.iv_h3, getString(R.string.ArticleHeading3), "###", "/h3"));
        cmds.add(new RichCommand(R.drawable.iv_h4, getString(R.string.ArticleHeading4), "####", "/h4"));
        cmds.add(new RichCommand(R.drawable.iv_h5, getString(R.string.ArticleHeading5), "#####", "/h5"));
        cmds.add(new RichCommand(R.drawable.iv_h6, getString(R.string.ArticleHeading6), "######", "/h6"));
        cmds.add(new RichCommand(R.drawable.iv_quote, getString(R.string.ArticleQuote), "|", "/quote"));
        cmds.add(new RichCommand(R.drawable.iv_pullquote, getString(R.string.ArticlePullquote), "/pullquote"));
        cmds.add(new RichCommand(R.drawable.iv_code, getString(R.string.ArticleCode), "```", "/code", "/pre", "/preformatted"));
        cmds.add(new RichCommand(R.drawable.iv_footer, getString(R.string.ArticleFooter), "/footer"));
        cmds.add(new RichCommand(R.drawable.iv_list, getString(R.string.ArticleCommandList), "-", "/list"));
        cmds.add(new RichCommand(R.drawable.iv_ordered_list, getString(R.string.ArticleCommandOrderedList), "1."));
        cmds.add(new RichCommand(R.drawable.iv_todo, getString(R.string.ArticleListChecklist), "[]", "/todo", "/checklist"));
        cmds.add(new RichCommand(R.drawable.iv_details, getString(R.string.ArticleCommandToggle), ">", "/toggle", "/details"));
        cmds.add(new RichCommand(R.drawable.iv_table, getString(R.string.ArticleCommandTable), "/table"));
        cmds.add(new RichCommand(R.drawable.iv_math, getString(R.string.ArticleCommandMath), "/math", "/latex", "/expression"));
        cmds.add(new RichCommand(R.drawable.iv_divider, getString(R.string.ArticleCommandDivider), "---"));
        cmds.add(new RichCommand(R.drawable.iv_media, getString(R.string.ArticleCommandImage), "/image", "/pic", "/picture", "/photo", "/img", "/media"));
        cmds.add(new RichCommand(R.drawable.iv_media, getString(R.string.ArticleCommandVideo), "/video", "/vid", "/media"));
        cmds.add(new RichCommand(R.drawable.iv_audio, getString(R.string.ArticleCommandAudio), "/audio", "/music", "/media"));
        cmds.add(new RichCommand(R.drawable.iv_location, getString(R.string.ArticleCommandMap), "/map", "/location", "/venue"));

        return cmds;
    }

    public static ArrayList<RichCommand> match(String query) {
        String q = query == null ? "" : query.trim();
        if (q.startsWith("/")) q = q.substring(1);
        q = q.toLowerCase();
        ArrayList<RichCommand> out = new ArrayList<>();
        for (RichCommand c : get()) {
            if (q.isEmpty() || c.matches(q)) out.add(c);
        }
        return out;
    }

    public final int icon;
    public final String name;
    public final List<String> commands;

    public RichCommand(int icon, String name, String ...commands) {
        this.icon = icon;
        this.name = name;
        this.commands = Arrays.asList(commands);
    }

    public boolean matches(String q) {
        for (String word : name.toLowerCase().split(" ")) {
            if (word.startsWith(q)) return true;
        }
        for (String cmd : commands) {
            String a = cmd.toLowerCase();
            if (a.startsWith("/")) a = a.substring(1);
            if (a.startsWith(q)) return true;
        }
        return false;
    }

    public static class View extends LinearLayout implements Theme.Colorable {

        private final Theme.ResourcesProvider resourcesProvider;
        private final RichCommand cmd;
        private final ImageView iconView;
        private final TextView textView, textView2;

        public View(Context context, RichCommand cmd, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.cmd = cmd;
            this.resourcesProvider = resourcesProvider;
            setOrientation(LinearLayout.HORIZONTAL);

            iconView = new ImageView(context);
            iconView.setScaleType(ImageView.ScaleType.CENTER);

            addView(iconView, LayoutHelper.createLinear(42, 42, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, 0, 0, 0));

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);

            textView2 = new TextView(context);
            textView2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView2.setGravity(Gravity.RIGHT);

            addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 8, 0, 0, 0));
            addView(new Space(context), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.FILL));
            addView(textView2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 8, 0, 0, 0));

            iconView.setImageResource(cmd.icon);
            textView.setText(cmd.name);
            textView2.setText(cmd.commands.get(0));

            updateColors();
        }

        @Override
        public void updateColors() {
            iconView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), PorterDuff.Mode.SRC_IN));
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            textView2.setTextColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider), .75f));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(dp(48), MeasureSpec.EXACTLY));
        }
    }

}
