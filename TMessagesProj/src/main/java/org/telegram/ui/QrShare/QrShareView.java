package org.telegram.ui.QrShare;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.ui.Components.StaticLayoutEx;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.Cells.SettingsSearchCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.BatchParticlesDrawHelper;
import org.telegram.ui.Stars.StarGiftPatterns;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Stars.StarsController;

import java.util.ArrayList;
import java.util.Random;

/**
 * The QR code of a user or a chat, after the iOS "share QR" screen with "Remove message fee":
 * - background scenes (profile colours, emoji, gifts) switch with a glowing ring wave from the tapped swatch;
 * - glass (real backdrop blur) for chip, bars, sheet, menu and toasts;
 * - "Remove message fee" dissolves the code into rising particles and grows the new one behind the front;
 * - the free code's chip burns down over a minute with sparks at the edge and a rolling countdown.
 */
public class QrShareView extends View {

    public interface Delegate {
        void onClose();

        void onShare(String link);

        /** The scan button: the camera for a QR code. */
        void onScan();

        /** The download button: the QR as an image to the gallery. */
        void onSave();

        /** The empty gifts state → "Buy Gifts". */
        void onBuyGifts();

        /** The Premium-only toast → "Subscribe". */
        void onPremiumSubscribe();

        void onSceneLight(boolean light);

        /** The real Telegram emoji keyboard lives in the sheet's emoji tab: place it (top in px) or hide it. */
        void onEmojiPanel(boolean visible, float top, float alpha);

        /** Horizontal bounds and bottom of the floating sheet the keyboard sits in. */
        void onEmojiPanelBounds(float left, float right, float bottom, float shapeLeft, float shapeTop, float shapeRight, float shapeBottom, float radius);

        /** The fee chip or its menu has switched the mode: one of {@link #FEE_PAID}, {@link #FEE_TIMER}, {@link #FEE_PERMANENT}. */
        void onFeeMode(int mode);

        /** "Reset Code": the free code is to be replaced by a new one. */
        void onResetCode();

        /** The user has picked a colour, a gift or emoji. */
        void onThemeChanged();
    }

    public static final int FEE_PAID = 0, FEE_TIMER = 1, FEE_PERMANENT = 2;

    /** The own QR or a chat one administers: the theme can be picked; else the QR only shows its owner's theme. */
    private boolean themeEditable = true;

    public void setThemeEditable(boolean editable) {
        if (themeEditable != editable) {
            themeEditable = editable;
            invalidate();
        }
    }

    // ---------------------------------------------------------------------------------------------------- scenes

    static final int SCENE_COLOR = 0, SCENE_EMOJI = 1, SCENE_GIFT = 2;

    /** The colour backdrop: a rotating gradient (base axis + two orbiting lights). Shaders are built once per scene. */
    static final class Scene {
        int type;               // SCENE_COLOR or SCENE_GIFT (which sheet item is marked)
        int center, edge, pattern, qr;
        boolean light;
        int index;
        int mid;                // the backdrop gradient's middle stop (perceptual, OKLab)
        boolean vertical;
        int shade;              // deep tint of this backdrop for the picker panel       // keep the light on top (the default): sway instead of a full turn
    }

    /** What floats on the backdrop: nothing, the picked emoji, or a gift's pattern + model. */
    static final class Pattern {
        int type;               // PAT_*
        Drawable[] emoji;       // one per picked instance (duplicates allowed)
        AnimatedEmojiDrawable symbol, model;
        Bitmap symbolBitmap;    // the symbol rendered once, then drawn as a bitmap (hundreds per frame)
        Bitmap fallback;        // the previous gift's symbol, drawn in the new ink until this one is ready
        long readyAt;           // when symbolBitmap arrived (quick crossfade from the fallback)
        boolean waveSwap;       // swap per cell when the scene wave reaches it
        final Paint tintPaint = new Paint(Paint.FILTER_BITMAP_FLAG); // the symbols, already in their ink (alpha only)
        int ink;                // the symbols' colour: baked into inked copies of the silhouette on the CPU
        Bitmap inkBitmap, inkBitmapFor, inkFallback, inkFallbackFor;
        int inkBitmapColor, inkFallbackColor;
        int giftIndex = -1;
        /** The seed of the symbols' layout: from the gift's id, so it stays when the gift list loads or changes. */
        int symbolSeed;
        int shineColor = Color.WHITE;
        int litTintFor = -1;
        PorterDuffColorFilter litTint;
        final Paint glowPaint = new Paint(Paint.FILTER_BITMAP_FLAG); // same hue, brighter
        // atlases: every frame of the emoji / the gift model in one texture, all items in one drawVertices per
        // texture; until an atlas is there the items are drawn one by one from their drawables
        long[] emojiIds;        // per emoji instance: the custom emoji id, or a negative key of a standard emoji
        boolean[] emojiText;    // per emoji instance: drawn in the text colour (white)
        long[] emojiShownAt;    // per emoji instance: first drawn after the open (its copies pop in from then on)
        TLRPC.Document modelDoc, symbolDoc;
        Object giftParent;
        long modelReadyAt;      // the model (or its outline) is there: the models pop in from then on, one by one
        Bitmap modelOutline;    // the model's SVG outline in the ink, rasterised once (half the model's resolution)
        long modelFrameAt;      // the first real frame: it crossfades from the outline (1: no outline was shown)
        QrGiftSymbol.Loader giftLoader;
        boolean released;
    }

    static final int PAT_NONE = 0, PAT_EMOJI = 1, PAT_GIFT = 2;

    private static final String[] EMOJI_SETS = {
        "❤️", "🔥", "🎉", "😎", "🚀", "💎", "💙", "✨", "⭐", "🍀", "🐸", "🦄",
        "🌈", "🍕", "👻", "🍒", "🌸", "⚡", "🏆", "🎁", "👀", "😍", "🥳", "🤩",
        "🐱", "🐶", "🦋", "🌙", "☀️", "🍓", "🍩", "🎈", "🎮", "🎧", "💜", "🌊"
    };
    static final int MAX_EMOJI = 6;

    private final int currentAccount;
    private final Delegate delegate;
    /** The link of the code with the fee; the free code comes from {@link #setFreeLink}. */
    private String baseLink;
    private String freeLink;
    /** Under the code: the username (drawn with the "@" glyph), a phone number or a name. */
    private String caption;
    private boolean captionIsUsername;
    private Scene scene, oldScene;
    private float wave = 1f;            // 0..1 scene wave
    private long waveStart;
    private float waveX, waveY;
    private static final long WAVE_MS = 600; // ~33% shorter

    // ---- rotating backdrop (measured on IMG_6085: hue field turns ~14°/s counter-clockwise; symbols drift ~6pt/s)
    private static final float PAT_DEG_PER_S = -0.763f; // idle 10% slower   // the symbol field turns with it, slowly (≈6dp/s at the edges)
    private final long clockStart = AnimationUtils.currentAnimationTimeMillis();
    private final Matrix shaderMatrix = new Matrix();

    // ---- pattern field: positions relative to the screen centre, covering the rotated screen
    // one toroidal field (W × H) that drifts, wraps beyond the screen and turns; three lattices share it
    private Pattern pattern = new Pattern(), oldPattern;
    private long patternStart;
    private final ArrayList<String> picked = new ArrayList<>();
    private final java.util.HashMap<String, Drawable> emojiCache = new java.util.HashMap<>();
    private final Paint shinePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG); // soft white core

    {
        shinePaint.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
    }

    // per slot: old shrinks for 0.18 s, the new one starts at 0.135 s and pops in for 0.315 s; slots start up to 0.3 s apart
    private static final float SHRINK = 0.18f, GROW_AT = 0.135f, GROW = 0.315f, SWAP_SPREAD = 0.3f; // seconds
    // idle drift: 6.5dp/s up-left (measured ≈ iOS (−3.5, −5.5)pt/s); the heading wanders φ(t) = 1.2·sin(0.025t) + 0.6·sin(0.016t + 1)
    private static final float DRIFT_SPEED = 6.2f, DRIFT_ANGLE = (float) Math.atan2(-5.5, -3.5);
    private static final float GLIDE_TAU = 0.35f, MAX_FLING = 2600f; // s, dp/s
    private float wanderX, wanderY, lastFieldT = -1f;  // screen px, integrated each frame
    private float panX, panY, panBaseX, panBaseY;      // finger + inertia offset, screen px
    private boolean panning, panMoved, gliding, springing;
    private long glideStart, springStart;
    private float glideFromX, glideFromY, glideVX, glideVY;
    private float stretchX, stretchY, stretchBaseX, stretchBaseY, springX, springY;
    private android.view.VelocityTracker velocity;
    private float slotScaleK, slotAlpha, slotRot, slotPulse, slotLight;
    private float placeMin; // the smallest size the ramp may shrink an item to (the +27% min clamp)

    private int waveId;
    private long cardPulseStart;

    // ---------------------------------------------------------------------------------------------------- QR / fee

    private final QrCodeLayer qr = new QrCodeLayer();
    private QrCodeLayer.Code feeCode, freeCode;
    private boolean free;
    private boolean timerOn = true;
    private boolean menuTimerOn = true; // the menu's snapshot of timerOn, taken when it opens (no wording flip while it closes)
    /** Free codes only with the timer (a contact token: no permanent one): the menu has no timer row. */
    private boolean timerOnly;

    public void setTimerOnly(boolean timerOnly) {
        this.timerOnly = timerOnly;
    }

    // the rows of the free code's menu (with the timer on / off): titles and subtitles
    private static String[] menuTitles(boolean timerOn) {
        return new String[]{
            org.telegram.messenger.LocaleController.getString(R.string.QrNoPaidMenuReset),
            org.telegram.messenger.LocaleController.getString(timerOn ? R.string.QrNoPaidMenuDisableTimer : R.string.QrNoPaidMenuEnableTimer),
            org.telegram.messenger.LocaleController.getString(R.string.QrNoPaidMenuRestoreFee)
        };
    }

    private static String[] menuSubtitles(boolean timerOn) {
        return new String[]{null, org.telegram.messenger.LocaleController.getString(timerOn ? R.string.QrNoPaidMenuDisableTimerInfo : R.string.QrNoPaidMenuEnableTimerInfo), null};
    }

    private long freeUntil;
    /** The timer's code is still on its way (e.g. offline): the minute stands full until it comes. */
    private boolean timerWaiting;
    private static final long FREE_MS = 60_000;

    /** A new minute of the timer, counted from the moment its code comes from the server. */
    private void startTimerWait() {
        timerWaiting = true;
        freeUntil = System.currentTimeMillis() + FREE_MS;
    }

    private long timerLeft(long now) {
        return timerWaiting ? FREE_MS : Math.max(0, freeUntil - now);
    }

    private float chipSweep;            // 0..1 the white fill sweeping in (free) / out (fee again)
    private float sweepFrom, sweepTo = 0f;
    private long sweepStart;
    private boolean sweepDone;          // the free text has fully taken over the chip
    private float burnFrac = 1f;        // remaining time 0..1 (frozen when the fee comes back)
    private final TextPaint chipPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    // the remove-price button: one label "Remove ★50 fee", the star inline before the price (feeA ★ feeB)
    private String feeA = "", feeB = "";
    private static final int FEE_PAD = 11; // symmetric text insets of the label
    /** The chip shows only while messages from non-contacts cost stars. */
    private boolean chipShown;
    private final AnimatedTextView.AnimatedTextDrawable chipText;
    // chip dust: a fixed pool (x, y, vx, vy, life, age, size, rot, vr, kind 0 dust / 1 spark / 2 chunk), swap-remove
    private static final int CHIP_CAP = 320;
    private final float[] cpx = new float[CHIP_CAP], cpy = new float[CHIP_CAP], cpvx = new float[CHIP_CAP], cpvy = new float[CHIP_CAP],
        cplife = new float[CHIP_CAP], cpage = new float[CHIP_CAP], cpsize = new float[CHIP_CAP], cprot = new float[CHIP_CAP], cpvr = new float[CHIP_CAP],
    // burn particles only: spawn point, buoyancy, wobble frequency / phase
    cpsx = new float[CHIP_CAP], cpsy = new float[CHIP_CAP], cpay = new float[CHIP_CAP], cpwf = new float[CHIP_CAP], cpwp = new float[CHIP_CAP];
    private final byte[] cpkind = new byte[CHIP_CAP];
    private final boolean[] cpburn = new boolean[CHIP_CAP];
    private int cpN;
    private boolean chipBurstPending;

    private void chipEmit(float x, float y, float maxLife) {
        if (cpN >= CHIP_CAP) return;
        final int i = cpN++;
        final float r = random.nextFloat();
        cpkind[i] = (byte) (r < 0.65f ? 0 : r < 0.9f ? 1 : 2);
        cpburn[i] = false;
        cpx[i] = x + (random.nextFloat() - 0.5f) * dp(4);
        cpy[i] = y;
        cpsize[i] = cpkind[i] == 0 ? dp(1.4f) + random.nextFloat() * dp(1.6f) : cpkind[i] == 1 ? dpf2(0.7f) + random.nextFloat() * dpf2(0.7f) : dp(3) + random.nextFloat() * dp(1.5f);
        cpvx[i] = -dp(30) + random.nextFloat() * dp(80);
        cpvy[i] = -dp(50) - random.nextFloat() * dp(100);
        cplife[i] = Math.max(0.05f, Math.min(0.30f + random.nextFloat() * 0.20f, maxLife));
        cpage[i] = 0f;
        cprot[i] = random.nextFloat() * 90f;
        cpvr[i] = (random.nextFloat() - 0.5f) * 1080f;
    }

    private final Random random = new Random();
    private float burnEdgeX;            // the current x of the burning fill edge (particle distance falloff)

    /** The restore shatter: droplets sampled on a ~3dp grid over the filled area (cap 260), outward + up. */
    private void shatterBurst(RectF area, RectF chip) {
        final float step = dp(3);
        final float cx = chip.centerX(), cyc = chip.centerY();
        final int cols = Math.max(1, (int) (area.width() / step)), rows = Math.max(1, (int) (area.height() / step));
        final int total = cols * rows;
        final float keep = Math.min(1f, 260f / total);
        for (int iy = 0; iy < rows; iy++) {
            for (int ix = 0; ix < cols; ix++) {
                if (random.nextFloat() > keep || cpN >= CHIP_CAP) continue;
                final float x = area.left + (ix + 0.5f + (random.nextFloat() - 0.5f) * 0.8f) * step;
                final float y = area.top + (iy + 0.5f + (random.nextFloat() - 0.5f) * 0.8f) * step;
                // inside the pill only
                final float rr = chip.height() / 2f;
                if (x < chip.left + rr && Math.hypot(x - (chip.left + rr), y - cyc) > rr) continue;
                float dx = x - cx, dy = y - cyc;
                final float d = Math.max(1f, (float) Math.hypot(dx, dy));
                dx /= d;
                dy /= d;
                final float sp = dp(60) + random.nextFloat() * dp(140);
                final boolean spark = random.nextFloat() < 0.15f;
                final int i = cpN++;
                cpkind[i] = (byte) (spark ? 4 : 3);
                cpburn[i] = false;
                cpx[i] = x;
                cpy[i] = y;
                cpsx[i] = x;
                cpsy[i] = y;
                cpvx[i] = dx * sp + (random.nextFloat() - 0.5f) * dp(60);
                cpvy[i] = dy * sp - (dp(40) + random.nextFloat() * dp(80)) + (random.nextFloat() - 0.5f) * dp(60);
                cpsize[i] = spark ? dpf2(1.4f) + random.nextFloat() * dpf2(0.8f) : dpf2(1.0f) + random.nextFloat() * dpf2(1.2f);
                cplife[i] = 0.35f + random.nextFloat() * 0.3f;
                cpage[i] = 0f;
                cprot[i] = 1f;
                cpvr[i] = 0f;
                cpay[i] = 0f;
                cpwf[i] = 0f;
                cpwp[i] = 0f;
            }
        }
        invalidate();
    }

    /** A burn particle (round): cprot holds its alpha scale; kind 1 = spark with a halo. */
    private void chipEmitBurn(float x, float y, float vx, float vy, float ay, float size, float life, float alpha, boolean spark, boolean wobble) {
        if (cpN >= CHIP_CAP) return;
        final int i = cpN++;
        cpkind[i] = (byte) (spark ? 1 : 0);
        cpburn[i] = true;
        cpx[i] = x;
        cpy[i] = y;
        cpvx[i] = vx;
        cpvy[i] = vy;
        cpsx[i] = x;
        cpsy[i] = y;
        cpay[i] = ay;
        cpwf[i] = wobble ? 4f + random.nextFloat() * 3f : 0f;
        cpwp[i] = random.nextFloat() * 6.2832f;
        cpsize[i] = size;
        cplife[i] = life;
        cpage[i] = 0f;
        cprot[i] = alpha;
        cpvr[i] = 0f;
    }

    // ---------------------------------------------------------------------------------------------------- ui state

    private float sheet;                // 0..1 sheet open
    private float sheetTarget;
    private int tab = SCENE_COLOR;
    private float tabX;                 // animated selected tab index
    private float sheetScroll;
    private float menu;                 // 0..1 menu open
    private float menuTarget;
    private String toastText;
    private int toastIcon;
    private String toastAction, shownToastAction; // an optional action on the right of the toast (blue, tappable)
    private final RectF toastActionRect = new RectF();
    private boolean toastActionDown;
    private float toastTextSize;

    /** The QR image has been saved to the gallery. */
    public void showSavedToast() {
        showToast(R.drawable.msg_download, org.telegram.messenger.LocaleController.getString(R.string.QrSavedToGallery));
    }

    /** Without Premium a custom emoji other than the presets was tapped: nothing is applied, the star toast explains it. */
    public void showPremiumEmojiToast() {
        showToast(0, org.telegram.messenger.LocaleController.getString(R.string.QrPremiumEmojiToast), org.telegram.messenger.LocaleController.getString(R.string.QrPremiumSubscribe));
        haptic();
    }

    private float toast;                // 0..1
    private long toastUntil;
    private final ArrayList<TL_stars.TL_starGiftUnique> gifts = new ArrayList<>();
    /** The owner of the gifts on the sheet (the user, or a chat as a negative id), 0 for none. */
    private long giftsOwner;
    private boolean giftsEndReached;
    /** The saved id of a gift on the sheet by the gift id: a chat's gift is set as a theme by it. */
    private final java.util.HashMap<Long, Long> giftSavedIds = new java.util.HashMap<>();

    // ---------------------------------------------------------------------------------------------------- paints

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint bold = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path star = new Path();
    private final RectF rect = new RectF();
    // render nodes (QrRender, Android 12+), kept as Object so this class has no Android 10+ types
    private Object bgNode, blurNode;
    private static final int BLUR_DS = 9;
    private int blurBuiltVersion = -1;
    private final Drawable icCopy, icPremiumMain;
    private final Drawable icShare, icDownload, icScan, icPalette, icSmile, icGift, icRetry, icClock, icStar;
    private final Paint scanText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String scanLabel = org.telegram.messenger.LocaleController.getString(R.string.QrScanQr);
    private final BackDrawable icClose;     // the app's own close glyph (ActionBar back button in close state)
    private final RLottieDrawable logo;     // the flying plane of the stock QR screen
    private final Paint cutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public QrShareView(Context context, int account, String baseLink, String caption, boolean captionIsUsername, Delegate delegate) {
        super(context);
        this.currentAccount = account;
        this.baseLink = baseLink;
        this.caption = caption;
        this.captionIsUsername = captionIsUsername;
        this.delegate = delegate;
        icShare = d(R.drawable.msg_share);
        icDownload = d(R.drawable.msg_download);
        icScan = d(R.drawable.msg_qr_scan);
        scanText.setTypeface(AndroidUtilities.bold());
        icPalette = d(R.drawable.msg_colors);
        icSmile = d(R.drawable.input_smile);
        icGift = d(R.drawable.menu_gift);
        icClose = new BackDrawable(true);
        icRetry = d(R.drawable.msg_retry);
        icClock = d(R.drawable.msg_recent);
        icStar = new QrStarDrawable(); // star_premium has a gradient: empty before Android 7
        icCopy = d(R.drawable.msg_copy);
        icPremiumMain = d(R.drawable.menu_premium_main);
        tabIcons = new Drawable[]{icPalette, icSmile, icGift};
        logo = new RLottieDrawable(R.raw.plane_logo_plain, dp(60), dp(60), false, null);
        logo.setMasterParent(this);
        logo.setAutoRepeat(1);
        logo.setCallback(this);
        logo.start();
        cutPaint.setStyle(Paint.Style.STROKE);
        cutPaint.setStrokeCap(Paint.Cap.ROUND);
        cutPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        text.setTextSize(dp(11));
        bold.setTypeface(AndroidUtilities.bold());
        stroke.setStyle(Paint.Style.STROKE);

        chipText = new AnimatedTextView.AnimatedTextDrawable(false, true, true);
        chipText.setTextSize(dp(11));
        chipText.setAnimationProperties(0.35f, 0, 280, CubicBezierInterpolator.EASE_OUT_QUINT);
        chipText.setCallback(this);
        chipText.setGravity(Gravity.LEFT);

        scene = colorScene(DEFAULT_COLOR);
        rimLightColor = rimLightFor(scene.edge);
        if (baseLink != null) {
            rebuildCodes();
            qr.assemble(feeCode);
        }
        updateChipText(false);
    }

    /**
     * A new link of the code with the fee (e.g. a refreshed contact token) and the caption under it. The first link of a
     * screen opened without one (a contact token still on its way) assembles the code like the opening does.
     */
    public void setLinks(String baseLink, String caption, boolean captionIsUsername) {
        if (!TextUtils.equals(this.caption, caption) || this.captionIsUsername != captionIsUsername) {
            this.caption = caption;
            this.captionIsUsername = captionIsUsername;
            nameLayout = null;
            nameLayoutWidth = 0;
        }
        if (TextUtils.equals(this.baseLink, baseLink)) {
            return;
        }
        this.baseLink = baseLink;
        buildCode(baseLink, code -> {
            if (!TextUtils.equals(this.baseLink, baseLink)) return;
            final boolean first = feeCode == null;
            feeCode = code;
            if (free) return;
            if (first) {
                qr.assemble(code);
                invalidate();
            } else {
                swapCode(code);
            }
        });
    }

    /** The link of the free code from the server: the code appears as soon as it is built, while the chip is free. */
    public void setFreeLink(String link) {
        if (TextUtils.equals(freeLink, link)) return;
        freeLink = link;
        if (link == null) {
            freeCode = null;
            return;
        }
        buildCode(link, code -> {
            if (!TextUtils.equals(freeLink, link)) return;
            freeCode = code;
            if (free && timerOn && timerWaiting) {
                // the minute starts with the code on the screen
                timerWaiting = false;
                freeUntil = System.currentTimeMillis() + FREE_MS;
                lastChipSec = -1;
            }
            if (free) swapCode(code);
        });
    }

    private void buildCode(String link, Utilities.Callback<QrCodeLayer.Code> onBuilt) {
        Utilities.globalQueue.postRunnable(() -> {
            final QrCodeLayer.Code code = QrCodeLayer.build(link, AndroidUtilities.dp(240));
            AndroidUtilities.runOnUIThread(() -> onBuilt.run(code));
        });
    }

    /** The link the code on the screen leads to, null while there is no code yet. */
    public String getCurrentLink() {
        return free && freeLink != null && freeCode != null ? freeLink : feeCode != null ? baseLink : null;
    }

    private Drawable d(int res) {
        return getContext().getResources().getDrawable(res).mutate();
    }

    /* Glass without a blur source (before Android 12, or software drawing) */

    private static final float FROST_ALPHA = 0.88f;

    /**
     * Whether the glass has its blurred backdrop to draw; without it {@link #drawFrost} stands in for the blur, so the
     * pattern does not show through the glass as if it were clear.
     */
    private boolean hasBlur(Canvas c) {
        return blurAllowed() && blurNow() && c.isHardwareAccelerated() && QrRender.hasDisplayList(blurNode);
    }

    /**
     * The blur from the first frame on high-end devices (the open's GPU work is theirs to carry); elsewhere only once
     * the open has finished, the glass showing the backdrop picture until then.
     */
    private boolean blurNow() {
        return openDone || immediateOnOpen;
    }

    /** The gifts, the emoji and the blur from the open's first frame (the screen's argument, high-end devices only). */
    private boolean immediateOnOpen;

    void setImmediateOnOpen(boolean immediate) {immediateOnOpen = immediate;}

    /**
     * The glass blur at all: Android 12+ (render effects) and blur on in the power saving settings (LiteMode). Without
     * it the glass keeps the backdrop picture it shows during the open.
     */
    private static boolean blurAllowed() {
        return QrRender.hasEffects() && LiteMode.isEnabled(LiteMode.FLAG_CHAT_BLUR);
    }

    /** No blur (yet, during the open; or none at all): the glass shows the backdrop picture (a blur of the gradient ≈ itself). */
    private boolean blurPending(Canvas c) {
        return backdrop != null;
    }

    /** The blur's stand-in under the glass: the low-res backdrop, the same plain bitmap draw as the backdrop. */
    private void drawBlurStandIn(Canvas c) {
        if (backdrop != null) drawBackdropBitmap(c, backdrop);
    }

    /**
     * The stand-in in a rounded rect: the low-res backdrop as a BitmapShader (the same draw as the backdrop's rounded
     * corners during the open: the same GPU program, no clip).
     */
    private void drawBlurStandIn(Canvas c, RectF r, float radius, float alpha) {
        if (backdrop == null || backdropShader == null) return;
        final float cell = backdropCell;
        backdropMatrix.setScale(cell, cell);
        backdropMatrix.postTranslate(-cell / 2f, -cell / 2f);
        backdropShader.setLocalMatrix(backdropMatrix);
        // a fading glass fades its stand-in too (the paint's alpha: no layer); a frost fill instead of it switched to
        // the picture in one frame at full alpha, a jump of the buttons' brightness
        backdropRoundPaint.setAlpha((int) (255 * Utilities.clamp01(alpha)));
        c.drawRoundRect(r, radius, radius, backdropRoundPaint);
        backdropRoundPaint.setAlpha(255);
    }

    private void drawBlurNode(Canvas c, Object node) {
        c.save();
        c.scale(BLUR_DS, BLUR_DS);
        QrRender.draw(c, node);
        c.restore();
    }

    /** The blur's stand-in: the backdrop's mean colour, nearly opaque (a blur of a gradient is close to its mean). */
    private void drawFrost(Canvas c, RectF bounds, float alpha) {
        final int base = scene != null ? ColorUtils.blendARGB(scene.center, scene.edge, 0.5f) : 0xFF3A6EA5;
        fill.setColor(base | 0xFF000000);
        fill.setAlpha((int) (255 * FROST_ALPHA * Utilities.clamp01(alpha)));
        c.drawRect(bounds, fill);
    }

    // ---------------------------------------------------------------------------------------------------- data

    /**
     * Backdrops a little deeper and more contrasty than the gift table, same hue: lightness −9 (centre) / −13 (edge)
     * in CIELAB, chroma +8%.
     */
    private static int deepen(int c, boolean edge) {
        final double[] lab = new double[3];
        ColorUtils.colorToLAB(c, lab);
        if (!edge) return c; // the centre exactly as in Telegram
        lab[0] = Math.max(0, lab[0] - 3); // the edge: a hair more depth on a full-screen radial
        lab[1] *= 1.03;
        lab[2] *= 1.03;
        return ColorUtils.LABToColor(lab[0], lab[1], lab[2]);
    }

    private static int[] DEEP_CENTER, DEEP_EDGE, DEEP_QR;

    private static void buildDeep() {
        if (DEEP_CENTER != null) return;
        final int n = Backdrops.CENTER.length;
        DEEP_CENTER = new int[n];
        DEEP_EDGE = new int[n];
        DEEP_QR = new int[n];
        for (int i = 0; i < n; i++) {
            DEEP_CENTER[i] = deepen(Backdrops.CENTER[i], false);
            DEEP_EDGE[i] = deepen(Backdrops.EDGE[i], true);
            DEEP_QR[i] = contrastMatchedQr(DEEP_CENTER[i], DEEP_EDGE[i]);
        }
    }

    private static int deepCenter(int i) {
        if (i == TUNED_COLOR) return DEFAULT_TOP;
        buildDeep();
        return DEEP_CENTER[i];
    }

    private static int deepEdge(int i) {
        if (i == TUNED_COLOR) return DEFAULT_BOTTOM;
        buildDeep();
        return DEEP_EDGE[i];
    }

    /** The default backdrop ("Black"): shown without a theme, saved as the default theme; its index follows the palette. */
    static int DEFAULT_COLOR = Backdrops.indexOfName(Backdrops.DEFAULT_NAME);
    /** "Neon Blue", drawn with the tuned colours (DEFAULT_TOP / DEFAULT_BOTTOM / DEFAULT_QR); -1 if not in the palette. */
    static int TUNED_COLOR = Backdrops.indexOfName(Backdrops.TUNED_NAME);

    /** The palette has been replaced (see {@link Backdrops#apply}). */
    static void onBackdropsChanged(int defaultIndex, int tunedIndex) {
        DEFAULT_COLOR = defaultIndex;
        TUNED_COLOR = tunedIndex;
        DEEP_CENTER = DEEP_EDGE = DEEP_QR = null;
    }

    static final int PRESET_COUNT = 0;

    static int slotIndex(int slot) {return Backdrops.orderToIndex(slot);}

    static final int DEFAULT_TOP = 0xFF3FB1FF, DEFAULT_BOTTOM = 0xFF166ACE, DEFAULT_QR = 0xFF0075F1; // as tuned by the user 2026-10-15

    private Scene colorScene(int i) {
        final Scene s = new Scene();
        s.type = SCENE_COLOR;
        s.index = i;
        if (i == TUNED_COLOR) {
            // Neon Blue: sky blue on top, a deeper saturated blue at the bottom
            s.center = DEFAULT_TOP;
            s.edge = DEFAULT_BOTTOM;
            s.pattern = Backdrops.PATTERN[i];
            s.vertical = true;
        } else {
            s.center = deepen(Backdrops.CENTER[i], false);
            s.edge = deepen(Backdrops.EDGE[i], true);
            s.pattern = Backdrops.PATTERN[i];
        }
        if (i == TUNED_COLOR) {
            s.qr = DEFAULT_QR;
        } else {
            buildDeep();
            s.qr = DEEP_QR[i];
        }
        buildShaders(s);
        return s;
    }

    /** A lighter tint of the backdrop's own hue (not milky), 10%: picker layers sit above the backdrop. */
    static int shadeFor(int center) {
        final double[] lab = new double[3];
        ColorUtils.colorToLAB(center, lab);
        return ColorUtils.setAlphaComponent(ColorUtils.LABToColor(Math.min(96, lab[0] + 24), lab[1] * 0.75, lab[2] * 0.75), 0x1A); // +24 L (was +28)
    }

    /** The scene's derived colours (the backdrop itself is generated as a picture, see drawBackdrop). */
    private static void buildShaders(Scene s) {
        s.shade = shadeFor(s.center);
        s.mid = QrCodeLayer.mixOklab(s.center, s.edge, 0.5f);
    }

    /**
     * Symbol ink that always reads: the gift's pattern colour, its lightness pushed away from the backdrop's
     * mid tone (darker on light backdrops, lighter on dark ones), slightly more saturated, ~62% opaque.
     */
    private static int symbolInk(int pattern, int center, int edge) {
        // the backdrop's own pattern colour as is; only when it's too close to the centre (contrast < 1.15) is it
        // nudged darker by the minimum needed
        int c = pattern | 0xFF000000;
        if (ColorUtils.calculateContrast(c, center | 0xFF000000) < 1.15) {
            final double[] lab = new double[3];
            ColorUtils.colorToLAB(c, lab);
            for (int k = 0; k < 40 && ColorUtils.calculateContrast(c, center | 0xFF000000) < 1.15; k++) {
                lab[0] = Math.max(0, lab[0] - 2);
                c = ColorUtils.LABToColor(lab[0], lab[1], lab[2]);
            }
        }
        return ColorUtils.setAlphaComponent(c, 95); // ~0.37
    }

    /** The same hue, lit: lightness +20 (LAB), a little more saturated, opaque. Never the opposite tone. */
    private static int brighter(int ink) {
        final double[] lab = new double[3];
        ColorUtils.colorToLAB(ink | 0xFF000000, lab);
        lab[0] = Math.min(97, lab[0] + 20);
        int c = ColorUtils.LABToColor(lab[0], lab[1], lab[2]);
        final float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.15f);
        return Color.HSVToColor(hsv);
    }

    /** A gift sets the backdrop colours and its own floating pattern. */
    private void pickGift(int i, float x, float y) {
        final TL_stars.TL_starGiftUnique g = gifts.get(i);
        final Scene s = new Scene();
        s.type = SCENE_GIFT;
        s.index = i;
        final Pattern p = new Pattern();
        p.type = PAT_GIFT;
        p.giftIndex = i;
        p.symbolSeed = (int) (g.id ^ (g.id >>> 32));
        for (TL_stars.StarGiftAttribute a : g.attributes) {
            if (a instanceof TL_stars.starGiftAttributePattern && ((TL_stars.starGiftAttributePattern) a).document != null) {
                p.symbolDoc = ((TL_stars.starGiftAttributePattern) a).document;
                p.symbol = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_EMOJI_STATUS, p.symbolDoc); // as the gift sheet draws patterns: crisp
            } else if (a instanceof TL_stars.starGiftAttributeModel && ((TL_stars.starGiftAttributeModel) a).document != null) {
                p.modelDoc = ((TL_stars.starGiftAttributeModel) a).document;
                p.model = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_LARGE_140, p.modelDoc); // same instance as the tile; sharp
            }
        }
        giftColors(i, giftCol);
        s.center = deepen(giftCol[0], false);
        s.edge = deepen(giftCol[1], true);
        s.pattern = giftCol[2];
        s.qr = contrastMatchedQr(s.center, s.edge);
        buildShaders(s);
        p.ink = symbolInk(s.pattern, s.center, s.edge);
        p.shineColor = brighter(symbolInk(s.pattern, s.center, s.edge)) | 0xFF000000;
        p.glowPaint.setColorFilter(new PorterDuffColorFilter(p.shineColor, PorterDuff.Mode.SRC_IN));
        p.litTintFor = -1; // the in-place light tint (toward white) is built on first use
        p.giftParent = g;
        if (p.symbol != null) p.symbol.addView(this);
        if (p.model != null) p.model.addView(this);
        loadModelOutline(p);
        picked.clear();
        setScene(s, x, y);
        if (!instantTheme) {
            qrBounceFrom = bounce(qrBounceStart, 0.03f, 0.005f);
            qrBounceStart = AnimationUtils.currentAnimationTimeMillis();
        }
        setPattern(p);
    }

    /**
     * The model's outline (its document's SVG path thumb) rasterised on a background thread in the pattern's ink, at
     * half the model's resolution: drawn as a plain bitmap at 15% while the model loads (the path itself would be
     * Skia's stencil-then-cover on the GPU, two programs of its own).
     */
    private void loadModelOutline(Pattern p) {
        final TLRPC.Document doc = p.modelDoc;
        if (doc == null) return;
        android.graphics.Path svg = null;
        for (int i = 0; i < doc.thumbs.size(); i++) {
            if (doc.thumbs.get(i) instanceof TLRPC.TL_photoPathSize) {
                svg = ((TLRPC.TL_photoPathSize) doc.thumbs.get(i)).svgPath;
                break;
            }
        }
        if (svg == null) return;
        int sw = 512, sh = 512;
        for (int i = 0; i < doc.attributes.size(); i++) {
            final TLRPC.DocumentAttribute a = doc.attributes.get(i);
            if ((a instanceof TLRPC.TL_documentAttributeImageSize || a instanceof TLRPC.TL_documentAttributeVideo) && a.w > 0 && a.h > 0) {
                sw = a.w;
                sh = a.h;
                break;
            }
        }
        final Path path = new Path(svg); // the thumb's path is shared with the gift tiles: a copy for the other thread
        final float scaleX = MODEL_OUTLINE_PX / (float) sw, scaleY = MODEL_OUTLINE_PX / (float) sh;
        final int color = p.ink;
        Utilities.globalQueue.postRunnable(() -> {
            Bitmap b;
            try {
                b = Bitmap.createBitmap(MODEL_OUTLINE_PX, MODEL_OUTLINE_PX, Bitmap.Config.ARGB_8888);
                final Canvas c = new Canvas(b);
                c.scale(scaleX, scaleY);
                final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setColor(color);
                c.drawPath(path, paint);
                b.prepareToDraw();
            } catch (Throwable e) {
                org.telegram.messenger.FileLog.e(e);
                b = null;
            }
            final Bitmap out = b;
            AndroidUtilities.runOnUIThread(() -> {
                if (p.released || out == null) return;
                p.modelOutline = out;
                invalidate();
            });
        });
    }

    /** Half the model's resolution (CACHE_TYPE_ALERT_PREVIEW_LARGE_140: 140dp). */
    private static final int MODEL_OUTLINE_PX = AndroidUtilities.dp(70);

    // ---- for the emoji pick overlay (badges + bottom island)
    public java.util.List<String> pickedKeys() {return picked;}

    public Drawable emojiFor(String key) {return emojiDrawable(key);}

    public void removePickAt(int i) {
        if (i < 0 || i >= picked.size()) return;
        picked.remove(i);
        applyEmojiPattern();
        haptic();
    }

    public int islandTint() {return scene != null ? scene.shade : 0x33FFFFFF;}

    private int headerColorFor = 1, headerColor; // (1: never a real colour → computed on first use)

    /**
     * The emoji keyboard's section headers: soft white with a hint of the scene (white mixed with 13% of the backdrop
     * colour, its OKLCH chroma ×1.3), at 70%. Cached per backdrop colour.
     */
    public int sectionHeaderColor() {
        final int base = scene != null ? scene.center : 0xFF000000;
        if (base != headerColorFor) {
            headerColorFor = base;
            // soft white with a hint of the scene: 13% of the chroma-boosted backdrop colour, at 70%
            final int tint = oklchRelight(base, 0.75f, 1.3f, 0.16f);
            headerColor = (ColorUtils.blendARGB(Color.WHITE, tint, 0.13f) & 0xFFFFFF) | 0x99000000; // 60% (was 70%)
        }
        return headerColor;
    }

    private static float srgbToLin(int c) {
        final float v = c / 255f;
        return v <= 0.04045f ? v / 12.92f : (float) Math.pow((v + 0.055f) / 1.055f, 2.4f);
    }

    private static int linToSrgb(float v) {
        v = Math.max(0f, Math.min(1f, v));
        final float s = v <= 0.0031308f ? 12.92f * v : 1.055f * (float) Math.pow(v, 1 / 2.4f) - 0.055f;
        return Math.round(Math.max(0f, Math.min(1f, s)) * 255f);
    }

    /** rgb → OKLCH, lightness set to L, chroma ×cMul (capped at cMax) → rgb (opaque); greys stay grey. */
    static int oklchRelight(int rgb, float L, float cMul, float cMax) {
        final float r = srgbToLin(Color.red(rgb)), g = srgbToLin(Color.green(rgb)), b = srgbToLin(Color.blue(rgb));
        final float l = (float) Math.cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b);
        final float m = (float) Math.cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b);
        final float s = (float) Math.cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b);
        final float A = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s;
        final float B = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s;
        final float C = (float) Math.hypot(A, B), k = C > 1e-5f ? Math.min(cMax, C * cMul) / C : 0f;
        final float a2 = A * k, b2 = B * k;
        final float l2 = L + 0.3963377774f * a2 + 0.2158037573f * b2;
        final float m2 = L - 0.1055613458f * a2 - 0.0638541728f * b2;
        final float s2 = L - 0.0894841775f * a2 - 1.2914855480f * b2;
        final float L3 = l2 * l2 * l2, M3 = m2 * m2 * m2, S3 = s2 * s2 * s2;
        return Color.rgb(
            linToSrgb(4.0767416621f * L3 - 3.3077115913f * M3 + 0.2309699292f * S3),
            linToSrgb(-1.2684380046f * L3 + 2.6097574011f * M3 - 0.3413193965f * S3),
            linToSrgb(-0.0041960863f * L3 - 0.7034186147f * M3 + 1.7076147010f * S3));
    }

    /**
     * Draws the screen's already-blurred backdrop (the same 1/4-res blur every glass element samples) into another
     * view's canvas; (dx, dy) = that canvas' origin relative to this view. No extra blur pass. Returns false if none.
     */
    /**
     * Like drawBlur, but the sheet's own glass under it: the lifted blur (brightness/contrast), the sheet's white fill
     * and its tint — so a light island inside the sheet starts from what the sheet shows, not the darker raw blur.
     */
    public boolean drawSheetGlass(Canvas c, float dx, float dy, RectF clip) {
        // the packs island: one flat colour per backdrop — the fully open sheet glass (lifted blur + white fill + scene
        // shade) evaluated on the backdrop colour, cached (prewarmed for all the backdrops); no GPU pass
        fill.setColor(islandFlatColor());
        c.drawRect(clip, fill);
        return true;
    }

    public boolean drawBlur(Canvas c, float dx, float dy) {
        if (!hasBlur(c) && blurPending(c)) {
            c.save();
            c.translate(-dx, -dy);
            drawBlurStandIn(c);
            c.restore();
            return true;
        }
        if (!QrRender.hasEffects() || !hasBlur(c)) {
            // the caller clips to its glass
            final int base = scene != null ? ColorUtils.blendARGB(scene.center, scene.edge, 0.5f) : 0xFF3A6EA5;
            c.drawColor(ColorUtils.setAlphaComponent(base, (int) (255 * FROST_ALPHA)));
            return true;
        }
        c.save();
        c.translate(-dx, -dy);
        drawBlurNode(c, blurNode);
        c.restore();
        return true;
    }

    /** From the emoji keyboard: a standard emoji. */
    public void pickEmoji(String emoji) {
        if (emoji == null) return;
        emojiTextOf.put(normKey(emoji), emoji);
        addEmoji(emoji);
    }

    /** A standard emoji's text as picked (its key drops the variation selector): the theme saves this. */
    private final java.util.HashMap<String, String> emojiTextOf = new java.util.HashMap<>();

    /** From the emoji keyboard: a custom (animated / pack) emoji, drawn by AnimatedEmojiDrawable. */
    public void pickCustomEmoji(long documentId, TLRPC.Document document) {
        final String key = "c:" + documentId;
        if (!emojiCache.containsKey(key)) {
            // animated, one shared drawable per emoji (all instances in one phase): ≤ 6 live animations
            final AnimatedEmojiDrawable d = document != null
                ? AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, document)
                : AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, documentId);
            d.addView(this);
            emojiCache.put(key, d);
        }
        addEmoji(key);
    }

    /** One key per emoji wherever it shows (recent or its category; ❤️ with or without the variation selector). */
    static String normKey(String e) {
        return e == null || e.startsWith("c:") ? e : e.replace("\uFE0F", "");
    }

    /**
     * Tap in the emoji tab: a toggle. An unselected emoji is picked (a full set drops the oldest); tapping a picked
     * one removes it (the badges renumber). One emoji = at most one pick: no duplicates in the island or the pattern.
     */
    private void addEmoji(String e) {
        final String k = normKey(e); // custom ("c:" + id) and standard emoji alike: the theme keeps both
        final int i = picked.indexOf(k);
        if (i >= 0) {
            picked.remove(i);
        } else {
            if (picked.size() >= MAX_EMOJI) picked.remove(0);
            picked.add(k);
        }
        applyEmojiPattern();
        haptic();
    }

    /** Long press / badge tap: remove the latest instance of that emoji. */
    private void removeEmoji(String e) {
        final int i = picked.lastIndexOf(e);
        if (i < 0) return;
        picked.remove(i);
        applyEmojiPattern();
        haptic();
    }

    private Runnable onPicksChanged;

    public void setOnPicksChanged(Runnable r) {onPicksChanged = r;}

    private Pattern emojiPatternOrNone() {
        final Pattern p = new Pattern();
        if (!picked.isEmpty()) {
            p.type = PAT_EMOJI;
            p.emoji = new Drawable[picked.size()];
            p.emojiIds = new long[picked.size()];
            p.emojiText = new boolean[picked.size()];
            p.emojiShownAt = new long[picked.size()];
            if (openDone) java.util.Arrays.fill(p.emojiShownAt, 1); // picked later: the swap's own pop, no extra delay
            for (int i = 0; i < p.emoji.length; i++) {
                final String key = picked.get(i);
                p.emoji[i] = emojiDrawable(key);
                long id = 0;
                if (key.startsWith("c:")) {
                    try {id = Long.parseLong(key.substring(2));} catch (NumberFormatException ignore) {}
                }
                p.emojiIds[i] = id > 0 ? id : -1L - (key.hashCode() & 0xFFFFFFFFL);
                p.emojiText[i] = QrEmojiPanel.isTextColor(p.emoji[i]);
            }
        }
        return p;
    }

    private void applyEmojiPattern() {
        if (onPicksChanged != null) onPicksChanged.run();
        // the theme keeps emoji together with a colour backdrop: they go over the colour shown; a gift gives way to the
        // last colour (a gift theme has no emoji)
        if (!applyingTheme && !picked.isEmpty() && scene != null && (scene.type != SCENE_COLOR || pattern.type == PAT_GIFT)) {
            // the gift's ring retracts, as when a colour is picked
            deselectAt = AnimationUtils.currentAnimationTimeMillis();
            prevSelGift = -1;
            selTarget = 0f;
            setSceneFromCard(colorScene(lastColorIndex));
        }
        setPattern(emojiPatternOrNone());
        if (!instantTheme) cardPulseStart = AnimationUtils.currentAnimationTimeMillis();
        notifyThemeChanged();
    }

    // ---- the theme on the server: what is shown now, and the saved theme applied on open

    private boolean applyingTheme;
    /** A theme known before the screen shows (the cache) is applied without the transitions. */
    private boolean instantTheme;

    public void setInstantTheme(boolean instant) {
        instantTheme = instant;
    }

    private void notifyThemeChanged() {
        if (!applyingTheme && delegate != null) delegate.onThemeChanged();
    }

    /** The picked custom emoji, in order; standard emoji are left out (the server keeps only custom ones). */
    /** The picked emoji as theme reactions: custom by document id, standard by their text. */
    public ArrayList<TLRPC.Reaction> getPickedEmoji() {
        final ArrayList<TLRPC.Reaction> list = new ArrayList<>();
        for (String key : picked) {
            if (key.startsWith("c:")) {
                try {
                    final TLRPC.TL_reactionCustomEmoji r = new TLRPC.TL_reactionCustomEmoji();
                    r.document_id = Long.parseLong(key.substring(2));
                    list.add(r);
                } catch (NumberFormatException ignore) {}
            } else {
                final TLRPC.TL_reactionEmoji r = new TLRPC.TL_reactionEmoji();
                final String text = emojiTextOf.get(key);
                r.emoticon = text != null ? text : key;
                list.add(r);
            }
        }
        return list;
    }

    /** The gift of the backdrop, null for a colour. */
    public TL_stars.TL_starGiftUnique getThemeGift() {
        return pattern.type == PAT_GIFT && pattern.giftIndex >= 0 && pattern.giftIndex < gifts.size() ? gifts.get(pattern.giftIndex) : null;
    }

    /** The palette index of the colour backdrop, -1 for a gift. */
    public int getThemeBackdropIndex() {
        return scene != null && scene.type == SCENE_COLOR ? scene.index : -1;
    }

    public boolean isDefaultBackdrop(int index) {
        return index == DEFAULT_COLOR;
    }

    /**
     * The saved theme: emoji and / or a backdrop of the palette (by its server id; none or an unknown one: the
     * default backdrop).
     */
    public void applyThemeEmojiBackdrop(boolean hasBackdrop, int backdropId, java.util.List<TLRPC.Reaction> emojis) {
        final int found = hasBackdrop ? Backdrops.indexOfId(backdropId) : -1;
        final int index = found >= 0 ? found : DEFAULT_COLOR;
        if (emojis == null || emojis.isEmpty()) {
            applyThemeColor(index);
            return;
        }
        applyingTheme = true;
        lastColorIndex = index;
        if (scene == null || scene.type != SCENE_COLOR || scene.index != index) setSceneFromCard(colorScene(index));
        selTarget = 0f;
        picked.clear();
        for (TLRPC.Reaction reaction : emojis) {
            if (picked.size() >= MAX_EMOJI) break;
            if (reaction instanceof TLRPC.TL_reactionCustomEmoji) {
                final long id = ((TLRPC.TL_reactionCustomEmoji) reaction).document_id;
                final String key = "c:" + id;
                if (!emojiCache.containsKey(key)) {
                    final AnimatedEmojiDrawable d = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_KEYBOARD, id);
                    d.addView(this);
                    emojiCache.put(key, d);
                }
                if (!picked.contains(key)) picked.add(key);
            } else if (reaction instanceof TLRPC.TL_reactionEmoji && !TextUtils.isEmpty(((TLRPC.TL_reactionEmoji) reaction).emoticon)) {
                final String text = ((TLRPC.TL_reactionEmoji) reaction).emoticon;
                final String key = normKey(text);
                emojiTextOf.put(key, text);
                if (!picked.contains(key)) picked.add(key);
            }
        }
        applyEmojiPattern();
        applyingTheme = false;
    }

    /**
     * A fresher server palette has come while the screen is open: it replaces the table at once. The colour shown keeps
     * its name (the indices change), else it becomes the default; no wave, nothing is saved.
     */
    public void replacePalette(java.util.List<TLRPC.TL_shareThemeBackdropOption> list) {
        final String shown = scene != null && scene.type == SCENE_COLOR ? backdropName(scene.index) : null;
        final String last = backdropName(lastColorIndex);
        if (!Backdrops.apply(list)) return;
        lastColorIndex = backdropIndex(last);
        if (shown != null) {
            if (oldScene != null) {
                releaseScene(oldScene);
                oldScene = null;
                wave = 1f;
            }
            scene = colorScene(backdropIndex(shown));
            if (delegate != null) delegate.onSceneLight(scene.light);
        }
        invalidate();
    }

    @Nullable
    private static String backdropName(int index) {
        return index >= 0 && index < Backdrops.NAMES.length ? Backdrops.NAMES[index] : null;
    }

    private static int backdropIndex(@Nullable String name) {
        if (name != null) {
            for (int i = 0; i < Backdrops.NAMES.length; i++) if (name.equals(Backdrops.NAMES[i])) return i;
        }
        return DEFAULT_COLOR;
    }

    /** Emoji always go on the default backdrop (black). */
    public void applyThemeDefault() {
        applyThemeColor(DEFAULT_COLOR);
    }

    private void applyThemeColor(int index) {
        applyingTheme = true;
        final boolean hadPattern = pattern.type != PAT_NONE;
        picked.clear();
        selTarget = 0f;
        lastColorIndex = index;
        if (scene == null || scene.type != SCENE_COLOR || scene.index != index) setSceneFromCard(colorScene(index));
        if (hadPattern) setPattern(new Pattern());
        if (onPicksChanged != null) onPicksChanged.run();
        applyingTheme = false;
    }

    /** The saved theme: a collectible gift, added to the list if it has not been loaded yet. */
    public void applyThemeGift(TL_stars.TL_starGiftUnique gift) {
        int at = -1;
        for (int k = 0; k < gifts.size(); k++)
            if (gifts.get(k).id == gift.id) {
                at = k;
                break;
            }
        if (at < 0) {
            gifts.add(0, gift);
            at = 0;
        }
        if (pattern.type == PAT_GIFT && pattern.giftIndex == at) return;
        applyingTheme = true;
        picked.clear();
        if (onPicksChanged != null) onPicksChanged.run();
        selGift = at;
        selA = instantTheme ? 1f : 0f;
        selTarget = 1f;
        pickGift(at, cardCenterX(), cardTop() + cardH() / 2f);
        applyingTheme = false;
    }

    /** The saved theme: custom emoji over the black backdrop. */
    private Drawable emojiDrawable(String e) {
        Drawable d = emojiCache.get(e);
        if (d == null) {
            d = Emoji.getEmojiDrawable(e);
            if (d == null)
                d = Emoji.getEmojiDrawable(e.replace("\uFE0F", "")); // ❤️ / ☀️ are stored without the variation selector
            if (d != null) emojiCache.put(e, d);
        }
        return d;
    }

    private Bitmap lastSymbol;

    private void setPattern(Pattern p) {
        if (instantTheme) {
            if (oldPattern != null && oldPattern != pattern) releasePattern(oldPattern);
            if (pattern != p) releasePattern(pattern);
            oldPattern = null;
            pattern = p;
            if (p.type == PAT_GIFT) {
                ensureSymbolBitmap(p);
                if (p.symbolBitmap == null) p.fallback = lastSymbol;
            }
            // already in place, nothing grows in
            patternStart = AnimationUtils.currentAnimationTimeMillis() - 10_000;
            invalidate();
            return;
        }
        p.waveSwap = wave < 1f && oldScene != null;
        if (p.type == PAT_GIFT) {
            ensureSymbolBitmap(p);
            if (p.symbolBitmap == null) p.fallback = lastSymbol;
        }
        if (oldPattern != null && oldPattern != pattern) releasePattern(oldPattern);
        // when a swap is still running its slots are mid-flight; the visible content is closest to the new one
        oldPattern = pattern;
        pattern = p;
        patternStart = AnimationUtils.currentAnimationTimeMillis();
        invalidate();
    }

    private void releasePattern(Pattern p) {
        if (p == null) return;
        if (p.symbol != null) p.symbol.removeView(this);
        if (p.model != null && !giftThumbs.containsValue(p.model)) p.model.removeView(this);
        p.released = true;
        // the atlases go back to the pool (reused after the frames that may still refer to them)
        if (p.giftLoader != null) {
            p.giftLoader.cancel();
            p.giftLoader = null;
        }
    }

    /* Atlases */

    /** Starts building the atlases of a pattern once (the emoji need their still frames for the static ones). */
    private void ensureAtlases(Pattern p) {
        if (p == null || p.released) return;
        if (p.type == PAT_GIFT && p.giftLoader == null && p.symbolDoc != null) {
            // the symbol only: the models are drawn straight from the drawable's current frame (see drawModels)
            p.giftLoader = new QrGiftSymbol.Loader(currentAccount, p.symbolDoc, p.giftParent, dp(48), symbol -> {
                if (p.released) return;
                if (p.symbolBitmap == null) onSymbolReady(p, symbol);
            });
        }
    }

    /** The symbol of the pattern has been rendered: drawn from now on (a quick crossfade from the stand-in). */
    private void onSymbolReady(Pattern p, Bitmap b) {
        p.symbolBitmap = b;
        prewarmSymbolPasses(p, b); // the band's passes are ready before the first sweep (no first-frame build)
        p.readyAt = AnimationUtils.currentAnimationTimeMillis();
        lastSymbol = b;
        invalidate();
    }

    /** Renders the gift's pattern symbol once into a bitmap as soon as it is loaded. */
    private void ensureSymbolBitmap(Pattern p) {
        if (p == null || p.symbolBitmap != null || p.symbol == null) return;
        // the symbol is rasterized from its lottie by the gift loader; the drawable is the stand-in if that fails
        if (p.giftLoader != null && !p.giftLoader.isDone()) return;
        final org.telegram.messenger.ImageReceiver ir = p.symbol.getImageReceiver();
        // wait for the real image: the blurred thumb would be captured otherwise (soft, low-contrast symbols)
        if (ir == null || !ir.hasImageLoaded() || !ir.hasNotThumb()) return;
        final int s = dp(48);
        final Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        final Canvas bc = new Canvas(b);
        p.symbol.setBounds(0, 0, s, s);
        p.symbol.setColorFilter(null);
        p.symbol.setAlpha(255);
        ir.setCurrentAlpha(1f); // skip the load cross-fade: drawn once, it would freeze half transparent
        p.symbol.draw(bc);
        onSymbolReady(p, b);
    }

    // ---- QR colour: the backdrop's hue at the same contrast on the white plate as the tuned Neon Blue (#0075F1, 4.36:1)
    private static final double QR_CONTRAST = 4.4;

    private static double lin(double v) {return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);}

    private static double gam(double v) {return v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;}

    /** sRGB int → OKLab {L, a, b}. */
    private static void toOkLab(int c, double[] out) {
        final double r = lin(Color.red(c) / 255.0), g = lin(Color.green(c) / 255.0), b = lin(Color.blue(c) / 255.0);
        final double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        final double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        final double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        out[0] = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s;
        out[1] = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s;
        out[2] = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s;
    }

    /** OKLab → linear sRGB (unclamped) into out. */
    private static void okLabToLinear(double L, double a, double b, double[] out) {
        double l = L + 0.3963377774 * a + 0.2158037573 * b;
        double m = L - 0.1055613458 * a - 0.0638541728 * b;
        double s = L - 0.0894841775 * a - 1.2914855480 * b;
        l = l * l * l;
        m = m * m * m;
        s = s * s * s;
        out[0] = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
        out[1] = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
        out[2] = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s;
    }

    private static boolean inGamut(double[] rgb) {
        final double e = 1e-4;
        return rgb[0] >= -e && rgb[0] <= 1 + e && rgb[1] >= -e && rgb[1] <= 1 + e && rgb[2] >= -e && rgb[2] <= 1 + e;
    }

    /** The largest chroma ≤ c that is inside sRGB at (L, h); the linear rgb of it is left in rgb. */
    private static double fitChroma(double L, double c, double h, double[] rgb) {
        final double ca = Math.cos(h), sa = Math.sin(h);
        okLabToLinear(L, c * ca, c * sa, rgb);
        if (inGamut(rgb)) return c;
        double lo = 0, hi = c;
        for (int k = 0; k < 14; k++) {
            final double mid = (lo + hi) / 2;
            okLabToLinear(L, mid * ca, mid * sa, rgb);
            if (inGamut(rgb)) lo = mid;
            else hi = mid;
        }
        okLabToLinear(L, lo * ca, lo * sa, rgb);
        return lo;
    }

    private static double contrastLinear(double[] rgb) {
        final double y = 0.2126 * clamp01(rgb[0]) + 0.7152 * clamp01(rgb[1]) + 0.0722 * clamp01(rgb[2]);
        return 1.05 / (y + 0.05);
    }

    private static double clamp01(double v) {return v < 0 ? 0 : v > 1 ? 1 : v;}

    static double contrastWhite(int c) {
        final double y = 0.2126 * lin(Color.red(c) / 255.0) + 0.7152 * lin(Color.green(c) / 255.0) + 0.0722 * lin(Color.blue(c) / 255.0);
        return 1.05 / (y + 0.05);
    }

    /**
     * The QR ink for a backdrop: the edge's OKLCH hue (the centre/edge mix for near-neutral edges), chroma edge·1.25+0.02
     * (max 0.20, reduced to fit sRGB at each lightness), lightness solved so the contrast on white is 4.4:1.
     * Near-neutral backdrops get a slightly hue-tinted grey at the same contrast.
     */
    static int contrastMatchedQr(int center, int edge) {return contrastMatchedQr(center, edge, true);}

    static int contrastMatchedQr(int center, int edge, boolean cap) {
        final double[] e = new double[3], m = new double[3], rgb = new double[3];
        toOkLab(edge, e);
        toOkLab(center, m);
        final double lCap = e[0] < 0.45 ? e[0] - 0.03 : 2.0; // dark backdrops only: never lighter than the edge
        final double edgeC = Math.hypot(e[1], e[2]);
        double h, c;
        if (edgeC < 0.03) {
            final double a = m[1] * 0.4 + e[1] * 0.6, b = m[2] * 0.4 + e[2] * 0.6;
            h = Math.atan2(b, a);
            c = Math.min(0.012, Math.hypot(a, b)); // a hint of the hue, still a grey
        } else {
            h = Math.atan2(e[2], e[1]);
            c = Math.min(edgeC * 1.25 + 0.02, 0.20);
        }
        double lo = 0.2, hi = 0.9; // contrast falls as L rises
        for (int k = 0; k < 12; k++) {
            final double mid = (lo + hi) / 2;
            fitChroma(mid, c, h, rgb);
            if (contrastLinear(rgb) > QR_CONTRAST) lo = mid;
            else hi = mid;
        }
        // pick the end of the final bracket closest to the target (the bracket is ~0.0002 wide)
        double L = (lo + hi) / 2;
        if (cap && L > lCap)
            L = Math.max(0.05, lCap); // dark backdrops: the code stays darker (contrast rises above 4.4)
        fitChroma(L, c, h, rgb);
        return Color.rgb((int) Math.round(clamp01(gam(clamp01(rgb[0]))) * 255), (int) Math.round(clamp01(gam(clamp01(rgb[1]))) * 255), (int) Math.round(clamp01(gam(clamp01(rgb[2]))) * 255));
    }

    /** center, edge, pattern of gift i. */
    private void giftColors(int i, int[] out) {
        out[0] = 0xFF888888;
        out[1] = 0xFF555555;
        out[2] = 0xFF333333;
        for (TL_stars.StarGiftAttribute a : gifts.get(i).attributes) {
            if (a instanceof TL_stars.starGiftAttributeBackdrop) {
                final TL_stars.starGiftAttributeBackdrop b = (TL_stars.starGiftAttributeBackdrop) a;
                out[0] = b.center_color | 0xFF000000;
                out[1] = b.edge_color | 0xFF000000;
                out[2] = b.pattern_color | 0xFF000000;
            }
        }
    }

    private final int[] giftCol = new int[3];

    /** Whose collectible gifts the sheet offers as a theme: the own ones, or those of a chat whose theme is set. */
    public void setGiftsOwner(long dialogId) {
        if (giftsOwner == dialogId) return;
        giftsOwner = dialogId;
        if (giftsList != null) giftsList.cancel();
        // its own list, not the profile's (whose filters the user sets there): only what the server takes as a theme,
        // the unique gifts, shown on the profile or hidden
        giftsList = new StarsController.GiftsList(currentAccount, dialogId, false);
        giftsList.setFilters(StarsController.GiftsList.INCLUDE_TYPE_UNIQUE_FLAG
            | StarsController.GiftsList.INCLUDE_VISIBILITY_DISPLAYED_FLAG | StarsController.GiftsList.INCLUDE_VISIBILITY_HIDDEN_FLAG); // loads the first page
        loadGifts();
    }

    private @Nullable StarsController.GiftsList giftsList;
    /** A page of the list has come: the sheet takes it and asks for the next one until the end. */
    private final org.telegram.messenger.NotificationCenter.NotificationCenterDelegate giftsObserver = (id, account, args) -> {
        if (args.length > 1 && args[1] == giftsList) loadGifts();
    };

    /** The saved id of the theme's gift among the owner's gifts, 0 if it is not among them. */
    public long getThemeGiftSavedId() {
        final TL_stars.TL_starGiftUnique gift = getThemeGift();
        final Long id = gift != null ? giftSavedIds.get(gift.id) : null;
        return id != null ? id : 0;
    }

    private AnimatedEmojiDrawable giftThumb(long id, TLRPC.Document model) {
        AnimatedEmojiDrawable d = giftThumbs.get(id);
        if (d == null) {
            // 140 dp frame cache (was ~40 dp for ALERT_PREVIEW_LARGE, upscaled ×2.5 on a ~95 dp tile: smeared); still cached
            // frames, not live Lottie, so a screen of tiles stays cheap
            d = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_LARGE_140, model);
            d.addView(this);
            giftThumbs.put(id, d);
        }
        return d;
    }

    /** Starts loading the first tiles' gift models and patterns as soon as the list is known, so the first open of the
     *  gifts layer doesn't pop them in a frame or two after the reveal (the "blink"). */
    private void prewarmGiftDrawables() {
        for (int i = 0; i < Math.min(gifts.size(), 9); i++) { // the first visible rows only
            final TL_stars.TL_starGiftUnique g = gifts.get(i);
            for (TL_stars.StarGiftAttribute a : g.attributes) {
                if (a instanceof TL_stars.starGiftAttributeModel && ((TL_stars.starGiftAttributeModel) a).document != null) {
                    giftThumb(g.id, ((TL_stars.starGiftAttributeModel) a).document);
                }
            }
            tilePattern(g);
        }
    }

    private final Runnable prewarmGiftsRunnable = this::prewarmGiftDrawables;
    private boolean giftsPrewarmed;

    private void loadGifts() {
        final StarsController.GiftsList list = giftsList;
        if (list == null) return;
        final long selectedId = pattern.type == PAT_GIFT && pattern.giftIndex >= 0 && pattern.giftIndex < gifts.size() ? gifts.get(pattern.giftIndex).id : 0;
        final TL_stars.TL_starGiftUnique selected = selectedId != 0 ? gifts.get(pattern.giftIndex) : null;
        gifts.clear();
        giftSavedIds.clear();
        for (TL_stars.SavedStarGift sg : list.gifts) {
            // the server takes only a unique gift that is neither refunded nor burned (COLLECTIBLE_INVALID)
            if (sg.gift instanceof TL_stars.TL_starGiftUnique && !sg.refunded && !((TL_stars.TL_starGiftUnique) sg.gift).burned) {
                gifts.add((TL_stars.TL_starGiftUnique) sg.gift);
                giftSavedIds.put(sg.gift.id, sg.saved_id);
            }
        }
        if (selected != null) {
            // the gift of the theme keeps its place in the list, so the pattern still points at it
            int at = -1;
            for (int k = 0; k < gifts.size(); k++)
                if (gifts.get(k).id == selectedId) {
                    at = k;
                    break;
                }
            if (at < 0) {
                gifts.add(0, selected);
                at = 0;
            }
            pattern.giftIndex = at;
            selGift = at;
        }
        // the next pages, one after another until the end (each page comes back through giftsObserver)
        giftsEndReached = list.endReached;
        if (!list.endReached && !list.loading) list.load();
        // prewarm the first tiles' drawables, but never on the open itself: once, after the intro has settled
        if (!gifts.isEmpty() && !giftsPrewarmed) {
            giftsPrewarmed = true;
            removeCallbacks(prewarmGiftsRunnable);
            postDelayed(prewarmGiftsRunnable, 900);
        }
        invalidate();
    }

    // the fee code is cached across opens (the first frame only draws); the free code is built off the UI thread
    private static String cachedFeeFor;
    private static QrCodeLayer.Code cachedFee;

    private void rebuildCodes() {
        final String base = baseLink;
        if (base.equals(cachedFeeFor) && cachedFee != null) {
            feeCode = cachedFee;
        } else {
            feeCode = QrCodeLayer.build(base, dp(240));
            cachedFee = feeCode;
            cachedFeeFor = base;
        }
    }

    // ---------------------------------------------------------------------------------------------------- actions

    private float cardCenterX() {return getWidth() / 2f;}

    /** Recolour from the QR card's centre, with the card bounce in the same frame. */
    private void setSceneFromCard(Scene s) {
        setScene(s, cardCenterX(), cardTop() + cardH() / 2f);
        if (instantTheme) return;
        qrBounceFrom = bounce(qrBounceStart, 0.03f, 0.005f);
        qrBounceStart = AnimationUtils.currentAnimationTimeMillis();
    }

    private void setScene(Scene s, float x, float y) {
        if (s != null) {rimLightColor = rimLightFor(s.edge);} // rims follow the backdrop (shaders rebuild once)
        if (instantTheme) {
            // the cached theme on open: shown as it is, no wave from the default
            if (oldScene != null) {
                releaseScene(oldScene);
                oldScene = null;
            }
            scene = s;
            wave = 1f;
            invalidate();
            return;
        }
        if (wave < 1f && oldScene != null) {
            // a wave is still running: let the current target become the base
            releaseScene(oldScene);
        }
        oldScene = scene;
        scene = s;
        wave = 0f;
        waveStart = System.currentTimeMillis();
        waveX = x;
        waveY = y;
        waveId++;
        // no whole-field glide: the wave front pushes each item away from the QR on its own (place())
        haptic();
        invalidate();
    }

    private void releaseScene(Scene s) {
        // scenes hold only shaders now; the drawables live in Pattern
    }

    private static final long FILL_MS = 340, BURST_AT_MS = 265; // burst + both bounces at ~78% of the fill, together
    private static final long BOUNCE_MS = 320, BOUNCE_PEAK_MS = 80;
    private long chipBounceStart, qrBounceStart, fillEndsAt;

    /** 1 → 1+a → 1−b → 1 over 320 ms (peak at 25%), one small undershoot. */
    private static float bounce(long start, float a, float b) {
        if (start == 0) return 1f;
        final float u = (AnimationUtils.currentAnimationTimeMillis() - start) / (float) BOUNCE_MS;
        if (u <= 0f || u >= 1f) return 1f;
        if (u < 0.25f) return 1f + a * (float) Math.sin(Math.PI / 2 * u / 0.25f);
        if (u < 0.6f) {
            final float k = (u - 0.25f) / 0.35f;
            return lerp(1f + a, 1f - b, k * k * (3f - 2f * k));
        }
        final float k = (u - 0.6f) / 0.4f;
        return lerp(1f - b, 1f, k * k * (3f - 2f * k));
    }

    public boolean feeMenuOpen() {return menuTarget > 0.5f || menu > 0.5f;}

    public void closeFeeMenu() {
        menuTarget = 0;
        invalidate();
    }

    /** The fee chip's tap: remove the fee, or (already free) open the fee menu. */
    private void chipAction() {
        if (!chipShown) return;
        if (!free) makeFree();
        else {
            menuTimerOn = timerOn; // snapshot the rows for this open/close
            menuTarget = 1;
            hideToast(true); // the menu never shares the screen with a toast: the fast hide
            haptic();
        }
    }

    private void openLayer(int t) {
        if (!themeEditable) return;
        // gifts as a theme are for Premium only; the colours and the emoji presets are for everyone
        if (t == SCENE_GIFT && !org.telegram.messenger.UserConfig.getInstance(currentAccount).isPremium()) {
            showToast(0, org.telegram.messenger.LocaleController.getString(R.string.QrPremiumGiftsToast), org.telegram.messenger.LocaleController.getString(R.string.QrPremiumSubscribe));
            haptic();
            return;
        }
        if (t == SCENE_GIFT && (tab != t || sheet < 0.5f)) giftOpenAt = AnimationUtils.currentAnimationTimeMillis();
        tab = t;
        tabX = tab;
        sheetScroll = 0;
        scroller.forceFinished(true);
        if (sheet > 0.5f) cascadeAt = AnimationUtils.currentAnimationTimeMillis(); // a tab switch cascades too
        if (sheetTarget < 0.5f) revealOpenAt = AnimationUtils.currentAnimationTimeMillis();
        sheetTarget = 1;
        hideToast(true); // a panel opening takes the place of the toast
        if (tab == SCENE_GIFT) loadGifts();
        haptic();
    }

    private void makeFree() {
        free = true;
        shatterAt = 0;
        timerOn = true;
        startTimerWait();
        startSweep(1f);
        sweepDone = false;
        chipBurstPending = true;
        // the fill runs first; at its end the chip peaks and the code bursts, the QR bounce follows ~35 ms later
        final long now = AnimationUtils.currentAnimationTimeMillis();
        chipBounceStart = now;            // all from the tap: fill, trail, chip bounce now; the QR 40 ms later
        fillEndsAt = System.currentTimeMillis() + FILL_MS;
        // the code of the timer comes from the server (setFreeLink)
        freeLink = null;
        freeCode = null;
        lastChip = null;
        updateChipText(false);
        showToast(R.drawable.msg_recent, expiryText());
        haptic();
        if (delegate != null) delegate.onFeeMode(FEE_TIMER);
    }

    private void startSweep(float to) {
        sweepFrom = chipSweep;
        sweepTo = to;
        sweepStart = System.currentTimeMillis();
    }

    /** The one QR swap: the dust swap (MODE_DUST), 40 ms after the action, with the QR bounce in parallel. */
    private void swapCode(QrCodeLayer.Code code) {
        if (qr.inIntro()) {
            qr.replaceSilently(code);
            invalidate();
            return;
        } // on open: only the reveal, never the swap dust
        postDelayed(() -> {
            if (qr.inIntro()) {
                qr.replaceSilently(code);
                invalidate();
                return;
            }
            // the swap and the card bounce start in the same frame; a running bounce restarts from its current scale
            final long now = AnimationUtils.currentAnimationTimeMillis();
            qrBounceFrom = bounce(qrBounceStart, 0.03f, 0.005f);
            qrBounceStart = now;
            qr.swapTo(code);
        }, 40);
    }

    private float qrBounceFrom = 1f;

    // restore: the whole white fill shatters at once (alpha 1→0 in 60 ms, ~260 droplets), content inverts in 80 ms
    private long shatterAt;
    private final RectF shatterRect = new RectF();
    private boolean shatterPending;

    private float restoreFreeW;

    private void restoreFee(String message) {
        if (!free) return;
        // the collapse starts from the free chip's width as it is now, frozen: the label's own text animation must
        // not move the right edge while the pill shrinks back to the fee
        restoreFreeW = dp(CHIP_PAD_L) + dp(STAR_DP) + dp(STAR_GAP) + chipText.getCurrentWidth() + dp(11);
        free = false;
        timerWaiting = false;
        shatterPending = chipSweep * burnFrac > 0.001f;
        shatterAt = AnimationUtils.currentAnimationTimeMillis();
        startSweep(0f);
        if (feeCode != null) swapCode(feeCode);
        updateChipText(true);
        if (message != null) showToast(0, message);
        haptic();
        freeLink = null;
        freeCode = null;
        if (delegate != null) delegate.onFeeMode(FEE_PAID);
    }

    private void resetCode() {
        if (timerOn) startTimerWait();
        lastChipSec = -1;
        // the new code comes from the server (setFreeLink)
        if (delegate != null) delegate.onResetCode();
        updateChipText(true);
        // "New code created. This code will expire in 1 minute." does not fit the toast: the expiry line + timer icon
        if (timerOn) showToast(R.drawable.msg_recent, expiryText());
        else
            showToast(R.drawable.msg_retry, org.telegram.messenger.LocaleController.getString(R.string.QrNoPaidToastNewCode));
        haptic();
    }

    private void toggleTimer() {
        timerOn = !timerOn;
        if (timerOn) startTimerWait();
        else timerWaiting = false;
        lastChipSec = -1;
        updateChipText(true);
        showToast(R.drawable.msg_recent, timerOn ? expiryText() : org.telegram.messenger.LocaleController.getString(R.string.QrNoPaidToastNoTimeLimit));
        if (delegate != null) delegate.onFeeMode(timerOn ? FEE_TIMER : FEE_PERMANENT);
    }

    /** Stars a message from a non-contact costs; 0 hides the chip and brings the fee code back. */
    public void setFeeStars(long stars) {
        chipShown = stars > 0;
        // "Remove ★50 fee": the star stands where the template has it
        final String label = stars > 0 ? org.telegram.messenger.LocaleController.formatString(R.string.QrNoPaidRemoveStarsFee, org.telegram.messenger.LocaleController.formatNumber(stars, ',')) : "";
        final int star = label.indexOf('★');
        feeA = star >= 0 ? label.substring(0, star) : "";
        feeB = star >= 0 ? label.substring(star + 1) : label;
        if (!chipShown && free) restoreFee(null);
        invalidate();
    }

    /** The free mode kept from the last time: the chip opens already white, the code follows from the server. */
    public void restoreFreeMode(boolean timer) {
        free = true;
        timerOn = timer;
        if (timer) startTimerWait();
        sweepFrom = sweepTo = chipSweep = 1f;
        sweepDone = true;
        burnFrac = 1f;
        lastChip = null;
        lastChipSec = -1;
        updateChipText(false);
        invalidate();
    }

    /** The free code could not be made: the fee comes back with the error. */
    public void cancelFree(String error) {
        if (free) {
            restoreFee(error != null ? org.telegram.messenger.LocaleController.getString(R.string.ErrorOccurred) : null);
        } else if (error != null) {
            showError(error);
        }
    }

    /** A request failed: a generic error (the server's code is in the log). */
    public void showError(String error) {
        showToast(R.drawable.msg_warning, org.telegram.messenger.LocaleController.getString(R.string.ErrorOccurred));
    }

    private long toastDelayUntil;

    // the expiry toast: its number in Medium, the rest Regular (three runs on one baseline)
    private String expiryText, expA, expB, expC;
    private final TextPaint toastMedium = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    {toastMedium.setTypeface(AndroidUtilities.bold());}

    private String expiryText() {
        final String t = org.telegram.messenger.LocaleController.getString(R.string.QrNoPaidToastExpires);
        if (!t.equals(expiryText)) {
            expiryText = t;
            // the first number of the copy (the minute) is the Medium run
            final java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(t);
            if (m.find()) {
                expA = t.substring(0, m.start());
                expB = m.group();
                expC = t.substring(m.end());
            } else {
                expA = t;
                expB = expC = "";
            }
        }
        return expiryText;
    }

    private boolean isExpiry(String t) {return t != null && t.equals(expiryText);}

    private float toastTextWidth(String t) {
        final int nl = t.indexOf('\n');
        if (nl >= 0) return Math.max(text.measureText(t, 0, nl), text.measureText(t, nl + 1, t.length()));
        if (!isExpiry(t)) return text.measureText(t);
        toastMedium.setTextSize(text.getTextSize());
        return text.measureText(expA) + toastMedium.measureText(expB) + text.measureText(expC);
    }

    private void drawToastText(Canvas c, String t, float x, float y) {
        final int nl = t.indexOf('\n');
        if (nl >= 0) { // two lines, 18dp apart, centred on the single-line baseline
            c.drawText(t, 0, nl, x, y - dp(9), text);
            c.drawText(t, nl + 1, t.length(), x, y + dp(9), text);
            return;
        }
        if (!isExpiry(t)) {
            c.drawText(t, x, y, text);
            return;
        }
        toastMedium.setTextSize(text.getTextSize());
        toastMedium.setColor(text.getColor());
        c.drawText(expA, x, y, text);
        x += text.measureText(expA);
        c.drawText(expB, x, y, toastMedium);
        x += toastMedium.measureText(expB);
        c.drawText(expC, x, y, text);
    }

    private void showToast(int icon, String message) {
        showToast(icon, message, null);
    }

    private void showToast(int icon, String message, String action) {
        final long delay = toastDelayUntil - System.currentTimeMillis();
        if (delay > 0) {
            postDelayed(() -> showToast(icon, message, action), delay);
            return;
        }
        toastIcon = icon;
        toastText = message;
        toastAction = action;
        toastUntil = System.currentTimeMillis() + (action != null ? 4000 : 2800);
        invalidate();
    }

    private String lastChip;
    private int lastChipSec = -1;

    private void updateChipText(boolean animated) {
        String s;
        if (!free) {
            return; // the fee text is static; the free text keeps its last value while sweeping out
        } else if (!timerOn) {
            s = org.telegram.messenger.LocaleController.getString(R.string.QrNoPaidFree) + " · " + org.telegram.messenger.LocaleController.getString(R.string.QrNoPaidNoTimeLimit);
            lastChipSec = -1;
        } else {
            final long left = timerLeft(System.currentTimeMillis());
            final int sec = (int) Math.ceil(left / 1000.0);
            if (sec == lastChipSec && lastChip != null) return; // format only when the second changes
            lastChipSec = sec;
            s = org.telegram.messenger.LocaleController.getString(R.string.QrNoPaidFree) + " · " + org.telegram.messenger.LocaleController.formatString(R.string.QrNoPaidExpiresIn, String.format(java.util.Locale.US, "%d:%02d", sec / 60, sec % 60));
        }
        if (!s.equals(lastChip)) {
            lastChip = s;
            chipText.setText(s, animated);
        }
    }

    private void haptic() {
        try {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        } catch (Exception ignore) {}
    }

    // ---------------------------------------------------------------------------------------------------- layout helpers

    private float cardW() {return getWidth() * 0.745f;}

    private float cardH() {return cardW() * lerp(1.07f, 1f, tempK);} // temporary link: no username line, a square card

    // ---- temporary link (no public username and the phone hidden): the code leads to a contact token, valid for
    // minutes and replaced by the server when it expires (see QrShareServer); a square card without the caption
    private boolean tempLink;
    private float tempK, tempFrom, tempTarget = -1f;
    private long tempKAt, linkExpires;
    /** Nothing has been drawn yet: the first states are set without animations. */
    private boolean drawnOnce;

    /** The code leads to a contact token: the square card, and without a fee the expiry chip. */
    public void setTemporaryLink(boolean temporary) {
        if (tempLink == temporary) return;
        tempLink = temporary;
        if (!drawnOnce) {
            tempK = tempTarget = temporary ? 1f : 0f;
        }
        invalidate();
    }

    /** The expiry of the contact token on the code (unix time), 0 while it is on its way. */
    public void setLinkExpires(long expires) {
        if (linkExpires == expires) return;
        // a replaced token: the code swap follows from setLinks
        if (linkExpires != 0 && expires != 0) haptic();
        linkExpires = expires;
        lastTempChipSec = -1;
        invalidate();
    }

    static final float REVEAL_MS = 420f, REVEAL_FROM_COLORS = 0.8f, REVEAL_FROM_GIFTS = 0.75f;
    /** Each swatch / gift tile also grows on its own, REVEAL_ITEM_FROM → 1, 12 ms later per visible row, 300 ms. */
    static final float REVEAL_ITEM_FROM = 0.82f;

    private float revealItemScale(float rt, float row) {
        final float lu = Utilities.clamp01((rt * REVEAL_MS - Math.max(0f, Math.min(10f, row)) * 12f) / (REVEAL_MS - 120f));
        return REVEAL_ITEM_FROM + (1f - REVEAL_ITEM_FROM) * (1f - (1f - lu) * (1f - lu) * (1f - lu));
    }

    private long revealOpenAt;

    /** 0..1 time-based reveal of the opening sheet's content (linear; callers apply their own curve); 1 when not opening. */
    public float revealTime() {
        if (sheetTarget < 0.5f) return 1f;
        return Utilities.clamp01((AnimationUtils.currentAnimationTimeMillis() - revealOpenAt) / REVEAL_MS);
    }

    // ---- the fee chip appears and leaves with paid messages (without a charge for messages there is no fee to
    // remove, no free-timer states; the card settles into the chip's space)
    private float paidK, paidFrom, paidTarget = -1f;
    private long paidKAt;

    private void updatePaid() {
        final float target = chipShown ? 1f : 0f;
        final long now = AnimationUtils.currentAnimationTimeMillis();
        if (paidTarget < 0f) {
            paidK = paidTarget = target;
            return;
        } // the first state: no animation
        // timed 320 ms ease-in-out cubic (the exponential τ 160 ms lost half the chip in the first ~100 ms: read as
        // an abrupt cut), the same for on and off; the card's 16 dp settle follows the same curve
        if (target != paidTarget) {
            paidTarget = target;
            paidFrom = paidK;
            paidKAt = now;
        }
        if (paidK != target) {
            final float u = Utilities.clamp01((now - paidKAt) / 320f);
            final float e = u < 0.5f ? 4f * u * u * u : 1f - (float) Math.pow(-2f * u + 2f, 3) / 2f;
            paidK = u >= 1f ? target : lerp(paidFrom, target, e);
            invalidate();
        }
    }

    private void updateTempLink() {
        final float target = tempLink ? 1f : 0f;
        final long now = AnimationUtils.currentAnimationTimeMillis();
        // timed (300 ms, ease-in-out cubic) with the code swap
        if (target != tempTarget) {
            // the username bursts only on a real username → temporary morph; back from the square: a plain fade-in
            if (target == 1f && tempTarget == 0f && tempK <= 0.001f) {
                nameBurst = true;
                nbLayout = null;
            } else if (target == 0f && tempK >= 0.999f) nameBurst = false;
            tempTarget = target;
            tempFrom = tempK;
            tempKAt = now;
        }
        if (tempK != target) {
            final float u = Utilities.clamp01((now - tempKAt) / 300f);
            final float e = u < 0.5f ? 4f * u * u * u : 1f - (float) Math.pow(-2f * u + 2f, 3) / 2f;
            tempK = u >= 1f ? target : lerp(tempFrom, target, e);
            if (tempK <= 0f) nameBurst = false; // reversed mid-way and fully back: the name is whole again
            invalidate();
        }
    }

    private long lastTempChipSec = -1;
    private String tempChipText = "";

    /** The expiry in our glass chip style (no white fill), counting down; stands in for the fee chip without a fee. */
    private void drawTempChip(Canvas c, int w, float a) {
        final long nowMs = System.currentTimeMillis();
        final long left = linkExpires > 0 ? Math.max(0, linkExpires - nowMs / 1000 - 1) : -1;
        if (left != lastTempChipSec) {
            // a new text once a second, not every frame
            lastTempChipSec = left;
            tempChipText = left >= 0
                ? org.telegram.messenger.LocaleController.formatString(R.string.QrNoPaidExpiresIn, String.format(java.util.Locale.US, "%d:%02d", left / 60, left % 60))
                : org.telegram.messenger.LocaleController.getString(R.string.QrCreatingLink);
        }
        final String s = tempChipText;
        chipPaint.setTextSize(dp(11));
        final float cw = dp(FEE_PAD) + chipPaint.measureText(s) + dp(FEE_PAD), ch = dp(22), cy = chipCY();
        rect.set((w - cw) / 2f, cy - ch / 2f, (w + cw) / 2f, cy + ch / 2f);
        final boolean light = scene != null && scene.light;
        glassButton = true;
        glassSmall = true;
        drawGlass(c, rect, ch / 2f, light, a);
        glassButton = false;
        glassSmall = false;
        final int onGlass = light ? 0xE6000000 : 0xF2FFFFFF;
        chipPaint.setColor(Theme_multAlpha(onGlass, a));
        c.drawText(s, rect.left + dp(FEE_PAD), cy + dp(4), chipPaint);
        if (linkExpires > 0) postInvalidateDelayed(1000 - nowMs % 1000 + 5); // the next second
    }

    /** The sheet ends below the fee chip at the card's open position (card + chip always fit above it). */
    private float sheetH() {
        final float openChipBottom = insetTop + dp(51) + cardH() + dp(24) + dp(11);
        final float full = Math.max(dp(160), sheetBottom() - (openChipBottom + dp(CARD_CHIP_GAP)));
        // a few gifts (1–3) take one row, none the empty state; the height eases (time-based)
        float target = full;
        if (tab == SCENE_GIFT && giftsEndReached && gifts.size() <= 3) {
            final float rowH = (getWidth() - dp(SHEET_INSET) * 2 - dp(8)) / 3f;
            target = Math.min(full, gifts.isEmpty() ? dp(EMPTY_H) : rowH + dp(8));
        }
        final long now = AnimationUtils.currentAnimationTimeMillis();
        if (sheetHCur < 0 || sheet < 0.01f) {
            sheetHCur = target;
        } else if (sheetHCur != target) {
            final long dt = Math.min(64, Math.max(0, now - sheetHAt));
            sheetHCur += (target - sheetHCur) * (1f - (float) Math.exp(-dt / 140f));
            if (Math.abs(target - sheetHCur) < 0.5f) sheetHCur = target;
            else invalidate();
        }
        sheetHAt = now;
        return sheetHCur;
    }

    private float sheetHCur = -1f;
    static final int CARD_CHIP_GAP = 13; // card bottom → chip top (chip centre 24dp below the card, 22dp tall)
    private long sheetHAt;
    static final int EMPTY_H = 286; // the centred group (99 dog + 22 + title + 7 + line + 20 + 36 pill) + balanced padding

    private boolean giftsEmpty() {return giftsEndReached && gifts.isEmpty();}

    private int giftCount() {return gifts.size();}

    /** Like the profile (ProfileGiftsContainer: span = min(3, count)): 1 gift fills the row, 2 split it. */
    private int giftCols() {
        final int n = giftCount();
        return giftsEndReached && n > 0 && n < 3 ? n : 3;
    }

    private float cardTop() {
        // raised 15dp at rest; no chip under the card → settles 16dp lower. Any chip counts (the expiry chip of the
        // temporary link too), so toggling paid messages without a username doesn't move the code
        final float rest = getHeight() * 0.468f - cardH() / 2f - dp(15) + dp(16) * (1f - Math.max(paidK, tempK));
        // a layer open: the card rises only as far as needed — card, chip and the layer stacked with one gap
        // (the card→chip gap, 13dp) — never above the old open position, never below its resting one
        final float chipK = Math.max(paidK, tempK); // the chip/expiry pill under the card (absent: paid messages off)
        final float layerTop = sheetBottom() - sheetH();
        final float need = layerTop - dp(CARD_CHIP_GAP) - dp(35) * chipK - cardH(); // 35 = gap + chip height
        final float open = Math.max(insetTop + dp(51), Math.min(rest, need));
        return lerp(rest, open, sheet);
    }

    private float chipCY() {return cardTop() + cardH() + dp(24);}

    private float barCY() {return getHeight() - insetBottom - dp(40);}

    // ---------------------------------------------------------------------------------------------------- share

    /** The longest share image, height to width (as the QR screen used to share). */
    private static final float SHARE_MAX_ASPECT = 1.92f;
    /** The frame of the plane logo for a shared image, as the stock QR screen takes it. */
    private static final int LOGO_OPTIMAL_FRAME = 33;
    private @Nullable Bitmap logoOptimal;
    private final Paint logoOptimalPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private boolean sharing;

    /** The plane at its best frame (the stock QR screen's frame), rendered once from the same animation. */
    private void ensureLogoOptimal() {
        if (logoOptimal != null || logo.getIntrinsicWidth() <= 0) return;
        org.telegram.ui.Components.RLottieNative frames = null;
        try {
            frames = org.telegram.ui.Components.RLottieNative.createFromRawJson(AndroidUtilities.readRes(R.raw.plane_logo_plain));
            if (frames == null) return;
            final Bitmap b = Bitmap.createBitmap(logo.getIntrinsicWidth(), logo.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
            frames.getFrame(Math.min(LOGO_OPTIMAL_FRAME, frames.getFrameCount() - 1), b, true);
            logoOptimal = b;
        } catch (Throwable e) {
            org.telegram.messenger.FileLog.e(e);
        } finally {
            if (frames != null) frames.recycle();
        }
    }

    /**
     * The QR as an image to share: the backdrop with its pattern and the card at its rest position, without the
     * controls; cropped around the card to at most {@link #SHARE_MAX_ASPECT}. Null before the first layout.
     */
    @Nullable
    public Bitmap renderShareImage() {
        final int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return null;
        final float wasSheet = sheet;
        sheet = 0f; // the card at rest, as when nothing is open
        final int height = Math.min(h, (int) (w * SHARE_MAX_ASPECT));
        final float cardCenter = cardTop() + cardH() / 2f;
        final int top = (int) Math.max(0, Math.min(h - height, cardCenter - height / 2f));
        final Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(w, height, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            sheet = wasSheet;
            return null;
        }
        ensureLogoOptimal();
        final Canvas c = new Canvas(bitmap);
        c.translate(0, -top);
        drawBackground(c, w, h, System.currentTimeMillis());
        sharing = true;
        drawCard(c, w);
        sharing = false;
        c.setBitmap(null);
        sheet = wasSheet;
        return bitmap;
    }

    // ---------------------------------------------------------------------------------------------------- drawing

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        drawnOnce = true;
        updateTempLink();
        updatePaid();
        // the open's first frame: the pattern's items whose frames are already there show at once (high-end devices)
        immediateFrame = !openDone && openFrames == 0 && !sharing && immediateOnOpen;
        onDrawInner(canvas);
        immediateFrame = false;
        if (!openDone) openFrames++;
    }

    /** Frames drawn during the open; immediateFrame: this draw is its first one and items may show at once. */
    private int openFrames;
    private boolean immediateFrame;

    private void onDrawInner(@NonNull Canvas canvas) {
        final long now = System.currentTimeMillis();
        final int w = getWidth(), h = getHeight();
        step(now);
        stepTilt();
        // the glass glint exists only while there is a pattern to shine on (fades with a swap to / from "none")
        glintGate = approach(glintGate, pattern != null && pattern.type != PAT_NONE ? 1f : 0f, 0.12f);

        // open/close: the whole layer (backdrop, pattern, card, controls) is one unit — rise, scale and fade are
        // view properties (one hardware layer + a rounded-corner outline), see setReveal()
        final boolean revealing = false;
        final boolean glass = QrRender.hasEffects() && canvas.isHardwareAccelerated();
        // the blur only once the open has finished (a separate offscreen pipeline, GPU programs of its own); until then
        // the glass shows the backdrop picture itself (see drawBlurStandIn), then the blur replaces it at once
        if (blurAllowed() && blurNow() && canvas.isHardwareAccelerated()) {
            if (bgNode == null) {
                bgNode = QrRender.createNode("qrBg");
                blurNode = QrRender.createNode("qrBlur");
                // the glass blur runs at 1/4 resolution (16× fewer pixels to blur), drawn back scaled ×4
                QrRender.setBlur(blurNode, dpf2(17.6f) / BLUR_DS, Shader.TileMode.CLAMP); // 22dp × 0.8
            }
            drawBackground(QrRender.beginRecording(bgNode, w, h), w, h, now);
            QrRender.endRecording(bgNode);
            if (blurBuiltVersion != RimTune.version) {
                // the glass blur (built once)
                blurBuiltVersion = RimTune.version;
                QrRender.setBlur(blurNode, Math.max(0.5f, dpf2(RimTune.v[RimTune.BLUR]) / BLUR_DS), Shader.TileMode.CLAMP);
            }
            final Canvas bc = QrRender.beginRecording(blurNode, (w + BLUR_DS - 1) / BLUR_DS, (h + BLUR_DS - 1) / BLUR_DS);
            bc.scale(1f / BLUR_DS, 1f / BLUR_DS);
            QrRender.draw(bc, bgNode);
            QrRender.endRecording(blurNode);
            QrRender.draw(canvas, bgNode);
        } else {
            drawBackground(canvas, w, h, now);
        }

        drawCard(canvas, w);
        final float ui = elem(0); // everything starts on the same frame
        uiK = ui;
        // the controls appear by scale only (elemScale): no fade, no offscreen layers around the groups
        drawTopBar(canvas, glass);
        drawBottomBar(canvas, glass);
        if (menu > 0.001f) {
            drawMenu(canvas, glass); // the chip itself becomes the menu: one shape
        } else {
            final float wob = chipWobble();
            float cb = bounce(chipBounceStart, 0.06f, 0.02f) * elemScale(elem(0));
            if (shatterAt > 0 && !free) {
                final float pu = (AnimationUtils.currentAnimationTimeMillis() - shatterAt) / 180f;
                if (pu < 1f) {
                    cb *= 1f + 0.04f * (float) Math.sin(Math.PI * pu);
                    invalidate();
                }
            }
            final boolean tr = wob != 0f || cb != 1f;
            if (tr) {
                canvas.save();
                canvas.scale((1f + wob * 0.5f) * cb, (1f - wob) * cb, chipRect.centerX(), chipRect.centerY());
            }
            drawChip(canvas, w, now);
            if (tr) canvas.restore();
        }
        drawSheet(canvas, glass, w, h);
        drawToast(canvas, glass, w, h, now);
        if (revealing) canvas.restoreToCount(revealSave);

        updateEmojiPanel();
        invalidate(); // the backdrop is alive (floating pattern, timer)
    }

    private long lastStep;
    private float frameK = 1f; // frame-rate independence for the exponential approaches

    private void step(long now) {
        final float dtf = lastStep == 0 ? 1f : Math.min(3f, (now - lastStep) / 16.667f);
        lastStep = now;
        frameK = dtf;
        if (wave < 1f) {
            final float was = wave;
            wave = Math.min(1f, (now - waveStart) / (float) WAVE_MS);
            if (was < 0.3f && wave >= 0.3f && delegate != null)
                delegate.onSceneLight(scene.light); // nav bar follows when the front reaches it
            if (wave >= 1f) {
                releaseScene(oldScene);
                oldScene = null;
            }
        }
        stepSheet();
        selA = approach(selA, selTarget, 0.25f);
        stepSwatchSelection();
        stepMenu();
        tabX = approach(tabX, tab, 0.25f);
        final boolean showToast = toastText != null && now < toastUntil;
        toast = approach(toast, showToast ? 1f : 0f, showToast ? 0.2f : (menuTarget > 0.5f ? 0.4f : 0.16f));
        {
            // back to the fee: 380 ms ease-out cubic (was 460 ms smoothstep — its slow start held the edge, then snapped)
            final long dur = sweepTo > sweepFrom ? FILL_MS : 380;
            final float lin = Math.min(1f, (now - sweepStart) / (float) dur);
            final float e = 1f - (float) Math.pow(1f - lin, 3);
            chipSweep = lerp(sweepFrom, sweepTo, e);
            if (free && chipSweep >= 0.999f) sweepDone = true;
        }
        if (free) {
            burnFrac = timerOn ? Utilities.clamp01(timerLeft(now) / (float) FREE_MS) : 1f;
            if (timerOn && !timerWaiting && now >= freeUntil) {
                restoreFee(org.telegram.messenger.LocaleController.getString(R.string.QrNoPaidToastExpired));
            }
        }
        updateChipText(true);
    }

    private float approach(float v, float target, float k) {
        final float kk = 1f - (float) Math.pow(1f - k, frameK);
        final float n = v + (target - v) * kk;
        return Math.abs(target - n) < 0.002f ? target : n;
    }

    // --- background + wave

    private void drawBackground(Canvas c, int w, int h, long now) {
        final boolean waving = wave < 1f && oldScene != null && scene != null;
        drawBackdrop(c, w, h, waving);
        drawPattern(c, w, h);
        if (waving && backdropRingOn) drawBackdropBitmap(c, backdropRing); // the wave's ring glows over the pattern
    }

    // the backdrop as a picture: a low-res bitmap of the gradient, generated natively and upscaled (bilinear) by the
    // GPU as a plain bitmap: no gradient shader to compile on the first open or to run for every pixel of every frame;
    // regenerated only when its inputs change (another scene, the card moving the radial centre, the wave)
    private static final float SIN18 = (float) Math.sin(Math.toRadians(18)), COS18 = (float) Math.cos(Math.toRadians(18));
    private static final float BACKDROP_TEXELS = 160f; // along the long side
    private Bitmap backdrop, backdropRing;
    private android.graphics.BitmapShader backdropShader;
    private final Paint backdropPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint backdropRoundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix backdropMatrix = new Matrix();
    private final RectF backdropDst = new RectF();
    private float backdropCell;
    private boolean backdropValid, backdropRingOn;
    private int backdropCount;
    private final float[] bdGeom = new float[14], bdGeomDrawn = new float[14];
    private final int[] bdColors = new int[7], bdColorsDrawn = new int[7];
    private final float[] bdWave = new float[15], bdWaveDrawn = new float[15];

    /**
     * The fade of the open / close goes into every draw's own alpha, not through an offscreen layer (no layer to render
     * and composite, no extra GPU program); the parts overlap a little see-through for those ~150 ms.
     */
    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    /** The screen's corner radius while the open / close moves it (the backdrop is drawn rounded); 0 at rest. */
    private float revealCorner = -1f; // the screen's corner radius while it moves; -1: not asked yet

    /**
     * The screen's corner radius from the window's insets, as ActionBarLayout clips with: asked while drawing, since
     * the first setReveal comes from createView, before the view is attached and has any insets.
     */
    private float revealCorner() {
        if (revealCorner < 0f && isAttachedToWindow()) {
            revealCorner = QrRender.screenCornerRadius(this); // 0 on a square screen: drawn without rounding
        }
        return revealCorner;
    }

    private void drawBackdrop(Canvas c, int w, int h, boolean waving) {
        if (scene == null || w <= 0 || h <= 0) {
            c.drawColor(Color.BLACK);
            return;
        }
        final float cell = Math.max(w, h) / BACKDROP_TEXELS;
        final int nx = (int) Math.ceil(w / cell) + 1, ny = (int) Math.ceil(h / cell) + 1;
        if (backdrop == null || backdrop.getWidth() != nx || backdrop.getHeight() != ny) {
            backdrop = Bitmap.createBitmap(nx, ny, Bitmap.Config.ARGB_8888);
            backdropRing = Bitmap.createBitmap(nx, ny, Bitmap.Config.ARGB_8888);
            backdropShader = new android.graphics.BitmapShader(backdrop, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            backdropRoundPaint.setShader(backdropShader);
            backdropValid = false;
        }
        backdropCell = cell;
        putBackdropScene(0, waving ? oldScene : scene, w, h);
        backdropRingOn = false;
        if (waving) {
            putBackdropScene(1, scene, w, h);
            putBackdropWave(w, h);
        }
        final int count = waving ? 2 : 1;
        if (!backdropValid || count != backdropCount || !java.util.Arrays.equals(bdGeom, bdGeomDrawn)
            || !java.util.Arrays.equals(bdColors, bdColorsDrawn) || waving && !java.util.Arrays.equals(bdWave, bdWaveDrawn)) {
            Utilities.generateQrBackdrop(backdrop, backdropRingOn ? backdropRing : null, cell, count, bdGeom, bdColors, bdWave);
            System.arraycopy(bdGeom, 0, bdGeomDrawn, 0, bdGeom.length);
            System.arraycopy(bdColors, 0, bdColorsDrawn, 0, bdColors.length);
            System.arraycopy(bdWave, 0, bdWaveDrawn, 0, bdWave.length);
            backdropCount = count;
            backdropValid = true;
        }
        if (reveal < 1f && revealCorner() > 0f) {
            // the open / close: rounded screen corners from the backdrop itself (no outline clip over a layer)
            backdropMatrix.setScale(cell, cell);
            backdropMatrix.postTranslate(-cell / 2f, -cell / 2f);
            backdropShader.setLocalMatrix(backdropMatrix);
            c.drawRoundRect(0, 0, w, h, revealCorner, revealCorner, backdropRoundPaint);
        } else {
            drawBackdropBitmap(c, backdrop);
        }
    }

    /** Texel (i, j) lands on the view point (i·cell, j·cell). */
    private void drawBackdropBitmap(Canvas c, Bitmap b) {
        final float cell = backdropCell;
        backdropDst.set(-cell / 2f, -cell / 2f, (b.getWidth() - 0.5f) * cell, (b.getHeight() - 0.5f) * cell);
        c.drawBitmap(b, null, backdropDst, backdropPaint);
    }

    /** 7 floats per scene: type (0 linear, 1 radial), origin / centre, direction, 1 / length or radius, middle stop. */
    private void putBackdropScene(int k, Scene s, int w, int h) {
        final int g = k * 7;
        if (s.type == SCENE_COLOR && s.index == TUNED_COLOR) {
            // a plain linear gradient ~18° off vertical: light in the top-left corner, darkest in the bottom-right
            // corner at the screen edge; no hotspot, no drift
            bdGeom[g] = 0f;
            bdGeom[g + 1] = 0f;
            bdGeom[g + 2] = 0f;
            bdGeom[g + 3] = SIN18;
            bdGeom[g + 4] = COS18;
            bdGeom[g + 5] = 1f / (w * SIN18 + h * COS18);
            bdGeom[g + 6] = 0.5f;
        } else {
            // gifts: their centre → edge radial, centred on the QR (no off-centre dark spot)
            bdGeom[g] = 1f;
            bdGeom[g + 1] = w / 2f;
            bdGeom[g + 2] = cardTop() + cardH() / 2f;
            bdGeom[g + 3] = 0f;
            bdGeom[g + 4] = 0f;
            bdGeom[g + 5] = 1f / ((float) Math.hypot(w, h) * 0.62f);
            bdGeom[g + 6] = 0.55f;
        }
        bdColors[k * 3] = s.center;
        bdColors[k * 3 + 1] = s.mid;
        bdColors[k * 3 + 2] = s.edge;
    }

    /** The new scene's circle, and its glowing ring: a bright rim with a soft refraction band inside. */
    private void putBackdropWave(int w, int h) {
        final float e = waveEase(wave);
        final float maxR = (float) Math.hypot(Math.max(waveX, w - waveX), Math.max(waveY, h - waveY)) + dp(60);
        final float r = maxR * e;
        bdWave[0] = waveX;
        bdWave[1] = waveY;
        bdWave[2] = r;
        backdropRingOn = r > dp(4);
        if (!backdropRingOn) return;
        // a soft glow in a light tint of the new colour (~38% of the old white rim), wider falloff
        final float bandW = lerp(dp(90), dp(56), e);
        final float a = Utilities.clamp01((1f - wave) * 1.6f) * 0.38f;
        final float inner = Math.max(0, r - bandW) / r, rr = r + dp(8);
        bdWave[3] = inner * r;
        bdWave[4] = lerp(inner, 1f, 0.45f) * r;
        bdWave[5] = lerp(inner, 1f, 0.82f) * r;
        bdWave[6] = r - 0.004f * rr;
        bdWave[7] = r + dp(3);
        bdWave[8] = rr;
        bdWave[9] = 0f;
        bdWave[10] = 0x33 / 255f * a;
        bdWave[11] = 0x99 / 255f * a;
        bdWave[12] = a;
        bdWave[13] = 0x66 / 255f * a;
        bdWave[14] = 0f;
        bdColors[6] = QrCodeLayer.mixOklab(scene.center, Color.WHITE, 0.55f);
    }

    private final double[] lab1 = new double[3], lab2 = new double[3], labOut = new double[3];

    private int blendLab(int a, int b, float t) {
        ColorUtils.colorToLAB(a, lab1);
        ColorUtils.colorToLAB(b, lab2);
        ColorUtils.blendLAB(lab1, lab2, t, labOut);
        return ColorUtils.LABToColor(labOut[0], labOut[1], labOut[2]);
    }

    private static float waveEase(float t) {
        // quick start, long glide: the ring is still visible when it crosses the far corners
        return 1f - (float) Math.pow(1f - t, 2.4f);
    }

    private static int Theme_multAlpha(int color, float a) {
        return ColorUtils.setAlphaComponent(color, (int) (Color.alpha(color) * Utilities.clamp01(a)));
    }

    // --- pattern field

    private final java.util.ArrayList<android.graphics.Rect> exclusion = new java.util.ArrayList<>();

    /** The system bars over this view: none where the screen is not under them (a tablet shows it as a floating layer). */
    private int insetTop = AndroidUtilities.statusBarHeight, insetBottom = AndroidUtilities.navigationBarHeight;

    /** The status bar's part over {@code v}: its layout top in the window (translations aside) against the bar. */
    static int insetTop(View v) {
        return Math.max(0, AndroidUtilities.statusBarHeight - layoutTopInWindow(v));
    }

    /** The navigation bar's part over {@code v}, likewise from its layout bottom. */
    static int insetBottom(View v) {
        final int below = v.getRootView().getHeight() - (layoutTopInWindow(v) + v.getHeight());
        return Math.max(0, AndroidUtilities.navigationBarHeight - below);
    }

    private static int layoutTopInWindow(View v) {
        int y = 0;
        while (true) {
            y += v.getTop();
            if (!(v.getParent() instanceof View)) return y;
            v = (View) v.getParent();
            y -= v.getScrollY();
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        // the bars' insets never put a button nearer to the top / bottom edge than to its side edge (a screen away from
        // the system bars, e.g. a tablet's floating layer, has none): the close button sits 9dp from the side, its top
        // at inset + bar / 2 − 20dp; the share button 22dp from the side, its bottom at inset + 20dp
        insetTop = Math.max(insetTop(this), dp(9) + dp(20) - ActionBar.getCurrentActionBarHeight() / 2);
        insetBottom = Math.max(insetBottom(this), dp(22) - dp(20));
        if (Build.VERSION.SDK_INT >= 29 && changed) {
            // the system back gesture must not steal backdrop drags: exclude 200dp (the system cap) on both edges,
            // centred on the QR card where the backdrop is usually dragged
            final int w = r - l, cy = (int) (cardTop() + cardH() / 2f), half = dp(100), edge = dp(24);
            exclusion.clear();
            exclusion.add(new android.graphics.Rect(0, cy - half, edge, cy + half));
            exclusion.add(new android.graphics.Rect(w - edge, cy - half, w, cy + half));
            setSystemGestureExclusionRects(exclusion);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        prewarmBgLight(w, h);
        super.onSizeChanged(w, h, oldw, oldh);

    }

    private float fieldCos, fieldSin, fieldT, shineC, shineW;
    private boolean shining;

    // ---- the light over the background: the same curved / cylinder field as the symbols, but ×2.8 wider, a much
    // lower peak and a slow 2-octave breakup along the band, evaluated on a coarse 12dp grid into a tiny bitmap that
    // is drawn upscaled with filtering (soft by construction, no straight edge; no allocation per frame)
    private Bitmap bgLight;
    private int[] bgLightPx;
    private final Paint bgLightPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF bgLightDst = new RectF();

    private static float valueNoise(float x) {
        final int i = (int) Math.floor(x);
        final float f = x - i, u = f * f * (3f - 2f * f);
        final float a = fract(Math.sin(i * 127.1) * 43758.5453), b = fract(Math.sin((i + 1) * 127.1) * 43758.5453);
        return a + (b - a) * u; // 0…1
    }

    /** Builds the band's outline mask and light tint for a symbol bitmap right when it is ready. */
    private void prewarmSymbolPasses(Pattern p, Bitmap b) {
        if (b == null) return;
        if (RimTune.v[RimTune.OUTLINE] > 0.5f) ensureEdge(b);
        litTint(p);
    }

    /** The background light's bitmap at the view's size (created at layout, never during a sweep). */
    private void prewarmBgLight(int w, int h) {
        if (w <= 0 || h <= 0) return;
        final float cell = dp(12);
        final int nx = (int) Math.ceil(w / cell) + 1, ny = (int) Math.ceil(h / cell) + 1;
        if (bgLight == null || bgLight.getWidth() != nx || bgLight.getHeight() != ny) {
            bgLight = Bitmap.createBitmap(nx, ny, Bitmap.Config.ARGB_8888);
            bgLightPx = new int[nx * ny];
        }
    }

    private void drawBackgroundLight(Canvas c, int w, int h) {
        final float str = RimTune.v[RimTune.BG_STR] / 100f;
        if (str <= 0f || w <= 0 || h <= 0) return;
        final float cell = dp(12);
        final int nx = (int) Math.ceil(w / cell) + 1, ny = (int) Math.ceil(h / cell) + 1;
        if (bgLight == null || bgLight.getWidth() != nx || bgLight.getHeight() != ny) {
            bgLight = Bitmap.createBitmap(nx, ny, Bitmap.Config.ARGB_8888);
            bgLightPx = new int[nx * ny];
        }
        final float k = Math.max(1f, RimTune.v[RimTune.BG_W]);
        final float nz = RimTune.v[RimTune.BG_NOISE] / 100f;
        final float peak = 0.0275f * str; // the old surface lift peaked at ~2.7% white
        final float acrossN = dp(130), t = fieldT * 0.12f;
        boolean any = false;
        for (int iy = 0; iy < ny; iy++) {
            for (int ix = 0; ix < nx; ix++) {
                final float x = ix * cell, y = iy * cell;
                shine(x, y, w, h, 0f);
                final float d = shineD / k;
                float v = 0f;
                if (d < 3.5f && d > -3.5f) {
                    v = shineDEnv * (float) Math.exp(-0.5f * d * d);
                    if (nz > 0f) {
                        final float across = ((x - w / 2f) * -sweepDY + (y - h / 2f) * sweepDX) / acrossN;
                        final float n = 0.65f * valueNoise(across + t) + 0.35f * valueNoise(across * 2.3f - t * 1.7f + 11.3f);
                        v *= 1f + nz * (n * 2f - 1f);
                    }
                }
                final int a = Math.max(0, Math.min(255, Math.round(255 * peak * v)));
                if (a > 0) any = true;
                bgLightPx[iy * nx + ix] = (a << 24) | 0xFFFFFF; // white at alpha a (setPixels takes unpremultiplied)
            }
        }
        if (!any) return;
        bgLight.setPixels(bgLightPx, 0, nx, 0, 0, nx, ny);
        bgLightDst.set(-cell / 2f, -cell / 2f, nx * cell - cell / 2f, ny * cell - cell / 2f);
        c.drawBitmap(bgLight, null, bgLightDst, bgLightPaint);
    }

    private boolean firstSweepSet;
    private float shineEnv, shineSpeed, lightSweepStart, sweepPeriod = 5f, sweepDX = -0.7071f, sweepDY = 0.7071f, shineK = 1f;
    private float slotX, slotY;

    /**
     * Places a field point: offset (wander + finger, converted into the turning field), wrap inside the torus,
     * turn around the screen centre, bob, then the edge squeeze — near an edge the distance to it is compressed
     * exponentially, so symbols shrink towards ~1/3 at the edge and never cross it (like the Apple Watch grid).
     */
    private boolean place(float px, float py, float size, int w, int h, int pop, int r, int col, float mass) {
        return placeInner(px, py, size, w, h, pop, r, col, mass);
    }

    /**
     * Places a lattice node at screen point (px, py): cull, the scene wave's push (per-node spring), radial shaping,
     * the iOS EdgeSqueeze (band 90dp: d' = band·e^(d/band − 1), scale e^(d/band − 1)), its alpha, the 12dp edge fade.
     */
    private boolean placeInner(float sx, float sy, float size, int w, int h, int pop, int r, int col, float mass) {
        final float band = dp(SQ_BAND), m = band * 1.3f;
        if (sx < -m || sx > w + m || sy < -m || sy > h + m) return false;
        final long key = nodeKey(pop, r, col);
        int slot = nodes.find(key);
        if (wave < 1f && oldScene != null && (slot < 0 || nodes.hit[slot] != waveId)) {
            final float maxR = (float) Math.hypot(Math.max(waveX, w - waveX), Math.max(waveY, h - waveY)) + dp(60);
            final float ddx = sx - waveX, ddy = sy - waveY;
            final float d = (float) Math.sqrt(ddx * ddx + ddy * ddy);
            if (d <= maxR * waveEase(wave)) {
                if (slot < 0) slot = nodes.obtain(key);
                if (slot >= 0) {
                    nodes.hit[slot] = waveId;
                    // outward push from the QR: k 220 / ζ 0.62 peaks at ~0.033 s·v → ~12dp for a small emoji
                    final float inv = 1f / mass;
                    final float dx = d > 1f ? ddx / d : 0f, dy = d > 1f ? ddy / d : -1f;
                    nodes.vx[slot] += dx * dp(360) * inv;
                    nodes.vy[slot] += dy * dp(360) * inv;
                    nodes.vr[slot] += (dx >= 0 ? 1f : -1f) * 180f * inv; // ~±6° / mass
                    nodes.vs[slot] += 1.8f;                               // ~+6% pulse
                }
            }
        }
        if (slot >= 0) {
            sx += nodes.x[slot];
            sy += nodes.y[slot];
            slotRot = nodes.r[slot];
            slotPulse = nodes.s[slot];
        } else {
            slotRot = 0f;
            slotPulse = 0f;
        }
        // radial shaping from the QR card centre: r = 1 where the ray from the card centre meets the screen edge
        final float rcx = w / 2f, rcy = cardTop() + cardH() / 2f, rdx = sx - rcx, rdy = sy - rcy;
        float rr = 0f;
        if (rdx > 0.5f) rr = Math.max(rr, rdx / Math.max(1f, w - rcx));
        else if (rdx < -0.5f) rr = Math.max(rr, -rdx / Math.max(1f, rcx));
        if (rdy > 0.5f) rr = Math.max(rr, rdy / Math.max(1f, h - rcy));
        else if (rdy < -0.5f) rr = Math.max(rr, -rdy / Math.max(1f, rcy));
        rr = Math.min(1f, rr);
        final float radialScale = 1f - 0.22f * smooth(0.55f, 1f, rr);   // 1 → 0.78 in the outer part only
        final float radialAlpha = 1f - 0.2f * smooth(0.8f, 1f, rr);     // 1 → 0.8 only near the edges
        float k = 1f;
        if (sx < band && sx <= w - sx) {
            final float g = (float) Math.exp(sx / band - 1f);
            sx = band * g;
            k = g;
        } else if (w - sx < band) {
            final float g = (float) Math.exp((w - sx) / band - 1f);
            sx = w - band * g;
            k = g;
        }
        if (sy < band && sy <= h - sy) {
            final float g = (float) Math.exp(sy / band - 1f);
            sy = band * g;
            k = Math.min(k, g);
        } else if (h - sy < band) {
            final float g = (float) Math.exp((h - sy) / band - 1f);
            sy = h - band * g;
            k = Math.min(k, g);
        }
        final float cylA = smooth(0.08f, 0.25f, k); // EdgeSqueeze.alpha
        slotAlpha = radialAlpha * cylA;
        if (slotAlpha <= 0.004f) return false;
        final float sized = size * radialScale;
        final float base = sized < dp(10) ? dp(10) / Math.max(1f, size) : radialScale;
        // never a speck: the projected size stays ≥ min(base size, 9dp); positions still compress into the band
        // (the gift symbols keep no 9dp floor: with the floor their compressed positions came closer than their
        // sizes allowed — the remaining overlaps were all in the edge squeeze band)
        slotScaleK = pop == 1 ? k * base : Math.max(k * base, Math.min(size, dp(9)) / Math.max(1f, size));
        // plus a last 12dp fade at the screen edge (from the item's projected bounds)
        final float half = size * slotScaleK / 2f;
        final float ed = Math.min(Math.min(sx - half, w - sx - half), Math.min(sy - half, h - sy - half));
        final float ue = Utilities.clamp01(ed / dp(12));
        slotAlpha *= ue * ue * (3f - 2f * ue);
        if (slotAlpha <= 0.004f) return false;
        slotX = sx;
        slotY = sy;
        slotLight = shine(sx, sy, w, h); // at the final drawn position, no lag
        slotCore = shineCore;
        slotD = shineD;
        slotDEnv = shineDEnv;
        return true;
    }

    private static long nodeKey(int pop, int r, int c) {
        return ((long) pop << 60) ^ (((long) r & 0x3FFFFFFFL) << 30) ^ ((long) c & 0x3FFFFFFFL);
    }

    /** Per-node spring state for the wave push (open addressing, preallocated; cleared once everything settles). */
    static final class NodeSprings {
        static final int CAP = 4096;
        final long[] keys = new long[CAP];
        final boolean[] used = new boolean[CAP];
        final float[] x = new float[CAP], y = new float[CAP], vx = new float[CAP], vy = new float[CAP], r = new float[CAP], vr = new float[CAP], s = new float[CAP], vs = new float[CAP];
        final int[] hit = new int[CAP];
        final int[] list = new int[CAP];
        int n;

        int find(long key) {
            if (n == 0) return -1;
            int i = (int) (mix(key) & (CAP - 1));
            while (used[i]) {
                if (keys[i] == key) return i;
                i = (i + 1) & (CAP - 1);
            }
            return -1;
        }

        int obtain(long key) {
            if (n >= CAP * 3 / 4) return -1;
            int i = (int) (mix(key) & (CAP - 1));
            while (used[i]) {
                if (keys[i] == key) return i;
                i = (i + 1) & (CAP - 1);
            }
            used[i] = true;
            keys[i] = key;
            hit[i] = -1;
            x[i] = y[i] = vx[i] = vy[i] = r[i] = vr[i] = s[i] = vs[i] = 0;
            list[n++] = i;
            return i;
        }

        private static long mix(long k) {
            k ^= k >>> 33;
            k *= 0xff51afd7ed558ccdL;
            k ^= k >>> 33;
            return k;
        }

        /** k 220, ζ 0.62; returns true while anything moves. */
        boolean step(float dt) {
            final float k = 220f, c = 2f * 0.62f * (float) Math.sqrt(220f);
            boolean moving = false;
            for (int q = 0; q < n; q++) {
                final int i = list[q];
                if (x[i] == 0 && y[i] == 0 && vx[i] == 0 && vy[i] == 0 && r[i] == 0 && vr[i] == 0 && s[i] == 0 && vs[i] == 0)
                    continue;
                vx[i] += (-k * x[i] - c * vx[i]) * dt;
                x[i] += vx[i] * dt;
                vy[i] += (-k * y[i] - c * vy[i]) * dt;
                y[i] += vy[i] * dt;
                vr[i] += (-k * r[i] - c * vr[i]) * dt;
                r[i] += vr[i] * dt;
                vs[i] += (-k * s[i] - c * vs[i]) * dt;
                s[i] += vs[i] * dt;
                if (Math.abs(x[i]) + Math.abs(y[i]) + Math.abs(vx[i]) + Math.abs(vy[i]) < 0.05f && Math.abs(r[i]) + Math.abs(vr[i]) < 0.02f && Math.abs(s[i]) + Math.abs(vs[i]) < 0.0005f) {
                    x[i] = y[i] = vx[i] = vy[i] = r[i] = vr[i] = s[i] = vs[i] = 0;
                } else {
                    moving = true;
                }
            }
            return moving;
        }

        void clear() {
            for (int q = 0; q < n; q++) used[list[q]] = false;
            n = 0;
        }
    }

    private final NodeSprings nodes = new NodeSprings();

    /** Integrates the wandering drift and advances the glide and the spring (once per frame). */
    private float motionT, idleK = 1f, idleDt, lastWallT = -1f, idleResumeAt;

    private void stepMotion(long now) {
        final float t = fieldT;
        if (lastFieldT >= 0f) {
            final float dt = idleDt; // idle drift runs on the idle clock (paused while the finger holds the field)
            if (dt > 0f) {
                final float tm = motionT - dt / 2f;
                final float a = DRIFT_ANGLE + 1.2f * (float) Math.sin(0.025f * tm) + 0.6f * (float) Math.sin(0.016f * tm + 1f);
                wanderX += (float) Math.cos(a) * DRIFT_SPEED * dp(1) * dt;
                wanderY += (float) Math.sin(a) * DRIFT_SPEED * dp(1) * dt;
            }
        }
        final float sdt = lastFieldT >= 0f ? Math.min(0.033f, Math.max(0f, t - lastFieldT)) : 0f;
        shineK = 1f - (float) Math.exp(-sdt / 0.09f);
        lastFieldT = t;
        if (sdt > 0f && nodes.n > 0) {
            // sub-step for a stable spring at any frame rate; free the table once the wave is over and all settled
            final float h2 = sdt / 2f;
            boolean moving = false;
            for (int q = 0; q < 2; q++) moving |= nodes.step(h2);
            if (!moving && (wave >= 1f || oldScene == null)) nodes.clear();
        }
        if (gliding) {
            final float gt = (now - glideStart) / 1000f;
            final float f = GLIDE_TAU * (1f - (float) Math.exp(-gt / GLIDE_TAU));
            panX = glideFromX + glideVX * f;
            panY = glideFromY + glideVY * f;
            if (gt > GLIDE_TAU * 8f) gliding = false;
        }
        if (springing) {
            final float st = (now - springStart) / 1000f;
            final float k = (float) (Math.exp(-st / 0.22f) * Math.cos(2 * Math.PI * st / 0.85f));
            stretchX = springX * k;
            stretchY = springY * k;
            if (st > 1.6f) {
                springing = false;
                stretchX = stretchY = 0f;
            }
        }
    }

    private float rubber(float d, float length) {
        return 0.32f * length * (float) Math.tanh(d / Math.max(1f, length) * 2.5f);
    }

    private void drawPattern(Canvas c, int w, int h) {
        final long now = AnimationUtils.currentAnimationTimeMillis();
        fieldT = (now - clockStart) / 1000f;
        // idle-motion clock: the field stops turning/drifting under the finger and picks up again after release
        final float dtw = lastWallT < 0f ? 0f : Math.max(0f, Math.min(0.05f, fieldT - lastWallT));
        lastWallT = fieldT;
        final boolean held = panning || gliding || springing;
        if (held) idleResumeAt = fieldT + 0.25f;
        final float idleTarget = held || fieldT < idleResumeAt ? 0f : 1f;
        final float tau = idleTarget < idleK ? 0.07f : 0.45f;
        if (dtw > 0f) idleK += (idleTarget - idleK) * (1f - (float) Math.exp(-dtw / tau));
        idleDt = dtw * idleK;
        motionT += idleDt;
        final float rad = (float) Math.toRadians((PAT_DEG_PER_S * motionT) % 360f);
        fieldCos = (float) Math.cos(rad);
        fieldSin = (float) Math.sin(rad);
        stepMotion(now);
        // shimmer: a soft light (Gaussian across the band, σ ≈ 48dp) sweeping from the upper right to the lower left;
        // sine-eased over 2.2 s, every ~5 s, with a little variation of angle and period per sweep
        final float randK = RimTune.v[RimTune.SH_RAND] / 100f;
        while (fieldT >= lightSweepStart + sweepPeriod) {
            lightSweepStart += sweepPeriod;
            sweepPeriod = Math.max(RimTune.v[RimTune.SH_DUR] + 0.2f, RimTune.v[RimTune.SH_PER] * (emojiOnlyShine() ? 1.5f : 1f) + random.nextFloat() * 0.8f * randK); // emoji-only: 1.5× rarer
            final double a = Math.toRadians(RimTune.v[RimTune.SH_ANG] + (random.nextFloat() - 0.5f) * 12f * randK); // ±6° per sweep
            sweepDX = (float) Math.cos(a);
            sweepDY = (float) Math.sin(a);
        }
        if (!firstSweepSet) {
            // the first sweep waits until ~1.5 s after the QR intro (it used to start right at open, over the card)
            firstSweepSet = true;
            lightSweepStart = fieldT + RimTune.v[RimTune.INTRO_MS] / 1000f + 1.5f;
            sweepPeriod = 1000f; // the loop below rolls the period only once the first sweep has passed
        }
        if (fieldT >= lightSweepStart + RimTune.v[RimTune.SH_DUR] && sweepPeriod > 999f)
            sweepPeriod = RimTune.v[RimTune.SH_PER];
        final float sp = (fieldT - lightSweepStart) / RimTune.v[RimTune.SH_DUR];
        shining = reveal >= 1f && sp >= 0f && sp < 1f; // no lights while the screen opens / closes
        shineW = dpf2(RimTune.v[RimTune.SH_W]);
        if (shining) {
            final float e = 0.5f - 0.5f * (float) Math.cos(Math.PI * sp);
            final float ext = (Math.abs(sweepDX) * w + Math.abs(sweepDY) * h) / 2f + shineW * 3f;
            shineC = lerp(-ext, ext, e);
            shineEnv = 1f;
        }
        stepTiltShine(w, h);
        if (shining && pattern.type != PAT_NONE) {
            drawBackgroundLight(c, w, h);
        }
        final float st = (now - patternStart) / 1000f;
        final boolean swapping = oldPattern != null && st < (pattern.waveSwap ? WAVE_MS / 1000f : SWAP_SPREAD) + GROW_AT + GROW;
        if (!swapping && oldPattern != null) {
            releasePattern(oldPattern);
            oldPattern = null;
        }
        if (swapping && oldPattern.type == PAT_EMOJI && pattern.type == PAT_EMOJI) {
            drawEmojiSwap(c, w, h, oldPattern, pattern, st);
            return;
        }
        if (swapping && pattern.waveSwap) {
            // a recolour wave (a gift picked): the symbols change exactly at the backdrop's wave front — the old layer
            // outside the circle, the new one inside, both at full scale (no staggered shrink / grow that left
            // old-colour symbols on the new background); the old layer goes as soon as the wave has passed
            if (wave >= 1f || oldScene == null) {
                releasePattern(oldPattern);
                oldPattern = null;
                drawField(c, w, h, pattern, false, true, 100f);
                return;
            }
            final float maxR = (float) Math.hypot(Math.max(waveX, w - waveX), Math.max(waveY, h - waveY)) + dp(60);
            final float r = maxR * waveEase(wave);
            wavePath.rewind();
            wavePath.addCircle(waveX, waveY, r, Path.Direction.CW);
            c.save();
            QrRender.clipOut(c, wavePath);
            drawField(c, w, h, oldPattern, true, pattern.type == PAT_NONE, -1f); // before its exit: full scale
            c.restore();
            c.save();
            c.clipPath(wavePath);
            drawField(c, w, h, pattern, false, oldPattern.type == PAT_NONE, 100f); // grown: full scale
            c.restore();
            return;
        }
        if (swapping) drawField(c, w, h, oldPattern, true, pattern.type == PAT_NONE, st);
        drawField(c, w, h, pattern, false, !swapping || oldPattern.type == PAT_NONE, swapping ? st : 100f);
    }

    private final Path wavePath = new Path();

    /**
     * The symbol (a white silhouette) in the pattern's ink, coloured on the CPU when the symbol or the ink changes:
     * drawn as a plain bitmap, no colour filter per draw. Two slots: the symbol and the stand-in it crossfades from.
     */
    private Bitmap inked(Pattern p, Bitmap src, boolean fallback) {
        if (fallback) {
            if (p.inkFallback == null || p.inkFallbackFor != src || p.inkFallbackColor != p.ink) {
                p.inkFallback = inkCopy(src, p.ink, p.inkFallback);
                p.inkFallbackFor = src;
                p.inkFallbackColor = p.ink;
            }
            return p.inkFallback;
        }
        if (p.inkBitmap == null || p.inkBitmapFor != src || p.inkBitmapColor != p.ink) {
            p.inkBitmap = inkCopy(src, p.ink, p.inkBitmap);
            p.inkBitmapFor = src;
            p.inkBitmapColor = p.ink;
        }
        return p.inkBitmap;
    }

    /** src's alpha × the colour (as SRC_IN would give); reuses the previous copy when it has the size. */
    private static Bitmap inkCopy(Bitmap src, int color, Bitmap reuse) {
        final int w = src.getWidth(), h = src.getHeight();
        final int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        final int rgb = color & 0xFFFFFF, ca = color >>> 24;
        for (int i = 0; i < px.length; i++) {
            px[i] = ((px[i] >>> 24) * ca / 255) << 24 | rgb;
        }
        final Bitmap out = reuse != null && reuse.isMutable() && reuse.getWidth() == w && reuse.getHeight() == h ? reuse : Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, w, 0, 0, w, h);
        out.setHasMipMap(src.hasMipMap());
        return out;
    }

    // the outline's edge mask: the symbol's alpha minus itself shifted along the light axis (the edge facing the
    // light), moved toward the light by the offset; rebuilt only when the symbol / width / offset / angle change
    private Bitmap edgeBitmap, edgeFor;
    private float edgeW = -1, edgeOff = -1, edgeAng = -1;
    private final Paint edgeOutPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    {edgeOutPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));}

    private boolean ensureEdge(Bitmap b) {
        final float ww = RimTune.v[RimTune.OUT_W], oo = RimTune.v[RimTune.OUT_OFF], an = RimTune.v[RimTune.SH_ANG];
        if (edgeBitmap != null && edgeFor == b && ww == edgeW && oo == edgeOff && an == edgeAng) return true;
        if (b == null || b.isRecycled()) return false;
        edgeFor = b;
        edgeW = ww;
        edgeOff = oo;
        edgeAng = an;
        if (edgeBitmap == null || edgeBitmap.getWidth() != b.getWidth() || edgeBitmap.getHeight() != b.getHeight()) {
            edgeBitmap = Bitmap.createBitmap(b.getWidth(), b.getHeight(), Bitmap.Config.ARGB_8888);
        }
        edgeBitmap.eraseColor(0);
        final Canvas ec = new Canvas(edgeBitmap);
        // toward the light = against the sweep direction
        final double a = Math.toRadians(an);
        final float lx = (float) -Math.cos(a), ly = (float) -Math.sin(a);
        final float sw = dpf2(ww), so = dpf2(oo);
        ec.drawBitmap(b, lx * so, ly * so, null);
        ec.drawBitmap(b, lx * so - lx * sw, ly * so - ly * sw, edgeOutPaint); // minus itself, shifted away from the light
        return true;
    }

    // ---- symbol nodes per frame (preallocated) + the blue-noise tile
    private static final int SYM_NODE_CAP = 1024;
    private final int[] symR = new int[SYM_NODE_CAP], symC = new int[SYM_NODE_CAP];
    private final float[] symBX = new float[SYM_NODE_CAP], symBY = new float[SYM_NODE_CAP], symFitA = new float[SYM_NODE_CAP], symH5 = new float[SYM_NODE_CAP];
    private int symN;
    private static final float TILE_DP = 300f;
    private float[] tileX, tileY, tileH5;
    private int tileN, tileFor = -1;

    /**
     * Mitchell's best-candidate on a torus (period TILE_DP): deterministic per pattern (seed), each point's size tier
     * random but drawn before placing, so the spacing is size-aware — every pair keeps the gap rule at maximum drawn
     * size incl. both orbits (no per-frame sampling; the tile repeats over the plane). Count tuned to ≈72 on screen.
     */
    private void ensureTile(int seed) {
        if (tileFor == seed && tileX != null) return;
        tileFor = seed;
        final java.util.Random rnd = new java.util.Random(0x51ED + seed * 7919L);
        final float T = dp(TILE_DP);
        // target: ≈72 symbols over the screen's free area ≈ (360 × 780 dp) → per tile (models take their share later)
        final int target = Math.round(72f * (TILE_DP * TILE_DP) / (360f * 780f) * 1.45f);
        tileX = new float[target];
        tileY = new float[target];
        tileH5 = new float[target];
        tileN = 0;
        for (int n = 0; n < target; n++) {
            final float h5 = rnd.nextFloat();
            final float rA = dp(21) * SYM_SIZE_K * (0.55f + 0.75f * h5 * h5) / 2f * SYM_MAX_K + dp(7);
            float bx = 0, by = 0, best = -Float.MAX_VALUE;
            final int k = 12 + n; // more candidates as the tile fills (classic best-candidate)
            for (int c = 0; c < k; c++) {
                final float x = rnd.nextFloat() * T, y = rnd.nextFloat() * T;
                float sc = Float.MAX_VALUE;
                for (int i = 0; i < tileN; i++) {
                    float ddx = Math.abs(x - tileX[i]), ddy = Math.abs(y - tileY[i]);
                    if (ddx > T / 2) ddx = T - ddx;
                    if (ddy > T / 2) ddy = T - ddy;
                    final float h = tileH5[i];
                    final float rB = dp(21) * SYM_SIZE_K * (0.55f + 0.75f * h * h) / 2f * SYM_MAX_K + dp(7);
                    // big next to big costs extra, so the tiers spread
                    final float extra = h5 > 0.7f && h > 0.7f ? dp(10) : 0f;
                    sc = Math.min(sc, (float) Math.hypot(ddx, ddy) - rA - rB - extra);
                }
                if (sc > best) {
                    best = sc;
                    bx = x;
                    by = y;
                }
            }
            if (tileN > 0 && best < dp(SYM_GAP)) break; // no room left with the gap: stop
            tileX[tileN] = bx;
            tileY[tileN] = by;
            tileH5[tileN] = h5;
            tileN++;
        }
    }

    private void collectSymbolNodes(int seed, float ox, float oy, int w, int h, float margin, float dx, float dy) {
        symN = 0;
        ensureTile(seed);
        final float T = dp(TILE_DP);
        final int ty0 = (int) Math.floor((-margin - oy) / T) - 1, ty1 = (int) Math.ceil((h + margin - oy) / T);
        final int tx0 = (int) Math.floor((-margin - ox) / T) - 1, tx1 = (int) Math.ceil((w + margin - ox) / T);
        for (int ty = ty0; ty <= ty1; ty++) {
            for (int tx = tx0; tx <= tx1; tx++) {
                for (int i = 0; i < tileN && symN < SYM_NODE_CAP; i++) {
                    final float bx = tx * T + tileX[i], by = ty * T + tileY[i];
                    final float sx = bx + ox, sy = by + oy;
                    if (sx < -margin - dp(20) || sx > w + margin + dp(20) || sy < -margin - dp(20) || sy > h + margin + dp(20))
                        continue;
                    final float h5 = tileH5[i];
                    final float rA = dp(21) * SYM_SIZE_K * (0.55f + 0.75f * h5 * h5) / 2f * SYM_MAX_K;
                    if (nearSticker(bx, by, rA + dp(7))) continue;
                    symR[symN] = ty * 4096 + i;
                    symC[symN] = tx;
                    symBX[symN] = bx;
                    symBY[symN] = by;
                    symFitA[symN] = 1f;
                    symH5[symN] = h5;
                    symN++;
                }
            }
        }
    }

    private PorterDuffColorFilter metalTintF;
    private int metalTintKey = -1;

    /** The metallic core's colour: the shine colour mixed toward white by Core whiteness (cached per value). */
    private PorterDuffColorFilter metalTint(Pattern p) {
        final int key = (int) (RimTune.v[RimTune.CORE_WHITE] * 10) ^ p.shineColor * 31;
        if (metalTintF == null || metalTintKey != key) {
            metalTintKey = key;
            metalTintF = new PorterDuffColorFilter(ColorUtils.blendARGB(p.shineColor, Color.WHITE, RimTune.v[RimTune.CORE_WHITE] / 100f), PorterDuff.Mode.SRC_IN);
        }
        return metalTintF;
    }

    /** The band's in-place light: the pattern's shine colour mixed toward white (Pattern shine whiteness). */
    private PorterDuffColorFilter litTint(Pattern p) {
        final int key = (int) (RimTune.v[RimTune.SHINE_WHITE] * 10) ^ p.shineColor;
        if (p.litTint == null || p.litTintFor != key) {
            p.litTintFor = key;
            p.litTint = new PorterDuffColorFilter(ColorUtils.blendARGB(p.shineColor, Color.WHITE, RimTune.v[RimTune.SHINE_WHITE] / 100f), PorterDuff.Mode.SRC_IN);
        }
        return p.litTint;
    }

    private float shineCore, slotCore, shineD, shineDEnv, slotD, slotDEnv;

    /**
     * The wave's push at the last placed node: offset along the sweep direction = P·d·e^(−d²/2) (d: signed distance
     * to the band centre in σ — ahead nudged forward, behind settled back), P clamped so the peak displacement stays
     * under half the gap budget (neighbours never touch). Returns the offset in px (0 when not shining).
     */
    private float pushOffset(float share) {
        if (!shining || slotDEnv <= 0.001f) return 0f;
        final float d = slotD / Math.max(0.3f, RimTune.v[RimTune.PUSH_W]); // the push profile is wider than the light
        if (d > 4f || d < -4f) return 0f;
        final float pMax = dp(SYM_GAP) / 2f / 0.607f; // max of d·e^(−d²/2) is 0.607 at |d| = 1
        final float P = Math.min(dpf2(RimTune.v[RimTune.PUSH]), pMax);
        return share * P * d * (float) Math.exp(-0.5f * d * d) * slotDEnv;
    }

    // ---- push smoothing per symbol: the field gives a TARGET (offset, light for scale/tilt); each symbol follows it
    // through two cascaded first-order filters (smooth S-shaped rise, no overshoot): pickup ≈ Push pickup ms, release
    // ≈ Push release ms (slower settle). State in preallocated arrays keyed by the stable symbol id.
    private static final int PS_CAP = 2048;
    private final long[] psKey = new long[PS_CAP];
    private final float[] psO1 = new float[PS_CAP], psO2 = new float[PS_CAP], psL1 = new float[PS_CAP], psL2 = new float[PS_CAP];
    private final long[] psT = new long[PS_CAP];
    private final boolean[] psUsed = new boolean[PS_CAP];
    private float psOut, psLight;

    private void pushSmooth(int r, int col, float targetOff, float targetLight) {
        final long key = ((long) r << 32) ^ (col & 0xFFFFFFFFL);
        int i = (int) ((key * 0x9E3779B97F4A7C15L) >>> 53) & (PS_CAP - 1);
        for (int n = 0; n < PS_CAP; n++, i = (i + 1) & (PS_CAP - 1)) {
            if (!psUsed[i] || psKey[i] == key) break;
        }
        final long now = AnimationUtils.currentAnimationTimeMillis();
        if (!psUsed[i] || psKey[i] != key || now - psT[i] > 1000) {
            psUsed[i] = true;
            psKey[i] = key;
            psO1[i] = psO2[i] = targetOff;
            psL1[i] = psL2[i] = targetLight; // (re)appearing: start where the field is
        } else {
            final float dt = Math.min(64f, now - psT[i]);
            final float up = Math.abs(targetOff) > Math.abs(psO2[i]) || targetLight > psL2[i] ? RimTune.v[RimTune.PUSH_IN] : RimTune.v[RimTune.PUSH_OUT];
            final float tau = Math.max(10f, up / 3.4f / 2f); // two stages: 10–90% ≈ 3.4·τ each half
            final float k = 1f - (float) Math.exp(-dt / tau);
            psO1[i] += (targetOff - psO1[i]) * k;
            psO2[i] += (psO1[i] - psO2[i]) * k;
            psL1[i] += (targetLight - psL1[i]) * k;
            psL2[i] += (psL1[i] - psL2[i]) * k;
        }
        psT[i] = now;
        psOut = psO2[i];
        psLight = psL2[i];
    }

    private final Paint metalPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    {metalPaint.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));}

    private boolean emojiOnlyShine() {return pattern != null && pattern.type == PAT_EMOJI;}

    /** Shimmer intensity at a screen point (0..1). */
    private float shine(float x, float y, int w, int h) {
        return shine(x, y, w, h, 0f);
    }

    /** lag (s): this symbol answers the light a little late, so it seems to roll over the field. */
    private float shine(float x, float y, int w, int h, float lag) {
        if (!shining) return 0f;
        float proj = (x - w / 2f) * sweepDX + (y - h / 2f) * sweepDY;
        // the pattern lies on a cylinder whose axis runs along the band:
        // 1) the band bows: its centre line shifts along the sweep by curvature·width·u² (u: −1…1 across the band)
        final float ext = Math.max(1f, (Math.abs(sweepDX) * w + Math.abs(sweepDY) * h) / 2f);
        final float curve = RimTune.v[RimTune.SHINE_CURVE] / 100f;
        if (curve > 0f) {
            final float across = (x - w / 2f) * -sweepDY + (y - h / 2f) * sweepDX;
            final float u = across / Math.max(1f, (Math.abs(sweepDY) * w + Math.abs(sweepDX) * h) / 2f);
            proj -= curve * w * u * u;
        }
        final float dFlat = (proj - shineC) / shineW;
        float d = dFlat, amp = 1f;
        final float cyl = RimTune.v[RimTune.SHINE_CYL] / 100f;
        if (cyl > 0f) {
            // 2) travel: the band's centre moves linearly in angle θ (screen = sin θ): fast through the middle, slow and
            // narrower (σ·cos θ) toward the edges, a little dimmer there (0.7 + 0.3·cos θ)
            final float halfPi = (float) (Math.PI / 2);
            final float tp = (float) Math.asin(Math.max(-1f, Math.min(1f, proj / ext)));
            final float tc = Math.max(-halfPi * 1.4f, Math.min(halfPi * 1.4f, shineC / ext * halfPi));
            final float dCyl = (tp - tc) / (shineW / ext * halfPi);
            d = dFlat + (dCyl - dFlat) * cyl;
            amp = 1f - cyl * RimTune.v[RimTune.EDGE_DIM] / 100f * (1f - (float) Math.cos(Math.max(-halfPi, Math.min(halfPi, tp))));
        }
        shineD = d;
        shineDEnv = shineEnv * amp;
        final float sh2 = RimTune.v[RimTune.SH2_STR] / 100f;
        if ((d > 4f || d < -4f) && sh2 <= 0f) {
            shineCore = 0f;
            return 0f;
        }
        // metallic: a narrow bright core (σ·core width) on top of the soft band — read by the lit passes as a flash
        final float ex = RimTune.v[RimTune.SH_EXP];
        final float dc = d / RimTune.v[RimTune.CORE_W];
        // emoji-only pattern (no gift): the light is softer, 70% (band, core and the glass glint that follows it)
        amp *= emojiOnlyShine() ? 0.7f : 1f;
        shineCore = dc > 4f || dc < -4f ? 0f : shineEnv * amp * (float) Math.exp(-0.5f * Math.pow(Math.abs(dc), ex));
        float v = RimTune.v[RimTune.SH_SOFT] / 100f * (d > 4f || d < -4f ? 0f : (float) Math.exp(-0.5f * Math.pow(Math.abs(d), ex)));
        if (sh2 > 0f) {
            // a second, fainter reflection behind the first (double reflection on metal)
            final float d2 = d - RimTune.v[RimTune.SH2_OFF];
            if (d2 < 4f && d2 > -4f) v += sh2 * (float) Math.exp(-0.5f * Math.pow(Math.abs(d2), ex));
        }
        return shineEnv * amp * v;
    }

    /** When the scene wave reaches a screen point (s since the wave start). */
    private float waveArrival(float x, float y, int w, int h) {
        final float maxR = (float) Math.hypot(Math.max(waveX, w - waveX), Math.max(waveY, h - waveY)) + dp(60);
        final float f = Utilities.clamp01((float) Math.hypot(x - waveX, y - waveY) / maxR);
        final float t = 1f - (float) Math.pow(1f - f, 1f / 2.4f); // inverse of waveEase
        return t * WAVE_MS / 1000f - 0.03f;
    }

    private static final long MODEL_APPEAR_MS = 380, MODEL_APPEAR_SPREAD_MS = 300; // each item's pop, the delays' range
    /** From the end of the open until the last item has popped in (when its frame is there by then). */
    static final long ITEMS_APPEAR_MS = MODEL_APPEAR_SPREAD_MS + MODEL_APPEAR_MS;
    /** A new order every open: the items' delays come from their hash mixed with this (drawn once per screen). */
    private final float appearSalt = (float) Math.random();

    /** An item's pop-in delay (ms) for its hash h, in a random order each time the screen opens. */
    private float appearDelay(double h) {
        final double x = Math.sin((h + appearSalt * 17.31) * 12.9898 + appearSalt * 78.233) * 43758.5453;
        return (float) (x - Math.floor(x)) * MODEL_APPEAR_SPREAD_MS;
    }

    private final Paint modelPaint = new Paint(Paint.FILTER_BITMAP_FLAG), modelOutlinePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF modelDst = new RectF();

    private static float backOut(float u) {
        final float c1 = 1.70158f, c3 = c1 + 1f;
        u -= 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    /** Old content shrinks away, new content pops in with a small overshoot; p = time since this slot's start. */
    private static float slotScale(float p, boolean old, boolean otherNone) {
        if (old) {
            if (p <= 0f) return 1f;
            return Math.max(0f, 1f - p / SHRINK);
        }
        final float q = otherNone ? p : p - GROW_AT;
        if (q <= 0f) return 0f;
        if (q >= GROW) return 1f;
        return backOut(q / GROW);
    }

    // ---- iOS lattices (Backgrounds.swift): hex-staggered grids, odd rows shifted dx/2, per-node hash, own orbit;
    // cells covering the screen + margin are walked each frame (culled before any per-item math)
    private static final float EM_DX = 86, EM_DY = 70, SY_DX = 44, SY_DY = 38, ST_DX = 170, ST_DY = 150; // dp
    private static final float SQ_BAND = 90;                                                              // EdgeSqueeze band, dp
    private static final int SPOT_CAP = 96;
    private final float[] spX = new float[SPOT_CAP], spY = new float[SPOT_CAP], spSide = new float[SPOT_CAP], spAng = new float[SPOT_CAP], spH = new float[SPOT_CAP];
    private final long[] spKey = new long[SPOT_CAP];
    private int spN;

    static double latticeHash(int r, int c) {
        long h = ((long) r) * 0x9E3779B97F4A7C15L ^ ((long) c) * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return (double) (h >>> 11) / (double) (1L << 53);
    }

    private static float fract(double x) {return (float) (x - Math.floor(x));}

    private float orbitX, orbitY;

    /** A node's own slow orbit (iOS Lattice.own): radius in [rmin, rmax] dp, period 9–20 s, either direction. */
    private void orbit(double hh, float rmin, float rmax) {
        final float radius = dp(1) * (rmin + (rmax - rmin) * fract(hh * 31.7));
        final float period = 9f + 11f * fract(hh * 37.1);
        final double a = (fract(hh * 43.3) < 0.5f ? -1.0 : 1.0) * 2 * Math.PI * motionT / period + hh * 6.283;
        orbitX = radius * (float) Math.cos(a);
        orbitY = radius * (float) Math.sin(a);
    }

    private static int wrapIdx(int v, int n) {return ((v % n) + n) % n;}

    /** seed 0..1: the item's own position in the animation (each emoji on the screen plays from its own frame). */
    private void drawEmoji(Canvas c, Pattern p, int i, float size, float rot, float breathPh, float scale, float seed) {
        final Drawable d = p.emoji[i];
        if (d == null || scale <= 0.01f) return;
        // a custom emoji: its current frame as a plain bitmap (one playback position for all its copies); nothing until
        // it has a real frame (no thumb outline)
        final Bitmap frame = d instanceof AnimatedEmojiDrawable ? emojiFrame((AnimatedEmojiDrawable) d) : null;
        final boolean ready = d instanceof AnimatedEmojiDrawable ? frame != null : !(d instanceof Emoji.EmojiDrawable) || ((Emoji.EmojiDrawable) d).isLoaded();
        // none during the open; then each copy pops in after its own random delay (as the gift models)
        final float appear = popIn(p.emojiShownAt, i, ready, seed);
        if (appear <= 0f) return;
        // the band's light is the pattern's only: the emoji are not lit up nor enlarged by it
        final float k = scale * (appear < 1f ? backOut(appear) : 1f) * slotScaleK * (1f + slotPulse) * (1f + 0.03f * (float) Math.sin(motionT * 1.3f + breathPh));
        final int hs = Math.round(size / 2f);
        if (frame != null) {
            final float side = hs * k;
            c.save();
            c.translate(slotX, slotY);
            c.rotate(rot + slotRot);
            emojiDst.set(-side, -side, side, side);
            if (QrEmojiPanel.isTextColor(d)) {
                emojiTextPaint.setAlpha((int) (255 * slotAlpha)); // a text-colour emoji: white
                c.drawBitmap(frame, null, emojiDst, emojiTextPaint);
            } else {
                emojiPaint.setAlpha((int) (255 * slotAlpha));
                c.drawBitmap(frame, null, emojiDst, emojiPaint);
            }
            c.restore();
            return;
        }
        // a standard emoji: its drawable draws its sprite bitmap
        c.save();
        c.translate(slotX, slotY);
        c.rotate(rot + slotRot);
        c.scale(k, k);
        d.setBounds(-hs, -hs, hs, hs);
        d.setAlpha((int) (255 * slotAlpha));
        if (QrEmojiPanel.isTextColor(d)) {
            QrEmojiPanel.drawEmoji(c, d);
        } else {
            d.draw(c);
        }
        d.setAlpha(255); // Emoji drawables share one static paint: leave it opaque, or the keyboard's emoji turn pale
        c.restore();
    }

    /**
     * 0..1: an item's pop-in. Nothing while the screen opens (nor before the item is ready), then it pops in after
     * its own random delay from the moment it was first shown; the shared image has everything.
     */
    private float popIn(long[] shownAt, int i, boolean ready, float seed) {
        if (sharing) return 1f;
        if (shownAt == null || i >= shownAt.length) return 0f;
        if (shownAt[i] == 1) return ready ? 1f : 0f; // shown at once (on the open's first frame, or picked later)
        if (!openDone) {
            // high-end devices: an item with its frame on the open's first frame is there from the start (no pop)
            if (immediateFrame && ready) {
                shownAt[i] = 1;
                return 1f;
            }
            return 0f;
        }
        if (!ready) return 0f;
        final long now = android.os.SystemClock.elapsedRealtime();
        if (shownAt[i] == 0) shownAt[i] = now;
        return Utilities.clamp01((now - shownAt[i] - appearDelay(seed)) / (float) MODEL_APPEAR_MS);
    }

    private final Paint emojiPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint emojiTextPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    {emojiTextPaint.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));}

    private final RectF emojiDst = new RectF();

    /** A custom emoji's current frame, advanced as a draw of it would (lottie or video), or its loaded still. */
    private Bitmap emojiFrame(AnimatedEmojiDrawable d) {
        final org.telegram.messenger.ImageReceiver ir = d.getImageReceiver();
        if (ir == null) return null;
        final RLottieDrawable lottie = ir.getLottieAnimation();
        if (lottie != null) {
            if (!sharing) lottie.updateCurrentFrame(0, false);
            return lottie.hasBitmap() ? lottie.getAnimatedBitmap() : null;
        }
        final org.telegram.ui.Components.AnimatedFileDrawable video = ir.getAnimation();
        if (video != null) {
            if (!sharing) video.updateCurrentFrame(System.currentTimeMillis(), false);
            return video.hasBitmap() ? video.getAnimatedBitmap() : null;
        }
        if (!ir.hasImageLoaded() || !ir.hasNotThumb()) return null;
        final Bitmap b = ir.getBitmap();
        return b != null && !b.isRecycled() ? b : null;
    }

    // ---- the safety gap between pattern symbols: a static, deterministic decision per lattice node. Two symbols
    // conflict if their circles at the MAXIMUM drawn size (×1.25: slot overshoot, pulse, twinkle, shine) plus both
    // orbit radii plus a 3.5dp gap overlap; the smaller one (tie: hash) is dropped. Cached per node (no allocation).
    private static final float SYM_MAX_K = 1.25f, SYM_GAP = 3.5f, SYM_SIZE_K = 1.1f; // all tiers ×1.1
    private static final float MODEL_K = 0.35f; // the model's artwork share of its box for the exclusion

    private boolean nearSticker(float bx, float by) {
        return nearSticker(bx, by, -1f);
    }

    /** symExtent < 0: the old rule (0.66·side); else model half-size + the symbol's extent + both orbits + gap */
    private boolean nearSticker(float bx, float by, float symExtent) {
        final float sdx = dp(ST_DX), sdy = dp(ST_DY);
        final int rc = Math.round(by / sdy);
        for (int r = rc - 1; r <= rc + 1; r++) {
            final float shift = (r & 1) == 0 ? 0 : sdx / 2f;
            final int cc = Math.round((bx - shift) / sdx);
            for (int col = cc - 1; col <= cc + 1; col++) {
                final double hh = latticeHash(r, col);
                final float sx = col * sdx + shift + (fract(hh * 3.7) - 0.5f) * dp(76);
                final float sy = r * sdy + (fract(hh * 5.3) - 0.5f) * dp(64);
                final float side = dp(62) + dp(32) * fract(hh * 11.9);
                final float lim = symExtent < 0f ? side * 0.66f : side * MODEL_K + symExtent + dp(1) * (5f + 7f * fract(hh * 31.7)) + dp(SYM_GAP); // the model's artwork ≈ 84% of its box
                final float ddx = sx - bx, ddy = sy - by;
                if (ddx * ddx + ddy * ddy < lim * lim) return true;
            }
        }
        return false;
    }

    /** Walks the emoji lattice; for each visible node calls back with its placement done (slot*). */
    private void drawEmojiField(Canvas c, int w, int h, Pattern from, Pattern to, float st, boolean old, boolean otherNone) {
        ensureAtlases(from);
        ensureAtlases(to);
        drawEmojiFieldInner(c, w, h, from, to, st, old, otherNone);
    }

    private void drawEmojiFieldInner(Canvas c, int w, int h, Pattern from, Pattern to, float st, boolean old, boolean otherNone) {
        final float dx = dp(EM_DX), dy = dp(EM_DY), margin = dp(SQ_BAND) * 1.3f;
        final float ox = wanderX + panX, oy = wanderY + panY;
        final int r0 = (int) Math.floor((-margin - oy) / dy) - 1, r1 = (int) Math.ceil((h + margin - oy) / dy) + 1;
        placeMin = dp(24) * 0.76f;
        for (int r = r0; r <= r1; r++) {
            final float shift = (r & 1) == 0 ? 0 : dx / 2f;
            final int c0 = (int) Math.floor((-margin - ox - shift) / dx) - 1, c1 = (int) Math.ceil((w + margin - ox - shift) / dx) + 1;
            for (int col = c0; col <= c1; col++) {
                final double hh = latticeHash(r, col);
                if (fract(hh * 53.1) < 0.2f) continue; // 20% of the nodes empty (static, per cell)
                orbit(hh, 3, 10);
                final float phase = (float) (hh * 2 * Math.PI);
                final float px = col * dx + shift + ox + orbitX;
                final float py = r * dy + oy + orbitY + (float) Math.sin(motionT * 0.45f + phase) * dp(3);
                final boolean big = (r & 1) == 0;
                final float size = dp(38) * (big ? 1.3f : 0.9f) * (0.92f + 0.16f * (float) hh);
                if (!place(px, py, size, w, h, 0, r, col, 1f + Math.max(0f, size / dp(24) - 1f) * 0.8f)) continue;
                // the largest tier draws 10% smaller about its centre; spacing / placement keep the original size
                final float gs = big ? size * 0.9f : size;
                final float rot = ((float) hh - 0.5f) * 26f + 4f * (float) Math.sin(motionT * 0.3f + phase);
                final int q = col - (r - (r & 1)) / 2;
                final float delay = fract(hh * 59.3) * SWAP_SPREAD;
                final int bi = wrapIdx(q - r, to.emoji.length);
                final float seed = fract(hh * 71.9);
                if (from == null) {
                    drawEmoji(c, to, bi, gs, rot, phase * 2f, slotScale(st - delay, old, otherNone), seed);
                } else {
                    final int ai = wrapIdx(q - r, from.emoji.length);
                    if (from.emoji[ai] == to.emoji[bi]) {
                        drawEmoji(c, to, bi, gs, rot, phase * 2f, 1f, seed);
                    } else {
                        drawEmoji(c, from, ai, gs, rot, phase * 2f, slotScale(st - delay, true, false), seed);
                        drawEmoji(c, to, bi, gs, rot, phase * 2f, slotScale(st - delay, false, false), seed);
                    }
                }
            }
        }
    }

    /** Emoji → emoji: only the slots whose emoji changes animate (shrink, then pop the new one). */
    private void drawEmojiSwap(Canvas c, int w, int h, Pattern from, Pattern to, float st) {
        drawEmojiField(c, w, h, from, to, st, false, false);
    }

    private void drawField(Canvas c, int w, int h, Pattern p, boolean old, boolean otherNone, float st) {
        drawFieldInner(c, w, h, p, old, otherNone, st);
    }

    private void drawFieldInner(Canvas c, int w, int h, Pattern p, boolean old, boolean otherNone, float st) {
        if (p == null || p.type == PAT_NONE) return;
        final float t = fieldT;
        if (p.type == PAT_EMOJI) {
            drawEmojiField(c, w, h, null, p, st, old, otherNone);
            return;
        }
        final float ox = wanderX + panX, oy = wanderY + panY;
        final float margin = dp(SQ_BAND) * 1.3f;
        // stickers (floating gifts) first: their spots keep the symbols away
        {
            final float dx = dp(ST_DX), dy = dp(ST_DY), mg = margin + dp(60);
            spN = 0;
            final int r0 = (int) Math.floor((-mg - oy) / dy) - 1, r1 = (int) Math.ceil((h + mg - oy) / dy) + 1;
            for (int r = r0; r <= r1 && spN < SPOT_CAP; r++) {
                final float shift = (r & 1) == 0 ? 0 : dx / 2f;
                final int c0 = (int) Math.floor((-mg - ox - shift) / dx) - 1, c1 = (int) Math.ceil((w + mg - ox - shift) / dx) + 1;
                for (int col = c0; col <= c1 && spN < SPOT_CAP; col++) {
                    final double hh = latticeHash(r, col);
                    orbit(hh, 5, 12);
                    spX[spN] = col * dx + shift + ox + orbitX + (fract(hh * 3.7) - 0.5f) * dp(76);
                    spY[spN] = r * dy + oy + orbitY + (fract(hh * 5.3) - 0.5f) * dp(64);
                    spSide[spN] = dp(62) + dp(32) * fract(hh * 11.9);
                    spAng[spN] = (fract(hh * 17.3) - 0.5f) * 22f;
                    spH[spN] = (float) hh;
                    spKey[spN] = ((long) r << 32) ^ (col & 0xFFFFFFFFL);
                    spN++;
                }
            }
        }
        // gift symbols: 44 × 38 lattice, jitter ±19/±16, a quarter of the nodes empty, none within 0.66·side of a sticker
        ensureAtlases(p);
        ensureSymbolBitmap(p);
        final long nowMs = AnimationUtils.currentAnimationTimeMillis();
        final Bitmap real = p.symbolBitmap;
        final Bitmap b = real != null ? real : p.fallback;
        // the real symbol replaces the stand-in with a quick crossfade
        final float fresh = real != null && p.fallback != null ? Utilities.clamp01((nowMs - p.readyAt) / 150f) : 1f;
        if (b != null) {
            final float bh = b.getWidth() / 2f;
            final float dx = dp(SY_DX), dy = dp(SY_DY);
            // every symbol is a plain drawBitmap, already in its ink (alpha in the paint): drawVertices is badly
            // optimised on a number of devices (and a mesh program of its own)
            placeMin = dp(17) * 0.76f;
            // the nodes of this frame: blue-noise (a toroidal tile of best-candidate points, built once per pattern; the
            // gift models are excluded per node)
            collectSymbolNodes(p.symbolSeed, ox, oy, w, h, margin, dx, dy);
            for (int ni = 0; ni < symN; ni++) {
                {
                    final int r = symR[ni], col = symC[ni];
                    final double hh = latticeHash(r, col);
                    final float bx = symBX[ni], by = symBY[ni];
                    final float symFit = symFitA[ni];
                    final float h5override = symH5[ni];
                    orbit(hh, 2, 7);
                    final float px = bx + ox + orbitX;
                    final float py = by + oy + orbitY;
                    final float h2 = fract(hh * 7.31), h5 = h5override;
                    final float size = dp(21) * SYM_SIZE_K * (0.55f + 0.75f * h5 * h5) * symFit;
                    if (!place(px, py, size, w, h, 1, r, col, 1f + Math.max(0f, size / dp(15) - 1f) * 0.8f)) continue;
                    final float sc = slotScale(st - (p.waveSwap || (old && pattern.waveSwap) ? waveArrival(slotX, slotY, w, h) : fract(hh * 53.1) * SWAP_SPREAD), old, otherNone);
                    if (sc <= 0.01f) continue;
                    pushSmooth(r, col, pushOffset(1f), slotLight);
                    final float push = psOut, pushLight = psLight;
                    if (Math.abs(push) > 0.05f || pushLight > 0.002f) invalidate();
                    final float x = slotX + sweepDX * push, y = slotY + sweepDY * push;
                    final float sh = slotLight;
                    // depth: a fixed share of the cells is paler at rest (the band's light is not scaled by this)
                    final float pale = fract(hh * 61.37) * 100f < RimTune.v[RimTune.PALE_SHARE] ? RimTune.v[RimTune.PALE_OPACITY] / 100f : 1f;
                    // a quiet twinkle like the stars (iOS: sin(t·(0.35 + 0.55·h2) + h·2π)²), plus the band
                    final float tw = (float) Math.pow(0.5f + 0.5f * Math.sin(t * (0.35f + 0.55f * h2) + hh * 6.283), 2);
                    final float k = sc * slotScaleK * (1f + slotPulse) * (1f + 0.06f * sh + 0.1f * tw) * (1f + RimTune.v[RimTune.PUSH_SCALE] / 100f * pushLight) * size / b.getWidth();
                    final float pushRot = RimTune.v[RimTune.PUSH_TILT] * pushLight;
                    c.save();
                    c.translate(x, y);
                    c.rotate((h2 - 0.5f) * 70f + slotRot + pushRot);
                    c.scale(k, k);
                    final int ia = (int) (255 * slotAlpha * (0.85f + 0.15f * tw) * pale);
                    if (fresh < 1f) {
                        p.tintPaint.setAlpha((int) (ia * (1f - fresh)));
                        c.drawBitmap(inked(p, p.fallback, true), -bh, -bh, p.tintPaint);
                    }
                    p.tintPaint.setAlpha((int) (ia * fresh));
                    c.drawBitmap(inked(p, b, false), -bh, -bh, p.tintPaint);
                    if (sh > 0.01f) {
                        // the band brightens the symbol itself: the same bitmap, same transform, tinted lighter
                        // (no offset core copy, no scaled ghost)
                        final ColorFilter was = p.glowPaint.getColorFilter();
                        p.glowPaint.setColorFilter(litTint(p));
                        p.glowPaint.setAlpha((int) (255 * Math.min(1f, sh * RimTune.v[RimTune.SHINE] / 100f) * slotAlpha * fresh));
                        c.drawBitmap(b, -bh, -bh, p.glowPaint);
                        p.glowPaint.setColorFilter(was);
                        if (RimTune.v[RimTune.OUTLINE] > 0.5f && ensureEdge(b)) {
                            metalPaint.setColorFilter(metalTint(p));
                            metalPaint.setAlpha((int) (Math.min(255, 255 * sh * RimTune.v[RimTune.OUT_STR] / 100f) * slotAlpha * fresh));
                            c.drawBitmap(edgeBitmap, -bh, -bh, metalPaint);
                        }
                        final float mk = slotCore * RimTune.v[RimTune.METAL] / 100f;
                        if (mk > 0.01f) {
                            metalPaint.setColorFilter(metalTint(p));
                            metalPaint.setAlpha((int) (255 * Math.min(1f, mk) * slotAlpha * fresh));
                            c.drawBitmap(b, -bh, -bh, metalPaint);
                        }
                    }
                    c.restore();
                }
            }
        }
        // the models: the drawable's current frame (one playback position for all, the lottie plays as for any
        // drawable), drawn as a plain bitmap per model; until it has a frame, its outline rasterised once (the SVG path
        // drawn as is would be Skia's stencil-then-cover, two GPU programs on the open); after the open each pops in
        // after its own random delay (the lottie, a video, or the still image where animations are off: low-end
        // devices, Lite mode)
        final Bitmap frameNow = p.model != null ? emojiFrame(p.model) : null;
        final Bitmap frame = frameNow != null && !frameNow.isRecycled() ? frameNow : null;
        final Bitmap outline = p.modelOutline; // until the model has a frame: its outline at 15%
        {
            if (frame != null || outline != null) {
                if (p.modelReadyAt == 0 && immediateFrame)
                    p.modelReadyAt = 1; // its frame (or outline) on the open's first frame: at once
                if (p.modelReadyAt == 0 && openDone)
                    p.modelReadyAt = android.os.SystemClock.elapsedRealtime(); // after the open
                final long nowMs2 = android.os.SystemClock.elapsedRealtime();
                if (frame != null && p.modelFrameAt == 0)
                    p.modelFrameAt = outline != null && p.modelReadyAt != 0 ? nowMs2 : 1; // crossfade only from a shown outline
                final float frameK = frame == null ? 0f : p.modelFrameAt == 1 ? 1f : Utilities.clamp01((nowMs2 - p.modelFrameAt) / 150f);
                if (frameK > 0f && frameK < 1f) invalidate();
                final long appearMs = sharing ? Long.MAX_VALUE : p.modelReadyAt == 0 ? -1 : nowMs2 - p.modelReadyAt;
                placeMin = dp(41);
                for (int i = 0; i < spN; i++) {
                    final float size = spSide[i];
                    final float py = spY[i] + (float) Math.sin(motionT * 0.35f + spH[i] * 2 * Math.PI) * dp(3);
                    if (!place(spX[i], py, size, w, h, 2, (int) (spKey[i] >> 32), (int) spKey[i], 1f + Math.max(0f, size / dp(40) - 1f) * 0.8f))
                        continue;
                    final float sc = slotScale(st - (p.waveSwap || (old && pattern.waveSwap) ? waveArrival(slotX, slotY, w, h) : fract(spH[i] * 53.1) * SWAP_SPREAD), old, otherNone);
                    if (sc <= 0.01f) continue;
                    final float appear = Utilities.clamp01((appearMs - appearDelay(spH[i])) / (float) MODEL_APPEAR_MS);
                    if (appear <= 0f) continue;
                    final float k = sc * (appear < 1f ? backOut(appear) : 1f) * slotScaleK * (1f + slotPulse) * (1f + 0.03f * (float) Math.sin(motionT * 1.3f + spH[i] * 12.6f)); // not lit up by the band
                    final float hs = size / 2f * k;
                    final float mpush = pushOffset(0.5f); // gift models get half of the push, no tilt
                    c.save();
                    c.translate(slotX + sweepDX * mpush, slotY + sweepDY * mpush);
                    c.rotate(spAng[i] + slotRot);
                    modelDst.set(-hs, -hs, hs, hs);
                    if (outline != null && frameK < 1f) {
                        modelOutlinePaint.setAlpha((int) (255 * 0.15f * slotAlpha * (1f - frameK)));
                        c.drawBitmap(outline, null, modelDst, modelOutlinePaint);
                    }
                    if (frame != null) {
                        modelPaint.setAlpha((int) (255 * slotAlpha * frameK));
                        c.drawBitmap(frame, null, modelDst, modelPaint);
                    }
                    c.restore();
                }
            }
        }
    }

    // --- card

    private int qrColor() {
        if (scene == null) return Color.BLACK;
        if (wave < 1f && oldScene != null) {
            // the code takes the new colour as the wave front passes the card centre
            final float cardCY = cardTop() + cardH() / 2f;
            final float maxR = (float) Math.hypot(Math.max(waveX, getWidth() - waveX), Math.max(waveY, getHeight() - waveY)) + dp(60);
            final float e = waveEase(wave);
            final float d = (float) Math.hypot(getWidth() / 2f - waveX, cardCY - waveY);
            final float k = Utilities.clamp01((maxR * e - d + dp(80)) / dp(160));
            return blendLab(oldScene.qr, scene.qr, k * k * (3 - 2 * k));
        }
        return scene.qr;
    }

    // ---- screen reveal (driven by the fragment's custom transition)
    private float reveal = 1f, uiK = 1f;
    /** The open has finished (once): the pattern's items appear only from then on. */
    private boolean openDone;
    private int revealSave;

    /** 0..1 of a ~100 ms follow-up step that starts [startMs] after the QR build began (1 when not building). */
    private float stage(int startMs) {
        final long e = qr.buildElapsedMs();
        if (e < 0) return 0f;
        if (e == Long.MAX_VALUE) return 1f;
        final float u = Utilities.clamp01((e - startMs) / 270f); // the layer's curve and duration
        return 1f - (1f - u) * (1f - u) * (1f - u);
    }

    /** One breath: element progress for open (stage) and close (mirror of the layer). */
    /** Controls follow the open spring: progress (alpha) … */
    private float elem(int startMs) {
        return reveal < 1f ? Utilities.clamp01(revealX) : 1f;
    }

    /** … and scale 0.85 → 1 (close: → 0.92) around their own centres. */
    private float elemScale(float k) {return reveal >= 1f ? 1f : revealOpening ? lerp(0.85f, 1f, k) : lerp(0.92f, 1f, k);}

    private boolean revealOpening = true;
    private float revealX = 1f;

    public void setRevealOpening(boolean opening) {revealOpening = opening;}

    /**
     * Shown as it is from the first frame, without the custom open (tablets: the default transition moves it); the
     * pattern's items and the blur still wait for the end of the open, as after the custom one.
     */
    public void setRevealStatic() {
        setReveal(1f);
        openDone = false;
    }

    /**
     * v: open = linear time over 600 ms, close = 1 → 0 over 200 ms. One value x drives the rise (64dp), the shared
     * scale (0.92 → 1 around the QR card centre; close 1 → 0.95), the fade (x / 0.35) and the halftone build.
     * Open: an iOS "smooth"-like spring (response 0.42 s, ζ 0.9); close: ease-in.
     */
    public void setReveal(float v) {
        reveal = v;
        final float x;
        if (revealOpening) {
            final double t = v * 0.86, z = 0.9, w0 = 2 * Math.PI / 0.62, wd = w0 * Math.sqrt(1 - z * z); // ×1.24 slower, everything together
            final float xs = v >= 1f ? 1f : (float) (1 - Math.exp(-z * w0 * t) * (Math.cos(wd * t) + z / Math.sqrt(1 - z * z) * Math.sin(wd * t)));
            // the spring is not settled when the transition's animator ends: blend it into exactly 1 over the last 15%
            // (smoothstep), so the end of the open has no step (it showed as a ~3 px jump of the card)
            final float bu = Utilities.clamp01((v - 0.85f) / 0.15f), bk = bu * bu * (3f - 2f * bu);
            x = xs + (1f - xs) * bk;
        } else {
            final float u = 1f - v;
            x = 1f - u * u; // ease-in
        }
        revealX = x;
        final boolean active = v < 1f;
        if (!active) openDone = true; // the pattern's items appear from now on (stay through the close)
        setTranslationY(active ? Math.round(dp(64) * (1f - x)) : 0f); // whole pixels: the layer is composited unfiltered (one GPU program)
        // rounded screen corners while it moves: the backdrop is drawn as a rounded rect (no outline clip: one more GPU
        // program on the open); the card and the controls scale inside
        setAlpha(active ? Utilities.clamp01(x / 0.35f) : 1f); // opaque at 35% of the way, the open and the close alike
        if (getParent() instanceof View)
            ((View) getParent()).setBackgroundColor(active ? Theme_multAlpha(0x40000000, x) : 0); // the profile dims
        if (!active) revealCorner = -1f; // asked again on the next open / close (see revealCorner())
        qr.setBuildOverride(active && revealOpening ? Utilities.clamp01(x) : -1f);
        // the content rendered opaque into a layer, the fade applied once when the layer is composited (no draw of its
        // own in a translucent variant)
        final int want = active ? LAYER_TYPE_HARDWARE : LAYER_TYPE_NONE;
        if (getLayerType() != want) setLayerType(want, null);
        invalidate();
    }

    private void drawCard(Canvas c, int w) {
        // the card rides up with the layer and scales 0.92 → 1 around its own centre on the same curve
        c.save();
        float cs = reveal >= 1f ? 1f : revealOpening ? lerp(0.9f, 1f, revealX) : lerp(0.92f, 1f, revealX); // the card: 0.9 → 1 on the open spring
        {
            // the swap bounce (1 → 1.03 → 0.995 → 1, 320 ms) on the whole card; continues from where a previous one was
            final float u = qrBounceStart == 0 ? 1f : Utilities.clamp01((AnimationUtils.currentAnimationTimeMillis() - qrBounceStart) / (float) BOUNCE_MS);
            cs *= bounce(qrBounceStart, 0.03f, 0.005f) + (qrBounceFrom - 1f) * (1f - u) * (1f - u);
        }
        if (cardPulseStart != 0) {
            // the card dips a little when the emoji set changes (IMG_6085 ~frame 732)
            final float u = (AnimationUtils.currentAnimationTimeMillis() - cardPulseStart) / 360f;
            if (u >= 1f) cardPulseStart = 0;
            else if (u > 0f) cs *= 1f - 0.03f * (float) Math.sin(Math.PI * u) * (1f - u * 0.3f);
        }
        c.scale(cs, cs, w / 2f, cardTop() + cardH() / 2f);
        drawCardInner(c, w);
        c.restore();
    }

    // the card's shadow: the blurred rounded rect rendered once on the CPU as an alpha mask, a quarter of the screen
    // resolution (a 24dp blur is smooth: upscaled, it looks the same), coloured on the CPU whenever the code colour
    // changes and drawn as a plain bitmap in 9 slices (the corners as they are, the sides stretched): no blur on the
    // GPU (setShadowLayer is a blurred mask per draw, its own GPU program), no tint there either
    private static final int CARD_RADIUS_DP = 30, SHADOW_BLUR_DP = 24, SHADOW_DROP_DP = 4, SHADOW_DS = 4;
    /** The shadow's alpha, SHADOW_DS px per texel; its geometry in texels. */
    private static volatile byte[] shadowMask;
    private static int shadowN, shadowE, shadowK;
    private boolean cardShadowQueued;
    private Bitmap cardShadow;
    private int[] cardShadowPx;
    private int cardShadowColor;
    private final Paint cardShadowPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final android.graphics.Rect shadowSrc = new android.graphics.Rect();
    private final RectF shadowDst = new RectF();
    private final int[] shadowSx = new int[4];
    private final float[] shadowDx = new float[4], shadowDy = new float[4];

    /** The mask, once per process (any thread; the geometry is published by the volatile mask). */
    private static synchronized byte[] cardShadowMask() {
        if (shadowMask != null) return shadowMask;
        // in texels: the blur's reach (3σ of setShadowLayer's σ = 0.57735·r + 0.5), the corner slice (reach +
        // radius + 1, past the arc), the whole mask (two corners + 2 texels to stretch)
        final float sigma = (0.57735f * AndroidUtilities.dp(SHADOW_BLUR_DP) + 0.5f) / SHADOW_DS;
        final float r = AndroidUtilities.dp(CARD_RADIUS_DP) / (float) SHADOW_DS;
        final int e = (int) Math.ceil(3f * sigma), k = e + (int) Math.ceil(r) + 1, n = 2 * k + 2;
        final Bitmap b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setMaskFilter(new android.graphics.BlurMaskFilter(Math.max(0.1f, (sigma - 0.5f) / 0.57735f), android.graphics.BlurMaskFilter.Blur.NORMAL));
        new Canvas(b).drawRoundRect(e, e, n - e, n - e, r, r, p);
        final Bitmap a = b.extractAlpha();
        b.recycle();
        final int stride = a.getRowBytes(); // ALPHA_8 rows may be padded
        final byte[] rows = new byte[stride * n], mask = new byte[n * n];
        a.copyPixelsToBuffer(java.nio.ByteBuffer.wrap(rows));
        a.recycle();
        for (int y = 0; y < n; y++) System.arraycopy(rows, y * stride, mask, y * n, n);
        shadowN = n;
        shadowE = e;
        shadowK = k;
        shadowMask = mask;
        return mask;
    }

    /** The mask times the colour: ARGB, unpremultiplied (as setPixels takes it). */
    private static void colorCardShadow(int[] px, byte[] mask, int color) {
        final int rgb = color & 0xFFFFFF, ca = color >>> 24;
        for (int i = 0; i < px.length; i++) {
            px[i] = ((mask[i] & 0xFF) * ca / 255) << 24 | rgb;
        }
    }

    /**
     * The shadow ready before the open's first frame: mask, colour and bitmap in the background, its GPU upload
     * started there too (prepareToDraw); a later colour is baked on the UI thread.
     */
    private void prepareCardShadow() {
        if (cardShadow != null || cardShadowQueued) return;
        cardShadowQueued = true;
        final int color = cardShadowColorFor(qrColor());
        Utilities.globalQueue.postRunnable(() -> {
            final byte[] mask = cardShadowMask();
            final int n = shadowN;
            final Bitmap b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
            final int[] px = new int[n * n];
            colorCardShadow(px, mask, color);
            b.setPixels(px, 0, n, 0, 0, n, n);
            b.prepareToDraw();
            AndroidUtilities.runOnUIThread(() -> {
                cardShadowQueued = false;
                if (cardShadow == null) {
                    cardShadow = b;
                    cardShadowPx = px;
                    cardShadowColor = color;
                }
            });
        });
    }

    /** The shadow's colour for a code colour: a soft lift tinted with the scene (the darkened code colour), 6%. */
    private static int cardShadowColorFor(int qr) {
        return ColorUtils.setAlphaComponent(ColorUtils.blendARGB(qr, Color.BLACK, 0.4f), 0x10);
    }

    private void drawCardShadow(Canvas c, RectF card, int color) {
        final Bitmap b = cardShadow;
        if (b == null) {
            prepareCardShadow();
            return;
        }
        final byte[] mask = shadowMask;
        final int n = shadowN;
        if (color != cardShadowColor) {
            // the colour baked in (follows the code colour, the wave included: ~n² ints, no blur)
            cardShadowColor = color;
            colorCardShadow(cardShadowPx, mask, color);
            b.setPixels(cardShadowPx, 0, n, 0, 0, n, n);
        }
        final float ds = SHADOW_DS, e = shadowE * ds, k = shadowK * ds, drop = dp(SHADOW_DROP_DP);
        shadowSx[0] = 0;
        shadowSx[1] = shadowK;
        shadowSx[2] = n - shadowK;
        shadowSx[3] = n;
        shadowDx[0] = card.left - e;
        shadowDx[1] = shadowDx[0] + k;
        shadowDx[3] = card.right + e;
        shadowDx[2] = shadowDx[3] - k;
        shadowDy[0] = card.top - e + drop;
        shadowDy[1] = shadowDy[0] + k;
        shadowDy[3] = card.bottom + e + drop;
        shadowDy[2] = shadowDy[3] - k;
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 3; i++) {
                shadowSrc.set(shadowSx[i], shadowSx[j], shadowSx[i + 1], shadowSx[j + 1]);
                shadowDst.set(shadowDx[i], shadowDy[j], shadowDx[i + 1], shadowDy[j + 1]);
                c.drawBitmap(b, shadowSrc, shadowDst, cardShadowPaint);
            }
        }
    }

    private void drawCardInner(Canvas c, int w) {
        final float cw = cardW(), ch = cardH(), left = (w - cw) / 2f, top = cardTop();
        rect.set(left, top, left + cw, top + ch);
        cardPaint.setColor(Color.WHITE);
        // a soft lift tinted with the scene (darkened QR colour), not grey: 6% alpha, 24dp blur, 4dp drop (was black 13%, 18dp, 6dp)
        drawCardShadow(c, rect, cardShadowColorFor(qrColor()));
        c.drawRoundRect(rect, dp(30), dp(30), cardPaint);
        qr.setCardBounds(left, top, left + cw, top + ch);
        final int color = qrColor();
        final float qs = cw * 0.70f, ql = left + (cw - qs) / 2f, qt = top + lerp(cw * 0.12f, (cw - qs) / 2f, tempK); // centred in the square
        qr.setColor(color);
        qr.draw(c, ql, qt, qs);
        // logo: a qr-coloured circle with the white flying plane on top, as on the stock QR screen
        final float lr = qr.logoSize(qs) / 2f, lcx = ql + qs / 2f, lcy = qt + qs / 2f;
        final float bp = qr.buildProgress();
        final float lp = 1f; // logo: part of the card (scales with it)
        final float ls = elemScale(lp);
        if (lp > 0.004f) {
            c.save();
            c.scale(ls, ls, lcx, lcy);
            if (lp < 1f) c.saveLayerAlpha(lcx - lr - 2, lcy - lr - 2, lcx + lr + 2, lcy + lr + 2, (int) (255 * lp));
            fill.setColor(color);
            c.drawCircle(lcx, lcy, lr, fill);
            // the plane flies in from the circle's bottom-left (0.55 r), 0.5 → 1, clipped to the circle; lands by ~400 ms
            final long be = qr.buildElapsedMs();
            final float pu = sharing || be == Long.MAX_VALUE ? 1f : be < 0 ? 0f : Utilities.clamp01(be / 500f);
            final float pe = pu >= 1f ? 1f : 1f - (float) Math.pow(1f - pu, 3) + 0.015f * (float) Math.sin(Math.PI * pu) * pu; // ≤ 2% settle
            if (pe > 0.004f) {
                c.save();
                logoClip.rewind();
                logoClip.addCircle(lcx, lcy, lr, Path.Direction.CW);
                c.clipPath(logoClip);
                final float off = (1f - Math.min(1f, pe)) * lr * 0.55f, psc = 0.5f + 0.5f * pe;
                c.translate(-off, off);
                c.scale(psc, psc, lcx, lcy);
                if (sharing && logoOptimal != null) {
                    // a shared image takes the plane's best frame, not the one the animation is at
                    rect3.set(lcx - lr, lcy - lr, lcx + lr, lcy + lr);
                    c.drawBitmap(logoOptimal, null, rect3, logoOptimalPaint);
                } else {
                    logo.setAlpha((int) (255 * Utilities.clamp01(pu * 2f)));
                    logo.setBounds((int) (lcx - lr), (int) (lcy - lr), (int) (lcx + lr), (int) (lcy + lr));
                    logo.draw(c);
                    logo.setAlpha(255);
                }
                c.restore();
            }
            if (lp < 1f) c.restore();
            c.restore();
        }
        // username: stock layout (condensed bold, "@" glyph drawable, 30/25/19dp until it fits)
        final StaticLayout name = nameLayout((int) cw);
        if (name != null) {
            nameTextPaint.setColor(color);
            nameAt.setColorFilter(tint(color));
            final float textH = (nameTextPaint.descent() - nameTextPaint.ascent()) * name.getLineCount();
            final float qb = qt + qs, cb = top + ch;
            // username → temporary link: the name dissolves left → right into rising particles (soft wipe)
            final boolean burst = nameBurst && !sharing;
            final float na = burst ? (tempK < 0.999f ? 1f : 0f) : 1f - tempK; // username: part of the card (gone with the temporary link)
            final float nx = left + (cw - name.getWidth()) / 2f, ny = qb + (cb - qb - textH) / 2f - dp(4);
            if (burst && nbLayout != name) sampleNameInk(name);
            if (na > 0.004f) {
                c.save();
                c.translate(nx, ny);
                final float ns = elemScale(na);
                c.scale(ns, ns, name.getWidth() / 2f, textH / 2f);
                if (burst) {
                    // the ink erased behind the sweep front, soft over the cells' ragged spread (no hard cut)
                    c.saveLayer(-dp(8), -dp(4), name.getWidth() + dp(8), name.getHeight() + dp(4), null);
                    name.draw(c);
                    final float f = tempK * NB_FRONT;
                    final float xFull = nbInkL + nbInkW * (f - NB_RAG) / (1f - NB_RAG), xNone = nbInkL + nbInkW * f / (1f - NB_RAG);
                    shaderMatrix.setScale(Math.max(1f, xNone - xFull), 1f);
                    shaderMatrix.postTranslate(xFull, 0f);
                    nbWipe.setLocalMatrix(shaderMatrix);
                    c.drawRect(-dp(8), -dp(4), name.getWidth() + dp(8), name.getHeight() + dp(4), nbWipePaint);
                    c.restore();
                } else {
                    if (na < 1f) c.saveLayerAlpha(0, -dp(4), name.getWidth(), textH + dp(4), (int) (255 * na));
                    name.draw(c);
                    if (na < 1f) c.restore();
                }
                c.restore();
            }
            if (burst) emitNameBurst(nx, ny);
        }
        // drawn over the card in its own transform like the code's particles (no clip: they rise over the QR and off it)
        if (!sharing && nbN > 0) drawNameBurst(c, color);
    }

    // ---- username burst: the name's ink sampled on a 2dp grid once per morph (≈ 80–200 cells by width),
    // each cell lifts off when the ragged sweep front passes it; the chip burn's physics (buoyancy, drag, wobble,
    // fading with distance) and the code dust's colour (the QR colour, lightening to white off the card)
    private static final int NB_CAP = 240;
    private static final float NB_RAG = 0.18f;   // threshold = (1 - RAG)·x + RAG·noise
    private static final float NB_FRONT = 1.12f; // front at tempK = 1 (past the last cell, the wipe fully through)
    private final float[] nbcx = new float[NB_CAP], nbcy = new float[NB_CAP], nbthr = new float[NB_CAP];
    private final boolean[] nbOut = new boolean[NB_CAP];
    private int nbCells;
    private float nbInkL, nbInkW;
    private StaticLayout nbLayout;                // the layout the cells were sampled from
    private boolean nameBurst;                   // the running username → temporary morph dissolves the name
    // particle pool: fixed arrays, swap-remove
    private final float[] nbx = new float[NB_CAP], nby = new float[NB_CAP], nbvx = new float[NB_CAP], nbvy = new float[NB_CAP],
        nbay = new float[NB_CAP], nbsx = new float[NB_CAP], nbsy = new float[NB_CAP], nbsize = new float[NB_CAP],
        nblife = new float[NB_CAP], nbage = new float[NB_CAP], nbwf = new float[NB_CAP], nbwp = new float[NB_CAP];
    private final boolean[] nbspark = new boolean[NB_CAP];
    private int nbN;
    private final LinearGradient nbWipe = new LinearGradient(0, 0, 1, 0, 0xFF000000, 0x00000000, Shader.TileMode.CLAMP);
    private final Paint nbWipePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    {
        nbWipePaint.setShader(nbWipe);
        nbWipePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }

    /** Once per morph: the layout drawn into an alpha mask, ink cells on a 2dp grid, thinned to ≈ 80–200. */
    private void sampleNameInk(StaticLayout name) {
        nbLayout = name;
        nbCells = 0;
        final int bw = Math.max(1, name.getWidth()), bh = Math.max(1, name.getHeight());
        final Bitmap mask = Bitmap.createBitmap(bw, bh, Bitmap.Config.ALPHA_8);
        name.draw(new Canvas(mask));
        final int[] row = new int[bw];
        final int step = Math.max(2, dp(2));
        int ink = 0, minX = bw, maxX = 0;
        for (int y = step / 2; y < bh; y += step) {
            mask.getPixels(row, 0, bw, 0, y, bw, 1);
            for (int x = step / 2; x < bw; x += step) {
                if ((row[x] >>> 24) < 96) continue;
                ink++;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
            }
        }
        if (ink == 0) {
            mask.recycle();
            nbInkL = 0;
            nbInkW = bw;
            return;
        }
        nbInkL = minX;
        nbInkW = Math.max(1, maxX - minX);
        final int want = Math.max(80, Math.min(200, (int) (nbInkW / dpf2(1.3f))));
        final float keep = Math.min(1f, want / (float) ink);
        for (int y = step / 2; y < bh; y += step) {
            mask.getPixels(row, 0, bw, 0, y, bw, 1);
            for (int x = step / 2; x < bw && nbCells < NB_CAP; x += step) {
                if ((row[x] >>> 24) < 96 || random.nextFloat() > keep) continue;
                final int k = nbCells++;
                nbcx[k] = x;
                nbcy[k] = y;
                nbthr[k] = (1f - NB_RAG) * (x - nbInkL) / nbInkW + NB_RAG * random.nextFloat();
                nbOut[k] = false;
            }
        }
        mask.recycle();
    }

    /** The cells the front has passed lift off (only while it advances; a reversed morph re-arms them). */
    private void emitNameBurst(float ox, float oy) {
        final float f = tempK * NB_FRONT;
        for (int k = 0; k < nbCells; k++) {
            final boolean in = nbthr[k] < f;
            if (in && !nbOut[k] && tempTarget == 1f && nbN < NB_CAP) {
                final int i = nbN++;
                final boolean spark = random.nextFloat() < 0.12f;
                nbx[i] = nbsx[i] = ox + nbcx[k] + (random.nextFloat() - 0.5f) * dp(2);
                nby[i] = nbsy[i] = oy + nbcy[k] + (random.nextFloat() - 0.5f) * dp(2);
                // a light kick up and a little along the sweep, then the air lifts them (stronger than the chip's trail)
                nbvx[i] = dp(6) + (random.nextFloat() - 0.5f) * dp(36);
                nbvy[i] = -(dp(18) + random.nextFloat() * dp(36));
                nbay[i] = -(dp(60) + random.nextFloat() * dp(70));
                nbsize[i] = spark ? dpf2(1.6f) + random.nextFloat() * dpf2(0.8f) : dpf2(1.2f) + random.nextFloat() * dpf2(1.3f);
                nblife[i] = 0.6f + random.nextFloat() * 0.4f;
                nbage[i] = 0f;
                nbwf[i] = 4f + random.nextFloat() * 3f;
                nbwp[i] = random.nextFloat() * 6.2832f;
                nbspark[i] = spark;
            }
            nbOut[k] = in;
        }
    }

    private void drawNameBurst(Canvas c, int color) {
        final float dt = 0.016667f * frameK;
        for (int i = nbN - 1; i >= 0; i--) {
            nbage[i] += dt;
            if (nbage[i] >= nblife[i]) {
                final int l = --nbN;
                nbx[i] = nbx[l];
                nby[i] = nby[l];
                nbvx[i] = nbvx[l];
                nbvy[i] = nbvy[l];
                nbay[i] = nbay[l];
                nbsx[i] = nbsx[l];
                nbsy[i] = nbsy[l];
                nbsize[i] = nbsize[l];
                nblife[i] = nblife[l];
                nbage[i] = nbage[l];
                nbwf[i] = nbwf[l];
                nbwp[i] = nbwp[l];
                nbspark[i] = nbspark[l];
                continue;
            }
            // the burn particles' motion: buoyancy, side drag 1.6/s, vertical drag 0.8/s
            nbvy[i] += nbay[i] * dt;
            nbvx[i] *= (1f - 1.6f * dt);
            nbvy[i] *= (1f - 0.8f * dt);
            nbx[i] += nbvx[i] * dt;
            nby[i] += nbvy[i] * dt;
            final float ddx = nbx[i] - nbsx[i], ddy = nby[i] - nbsy[i];
            final float dist = (float) Math.sqrt(ddx * ddx + ddy * ddy);
            final float t = nbage[i] / nblife[i];
            float a = t < 0.08f ? t / 0.08f : (float) Math.pow(1f - (t - 0.08f) / 0.92f, 0.55f);
            a *= (float) Math.exp(-dist / dp(90));
            final float rr = nbsize[i] / 2f * (1f - 0.4f * Math.min(1f, dist / dp(90)));
            final float sp = (float) Math.hypot(nbvx[i], nbvy[i]);
            float dx = nbx[i], dy = nby[i];
            if (sp > 1f) {
                final float wob = dp(3) * (float) Math.sin(nbage[i] * nbwf[i] + nbwp[i]);
                dx += -nbvy[i] / sp * wob;
                dy += nbvx[i] / sp * wob;
            }
            // the name's colour on the card, lightening to white as it leaves it (as the code's dust)
            final float ox = Math.max(rect.left - dx, Math.max(0f, dx - rect.right));
            final float oy = Math.max(rect.top - dy, Math.max(0f, dy - rect.bottom));
            final float out = Math.min(1f, Math.max(ox, oy) / dp(36));
            fill.setColor(out > 0f ? QrCodeLayer.mixOklab(color, Color.WHITE, out) : color);
            fill.setAlpha((int) (255 * a));
            final float st = Math.min(1.8f, 1f + sp / dp(220));
            c.save();
            c.rotate((float) Math.toDegrees(Math.atan2(nbvy[i], nbvx[i])), dx, dy);
            sparkOval.set(dx - rr * st, dy - rr, dx + rr * st, dy + rr);
            c.drawOval(sparkOval, fill);
            c.restore();
            if (nbspark[i]) {
                fill.setAlpha((int) (36 * a));
                c.drawCircle(dx, dy, rr * 2.2f, fill);
            }
        }
        if (nbN > 0) invalidate();
    }

    private final Path logoClip = new Path();
    private final TextPaint nameTextPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG);
    private Drawable nameAt;
    private StaticLayout nameLayout;
    private int nameLayoutWidth;

    /** Same as QrActivity.QrView#prepareContent: the largest of three sizes that fits on one line, else wraps. */
    private StaticLayout nameLayout(int cardWidth) {
        if (nameLayout != null && nameLayoutWidth == cardWidth) return nameLayout;
        nameLayoutWidth = cardWidth;
        final String userText = (caption != null ? caption : "").toUpperCase();
        nameTextPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rcondensedbold.ttf"));
        final int textMaxWidth = cardWidth - dp(20) * 2;
        nameLayout = null;
        for (int i = 0; i <= 2; ++i) {
            if (i == 0) {
                nameAt = d(R.drawable.qr_at_large);
                nameTextPaint.setTextSize(dp(30));
            } else if (i == 1) {
                nameAt = d(R.drawable.qr_at_medium);
                nameTextPaint.setTextSize(dp(25));
            } else {
                nameAt = d(R.drawable.qr_at_small);
                nameTextPaint.setTextSize(dp(19));
            }
            nameAt.setBounds(0, 0, nameAt.getIntrinsicWidth(), nameAt.getIntrinsicHeight());
            // the "@" glyph only in front of a username, not a phone number or a name
            final SpannableStringBuilder string = new SpannableStringBuilder(captionIsUsername ? " " + userText : userText);
            if (captionIsUsername)
                string.setSpan(new SettingsSearchCell.VerticalImageSpan(nameAt), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            final int glyphW = captionIsUsername ? nameAt.getBounds().width() : 0;
            final float textWidth = nameTextPaint.measureText(string, captionIsUsername ? 1 : 0, string.length()) + glyphW;
            if (i <= 1 && textWidth > textMaxWidth) {
                continue;
            }
            int linesCount = textWidth > textMaxWidth ? 2 : 1;
            int layoutWidth = textMaxWidth;
            if (linesCount > 1) {
                layoutWidth = (int) (textWidth + glyphW) / 2 + dp(2);
            }
            if (layoutWidth > textMaxWidth) {
                linesCount = 3;
                layoutWidth = (int) (textWidth + glyphW) / 3 + dp(4);
            }
            nameLayout = StaticLayoutEx.createStaticLayout(string, nameTextPaint, layoutWidth, Layout.Alignment.ALIGN_CENTER, 1f, 0f, false, null, Math.min(layoutWidth + dp(10), cardWidth), linesCount);
            break;
        }
        return nameLayout;
    }

    // --- chip with burning timer

    private void drawChip(Canvas c, int w, long now) {
        // the fee chip fades with its glass + rim drawn at that alpha straight onto the scene (an offscreen layer hid the
        // Dark out rim, which needs the backdrop under it, until the fade ended); without a fee a temporary link shows
        // its expiry chip instead (info only, no tap)
        final float tempA = tempK * (1f - paidK);
        if (paidK > 0.001f) {
            // paid messages toggled: the chip grows in / shrinks away (scale only, no fade layer)
            final boolean scaled = paidK < 0.999f;
            if (scaled) {
                c.save();
                c.scale(paidK, paidK, w / 2f, chipCY());
            }
            drawFeeChip(c, w, now);
            if (scaled) c.restore();
        } else {
            chipRect.setEmpty();
        }
        if (tempA > 0.001f) drawTempChip(c, w, tempA);
    }

    private void drawFeeChip(Canvas c, int w, long now) {
        final float cy = chipCY();
        chipPaint.setTextSize(dp(11));
        final float feeW = dp(FEE_PAD) + chipPaint.measureText(feeA) + dp(STAR_DP) + dp(STAR_GAP - 1) + chipPaint.measureText(feeB) + dp(FEE_PAD); // the price 1dp closer to the star
        final float freeW = !free && restoreFreeW > 0f && chipSweep > 0.001f ? restoreFreeW : dp(CHIP_PAD_L) + dp(STAR_DP) + dp(STAR_GAP) + chipText.getCurrentWidth() + dp(11);
        if (!free && chipSweep <= 0.001f) restoreFreeW = 0f;
        final float sweepE = chipSweep;
        final float cw = lerp(feeW, freeW, free ? Math.max(sweepE, sweepDone ? 1f : 0f) : sweepE), ch = dp(22);
        final float left = (w - cw) / 2f;
        rect.set(left, cy - ch / 2f, left + cw, cy + ch / 2f);
        chipRect.set(rect);
        final boolean light = scene != null && scene.light;
        if (!chipSkipGlass) {
            glassButton = true;
            glassSmall = true;
            drawGlass(c, rect, ch / 2f, light, 1f);
            glassButton = false;
            glassSmall = false;
        }
        drawRipple(c, RP_CHIP, rect, light);

        // white fill: sweeps in from the left, then burns down from the right as the minute runs out. Dark out: the
        // fill covers the rim too (the grown rim shape), so a white chip reads as one solid shape
        final RectF fr = chipFillRect;
        float fRad = ch / 2f;
        fr.set(rect);
        {
            final float gin = Math.min(0f, dpf2(RimTune.g[RimTune.SMALL][RimTune.INSET]));
            fr.inset(gin, gin);
            fRad -= gin;
        }
        final boolean shattered = shatterAt > 0 && !free;
        if (shatterPending) {
            // the restore frame: remember the filled area and burst it into droplets
            shatterPending = false;
            shatterRect.set(fr.left, fr.top, fr.left + fr.width() * chipSweep * burnFrac, fr.bottom);
            shatterBurst(shatterRect, rect);
        }
        final float frac = shattered ? 0f : chipSweep * burnFrac;
        final float edge = fr.left + fr.width() * frac;
        final float shU = shattered ? Utilities.clamp01((AnimationUtils.currentAnimationTimeMillis() - shatterAt) / 80f) : 1f;
        if (shattered && shU < 1f) {
            // the old fill fades out in 60 ms, its dark content with it (80 ms); the fee content fades in on glass
            final float fa = 1f - Utilities.clamp01((AnimationUtils.currentAnimationTimeMillis() - shatterAt) / 60f);
            c.save();
            path.rewind();
            path.addRoundRect(fr, fRad, fRad, Path.Direction.CW);
            c.clipPath(path);
            c.clipRect(shatterRect);
            if (fa > 0f) {
                fill.setColor(0xF7FFFFFF);
                fill.setAlpha((int) (0xF7 * fa));
                c.drawRect(shatterRect, fill);
            }
            drawFreeContent(c, rect, Theme_multAlpha(scene != null ? qrColor() : Color.BLACK, 1f - shU));
            c.restore();
            invalidate();
        }
        final boolean burning = free && sweepDone && timerOn && frac < 0.999f;
        buildChipEdge(edge, rect, fr, free && !sweepDone);
        if (frac > 0.001f) {
            c.save();
            path.rewind();
            path.addRoundRect(fr, fRad, fRad, Path.Direction.CW);
            c.clipPath(path);
            c.clipPath(chipEdgePath);
            fill.setColor(0xF7FFFFFF);
            if (burning) {
                // torn-paper end while it burns (as on iOS): a jagged line, stable for a given edge position
                buildTornPath(fr, edge);
                c.drawPath(tornPath, fill);
            } else {
                c.drawRect(fr.left, fr.top, edge + chipEdgeAmp, fr.bottom, fill);
            }
            // a neutral white sheen just behind the edge (burning or sweeping in)
            if (burning || (free && !sweepDone)) {
                chipSheenM.setScale(dp(23), 1f);
                chipSheenM.postTranslate(edge - dp(22), 0);
                chipSheen.setLocalMatrix(chipSheenM);
                fill.setShader(chipSheen);
                c.drawRect(edge - dp(22), fr.top, edge + dp(1), fr.bottom, fill);
                fill.setShader(null);
            }
            c.restore();
            // dust off the moving fill edge, frame-rate independent (~260/s while it sweeps in; a quiet trickle while burning)
            final float dt = 0.016667f * frameK;
            final float maxLife = burning ? 1.2f : (fillEndsAt + 250 - System.currentTimeMillis()) / 1000f; // trail gone ~250 ms after the fill
            burnEdgeX = edge;
            if (burning) {
                // 1) edge fizz: a dense bright band hugging the edge
                final int ne = (int) Math.floor(110f * dt + random.nextFloat());
                for (int q = 0; q < ne; q++) {
                    final float ey = rect.top + random.nextFloat() * ch;
                    chipEmitBurn(edge + tornOffset(ey, edge, fr.top) + random.nextFloat() * dp(3), ey,
                        dp(10) + random.nextFloat() * dp(20), -(dp(4) + random.nextFloat() * dp(10)), 0f,
                        dpf2(0.8f) + random.nextFloat() * dpf2(0.7f), 0.25f + random.nextFloat() * 0.25f, 0.9f + random.nextFloat() * 0.1f, false, false);
                }
                // 2) the rising trail (12% sparks): right, then lifted by the air
                final int nt = (int) Math.floor(70f * dt + random.nextFloat());
                for (int q = 0; q < nt; q++) {
                    final boolean spark = random.nextFloat() < 0.12f;
                    final float ty = rect.top + random.nextFloat() * ch;
                    chipEmitBurn(edge + tornOffset(ty, edge, fr.top) + dp(2) + random.nextFloat() * dp(10), ty,
                        dp(40) + random.nextFloat() * dp(70), -(dp(10) + random.nextFloat() * dp(30)), -(dp(26) + random.nextFloat() * dp(34)),
                        spark ? dpf2(1.4f) + random.nextFloat() * dpf2(0.8f) : dpf2(0.9f) + random.nextFloat() * dpf2(1.1f),
                        0.6f + random.nextFloat() * 0.7f, 1f, spark, true);
                }
            } else {
                final int n = (int) Math.floor((free && !sweepDone ? 260f : 0f) * dt + random.nextFloat());
                for (int q = 0; q < n; q++) chipEmit(edge, rect.top + random.nextFloat() * ch, maxLife);
            }
        }
        final int onGlass = light ? 0xE6000000 : 0xF2FFFFFF;
        final int onFill = scene != null ? qrColor() : Color.BLACK;
        // glass side: the old (fee) text until the free text has fully swept in; then the free text (burnt part)
        c.save();
        path.rewind();
        path.addRoundRect(rect, ch / 2f, ch / 2f, Path.Direction.CW);
        c.clipPath(path);
        if (frac > 0.001f) QrRender.clipOut(c, burning ? tornPath : chipEdgePath);
        if (free && sweepDone) {
            drawFreeContent(c, rect, onGlass);
        } else {
            // the old text dims ahead of the sweep instead of being cut hard
            drawFeeContent(c, rect, Theme_multAlpha(onGlass, shattered ? shU : 1f - Utilities.clamp01(chipSweep * 1.25f)));
        }
        c.restore();
        if (frac > 0.001f) {
            c.save();
            c.clipPath(burning ? tornPath : chipEdgePath);
            drawFreeContent(c, rect, onFill);
            c.restore();
        }
        if (chipBurstPending) {
            // the tap: a burst from the left 40% where the fill starts
            chipBurstPending = false;
            for (int q = 0; q < 24; q++)
                chipEmit(rect.left + random.nextFloat() * rect.width() * 0.4f, rect.top + random.nextFloat() * rect.height(), 0.5f);
        }
        drawSparks(c);
    }

    private final RectF chipRect = new RectF(), chipFillRect = new RectF();

    // the fill's ragged edge (as in the first QR screen): a few slowly wobbling points, bigger waves while the fill
    // flows in; quiet at the chip's ends, so the round tips are never bitten
    private static final int CHIP_EDGE_POINTS = 7;
    private final Path chipEdgePath = new Path();
    private final float[] chipEdgeX = new float[CHIP_EDGE_POINTS];
    private float chipEdgeAmp;

    /** chipEdgePath = everything left of the fill edge at x, with the ragged edge; a little beyond the chip. */
    private void buildChipEdge(float x, RectF chip, RectF fill, boolean flowing) {
        final float t = (android.os.SystemClock.elapsedRealtime() % 100_000) / 1000f;
        final float ends = Utilities.clamp01((x - fill.left) / dp(6)) * Utilities.clamp01((fill.right - x) / dp(6));
        chipEdgeAmp = dpf2(flowing ? 5f : 2.2f) * ends;
        final float top = chip.top - dp(4), bottom = chip.bottom + dp(4), h = bottom - top;
        for (int i = 0; i < CHIP_EDGE_POINTS; i++) {
            final float n = (float) (Math.sin(t * 5.1f + i * 1.7f) * .6f + Math.sin(t * 8.3f + i * 2.9f) * .4f);
            chipEdgeX[i] = x + n * chipEdgeAmp;
        }
        final Path p = chipEdgePath;
        p.rewind();
        p.moveTo(chip.left - dp(8), top);
        p.lineTo(chipEdgeX[0], top);
        for (int i = 1; i < CHIP_EDGE_POINTS; i++) {
            final float y0 = top + h * (i - 1) / (CHIP_EDGE_POINTS - 1), y1 = top + h * i / (CHIP_EDGE_POINTS - 1);
            p.quadTo(chipEdgeX[i - 1], y0, (chipEdgeX[i - 1] + chipEdgeX[i]) / 2f, (y0 + y1) / 2f);
        }
        p.lineTo(chipEdgeX[CHIP_EDGE_POINTS - 1], bottom);
        p.lineTo(chip.left - dp(8), bottom);
        p.close();
        // the waves keep moving while the edge is inside the chip
        if (chipEdgeAmp > 0.01f) invalidate();
    }

    private final Matrix tileM = new Matrix(), chipSheenM = new Matrix();
    // the sheen behind the fill edge in unit space, placed with a matrix (no per-frame allocation)
    private final LinearGradient chipSheen = new LinearGradient(0, 0, 1, 0, new int[]{0x00FFFFFF, 0xFFFFFFFF, 0x00FFFFFF}, new float[]{0, 0.88f, 1}, Shader.TileMode.CLAMP);
    // a gift tile's backdrop in unit space (centre 0, radius 1) by the gift id, placed with tileM
    private final android.util.LongSparseArray<RadialGradient> tileShaders = new android.util.LongSparseArray<>();
    private final Path tornPath = new Path();

    /** The fill up to a torn edge at x = edge: notches of ~1–3 dp, fine along the height. The noise depends only on
     *  the height and the edge position (no time), so it never flickers and evolves smoothly as the edge burns. */
    private void buildTornPath(RectF fr, float edge) {
        tornPath.rewind();
        final float top = fr.top - dp(2), bottom = fr.bottom + dp(2), step = dpf2(0.75f);
        tornPath.moveTo(fr.left - dp(4), top);
        for (float y = top; ; y += step) {
            final float yy = Math.min(y, bottom);
            tornPath.lineTo(edge + tornOffset(yy, edge, fr.top), yy);
            if (yy >= bottom) break;
        }
        tornPath.lineTo(fr.left - dp(4), bottom);
        tornPath.close();
    }

    /** Horizontal offset of the torn edge at height y: two octaves of value noise over (height, edge position). */
    private float tornOffset(float y, float edge, float top) {
        final float u = (y - top) / dpf2(1.7f), v = edge / dpf2(3.2f);
        final float n = 0.68f * valueNoise(u, v, 17) + 0.32f * valueNoise(u * 2.6f, v * 1.7f, 91);
        return (n - 0.5f) * dpf2(3.6f);
    }

    private static float valueNoise(float x, float y, int seed) {
        final int xi = (int) Math.floor(x), yi = (int) Math.floor(y);
        float fx = x - xi, fy = y - yi;
        fx = fx * fx * (3f - 2f * fx);
        fy = fy * fy * (3f - 2f * fy);
        final float a = hash01(xi, yi, seed), b = hash01(xi + 1, yi, seed), c = hash01(xi, yi + 1, seed), d = hash01(xi + 1, yi + 1, seed);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    private static float hash01(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
    }

    private boolean chipSkipGlass; // while the chip is part of the menu blob

    private static final int STAR_DP = 12, STAR_GAP = 2, CHIP_PAD_L = 8; // star sits 2dp closer to the "50"

    private void drawFeeContent(Canvas c, RectF r, int color) {
        final float cy = r.centerY(), is = dp(STAR_DP);
        final float x = r.left + dp(FEE_PAD); // left-anchored like the free label (the chip width animates around it)
        chipPaint.setColor(color);
        c.drawText(feeA, x, cy + dp(4), chipPaint);
        final float sx = x + chipPaint.measureText(feeA);
        drawStar(c, sx, cy, is, color, false);
        chipPaint.setColor(color);
        c.drawText(feeB, sx + is + dp(STAR_GAP - 1), cy + dp(4), chipPaint); // the price 1dp closer to the star
    }

    private void drawFreeContent(Canvas c, RectF r, int color) {
        final float cy = r.centerY(), is = dp(STAR_DP);
        final float total = is + dp(STAR_GAP) + chipText.getCurrentWidth();
        final float x = r.left + dp(CHIP_PAD_L);
        drawStar(c, x, cy, is, color, true);
        chipText.setTextColor(color);
        chipText.setBounds((int) (x + is + dp(STAR_GAP)), (int) (cy - dp(8)), (int) (x + total + dp(40)), (int) (cy + dp(8)));
        chipText.draw(c);
    }

    /** The filled premium star; crossed = the same star with a cut gap and a slash, like the app's "off" icons. */
    private void drawStar(Canvas c, float x, float cy, float is, int color, boolean crossed) {
        icStar.setColorFilter(tint(color));
        icStar.setAlpha(255);
        icStar.setBounds((int) x, (int) (cy - is / 2f), (int) (x + is), (int) (cy + is / 2f));
        if (!crossed) {
            icStar.draw(c);
            return;
        }
        final float lw = is * 0.095f, gap = is * 0.065f; // proportional to the star
        // the slash stays inside the star: ~75% of its diagonal, centred
        final float x1 = x + is * 0.28f, y1 = cy - is * 0.21f, x2 = x + is * 0.74f, y2 = cy + is * 0.25f;
        rect2.set(x - dp(2), cy - is / 2f - dp(2), x + is + dp(2), cy + is / 2f + dp(2));
        c.saveLayer(rect2, null);
        icStar.draw(c);
        cutPaint.setStrokeWidth(lw + gap * 2f);
        c.drawLine(x1, y1, x2, y2, cutPaint);
        c.restore();
        stroke.setColor(color);
        stroke.setStrokeWidth(lw);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(x1, y1, x2, y2, stroke);
        stroke.setStrokeCap(Paint.Cap.BUTT);
    }

    private final RectF sparkOval = new RectF();

    private void drawSparks(Canvas c) {
        final float dt = 0.016667f * frameK;
        final float ceiling = chipRect.top - dp(40);
        final float burnCeil = chipRect.top - dp(48);
        for (int i = cpN - 1; i >= 0; i--) {
            cpage[i] += dt;
            if (cpage[i] >= cplife[i]) {
                final int l = --cpN;
                cpx[i] = cpx[l];
                cpy[i] = cpy[l];
                cpvx[i] = cpvx[l];
                cpvy[i] = cpvy[l];
                cplife[i] = cplife[l];
                cpage[i] = cpage[l];
                cpsize[i] = cpsize[l];
                cprot[i] = cprot[l];
                cpvr[i] = cpvr[l];
                cpkind[i] = cpkind[l];
                cpburn[i] = cpburn[l];
                cpsx[i] = cpsx[l];
                cpsy[i] = cpsy[l];
                cpay[i] = cpay[l];
                cpwf[i] = cpwf[l];
                cpwp[i] = cpwp[l];
                continue;
            }
            if (cpkind[i] >= 3) {
                // shatter droplets: drag 2.4/s, a little gravity after 120 ms, soft ceiling 70dp above the chip
                cpvx[i] *= (1f - 2.4f * dt);
                cpvy[i] *= (1f - 2.4f * dt);
                if (cpage[i] > 0.12f) cpvy[i] += dp(120) * dt;
                if (cpy[i] < chipRect.top - dp(70)) cpvy[i] *= 0.85f;
            } else if (cpburn[i]) {
                cpvy[i] += cpay[i] * dt;          // buoyancy
                cpvx[i] *= (1f - 1.6f * dt);
                cpvy[i] *= (1f - 0.8f * dt);
                if (cpy[i] < burnCeil) cpvy[i] *= (float) Math.pow(0.9f, frameK);
            } else {
                cpvy[i] -= dp(20) * dt;
                cpvx[i] *= (1f - 3f * dt);
                cpvy[i] *= (1f - 3f * dt);
                if (cpy[i] < ceiling) cpvy[i] *= 0.85f; // the same soft ceiling as the QR dust
            }
            cpx[i] += cpvx[i] * dt;
            cpy[i] += cpvy[i] * dt;
            final float t = cpage[i] / cplife[i];
            float a = t < 0.12f ? t / 0.12f : (float) Math.pow(1f - (t - 0.12f) / 0.88f, cpburn[i] ? 0.55f : 0.7f);
            if (cpy[i] < ceiling) a *= Math.max(0f, 1f - (ceiling - cpy[i]) / dp(24));
            final float r = cpsize[i] * (1f - 0.5f * t) / 2f;
            fill.setColor(0xFFFFFFFF);
            fill.setAlpha((int) (255 * a));
            if (cpkind[i] >= 3) {
                // round droplets stretched along their velocity (≤ 2×), sparks with a faint halo
                final float spd = (float) Math.hypot(cpvx[i], cpvy[i]);
                final float st = Math.min(2f, 1f + spd / dp(200));
                final float rr0 = cpsize[i] * (1f - 0.4f * t) / 2f;
                float aa = a;
                if (cpy[i] < chipRect.top - dp(70)) aa *= Math.max(0f, 1f - (chipRect.top - dp(70) - cpy[i]) / dp(16));
                fill.setAlpha((int) (255 * aa));
                c.save();
                c.rotate((float) Math.toDegrees(Math.atan2(cpvy[i], cpvx[i])), cpx[i], cpy[i]);
                sparkOval.set(cpx[i] - rr0 * st, cpy[i] - rr0, cpx[i] + rr0 * st, cpy[i] + rr0);
                c.drawOval(sparkOval, fill);
                c.restore();
                if (cpkind[i] == 4) {
                    fill.setAlpha((int) (36 * aa));
                    c.drawCircle(cpx[i], cpy[i], rr0 * 2.2f, fill);
                }
                continue;
            }
            if (cpburn[i]) {
                // brightest and densest at the edge: fades and thins with the distance from its spawn point
                final float ddx = cpx[i] - cpsx[i], ddy = cpy[i] - cpsy[i];
                final float dist = (float) Math.sqrt(ddx * ddx + ddy * ddy);
                final float tb = cpage[i] / cplife[i];
                float ab = tb < 0.08f ? tb / 0.08f : (float) Math.pow(1f - (tb - 0.08f) / 0.92f, 0.55f);
                ab *= cprot[i] * (float) Math.exp(-dist / dp(44));
                if (cpy[i] < burnCeil) ab *= Math.max(0f, 1f - (burnCeil - cpy[i]) / dp(16));
                final float rr = cpsize[i] / 2f * (1f - 0.4f * Math.min(1f, dist / dp(70)));
                // flutter: a small wobble along the normal of the velocity
                final float sp = (float) Math.hypot(cpvx[i], cpvy[i]);
                float dx = cpx[i], dy = cpy[i];
                if (cpwf[i] > 0f && sp > 1f) {
                    final float wob = dp(3) * (float) Math.sin(cpage[i] * cpwf[i] + cpwp[i]);
                    dx += -cpvy[i] / sp * wob;
                    dy += cpvx[i] / sp * wob;
                }
                fill.setAlpha((int) (255 * ab));
                // a slight stretch along the velocity, round ends
                final float st = Math.min(1.8f, 1f + sp / dp(220));
                c.save();
                c.rotate((float) Math.toDegrees(Math.atan2(cpvy[i], cpvx[i])), dx, dy);
                sparkOval.set(dx - rr * st, dy - rr, dx + rr * st, dy + rr);
                c.drawOval(sparkOval, fill);
                c.restore();
                if (cpkind[i] == 1) {
                    fill.setAlpha((int) (36 * ab));
                    c.drawCircle(dx, dy, rr * 2.2f, fill);
                }
                continue;
            } else {
                c.drawCircle(cpx[i], cpy[i], cpkind[i] == 1 ? r * 2f : r, fill);
            }
            if (cpkind[i] == 1) {
                fill.setAlpha((int) (36 * a));
                c.drawCircle(cpx[i], cpy[i], r * 4.4f, fill);
            }
        }
    }

    // --- glass

    /** Glass in any outline (the menu droplet): same blur, tint, sheen and rim as the rounded-rect glass. */
    private void drawGlassPath(Canvas c, Path p, RectF bounds, boolean light, float alpha) {
        if (alpha <= 0) return;
        c.save();
        c.clipPath(p);
        if (QrRender.hasEffects() && hasBlur(c)) {
            if (alpha < 1f) c.saveLayerAlpha(bounds, (int) (255 * alpha));
            drawBlurNode(c, blurNode);
            if (alpha < 1f) c.restore();
        } else if (blurPending(c)) {
            backdropPaint.setAlpha((int) (255 * Utilities.clamp01(alpha))); // fades with the glass (no frost switch)
            drawBlurStandIn(c);
            backdropPaint.setAlpha(255);
        } else {
            drawFrost(c, bounds, alpha);
        }
        fill.setColor(light ? 0x12000000 : 0x14FFFFFF); // menu glass ~8%
        fill.setAlpha((int) (Color.alpha(fill.getColor()) * alpha));
        c.drawRect(bounds, fill);
        QrGlass.drawSheen(c, bounds, light, alpha);
        c.restore();
        drawDarkOut(c, p, bounds, 0, alpha);
    }

    private void drawGlass(Canvas c, RectF r, float radius, boolean light, float alpha) {
        if (alpha <= 0) return;
        // sheets: the glass body covers the rim's grown 1dp band too (else raw backdrop shows between the glass edge
        // and the outer dark ring: a second dark band along the sides)
        RectF gb = r;
        float gbR = radius;
        if (glassSheet) {
            final float gin = Math.min(0f, dpf2(RimTune.g[RimTune.SHEET][RimTune.INSET]));
            glassBody.set(r);
            glassBody.inset(gin, gin);
            gb = glassBody;
            gbR = radius - gin;
        }
        final boolean standIn = !hasBlur(c) && blurPending(c);
        if (standIn) drawBlurStandIn(c, gb, gbR, alpha);
        path.rewind();
        path.addRoundRect(gb, gbR, gbR, Path.Direction.CW);
        c.save();
        c.clipPath(path);
        if (standIn) {
            // drawn above (no clip)
        } else if (QrRender.hasEffects() && hasBlur(c)) {
            if (alpha < 1f) c.saveLayerAlpha(gb, (int) (255 * alpha));
            if (liftThis && sheetLift > 0.004f) {
                c.save();
                c.scale(LIFT_DS, LIFT_DS);
                QrRender.draw(c, liftedBlur());
                c.restore();
            } else {
                drawBlurNode(c, blurNode);
            }
            if (alpha < 1f) c.restore();
        } else {
            drawFrost(c, gb, alpha);
        }
        // on a light scene white glass vanishes: tint it slightly dark instead (iOS does the same)
        fill.setColor(glassFill != 0 ? glassFill : (light ? 0x12000000 : argb((RimTune.v[RimTune.FILL] + (liftThis ? RimTune.v[RimTune.SH_FILL] * sheetLift : 0f)) / 100f, 0xFFFFFF))); // tuner: glass fill white (+ the sheet's lift)
        fill.setAlpha((int) (Color.alpha(fill.getColor()) * alpha));
        c.drawRect(gb, fill);
        // light from above: a soft sheen on the upper half
        QrGlass.drawSheen(c, gb, light, alpha);
        // glint: the light band crossing this glass (same field, same frame as the pattern): a soft white sheen
        // following the band, sampled at the element's left / centre / right along the sweep, ≤ 10% white at peak
        final float gl = liftThis || glassSheet ? 0f : RimTune.v[RimTune.GLINT] / 100f * glintGate;
        if ((gl > 0f || RimTune.v[RimTune.GLINT_SURF] > 0f) && shining && !(liftThis || glassSheet)) {
            final int vw = getWidth(), vh = getHeight();
            final float s0 = shine(gb.left, gb.centerY(), vw, vh), s1 = shine(gb.centerX(), gb.centerY(), vw, vh), s2 = shine(gb.right, gb.centerY(), vw, vh);
            if (s0 + s1 + s2 > 0.01f) {
                // no allocation: cached unit ramps placed by a matrix (left half a0→a1, right half a1→a2)
                final float gs = 1.25f * RimTune.v[RimTune.GLINT_SURF] / 100f * glintGate; // ×1.25 gain
                drawGlint(c, gb, 0.10f * gs * s0 * alpha, 0.10f * gs * s1 * alpha, 0.10f * gs * s2 * alpha);
            }
        }
        c.restore();
        drawDarkOut(c, null, r, radius, alpha);
    }

    private final RectF rect2 = new RectF(), glassBody = new RectF();
    // the glint sheen: three alpha levels (left / centre / right) → two cached unit ramps (0→1 white), placed by a
    // matrix and combined as base + ramp·Δ — no allocation per frame
    private final LinearGradient glintRampUp = new LinearGradient(0, 0, 1, 0, 0x00FFFFFF, 0xFFFFFFFF, Shader.TileMode.CLAMP);
    private final LinearGradient glintRampDown = new LinearGradient(0, 0, 1, 0, 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
    private final Matrix glintM = new Matrix();
    private final Paint glintPaint = new Paint();

    private void drawGlint(Canvas c, RectF r, float a0, float a1, float a2) {
        final float mx = r.centerX();
        // left half: from a0 to a1
        drawRamp(c, r.left, mx, r, a0, a1);
        drawRamp(c, mx, r.right, r, a1, a2);
    }

    private void drawRamp(Canvas c, float x0, float x1, RectF r, float aFrom, float aTo) {
        final float lo = Math.min(aFrom, aTo), hi = Math.max(aFrom, aTo);
        if (lo > 0.002f) {
            glintPaint.setShader(null);
            glintPaint.setColor(argb(lo, 0xFFFFFF));
            c.drawRect(x0, r.top, x1, r.bottom, glintPaint);
        }
        if (hi - lo > 0.002f) {
            final LinearGradient g = aTo >= aFrom ? glintRampUp : glintRampDown;
            glintM.setScale(x1 - x0, 1f);
            glintM.postTranslate(x0, 0);
            g.setLocalMatrix(glintM);
            glintPaint.setShader(g);
            glintPaint.setColor(Color.WHITE);
            glintPaint.setAlpha((int) (255 * (hi - lo)));
            c.drawRect(x0, r.top, x1, r.bottom, glintPaint);
            glintPaint.setShader(null);
        }
    }

    private float glintGate;

    // ---- the cascade, in the direction the sheet moves. Open: rows from the BOTTOM visible row upward (fade 0→1,
    // scale 0.85→1, 7dp rise, 220 ms ease-out), ≤ 4 ms left→right inside a row, the whole cascade ≤ 250 ms, starting
    // at ~35% open. Close: the reverse, rows from the TOP down, 90 ms each, ≤ 150 ms in all (fade, scale → 0.9,
    // 4dp down). Alpha via DST_IN rects in the content's existing layer (no per-item layers).
    private static final long CASCADE_ITEM_MS = 220, CASCADE_SPAN_MS = 250, CLOSE_ITEM_MS = 90, CLOSE_SPAN_MS = 150;
    private long cascadeAt, closeAt;
    private boolean cascadeFired, cascadeClosing;
    private int cascadeFirstRow, cascadeLastRow, cascadeMaskN;
    private float cascadeRowStagger, cascadeT, cascadeMin = 0.85f, cascadeDy;
    private final float[] cascadeRects = new float[5 * 64];
    private final Paint cascadePaint = new Paint();

    {cascadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));}

    private void cascadeBegin(int cols, float cell, float gridTop, float top, float bottom, float openMin, boolean overshoot) {
        cascadeOvershoot = overshoot;
        cascadeMaskN = 0;
        cascadeFirstRow = (int) Math.max(0, Math.floor((top + sheetScroll - gridTop) / cell));
        cascadeLastRow = (int) Math.max(cascadeFirstRow, Math.floor((bottom + sheetScroll - gridTop) / cell));
        final int rows = cascadeLastRow - cascadeFirstRow + 1;
        cascadeClosing = sheetTarget < 0.5f;
        if (cascadeClosing) {
            cascadeT = AnimationUtils.currentAnimationTimeMillis() - closeAt;
            cascadeRowStagger = Math.min(25f, (CLOSE_SPAN_MS - CLOSE_ITEM_MS) / (float) Math.max(1, rows - 1));
            cascadeMin = 0.6f;
            cascadeDy = dp(4);
        } else {
            // opening, not yet uncovered: everything waits at the start of the cascade
            cascadeT = !cascadeFired ? 0f : AnimationUtils.currentAnimationTimeMillis() - cascadeAt;
            if (cascadeT > CASCADE_SPAN_MS + CASCADE_ITEM_MS) {
                cascadeT = -1;
                return;
            }
            cascadeRowStagger = Math.min(45f, (CASCADE_SPAN_MS - 4f * cols) / (float) Math.max(1, rows));
            cascadeMin = openMin;
            cascadeDy = dp(7);
        }
        invalidate();
    }

    /** the scale of the item last passed to cascadeE (the motion is carried by scale; alpha leads) */
    private float cascadeScale = 1f;
    private boolean cascadeOvershoot = true;

    /** Item progress for (row, col): returns its alpha; sets cascadeScale. 1 / 1 = fully shown. */
    private float cascadeE(int row, int col) {
        cascadeScale = 1f;
        if (cascadeT < 0) return 1f;
        if (cascadeClosing) {
            final float d = Math.max(0, row - cascadeFirstRow) * cascadeRowStagger + col * 3f;
            final float u = Utilities.clamp01((cascadeT - d) / CLOSE_ITEM_MS);
            cascadeScale = lerp(1f, cascadeMin, u * u); // ease-in
            return u < 0.7f ? 1f : 1f - (u - 0.7f) / 0.3f; // alpha only at the very end
        }
        final float d = Math.max(0, cascadeLastRow - row) * cascadeRowStagger + col * 4f;
        final float u = Utilities.clamp01((cascadeT - d) / CASCADE_ITEM_MS);
        cascadeScale = lerp(cascadeMin, 1f, cascadeOvershoot ? easeOutBack(u, 1.2f) : 1f - (1f - u) * (1f - u) * (1f - u));
        final float ua = Utilities.clamp01(u / 0.35f); // alpha is there in the first 35%
        return 1f - (1f - ua) * (1f - ua);
    }

    /** soft ease-out with a small overshoot (c1 1.2: ~+4% of the travel at the peak) */
    static float easeOutBack(float u, float c1) {
        final float c3 = c1 + 1f, v = u - 1f;
        return 1f + c3 * v * v * v + c1 * v * v;
    }

    private void cascadeMask(float l, float t, float r, float b, float e) {
        if (cascadeMaskN >= 64) return;
        final int o = cascadeMaskN++ * 5;
        cascadeRects[o] = l;
        cascadeRects[o + 1] = t;
        cascadeRects[o + 2] = r;
        cascadeRects[o + 3] = b;
        cascadeRects[o + 4] = e;
    }

    private void cascadeApply(Canvas c) {
        for (int i = 0; i < cascadeMaskN; i++) {
            final int o = i * 5;
            cascadePaint.setAlpha((int) (255 * cascadeRects[o + 4]));
            c.drawRect(cascadeRects[o], cascadeRects[o + 1], cascadeRects[o + 2], cascadeRects[o + 3], cascadePaint);
        }
        cascadeMaskN = 0;
    }

    /**
     * For the emoji keyboard (a row reveal driven by the same clock): open → progress 0…1 of the bottom-up reveal;
     * close → −(0…1) of the top-down hide. 1 = nothing running.
     */
    /** The sheet's openness 0..1 (the morph progress, overshoot clamped), and whether it is opening. */
    public float sheetProgress() {return Utilities.clamp01(sheet);}

    public boolean sheetOpening() {return sheetTarget > 0.5f;}

    public float cascadeProgress() {
        if (sheetTarget < 0.5f)
            return -Utilities.clamp01((AnimationUtils.currentAnimationTimeMillis() - closeAt) / (float) CLOSE_SPAN_MS) - 0.0001f;
        if (!cascadeFired) return 0f;
        final float t = AnimationUtils.currentAnimationTimeMillis() - cascadeAt;
        return Utilities.clamp01(t / (float) (CASCADE_SPAN_MS + CASCADE_ITEM_MS));
    }

    // ---- the open sheet's lift: its blurred backdrop a bit brighter + more contrast (only inside the sheet),
    // animating in with the sheet (sheetLift = its openness); a 1/4-res copy of the shared blur with a colour matrix
    private float sheetLift;
    private boolean liftThis; // set while the sheet's own glass draws
    private Object liftNode;
    private float liftBuiltK = -1f;
    private int liftBuiltVersion = -1, liftBuiltScene;

    /**
     * The sheet's own blur: the backdrop at 1/8 size, blurred with a radius that ramps from the buttons' (Glass blur)
     * to Sheet blur (≈2×) as the sheet opens, then the brightness/contrast lift. 1/8 keeps the bigger radius cheap.
     */
    private static final int LIFT_DS = 8;

    @androidx.annotation.RequiresApi(31)
    private Object liftedBlur() {
        if (liftNode == null) liftNode = QrRender.createNode("qrSheetLift");
        final float k = Math.round(sheetLift * 50f) / 50f; // effect rebuilt only on visible steps
        final int sc = scene != null ? scene.center : 0;
        if (k != liftBuiltK || liftBuiltVersion != RimTune.version || liftBuiltScene != sc) {
            liftBuiltK = k;
            liftBuiltVersion = RimTune.version;
            liftBuiltScene = sc;
            setLiftEffect(liftNode, k);
        }
        final int w = getWidth(), h = getHeight();
        final Canvas rc = QrRender.beginRecording(liftNode, (w + LIFT_DS - 1) / LIFT_DS, (h + LIFT_DS - 1) / LIFT_DS);
        rc.scale(1f / LIFT_DS, 1f / LIFT_DS);
        QrRender.draw(rc, bgNode);
        QrRender.endRecording(liftNode);
        return liftNode;
    }

    private final android.util.SparseIntArray islandFlat = new android.util.SparseIntArray();
    private int islandFlatVersion = -1;

    /** The packs island's flat colour for the current backdrop (cached per backdrop centre + tune version). */
    int islandFlatColor() {
        if (islandFlatVersion != RimTune.version) {
            islandFlat.clear();
            islandFlatVersion = RimTune.version;
        }
        final int key = scene != null ? scene.center : 0;
        final int i = islandFlat.indexOfKey(key);
        if (i >= 0) return islandFlat.valueAt(i);
        final int v = computeIslandFlat(scene);
        islandFlat.put(key, v);
        return v;
    }

    /** All 80 backdrops at once (cheap arithmetic), so no backdrop pays for it at the moment the layer opens. */
    void prewarmIslandColors() {
        if (islandFlatVersion != RimTune.version) {
            islandFlat.clear();
            islandFlatVersion = RimTune.version;
        }
        for (int i = 0; i < Backdrops.CENTER.length; i++) {
            final Scene sc = new Scene();
            sc.center = Backdrops.CENTER[i] | 0xFF000000;
            sc.edge = Backdrops.EDGE[i] | 0xFF000000;
            sc.shade = shadeFor(sc.center);
            if (islandFlat.indexOfKey(sc.center) < 0) islandFlat.put(sc.center, computeIslandFlat(sc));
        }
    }

    private int computeIslandFlat(Scene sc) {
        // the backdrop under the island: halfway between the scene's centre and edge colours
        final int bg = sc != null ? ColorUtils.blendARGB(sc.center, sc.edge, 0.5f) : 0xFF000000;
        final double lum = sc != null ? ColorUtils.calculateLuminance(sc.center) : 0.3;
        final float bright = RimTune.v[RimTune.SH_BRIGHT] / 100f * (float) Math.max(0.0, Math.min(1.0, (0.85 - lum) / 0.45));
        final float con = 1f + RimTune.v[RimTune.SH_CONTRAST] / 100f;
        final float off = 255f * (0.5f - 0.5f * con + bright);
        int col = Color.rgb(
            Math.max(0, Math.min(255, Math.round(con * Color.red(bg) + off))),
            Math.max(0, Math.min(255, Math.round(con * Color.green(bg) + off))),
            Math.max(0, Math.min(255, Math.round(con * Color.blue(bg) + off))));
        col = ColorUtils.compositeColors(argb((RimTune.v[RimTune.FILL] + RimTune.v[RimTune.SH_FILL]) / 100f, 0xFFFFFF), col);
        col = ColorUtils.compositeColors(sc != null ? sc.shade : 0x40000000, col);
        return col;
    }

    @androidx.annotation.RequiresApi(31)
    private void setLiftEffect(Object node, float k) {
        {
            // very light backdrops: less brightness (no wash-out), contrast kept
            final double lum = scene != null ? ColorUtils.calculateLuminance(scene.center) : 0.3;
            final float bright = RimTune.v[RimTune.SH_BRIGHT] / 100f * k * (float) Math.max(0.0, Math.min(1.0, (0.85 - lum) / 0.45));
            final float con = 1f + RimTune.v[RimTune.SH_CONTRAST] / 100f * k;
            final float off = 255f * (0.5f - 0.5f * con + bright);
            final float br = Math.max(0.5f, dpf2(lerp(RimTune.v[RimTune.BLUR], RimTune.v[RimTune.SH_BLUR], k)) / LIFT_DS);
            QrRender.setBlurAndColorMatrix(node, br, new float[]{
                con, 0, 0, 0, off,
                0, con, 0, 0, off,
                0, 0, con, 0, off,
                0, 0, 0, 1, 0});
        }
    }

    // glass shaders in unit space, placed with a matrix (no per-frame allocation)
    private boolean glassButton, glassSmall, glassSheet;

    // ---- rim shaders built from RimTune + the backdrop-tinted dark (rebuilt only when either changes)
    private int rimBuiltVersion = -1;

    private void ensureRims() {
        if (rimBuiltVersion == RimTune.version) return;
        rimBuiltVersion = RimTune.version;
        // Dark out (Glass Lab): a(θ) = tb + (dA − tb)·|cos θ|^exp, tb = dTop on the top half, dBot on the bottom half;
        // 73 stops (every 5°, clockwise from 3 o'clock). Multiply by grey (1 − a) == black at alpha a (layer-safe).
        final int no = 73;
        for (int k = 0; k < RimTune.NG; k++) {
            final float[] gk = RimTune.g[k];
            final int[] cols = new int[no];
            final float[] pos = new float[no];
            for (int i = 0; i < no; i++) {
                final double th = Math.toRadians(i * 5);
                final float side = (float) Math.pow(Math.abs(Math.cos(th)), gk[RimTune.D_EXP]);
                final float sn = (float) Math.sin(th); // + = bottom
                final float tb = (sn < 0 ? gk[RimTune.D_TOP] : gk[RimTune.D_BOT]) / 100f;
                final float a = Math.min(1f, tb + (gk[RimTune.D_A] / 100f - tb) * side);
                cols[i] = argb(a, 0); // Multiply as black at alpha a
                pos[i] = i / (float) (no - 1);
            }
            rimOuterSweeps[k] = new SweepGradient(0, 0, cols, pos);
        }
    }

    private final SweepGradient[] rimOuterSweeps = new SweepGradient[RimTune.NG];

    // ---- tilt (light axis rotation / top-bottom balance), −1…1; 0 = upright. As in Glass Lab: the gravity sensor,
    // relative to a baseline that follows slowly (4 s), ±30° = full, smoothed (80 ms); quantised so cached rim paths
    // are rebuilt only on visible steps
    private float tiltX, tiltY, rawTiltX, rawTiltY;
    private float gx, gy, gz = 9.81f, bx, by, bz = 9.81f;
    private boolean haveG;
    private long lastTiltNs;
    private android.hardware.SensorManager sensors;
    private final android.hardware.SensorEventListener gravityListener = new android.hardware.SensorEventListener() {
        @Override
        public void onSensorChanged(android.hardware.SensorEvent e) {
            gx = e.values[0];
            gy = e.values[1];
            gz = e.values[2];
            if (!haveG) {
                bx = gx;
                by = gy;
                bz = gz;
                haveG = true;
            }
        }

        @Override
        public void onAccuracyChanged(android.hardware.Sensor sensor, int accuracy) {}
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        prepareCardShadow(); // ready before the open's first frame
        prewarmIslandColors(); // the packs island colours for all the backdrops, ahead of any open
        org.telegram.messenger.NotificationCenter.getInstance(currentAccount).addObserver(giftsObserver, org.telegram.messenger.NotificationCenter.starUserGiftsLoaded);
        loadGifts(); // pages that came while detached
        sensors = (android.hardware.SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        final android.hardware.Sensor g = sensors != null ? sensors.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY) : null;
        if (g != null) sensors.registerListener(gravityListener, g, android.hardware.SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        org.telegram.messenger.NotificationCenter.getInstance(currentAccount).removeObserver(giftsObserver, org.telegram.messenger.NotificationCenter.starUserGiftsLoaded);
        removeCallbacks(prewarmGiftsRunnable);
        if (sensors != null) sensors.unregisterListener(gravityListener);
        haveG = false;
        // detached mid-press (no UP / CANCEL): no wave may wait for the next attach
        pressedRipple = -1;
        java.util.Arrays.fill(releaseT, 0);
    }

    // ---- the shine follows the tilt (holographic card): while the phone moves, the band's position along its axis is
    // the tilt (± Shine sensitivity ° = the whole screen), smoothed 100 ms, at full light; still for 1.5 s → it fades
    // out (0.6 s) and the slow auto sweep returns; the next tilt fades it back in where the tilt puts it
    private float tsC, tsEnv, tsLastX, tsLastY;
    private long tsMovedAt, tsLastNs;
    private boolean tsHave;

    private void stepTiltShine(int w, int h) {
        final long ns = System.nanoTime();
        final float dt = tsLastNs == 0 ? 0.016f : Math.min(0.1f, (ns - tsLastNs) / 1e9f);
        tsLastNs = ns;
        if (RimTune.v[RimTune.SHINE_TILT] < 0.5f || reveal < 1f) {
            tsEnv = 0f;
            return;
        } // not while it opens / closes
        final float k = 30f / Math.max(1f, RimTune.v[RimTune.SHINE_SENS]);
        final float tx = Math.max(-1f, Math.min(1f, rawTiltX * k));
        final float ty = Math.max(-1f, Math.min(1f, rawTiltY * k));
        if (!tsHave) {
            tsLastX = tx;
            tsLastY = ty;
            tsHave = true;
        }
        final float vel = (float) Math.hypot(tx - tsLastX, ty - tsLastY) / dt; // full-scale units per second
        tsLastX = tx;
        tsLastY = ty;
        final long now = AnimationUtils.currentAnimationTimeMillis();
        if (vel > 0.15f) tsMovedAt = now; // ≈ 4.5°/s at the default sensitivity
        final boolean active = now - tsMovedAt < 1500;
        final float ext = (Math.abs(sweepDX) * w + Math.abs(sweepDY) * h) / 2f + shineW;
        // the band crosses the screen along the sweep axis as the phone tilts across it
        final float target = (tx * -sweepDX + ty * -sweepDY) * ext;
        if (tsEnv <= 0.001f) tsC = target; // fading back in: start where the tilt is (no jump)
        tsC += (target - tsC) * (1f - (float) Math.exp(-dt / 0.1f));
        tsEnv = active ? Math.min(1f, tsEnv + dt / 0.2f) : Math.max(0f, tsEnv - dt / 0.6f);
        if (tsEnv > 0.001f) {
            final float te = tsEnv * tsEnv * (3f - 2f * tsEnv);
            if (shining) {
                // the auto sweep is on screen: hand the band over smoothly (no cut of a lit band in one frame)
                shineC = lerp(shineC, tsC, te);
                shineEnv = lerp(shineEnv, 1f, te);
            } else {
                shineC = tsC;
                shineEnv = te;
            }
            shining = true;
            if (tsEnv >= 0.999f) lightSweepStart = fieldT; // the auto sweep waits while the tilt drives the light
        }
    }

    private void stepTilt() {
        final long now = System.nanoTime();
        final float dt = lastTiltNs == 0 ? 0.016f : Math.min(0.1f, (now - lastTiltNs) / 1e9f);
        lastTiltNs = now;
        float tx = 0, ty = 0;
        if (haveG) {
            final float kb = 1f - (float) Math.exp(-dt / 4f);
            bx += (gx - bx) * kb;
            by += (gy - by) * kb;
            bz += (gz - bz) * kb;
            tx = Math.max(-1f, Math.min(1f, -(gx - bx) / 4.9f));
            ty = Math.max(-1f, Math.min(1f, (gy - by) / 4.9f));
        }
        final float k = 1f - (float) Math.exp(-dt / 0.08f);
        rawTiltX += (tx - rawTiltX) * k;
        rawTiltY += (ty - rawTiltY) * k;
        // 0.5° rotation / 0.5% balance steps
        final float degStep = RimTune.v[RimTune.TILT_DEG] > 0 ? 0.5f / RimTune.v[RimTune.TILT_DEG] : 1f;
        tiltX = Math.round(rawTiltX / degStep) * degStep;
        tiltY = Math.round(rawTiltY * 40f) / 40f;
        RimPainter.tiltRot = tiltX * RimTune.v[RimTune.TILT_DEG];
        RimPainter.tiltBal = tiltY * RimTune.v[RimTune.TILT_BAL] / 100f;
    }

    private final Matrix rimTmpM = new Matrix();
    private final Path rimGrown = new Path();
    private final RectF rect4 = new RectF();

    /**
     * Dark out (ported from Glass Lab): white always inside, dark always outside, both blended over the pixels under
     * them. White: the shape minus itself shifted along the (tilted) light axis, an opaque grey of the strength drawn
     * with the white blend (Plus). Dark: a ring just outside the shape, sweep a(θ), Multiply (= black at alpha a).
     * inset < 0 grows the rim geometry. shape == null → a round rect (r, radius).
     */
    private void drawDarkOut(Canvas c, Path shape, RectF r, float radius, float alpha) {
        if (shape == null && QrGlass.drawRim(c, r, radius, rimGroup(), alpha)) return; // baked (see QrGlass)
        ensureRims();
        final int grp = rimGroup();
        final float[] gk = RimTune.g[grp];
        final float rot = tiltX * RimTune.v[RimTune.TILT_DEG];
        final float bal = tiltY * RimTune.v[RimTune.TILT_BAL] / 100f;
        final float inset = dpf2(gk[RimTune.INSET]);
        Path sh = shape;
        if (inset != 0f) {
            rect3.set(r);
            rect3.inset(inset, inset);
            if (shape != null) {
                rimTmpM.setScale(rect3.width() / Math.max(1f, r.width()), rect3.height() / Math.max(1f, r.height()), r.centerX(), r.centerY());
                shape.transform(rimTmpM, rimGrown);
                sh = rimGrown;
            }
            r = rect3;
            radius = Math.max(0f, radius - inset);
        }
        glassMatrix.setRotate(rot);
        glassMatrix.postScale(r.width() / Math.max(1f, r.height()), 1f);
        glassMatrix.postTranslate(r.centerX(), r.centerY());
        // 1) the dark ring outside
        final float dw = dpf2(gk[RimTune.D_W]);
        if (dw > 0.05f) {
            final SweepGradient ds = rimOuterSweeps[grp];
            ds.setLocalMatrix(glassMatrix);
            stroke.setShader(ds);
            stroke.setColor(Color.WHITE);
            stroke.setAlpha((int) (255 * alpha));
            if (sh == null) {
                stroke.setStrokeWidth(dw);
                rect2.set(r);
                rect2.inset(-dw / 2f, -dw / 2f);
                c.drawRoundRect(rect2, radius + dw / 2f, radius + dw / 2f, stroke);
            } else {
                c.save();
                QrRender.clipOut(c, sh);
                stroke.setStrokeWidth(dw * 2f);
                c.drawPath(sh, stroke);
                c.restore();
            }
            stroke.setShader(null);
            stroke.setAlpha(255);
        }
        // 2) the white crescents inside
        final double phi = Math.toRadians(rot);
        final float sx = (float) -Math.sin(phi), sy = (float) Math.cos(phi);
        final float tTop = dpf2(gk[RimTune.T_TOP]), tBot = dpf2(gk[RimTune.T_BOT]);
        // the pattern's light band glints on the rim too: the very same field, this frame, at the crescent's place
        final float gl = glassSheet ? 0f : 1.25f * RimTune.v[RimTune.GLINT] / 100f * glintGate; // ×1.25 gain
        final float bTop = gl > 0f && shining ? shine(r.centerX(), r.top, getWidth(), getHeight()) : 0f;
        final float bBot = gl > 0f && shining ? shine(r.centerX(), r.bottom, getWidth(), getHeight()) : 0f;
        final float aTop = Math.min(1f, gk[RimTune.A_TOP] / 100f * (1f + bal) * (1f + gl * bTop)), aBot = Math.min(1f, gk[RimTune.A_BOT] / 100f * (1f - bal) * (1f + gl * bBot));
        // the white crescents add light (Plus) over what is beneath; Android 6–9 have no Plus: plain alpha
        final boolean plus = QrRender.hasNodes();
        for (int pass = 0; pass < 2; pass++) {
            final float off = pass == 0 ? tTop : -tBot, a = pass == 0 ? aTop : aBot;
            if (a <= 0.004f || Math.abs(off) < 0.1f) continue;
            // the crescent: the shape drawn with its shifted copy clipped out. No Path.op: on some devices it returned
            // a wrong shape (the whole element) at some tilts, a flash of the button
            final Path cp;
            c.save();
            if (sh == null) {
                final float rr = Math.min(radius, Math.min(r.width(), r.height()) / 2f);
                crescentA.rewind();
                crescentA.addRoundRect(r, rr, rr, Path.Direction.CW);
                rect4.set(r);
                rect4.offset(sx * off, sy * off);
                crescentB.rewind();
                crescentB.addRoundRect(rect4, rr, rr, Path.Direction.CW);
            } else {
                crescentA.set(sh);
                crescentB.set(sh);
                crescentB.offset(sx * off, sy * off);
            }
            QrRender.clipOut(c, crescentB);
            cp = crescentA;
            if (!plus) {
                fill.setColor(rimWhite(grp));
                fill.setAlpha((int) (255 * a * alpha));
            } else {
                // opaque grey whose level is the strength (grey "identity" = no change); alpha fades it linearly
                fill.setColor(gray(a));
                fill.setAlpha((int) (255 * alpha));
                QrRender.setBlendMode(fill, QrRender.BLEND_PLUS);
            }
            c.drawPath(cp, fill);
            if (plus) QrRender.setBlendMode(fill, QrRender.BLEND_NONE);
            c.restore();
        }
    }

    private final Path crescentA = new Path(), crescentB = new Path();

    private static int gray(float g) {
        final int v = Math.round(255 * Math.max(0f, Math.min(1f, g)));
        return 0xFF000000 | v << 16 | v << 8 | v;
    }

    private int rimLightColor = 0xFFFFFFFF;

    /** The crescents' white, tinted toward the backdrop by the group's strength (0 white, 1 formula, 1.5 stronger). */
    private int rimWhite(int grp) {
        final float k = RimTune.g[grp][RimTune.W_TINT];
        final int t = rimLightColor;
        final int r = Math.max(0, Math.min(255, Math.round(255 + (Color.red(t) - 255) * k)));
        final int gg = Math.max(0, Math.min(255, Math.round(255 + (Color.green(t) - 255) * k)));
        final int b = Math.max(0, Math.min(255, Math.round(255 + (Color.blue(t) - 255) * k)));
        return Color.rgb(r, gg, b);
    }

    /** OKLCH(0.96, min(0.06, C·0.7), edge hue); near-neutral edges → white. */
    static int rimLightFor(int edge) {
        final double[] e = new double[3], rgb = new double[3];
        toOkLab(edge, e);
        final double c0 = Math.hypot(e[1], e[2]);
        if (c0 < 0.03) return 0xFFFFFFFF;
        fitChroma(0.96, Math.min(0.06, c0 * 0.7), Math.atan2(e[2], e[1]), rgb);
        return Color.rgb((int) Math.round(clamp01(gam(clamp01(rgb[0]))) * 255), (int) Math.round(clamp01(gam(clamp01(rgb[1]))) * 255), (int) Math.round(clamp01(gam(clamp01(rgb[2]))) * 255));
    }

    private int rimGroup() {return glassSmall ? RimTune.SMALL : glassSheet ? RimTune.SHEET : RimTune.BIG;}

    private static int argb(float a, int rgb) {return ((int) (255 * Math.max(0f, Math.min(1f, a))) << 24) | (rgb & 0xFFFFFF);}

    /** White centred at the top / bottom (± whiteHalf), dark at the sides (± darkHalf), transparent gaps between. */
    private final RectF rect3 = new RectF();

    private final Matrix glassMatrix = new Matrix();
    private final android.util.SparseArray<PorterDuffColorFilter> tints = new android.util.SparseArray<>();

    /** Cached SRC_IN tints: the QR colour only changes during a wave, so the cache stays small. */
    private PorterDuffColorFilter tint(int color) {
        PorterDuffColorFilter f = tints.get(color);
        if (f == null) {
            if (tints.size() > 96) tints.clear();
            f = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
            tints.put(color, f);
        }
        return f;
    }

    private int glassIconColor() {
        return scene != null && scene.light ? 0xFF1A1D21 : Color.WHITE;
    }

    private void icon(Canvas c, Drawable d, float cx, float cy, float size, int color, float alpha) {
        d.setColorFilter(tint(color));
        d.setAlpha((int) (255 * alpha));
        d.setBounds((int) (cx - size / 2), (int) (cy - size / 2), (int) (cx + size / 2), (int) (cy + size / 2));
        d.draw(c);
    }

    private Drawable[] tabIcons;
    private final RectF closeRect = new RectF(), shareRect = new RectF(), saveRect = new RectF(), scanRect = new RectF(), tabsRect = new RectF();
    // the scanner grows out of the scan button and takes its place while it is open
    private boolean scanHidden;

    /** The scan button in view coordinates. */
    public void getScanRect(RectF out) {
        out.set(scanRect);
    }

    /** The close button in view coordinates. */
    public void getCloseRect(RectF out) {
        out.set(closeRect);
    }

    public void setScanHidden(boolean hidden) {
        if (scanHidden != hidden) {
            scanHidden = hidden;
            invalidate();
        }
    }

    private void drawTopBar(Canvas c, boolean glass) {
        // round buttons are 40dp; centred where the profile's glass back button sits (29dp in, middle of the action bar)
        final float cy = insetTop + ActionBar.getCurrentActionBarHeight() / 2f;
        closeRect.set(dp(29) - dp(20), cy - dp(20), dp(29) + dp(20), cy + dp(20));
        final float es = elemScale(uiK);
        c.save();
        c.scale(es, es, closeRect.centerX(), closeRect.centerY());
        glassButton = true;
        drawGlass(c, closeRect, dp(20), scene != null && scene.light, 1f);
        glassButton = false;
        drawRipple(c, RP_CLOSE, closeRect, scene != null && scene.light);
        icClose.setColor(glassIconColor());
        c.save();
        c.translate(closeRect.centerX() - icClose.getIntrinsicWidth() / 2f, cy - icClose.getIntrinsicHeight() / 2f);
        icClose.draw(c);
        c.restore();
        c.restore();
        // "Scan QR" pill on the right (mirrors the close button's inset), same glass; the scanner grows out of it
        if (!scanHidden) {
            final boolean light = scene != null && scene.light;
            scanText.setTextSize(dp(14)); // Roboto Medium 14
            final float pw = dp(12) + dp(20) + dp(6) + scanText.measureText(scanLabel) + dp(14);
            scanRect.set(getWidth() - dp(9) - pw, cy - dp(20), getWidth() - dp(9), cy + dp(20));
            c.save();
            c.scale(es, es, scanRect.centerX(), scanRect.centerY());
            glassButton = true;
            drawGlass(c, scanRect, dp(20), light, 1f);
            glassButton = false;
            drawRipple(c, RP_SCAN, scanRect, light);
            icon(c, icScan, scanRect.left + dp(12) + dp(10), cy, dp(20), glassIconColor(), 1f);
            scanText.setColor(glassIconColor());
            final Paint.FontMetrics fm = scanText.getFontMetrics();
            c.drawText(scanLabel, scanRect.left + dp(12) + dp(20) + dp(6), cy - (fm.ascent + fm.descent) / 2f, scanText);
            c.restore();
        }
    }

    private boolean barHidden;
    private float barK = 1f, barFrom = 1f;
    private long barAt;
    // the share button has its own collapse: also hidden while the free code runs on a timer (a code valid for a
    // minute is not for sharing); the theme island stays then
    private boolean shareHidden;
    private float shareK = 1f, shareFrom = 1f;
    private long shareAt;

    private void drawBottomBar(Canvas c, boolean glass) {
        final float sa = 1f - Utilities.clamp01(sheet * 2.2f);
        // the fee menu hides the bar: each control collapses about its own centre (scale → 0.6, opacity mostly in the
        // second half, 220 ms ease-in; showing = the mirror with a soft ≤3% overshoot). No vertical motion. The QR
        // (scan) button stays.
        final long bnow = AnimationUtils.currentAnimationTimeMillis();
        // collapsed while the fee menu is open — same frame as the change
        final boolean hide = menuTarget > 0.5f;
        if (hide != barHidden) {
            barHidden = hide;
            barFrom = barK;
            barAt = bnow;
        }
        final float bu = Utilities.clamp01((bnow - barAt) / 220f);
        barK = hide ? lerp(barFrom, 0f, bu * bu) : lerp(barFrom, 1f, Math.min(1f, easeOutBack(bu, 0.6f)));
        if (bu < 1f) invalidate();
        final float ma = Utilities.clamp01(Utilities.clamp01(barK) * 2f); // opacity in the second half of the hide / first half of the show
        final float mscale = lerp(0.6f, 1f, barK);
        // share: the same collapse, also while the free code runs on a timer; free without a time limit (or the fee
        // back) shows it again
        final boolean hideShare = hide || (free && timerOn);
        if (hideShare != shareHidden) {
            shareHidden = hideShare;
            shareFrom = shareK;
            shareAt = bnow;
        }
        final float su = Utilities.clamp01((bnow - shareAt) / 220f);
        shareK = hideShare ? lerp(shareFrom, 0f, su * su) : lerp(shareFrom, 1f, Math.min(1f, easeOutBack(su, 0.6f)));
        if (su < 1f) invalidate();
        final float shareScale = lerp(0.6f, 1f, shareK);
        final float a = Math.min(sa, Utilities.clamp01(Utilities.clamp01(shareK) * 2f));
        final boolean island = sheet <= 0.001f;
        if (a <= 0 && sa <= 0 && !island) return;
        final float cy = barCY();
        final int w = getWidth();
        final boolean light = scene != null && scene.light;
        // exact circles: dp(62) − dp(22) is not 2·dp(20) at every density, and a round rect a pixel narrower than its
        // radius is not a circle (its rim crescents came out wrong at some tilts, a flash of the whole button)
        final int br = dp(20);
        shareRect.set(dp(42) - br, cy - br, dp(42) + br, cy + br);
        saveRect.set(w - dp(42) - br, cy - br, w - dp(42) + br, cy + br);
        tabsRect.set(w / 2f - dp(72), cy - dp(20), w / 2f + dp(72), cy + dp(20));
        final float bs = lerp(0.6f, 1f, sa) * elemScale(uiK); // side buttons scale out as the island expands (and in with the open)
        if (a > 0) {
            c.save();
            c.scale(bs * shareScale, bs * shareScale, shareRect.centerX(), cy);
            glassButton = true;
            drawGlass(c, shareRect, dp(20), light, a);
            glassButton = false;
            drawRipple(c, RP_SHARE, shareRect, light);
            icon(c, icShare, shareRect.centerX(), cy, dp(22), glassIconColor(), a);
            c.restore();
            // download: the QR to the gallery (same glass, size and ripple as share; collapses with the bar)
            c.save();
            c.scale(bs * mscale, bs * mscale, saveRect.centerX(), cy);
            glassButton = true;
            drawGlass(c, saveRect, dp(20), light, a);
            glassButton = false;
            drawRipple(c, RP_SAVE, saveRect, light);
            icon(c, icDownload, saveRect.centerX(), cy, dp(24), glassIconColor(), a);
            c.restore();
        }
        if (!island || ma <= 0 || !themeEditable) return;
        final float ts = elemScale(uiK) * mscale;
        c.save();
        c.scale(ts, ts, tabsRect.centerX(), tabsRect.centerY());
        glassButton = true;
        drawGlass(c, tabsRect, dp(20), light, ma);
        glassButton = false;
        final float seg = tabsRect.width() / 3f;
        c.save();
        path.rewind();
        path.addRoundRect(tabsRect, dp(20), dp(20), Path.Direction.CW);
        c.clipPath(path);
        for (int i = 0; i < 3; i++) {
            rect3.set(tabsRect.left + seg * i, tabsRect.top, tabsRect.left + seg * (i + 1), tabsRect.bottom);
            drawRipple(c, RP_TAB + i, rect3, light);
        }
        c.restore();
        final Drawable[] icons = tabIcons;
        for (int i = 0; i < 3; i++) {
            icon(c, icons[i], tabsRect.left + seg * (i + 0.5f), cy, dp(i == 1 ? 24.5f : 22), glassIconColor(), ma); // the smile glyph is optically smaller
        }
        c.restore();
    }

    // --- sheet

    private final RectF sheetRect = new RectF();

    // the island ↔ sheet morph: opens with a spring (~400 ms, slight overshoot); closing fades the content first
    private long sheetAnimStart;
    private float sheetAnimFrom, lastSheetTarget, contentA;
    private final RectF islandRect = new RectF();

    private static final CubicBezierInterpolator SHEET_EASE = new CubicBezierInterpolator(0.16, 1, 0.3, 1);

    private void stepSheet() {
        final long now = AnimationUtils.currentAnimationTimeMillis();
        if (!scroller.isFinished() && scroller.computeScrollOffset())
            sheetScroll = Math.max(0, Math.min(maxScroll(), scroller.getCurrY()));
        if (sheetTarget != lastSheetTarget) {
            if (sheetTarget < 0.5f) closeAt = now; // the closing cascade starts with the close
            else if (sheet < 0.35f) cascadeFired = false; // reopened early: cascade again from the bottom
            if (sheetTarget > 0.5f) revealOpenAt = now;
            lastSheetTarget = sheetTarget;
            sheetAnimFrom = sheet;
            sheetAnimStart = now;
        }
        final float t = (now - sheetAnimStart) / 1000f;
        if (sheetTarget > 0.5f) {
            // fast start, long soft settle, no bounce: expo-out cubic-bezier(0.16, 1, 0.3, 1) over 420 ms (~60% of the
            // travel in the first ~110 ms); everything riding the sheet (shape, rim, blur lift, content) follows it.
            // (was: a spring ζ 0.75 / 0.30 s with ~2.8% overshoot, cut to 1 at 0.7 s)
            final float u = Utilities.clamp01(t / 0.42f);
            sheet = lerp(sheetAnimFrom, 1f, SHEET_EASE.getInterpolation(u));
            contentA = Utilities.clamp01(Utilities.clamp01(sheet) / 0.8f); // with the morph, clipped to it
        } else {
            // closing: the same curve family, a bit shorter (360 ms ≈ 85%); the content fades out over the first 0.22 s
            // (was: ease-in-out-quad over 0.22 s)
            // content out faster with no long faint tail: 170 ms, ease-in (1 − u)^1.7 (was linear over 220 ms) — the
            // colours' fade and shrink (both driven by contentA) are done ~60 ms sooner
            final float uc = Utilities.clamp01(t / 0.17f);
            contentA = Math.min(contentA, (float) Math.pow(1f - uc, 1.7f));
            final float u = Utilities.clamp01(t / 0.36f);
            sheet = lerp(sheetAnimFrom, 0f, SHEET_EASE.getInterpolation(u));
        }
    }

    /** The floating sheet: inset on all sides, every corner rounded. */
    /** Grid top padding inside the sheet: colours = the side gap (dp 4 + (cell − ball)/2) minus the cell's own margin; gifts = header gap. */
    private float gridPad() {return dp(4);}

    private float sheetBottom() {return getHeight() - insetBottom - dp(10);}

    static final int SHEET_INSET = 10, CONTENT_PAD = 16;
    // concentric with the emoji layer's packs island: its radius (101 px / 2 = 16.83 dp) + its gap to the sheet
    // edge (8 + 2 dp + 4 px = 11.33 dp) = 28.17 dp (was 30)
    static final float SHEET_RADIUS = 28.17f;
    private final float GIFT_HEADER = 0; // no header: the first row's top gap equals the side gap
    // scroll fades: 12dp at the top and bottom of the sheet, DST_IN on the grid layer (prebuilt; matrix only)
    private final LinearGradient fadeTop = new LinearGradient(0, 0, 0, 1, new int[]{0x00000000, 0x30000000, 0x9F000000, 0xEF000000, 0xFF000000}, new float[]{0f, 0.25f, 0.5f, 0.75f, 1f}, Shader.TileMode.CLAMP);
    private final Paint fadePaint = new Paint();

    {
        fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        fadePaint.setShader(fadeTop);
    }

    private final Matrix fadeMatrix = new Matrix();

    private void drawSheet(Canvas c, boolean glass, int w, int h) {
        if (sheet <= 0.001f) return;
        final float m = sheet, mc = Utilities.clamp01(m);
        final float sh = sheetH(), bottom = sheetBottom();
        final float top = bottom - sh;
        sheetRect.set(dp(SHEET_INSET), top, w - dp(SHEET_INSET), bottom);
        final boolean light = scene != null && scene.light;
        // the island itself grows into the floating sheet: one continuous glass surface
        final float icy = barCY();
        // the spring may overshoot: the shape grows slightly past its final top / edges and settles back
        final float minInset = dp(SHEET_INSET) - dp(3);
        islandRect.set(Math.max(minInset, lerp(w / 2f - dp(72), sheetRect.left, m)), lerp(icy - dp(20), top, m),
            Math.min(w - minInset, lerp(w / 2f + dp(72), sheetRect.right, m)), lerp(icy + dp(20), bottom, mc));
        final float rad = Math.min(islandRect.height() / 2f, lerp(dp(20), dp(SHEET_RADIUS), mc));
        glassSheet = mc > 0.5f; // the island grows into the sheet: the sheet rim once it is mostly open
        if (mc >= 0.35f && !cascadeFired && sheetTarget > 0.5f) {
            cascadeFired = true;
            cascadeAt = AnimationUtils.currentAnimationTimeMillis();
        }
        if (mc < 0.05f) cascadeFired = false;
        sheetLift = mc;
        liftThis = true;
        drawGlass(c, islandRect, rad, light && mc < 0.5f, 1f);
        liftThis = false;
        glassSheet = false;
        // a distinct, lighter layer: a light tint of the backdrop's own hue (+ the glass white) ~ +12%
        final int shade = scene != null ? scene.shade : 0x40000000;
        fill.setColor(shade);
        fill.setAlpha((int) (Color.alpha(shade) * mc));
        if (mc > 0.5f) {
            // over the same grown body as the glass (see drawGlass)
            final float gin = Math.min(0f, dpf2(RimTune.g[RimTune.SHEET][RimTune.INSET]));
            rect2.set(islandRect);
            rect2.inset(gin, gin);
            c.drawRoundRect(rect2, rad - gin, rad - gin, fill);
        } else {
            c.drawRoundRect(islandRect, rad, rad, fill);
        }
        // the island's icons stay put and fade as the island opens (no tabs inside the sheet)
        final float ia = 1f - Utilities.clamp01(mc / 0.35f);
        if (ia > 0f) {
            c.save();
            path.rewind();
            path.addRoundRect(islandRect, rad, rad, Path.Direction.CW);
            c.clipPath(path);
            final float iseg = dp(144) / 3f;
            for (int i = 0; i < 3; i++) {
                final float ix = w / 2f - dp(72) + iseg * (i + 0.5f);
                icon(c, tabIcons[i], ix, lerp(icy, icy - dp(10), mc), dp(i == 1 ? 24.5f : 22) * (1f + 0.15f * mc * (i == tab ? 1 : 0)), glassIconColor(), ia);
            }
            c.restore();
        }
        if (contentA <= 0.004f) return;
        // content fades and rises in with the sheet, clipped to the glass
        final boolean layer = contentA < 0.999f;
        if (layer) c.saveLayerAlpha(sheetRect.left, top, sheetRect.right, bottom, (int) (255 * contentA));
        c.save();
        path.rewind();
        path.addRoundRect(islandRect, rad, rad, Path.Direction.CW);
        c.clipPath(path);
        // the scrolling content is cut by the layer's own rounded shape, with 12dp fades at its top and bottom
        final boolean fades = tab != SCENE_EMOJI;
        if (fades) c.saveLayer(islandRect, null);
        c.save();
        c.translate(0, (1f - contentA) * dp(14));
        c.translate(0, -sheetScroll);
        final float gridTop = top + gridPad(); // the top gap equals the side gap
        if (tab == SCENE_COLOR) {
            final int cols = 6;
            final float cell = (sheetRect.width() - dp(8)) / cols, ball = Math.min(cell - dp(10), dp(44));
            // the whole grid at its final layout, revealed as one with the layer (its growing clip + the content fade
            // above): a uniform 0.96 → 1 scale on the same progress, the reverse on close — no per-circle stagger
            // (the same ranges timed from the open tap, 420 ms ease-out cubic)
            final boolean timed = sheetTarget > 0.5f;
            final float rt = timed ? revealTime() : 1f;
            // Time: like the emoji keyboard — the whole grid 0.8 → 1 from the island's bottom centre, a lighter own swatch grow
            final float gs = timed ? lerp(REVEAL_FROM_COLORS, 1f, 1f - (1f - rt) * (1f - rt) * (1f - rt)) : lerp(0.96f, 1f, contentA);
            if (timed && rt < 1f) invalidate();
            c.save();
            c.scale(gs, gs, sheetRect.centerX(), timed ? bottom + sheetScroll : top + sheetScroll + (bottom - top) / 2f);
            for (int slot = 0; slot < PRESET_COUNT + Backdrops.CENTER.length; slot++) {
                final int i = slotIndex(slot); // the swatch order (Backdrops.orderToIndex)
                final float x = sheetRect.left + dp(4) + cell * (slot % cols + 0.5f), y = gridTop + cell * (slot / cols + 0.5f);
                if (y - sheetScroll > h + cell || y - sheetScroll < top - cell) continue;
                // a subtle own scale on top: 0.85 → 1, ease-out, ~12 ms later per visible row, done with the morph
                final float row = Math.max(0f, Math.min(10f, (y - sheetScroll - top) / cell));
                final float lu = timed ? Utilities.clamp01((rt * REVEAL_MS - row * 12f) / (REVEAL_MS - 120f)) : Utilities.clamp01((contentA - row * 0.02f) / 0.8f);
                final float sMin = timed ? REVEAL_ITEM_FROM : 0.85f;
                final float ss = swatchScale(slot) * (sMin + (1f - sMin) * (1f - (1f - lu) * (1f - lu) * (1f - lu)));
                if (ss != 1f) {
                    c.save();
                    c.scale(ss, ss, x, y);
                }
                drawSphere(c, slot, x, y, ball / 2f, deepCenter(i), deepEdge(i), scene != null && scene.type == SCENE_COLOR && scene.index == i);
                if (ss != 1f) {
                    c.restore();
                    invalidate();
                }
            }
            c.restore();
        } else if (tab == SCENE_EMOJI) {
            // the real emoji keyboard (a child view) fills this area
        } else {
            // Time (opening): like the emoji keyboard — no row cascade, the whole layer content 0.75 → 1 from the
            // island's bottom centre, timed from the open tap (420 ms ease-out cubic)
            final boolean giftsTimed = sheetTarget > 0.5f;
            final float grt = giftsTimed ? revealTime() : 1f;
            final boolean giftsScaled = giftsTimed && grt < 1f;
            if (giftsScaled) {
                final float gsc = lerp(REVEAL_FROM_GIFTS, 1f, 1f - (1f - grt) * (1f - grt) * (1f - grt));
                c.save();
                c.scale(gsc, gsc, sheetRect.centerX(), bottom + sheetScroll);
                invalidate();
            }
            if (giftsEmpty()) {
                drawGiftsEmpty(c, top, mc);
            } else if (gifts.isEmpty()) {
                text.setColor(0xB3FFFFFF);
                text.setTextSize(dp(14));
                final String s = org.telegram.messenger.LocaleController.getString(!giftsEndReached ? R.string.QrLoadingGifts : giftsOwner < 0 ? R.string.QrChatNoCollectibles : R.string.QrNoCollectibles);
                c.drawText(s, (w - text.measureText(s)) / 2f, gridTop + dp(40), text);
            }
            final int cols = giftCols(), count = giftCount();
            final float cell = (sheetRect.width() - dp(8)) / 3f, cellW = (sheetRect.width() - dp(8)) / cols;
            cascadeBegin(cols, cell, gridTop + GIFT_HEADER, top, bottom, 0.7f, false);
            if (giftsTimed) cascadeT = -1; // Time: the uniform grow above replaces the opening cascade
            for (int i = 0; i < count; i++) {
                final float x = sheetRect.left + dp(4) + cellW * (i % cols), y = gridTop + GIFT_HEADER + cell * (i / cols);
                if (y - sheetScroll > bottom || y + cell - sheetScroll < top) continue;
                final float ce = cascadeE(i / cols, i % cols);
                final boolean cs = ce < 1f || cascadeScale != 1f;
                if (cs) {
                    c.save();
                    c.translate(0, cascadeDy * Utilities.clamp01((1f - cascadeScale) / (1f - cascadeMin)));
                    c.scale(cascadeScale, cascadeScale, x + cellW / 2f, y + cell / 2f);
                    if (ce < 1f) cascadeMask(x, y, x + cellW, y + cell, ce);
                }
                final float is = giftsScaled ? revealItemScale(grt, (y - sheetScroll - top) / cell) : 1f;
                if (is != 1f) {
                    c.save();
                    c.scale(is, is, x + cellW / 2f, y + cell / 2f);
                }
                drawGiftCell(c, i, gifts.get(i), x + dp(4), y + dp(4), cellW - dp(8), cell - dp(8), pattern.type == PAT_GIFT && pattern.giftIndex == i);
                if (is != 1f) c.restore();
                if (cs) c.restore();
            }
            cascadeApply(c);
            if (giftsScaled) c.restore();
        }
        c.restore();
        if (fades) {
            final float fh = dp(12);
            fadeMatrix.setScale(1f, fh);
            fadeMatrix.postTranslate(0, islandRect.top);
            fadeTop.setLocalMatrix(fadeMatrix);
            c.drawRect(islandRect.left, islandRect.top, islandRect.right, islandRect.top + fh, fadePaint);
            fadeMatrix.setScale(1f, -fh);
            fadeMatrix.postTranslate(0, islandRect.bottom);
            fadeTop.setLocalMatrix(fadeMatrix);
            c.drawRect(islandRect.left, islandRect.bottom - fh, islandRect.right, islandRect.bottom, fadePaint);
            c.restore();
        }
        c.restore();
        if (layer) c.restore();
    }

    private final RectF[] stripRects = {new RectF(), new RectF(), new RectF(), new RectF(), new RectF(), new RectF()};
    private float stripH; // the picked strip's animated height (0 when nothing is picked)

    /** The emoji keyboard's final frame (for laying it out once, before any morph). */
    public float panelFinalTop() {return sheetBottom() - sheetH() + dp(8);}

    public float panelFinalBottom() {return sheetBottom();}

    public float panelFinalLeft() {return dp(SHEET_INSET);}

    public float panelFinalRight() {return getWidth() - dp(SHEET_INSET);}

    private float panelTop = -1, panelAlpha = -1;
    private boolean panelVisible;
    private int panelScene;

    private final RectF panelShape = new RectF();

    private void updateEmojiPanel() {
        if (delegate == null) return;
        // (the fee menu no longer hides the keyboard: it opens upward over the card while the sheet stays as it is)
        final boolean visible = sheet > 0.01f && contentA > 0.004f && tab == SCENE_EMOJI;
        stripH = 0f;
        // laid out once at the final sheet size; the morph only moves the clip (no relayout while opening/closing)
        final float top = sheetBottom() - sheetH() + dp(8) + (1f - contentA) * dp(14);
        final float alpha = contentA;
        // also while only the glass shape still morphs (contentA settles first): the keyboard's clip/fades follow it
        final boolean shapeMoved = visible && !islandRect.equals(panelShape);
        // the backdrop changed under the open keyboard (e.g. a picked emoji brings back the default colour): the packs
        // island and the headers take the new colours
        final int sceneKey = scene != null ? scene.center : 0;
        final boolean recoloured = visible && sceneKey != panelScene;
        if (visible != panelVisible || shapeMoved || recoloured || (visible && (Math.abs(top - panelTop) > 0.5f || Math.abs(alpha - panelAlpha) > 0.01f))) {
            panelScene = sceneKey;
            panelShape.set(islandRect);
            panelVisible = visible;
            panelTop = top;
            panelAlpha = alpha;
            delegate.onEmojiPanel(visible, top, alpha);
            delegate.onEmojiPanelBounds(dp(SHEET_INSET), getWidth() - dp(SHEET_INSET), sheetBottom(),
                islandRect.left, islandRect.top, islandRect.right, islandRect.bottom, Math.min(islandRect.height() / 2f, lerp(dp(20), dp(SHEET_RADIUS), Utilities.clamp01(sheet))));
        }
    }

    private final java.util.HashMap<Long, AnimatedEmojiDrawable> giftThumbs = new java.util.HashMap<>();
    // AnimatedEmojiDrawable.make() caches per document: a tile and the backdrop's floating model are the SAME instance
    // (views are a set, not counted): releasing the pattern must not removeView(this) while a tile still uses it.

    /** A gift tile's backdrop pattern, drawn as GiftSheet's cells do (StarGiftPatterns TYPE_GIFT). */
    private static final class TilePattern {
        AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable d;
        long docId;
        Bitmap batchBitmap;
        Paint batchPaint;
        int batchColor;
    }

    private final java.util.HashMap<Long, TilePattern> giftPatterns = new java.util.HashMap<>();

    private void drawTilePattern(Canvas c, TL_stars.TL_starGiftUnique g, RectF tile, int color) {
        final TilePattern tp = tilePattern(g);
        if (tp.d == null || tp.d.getDrawable() == null) return;
        drawTilePattern(c, tp, tile, color);
    }

    private TilePattern tilePattern(TL_stars.TL_starGiftUnique g) {
        TilePattern tp = giftPatterns.get(g.id);
        if (tp == null) {
            tp = new TilePattern();
            TLRPC.Document doc = null;
            for (TL_stars.StarGiftAttribute a : g.attributes) {
                if (a instanceof TL_stars.starGiftAttributePattern)
                    doc = ((TL_stars.starGiftAttributePattern) a).document;
            }
            if (doc != null) {
                tp.d = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this, dp(28));
                tp.d.set(doc, false);
                tp.d.attach();
                tp.docId = doc.id;
            }
            giftPatterns.put(g.id, tp);
        }
        return tp;
    }

    private void drawTilePattern(Canvas c, TilePattern tp, RectF tile, int color) {
        c.save();
        c.translate(tile.centerX(), tile.centerY());
        boolean legacy = true;
        if (BatchParticlesDrawHelper.isAvailable() && tp.d.isStable() && tp.d.getDrawable() instanceof AnimatedEmojiDrawable) {
            final AnimatedEmojiDrawable ad = (AnimatedEmojiDrawable) tp.d.getDrawable();
            final org.telegram.messenger.ImageReceiver ir = ad.getImageReceiver();
            final Bitmap bm = ir != null && ad.getDocumentId() == tp.docId ? ir.getBitmap() : null;
            if (bm != null) {
                boolean changed = false;
                if (tp.batchBitmap != bm || tp.batchPaint == null) {
                    tp.batchBitmap = bm;
                    tp.batchPaint = BatchParticlesDrawHelper.createBatchParticlesPaint(bm);
                    changed = true;
                }
                if (changed || tp.batchColor != color) {
                    tp.batchColor = color;
                    tp.batchPaint.setColorFilter(QrRender.hasNodes() ? QrRender.srcIn(color) : new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                }
                StarGiftPatterns.drawPatternBatch(c, StarGiftPatterns.TYPE_GIFT, tp.batchPaint, bm, tile.width(), tile.height(), 1f, 1f);
                legacy = false;
            }
        }
        if (legacy) {
            tp.d.setColor(color);
            StarGiftPatterns.drawPattern(c, StarGiftPatterns.TYPE_GIFT, tp.d, tile.width(), tile.height(), 1f, 1f);
        }
        c.restore();
    }

    private int selGift = -1, lastColorIndex = DEFAULT_COLOR;
    private float selA, selTarget; // the gift selection ring fades in / out

    // gift tile selection: press 0.94 on down, a spring back on release; the model sits at 0.86 while selected
    private int pressGift = -1, prevSelGift = -1;
    private long pressDownAt, releaseAt, deselectAt;

    // concentric corners: sheet 30dp − the 8dp gap (sheet edge → tile edge) = 22dp tiles; the ring (4dp in) 18dp
    private final float TILE_R = dp(SHEET_RADIUS - 8);

    private float giftRing(int index) {
        final long now = AnimationUtils.currentAnimationTimeMillis();
        if (index == selGift && selTarget > 0.5f) {
            final float u = Utilities.clamp01((now - releaseAt) / 260f);
            return 1f - (1f - u) * (1f - u) * (1f - u);
        }
        if (index == prevSelGift || (index == selGift && selTarget < 0.5f)) {
            final float u = Utilities.clamp01((now - deselectAt) / 180f);
            return 1f - u * u;
        }
        return 0f;
    }

    // swatch press: 0.92 on touch-down (110 ms ease-out), spring back on release (ζ 0.6, period 0.28 s)
    private int pressSwatch = -1;
    private long swatchDownAt, swatchUpAt = -1;
    private float swatchUpFrom = 1f;

    private float swatchScale(int slot) {
        if (slot != pressSwatch) return 1f;
        final long now = AnimationUtils.currentAnimationTimeMillis();
        if (swatchUpAt < swatchDownAt) {
            final float u = Utilities.clamp01((now - swatchDownAt) / 110f);
            return 1f - 0.08f * (1f - (1f - u) * (1f - u));
        }
        final double sp = springStep((now - swatchUpAt) / 1000f, 0.6, 0.28);
        if (now - swatchUpAt > 900) {
            pressSwatch = -1;
            return 1f;
        }
        return swatchUpFrom + (1f - swatchUpFrom) * (float) sp;
    }

    private void releaseSwatch() {
        if (pressSwatch < 0 || swatchUpAt >= swatchDownAt) return;
        swatchUpFrom = swatchScale(pressSwatch);
        swatchUpAt = AnimationUtils.currentAnimationTimeMillis();
        invalidate();
    }

    private float giftTileScale(int index) {
        final long now = AnimationUtils.currentAnimationTimeMillis();
        if (index == pressGift && releaseAt < pressDownAt) {
            final float u = Utilities.clamp01((now - pressDownAt) / 120f);
            return 1f - 0.06f * (1f - (1f - u) * (1f - u));
        }
        if (index == pressGift) return 1f - 0.06f * (1f - (float) springStep((now - releaseAt) / 1000f, 0.6, 0.3));
        return 1f;
    }

    private float giftModelScale(int index) {
        final long now = AnimationUtils.currentAnimationTimeMillis();
        if (index == selGift && selTarget > 0.5f)
            return 1f - 0.14f * (float) springStep((now - releaseAt) / 1000f, 0.6, 0.3);
        if (index == prevSelGift || (index == selGift && selTarget < 0.5f)) {
            final float u = Utilities.clamp01((now - deselectAt) / 180f);
            return 0.86f + 0.14f * u * u * (3f - 2f * u);
        }
        return 1f;
    }

    private final RectF buyRect = new RectF();
    private final TextPaint emptyTitle = new TextPaint(Paint.ANTI_ALIAS_FLAG), emptySub = new TextPaint(Paint.ANTI_ALIAS_FLAG), buyText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private TextPaint buyDarken;
    private final TextPaint buyKnock = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);

    {buyKnock.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));}

    private StaticLayout emptySubLayout;
    private int emptySubW;
    private boolean emptySubChat;
    private final String emptyTitleText = org.telegram.messenger.LocaleController.getString(R.string.QrNoGiftsTitle);
    private final String buyLabel = org.telegram.messenger.LocaleController.getString(R.string.QrBrowseGifts);

    /** The own gifts can be bought from the empty state; a chat's empty state only says there are none. */
    private boolean canBuyGifts() {return giftsOwner > 0;}

    private AnimatedEmojiDrawable emptyDog;
    private org.telegram.ui.Components.RLottieDrawable emptyLottie;
    private long emptyDogDocId;

    private static final String DOG_SET = "Redo_edc21keMa";
    private boolean dogSetRequested;
    private TLRPC.TL_inputStickerSetShortName dogInput;
    private TLRPC.Document dogDoc;
    private long dogFadeStart;
    private long giftOpenAt; // the gifts layer's opening (the empty-state dog's pop-in) // when the real dog first had a frame (crossfade from the bundled fallback)

    /** The empty-state dog: the first custom emoji of the "Redo_edc21keMa" pack (null while loading / offline). */
    private TLRPC.Document emptyDogDocument() {
        if (dogDoc != null) return dogDoc;
        if (dogInput == null) {
            dogInput = new TLRPC.TL_inputStickerSetShortName();
            dogInput.short_name = DOG_SET;
        }
        if (dogSetRequested) return null; // one request; the callback fills dogDoc (offline: the Lottie fallback stays)
        dogSetRequested = true;
        final TLRPC.TL_messages_stickerSet set = org.telegram.messenger.MediaDataController.getInstance(currentAccount).getStickerSet(dogInput, null, false, r -> {
            if (r != null && r.documents != null && !r.documents.isEmpty()) dogDoc = r.documents.get(0);
            invalidate();
        });
        if (set != null && set.documents != null && !set.documents.isEmpty()) dogDoc = set.documents.get(0);
        return dogDoc;
    }

    /** No gifts: the Resistance Dog, a title, a line and (own gifts) the "Browse Gifts" pill, centred. */
    private void drawGiftsEmpty(Canvas c, float top, float a) {
        final float cx = sheetRect.centerX();
        // the whole group (dog, title, line, button) centred as one block in the layer
        final float ps = dp(99); // 10% smaller
        emptyTitle.setTypeface(AndroidUtilities.bold());
        emptyTitle.setTextSize(dp(16));
        final Paint.FontMetrics tfm0 = emptyTitle.getFontMetrics();
        ensureEmptySub((int) (sheetRect.width() - dp(32)));
        final float groupH = ps + dp(22) + (tfm0.descent - tfm0.ascent) + dp(7) + emptySubLayout.getHeight() + (canBuyGifts() ? dp(20) + dp(36) : 0);
        final float py = top + (dp(EMPTY_H + 14) - groupH) / 2f - dp(8); // padding: 8dp less above, 6dp less below
        // the dog = the first custom emoji of the Redo_edc21keMa pack (stock AnimatedEmojiDrawable, looping); the bundled
        // Resistance Dog Lottie stands in while the pack loads / offline and crossfades out (same box: no layout jump)
        final TLRPC.Document doc = emptyDogDocument();
        if (doc != null && (emptyDog == null || emptyDogDocId != doc.id)) {
            if (emptyDog != null) emptyDog.removeView(this);
            // live Lottie at 100 dp (no "_pcache"/"_compress" frame cache, which rendered it at ~40 dp and upscaled: smeared)
            emptyDog = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_AVATAR_CONSTRUCTOR_PREVIEW, doc);
            emptyDog.addView(this);
            emptyDogDocId = doc.id;
            dogFadeStart = 0;
        }
        final org.telegram.messenger.ImageReceiver ir = emptyDog != null ? emptyDog.getImageReceiver() : null;
        final long now = AnimationUtils.currentAnimationTimeMillis();
        if (ir != null && ir.hasBitmapImage() && dogFadeStart == 0) dogFadeStart = now;
        final float k = dogFadeStart == 0 ? 0f : Math.min(1f, (now - dogFadeStart) / 250f);
        final float ke = k * k * (3f - 2f * k);
        // the dog comes in WITH the layer (same 420 ms clock as the reveal, ease-out cubic): its own 0.85 → 1 on top of
        // the layer's grow, rising 10 dp from below (was a separate 0.6 → 1 spring starting 70 ms late, ~0.7 s long)
        // its own rise + grow done in the first 70% of the reveal (~295 ms): a translation's soft tail read as "late"
        final float drt = sheetTarget > 0.5f ? Utilities.clamp01(revealTime() / 0.7f) : 1f;
        final float de = 1f - (1f - drt) * (1f - drt) * (1f - drt);
        final float ds = lerp(0.95f, 1f, de); // a light own grow: with the layer's 0.75 it starts at ~0.71 (was 0.85 → ~0.64)
        if (drt < 1f) invalidate();
        c.save();
        c.translate(0, (1f - de) * dp(6)); // 6 dp from below (was 10)
        c.scale(ds, ds, cx, py - dp(2) + ps / 2f);
        if (ke < 1f) {
            if (emptyLottie == null) {
                emptyLottie = new org.telegram.ui.Components.RLottieDrawable(R.raw.qr_resistance_dog, (int) ps, (int) ps, true, null);
                emptyLottie.setAutoRepeat(1);
                emptyLottie.setAllowDecodeSingleFrame(true);
                emptyLottie.setMasterParent(this);
                emptyLottie.setCallback(this);
                emptyLottie.start();
            }
            if (emptyLottie.hasBitmap()) {
                emptyLottie.setBounds((int) (cx - ps / 2f), (int) (py - dp(2)), (int) (cx + ps / 2f), (int) (py - dp(2) + ps));
                emptyLottie.setAlpha((int) (255 * a * (1f - ke)));
                emptyLottie.draw(c);
            }
            invalidate();
        } else if (emptyLottie != null) {
            emptyLottie.recycle(false);
            emptyLottie = null;
        }
        if (emptyDog != null && ke > 0f) {
            emptyDog.setBounds((int) (cx - ps / 2f), (int) (py - dp(2)), (int) (cx + ps / 2f), (int) (py - dp(2) + ps));
            emptyDog.setAlpha((int) (255 * a * ke));
            emptyDog.draw(c);
        }
        c.restore();
        emptyTitle.setColor(Color.WHITE);
        emptyTitle.setAlpha((int) (255 * a));
        final Paint.FontMetrics tfm = emptyTitle.getFontMetrics();
        final float ty = py + ps + dp(18) - tfm.ascent; // dog 2dp up, text block 4dp up → dog → title 20dp
        c.drawText(emptyTitleText, cx - emptyTitle.measureText(emptyTitleText) / 2f, ty, emptyTitle);
        emptySub.setColor(Color.WHITE);
        emptySub.setAlpha((int) (0xBF * a)); // white 75%
        final float sy = ty + tfm.descent + dp(7); // title → line 7dp
        c.save();
        c.translate(cx - emptySubLayout.getWidth() / 2f, sy);
        emptySubLayout.draw(c);
        c.restore();
        if (canBuyGifts()) {
            drawBuyButton(c, cx, sy + emptySubLayout.getHeight() + dp(20), a);
        } else {
            buyRect.setEmpty();
        }
    }

    private void ensureEmptySub(int maxW) {
        final boolean chat = !canBuyGifts();
        if (emptySubLayout == null || emptySubW != maxW || emptySubChat != chat) {
            emptySubW = maxW;
            emptySubChat = chat;
            // always two evenly split lines at 14sp, 18.5dp line height
            emptySub.setTextSize(dp(14));
            final Paint.FontMetrics fm = emptySub.getFontMetrics();
            final float add = dp(18.5f) - (fm.descent - fm.ascent);
            final String sub = org.telegram.messenger.LocaleController.getString(chat ? R.string.QrChatNoCollectibles : R.string.QrNoGiftsInfo);
            emptySubLayout = new StaticLayout(sub, emptySub, maxW, android.text.Layout.Alignment.ALIGN_CENTER, 1f, add, false);
        }
    }

    /** "Buy Gifts" with its top at y. */
    private void drawBuyButton(Canvas c, float cx, float by, float a) {
        // the glass pill (our QR glass buttons), white label
        buyText.setTypeface(AndroidUtilities.bold());
        buyText.setTextSize(dp(14));
        final float bw = buyText.measureText(buyLabel) + dp(19) * 2, bh = dp(36); // a neat pill: 36 high, 19 side padding
        buyRect.set(cx - bw / 2f, by, cx + bw / 2f, by + bh);
        // a solid white pill, no rim; the label is cut out of it (the backdrop shows through the letters)
        final int sl = c.saveLayer(buyRect.left - 1, buyRect.top - 1, buyRect.right + 1, buyRect.bottom + 1, null);
        fill.setColor(Color.WHITE);
        fill.setAlpha((int) (255 * a));
        c.drawRoundRect(buyRect, bh / 2f, bh / 2f, fill);
        drawRipple(c, RP_BUY, buyRect, true); // a subtle dark tint over the white (cut out with the letters)
        buyKnock.setTypeface(AndroidUtilities.bold());
        buyKnock.setTextSize(dp(14));
        final Paint.FontMetrics fm = buyKnock.getFontMetrics();
        c.drawText(buyLabel, cx - buyKnock.measureText(buyLabel) / 2f, buyRect.centerY() - (fm.ascent + fm.descent) / 2f, buyKnock);
        c.restoreToCount(sl);
        // the cut-out letters a touch darker, hue kept: multiplied by a light, chroma-boosted tint of the backdrop
        // (the pattern still shows through; ≈12% darker, slightly richer)
        if (buyDarken == null) {
            buyDarken = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            buyDarken.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
        }
        buyDarken.setTypeface(AndroidUtilities.bold());
        buyDarken.setTextSize(dp(14));
        final int base = scene != null ? ColorUtils.blendARGB(scene.center, scene.edge, 0.5f) : 0xFF808080;
        buyDarken.setColor(oklchRelight(base, 0.90f, 1.4f, 0.10f));
        buyDarken.setAlpha((int) (255 * a));
        c.drawText(buyLabel, cx - buyDarken.measureText(buyLabel) / 2f, buyRect.centerY() - (fm.ascent + fm.descent) / 2f, buyDarken);
    }

    private void drawGiftCell(Canvas c, int index, TL_stars.TL_starGiftUnique g, float x, float y, float tw, float s, boolean selected) {
        final float ts = giftTileScale(index);
        if (ts != 1f) {
            c.save();
            c.scale(ts, ts, x + tw / 2f, y + s / 2f);
        }
        drawGiftCellInner(c, index, g, x, y, tw, s);
        if (ts != 1f) c.restore();
    }

    private void drawGiftCellInner(Canvas c, int index, TL_stars.TL_starGiftUnique g, float x, float y, float tw, float s) {
        giftColors(index, giftCol);
        final int center = giftCol[0], edge = giftCol[1];
        TLRPC.Document model = null;
        for (TL_stars.StarGiftAttribute a : g.attributes) {
            if (a instanceof TL_stars.starGiftAttributeModel) {
                model = ((TL_stars.starGiftAttributeModel) a).document;
            }
        }
        rect.set(x, y, x + tw, y + s);
        fill.setColor(Color.WHITE);
        RadialGradient bg = tileShaders.get(g.id);
        if (bg == null) {
            bg = new RadialGradient(0, 0, 1, new int[]{center, edge}, null, Shader.TileMode.CLAMP);
            tileShaders.put(g.id, bg);
        }
        // a wide tile (1–2 gifts): the backdrop stretches with it; the pattern and the gift stay centred
        tileM.setScale(s * 0.7f * Math.max(1f, tw / s), s * 0.7f);
        tileM.postTranslate(rect.centerX(), rect.centerY());
        bg.setLocalMatrix(tileM);
        fill.setShader(bg);
        c.drawRoundRect(rect, TILE_R, TILE_R, fill);
        fill.setShader(null);
        // the backdrop pattern, tinted with the backdrop's pattern colour, clipped to the tile
        c.save();
        path.rewind();
        path.addRoundRect(rect, TILE_R, TILE_R, Path.Direction.CW);
        c.clipPath(path);
        drawTilePattern(c, g, rect, giftCol[2] | 0xFF000000);
        c.restore();
        // a step lighter than the panel: a soft white lift and a crisp light rim
        fill.setColor(0x12FFFFFF); // 7%: cells stay a step above the panel
        c.drawRoundRect(rect, TILE_R, TILE_R, fill);
        stroke.setColor(0x4DFFFFFF);
        stroke.setStrokeWidth(Math.max(1f, dpf2(0.4f)));
        rect2.set(rect);
        rect2.inset(0.5f, 0.5f);
        c.drawRoundRect(rect2, TILE_R - 0.5f, TILE_R - 0.5f, stroke);
        // the selection ring, like the colour circles: the whole concentric ring at once, growing in from the tile
        // edge (stroke 0 → 2dp, inset → 4dp, alpha only at the start); deselect = the mirror back to the edge
        final float ring = giftRing(index);
        if (ring > 0.002f) {
            final boolean sel = index == selGift && selTarget > 0.5f;
            final float in = lerp(dpf2(1f), dp(4), ring), sw = dp(2) * ring;
            final float a = sel ? Math.min(1f, ring * 4f) : Math.min(1f, ring * 3f);
            rect2.set(rect);
            rect2.inset(in, in);
            stroke.setColor(Color.WHITE);
            stroke.setAlpha((int) (255 * a * (index == selGift ? 0.7f + 0.3f * ring : 1f)));
            stroke.setStrokeWidth(sw);
            c.drawRoundRect(rect2, Math.max(0f, TILE_R - in), Math.max(0f, TILE_R - in), stroke); // concentric
        }
        if (model != null) {
            final AnimatedEmojiDrawable d = giftThumb(g.id, model);
            final float ms = s * 0.82f * giftModelScale(index); // the gift; pressed into the ring while selected
            d.setBounds((int) (rect.centerX() - ms / 2), (int) (rect.centerY() - ms / 2), (int) (rect.centerX() + ms / 2), (int) (rect.centerY() + ms / 2));
            // the shared instance keeps the pattern's last slot alpha / lit filter: reset them for the tile
            d.setAlpha(255);
            d.setColorFilter(null);
            d.draw(c);
        }
    }

    private LinearGradient[] swatchShaders = new LinearGradient[Backdrops.CENTER.length];
    private int[] swatchFor = new int[Backdrops.CENTER.length];

    private void drawSphere(Canvas c, int index, float x, float y, float r, int center, int edge, boolean selected) {
        final float selA2 = index == swSel ? swSelA : (index == swPrev ? swPrevA : 0f);
        fill.setColor(Color.WHITE);
        // a plain linear gradient, top-left centre colour → bottom-right edge colour
        if (swatchShaders.length != Backdrops.CENTER.length) {
            // the palette has changed while the screen is open
            swatchShaders = new LinearGradient[Backdrops.CENTER.length];
            swatchFor = new int[Backdrops.CENTER.length];
        }
        LinearGradient g = swatchShaders[index];
        if (g == null || swatchFor[index] != center * 31 + edge) {
            g = swatchShaders[index] = new LinearGradient(0, 0, 1, 1, center, edge, Shader.TileMode.CLAMP);
            swatchFor[index] = center * 31 + edge; // the tuned default re-makes its shader when retuned
        }
        glassMatrix.setScale(2 * r, 2 * r);
        glassMatrix.postTranslate(x - r, y - r);
        g.setLocalMatrix(glassMatrix);
        fill.setShader(g);
        c.drawCircle(x, y, r, fill);
        fill.setShader(null);
        // Dark out rim (Small group), blended over the swatch's own colour. The swatches sit in the sheet's content
        // layer (no backdrop pixels there), so the rim is not grown outward: the crescents stay on the swatch.
        // + 1 px: the crescents must not touch the swatch's anti-aliased edge (half-transparent in that layer,
        // where Plus would turn it into dark dots)
        final float gin = Math.max(0f, -dpf2(RimTune.g[RimTune.SWATCH][RimTune.INSET])) + 1f;
        rect3.set(x - r + gin, y - r + gin, x + r - gin, y + r - gin);
        swatchRim.draw(c, rect3, r - gin, RimTune.SWATCH, 1f);
        if (selA2 > 0.01f) {
            // selection: a white ring inside the circle (colour shows on both sides). Select: grows 0.8 → 1 with a
            // fade-in. Deselect (mirrored, at the same time): thins to 0 while easing out to the swatch edge; the
            // alpha only goes in the last third — the selection reads as moving from one circle to the other.
            final boolean sel = index == swSel;
            final float full = r - dp(4) - dp(1); // inset ~4dp
            final float rr = sel ? full * lerp(0.8f, 1f, selA2) : lerp(r - dpf2(1f), full, selA2);
            final float sw = sel ? dp(2) : dp(2) * selA2;
            final float a = sel ? selA2 : Math.min(1f, selA2 * 3f);
            if (ColorUtils.calculateLuminance(center) > 0.62) {
                stroke.setColor(Theme_multAlpha(0x38000000, a)); // a faint dark hairline keeps it visible on very light swatches
                stroke.setStrokeWidth(sw + dpf2(1.2f) * (sel ? 1f : selA2));
                c.drawCircle(x, y, rr, stroke);
            }
            stroke.setColor(Theme_multAlpha(Color.WHITE, a));
            stroke.setStrokeWidth(sw);
            c.drawCircle(x, y, rr, stroke);
        }
    }

    private final RimPainter swatchRim = new RimPainter();
    private int swSel = -100, swPrev = -100;
    private float swSelA, swPrevA;

    private void stepSwatchSelection() {
        int cur = -100; // slot of the active swatch
        if (scene != null && scene.type == SCENE_COLOR) {
            for (int k = 0; k < PRESET_COUNT + Backdrops.CENTER.length; k++)
                if (slotIndex(k) == scene.index) {
                    cur = k;
                    break;
                }
        }
        if (cur != swSel) {
            // back to the one that is still deselecting: it continues from where it is (no jump)
            final float back = cur == swPrev ? swPrevA : 0f;
            swPrev = swSel;
            swPrevA = swSelA;
            swSel = cur;
            swSelA = back;
        }
        swSelA = approach(swSelA, 1f, 0.2f);   // ~180 ms, in sync
        swPrevA = approach(swPrevA, 0f, 0.2f);
    }

    // --- menu

    private final RectF[] menuRows = {new RectF(), new RectF(), new RectF()};
    private int menuPressed = -1;

    // the chip IS the menu: its own glass pill stretches into the menu (width leads, height follows, the moving
    // edge bulges like liquid), the chip's content fades out inside it and the rows fade in; closing is the reverse
    private long menuAnimStart, wobbleStart;
    private float menuAnimFrom, lastMenuTarget, prevMenu, menuW, menuH, menuHVel, lastMenuH;
    private final Path menuPath = new Path();
    private final RectF morphRect = new RectF(), menuBounds = new RectF();

    private static double springStep(double t, double z, double period) {
        if (t <= 0) return 0;
        final double w0 = 2 * Math.PI / period, wd = w0 * Math.sqrt(1 - z * z);
        return 1 - Math.exp(-z * w0 * t) * (Math.cos(wd * t) + z / Math.sqrt(1 - z * z) * Math.sin(wd * t));
    }

    private void stepMenu() {
        final long now = AnimationUtils.currentAnimationTimeMillis();
        if (menuTarget != lastMenuTarget) {
            lastMenuTarget = menuTarget;
            menuAnimFrom = Math.max(menuW, menuH);
            menuAnimStart = now;
        }
        final float t = (now - menuAnimStart) / 1000f;
        if (menuTarget > 0.5f) {
            final float f = menuAnimFrom;
            menuW = t > 0.7f ? 1f : f + (1f - f) * (float) springStep(t, 0.78, 0.30);        // ≈2% overshoot
            menuH = t > 0.7f ? 1f : f + (1f - f) * (float) springStep(t - 0.02, 0.80, 0.34); // ≈1.5%
        } else {
            final float u = Utilities.clamp01(t / 0.2f), uw = Utilities.clamp01(t / 0.17f);
            menuW = menuAnimFrom * (1f - uw * uw * (3f - 2f * uw));
            menuH = menuAnimFrom * (1f - u * u * (3f - 2f * u));
        }
        final float dt = Math.max(0.004f, frameK * 0.016667f);
        menuHVel = (menuH - lastMenuH) / dt;
        lastMenuH = menuH;
        menu = Math.max(0f, Math.max(menuW, menuH));
        if (prevMenu > 0.001f && menu <= 0.001f) wobbleStart = now; // the drop settles back into the chip
        prevMenu = menu;
    }

    /** Chip settle after the menu pours back: a small squash, ~0.35 s. */
    private float chipWobble() {
        if (wobbleStart == 0) return 0f;
        final float t = (AnimationUtils.currentAnimationTimeMillis() - wobbleStart) / 1000f;
        if (t > 0.45f) return 0f;
        return 0.07f * (float) Math.exp(-t / 0.11f) * (float) Math.sin(2 * Math.PI * t / 0.17f);
    }

    private void drawMenu(Canvas c, boolean glass) {
        if (menu <= 0.001f) return;
        final float mw = dp(222), rowH = dp(46), subH = dp(54), gap = dp(8);
        final float mh = timerOnly ? rowH + rowH : rowH + subH + rowH; // no gaps, rows to the edges
        // a layer open below: the menu grows upward from the chip, over the card, never onto the layer
        final float top = sheet > 0.5f ? chipRect.bottom + dp(6) - mh : chipRect.top - dp(6);
        final float left = (getWidth() - mw) / 2f; // centred
        menuBounds.set(left, top, left + mw, top + mh);
        final float pw = menuW, ph = menuH, mc = Utilities.clamp01(menu);
        morphRect.set(lerp(chipRect.left, menuBounds.left, pw), lerp(chipRect.top, menuBounds.top, ph),
            lerp(chipRect.right, menuBounds.right, pw), lerp(chipRect.bottom, menuBounds.bottom, ph));
        final float r = Math.min(morphRect.height() / 2f, Math.min(morphRect.width() / 2f, lerp(chipRect.height() / 2f, dp(22), Utilities.clamp01(ph))));
        // the moving bottom edge bulges forward with its speed (and dents a little on the spring's return)
        final float bulge = Math.max(-0.25f, Math.min(1f, menuHVel / 9f)) * dp(5);
        final float l = morphRect.left, t = morphRect.top, rr = morphRect.right, b = morphRect.bottom, cx = morphRect.centerX();
        menuPath.rewind();
        menuPath.moveTo(l + r, t);
        menuPath.lineTo(rr - r, t);
        menuPath.quadTo(rr, t, rr, t + r);
        menuPath.lineTo(rr, b - r);
        menuPath.quadTo(rr, b, rr - r, b);
        menuPath.cubicTo(lerp(rr - r, cx, 0.55f), b + bulge, lerp(l + r, cx, 0.55f), b + bulge, l + r, b);
        menuPath.quadTo(l, b, l, b - r);
        menuPath.lineTo(l, t + r);
        menuPath.quadTo(l, t, l + r, t);
        menuPath.close();
        rect.set(l, t, rr, b + Math.max(0f, bulge));
        final boolean light = scene != null && scene.light;
        drawGlassPath(c, menuPath, rect, light, 1f); // the one and only glass shape
        c.save();
        c.clipPath(menuPath);
        // the chip's own content fades and shrinks away inside the shape (first ~30%)
        final float ca = 1f - Utilities.clamp01(mc / 0.3f);
        if (ca > 0.004f) {
            c.saveLayerAlpha(rect, (int) (255 * ca));
            final float s = 1f - 0.15f * Utilities.clamp01(mc / 0.3f);
            c.scale(s, s, chipRect.centerX(), chipRect.centerY());
            chipSkipGlass = true;
            drawChip(c, getWidth(), System.currentTimeMillis());
            chipSkipGlass = false;
            c.restore();
        }
        // rows ride the shape every frame: a uniform scale with its size (overshoot included) and its top edge,
        // plus the vertical stretch per row (lower rows move more, the last one with the bulge)
        final int tc = light ? 0xFF1A1D21 : Color.WHITE, sc2 = light ? 0x991A1D21 : 0xA6FFFFFF;
        // rows are rigid: only a translate with the shape's top-centre (whole pixels once it settles); the clip reveals them
        float mtx = morphRect.centerX() - menuBounds.centerX(), mty = morphRect.top - menuBounds.top;
        if (Math.abs(menuHVel) < 0.6f) {
            mtx = Math.round(mtx);
            mty = Math.round(mty);
        }
        c.save();
        c.translate(mtx, mty);
        text.setSubpixelText(true);
        text.setLinearText(false);
        float y = top;
        final String[] titles = menuTitles(menuTimerOn);
        final String[] subs = menuSubtitles(menuTimerOn);
        for (int i = 0; i < 3; i++) {
            if (i == 1 && timerOnly) {
                menuRows[i].setEmpty();
                continue;
            }
            final float rh = subs[i] != null ? subH : rowH;
            menuRows[i].set(menuBounds.left, y, menuBounds.right, y + rh);
            final float ra = menuTarget > 0.5f ? smooth(0.15f, 0.6f, mc) : smooth(0.6f, 1f, mc); // close: out over the first 40%
            if (ra <= 0.004f) {
                y += rh;
                continue;
            }
            drawRipple(c, RP_MENU + i, menuRows[i], light); // the system ripple (inside the menu's clip)
            c.save();

            final float cy = y + rh / 2f;
            if (i == 2) {
                // "Icon / Premium / premium_main" (Figma): outline star with a cutout, crisp vector, 24 → 20dp
                icon(c, icPremiumMain, menuBounds.left + dp(26), cy, dp(20), tc, ra); // Telegram's own menu_premium_main
            } else {
                if (i == 0) icon(c, icRetry, menuBounds.left + dp(26), cy, dp(20), tc, ra);
                else
                    drawBitmapIcon(c, clockIcon(menuTimerOn), menuBounds.left + dp(26), cy, tc, ra); // clock with an × badge (timer on) / plain clock
            }
            text.setTextSize(dp(15));
            text.setColor(tc);
            text.setAlpha((int) (255 * ra));
            c.drawText(titles[i], menuBounds.left + dp(52), subs[i] != null ? cy - dp(2) : cy + dp(5), text);
            if (subs[i] != null) {
                text.setTextSize(dp(12));
                text.setColor(sc2);
                text.setAlpha((int) (Color.alpha(sc2) * ra));
                c.drawText(subs[i], menuBounds.left + dp(52), cy + dp(14), text);
            }
            c.restore();
            y += rh;
        }
        c.restore();
        c.restore();
    }

    // --- toast

    private final RectF toastRect = new RectF();

    // toast: grows from a dot (width leads), slight overshoot, content in at 130–200 ms; dismiss = reverse into a dot
    private long toastPhaseStart;
    private boolean toastShown;
    // menu icons drawn once into ALPHA_8 bitmaps (24-unit grid at 20dp, Telegram outline weight 1.66u)
    private Bitmap clockX, clockPlain, retryBmp;
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    private Bitmap clockIcon(boolean badge) {
        Bitmap b = badge ? clockX : clockPlain;
        if (b != null) return b;
        final int px = Math.round(dp(22.4f)); // ×1.12: optically matches the neighbouring icons
        b = Bitmap.createBitmap(px, px, Bitmap.Config.ALPHA_8);
        final Canvas c = new Canvas(b);
        final float u = px / 24f;
        c.scale(u, u);
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.66f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(Color.BLACK);
        c.drawCircle(11, 11, 8, p);
        c.drawLine(11, 11, 11, 6.6f, p);
        c.drawLine(11, 11, 7.8f, 11, p);
        if (badge) {
            final Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
            clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            c.drawCircle(18.2f, 18.2f, 5.7f, clear);
            final Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
            f.setColor(Color.BLACK);
            c.drawCircle(18.2f, 18.2f, 4.4f, f);
            clear.setStyle(Paint.Style.STROKE);
            clear.setStrokeWidth(1.4f);
            clear.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(16.6f, 16.6f, 19.8f, 19.8f, clear);
            c.drawLine(19.8f, 16.6f, 16.6f, 19.8f, clear);
            clockX = b;
        } else {
            clockPlain = b;
        }
        return b;
    }

    private void drawBitmapIcon(Canvas c, Bitmap b, float cx, float cy, int color, float alpha) {
        iconPaint.setColor(color);
        iconPaint.setAlpha((int) (Color.alpha(color) * alpha));
        c.drawBitmap(b, cx - b.getWidth() / 2f, cy - b.getHeight() / 2f, iconPaint);
    }

    private final Paint timerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF timerArc = new RectF();

    /**
     * SF-style "timer", 20dp box centred at (cx, cy): ring r 7.25 at (10, 11) with a ±14° gap at the top, a crown
     * (stem to y 2.2 + a 4dp bar) and a hand; strokes 2dp, round caps. t ≥ 0 animates: the ring draws clockwise from
     * the gap (420 ms, ease-out), the hand sweeps a full turn (600 ms, +4° settle over 120 ms), the crown pops 0.6 → 1.
     */
    private void drawTimer(Canvas c, float cx, float cy, int color, float alpha, float t) {
        final float ox = cx - dp(10), oy = cy - dp(10);
        timerPaint.setStyle(Paint.Style.STROKE);
        timerPaint.setStrokeCap(Paint.Cap.ROUND);
        timerPaint.setStrokeWidth(dp(2));
        timerPaint.setColor(color);
        timerPaint.setAlpha((int) (Color.alpha(color) * alpha));
        final float rcx = ox + dp(10), rcy = oy + dp(11), r = dp(7.25f);
        float ring = 1f, hand = 0f;
        if (t >= 0f) {
            final float u = Utilities.clamp01(t / 0.42f);
            ring = 1f - (1f - u) * (1f - u) * (1f - u);
            if (t < 0.6f) {
                final float hu = t / 0.6f;
                hand = 364f * (1f - (1f - hu) * (1f - hu) * (1f - hu));
            } else {
                final float su = Utilities.clamp01((t - 0.6f) / 0.12f);
                hand = 364f - 4f * su * su * (3f - 2f * su);
            }

        }
        timerArc.set(rcx - r, rcy - r, rcx + r, rcy + r);
        c.drawArc(timerArc, -90f + 8f, 344f * ring, false, timerPaint); // gap ±8°
        c.drawLine(rcx, rcy - r, rcx, oy + dp(2.2f), timerPaint);    // the short stem (no button bar)
        // hand
        final double a = Math.toRadians(-90 + hand);
        c.drawLine(rcx, rcy, rcx + (float) Math.cos(a) * dpf2(3.8f), rcy + (float) Math.sin(a) * dpf2(3.8f), timerPaint);
        timerPaint.setStyle(Paint.Style.FILL);
        c.drawCircle(rcx, rcy, dpf2(1.2f), timerPaint);
    }

    private int glassFill;

    // iOS-style lens toast: one progress p drives the shape (dot → circle → pill) and the content (blur, swell, alpha)
    private String shownToastText;
    private boolean toastFastHide;

    /** Hides the toast: fast = the quick horizontal shrink (menu opening), else the lens collapse. */
    private void hideToast(boolean fast) {
        if (shownToastText == null) {
            toastUntil = 0;
            return;
        }
        toastUntil = 0;
        if (toastShown) {
            toastShown = false;
            toastPhaseStart = AnimationUtils.currentAnimationTimeMillis();
        }
        toastFastHide = fast;
        invalidate();
    }

    private int shownToastIcon;
    private long toastOpenedAt;
    private RLottieDrawable toastStar; // "Message fee restored": the animated premium star, 34dp, white, plays once

    private RLottieDrawable toastStar() {
        if (toastStar == null) {
            toastStar = new RLottieDrawable(R.raw.star_premium_2, dp(27), dp(27));
            toastStar.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            toastStar.setAutoRepeat(0);
            toastStar.setMasterParent(this);
            toastStar.setAllowDecodeSingleFrame(true);
        }
        return toastStar;
    }

    private RLottieDrawable toastCheck; // "New code created": Telegram's success check, white, plays once

    private RLottieDrawable toastCheck() {
        if (toastCheck == null) {
            toastCheck = new RLottieDrawable(R.raw.contact_check, dp(28), dp(28));
            // the lottie is a white circle with a BLACK check: luminance → alpha keeps the circle white and cuts the
            // check out (A' = A + lum − 255; RGB = white), so the glass shows through it
            toastCheck.setColorFilter(new android.graphics.ColorMatrixColorFilter(new float[]{
                0, 0, 0, 0, 255,
                0, 0, 0, 0, 255,
                0, 0, 0, 0, 255,
                0.2126f, 0.7152f, 0.0722f, 1f, -255}));
            toastCheck.setAutoRepeat(0);
            toastCheck.setMasterParent(this);
            toastCheck.setAllowDecodeSingleFrame(true);
        }
        return toastCheck;
    }

    private RLottieDrawable toastSave; // "QR code saved to Gallery": the stock save-to-gallery animation, white, once

    private RLottieDrawable toastSave() {
        if (toastSave == null) {
            toastSave = new RLottieDrawable(R.raw.ic_save_to_gallery, dp(28), dp(28));
            toastSave.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
            toastSave.setAutoRepeat(0);
            toastSave.setMasterParent(this);
            toastSave.setAllowDecodeSingleFrame(true);
        }
        return toastSave;
    }

    private boolean toastSavePlaying() {
        return toastSave != null && shownToastIcon == R.drawable.msg_download && toastSave.getCurrentFrame() < toastSave.getFramesCount() - 1;
    }

    private boolean toastCheckPlaying() {
        return toastCheck != null && toastCheck.getCurrentFrame() < toastCheck.getFramesCount() - 1;
    }

    private boolean checkToast() {
        return shownToastIcon == R.drawable.msg_retry && shownToastText != null && !isExpiry(shownToastText);
    }

    private boolean toastStarPlaying() {
        return toastStar != null && toastStar.getCurrentFrame() < toastStar.getFramesCount() - 1;
    }

    private Object toastContent;
    private float toastBlurR = -1f;
    private String recordedFor;

    private static float smooth(float a, float b, float x) {
        final float t = Utilities.clamp01((x - a) / (b - a));
        return t * t * (3f - 2f * t);
    }

    /** cubic-bezier(0.5, 0, 0.9, 0.5): accelerating, so the small lens doesn't linger. */
    private static float closeCurve(float x) {
        float lo = 0f, hi = 1f, u = x;
        for (int i = 0; i < 12; i++) {
            u = (lo + hi) / 2f;
            final float bx = 3 * (1 - u) * (1 - u) * u * 0.5f + 3 * (1 - u) * u * u * 0.9f + u * u * u;
            if (bx < x) lo = u;
            else hi = u;
        }
        return 3 * (1 - u) * u * u * 0.5f + u * u * u;
    }

    private long toastFreezeAt;
    private float toastFrozenCy;
    private boolean toastFrozen;

    /**
     * A layer collapsing under an open toast: the toast doesn't ride the sheet down — it hides in place (fade + a
     * slight scale-down, 140 ms) from where it stood when the collapse began.
     */
    private void drawToast(Canvas c, boolean glass, int w, int h, long now) {
        final long an = AnimationUtils.currentAnimationTimeMillis();
        if (toastFreezeAt == 0 && toastShown && shownToastText != null && sheetTarget == 0 && sheet > 0.001f && !toastRect.isEmpty()) {
            toastFreezeAt = an;
            toastFrozenCy = toastRect.centerY();
            toastText = null;
            toastUntil = 0;
        }
        if (toastFreezeAt != 0) {
            final float u = (an - toastFreezeAt) / 140f;
            if (u >= 1f || shownToastText == null) {
                toastFreezeAt = 0;
                toastShown = false;
                shownToastText = null;
                return;
            }
            invalidate();
            c.saveLayerAlpha(0, 0, w, h, (int) (255 * (1f - u)));
            final float s = 1f - 0.08f * u;
            c.scale(s, s, w / 2f, toastFrozenCy);
            toastFrozen = true;
            drawToastInner(c, glass, w, h, now);
            toastFrozen = false;
            c.restore();
            return;
        }
        drawToastInner(c, glass, w, h, now);
    }

    private void drawToastInner(Canvas c, boolean glass, int w, int h, long now) {
        final boolean want = toastFrozen || (toastText != null && now < toastUntil);
        final long an = AnimationUtils.currentAnimationTimeMillis();
        // a new toast while one is open: close the current one first, then open the new one
        if (!toastFrozen && want && toastShown && shownToastText != null && !shownToastText.equals(toastText)) {
            toastShown = false;
            toastPhaseStart = an;
        }
        if (want && !toastShown) {
            final boolean closing = shownToastText != null && (an - toastPhaseStart) < 360;
            if (!closing) {
                toastShown = true;
                toastPhaseStart = an;
                shownToastText = toastText;
                shownToastIcon = toastIcon;
                shownToastAction = toastAction;
                toastFastHide = false;
                toastOpenedAt = an;
                if (shownToastIcon == 0) {
                    // starts on the toast's show frame, plays once
                    final RLottieDrawable st = toastStar();
                    st.setCurrentFrame(0, false);
                    st.start();
                } else if (checkToast()) {
                    final RLottieDrawable ck = toastCheck();
                    ck.setCurrentFrame(0, false);
                    ck.start();
                } else if (shownToastIcon == R.drawable.msg_download) {
                    final RLottieDrawable sv = toastSave();
                    sv.setCurrentFrame(0, false);
                    sv.start();
                }
            }
        } else if (!want && toastShown) {
            toastShown = false;
            toastPhaseStart = an;
        }
        if (shownToastText == null) return;
        final float t = (an - toastPhaseStart) / 1000f;
        final float p;
        if (toastShown) {
            // compact one-line toasts: the lively spring (~1.06); multi-line / full-width ones: a softer one (~2–3%)
            final boolean soft = shownToastText.indexOf('\n') >= 0 || toastTextWidth(shownToastText) + dp(66) >= w - dp(32);
            p = t >= 0.5f ? 1f : (float) springStep(t, soft ? 0.75 : 0.6, 0.42);
        } else if (toastFastHide) {
            // fast hide (menu opening): width shrinks to a circle in 140 ms, height and centre fixed; no blur, no swell
            if (t >= 0.14f) {
                shownToastText = null;
                toastFastHide = false;
                return;
            }
            p = -1f;
        } else {
            if (t >= 0.36f) {
                shownToastText = null;
                return;
            }
            p = 1f - closeCurve(t / 0.36f);
        }
        if (toastShown || p > 0f) invalidate();
        final boolean twoLines = shownToastText.indexOf('\n') >= 0;
        text.setTextSize(dp(twoLines ? 14 : 15));
        final float tw = toastTextWidth(shownToastText);
        final float aw = shownToastAction != null ? toastActionWidth() + dp(16) : 0f;
        final float W = Math.min(w - dp(32), tw + dp(66) + aw), H = dp(twoLines ? 56 : 44);
        // narrow screens: the text shrinks to fit next to the icon and the action (never clipped)
        final float avail = W - dp(66) - aw;
        toastTextSize = text.getTextSize() * (tw > avail && tw > 0 ? Math.max(0.8f, avail / tw) : 1f);
        final boolean fast = p < 0f;
        final float fu = fast ? Utilities.clamp01(t / 0.14f) : 0f;
        final float q = Math.max(p, 0f);
        final float lh = fast ? H : H * (Math.min(1f, q / 0.55f) + Math.max(0f, q - 1f));
        final float lw = fast ? lerp(W, H, fu * fu) : lh + (W - lh) * (q > 1f ? q : smooth(0.15f, 1f, q));
        final float fastGlassA = fast ? 1f - fu * fu * fu : 1f;
        if (lh < 0.5f) return;
        final float cx = w / 2f;
        final float bottom = sheet > 0.001f ? sheetBottom() - sheetH() * Utilities.clamp01(sheet) - dp(12) : barCY() - dp(20) - dp(14);
        final float cy = toastFrozen ? toastFrozenCy : bottom - H / 2f; // fixed centre: collapses in place
        toastRect.set(cx - lw / 2f, cy - lh / 2f, cx + lw / 2f, cy + lh / 2f);
        final float rad = Math.min(lh / 2f, dp(24));
        // glass: blur + 0x14 white + the single rim, always following the shape
        path.rewind();
        path.addRoundRect(toastRect, rad, rad, Path.Direction.CW);
        glassFill = 0x14FFFFFF;
        glassSheet = true; // the toast wears the Sheet rim
        drawGlass(c, toastRect, rad, false, fastGlassA);
        glassFill = 0;
        glassSheet = false;
        // (no extra darkening: neither on light backdrops nor under the premium toast)
        // content: blur (5dp → 0), lens swell (1.35 → 1), alpha — clipped to the shape
        final float cc = fast ? 1f : smooth(0.2f, 0.95f, p);
        final float ca = fast ? 1f - Utilities.clamp01(t / 0.05f) : smooth(0.08f, 0.5f, p);
        if (ca <= 0.004f) return;
        final float tt = toastShown ? (an - toastOpenedAt) / 1000f : -1f;
        // the action comes in after the shape has mostly opened (150 → 380 ms), so the pill's small bounce happens
        // without it; on hide it simply follows the content
        final float au = toastShown ? Utilities.clamp01((t - 0.15f) / 0.23f) : 1f;
        final float actionA = ca * au * au * (3f - 2f * au);
        if (toastShown && au < 1f) invalidate();
        final boolean timerIcon = shownToastIcon == R.drawable.msg_recent || (shownToastIcon == R.drawable.msg_retry && isExpiry(shownToastText));
        // appear: the lens swell (1.35 → 1); collapse: everything (icon, text, action) shrinks with the pill, so the
        // icon at the edge never sticks out of the shrinking shape
        final float sw = toastShown ? 1f + 0.35f * (1f - cc) : Math.max(0.05f, Math.min(1f, lw / W));
        c.save();
        c.clipPath(path);
        c.scale(sw, sw, cx, cy);
        if (blurAllowed() && blurNow() && c.isHardwareAccelerated()) {
            if (toastContent == null) toastContent = QrRender.createNode("toastContent");
            final boolean starAnim = (shownToastIcon == 0 && toastStarPlaying()) || (checkToast() && toastCheckPlaying()) || toastSavePlaying();
            final boolean animating = (timerIcon && tt >= 0f && tt < 0.8f) || starAnim;
            if (starAnim) invalidate();
            final String key = shownToastText + shownToastIcon + (int) W + (int) toastTextSize; // (the action is fixed per text)
            if (animating || !key.equals(recordedFor) || !QrRender.hasDisplayList(toastContent)) {
                recordedFor = key;
                final Canvas rc = QrRender.beginRecording(toastContent, (int) W, (int) H);
                drawToastContent(rc, 0f, H / 2f, timerIcon, tt);
                QrRender.endRecording(toastContent);
            }
            final float r = dp(5) * (1f - cc);
            if (Math.abs(r - toastBlurR) > 0.25f) {
                toastBlurR = r;
                QrRender.setBlur(toastContent, r > 0.3f ? r : 0f, Shader.TileMode.CLAMP);
            }
            QrRender.setAlpha(toastContent, ca);
            c.translate(cx - W / 2f, cy - H / 2f);
            QrRender.draw(c, toastContent);
            drawToastAction(c, 0f, H / 2f, W, actionA);
        } else {
            c.saveLayerAlpha(toastRect, (int) (255 * ca));
            drawToastContent(c, cx - W / 2f, cy, timerIcon, tt);
            c.restore();
            drawToastAction(c, cx - W / 2f, cy, W, actionA);
        }
        c.restore();
    }

    private void drawToastContent(Canvas c, float left, float cy, boolean timerIcon, float tt) {
        if (shownToastIcon != 0) {
            if (timerIcon) drawTimer(c, left + dp(26), cy, Color.WHITE, 1f, tt);
            else if (checkToast()) {
                final RLottieDrawable ck = toastCheck();
                final int sz = dp(28);
                ck.setBounds((int) (left + dp(26) - sz / 2f), (int) (cy - sz / 2f), (int) (left + dp(26) - sz / 2f) + sz, (int) (cy - sz / 2f) + sz);
                ck.draw(c);
            } else if (shownToastIcon == R.drawable.msg_download) {
                final RLottieDrawable sv = toastSave();
                final int sz = dp(28);
                sv.setBounds((int) (left + dp(26) - sz / 2f), (int) (cy - sz / 2f), (int) (left + dp(26) - sz / 2f) + sz, (int) (cy - sz / 2f) + sz);
                sv.draw(c);
            } else
                icon(c, shownToastIcon == R.drawable.msg_retry ? icRetry : shownToastIcon == R.drawable.msg_copy ? icCopy : icClock, left + dp(26), cy, dp(20), Color.WHITE, 1f);
        } else {
            final RLottieDrawable st = toastStar();
            final int sz = dp(27);
            st.setBounds((int) (left + dp(26) - sz / 2f), (int) (cy - sz / 2f), (int) (left + dp(26) - sz / 2f) + sz, (int) (cy - sz / 2f) + sz);
            st.draw(c);
        }
        text.setColor(Color.WHITE);
        text.setTextSize(toastTextSize > 0 ? toastTextSize : dp(15));
        drawToastText(c, shownToastText, left + dp(48), cy + dp(5));
    }

    /** The action ("Subscribe") at its final place (the toast's full width W, not the animating shape), own alpha. */
    private void drawToastAction(Canvas c, float left, float cy, float W, float alpha) {
        if (shownToastAction == null || alpha <= 0.004f) return;
        toastMedium.setTextSize(dp(15));
        toastMedium.setColor(Color.WHITE); // white: reads on every backdrop
        toastMedium.setAlpha((int) (255 * alpha));
        final float aw = toastMedium.measureText(shownToastAction);
        c.drawText(shownToastAction, left + W - dp(16) - aw, cy + dp(5), toastMedium);
        toastMedium.setAlpha(255);
    }

    private float toastActionWidth() {
        toastMedium.setTextSize(dp(15));
        return toastMedium.measureText(shownToastAction);
    }

    /** The toast's action hit area (the right part of the shown toast, full height). */
    private boolean hitToastAction(float x, float y) {
        if (shownToastAction == null || !toastShown || toastRect.isEmpty()) return false;
        toastActionRect.set(toastRect.right - toastActionWidth() - dp(28), toastRect.top - dp(6), toastRect.right + dp(6), toastRect.bottom + dp(6));
        return toastActionRect.contains(x, y);
    }

    // ---------------------------------------------------------------------------------------------------- touch

    private float downX, downY, downScroll;
    // sheet grid scrolling: platform slop, VelocityTracker and OverScroller fling (as a RecyclerView)
    private final android.widget.OverScroller scroller = new android.widget.OverScroller(getContext());
    private final android.view.ViewConfiguration vc = android.view.ViewConfiguration.get(getContext());
    private android.view.VelocityTracker scrollVelocity;
    private boolean caughtFling;
    private boolean dragging;
    private boolean longPressed;

    private final Runnable longPress = () -> {
        // long press on a picked emoji removes its latest instance
        if (sheet < 0.5f || tab != SCENE_EMOJI || dragging) return;
        final int i = emojiIndexAt(downX, downY);
        if (i >= 0 && picked.contains(EMOJI_SETS[i])) {
            longPressed = true;
            removeEmoji(EMOJI_SETS[i]);
        }
    };

    private int emojiIndexAt(float x, float y) {
        final float cell = (getWidth() - dp(28)) / 6f;
        final float gy = y + sheetScroll - (sheetRect.top + dp(CONTENT_PAD));
        if (gy < 0 || x < dp(14) || y < sheetRect.top + dp(8)) return -1;
        final int i = (int) (gy / cell) * 6 + (int) ((x - dp(14)) / cell);
        return i >= 0 && i < EMOJI_SETS.length ? i : -1;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        final float x = e.getX(), y = e.getY();
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) toastActionDown = hitToastAction(x, y);
        if (toastActionDown) { // the toast's action ("Subscribe") takes the whole gesture
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                toastActionDown = false;
                if (hitToastAction(x, y)) {
                    toastUntil = 0;
                    invalidate();
                    if (delegate != null) delegate.onPremiumSubscribe();
                }
            } else if (e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                toastActionDown = false;
            }
            return true;
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                caughtFling = !scroller.isFinished();
                if (caughtFling) {
                    scroller.forceFinished(true);
                    invalidate();
                }
                downX = x;
                downY = y;
                downScroll = sheetScroll;
                dragging = false;
                longPressed = false;
                if (scrollVelocity == null) scrollVelocity = android.view.VelocityTracker.obtain();
                else scrollVelocity.clear();
                scrollVelocity.addMovement(e);
                panning = sheet < 0.5f && menu < 0.5f && isBackdropAt(x, y);
                if (sheet > 0.5f && tab == SCENE_GIFT) {
                    final int gc = giftCols();
                    final float gx = x - (sheetRect.left + dp(4)), gy = y + sheetScroll - (sheetRect.top + gridPad()) - GIFT_HEADER, cell = (sheetRect.width() - dp(8)) / 3f, cw = (sheetRect.width() - dp(8)) / gc;
                    final int gi = gx >= 0 && gy >= 0 && !giftsEmpty() ? (int) (gy / cell) * gc + (int) (gx / cw) : -1;
                    if (gi >= 0 && gi < giftCount()) {
                        pressGift = gi;
                        pressDownAt = AnimationUtils.currentAnimationTimeMillis();
                    }
                }
                if (sheet > 0.5f && tab == SCENE_COLOR && y > sheetRect.top) {
                    final float gx = x - (sheetRect.left + dp(4)), gy = y + sheetScroll - (sheetRect.top + gridPad()), cell = (sheetRect.width() - dp(8)) / 6f;
                    final int si = gx >= 0 && gy >= 0 && gx < cell * 6 ? (int) (gy / cell) * 6 + (int) (gx / cell) : -1;
                    if (si >= 0 && si < PRESET_COUNT + Backdrops.CENTER.length) {
                        pressSwatch = si;
                        swatchDownAt = AnimationUtils.currentAnimationTimeMillis();
                        invalidate();
                    }
                }
                panMoved = false;
                if (panning) {
                    // catch a running glide / spring where it is: no jump
                    gliding = false;
                    springing = false;
                    panBaseX = panX;
                    panBaseY = panY;
                    stretchBaseX = stretchX;
                    stretchBaseY = stretchY;
                    if (velocity == null) velocity = android.view.VelocityTracker.obtain();
                    else velocity.clear();
                    velocity.addMovement(e);
                }
                removeCallbacks(longPress);

                menuPressed = -1;
                if (menu > 0.5f) for (int i = 0; i < 3; i++) if (menuRows[i].contains(x, y)) menuPressed = i;
                pressRipple(x, y);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (panning) {
                    velocity.addMovement(e);
                    final float ddx = x - downX, ddy = y - downY;
                    if (!panMoved && Math.hypot(ddx, ddy) > dp(8)) panMoved = true;
                    if (panMoved) {
                        panX = panBaseX + ddx;
                        panY = panBaseY + ddy;
                        stretchX = stretchBaseX + rubber(ddx, getWidth());
                        stretchY = stretchBaseY + rubber(ddy, getHeight());
                    }
                    return true;
                }
                if (pressedRipple >= 0 && ripples[pressedRipple] != null) ripples[pressedRipple].setHotspot(x, y);
                if (pressedRipple >= 0 && Math.hypot(x - downX, y - downY) > dp(10)) releaseRipple();
                if (Math.hypot(x - downX, y - downY) > dp(10)) releaseSwatch(); // a scroll: spring back, no select
                if (scrollVelocity != null) scrollVelocity.addMovement(e);
                if (!dragging && sheet > 0.5f && downY > sheetRect.top && Math.abs(y - downY) > vc.getScaledTouchSlop()) {
                    dragging = true;
                    removeCallbacks(longPress);
                    downY = y; // 1:1 from here: no jump by the slop
                    downScroll = sheetScroll;
                }
                if (dragging) sheetScroll = Math.max(0, Math.min(maxScroll(), downScroll - (y - downY)));
                return true;
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                menuPressed = -1;
                releaseRipple();
                releaseSwatch(); // springs back on the same frame as the selection (onTap below)
                if (panning) {
                    panning = false;
                    if (panMoved) {
                        velocity.addMovement(e);
                        velocity.computeCurrentVelocity(1000);
                        float vx = velocity.getXVelocity(), vy = velocity.getYVelocity();
                        final float v = (float) Math.hypot(vx, vy), max = MAX_FLING * dp(1);
                        if (v > max) {
                            vx *= max / v;
                            vy *= max / v;
                        }
                        glideFromX = panX;
                        glideFromY = panY;
                        glideVX = vx;
                        glideVY = vy;
                        glideStart = AnimationUtils.currentAnimationTimeMillis();
                        gliding = true;
                        releaseStretch();
                        return true;
                    }
                    releaseStretch();
                }
                if (dragging && scrollVelocity != null) {
                    scrollVelocity.addMovement(e);
                    scrollVelocity.computeCurrentVelocity(1000, vc.getScaledMaximumFlingVelocity());
                    final float vy = scrollVelocity.getYVelocity();
                    final int max = (int) maxScroll();
                    if (Math.abs(vy) > vc.getScaledMinimumFlingVelocity() && max > 0) {
                        scroller.fling(0, Math.round(sheetScroll), 0, Math.round(-vy), 0, 0, 0, max);
                        invalidate();
                    }
                }
                if (!dragging && !caughtFling && Math.hypot(x - downX, y - downY) < dp(10)) onTap(x, y);
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                menuPressed = -1;
                releaseRipple();
                releaseSwatch();
                if (panning) {
                    panning = false;
                    releaseStretch();
                }
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void releaseStretch() {
        if (stretchX == 0f && stretchY == 0f) return;
        springX = stretchX;
        springY = stretchY;
        springStart = AnimationUtils.currentAnimationTimeMillis();
        springing = true;
    }

    /** The touch area of a button: its bounds and 8dp around, easier to hit than the glass itself. */
    private boolean hit(RectF r, float x, float y) {
        final float m = dp(8);
        return !r.isEmpty() && x >= r.left - m && x <= r.right + m && y >= r.top - m && y <= r.bottom + m;
    }

    /** Backdrop = not the card, chip, close button or the bottom bar. */
    private boolean isBackdropAt(float x, float y) {
        final float cw = cardW(), left = (getWidth() - cw) / 2f, top = cardTop();
        if (x > left && x < left + cw && y > top && y < top + cardH()) return false;
        if (chipShown && hit(chipRect, x, y)) return false;
        if (hit(closeRect, x, y) || (!shareHidden && (hit(shareRect, x, y) || hit(saveRect, x, y))) || hit(scanRect, x, y) || hit(tabsRect, x, y))
            return false;
        return true;
    }

    private float maxScroll() {
        final int w = getWidth();
        final float cell;
        final int rows;
        float extra = 0f;
        final float sw = w - dp(SHEET_INSET) * 2 - dp(8);
        if (tab == SCENE_COLOR) {
            cell = sw / 6f;
            rows = (Backdrops.CENTER.length + 5) / 6;
            extra = dp(4) - dp(CONTENT_PAD * 2) + dp(4);
        } else if (tab == SCENE_EMOJI) {
            cell = sw / 6f;
            rows = 0;
        } else if (giftsEmpty()) {
            cell = 0;
            rows = 0;
        } else {
            cell = sw / 3f;
            rows = (giftCount() + giftCols() - 1) / giftCols();
            extra = dp(4) - dp(CONTENT_PAD * 2) + dp(4);
        }
        return Math.max(0, rows * cell + extra + dp(CONTENT_PAD * 2) - sheetH());
    }

    private void onTap(float x, float y) {
        if (!scanHidden && hit(scanRect, x, y)) {
            // with a layer open the scan pill closes the layer (as back does); otherwise it opens the scanner (over the
            // fee menu too, which stays open)
            if (sheet > 0.001f) {
                sheetTarget = 0;
                invalidate();
                return;
            }
            if (delegate != null) delegate.onScan();
            return;
        }
        if (menu > 0.5f) {
            for (int i = 0; i < 3; i++) {
                if (menuRows[i].contains(x, y)) {
                    menuTarget = 0;
                    // everything in parallel: the menu pours back while the toast comes up (they sit apart)
                    if (i == 0) resetCode();
                    else if (i == 1) toggleTimer();
                    else
                        restoreFee(org.telegram.messenger.LocaleController.getString(R.string.QrNoPaidToastFeeRestored));
                    return;
                }
            }
            menuTarget = 0;
            if (tabsRect.contains(x, y)) { // a layer tab while the fee menu is open: the menu closes first, then the layer opens
                final int t = Math.max(0, Math.min(2, (int) ((x - tabsRect.left) / (tabsRect.width() / 3f))));
                haptic();
                postDelayed(() -> openLayer(t), 200);
            }
            return;
        }
        if (hit(closeRect, x, y)) {
            if (sheet > 0.5f) {
                sheetTarget = 0;
                return;
            }
            if (delegate != null) delegate.onClose();
            return;
        }
        // chip: with a layer open the fee menu opens upward over the card (the layer stays)
        if (chipShown && hit(chipRect, x, y)) {
            chipAction();
            return;
        }
        if (sheet > 0.5f) {
            if (y < sheetRect.top || y > sheetRect.bottom) {
                sheetTarget = 0;
                return;
            }
            if (tab == SCENE_EMOJI) {
                for (int i = 0; i < picked.size(); i++) {
                    if (stripRects[i].contains(x, y)) {
                        picked.remove(i);
                        applyEmojiPattern();
                        haptic();
                        return;
                    }
                }
                return;
            }
            final int w = getWidth();
            final float gridTop = sheetRect.top + gridPad();
            final float gy = y + sheetScroll - gridTop;
            if (gy < 0) return;
            final float gx = x - (sheetRect.left + dp(4)), sw = sheetRect.width() - dp(8);
            if (gx < 0 || gx >= sw) return;
            if (tab == SCENE_GIFT) {
                if (giftsEmpty()) {
                    if (buyRect.contains(x, y)) {
                        haptic();
                        if (delegate != null) delegate.onBuyGifts();
                    }
                    return;
                }
                final float cell = sw / 3f, cw = sw / giftCols();
                if (gy < GIFT_HEADER) return;
                final int i = (int) ((gy - GIFT_HEADER) / cell) * giftCols() + (int) (gx / cw);
                if (i >= 0 && i < giftCount()) {
                    releaseAt = AnimationUtils.currentAnimationTimeMillis();
                    if (pattern.type == PAT_GIFT && pattern.giftIndex == i) {
                        // second tap: deselect — back to the last plain colour (or the default), the pattern leaves with the wave
                        deselectAt = releaseAt;
                        prevSelGift = -1;
                        selTarget = 0f;
                        setSceneFromCard(colorScene(lastColorIndex));
                        setPattern(emojiPatternOrNone());
                        notifyThemeChanged();
                    } else {
                        if (selGift >= 0 && selGift != i && selTarget > 0.5f) {
                            prevSelGift = selGift;
                            deselectAt = releaseAt;
                        }
                        selGift = i;
                        selA = 0f;
                        selTarget = 1f;
                        pickGift(i, cardCenterX(), cardTop() + cardH() / 2f);
                        notifyThemeChanged();
                    }
                }
            } else if (tab == SCENE_COLOR) {
                final float cell = sw / 6f;
                final int i = (int) (gy / cell) * 6 + (int) (gx / cell);
                if (i >= 0 && i < PRESET_COUNT + Backdrops.CENTER.length) {
                    if (slotIndex(i) == DEFAULT_COLOR && scene != null && scene.type == SCENE_COLOR && scene.index == DEFAULT_COLOR && pattern.type == PAT_NONE)
                        return; // already the default
                    final int bi = slotIndex(i);
                    lastColorIndex = bi;
                    final boolean hadGift = pattern.type == PAT_GIFT;
                    if (hadGift) {
                        // the gift no longer matches the colour: deselect it (ring retracts, model springs back)
                        deselectAt = AnimationUtils.currentAnimationTimeMillis();
                        prevSelGift = -1;
                        selTarget = 0f;
                    }
                    setSceneFromCard(colorScene(bi)); // the wave spreads from the QR code
                    // the theme keeps the picked emoji with the colour; a gift's symbols and models leave with the wave
                    if (hadGift) setPattern(emojiPatternOrNone());
                    notifyThemeChanged();
                }
            }
            return;
        }
        if (themeEditable && hit(tabsRect, x, y)) {
            final int t = Math.max(0, Math.min(2, (int) ((x - tabsRect.left) / (tabsRect.width() / 3f))));
            if (menuTarget > 0 || menu > 0.05f) {
                // the reverse: the fee menu closes first, then the layer opens
                menuTarget = 0;
                haptic();
                postDelayed(() -> openLayer(t), 200);
            } else {
                openLayer(t);
            }
            return;
        }
        if (!shareHidden && hit(shareRect, x, y)) {
            if (delegate != null) delegate.onShare(getCurrentLink());
        } else if (!shareHidden && hit(saveRect, x, y)) {
            if (delegate != null) delegate.onSave();
        }
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        if (who instanceof android.graphics.drawable.RippleDrawable) return true;
        return who == chipText || who == logo || (emptyLottie != null && who == emptyLottie) || super.verifyDrawable(who);
    }

    // ---- system ripples on canvas buttons and menu rows (real RippleDrawables, callback = this view)
    static final int RP_MENU = 0, RP_CLOSE = 3, RP_SHARE = 4, RP_SCAN = 5, RP_TAB = 6, RP_CHIP = 9, RP_BUY = 10, RP_SAVE = 11, RP_COUNT = 12;
    private final android.graphics.drawable.RippleDrawable[] ripples = new android.graphics.drawable.RippleDrawable[RP_COUNT];
    private final android.content.res.ColorStateList rippleDark = android.content.res.ColorStateList.valueOf(0x2BFFFFFF), rippleLight = android.content.res.ColorStateList.valueOf(0x14000000);
    private final boolean[] rippleLightSet = new boolean[RP_COUNT];
    private static final int[] ST_PRESSED = {android.R.attr.state_pressed, android.R.attr.state_enabled}, ST_IDLE = {android.R.attr.state_enabled};
    private int pressedRipple = -1;

    private android.graphics.drawable.RippleDrawable ripple(int i) {
        android.graphics.drawable.RippleDrawable d = ripples[i];
        if (d == null) {
            final android.graphics.drawable.GradientDrawable mask = new android.graphics.drawable.GradientDrawable();
            mask.setColor(Color.WHITE);
            if (i == RP_CLOSE || i == RP_SHARE || i == RP_SAVE)
                mask.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            else if (i >= RP_TAB || i == RP_SCAN)
                mask.setCornerRadius(dp(20)); // pills: the radius is clamped to the half-height
            d = new android.graphics.drawable.RippleDrawable(rippleDark, null, mask);
            d.setCallback(this);
            d.setState(ST_IDLE);
            ripples[i] = d;
        }
        return d;
    }

    /** Draws ripple i in rect r (above the glass fill, below icons / text). */
    private void drawRipple(Canvas c, int i, RectF r, boolean light) {
        if (ownRipple(i)) {
            drawOwnRipple(c, i, r, light);
            return;
        }
        final android.graphics.drawable.RippleDrawable d = ripples[i];
        if (d == null) return;
        if (rippleLightSet[i] != light) {
            rippleLightSet[i] = light;
            d.setColor(light ? rippleLight : rippleDark);
        }
        d.setBounds((int) r.left, (int) r.top, (int) Math.ceil(r.right), (int) Math.ceil(r.bottom));
        rippleBounds[i].set(d.getBounds());
        // eased press envelope: some ROMs (One UI) draw the pressed state as a solid fill that switches on / off in
        // one frame, so a brush of the thumb flashed the whole button. The drawable stays pressed until the
        // envelope has faded out; a 1–2 frame touch barely shows.
        final float target = pressedRipple == i ? 1f : 0f;
        final long now = AnimationUtils.currentAnimationTimeMillis();
        final long dt = pressT[i] == 0 ? 16 : Math.min(48, Math.max(0, now - pressT[i]));
        pressT[i] = now;
        pressK[i] += (target - pressK[i]) * (1f - (float) Math.exp(-dt / (target > pressK[i] ? 90f : 180f)));
        if (target == 0f && pressK[i] < 0.004f) {
            pressK[i] = 0f;
            pressT[i] = 0;
            if (rippleHeld[i]) {
                rippleHeld[i] = false;
                d.setState(ST_IDLE);
            }
            return;
        }
        if (Math.abs(target - pressK[i]) > 0.004f) invalidate();
        final float k = pressK[i], e = k * k * (3f - 2f * k);
        c.saveLayerAlpha(r.left, r.top, r.right, r.bottom, (int) (255 * e));
        d.draw(c);
        c.restore();
    }

    /** Every canvas button draws its own press wave, started only by a finger (ACTION_DOWN): the system
     *  RippleDrawable animates on the RenderThread and on One UI flashed buttons for 1–2 frames without a touch. */
    private static boolean ownRipple(int i) {
        return true;
    }

    private void drawOwnRipple(Canvas c, int i, RectF r, boolean light) {
        // the envelope is a function of the press / release times, not of drawn frames: a button hidden under a
        // sheet or menu stopped drawing, froze at full strength and "finished" its wave after the layer closed
        final long now = AnimationUtils.currentAnimationTimeMillis();
        final boolean pressed = pressedRipple == i;
        if (!pressed && releaseT[i] == 0) return;
        final float k = pressed ? rippleK(i, now) : releaseK[i] * (float) Math.exp(-(now - releaseT[i]) / 180f);
        if (!pressed && k < 0.004f) {
            releaseT[i] = 0;
            return;
        }
        final float e = k * k * (3f - 2f * k);
        final float grow = Math.min(1f, (now - pressStart[i]) / 320f), g = 1f - (1f - grow) * (1f - grow) * (1f - grow);
        if (!pressed || k < 0.996f || grow < 1f) invalidate();
        final float hx = Math.max(r.left, Math.min(r.right, hotX[i])), hy = Math.max(r.top, Math.min(r.bottom, hotY[i]));
        final float far = (float) Math.max(Math.hypot(hx - r.left, hy - r.top), Math.max(Math.hypot(hx - r.right, hy - r.top),
            Math.max(Math.hypot(hx - r.left, hy - r.bottom), Math.hypot(hx - r.right, hy - r.bottom))));
        final int base = light ? 0x14000000 : 0x2BFFFFFF;
        ownRipplePaint.setColor((base & 0xFFFFFF) | ((int) ((base >>> 24) * e) << 24));
        ownRipplePath.rewind();
        if (i == RP_CLOSE || i == RP_SHARE || i == RP_SAVE)
            ownRipplePath.addOval(r, android.graphics.Path.Direction.CW);
        else if (i >= RP_TAB || i == RP_SCAN) {
            final float rr = Math.min(dp(20), r.height() / 2f);
            ownRipplePath.addRoundRect(r, rr, rr, android.graphics.Path.Direction.CW);
        } else
            ownRipplePath.addRect(r, android.graphics.Path.Direction.CW); // menu / ⋮ rows: the caller clips to the menu shape
        c.save();
        c.clipPath(ownRipplePath);
        c.drawCircle(hx, hy, Math.max(dp(6), far * g), ownRipplePaint);
        c.restore();
    }

    private final android.graphics.Paint ownRipplePaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.Path ownRipplePath = new android.graphics.Path();
    private final float[] hotX = new float[RP_COUNT], hotY = new float[RP_COUNT];
    private final long[] pressStart = new long[RP_COUNT], releaseT = new long[RP_COUNT];
    private final float[] releaseK = new float[RP_COUNT];

    /** Press envelope while held: rises from where a still-fading previous wave left off. */
    private float rippleK(int i, long now) {
        return 1f - (1f - startK[i]) * (float) Math.exp(-(now - pressStart[i]) / 90f);
    }

    private final float[] startK = new float[RP_COUNT];
    private final float[] pressK = new float[RP_COUNT];
    private final long[] pressT = new long[RP_COUNT];
    private final boolean[] rippleHeld = new boolean[RP_COUNT];
    private final android.graphics.Rect[] rippleBounds = new android.graphics.Rect[RP_COUNT];

    {for (int i = 0; i < RP_COUNT; i++) rippleBounds[i] = new android.graphics.Rect();}

    /** ACTION_DOWN: which ripple target is under (x, y), or −1. */
    private int rippleTarget(float x, float y) {
        if (menu > 0.5f) {
            if (!scanHidden && hit(scanRect, x, y)) return RP_SCAN;
            for (int i = 0; i < 3; i++) if (menuRows[i].contains(x, y)) return RP_MENU + i;
            return -1;
        }
        if (hit(closeRect, x, y)) return RP_CLOSE;
        if (!scanHidden && hit(scanRect, x, y)) return RP_SCAN; // with a layer open too
        if (chipShown && hit(chipRect, x, y)) return RP_CHIP;
        if (sheet < 0.5f) {
            if (!shareHidden && hit(shareRect, x, y)) return RP_SHARE;
            if (!shareHidden && hit(saveRect, x, y)) return RP_SAVE;
            if (themeEditable && hit(tabsRect, x, y))
                return RP_TAB + Math.max(0, Math.min(2, (int) ((x - tabsRect.left) / (tabsRect.width() / 3f))));
        }
        if (sheet > 0.5f && tab == SCENE_GIFT && giftsEmpty() && hit(buyRect, x, y)) return RP_BUY;
        return -1;
    }

    private void pressRipple(float x, float y) {
        releaseRipple();
        final int t = rippleTarget(x, y);
        if (t < 0) return;
        if (ownRipple(t)) {
            final long now = AnimationUtils.currentAnimationTimeMillis();
            startK[t] = releaseT[t] != 0 ? releaseK[t] * (float) Math.exp(-(now - releaseT[t]) / 180f) : 0f;
            releaseT[t] = 0;
            hotX[t] = x;
            hotY[t] = y;
            pressStart[t] = now;
            pressedRipple = t;
            invalidate();
            return;
        }
        final android.graphics.drawable.RippleDrawable d = ripple(t);
        // bounds first (the ripple grows from the hotspot inside them), then the touch point — never the centre
        if (!rippleBounds[t].isEmpty()) d.setBounds(rippleBounds[t]);
        d.setHotspot(x, y);
        if (rippleHeld[t]) d.setState(ST_IDLE); // still fading from the last press: a fresh wave
        d.setState(ST_PRESSED);
        rippleHeld[t] = true;
        pressedRipple = t;
        invalidate();
    }

    private void releaseRipple() {
        // the drawable stays pressed while the envelope fades (drawRipple sets it idle once invisible)
        if (pressedRipple >= 0) {
            final int i = pressedRipple;
            final long now = AnimationUtils.currentAnimationTimeMillis();
            releaseK[i] = rippleK(i, now);
            releaseT[i] = now;
            invalidate();
        }
        pressedRipple = -1;
    }

    public void release() {
        logo.recycle(false);
        if (logoOptimal != null) {
            logoOptimal.recycle();
            logoOptimal = null;
        }
        if (toastSave != null) {
            toastSave.recycle(false);
            toastSave = null;
        }
        if (emptyLottie != null) {
            emptyLottie.recycle(false);
            emptyLottie = null;
        }
        if (emptyDog != null) {
            emptyDog.removeView(this);
            emptyDog = null;
            emptyDogDocId = 0;
        }
        releasePattern(pattern);
        releasePattern(oldPattern);
        removeCallbacks(longPress);
        if (giftsList != null) giftsList.cancel();
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
        if (scrollVelocity != null) {
            scrollVelocity.recycle();
            scrollVelocity = null;
        }
        for (AnimatedEmojiDrawable d : giftThumbs.values()) d.removeView(this);
        for (Drawable d : emojiCache.values())
            if (d instanceof AnimatedEmojiDrawable) ((AnimatedEmojiDrawable) d).removeView(this);
        giftThumbs.clear();
        for (TilePattern tp : giftPatterns.values()) if (tp.d != null) tp.d.detach();
        giftPatterns.clear();
    }

    public int navColor() {
        return scene != null ? scene.edge : Color.BLACK;
    }

    public boolean canConsumeBack() {
        return menu > 0.5f || sheet > 0.5f;
    }

    public boolean onBackPressed() {
        if (menu > 0.5f) {
            menuTarget = 0;
            return true;
        }
        if (sheet > 0.5f) {
            sheetTarget = 0;
            return true;
        }
        return false;
    }
}
