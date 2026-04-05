package org.telegram.ui.Components.poll;

import android.text.TextUtils;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public class PollSendParams {
    public final PollAttachedMediaPack mediaPack;
    public final TLRPC.TL_messageMediaPoll poll;
    public final TLRPC.TL_inputMediaPoll inputMediaPoll;
    public final long groupId;

    public final String caption;
    public final ArrayList<TLRPC.MessageEntity> entities;

    public PollSendParams(PollAttachedMediaPack mediaPack, TLRPC.TL_messageMediaPoll poll, long groupId, String caption, ArrayList<TLRPC.MessageEntity> entities, ArrayList<Integer> correctAnswers) {
        this.mediaPack = mediaPack;
        this.groupId = groupId;
        this.caption = caption;
        this.entities = entities;

        TLRPC.TL_inputMediaPoll inputMediaPoll = new TLRPC.TL_inputMediaPoll();
        inputMediaPoll.poll = new TLRPC.TL_poll();
        inputMediaPoll.poll.id = poll.poll.id;
        inputMediaPoll.poll.flags = poll.poll.flags;

        inputMediaPoll.poll.closed = poll.poll.closed;
        inputMediaPoll.poll.public_voters = poll.poll.public_voters;
        inputMediaPoll.poll.multiple_choice = poll.poll.multiple_choice;
        inputMediaPoll.poll.open_answers = poll.poll.open_answers;
        inputMediaPoll.poll.revoting_disabled = poll.poll.revoting_disabled;
        inputMediaPoll.poll.shuffle_answers = poll.poll.shuffle_answers;
        inputMediaPoll.poll.hide_results_until_close = poll.poll.hide_results_until_close;
        inputMediaPoll.poll.creator = poll.poll.creator;
        inputMediaPoll.poll.quiz = poll.poll.quiz;

        inputMediaPoll.poll.answers = new ArrayList<>(poll.poll.answers);
        for (TLRPC.PollAnswer pollAnswer: poll.poll.answers) {
            TLRPC.TL_inputPollAnswer inputPollAnswer = new TLRPC.TL_inputPollAnswer();
            inputPollAnswer.text = pollAnswer.text;
        }
        inputMediaPoll.poll.question = poll.poll.question;
        inputMediaPoll.poll.close_period = poll.poll.close_period;
        inputMediaPoll.poll.close_date = poll.poll.close_date;
        inputMediaPoll.poll.hash = poll.poll.hash;

        if (poll.results != null && !TextUtils.isEmpty(poll.results.solution)) {
            inputMediaPoll.solution = poll.results.solution;
            inputMediaPoll.solution_entities = poll.results.solution_entities;
            inputMediaPoll.flags |= 2;
        }
        if (correctAnswers != null && !correctAnswers.isEmpty()) {
            inputMediaPoll.correct_answers = new ArrayList<>(correctAnswers);
            inputMediaPoll.flags |= 1;
        }

        if (mediaPack != null) {
            mediaPack.applyAllQuickMedia(inputMediaPoll);
            mediaPack.applyAllQuickMedia(poll);
        }

        this.poll = poll;
        this.inputMediaPoll = inputMediaPoll;
    }
}
