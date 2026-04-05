package org.telegram.ui.Components.poll;

import android.net.Uri;
import android.util.Base64;
import android.util.SparseArray;

import org.telegram.messenger.utils.tlutils.TlUtils;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.poll.attached.PollAttachedMediaLocation;
import org.telegram.ui.Components.poll.attached.PollAttachedMediaSticker;

public class PollAttachedMediaPack {
    public static final int INDEX_EXPLANATION = -3;
    public static final int INDEX_DESCRIPTION = -2;
    public static final int INDEX_NONE = -1;

    public final SparseArray<PollAttachedMedia> medias = new SparseArray<>();

    public PollAttachedMedia get(int index) {
        return medias.get(index);
    }

    public void set(int index, PollAttachedMedia media) {
        medias.set(index, media);
    }

    public void remove(int index) {
        medias.remove(index);
    }

    public void removeAnswerAndShift(int index) {
        if (hasKeyBiggerThan(index)) {
            removeAndShiftKeys(index);
        } else {
            medias.remove(index);
        }
    }

    private boolean hasKeyBiggerThan(int index) {
        for (int a = 0, N = medias.size(); a < N; a++) {
            final int key = medias.keyAt(a);
            if (key > index) {
                return true;
            }
        }
        return false;
    }

    private void removeAndShiftKeys(int index) {
        if (index < 0) {
            //
            return;
        }

        final SparseArray<PollAttachedMedia> medias = this.medias.clone();
        this.medias.clear();

        for (int a = 0, N = medias.size(); a < N; a++) {
            final int key = medias.keyAt(a);
            final PollAttachedMedia media = medias.valueAt(a);
            if (key < index) {
                this.medias.put(key, media);
            }
            if (key > index) {
                this.medias.put(key - 1, media);
            }
        }
    }



    public static int findInputMedia(TLRPC.TL_inputMediaPoll inputMediaPoll, TLRPC.InputMedia inputMedia) {
        if (inputMediaPoll.attached_media == inputMedia) {
            return INDEX_DESCRIPTION;
        } else if (inputMediaPoll.solution_media == inputMedia) {
            return INDEX_EXPLANATION;
        } else {
            for (int a = 0, N = inputMediaPoll.poll.answers.size(); a < N; a++) {
                final TLRPC.PollAnswer answer = inputMediaPoll.poll.answers.get(a);
                if (answer.input_media == inputMedia) {
                    return a;
                }
            }
        }
        return INDEX_NONE;
    }

    public static TLRPC.InputMedia getFirstInputMedia(TLRPC.TL_inputMediaPoll inputMediaPoll) {
        if (inputMediaPoll.attached_media != null) {
            return inputMediaPoll.attached_media;
        }
        if (inputMediaPoll.solution_media != null) {
            return inputMediaPoll.solution_media;
        }
        for (int a = 0, N = inputMediaPoll.poll.answers.size(); a < N; a++) {
            final TLRPC.PollAnswer answer = inputMediaPoll.poll.answers.get(a);
            if (answer.input_media != null) {
                return answer.input_media;
            }
        }
        return null;
    }

    public static TLRPC.InputMedia getInputMedia(TLRPC.TL_inputMediaPoll inputMediaPoll, int index) {
        if (index == INDEX_DESCRIPTION) {
            return inputMediaPoll.attached_media;
        } else if (index == INDEX_EXPLANATION) {
            return inputMediaPoll.solution_media;
        } else if (index >= 0 && index < inputMediaPoll.poll.answers.size()) {
            TLRPC.PollAnswer pollAnswer = inputMediaPoll.poll.answers.get(index);
            return pollAnswer != null ? pollAnswer.input_media : null;
        }
        return null;
    }

    public static TLRPC.MessageMedia getMedia(TLRPC.TL_messageMediaPoll inputMediaPoll, int index) {
        if (index == INDEX_DESCRIPTION) {
            return inputMediaPoll.attached_media;
        } else if (index == INDEX_EXPLANATION) {
            return inputMediaPoll.results.solution_media;
        } else if (index >= 0 && index < inputMediaPoll.poll.answers.size()) {
            TLRPC.PollAnswer pollAnswer = inputMediaPoll.poll.answers.get(index);
            return pollAnswer != null ? pollAnswer.media : null;
        }
        return null;
    }

    public static void removeInputMedia(TLRPC.TL_inputMediaPoll inputMediaPoll, int index) {
        if (index == INDEX_DESCRIPTION) {
            inputMediaPoll.attached_media = null;
        } else if (index == INDEX_EXPLANATION) {
            inputMediaPoll.solution_media = null;
        } else if (index >= 0 && index < inputMediaPoll.poll.answers.size()) {
            TLRPC.PollAnswer pollAnswer = inputMediaPoll.poll.answers.get(index);
            if (pollAnswer != null) {
                pollAnswer.input_media = null;
            }
        }
    }

    public static void setInputMedia(TLRPC.TL_inputMediaPoll inputMediaPoll, int index, TLRPC.InputMedia inputMedia) {
        if (index == INDEX_DESCRIPTION) {
            inputMediaPoll.attached_media = inputMedia;
        } else if (index == INDEX_EXPLANATION) {
            inputMediaPoll.solution_media = inputMedia;
        } else if (index >= 0 && index < inputMediaPoll.poll.answers.size()) {
            TLRPC.PollAnswer pollAnswer = inputMediaPoll.poll.answers.get(index);
            if (pollAnswer instanceof TLRPC.TL_inputPollAnswer) {
                pollAnswer.input_media = inputMedia;
            } else {
                TLRPC.TL_inputPollAnswer inputPollAnswer = new TLRPC.TL_inputPollAnswer();
                inputPollAnswer.input_media = inputMedia;
                inputPollAnswer.text = pollAnswer.text;
                inputPollAnswer.media = pollAnswer.media;
                inputPollAnswer.option = pollAnswer.option;
                inputMediaPoll.poll.answers.set(index, inputPollAnswer);
            }
        }
    }

    public void applyAllQuickMedia(TLRPC.TL_inputMediaPoll inputMediaPoll) {
        for (int a = 0, N = medias.size(); a < N; a++) {
            final int index = medias.keyAt(a);
            final PollAttachedMedia media = medias.valueAt(a);

            if (media instanceof PollAttachedMediaLocation) {
                setInputMedia(inputMediaPoll, index, TlUtils.toInputMediaGeo(((PollAttachedMediaLocation) media).media));
            } else if (media instanceof PollAttachedMediaSticker) {
                final PollAttachedMediaSticker sticker = (PollAttachedMediaSticker) media;
                TLRPC.TL_inputMediaDocument inputMediaDocument = new TLRPC.TL_inputMediaDocument();
                TLRPC.TL_inputDocument inputDocument = new TLRPC.TL_inputDocument();
                inputDocument.id = sticker.sticker.id;
                inputDocument.access_hash = sticker.sticker.access_hash;
                inputDocument.file_reference = sticker.sticker.file_reference;
                inputMediaDocument.id = inputDocument;
                setInputMedia(inputMediaPoll, index, inputMediaDocument);
            }
        }
    }

    public void applyAllQuickMedia(TLRPC.TL_messageMediaPoll messageMediaPoll) {
        for (int a = 0, N = medias.size(); a < N; a++) {
            final int index = medias.keyAt(a);
            final PollAttachedMedia media = medias.valueAt(a);

            if (media instanceof PollAttachedMediaLocation) {
                setMessageMedia(messageMediaPoll, index, ((PollAttachedMediaLocation) media).media);
            } else if (media instanceof PollAttachedMediaSticker) {
                final PollAttachedMediaSticker sticker = (PollAttachedMediaSticker) media;
                TLRPC.TL_messageMediaDocument mediaDocument = new TLRPC.TL_messageMediaDocument();
                mediaDocument.document = sticker.sticker;
                setMessageMedia(messageMediaPoll, index, mediaDocument);
            }
        }
    }

    public static void setMessageMedia(TLRPC.TL_messageMediaPoll mediaPoll, int index, TLRPC.MessageMedia media) {
        if (index == INDEX_DESCRIPTION) {
            mediaPoll.attached_media = media;
        } else if (index == INDEX_EXPLANATION) {
            mediaPoll.results.solution_media = media;
        } else if (index >= 0 && index < mediaPoll.poll.answers.size()) {
            TLRPC.PollAnswer pollAnswer = mediaPoll.poll.answers.get(index);
            if (pollAnswer instanceof TLRPC.TL_inputPollAnswer) {
                TLRPC.TL_pollAnswer mPollAnswer = new TLRPC.TL_pollAnswer();
                mPollAnswer.text = pollAnswer.text;
                mPollAnswer.option = new byte[1];
                mPollAnswer.option[0] = (byte) (48 + index);
                mPollAnswer.media = media;
                mediaPoll.poll.answers.set(index, mPollAnswer);
            } else {
                pollAnswer.media = media;
                TLRPC.TL_inputPollAnswer inputPollAnswer = new TLRPC.TL_inputPollAnswer();
                inputPollAnswer.text = pollAnswer.text;
                inputPollAnswer.media = pollAnswer.media;
                inputPollAnswer.option = pollAnswer.option;
            }
        }
    }

    public static byte[] getOptionIdQueryParameter(Uri data, String name) {
        byte[] optionId = null;
        try {
            String optionIdBase64 = data.getQueryParameter(name);
            if (optionIdBase64 != null) {
                optionId = Base64.decode(optionIdBase64, Base64.URL_SAFE | Base64.NO_PADDING);
            }
        } catch (Throwable t) {}
        return optionId;
    }

    public static boolean hasWrongInputMediaTypes(TLRPC.TL_inputMediaPoll inputMediaPoll) {
        TLRPC.InputMedia inputMedia = getInputMedia(inputMediaPoll, INDEX_DESCRIPTION);
        if (inputMedia instanceof TLRPC.TL_inputMediaUploadedPhoto || inputMedia instanceof TLRPC.TL_inputMediaUploadedDocument) {
            return true;
        }

        inputMedia = getInputMedia(inputMediaPoll, INDEX_EXPLANATION);
        if (inputMedia instanceof TLRPC.TL_inputMediaUploadedPhoto || inputMedia instanceof TLRPC.TL_inputMediaUploadedDocument) {
            return true;
        }

        for (int a = 0, N = inputMediaPoll.poll.answers.size(); a < N; a++) {
            inputMedia = getInputMedia(inputMediaPoll, a);
            if (inputMedia instanceof TLRPC.TL_inputMediaUploadedPhoto || inputMedia instanceof TLRPC.TL_inputMediaUploadedDocument) {
                return true;
            }
        }

        return false;
    }

    public static void setAttachPath(TLRPC.Message message, String attachPath, int index) {
        if (message == null) {
            return;
        }
        if (message.pollMediaAttachPaths == null) {
            message.pollMediaAttachPaths = new SparseArray<>();
        }
        message.pollMediaAttachPaths.put(index, attachPath);
    }

    public static String getAttachPath(TLRPC.Message message, int index) {
        if (message == null || message.pollMediaAttachPaths == null) {
            return null;
        }
        return message.pollMediaAttachPaths.get(index);
    }
}
