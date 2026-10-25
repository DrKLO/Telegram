package org.telegram.ui.QrShare;

import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionIntroActivity;
import org.telegram.ui.CameraScanActivity;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.OrientationController;
import org.telegram.ui.ProfileActivity;

/**
 * The QR code of a user or a chat (args "user_id" / "chat_id"): the iOS "share QR" screen with "Remove message fee".
 * The own QR also keeps the theme and the free codes on the server, see {@link QrShareServer}.
 */
public class QrShareActivity extends BaseFragment {

    private long userId, chatId;
    private @Nullable OrientationController.Request portraitRequest;
    private QrShareView view;
    private QrShareServer server;
    private FrameLayout frame;
    private QrEmojiPanel emojiView;
    private float panelLeft, panelRight, panelBottom, shapeL, shapeT, shapeRr, shapeB, shapeR;
    private EmojiPickOverlay pickOverlay;
    private boolean light;

    public QrShareActivity(Bundle args) {
        super(args);
        hasOwnBackground = true; // the scene paints everything; outside the reveal circle the profile must show through
    }

    /** boolean: the gifts, the emoji and the blur show during the open already (high-end devices only). */
    public static final String ARG_IMMEDIATE = "immediate";
    private boolean immediateOnOpen;

    /** The own QR. */
    public static QrShareActivity self(int account) {
        final Bundle args = new Bundle();
        args.putLong("user_id", org.telegram.messenger.UserConfig.getInstance(account).getClientUserId());
        return new QrShareActivity(args);
    }

    @Override
    public boolean onFragmentCreate() {
        userId = arguments != null ? arguments.getLong("user_id") : 0;
        chatId = arguments != null ? arguments.getLong("chat_id") : 0;
        immediateOnOpen = arguments != null && arguments.getBoolean(ARG_IMMEDIATE)
            && org.telegram.messenger.SharedConfig.getDevicePerformanceClass() == org.telegram.messenger.SharedConfig.PERFORMANCE_CLASS_HIGH;
        if (userId == 0 && chatId == 0) {
            userId = getUserConfig().getClientUserId();
        }
        return super.onFragmentCreate();
    }

    @Override
    public ActionBar createActionBar(Context context) {
        return null;
    }

    @Override
    public View createView(Context context) {
        // the palette of the last time; a refreshed one is used from the next opening
        final QrBackdrops backdrops = QrBackdrops.get(currentAccount);
        Backdrops.apply(backdrops.getList());
        final java.util.List<TLRPC.TL_shareThemeBackdropOption> shownPalette = backdrops.getList();
        // a changed palette (or the first one ever) is applied at once
        backdrops.load(() -> {
            final java.util.List<TLRPC.TL_shareThemeBackdropOption> list = backdrops.getList();
            if (list == null || list == shownPalette || view == null || isFinished) return;
            view.replacePalette(list);
            if (server != null) server.onPaletteChanged();
        });
        lockPortrait();
        final QrShareServer.Link link = QrShareServer.resolveLink(currentAccount, userId, chatId);
        frame = new FrameLayout(context);
        view = new QrShareView(context, currentAccount, link.url, link.caption, link.captionIsUsername, new QrShareView.Delegate() {
            @Override
            public void onClose() {finishFragment();}

            @Override
            public void onShare(String link) {
                shareLink(link);
            }

            @Override
            public void onScan() {
                onScanClick();
            }

            @Override
            public void onSave() {
                saveToGallery();
            }

            @Override
            public void onBuyGifts() {
                openGiftStore();
            }

            @Override
            public void onPremiumSubscribe() {
                openPremium();
            }

            @Override
            public void onEmojiPanel(boolean visible, float top, float alpha) {
                placeEmojiPanel(visible, top, alpha);
            }

            @Override
            public void onEmojiPanelBounds(float left, float right, float bottom, float shapeLeft, float shapeTop, float shapeRight, float shapeBottom, float radius) {
                panelLeft = left;
                panelRight = right;
                panelBottom = bottom;
                shapeL = shapeLeft;
                shapeT = shapeTop;
                shapeRr = shapeRight;
                shapeB = shapeBottom;
                shapeR = radius;
                if (emojiView != null) {
                    // the glass bounds in keyboard coordinates, for the fades and the scrollbar (the keyboard's own
                    // dispatchDraw does not run when only the grid redraws)
                    final float glassBottom = Math.min(emojiView.getHeight(), shapeB - (emojiView.getTop() + emojiView.getTranslationY()));
                    emojiGlassBottom = pickOverlay != null ? pickOverlay.gridCutY(glassBottom) : glassBottom;
                    if (pickOverlay != null) pickOverlay.setGlassRight(shapeRr - pickOverlay.getLeft());
                    if (emojiGrid != null) emojiGrid.invalidate();
                    emojiView.invalidateOutline();
                }
                if (pickOverlay != null) pickOverlay.invalidateOutline();
            }

            @Override
            public void onFeeMode(int mode) {
                if (server != null) server.onFeeMode(mode);
            }

            @Override
            public void onResetCode() {
                if (server != null) server.onResetCode();
            }

            @Override
            public void onThemeChanged() {
                if (server != null) server.onThemeChanged();
            }

            @Override
            public void onSceneLight(boolean l) {
                // system bars stay white on every backdrop here (also on Gold / Silver / Yellow)
                light = false;
                applyWhiteBars();
            }
        });
        frame.addView(view, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        // the open's first state from the very first frame: no frame of the fully open screen before the transition
        // starts (it was drawn opaque, the open's fade then compiled every draw a second time)
        view.setImmediateOnOpen(immediateOnOpen);
        view.setRevealOpening(true);
        if (opened) {
            // rebuilt in place after the open (the passcode's unlock, a theme change): no transition comes to reveal it
            view.setReveal(1f);
            frame.postDelayed(preloadEmojiRunnable, QrShareView.ITEMS_APPEAR_MS);
        } else if (AndroidUtilities.isTablet()) {
            view.setRevealStatic(); // the system's transition moves it (see onCustomTransitionAnimation)
        } else {
            view.setReveal(0f);
        }
        fragmentView = frame;
        if (server != null) server.release();
        server = new QrShareServer(currentAccount, view, link);
        server.start();
        return frame;
    }

    /** Shares the QR as an image: the backdrop with its pattern and the card with the code that is shown now. */
    public void performShare() {
        if (view != null) {
            shareLink(view.getCurrentLink());
        }
    }

    private void shareLink(@Nullable String link) {
        // no code yet (a contact token on its way): nothing to share
        if (getParentActivity() == null || view == null || view.getCurrentLink() == null) {
            return;
        }
        final android.graphics.Bitmap bitmap = view.renderShareImage();
        final Uri uri = bitmap != null ? AndroidUtilities.getBitmapShareUri(bitmap, "qr_tmp.jpg", android.graphics.Bitmap.CompressFormat.JPEG) : null;
        final Intent intent = new Intent(Intent.ACTION_SEND);
        if (uri != null) {
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else if (!TextUtils.isEmpty(link)) {
            // no image (not laid out yet): the link as text
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, link);
        } else {
            return;
        }
        try {
            getParentActivity().startActivity(Intent.createChooser(intent, getString(R.string.InviteByQRCode)));
        } catch (ActivityNotFoundException e) {
            FileLog.e(e);
        }
    }

    /** The stock Premium screen. */
    private void openPremium() {
        presentFragment(new org.telegram.ui.PremiumPreviewFragment("qr_emoji"));
    }

    /** "Browse Gifts" of the empty gifts layer: the gift store for oneself (collectibles are bought from there). */
    private void openGiftStore() {
        if (getParentActivity() == null) return;
        final org.telegram.ui.Gifts.GiftSheet sheet = new org.telegram.ui.Gifts.GiftSheet(getParentActivity(), currentAccount, getUserConfig().getClientUserId(), null);
        sheet.setBrowseCollectibles();
        sheet.show();
    }

    /* Download */

    /** Waits for the storage permission (Android 6–10), then saves. */
    private boolean saveAfterPermission;

    /** The QR as an image (the same one as shared) to the gallery: Pictures/Telegram before Android 10, MediaStore after. */
    private void saveToGallery() {
        final android.app.Activity activity = getParentActivity();
        if (activity == null || view == null || view.getCurrentLink() == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && (Build.VERSION.SDK_INT <= 28 || org.telegram.messenger.BuildVars.NO_SCOPED_STORAGE)
            && activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            saveAfterPermission = true;
            activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, org.telegram.ui.BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE);
            return;
        }
        final android.graphics.Bitmap bitmap = view.renderShareImage();
        if (bitmap == null) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            // the external cache: MediaController.saveFile does not take files from the app's internal storage
            final java.io.File file = new java.io.File(AndroidUtilities.getCacheDir(), "qr_code_" + System.currentTimeMillis() + ".png");
            boolean written = false;
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
                written = bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
            } catch (Exception e) {
                FileLog.e(e);
            }
            bitmap.recycle();
            if (!written) {
                return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                final android.app.Activity a = getParentActivity();
                if (a == null) return;
                MediaController.saveFile(file.getAbsolutePath(), a, 0, file.getName(), "image/png", uri -> {
                    if (view != null && !isFinished) view.showSavedToast();
                }, false);
            });
        });
    }

    /* Scan */

    private void onScanClick() {
        if (getParentActivity() == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && getParentActivity().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            getParentActivity().requestPermissions(new String[]{Manifest.permission.CAMERA}, ActionIntroActivity.CAMERA_PERMISSION_REQUEST_CODE);
            return;
        }
        openScanner();
    }

    private @Nullable QrScanOverlay scanOverlay;

    /** The scanner grows out of the scan button over the whole screen, like the story camera. */
    private void openScanner() {
        if (scanOverlay != null || view == null || frame == null || getParentActivity() == null) {
            return;
        }
        final android.graphics.RectF from = new android.graphics.RectF(), close = new android.graphics.RectF();
        view.getScanRect(from);
        view.getCloseRect(close);
        if (from.isEmpty()) {
            // not laid out yet: the scanner grows from the middle
            from.set(frame.getWidth() / 2f - AndroidUtilities.dp(20), frame.getHeight() / 2f - AndroidUtilities.dp(20), frame.getWidth() / 2f + AndroidUtilities.dp(20), frame.getHeight() / 2f + AndroidUtilities.dp(20));
        }
        view.setScanHidden(true);
        scanOverlay = new QrScanOverlay(getParentActivity(), from, close, new QrScanOverlay.Listener() {
            @Override
            public void onScanned(String link) {
                if (getParentActivity() != null) {
                    Browser.openUrl(getParentActivity(), link);
                }
            }

            @Override
            public void onClosed(QrScanOverlay overlay) {
                removeScanner(overlay);
            }
        });
        scanOverlay.setButtonLabel(getString(R.string.QrScanQr)); // the "Scan QR" pill
        final QrScanOverlay overlay = scanOverlay;
        overlay.setOnGallery(() -> {
            if (!QrScanOverlay.checkGalleryPermission(getParentActivity())) {
                return;
            }
            overlay.close();
            QrScanOverlay.pickFromGallery(this, QrScanOverlay::isTelegramLink, link -> {
                if (getParentActivity() != null) {
                    Browser.openUrl(getParentActivity(), link);
                }
            });
        });
        frame.addView(scanOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
    }

    private void removeScanner(QrScanOverlay overlay) {
        if (frame != null) {
            frame.removeView(overlay);
        }
        if (scanOverlay == overlay) {
            scanOverlay = null;
        }
        if (view != null) {
            view.setScanHidden(false);
        }
    }

    /** The camera sheet from any screen: a scanned link to a username opens that profile. */
    public static void openCameraScanActivity(BaseFragment fragment) {
        CameraScanActivity.showAsSheet(fragment, false, CameraScanActivity.TYPE_QR, scanDelegate(fragment));
    }

    private static CameraScanActivity.CameraScanActivityDelegate scanDelegate(BaseFragment fragment) {
        final int currentAccount = fragment.getCurrentAccount();
        return new CameraScanActivity.CameraScanActivityDelegate() {
            @Override
            public void didFindQr(String text) {
                final String username = Browser.extractUsername(text);
                if (TextUtils.isEmpty(username)) {
                    showScanError();
                    return;
                }
                MessagesController.getInstance(currentAccount).getUserNameResolver().resolve(username, peerId -> {
                    if (fragment.isFinished) {
                        return;
                    }
                    if (peerId == null || peerId == Long.MAX_VALUE) {
                        showScanError();
                        return;
                    }
                    fragment.presentFragment(ProfileActivity.of(peerId), true);
                });
            }
        };
    }

    private static void showScanError() {
        AndroidUtilities.runOnUIThread(() -> BulletinFactory.global().createSimpleBulletin(
            getString(R.string.ScanQrCode),
            getString(R.string.ErrorOccurred)
        ).show());
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == org.telegram.ui.BasePermissionsActivity.REQUEST_CODE_EXTERNAL_STORAGE) {
            // a refusal is explained by LaunchActivity
            final boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (saveAfterPermission && granted) {
                saveAfterPermission = false;
                saveToGallery();
            }
            saveAfterPermission = false;
            return;
        }
        if (getParentActivity() == null || requestCode != ActionIntroActivity.CAMERA_PERMISSION_REQUEST_CODE) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openScanner();
        } else {
            new AlertDialog.Builder(getParentActivity())
                .setMessage(AndroidUtilities.replaceTags(getString(R.string.QRCodePermissionNoCameraWithHint)))
                .setPositiveButton(getString(R.string.PermissionOpenSettings), (dialogInterface, i) -> {
                    try {
                        final Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + ApplicationLoader.applicationContext.getPackageName()));
                        getParentActivity().startActivity(intent);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                })
                .setNegativeButton(getString(R.string.ContactsPermissionAlertNotNow), null)
                .setTopAnimation(R.raw.permission_request_camera, 72, false, Theme.getColor(Theme.key_dialogTopBackground))
                .show();
        }
    }

    /* Orientation */

    /** The screen asks for portrait; whether it can be applied is up to the controller. */
    private void lockPortrait() {
        final OrientationController orientation = OrientationController.of(getParentActivity());
        if (orientation != null && portraitRequest == null) {
            portraitRequest = orientation.requestPortrait();
        }
    }

    private void unlockPortrait() {
        if (portraitRequest != null) {
            portraitRequest.release();
            portraitRequest = null;
        }
    }

    /** Lays the keyboard out once at the final sheet frame (no reflow during the morph; reused on reopen). */
    private void layoutEmojiFinal() {
        if (emojiView == null || view == null || view.getWidth() == 0) return;
        final int top = (int) view.panelFinalTop();
        final int h = Math.max(1, (int) (view.panelFinalBottom() - top)); // down to the glass edge (the 10dp fade ends on it)
        final int lm = (int) view.panelFinalLeft(), rm = (int) (frame.getWidth() - view.panelFinalRight());
        final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) emojiView.getLayoutParams();
        if (lp.height != h || lp.leftMargin != lm || lp.rightMargin != rm) {
            lp.height = h;
            lp.leftMargin = lm;
            lp.rightMargin = rm;
            emojiView.setLayoutParams(lp);
        }
    }

    /** Creates the keyboard early (invisible), so its first pages are measured and loaded before the sheet ever opens. */
    private static final long OPEN_MS = 860;
    /** The open has finished: a later createView (the views rebuilt in place) shows the screen as it is. */
    private boolean opened;

    /**
     * The views are dropped to be rebuilt by createView (LaunchActivity does it after the passcode's unlock, and on a
     * theme or language change): the old screen's view, panels, scanner and server go with them.
     */
    @Override
    public void clearViews() {
        if (frame != null) frame.removeCallbacks(preloadEmojiRunnable);
        if (scanOverlay != null) {
            scanOverlay.destroy();
            scanOverlay = null;
        }
        if (server != null) {
            server.release();
            server = null;
        }
        super.clearViews();
        if (view != null) {
            view.release();
            view = null;
        }
        frame = null;
        emojiView = null;
        pickOverlay = null;
        emojiGrid = null;
    }
    private final Runnable preloadEmojiRunnable = this::preloadEmoji;

    private void preloadEmoji() {
        // the delayed preload may come after the screen is closed: the keyboard needs the activity
        if (emojiView != null || view == null || isFinished || getParentActivity() == null) return;
        placeEmojiPanel(true, view.panelFinalTop(), 0f);
        emojiView.setVisibility(View.INVISIBLE);
        if (pickOverlay != null) pickOverlay.setVisibility(View.INVISIBLE);
    }

    private int stripColorDrawn;
    private final android.graphics.Paint emojiFadePaint = new android.graphics.Paint();
    private float emojiGlassBottom = Float.NaN;
    private final int[] gridLoc = new int[2], emojiLoc = new int[2];
    private android.graphics.LinearGradient topFade;
    private final android.graphics.Paint topFadePaint = new android.graphics.Paint();
    private final android.graphics.Matrix topFadeM = new android.graphics.Matrix();

    /** smoothstep stops 1 → 0 (inverted = 0 → 1) for the DST_IN fades */
    private static android.graphics.LinearGradient smoothFade(boolean in) {
        final int n = 9;
        final int[] cols = new int[n];
        final float[] pos = new float[n];
        for (int i = 0; i < n; i++) {
            final float u = i / (float) (n - 1);
            final float a = u * u * (3f - 2f * u);
            cols[i] = ((int) (255 * (in ? a : 1f - a) + 0.5f)) << 24;
            pos[i] = u;
        }
        return new android.graphics.LinearGradient(0, 0, 0, 1, cols, pos, android.graphics.Shader.TileMode.CLAMP);
    }

    /**
     * Runs inside the grid's own layer (grid coordinates), after its cells: the grid scrolls on under the packs island
     * and dissolves toward the top (0 at the island's top → 1 at 12dp below it), dissolves over 10dp at the glass
     * bottom, and the picks island's own shape is cut out (its glass shows the sheet under it).
     */
    private void gridFades(android.graphics.Canvas c, androidx.recyclerview.widget.RecyclerView grid, View strip) {
        if (grid.isVerticalScrollBarEnabled()) grid.setVerticalScrollBarEnabled(false); // only our own thin scrollbar
        if (!nativeBarsOff && emojiView != null) {
            nativeBarsOff = true;
            disableNativeScrollbars(emojiView);
        }
        if (emojiView == null || Float.isNaN(emojiGlassBottom)) return;
        // the cascade by rows in the sheet's direction, carried by scale (each cell 0.5 → 1 with a small overshoot,
        // alpha within the first 35%); opening: bottom row first; closing: top row first, scale → 0.6, alpha at the end.
        // Scale per cell (Cell.cascadeScale), alpha as a soft row band in this layer (no per-cell layers).
        final float cp = view != null ? view.cascadeProgress() : 1f;
        final boolean opening = view != null && view.sheetOpening();
        // opening: no row cascade — the content sits at its final layout, revealed by the growing sheet, with one
        // uniform scale 0.75 → 1 timed from the open tap (420 ms, ease-out cubic; pivot at the visible glass bottom)
        // and a fade on the sheet's own progress; if the opening flag drops before the morph lands, the scale holds
        // and only completes with it, so nothing pops
        final float mp = view != null ? view.sheetProgress() : 1f;
        final float me = 1f - (1f - mp) * (1f - mp) * (1f - mp);
        final float rt = opening ? view.revealTime() : -1f;
        final float gs = rt >= 0f ? 0.75f + 0.25f * (1f - (1f - rt) * (1f - rt) * (1f - rt)) : mp >= 0.999f ? 1f : Math.max(grid.getScaleX(), 0.75f + 0.25f * me);
        final float ga = opening ? Math.min(1f, mp / 0.55f) : 1f;
        if (rt >= 0f && rt < 1f) view.invalidate(); // keep frames coming until the timed grow lands
        // the grid itself too: its scale is set from here, in its own draw — without frames of its own it kept the last
        // one short of 1 (the grid and the picks island's cut then stayed smaller than the island)
        if (gs < 1f) grid.postInvalidateOnAnimation();
        if (grid.getScaleX() != gs) {
            // pivot on the VISIBLE glass bottom (not the grid's own bottom, which sits lower under the picks island's
            // padding): with the grid's bottom the bottom rows were pulled below the glass at 0.75 and "filled in" last
            float topInKb = 0f;
            for (View v = grid; v != null && v != emojiView && v.getParent() instanceof View; v = (View) v.getParent())
                topInKb += v.getTop();
            final float pivotY = Float.isNaN(emojiGlassBottom) ? grid.getHeight() : Math.max(0f, Math.min(grid.getHeight(), emojiGlassBottom - topInKb));
            grid.setPivotX(grid.getWidth() / 2f);
            grid.setPivotY(pivotY);
            grid.setScaleX(gs);
            grid.setScaleY(gs);
        }
        if (grid.getAlpha() != ga) grid.setAlpha(ga);
        grid.getLocationOnScreen(gridLoc);
        emojiView.getLocationOnScreen(emojiLoc);
        // the grid's (scaled) origin in keyboard coordinates; everything drawn here is in the grid's own units, so a
        // keyboard length is divided by the scale (the grid is scaled during the open, the masks are not)
        final float oy = gridLoc[1] - emojiLoc[1];
        final float gk = grid.getScaleY() > 0.01f ? grid.getScaleY() : 1f;
        final float w = grid.getWidth();
        if (emojiFade == null) {
            emojiFade = smoothFade(false);
            emojiFadePaint.setShader(emojiFade);
            emojiFadePaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
            topFade = smoothFade(true);
            topFadePaint.setShader(topFade);
            topFadePaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
            islandCutPaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT));
        }
        // top: under the packs island
        if (strip != null) {
            strip.getLocationOnScreen(gridLoc);
            final float stKb = gridLoc[1] - emojiLoc[1]; // the strip's top in keyboard coordinates
            final float st = (stKb - oy) / gk, sb = st + (strip.getHeight() + org.telegram.messenger.AndroidUtilities.dp(12)) / gk;
            if (pickOverlay != null) pickOverlay.setTopClip(stKb + strip.getHeight() - stripInsetBottom);
            // the island's own shape is cut: its glass shows the sheet's glass instead (see stripUnder)
            final float sx0 = stripLoc0X(strip, grid) / grid.getScaleX();
            stripCut.set(sx0 + stripRimOut / gk, st + stripInsetTop / gk, sx0 + (strip.getWidth() - stripRimOut) / gk, st + (strip.getHeight() - stripInsetBottom) / gk);
            islandCutPaint.setAlpha(255);
            c.drawRoundRect(stripCut, stripCut.height() / 2f, stripCut.height() / 2f, islandCutPaint);
            topFadeM.setScale(1f, sb - st);
            topFadeM.postTranslate(0, st);
            topFade.setLocalMatrix(topFadeM);
            c.drawRect(0, Math.min(st, -oy / gk), w, sb, topFadePaint); // above st the shader clamps to 0: cleared
        }
        // bottom: 10dp at the glass edge, nothing below
        final int fh = org.telegram.messenger.AndroidUtilities.dp(10);
        final float ft = (emojiGlassBottom - oy - fh) / gk;
        topFadeM.setScale(1f, fh / gk);
        topFadeM.postTranslate(0, ft);
        emojiFade.setLocalMatrix(topFadeM);
        c.drawRect(0, ft, w, Math.max(ft + fh / gk, grid.getHeight() + org.telegram.messenger.AndroidUtilities.dp(200)), emojiFadePaint);
        final boolean running = cp < 1f && !opening;
        stepStrip(strip, opening ? mp : cp);
        final float visTop = Math.max(0f, -oy), visBottom = emojiGlassBottom - oy;
        float rowH = 0;
        for (int i = 0; i < grid.getChildCount(); i++) {
            final View ch = grid.getChildAt(i);
            if (!(ch instanceof QrEmojiPanel.Cell)) continue;
            final QrEmojiPanel.Cell iv = (QrEmojiPanel.Cell) ch;
            if (rowH == 0) rowH = ch.getHeight();
            float sc = 1f;
            if (running && rowH > 0) {
                final float rows = Math.max(1f, (visBottom - visTop) / rowH);
                final float cy = ch.getTop() + ch.getHeight() / 2f;
                if (cp >= 0f) {
                    final float stagger = Math.min(45f, 250f / rows), t = cp * 470f;
                    final float d = Math.max(0f, (visBottom - cy) / rowH - 0.5f) * stagger;
                    final float u = Math.max(0f, Math.min(1f, (t - d) / 220f));
                    sc = 0.5f + 0.5f * QrShareView.easeOutBack(u, 1.2f);
                } else {
                    final float stagger = Math.min(25f, 60f / Math.max(1f, rows - 1f)), t = -cp * 150f;
                    final float d = Math.max(0f, (cy - visTop) / rowH - 0.5f) * stagger;
                    final float u = Math.max(0f, Math.min(1f, (t - d) / 90f));
                    sc = 1f - 0.4f * u * u;
                }
            }
            if (iv.cascadeScale != sc) {
                iv.cascadeScale = sc;
                iv.invalidate();
            }
        }
        if (running && rowH > 0) {
            final float rows = Math.max(1f, (visBottom - visTop) / rowH);
            if (revealShader == null)
                revealShader = new android.graphics.LinearGradient(0, 0, 0, 1, 0x00000000, 0xFF000000, android.graphics.Shader.TileMode.CLAMP);
            final float band = rowH;
            if (cp >= 0f) {
                // alpha front = where a row's alpha is half there: (t − 0.175·220) / stagger rows above the bottom
                final float stagger = Math.min(45f, 250f / rows), t = cp * 470f;
                final float f = visBottom - Math.max(-1f, (t - 0.175f * 220f) / stagger) * rowH;
                topFadeM.setScale(1f, band);
                topFadeM.postTranslate(0, f - band / 2f);
            } else {
                // closing: alpha goes at 70–100% of a row's 90 ms
                final float stagger = Math.min(25f, 60f / Math.max(1f, rows - 1f)), t = -cp * 150f;
                final float f = visTop + Math.max(-1f, (t - 0.85f * 90f) / stagger) * rowH;
                topFadeM.setScale(1f, band);
                topFadeM.postTranslate(0, f - band / 2f);
            }
            revealShader.setLocalMatrix(topFadeM);
            revealPaint.setShader(revealShader);
            if (revealPaint.getXfermode() == null)
                revealPaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
            c.drawRect(0, 0, w, grid.getHeight(), revealPaint);
            grid.invalidate();
        }
        if (pickOverlay != null && pickOverlay.islandCut(islandCutRect, islandCutRA)) {
            // the island (overlay coordinates) → the screen → the grid's own coordinates: the grid may be scaled (the
            // open's grow), its screen origin then moves and its units shrink; the island is not scaled
            pickOverlay.getLocationOnScreen(cutOverlayLoc);
            grid.getLocationOnScreen(cutGridLoc);
            final float gsx = grid.getScaleX() > 0.01f ? grid.getScaleX() : 1f, gsy = grid.getScaleY() > 0.01f ? grid.getScaleY() : 1f;
            final float dx = cutOverlayLoc[0] - cutGridLoc[0], dy = cutOverlayLoc[1] - cutGridLoc[1];
            islandCutRect.set((islandCutRect.left + dx) / gsx, (islandCutRect.top + dy) / gsy,
                (islandCutRect.right + dx) / gsx, (islandCutRect.bottom + dy) / gsy);
            islandCutRA[0] /= gsy;
            islandCutPaint.setAlpha((int) (255 * islandCutRA[1]));
            c.drawRoundRect(islandCutRect, islandCutRA[0], islandCutRA[0], islandCutPaint);
        }
    }

    private final android.graphics.Paint islandCutPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    private final android.graphics.RectF islandCutRect = new android.graphics.RectF();
    private final float[] islandCutRA = new float[2];
    private final int[] cutOverlayLoc = new int[2], cutGridLoc = new int[2];
    private android.graphics.LinearGradient emojiFade;

    private void placeEmojiPanel(boolean visible, float top, float alpha) {
        if (!visible) {
            if (emojiView != null && emojiView.getVisibility() == View.VISIBLE) emojiView.setVisibility(View.INVISIBLE);
            if (pickOverlay != null && pickOverlay.getVisibility() == View.VISIBLE)
                pickOverlay.setVisibility(View.INVISIBLE);
            return;
        }
        if (emojiView == null) {
            if (getParentActivity() == null) return;
            emojiView = new QrEmojiPanel(getContext(), currentAccount, new QrEmojiPanel.Delegate() {
                @Override
                public void onEmojiPicked(long documentId, @Nullable TLRPC.Document document) {
                    if (view == null) return;
                    // without Premium only the presets apply; any other custom emoji: the Premium toast, nothing applied
                    if (!getUserConfig().isPremium() && (emojiView == null || !emojiView.isPreset(documentId))) {
                        view.showPremiumEmojiToast();
                        return;
                    }
                    view.pickCustomEmoji(documentId, document);
                    if (emojiView != null) emojiView.addRecent(documentId); // whatever styles the QR goes to Recent
                }

                @Override
                public void onUnlock() {
                    openPremium();
                }
            }) {
                @Override
                public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
                    // the fee menu open over the card: the keyboard below doesn't react; a tap here just closes the menu
                    if (view != null && view.feeMenuOpen()) {
                        if (ev.getActionMasked() == android.view.MotionEvent.ACTION_UP) view.closeFeeMenu();
                        return true;
                    }
                    return super.dispatchTouchEvent(ev);
                }

                // the fades are applied by the grid itself (its own layer, see gridFades): here only the glass bounds
                @Override
                protected void dispatchDraw(android.graphics.Canvas canvas) {
                    final float glassBottom = Math.min(getHeight(), shapeB - (getTop() + getTranslationY()));
                    emojiGlassBottom = pickOverlay != null ? pickOverlay.gridCutY(glassBottom) : glassBottom;
                    if (pickOverlay != null) pickOverlay.setGlassRight(shapeRr - pickOverlay.getLeft());
                    super.dispatchDraw(canvas);
                }
            };
            emojiView.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View v, android.graphics.Outline outline) {
                    // the (morphing) sheet shape in the view's own coordinates: all four corners round
                    final float vt = v.getTop() + v.getTranslationY(), vl = v.getLeft() + v.getTranslationX();
                    outline.setRoundRect(Math.round(shapeL - vl), Math.round(shapeT - vt), Math.round(shapeRr - vl), Math.round(shapeB - vt), shapeR);
                }
            });
            emojiView.setClipToOutline(true);
            pickOverlay = new EmojiPickOverlay(getContext(), new EmojiPickOverlay.Host() {
                @Override
                public java.util.List<String> pickedKeys() {return view.pickedKeys();}

                @Override
                public android.graphics.drawable.Drawable emojiFor(String key) {return view.emojiFor(key);}

                @Override
                public void removePickAt(int index) {view.removePickAt(index);}

                @Override
                public int islandTint() {return view.islandTint();}

                @Override
                public boolean drawBlur(android.graphics.Canvas c, float dx, float dy) {return view.drawBlur(c, dx, dy);}

                @Override
                public void qrViewLocation(int[] out) {view.getLocationOnScreen(out);}
            });
            pickOverlay.attach(emojiView);
            view.setOnPicksChanged(() -> {
                if (pickOverlay != null) pickOverlay.invalidate();
                updateGridBottomPad();
            }); // the island reacts on the first pick too
            pickOverlay.setOutlineProvider(emojiView.getOutlineProvider());
            pickOverlay.setClipToOutline(true);
            frame.addView(emojiView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, android.view.Gravity.TOP));
            layoutEmojiFinal();
            frame.post(this::styleEmojiTabs);
        }
        layoutEmojiFinal();
        final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) emojiView.getLayoutParams();
        final int h = lp.height, lm = lp.leftMargin, rm = lp.rightMargin;
        if (pickOverlay.getParent() == null) {
            frame.addView(pickOverlay, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, android.view.Gravity.TOP));
        }
        final FrameLayout.LayoutParams olp = (FrameLayout.LayoutParams) pickOverlay.getLayoutParams();
        if (olp.height != h || olp.leftMargin != lm || olp.rightMargin != rm) {
            olp.height = h;
            olp.leftMargin = lm;
            olp.rightMargin = rm;
            pickOverlay.setLayoutParams(olp);
        }
        pickOverlay.setTranslationY(top);
        pickOverlay.setAlpha(alpha);
        pickOverlay.invalidateOutline();
        pickOverlay.invalidate();
        if (pickOverlay.getVisibility() != View.VISIBLE) pickOverlay.setVisibility(View.VISIBLE);
        // the packs island's colour follows the backdrop: drawn again when it changes (also while the panel was hidden)
        final int stripColor = view.islandFlatColor() ^ view.islandTint();
        if (emojiView.getTranslationY() != top || stripColor != stripColorDrawn) {
            emojiView.setTranslationY(top);
            stripColorDrawn = stripColor;
            emojiView.getTabs().invalidate();
        }
        emojiView.invalidateOutline();
        emojiView.setAlpha(alpha);
        emojiView.setHeaderColor(view.sectionHeaderColor()); // cached per backdrop; follows the scene as it changes
        if (emojiView.getVisibility() != View.VISIBLE) {
            emojiView.refreshRecent(); // emoji applied since the last open show up in Recent now (not while it is open)
            emojiView.setVisibility(View.VISIBLE);
        }
        if (!tabsStyled) styleEmojiTabs(); // once the grid has its first cells
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        super.onTransitionAnimationEnd(isOpen, backward);
        if (isOpen) opened = true;
        if (isOpen && view != null) view.setReveal(1f); // opened without the custom transition: shown as it is
        if (isOpen && !backward && frame != null) {
            // building the keyboard blocks the main thread ~280 ms (34 skipped frames): only after the open, the QR intro
            // (its clock starts with the open's first frame and may outlast it) and the items' pop-in
            final long introLeft = QrCodeLayer.introPlays() ? Math.max(0, Math.round(RimTune.v[RimTune.INTRO_MS]) - OPEN_MS) : 0;
            frame.removeCallbacks(preloadEmojiRunnable);
            frame.postDelayed(preloadEmojiRunnable, introLeft + QrShareView.ITEMS_APPEAR_MS);
        }
    }

    /** A full-height layer slides up over the (dimmed) profile; closing slides it back down. */
    @Override
    public android.animation.AnimatorSet onCustomTransitionAnimation(boolean isOpen, Runnable callback) {
        // tablets: the screen is not full-screen there (a dialog-like layer), the slide-up looks abrupt; the default
        // transition instead
        if (view == null || AndroidUtilities.isTablet()) return null;
        view.setRevealOpening(isOpen);
        view.setReveal(isOpen ? 0f : 1f);
        final android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(isOpen ? 0f : 1f, isOpen ? 1f : 0f);
        va.addUpdateListener(a -> view.setReveal((float) a.getAnimatedValue()));
        final android.animation.AnimatorSet set = new android.animation.AnimatorSet();
        set.playTogether(va);
        set.setDuration(isOpen ? OPEN_MS : 200); // open: the spring settles by ~0.6 s; close: 200 ms ease-in
        set.setInterpolator(new android.view.animation.LinearInterpolator()); // the view eases it
        set.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                unlockTransition();
                view.setReveal(isOpen ? 1f : 0f);
                callback.run();
            }
        });
        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
            lockTransition();
            set.start();
        });
        return set;
    }

    // the screens' switch defers the notifications (no list updates mid-animation); the heavy operations (lottie and
    // video frames) are stopped too, except with ARG_IMMEDIATE on high-end devices: there the pattern's emoji and models
    // play through the open (a stop reaches every ImageReceiver below its layer: its animation neither runs nor starts
    // until the end)
    private int transitionLock = -1, transitionGlobalLock = -1, transitionLockAccount = -1;

    private void lockTransition() {
        unlockTransition();
        final boolean stopHeavy = !immediateOnOpen; // otherwise nothing of the pattern plays during the open
        transitionLockAccount = currentAccount;
        transitionLock = org.telegram.messenger.NotificationCenter.getInstance(currentAccount).setAnimationInProgress(-1, null, stopHeavy);
        transitionGlobalLock = org.telegram.messenger.NotificationCenter.getGlobalInstance().setAnimationInProgress(-1, null, stopHeavy);
    }

    private void unlockTransition() {
        if (transitionLock >= 0)
            org.telegram.messenger.NotificationCenter.getInstance(transitionLockAccount).onAnimationFinish(transitionLock);
        if (transitionGlobalLock >= 0)
            org.telegram.messenger.NotificationCenter.getGlobalInstance().onAnimationFinish(transitionGlobalLock);
        transitionLock = transitionGlobalLock = -1;
    }

    private androidx.recyclerview.widget.RecyclerView emojiGrid;
    private int stripRimOut, stripInsetTop, stripInsetBottom;
    private float stripTy;

    /**
     * The packs island is part of the unfold: it rides on the sheet's top edge (no fixed screen position) and grows IN
     * PARALLEL with the sheet's morph on open (scale 0.85 → 1 about its centre on the same progress, opacity in its
     * first 35%); it goes FIRST on close (90 ms, → 0.85).
     */
    private void stepStrip(View strip, float cp) {
        if (strip == null || view == null) return;
        // ride with the sheet: the glass top now vs. its final top
        stripTy = shapeT - (view.panelFinalTop() - org.telegram.messenger.AndroidUtilities.dp(8));
        // its glass samples the backdrop at its screen position: recorded again whenever it moves or scales
        if (strip.getTranslationY() != stripTy) {
            strip.setTranslationY(stripTy);
            strip.invalidate();
        }
        float sc = 1f, al = 1f;
        if (cp < 1f) {
            if (cp >= 0f) {
                // opening: cp is the sheet's own progress here — the island grows in parallel with the morph
                final float u = Math.max(0f, Math.min(1f, cp));
                sc = 0.85f + 0.15f * (1f - (1f - u) * (1f - u) * (1f - u));
                al = Math.min(1f, u / 0.35f);
            } else {
                final float u = Math.max(0f, Math.min(1f, -cp * 150f / 90f));
                sc = 1f - 0.15f * u * u;
                al = 1f - u;
            }
        }
        if (strip.getScaleX() != sc) {
            strip.setPivotX(strip.getWidth() / 2f);
            strip.setPivotY(strip.getHeight() / 2f);
            strip.setScaleX(sc);
            strip.setScaleY(sc);
            strip.invalidate();
        }
        if (strip.getAlpha() != al) strip.setAlpha(al);
        // this runs from the grid's draw: keep that drawing until the island has fully landed (else it could stay at
        // ~0.9 alpha — a darker island — until the first scroll redrew the grid)
        if ((sc != 1f || al != 1f) && emojiGrid != null) emojiGrid.postInvalidateOnAnimation();
    }

    private boolean nativeBarsOff;

    /** Every scrolling view inside the keyboard: no native scrollbars (the overlay draws the one thin bar). */
    private static void disableNativeScrollbars(View v) {
        v.setVerticalScrollBarEnabled(false);
        v.setHorizontalScrollBarEnabled(false);
        if (v instanceof android.view.ViewGroup) {
            final android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) disableNativeScrollbars(g.getChildAt(i));
        }
    }

    private android.graphics.LinearGradient revealShader;
    private final android.graphics.Paint revealPaint = new android.graphics.Paint();
    private final android.graphics.RectF stripCut = new android.graphics.RectF();
    private final int[] stripLoc = new int[2], gridLoc2 = new int[2], qrLoc2 = new int[2];

    /** strip left in grid coordinates */
    private float stripLoc0X(View strip, View grid) {
        strip.getLocationOnScreen(stripLoc);
        grid.getLocationOnScreen(gridLoc2);
        return stripLoc[0] - gridLoc2[0];
    }

    /**
     * Under the packs island (strip canvas, visible bounds): the sheet's glass, clipped to the island. The grid is not
     * replayed blurred under it: at the downsampled size the emoji turned into smudges.
     */
    private void stripUnder(android.graphics.Canvas cv, View strip, float l, float t, float r, float b) {
        if (emojiGrid == null || view == null) return;
        stripCut.set(l + stripRimOut, t + stripInsetTop, r - stripRimOut, b - stripInsetBottom);
        cv.save();
        stripClip.rewind();
        stripClip.addRoundRect(stripCut, stripCut.height() / 2f, stripCut.height() / 2f, android.graphics.Path.Direction.CW);
        cv.clipPath(stripClip);
        strip.getLocationOnScreen(stripLoc);
        view.getLocationOnScreen(qrLoc2);
        // the strip's own scale (the open / close of the island): a point x of this canvas is on screen at
        // loc + scale·(x − l), so the backdrop is drawn unscaled from the strip's screen position
        final float sc = Math.max(0.01f, strip.getScaleX());
        cv.translate(l, t);
        cv.scale(1f / sc, 1f / sc);
        stripGlassRect.set((stripCut.left - l) * sc, (stripCut.top - t) * sc, (stripCut.right - l) * sc, (stripCut.bottom - t) * sc);
        view.drawSheetGlass(cv, stripLoc[0] - qrLoc2[0], stripLoc[1] - qrLoc2[1], stripGlassRect); // the sheet's own glass (not the darker raw blur)
        cv.restore();
        // same as the picks island: the backdrop-hue tint over the glass (then the drawable's 20% white)
        if (stripTintPaint == null) stripTintPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        stripTintPaint.setColor(view.islandTint());
        cv.drawRoundRect(stripCut, stripCut.height() / 2f, stripCut.height() / 2f, stripTintPaint);
    }

    private final android.graphics.Path stripClip = new android.graphics.Path();
    private final android.graphics.RectF stripGlassRect = new android.graphics.RectF();
    private android.graphics.Paint stripTintPaint;
    private final RimPainter stripRim = new RimPainter();
    private final android.graphics.RectF stripRect = new android.graphics.RectF();

    /** Bottom padding: room for the picks island (66dp + its 10dp fade) only while it shows; otherwise just the fade. */
    private void updateGridBottomPad() {
        final androidx.recyclerview.widget.RecyclerView grid = emojiGrid;
        if (grid == null || view == null) return;
        final int padBottom = org.telegram.messenger.AndroidUtilities.dp(view.pickedKeys().isEmpty() ? 12 : 73);
        if (grid.getPaddingBottom() != padBottom)
            grid.setPadding(grid.getPaddingLeft(), grid.getPaddingTop(), grid.getPaddingRight(), padBottom);
    }

    /** The pack/category strip becomes a rounded glass pill inside the sheet. */
    private boolean tabsStyled;

    private void styleEmojiTabs() {
        if (tabsStyled) return;
        if (emojiView == null) return;
        final QrEmojiPanel.Tabs strip = emojiView.getTabs();
        final android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0x33FFFFFF); // the picks island's recipe: the backdrop-hue tint (drawn under, see stripUnder) + 20% white
        bg.setCornerRadius(org.telegram.messenger.AndroidUtilities.dp(20));
        // the glass keeps its earlier visible size/margins (it used to sit a rim-width inside the view bounds), but is
        // 3 px taller: 1 px up, 2 px down (≈ +1 dp on this 3.0-density phone)
        final int rimOut = (int) Math.ceil(org.telegram.messenger.AndroidUtilities.dpf2(RimTune.g[RimTune.BIG][RimTune.D_W] + Math.max(0f, -RimTune.g[RimTune.BIG][RimTune.INSET])));
        // 4 px taller than before (2 up, 2 down) and the side margins 2–3 px smaller, so the gap to the sheet's
        // edge is the same on the top and the sides: insets 4 px top/sides, 3 px bottom (view 36 dp → pill 101 px)
        stripRimOut = Math.max(0, rimOut - 3);
        stripInsetTop = Math.max(0, rimOut - 3);
        stripInsetBottom = Math.max(0, rimOut - 4);
        strip.underDraw = (cv, l, t, r, b2) -> stripUnder(cv, strip, l, t, r, b2);
        // the picks island's rim (Dark out, Big group), so both islands read as one family
        strip.overDraw = (cv, l, t, r, b2) -> {
            stripRect.set(l + stripRimOut, t + stripInsetTop, r - stripRimOut, b2 - stripInsetBottom);
            stripRim.draw(cv, stripRect, stripRect.height() / 2f, RimTune.BIG, 1f);
        };
        final int pl = strip.getPaddingLeft(), pt = strip.getPaddingTop(), pr = strip.getPaddingRight(), pb = strip.getPaddingBottom();
        strip.setBackground(new android.graphics.drawable.InsetDrawable(bg, stripRimOut, stripInsetTop, stripRimOut, stripInsetBottom));
        strip.setPadding(pl, pt, pr, pb); // an InsetDrawable reports its inset as padding: keep the layout as it was
        strip.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                // clip everything (icons, active circle, ripples) to the visible glass pill, AA
                final int h = v.getHeight() - stripInsetTop - stripInsetBottom;
                o.setRoundRect(stripRimOut, stripInsetTop, v.getWidth() - stripRimOut, v.getHeight() - stripInsetBottom, h / 2f);
            }
        });
        strip.setClipToOutline(true);
        if (strip.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            final android.view.ViewGroup.MarginLayoutParams mlp = (android.view.ViewGroup.MarginLayoutParams) strip.getLayoutParams();
            final int m = org.telegram.messenger.AndroidUtilities.dp(10);
            // the keyboard itself starts 8dp below the glass top: topMargin 0 puts the pill 8dp from the glass edge
            final int tm = org.telegram.messenger.AndroidUtilities.dp(2); // the keyboard starts 8dp below the glass: 10dp in total
            if (mlp.leftMargin != m || mlp.rightMargin != m || mlp.topMargin != tm) {
                mlp.leftMargin = m;
                mlp.rightMargin = m;
                mlp.topMargin = tm;
                strip.setLayoutParams(mlp);
            }
        }
        // the grid starts below the island (36 + 8 top + 6 gap) and scrolls up under the glass; the island stays put
        final QrEmojiPanel.Grid grid = emojiView.getGrid();
        {
            tabsStyled = true;
            grid.afterChildrenDraw = cv -> gridFades(cv, grid, strip);
            grid.beforeChildrenDraw = cv -> {
                pickOverlay.drawUnder(cv, grid);
                if (!view.pickedKeys().isEmpty()) pickOverlay.invalidate(); // badges and borders follow the grid
            };
            pickOverlay.attachGrid(grid); // our own thin white scrollbar instead of the system one
            final int padTop = org.telegram.messenger.AndroidUtilities.dp(2 + 36 + 6);
            emojiGrid = grid;
            // right: a gutter for the scrollbar (3dp bar, 3dp from the glass edge) so it never covers a cell
            final int padRight = grid.getPaddingRight() + org.telegram.messenger.AndroidUtilities.dp(5);
            grid.setPadding(grid.getPaddingLeft(), padTop, padRight, grid.getPaddingBottom());
            updateGridBottomPad();
            grid.setClipToPadding(false);
            grid.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(androidx.recyclerview.widget.RecyclerView rv, int dx, int dy) {
                    if (strip.getTranslationY() != stripTy) {
                        strip.setTranslationY(stripTy);
                        strip.invalidate();
                    } // the keyboard would slide it away; keep it on the sheet
                }
            });
        }
        strip.bringToFront();
    }

    /** No Telegram swipe-back here: horizontal drags rotate / move the backdrop. Back button and close still work. */
    @Override
    public boolean isSwipeBackEnabled(android.view.MotionEvent event) {
        return false;
    }

    @Override
    public boolean canBeginSlide() {
        return false;
    }

    @Override
    public void onPause() {
        super.onPause();
        // the camera does not stay on in the background
        if (scanOverlay != null) {
            scanOverlay.destroy();
            removeScanner(scanOverlay);
        }
        restoreBars();
    }

    /** White status + navigation bar controls, transparent, no contrast scrim. */
    private void applyWhiteBars() {
        final android.app.Activity a = getParentActivity();
        if (a == null) return;
        final android.view.Window w = a.getWindow();
        org.telegram.messenger.AndroidUtilities.setLightNavigationBar(a, false);
        org.telegram.messenger.AndroidUtilities.setLightStatusBar(w, false);
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            if (savedContrast == null) savedContrast = w.isNavigationBarContrastEnforced();
            w.setNavigationBarContrastEnforced(false);
        }
    }

    private Boolean savedContrast;

    private void restoreBars() {
        final android.app.Activity a = getParentActivity();
        if (a == null) return;
        if (android.os.Build.VERSION.SDK_INT >= 29 && savedContrast != null) {
            a.getWindow().setNavigationBarContrastEnforced(savedContrast);
            savedContrast = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        applyWhiteBars();
        if (getParentActivity() != null)
            org.telegram.messenger.AndroidUtilities.setLightNavigationBar(getParentActivity(), false);
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true; // the scene runs under the system bars, like on iOS
    }

    @Override
    public boolean drawEdgeNavigationBar() {
        return false; // no scrim over the scene
    }

    @Override
    public boolean isLightStatusBar() {
        return false; // white status bar icons on every backdrop
    }

    /**
     * LaunchActivity.checkSystemBarColors derives the nav-bar icon colour from this (light grey → dark icons): a dark
     * value keeps the gesture handle / buttons white for the whole screen (layers, keyboard, toasts, light backdrops).
     */
    @Override
    public int getNavigationBarColor() {
        return 0xFF000000;
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (scanOverlay != null) {
            if (invoked) scanOverlay.close();
            return false;
        }
        if (view != null && view.canConsumeBack()) {
            if (invoked) view.onBackPressed();
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        unlockTransition(); // a transition cut short
        unlockPortrait();
        if (frame != null) frame.removeCallbacks(preloadEmojiRunnable);
        if (scanOverlay != null) {
            scanOverlay.destroy();
            scanOverlay = null;
        }
        if (server != null) {
            server.release();
            server = null;
        }
        if (view != null) view.release();
    }
}
