//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef TGVOIP_VOIPSERVERCONFIG_H
#define TGVOIP_VOIPSERVERCONFIG_H

#include "json11.hpp"
#include "threading.h"

#include <cstdint>
#include <string>

namespace tgvoip
{

class ServerConfig
{
public:
    ServerConfig();
    ~ServerConfig();
    static ServerConfig* GetSharedInstance();
    std::int32_t GetInt(const std::string& name, std::int32_t fallback);
    double GetDouble(const std::string& name, double fallback);
    std::string GetString(const std::string& name, const std::string& fallback);
    bool GetBoolean(const std::string& name, bool fallback);
    void Update(const std::string& jsonString);

private:
    static ServerConfig* m_sharedInstance;
    json11::Json m_config;
    Mutex m_mutex;

    [[nodiscard]] bool ContainsKey(const std::string& key) const;
};

} // namespace tgvoip

#endif // TGVOIP_VOIPSERVERCONFIG_H
