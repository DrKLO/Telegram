#include "TurnCustomizerImpl.h"

#include "api/transport/stun.h"

namespace tgcalls {

TurnCustomizerImpl::TurnCustomizerImpl() {
}

TurnCustomizerImpl::~TurnCustomizerImpl() {
}

void TurnCustomizerImpl::MaybeModifyOutgoingStunMessage(cricket::PortInterface* port, cricket::StunMessage* message) {
    message->AddAttribute(std::make_unique<cricket::StunByteStringAttribute>(cricket::STUN_ATTR_SOFTWARE, "Telegram "));
}

bool TurnCustomizerImpl::AllowChannelData(cricket::PortInterface* port, const void *data, size_t size, bool payload) {
    return true;
}

}
