// Copyright 2023 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.jni_zero;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Causes build to assert that annotated classes / methods / fields are optimized away in release
 * builds (when using checkdiscard_proguard.flags).
 */
@Target({ElementType.CONSTRUCTOR, ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface CheckDiscard {
    /**
     * Describes why the element should be discarded.
     *
     * @return reason for discarding (crbug links are preferred unless reason is trivial).
     */
    String value();
}
