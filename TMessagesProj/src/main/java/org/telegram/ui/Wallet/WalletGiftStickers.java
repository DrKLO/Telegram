package org.telegram.ui.Wallet;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.Utilities.clamp01;
import static org.telegram.messenger.Utilities.dist;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Gravity;
import android.text.Layout;
import android.widget.FrameLayout;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AppGlobalConfig;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_stars;
import org.telegram.tgnet.tl.TL_stories;
import org.telegram.tgnet.tl.TL_wallet;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Stories.recorder.HintView2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * Android View port of GramWalletCards2's StickerBoard. All positions are in the card plane or overlay.
 */
final class WalletGiftStickers extends View {
    private static final int GRID = 0, FLIGHT = 1, CARD = 2, DRAG = 3, HOME = 4, TRASH = 5;
    private static final float SHARE = .231f;
    private static final int MESH = 14;
    private static final float MAX_SCALE = 1.21f;
    private static final float HOLD_GROW = .04f;
    private static final long PICKUP_MS = 170;

    interface Host {
        /**
         * A gift started moving from the grid: show the card (scroll back to it, or float it over the list).
         */
        void bringCard();

        /**
         * A menu action is about to put something onto the card: show it for a while.
         */
        void showCardFor(long holdMs);

        /**
         * Whether the card is on screen now.
         */
        boolean isCardShown();

        /**
         * A drag ended: a floated card may go away again.
         */
        void onDragFinished();

        /**
         * The stickers took over a touch the views below had already seen.
         */
        void cancelChildTouches();
    }

    private final BaseFragment fragment;
    private final boolean readOnly;
    private final WalletCardComponent card;
    private final Host host;
    private final SharedPreferences prefs;
    private final LinkedHashMap<String, Sticker> stickers = new LinkedHashMap<>();
    private final ArrayList<WalletGiftCell> cells = new ArrayList<>();
    private final ArrayList<Ripple> ripples = new ArrayList<>();
    private final Matrix face = new Matrix(), inverse = new Matrix();
    private final Matrix tileTransform = new Matrix(), overlayTransform = new Matrix();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint plainPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF plainRect = new RectF();
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final float[] point = new float[2];
    private final float[] mesh = new float[(MESH + 1) * (MESH + 1) * 2];
    private final int[] meshColors = new int[(MESH + 1) * (MESH + 1)];
    private final View cardLayer, patternLayer;
    private final Spring bin = new Spring(0, 260, .78f), binHover = new Spring(0, 420, .72f);
    private final Spring squash = new Spring(1, 420, .38f);
    private final Spring kickH = new Spring(0, 170, .42f), kickV = new Spring(0, 170, .42f);
    private final ArrayList<Spark> sparks = new ArrayList<>();
    private final Path sparkPath = new Path();
    private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final LinearGradient sheen = new LinearGradient(0, 0, 1, 0,
            new int[]{0x00FFFFFF, 0x00FFFFFF, 0x14FFFFFF, 0x00FFFFFF, 0x00FFFFFF},
            new float[]{0f, .36f, .5f, .64f, 1f}, Shader.TileMode.CLAMP);
    private final Matrix sheenMatrix = new Matrix();
    private float rotX, rotY;
    private final Spring trashFrame = new Spring(0, 120, 1);
    private final Paint trashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RLottieDrawable trashDrawable;
    private boolean trashClosing;
    private Sticker touched, selected;
    private boolean handling;
    private final Paint selectionDash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectionStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean dragging, holding, peeled, ownsTouch, pending, onArt, menuOpen, overBin, fromCard;
    private int pointerId;
    private float releaseVelocityY;
    private float downX, downY, dragX, dragY, knobAngle, knobTotal, knobBase, knobDistance, knobScale;
    private float originalX, originalY, originalScale, originalAngle;
    private int originalState;
    private boolean snapped;
    private long downTime, lastFrame, z;
    private VelocityTracker velocity;
    private String styleKey, pendingStyle;
    private static final int MAX_CARD_STICKERS = 10;
    private long addedCounter;
    private Sticker oldStyle, shownStyle;
    private float reveal = 1, revealX = .5f, revealY = .5f;
    private boolean fadeReveal;
    private float width, height;
    private boolean disposed;
    private TL_wallet.card serverCard;
    private String serverSignature;
    private boolean dirty, applying;
    private int syncRequestId;
    private final Runnable sendCard = this::sendCard;
    private static final float HINT_LOOP = 2;
    private boolean hintOn, hintDragOn, hintWanted;
    private Sticker hintSticker;
    private WalletGiftCell demoCell;
    private float[] hintFit;
    private float hintClock;
    private final float[] hintPose = new float[3];
    private final Spring hintDemo = new Spring(0, 260, 1), outlineShow = new Spring(0, 220, 1);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG), outlineFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix outlineMatrix = new Matrix();
    private final Path outlinePath = new Path();
    private final RectF outlineBounds = new RectF();
    private HintView2 hintView, dragTipView;
    private final ViewTreeObserver.OnScrollChangedListener hintScroll = this::wakeHint;
    private final ViewTreeObserver.OnPreDrawListener rotationSync = this::syncRotation;
    private boolean syncRotation() {
        float x = card.getCardRotationX(), y = card.getCardRotationY();
        if (x != rotX || y != rotY) {
            rotX = x;
            rotY = y;
            cardLayer.invalidate();
        }
        return true;
    }

    void wakeHint() {
        if (hintOn) wake();
    }

    private final Runnable longPress = () -> {
        if (touched == null || dragging) return;
        if (fromCard) {
            holding = true;
            selected = touched;
            knobAngle = Float.NaN;
            knobDistance = Float.NaN;
            knobTotal = 0;
            snapped = false;
            knobBase = touched.rotation.target;
            touched.realSize.set(0);
            touched.press.with(260, .58f).to(1);
            touched.z = ++z;
            touched.image.setAllowStartLottieAnimation(true);
            touched.image.startAnimation();
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            wake();
        } else if (ownsTouch) {
            WalletGiftCell cell = cell(touched);
            if (cell != null) showMenu(cell);
        }
    };
    private final Runnable pickUp = () -> {
        if (!pending || touched == null) return;
        claim();
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        WalletGiftCell c = cell(touched);
        if (c != null) {
            c.takeDrawnScale();
            c.animate().scaleX(.94f).scaleY(.94f).setDuration(100).start();
        }
        wake();
    };

    WalletGiftStickers(WalletActivity fragment, WalletCardComponent card, Host host) {
        this(fragment, card, host, null);
    }

    WalletGiftStickers(BaseFragment fragment, WalletCardComponent card, TL_wallet.card profileCard) {
        this(fragment, card, null, profileCard);
    }

    private WalletGiftStickers(BaseFragment fragment, WalletCardComponent card, Host host, TL_wallet.card profileCard) {
        super(fragment.getContext());
        this.fragment = fragment;
        readOnly = profileCard != null;
        trashDrawable = new RLottieDrawable(R.raw.wallet_gift_trash, dp(42), dp(42), true, null);
        trashDrawable.setMasterParent(this);
        this.card = card;
        this.host = host;
        sheenPaint.setShader(sheen);
        sheenPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        prefs = readOnly ? null : getContext().getSharedPreferences("wallet_card_stickers_" + fragment.getCurrentAccount() + "_" + GramWallet.getInstance(fragment.getCurrentAccount()).getAddress(), Context.MODE_PRIVATE);
        styleKey = readOnly ? null : prefs.getString("style", null);
        hintOn = !readOnly && !MessagesController.getGlobalMainSettings().getBoolean("walletcardstickerhint", false);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeCap(Paint.Cap.ROUND);
        outlinePaint.setStrokeJoin(Paint.Join.ROUND);
        outlineFill.setColor(Color.WHITE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setTranslationZ(dp(24));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        text.setTypeface(AndroidUtilities.bold());
        patternLayer = new View(getContext()) {
            @Override
            protected void onDraw(Canvas canvas) {
                drawPatterns(canvas);
            }
        };
        cardLayer = new View(getContext()) {
            @Override
            protected void onDraw(Canvas canvas) {
                drawOnCard(canvas);
            }
        };
        patternLayer.setTag(WalletCard2DView.BACK_PATTERN);
        card.getFrontFace().addView(patternLayer, 0, LayoutHelper.createFrame(-1, -1));
        card.getFrontFace().addView(cardLayer, card.getStickerLayerIndex(), LayoutHelper.createFrame(-1, -1));
        if (readOnly) {
            cardLayer.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                if (r - l != or - ol || b - t != ob - ot) {
                    for (Sticker sticker : stickers.values()) sticker.restored = false;
                    wake();
                }
            });
            applyCard(profileCard);
        } else {
            loadCard();
        }
    }

    static final class Spring {
        float value, target, velocity, stiffness, damping;

        Spring(float v, float k, float d) {
            value = target = v;
            stiffness = k;
            damping = d;
        }

        Spring set(float v) {
            value = target = v;
            velocity = 0;
            return this;
        }

        Spring to(float v) {
            target = v;
            return this;
        }

        Spring with(float k, float d) {
            stiffness = k;
            damping = d;
            return this;
        }

        boolean step(float dt) {
            float friction = 2 * damping * (float) Math.sqrt(stiffness);
            for (float left = dt; left > 0; ) {
                float h = Math.min(left, 1f / 480);
                velocity += (-stiffness * (value - target) - friction * velocity) * h;
                value += velocity * h;
                left -= h;
            }
            boolean moving = Math.abs(value - target) > .002f || Math.abs(velocity) > .02f;
            if (!moving) set(target);
            return moving;
        }
    }

    final class Sticker {
        final String key;
        TL_wallet.nftItem nft;
        final ImageReceiver image = new ImageReceiver(WalletGiftStickers.this) {
            @Override
            public void invalidate() {
                super.invalidate();
                wake();
            }
        };
        final ImageReceiver pattern = new ImageReceiver(patternLayer);
        final Spring x = new Spring(0, 900, .86f), y = new Spring(0, 900, .86f);
        final Spring scale = new Spring(1, 420, .78f), user = new Spring(1, 700, .9f);
        final Spring rotation = new Spring(0, 700, .9f), lift = new Spring(0, 380, .82f);
        final Spring curl = new Spring(0, 30, 1), press = new Spring(0, 520, .62f);
        final Spring border = new Spring(0, 300, 1);
        final Spring arcX = new Spring(0, 150, .82f), arcY = new Spring(0, 150, .82f);
        final Spring impact = new Spring(0, 240, 1);
        final Spring realSize = new Spring(0, 700, 1);
        final Spring appear = new Spring(1, 260, 1);
        final Spring[] springs = {x, y, scale, user, rotation, lift, curl, press, border, arcX, arcY, impact, realSize, appear};
        TL_stars.starGiftAttributeBackdrop backdrop;
        Bitmap art, plain, shadow;
        Canvas plainCanvas;
        int[] outlineInput, outlineOutput, outlineAlpha, outlineLine;
        boolean outlining, outlineStale;
        int lastImageFrame = -2;
        Path outline;
        Bitmap lastBitmap;
        int state;
        long z;
        long added;
        boolean restored, contact, ring, perched, tossed, flewIn, slapped;
        boolean ribbonOn;
        boolean playOnce, playStarted;
        float curlX, curlY = 1, homeT, homeX, homeY, homeScale, homeAngle, homeDuration, homeCell = 1;

        Sticker(TL_wallet.nftItem nft) {
            this.nft = nft;
            key = nft.address;
            for (TL_stars.StarGiftAttribute attr : nft.gift.attributes) {
                if (attr instanceof TL_stars.starGiftAttributeBackdrop)
                    backdrop = (TL_stars.starGiftAttributeBackdrop) attr;
                if (attr instanceof TL_stars.starGiftAttributePattern) {
                    pattern.setImage(ImageLocation.getForDocument(((TL_stars.starGiftAttributePattern) attr).document), "60_60_firstframe", null, null, nft.gift, 1);
                }
            }
            ImageReceiver.ImageReceiverDelegate loaded = (receiver, set, thumb, memCache) -> wake();
            image.setDelegate(loaded);
            pattern.setDelegate(loaded);
            image.setCurrentAccount(fragment.getCurrentAccount());
            pattern.setCurrentAccount(fragment.getCurrentAccount());
            image.setAllowStartLottieAnimation(false);
            image.setAutoRepeat(1);
            image.setImage(ImageLocation.getForDocument(nft.gift.getDocument()), "120_120_nolimit", null, null, nft.gift, 1);
            if (isAttachedToWindow()) {
                image.onAttachedToWindow();
                pattern.onAttachedToWindow();
            }
        }

        boolean onCard() {
            return state == CARD || state == FLIGHT;
        }

        Bitmap bitmap() {
            Bitmap source = image.getBitmap();
            if (source == null || source.isRecycled()) return art;
            RLottieDrawable animation = image.getLottieAnimation();
            int frame = animation == null ? 0 : animation.getCurrentFrame();
            if (plain != null && lastBitmap == source && lastImageFrame == frame) return art;
            lastBitmap = source;
            lastImageFrame = frame;
            final int n = WalletStickerOutline.SIZE, padding = WalletStickerOutline.PADDING;
            if (plain == null) {
                plain = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
                plainCanvas = new Canvas(plain);
            }
            plain.eraseColor(0);
            plainRect.set(padding, padding, n - padding, n - padding);
            plainCanvas.drawBitmap(source, null, plainRect, plainPaint);
            if (outlining) outlineStale = true;
            else outline();
            return art;
        }

        private void outline() {
            final int n = WalletStickerOutline.SIZE;
            if (outlineInput == null) {
                outlineInput = new int[n * n];
                outlineOutput = new int[n * n];
                outlineAlpha = new int[n * n];
                outlineLine = new int[WalletStickerOutline.lineBufferSize()];
            }
            plain.getPixels(outlineInput, 0, n, 0, 0, n, n);
            outlining = true;
            outlineStale = false;
            final boolean needShadow = shadow == null;
            final int[] input = outlineInput, output = outlineOutput, alpha = outlineAlpha, line = outlineLine;
            WalletStickerOutline.queue().postRunnable(() -> {
                WalletStickerOutline.compose(input, output, alpha, line);
                final Bitmap newShadow = needShadow && WalletStickerOutline.covered(output) ? WalletStickerOutline.shadow(output) : null;
                AndroidUtilities.runOnUIThread(() -> {
                    outlining = false;
                    if (disposed) return;
                    if (art == null) {
                        art = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
                        if (card.isShown() && card.getAlpha() > 0) appear.set(0).to(1);
                    }
                    art.setPixels(output, 0, n, 0, 0, n, n);
                    if (newShadow != null && shadow == null) shadow = newShadow;
                    if (outlineStale) outline();
                    wake();
                });
            });
        }
    }

    void bind(WalletGiftCell cell) {
        if (!cells.contains(cell)) cells.add(cell);
        Sticker s = ensure(cell.nft);
        cell.setWalletStickerState(s.state == GRID ? 0 : 1, s.ribbonOn, false);
        wake();
    }

    void unbind(WalletGiftCell cell) {
        cells.remove(cell);
    }

    private boolean allowed(Sticker s) {
        return s != null && s.nft != null && s.nft.gift != null;
    }

    private boolean colorAllowed(Sticker s) {
        return allowed(s) && isCardBgGift(fragment.getCurrentAccount(), s.nft.gift.slug);
    }

    public static boolean isCardBgGift(int currentAccount, String slug) {
        if (TextUtils.isEmpty(slug)) return false;
        final int dash = slug.lastIndexOf('-');
        final java.util.Set<String> collections = AppGlobalConfig.getInstance(currentAccount).walletCardBgGifts.get();
        return collections != null && collections.contains(dash > 0 ? slug.substring(0, dash) : slug);
    }

    boolean isColorApplied(TL_wallet.nftItem nft) {
        return nft != null && nft.address != null && nft.address.equals(styleKey);
    }

    boolean isOnCard(TL_wallet.nftItem nft) {
        if (nft == null || nft.address == null) return false;
        final Sticker s = stickers.get(nft.address);
        return s != null && s.onCard();
    }

    void toggleColor(TL_wallet.nftItem nft) {
        if (!geometry()) return;
        toggleColor(ensure(nft));
    }

    private void toggleColor(Sticker s) {
        if (s.key.equals(styleKey)) clearStyle();
        else if (colorAllowed(s)) {
            styleKey = s.key;
            if (s.onCard()) {
                startReveal(s, false);
                changed();
            } else {
                pendingStyle = s.key;
                toss(s);
                if (s.state == GRID) {
                    styleKey = pendingStyle = null;
                } else changed();
            }
            save();
        }
    }

    private Sticker ensure(TL_wallet.nftItem nft) {
        Sticker s = stickers.get(nft.address);
        if (s == null) {
            s = new Sticker(nft);
            stickers.put(nft.address, s);
        }
        s.nft = nft;
        return s;
    }

    void sync(GramWallet.CollectiblesList list) {
        for (TL_wallet.nftItem nft : list.collectibles) if (nft.gift != null) ensure(nft);
        boolean removed = false;
        for (java.util.Iterator<Sticker> it = stickers.values().iterator(); it.hasNext(); ) {
            final Sticker s = it.next();
            if (s.state == DRAG || !list.isSending(s.key)) continue;
            if (s.key.equals(styleKey) || s.key.equals(pendingStyle)) {
                styleKey = pendingStyle = null;
                startReveal(null, true);
            }
            it.remove();
            if (prefs != null) prefs.edit().remove(s.key).apply();
            removed = true;
        }
        if (removed && !readOnly) {
            save();
            changed();
        }
        wake();
    }

    private void updateCell(WalletGiftCell cell, Sticker s) {
        cell.setWalletStickerState(s.state == GRID ? 0 : 1, s.ribbonOn);
    }

    private WalletGiftCell cell(Sticker s) {
        for (WalletGiftCell c : cells)
            if (c.isAttachedToWindow() && c.nft.address.equals(s.key)) return c;
        return null;
    }

    private RectF tile(Sticker s) {
        WalletGiftCell cell = cell(s);
        if (cell == null || !cell.isShown()) return null;
        WalletCardComponent.transformToRoot(cell, tileTransform);
        WalletCardComponent.transformToRoot(this, overlayTransform);
        if (!overlayTransform.invert(overlayTransform)) return null;
        tileTransform.postConcat(overlayTransform);
        RectF r = new RectF(0, 0, cell.getWidth(), cell.getHeight());
        tileTransform.mapRect(r);
        return r.bottom <= 0 || r.top >= getHeight() ? null : r;
    }

    private float artShare(Sticker s) {
        WalletGiftCell cell = cell(s);
        if (cell == null || cell.getWidth() <= 0 || cell.imageView.getWidth() <= 0) return .77f;
        return cell.imageView.getWidth() / (float) cell.getWidth()
                * WalletStickerOutline.SIZE / (WalletStickerOutline.SIZE - 2f * WalletStickerOutline.PADDING);
    }

    private boolean geometry() {
        width = card.getFrontFace().getWidth();
        height = card.getFrontFace().getHeight();
        return width > 0 && height > 0 && card.getFrontFaceTransform(this, face) && face.invert(inverse);
    }

    private float[] local(float x, float y) {
        float[] p = {x, y};
        inverse.mapPoints(p);
        return p;
    }

    private float[] root(float x, float y) {
        float[] p = {x, y};
        face.mapPoints(p);
        return p;
    }

    private float size() {
        return width * SHARE;
    }

    private float binY() {
        return getHeight() - dp(64) - AndroidUtilities.navigationBarHeight;
    }

    void toggle(TL_wallet.nftItem nft) {
        if (!geometry()) return;
        Sticker s = ensure(nft);
        if (s.onCard()) {
            if (host.isCardShown()) home(s);
            else snapHome(s);
        } else if (s.state == GRID && allowed(s)) toss(s);
        changed();
    }

    private void snapHome(Sticker s) {
        if (s.key.equals(styleKey)) clearStyle();
        s.perched = false;
        back(s);
        WalletGiftCell c = cell(s);
        if (c != null) {
            c.setScaleX(.93f);
            c.setScaleY(.93f);
            c.animate().scaleX(1).scaleY(1).setDuration(260).setInterpolator(new android.view.animation.OvershootInterpolator(2)).start();
        }
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        save();
        wake();
    }

    void showMenu(WalletGiftCell cell) {
        removeCallbacks(longPress);
        menuOpen = true;
        ownsTouch = false;
        getParent().requestDisallowInterceptTouchEvent(false);
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
        cell.releaseIntoDrawnScale(150);
        Sticker s = ensure(cell.nft);
        if (!allowed(s) && !s.onCard()) {
            menuOpen = false;
            return;
        }
        ItemOptions menu = ItemOptions.makeOptions(fragment, cell, true);
        menu.setDimAlpha(51);
        if (colorAllowed(s) || s.key.equals(styleKey))
            menu.add(R.drawable.msg_colors, LocaleController.getString(s.key.equals(styleKey) ? R.string.WalletGiftRemoveColor : R.string.WalletGiftApplyColor), () -> toggleColor(s));
        menu.add(R.drawable.msg_sticker, LocaleController.getString(s.onCard() ? R.string.WalletGiftRemoveFromCard : R.string.WalletGiftAddToCard), () -> toggle(s.nft));
        menu.setOnDismiss(() -> {
            menuOpen = false;
            touched = null;
            wake();
        });
        menu.show();
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    boolean touch(MotionEvent event) {
        if (readOnly) return false;
        if (card.isBackFacing() && !ownsTouch && !pending) return false;
        if (disposed || menuOpen || !geometry()) return ownsTouch;
        if (hintOn) wake();
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            removeCallbacks(longPress);
            removeCallbacks(pickUp);
            touched = null;
            ownsTouch = pending = dragging = holding = handling = peeled = false;
            pointerId = event.getPointerId(0);
            float x = event.getX(), y = event.getY();
            float[] p = local(x, y);
            handling = handleAt(x, y);
            touched = handling ? selected : stickerAt(p[0], p[1]);
            if (!handling && touched != selected) {
                selected = null;
                wake();
            }
            fromCard = touched != null;
            if (touched == null) for (Sticker s : stickers.values()) {
                RectF r = tile(s);
                if (r != null && r.contains(x, y)) {
                    if (!allowed(s)) break;
                    onArt = Math.abs(x - r.centerX()) < r.width() * .35f && Math.abs(y - r.centerY()) < r.height() * .35f;
                    touched = s;
                    break;
                }
            }
            if (touched == null || (touched.state != GRID && touched.state != CARD)) {
                touched = null;
                return false;
            }
            downX = x;
            downY = y;
            downTime = SystemClock.uptimeMillis();
            originalState = touched.state;
            originalX = touched.x.target;
            originalY = touched.y.target;
            originalAngle = touched.rotation.target;
            originalScale = touched.user.target;
            if (velocity != null) velocity.recycle();
            velocity = VelocityTracker.obtain();
            velocity.addMovement(event);
            if (handling) {
                knobAngle = Float.NaN;
                knobDistance = Float.NaN;
                knobTotal = 0;
                knobBase = touched.rotation.target;
                snapped = false;
                touched.z = ++z;
                rotate(touched, x, y);
            } else postDelayed(longPress, fromCard ? 450 : 500);
            if (fromCard) {
                ownsTouch = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                wake();
                return true;
            }
            pending = true;
            postDelayed(pickUp, PICKUP_MS);
            return false;
        }
        if (!ownsTouch && !pending) return false;
        if (velocity != null) velocity.addMovement(event);
        final int index = event.findPointerIndex(pointerId);
        final boolean primaryUp = action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.getActionIndex()) == pointerId;
        if (index < 0) {
            if (pending) abandon();
            else finishTouch(true, 0, 0);
            return true;
        }
        float x = event.getX(index), y = event.getY(index);
        if (pending) {
            if (action == MotionEvent.ACTION_MOVE && dist(x, y, downX, downY) > ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
                if (onArt && touched.state == GRID) {
                    claim();
                    startDrag(touched, x, y);
                    drag(touched, x, y);
                    wake();
                    return true;
                }
                abandon();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || primaryUp) {
                abandon();
            }
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE && touched != null) {
            if (holding || handling) rotate(touched, x, y);
            else if (dist(x, y, downX, downY) > ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
                removeCallbacks(longPress);
                if (!dragging && (fromCard || touched.state == GRID)) startDrag(touched, x, y);
                if (dragging) drag(touched, x, y);
            }
            wake();
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || primaryUp) {
            float vx = 0, vy = 0;
            if (velocity != null) {
                velocity.computeCurrentVelocity(1000);
                vx = velocity.getXVelocity(pointerId);
                vy = velocity.getYVelocity(pointerId);
            }
            finishTouch(action == MotionEvent.ACTION_CANCEL, vx, vy);
        }
        return true;
    }

    private void claim() {
        pending = false;
        ownsTouch = true;
        removeCallbacks(pickUp);
        host.cancelChildTouches();
        getParent().requestDisallowInterceptTouchEvent(true);
    }

    private void abandon() {
        pending = false;
        removeCallbacks(pickUp);
        removeCallbacks(longPress);
        touched = null;
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
        wake();
    }

    private void rotate(Sticker s, float x, float y) {
        float[] c = root(s.x.value, s.y.value);
        float distance = dist(x, y, c[0], c[1]);
        if (Float.isNaN(knobDistance)) {
            knobDistance = distance;
            knobScale = s.user.target;
        }
        if (Math.abs(distance - knobDistance) > dp(5)) s.realSize.with(700, 1).to(1);
        float min = dp(32) / size();
        float scale = handling ? knobScale * distance / Math.max(1, knobDistance)
                : knobScale + (distance - knobDistance) / dp(110) * (MAX_SCALE - min);
        s.user.with(900, 1).to(Math.max(min, Math.min(MAX_SCALE, scale)));
        if (distance < dp(14)) {
            knobAngle = Float.NaN;
            return;
        }
        float a = (float) Math.toDegrees(Math.atan2(y - c[1], x - c[0]));
        if (!Float.isNaN(knobAngle)) knobTotal += angle(a - knobAngle);
        knobAngle = a;
        float target = knobBase + knobTotal, nearest = Math.round(target / 90) * 90;
        boolean snap = Math.abs(target - nearest) < 5;
        if (snap && !snapped) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        snapped = snap;
        s.rotation.with(900, .95f).to(snap ? nearest : target);
    }

    private void startDrag(Sticker s, float pointerX, float pointerY) {
        selected = null;
        dragging = true;
        peeled = originalState == CARD;
        if (hintOn) {
            hintDragOn = s == hintSticker && !peeled;
            hintOn = hintWanted = false;
            MessagesController.getGlobalMainSettings().edit().putBoolean("walletcardstickerhint", true).apply();
        }
        if (s.perched) {
            s.perched = false;
            s.user.with(420, .8f).to(1);
        }
        WalletGiftCell sourceCell = cell(s);
        if (sourceCell != null) sourceCell.animate().scaleX(1).scaleY(1).setDuration(220).start();
        overBin = false;
        float[] p;
        if (peeled) p = root(s.x.value, s.y.value);
        else {
            RectF tile = tile(s);
            if (tile == null) return;
            p = new float[]{tile.centerX(), tile.centerY()};
            s.scale.with(500, .75f).set(tile.width() * artShare(s) / size());
            s.border.set(0);
            host.bringCard();
        }
        s.x.with(1100, .9f).set(p[0]);
        s.y.with(1100, .9f).set(p[1]);
        s.state = DRAG;
        s.z = ++z;
        s.border.to(1);
        s.lift.with(320, .75f).to(1);
        s.press.set(0);
        dragX = p[0] - pointerX;
        dragY = p[1] - pointerY;
        bin.to(peeled ? 1 : 0);
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private void drag(Sticker s, float x, float y) {
        dragX *= .9f;
        dragY *= .9f;
        float[] p = local(x + dragX, y + dragY);
        boolean over = p[0] >= 0 && p[0] <= width && p[1] >= 0 && p[1] <= height;
        boolean near = peeled && !over && dist(x, y, getWidth() / 2f, binY()) < dp(78);
        if (near != overBin) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        overBin = near;
        bin.to(peeled && !over ? 1 : 0);
        binHover.to(near ? 1 : 0);
        s.x.to(near ? binIconX() : x + dragX);
        s.y.to(near ? binY() - dp(46) : y + dragY);
        RectF source = tile(s);
        float tileSize = source == null ? size() : source.width();
        s.scale.to(near ? dp(38) / size() : 1.24f * (over ? 1 : tileSize * .77f / size()));
        if (peeled)
            s.rotation.with(260, .9f).to(s.rotation.value + angle((over ? originalAngle : 0) - s.rotation.value));
    }

    boolean isInteracting() {
        return ownsTouch;
    }

    private void finishTouch(boolean cancel, float vx, float vy) {
        getParent().requestDisallowInterceptTouchEvent(false);
        removeCallbacks(longPress);
        removeCallbacks(pickUp);
        releaseVelocityY = vy;
        if (dragging) host.onDragFinished();
        Sticker s = touched;
        if (s != null) {
            WalletGiftCell c = cell(s);
            if (c != null) c.animate().scaleX(1).scaleY(1).setDuration(220).start();
            if (dragging) {
                if (cancel && originalState == CARD) {
                    float[] from = local(s.x.value, s.y.value);
                    s.user.to(originalScale);
                    s.rotation.to(originalAngle);
                    place(s, from, new float[]{originalX, originalY, originalScale}, false);
                } else if (cancel) home(s);
                else if (overBin) {
                    s.state = TRASH;
                    s.z = ++z;
                    s.added = 0;
                    trashClosing = true;
                    trashFrame.set(Math.max(30, trashFrame.value));
                    s.x.with(500, .9f).to(binIconX());
                    s.y.with(500, .9f).to(binY());
                    s.scale.with(240, .95f).to(0);
                    s.lift.to(0);
                    s.rotation.to(0);
                    if (s.key.equals(styleKey)) clearStyle();
                    performHapticFeedback(HapticFeedbackConstants.CONFIRM);
                } else {
                    float[] from = local(s.x.value, s.y.value);
                    boolean over = from[0] >= 0 && from[0] <= width && from[1] >= 0 && from[1] <= height;
                    boolean thrown = !peeled && (float) Math.hypot(vx, vy) > dp(700) && (over || vy < -dp(600));
                    float[] fit = over ? fitting(s, from[0], from[1], false) : null;
                    if (thrown)
                        fit = fitting(s, Math.max(0, Math.min(width, from[0] + vx * .08f)), Math.max(0, Math.min(height, from[1] + vy * .08f)), true);
                    if (fit != null) place(s, from, fit, false);
                    else home(s);
                    if (fit != null) {
                        s.flewIn = thrown;
                        if (thrown) {
                            s.x.with(260, .8f);
                            s.y.with(260, .8f);
                        }
                    }
                }
            } else if (holding || handling) {
                s.press.with(380, .62f).to(0);
                s.rotation.value = angle(s.rotation.value);
                s.rotation.target = angle(s.rotation.target);
            } else if (!cancel) {
                if (!fromCard) {
                    ((WalletActivity) fragment).openWalletCollectible(s.nft);
                } else {
                    selected = s;
                    s.scale.velocity += 3;
                    s.z = ++z;
                    s.playOnce = true;
                    s.playStarted = false;
                    s.image.setAllowStartLottieAnimation(true);
                    s.image.startAnimation();
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
            }
        }
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
        if (s != null && (dragging || holding || handling)) changed();
        ownsTouch = pending = dragging = holding = handling = overBin = peeled = hintDragOn = false;
        touched = null;
        bin.to(0);
        binHover.to(0);
        save();
        wake();
    }

    private ArrayList<RectF> obstacles() {
        ArrayList<RectF> result = card.getStickerObstacles();
        float pad = width * .012f;
        for (RectF bounds : result) bounds.inset(-pad, -pad);
        result.get(2).left -= width * .035f;
        return result;
    }

    private static float distanceTo(RectF bounds, float x, float y) {
        return dist(x, y, Math.max(bounds.left, Math.min(bounds.right, x)), Math.max(bounds.top, Math.min(bounds.bottom, y)));
    }

    private float[] fitting(Sticker self, float aimX, float aimY, boolean spread) {
        // Hand placement preserves the exact position and size, including overlaps.
        if (!spread) {
            self.perched = false;
            return new float[]{aimX, aimY, self.user.target};
        }
        ArrayList<RectF> obstacles = obstacles();
        float min = dp(32) / size(), base = self.user.target;
        self.perched = false;
        ArrayList<Sticker> others = new ArrayList<>();
        for (Sticker other : stickers.values())
            if (other != self && other.onCard()) others.add(other);
        boolean hasAim = !Float.isNaN(aimX) && !Float.isNaN(aimY);
        if (!hasAim && self == hintSticker && hintFit != null && others.isEmpty()) return hintFit.clone();
        if (!hasAim) {
            aimX = width * .6f;
            aimY = height * .68f;
        }
        java.util.Random random = new java.util.Random(self.key.hashCode() * 7919L + others.size() * 31L + System.nanoTime() / 400000000L);
        float sizeStep = .05f + .04f * random.nextFloat();
        float balanceX = width * (.5f + .08f * (random.nextFloat() - .5f));
        float balanceY = height * (.55f + .08f * (random.nextFloat() - .5f));
        float best = -Float.MAX_VALUE, edge = min * size() * .5f;
        float[] found = null;
        for (float y = edge; y <= height - edge; y += dp(5)) {
            for (float x = edge; x <= width - edge; x += dp(5)) {
                float edgeRoom = Math.min(Math.min(x, width - x), Math.min(y, height - y));
                float scale = Math.min(base * Math.max(.74f, 1 - sizeStep * others.size()), edgeRoom / (size() * .5f));
                float obstacleRoom = Float.MAX_VALUE;
                for (RectF obstacle : obstacles)
                    obstacleRoom = Math.min(obstacleRoom, distanceTo(obstacle, x, y));
                scale = Math.min(scale, obstacleRoom / (size() * .44f));
                for (Sticker other : others)
                    scale = Math.min(scale,
                            dist(x, y, other.x.target, other.y.target) / (size() * .29f) - other.user.target);
                float edgeGap = edgeRoom - size() * .5f * scale;
                scale *= .9f + .1f * clamp01(edgeGap / dp(14));
                if (scale < min) continue;
                float score;
                if (others.isEmpty()) {
                    float hx = width * .6f + (aimX - width * .6f) * .2f, hy = height * .68f + (aimY - height * .68f) * .2f;
                    score = -.45f * dist(x, y, hx, hy) + 1.1f * scale * size() + random.nextFloat() * dp(5);
                } else {
                    float gap = Float.MAX_VALUE, mx = x * scale * scale, my = y * scale * scale, weight = scale * scale, aligned = 0;
                    for (Sticker other : others) {
                        gap = Math.min(gap, dist(x, y, other.x.target, other.y.target) - size() * .4f * (other.user.target + scale));
                        float area = other.user.target * other.user.target;
                        mx += other.x.target * area;
                        my += other.y.target * area;
                        weight += area;
                        aligned += Math.max(0, 1 - Math.abs(other.x.target - x) / (size() * .12f));
                        aligned += Math.max(0, 1 - Math.abs(other.y.target - y) / (size() * .12f));
                    }
                    float gapScore = gap < 0 ? gap * 2.5f : Math.min(gap, size() * .3f);
                    score = 1.2f * gapScore + .9f * scale * size() + .2f * Math.min(Math.min(edgeRoom, obstacleRoom), size())
                            - .35f * dist(mx / weight, my / weight, balanceX, balanceY) - aligned * size() * .08f
                            - .04f * dist(x, y, width * .5f, height * .55f) + random.nextFloat() * dp(10)
                            - (hasAim ? .04f * dist(x, y, aimX, aimY) : 0);
                }
                if (score > best) {
                    best = score;
                    found = new float[]{x, y, scale};
                }
            }
        }
        return found;
    }

    private void toss(Sticker s) {
        host.showCardFor(2600);
        if (!geometry()) return;
        float[] fit = fitting(s, Float.NaN, Float.NaN, true);
        RectF tile = tile(s);
        if (fit == null || tile == null) {
            WalletGiftCell c = cell(s);
            if (c != null)
                c.animate().rotation(3).setDuration(90).withEndAction(() -> c.animate().rotation(0).setDuration(120).start()).start();
            return;
        }
        s.scale.set(tile.width() * artShare(s) / size());
        s.border.set(0);
        s.lift.set(1);
        place(s, local(tile.centerX(), tile.centerY()), fit, true);
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private void place(Sticker s, float[] from, float[] to, boolean tossed) {
        if (s.added == 0) {
            s.added = ++addedCounter;
            evictOverLimit(s);
        }
        s.state = FLIGHT;
        s.contact = s.ring = s.slapped = false;
        s.tossed = s.flewIn = tossed;
        s.z = ++z;
        s.arcX.set(0);
        s.arcY.set(0);
        s.x.with(tossed ? 150 : 500, tossed ? .82f : 1).set(from[0]).to(to[0]);
        s.y.with(tossed ? 150 : 500, tossed ? .82f : 1).set(from[1]).to(to[1]);
        s.user.to(to[2]);
        s.scale.with(300, .5f).to(1);
        s.lift.with(tossed ? 110 : 150, .9f).to(0);
        s.border.to(1);
        s.press.to(0);
        float dx = to[0] - from[0], dy = to[1] - from[1], len = Math.max(1, (float) Math.hypot(dx, dy));
        s.curlX = len > dp(6) ? dx / len : 0;
        s.curlY = len > dp(6) ? dy / len : 1;
        s.curl.set(.9f).to(0);
        if (tossed) {
            float bow = Math.min(dp(110), len * .32f), side = dx >= 0 ? -1 : 1;
            s.arcX.set(0).velocity = -dy / len * bow * side * 20;
            s.arcY.set(0).velocity = (dx / len * bow * side - bow * .5f) * 20;
        }
        save();
        wake();
    }

    private void evictOverLimit(Sticker placed) {
        ArrayList<Sticker> onCard = new ArrayList<>();
        for (Sticker other : stickers.values())
            if (other != placed && other.onCard()) onCard.add(other);
        if (onCard.size() < MAX_CARD_STICKERS) return;
        onCard.sort(Comparator.comparingLong(a -> a.added));
        for (int i = 0; i <= onCard.size() - MAX_CARD_STICKERS; i++) {
            Sticker oldest = onCard.get(i);
            if (host.isCardShown()) home(oldest);
            else snapHome(oldest);
        }
        changed();
    }

    private void home(Sticker s) {
        s.added = 0;
        if (s.key.equals(styleKey)) clearStyle();
        if (s.onCard()) {
            float[] p = root(s.x.value, s.y.value);
            s.x.set(p[0]);
            s.y.set(p[1]);
        }
        s.state = HOME;
        s.homeT = 0;
        s.homeX = s.x.value;
        s.homeY = s.y.value;
        s.homeScale = s.scale.value * s.user.value;
        s.homeAngle = s.rotation.value;
        s.homeDuration = releaseVelocityY > dp(650) ? .32f : .44f;
        releaseVelocityY = 0;
        // The flight owns the cell's scale from here: its release animation would fight the dip below.
        WalletGiftCell cell = cell(s);
        if (cell != null) {
            cell.animate().cancel();
            cell.takeDrawnScale();
        }
        s.homeCell = cell != null ? cell.getScaleX() : 1;
        RectF tile = tile(s);
        if (tile != null) {
            float dx = tile.centerX() - s.homeX, dy = tile.centerY() - s.homeY, len = (float) Math.hypot(dx, dy);
            s.curlX = len > dp(6) ? dx / len : 0;
            s.curlY = len > dp(6) ? dy / len : 1;
        }
        s.press.set(0);
        s.user.set(1);
        s.z = ++z;
        save();
        wake();
    }

    private void back(Sticker s) {
        s.state = GRID;
        s.added = 0;
        s.scale.set(1);
        s.user.set(1);
        s.rotation.set(0);
        s.border.set(0);
        s.lift.set(0);
        s.curl.set(0);
        WalletGiftCell c = cell(s);
        if (c != null) {
            c.animate().cancel();
            c.setScaleX(1);
            c.setScaleY(1);
        }
    }

    private void startReveal(Sticker style, boolean fade) {
        oldStyle = shownStyle;
        shownStyle = style;
        reveal = 0;
        fadeReveal = fade;
        if (style != null) {
            revealX = style.x.target / width;
            revealY = style.y.target / height;
        }
        wake();
    }

    private void clearStyle() {
        styleKey = pendingStyle = null;
        startReveal(null, true);
        save();
        changed();
    }

    private void save() {
        if (readOnly) return;
        if (width <= 0 || height <= 0) return;
        SharedPreferences.Editor edit = prefs.edit();
        for (Sticker s : stickers.values()) {
            if (s.onCard()) try {
                JSONObject p = new JSONObject();
                p.put("x", s.x.target / width);
                p.put("y", s.y.target / height);
                p.put("r", s.rotation.target);
                p.put("s", s.user.target);
                p.put("q", s.perched);
                edit.putString(s.key, p.toString());
            } catch (Exception ignored) {
            }
            else edit.remove(s.key);
        }
        if (styleKey == null) edit.remove("style");
        else edit.putString("style", styleKey);
        edit.apply();
    }

    private void restore(Sticker s) {
        if (s.restored) return;
        s.restored = true;
        String saved = readOnly ? null : prefs.getString(s.key, null);
        if (serverCard != null) {
            TL_wallet.cardSticker cs = serverSticker(s.key);
            if (cs != null) putOnCard(s, cs.coordinates, perched(saved));
            return;
        }
        if (saved == null) return;
        try {
            JSONObject p = new JSONObject(saved);
            s.user.set((float) p.optDouble("s", 1));
            s.rotation.set((float) p.optDouble("r", 0));
            s.perched = p.optBoolean("q");
            float x = (float) p.getDouble("x") * width, y = (float) p.getDouble("y") * height;
            float[] fit = new float[]{x, y, s.user.target};
            s.x.set(fit[0]);
            s.y.set(fit[1]);
            s.user.set(fit[2]);
            s.state = CARD;
            s.z = ++z;
            s.border.set(1);
            if (s.added == 0) s.added = ++addedCounter;
            if (s.key.equals(styleKey)) {
                shownStyle = s;
                oldStyle = s;
                reveal = 1;
                applyStyle();
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean perched(String saved) {
        if (saved == null) return false;
        try {
            return new JSONObject(saved).optBoolean("q");
        } catch (Exception e) {
            return false;
        }
    }

    private void putOnCard(Sticker s, TL_stories.MediaAreaCoordinates c, boolean perched) {
        s.user.set((float) (c.w / (SHARE * 100)));
        s.rotation.set(angle((float) c.rotation));
        s.perched = perched;
        s.x.set((float) c.x / 100 * width);
        s.y.set((float) c.y / 100 * height);
        s.scale.set(1);
        s.lift.set(0);
        s.curl.set(0);
        s.arcX.set(0);
        s.arcY.set(0);
        s.press.set(0);
        s.state = CARD;
        s.z = ++z;
        s.border.set(1);
        if (s.added == 0) s.added = ++addedCounter;
        if (s.key.equals(styleKey)) {
            shownStyle = s;
            oldStyle = s;
            reveal = 1;
            applyStyle();
        }
    }

    private void loadCard() {
        final int account = fragment.getCurrentAccount();
        final MessagesController controller = MessagesController.getInstance(account);
        final long selfId = UserConfig.getInstance(account).getClientUserId();
        TLRPC.UserFull full = controller.getUserFull(selfId);
        if (full != null) {
            applyCard(full.wallet_card == null ? new TL_wallet.card() : full.wallet_card);
            return;
        }
        controller.loadFullUser(controller.getUser(selfId), 0, true, loaded -> AndroidUtilities.runOnUIThread(() -> {
            if (!disposed && loaded != null)
                applyCard(loaded.wallet_card == null ? new TL_wallet.card() : loaded.wallet_card);
        }));
    }

    private TL_wallet.cardSticker serverSticker(String key) {
        if (serverCard == null || serverCard.stickers == null) return null;
        for (TL_wallet.cardSticker cs : serverCard.stickers)
            if (cs.nft != null && key.equals(cs.nft.address)) return cs;
        return null;
    }

    private void applyCard(TL_wallet.card card) {
        serverCard = card;
        serverSignature = signature(card);
        if (dirty || syncRequestId != 0 || ownsTouch) return;
        final String bg = card.bg_nft_item == null ? null : card.bg_nft_item.address;
        final boolean styleChanged = !TextUtils.equals(bg, styleKey);
        if (styleChanged) {
            styleKey = bg;
            pendingStyle = null;
        }
        if (card.stickers != null) for (TL_wallet.cardSticker cs : card.stickers) {
            if (cs.nft != null && (cs.nft.gift != null)) ensure(cs.nft);
        }
        if (readOnly) {
            TL_wallet.nftItem backdrop = card.bg_nft_item;
            shownStyle = oldStyle = backdrop != null && backdrop.gift != null ? ensure(backdrop) : null;
            reveal = 1;
            applyStyle();
            wake();
            return;
        }
        if (!geometry()) {
            wake();
            return;
        }
        applying = true;
        for (Sticker s : ordered()) {
            if (!s.restored || s.state == DRAG || s.state == HOME || s.state == TRASH) continue;
            TL_wallet.cardSticker cs = serverSticker(s.key);
            if (cs != null) {
                if (!s.onCard() || !coordinates(s).equals(format(cs.coordinates)))
                    putOnCard(s, cs.coordinates, s.perched);
            } else if (s.onCard()) home(s);
        }
        if (card.stickers != null) for (TL_wallet.cardSticker cs : card.stickers) {
            Sticker s = cs.nft == null ? null : stickers.get(cs.nft.address);
            if (s != null && s.state == CARD) s.z = ++z;
        }
        if (styleChanged) {
            Sticker style = styleKey == null ? null : stickers.get(styleKey);
            if (style != null && style.onCard()) startReveal(style, false);
            else startReveal(null, true);
        }
        applying = false;
        save();
        wake();
    }

    private void changed() {
        if (readOnly || applying) return;
        dirty = true;
        AndroidUtilities.cancelRunOnUIThread(sendCard);
        AndroidUtilities.runOnUIThread(sendCard, 1500);
    }

    private void sendCard() {
        if (!dirty) return;
        if (syncRequestId != 0) return; // resent from the response
        if (!disposed && (ownsTouch || !geometry())) {
            AndroidUtilities.runOnUIThread(sendCard, 1000);
            return;
        }
        final String signature = localSignature();
        if (signature.equals(serverSignature)) {
            dirty = false;
            return;
        }
        TL_wallet.setCardStickers req = new TL_wallet.setCardStickers();
        for (Sticker s : ordered()) {
            if (!s.onCard()) continue;
            TL_wallet.inputWalletCardSticker input = new TL_wallet.inputWalletCardSticker();
            input.nft_address = s.key;
            input.coordinates = toCoordinates(s);
            req.stickers.add(input);
            if (s.key.equals(styleKey) && colorAllowed(s)) req.bg_nft_address = s.key;
        }
        dirty = false;
        final int account = fragment.getCurrentAccount();
        final TL_wallet.card previous = serverCard;
        updateUserFull(account, localCard(req));
        syncRequestId = ConnectionsManager.getInstance(account).sendRequestTyped(req, AndroidUtilities::runOnUIThread, (result, error) -> {
            syncRequestId = 0;
            if (result != null) {
                updateUserFull(account, result);
                if (disposed) return;
                if (dirty) {
                    serverCard = result;
                    serverSignature = signature(result);
                    sendCard();
                } else applyCard(result);
            } else {
                if (previous != null) updateUserFull(account, previous);
                if (disposed) return;
                if (error != null) BulletinFactory.of(fragment).showForError(error);
                dirty = false;
                if (serverCard != null) applyCard(serverCard);
            }
        });
    }

    private TL_wallet.card localCard(TL_wallet.setCardStickers req) {
        TL_wallet.card card = new TL_wallet.card();
        if (serverCard != null) {
            card.address = serverCard.address;
            card.balance = serverCard.balance;
        }
        for (TL_wallet.inputWalletCardSticker input : req.stickers) {
            Sticker s = stickers.get(input.nft_address);
            if (s == null || s.nft == null) continue;
            TL_wallet.cardSticker cs = new TL_wallet.cardSticker();
            cs.nft = s.nft;
            cs.coordinates = input.coordinates;
            card.stickers.add(cs);
            if (input.nft_address.equals(req.bg_nft_address)) card.bg_nft_item = s.nft;
        }
        return card;
    }

    private static void updateUserFull(int account, TL_wallet.card card) {
        long selfId = UserConfig.getInstance(account).getClientUserId();
        TLRPC.UserFull full = MessagesController.getInstance(account).getUserFull(selfId);
        if (full == null) return;
        full.wallet_card = card;
        MessagesStorage.getInstance(account).updateUserInfo(full, false);
    }

    private TL_stories.MediaAreaCoordinates toCoordinates(Sticker s) {
        TL_stories.TL_mediaAreaCoordinates c = new TL_stories.TL_mediaAreaCoordinates();
        float n = size() * s.user.target;
        c.x = round(s.x.target / width * 100);
        c.y = round(s.y.target / height * 100);
        c.w = round(n / width * 100);
        c.h = round(n / height * 100);
        float r = s.rotation.target % 360;
        if (r < 0) r += 360;
        c.rotation = round(r);
        return c;
    }

    private static double round(float v) {
        return Math.round(v * 100) / 100.0;
    }

    private String coordinates(Sticker s) {
        return format(toCoordinates(s));
    }

    private static String format(TL_stories.MediaAreaCoordinates c) {
        if (c == null) return "";
        return String.format(Locale.US, "%.2f,%.2f,%.2f,%.2f", c.x, c.y, c.w, c.rotation % 360);
    }

    private String localSignature() {
        StringBuilder sb = new StringBuilder();
        String bg = null;
        for (Sticker s : ordered())
            if (s.onCard()) {
                sb.append(s.key).append(':').append(coordinates(s)).append(';');
                if (s.key.equals(styleKey) && colorAllowed(s)) bg = s.key;
            }
        return sb.append("bg:").append(bg).toString();
    }

    private static String signature(TL_wallet.card card) {
        StringBuilder sb = new StringBuilder();
        if (card.stickers != null) for (TL_wallet.cardSticker cs : card.stickers)
            if (cs.nft != null)
                sb.append(cs.nft.address).append(':').append(format(cs.coordinates)).append(';');
        return sb.append("bg:").append(card.bg_nft_item == null ? null : card.bg_nft_item.address).toString();
    }

    private void applyStyle() {
        float[] from = WalletGiftPalette.of(oldStyle == null ? null : oldStyle.backdrop);
        float[] to = WalletGiftPalette.of(shownStyle == null ? null : shownStyle.backdrop);
        float p = fadeReveal ? reveal * reveal * (3 - 2 * reveal) : 1 - (float) Math.pow(1 - reveal, 4);
        float[] style = new float[18];
        float[] transition = {from[0], from[1], from[2], to[0], to[1], to[2], revealX, revealY, p, fadeReveal ? 1 : 0};
        System.arraycopy(transition, 0, style, 0, 10);
        WalletGiftPalette.putBackdrop(style, 0, oldStyle == null ? null : oldStyle.backdrop);
        WalletGiftPalette.putBackdrop(style, 1, shownStyle == null ? null : shownStyle.backdrop);
        card.setStickerStyle(style);
    }

    private void wake() {
        if (!disposed && isAttachedToWindow()) {
            removeCallbacks(tick);
            postOnAnimation(tick);
        }
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (disposed || !isAttachedToWindow()) return;
            long now = SystemClock.uptimeMillis();
            float dt = lastFrame == 0 ? 1f / 60 : Math.min(.032f, (now - lastFrame) / 1000f);
            lastFrame = now;
            if (!geometry()) return;
            boolean moving = false;
            for (Sticker s : stickers.values()) {
                restore(s);
                if (s.onCard()) s.bitmap();
                for (Spring spring : s.springs) moving |= spring.step(dt);
                if (s.state == FLIGHT) {
                    float d = dist(s.x.value + s.arcX.value, s.y.value + s.arcY.value, s.x.target, s.y.target);
                    if (!s.ring && s.lift.value < .36f && d < dp(24)) {
                        s.ring = true;
                        ripples.add(new Ripple(s.x.target, s.y.target));
                    }
                    if (!s.contact && s.lift.value < .22f && d < dp(12)) {
                        s.contact = true;
                        s.impact.set(1).to(0);
                        squash.with(s.flewIn ? 420 : 260, s.flewIn ? .38f : .85f);
                        squash.velocity -= s.flewIn ? .75f : .28f;
                        float push = s.flewIn ? 3.4f : 1.4f;
                        kickH.velocity += (s.x.target / width - .5f) * 2 * push;
                        kickV.velocity += (s.y.target / height - .5f) * 2 * push;
                        if (s.flewIn) spawnSparks(s.x.target, s.y.target);
                        if (s.key.equals(pendingStyle)) {
                            startReveal(s, false);
                            pendingStyle = null;
                        }
                    }
                    if (!s.slapped && s.curl.value < .3f) {
                        s.slapped = true;
                        s.scale.velocity -= 1.4f;
                        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    }
                    if (s.slapped && d < .5f && Math.abs(s.scale.value - 1) < .004f && Math.abs(s.scale.velocity) < .02f) {
                        s.state = CARD;
                        s.x.set(s.x.target);
                        s.y.set(s.y.target);
                    }
                    moving = true;
                } else if (s.state == HOME) {
                    s.homeT = Math.min(1, s.homeT + dt / s.homeDuration);
                    float t = s.homeT, e = 1 - (1 - t) * (1 - t);
                    WalletGiftCell c = cell(s);
                    float settle = clamp01(t / .5f), cellScale = c != null ? lerp(s.homeCell, 1, settle * settle * (3 - 2 * settle)) : 1;
                    if (c != null) {
                        c.setScaleX(cellScale);
                        c.setScaleY(cellScale);
                    }
                    RectF tile = tile(s);
                    if (tile == null) s.scale.set(s.homeScale * (1 - e));
                    else {
                        float arc = (float) Math.sin(Math.PI * e);
                        float bow = Math.min(dp(90), dist(tile.centerX(), tile.centerY(), s.homeX, s.homeY) * .28f);
                        s.x.set(lerp(s.homeX, tile.centerX(), e));
                        s.y.set(lerp(s.homeY, tile.centerY(), e) - 2 * (1 - e) * e * bow);
                        float restScale = tile.width() / cellScale * artShare(s) / size();
                        s.scale.set(lerp(s.homeScale, restScale, e) * (1 + .05f * arc) * lerp(1, cellScale, e));
                        s.lift.set(.8f * arc);
                        s.rotation.set(s.homeAngle + angle(-s.homeAngle) * e);
                        s.border.set(clamp01(1 - (e - .35f) / .4f));
                    }
                    if (t >= 1) {
                        back(s);
                        if (c != null)
                            c.animate().scaleX(.955f).scaleY(.955f).setDuration(70).setInterpolator(null)
                                    .withEndAction(() -> c.animate().scaleX(1).scaleY(1).setDuration(260).setInterpolator(new android.view.animation.OvershootInterpolator(2)).start()).start();
                    }
                    moving = true;
                } else if (s.state == TRASH) {
                    if (s.scale.value < .03f) back(s);
                    moving = true;
                }
                if (s.onCard()) s.ribbonOn = true;
                else if (s.state == GRID) s.ribbonOn = false;
                if (s.image.isLottieRunning()) {
                    RLottieDrawable a = s.image.getLottieAnimation();
                    int frame = a == null ? 0 : a.getCurrentFrame();
                    if (frame >= 2) s.playStarted = true;
                    boolean once = s.playOnce && !s.playStarted && s.state == CARD;
                    if (s.press.target == 0 && !once && a != null && frame < 2) {
                        s.image.stopAnimation();
                        s.image.setAllowStartLottieAnimation(false);
                        s.playOnce = false;
                    }
                    moving = true;
                }
            }
            moving |= stepHint(dt);
            for (int i = sparks.size() - 1; i >= 0; i--) {
                Spark sp = sparks.get(i);
                sp.age += dt;
                float drag = (float) Math.exp(-2.4f * dt);
                sp.vx *= drag;
                sp.vy *= drag;
                sp.x += sp.vx * dt;
                sp.y += sp.vy * dt;
                sp.rot += 90 * dt;
                if (sp.age >= sp.life) sparks.remove(i);
            }
            if (!sparks.isEmpty()) moving = true;
            for (int i = ripples.size() - 1; i >= 0; i--) {
                Ripple r = ripples.get(i);
                r.age += dt;
                if (r.age > .42f) ripples.remove(i);
            }
            if (reveal < 1) {
                reveal = Math.min(1, reveal + dt / (fadeReveal ? .45f : 1.2f));
                applyStyle();
                moving = true;
            }
            boolean trashing = false;
            for (Sticker s : stickers.values()) if (s.state == TRASH) trashing = true;
            bin.to((dragging && peeled && bin.target > 0) || trashing || trashClosing ? 1 : 0);
            binHover.to(overBin || trashing || trashClosing ? 1 : 0);
            if (trashClosing) {
                trashFrame.set(Math.min(66, trashFrame.value + dt * 60));
                if (trashFrame.value >= 66) {
                    trashClosing = false;
                    trashFrame.set(0);
                }
                moving = true;
            } else {
                trashFrame.to(overBin ? 28 : 0);
                moving |= trashFrame.step(dt);
            }
            moving |= bin.step(dt) | binHover.step(dt) | squash.step(dt) | kickH.step(dt) | kickV.step(dt);
            card.setStickerImpact(squash.value);
            card.setStickerKick(Math.max(-1, Math.min(1, kickH.value)), Math.max(-1, Math.min(1, kickV.value)));
            rotX = card.getCardRotationX();
            rotY = card.getCardRotationY();
            for (WalletGiftCell c : cells) {
                Sticker s = stickers.get(c.nft.address);
                if (s != null) updateCell(c, s);
            }
            invalidate();
            cardLayer.invalidate();
            patternLayer.invalidate();
            if (moving || !ripples.isEmpty() || ownsTouch) postOnAnimation(this);
            else lastFrame = 0;
        }
    };

    private static final class Ripple {
        float x, y, age;

        Ripple(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    private static final class Spark {
        float x, y, vx, vy, size, life, rot, age;
    }

    private void spawnSparks(float x, float y) {
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 14; i++) {
            Spark sp = new Spark();
            float a = (float) (i / 14f * Math.PI * 2) + random.nextFloat() * .35f, speed = dp(265) + random.nextFloat() * dp(180);
            sp.x = x;
            sp.y = y;
            sp.vx = (float) Math.cos(a) * speed;
            sp.vy = (float) Math.sin(a) * speed;
            sp.size = dp(.8f) + random.nextFloat() * dp(1.1f);
            sp.life = .7f + random.nextFloat() * .35f;
            sp.rot = random.nextFloat() * 360;
            sparks.add(sp);
        }
    }

    private ArrayList<Sticker> ordered() {
        ArrayList<Sticker> list = new ArrayList<>(stickers.values());
        list.sort(Comparator.comparingLong(a -> a.z));
        return list;
    }

    private void drawOnCard(Canvas canvas) {
        if (width <= 0) return;
        paint.setShader(null);
        paint.setColorFilter(null);
        for (Spark sp : sparks) {
            float t = sp.age / sp.life;
            float pop = t < .1f ? t / .1f : (1 - (t - .1f) / .9f) * (1 - (t - .1f) / .9f);
            float r = sp.size * (.6f + .4f * pop);
            paint.setColor(Color.WHITE);
            paint.setAlpha((int) (130 * clamp01(pop)));
            canvas.save();
            canvas.translate(sp.x, sp.y);
            canvas.rotate(sp.rot);
            sparkPath.rewind();
            sparkPath.moveTo(0, -r * 2);
            sparkPath.lineTo(r * .35f, -r * .35f);
            sparkPath.lineTo(r * 2, 0);
            sparkPath.lineTo(r * .35f, r * .35f);
            sparkPath.lineTo(0, r * 2);
            sparkPath.lineTo(-r * .35f, r * .35f);
            sparkPath.lineTo(-r * 2, 0);
            sparkPath.lineTo(-r * .35f, -r * .35f);
            sparkPath.close();
            canvas.drawPath(sparkPath, paint);
            canvas.restore();
        }
        paint.setAlpha(255);
        for (Ripple r : ripples) {
            float t = clamp01(r.age / .42f), e = 1 - (1 - t) * (1 - t) * (1 - t);
            line.setColor(Color.WHITE);
            line.setAlpha((int) (90 * (1 - t)));
            line.setStrokeWidth(dp(2.4f) * (1 - t));
            canvas.drawCircle(r.x, r.y, size() * (.42f + .43f * e), line);
        }
        drawHintOutline(canvas);
        float shift = (rotY + rotX * .6f) / 10f;
        sheenMatrix.setScale(width * 1.5f, 1);
        sheenMatrix.postRotate(-24);
        sheenMatrix.postTranslate(-width * .25f + shift * width * .45f, 0);
        sheen.setLocalMatrix(sheenMatrix);
        // Balance and holder stay above this layer (see attach) and get a shadow instead of clipping stickers.
        final ArrayList<RectF> obstacles = card.getStickerObstacles();
        final RectF balanceText = obstacles.get(0), holderText = obstacles.get(1), numberMask = obstacles.get(2), qrMask = obstacles.get(3), usdText = obstacles.get(4), diamondIcon = obstacles.get(5);
        float balanceShadow = 0, holderShadow = 0, diamondFallback = 0, numberShadow = 0;
        for (Sticker s : ordered())
            if (s.state == CARD || (s.state == FLIGHT && (!s.tossed || s.lift.value <= .3f))) {
                Bitmap bitmap = s.bitmap();
                float press = s.press.value;
                float appear = clamp01(s.appear.value);
                float n = size() * s.scale.value * s.user.value * (1 + HOLD_GROW * press * (1 - .9f * s.realSize.value)) * (.8f + .2f * appear);
                if (bitmap == null || n < 1) continue;
                float lift = Math.max(s.lift.value, press * .7f);
                float depth = dp(1.5f) + lift * dp(9);
                float px = (float) Math.sin(Math.toRadians(rotY)) * depth * 2.2f, py = (float) Math.sin(Math.toRadians(rotX)) * depth * 2.2f;
                float x = s.x.value + s.arcX.value + px, y = s.y.value + s.arcY.value + py - lift * dp(4);
                float sx = s.x.value - px * .3f, sy = s.y.value - py * .3f + depth * .22f;
                float presence = clamp01(lift * 3 + press * 1.5f + s.impact.value + s.curl.value);
                stickerBounds.set(Math.min(x, sx) - n, Math.min(y, sy) - n, Math.max(x, sx) + n, Math.max(y, sy) + n);
                boolean overQr = RectF.intersects(stickerBounds, qrMask);
                boolean masked = overQr;
                rect.set(x - n * .4f, y - n * .4f, x + n * .4f, y + n * .4f);
                balanceShadow = Math.max(balanceShadow, appear * Math.max(coverage(rect, balanceText), coverage(rect, usdText)));
                holderShadow = Math.max(holderShadow, appear * coverage(rect, holderText));
                diamondFallback = Math.max(diamondFallback, appear * coverage(rect, diamondIcon));
                numberShadow = Math.max(numberShadow, appear * coverage(rect, numberMask));
                int maskLayer = masked ? canvas.saveLayer(stickerBounds, null) : -1;
                canvas.save();
                canvas.rotate(s.rotation.value, sx, sy);
                drawShadow(canvas, s, sx, sy, n * (.97f + lift * .08f + s.impact.value * .07f), Math.max(lift, s.curl.value * .5f), s.impact.value, presence * appear);
                canvas.restore();
                int layer = canvas.saveLayer(x - n, y - n, x + n, y + n, null);
                paint.setAlpha((int) (255 * appear));
                if (s.curl.value > .004f) drawCurl(canvas, s, bitmap, x, y, n, s.curl.value, 1);
                else {
                    canvas.save();
                    canvas.rotate(s.rotation.value, x, y);
                    rect.set(x - n / 2, y - n / 2, x + n / 2, y + n / 2);
                    canvas.drawBitmap(bitmap, null, rect, paint);
                    canvas.restore();
                }
                paint.setAlpha(255);
                canvas.drawRect(x - n, y - n, x + n, y + n, sheenPaint);
                canvas.restoreToCount(layer);
                if (masked) {
                    if (overQr) {
                        card.getQrButtonPath(path, width, height);
                        canvas.drawPath(path, erasePaint);
                    }
                    canvas.restoreToCount(maskLayer);
                }

            }
        drawSelection(canvas);
        card.setStickerTextShadow(balanceShadow, holderShadow, diamondFallback, numberShadow);
    }

    /** How much of text is covered by art, eased so a partial overlap already gives the full shadow. */
    private static float coverage(RectF art, RectF text) {
        float w = Math.min(art.right, text.right) - Math.max(art.left, text.left);
        float h = Math.min(art.bottom, text.bottom) - Math.max(art.top, text.top);
        if (w <= 0 || h <= 0 || text.width() <= 0 || text.height() <= 0) return 0;
        return clamp01(w * h / (text.width() * text.height()) * 4);
    }

    private final RectF stickerBounds = new RectF();
    private final Paint erasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    {
        erasePaint.setColor(Color.BLACK);
        erasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }


    @Override
    protected void onDraw(Canvas canvas) {
        if (!geometry()) return;
        drawBin(canvas);
        paint.setShader(null);
        paint.setColorFilter(null);
        paint.setAlpha(255);
        for (Sticker s : ordered()) {
            float x = s.x.value, y = s.y.value, n = size() * s.scale.value * s.user.value, lift = s.lift.value;
            if (s.state == FLIGHT && s.tossed && lift > .3f) {
                float[] p = root(x + s.arcX.value, y + s.arcY.value);
                x = p[0];
                y = p[1];
                n *= 1 + lift * .15f;
            } else if (s.state != DRAG && s.state != HOME && s.state != TRASH) continue;
            Bitmap bitmap = s.bitmap();
            if (bitmap == null || n < 1) continue;
            canvas.save();
            canvas.rotate(s.rotation.value, x, y);
            drawShadow(canvas, s, x, y + dp(1.5f) + lift * dp(5), n * (1 + lift * .04f), lift, 0, 1);
            canvas.restore();
            float b = clamp01(s.border.value);
            if (s.state == HOME && s.homeT > .6f && cell(s) != null) {
                float k = clamp01((s.homeT - .6f) / .4f);
                float c = .5f * .9f * (1 - k * k * (3 - 2 * k));
                float bend = .55f * (1 - .6f * clamp01(-s.curlY));
                if (b < 1) {
                    paint.setAlpha((int) (255 * (1 - b)));
                    drawCurl(canvas, s, s.plain, x, y, n, c, bend);
                }
                if (b > 0) {
                    paint.setAlpha((int) (255 * b));
                    drawCurl(canvas, s, bitmap, x, y, n, c, bend);
                }
                paint.setAlpha(255);
                continue;
            }
            canvas.save();
            canvas.rotate(s.rotation.value, x, y);
            rect.set(x - n / 2, y - n / 2, x + n / 2, y + n / 2);
            if (b < 1) {
                paint.setAlpha((int) (255 * (1 - b)));
                canvas.drawBitmap(s.plain, null, rect, paint);
            }
            if (b > 0) {
                paint.setAlpha((int) (255 * b));
                canvas.drawBitmap(bitmap, null, rect, paint);
            }
            paint.setAlpha(255);
            canvas.restore();
        }
    }

    private void drawShadow(Canvas canvas, Sticker s, float cx, float cy, float size, float lift, float impact, float presence) {
        if (presence <= .01f || s.shadow == null) return;
        float n = size * 176f / 128f * (1 + lift * .05f);
        rect.set(cx - n / 2, cy - n / 2, cx + n / 2, cy + n / 2);
        int alpha = paint.getAlpha();
        paint.setColor(0xFF0B2E5C);
        paint.setAlpha((int) (255 * clamp01(.16f + (.11f - .16f) * lift + .12f * impact) * clamp01(s.border.value) * presence));
        canvas.drawBitmap(s.shadow, null, rect, paint);
        paint.setColor(Color.WHITE);
        paint.setAlpha(alpha);
    }

    private void drawCurl(Canvas canvas, Sticker s, Bitmap bitmap, float cx, float cy, float n, float curl, float bendScale) {
        drawCurl(canvas, bitmap, cx, cy, n, curl, bendScale, s.rotation.value, s.curlX, s.curlY);
    }

    private void drawCurl(Canvas canvas, Bitmap bitmap, float cx, float cy, float n, float curl, float bendScale, float rotation, float curlX, float curlY) {
        float contact = -n / 2 + n * curl, r = n * .3f, eye = n * 3.2f, maxAngle = .72f * bendScale, arc = r * maxAngle;
        float cos = (float) Math.cos(Math.toRadians(rotation)), sin = (float) Math.sin(Math.toRadians(rotation));
        int k = 0;
        for (int row = 0; row <= MESH; row++)
            for (int col = 0; col <= MESH; col++) {
                float ax = (col / (float) MESH - .5f) * n, ay = (row / (float) MESH - .5f) * n;
                float lx = ax * cos - ay * sin, ly = ax * sin + ay * cos, p = lx * curlX + ly * curlY, q = -lx * curlY + ly * curlX;
                float along = p, z = 0, shade = 1, d = contact - p;
                if (d > 0) {
                    float phi = Math.min(maxAngle, d / r), rest = Math.max(0, d - arc);
                    along = contact - r * (float) Math.sin(phi) - rest * (float) Math.cos(phi);
                    z = r * (1 - (float) Math.cos(phi)) + rest * (float) Math.sin(phi);
                    shade = .62f + .38f * (float) Math.cos(phi);
                }
                float perspective = eye / (eye - z);
                mesh[k * 2] = cx + (along * curlX - q * curlY) * perspective;
                mesh[k * 2 + 1] = cy + (along * curlY + q * curlX) * perspective - z * .15f;
                int c = (int) (255 * shade);
                meshColors[k++] = Color.rgb(c, c, c);
            }
        canvas.drawBitmapMesh(bitmap, MESH, MESH, mesh, 0, meshColors, 0, paint);
    }

    private Sticker hintCandidate() {
        if (hintSticker != null && hintSticker.state == GRID) {
            WalletGiftCell c = cell(hintSticker);
            if (c != null && fullyShown(c) && allowed(hintSticker)) return hintSticker;
        }
        Sticker best = null;
        float bestTop = 0, bestLeft = 0;
        for (WalletGiftCell c : cells) {
            Sticker s = stickers.get(c.nft.address);
            if (s == null || s.state != GRID || !allowed(s) || !c.isAttachedToWindow() || !fullyShown(c))
                continue;
            RectF r = tile(s);
            if (r == null) continue;
            if (best == null || r.top < bestTop - 1 || (Math.abs(r.top - bestTop) <= 1 && r.left < bestLeft)) {
                best = s;
                bestTop = r.top;
                bestLeft = r.left;
            }
        }
        return best;
    }

    private final Rect visible = new Rect();

    private boolean fullyShown(WalletGiftCell c) {
        return c.isShown() && c.getGlobalVisibleRect(visible) && visible.width() >= c.getWidth() - 1 && visible.height() >= c.getHeight() - 1;
    }

    private boolean stepHint(float dt) {
        if (readOnly) return false;
        if (hintOn) {
            Sticker candidate = hintCandidate();
            if (candidate != null && candidate != hintSticker) {
                hintSticker = candidate;
                hintFit = null;
                hintClock = 0;
            }
        }
        final Sticker s = hintSticker;
        final WalletGiftCell cell = s == null ? null : cell(s);
        final boolean inView = hintOn && s != null && s.state == GRID && allowed(s) && cell != null && fullyShown(cell) && !menuOpen && !ownsTouch;
        if (s != null && (inView || hintDragOn)) {
            s.bitmap();
            if (hintFit == null && s.art != null) {
                hintFit = fitting(s, width * .6f, height * .68f, true);
                s.perched = false;
            }
        }
        hintDemo.to(inView ? 1 : 0);
        outlineShow.to((inView || hintDragOn) && hintFit != null && outlineOf(s) != null ? 1 : 0);
        boolean moving = hintDemo.step(dt) | outlineShow.step(dt);
        final boolean active = hintDemo.value > .002f || outlineShow.value > .002f;
        if (active) hintClock += dt;
        else {
            hintClock = 0;
            if (!hintOn && !hintDragOn) hintFit = null;
        }

        final WalletGiftCell shown = s != null && s.state == GRID && s.plain != null && hintDemo.value > .002f && posed() ? cell : null;
        if (demoCell != null && demoCell != shown) demoCell.setHintDemo(false);
        demoCell = shown;
        if (shown != null) shown.setHintDemo(true);

        updateHintBubbles(inView && hintDemo.value > .5f ? cell : null);
        return moving || active;
    }

    private float[] hintPose(float t) {
        hintPose[0] = (ease(t, .45f, .82f) - ease(t, 1.3f, 1.72f)) * .9f;
        hintPose[1] = ease(t, .6f, .92f) - ease(t, 1.18f, 1.46f) + (float) Math.sin(clamp01((t - .92f) / .26f) * Math.PI) * .035f;
        hintPose[2] = ease(t, 1.58f, 1.7f) - ease(t, 1.7f, 1.92f);
        return hintPose;
    }

    private static float ease(float t, float from, float to) {
        float x = clamp01((t - from) / (to - from));
        return x * x * (3 - 2 * x);
    }

    private boolean posed() {
        float[] pose = hintPose(hintClock % HINT_LOOP);
        return Math.max(pose[0], Math.max(pose[1], pose[2])) * clamp01(hintDemo.value) >= .003f;
    }

    void drawHintDemo(Canvas canvas, WalletGiftCell cell) {
        final Sticker s = hintSticker;
        if (cell != demoCell || s == null || s.plain == null) return;
        paint.setShader(null);
        paint.setColorFilter(null);
        final float d = clamp01(hintDemo.value);
        final float[] pose = hintPose(hintClock % HINT_LOOP);
        final float curl = pose[0] * d, lift = pose[1] * d, squash = pose[2] * d;
        final View image = cell.imageView;
        final float cx = cell.card.getLeft() + image.getLeft() + image.getWidth() / 2f;
        final float cy = cell.card.getTop() + image.getTop() + image.getHeight() / 2f;
        final float n = image.getWidth() * WalletStickerOutline.SIZE / (WalletStickerOutline.SIZE - 2f * WalletStickerOutline.PADDING)
                * (1 + .07f * lift - .025f * squash);
        if (lift > .01f && s.shadow != null) {
            final float half = n * 176f / 128f / 2;
            final float sy = cy + dp(2) * lift;
            rect.set(cx - half, sy - half, cx + half, sy + half);
            paint.setColor(Color.BLACK);
            paint.setAlpha((int) (60 * lift));
            canvas.drawBitmap(s.shadow, null, rect, paint);
            paint.setColor(Color.WHITE);
        }
        final float art = s.art == null ? 0 : clamp01(lift * 1.4f);
        final float y = cy - dp(5) * lift, rotation = -5 * lift;
        if (art < .99f) {
            paint.setAlpha(255);
            drawCurl(canvas, s.plain, cx, y, n, curl, 1, rotation, .24f, .97f);
        }
        if (art > .01f) {
            paint.setAlpha((int) (255 * art));
            drawCurl(canvas, s.art, cx, y, n, curl, 1, rotation, .24f, .97f);
        }
        paint.setAlpha(255);
    }

    private void drawHintOutline(Canvas canvas) {
        final float show = clamp01(outlineShow.value);
        if (show < .01f || !hintOutline()) return;
        outlineFill.setAlpha((int) (22 * show));
        canvas.drawPath(outlinePath, outlineFill);
        final float dash = dp(6), gap = dp(4.5f), period = dash + gap;
        outlinePaint.setPathEffect(new DashPathEffect(new float[]{dash, gap}, period - hintClock * dp(18) % period));
        outlinePaint.setStrokeWidth(dp(3.2f));
        outlinePaint.setColor(Color.argb((int) (40 * show), 0, 18, 48));
        canvas.drawPath(outlinePath, outlinePaint);
        outlinePaint.setStrokeWidth(dp(1.8f));
        outlinePaint.setColor(Color.argb((int) (180 * show), 255, 255, 255));
        canvas.drawPath(outlinePath, outlinePaint);
    }

    private boolean hintOutline() {
        final Path outline = hintFit == null ? null : outlineOf(hintSticker);
        if (outline == null) return false;
        final float k = size() * hintFit[2];
        outlineMatrix.setScale(k, k);
        outlineMatrix.postTranslate(hintFit[0], hintFit[1]);
        outline.transform(outlineMatrix, outlinePath);
        return true;
    }

    private Path outlineOf(Sticker s) {
        if (s == null || s.outline != null || s.art == null) return s == null ? null : s.outline;
        final int n = 128;
        final Bitmap small = Bitmap.createScaledBitmap(s.art, n, n, true);
        final int[] pixels = new int[n * n];
        small.getPixels(pixels, 0, n, 0, 0, n, n);
        if (small != s.art) small.recycle();
        final boolean[] inside = new boolean[n * n];
        int start = -1;
        for (int i = 0; i < n * n; i++) {
            inside[i] = pixels[i] >>> 24 >= 110;
            if (start < 0 && inside[i]) start = i;
        }
        if (start < 0) return null;
        final int[] dx = {-1, -1, 0, 1, 1, 1, 0, -1}, dy = {0, -1, -1, -1, 0, 1, 1, 1};
        final int sx = start % n, sy = start / n;
        int x = sx, y = sy, bx = sx - 1, by = sy, count = 0;
        float[] points = new float[512];
        for (int step = 0; step < 20000; step++) {
            if (count * 2 + 2 > points.length)
                points = java.util.Arrays.copyOf(points, points.length * 2);
            points[count * 2] = x + .5f;
            points[count * 2 + 1] = y + .5f;
            count++;
            int from = 0;
            while (from < 8 && (dx[from] != bx - x || dy[from] != by - y)) from++;
            boolean found = false;
            for (int i = 1; i <= 8; i++) {
                final int d = (from + i) % 8, nx = x + dx[d], ny = y + dy[d];
                if (nx < 0 || ny < 0 || nx >= n || ny >= n || !inside[ny * n + nx]) continue;
                final int back = (d + 7) % 8;
                bx = x + dx[back];
                by = y + dy[back];
                x = nx;
                y = ny;
                found = true;
                break;
            }
            if (!found || x == sx && y == sy) break;
        }
        final int m = (count + 1) / 2;
        if (m < 3) return null;
        final float[] smooth = new float[m * 2];
        for (int i = 0; i < count; i += 2) {
            float sumX = 0, sumY = 0;
            for (int j = -3; j <= 3; j++) {
                final int k = ((i + j) % count + count) % count;
                sumX += points[k * 2];
                sumY += points[k * 2 + 1];
            }
            smooth[i] = sumX / 7 / n - .5f;
            smooth[i + 1] = sumY / 7 / n - .5f;
        }
        final Path path = new Path();
        path.moveTo((smooth[0] + smooth[2]) / 2, (smooth[1] + smooth[3]) / 2);
        for (int i = 1; i <= m; i++) {
            final int a = i % m, b = (i + 1) % m;
            path.quadTo(smooth[a * 2], smooth[a * 2 + 1], (smooth[a * 2] + smooth[b * 2]) / 2, (smooth[a * 2 + 1] + smooth[b * 2 + 1]) / 2);
        }
        path.close();
        return s.outline = path;
    }

    private HintView2 hintBubble(int text, boolean closeButton) {
        final HintView2 view = new HintView2(getContext(), HintView2.DIRECTION_BOTTOM)
                .setMultilineText(true)
                .setInnerPadding(11, 6, closeButton ? 15 : 11, 7)
                .setCloseButton(closeButton)
                .setTextAlign(Layout.Alignment.ALIGN_NORMAL)
                .setRounding(12)
                .setDuration(-1);
        final CharSequence string = LocaleController.getString(text);
        view.setMaxWidthPx(HintView2.cutInFancyHalf(string, view.getTextPaint()));
        view.setText(string);
        view.setPadding(dp(16), 0, dp(16), 0);
        view.setTranslationZ(getTranslationZ() + 1);
        ((ViewGroup) getParent()).addView(view, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 150, Gravity.TOP));
        return view;
    }

    private static void pointHint(HintView2 view, float x, float y) {
        direct(view, HintView2.DIRECTION_BOTTOM);
        view.setJointPx(0, x - view.getPaddingLeft());
        view.setTranslationY(y - view.getLayoutParams().height);
        view.invalidate();
    }

    private static void pointHintBelow(HintView2 view, float x, float y) {
        direct(view, HintView2.DIRECTION_TOP);
        view.setJointPx(0, x - view.getPaddingLeft());
        view.setTranslationY(y);
        view.invalidate();
    }

    private static void direct(HintView2 view, int direction) {
        if (view.getTag() instanceof Integer && (Integer) view.getTag() == direction) return;
        view.setTag(direction);
        view.setDirection(direction);
        view.setJointPx(1, 0);
    }

    private void updateHintBubbles(WalletGiftCell cell) {
        if (!(getParent() instanceof ViewGroup)) return;
        final RectF tile = cell == null ? null : tile(hintSticker);
        if (tile != null) {
            if (hintView == null) hintView = hintBubble(R.string.WalletGiftDragHint, true);
            else if (hintWanted && !hintView.shown()) {
                dismissHint();
                return;
            }
            pointHint(hintView, tile.centerX(), tile.top + dp(4));
            if (!hintView.shown()) hintView.show();
            hintWanted = true;
        } else {
            hintWanted = false;
            if (hintView != null && hintView.shown()) hintView.hide();
        }

        if (hintDragOn && outlineShow.value > .5f && host != null && host.isCardShown() && hintOutline()) {
            if (dragTipView == null) {
                dragTipView = hintBubble(R.string.WalletGiftDragHere, false);
            }
            outlinePath.computeBounds(outlineBounds, true);
            final float[] top = root(outlineBounds.centerX(), outlineBounds.top);
            if (top[1] - dp(2) - dp(56) < AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight()) {
                final float[] bottom = root(outlineBounds.centerX(), outlineBounds.bottom);
                pointHintBelow(dragTipView, bottom[0], bottom[1] + dp(2));
            } else {
                pointHint(dragTipView, top[0], top[1] - dp(2));
            }
            if (!dragTipView.shown()) dragTipView.show();
        } else if (dragTipView != null && dragTipView.shown()) {
            dragTipView.hide();
        }
    }

    private void dismissHint() {
        hintOn = hintDragOn = hintWanted = false;
        if (hintView != null && hintView.shown()) hintView.hide();
        if (!readOnly)
            MessagesController.getGlobalMainSettings().edit().putBoolean("walletcardstickerhint", true).apply();
        wake();
    }

    private float selectionSize(Sticker s) {
        return size() * s.scale.value * s.user.value
                * (1 + HOLD_GROW * s.press.value * (1 - .9f * s.realSize.value));
    }

    private Sticker stickerAt(float x, float y) {
        Sticker hit = null;
        for (Sticker s : stickers.values()) {
            if (s.state == CARD && dist(x, y, s.x.value, s.y.value) < selectionSize(s) * .44f
                    && (hit == null || s.z > hit.z)) hit = s;
        }
        return hit;
    }

    private boolean handleAt(float x, float y) {
        Sticker s = selected;
        if (s == null || s.state != CARD) return false;
        float radius = selectionSize(s) * .5f + dp(9);
        double a = Math.toRadians(s.rotation.value);
        for (int side = -1; side <= 1; side += 2) {
            float[] h = root(s.x.value + s.arcX.value + side * radius * (float) Math.cos(a),
                    s.y.value + s.arcY.value + side * radius * (float) Math.sin(a));
            if (dist(x, y, h[0], h[1]) < dp(22)) return true;
        }
        return false;
    }

    private void drawSelection(Canvas canvas) {
        Sticker s = selected;
        if (s == null || s.state != CARD) return;
        float cx = s.x.value + s.arcX.value, cy = s.y.value + s.arcY.value;
        float r = selectionSize(s) * .5f + dp(9);
        selectionDash.setColor(Color.WHITE);
        selectionDash.setStyle(Paint.Style.STROKE);
        selectionDash.setStrokeCap(Paint.Cap.ROUND);
        selectionDash.setStrokeWidth(dp(2));
        if (selectionDash.getPathEffect() == null)
            selectionDash.setPathEffect(new android.graphics.DashPathEffect(new float[]{dp(10), dp(10)}, .5f));
        selectionDash.setShadowLayer(dp(.75f), 0, 0, 0x50000000);
        selectionStroke.setColor(Color.WHITE);
        selectionStroke.setStyle(Paint.Style.STROKE);
        selectionStroke.setStrokeWidth(dp(2.66f));
        selectionStroke.setShadowLayer(dp(.75f), 0, 0, 0x50000000);
        selectionDot.setColor(0xFF1A9CFF);
        canvas.save();
        canvas.rotate(s.rotation.value, cx, cy);
        canvas.drawArc(cx - r, cy - r, cx + r, cy + r, 0, 180, false, selectionDash);
        canvas.drawArc(cx - r, cy - r, cx + r, cy + r, 180, 180, false, selectionDash);
        for (int side = -1; side <= 1; side += 2) {
            canvas.drawCircle(cx + side * r, cy, dp(5.66f), selectionStroke);
            canvas.drawCircle(cx + side * r, cy, dp(4.66f), selectionDot);
        }
        canvas.restore();
    }

    private float binOpenWidth() {
        text.setTextSize(dp(15));
        return dp(72) + text.measureText(LocaleController.getString(R.string.Remove));
    }

    private float binIconX() {
        return getWidth() / 2f - binOpenWidth() / 2 + dp(30);
    }

    private void drawBin(Canvas canvas) {
        float show = bin.value;
        if (show < .01f) return;
        float hover = Math.max(0, Math.min(1.2f, binHover.value)), h = clamp01(hover);
        float w = dp(56) + (binOpenWidth() - dp(56)) * hover;
        canvas.save();
        canvas.translate(getWidth() / 2f, binY() + (1 - show) * dp(90));
        float scale = .6f + .4f * show;
        canvas.scale(scale, scale);
        paint.setColor(androidx.core.graphics.ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhite, fragment.getResourceProvider()), 0xFFE5484D, h));
        paint.setAlpha((int) (255 * clamp01(show)));
        rect.set(-w / 2, -dp(28), w / 2, dp(28));
        canvas.drawRoundRect(rect, dp(28), dp(28), paint);
        float iconX = (-w / 2 + dp(30)) * h, half = dp(21);
        trashDrawable.setCurrentFrame(Math.round(trashFrame.value), false);
        trashDrawable.setBounds((int) (iconX - half), (int) -half, (int) (iconX + half), (int) half);
        trashPaint.setColorFilter(new PorterDuffColorFilter(
                androidx.core.graphics.ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, fragment.getResourceProvider()), Color.WHITE, h), PorterDuff.Mode.SRC_IN));
        trashPaint.setAlpha((int) (255 * clamp01(show)));
        int layer = canvas.saveLayer(iconX - half, -half, iconX + half, half, trashPaint);
        trashDrawable.draw(canvas);
        canvas.restoreToCount(layer);
        if (h > .02f) {
            text.setColor(Color.WHITE);
            text.setAlpha((int) (255 * clamp01((h - .4f) / .6f)));
            Paint.FontMetrics metrics = text.getFontMetrics();
            canvas.save();
            canvas.clipRect(iconX + dp(22), -dp(28), w / 2 - dp(16), dp(28));
            canvas.drawText(LocaleController.getString(R.string.Remove), iconX + dp(22), -(metrics.ascent + metrics.descent) / 2, text);
            canvas.restore();
        }
        canvas.restore();
    }

    private void drawPatterns(Canvas canvas) {
        if (width <= 0) return;
        float p = fadeReveal ? reveal * reveal * (3 - 2 * reveal) : 1 - (float) Math.pow(1 - reveal, 4);
        float radius = fadeReveal || p >= 1 ? -1 : width * 1.2f * p;
        // The old symbols leave where the reveal circle has passed, so they don't linger under the new ones.
        if (oldStyle != null && p < 1) drawPattern(canvas, oldStyle, fadeReveal ? 1 - p : 1, radius, true);
        if (shownStyle != null) drawPattern(canvas, shownStyle, fadeReveal ? p : 1, radius, false);
    }

    private void drawPattern(Canvas canvas, Sticker s, float alpha, float radius, boolean leaving) {
        Bitmap bitmap = s.pattern.getBitmap();
        if (bitmap == null || s.backdrop == null) return;
        canvas.save();
        WalletCardGeometry.setFacePath(path, width, height);
        canvas.clipPath(path);
        card.getQrButtonPath(path, width, height);
        canvas.clipPath(path, android.graphics.Region.Op.DIFFERENCE);
        if (radius >= 0 && !leaving) {
            path.rewind();
            path.addCircle(revealX * width, revealY * height, radius, Path.Direction.CW);
            canvas.clipPath(path);
        }
        paint.setColorFilter(new PorterDuffColorFilter(s.backdrop.pattern_color | 0xFF000000, PorterDuff.Mode.SRC_IN));
        float scaleX = width / 336f, scaleY = height / 205f;
        for (int col = 0, gx = 16; gx < 336; gx += 33, col++) {
            for (int gy = 16 + (col % 2) * 18; gy < 205; gy += 37) {
                float x = gx * scaleX, y = gy * scaleY, n = 12 * scaleX;
                float edgeDistance = Math.min(Math.min(gx, 336 - gx), Math.min(gy, 205 - gy));
                float edgeFade = clamp01(edgeDistance / 48f);
                edgeFade = edgeFade * edgeFade * (3 - 2 * edgeFade);
                float opacity = alpha * .15f * edgeFade;
                if (radius >= 0) {
                    float t = clamp01((radius - dist(x, y, revealX * width, revealY * height)) / dp(120));
                    if (leaving) {
                        n *= 1 - .8f * t;
                        opacity *= 1 - clamp01(t * 1.6f);
                    } else {
                        n *= .2f + .8f * (t + .28f * (float) Math.sin(Math.PI * t));
                        opacity *= clamp01(t * 1.6f);
                    }
                }
                paint.setAlpha((int) (255 * opacity));
                rect.set(x - n / 2, y - n / 2, x + n / 2, y + n / 2);
                canvas.drawBitmap(bitmap, null, rect, paint);
            }
        }
        paint.setColorFilter(null);
        paint.setAlpha(255);
        canvas.restore();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        wake();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnScrollChangedListener(hintScroll);
        getViewTreeObserver().addOnPreDrawListener(rotationSync);
        for (Sticker s : stickers.values()) {
            s.image.onAttachedToWindow();
            s.pattern.onAttachedToWindow();
        }
        wake();
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnScrollChangedListener(hintScroll);
        getViewTreeObserver().removeOnPreDrawListener(rotationSync);
        removeCallbacks(tick);
        removeCallbacks(longPress);
        for (Sticker s : stickers.values()) {
            s.image.onDetachedFromWindow();
            s.pattern.onDetachedFromWindow();
        }
        lastFrame = 0;
        super.onDetachedFromWindow();
    }

    void dispose() {
        AndroidUtilities.removeFromParent(hintView);
        AndroidUtilities.removeFromParent(dragTipView);
        hintView = dragTipView = null;
        save();
        AndroidUtilities.cancelRunOnUIThread(sendCard);
        selected = null;
        disposed = true;
        if (dirty) sendCard();
        trashDrawable.recycle(false);
        removeCallbacks(tick);
        removeCallbacks(longPress);
        removeCallbacks(pickUp);
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
        for (Sticker s : stickers.values()) {
            s.image.onDetachedFromWindow();
            s.pattern.onDetachedFromWindow();
        }
        cells.clear();
    }

    private static float angle(float a) {
        a %= 360;
        if (a > 180) a -= 360;
        if (a < -180) a += 360;
        return a;
    }
}
