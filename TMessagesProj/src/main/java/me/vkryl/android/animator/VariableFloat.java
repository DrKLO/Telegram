/*
 * This file is a part of X-Android
 * Copyright © Vyacheslav Krylov 2014
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

package me.vkryl.android.animator;

public class VariableFloat {
  private float now;
  private float from, to;

  public VariableFloat (float now) {
    set(now);
  }

  public void set (float value) {
    this.now = this.to = this.from = value;
  }

  public float get () {
    return now;
  }

  public void setFrom (float from) {
    this.from = from;
  }

  public void setTo (float to) {
    this.to = to;
  }

  public boolean differs (float future) {
    return to != future;
  }

  public void finishAnimation (boolean future) {
    if (future) {
      this.from = this.now = this.to;
    } else {
      /*this.to = */this.from = this.now;
    }
  }

  public boolean applyAnimation (float changeFactor) {
    float newValue = from + (to - from) * changeFactor;
    if (this.now != newValue) {
      this.now = newValue;
      return true;
    }
    return false;
  }
}
