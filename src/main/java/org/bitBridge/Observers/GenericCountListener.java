package org.bitBridge.Observers;

@FunctionalInterface
public interface GenericCountListener<T> {
    void onCountChanged(T source, int count);
}