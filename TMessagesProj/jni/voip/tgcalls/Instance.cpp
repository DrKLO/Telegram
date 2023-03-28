#include "Instance.h"

#include <algorithm>
#include <stdarg.h>

namespace tgcalls {
namespace {

std::function<void(std::string const &)> globalLoggingFunction;

std::map<std::string, std::shared_ptr<Meta>> &MetaMap() {
	static auto result = std::map<std::string, std::shared_ptr<Meta>>();
	return result;
}

} // namespace

std::vector<std::string> Meta::Versions() {
	auto &map = MetaMap();
	auto result = std::vector<std::string>();
	result.reserve(map.size());
	for (const auto &entry : map) {
		result.push_back(entry.first);
	}
	return result;
}

int Meta::MaxLayer() {
	auto result = 0;
	for (const auto &entry : MetaMap()) {
		result = std::max(result, entry.second->connectionMaxLayer());
	}
	return result;
}

std::unique_ptr<Instance> Meta::Create(
		const std::string &version,
		Descriptor &&descriptor) {
	const auto i = MetaMap().find(version);

	// Enforce correct protocol version.
	if (version == "2.7.7") {
		descriptor.config.protocolVersion = ProtocolVersion::V0;
	} else if (version == "5.0.0") {
		descriptor.config.protocolVersion = ProtocolVersion::V1;
	}

	return (i != MetaMap().end())
		? i->second->construct(std::move(descriptor))
		: nullptr;
}

void Meta::RegisterOne(std::shared_ptr<Meta> meta) {
	if (meta) {
		const auto versions = meta->versions();
        for (auto &it : versions) {
            MetaMap().emplace(it, meta);
        }
	}
}

void SetLoggingFunction(std::function<void(std::string const &)> loggingFunction) {
	globalLoggingFunction = loggingFunction;
}

} // namespace tgcalls
