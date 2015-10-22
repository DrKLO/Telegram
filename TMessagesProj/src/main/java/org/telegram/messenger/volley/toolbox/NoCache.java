/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.telegram.messenger.volley.toolbox;

import org.telegram.messenger.volley.Cache;

/**
 * A cache that doesn't.
 */
public class NoCache implements Cache {
    @Override
    public void clear() {
    }

    @Override
    public Entry get(String key) {
        return null;
    }

    @Override
    public void put(String key, Entry entry) {
    }

    @Override
    public void invalidate(String key, boolean fullExpire) {
    }

    @Override
    public void remove(String key) {
    }

    @Override
    public void initialize() {
    }
}
