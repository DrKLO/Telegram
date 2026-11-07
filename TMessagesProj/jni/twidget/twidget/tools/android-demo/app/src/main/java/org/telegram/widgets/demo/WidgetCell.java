package org.telegram.widgets.demo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;
import org.telegram.widgets.WidgetHostNative;
import org.telegram.widgets.WidgetRenderer;

import java.util.Locale;

/**
 * One widget in the feed, as a message bubble would hold it: its name, the widget at the bubble's width and the
 * height it reports, and a line of diagnostics. Owns the widget's host and renderer from start() to destroy().
 */
@SuppressLint("ViewConstructor")
public class WidgetCell extends FrameLayout {

    private final GradientDrawable bubble = new GradientDrawable();
    private final TextView title;
    private final TextView status;
    private final WidgetRenderer renderer;
    private long host;
    private boolean onScreen = true;
    private String lastDiagnostics = "";

    public WidgetCell(Context context, String name, String html, double widthDp, String resourcesDir) {
        super(context);
        bubble.setCornerRadius(AndroidUtilities.dp(16));
        setBackground(bubble);
        setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(8), AndroidUtilities.dp(10), AndroidUtilities.dp(8));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, 0, 0, 0));

        title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setText(name);
        column.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        renderer = new WidgetRenderer(context, Theme.widgetTheme(), widthDp);
        column.addView(renderer.getView(), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

        status = new TextView(context);
        status.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        column.addView(status, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));

        updateColors();
        host = WidgetHostNative.create(html, Theme.widgetTheme(), widthDp, AndroidUtilities.density, resourcesDir, renderer.bridge(),
            json -> AndroidUtilities.runOnUIThread(() -> onDiagnostics(json)));
        if (host == 0) {
            status.setText("the widget host failed to start: the WidgetJS bundles?");
            renderer.release();
        } else {
            renderer.attach(host);
        }
    }

    public void start() {
        if (host != 0) {
            WidgetHostNative.start(host);
        }
    }

    public void setDark(boolean dark) {
        updateColors();
        if (host != 0) {
            WidgetHostNative.setTheme(host, Theme.widgetTheme());
        }
    }

    /** Off screen the widget pauses and lets go of its caches. */
    public void setOnScreen(boolean visible) {
        if (onScreen == visible || host == 0) {
            return;
        }
        onScreen = visible;
        WidgetHostNative.setVisible(host, visible);
        renderer.setVisible(visible);
    }

    public void destroy() {
        if (host != 0) {
            // The renderer stops talking to the host first: the host is freed when destroy returns.
            renderer.detach();
            WidgetHostNative.destroy(host);
            host = 0;
        }
        renderer.release();
    }

    private void updateColors() {
        bubble.setColor(Theme.bubbleIn());
        title.setTextColor(Theme.bubbleText());
        status.setTextColor(Theme.bubbleSecondary());
    }

    private void onDiagnostics(String json) {
        if (host == 0 || json.equals(lastDiagnostics)) {
            return;
        }
        lastDiagnostics = json;
        try {
            JSONObject d = new JSONObject(json);
            if (d.has("dead")) {
                status.setText("stopped: " + d.optString("dead"));
                return;
            }
            StringBuilder text = new StringBuilder();
            text.append(d.has("loadedMs") ? String.format(Locale.US, "loaded %.0f ms", d.optDouble("loadedMs")) : "loading");
            text.append(String.format(Locale.US, " | js %.0f ms | ops %d", d.optDouble("jsMs"), d.optInt("ops")));
            if (d.has("error")) {
                text.append(" | error: ").append(d.optString("error"));
            }
            status.setText(text);
        } catch (Exception ignore) {
        }
    }
}
