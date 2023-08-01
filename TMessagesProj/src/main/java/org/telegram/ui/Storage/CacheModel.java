package org.telegram.ui.Storage;

import android.util.LongSparseArray;

import org.telegram.messenger.MessageObject;
import org.telegram.ui.CacheControlActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

public class CacheModel {

    public final boolean isDialog;
    public ArrayList<CacheControlActivity.DialogFileEntities> entities = new ArrayList<>();
    private final LongSparseArray<CacheControlActivity.DialogFileEntities> entitiesByDialogId = new LongSparseArray<>();
    public final ArrayList<FileInfo> media = new ArrayList<>();
    public final ArrayList<FileInfo> documents = new ArrayList<>();
    public final ArrayList<FileInfo> music = new ArrayList<>();
    public final ArrayList<FileInfo> voice = new ArrayList<>();
    public final ArrayList<FileInfo> stories = new ArrayList<>();

    private final HashSet<Long> dialogIdsTmp = new HashSet<>();

    public HashSet<FileInfo> selectedFiles = new HashSet<>();
    private long selectedSize;
    public HashSet<Long> selectedDialogs = new HashSet<>();

    //dialogs only properties
    public boolean allPhotosSelected;
    public boolean allVideosSelected;
    public boolean allDocumentsSelected;
    public boolean allMusicSelected;
    public boolean allVoiceSelected;
    public boolean allStoriesSelected;
    public long photosSelectedSize;
    public long videosSelectedSize;
    public long documentsSelectedSize;
    public long musicSelectedSize;
    public long voiceSelectedSize;
    public long storiesSelectedSize;

    public CacheModel(boolean isDialog) {
        this.isDialog = isDialog;
    }

    public void add(int addToType, FileInfo fileInfo) {
        getListByType(addToType).add(fileInfo);
    }

    private ArrayList<CacheModel.FileInfo> getListByType(int type) {
        if (type == CacheControlActivity.TYPE_PHOTOS) {
            return media;
        } else if (type == CacheControlActivity.TYPE_VIDEOS) {
            return media;
        } else if (type == CacheControlActivity.TYPE_DOCUMENTS) {
            return documents;
        } else if (type == CacheControlActivity.TYPE_MUSIC) {
            return music;
        } else if (type == CacheControlActivity.TYPE_VOICE) {
            return voice;
        } else if (type == CacheControlActivity.TYPE_STORIES) {
            return stories;
        }
        return null;
    }

    private void remove(int type, FileInfo fileInfo) {
        ArrayList<FileInfo> list = getListByType(type);
        if (list != null) {
            list.remove(fileInfo);
        }
    }

    public void remove(CacheControlActivity.DialogFileEntities dialogEntities) {
        entities.remove(dialogEntities);
        //TODO
    }

    public void sortBySize() {
        sort(media);
        sort(documents);
        sort(music);
        sort(voice);
        sort(stories);
    }

    private void sort(ArrayList<FileInfo> entities) {
        Collections.sort(entities, (o1, o2) -> {
            if (o2.size > o1.size) {
                return 1;
            } else if (o2.size < o1.size) {
                return -1;
            }
            return 0;
        });
    }

    public void toggleSelect(FileInfo fileInfo) {
        if (selectedFiles.contains(fileInfo)) {
            selectedFiles.remove(fileInfo);
            incSize(fileInfo, false);
            selectedSize -= fileInfo.size;
            checkAllFilesSelected(fileInfo.type, false);
        } else {
            selectedFiles.add(fileInfo);
            incSize(fileInfo, true);
            selectedSize += fileInfo.size;
            checkAllFilesSelected(fileInfo.type, true);
        }
        checkSelectedDialogs();
    }

    private void checkAllFilesSelected(int type, boolean added) {
        if (!isDialog) {
            return;
        }
        if (!added) {
            if (type == CacheControlActivity.TYPE_PHOTOS) {
                allPhotosSelected = false;
            } else if (type == CacheControlActivity.TYPE_VIDEOS) {
                allVideosSelected = false;
            } else if (type == CacheControlActivity.TYPE_DOCUMENTS) {
                allDocumentsSelected = false;
            } else if (type == CacheControlActivity.TYPE_MUSIC) {
                allMusicSelected = false;
            } else if (type == CacheControlActivity.TYPE_VOICE) {
                allVoiceSelected = false;
            } else if (type == CacheControlActivity.TYPE_STORIES) {
                allStoriesSelected = false;
            }
        } else {
            if (type == CacheControlActivity.TYPE_PHOTOS) {
                allPhotosSelected = checkAllFilesSelectedInArray(type, media);
            } else if (type == CacheControlActivity.TYPE_VIDEOS) {
                allVideosSelected = checkAllFilesSelectedInArray(type, media);
            } else if (type == CacheControlActivity.TYPE_DOCUMENTS) {
                allDocumentsSelected = checkAllFilesSelectedInArray(type, documents);
            } else if (type == CacheControlActivity.TYPE_MUSIC) {
                allMusicSelected = checkAllFilesSelectedInArray(type, music);
            } else if (type == CacheControlActivity.TYPE_VOICE) {
                allVoiceSelected = checkAllFilesSelectedInArray(type, voice);
            } else if (type == CacheControlActivity.TYPE_STORIES) {
                allStoriesSelected = checkAllFilesSelectedInArray(type, stories);
            }
        }
    }

    private boolean checkAllFilesSelectedInArray(int type, ArrayList<FileInfo> files) {
        boolean selected = true;
        for (int i = 0; i < files.size(); i++) {
            if (files.get(i).type == type && !selectedFiles.contains(files.get(i))) {
                selected = false;
                break;
            }
        }
        return selected;
    }

    public void toggleSelect(CacheControlActivity.DialogFileEntities entities) {
        if (!selectedDialogs.contains(entities.dialogId)) {
            for (int i = 0; i < entities.entitiesByType.size(); i++) {
                CacheControlActivity.FileEntities entriesType = entities.entitiesByType.valueAt(i);
                for (FileInfo fileInfo : entriesType.files) {
                    if (selectedFiles.add(fileInfo)) {
                        selectedSize += fileInfo.size;
                    }
                }
            }
        } else {
            for (int i = 0; i < entities.entitiesByType.size(); i++) {
                CacheControlActivity.FileEntities entriesType = entities.entitiesByType.valueAt(i);
                for (FileInfo fileInfo : entriesType.files) {
                    if (selectedFiles.remove(fileInfo)) {
                        selectedSize -= fileInfo.size;
                    }
                }
            }
        }
        checkSelectedDialogs();
    }

    private void checkSelectedDialogs() {
        if (isDialog) {
            return;
        }
        dialogIdsTmp.clear();
        for (FileInfo fileInfo : selectedFiles) {
            if (fileInfo.dialogId != 0) {
                dialogIdsTmp.add(fileInfo.dialogId);
            }
        }
        selectedDialogs.clear();
        lookup:
        for (Long dialogId : dialogIdsTmp) {
            CacheControlActivity.DialogFileEntities entity = entitiesByDialogId.get(dialogId);
            if (entity != null) {
                for (int i = 0; i < entity.entitiesByType.size(); i++) {
                    CacheControlActivity.FileEntities entriesType = entity.entitiesByType.valueAt(i);
                    for (FileInfo fileInfo : entriesType.files) {
                        if (!selectedFiles.contains(fileInfo)) {
                            continue lookup;
                        }
                    }
                }
                selectedDialogs.add(entity.dialogId);
            }
        }
    }

    public boolean isSelected(FileInfo file) {
        return selectedFiles.contains(file);
    }

    public int getSelectedFiles() {
        return selectedFiles.size();
    }

    public void clearSelection() {
        selectedSize = 0;
        selectedFiles.clear();
        selectedDialogs.clear();
    }

    public void setEntities(ArrayList<CacheControlActivity.DialogFileEntities> entities) {
        this.entities = entities;
        entitiesByDialogId.clear();
        for (CacheControlActivity.DialogFileEntities entity : entities) {
            entitiesByDialogId.put(entity.dialogId, entity);
        }
    }

    public boolean isSelected(long dialogId) {
        return selectedDialogs.contains(dialogId);
    }

    public CacheControlActivity.DialogFileEntities removeSelectedFiles() {
        CacheControlActivity.DialogFileEntities entities = new CacheControlActivity.DialogFileEntities(0);
        for (FileInfo fileInfo : selectedFiles) {
            entities.addFile(fileInfo, fileInfo.type);
            CacheControlActivity.DialogFileEntities entitiesFromRemove = entitiesByDialogId.get(fileInfo.dialogId);
            if (entitiesFromRemove == null) {
                continue;
            }
            entitiesFromRemove.removeFile(fileInfo);
            if (entitiesFromRemove.isEmpty()) {
                entitiesByDialogId.remove(fileInfo.dialogId);
                this.entities.remove(entitiesFromRemove);
            }
            remove(fileInfo.type, fileInfo);
        }
        return entities;
    }

    public long getSelectedFilesSize() {
        return selectedSize;
    }

    public long getSelectedFilesSize(int type) {
        if (type == CacheControlActivity.TYPE_PHOTOS) {
            return photosSelectedSize;
        } else if (type == CacheControlActivity.TYPE_VIDEOS) {
            return videosSelectedSize;
        } else if (type == CacheControlActivity.TYPE_DOCUMENTS) {
            return documentsSelectedSize;
        } else if (type == CacheControlActivity.TYPE_MUSIC) {
            return musicSelectedSize;
        } else if (type == CacheControlActivity.TYPE_VOICE) {
            return voiceSelectedSize;
        }
        return -1;
    }

    public void selectAllFiles() {
        for (int i = 0; i < media.size(); i++) {
            selectedFiles.add(media.get(i));
            if (media.get(i).type == CacheControlActivity.TYPE_PHOTOS) {
                photosSelectedSize += media.get(i).size;
            } else {
                videosSelectedSize += media.get(i).size;
            }
        }
        for (int i = 0; i < documents.size(); i++) {
            selectedFiles.add(documents.get(i));
            documentsSelectedSize += documents.get(i).size;
        }
        for (int i = 0; i < music.size(); i++) {
            selectedFiles.add(music.get(i));
            musicSelectedSize += music.get(i).size;
        }
        for (int i = 0; i < voice.size(); i++) {
            selectedFiles.add(voice.get(i));
            voiceSelectedSize += voice.get(i).size;
        }
        allPhotosSelected = true;
        allVideosSelected = true;
        allDocumentsSelected = true;
        allMusicSelected = true;
        allVoiceSelected = true;
    }

    public void allFilesSelcetedByType(int type, boolean selected) {
        ArrayList<FileInfo> files = null;
        if (type == CacheControlActivity.TYPE_PHOTOS) {
            files = media;
            allPhotosSelected = selected;
        } else if (type == CacheControlActivity.TYPE_VIDEOS) {
            files = media;
            allVideosSelected = selected;
        } else if (type == CacheControlActivity.TYPE_DOCUMENTS) {
            files = documents;
            allDocumentsSelected = selected;
        } else if (type == CacheControlActivity.TYPE_MUSIC) {
            files = music;
            allMusicSelected = selected;
        } else if (type == CacheControlActivity.TYPE_VOICE) {
            files = voice;
            allVoiceSelected = selected;
        } else if (type == CacheControlActivity.TYPE_STORIES) {
            files = stories;
            allStoriesSelected = selected;
        }
        if (files != null) {
            for (int i = 0; i < files.size(); i++) {
                if (files.get(i).type == type) {
                    if (selected) {
                        if (!selectedFiles.contains(files.get(i))) {
                            selectedFiles.add(files.get(i));
                            incSize(files.get(i), true);
                        }
                    } else {
                        if (selectedFiles.contains(files.get(i))) {
                            selectedFiles.remove(files.get(i));
                            incSize(files.get(i), false);
                        }
                    }
                }
            }
        }
    }

    private void incSize(FileInfo fileInfo, boolean inc) {
        long size = inc ? fileInfo.size : -fileInfo.size;
        if (fileInfo.type == CacheControlActivity.TYPE_PHOTOS) {
            photosSelectedSize += size;
        } else if (fileInfo.type == CacheControlActivity.TYPE_VIDEOS) {
            videosSelectedSize += size;
        } else if (fileInfo.type == CacheControlActivity.TYPE_DOCUMENTS) {
            documentsSelectedSize += size;
        } else if (fileInfo.type == CacheControlActivity.TYPE_MUSIC) {
            musicSelectedSize += size;
        } else if (fileInfo.type == CacheControlActivity.TYPE_VOICE) {
            voiceSelectedSize += size;
        } else if (fileInfo.type == CacheControlActivity.TYPE_STORIES) {
            storiesSelectedSize += size;
        }
    }

    public boolean isEmpty() {
        return media.isEmpty() && documents.isEmpty() && music.isEmpty() && (isDialog || entities.isEmpty());
    }

    public void onFileDeleted(FileInfo fileInfo) {
        if (selectedFiles.remove(fileInfo)) {
            selectedSize -= fileInfo.size;
        }
        remove(fileInfo.type, fileInfo);
    }

    public static class FileInfo {
        public final File file;
        public long dialogId;
        public long size;
        public int type;

        public FileMetadata metadata;
        public MessageObject messageObject;
        public int messageId;
        public int messageType;

        public FileInfo(File cacheFileTemp) {
            file = cacheFileTemp;
        }

        public static class FileMetadata {
            public boolean loading;
            public long duration;
            public String title;
            public String author;
        }
    }
}
