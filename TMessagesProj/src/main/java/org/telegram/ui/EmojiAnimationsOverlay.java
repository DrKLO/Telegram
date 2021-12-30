package org.telegram.ui;

import android.graphics.Canvas;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.EmojiData;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.StickerSetBulletinLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

public class EmojiAnimationsOverlay implements NotificationCenter.NotificationCenterDelegate {

    private final int ANIMATION_JSON_VERSION = 1;
    private final String INTERACTIONS_STICKER_PACK = "EmojiAnimations";

    ChatActivity chatActivity;
    int currentAccount;
    TLRPC.TL_messages_stickerSet set;
    boolean inited = false;
    HashMap<String, ArrayList<TLRPC.Document>> emojiInteractionsStickersMap = new HashMap<>();
    HashMap<Long, Integer> lastAnimationIndex = new HashMap<>();
    Random random = new Random();
    private boolean attached;

    int lastTappedMsgId = -1;
    long lastTappedTime = 0;
    String lastTappedEmoji;
    ArrayList<Long> timeIntervals = new ArrayList<>();
    ArrayList<Integer> animationIndexes = new ArrayList<>();
    Runnable sentInteractionsRunnable;

    Runnable hintRunnable;
    private final static HashSet<String> supportedEmoji = new HashSet<>();
    private final static HashSet<String> excludeEmojiFromPack = new HashSet<>();

    static {
        // 1️⃣, 2️⃣, 3️⃣... etc
        excludeEmojiFromPack.add("\u0030\u20E3");
        excludeEmojiFromPack.add("\u0031\u20E3");
        excludeEmojiFromPack.add("\u0032\u20E3");
        excludeEmojiFromPack.add("\u0033\u20E3");
        excludeEmojiFromPack.add("\u0034\u20E3");
        excludeEmojiFromPack.add("\u0035\u20E3");
        excludeEmojiFromPack.add("\u0036\u20E3");
        excludeEmojiFromPack.add("\u0037\u20E3");
        excludeEmojiFromPack.add("\u0038\u20E3");
        excludeEmojiFromPack.add("\u0039\u20E3");
    }

    ArrayList<DrawingObject> drawingObjects = new ArrayList<>();

    FrameLayout contentLayout;
    RecyclerListView listView;
    long dialogId;
    int threadMsgId;


    public EmojiAnimationsOverlay(ChatActivity chatActivity, FrameLayout frameLayout, RecyclerListView chatListView, int currentAccount, long dialogId, int threadMsgId) {
        this.chatActivity = chatActivity;
        this.contentLayout = frameLayout;
        this.listView = chatListView;
        this.currentAccount = currentAccount;
        this.dialogId = dialogId;
        this.threadMsgId = threadMsgId;
    }

    protected void onAttachedToWindow() {
        attached = true;
        checkStickerPack();
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.diceStickersDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.onEmojiInteractionsReceived);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
    }

    protected void onDetachedFromWindow() {
        attached = false;
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.diceStickersDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.onEmojiInteractionsReceived);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
    }

    public void checkStickerPack() {
        if (inited) {
            return;
        }
        set = MediaDataController.getInstance(currentAccount).getStickerSetByName(INTERACTIONS_STICKER_PACK);
        if (set == null) {
            set = MediaDataController.getInstance(currentAccount).getStickerSetByEmojiOrName(INTERACTIONS_STICKER_PACK);
        }
        if (set == null) {
            MediaDataController.getInstance(currentAccount).loadStickersByEmojiOrName(INTERACTIONS_STICKER_PACK, false, true);
        }
        if (set != null) {
            HashMap<Long, TLRPC.Document> stickersMap = new HashMap<>();
            for (int i = 0; i < set.documents.size(); i++) {
                stickersMap.put(set.documents.get(i).id, set.documents.get(i));
            }
            for (int i = 0; i < set.packs.size(); i++) {
                TLRPC.TL_stickerPack pack = set.packs.get(i);
                if (!excludeEmojiFromPack.contains(pack.emoticon) && pack.documents.size() > 0) {
                    supportedEmoji.add(pack.emoticon);
                    ArrayList<TLRPC.Document> stickers = new ArrayList<>();
                    emojiInteractionsStickersMap.put(pack.emoticon, stickers);
                    for (int j = 0; j < pack.documents.size(); j++) {
                        stickers.add(stickersMap.get(pack.documents.get(j)));
                    }

                    if (pack.emoticon.equals("❤")) {
                        String[] heartEmojies = new String[]{"🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎"};
                        for (String heart : heartEmojies) {
                            supportedEmoji.add(heart);
                            emojiInteractionsStickersMap.put(heart, stickers);
                        }
                    }
                }
            }
            inited = true;
        }
    }


    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.diceStickersDidLoad) {
            String name = (String) args[0];
            if (INTERACTIONS_STICKER_PACK.equals(name)) {
                checkStickerPack();
            }
        } else if (id == NotificationCenter.onEmojiInteractionsReceived) {
            long dialogId = (long) args[0];
            TLRPC.TL_sendMessageEmojiInteraction action = (TLRPC.TL_sendMessageEmojiInteraction) args[1];
            if (dialogId == this.dialogId && supportedEmoji.contains(action.emoticon)) {
                int messageId = action.msg_id;
                if (action.interaction.data != null) {
                    try {
                        JSONObject jsonObject = new JSONObject(action.interaction.data);
                        JSONArray array = jsonObject.getJSONArray("a");
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject actionObject = array.getJSONObject(i);
                            int animation = actionObject.optInt("i", 1) - 1;
                            double time = actionObject.optDouble("t", 0.0);
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    findViewAndShowAnimation(messageId, animation);
                                }
                            }, (long) (time * 1000));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

            }
        } else if (id == NotificationCenter.updateInterfaces) {
            Integer printingType = MessagesController.getInstance(currentAccount).getPrintingStringType(dialogId, threadMsgId);
            if (printingType != null && printingType == 5) {
                cancelHintRunnable();
            }
        }
    }

    private void findViewAndShowAnimation(int messageId, int animation) {
        if (!attached) {
            return;
        }
        ChatMessageCell bestView = null;
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            if (child instanceof ChatMessageCell) {
                ChatMessageCell cell = (ChatMessageCell) child;
                if (cell.getPhotoImage().hasNotThumb() && cell.getMessageObject().getStickerEmoji() != null) {
                    if (cell.getMessageObject().getId() == messageId) {
                        bestView = cell;
                        break;
                    }
                }
            }
        }

        if (bestView != null) {
            chatActivity.restartSticker(bestView);
            if (!EmojiData.hasEmojiSupportVibration(bestView.getMessageObject().getStickerEmoji())) {
                bestView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
            showAnimationForCell(bestView, animation, false, true);
        }
    }

    public void draw(Canvas canvas) {
        if (!drawingObjects.isEmpty()) {
            for (int i = 0; i < drawingObjects.size(); i++) {
                DrawingObject drawingObject = drawingObjects.get(i);
                drawingObject.viewFound = false;
                for (int k = 0; k < listView.getChildCount(); k++) {
                    View child = listView.getChildAt(k);
                    if (child instanceof ChatMessageCell) {
                        ChatMessageCell cell = (ChatMessageCell) child;
                        if (cell.getMessageObject().getId() == drawingObject.messageId) {
                            drawingObject.viewFound = true;
                            float viewX = listView.getX() + child.getX() + cell.getPhotoImage().getImageX();
                            float viewY = listView.getY() + child.getY() + cell.getPhotoImage().getImageY();
                            if (drawingObject.isOut) {
                                viewX += -cell.getPhotoImage().getImageWidth() * 2 + AndroidUtilities.dp(24);
                            } else {
                                viewX += -AndroidUtilities.dp(24);
                            }
                            viewY -= cell.getPhotoImage().getImageWidth();
                            drawingObject.lastX = viewX;
                            drawingObject.lastY = viewY;
                            drawingObject.lastW = cell.getPhotoImage().getImageWidth();
                            break;
                        }
                    }
                }

                drawingObject.imageReceiver.setImageCoords(drawingObject.lastX + drawingObject.randomOffsetX, drawingObject.lastY + drawingObject.randomOffsetY, drawingObject.lastW * 3, drawingObject.lastW * 3);
                if (!drawingObject.isOut) {
                    canvas.save();
                    canvas.scale(-1f, 1, drawingObject.imageReceiver.getCenterX(), drawingObject.imageReceiver.getCenterY());
                    drawingObject.imageReceiver.draw(canvas);
                    canvas.restore();
                } else {
                    drawingObject.imageReceiver.draw(canvas);
                }
                if (drawingObject.wasPlayed && drawingObject.imageReceiver.getLottieAnimation() != null && drawingObject.imageReceiver.getLottieAnimation().getCurrentFrame() == drawingObject.imageReceiver.getLottieAnimation().getFramesCount() - 2) {
                    drawingObjects.remove(i);
                    i--;
                } else if (drawingObject.imageReceiver.getLottieAnimation() != null && drawingObject.imageReceiver.getLottieAnimation().isRunning()) {
                    drawingObject.wasPlayed = true;
                } else if (drawingObject.imageReceiver.getLottieAnimation() != null && !drawingObject.imageReceiver.getLottieAnimation().isRunning()) {
                    drawingObject.imageReceiver.getLottieAnimation().setCurrentFrame(0, true);
                    drawingObject.imageReceiver.getLottieAnimation().start();
                }
            }
            contentLayout.invalidate();
        }
    }

    public void onTapItem(ChatMessageCell view, ChatActivity chatActivity) {
        if (chatActivity.currentUser == null || chatActivity.isSecretChat() || view.getMessageObject() == null || view.getMessageObject().getId() < 0) {
            return;
        }
        boolean show = showAnimationForCell(view, -1, true, false);
        if (show && !EmojiData.hasEmojiSupportVibration(view.getMessageObject().getStickerEmoji())) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }

        Integer printingType = MessagesController.getInstance(currentAccount).getPrintingStringType(dialogId, threadMsgId);
        boolean canShowHint = true;
        if (printingType != null && printingType == 5) {
            canShowHint = false;
        }
        if (canShowHint && hintRunnable == null && show && (Bulletin.getVisibleBulletin() == null || !Bulletin.getVisibleBulletin().isShowing()) && SharedConfig.emojiInteractionsHintCount > 0 && UserConfig.getInstance(currentAccount).getClientUserId() != chatActivity.currentUser.id) {
            SharedConfig.updateEmojiInteractionsHintCount(SharedConfig.emojiInteractionsHintCount - 1);
            TLRPC.Document document = MediaDataController.getInstance(currentAccount).getEmojiAnimatedSticker(view.getMessageObject().getStickerEmoji());
            StickerSetBulletinLayout layout = new StickerSetBulletinLayout(chatActivity.getParentActivity(), null, StickerSetBulletinLayout.TYPE_EMPTY, document, chatActivity.getResourceProvider());
            layout.subtitleTextView.setVisibility(View.GONE);
            layout.titleTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("EmojiInteractionTapHint", R.string.EmojiInteractionTapHint, chatActivity.currentUser.first_name)));
            layout.titleTextView.setTypeface(null);
            layout.titleTextView.setMaxLines(3);
            layout.titleTextView.setSingleLine(false);
            Bulletin bulletin = Bulletin.make(chatActivity, layout, Bulletin.DURATION_LONG);
            AndroidUtilities.runOnUIThread(hintRunnable = new Runnable() {
                @Override
                public void run() {
                    bulletin.show();
                    hintRunnable = null;
                }
            }, 1500);
        }
    }

    public void cancelHintRunnable() {
        if (hintRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(hintRunnable);
        }
        hintRunnable = null;
    }

    private boolean showAnimationForCell(ChatMessageCell view, int animation, boolean sendTap, boolean sendSeen) {
        if (drawingObjects.size() > 12) {
            return false;
        }
        if (!view.getPhotoImage().hasNotThumb()) {
            return false;
        }
        String emoji = view.getMessageObject().getStickerEmoji();
        if (emoji == null) {
            return false;
        }
        float imageH = view.getPhotoImage().getImageHeight();
        float imageW = view.getPhotoImage().getImageWidth();
        if (imageH <= 0 || imageW <= 0) {
            return false;
        }

        emoji = unwrapEmoji(emoji);

        if (supportedEmoji.contains(emoji)) {
            ArrayList<TLRPC.Document> arrayList = emojiInteractionsStickersMap.get(emoji);
            if (arrayList != null && !arrayList.isEmpty()) {
                int sameAnimationsCount = 0;
                for (int i = 0; i < drawingObjects.size(); i++) {
                    if (drawingObjects.get(i).messageId == view.getMessageObject().getId()) {
                        sameAnimationsCount++;
                        if (drawingObjects.get(i).imageReceiver.getLottieAnimation() == null || drawingObjects.get(i).imageReceiver.getLottieAnimation().isGeneratingCache()) {
                            return false;
                        }
                    }
                }
                if (sameAnimationsCount >= 4) {
                    return false;
                }
                if (animation < 0 || animation > arrayList.size() - 1) {
                    animation = Math.abs(random.nextInt()) % arrayList.size();
                }
                TLRPC.Document document = arrayList.get(animation);

                DrawingObject drawingObject = new DrawingObject();
                drawingObject.randomOffsetX = imageW / 4 * ((random.nextInt() % 101) / 100f);
                drawingObject.randomOffsetY = imageH / 4 * ((random.nextInt() % 101) / 100f);
                drawingObject.messageId = view.getMessageObject().getId();
                drawingObject.document = document;
                drawingObject.isOut = view.getMessageObject().isOutOwner();

                Integer lastIndex = lastAnimationIndex.get(document.id);
                int currentIndex = lastIndex == null ? 0 : lastIndex;
                lastAnimationIndex.put(document.id, (currentIndex + 1) % 4);


                ImageLocation imageLocation = ImageLocation.getForDocument(document);
                drawingObject.imageReceiver.setUniqKeyPrefix(currentIndex + "_" + drawingObject.messageId + "_");
                int w = (int) (2f * imageW / AndroidUtilities.density);
                drawingObject.imageReceiver.setImage(imageLocation, w + "_" + w + "_pcache", null, "tgs", set, 1);
                drawingObject.imageReceiver.setLayerNum(Integer.MAX_VALUE);
                drawingObject.imageReceiver.setAllowStartAnimation(true);
                drawingObject.imageReceiver.setAutoRepeat(0);
                if (drawingObject.imageReceiver.getLottieAnimation() != null) {
                    drawingObject.imageReceiver.getLottieAnimation().start();
                }
                drawingObjects.add(drawingObject);
                drawingObject.imageReceiver.onAttachedToWindow();
                drawingObject.imageReceiver.setParentView(contentLayout);
                contentLayout.invalidate();

                if (sendTap) {
                    if (lastTappedMsgId != 0 && lastTappedMsgId != view.getMessageObject().getId()) {
                        if (sentInteractionsRunnable != null) {
                            AndroidUtilities.cancelRunOnUIThread(sentInteractionsRunnable);
                            sentInteractionsRunnable.run();
                        }
                    }
                    lastTappedMsgId = view.getMessageObject().getId();
                    lastTappedEmoji = emoji;
                    if (lastTappedTime == 0) {
                        lastTappedTime = System.currentTimeMillis();
                        timeIntervals.clear();
                        animationIndexes.clear();
                        timeIntervals.add(0L);
                        animationIndexes.add(animation);
                    } else {
                        timeIntervals.add(System.currentTimeMillis() - lastTappedTime);
                        animationIndexes.add(animation);
                    }
                    if (sentInteractionsRunnable != null) {
                        AndroidUtilities.cancelRunOnUIThread(sentInteractionsRunnable);
                        sentInteractionsRunnable = null;
                    }
                    AndroidUtilities.runOnUIThread(sentInteractionsRunnable = () -> {
                        sendCurrentTaps();
                        sentInteractionsRunnable = null;
                    }, 500);
                }

                if (sendSeen) {
                    MessagesController.getInstance(currentAccount).sendTyping(dialogId, threadMsgId, 11, emoji, 0);
                }
                return true;
            }
        }
        return false;
    }

    private String unwrapEmoji(String emoji) {
        CharSequence fixedEmoji = emoji;
        int length = emoji.length();
        for (int a = 0; a < length; a++) {
            if (a < length - 1 && (fixedEmoji.charAt(a) == 0xD83C && fixedEmoji.charAt(a + 1) >= 0xDFFB && fixedEmoji.charAt(a + 1) <= 0xDFFF || fixedEmoji.charAt(a) == 0x200D && (fixedEmoji.charAt(a + 1) == 0x2640 || fixedEmoji.charAt(a + 1) == 0x2642))) {
                fixedEmoji = TextUtils.concat(fixedEmoji.subSequence(0, a), fixedEmoji.subSequence(a + 2, fixedEmoji.length()));
                length -= 2;
                a--;
            } else if (fixedEmoji.charAt(a) == 0xfe0f) {
                fixedEmoji = TextUtils.concat(fixedEmoji.subSequence(0, a), fixedEmoji.subSequence(a + 1, fixedEmoji.length()));
                length--;
                a--;
            }
        }
        return fixedEmoji.toString();
    }

    private void sendCurrentTaps() {
        if (lastTappedMsgId == 0) {
            return;
        }
        TLRPC.TL_sendMessageEmojiInteraction interaction = new TLRPC.TL_sendMessageEmojiInteraction();
        interaction.msg_id = lastTappedMsgId;
        interaction.emoticon = lastTappedEmoji;
        interaction.interaction = new TLRPC.TL_dataJSON();
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("v", ANIMATION_JSON_VERSION);
            JSONArray array = new JSONArray();

            for (int i = 0; i < timeIntervals.size(); i++) {
                JSONObject action = new JSONObject();
                action.put("i", animationIndexes.get(i) + 1);
                action.put("t", timeIntervals.get(i) / 1000f);
                array.put(i, action);
            }

            jsonObject.put("a", array);
        } catch (JSONException e) {
            clearSendingInfo();
            FileLog.e(e);
            return;
        }
        interaction.interaction.data = jsonObject.toString();

        TLRPC.TL_messages_setTyping req = new TLRPC.TL_messages_setTyping();
        if (threadMsgId != 0) {
            req.top_msg_id = threadMsgId;
            req.flags |= 1;
        }
        req.action = interaction;
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, null);
        clearSendingInfo();
    }

    private void clearSendingInfo() {
        lastTappedMsgId = 0;
        lastTappedEmoji = null;
        lastTappedTime = 0;
        timeIntervals.clear();
        animationIndexes.clear();
    }

    public void onScrolled(int dy) {
        for (int i = 0; i < drawingObjects.size(); i++) {
            if (!drawingObjects.get(i).viewFound) {
                drawingObjects.get(i).lastY -= dy;
            }
        }
    }

    private class DrawingObject {
        public float lastX;
        public float lastY;
        public boolean viewFound;
        public float lastW;
        public float randomOffsetX;
        public float randomOffsetY;
        boolean wasPlayed;
        boolean isOut;
        int messageId;
        TLRPC.Document document;
        ImageReceiver imageReceiver = new ImageReceiver();
    }
}
