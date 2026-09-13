package org.telegram.messenger.gramify;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;

/**
 * FIXED VERSION - Devgram Bubble
 * Problem in old repo:
 * 1. Ek baar music panel khula to dubara click pe band nahi hota tha
 * 2. Bubble gayab ho jata tha (root remove ho jata tha)
 * 
 * Fix:
 * - Bubble hamesha visible rahega, sirf panel toggle hoga
 * - Click = toggle logic (open/close)
 * - Drag vs Click ka sahi difference
 * - Music system bhi toggle (open on 1st click, close on 2nd click)
 */
public final class GramifyBubble_FIXED {

    private static final int ACCENT = 0xFF000000; // Devgram black theme
    private static final int BG = 0xF2111111; // pure black card
    private static final int TEXT = 0xFFFFFFFF;

    private final Context context;
    private final WindowManager windowManager;
    private final WindowManager.LayoutParams params;
    private FrameLayout root;
    private View bubbleView;
    private View panelView;
    
    // Toggle states - YEHI FIX HAI
    private boolean isPanelExpanded = false;
    private boolean isMusicOpen = false; // Music wala system ka toggle
    private boolean dragged = false;
    private float downX, downY;
    private int startX, startY;
    private static final int CLICK_THRESHOLD = 15; // dp

    private final Handler handler = new Handler(Looper.getMainLooper());

    public GramifyBubble_FIXED(Context context, WindowManager windowManager) {
        this.context = context;
        this.windowManager = windowManager;
        this.params = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(8);
        params.y = dp(120);
    }

    public void show() {
        if (root != null) return; // already shown, don't recreate

        root = new FrameLayout(context);
        
        // Panel - initially GONE
        panelView = buildPanel();
        panelView.setVisibility(View.GONE); // IMPORTANT: start me band
        root.addView(panelView, wrap());

        // Bubble - hamesha dikhega
        bubbleView = buildBubble();
        FrameLayout.LayoutParams blp = wrap();
        blp.gravity = Gravity.BOTTOM | Gravity.END;
        blp.bottomMargin = dp(20);
        blp.rightMargin = dp(20);
        root.addView(bubbleView, blp);

        try {
            windowManager.addView(root, params);
        } catch (Throwable t) {
            root = null;
            Toast.makeText(context, "Bubble error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public void hide() {
        if (root != null) {
            try {
                windowManager.removeView(root);
            } catch (Throwable ignore) {}
            root = null;
            bubbleView = null;
            panelView = null;
            isPanelExpanded = false;
            isMusicOpen = false;
        }
    }

    public boolean isShown() {
        return root != null;
    }

    private View buildBubble() {
        // Round black bubble - Devgram style
        FrameLayout bubble = new FrameLayout(context);
        int size = dp(56);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
        bubble.setLayoutParams(lp);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.BLACK);
        bg.setStroke(dp(2), Color.WHITE);
        bubble.setBackground(bg);
        bubble.setElevation(dp(8));

        ImageView icon = new ImageView(context);
        // Telegram plane but black theme - use your custom drawable
        icon.setImageResource(org.telegram.messenger.R.drawable.ic_music); // ya apna devgram icon
        icon.setColorFilter(Color.WHITE);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dp(24), dp(24));
        iconLp.gravity = Gravity.CENTER;
        bubble.addView(icon, iconLp);

        // FIXED TOUCH LOGIC - Drag vs Click
        bubble.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = params.x;
                        startY = params.y;
                        dragged = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downX;
                        float dy = event.getRawY() - downY;
                        if (Math.abs(dx) > dp(5) || Math.abs(dy) > dp(5)) {
                            dragged = true;
                            params.x = startX + (int) dx;
                            params.y = startY + (int) dy;
                            try {
                                windowManager.updateViewLayout(root, params);
                            } catch (Throwable ignore) {}
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragged) {
                            // YEHI MAIN FIX HAI - Toggle, not hide
                            togglePanel();
                        }
                        dragged = false;
                        return true;
                }
                return false;
            }
        });

        return bubble;
    }

    private View buildPanel() {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(createPanelBackground());
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));
        panel.setElevation(dp(12));

        // Title
        TextView title = new TextView(context);
        title.setText("Devgram Music");
        title.setTextColor(TEXT);
        title.setTextSize(16);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        panel.addView(title);

        // Music Toggle Button - FIXED LOGIC
        LinearLayout musicRow = new LinearLayout(context);
        musicRow.setOrientation(LinearLayout.HORIZONTAL);
        musicRow.setPadding(0, dp(12), 0, dp(12));

        TextView musicLabel = new TextView(context);
        musicLabel.setText("Gramify Player");
        musicLabel.setTextColor(TEXT);
        musicLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        View musicToggleBtn = createButton("Open");
        musicRow.addView(musicLabel);
        musicRow.addView(musicToggleBtn);
        panel.addView(musicRow);

        // Music Container - ye khulega/band hoga toggle pe
        LinearLayout musicContainer = new LinearLayout(context);
        musicContainer.setOrientation(LinearLayout.VERTICAL);
        musicContainer.setVisibility(View.GONE); // Start me band
        musicContainer.setBackgroundColor(0xFF1A1A1A);
        musicContainer.setPadding(dp(12), dp(12), dp(12), dp(12));
        
        TextView nowPlaying = new TextView(context);
        nowPlaying.setText("No song playing - JioSaavn search");
        nowPlaying.setTextColor(0xFFAAAAAA);
        musicContainer.addView(nowPlaying);
        
        panel.addView(musicContainer);

        // Music toggle ka click - YEHI FIX HAI
        musicToggleBtn.setOnClickListener(v -> {
            isMusicOpen = !isMusicOpen; // Toggle
            if (isMusicOpen) {
                musicContainer.setVisibility(View.VISIBLE);
                ((TextView) musicToggleBtn).setText("Close");
                // Yahan GramifyPlayer start karo
                Toast.makeText(context, "Music Open", Toast.LENGTH_SHORT).show();
            } else {
                musicContainer.setVisibility(View.GONE);
                ((TextView) musicToggleBtn).setText("Open");
                Toast.makeText(context, "Music Closed", Toast.LENGTH_SHORT).show();
            }
        });

        // Equalizer Button - same toggle logic
        LinearLayout eqRow = new LinearLayout(context);
        eqRow.setOrientation(LinearLayout.HORIZONTAL);
        eqRow.setPadding(0, dp(8), 0, dp(8));
        
        TextView eqLabel = new TextView(context);
        eqLabel.setText("Equalizer");
        eqLabel.setTextColor(TEXT);
        eqLabel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        
        View eqBtn = createButton("Open");
        eqRow.addView(eqLabel);
        eqRow.addView(eqBtn);
        panel.addView(eqRow);

        LinearLayout eqContainer = new LinearLayout(context);
        eqContainer.setOrientation(LinearLayout.VERTICAL);
        eqContainer.setVisibility(View.GONE);
        eqContainer.addView(createEqSliders());
        panel.addView(eqContainer);

        final boolean[] isEqOpen = {false};
        eqBtn.setOnClickListener(v -> {
            isEqOpen[0] = !isEqOpen[0];
            eqContainer.setVisibility(isEqOpen[0] ? View.VISIBLE : View.GONE);
            ((TextView) eqBtn).setText(isEqOpen[0] ? "Close" : "Open");
        });

        // Close Panel Button
        View closeBtn = createButton("Hide Panel");
        closeBtn.setOnClickListener(v -> togglePanel()); // Panel band, bubble rahega
        panel.addView(closeBtn);

        return panel;
    }

    // FIXED: Panel toggle - bubble kabhi gayab nahi hoga
    private void togglePanel() {
        if (panelView == null) return;
        
        isPanelExpanded = !isPanelExpanded; // Toggle logic
        
        if (isPanelExpanded) {
            panelView.setVisibility(View.VISIBLE);
            panelView.setAlpha(0f);
            panelView.animate().alpha(1f).setDuration(200).start();
        } else {
            panelView.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                if (panelView != null) panelView.setVisibility(View.GONE);
            }).start();
            // Bubble yahin rahega, gayab nahi hoga - YEHI FIX HAI
        }
    }

    private View createButton(String text) {
        TextView btn = new TextView(context);
        btn.setText(text);
        btn.setTextColor(Color.BLACK);
        btn.setBackground(createButtonBackground());
        btn.setPadding(dp(16), dp(8), dp(16), dp(8));
        btn.setGravity(Gravity.CENTER);
        return btn;
    }

    private GradientDrawable createPanelBackground() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(BG);
        d.setCornerRadius(dp(16));
        d.setStroke(dp(1), Color.WHITE);
        return d;
    }

    private GradientDrawable createButtonBackground() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.WHITE);
        d.setCornerRadius(dp(20));
        return d;
    }

    private View createEqSliders() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(context);
        t.setText("Bass Boost | Virtualizer | Presets");
        t.setTextColor(0xFF888888);
        layout.addView(t);
        return layout;
    }

    private int dp(int v) {
        return AndroidUtilities.dp(v);
    }

    private FrameLayout.LayoutParams wrap() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
