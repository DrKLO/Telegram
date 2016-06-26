/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.upstream;

/**
 * A component that provides media data from a URI.
 */
public interface UriDataSource extends DataSource {

  /**
   * When the source is open, returns the URI from which data is being read.
   * <p>
   * If redirection occurred, the URI after redirection is the one returned.
   *
   * @return When the source is open, the URI from which data is being read. Null otherwise.
   */
  String getUri();

}
