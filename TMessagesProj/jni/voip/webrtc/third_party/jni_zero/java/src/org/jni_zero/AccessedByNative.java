// Copyright 2012 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.jni_zero;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Applied to fields that are accessed from native via JNI. Causes R8 to not rename them. */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
public @interface AccessedByNative {
    public String value() default "";
}
