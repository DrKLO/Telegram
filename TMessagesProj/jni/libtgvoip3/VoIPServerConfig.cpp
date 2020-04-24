//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "logging.h"
#include "VoIPServerConfig.h"

#include <locale>
#include <sstream>
#include <cstdlib>

using namespace tgvoip;

ServerConfig* ServerConfig::m_sharedInstance = nullptr;

ServerConfig::ServerConfig() = default;

ServerConfig::~ServerConfig() = default;

ServerConfig* ServerConfig::GetSharedInstance()
{
    if (m_sharedInstance == nullptr)
        m_sharedInstance = new ServerConfig();
    return m_sharedInstance;
}

bool ServerConfig::GetBoolean(const std::string& name, bool fallback)
{
    MutexGuard sync(m_mutex);
    if (ContainsKey(name) && m_config[name].is_bool())
        return m_config[name].bool_value();
    return fallback;
}

double ServerConfig::GetDouble(const std::string& name, double fallback)
{
    MutexGuard sync(m_mutex);
    if (ContainsKey(name) && m_config[name].is_number())
        return m_config[name].number_value();
    return fallback;
}

std::int32_t ServerConfig::GetInt(const std::string& name, std::int32_t fallback)
{
    MutexGuard sync(m_mutex);
    if (ContainsKey(name) && m_config[name].is_number())
        return m_config[name].int_value();
    return fallback;
}

std::string ServerConfig::GetString(const std::string& name, const std::string& fallback)
{
    MutexGuard sync(m_mutex);
    if (ContainsKey(name) && m_config[name].is_string())
        return m_config[name].string_value();
    return fallback;
}

void ServerConfig::Update(const std::string& jsonString)
{
    MutexGuard sync(m_mutex);
    LOGD("=== Updating voip config ===");
    LOGD("%s", jsonString.c_str());
    std::string jsonError;
    m_config = json11::Json::parse(jsonString, jsonError);
    if (!jsonError.empty())
        LOGE("Error parsing server config: %s", jsonError.c_str());
}

bool ServerConfig::ContainsKey(const std::string& key) const
{
    return m_config.object_items().find(key) != m_config.object_items().end();
}
