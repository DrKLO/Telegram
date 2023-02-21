#include "v2/ExternalSignalingConnection.h"

namespace tgcalls {

namespace {

}

ExternalSignalingConnection::ExternalSignalingConnection(std::function<void(const std::vector<uint8_t> &)> onIncomingData, std::function<void(const std::vector<uint8_t> &)> emitData) :
_onIncomingData(onIncomingData),
_emitData(emitData) {
}

ExternalSignalingConnection::~ExternalSignalingConnection() {

}

void ExternalSignalingConnection::start() {
    
}

void ExternalSignalingConnection::send(const std::vector<uint8_t> &data) {
    _emitData(data);
}

void ExternalSignalingConnection::receiveExternal(const std::vector<uint8_t> &data) {
    _onIncomingData(data);
}

}
