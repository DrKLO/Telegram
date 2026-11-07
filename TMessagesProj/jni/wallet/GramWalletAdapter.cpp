#include "GramWalletAdapter.h"
#include "gram_wallet.hpp"

namespace gram_adapter {
std::string ton_connect_crypto(const std::string &phrase, const std::string &parameters) {
    try {
        return gram_wallet::gram_ton_connect_crypto(phrase, parameters);
    } catch (const gram_wallet::gram_wallet_error::Protocol &error) {
        throw std::invalid_argument(error.message);
    } catch (const gram_wallet::GramWalletError &error) {
        throw std::invalid_argument(error.what());
    }
}

bool is_valid_cell_boc(const std::string &boc) {
    return gram_wallet::is_valid_cell_boc(boc);
}

std::string message_body_hash(const std::string &boc) {
    return gram_wallet::message_body_hash(boc);
}
}
