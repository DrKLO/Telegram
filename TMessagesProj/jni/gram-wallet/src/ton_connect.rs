use std::sync::Arc;

use base64::{Engine as _, engine::general_purpose::STANDARD};
use serde::Deserialize;
use serde_json::{Value, json};
use wallet_engine::*;
use zeroize::Zeroizing;

use crate::{GramWalletError, Result};

fn invalid(message: impl Into<String>) -> GramWalletError {
    GramWalletError::Protocol {
        message: message.into(),
    }
}

fn protocol<T, E: std::fmt::Display>(
    operation: &str,
    result: std::result::Result<T, E>,
) -> Result<T> {
    result.map_err(|error| invalid(format!("{operation}: {error}")))
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct Parameters {
    dapp_client_id: String,
    session_nonce: String,
    wallet_public_key: String,
    wallet_address: String,
    #[serde(default)]
    client_id: Option<String>,
    #[serde(default)]
    challenge: Option<String>,
    #[serde(default)]
    decrypt: Option<String>,
    #[serde(default)]
    encrypt: Option<Value>,
    #[serde(default)]
    account: bool,
    #[serde(default)]
    proof_domain: Option<String>,
    #[serde(default)]
    proof_payload: Option<String>,
    #[serde(default)]
    sign_data: Option<ton_connect_core::SignDataPayload>,
    #[serde(default)]
    sign_domain: Option<String>,
    #[serde(default)]
    timestamp: Option<u64>,
}

// An invocation can only read the already-authorized phrase. It cannot persist
// secrets, submit transactions, or modify a journal.
struct PhraseHost(Zeroizing<String>);

fn unavailable() -> ProtectedSecretHostError {
    ProtectedSecretHostError::Failed {
        kind: ProtectedSecretHostErrorKind::PolicyViolation,
        diagnostic: "Unsupported adapter operation".to_owned(),
    }
}

#[async_trait::async_trait]
impl WalletPlatformHost for PhraseHost {
    async fn read_protected_secret(
        &self,
        request: ProtectedSecretRead,
    ) -> std::result::Result<Vec<u8>, ProtectedSecretHostError> {
        if !matches!(
            request.reason,
            SecretAccessReason::SignTonConnectProof
                | SecretAccessReason::DeriveTonConnectSessionKey
                | SecretAccessReason::SignTonConnectData
        ) {
            return Err(unavailable());
        }
        Ok(self.0.as_bytes().to_vec())
    }
    async fn store_protected_secret(
        &self,
        _: ProtectedSecretStore,
    ) -> std::result::Result<(), ProtectedSecretHostError> {
        Err(unavailable())
    }
    async fn delete_protected_secret(
        &self,
        _: ProtectedSecretRef,
    ) -> std::result::Result<(), ProtectedSecretHostError> {
        Err(unavailable())
    }
    async fn load_journal(
        &self,
        _: JournalKey,
    ) -> std::result::Result<Option<JournalRecord>, JournalHostError> {
        Ok(None)
    }
    async fn compare_exchange_journal(
        &self,
        _: JournalCompareExchange,
    ) -> std::result::Result<JournalCompareExchangeResult, JournalHostError> {
        Err(JournalHostError::Failed {
            kind: JournalHostErrorKind::Unavailable,
            diagnostic: "No adapter journal".to_owned(),
        })
    }
}

struct Session {
    derived: Arc<TonConnectDerivedSession>,
    lifecycle: Arc<WalletLifecycle>,
    descriptor: WalletDescriptor,
}

impl Session {
    fn new(phrase: String, p: &Parameters) -> Result<Self> {
        let phrase = Zeroizing::new(phrase);
        let normalized = Zeroizing::new(
            phrase
                .split_whitespace()
                .map(str::to_lowercase)
                .collect::<Vec<_>>()
                .join(" "),
        );
        // The descriptor uses the immutable anchor; the derived session below
        // exposes the current signing key separately for the MTProto key check.
        let anchor = rotation_mnemonic_public_key(normalized.to_string())
            .map_err(|_| invalid("Invalid wallet recovery phrase"))?;
        let lifecycle = WalletLifecycle::new(Arc::new(PhraseHost(normalized)));
        // Gram Wallet currently uses mainnet, as does the Android WalletClient.
        let descriptor = WalletDescriptor {
            record_id: "ton-connect".to_owned(),
            address: protocol(
                "Parse wallet address",
                TonAddressString::try_from(p.wallet_address.clone()),
            )?,
            public_key: anchor,
            network: Network::Mainnet,
            secret_ref: ProtectedSecretRef {
                value: "wallet:ton-connect:mnemonic".to_owned(),
            },
        };
        // Upstream verifies that the descriptor's anchor key derives its address.
        protocol(
            "Validate wallet account",
            lifecycle.ton_connect_account(descriptor.clone()),
        )?;
        let derived = protocol(
            "Derive TON Connect session",
            futures::executor::block_on(lifecycle.derive_ton_connect_session(
                TonConnectDerivedSessionRequest {
                    descriptor: descriptor.clone(),
                    dapp_client_id: p.dapp_client_id.to_lowercase(),
                    nonce: protocol("Decode session nonce", STANDARD.decode(&p.session_nonce))?,
                },
            )),
        )?;
        if protocol(
            "Decode wallet public key",
            STANDARD.decode(&p.wallet_public_key),
        )? != derived.signing_public_key()
        {
            return Err(invalid(
                "Recovery phrase does not match the current wallet signing key",
            ));
        }
        if let Some(expected) = &p.client_id {
            if !derived.public_key_hex().eq_ignore_ascii_case(expected) {
                return Err(invalid(
                    "Derived session key does not match the registered client ID",
                ));
            }
        }
        Ok(Self {
            derived,
            lifecycle,
            descriptor,
        })
    }

    fn client_id(&self) -> String {
        self.derived.public_key_hex()
    }

    fn encrypt(&self, event: &Value) -> Result<String> {
        let body = protocol("Serialize TON Connect response", serde_json::to_vec(event))?;
        Ok(STANDARD.encode(protocol(
            "Encrypt TON Connect response",
            self.derived.encrypt_envelope(body),
        )?))
    }
}

/// Stateless cryptographic adapter. Java owns TON Connect envelopes, methods,
/// capabilities and MTProto workflow; secret keys never leave this call.
#[uniffi::export]
pub fn gram_ton_connect_crypto(phrase: String, parameters: String) -> Result<String> {
    let p: Parameters = protocol("Parse crypto parameters", serde_json::from_str(&parameters))?;
    let session = Session::new(phrase, &p)?;
    let mut output = json!({"clientId": session.client_id()});
    if let Some(challenge) = &p.challenge {
        let bytes = protocol("Decode challenge", STANDARD.decode(challenge))?;
        let answer = Zeroizing::new(protocol(
            "Open session challenge",
            session.derived.open_challenge(bytes),
        )?);
        output["challengeAnswer"] = json!(STANDARD.encode(answer.as_slice()));
    }
    if let Some(body) = &p.decrypt {
        let body = protocol("Decode encrypted body", STANDARD.decode(body))?;
        if body.len() < 40 || body.len() > 1024 * 1024 {
            return Err(invalid("Encrypted body must be between 40 bytes and 1 MiB"));
        }
        let plaintext = Zeroizing::new(protocol(
            "Decrypt request",
            session.derived.decrypt_envelope(body),
        )?);
        output["plaintext"] = json!(protocol(
            "Decode UTF-8 request",
            std::str::from_utf8(&plaintext)
        )?);
    }
    if let Some(message) = &p.encrypt {
        output["body"] = json!(session.encrypt(message)?);
    }
    if p.account {
        let account = protocol(
            "Get wallet account",
            session
                .lifecycle
                .ton_connect_account(session.descriptor.clone()),
        )?;
        output["account"] = json!({"name":"ton_addr", "address":account.address,
            "network":account.network, "walletStateInit":account.wallet_state_init,
            "publicKey":hex::encode(protocol("Decode signing key", STANDARD.decode(&p.wallet_public_key))?)});
    }
    if let Some(domain) = &p.proof_domain {
        // The import proof uses this same signing format. Keep its domain
        // unavailable to dApps even if a caller skips the Java validation.
        if domain
            .trim_end_matches('.')
            .eq_ignore_ascii_case("telegram.org")
        {
            return Err(invalid(
                "The telegram.org proof domain is reserved for wallet import",
            ));
        }
        let timestamp = p
            .timestamp
            .filter(|t| *t > 0)
            .ok_or_else(|| invalid("Missing proof timestamp"))?;
        let payload = p
            .proof_payload
            .as_ref()
            .ok_or_else(|| invalid("Missing proof payload"))?;
        let signed = protocol(
            "Sign proof",
            futures::executor::block_on(session.lifecycle.sign_ton_connect_proof(
                TonConnectProofSignRequest {
                    descriptor: session.descriptor.clone(),
                    domain: domain.clone(),
                    timestamp,
                    payload: payload.clone(),
                },
            )),
        )?;
        output["signature"] = json!(STANDARD.encode(signed.signature));
    }
    if let Some(payload) = &p.sign_data {
        use ton_connect_core::SignDataPayload;
        let value = protocol("Serialize signing payload", serde_json::to_value(payload))?;
        let request = TonConnectSignDataRequest {
            payload: match payload {
                SignDataPayload::Text { text, .. } => {
                    TonConnectSignDataPayload::Text { text: text.clone() }
                }
                SignDataPayload::Binary { bytes, .. } => TonConnectSignDataPayload::Binary {
                    bytes: bytes.as_str().to_owned(),
                },
                SignDataPayload::Cell { schema, cell, .. } => TonConnectSignDataPayload::Cell {
                    schema: schema.clone(),
                    cell: cell.as_str().to_owned(),
                },
            },
            network: value
                .get("network")
                .and_then(Value::as_str)
                .map(str::to_owned),
            from: value.get("from").and_then(Value::as_str).map(str::to_owned),
        };
        let domain = p
            .sign_domain
            .as_deref()
            .filter(|d| !d.is_empty())
            .ok_or_else(|| invalid("Missing signing domain"))?;
        let timestamp = p
            .timestamp
            .filter(|t| *t > 0)
            .ok_or_else(|| invalid("Missing signing timestamp"))?;
        let signed = protocol(
            "Sign data",
            futures::executor::block_on(session.lifecycle.sign_ton_connect_data(
                TonConnectSignDataSignRequest {
                    descriptor: session.descriptor.clone(),
                    request,
                    domain: domain.to_owned(),
                    timestamp,
                },
            )),
        )?;
        let account = protocol(
            "Get signing account",
            session
                .lifecycle
                .ton_connect_account(session.descriptor.clone()),
        )?;
        output["signature"] = json!(STANDARD.encode(signed.signature));
        output["address"] = json!(account.address);
    }
    Ok(output.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;
    use ton::{
        block_tlb::StateInit,
        ton_core::{cell::TonHash, traits::tlb::TLB},
        ton_wallet::{WALLET_SUBWALLET_ID_DEFAULT, WalletData, WalletVersion},
    };
    use ton_connect_core::{
        ClientId, Ed25519PublicKey, Ed25519Signature, RawAccountAddress, SessionCrypto,
        SignatureDomain, ton_proof_signing_hash, verify_signature,
    };
    const PHRASE: &str =
        "notice tortoise soup strong gun divide offer process salon siren general carry";
    const ROTATED: &str = "notice tortoise soup strong gun divide offer process salon siren general carry clump left year void clutch tool case burden fix income champion lounge";
    fn parameters(peer: &SessionCrypto, phrase: &str) -> Value {
        let anchor = rotation_mnemonic_public_key(
            phrase
                .split_whitespace()
                .take(12)
                .collect::<Vec<_>>()
                .join(" "),
        )
        .unwrap();
        let code = WalletVersion::get_code(WalletVersion::Wallet)
            .unwrap()
            .clone();
        let data = WalletData::new(
            WALLET_SUBWALLET_ID_DEFAULT,
            TonHash::from_slice(&anchor).unwrap(),
        )
        .to_cell()
        .unwrap();
        let address = StateInit::new(code, data)
            .derive_address(0)
            .unwrap()
            .to_hex();
        let current_key = rotation_mnemonic_public_key(
            phrase.split_whitespace().collect::<Vec<_>>()[phrase.split_whitespace().count() - 12..]
                .join(" "),
        )
        .unwrap();
        json!({"dappClientId":peer.client_id().to_string(),"sessionNonce":STANDARD.encode([7u8;32]),
            "walletPublicKey":STANDARD.encode(current_key),"walletAddress":address})
    }

    fn call(phrase: &str, p: &Value) -> Value {
        serde_json::from_str(&gram_ton_connect_crypto(phrase.to_owned(), p.to_string()).unwrap())
            .unwrap()
    }

    #[test]
    fn derivation_matches_independent_vector() {
        let peer = SessionCrypto::generate().unwrap();
        let mut p = parameters(&peer, PHRASE);
        p["dappClientId"] = json!("11".repeat(32));
        // Reference vector from Python hashlib/hmac and OpenSSL X25519.
        p["sessionNonce"] = json!(STANDARD.encode([7u8; 32]));
        assert_eq!(
            call(PHRASE, &p)["clientId"],
            "908ba37dfad5b2d9898491ec4c8955f9342407261291d4065059d339d2c1f679"
        );
    }

    #[test]
    fn boxes_and_challenges_interoperate_and_authenticate() {
        let peer = SessionCrypto::generate().unwrap();
        let mut p = parameters(&peer, PHRASE);
        let identity = call(PHRASE, &p);
        let client: ClientId = identity["clientId"].as_str().unwrap().parse().unwrap();
        p["clientId"] = identity["clientId"].clone();
        let request = br#"{"id":"request-ABC","method":"disconnect","params":[]}"#;
        p["decrypt"] = json!(STANDARD.encode(peer.encrypt(client, request).unwrap()));
        let ephemeral = SessionCrypto::generate().unwrap();
        let mut challenge = ephemeral.client_id().to_bytes().to_vec();
        challenge.extend(ephemeral.encrypt(client, &[42; 32]).unwrap());
        p["challenge"] = json!(STANDARD.encode(challenge));
        p["encrypt"] = json!({"id":"request-ABC","result":{}});
        let result = call(PHRASE, &p);
        assert_eq!(result["plaintext"], std::str::from_utf8(request).unwrap());
        assert_eq!(result["challengeAnswer"], STANDARD.encode([42; 32]));
        let response: Value = serde_json::from_slice(
            &peer
                .decrypt(
                    client,
                    &STANDARD.decode(result["body"].as_str().unwrap()).unwrap(),
                )
                .unwrap(),
        )
        .unwrap();
        assert_eq!(response, p["encrypt"]);
        assert_ne!(result["body"], call(PHRASE, &p)["body"]);
        let mut bad = p.clone();
        bad["clientId"] = json!(peer.client_id().to_string());
        assert!(gram_ton_connect_crypto(PHRASE.to_owned(), bad.to_string()).is_err());
        let mut body = STANDARD.decode(p["decrypt"].as_str().unwrap()).unwrap();
        *body.last_mut().unwrap() ^= 1;
        p["decrypt"] = json!(STANDARD.encode(body));
        assert!(gram_ton_connect_crypto(PHRASE.to_owned(), p.to_string()).is_err());
    }

    #[test]
    fn rotated_session_preserves_sign_message_envelopes_and_rejects_stale_keys() {
        let peer = SessionCrypto::generate().unwrap();
        let mut p = parameters(&peer, ROTATED);
        let identity = call(ROTATED, &p);
        let client: ClientId = identity["clientId"].as_str().unwrap().parse().unwrap();
        let request = br#"{"id":"message-ABC","method":"signMessage","params":["{}"]}"#;
        p["decrypt"] = json!(STANDARD.encode(peer.encrypt(client, request).unwrap()));
        p["encrypt"] = json!({"id":"message-ABC", "result":{"internalBoc":"opaque"}});
        let result = call(ROTATED, &p);
        assert_eq!(result["plaintext"], std::str::from_utf8(request).unwrap());
        let response: Value = serde_json::from_slice(
            &peer
                .decrypt(
                    client,
                    &STANDARD.decode(result["body"].as_str().unwrap()).unwrap(),
                )
                .unwrap(),
        )
        .unwrap();
        assert_eq!(response, p["encrypt"]);
        p["walletPublicKey"] =
            json!(STANDARD.encode(rotation_mnemonic_public_key(ROTATED.to_owned()).unwrap()));
        assert!(gram_ton_connect_crypto(ROTATED.to_owned(), p.to_string()).is_err());
    }

    #[test]
    fn signs_all_data_types_with_the_current_key_and_bound_context() {
        for phrase in [PHRASE, ROTATED] {
            let peer = SessionCrypto::generate().unwrap();
            let mut p = parameters(&peer, phrase);
            p["signDomain"] = json!("example.com");
            p["timestamp"] = json!(1700000000u64);
            for payload in [
                json!({"type":"text", "text":"Hello 🌍\nKeep this line", "network":"-239"}),
                json!({"type":"binary", "bytes":"AAEC/f7/"}),
                json!({"type":"cell", "schema":"empty$_ = Empty;", "cell":"te6ccgEBAQEAAgAAAA=="}),
            ] {
                p["signData"] = payload.clone();
                let signed = call(phrase, &p);
                let mut result: ton_connect_core::SignDataResult = serde_json::from_value(json!({
                    "signature":signed["signature"], "address":signed["address"], "domain":"example.com",
                    "timestamp":1700000000u64, "payload":payload
                })).unwrap();
                let key = Ed25519PublicKey::from_bytes(
                    rotation_mnemonic_public_key(
                        phrase.split_whitespace().collect::<Vec<_>>()
                            [phrase.split_whitespace().count() - 12..]
                            .join(" "),
                    )
                    .unwrap()
                    .try_into()
                    .unwrap(),
                );
                assert!(result.verify(&key).unwrap());
                result.domain = "other.example".to_owned();
                assert!(!result.verify(&key).unwrap());
                result.domain = "example.com".to_owned();
                result.timestamp += 1;
                assert!(!result.verify(&key).unwrap());
            }
            for payload in [
                json!({"type":"text", "text":"test", "network":"-3"}),
                json!({"type":"text", "text":"test", "from":"0:1111111111111111111111111111111111111111111111111111111111111111"}),
                json!({"type":"binary", "bytes":"bad!"}),
                json!({"type":"cell", "schema":"empty$_ = Empty;", "cell":"bad"}),
            ] {
                p["signData"] = payload;
                assert!(gram_ton_connect_crypto(phrase.to_owned(), p.to_string()).is_err());
            }
        }
    }

    #[test]
    fn proof_uses_current_key_and_refuses_import_domain() {
        for phrase in [PHRASE, ROTATED] {
            let peer = SessionCrypto::generate().unwrap();
            let mut p = parameters(&peer, phrase);
            p["account"] = json!(true);
            p["proofDomain"] = json!("example.com");
            p["proofPayload"] = json!("challenge");
            p["timestamp"] = json!(1700000000u64);
            let result = call(phrase, &p);
            let address: RawAccountAddress = result["account"]["address"]
                .as_str()
                .unwrap()
                .parse()
                .unwrap();
            let hash =
                ton_proof_signing_hash(&address, "example.com", 1700000000, "challenge").unwrap();
            let signature = Ed25519Signature::from_bytes(
                STANDARD
                    .decode(result["signature"].as_str().unwrap())
                    .unwrap()
                    .try_into()
                    .unwrap(),
            );
            let key = Ed25519PublicKey::from_bytes(
                rotation_mnemonic_public_key(
                    phrase.split_whitespace().collect::<Vec<_>>()
                        [phrase.split_whitespace().count() - 12..]
                        .join(" "),
                )
                .unwrap()
                .try_into()
                .unwrap(),
            );
            assert!(verify_signature(&hash, &signature, &key, SignatureDomain::Empty).unwrap());
            for domain in ["telegram.org", "TELEGRAM.ORG", "telegram.org."] {
                p["proofDomain"] = json!(domain);
                assert!(gram_ton_connect_crypto(phrase.to_owned(), p.to_string()).is_err());
            }
        }
    }
}
