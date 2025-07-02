package org.telegram.messenger.pip.activity;

public interface IPipActivityListener {
    default void onStartEnterToPip() {}

    default void onCompleteEnterToPip() {}

    default void onStartExitFromPip(boolean byActivityStop) {}

    default void onCompleteExitFromPip(boolean byActivityStop) {}
}
