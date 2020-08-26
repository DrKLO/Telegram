#include "JsonConfig.h"

namespace tgcalls {

JsonConfig::JsonConfig(Values values) : _values(values) {

}

Value JsonConfig::getValue(std::string key) {
    return _values[key];
}

}  // namespace tgcalls
