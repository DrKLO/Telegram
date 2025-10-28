// Copyright 2018 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.jni_zero;

/**
 * Implemented by the TEST_HOOKS field in JNI wrapper classes that are generated
 * by the JNI annotation processor. Used in tests for setting the mock
 * implementation of a {@link org.chromium.base.annotations.NativeMethods} interface.
 * @param <T> The interface annotated with {@link org.chromium.base.annotations.NativeMethods}
 */
public interface JniStaticTestMocker<T> {
    void setInstanceForTesting(T instance);
}
