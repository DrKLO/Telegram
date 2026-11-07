package org.telegram.widgets.demo;

import android.app.Activity;
import android.content.res.AssetManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * widget-sandbox's corpus in a chat-like feed: each widget in a bubble, run by widget-sandbox's C++ WidgetHost and
 * drawn by TwidgetCore. The assets hold the corpus and the WidgetJS bundles; the bundles are copied out to the
 * files directory, where the host reads them.
 */
public class LaunchActivity extends Activity {

    private static final String CORPUS = "corpus";
    private static final String WIDGET_JS = "WidgetJS";

    private final ArrayList<WidgetCell> cells = new ArrayList<>();
    private FrameLayout header;
    private ScrollView scroll;
    private LinearLayout feed;
    private String resourcesDir;
    private String folder;
    private final Rect visible = new Rect();

    private final ViewTreeObserver.OnScrollChangedListener scrollListener = this::updateOnScreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AndroidUtilities.init(this);
        resourcesDir = copyAssets(WIDGET_JS);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        header = new FrameLayout(this);
        root.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56, 0, 0, 0, 0));
        TextView title = new TextView(this);
        title.setText("Widgets");
        title.setTextColor(0xffffffff);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        header.addView(title, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(controls, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, Gravity.END, 0, 0, 8, 0));

        String[] folders = corpusFolders();
        Spinner picker = new Spinner(this);
        picker.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, folders));
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                showFolder(folders[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        controls.addView(picker, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

        CheckBox dark = new CheckBox(this);
        dark.setText("Dark");
        dark.setTextColor(0xffffffff);
        dark.setOnCheckedChangeListener((button, checked) -> setDark(checked));
        controls.addView(dark, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

        scroll = new ScrollView(this);
        feed = new LinearLayout(this);
        feed.setOrientation(LinearLayout.VERTICAL);
        feed.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        scroll.addView(feed, new ScrollView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
        scroll.getViewTreeObserver().addOnScrollChangedListener(scrollListener);

        setContentView(root);
        updateColors();
    }

    @Override
    protected void onDestroy() {
        scroll.getViewTreeObserver().removeOnScrollChangedListener(scrollListener);
        clearFeed();
        super.onDestroy();
    }

    private void setDark(boolean dark) {
        Theme.dark = dark;
        updateColors();
        for (WidgetCell cell : cells) {
            cell.setDark(dark);
        }
    }

    private void updateColors() {
        header.setBackgroundColor(Theme.header());
        scroll.setBackgroundColor(Theme.chatBackground());
    }

    private void clearFeed() {
        for (WidgetCell cell : cells) {
            cell.destroy();
        }
        cells.clear();
        feed.removeAllViews();
    }

    private void showFolder(String name) {
        if (name.equals(folder)) {
            return;
        }
        folder = name;
        clearFeed();
        // A bubble leaves room for the avatar side, as in a chat.
        int widthPx = getResources().getDisplayMetrics().widthPixels - AndroidUtilities.dp(8 * 2 + 10 * 2 + 48);
        double widthDp = Math.min(widthPx / AndroidUtilities.density, 400);
        for (String file : list(CORPUS + "/" + name)) {
            if (!file.endsWith(".html")) {
                continue;
            }
            String html = read(CORPUS + "/" + name + "/" + file);
            if (html == null) {
                continue;
            }
            WidgetCell cell = new WidgetCell(this, file, html, widthDp, resourcesDir);
            feed.addView(cell, LayoutHelper.createLinear((float) widthDp + 20, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
            cells.add(cell);
            cell.start();
        }
        scroll.scrollTo(0, 0);
        scroll.post(this::updateOnScreen);
    }

    /** A widget scrolled out of sight pauses; one coming back resumes. */
    private void updateOnScreen() {
        for (WidgetCell cell : cells) {
            cell.setOnScreen(cell.getLocalVisibleRect(visible));
        }
    }

    // ── assets ──

    private String[] corpusFolders() {
        ArrayList<String> folders = new ArrayList<>();
        for (String name : list(CORPUS)) {
            String[] inside = list(CORPUS + "/" + name);
            for (String file : inside) {
                if (file.endsWith(".html")) {
                    folders.add(name);
                    break;
                }
            }
        }
        return folders.toArray(new String[0]);
    }

    private String[] list(String path) {
        try {
            String[] names = getAssets().list(path);
            if (names == null) {
                return new String[0];
            }
            Arrays.sort(names);
            return names;
        } catch (Exception e) {
            return new String[0];
        }
    }

    private String read(String path) {
        try (InputStream in = getAssets().open(path)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16384];
            for (int n; (n = in.read(buffer)) > 0; ) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** Copies an assets folder, recursively, into the files directory; returns where it went. */
    private String copyAssets(String path) {
        File target = new File(getFilesDir(), path);
        copy(getAssets(), path, target);
        return target.getAbsolutePath();
    }

    private static void copy(AssetManager assets, String path, File target) {
        try {
            String[] children = assets.list(path);
            if (children != null && children.length > 0) {
                if (!target.isDirectory() && !target.mkdirs()) {
                    return;
                }
                for (String child : children) {
                    copy(assets, path + "/" + child, new File(target, child));
                }
                return;
            }
            try (InputStream in = assets.open(path); OutputStream out = new FileOutputStream(target)) {
                byte[] buffer = new byte[16384];
                for (int n; (n = in.read(buffer)) > 0; ) {
                    out.write(buffer, 0, n);
                }
            }
        } catch (Exception ignore) {
        }
    }
}
