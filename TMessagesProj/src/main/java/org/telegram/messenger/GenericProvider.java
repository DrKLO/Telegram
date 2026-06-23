package org.Tajgram.messenger;

public interface GenericProvider<F, T> {
    T provide(F obj);
}
