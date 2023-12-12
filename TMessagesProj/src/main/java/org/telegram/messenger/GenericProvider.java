package org.telegram.messenger;

public interface GenericProvider<F, T> {
    T provide(F obj);
}
