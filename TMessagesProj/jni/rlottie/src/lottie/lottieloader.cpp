/*
 * Copyright (c) 2018 Samsung Electronics Co., Ltd. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301 USA
 */

#include "lottieloader.h"
#include "lottieparser.h"

#include <cstring>
#include <fstream>
#include <unordered_map>
using namespace std;

#ifdef LOTTIE_CACHE_SUPPORT

class LottieFileCache {
public:
    static LottieFileCache &instance()
    {
        static LottieFileCache CACHE;
        return CACHE;
    }
    std::shared_ptr<LOTModel> find(const std::string &key)
    {
        auto search = mHash.find(key);
        if (search != mHash.end()) {
            return search->second;
        } else {
            return nullptr;
        }
    }
    void add(const std::string &key, std::shared_ptr<LOTModel> value)
    {
        mHash[key] = std::move(value);
    }

private:
    LottieFileCache() = default;

    std::unordered_map<std::string, std::shared_ptr<LOTModel>> mHash;
};

#else

class LottieFileCache {
public:
    static LottieFileCache &instance()
    {
        static LottieFileCache CACHE;
        return CACHE;
    }
    std::shared_ptr<LOTModel> find(const std::string &) { return nullptr; }
    void add(const std::string &, std::shared_ptr<LOTModel>) {}
};

#endif

static std::string dirname(const std::string &path)
{
    const char *ptr = strrchr(path.c_str(), '/');
    int         len = int(ptr + 1 - path.c_str());  // +1 to include '/'
    return std::string(path, 0, len);
}

bool LottieLoader::load(const std::string &path, std::map<int32_t, int32_t> &colorReplacement)
{
    mModel = LottieFileCache::instance().find(path);
    if (mModel) return true;

    std::ifstream f;
    f.open(path);

    if (!f.is_open()) {
        vCritical << "failed to open file = " << path.c_str();
        return false;
    } else {
        std::stringstream buf;
        buf << f.rdbuf();

        LottieParser parser(const_cast<char *>(buf.str().data()), dirname(path).c_str(), colorReplacement);
        if (parser.hasParsingError()) {
            f.close();
            return false;
        }
        mModel = parser.model();
        LottieFileCache::instance().add(path, mModel);

        f.close();
    }

    return true;
}

bool LottieLoader::loadFromData(std::string &&jsonData, const std::string &key,
                                const std::string &resourcePath)
{
    mModel = LottieFileCache::instance().find(key);
    if (mModel) return true;

    std::map<int32_t, int32_t> colors;
    LottieParser parser(const_cast<char *>(jsonData.c_str()), resourcePath.c_str(), colors);
    mModel = parser.model();
    LottieFileCache::instance().add(key, mModel);

    return true;
}

std::shared_ptr<LOTModel> LottieLoader::model()
{
    return mModel;
}
