//! App-owned MTProto adaptation. Wallet account construction and proof signing
//! use wallet-engine's public API; no engine internals are imported here.

mod ton_connect;

pub use ton_connect::gram_ton_connect_crypto;

use base64::{Engine as _, engine::general_purpose::STANDARD};
use ton::{
    block_tlb::Msg,
    ton_core::{cell::TonCell, traits::tlb::TLB},
};

uniffi::setup_scaffolding!();
wallet_engine::uniffi_reexport_scaffolding!();

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum GramWalletError {
    #[error("Invalid Gram Wallet protocol data")]
    InvalidData,
    #[error("{message}")]
    Protocol { message: String },
}

type Result<T> = std::result::Result<T, GramWalletError>;

fn checked<T, E>(value: std::result::Result<T, E>) -> Result<T> {
    value.map_err(|_| GramWalletError::InvalidData)
}

/// Validates the single-root BoC envelope before passing untrusted cells to the wallet.
#[uniffi::export]
pub fn is_valid_cell_boc(boc: String) -> bool {
    ton_connect_core::CellBoc::try_from(boc).is_ok()
}

/// Hash of the message body, as used by the MTProto transaction matcher.
/// This deliberately differs from the hash of the complete external message.
#[uniffi::export]
pub fn message_body_hash(boc: String) -> Result<String> {
    let message = checked(Msg::<TonCell>::from_boc(checked(STANDARD.decode(boc))?))?;
    Ok(STANDARD.encode(checked(message.body.value.cell_hash())?.as_slice()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use ton::{
        block_tlb::{CommonMsgInfo, CommonMsgInfoExtIn, CommonMsgInfoInt},
        ton_core::types::{
            TonAddress,
            tlb_core::{EitherRefLayout, MsgAddressExt, TLBCoins, TLBEitherRef},
        },
    };

    #[test]
    fn hashes_body_for_both_delivery_forms_and_cell_layouts() {
        let address: TonAddress =
            "0:1111111111111111111111111111111111111111111111111111111111111111"
                .parse()
                .unwrap();
        let mut body = TonCell::builder();
        body.write_bits([0x12, 0x34], 16).unwrap();
        let body = body.build().unwrap();
        let expected = STANDARD.encode(body.cell_hash().unwrap().as_slice());
        let external = CommonMsgInfo::ExtIn(CommonMsgInfoExtIn {
            src: MsgAddressExt::NONE,
            dst: address.to_msg_address_int(),
            import_fee: TLBCoins::ZERO,
        });
        let internal = CommonMsgInfo::Int(CommonMsgInfoInt::new(
            address.to_msg_address(),
            TLBCoins::ZERO,
        ));
        for info in [external, internal] {
            for layout in [EitherRefLayout::ToRef, EitherRefLayout::ToCell] {
                let mut message = Msg::new(info.clone(), body.clone());
                message.body = TLBEitherRef::new_with_layout(body.clone(), layout);
                assert_eq!(
                    message_body_hash(STANDARD.encode(message.to_boc().unwrap())).unwrap(),
                    expected
                );
                assert_ne!(
                    STANDARD.encode(message.to_cell().unwrap().cell_hash().unwrap().as_slice()),
                    expected
                );
            }
        }
        assert!(message_body_hash("invalid".to_owned()).is_err());
        assert!(message_body_hash(STANDARD.encode(body.to_boc().unwrap())).is_err());
    }
}
