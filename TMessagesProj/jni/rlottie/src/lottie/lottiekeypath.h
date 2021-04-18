/*
 * Copyright (c) 2020 Samsung Electronics Co., Ltd. All rights reserved.

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#ifndef LOTTIEKEYPATH_H
#define LOTTIEKEYPATH_H

#include <string>
#include <vector>
#include "vglobal.h"

class LOTKeyPath {
public:
    LOTKeyPath(const std::string &keyPath);
    bool matches(const std::string &key, uint depth);
    uint nextDepth(const std::string key, uint depth);
    bool fullyResolvesTo(const std::string key, uint depth);

    bool propagate(const std::string key, uint depth)
    {
        return skip(key) ? true : (depth < size()) || (mKeys[depth] == "**");
    }
    bool skip(const std::string &key) const { return key == "__"; }

private:
    bool   isGlobstar(uint depth) const { return mKeys[depth] == "**"; }
    bool   isGlob(uint depth) const { return mKeys[depth] == "*"; }
    bool   endsWithGlobstar() const { return mKeys.back() == "**"; }
    size_t size() const { return mKeys.size() - 1; }

private:
    std::vector<std::string> mKeys;
};

#endif  // LOTTIEKEYPATH_H
