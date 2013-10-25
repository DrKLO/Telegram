/*
 * This is the source code of Telegram for Android v. 1.2.3.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;

import org.telegram.messenger.ConnectionsManager;
import org.telegram.objects.PhotoObject;
import org.telegram.ui.Views.AbstractGalleryActivity;
import org.telegram.ui.Views.GalleryViewPager;
import org.telegram.ui.Views.PZSImageView;
import org.telegram.TL.TLRPC;
import org.telegram.objects.MessageObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;

public class GalleryImageViewer extends AbstractGalleryActivity implements NotificationCenter.NotificationCenterDelegate {
    private TextView nameTextView;
    private TextView timeTextView;
    private View bottomView;
    private TextView fakeTitleView;
    private LocalPagerAdapter localPagerAdapter;
    private GalleryViewPager mViewPager;
    private boolean withoutBottom = false;
    private boolean fromAll = false;
    private boolean isVideo = false;
    private boolean needSearchMessage = false;
    private boolean loadingMore = false;
    private TextView title;
    private boolean ignoreSet = false;

    private ArrayList<MessageObject> imagesArrTemp = new ArrayList<MessageObject>();
    private HashMap<Integer, MessageObject> imagesByIdsTemp = new HashMap<Integer, MessageObject>();

    private ArrayList<TLRPC.FileLocation> imagesArrLocations;
    private long currentDialog = 0;
    private int totalCount = 0;
    private int classGuid;
    private boolean cacheEndReached;

    private HashMap<String, ProgressBar> loadingFile = new HashMap<String, ProgressBar>();
    private HashMap<Integer, String> progressByTag = new HashMap<Integer, String>();

    public static int needShowAllMedia = 2000;

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        classGuid = ConnectionsManager.Instance.generateClassGuid();
        setContentView(R.layout.gallery_layout);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayUseLogoEnabled(false);
        actionBar.setTitle(getString(R.string.Gallery));
        actionBar.show();

        mViewPager = (GalleryViewPager)findViewById(R.id.gallery_view_pager);
        ImageView shareButton = (ImageView)findViewById(R.id.gallery_view_share_button);
        ImageView deleteButton = (ImageView) findViewById(R.id.gallery_view_delete_button);
        nameTextView = (TextView)findViewById(R.id.gallery_view_name_text);
        timeTextView = (TextView)findViewById(R.id.gallery_view_time_text);
        bottomView = findViewById(R.id.gallery_view_bottom_view);
        fakeTitleView = (TextView)findViewById(R.id.fake_title_view);

        title = (TextView)findViewById(R.id.abs__action_bar_title);
        if (title == null) {
            final int titleId = getResources().getIdentifier("action_bar_title", "id", "android");
            title = (TextView)findViewById(titleId);
        }

        NotificationCenter.Instance.addObserver(this, FileLoader.FileDidFailedLoad);
        NotificationCenter.Instance.addObserver(this, FileLoader.FileDidLoaded);
        NotificationCenter.Instance.addObserver(this, FileLoader.FileLoadProgressChanged);
        NotificationCenter.Instance.addObserver(this, MessagesController.mediaCountDidLoaded);
        NotificationCenter.Instance.addObserver(this, MessagesController.mediaDidLoaded);

        Integer index = null;
        if (localPagerAdapter == null) {
            final MessageObject file = (MessageObject)NotificationCenter.Instance.getFromMemCache(1);
            final TLRPC.FileLocation fileLocation = (TLRPC.FileLocation)NotificationCenter.Instance.getFromMemCache(3);
            final ArrayList<MessageObject> messagesArr = (ArrayList<MessageObject>)NotificationCenter.Instance.getFromMemCache(4);
            index = (Integer)NotificationCenter.Instance.getFromMemCache(5);

            ArrayList<MessageObject> imagesArr = null;
            HashMap<Integer, MessageObject> imagesByIds = null;
            if (file != null) {
                imagesArr = new ArrayList<MessageObject>();
                imagesByIds = new HashMap<Integer, MessageObject>();
                imagesArr.add(file);
                if (file.messageOwner.action == null || file.messageOwner.action instanceof TLRPC.TL_messageActionEmpty) {
                    needSearchMessage = true;
                    imagesByIds.put(file.messageOwner.id, file);
                    if (file.messageOwner.dialog_id != 0) {
                        currentDialog = file.messageOwner.dialog_id;
                    } else {
                        if (file.messageOwner.to_id.chat_id != 0) {
                            currentDialog = -file.messageOwner.to_id.chat_id;
                        } else {
                            if (file.messageOwner.to_id.user_id == UserConfig.clientUserId) {
                                currentDialog = file.messageOwner.from_id;
                            } else {
                                currentDialog = file.messageOwner.to_id.user_id;
                            }
                        }
                    }
                }
            } else if (fileLocation != null) {
                ArrayList<TLRPC.FileLocation> arr = new ArrayList<TLRPC.FileLocation>();
                arr.add(fileLocation);
                imagesArrLocations = arr;
                withoutBottom = true;
                deleteButton.setVisibility(View.INVISIBLE);
                nameTextView.setVisibility(View.INVISIBLE);
                timeTextView.setVisibility(View.INVISIBLE);
            } else if (messagesArr != null) {
                imagesArr = new ArrayList<MessageObject>();
                imagesByIds = new HashMap<Integer, MessageObject>();
                imagesArr.addAll(messagesArr);
                Collections.reverse(imagesArr);
                for (MessageObject message : imagesArr) {
                    imagesByIds.put(message.messageOwner.id, message);
                }
                index = imagesArr.size() - index - 1;

                MessageObject object = imagesArr.get(0);
                if (object.messageOwner.dialog_id != 0) {
                    currentDialog = object.messageOwner.dialog_id;
                } else {
                    if (object.messageOwner.to_id.chat_id != 0) {
                        currentDialog = -object.messageOwner.to_id.chat_id;
                    } else {
                        if (object.messageOwner.to_id.user_id == UserConfig.clientUserId) {
                            currentDialog = object.messageOwner.from_id;
                        } else {
                            currentDialog = object.messageOwner.to_id.user_id;
                        }
                    }
                }
            }
            localPagerAdapter = new LocalPagerAdapter(imagesArr, imagesByIds);
        }

        float density = Utilities.applicationContext.getResources().getDisplayMetrics().density;

        mViewPager.setPageMargin((int)(20 * density));
        mViewPager.setOffscreenPageLimit(1);
        mViewPager.setAdapter(localPagerAdapter);

        if (index != null) {
            fromAll = true;
            mViewPager.setCurrentItem( index);
        }

        shareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    TLRPC.FileLocation file = getCurrentFile();
                    File f = new File(Utilities.getCacheDir(), file.volume_id + "_" + file.local_id + ".jpg");
                    if (f.exists()) {
                        Intent intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("image/jpeg");
                        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
                        startActivity(intent);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int item = mViewPager.getCurrentItem();
                MessageObject obj = localPagerAdapter.imagesArr.get(item);
                if (obj.messageOwner.send_state == MessagesController.MESSAGE_SEND_STATE_SENT) {
                    ArrayList<Integer> arr = new ArrayList<Integer>();
                    arr.add(obj.messageOwner.id);
                    MessagesController.Instance.deleteMessages(arr);
                    finish();
                }
            }
        });

        if (currentDialog != 0 && totalCount == 0) {
            MessagesController.Instance.getMediaCount(currentDialog, classGuid, true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        NotificationCenter.Instance.removeObserver(this, FileLoader.FileDidFailedLoad);
        NotificationCenter.Instance.removeObserver(this, FileLoader.FileDidLoaded);
        NotificationCenter.Instance.removeObserver(this, FileLoader.FileLoadProgressChanged);
        NotificationCenter.Instance.removeObserver(this, MessagesController.mediaCountDidLoaded);
        NotificationCenter.Instance.removeObserver(this, MessagesController.mediaDidLoaded);
        ConnectionsManager.Instance.cancelRpcsForClassGuid(classGuid);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void didReceivedNotification(int id, final Object... args) {
        if (id == FileLoader.FileDidFailedLoad) {
            String location = (String)args[0];
            if (loadingFile.containsKey(location)) {
                loadingFile.remove(location);
                localPagerAdapter.updateViews();
            }
        } else if (id == FileLoader.FileDidLoaded) {
            String location = (String)args[0];
            if (loadingFile.containsKey(location)) {
                loadingFile.remove(location);
                localPagerAdapter.updateViews();
            }
        } else if (id == FileLoader.FileLoadProgressChanged) {
            String location = (String)args[0];
            ProgressBar bar;
            if ((bar = loadingFile.get(location)) != null) {
                Float progress = (Float)args[1];
                bar.setProgress((int)(progress * 100));
            }
        } else if (id == MessagesController.mediaCountDidLoaded) {
            long uid = (Long)args[0];
            if (uid == currentDialog) {
                totalCount = (Integer)args[1];
                if (needSearchMessage) {
                    MessagesController.Instance.loadMedia(currentDialog, 0, 100, 0, true, classGuid);
                    loadingMore = true;
                } else {
                    if (mViewPager != null && localPagerAdapter != null && localPagerAdapter.imagesArr != null) {
                        final int pos = (totalCount - localPagerAdapter.imagesArr.size()) + mViewPager.getCurrentItem() + 1;
                        Utilities.RunOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                getSupportActionBar().setTitle(String.format("%d %s %d", pos, getString(R.string.Of), totalCount));
                                if (title != null) {
                                    fakeTitleView.setText(String.format("%d %s %d", pos, getString(R.string.Of), totalCount));
                                    fakeTitleView.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.AT_MOST), 40);
                                    title.setWidth(fakeTitleView.getMeasuredWidth() + (int)(8 * getResources().getDisplayMetrics().density));
                                    title.setMaxWidth(fakeTitleView.getMeasuredWidth() + (int)(8 * getResources().getDisplayMetrics().density));
                                }
                            }
                        });
                    }
                }
            }
        } else if (id == MessagesController.mediaDidLoaded) {
            long uid = (Long)args[0];
            int guid = (Integer)args[4];
            if (uid == currentDialog && guid == classGuid) {
                if (localPagerAdapter == null || localPagerAdapter.imagesArr == null) {
                    return;
                }
                loadingMore = false;
                ArrayList<MessageObject> arr = (ArrayList<MessageObject>)args[2];
                boolean fromCache = (Boolean)args[3];
                cacheEndReached = !fromCache;
                if (needSearchMessage) {
                    if (arr.isEmpty()) {
                        needSearchMessage = false;
                        return;
                    }
                    int foundIndex = -1;

                    int index = mViewPager.getCurrentItem();
                    MessageObject currentMessage = localPagerAdapter.imagesArr.get(index);

                    int added = 0;
                    for (MessageObject message : arr) {
                        if (!imagesByIdsTemp.containsKey(message.messageOwner.id)) {
                            added++;
                            imagesArrTemp.add(0, message);
                            imagesByIdsTemp.put(message.messageOwner.id, message);
                            if (message.messageOwner.id == currentMessage.messageOwner.id) {
                                foundIndex = arr.size() - added;
                            }
                        }
                    }

                    if (foundIndex != -1) {
                        mViewPager.setAdapter(null);
                        int count = mViewPager.getChildCount();
                        for (int a = 0; a < count; a++) {
                            View child = mViewPager.getChildAt(0);
                            mViewPager.removeView(child);
                        }
                        mViewPager.mCurrentView = null;
                        needSearchMessage = false;
                        ignoreSet = true;
                        mViewPager.setAdapter(localPagerAdapter = new LocalPagerAdapter(imagesArrTemp, imagesByIdsTemp));
                        mViewPager.invalidate();
                        ignoreSet = false;
                        mViewPager.setCurrentItem(foundIndex);
                        imagesArrTemp = null;
                        imagesByIdsTemp = null;
                    } else {
                        if (!cacheEndReached || !arr.isEmpty()) {
                            MessageObject lastMessage = imagesArrTemp.get(0);
                            loadingMore = true;
                            MessagesController.Instance.loadMedia(currentDialog, 0, 100, lastMessage.messageOwner.id, true, classGuid);
                        }
                    }
                } else {
                    int added = 0;
                    for (MessageObject message : arr) {
                        if (!localPagerAdapter.imagesByIds.containsKey(message.messageOwner.id)) {
                            added++;
                            localPagerAdapter.imagesArr.add(0, message);
                            localPagerAdapter.imagesByIds.put(message.messageOwner.id, message);
                        }
                    }
                    if (arr.isEmpty() && !fromCache) {
                        totalCount = arr.size();
                    }
                    int current = mViewPager.getCurrentItem();
                    ignoreSet = true;
                    imagesArrTemp = new ArrayList<MessageObject>(localPagerAdapter.imagesArr);
                    imagesByIdsTemp = new HashMap<Integer, MessageObject>(localPagerAdapter.imagesByIds);
                    mViewPager.setAdapter(localPagerAdapter = new LocalPagerAdapter(imagesArrTemp, imagesByIdsTemp));
                    mViewPager.invalidate();
                    ignoreSet = false;
                    imagesArrTemp = null;
                    imagesByIdsTemp = null;
                    if (added != 0) {
                        mViewPager.setCurrentItem(current + added);
                    }
                }
            }
        }
    }

    private TLRPC.FileLocation getCurrentFile() {
        int item = mViewPager.getCurrentItem();
        if (withoutBottom) {
            return imagesArrLocations.get(item);
        } else {
            MessageObject message = localPagerAdapter.imagesArr.get(item);
            if (message.messageOwner instanceof TLRPC.TL_messageService) {
                ArrayList<TLRPC.PhotoSize> sizes = message.messageOwner.action.photo.sizes;
                if (sizes.size() > 0) {
                    return sizes.get(sizes.size() - 1).location;
                }
            } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                ArrayList<TLRPC.PhotoSize> sizes = message.messageOwner.media.photo.sizes;
                if (sizes.size() > 0) {
                    return sizes.get(sizes.size() - 1).location;
                }
            } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaVideo) {
                return message.messageOwner.media.video.thumb.location;
            }
        }
        return null;
    }

    @Override
    public void topBtn() {
        if (getSupportActionBar().isShowing()) {
            getSupportActionBar().hide();
            startViewAnimation(bottomView, false);
        } else {
            bottomView.setVisibility(View.VISIBLE);
            getSupportActionBar().show();
            startViewAnimation(bottomView, true);
        }
    }

    @Override
    public void didShowMessageObject(MessageObject obj) {
        TLRPC.User user = MessagesController.Instance.users.get(obj.messageOwner.from_id);
        if (user != null) {
            nameTextView.setText(Utilities.formatName(user.first_name, user.last_name));
            timeTextView.setText(Utilities.formatterYearMax.format(((long)obj.messageOwner.date) * 1000));
        } else {
            nameTextView.setText("");
        }
        invalidateOptionsMenu();
        isVideo = obj.messageOwner.media != null && obj.messageOwner.media instanceof TLRPC.TL_messageMediaVideo;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getSupportMenuInflater();
        if (withoutBottom) {
            inflater.inflate(R.menu.gallery_save_only_menu, menu);
        } else {
            if (isVideo) {
                inflater.inflate(R.menu.gallery_video_menu, menu);
            } else {
                inflater.inflate(R.menu.gallery_menu, menu);
            }
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void openOptionsMenu() {
        TLRPC.FileLocation file = getCurrentFile();
        File f = new File(Utilities.getCacheDir(), file.volume_id + "_" + file.local_id + ".jpg");
        if (f.exists()) {
            super.openOptionsMenu();
        }
    }

    @Override
    public boolean onOptionsItemSelected(com.actionbarsherlock.view.MenuItem item) {
        int itemId = item.getItemId();
        processSelectedMenu(itemId);
        return true;
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        fixLayout();
    }

    private void fixLayout() {
        if (mViewPager != null) {
            ViewTreeObserver obs = mViewPager.getViewTreeObserver();
            obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    mViewPager.beginFakeDrag();
                    if (mViewPager.isFakeDragging()) {
                        mViewPager.fakeDragBy(1);
                        mViewPager.endFakeDrag();
                    }
                    mViewPager.getViewTreeObserver().removeOnPreDrawListener(this);
                    return false;
                }
            });
        }
    }

    private void processSelectedMenu(int itemId) {
        switch (itemId) {
            case android.R.id.home:
                mViewPager.setAdapter(null);
                localPagerAdapter = null;
                finish();
                System.gc();
                break;
            case R.id.gallery_menu_save:
                TLRPC.FileLocation file = getCurrentFile();
                File f = new File(Utilities.getCacheDir(), file.volume_id + "_" + file.local_id + ".jpg");
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String imageFileName = "IMG_" + timeStamp + ".jpg";
                try {
                    MediaStore.Images.Media.insertImage(getContentResolver(), f.getAbsolutePath(), imageFileName, "");
                } catch (Exception e) {
                    e.printStackTrace();
                }
                //Utilities.addMediaToGallery(addToTouchActiveAlbum(imageFileName, f.getAbsolutePath()));
                break;
//            case R.id.gallery_menu_send: {
//                Intent intent = new Intent(this, MessagesActivity.class);
//                intent.putExtra("onlySelect", true);
//                startActivityForResult(intent, 10);
//                break;
//            }
            case R.id.gallery_menu_showall: {
                if (fromAll) {
                    finish();
                } else {
                    if (!localPagerAdapter.imagesArr.isEmpty() && currentDialog != 0) {
                        finish();
                        NotificationCenter.Instance.postNotificationName(needShowAllMedia, currentDialog);
                    }
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == 10) {
                int chatId = data.getIntExtra("chatId", 0);
                int userId = data.getIntExtra("userId", 0);
                int dialog_id = 0;
                if (chatId != 0) {
                    dialog_id = -chatId;
                } else if (userId != 0) {
                    dialog_id = userId;
                }
                TLRPC.FileLocation location = getCurrentFile();
                if (dialog_id != 0 && location != null) {
                    Intent intent = new Intent(GalleryImageViewer.this, ChatActivity.class);
                    if (chatId != 0) {
                        intent.putExtra("chatId", chatId);
                    } else {
                        intent.putExtra("userId", userId);
                    }
                    startActivity(intent);
                    NotificationCenter.Instance.postNotificationName(MessagesController.closeChats);
                    finish();
                    if (withoutBottom) {
                        MessagesController.Instance.sendMessage(location, dialog_id);
                    } else {
                        int item = mViewPager.getCurrentItem();
                        MessageObject obj = localPagerAdapter.imagesArr.get(item);
                        MessagesController.Instance.sendMessage(obj, dialog_id);
                    }
                }
            }
        }
    }



    /*public Uri addToTouchActiveAlbum(String title, String filePath) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, title);
        values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        values.put(MediaStore.Images.Media.BUCKET_ID, filePath.hashCode());
        values.put(MediaStore.Images.Media.BUCKET_DISPLAY_NAME, "TMessages");

        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        //values.put(MediaStore.MediaColumns.DATA, filePath);
        values.put(MediaStore.Images.Media.DATA, filePath);
        return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    }*/

    private void startViewAnimation(final View panel, boolean up) {
        Animation animation;
        if (!up) {
            animation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 1.0f);
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    panel.setVisibility(View.INVISIBLE);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            animation.setDuration(400);
        } else {
            animation = new TranslateAnimation(Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                    Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, 0.0f);
            animation.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    panel.setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(Animation animation) {

                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });
            animation.setDuration(100);
        }
        panel.startAnimation(animation);
    }

    public class LocalPagerAdapter extends PagerAdapter {
        public ArrayList<MessageObject> imagesArr;
        public HashMap<Integer, MessageObject> imagesByIds;
        public int tagCounter = 0;

        public LocalPagerAdapter(ArrayList<MessageObject> _imagesArr, HashMap<Integer, MessageObject> _imagesByIds) {
            imagesArr = _imagesArr;
            imagesByIds = _imagesByIds;
        }

        @Override
        public void setPrimaryItem(ViewGroup container, final int position, Object object) {
            super.setPrimaryItem(container, position, object);
            if (container == null || object == null || ignoreSet) {
                return;
            }
            ((GalleryViewPager) container).mCurrentView = (PZSImageView)((View) object).findViewById(R.id.page_image);
            if (imagesArr != null) {
                didShowMessageObject(imagesArr.get(position));
                if (totalCount != 0 && !needSearchMessage) {
                    if (imagesArr.size() < totalCount && !loadingMore && position < 5) {
                        MessageObject lastMessage = imagesArr.get(0);
                        MessagesController.Instance.loadMedia(currentDialog, 0, 100, lastMessage.messageOwner.id, !cacheEndReached, classGuid);
                        loadingMore = true;
                    }


                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            getSupportActionBar().setTitle(String.format("%d %s %d", (totalCount - imagesArr.size()) + position + 1, getString(R.string.Of), totalCount));
                            if (title != null) {
                                fakeTitleView.setText(String.format("%d %s %d", (totalCount - imagesArr.size()) + position + 1, getString(R.string.Of), totalCount));
                                fakeTitleView.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.AT_MOST), 40);
                                title.setWidth(fakeTitleView.getMeasuredWidth() + (int)(8 * getResources().getDisplayMetrics().density));
                                title.setMaxWidth(fakeTitleView.getMeasuredWidth() + (int)(8 * getResources().getDisplayMetrics().density));
                            }
                        }
                    });

                }
            }
        }

        public void updateViews() {
            int count = mViewPager.getChildCount();
            for (int a = 0; a < count; a++) {
                View v = mViewPager.getChildAt(a);
                final ProgressBar progressBar = (ProgressBar)v.findViewById(R.id.action_progress);
                final TextView playButton = (TextView)v.findViewById(R.id.action_button);
                final View progressBarParent = v.findViewById(R.id.action_progress_parent);
                MessageObject message = (MessageObject)playButton.getTag();
                if (progressBar != null && message != null) {
                    processViews(progressBar, playButton, message, progressBarParent);
                }
            }
        }

        public void processViews(ProgressBar progressBar, TextView playButton, MessageObject message, View progressBarParent) {
            if (message.messageOwner.send_state != MessagesController.MESSAGE_SEND_STATE_SENDING && message.messageOwner.send_state != MessagesController.MESSAGE_SEND_STATE_SEND_ERROR) {
                String fileName = message.messageOwner.media.video.dc_id + "_" + message.messageOwner.media.video.id + ".mp4";
                boolean load = false;
                if (message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0) {
                    File f = new File(message.messageOwner.attachPath);
                    if (f.exists()) {
                        playButton.setVisibility(View.VISIBLE);
                        progressBarParent.setVisibility(View.GONE);
                        playButton.setText(getString(R.string.ViewVideo));
                    } else {
                        load = true;
                    }
                } else {
                    File cacheFile = new File(Utilities.getCacheDir(), fileName);
                    if (cacheFile.exists()) {
                        playButton.setVisibility(View.VISIBLE);
                        progressBarParent.setVisibility(View.GONE);
                        playButton.setText(getString(R.string.ViewVideo));
                    } else {
                        load = true;
                    }
                }
                if (load) {
                    Float progress = FileLoader.Instance.fileProgresses.get(fileName);
                    if (loadingFile.containsKey(fileName) || progress != null) {
                        if (progress != null) {
                            progressBar.setProgress((int) (progress * 100));
                        } else {
                            progressBar.setProgress(0);
                        }
                        progressByTag.put((Integer)progressBar.getTag(), fileName);
                        loadingFile.put(fileName, progressBar);
                        progressBarParent.setVisibility(View.VISIBLE);
                        playButton.setVisibility(View.GONE);
                    } else {
                        progressBarParent.setVisibility(View.GONE);
                        playButton.setVisibility(View.VISIBLE);
                        playButton.setText(String.format("%s %.1f MB", getString(R.string.DOWNLOAD), message.messageOwner.media.video.size / 1024.0f / 1024.0f));
                    }
                } else {
                    Integer tag = (Integer)progressBar.getTag();
                    String file = progressByTag.get(tag);
                    if (file != null) {
                        loadingFile.remove(file);
                    }
                }
            } else {
                Integer tag = (Integer)progressBar.getTag();
                String file = progressByTag.get(tag);
                if (file != null) {
                    loadingFile.remove(file);
                }
            }
        }

        @Override
        public int getItemPosition(Object object) {
            return POSITION_NONE;
        }

        @Override
        public Object instantiateItem(View collection, int position) {
            View view = View.inflate(collection.getContext(), R.layout.gallery_page_layout, null);
            ((ViewPager) collection).addView(view, 0);

            PZSImageView iv = (PZSImageView)view.findViewById(R.id.page_image);
            final ProgressBar progressBar = (ProgressBar)view.findViewById(R.id.action_progress);
            final TextView playButton = (TextView)view.findViewById(R.id.action_button);
            final View progressBarParent = view.findViewById(R.id.action_progress_parent);
            progressBar.setTag(tagCounter);
            tagCounter++;

            if (imagesArr != null) {
                final MessageObject message = imagesArr.get(position);
                view.setTag(message.messageOwner.id);
                if (message.messageOwner instanceof TLRPC.TL_messageService) {
                    ArrayList<TLRPC.PhotoSize> sizes = message.messageOwner.action.photo.sizes;
                    iv.isVideo = false;
                    if (sizes.size() > 0) {
                        TLRPC.PhotoSize sizeFull = PhotoObject.getClosestPhotoSizeWithSize(sizes, 800, 800);
                        if (message.imagePreview != null) {
                            iv.setImage(sizeFull.location, null, message.imagePreview);
                        } else {
                            iv.setImage(sizeFull.location, null, 0);
                        }
                    }
                } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                    ArrayList<TLRPC.PhotoSize> sizes = message.messageOwner.media.photo.sizes;
                    iv.isVideo = false;
                    if (sizes.size() > 0) {
                        TLRPC.PhotoSize sizeFull = PhotoObject.getClosestPhotoSizeWithSize(sizes, 800, 800);
                        if (message.imagePreview != null) {
                            iv.setImage(sizeFull.location, null, message.imagePreview);
                        } else {
                            iv.setImage(sizeFull.location, null, 0);
                        }
                    }
                } else if (message.messageOwner.media instanceof TLRPC.TL_messageMediaVideo) {
                    processViews(progressBar, playButton, message, progressBarParent);
                    playButton.setTag(message);

                    playButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            boolean loadFile = false;
                            String fileName = message.messageOwner.media.video.dc_id + "_" + message.messageOwner.media.video.id + ".mp4";
                            if (message.messageOwner.attachPath != null && message.messageOwner.attachPath.length() != 0) {
                                File f = new File(message.messageOwner.attachPath);
                                if (f.exists()) {
                                    Intent intent = new Intent(Intent.ACTION_VIEW);
                                    intent.setDataAndType(Uri.fromFile(f), "video/mp4");
                                    startActivity(intent);
                                } else {
                                    loadFile = true;
                                }
                            } else {
                                File cacheFile = new File(Utilities.getCacheDir(), fileName);
                                if (cacheFile.exists()) {
                                    Intent intent = new Intent(Intent.ACTION_VIEW);
                                    intent.setDataAndType(Uri.fromFile(cacheFile), "video/mp4");
                                    startActivity(intent);
                                } else {
                                    loadFile = true;
                                }
                            }
                            if (loadFile) {
                                if (!loadingFile.containsKey(fileName)) {
                                    progressByTag.put((Integer)progressBar.getTag(), fileName);
                                    loadingFile.put(fileName, progressBar);
                                    FileLoader.Instance.loadFile(message.messageOwner.media.video, null);
                                    progressBar.setProgress(0);
                                    progressBarParent.setVisibility(View.VISIBLE);
                                    playButton.setVisibility(View.INVISIBLE);
                                }
                            }
                        }
                    });
                    iv.isVideo = true;
                    if (message.messageOwner.media.video.thumb instanceof TLRPC.TL_photoCachedSize) {
                        iv.setImageBitmap(message.imagePreview);
                    } else {
                        iv.setImage(message.messageOwner.media.video.thumb.location, null, 0);
                    }
                }
            } else {
                iv.isVideo = false;
                iv.setImage(imagesArrLocations.get(position), null, 0);
            }

            return view;
        }

        @Override
        public void destroyItem(View collection, int position, Object view) {
            ((ViewPager)collection).removeView((View)view);
            PZSImageView iv = (PZSImageView)((View)view).findViewById(R.id.page_image);
            FileLoader.Instance.cancelLoadingForImageView(iv);
            iv.clearImage();
            final ProgressBar progressBar = (ProgressBar)((View)view).findViewById(R.id.action_progress);
            Integer tag = (Integer)progressBar.getTag();
            String file = progressByTag.get(tag);
            if (file != null) {
                loadingFile.remove(file);
            }
        }

        @Override
        public int getCount() {
            if (imagesArr != null) {
                return imagesArr.size();
            } else if (imagesArrLocations != null) {
                return imagesArrLocations.size();
            } else {
                return 0;
            }
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public void restoreState(Parcelable state, ClassLoader loader) {

        }

        @Override
        public void finishUpdate(View container) {

        }

        @Override
        public void startUpdate(View container) {

        }
    }
}
