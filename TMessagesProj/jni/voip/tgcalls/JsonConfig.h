#ifndef TGCALLS_JSON_CONFIG_H
#define TGCALLS_JSON_CONFIG_H

#include <string>
#include <map>
#include "absl/types/variant.h"

namespace tgcalls {

typedef absl::variant<int, double, bool, std::string> Value;
typedef std::map<std::string, Value> Values;

class JsonConfig {

public:
    JsonConfig(Values values);
    Value getValue(std::string key);

private:
    Values _values;
};

} // namespace tgcalls

#endif
