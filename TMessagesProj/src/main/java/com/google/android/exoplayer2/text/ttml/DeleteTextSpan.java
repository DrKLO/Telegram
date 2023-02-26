/*
 * Copyright (C) 2020 The Android Open Source Project
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
 *
 */
package com.google.android.exoplayer2.text.ttml;

import android.text.Spanned;

/**
 * A span used to mark a section of text for later deletion.
 *
 * <p>This is deliberately package-private because it's not generally supported by Android and
 * results in surprising behaviour when simply calling {@link Spanned#toString} (i.e. the text isn't
 * deleted).
 *
 * <p>This span is explicitly handled in {@code TtmlNode#cleanUpText}.
 */
/* package */ final class DeleteTextSpan {}
