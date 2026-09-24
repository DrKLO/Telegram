#ifndef TGCALLS_CUSTOM_PARAMETERS_H
#define TGCALLS_CUSTOM_PARAMETERS_H

#include <map>
#include <string>

#include "third-party/json11.hpp"

namespace tgcalls {

// Custom parameters arrive from the server as a JSON object string on
// Descriptor::config::customParameters. Each engine parses it once into a map;
// these read individual keys out of that map.
//
// getCustomParameterBool/Int default to off/zero, so an absent key means
// "unchanged behaviour" - the right default for a flag that opts into a new
// behaviour. getCustomParameterBoolDefaultTrue is for a flag guarding a fix
// that is already the intended behaviour: an absent key means "on", and only
// an explicit `false` from the server turns it off (a rollback switch).

inline bool getCustomParameterBool(std::map<std::string, json11::Json> const &parameters, std::string const &name) {
    const auto value = parameters.find(name);
    if (value != parameters.end() && value->second.is_bool() && value->second.bool_value()) {
        return true;
    } else {
        return false;
    }
}

inline bool getCustomParameterBoolDefaultTrue(std::map<std::string, json11::Json> const &parameters, std::string const &name) {
    const auto value = parameters.find(name);
    if (value != parameters.end() && value->second.is_bool()) {
        return value->second.bool_value();
    }
    return true;
}

inline int getCustomParameterInt(std::map<std::string, json11::Json> const &parameters, std::string const &name) {
    const auto value = parameters.find(name);
    if (value != parameters.end() && value->second.is_number()) {
        return value->second.int_value();
    }
    return 0;
}

} // namespace tgcalls

#endif
