#ifndef TGCALLS_TURN_CUSTOMIZER_H
#define TGCALLS_TURN_CUSTOMIZER_H

#include "api/turn_customizer.h"

namespace tgcalls {

class TurnCustomizerImpl : public webrtc::TurnCustomizer {
public:
    TurnCustomizerImpl();
    virtual ~TurnCustomizerImpl();

    void MaybeModifyOutgoingStunMessage(cricket::PortInterface* port, cricket::StunMessage* message) override;
    bool AllowChannelData(cricket::PortInterface* port, const void *data, size_t size, bool payload) override;
};

} // namespace tgcalls

#endif
